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
import vaadinx.swing.JPanel;

import java.awt.GridBagConstraints;
import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GridBagLayoutTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    /** One CSS property off {@code c}'s peer element — the shape every assertion here needs. */
    private static String css(Component c, String property) {
        return c.getPeer().getElement().getStyle().get(property);
    }

    @Test
    @DisplayName("addLayoutComponent clones constraints — post-add mutation does not bleed")
    void addLayoutComponentClonesConstraintsPostAddMutationDoesNotBleed() {
        // The canonical row-by-row idiom mutates one shared GridBagConstraints
        // between adds. Without clone-on-store the second add would overwrite
        // the first child's stored coords. Mirrors JDK's Hashtable contract.
        GridBagLayout gb = new GridBagLayout();
        Component a = component();
        Component b = component();
        GridBagConstraints g = new GridBagConstraints();

        g.gridx = 0;
        g.gridy = 0;
        gb.addLayoutComponent(a, g);
        g.gridx = 1;
        g.gridy = 0;
        gb.addLayoutComponent(b, g);

        GridBagConstraints ga = gb.getConstraints(a);
        GridBagConstraints gbc = gb.getConstraints(b);
        assertEquals(0, ga.gridx);
        assertEquals(1, gbc.gridx);
        // getConstraints returns clones — caller can't mutate stored state.
        assertNotSame(g, ga);
    }

    @Test
    @DisplayName("non-GridBagConstraints constraint throws IllegalArgumentException (R_match_swing_errors)")
    void nonGridBagConstraintsConstraintThrowsIllegalArgumentException() {
        GridBagLayout gb = new GridBagLayout();
        Component c = component();
        assertThrows(IllegalArgumentException.class, () -> gb.addLayoutComponent(c, "WEST"));
    }

    @Test
    @DisplayName("null constraint falls back to defaultConstraints (JDK behavior)")
    void nullConstraintFallsBackToDefaultConstraints() {
        GridBagLayout gb = new GridBagLayout();
        Component c = component();
        gb.addLayoutComponent(c, null);
        // Default GridBagConstraints has gridx == RELATIVE; cursor resolves
        // to 0 on first add.
        GridBagConstraints stored = gb.getConstraints(c);
        assertEquals(0, stored.gridx);
        assertEquals(0, stored.gridy);
    }

    @Test
    @DisplayName("absolute gridx/gridy writes per-child grid-column and grid-row at layout time")
    void absoluteGridxGridyWritesPerChildGridColumnAndGridRowAtLayoutTime() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component a = component();
        Component b = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        parent.add(a, g);
        g.gridx = 1;
        g.gridy = 0;
        parent.add(b, g);

        gb.layoutContainer(parent);

        // 1-indexed CSS lines: gridx=0 → grid-column: 1 / span 1
        assertEquals("1 / span 1", css(a, "grid-column"));
        assertEquals("1 / span 1", css(a, "grid-row"));
        assertEquals("2 / span 1", css(b, "grid-column"));
    }

    @Test
    @DisplayName("REMAINDER on gridwidth resolves to 'fill to row end' (bucket 1+2)")
    void remainderOnGridwidthResolvesToFillToRowEnd() {
        // Canonical row-stack idiom:
        //   g.gridwidth = 1; add(label, g)
        //   g.gridwidth = REMAINDER; add(field, g)
        // Two rows; field spans from its start to peakCols.
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component label1 = component();
        Component field1 = component();
        Component label2 = component();
        Component field2 = component();

        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 1;
        parent.add(label1, g);
        g.gridx = 1;
        g.gridwidth = GridBagConstraints.REMAINDER;
        parent.add(field1, g);
        g.gridx = 0;
        g.gridy = 1;
        g.gridwidth = 1;
        parent.add(label2, g);
        g.gridx = 1;
        g.gridwidth = GridBagConstraints.REMAINDER;
        parent.add(field2, g);

        gb.layoutContainer(parent);

        // peakCols = 2; field at gridx=1 with REMAINDER spans
        // peakCols - gridx = 1 column.
        assertEquals("2 / span 1", css(field1, "grid-column"));
        assertEquals("2 / span 1", css(field2, "grid-column"));
    }

    @Test
    @DisplayName("RELATIVE gridx places at next column in row (cursor model)")
    void relativeGridxPlacesAtNextColumnInRow() {
        // gridx defaults to RELATIVE on a fresh GridBagConstraints. Three
        // adds in a row should land at columns 0, 1, 2 from the cursor.
        GridBagLayout gb = new GridBagLayout();
        Component a = component();
        Component b = component();
        Component c = component();
        GridBagConstraints g = new GridBagConstraints(); // gridx, gridy both RELATIVE
        g.gridy = 0;
        gb.addLayoutComponent(a, g);
        gb.addLayoutComponent(b, g);
        gb.addLayoutComponent(c, g);

        assertEquals(0, gb.getConstraints(a).gridx);
        assertEquals(1, gb.getConstraints(b).gridx);
        assertEquals(2, gb.getConstraints(c).gridx);
    }

    @Test
    @DisplayName("REMAINDER advances cursor to next row")
    void remainderAdvancesCursorToNextRow() {
        GridBagLayout gb = new GridBagLayout();
        Component label1 = component();
        Component field1 = component();
        Component label2 = component();
        Component field2 = component();
        GridBagConstraints g = new GridBagConstraints();

        g.gridwidth = 1;
        gb.addLayoutComponent(label1, g);
        g.gridwidth = GridBagConstraints.REMAINDER;
        gb.addLayoutComponent(field1, g); // ends row 0; cursor → (0, 1)
        g.gridwidth = 1;
        gb.addLayoutComponent(label2, g);
        g.gridwidth = GridBagConstraints.REMAINDER;
        gb.addLayoutComponent(field2, g); // ends row 1

        assertEquals(0, gb.getConstraints(label1).gridy);
        assertEquals(0, gb.getConstraints(field1).gridy);
        assertEquals(1, gb.getConstraints(label2).gridy);
        assertEquals(1, gb.getConstraints(field2).gridy);
    }

    @Test
    @DisplayName("RELATIVE gridwidth resolves at layout time (next-to-last)")
    void relativeGridwidthResolvesAtLayoutTime() {
        // RELATIVE on gridwidth means "next-to-last in row" — needs peak
        // track count, resolved at layoutContainer.
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);
        Component a = component();
        Component b = component();
        Component c = component();

        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        parent.add(a, g);
        g.gridx = 1;
        g.gridwidth = GridBagConstraints.RELATIVE;
        parent.add(b, g);
        g.gridx = 3;
        g.gridwidth = 1;
        parent.add(c, g);

        gb.layoutContainer(parent);

        // peakCols = 4 (gridx=3 + width=1); RELATIVE for b: peakCols-1-gridx = 4-1-1 = 2.
        assertEquals("2 / span 2", css(b, "grid-column"));
    }

    @Test
    @DisplayName("weightx aggregates per-column max — non-zero maps to fr, zero to auto")
    void weightxAggregatesPerColumnMax() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        // Column 0: label-shape (weight 0), Column 1: field-shape (weight 1).
        Component label = component();
        Component field = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 0.0;
        parent.add(label, g);
        g.gridx = 1;
        g.weightx = 1.0;
        parent.add(field, g);

        gb.layoutContainer(parent);

        String cols = parent.getPeer().getElement().getStyle().get("grid-template-columns");
        // Column 0 zero-weight → auto; column 1 weighted → minmax(0, 1.0fr).
        assertEquals("auto minmax(0, 1.0fr)", cols);
    }

    @Test
    @DisplayName("anchor WEST maps to start/center self pair")
    void anchorWestMapsToStartCenterSelfPair() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        parent.add(c, g);
        gb.layoutContainer(parent);

        assertEquals("start", css(c, "justify-self"));
        assertEquals("center", css(c, "align-self"));
    }

    @Test
    @DisplayName("fill HORIZONTAL overrides anchor on the X axis only")
    void fillHorizontalOverridesAnchorOnTheXAxisOnly() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;     // would write start/center
        g.fill = GridBagConstraints.HORIZONTAL; // overrides justify-self only
        parent.add(c, g);
        gb.layoutContainer(parent);

        assertEquals("stretch", css(c, "justify-self"));
        // align-self preserved from anchor
        assertEquals("center", css(c, "align-self"));
        // fill HORIZONTAL hands the X axis to the layout, so a preferred width
        // (setPreferredSize, or a JTextField's columns width) yields to the grid
        // stretch — justify-self:stretch alone cannot override an explicit width.
        // D_layout_owns_child_sizing: the policy travels in the variable, never in
        // `width`, so a non-filled sibling's own width is never at risk.
        assertEquals("auto", css(c, "--emul-layout-w"));
        assertEquals("initial", css(c, "--emul-layout-h")); // Y axis stays the child's
    }

    @Test
    @DisplayName("fill BOTH stretches both axes")
    void fillBothStretchesBothAxes() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.NORTHEAST; // would be end/start
        g.fill = GridBagConstraints.BOTH;
        parent.add(c, g);
        gb.layoutContainer(parent);

        assertEquals("stretch", css(c, "justify-self"));
        assertEquals("stretch", css(c, "align-self"));
        // fill BOTH hands both axes to the layout, so the child fills its cell
        // whatever preferred size it carries.
        assertEquals("auto", css(c, "--emul-layout-w"));
        assertEquals("auto", css(c, "--emul-layout-h"));
    }

    @Test
    @DisplayName("fill NONE leaves child width and height untouched (keeps columns size)")
    void fillNoneLeavesChildWidthAndHeightUntouched() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        // Simulate a field surrogate that set its own columns width.
        c.getPeer().getElement().getStyle().set("width", "28ch");
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.fill = GridBagConstraints.NONE;
        parent.add(c, g);
        gb.layoutContainer(parent);

        // NONE must not clobber the surrogate's columns width.
        assertEquals("28ch", css(c, "width"));
        // And it says so in the variable rather than by staying silent, so a
        // previous layout's `auto` cannot survive on this child, and an
        // ancestor's cannot be inherited into it (D_layout_sizing_inheritance).
        assertEquals("initial", css(c, "--emul-layout-w"));
        assertEquals("initial", css(c, "--emul-layout-h"));
    }

    @Test
    @DisplayName("insets writes margin in CSS top right bottom left order")
    void insetsWritesMarginInCssTopRightBottomLeftOrder() {
        // JDK Insets(top, left, bottom, right) — note left/right swap
        // vs CSS margin shorthand (top right bottom left).
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.insets = new Insets(/* top */ 3, /* left */ 5, /* bottom */ 7, /* right */ 9);
        parent.add(c, g);
        gb.layoutContainer(parent);

        // CSS shorthand: top right bottom left.
        assertEquals("3px 9px 7px 5px", css(c, "margin"));
    }

    @Test
    @DisplayName("ipadx and ipady write padding (vertical horizontal)")
    void ipadxAndIpadyWritePadding() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.ipadx = 4;
        g.ipady = 2;
        parent.add(c, g);
        gb.layoutContainer(parent);

        assertEquals("2px 4px", css(c, "padding"));
    }

    @Test
    @DisplayName("min-width and min-height are 0 (BorderLayout precedent for shrinkable cells)")
    void minWidthAndMinHeightAre0() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        parent.add(c, g);
        gb.layoutContainer(parent);

        assertEquals("0", css(c, "min-width"));
        assertEquals("0", css(c, "min-height"));
    }

    @Test
    @DisplayName("LINE_START anchor pins to LTR (start/center) per R_layouts_close_enough")
    void lineStartAnchorPinsToLtr() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.LINE_START;
        parent.add(c, g);
        gb.layoutContainer(parent);

        assertEquals("start", css(c, "justify-self"));
        assertEquals("center", css(c, "align-self"));
    }

    @Test
    @DisplayName("removeLayoutComponent clears stored constraints and per-child CSS")
    void removeLayoutComponentClearsStoredConstraintsAndPerChildCss() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        parent.add(c, g);
        gb.layoutContainer(parent);

        // Pre-remove: per-child CSS landed.
        assertEquals("1 / span 1", css(c, "grid-column"));

        gb.removeLayoutComponent(c);

        // Post-remove: stored constraints reset to defaults (RELATIVE);
        // CSS cleared.
        assertEquals(GridBagConstraints.RELATIVE, gb.getConstraints(c).gridx);
        assertNull(css(c, "grid-column"));
        assertNull(css(c, "grid-row"));
        assertNull(css(c, "justify-self"));
        assertNull(css(c, "min-width"));
    }

    @Test
    @DisplayName("setLayout(GridBagLayout) on JPanel + validate writes container CSS")
    void setLayoutGridBagLayoutOnJPanelPlusValidateWritesContainerCss() {
        // End-to-end: install the layout, validate, check the peer's
        // display style. Same shape as the FlowLayout / BorderLayout /
        // BoxLayout sanity tests.
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 1.0;
        panel.add(c, g);
        panel.validate();

        var style = panel.getPeer().getElement().getStyle();
        assertEquals("grid", style.get("display"));
        // Single column with weightx=1.0 → minmax(0, 1.0fr).
        assertEquals("minmax(0, 1.0fr)", style.get("grid-template-columns"));
        assertEquals("auto", style.get("grid-template-rows"));
        // Reset of grid-template-areas (BorderLayout might have written it
        // before a layout swap).
        assertNull(style.get("grid-template-areas"));
    }

    @Test
    @DisplayName("non-Div peer gets no CSS — onUnsupportedPeerShape guard per D_layout_css_on_content")
    void nonDivPeerGetsNoCss() {
        Container c = new Container(new Span()) {
        };
        new GridBagLayout().layoutContainer(c);

        assertNull(c.getPeer().getElement().getStyle().get("display"));
        assertNull(c.getPeer().getElement().getStyle().get("grid-template-columns"));
    }

    @Test
    @DisplayName("maximumLayoutSize is Integer.MAX_VALUE (BorderLayout precedent)")
    void maximumLayoutSizeIsIntegerMaxValue() {
        GridBagLayout gb = new GridBagLayout();
        Container c = new Container();
        java.awt.Dimension max = gb.maximumLayoutSize(c);
        assertEquals(Integer.MAX_VALUE, max.width);
        assertEquals(Integer.MAX_VALUE, max.height);
    }

    @Test
    @DisplayName("preferred and minimum sizes are dummy 1x1")
    void preferredAndMinimumSizesAreDummy1x1() {
        GridBagLayout gb = new GridBagLayout();
        Container c = new Container();
        assertEquals(1, gb.preferredLayoutSize(c).width);
        assertEquals(1, gb.preferredLayoutSize(c).height);
        assertEquals(1, gb.minimumLayoutSize(c).width);
        assertEquals(1, gb.minimumLayoutSize(c).height);
    }

    @Test
    @DisplayName("setConstraints clones and re-applies on next layout")
    void setConstraintsClonesAndReAppliesOnNextLayout() {
        Container parent = new Container();
        GridBagLayout gb = new GridBagLayout();
        parent.setLayout(gb);

        Component c = component();
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        parent.add(c, g);

        // Reconfigure via setConstraints.
        GridBagConstraints g2 = new GridBagConstraints();
        g2.gridx = 2;
        g2.gridy = 1;
        gb.setConstraints(c, g2);
        gb.layoutContainer(parent);

        assertEquals("3 / span 1", css(c, "grid-column"));
        assertEquals("2 / span 1", css(c, "grid-row"));
    }
}
