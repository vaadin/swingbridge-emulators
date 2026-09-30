/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.tree.DefaultTreeCellRenderer
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.tree;

import vaadinx.swing.Icon;
import vaadinx.swing.JLabel;

/**
 * Port of {@link javax.swing.tree.DefaultTreeCellRenderer}. Extends
 * {@link vaadinx.swing.JLabel} so its peer is the SJLabel surrogate (a real
 * Vaadin Component), satisfying the D_jtree_renderer_registry bridge's "return a Component-shaped
 * emulator" requirement — exactly
 * {@link vaadinx.swing.table.DefaultTableCellRenderer}'s shape (D_jtable_type_ports).
 *
 * <p>{@code getTreeCellRendererComponent} routes the value through
 * {@code tree.convertValueToText(...)} — the JDK-overridable formatting hook
 * — and picks the leaf / open / closed {@link Icon} for the JLabel icon slot
 * (D_icon_rendering Path 1: {@code ImageIcon} renders through the surrogate's
 * Vaadin-Image bridge). Icons default to {@code null}: the JDK defaults come
 * from UIManager / L&amp;F, which is dropped per R_match_swing_errors sub-bucket (b), so a
 * fresh renderer shows text only until the app installs its own icons.
 *
 * <p>Selection / focus colour chrome ({@code setBackgroundSelectionColor}
 * et al.) <b>stores and round-trips</b>; only the rendered effect is declined,
 * because the Vaadin theme owns a Grid's row-selection styling and an inline-CSS
 * shadow would not reach the rendered snapshot anyway. Keeping the values is
 * R_decline_effect_only: the effect is droppable, the state never was. JDK's
 * {@code DefaultTreeCellRenderer} also overrides the
 * paint/validate family as performance no-ops; our paint pipeline doesn't
 * run (R_layouts_close_enough), so those overrides are omitted rather than replicated.
 */
public class DefaultTreeCellRenderer extends JLabel implements TreeCellRenderer {

    protected Icon leafIcon;
    protected Icon openIcon;
    protected Icon closedIcon;

    public DefaultTreeCellRenderer() {
        super();
    }

    // --- Icon slots (ride the JLabel icon at render time; D_jtree_type_ports) ---

    /** Icon shown for leaf nodes. {@code null} (the default) renders no icon. */
    public void setLeafIcon(Icon newIcon) {
        leafIcon = newIcon;
    }

    public Icon getLeafIcon() {
        return leafIcon;
    }

    /** Icon shown for expanded non-leaf nodes. {@code null} renders no icon. */
    public void setOpenIcon(Icon newIcon) {
        openIcon = newIcon;
    }

    public Icon getOpenIcon() {
        return openIcon;
    }

    /** Icon shown for collapsed non-leaf nodes. {@code null} renders no icon. */
    public void setClosedIcon(Icon newIcon) {
        closedIcon = newIcon;
    }

    public Icon getClosedIcon() {
        return closedIcon;
    }

    /**
     * JDK sources these from UIManager; L&amp;F dispatch is dropped per R_match_swing_errors
     * sub-bucket (b), so the "default" icons are honestly {@code null}.
     */
    public Icon getDefaultLeafIcon() {
        return null;
    }

    /** See {@link #getDefaultLeafIcon()}. */
    public Icon getDefaultOpenIcon() {
        return null;
    }

    /** See {@link #getDefaultLeafIcon()}. */
    public Icon getDefaultClosedIcon() {
        return null;
    }

    // --- Selection / focus colour chrome ---
    //
    // Five plain fields, as in the JDK: the setters store, the getters read
    // back, and nothing fires (measured against JDK 25 — these post no events).
    // Only the *rendering* is declined, because the Vaadin theme owns a Grid's
    // selection chrome; the values themselves cost nothing to keep and are what
    // migrated code reads, so dropping them was the R_decline_effect_only
    // violation this restores. Defaults are Metal's, installed the way
    // LookAndFeelDefaults installs a component's — a ColorUIResource, so the
    // marker still tells a renderer these are defaults it may substitute.

    protected java.awt.Color textSelectionColor = new javax.swing.plaf.ColorUIResource(51, 51, 51);
    protected java.awt.Color textNonSelectionColor = new javax.swing.plaf.ColorUIResource(51, 51, 51);
    protected java.awt.Color backgroundSelectionColor =
            new javax.swing.plaf.ColorUIResource(184, 207, 229);
    protected java.awt.Color backgroundNonSelectionColor =
            new javax.swing.plaf.ColorUIResource(255, 255, 255);
    protected java.awt.Color borderSelectionColor =
            new javax.swing.plaf.ColorUIResource(99, 130, 191);

    /**
     * Store the colour selected text paints in. The value round-trips; the
     * rendered effect is declined, since a Grid's selection chrome is the
     * theme's ([D_theme_is_lookandfeel](../../../../../../emulators/decisions.md#D_theme_is_lookandfeel)).
     *
     * <p>{@code null} is stored as null and read back as null — the JDK keeps no
     * fallback here, unlike {@code JTabbedPane}'s per-tab colours.
     */
    public void setTextSelectionColor(java.awt.Color newColor) {
        textSelectionColor = newColor;
    }

    public java.awt.Color getTextSelectionColor() {
        return textSelectionColor;
    }

    /** @see #setTextSelectionColor */
    public void setTextNonSelectionColor(java.awt.Color newColor) {
        textNonSelectionColor = newColor;
    }

    public java.awt.Color getTextNonSelectionColor() {
        return textNonSelectionColor;
    }

    /** @see #setTextSelectionColor */
    public void setBackgroundSelectionColor(java.awt.Color newColor) {
        backgroundSelectionColor = newColor;
    }

    public java.awt.Color getBackgroundSelectionColor() {
        return backgroundSelectionColor;
    }

    /** @see #setTextSelectionColor */
    public void setBackgroundNonSelectionColor(java.awt.Color newColor) {
        backgroundNonSelectionColor = newColor;
    }

    public java.awt.Color getBackgroundNonSelectionColor() {
        return backgroundNonSelectionColor;
    }

    /** @see #setTextSelectionColor */
    public void setBorderSelectionColor(java.awt.Color newColor) {
        borderSelectionColor = newColor;
    }

    public java.awt.Color getBorderSelectionColor() {
        return borderSelectionColor;
    }

    // --- Render hook ---

    // Last-render state, assigned per call exactly where the JDK assigns it
    // (D_instance_field_surface) — a subclassed renderer's paint-era code reads
    // these after calling super.getTreeCellRendererComponent.
    protected boolean selected;
    protected boolean hasFocus;

    @Override
    public vaadinx.awt.Component getTreeCellRendererComponent(vaadinx.swing.JTree tree, Object value,
                                                              boolean selected, boolean expanded,
                                                              boolean leaf, int row, boolean hasFocus) {
        // JDK's body: text from tree.convertValueToText (the overridable
        // formatting hook), icon from the leaf/open/closed slot, then the
        // selection chrome — whose values we now keep but whose painting stays
        // the theme's (D_theme_is_lookandfeel).
        this.hasFocus = hasFocus;
        String text = (tree != null)
                ? tree.convertValueToText(value, selected, expanded, leaf, row, hasFocus)
                : (value == null ? "" : String.valueOf(value));
        setText(text);
        this.selected = selected;
        if (leaf) {
            setIcon(getLeafIcon());
        } else if (expanded) {
            setIcon(getOpenIcon());
        } else {
            setIcon(getClosedIcon());
        }
        return this;
    }
}
