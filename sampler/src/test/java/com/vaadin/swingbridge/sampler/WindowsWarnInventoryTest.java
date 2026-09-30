/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JWindow} WARN inventory exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code SHelper.onUnimplemented}
 * fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "Windows", runs the splash demo end-to-end (open →
 *       Timer-stepped progress → auto-dispose) and the toast demo
 *       (setBounds placement, one-shot dismissal), asserting both
 *       SJWindow peers open/close WARN-free.</li>
 *   <li>{@link #inventory_jwindow_api_surface} — micro-driver over the
 *       JWindow API buckets: all five ctors, RootPaneContainer surface,
 *       Window geometry, lifecycle. The known-WARN members
 *       ({@code getAccessibleContext}, {@code setTransferHandler} per
 *       D_drag_and_drop scope) are deliberately NOT exercised — the goal is to lock
 *       in the WARN-free supported surface as a regression gate.</li>
 * </ol>
 */
class WindowsWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() throws InterruptedException {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Windows");
        dump("Step 0b (WindowsPanel swap)", warnings);

        // Demo 1 — splash. Opens centered, Timer steps 20%/250ms, auto-
        // disposes at 100% (~1.25s). Poll-and-drain until the SJWindow
        // peer detaches.
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("Show splash (auto-closes)")));
        List<com.vaadin.swingbridge.surrogates.SJWindow> splashes =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJWindow.class, spec -> spec.withCount(1));
        com.vaadin.swingbridge.surrogates.SJWindow splash = splashes.get(0);
        if (!splash.isUndecoratedChrome()) {
            throw new AssertionError("Splash SJWindow must carry the emul-undecorated class");
        }
        dump("Step 1 (splash opened, undecorated)", warnings);

        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            MockVaadin.runUIQueue();
            if (!splash.isOpened()) break;
            Thread.sleep(50);
        }
        if (splash.isOpened()) {
            throw new AssertionError("Splash did not auto-dispose within 5s");
        }
        dump("Step 2 (splash progress ran + auto-disposed)", warnings);

        // Demo 2 — toast. Bottom-right anchoring computed from
        // Toolkit.getScreenSize() (the browser viewport per D_toolkit_screen_size). Inject a
        // deterministic viewport so the classic Swing edge-math
        // (viewport - size - margin) is assertable: left = 1280-320-24,
        // top = 800-48-24. The 2.5s auto-dismissal is Timer machinery
        // already gated in Step 2 and TimersAndWorkers — not re-awaited here.
        vaadinx.awt.BrowserToolkitInfo.setTestInfo(1280, 800, 1.0, false);
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("Show toast (bottom-right, 2.5s)")));
        List<com.vaadin.swingbridge.surrogates.SJWindow> toasts = LocatorJ._find(
                com.vaadin.swingbridge.surrogates.SJWindow.class,
                spec -> spec.withPredicate(w -> w.isOpened()).withCount(1));
        com.vaadin.swingbridge.surrogates.SJWindow toast = toasts.get(0);
        assertEq("936px", toast.getLeft(), "toast left (1280 - 320 - 24)");
        assertEq("728px", toast.getTop(), "toast top (800 - 48 - 24)");
        assertEq("320px", toast.getWidth(), "toast width");
        assertEq("48px", toast.getHeight(), "toast height");
        dump("Step 3 (toast anchored bottom-right of the injected viewport)", warnings);

        WarnDump.println();
        WarnDump.println("=== windows user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jwindow_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors — all five JDK signatures --
        new JWindow();
        dump("JWindow  new JWindow()", warnings);

        new JWindow((vaadinx.awt.GraphicsConfiguration) null);
        dump("JWindow  new JWindow(GraphicsConfiguration)", warnings);

        vaadinx.swing.JFrame ownerFrame = new vaadinx.swing.JFrame();
        new JWindow(ownerFrame);
        dump("JWindow  new JWindow(Frame)", warnings);

        JWindow ownerWindow = new JWindow();
        new JWindow(ownerWindow);
        dump("JWindow  new JWindow(Window)", warnings);

        new JWindow(ownerWindow, null);
        dump("JWindow  new JWindow(Window, GraphicsConfiguration)", warnings);

        // -- RootPaneContainer surface --
        JWindow w = new JWindow();
        w.getContentPane();
        w.setContentPane(new vaadinx.awt.Container());
        w.add(new vaadinx.swing.JLabel("content"));
        w.setLayout(new vaadinx.awt.BorderLayout());
        w.getRootPane();
        w.getLayeredPane();
        w.getGlassPane();
        dump("JWindow  RootPaneContainer surface", warnings);

        // -- Window geometry (the surface that was WARN before this slice) --
        w.setLocation(10, 20);
        w.setLocation(new java.awt.Point(11, 21));
        w.setSize(300, 200);
        w.setSize(new java.awt.Dimension(310, 210));
        w.setBounds(5, 6, 400, 300);
        w.setBounds(new java.awt.Rectangle(7, 8, 410, 310));
        w.getLocation();
        w.getSize();
        w.getBounds();
        w.getX();
        w.getY();
        w.getWidth();
        w.getHeight();
        w.setLocationRelativeTo(null);
        dump("JWindow  Window geometry round-trip", warnings);

        // -- Owner chain --
        JWindow owned = new JWindow(ownerWindow);
        owned.getOwner();
        ownerWindow.getOwnedWindows();
        dump("JWindow  owner chain", warnings);

        // -- Lifecycle --
        w.setVisible(true);
        w.isVisible();
        w.isShowing();
        w.dispose();
        w.pack();
        dump("JWindow  lifecycle (show / dispose / pack)", warnings);

        WarnDump.println();
        WarnDump.println("=== JWindow API-surface WARN total across buckets above ===");
    }

    private static void assertEq(String expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + ", got " + actual);
        }
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the Windows exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
