/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JPopupMenu
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JPopupMenu per D_jpopupmenu / SD_sjpopupmenu. R_leaf_peer_lockdown
// lock-down: javax.swing.JPopupMenu is a leaf in the public Swing
// hierarchy (its only subclass, javax.swing.plaf.basic.BasicComboPopup,
// is L&F — dropped per R_match_swing_errors sub-bucket (b)), so the protected (Component
// peer) ctor is omitted and the no-arg ctor calls super(new SJPopupMenu())
// directly.
//
// Structurally a sibling of JMenuBar: holds the JDK menu-item tree
// (List<Object> of JMenuItem / JSeparator, mirroring JMenu's own child
// list) and pushes a MenuNode snapshot to its SJPopupMenu peer via
// pushTree() on every mutation. The peer's ContextMenu renders the tree;
// right-click on the popup's target opens it.

/** Emulator for {@link javax.swing.JPopupMenu}. R_leaf_peer_lockdown-locked over {@link com.vaadin.swingbridge.surrogates.SJPopupMenu}. */
public class JPopupMenu extends vaadinx.swing.JComponent implements javax.accessibility.Accessible, vaadinx.swing.MenuElement {

    /**
     * JDK-shape children list — {@link JMenuItem}s (including {@link JMenu},
     * {@link JCheckBoxMenuItem}, {@link JRadioButtonMenuItem}) and
     * {@link JSeparator}s, stored as {@link Object} so the homogeneous list
     * carries both without a common base. The rebuild walk type-tests each
     * element. Mirrors {@link JMenu}'s own child-list shape.
     */
    private final java.util.List<Object> children = new java.util.ArrayList<>();

    /** Optional label (rarely rendered by L&amp;Fs; round-trips per JDK). */
    private String label;

    /**
     * Stands for the JDK's {@code popup} field, whose nullity <em>is</em>
     * {@link #isVisible()}. Distinct from the inherited {@code Component.visible}
     * because the JDK's is too — overriding both accessors leaves that one at
     * {@code JComponent}'s {@code true} for the object's whole life.
     */
    private boolean popupVisible;

    /**
     * R_swing_is_truth feedback-loop guard, held across our own
     * {@link com.vaadin.flow.component.contextmenu.ContextMenuBase#close()} so the
     * echo does not re-fire what {@link #setVisible} has already fired.
     */
    private boolean preventPeerEvents;

    public JPopupMenu() {
        super(new com.vaadin.swingbridge.surrogates.SJPopupMenu());
        installOpenedSync();
    }

    /**
     * Peer→Swing sync for the open state (R_swing_is_truth / R_callswing_envelope). The browser
     * opens and closes the {@code ContextMenu} on its own — right-click,
     * Esc, click-outside, item selection — so the peer's
     * {@code OpenedChangeEvent} is the only signal there is, and without
     * this seam the Swing side never learns (Q_popupmenu_visible_sync).
     * Fires the JDK's {@code setVisible} notifications in the JDK's own
     * order: the {@code PopupMenuListener} event first, then the
     * {@code "visible"} bound property.
     *
     * <p>Two documented divergences: the JDK fires {@code willBecomeVisible}
     * <em>before</em> display, while here the popup is already open/closed
     * when the server learns (the standard peer→Swing skew); and
     * {@link #firePopupMenuCanceled()} is never reached — the peer signal
     * cannot distinguish an Esc/click-outside cancel from an item-selection
     * close (R_match_swing_errors sub-bucket (a)).
     *
     * <p>The guard read and the {@link #popupVisible} write both sit
     * outside {@code callSwing}, per R_swing_is_truth: an echo of our
     * own {@code close()} is not peer→Swing work and must never reach
     * the R_callswing_envelope envelope, and writing the state first
     * means the no-change check cannot be re-entered by the next
     * {@code OpenedChangeEvent} while a handler is parked.
     */
    private void installOpenedSync() {
        ((com.vaadin.swingbridge.surrogates.SJPopupMenu) getPeer()).addOpenedChangeListener(e -> {
            if (preventPeerEvents) return;
            final boolean opened = e.isOpened();
            if (popupVisible == opened) return;
            popupVisible = opened;
            vaadinx.EHelper.callSwing(() -> {
                if (opened) {
                    firePopupMenuWillBecomeVisible();
                    firePropertyChange("visible", false, true);
                } else {
                    firePopupMenuWillBecomeInvisible();
                    firePropertyChange("visible", true, false);
                }
            });
        });
    }

    public JPopupMenu(String label) {
        this();
        this.label = label;
    }

    // ---- Children API (mirrors JMenu) --------------------------------

    public JMenuItem add(JMenuItem item) {
        if (item == null) return null;
        children.add(item);
        item.setParent(this);
        pushTree();
        return item;
    }

    public JMenuItem add(String text) {
        return add(new JMenuItem(text));
    }

    public JMenuItem add(javax.swing.Action a) {
        JMenuItem item = new JMenuItem();
        item.setAction(a);
        return add(item);
    }

    public void addSeparator() {
        children.add(new JSeparator());
        pushTree();
    }

    public void insert(JMenuItem item, int index) {
        if (item == null) return;
        children.add(index, item);
        item.setParent(this);
        pushTree();
    }

    public void insert(javax.swing.Action a, int index) {
        JMenuItem item = new JMenuItem();
        item.setAction(a);
        insert(item, index);
    }

    public void remove(JMenuItem item) {
        if (item == null) return;
        if (children.remove(item)) {
            item.setParent(null);
            pushTree();
        }
    }

    public void remove(int index) {
        Object old = children.remove(index);
        if (old instanceof JMenuItem item) item.setParent(null);
        pushTree();
    }

    public void removeAll() {
        if (children.isEmpty()) return;
        for (Object child : children) {
            if (child instanceof JMenuItem item) item.setParent(null);
        }
        children.clear();
        pushTree();
    }

    /**
     * JDK inherits {@code Container.getComponentCount()}; since our menu
     * children live in this list (not via {@code Container.addImpl}, per
     * D_menu_tree), override so the count reflects the JDK menu tree.
     */
    @Override
    public int getComponentCount() {
        return children.size();
    }

    public int getComponentIndex(vaadinx.awt.Component c) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) == c) return i;
        }
        return -1;
    }

    // ---- Tree-rebuild push (mirrors JMenuBar) ------------------------

    /**
     * Walk the JDK menu tree and push a fresh {@link com.vaadin.swingbridge.surrogates.MenuNode}
     * snapshot to the surrogate. Called from every tree-mutating operation
     * on this popup or its descendants — child {@link JMenuItem}s bubble up
     * via {@link #notifyTreeMutated()}.
     */
    void pushTree() {
        if (!(getPeer() instanceof com.vaadin.swingbridge.surrogates.SJPopupMenu menu)) return;
        boolean enabled = isEnabled();
        java.util.List<com.vaadin.swingbridge.surrogates.MenuNode> snapshot = new java.util.ArrayList<>(children.size());
        for (Object child : children) {
            if (child instanceof JSeparator) {
                snapshot.add(com.vaadin.swingbridge.surrogates.MenuNode.ofSeparator());
            } else if (child instanceof JMenuItem item) {
                snapshot.add(item.toMenuNode(enabled));
            }
        }
        withPeer(p -> menu.rebuildFromTree(snapshot));
    }

    /** A descendant mutated — re-push. {@link JPopupMenu} is a tree root, like {@link JMenuBar}. */
    void notifyTreeMutated() {
        pushTree();
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean old = isEnabled();
        super.setEnabled(enabled);
        if (old != enabled) pushTree();
    }

    // ---- Show / visibility -------------------------------------------

    /**
     * Binds the popup to {@code invoker} so a right-click (or long-press) on
     * it opens the menu, then ends in {@link #setVisible setVisible(true)} as
     * the JDK's own body does. The coordinates are dropped —
     * R_layouts_close_enough position loss, and Vaadin has no programmatic
     * open at a point (SD_sjpopupmenu); the common right-click-triggered
     * idiom works natively once the target is bound.
     *
     * <p>Calling {@code setVisible(true)} here cannot double-fire
     * against the browser's own open in the same round trip:
     * whichever of the two lands first writes the state, and the
     * other hits a no-change early return, so exactly one
     * {@code willBecomeVisible} reaches the migrator either way.
     */
    public void show(vaadinx.awt.Component invoker, int x, int y) {
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJPopupMenu menu && invoker != null) {
            withPeer(p -> menu.setTarget(invoker.getPeer()));
        }
        vaadinx.EHelper.onUnimplemented("JPopupMenu", "show/open-at-coordinates", x, y);
        setVisible(true);
    }

    /**
     * Shows or hides the popup, firing the JDK's notifications either way.
     *
     * <p>The two directions are not symmetric, because Vaadin's
     * {@code ContextMenu} is not: it has a {@code close()} but no server-side
     * programmatic open, so <b>hiding really hides</b> while showing is a
     * declined effect (R_match_swing_errors sub-bucket (a) — graduates if
     * upstream lands one). R_decline_effect_only keeps the state and the
     * notification either way, so a migrator's {@code PopupMenuListener} and
     * {@code "visible"} listener fire where they fire on the desktop.
     *
     * <p>The only gate is {@code b == isVisible()} — no displayability check
     * and no invoker needed, a bare {@code new JPopupMenu().setVisible(true)}
     * reporting {@code true} on JDK 25 (measured).
     *
     * <p>Two things the JDK's hide path also does are absent:
     * {@link #firePopupMenuCanceled()}, reachable only through the
     * client property its L&amp;F sets, and the selection-model
     * clear, {@link MenuSelectionManager} being a WARN-stub with no
     * selection to clear.
     */
    @Override
    public void setVisible(boolean visible) {
        if (visible == isVisible()) return;
        if (visible) {
            firePopupMenuWillBecomeVisible();
            vaadinx.EHelper.onUnimplemented("JPopupMenu", "setVisible(true)/programmatic-open");
            popupVisible = true;
            firePropertyChange("visible", false, true);
        } else {
            firePopupMenuWillBecomeInvisible();
            withPeer(p -> {
                preventPeerEvents = true;
                try {
                    ((com.vaadin.swingbridge.surrogates.SJPopupMenu) p).close();
                } finally {
                    preventPeerEvents = false;
                }
            });
            popupVisible = false;
            firePropertyChange("visible", true, false);
        }
    }

    /**
     * @return whether the popup is open — the JDK's {@code popup != null}, written by
     *         {@link #setVisible} and by the browser's own open/close alike. Not read
     *         off the peer, which would have the getter deny the state a declined
     *         programmatic open still owes (R_decline_effect_only).
     */
    @Override
    public boolean isVisible() {
        return popupVisible;
    }

    // ---- Label / misc ------------------------------------------------

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        String old = this.label;
        this.label = label;
        firePropertyChange("label", old, label);
    }

    public void pack() {
        // Layout-pack — Vaadin sizes the overlay itself (R_layouts_close_enough). No-op.
        vaadinx.EHelper.onNoop("JPopupMenu", "pack");
    }

    public void setInvoker(vaadinx.awt.Component invoker) {
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJPopupMenu menu && invoker != null) {
            withPeer(p -> menu.setTarget(invoker.getPeer()));
        }
    }

    public boolean isLightWeightPopupEnabled() {
        return lightWeightPopupEnabled;
    }

    /**
     * Stores the flag; the popup renders as a Vaadin DOM overlay either way, which is
     * the effect R_decline_effect_only permits dropping. The state is not — a migrated
     * app that reads the flag back must see what it set.
     */
    public void setLightWeightPopupEnabled(boolean enabled) {
        lightWeightPopupEnabled = enabled;
        vaadinx.EHelper.onNoop("JPopupMenu", "setLightWeightPopupEnabled");
    }

    /**
     * AWT's heavyweight/lightweight popup toggle — meaningless under Vaadin's DOM
     * overlay, stored per R_decline_effect_only. Defaults {@code true} as the JDK's
     * {@code defaultLWPopupEnabledKey} does.
     */
    private boolean lightWeightPopupEnabled = true;

    // ---- MenuElement -------------------------------------------------

    public vaadinx.swing.MenuElement[] getSubElements() {
        java.util.List<vaadinx.swing.MenuElement> out = new java.util.ArrayList<>();
        for (Object child : children) {
            if (child instanceof vaadinx.swing.MenuElement me) out.add(me);
        }
        return out.toArray(new vaadinx.swing.MenuElement[0]);
    }

    /**
     * @return {@code this} — the JDK's own answer. See
     *         {@link JMenuItem#getComponent()} for why the ported
     *         {@link vaadinx.swing.MenuElement} is what makes that possible.
     */
    public vaadinx.awt.Component getComponent() {
        return this;
    }

    public void menuSelectionChanged(boolean isIncluded) {
        vaadinx.EHelper.onNoop("JPopupMenu", "menuSelectionChanged");
    }

    public void processKeyEvent(vaadinx.awt.event.KeyEvent e, vaadinx.swing.MenuElement[] path,
                                vaadinx.swing.MenuSelectionManager manager) {
        vaadinx.EHelper.onUnimplemented("JPopupMenu", "processKeyEvent");
    }

    public void processMouseEvent(vaadinx.awt.event.MouseEvent event, vaadinx.swing.MenuElement[] path,
                                  vaadinx.swing.MenuSelectionManager manager) {
        vaadinx.EHelper.onUnimplemented("JPopupMenu", "processMouseEvent");
    }

    // ---- PopupMenuListener — real storage, fired from the peer's OpenedChangeEvent ----
    // (installOpenedSync). R_decline_effect_only: state + notification are the emulator's
    // to keep; only popupMenuCanceled stays undeliverable — see firePopupMenuCanceled.

    public void addPopupMenuListener(javax.swing.event.PopupMenuListener l) {
        listenerList.add(javax.swing.event.PopupMenuListener.class, l);
    }

    public void removePopupMenuListener(javax.swing.event.PopupMenuListener l) {
        listenerList.remove(javax.swing.event.PopupMenuListener.class, l);
    }

    public javax.swing.event.PopupMenuListener[] getPopupMenuListeners() {
        return listenerList.getListeners(javax.swing.event.PopupMenuListener.class);
    }

    protected void firePopupMenuWillBecomeVisible() {
        javax.swing.event.PopupMenuEvent e = null;
        for (javax.swing.event.PopupMenuListener l
                : listenerList.getListeners(javax.swing.event.PopupMenuListener.class)) {
            if (e == null) e = new javax.swing.event.PopupMenuEvent(this);
            l.popupMenuWillBecomeVisible(e);
        }
    }

    protected void firePopupMenuWillBecomeInvisible() {
        javax.swing.event.PopupMenuEvent e = null;
        for (javax.swing.event.PopupMenuListener l
                : listenerList.getListeners(javax.swing.event.PopupMenuListener.class)) {
            if (e == null) e = new javax.swing.event.PopupMenuEvent(this);
            l.popupMenuWillBecomeInvisible(e);
        }
    }

    /**
     * JDK-shaped notifier, kept for the API surface but never reached: the
     * JDK's callers are L&amp;F machinery (dropped per R_match_swing_errors sub-bucket (b)), and
     * the peer's {@code OpenedChangeEvent} cannot distinguish a cancel
     * (Esc, click-outside) from an item-selection close — both arrive as
     * {@code opened=false} and fire {@code willBecomeInvisible} only. A
     * migrator's override compiles and does not run; documented rather than
     * guessed at, since a heuristic would invent cancels the desktop never
     * fired (R_no_silent_improvements).
     */
    protected void firePopupMenuCanceled() {
        javax.swing.event.PopupMenuEvent e = null;
        for (javax.swing.event.PopupMenuListener l
                : listenerList.getListeners(javax.swing.event.PopupMenuListener.class)) {
            if (e == null) e = new javax.swing.event.PopupMenuEvent(this);
            l.popupMenuCanceled(e);
        }
    }

    // ---- L&F / accessibility -----------------------------------------

    public java.lang.String getUIClassID() {
        return "PopupMenuUI";
    }

    public void updateUI() {
        // L&F swap — no-op (Vaadin owns the DOM).
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JPopupMenu", "getAccessibleContext");
        return null;
    }
}
