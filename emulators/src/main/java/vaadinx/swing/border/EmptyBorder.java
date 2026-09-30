/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.border.EmptyBorder
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.border;

/**
 * Port of {@link javax.swing.border.EmptyBorder}. Invisible border that
 * only adds insets — in CSS terms, pure {@code padding}.
 */
public class EmptyBorder extends AbstractBorder {

    // The JDK stores the four sides as protected ints (not an Insets), and
    // MatteBorder reads them by inheritance — kept that shape per
    // D_instance_field_surface so a migrator's subclass naming them compiles.
    protected int left;
    protected int right;
    protected int top;
    protected int bottom;

    public EmptyBorder(int top, int left, int bottom, int right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    public EmptyBorder(java.awt.Insets borderInsets) {
        // JDK: copies the values out — user code mutating the Insets
        // after construction shouldn't affect us.
        this(borderInsets.top, borderInsets.left, borderInsets.bottom, borderInsets.right);
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        return new java.awt.Insets(top, left, bottom, right);
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c, java.awt.Insets target) {
        target.top = top;
        target.left = left;
        target.bottom = bottom;
        target.right = right;
        return target;
    }

    @Override
    public boolean isBorderOpaque() {
        return false;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        // CSS padding shorthand takes top/right/bottom/left in that
        // order; AWT Insets are top/left/bottom/right, so remap.
        com.vaadin.flow.dom.Style style = element.getStyle();
        style.set("padding",
                top + "px " + right + "px " + bottom + "px " + left + "px");
    }
}
