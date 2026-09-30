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

import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SJRadioButton surrogate. Mirrors the
 * SJToggleButton test suite (same SD_toggle_checkbox_first_cut architecture: ToggleButtonModel
 * source-of-truth, ValueChange-only browser wire, model→peer push) but
 * against the re-hosted {@link VaadinRadioButton} peer instead of Vaadin
 * Checkbox.
 *
 * <p>Per D_buttongroup_browser_click, ButtonGroup mutex coordination lives at the emulator layer
 * (vaadinx.swing.ButtonGroup against vaadinx.swing.AbstractButton
 * subclasses), not at the surrogate layer. The surrogate-side
 * guarantee tested here is: a programmatic setSelected(false) — the
 * shape the emulator group's cascade ultimately calls on a deselected
 * sibling — drives the peer's checked=false under preventPeerEvents,
 * so the visual de-selects.
 */
class SJRadioButtonTest extends AbstractKaribuTest {

    /** The peer's own {@code getValue()}, reached past the surrogate's Swing API. */
    private static boolean peerValue(SJRadioButton b) {
        return ((VaadinRadioButton) b).getValue();
    }

    /** Collects PCEs for one named property. */
    private static List<PropertyChangeEvent> recordPces(SJRadioButton b, String property) {
        final List<PropertyChangeEvent> events = new ArrayList<>();
        b.addPropertyChangeListener(property, events::add);
        return events;
    }

    /** An Action whose body does nothing — only its NAME/enabled matter here. */
    private static AbstractAction inertAction(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
    }

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor leaves text empty per R_vaadin_first lossy via setLabel")
    void noArgCtorTextIsEmpty() {
        // setText(null) writes "" via VaadinRadioButton.setLabel which
        // clears the slot; getText reads back "" per the surrogate's
        // null→"" normalisation. Same R_vaadin_first shape SJToggleButton documents.
        assertEquals("", new SJRadioButton().getText());
    }

    @Test
    @DisplayName("string ctor seeds label and actionCommand falls back to it")
    void stringCtorSeedsLabelAndCommand() {
        final SJRadioButton b = new SJRadioButton("Option A");
        assertEquals("Option A", b.getText());
        assertEquals("Option A", ((VaadinRadioButton) b).getLabel());
        assertEquals("Option A", b.getActionCommand());
    }

    @Test
    @DisplayName("text + selected ctor seeds both")
    void textAndSelectedCtor() {
        final SJRadioButton b = new SJRadioButton("Yes", true);
        assertEquals("Yes", b.getText());
        assertTrue(b.isSelected());
        assertTrue(peerValue(b));
    }

    @Test
    @DisplayName("setText routes through peer setLabel")
    void setTextRoutesThroughLabel() {
        final SJRadioButton b = new SJRadioButton("one");
        b.setText("two");
        assertEquals("two", b.getText());
        assertEquals("two", ((VaadinRadioButton) b).getLabel());
    }

    @Test
    @DisplayName("setText null reads back as empty string per R_vaadin_first")
    void setTextNullReadsEmpty() {
        final SJRadioButton b = new SJRadioButton("seed");
        b.setText(null);
        assertEquals("", b.getText());
    }

    @Test
    @DisplayName("setText fires text PCE with old and new values")
    void setTextFiresPce() {
        final SJRadioButton b = new SJRadioButton("old");
        final List<PropertyChangeEvent> events = recordPces(b, "text");
        b.setText("new");
        assertEquals(1, events.size());
        assertEquals("old", events.get(0).getOldValue());
        assertEquals("new", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("default model is JToggleButton ToggleButtonModel")
    void defaultModelIsToggleButtonModel() {
        // Same default as SJToggleButton — needed for press→release-while-armed
        // toggle semantics (a DefaultButtonModel wouldn't toggle on click).
        assertInstanceOf(JToggleButton.ToggleButtonModel.class, new SJRadioButton().getModel());
    }

    @Test
    @DisplayName("UIClassID is RadioButtonUI")
    void uiClassIdIsRadioButtonUi() {
        assertEquals("RadioButtonUI", new SJRadioButton().getUIClassID());
    }

    // --- setSelected programmatic ------------------------------------

    @Test
    @DisplayName("setSelected mirrors into the peer value")
    void setSelectedMirrorsToPeer() {
        final SJRadioButton b = new SJRadioButton();
        b.setSelected(true);
        assertTrue(peerValue(b));
        b.setSelected(false);
        assertFalse(peerValue(b));
    }

    @Test
    @DisplayName("setSelected fires ItemEvent with SELECTED state")
    void setSelectedFiresItemEvent() {
        final SJRadioButton b = new SJRadioButton("X");
        final List<ItemEvent> events = new ArrayList<>();
        b.addItemListener(events::add);

        b.setSelected(true);

        assertEquals(1, events.size());
        assertEquals(ItemEvent.SELECTED, events.get(0).getStateChange());
    }

    @Test
    @DisplayName("setSelected fires ChangeEvent")
    void setSelectedFiresChangeEvent() {
        final SJRadioButton b = new SJRadioButton();
        final Counter hits = new Counter();
        b.addChangeListener(e -> hits.inc());

        b.setSelected(true);

        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setSelected does not fire ActionEvent")
    void setSelectedFiresNoAction() {
        // JDK contract: setSelected does NOT fire Action — only a click
        // (programmatic doClick or user-click) does. Same as SJToggleButton.
        final SJRadioButton b = new SJRadioButton("a");
        final Counter actions = new Counter();
        b.addActionListener(e -> actions.inc());

        b.setSelected(true);

        actions.assertEquals(0);
    }

    @Test
    @DisplayName("setSelected to same value is a no-op")
    void setSelectedSameIsNoop() {
        final SJRadioButton b = new SJRadioButton();
        final Counter hits = new Counter();
        b.addItemListener(e -> hits.inc());

        b.setSelected(false);  // already false

        hits.assertEquals(0);
    }

    @Test
    @DisplayName("setSelected is feedback-loop-safe")
    void setSelectedIsLoopSafe() {
        // Regression for preventPeerEvents: model push to peer must not
        // re-enter the model via the ValueChangeListener.
        final SJRadioButton b = new SJRadioButton();
        final Counter hits = new Counter();
        b.addItemListener(e -> hits.inc());
        b.setSelected(true);
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setSelected false on a selected radio drives peer checked false")
    void deselectDrivesPeerFalse() {
        // This is the shape the emulator-layer ButtonGroup mutex cascade
        // ultimately calls when a sibling is selected and the group
        // cascades deselection to the previous selection. The surrogate
        // owes: peer.checked goes to false, ItemEvent.DESELECTED fires.
        final SJRadioButton b = new SJRadioButton("opt", true);
        assertTrue(peerValue(b));

        final List<Integer> items = new ArrayList<>();
        b.addItemListener(e -> items.add(e.getStateChange()));

        b.setSelected(false);

        assertFalse(peerValue(b));
        assertEquals(List.of(ItemEvent.DESELECTED), items);
    }

    // --- doClick (full pulse via ToggleButtonModel) ------------------

    @Test
    @DisplayName("doClick fires Item before Action and all three families fire")
    void doClickFiresItemBeforeAction() {
        // ToggleButtonModel pulse: same shape as SJToggleButton. Item
        // before Action; actionCommand is the button's text.
        final SJRadioButton b = new SJRadioButton("toggle");
        final List<String> log = new ArrayList<>();
        b.addItemListener(e -> log.add("item:" + e.getStateChange()));
        b.addChangeListener(e -> log.add("change"));
        b.addActionListener(e -> log.add("action:" + e.getActionCommand()));

        b.doClick();

        assertTrue(b.isSelected());
        final int itemIdx = log.indexOf("item:" + ItemEvent.SELECTED);
        final int actionIdx = log.indexOf("action:toggle");
        assertTrue(itemIdx >= 0, "Item event missing: " + log);
        assertTrue(actionIdx >= 0, "Action event missing: " + log);
        assertTrue(itemIdx < actionIdx, "Item must fire before Action: " + log);
        assertTrue(log.contains("change"), "Change must fire: " + log);
    }

    @Test
    @DisplayName("doClick on selected toggles back to deselected without a group")
    void doClickTogglesBackWithoutGroup() {
        // Standalone (no ButtonGroup): clicks toggle like a checkbox.
        // "Radio stays selected when clicked while already-selected" is a
        // group-veto effect — without a group, ToggleButtonModel toggles.
        // This documents the surrogate-layer-only behaviour; emulator-
        // layer JRadioButton + vaadinx.swing.ButtonGroup adds the veto.
        final SJRadioButton b = new SJRadioButton("t", true);
        final List<Integer> items = new ArrayList<>();
        b.addItemListener(e -> items.add(e.getStateChange()));

        b.doClick();

        assertFalse(b.isSelected());
        assertEquals(List.of(ItemEvent.DESELECTED), items);
    }

    // --- Browser-originated toggle -----------------------------------

    @Test
    @DisplayName("peer value change drives ItemEvent + ActionEvent like a user click")
    void peerValueChangeDrivesFullPulse() {
        // Browser-side toggle: ValueChangeListener fires the model pulse.
        final SJRadioButton b = new SJRadioButton("Option");
        final List<String> log = new ArrayList<>();
        b.addItemListener(e -> log.add("item:" + e.getStateChange()));
        b.addActionListener(e -> log.add("action"));

        ((VaadinRadioButton) b).setValue(true);

        assertTrue(b.isSelected());
        assertTrue(log.contains("item:" + ItemEvent.SELECTED));
        assertTrue(log.contains("action"));
    }

    @Test
    @DisplayName("idempotent peer value change does not re-pulse the model")
    void idempotentPeerValueChangeIsSilent() {
        // If model.selected already matches the new value, the guard
        // skips. JDK semantics: a setValue that doesn't change anything
        // doesn't fire Action.
        final SJRadioButton b = new SJRadioButton("x", true);
        final Counter actions = new Counter();
        b.addActionListener(e -> actions.inc());

        ((VaadinRadioButton) b).setValue(true);

        actions.assertEquals(0);
    }

    @Test
    @DisplayName("user click while hosted drives the full event sequence end-to-end")
    void userClickEndToEnd() {
        final SJRadioButton b = new SJRadioButton("Pick");
        UI.getCurrent().add(b);
        final List<Integer> items = new ArrayList<>();
        final Counter actions = new Counter();
        b.addItemListener(e -> items.add(e.getStateChange()));
        b.addActionListener(e -> actions.inc());

        LocatorJ._setValue(LocatorJ._get(VaadinRadioButton.class), true);

        assertTrue(b.isSelected());
        assertEquals(List.of(ItemEvent.SELECTED), items);
        actions.assertEquals(1);
    }

    // --- ButtonModel swap --------------------------------------------

    @Test
    @DisplayName("setModel installs new model and tears down push wire on the old one")
    void setModelSwapsPushWire() {
        final SJRadioButton b = new SJRadioButton();
        final ButtonModel first = b.getModel();
        final ButtonModel second = new JToggleButton.ToggleButtonModel();

        b.setModel(second);

        assertSame(second, b.getModel());
        assertNotSame(first, b.getModel());

        // Push wire on the OLD model must be detached: mutating the old
        // model after swap must NOT reach the peer.
        first.setSelected(true);
        assertFalse(peerValue(b));

        // Push wire on the NEW model must be installed: mutating the new
        // model DOES reach the peer.
        second.setSelected(true);
        assertTrue(peerValue(b));
    }

    @Test
    @DisplayName("setModel fires the model PCE")
    void setModelFiresPce() {
        final SJRadioButton b = new SJRadioButton();
        final List<PropertyChangeEvent> events = recordPces(b, "model");

        b.setModel(new JToggleButton.ToggleButtonModel());

        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setModel to a non-toggle DefaultButtonModel still works for selection plumbing")
    void setModelToDefaultButtonModel() {
        final SJRadioButton b = new SJRadioButton();
        b.setModel(new DefaultButtonModel());
        b.setSelected(true);
        assertTrue(b.isSelected());
        assertTrue(peerValue(b));
    }

    // --- Action wiring ------------------------------------------------

    @Test
    @DisplayName("setAction copies NAME into text and registers Action as ActionListener")
    void setActionCopiesName() {
        final SJRadioButton b = new SJRadioButton();
        b.setAction(inertAction("ActionName"));

        assertEquals("ActionName", b.getText());
        assertEquals(1, b.getActionListeners().length);
    }

    @Test
    @DisplayName("Action enabled state propagates to the button")
    void actionEnabledPropagates() {
        final AbstractAction a = inertAction("X");
        a.setEnabled(false);

        final SJRadioButton b = new SJRadioButton();
        b.setAction(a);

        assertFalse(b.isEnabled());
    }

    // --- Layout drop-and-WARN ----------------------------------------

    @Test
    @DisplayName("setHorizontalAlignment LEFT WARNs and drops")
    void horizontalAlignmentLeftWarns() {
        final SJRadioButton b = new SJRadioButton();
        b.setHorizontalAlignment(SwingConstants.LEFT);
        assertEquals(1, capturedWarns.size());
        // Getter returns JDK default (CENTER), not the set value.
        assertEquals(SwingConstants.CENTER, b.getHorizontalAlignment());
    }

    @Test
    @DisplayName("setHorizontalAlignment CENTER (default) is silent")
    void horizontalAlignmentCenterIsSilent() {
        final SJRadioButton b = new SJRadioButton();
        b.setHorizontalAlignment(SwingConstants.CENTER);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setHorizontalAlignment with bad axis throws IAE per R_match_swing_errors")
    void horizontalAlignmentBadAxisThrows() {
        final SJRadioButton b = new SJRadioButton();
        // vertical key on horizontal
        assertThrows(IllegalArgumentException.class, () -> b.setHorizontalAlignment(SwingConstants.TOP));
    }

    @Test
    @DisplayName("setVerticalAlignment with bad axis throws IAE per R_match_swing_errors")
    void verticalAlignmentBadAxisThrows() {
        final SJRadioButton b = new SJRadioButton();
        // horizontal key on vertical
        assertThrows(IllegalArgumentException.class, () -> b.setVerticalAlignment(SwingConstants.LEFT));
    }

    @Test
    @DisplayName("setHorizontalTextPosition LEFT WARNs and drops")
    void horizontalTextPositionWarns() {
        final SJRadioButton b = new SJRadioButton();
        b.setHorizontalTextPosition(SwingConstants.LEFT);
        assertEquals(1, capturedWarns.size());
        assertEquals(SwingConstants.TRAILING, b.getHorizontalTextPosition());
    }

    @Test
    @DisplayName("setVerticalTextPosition TOP WARNs and drops")
    void verticalTextPositionWarns() {
        final SJRadioButton b = new SJRadioButton();
        b.setVerticalTextPosition(SwingConstants.TOP);
        assertEquals(1, capturedWarns.size());
        assertEquals(SwingConstants.CENTER, b.getVerticalTextPosition());
    }

    @Test
    @DisplayName("setIconTextGap non-default WARNs and drops")
    void iconTextGapNonDefaultWarns() {
        final SJRadioButton b = new SJRadioButton();
        b.setIconTextGap(12);
        assertEquals(1, capturedWarns.size());
        assertEquals(4, b.getIconTextGap());
    }

    @Test
    @DisplayName("setIconTextGap default (4) is silent")
    void iconTextGapDefaultIsSilent() {
        final SJRadioButton b = new SJRadioButton();
        b.setIconTextGap(4);
        assertEquals(0, capturedWarns.size());
    }

    // --- setIcon (mixin's non-Button else branch) -------------------

    @Test
    @DisplayName("setIcon non-null WARNs and drops via mixin's non-Button branch")
    void setIconWarns() {
        final SJRadioButton b = new SJRadioButton();
        b.setIcon(new ImageIcon(new byte[] { 0 }));
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("setIcon null is silent")
    void setIconNullIsSilent() {
        final SJRadioButton b = new SJRadioButton();
        b.setIcon(null);
        assertEquals(0, capturedWarns.size());
    }

    // --- setEnabled --------------------------------------------------

    @Test
    @DisplayName("setEnabled false disables the peer")
    void setEnabledFalseDisables() {
        final SJRadioButton b = new SJRadioButton("opt");
        b.setEnabled(false);
        assertFalse(b.isEnabled());
    }

    @Test
    @DisplayName("setEnabled fires the enabled PCE per SD_auto_pce")
    void setEnabledFiresPce() {
        final SJRadioButton b = new SJRadioButton();
        final List<PropertyChangeEvent> events = recordPces(b, "enabled");
        b.setEnabled(false);
        assertEquals(1, events.size());
        assertEquals(true, events.get(0).getOldValue());
        assertEquals(false, events.get(0).getNewValue());
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathIsWarnFree() {
        final SJRadioButton b = new SJRadioButton("Subscribe");
        UI.getCurrent().add(b);
        b.setActionCommand("subscribe-cmd");
        b.setSelected(true);
        b.doClick();
        b.setSelected(false);
        b.setText("Other");

        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
