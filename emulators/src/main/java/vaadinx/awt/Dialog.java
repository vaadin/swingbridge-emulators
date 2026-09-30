/*
 * Copyright (c) 1995, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Dialog
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-finished emulator for java.awt.Dialog, rendered by
// com.vaadin.swingbridge.surrogates.SFrame (the same surrogate Window uses) — Dialog adds
// the modal / modalityType / title / resizable surface on top of Window's
// peer-sync, owner chain, and listener families. It owns that state as the JDK's does:
// the fields and bodies are the JDK's, and no getter reads the peer
// (D_emulator_owned_state). Non-leaf in the
// public hierarchy: javax.swing.JDialog extends it, so a protected
// (Component peer, ...) ctor stays open for that subclass to thread its
// own peer through (D_peer_ctor_injection); R_leaf_peer_lockdown lock-down lives on JDialog instead.
//
// Modal show behaviour (setVisible(true) parking the calling thread
// until dispose when modal) parks a UI fiber of vaadin-blocking-dialogs
// — see show() / parkUntilClose() below. Non-blocking modal
// (setModal(true) + setVisible(true) returning immediately, listening
// for windowClosed) parks nothing — that's the CRUD edit-form path.

/** Emulator for {@link java.awt.Dialog}, rendered by {@link com.vaadin.swingbridge.surrogates.SFrame}. */
public class Dialog extends vaadinx.awt.Window {

    /**
     * AWT's modality type — controls which other windows the modal Dialog
     * blocks. Mirrors {@link java.awt.Dialog.ModalityType} verbatim;
     * import-swap from {@code java.awt.Dialog.ModalityType} to
     * {@code vaadinx.awt.Dialog.ModalityType} works because the constant
     * names line up.
     *
     * <p>Behavioural mapping under R_vaadin_first: all non-{@code MODELESS} types
     * collapse to a single Vaadin {@code Dialog.modal=true}, since
     * Vaadin's overlay only supports a binary modal flag. The type is
     * stored verbatim for round-trip ({@link #getModalityType()} reads
     * back what was set), but the actual blocking behaviour is the same
     * for {@code APPLICATION_MODAL} / {@code DOCUMENT_MODAL} /
     * {@code TOOLKIT_MODAL}: the dialog overlay traps focus until closed.
     * {@code TOOLKIT_MODAL}'s "block every other top-level window in this
     * Java toolkit" can't be honored — there's only one Vaadin UI per
     * tab — but for a single-UI app the observable difference vs.
     * {@code APPLICATION_MODAL} is nil.
     */
    public static enum ModalityType {
        /** Non-modal: dialog doesn't block any other windows. */
        MODELESS,
        /** Modal w.r.t. the document subtree the dialog belongs to. */
        DOCUMENT_MODAL,
        /** Modal w.r.t. the entire application (same VM). */
        APPLICATION_MODAL,
        /** Modal w.r.t. all top-level windows in the Java toolkit. */
        TOOLKIT_MODAL
    }

    /**
     * AWT's default modality type for {@code setModal(true)} and the
     * boolean-taking ctors: {@code APPLICATION_MODAL}.
     */
    public static final ModalityType DEFAULT_MODALITY_TYPE = ModalityType.APPLICATION_MODAL;

    // The JDK's fields, under its names. The constructors assign title directly, so it
    // stays null when a constructor is given null.
    private java.lang.String title;
    private boolean resizable = true;

    // Non-modal by default — matches AWT's no-arg / owner-only ctors. The
    // boolean / ModalityType-taking ctors override this in their bodies.
    private ModalityType modalityType = ModalityType.MODELESS;

    // Every public ctor funnels into (Window, String, ModalityType), as the JDK's do, so a
    // subclass's setModalityType override runs once during construction and its setTitle /
    // setModal overrides do not run at all.

    // Frame-owner ctors -------------------------------------------------

    public Dialog(vaadinx.awt.Frame owner) {
        this(owner, "", false);
    }

    public Dialog(vaadinx.awt.Frame owner, boolean modal) {
        this(owner, "", modal);
    }

    public Dialog(vaadinx.awt.Frame owner, java.lang.String title) {
        this(owner, title, false);
    }

    public Dialog(vaadinx.awt.Frame owner, java.lang.String title, boolean modal) {
        this(owner, title, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
    }

    public Dialog(vaadinx.awt.Frame owner, java.lang.String title, boolean modal,
                  java.awt.GraphicsConfiguration gc) {
        // GraphicsConfiguration doesn't map onto a browser (R_layouts_close_enough — no pixel
        // geometry / screen devices), so we accept and ignore it.
        this(owner, title, modal);
    }

    // Dialog-owner ctors ------------------------------------------------

    public Dialog(vaadinx.awt.Dialog owner) {
        this(owner, "", false);
    }

    public Dialog(vaadinx.awt.Dialog owner, java.lang.String title) {
        this(owner, title, false);
    }

    public Dialog(vaadinx.awt.Dialog owner, java.lang.String title, boolean modal) {
        this(owner, title, modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
    }

    public Dialog(vaadinx.awt.Dialog owner, java.lang.String title, boolean modal,
                  java.awt.GraphicsConfiguration gc) {
        // GraphicsConfiguration accepted-and-ignored — see Frame ctor.
        this(owner, title, modal);
    }

    // Window-owner ctors (ModalityType-taking) -------------------------

    public Dialog(vaadinx.awt.Window owner) {
        this(owner, "", ModalityType.MODELESS);
    }

    public Dialog(vaadinx.awt.Window owner, ModalityType modalityType) {
        this(owner, "", modalityType);
    }

    public Dialog(vaadinx.awt.Window owner, java.lang.String title) {
        this(owner, title, ModalityType.MODELESS);
    }

    /** @throws IllegalArgumentException if {@code owner} is neither a {@link Frame} nor a {@code Dialog} */
    public Dialog(vaadinx.awt.Window owner, java.lang.String title, ModalityType modalityType) {
        this(new com.vaadin.swingbridge.surrogates.SFrame(windowOwnerToSFrame(owner)), owner, title, modalityType);
    }

    public Dialog(vaadinx.awt.Window owner, java.lang.String title, ModalityType modalityType,
                  java.awt.GraphicsConfiguration gc) {
        // GraphicsConfiguration accepted-and-ignored — see Frame ctor.
        this(owner, title, modalityType);
    }

    /**
     * The JDK's {@code Dialog(Window, String, ModalityType)} body over a given peer:
     * {@code JDialog} threads its {@code SJDialog} through here (D_peer_ctor_injection),
     * since the public ctors hardcode {@code SFrame}.
     *
     * @throws IllegalArgumentException if {@code owner} is neither a {@link Frame} nor a {@code Dialog}
     */
    protected Dialog(com.vaadin.flow.component.Component peer, vaadinx.awt.Window owner,
                     java.lang.String title, ModalityType modalityType) {
        super(peer);
        this.owner = owner;
        if (owner != null) owner.addOwnedWindow(this);
        // Checked after the owner registration, as the JDK's Window(owner) super ctor has
        // already registered it by then.
        if (owner != null && !(owner instanceof Frame) && !(owner instanceof Dialog)) {
            throw new IllegalArgumentException("Wrong parent window");
        }
        this.title = title;
        flushTitle();
        syncInitialPeerModality();
        setModalityType(modalityType);
    }

    /**
     * Push our initial modality state to the peer at construction. Vaadin
     * Dialog defaults to {@code VISUAL} (modal curtain + RPC allowed),
     * which doesn't match AWT's MODELESS default; without this sync, a
     * fresh AWT-Dialog whose {@code modalityType == MODELESS} would still
     * render with a Vaadin overlay curtain.
     */
    private void syncInitialPeerModality() {
        if (getPeer() instanceof com.vaadin.flow.component.dialog.Dialog d) {
            d.setModality(this.modalityType != ModalityType.MODELESS
                    ? com.vaadin.flow.component.ModalityMode.STRICT
                    : com.vaadin.flow.component.ModalityMode.MODELESS);
        }
    }

    private static com.vaadin.swingbridge.surrogates.SFrame windowOwnerToSFrame(vaadinx.awt.Window w) {
        if (w == null) return null;
        return w.getPeer() instanceof com.vaadin.swingbridge.surrogates.SFrame sf ? sf : null;
    }

    // Title -------------------------------------------------------------

    /** The title, which is {@code null} if the dialog was given none — unlike {@link Frame#getTitle}. */
    public java.lang.String getTitle() {
        return title;
    }

    public void setTitle(java.lang.String title) {
        java.lang.String oldTitle = this.title;
        synchronized (this) {
            this.title = title;
        }
        // Pushed after the monitor is released: an off-thread push takes the session lock.
        flushTitle();
        firePropertyChange("title", oldTitle, title);
    }

    /** SFrame renders a null title as none (SD_sframe); its own "title" event stays on its own layer. */
    private void flushTitle() {
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SFrame sf) {
            withPeer(p -> sf.setTitle(this.title));
        }
    }

    // Modal / modality type --------------------------------------------

    /**
     * AWT contract: {@code isModal()} returns {@code modalityType != MODELESS}.
     */
    public boolean isModal() {
        return modalityType != ModalityType.MODELESS;
    }

    /**
     * AWT shape: {@code setModal(true)} sets the modality type to
     * {@code DEFAULT_MODALITY_TYPE} ({@code APPLICATION_MODAL});
     * {@code setModal(false)} sets it to {@code MODELESS}. The actual
     * modal flag is stored on the modality-type field.
     */
    public void setModal(boolean modal) {
        setModalityType(modal ? DEFAULT_MODALITY_TYPE : ModalityType.MODELESS);
    }

    public ModalityType getModalityType() {
        return modalityType;
    }

    /**
     * Store the modality type and mirror the bool ({@code != MODELESS})
     * to the Vaadin Dialog peer so the overlay traps focus correctly.
     * Null coerces to {@code MODELESS} per AWT contract.
     */
    public void setModalityType(ModalityType type) {
        ModalityType normalized = type != null ? type : ModalityType.MODELESS;
        if (this.modalityType == normalized) return;
        boolean newModal = normalized != ModalityType.MODELESS;
        this.modalityType = normalized;
        if (getPeer() instanceof com.vaadin.flow.component.dialog.Dialog d) {
            withPeer(p -> d.setModality(newModal
                    ? com.vaadin.flow.component.ModalityMode.STRICT
                    : com.vaadin.flow.component.ModalityMode.MODELESS));
        }
        // No PropertyChangeEvent: java.awt.Dialog.setModalityType assigns
        // modalityType and derives the modal bit, and setModal is a two-liner
        // that delegates here. Neither is a bound property (D_property_fanout_audit).
    }

    // Resizable -------------------------------------------------------

    public boolean isResizable() {
        return resizable;
    }

    /**
     * Also drives the peer's resize grips, since a Vaadin Dialog overlay has real ones.
     * Fires no {@code PropertyChangeEvent}, unlike {@link Frame#setResizable}.
     */
    public void setResizable(boolean resizable) {
        synchronized (this) {
            this.resizable = resizable;
        }
        // Pushed after the monitor is released, reading the field so racing writers leave
        // the peer on the last state. The JDK's invalidateIfValid() is not reproduced
        // (R_layouts_close_enough).
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SFrame sf) {
            withPeer(p -> sf.setResizable(this.resizable));
        }
    }

    /** R_swing_is_truth field shadow — see {@link #setUndecorated(boolean)}. AWT default false. */
    private boolean undecorated;

    public boolean isUndecorated() {
        return undecorated;
    }

    public void setUndecorated(boolean b) {
        // Same shape as Frame.setUndecorated: R_swing_is_truth mirror driving SWindow's
        // emul-undecorated CSS class (strips header/padding/radius; keeps
        // box-shadow per the R_layouts_close_enough divergence documented at
        // SWindow.setUndecoratedChrome), with JDK Dialog's
        // displayable-time throw (R_match_swing_errors; JDK message verbatim).
        if (isDisplayable()) {
            throw new java.awt.IllegalComponentStateException("The dialog is displayable.");
        }
        this.undecorated = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw) {
            withPeer(p -> sw.setUndecoratedChrome(b));
        }
    }

    // setOpacity / setShape / setBackground: same shape as Frame's, and for
    // the same JDK reason — per-pixel translucency and non-rectangular
    // outlines require an undecorated dialog, so a decorated one throws (R_match_swing_errors:
    // real Swing rejects these inputs). Each chains to Window's, which keeps
    // its R_layouts_close_enough stance: setOpacity WARNs, setShape WARNs only on non-null,
    // setBackground reaches Component's real CSS writer. JDK messages
    // verbatim — note they say "dialog", not "frame".

    @Override
    public void setOpacity(float opacity) {
        if (opacity < 1.0f && !isUndecorated()) {
            throw new java.awt.IllegalComponentStateException("The dialog is decorated");
        }
        super.setOpacity(opacity);
    }

    @Override
    public void setShape(java.awt.Shape shape) {
        if (shape != null && !isUndecorated()) {
            throw new java.awt.IllegalComponentStateException("The dialog is decorated");
        }
        super.setShape(shape);
    }

    @Override
    public void setBackground(java.awt.Color bgColor) {
        if (bgColor != null && bgColor.getAlpha() < 255 && !isUndecorated()) {
            throw new java.awt.IllegalComponentStateException("The dialog is decorated");
        }
        super.setBackground(bgColor);
    }

    // Blocking modal show ----------------------------------------------
    //
    // JDK contract: Dialog.setVisible(true) on a modal dialog blocks the
    // calling thread until the dialog is closed (via dispose() /
    // setVisible(false) / user gesture). We honour this on the emulator
    // layer by parking the calling UI fiber on a future that's completed
    // when the dialog closes; the park releases the session lock, so the
    // dialog flushes to the browser instead of the request thread blocking
    // forever.
    //
    // Surrogate-side stays sync per SD_sframe — SJDialog.setVisible (inherited
    // from SFrame) returns immediately. Modal blocking is emulator-only.

    private java.util.concurrent.CompletableFuture<Void> modalClosed;

    /**
     * The blocking-modal park splits across {@link #show()} and {@link #hide()},
     * where the JDK puts it, so this stays the JDK's own pure delegation:
     *
     * <ul>
     *   <li>{@link #show()} — after {@code super.show()} has opened the peer,
     *       {@link #isModal()} parks the caller on a fresh future until
     *       close.</li>
     *   <li>{@link #hide()} — releases any modal latch, mirroring JDK Dialog's
     *       contract that a programmatic hide unblocks a parked modal show.</li>
     * </ul>
     */
    @Override
    public void setVisible(boolean b) {
        // JDK shape: pure delegation. The modal park lives in show(), which is
        // where the JDK's own blocking loop lives — see show() below.
        super.setVisible(b);
    }

    /**
     * Show, then (if modal) block — <b>two steps, not one</b>, which is what lets a
     * background thread show a modal at all.
     *
     * <p>The JDK's own shape, from {@code java.awt.Dialog.show()} /
     * {@code WaitDispatchSupport.enter()}: {@code conditionalShow} realises the dialog
     * <em>first</em>, and only then does a modal enter its secondary loop — which, for a
     * non-EDT caller, posts the pump to the EDT and blocks the caller alone. So real Swing
     * blocks exactly one thread while the EDT stays free to deliver the click that ends
     * it. Vaadin's request threads play the EDT's part: {@code super.show()} hops the peer
     * write onto the UI thread and returns, and the caller blocks below, outside that hop.
     */
    @Override
    public void show() {
        if (!isModal()) {
            super.show();
            return;
        }
        // Armed before the show, not in parkUntilClose: a background caller's show hops
        // and returns, so a click can land before the park — and would find no future to
        // complete, leaving the worker parked on a close that already happened.
        java.util.concurrent.CompletableFuture<Void> closed = new java.util.concurrent.CompletableFuture<>();
        this.modalClosed = closed;
        try {
            super.show();
            parkUntilClose(closed);
        } finally {
            this.modalClosed = null;
        }
    }

    /**
     * A modal show is followed by {@code parkUntilClose}, so its peer write is the one
     * that must not be quietly dropped — see
     * {@link vaadinx.awt.Window#blocksAfterVisibilityChange(boolean)}.
     */
    @Override
    protected boolean blocksAfterVisibilityChange(boolean b) {
        return b && isModal();
    }

    @Override
    public void hide() {
        super.hide();
        releaseModalLatch();
    }

    /**
     * Override of {@link vaadinx.awt.Window#dispose()} that releases any
     * modal latch after the standard dispose work — mirrors JDK Dialog's
     * "dispose unblocks a parked modal show" contract.
     */
    @Override
    public void dispose() {
        super.dispose();
        releaseModalLatch();
    }

    /**
     * Release on peer-originated close too. {@code WINDOW_CLOSING} fires
     * via {@link vaadinx.awt.Window#processWindowEvent(vaadinx.awt.event.WindowEvent)}
     * for ESC / outside-click / header close. JDialog's close-op switch
     * may dispose (which releases too — countDown is idempotent on a
     * 1-count latch), but {@code HIDE_ON_CLOSE} closes the peer without
     * disposing, and that path also needs to release the latch.
     */
    @Override
    protected void processWindowEvent(vaadinx.awt.event.WindowEvent e) {
        super.processWindowEvent(e);
        if (e != null && e.getID() == vaadinx.awt.event.WindowEvent.WINDOW_CLOSING) {
            releaseModalLatch();
        }
    }

    /**
     * Block until the dialog closes — <b>the same one line on both threads</b>, because
     * what {@code latch.await()} does depends on who is calling.
     *
     * <ul>
     *   <li><b>UI thread.</b> The caller is a loom virtual thread whose carrier is the
     *       request thread holding the session lock. Awaiting unmounts the carrier, the
     *       lock goes, the access queue drains and the response flushes (D_callswing_loom).
     *       This is why a non-VT UI-thread caller is still refused below: it would block
     *       the platform request thread outright.</li>
     *   <li><b>Background thread.</b> The caller is an ordinary pool thread holding
     *       nothing, so awaiting simply blocks it. {@link #show()} has already flushed the
     *       dialog, and the request threads stay free to service the click that ends it.</li>
     * </ul>
     *
     * The wake is an ordinary Vaadin request either way — click → UI thread → callSwing →
     * {@link #releaseModalLatch()}.
     */
    private void parkUntilClose(java.util.concurrent.CompletableFuture<Void> closed) {
        // Shutdown guard (D_shutdown_lifecycle): during the session-destroy → WINDOW_CLOSING
        // dispatch there is no browser to answer a modal, so a park would
        // block forever. Throw an actionable message ahead of the generic
        // checkInUIFiber one (which would also fire, since the dispatch
        // runs outside any UI fiber — but its wording wouldn't tell a
        // migrator that the real cause is shutdown).
        if (vaadinx.EHelper.isShuttingDown()) {
            throw new IllegalStateException(
                    "Application is shutting down — modal dialogs and browser round-trips are "
                            + "unavailable inside WINDOW_CLOSING listeners. Do server-side cleanup only "
                            + "(save state, release resources); ask-the-user-on-exit is not supported.");
        }
        final boolean onUIThread = vaadinx.swing.SwingUtilities.isEventDispatchThread();
        if (onUIThread) {
            // A UI-thread caller must be in a UI fiber, or the await below blocks the
            // platform request thread and nothing can ever deliver the close (the loom
            // rescue trigger, D_gap_severity_triage). A background caller has no such
            // constraint — it is precisely the case this guard used to reject.
            com.github.mvysny.blockingdialogs.UIFibers.checkInUIFiber();
        }
        try {
            if (onUIThread) {
                // The peer anchors the wait: a tab close or session destroy detaches it and ends
                // the park in BrowserSessionClosedError (R_match_swing_errors case (8)).
                // On resume UI.getCurrent() is the UI the peer was last attached to, so a
                // chained dialog / post-dialog code lands on the right UI across an F5
                // @PreserveOnRefresh teleport. See EHelper.awaitModal.
                vaadinx.EHelper.awaitModal(getPeer(), closed);
            } else {
                // Q_rebind_skip: a plain wait on a background thread. Parking through the UI
                // fiber would *install* a current UI on a worker that deliberately has none —
                // silencing checkUIThread()'s off-EDT WARN for the rest of the body and
                // making every later peer write look like legitimate UI-thread access
                // while holding no lock.
                closed.get();
            }
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("The modal close future never fails", e);
        } catch (InterruptedException ie) {
            // Only the background wait gets here: on the UI thread parkAndAwait turns an
            // interrupt into the CancellationException that ends the UI fiber.
            Thread.currentThread().interrupt();
            // Returning normally here is indistinguishable from the user dismissing
            // the dialog — JOptionPane would report CLOSED_OPTION — and "the user
            // declined" and "nobody will ever answer" are different facts: the worker
            // would run on against a browser that is gone, doing dead work on a
            // fabricated answer. The interrupt arrives from a session destroy
            // (SwingWorker's session-scoped pool shutdownNow()) or from
            // worker.cancel(true); the message covers both, since the answer is the
            // same either way.
            throw new vaadinx.BrowserSessionClosedError(
                    "This thread was waiting for an answer from a modal dialog and was "
                            + "interrupted before one arrived — its session is going away, or "
                            + "the work was cancelled. No answer can arrive now.", ie);
        }
    }

    private void releaseModalLatch() {
        java.util.concurrent.CompletableFuture<Void> closed = this.modalClosed;
        if (closed != null) {
            closed.complete(null);
        }
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Dialog", "getAccessibleContext");
        return null;
    }

    @Override
    protected java.lang.String paramString() {
        java.lang.String str = super.paramString() + "," + modalityType;
        if (title != null) {
            str += ",title=" + title;
        }
        return str;
    }
}
