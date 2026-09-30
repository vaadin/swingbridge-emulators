/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.LayoutStyle
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

/**
 * Port of {@link javax.swing.LayoutStyle} (D_grouplayout). Required for the import-swap:
 * NetBeans GUI-builder code references {@code javax.swing.LayoutStyle.ComponentPlacement}
 * inside {@code GroupLayout} gap calls, which swaps to
 * {@code vaadinx.swing.LayoutStyle.ComponentPlacement} — so this class must
 * exist for such code to compile.
 *
 * <p><b>Only the {@link ComponentPlacement} enum is load-bearing.</b>
 * {@link GroupLayout} consumes it directly (mapping {@code RELATED} /
 * {@code UNRELATED} to fixed pixel gaps in {@code addPreferredGap}), so it does
 * not go through {@link #getPreferredGap}/{@link #getContainerGap} dispatch. The
 * gap-computation methods are provided for API completeness and return R_layouts_close_enough
 * defaults via {@link vaadinx.EHelper#onUnimplemented} — no migrated code we
 * target calls them (NetBeans emits the enum, not the LayoutStyle instance).
 *
 * <p>Kept concrete (JDK's is abstract) so {@link #getInstance()} returns a
 * usable object without a separate default subclass; non-final to preserve the
 * JDK's extensible shape per R_swing_is_truth.
 */
public class LayoutStyle {

    /** Port of {@code javax.swing.LayoutStyle.ComponentPlacement}. */
    public enum ComponentPlacement {
        RELATED, UNRELATED, INDENT
    }

    private static LayoutStyle instance = new LayoutStyle();

    public static LayoutStyle getInstance() {
        return instance;
    }

    public static void setInstance(LayoutStyle style) {
        instance = (style == null) ? new LayoutStyle() : style;
    }

    /**
     * Preferred gap between two components. Not on the migrated-code path
     * (NetBeans hands {@link ComponentPlacement} to {@code GroupLayout} directly);
     * returns {@code GroupLayout}'s RELATED default (6px) as a sensible R_layouts_close_enough answer.
     */
    public int getPreferredGap(JComponent component1, JComponent component2,
                               ComponentPlacement type, int position,
                               vaadinx.awt.Container parent) {
        vaadinx.EHelper.onUnimplemented("vaadinx.swing.LayoutStyle", "getPreferredGap",
                type, position);
        return type == ComponentPlacement.UNRELATED ? 12 : 6;
    }

    /** Preferred gap between a component and the container edge. R_layouts_close_enough default (10px). */
    public int getContainerGap(JComponent component, int position, vaadinx.awt.Container parent) {
        vaadinx.EHelper.onUnimplemented("vaadinx.swing.LayoutStyle", "getContainerGap", position);
        return 10;
    }
}
