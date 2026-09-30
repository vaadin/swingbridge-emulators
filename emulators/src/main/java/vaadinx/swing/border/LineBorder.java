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
 * This file is derived from OpenJDK's javax.swing.border.LineBorder
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
 * Port of {@link javax.swing.border.LineBorder}. Solid rectangular border
 * around the component. Optional rounded corners.
 *
 * <p>CSS mapping: {@code border: <thickness>px solid <color>} plus a
 * border-radius when rounded — close-enough to Swing's paint output for
 * R_best_effort_behaviour best-effort. Thick line borders render identically in both stacks;
 * the difference is inner-pixel-perfect anti-aliasing, which R_layouts_close_enough carves out.
 */
public class LineBorder extends AbstractBorder {

    protected final java.awt.Color lineColor;
    protected final int thickness;
    protected final boolean roundedCorners;

    public LineBorder(java.awt.Color color) {
        this(color, 1, false);
    }

    public LineBorder(java.awt.Color color, int thickness) {
        this(color, thickness, false);
    }

    public LineBorder(java.awt.Color color, int thickness, boolean roundedCorners) {
        // JDK doesn't null-check color here; paintBorder silently uses
        // the previous graphics color on null. We match for API parity —
        // applyCss below handles null by using CSS 'currentcolor'.
        this.lineColor = color;
        this.thickness = thickness;
        this.roundedCorners = roundedCorners;
    }

    public java.awt.Color getLineColor() {
        return lineColor;
    }

    public int getThickness() {
        return thickness;
    }

    public boolean getRoundedCorners() {
        return roundedCorners;
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        return new java.awt.Insets(thickness, thickness, thickness, thickness);
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c, java.awt.Insets target) {
        target.top = thickness;
        target.left = thickness;
        target.bottom = thickness;
        target.right = thickness;
        return target;
    }

    @Override
    public boolean isBorderOpaque() {
        // Solid colour line ⇒ opaque (JDK matches unless color is null).
        return !roundedCorners;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        com.vaadin.flow.dom.Style style = element.getStyle();
        String css = thickness + "px solid "
                + (lineColor == null ? "currentcolor" : com.vaadin.swingbridge.surrogates.util.CssConvert.toCss(lineColor));
        style.set("border", css);
        if (roundedCorners) {
            // JDK's rounded LineBorder uses a corner radius twice the
            // thickness in its paintBorder path — matches what users
            // expect when they enable the flag.
            style.set("border-radius", (thickness * 2) + "px");
        }
    }

    // Shared black/grey instances (field-populated on first request) — JDK
    // pattern to avoid allocation on the common "just make it a default
    // line" calls. Lazy since most migrated apps pick explicit colors.
    private static LineBorder blackLine;
    private static LineBorder grayLine;

    public static LineBorder createBlackLineBorder() {
        if (blackLine == null) blackLine = new LineBorder(java.awt.Color.BLACK, 1);
        return blackLine;
    }

    public static LineBorder createGrayLineBorder() {
        if (grayLine == null) grayLine = new LineBorder(java.awt.Color.GRAY, 1);
        return grayLine;
    }
}
