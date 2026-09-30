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
import com.vaadin.flow.component.checkbox.Checkbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJCheckBox;
import com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JCheckBoxTest extends AbstractKaribuTest {

    /** The peer, as the Vaadin Checkbox every value assertion below reads through. */
    private static Checkbox peerOf(JCheckBox cb) {
        return (Checkbox) cb.getPeer();
    }

    @Test
    @DisplayName("can instantiate with Checkbox peer")
    void canInstantiateWithCheckboxPeer() {
        JCheckBox cb = new JCheckBox();
        // SJCheckBox IS-A Checkbox per SD_toggle_checkbox_first_cut — Karibu locators that find
        // by Checkbox.class still resolve.
        assertInstanceOf(Checkbox.class, cb.getPeer());
    }

    @Test
    @DisplayName("peer is an SJCheckBox surrogate")
    void peerIsAnSjCheckBoxSurrogate() {
        JCheckBox cb = new JCheckBox();
        assertInstanceOf(SJCheckBox.class, cb.getPeer());
    }

    @Test
    @DisplayName("doClick fires ChangeEvent through the bridged surrogate model pulse")
    void doClickFiresChangeEventThroughTheModelPulse() {
        // doClick delegates to the surrogate's doClick → ToggleButtonModel
        // pulse → multiple ChangeEvents (setArmed / setPressed beats) →
        // bridge → emulator listeners, exactly as a real L&F does.
        JCheckBox cb = new JCheckBox("toggle");
        Counter changeHits = new Counter();
        cb.addChangeListener(e -> changeHits.inc());

        cb.doClick();

        assertTrue(changeHits.get() >= 1,
                "doClick should fire at least one ChangeEvent via surrogate model bridge (got " + changeHits + ")");
    }

    @Test
    @DisplayName("ButtonModel selection state survives setText and setEnabled cycles")
    void selectionSurvivesSetTextAndSetEnabledCycles() {
        // Regression: setText / setEnabled go through the mixin chain
        // (HasLabel + HasEnabled). Neither should reset model.selected.
        JCheckBox cb = new JCheckBox("initial", true);
        assertTrue(cb.isSelected());

        cb.setText("renamed");
        assertTrue(cb.isSelected(), "setText must not reset selection");

        cb.setEnabled(false);
        assertTrue(cb.isSelected(), "setEnabled(false) must not reset selection");

        cb.setEnabled(true);
        assertTrue(cb.isSelected(), "setEnabled(true) must not reset selection");
    }

    @Test
    @DisplayName("text ctor seeds label and selection")
    void textCtorSeedsLabelAndSelection() {
        JCheckBox cb = new JCheckBox("Agree", true);
        assertEquals("Agree", cb.getText());
        assertTrue(cb.isSelected());
        assertEquals("Agree", peerOf(cb).getLabel());
        assertTrue(peerOf(cb).getValue());
    }

    @Test
    @DisplayName("default selection is false")
    void defaultSelectionIsFalse() {
        // Matches Swing JCheckBox — freshly constructed checkbox is
        // not selected unless the ctor passes selected=true.
        assertFalse(new JCheckBox("plain").isSelected());
    }

    @Test
    @DisplayName("setText routes label onto Checkbox peer")
    void setTextRoutesLabelOntoCheckboxPeer() {
        // Vaadin Checkbox isn't HasText — AbstractButton.setText would
        // otherwise silently drop the write. JToggleButton's override
        // routes through Checkbox.setLabel.
        JCheckBox cb = new JCheckBox("one");
        cb.setText("two");
        assertEquals("two", cb.getText());
        assertEquals("two", peerOf(cb).getLabel());
    }

    @Test
    @DisplayName("setSelected mirrors into the peer value")
    void setSelectedMirrorsIntoThePeerValue() {
        // R_swing_is_truth setter → peer path under the preventPeerEvents guard.
        JCheckBox cb = new JCheckBox();
        cb.setSelected(true);
        assertTrue(peerOf(cb).getValue());
        cb.setSelected(false);
        assertFalse(peerOf(cb).getValue());
    }

    @Test
    @DisplayName("peer value change mirrors into selected field")
    void peerValueChangeMirrorsIntoSelectedField() {
        // R_swing_is_truth peer → setter path: setting the Checkbox peer's value
        // (simulating a user toggle in the browser) updates isSelected.
        JCheckBox cb = new JCheckBox();
        peerOf(cb).setValue(true);
        assertTrue(cb.isSelected());
        peerOf(cb).setValue(false);
        assertFalse(cb.isSelected());
    }

    @Test
    @DisplayName("setSelected does not cause infinite feedback loop")
    void setSelectedDoesNotCauseInfiniteFeedbackLoop() {
        // Regression for preventPeerEvents: setSelected → push to
        // peer → peer fires ValueChange synchronously → our listener
        // would re-enter setSelected without the guard. One
        // ItemEvent per call is the contract.
        JCheckBox cb = new JCheckBox();
        List<ItemEvent> events = new ArrayList<>();
        cb.addItemListener(events::add);
        cb.setSelected(true);
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setSelected fires ItemEvent with SELECTED state and JCheckBox source")
    void setSelectedFiresSelectedItemEvent() {
        JCheckBox cb = new JCheckBox("Agree");
        List<ItemEvent> events = new ArrayList<>();
        cb.addItemListener(events::add);

        cb.setSelected(true);

        ItemEvent e = assertSingle(events);
        assertSame(cb, e.getSource());
        assertEquals(ItemEvent.SELECTED, e.getStateChange());
        assertSame(cb, e.getItem());
    }

    @Test
    @DisplayName("setSelected fires ItemEvent with DESELECTED on deselection")
    void setSelectedFiresDeselectedItemEvent() {
        JCheckBox cb = new JCheckBox("Agree", true);
        List<ItemEvent> events = new ArrayList<>();
        cb.addItemListener(events::add);

        cb.setSelected(false);

        assertEquals(ItemEvent.DESELECTED, assertSingle(events).getStateChange());
    }

    @Test
    @DisplayName("setSelected fires ChangeEvent")
    void setSelectedFiresChangeEvent() {
        // Swing also fires a ChangeEvent via ButtonModel. We flatten
        // the model but still fire ChangeEvent so migrated MVC wiring
        // keeps working.
        JCheckBox cb = new JCheckBox();
        Counter hits = new Counter();
        cb.addChangeListener(e -> hits.inc());
        cb.setSelected(true);
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setSelected to same value is a no-op")
    void setSelectedToSameValueIsANoOp() {
        // Swing's setSelected on an unchanged value doesn't fire events
        // (ButtonModel guard) — we match.
        JCheckBox cb = new JCheckBox();
        Counter hits = new Counter();
        cb.addItemListener(e -> hits.inc());
        cb.setSelected(false);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("peer-originated toggle fires ItemEvent and ChangeEvent")
    void peerOriginatedToggleFiresItemAndChangeEvents() {
        // Browser-click equivalent: peer.value toggles. The surrogate's
        // ToggleButtonModel pulse fires multiple ChangeEvents (setArmed /
        // setPressed beats), all of which the bridge delivers —
        // JDK-faithful (matches javax.swing.JCheckBox under a real L&F).
        // Item still fires exactly once per actual selection transition.
        JCheckBox cb = new JCheckBox("Subscribe");
        List<Integer> itemHits = new ArrayList<>();
        Counter changeHits = new Counter();
        cb.addItemListener(e -> itemHits.add(e.getStateChange()));
        cb.addChangeListener(e -> changeHits.inc());

        peerOf(cb).setValue(true);

        assertEquals(List.of(ItemEvent.SELECTED), itemHits);
        assertTrue(changeHits.get() > 0, "Expected at least one ChangeEvent");
    }

    @Test
    @DisplayName("doClick toggles and fires ItemEvent then ActionEvent")
    void doClickTogglesAndFiresItemThenAction() {
        // doClick serialises the event sequence deterministically —
        // unlike browser clicks (where Vaadin's ValueChange/Click
        // ordering is implementation-defined, R_best_effort_behaviour-accepted). Users
        // driving programmatic clicks get Swing's canonical order.
        JCheckBox cb = new JCheckBox("toggle");
        List<String> events = new ArrayList<>();
        cb.addItemListener(e -> events.add("item:" + e.getStateChange()));
        cb.addActionListener(e -> events.add("action:" + e.getActionCommand()));

        cb.doClick();

        assertTrue(cb.isSelected());
        assertEquals(List.of("item:" + ItemEvent.SELECTED, "action:toggle"), events);
    }

    @Test
    @DisplayName("getSelectedObjects returns text when selected and null otherwise")
    void getSelectedObjectsReturnsTextWhenSelected() {
        // ItemSelectable contract — AbstractButton override in
        // JToggleButton honours the "array with text on select,
        // null on deselect" JDK shape.
        JCheckBox cb = new JCheckBox("choice");
        assertNull(cb.getSelectedObjects());
        cb.setSelected(true);
        assertArrayEquals(new Object[]{"choice"}, cb.getSelectedObjects());
    }

    @Test
    @DisplayName("user click while hosted drives ItemListener end-to-end")
    void userClickWhileHostedDrivesItemListener() {
        // End-to-end: JCheckBox inside a visible JFrame; driving the
        // Vaadin Checkbox's value simulates a browser toggle; the
        // Swing ItemListener sees the event.
        JCheckBox cb = new JCheckBox("Terms");
        JFrame frame = new JFrame();
        frame.add(cb);
        frame.setVisible(true);

        List<Integer> events = new ArrayList<>();
        cb.addItemListener(e -> events.add(e.getStateChange()));

        LocatorJ._setValue(LocatorJ._get(Checkbox.class), true);

        assertTrue(cb.isSelected());
        assertEquals(List.of(ItemEvent.SELECTED), events);
    }

    @Test
    @DisplayName("setBorderPaintedFlat round-trips and fires PCE")
    void setBorderPaintedFlatRoundTripsAndFiresPce() {
        JCheckBox cb = new JCheckBox();
        List<PropertyChangeEvent> events = new ArrayList<>();
        cb.addPropertyChangeListener("borderPaintedFlat", events::add);

        cb.setBorderPaintedFlat(true);

        assertTrue(cb.isBorderPaintedFlat());
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("getUIClassID is CheckBoxUI")
    void getUiClassIdIsCheckBoxUi() {
        assertEquals("CheckBoxUI", new JCheckBox().getUIClassID());
    }

    @Test
    @DisplayName("ButtonGroup-coordinated setSelected enforces select-one-of-N")
    void buttonGroupCoordinatedSetSelectedEnforcesSelectOneOfN() {
        // JCheckBox extends JToggleButton; setSelected consults the
        // installed group at the top of its body. Two grouped checkboxes
        // can never both be selected.
        JCheckBox a = new JCheckBox("A");
        JCheckBox b = new JCheckBox("B");
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);
        a.setSelected(true);
        b.setSelected(true);
        assertFalse(a.isSelected());
        assertTrue(b.isSelected());
    }

    @Test
    @DisplayName("ButtonGroup browser-click bridge cascades deselect to prior selection")
    void buttonGroupBridgeCascadesDeselect() {
        // Simulates the SJToggleButton→emulator ItemEvent bridge for a
        // user clicking the unselected sibling: the bridge consults the
        // group, sees the click should land, cascades deselect to the
        // currently-selected sibling via the group, and lets the verdict
        // proceed for the click target.
        JCheckBox a = new JCheckBox("A", true);
        JCheckBox b = new JCheckBox("B");
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);

        // Drive the surrogate model directly — same path the browser
        // pulse takes (setSelected pulses ToggleButtonModel which fires
        // Item via the surrogate's bridge, landing in the emulator's
        // bridge listener). Cast to the mixin (JCheckBox peers over
        // SJCheckBox), same interface the emulator dispatches on.
        ((AbstractButtonMixin) b.getPeer()).setSelected(true);

        assertFalse(a.isSelected(), "Group cascade must deselect prior selection through the bridge");
        assertTrue(b.isSelected());
    }

    @Test
    @DisplayName("ButtonGroup browser-click bridge vetoes click-to-deselect-current")
    void buttonGroupBridgeVetoesClickToDeselectCurrent() {
        // User clicks the lone-selected member trying to deselect it.
        // The bridge consults the group, group keeps the selection,
        // bridge pushes the verdict back to the surrogate via guarded
        // sjt.setSelected. Net effect: button stays selected, no
        // user-facing ItemEvent fires (the model's net state didn't
        // change).
        JCheckBox a = new JCheckBox("A", true);
        ButtonGroup g = new ButtonGroup();
        g.add(a);

        Counter aItemFired = new Counter();
        a.addItemListener(e -> aItemFired.inc());

        // Toggle the surrogate's model — same as a browser click on
        // the lone selection. Cast to the mixin (peer is SJCheckBox).
        ((AbstractButtonMixin) a.getPeer()).setSelected(false);

        assertTrue(a.isSelected(), "Lone-selected radio must not deselect on click");
        aItemFired.assertEquals(0);
    }
}
