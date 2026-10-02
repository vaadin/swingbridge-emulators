/*
 * Copyright (c) 1995, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Window
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

import com.vaadin.flow.component.UI;
import com.vaadin.swingbridge.surrogates.SWindow;
import vaadinx.EHelper;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.awt.event.WindowEvent;
import vaadinx.awt.event.WindowFocusListener;
import vaadinx.awt.event.WindowListener;
import vaadinx.awt.event.WindowStateListener;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.AWTKeyStroke;
import java.awt.BufferCapabilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Event;
import java.awt.Graphics;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.im.InputContext;
import java.awt.image.BufferStrategy;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;

// Hand-finished emulator for java.awt.Window. Thin shell over
// SWindow. SWindow carries the peer↔window sync (OpenedChangeListener
// mirror, visible shadow, preventPeerEvents guard, ownedWindow
// WeakReferences), and this emulator exists to:
//
//   (1) expose the java.awt.Window API surface user code migrated
//       against, including the AWT listener families that fire with
//       source=this(emulator);
//   (2) bridge peer-originated closes (ESC / outside-click) into the
//       emulator's listenerList — the OpenedChangeListener wired
//       below mirrors into the emulator state and re-fires
//       WINDOW_CLOSING locally;
//   (3) keep emulator-layer first-show / dispose fires flowing through
//       Window's own listenerList so migrated code casting
//       (Window) e.getSource() still sees the emulator, not SWindow.
//
// Behaviour compat for the peer↔window sync is carried by SWindow;
// signature compat (AWT Window listener families, owner chain,
// getWindows registry) is carried here.

/** Emulator for {@link java.awt.Window}. Thin shell over {@link SWindow}. */
public class Window extends Container implements Accessible {

    // The AWT "owner" link: every Window except a top-level Frame has one.
    // Package-private so subclasses (and same-package helpers) can read it;
    // never reassigned, so an owned Window stays owned for life. Emulator
    // owner chain runs in parallel to SWindow's — both populated at ctor
    // time from their respective layers (emulator.Window for migrated
    // code, SWindow for surrogate-level usage).
    Window owner;

    // Swing's owner→children link: each Window keeps WeakReferences to the
    // Windows it owns. See getOwnedWindows for the GC-prunes-dead-entries
    // contract. Parallel to SWindow.ownedWindowList; each layer's list
    // carries its own Window / SWindow references.
    private final java.util.List<WeakReference<Window>>
            ownedWindowList = new ArrayList<>();

    // First-show latch: WINDOW_OPENED fires once on the emulator's
    // listenerList per "shown-from-dispose" cycle. Independent of
    // SWindow's own everShown — the two layers have independent listener
    // lists and fire in parallel (SD_sjslider/SD_sjspinner shape).
    private boolean everShown;

    // R_swing_is_truth shadows for explicit window geometry. Null until user code
    // places/sizes the window — the getters fall back to Component's
    // WARN stubs while unset, so a read that was never preceded by a
    // write keeps surfacing "we don't know browser geometry" instead of
    // silently answering (0,0). Window-level only: general component
    // bounds stay R_layouts_close_enough-out-of-scope (layout is browser-driven); a
    // top-level Window maps 1:1 onto the Dialog overlay's native
    // top/left/width/height, no layout manager involved.
    private Point location;
    private Dimension size;

    // Emulator-side R_swing_is_truth feedback-loop guard. Set to true around our calls
    // into SWindow.setVisible / SWindow.dispose so our own Dialog
    // OpenedChangeListener (wired below) bails while WE are the driver
    // of the peer change. Independent from SWindow's own preventPeerEvents:
    // each layer guards its own callback. Under a peer-originated close
    // (browser ESC / outside-click) neither flag is set and both
    // listeners fire, mirroring into both layers' state in parallel.
    private boolean preventPeerEvents;

    public Window(Frame arg0) {
        this(SWindow.class,
                () -> new SWindow(unwrapSWindow(arg0)), arg0);
    }

    public Window(Window arg0) {
        this(SWindow.class,
                () -> new SWindow(unwrapSWindow(arg0)), arg0);
    }

    public Window(Window arg0, GraphicsConfiguration arg1) {
        // Accepted and ignored: one viewport, one configuration — see Frame(String, GC).
        this(arg0);
    }

    /**
     * Owner-aware peer-injection seam, mirroring
     * {@link Dialog#Dialog(com.vaadin.flow.component.Component, Window, String, Dialog.ModalityType)}.
     * {@code vaadinx.swing.JWindow} threads its {@code SJWindow} peer plus
     * an emulator-side owner through in one step — the public owner-taking
     * ctors here can't be reused because they hardcode {@code SWindow} as
     * the peer.
     */
    protected Window(com.vaadin.flow.component.Component peer, Window owner) {
        this(peer);
        setOwner(owner);
    }

    protected Window(com.vaadin.flow.component.Component peer) {
        super(peer);
        initWindow();
    }

    /**
     * The lazy form of {@link #Window(com.vaadin.flow.component.Component, Window)}: see
     * {@link Component#Component(Class, Supplier)}. A factory
     * that reads the owner's peer reads it when it runs, on the UI thread.
     */
    protected <P extends com.vaadin.flow.component.Component> Window(Class<P> peerType,
            Supplier<? extends P> peerFactory, Window owner) {
        this(peerType, peerFactory);
        setOwner(owner);
    }

    /** The lazy form: see {@link Component#Component(Class, Supplier)}. */
    protected <P extends com.vaadin.flow.component.Component> Window(Class<P> peerType,
            Supplier<? extends P> peerFactory) {
        super(peerType, peerFactory);
        initWindow();
    }

    /** The owner link, both halves, as the JDK's {@code Window(Window owner)} sets it. */
    private void setOwner(Window owner) {
        this.owner = owner;
        if (owner != null) owner.addOwnedWindow(this);
    }

    private void initWindow() {
        // AWT's Window default is invisible until show()/setVisible(true);
        // Component's member-initializer defaults to true for leaf components
        // that are visible from the moment they exist. Flip here — the field
        // is package-private exactly so Window can do this.
        this.visible = false;
        // Subscribe to the Dialog peer's OpenedChangeEvent so user-initiated
        // closes (ESC, outside-click) round-trip back into emulator-side
        // state and fire WINDOW_CLOSING on the emulator's listenerList.
        // SWindow has its OWN separate OpenedChangeListener wired in its
        // own ctor — under a peer-originated close, both listeners fire
        // (once each on their respective layers). Under emulator-driven
        // setVisible/dispose, our preventPeerEvents flag bails here while
        // SWindow's own flag bails its listener, keeping each layer to
        // one fire (SD_sframe shape). A write, so a lazy peer gets it once built.
        withPeer(p -> {
            if (p instanceof com.vaadin.flow.component.dialog.Dialog dialog) {
                dialog.addOpenedChangeListener(this::onPeerOpenedChanged);
            }
        });
        // The JDK's Window.init() ends with addToWindowList(), so a Window is
        // enumerable from inside its own constructor — `new Frame() {{
        // assert getFrames().contains(this); }}` passes on the desktop
        // (measured), and the anonymous subclass's initializer runs after this.
        EHelper.onWindowCreated(this);
    }

    /**
     * The {@code BorderLayout} that {@code java.awt.Window.init()} installs, and what
     * {@link #getLayout} answers — an AWT constructor default, not an L&amp;F object, and
     * the mechanism by which a {@code JFrame}'s root pane fills the frame (it goes in
     * unconstrained, so it lands in {@code CENTER}).
     *
     * <p>Deliberately not {@link Container}'s slot: that is the field
     * {@code doLayout} dispatches to CSS, and a {@code Window}'s peer is a
     * {@code <vaadin-dialog>}, which {@code LayoutCss.applyContainerCss}' {@code <div>}
     * gate refuses with an unsupported-peer-shape ERROR. Nothing is lost — the window's one
     * child is the root pane, whose placement D_rootpane_containment expresses
     * as CSS on the root pane itself — and leaving {@code Container}'s slot
     * empty keeps {@code doLayout} and {@code getPreferredSize} on their
     * no-manager path, so this field costs no rendering change.
     */
    private LayoutManager windowLayout = new BorderLayout();

    @Override
    public LayoutManager getLayout() {
        return windowLayout;
    }

    /**
     * Stores the manager and invalidates as AWT does, minus the CSS dispatch — see
     * {@link #windowLayout}.
     *
     * @param mgr may be {@code null}, as AWT allows
     */
    @Override
    public void setLayout(LayoutManager mgr) {
        windowLayout = mgr;
        invalidate();
    }

    /**
     * Mirror peer-originated Dialog open/close back into emulator-layer
     * state and fire the corresponding AWT events on the emulator's
     * listenerList, inside the R_callswing_envelope {@code callSwing} envelope. Skipped while
     * {@link #preventPeerEvents} is set — that indicates WE drove the peer write
     * (through {@link #setVisible(boolean)} / {@link #dispose()}) and fired the
     * emulator events directly, so a listener echo would double-fire.
     */
    private void onPeerOpenedChanged(
            com.vaadin.flow.component.dialog.Dialog.OpenedChangeEvent e) {
        // Guard and bookkeeping first, outside the envelope (R_swing_is_truth): the flag read
        // must precede callSwing, and writing `visible` before firing means a
        // handler that parks cannot be re-entered by the next OpenedChangeEvent
        // through the equality check below.
        if (preventPeerEvents) return;
        final boolean opened = e.isOpened();
        final boolean old = this.visible;
        if (old == opened) return;
        this.visible = opened;

        // R_callswing_envelope: firing these is the listener's whole job, and a WINDOW_CLOSING
        // handler is the canonical place migrated code opens a blocking "save
        // before closing?" dialog — which needs a virtual thread to park on.
        // Nested calls run inline (D_callswing_loom); during app shutdown there is no current
        // UI and callSwing runs the callback inline per the D_shutdown_lifecycle carve-out.
        EHelper.callSwing(() -> {
            // No "visible" PropertyChangeEvent: AWT signals visibility with a
            // ComponentEvent and nothing else. JPopupMenu is the one class in
            // all of java.awt/javax.swing that fires a "visible" bound
            // property, and it is not in this hierarchy (R_decline_effect_only).
            fireComponentEvent(opened
                    ? ComponentEvent.COMPONENT_SHOWN
                    : ComponentEvent.COMPONENT_HIDDEN);
            if (!opened) {
                // AWT maps "user asks to close" to WINDOW_CLOSING, distinct
                // from WINDOW_CLOSED (programmatic dispose). Whether to
                // actually dispose belongs to JFrame.defaultCloseOperation;
                // Window itself just reports the intent. Route through
                // processWindowEvent directly (not fireWindowEvent) so
                // JFrame.processWindowEvent's close-op switch applies even
                // when no WindowListener is registered — DISPOSE_ON_CLOSE
                // must still detach, EXIT_ON_CLOSE must still throw per D_gap_severity_triage.
                processWindowEvent(new WindowEvent(
                        this, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

    /**
     * Helper for the Frame / Window / Dialog-owner ctors' peer factories: unwrap the
     * emulator owner's surrogate-side SWindow peer, or null. Frame's peer
     * is SFrame (an SWindow subclass), Dialog's peer is SFrame too,
     * Window's peer is SWindow — all three pass the {@code instanceof SWindow}
     * check so a single helper covers every owner type. Called from a factory, so on
     * the UI thread, where reading the owner's peer builds it.
     */
    private static SWindow unwrapSWindow(Window w) {
        if (w == null) return null;
        return w.getPeer() instanceof SWindow sw ? sw : null;
    }

    public boolean isOpaque() {
        // AWT default is true for a Window — a top-level Window paints its
        // entire bounds opaquely unless setBackground installs an alpha<255
        // color (which we don't honor; see setBackground). Reporting false
        // here contradicts getBackground's opaque handling and would mislead
        // any caller deciding whether to paint below the Window.
        return true;
    }

    public boolean isActive() {
        // AWT's "active" tracks whether this Window is the one the OS currently
        // considers focused (the one whose titlebar is highlighted). We don't
        // model window activation (D_single_ui_per_session — single UI, no window-manager concept
        // in a browser tab), so false matches AWT's pre-activation default
        // without misleading callers. Pairs with isFocused silently.
        return false;
    }

    public Window getOwner() {
        // AWT: the Window passed to the (Window) / (Frame) constructors,
        // null for an owner-less Window. Field is set by those ctors.
        return owner;
    }

    public Locale getLocale() {
        // AWT's Window overrides Component.getLocale to fall back to
        // Locale.getDefault() instead of throwing IllegalComponentStateException
        // on an orphan — a Window is normally the top of the tree, so the
        // Component walk-up always bottoms out here. We upgrade the fallback:
        // prefer the *browser* locale (UI.getLocale, resolved from the
        // Accept-Language header) so user code sees what the user asked for.
        // Locale.getDefault is the last-resort safety net for code paths
        // outside a Vaadin request (no current UI). We reuse Component's
        // walk-up by catching the orphan exception rather than duplicating
        // field access (the field is private there).
        //
        // Live for the AWT windows, which have no *Init hook and so never take
        // an explicit locale. A JFrame / JDialog / JWindow gets one from
        // JComponent.getDefaultLocale during construction and returns above.
        try {
            return super.getLocale();
        } catch (IllegalComponentStateException orphan) {
            UI ui = UI.getCurrent();
            return ui != null ? ui.getLocale() : Locale.getDefault();
        }
    }

    /**
     * R_swing_is_truth mirror: store the Swing-side size, push to the SWindow peer's
     * width/height channel, fire COMPONENT_RESIZED when the value
     * actually changed. Non-SWindow peers (JFrame's InlineStrategy /
     * SJPanel) skip the peer write — an inline main window keeps its
     * viewport-fill stance per D_inline_route_sizing; the field shadow + event still
     * happen so user code sees consistent Swing state.
     */
    public void setSize(int width, int height) {
        Dimension old = this.size;
        boolean resized = old == null || old.width != width || old.height != height;
        this.size = new Dimension(width, height);
        withPeer(p -> {
            if (p instanceof SWindow sw) sw.setSize(width, height);
        });
        if (resized) {
            fireComponentEvent(ComponentEvent.COMPONENT_RESIZED);
        }
    }

    public void setSize(Dimension d) {
        // AWT NPEs on null here (d.width dereference) — same for us.
        setSize(d.width, d.height);
    }

    public void dispose() {
        // AWT's doDispose, in its order: owned windows first, then hide(), then
        // reset the first-show latch, then removeNotify() — which is what
        // actually releases the peer. The teardown lives there and not here
        // precisely so the JDK's dispose() -> removeNotify() edge is real: a
        // migrator's removeNotify override runs on dispose, as on the desktop.
        // A subsequent setVisible(true) re-attaches through addNotify +
        // SWindow.setVisible and refires WINDOW_OPENED via the everShown reset
        // — AWT's "dispose-then-show is a new first-show".
        //
        // The gate is read BEFORE any of the work, because that is where the
        // JDK reads it (`boolean fireWindowClosedEvent = isDisplayable()`) and
        // removeNotify() below clears the bit. Disposing a never-shown,
        // never-packed Window therefore fires no WINDOW_CLOSED at all — R_decline_effect_only:
        // firing an event real Swing never fires is the same class of bug as
        // dropping one (measured on JDK 25, with the EventQueue drained; an
        // unflushed read reports the opposite because postWindowEvent is async).
        final boolean fireWindowClosedEvent = isDisplayable();
        // Children before self, as the JDK does (doDispose -> child.disposeImpl()).
        // Iterate the snapshot getOwnedWindows() hands back — a child's dispose
        // does not touch the owner link, but nothing here should depend on that.
        for (Window child : getOwnedWindows()) {
            child.dispose();
        }
        // hide() rather than an inlined field write: it is the JDK's own call
        // here, so a migrator's hide() override runs on dispose. Idempotent
        // when already hidden, and its owned-window cascade finds the children
        // already invisible from their own dispose above.
        hide();
        everShown = false;
        removeNotify();
        if (fireWindowClosedEvent) {
            fireWindowEvent(WindowEvent.WINDOW_CLOSED);
        }
    }

    /**
     * Tear down the peer in response to {@link #dispose()}. Default
     * delegates to {@link SWindow#dispose()} when the peer
     * is an SWindow (the standard SFrame / SJFrame / SDialog path).
     * Subclasses with non-SWindow peers — notably JFrame's InlineStrategy
     * with an SJPanel peer — override to detach from the route's div.
     * Called under the {@link #preventPeerEvents} guard so a
     * peer-originated OpenedChangeEvent doesn't double-fire WINDOW_CLOSING.
     */
    protected void disposePeer() {
        withPeer(p -> {
            if (p instanceof SWindow sw) sw.dispose();
        });
    }

    public Shape getShape() {
        // AWT default is null until setShape installs a non-rectangular
        // outline. We can't render non-rectangular windows (DOM is
        // rectangular), so the setter is a stub and the shape stays null.
        return null;
    }

    public Toolkit getToolkit() {
        // AWT's Window override exists to return the peer's toolkit. We
        // don't have a native peer — Component.getToolkit already returns
        // the vaadinx full-surface emulator toolkit (D_toolkit_full_surface), which is what
        // user code needs. Keeping the override so the reason is visible here.
        return super.getToolkit();
    }

    /**
     * R_swing_is_truth mirror of {@link #setSize(int, int)} for placement: store,
     * push to the SWindow peer's top/left channel (which takes the
     * Dialog overlay out of its default centered position), fire
     * COMPONENT_MOVED on actual change. Same non-SWindow-peer skip as
     * setSize.
     */
    public void setLocation(int x, int y) {
        Point old = this.location;
        boolean moved = old == null || old.x != x || old.y != y;
        this.location = new Point(x, y);
        withPeer(p -> {
            if (p instanceof SWindow sw) sw.setLocation(x, y);
        });
        if (moved) {
            fireComponentEvent(ComponentEvent.COMPONENT_MOVED);
        }
    }

    public void setLocation(Point p) {
        // AWT NPEs on null here (p.x dereference) — same for us.
        setLocation(p.x, p.y);
    }

    /**
     * @throws IllegalComponentStateException if {@code b} and the window is
     *         already showing, as AWT's own body does before it stores
     */
    public void setLocationByPlatform(boolean b) {
        // AWT's gate comes first and is a programming error either side of the browser
        // (R_match_swing_errors), so it is reproduced; only the placement is declined,
        // browsers having no say in tab positioning (D_pixel_layout_not_planned).
        if (b && isShowing()) {
            throw new IllegalComponentStateException("The window is showing on screen.");
        }
        if (b) {
            EHelper.onUnimplemented("Window", "setLocationByPlatform", b);
        }
        this.locationByPlatform = b;
    }

    /**
     * AWT's always-on-top flag. Stored even though nothing honours it —
     * the JDK writes this field <em>before</em> consulting
     * {@link #isAlwaysOnTopSupported()}, so on a platform that cannot honour
     * the request {@code isAlwaysOnTop()} still echoes it. Echoing is the
     * faithful answer, not a shadow cache (R_decline_effect_only).
     */
    private boolean alwaysOnTop;

    // The other three AWT flags whose effect a browser cannot deliver but whose value
    // its own getter answers, same shape as alwaysOnTop above. AWT's defaults.
    private boolean autoRequestFocus = true;
    private boolean locationByPlatform;
    private float opacity = 1.0f;

    public final boolean isAlwaysOnTop() {
        return alwaysOnTop;
    }

    public final void setAlwaysOnTop(boolean b) {
        // JDK body. Three limbs, and only the first is undeliverable here:
        // the peer's z-order update (browsers have no window stacking we
        // could drive) is what WARNs. The field and the property change are
        // gated on an actual change; the owned-window fan-out is NOT — the
        // JDK runs it outside that guard, so a re-set to the same value
        // still propagates. R_decline_effect_only: decline the effect, keep the rest.
        boolean oldAlwaysOnTop = this.alwaysOnTop;
        this.alwaysOnTop = b;
        if (oldAlwaysOnTop != b) {
            if (isAlwaysOnTopSupported()) {
                // Where the JDK calls peer.updateAlwaysOnTopState().
                EHelper.onNoop("Window", "setAlwaysOnTop/peer");
            } else if (b) {
                EHelper.onUnimplemented("Window", "setAlwaysOnTop", b);
            }
            firePropertyChange("alwaysOnTop", oldAlwaysOnTop, b);
        }
        setOwnedWindowsAlwaysOnTop(b);
    }

    /** JDK private helper: every owned Window inherits the flag. */
    private void setOwnedWindowsAlwaysOnTop(boolean b) {
        for (Window w : getOwnedWindows()) {
            w.setAlwaysOnTop(b);
        }
    }

    /**
     * AWT's icon list. Never {@code null} — an empty list is the AWT default,
     * and {@link #setIconImages} normalises {@code null} to empty exactly as
     * the JDK does.
     */
    private java.util.List<Image> icons = new ArrayList<>();

    public synchronized void setIconImages(java.util.List<? extends Image> icons) {
        // JDK body. The undeliverable limb is the peer's updateIconImages() —
        // a browser's window icon is the route-level favicon, not a per-Window
        // property — and it stays silent rather than WARNing, because migrated
        // apps set icons during startup and the noise would swamp real
        // warnings. The list and the event are kept: the JDK's own comment on
        // this line reads "Always send a property change event", and it passes
        // (null, null) precisely so no equality check can suppress it (R_decline_effect_only).
        this.icons = (icons == null)
                ? new ArrayList<>()
                : new ArrayList<>(icons);
        firePropertyChange("iconImage", null, null);
    }

    public void addNotify() {
        // Where the JDK creates the native peer. Ours exists from the
        // constructor, so all that is left is the bit the peer's existence
        // stood for (Component.addNotify sets it) — plus the registration the
        // JDK does on the same line, ahead of `super.addNotify()` and thus
        // ahead of the child cascade, so a child's addNotify override already
        // finds this window in Frame.getFrames(). Guarded like the JDK's
        // `if (peer == null)`, so a redundant call (pack() then show(), or a
        // Vaadin re-attach) is a no-op.
        if (displayable) return;
        EHelper.onWindowDisplayable(this);
        super.addNotify();
    }

    /**
     * Vaadin detach must not clear {@code displayable} — a hidden Window is
     * still displayable in AWT, and hiding is exactly what detaches our peer.
     * Only {@link #dispose()} undisplayables a Window, which is the JDK's rule
     * too ({@code doDispose} calls {@code removeNotify}; {@code hide()} never
     * does). This is the one place a Window departs from
     * {@link Component#onPeerDetached()}'s realisation-root rule,
     * and the reason is that a Window is the only emulator with a
     * <em>separate</em> unrealisation trigger to preserve. Attach needs no such
     * carve-out and is inherited, so a Window realised by a route rather than
     * by {@code show()} — a {@code @MainWindow} JFrame — still becomes
     * displayable the moment it is rendered.
     */
    @Override
    void onPeerDetached() {
    }

    public void removeNotify() {
        // Where the JDK destroys the native peer. Ours survives, so this
        // detaches it instead, through the {@link #disposePeer()} hook that
        // subclasses override (notably JFrame's InlineStrategy, which
        // substitutes a route-detach). The preventPeerEvents flag is for OUR
        // Dialog OpenedChangeListener — SWindow guards its own independently.
        // Guarded on the flag, so the JDK's dispose() -> removeNotify() edge
        // and any stray call collapse to one teardown.
        if (!displayable) return;
        EHelper.onWindowUndisplayable(this);
        preventPeerEvents = true;
        try {
            disposePeer();
        } finally {
            preventPeerEvents = false;
        }
        super.removeNotify();
    }

    public void setMinimumSize(Dimension arg0) {
        // AWT's Window override also nudges the current size up if it's
        // below the new minimum. We don't model window sizing (R_layouts_close_enough — the
        // browser picks viewport geometry), so Component.setMinimumSize's
        // CSS min-width/min-height write is all there is.
        super.setMinimumSize(arg0);
    }

    /**
     * Deprecated AWT 1.0 alias — in the JDK this is the actual worker
     * setBounds delegates to; we invert the delegation so the modern
     * name carries the implementation.
     */
    public void reshape(int x, int y, int width, int height) {
        setBounds(x, y, width, height);
    }

    /**
     * Place + size in one call. Fires up to two component events
     * (MOVED then RESIZED), matching AWT's reshape behaviour of
     * reporting each aspect that changed.
     */
    public void setBounds(int x, int y, int width, int height) {
        setLocation(x, y);
        setSize(width, height);
    }

    public void setBounds(Rectangle r) {
        // AWT NPEs on null here (r.x dereference) — same for us.
        setBounds(r.x, r.y, r.width, r.height);
    }

    // Geometry getters: serve the R_swing_is_truth shadow once user code has placed /
    // sized the window; fall back to Component's WARN stubs while unset
    // (we can't read browser geometry synchronously, and answering (0,0)
    // without a prior write would be a silent lie). The (rv) variants
    // honor AWT's fill-or-allocate contract.

    public Point getLocation() {
        if (location == null) return super.getLocation();
        return new Point(location);
    }

    public Point getLocation(Point rv) {
        if (location == null) return super.getLocation(rv);
        if (rv == null) return new Point(location);
        rv.setLocation(location);
        return rv;
    }

    public Dimension getSize() {
        if (size == null) return super.getSize();
        return new Dimension(size);
    }

    public Dimension getSize(Dimension rv) {
        if (size == null) return super.getSize(rv);
        if (rv == null) return new Dimension(size);
        rv.setSize(size);
        return rv;
    }

    public Rectangle getBounds() {
        if (location == null && size == null) return super.getBounds();
        // Partial knowledge: the unset half reads 0 — AWT's own
        // pre-layout default — rather than WARN-nulling the half we do know.
        return new Rectangle(
                location != null ? location.x : 0,
                location != null ? location.y : 0,
                size != null ? size.width : 0,
                size != null ? size.height : 0);
    }

    public Rectangle getBounds(Rectangle rv) {
        if (location == null && size == null) return super.getBounds(rv);
        Rectangle r = getBounds();
        if (rv == null) return r;
        rv.setBounds(r);
        return rv;
    }

    public int getX() {
        return location != null ? location.x : super.getX();
    }

    public int getY() {
        return location != null ? location.y : super.getY();
    }

    public int getWidth() {
        return size != null ? size.width : super.getWidth();
    }

    public int getHeight() {
        return size != null ? size.height : super.getHeight();
    }

    @Override
    boolean pushesEnabledToPeer() {
        // Unlike a JPanel's, a Window's disable *does* cascade on the desktop:
        // AWT blocks input at the native window, which is how it blocks a modal
        // dialog's owner and what an app using frame.setEnabled(false) as a
        // poor-man's modal relies on. So Flow's subtree disable is faithful here
        // rather than the bug D_container_enabled_no_cascade describes.
        return true;
    }

    public void setVisible(boolean b) {
        // JDK shape: a pure delegation. Window's visibility work lives in
        // show() / hide(), which is where the JDK put it and where subclass
        // overrides (Dialog's modal park) hang off — see Component's
        // visibility block for why the direction is not "fixed".
        super.setVisible(b);
    }

    @Override
    public void show() {
        // validate() walks Swing-side children so LayoutManager CSS lands on
        // every container — D_layout_css_on_content's emulator-layer layout dispatch stays here
        // rather than inside SWindow (SD_sframe: the surrogate module stays unaware
        // of emulator-owned layout machinery). The JDK calls
        // validateUnconditionally() at the top of show() whether or not the
        // window was already visible, so this sits above the guard too.
        //
        if (!isDisplayable()) {
            addNotify();
        }
        validate();
        if (isVisible()) {
            // Already showing: the JDK raises rather than re-shows.
            toFront();
            return;
        }
        super.show();
        if (!everShown) {
            everShown = true;
            // WINDOW_OPENED fires exactly once per "shown-from-dispose" cycle.
            // dispose() resets everShown so a post-dispose show refires it —
            // AWT's contract for heavyweight-peer recreation.
            fireWindowEvent(WindowEvent.WINDOW_OPENED);
        }
    }

    @Override
    public void hide() {
        // The JDK hides every owned window that is currently showing and marks
        // it showWithParent so a later show() brings the whole set back. We
        // reproduce the hide half; the showWithParent restore is not modelled,
        // because a browser overlay has no "raise the family with the parent"
        // behaviour to restore it into.
        for (Window child : getOwnedWindows()) {
            if (child.isVisible()) {
                child.hide();
            }
        }
        super.hide();
    }

    /**
     * Apply visibility to the peer in response to {@link #setVisible(boolean)}.
     * Default delegates to {@link SWindow#setVisible(boolean)}
     * when the peer is an SWindow (standard Dialog-backed shape); falls
     * through to {@link com.vaadin.flow.component.Component#setVisible(boolean)}
     * for non-SWindow peers as defensive coverage. Subclasses with bespoke
     * peer-attach semantics — notably JFrame's InlineStrategy with an
     * SJPanel peer attached to a route's div — override to substitute
     * their own peer manipulation. Called under the {@link #preventPeerEvents}
     * guard so a peer-originated OpenedChangeEvent doesn't double-fire.
     */
    @Override
    protected final void applyVisibleToPeer(boolean b) {
        // The hop is on the effect alone, not around Component.show()'s whole state
        // machine: the field write and the COMPONENT_SHOWN / WINDOW_OPENED fan-out are
        // pure Java that cost nothing and are what user code observes, so a browser that
        // has gone away must not take them with it (R_decline_effect_only). Only this
        // line is droppable — and blocksAfterVisibilityChange is what stops that drop
        // becoming a hang, since a modal show is followed by a park and so throws instead
        // (R_match_swing_errors case (8), D_modal_from_background). On the UI thread the
        // body runs inline, so nothing that already worked changes.
        EHelper.runInUIThread(blocksAfterVisibilityChange(b), () -> {
            // The preventPeerEvents envelope lives with the peer write it guards,
            // rather than around the whole state machine as it once did — a
            // peer-originated OpenedChangeEvent can only arrive from this line.
            preventPeerEvents = true;
            try {
                applyVisibleToPeerImpl(b);
            } finally {
                preventPeerEvents = false;
            }
        });
    }

    /**
     * Whether the caller will block on a browser round-trip once this visibility change
     * has been applied — {@code true} only for a modal {@link Dialog#show()}, which parks
     * until the user answers. Decided on our side rather than the migrator's, which is
     * what makes it answerable at all; the set is small and closed.
     *
     * @param b the visibility being applied, since only a show can be followed by a park
     */
    protected boolean blocksAfterVisibilityChange(boolean b) {
        return false;
    }

    /**
     * The peer write itself, already on the UI thread and inside the
     * {@link #preventPeerEvents} guard. Subclasses with bespoke peer-attach semantics —
     * notably JFrame's InlineStrategy with an SJPanel peer attached to a route's div —
     * override this rather than {@link #applyVisibleToPeer(boolean)}, so the hop and the
     * guard cannot be bypassed by forgetting to call {@code super}.
     */
    protected void applyVisibleToPeerImpl(boolean b) {
        if (getPeer() instanceof SWindow sw) {
            sw.setVisible(b);
        } else {
            // Defensive: no standard ctor picks a non-SWindow peer
            // outside InlineStrategy, but if a subclass ever does, fall
            // back to Component-level display:none toggling.
            getPeer().setVisible(b);
        }
    }

    public void toFront() {
        // Browser tabs have no z-order we can reach; a Dialog peer is
        // already rendered above page content when opened. Silent no-op
        // rather than WARN because apps routinely pair setVisible(true)
        // with toFront() — logging each call would swamp real warnings.
        EHelper.onNoop("Window", "toFront");
    }

    // isShowing() is inherited from Component, whose parent walk bottoms out
    // here: a Window has no parent, so its answer is visible && displayable —
    // AWT's own, and the reason a hidden-but-undisposed Window is displayable
    // and not showing, a packed one likewise. Everything below reaches this
    // Window through the walk, which is what makes a child of a hidden window
    // not-showing while its own `visible` flag stays untouched.
    // show() / hide() are overridden above, not inherited: they hold the whole
    // state machine and setVisible delegates *to* them, which is the JDK's
    // direction. See Component's visibility block for why.

    @Deprecated
    public boolean postEvent(Event arg0) {
        // Deprecated AWT 1.0 — Component.postEvent already returns false
        // ("nobody handled it"). AWT's Window override forwarded to an owner
        // if present; we don't track owners (see getOwner), so super's
        // plain-false is right.
        return super.postEvent(arg0);
    }

    public void toBack() {
        // Mirror of toFront — no z-order, silent no-op. Apps that want a
        // Window "behind" others are expressing OS-window-manager intent
        // that doesn't translate to a single-tab Vaadin UI.
        EHelper.onNoop("Window", "toBack");
    }

    public void setCursor(Cursor arg0) {
        // AWT's Window override updates a native cursor struct. Our DOM
        // peer accepts CSS cursor via Component.setCursor — that's all we
        // need. Keeping the override so the rationale lives here.
        super.setCursor(arg0);
    }

    public static Window[] getWindows() {
        // Every Window this app instance constructed and still holds, in
        // construction order — AWT's contract, leak included, bounded to the
        // browser tab rather than the JVM. See vaadinx.WindowRegistry.
        java.util.List<Window> all = EHelper.getWindows();
        return all.toArray(new Window[0]);
    }

    public Window[] getOwnedWindows() {
        // Swing's approach: dereference each WeakReference, keep the ones
        // whose Window is still reachable, opportunistically drop the
        // cleared entries so the list doesn't grow unbounded for long-lived
        // owners. Logically independent of peer attachment state — AWT's
        // owner link persists whether the owned Window is displayed or not,
        // so we report it even if its Dialog peer isn't opened.
        java.util.List<Window> live = new ArrayList<>();
        synchronized (ownedWindowList) {
            Iterator<WeakReference<Window>> it =
                    ownedWindowList.iterator();
            while (it.hasNext()) {
                Window w = it.next().get();
                if (w == null) {
                    it.remove();
                } else {
                    live.add(w);
                }
            }
        }
        return live.toArray(new Window[0]);
    }

    /**
     * Register {@code child} as a Window owned by this one. Called from the
     * owner-taking constructors after the owner field is set, mirroring
     * AWT's {@code Window.addOwnedWindow}. Package-private because it's an
     * internal invariant — only Window's own ctors should be calling it.
     */
    void addOwnedWindow(Window child) {
        synchronized (ownedWindowList) {
            ownedWindowList.add(new WeakReference<>(child));
        }
    }

    // getListeners(Class) inherited from Component. AWT's Window override folds
    // WindowListener / WindowFocusListener / WindowStateListener into the
    // result; our shared listenerList already stores them where Component's
    // impl reads from, so no override is needed.

    protected void processWindowEvent(WindowEvent e) {
        // AWT's processWindowEvent handles the non-focus, non-state subset —
        // opened/closing/closed/iconified/deiconified/activated/deactivated.
        // Focus ids go to processWindowFocusEvent, state-changed to
        // processWindowStateEvent; processEvent above routes by id.
        //
        // Null-guard matches Component's process* family (smoke-test null
        // probe shouldn't crash on a trivial empty answer).
        if (e == null) return;
        int id = e.getID();
        for (WindowListener l : awtListeners(WindowListener.class)) {
            switch (id) {
                case WindowEvent.WINDOW_OPENED -> l.windowOpened(e);
                case WindowEvent.WINDOW_CLOSING -> l.windowClosing(e);
                case WindowEvent.WINDOW_CLOSED -> l.windowClosed(e);
                case WindowEvent.WINDOW_ICONIFIED -> l.windowIconified(e);
                case WindowEvent.WINDOW_DEICONIFIED -> l.windowDeiconified(e);
                case WindowEvent.WINDOW_ACTIVATED -> l.windowActivated(e);
                case WindowEvent.WINDOW_DEACTIVATED -> l.windowDeactivated(e);
                default -> { /* focus/state ids route to their own process methods */ }
            }
        }
    }

    protected void processWindowFocusEvent(WindowEvent e) {
        if (e == null) return;
        int id = e.getID();
        for (WindowFocusListener l : awtListeners(WindowFocusListener.class)) {
            switch (id) {
                case WindowEvent.WINDOW_GAINED_FOCUS -> l.windowGainedFocus(e);
                case WindowEvent.WINDOW_LOST_FOCUS -> l.windowLostFocus(e);
                default -> { /* out-of-range id, drop */ }
            }
        }
    }

    protected void processWindowStateEvent(WindowEvent e) {
        // Only one id routes here (WINDOW_STATE_CHANGED), one callback —
        // still id-check so a mis-routed event silently drops instead of
        // firing a bogus state-changed.
        if (e == null) return;
        if (e.getID() != WindowEvent.WINDOW_STATE_CHANGED) return;
        for (WindowStateListener l : awtListeners(WindowStateListener.class)) {
            l.windowStateChanged(e);
        }
    }

    protected void processEvent(AWTEvent e) {
        // AWT's Window.processEvent routes WindowEvent by id to one of the
        // three process*Event methods; everything else delegates up the
        // chain (Container → Component) so ContainerEvent / ComponentEvent
        // / mouse / key / … land in the right sinks.
        if (e instanceof WindowEvent we) {
            switch (we.getID()) {
                case WindowEvent.WINDOW_OPENED,
                     WindowEvent.WINDOW_CLOSING,
                     WindowEvent.WINDOW_CLOSED,
                     WindowEvent.WINDOW_ICONIFIED,
                     WindowEvent.WINDOW_DEICONIFIED,
                     WindowEvent.WINDOW_ACTIVATED,
                     WindowEvent.WINDOW_DEACTIVATED
                        -> processWindowEvent(we);
                case WindowEvent.WINDOW_GAINED_FOCUS,
                     WindowEvent.WINDOW_LOST_FOCUS
                        -> processWindowFocusEvent(we);
                case WindowEvent.WINDOW_STATE_CHANGED
                        -> processWindowStateEvent(we);
                default -> { /* out-of-range WindowEvent id, drop — matches AWT */ }
            }
            return;
        }
        super.processEvent(e);
    }

    // fire* helpers follow Component/Container's pattern: short-circuit event
    // allocation when nobody's listening, then route through the matching
    // process*Event method so user-overridden process methods still run. Each
    // listener family has its own helper so callers don't have to know the
    // routing table — they just say what happened.

    protected void fireWindowEvent(int id) {
        if (listenerList.getListenerCount(WindowListener.class) == 0) return;
        processWindowEvent(new WindowEvent(this, id));
    }

    protected void fireWindowFocusEvent(int id, Window opposite) {
        if (listenerList.getListenerCount(WindowFocusListener.class) == 0) return;
        processWindowFocusEvent(new WindowEvent(this, id, opposite));
    }

    protected void fireWindowStateEvent(int oldState, int newState) {
        // WINDOW_STATE_CHANGED is the only id this method fires — old and new
        // state carry the transition, opposite isn't meaningful here.
        if (listenerList.getListenerCount(WindowStateListener.class) == 0) return;
        processWindowStateEvent(new WindowEvent(
                this, WindowEvent.WINDOW_STATE_CHANGED, oldState, newState));
    }

    public boolean isAlwaysOnTopSupported() {
        // We don't honor always-on-top (see isAlwaysOnTop); reporting "not
        // supported" is the documented AWT way to tell callers that
        // setAlwaysOnTop(true) won't stick.
        return false;
    }

    public boolean isFocused() {
        // Browser tab focus isn't something we track; callers using this as
        // a gate ("only refresh when visible") will get steady-state false,
        // which they handle by also listening for focus events. Pairs with
        // isActive silently.
        return false;
    }

    public Component getFocusOwner() {
        // AWT: returns the focused child if this Window is the active one,
        // null otherwise. We never report isFocused=true (browser focus
        // isn't tracked), so null is consistent — no one can own focus on
        // an unfocused window.
        return null;
    }

    public Component getMostRecentFocusOwner() {
        // AWT tracks this across activation cycles. We don't track focus
        // at all (R_infra_not_surface), so the "last child to have focus" is always null.
        // Paired with getFocusOwner silently.
        return null;
    }

    public final boolean isFocusableWindow() {
        // JDK's first limb, verbatim: a window made non-focusable is always
        // non-focusable, so this reads the flag rather than hard-returning
        // true. The JDK's remaining limbs — focus-traversal-policy default
        // component, nearest owning Frame/Dialog showing — need the focus
        // subsystem that D_focus_managers puts permanently out of scope; every window we
        // map is a Frame or a Dialog, which the JDK short-circuits to true
        // anyway, so the limbs we drop are the ones that would not run.
        if (!getFocusableWindowState()) {
            return false;
        }
        return true;
    }

    /** AWT's user-controllable focusability flag; default true. */
    private boolean focusableWindowState = true;

    public boolean getFocusableWindowState() {
        return focusableWindowState;
    }

    public void setFocusableWindowState(boolean focusableWindowState) {
        // JDK body minus its focus-transfer tail. The undeliverable limb is
        // peer.updateFocusableWindowState(); the tail after the event (walk
        // the owner chain looking for somewhere to move focus when a focused
        // window opts out) is D_focus_managers's permanently-out focus machinery. Field
        // and event stay, and isFocusableWindow above actually reads the
        // field, so the flag is not write-only (R_decline_effect_only).
        boolean oldFocusableWindowState = this.focusableWindowState;
        this.focusableWindowState = focusableWindowState;
        if (!focusableWindowState) {
            EHelper.onUnimplemented("Window", "setFocusableWindowState", focusableWindowState);
        }
        firePropertyChange("focusableWindowState", oldFocusableWindowState, focusableWindowState);
    }

    public void addPropertyChangeListener(String arg0, PropertyChangeListener arg1) {
        // AWT's Window overrides this for alwaysOnTop/focusable-window bookkeeping.
        // We don't track those, so this is a pass-through — kept as an override so
        // the reason is visible here instead of hidden in an ancestor.
        super.addPropertyChangeListener(arg0, arg1);
    }

    public void addPropertyChangeListener(PropertyChangeListener arg0) {
        // See addPropertyChangeListener(String, PropertyChangeListener) above.
        super.addPropertyChangeListener(arg0);
    }

    public void applyResourceBundle(ResourceBundle arg0) {
        EHelper.onUnimplemented("Window", "applyResourceBundle", arg0);
    }

    public void applyResourceBundle(String arg0) {
        EHelper.onUnimplemented("Window", "applyResourceBundle", arg0);
    }

    // WindowListener, WindowFocusListener, and WindowStateListener all share
    // the EventListenerList with every other AWT listener family (see Component).
    // EventListenerList.add silently ignores null, matching AWT's contract;
    // the `synchronized` here is for signature parity with AWT — the list's
    // own add/remove are already synchronized internally.

    public synchronized void addWindowListener(WindowListener l) {
        listenerList.add(WindowListener.class, l);
    }

    public synchronized void addWindowFocusListener(WindowFocusListener l) {
        listenerList.add(WindowFocusListener.class, l);
    }

    public synchronized void addWindowStateListener(WindowStateListener l) {
        listenerList.add(WindowStateListener.class, l);
    }

    public void createBufferStrategy(int arg0, BufferCapabilities arg1) throws AWTException {
        EHelper.onUnimplemented("Window", "createBufferStrategy", arg0, arg1);
    }

    public void createBufferStrategy(int arg0) {
        EHelper.onUnimplemented("Window", "createBufferStrategy", arg0);
    }

    public BufferStrategy getBufferStrategy() {
        EHelper.onUnimplemented("Window", "getBufferStrategy");
        return null;
    }

    /**
     * AWT's other two throws are not reproduced, and deliberately: both ask the
     * platform whether it can do translucency at all — a browser can, so the
     * answers would be "yes, supported" and the throws unreachable. What is
     * declined is the peer push, so the window renders fully opaque.
     *
     * @param arg0 in {@code [0, 1]}
     * @throws IllegalArgumentException outside that range, as AWT does
     */
    public void setOpacity(float arg0) {
        if (arg0 < 0.0f || arg0 > 1.0f) {
            throw new IllegalArgumentException("The value of opacity should be in the range [0.0f .. 1.0f].");
        }
        if (arg0 < 1.0f) {
            EHelper.onUnimplemented("Window", "setOpacity", arg0);
        }
        this.opacity = arg0;
    }

    public Color getBackground() {
        // AWT's Window override forces opaque if the color has alpha<255
        // unless the window is translucent-capable; we don't model
        // transparency (see getOpacity), so the plain Component walk-up
        // is the right behavior.
        return super.getBackground();
    }

    public void setBackground(Color arg0) {
        // AWT's Window override rejects alpha<255 when translucency isn't
        // supported. We let the base setter write CSS background-color as
        // usual; alpha round-trips through com.vaadin.swingbridge.surrogates.util.CssConvert.toCss's rgba() branch.
        super.setBackground(arg0);
    }

    public void paint(Graphics arg0) {
        // AWT's Window override blits an off-screen buffer if one exists;
        // we don't double-buffer (R_layouts_close_enough — browser composes frames). Deferring
        // to Container.paint preserves the self-paint + recurse-children
        // contract so user overrides on child components still fire.
        super.paint(arg0);
    }

    public java.util.List<Image> getIconImages() {
        // Defensive copy, as the JDK's does — the caller must not be able to
        // mutate our list. Empty until setIconImages installs one; never null.
        return new ArrayList<>(icons);
    }

    public void setIconImage(Image image) {
        // JDK body: wrap and delegate, so setIconImages is the single writer
        // and an override of it catches this entry point too (R_no_vaadin_in_api limb 2).
        java.util.List<Image> imageList = new ArrayList<>();
        if (image != null) {
            imageList.add(image);
        }
        setIconImages(imageList);
    }

    public void pack() {
        // The JDK's first act here is `if (peer == null) addNotify()` — which
        // is why a packed-but-never-shown window is displayable, and why
        // pack() then setUndecorated(true) throws on the desktop.
        if (!isDisplayable()) {
            addNotify();
        }
        // AWT: size the window to its preferred-size tree. The Dialog peer
        // already auto-sizes to its CSS content — we have no pixel numbers
        // to reconcile and no way to push "fit to children" harder than the
        // browser already does under R_layouts_close_enough. Joined the layout-invalidation
        // no-op family rather than left as WARN: apps call pack() routinely
        // and the log noise would swamp real warnings.
        EHelper.onNoop("Window", "pack");
    }

    public final String getWarningString() {
        // AWT returns the "Java Applet Window" banner text for applets
        // without AWTPermission; returns null for trusted code. We're
        // always trusted (no SecurityManager in a Vaadin server), so null
        // is the correct AWT answer.
        return null;
    }

    public InputContext getInputContext() {
        EHelper.onUnimplemented("Window", "getInputContext");
        return null;
    }

    public static Window[] getOwnerlessWindows() {
        // Subset of getWindows() with owner == null.
        java.util.List<Window> out = new ArrayList<>();
        for (Window w : EHelper.getWindows()) {
            if (w.owner == null) out.add(w);
        }
        return out.toArray(new Window[0]);
    }

    // EventListenerList.remove is null-safe and no-ops on an unregistered
    // listener — matches AWT's remove*Listener contract.

    public synchronized void removeWindowListener(WindowListener l) {
        listenerList.remove(WindowListener.class, l);
    }

    public synchronized void removeWindowStateListener(WindowStateListener l) {
        listenerList.remove(WindowStateListener.class, l);
    }

    public synchronized void removeWindowFocusListener(WindowFocusListener l) {
        listenerList.remove(WindowFocusListener.class, l);
    }

    // Getters return an empty array rather than null when no listeners are
    // registered — AWT contract, honored by EventListenerList.getListeners.

    public synchronized WindowListener[] getWindowListeners() {
        return awtListeners(WindowListener.class);
    }

    public synchronized WindowFocusListener[] getWindowFocusListeners() {
        return awtListeners(WindowFocusListener.class);
    }

    public synchronized WindowStateListener[] getWindowStateListeners() {
        return awtListeners(WindowStateListener.class);
    }

    public Set<AWTKeyStroke> getFocusTraversalKeys(int arg0) {
        // Same rationale as Container.getFocusTraversalKeys — we don't model
        // focus traversal (R_infra_not_surface). AWT's Window adds extra forward/backward down
        // cycle keys on top of Container's; still empty here for the same reason.
        return Collections.emptySet();
    }

    public final void setFocusCycleRoot(boolean b) {
        // Kept as an override, and deliberately empty: AWT's Window body is empty
        // too — a Window is unconditionally a cycle root, so there is nothing to
        // store and, unlike Container's version, no bound property to fire. A WARN
        // here would report an incompleteness that does not exist.
    }

    public final boolean isFocusCycleRoot() {
        // AWT: always true, because every Window is a focus-cycle root. It used to
        // answer false here on the reasoning that no traversal engine reads it and
        // false was the less surprising default — which is the kind of judgement
        // R_decline_effect_only rules out: the JDK's body is the specification. The answer is also no
        // longer inert, since Container.getFocusTraversalPolicy gates on it, so a
        // policy installed on a window is now readable back as AWT reads it.
        return true;
    }

    public final Container getFocusCycleRootAncestor() {
        // AWT's Window has no focus-cycle-root ancestor (it IS the root on
        // the real JDK). Whether isFocusCycleRoot returns true or false
        // here, the ancestor lookup walks *up* and a Window has no parent
        // Container in practice — null is right either way.
        return null;
    }

    public void setAutoRequestFocus(boolean b) {
        // Warn only when user code asks to disable auto-focus: the behaviour we cannot
        // honour is the *change*, the default being what we do anyway. Same precedent
        // as setFocusableWindowState.
        if (!b) {
            EHelper.onUnimplemented("Window", "setAutoRequestFocus", b);
        }
        this.autoRequestFocus = b;
    }

    public boolean isAutoRequestFocus() {
        return autoRequestFocus;
    }

    public boolean isValidateRoot() {
        // AWT: Window is always a validate-root. revalidate() on a descendant
        // walks up and stops here, so Window.validate() runs instead of the
        // walk falling off the top of the tree.
        return true;
    }

    public AccessibleContext getAccessibleContext() {
        EHelper.onUnimplemented("Window", "getAccessibleContext");
        return null;
    }

    /**
     * AWT: {@code null} centers on screen, a visible component centers
     * over that component. Both map to viewport-centering here — see
     * {@link SWindow#setLocationRelativeTo} for the R_layouts_close_enough
     * close-enough rationale. Clears the explicit-location shadow (the
     * window is centered again, not at the last set coordinates);
     * fires COMPONENT_MOVED only if an explicit placement was in effect,
     * since a still-centered window didn't move.
     */
    public void setLocationRelativeTo(Component c) {
        boolean moved = this.location != null;
        this.location = null;
        withPeer(p -> {
            if (p instanceof SWindow sw) sw.centerOnScreen();
        });
        if (moved) {
            fireComponentEvent(ComponentEvent.COMPONENT_MOVED);
        }
    }

    public boolean isLocationByPlatform() {
        return locationByPlatform;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setShape(Shape arg0) {
        if (arg0 != null) {
            EHelper.onUnimplemented("Window", "setShape", arg0);
        }
    }
}
