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
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.BrowserSessionClosedError;
import vaadinx.EHelper;
import vaadinx.EmulatorContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Modal dialogs shown from a background thread — the {@code SwingWorker.doInBackground()}
 * case that real Swing apps ship (D_modal_from_background).
 *
 * <p>The shape under test is three steps, the same on both threads: show on the UI thread,
 * block <em>outside</em> that hop, hide on the UI thread. Only one thread is ever parked,
 * which is what the JDK does too — {@code WaitDispatchSupport.enter()} posts the pump to
 * the EDT and blocks the calling thread alone.
 *
 * <p>Karibu's session lock is held by the test thread for the whole test, so every helper
 * here releases it around the join: the background thread needs the lock to hop, and
 * waiting for it while holding it is the deadlock under test seen from the wrong side.
 */
class ModalFromBackgroundThreadTest extends AbstractKaribuTest {

    /**
     * Starts {@code body} on a background thread carrying this session's
     * {@link EmulatorContext} — a {@code doInBackground()} in miniature — without waiting
     * for it. The caller drives the browser side and then {@link #join}s.
     */
    private static Thread startWorker(Runnable body, AtomicReference<Throwable> failure) {
        EmulatorContext ctx = EmulatorContext.get();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "modal-worker");
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
                fail("interrupted waiting for the worker");
            }
        });
        if (t.isAlive()) fail("the worker never returned — the modal park was not released");
    }

    /**
     * Waits, off the lock, for the worker to reach its park and the dialog to render.
     * Polls rather than latches because the thing being waited for is the peer write
     * landing on the UI thread, which no Swing-side signal announces.
     */
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
        assertNotNull(found.get(), "the dialog never reached the browser — step 1's hop did not land");
        return found.get();
    }

    @Test
    @DisplayName("showConfirmDialog from a background thread renders and returns the user's choice")
    void showConfirmDialogFromBackgroundThreadReturnsTheChoice() {
        AtomicInteger ret = new AtomicInteger(-99);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread worker = startWorker(
                () -> ret.set(JOptionPane.showConfirmDialog(null, "Sure?", "Confirm",
                        JOptionPane.YES_NO_OPTION)),
                failure);

        Button yes = awaitButton("Yes");
        EHelper.callSwing(() -> LocatorJ._click(yes));
        join(worker);

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertEquals(JOptionPane.YES_OPTION, ret.get(),
                "the click's answer must come back out of the blocking call, as on the desktop");
    }

    @Test
    @DisplayName("a close landing between the show and the park still ends the park")
    void closeBeforeTheParkIsNotLost() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean returned = new AtomicBoolean();
        JDialog dialog = new JDialog();
        dialog.setModal(true);
        // A click racing the worker to its park, made deterministic: WINDOW_OPENED fires
        // inside the show, so this close lands before the park begins.
        dialog.addWindowListener(new vaadinx.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(vaadinx.awt.event.WindowEvent e) {
                dialog.dispose();
            }
        });

        Thread worker = startWorker(() -> {
            dialog.setVisible(true);
            returned.set(true);
        }, failure);
        join(worker);

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertTrue(returned.get(), "a close before the park must still release it");
    }

    @Test
    @DisplayName("the worker parks alone — the UI thread stays free to service the click")
    void onlyTheWorkerParks() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<UI> uiDuringPark = new AtomicReference<>();

        Thread worker = startWorker(
                () -> JOptionPane.showMessageDialog(null, "hello"),
                failure);

        Button ok = awaitButton("OK");
        // The parked worker holds neither the lock nor a current UI: the test thread can
        // still take the lock and run UI work. That is the whole point of blocking
        // outside the hop rather than inside it.
        EHelper.callSwing(() -> uiDuringPark.set(UI.getCurrent()));
        assertNotNull(uiDuringPark.get(), "the UI thread must remain usable while the worker is parked");

        EHelper.callSwing(() -> LocatorJ._click(ok));
        join(worker);
        if (failure.get() != null) fail("the worker failed", failure.get());
    }

    @Test
    @DisplayName("a background modal show leaves no current UI on the worker")
    void theWorkerKeepsNoCurrentUi() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<UI> afterReturn = new AtomicReference<>();
        AtomicBoolean sawUi = new AtomicBoolean();

        Thread worker = startWorker(() -> {
            sawUi.set(UI.getCurrent() != null);
            JOptionPane.showMessageDialog(null, "hello");
            afterReturn.set(UI.getCurrent());
        }, failure);

        Button ok = awaitButton("OK");
        EHelper.callSwing(() -> LocatorJ._click(ok));
        join(worker);

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertFalse(sawUi.get(), "an EmulatorContext carries the session, never a current UI");
        // Q_rebind_skip: awaitModal's rebind would install one here, silencing the
        // off-UI-thread WARN for the rest of the body and making every later peer write
        // look like legitimate UI-thread access while holding no lock.
        assertNull(afterReturn.get(),
                "the background park must not rebind UI.getCurrent() onto the worker");
    }

    @Test
    @DisplayName("interrupting a parked worker throws rather than reporting a dismissal")
    void interruptedParkThrowsInsteadOfLookingLikeAClose() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger ret = new AtomicInteger(-99);

        Thread worker = startWorker(
                () -> ret.set(JOptionPane.showConfirmDialog(null, "Sure?", "Confirm",
                        JOptionPane.YES_NO_OPTION)),
                failure);

        awaitButton("Yes");
        // What a session destroy does: SwingWorker's session-scoped pool shutdownNow()s,
        // which interrupts the worker mid-park.
        worker.interrupt();
        join(worker);

        // Returning normally would report CLOSED_OPTION — indistinguishable from the user
        // dismissing the dialog, and a lie with consequences: the worker would run on
        // against a browser that is gone. An Error, so a migrated catch (Exception) in the
        // worker body does not swallow it.
        assertInstanceOf(BrowserSessionClosedError.class, failure.get(),
                "an interrupted park must not be reported as a dismissal");
        assertEquals(-99, ret.get(), "no answer may be produced when none arrived");
    }

    @Test
    @DisplayName("a modal show with no live UI throws before parking, not after")
    void noLiveUiThrowsBeforeTheParkRatherThanHanging() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // The tab is gone: the AppTab record goes stale while the session stays lockable.
        UI.getCurrent().close();

        Thread worker = startWorker(
                () -> JOptionPane.showConfirmDialog(null, "Sure?", "Confirm",
                        JOptionPane.YES_NO_OPTION),
                failure);
        join(worker);

        // The failure this prevents is not a wrong answer but an indefinite hang: degrade
        // the show and the next line parks on a latch nothing can ever count down.
        assertInstanceOf(BrowserSessionClosedError.class, failure.get(),
                "a dropped show followed by a park is a hang, not a degradation");
    }

    @Test
    @DisplayName("a non-modal show from a background thread still degrades rather than throwing")
    void nonModalShowKeepsTheDegradeBargain() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        UI.getCurrent().close();

        JDialog dialog = new JDialog();
        dialog.setModal(false);
        CountDownLatch done = new CountDownLatch(1);
        Thread worker = startWorker(() -> {
            dialog.setVisible(true);
            done.countDown();
        }, failure);
        join(worker);

        assertTrue(done.await(1, TimeUnit.SECONDS), "a non-modal show must not block");
        assertNull(failure.get(),
                "nothing blocks after a non-modal show, so R_decline_effect_only's bargain stands");
        assertTrue(dialog.isVisible(), "the Swing-side state is kept; only the effect is dropped");
    }
}
