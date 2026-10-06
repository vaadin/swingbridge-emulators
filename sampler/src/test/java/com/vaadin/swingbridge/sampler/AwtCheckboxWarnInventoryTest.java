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
import com.vaadin.swingbridge.surrogates.SCheckbox;
import vaadinx.EHelper;

import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.Checkbox} + {@link vaadinx.awt.CheckboxGroup} WARN
 * inventory exit gate — D_awt_checkbox / SD_scheckbox (see {@code D_awt_lane}). Every
 * test fails if any {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "Checkbox" leaf,
 *       asserts six {@link SCheckbox} peers across both demos, then toggles them
 *       — ungrouped first (a browser toggle beside a {@code setState} button that
 *       deliberately fires nothing), then as radios, including the vetoed
 *       re-click and the live {@code setCheckboxGroup} glyph flip.</li>
 *   <li>{@link #inventory_awt_checkbox_api_surface} — micro-driver over
 *       {@code java.awt.Checkbox}: all five ctors including both group-argument
 *       orders, label and state including null and the in-group veto,
 *       {@code getSelectedObjects} in both states, {@code setCheckboxGroup}
 *       through install → re-home → same → null, listeners with null arguments,
 *       {@code addNotify} and {@code toString} — then {@code CheckboxGroup}'s own
 *       six members. Exhaustive over both classes minus
 *       {@code getAccessibleContext}, an expected WARN covered in
 *       {@code vaadinx.awt.CheckboxTest}.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtCheckboxWarnInventoryTest {

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

        Navigate.to("Checkbox");
        dump("Step 0b (AwtCheckboxPanel swap)", warnings);

        // Six SCheckbox peers across the two demos (two ungrouped, three
        // radios, one joiner). The Swing JCheckBoxes elsewhere in the Sampler
        // peer on SJCheckBox, so the count isolates the AWT lane.
        LocatorJ._find(SCheckbox.class, spec -> spec.withCount(6));
        dump("Step 1 (6 SCheckbox peers found)", warnings);

        // A browser toggle is the only path that posts an ItemEvent at all.
        LocatorJ._setValue(
                LocatorJ._get(SCheckbox.class, spec -> spec.withLabel("Verbose logging")), true);
        dump("Step 2 (Demo 1: browser toggle posts one ItemEvent)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("setState(!state) — fires nothing")));
        dump("Step 3 (Demo 1: setState is silent)", warnings);

        // Demo 2 — the group. Selecting a second radio cascades the first off
        // with no event of its own; re-clicking the selection is vetoed and
        // bounces the peer back.
        LocatorJ._setValue(
                LocatorJ._get(SCheckbox.class, spec -> spec.withLabel("imperial")), true);
        dump("Step 4 (Demo 2: radio pick cascades the sibling off)", warnings);
        LocatorJ._setValue(
                LocatorJ._get(SCheckbox.class, spec -> spec.withLabel("imperial")), false);
        dump("Step 5 (Demo 2: re-click on the selection is vetoed)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("setSelectedCheckbox(nautical) — fires nothing")));
        dump("Step 6 (Demo 2: the group moves silently)", warnings);

        // The glyph flip: setCheckboxGroup at runtime, both directions.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Join / leave the group")));
        dump("Step 7 (Demo 2: setCheckboxGroup installs the group)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Join / leave the group")));
        dump("Step 8 (Demo 2: setCheckboxGroup removes it again)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT Checkbox user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_awt_checkbox_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- All five ctors, both group-argument orders, null labels --
        vaadinx.awt.CheckboxGroup g = new vaadinx.awt.CheckboxGroup();
        new vaadinx.awt.Checkbox();
        new vaadinx.awt.Checkbox("Verbose");
        new vaadinx.awt.Checkbox(null);
        new vaadinx.awt.Checkbox("Verbose", true);
        new vaadinx.awt.Checkbox("metric", true, g);
        new vaadinx.awt.Checkbox("imperial", g, false);
        dump("Checkbox  all five ctors (both group-arg orders, null label)", warnings);

        // -- label --
        vaadinx.awt.Checkbox c = new vaadinx.awt.Checkbox("Verbose");
        c.getLabel();
        c.setLabel("Verbose logging");
        c.setLabel(null);
        c.setLabel("Verbose");
        dump("Checkbox  label round-trip (including null)", warnings);

        // -- state + getSelectedObjects, in both states and in a group --
        c.getState();
        c.getSelectedObjects();
        c.setState(true);
        c.getSelectedObjects();
        c.setState(false);
        dump("Checkbox  state + getSelectedObjects, checked and not", warnings);

        // -- group: install, re-home, remove; setState vetoed inside one --
        vaadinx.awt.CheckboxGroup other = new vaadinx.awt.CheckboxGroup();
        c.setCheckboxGroup(g);
        c.getCheckboxGroup();
        c.setState(true);
        c.setState(false);          // vetoed — c is the group's selection
        c.setCheckboxGroup(other);
        c.setCheckboxGroup(other);  // same group — early return
        c.setCheckboxGroup(null);
        dump("Checkbox  setCheckboxGroup install / re-home / same / null", warnings);

        // -- listeners --
        ItemListener l = e -> {};
        c.addItemListener(l);
        c.addItemListener(null);
        c.getItemListeners();
        c.getListeners(ItemListener.class);
        c.removeItemListener(l);
        c.removeItemListener(null);
        // processEvent / processItemEvent are protected, so they can't be
        // driven from here; vaadinx.awt.CheckboxTest covers both (same
        // package) including the peel-ItemEvent-off-first routing and the
        // R_no_vaadin_in_api limb 2 guard that the peer bridge enters at processEvent.
        dump("Checkbox  ItemListener add/remove/query (null args included)", warnings);

        // -- peer lifecycle + toString shape --
        c.addNotify();
        c.toString();
        dump("Checkbox  addNotify + toString (paramString)", warnings);

        // -- CheckboxGroup's own six members --
        vaadinx.awt.Checkbox mine = new vaadinx.awt.Checkbox("mine", true, g);
        vaadinx.awt.Checkbox foreign = new vaadinx.awt.Checkbox("foreign", false, other);
        g.getSelectedCheckbox();
        g.getCurrent();
        g.setSelectedCheckbox(mine);
        g.setCurrent(mine);
        g.setSelectedCheckbox(foreign);  // foreign box — a silent no-op
        g.setSelectedCheckbox(null);
        g.toString();
        dump("CheckboxGroup  all six members (foreign no-op + null included)", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.Checkbox API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT Checkbox exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
