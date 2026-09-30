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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_schoice's SChoice surrogate — the AWT 1.0 {@code java.awt.Choice},
 * not {@code JComboBox}. Covers:
 *
 * <ol>
 *  <li>The four load-bearing quirks: {@code select()} is silent, one SELECTED and
 *      never a DESELECTED partner, insert-at-or-before-selection re-selects
 *      0, remove adjusts through {@code select}.
 *  <li>{@code getSelectedObjects()} returning null (not {@code Object[0]}) when empty —
 *      the opposite of SJComboBox's Swing convention.
 *  <li>Index identity: duplicate item Strings stay independently selectable.
 *  <li>The rebuild churn — {@code Select.reset()} clears the value and fires a
 *      ValueChangeEvent, which must never surface as an ItemEvent.
 *  <li>The R_match_swing_errors exception table, row by row.
 *  <li>The inherited ComponentMixin surface, and zero stub WARNs on the
 *      happy path.
 * </ol>
 */
class SChoiceTest extends AbstractKaribuTest {

    /** Attached + enabled, which Karibu's {@code _setValue} requires to simulate a browser pick. */
    private static SChoice attached(String... items) {
        SChoice c = new SChoice();
        for (String item : items) {
            c.addItem(item);
        }
        UI.getCurrent().add(c);
        return c;
    }

    /** The item texts the peer actually rendered, read off the DOM. */
    private static List<String> renderedOptions(SChoice c) {
        return c.getElement().getChild(0).getChildren().map(it -> it.getText()).toList();
    }

    /** The item list, read back through the index API. */
    private static List<String> itemsOf(SChoice c) {
        return IntStream.range(0, c.getItemCount()).mapToObj(c::getItemAt).toList();
    }

    // --- Defaults -----------------------------------------------------

    @Test
    @DisplayName("a fresh Choice is empty with no selection")
    void aFreshChoiceIsEmptyWithNoSelection() {
        SChoice c = new SChoice();
        assertEquals(0, c.getItemCount());
        assertEquals(-1, c.getSelectedIndex());
        assertNull(c.getSelectedItem());
    }

    @Test
    @DisplayName("getSelectedObjects returns null when empty, not an empty array")
    void getSelectedObjectsReturnsNullWhenEmpty() {
        // AWT and Swing take opposite conventions on the same ItemSelectable
        // method; SJComboBox returns Object[0] here.
        assertNull(new SChoice().getSelectedObjects());
        assertArrayEquals(new Object[0], new SJComboBox<String>().getSelectedObjects());
    }

    @Test
    @DisplayName("the first add selects it, so a non-empty Choice always has a selection")
    void theFirstAddSelectsIt() {
        SChoice c = new SChoice();
        c.addItem("one");
        assertEquals(0, c.getSelectedIndex());
        assertEquals("one", c.getSelectedItem());
        c.addItem("two");
        assertEquals(0, c.getSelectedIndex());
        assertArrayEquals(new Object[]{"one"}, c.getSelectedObjects());
    }

    // --- Quirk 1: select() is silent ----------------------------------

    @Test
    @DisplayName("select by index fires no ItemEvent")
    void selectByIndexFiresNoItemEvent() {
        SChoice c = attached("a", "b", "c");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        c.select(2);
        assertEquals(2, c.getSelectedIndex());
        assertEquals(List.of(), events, "AWT fires ItemEvents only on user interaction");
    }

    @Test
    @DisplayName("select by string fires no ItemEvent")
    void selectByStringFiresNoItemEvent() {
        SChoice c = attached("a", "b", "c");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        c.select("b");
        assertEquals(1, c.getSelectedIndex());
        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("setValue converges with select and stays just as silent")
    void setValueConvergesWithSelect() {
        SChoice c = attached("a", "b");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        c.setValue(1);
        assertEquals(1, c.getSelectedIndex());
        assertEquals("b", c.getSelectedItem());
        assertEquals(List.of(), events);
    }

    // --- Quirk 2: one SELECTED, never a pair --------------------------

    @Test
    @DisplayName("a browser pick fires exactly one SELECTED carrying the item String")
    void aBrowserPickFiresExactlyOneSelected() {
        SChoice c = attached("a", "b", "c");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);

        LocatorJ._setValue(c, 2); // Karibu: isFromClient = true

        assertEquals(1, events.size(), "AWT posts no DESELECTED partner; got " + events);
        assertEquals(ItemEvent.SELECTED, events.get(0).getStateChange());
        assertEquals(ItemEvent.ITEM_STATE_CHANGED, events.get(0).getID());
        assertEquals("c", events.get(0).getItem(), "the payload is the item String, not the index");
        assertSame(c, events.get(0).getItemSelectable());
        assertEquals(2, c.getSelectedIndex());
    }

    @Test
    @DisplayName("listeners are delivered in AWT's first-registered-first order")
    void listenersAreDeliveredFirstRegisteredFirst() {
        // AWTEventMulticaster dispatches FIFO where EventListenerList is
        // LIFO; an AWT surrogate built naively on the latter reverses the
        // order the JDK guarantees.
        SChoice c = attached("a", "b");
        List<String> order = new ArrayList<>();
        c.addItemListener(e -> order.add("first"));
        c.addItemListener(e -> order.add("second"));
        LocatorJ._setValue(c, 1);
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    @DisplayName("removeItemListener stops delivery and null listeners are ignored")
    void removeItemListenerStopsDelivery() {
        SChoice c = attached("a", "b");
        List<ItemEvent> events = new ArrayList<>();
        ItemListener l = events::add;
        c.addItemListener(l);
        c.addItemListener(null);
        assertEquals(1, c.getItemListeners().length);
        c.removeItemListener(l);
        c.removeItemListener(null);
        LocatorJ._setValue(c, 1);
        assertEquals(List.of(), events);
    }

    // --- setItemsAndSelection ------------------------------------------

    @Test
    @DisplayName("setItemsAndSelection replaces both, applies no selection rule and stays silent")
    void setItemsAndSelectionReplacesBothSilently() {
        SChoice c = new SChoice();
        c.addItem("old");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        List<String> texts = new ArrayList<>(List.of("a", "b", "a"));

        c.setItemsAndSelection(texts, 2);
        texts.clear(); // copied, not kept

        assertEquals(List.of("a", "b", "a"), itemsOf(c));
        assertEquals(2, c.getSelectedIndex());
        // -1 with items is what a select override that skips super leaves.
        c.setItemsAndSelection(List.of("x"), -1);
        assertEquals(-1, c.getSelectedIndex());
        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("setItemsAndSelection refuses a null item or an out-of-range selection")
    void setItemsAndSelectionRefusesBadInput() {
        SChoice c = new SChoice();
        assertThrows(IllegalArgumentException.class, () -> c.setItemsAndSelection(List.of("a"), 1));
        assertThrows(IllegalArgumentException.class, () -> c.setItemsAndSelection(List.of("a"), -2));
        assertThrows(NullPointerException.class,
                () -> c.setItemsAndSelection(java.util.Arrays.asList("a", null), 0));
        assertEquals(0, c.getItemCount());
    }

    // --- Quirk 4: insert re-selects 0 ---------------------------------

    @Test
    @DisplayName("inserting at or before the selection re-selects index 0")
    void insertingAtOrBeforeTheSelectionReSelectsZero() {
        // The JDK comments this as "selection shifted up" but the code does
        // not shift — it calls select(0). Reproduced verbatim or not at all.
        SChoice c = new SChoice();
        c.addItem("a");
        c.addItem("b");
        c.select(1);
        c.insertItemAt("new", 0);
        assertEquals(List.of("new", "a", "b"), itemsOf(c));
        assertEquals(0, c.getSelectedIndex());
        assertEquals("new", c.getSelectedItem());
    }

    @Test
    @DisplayName("inserting after the selection leaves it where it was")
    void insertingAfterTheSelectionLeavesIt() {
        SChoice c = new SChoice();
        c.addItem("a");
        c.addItem("b");
        c.select(0);
        c.insertItemAt("tail", 2);
        assertEquals(0, c.getSelectedIndex());
        assertEquals("a", c.getSelectedItem());
    }

    @Test
    @DisplayName("insert clamps an index past the end rather than throwing")
    void insertClampsAnIndexPastTheEnd() {
        SChoice c = new SChoice();
        c.addItem("a");
        c.insertItemAt("z", 99);
        assertEquals("z", c.getItemAt(1));
    }

    @Test
    @DisplayName("appending never disturbs an existing selection")
    void appendingNeverDisturbsAnExistingSelection() {
        SChoice c = new SChoice();
        c.addItem("a");
        c.addItem("b");
        c.select(1);
        c.addItem("c");
        assertEquals(1, c.getSelectedIndex());
        assertEquals("b", c.getSelectedItem());
    }

    // --- Quirk 5: remove adjusts through select -----------------------

    @Test
    @DisplayName("removing the selected item re-selects index 0")
    void removingTheSelectedItemReSelectsZero() {
        SChoice c = attachedDetached("a", "b", "c");
        c.select(1);
        c.removeItemAt(1);
        assertEquals(0, c.getSelectedIndex());
        assertEquals("a", c.getSelectedItem());
    }

    @Test
    @DisplayName("removing below the selection decrements it")
    void removingBelowTheSelectionDecrementsIt() {
        SChoice c = attachedDetached("a", "b", "c");
        c.select(2);
        c.removeItemAt(0);
        assertEquals(1, c.getSelectedIndex());
        assertEquals("c", c.getSelectedItem(), "the same item stays selected, at its new index");
    }

    @Test
    @DisplayName("removing above the selection leaves it alone")
    void removingAboveTheSelectionLeavesItAlone() {
        SChoice c = attachedDetached("a", "b", "c");
        c.select(0);
        c.removeItemAt(2);
        assertEquals(0, c.getSelectedIndex());
        assertEquals("a", c.getSelectedItem());
    }

    @Test
    @DisplayName("removing the last item drops the selection to none")
    void removingTheLastItemDropsTheSelection() {
        SChoice c = new SChoice();
        c.addItem("only");
        c.removeItemAt(0);
        assertEquals(0, c.getItemCount());
        assertEquals(-1, c.getSelectedIndex());
        assertNull(c.getSelectedItem());
        assertNull(c.getSelectedObjects());
    }

    @Test
    @DisplayName("removeItem removes the first occurrence by value")
    void removeItemRemovesTheFirstOccurrenceByValue() {
        SChoice c = attachedDetached("a", "b", "a");
        c.removeItem("a");
        assertEquals(List.of("b", "a"), itemsOf(c));
    }

    @Test
    @DisplayName("removeAllItems empties the list and the selection")
    void removeAllItemsEmptiesTheListAndTheSelection() {
        SChoice c = attachedDetached("a", "b");
        c.removeAllItems();
        assertEquals(0, c.getItemCount());
        assertEquals(-1, c.getSelectedIndex());
        assertNull(c.getSelectedObjects());
    }

    /** Seeded but not attached — the shape the mutation tests want. */
    private static SChoice attachedDetached(String... items) {
        SChoice c = new SChoice();
        for (String item : items) {
            c.addItem(item);
        }
        return c;
    }

    // --- Index identity -----------------------------------------------

    @Test
    @DisplayName("duplicate item Strings stay independently selectable")
    void duplicateItemStringsStayIndependentlySelectable() {
        // The case index identity exists for: Vaadin's KeyMapper keys items
        // in a HashMap, so two equal Strings would collapse to one key.
        SChoice c = attached("A", "A", "B");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        assertEquals(List.of("A", "A", "B"), renderedOptions(c));

        LocatorJ._setValue(c, 1);
        assertEquals(1, c.getSelectedIndex(), "the second A, not the first");
        assertEquals("A", c.getSelectedItem());
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("the rendered options track the item list through every mutation")
    void theRenderedOptionsTrackTheItemList() {
        SChoice c = attached("a", "b");
        assertEquals(List.of("a", "b"), renderedOptions(c));
        c.insertItemAt("head", 0);
        assertEquals(List.of("head", "a", "b"), renderedOptions(c));
        c.removeItem("a");
        assertEquals(List.of("head", "b"), renderedOptions(c));
        c.removeAllItems();
        assertEquals(List.of(), renderedOptions(c));
    }

    // --- The rebuild churn --------------------------------------------

    @Test
    @DisplayName("mutating a selected Choice fires no ItemEvent despite the peer rebuild")
    void mutatingASelectedChoiceFiresNoItemEvent() {
        // Vaadin's Select.reset() — which every setItems funnels through —
        // calls clear(), so each mutation nulls the value and fires a
        // ValueChangeEvent. This test fails if preventPeerEvents is raised
        // around only the selection push and not the items push.
        SChoice c = attached("a", "b");
        c.select(1);
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);

        c.addItem("c");
        c.insertItemAt("tail", 3);
        c.removeItemAt(0);
        c.removeAllItems();

        assertEquals(List.of(), events, "peer rebuild churn must not surface as ItemEvents");
    }

    @Test
    @DisplayName("a browser pick still fires after a mutation restores the guard")
    void aBrowserPickStillFiresAfterAMutation() {
        // The guard is save-and-restore rather than a flat assignment,
        // because pushItems nests (reset → clear → our setValue). A flat
        // `finally = false` would leave the outer rebuild unguarded.
        SChoice c = attached("a", "b");
        c.addItem("c");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        LocatorJ._setValue(c, 2);
        assertEquals(1, events.size());
        assertEquals("c", events.get(0).getItem());
    }

    // --- R_match_swing_errors exception table -------------------------------------------

    @Test
    @DisplayName("add null throws NPE with AWT's message")
    void addNullThrowsNpeWithAwtsMessage() {
        SChoice c = new SChoice();
        NullPointerException e = assertThrows(NullPointerException.class, () -> c.addItem(null));
        assertEquals("cannot add null item to Choice", e.getMessage());
        NullPointerException e2 = assertThrows(NullPointerException.class, () -> c.insertItemAt(null, 0));
        assertEquals("cannot add null item to Choice", e2.getMessage());
    }

    @Test
    @DisplayName("insert at a negative index throws IAE with AWT's message")
    void insertAtANegativeIndexThrowsIae() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new SChoice().insertItemAt("x", -1));
        assertEquals("index less than zero.", e.getMessage());
    }

    @Test
    @DisplayName("getItemAt out of range throws ArrayIndexOutOfBounds, not the plain superclass")
    void getItemAtOutOfRangeThrowsAioobe() {
        // ArrayList.get raises IndexOutOfBoundsException; AWT's Vector
        // raises the AIOOBE subclass, so a migrator's catch would miss it.
        SChoice c = new SChoice();
        c.addItem("a");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> c.getItemAt(1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> c.getItemAt(-1));
    }

    @Test
    @DisplayName("removeItemAt out of range throws ArrayIndexOutOfBounds")
    void removeItemAtOutOfRangeThrowsAioobe() {
        SChoice c = new SChoice();
        c.addItem("a");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> c.removeItemAt(1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> c.removeItemAt(-1));
    }

    @Test
    @DisplayName("select out of range throws IAE with AWT's message")
    void selectOutOfRangeThrowsIae() {
        SChoice c = new SChoice();
        c.addItem("a");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> c.select(1));
        assertEquals("illegal Choice item position: 1", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> c.select(-1));
        assertEquals(0, c.getSelectedIndex(), "nothing was written before the throw");
    }

    @Test
    @DisplayName("select on an unknown string is a silent no-op")
    void selectOnAnUnknownStringIsASilentNoOp() {
        SChoice c = attachedDetached("a", "b");
        c.select(1);
        c.select("nope");
        assertEquals(1, c.getSelectedIndex());
    }

    @Test
    @DisplayName("removeItem of an absent item throws IAE with AWT's message")
    void removeItemOfAnAbsentItemThrowsIae() {
        SChoice c = new SChoice();
        c.addItem("a");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> c.removeItem("nope"));
        assertEquals("item nope not found in choice", e.getMessage());
    }

    // --- Inherited ComponentMixin surface -----------------------------

    @Test
    @DisplayName("ComponentMixin works on this Select host")
    void componentMixinWorksOnThisSelectHost() {
        SChoice c = new SChoice();
        c.setName("units");
        assertEquals("units", c.getName());
        assertEquals("units", c.getElement().getAttribute("data-swing-name"));

        c.setForeground(Color.RED);
        assertEquals(Color.RED, c.getForeground());

        c.setEnabled(false);
        assertFalse(c.isEnabled());
    }

    // --- toString shape / stubs ---------------------------------------

    @Test
    @DisplayName("paramString reports the selected item")
    void paramStringReportsTheSelectedItem() {
        SChoice c = new SChoice();
        assertEquals("current=null", c.paramString());
        c.addItem("metric");
        assertEquals("current=metric", c.paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        assertNull(new SChoice().getAccessibleContext());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("getAccessibleContext"));
    }

    // --- Exit gate ----------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SChoice c = attached("metric", "imperial");
        c.setName("units");
        c.addItemListener(e -> { /* observe */ });
        c.select("imperial");
        c.addItem("nautical");
        c.insertItemAt("astronomical", 0);
        c.removeItem("nautical");
        LocatorJ._setValue(c, 1);
        c.getItemCount();
        c.getSelectedItem();
        c.getSelectedObjects();
        c.removeAllItems();
        assertNoWarns("no stub WARNs expected from SChoice's happy path");
    }
}
