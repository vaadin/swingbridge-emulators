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
import com.vaadin.flow.component.html.Div;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;

import javax.swing.WindowConstants;
import java.util.Collection;

/**
 * Surrogate for {@link javax.swing.JFrame} per SD_sjframe. Extends {@link SFrame}
 * (the {@code java.awt.Window} + {@code java.awt.Frame} surface) and adds
 * the JFrame-specific layer: {@code defaultCloseOperation} +
 * {@link WindowConstants} surface, content-pane routing, JRootPane holder,
 * and the {@code "FrameUI"} L&amp;F class id.
 *
 * <h2>Why inheritance, not composition</h2>
 *
 * JDK shape is JFrame IS-A Frame IS-A Window. SJFrame refines SFrame with
 * the JRootPane + close-op + content-pane surface so the surrogate
 * hierarchy mirrors JDK exactly: customer code that ported
 * {@code extends Frame} → {@code extends SFrame} doesn't pay for JFrame
 * affordances it doesn't use; customer code that ported
 * {@code extends JFrame} → {@code extends SJFrame} gets the full surface.
 *
 * <h2>Default close operation</h2>
 *
 * {@code HIDE} (default) / {@code DISPOSE} / {@code DO_NOTHING} map cleanly onto Dialog
 * ({@code setOpened(false)} / {@code removeFromParent} / blocked
 * {@code closeOnEsc}+{@code closeOnOutsideClick}). {@code EXIT_ON_CLOSE}
 * is silently accepted at set-time but throws {@link IllegalStateException}
 * at close-time per [D_gap_severity_triage](../emulators/decisions.md#D_gap_severity_triage)'s
 * second throw case. The {@link #processWindowEvent} override layers the
 * close-op switch on top of {@link SWindow}'s standard listener
 * dispatch.
 *
 * <h2>Content-pane routing</h2>
 *
 * JFrame's post-Java-5 contract: {@code frame.add(child)} routes into the
 * content pane. We reproduce this by overriding the {@link com.vaadin.flow.component.dialog.Dialog}
 * add/remove family to redirect into a lazy {@link Div} contentPane. The
 * stateful machinery (lazy panes, the {@code rootPaneCheckingEnabled}
 * recursion guard, menubar slot) lives single-copy on the package-private
 * {@link RootPaneScaffold} shared with {@link SJDialog} / {@link SJWindow}.
 *
 * <h2>Accessor naming — {@code getRootPane()}</h2>
 *
 * {@link com.vaadin.swingbridge.surrogates.swing.JComponentMixin#getRootPane()} returns
 * {@link SJRootPane} on the surrogate layer (the inherited signature
 * carries no type collision with
 * {@code javax.swing.JRootPane} — SJRootPane is the
 * surrogate-layer root-pane type, and the mixin's walk-and-return
 * surface this directly). SJFrame overrides {@code getRootPane()} to
 * return its own lazy-constructed pane rather than walking parents,
 * matching JDK JFrame's "I am the root-pane host" semantics.
 *
 * <h2>Lazy rootPane construction</h2>
 *
 * AWT contract: JRootPane accessors are lazy — most JFrames never
 * reach through {@code getRootPane()}, and eager construction would
 * allocate three panes per frame for nothing. {@link #getRootPane()}
 * constructs on first read via {@link #createRootPane()}; the factory
 * is a subclass hook matching JDK JFrame's override point. Before
 * first read, the root pane is null and {@link #setContentPane(Div)}
 * doesn't force-construct it — the setter just updates this class's
 * own contentPane Div, and the lazy seed at first read picks up the
 * current contentPane.
 *
 * <h2>Content-pane mirror invariant</h2>
 *
 * Migrated code that alternates {@code frame.getContentPane()} and
 * {@code frame.getRootPane().getContentPane()} expects both to report
 * the same Div across content-pane swaps. {@link #setContentPane(Div)}
 * mirrors the reference into any already-constructed rootPane.
 * Mirror-on-write beats a listener subscription — content pane swaps
 * are rare and cheap to bridge explicitly.
 */
public class SJFrame extends SFrame {

    // ---- Default close operation (JFrame surface) ----

    /**
     * Swing's default is {@link WindowConstants#HIDE_ON_CLOSE}. Migrated
     * apps typically override to {@code EXIT_ON_CLOSE} (silent at set-time;
     * throws at close-time per D_gap_severity_triage — see {@link #setDefaultCloseOperation(int)}).
     */
    private int defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE;

    // ---- Content / root pane + menubar machinery ----

    /**
     * Shared RootPaneContainer engine ({@link RootPaneScaffold}) — the
     * lazy contentPane Div, lazy {@link SJRootPane}, SD_sjmenubar/D_menu_tree menubar
     * slot, and the {@code rootPaneCheckingEnabled} idiom all live
     * there, single-copy across SJFrame / SJDialog / SJWindow. Our
     * overrides below stay one-liners consulting {@code scaffold.isChecking()}.
     * Field initializer runs after the SFrame ctor chain; the overrides
     * null-guard for any super-ctor-time add that could land before it.
     */
    private final RootPaneScaffold scaffold =
            new RootPaneScaffold(this, this::createRootPane);

    // ---- Constructors (mirror SFrame's) -----------------------------

    /** Untitled, no owner. */
    public SJFrame() {
        super();
    }

    /** Titled, no owner. Null title coerces to empty per SFrame. */
    public SJFrame(String title) {
        super(title);
    }

    /** Untitled, with owner. Null owner makes this top-level. */
    public SJFrame(SFrame owner) {
        super(owner);
    }

    /** Full ctor — forwards to {@link SFrame#SFrame(String, SFrame)}. */
    public SJFrame(String title, SFrame owner) {
        super(title, owner);
    }

    // ---- Default close operation ----

    /** Current close operation; one of {@link WindowConstants}'s four values. */
    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    /**
     * Validate + apply. All four {@link WindowConstants} values are
     * accepted silently at set-time. The throw trigger for
     * {@code EXIT_ON_CLOSE} fires at close-time (per
     * [D_gap_severity_triage](../emulators/decisions.md#D_gap_severity_triage)'s
     * second case): {@link #processWindowEvent} throws
     * {@link IllegalStateException} when a peer-originated close-attempt
     * lands while the op is {@code EXIT_ON_CLOSE}, so the migrator's
     * "terminate" intent is surfaced rather than silently ignored.
     * {@code DO_NOTHING_ON_CLOSE} disables the peer's ESC /
     * outside-click dismissal so the close gesture never fires.
     *
     * <p>Throws {@link IllegalArgumentException} on unknown values per
     * Swing's contract (D_never_fail_on_gaps — incomplete-emulation rule doesn't scope over
     * inputs Swing itself rejects).
     */
    public void setDefaultCloseOperation(int operation) {
        if (operation != WindowConstants.DO_NOTHING_ON_CLOSE
                && operation != WindowConstants.HIDE_ON_CLOSE
                && operation != WindowConstants.DISPOSE_ON_CLOSE
                && operation != WindowConstants.EXIT_ON_CLOSE) {
            throw new IllegalArgumentException(
                    "defaultCloseOperation must be one of: DO_NOTHING_ON_CLOSE, "
                            + "HIDE_ON_CLOSE, DISPOSE_ON_CLOSE, or EXIT_ON_CLOSE");
        }

        int old = this.defaultCloseOperation;
        if (old == operation) return;
        this.defaultCloseOperation = operation;

        // DO_NOTHING_ON_CLOSE must prevent the close gesture from reaching
        // WINDOW_CLOSING in the first place: by the time we'd see the
        // event, the peer has already closed and we can't uncommit that.
        // Other ops allow the close to fire, and processWindowEvent below
        // decides what to do with it.
        boolean dismissible = (operation != WindowConstants.DO_NOTHING_ON_CLOSE);
        setCloseOnEsc(dismissible);
        setCloseOnOutsideClick(dismissible);
        firePropertyChange("defaultCloseOperation", old, operation);
    }

    // ---- processWindowEvent override: apply close op after user listeners ----

    /**
     * JFrame-shape: let user listeners run first (they may call
     * {@code System.exit} or otherwise short-circuit), then apply the
     * default close operation. HIDE calls
     * {@link #setVisible(boolean) setVisible(false)} — {@code setVisible} is
     * idempotent, so on the native ESC / outside-click path (where
     * {@link SWindow#onPeerOpenedChanged} already flipped {@code visible} to
     * false before routing here) it early-returns, while on the header
     * close-X path (which fires {@code WINDOW_CLOSING} directly via
     * {@link SFrame#installHeaderCloseButton} without touching the peer's
     * opened state) it performs the real hide; DISPOSE calls through to
     * {@link #dispose()} for the detach + {@code WINDOW_CLOSED}; DO_NOTHING is
     * a genuine no-op (the window must stay open even when the close-X — which
     * is never disarmed — delivers the event).
     *
     * <p>{@code EXIT_ON_CLOSE} throws {@link IllegalStateException} per
     * [D_gap_severity_triage](../emulators/decisions.md#D_gap_severity_triage)'s
     * second throw case. The exception propagates per R_callswing_envelope to Vaadin's
     * {@code ErrorHandler}; the dialog stays visually closed because the
     * peer already closed before we got the close event, but the throw
     * surfaces the migrator's "terminate the JVM" intent rather than
     * letting it silently noop. A later revision may swap this to close the
     * Vaadin session instead.
     */
    @Override
    public void processWindowEvent(SWindowEvent e) {
        // super resolves to SFrame, which doesn't override — falls through
        // to WindowMixin's default (the listener fan-out). Then the
        // close-op switch below layers on top.
        super.processWindowEvent(e);
        if (e == null) return;
        if (e.getID() != SWindowEvent.WINDOW_CLOSING) return;
        switch (defaultCloseOperation) {
            case WindowConstants.DISPOSE_ON_CLOSE -> dispose();
            case WindowConstants.HIDE_ON_CLOSE -> setVisible(false);
            case WindowConstants.DO_NOTHING_ON_CLOSE -> { /* no-op — window stays open */ }
            case WindowConstants.EXIT_ON_CLOSE -> throw new IllegalStateException(
                    "EXIT_ON_CLOSE close-attempt on " + this
                            + " — Swing would terminate the JVM here. "
                            + "a Vaadin server can't honour that intent silently; "
                            + "switch to DISPOSE_ON_CLOSE / HIDE_ON_CLOSE, or use an @MainWindow frame "
                            + "(InlineStrategy maps EXIT_ON_CLOSE to session-close). "
                            + "See emulators/decisions.md D_gap_severity_triage + D_jframe_as_route.");
            default -> { /* unreachable (setter validates), drop */ }
        }
    }

    // ---- Content pane (JFrame surface) ----

    /** Lazy-creates if never accessed. Never returns null. */
    public Div getContentPane() {
        return scaffold.getContentPane();
    }

    /**
     * Swap the content pane. JDK contract: null throws
     * {@link java.awt.IllegalComponentStateException}; existing children
     * are NOT migrated; the SD_sjframe mirror invariant
     * ({@code frame.getContentPane() == frame.getRootPane().getContentPane()})
     * holds across swaps. All carried by {@link RootPaneScaffold}.
     */
    public void setContentPane(Div newPane) {
        scaffold.setContentPane(newPane);
    }

    // ---- add / remove routing ----
    //
    // Override the Collection form (Dialog overrides this one) and the
    // index / removeAll forms. Varargs add(Component...) defaults through
    // Collection, so our Collection override catches both. The
    // scaffold-null guard covers super-ctor-time adds that could land
    // before the scaffold field initializer runs.

    /** Route into content pane while the scaffold's checking flag is set. */
    @Override
    public void add(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContent(components);
        } else {
            super.add(components);
        }
    }

    /** Route into content pane while the scaffold's checking flag is set. */
    @Override
    public void addComponentAtIndex(int index, Component component) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContentAtIndex(index, component);
        } else {
            super.addComponentAtIndex(index, component);
        }
    }

    /** Route into content pane while the scaffold's checking flag is set. */
    @Override
    public void remove(Component... components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    /** Route into content pane while the scaffold's checking flag is set. */
    @Override
    public void remove(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    /**
     * Route into content pane while the scaffold's checking flag is set.
     * Otherwise delegate to Dialog's {@code removeAll} (which iterates
     * children manually to preserve the overlay's slot elements).
     */
    @Override
    public void removeAll() {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeAllFromContent();
        } else {
            super.removeAll();
        }
    }

    // ---- JMenuBar slot (SD_sjmenubar / D_menu_tree) ---------------------------------

    /**
     * Attach a menubar to this frame. Pass {@code null} to detach.
     * Slot semantics (index 0, sibling of the content pane, detach-old-
     * first) live on {@link RootPaneScaffold}.
     */
    public void setJMenuBar(SJMenuBar bar) {
        scaffold.setMenuBar(bar);
    }

    /** {@code null} until {@link #setJMenuBar} attaches one. */
    public SJMenuBar getJMenuBar() {
        return scaffold.getMenuBar();
    }

    // ---- Root pane ---------------------------------------------------

    /**
     * Lazy-constructs via {@link #createRootPane()} on first read (the
     * scaffold captures the hook and seeds the SD_sjframe mirror invariant).
     * Covariant override of
     * {@link com.vaadin.swingbridge.surrogates.swing.JComponentMixin#getRootPane()} —
     * SJFrame returns its own held pane rather than walking parents.
     */
    @Override
    public SJRootPane getRootPane() {
        return scaffold.getRootPane();
    }

    /**
     * Subclass hook matching JDK JFrame's {@code createRootPane}.
     * Called once on first {@link #getRootPane()} read. Subclasses
     * can override to supply a custom {@link SJRootPane}; the default
     * returns a plain one.
     */
    protected SJRootPane createRootPane() {
        return new SJRootPane();
    }

    // ---- Layered / glass pane pass-throughs --------------------------
    //
    // Delegated through getRootPane() so the first touch lazy-constructs
    // the root pane (AWT contract — reading a layered pane forces a
    // root pane). Matches :emulators.JFrame's existing shape.

    /** Delegates to {@code getRootPane().getLayeredPane()}. */
    public Component getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    /** Delegates to {@code getRootPane().setLayeredPane(...)}. */
    public void setLayeredPane(Component layered) {
        getRootPane().setLayeredPane(layered);
    }

    /** Delegates to {@code getRootPane().getGlassPane()}. */
    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    /** Delegates to {@code getRootPane().setGlassPane(...)}. */
    public void setGlassPane(Component glass) {
        getRootPane().setGlassPane(glass);
    }

    // ---- Default button (convenience pass-through) -------------------
    //
    // Most common use — frame.getRootPane().setDefaultButton(b) — works
    // unchanged; this convenience getter mirrors :emulators.JFrame-style
    // patterns migrators occasionally lean on.

    /** Convenience accessor: {@code getRootPane().getDefaultButton()}. */
    public com.vaadin.flow.component.button.Button getDefaultButton() {
        return getRootPane().getDefaultButton();
    }

    // ---- L&F class ID + paramString ---------------------------------

    /** Matches {@code javax.swing.JFrame.getUIClassID()}. */
    public String getUIClassID() {
        return "FrameUI";
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
            case WindowConstants.EXIT_ON_CLOSE       -> "EXIT_ON_CLOSE";
            default                                  -> "UNKNOWN_CLOSE_OPERATION";
        };
    }
}
