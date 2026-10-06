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
import com.vaadin.swingbridge.surrogates.SChoice;
import vaadinx.EHelper;

import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.Choice} WARN inventory exit gate — D_awt_choice / SD_schoice (see
 * {@code D_awt_lane}). Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "Choice" leaf,
 *       asserts the one {@link SChoice} peer, makes a browser pick of a
 *       deliberately duplicated item (the case index identity exists for), then
 *       clicks through {@code select()}-is-silent, insert-re-selects-0,
 *       remove-adjusts and {@code removeAll()}.</li>
 *   <li>{@link #inventory_awt_choice_api_surface} — micro-driver over the whole
 *       {@code java.awt.Choice} surface: the item family including both
 *       deprecated aliases, all three selection reads, both {@code select}
 *       overloads, listeners, {@code addNotify} and {@code toString}. Exhaustive
 *       over the class minus {@code getAccessibleContext}, which is an expected
 *       WARN and lives in {@code vaadinx.awt.ChoiceTest}.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtChoiceWarnInventoryTest {

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

        Navigate.to("Choice");
        dump("Step 0b (AwtChoicePanel swap)", warnings);

        // A browser pick first: the only path that fires an ItemEvent at all,
        // and the one that exercises the duplicate-item entry index identity
        // exists for.
        SChoice choice = LocatorJ._get(SChoice.class);
        dump("Step 1 (the SChoice peer)", warnings);
        LocatorJ._setValue(choice, 2); // the second "imperial" — the duplicate
        dump("Step 2 (browser pick of the duplicate item)", warnings);

        // Then the mutators, in an order that leaves the Choice empty last so
        // getSelectedObjects() renders as null rather than [].
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("select(2) — fires nothing")));
        dump("Step 3 (select() is silent)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("insert(\"New\", 0)")));
        dump("Step 4 (insert re-selects index 0)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("remove(0)")));
        dump("Step 5 (remove adjusts the selection)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("removeAll()")));
        dump("Step 6 (empty Choice, getSelectedObjects() == null)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT Choice user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_awt_choice_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctor + the empty-Choice reads --
        vaadinx.awt.Choice c = new vaadinx.awt.Choice();
        c.getItemCount();
        c.countItems();
        c.getSelectedIndex();
        c.getSelectedItem();
        c.getSelectedObjects();
        dump("Choice  ctor + empty-state reads (selectedObjects is null here)", warnings);

        // -- items, both the modern names and the deprecated aliases --
        c.add("metric");
        c.addItem("imperial");
        c.insert("nautical", 1);
        c.insert("clamped past the end", 99);
        c.getItem(0);
        c.getItemCount();
        c.countItems();
        dump("Choice  add / addItem / insert (including the clamp) / getItem", warnings);

        // -- selection, both overloads --
        c.select(2);
        c.select("metric");
        c.getSelectedIndex();
        c.getSelectedItem();
        c.getSelectedObjects();
        dump("Choice  select by index + by string, then all three reads", warnings);

        // -- listeners --
        ItemListener l = e -> {};
        c.addItemListener(l);
        c.getItemListeners();
        c.getListeners(ItemListener.class);
        c.removeItemListener(l);
        // processEvent / processItemEvent are protected, so they can't be
        // driven from here; vaadinx.awt.ChoiceTest covers both (same package)
        // including the peel-ItemEvent-off-first routing and the three R_no_vaadin_in_api
        // limb 2 call directions.
        dump("Choice  ItemListener add/remove/query", warnings);

        // -- removal, ending empty --
        c.remove("metric");
        c.remove(0);
        c.removeAll();
        c.getSelectedIndex();
        dump("Choice  remove by value / by position / removeAll", warnings);

        // -- peer lifecycle + toString shape --
        c.addNotify();
        c.toString();
        dump("Choice  addNotify + toString (paramString)", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.Choice API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT Choice exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
