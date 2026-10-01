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
import com.vaadin.swingbridge.surrogates.SJRadioButton;
import com.vaadin.swingbridge.surrogates.VaadinRadioButton;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.awt.event.ItemEvent;
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

/**
 * Exit gate for D_jradiobutton — vaadinx.swing.JRadioButton
 * emulator over SJRadioButton peer. R_leaf_peer_lockdown leaf lock-down: peer is
 * hard-coded; the protected (Component peer) ctor is omitted.
 *
 * <p>The ButtonGroup integration tests are the headline coverage —
 * D_jradiobutton's mixin refactor of JToggleButton's peer-dispatch means
 * SJRadioButton peers slot into the existing toggle pipeline (Item-
 * bridge, setSelected, doClick) without per-peer narrowing, and that
 * propagates straight into ButtonGroup's mutex cascade.
 *
 * <p>Mirror coverage to JCheckBoxTest where the contract is identical
 * (ctors, listener fan-out, doClick pulse, peer round-trip); add
 * JRadioButton-specific cases (UIClassID, SJRadioButton peer
 * verification, mixed JCheckBox+JRadioButton group as the headline
 * mixin-generalisation proof).
 */
class JRadioButtonTest extends AbstractKaribuTest {

    /** The peer, as the Vaadin radio button every value assertion below reads through. */
    private static VaadinRadioButton peerOf(JRadioButton rb) {
        return (VaadinRadioButton) rb.getPeer();
    }

    @Test
    @DisplayName("peer is a VaadinRadioButton re-host")
    void peerIsAVaadinRadioButtonReHost() {
        // Stage-2 contract: SJRadioButton IS-A VaadinRadioButton, so
        // Karibu locators that find by VaadinRadioButton.class resolve
        // through to the emulator's peer.
        JRadioButton rb = new JRadioButton();
        assertInstanceOf(VaadinRadioButton.class, rb.getPeer());
    }

    @Test
    @DisplayName("peer is an SJRadioButton surrogate per R_leaf_peer_lockdown lock-down")
    void peerIsAnSjRadioButtonSurrogate() {
        JRadioButton rb = new JRadioButton();
        assertInstanceOf(SJRadioButton.class, rb.getPeer());
    }

    @Test
    @DisplayName("text ctor seeds label and selection")
    void textCtorSeedsLabelAndSelection() {
        JRadioButton rb = new JRadioButton("Yes", true);
        assertEquals("Yes", rb.getText());
        assertTrue(rb.isSelected());
        assertEquals("Yes", peerOf(rb).getLabel());
        assertTrue(peerOf(rb).getValue());
    }

    @Test
    @DisplayName("default selection is false")
    void defaultSelectionIsFalse() {
        assertFalse(new JRadioButton("plain").isSelected());
    }

    @Test
    @DisplayName("setText routes label onto VaadinRadioButton peer via HasLabel")
    void setTextRoutesLabelOntoPeerViaHasLabel() {
        // VaadinRadioButton isn't HasText — same situation as Checkbox —
        // so AbstractButton.setText's HasText branch doesn't push. The
        // D_jradiobutton refactor generalised JToggleButton.setText from
        // `instanceof Checkbox` to `instanceof HasLabel`, which catches
        // VaadinRadioButton uniformly.
        JRadioButton rb = new JRadioButton("one");
        rb.setText("two");
        assertEquals("two", rb.getText());
        assertEquals("two", peerOf(rb).getLabel());
    }

    @Test
    @DisplayName("setSelected mirrors into the peer value")
    void setSelectedMirrorsIntoThePeerValue() {
        // R_swing_is_truth setter → peer path under the preventPeerEvents guard. After
        // D_jradiobutton this routes through abm.setSelected(b) for the surrogate
        // branch; SJRadioButton's mixin-supplied setSelected fires
        // ItemEvent + ChangeEvent + pushes peer value.
        JRadioButton rb = new JRadioButton();
        rb.setSelected(true);
        assertTrue(peerOf(rb).getValue());
        rb.setSelected(false);
        assertFalse(peerOf(rb).getValue());
    }

    @Test
    @DisplayName("peer value change mirrors into selected field")
    void peerValueChangeMirrorsIntoSelectedField() {
        // R_swing_is_truth peer → setter path: setting VaadinRadioButton's checked
        // (simulating a browser-side toggle) updates isSelected via the
        // ValueChange wire on the surrogate, which pulses the emulator's
        // own model.
        JRadioButton rb = new JRadioButton();
        peerOf(rb).setValue(true);
        assertTrue(rb.isSelected());
        peerOf(rb).setValue(false);
        assertFalse(rb.isSelected());
    }

    @Test
    @DisplayName("setSelected does not cause infinite feedback loop")
    void setSelectedDoesNotCauseInfiniteFeedbackLoop() {
        JRadioButton rb = new JRadioButton();
        List<ItemEvent> events = new ArrayList<>();
        rb.addItemListener(events::add);
        rb.setSelected(true);
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setSelected fires ItemEvent with SELECTED state and JRadioButton source")
    void setSelectedFiresSelectedItemEvent() {
        JRadioButton rb = new JRadioButton("Pick");
        List<ItemEvent> events = new ArrayList<>();
        rb.addItemListener(events::add);

        rb.setSelected(true);

        ItemEvent e = assertSingle(events);
        assertSame(rb, e.getSource());
        assertEquals(ItemEvent.SELECTED, e.getStateChange());
        assertSame(rb, e.getItem());
    }

    @Test
    @DisplayName("setSelected fires ItemEvent with DESELECTED on deselection")
    void setSelectedFiresDeselectedItemEvent() {
        JRadioButton rb = new JRadioButton("Pick", true);
        List<ItemEvent> events = new ArrayList<>();
        rb.addItemListener(events::add);

        rb.setSelected(false);

        assertEquals(ItemEvent.DESELECTED, assertSingle(events).getStateChange());
    }

    @Test
    @DisplayName("setSelected fires ChangeEvent")
    void setSelectedFiresChangeEvent() {
        JRadioButton rb = new JRadioButton();
        Counter hits = new Counter();
        rb.addChangeListener(e -> hits.inc());
        rb.setSelected(true);
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setSelected to same value is a no-op")
    void setSelectedToSameValueIsANoOp() {
        JRadioButton rb = new JRadioButton();
        Counter hits = new Counter();
        rb.addItemListener(e -> hits.inc());
        rb.setSelected(false);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("peer-originated toggle fires ItemEvent and ChangeEvent")
    void peerOriginatedToggleFiresItemAndChangeEvents() {
        JRadioButton rb = new JRadioButton("Subscribe");
        List<Integer> itemHits = new ArrayList<>();
        Counter changeHits = new Counter();
        rb.addItemListener(e -> itemHits.add(e.getStateChange()));
        rb.addChangeListener(e -> changeHits.inc());

        peerOf(rb).setValue(true);

        assertEquals(List.of(ItemEvent.SELECTED), itemHits);
        assertTrue(changeHits.get() > 0, "Expected at least one ChangeEvent");
    }

    @Test
    @DisplayName("doClick toggles and fires ItemEvent then ActionEvent")
    void doClickTogglesAndFiresItemThenAction() {
        // doClick serialises the event sequence deterministically via
        // ToggleButtonModel pulse. D_jradiobutton routes through abm.doClick — the
        // mixin's default that synthesises armed/pressed via the model.
        JRadioButton rb = new JRadioButton("toggle");
        List<String> events = new ArrayList<>();
        rb.addItemListener(e -> events.add("item:" + e.getStateChange()));
        rb.addActionListener(e -> events.add("action:" + e.getActionCommand()));

        rb.doClick();

        assertTrue(rb.isSelected());
        assertEquals(List.of("item:" + ItemEvent.SELECTED, "action:toggle"), events);
    }

    @Test
    @DisplayName("getSelectedObjects returns text when selected and null otherwise")
    void getSelectedObjectsReturnsTextWhenSelected() {
        JRadioButton rb = new JRadioButton("choice");
        assertNull(rb.getSelectedObjects());
        rb.setSelected(true);
        assertArrayEquals(new Object[]{"choice"}, rb.getSelectedObjects());
    }

    @Test
    @DisplayName("user click while hosted drives ItemListener end-to-end")
    void userClickWhileHostedDrivesItemListener() {
        JRadioButton rb = new JRadioButton("Pick");
        JFrame frame = new JFrame();
        frame.add(rb);
        frame.setVisible(true);

        List<Integer> events = new ArrayList<>();
        rb.addItemListener(e -> events.add(e.getStateChange()));

        LocatorJ._setValue(LocatorJ._get(VaadinRadioButton.class), true);

        assertTrue(rb.isSelected());
        assertEquals(List.of(ItemEvent.SELECTED), events);
    }

    @Test
    @DisplayName("getUIClassID is RadioButtonUI")
    void getUiClassIdIsRadioButtonUi() {
        assertEquals("RadioButtonUI", new JRadioButton().getUIClassID());
    }

    // --- ButtonGroup integration (headline coverage) ----------------

    @Test
    @DisplayName("ButtonGroup-coordinated setSelected enforces select-one-of-N")
    void buttonGroupCoordinatedSetSelectedEnforcesSelectOneOfN() {
        // Standard mutex via setSelected — consults the group, the second
        // setSelected(true) cascades a deselect to the first.
        JRadioButton a = new JRadioButton("A");
        JRadioButton b = new JRadioButton("B");
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);
        a.setSelected(true);
        b.setSelected(true);
        assertFalse(a.isSelected());
        assertTrue(b.isSelected());
    }

    @Test
    @DisplayName("ButtonGroup deselect cascade fires DESELECTED on the prior selection")
    void buttonGroupDeselectCascadeFiresDeselected() {
        // The cascade flows: b's model consults the group, which calls
        // setSelected(false) on a's model → it fires DESELECTED → a's
        // Handler re-fires it at the emulator level.
        JRadioButton a = new JRadioButton("A", true);
        JRadioButton b = new JRadioButton("B");
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);

        List<Integer> aEvents = new ArrayList<>();
        a.addItemListener(e -> aEvents.add(e.getStateChange()));

        b.setSelected(true);

        assertFalse(a.isSelected(), "Group cascade must deselect prior selection");
        assertEquals(List.of(ItemEvent.DESELECTED), aEvents,
                "Deselected sibling must observe DESELECTED");
    }

    @Test
    @DisplayName("ButtonGroup browser-click bridge cascades deselect to prior selection")
    void buttonGroupBridgeCascadesDeselect() {
        // Direct surrogate-side toggle = simulates a browser click. It
        // pulses the emulator's model, whose setSelected consults the
        // group, which cascades the deselect to the prior selection.
        JRadioButton a = new JRadioButton("A", true);
        JRadioButton b = new JRadioButton("B");
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);

        ((SJRadioButton) b.getPeer()).setSelected(true);

        assertFalse(a.isSelected(), "Group cascade must deselect prior selection through the bridge");
        assertTrue(b.isSelected());
        // Peer must also reflect the deselected state.
        assertFalse(peerOf(a).getValue(), "Deselected sibling's peer must have checked=false");
    }

    @Test
    @DisplayName("ButtonGroup browser-click bridge vetoes click-to-deselect-current")
    void buttonGroupBridgeVetoesClickToDeselectCurrent() {
        // User clicks the lone-selected radio trying to deselect it.
        // The bridge consults the group, group keeps the selection,
        // bridge pushes the verdict back to the surrogate via guarded
        // abm.setSelected. Net: radio stays selected, no user-facing
        // ItemEvent.
        JRadioButton a = new JRadioButton("A", true);
        ButtonGroup g = new ButtonGroup();
        g.add(a);

        Counter aItemFired = new Counter();
        a.addItemListener(e -> aItemFired.inc());

        ((SJRadioButton) a.getPeer()).setSelected(false);

        assertTrue(a.isSelected(), "Lone-selected radio must not deselect on click");
        aItemFired.assertEquals(0);
    }

    @Test
    @DisplayName("mixed JCheckBox plus JRadioButton in one ButtonGroup coordinate via D_jradiobutton mixin path")
    void mixedCheckBoxAndRadioButtonInOneGroupCoordinate() {
        // Headline coverage for D_jradiobutton: a single ButtonGroup containing a
        // JCheckBox (peer SJCheckBox) and a JRadioButton (peer
        // SJRadioButton) coordinates correctly. Pre-D_jradiobutton the JToggleButton
        // bridge narrowed on `peer instanceof SJToggleButton` — SJRadioButton
        // wouldn't match so its toggle would be invisible to the group.
        // Post-D_jradiobutton both peers implement AbstractButtonMixin, both
        // bridges install, the group sees both, and mutex works.
        JCheckBox cb = new JCheckBox("CB-A", true);
        JRadioButton rb = new JRadioButton("RB-B");
        ButtonGroup g = new ButtonGroup();
        g.add(cb);
        g.add(rb);

        rb.setSelected(true);

        assertFalse(cb.isSelected(), "JCheckBox sibling must be deselected by group cascade");
        assertTrue(rb.isSelected());
    }
}
