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

import com.vaadin.flow.component.checkbox.Checkbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JToggleButton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_toggle_checkbox_first_cut's SJCheckBox surrogate (standalone Checkbox peer
 * since SD_sjtogglebutton_button_peer — SJToggleButton moved to a Button peer). Asserts:
 *
 * <ol>
 *  <li>Standalone Checkbox-peer toggle wiring (Checkbox peer,
 *      ToggleButtonModel, setText through setLabel, etc.) — spot-checked
 *      rather than re-running the whole surface. Crucially NOT-a
 *      SJToggleButton anymore.
 *  <li>JCheckBox-specific surface:
 *      <ul>
 *      <li>getUIClassID is "CheckBoxUI" (not "ToggleButtonUI").
 *      <li>setBorderPaintedFlat(true) WARNs and drops; isBorderPaintedFlat
 *          returns false; no PCE fires (R_vaadin_first drop per SD_toggle_checkbox_first_cut).
 *      <li>setBorderPaintedFlat(false) is silent (default value).
 *      </ul>
 * </ol>
 */
class SJCheckBoxTest extends AbstractKaribuTest {

    // --- Standalone Checkbox-peer toggle wiring sanity ---------------

    @Test
    @DisplayName("SJCheckBox is a Checkbox and no longer a SJToggleButton")
    void sjCheckBoxIsACheckboxAndNoLongerASjToggleButton() {
        // Widened to Component so the negative instance check is legal —
        // the static SJCheckBox type already excludes SJToggleButton.
        com.vaadin.flow.component.Component cb = new SJCheckBox("Agree");
        assertInstanceOf(Checkbox.class, cb);
        assertFalse(cb instanceof SJToggleButton);
    }

    @Test
    @DisplayName("default model is ToggleButtonModel")
    void defaultModelIsToggleButtonModel() {
        assertInstanceOf(JToggleButton.ToggleButtonModel.class, new SJCheckBox().getModel());
    }

    @Test
    @DisplayName("text + selected ctor seeds both")
    void textPlusSelectedCtorSeedsBoth() {
        SJCheckBox cb = new SJCheckBox("Terms", true);
        assertEquals("Terms", cb.getText());
        assertEquals("Terms", ((Checkbox) cb).getLabel());
        assertTrue(cb.isSelected());
        assertEquals(true, ((Checkbox) cb).getValue());
    }

    @Test
    @DisplayName("setSelected mirrors into the peer value")
    void setSelectedMirrorsIntoThePeerValue() {
        SJCheckBox cb = new SJCheckBox();
        cb.setSelected(true);
        assertEquals(true, ((Checkbox) cb).getValue());
    }

    @Test
    @DisplayName("doClick toggles selection")
    void doClickTogglesSelection() {
        SJCheckBox cb = new SJCheckBox();
        cb.doClick();
        assertTrue(cb.isSelected());
        cb.doClick();
        assertFalse(cb.isSelected());
    }

    // --- JCheckBox-specific surface ---------------------------------

    @Test
    @DisplayName("getUIClassID is CheckBoxUI")
    void getUiClassIdIsCheckBoxUi() {
        assertEquals("CheckBoxUI", new SJCheckBox().getUIClassID());
    }

    @Test
    @DisplayName("setBorderPaintedFlat true WARNs and drops per R_vaadin_first")
    void setBorderPaintedFlatTrueWarnsAndDropsPerRVaadinFirst() {
        SJCheckBox cb = new SJCheckBox();
        cb.setBorderPaintedFlat(true);
        assertEquals(1, capturedWarns.size());
        // Getter returns JDK default — surrogate doesn't store the value.
        assertFalse(cb.isBorderPaintedFlat());
    }

    @Test
    @DisplayName("setBorderPaintedFlat false default is silent")
    void setBorderPaintedFlatFalseDefaultIsSilent() {
        SJCheckBox cb = new SJCheckBox();
        cb.setBorderPaintedFlat(false);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setBorderPaintedFlat fires no PCE per R_vaadin_first drop")
    void setBorderPaintedFlatFiresNoPcePerRVaadinFirstDrop() {
        // No state, no change — same shape as SJButton's per-state icon
        // setters per SD_sjbutton. Emulator-side keeps PCE for full R_swing_is_truth round-trip.
        SJCheckBox cb = new SJCheckBox();
        List<PropertyChangeEvent> events = new ArrayList<>();
        cb.addPropertyChangeListener("borderPaintedFlat", events::add);
        cb.setBorderPaintedFlat(true);
        cb.setBorderPaintedFlat(false);
        assertEquals(0, events.size());
    }

    // --- Happy-path zero-WARN ---------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathUiFunctionalSurfaceIsWarnFree() {
        SJCheckBox cb = new SJCheckBox("Notify on click");
        cb.setActionCommand("notify");
        cb.setSelected(true);
        cb.doClick();
        cb.setText("Other");
        cb.setBorderPaintedFlat(false);  // default — silent

        assertEquals(0, capturedWarns.size(), "Warns: " + capturedWarns);
    }
}
