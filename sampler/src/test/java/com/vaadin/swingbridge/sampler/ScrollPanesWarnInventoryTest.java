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
import com.vaadin.flow.component.orderedlayout.Scroller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JLabel;
import vaadinx.swing.JScrollBar;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JViewport;

import javax.swing.ScrollPaneConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * JScrollPane WARN inventory exit gate (D_jscrollpane). Both tests
 * fail if any {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape} fires
 * along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "ScrollPanes", and asserts {@link ScrollPanesPanel}
 *       constructs three demos (tall labels / JTextArea / JTable) without
 *       any stub fire. Validates the SD_sjscrollpane auto-scroll guard fires silently
 *       on the JTextArea + JTable cases (the WARN-free path means the
 *       guard's NONE-force on the surrogate doesn't go through
 *       {@code EHelper.onUnimplemented}) and the D_inline_route_sizing route-sizing model
 *       wires up cleanly (route setSizeFull → SJPanel 100% → BorderLayout
 *       grid track → JScrollPane → Grid).</li>
 *   <li>{@link #inventory_jscrollpane_api_surface} — micro-driver over the
 *       JScrollPane / JViewport / JScrollBar API buckets. The expected-
 *       WARN paths from D_jscrollpane (ALWAYS policy R_match_swing_errors sub-bucket (a),
 *       header / corner / setViewportBorder R_vaadin_first drop) are explicitly NOT
 *       exercised here; the goal is to lock in the WARN-free supported
 *       surface as a regression gate.</li>
 * </ol>
 */
class ScrollPanesWarnInventoryTest {

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
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("ScrollPanes");
        dump("Step 0b (ScrollPanesPanel swap)", warnings);

        // Three SJScrollPane peers should now be in the DOM (one per demo).
        // The instance count is asserted explicitly so a future refactor that
        // accidentally drops a demo trips this gate.
        List<com.vaadin.swingbridge.surrogates.SJScrollPane> panes =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJScrollPane.class, spec -> spec.withCount(3));
        dump("Step 1 (3 SJScrollPane peers found)", warnings);

        // Demo 1 wraps a tall JPanel of labels (non-auto-scrolling content).
        // Default policy → BOTH on the surrogate; auto-scroll guard does NOT
        // fire. Demo 2 wraps a JTextArea (TextArea peer); guard forces NONE.
        // Demo 3 wraps a JTable (Grid peer via SJTable); guard forces NONE.
        long bothCount = panes.stream()
                .filter(sp -> sp.getScrollDirection() == Scroller.ScrollDirection.BOTH).count();
        long noneCount = panes.stream()
                .filter(sp -> sp.getScrollDirection() == Scroller.ScrollDirection.NONE).count();
        if (bothCount != 1 || noneCount != 2) {
            throw new AssertionError(
                    "Expected 1 BOTH (tall labels) + 2 NONE (TextArea / Table) auto-scroll-guard outcomes, got BOTH="
                            + bothCount + " NONE=" + noneCount);
        }
        dump("Step 2 (auto-scroll guard outcomes asserted)", warnings);

        WarnDump.println();
        WarnDump.println("=== scrollpanes user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jscrollpane_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Ctors --
        new JScrollPane();
        dump("JScrollPane  new JScrollPane()", warnings);

        new JScrollPane(new JLabel("hi"));
        dump("JScrollPane  new JScrollPane(Component)", warnings);

        new JScrollPane(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        dump("JScrollPane  new JScrollPane(int, int)", warnings);

        new JScrollPane(new JLabel("hi"),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        dump("JScrollPane  new JScrollPane(Component, int, int)", warnings);

        // -- Viewport view round-trip + null-clear --
        JScrollPane sp = new JScrollPane();
        sp.setViewportView(new JLabel("body"));
        sp.getViewportView();
        sp.setViewportView(null);
        dump("JScrollPane  setViewportView round-trip + null-clear", warnings);

        // -- Scrollbar policy round-trip (NEVER + AS_NEEDED only — ALWAYS WARNs by design) --
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBarPolicy();
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.getHorizontalScrollBarPolicy();
        dump("JScrollPane  scrollbar policy round-trip (NEVER / AS_NEEDED)", warnings);

        // -- Wheel scrolling round-trip (always-on in browsers; field-only) --
        sp.setWheelScrollingEnabled(false);
        sp.isWheelScrollingEnabled();
        sp.setWheelScrollingEnabled(true);
        dump("JScrollPane  setWheelScrollingEnabled round-trip", warnings);

        // -- L&F surface --
        sp.getUIClassID();
        sp.updateUI();
        dump("JScrollPane  L&F stubs", warnings);

        // -- JViewport shadow surface --
        JViewport vp = sp.getViewport();
        vp.getView();
        vp.setView(new JLabel("v"));
        vp.getExtentSize();
        vp.setExtentSize(new java.awt.Dimension(100, 50));
        vp.getViewPosition();
        vp.setViewPosition(new java.awt.Point(0, 0));
        vp.getViewSize();
        vp.setViewSize(new java.awt.Dimension(200, 200));
        vp.getViewRect();
        vp.getInsets();
        vp.getInsets(null);
        vp.getScrollMode();
        vp.setScrollMode(JViewport.BLIT_SCROLL_MODE);
        vp.setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        vp.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        dump("JViewport  shadow component round-trip", warnings);

        javax.swing.event.ChangeListener vpListener = e -> {};
        vp.addChangeListener(vpListener);
        vp.getChangeListeners();
        vp.removeChangeListener(vpListener);
        dump("JViewport  ChangeListener add/get/remove", warnings);

        vp.getUIClassID();
        vp.updateUI();
        dump("JViewport  L&F stubs", warnings);

        // -- JScrollBar shadow surface --
        JScrollBar vsb = sp.getVerticalScrollBar();
        JScrollBar hsb = sp.getHorizontalScrollBar();
        vsb.getOrientation();
        hsb.getOrientation();
        vsb.setMinimum(0);
        vsb.setMaximum(200);
        vsb.setVisibleAmount(20);
        vsb.setValue(50);
        vsb.setUnitIncrement(16);
        vsb.setBlockIncrement(100);
        vsb.getMinimum();
        vsb.getMaximum();
        vsb.getVisibleAmount();
        vsb.getValue();
        vsb.getUnitIncrement();
        vsb.getBlockIncrement();
        vsb.setValueIsAdjusting(true);
        vsb.getValueIsAdjusting();
        vsb.setValueIsAdjusting(false);
        vsb.setValues(10, 5, 0, 100);
        dump("JScrollBar  BoundedRangeModel round-trip", warnings);

        java.awt.event.AdjustmentListener sbListener = e -> {};
        vsb.addAdjustmentListener(sbListener);
        vsb.getAdjustmentListeners();
        vsb.removeAdjustmentListener(sbListener);
        dump("JScrollBar  AdjustmentListener add/get/remove", warnings);

        vsb.getUIClassID();
        vsb.updateUI();
        dump("JScrollBar  L&F stubs", warnings);

        // -- User-supplied scrollbar / viewport instances (round-trip via field shadow) --
        JScrollBar custom = new JScrollBar(java.awt.Adjustable.HORIZONTAL);
        sp.setHorizontalScrollBar(custom);
        sp.getHorizontalScrollBar();
        sp.setVerticalScrollBar(new JScrollBar(java.awt.Adjustable.VERTICAL));
        sp.getVerticalScrollBar();
        dump("JScrollPane  setHorizontalScrollBar / setVerticalScrollBar round-trip", warnings);

        JViewport customVp = new JViewport();
        sp.setViewport(customVp);
        sp.getViewport();
        dump("JScrollPane  setViewport round-trip", warnings);

        WarnDump.println();
        WarnDump.println("=== JScrollPane API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the ScrollPanes exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
