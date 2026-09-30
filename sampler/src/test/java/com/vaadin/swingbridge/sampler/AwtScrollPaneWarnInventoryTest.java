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
import vaadinx.awt.Panel;
import vaadinx.awt.ScrollPane;
import vaadinx.awt.ScrollPaneAdjustable;

import java.awt.Adjustable;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.ScrollPane} WARN inventory exit gate — D_awt_scrollpane / SD_sscrollpane.
 * Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "ScrollPane" leaf
 *       and drives the scroll and replace-child controls.</li>
 *   <li>{@link #inventory_awt_scrollpane_api_surface} — micro-driver over the
 *       whole public surface of {@code java.awt.ScrollPane} plus
 *       {@code ScrollPaneAdjustable}, minus the four expected-WARN members
 *       listed on the method.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtScrollPaneWarnInventoryTest {

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

        Navigate.to("ScrollPane");
        dump("Step 0b (AwtScrollPanePanel swap)", warnings);

        // Three SScrollPane peers: the AS_NEEDED / NEVER pair in Demo 1 plus
        // the live pane in Demo 2. A deliberate tripwire — the count fails if a
        // demo is dropped.
        LocatorJ._find(com.vaadin.swingbridge.surrogates.SScrollPane.class, spec -> spec.withCount(3));
        dump("Step 1 (3 SScrollPane peers across Demos 1-2)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Scroll to 200")));
        dump("Step 2 (setScrollPosition + AdjustmentEvent fan-out)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Scroll to top")));
        dump("Step 3 (scroll back to origin)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Replace child")));
        dump("Step 4 (single-child replacement + offset re-push)", warnings);

        // Still three: the replacement swapped the live pane's child, not the
        // pane itself.
        LocatorJ._find(com.vaadin.swingbridge.surrogates.SScrollPane.class, spec -> spec.withCount(3));
        dump("Step 5 (peer count unchanged after replacement)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT ScrollPane user-path WARN total: " + warnings.size() + " ===");
    }

    /**
     * The whole public surface of {@code java.awt.ScrollPane} and
     * {@code java.awt.ScrollPaneAdjustable}. <b>Excluded on purpose</b>, each a
     * documented WARN whose inclusion would fail the gate for a
     * non-regression: {@code getAccessibleContext} (R_match_swing_errors sub-bucket (b));
     * {@code printComponents} (user-authored {@code Graphics} paint, also (b));
     * {@code setWheelScrollingEnabled(false)} (R_match_swing_errors sub-bucket (c) — CSS cannot
     * disable the wheel while keeping scrollbars draggable); and the
     * {@code SCROLLBARS_ALWAYS} ctor (R_match_swing_errors sub-bucket (a) — Vaadin's
     * {@code Scroller} has no force-show affordance).
     *
     * <p>Inherited {@code vaadinx.awt.Container} members are covered by
     * {@code AwtPanelWarnInventoryTest} and are not re-driven here, beyond the
     * few {@code ScrollPane} overrides or constrains.
     */
    @Test
    void inventory_awt_scrollpane_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- ctors: the two non-WARNing policies plus the no-arg chain --
        new ScrollPane();
        new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        new ScrollPane(ScrollPane.SCROLLBARS_NEVER);
        dump("ScrollPane  ctors (AS_NEEDED, NEVER, no-arg chain)", warnings);

        // -- the single-child rule, through add and remove --
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        sp.add(new Panel());            // replaces rather than appends
        sp.getComponent(0);
        sp.getComponents();
        sp.getComponentCount();
        dump("ScrollPane  add / replace / child access", warnings);

        // -- scroll position, both setters and the getter --
        sp.setScrollPosition(0, 200);
        sp.setScrollPosition(new Point(10, 20));
        sp.getScrollPosition();
        dump("ScrollPane  setScrollPosition ×2 + getScrollPosition", warnings);

        // -- geometry: JDK-faithful un-realized answers, none of them WARNs --
        sp.getViewportSize();
        sp.getHScrollbarHeight();
        sp.getVScrollbarWidth();
        sp.getScrollbarDisplayPolicy();
        dump("ScrollPane  viewport + scrollbar dimensions + policy", warnings);

        // -- wheel flag: only the `true` path is WARN-free --
        sp.isWheelScrollingEnabled();
        sp.setWheelScrollingEnabled(true);
        dump("ScrollPane  wheel flag (true path only)", warnings);

        // -- layout lifecycle: doLayout -> layout, plus the peer hook --
        sp.doLayout();
        sp.layout();
        sp.validate();
        sp.invalidate();
        sp.addNotify();
        sp.paramString();
        sp.toString();
        dump("ScrollPane  doLayout / layout / validate / addNotify / paramString", warnings);

        // -- removal clears the peer content slot --
        sp.remove(0);
        dump("ScrollPane  remove(int)", warnings);

        // -- ScrollPaneAdjustable: the whole surface bar the three AWTErrors --
        ScrollPane host = new ScrollPane();
        host.add(new Panel());
        for (Adjustable a : new Adjustable[] {host.getVAdjustable(), host.getHAdjustable()}) {
            a.getOrientation();
            a.getValue();
            a.setValue(120);
            a.getMinimum();
            a.getMaximum();
            a.getVisibleAmount();
            a.getUnitIncrement();
            a.setUnitIncrement(4);
            a.getBlockIncrement();
            a.setBlockIncrement(40);
            a.addAdjustmentListener(ev -> {});
            a.removeAdjustmentListener(ev -> {});

            ScrollPaneAdjustable spa = (ScrollPaneAdjustable) a;
            spa.getValueIsAdjusting();
            spa.setValueIsAdjusting(true);
            spa.setValueIsAdjusting(false);
            spa.getAdjustmentListeners();
            spa.paramString();
            spa.toString();
        }
        dump("ScrollPaneAdjustable  full surface on both axes", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.ScrollPane API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT ScrollPane exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
