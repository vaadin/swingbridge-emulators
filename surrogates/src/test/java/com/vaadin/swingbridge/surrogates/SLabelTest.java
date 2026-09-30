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
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_slabel's SLabel surrogate — the AWT 1.0 {@code java.awt.Label},
 * not {@code JLabel}. Covers:
 *
 * <ol>
 *  <li>Ctors + JDK defaults (empty text, LEFT alignment).
 *  <li>Text round-trip and the R_vaadin_first lossy {@code setText(null)} → {@code ""}.
 *  <li>Alignment → CSS {@code text-align}, read back losslessly; R_match_swing_errors IAE on a
 *      bad value, thrown before anything is written.
 *  <li>The {@code display: block} host that makes alignment observable.
 *  <li>The inherited ComponentMixin surface on a non-JComponent host.
 *  <li>Zero stub WARNs across the happy path.
 * </ol>
 */
class SLabelTest extends AbstractKaribuTest {

    // --- Ctors / defaults ---------------------------------------------

    @Test
    @DisplayName("no-arg ctor matches AWT's empty-text LEFT-aligned default")
    void noArgCtorMatchesAwtsEmptyTextLeftAlignedDefault() {
        SLabel l = new SLabel();
        assertEquals("", l.getText());
        assertEquals(SLabel.LEFT, l.getAlignment());
    }

    @Test
    @DisplayName("string ctor seeds the text and leaves alignment at LEFT")
    void stringCtorSeedsTheTextAndLeavesAlignmentAtLeft() {
        SLabel l = new SLabel("Total");
        assertEquals("Total", l.getText());
        assertEquals(SLabel.LEFT, l.getAlignment());
    }

    @Test
    @DisplayName("two-arg ctor seeds both")
    void twoArgCtorSeedsBoth() {
        SLabel l = new SLabel("Total", SLabel.RIGHT);
        assertEquals("Total", l.getText());
        assertEquals(SLabel.RIGHT, l.getAlignment());
    }

    @Test
    @DisplayName("ctor rejects a bad alignment the way AWT does")
    void ctorRejectsABadAlignmentTheWayAwtDoes() {
        assertThrows(IllegalArgumentException.class, () -> new SLabel("x", 99));
    }

    @Test
    @DisplayName("AWT alignment constants are not the SwingConstants ones")
    void awtAlignmentConstantsAreNotTheSwingConstantsOnes() {
        // The trap this class exists next to: java.awt.Label's LEFT is 0
        // where SwingConstants.LEFT is 2, so a value borrowed from the
        // Swing set means something else here — or throws.
        assertEquals(0, SLabel.LEFT);
        assertEquals(1, SLabel.CENTER);
        assertEquals(2, SLabel.RIGHT);
        assertThrows(IllegalArgumentException.class,
                () -> new SLabel().setAlignment(SwingConstants.RIGHT));
    }

    // --- text ---------------------------------------------------------

    @Test
    @DisplayName("setText round-trips")
    void setTextRoundTrips() {
        SLabel l = new SLabel("before");
        l.setText("after");
        assertEquals("after", l.getText());
    }

    @Test
    @DisplayName("setText null renders blank rather than NPEing the element")
    void setTextNullRendersBlankRatherThanNpeingTheElement() {
        // Vaadin's element text content can't hold null; the emulator
        // vaadinx.awt.Label preserves AWT's null at its own field shadow.
        SLabel l = new SLabel("x");
        l.setText(null);
        assertEquals("", l.getText());
    }

    @Test
    @DisplayName("setText reaches the rendered span")
    void setTextReachesTheRenderedSpan() {
        SLabel l = new SLabel("before");
        UI.getCurrent().add(l);
        l.setText("after");
        assertEquals("after", LocatorJ._get(Span.class).getText());
    }

    // --- alignment ----------------------------------------------------

    @Test
    @DisplayName("alignment lands in CSS text-align")
    void alignmentLandsInCssTextAlign() {
        SLabel l = new SLabel("x");
        assertEquals("left", l.getElement().getStyle().get("text-align"));
        l.setAlignment(SLabel.CENTER);
        assertEquals("center", l.getElement().getStyle().get("text-align"));
        l.setAlignment(SLabel.RIGHT);
        assertEquals("right", l.getElement().getStyle().get("text-align"));
    }

    @Test
    @DisplayName("alignment round-trips losslessly through the CSS")
    void alignmentRoundTripsLosslesslyThroughTheCss() {
        // Unlike SJLabel's LEFT-and-LEADING-both-read-back-as-LEADING
        // collapse, AWT's three values and the three CSS keywords are in
        // bijection — which is why no shadow field is warranted (R_vaadin_first).
        SLabel l = new SLabel("x");
        for (int a : List.of(SLabel.LEFT, SLabel.CENTER, SLabel.RIGHT)) {
            l.setAlignment(a);
            assertEquals(a, l.getAlignment());
        }
    }

    @Test
    @DisplayName("bad alignment throws before touching the CSS")
    void badAlignmentThrowsBeforeTouchingTheCss() {
        SLabel l = new SLabel("x", SLabel.CENTER);
        assertThrows(IllegalArgumentException.class, () -> l.setAlignment(-1));
        assertEquals(SLabel.CENTER, l.getAlignment());
        assertEquals("center", l.getElement().getStyle().get("text-align"));
    }

    @Test
    @DisplayName("host is block-level so text-align has room to act")
    void hostIsBlockLevelSoTextAlignHasRoomToAct() {
        // An inline span shrink-wraps its text and text-align becomes
        // invisible; block-level fills the layout region, which is what
        // AWT's Label does. Silent under test without this assertion —
        // alignment would still "round-trip" while rendering flush left.
        assertEquals("block", new SLabel("x").getElement().getStyle().get("display"));
    }

    // --- Inherited ComponentMixin surface -----------------------------

    @Test
    @DisplayName("ComponentMixin works on this non-JComponent host")
    void componentMixinWorksOnThisNonJComponentHost() {
        SLabel l = new SLabel("x");
        l.setName("total-label");
        assertEquals("total-label", l.getName());
        assertEquals("total-label", l.getElement().getAttribute("data-swing-name"));

        l.setForeground(Color.RED);
        assertEquals(Color.RED, l.getForeground());

        l.setEnabled(false);
        assertFalse(l.isEnabled());
    }

    // --- toString shape / stubs ---------------------------------------

    @Test
    @DisplayName("paramString reports the alignment keyword and the text")
    void paramStringReportsTheAlignmentKeywordAndTheText() {
        assertEquals("align=right,text=Total", new SLabel("Total", SLabel.RIGHT).paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        assertNull(new SLabel().getAccessibleContext());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("getAccessibleContext"));
    }

    // --- Exit gate ----------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SLabel l = new SLabel("Total", SLabel.CENTER);
        l.setName("total-label");
        UI.getCurrent().add(l);
        l.setText("Total: 42");
        l.setAlignment(SLabel.RIGHT);
        l.setEnabled(true);
        assertEquals("Total: 42", LocatorJ._get(Span.class).getText());
        assertNoWarns("no stub WARNs expected from SLabel's happy path");
    }
}
