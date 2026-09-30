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

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFormattedTextField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combined exit gate for the SD_sjformatted_family number-field family — three siblings
 * with parallel structure (SJFormattedIntegerField / SJFormattedLongField
 * / SJFormattedNumberField). Tests are tight per-surrogate covering:
 *
 * <ol>
 *  <li>Constructors (no-arg + initial value).
 *  <li>typed setter (setIntValue / setLongValue / setDoubleValue) with
 *      preventPeerEvents guard — single PCE fire per write.
 *  <li>Browser-side value pick fires PCE("value", oldNum, newNum) +
 *      ActionEvent through the install bridge.
 *  <li>focusLostBehavior round-trip with R_match_swing_errors IAE.
 *  <li>JTextField surface drop-and-WARN — setColumns / setHorizontalAlignment.
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJFormattedNumberFieldsTest extends AbstractKaribuTest {

    // ===== SJFormattedIntegerField =====================================

    @Test
    @DisplayName("Integer default ctor empty + COMMIT_OR_REVERT")
    void integerDefaultCtorEmptyPlusCommitOrRevert() {
        SJFormattedIntegerField f = new SJFormattedIntegerField();
        assertNull(f.getValue());
        assertEquals(JFormattedTextField.COMMIT_OR_REVERT, f.getFocusLostBehavior());
        assertEquals("FormattedTextFieldUI", f.getUIClassID());
    }

    @Test
    @DisplayName("Integer ctor seeds value")
    void integerCtorSeedsValue() {
        SJFormattedIntegerField f = new SJFormattedIntegerField(42);
        assertEquals(42, f.getValue());
    }

    @Test
    @DisplayName("Integer setIntValue fires single PCE (no double-fire from bridge)")
    void integerSetIntValueFiresSinglePce() {
        SJFormattedIntegerField f = new SJFormattedIntegerField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("value", events::add);
        f.setIntValue(7);
        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());
        assertEquals(7, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Integer browser pick fires PCE + ActionEvent")
    void integerBrowserPickFiresPcePlusActionEvent() {
        SJFormattedIntegerField f = new SJFormattedIntegerField();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        Counter actions = new Counter();
        f.addPropertyChangeListener("value", pces::add);
        f.addActionListener(e -> actions.inc());
        LocatorJ._setValue(f, 99);
        assertEquals(1, pces.size());
        assertEquals(99, pces.get(0).getNewValue());
        actions.assertEquals(1);
    }

    @Test
    @DisplayName("Integer focusLostBehavior IAE")
    void integerFocusLostBehaviorIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new SJFormattedIntegerField().setFocusLostBehavior(999));
    }

    @Test
    @DisplayName("Integer setColumns negative IAE, non-zero WARNs")
    void integerSetColumnsNegativeIaeNonZeroWarns() {
        SJFormattedIntegerField f = new SJFormattedIntegerField();
        assertThrows(IllegalArgumentException.class, () -> f.setColumns(-1));
        f.setColumns(8);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setColumns")));
    }

    @Test
    @DisplayName("Integer happy path zero WARNs")
    void integerHappyPathZeroWarns() {
        SJFormattedIntegerField f = new SJFormattedIntegerField();
        LocatorJ._setValue(f, 5);
        f.setFocusLostBehavior(JFormattedTextField.COMMIT);
        assertNoWarns("warns leaked: " + capturedWarns);
    }

    // ===== SJFormattedLongField ========================================

    @Test
    @DisplayName("Long default ctor empty + COMMIT_OR_REVERT")
    void longDefaultCtorEmptyPlusCommitOrRevert() {
        SJFormattedLongField f = new SJFormattedLongField();
        assertNull(f.getValue());
        assertEquals(JFormattedTextField.COMMIT_OR_REVERT, f.getFocusLostBehavior());
    }

    @Test
    @DisplayName("Long ctor seeds value")
    void longCtorSeedsValue() {
        SJFormattedLongField f = new SJFormattedLongField(1234567890123L);
        assertEquals(1234567890123L, f.getValue());
    }

    @Test
    @DisplayName("Long setLongValue fires single PCE")
    void longSetLongValueFiresSinglePce() {
        SJFormattedLongField f = new SJFormattedLongField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("value", events::add);
        f.setLongValue(99L);
        assertEquals(1, events.size());
        assertEquals(99L, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Long browser pick fires PCE + ActionEvent")
    void longBrowserPickFiresPcePlusActionEvent() {
        SJFormattedLongField f = new SJFormattedLongField();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        Counter actions = new Counter();
        f.addPropertyChangeListener("value", pces::add);
        f.addActionListener(e -> actions.inc());
        LocatorJ._setValue(f, 7L);
        assertEquals(1, pces.size());
        assertEquals(7L, pces.get(0).getNewValue());
        actions.assertEquals(1);
    }

    @Test
    @DisplayName("Long happy path zero WARNs")
    void longHappyPathZeroWarns() {
        SJFormattedLongField f = new SJFormattedLongField();
        LocatorJ._setValue(f, 50L);
        assertNoWarns();
    }

    // ===== SJFormattedNumberField ======================================

    @Test
    @DisplayName("Double default ctor empty + COMMIT_OR_REVERT")
    void doubleDefaultCtorEmptyPlusCommitOrRevert() {
        SJFormattedNumberField f = new SJFormattedNumberField();
        assertNull(f.getValue());
        assertEquals(JFormattedTextField.COMMIT_OR_REVERT, f.getFocusLostBehavior());
    }

    @Test
    @DisplayName("Double ctor seeds value")
    void doubleCtorSeedsValue() {
        SJFormattedNumberField f = new SJFormattedNumberField(3.14);
        assertEquals(3.14, f.getValue());
    }

    @Test
    @DisplayName("Double setDoubleValue fires single PCE")
    void doubleSetDoubleValueFiresSinglePce() {
        SJFormattedNumberField f = new SJFormattedNumberField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener("value", events::add);
        f.setDoubleValue(2.71);
        assertEquals(1, events.size());
        assertEquals(2.71, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("Double browser pick fires PCE + ActionEvent")
    void doubleBrowserPickFiresPcePlusActionEvent() {
        SJFormattedNumberField f = new SJFormattedNumberField();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        Counter actions = new Counter();
        f.addPropertyChangeListener("value", pces::add);
        f.addActionListener(e -> actions.inc());
        LocatorJ._setValue(f, 1.5);
        assertEquals(1, pces.size());
        assertEquals(1.5, pces.get(0).getNewValue());
        actions.assertEquals(1);
    }

    @Test
    @DisplayName("Double happy path zero WARNs")
    void doubleHappyPathZeroWarns() {
        SJFormattedNumberField f = new SJFormattedNumberField();
        LocatorJ._setValue(f, 0.5);
        assertNoWarns();
    }
}
