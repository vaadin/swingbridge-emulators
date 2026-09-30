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
import vaadinx.awt.EventQueue;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@code SwingUtilities.invokeLater} / {@code invokeAndWait} from a background thread carrying an
 * {@link EmulatorContext} — the desktop's way for a {@code SwingWorker.doInBackground()} to touch
 * its components (D_invoke_from_background).
 *
 * <p>Karibu's session lock is held by the test thread for the whole test, so the helpers release
 * it while the worker runs: the worker's delivery needs the lock, and a Karibu unlock is also what
 * drains the queued access task, as a Push-driven request would in production.
 */
class SwingUtilitiesFromBackgroundTest extends AbstractKaribuTest {

    /** Starts {@code body} on a thread carrying this session's context, without waiting for it. */
    private static Thread startWorker(ThrowingRunnable body, AtomicReference<Throwable> failure) {
        EmulatorContext ctx = EmulatorContext.get();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "invoke-worker");
        t.setDaemon(true);
        t.start();
        return t;
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

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
        if (t.isAlive()) fail("the worker never returned");
    }

    /** Polls off the lock for a button the worker's delivery renders. */
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
                // Not rendered yet.
            }
        }
        assertNotNull(found.get(), "the dialog never reached the browser");
        return found.get();
    }

    @Test
    @DisplayName("invokeAndWait from a worker runs the runnable on the EDT and returns after it")
    void invokeAndWaitRunsOnTheEdt() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean onEdt = new AtomicBoolean();
        AtomicBoolean ranBeforeReturn = new AtomicBoolean();
        AtomicBoolean ran = new AtomicBoolean();
        UI expected = UI.getCurrent();
        AtomicReference<UI> uiInside = new AtomicReference<>();

        Thread worker = startWorker(() -> {
            SwingUtilities.invokeAndWait(() -> {
                onEdt.set(SwingUtilities.isEventDispatchThread());
                uiInside.set(UI.getCurrent());
                ran.set(true);
            });
            ranBeforeReturn.set(ran.get());
        }, failure);
        join(worker);

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertTrue(onEdt.get(), "the runnable must run on the EDT");
        assertSame(expected, uiInside.get(), "the EDT is the session's live UI");
        assertTrue(ranBeforeReturn.get(), "invokeAndWait returns only once the runnable has run");
    }

    @Test
    @DisplayName("a component write inside invokeAndWait from a worker lands on the peer")
    void componentWriteInsideInvokeAndWaitLands() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        JTextField field = new JTextField();
        UI.getCurrent().add(field.getPeer());

        Thread worker = startWorker(
                () -> SwingUtilities.invokeAndWait(() -> field.setBackground(java.awt.Color.PINK)),
                failure);
        join(worker);

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertEquals("rgb(255,175,175)", field.getPeer().getElement().getStyle().get("background-color"));
    }

    @Test
    @DisplayName("a runnable's exception reaches the caller as InvocationTargetException")
    void runnableFailureIsWrapped() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Throwable> handled = new CopyOnWriteArrayList<>();
        VaadinSession.getCurrent().setErrorHandler(e -> handled.add(e.getThrowable()));
        RuntimeException boom = new RuntimeException("boom");

        Thread worker = startWorker(() -> SwingUtilities.invokeAndWait(() -> {
            throw boom;
        }), failure);
        join(worker);

        InvocationTargetException ite = assertInstanceOf(InvocationTargetException.class, failure.get());
        assertSame(boom, ite.getCause());
        // The JDK's InvocationEvent catches it for the caller; the EDT's handler never sees it.
        assertEquals(List.of(), handled, "the caller owns the failure, not the session ErrorHandler");
    }

    @Test
    @DisplayName("EventQueue.invokeAndWait from a worker behaves as SwingUtilities' does")
    void eventQueueTwinDelegates() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean onEdt = new AtomicBoolean();
        RuntimeException boom = new RuntimeException("boom");

        Thread worker = startWorker(() -> {
            EventQueue.invokeAndWait(() -> onEdt.set(EventQueue.isDispatchThread()));
            EventQueue.invokeAndWait(() -> {
                throw boom;
            });
        }, failure);
        join(worker);

        assertTrue(onEdt.get(), "the runnable must run on the EDT");
        InvocationTargetException ite = assertInstanceOf(InvocationTargetException.class, failure.get());
        assertSame(boom, ite.getCause());
    }

    @Test
    @DisplayName("invokeAndWait around a modal dialog returns once the user has answered it")
    void invokeAndWaitWaitsForAModalInsideIt() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger answer = new AtomicInteger(-99);

        Thread worker = startWorker(() -> SwingUtilities.invokeAndWait(() -> answer.set(
                JOptionPane.showConfirmDialog(null, "Sure?", "Confirm", JOptionPane.YES_NO_OPTION))),
                failure);

        Button yes = awaitButton("Yes");
        assertTrue(worker.isAlive(), "the caller must still be waiting while the dialog is open");
        EHelper.callSwing(() -> LocatorJ._click(yes));
        join(worker);

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertEquals(JOptionPane.YES_OPTION, answer.get());
    }

    @Test
    @DisplayName("invokeAndWait with no live UI throws rather than waiting forever")
    void invokeAndWaitWithNoLiveUiThrows() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        UI.getCurrent().close();

        Thread worker = startWorker(() -> SwingUtilities.invokeAndWait(() -> { }), failure);
        join(worker);

        assertInstanceOf(BrowserSessionClosedError.class, failure.get(),
                "an Error, so the worker body's catch (Exception) does not swallow it");
    }

    @Test
    @DisplayName("invokeLater from a worker runs the runnable on the EDT")
    void invokeLaterRunsOnTheEdt() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<UI> uiInside = new AtomicReference<>();
        UI expected = UI.getCurrent();

        Thread worker = startWorker(() -> SwingUtilities.invokeLater(() -> uiInside.set(UI.getCurrent())),
                failure);
        join(worker);
        // Whatever delivery is still queued runs on the next lock release.
        withoutSessionLock(() -> { });

        if (failure.get() != null) fail("the worker failed", failure.get());
        assertSame(expected, uiInside.get());
    }

    @Test
    @DisplayName("invokeLater with no live UI drops the runnable without throwing")
    void invokeLaterWithNoLiveUiDrops() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean ran = new AtomicBoolean();
        UI.getCurrent().close();

        Thread worker = startWorker(() -> SwingUtilities.invokeLater(() -> ran.set(true)), failure);
        join(worker);
        withoutSessionLock(() -> { });

        assertNull(failure.get());
        assertFalse(ran.get(), "a closed tab has no EDT to run it on");
    }
}
