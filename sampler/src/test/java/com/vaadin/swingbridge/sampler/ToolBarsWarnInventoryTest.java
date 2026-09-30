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
import vaadinx.swing.JToolBar;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingConstants;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JToolBar} WARN inventory exit gate. Both
 * tests fail if any {@code EHelper.onUnimplemented} fires along the
 * asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "ToolBars", asserts all three demos construct WARN-free,
 *       and clicks one button per toolbar to verify the full ActionEvent
 *       fan-out path runs WARN-free end-to-end (including the
 *       {@code add(Action)} convenience path on Demo 1).</li>
 *   <li>{@link #inventory_jtoolbar_api_surface} — micro-driver over
 *       the JToolBar / SJToolBar API buckets. Exercises every surface
 *       in the WARN-free set: ctors (4 variants), orientation forwarding,
 *       emulator-only field shadow (floatable / rollover / borderPainted),
 *       margin → CSS padding, addSeparator (both overloads), add(Action),
 *       and Container introspection helpers. Expected-WARN paths
 *       (setLayout with non-null LayoutManager, getUI / setUI) live in
 *       the per-class unit tests.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks are wired to a single sink so
 * a stub fire from either layer surfaces.
 */
class ToolBarsWarnInventoryTest {

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

        Navigate.to("ToolBars");
        dump("Step 0b (ToolBarsPanel swap)", warnings);

        // Three SJToolBar peers should be in the DOM (one per demo).
        // Asserting count guards against a future refactor accidentally
        // dropping a demo.
        List<com.vaadin.swingbridge.surrogates.SJToolBar> toolbars =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJToolBar.class, spec -> spec.withCount(3));
        dump("Step 1 (3 SJToolBar peers found)", warnings);

        // Verify the orientation mix: 2 horizontal + 1 vertical.
        long horizontal = toolbars.stream()
                .filter(t -> t.getOrientation() == SwingConstants.HORIZONTAL).count();
        long vertical = toolbars.stream()
                .filter(t -> t.getOrientation() == SwingConstants.VERTICAL).count();
        if (horizontal != 2 || vertical != 1) {
            throw new AssertionError(
                    "Expected 2 HORIZONTAL + 1 VERTICAL toolbars, got "
                            + horizontal + "/" + vertical);
        }
        dump("Step 2 (orientation mix: 2 H + 1 V)", warnings);

        // Click "New" on Demo 1 — exercises the standard JButton path
        // through the toolbar.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("New")));
        dump("Step 3 (Demo 1: click 'New' button)", warnings);

        // Click "Long op" on Demo 1 — exercises the add(Action) convenience
        // path (button wired via setAction, not addActionListener).
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Long op")));
        dump("Step 4 (Demo 1: click add(Action)-wired 'Long op')", warnings);

        // Click "Up" on Demo 2's vertical toolbar.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Up")));
        dump("Step 5 (Demo 2: click 'Up' on vertical toolbar)", warnings);

        // Click "Action A" on Demo 3's margin-toolbar.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Action A")));
        dump("Step 6 (Demo 3: click 'Action A' on margin-toolbar)", warnings);

        WarnDump.println();
        WarnDump.println("=== toolbars user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jtoolbar_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors --
        new JToolBar();
        new JToolBar(SwingConstants.HORIZONTAL);
        new JToolBar(SwingConstants.VERTICAL);
        new JToolBar("Edit");
        new JToolBar("Edit", SwingConstants.VERTICAL);
        dump("JToolBar  ctors (4 variants + name+orientation)", warnings);

        // -- Orientation round-trip --
        JToolBar tb = new JToolBar();
        tb.getOrientation();
        tb.setOrientation(SwingConstants.VERTICAL);
        tb.setOrientation(SwingConstants.HORIZONTAL);
        dump("JToolBar  orientation round-trip", warnings);

        // -- Emulator-only field shadows --
        tb.setFloatable(false);  // JLawyer's common path
        tb.isFloatable();
        tb.setFloatable(true);
        tb.setRollover(true);
        tb.isRollover();
        tb.setRollover(false);
        tb.setBorderPainted(false);
        tb.isBorderPainted();
        tb.setBorderPainted(true);
        dump("JToolBar  floatable / rollover / borderPainted round-trip", warnings);

        // -- Margin → CSS padding --
        tb.setMargin(new Insets(2, 4, 2, 4));
        tb.getMargin();
        tb.setMargin(null);
        dump("JToolBar  margin round-trip", warnings);

        // -- addSeparator (both overloads) --
        tb.addSeparator();
        tb.addSeparator(new java.awt.Dimension(20, 10));
        dump("JToolBar  addSeparator (both overloads)", warnings);

        // -- add(Action) convenience --
        Action action = new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) { /* no-op */ }
        };
        tb.add(action);
        dump("JToolBar  add(Action) convenience", warnings);

        // -- Container introspection --
        tb.getComponentIndex(tb.getComponent(0));
        tb.getComponentAtIndex(0);
        dump("JToolBar  getComponentIndex / getComponentAtIndex", warnings);

        // -- L&F surface (WARN-free portion only — getUI/setUI WARN and
        //    are covered in the per-class unit tests) --
        tb.getUIClassID();
        tb.updateUI();
        dump("JToolBar  L&F (getUIClassID + updateUI)", warnings);

        WarnDump.println();
        WarnDump.println("=== JToolBar API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the ToolBars exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
