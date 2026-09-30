/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's java.awt.BorderLayout
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import java.awt.Dimension;
import java.io.Serializable;
import java.util.Map;

/**
 * Emulator for {@link java.awt.BorderLayout}. Under D_layout_css_on_content,
 * {@link #layoutContainer} writes
 * {@code display: grid; grid-template-columns: auto 1fr auto;
 * grid-template-rows: auto 1fr auto; grid-template-areas: …; gap: …}
 * to the container's peer content element, and
 * {@link #addLayoutComponent(Component, Object)} writes a per-child
 * {@code grid-area} matching the Swing constraint.
 *
 * <p>Hand-written rather than generator-emitted for the same reason
 * FlowLayout is — the generator's root-class detection would attach a
 * {@code peer} field to a LayoutManager.
 *
 * <p><b>LINE_START / LINE_END caveat.</b> Real Swing's BorderLayout
 * flips {@code LINE_START} between west and east based on the
 * container's {@link java.awt.ComponentOrientation}. CSS grid-area
 * names aren't orientation-aware, and re-writing the area name on
 * every orientation change would require tracking orientation at
 * layoutContainer time. We pin the LTR mapping
 * ({@code LINE_START → west}, {@code LINE_END → east}) and document
 * the limitation; R_layouts_close_enough applies.
 */
public class BorderLayout implements LayoutManager2, CssEmittingLayoutManager, Serializable {

    // Swing's String constants — values match JDK BorderLayout verbatim
    // so legacy code that writes "North" directly (rather than the
    // constant) still lands in the right slot.
    public static final String CENTER = "Center";
    public static final String NORTH = "North";
    public static final String SOUTH = "South";
    public static final String EAST = "East";
    public static final String WEST = "West";
    public static final String BEFORE_FIRST_LINE = "First";
    public static final String AFTER_LAST_LINE = "Last";
    public static final String BEFORE_LINE_BEGINS = "Before";
    public static final String AFTER_LINE_ENDS = "After";
    // Aliases share the same underlying String value — these cases
    // collapse in the switch below, so only the "Before..." / "After..."
    // names appear there.
    public static final String PAGE_START = BEFORE_FIRST_LINE;
    public static final String PAGE_END = AFTER_LAST_LINE;
    public static final String LINE_START = BEFORE_LINE_BEGINS;
    public static final String LINE_END = AFTER_LINE_ENDS;

    private int hgap;
    private int vgap;

    // Per-region slots — Swing replaces the occupant when a new
    // component is added to the same region. We mirror that exactly so
    // getLayoutComponent / getConstraints report what the container
    // actually shows. Orientation-aware slots sit alongside the
    // cardinal ones; getLayoutComponent(constraint) resolves by slot,
    // not by region, matching Swing.
    private Component north, south, east, west, center;
    private Component firstLine, lastLine, firstItem, lastItem;

    public BorderLayout() {
        this(0, 0);
    }

    public BorderLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getHgap() {
        return hgap;
    }

    public void setHgap(int hgap) {
        // No auto-revalidate (D_no_silent_improvements) — user code calls
        // container.revalidate() after mutating layout-manager state,
        // matching real Swing.
        this.hgap = hgap;
    }

    public int getVgap() {
        return vgap;
    }

    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // Legacy LayoutManager single-arg form — route through the
        // LayoutManager2 path so the per-child grid-area still lands.
        // Matches Swing's own internal dispatch.
        addLayoutComponent(comp, name);
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        // Null constraint defaults to CENTER — Swing's documented
        // behavior, and what Container.addImpl passes when the caller
        // used the no-constraint add(comp) overload.
        Object key = constraints == null ? CENTER : constraints;
        if (!(key instanceof String s)) {
            // Swing throws IllegalArgumentException; D_never_fail_on_gaps preserves that
            // failure mode since it's a programming error (non-String
            // constraint on BorderLayout).
            throw new IllegalArgumentException(
                    "cannot add to layout: constraint must be a string (or null)");
        }
        String area = switch (s) {
            case CENTER -> { center = comp; yield "center"; }
            case NORTH  -> { north = comp;  yield "north"; }
            case SOUTH  -> { south = comp;  yield "south"; }
            case EAST   -> { east = comp;   yield "east"; }
            case WEST   -> { west = comp;   yield "west"; }
            case BEFORE_FIRST_LINE -> { firstLine = comp; yield "north"; }
            case AFTER_LAST_LINE   -> { lastLine = comp;  yield "south"; }
            case BEFORE_LINE_BEGINS -> { firstItem = comp; yield "west"; }
            case AFTER_LINE_ENDS    -> { lastItem = comp;  yield "east"; }
            default -> throw new IllegalArgumentException(
                    "cannot add to layout: unknown constraint: " + s);
        };
        comp.withPeer(p -> {
            p.getElement().getStyle().set("grid-area", area);
            // Sizing policy (D_layout_owns_child_sizing) alongside grid-area, and
            // eagerly for the same reason: a child added after the container's peer
            // has attached gets no layoutContainer pass until the next validate,
            // and an unruled child renders at its preferred width inside a region
            // it should fill. childCss re-asserts it on every later pass.
            vaadinx.EHelper.applyChildCss(p.getElement(), sizingCss(area));
            // Grid items inherit min-width:auto / min-height:auto by default,
            // which translates to "at least min-content." When the cell holds
            // an intrinsically large subtree (a JScrollPane wrapping many
            // rows, a Grid, a long label stack), that auto-minimum forces
            // the track to expand past the parent's bounded height — defeats
            // the minmax(0, 1fr) on the track. Setting both to 0 lets the
            // child fit its assigned cell, matching Swing's BorderLayout
            // semantics where each region is hard-bounded by its track.
            p.getElement().getStyle().set("min-width", "0");
            p.getElement().getStyle().set("min-height", "0");
        });
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // Scan slots — a single component only occupies one, so the
        // first hit is the only hit. Clear the child's grid-area so if
        // it gets reparented under a non-grid container the leftover
        // style doesn't confuse that layout.
        if      (comp == center)    center = null;
        else if (comp == north)     north = null;
        else if (comp == south)     south = null;
        else if (comp == east)      east = null;
        else if (comp == west)      west = null;
        else if (comp == firstLine) firstLine = null;
        else if (comp == lastLine)  lastLine = null;
        else if (comp == firstItem) firstItem = null;
        else if (comp == lastItem)  lastItem = null;
        comp.withPeer(p -> {
            p.getElement().getStyle().remove("grid-area");
            p.getElement().getStyle().remove("min-width");
            p.getElement().getStyle().remove("min-height");
            // Same for the sizing policy: a component that leaves this layout must
            // stop being ruled by it, or its preferred size stays suppressed under
            // whatever it is reparented into (D_layout_owns_child_sizing).
            p.getElement().getStyle().remove(com.vaadin.swingbridge.surrogates.util.LayoutCss.PREF_W_VAR);
            p.getElement().getStyle().remove(com.vaadin.swingbridge.surrogates.util.LayoutCss.PREF_H_VAR);
        });
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // Dummy — we don't compute
        // real pixel numbers. 1×1 matches FlowLayout's stance; apps
        // that read preferredLayoutSize after install get a non-zero
        // value.
        return new Dimension(1, 1);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(1, 1);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        // Swing's BorderLayout reports Integer.MAX_VALUE — a center
        // region with a flexible child can grow arbitrarily. Same
        // dummy-but-honest answer since we don't enforce max sizes.
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // No cached layout state — the slot fields are authoritative
        // and update on every add/remove. Swing's BorderLayout also
        // does nothing here beyond resetting some cached size hints we
        // don't compute (R_layouts_close_enough).
    }

    /**
     * Which axes this layout sizes for a child in {@code area}, per
     * {@code D_layout_owns_child_sizing}. Straight off the JDK's own
     * {@code layoutContainer} (BorderLayout.java:826-851): NORTH/SOUTH are laid out at
     * {@code (right - left, d.height)} — container width, preferred height — WEST/EAST
     * at {@code (d.width, bottom - top)}, and CENTER at the leftover rect, consulting no
     * preferred size at all.
     */
    private static Map<String, String> sizingCss(String area) {
        return switch (area) {
            case "north", "south" -> com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(true, false);
            case "west", "east"   -> com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(false, true);
            default               -> com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(true, true);
        };
    }

    /**
     * The area {@code comp} occupies, or {@code null} when this layout does not hold it.
     * Slot fields are authoritative (a component occupies exactly one), so the first hit
     * is the only hit — same scan shape as {@link #removeLayoutComponent}.
     */
    private String areaOf(Component comp) {
        if (comp == north || comp == firstLine) return "north";
        if (comp == south || comp == lastLine)  return "south";
        if (comp == west  || comp == firstItem) return "west";
        if (comp == east  || comp == lastItem)  return "east";
        if (comp == center)                     return "center";
        return null;
    }

    @Override
    public Map<String, String> childCss(Container parent, Component child) {
        // Only the sizing policy: grid-area and the min-* pins are known at add time and
        // written there. Re-asserted here so a validate() pass restores the policy if a
        // child moved between regions (remove + re-add under a different constraint).
        String area = areaOf(child);
        return area == null ? null : sizingCss(area);
    }

    @Override
    public Map<String, String> containerCss(Container parent) {
        // Container grid only. Per-child grid-area is written eagerly in
        // addLayoutComponent (the constraint — NORTH/CENTER/… — is known at add
        // time, so there's no whole-container analysis to defer, unlike
        // GridBag/Group). Body in
        // com.vaadin.swingbridge.surrogates.util.LayoutCss.borderLayoutCss so the surrogate-side
        // ContainerMixin shares the same builder.
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.borderLayoutCss(hgap, vgap);
    }

    public Component getLayoutComponent(Object constraints) {
        if (!(constraints instanceof String s)) return null;
        return switch (s) {
            case CENTER -> center;
            case NORTH  -> north;
            case SOUTH  -> south;
            case EAST   -> east;
            case WEST   -> west;
            case BEFORE_FIRST_LINE -> firstLine;
            case AFTER_LAST_LINE   -> lastLine;
            case BEFORE_LINE_BEGINS -> firstItem;
            case AFTER_LINE_ENDS    -> lastItem;
            default -> null;
        };
    }

    public Component getLayoutComponent(Container target, Object constraints) {
        // Swing's container-aware variant resolves PAGE_START etc. to
        // the concrete cardinal region for the container's
        // ComponentOrientation, preferring the orientation-specific
        // slot when set. With our LTR-only mapping (see class javadoc)
        // we can't meaningfully honor orientation; fall back to the
        // slot lookup. Revisited if real apps hit it.
        return getLayoutComponent(constraints);
    }

    public Object getConstraints(Component comp) {
        // Reverse lookup — Swing contract: returns the constraint the
        // component was added with, or null if not in this layout.
        if (comp == null) return null;
        if (comp == center)    return CENTER;
        if (comp == north)     return NORTH;
        if (comp == south)     return SOUTH;
        if (comp == east)      return EAST;
        if (comp == west)      return WEST;
        if (comp == firstLine) return BEFORE_FIRST_LINE;
        if (comp == lastLine)  return AFTER_LAST_LINE;
        if (comp == firstItem) return BEFORE_LINE_BEGINS;
        if (comp == lastItem)  return AFTER_LINE_ENDS;
        return null;
    }

    @Override
    public String toString() {
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + "]";
    }
}
