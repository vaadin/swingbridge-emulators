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
import com.vaadin.flow.router.Route;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.Counter;
import vaadinx.MockVirtualThreadAwareServlet;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1D_bootstrap_canonical — {@link MainWindowRoute} refuses to attach on a UI where
 * {@link SwingBridgeEmulatorsBootstrap} never ran, so an app that forgot the one SPI line fails at first
 * load naming that file, instead of three separate late failures (a date input throwing,
 * focus silently wrong, no shutdown on tab close).
 *
 * <p>The wired case is asserted too, so an inverted check can't pass.
 */
public class MainWindowRouteWiringTest {

    /** Landing route so {@code MockVaadin.setup}'s default navigation lands somewhere. */
    @Route("")
    public static class LandingView extends Div {
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

    @Test
    @DisplayName("attaching on a UI SwingBridgeEmulatorsBootstrap never touched throws, naming the SPI file")
    void unwiredUiThrowsOnAttach() {
        UI ui = UI.getCurrent();
        // The emulators test classpath registers SwingBridgeEmulatorsBootstrap, so the marker is
        // there — clearing it is how a test reproduces an app that never did.
        ComponentUtil.setData(ui, SwingBridgeEmulatorsBootstrap.class, null);
        Counter booted = new Counter();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ui.add(new CountingRoute(booted::inc)));

        assertTrue(ex.getMessage().contains("SwingBridgeEmulatorsBootstrap"), "message names the class: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener"),
                "message names the SPI file: " + ex.getMessage());
        booted.assertEquals(0);
    }

    @Test
    @DisplayName("attaching on a wired UI bootstraps normally")
    void wiredUiBootstrapsNormally() {
        Counter booted = new Counter();
        UI.getCurrent().add(new CountingRoute(booted::inc));
        booted.assertEquals(1);
    }

    /** Ad-hoc MainWindowRoute counting its bootstraps. */
    private static class CountingRoute extends MainWindowRoute {

        private final Runnable onBootstrap;

        CountingRoute(Runnable onBootstrap) {
            this.onBootstrap = onBootstrap;
        }

        @Override
        protected void bootstrap() {
            onBootstrap.run();
        }
    }
}
