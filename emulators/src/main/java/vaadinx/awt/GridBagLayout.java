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
 * This file is derived from OpenJDK's java.awt.GridBagLayout
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import com.vaadin.flow.dom.Style;

import java.awt.Dimension;
import java.awt.Insets;
import java.io.Serializable;
import java.util.Map;

/**
 * Emulator for {@link java.awt.GridBagLayout}. Under D_layout_css_on_content,
 * {@link #layoutContainer} writes
 * {@code display: grid; grid-template-columns: …; grid-template-rows: …}
 * to the container's peer content element via
 * {@link vaadinx.EHelper#applyContainerCss}, plus per-child
 * {@code grid-row}/{@code grid-column}/{@code justify-self}/
 * {@code align-self}/{@code margin}/{@code padding} writes.
 *
 * <p>Hand-written rather than generator-emitted: same as
 * {@link FlowLayout} / {@link BorderLayout} / {@code BoxLayout} — the
 * generator's root-class detection assumes Component-hierarchy ancestry
 * and would emit a {@code peer} field, which a LayoutManager doesn't
 * have.
 *
 * <p><b>GridBagConstraints stays JDK</b> per D_whitelist_porting's data-types-stay-JDK
 * rule. Constraints are pure data carriers — no Component reference, no
 * methods that touch the Component hierarchy. User code's
 * {@code GridBagConstraints.WEST}/{@code HORIZONTAL}/etc. constants land
 * from {@link java.awt.GridBagConstraints} unchanged across the import
 * swap. {@code Insets} likewise stays JDK.
 *
 * <p><b>CSS Grid mapping.</b> Per-track sizing follows JDK GridBagLayout's
 * "any non-zero {@code weightx} in a column makes that column absorb
 * extra space" rule: aggregate per-column max {@code weightx} and per-row
 * max {@code weighty} across all children, then map non-zero weights to
 * {@code minmax(0, Nfr)} and zero weights to {@code auto}. The
 * {@code minmax(0, Nfr)} (vs. plain {@code Nfr}) lets weighted tracks
 * shrink below their content's intrinsic size when the grid container is
 * height/width-bounded — same shape as {@link BorderLayout}'s CENTER track.
 *
 * <p><b>RELATIVE / REMAINDER resolution.</b> Constraints are cloned at
 * {@link #addLayoutComponent} time (matching JDK's {@code Hashtable<Component,
 * GridBagConstraints>} semantics — user code that mutates the original
 * {@code GridBagConstraints} after each {@code add()} relies on this), and
 * {@code RELATIVE}/{@code REMAINDER} for {@code gridx}/{@code gridy}
 * resolves immediately against the running cursor. {@code RELATIVE} for
 * {@code gridwidth}/{@code gridheight} resolves at {@link #layoutContainer}
 * time once the peak track count is known. Bucket 3 of D_gridbaglayout (multi-row
 * REMAINDER cursor reset, RELATIVE chains beyond the canonical idioms) is
 * deferred — drop-and-WARN on the configurations the cursor model can't
 * resolve.
 *
 * <p><b>Anchor / fill mapping.</b> All 13 absolute anchor constants
 * (CENTER + 8 cardinals/corners + 4 baseline variants) map to
 * ({@code justify-self}, {@code align-self}) pairs. Orientation-relative
 * anchors (LINE_START / PAGE_START / FIRST_LINE_START / etc.) pin to the
 * LTR mapping per R_layouts_close_enough — same caveat as BorderLayout's LINE_START/LINE_END
 * and BoxLayout's LINE_AXIS/PAGE_AXIS. Baseline anchors collapse to
 * top/center/bottom — CSS has no baseline-grid concept. {@code fill}
 * (NONE/HORIZONTAL/VERTICAL/BOTH) overrides {@code anchor} on whichever
 * axis it stretches.
 *
 * <p><b>Insets and ipadx/ipady.</b> {@code insets} writes per-child
 * {@code margin} (external spacing — matches JDK's "space around the
 * component within its cell"). {@code ipadx}/{@code ipady} writes per-child
 * {@code padding} (internal padding — matches JDK's "extra width/height
 * added to the component's preferred size before layout"). Combined
 * effect approximates JDK's pixel arithmetic to within R_layouts_close_enough.
 */
public class GridBagLayout implements LayoutManager2, CssEmittingLayoutManager, Serializable {

    // The protected constants a GridBagLayout subclass names when it calls the
    // JDK's own getLayoutInfo / getMinSize protected API. Nothing here reads them
    // — this layout emits CSS Grid rather than running a pixel engine (R_layouts_close_enough) — but
    // a subclass that references them has to compile (D_missing_constants).
    // MAXGRIDSIZE is vestigial in the JDK too — its own javadoc says the current
    // implementation imposes no limit on grid size. Not @Deprecated there, so not
    // here either.
    protected static final int MAXGRIDSIZE = 512;
    protected static final int MINSIZE = 1;
    protected static final int PREFERREDSIZE = 2;

    // defaultConstraints is protected in the JDK, not public — matched here, because
    // widening is as much a divergence as narrowing and this was the emulator surface's
    // only one (D_instance_field_surface). Carried because addLayoutComponent(comp, null)
    // in the JDK falls back to it.
    protected java.awt.GridBagConstraints defaultConstraints = new java.awt.GridBagConstraints();

    // The JDK's four public track-sizing hints: per-column/row pixel minima and extra-space
    // weights, which its pixel engine folds into getLayoutInfo. Nothing reads them here —
    // layoutContainer emits CSS Grid instead — so this is R_decline_effect_only in its plainest
    // form: keep the state, decline the effect, and the effect declined is one
    // R_layouts_close_enough already defers. They are declared because a GUI builder writes
    // them unconditionally (four assignments per Matisse/WindowBuilder form), so their absence
    // was four compile errors in the migrator's tree per form. Assigning them changes nothing
    // about the rendered layout; the CSS mapping derives its tracks from the children's
    // gridx/gridy/weightx/weighty as documented above.
    public int[] columnWidths;
    public int[] rowHeights;
    public double[] columnWeights;
    public double[] rowWeights;

    // Per-component constraint storage — keyed by Component, mirroring
    // JDK's comptable. Cloned on insert so post-add mutation of the
    // user's GridBagConstraints (the canonical row-by-row pattern in real
    // Swing forms) doesn't bleed back into stored state.
    private final Map<Component, java.awt.GridBagConstraints> constraintsByComp = new java.util.LinkedHashMap<>();

    // Cursor for RELATIVE/REMAINDER on gridx/gridy. Mirrors JDK's
    // documented "next position" tracking for the row-by-row idiom:
    //   g.gridwidth = REMAINDER; add(field, g);  // ends row, advances cursor
    // Bucket 1 of D_gridbaglayout — covers the canonical row-stack pattern most
    // GridBag forms in the wild use.
    private int cursorX = 0;
    private int cursorY = 0;

    public GridBagLayout() {
    }

    public java.awt.GridBagConstraints getConstraints(Component comp) {
        // JDK returns a clone so the caller can't mutate stored state
        // through it. Match per R_swing_is_truth.
        java.awt.GridBagConstraints g = constraintsByComp.get(comp);
        return g == null ? (java.awt.GridBagConstraints) defaultConstraints.clone()
                         : (java.awt.GridBagConstraints) g.clone();
    }

    public void setConstraints(Component comp, java.awt.GridBagConstraints constraints) {
        // JDK clones too — keeps the user's struct independent of stored
        // state. Constraints aren't re-resolved against the cursor here:
        // setConstraints is the post-add reconfiguration path, by which
        // point absolute coords are the user's responsibility (RELATIVE
        // semantics tie to add-order). User code calls revalidate() after.
        constraintsByComp.put(comp, (java.awt.GridBagConstraints) constraints.clone());
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // Plain LayoutManager form — JDK's GridBagLayout doesn't accept
        // this path (constraints must be GridBagConstraints), but the
        // generator-emitted Container.addImpl funnels through the
        // LayoutManager2 form when constraints are non-null. This branch
        // catches the no-constraints add(comp) overload.
        addLayoutComponent(comp, null);
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        java.awt.GridBagConstraints g;
        if (constraints == null) {
            // JDK falls back to defaultConstraints (cloned) when the
            // caller used add(comp) without a constraint object.
            g = (java.awt.GridBagConstraints) defaultConstraints.clone();
        } else if (constraints instanceof java.awt.GridBagConstraints in) {
            // Clone-on-add: the user's typical pattern is `g.gridx = 0;
            // form.add(label, g); g.gridx = 1; form.add(field, g)` —
            // mutating one shared GridBagConstraints between adds.
            // Without cloning, both children would share the latest
            // mutation.
            g = (java.awt.GridBagConstraints) in.clone();
        } else {
            // JDK throws IllegalArgumentException("cannot add to layout:
            // constraint must be a GridBagConstraint"). R_match_swing_errors — preserve the
            // failure mode since it's a programming error.
            throw new IllegalArgumentException(
                    "cannot add to layout: constraints must be a GridBagConstraint");
        }

        // Resolve RELATIVE/REMAINDER on gridx/gridy against the running
        // cursor. RELATIVE means "next slot in flow direction"; REMAINDER
        // on the position fields isn't a JDK feature (it's only valid on
        // gridwidth/gridheight), but we handle it as an alias for
        // RELATIVE since JDK silently treats it the same way.
        if (g.gridx == java.awt.GridBagConstraints.RELATIVE) {
            g.gridx = cursorX;
        }
        if (g.gridy == java.awt.GridBagConstraints.RELATIVE) {
            g.gridy = cursorY;
        }

        // Cursor advance. REMAINDER on gridwidth marks "this child is the
        // last in its row" — the cursor wraps to (0, gridy + 1) so the
        // next add lands at the start of the following row. REMAINDER on
        // gridheight is symmetric for column-major flow.
        boolean rowEnd = g.gridwidth == java.awt.GridBagConstraints.REMAINDER;
        boolean colEnd = g.gridheight == java.awt.GridBagConstraints.REMAINDER;
        if (rowEnd) {
            cursorX = 0;
            // gridheight may also be REMAINDER (rare); use 1 as a safe
            // default — bucket 3 (multi-row REMAINDER cursor reset) is
            // deferred per D_gridbaglayout.
            int h = colEnd ? 1 : Math.max(1, g.gridheight);
            cursorY = g.gridy + h;
        } else if (colEnd) {
            // Column-major REMAINDER: advance cursorY past this child's
            // span; cursorX stays put. Less common than row-end in
            // practice — JDK forms overwhelmingly use row-flow.
            cursorY = 0;
            cursorX = g.gridx + Math.max(1, g.gridwidth);
        } else {
            // Plain advance: next column in the current row. The width
            // may itself be RELATIVE (resolved later); treat as 1 for
            // cursor purposes — bucket 2's "next-to-last" semantics
            // settle in layoutContainer once peak tracks are known.
            int w = g.gridwidth == java.awt.GridBagConstraints.RELATIVE
                    ? 1 : Math.max(1, g.gridwidth);
            cursorX = g.gridx + w;
        }

        constraintsByComp.put(comp, g);
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // Per JDK: drop the constraint mapping. Cursor isn't rewound —
        // the cursor model is add-order-only, and JDK doesn't unwind it
        // either. User code that removes mid-stream is on its own to
        // re-establish positions if it later re-adds.
        java.awt.GridBagConstraints removed = constraintsByComp.remove(comp);
        if (removed != null) {
            // Clear the per-child CSS so the component's element doesn't
            // carry stale grid-area/justify-self/etc. styles into a
            // subsequent reparent under a non-grid container.
            comp.withPeer(p -> {
                Style style = p.getElement().getStyle();
                style.remove("grid-column");
                style.remove("grid-row");
                style.remove("justify-self");
                style.remove("align-self");
                style.remove("margin");
                style.remove("padding");
                style.remove("min-width");
                style.remove("min-height");
                // The sizing policy goes too, so a component that leaves this layout
                // stops being ruled by its `fill` (D_layout_owns_child_sizing). Safe to
                // clear where clearing `width`/`height` never was: those are co-owned
                // by the field surrogate (setColumns writes an inline width), the
                // variables are the layout's alone.
                style.remove(com.vaadin.swingbridge.surrogates.util.LayoutCss.PREF_W_VAR);
                style.remove(com.vaadin.swingbridge.surrogates.util.LayoutCss.PREF_H_VAR);
            });
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // R_layouts_close_enough / FlowLayout precedent — dummy 1×1 keeps user code that
        // reads getPreferredSize after install from getting a zero some
        // legacy code treats as "not laid out yet."
        return new Dimension(1, 1);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(1, 1);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        // BorderLayout / BoxLayout precedent — Integer.MAX_VALUE allows
        // weighted tracks to grow arbitrarily.
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        // Same dummy answer as the other built-in layouts. JDK averages
        // child alignments — too tangled for R_layouts_close_enough, and Component.alignmentX
        // is always 0.5f today (JComponent.setAlignmentX is onNoop).
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // No cached layout state — the constraintsByComp map is
        // authoritative and updates on every add/remove. JDK clears
        // pixel-arithmetic caches we don't compute (R_layouts_close_enough).
    }

    @Override
    public Map<String, String> containerCss(Container parent) {
        // Two-pass whole-container analysis: (1) compute peak track count,
        // resolving RELATIVE/REMAINDER width/height in place; (2) aggregate
        // per-track weights → the grid template. The resolved constraints left
        // in constraintsByComp are the stash childCss reads back per the
        // CssEmittingLayoutManager call-order contract (containerCss before
        // childCss), so no separate per-child cache is needed.

        // Pass 1: peak grid dims. Walk every child's constraints to find
        // the maximum (gridx + gridwidth) and (gridy + gridheight). Skip
        // constraints whose gridx/gridy is still RELATIVE — those didn't
        // resolve at addLayoutComponent (the cursor model couldn't pin
        // them), so they fall under bucket 3's drop-and-WARN.
        int peakCols = 1;
        int peakRows = 1;
        for (Map.Entry<Component, java.awt.GridBagConstraints> e : constraintsByComp.entrySet()) {
            java.awt.GridBagConstraints g = e.getValue();
            if (g.gridx == java.awt.GridBagConstraints.RELATIVE
                    || g.gridy == java.awt.GridBagConstraints.RELATIVE) {
                continue;
            }
            int w = g.gridwidth == java.awt.GridBagConstraints.RELATIVE
                    || g.gridwidth == java.awt.GridBagConstraints.REMAINDER
                    ? 1 : Math.max(1, g.gridwidth);
            int h = g.gridheight == java.awt.GridBagConstraints.RELATIVE
                    || g.gridheight == java.awt.GridBagConstraints.REMAINDER
                    ? 1 : Math.max(1, g.gridheight);
            peakCols = Math.max(peakCols, g.gridx + w);
            peakRows = Math.max(peakRows, g.gridy + h);
        }

        // Now resolve RELATIVE width/height (bucket 2): "next-to-last in
        // row/column" — pin to peakCols-1 / peakRows-1 minus the start.
        // REMAINDER width/height resolves to "fill to the row/column end"
        // — peakCols / peakRows minus the start. Mutate the stored
        // constraint in place; subsequent layoutContainer calls are
        // idempotent because the value is already an int by then.
        for (Map.Entry<Component, java.awt.GridBagConstraints> e : constraintsByComp.entrySet()) {
            java.awt.GridBagConstraints g = e.getValue();
            if (g.gridx == java.awt.GridBagConstraints.RELATIVE
                    || g.gridy == java.awt.GridBagConstraints.RELATIVE) {
                vaadinx.EHelper.onUnimplemented("vaadinx.awt.GridBagLayout",
                        "layoutContainer(unresolved RELATIVE position)",
                        "gridx=" + g.gridx, "gridy=" + g.gridy);
                continue;
            }
            if (g.gridwidth == java.awt.GridBagConstraints.REMAINDER) {
                g.gridwidth = Math.max(1, peakCols - g.gridx);
            } else if (g.gridwidth == java.awt.GridBagConstraints.RELATIVE) {
                g.gridwidth = Math.max(1, peakCols - 1 - g.gridx);
            }
            if (g.gridheight == java.awt.GridBagConstraints.REMAINDER) {
                g.gridheight = Math.max(1, peakRows - g.gridy);
            } else if (g.gridheight == java.awt.GridBagConstraints.RELATIVE) {
                g.gridheight = Math.max(1, peakRows - 1 - g.gridy);
            }
        }

        // Pass 2: aggregate per-track weights. JDK's rule: a column's
        // size hint is the max weightx among its members, same for rows.
        // We use the same rule — write Nfr for non-zero weights, auto
        // otherwise.
        double[] colWeights = new double[peakCols];
        double[] rowWeights = new double[peakRows];
        for (java.awt.GridBagConstraints g : constraintsByComp.values()) {
            if (g.gridx == java.awt.GridBagConstraints.RELATIVE
                    || g.gridy == java.awt.GridBagConstraints.RELATIVE) {
                continue; // bucket 3 — already WARN-logged above
            }
            // Spread the weight across spanned tracks per JDK: each
            // track this child spans takes max(currentTrackWeight,
            // weight / span). Approximation that matches JDK's
            // behavior for the common case (single-cell components
            // carry the full weight; multi-cell spans share).
            int colSpan = Math.max(1, g.gridwidth);
            int rowSpan = Math.max(1, g.gridheight);
            double colShare = g.weightx / colSpan;
            double rowShare = g.weighty / rowSpan;
            for (int c = g.gridx; c < g.gridx + colSpan && c < peakCols; c++) {
                if (colShare > colWeights[c]) colWeights[c] = colShare;
            }
            for (int r = g.gridy; r < g.gridy + rowSpan && r < peakRows; r++) {
                if (rowShare > rowWeights[r]) rowWeights[r] = rowShare;
            }
        }

        // Container CSS. Per-child CSS is emitted by childCss (below), which the
        // framework calls after this method for each child.
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.gridBagLayoutCss(colWeights, rowWeights);
    }

    @Override
    public Map<String, String> childCss(Container parent, Component child) {
        java.awt.GridBagConstraints g = constraintsByComp.get(child);
        if (g == null) {
            // Not tracked by this layout (shouldn't happen — addLayoutComponent
            // fires for every child). Leave it untouched.
            return null;
        }
        if (g.gridx == java.awt.GridBagConstraints.RELATIVE
                || g.gridy == java.awt.GridBagConstraints.RELATIVE) {
            // Unresolved RELATIVE position (bucket 3) — already WARN-logged in
            // containerCss; skip the per-child write.
            return null;
        }
        return buildChildCss(g);
    }

    private static Map<String, String> buildChildCss(java.awt.GridBagConstraints g) {
        Map<String, String> css = new java.util.LinkedHashMap<>();

        // CSS Grid lines are 1-indexed; gridx/gridy are 0-indexed.
        int colStart = g.gridx + 1;
        int rowStart = g.gridy + 1;
        int colSpan = Math.max(1, g.gridwidth);
        int rowSpan = Math.max(1, g.gridheight);
        css.put("grid-column", colStart + " / span " + colSpan);
        css.put("grid-row", rowStart + " / span " + rowSpan);

        // anchor → (justify-self, align-self). fill overrides on the
        // relevant axis (HORIZONTAL → justify-self: stretch;
        // VERTICAL → align-self: stretch; BOTH → both stretch).
        String[] anchorPair = anchorToSelfPair(g.anchor);
        String justifySelf = anchorPair[0];
        String alignSelf = anchorPair[1];
        switch (g.fill) {
            case java.awt.GridBagConstraints.HORIZONTAL -> justifySelf = "stretch";
            case java.awt.GridBagConstraints.VERTICAL -> alignSelf = "stretch";
            case java.awt.GridBagConstraints.BOTH -> {
                justifySelf = "stretch";
                alignSelf = "stretch";
            }
            case java.awt.GridBagConstraints.NONE -> { /* keep anchor mapping */ }
            default -> vaadinx.EHelper.onUnimplemented("vaadinx.awt.GridBagLayout",
                    "childCss(unknown fill)", g.fill);
        }
        css.put("justify-self", justifySelf);
        css.put("align-self", alignSelf);

        // Swing's `fill` overrides the child's *preferred* size on the
        // stretched axis — a JTextField's columns width is a preferred
        // hint, and GridBagLayout stretches past it when fill demands.
        // Both kinds of preferred width reach CSS through the
        // D_layout_owns_child_sizing variable (setPreferredSize's own, and
        // SJTextField.applyColumnsToPeer's columns width), so claiming the axis
        // is one variable write: `justify-self: stretch` alone cannot do it,
        // since stretch only grows auto-sized items and an explicit width wins.
        //
        // Writing the variable rather than `width: auto` is what keeps a
        // *non*-filled sibling's columns width intact — the axis policy no
        // longer travels in the same property as the value, so this layout
        // never has to weigh clobbering a surrogate's own inline width.
        boolean fillH = g.fill == java.awt.GridBagConstraints.HORIZONTAL
                || g.fill == java.awt.GridBagConstraints.BOTH;
        boolean fillV = g.fill == java.awt.GridBagConstraints.VERTICAL
                || g.fill == java.awt.GridBagConstraints.BOTH;
        css.putAll(com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(fillH, fillV));

        // Insets → margin. JDK Insets is the JDK-reused data type. null value
        // removes any stale margin from a prior layout pass with insets set.
        Insets in = g.insets;
        if (in != null && (in.top != 0 || in.right != 0 || in.bottom != 0 || in.left != 0)) {
            css.put("margin",
                    in.top + "px " + in.right + "px " + in.bottom + "px " + in.left + "px");
        } else {
            css.put("margin", null);
        }

        // ipadx/ipady → padding. JDK's exact semantics: ipadx is added
        // to the component's preferred width *both sides* (so total
        // extra width = 2*ipadx). We mirror by writing
        // padding: ipady ipadx (vertical horizontal) — close-enough
        // per R_layouts_close_enough; the exact "preferred-size adjustment before grid
        // assignment" doesn't translate to the CSS Grid model.
        if (g.ipadx != 0 || g.ipady != 0) {
            css.put("padding", g.ipady + "px " + g.ipadx + "px");
        } else {
            css.put("padding", null);
        }

        // Grid items inherit min-width:auto / min-height:auto, which
        // forces tracks to expand past the container's bounded size
        // when the cell holds an intrinsically large subtree
        // (JScrollPane / JTable / long label stack). BorderLayout
        // precedent — set both to 0 so the cell respects its track.
        css.put("min-width", "0");
        css.put("min-height", "0");
        return css;
    }

    /**
     * Map a JDK {@link java.awt.GridBagConstraints} anchor constant to a
     * {@code (justify-self, align-self)} pair. All 13 absolute anchors
     * (CENTER, NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST,
     * NORTHWEST) plus orientation-relative anchors (PAGE_START / LINE_END /
     * FIRST_LINE_START / etc.) are covered; orientation-relatives pin LTR
     * per R_layouts_close_enough (BorderLayout LINE_START caveat shape). Baseline anchors
     * (BASELINE / ABOVE_BASELINE / BELOW_BASELINE + leading/trailing
     * variants) collapse to top/center/bottom — CSS has no baseline-grid
     * concept, R_layouts_close_enough close-enough.
     */
    private static String[] anchorToSelfPair(int anchor) {
        return switch (anchor) {
            case java.awt.GridBagConstraints.CENTER          -> new String[] {"center", "center"};
            case java.awt.GridBagConstraints.NORTH           -> new String[] {"center", "start"};
            case java.awt.GridBagConstraints.NORTHEAST       -> new String[] {"end",    "start"};
            case java.awt.GridBagConstraints.EAST            -> new String[] {"end",    "center"};
            case java.awt.GridBagConstraints.SOUTHEAST       -> new String[] {"end",    "end"};
            case java.awt.GridBagConstraints.SOUTH           -> new String[] {"center", "end"};
            case java.awt.GridBagConstraints.SOUTHWEST       -> new String[] {"start",  "end"};
            case java.awt.GridBagConstraints.WEST            -> new String[] {"start",  "center"};
            case java.awt.GridBagConstraints.NORTHWEST       -> new String[] {"start",  "start"};
            // Orientation-relatives — LTR pinning. PAGE_START/END are
            // vertical (column-major), LINE_START/END horizontal.
            case java.awt.GridBagConstraints.PAGE_START      -> new String[] {"center", "start"};
            case java.awt.GridBagConstraints.PAGE_END        -> new String[] {"center", "end"};
            case java.awt.GridBagConstraints.LINE_START      -> new String[] {"start",  "center"};
            case java.awt.GridBagConstraints.LINE_END        -> new String[] {"end",    "center"};
            case java.awt.GridBagConstraints.FIRST_LINE_START -> new String[] {"start", "start"};
            case java.awt.GridBagConstraints.FIRST_LINE_END   -> new String[] {"end",   "start"};
            case java.awt.GridBagConstraints.LAST_LINE_START  -> new String[] {"start", "end"};
            case java.awt.GridBagConstraints.LAST_LINE_END    -> new String[] {"end",   "end"};
            // Baseline family — collapse vertical to top/center/bottom.
            case java.awt.GridBagConstraints.BASELINE             -> new String[] {"center", "center"};
            case java.awt.GridBagConstraints.BASELINE_LEADING     -> new String[] {"start",  "center"};
            case java.awt.GridBagConstraints.BASELINE_TRAILING    -> new String[] {"end",    "center"};
            case java.awt.GridBagConstraints.ABOVE_BASELINE         -> new String[] {"center", "start"};
            case java.awt.GridBagConstraints.ABOVE_BASELINE_LEADING -> new String[] {"start",  "start"};
            case java.awt.GridBagConstraints.ABOVE_BASELINE_TRAILING-> new String[] {"end",    "start"};
            case java.awt.GridBagConstraints.BELOW_BASELINE         -> new String[] {"center", "end"};
            case java.awt.GridBagConstraints.BELOW_BASELINE_LEADING -> new String[] {"start",  "end"};
            case java.awt.GridBagConstraints.BELOW_BASELINE_TRAILING-> new String[] {"end",    "end"};
            default -> {
                vaadinx.EHelper.onUnimplemented("vaadinx.awt.GridBagLayout",
                        "anchorToSelfPair(unknown anchor)", anchor);
                yield new String[] {"center", "center"};
            }
        };
    }

    @Override
    public String toString() {
        return getClass().getName();
    }
}
