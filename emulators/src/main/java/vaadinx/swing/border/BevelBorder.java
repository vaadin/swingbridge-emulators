/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.border.BevelBorder
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
 * Port of {@link javax.swing.border.BevelBorder}. Two-pixel raised or
 * lowered bevel around the component.
 *
 * <p>CSS has direct {@code border-style: inset} and {@code outset}
 * keywords that render the same visual cue the Swing bevel draws — the
 * browser automatically derives lighter/darker shades from the border
 * color, so we don't need to separately wire the highlight/shadow
 * colours. Best-effort per R_best_effort_behaviour; exact highlight/shadow control is
 * dropped (fields stored for round-trip).
 */
public class BevelBorder extends AbstractBorder {

    public static final int RAISED = 0;
    public static final int LOWERED = 1;

    protected int bevelType;
    protected java.awt.Color highlightOuter;
    protected java.awt.Color highlightInner;
    protected java.awt.Color shadowInner;
    protected java.awt.Color shadowOuter;

    public BevelBorder(int bevelType) {
        this(bevelType, null, null, null, null);
    }

    public BevelBorder(int bevelType, java.awt.Color highlight, java.awt.Color shadow) {
        this(bevelType, highlight, null, null, shadow);
    }

    public BevelBorder(int bevelType, java.awt.Color highlightOuterColor,
                       java.awt.Color highlightInnerColor,
                       java.awt.Color shadowOuterColor,
                       java.awt.Color shadowInnerColor) {
        if (bevelType != RAISED && bevelType != LOWERED) {
            throw new IllegalArgumentException("invalid bevel type");
        }
        this.bevelType = bevelType;
        this.highlightOuter = highlightOuterColor;
        this.highlightInner = highlightInnerColor;
        this.shadowInner = shadowInnerColor;
        this.shadowOuter = shadowOuterColor;
    }

    public int getBevelType() {
        return bevelType;
    }

    public java.awt.Color getHighlightOuterColor(vaadinx.awt.Component c) {
        return highlightOuter;
    }

    public java.awt.Color getHighlightInnerColor(vaadinx.awt.Component c) {
        return highlightInner;
    }

    public java.awt.Color getShadowOuterColor(vaadinx.awt.Component c) {
        return shadowOuter;
    }

    public java.awt.Color getShadowInnerColor(vaadinx.awt.Component c) {
        return shadowInner;
    }

    public java.awt.Color getHighlightOuterColor() {
        return highlightOuter;
    }

    public java.awt.Color getHighlightInnerColor() {
        return highlightInner;
    }

    public java.awt.Color getShadowOuterColor() {
        return shadowOuter;
    }

    public java.awt.Color getShadowInnerColor() {
        return shadowInner;
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        // JDK's BevelBorder is always 2px on every side.
        return new java.awt.Insets(2, 2, 2, 2);
    }

    @Override
    public boolean isBorderOpaque() {
        return true;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        // border-style: inset ≈ LOWERED; outset ≈ RAISED. Browsers
        // compute highlight/shadow from the single border-color (or
        // default to the current text color). Picking 2px matches
        // JDK's 2px bevel insets.
        String style = (bevelType == RAISED) ? "outset" : "inset";
        // Use the shadow color if provided, otherwise fall back to a
        // default gray so the bevel is visible.
        String color = (shadowOuter != null) ? com.vaadin.swingbridge.surrogates.util.CssConvert.toCss(shadowOuter) : "#808080";
        element.getStyle().set("border", "2px " + style + " " + color);
    }
}
