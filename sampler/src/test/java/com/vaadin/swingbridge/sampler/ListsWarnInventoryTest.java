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

import com.github.mvysny.kaributesting.v10.GridKt;
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
import vaadinx.swing.DefaultListCellRenderer;
import vaadinx.swing.JList;

import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;
import java.util.ArrayList;
import java.util.List;

/**
 * ListsPanel (JList demo) WARN inventory exit gate. Both tests fail
 * if any {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape} fires.
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — drives the route end-to-end: click
 *       "Add document" → programmatic select → double-click-to-open (the
 *       JList idiom, via the item-double-click bridge + locationToIndex) →
 *       "Remove selected". Exercises the DefaultListModel mutation surface,
 *       the custom-renderer snapshot bridge, the selection-model wiring,
 *       and the JScrollPane auto-scroll guard around the Grid peer.</li>
 *   <li>{@link #inventory_jlist_api_surface} — micro-driver over the
 *       supported JList surface: ctors, model swap + PCE, selection-mode
 *       round-trip, selection state, renderer round-trip, layout/sizing
 *       round-trip, Scrollable, L&F stubs.</li>
 * </ol>
 */
class ListsWarnInventoryTest {

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
    @SuppressWarnings("unchecked")
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Lists");
        dump("Step 0b (ListsPanel swap)", warnings);

        // Reach the emulator JList via its SJList peer.
        com.vaadin.swingbridge.surrogates.SJList<Object> sList = LocatorJ._get(com.vaadin.swingbridge.surrogates.SJList.class);
        JList<String> list = (JList<String>) vaadinx.EHelper.getEmulator(sList);
        if (list.getModel().getSize() != 4) {
            throw new AssertionError("expected 4 seed documents, got " + list.getModel().getSize());
        }

        // Add a document — DefaultListModel.addElement → intervalAdded →
        // surrogate refreshes the Grid.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add document")));
        if (list.getModel().getSize() != 5) {
            throw new AssertionError("expected 5 documents after add, got " + list.getModel().getSize());
        }
        dump("Step 1 (add document)", warnings);

        // Programmatic selection — fires the ListSelectionListener that
        // updates the status label and pushes selection to the Grid.
        list.setSelectedIndex(0);
        if (list.getSelectedIndex() != 0) {
            throw new AssertionError("expected row 0 selected, got " + list.getSelectedIndex());
        }
        dump("Step 2 (select row 0)", warnings);

        // Double-click-to-open: the canonical JList idiom. item-double-click
        // → surrogate bridge → emulator MouseEvent (clickCount=2) → user's
        // mouseClicked → locationToIndex(point) resolves to the clicked row.
        GridKt._doubleClickItem(sList, 1, 1, false, false, false, false);
        dump("Step 3 (double-click row 1 to open)", warnings);

        // Remove the selected document.
        list.setSelectedIndex(0);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Remove selected")));
        if (list.getModel().getSize() != 4) {
            throw new AssertionError("expected 4 documents after remove, got " + list.getModel().getSize());
        }
        dump("Step 4 (remove selected)", warnings);

        WarnDump.println();
        WarnDump.println("=== lists user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jlist_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Ctors --
        JList<String> jl = new JList<>();
        dump("JList  new JList()", warnings);

        DefaultListModel<String> dm = new DefaultListModel<>();
        dm.addElement("a");
        dm.addElement("b");
        dm.addElement("c");
        new JList<>(dm);
        dump("JList  new JList(ListModel)", warnings);

        new JList<>(new String[] { "x", "y" });
        dump("JList  new JList(E[])", warnings);

        new JList<>(new java.util.Vector<>(List.of("p", "q")));
        dump("JList  new JList(Vector)", warnings);

        // -- Model swap + PCE / setListData --
        jl.setModel(dm);
        jl.getModel();
        jl.setListData(new String[] { "1", "2", "3", "4" });
        dump("JList  setModel / getModel / setListData", warnings);

        // -- Selection mode round-trip --
        jl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jl.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        jl.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        jl.getSelectionMode();
        dump("JList  setSelectionMode round-trip", warnings);

        // -- Selection state --
        jl.setSelectedIndex(0);
        jl.setSelectedIndices(new int[] { 0, 2 });
        jl.getSelectedIndex();
        jl.getSelectedIndices();
        jl.getSelectedValue();
        jl.getSelectedValuesList();
        jl.getMinSelectionIndex();
        jl.getMaxSelectionIndex();
        jl.getAnchorSelectionIndex();
        jl.getLeadSelectionIndex();
        jl.isSelectedIndex(0);
        jl.isSelectionEmpty();
        jl.setSelectedValue("2", false);
        jl.setSelectionInterval(0, 1);
        jl.addSelectionInterval(2, 2);
        jl.removeSelectionInterval(0, 0);
        jl.getValueIsAdjusting();
        jl.setValueIsAdjusting(false);
        jl.clearSelection();
        dump("JList  selection state accessors", warnings);

        // -- Selection model swap --
        jl.setSelectionModel(new javax.swing.DefaultListSelectionModel());
        jl.getSelectionModel();
        dump("JList  selection model swap", warnings);

        // -- Listener registration --
        javax.swing.event.ListSelectionListener lsl = e -> { };
        jl.addListSelectionListener(lsl);
        jl.getListSelectionListeners();
        jl.removeListSelectionListener(lsl);
        vaadinx.awt.event.MouseAdapter ma = new vaadinx.awt.event.MouseAdapter() { };
        jl.addMouseListener(ma);
        jl.removeMouseListener(ma);
        dump("JList  listener registration", warnings);

        // -- Renderer round-trip (default + custom) --
        if (!(jl.getCellRenderer() instanceof DefaultListCellRenderer)) {
            throw new AssertionError("default cell renderer should be a DefaultListCellRenderer");
        }
        jl.setCellRenderer(new DefaultListCellRenderer());
        dump("JList  cell renderer round-trip", warnings);

        // -- Hit testing (R_layouts_close_enough — no WARN; returns stash/-1/null) --
        jl.locationToIndex(new java.awt.Point(0, 0));
        jl.indexToLocation(0);
        jl.getCellBounds(0, 0);
        jl.getFirstVisibleIndex();
        jl.getLastVisibleIndex();
        jl.ensureIndexIsVisible(0);
        dump("JList  hit testing / visible-index", warnings);

        // -- Layout orientation (VERTICAL only — wrap WARNs by design) + sizing --
        jl.setLayoutOrientation(JList.VERTICAL);
        jl.getLayoutOrientation();
        jl.setVisibleRowCount(10);
        jl.getVisibleRowCount();
        jl.setFixedCellWidth(120);
        jl.getFixedCellWidth();
        jl.setFixedCellHeight(22);
        jl.getFixedCellHeight();
        jl.getPrototypeCellValue();
        jl.setPrototypeCellValue(null);
        dump("JList  layout / sizing round-trip", warnings);

        // -- Selection chrome (round-trip-only) --
        jl.setSelectionForeground(java.awt.Color.BLUE);
        jl.getSelectionForeground();
        jl.setSelectionBackground(java.awt.Color.YELLOW);
        jl.getSelectionBackground();
        dump("JList  selection chrome round-trip", warnings);

        // -- Drag (round-trip-only; false doesn't WARN) --
        jl.setDragEnabled(false);
        jl.getDragEnabled();
        dump("JList  dragEnabled(false)", warnings);

        // -- Scrollable interface --
        jl.getPreferredScrollableViewportSize();
        jl.getScrollableUnitIncrement(new java.awt.Rectangle(0, 0, 100, 100),
                javax.swing.SwingConstants.VERTICAL, 1);
        jl.getScrollableBlockIncrement(new java.awt.Rectangle(0, 0, 100, 100),
                javax.swing.SwingConstants.VERTICAL, 1);
        jl.getScrollableTracksViewportWidth();
        jl.getScrollableTracksViewportHeight();
        dump("JList  Scrollable interface", warnings);

        // -- L&F stubs --
        jl.getUIClassID();
        jl.updateUI();
        jl.getUI();
        dump("JList  L&F stubs", warnings);

        WarnDump.println();
        WarnDump.println("=== JList API-surface WARN total: " + warnings.size() + " ===");
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
                    + " stub WARN(s) fired — regression in the Lists exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
