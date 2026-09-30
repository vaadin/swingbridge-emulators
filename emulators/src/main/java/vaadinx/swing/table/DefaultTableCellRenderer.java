/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.table.DefaultTableCellRenderer
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.table;

import java.io.Serializable;
import vaadinx.swing.JLabel;
import vaadinx.swing.border.Border;
import vaadinx.swing.border.EmptyBorder;

/**
 * Port of {@link javax.swing.table.DefaultTableCellRenderer}. Extends
 * {@link vaadinx.swing.JLabel} so its peer is the SJLabel surrogate (a real
 * Vaadin Component), satisfying the SJTable bridge's "return a
 * Component-shaped emulator" requirement.
 *
 * <p>The body is intentionally minimal: {@code getTableCellRendererComponent}
 * just routes the value through {@link #setValue(Object)} (which writes the
 * cell text via {@link JLabel#setText}). The selection-foreground /
 * background / font / border-on-focus chrome JDK applies via UIManager
 * defaults is not applied here. R_vaadin_first-acceptable: Vaadin Grid owns the
 * row-selection theme, and our migration shape is "list view + edit form"
 * where in-cell selection chrome rarely matters.
 *
 * <p>JDK has a static nested {@code DefaultTableCellRenderer.UIResource}
 * subclass for L&amp;F-internal sourcing — dropped per R_match_swing_errors sub-bucket (b)
 * (L&amp;F dispatch is permanent). The performance-no-op overrides JDK
 * carries (validate / invalidate / revalidate / repaint / firePropertyChange
 * filter) are preserved verbatim per R_swing_is_truth — they're inert in our world but
 * keep the JDK shape intact for migrated user-subclasses.
 */
@SuppressWarnings("serial") // Same-version serialization only
public class DefaultTableCellRenderer extends JLabel
        implements TableCellRenderer, Serializable {

    private static final Border DEFAULT_NO_FOCUS_BORDER = new EmptyBorder(1, 1, 1, 1);
    protected static Border noFocusBorder = DEFAULT_NO_FOCUS_BORDER;

    private java.awt.Color unselectedForeground;
    private java.awt.Color unselectedBackground;

    public DefaultTableCellRenderer() {
        super();
        setOpaque(true);
        setBorder(noFocusBorder);
        // JDK calls setName("Table.cellRenderer"); name is for component-name
        // scoping under L&F dispatch (sub-bucket (b) per R_match_swing_errors). Skip.
    }

    public void setForeground(java.awt.Color c) {
        super.setForeground(c);
        unselectedForeground = c;
    }

    public void setBackground(java.awt.Color c) {
        super.setBackground(c);
        unselectedBackground = c;
    }

    public void updateUI() {
        super.updateUI();
        // JDK resets foreground/background to null here so the next
        // getTableCellRendererComponent picks up the new L&F's defaults.
        // We don't dispatch L&F (R_match_swing_errors sub-bucket (b)), but the field reset
        // matches the JDK-faithful shape for migrated subclasses that
        // override updateUI and chain super.updateUI().
        setForeground(null);
        setBackground(null);
    }

    @Override
    public vaadinx.awt.Component getTableCellRendererComponent(vaadinx.swing.JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus,
                                                               int row, int column) {
        // Minimal value-to-text path. Selection chrome (foreground/
        // background flip on isSelected, border swap on hasFocus, font from
        // table.getFont()) is dropped: Vaadin Grid owns row-selection theme,
        // so the visual loss is bounded (R_layouts_close_enough).
        setValue(value);
        return this;
    }

    /*
     * JDK overrides validate / invalidate / revalidate / repaint /
     * firePropertyChange as performance no-ops because the renderer is used
     * as a "rubber-stamp" — reused for every cell, parented only briefly.
     * Our paint pipeline doesn't run (R_layouts_close_enough), so these are functionally inert
     * either way. Preserved verbatim per R_swing_is_truth so migrated subclasses that
     * super.invalidate() see the JDK-faithful shape.
     */

    public boolean isOpaque() {
        // JDK's optimized version checks the parent JTable's background to
        // skip painting when colours match. Without a paint pipeline (R_layouts_close_enough)
        // and without yet reading from JTable's accessors, the
        // honest answer is the field-backed JComponent default.
        return super.isOpaque();
    }

    public void invalidate() {}

    public void validate() {}

    public void revalidate() {}

    public void repaint(long tm, int x, int y, int width, int height) {}

    public void repaint(java.awt.Rectangle r) {}

    public void repaint() {}

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        // JDK filters PCEs to a small allowlist (text / labelFor /
        // displayedMnemonic / font / foreground when BasicHTML clientProp
        // is set) for performance. Without the BasicHTML hook we simplify
        // to "always allow text-related PCE through" — preserves the
        // common-case behavior that migrated subclasses depend on.
        if ("text".equals(propertyName)
                || "labelFor".equals(propertyName)
                || "displayedMnemonic".equals(propertyName)) {
            super.firePropertyChange(propertyName, oldValue, newValue);
        }
    }

    public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        // Performance no-op per JDK.
    }

    /**
     * Sets the cell's text from {@code value}. Migrated subclasses commonly
     * override this hook to format values differently (currency, dates,
     * yes/no) — the canonical "DefaultTableCellRenderer subclass" pattern.
     */
    protected void setValue(Object value) {
        setText((value == null) ? "" : value.toString());
    }
}
