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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JPanel;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * D_layout_owns_child_sizing across every built-in layout: a child writes its preferred
 * size as {@code width: var(--emul-layout-w, Npx)}, and its parent layout rules on each
 * axis by writing that variable — {@code auto} where the layout sizes the axis itself,
 * {@code initial} where the preferred size stands.
 *
 * <p>Per-layout expectations are the JDK's own {@code layoutContainer} bodies, tabulated
 * in the decision entry. Browserless, so these assert the emitted CSS, not resolved
 * geometry; the pixel proof is the inventory toolbar filling its region in a browser.
 */
class LayoutOwnsChildSizingTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    private static String css(Component c, String prop) {
        return c.getPeer().getElement().getStyle().get(prop);
    }

    private static void assertPolicy(Component c, String widthAxis, String heightAxis) {
        assertEquals(widthAxis, css(c, "--emul-layout-w"), "--emul-layout-w");
        assertEquals(heightAxis, css(c, "--emul-layout-h"), "--emul-layout-h");
    }

    // --- the child's half --------------------------------------------------

    @Test
    @DisplayName("setPreferredSize writes the pref as a variable fallback, not a bare width")
    void setPreferredSizeWritesTheVariableFallback() {
        Component c = component();
        c.setPreferredSize(new Dimension(850, 80));

        assertEquals("var(--emul-layout-w, 850px)", css(c, "width"));
        assertEquals("var(--emul-layout-h, 80px)", css(c, "height"));

        // Clearing the pref clears both declarations, variables or not.
        c.setPreferredSize(null);
        assertNull(css(c, "width"));
        assertNull(css(c, "height"));
    }

    @Test
    @DisplayName("the pref width is also the intrinsic width a content-sized parent sees")
    void prefWidthIsTheIntrinsicInlineSize() {
        // crud's preview panel: setPreferredSize(280, 0) in a BorderLayout CENTER, whose
        // parent sits in an `auto` EAST track. The CENTER child is stretched (`auto`),
        // so only the intrinsic size can carry the 280 up, as preferredLayoutSize does.
        Component c = component();
        c.setPreferredSize(new Dimension(280, 0));
        assertEquals("inline-size", css(c, "contain"));
        assertEquals("280px", css(c, "contain-intrinsic-inline-size"));

        // Height only: no containment. CSS has no block-axis-only size containment.
        c.setPreferredSize(new Dimension(0, 20));
        assertNull(css(c, "contain"));
        assertNull(css(c, "contain-intrinsic-inline-size"));

        c.setPreferredSize(new Dimension(120, 24));
        c.setPreferredSize(null);
        assertNull(css(c, "contain"));
        assertNull(css(c, "contain-intrinsic-inline-size"));
    }

    // --- BorderLayout: the measured case ----------------------------------

    @Test
    @DisplayName("BorderLayout NORTH/SOUTH take the container width and leave the pref height")
    void borderLayoutNorthAndSouthOwnTheWidthOnly() {
        // JDK BorderLayout.java:826-831 — c.setBounds(left, top, right - left, d.height).
        Container parent = new Container();
        parent.setLayout(new BorderLayout());
        Component north = component();
        Component south = component();
        parent.add(north, BorderLayout.NORTH);
        parent.add(south, BorderLayout.SOUTH);

        assertPolicy(north, "auto", "initial");
        assertPolicy(south, "auto", "initial");
    }

    @Test
    @DisplayName("BorderLayout WEST/EAST take the container height and leave the pref width")
    void borderLayoutWestAndEastOwnTheHeightOnly() {
        Container parent = new Container();
        parent.setLayout(new BorderLayout());
        Component west = component();
        Component east = component();
        parent.add(west, BorderLayout.WEST);
        parent.add(east, BorderLayout.EAST);

        assertPolicy(west, "initial", "auto");
        assertPolicy(east, "initial", "auto");
    }

    @Test
    @DisplayName("BorderLayout CENTER consults no preferred size at all")
    void borderLayoutCenterOwnsBothAxes() {
        Container parent = new Container();
        parent.setLayout(new BorderLayout());
        Component center = component();
        parent.add(center, BorderLayout.CENTER);

        assertPolicy(center, "auto", "auto");
    }

    @Test
    @DisplayName("the orientation-relative constraints get their absolute region's policy")
    void borderLayoutRelativeConstraintsFollowTheirRegion() {
        Container parent = new Container();
        parent.setLayout(new BorderLayout());
        Component pageStart = component();
        Component lineStart = component();
        parent.add(pageStart, BorderLayout.PAGE_START);
        parent.add(lineStart, BorderLayout.LINE_START);

        assertPolicy(pageStart, "auto", "initial");   // north
        assertPolicy(lineStart, "initial", "auto");   // west
    }

    @Test
    @DisplayName("a NORTH child's preferred width yields to the region, whichever order it was set in")
    void borderLayoutNorthPolicyIsOrderIndependent() {
        // The inventory toolbar: setPreferredSize(new Dimension(getWidth(), 80)) on a
        // BorderLayout.NORTH panel rendered 854px wide inside a 1400px region. Both
        // orders matter: the app happens to size before adding, and the eager
        // addLayoutComponent write covers that, while a later setPreferredSize must not
        // be able to take the axis back — which is what the var() indirection buys.
        Container parent = new Container();
        parent.setLayout(new BorderLayout());

        Component sizedFirst = component();
        sizedFirst.setPreferredSize(new Dimension(850, 80));
        parent.add(sizedFirst, BorderLayout.NORTH);

        Component sizedAfter = component();
        parent.add(sizedAfter, BorderLayout.SOUTH);
        sizedAfter.setPreferredSize(new Dimension(850, 80));

        for (Component c : new Component[] {sizedFirst, sizedAfter}) {
            assertPolicy(c, "auto", "initial");
            // The pref survives as the fallback — it is the height that still uses it.
            assertEquals("var(--emul-layout-w, 850px)", css(c, "width"));
            assertEquals("var(--emul-layout-h, 80px)", css(c, "height"));
        }
    }

    @Test
    @DisplayName("a re-added child gets its new region's policy on the next layout pass")
    void borderLayoutChildMovedBetweenRegionsIsRepoliced() {
        Container parent = new Container();
        BorderLayout bl = new BorderLayout();
        parent.setLayout(bl);
        Component c = component();
        parent.add(c, BorderLayout.NORTH);
        assertPolicy(c, "auto", "initial");

        parent.remove(c);
        // Leaving the layout drops the policy, so the pref is the child's own again
        // wherever it lands next.
        assertPolicy(c, null, null);

        parent.add(c, BorderLayout.WEST);
        bl.layoutContainer(parent);
        assertPolicy(c, "initial", "auto");
    }

    // --- the other built-ins ----------------------------------------------

    @Test
    @DisplayName("FlowLayout honours the preferred size on both axes")
    void flowLayoutLeavesBothAxesToTheChild() {
        // JDK FlowLayout sizes every child with c.setSize(c.getPreferredSize()).
        Container parent = new Container();
        FlowLayout fl = new FlowLayout();
        parent.setLayout(fl);
        Component c = component();
        parent.add(c);
        fl.layoutContainer(parent);

        assertPolicy(c, "initial", "initial");
    }

    @Test
    @DisplayName("GridLayout owns both axes — every cell is w/ncols by h/nrows")
    void gridLayoutOwnsBothAxes() {
        Container parent = new Container();
        GridLayout gl = new GridLayout(2, 2);
        parent.setLayout(gl);
        Component c = component();
        c.setPreferredSize(new Dimension(120, 40));
        parent.add(c);
        gl.layoutContainer(parent);

        assertPolicy(c, "auto", "auto");
    }

    @Test
    @DisplayName("BoxLayout tiles the major axis at the pref and stretches the cross axis")
    void boxLayoutOwnsTheCrossAxisOnly() {
        Container column = new JPanel();
        BoxLayout vertical = new BoxLayout(column, BoxLayout.Y_AXIS);
        column.setLayout(vertical);
        Component inColumn = component();
        column.add(inColumn);
        vertical.layoutContainer(column);
        // A row panel in a Y_AXIS box fills the container width (JDK stretches the cross
        // axis to min(alloc, getMaximumSize()), and an unset max is Short.MAX_VALUE).
        assertPolicy(inColumn, "auto", "initial");

        Container row = new JPanel();
        BoxLayout horizontal = new BoxLayout(row, BoxLayout.X_AXIS);
        row.setLayout(horizontal);
        Component inRow = component();
        row.add(inRow);
        horizontal.layoutContainer(row);
        assertPolicy(inRow, "initial", "auto");
    }

    // --- no manager, and a manager we cannot read -------------------------

    @Test
    @DisplayName("a container with no layout manager leaves both axes to the child")
    void noLayoutManagerLeavesBothAxesToTheChild() {
        Container parent = new Container();
        parent.setLayout(null);
        Component c = component();
        parent.add(c);
        parent.doLayout();

        assertPolicy(c, "initial", "initial");
    }

    @Test
    @DisplayName("swapping to no manager releases the previous manager's axis policy")
    void swappingToNoManagerReleasesThePreviousPolicy() {
        // The absolute-layout panel inside a BorderLayout region is the case
        // D_layout_sizing_inheritance is about: without an explicit write here the
        // child would keep — or inherit — an `auto` that erases its preferred width.
        Container parent = new Container();
        parent.setLayout(new BorderLayout());
        Component c = component();
        parent.add(c, BorderLayout.CENTER);
        assertPolicy(c, "auto", "auto");

        parent.setLayout(null);
        parent.doLayout();
        assertPolicy(c, "initial", "initial");
    }

    @Test
    @DisplayName("an untranslatable custom manager leaves both axes to the child")
    void customManagerFallbackLeavesBothAxesToTheChild() {
        // The vertical fallback cannot know the real manager's per-axis opinion, so it
        // does not guess (Option A, M1D_custom_layoutmanager).
        Container parent = new Container();
        parent.setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component comp) {}
            @Override public void removeLayoutComponent(Component comp) {}
            @Override public Dimension preferredLayoutSize(Container p) { return new Dimension(1, 1); }
            @Override public Dimension minimumLayoutSize(Container p) { return new Dimension(1, 1); }
            @Override public void layoutContainer(Container p) {}
        });
        Component c = component();
        parent.add(c);
        parent.doLayout();

        assertPolicy(c, "initial", "initial");
    }
}
