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
 * This file is derived from OpenJDK's java.awt.FlowLayout
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
 * Emulator for {@link java.awt.FlowLayout}. Under D_layout_css_on_content,
 * {@link #layoutContainer} writes
 * {@code display: flex; flex-wrap: wrap; justify-content: …; gap: …} to
 * the container's peer content element via
 * {@link vaadinx.EHelper#applyContainerCss}.
 *
 * <p>Hand-written rather than generator-emitted: the generator's
 * root-class detection assumes Component-hierarchy ancestry and would
 * emit a {@code peer} field, which a LayoutManager doesn't have. Plain
 * LayoutManager (not LayoutManager2) — JDK FlowLayout has no per-child
 * constraint storage; position is purely insertion-order driven.
 */
public class FlowLayout implements CssEmittingLayoutManager, Serializable {

    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int LEADING = 3;
    public static final int TRAILING = 4;

    private int align;
    private int hgap;
    private int vgap;
    private boolean alignOnBaseline;

    public FlowLayout() {
        this(CENTER, 5, 5);
    }

    public FlowLayout(int align) {
        this(align, 5, 5);
    }

    public FlowLayout(int align, int hgap, int vgap) {
        this.align = align;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    public int getAlignment() {
        return align;
    }

    public void setAlignment(int align) {
        // JDK tolerates any int; unknown values render as flex-start in the
        // CSS mapping below. Matching JDK's permissive behavior keeps D_never_fail_on_gaps
        // consistent — setAlignment isn't one of the programming-error
        // paths real Swing rejects. No revalidate here either — Swing's
        // contract is that user code calls container.revalidate() after
        // mutating layout-manager state, and we emulate that quirk rather
        // than silently "fixing" it.
        this.align = align;
    }

    public int getHgap() {
        return hgap;
    }

    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    public int getVgap() {
        return vgap;
    }

    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    public boolean getAlignOnBaseline() {
        return alignOnBaseline;
    }

    public void setAlignOnBaseline(boolean alignOnBaseline) {
        // Field-only round-trip: CSS flex has no clean equivalent for
        // FlowLayout's per-row baseline alignment (CSS {@code align-items:
        // baseline} applies across the whole flex container, not per
        // wrapped row). R_layouts_close_enough — close-enough; user code that reads back this
        // flag stays honest.
        this.alignOnBaseline = alignOnBaseline;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // JDK FlowLayout does nothing here — the layout has no per-child
        // constraint storage. Matching so Container.addImpl's
        // LayoutManager-branch call is a harmless no-op.
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // Same as addLayoutComponent — no per-child state to clear.
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // No pixel layout is planned (D_pixel_layout_not_planned): we don't compute real pixel
        // numbers. Dummy 1×1 keeps user code that reads getPreferredSize
        // right after layout installation from getting a zero that some
        // legacy code treats as "not laid out yet."
        return new Dimension(1, 1);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(1, 1);
    }

    @Override
    public Map<String, String> childCss(Container parent, Component child) {
        // The one layout whose answer *is* the preferred size, on both axes:
        // JDK's layoutContainer sizes every child with c.setSize(c.getPreferredSize())
        // and then only positions it. So the policy is "pref stands" — written
        // explicitly rather than omitted, both to clear a previous layout's policy
        // after setLayout and to shield the child from an ancestor's inherited
        // value (D_layout_owns_child_sizing).
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(false, false);
    }

    @Override
    public Map<String, String> containerCss(Container parent) {
        // D_layout_css_on_content: container-only — FlowLayout has no per-child state
        // beyond the sizing policy above. Insertion order in the child list is the layout order; the
        // browser's flex engine handles wrapping and justification. Body in
        // com.vaadin.swingbridge.surrogates.util.LayoutCss.flowLayoutCss so the surrogate-side
        // ContainerMixin shares the same builder.
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.flowLayoutCss(align, hgap, vgap);
    }

    @Override
    public String toString() {
        String alignName = switch (align) {
            case LEFT -> "left";
            case CENTER -> "center";
            case RIGHT -> "right";
            case LEADING -> "leading";
            case TRAILING -> "trailing";
            default -> "";
        };
        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + ",align=" + alignName + "]";
    }
}
