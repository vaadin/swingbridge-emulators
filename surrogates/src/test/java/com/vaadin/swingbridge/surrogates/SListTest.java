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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SList surrogate — the AWT 1.0 {@code java.awt.List}, not
 * {@code JList}. Covers:
 *
 * <ol>
 *  <li>The {@code size} floor at 2, which is what keeps a {@code <select>} from rendering
 *      as a drop-down.
 *  <li>Items living as {@code <option>} children, duplicates included.
 *  <li>{@code addItem}'s two coercions and {@code removeItemRange}'s downward loop.
 *  <li>The selection mirror: single vs multiple, index shifting on mutation.
 *  <li>That every programmatic path is silent and only the browser fires.
 *  <li>Both event shapes — {@code ItemEvent} carrying an {@code Integer} index,
 *      {@code ActionEvent} carrying the item text.
 *  <li>Zero stub WARNs across the happy path.
 * </ol>
 *
 * <p>Karibu note the event tests rest on: there is no {@code _setValue} seam here
 * because the peer is a raw {@code <select>}, so {@link #dispatchChange} fires the DOM
 * event Flow would deliver, carrying the same joined-index payload the
 * client-side expression produces. That is the exact server-side seam; the
 * browser half of it is verified separately and recorded in SD_slist.
 */
class SListTest extends AbstractKaribuTest {

    private static final String SELECTED_INDICES =
            "[...event.target.selectedOptions].map(o=>o.index).join(',')";

    /** Fires the {@code change} a browser selection would, with the joined indices. */
    private static void dispatchChange(SList l, int... indices) {
        String joined = java.util.Arrays.stream(indices)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(","));
        fire(l, "change", SELECTED_INDICES, joined);
    }

    private static void dispatchDoubleClick(SList l, int index) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("event.target.index", index);
        fire(l, "dblclick", data);
    }

    /**
     * Flow re-checks a {@code setFilter} expression server-side, using the
     * expression itself as the event-data key, so the payload has to carry it
     * — a browser that filtered the event out would never have sent it.
     */
    private static void dispatchEnter(SList l, String key) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("event.key === 'Enter'", "Enter".equals(key));
        fire(l, "keydown", data);
    }

    private static void dispatchEnter(SList l) {
        dispatchEnter(l, "Enter");
    }

    private static void fire(SList l, String type, String key, String value) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put(key, value);
        fire(l, type, data);
    }

    private static void fire(SList l, String type, ObjectNode data) {
        l.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(l.getElement(), type, data));
    }

    private static SList listOfItems(String... items) {
        return listOfItems(false, items);
    }

    private static SList listOfItems(boolean multiple, String... items) {
        SList l = new SList(4, multiple);
        for (String item : items) {
            l.addItem(item, -1);
        }
        UI.getCurrent().add(l);
        return l;
    }

    /** The (index, stateChange) pairs an ItemEvent batch carried, order-independent. */
    private static Set<List<Integer>> indexAndState(List<ItemEvent> events) {
        return events.stream()
                .map(it -> List.of((Integer) it.getItem(), it.getStateChange()))
                .collect(Collectors.toSet());
    }

    // --- rendering contract ------------------------------------------

    @Test
    @DisplayName("the peer is a select whose size is floored at 2")
    void thePeerIsASelectWhoseSizeIsFlooredAtTwo() {
        assertEquals("select", new SList().getElement().getTag());
        // A <select size=1> without `multiple` renders as a drop-down, which
        // is the one thing a java.awt.List never is.
        assertEquals("2", new SList(1).getElement().getAttribute("size"));
        assertEquals("2", new SList(-3).getElement().getAttribute("size"));
        assertEquals("4", new SList(4).getElement().getAttribute("size"));
        assertEquals("9", new SList(9).getElement().getAttribute("size"));
    }

    @Test
    @DisplayName("multipleMode reads back off the peer with no shadow")
    void multipleModeReadsBackOffThePeer() {
        assertFalse(new SList(4, false).isMultipleMode());
        assertTrue(new SList(4, true).isMultipleMode());
        SList l = new SList(4, false);
        l.setMultipleMode(true);
        // The attribute, not the property: it has to be in the initial markup
        // or the browser auto-selects option 0 before `multiple` lands.
        assertTrue(l.getElement().hasAttribute("multiple"));
        assertTrue(l.isMultipleMode());
        l.setMultipleMode(false);
        assertFalse(l.getElement().hasAttribute("multiple"));
        // The companion half — pushing `selectedIndex = -1` when the selection
        // is empty — is an executeJs call with no server-side trace, so it is
        // verified in a browser instead (SD_slist) rather than asserted here.
    }

    // --- items --------------------------------------------------------

    @Test
    @DisplayName("items are option children, duplicates and all")
    void itemsAreOptionChildrenDuplicatesAndAll() {
        SList l = listOfItems("Apple", "Apple", "Cherry");
        assertEquals(3, l.getItemCount());
        assertEquals(3, l.getElement().getChildCount());
        assertEquals("option", l.getElement().getChild(0).getTag());
        assertArrayEquals(new String[]{"Apple", "Apple", "Cherry"}, l.getItemsArray());
        // The whole point of positional identity: two equal Strings stay two
        // independently addressable rows.
        l.select(0);
        assertTrue(l.isIndexSelected(0));
        assertFalse(l.isIndexSelected(1));
    }

    @Test
    @DisplayName("addItem coerces an out-of-range index to append and a null item to empty")
    void addItemCoercesOutOfRangeIndexAndNullItem() {
        SList l = listOfItems("a", "b");
        l.addItem("c", 99);
        l.addItem("d", -7);
        assertArrayEquals(new String[]{"a", "b", "c", "d"}, l.getItemsArray());
        l.addItem(null, 1);
        assertEquals("", l.getItemAt(1));
        assertEquals(5, l.getItemCount());
    }

    @Test
    @DisplayName("addItem inserts at the index and shifts the rest up")
    void addItemInsertsAtTheIndex() {
        SList l = listOfItems("a", "c");
        l.addItem("b", 1);
        assertArrayEquals(new String[]{"a", "b", "c"}, l.getItemsArray());
    }

    @Test
    @DisplayName("getItemAt throws AWT's array exception, not the plain superclass")
    void getItemAtThrowsAwtsArrayException() {
        SList l = listOfItems("a", "b");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.getItemAt(2));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.getItemAt(-1));
    }

    @Test
    @DisplayName("removeItemRange runs downwards, so a negative start mutates and then throws")
    void removeItemRangeRunsDownwards() {
        SList l = listOfItems("a", "b", "c");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.removeItemRange(-1, 2));
        // Already three items shorter — AWT's own quirk, reproduced.
        assertEquals(0, l.getItemCount());
    }

    @Test
    @DisplayName("removeItemRange with an end past the top throws before removing anything")
    void removeItemRangeWithEndPastTheTopThrowsFirst() {
        SList l = listOfItems("a", "b", "c");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.removeItemRange(1, 5));
        assertEquals(3, l.getItemCount());
    }

    @Test
    @DisplayName("removeAllItems empties the list and the selection")
    void removeAllItemsEmptiesTheListAndTheSelection() {
        SList l = listOfItems(true, "a", "b");
        l.select(0);
        l.select(1);
        l.removeAllItems();
        assertEquals(0, l.getItemCount());
        assertArrayEquals(new int[]{}, l.getSelectedIndexes());
    }

    @Test
    @DisplayName("indexOfItem finds the first occurrence")
    void indexOfItemFindsTheFirstOccurrence() {
        SList l = listOfItems("a", "b", "a");
        assertEquals(0, l.indexOfItem("a"));
        assertEquals(1, l.indexOfItem("b"));
        assertEquals(-1, l.indexOfItem("zzz"));
    }

    @Test
    @DisplayName("setItemsAndSelection keeps the unchanged rows and shows exactly what it is given")
    void setItemsAndSelectionKeepsTheUnchangedRows() {
        SList l = listOfItems(true, "a", "b", "c", "d");
        com.vaadin.flow.dom.Element keptHead = l.getElement().getChild(0);
        com.vaadin.flow.dom.Element keptTail = l.getElement().getChild(3);
        List<ItemEvent> events = new ArrayList<>();
        l.addItemListener(events::add);

        // One row replaced in the middle; out-of-range and unsorted entries skipped.
        l.setItemsAndSelection(java.util.Arrays.asList("a", "X", null, "d"), new int[] {3, 9, 1, -1, 3});

        assertArrayEquals(new String[] {"a", "X", "", "d"}, l.getItemsArray());
        assertTrue(keptHead.getNode() == l.getElement().getChild(0).getNode(), "the common prefix is not rebuilt");
        assertTrue(keptTail.getNode() == l.getElement().getChild(3).getNode(), "the common suffix is not rebuilt");
        assertArrayEquals(new int[] {1, 3}, l.getSelectedIndexes());
        assertTrue(l.getElement().getChild(3).getProperty("selected", false));
        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("setSelectedIndexes shows one row in single mode, the first renderable entry")
    void setSelectedIndexesShowsOneRowInSingleMode() {
        SList l = listOfItems("a", "b", "c");
        l.select(0);
        // What AWT's own array can hold after a peerless multi -> single flip.
        l.setSelectedIndexes(new int[] {7, 2, 1});
        assertArrayEquals(new int[] {2}, l.getSelectedIndexes());
        assertFalse(l.getElement().getChild(0).getProperty("selected", false));
        l.setSelectedIndexes(new int[0]);
        assertArrayEquals(new int[] {}, l.getSelectedIndexes());
    }

    // --- selection ------------------------------------------------------

    @Test
    @DisplayName("single mode replaces the selection, multiple mode accumulates")
    void singleModeReplacesMultipleAccumulates() {
        SList single = listOfItems("a", "b", "c");
        single.select(0);
        single.select(2);
        assertArrayEquals(new int[]{2}, single.getSelectedIndexes());

        SList multi = listOfItems(true, "a", "b", "c");
        multi.select(2);
        multi.select(0);
        // Ascending, regardless of the order they were selected in.
        assertArrayEquals(new int[]{0, 2}, multi.getSelectedIndexes());
    }

    @Test
    @DisplayName("getSelectedIndex is -1 when two rows are selected, not the first")
    void getSelectedIndexIsMinusOneWhenTwoRowsSelected() {
        SList l = listOfItems(true, "a", "b", "c");
        l.select(1);
        assertEquals(1, l.getSelectedIndex());
        assertEquals("b", l.getSelectedItem());
        l.select(2);
        assertEquals(-1, l.getSelectedIndex());
        assertNull(l.getSelectedItem());
        // ...while the plural getters still see both.
        assertArrayEquals(new String[]{"b", "c"}, l.getSelectedItemsArray());
    }

    @Test
    @DisplayName("getSelectedObjects is an empty array when nothing is selected")
    void getSelectedObjectsIsEmptyWhenNothingSelected() {
        // AWT's List convention — the opposite of Choice, which answers null.
        assertArrayEquals(new Object[]{}, listOfItems("a").getSelectedObjects());
    }

    @Test
    @DisplayName("select and deselect out of range are no-ops")
    void selectAndDeselectOutOfRangeAreNoOps() {
        SList l = listOfItems("a", "b");
        l.select(5);
        l.select(-1);
        l.deselect(5);
        assertArrayEquals(new int[]{}, l.getSelectedIndexes());
    }

    @Test
    @DisplayName("deselect in single mode only bites on the sole selected row")
    void deselectInSingleModeOnlyBitesOnTheSoleSelectedRow() {
        SList l = listOfItems("a", "b");
        l.select(0);
        // AWT's guard: isMultipleMode() || getSelectedIndex() == index.
        l.deselect(1);
        assertArrayEquals(new int[]{0}, l.getSelectedIndexes());
        l.deselect(0);
        assertArrayEquals(new int[]{}, l.getSelectedIndexes());
    }

    @Test
    @DisplayName("the selection shifts with inserts and removes")
    void theSelectionShiftsWithInsertsAndRemoves() {
        SList l = listOfItems(true, "a", "b", "c", "d");
        l.select(2);
        l.select(3);
        l.removeItemRange(0, 0);
        assertArrayEquals(new int[]{1, 2}, l.getSelectedIndexes());
        l.addItem("z", 0);
        assertArrayEquals(new int[]{2, 3}, l.getSelectedIndexes());
        // Removing a selected row drops it rather than shifting it.
        l.removeItemRange(2, 2);
        assertArrayEquals(new int[]{2}, l.getSelectedIndexes());
    }

    @Test
    @DisplayName("flipping to single mode keeps the first selected row")
    void flippingToSingleModeKeepsTheFirstSelectedRow() {
        SList l = listOfItems(true, "a", "b", "c");
        l.select(0);
        l.select(2);
        l.setMultipleMode(false);
        assertArrayEquals(new int[]{0}, l.getSelectedIndexes());
        assertFalse(l.getElement().getChild(2).getProperty("selected", false));
    }

    // --- events ----------------------------------------------------------

    @Test
    @DisplayName("every programmatic path is silent")
    void everyProgrammaticPathIsSilent() {
        SList l = listOfItems(true, "a", "b", "c");
        List<Object> seen = new ArrayList<>();
        l.addItemListener(seen::add);
        l.addActionListener(seen::add);
        l.select(0);
        l.select(1);
        l.deselect(0);
        l.addItem("d", -1);
        l.removeItemRange(0, 0);
        l.setMultipleMode(false);
        l.makeVisible(1);
        l.removeAllItems();
        assertEquals(List.of(), seen, "only the browser fires on java.awt.List");
    }

    @Test
    @DisplayName("a browser change fires one ItemEvent per index, payload the Integer index")
    void aBrowserChangeFiresOneItemEventPerIndex() {
        SList l = listOfItems(true, "a", "b", "c");
        List<ItemEvent> events = new ArrayList<>();
        l.addItemListener(events::add);

        dispatchChange(l, 0, 2);
        assertEquals(2, events.size());
        assertTrue(events.stream().allMatch(it -> it.getStateChange() == ItemEvent.SELECTED));
        assertTrue(events.stream().allMatch(it -> it.getID() == ItemEvent.ITEM_STATE_CHANGED));
        // XListPeer posts the index, not the item String — unlike Choice.
        assertEquals(List.of(0, 2), events.stream().map(ItemEvent::getItem).toList());
        assertInstanceOf(Integer.class, events.get(0).getItem());
        assertEquals(l, events.get(0).getItemSelectable());
        assertArrayEquals(new int[]{0, 2}, l.getSelectedIndexes());

        // A delta fires only for what actually moved.
        events.clear();
        dispatchChange(l, 2, 1);
        assertEquals(2, events.size());
        assertEquals(
                Set.of(List.of(1, ItemEvent.SELECTED), List.of(0, ItemEvent.DESELECTED)),
                indexAndState(events));
    }

    @Test
    @DisplayName("a change that moves nothing fires nothing")
    void aChangeThatMovesNothingFiresNothing() {
        SList l = listOfItems("a", "b");
        List<ItemEvent> events = new ArrayList<>();
        dispatchChange(l, 1);
        l.addItemListener(events::add);
        dispatchChange(l, 1);
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("single-mode browser selection fires SELECTED only, never a DESELECTED for the replaced row")
    void singleModeBrowserSelectionFires() {
        SList l = listOfItems("a", "b", "c");
        dispatchChange(l, 0);
        List<ItemEvent> events = new ArrayList<>();
        l.addItemListener(events::add);
        dispatchChange(l, 1);
        // AWT's XListPeer only ever posts SELECTED in single mode; the
        // implicitly-replaced row gets nothing. Recorded so nobody "fixes" it.
        assertEquals(2, events.size());
        assertEquals(
                Set.of(List.of(1, ItemEvent.SELECTED), List.of(0, ItemEvent.DESELECTED)),
                indexAndState(events));
    }

    @Test
    @DisplayName("double-click fires an ActionEvent whose command is the item text")
    void doubleClickFiresAnActionEvent() {
        SList l = listOfItems("Mercury", "Venus");
        List<ActionEvent> events = new ArrayList<>();
        l.addActionListener(events::add);
        dispatchDoubleClick(l, 1);
        assertEquals(1, events.size());
        assertEquals("Venus", events.get(0).getActionCommand());
        assertEquals(ActionEvent.ACTION_PERFORMED, events.get(0).getID());
        assertEquals(l, events.get(0).getSource());
    }

    @Test
    @DisplayName("Enter fires only with a non-empty selection")
    void enterFiresOnlyWithANonEmptySelection() {
        SList l = listOfItems("Mercury", "Venus");
        List<ActionEvent> events = new ArrayList<>();
        l.addActionListener(events::add);
        dispatchEnter(l);
        assertEquals(0, events.size(), "AWT gates Enter on selected.length > 0");
        dispatchChange(l, 1);
        dispatchEnter(l);
        assertEquals(1, events.size());
        assertEquals("Venus", events.get(0).getActionCommand());
        // Any other key is filtered out before it reaches the server.
        dispatchEnter(l, "a");
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("a malformed selection payload does not take the bridge down")
    void aMalformedSelectionPayloadDoesNotTakeTheBridgeDown() {
        SList l = listOfItems("a", "b");
        fire(l, "change", SELECTED_INDICES, "not,a,number");
        assertArrayEquals(new int[]{}, l.getSelectedIndexes());
    }

    @Test
    @DisplayName("null listeners are ignored, as in AWT")
    void nullListenersAreIgnored() {
        SList l = new SList();
        l.addItemListener(null);
        l.removeItemListener(null);
        l.addActionListener(null);
        l.removeActionListener(null);
        assertEquals(0, l.getItemListeners().length);
        assertEquals(0, l.getActionListeners().length);
    }

    @Test
    @DisplayName("listeners dispatch in AWT's first-registered-first order")
    void listenersDispatchFirstRegisteredFirst() {
        SList l = listOfItems("a");
        List<String> order = new ArrayList<>();
        l.addItemListener(e -> order.add("first"));
        l.addItemListener(e -> order.add("second"));
        dispatchChange(l, 0);
        assertEquals(List.of("first", "second"), order);
    }

    // --- inherited surface -------------------------------------------------

    @Test
    @DisplayName("the ComponentMixin surface works on this non-JComponent host")
    void theComponentMixinSurfaceWorksOnThisNonJComponentHost() {
        SList l = new SList();
        l.setName("planets");
        assertEquals("planets", l.getName());
        l.setEnabled(false);
        assertFalse(l.isEnabled());
        l.setEnabled(true);
        l.setBackground(Color.WHITE);
        l.setFont(new Font("Dialog", Font.PLAIN, 12));
        assertNoWarns("ComponentMixin's basics need no host capability SList lacks");
    }

    @Test
    @DisplayName("paramString carries AWT's tail")
    void paramStringCarriesAwtsTail() {
        SList l = listOfItems("a", "b");
        assertEquals("selected=null", l.paramString());
        l.select(1);
        assertEquals("selected=b", l.paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        assertNull(new SList().getAccessibleContext());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("getAccessibleContext"));
    }

    // --- Exit gate -----------------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SList l = new SList(6, true);
        l.setName("planets");
        UI.getCurrent().add(l);
        l.addItem("Mercury", -1);
        l.addItem("Venus", -1);
        l.addItem("Earth", 0);
        l.addItemListener(e -> {
        });
        l.addActionListener(e -> {
        });
        l.select(0);
        l.select(2);
        l.deselect(0);
        dispatchChange(l, 1, 2);
        dispatchDoubleClick(l, 1);
        dispatchEnter(l);
        l.makeVisible(2);
        l.setMultipleMode(false);
        l.removeItemRange(0, 0);
        l.getItemsArray();
        l.getSelectedItemsArray();
        l.getSelectedObjects();
        l.paramString();
        l.removeAllItems();
        assertNoWarns("no stub WARNs expected from SList's happy path");
    }
}
