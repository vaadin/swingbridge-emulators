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
 * This file is derived from OpenJDK's javax.swing.JDialog
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.swingbridge.surrogates.SFrame;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJDialog;
import com.vaadin.swingbridge.surrogates.SJRootPane;
import vaadinx.EHelper;
import vaadinx.awt.Component;
import vaadinx.awt.Container;
import vaadinx.awt.Dialog;
import vaadinx.awt.Frame;
import vaadinx.awt.GraphicsConfiguration;
import vaadinx.awt.LayoutManager;
import vaadinx.awt.Window;
import vaadinx.awt.event.WindowEvent;

import javax.accessibility.AccessibleContext;
import javax.swing.WindowConstants;
import java.awt.AWTEvent;
import java.awt.HeadlessException;
import java.awt.IllegalComponentStateException;

// Hand-finished emulator for javax.swing.JDialog. Edit-form host.
// Mirrors JFrame's shape: own contentPane (Container)
// + lazy JRootPane holder + rootPaneCheckingEnabled redirect for
// add/remove/setLayout, JMenuBar slot delegating to SJDialog, plus the
// JDialog-specific defaultCloseOperation that rejects EXIT_ON_CLOSE at
// set-time per JDK contract.
//
// R_leaf_peer_lockdown leaf lock-down: peer is hardcoded to SJDialog;
// no protected (Component peer) ctor. Subclasses for behaviour (custom
// JDialog with extra event wiring) inherit the locked peer; subclasses
// that wanted to swap the peer don't have a seam.
//
// Modal show: Dialog already mirrors setModal/setModalityType
// to peer.setModality. Blocking modal (setVisible(true) parks the
// calling thread until dispose) parks through Dialog.parkUntilClose.

/** Emulator for {@link javax.swing.JDialog}. R_leaf_peer_lockdown-locked peer is {@link SJDialog}. */
public class JDialog extends Dialog
        implements WindowConstants, RootPaneContainer {

    // ---- Content pane / root pane (mirrors JFrame) -----------------

    // The dialog's one child, as in Swing: the root pane holds the layered pane
    // (menu bar + content pane) and the glass pane beside it. Planted by
    // dialogInit, so never null after construction.
    protected JRootPane rootPane;
    protected boolean rootPaneCheckingEnabled;

    // ---- Default close operation (JDialog surface) ------------------

    /**
     * Swing's default is {@link WindowConstants#HIDE_ON_CLOSE}.
     * Unlike JFrame, JDialog rejects {@code EXIT_ON_CLOSE} at set-time
     * (JDK contract — only JFrame may terminate the JVM).
     */
    private int defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE;

    // ============================================================
    // Constructors — the JDK's 16 signatures, chained as the JDK's are.
    //
    // Each lands in Dialog's peer seam, which runs the JDK's
    // Dialog(Window, String, ModalityType) body over an SJDialog
    // (R_leaf_peer_lockdown): title assigned, setModalityType called,
    // before dialogInit. A null Frame owner stays null: the JDK's shared
    // owner frame is not emulated.
    // ============================================================

    public JDialog() throws HeadlessException {
        this((Frame) null, false);
    }

    // Frame-owner ctors -------------------------------------------------

    public JDialog(Frame owner) {
        this(owner, false);
    }

    public JDialog(Frame owner, boolean modal) {
        this(owner, "", modal);
    }

    public JDialog(Frame owner, String title) {
        this(owner, title, false);
    }

    public JDialog(Frame owner, String title, boolean modal) {
        super(SJDialog.class,
                () -> new SJDialog(unwrapSFrame(owner)), owner, title,
                modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
        dialogInit();
    }

    public JDialog(Frame owner, String title, boolean modal,
                   GraphicsConfiguration gc) {
        // GraphicsConfiguration accepted-and-ignored (R_layouts_close_enough).
        this(owner, title, modal);
    }

    // Dialog-owner ctors ------------------------------------------------

    public JDialog(Dialog owner) {
        this(owner, false);
    }

    public JDialog(Dialog owner, boolean modal) {
        this(owner, "", modal);
    }

    public JDialog(Dialog owner, String title) {
        this(owner, title, false);
    }

    public JDialog(Dialog owner, String title, boolean modal) {
        super(SJDialog.class,
                () -> new SJDialog(unwrapSFrame(owner)), owner, title,
                modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
        dialogInit();
    }

    public JDialog(Dialog owner, String title, boolean modal,
                   GraphicsConfiguration gc) {
        this(owner, title, modal);
    }

    // Window-owner ctors ------------------------------------------------

    public JDialog(Window owner) {
        this(owner, ModalityType.MODELESS);
    }

    public JDialog(Window owner, Dialog.ModalityType modalityType) {
        this(owner, "", modalityType);
    }

    public JDialog(Window owner, String title) {
        this(owner, title, ModalityType.MODELESS);
    }

    /** @throws IllegalArgumentException if {@code owner} is neither a {@code Frame} nor a {@code Dialog} */
    public JDialog(Window owner, String title,
                   Dialog.ModalityType modalityType) {
        super(SJDialog.class,
                () -> new SJDialog(unwrapSFrame(owner)), owner, title, modalityType);
        dialogInit();
    }

    public JDialog(Window owner, String title,
                   Dialog.ModalityType modalityType,
                   GraphicsConfiguration gc) {
        this(owner, title, modalityType);
    }

    // ---- Owner-unwrap helper ----------------------------------------

    /**
     * Unwrap the SFrame peer from an emulator-layer Window for SJDialog's
     * SFrame-typed owner argument. Called from the peer factory, so on the UI
     * thread, where reading the owner's peer builds it.
     */
    private static SFrame unwrapSFrame(Window w) {
        if (w == null) return null;
        return w.getPeer() instanceof SFrame sf ? sf : null;
    }

    // ---- dialogInit / contentPane (mirrors JFrame.frameInit) --------

    /**
     * Mirror of {@code JFrame.frameInit}, and of the JDK's own
     * {@code JDialog.dialogInit}: build the root pane through
     * {@link #createRootPane()} and plant it via {@link #setRootPane}, then turn
     * root-pane checking on — each as a call, so a subclass override of any
     * hook actually runs (R_no_vaadin_in_api limb 2). Carries the JDK's full
     * five-line body: {@code enableEvents} and {@code setBackground} are here
     * because {@code JDialog.dialogInit} has them too, where
     * {@code JWindow.windowInit} has neither — see {@code JFrame.frameInit} for
     * why both land on deliberate nothings.
     */
    protected void dialogInit() {
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK);
        setLocale(JComponent.getDefaultLocale());
        setRootPane(createRootPane());
        setBackground(UIManager.controlColor());
        // Content pane fills the window body rather than sitting at intrinsic
        // height inside it. The pane-level classes come from the surrogate chain;
        // this adds the host-level one — see emul/swindow.css.
        // A write, so a content pane whose peer is lazy is built with a UI, not here.
        Container contentPane = getContentPane();
        withPeer(p -> SHelper.markContentPaneSpan(p, contentPane.getPeer(), null));
        setRootPaneCheckingEnabled(true);
    }

    /** Delegates to the root pane, as the JDK's does. */
    public Container getContentPane() {
        return getRootPane().getContentPane();
    }

    /**
     * Delegates to the root pane, as the JDK's does — so
     * {@code dialog.getContentPane()} and
     * {@code dialog.getRootPane().getContentPane()} are the same pane by
     * construction rather than by a mirror kept in step.
     */
    public void setContentPane(Container newPane) {
        if (newPane == null) {
            throw new IllegalComponentStateException("contentPane cannot be set to null");
        }
        Container old = getRootPane().getContentPane();
        if (newPane == old) return;
        getRootPane().setContentPane(newPane);
        withPeer(p -> SHelper.markContentPaneSpan(
                p, newPane.getPeer(), old == null ? null : old.getPeer()));
    }

    // ---- add/remove/setLayout redirect (mirrors JFrame) -------------

    @Override
    protected void addImpl(Component comp, Object constraints, int index) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().add(comp, constraints, index);
        } else {
            super.addImpl(comp, constraints, index);
        }
    }

    @Override
    public void setLayout(LayoutManager manager) {
        if (isRootPaneCheckingEnabled()) {
            getContentPane().setLayout(manager);
        } else {
            super.setLayout(manager);
        }
    }

    @Override
    public void remove(Component comp) {
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

    // ---- JRootPane (lazy, mirrors JFrame) ---------------------------

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
        // The surrogate holds the same root pane, so both layers share its default-button
        // wiring — see JFrame.setRootPane.
        withPeer(p -> {
            if (p instanceof SJDialog sjd) {
                sjd.setRootPane(newRootPane == null ? null
                        : (SJRootPane) newRootPane.getPeer());
            }
        });
    }

    /** A fresh {@code JRootPane}, which {@link #setRootPane} hands to the surrogate. */
    protected JRootPane createRootPane() {
        return new JRootPane();
    }

    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(JLayeredPane layered) {
        getRootPane().setLayeredPane(layered);
    }

    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(Component glass) {
        getRootPane().setGlassPane(glass);
    }

    // ---- Default close operation -----------------------------------

    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    /**
     * JDialog rejects {@code EXIT_ON_CLOSE} at set-time per JDK contract
     * — only JFrame may terminate the JVM. The other three values are
     * accepted silently. Validated here, as the JDK's body does, so the
     * throw does not wait for a peer; the gesture-disarm is the SJDialog peer's.
     *
     * @throws IllegalArgumentException on {@code EXIT_ON_CLOSE} or an unknown value
     */
    public void setDefaultCloseOperation(int operation) {
        if (operation != WindowConstants.DO_NOTHING_ON_CLOSE
                && operation != WindowConstants.HIDE_ON_CLOSE
                && operation != WindowConstants.DISPOSE_ON_CLOSE) {
            throw new IllegalArgumentException(
                    "defaultCloseOperation must be one of: DO_NOTHING_ON_CLOSE, "
                            + "HIDE_ON_CLOSE, or DISPOSE_ON_CLOSE");
        }
        int old = this.defaultCloseOperation;
        withPeer(p -> {
            if (p instanceof SJDialog sjd) sjd.setDefaultCloseOperation(operation);
        });
        if (old == operation) return;
        this.defaultCloseOperation = operation;
        firePropertyChange("defaultCloseOperation", old, operation);
    }

    /**
     * Mirrors {@code JFrame.processWindowEvent} minus the EXIT_ON_CLOSE
     * branch — JDialog rejects EXIT_ON_CLOSE at set-time.
     */
    @Override
    protected void processWindowEvent(WindowEvent e) {
        super.processWindowEvent(e);
        if (e == null) return;
        if (e.getID() != WindowEvent.WINDOW_CLOSING) return;
        switch (defaultCloseOperation) {
            case WindowConstants.DISPOSE_ON_CLOSE -> dispose();
            case WindowConstants.HIDE_ON_CLOSE,
                 WindowConstants.DO_NOTHING_ON_CLOSE -> { /* no-op — see Javadoc */ }
            default -> { /* unreachable (setter validates), drop */ }
        }
    }

    // ---- JMenuBar slot (mirrors JFrame) ----------------------------

    /**
     * The JDK's one-line delegation to {@code getRootPane().setJMenuBar(menu)},
     * which is now literally that — so {@code dialog.setJMenuBar(x)} and
     * {@code dialog.getRootPane().setJMenuBar(x)} are the same bar. It is planted
     * in the root pane's layered pane, above the content pane.
     *
     * <p>Fires no {@code "JMenuBar"} property change: neither class fires
     * anything. {@code JInternalFrame} is the only class in {@code javax.swing}
     * that does (its {@code MENU_BAR_PROPERTY}), which is why the absence here is
     * a decision and not an oversight (R_decline_effect_only, D_window_fanout).
     */
    public void setJMenuBar(JMenuBar bar) {
        getRootPane().setJMenuBar(bar);
    }

    public JMenuBar getJMenuBar() {
        return getRootPane().getJMenuBar();
    }

    // ---- paramString -----------------------------------------------

    @Override
    protected String paramString() {
        String defaultCloseOperationString;
        if (defaultCloseOperation == HIDE_ON_CLOSE) {
            defaultCloseOperationString = "HIDE_ON_CLOSE";
        } else if (defaultCloseOperation == DISPOSE_ON_CLOSE) {
            defaultCloseOperationString = "DISPOSE_ON_CLOSE";
        } else if (defaultCloseOperation == DO_NOTHING_ON_CLOSE) {
            defaultCloseOperationString = "DO_NOTHING_ON_CLOSE";
        } else defaultCloseOperationString = "";
        String rootPaneString = (rootPane != null ?
                                 rootPane.toString() : "");
        String rootPaneCheckingEnabledString = (rootPaneCheckingEnabled ?
                                                "true" : "false");

        return super.paramString() +
        ",defaultCloseOperation=" + defaultCloseOperationString +
        ",rootPane=" + rootPaneString +
        ",rootPaneCheckingEnabled=" + rootPaneCheckingEnabledString;
    }

    // ---- Misc ------------------------------------------------------

    public AccessibleContext getAccessibleContext() {
        EHelper.onUnimplemented("JDialog", "getAccessibleContext");
        return null;
    }

    // D_drag_and_drop: signature ported to the emulator type so migrated code compiles
    // against TransferHandler; window-level DnD wiring is out of
    // the D_drag_and_drop scope (JComponent / JList / JTable only) — drop-and-WARN.
    /**
     * Stores the handler and fires {@code "transferHandler"}; no drop target is
     * installed. Window-level drop is not wired — {@code JComponent}'s own
     * {@code setTransferHandler} is (D_drag_and_drop) — so what is declined is the effect
     * (R_decline_effect_only, D_owed_events).
     */
    public void setTransferHandler(TransferHandler h) {
        TransferHandler old = this.transferHandler;
        this.transferHandler = h;
        EHelper.onUnimplemented("JDialog", "setTransferHandler(drop target)", h);
        firePropertyChange("transferHandler", old, h);
    }

    // Inert window-level handler — stored so the getter and the bound property
    // are honest (R_decline_effect_only, D_owed_events).
    private TransferHandler transferHandler;

    public TransferHandler getTransferHandler() {
        if (transferHandler != null) return transferHandler;
        EHelper.onUnimplemented("JDialog", "getTransferHandler");
        return null;
    }
}
