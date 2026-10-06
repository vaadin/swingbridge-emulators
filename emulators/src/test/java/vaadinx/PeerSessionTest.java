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

package vaadinx;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.swing.JLabel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A worker's peer write never runs without the session locked and a UI current: it hops through
 * the session the peer attached to, or waits in the peer's queue until a UI is reachable
 * (vaadinx.awt.PeerWriteQueue). So it never races an attach in flight on the UI thread. The
 * surrogate still learns its session from the constructing thread's context (D_attach_aware_hop).
 */
class PeerSessionTest extends AbstractKaribuTest {

    private interface Body {
        void run() throws Exception;
    }

    /** Runs {@code body} on a thread, optionally carrying this session's context, with the Karibu lock released. */
    private static void onThread(boolean withContext, Body body) {
        EmulatorContext ctx = withContext ? EmulatorContext.get() : null;
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Runnable r = () -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        };
        Thread t = new Thread(ctx != null ? () -> ctx.run(r) : r, "peer-session");
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("the thread did not finish");
        if (failure.get() != null) fail("the thread failed", failure.get());
    }

    @Test
    @DisplayName("an emulator built on a context-carrying worker knows its session before attach, and so does its surrogate")
    void contextHandsTheSessionDown() {
        VaadinSession session = VaadinSession.getCurrent();
        AtomicReference<JLabel> built = new AtomicReference<>();
        onThread(true, () -> built.set(new JLabel("x")));

        assertSame(session, SHelper.sessionOf(built.get().getPeer()),
                "the surrogate learns the session from its emulator, since its own ctor found none current");
    }

    @Test
    @DisplayName("a write to a never-attached peer from a context-carrying worker waits for a UI, then runs locked")
    void contextCarryingWorkerQueuesUntilAUIIsReachable() {
        assertWriteQueuedUntilAUIIsReachable(true);
    }

    @Test
    @DisplayName("with no session known anywhere, the write waits for a UI rather than running unlocked")
    void noSessionAnywhereQueuesUntilAUIIsReachable() {
        assertWriteQueuedUntilAUIIsReachable(false);
    }

    /**
     * A worker's write to a peer that has never been attached does not run on the worker, context
     * or not; the next {@code getPeer()} with a UI current runs it, locked and with that UI current.
     */
    private static void assertWriteQueuedUntilAUIIsReachable(boolean writerHasContext) {
        AtomicReference<JLabel> built = new AtomicReference<>();
        onThread(false, () -> built.set(new JLabel("x")));
        JLabel label = built.get();

        AtomicInteger runs = new AtomicInteger();
        AtomicBoolean lockedWithUI = new AtomicBoolean();
        onThread(writerHasContext, () -> label.withPeer(p -> {
            runs.incrementAndGet();
            lockedWithUI.set(UI.getCurrent() != null && VaadinSession.getCurrent() != null
                    && VaadinSession.getCurrent().hasLock());
        }));
        assertEquals(0, runs.get(), "the worker has no UI to run the write with, so it waits");

        label.getPeer();
        assertEquals(1, runs.get(), "getPeer() with a UI current drains it, once");
        assertTrue(lockedWithUI.get(), "and runs it locked, with a UI current");
    }

    @Test
    @DisplayName("a worker writing while the UI thread attaches and detaches never writes unlocked")
    void attachRacingWritesStaysLocked() {
        AtomicReference<JLabel> built = new AtomicReference<>();
        onThread(false, () -> built.set(new JLabel("x")));
        JLabel label = built.get();
        UI ui = UI.getCurrent();
        VaadinSession session = VaadinSession.getCurrent();

        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EmulatorContext ctx = EmulatorContext.get();
        Thread writer = new Thread(() -> ctx.run(() -> {
            try {
                while (!stop.get()) {
                    label.setText("w" + writes.incrementAndGet());
                    label.setBackground(writes.get() % 2 == 0 ? java.awt.Color.PINK : java.awt.Color.BLUE);
                }
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "racing-writer");
        try {
            for (int i = 0; i < 200 && failure.get() == null; i++) {
                ui.add(label.getPeer());
                // Not before: until the first attach records a session the writer never blocks, so
                // its backlog grows with however long the peer takes to build, and draining it all
                // at once exhausts the heap. A pre-attach backlog is the test above's subject.
                if (i == 0) writer.start();
                session.unlock();
                Thread.onSpinWait();
                session.lock();
                VaadinSession.setCurrent(session);
                UI.setCurrent(ui);
                ui.remove(label.getPeer());
                session.unlock();
                Thread.onSpinWait();
                session.lock();
                VaadinSession.setCurrent(session);
                UI.setCurrent(ui);
            }
        } finally {
            stop.set(true);
            session.unlock();
            try {
                writer.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                session.lock();
                VaadinSession.setCurrent(session);
                UI.setCurrent(ui);
            }
        }
        if (failure.get() != null) fail("a write raced the attach", failure.get());
        assertTrue(writes.get() > 0, "the writer never ran");
        assertEquals(session, SHelper.sessionOf(label.getPeer()), "attach recorded the session");
    }
}
