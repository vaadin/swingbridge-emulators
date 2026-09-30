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

import com.github.mvysny.kaributesting.v10.KaribuConfig;
import com.github.mvysny.kaributesting.v10.MockBrowser;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.server.VaadinSessionState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.MockVirtualThreadAwareServlet;
import vaadinx.swing.app.MainWindowRoute;
import vaadinx.swing.app.SwingBridgeEmulatorsBootstrap;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * D_shutdown_lifecycle Part B — the {@code vaadin-tab-scope} tab-close detector, driven browserlessly
 * via Karibu's {@link MockBrowser} (the trick vaadin-tab-scope uses to test
 * itself). Covers the three behaviours a real browser would otherwise be needed
 * for: the app tab's close kills the session, a curtained tab's close does not,
 * and F5 doesn't kill it.
 *
 * <p>{@link SwingBridgeEmulatorsBootstrap} wires the tab-close leg unconditionally, so no opt-in
 * is needed — Karibu fakes {@code ExtendedClientDetails}, which is all the
 * handshake requires. Reaping is grace-gated by
 * {@code TabScope.CLEANUP_DURATION_MS} (package-private), shrunk here via
 * reflection so an orphaned scope is reaped in the same sweep — mirroring
 * vaadin-tab-scope's own {@code TabScopeLifecycleTest}.
 */
class JFrameTabCloseTest {

    private String savedWindowName;

    @BeforeEach
    void setup() {
        savedWindowName = KaribuConfig.getWindowName();
        KaribuConfig.setWindowName("tab-A");   // seed tab #1's window.name (before setup)
        Routes routes = new Routes(
                new LinkedHashSet<>(List.of(TabCloseAppRoute.class, EmptyTestRoute.class)),
                new LinkedHashSet<>(), false);
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
        JFrame.resetMainWindowClassForTesting();
    }

    @AfterEach
    void teardown() {
        setCleanupDurationMs(60_000L);   // restore the production grace period
        JFrame.resetMainWindowClassForTesting();
        try {
            MockVaadin.tearDown();
        } catch (Exception e) {
            // A test that closes the session leaves teardown little to do.
        }
        KaribuConfig.setWindowName(savedWindowName);
    }

    @Test
    @DisplayName("closing the app tab closes the session")
    void closingTheAppTabClosesTheSession() {
        UI.getCurrent().navigate("app");                // tab-A hosts the app → marked
        VaadinSession session = UI.getCurrent().getSession();
        assertEquals(VaadinSessionState.OPEN, session.getState(), "session starts open");
        String appTab = MockBrowser.getCurrentWindowName();

        MockBrowser.newTab("tab-B", "app");    // a second (curtained) tab, now focused
        setCleanupDurationMs(-1L);
        MockBrowser.closeTab(appTab, true);    // close the app tab (now background); beacon lost → lingers
        try {
            MockVaadin.reapInactiveUIs();               // reap → app scope destroyed → session.close()
        } catch (UIDetachedException e) {
            // Karibu-only artifact: reapInactiveUIs synchronously discards the
            // reaped UI, but our destroy → session.close() already detached it in
            // the same sweep, so the harness's follow-up push-mode reset trips.
            // session.close() has already run by then (asserted below); in
            // production the destroy fires async against an already-gone tab, so
            // there is no synchronous discard to race.
        }

        assertNotEquals(VaadinSessionState.OPEN, session.getState(),
                "closing the app tab drove session shutdown");
    }

    @Test
    @DisplayName("closing a curtained (non-app) tab leaves the session open")
    void closingACurtainedTabLeavesTheSessionOpen() {
        UI.getCurrent().navigate("app");                // tab-A app → marked
        VaadinSession session = UI.getCurrent().getSession();
        MockBrowser.newTab("tab-B", "app");    // tab-B is the curtain — never marked
        MockBrowser.switchTo("tab-A");         // can't close the focused tab; focus the app tab

        setCleanupDurationMs(-1L);
        MockBrowser.closeTab("tab-B", true);   // close the curtained tab
        MockVaadin.reapInactiveUIs();

        assertEquals(VaadinSessionState.OPEN, session.getState(),
                "a curtained tab's close must not kill the app in the active tab");
    }

    @Test
    @DisplayName("F5 reload does not close the session")
    void f5ReloadDoesNotCloseTheSession() {
        UI.getCurrent().navigate("app");                // tab-A app → marked
        VaadinSession session = UI.getCurrent().getSession();

        UI.getCurrent().getPage().reload();             // F5 — tab scope survives (window.name-keyed)

        assertEquals(VaadinSessionState.OPEN, session.getState(), "F5 must not be read as a tab close");
    }

    // ---- helpers ------------------------------------------------------

    /** Shrink/grow tab-scope's orphan grace period (package-private static) via reflection. */
    private static void setCleanupDurationMs(long ms) {
        try {
            Field f = Class.forName("com.github.mvysny.vaadin.tabscope.TabScope")
                    .getDeclaredField("CLEANUP_DURATION_MS");
            f.setAccessible(true);
            f.setLong(null, ms);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not reach TabScope.CLEANUP_DURATION_MS", e);
        }
    }

    /** Test @MainWindow route: bootstrap builds and shows the app's main frame. */
    @Route("app")
    @PreserveOnRefresh
    public static class TabCloseAppRoute extends MainWindowRoute {
        @Override
        protected void bootstrap() {
            new TabCloseMainFrame().setVisible(true);
        }
    }

    /** The app's @MainWindow frame for the tab-close tests. */
    @MainWindow
    public static class TabCloseMainFrame extends JFrame {
    }
}
