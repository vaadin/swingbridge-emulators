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
 * This file is derived from OpenJDK's javax.swing.border.MatteBorder
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
 * Port of {@link javax.swing.border.MatteBorder}. Per-side coloured
 * border (LineBorder with independent widths on each side) or
 * tile-filled border (via Icon).
 *
 * <p>The (top, left, bottom, right, Color) ctor is the common case and
 * maps cleanly to per-side CSS {@code border-top/right/bottom/left}. The
 * Icon form has no simple CSS analogue — our ctor accepts it for API
 * parity but logs a WARN on install and falls back to a plain-insets
 * EmptyBorder-equivalent rendering until icon rendering lands.
 */
public class MatteBorder extends EmptyBorder {

    protected final java.awt.Color color;
    protected final vaadinx.swing.Icon tileIcon;

    public MatteBorder(int top, int left, int bottom, int right, java.awt.Color matteColor) {
        super(top, left, bottom, right);
        this.color = matteColor;
        this.tileIcon = null;
    }

    public MatteBorder(java.awt.Insets borderInsets, java.awt.Color matteColor) {
        super(borderInsets);
        this.color = matteColor;
        this.tileIcon = null;
    }

    public MatteBorder(int top, int left, int bottom, int right, vaadinx.swing.Icon tileIcon) {
        super(top, left, bottom, right);
        this.color = null;
        this.tileIcon = tileIcon;
    }

    public MatteBorder(java.awt.Insets borderInsets, vaadinx.swing.Icon tileIcon) {
        super(borderInsets);
        this.color = null;
        this.tileIcon = tileIcon;
    }

    public MatteBorder(vaadinx.swing.Icon tileIcon) {
        // JDK default: uses the icon's intrinsic size as insets. Our
        // Icon.getIconWidth/Height may return 0 if not set; match JDK's
        // "zero insets on null icon" fallback.
        super(tileIcon == null ? 0 : tileIcon.getIconHeight(),
                tileIcon == null ? 0 : tileIcon.getIconWidth(),
                tileIcon == null ? 0 : tileIcon.getIconHeight(),
                tileIcon == null ? 0 : tileIcon.getIconWidth());
        this.color = null;
        this.tileIcon = tileIcon;
    }

    public java.awt.Color getMatteColor() {
        return color;
    }

    public vaadinx.swing.Icon getTileIcon() {
        return tileIcon;
    }

    @Override
    public boolean isBorderOpaque() {
        // JDK: opaque when backed by a color; icon-tile borders may have
        // transparent pixels, so non-opaque in that branch.
        return color != null;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        if (tileIcon != null) {
            // Tile rendering needs the icon rasterised to a background-
            // image; covered by future icon-rendering work. Until
            // then: log once, use the icon insets so layouts still
            // reserve space.
            vaadinx.EHelper.onUnimplemented("MatteBorder", "tile icon rendering", tileIcon);
            return;
        }
        if (color == null) return;  // JDK: null color ⇒ no paint
        String cssColor = com.vaadin.swingbridge.surrogates.util.CssConvert.toCss(color);
        com.vaadin.flow.dom.Style style = element.getStyle();
        // Only emit sides with non-zero thickness so we don't force
        // 'border-top-style: none' declarations where the user expected
        // no edge at all — cleaner DOM inspector output. Side widths are
        // EmptyBorder's inherited protected ints, as in the JDK.
        if (top > 0) style.set("border-top", top + "px solid " + cssColor);
        if (right > 0) style.set("border-right", right + "px solid " + cssColor);
        if (bottom > 0) style.set("border-bottom", bottom + "px solid " + cssColor);
        if (left > 0) style.set("border-left", left + "px solid " + cssColor);
    }
}
