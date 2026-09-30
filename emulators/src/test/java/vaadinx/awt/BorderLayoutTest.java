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

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BorderLayoutTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    @Test
    @DisplayName("default ctor has no gaps")
    void defaultCtorHasNoGaps() {
        BorderLayout bl = new BorderLayout();
        assertEquals(0, bl.getHgap());
        assertEquals(0, bl.getVgap());
    }

    @Test
    @DisplayName("constants match JDK BorderLayout verbatim")
    void constantsMatchJdkBorderLayoutVerbatim() {
        // Legacy user code writes "North", "Center" directly — the
        // same literals must round-trip through our constraint
        // handling.
        assertEquals("North", BorderLayout.NORTH);
        assertEquals("South", BorderLayout.SOUTH);
        assertEquals("East", BorderLayout.EAST);
        assertEquals("West", BorderLayout.WEST);
        assertEquals("Center", BorderLayout.CENTER);
        assertEquals("First", BorderLayout.BEFORE_FIRST_LINE);
        assertEquals("Last", BorderLayout.AFTER_LAST_LINE);
        assertEquals("Before", BorderLayout.BEFORE_LINE_BEGINS);
        assertEquals("After", BorderLayout.AFTER_LINE_ENDS);
        assertSame(BorderLayout.BEFORE_FIRST_LINE, BorderLayout.PAGE_START);
        assertSame(BorderLayout.AFTER_LAST_LINE, BorderLayout.PAGE_END);
        assertSame(BorderLayout.BEFORE_LINE_BEGINS, BorderLayout.LINE_START);
        assertSame(BorderLayout.AFTER_LINE_ENDS, BorderLayout.LINE_END);
    }

    @Test
    @DisplayName("layoutContainer writes grid CSS to the container peer element")
    void layoutContainerWritesGridCssToTheContainerPeerElement() {
        Container c = new Container();
        new BorderLayout(4, 2).layoutContainer(c);

        var style = c.getPeer().getElement().getStyle();
        assertEquals("grid", style.get("display"));
        assertEquals("auto minmax(0, 1fr) auto", style.get("grid-template-columns"));
        assertEquals("auto minmax(0, 1fr) auto", style.get("grid-template-rows"));
        assertEquals(
                "\"north north north\" \"west center east\" \"south south south\"",
                style.get("grid-template-areas"));
        // gap is row-gap column-gap: vgap first, hgap second.
        assertEquals("2px 4px", style.get("gap"));
    }

    @Test
    @DisplayName("addLayoutComponent writes per-child grid-area")
    void addLayoutComponentWritesPerChildGridArea() {
        BorderLayout bl = new BorderLayout();
        Component north = component();
        Component south = component();
        Component east = component();
        Component west = component();
        Component center = component();

        bl.addLayoutComponent(north, BorderLayout.NORTH);
        bl.addLayoutComponent(south, BorderLayout.SOUTH);
        bl.addLayoutComponent(east, BorderLayout.EAST);
        bl.addLayoutComponent(west, BorderLayout.WEST);
        bl.addLayoutComponent(center, BorderLayout.CENTER);

        assertEquals("north", north.getPeer().getElement().getStyle().get("grid-area"));
        assertEquals("south", south.getPeer().getElement().getStyle().get("grid-area"));
        assertEquals("east", east.getPeer().getElement().getStyle().get("grid-area"));
        assertEquals("west", west.getPeer().getElement().getStyle().get("grid-area"));
        assertEquals("center", center.getPeer().getElement().getStyle().get("grid-area"));
    }

    @Test
    @DisplayName("orientation-aware constraints map to the corresponding cardinal region")
    void orientationAwareConstraintsMapToTheCorrespondingCardinalRegion() {
        // PAGE_START / PAGE_END always map to north / south;
        // LINE_START / LINE_END pin to west / east per the LTR
        // simplification documented in the class javadoc.
        BorderLayout bl = new BorderLayout();
        Component pageStart = component();
        Component lineStart = component();

        bl.addLayoutComponent(pageStart, BorderLayout.PAGE_START);
        bl.addLayoutComponent(lineStart, BorderLayout.LINE_START);

        assertEquals("north", pageStart.getPeer().getElement().getStyle().get("grid-area"));
        assertEquals("west", lineStart.getPeer().getElement().getStyle().get("grid-area"));
    }

    @Test
    @DisplayName("null constraint defaults to CENTER")
    void nullConstraintDefaultsToCenter() {
        // Matches Swing's documented behavior and Container.addImpl's
        // null-constraint path (add(comp) without a region).
        BorderLayout bl = new BorderLayout();
        Component comp = component();
        bl.addLayoutComponent(comp, null);
        assertEquals("center", comp.getPeer().getElement().getStyle().get("grid-area"));
        assertSame(comp, bl.getLayoutComponent(BorderLayout.CENTER));
    }

    @Test
    @DisplayName("non-String constraint throws IllegalArgumentException")
    void nonStringConstraintThrowsIllegalArgumentException() {
        BorderLayout bl = new BorderLayout();
        assertThrows(IllegalArgumentException.class,
                () -> bl.addLayoutComponent(component(), Integer.valueOf(42)));
    }

    @Test
    @DisplayName("unknown String constraint throws IllegalArgumentException")
    void unknownStringConstraintThrowsIllegalArgumentException() {
        BorderLayout bl = new BorderLayout();
        assertThrows(IllegalArgumentException.class,
                () -> bl.addLayoutComponent(component(), "NorthEast"));
    }

    @Test
    @DisplayName("same region accepts only the latest component — previous occupant stays but loses the slot")
    void sameRegionAcceptsOnlyTheLatestComponentPreviousOccupantStaysButLosesTheSlot() {
        // Swing's BorderLayout replaces the per-region slot on add —
        // the old occupant is silently ejected from the layout's slot
        // map (though its container still sees it as a child; user
        // code is expected to call container.remove on the old one).
        // We match.
        BorderLayout bl = new BorderLayout();
        Component first = component();
        Component second = component();
        bl.addLayoutComponent(first, BorderLayout.NORTH);
        bl.addLayoutComponent(second, BorderLayout.NORTH);

        assertSame(second, bl.getLayoutComponent(BorderLayout.NORTH));
        assertNull(bl.getConstraints(first));
        assertEquals("north", second.getPeer().getElement().getStyle().get("grid-area"));
    }

    @Test
    @DisplayName("removeLayoutComponent clears the slot and the child's grid-area")
    void removeLayoutComponentClearsTheSlotAndTheChildsGridArea() {
        BorderLayout bl = new BorderLayout();
        Component comp = component();
        bl.addLayoutComponent(comp, BorderLayout.EAST);

        bl.removeLayoutComponent(comp);

        assertNull(bl.getLayoutComponent(BorderLayout.EAST));
        assertNull(bl.getConstraints(comp));
        assertNull(comp.getPeer().getElement().getStyle().get("grid-area"));
    }

    @Test
    @DisplayName("getConstraints reverse-lookup returns the Swing constraint")
    void getConstraintsReverseLookupReturnsTheSwingConstraint() {
        BorderLayout bl = new BorderLayout();
        Component n = component();
        Component s = component();
        bl.addLayoutComponent(n, BorderLayout.NORTH);
        bl.addLayoutComponent(s, BorderLayout.PAGE_END);
        assertEquals(BorderLayout.NORTH, bl.getConstraints(n));
        assertEquals(BorderLayout.AFTER_LAST_LINE, bl.getConstraints(s));
        assertNull(bl.getConstraints(null));
        assertNull(bl.getConstraints(component()));
    }

    @Test
    @DisplayName("non-Div peer gets no CSS — onUnsupportedPeerShape guard per D_layout_css_on_content")
    void nonDivPeerGetsNoCssUnsupportedPeerShapeGuardPerDLayoutCssOnContent() {
        // LayoutManagers refuse to write to a non-Div peer content
        // element; verify the observable outcome (style stays clean).
        Container c = new Container(new Span()) {
        };

        new BorderLayout().layoutContainer(c);

        assertNull(c.getPeer().getElement().getStyle().get("display"));
        assertNull(c.getPeer().getElement().getStyle().get("grid-template-areas"));
    }

    @Test
    @DisplayName("maximumLayoutSize reports unlimited for the flex-centre region")
    void maximumLayoutSizeReportsUnlimitedForTheFlexCentreRegion() {
        // Swing's BorderLayout returns Integer.MAX_VALUE to signal the
        // center region can grow; we match so code that consults this
        // for layout decisions (rare — mostly Swing internals) sees
        // the expected sentinel.
        Dimension dim = new BorderLayout().maximumLayoutSize(new Container());
        assertEquals(Integer.MAX_VALUE, dim.width);
        assertEquals(Integer.MAX_VALUE, dim.height);
    }
}
