/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;

import javax.swing.WindowConstants;
import java.util.Collection;

/**
 * Surrogate for {@link javax.swing.JDialog}, the edit-form host.
 *
 * <h2>Why extend {@link SFrame}, not a sibling SDialog</h2>
 *
 * JDK shape is {@code JDialog} IS-A {@code Dialog} IS-A {@code Window} —
 * Dialog and Frame are siblings under Window, not parent / child. The
 * surrogate-side Window analog ({@link SFrame}) currently subsumes both
 * the {@code java.awt.Frame} state (title, owner, defaultCloseOperation
 * machinery via {@link SJFrame}) and the {@code java.awt.Window} state
 * (visible shadow, listener families, owner tree). SJDialog extends
 * SFrame to inherit that state directly rather than introducing an
 * SDialog tier — the modal/modalityType API surface lives on
 * {@code :emulators.awt.Dialog} (the emulator), and the Vaadin-first
 * modality knob is reachable on every surrogate via the inherited
 * {@code Dialog.setModality(ModalityMode)} from {@link SWindow}'s parent.
 *
 * <p>R_vaadin_first stance: this surrogate doesn't reproduce the AWT
 * {@code setModal(boolean)} / {@code setModalityType(ModalityType)} API
 * on the surrogate side — direct surrogate users reach Vaadin's
 * {@code setModality} natively. The emulator-layer {@code Dialog}
 * carries the AWT-shape modal API and mirrors into Vaadin's
 * {@code setModality} on the peer.
 *
 * <h2>RootPane / contentPane / JMenuBar scaffolding</h2>
 *
 * JDK shape has both {@code JFrame} and {@code JDialog} independently
 * implementing {@code RootPaneContainer} / lazy JRootPane — inheriting
 * from SJFrame would imply JDialog IS-A JFrame and leak that misshape
 * into migrated code that reads {@code dialog instanceof SJFrame}. The
 * public surface stays sibling-shaped here; the stateful machinery
 * (lazy panes, recursion guard, menubar slot) lives single-copy on the
 * package-private {@link RootPaneScaffold} shared with {@link SJFrame} /
 * {@link SJWindow} — SD_sjdialog's third-consumer dedup, resolved by
 * composition when SJWindow landed.
 *
 * <h2>Default close operation</h2>
 *
 * JDialog rejects {@code EXIT_ON_CLOSE} at set-time per JDK contract —
 * unlike JFrame, JDialog can't terminate the JVM. Only
 * {@code DO_NOTHING_ON_CLOSE} / {@code HIDE_ON_CLOSE} (default) /
 * {@code DISPOSE_ON_CLOSE} are valid. The {@link #processWindowEvent}
 * override layers the close-op switch on top of {@link SWindow}'s
 * standard listener dispatch — same shape as SJFrame minus the
 * EXIT_ON_CLOSE close-time throw (since set-time already rejects it).
 *
 * <h2>Content-pane mirror invariant</h2>
 *
 * Same as SJFrame: {@code dialog.getContentPane() == dialog.getRootPane().getContentPane()}
 * holds across {@link #setContentPane} swaps. {@code rootPaneCheckingEnabled}
 * routing prevents add/remove recursion during the bootstrap.
 */
public class SJDialog extends SFrame {

    // ---- Default close operation (JDialog surface) ----

    /**
     * Swing's default is {@link WindowConstants#HIDE_ON_CLOSE}. JDialog
     * does not accept {@code EXIT_ON_CLOSE} (rejected at set-time per
     * JDK contract — see {@link #setDefaultCloseOperation(int)}).
     */
    private int defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE;

    // ---- Content / root pane + menubar machinery ----

    /**
     * Shared RootPaneContainer engine — see {@link RootPaneScaffold} and
     * {@link SJFrame}'s field of the same name. Field initializer runs
     * after the SFrame ctor chain; the overrides null-guard for any
     * super-ctor-time add that could land before it.
     */
    private final RootPaneScaffold scaffold =
            new RootPaneScaffold(this, this::createRootPane);

    // ---- Constructors (mirror SFrame's) -----------------------------

    /** Untitled, no owner. */
    public SJDialog() {
        super();
    }

    /** Titled, no owner. Null title coerces to empty per SFrame. */
    public SJDialog(String title) {
        super(title);
    }

    /** Untitled, with owner. Null owner makes this top-level. */
    public SJDialog(SFrame owner) {
        super(owner);
    }

    /** Full ctor — forwards to {@link SFrame#SFrame(String, SFrame)}. */
    public SJDialog(String title, SFrame owner) {
        super(title, owner);
    }

    // ---- Default close operation ----

    /** Current close operation; one of {@code DO_NOTHING_ON_CLOSE / HIDE_ON_CLOSE / DISPOSE_ON_CLOSE}. */
    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    /**
     * Validate + apply. JDialog rejects {@link WindowConstants#EXIT_ON_CLOSE}
     * at set-time (JDK contract — only JFrame may terminate the JVM); the
     * other three values are accepted silently.
     * {@code DO_NOTHING_ON_CLOSE} disables the peer's ESC / outside-click
     * dismissal so the close gesture never fires.
     *
     * <p>Throws {@link IllegalArgumentException} on unknown values per
     * Swing's contract (D_never_fail_on_gaps — incomplete-emulation rule doesn't scope
     * over inputs Swing itself rejects).
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
        if (old == operation) return;
        this.defaultCloseOperation = operation;

        boolean dismissible = (operation != WindowConstants.DO_NOTHING_ON_CLOSE);
        setCloseOnEsc(dismissible);
        setCloseOnOutsideClick(dismissible);
        firePropertyChange("defaultCloseOperation", old, operation);
    }

    // ---- processWindowEvent override: apply close op after user listeners ----

    /**
     * JDialog-shape: let user listeners run first, then apply the default
     * close operation. HIDE calls {@link #setVisible(boolean) setVisible(false)}
     * — {@code setVisible} is idempotent, so on the native ESC / outside-click
     * path (where {@link SWindow#onPeerOpenedChanged} already flipped
     * {@code visible} to false before routing here) it early-returns, while
     * on the header close-X path (which fires {@code WINDOW_CLOSING} directly
     * via {@link SFrame#installHeaderCloseButton} without touching the peer's
     * opened state) it performs the real hide. DISPOSE calls through to
     * {@link #dispose()} for the detach + {@code WINDOW_CLOSED}; DO_NOTHING is
     * a genuine no-op (the window must stay open even when the close-X — which
     * is never disarmed — delivers the event). Unlike {@link SJFrame}, no
     * EXIT_ON_CLOSE branch — set-time rejection covers that case.
     */
    @Override
    public void processWindowEvent(SWindowEvent e) {
        super.processWindowEvent(e);
        if (e == null) return;
        if (e.getID() != SWindowEvent.WINDOW_CLOSING) return;
        switch (defaultCloseOperation) {
            case WindowConstants.DISPOSE_ON_CLOSE -> dispose();
            case WindowConstants.HIDE_ON_CLOSE -> setVisible(false);
            case WindowConstants.DO_NOTHING_ON_CLOSE -> { /* no-op — window stays open */ }
            default -> { /* unreachable (setter validates), drop */ }
        }
    }

    // ---- Content pane (JDialog surface, mirrors SJFrame) ----

    /** Lazy-creates if never accessed. Never returns null. */
    public Div getContentPane() {
        return scaffold.getContentPane();
    }

    /**
     * Swap the content pane. JDK contract: null throws
     * {@link java.awt.IllegalComponentStateException}; the mirror-into-
     * rootPane invariant holds across swaps. Carried by
     * {@link RootPaneScaffold}.
     */
    public void setContentPane(Div newPane) {
        scaffold.setContentPane(newPane);
    }

    // ---- add / remove routing (mirrors SJFrame) ----
    //
    // Scaffold-null guard covers super-ctor-time adds that could land
    // before the scaffold field initializer runs.

    @Override
    public void add(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContent(components);
        } else {
            super.add(components);
        }
    }

    @Override
    public void addComponentAtIndex(int index, Component component) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContentAtIndex(index, component);
        } else {
            super.addComponentAtIndex(index, component);
        }
    }

    @Override
    public void remove(Component... components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    @Override
    public void remove(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    @Override
    public void removeAll() {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeAllFromContent();
        } else {
            super.removeAll();
        }
    }

    // ---- JMenuBar slot (mirrors SJFrame) -----------------------------

    /**
     * Attach a menubar. Pass {@code null} to detach. Same idiom as
     * {@link SJFrame#setJMenuBar(SJMenuBar)}; slot semantics live on
     * {@link RootPaneScaffold}.
     */
    public void setJMenuBar(SJMenuBar bar) {
        scaffold.setMenuBar(bar);
    }

    /** {@code null} until {@link #setJMenuBar} attaches one. */
    public SJMenuBar getJMenuBar() {
        return scaffold.getMenuBar();
    }

    // ---- Root pane (mirrors SJFrame) ---------------------------------

    @Override
    public SJRootPane getRootPane() {
        return scaffold.getRootPane();
    }

    /**
     * Replaces the root pane, as the JDK's protected {@code setRootPane} does: the outgoing
     * one leaves this window and {@code root} becomes its one child, holding the content pane
     * from then on. Public so the emulator layer can hand down the peer of the root pane it
     * built itself, which is how one root pane serves both layers.
     *
     * @param root {@code null} leaves the window without a root pane, as the JDK allows
     */
    public void setRootPane(SJRootPane root) {
        scaffold.setRootPane(root);
    }

    /**
     * Subclass hook matching JDK JDialog's {@code createRootPane}.
     * Called once on first {@link #getRootPane()} read.
     */
    protected SJRootPane createRootPane() {
        return new SJRootPane();
    }

    // ---- Layered / glass pane pass-throughs (mirrors SJFrame) --------

    public Component getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(Component layered) {
        getRootPane().setLayeredPane(layered);
    }

    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(Component glass) {
        getRootPane().setGlassPane(glass);
    }

    public Button getDefaultButton() {
        return getRootPane().getDefaultButton();
    }

    // ---- L&F class ID + paramString ---------------------------------

    /** Matches {@code javax.swing.JDialog.getUIClassID()}. */
    public String getUIClassID() {
        return "DialogUI";
    }

    /**
     * Extends {@link SFrame#paramString()} with the defaultCloseOperation
     * report. Diagnostic only — not a contract users should rely on.
     */
    @Override
    public String paramString() {
        return super.paramString()
                + ",defaultCloseOperation=" + closeOpName(defaultCloseOperation);
    }

    private static String closeOpName(int op) {
        return switch (op) {
            case WindowConstants.DO_NOTHING_ON_CLOSE -> "DO_NOTHING_ON_CLOSE";
            case WindowConstants.HIDE_ON_CLOSE       -> "HIDE_ON_CLOSE";
            case WindowConstants.DISPOSE_ON_CLOSE    -> "DISPOSE_ON_CLOSE";
            default                                  -> "UNKNOWN_CLOSE_OPERATION";
        };
    }
}
