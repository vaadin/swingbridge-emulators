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
 * This file is derived from OpenJDK's java.awt.GridLayout
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
 * Emulator for {@link java.awt.GridLayout} (D_gridlayout). Under D_layout_css_on_content,
 * {@link #layoutContainer} writes
 * {@code display: grid; grid-template-columns: repeat(ncols, 1fr);
 * grid-template-rows: repeat(nrows, 1fr); gap: …} to the container's peer
 * content element via {@link vaadinx.EHelper#applyContainerCss}.
 *
 * <p>Hand-written rather than generator-emitted: the generator's root-class
 * detection assumes Component-hierarchy ancestry and would emit a {@code peer}
 * field, which a LayoutManager doesn't have. Plain {@link LayoutManager} (not
 * {@link LayoutManager2}) — JDK GridLayout has no per-child constraint storage;
 * cell assignment is purely insertion-order driven, which the browser's grid
 * auto-placement (row-major, the CSS default) reproduces exactly.
 *
 * <p><b>Dimension resolution.</b> JDK GridLayout fixes one dimension and
 * derives the other from the live component count: with {@code rows > 0} the
 * row count is authoritative and columns are {@code ceil(n / rows)}; with
 * {@code rows == 0} columns are authoritative and rows are {@code ceil(n /
 * cols)}. That count isn't known until layout time, so — unlike the
 * constraint-carrying {@link GridBagLayout} — this layout reads
 * {@link Container#getComponentCount()} inside {@link #layoutContainer} and
 * computes the resolved dimensions there, exactly as JDK's
 * {@code layoutContainer} does.
 *
 * <p><b>Cell sizing (R_layouts_close_enough).</b> Every cell is an equal fraction ({@code 1fr})
 * and every child stretches to fill it ({@code align/justify-items: stretch})
 * — matching GridLayout's "all cells equal, components resized to fill"
 * contract. We do not compute the pixel arithmetic JDK does from child
 * preferred sizes; the browser divides the container evenly, which is
 * close-enough per R_layouts_close_enough. {@code hgap}/{@code vgap} carry through to CSS
 * {@code gap}.
 */
public class GridLayout implements CssEmittingLayoutManager, Serializable {

    private int rows;
    private int cols;
    private int hgap;
    private int vgap;

    /** JDK: {@code GridLayout()} → one row, columns derived from child count. */
    public GridLayout() {
        this(1, 0, 0, 0);
    }

    public GridLayout(int rows, int cols) {
        this(rows, cols, 0, 0);
    }

    public GridLayout(int rows, int cols, int hgap, int vgap) {
        // JDK throws IllegalArgumentException when both dimensions are zero —
        // there's no way to derive a grid. R_match_swing_errors: preserve the failure mode, it's
        // a programming error. Negative rows/cols are also rejected by JDK.
        if (rows == 0 && cols == 0) {
            throw new IllegalArgumentException("rows and cols cannot both be zero");
        }
        this.rows = rows;
        this.cols = cols;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        // JDK guard: setting rows to 0 is only legal while cols is non-zero
        // (the other dimension must remain derivable). Mirror per R_match_swing_errors. No
        // revalidate — Swing's contract is that user code calls
        // container.revalidate() after mutating layout state.
        if (rows == 0 && this.cols == 0) {
            throw new IllegalArgumentException("rows and cols cannot both be zero");
        }
        this.rows = rows;
    }

    public int getColumns() {
        return cols;
    }

    public void setColumns(int cols) {
        if (cols == 0 && this.rows == 0) {
            throw new IllegalArgumentException("rows and cols cannot both be zero");
        }
        this.cols = cols;
    }

    public int getHgap() {
        return hgap;
    }

    public void setHgap(int hgap) {
        // JDK tolerates negative gaps (they overlap cells); no validation.
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
        // JDK GridLayout does nothing — no per-child constraint storage. The
        // resolved grid depends only on the child count, read at layout time.
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // No per-child state to clear.
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // FlowLayout / GridBagLayout precedent — dummy 1×1 keeps user code that
        // reads getPreferredSize right after install from getting a zero some
        // legacy code treats as "not laid out yet." We don't compute the
        // sum-of-max-child-size arithmetic JDK does (R_layouts_close_enough).
        return new Dimension(1, 1);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(1, 1);
    }

    @Override
    public Map<String, String> childCss(Container parent, Component child) {
        // GridLayout consults no preferred size on either axis: JDK's layoutContainer
        // gives every child the same (w / ncols, h / nrows) cell. So both axes are the
        // layout's (D_layout_owns_child_sizing) — a pref-sized child stops honouring
        // its pref here, which is the JDK's behaviour and a visible change from
        // SB-Emulators' earlier bare-width write.
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(true, true);
    }

    @Override
    public Map<String, String> containerCss(Container parent) {
        // Resolve the grid the way JDK's layoutContainer does: the fixed
        // dimension wins, the other is ceil(ncomponents / fixed). Children
        // auto-place row-major, so childCss carries only the sizing policy. An empty container
        // lays out to nothing in JDK; we clamp both dimensions to at least 1
        // (via gridLayoutCss) so the emitted repeat() stays valid CSS and any
        // leftover FlowLayout keys still get reset.
        int ncomponents = parent.getComponentCount();
        int nrows = rows;
        int ncols = cols;
        if (ncomponents > 0) {
            if (nrows > 0) {
                ncols = (ncomponents + nrows - 1) / nrows;
            } else {
                nrows = (ncomponents + ncols - 1) / ncols;
            }
        }
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.gridLayoutCss(nrows, ncols, hgap, vgap);
    }

    @Override
    public String toString() {
        return getClass().getName()
                + "[hgap=" + hgap + ",vgap=" + vgap + ",rows=" + rows + ",cols=" + cols + "]";
    }
}
