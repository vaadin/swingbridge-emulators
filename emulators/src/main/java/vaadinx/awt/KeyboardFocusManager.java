/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.KeyboardFocusManager
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator (not generated). Flattens the JDK's four-class focus
// chain — KeyboardFocusManager → DefaultKeyboardFocusManager →
// javax.swing.FocusManager → DefaultFocusManager — into two: this class plus
// the statics-only vaadinx.swing.FocusManager. The two Default* classes exist
// almost entirely for the key-event machinery tier 3 drops, and reproducing
// them would mean porting KeyEventDispatcher / KeyEventPostProcessor for
// methods that WARN. They are deliberately absent instead, so code naming them
// fails at compile time with the class name in the message rather than getting
// a stub that silently does nothing (D_focus_managers).

/**
 * Emulator for {@link java.awt.KeyboardFocusManager} — who has focus, and
 * moving it on (D_focus_managers). Concrete, where the JDK's is abstract, so the statics
 * have something to hand out. The auto-advance idiom, from inside a
 * {@code Document} that has no back-reference to its own component:
 *
 * <pre>{@code
 * Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
 * if (owner instanceof JTextComponent text && text.getDocument() == this) {
 *     owner.transferFocus();          // → focusNextComponent(owner)
 * }
 * }</pre>
 *
 * <p>Real: the focus-owner and window queries, and single-step traversal.
 * WARNing (R_match_swing_errors sub-bucket (b)): key-event dispatch, focus cycle roots,
 * {@code FocusTraversalPolicy} as a pluggable object, the protected
 * {@code setGlobal*} mutators, and the vetoable-listener registry — the browser
 * owns key routing and Tab order.
 *
 * <p><b>Stateless facade; the state is per UI.</b> Every method resolves
 * {@code UI.getCurrent()} at call time and reads
 * {@link com.vaadin.swingbridge.surrogates.FocusTracker}'s per-UI pointer, so this process-wide
 * instance holds nothing UI-bound — which is what makes
 * {@code static final FocusManager FM = FocusManager.getCurrentManager();}
 * safe to write. Per-UI instances would turn that idiom into a cross-tab bug
 * the static-field sweep cannot catch, since a focus manager is not a
 * {@code Component}.
 *
 * <p><b>Bound properties are per UI, and only the four the getters can back.</b>
 * The listener registry lives in the current UI's data rather than on this
 * process-wide facade — a {@code PropertyChangeSupport} field here would pool every
 * session's listeners on one list and deliver every tab's focus change to all of
 * them, the same cross-tab hazard the paragraph above rules out for state:
 *
 * <pre>{@code
 * FocusManager.getCurrentManager().addPropertyChangeListener("focusOwner",
 *         e -> log.info("focus: {} -> {}", e.getOldValue(), e.getNewValue()));
 * }</pre>
 *
 * <p>{@code focusOwner}, {@code permanentFocusOwner}, {@code focusedWindow} and
 * {@code activeWindow} fire off {@link com.vaadin.swingbridge.surrogates.FocusTracker}'s pointer move —
 * the place the real transition happens, so what the event announces is what
 * {@link #getFocusOwner()} answers a line later. {@code defaultFocusTraversalPolicy}
 * fires from its own setter. The remaining three JDK properties stay silent
 * <em>because their getters would deny the transition</em>, the reasoning
 * D_reverse_fanout_rows applied to {@code JPopupMenu.setVisible}:
 * {@code currentFocusCycleRoot} and the traversal-key sets have no state behind
 * them (tier 3 below), and {@code setCurrentKeyboardFocusManager} does not swap the
 * manager, so a {@code managingFocus} event would report a handover that did not
 * happen.
 *
 * <p><b>Vetoable properties are not honoured</b> — {@link #addVetoableChangeListener}
 * still WARNs and registers nothing. The JDK vetoes <em>before</em> the change; here
 * the browser has already moved focus by the time the server hears about it, so a
 * veto could only be implemented as a focus-restoring effect, which is a different
 * thing from the notification R_decline_effect_only owes.
 */
public class KeyboardFocusManager implements java.io.Serializable {

    public static final int FORWARD_TRAVERSAL_KEYS = 0;
    public static final int BACKWARD_TRAVERSAL_KEYS = 1;
    public static final int UP_CYCLE_TRAVERSAL_KEYS = 2;
    public static final int DOWN_CYCLE_TRAVERSAL_KEYS = 3;

    /**
     * The one facade handed out process-wide. Safe as a static because it holds
     * no state — see the class javadoc.
     */
    private static final KeyboardFocusManager CURRENT = new KeyboardFocusManager();

    public KeyboardFocusManager() {}

    /** The shared stateless facade; never {@code null}. */
    public static KeyboardFocusManager getCurrentKeyboardFocusManager() {
        return CURRENT;
    }

    /**
     * WARN and ignore. A replacement manager's only overridable hooks are the
     * ones this class WARNs on anyway (key dispatch, traversal policy), so
     * honouring the swap would advertise more working behaviour than exists.
     * Revisit if a real migrated app turns up subclassing a focus manager.
     */
    public static void setCurrentKeyboardFocusManager(KeyboardFocusManager manager) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setCurrentKeyboardFocusManager", manager);
    }

    // --- Tier 1: the focus-owner queries -----------------------------

    /**
     * The emulator that owns browser focus in the current UI, or {@code null}
     * when focus sits outside our component tree (on {@code <body>}, or on a
     * Vaadin component the app added outside vaadinx).
     *
     * <p>Resolved through {@link vaadinx.EHelper#emulatorFor}, so focus landing
     * on the inner field of a composite peer answers with the emulator that
     * owns the composite.
     */
    public Component getFocusOwner() {
        com.vaadin.flow.component.Component focused = com.vaadin.swingbridge.surrogates.FocusTracker.getFocusOwner();
        return focused == null ? null : vaadinx.EHelper.emulatorFor(focused);
    }

    /**
     * Same answer as {@link #getFocusOwner()}. The JDK's split exists for
     * <em>temporary</em> focus loss (a scrollbar drag, a native popup stealing
     * focus and giving it back); the browser has no such concept, so every
     * focus change we see is permanent.
     */
    public Component getPermanentFocusOwner() {
        return getFocusOwner();
    }

    /**
     * The {@link Window} containing the focus owner — found by walking the
     * emulator parent chain, so a {@code JDialog}, {@code JWindow} or
     * {@code JInternalFrame} overlay wins over the frame behind it.
     *
     * <p>The walk needs no special case for the {@code @MainWindow} frame:
     * that frame peers on an {@code SJPanel} rather than a dialog (D_frame_strategy), but
     * it is still a {@code JFrame} and therefore still a {@link Window}
     * emulator in the parent chain, so the walk finds it like any other.
     * {@code null} means nothing is focused, or the focus owner is not parented
     * under a Window yet — the same answer the JDK gives.
     */
    public Window getFocusedWindow() {
        return windowOf(getFocusOwner());
    }

    /**
     * Same answer as {@link #getFocusedWindow()}: a browser tab has exactly one
     * active window by construction, so the JDK's focused-vs-active distinction
     * (which separates a focused owner-less {@code Window} from the {@code Frame}
     * that activates with it) has nothing to distinguish here.
     */
    public Window getActiveWindow() {
        return getFocusedWindow();
    }

    /**
     * Drops focus entirely: clears the server-side pointer <em>and</em> blurs
     * the browser's {@code activeElement}, so the two stay in agreement.
     */
    public void clearFocusOwner() {
        com.vaadin.swingbridge.surrogates.FocusTracker.setFocusOwner(null);
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (ui != null) {
            ui.getPage().executeJs("document.activeElement && document.activeElement.blur();");
        }
    }

    /**
     * Same as {@link #clearFocusOwner()}. The JDK's split is about which
     * AppContext may clear whose focus owner — a distinction with no analogue
     * in a single browser tab.
     */
    public void clearGlobalFocusOwner() {
        clearFocusOwner();
    }

    // --- Tier 2: single-step traversal -------------------------------

    /**
     * Moves focus to the next focusable component after {@code component},
     * wrapping at the end; silently stays put when there is no other candidate
     * (as the JDK does). The engine behind {@link Component#transferFocus()}.
     *
     * <p><b>Order comes from the emulator tree, not the browser's Tab
     * order</b> — a depth-first pre-order walk from the top-level ancestor.
     * The two diverge only where DOM order or an explicit {@code tabindex}
     * disagrees with our child order, which R_layouts_close_enough's close-enough licence
     * covers; a browser round trip for "next tabbable element" would agree
     * by construction but be async and untestable browserless.
     */
    public void focusNextComponent(Component component) {
        traverse(component, +1);
    }

    /** Backward counterpart of {@link #focusNextComponent(Component)}. */
    public void focusPreviousComponent(Component component) {
        traverse(component, -1);
    }

    /** Operates on the current focus owner; no-op when nothing is focused. */
    public final void focusNextComponent() {
        Component owner = getFocusOwner();
        if (owner != null) focusNextComponent(owner);
    }

    /** Operates on the current focus owner; no-op when nothing is focused. */
    public final void focusPreviousComponent() {
        Component owner = getFocusOwner();
        if (owner != null) focusPreviousComponent(owner);
    }

    /**
     * Walks the traversal ring from {@code from} in {@code direction} and
     * focuses the first eligible component that isn't {@code from} itself.
     */
    private void traverse(Component from, int direction) {
        if (from == null) return;
        Component root = from;
        while (root.getParent() != null) root = root.getParent();
        java.util.List<Component> ring = new java.util.ArrayList<>();
        // The walk's root is collected unconditionally, its descendants only
        // while visible: a top-level Window is hidden until setVisible(true),
        // and a ctor-time transferFocus (the auto-advance idiom's own timing)
        // runs inside exactly that window.
        ring.add(root);
        if (root instanceof Container rootContainer) {
            for (Component child : rootContainer.getComponents()) collect(child, ring);
        }
        // Identity, not indexOf: a user-code emulator subclass is free to
        // override equals, and two "equal" components are still two places in
        // the ring.
        int start = -1;
        for (int i = 0; i < ring.size(); i++) {
            if (ring.get(i) == from) { start = i; break; }
        }
        // `from` is absent when one of its ancestors is invisible — there is no
        // visible ring to move within, so stay put.
        if (start < 0 || ring.size() < 2) return;
        int size = ring.size();
        for (int step = 1; step < size; step++) {
            Component candidate = ring.get(((start + step * direction) % size + size) % size);
            if (candidate != from && isFocusTarget(candidate)) {
                candidate.requestFocus();
                return;
            }
        }
    }

    /**
     * Depth-first pre-order over the visible subtree, collecting every visible
     * component — focusable or not, so {@link #traverse} can still locate a
     * starting point that would not itself be picked as a target (a field the
     * user was typing in when it got disabled).
     */
    private static void collect(Component component, java.util.List<Component> out) {
        if (!component.isVisible()) return;   // an invisible container hides its subtree
        out.add(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, out);
        }
    }

    /**
     * Whether traversal may land on {@code component}: Swing's own
     * focusable-and-visible-and-enabled gate, plus the one thing Swing takes for
     * granted and we cannot — the peer must be able to take DOM focus.
     *
     * <p>{@link Component#isVisible()} rather than {@code isShowing()}:
     * the latter also requires the Vaadin attach chain, which excludes a
     * component being wired up before its frame is shown — exactly when a
     * ctor-time {@code transferFocus} runs.
     */
    private static boolean isFocusTarget(Component component) {
        return component.isFocusable()
                && component.isVisible()
                && component.isEnabled()
                && component.getPeer() instanceof com.vaadin.flow.component.Focusable<?>;
    }

    // --- Tier 3: WARN and move on (R_match_swing_errors sub-bucket (b)) ----------------
    //
    // Key-event dispatch, focus cycle roots, pluggable traversal policy, the
    // protected global mutators, and the vetoable-listener registry. The browser
    // owns key routing and Tab order; nothing here could observably work.
    //
    // KeyEventDispatcher / KeyEventPostProcessor are absent rather than
    // WARNing: both interfaces reference java.awt.Component, so D_whitelist_porting would drag
    // them into the port for methods that do nothing. Code naming them gets a
    // compile error that says so — same call as the two Default* classes above.

    public void upFocusCycle(Component component) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "upFocusCycle", component);
    }

    public void downFocusCycle(Container container) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "downFocusCycle", container);
    }

    public final void upFocusCycle() {
        upFocusCycle(getFocusOwner());
    }

    public final void downFocusCycle() {
        Component owner = getFocusOwner();
        downFocusCycle(owner instanceof Container c ? c : null);
    }

    public Container getCurrentFocusCycleRoot() {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "getCurrentFocusCycleRoot");
        return null;
    }

    public void setGlobalCurrentFocusCycleRoot(Container container) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setGlobalCurrentFocusCycleRoot", container);
    }

    protected Container getGlobalCurrentFocusCycleRoot() {
        return getCurrentFocusCycleRoot();
    }

    /**
     * The installed default policy, or {@code null} when none was set — never a
     * fabricated one, since there is no traversal engine whose behaviour a default
     * would describe.
     */
    public java.awt.FocusTraversalPolicy getDefaultFocusTraversalPolicy() {
        return defaultFocusTraversalPolicy;
    }

    /**
     * Stores the policy; nothing dispatches on it (D_focus_managers tier 3). Safe to hold on
     * this process-wide facade where per-UI state would not be — the JDK scopes
     * this default per AppContext, i.e. per application, and a policy is a
     * stateless strategy object rather than anything tab-bound.
     *
     * <p>Fires {@code "defaultFocusTraversalPolicy"} to the current UI's
     * listeners, which is narrower than the value it announces: the field is
     * process-wide, so another tab's listeners miss a change they can read back.
     * Per-UI is the widest delivery a tab-scoped registry can offer, and the
     * alternative — reaching into every session's listener list from one
     * request thread — is the cross-tab hazard this class exists to avoid.
     *
     * @throws IllegalArgumentException if {@code policy} is {@code null}, as the
     *     JDK does
     */
    public void setDefaultFocusTraversalPolicy(java.awt.FocusTraversalPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("default focus traversal policy cannot be null");
        }
        java.awt.FocusTraversalPolicy old = defaultFocusTraversalPolicy;
        defaultFocusTraversalPolicy = policy;
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setDefaultFocusTraversalPolicy(traversal)", policy);
        firePropertyChange("defaultFocusTraversalPolicy", old, policy);
    }

    /** @see #setDefaultFocusTraversalPolicy */
    private static java.awt.FocusTraversalPolicy defaultFocusTraversalPolicy;

    /**
     * Empty set, no WARN — the same honest answer
     * {@link Component#getFocusTraversalKeys} gives: the browser owns the Tab
     * key, so there are no server-side traversal keystrokes to report.
     */
    public java.util.Set<java.awt.AWTKeyStroke> getDefaultFocusTraversalKeys(int id) {
        return java.util.Collections.emptySet();
    }

    public void setDefaultFocusTraversalKeys(int id, java.util.Set<? extends java.awt.AWTKeyStroke> keystrokes) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setDefaultFocusTraversalKeys", id, keystrokes);
    }

    // Protected globals: the JDK's own accessors read the same values as the
    // public getters (the "global" prefix distinguishes AppContexts, which we
    // don't have), so the getters chain; the setters would have to rewrite
    // browser focus state from the server and WARN instead.

    protected Component getGlobalFocusOwner() {
        return getFocusOwner();
    }

    protected void setGlobalFocusOwner(Component component) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setGlobalFocusOwner", component);
    }

    protected Component getGlobalPermanentFocusOwner() {
        return getPermanentFocusOwner();
    }

    protected void setGlobalPermanentFocusOwner(Component component) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setGlobalPermanentFocusOwner", component);
    }

    protected Window getGlobalFocusedWindow() {
        return getFocusedWindow();
    }

    protected void setGlobalFocusedWindow(Window window) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setGlobalFocusedWindow", window);
    }

    protected Window getGlobalActiveWindow() {
        return getActiveWindow();
    }

    protected void setGlobalActiveWindow(Window window) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "setGlobalActiveWindow", window);
    }

    // Key plumbing. Declared (they reference only ported or reused types) so
    // migrated code compiles, WARNing so nobody believes a server-side key
    // pipeline exists.

    public boolean dispatchEvent(java.awt.AWTEvent event) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "dispatchEvent", event);
        return false;
    }

    public final void redispatchEvent(Component target, java.awt.AWTEvent event) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "redispatchEvent", target, event);
    }

    public boolean dispatchKeyEvent(vaadinx.awt.event.KeyEvent event) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "dispatchKeyEvent", event);
        return false;
    }

    public boolean postProcessKeyEvent(vaadinx.awt.event.KeyEvent event) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "postProcessKeyEvent", event);
        return false;
    }

    public void processKeyEvent(Component focusedComponent, vaadinx.awt.event.KeyEvent event) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "processKeyEvent", focusedComponent, event);
    }

    protected void enqueueKeyEvents(long after, Component untilFocused) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "enqueueKeyEvents", after, untilFocused);
    }

    protected void dequeueKeyEvents(long after, Component untilFocused) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "dequeueKeyEvents", after, untilFocused);
    }

    protected void discardKeyEvents(Component component) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "discardKeyEvents", component);
    }

    // --- Bound properties: a per-UI registry fed by FocusTracker -----
    //
    // The four focus properties are observable; currentFocusCycleRoot,
    // managingFocus and the traversal-key names stay silent because their
    // getters would deny the transition. See the class javadoc.

    /**
     * One UI's listeners, plus the manager they registered on so a user
     * subclass's {@link #firePropertyChange} override is the one that runs.
     *
     * <p>Not {@code com.vaadin.swingbridge.surrogates.internal.PceSupport}, which keys its
     * holder on a Vaadin component and would therefore make the <em>UI</em>
     * the event source; a listener's {@code getSource()} has to be the
     * manager it registered on. The {@code FocusTracker} subscription is
     * never torn down when the last listener leaves: it is one list entry per
     * UI, and the re-subscribe bookkeeping costs more than the entry.
     */
    private static final class Bound implements java.io.Serializable {
        final KeyboardFocusManager manager;
        final java.beans.PropertyChangeSupport support;

        Bound(KeyboardFocusManager manager) {
            this.manager = manager;
            this.support = new java.beans.PropertyChangeSupport(manager);
        }
    }

    /**
     * This UI's registry, created on the first registration and wired to
     * {@code FocusTracker} then; {@code null} when there is no current UI.
     */
    private Bound bind() {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (ui == null) return null;
        Bound bound = com.vaadin.flow.component.ComponentUtil.getData(ui, Bound.class);
        if (bound != null) return bound;
        bound = new Bound(this);
        com.vaadin.flow.component.ComponentUtil.setData(ui, Bound.class, bound);
        Bound target = bound;
        // R_callswing_envelope: the far end of this is migrated app code, which
        // may open a modal dialog from a focus change.
        com.vaadin.swingbridge.surrogates.FocusTracker.addFocusOwnerListener((oldOwner, newOwner) ->
                vaadinx.EHelper.callSwing(() -> target.manager.fireFocusTransition(oldOwner, newOwner)));
        return bound;
    }

    /** This UI's registry if one was ever created, else {@code null}. */
    private static Bound existing() {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        return ui == null ? null : com.vaadin.flow.component.ComponentUtil.getData(ui, Bound.class);
    }

    /**
     * Registers for every bound property of this manager, in the current UI:
     *
     * <pre>{@code
     * FocusManager.getCurrentManager().addPropertyChangeListener("focusOwner",
     *         e -> statusBar.setText("focus: " + e.getNewValue()));
     * }</pre>
     *
     * <p><b>Per UI, not process-wide.</b> The JDK scopes these listeners
     * per AppContext; a browser tab is the closest thing SB-Emulators has, so a
     * listener registered from one tab never hears another's focus changes —
     * the same call {@code FocusTracker} makes for the pointer itself. A
     * registration made with no current UI is dropped with a WARN, since
     * there is no tab to scope it to.
     */
    public void addPropertyChangeListener(java.beans.PropertyChangeListener listener) {
        Bound bound = bind();
        if (bound == null) {
            vaadinx.EHelper.onUnimplemented("KeyboardFocusManager",
                    "addPropertyChangeListener(no-current-UI)", listener);
            return;
        }
        bound.support.addPropertyChangeListener(listener);
    }

    /** @see #addPropertyChangeListener(java.beans.PropertyChangeListener) */
    public void addPropertyChangeListener(String propertyName, java.beans.PropertyChangeListener listener) {
        Bound bound = bind();
        if (bound == null) {
            vaadinx.EHelper.onUnimplemented("KeyboardFocusManager",
                    "addPropertyChangeListener(no-current-UI)", propertyName, listener);
            return;
        }
        bound.support.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(java.beans.PropertyChangeListener listener) {
        Bound bound = existing();
        if (bound != null) bound.support.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, java.beans.PropertyChangeListener listener) {
        Bound bound = existing();
        if (bound != null) bound.support.removePropertyChangeListener(propertyName, listener);
    }

    public java.beans.PropertyChangeListener[] getPropertyChangeListeners() {
        Bound bound = existing();
        return bound == null ? new java.beans.PropertyChangeListener[0]
                : bound.support.getPropertyChangeListeners();
    }

    public java.beans.PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
        Bound bound = existing();
        return bound == null ? new java.beans.PropertyChangeListener[0]
                : bound.support.getPropertyChangeListeners(propertyName);
    }

    /**
     * Delivers to the current UI's listeners, or nowhere when none registered
     * in this tab.
     *
     * <p>The {@code oldValue == newValue} short-circuit is the JDK's own,
     * and is <em>reference</em> identity — a stricter filter than the
     * {@code equals} dedupe {@code PropertyChangeSupport} then applies, and
     * the reason a hand-written {@code (null, null)} defeats both.
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (oldValue == newValue) return;
        Bound bound = existing();
        if (bound != null) bound.support.firePropertyChange(propertyName, oldValue, newValue);
    }

    /**
     * Turns one focus-pointer move into the JDK's four bound properties, in the
     * order {@code DefaultKeyboardFocusManager} produces on a focus gain:
     * window activation first, then the owner pair.
     *
     * <p><b>No {@code null} intermediate</b>, where the desktop's
     * FOCUS_LOST-then-FOCUS_GAINED pair fires {@code (old, null)} before
     * {@code (null, new)}. Synthesizing it would announce a transition
     * {@link #getFocusOwner()} denies — the pointer moves atomically here, so
     * a listener reading the owner back inside its own callback would see the
     * new one while holding an event that says {@code null}. That is the trap
     * {@code JPopupMenu.setVisible} declines for the same reason
     * (D_reverse_fanout_rows). The pair still arrives whenever the pointer
     * genuinely passes through {@code null} — a blur into empty space
     * followed by a click elsewhere.
     */
    private void fireFocusTransition(com.vaadin.flow.component.Component oldPeer,
                                     com.vaadin.flow.component.Component newPeer) {
        Component oldOwner = oldPeer == null ? null : vaadinx.EHelper.emulatorFor(oldPeer);
        Component newOwner = newPeer == null ? null : vaadinx.EHelper.emulatorFor(newPeer);
        Window oldWindow = windowOf(oldOwner);
        Window newWindow = windowOf(newOwner);
        firePropertyChange("activeWindow", oldWindow, newWindow);
        firePropertyChange("focusedWindow", oldWindow, newWindow);
        firePropertyChange("focusOwner", oldOwner, newOwner);
        firePropertyChange("permanentFocusOwner", oldOwner, newOwner);
    }

    /** The {@link Window} {@code owner} sits in, or {@code null}. */
    private static Window windowOf(Component owner) {
        if (owner == null) return null;
        if (owner instanceof Window self) return self;
        for (Container p = owner.getParent(); p != null; p = p.getParent()) {
            if (p instanceof Window w) return w;
        }
        return null;
    }

    public void addVetoableChangeListener(java.beans.VetoableChangeListener listener) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "addVetoableChangeListener", listener);
    }

    public void addVetoableChangeListener(String propertyName, java.beans.VetoableChangeListener listener) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "addVetoableChangeListener", propertyName, listener);
    }

    public void removeVetoableChangeListener(java.beans.VetoableChangeListener listener) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "removeVetoableChangeListener", listener);
    }

    public void removeVetoableChangeListener(String propertyName, java.beans.VetoableChangeListener listener) {
        vaadinx.EHelper.onUnimplemented("KeyboardFocusManager", "removeVetoableChangeListener", propertyName, listener);
    }

    public java.beans.VetoableChangeListener[] getVetoableChangeListeners() {
        return new java.beans.VetoableChangeListener[0];
    }

    public java.beans.VetoableChangeListener[] getVetoableChangeListeners(String propertyName) {
        return new java.beans.VetoableChangeListener[0];
    }

    protected void fireVetoableChange(String propertyName, Object oldValue, Object newValue)
            throws java.beans.PropertyVetoException {
        vaadinx.EHelper.onNoop("KeyboardFocusManager", "fireVetoableChange");
    }
}
