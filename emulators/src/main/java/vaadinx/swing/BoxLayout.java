/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.BoxLayout
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

import java.awt.AWTError;
import java.awt.Dimension;
import java.io.Serializable;
import java.util.Map;

/**
 * Emulator for {@link javax.swing.BoxLayout}. Joins
 * {@link vaadinx.awt.FlowLayout} / {@link vaadinx.awt.BorderLayout} as the
 * third built-in layout manager. Under D_layout_css_on_content,
 * {@link #layoutContainer} writes
 * {@code display: flex; flex-direction: row|column} to the container's
 * peer content element via
 * {@link vaadinx.EHelper#applyContainerCss}.
 *
 * <p>Hand-written rather than generator-emitted: the generator's
 * root-class detection assumes Component-hierarchy ancestry and would
 * emit a {@code peer} field, which a LayoutManager doesn't have. Lives
 * in {@code vaadinx.swing} (not {@code vaadinx.awt}) because the JDK
 * class is {@code javax.swing.BoxLayout}, not {@code java.awt.BoxLayout}.
 *
 * <p><b>RTL caveat.</b> JDK BoxLayout flips {@code LINE_AXIS} between row
 * and row-reverse based on the container's
 * {@link java.awt.ComponentOrientation}; {@code PAGE_AXIS} flips for
 * vertical-text orientations. CSS {@code flex-direction} isn't
 * orientation-aware, and tracking orientation at {@code layoutContainer}
 * time would require rebuilding the dispatch path. We pin the LTR
 * mapping ({@code X_AXIS}/{@code LINE_AXIS} → {@code row},
 * {@code Y_AXIS}/{@code PAGE_AXIS} → {@code column}) per R_layouts_close_enough — same shape
 * as BorderLayout's LINE_START/LINE_END caveat.
 *
 * <p><b>Per-child sizing.</b> JDK BoxLayout reads each child's
 * {@code getMinimumSize} / {@code getPreferredSize} / {@code getMaximumSize}
 * and {@code getAlignmentX} / {@code getAlignmentY} to distribute space.
 * Vaadin's flex defaults ({@code flex: 0 0 auto}) give children their
 * intrinsic content size with no growth — close-enough for the canonical
 * Swing pattern (vertical row stack with {@link Box#createVerticalStrut}
 * / {@link Box#createVerticalGlue} carrying spacing and expansion).
 * Setting {@code Component.alignmentX/Y} doesn't write a per-child
 * {@code align-self} today: {@code JComponent.setAlignmentX/Y} is
 * {@code onNoop} and the getters always return {@code 0.5f}, so any write
 * would be {@code align-self: center} — flex's default, so adds DOM noise
 * with zero information. R_layouts_close_enough; revisit when alignment becomes a stored
 * property.
 *
 * <p><b>Bound to one container.</b> Like JDK BoxLayout, instances are
 * tied to the {@link Container} passed at construction time:
 * {@link #invalidateLayout(Container)} (and
 * {@link #layoutContainer(Container)}) throw {@link AWTError} when called
 * with a different container, matching Swing per R_match_swing_errors.
 */
public class BoxLayout implements LayoutManager2, CssEmittingLayoutManager, Serializable {

    public static final int X_AXIS = 0;
    public static final int Y_AXIS = 1;
    public static final int LINE_AXIS = 2;
    public static final int PAGE_AXIS = 3;

    private final Container target;
    private final int axis;

    public BoxLayout(Container target, int axis) {
        // JDK throws AWTError on invalid axis. Per R_match_swing_errors we match — throwing
        // the same exception type at the same input boundary keeps
        // migrated code's failure mode identical.
        if (axis != X_AXIS && axis != Y_AXIS && axis != LINE_AXIS && axis != PAGE_AXIS) {
            throw new AWTError("Invalid axis");
        }
        this.target = target;
        this.axis = axis;
    }

    public final int getAxis() {
        return axis;
    }

    public final Container getTarget() {
        return target;
    }

    private void checkContainer(Container c) {
        // JDK BoxLayout's internal guard — both invalidateLayout and
        // layoutContainer go through it. Mirrors JDK exactly per R_match_swing_errors.
        if (this.target != c) {
            throw new AWTError("BoxLayout can't be shared");
        }
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // JDK BoxLayout does nothing — layout reads child sizes/alignments
        // directly at layoutContainer time, no per-child storage.
    }

    @Override
    public void addLayoutComponent(Component comp, Object constraints) {
        // Same as the String form — no constraint storage.
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // No per-child state to clear.
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        // D_pixel_layout_not_planned — dummy 1×1 matches FlowLayout/
        // BorderLayout precedent, keeps user code that reads
        // preferredLayoutSize after install from getting a zero some legacy
        // code treats as "not laid out yet."
        return new Dimension(1, 1);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return new Dimension(1, 1);
    }

    @Override
    public Dimension maximumLayoutSize(Container target) {
        // BorderLayout precedent — Integer.MAX_VALUE so a flexible center
        // child can grow arbitrarily. BoxLayout's real JDK
        // maximumLayoutSize sums child max sizes; we take the same dummy-
        // but-honest stance since we don't enforce max sizes (R_layouts_close_enough).
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container target) {
        // BorderLayout precedent. JDK averages child alignments — too
        // tangled for R_layouts_close_enough, and alignmentX/Y is always 0.5f anyway today
        // (JComponent.setAlignmentX is onNoop).
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container target) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container target) {
        // JDK throws AWTError on container mismatch — match per R_match_swing_errors. No
        // cached layout state to drop otherwise (we don't compute the
        // size hints JDK caches here).
        checkContainer(target);
    }

    @Override
    public Map<String, String> containerCss(Container parent) {
        // checkContainer stays the entry guard (JDK's "can't be shared" contract,
        // R_match_swing_errors) — the framework's default layoutContainer calls containerCss first,
        // so it fires before any CSS is written.
        checkContainer(parent);
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.boxLayoutCss(axis);
    }

    @Override
    public Map<String, String> childCss(Container parent, Component child) {
        // D_layout_owns_child_sizing, split by axis the way JDK's layoutContainer
        // does: the major axis tiles children at their preferred span
        // (calculateTiledPositions), while the cross axis *stretches* each child to
        // min(alloc, child.getMaximumSize()) via calculateAlignedPositions — and an
        // unset maximum is Short.MAX_VALUE (java.awt.Component:2856-2861), so the
        // common child fills the container across.
        //
        // That cross-axis stretch is `align-items: stretch` in boxLayoutCss, which an
        // explicit width defeated until the pref moved behind the variable — so a
        // row panel with a pref width inside a Y_AXIS box now fills the container
        // as it does on the desktop.
        //
        // An explicitly *set* maximumSize would cap the stretch in Swing and does not
        // here: min/max size still write hard CSS, out of this pass' scope.
        boolean row = axis == X_AXIS || axis == LINE_AXIS;
        return com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(!row, row);
    }

    @Override
    public String toString() {
        String axisName = switch (axis) {
            case X_AXIS -> "X_AXIS";
            case Y_AXIS -> "Y_AXIS";
            case LINE_AXIS -> "LINE_AXIS";
            case PAGE_AXIS -> "PAGE_AXIS";
            default -> Integer.toString(axis); // unreachable
        };
        return getClass().getName() + "[axis=" + axisName + ",target=" + target + "]";
    }
}
