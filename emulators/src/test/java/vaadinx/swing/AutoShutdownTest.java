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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AppTab;
import vaadinx.EHelper;
import vaadinx.MockVirtualThreadAwareServlet;
import vaadinx.swing.app.MainWindowRoute;

import javax.swing.WindowConstants;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_auto_shutdown — the app ends when its last displayable Window is disposed.
 *
 * <p><b>The oracle is a measurement, not a reading of the JDK's source.</b> One
 * test per scenario of the JDK 25 / Xvfb probe tabulated in D_auto_shutdown:
 * where the desktop JVM ended, the session must close; where it stayed alive (a
 * hidden window, an ownerless dialog, a login→main handoff, a running Timer),
 * the session must stay open.
 *
 * <p>Every dispose here runs inside {@link EHelper#callSwing}, where a migrated
 * app's disposes come from and whose epilogue takes the decision. A dispose from
 * outside any envelope only arms the check — the EDT-idle shape, and why tests
 * elsewhere that dispose directly see no session close.
 */
class AutoShutdownTest {

    /**
     * Karibu navigates to {@code ""} after a session close, so without
     * an empty-path route registered these tests fail on the cleanup
     * navigation instead of on their assertion.
     */
    @BeforeEach
    void setupKaribu() {
        Routes routes = new Routes(new LinkedHashSet<>(List.of(EmptyTestRoute.class)),
                new LinkedHashSet<>(), false);
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
        AppTab.markAppUI(UI.getCurrent());
        JFrame.resetMainWindowClassForTesting();
    }

    @AfterEach
    void teardownKaribu() {
        JFrame.resetMainWindowClassForTesting();
        MockVaadin.tearDown();
    }

    // ---- The four dispose rows: the close operation plays no part ----------

    @Test
    @DisplayName("dispose of the last displayable window ends the app — DO_NOTHING_ON_CLOSE")
    void disposeEndsTheAppOnDoNothing() {
        assertDisposeEndsTheApp(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    @Test
    @DisplayName("dispose of the last displayable window ends the app — HIDE_ON_CLOSE")
    void disposeEndsTheAppOnHide() {
        assertDisposeEndsTheApp(WindowConstants.HIDE_ON_CLOSE);
    }

    @Test
    @DisplayName("dispose of the last displayable window ends the app — DISPOSE_ON_CLOSE")
    void disposeEndsTheAppOnDispose() {
        assertDisposeEndsTheApp(WindowConstants.DISPOSE_ON_CLOSE);
    }

    @Test
    @DisplayName("dispose of the last displayable window ends the app — EXIT_ON_CLOSE")
    void disposeEndsTheAppOnExit() {
        assertDisposeEndsTheApp(WindowConstants.EXIT_ON_CLOSE);
    }

    /** One test per close operation: each needs its own session to close, as the probe needed its own JVM. */
    private static void assertDisposeEndsTheApp(int closeOp) {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(closeOp);
        VaadinSession session = UI.getCurrent().getSession();
        assertEquals(VaadinSessionState.OPEN, session.getState(), "session starts open");

        EHelper.callSwing(frame::dispose);

        assertNotEquals(VaadinSessionState.OPEN, session.getState(),
                "session ends on the last window's dispose, whatever the close op");
    }

    // ---- Rows that keep the app alive --------------------------------------

    @Test
    @DisplayName("hiding the last window keeps the session open — hidden is still displayable")
    void hideKeepsTheAppAlive() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        VaadinSession session = UI.getCurrent().getSession();

        EHelper.callSwing(() -> frame.setVisible(false));

        assertEquals(VaadinSessionState.OPEN, session.getState(),
                "setVisible(false) undisplayables nothing (D_window_displayable)");
    }

    @Test
    @DisplayName("an ownerless dialog keeps the session open after the frame is disposed")
    void anotherDisplayableWindowKeepsTheAppAlive() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        VaadinSession session = UI.getCurrent().getSession();
        // Ownerless on purpose, as the probe's row is: dispose() disposes owned
        // windows first, so an owned dialog would go undisplayable with its owner.
        JDialog dialog = new JDialog((vaadinx.awt.Frame) null, "keeps me alive", false);
        dialog.setVisible(true);

        EHelper.callSwing(frame::dispose);

        assertEquals(VaadinSessionState.OPEN, session.getState(),
                "any displayable Window counts, not only frames");
    }

    @Test
    @DisplayName("dispose-then-show a new frame in the same envelope keeps the session open")
    void handoffKeepsTheAppAlive() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        VaadinSession session = UI.getCurrent().getSession();

        EHelper.callSwing(() -> {
            frame.dispose();
            new TestMainFrame().setVisible(true);
        });

        assertEquals(VaadinSessionState.OPEN, session.getState(),
                "the login -> main window handoff survives");
    }

    @Test
    @DisplayName("dispose-then-invokeLater a new frame keeps the session open")
    void laterHandoffKeepsTheAppAlive() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        VaadinSession session = UI.getCurrent().getSession();

        EHelper.callSwing(() -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> new TestMainFrame().setVisible(true));
        });

        assertEquals(VaadinSessionState.OPEN, session.getState(),
                "the deferred ctor runs in the envelope's own drain, ahead of the check");
    }

    // ---- Vetoes -----------------------------------------------------------

    @Test
    @DisplayName("a running Timer keeps the session open, and stopping it ends the app")
    void runningTimerVetoesUntilStopped() {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        VaadinSession session = UI.getCurrent().getSession();
        // 10 minutes: this asserts the veto, not the fire path — the timer must not tick.
        Timer clock = new Timer(600_000, e -> { });

        EHelper.callSwing(() -> {
            clock.start();
            frame.dispose();
        });
        assertEquals(VaadinSessionState.OPEN, session.getState(),
                "the status-bar-clock paper cut, reproduced (R_no_silent_improvements)");

        // Armed through the veto, so this envelope re-runs the check.
        EHelper.callSwing(clock::stop);

        assertNotEquals(VaadinSessionState.OPEN, session.getState(),
                "app ends once the last hold is released");
    }

    @Test
    @DisplayName("an in-flight SwingWorker does not veto, and its done() is abandoned")
    void inFlightSwingWorkerDoesNotVeto() throws Exception {
        TestMainFrame frame = inBootstrap(TestMainFrame::new);
        frame.setVisible(true);
        VaadinSession session = UI.getCurrent().getSession();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        boolean[] doneRan = {false};
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
            }

            @Override
            protected void done() {
                doneRan[0] = true;
            }
        };
        worker.execute();
        // Drain the worker's STARTED-state hop first (it only exists once the worker
        // runs), leaving the dispose envelope's own drain empty: Karibu's
        // post-session-close hook re-runs the UI queue and asserts a single live UI,
        // which a close from inside a drain trips — a harness artifact, not a product one.
        assertTrue(started.await(5, TimeUnit.SECONDS), "worker never started");
        MockVaadin.runUIQueue();

        EHelper.callSwing(frame::dispose);
        assertNotEquals(VaadinSessionState.OPEN, session.getState(),
                "worker threads are daemons on the desktop — the JVM ended without them");

        // Let the worker finish into the session that just went away.
        release.countDown();
        Thread.sleep(300);
        assertFalse(doneRan[0], "done() is lost with the app, as it is on the desktop");
    }

    // ---- Helpers ----------------------------------------------------------

    /** Runs {@code block} inside a {@link MainWindowRoute#current()} scope, which is what lets an {@code @MainWindow} JFrame capture a route. */
    private static <T extends JFrame> T inBootstrap(Supplier<T> block) {
        List<T> captured = new java.util.ArrayList<>();
        TestRoute route = new TestRoute(() -> captured.add(block.get()));
        UI.getCurrent().add(route);
        return captured.get(0);
    }

    /** Ad-hoc MainWindowRoute that runs a lambda for bootstrap. */
    private static class TestRoute extends MainWindowRoute {

        private final Runnable onBootstrap;

        TestRoute(Runnable onBootstrap) {
            this.onBootstrap = onBootstrap;
        }

        @Override
        protected void bootstrap() {
            onBootstrap.run();
        }
    }

    @MainWindow
    private static class TestMainFrame extends JFrame {
    }
}
