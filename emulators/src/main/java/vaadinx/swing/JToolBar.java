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
 * This file is derived from OpenJDK's javax.swing.JToolBar
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JToolBar (D_jtoolbar).
//
// The JDK's four fields (paintBorder, margin, floatable, orientation) and its
// bodies over them; rollover lives in the client-property table, as in the JDK.
// The SJToolBar peer is told the current orientation and margin and never read
// back. floatable / borderPainted / rollover have no Vaadin counterpart (no
// detachable toolbars in a browser, no rollover toggle, no painted-border flag
// distinct from the CSS border), so SJToolBar does not declare them at all
// (SD_sjtoolbar) and only the effect is declined here (R_decline_effect_only).
//
// Separators are JToolBar.Separator children, as in the JDK, so they occupy
// component indices; each renders through its own SJSeparator peer. The
// DefaultToolBarLayout is not ported: SJToolBar pins its flex flow per
// orientation instead.
//
// Peer lock-down per R_leaf_peer_lockdown: javax.swing.JToolBar is a leaf in the public
// Swing hierarchy (JToolBar.Separator extends JSeparator, not JToolBar;
// javax.swing.plaf.* drops per R_match_swing_errors sub-bucket b). Every ctor calls
// super(new SJToolBar(orientation)) directly — no protected
// (Component peer) ctor. User-code subclasses inherit the locked peer.

/** Emulator for {@link javax.swing.JToolBar}. See D_jtoolbar. */
public class JToolBar extends vaadinx.swing.JComponent implements javax.swing.SwingConstants, javax.accessibility.Accessible {

    private boolean paintBorder = true;
    private java.awt.Insets margin = null;
    private boolean floatable = true;
    private int orientation = HORIZONTAL;

    /**
     * Client-property key {@code rollover} is stored under, as in the JDK.
     * Not a JDK constant — the JDK inlines the string in both accessors —
     * but naming it here keeps the two in step.
     */
    private static final String ROLLOVER_KEY = "JToolBar.isRollover";

    public JToolBar() {
        this(HORIZONTAL);
    }

    public JToolBar(int orientation) {
        this(null, orientation);
    }

    public JToolBar(java.lang.String name) {
        this(name, HORIZONTAL);
    }

    /**
     * @throws IllegalArgumentException if {@code orientation} is neither
     *         {@code HORIZONTAL} nor {@code VERTICAL}, as in the JDK
     */
    public JToolBar(java.lang.String name, int orientation) {
        super(new com.vaadin.swingbridge.surrogates.SJToolBar(checkOrientation(orientation)));
        setName(name);
        this.orientation = orientation;
        updateUI();
    }

    private com.vaadin.swingbridge.surrogates.SJToolBar surrogate() {
        return (com.vaadin.swingbridge.surrogates.SJToolBar) getPeer();
    }

    /**
     * Chrome tint rather than the flat surface fill the {@code Div} peer would
     * otherwise select. A Swing tool bar reads as raised against the window it
     * sits in, not as part of the page — measured in a browser, the base token
     * is invisible here because it is the same colour as the content behind it,
     * where the container token renders the strip a migrated app expects
     * (D_theme_is_lookandfeel).
     */
    @Override
    protected String defaultFillCssClass() {
        return FILL_CHROME;
    }

    // ---- Container introspection ------------------------------------------

    /** Separators occupy index positions, as in the JDK. */
    public int getComponentIndex(vaadinx.awt.Component c) {
        int ncomponents = this.getComponentCount();
        vaadinx.awt.Component[] component = this.getComponents();
        for (int i = 0; i < ncomponents; i++) {
            vaadinx.awt.Component comp = component[i];
            if (comp == c)
                return i;
        }
        return -1;
    }

    /** @return the child at {@code i}, or {@code null} for an invalid index — the JDK does not throw */
    public vaadinx.awt.Component getComponentAtIndex(int i) {
        int ncomponents = this.getComponentCount();
        if (i >= 0 && i < ncomponents) {
            vaadinx.awt.Component[] component = this.getComponents();
            return component[i];
        }
        return null;
    }

    // ---- Margin -----------------------------------------------------------

    /**
     * Rendered as CSS {@code padding} on the peer; {@code null} removes it.
     * Unguarded, as the JDK's is, so a repeated {@code null} still fires.
     */
    public void setMargin(java.awt.Insets m) {
        java.awt.Insets old = margin;
        margin = m;
        firePropertyChange("margin", old, m);
        revalidate();
        repaint();
        withPeer(p -> surrogate().setMargin(margin));
    }

    /** @return the margin as set, or a fresh zero {@code Insets} while none is */
    public java.awt.Insets getMargin() {
        if (margin == null) {
            return new java.awt.Insets(0, 0, 0, 0);
        } else {
            return margin;
        }
    }

    // ---- borderPainted / floatable / rollover (effect declined) -----------

    public boolean isBorderPainted() {
        return paintBorder;
    }

    /**
     * Stored and fired; the effect is declined — the CSS border installed through
     * {@link #setBorder} paints either way.
     */
    public void setBorderPainted(boolean b) {
        if (paintBorder != b) {
            boolean old = paintBorder;
            paintBorder = b;
            firePropertyChange("borderPainted", old, b);
            revalidate();
            repaint();
        }
    }

    public boolean isFloatable() {
        return floatable;
    }

    /** Stored and fired; the effect is declined — a browser has no detachable toolbars. */
    public void setFloatable(boolean b) {
        if (floatable != b) {
            boolean old = floatable;
            floatable = b;
            firePropertyChange("floatable", old, b);
            revalidate();
            repaint();
        }
    }

    // ---- Orientation --------------------------------------------------------

    public int getOrientation() {
        return this.orientation;
    }

    /**
     * Also turns every {@link Separator} child perpendicular to the new
     * orientation, as the L&amp;F's listener does on the desktop.
     *
     * @throws IllegalArgumentException if {@code o} is neither {@code HORIZONTAL}
     *         nor {@code VERTICAL}, even when unchanged — the JDK checks first
     */
    public void setOrientation(int o) {
        checkOrientation(o);
        if (orientation != o) {
            int old = orientation;
            orientation = o;
            orientSeparators(o);
            firePropertyChange("orientation", old, o);
            revalidate();
            repaint();
            withPeer(p -> surrogate().setOrientation(orientation));
        }
    }

    /**
     * BasicToolBarUI's {@code "orientation"} listener, which every desktop L&amp;F
     * inherits: each {@link Separator} child turns perpendicular to the toolbar and
     * a non-square size swaps its sides. The L&amp;F registers before any app
     * listener, so running this ahead of the fire reproduces the order an app's
     * {@code PropertyChangeListener} observes.
     */
    private void orientSeparators(int orientation) {
        for (vaadinx.awt.Component c : getComponents()) {
            if (c instanceof Separator separator) {
                if (orientation == HORIZONTAL) {
                    separator.setOrientation(JSeparator.VERTICAL);
                } else {
                    separator.setOrientation(JSeparator.HORIZONTAL);
                }
                java.awt.Dimension size = separator.getSeparatorSize();
                if (size != null && size.width != size.height) {
                    separator.setSeparatorSize(new java.awt.Dimension(size.height, size.width));
                }
            }
        }
    }

    /**
     * Stored under {@value #ROLLOVER_KEY} in the client-property table, where the JDK
     * puts it, so the {@code "JToolBar.isRollover"} PropertyChangeEvent is
     * {@link #putClientProperty}'s — which is why its old value is {@code null} on
     * the first call and why the values are boxed (D_property_fanout_audit). The
     * effect is declined: Vaadin buttons paint their own hover state.
     */
    public void setRollover(boolean rollover) {
        putClientProperty(ROLLOVER_KEY, rollover ? Boolean.TRUE : Boolean.FALSE);
    }

    public boolean isRollover() {
        Boolean rollover = (Boolean) getClientProperty(ROLLOVER_KEY);
        if (rollover != null) {
            return rollover.booleanValue();
        }
        return false;
    }

    /** The JDK's check, returning its argument so a constructor can run it before {@code super}. */
    private static int checkOrientation(int orientation) {
        switch (orientation) {
            case VERTICAL:
            case HORIZONTAL:
                break;
            default:
                throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
        return orientation;
    }

    // ---- Separators and actions ---------------------------------------------

    /** Appends a {@link Separator} of the default size. */
    public void addSeparator() {
        addSeparator(null);
    }

    /**
     * Appends a {@link Separator}. The size is stored on it but not rendered
     * (R_layouts_close_enough): every separator paints as the same thin bar.
     */
    public void addSeparator(java.awt.Dimension size) {
        JToolBar.Separator s = new JToolBar.Separator(size);
        add(s);
    }

    public JButton add(javax.swing.Action a) {
        JButton b = createActionComponent(a);
        b.setAction(a);
        add(b);
        return b;
    }

    /**
     * The button {@link #add(javax.swing.Action)} wraps an Action in: its text is
     * hidden when the Action carries an icon, and its Action listener comes from
     * {@link #createActionChangeListener} when that returns one.
     */
    protected JButton createActionComponent(javax.swing.Action a) {
        JButton b = new JButton() {
            protected java.beans.PropertyChangeListener createActionPropertyChangeListener(javax.swing.Action a) {
                java.beans.PropertyChangeListener pcl = createActionChangeListener(this);
                if (pcl == null) {
                    pcl = super.createActionPropertyChangeListener(a);
                }
                return pcl;
            }
        };
        if (a != null && (a.getValue(javax.swing.Action.SMALL_ICON) != null
                || a.getValue(javax.swing.Action.LARGE_ICON_KEY) != null)) {
            b.setHideActionText(true);
        }
        b.setHorizontalTextPosition(JButton.CENTER);
        b.setVerticalTextPosition(JButton.BOTTOM);
        return b;
    }

    /** @return {@code null}, which keeps the button's default Action listener */
    protected java.beans.PropertyChangeListener createActionChangeListener(JButton b) {
        return null;
    }

    /**
     * Turns a {@link Separator} perpendicular to this toolbar and makes a
     * {@link JButton} non-default-capable, then adds as usual.
     */
    protected void addImpl(vaadinx.awt.Component comp, java.lang.Object constraints, int index) {
        if (comp instanceof Separator) {
            if (getOrientation() == VERTICAL) {
                ((Separator) comp).setOrientation(JSeparator.HORIZONTAL);
            } else {
                ((Separator) comp).setOrientation(JSeparator.VERTICAL);
            }
        }
        super.addImpl(comp, constraints, index);
        if (comp instanceof JButton) {
            ((JButton) comp).setDefaultCapable(false);
        }
    }

    /**
     * A toolbar-specific separator, rendered as the same thin bar as
     * {@link JSeparator}; its size is stored and read back but not rendered
     * (R_layouts_close_enough).
     */
    public static class Separator extends JSeparator {

        /**
         * BasicToolBarSeparatorUI's {@code "ToolBar.separatorSize"}, the size every
         * desktop L&amp;F installs on a separator constructed without one.
         */
        private static final int DEFAULT_SIZE = 10;

        private java.awt.Dimension separatorSize;

        public Separator() {
            this(null);
        }

        public Separator(java.awt.Dimension size) {
            super(JSeparator.HORIZONTAL);
            setSeparatorSize(size);
        }

        public String getUIClassID() {
            return "ToolBarSeparatorUI";
        }

        /**
         * {@code null} keeps the current size, and installs the L&amp;F's 10&times;10
         * default only while there is none.
         *
         * <p>On the desktop the default arrives through {@code updateUI()}, from
         * the constructor, while the separator is still horizontal — and the
         * L&amp;F's width/height swap for a horizontal separator builds a plain
         * {@code Dimension}, dropping the {@code UIResource} marker that would let
         * a later reinstall replace it. So the default is installed once and then
         * held like an app's own size.
         */
        public void setSeparatorSize(java.awt.Dimension size) {
            if (size != null) {
                separatorSize = size;
            } else {
                super.updateUI();
                if (separatorSize == null) {
                    separatorSize = new java.awt.Dimension(DEFAULT_SIZE, DEFAULT_SIZE);
                }
            }
            this.invalidate();
        }

        /** @return the stored size itself, not a copy, as in the JDK */
        public java.awt.Dimension getSeparatorSize() {
            return separatorSize;
        }

        public java.awt.Dimension getMinimumSize() {
            if (separatorSize != null) {
                return separatorSize.getSize();
            } else {
                return super.getMinimumSize();
            }
        }

        public java.awt.Dimension getMaximumSize() {
            if (separatorSize != null) {
                return separatorSize.getSize();
            } else {
                return super.getMaximumSize();
            }
        }

        public java.awt.Dimension getPreferredSize() {
            if (separatorSize != null) {
                return separatorSize.getSize();
            } else {
                return super.getPreferredSize();
            }
        }
    }

    // ---- L&F surface --------------------------------------------------------

    public java.lang.String getUIClassID() {
        return "ToolBarUI";
    }

    public void updateUI() {
        // L&F swap — no-op for us; Vaadin owns the DOM. Same shape as
        // JPanel.updateUI / JButton.updateUI.
    }

    public javax.swing.plaf.ToolBarUI getUI() {
        // No UI delegate; real Swing hands back a BasicToolBarUI. Null-and-
        // WARN so migration logs surface user/library code that reaches
        // for the UI delegate.
        vaadinx.EHelper.onUnimplemented("JToolBar", "getUI");
        return null;
    }

    public void setUI(javax.swing.plaf.ToolBarUI ui) {
        // L&F surgery — we don't model it.
        vaadinx.EHelper.onUnimplemented("JToolBar", "setUI", ui);
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JToolBar", "getAccessibleContext");
        return null;
    }

    protected java.lang.String paramString() {
        java.lang.String paintBorderString = (paintBorder ? "true" : "false");
        java.lang.String marginString = (margin != null ? margin.toString() : "");
        java.lang.String floatableString = (floatable ? "true" : "false");
        java.lang.String orientationString = (orientation == HORIZONTAL ? "HORIZONTAL" : "VERTICAL");

        return super.paramString()
                + ",floatable=" + floatableString
                + ",margin=" + marginString
                + ",orientation=" + orientationString
                + ",paintBorder=" + paintBorderString;
    }
}
