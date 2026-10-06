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
import com.vaadin.swingbridge.surrogates.SList;
import vaadinx.EHelper;

import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.List} WARN inventory exit gate — D_awt_list / SD_slist (see
 * {@code D_awt_lane}). Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "List" leaf,
 *       asserts the three {@link SList} peers, fires a browser selection and a
 *       double-click, then clicks through the silent-select, mode-flip and
 *       mutator buttons. Also asserts the readout the silent-select button
 *       must <em>not</em> move, so a regression that starts firing on
 *       programmatic selects fails the build rather than passing quietly.</li>
 *   <li>{@link #inventory_awt_list_api_surface} — micro-driver over the whole
 *       {@code java.awt.List} surface, every deprecated alias included.
 *       Exhaustive over the class minus {@code getAccessibleContext} (an
 *       expected WARN, covered in {@code vaadinx.awt.ListTest}) and the three
 *       protected {@code process*} hooks, which are not reachable from this
 *       package — the same carve-out the Choice bucket documents.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtListWarnInventoryTest {

    private static final String SELECTED_INDICES =
            "[...event.target.selectedOptions].map(o=>o.index).join(',')";

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
        java.util.List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("List");
        dump("Step 0b (AwtListPanel swap)", warnings);

        java.util.List<SList> peers = LocatorJ._find(SList.class);
        if (peers.size() != 3) {
            throw new AssertionError("expected exactly 3 SList peers on the pane, got " + peers.size());
        }
        dump("Step 1 (the three SList peers)", warnings);

        // A browser selection: the only path that fires an ItemEvent at all.
        fireChange(peers.get(0), 1);
        dump("Step 2 (browser selection, single mode)", warnings);
        fireDoubleClick(peers.get(0), 1);
        dump("Step 3 (browser double-click → ActionEvent)", warnings);

        // The load-bearing assertion of this pane: the ItemEvent readout must
        // not move when the program selects. Capture it, click, compare.
        String before = itemReadoutText();
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("select(2) — the ItemEvent readout must not move")));
        dump("Step 4 (select() is silent)", warnings);
        if (!before.equals(itemReadoutText())) {
            throw new AssertionError(
                    "programmatic select fired an ItemEvent — java.awt.List must be silent. Readout moved from ["
                            + before + "] to [" + itemReadoutText() + "]");
        }

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("makeVisible(4)")));
        dump("Step 5 (makeVisible + getVisibleIndex)", warnings);

        // Multiple mode: two rows selected, then both flips.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("select(0) + select(2)")));
        dump("Step 6 (two rows selected — getSelectedIndex() is -1)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("setMultipleMode(false)")));
        dump("Step 7 (flip to single mode)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("setMultipleMode(true)")));
        dump("Step 8 (flip back to multiple)", warnings);

        // Mutators, ending empty so getSelectedItems() renders as [].
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("add(\"Date\")")));
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("add(\"New\", 0) — shifts the selection")));
        LocatorJ._click(LocatorJ._get(Button.class,
                spec -> spec.withText("add(null, 99) — appends \"\"")));
        dump("Step 9 (append, insert-with-shift, both coercions)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("replaceItem(\"Banana\", 0)")));
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("remove(0)")));
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("removeAll()")));
        dump("Step 10 (replace, remove, removeAll → empty)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT List user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_awt_list_api_surface() {
        java.util.List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- all three ctors, and the empty-state reads --
        new vaadinx.awt.List();
        new vaadinx.awt.List(6);
        vaadinx.awt.List l = new vaadinx.awt.List(5, true);
        l.getItemCount();
        l.countItems();
        l.getItems();
        l.getSelectedIndex();
        l.getSelectedIndexes();
        l.getSelectedItem();
        l.getSelectedItems();
        l.getSelectedObjects();
        dump("List  three ctors + empty-state reads (selectedObjects is [] here)", warnings);

        // -- items: both arities, both names, plus both coercions --
        l.add("Mercury");
        l.addItem("Venus");
        l.add("Earth", 1);
        l.addItem("Mars", 0);
        l.add((String) null);              // coerced to ""
        l.add("appended", 99);             // out-of-range index appends
        l.getItem(0);
        l.getItems();
        l.getItemCount();
        l.countItems();
        l.replaceItem("Mercury II", 0);
        dump("List  add / addItem in both arities, the two coercions, replaceItem", warnings);

        // -- selection, all five getters plus both mutators --
        l.select(1);
        l.select(2);
        l.isIndexSelected(1);
        l.isSelected(1);
        l.deselect(1);
        l.getSelectedIndex();
        l.getSelectedIndexes();
        l.getSelectedItem();
        l.getSelectedItems();
        l.getSelectedObjects();
        dump("List  select / deselect / isIndexSelected / isSelected + all five reads", warnings);

        // -- rows, mode, scrolling --
        l.getRows();
        l.isMultipleMode();
        l.allowsMultipleSelections();
        l.setMultipleMode(false);
        l.setMultipleSelections(true);
        l.getVisibleIndex();
        l.makeVisible(2);
        l.getVisibleIndex();
        dump("List  rows / mode (both name pairs) / makeVisible", warnings);

        // -- all eight size forms, including the deprecated aliases --
        l.getPreferredSize();
        l.getPreferredSize(3);
        l.preferredSize();
        l.preferredSize(3);
        l.getMinimumSize();
        l.getMinimumSize(3);
        l.minimumSize();
        l.minimumSize(3);
        dump("List  the eight size forms", warnings);

        // -- listeners, both types --
        ItemListener il = e -> {};
        ActionListener al = e -> {};
        l.addItemListener(il);
        l.addActionListener(al);
        l.getItemListeners();
        l.getActionListeners();
        l.getListeners(ItemListener.class);
        l.getListeners(ActionListener.class);
        l.removeItemListener(il);
        l.removeActionListener(al);
        // processEvent / processItemEvent / processActionEvent are protected,
        // so they can't be driven from here; vaadinx.awt.ListTest covers all
        // three (same package), including the peel order and the nine R_no_vaadin_in_api
        // limb 2 call directions.
        dump("List  ItemListener + ActionListener add/remove/query", warnings);

        // -- removal, every name, ending empty --
        l.remove("Venus");
        l.remove(0);
        l.delItem(0);
        l.delItems(0, 0);
        l.clear();
        l.add("only");
        l.removeAll();
        l.getItemCount();
        dump("List  remove by value / by position / delItem / delItems / clear / removeAll", warnings);

        // -- peer lifecycle + toString shape --
        l.addNotify();
        l.removeNotify();
        l.toString();
        dump("List  addNotify + removeNotify + toString (paramString)", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.List API-surface WARN total across buckets above ===");
    }

    /** The DOM event a browser selection produces, with the joined indices. */
    private static void fireChange(SList peer, int... indices) {
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < indices.length; i++) {
            if (i > 0) {
                joined.append(',');
            }
            joined.append(indices[i]);
        }
        tools.jackson.databind.node.ObjectNode data =
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        data.put(SELECTED_INDICES, joined.toString());
        peer.getElement().getNode()
                .getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                .fireEvent(new com.vaadin.flow.dom.DomEvent(peer.getElement(), "change", data));
    }

    private static void fireDoubleClick(SList peer, int index) {
        tools.jackson.databind.node.ObjectNode data =
                tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        data.put("event.target.index", index);
        peer.getElement().getNode()
                .getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                .fireEvent(new com.vaadin.flow.dom.DomEvent(peer.getElement(), "dblclick", data));
    }

    /** The Demo 1 ItemEvent readout, found by its distinctive prefix. */
    private static String itemReadoutText() {
        for (com.vaadin.flow.component.html.Span span
                : LocatorJ._find(com.vaadin.flow.component.html.Span.class)) {
            String text = span.getText();
            if (text != null && (text.startsWith("ItemEvent:") || text.startsWith("no ItemEvent yet"))) {
                return text;
            }
        }
        throw new AssertionError("Demo 1's ItemEvent readout not found on the pane");
    }

    private static void dump(String banner, java.util.List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the AWT List exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
