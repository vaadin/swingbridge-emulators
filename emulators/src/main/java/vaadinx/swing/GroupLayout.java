/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.GroupLayout
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.awt.CssEmittingLayoutManager;
import vaadinx.awt.LayoutManager2;

import com.vaadin.flow.dom.Style;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emulator for {@link javax.swing.GroupLayout} (D_grouplayout) — the layout manager
 * NetBeans' GUI builder ("Free Design") emits in every generated
 * {@code initComponents()}. Without it the import-swap of any NetBeans-built
 * panel fails to <em>compile</em> (the reference to {@code javax.swing.GroupLayout}
 * swaps to {@code vaadinx.swing.GroupLayout}); it is therefore a hard
 * prerequisite for migrating real-world Swing rather than a runtime nicety.
 * Built alongside {@link vaadinx.awt.GridLayout} ([D_gridlayout]) as the two layout
 * managers the first real JLawyer slice ({@code InvoicePositionEntryPanel})
 * forced.
 *
 * <p><b>Auto-add.</b> NetBeans-generated code never calls {@code container.add(comp)}
 * — GroupLayout adds referenced components to the host itself. So does this
 * emulator: {@link Group#addComponent} routes through {@link #autoAdd}, which
 * adds the component to the host {@link Container} exactly once (each component
 * is referenced in <em>both</em> the horizontal and vertical group trees, so
 * the add is idempotent via a managed-set guard). The host's peer-DOM insertion
 * happens through {@code Container.add}; grid placement is written later at
 * {@link #layoutContainer}.
 *
 * <p><b>Rendering — grid reconstruction (R_layouts_close_enough).</b> GroupLayout positions each
 * component independently on two axes (the {@link #setHorizontalGroup} tree and
 * the {@link #setVerticalGroup} tree), every managed component appearing once in
 * each. The emulator flattens each axis tree into integer track intervals and
 * emits a CSS grid: a {@code SequentialGroup} lays its children in consecutive
 * slots; a {@code ParallelGroup} overlays its children into the same slot band
 * (its direct component children stretch to fill the band, matching
 * GroupLayout's "all parallel children share the group's span"). Gaps consume
 * no track — a single uniform container {@code gap} approximates their spacing.
 * Each component is placed with {@code grid-column}/{@code grid-row} spanning
 * its two bands, and {@code justify-self}/{@code align-self} from the effective
 * alignment of the nearest enclosing parallel group (per-component
 * {@link Alignment} override winning). This reproduces the dominant NetBeans
 * idiom (a vertical sequence of rows, each a parallel/baseline group of a few
 * components aligned into columns by the horizontal tree) faithfully; exact
 * per-gap pixel spacing and independent-strip widths are R_layouts_close_enough-approximate, left
 * as pixel-accurate layout is out of scope permanently
 * (D_pixel_layout_not_planned).
 *
 * <p><b>Size hints (R_layouts_close_enough).</b> A track is flexible ({@code minmax(0, 1fr)}) when a
 * component spanning it was added fully-resizable (max = {@code Short.MAX_VALUE});
 * otherwise the track is {@code auto} (content-sized), which keeps forms compact
 * and label columns tight. Explicit preferred-pixel sizes (e.g.
 * {@code addComponent(c, PREFERRED_SIZE, 120, PREFERRED_SIZE)}) are not written
 * as hard CSS widths — that would fight the field surrogates' own
 * columns-derived width; the {@code auto} track sizes to the surrogate's
 * intrinsic width instead.
 */
public class GroupLayout implements LayoutManager2, CssEmittingLayoutManager {

    /** Use the component's default size for the dimension. Matches JDK's value. */
    public static final int DEFAULT_SIZE = -1;
    /** Use the component's preferred size for the dimension (fixed). Matches JDK. */
    public static final int PREFERRED_SIZE = -2;

    /** Port of {@code javax.swing.GroupLayout.Alignment}. */
    public enum Alignment {
        LEADING, TRAILING, CENTER, BASELINE
    }

    private final Container host;
    private Group horizontalGroup;
    private Group verticalGroup;
    private boolean autoCreateGaps;
    private boolean autoCreateContainerGaps;

    // Components already added to the host, so the second tree's reference
    // doesn't double-add. LinkedHashSet keeps a stable order for debugging.
    private final Set<Component> managed = new LinkedHashSet<>();

    // Per-child placement computed by containerCss and read back by childCss
    // within one layoutContainer pass (CssEmittingLayoutManager's call-order
    // contract: containerCss runs first). GroupLayout's per-child grid-column /
    // grid-row / stretch are a byproduct of the whole-container band analysis,
    // so they can't be derived from one child in isolation — hence the stash.
    private Map<Component, int[]> lastColBands = Map.of();
    private Map<Component, int[]> lastRowBands = Map.of();
    private Map<Component, Alignment> lastColAligns = Map.of();
    private Map<Component, Alignment> lastRowAligns = Map.of();
    private Map<Component, Boolean> lastColResizable = Map.of();
    private Map<Component, Boolean> lastRowResizable = Map.of();

    public GroupLayout(Container host) {
        // JDK throws IllegalArgumentException on a null host — R_match_swing_errors, it's a
        // programming error (the layout has nothing to manage).
        if (host == null) {
            throw new IllegalArgumentException("Container must be non-null");
        }
        this.host = host;
    }

    // ---- configuration -----------------------------------------------------

    public void setHorizontalGroup(Group group) {
        if (group == null) {
            throw new IllegalArgumentException("Group must be non-null");
        }
        this.horizontalGroup = group;
    }

    public void setVerticalGroup(Group group) {
        if (group == null) {
            throw new IllegalArgumentException("Group must be non-null");
        }
        this.verticalGroup = group;
    }

    public SequentialGroup createSequentialGroup() {
        return new SequentialGroup(this);
    }

    public ParallelGroup createParallelGroup() {
        return createParallelGroup(Alignment.LEADING, true);
    }

    public ParallelGroup createParallelGroup(Alignment alignment) {
        return createParallelGroup(alignment, true);
    }

    public ParallelGroup createParallelGroup(Alignment alignment, boolean resizable) {
        if (alignment == null) {
            throw new IllegalArgumentException("Alignment must be non-null");
        }
        return new ParallelGroup(this, alignment, resizable);
    }

    public void setAutoCreateGaps(boolean autoCreateGaps) {
        // Stored for API fidelity; the reconstruction uses a uniform container
        // gap regardless (R_layouts_close_enough), so this flag doesn't change rendering today.
        this.autoCreateGaps = autoCreateGaps;
    }

    public boolean getAutoCreateGaps() {
        return autoCreateGaps;
    }

    public void setAutoCreateContainerGaps(boolean autoCreateContainerGaps) {
        this.autoCreateContainerGaps = autoCreateContainerGaps;
    }

    public boolean getAutoCreateContainerGaps() {
        return autoCreateContainerGaps;
    }

    public void setHonorsVisibility(boolean honorsVisibility) {
        // No Vaadin counterpart for GroupLayout's hide-collapses-space behaviour
        // (a hidden peer element still occupies its grid cell). R_vaadin_first drop-and-WARN.
        vaadinx.EHelper.onUnimplemented("vaadinx.swing.GroupLayout", "setHonorsVisibility",
                honorsVisibility);
    }

    public void linkSize(Component... components) {
        // Forcing linked components to a common size has no clean grid analog. WARN.
        vaadinx.EHelper.onUnimplemented("vaadinx.swing.GroupLayout", "linkSize",
                (Object[]) components);
    }

    public void replace(Component existingComponent, Component newComponent) {
        vaadinx.EHelper.onUnimplemented("vaadinx.swing.GroupLayout", "replace",
                existingComponent, newComponent);
    }

    /** Add the component to the host exactly once. Idempotent across both trees. */
    void autoAdd(Component comp) {
        if (comp != null && managed.add(comp)) {
            host.add(comp);
        }
    }

    // ---- LayoutManager2 ----------------------------------------------------

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // No-op: the group trees are authoritative. Called by Container.addImpl
        // when autoAdd → host.add(comp) runs; must not re-enter.
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        // No-op — same reason as the String form (host.add with null constraint).
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        managed.remove(comp);
        if (comp != null && comp.getPeer() != null) {
            comp.withPeer(p -> {
                Style style = p.getElement().getStyle();
                style.remove("grid-column");
                style.remove("grid-row");
                style.remove("justify-self");
                style.remove("align-self");
                style.remove("min-width");
                style.remove("min-height");
                // And the sizing policy, so a removed component's preferred size is its
                // own again wherever it lands next (D_layout_owns_child_sizing).
                style.remove(com.vaadin.swingbridge.surrogates.util.LayoutCss.PREF_W_VAR);
                style.remove(com.vaadin.swingbridge.surrogates.util.LayoutCss.PREF_H_VAR);
            });
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // FlowLayout/GridBagLayout precedent — dummy 1×1. GroupLayout's real
        // preferred-size solver is R_layouts_close_enough-out-of-scope.
        return new Dimension(1, 1);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(1, 1);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // No cached pixel state to drop (R_layouts_close_enough).
    }

    @Override
    public Map<String, String> containerCss(Container parent) {
        if (horizontalGroup == null || verticalGroup == null) {
            // A GroupLayout with only one axis set can't place anything. Real
            // NetBeans code always sets both; WARN rather than throw (R_match_swing_errors minor).
            // Clear the stash so childCss places nothing this pass.
            lastColBands = Map.of();
            lastRowBands = Map.of();
            vaadinx.EHelper.onUnimplemented("vaadinx.swing.GroupLayout",
                    "layoutContainer(missing group)",
                    horizontalGroup == null ? "horizontalGroup" : "verticalGroup");
            return Map.of();
        }

        // Assign each component a column band (horizontal tree) and row band
        // (vertical tree), plus its effective alignment on each axis. Resizable
        // gaps (springs) claim their own flexible spacer track, recorded here.
        // Horizontal pass is row-stack aware (independent per-row fill with
        // right-aligned trailing components); the flexible "middle" columns it
        // picks are collected in middleCols. The vertical pass is a plain
        // top-to-bottom reconstruction (rows never right-align).
        Map<Component, int[]> colBands = new LinkedHashMap<>();
        Map<Component, Alignment> colAligns = new LinkedHashMap<>();
        List<int[]> colGapSpacers = new ArrayList<>();
        Set<Integer> middleCols = new LinkedHashSet<>();
        int ncols = assignAxis(horizontalGroup, 0, Alignment.LEADING, colBands, colAligns,
                colGapSpacers, true, middleCols);

        Map<Component, int[]> rowBands = new LinkedHashMap<>();
        Map<Component, Alignment> rowAligns = new LinkedHashMap<>();
        List<int[]> rowGapSpacers = new ArrayList<>();
        int nrows = assignAxis(verticalGroup, 0, Alignment.LEADING, rowBands, rowAligns,
                rowGapSpacers, false, null);

        // Per-component resizability on each axis (explicit max wins, else the
        // component's natural fill behaviour). Drives both flexible-track
        // selection and justify-self/align-self:stretch below.
        Map<Component, Boolean> colResizable = new LinkedHashMap<>();
        Map<Component, Boolean> rowResizable = new LinkedHashMap<>();
        collectResizable(horizontalGroup, colResizable, true);
        collectResizable(verticalGroup, rowResizable, false);

        // A track is flexible (1fr, absorbs slack) iff a *single-track*
        // resizable component occupies it, or it is a spring's spacer track.
        // Otherwise auto (content-sized). A component stretched across a
        // multi-track parallel band (e.g. a full-width separator) must NOT
        // force every sub-track to 1fr — that equalizes all columns; it fills
        // via justify-self:stretch instead. Without any flexible track a grid
        // narrower than its container packs to the left, leaving the far side
        // empty — the structural failure R_layouts_close_enough does NOT license.
        boolean[] colFlex = new boolean[Math.max(1, ncols)];
        boolean[] rowFlex = new boolean[Math.max(1, nrows)];
        markCompFlex(colBands, colResizable, colFlex);
        markCompFlex(rowBands, rowResizable, rowFlex);
        markSpacerTracks(colGapSpacers, colFlex);
        markSpacerTracks(rowGapSpacers, rowFlex);
        // The row-stack's middle columns are the slack absorbers (a spring, or
        // the column resizable components span) — always flexible.
        for (int c : middleCols) {
            if (c < colFlex.length) colFlex[c] = true;
        }

        // Stash the per-child bands/aligns/resizability for childCss to read
        // back this pass (call-order contract). The container grid template is
        // the return value; the framework applies it.
        lastColBands = colBands;
        lastRowBands = rowBands;
        lastColAligns = colAligns;
        lastRowAligns = rowAligns;
        lastColResizable = colResizable;
        lastRowResizable = rowResizable;
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.groupLayoutCss(
                trackTemplate(colFlex), trackTemplate(rowFlex), 6);
    }

    @Override
    public Map<String, String> childCss(Container parent, Component child) {
        if (!managed.contains(child)) {
            // Not one of GroupLayout's components (the default layoutContainer
            // visits every child of the host); leave it untouched.
            return null;
        }
        int[] col = lastColBands.get(child);
        int[] row = lastRowBands.get(child);
        if (col == null || row == null) {
            // Managed but absent from a tree — shouldn't happen for NetBeans
            // code (every added component is in both trees).
            vaadinx.EHelper.onUnimplemented("vaadinx.swing.GroupLayout",
                    "layoutContainer(component not in both groups)", child);
            return null;
        }
        Map<String, String> css = new LinkedHashMap<>();
        css.put("grid-column", (col[0] + 1) + " / " + (col[1] + 1));
        css.put("grid-row", (row[0] + 1) + " / " + (row[1] + 1));
        // A resizable component fills its cell/band (stretch); a fixed one sits
        // at its alignment. This is what makes txtName / a scroll pane actually
        // occupy the flexible track (rather than sitting content-width at its
        // leading edge) and a full-width separator span its band.
        boolean colResizable = Boolean.TRUE.equals(lastColResizable.get(child));
        boolean rowResizable = Boolean.TRUE.equals(lastRowResizable.get(child));
        css.put("justify-self", colResizable ? "stretch" : justifySelf(lastColAligns.get(child)));
        css.put("align-self", rowResizable ? "stretch" : alignSelf(lastRowAligns.get(child)));
        // The stretch above only grows an auto-sized item, so a resizable component
        // carrying a preferred width would sit at that width instead of filling its
        // band. Hand the axis to the layout on exactly the axes that stretch
        // (D_layout_owns_child_sizing) — which is also GroupLayout's own model: a
        // component is added with a per-axis size of DEFAULT_SIZE / PREFERRED_SIZE /
        // an explicit span, and only the non-PREFERRED_SIZE ones resize.
        css.putAll(com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(colResizable, rowResizable));
        // Grid items default to min-width/height:auto, which can overflow a
        // bounded container when a cell holds an intrinsically large subtree
        // (a JScrollPane). GridBagLayout precedent — pin to 0.
        css.put("min-width", "0");
        css.put("min-height", "0");
        return css;
    }

    // ---- reconstruction helpers -------------------------------------------

    /**
     * Assign track intervals for one axis. Returns the exclusive end slot.
     * Sequential groups concatenate; parallel groups overlay (direct component
     * children stretch to the group's full band); a fixed gap consumes no slot,
     * a resizable gap (spring) consumes one flexible spacer track recorded in
     * {@code gapSpacers}.
     *
     * <p>On the horizontal axis, a parallel group of independent rows is
     * handled by {@link #assignRowStack} (right-aligned trailing components);
     * {@code middleCols} collects the flexible slack columns it picks. The
     * vertical axis passes {@code horizontal=false} / {@code middleCols=null}
     * and always uses the plain overlay.
     */
    private int assignAxis(Object node, int start, Alignment inherited,
                           Map<Component, int[]> bands, Map<Component, Alignment> aligns,
                           List<int[]> gapSpacers, boolean horizontal, Set<Integer> middleCols) {
        if (node instanceof CompSpring cs) {
            bands.put(cs.comp, new int[]{start, start + 1});
            aligns.put(cs.comp, cs.align != null ? cs.align : inherited);
            return start + 1;
        }
        if (node instanceof GapSpring gs) {
            if (gs.resizable) {
                // A spring claims a track so it can absorb slack (1fr) and push
                // the following components toward the far edge.
                gapSpacers.add(new int[]{start, start + 1});
                return start + 1;
            }
            return start; // fixed gaps map to the uniform container gap, not a track
        }
        if (node instanceof SequentialGroup sg) {
            int cursor = start;
            for (Object child : sg.springs) {
                cursor = assignAxis(child, cursor, inherited, bands, aligns, gapSpacers, horizontal, middleCols);
            }
            return Math.max(start, cursor);
        }
        if (node instanceof ParallelGroup pg) {
            if (horizontal) {
                int end = assignRowStack(pg, start, bands, aligns, gapSpacers, middleCols);
                if (end >= 0) return end; // row-stack handled it
            }
            // Generic overlay (all vertical parallels, and horizontal parallels
            // that don't qualify as a right-aligning row stack).
            int maxEnd = start;
            for (Object child : pg.springs) {
                int e = assignAxis(child, start, pg.alignment, bands, aligns, gapSpacers, horizontal, middleCols);
                if (e > maxEnd) maxEnd = e;
            }
            int band = Math.max(start + 1, maxEnd);
            // Stretch direct component children to fill the parallel band.
            for (Object child : pg.springs) {
                if (child instanceof CompSpring cs) {
                    bands.put(cs.comp, new int[]{start, band});
                }
            }
            return band;
        }
        return start;
    }

    /**
     * Horizontal reconstruction of a parallel group of independent rows — the
     * dominant NetBeans form idiom. Each row lays its leading components from
     * the left, its trailing components (those after the row's slack absorber —
     * a resizable component or a spring) flush RIGHT, and the absorber spans the
     * flexible middle column. Because absorbers <em>span</em> the middle rather
     * than flexing the columns they cross, a narrow leading column in one row
     * (a {@code "Menge:"} label) and a wide filling component in another (a text
     * field) no longer fight over a shared column: the last trailing component
     * of every row lands in the same rightmost column, so trash / up-down /
     * total controls align at the dialog's right edge — matching how GroupLayout
     * lays each row out independently across the full width.
     *
     * <p>Returns the exclusive band end, or {@code -1} when the group doesn't
     * qualify (a child isn't a sequential row / component, or no row has both an
     * absorber and trailing content). The caller then falls back to a plain
     * overlay, so simple parallel groups render exactly as before.
     */
    private int assignRowStack(ParallelGroup pg, int start,
                               Map<Component, int[]> bands, Map<Component, Alignment> aligns,
                               List<int[]> gapSpacers, Set<Integer> middleCols) {
        List<RowSlots> rows = new ArrayList<>();
        for (Object child : pg.springs) {
            RowSlots r = analyzeRow(child);
            if (r == null) return -1; // unsupported child shape → fall back
            rows.add(r);
        }
        int maxLeading = 0;
        int maxTrailing = 0;
        boolean anyAbsorber = false;
        boolean anyTrailing = false;
        for (RowSlots r : rows) {
            maxLeading = Math.max(maxLeading, r.leading.size());
            maxTrailing = Math.max(maxTrailing, r.trailing.size());
            anyAbsorber |= r.hasAbsorber;
            anyTrailing |= !r.trailing.isEmpty();
        }
        // Only worth the right-alignment when trailing content exists to push
        // against an absorber; otherwise a plain overlay is already correct
        // (and this keeps existing simple layouts byte-for-byte unchanged).
        if (!anyAbsorber || !anyTrailing) return -1;

        int bandWidth = maxLeading + 1 + maxTrailing;
        int bandEnd = start + bandWidth;
        middleCols.add(start + maxLeading); // the single flexible slack column

        for (RowSlots r : rows) {
            for (int k = 0; k < r.leading.size(); k++) {
                placeSingle(r.leading.get(k), start + k, pg.alignment, bands, aligns, gapSpacers);
            }
            int tc = r.trailing.size();
            for (int k = 0; k < tc; k++) {
                // Flush right, order preserved (last item at bandEnd-1). Trailing
                // components right-align within their column so they hug the edge.
                placeSingle(r.trailing.get(k), bandEnd - tc + k, Alignment.TRAILING, bands, aligns, gapSpacers);
            }
            int mStart = start + r.leading.size();
            int mEnd = bandEnd - tc;
            for (Object m : r.middle) {
                if (m instanceof CompSpring cs) {
                    bands.put(cs.comp, new int[]{mStart, mEnd});
                    aligns.put(cs.comp, cs.align != null ? cs.align : pg.alignment);
                }
                // A middle spring (resizable gap) needs no band — the middle
                // column is already flexible, absorbing the row's slack.
            }
        }
        return bandEnd;
    }

    /** Place a single-column item (component, spring, or a one-column nested group). */
    private void placeSingle(Object item, int col, Alignment inherited,
                             Map<Component, int[]> bands, Map<Component, Alignment> aligns,
                             List<int[]> gapSpacers) {
        if (item instanceof CompSpring cs) {
            bands.put(cs.comp, new int[]{col, col + 1});
            aligns.put(cs.comp, cs.align != null ? cs.align : inherited);
        } else if (item instanceof GapSpring gs) {
            if (gs.resizable) gapSpacers.add(new int[]{col, col + 1});
        } else if (item instanceof Group g) {
            // Nested group occupying a single column (e.g. the up/down button
            // parallel): overlay its children into that column.
            assignAxis(g, col, inherited, bands, aligns, gapSpacers, true, new LinkedHashSet<>());
        }
    }

    /**
     * Split one horizontal child of a row-stack parallel group into leading /
     * middle / trailing runs around its slack absorber. Returns {@code null}
     * when the child isn't a supported row shape (a bare nested parallel).
     */
    private RowSlots analyzeRow(Object child) {
        List<Object> items = new ArrayList<>();
        if (child instanceof CompSpring) {
            items.add(child);
        } else if (child instanceof SequentialGroup sg) {
            for (Object s : sg.springs) {
                if (s instanceof CompSpring) {
                    items.add(s);
                } else if (s instanceof GapSpring gs) {
                    if (gs.resizable) items.add(s); // only springs consume a column
                } else if (s instanceof Group) {
                    items.add(s); // nested group consumes a column
                }
            }
        } else {
            return null;
        }
        int first = -1;
        int last = -1;
        for (int i = 0; i < items.size(); i++) {
            if (isAbsorber(items.get(i))) {
                if (first < 0) first = i;
                last = i;
            }
        }
        List<Object> leading = new ArrayList<>();
        List<Object> middle = new ArrayList<>();
        List<Object> trailing = new ArrayList<>();
        if (first < 0) {
            leading.addAll(items); // no absorber: everything is leading
        } else {
            leading.addAll(items.subList(0, first));
            middle.addAll(items.subList(first, last + 1));
            trailing.addAll(items.subList(last + 1, items.size()));
        }
        return new RowSlots(leading, middle, trailing, first >= 0);
    }

    /** A slack absorber on the horizontal axis: a resizable component or a spring. */
    private static boolean isAbsorber(Object item) {
        if (item instanceof CompSpring cs) return isCompResizable(cs, true);
        if (item instanceof GapSpring gs) return gs.resizable;
        return false; // nested groups aren't absorbers
    }

    /** Leading / middle / trailing runs of one row-stack child (see {@link #assignRowStack}). */
    private static final class RowSlots {
        final List<Object> leading;
        final List<Object> middle;
        final List<Object> trailing;
        final boolean hasAbsorber;

        RowSlots(List<Object> leading, List<Object> middle, List<Object> trailing, boolean hasAbsorber) {
            this.leading = leading;
            this.middle = middle;
            this.trailing = trailing;
            this.hasAbsorber = hasAbsorber;
        }
    }

    /** Record each component's resizability on this axis into {@code out}. */
    private void collectResizable(Object node, Map<Component, Boolean> out, boolean horizontal) {
        if (node instanceof CompSpring cs) {
            out.put(cs.comp, isCompResizable(cs, horizontal));
            return;
        }
        if (node instanceof GapSpring) {
            return;
        }
        if (node instanceof Group g) {
            for (Object child : g.springs) collectResizable(child, out, horizontal);
        }
    }

    /**
     * Mark a track flexible only when a <em>single-track</em> resizable
     * component occupies it. Multi-track spanners (a component stretched to a
     * parallel group's full band) are deliberately skipped — they fill via
     * {@code justify-self:stretch}, and flexing all their sub-tracks would
     * equalize every column into one uniform width.
     */
    private static void markCompFlex(Map<Component, int[]> bands,
                                     Map<Component, Boolean> resizable, boolean[] flex) {
        for (Map.Entry<Component, int[]> e : bands.entrySet()) {
            int[] band = e.getValue();
            if (band == null || band[1] - band[0] != 1) continue;
            if (Boolean.TRUE.equals(resizable.get(e.getKey())) && band[0] < flex.length) {
                flex[band[0]] = true;
            }
        }
    }

    /** Every spring spacer track is flexible (1fr) so the spring absorbs slack. */
    private static void markSpacerTracks(List<int[]> spacers, boolean[] flex) {
        for (int[] sp : spacers) {
            for (int t = sp[0]; t < sp[1] && t < flex.length; t++) flex[t] = true;
        }
    }

    /**
     * Whether a component fills (stretches) on the given axis. An explicit max
     * wins ({@code Short.MAX_VALUE} → resizable, {@code PREFERRED_SIZE} → fixed);
     * the {@code DEFAULT_SIZE} case (the 1-arg {@code addComponent}) defers to
     * the component's natural resizability, exactly as JDK GroupLayout resolves
     * it from {@code comp.getMaximumSize()}.
     */
    private static boolean isCompResizable(CompSpring cs, boolean horizontal) {
        if (cs.max == Short.MAX_VALUE) return true;
        if (cs.max == PREFERRED_SIZE) return false;
        return isNaturallyResizable(cs.comp, horizontal);
    }

    /**
     * Approximate {@code comp.getMaximumSize()}'s resize contract (R_layouts_close_enough — our
     * components don't carry pixel max metrics). Honour an explicitly-set
     * maximumSize, then fall back to the Swing defaults of the fill-natured
     * components: scroll panes / text areas / editor panes fill both axes,
     * single-line text fields and horizontal separators fill horizontally, and
     * everything else (buttons, labels, combo boxes, spinners) stays fixed to
     * its preferred size.
     */
    private static boolean isNaturallyResizable(Component comp, boolean horizontal) {
        if (comp.isMaximumSizeSet()) {
            java.awt.Dimension max = comp.getMaximumSize();
            return (horizontal ? max.width : max.height) >= Short.MAX_VALUE;
        }
        if (comp instanceof vaadinx.swing.JScrollPane
                || comp instanceof vaadinx.swing.JTextArea
                || comp instanceof vaadinx.swing.JEditorPane) {
            return true;
        }
        if (comp instanceof vaadinx.swing.text.JTextComponent) {
            return horizontal;
        }
        if (comp instanceof vaadinx.swing.JSeparator) {
            return horizontal;
        }
        return false;
    }

    private static String trackTemplate(boolean[] flex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < flex.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(flex[i] ? "minmax(0, 1fr)" : "auto");
        }
        return sb.length() == 0 ? "auto" : sb.toString();
    }

    private static String justifySelf(Alignment a) {
        // Horizontal axis. BASELINE has no horizontal meaning → treat as start.
        return switch (a == null ? Alignment.LEADING : a) {
            case TRAILING -> "end";
            case CENTER -> "center";
            case LEADING, BASELINE -> "start";
        };
    }

    private static String alignSelf(Alignment a) {
        // Vertical axis. BASELINE maps to CSS baseline (grid supports it).
        return switch (a == null ? Alignment.LEADING : a) {
            case TRAILING -> "end";
            case CENTER -> "center";
            case BASELINE -> "baseline";
            case LEADING -> "start";
        };
    }

    @Override
    public String toString() {
        return getClass().getName() + "[host=" + host + "]";
    }

    // ---- spring model (private) -------------------------------------------
    // A group's children are "springs": a component, a gap, or a nested group.
    // Group itself is the nested-group case, so springs is List<Object> holding
    // CompSpring / GapSpring / Group.

    private static final class CompSpring {
        final Component comp;
        final int min, pref, max;
        final Alignment align; // per-component override; null = inherit group's
        CompSpring(Component comp, int min, int pref, int max, Alignment align) {
            this.comp = comp;
            this.min = min;
            this.pref = pref;
            this.max = max;
            this.align = align;
        }
    }

    private static final class GapSpring {
        // A gap whose max is Short.MAX_VALUE is a *spring* (a.k.a. glue): it
        // absorbs slack and pushes the components on either side apart. In the
        // grid reconstruction it consumes one flexible (1fr) track so the
        // trailing content is driven to the far side, matching GroupLayout's
        // resizable-gap behaviour. A fixed gap consumes no track (its spacing
        // is approximated by the uniform container gap, R_layouts_close_enough).
        final boolean resizable;

        GapSpring() {
            this(false);
        }

        GapSpring(boolean resizable) {
            this.resizable = resizable;
        }
    }

    /**
     * Base of the group hierarchy. Holds an ordered list of springs. The
     * add-methods return {@code Group}; {@link SequentialGroup} /
     * {@link ParallelGroup} override with covariant return types so the
     * NetBeans fluent chain (which calls subtype-specific methods off the
     * result) compiles — mirroring JDK's covariant API.
     */
    public abstract static class Group {
        final GroupLayout owner;
        final List<Object> springs = new ArrayList<>();

        Group(GroupLayout owner) {
            this.owner = owner;
        }

        public Group addComponent(Component component) {
            return addComponent(component, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE);
        }

        public Group addComponent(Component component, int min, int pref, int max) {
            owner.autoAdd(component);
            springs.add(new CompSpring(component, min, pref, max, null));
            return this;
        }

        public Group addGap(int size) {
            springs.add(new GapSpring());
            return this;
        }

        public Group addGap(int min, int pref, int max) {
            springs.add(new GapSpring(max == Short.MAX_VALUE));
            return this;
        }

        public Group addGroup(Group group) {
            springs.add(group);
            return this;
        }
    }

    /** Port of {@code javax.swing.GroupLayout.SequentialGroup}. */
    public static final class SequentialGroup extends Group {
        SequentialGroup(GroupLayout owner) {
            super(owner);
        }

        @Override
        public SequentialGroup addComponent(Component component) {
            super.addComponent(component);
            return this;
        }

        @Override
        public SequentialGroup addComponent(Component component, int min, int pref, int max) {
            super.addComponent(component, min, pref, max);
            return this;
        }

        @Override
        public SequentialGroup addGap(int size) {
            super.addGap(size);
            return this;
        }

        @Override
        public SequentialGroup addGap(int min, int pref, int max) {
            super.addGap(min, pref, max);
            return this;
        }

        @Override
        public SequentialGroup addGroup(Group group) {
            super.addGroup(group);
            return this;
        }

        public SequentialGroup addContainerGap() {
            springs.add(new GapSpring());
            return this;
        }

        public SequentialGroup addContainerGap(int pref, int max) {
            springs.add(new GapSpring(max == Short.MAX_VALUE));
            return this;
        }

        public SequentialGroup addPreferredGap(LayoutStyle.ComponentPlacement type) {
            springs.add(new GapSpring());
            return this;
        }

        public SequentialGroup addPreferredGap(LayoutStyle.ComponentPlacement type,
                                               int pref, int max) {
            springs.add(new GapSpring(max == Short.MAX_VALUE));
            return this;
        }
    }

    /** Port of {@code javax.swing.GroupLayout.ParallelGroup}. */
    public static final class ParallelGroup extends Group {
        final Alignment alignment;
        final boolean resizable;

        ParallelGroup(GroupLayout owner, Alignment alignment, boolean resizable) {
            super(owner);
            this.alignment = alignment;
            this.resizable = resizable;
        }

        @Override
        public ParallelGroup addComponent(Component component) {
            super.addComponent(component);
            return this;
        }

        @Override
        public ParallelGroup addComponent(Component component, int min, int pref, int max) {
            super.addComponent(component, min, pref, max);
            return this;
        }

        public ParallelGroup addComponent(Component component, Alignment alignment) {
            owner.autoAdd(component);
            springs.add(new CompSpring(component, DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE, alignment));
            return this;
        }

        public ParallelGroup addComponent(Component component, Alignment alignment,
                                          int min, int pref, int max) {
            owner.autoAdd(component);
            springs.add(new CompSpring(component, min, pref, max, alignment));
            return this;
        }

        @Override
        public ParallelGroup addGap(int size) {
            super.addGap(size);
            return this;
        }

        @Override
        public ParallelGroup addGap(int min, int pref, int max) {
            super.addGap(min, pref, max);
            return this;
        }

        @Override
        public ParallelGroup addGroup(Group group) {
            super.addGroup(group);
            return this;
        }

        public ParallelGroup addGroup(Alignment alignment, Group group) {
            // The per-group alignment override isn't modelled separately in the
            // reconstruction — the nested group carries its own alignment. R_layouts_close_enough.
            springs.add(group);
            return this;
        }
    }
}
