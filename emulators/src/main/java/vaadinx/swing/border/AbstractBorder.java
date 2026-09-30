/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.border.AbstractBorder
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
 * Port of {@link javax.swing.border.AbstractBorder} — base class for every
 * concrete border in this package plus the extension point user code
 * subclasses for custom borders.
 *
 * <p>Adds a browser-side {@link #applyCss(com.vaadin.flow.dom.Element)}
 * template method on top of Swing's paint-only contract. Concrete borders
 * override it to write the CSS representation of themselves onto the
 * target element; vaadinx's {@link vaadinx.swing.JComponent#setBorder}
 * calls into that hook, so user code that writes
 * {@code panel.setBorder(new LineBorder(Color.BLACK))} sees the line
 * actually render in the browser.
 *
 * <p>Custom user borders extending {@code AbstractBorder} without
 * overriding {@code applyCss} won't render (R_best_effort_behaviour best-effort) — the base
 * default clears any previously-installed border CSS and writes nothing.
 * {@link #paintBorder} + {@link #getBorderInsets} still work as on real
 * Swing, so server-side code that reads insets or drives paint onto a
 * Graphics sink keeps behaving the same.
 */
public abstract class AbstractBorder implements Border, java.io.Serializable {

    @Override
    public void paintBorder(vaadinx.awt.Component c, java.awt.Graphics g,
                            int x, int y, int width, int height) {
        // Real Swing's AbstractBorder default is no-op — concrete borders
        // override to draw. We never reach a real Graphics sink (R_layouts_close_enough — the
        // browser paints), but keeping the no-op matches Swing's contract
        // so user code calling paintBorder on a buffered Graphics sees
        // the same "nothing drawn for bare AbstractBorder" it would in Swing.
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        // AWT pattern: fill-the-arg variant takes an Insets; the no-arg
        // variant allocates. We don't cache a zero-sized Insets (trivial
        // allocation); mirror JDK shape.
        return new java.awt.Insets(0, 0, 0, 0);
    }

    /**
     * Fill-the-arg variant of getBorderInsets — concrete borders override
     * when they want to avoid the Insets allocation. Default reads
     * {@link #getBorderInsets(vaadinx.awt.Component)} and copies.
     */
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c, java.awt.Insets insets) {
        java.awt.Insets from = getBorderInsets(c);
        insets.top = from.top;
        insets.left = from.left;
        insets.bottom = from.bottom;
        insets.right = from.right;
        return insets;
    }

    @Override
    public boolean isBorderOpaque() {
        // JDK default.
        return false;
    }

    /**
     * Write this border's CSS onto the target element. Called by
     * {@link vaadinx.swing.JComponent#setBorder}. Concrete border classes
     * override to emit the CSS that represents them (padding for
     * EmptyBorder, {@code border} for LineBorder, per-side for MatteBorder,
     * …). The base implementation clears any previously-installed border
     * CSS via {@link #clearBorderCss} and writes nothing, which is the
     * honest behaviour for a user-authored custom AbstractBorder that
     * didn't override this hook.
     *
     * <p>Typical override: {@code clearBorderCss(e); set("border", …);}.
     * Always clear first so a previous border's keys don't linger.
     */
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
    }

    /**
     * Remove every border-related inline style key that a concrete border
     * from this package might have written. Called by both
     * {@link vaadinx.swing.JComponent#setBorder} (when the new border is
     * null or custom) and every concrete border's {@link #applyCss}
     * before it writes its own keys.
     *
     * <p>The whitelist covers the shorthand {@code border},
     * per-side {@code border-top/right/bottom/left} and their
     * width/style/color longhands, {@code border-radius}, and
     * {@code padding} + its four longhands. If a user also wrote one of
     * these inline styles manually, it's cleared too — {@code setBorder}
     * owns these CSS properties on a container.
     */
    public static void clearBorderCss(com.vaadin.flow.dom.Element element) {
        com.vaadin.flow.dom.Style style = element.getStyle();
        for (String key : BORDER_CSS_KEYS) {
            style.remove(key);
        }
    }

    /**
     * Every inline-style key a concrete border from this package might
     * write. Kept in one place so new border impls don't silently leak
     * styles across setBorder swaps.
     */
    private static final String[] BORDER_CSS_KEYS = {
            "border",
            "border-top", "border-right", "border-bottom", "border-left",
            "border-width", "border-style", "border-color",
            "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
            "border-top-style", "border-right-style", "border-bottom-style", "border-left-style",
            "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
            "border-radius",
            "padding", "padding-top", "padding-right", "padding-bottom", "padding-left",
    };
}
