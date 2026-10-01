/*
 * Copyright (c) 1995, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Scrollbar
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — the AWT lane's first value-model widget, after
// Button, Label, Choice, Checkbox and Panel. Two things make it unlike those:
// the JDK's own call graph runs *backwards* here (the deprecated AWT-1.0
// names are the real implementations, the modern ones are the aliases), and
// the AdjustmentEvent source is a typed interface the surrogate cannot
// implement, so the listener fan-out lives here rather than on the peer.
//
// The scrollbar owns its state, as the JDK's does: the value model, the
// orientation, the increments and isAdjusting are the JDK's fields under its
// names, the mutators are its bodies, and no getter reads the peer
// (D_emulator_owned_state). Every write flushes the current
// fields on to the SScrollbar, which clamps again — idempotently, since the
// values arrive clamped.
//
// Browser -> AWT: a user's drag runs setValueIsAdjusting + setValue and then
// posts, and the drag end runs setValueIsAdjusting(false) and posts, as the
// JDK's Windows peer does (WScrollbarPeer.postAdjustmentEvent / dragEnd).
// Rationale: D_awt_scrollbar / SD_sscrollbar; family sizing: ideas/awt-widgets.md.

/**
 * Emulator for {@link java.awt.Scrollbar} — the AWT 1.0 scrollbar, not
 * {@link vaadinx.swing.JScrollBar}. Holds the JDK's value model itself, so
 * every getter answers on any thread without reaching the
 * {@link com.vaadin.swingbridge.surrogates.SScrollbar} peer, which renders it.
 * The fan-out lives here too: the peer cannot hold the
 * {@link java.awt.Adjustable} identity that
 * {@link java.awt.event.AdjustmentEvent} demands of its source.
 *
 * <pre>{@code
 * Scrollbar zoom = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 255);
 * zoom.addAdjustmentListener(e -> setZoom(e.getValue()));
 * zoom.setValue(128);            // silent: no AdjustmentEvent, as in AWT
 * }</pre>
 *
 * <h2>It is a value control, and it renders as a slider</h2>
 *
 * AWT's scroll pane does <em>not</em> use this class — {@code ScrollPane}
 * hands out {@link java.awt.ScrollPaneAdjustable}, a different final class —
 * so a {@code Scrollbar} exists only because migrated code constructed one.
 * AWT 1.0 had no slider, so that code is overwhelmingly using it as a zoom /
 * volume / colour knob, which is why the peer is a range slider rather than
 * the inert {@code Div} {@code vaadinx.swing.JScrollBar} peers on (D_viewport_scrollbar_shadows's
 * "it is chrome the scroll container renders for us" is true in Swing and
 * false here). The visual cost — no end arrows, and a thumb whose length
 * cannot carry {@code visibleAmount} — is R_match_swing_errors sub-bucket (c); see
 * {@link com.vaadin.swingbridge.surrogates.SScrollbar}.
 *
 * <h2>Only the browser posts events</h2>
 *
 * {@code setValue} and {@code setValues} fire nothing: their javadoc says so
 * outright, and the JDK source fires only an accessibility PCE. That inverts
 * {@link vaadinx.swing.JScrollBar}, whose {@code BoundedRangeModel} fires on
 * every write, and it means no {@code preventPeerEvents} flag is needed here —
 * a server-side push is precisely the case AWT stays silent for, so the
 * bridge gates on {@code isFromClient()} instead (the {@code SCheckbox}
 * precedent).
 *
 * <p>A browser gesture reaches the model through the public methods, as a
 * platform peer's does: a drag step calls {@link #setValueIsAdjusting} and
 * {@link #setValue} before the event posts, so a migrator's {@code setValue}
 * override sees the user's traffic too. Every browser-sourced event reports
 * {@link java.awt.event.AdjustmentEvent#TRACK}: nothing distinguishes an
 * end-arrow click from a track click from a keypress server-side, and a wrong
 * adjustment type is worse than a consistently honest one.
 *
 * <h2>Leaf lock-down (R_leaf_peer_lockdown)</h2>
 *
 * Nothing in {@code java.awt} extends {@code Scrollbar}
 * ({@code javax.swing.JScrollBar} descends from {@code JComponent};
 * {@code ScrollPaneAdjustable} is {@code final} and merely implements
 * {@code Adjustable}), so there is no protected {@code (Component peer)} ctor
 * and every instance peers over an {@code SScrollbar}. Both orientations ride
 * one peer type, so no D_frame_strategy-style peer dispatch is needed either.
 */
public class Scrollbar extends vaadinx.awt.Component
        implements java.awt.Adjustable, javax.accessibility.Accessible {

    /** 0 — the same value as {@link java.awt.Adjustable#HORIZONTAL}. */
    public static final int HORIZONTAL = java.awt.Adjustable.HORIZONTAL;

    /** 1 — the same value as {@link java.awt.Adjustable#VERTICAL}. */
    public static final int VERTICAL = java.awt.Adjustable.VERTICAL;

    // The JDK's fields, under its names. setValues is the only writer of the
    // four value fields bar setMaximum's pre-step, which writes minimum itself.
    private int value;
    private int maximum;
    private int minimum;
    private int visibleAmount;
    private int orientation;
    private int lineIncrement = 1;
    private int pageIncrement = 10;
    private boolean isAdjusting;

    /** A vertical scrollbar, value 0, visibleAmount 10, band 0–100. */
    public Scrollbar() throws java.awt.HeadlessException {
        this(VERTICAL, 0, 10, 0, 100);
    }

    public Scrollbar(int orientation) throws java.awt.HeadlessException {
        this(orientation, 0, 10, 0, 100);
    }

    /**
     * Seeds the value model through {@link #setValues}, as the JDK
     * does, so a subclass's override runs from the constructor.
     *
     * @param orientation {@link #HORIZONTAL} or {@link #VERTICAL};
     *        {@code Adjustable.NO_ORIENTATION} is <em>not</em> legal, even
     *        though it is a constant on the interface this class implements
     * @throws IllegalArgumentException on any other value — out of
     *         {@code super(...)}, so no half-built {@code Scrollbar} escapes
     * @throws java.awt.HeadlessException never in practice; the clause is kept
     *         for signature fidelity, matching {@link vaadinx.awt.Frame}
     */
    public Scrollbar(int orientation, int value, int visible, int minimum, int maximum)
            throws java.awt.HeadlessException {
        // R_leaf_peer_lockdown lock-down: super(...) takes the SScrollbar directly, no peer seam.
        super(com.vaadin.swingbridge.surrogates.SScrollbar.class, () -> new com.vaadin.swingbridge.surrogates.SScrollbar(orientation));
        // Checked here, not left to the peer's ctor, which a lazy peer runs later.
        switch (orientation) {
            case HORIZONTAL, VERTICAL -> { }
            default -> throw new IllegalArgumentException("illegal scrollbar orientation");
        }
        this.orientation = orientation;
        setValues(value, visible, minimum, maximum);
        installPeerBridge();
    }

    private void installPeerBridge() {
        // Registered once the peer exists, which for a lazy peer is when a UI is current.
        withPeer(peer -> {
            // Peer → AWT event pipeline, with the source re-bound to `this` so
            // migrated code casting `(Scrollbar) e.getAdjustable()` sees the
            // emulator. The surrogate cannot build this event itself:
            // AdjustmentEvent's source parameter is typed java.awt.Adjustable, and
            // SScrollbar cannot implement Adjustable (int getValue() and
            // int getOrientation() clash irreconcilably with Vaadin's Double /
            // Orientation returns). SD_sscrollbar.
            //
            // Enter at processEvent, not processAdjustmentEvent: AWT routes a
            // peer-posted event dispatchEvent → processEvent → processAdjustmentEvent,
            // so entering at the second hop would leave a migrator's processEvent
            // override compiling, looking wired, and never running (R_no_vaadin_in_api limb 2,
            // and D_awt_dead_hooks's finding on the click bridge).
            surrogate().addValueChangeListener(e -> {
                // AWT posts nothing for a programmatic write, which is exactly the
                // !isFromClient case — so there is no echo to suppress and no
                // preventPeerEvents flag to carry.
                if (!e.isFromClient()) return;
                // The browser's report, read here on the request thread. The
                // surrogate's own listener ran first (it registered in its ctor),
                // so its flag already says the gesture is in flight.
                int v = surrogate().getIntValue();
                boolean adjusting = surrogate().getValueIsAdjusting();
                // R_callswing_envelope: the browser → AWT seam funnels through callSwing so a
                // listener that opens a modal dialog can park on the loom virtual
                // thread. Nested callSwing runs inline (D_callswing_loom).
                vaadinx.EHelper.callSwing(() -> {
                    // WScrollbarPeer.postAdjustmentEvent's order.
                    setValueIsAdjusting(adjusting);
                    setValue(v);
                    postAdjustment(v, adjusting);
                });
            });
            // The drag-end event. Without this the "commit when getValueIsAdjusting()
            // goes false" idiom — the whole point of the property — would never
            // fire: the browser's `change` carries no new value, so it produces no
            // Vaadin value-change event for the listener above to ride.
            getPeer().getElement().addEventListener("change", e -> {
                int v = surrogate().getIntValue();
                vaadinx.EHelper.callSwing(() -> {
                    // WScrollbarPeer.dragEnd: no setValue, the last drag step wrote it.
                    setValueIsAdjusting(false);
                    postAdjustment(v, false);
                });
            });
        });
    }

    /** @param v the browser's value, as a platform peer posts it — not {@link #getValue()} */
    private void postAdjustment(int v, boolean adjusting) {
        processEvent(new java.awt.event.AdjustmentEvent(
                this,
                java.awt.event.AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                // TRACK unconditionally — the browser reports a new value and
                // nothing about the gesture that produced it (R_match_swing_errors(c)).
                java.awt.event.AdjustmentEvent.TRACK,
                v,
                adjusting));
    }

    private com.vaadin.swingbridge.surrogates.SScrollbar surrogate() {
        return (com.vaadin.swingbridge.surrogates.SScrollbar) getPeer();
    }

    @Override
    public void addNotify() {
        // JDK creates the native peer here. Ours is eternal and created in the
        // ctor, so there is nothing to allocate — but the override is kept
        // rather than dropped: user code overriding addNotify() and calling
        // super.addNotify() is a common AWT idiom, and Component's
        // implementation is what logs the displayable transition.
        super.addNotify();
    }

    // --- orientation ---------------------------------------------------

    @Override
    public int getOrientation() {
        // R_no_vaadin_in_api limb 1: int, never RangeInput.Orientation — the Vaadin enum
        // stays behind surrogate().
        return orientation;
    }

    /**
     * @param orientation an unchanged value returns early <em>before</em> the
     *        legality check, as in AWT, so an illegal int equal to the current
     *        orientation cannot be reached
     * @throws IllegalArgumentException {@code "illegal scrollbar orientation"}
     */
    public void setOrientation(int orientation) {
        synchronized (getTreeLock()) {
            if (orientation == this.orientation) {
                return;
            }
            switch (orientation) {
                case HORIZONTAL:
                case VERTICAL:
                    this.orientation = orientation;
                    break;
                default:
                    throw new IllegalArgumentException("illegal scrollbar orientation");
            }
        }
        // The JDK recreates its peer here; ours flips in place. Pushed outside
        // the tree lock, since a push off the UI thread takes the session lock.
        withPeer(p -> surrogate().setAwtOrientation(this.orientation));
    }

    // --- the value model ------------------------------------------------

    @Override
    public int getValue() {
        return value;
    }

    /**
     * Routes through {@link #setValues} rather than writing the peer
     * directly — {@code setValues} is the JDK's single funnel and the
     * natural override point for a migrator tightening clamping, so a
     * direct write here would leave that override never running
     * (R_no_vaadin_in_api limb 2).
     *
     * @param value clamped into {@code [minimum, maximum - visibleAmount]};
     *        fires no {@link java.awt.event.AdjustmentEvent}
     */
    @Override
    public void setValue(int value) {
        // The JDK passes its fields, not its getters: an overridden
        // getMinimum() is not on this path.
        setValues(value, visibleAmount, minimum, maximum);
    }

    @Override
    public int getMinimum() {
        return minimum;
    }

    /** @param minimum see {@link #setValue} on the {@code setValues} funnel */
    @Override
    public void setMinimum(int minimum) {
        setValues(value, visibleAmount, minimum, maximum);
    }

    @Override
    public int getMaximum() {
        return maximum;
    }

    /**
     * The rewritten minimum is stored straight into the field, as
     * the JDK does, so it holds even when an overriding
     * {@link #setValues} does not call super.
     *
     * @param maximum {@link Integer#MIN_VALUE} is nudged up by one, and a
     *        {@code minimum} that would meet or pass the new maximum is
     *        rewritten <em>before</em> the funnel runs — both are AWT's own
     *        pre-steps, outside {@code setValues}
     */
    @Override
    public void setMaximum(int maximum) {
        if (maximum == Integer.MIN_VALUE) {
            maximum = Integer.MIN_VALUE + 1;
        }
        if (minimum >= maximum) {
            minimum = maximum - 1;
        }
        setValues(value, visibleAmount, minimum, maximum);
    }

    @Override
    public int getVisibleAmount() {
        // The JDK implements this as a call to the deprecated getVisible(),
        // not the other way round (R_no_vaadin_in_api limb 2).
        return getVisible();
    }

    /** @param visible see {@link #setValue} on the {@code setValues} funnel */
    public void setVisibleAmount(int visible) {
        setValues(value, visible, minimum, maximum);
    }

    /**
     * The single clamping funnel every value mutator routes through — and the
     * override point AWT subclasses use to tighten clamping, so it must stay
     * the only path to the peer.
     *
     * <p>Never throws: every int, {@link Integer#MIN_VALUE} and
     * {@link Integer#MAX_VALUE} included, is clamped. Fires no
     * {@link java.awt.event.AdjustmentEvent}.
     */
    public void setValues(int value, int visible, int minimum, int maximum) {
        synchronized (this) {
            if (minimum == Integer.MAX_VALUE) {
                minimum = Integer.MAX_VALUE - 1;
            }
            if (maximum <= minimum) {
                maximum = minimum + 1;
            }
            long maxMinusMin = (long) maximum - (long) minimum;
            if (maxMinusMin > Integer.MAX_VALUE) {
                maxMinusMin = Integer.MAX_VALUE;
                maximum = minimum + (int) maxMinusMin;
            }
            if (visible > (int) maxMinusMin) {
                visible = (int) maxMinusMin;
            }
            if (visible < 1) {
                visible = 1;
            }
            if (value < minimum) {
                value = minimum;
            }
            if (value > maximum - visible) {
                value = maximum - visible;
            }
            this.value = value;
            this.visibleAmount = visible;
            this.minimum = minimum;
            this.maximum = maximum;
        }
        pushValues();
    }

    /**
     * Flushes the value model on to the peer. The JDK pushes inside its
     * monitor; this reads the fields inside the push instead, so the monitor
     * is never held while a push off the UI thread takes the session lock,
     * and two racing writers still leave the peer on the last state.
     */
    private void pushValues() {
        withPeer(p -> {
            int v, vis, min, max;
            synchronized (this) {
                v = value;
                vis = visibleAmount;
                min = minimum;
                max = maximum;
            }
            surrogate().setValues(v, vis, min, max);
        });
    }

    // --- increments -----------------------------------------------------

    @Override
    public int getUnitIncrement() {
        // JDK: getUnitIncrement() calls getLineIncrement(). Backwards from
        // every other deprecated pair in the tree, and load-bearing — see the
        // deprecated block below.
        return getLineIncrement();
    }

    /**
     * @param v clamped up to 1, as AWT does. Stored but <em>inert</em>: the
     *        only browser lever is the peer's {@code step}, which would
     *        quantize the whole value space rather than just the arrow delta
     *        (R_match_swing_errors sub-bucket (c); rationale in {@link com.vaadin.swingbridge.surrogates.SScrollbar})
     */
    public void setUnitIncrement(int v) {
        setLineIncrement(v);
    }

    @Override
    public int getBlockIncrement() {
        return getPageIncrement();
    }

    /**
     * @param v clamped up to 1, as AWT does. Stored but inert — PageUp /
     *        PageDown on {@code <input type=range>} move by a browser-chosen
     *        amount that is not settable (R_match_swing_errors sub-bucket (c))
     */
    public void setBlockIncrement(int v) {
        setPageIncrement(v);
    }

    // --- the deprecated AWT-1.0 names, which are the real implementations --
    //
    // R_no_vaadin_in_api limb 2, and the sharpest instance of it in the AWT lane: in the JDK
    // *these* carry the behaviour and the modern names delegate to them, not
    // the reverse. Implementing it the other way round leaves a migrator's
    // setLineIncrement override compiling, looking wired, and never running.

    /** @deprecated as of JDK 1.1, replaced by {@link #getVisibleAmount} */
    @Deprecated
    public int getVisible() {
        return visibleAmount;
    }

    // The JDK declares both setters synchronized and pushes inside the
    // monitor; these push after it, for pushValues' reason.

    /** @deprecated as of JDK 1.1, replaced by {@link #setUnitIncrement} */
    @Deprecated
    public void setLineIncrement(int v) {
        int tmp = (v < 1) ? 1 : v;
        synchronized (this) {
            if (lineIncrement == tmp) {
                return;
            }
            lineIncrement = tmp;
        }
        withPeer(p -> surrogate().setUnitIncrement(lineIncrement));
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getUnitIncrement} */
    @Deprecated
    public int getLineIncrement() {
        return lineIncrement;
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #setBlockIncrement} */
    @Deprecated
    public void setPageIncrement(int v) {
        int tmp = (v < 1) ? 1 : v;
        synchronized (this) {
            if (pageIncrement == tmp) {
                return;
            }
            pageIncrement = tmp;
        }
        withPeer(p -> surrogate().setBlockIncrement(pageIncrement));
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getBlockIncrement} */
    @Deprecated
    public int getPageIncrement() {
        return pageIncrement;
    }

    // --- valueIsAdjusting -----------------------------------------------

    /** @return true while a browser drag is in flight, or after a {@code setValueIsAdjusting(true)} */
    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    /**
     * @param b advisory on the peer — the browser overwrites its flag on the
     *        next gesture (R_match_swing_errors sub-bucket (c)), and the drag
     *        writes this one back through here
     */
    public void setValueIsAdjusting(boolean b) {
        synchronized (this) {
            isAdjusting = b;
        }
        withPeer(p -> surrogate().setValueIsAdjusting(isAdjusting));
    }

    // --- AdjustmentListener ----------------------------------------------

    /** @param l null is a documented no-op, not an NPE */
    public synchronized void addAdjustmentListener(java.awt.event.AdjustmentListener l) {
        // EventListenerList.add would happily store a null and NPE at dispatch.
        if (l == null) return;
        listenerList.add(java.awt.event.AdjustmentListener.class, l);
    }

    /** @param l null is a documented no-op */
    public synchronized void removeAdjustmentListener(java.awt.event.AdjustmentListener l) {
        if (l == null) return;
        listenerList.remove(java.awt.event.AdjustmentListener.class, l);
    }

    public synchronized java.awt.event.AdjustmentListener[] getAdjustmentListeners() {
        // JDK routes this through getListeners(AdjustmentListener.class), whose
        // own implementation special-cases AdjustmentListener before delegating
        // to Component's. No getListeners override is needed here: our
        // Component.getListeners is already type-keyed over the shared
        // listenerList, so the JDK's special case is that method's general case
        // (the same call D_awt_button made for ActionListener).
        return getListeners(java.awt.event.AdjustmentListener.class);
    }

    @Override
    protected void processEvent(java.awt.AWTEvent e) {
        // JDK's Scrollbar.processEvent peels AdjustmentEvent off before
        // delegating the rest to Component.processEvent.
        if (e instanceof java.awt.event.AdjustmentEvent ae) {
            processAdjustmentEvent(ae);
            return;
        }
        super.processEvent(e);
    }

    /**
     * The single funnel every adjustment reaches the listeners through — both
     * the peer bridge and a user-code {@code processEvent} call. AWT
     * subclasses override this (calling super) to intercept adjustments
     * wholesale, so it must stay the only dispatch path.
     *
     * @param e null is a no-op; the JDK calls the behaviour "unspecified"
     *        there, and D_awt_button settled that as no-op rather than NPE
     */
    protected void processAdjustmentEvent(java.awt.event.AdjustmentEvent e) {
        if (e == null) return;
        for (java.awt.event.AdjustmentListener l
                : awtListeners(java.awt.event.AdjustmentListener.class)) {
            l.adjustmentValueChanged(e);
        }
    }

    @Override
    protected java.lang.String paramString() {
        // JDK's tail; note ",vert" / ",horz" carry no "=".
        return super.paramString()
                + ",val=" + value
                + ",vis=" + visibleAmount
                + ",min=" + minimum
                + ",max=" + maximum
                + (orientation == VERTICAL ? ",vert" : ",horz")
                + ",isAdjusting=" + isAdjusting;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Scrollbar", "getAccessibleContext");
        return null;
    }
}
