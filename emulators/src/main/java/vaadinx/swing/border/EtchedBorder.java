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
 * This file is derived from OpenJDK's javax.swing.border.EtchedBorder
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
 * Port of {@link javax.swing.border.EtchedBorder}. Low-relief etched
 * border (groove or ridge).
 *
 * <p>CSS {@code border-style: groove} and {@code ridge} render the same
 * visual pattern — etched into or raised from the surface. Browsers
 * synthesise the highlight/shadow pair from the single border-color.
 * R_best_effort_behaviour best-effort: the hand-picked {@code highlight} / {@code shadow}
 * constructor fields are stored for round-trip but don't influence the
 * rendered colour (the browser picks).
 */
public class EtchedBorder extends AbstractBorder {

    public static final int RAISED = 0;
    public static final int LOWERED = 1;

    protected int etchType;
    protected java.awt.Color highlight;
    protected java.awt.Color shadow;

    public EtchedBorder() {
        this(LOWERED, null, null);
    }

    public EtchedBorder(int etchType) {
        this(etchType, null, null);
    }

    public EtchedBorder(java.awt.Color highlight, java.awt.Color shadow) {
        this(LOWERED, highlight, shadow);
    }

    public EtchedBorder(int etchType, java.awt.Color highlight, java.awt.Color shadow) {
        if (etchType != RAISED && etchType != LOWERED) {
            throw new IllegalArgumentException("invalid etch type");
        }
        this.etchType = etchType;
        this.highlight = highlight;
        this.shadow = shadow;
    }

    public int getEtchType() {
        return etchType;
    }

    public java.awt.Color getHighlightColor(vaadinx.awt.Component c) {
        return highlight;
    }

    public java.awt.Color getHighlightColor() {
        return highlight;
    }

    public java.awt.Color getShadowColor(vaadinx.awt.Component c) {
        return shadow;
    }

    public java.awt.Color getShadowColor() {
        return shadow;
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        // JDK: 2px on every side.
        return new java.awt.Insets(2, 2, 2, 2);
    }

    @Override
    public boolean isBorderOpaque() {
        return true;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        // groove ≈ LOWERED etched look; ridge ≈ RAISED.
        String style = (etchType == RAISED) ? "ridge" : "groove";
        String color = (shadow != null) ? com.vaadin.swingbridge.surrogates.util.CssConvert.toCss(shadow) : "#808080";
        element.getStyle().set("border", "2px " + style + " " + color);
    }
}
