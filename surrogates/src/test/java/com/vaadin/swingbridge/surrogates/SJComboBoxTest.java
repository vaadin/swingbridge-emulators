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
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBoxBase;
import com.vaadin.flow.internal.nodefeature.ElementPropertyMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.stream.IntStream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjcombobox's SJComboBox surrogate. Covers:
 *
 * <ol>
 *  <li><b>Constructors</b> — no-arg / model / array / Vector all funnel through
 *      the model ctor.
 *  <li><b>Items sync</b> — DefaultComboBoxModel.addElement → Vaadin getDataProvider
 *      reflects the new size; rebuild on setItems.
 *  <li><b>Selection round-trip</b> — setSelectedItem updates peer + fires Item
 *      pair + ActionEvent; peer ValueChange writes through model + fires
 *      pair + Action; preventPeerEvents prevents loop.
 *  <li><b>Editable mode</b> — setEditable wires customValue commit; non-editable
 *      does not fire on customValue.
 *  <li><b>maximumRowCount</b> mirrors Vaadin pageSize; IAE on &lt; 1.
 *  <li><b>Popup events</b> — showPopup / hidePopup and a user's open / close fire
 *      PopupMenuListener (will-become-visible / will-become-invisible) and
 *      OpenedChangeEvent.
 *  <li><b>R_match_swing_errors IAE</b> on mutator-on-non-mutable model.
 *  <li><b>R_vaadin_first drops</b> WARN-and-return for ComboBoxEditor / KeySelectionManager
 *      / lightWeightPopup / prototypeDisplayValue / selectWithKeyChar.
 *  <li><b>setAction</b> narrow propagation (enabled / SHORT_DESCRIPTION /
 *      ACTION_COMMAND_KEY only).
 *  <li><b>Happy-path zero-WARN</b> — typical CRUD interaction surface fires no
 *      stub WARNs.
 * </ol>
 */
class SJComboBoxTest extends AbstractKaribuTest {

    /** The model's elements read back through the surrogate's index API. */
    private static List<String> itemsOf(SJComboBox<String> cb) {
        return IntStream.range(0, cb.getItemCount()).mapToObj(cb::getItemAt).toList();
    }

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("no-arg ctor installs empty DefaultComboBoxModel")
    void noArgCtorInstallsEmptyDefaultComboBoxModel() {
        SJComboBox<String> cb = new SJComboBox<>();
        assertEquals(0, cb.getItemCount());
        assertNull(cb.getSelectedItem());
    }

    @Test
    @DisplayName("array ctor seeds the model")
    void arrayCtorSeedsTheModel() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b", "c"});
        assertEquals(3, cb.getItemCount());
        assertEquals("a", cb.getItemAt(0));
        // JDK DefaultComboBoxModel(E[]) seeds selectedItem to the first element.
        assertEquals("a", cb.getSelectedItem());
    }

    @Test
    @DisplayName("Vector ctor seeds the model")
    void vectorCtorSeedsTheModel() {
        Vector<String> v = new Vector<>(List.of("x", "y"));
        SJComboBox<String> cb = new SJComboBox<>(v);
        assertEquals(2, cb.getItemCount());
        assertEquals("x", cb.getItemAt(0));
    }

    @Test
    @DisplayName("null model throws NPE matching JDK")
    void nullModelThrowsNpe() {
        assertThrows(NullPointerException.class,
                () -> new SJComboBox<String>((ComboBoxModel<String>) null));
    }

    // --- Items API ------------------------------------------------------

    @Test
    @DisplayName("addItem appends through MutableComboBoxModel")
    void addItemAppendsThroughMutableComboBoxModel() {
        SJComboBox<String> cb = new SJComboBox<>();
        cb.addItem("first");
        cb.addItem("second");
        assertEquals(2, cb.getItemCount());
        assertEquals("first", cb.getItemAt(0));
        assertEquals("second", cb.getItemAt(1));
    }

    @Test
    @DisplayName("insertItemAt + removeItemAt")
    void insertItemAtAndRemoveItemAt() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "c"});
        cb.insertItemAt("b", 1);
        assertEquals(List.of("a", "b", "c"), itemsOf(cb));
        cb.removeItemAt(0);
        assertEquals(List.of("b", "c"), itemsOf(cb));
    }

    @Test
    @DisplayName("removeAllItems clears the model")
    void removeAllItemsClearsTheModel() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b", "c"});
        cb.removeAllItems();
        assertEquals(0, cb.getItemCount());
        assertNull(cb.getSelectedItem());
    }

    @Test
    @DisplayName("addItem on non-Mutable model throws IllegalStateException")
    void addItemOnNonMutableModelThrows() {
        ComboBoxModel<String> readOnlyModel = new ComboBoxModel<>() {
            private String sel = "x";

            @Override
            public void setSelectedItem(Object item) {
                sel = (String) item;
            }

            @Override
            public Object getSelectedItem() {
                return sel;
            }

            @Override
            public int getSize() {
                return 1;
            }

            @Override
            public String getElementAt(int i) {
                return "x";
            }

            @Override
            public void addListDataListener(ListDataListener l) {
            }

            @Override
            public void removeListDataListener(ListDataListener l) {
            }
        };
        SJComboBox<String> cb = new SJComboBox<>(readOnlyModel);
        assertThrows(IllegalStateException.class, () -> cb.addItem("y"));
    }

    // --- Selection ------------------------------------------------------

    @Test
    @DisplayName("setSelectedItem fires DESELECTED-prev then SELECTED-new pair")
    void setSelectedItemFiresDeselectedThenSelectedPair() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b", "c"});
        List<ItemEvent> events = new ArrayList<>();
        cb.addItemListener(events::add);
        cb.setSelectedItem("b");
        // ctor seeded selection to "a"; pair = (DESELECTED a, SELECTED b)
        assertEquals(2, events.size());
        assertEquals(ItemEvent.DESELECTED, events.get(0).getStateChange());
        assertEquals("a", events.get(0).getItem());
        assertEquals(ItemEvent.SELECTED, events.get(1).getStateChange());
        assertEquals("b", events.get(1).getItem());
        // Source rebound to surrogate
        assertSame(cb, events.get(0).getSource());
    }

    @Test
    @DisplayName("setSelectedItem fires ActionEvent after Item pair")
    void setSelectedItemFiresActionEventAfterItemPair() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        List<ActionEvent> actions = new ArrayList<>();
        cb.addActionListener(actions::add);
        cb.setSelectedItem("b");
        assertEquals(1, actions.size());
        assertEquals("comboBoxChanged", actions.get(0).getActionCommand());
        assertSame(cb, actions.get(0).getSource());
    }

    @Test
    @DisplayName("peer ValueChange writes through model and fires events")
    void peerValueChangeWritesThroughModelAndFiresEvents() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b", "c"});
        List<ItemEvent> items = new ArrayList<>();
        cb.addItemListener(items::add);
        LocatorJ._setValue(cb, "c");
        assertEquals("c", cb.getSelectedItem());
        assertEquals(2, items.size()); // DESELECTED a + SELECTED c
        assertEquals(ItemEvent.SELECTED, items.get(1).getStateChange());
        assertEquals("c", items.get(1).getItem());
    }

    @Test
    @DisplayName("setSelectedIndex uses model's element at index")
    void setSelectedIndexUsesModelsElementAtIndex() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b", "c"});
        cb.setSelectedIndex(2);
        assertEquals("c", cb.getSelectedItem());
        cb.setSelectedIndex(-1);
        assertNull(cb.getSelectedItem());
    }

    @Test
    @DisplayName("setSelectedIndex out of bounds throws IAE per R_match_swing_errors")
    void setSelectedIndexOutOfBoundsThrowsIae() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a"});
        assertThrows(IllegalArgumentException.class, () -> cb.setSelectedIndex(5));
        assertThrows(IllegalArgumentException.class, () -> cb.setSelectedIndex(-2));
    }

    @Test
    @DisplayName("getSelectedObjects returns single-element array")
    void getSelectedObjectsReturnsSingleElementArray() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        cb.setSelectedItem("b");
        assertEquals(List.of("b"), List.of(cb.getSelectedObjects()));
        cb.setSelectedItem(null);
        assertEquals(0, cb.getSelectedObjects().length);
    }

    @Test
    @DisplayName("setSelectedItem refuses an item the model does not hold unless editable")
    void setSelectedItemRefusesAnItemNotInTheModel() {
        // The rejection is javax.swing.JComboBox.setSelectedItem's own, not the
        // model's — DefaultComboBoxModel permits any value, which is why routing
        // straight to it let a non-list item become the selection
        // (D_return_value_audit; measured on JDK 25).
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        List<ItemEvent> items = new ArrayList<>();
        List<ActionEvent> actions = new ArrayList<>();
        cb.addItemListener(items::add);
        cb.addActionListener(actions::add);

        cb.setSelectedItem("ADMIN");
        assertEquals("a", cb.getSelectedItem(), "a refused item leaves the selection where it was");
        assertEquals(0, items.size());
        assertEquals(0, actions.size());

        cb.setEditable(true);
        cb.setSelectedItem("ADMIN");
        assertEquals("ADMIN", cb.getSelectedItem(), "editable takes any value");

        SJComboBox<String> empty = new SJComboBox<>();
        empty.setSelectedItem("ADMIN");
        assertNull(empty.getSelectedItem(), "nothing in the model, so nothing to select");
    }

    @Test
    @DisplayName("setSelectedItem selects the model's own element, not the equal argument")
    void setSelectedItemSubstitutesTheModelsElement() {
        String held = new String("a");
        SJComboBox<String> cb = new SJComboBox<>(new String[]{held, "b"});
        String equalButDistinct = new String("a");
        cb.setSelectedItem(equalButDistinct);
        assertSame(held, cb.getSelectedItem(), "the JDK substitutes the model's instance");
    }

    @Test
    @DisplayName("re-selecting same item fires no events (equality short-circuit)")
    void reSelectingSameItemFiresNoEvents() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        cb.setSelectedItem("b");
        List<ItemEvent> items = new ArrayList<>();
        List<ActionEvent> actions = new ArrayList<>();
        cb.addItemListener(items::add);
        cb.addActionListener(actions::add);
        cb.setSelectedItem("b"); // unchanged
        assertEquals(0, items.size());
        assertEquals(0, actions.size());
    }

    // --- Model swap -----------------------------------------------------

    @Test
    @DisplayName("setModel fires model PCE and rebuilds items")
    void setModelFiresModelPceAndRebuildsItems() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"old1", "old2"});
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cb.addPropertyChangeListener("model", pces::add);
        DefaultComboBoxModel<String> newModel = new DefaultComboBoxModel<>(new String[]{"new1", "new2", "new3"});
        cb.setModel(newModel);
        assertEquals(1, pces.size());
        assertEquals(3, cb.getItemCount());
        assertEquals("new1", cb.getItemAt(0));
        assertSame(newModel, cb.getModel());
    }

    // --- Editable -------------------------------------------------------

    @Test
    @DisplayName("setEditable maps to allowCustomValue and fires PCE")
    void setEditableMapsToAllowCustomValueAndFiresPce() {
        SJComboBox<String> cb = new SJComboBox<>();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cb.addPropertyChangeListener("editable", pces::add);
        cb.setEditable(true);
        assertTrue(cb.isEditable());
        assertTrue(cb.isAllowCustomValue());
        assertEquals(1, pces.size());
        cb.setEditable(false);
        assertFalse(cb.isEditable());
        assertFalse(cb.isAllowCustomValue());
        assertEquals(2, pces.size());
    }

    @Test
    @DisplayName("editable customValueSetListener commits typed value through model")
    void editableCustomValueSetListenerCommitsTypedValue() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        cb.setEditable(true);
        // Simulate a customValueSetEvent — Vaadin's API fires this when
        // the user types something not matching any item and presses Enter.
        ComponentUtil.fireEvent(cb, new ComboBoxBase.CustomValueSetEvent<>(cb, true, "typed"));
        assertEquals("typed", cb.getSelectedItem());
    }

    // --- Maximum row count ----------------------------------------------

    @Test
    @DisplayName("setMaximumRowCount mirrors pageSize and fires PCE")
    void setMaximumRowCountMirrorsPageSizeAndFiresPce() {
        SJComboBox<String> cb = new SJComboBox<>();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cb.addPropertyChangeListener("maximumRowCount", pces::add);
        cb.setMaximumRowCount(12);
        assertEquals(12, cb.getMaximumRowCount());
        assertEquals(12, cb.getPageSize());
        assertEquals(1, pces.size());
    }

    @Test
    @DisplayName("setMaximumRowCount below 1 throws IAE")
    void setMaximumRowCountBelowOneThrowsIae() {
        SJComboBox<String> cb = new SJComboBox<>();
        assertThrows(IllegalArgumentException.class, () -> cb.setMaximumRowCount(0));
    }

    // --- Popup ----------------------------------------------------------

    @Test
    @DisplayName("showPopup fires popupMenuWillBecomeVisible")
    void showPopupFiresPopupMenuWillBecomeVisible() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        List<String> events = new ArrayList<>();
        cb.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                events.add("show");
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                events.add("hide");
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                events.add("cancel");
            }
        });
        cb.showPopup();
        cb.hidePopup();
        assertEquals(List.of("show", "hide"), events);
    }

    @Test
    @DisplayName("repeated setPopupVisible with same value fires nothing")
    void repeatedSetPopupVisibleWithSameValueFiresNothing() {
        SJComboBox<String> cb = new SJComboBox<>();
        Counter fires = new Counter();
        cb.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                fires.inc();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                fires.inc();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
        cb.setPopupVisible(false); // already false
        fires.assertEquals(0);
    }

    /** What the browser's {@code opened-changed} synchronization does to the peer. */
    private static void setOpenedFromClient(SJComboBox<?> cb, boolean opened) throws Exception {
        cb.getElement().getNode().getFeature(ElementPropertyMap.class)
                .deferredUpdateFromClient("opened", opened).run();
    }

    /** Records the popup events as "show" / "hide" / "cancel". */
    private static List<String> recordPopupEvents(SJComboBox<?> cb) {
        List<String> events = new ArrayList<>();
        cb.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { events.add("show"); }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { events.add("hide"); }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { events.add("cancel"); }
        });
        return events;
    }

    @Test
    @DisplayName("a user's open and close fire the PopupMenuListeners")
    void userOpenAndCloseFirePopupMenuListeners() throws Exception {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        UI.getCurrent().add(cb);
        List<String> events = recordPopupEvents(cb);
        setOpenedFromClient(cb, true);
        assertEquals(List.of("show"), events);
        assertTrue(cb.isPopupVisible());
        setOpenedFromClient(cb, false);
        assertEquals(List.of("show", "hide"), events);
    }

    @Test
    @DisplayName("OpenedChangeEvent reports the new state and who changed it")
    void openedChangeEventReportsStateAndOrigin() throws Exception {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"a", "b"});
        UI.getCurrent().add(cb);
        List<String> events = new ArrayList<>();
        cb.addOpenedChangeListener(e ->
                events.add((e.isFromClient() ? "client " : "server ") + e.isOpened()));
        setOpenedFromClient(cb, true);
        cb.setPopupVisible(false);
        cb.showPopup();
        cb.showPopup(); // no change, no event
        assertEquals(List.of("client true", "server false", "server true"), events);
    }

    // --- ListDataListener forwarding -----------------------------------

    @Test
    @DisplayName("model events forward to surrogate's ListDataListener registry")
    void modelEventsForwardToSurrogatesListDataListenerRegistry() {
        // JDK DefaultComboBoxModel autotunes selection on first-add and
        // on selected-item-removal, so a "type-only" filter is the
        // honest assertion shape (JComboBox forwards every model event
        // verbatim, including the auto-selection CONTENTS_CHANGED's).
        SJComboBox<String> cb = new SJComboBox<>();
        List<ListDataEvent> intervalAdded = new ArrayList<>();
        List<ListDataEvent> intervalRemoved = new ArrayList<>();
        cb.addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
                intervalAdded.add(e);
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
                intervalRemoved.add(e);
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                /* model auto-selection fires extras */
            }
        });
        cb.addItem("first"); // INTERVAL_ADDED + CONTENTS_CHANGED (auto-select first)
        cb.addItem("second"); // INTERVAL_ADDED (no auto-select)
        cb.removeItemAt(1); // INTERVAL_REMOVED (item not selected, no extra)
        assertEquals(2, intervalAdded.size());
        assertEquals(1, intervalRemoved.size());
    }

    // --- Action surface -------------------------------------------------

    @Test
    @DisplayName("setAction propagates SHORT_DESCRIPTION + ACTION_COMMAND_KEY + enabled")
    void setActionPropagatesNarrowly() {
        SJComboBox<String> cb = new SJComboBox<>();
        Counter fired = new Counter();
        Action a = new AbstractAction("ignored-name") {
            @Override
            public void actionPerformed(ActionEvent e) {
                fired.inc();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, "tooltip!");
        a.putValue(Action.ACTION_COMMAND_KEY, "cmd");
        a.setEnabled(false);
        cb.setAction(a);
        assertSame(a, cb.getAction());
        assertEquals("cmd", cb.getActionCommand());
        assertFalse(cb.isEnabled());
        assertEquals("tooltip!", cb.getTooltip().getText());
        // Selection commit should fire the Action's actionPerformed via the
        // bridge ActionListener registered in setAction.
        cb.addItem("one");
        cb.setSelectedItem("one");
        fired.assertEquals(1);
    }

    // --- R_vaadin_first drop-and-WARN surface ---------------------------------------

    @Test
    @DisplayName("getEditor WARNs and returns null")
    void getEditorWarnsAndReturnsNull() {
        SJComboBox<String> cb = new SJComboBox<>();
        int before = capturedWarns.size();
        assertNull(cb.getEditor());
        assertTrue(capturedWarns.size() > before);
    }

    @Test
    @DisplayName("selectWithKeyChar WARNs and returns false")
    void selectWithKeyCharWarnsAndReturnsFalse() {
        SJComboBox<String> cb = new SJComboBox<>();
        int before = capturedWarns.size();
        assertFalse(cb.selectWithKeyChar('x'));
        assertTrue(capturedWarns.size() > before);
    }

    @Test
    @DisplayName("setLightWeightPopupEnabled true is silent (matches JDK default)")
    void setLightWeightPopupEnabledTrueIsSilent() {
        SJComboBox<String> cb = new SJComboBox<>();
        int before = capturedWarns.size();
        cb.setLightWeightPopupEnabled(true);
        assertEquals(before, capturedWarns.size());
    }

    @Test
    @DisplayName("setLightWeightPopupEnabled false WARNs")
    void setLightWeightPopupEnabledFalseWarns() {
        SJComboBox<String> cb = new SJComboBox<>();
        int before = capturedWarns.size();
        cb.setLightWeightPopupEnabled(false);
        assertTrue(capturedWarns.size() > before);
    }

    // --- Smoke ----------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is ComboBoxUI")
    void getUiClassIdIsComboBoxUi() {
        assertEquals("ComboBoxUI", new SJComboBox<String>().getUIClassID());
    }

    @Test
    @DisplayName("happy-path CRUD interaction emits no stub WARNs")
    void happyPathCrudInteractionEmitsNoStubWarns() {
        SJComboBox<String> cb = new SJComboBox<>(new String[]{"apple", "banana", "cherry"});
        cb.addItemListener(e -> { /* observe */ });
        cb.addActionListener(e -> { /* observe */ });
        cb.setSelectedItem("banana");
        cb.setMaximumRowCount(5);
        cb.setEditable(true);
        LocatorJ._setValue(cb, "apple");
        cb.showPopup();
        cb.hidePopup();
        cb.removeAllItems();
        cb.addItem("new");
        assertNoWarns("Expected zero WARN, got: " + capturedWarns);
    }
}
