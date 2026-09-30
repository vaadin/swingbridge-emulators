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

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.Container;

import java.awt.AWTError;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoxLayoutTest extends AbstractKaribuTest {

    @Test
    @DisplayName("Y_AXIS writes display flex + flex-direction column")
    void yAxisWritesFlexColumn() {
        Container c = new Container();
        new BoxLayout(c, BoxLayout.Y_AXIS).layoutContainer(c);

        Style style = c.getPeer().getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("column", style.get("flex-direction"));
    }

    @Test
    @DisplayName("X_AXIS writes flex-direction row")
    void xAxisWritesFlexRow() {
        Container c = new Container();
        new BoxLayout(c, BoxLayout.X_AXIS).layoutContainer(c);
        assertEquals("row", c.getPeer().getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("LINE_AXIS pins to row (LTR — RTL flip out of scope per R_layouts_close_enough)")
    void lineAxisPinsToRow() {
        Container c = new Container();
        new BoxLayout(c, BoxLayout.LINE_AXIS).layoutContainer(c);
        assertEquals("row", c.getPeer().getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("PAGE_AXIS pins to column (LTR — RTL flip out of scope per R_layouts_close_enough)")
    void pageAxisPinsToColumn() {
        Container c = new Container();
        new BoxLayout(c, BoxLayout.PAGE_AXIS).layoutContainer(c);
        assertEquals("column", c.getPeer().getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("invalid axis throws AWTError at construction (R_match_swing_errors — Swing matches)")
    void invalidAxisThrowsAtConstruction() {
        Container c = new Container();
        assertThrows(AWTError.class, () -> new BoxLayout(c, 999));
        assertThrows(AWTError.class, () -> new BoxLayout(c, -1));
    }

    @Test
    @DisplayName("invalidateLayout with foreign container throws AWTError (R_match_swing_errors)")
    void invalidateLayoutRejectsForeignContainer() {
        Container owner = new Container();
        Container foreign = new Container();
        BoxLayout box = new BoxLayout(owner, BoxLayout.Y_AXIS);

        // Own container: silent.
        box.invalidateLayout(owner);

        // Foreign container: AWTError, matching JDK BoxLayout.checkContainer.
        AWTError ex = assertThrows(AWTError.class, () -> box.invalidateLayout(foreign));
        assertEquals("BoxLayout can't be shared", ex.getMessage());
    }

    @Test
    @DisplayName("layoutContainer with foreign container throws AWTError (R_match_swing_errors)")
    void layoutContainerRejectsForeignContainer() {
        Container owner = new Container();
        Container foreign = new Container();
        BoxLayout box = new BoxLayout(owner, BoxLayout.Y_AXIS);

        assertThrows(AWTError.class, () -> box.layoutContainer(foreign));
    }

    @Test
    @DisplayName("getAxis and getTarget round-trip")
    void axisAndTargetRoundTrip() {
        Container c = new Container();
        BoxLayout box = new BoxLayout(c, BoxLayout.Y_AXIS);

        assertEquals(BoxLayout.Y_AXIS, box.getAxis());
        assertSame(c, box.getTarget());
    }

    @Test
    @DisplayName("addLayoutComponent is a true no-op (no DOM mutation)")
    void addLayoutComponentIsANoOp() {
        // JDK BoxLayout has no per-child storage — neither overload writes
        // anything. Regression against accidentally porting the BorderLayout
        // pattern (which DOES write per-child grid-area).
        Container c = new Container();
        Container child = new Container();
        BoxLayout box = new BoxLayout(c, BoxLayout.Y_AXIS);

        box.addLayoutComponent("ignored", child);
        box.addLayoutComponent(child, "ignored");

        // No CSS landed on the child's peer.
        Style childStyle = child.getPeer().getElement().getStyle();
        assertNull(childStyle.get("flex-grow"));
        assertNull(childStyle.get("width"));
        assertNull(childStyle.get("grid-area"));
    }

    @Test
    @DisplayName("setLayout(BoxLayout) on a JPanel + validate writes CSS")
    void setLayoutOnAPanelThenValidateWritesCss() {
        // End-to-end: construct a BoxLayout pointing at a JPanel, install,
        // validate. Same shape as the FlowLayout / BorderLayout sanity tests.
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.validate();

        Style style = panel.getPeer().getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("column", style.get("flex-direction"));
    }

    @Test
    @DisplayName("non-Div peer gets no CSS — onUnsupportedPeerShape guard per D_layout_css_on_content")
    void nonDivPeerGetsNoCss() {
        // Container with a Span peer — applyContainerCss refuses to write,
        // so no display style lands. FlowLayoutTest precedent.
        Container c = new Container(new Span()) {
        };
        new BoxLayout(c, BoxLayout.Y_AXIS).layoutContainer(c);

        assertNull(c.getPeer().getElement().getStyle().get("display"));
        assertNull(c.getPeer().getElement().getStyle().get("flex-direction"));
    }

    @Test
    @DisplayName("maximumLayoutSize returns Integer MAX_VALUE (BorderLayout precedent)")
    void maximumLayoutSizeIsMaxValue() {
        Container c = new Container();
        BoxLayout box = new BoxLayout(c, BoxLayout.Y_AXIS);
        Dimension max = box.maximumLayoutSize(c);
        assertEquals(Integer.MAX_VALUE, max.width);
        assertEquals(Integer.MAX_VALUE, max.height);
    }

    @Test
    @DisplayName("preferred and minimum sizes are dummy 1x1")
    void preferredAndMinimumAreDummy() {
        Container c = new Container();
        BoxLayout box = new BoxLayout(c, BoxLayout.Y_AXIS);
        assertEquals(1, box.preferredLayoutSize(c).width);
        assertEquals(1, box.preferredLayoutSize(c).height);
        assertEquals(1, box.minimumLayoutSize(c).width);
        assertEquals(1, box.minimumLayoutSize(c).height);
    }

    @Test
    @DisplayName("toString reports the axis name")
    void toStringReportsTheAxisName() {
        Container c = new Container();
        String s = new BoxLayout(c, BoxLayout.Y_AXIS).toString();
        assertNotNull(s);
        assertTrue(s.contains("Y_AXIS"), "expected Y_AXIS in " + s);
    }
}
