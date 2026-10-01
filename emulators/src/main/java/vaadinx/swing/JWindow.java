/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JWindow
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JWindow, mirroring JDialog's
// shape minus the sections JWindow doesn't have: no title, no
// defaultCloseOperation (JWindow doesn't implement WindowConstants — the
// user cannot close it; there's no chrome), no JMenuBar slot. What's
// left is the RootPaneContainer subset: own contentPane + lazy JRootPane
// holder + rootPaneCheckingEnabled redirect for add/remove/setLayout.
//
// The emulator-side contentPane/rootPane scaffolding is a deliberate
// mirror-copy of JFrame's/JDialog's rather than a pulled-up base: the
// JDK itself duplicates this per RootPaneContainer implementor
// (java.awt.Window has no root pane), so mirror-copy is the R_swing_is_truth-faithful
// shape here. The surrogate side deduplicates via RootPaneScaffold
// composition instead — see com.vaadin.swingbridge.surrogates.RootPaneScaffold.
//
// R_leaf_peer_lockdown leaf lock-down: javax.swing.JWindow is a public-hierarchy leaf, so
// the peer is hardcoded to com.vaadin.swingbridge.surrogates.SJWindow; no protected
// (Component peer) ctor.

/** Emulator for {@link javax.swing.JWindow}. R_leaf_peer_lockdown-locked peer is {@link com.vaadin.swingbridge.surrogates.SJWindow}. */
public class JWindow extends vaadinx.awt.Window
        implements javax.accessibility.Accessible, vaadinx.swing.RootPaneContainer {

    // ---- Content pane / root pane (mirrors JDialog) -----------------

    // The window's one child, as in Swing: the root pane holds the layered pane
    // (content pane; JWindow has no menu bar) and the glass pane beside it.
    // Planted by windowInit, so never null after construction.
    protected JRootPane rootPane;
    protected boolean rootPaneCheckingEnabled;

    // ============================================================
    // Constructors — the 5 JDK signatures.
    //
    // JDK divergences, both deliberate:
    //  - JDK substitutes SwingUtilities.getSharedOwnerFrame() when the
    //    owner is null (so an ownerless JWindow still parents to an
    //    invisible shared frame). We keep owner = null instead: the
    //    shared frame exists to satisfy the native windowing system's
    //    parenting requirement, which a browser doesn't have, and
    //    nothing observable in migrated code depends on it without
    //    focus modeling (R_infra_not_surface).
    //  - JDK calls setFocusableWindowState(false) for ownerless
    //    JWindows. We don't model focus (R_infra_not_surface); replicating the call
    //    would only WARN on every construction.
    // ============================================================

    public JWindow() {
        super(new com.vaadin.swingbridge.surrogates.SJWindow(), null);
        windowInit();
    }

    public JWindow(vaadinx.awt.GraphicsConfiguration gc) {
        // Accepted and ignored: one viewport, one configuration.
        this();
    }

    public JWindow(vaadinx.awt.Frame owner) {
        super(new com.vaadin.swingbridge.surrogates.SJWindow(unwrapSWindow(owner)), owner);
        windowInit();
    }

    public JWindow(vaadinx.awt.Window owner) {
        super(new com.vaadin.swingbridge.surrogates.SJWindow(unwrapSWindow(owner)), owner);
        windowInit();
    }

    public JWindow(vaadinx.awt.Window owner, vaadinx.awt.GraphicsConfiguration gc) {
        this(owner);
    }

    // ---- Owner-unwrap helper ----------------------------------------

    /**
     * Unwrap the SWindow peer from an emulator-layer Window for
     * SJWindow's owner argument — any window-family emulator peer
     * (SFrame, SJFrame, SJDialog, SWindow, SJWindow) passes the
     * {@code instanceof SWindow} check. Public {@code getPeer()} is the
     * cross-package access path.
     */
    private static com.vaadin.swingbridge.surrogates.SWindow unwrapSWindow(vaadinx.awt.Window w) {
        if (w == null) return null;
        return w.getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw ? sw : null;
    }

    // ---- windowInit / contentPane (mirrors JDialog.dialogInit) ------

    /**
     * Mirror of JDK {@code JWindow.windowInit}: take the default locale, build
     * the root pane through {@link #createRootPane()} and plant it via
     * {@link #setRootPane}, then turn root-pane checking on — each as a call, so
     * a subclass override of any hook actually runs (R_no_vaadin_in_api limb 2).
     *
     * <p>Three lines where {@code JFrame.frameInit} /
     * {@code JDialog.dialogInit} have five: {@code JWindow.windowInit}
     * calls neither {@code enableEvents} nor {@code setBackground}
     * (JWindow.java:260). Reproduced rather than regularised — a
     * migrator's {@code setBackground} override must *not* fire from a
     * JWindow constructor.
     */
    protected void windowInit() {
        setLocale(JComponent.getDefaultLocale());
        setRootPane(createRootPane());
        // Content pane fills the window body rather than sitting at intrinsic
        // height inside it. The pane-level classes come from the surrogate chain;
        // this adds the host-level one — see emul/swindow.css.
        // A write, so a content pane whose peer is lazy is built with a UI, not here.
        vaadinx.awt.Container contentPane = getContentPane();
        withPeer(p -> com.vaadin.swingbridge.surrogates.SHelper.markContentPaneSpan(p, contentPane.getPeer(), null));
        setRootPaneCheckingEnabled(true);
    }

    /** Delegates to the root pane, as the JDK's does. */
    public vaadinx.awt.Container getContentPane() {
        return getRootPane().getContentPane();
    }

    /**
     * Delegates to the root pane, as the JDK's does — so
     * {@code window.getContentPane()} and
     * {@code window.getRootPane().getContentPane()} are the same pane by
     * construction rather than by a mirror kept in step.
     */
    public void setContentPane(vaadinx.awt.Container newPane) {
        if (newPane == null) {
            throw new java.awt.IllegalComponentStateException("contentPane cannot be set to null");
        }
        vaadinx.awt.Container old = getRootPane().getContentPane();
        if (newPane == old) return;
        getRootPane().setContentPane(newPane);
        withPeer(p -> com.vaadin.swingbridge.surrogates.SHelper.markContentPaneSpan(
                p, newPane.getPeer(), old == null ? null : old.getPeer()));
    }

    // ---- add/remove/setLayout redirect (mirrors JDialog) -------------

    @Override
    protected void addImpl(vaadinx.awt.Component comp, Object constraints, int index) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints, index);
        } else {
            super.addImpl(comp, constraints, index);
        }
    }

    @Override
    public void setLayout(vaadinx.awt.LayoutManager manager) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().setLayout(manager);
        } else {
            super.setLayout(manager);
        }
    }

    @Override
    public void remove(vaadinx.awt.Component comp) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().remove(comp);
        } else {
            super.remove(comp);
        }
    }

    protected void setRootPaneCheckingEnabled(boolean enabled) {
        this.rootPaneCheckingEnabled = enabled;
    }

    /**
     * Whether {@code add} / {@code remove} / {@code setLayout} redirect into the
     * content pane. The redirect consults this method rather than the field, so a
     * subclass override is honoured (R_no_vaadin_in_api second limb, D_r12_provenance).
     */
    protected boolean isRootPaneCheckingEnabled() {
        return rootPaneCheckingEnabled;
    }

    // ---- JRootPane (lazy, mirrors JDialog) ---------------------------

    public JRootPane getRootPane() {
        return rootPane;
    }

    /**
     * The JDK's body: remove the outgoing root pane, then add the incoming one,
     * with root-pane checking cleared so {@link #addImpl}'s redirect falls
     * through instead of routing the new root pane into the old one's content
     * pane.
     */
    protected void setRootPane(JRootPane newRootPane) {
        boolean wasChecking = rootPaneCheckingEnabled;
        rootPaneCheckingEnabled = false;
        try {
            if (rootPane != null) {
                super.remove(rootPane);
            }
            this.rootPane = newRootPane;
            if (newRootPane != null) {
                super.addImpl(newRootPane, null, -1);
            }
        } finally {
            rootPaneCheckingEnabled = wasChecking;
        }
    }

    protected JRootPane createRootPane() {
        // Share the SJRootPane surrogate with the SJWindow peer so any
        // default-button Enter wiring and other browser-side root-pane
        // state are unified across the emulator and surrogate layers —
        // same idiom as JFrame.createRootPane / JDialog.createRootPane.
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJWindow sjw) {
            return new JRootPane(sjw.getRootPane());
        }
        // Defensive fallback — R_leaf_peer_lockdown lock-down means no current ctor picks
        // a non-SJWindow peer, but a behaviour-only subclass could
        // theoretically override createRootPane and reach here.
        return new JRootPane();
    }

    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(JLayeredPane layered) {
        getRootPane().setLayeredPane(layered);
    }

    public vaadinx.awt.Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(vaadinx.awt.Component glass) {
        getRootPane().setGlassPane(glass);
    }

    // ---- Misc ---------------------------------------------------------

    public void update(java.awt.Graphics g) {
        // JDK JWindow.update forwards straight to paint (no background
        // clear). Container.update already delegates to paint and our
        // leaf paint() is an R_layouts_close_enough no-op; chain up so user overrides on
        // children still fire.
        super.update(g);
    }

    @Override
    protected String paramString() {
        // JDK JWindow appends the rootPaneCheckingEnabled report.
        return super.paramString()
                + ",rootPaneCheckingEnabled=" + rootPaneCheckingEnabled;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JWindow", "getAccessibleContext");
        return null;
    }

    // D_drag_and_drop: signature ported to the emulator type so migrated code compiles
    // against vaadinx.swing.TransferHandler; window-level DnD wiring is out of
    // the D_drag_and_drop scope (JComponent / JList / JTable only) — drop-and-WARN.
    public void setTransferHandler(vaadinx.swing.TransferHandler h) {
        vaadinx.swing.TransferHandler old = this.transferHandler;
        this.transferHandler = h;
        vaadinx.EHelper.onUnimplemented("JWindow", "setTransferHandler(drop target)", h);
        firePropertyChange("transferHandler", old, h);
    }

    // Inert window-level handler — stored so the getter and the bound property are
    // honest (R_decline_effect_only, D_owed_events). Same shape as JDialog's; this one was missed there only
    // because JWindow fires no bound property at all, which put the whole class
    // outside the reverse sweep's work-list (D_missing_constants).
    private vaadinx.swing.TransferHandler transferHandler;

    public vaadinx.swing.TransferHandler getTransferHandler() {
        if (transferHandler != null) return transferHandler;
        vaadinx.EHelper.onUnimplemented("JWindow", "getTransferHandler");
        return null;
    }
}
