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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjlabel's SJLabel surrogate. Covers:
 *
 * <ol>
 *  <li>Constructors + UIClassID + JDK defaults.
 *  <li>Text round-trip via the inner text Span (setText doesn't clobber
 *      a sibling icon child, setIcon doesn't clobber the text).
 *  <li>Icon Path 1 — ImageIcon → Vaadin Image, null clears, non-ImageIcon
 *      drops with a WARN.
 *  <li>labelFor delegates to NativeLabel.setFor with a real Component.
 *  <li>R_vaadin_first drop-and-WARN — alignment / text-position / mnemonic /
 *      iconTextGap setters validate (R_match_swing_errors IAE) but drop the value, getters
 *      return JDK defaults.
 *  <li>Happy-path zero stub WARNs across the UI-functional surface.
 * </ol>
 */
class SJLabelTest extends AbstractKaribuTest {

    /** A tiny transparent ImageIcon — the only Path 1 input shape these tests need. */
    private static ImageIcon icon(int size) {
        return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
    }

    /** The inner text Span's innerHTML, which is where HTML-mode markup lands. */
    private static String innerHtml(SJLabel l) {
        return l.getElement().getChildren()
                .filter(it -> "span".equals(it.getTag()))
                .findFirst()
                .map(it -> it.getProperty("innerHTML", ""))
                .orElse(null);
    }

    /** The inner text Span's {@code white-space}, or {@code null} when unset. */
    private static String whiteSpace(SJLabel l) {
        return l.getElement().getChildren()
                .filter(it -> "span".equals(it.getTag()))
                .findFirst()
                .map(it -> it.getStyle().get("white-space"))
                .orElse(null);
    }

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor leaves text empty")
    void noArgCtorLeavesTextEmpty() {
        // Vaadin Span.getText() returns "" for an empty span — R_vaadin_first lossy
        // round-trip vs JDK's null on a default JLabel.
        assertEquals("", new SJLabel().getText());
    }

    @Test
    @DisplayName("string ctor seeds text on the inner span")
    void stringCtorSeedsTextOnTheInnerSpan() {
        assertEquals("hello", new SJLabel("hello").getText());
    }

    @Test
    @DisplayName("icon-only ctor installs icon as a child of the label")
    void iconOnlyCtorInstallsIconAsAChild() {
        SJLabel l = new SJLabel(icon(2));
        assertNotNull(l.getIcon());
        assertInstanceOf(Image.class, l.getIcon());
    }

    @Test
    @DisplayName("getUIClassID is LabelUI for UIManager compatibility")
    void getUiClassIdIsLabelUi() {
        assertEquals("LabelUI", new SJLabel().getUIClassID());
    }

    @Test
    @DisplayName("JDK defaults — alignment LEADING, text-position TRAILING, gap 4, mnemonic 0 -1")
    void jdkDefaults() {
        SJLabel l = new SJLabel();
        assertEquals(SwingConstants.LEADING, l.getHorizontalAlignment());
        assertEquals(SwingConstants.CENTER, l.getVerticalAlignment());
        assertEquals(SwingConstants.TRAILING, l.getHorizontalTextPosition());
        assertEquals(SwingConstants.CENTER, l.getVerticalTextPosition());
        assertEquals(4, l.getIconTextGap());
        assertEquals(0, l.getDisplayedMnemonic());
        assertEquals(-1, l.getDisplayedMnemonicIndex());
    }

    // --- Text + icon coexistence (the load-bearing slice) ------------

    @Test
    @DisplayName("setIcon then setText keeps both children")
    void setIconThenSetTextKeepsBothChildren() {
        // Regression: Vaadin HasText.setText calls Element.setText which
        // removes all children. SJLabel writes through the inner text
        // Span instead, so the icon survives.
        SJLabel l = new SJLabel();
        l.setIcon(icon(1));
        l.setText("Caption");

        assertNotNull(l.getIcon(), "icon should survive a subsequent setText");
        assertEquals("Caption", l.getText());
    }

    @Test
    @DisplayName("setText then setIcon keeps text content readable")
    void setTextThenSetIconKeepsTextReadable() {
        SJLabel l = new SJLabel("Caption");
        l.setIcon(icon(1));
        assertEquals("Caption", l.getText());
        assertNotNull(l.getIcon());
    }

    @Test
    @DisplayName("setText null lands as empty string per R_vaadin_first")
    void setTextNullLandsAsEmptyString() {
        SJLabel l = new SJLabel("set");
        l.setText(null);
        assertEquals("", l.getText());
        assertNoWarns();
    }

    @Test
    @DisplayName("setText fires text PCE with old and new")
    void setTextFiresTextPce() {
        SJLabel l = new SJLabel("old");
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("text", events::add);
        l.setText("new");
        assertEquals(1, events.size());
        assertEquals("old", events.get(0).getOldValue());
        assertEquals("new", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setText same value fires no PCE")
    void setTextSameValueFiresNoPce() {
        SJLabel l = new SJLabel("same");
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("text", events::add);
        l.setText("same");
        assertEquals(0, events.size());
    }

    // --- HTML rendering (Swing's BasicHTML.isHTMLString trigger) -----

    @Test
    @DisplayName("setText with leading html tag renders inner markup as DOM")
    void setTextWithLeadingHtmlTagRendersInnerMarkup() {
        SJLabel l = new SJLabel();
        l.setText("<html><b>bold</b> rest</html>");
        // textSpan's inner HTML should contain the bold tag as parsed DOM.
        assertEquals("<b>bold</b> rest", innerHtml(l));
    }

    @Test
    @DisplayName("setText with html round-trips raw markup via getText")
    void setTextWithHtmlRoundTripsRawMarkup() {
        SJLabel l = new SJLabel();
        String raw = "<html><body style='width:230px'>hello</body></html>";
        l.setText(raw);
        assertEquals(raw, l.getText());
    }

    @Test
    @DisplayName("setText strips outer html and body wrappers from innerHTML")
    void setTextStripsOuterHtmlAndBodyWrappers() {
        SJLabel l = new SJLabel();
        l.setText("<html><body style='width:230px'>plain</body></html>");
        assertEquals("plain", innerHtml(l));
    }

    @Test
    @DisplayName("plain text never wraps; html text does, and plain again does not")
    void plainTextNeverWrapsHtmlDoes() {
        SJLabel l = new SJLabel("Item Name");
        assertEquals("nowrap", whiteSpace(l));

        l.setText("<html>a long caption the html view may wrap</html>");
        assertNull(whiteSpace(l), "html text wraps, as the JDK's HTML view does");

        l.setText("Rack Number");
        assertEquals("nowrap", whiteSpace(l));
    }

    @Test
    @DisplayName("setText html then plain exits HTML mode")
    void setTextHtmlThenPlainExitsHtmlMode() {
        SJLabel l = new SJLabel();
        l.setText("<html><b>bold</b></html>");
        l.setText("plain again");
        // getText round-trips the plain string (HTML mode exited —
        // when htmlText is non-null getText would return the raw HTML).
        assertEquals("plain again", l.getText());
        // And a third setText, also plain, must round-trip too.
        l.setText("third");
        assertEquals("third", l.getText());
    }

    @Test
    @DisplayName("setText case-insensitive html tag detection")
    void setTextCaseInsensitiveHtmlTagDetection() {
        SJLabel l = new SJLabel();
        l.setText("<HTML><i>upper</i></HTML>");
        assertEquals("<i>upper</i>", innerHtml(l));
    }

    @Test
    @DisplayName("setText html fires text PCE with raw markup as old and new")
    void setTextHtmlFiresTextPceWithRawMarkup() {
        SJLabel l = new SJLabel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("text", events::add);
        l.setText("<html><b>bold</b></html>");
        l.setText("plain");
        assertEquals(2, events.size());
        assertEquals("", events.get(0).getOldValue());
        assertEquals("<html><b>bold</b></html>", events.get(0).getNewValue());
        assertEquals("<html><b>bold</b></html>", events.get(1).getOldValue());
        assertEquals("plain", events.get(1).getNewValue());
    }

    @Test
    @DisplayName("setText with leading whitespace before html is plain text per Swing")
    void setTextWithLeadingWhitespaceBeforeHtmlIsPlain() {
        // Swing's BasicHTML.isHTMLString requires the leading '<' at index 0.
        SJLabel l = new SJLabel();
        l.setText(" <html><b>x</b></html>");
        // Treated as plain — getText round-trips the literal string.
        assertEquals(" <html><b>x</b></html>", l.getText());
    }

    // --- Icon Path 1 -------------------------------------------------

    @Test
    @DisplayName("setIcon ImageIcon installs Vaadin Image with no WARN")
    void setIconImageIconInstallsVaadinImage() {
        SJLabel l = new SJLabel();
        l.setIcon(icon(2));
        assertNotNull(l.getIcon());
        assertInstanceOf(Image.class, l.getIcon());
        assertNoWarns("Path 1 should be silent");
    }

    @Test
    @DisplayName("setIcon null clears the slot")
    void setIconNullClearsTheSlot() {
        SJLabel l = new SJLabel();
        l.setIcon(icon(1));
        assertNotNull(l.getIcon());
        l.setIcon(null);
        assertNull(l.getIcon());
        assertNoWarns();
    }

    @Test
    @DisplayName("setIcon swap replaces the previous child")
    void setIconSwapReplacesThePreviousChild() {
        SJLabel l = new SJLabel();
        l.setIcon(icon(1));
        Component first = l.getIcon();
        l.setIcon(icon(2));
        Component second = l.getIcon();
        assertNotNull(second);
        assertNotNull(first);
        assertNotSame(first, second, "setIcon should install a fresh child on swap");
    }

    @Test
    @DisplayName("setIcon non-ImageIcon WARNs and clears the slot pending Path 2-3")
    void setIconNonImageIconWarnsAndClears() {
        Icon custom = new Icon() {
            @Override
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 4;
            }

            @Override
            public int getIconHeight() {
                return 4;
            }
        };
        SJLabel l = new SJLabel();
        l.setIcon(custom);
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setIcon"));
        assertNull(l.getIcon());
    }

    @Test
    @DisplayName("setIcon fires icon PCE with Vaadin Component-typed values")
    void setIconFiresIconPce() {
        SJLabel l = new SJLabel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("icon", events::add);
        l.setIcon(icon(1));
        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());
        assertInstanceOf(Image.class, events.get(0).getNewValue());
    }

    // --- labelFor ----------------------------------------------------

    @Test
    @DisplayName("setLabelFor delegates to NativeLabel setFor and round-trips")
    void setLabelForDelegatesToNativeLabelSetFor() {
        Button target = new Button("target");
        UI.getCurrent().add(target);
        SJLabel l = new SJLabel("name:");
        l.setLabelFor(target);

        assertSame(target, l.getLabelFor());
        // Vaadin issues a generated id and writes it to the for attribute.
        assertNotNull(l.getFor().orElse(null), "Vaadin should write the for= attribute");
    }

    @Test
    @DisplayName("setLabelFor null clears")
    void setLabelForNullClears() {
        SJLabel l = new SJLabel();
        Button target = new Button("t");
        UI.getCurrent().add(target);
        l.setLabelFor(target);
        l.setLabelFor(null);
        assertNull(l.getLabelFor());
    }

    @Test
    @DisplayName("setLabelFor fires labelFor PCE")
    void setLabelForFiresLabelForPce() {
        Button target = new Button("t");
        UI.getCurrent().add(target);
        SJLabel l = new SJLabel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("labelFor", events::add);
        l.setLabelFor(target);
        assertEquals(1, events.size());
        assertSame(target, events.get(0).getNewValue());
    }

    // --- Layout setters (drive inline-flex CSS) ---------------------
    // R_match_swing_errors IAE preserved; the four alignment / text-position / iconTextGap
    // setters round-trip via local fields and write the corresponding
    // flex CSS to the host <label>. SHelper.onNoop / onUnimplemented
    // never fires from this surface — silent at the WARN-inventory level.

    @Test
    @DisplayName("host label runs as inline-flex with JDK defaults")
    void hostLabelRunsAsInlineFlexWithJdkDefaults() {
        SJLabel l = new SJLabel();
        Style style = l.getElement().getStyle();
        assertEquals("inline-flex", style.get("display"));
        // JDK defaults: TRAILING h-text-pos + CENTER v-text-pos → row.
        assertEquals("row", style.get("flex-direction"));
        // LEADING h-align → flex-start; CENTER v-align → center; gap 4px.
        assertEquals("flex-start", style.get("justify-content"));
        assertEquals("center", style.get("align-items"));
        assertEquals("4px", style.get("gap"));
    }

    @Test
    @DisplayName("icon-only ctor seeds horizontalAlignment to CENTER per JDK")
    void iconOnlyCtorSeedsHorizontalAlignmentToCenter() {
        SJLabel l = new SJLabel(icon(1));
        assertEquals(SwingConstants.CENTER, l.getHorizontalAlignment());
        assertEquals("center", l.getElement().getStyle().get("justify-content"));
    }

    @Test
    @DisplayName("setHorizontalAlignment writes justify-content with TRAILING canonical readback")
    void setHorizontalAlignmentWritesJustifyContent() {
        // R_vaadin_first lossy round-trip: RIGHT and TRAILING both write `flex-end`,
        // and the getter reads back as TRAILING (canonical) — same
        // shape as setBorder/getBorder per SD_border_css_lossy.
        SJLabel l = new SJLabel();
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        assertEquals(SwingConstants.TRAILING, l.getHorizontalAlignment());
        assertEquals("flex-end", l.getElement().getStyle().get("justify-content"));
        assertNoWarns();
    }

    @Test
    @DisplayName("setHorizontalAlignment fires PCE")
    void setHorizontalAlignmentFiresPce() {
        SJLabel l = new SJLabel();
        List<PropertyChangeEvent> events = new ArrayList<>();
        l.addPropertyChangeListener("horizontalAlignment", events::add);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        assertEquals(1, events.size());
        assertEquals(SwingConstants.LEADING, events.get(0).getOldValue());
        assertEquals(SwingConstants.CENTER, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setHorizontalAlignment invalid throws IAE per R_match_swing_errors")
    void setHorizontalAlignmentInvalidThrowsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new SJLabel().setHorizontalAlignment(SwingConstants.TOP));
    }

    @Test
    @DisplayName("setVerticalAlignment round-trips and writes align-items")
    void setVerticalAlignmentRoundTripsAndWritesAlignItems() {
        SJLabel l = new SJLabel();
        l.setVerticalAlignment(SwingConstants.TOP);
        assertEquals(SwingConstants.TOP, l.getVerticalAlignment());
        assertEquals("flex-start", l.getElement().getStyle().get("align-items"));
    }

    @Test
    @DisplayName("setVerticalAlignment invalid throws IAE per R_match_swing_errors")
    void setVerticalAlignmentInvalidThrowsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new SJLabel().setVerticalAlignment(SwingConstants.LEADING));
    }

    @Test
    @DisplayName("setVerticalTextPosition TOP promotes layout to column-reverse")
    void setVerticalTextPositionTopPromotesToColumnReverse() {
        // TOP = text above icon → DOM order [icon, text] needs reversing
        // along the column axis, so column-reverse.
        SJLabel l = new SJLabel();
        l.setVerticalTextPosition(SwingConstants.TOP);
        assertEquals("column-reverse", l.getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("setVerticalTextPosition BOTTOM uses column direction")
    void setVerticalTextPositionBottomUsesColumn() {
        SJLabel l = new SJLabel();
        l.setVerticalTextPosition(SwingConstants.BOTTOM);
        assertEquals("column", l.getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("setHorizontalTextPosition LEADING flips to row-reverse")
    void setHorizontalTextPositionLeadingFlipsToRowReverse() {
        SJLabel l = new SJLabel();
        l.setHorizontalTextPosition(SwingConstants.LEADING);
        assertEquals("row-reverse", l.getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("setHorizontalTextPosition TRAILING uses row direction (JDK default)")
    void setHorizontalTextPositionTrailingUsesRow() {
        SJLabel l = new SJLabel();
        l.setHorizontalTextPosition(SwingConstants.LEADING);  // flip away from default
        l.setHorizontalTextPosition(SwingConstants.TRAILING);
        assertEquals("row", l.getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("vertical text-position non-CENTER overrides horizontal direction")
    void verticalTextPositionNonCenterOverridesHorizontal() {
        SJLabel l = new SJLabel();
        l.setHorizontalTextPosition(SwingConstants.LEADING);  // would set row-reverse
        l.setVerticalTextPosition(SwingConstants.BOTTOM);     // promotes to column
        assertEquals("column", l.getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("setIconTextGap round-trips and writes CSS gap")
    void setIconTextGapRoundTripsAndWritesCssGap() {
        SJLabel l = new SJLabel();
        l.setIconTextGap(12);
        assertEquals(12, l.getIconTextGap());
        assertEquals("12px", l.getElement().getStyle().get("gap"));
    }

    @Test
    @DisplayName("setDisplayedMnemonic is silent and drops the value Bucket B")
    void setDisplayedMnemonicIsSilentAndDrops() {
        SJLabel l = new SJLabel();
        l.setDisplayedMnemonic(0);
        l.setDisplayedMnemonic('A');
        assertNoWarns();
        assertEquals(0, l.getDisplayedMnemonic(), "value dropped");
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex is silent including -1 sentinel Bucket B")
    void setDisplayedMnemonicIndexIsSilent() {
        new SJLabel().setDisplayedMnemonicIndex(-1);
        SJLabel l = new SJLabel("abc");
        l.setDisplayedMnemonicIndex(-1);
        l.setDisplayedMnemonicIndex(1);
        assertNoWarns();
        assertEquals(-1, l.getDisplayedMnemonicIndex());
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex out-of-range throws IAE per R_match_swing_errors")
    void setDisplayedMnemonicIndexOutOfRangeThrowsIae() {
        assertThrows(IllegalArgumentException.class,
                () -> new SJLabel("abc").setDisplayedMnemonicIndex(5));
    }

    // --- Happy path --------------------------------------------------

    @Test
    @DisplayName("full SJLabel happy path fires no stub WARNs")
    void fullSjLabelHappyPathFiresNoStubWarns() {
        SJLabel l = new SJLabel("Welcome");
        l.setIcon(icon(2));
        l.setText("Updated");
        UI.getCurrent().add(l);
        assertNoWarns();
    }
}
