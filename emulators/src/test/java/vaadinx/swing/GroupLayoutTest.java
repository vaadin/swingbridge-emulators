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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.swing.GroupLayout.Alignment;
import vaadinx.swing.LayoutStyle.ComponentPlacement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupLayoutTest extends AbstractKaribuTest {

    /** A bare Div-peered component — not one of the fill-natured types. */
    private static Component component() {
        return new Component(new Div()) {
        };
    }

    /** The peer element's inline style, which is where every layout assertion below reads. */
    private static Style style(Component c) {
        return c.getPeer().getElement().getStyle();
    }

    /** {@code Short.MAX_VALUE} as an int — GroupLayout's "fully resizable" sentinel. */
    private static final int MAX_VALUE = Short.MAX_VALUE;

    @Test
    @DisplayName("null host throws (R_match_swing_errors)")
    void nullHostThrows() {
        assertThrows(IllegalArgumentException.class, () -> new GroupLayout(null));
    }

    @Test
    @DisplayName("setHorizontalGroup null throws (R_match_swing_errors)")
    void setHorizontalGroupNullThrows() {
        GroupLayout layout = new GroupLayout(new Container());
        assertThrows(IllegalArgumentException.class, () -> layout.setHorizontalGroup(null));
    }

    @Test
    @DisplayName("components are auto-added to host exactly once despite appearing in both trees")
    void componentsAreAutoAddedExactlyOnce() {
        // NetBeans never calls host.add(comp); GroupLayout auto-adds. Each
        // component is referenced in BOTH the horizontal and vertical trees,
        // so the add must be idempotent.
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();
        Component c = component();

        layout.setHorizontalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup().addComponent(a).addComponent(b))
                        .addComponent(c));
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(a).addComponent(b))
                        .addComponent(c));

        assertEquals(3, host.getComponentCount());
    }

    @Test
    @DisplayName("grid reconstruction places components by their two axis bands")
    void gridReconstructionPlacesComponentsByBands() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();
        Component c = component();

        // Horizontal: [a b] side by side; c spans both columns (direct child
        // of the parallel group → fills the band).
        layout.setHorizontalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup().addComponent(a).addComponent(b))
                        .addComponent(c));
        // Vertical: row 0 = {a, b} baseline-aligned; row 1 = c.
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(a).addComponent(b))
                        .addComponent(c));

        layout.layoutContainer(host);

        // Container is a 2×2 grid, all tracks content-sized.
        assertEquals("grid", style(host).get("display"));
        assertEquals("auto auto", style(host).get("grid-template-columns"));
        assertEquals("auto auto", style(host).get("grid-template-rows"));

        // a → col 1, row 1.  b → col 2, row 1.  c → spans cols 1-2, row 2.
        assertEquals("1 / 2", style(a).get("grid-column"));
        assertEquals("1 / 2", style(a).get("grid-row"));
        assertEquals("2 / 3", style(b).get("grid-column"));
        assertEquals("1 / 2", style(b).get("grid-row"));
        assertEquals("1 / 3", style(c).get("grid-column"));
        assertEquals("2 / 3", style(c).get("grid-row"));
    }

    @Test
    @DisplayName("parallel-group alignment maps to align-self and justify-self")
    void parallelGroupAlignmentMapsToSelfProperties() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();

        // Horizontal: a and b overlaid, right-aligned (TRAILING → justify-self end).
        layout.setHorizontalGroup(
                layout.createParallelGroup(Alignment.TRAILING)
                        .addComponent(a)
                        .addComponent(b));
        // Vertical: a,b baseline-aligned in one row (BASELINE → align-self baseline).
        layout.setVerticalGroup(
                layout.createParallelGroup(Alignment.BASELINE)
                        .addComponent(a)
                        .addComponent(b));

        layout.layoutContainer(host);

        assertEquals("end", style(a).get("justify-self"));
        assertEquals("baseline", style(a).get("align-self"));
        assertEquals("end", style(b).get("justify-self"));
        assertEquals("baseline", style(b).get("align-self"));
    }

    @Test
    @DisplayName("per-component alignment override wins over the group alignment")
    void perComponentAlignmentOverrideWins() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();

        // Group is LEADING, but b overrides to TRAILING.
        layout.setHorizontalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addComponent(a)
                        .addComponent(b, Alignment.TRAILING));
        layout.setVerticalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addComponent(a)
                        .addComponent(b));

        layout.layoutContainer(host);

        assertEquals("start", style(a).get("justify-self"));
        assertEquals("end", style(b).get("justify-self"));
    }

    @Test
    @DisplayName("fully-resizable component (Short MAX_VALUE max) makes its track flexible")
    void fullyResizableComponentMakesItsTrackFlexible() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();

        layout.setHorizontalGroup(
                layout.createSequentialGroup()
                        .addComponent(a, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, MAX_VALUE));
        layout.setVerticalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addComponent(a, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, MAX_VALUE));

        layout.layoutContainer(host);

        assertEquals("minmax(0, 1fr)", style(host).get("grid-template-columns"));
        assertEquals("minmax(0, 1fr)", style(host).get("grid-template-rows"));
    }

    @Test
    @DisplayName("fixed-preferred component leaves its track content-sized (auto)")
    void fixedPreferredComponentLeavesItsTrackAuto() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();

        layout.setHorizontalGroup(
                layout.createSequentialGroup().addComponent(
                        a, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE));
        layout.setVerticalGroup(
                layout.createParallelGroup(Alignment.LEADING).addComponent(a));

        layout.layoutContainer(host);

        assertEquals("auto", style(host).get("grid-template-columns"));
    }

    @Test
    @DisplayName("preferred gaps consume no grid track (spacing rides the uniform gap)")
    void preferredGapsConsumeNoGridTrack() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();

        // A gap between a and b must NOT create a third column — only a and b
        // get tracks, so the grid is 2 columns wide.
        layout.setHorizontalGroup(
                layout.createSequentialGroup()
                        .addComponent(a)
                        .addPreferredGap(ComponentPlacement.RELATED)
                        .addComponent(b));
        layout.setVerticalGroup(
                layout.createParallelGroup(Alignment.BASELINE).addComponent(a).addComponent(b));

        layout.layoutContainer(host);

        assertEquals("auto auto", style(host).get("grid-template-columns"));
        assertEquals("6px", style(host).get("gap"));
        assertEquals("1 / 2", style(a).get("grid-column"));
        assertEquals("2 / 3", style(b).get("grid-column"));
    }

    @Test
    @DisplayName("a resizable gap (spring) claims a flexible spacer track and drives the edges apart")
    void aResizableGapClaimsAFlexibleSpacerTrack() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();

        // a --spring-- b: the spring must claim a middle 1fr track so a sits at
        // the leading edge and b is driven to the trailing edge.
        layout.setHorizontalGroup(
                layout.createSequentialGroup()
                        .addComponent(a)
                        .addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, MAX_VALUE)
                        .addComponent(b));
        layout.setVerticalGroup(
                layout.createParallelGroup(Alignment.BASELINE).addComponent(a).addComponent(b));

        layout.layoutContainer(host);

        // 3 columns: a (auto) | spring (1fr) | b (auto).
        assertEquals("auto minmax(0, 1fr) auto", style(host).get("grid-template-columns"));
        assertEquals("1 / 2", style(a).get("grid-column"));
        assertEquals("3 / 4", style(b).get("grid-column"));
    }

    @Test
    @DisplayName("naturally-resizable text field fills its track (1-arg addComponent, flexible + stretch)")
    void naturallyResizableTextFieldFillsItsTrack() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        // A single-line text field is horizontally fill-natured; 1-arg
        // addComponent (DEFAULT max) must defer to that, as JDK GroupLayout does
        // via comp.getMaximumSize().
        JTextField field = new JTextField();

        layout.setHorizontalGroup(layout.createSequentialGroup().addComponent(field));
        layout.setVerticalGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(field));

        layout.layoutContainer(host);

        // Horizontal: flexible track + stretch so the field fills the width.
        assertEquals("minmax(0, 1fr)", style(host).get("grid-template-columns"));
        assertEquals("stretch", style(field).get("justify-self"));
        // Vertical: a single-line field does NOT fill vertically — auto row, no stretch.
        assertEquals("auto", style(host).get("grid-template-rows"));
        assertEquals("start", style(field).get("align-self"));
        // D_layout_owns_child_sizing: the axis that stretches is the layout's, so the
        // field's own columns width yields to the track — stretch alone cannot override
        // an explicit width. The fixed axis stays the child's.
        assertEquals("auto", style(field).get("--emul-layout-w"));
        assertEquals("initial", style(field).get("--emul-layout-h"));
    }

    @Test
    @DisplayName("naturally-fixed component (button-like) stays content-sized and does not stretch")
    void naturallyFixedComponentStaysContentSized() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component(); // a plain component is not a fill-natured type

        layout.setHorizontalGroup(layout.createSequentialGroup().addComponent(a));
        layout.setVerticalGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(a));

        layout.layoutContainer(host);

        assertEquals("auto", style(host).get("grid-template-columns"));
        assertEquals("start", style(a).get("justify-self"));
        // Neither axis stretches, so the preferred size stands on both.
        assertEquals("initial", style(a).get("--emul-layout-w"));
        assertEquals("initial", style(a).get("--emul-layout-h"));
    }

    @Test
    @DisplayName("multi-track resizable spanner fills via stretch without flexing every sub-track")
    void multiTrackResizableSpannerFillsViaStretch() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        Component b = component();
        JSeparator sep = new JSeparator(); // horizontally fill-natured, spans the whole band

        // Parallel band: [a b] sequential (two content columns) overlaid with a
        // full-width separator. The separator spanning both columns must NOT turn
        // them both into 1fr (that would collapse them to one uniform width).
        layout.setHorizontalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup().addComponent(a).addComponent(b))
                        .addComponent(sep));
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(a).addComponent(b))
                        .addComponent(sep));

        layout.layoutContainer(host);

        // Both columns stay content-sized (a and b are fixed plain components).
        assertEquals("auto auto", style(host).get("grid-template-columns"));
        // The separator spans both columns and stretches to fill them.
        assertEquals("1 / 3", style(sep).get("grid-column"));
        assertEquals("stretch", style(sep).get("justify-self"));
    }

    @Test
    @DisplayName("row-stack right-aligns trailing components across independent rows")
    void rowStackRightAlignsTrailingComponents() {
        // The NetBeans form idiom: a parallel group of sequential rows with
        // different column counts. Each row's trailing components must align at
        // the SAME rightmost column (matching GroupLayout's independent per-row
        // full-width fill), while the resizable absorber spans the flexible
        // middle — not forcing the shared columns to equal width.
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        JTextField field = new JTextField();  // row A absorber (resizable)
        Component a1 = component();           // row A trailing (fixed)
        Component a2 = component();
        Component lead = component();         // row B leading (fixed)
        Component b1 = component();           // row B trailing (fixed)

        // Row A: [field fills] [a1] [a2]
        // Row B: [lead] --spring-- [b1]
        layout.setHorizontalGroup(
                layout.createParallelGroup(Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(field).addComponent(a1).addComponent(a2))
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(lead)
                                .addPreferredGap(ComponentPlacement.RELATED, GroupLayout.DEFAULT_SIZE, MAX_VALUE)
                                .addComponent(b1)));
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                                .addComponent(field).addComponent(a1).addComponent(a2))
                        .addGroup(layout.createParallelGroup(Alignment.BASELINE)
                                .addComponent(lead).addComponent(b1)));

        layout.layoutContainer(host);

        // maxLeading=1 (row B's lead), maxTrailing=2 (row A's a1,a2) → 4 columns,
        // the single flexible middle at column index 1.
        assertEquals("auto minmax(0, 1fr) auto auto", style(host).get("grid-template-columns"));
        // The absorber spans from the left through the flexible middle.
        assertEquals("1 / 3", style(field).get("grid-column"));
        assertEquals("stretch", style(field).get("justify-self"));
        // Trailing components of BOTH rows land in the same rightmost column and
        // right-align within it (flush to the edge).
        assertEquals("4 / 5", style(a2).get("grid-column"));
        assertEquals("4 / 5", style(b1).get("grid-column"));
        assertEquals("end", style(a2).get("justify-self"));
        assertEquals("end", style(b1).get("justify-self"));
        // Leading component of the shorter row stays at the left.
        assertEquals("1 / 2", style(lead).get("grid-column"));
    }

    @Test
    @DisplayName("layoutContainer with only one axis set writes no CSS (WARN, no crash)")
    void layoutContainerWithOnlyOneAxisWritesNoCss() {
        Container host = new Container();
        GroupLayout layout = new GroupLayout(host);
        host.setLayout(layout);
        Component a = component();
        layout.setHorizontalGroup(layout.createSequentialGroup().addComponent(a));
        // verticalGroup left null

        layout.layoutContainer(host);

        assertNull(style(host).get("display"));
    }
}
