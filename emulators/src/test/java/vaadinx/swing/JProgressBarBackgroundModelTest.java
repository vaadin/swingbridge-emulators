/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.EmulatorContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JProgressBar} advanced from a {@code doInBackground()}-shaped worker. The
 * surrogate hops the peer write onto the UI thread (SD_background_model_hop); the emulator's
 * {@code ChangeListener}s hear the model on the worker, as on the desktop.
 */
class JProgressBarBackgroundModelTest extends AbstractKaribuTest {

    /** Starts {@code body} on a thread carrying this session's {@link EmulatorContext}. */
    private static Thread startWorker(Runnable body, AtomicReference<Throwable> failure) {
        EmulatorContext ctx = EmulatorContext.get();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "progress-worker");
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Releases the Karibu lock so the worker can take it, then restores it. */
    private static void withoutSessionLock(Runnable body) {
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            body.run();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
    }

    private static void join(Thread t) {
        withoutSessionLock(() -> {
            try {
                t.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        if (t.isAlive()) fail("the worker never returned");
    }

    private static void runOnWorker(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        join(startWorker(body, failure));
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
    }

    @Test
    @DisplayName("constructed and advanced on a worker, never attached: fires on the worker, as the JDK does")
    void detachedBarBuiltOnWorker() {
        AtomicReference<JProgressBar> built = new AtomicReference<>();
        List<UI> uis = new CopyOnWriteArrayList<>();

        runOnWorker(() -> {
            JProgressBar bar = new JProgressBar(0, 10);
            bar.addChangeListener(e -> uis.add(UI.getCurrent()));
            bar.setValue(4);
            built.set(bar);
        });

        assertEquals(1, uis.size());
        assertNull(uis.get(0));
        assertEquals(4, built.get().getValue());
    }

    @Test
    @DisplayName("attached, advanced on a worker through the model: fires on the worker, the peer write still lands")
    void attachedBarAdvancedThroughModel() {
        JProgressBar bar = new JProgressBar(0, 10);
        UI.getCurrent().add(bar.getPeer());
        List<Object> sources = new CopyOnWriteArrayList<>();
        List<UI> uis = new CopyOnWriteArrayList<>();
        bar.addChangeListener(e -> {
            sources.add(e.getSource());
            uis.add(UI.getCurrent());
        });

        runOnWorker(() -> {
            bar.getModel().setValue(3);
            bar.getModel().setValue(7);
        });

        assertEquals(2, sources.size());
        assertSame(bar, sources.get(0), "the emulator re-sources the model's event");
        assertNull(uis.get(0), "the JDK fires model events on the mutating thread");
        assertEquals(7, bar.getValue());
        assertEquals(7.0, ((ProgressBar) bar.getPeer()).getValue());
    }

    @Test
    @DisplayName("a listener opening a modal at 100% blocks the worker until answered, as on the desktop")
    void modalFromChangeListenerBlocksTheWorker() {
        JProgressBar bar = new JProgressBar(0, 10);
        UI.getCurrent().add(bar.getPeer());
        AtomicBoolean answered = new AtomicBoolean();
        bar.addChangeListener(e -> {
            if (bar.getValue() == bar.getMaximum()) {
                JOptionPane.showMessageDialog(null, "Import finished");
                answered.set(true);
            }
        });
        AtomicReference<Throwable> failure = new AtomicReference<>();

        // The listener runs on the worker with no UI current, outside the surrogate's hop,
        // so the modal takes D_modal_from_background's path: show on the UI thread, block
        // the worker alone until the user answers.
        Thread worker = startWorker(() -> bar.setValue(10), failure);
        Button ok = awaitButton("OK");
        assertFalse(answered.get(), "the dialog has not been answered yet");

        EHelper.callSwing(() -> LocatorJ._click(ok));
        join(worker);
        if (failure.get() != null) fail("the worker failed", failure.get());
        assertTrue(answered.get(), "the click releases the blocked worker");
    }

    /** Waits, off the lock, for the worker's dialog to render; see ModalFromBackgroundThreadTest. */
    private static Button awaitButton(String text) {
        AtomicReference<Button> found = new AtomicReference<>();
        for (int i = 0; i < 200 && found.get() == null; i++) {
            withoutSessionLock(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            try {
                found.set(LocatorJ._get(Button.class, spec -> spec.withText(text)));
            } catch (AssertionError notYet) {
                // Not rendered yet; the worker is still hopping.
            }
        }
        assertNotNull(found.get(), "the dialog never reached the browser");
        return found.get();
    }
}
