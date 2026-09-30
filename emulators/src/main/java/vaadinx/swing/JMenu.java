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
 * This file is derived from OpenJDK's javax.swing.JMenu
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JMenu per D_menu_tree / SD_sjmenubar. R_leaf_peer_lockdown
// lock-down: javax.swing.JMenu is a leaf in the public Swing hierarchy
// (JPopupMenu.Wrapper etc. are package-private internals, not public
// extension points), so the protected (Component peer) ctor is omitted.
// The root no-arg ctor calls super() (JMenuItem's no-arg) directly.

/** Emulator for {@link javax.swing.JMenu}. R_leaf_peer_lockdown-locked over the JMenuItem placeholder peer. */
public class JMenu extends vaadinx.swing.JMenuItem {

    /**
     * Children list — JMenuItems (including JMenus, JCheckBoxMenuItems,
     * JRadioButtonMenuItems) and JSeparators. Order matches insertion
     * order; the rebuild walk emits them in this order.
     *
     * <p>Children are stored as {@link Object} so the homogeneous list
     * can carry both {@link JMenuItem} and {@link JSeparator} instances
     * without forcing a common base type. The rebuild walk type-tests
     * each element to choose between the leaf-shape MenuNode + the
     * separator-shape MenuNode.
     */
    private final java.util.List<Object> children = new java.util.ArrayList<>();

    /** Submenu popup delay in milliseconds — stored per R_decline_effect_only, never acted on. */
    private int delay;

    public JMenu() {
        super();
    }

    public JMenu(java.lang.String text) {
        super(text);
    }

    public JMenu(java.lang.String text, boolean tearOff) {
        super(text);
        // tearOff is JDK accepted-and-ignored for non-tear-off
        // implementations — Swing's own default L&F doesn't support
        // tear-off menus either. Silent on construction; users reading
        // back the value get false (we don't store it).
        if (tearOff) {
            vaadinx.EHelper.onUnimplemented("JMenu", "<init>(String,boolean)/tearOff");
        }
    }

    public JMenu(javax.swing.Action a) {
        super();
        setAction(a);
    }

    // ---- Children API ------------------------------------------------

    public JMenuItem add(JMenuItem item) {
        if (item == null) return null;
        children.add(item);
        item.setParent(this);
        notifyTreeMutated();
        return item;
    }

    public JMenuItem add(java.lang.String text) {
        return add(new JMenuItem(text));
    }

    public JMenuItem add(javax.swing.Action a) {
        JMenuItem item = createActionComponent(a);
        item.setAction(a);
        return add(item);
    }

    /**
     * JDK factory used by {@link #add(javax.swing.Action)}. Subclasses
     * override to customize the per-Action item type. Default: a fresh
     * {@link JMenuItem} configured from the Action.
     */
    protected JMenuItem createActionComponent(javax.swing.Action a) {
        // JDK builds an anonymous subclass that overrides
        // createActionPropertyChangeListener; we don't yet bridge that
        // weak-reference shape (AbstractButton uses the strong-ref
        // listener on this side too), so a plain JMenuItem is honest.
        return new JMenuItem();
    }

    public void addSeparator() {
        children.add(new JSeparator());
        notifyTreeMutated();
    }

    public JMenuItem insert(JMenuItem item, int pos) {
        if (item == null) return null;
        children.add(pos, item);
        item.setParent(this);
        notifyTreeMutated();
        return item;
    }

    public void insert(javax.swing.Action a, int pos) {
        JMenuItem item = createActionComponent(a);
        item.setAction(a);
        insert(item, pos);
    }

    public void insertSeparator(int pos) {
        children.add(pos, new JSeparator());
        notifyTreeMutated();
    }

    public void remove(JMenuItem item) {
        if (item == null) return;
        if (children.remove(item)) {
            item.setParent(null);
            notifyTreeMutated();
        }
    }

    public void remove(int pos) {
        Object old = children.remove(pos);
        if (old instanceof JMenuItem item) item.setParent(null);
        notifyTreeMutated();
    }

    public void removeAll() {
        if (children.isEmpty()) return;
        for (Object child : children) {
            if (child instanceof JMenuItem item) item.setParent(null);
        }
        children.clear();
        notifyTreeMutated();
    }

    public int getItemCount() {
        return children.size();
    }

    public JMenuItem getItem(int pos) {
        Object child = children.get(pos);
        return child instanceof JMenuItem item ? item : null;
    }

    public int getMenuComponentCount() {
        return children.size();
    }

    /**
     * Typed {@code vaadinx.awt.Component} per R_no_vaadin_in_api limb 1. Under the
     * JDK's type the argument was unproducible after the import swap,
     * so the body could only return {@code false}; ported, the
     * children this menu already tracks are exactly the right type to
     * answer with.
     *
     * @return true when {@code c} is this menu or, recursively, one of its menu
     *         children — the JDK's own contract.
     */
    public boolean isMenuComponent(vaadinx.awt.Component c) {
        if (c == null) return false;
        if (c == this) return true;
        for (Object child : children) {
            if (child == c) return true;
            if (child instanceof JMenu sub && sub.isMenuComponent(c)) return true;
        }
        return false;
    }

    // ---- MenuNode emission ------------------------------------------

    @Override
    java.util.List<com.vaadin.swingbridge.surrogates.MenuNode> collectChildren(boolean parentEnabled) {
        java.util.List<com.vaadin.swingbridge.surrogates.MenuNode> out = new java.util.ArrayList<>(children.size());
        for (Object child : children) {
            if (child instanceof JSeparator) {
                out.add(com.vaadin.swingbridge.surrogates.MenuNode.ofSeparator());
            } else if (child instanceof JMenuItem item) {
                out.add(item.toMenuNode(parentEnabled));
            }
        }
        return out;
    }

    @Override
    Runnable makeOnClick() {
        // Top-level container: Vaadin auto-opens the submenu on parent
        // click, so no Swing-side ActionListener fan-out from the
        // surrogate is needed. Returning null tells SJMenuBar to install
        // a no-op click handler — Vaadin's open-on-click still fires
        // natively.
        if (!children.isEmpty()) return null;
        return super.makeOnClick();
    }

    // ---- Popup / MenuListener — drop-and-WARN per R_vaadin_first ----------------

    public void setPopupMenuVisible(boolean b) {
        // Vaadin owns popup state with no programmatic-open API today;
        // R_match_swing_errors sub-bucket (a) — graduates if upstream lands one.
        if (b) {
            vaadinx.EHelper.onUnimplemented("JMenu", "setPopupMenuVisible(true)");
        }
    }

    public boolean isPopupMenuVisible() {
        return false;
    }

    public boolean isTearOff() {
        return false;
    }

    public void addMenuListener(javax.swing.event.MenuListener l) {
        // R_match_swing_errors sub-bucket (a) — Vaadin doesn't surface menu open/close
        // events typed today.
        vaadinx.EHelper.onUnimplemented("JMenu", "addMenuListener", l);
    }

    public void removeMenuListener(javax.swing.event.MenuListener l) {
        vaadinx.EHelper.onUnimplemented("JMenu", "removeMenuListener", l);
    }

    public javax.swing.event.MenuListener[] getMenuListeners() {
        return new javax.swing.event.MenuListener[0];
    }

    public int getDelay() {
        return delay;
    }

    /**
     * Stores the delay and rejects a negative one as the JDK does; the submenu still
     * opens on Vaadin's own schedule, since the effect is what R_decline_effect_only
     * permits dropping and the state is not.
     */
    public void setDelay(int d) {
        if (d < 0) {
            throw new IllegalArgumentException("Delay must be a positive integer");
        }
        delay = d;
        vaadinx.EHelper.onNoop("JMenu", "setDelay");
    }

    public vaadinx.swing.JPopupMenu getPopupMenu() {
        // R_match_swing_errors sub-bucket (a) — Vaadin doesn't surface a JPopupMenu-shape
        // handle on the open submenu.
        vaadinx.EHelper.onUnimplemented("JMenu", "getPopupMenu");
        return null;
    }

    public boolean isTopLevelMenu() {
        // True if this JMenu's parent is a JMenuBar (vs. a JMenu).
        return getParentNode() instanceof JMenuBar;
    }

    @Override
    public java.lang.String getUIClassID() {
        return "MenuUI";
    }
}
