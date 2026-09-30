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

package com.vaadin.swingbridge.surrogates;

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surrogate tests for {@link SJList} (SD_sjlist) — the Grid&lt;Integer&gt; +
 * ListModel-as-source-of-truth + ListSelectionModel bridge + item-click
 * → SMouseEvent double-click wire. Emulator delegation / re-source lives
 * in {@code vaadinx.swing.JListTest}.
 */
class SJListTest extends AbstractKaribuTest {

    /** clickCount paired with what locationToIndex resolved to during dispatch. */
    private record Click(int clickCount, int index) {
    }

    private static <E> SJList<E> attach(SJList<E> list) {
        UI.getCurrent().add(list);
        return list;
    }

    // --- Ctors + model -------------------------------------------------

    @Test
    @DisplayName("no-arg ctor installs empty DefaultListModel")
    void noArgCtorInstallsEmptyDefaultListModel() {
        SJList<String> list = new SJList<>();
        assertInstanceOf(DefaultListModel.class, list.getModel());
        assertEquals(0, list.getModel().getSize());
    }

    @Test
    @DisplayName("ListModel ctor installs the given model")
    void listModelCtorInstallsTheGivenModel() {
        DefaultListModel<String> m = new DefaultListModel<>();
        m.addElement("a");
        m.addElement("b");
        SJList<String> list = new SJList<>(m);
        assertEquals(m, list.getModel());
        assertEquals(2, list.getModel().getSize());
    }

    @Test
    @DisplayName("array ctor wraps in a non-mutable model")
    void arrayCtorWrapsInANonMutableModel() {
        SJList<String> list = new SJList<>(new String[]{"a", "b", "c"});
        assertEquals(3, list.getModel().getSize());
        assertEquals("b", list.getModel().getElementAt(1));
        assertFalse(list.getModel() instanceof DefaultListModel<?>,
                "array ctor must wrap in an immutable AbstractListModel");
    }

    @Test
    @DisplayName("vector ctor wraps in a non-mutable model")
    void vectorCtorWrapsInANonMutableModel() {
        SJList<String> list = new SJList<>(new Vector<>(List.of("x", "y")));
        assertEquals(2, list.getModel().getSize());
        assertEquals("y", list.getModel().getElementAt(1));
    }

    // --- Selection mode ------------------------------------------------

    @Test
    @DisplayName("default selection mode is MULTIPLE_INTERVAL")
    void defaultSelectionModeIsMultipleInterval() {
        SJList<String> list = new SJList<>(new String[]{"a", "b"});
        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, list.getJdkSelectionMode());
    }

    @Test
    @DisplayName("setSelectionMode SINGLE maps to Grid SINGLE and round-trips")
    void setSelectionModeSingleMapsToGridSingle() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertEquals(ListSelectionModel.SINGLE_SELECTION, list.getJdkSelectionMode());
    }

    @Test
    @DisplayName("setSelectionMode rejects invalid mode")
    void setSelectionModeRejectsInvalidMode() {
        SJList<String> list = new SJList<>(new String[]{"a"});
        assertThrows(IllegalArgumentException.class, () -> list.setSelectionMode(99));
    }

    // --- Selection bridge ----------------------------------------------

    @Test
    @DisplayName("model to peer — setSelectedIndex pushes to Grid selection")
    void modelToPeerSetSelectedIndexPushesToGridSelection() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        list.setSelectedIndex(1);
        assertEquals(Set.of(1), list.getSelectedItems());
        assertEquals(1, list.getSelectedIndex());
        assertEquals("b", list.getSelectedValue());
    }

    @Test
    @DisplayName("peer to model — Grid select writes through to the selection model")
    void peerToModelGridSelectWritesThroughToTheSelectionModel() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        GridKt._select(list, 2);
        assertEquals(2, list.getSelectedIndex());
        assertEquals("c", list.getSelectedValue());
        assertTrue(list.isSelectedIndex(2));
    }

    @Test
    @DisplayName("selection round-trip does not infinite-loop")
    void selectionRoundTripDoesNotInfiniteLoop() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        // model→peer then peer→model in sequence — preventPeerEvents guards.
        list.setSelectedIndex(0);
        GridKt._select(list, 2);
        assertEquals(2, list.getSelectedIndex());
        assertEquals(Set.of(2), list.getSelectedItems());
    }

    @Test
    @DisplayName("setSelectedIndices + getSelectedIndices round-trip (multi)")
    void setSelectedIndicesRoundTrip() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c", "d"}));
        list.setSelectedIndices(new int[]{0, 2});
        Set<Integer> got = Arrays.stream(list.getSelectedIndices()).boxed().collect(Collectors.toSet());
        assertEquals(Set.of(0, 2), got);
        assertEquals(List.of("a", "c"), list.getSelectedValuesList());
    }

    @Test
    @DisplayName("setSelectedValue selects the matching element")
    void setSelectedValueSelectsTheMatchingElement() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        list.setSelectedValue("c", false);
        assertEquals(2, list.getSelectedIndex());
        list.setSelectedValue(null, false);
        assertTrue(list.isSelectionEmpty());
    }

    @Test
    @DisplayName("setSelectedIndex past model size is a no-op (JDK contract)")
    void setSelectedIndexPastModelSizeIsANoOp() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b"}));
        list.setSelectedIndex(5);
        assertTrue(list.isSelectionEmpty());
    }

    @Test
    @DisplayName("clearSelection empties selection")
    void clearSelectionEmptiesSelection() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b"}));
        list.setSelectedIndex(1);
        list.clearSelection();
        assertTrue(list.isSelectionEmpty());
        assertEquals(-1, list.getSelectedIndex());
    }

    @Test
    @DisplayName("setSelectionModel swaps the model and re-subscribes")
    void setSelectionModelSwapsTheModelAndResubscribes() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        DefaultListSelectionModel fresh = new DefaultListSelectionModel();
        list.setSelectionModel(fresh);
        list.setSelectedIndex(1);
        assertTrue(fresh.isSelectedIndex(1));
    }

    // --- ListDataListener — model mutations refresh the Grid -----------

    @Test
    @DisplayName("DefaultListModel addElement grows the list")
    void defaultListModelAddElementGrowsTheList() {
        DefaultListModel<String> m = new DefaultListModel<>();
        m.addElement("a");
        SJList<String> list = attach(new SJList<>(m));
        m.addElement("b");
        m.addElement("c");
        assertEquals(3, list.getModel().getSize());
        assertEquals("c", list.getModel().getElementAt(2));
    }

    @Test
    @DisplayName("DefaultListModel removeElement shrinks the list and shifts selection")
    void defaultListModelRemoveElementShrinksTheList() {
        DefaultListModel<String> m = new DefaultListModel<>();
        m.addElement("a");
        m.addElement("b");
        m.addElement("c");
        SJList<String> list = attach(new SJList<>(m));
        list.setSelectedIndex(2);        // "c"
        m.remove(0);                     // drop "a" → "c" now at index 1
        assertEquals(2, list.getModel().getSize());
        assertEquals(1, list.getSelectedIndex());
        assertEquals("c", list.getSelectedValue());
    }

    @Test
    @DisplayName("setModel clears selection")
    void setModelClearsSelection() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        list.setSelectedIndex(1);
        DefaultListModel<String> fresh = new DefaultListModel<>();
        fresh.addElement("x");
        list.setModel(fresh);
        assertTrue(list.isSelectionEmpty());
        assertEquals(1, list.getModel().getSize());
    }

    // --- Double-click bridge (locationToIndex) -------------------------

    @Test
    @DisplayName("double-click fires MOUSE_CLICKED clickCount 2 and locationToIndex resolves")
    void doubleClickFiresMouseClickedClickCountTwo() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        List<Click> seen = new ArrayList<>();
        list.addMouseListener(new SMouseAdapter() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                seen.add(new Click(e.getClickCount(), list.locationToIndex(new Point(0, 0))));
            }
        });
        GridKt._doubleClickItem(list, 1);
        assertEquals(1, seen.size());
        assertEquals(2, seen.get(0).clickCount(), "double-click must surface clickCount=2");
        assertEquals(1, seen.get(0).index(), "locationToIndex must resolve to the clicked row during dispatch");
        // Stash cleared after the dispatch.
        assertEquals(-1, list.locationToIndex(new Point(0, 0)));
    }

    @Test
    @DisplayName("single-click fires MOUSE_CLICKED clickCount 1")
    void singleClickFiresMouseClickedClickCountOne() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b", "c"}));
        List<Integer> counts = new ArrayList<>();
        list.addMouseListener(new SMouseAdapter() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                counts.add(e.getClickCount());
            }
        });
        GridKt._clickItem(list, 2);
        assertEquals(List.of(1), counts);
    }

    @Test
    @DisplayName("locationToIndex is -1 outside a click dispatch")
    void locationToIndexIsMinusOneOutsideAClickDispatch() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b"}));
        assertEquals(-1, list.locationToIndex(new Point(10, 10)));
    }

    // --- Renderer (Vaadin-first) ---------------------------------------

    @Test
    @DisplayName("setRenderer replaces the single column")
    void setRendererReplacesTheSingleColumn() {
        SJList<String> list = attach(new SJList<>(new String[]{"a", "b"}));
        assertEquals(1, list.getColumns().size());
        list.setRenderer(new ComponentRenderer<>((Integer idx) -> new Span("row " + idx)));
        assertEquals(1, list.getColumns().size());
    }

    // --- Layout orientation + sizing (round-trip; WARN on wrap) --------

    @Test
    @DisplayName("layoutOrientation drops on the surrogate and rejects invalid")
    void layoutOrientationDropsOnTheSurrogate() {
        // Surrogate is Vaadin-first (R_vaadin_first): no shadow. Wrap drops + WARNs;
        // getter always returns VERTICAL. JDK round-trip lives on the emulator.
        SJList<String> list = new SJList<>(new String[]{"a"});
        assertEquals(SJList.VERTICAL, list.getLayoutOrientation());
        list.setLayoutOrientation(SJList.VERTICAL_WRAP);   // WARN + drop
        assertEquals(SJList.VERTICAL, list.getLayoutOrientation(), "surrogate drops wrap per R_vaadin_first");
        assertThrows(IllegalArgumentException.class, () -> list.setLayoutOrientation(7));
    }

    @Test
    @DisplayName("sizing + chrome knobs drop on the surrogate (emulator owns round-trip)")
    void sizingAndChromeKnobsDropOnTheSurrogate() {
        SJList<String> list = new SJList<>(new String[]{"a"});
        list.setVisibleRowCount(12);
        list.setFixedCellWidth(80);
        list.setFixedCellHeight(24);
        // No shadow cache — getters return JDK defaults regardless of what was set.
        assertEquals(8, list.getVisibleRowCount());
        assertEquals(-1, list.getFixedCellWidth());
        assertEquals(-1, list.getFixedCellHeight());
        assertNull(list.getSelectionForeground());
        assertNull(list.getSelectionBackground());
        assertFalse(list.getDragEnabled());
    }

    @Test
    @DisplayName("prototypeCellValue null is silent and returns null")
    void prototypeCellValueNullIsSilent() {
        SJList<String> list = new SJList<>(new String[]{"a"});
        list.setPrototypeCellValue(null);
        assertNull(list.getPrototypeCellValue());
    }

    @Test
    @DisplayName("getUIClassID is ListUI")
    void getUiClassIdIsListUi() {
        assertEquals("ListUI", new SJList<String>().getUIClassID());
    }
}
