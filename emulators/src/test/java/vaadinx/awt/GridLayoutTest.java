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

package vaadinx.awt;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GridLayoutTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    @Test
    @DisplayName("both dimensions zero throws IllegalArgumentException (R_match_swing_errors)")
    void bothDimensionsZeroThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new GridLayout(0, 0));
    }

    @Test
    @DisplayName("setRows to zero while cols is zero throws (R_match_swing_errors)")
    void setRowsToZeroWhileColsIsZeroThrows() {
        GridLayout g = new GridLayout(2, 0); // cols == 0, rows fixed
        assertThrows(IllegalArgumentException.class, () -> g.setRows(0));
    }

    @Test
    @DisplayName("setColumns to zero while rows is zero throws (R_match_swing_errors)")
    void setColumnsToZeroWhileRowsIsZeroThrows() {
        GridLayout g = new GridLayout(0, 2); // rows == 0, cols fixed
        assertThrows(IllegalArgumentException.class, () -> g.setColumns(0));
    }

    @Test
    @DisplayName("getters round-trip ctor args")
    void gettersRoundTripCtorArgs() {
        GridLayout g = new GridLayout(3, 4, 7, 9);
        assertEquals(3, g.getRows());
        assertEquals(4, g.getColumns());
        assertEquals(7, g.getHgap());
        assertEquals(9, g.getVgap());
    }

    @Test
    @DisplayName("rows fixed — columns derived from child count")
    void rowsFixedColumnsDerivedFromChildCount() {
        // GridLayout(2, 0): 2 rows fixed, 4 children → 2 columns.
        Container parent = new Container();
        GridLayout gl = new GridLayout(2, 0);
        parent.setLayout(gl);
        for (int i = 0; i < 4; i++) {
            parent.add(component());
        }

        gl.layoutContainer(parent);

        var style = parent.getPeer().getElement().getStyle();
        assertEquals("grid", style.get("display"));
        assertEquals("repeat(2, 1fr)", style.get("grid-template-columns"));
        assertEquals("repeat(2, 1fr)", style.get("grid-template-rows"));
    }

    @Test
    @DisplayName("columns fixed — rows derived with ceiling")
    void columnsFixedRowsDerivedWithCeiling() {
        // GridLayout(0, 3): 3 columns fixed, 7 children → ceil(7/3) = 3 rows.
        Container parent = new Container();
        GridLayout gl = new GridLayout(0, 3);
        parent.setLayout(gl);
        for (int i = 0; i < 7; i++) {
            parent.add(component());
        }

        gl.layoutContainer(parent);

        var style = parent.getPeer().getElement().getStyle();
        assertEquals("repeat(3, 1fr)", style.get("grid-template-columns"));
        assertEquals("repeat(3, 1fr)", style.get("grid-template-rows"));
    }

    @Test
    @DisplayName("default ctor is one row — columns equal child count")
    void defaultCtorIsOneRowColumnsEqualChildCount() {
        Container parent = new Container();
        GridLayout gl = new GridLayout();
        parent.setLayout(gl);
        for (int i = 0; i < 3; i++) {
            parent.add(component());
        }

        gl.layoutContainer(parent);

        var style = parent.getPeer().getElement().getStyle();
        assertEquals("repeat(1, 1fr)", style.get("grid-template-rows"));
        assertEquals("repeat(3, 1fr)", style.get("grid-template-columns"));
    }

    @Test
    @DisplayName("hgap and vgap map to gap shorthand (row-gap column-gap)")
    void hgapAndVgapMapToGapShorthand() {
        Container parent = new Container();
        GridLayout gl = new GridLayout(1, 2, 10, 4); // hgap=10, vgap=4
        parent.setLayout(gl);
        parent.add(component());
        parent.add(component());

        gl.layoutContainer(parent);

        // CSS gap shorthand is "row-gap column-gap" → "vgap px hgap px".
        assertEquals("4px 10px", parent.getPeer().getElement().getStyle().get("gap"));
    }

    @Test
    @DisplayName("cells stretch to fill (GridLayout resizes components)")
    void cellsStretchToFill() {
        Container parent = new Container();
        GridLayout gl = new GridLayout(1, 1);
        parent.setLayout(gl);
        parent.add(component());

        gl.layoutContainer(parent);

        var style = parent.getPeer().getElement().getStyle();
        assertEquals("stretch", style.get("align-items"));
        assertEquals("stretch", style.get("justify-items"));
    }

    @Test
    @DisplayName("empty container emits valid clamped grid — no repeat(0)")
    void emptyContainerEmitsValidClampedGrid() {
        // JDK lays out nothing for an empty container; we clamp to repeat(1,1fr)
        // so the emitted template is always valid CSS.
        Container parent = new Container();
        GridLayout gl = new GridLayout(0, 3); // rows would compute to 0 with no children
        parent.setLayout(gl);

        gl.layoutContainer(parent);

        var style = parent.getPeer().getElement().getStyle();
        assertEquals("repeat(3, 1fr)", style.get("grid-template-columns"));
        assertEquals("repeat(1, 1fr)", style.get("grid-template-rows"));
    }

    @Test
    @DisplayName("non-Div peer gets no CSS — onUnsupportedPeerShape guard per D_layout_css_on_content")
    void nonDivPeerGetsNoCss() {
        Container c = new Container(new Span()) {
        };
        new GridLayout().layoutContainer(c);

        assertNull(c.getPeer().getElement().getStyle().get("display"));
        assertNull(c.getPeer().getElement().getStyle().get("grid-template-columns"));
    }
}
