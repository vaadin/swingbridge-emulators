/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJComboBox;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Emulator tests for the thin {@code JComboBox} shell over
 * {@code com.vaadin.swingbridge.surrogates.SJComboBox} (SD_sjcombobox + D_jcombobox). The surrogate carries the
 * model / R_swing_is_truth / R_callswing_envelope plumbing — coverage for that lives in
 * {@code com.vaadin.swingbridge.surrogates.SJComboBoxTest}. These tests focus on the emulator's
 * responsibilities: JDK-shape API delegation, two-layer event re-sourcing
 * to the emulator (Item / Action / Popup / ListData), independent PCE
 * listener lists, the ListCellRenderer bridge boundary, and R_leaf_peer_lockdown lock-down.
 */
class JComboBoxTest extends AbstractKaribuTest {

    @Test
    @DisplayName("peer is SJComboBox per surrogate-first thin-down")
    void peerIsSJComboBoxPerSurrogateFirstThinDown() {
        JComboBox<String> cb = new JComboBox<>();
        assertInstanceOf(SJComboBox.class, cb.getPeer());
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — only public ctors are accessible")
    void rLeafPeerLockdownLockDownOnlyPublicCtorsAreAccessible() {
        // R_leaf_peer_lockdown: leaf class. The protected (Component peer) ctor must not
        // exist on JComboBox; only the public ctors. Reflection check
        // since visibility-only enforcement isn't otherwise observable.
        List<Constructor<?>> protectedCtors = Arrays.stream(JComboBox.class.getDeclaredConstructors())
                .filter(it -> java.lang.reflect.Modifier.isProtected(it.getModifiers()))
                .toList();
        assertTrue(protectedCtors.isEmpty(),
                "R_leaf_peer_lockdown: no protected ctor allowed on JDK-leaf JComboBox; got: " + protectedCtors);
    }

    @Test
    @DisplayName("array ctor seeds model and selects first element")
    void arrayCtorSeedsModelAndSelectsFirstElement() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b", "c"});
        assertEquals(3, cb.getItemCount());
        assertEquals("a", cb.getSelectedItem());
    }

    // --- Two-layer ItemListener fan-out --------------------------------

    @Test
    @DisplayName("setSelectedItem fires Item pair on emulator with source rebound")
    void setSelectedItemFiresItemPairOnEmulatorWithSourceRebound() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b"});
        List<ItemEvent> events = new ArrayList<>();
        cb.addItemListener(it -> events.add(it));
        cb.setSelectedItem("b");
        assertEquals(2, events.size());
        assertEquals(ItemEvent.DESELECTED, events.get(0).getStateChange());
        assertEquals("a", events.get(0).getItem());
        assertEquals(ItemEvent.SELECTED, events.get(1).getStateChange());
        assertEquals("b", events.get(1).getItem());
        // Source rebound to emulator
        assertSame(cb, events.get(0).getSource());
        assertSame(cb, events.get(1).getSource());
    }

    @Test
    @DisplayName("independent listener lists — emulator-only listener does not fire on surrogate-only registration")
    void independentListenerListsEmulatorOnlyListenerDoesNotFireOnSurrogateOnlyRegistration() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b"});
        Counter emulatorFires = new Counter();
        Counter surrogateFires = new Counter();
        cb.addItemListener(it -> emulatorFires.inc());
        @SuppressWarnings("unchecked")
        SJComboBox<String> sb = (SJComboBox<String>) cb.getPeer();
        sb.addItemListener(it -> surrogateFires.inc());
        cb.setSelectedItem("b");
        // Both layers fire on selection commit (surrogate is the
        // primary source, emulator bridges through). Two events per
        // layer for the DESELECTED + SELECTED pair.
        emulatorFires.assertEquals(2);
        surrogateFires.assertEquals(2);
    }

    // --- ActionListener bridge ----------------------------------------

    @Test
    @DisplayName("setSelectedItem fires ActionEvent with emulator's actionCommand")
    void setSelectedItemFiresActionEventWithEmulatorsActionCommand() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b"});
        cb.setActionCommand("myCmd");
        List<ActionEvent> actions = new ArrayList<>();
        cb.addActionListener(it -> actions.add(it));
        cb.setSelectedItem("b");
        assertEquals(1, actions.size());
        assertEquals("myCmd", actions.get(0).getActionCommand());
        assertSame(cb, actions.get(0).getSource());
    }

    @Test
    @DisplayName("default actionCommand is comboBoxChanged when not set")
    void defaultActionCommandIsComboBoxChangedWhenNotSet() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b"});
        assertEquals("comboBoxChanged", cb.getActionCommand());
        List<ActionEvent> actions = new ArrayList<>();
        cb.addActionListener(it -> actions.add(it));
        cb.setSelectedItem("b");
        assertEquals("comboBoxChanged", actions.get(0).getActionCommand());
    }

    // --- Peer ValueChange round-trip ---------------------------------

    @Test
    @DisplayName("peer setValue propagates through both layers")
    void peerSetValuePropagatesThroughBothLayers() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b", "c"});
        Counter emulatorFires = new Counter();
        cb.addItemListener(it -> emulatorFires.inc());
        @SuppressWarnings("unchecked")
        SJComboBox<String> sb = (SJComboBox<String>) cb.getPeer();
        LocatorJ._setValue(sb, "c");
        assertEquals("c", cb.getSelectedItem());
        emulatorFires.assertEquals(2); // DESELECTED a + SELECTED c on emulator
    }

    @Test
    @DisplayName("a browser pick reaches the combo's listeners inside a UI fiber, so they can open a modal dialog")
    void browserPickRunsInAUiFiber() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b", "c"});
        com.vaadin.flow.component.UI.getCurrent().add(cb.getPeer());
        List<Boolean> inFiber = new ArrayList<>();
        cb.addItemListener(e -> inFiber.add(com.github.mvysny.blockingdialogs.UIFibers.isInUIFiber()));
        cb.addActionListener(e -> inFiber.add(com.github.mvysny.blockingdialogs.UIFibers.isInUIFiber()));
        @SuppressWarnings("unchecked")
        SJComboBox<String> sb = (SJComboBox<String>) cb.getPeer();
        LocatorJ._setValue(sb, "c");
        assertEquals(List.of(true, true, true), inFiber); // DESELECTED a, SELECTED c, the ActionEvent
    }

    // --- Editable ----------------------------------------------------

    @Test
    @DisplayName("setEditable propagates and fires emulator-side PCE")
    void setEditablePropagatesAndFiresEmulatorSidePce() {
        JComboBox<String> cb = new JComboBox<>();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cb.addPropertyChangeListener("editable", it -> pces.add(it));
        cb.setEditable(true);
        assertTrue(cb.isEditable());
        @SuppressWarnings("unchecked")
        SJComboBox<String> sb = (SJComboBox<String>) cb.getPeer();
        assertTrue(sb.isEditable());
        assertEquals(1, pces.size());
    }

    // --- Items API ---------------------------------------------------

    @Test
    @DisplayName("addItem + removeItem propagate to model")
    void addItemPlusRemoveItemPropagateToModel() {
        JComboBox<String> cb = new JComboBox<>();
        cb.addItem("first");
        cb.addItem("second");
        assertEquals(2, cb.getItemCount());
        cb.removeItem("first");
        assertEquals(1, cb.getItemCount());
        assertEquals("second", cb.getItemAt(0));
    }

    // --- Model PCE ---------------------------------------------------

    @Test
    @DisplayName("setModel fires emulator-local model PCE")
    void setModelFiresEmulatorLocalModelPce() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"old"});
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cb.addPropertyChangeListener("model", it -> pces.add(it));
        DefaultComboBoxModel<String> newModel = new DefaultComboBoxModel<>(new String[]{"new1", "new2"});
        cb.setModel(newModel);
        assertEquals(1, pces.size());
        assertSame(newModel, cb.getModel());
    }

    // --- The model subscription (the JDK's event engine) ------------

    @Test
    @DisplayName("the combo is its model's ListDataListener, moved by setModel, as in the JDK")
    void theComboIsItsModelsListDataListener() {
        DefaultComboBoxModel<String> first = new DefaultComboBoxModel<>();
        JComboBox<String> cb = new JComboBox<>(first);
        assertTrue(Arrays.asList(first.getListDataListeners()).contains(cb));

        DefaultComboBoxModel<String> second = new DefaultComboBoxModel<>();
        cb.setModel(second);
        assertFalse(Arrays.asList(first.getListDataListeners()).contains(cb));
        assertTrue(Arrays.asList(second.getListDataListeners()).contains(cb));
    }

    @Test
    @DisplayName("setSelectedItem fires one ActionEvent even when the model echoes the change")
    void setSelectedItemFiresOneActionEvent() {
        JComboBox<String> cb = new JComboBox<>(new String[]{"a", "b"});
        Counter actions = new Counter();
        cb.addActionListener(e -> actions.inc());
        cb.setSelectedItem("b");
        actions.assertEquals(1);
        // Re-selecting the same item still fires, as in the JDK.
        cb.setSelectedItem("b");
        actions.assertEquals(2);
    }

    @Test
    @DisplayName("item listeners are notified last to first, as in the JDK")
    void itemListenersAreNotifiedLastToFirst() {
        JComboBox<String> cb = new JComboBox<>(new String[]{"a", "b"});
        List<String> order = new ArrayList<>();
        cb.addItemListener(e -> { if (e.getStateChange() == ItemEvent.SELECTED) order.add("first"); });
        cb.addItemListener(e -> { if (e.getStateChange() == ItemEvent.SELECTED) order.add("second"); });
        cb.setSelectedItem("b");
        assertEquals(List.of("second", "first"), order);
    }

    // --- Popup events ------------------------------------------------

    @Test
    @DisplayName("showPopup + hidePopup fire emulator's PopupMenuListeners with rebound source")
    void showPopupPlusHidePopupFireEmulatorsPopupMenuListenersWithReboundSource() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b"});
        // Showing, or the opening call throws the way a real JComboBox does —
        // see popupCannotOpenOnANonShowingComboBox below.
        com.vaadin.flow.component.UI.getCurrent().getElement()
                .appendChild(cb.getPeer().getElement());
        List<PopupMenuEvent> events = new ArrayList<>();
        cb.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { events.add(e); }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { events.add(e); }
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
        cb.showPopup();
        cb.hidePopup();
        assertEquals(2, events.size());
        // Source rebound to emulator on the bridge re-fire.
        assertSame(cb, events.get(0).getSource());
        assertSame(cb, events.get(1).getSource());
    }

    @Test
    @DisplayName("a user's open and close fire the emulator's PopupMenuListeners")
    void userOpenAndCloseFireEmulatorsPopupMenuListeners() throws Exception {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a", "b"});
        com.vaadin.flow.component.UI.getCurrent().getElement()
                .appendChild(cb.getPeer().getElement());
        List<String> events = new ArrayList<>();
        cb.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                assertSame(cb, e.getSource());
                events.add("visible");
            }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { events.add("invisible"); }
            @Override public void popupMenuCanceled(PopupMenuEvent e) { events.add("canceled"); }
        });

        setOpenedFromClient(cb, true);
        assertEquals(List.of("visible"), events);
        assertTrue(cb.isPopupVisible());
        setOpenedFromClient(cb, false);
        assertEquals(List.of("visible", "invisible"), events,
                "the browser cannot tell a cancel from a pick, so no popupMenuCanceled");
        assertFalse(cb.isPopupVisible());
    }

    /** What the browser's {@code opened-changed} synchronization does to the peer. */
    private static void setOpenedFromClient(JComboBox<?> cb, boolean opened) throws Exception {
        cb.getPeer().getElement().getNode()
                .getFeature(com.vaadin.flow.internal.nodefeature.ElementPropertyMap.class)
                .deferredUpdateFromClient("opened", opened).run();
    }


    // --- maximumRowCount + PCE ---------------------------------------

    @Test
    @DisplayName("setMaximumRowCount propagates and fires emulator PCE")
    void setMaximumRowCountPropagatesAndFiresEmulatorPce() {
        JComboBox<String> cb = new JComboBox<>();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cb.addPropertyChangeListener("maximumRowCount", it -> pces.add(it));
        cb.setMaximumRowCount(12);
        assertEquals(12, cb.getMaximumRowCount());
        assertEquals(1, pces.size());
    }

    // --- ListCellRenderer bridge -------------------------------------

    @Test
    @DisplayName("setRenderer installs and is readable via getRenderer")
    void setRendererInstallsAndIsReadableViaGetRenderer() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"apple", "banana"});
        ListCellRenderer<String> r = (list, value, index, isSelected, cellHasFocus) -> new JLabel("custom: " + value);
        cb.setRenderer(r);
        // The bridge converts the JDK-shaped renderer into a Vaadin
        // ComponentRenderer; the JDK-shaped one round-trips through
        // getRenderer so migrated code that reads it back works.
        // Karibu doesn't simulate the dropdown render pipeline, so
        // we assert the registration boundary, not the rendered DOM.
        assertSame((Object) r, (Object) cb.getRenderer());
    }

    @Test
    @DisplayName("setRenderer null restores default toString rendering")
    void setRendererNullRestoresDefaultToStringRendering() {
        JComboBox<String> cb = new JComboBox<String>(new String[]{"a"});
        ListCellRenderer<String> r = (list, v, index, isSelected, cellHasFocus) -> new JLabel("custom " + v);
        cb.setRenderer(r);
        assertSame((Object) r, (Object) cb.getRenderer());
        cb.setRenderer(null);
        assertNull(cb.getRenderer());
    }

    @Test
    @DisplayName("DefaultListCellRenderer renders value via toString")
    void defaultListCellRendererRendersValueViaToString() {
        DefaultListCellRenderer r = new DefaultListCellRenderer();
        vaadinx.awt.Component out = r.getListCellRendererComponent(null, "hello", 0, false, false);
        assertSame(r, out);
        assertEquals("hello", r.getText());
    }

    @Test
    @DisplayName("renderer snapshot returns fresh Span per call with distinct text")
    void rendererSnapshotReturnsFreshSpanPerCallWithDistinctText() {
        // Regression for the "JDK mutate-and-return-self vs Vaadin
        // fresh-per-call" mismatch: a DefaultListCellRenderer subclass
        // that returns `this` (the same JLabel instance) on every
        // invocation must yield distinct Vaadin Components per dropdown
        // item, each carrying the right text. Without the bridge's
        // per-call snapshot, all positions would share the same DOM
        // node and only the last one would render.
        DefaultListCellRenderer r = new DefaultListCellRenderer() {
            @Override
            public vaadinx.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                vaadinx.awt.Component out = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText("- " + value);
                return out;
            }
        };
        // Drive the same flow the bridge runs per item.
        vaadinx.awt.Component out1 = r.getListCellRendererComponent(null, "red", 0, false, false);
        com.vaadin.flow.component.Component snap1 = JComboBox.snapshotRendererOutput(out1, "red");
        vaadinx.awt.Component out2 = r.getListCellRendererComponent(null, "green", 1, false, false);
        com.vaadin.flow.component.Component snap2 = JComboBox.snapshotRendererOutput(out2, "green");
        vaadinx.awt.Component out3 = r.getListCellRendererComponent(null, "blue", 2, false, false);
        com.vaadin.flow.component.Component snap3 = JComboBox.snapshotRendererOutput(out3, "blue");

        // Each snapshot is a distinct Component instance (fresh-per-call).
        assertTrue(snap1 != snap2);
        assertTrue(snap2 != snap3);
        assertTrue(snap1 != snap3);
        // Each carries the renderer's mutated text — not just the last
        // value (which was the pre-fix bug).
        assertEquals("- red", snap1.getElement().getText());
        assertEquals("- green", snap2.getElement().getText());
        assertEquals("- blue", snap3.getElement().getText());
    }

    @Test
    @DisplayName("renderer snapshot for non-JLabel WARNs and falls back to toString")
    void rendererSnapshotForNonJLabelWarnsAndFallsBackToToString() {
        // Non-JLabel-shaped renderers can't be snapshotted faithfully
        // (R_match_swing_errors sub-bucket (a)) — bridge falls back to a
        // String.valueOf(item) Span and emits a WARN.
        List<String> warns = new ArrayList<>();
        vaadinx.EHelper.warnHook = it -> warns.add(it);
        try {
            ListCellRenderer<String> r = (list, value, index, isSelected, cellHasFocus) -> {
                // Return a JButton — not JLabel-shaped, no faithful
                // snapshot path.
                return new JButton(value);
            };
            vaadinx.awt.Component out = r.getListCellRendererComponent(null, "ok", 0, false, false);
            com.vaadin.flow.component.Component snap = JComboBox.snapshotRendererOutput(out, "ok");
            // Fallback Span carries the model value's toString.
            assertEquals("ok", snap.getElement().getText());
            assertTrue(warns.stream().anyMatch(it -> it.contains("non-JLabel")),
                    "Expected WARN for non-JLabel renderer; got: " + warns);
        } finally {
            vaadinx.EHelper.warnHook = it -> { /* no-op default */ };
        }
    }

    @Test
    @DisplayName("renderer snapshot for JCheckBox unwraps to read-only Vaadin Checkbox")
    void rendererSnapshotForJCheckBoxUnwrapsToReadOnlyVaadinCheckbox() {
        // JTable's Boolean cell renderer returns a JCheckBox configured with
        // setSelected(value). snapshotRendererOutput must turn that into a
        // fresh Vaadin Checkbox carrying the same selected state, so the
        // grid cell renders a checkbox glyph instead of "true"/"false" text.
        List<String> warns = new ArrayList<>();
        vaadinx.EHelper.warnHook = it -> warns.add(it);
        try {
            JCheckBox checked = new JCheckBox();
            checked.setSelected(true);
            com.vaadin.flow.component.Component snapT = JComboBox.snapshotRendererOutput(checked, java.lang.Boolean.TRUE);
            JCheckBox unchecked = new JCheckBox();
            unchecked.setSelected(false);
            com.vaadin.flow.component.Component snapF = JComboBox.snapshotRendererOutput(unchecked, java.lang.Boolean.FALSE);

            assertTrue(snapT instanceof com.vaadin.flow.component.checkbox.Checkbox,
                    "snapshot should be a Vaadin Checkbox, got " + snapT.getClass().getCanonicalName());
            assertTrue(snapF instanceof com.vaadin.flow.component.checkbox.Checkbox);
            assertEquals(true, ((com.vaadin.flow.component.checkbox.Checkbox) snapT).getValue());
            assertEquals(false, ((com.vaadin.flow.component.checkbox.Checkbox) snapF).getValue());
            assertTrue(((com.vaadin.flow.component.checkbox.Checkbox) snapT).isReadOnly(),
                    "snapshot must be read-only (non-interactive cell render)");
            assertTrue(((com.vaadin.flow.component.checkbox.Checkbox) snapF).isReadOnly());
            assertNoWarns(warns, "JCheckBox snapshot path should not WARN; got " + warns);
        } finally {
            vaadinx.EHelper.warnHook = it -> { /* no-op default */ };
        }
    }

    // --- setAction surface -------------------------------------------

    @Test
    @DisplayName("setAction propagates SHORT_DESCRIPTION + ACTION_COMMAND_KEY + enabled")
    void setActionPropagatesShortDescriptionPlusActionCommandKeyPlusEnabled() {
        JComboBox<String> cb = new JComboBox<>();
        Counter fired = new Counter();
        Action a = new AbstractAction("ignored-name") {
            @Override
            public void actionPerformed(ActionEvent e) { fired.inc(); }
        };
        a.putValue(Action.SHORT_DESCRIPTION, "tt");
        a.putValue(Action.ACTION_COMMAND_KEY, "cmd");
        a.setEnabled(false);
        cb.setAction(a);
        assertSame(a, cb.getAction());
        assertEquals("cmd", cb.getActionCommand());
        assertFalse(cb.isEnabled());
        // The Action is an ActionListener: adding the first item selects it and fires once,
        // and re-selecting it fires again, as in the JDK (measured on JDK 25).
        cb.addItem("one");
        cb.setSelectedItem("one");
        fired.assertEquals(2);
    }

    @Test
    @DisplayName("the item and action event sequence is the JDK's, measured on JDK 25")
    void eventSequenceIsTheJdks() {
        JComboBox<String> cb = new JComboBox<>();
        List<String> ev = new ArrayList<>();
        cb.addActionListener(e -> ev.add("action:" + e.getActionCommand()));
        cb.addItemListener(e -> ev.add((e.getStateChange() == ItemEvent.SELECTED ? "sel " : "desel ") + e.getItem()));

        cb.addItem("one");
        ev.add("|");
        cb.setSelectedItem("one");
        ev.add("|");
        cb.addItem("two");
        ev.add("|");
        cb.setSelectedItem("zzz"); // not in the list, not editable: rejected silently
        ev.add("|");
        cb.setSelectedIndex(1);
        ev.add("|");
        cb.removeAllItems();
        ev.add("|");

        // javax.swing.JComboBox, same calls, JDK 25 headless.
        assertEquals(List.of("sel one", "action:comboBoxChanged", "|", "action:comboBoxChanged", "|", "|", "|",
                "desel one", "sel two", "action:comboBoxChanged", "|", "desel two", "action:comboBoxChanged", "|"), ev);
    }
}
