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
import com.vaadin.swingbridge.surrogates.SScrollbar;
import vaadinx.EHelper;

import java.awt.Adjustable;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.Scrollbar} WARN inventory exit gate — D_awt_scrollbar / SD_sscrollbar; the
 * AWT lane's rationale is {@code ideas/awt-widgets.md}. Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "Scrollbar" leaf,
 *       asserts three {@link SScrollbar} peers across the three demos, drags the
 *       colour knob, and clicks through the clamping bench (each button a
 *       programmatic write, which fires no {@code AdjustmentEvent}).</li>
 *   <li>{@link #inventory_awt_scrollbar_api_surface} — micro-driver over
 *       {@code java.awt.Scrollbar}: all three ctors, {@code setValues} and each
 *       of the four single-property setters with in-band, out-of-band and
 *       extreme-int arguments, both increments through <em>both</em> name pairs
 *       so the alias funnels are on the asserted path, {@code getVisible},
 *       orientation both ways, {@code valueIsAdjusting}, the listener family
 *       including nulls, {@code addNotify} and {@code toString}. Exhaustive over
 *       the class minus {@code getAccessibleContext}, an expected WARN covered
 *       in {@code vaadinx.awt.ScrollbarTest}.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtScrollbarWarnInventoryTest {

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

        Navigate.to("Scrollbar");
        dump("Step 0b (AwtScrollbarPanel swap)", warnings);

        // Three SScrollbar peers, one per demo. The Swing JSliders elsewhere in
        // the Sampler peer on SJSlider, so the count isolates the AWT lane.
        LocatorJ._find(SScrollbar.class, spec -> spec.withCount(3));
        dump("Step 1 (3 SScrollbar peers found)", warnings);

        // Demo 1 — a from-client value change is the only path that posts an
        // AdjustmentEvent at all, and it drives the Label tint.
        List<SScrollbar> bars = LocatorJ._find(SScrollbar.class, spec -> spec.withCount(3));
        LocatorJ._setValue(bars.get(0), 200d);
        dump("Step 2 (Demo 1: browser drag posts TRACK and tints the swatch)", warnings);

        // Demo 2 — the vertical bar. Its *rendering* is browser-only; what is
        // assertable here is that a value change round-trips through the
        // vertical peer without a stub fire.
        LocatorJ._setValue(bars.get(1), 60d);
        dump("Step 3 (Demo 2: the VERTICAL peer takes a value change)", warnings);

        // Demo 3 — every button is a programmatic write, so each refreshes its
        // own readout and fires nothing.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("setValue(999)")));
        dump("Step 4 (Demo 3: setValue clamps to the band's top)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("setVisibleAmount(50)")));
        dump("Step 5 (Demo 3: visibleAmount narrows the band)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("setMaximum(20) below the value")));
        dump("Step 6 (Demo 3: setMaximum drags minimum and value with it)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("setValues(40, 10, 0, 100)")));
        dump("Step 7 (Demo 3: setValues resets the band)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT Scrollbar user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_awt_scrollbar_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- All three ctors --
        new vaadinx.awt.Scrollbar();
        new vaadinx.awt.Scrollbar(vaadinx.awt.Scrollbar.HORIZONTAL);
        new vaadinx.awt.Scrollbar(vaadinx.awt.Scrollbar.VERTICAL, 30, 5, 10, 60);
        dump("Scrollbar  all three ctors", warnings);

        // -- setValues and the four single setters, in-band and out --
        vaadinx.awt.Scrollbar s = new vaadinx.awt.Scrollbar(
                vaadinx.awt.Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        s.setValues(50, 10, 0, 100);
        s.setValue(30);
        s.setValue(999);
        s.setValue(-999);
        s.setMinimum(5);
        s.setMinimum(Integer.MAX_VALUE);
        s.setMaximum(80);
        s.setMaximum(Integer.MIN_VALUE);
        s.setVisibleAmount(20);
        s.setVisibleAmount(0);
        s.setVisibleAmount(Integer.MAX_VALUE);
        s.setValues(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        s.setValues(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        s.getValue();
        s.getMinimum();
        s.getMaximum();
        s.getVisibleAmount();
        dump("Scrollbar  setValues + four setters (in-band, out-of-band, extreme ints)", warnings);

        // -- Both increments through BOTH name pairs: the deprecated AWT-1.0
        // names are the JDK's real implementations, so they are on the path.
        s.setUnitIncrement(16);
        s.getUnitIncrement();
        s.setLineIncrement(4);
        s.getLineIncrement();
        s.setUnitIncrement(0);
        s.setBlockIncrement(25);
        s.getBlockIncrement();
        s.setPageIncrement(40);
        s.getPageIncrement();
        s.setBlockIncrement(-5);
        s.getVisible();
        dump("Scrollbar  both increments through both name pairs + getVisible", warnings);

        // -- orientation, both ways, through the emulator's int API --
        s.getOrientation();
        s.setOrientation(vaadinx.awt.Scrollbar.VERTICAL);
        s.setOrientation(vaadinx.awt.Scrollbar.VERTICAL);   // unchanged: early return
        s.setOrientation(vaadinx.awt.Scrollbar.HORIZONTAL);
        dump("Scrollbar  orientation get/set both ways (including the no-op)", warnings);

        // -- valueIsAdjusting --
        s.getValueIsAdjusting();
        s.setValueIsAdjusting(true);
        s.setValueIsAdjusting(false);
        dump("Scrollbar  valueIsAdjusting get/set", warnings);

        // -- the listener family, nulls included --
        AdjustmentListener l = e -> {};
        s.addAdjustmentListener(l);
        s.addAdjustmentListener(null);
        s.getAdjustmentListeners();
        s.getListeners(AdjustmentListener.class);
        s.removeAdjustmentListener(l);
        s.removeAdjustmentListener(null);
        dump("Scrollbar  listener add/remove/get (null arguments included)", warnings);

        // -- Adjustable as the interface migrated code holds it through --
        Adjustable a = s;
        a.getOrientation();
        a.getValue();
        a.setValue(20);
        a.getMinimum();
        a.getMaximum();
        a.getVisibleAmount();
        a.setVisibleAmount(10);
        a.getUnitIncrement();
        a.setUnitIncrement(2);
        a.getBlockIncrement();
        a.setBlockIncrement(20);
        a.addAdjustmentListener(l);
        a.removeAdjustmentListener(l);
        dump("Scrollbar  the whole java.awt.Adjustable surface", warnings);

        // -- inherited Component surface + toString --
        s.addNotify();
        s.setName("zoom");
        s.getName();
        s.setEnabled(false);
        s.setEnabled(true);
        s.toString();
        dump("Scrollbar  addNotify, name, enabled, toString", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.Scrollbar API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT Scrollbar exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
