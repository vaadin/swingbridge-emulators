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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJToolBar (SD_sjtoolbar). Covers:
 *
 * <ol>
 *  <li><b>Ctors + orientation validation</b> — no-arg defaults to HORIZONTAL;
 *      int-arg accepts HORIZONTAL/VERTICAL, throws IAE on garbage.
 *  <li><b>Pinned flex CSS</b> — display:inline-flex, align-items:center, gap
 *      set on the host; flex-direction tracks orientation.
 *  <li><b>Runtime setOrientation flip</b> — switches flex-direction live,
 *      fires "orientation" PCE, no-op on equal state.
 *  <li><b>addSeparator pushes a perpendicular thin bar</b> — horizontal
 *      toolbar gets a min-width:1px child, vertical gets min-height:1px.
 *      Dimension-arg overload accepts and drops per R_layouts_close_enough.
 *  <li><b>setMargin → CSS padding</b> — Insets(2,4,2,4) writes
 *      "2px 4px 2px 4px"; null margin removes padding; PCE on change.
 *  <li><b>setLayout drop-and-WARN</b> — non-null LayoutManager WARNs; null
 *      is silently accepted.
 *  <li><b>L&amp;F surface</b> — getUIClassID == "ToolBarUI"; setUI WARNs.
 *  <li><b>add/remove children via inherited HasComponents</b> — Vaadin
 *      Buttons land as flex children.
 * </ol>
 */
class SJToolBarTest extends AbstractKaribuTest {

    // --- Ctors + orientation validation -------------------------------------

    @Test
    @DisplayName("no-arg ctor defaults to HORIZONTAL")
    void noArgCtorDefaultsToHorizontal() {
        SJToolBar tb = new SJToolBar();
        assertEquals(SwingConstants.HORIZONTAL, tb.getOrientation());
        assertNoWarns("no-arg ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("int-arg ctor accepts HORIZONTAL")
    void intArgCtorAcceptsHorizontal() {
        SJToolBar tb = new SJToolBar(SwingConstants.HORIZONTAL);
        assertEquals(SwingConstants.HORIZONTAL, tb.getOrientation());
    }

    @Test
    @DisplayName("int-arg ctor accepts VERTICAL")
    void intArgCtorAcceptsVertical() {
        SJToolBar tb = new SJToolBar(SwingConstants.VERTICAL);
        assertEquals(SwingConstants.VERTICAL, tb.getOrientation());
    }

    @Test
    @DisplayName("int-arg ctor throws IAE on garbage orientation")
    void intArgCtorThrowsIaeOnGarbageOrientation() {
        assertThrows(IllegalArgumentException.class, () -> new SJToolBar(42));
    }

    // --- Pinned flex CSS ----------------------------------------------------

    @Test
    @DisplayName("no-arg ctor writes pinned flex CSS to host element")
    void noArgCtorWritesPinnedFlexCssToHostElement() {
        SJToolBar tb = new SJToolBar();
        Style s = tb.getElement().getStyle();
        assertEquals("inline-flex", s.get("display"));
        assertEquals("row", s.get("flex-direction"));
        assertEquals("center", s.get("align-items"));
        assertEquals("var(--vaadin-gap-xs)", s.get("gap"));
    }

    @Test
    @DisplayName("VERTICAL ctor pins flex-direction to column")
    void verticalCtorPinsFlexDirectionToColumn() {
        SJToolBar tb = new SJToolBar(SwingConstants.VERTICAL);
        assertEquals("column", tb.getElement().getStyle().get("flex-direction"));
    }

    // --- Runtime setOrientation flip ----------------------------------------

    @Test
    @DisplayName("setOrientation flips flex-direction live")
    void setOrientationFlipsFlexDirectionLive() {
        SJToolBar tb = new SJToolBar();  // HORIZONTAL by default → row
        assertEquals("row", tb.getElement().getStyle().get("flex-direction"));
        tb.setOrientation(SwingConstants.VERTICAL);
        assertEquals("column", tb.getElement().getStyle().get("flex-direction"));
        tb.setOrientation(SwingConstants.HORIZONTAL);
        assertEquals("row", tb.getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("setOrientation fires PCE on change and no-op on equal")
    void setOrientationFiresPceOnChangeAndNoOpOnEqual() {
        SJToolBar tb = new SJToolBar();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tb.addPropertyChangeListener(events::add);

        tb.setOrientation(SwingConstants.VERTICAL);
        List<PropertyChangeEvent> o = events.stream()
                .filter(it -> "orientation".equals(it.getPropertyName()))
                .toList();
        assertEquals(1, o.size(), "exactly one PCE on actual change");
        assertEquals(SwingConstants.HORIZONTAL, o.get(0).getOldValue());
        assertEquals(SwingConstants.VERTICAL, o.get(0).getNewValue());

        // No-op on equal state.
        events.clear();
        tb.setOrientation(SwingConstants.VERTICAL);
        assertTrue(events.stream().noneMatch(it -> "orientation".equals(it.getPropertyName())),
                "no-op on equal orientation should fire no PCE");
    }

    @Test
    @DisplayName("setOrientation throws IAE on garbage")
    void setOrientationThrowsIaeOnGarbage() {
        SJToolBar tb = new SJToolBar();
        assertThrows(IllegalArgumentException.class, () -> tb.setOrientation(99));
    }

    // --- addSeparator -------------------------------------------------------

    @Test
    @DisplayName("addSeparator on HORIZONTAL pushes a vertical thin bar")
    void addSeparatorOnHorizontalPushesAVerticalThinBar() {
        SJToolBar tb = new SJToolBar();  // HORIZONTAL
        tb.addSeparator();
        List<com.vaadin.flow.component.Component> children = tb.getChildren().toList();
        assertEquals(1, children.size());
        Div sep = (Div) children.get(0);
        assertEquals("", sep.getElement().getAttribute("data-emul-toolbar-separator"));
        Style ss = sep.getElement().getStyle();
        assertEquals("var(--vaadin-border-color-secondary)", ss.get("background-color"));
        assertEquals("stretch", ss.get("align-self"));
        assertEquals("1px", ss.get("min-width"));
        assertNull(ss.get("min-height"));
    }

    @Test
    @DisplayName("addSeparator on VERTICAL pushes a horizontal thin bar")
    void addSeparatorOnVerticalPushesAHorizontalThinBar() {
        SJToolBar tb = new SJToolBar(SwingConstants.VERTICAL);
        tb.addSeparator();
        Div sep = (Div) tb.getChildren().toList().get(0);
        assertEquals("1px", sep.getElement().getStyle().get("min-height"));
        assertNull(sep.getElement().getStyle().get("min-width"));
    }

    @Test
    @DisplayName("addSeparator with Dimension drops the dim per R_layouts_close_enough")
    void addSeparatorWithDimensionDropsTheDim() {
        SJToolBar tb = new SJToolBar();
        tb.addSeparator(new Dimension(20, 10));
        Div sep = (Div) tb.getChildren().toList().get(0);
        // Dimension intentionally ignored — the bar is 1px regardless.
        assertEquals("1px", sep.getElement().getStyle().get("min-width"));
        // Argument WARN-free — accepted-and-dropped per R_layouts_close_enough, not stub.
        assertNoWarns("addSeparator(Dimension) is R_layouts_close_enough accept-drop, not WARN: " + capturedWarns);
    }

    // --- setMargin → CSS padding --------------------------------------------

    @Test
    @DisplayName("setMargin writes padding shorthand")
    void setMarginWritesPaddingShorthand() {
        SJToolBar tb = new SJToolBar();
        tb.setMargin(new Insets(2, 4, 6, 8));
        // Order matches CSS shorthand: top right bottom left.
        assertEquals("2px 8px 6px 4px", tb.getElement().getStyle().get("padding"));
        assertEquals(new Insets(2, 4, 6, 8), tb.getMargin());
    }

    @Test
    @DisplayName("setMargin null removes padding")
    void setMarginNullRemovesPadding() {
        SJToolBar tb = new SJToolBar();
        tb.setMargin(new Insets(2, 4, 2, 4));
        assertNotNull(tb.getElement().getStyle().get("padding"));
        tb.setMargin(null);
        assertNull(tb.getElement().getStyle().get("padding"));
        assertNull(tb.getMargin());
    }

    @Test
    @DisplayName("setMargin fires PCE on change")
    void setMarginFiresPceOnChange() {
        SJToolBar tb = new SJToolBar();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tb.addPropertyChangeListener("margin", events::add);
        tb.setMargin(new Insets(2, 4, 2, 4));
        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());
        assertEquals(new Insets(2, 4, 2, 4), events.get(0).getNewValue());

        // No-op on equal Insets.
        events.clear();
        tb.setMargin(new Insets(2, 4, 2, 4));
        assertTrue(events.isEmpty(),
                "no-op on equal Insets should fire no PCE");
    }

    // --- setLayout drop-and-WARN --------------------------------------------

    @Test
    @DisplayName("setLayout with non-null LayoutManager WARNs")
    void setLayoutWithNonNullLayoutManagerWarns() {
        SJToolBar tb = new SJToolBar();
        tb.setLayout(new BorderLayout());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setLayout")),
                "setLayout(BorderLayout) should WARN per R_vaadin_first pinned-flex stance: " + capturedWarns);
    }

    @Test
    @DisplayName("setLayout null is silently accepted")
    void setLayoutNullIsSilentlyAccepted() {
        SJToolBar tb = new SJToolBar();
        tb.setLayout(null);
        assertNoWarns("setLayout(null) is the Container.removeAll teardown path, no WARN");
    }

    @Test
    @DisplayName("setLayout drop preserves pinned flex CSS")
    void setLayoutDropPreservesPinnedFlexCss() {
        SJToolBar tb = new SJToolBar();
        tb.setLayout(new BorderLayout());
        // The toolbar's flex CSS must survive — that's the whole point of
        // the R_vaadin_first drop. A user installing BorderLayout doesn't get
        // grid-template-areas clobbering our flex.
        Style s = tb.getElement().getStyle();
        assertEquals("inline-flex", s.get("display"));
        assertEquals("row", s.get("flex-direction"));
    }

    // --- L&F + Children ----------------------------------------------------

    @Test
    @DisplayName("getUIClassID returns ToolBarUI")
    void getUiClassIdReturnsToolBarUi() {
        assertEquals("ToolBarUI", new SJToolBar().getUIClassID());
    }

    @Test
    @DisplayName("setUI WARNs (L&F surgery not modelled)")
    void setUiWarns() {
        SJToolBar tb = new SJToolBar();
        tb.setUI(null);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setUI")));
    }

    @Test
    @DisplayName("Vaadin Button children land as flex items")
    void vaadinButtonChildrenLandAsFlexItems() {
        SJToolBar tb = new SJToolBar();
        tb.add(new Button("New"));
        tb.add(new Button("Delete"));
        tb.addSeparator();
        tb.add(new Button("Upload"));
        assertEquals(4, tb.getChildren().count());
    }

    @Test
    @DisplayName("data-swing-class stamped via _installSwingClass")
    void dataSwingClassStamped() {
        SJToolBar tb = new SJToolBar();
        // resolveSwingClassName uses Class.getSimpleName.
        assertEquals("SJToolBar", tb.getElement().getAttribute("data-swing-class"));
    }
}
