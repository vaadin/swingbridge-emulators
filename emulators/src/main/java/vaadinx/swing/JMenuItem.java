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
 * This file is derived from OpenJDK's javax.swing.JMenuItem
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JMenuItem per D_menu_tree / SD_sjmenubar.
//
// JMenuItem is NON-leaf in the public Swing hierarchy (JMenu /
// JCheckBoxMenuItem / JRadioButtonMenuItem extend it), so the protected
// (Component peer) ctor stays per D_peer_ctor_injection / R_leaf_peer_lockdown. Its peer is a placeholder
// Span — JMenuItem has no individual Vaadin peer of its own; its
// rendered identity lives inside the parent JMenuBar's tree rebuild
// (matches Swing's own "JMenuItem outside a parent doesn't paint"
// behaviour). The Span placeholder is never attached to a real UI; it
// exists only to satisfy AbstractButton's (Component peer) ctor chain.
//
// State (text / icon / mnemonic / accelerator / actionCommand /
// enabled / selected — the last via JCheckBoxMenuItem / JRadioButtonMenuItem
// overrides) lives on emulator field shadows. Mutations bubble up
// through the parent chain to the root JMenuBar, which pushes a fresh
// MenuNode tree to its SJMenuBar peer per D_menu_tree.

/** Emulator for {@link javax.swing.JMenuItem}. */
public class JMenuItem extends vaadinx.swing.AbstractButton implements javax.accessibility.Accessible, vaadinx.swing.MenuElement {

    /**
     * Set by the containing JMenu / JMenuBar via {@link #setParent(Object)}.
     * Walked by {@link #notifyTreeMutated()} to find the root JMenuBar
     * for the rebuild push. Null until added to a parent — mutations on
     * a detached item field-shadow but don't reach the surrogate.
     */
    private Object parentNode;

    /**
     * Per-item accelerator. Null = none. JDK fires "accelerator" PCE on
     * change; the surrogate's tree-rebuild reads this field and installs
     * a UI-scoped Vaadin shortcut per D_menu_tree.
     */
    private javax.swing.KeyStroke accelerator;

    /**
     * The icon's Vaadin rendering, kept while {@link #getIcon()} stays the same
     * object. An unchanged item then carries the identical component in every
     * snapshot, which lets the surrogate update the rendered menu in place
     * instead of rebuilding it (MenuTreeBuilder.patchInPlace compares icons by
     * identity).
     */
    private vaadinx.swing.Icon renderedIconSource;
    private com.vaadin.flow.component.Component renderedIcon;

    public JMenuItem() {
        // Root no-arg ctor: peer is a placeholder Span that's never
        // attached. The actual rendered MenuItem is built by the parent
        // JMenuBar's tree-rebuild walk on each push.
        this(com.vaadin.flow.component.html.Span.class, com.vaadin.flow.component.html.Span::new);
    }

    public JMenuItem(java.lang.String text) {
        this();
        setText(text);
    }

    public JMenuItem(vaadinx.swing.Icon icon) {
        this();
        setIcon(icon);
    }

    public JMenuItem(java.lang.String text, vaadinx.swing.Icon icon) {
        this();
        setText(text);
        setIcon(icon);
    }

    public JMenuItem(java.lang.String text, int mnemonic) {
        this();
        setText(text);
        setMnemonic(mnemonic);
    }

    public JMenuItem(javax.swing.Action a) {
        this();
        setAction(a);
    }

    /**
     * Protected (Component peer) ctor — D_peer_ctor_injection seam. JMenuItem is non-leaf
     * (JMenu / JCheckBoxMenuItem / JRadioButtonMenuItem subclass it),
     * so the seam carries through. User-code subclasses passing a
     * custom peer get a JMenuItem whose peer is unreachable from the
     * rendered DOM (the actual Vaadin MenuItem is built per rebuild) —
     * acceptable per D_menu_tree (the migrator's intent for JMenuItem
     * subclassing is server-side state + listeners, not custom Vaadin
     * chrome).
     */
    protected JMenuItem(com.vaadin.flow.component.Component peer) {
        super(peer);
        setModel(new javax.swing.DefaultButtonModel());
    }

    /** The lazy form: see {@link vaadinx.awt.Component#Component(Class, java.util.function.Supplier)}. */
    protected <P extends com.vaadin.flow.component.Component> JMenuItem(Class<P> peerType,
            java.util.function.Supplier<? extends P> peerFactory) {
        super(peerType, peerFactory);
        setModel(new javax.swing.DefaultButtonModel());
    }

    // ---- Parent / tree-rebuild bubble --------------------------------

    /**
     * Set by the containing JMenu (when added to a submenu) or by
     * JMenuBar's bridge through JMenu (top-level menus). Visible to
     * package-mates so JMenu / JMenuBar can wire the parent pointer
     * directly.
     */
    void setParent(Object parent) {
        this.parentNode = parent;
    }

    /** {@code null} until added to a JMenu or (via JMenu) a JMenuBar. */
    Object getParentNode() {
        return parentNode;
    }

    /**
     * Walk the parent chain to find the root JMenuBar; if found, push a
     * fresh tree. Detached items skip silently — the next add to a
     * parent re-snapshots state via the rebuild walk.
     */
    void notifyTreeMutated() {
        Object p = parentNode;
        while (p instanceof JMenu jm) {
            p = jm.getParentNode();
        }
        if (p instanceof JMenuBar bar) {
            bar.notifyTreeMutated();
        } else if (p instanceof JPopupMenu popup) {
            // A JPopupMenu is a tree root too (SD_sjpopupmenu / D_jpopupmenu) — a JMenuItem (or
            // JMenu) added directly to a popup bubbles its mutations here.
            popup.notifyTreeMutated();
        }
    }

    // ---- MenuNode emission for the rebuild push ----------------------

    /**
     * Build the {@link com.vaadin.swingbridge.surrogates.MenuNode} this item contributes to
     * its parent's rebuild. {@code parentEnabled} cascades the
     * disabled-bar state per JDK — a child item appears enabled in the
     * snapshot only if its own enabled flag and every ancestor's
     * enabled flag are true.
     */
    com.vaadin.swingbridge.surrogates.MenuNode toMenuNode(boolean parentEnabled) {
        boolean effEnabled = parentEnabled && isEnabled();
        java.util.List<com.vaadin.swingbridge.surrogates.MenuNode> children = collectChildren(effEnabled);
        vaadinx.swing.Icon icon = getIcon();
        if (icon != renderedIconSource) {
            renderedIconSource = icon;
            renderedIcon = vaadinx.EHelper.toVaadinIconComponent("JMenuItem", icon);
        }
        com.vaadin.flow.component.Component iconComponent = renderedIcon;
        Runnable onClick = makeOnClick();
        boolean checkable = isCheckableNode();
        boolean checked = checkable && isCheckedNode();
        return new com.vaadin.swingbridge.surrogates.MenuNode(
                getText(),
                iconComponent,
                effEnabled,
                /* separator */ false,
                checkable,
                checked,
                accelerator,
                onClick,
                children);
    }

    /**
     * Children produced by JMenu; leaf items return empty. Overridden
     * in JMenu to walk its child list.
     */
    java.util.List<com.vaadin.swingbridge.surrogates.MenuNode> collectChildren(boolean parentEnabled) {
        return java.util.Collections.emptyList();
    }

    /**
     * Whether the {@link com.vaadin.swingbridge.surrogates.MenuNode} should request Vaadin
     * {@code MenuItem.setCheckable(true)}. Overridden in
     * JCheckBoxMenuItem / JRadioButtonMenuItem.
     */
    boolean isCheckableNode() {
        return false;
    }

    /**
     * Checkable-state value for the MenuNode's {@code checked} slot.
     * Only consulted when {@link #isCheckableNode()} is true. Overridden
     * in JCheckBoxMenuItem / JRadioButtonMenuItem.
     */
    boolean isCheckedNode() {
        return false;
    }

    /**
     * Build the onClick Runnable for the MenuNode. Top-level JMenus
     * (Vaadin auto-opens their submenu) return {@code null}; every other
     * item returns a Runnable that clicks it as the JDK's menu UI does,
     * through {@link #doClick(int) doClick(0)}: the model pulse fires the
     * item's Change / Action events, and flips a checkable item's selection
     * through its {@code ToggleButtonModel} and {@code ButtonGroup}.
     *
     * <p>A checkable item re-renders afterwards even when nothing changed:
     * Vaadin toggles a checkable {@code MenuItem} on the click itself, so a
     * group refusing to deselect its selection has to be shown again.
     *
     * <p>Body wraps in {@link vaadinx.EHelper#callSwing(Runnable)} per R_callswing_envelope
     * / D_callswing_loom: SJMenuBar's installed click listener invokes this Runnable
     * through {@code com.vaadin.swingbridge.surrogates.SHelper.callSwing} (inline per SD_sframe),
     * so the {@code callSwing} UI-fiber envelope must be applied on the
     * emulator side before user {@code actionPerformed} runs — otherwise
     * user code calling {@code JOptionPane.showXxxDialog} or modal
     * {@code dialog.setVisible(true)} fails {@code Dialog.parkUntilClose}'s
     * UI-fiber check.
     */
    Runnable makeOnClick() {
        return () -> vaadinx.EHelper.callSwing(() -> {
            doClick(0);
            if (isCheckableNode()) {
                notifyTreeMutated();
            }
        });
    }

    /** A checkable item's checked state is part of the rendered tree. */
    @Override
    void modelSelectionChanged() {
        notifyTreeMutated();
    }

    // ---- Text / icon / actionCommand / enabled — bubble on change ---

    @Override
    public void setText(java.lang.String text) {
        super.setText(text);
        notifyTreeMutated();
    }

    @Override
    public void setIcon(vaadinx.swing.Icon icon) {
        super.setIcon(icon);
        notifyTreeMutated();
    }

    @Override
    public void setActionCommand(java.lang.String command) {
        super.setActionCommand(command);
        notifyTreeMutated();
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean old = isEnabled();
        super.setEnabled(enabled);
        if (old != enabled) notifyTreeMutated();
    }

    // ---- Accelerator -------------------------------------------------

    public javax.swing.KeyStroke getAccelerator() {
        return accelerator;
    }

    /**
     * JDK fires "accelerator" PCE; the rebuild then walks to find this
     * item's MenuNode and re-installs the UI-scoped shortcut on the
     * new keystroke (or skips install when the keystroke is unmappable).
     */
    public void setAccelerator(javax.swing.KeyStroke keyStroke) {
        javax.swing.KeyStroke old = this.accelerator;
        if (old == null ? keyStroke == null : old.equals(keyStroke)) return;
        this.accelerator = keyStroke;
        firePropertyChange("accelerator", old, keyStroke);
        notifyTreeMutated();
    }

    // ---- MenuElement ------------------------------------------------

    public vaadinx.swing.MenuElement[] getSubElements() {
        return new vaadinx.swing.MenuElement[0];
    }

    /**
     * Returns a real object only because {@link vaadinx.swing.MenuElement}
     * is ported: against the JDK interface this had to be typed
     * {@code java.awt.Component}, which a {@code JMenuItem} is not,
     * so it could only return null (R_no_vaadin_in_api limb 1).
     *
     * @return {@code this} — the JDK's own answer, a menu item being its own
     *         {@code Component} representation.
     */
    public vaadinx.awt.Component getComponent() {
        return this;
    }

    public void menuSelectionChanged(boolean isIncluded) {
        vaadinx.EHelper.onNoop("JMenuItem", "menuSelectionChanged");
    }

    public void processKeyEvent(vaadinx.awt.event.KeyEvent e, vaadinx.swing.MenuElement[] path,
                                vaadinx.swing.MenuSelectionManager manager) {
        vaadinx.EHelper.onUnimplemented("JMenuItem", "processKeyEvent");
    }

    public void processMouseEvent(vaadinx.awt.event.MouseEvent event, vaadinx.swing.MenuElement[] path,
                                  vaadinx.swing.MenuSelectionManager manager) {
        vaadinx.EHelper.onUnimplemented("JMenuItem", "processMouseEvent");
    }

    public java.lang.String getUIClassID() {
        return "MenuItemUI";
    }

    public void updateUI() {
        // L&F swap — no-op.
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JMenuItem", "getAccessibleContext");
        return null;
    }

    public void addMenuKeyListener(javax.swing.event.MenuKeyListener l) {
        // R_match_swing_errors sub-bucket (a) — Vaadin doesn't surface menu key navigation
        // events typed today.
        vaadinx.EHelper.onUnimplemented("JMenuItem", "addMenuKeyListener", l);
    }

    public void removeMenuKeyListener(javax.swing.event.MenuKeyListener l) {
        vaadinx.EHelper.onUnimplemented("JMenuItem", "removeMenuKeyListener", l);
    }

    public javax.swing.event.MenuKeyListener[] getMenuKeyListeners() {
        return new javax.swing.event.MenuKeyListener[0];
    }

    public void addMenuDragMouseListener(javax.swing.event.MenuDragMouseListener l) {
        vaadinx.EHelper.onUnimplemented("JMenuItem", "addMenuDragMouseListener", l);
    }

    public void removeMenuDragMouseListener(javax.swing.event.MenuDragMouseListener l) {
        vaadinx.EHelper.onUnimplemented("JMenuItem", "removeMenuDragMouseListener", l);
    }

    public javax.swing.event.MenuDragMouseListener[] getMenuDragMouseListeners() {
        return new javax.swing.event.MenuDragMouseListener[0];
    }
}
