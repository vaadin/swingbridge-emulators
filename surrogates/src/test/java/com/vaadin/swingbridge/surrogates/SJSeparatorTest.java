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

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingConstants;

import com.vaadin.flow.dom.Style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJSeparator (SD_sjseparator). Covers:
 *
 * <ol>
 *  <li><b>Ctors + orientation validation</b> — no-arg defaults to HORIZONTAL;
 *      int-arg accepts HORIZONTAL/VERTICAL, throws IAE on garbage.
 *  <li><b>Orientation CSS on the host</b> — Lumo-toned background +
 *      align-self:stretch always; a HORIZONTAL bar pins min-height:1px
 *      (fills width), a VERTICAL one pins min-width:1px (fills height);
 *      the flip clears the opposite key.
 *  <li><b>Runtime setOrientation flip</b> — repaints the bar, fires
 *      "orientation" PCE once on change, no-op on equal.
 *  <li><b>L&amp;F surface</b> — getUIClassID == "SeparatorUI"; setUI WARNs.
 *  <li><b>getAccessibleContext</b> — deferred WARN + null, surrogate-wide stance.
 * </ol>
 */
class SJSeparatorTest extends AbstractKaribuTest {

    // --- Ctors + orientation validation -------------------------------------

    @Test
    @DisplayName("no-arg ctor defaults to HORIZONTAL")
    void noArgCtorDefaultsToHorizontal() {
        SJSeparator s = new SJSeparator();
        assertEquals(SwingConstants.HORIZONTAL, s.getOrientation());
        assertNoWarns("no-arg ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("int-arg ctor accepts VERTICAL")
    void intArgCtorAcceptsVertical() {
        assertEquals(SwingConstants.VERTICAL, new SJSeparator(SwingConstants.VERTICAL).getOrientation());
    }

    @Test
    @DisplayName("int-arg ctor throws IAE on garbage orientation")
    void intArgCtorThrowsIaeOnGarbageOrientation() {
        assertThrows(IllegalArgumentException.class, () -> new SJSeparator(42));
    }

    // --- Orientation CSS on the host ----------------------------------------

    @Test
    @DisplayName("HORIZONTAL paints a full-width hairline")
    void horizontalPaintsAFullWidthHairline() {
        SJSeparator s = new SJSeparator();
        Style css = s.getElement().getStyle();
        assertEquals("var(--vaadin-border-color-secondary)", css.get("background-color"));
        assertEquals("stretch", css.get("align-self"));
        assertEquals("1px", css.get("min-height"));
        assertNull(css.get("min-width"), "horizontal bar must not pin min-width");
    }

    @Test
    @DisplayName("VERTICAL paints a full-height hairline")
    void verticalPaintsAFullHeightHairline() {
        Style css = new SJSeparator(SwingConstants.VERTICAL).getElement().getStyle();
        assertEquals("1px", css.get("min-width"));
        assertNull(css.get("min-height"), "vertical bar must not pin min-height");
    }

    // --- Runtime setOrientation flip ----------------------------------------

    @Test
    @DisplayName("setOrientation repaints the bar and clears the opposite key")
    void setOrientationRepaintsTheBarAndClearsTheOppositeKey() {
        SJSeparator s = new SJSeparator();  // HORIZONTAL → min-height:1px
        s.setOrientation(SwingConstants.VERTICAL);
        assertEquals("1px", s.getElement().getStyle().get("min-width"));
        assertNull(s.getElement().getStyle().get("min-height"));
        s.setOrientation(SwingConstants.HORIZONTAL);
        assertEquals("1px", s.getElement().getStyle().get("min-height"));
        assertNull(s.getElement().getStyle().get("min-width"));
    }

    @Test
    @DisplayName("setOrientation fires PCE on change and no-op on equal")
    void setOrientationFiresPceOnChangeAndNoOpOnEqual() {
        SJSeparator s = new SJSeparator();
        List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener(events::add);

        s.setOrientation(SwingConstants.VERTICAL);
        List<PropertyChangeEvent> o = events.stream()
                .filter(it -> "orientation".equals(it.getPropertyName()))
                .toList();
        assertEquals(1, o.size(), "exactly one PCE on actual change");
        assertEquals(SwingConstants.HORIZONTAL, o.get(0).getOldValue());
        assertEquals(SwingConstants.VERTICAL, o.get(0).getNewValue());

        events.clear();
        s.setOrientation(SwingConstants.VERTICAL);
        assertTrue(events.stream().noneMatch(it -> "orientation".equals(it.getPropertyName())),
                "no-op on equal orientation should fire no PCE");
    }

    @Test
    @DisplayName("setOrientation throws IAE on garbage")
    void setOrientationThrowsIaeOnGarbage() {
        assertThrows(IllegalArgumentException.class, () -> new SJSeparator().setOrientation(99));
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is SeparatorUI")
    void getUiClassIdIsSeparatorUi() {
        assertEquals("SeparatorUI", new SJSeparator().getUIClassID());
    }

    @Test
    @DisplayName("setUI WARNs")
    void setUiWarns() {
        new SJSeparator().setUI(null);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setUI")),
                "setUI should WARN: " + capturedWarns);
    }

    // --- Accessibility ------------------------------------------------------

    @Test
    @DisplayName("getAccessibleContext is deferred WARN plus null")
    void getAccessibleContextIsDeferredWarnPlusNull() {
        assertNull(new SJSeparator().getAccessibleContext());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("getAccessibleContext")));
    }
}
