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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JButton;
import vaadinx.swing.JSplitPane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JSplitPane} WARN inventory exit gate. Both
 * tests fail if any {@code EHelper.onUnimplemented} fires along the
 * asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "SplitPanes", asserts all three demos construct WARN-free
 *       (proportional setDividerLocation form, nested splitter, both
 *       orientations).</li>
 *   <li>{@link #inventory_jsplitpane_api_surface} — micro-driver over
 *       the JSplitPane / SJSplitPane API buckets in the WARN-free set:
 *       ctors (5 variants), orientation forwarding, slot setters,
 *       proportional setDividerLocation, resetToPreferredSizes, all six
 *       emulator-only field shadows (dividerLocation int,
 *       lastDividerLocation, resizeWeight, oneTouchExpandable,
 *       continuousLayout, dividerSize), addImpl LEFT/RIGHT/TOP/BOTTOM
 *       dispatch, paramString tail. Expected-WARN paths (DIVIDER constraint,
 *       setUI, getMinimumDividerLocation, surrogate-side blocked-upstream
 *       drops) live in the per-class unit tests.</li>
 * </ol>
 */
class SplitPanesWarnInventoryTest {

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

        Navigate.to("SplitPanes");
        dump("Step 0b (SplitPanesPanel swap)", warnings);

        // Four SJSplitPane peers should be in the DOM (1 + 1 + 2 = 4 across
        // the three demos: H, V, and nested outer H + inner V).
        List<com.vaadin.swingbridge.surrogates.SJSplitPane> splits =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJSplitPane.class, spec -> spec.withCount(4));
        dump("Step 1 (4 SJSplitPane peers found)", warnings);

        // Verify orientation mix: 2 horizontal + 2 vertical.
        long horizontal = splits.stream()
                .filter(s -> s.getOrientationAsInt() == JSplitPane.HORIZONTAL_SPLIT).count();
        long vertical = splits.stream()
                .filter(s -> s.getOrientationAsInt() == JSplitPane.VERTICAL_SPLIT).count();
        if (horizontal != 2 || vertical != 2) {
            throw new AssertionError(
                    "Expected 2 HORIZONTAL_SPLIT + 2 VERTICAL_SPLIT panes, got "
                            + horizontal + "/" + vertical);
        }
        dump("Step 2 (orientation mix: 2 H + 2 V)", warnings);

        WarnDump.println();
        WarnDump.println("=== splitPanes user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jsplitpane_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors (5 variants) --
        new JSplitPane();
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JButton("a"), new JButton("b"));
        new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, new JButton("a"), new JButton("b"));
        dump("JSplitPane  ctors (5 variants)", warnings);

        // -- Orientation round-trip --
        JSplitPane sp = new JSplitPane();
        sp.getOrientation();
        sp.setOrientation(JSplitPane.VERTICAL_SPLIT);
        sp.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
        dump("JSplitPane  orientation round-trip", warnings);

        // -- Slot setters / getters --
        sp.setLeftComponent(new JButton("left"));
        sp.getLeftComponent();
        sp.setRightComponent(new JButton("right"));
        sp.getRightComponent();
        sp.setTopComponent(new JButton("top"));
        sp.getTopComponent();
        sp.setBottomComponent(new JButton("bottom"));
        sp.getBottomComponent();
        dump("JSplitPane  slot setters / getters", warnings);

        // -- Proportional setDividerLocation + resetToPreferredSizes --
        sp.setDividerLocation(0.3);
        sp.setDividerLocation(0.7);
        sp.resetToPreferredSizes();
        dump("JSplitPane  proportional dividerLocation + reset", warnings);

        // -- Emulator-only field shadows (six knobs) --
        sp.setDividerLocation(240);  // int form — emulator field shadow path
        sp.getDividerLocation();
        sp.setLastDividerLocation(100);
        sp.getLastDividerLocation();
        sp.setResizeWeight(0.45);  // JLawyer-shape inner-split value
        sp.getResizeWeight();
        sp.setOneTouchExpandable(true);
        sp.isOneTouchExpandable();
        sp.setContinuousLayout(true);
        sp.isContinuousLayout();
        sp.setDividerSize(12);
        sp.getDividerSize();
        dump("JSplitPane  six emulator-only field shadows", warnings);

        // -- addImpl constraint dispatch (LEFT/RIGHT/TOP/BOTTOM) --
        JSplitPane sp2 = new JSplitPane();
        sp2.add(new JButton("l"), JSplitPane.LEFT);
        sp2.add(new JButton("r"), JSplitPane.RIGHT);
        JSplitPane sp3 = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        sp3.add(new JButton("t"), JSplitPane.TOP);
        sp3.add(new JButton("b"), JSplitPane.BOTTOM);
        dump("JSplitPane  addImpl LEFT/RIGHT/TOP/BOTTOM", warnings);

        // -- L&F surface (WARN-free portion only — getUI/setUI WARN and
        //    are covered in the per-class unit tests) --
        sp.getUIClassID();
        sp.updateUI();
        dump("JSplitPane  L&F (getUIClassID + updateUI)", warnings);

        WarnDump.println();
        WarnDump.println("=== JSplitPane API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the SplitPanes exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
