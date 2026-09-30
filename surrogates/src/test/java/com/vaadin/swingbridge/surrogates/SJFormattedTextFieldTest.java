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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFormattedTextField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exit gate for SD_sjformatted_family's SJFormattedTextField — second surrogate of the
 * SJFormatted* family per D_jformattedtextfield strategy dispatch (catch-all + future
 * MaskFormatter peer). Inherits the JTextField surface from SJTextField
 * directly; covers only the JFormattedTextField-specific additions:
 *
 * <ol>
 *  <li>Constructors (no-arg + initial text).
 *  <li>focusLostBehavior round-trip with R_match_swing_errors IAE on bad int + PCE on swap.
 *  <li>UIClassID override ("FormattedTextFieldUI" not "TextFieldUI").
 *  <li>Inherited SJTextField surface (columns / setText / ActionListener
 *      on Enter) still works through the subclass — quick smoke test
 *      that nothing broke under the {@code extends SJTextField} shape.
 * </ol>
 */
class SJFormattedTextFieldTest extends AbstractKaribuTest {

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("default ctor empty + COMMIT_OR_REVERT focus behavior")
    void defaultCtorEmptyPlusCommitOrRevertFocusBehavior() {
        SJFormattedTextField f = new SJFormattedTextField();
        assertEquals("", f.getValue());
        assertEquals(JFormattedTextField.COMMIT_OR_REVERT, f.getFocusLostBehavior());
        assertEquals("FormattedTextFieldUI", f.getUIClassID());
    }

    @Test
    @DisplayName("text ctor seeds the peer value")
    void textCtorSeedsThePeerValue() {
        SJFormattedTextField f = new SJFormattedTextField("hello");
        assertEquals("hello", f.getValue());
    }

    // --- focusLostBehavior round-trip --------------------------------

    @Test
    @DisplayName("focusLostBehavior round-trips and fires no PCE")
    void focusLostBehaviorRoundTripsAndFiresNoPce() {
        // The JDK's JFormattedTextField.setFocusLostBehavior validates the
        // argument, assigns the field and returns — no bound property (SD_property_fanout_audit).
        // This asserted the event, i.e. one migrated code never received.
        SJFormattedTextField f = new SJFormattedTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("focusLostBehavior", events::add);
        f.setFocusLostBehavior(JFormattedTextField.PERSIST);
        assertEquals(JFormattedTextField.PERSIST, f.getFocusLostBehavior());
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("focusLostBehavior IAE on invalid int per R_match_swing_errors")
    void focusLostBehaviorIaeOnInvalidIntPerRMatchSwingErrors() {
        SJFormattedTextField f = new SJFormattedTextField();
        assertThrows(IllegalArgumentException.class, () -> f.setFocusLostBehavior(999));
    }

    @Test
    @DisplayName("focusLostBehavior same value short-circuits PCE")
    void focusLostBehaviorSameValueShortCircuitsPce() {
        SJFormattedTextField f = new SJFormattedTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("focusLostBehavior", events::add);
        f.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
        assertEquals(0, events.size());
    }

    // --- Inherited SJTextField surface still works -------------------

    @Test
    @DisplayName("inherited setColumns + ActionListener round-trip cleanly")
    void inheritedSetColumnsPlusActionListenerRoundTripCleanly() {
        SJFormattedTextField f = new SJFormattedTextField();
        f.setColumns(24);
        assertEquals(24, f.getColumns());
        Counter fired = new Counter();
        f.addActionListener(e -> fired.inc());
        // Direct fireActionPerformed via postActionEvent (the JDK public
        // synthesizer); avoids needing Karibu key-press for this smoke.
        f.postActionEvent();
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("inherited setValue (peer text) round-trips")
    void inheritedSetValuePeerTextRoundTrips() {
        SJFormattedTextField f = new SJFormattedTextField();
        f.setValue("round-trip");
        assertEquals("round-trip", f.getValue());
    }

    // --- Happy path -------------------------------------------------

    @Test
    @DisplayName("canonical user path fires zero stub WARNs")
    void canonicalUserPathFiresZeroStubWarns() {
        SJFormattedTextField f = new SJFormattedTextField("seed");
        f.setFocusLostBehavior(JFormattedTextField.COMMIT);
        f.setColumns(16);
        ActionListener l = e -> {
        };
        f.addActionListener(l);
        f.removeActionListener(l);
        assertNoWarns("happy-path WARNs leaked: " + capturedWarns);
    }
}
