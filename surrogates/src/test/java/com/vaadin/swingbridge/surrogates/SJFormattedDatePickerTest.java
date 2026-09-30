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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JFormattedTextField;
import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjformatted_family's SJFormattedDatePicker — first surrogate of the
 * SJFormatted* family per D_jformattedtextfield strategy dispatch. Covers:
 *
 * <ol>
 *  <li>Constructors (no-arg + Date initial value).
 *  <li>R_vaadin_first + R_swing_is_truth — peer's LocalDate is source-of-truth; Date↔LocalDate via
 *      {@code BrowserDateUtils.toLocalDate} / {@code BrowserDateUtils.toDate} at the boundary.
 *  <li>setDateValue / getDateValue round-trip with the preventPeerEvents
 *      guard (single PCE fire per write, no double-fire from the inner
 *      ValueChange bridge).
 *  <li>Browser-side date pick (Karibu _setValue with isFromClient=true)
 *      fires PropertyChangeEvent("value", oldDate, newDate) carrying
 *      JDK Date types and an ActionEvent to registered ActionListeners.
 *  <li>focusLostBehavior round-trip with R_match_swing_errors IAE on invalid int.
 *  <li>JTextField surface drop-and-WARN — setColumns / setHorizontalAlignment
 *      IAE on bad input; non-default values WARN; field-shadow round-trips.
 *  <li>UIClassID + accessibility WARN-and-null.
 *  <li>Happy-path zero stub WARNs across the canonical user path.
 * </ol>
 */
class SJFormattedDatePickerTest extends AbstractKaribuTest {

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("default ctor has no value and default focusLostBehavior")
    void defaultCtorHasNoValueAndDefaultFocusLostBehavior() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        assertNull(f.getValue());
        assertNull(f.getDateValue());
        assertEquals(JFormattedTextField.COMMIT_OR_REVERT, f.getFocusLostBehavior());
        assertArrayEquals(new ActionListener[0], f.getActionListeners());
        assertEquals("FormattedTextFieldUI", f.getUIClassID());
    }

    @Test
    @DisplayName("Date ctor seeds the peer via LocalDate conversion")
    void dateCtorSeedsThePeerViaLocalDateConversion() {
        Date date = jdkDate(2026, 5, 8);
        SJFormattedDatePicker f = new SJFormattedDatePicker(date);
        assertEquals(LocalDate.of(2026, 5, 8), f.getValue());
        assertEquals(date, f.getDateValue());
    }

    @Test
    @DisplayName("null Date ctor leaves the peer empty")
    void nullDateCtorLeavesThePeerEmpty() {
        SJFormattedDatePicker f = new SJFormattedDatePicker(null);
        assertNull(f.getValue());
        assertNull(f.getDateValue());
    }

    // --- setDateValue / getDateValue round-trip ----------------------

    @Test
    @DisplayName("setDateValue writes through to the LocalDate peer and reads back")
    void setDateValueWritesThroughToTheLocalDatePeerAndReadsBack() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        Date date = jdkDate(2026, 5, 8);
        f.setDateValue(date);
        assertEquals(LocalDate.of(2026, 5, 8), f.getValue());
        assertEquals(date, f.getDateValue());
    }

    @Test
    @DisplayName("setDateValue null clears the peer")
    void setDateValueNullClearsThePeer() {
        SJFormattedDatePicker f = new SJFormattedDatePicker(jdkDate(2026, 5, 8));
        f.setDateValue(null);
        assertNull(f.getValue());
        assertNull(f.getDateValue());
    }

    @Test
    @DisplayName("setDateValue same value does not fire PCE")
    void setDateValueSameValueDoesNotFirePce() {
        SJFormattedDatePicker f = new SJFormattedDatePicker(jdkDate(2026, 5, 8));
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("value", events::add);
        f.setDateValue(jdkDate(2026, 5, 8));
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("programmatic setDateValue fires PCE once with Date payloads (no double-fire from inner bridge)")
    void programmaticSetDateValueFiresPceOnceWithDatePayloads() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("value", events::add);

        Date newDate = jdkDate(2026, 5, 8);
        f.setDateValue(newDate);

        assertEquals(1, events.size(), "preventPeerEvents guard prevents the inner ValueChange bridge from re-firing");
        assertNull(events.get(0).getOldValue());
        assertEquals(newDate, events.get(0).getNewValue());
        assertSame(f, events.get(0).getSource());
    }

    // --- Browser-side date pick -------------------------------------

    @Test
    @DisplayName("browser date pick fires PCE and ActionEvent through the bridge")
    void browserDatePickFiresPceAndActionEventThroughTheBridge() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        List<ActionEvent> actionEvents = new ArrayList<>();
        f.addPropertyChangeListener("value", pces::add);
        f.addActionListener(actionEvents::add);

        LocalDate pickedDate = LocalDate.of(2026, 5, 8);
        LocatorJ._setValue(f, pickedDate); // Karibu: isFromClient=true

        assertEquals(1, pces.size());
        assertNull(pces.get(0).getOldValue());
        assertEquals(jdkDate(2026, 5, 8), pces.get(0).getNewValue());
        assertEquals(1, actionEvents.size());
        assertSame(f, actionEvents.get(0).getSource());
        assertEquals(ActionEvent.ACTION_PERFORMED, actionEvents.get(0).getID());
        assertEquals("", actionEvents.get(0).getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand round-trips into fired ActionEvent")
    void setActionCommandRoundTripsIntoFiredActionEvent() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        f.setActionCommand("dob-changed");
        List<ActionEvent> events = new ArrayList<>();
        f.addActionListener(events::add);
        LocatorJ._setValue(f, LocalDate.of(2026, 5, 8));
        assertEquals(1, events.size());
        assertEquals("dob-changed", events.get(0).getActionCommand());
    }

    @Test
    @DisplayName("removed ActionListener does not fire")
    void removedActionListenerDoesNotFire() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        Counter fired = new Counter();
        ActionListener l = e -> fired.inc();
        f.addActionListener(l);
        f.removeActionListener(l);
        LocatorJ._setValue(f, LocalDate.of(2026, 5, 8));
        fired.assertEquals(0);
    }

    // --- focusLostBehavior round-trip -------------------------------

    @Test
    @DisplayName("focusLostBehavior round-trips and fires no PCE")
    void focusLostBehaviorRoundTripsAndFiresNoPce() {
        // The JDK's JFormattedTextField.setFocusLostBehavior validates the
        // argument, assigns the field and returns — no bound property (SD_property_fanout_audit).
        // This asserted the event, i.e. one migrated code never received.
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("focusLostBehavior", events::add);
        f.setFocusLostBehavior(JFormattedTextField.PERSIST);
        assertEquals(JFormattedTextField.PERSIST, f.getFocusLostBehavior());
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("focusLostBehavior IAE on invalid int per R_match_swing_errors")
    void focusLostBehaviorIaeOnInvalidInt() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        assertThrows(IllegalArgumentException.class, () -> f.setFocusLostBehavior(999));
    }

    @Test
    @DisplayName("focusLostBehavior same value short-circuits PCE")
    void focusLostBehaviorSameValueShortCircuitsPce() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("focusLostBehavior", events::add);
        f.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
        assertEquals(0, events.size());
    }

    // --- JTextField surface drop-and-WARN ---------------------------

    @Test
    @DisplayName("setColumns negative throws IAE per R_match_swing_errors")
    void setColumnsNegativeThrowsIae() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        assertThrows(IllegalArgumentException.class, () -> f.setColumns(-1));
    }

    @Test
    @DisplayName("setColumns zero round-trips silently")
    void setColumnsZeroRoundTripsSilently() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        f.setColumns(0);
        assertEquals(0, f.getColumns());
        assertNoWarns("columns=0 (default) must not WARN: " + capturedWarns);
    }

    @Test
    @DisplayName("setColumns non-zero WARNs (no DatePicker peer concept) but field round-trips")
    void setColumnsNonZeroWarnsButFieldRoundTrips() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        f.setColumns(20);
        assertEquals(20, f.getColumns());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setColumns")));
    }

    @Test
    @DisplayName("setHorizontalAlignment LEADING (default) does not WARN")
    void setHorizontalAlignmentLeadingDoesNotWarn() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        f.setHorizontalAlignment(SwingConstants.LEADING);
        assertEquals(SwingConstants.LEADING, f.getHorizontalAlignment());
        assertNoWarns();
    }

    @Test
    @DisplayName("setHorizontalAlignment non-LEADING WARNs but round-trips")
    void setHorizontalAlignmentNonLeadingWarnsButRoundTrips() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        f.setHorizontalAlignment(SwingConstants.RIGHT);
        assertEquals(SwingConstants.RIGHT, f.getHorizontalAlignment());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setHorizontalAlignment")));
    }

    @Test
    @DisplayName("setHorizontalAlignment IAE on bad axis per R_match_swing_errors")
    void setHorizontalAlignmentIaeOnBadAxis() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        assertThrows(IllegalArgumentException.class, () -> f.setHorizontalAlignment(999));
    }

    // --- Accessibility ----------------------------------------------

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        assertNull(f.getAccessibleContext());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("getAccessibleContext")));
    }

    // --- Happy path -------------------------------------------------

    @Test
    @DisplayName("canonical user path fires zero stub WARNs")
    void canonicalUserPathFiresZeroStubWarns() {
        SJFormattedDatePicker f = new SJFormattedDatePicker();
        // simulate "click the field, pick a date" → Karibu _setValue is
        // the closest analogue (fires isFromClient=true).
        LocatorJ._setValue(f, LocalDate.of(2026, 5, 8));
        // emulator-side reads value back via the convenience accessor
        f.getDateValue();
        // listeners come and go
        ActionListener l = e -> {
        };
        f.addActionListener(l);
        f.removeActionListener(l);
        // focus-lost behavior round-trip
        f.setFocusLostBehavior(JFormattedTextField.COMMIT);
        assertNoWarns("happy-path WARNs leaked: " + capturedWarns);
    }

    // --- Helpers ---------------------------------------------------

    private static Date jdkDate(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day)
                .atStartOfDay(BrowserTimeZone.get()).toInstant());
    }
}
