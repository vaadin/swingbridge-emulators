/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.border.TitledBorder
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
 * Port of {@link javax.swing.border.TitledBorder}. Wraps an inner border
 * and adds a title label floating at the top edge.
 *
 * <p>CSS rendering is split in two: the <b>border</b> part lands via the
 * inside border's own {@link AbstractBorder#applyCss} (LineBorder →
 * {@code border}, EmptyBorder → {@code padding}, etc.). The <b>title</b>
 * text is written as {@code data-emul-border-title} on the element, and
 * the {@code ::before} pseudo-element rule is auto-injected once per UI
 * by {@link com.vaadin.swingbridge.surrogates.util.BorderCss#ensureTitledBorderStyleInjected}.
 * The injection is idempotent — sentinel data
 * on the UI gates duplicate writes — and survives even when applyCss is
 * called outside a UI context (no-op then; lands on the next applyCss
 * once a UI is current).
 */
public class TitledBorder extends AbstractBorder {

    /** Title positioning constants — identical values to JDK. */
    public static final int DEFAULT_POSITION = 0;
    public static final int ABOVE_TOP = 1;
    public static final int TOP = 2;
    public static final int BELOW_TOP = 3;
    public static final int ABOVE_BOTTOM = 4;
    public static final int BOTTOM = 5;
    public static final int BELOW_BOTTOM = 6;

    /** Title justification constants — identical values to JDK. */
    public static final int DEFAULT_JUSTIFICATION = 0;
    public static final int LEFT = 1;
    public static final int CENTER = 2;
    public static final int RIGHT = 3;
    public static final int LEADING = 4;
    public static final int TRAILING = 5;

    /** Internal padding used by JDK between the title and border line. */
    protected static final int EDGE_SPACING = 2;
    protected static final int TEXT_SPACING = 2;
    protected static final int TEXT_INSET_H = 5;

    protected String title;
    protected Border border;
    protected int titlePosition;
    protected int titleJustification;
    protected java.awt.Font titleFont;
    protected java.awt.Color titleColor;

    public TitledBorder(String title) {
        this(null, title, LEADING, DEFAULT_POSITION, null, null);
    }

    public TitledBorder(Border border) {
        this(border, "", LEADING, DEFAULT_POSITION, null, null);
    }

    public TitledBorder(Border border, String title) {
        this(border, title, LEADING, DEFAULT_POSITION, null, null);
    }

    public TitledBorder(Border border, String title, int titleJustification, int titlePosition) {
        this(border, title, titleJustification, titlePosition, null, null);
    }

    public TitledBorder(Border border, String title, int titleJustification, int titlePosition,
                        java.awt.Font titleFont) {
        this(border, title, titleJustification, titlePosition, titleFont, null);
    }

    public TitledBorder(Border border, String title, int titleJustification, int titlePosition,
                        java.awt.Font titleFont, java.awt.Color titleColor) {
        this.title = title;
        this.border = border;
        setTitleJustification(titleJustification);
        setTitlePosition(titlePosition);
        this.titleFont = titleFont;
        this.titleColor = titleColor;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Border getBorder() {
        return border;
    }

    public void setBorder(Border border) {
        this.border = border;
    }

    public int getTitlePosition() {
        return titlePosition;
    }

    public void setTitlePosition(int titlePosition) {
        // JDK validates per the constants above; invalid throws IAE.
        // We match per R_match_swing_errors/D_never_fail_on_gaps.
        if (titlePosition < DEFAULT_POSITION || titlePosition > BELOW_BOTTOM) {
            throw new IllegalArgumentException(titlePosition + " is not a valid title position.");
        }
        this.titlePosition = titlePosition;
    }

    public int getTitleJustification() {
        return titleJustification;
    }

    public void setTitleJustification(int titleJustification) {
        if (titleJustification < DEFAULT_JUSTIFICATION || titleJustification > TRAILING) {
            throw new IllegalArgumentException(titleJustification + " is not a valid title justification.");
        }
        this.titleJustification = titleJustification;
    }

    public java.awt.Font getTitleFont() {
        return titleFont;
    }

    public void setTitleFont(java.awt.Font titleFont) {
        this.titleFont = titleFont;
    }

    public java.awt.Color getTitleColor() {
        return titleColor;
    }

    public void setTitleColor(java.awt.Color titleColor) {
        this.titleColor = titleColor;
    }

    @Override
    public java.awt.Insets getBorderInsets(vaadinx.awt.Component c) {
        // JDK composes the inside border's insets with a top allowance
        // for the title text. Without a real font metrics pipeline (R_layouts_close_enough)
        // we can't compute the title height exactly; use a reasonable
        // default (16px) plus the inner border's insets.
        java.awt.Insets inner = (border == null)
                ? new java.awt.Insets(0, 0, 0, 0)
                : border.getBorderInsets(c);
        int titleHeight = 16;
        return new java.awt.Insets(inner.top + titleHeight,
                inner.left, inner.bottom, inner.right);
    }

    @Override
    public boolean isBorderOpaque() {
        // JDK: false — the title region pokes through the border.
        return false;
    }

    @Override
    public void applyCss(com.vaadin.flow.dom.Element element) {
        clearBorderCss(element);
        // Inner border's CSS (border / padding / radius) goes on the
        // element directly. Default to a 1px solid line if no inner
        // border was given — matches JDK's default TitledBorder shape.
        if (border instanceof AbstractBorder ab) {
            ab.applyCss(element);
        } else if (border == null) {
            element.getStyle().set("border", "1px solid currentcolor");
        }
        // Auto-inject the ::before pseudo-element rule once per UI.
        // The rule's positioning needs the host
        // to be position-relative, which the rule itself sets.
        com.vaadin.swingbridge.surrogates.util.BorderCss.ensureTitledBorderStyleInjected(
                com.vaadin.flow.component.UI.getCurrent());
        // The attribute carries the text for the rule's attr() read.
        // Empty string when the title is null; absent-attr would also
        // work but an empty attribute documents intent.
        element.setAttribute("data-emul-border-title",
                title == null ? "" : title);
    }
}
