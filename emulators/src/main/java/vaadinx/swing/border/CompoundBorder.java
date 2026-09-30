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
 * This file is derived from OpenJDK's javax.swing.border.CompoundBorder
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
 * Port of {@link javax.swing.border.CompoundBorder}. Stacks an "outside"
 * and "inside" border so they paint concentrically.
 *
 * <p>The common migration pattern is
 * {@code CompoundBorder(LineBorder, EmptyBorder)} — outer frame + inner
 * padding — which maps cleanly to CSS: outer's {@code border} plus
 * inner's insets as additional {@code padding}. That shape works
 * pixel-for-pixel in the browser.
 *
 * <p>{@code CompoundBorder(LineBorder, LineBorder)} (two concentric
 * stroked frames) has no single-element CSS equivalent; we render the
 * outer line correctly and add the inner's insets as padding so layouts
 * reserve the right space — the inner line itself doesn't render. R_best_effort_behaviour
 * accepted gap; revisit if migration targets actually use stacked lines.
 */
public class CompoundBorder extends AbstractBorder {

    protected final Border outsideBorder;
    protected final Border insideBorder;

    public CompoundBorder() {
        // JDK allows both-null to mean "no-op compound"; paintBorder and
        // getBorderInsets then return zeros. Matches.
        this(null, null);
    }

    public CompoundBorder(Border outsideBorder, Border insideBorder) {
        this.outsideBorder = outsideBorder;
        this.insideBorder = insideBorder;
    }

    public Border getOutsideBorder() {
        return outsideBorder;
    }

    public Border getInsideBorder() {
        return insideBorder;
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        java.awt.Insets out = new java.awt.Insets(0, 0, 0, 0);
        if (outsideBorder != null) {
            java.awt.Insets from = outsideBorder.getBorderInsets(c);
            out.top += from.top;
            out.left += from.left;
            out.bottom += from.bottom;
            out.right += from.right;
        }
        if (insideBorder != null) {
            java.awt.Insets from = insideBorder.getBorderInsets(c);
            out.top += from.top;
            out.left += from.left;
            out.bottom += from.bottom;
            out.right += from.right;
        }
        return out;
    }

    @Override
    public boolean isBorderOpaque() {
        // JDK: opaque iff both sides are opaque (nulls count as opaque
        // since they render nothing).
        boolean outsideOpaque = outsideBorder == null || outsideBorder.isBorderOpaque();
        boolean insideOpaque = insideBorder == null || insideBorder.isBorderOpaque();
        return outsideOpaque && insideOpaque;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        // Draw the outside border via its own CSS, then add the inside's
        // insets as padding. The inside's own CSS (border, etc.) is
        // dropped — this is the "outer draws, inner pads" simplification
        // noted in the class javadoc. Two AbstractBorder outside children
        // compose via this same recursion.
        if (outsideBorder instanceof AbstractBorder ab) {
            ab.applyCss(element);
        }
        if (insideBorder != null) {
            java.awt.Insets innerInsets = insideBorder.getBorderInsets(null);
            if (innerInsets.top > 0 || innerInsets.left > 0
                    || innerInsets.bottom > 0 || innerInsets.right > 0) {
                // Read the element's current padding (written by the
                // outer border if it was an EmptyBorder) and add to it,
                // rather than overwrite. Real Swing paints the inside
                // border with the outer border's insets as offset — our
                // CSS analogue is cumulative padding.
                com.vaadin.flow.dom.Style style = element.getStyle();
                String existing = style.get("padding");
                int outerTop = 0, outerRight = 0, outerBottom = 0, outerLeft = 0;
                if (existing != null) {
                    // Parse " Npx Npx Npx Npx " (what EmptyBorder emits).
                    // Defensive: a user-set custom padding value falls
                    // through to "treat as 0" — the inner insets still
                    // apply on top, but the user's string is replaced.
                    int[] parsed = parsePxQuad(existing);
                    if (parsed != null) {
                        outerTop = parsed[0];
                        outerRight = parsed[1];
                        outerBottom = parsed[2];
                        outerLeft = parsed[3];
                    }
                }
                style.set("padding",
                        (outerTop + innerInsets.top) + "px " +
                                (outerRight + innerInsets.right) + "px " +
                                (outerBottom + innerInsets.bottom) + "px " +
                                (outerLeft + innerInsets.left) + "px");
            }
        }
    }

    /**
     * Parse a 4-value {@code px} padding string into an int[4]
     * (top/right/bottom/left). Returns null for anything we didn't write
     * ourselves — e.g. {@code "10px"} single-value, em units, auto — so
     * the caller can fall through to treating outer padding as zero.
     */
    private static int[] parsePxQuad(String s) {
        String[] parts = s.trim().split("\\s+");
        if (parts.length != 4) return null;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].endsWith("px")) return null;
            try {
                out[i] = Integer.parseInt(parts[i].substring(0, parts[i].length() - 2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return out;
    }
}
