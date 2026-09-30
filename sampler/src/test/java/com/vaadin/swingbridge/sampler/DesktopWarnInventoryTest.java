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
import vaadinx.swing.JDesktopPane;
import vaadinx.swing.JInternalFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JDesktopPane} + {@link JInternalFrame} WARN inventory exit gate
 * (D_internal_frames). Both tests fail if any {@code EHelper.onUnimplemented} /
 * {@code SHelper.onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "Desktop", opens an internal frame (which renders as a
 *       decorated non-modal Dialog overlay), toggles maximize, all
 *       WARN-free.</li>
 *   <li>{@link #inventory_api_surface} — micro-driver over the supported
 *       JInternalFrame + JDesktopPane surface (ctors, geometry, vetoable
 *       properties, minimize, close-op, frame bookkeeping). The known-WARN
 *       members ({@code getAccessibleContext}, {@code setDesktopManager},
 *       {@code setDragMode(OUTLINE)}) are deliberately NOT exercised.</li>
 * </ol>
 */
class DesktopWarnInventoryTest {

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
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Desktop");
        dump("Step 0b (DesktopPanel swap)", warnings);

        // Open a frame — renders as a decorated non-modal Dialog overlay.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Open frame")));
        List<com.vaadin.swingbridge.surrogates.SJInternalFrame> frames =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJInternalFrame.class, spec -> spec.withCount(1));
        com.vaadin.swingbridge.surrogates.SJInternalFrame frame = frames.get(0);
        if (!frame.isOpened()) {
            throw new AssertionError("Internal frame overlay must be open");
        }
        if (!frame.isDraggable() || !frame.isResizable()) {
            throw new AssertionError("Internal frame must keep its drag/resize chrome");
        }
        dump("Step 1 (internal frame opened, decorated, non-modal)", warnings);

        // Maximize from the title-bar maximize button.
        clickHeaderIcon("Maximize");
        if (!"100%".equals(frame.getWidth())) {
            throw new AssertionError("Maximized frame should fill the viewport width, got " + frame.getWidth());
        }
        dump("Step 2 (title-bar maximize → viewport-fill)", warnings);

        // Minimize from the title-bar minimize button — the overlay hides and
        // a "Restore" chip appears in the taskbar (the emulator-only iconify
        // path, WARN-free per D_internalframe_minimize).
        clickHeaderIcon("Minimize");
        if (frame.isOpened()) {
            throw new AssertionError("Minimized frame's overlay must be hidden");
        }
        dump("Step 3 (title-bar minimize → overlay hidden, INTERNAL_FRAME_ICONIFIED fired)", warnings);

        // Restore from the taskbar chip — the overlay reopens.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Restore: Document 1")));
        if (!frame.isOpened()) {
            throw new AssertionError("Restored frame's overlay must reopen");
        }
        dump("Step 4 (restore → overlay reopened, INTERNAL_FRAME_DEICONIFIED fired)", warnings);

        WarnDump.println();
        WarnDump.println("=== desktop user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_api_surface() throws java.beans.PropertyVetoException {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- JInternalFrame ctors — all six JDK signatures --
        new JInternalFrame();
        new JInternalFrame("t");
        new JInternalFrame("t", true);
        new JInternalFrame("t", true, true);
        new JInternalFrame("t", true, true, true);
        JInternalFrame f = new JInternalFrame("Doc", true, true, true, true);
        dump("JInternalFrame  ctors", warnings);

        // -- RootPaneContainer + content --
        f.getContentPane();
        f.setContentPane(new vaadinx.awt.Container());
        f.add(new vaadinx.swing.JLabel("content"));
        f.setLayout(new vaadinx.awt.BorderLayout());
        f.getRootPane();
        f.getLayeredPane();
        f.getGlassPane();
        dump("JInternalFrame  RootPaneContainer surface", warnings);

        // -- Geometry (overlay-mapped) --
        f.setLocation(10, 20);
        f.setSize(320, 180);
        f.setBounds(5, 6, 400, 300);
        f.getLocation();
        f.getSize();
        dump("JInternalFrame  geometry", warnings);

        // -- Lifecycle + vetoable properties --
        f.setTitle("Renamed");
        f.setDefaultCloseOperation(JInternalFrame.DISPOSE_ON_CLOSE);
        f.setVisible(true);
        f.setSelected(true);
        f.setMaximum(true);
        f.setMaximum(false);
        f.setIcon(true);   // minimize — emulator-complete, WARN-free (D_internalframe_minimize)
        f.setIcon(false);
        f.getDesktopIcon();
        f.setClosed(true);
        dump("JInternalFrame  lifecycle + constrained properties", warnings);

        // -- JDesktopPane bookkeeping surface --
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame a = new JInternalFrame("A");
        desktop.add(a);
        desktop.getAllFrames();
        desktop.getAllFramesInLayer(vaadinx.swing.JLayeredPane.DEFAULT_LAYER);
        desktop.setSelectedFrame(a);
        desktop.getSelectedFrame();
        desktop.setDragMode(JDesktopPane.LIVE_DRAG_MODE);
        desktop.getDragMode();
        desktop.remove(a);
        dump("JDesktopPane  bookkeeping surface", warnings);

        WarnDump.println();
        WarnDump.println("=== JDesktopPane / JInternalFrame API-surface WARN total across buckets above ===");
    }

    /** Click a Dialog-header title-bar icon (close / minimize / maximize) by its aria-label. */
    private static void clickHeaderIcon(String ariaLabel) {
        com.vaadin.flow.component.icon.Icon icon = LocatorJ._get(
                com.vaadin.flow.component.icon.Icon.class,
                spec -> spec.withAttribute("aria-label", ariaLabel));
        com.vaadin.flow.component.ComponentUtil.fireEvent(
                icon, new com.vaadin.flow.component.ClickEvent<>(icon));
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
                    + " stub WARN(s) fired — regression in the Desktop exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
