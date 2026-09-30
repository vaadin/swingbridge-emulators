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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowFocusListener;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowListener;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowStateListener;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import java.awt.AWTEvent;
import java.awt.AWTKeyStroke;
import java.awt.Shape;
import java.awt.im.InputContext;
import java.awt.image.BufferStrategy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Surrogate for {@link java.awt.Window}: the {@code Window}-level listener
 * surface (WindowListener / WindowFocusListener / WindowStateListener) plus
 * the stateless / WARN-on-meaningful-change accessors that AWT defines on
 * Window without requiring per-instance state. Concrete subclasses
 * ({@link SFrame}, future SJDialog) — both {@link Dialog}-backed — layer
 * Frame / Dialog state on top.
 *
 * <h2>Split with subclasses</h2>
 *
 * <ul>
 *   <li><b>{@code SWindow} carries</b>: listener-family registration + fire
 *       helpers, id → process-method routing, stateless Window-level
 *       accessors whose value can be answered without instance fields
 *       ({@code isFocusableWindow}, {@code getWarningString},
 *       {@code isAlwaysOnTopSupported}, {@code isAutoRequestFocus},
 *       {@code toFront} / {@code toBack} no-ops, {@code pack} no-op,
 *       empty focus-traversal-keys, empty icon-images).</li>
 *   <li><b>Subclasses carry</b>: anything that stores state ({@code visible}
 *       shadow, {@code owner} / {@code ownedWindows}, {@code defaultCloseOperation},
 *       {@code title}, {@code contentPane}), peer-subscription
 *       (OpenedChangeListener mirroring for setVisible / dispose), and the
 *       {@code setVisible} / {@code dispose} paths that invoke the fire
 *       helpers here.</li>
 * </ul>
 *
 * <p>Window listener families don't funnel through {@link com.vaadin.swingbridge.surrogates.internal.Registrations}
 * because Vaadin 25 surfaces no server-side counterpart for
 * {@code WINDOW_ACTIVATED} / {@code WINDOW_ICONIFIED} / etc. — they're
 * server-side-fire-only (SD_listeners_without_analog shape). Storage lives directly on this
 * class via {@link #listenerList}, a {@link javax.swing.event.EventListenerList}
 * shared by the three families with type-keyed add/remove. Firing is driven by
 * the subclass's lifecycle (setVisible, dispose, peer close) calling the
 * {@code fireWindowEvent} family here.
 *
 * <p>Always-inert today: iconify / deiconify / activate / deactivate /
 * state-changed / gained-focus / lost-focus. Ported for API parity and so
 * migrated code compiles; no dispatch path drives them. Storage + removal
 * still work so listener-list introspection is honest.
 */
@com.vaadin.flow.component.dependency.StyleSheet("emul/swindow.css")
public class SWindow extends Dialog implements JComponentMixin {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    /**
     * Overlay class name that strips the Dialog chrome down to a bare
     * content rect — see {@code META-INF/resources/emul/swindow.css} and
     * {@link #setUndecoratedChrome(boolean)}.
     */
    static final String UNDECORATED_CLASS = "emul-undecorated";

    /**
     * Storage for {@link SWindowListener} / {@link SWindowFocusListener}
     * / {@link SWindowStateListener} — three families share one list
     * (same shape as JDK's {@code AWTEventMulticaster} approach), with
     * type-keyed {@code add} / {@code remove} / {@code getListeners}
     * keeping them separated. Eager allocation: an EventListenerList is
     * a single-pointer holder; deferred allocation would save a few
     * bytes on never-listened-to windows but cost a null-check on every
     * fire path. Not lazy.
     */
    private final javax.swing.event.EventListenerList listenerList =
            new javax.swing.event.EventListenerList();

    // ---- Peer↔Window sync state (R_swing_is_truth shape) -------------------------------

    /**
     * R_swing_is_truth feedback-loop guard. Set to {@code true} before our own peer
     * writes ({@link Dialog#setOpened}, {@link com.vaadin.flow.dom.Element#removeFromParent})
     * and cleared in a {@code finally}. The {@link #onPeerOpenedChanged}
     * listener bails when set, so user-driven writes don't round-trip back
     * into duplicate {@code WINDOW_CLOSING} fires.
     */
    private boolean preventPeerEvents;

    /**
     * WINDOW_OPENED first-show latch. Fires exactly once per
     * "shown-from-dispose" cycle; {@link #dispose()} resets this so a
     * post-dispose show refires WINDOW_OPENED — AWT's contract for
     * heavyweight-peer recreation.
     */
    private boolean everShown;

    /**
     * Shadow of {@link Dialog#isOpened}. AWT Window starts invisible; we
     * carry this bit so {@link #setVisible(boolean)} fired before attach
     * doesn't lose the intent. Narrow SD_vaadin_first_binding divergence — see class-level
     * javadoc §"Visible shadow bit".
     */
    private boolean visible;

    /**
     * Whether the browser has seen this window open: set when a response goes
     * out with {@code opened} true, cleared on close. Decides whether a close
     * will be answered by a client {@code closed} event (SD_detach_after_closed).
     */
    private boolean openedOnClient;

    /**
     * A close is animating in the browser and its {@code closed} event has not
     * arrived yet — {@link #dispose()} defers the detach until it does.
     */
    private boolean closeInFlight;

    /** {@link #dispose()} ran while {@link #closeInFlight}; the {@code closed} event detaches. */
    private boolean detachOnClosed;

    // ---- Owner / owned-window tree --------------------------------------

    /**
     * The owner passed to the owner-taking constructor; {@code null} for
     * top-level windows. Never reassigned after construction so an owned
     * Window stays owned for life. JDK shape: {@code SWindow} can own
     * any other {@code SWindow} (a frame can own a dialog can own a
     * window …); the field type is the supertype rather than narrowed.
     */
    final SWindow owner;

    /**
     * WeakReference-backed list, matching JDK {@code Window}'s shape.
     * Disposed owned windows get GC'd even while the owner lives, and
     * {@link #getOwnedWindows()} prunes cleared entries opportunistically.
     */
    private final List<WeakReference<SWindow>> ownedWindowList = new ArrayList<>();

    // ---- Constructors ---------------------------------------------------

    /** Untitled, no owner. */
    public SWindow() {
        this(null);
    }

    /**
     * With owner. Null owner is a top-level window. Subscribes to the
     * Dialog peer's {@code OpenedChangeEvent} so user-initiated closes
     * round-trip back into the {@link #visible} shadow and fire
     * {@code WINDOW_CLOSING} via {@link #processWindowEvent}.
     */
    public SWindow(SWindow owner) {
        super();
        _installSwingClass();
        this.visible = false;
        this.owner = owner;
        if (owner != null) owner.addOwnedWindow(this);

        addOpenedChangeListener(e ->
                SHelper.callSwing(() -> onPeerOpenedChanged(e.isOpened())));
        trackClientClose();
    }

    /**
     * Wires the {@link #openedOnClient} / {@link #closeInFlight} bookkeeping and
     * the {@code closed} listener that performs a deferred detach.
     *
     * <p>The listener mirrors Flow's own {@code OverlayAutoAddController}:
     * {@code allowInert} and {@code ALWAYS} so a window that is inert behind
     * another modal, or disabled, still gets its detach.
     */
    private void trackClientClose() {
        getElement().addPropertyChangeListener("opened", e -> {
            if (isOpened()) {
                closeInFlight = false;
                detachOnClosed = false;
                getUI().ifPresent(ui -> ui.beforeClientResponse(this, ctx -> {
                    if (isOpened()) openedOnClient = true;
                }));
            } else if (Boolean.TRUE.equals(e.getOldValue())) {
                closeInFlight = openedOnClient;
                openedOnClient = false;
            }
        });
        getElement().addEventListener("closed", e -> {
            closeInFlight = false;
            if (detachOnClosed && !isOpened()) {
                detachOnClosed = false;
                detachPeer();
            }
        }).allowInert().setDisabledUpdateMode(com.vaadin.flow.dom.DisabledUpdateMode.ALWAYS);
    }

    // --- Window listener family --------------------------------------
    //
    // Three families share one EventListenerList (same shape as JDK's
    // AWTEventMulticaster approach). Type-keyed add/remove/getListeners
    // on EventListenerList keeps them separated.

    /**
     * Register an {@link SWindowListener}. Storage only — firing is driven
     * by the subclass's lifecycle (e.g. {@code SFrame.setVisible} fires
     * {@link SWindowEvent#WINDOW_OPENED} through {@link #fireWindowEvent(int)}).
     * Null listener silently ignored, matching {@link javax.swing.event.EventListenerList}'s
     * contract.
     */
    public void addWindowListener(SWindowListener l) {
        listenerList.add(SWindowListener.class, l);
    }

    /** Remove a previously registered {@link SWindowListener}. No-op on unregistered. */
    public void removeWindowListener(SWindowListener l) {
        listenerList.remove(SWindowListener.class, l);
    }

    /** Registered {@link SWindowListener}s in registration order. Empty array when none. */
    public SWindowListener[] getWindowListeners() {
        return listenerList.getListeners(SWindowListener.class);
    }

    /** See {@link #addWindowListener(SWindowListener)}. */
    public void addWindowFocusListener(SWindowFocusListener l) {
        listenerList.add(SWindowFocusListener.class, l);
    }

    /** Remove a previously registered {@link SWindowFocusListener}. */
    public void removeWindowFocusListener(SWindowFocusListener l) {
        listenerList.remove(SWindowFocusListener.class, l);
    }

    /** Registered {@link SWindowFocusListener}s. Always empty in practice — no dispatch path today. */
    public SWindowFocusListener[] getWindowFocusListeners() {
        return listenerList.getListeners(SWindowFocusListener.class);
    }

    /** See {@link #addWindowListener(SWindowListener)}. */
    public void addWindowStateListener(SWindowStateListener l) {
        listenerList.add(SWindowStateListener.class, l);
    }

    /** Remove a previously registered {@link SWindowStateListener}. */
    public void removeWindowStateListener(SWindowStateListener l) {
        listenerList.remove(SWindowStateListener.class, l);
    }

    /** Registered {@link SWindowStateListener}s. Always empty in practice — no state transitions. */
    public SWindowStateListener[] getWindowStateListeners() {
        return listenerList.getListeners(SWindowStateListener.class);
    }

    // --- Fire helpers (called by the subclass's lifecycle paths) -----
    //
    // Short-circuit event allocation when nobody's listening — matches
    // JDK's EventListenerList pattern. Each helper routes through the
    // matching process*Event method so a user-overridden process method
    // still runs.

    /**
     * Fire one of the non-focus, non-state ids (OPENED / CLOSING / CLOSED
     * / ICONIFIED / DEICONIFIED / ACTIVATED / DEACTIVATED) through
     * {@link #processWindowEvent(SWindowEvent)}. Skipped when no
     * {@link SWindowListener} is registered.
     */
    public void fireWindowEvent(int id) {
        if (listenerList.getListenerCount(SWindowListener.class) == 0) return;
        processWindowEvent(new SWindowEvent(this, id));
    }

    /**
     * Fire {@link SWindowEvent#WINDOW_GAINED_FOCUS} /
     * {@link SWindowEvent#WINDOW_LOST_FOCUS}. {@code opposite} is always
     * {@code null} today — browser focus transitions don't surface the
     * opposite server-side.
     */
    public void fireWindowFocusEvent(int id, Component opposite) {
        if (listenerList.getListenerCount(SWindowFocusListener.class) == 0) return;
        processWindowFocusEvent(new SWindowEvent(this, id, opposite));
    }

    /**
     * Fire {@link SWindowEvent#WINDOW_STATE_CHANGED}. Never called today
     * (no iconify / maximize in our Dialog-backed Window); preserved so a
     * future slice that models window state can plug straight in.
     */
    public void fireWindowStateEvent(int oldState, int newState) {
        if (listenerList.getListenerCount(SWindowStateListener.class) == 0) return;
        processWindowStateEvent(new SWindowEvent(
                this, SWindowEvent.WINDOW_STATE_CHANGED, oldState, newState));
    }

    // --- Process helpers (id → listener-method dispatch) -------------

    /**
     * Dispatch the non-focus, non-state ids to registered
     * {@link SWindowListener}s. {@code null} guard matches
     * {@code ComponentMixin}'s process family — a smoke-test null probe
     * shouldn't crash.
     */
    public void processWindowEvent(SWindowEvent e) {
        if (e == null) return;
        int id = e.getID();
        for (SWindowListener l : listenerList.getListeners(SWindowListener.class)) {
            switch (id) {
                case SWindowEvent.WINDOW_OPENED       -> l.windowOpened(e);
                case SWindowEvent.WINDOW_CLOSING      -> l.windowClosing(e);
                case SWindowEvent.WINDOW_CLOSED       -> l.windowClosed(e);
                case SWindowEvent.WINDOW_ICONIFIED    -> l.windowIconified(e);
                case SWindowEvent.WINDOW_DEICONIFIED  -> l.windowDeiconified(e);
                case SWindowEvent.WINDOW_ACTIVATED    -> l.windowActivated(e);
                case SWindowEvent.WINDOW_DEACTIVATED  -> l.windowDeactivated(e);
                default -> { /* focus/state ids route to their own process methods */ }
            }
        }
    }

    /** Dispatch {@code WINDOW_GAINED_FOCUS} / {@code WINDOW_LOST_FOCUS} to focus listeners. */
    public void processWindowFocusEvent(SWindowEvent e) {
        if (e == null) return;
        int id = e.getID();
        for (SWindowFocusListener l : listenerList.getListeners(SWindowFocusListener.class)) {
            switch (id) {
                case SWindowEvent.WINDOW_GAINED_FOCUS -> l.windowGainedFocus(e);
                case SWindowEvent.WINDOW_LOST_FOCUS   -> l.windowLostFocus(e);
                default -> { /* out-of-range id, drop */ }
            }
        }
    }

    /** Dispatch {@code WINDOW_STATE_CHANGED} to state listeners. */
    public void processWindowStateEvent(SWindowEvent e) {
        if (e == null) return;
        if (e.getID() != SWindowEvent.WINDOW_STATE_CHANGED) return;
        for (SWindowStateListener l : listenerList.getListeners(SWindowStateListener.class)) {
            l.windowStateChanged(e);
        }
    }

    /**
     * Top-level AWT-event router. Only {@link SWindowEvent} ids dispatch
     * here; every other event type falls through (non-Window events
     * wouldn't have been passed in). Matches AWT's shape —
     * {@code Window.processEvent} routes by id then delegates to the
     * Container/Component chain for non-Window ids.
     */
    public void processEvent(AWTEvent e) {
        if (e instanceof SWindowEvent we) {
            switch (we.getID()) {
                case SWindowEvent.WINDOW_OPENED,
                     SWindowEvent.WINDOW_CLOSING,
                     SWindowEvent.WINDOW_CLOSED,
                     SWindowEvent.WINDOW_ICONIFIED,
                     SWindowEvent.WINDOW_DEICONIFIED,
                     SWindowEvent.WINDOW_ACTIVATED,
                     SWindowEvent.WINDOW_DEACTIVATED
                        -> processWindowEvent(we);
                case SWindowEvent.WINDOW_GAINED_FOCUS,
                     SWindowEvent.WINDOW_LOST_FOCUS
                        -> processWindowFocusEvent(we);
                case SWindowEvent.WINDOW_STATE_CHANGED
                        -> processWindowStateEvent(we);
                default -> { /* out-of-range WindowEvent id, drop — matches AWT */ }
            }
        }
        // Non-SWindowEvent types have no server-side routing we can do
        // generically; caller is responsible for dispatching them.
    }

    // --- Stateless Window-level accessors ----------------------------
    //
    // Answers that don't depend on fields. Live on SWindow so subclasses
    // (SFrame, SJDialog, …) get them for free.

    /**
     * JDK Frame/Dialog default is true. We don't model focus traversal
     * (R_infra_not_surface) so the gate never rejects anyone — {@code true} is the honest
     * "can this window take focus at all?" answer callers check.
     */
    public boolean isFocusableWindow() { return true; }

    /**
     * AWT's applet-sandbox banner. No SecurityManager runs in a Vaadin
     * server, so we're always trusted — null is AWT's correct answer.
     */
    public String getWarningString() { return null; }

    /** We don't honor always-on-top (browsers have no window z-order). */
    public boolean isAlwaysOnTopSupported() { return false; }

    /** AWT default; see {@link #setAutoRequestFocus(boolean)}. */
    public boolean isAutoRequestFocus() { return true; }

    /** AWT default; setter WARNs on {@code true}. */
    public boolean isAlwaysOnTop() { return false; }

    /** AWT default; setter WARNs on {@code true}. */
    public boolean isLocationByPlatform() { return false; }

    /** We can't render non-rectangular windows (DOM is rectangular). */
    public Shape getShape() { return null; }

    /** AWT default; we don't model transparency. */
    public float getOpacity() { return 1.0f; }

    /** AWT default; setter WARNs on {@code false}. */
    public boolean getFocusableWindowState() { return true; }

    /**
     * Window is always a validate-root in AWT. Resolves the mixin diamond
     * with {@link JComponentMixin#isValidateRoot()} (which returns false)
     * — Window's contract wins because a Frame / Dialog IS-A Window, and
     * migrated code that walks {@code revalidate} stops here.
     */
    @Override
    public boolean isValidateRoot() { return true; }

    /** No OS window-manager concept — {@code false} is the honest default. */
    public boolean isActive() { return false; }

    /** Browser tab focus isn't tracked server-side. */
    public boolean isFocused() { return false; }

    /** Focus traversal isn't modeled (R_infra_not_surface). */
    public Component getFocusOwner() { return null; }

    /** Focus traversal isn't modeled (R_infra_not_surface). */
    public Component getMostRecentFocusOwner() { return null; }

    /**
     * Window in AWT is always-true; we return {@code false} for parity
     * with Container's silent-false default since no focus-cycle walker
     * exists either way (R_infra_not_surface). Flip if focus modeling lands.
     */
    public boolean isFocusCycleRoot() { return false; }

    /** No parent Container above a Window. */
    public Component getFocusCycleRootAncestor() { return null; }

    /** Focus traversal isn't modeled (R_infra_not_surface). */
    public Set<AWTKeyStroke> getFocusTraversalKeys(int id) { return Collections.emptySet(); }

    /** Icons aren't wired to the browser (favicons are route-level). */
    public List<java.awt.Image> getIconImages() { return Collections.emptyList(); }

    /** No IME subsystem; {@code null} matches AWT's pre-IM default. */
    public InputContext getInputContext() { return null; }

    // --- Stateful no-ops + setters (WARN-on-meaningful-change) -------
    //
    // Setters that ask for behavior we can't honor: silent on the AWT
    // default (setAlwaysOnTop(false), setFocusableWindowState(true), …)
    // and WARN on the change direction. Pattern lifted from the
    // emulator-layer Window's existing accepts.

    /** WARN on {@code true} — no z-order in a browser tab. Silent on {@code false} (default). */
    public void setAlwaysOnTop(boolean b) {
        if (b) SHelper.onUnimplemented(this, "setAlwaysOnTop", b);
    }

    /** WARN on {@code true} — browsers don't position tabs. Silent on {@code false}. */
    public void setLocationByPlatform(boolean b) {
        if (b) SHelper.onUnimplemented(this, "setLocationByPlatform", b);
    }

    /** WARN on non-null — non-rectangular shapes don't render. */
    public void setShape(Shape shape) {
        if (shape != null) SHelper.onUnimplemented(this, "setShape", shape);
    }

    /** WARN — transparency isn't modeled. */
    public void setOpacity(float opacity) {
        if (opacity != 1.0f) SHelper.onUnimplemented(this, "setOpacity", opacity);
    }

    /** WARN on {@code false} — we have no focus subsystem to opt out of. */
    public void setFocusableWindowState(boolean b) {
        if (!b) SHelper.onUnimplemented(this, "setFocusableWindowState", b);
    }

    /** WARN on {@code false} — auto-focus behavior isn't modeled. */
    public void setAutoRequestFocus(boolean b) {
        if (!b) SHelper.onUnimplemented(this, "setAutoRequestFocus", b);
    }

    /** WARN on {@code true} — no cycle-root traversal machinery. */
    public void setFocusCycleRoot(boolean b) {
        if (b) SHelper.onUnimplemented(this, "setFocusCycleRoot", b);
    }

    /**
     * Silent accept — icons don't wire to the browser (favicons belong to
     * the route, not a Window). Apps routinely set icons at startup;
     * logging each call would swamp real warnings.
     */
    public void setIconImages(List<? extends java.awt.Image> icons) {
        // silent by design — see javadoc
    }

    /** Convenience for {@code setIconImages(List.of(icon))} — same silent-accept. */
    public void setIconImage(java.awt.Image icon) {
        // silent by design — see setIconImages javadoc
    }

    /** Silent no-op — browsers auto-stack overlays; apps pair this with setVisible routinely. */
    public void toFront() { SHelper.onNoop(this, "toFront"); }

    /** Silent no-op — no z-order in a browser tab. */
    public void toBack() { SHelper.onNoop(this, "toBack"); }

    /**
     * Silent no-op — the Dialog peer auto-sizes to content, and we have
     * no pixel numbers to reconcile (R_layouts_close_enough). Apps call pack() routinely as a
     * "finalise layout" gesture; WARN-per-call would swamp logs.
     */
    public void pack() { SHelper.onNoop(this, "pack"); }

    /** WARN — {@code ResourceBundle}-driven UI text isn't modeled. */
    public void applyResourceBundle(java.util.ResourceBundle bundle) {
        SHelper.onUnimplemented(this, "applyResourceBundle", bundle);
    }

    /** WARN — same as {@link #applyResourceBundle(java.util.ResourceBundle)}. */
    public void applyResourceBundle(String baseName) {
        SHelper.onUnimplemented(this, "applyResourceBundle", baseName);
    }

    /** WARN — no BufferStrategy / offscreen-buffer pipeline. */
    public void createBufferStrategy(int numBuffers) {
        SHelper.onUnimplemented(this, "createBufferStrategy", numBuffers);
    }

    /** WARN — see {@link #createBufferStrategy(int)}. */
    public void createBufferStrategy(int numBuffers, java.awt.BufferCapabilities caps)
            throws java.awt.AWTException {
        SHelper.onUnimplemented(this, "createBufferStrategy", numBuffers, caps);
    }

    /** WARN — no BufferStrategy. */
    public BufferStrategy getBufferStrategy() {
        SHelper.onUnimplemented(this, "getBufferStrategy");
        return null;
    }

    // ---- Window geometry (Vaadin-first: Dialog top/left + HasSize) ------
    //
    // Window placement maps 1:1 onto the Dialog overlay's native
    // top/left/width/height — no layout manager involved, so R_layouts_close_enough's
    // pixel-layout exclusion doesn't apply. Undecorated windows
    // (splash, toast) are almost always explicitly placed; decorated
    // frames/dialogs benefit too (setBounds is routine in Swing code).
    // Getters are Vaadin's own getTop/getLeft/getWidth/getHeight (R_vaadin_first —
    // setter writes the Vaadin property, getter reads it back).

    /**
     * Position the overlay's top-left corner at viewport pixel
     * ({@code x}, {@code y}). Setting an explicit position takes the
     * Dialog out of its default centered placement;
     * {@link #centerOnScreen()} restores it.
     */
    public void setLocation(int x, int y) {
        setLeft(x + "px");
        setTop(y + "px");
    }

    /** Pixel size for the overlay — Dialog's own width/height channel. */
    public void setSize(int width, int height) {
        setWidth(width + "px");
        setHeight(height + "px");
    }

    /** {@link #setLocation(int, int)} + {@link #setSize(int, int)} in one call. */
    public void setBounds(int x, int y, int width, int height) {
        setLocation(x, y);
        setSize(width, height);
    }

    /**
     * Clear any explicit {@link #setLocation(int, int)} placement and
     * return to the Dialog overlay's default centered position.
     */
    public void centerOnScreen() {
        setLeft(null);
        setTop(null);
    }

    // ---- Undecorated chrome ---------------------------------------------

    /**
     * Toggle the {@code emul-undecorated} overlay class: strips the
     * Dialog's border radius, content padding, and header (title +
     * SFrame's close-X chrome) so the window renders as a bare content
     * rect — Swing's undecorated-window shape. The box-shadow stays:
     * in a browser the window floats over live page content, not a
     * desktop, and a shadowless rect there is illegible (documented R_layouts_close_enough
     * divergence; see {@code emul/swindow.css}).
     *
     * <p>Always on for {@link SJWindow} ({@code javax.swing.JWindow} is
     * undecorated by definition); driven by {@code setUndecorated} on
     * SFrame and the emulator-layer Frame / Dialog / JFrame.
     */
    public void setUndecoratedChrome(boolean undecorated) {
        if (undecorated) {
            addClassName(UNDECORATED_CLASS);
        } else {
            removeClassName(UNDECORATED_CLASS);
        }
    }

    /** Vaadin-first read-back of {@link #setUndecoratedChrome(boolean)}. */
    public boolean isUndecoratedChrome() {
        return hasClassName(UNDECORATED_CLASS);
    }

    /**
     * Re-center the window. AWT semantics: {@code null} centers on
     * screen; a visible component argument centers over that component.
     * We map <em>both</em> to viewport-centering ({@link #centerOnScreen()})
     * — the browser has no screen geometry to compute a
     * component-relative position from, and a centered overlay is the
     * close-enough answer for the "center over my main window" idiom
     * (the main window effectively IS the viewport). R_layouts_close_enough divergence, accepted.
     */
    public void setLocationRelativeTo(Component c) {
        centerOnScreen();
    }

    // ---- Peer → Window mirror ------------------------------------------

    /**
     * Fires no {@code "enabled"} property change, unlike every other
     * {@link JComponentMixin} implementor. The bound {@code "enabled"} property
     * is {@code JComponent}'s, and {@code java.awt.Window} — with
     * {@code Frame}, {@code JFrame}, {@code JDialog} and {@code JWindow} under
     * it — is not a {@code JComponent}; it inherits
     * {@code java.awt.Component.setEnabled}, which fires nothing bound (SD_property_fanout_audit).
     *
     * <p>This class takes {@code JComponentMixin} for its helper surface
     * (border, tooltip, name), not because a window is a {@code JComponent},
     * so the mixin's JComponent-level property fan-out has to be suppressed
     * here. One override covers {@code SFrame}, {@code SJFrame},
     * {@code SJDialog} and {@code SJWindow} by inheritance.
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (isEnabled() == enabled) return;
        // The element write HasEnabled's default performs, reached directly:
        // JComponentMixin is this class's only direct superinterface, so
        // JComponentMixin.super.setEnabled would re-enter the very fan-out
        // being suppressed.
        getElement().setEnabled(enabled);
    }

    /**
     * {@link Dialog#isOpened} transitioned. Mirror into the
     * {@link #visible} shadow, fire {@code COMPONENT_SHOWN} /
     * {@code COMPONENT_HIDDEN} through the component-listener path, and
     * fire {@code WINDOW_CLOSING} on the close transition.
     *
     * <p>In practice only the close direction reaches here from the
     * client (Vaadin has no user-initiated "open a Dialog" gesture); we
     * handle both for robustness.
     *
     * <p>No {@code "visible"} property change — here or in {@link #setVisible}
     * or {@link #dispose()}. {@code JPopupMenu} is the only class in
     * {@code java.awt} + {@code javax.swing} that fires one, so a listener
     * registered on a window would be getting an event the desktop never sent.
     * AWT signals visibility with a {@code ComponentEvent}, which is what the
     * three methods fire. Same finding as
     * <a href="../../../../emulators/decisions.md#D_window_fanout">D_window_fanout</a>
     * removed from the emulator layer; R_vaadin_first does not license inventing Swing
     * events any more than R_decline_effect_only does.
     */
    private void onPeerOpenedChanged(boolean opened) {
        if (preventPeerEvents) return;
        boolean old = this.visible;
        if (old == opened) return;
        this.visible = opened;
        fireComponentEvent(opened
                ? SComponentEvent.COMPONENT_SHOWN
                : SComponentEvent.COMPONENT_HIDDEN);
        if (!opened) {
            // AWT's "user asks to close" is WINDOW_CLOSING, distinct from
            // WINDOW_CLOSED (which fires on programmatic dispose). Route
            // through processWindowEvent directly (not fireWindowEvent) so
            // a close-op switch overridden by SJFrame / SJDialog applies
            // even when no WindowListener is registered — DISPOSE_ON_CLOSE
            // must still detach, EXIT_ON_CLOSE must still throw per D_gap_severity_triage,
            // regardless of listener presence.
            fireClosingEvent();
        }
    }

    // ---- Lifecycle fire seams (SD_internalframe_fire_seams) ----------------------------------
    //
    // The three points where SWindow's lifecycle fires a window event are
    // routed through these protected methods so a subclass whose lifecycle
    // maps onto a *different* event family can translate without re-copying
    // the everShown latch / preventPeerEvents guard (both private).
    // SJInternalFrame overrides all three to fire INTERNAL_FRAME_* instead
    // (SD_internalframe_fire_seams). Defaults reproduce the pre-seam behaviour exactly — zero
    // change for SFrame / SJDialog / SJWindow.

    /** First-show fire (WINDOW_OPENED), gated by {@link #everShown} in {@link #setVisible}. */
    protected void fireOpenedEvent() {
        fireWindowEvent(SWindowEvent.WINDOW_OPENED);
    }

    /**
     * User-close fire (WINDOW_CLOSING). Routed through
     * {@link #processWindowEvent} directly (not {@link #fireWindowEvent})
     * so a close-op switch overridden by SJFrame / SJDialog applies even
     * when no WindowListener is registered.
     */
    protected void fireClosingEvent() {
        processWindowEvent(new SWindowEvent(this, SWindowEvent.WINDOW_CLOSING));
    }

    /** Dispose fire (WINDOW_CLOSED), from {@link #dispose}. */
    protected void fireClosedEvent() {
        fireWindowEvent(SWindowEvent.WINDOW_CLOSED);
    }

    /**
     * Override of {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin#addComponentListener}
     * — the default wiring dispatches COMPONENT_SHOWN on peer attach /
     * COMPONENT_HIDDEN on detach, which assumes attach ↔ visibility.
     * That assumption doesn't hold for SWindow: the Dialog peer stays
     * attached across {@code setVisible(false)} (only detaches on
     * {@link #dispose()}), so attach-driven fires misrepresent the
     * show/hide transitions we care about. Instead we register with a
     * no-op Vaadin-registration and drive the dispatch from
     * {@link #setVisible(boolean)} / {@link #onPeerOpenedChanged(boolean)}
     * / {@link #dispose()} directly via {@link #fireComponentEvent(int)}.
     */
    @Override
    public void addComponentListener(com.vaadin.swingbridge.surrogates.awt.event.SComponentListener listener) {
        com.vaadin.swingbridge.surrogates.internal.Registrations.of(this).add(listener, () -> { /* no peer sub to tear down */ });
    }

    /**
     * Fire a COMPONENT_SHOWN / COMPONENT_HIDDEN through the
     * {@link com.vaadin.swingbridge.surrogates.internal.Registrations}-backed SComponentListener
     * registry. Short-circuits when no listener is registered.
     */
    private void fireComponentEvent(int id) {
        var listeners = getComponentListeners();
        if (listeners.length == 0) return;
        SComponentEvent ev = new SComponentEvent(this, id);
        for (var l : listeners) {
            if (id == SComponentEvent.COMPONENT_SHOWN) l.componentShown(ev);
            else if (id == SComponentEvent.COMPONENT_HIDDEN) l.componentHidden(ev);
        }
    }

    // ---- setVisible / dispose ------------------------------------------

    /**
     * Mirror first, then drive the peer under {@link #preventPeerEvents}.
     * {@code setVisible(true)} attaches us at the point
     * {@link #attachForShow(UI)} picks ({@link IllegalStateException} when
     * there is no current {@link UI} — a narrow D_never_fail_on_gaps carve-out
     * for an environment we genuinely can't recover from). Refiring
     * {@code WINDOW_OPENED} is gated on {@link #everShown}, which
     * {@link #dispose()} resets so a dispose→show cycle fires again.
     */
    @Override
    public void setVisible(boolean b) {
        boolean old = this.visible;
        if (old == b) return;
        this.visible = b;
        preventPeerEvents = true;
        try {
            if (b) {
                UI ui = UI.getCurrent();
                if (ui == null) {
                    throw new IllegalStateException(
                            "setVisible(true) on " + this + " but there is no current Vaadin UI — "
                                    + "call from a request thread or inside UI.access");
                }
                attachForShow(ui);
                setOpened(true);
            } else {
                setOpened(false);
            }
        } finally {
            preventPeerEvents = false;
        }
        fireComponentEvent(b
                ? SComponentEvent.COMPONENT_SHOWN
                : SComponentEvent.COMPONENT_HIDDEN);
        if (b && !everShown) {
            everShown = true;
            fireOpenedEvent();
        }
    }

    /**
     * Attach — or re-attach — this window where a show should put it: under
     * the <em>active</em> modal when this window's owner chain reaches it,
     * otherwise in the UI.
     *
     * <p>Parentage <em>is</em> Flow's modality mechanism, which is why
     * the attach point carries the whole of D_owned_window_attach:
     * {@code inert} goes on the UI element and {@code ignoreParentInert}
     * on the active modal's, and {@code InertData} inherits down the
     * StateNode parent chain — so a window hanging under the modal stays
     * live where a body-sibling goes dead. Recomputed per show rather than
     * cached (D_reparent_each_show): hiding an owner cascades to the
     * windows it owns, and the modal a child was nested under can be
     * demoted or disposed before that child shows again.
     */
    private void attachForShow(UI ui) {
        Component modalOwner = activeModalInOwnerChain(ui);
        com.vaadin.flow.dom.Element desiredParent =
                (modalOwner != null ? modalOwner : ui).getElement();
        if (desiredParent.equals(getElement().getParent())) return;
        // Both calls append, and Flow's appendChild detaches from the previous
        // parent first, so this is a move rather than a second parentage.
        if (modalOwner != null) {
            ui.addToModalComponent(this);
        } else {
            ui.add(this);
        }
    }

    /**
     * The UI's active modal component if it is this window's owner, its
     * owner's owner, and so on; {@code null} otherwise (no modal up, or the
     * active modal is unrelated to this window).
     *
     * <p>The walk has one hole and it is unreachable: a
     * {@code @MainWindow} {@code JFrame} peers on an {@code SJPanel}
     * (D_frame_strategy), contributing no {@code SWindow} link — but AWT
     * {@code Frame} takes no owner, so such a frame is only ever the
     * chain's root, never a link in the middle that would stop the walk short.
     */
    private Component activeModalInOwnerChain(UI ui) {
        Component active = ui.getInternals().getActiveModalComponent();
        if (active == null) return null;
        for (SWindow w = this.owner; w != null; w = w.owner) {
            if (w == active) return active;
        }
        return null;
    }

    /**
     * AWT: close the window, release the native peer, fire
     * {@code WINDOW_CLOSED}, walk {@code removeNotify}. Our peer is
     * {@code this} and can't be nulled — we achieve "non-displayable" by
     * detaching from the UI. {@link #everShown} resets so a subsequent
     * {@link #setVisible(boolean)} refires {@code WINDOW_OPENED} —
     * AWT's dispose-then-show contract.
     *
     * <p>The detach waits for the client's {@code closed} event when the browser
     * is animating a close, so the overlay can hand focus back while its content
     * is still in the DOM (SD_detach_after_closed). Until then the peer sits
     * attached and closed, the state {@code setVisible(false)} leaves it in.
     */
    public void dispose() {
        boolean wasVisible = this.visible;
        preventPeerEvents = true;
        try {
            if (isOpened()) setOpened(false);
        } finally {
            preventPeerEvents = false;
        }
        if (closeInFlight) {
            detachOnClosed = true;
        } else {
            detachPeer();
        }
        if (wasVisible) {
            this.visible = false;
            fireComponentEvent(SComponentEvent.COMPONENT_HIDDEN);
        }
        everShown = false;
        fireClosedEvent();
    }

    private void detachPeer() {
        if (!getElement().getNode().isAttached()) return;
        preventPeerEvents = true;
        try {
            getElement().removeFromParent();
        } finally {
            preventPeerEvents = false;
        }
    }

    // ---- Owner / owned-window tree -------------------------------------

    public SWindow getOwner() {
        return owner;
    }

    /**
     * Register {@code child} as owned by this window. Called from the
     * owner-taking constructor; package-private since it's an internal
     * invariant — user code doesn't reach for this directly.
     */
    void addOwnedWindow(SWindow child) {
        synchronized (ownedWindowList) {
            ownedWindowList.add(new WeakReference<>(child));
        }
    }

    /**
     * Dereference each WeakReference, keep live ones, opportunistically
     * drop cleared entries. Mirrors JDK {@code Window.getOwnedWindows()}'s
     * weak-ref pruning. Logically independent of peer attachment state —
     * an owner link persists whether the owned window is displayed or not.
     */
    public SWindow[] getOwnedWindows() {
        List<SWindow> live = new ArrayList<>();
        synchronized (ownedWindowList) {
            Iterator<WeakReference<SWindow>> it = ownedWindowList.iterator();
            while (it.hasNext()) {
                SWindow w = it.next().get();
                if (w == null) it.remove();
                else live.add(w);
            }
        }
        return live.toArray(new SWindow[0]);
    }

    // ---- Static live-graph accessors (D_single_ui_per_session: per-UI scope, not JVM) -------

    /**
     * Every {@link SWindow} reachable from the current {@link UI}. Walks
     * the component tree for SWindow instances, then descends each
     * SWindow's owned-tree so an attached owner pulls in its owned
     * windows even when those aren't individually opened.
     */
    public static List<SWindow> getWindows() {
        UI ui = UI.getCurrent();
        if (ui == null) return Collections.emptyList();
        List<SWindow> out = new ArrayList<>();
        Set<SWindow> seen = new HashSet<>();
        collectWindows(ui, out, seen);
        return out;
    }

    private static void collectWindows(Component c, List<SWindow> out, Set<SWindow> seen) {
        if (c instanceof SWindow sw && seen.add(sw)) {
            out.add(sw);
            for (SWindow owned : sw.getOwnedWindows()) {
                collectWindows(owned, out, seen);
            }
        }
        c.getChildren().forEach(child -> collectWindows(child, out, seen));
    }

    /** Subset of {@link #getWindows()} with {@code owner == null}. */
    public static List<SWindow> getOwnerlessWindows() {
        List<SWindow> out = new ArrayList<>();
        for (SWindow w : getWindows()) if (w.owner == null) out.add(w);
        return out;
    }

    // ---- paramString ---------------------------------------------------

    /**
     * Diagnostic — mirrors the shape of {@code java.awt.Window.paramString()}.
     * Subclasses ({@link SFrame}) override and chain super to add
     * Frame-/Dialog-specific fields like {@code title}.
     */
    public String paramString() {
        return "visible=" + visible;
    }
}
