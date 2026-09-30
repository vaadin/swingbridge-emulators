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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AppInstance;
import vaadinx.EHelper;
import vaadinx.MockVirtualThreadAwareServlet;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;
import vaadinx.swing.app.MainWindowRoute;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.WindowConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * D_shutdown_lifecycle — shutdown lifecycle: session-destroy → {@code WINDOW_CLOSING}, and the
 * {@code shuttingDown} throw at the browser-round-trip / blocking-await seams.
 *
 * <p>The dispatch logic is driven directly via {@code JFrame.dispatchShutdownClosing}
 * (Karibu models {@code fireSessionDestroy} as a deferred, session-locked access
 * task that's awkward to drain mid-test); one teardown-driven test proves the
 * listener is actually wired to session-destroy. The prompt tab-close trigger
 * ({@code JFrame.dispatchShutdownFromTabClose}, fired by {@code AppTab.onTabClosed} on
 * the reaper thread) is covered here for its dispatch + dedup contract; its
 * end-to-end reaper timing is a real-browser/Playwright concern (Karibu can't
 * reproduce the request-less reaper thread — D_shutdown_lifecycle).
 */
class JFrameShutdownTest {

    /** Captures WINDOW_CLOSING from the teardown-driven wiring test (asserted after tearDown). */
    private final List<String> teardownEvents = new ArrayList<>();
    private boolean tornDown;

    @BeforeEach
    void setupKaribu() {
        Routes routes = new Routes(new LinkedHashSet<>(List.of(EmptyTestRoute.class)),
                new LinkedHashSet<>(), false);
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
        JFrame.resetMainWindowClassForTesting();
    }

    @AfterEach
    void teardownKaribu() {
        JFrame.resetMainWindowClassForTesting();
        if (!tornDown) {
            MockVaadin.tearDown();
        }
    }

    // ---- session-destroy → WINDOW_CLOSING (dispatch logic) ------------

    @Test
    @DisplayName("dispatch fires WINDOW_CLOSING on a visible MainWindow frame")
    void dispatchFiresWindowClosingOnAVisibleMainWindow() {
        ShutdownMainFrame frame = inBootstrap(ShutdownMainFrame::new);
        frame.setVisible(true);
        // DO_NOTHING isolates the WINDOW_CLOSING delivery from close-op side effects.
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        List<String> events = new ArrayList<>();
        frame.addWindowListener(closingRecorder(events));

        JFrame.dispatchShutdownClosing(UI.getCurrent().getSession());

        assertEquals(List.of("closing"), events, "WINDOW_CLOSING delivered to the registered main window");
        assertTrue(EHelper.isShuttingDown(UI.getCurrent().getSession()), "dispatch marks the session shutting down");
    }

    @Test
    @DisplayName("a WINDOW_CLOSING handler can still read app-instance state")
    void windowClosingHandlerCanReadAppInstanceState() {
        // The reason AppInstance exists rather than a TabScope.getValues() call at the
        // call site (M1D_former_singletons): cleanup handlers run with no current UI, and flushing a
        // cache or saving preferences out of a former static is exactly what they do.
        ShutdownMainFrame frame = inBootstrap(ShutdownMainFrame::new);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        VaadinSession session = UI.getCurrent().getSession();
        ShutdownHolder duringApp = AppInstance.get(ShutdownHolder.class, ShutdownHolder::new);
        duringApp.unsavedEdits = 3;

        // Nested class rather than captured locals: the recorder must survive
        // the dispatch, and a one-cell array per field would read as a puzzle.
        ShutdownStateRecorder recorder = new ShutdownStateRecorder();
        frame.addWindowListener(recorder);

        UI appUI = UI.getCurrent();
        UI.setCurrent(null);   // the request-less reaper / session-destroy thread
        try {
            JFrame.dispatchShutdownClosing(session);
        } finally {
            UI.setCurrent(appUI);   // restore for teardown; CurrentInstance is thread-local and shared
        }

        assertTrue(recorder.fired, "WINDOW_CLOSING must actually be delivered, else the rest asserts nothing");
        assertNull(recorder.uiAtShutdown, "precondition: cleanup really does run with no current UI");
        assertSame(duringApp, recorder.seenAtShutdown, "cleanup must see the app's own state, not a fresh instance");
        assertEquals(3, recorder.seenAtShutdown.unsavedEdits);
    }

    @Test
    @DisplayName("dispatch does NOT fire WINDOW_CLOSING if the app already closed itself (dedup)")
    void dispatchDedupsAfterAProgrammaticDispose() {
        // A programmatic dispose() (e.g. an EXIT_ON_CLOSE Quit menu) already ran
        // WINDOW_CLOSED and dropped visibility; the session-destroy dispatch must
        // not re-fire WINDOW_CLOSING out of order. D_shutdown_lifecycle dedup via the visibility check.
        ShutdownMainFrame frame = inBootstrap(ShutdownMainFrame::new);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);   // dispose() won't close the session
        List<String> events = new ArrayList<>();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                events.add("closing");
            }

            @Override
            public void windowClosed(WindowEvent e) {
                events.add("closed");
            }
        });

        frame.dispose();                                                    // fires WINDOW_CLOSED, drops visibility
        JFrame.dispatchShutdownClosing(UI.getCurrent().getSession());       // frame not visible → skip

        assertEquals(List.of("closed"), events, "no WINDOW_CLOSING after the app disposed itself");
    }

    @Test
    @DisplayName("tab-close prompt dispatch fires WINDOW_CLOSING once and dedups the session-destroy backstop")
    void tabClosePromptDispatchFiresOnceAndDedupsTheBackstop() {
        // The D_shutdown_lifecycle fix: on a real app-tab close AppTab.onTabClosed fires WINDOW_CLOSING
        // promptly via dispatchShutdownFromTabClose on the tab-scope reaper thread,
        // instead of waiting for VaadinSession-destroy (which never runs on that
        // request-less thread — the browser bug). The later real destroy (backstop)
        // must then no-op: the prompt dispatch nulls MAIN_WINDOW_KEY + marks shuttingDown.
        ShutdownMainFrame frame = inBootstrap(ShutdownMainFrame::new);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        List<String> events = new ArrayList<>();
        frame.addWindowListener(closingRecorder(events));
        VaadinSession session = UI.getCurrent().getSession();

        JFrame.dispatchShutdownFromTabClose(session);                // prompt tab-close path
        JFrame.dispatchShutdownClosing(session);                     // backstop, later at real destroy

        assertEquals(List.of("closing"), events, "WINDOW_CLOSING fires once on the tab-close path; the backstop dedups");
        assertTrue(EHelper.isShuttingDown(session), "prompt dispatch marks the session shutting down");
    }

    @Test
    @DisplayName("dispatch is a no-op for a session with no registered MainWindow")
    void dispatchIsANoOpWithNoRegisteredMainWindow() {
        // A plain DialogStrategy JFrame is not the app and never registers, so
        // there's nothing to dispatch to.
        JFrame f = new JFrame();   // DialogStrategy — not @MainWindow
        f.setVisible(true);
        List<String> events = new ArrayList<>();
        f.addWindowListener(closingRecorder(events));

        JFrame.dispatchShutdownClosing(UI.getCurrent().getSession());

        assertEquals(List.of(), events, "only a registered @MainWindow frame receives shutdown WINDOW_CLOSING");
    }

    @Test
    @DisplayName("a throwing WINDOW_CLOSING listener is routed to the ErrorHandler and dispatch continues")
    void aThrowingWindowClosingListenerGoesToTheErrorHandler() {
        ShutdownMainFrame frame = inBootstrap(ShutdownMainFrame::new);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                throw new RuntimeException("boom");
            }
        });
        List<Throwable> captured = new ArrayList<>();
        UI.getCurrent().getSession().setErrorHandler(event -> captured.add(event.getThrowable()));

        // Must not propagate — a buggy listener can't strand teardown (D_close_operation_dispatch).
        JFrame.dispatchShutdownClosing(UI.getCurrent().getSession());

        assertEquals("boom", assertSingle(captured).getMessage(),
                "buggy listener routed to the session ErrorHandler");
    }

    // ---- wiring: the listener is registered on session-destroy --------

    @Test
    @DisplayName("session-destroy actually delivers WINDOW_CLOSING (registration wiring)")
    void sessionDestroyActuallyDeliversWindowClosing() {
        ShutdownMainFrame frame = inBootstrap(ShutdownMainFrame::new);
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(closingRecorder(teardownEvents));

        // tearDown fires real session-destroy → the registered listener →
        // dispatch. Assert after; guard @AfterEach against a double tearDown.
        MockVaadin.tearDown();
        tornDown = true;

        assertEquals(List.of("closing"), teardownEvents, "session-destroy invoked the registered dispatch");
    }

    // ---- F5-safety ----------------------------------------------------

    @Test
    @DisplayName("F5 detach and re-attach does NOT fire WINDOW_CLOSING")
    void f5DetachAndReattachDoesNotFireWindowClosing() {
        // WINDOW_CLOSING fires only from session-destroy, which is F5-safe (the
        // session survives a refresh). A route detach/re-attach (the shape of an
        // @PreserveOnRefresh rebind) must not deliver it.
        Bound<ShutdownMainFrame> bound = inBootstrapWithRoute(ShutdownMainFrame::new);
        ShutdownMainFrame frame = bound.frame();
        frame.setVisible(true);
        List<String> events = new ArrayList<>();
        frame.addWindowListener(closingRecorder(events));

        UI.getCurrent().remove(bound.route());
        UI.getCurrent().add(bound.route());

        assertEquals(List.of(), events, "F5 detach/re-attach must not fire WINDOW_CLOSING");
    }

    // ---- the shuttingDown throw (D_shutdown_lifecycle) --------------------------------

    @Test
    @DisplayName("a modal dialog park throws the shutdown message while shutting down")
    void aModalDialogParkThrowsWhileShuttingDown() {
        // Category A — browser round-trip await. The funnel is
        // Dialog.parkUntilClose; with the tab gone the park could never unblock.
        EHelper.markShuttingDown(UI.getCurrent().getSession());
        JDialog dialog = new JDialog();
        dialog.setModal(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> dialog.setVisible(true));
        assertTrue(ex.getMessage().contains("shutting down"), "message names shutdown as the cause");
    }

    @Test
    @DisplayName("SwingWorker.get throws the shutdown message while shutting down")
    void swingWorkerGetThrowsWhileShuttingDown() {
        // Category B — server-side await; doesn't hang, but the shutdown-named
        // message beats the cryptic downstream error for log diagnosis.
        EHelper.markShuttingDown(UI.getCurrent().getSession());
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return "x";
            }
        };

        IllegalStateException ex = assertThrows(IllegalStateException.class, worker::get);
        assertTrue(ex.getMessage().contains("shutting down"), "message names shutdown as the cause");
    }

    @Test
    @DisplayName("SwingUtilities.invokeAndWait throws the shutdown message while shutting down")
    void invokeAndWaitThrowsWhileShuttingDown() {
        EHelper.markShuttingDown(UI.getCurrent().getSession());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> SwingUtilities.invokeAndWait(() -> { }));
        assertTrue(ex.getMessage().contains("shutting down"), "message names shutdown as the cause");
    }

    // ---- callSwing R_callswing_envelope shutdown carve-out (D_shutdown_lifecycle) -----------------------

    @Test
    @DisplayName("callSwing with no current UI runs inline while shutting down")
    void callSwingWithNoCurrentUiRunsInlineWhileShuttingDown() {
        // Reaper-thread reality: WINDOW_CLOSING detaches the peer tree, and the
        // detach listeners call callSwing with no current UI but the session
        // current (inside session.access). During shutdown that's expected
        // teardown, not a programming error — run inline instead of throwing.
        UI ui = UI.getCurrent();
        VaadinSession session = ui.getSession();
        EHelper.markShuttingDown(session);
        UI.setCurrent(null);                 // no current UI…
        VaadinSession.setCurrent(session);   // …but the session is current, as on the reaper thread
        try {
            vaadinx.Counter ran = new vaadinx.Counter();
            EHelper.callSwing(ran::inc);     // must NOT throw
            assertEquals(1, ran.get(), "teardown callback ran inline during shutdown despite no current UI");
        } finally {
            UI.setCurrent(ui);
        }
    }

    @Test
    @DisplayName("callSwing with no current UI still throws when not shutting down (R_callswing_envelope preserved)")
    void callSwingWithNoCurrentUiStillThrowsWhenLive() {
        // The carve-out is shutdown-only: a missing UI on a live session stays a
        // loud programming error.
        UI ui = UI.getCurrent();
        VaadinSession session = ui.getSession();
        UI.setCurrent(null);
        VaadinSession.setCurrent(session);
        try {
            assertThrows(IllegalStateException.class, () -> EHelper.callSwing(() -> { }));
        } finally {
            UI.setCurrent(ui);
        }
    }

    // ---- helpers ------------------------------------------------------

    /** A frame and the {@link ShutdownTestRoute} whose bootstrap constructed it. */
    private record Bound<T extends JFrame>(ShutdownTestRoute route, T frame) {
    }

    private static <T extends JFrame> Bound<T> inBootstrapWithRoute(Supplier<T> block) {
        List<T> captured = new ArrayList<>();
        ShutdownTestRoute route = new ShutdownTestRoute(() -> captured.add(block.get()));
        UI.getCurrent().add(route);
        return new Bound<>(route, captured.get(0));
    }

    private static <T extends JFrame> T inBootstrap(Supplier<T> block) {
        return inBootstrapWithRoute(block).frame();
    }

    /** Records the string {@code "closing"} into {@code events} on WINDOW_CLOSING. */
    private static WindowAdapter closingRecorder(List<String> events) {
        return new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                events.add("closing");
            }
        };
    }

    /** MainWindowRoute that runs a lambda for bootstrap. */
    private static class ShutdownTestRoute extends MainWindowRoute {

        private final Runnable onBootstrap;

        ShutdownTestRoute(Runnable onBootstrap) {
            this.onBootstrap = onBootstrap;
        }

        @Override
        protected void bootstrap() {
            onBootstrap.run();
        }
    }

    /** @MainWindow class for the shutdown tests. */
    @MainWindow
    private static class ShutdownMainFrame extends JFrame {
    }

    /** Stand-in for a migrated app's {@code FormerSingletons}, read from cleanup code. */
    static class ShutdownHolder {
        int unsavedEdits;
    }

    /** Records what the app can still see from inside a shutdown WINDOW_CLOSING. */
    private static class ShutdownStateRecorder extends WindowAdapter {

        boolean fired;
        UI uiAtShutdown = UI.getCurrent();
        ShutdownHolder seenAtShutdown;

        @Override
        public void windowClosing(WindowEvent e) {
            fired = true;
            uiAtShutdown = UI.getCurrent();
            seenAtShutdown = AppInstance.get(ShutdownHolder.class, ShutdownHolder::new);
        }
    }
}
