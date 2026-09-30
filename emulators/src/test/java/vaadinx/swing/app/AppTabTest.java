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

package vaadinx.swing.app;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AppTab;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.MockVirtualThreadAwareServlet;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session-scoped {@link AppTab} identity and the multi-tab <b>option 1</b> behaviour
 * it drives in {@link MainWindowRoute}: one app per session, a second browser tab does
 * not run a second copy — it shows the "already active elsewhere" curtain, and
 * the app tab stays on the first tab so Timer/SwingWorker delivery and
 * blocking-dialog resume keep targeting the running app.
 *
 * <p>A second "tab" is a second {@link UI} in the same session; Karibu's single-UI
 * fixture can't create one through navigation, so these hand-build one via
 * {@link #addFreshUI} (mirrors {@code UiScopedFeaturesF5Test}). A hand-built UI never runs the
 * tab-scope handshake, so its {@link AppTab} carries UI identity without a
 * {@code TabScope}; tab-close behaviour with a real
 * scope lives in {@code JFrameTabCloseTest}.
 */
public class AppTabTest {

    /** Landing route so {@code MockVaadin.setup}'s default navigation lands somewhere. */
    @Route("")
    public static class LandingView extends Div {
    }

    /** MainWindowRoute whose bootstrap bumps a counter. */
    public static class CountingRoute extends MainWindowRoute {

        private final Runnable onBoot;

        public CountingRoute() {
            this(() -> {
            });
        }

        public CountingRoute(Runnable onBoot) {
            this.onBoot = onBoot;
        }

        @Override
        protected void bootstrap() {
            onBoot.run();
        }
    }

    @BeforeEach
    void setup() {
        MockVaadin.setup(MockedUI::new,
                new MockVirtualThreadAwareServlet(new Routes(Set.of(LandingView.class), Set.of(), true)));
    }

    @AfterEach
    void teardown() {
        MockVaadin.tearDown();
    }

    /** Registers a fresh, initialised second UI in {@code session} — a second tab. */
    private static UI addFreshUI(VaadinSession session) {
        UI ui = new MockedUI();
        ui.getInternals().setSession(session);
        ui.doInit(null, session.getNextUIid(), "ROOT");
        session.addUI(ui);
        // The one UI-init effect this fixture needs: MainWindowRoute throws on attach
        // for a UI SwingBridgeEmulatorsBootstrap never ran on (M1D_bootstrap_canonical). Set directly
        // rather than by firing every init listener, which would also run the tab-scope
        // handshake and cost this fixture its bare-UI property.
        ComponentUtil.setData(ui, SwingBridgeEmulatorsBootstrap.class, new SwingBridgeEmulatorsBootstrap());
        return ui;
    }

    /** The session's app UI, asserting an AppTab exists at all. */
    private static UI appUI(VaadinSession session) {
        AppTab tab = AppTab.forSession(session);
        assertNotNull(tab, "the session should carry an AppTab");
        return tab.liveUI();
    }

    @Test
    @DisplayName("first attach becomes the app UI and bootstraps")
    void firstAttachBecomesTheAppUI() {
        UI ui1 = UI.getCurrent();
        Counter booted = new Counter();
        ui1.add(new CountingRoute(booted::inc));

        booted.assertEquals(1);
        assertSame(ui1, appUI(ui1.getSession()), "first tab is recorded as the app UI");
    }

    @Test
    @DisplayName("singleLiveUI returns the app UI even with a second UI present")
    void singleLiveUIResolvesTheAppUI() {
        UI ui1 = UI.getCurrent();
        ui1.add(new CountingRoute());
        addFreshUI(ui1.getSession()); // a second, bare UI in the session

        assertSame(ui1, EHelper.singleLiveUI(ui1.getSession()),
                "the AppTab resolves the app UI directly, no heuristic guessing");
    }

    @Test
    @DisplayName("a second tab does not bootstrap and shows the already-active curtain")
    void secondTabShowsTheCurtain() {
        UI ui1 = UI.getCurrent();
        Counter boot1 = new Counter();
        Counter boot2 = new Counter();
        ui1.add(new CountingRoute(boot1::inc));

        UI ui2 = addFreshUI(ui1.getSession());
        CountingRoute r2 = new CountingRoute(boot2::inc);
        ui2.add(r2);

        boot1.assertEquals(1);                  // first tab ran the app
        boot2.assertEquals(0);                  // second tab must not run a second copy
        assertSame(ui1, appUI(ui1.getSession()), "app tab stays on the first tab");
        assertTrue(r2.getChildren().anyMatch(c -> c instanceof Span),
                "second tab shows the already-active curtain");
    }

    @Test
    @DisplayName("AppTab liveUI goes null when the app UI is gone from the session")
    void liveUIGoesNullWhenTheAppUIIsGone() {
        UI ui1 = UI.getCurrent();
        ui1.add(new CountingRoute());
        assertSame(ui1, appUI(ui1.getSession()));

        // Simulate the app tab closing: Flow marks its UI closing before it
        // leaves the session.
        VaadinSession session = ui1.getSession();
        ui1.close();

        assertNull(appUI(session),
                "an app UI that is closing/gone resolves to null, so a new tab can claim the app");
    }
}
