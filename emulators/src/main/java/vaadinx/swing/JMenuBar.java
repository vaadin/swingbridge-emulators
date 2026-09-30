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
 * This file is derived from OpenJDK's javax.swing.JMenuBar
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JMenuBar per D_menu_tree / SD_sjmenubar. R_leaf_peer_lockdown
// lock-down: javax.swing.JMenuBar is a leaf in the public Swing
// hierarchy, so the protected (Component peer) ctor is omitted; the
// root no-arg ctor calls super(new SJMenuBar()) directly. Holds the
// JDK menu tree (List<JMenu>); every mutation walks the tree, builds a
// MenuNode snapshot, and pushes it through SJMenuBar.rebuildFromTree.

/** Emulator for {@link javax.swing.JMenuBar}. R_leaf_peer_lockdown-locked over {@link com.vaadin.swingbridge.surrogates.SJMenuBar}. */
public class JMenuBar extends vaadinx.swing.JComponent
        implements javax.accessibility.Accessible, vaadinx.swing.MenuElement {

    /**
     * JDK-shape children list. Order matches insertion order; index in
     * this list maps directly onto top-level position in the rendered
     * Vaadin MenuBar after {@link #pushTree()}.
     */
    private final java.util.List<JMenu> menus = new java.util.ArrayList<>();

    public JMenuBar() {
        super(new com.vaadin.swingbridge.surrogates.SJMenuBar());
        // No bridge wiring at construction time — every JMenuItem the
        // user adds owns its own ActionListener fan-out (MenuNode.onClick
        // funnels through fireActionPerformed on the emulator side per
        // D_menu_tree). JMenuBar itself has no event surface beyond its
        // contained items.
    }

    // ---- JDK children API --------------------------------------------

    /**
     * Add a top-level menu. JDK's signature returns the added menu; we
     * preserve that. Pushes a fresh tree on success.
     */
    public JMenu add(JMenu m) {
        if (m == null) return null;
        menus.add(m);
        m.setParent(this);
        pushTree();
        return m;
    }

    public JMenu getMenu(int index) {
        // JDK returns null when the indexed slot is non-JMenu (only
        // possible for raw Component adds, which we don't expose).
        // IndexOutOfBoundsException matches JDK getMenu's behaviour for
        // out-of-range — D_never_fail_on_gaps keeps the throw shape verbatim.
        return menus.get(index);
    }

    public int getMenuCount() {
        return menus.size();
    }

    /**
     * Typed {@code vaadinx.awt.Component}, not {@code java.awt.Component}
     * (R_no_vaadin_in_api limb 1) — with the JDK's own type this took an argument no
     * import-swapped code can produce, so it could only ever return
     * {@code -1}. Ported, it answers the real query, which is why the
     * SB-Emulators-invented {@code getComponentIndex(JMenu)} overload that
     * existed to work around that is gone: {@code JMenu} is a
     * {@code vaadinx.awt.Component}, so this signature covers it.
     *
     * @return the index of {@code c} among this bar's menus, or {@code -1}.
     */
    public int getComponentIndex(vaadinx.awt.Component c) {
        for (int i = 0; i < menus.size(); i++) {
            if (menus.get(i) == c) return i;
        }
        return -1;
    }

    /**
     * Replace the menu at {@code index} with {@code m}. JDK silently
     * accepts a null replacement (slot is left empty); we follow.
     */
    public void setMenu(int index, JMenu m) {
        JMenu old = menus.get(index);
        if (old != null) old.setParent(null);
        if (m == null) {
            menus.remove(index);
        } else {
            menus.set(index, m);
            m.setParent(this);
        }
        pushTree();
    }

    public void remove(JMenu m) {
        if (m == null) return;
        if (menus.remove(m)) {
            m.setParent(null);
            pushTree();
        }
    }

    public void remove(int index) {
        JMenu old = menus.remove(index);
        if (old != null) old.setParent(null);
        pushTree();
    }

    public void removeAll() {
        if (menus.isEmpty()) return;
        for (JMenu m : menus) m.setParent(null);
        menus.clear();
        pushTree();
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Cascades down to disable all items per JDK. R_swing_is_truth: store the
        // emulator-side field via super.setEnabled (Component drives
        // peer.setEnabled), then push a fresh tree so the disabled
        // flag reaches each rendered MenuItem.
        boolean old = isEnabled();
        super.setEnabled(enabled);
        if (old != enabled) pushTree();
    }

    // ---- Tree-rebuild push -------------------------------------------

    /**
     * Walk the JDK menu tree and push a fresh {@link com.vaadin.swingbridge.surrogates.MenuNode}
     * snapshot to the surrogate. Called from every tree-mutating
     * operation on this menubar or its descendants — child JMenuItems
     * bubble up via {@link #notifyTreeMutated()}.
     */
    void pushTree() {
        if (!(getPeer() instanceof com.vaadin.swingbridge.surrogates.SJMenuBar bar)) return;
        java.util.List<com.vaadin.swingbridge.surrogates.MenuNode> snapshot = new java.util.ArrayList<>(menus.size());
        boolean barEnabled = isEnabled();
        for (JMenu m : menus) {
            snapshot.add(m.toMenuNode(barEnabled));
        }
        withPeer(p -> bar.rebuildFromTree(snapshot));
    }

    /**
     * Called by descendants ({@link JMenu}, {@link JMenuItem}) when their
     * own state mutates. Walks up to the root via {@code parent}
     * pointers; any item not yet attached to a bar quietly skips, and
     * its state is picked up by the next rebuild after {@code add}.
     */
    void notifyTreeMutated() {
        pushTree();
    }

    // ---- MenuElement -------------------------------------------------
    // The JDK's JMenuBar leaves all three process*/selection members empty; they stay empty
    // here rather than WARNing, per R_no_silent_improvements — a WARN would report a gap
    // where real Swing also does nothing.

    /** @return this bar's menus, the JDK's own answer (it filters its children to MenuElements). */
    public vaadinx.swing.MenuElement[] getSubElements() {
        return menus.toArray(new vaadinx.swing.MenuElement[0]);
    }

    /** @return {@code this} — see {@link JMenuItem#getComponent()} for why the ported interface matters. */
    public vaadinx.awt.Component getComponent() {
        return this;
    }

    public void menuSelectionChanged(boolean isIncluded) {
    }

    public void processKeyEvent(vaadinx.awt.event.KeyEvent e, vaadinx.swing.MenuElement[] path,
                                vaadinx.swing.MenuSelectionManager manager) {
    }

    public void processMouseEvent(vaadinx.awt.event.MouseEvent event, vaadinx.swing.MenuElement[] path,
                                  vaadinx.swing.MenuSelectionManager manager) {
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JMenuBar", "getAccessibleContext");
        return null;
    }

    public java.lang.String getUIClassID() {
        return "MenuBarUI";
    }

    public void updateUI() {
        // L&F swap — no-op per the standard pattern (Vaadin owns the DOM).
    }
}
