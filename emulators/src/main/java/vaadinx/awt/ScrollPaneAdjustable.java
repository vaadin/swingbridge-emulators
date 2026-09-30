/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.ScrollPaneAdjustable
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator. Ported rather than JDK-reused because
// java.awt.ScrollPaneAdjustable is `public final` with a package-private
// constructor: we can neither instantiate nor subclass it, and returning some
// other Adjustable would break the migrator whose code casts
// pane.getVAdjustable() to ScrollPaneAdjustable — a cast that compiles today.
// It references ScrollPane (a Component) in its ctor, so it sits inside the
// porting rule's spirit the way D_event_port_policy's Component-referencing event classes do.
// Rationale: D_awt_scrollpane.

/**
 * Emulator for {@link java.awt.ScrollPaneAdjustable} — the value model behind
 * one axis of a {@link vaadinx.awt.ScrollPane}. Emulator-only: it is not a
 * {@link vaadinx.awt.Component}, has no DOM of its own, and Vaadin ships no
 * scrollbar-object concept (the web component renders its scrollbars
 * internally). D_viewport_scrollbar_shadows's "shadow JDK component" pattern — except that, unlike
 * {@code JScrollBar}, this one is not inert: {@link #setValue} really does
 * scroll the pane.
 *
 * <pre>{@code
 * Adjustable v = pane.getVAdjustable();
 * v.addAdjustmentListener(e -> log(e.getValue()));
 * v.setValue(200);               // scrolls, and fires
 * v.setMaximum(500);             // AWTError, as in AWT
 * }</pre>
 *
 * <h2>The range is not yours to set</h2>
 *
 * {@link #setMinimum} / {@link #setMaximum} / {@link #setVisibleAmount} throw
 * {@link java.awt.AWTError}, verbatim as the JDK does — in AWT the span is
 * derived by {@code ScrollPane.layout()} from the child's preferred size
 * against the viewport, never set by user code. {@link #getMinimum} ignores
 * its own field and hard-returns 0, also as the JDK does.
 *
 * <h2>Programmatic scrolls fire; user scrolls do not</h2>
 *
 * {@code setValue} fires an {@link java.awt.event.AdjustmentEvent} on change,
 * synchronously and inline, exactly as the JDK's {@code setTypedValue} does
 * (the JDK comments that the synchronous delivery is deliberate, so listeners
 * are up to date before the Adjustable mutates again). A <em>user</em> scroll
 * fires nothing: the browser&#8594;server channel is descoped per D_awt_scrollpane, so no
 * gesture reaches the server. A migrator syncing two panes, or paging on
 * scroll, will find their listener silent — see the class javadoc of
 * {@link vaadinx.awt.ScrollPane}.
 *
 * <h2>Deliberate divergence: no clamping</h2>
 *
 * The JDK clamps to {@code [minimum, maximum - visibleAmount]}. Since the span
 * is only ever written by {@code setSpan} — which only {@code ScrollPane
 * .layout()} calls, and we never compute a pixel span (R_layouts_close_enough) — a faithful clamp
 * would be {@code min(max(v, 0), 0)}, i.e. <b>every setValue would collapse to
 * 0 and fire nothing, forever</b>, killing {@code setScrollPosition} outright.
 * So this port does not clamp: the value is stored verbatim, and the browser
 * clamps it on arrival (verified synchronous). One consequence, named so it is
 * not rediscovered as a bug: {@link #getValue} can exceed {@link #getMaximum}
 * (200 against 0), which the JDK's clamp makes impossible. A migrator
 * computing {@code value / (double) maximum} gets nonsense — but under a
 * faithful port they would have got {@code 0 / 0}, so no working idiom is lost.
 */
public final class ScrollPaneAdjustable implements java.awt.Adjustable, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    /** The pane this axis scrolls; the push target. */
    private final vaadinx.awt.ScrollPane sp;

    /** {@link java.awt.Adjustable#HORIZONTAL} or {@link java.awt.Adjustable#VERTICAL}. */
    private final int orientation;

    // JDK field defaults, verbatim: value/minimum/maximum/visibleAmount all 0,
    // both increments 1. maximum and visibleAmount are never written here —
    // setSpan is the only writer in the JDK and we never call it — so they are
    // effectively constants. Kept as fields rather than folded into the getters
    // so paramString reads the same shape the JDK's does.
    private int value;
    // No `minimum` field: the JDK has one but getMinimum() ignores it and
    // hard-returns 0 (its own comment says so), and setSpan — the only writer —
    // never runs here. A field nothing reads is dead code.
    private final int maximum = 0;
    private final int visibleAmount = 0;
    private int unitIncrement = 1;
    private int blockIncrement = 1;
    private boolean isAdjusting;

    /**
     * The multicast chain. Starts <b>null</b> and every fire site must guard —
     * the JDK's can never be null because its ctor adds an internal
     * {@code PeerFixer} (the listener that scrolls by moving the child to a
     * negative offset). We have no PeerFixer: {@link #push} replaces it.
     */
    private transient java.awt.event.AdjustmentListener adjustmentListener;

    ScrollPaneAdjustable(vaadinx.awt.ScrollPane sp, int orientation) {
        this.sp = sp;
        this.orientation = orientation;
    }

    public int getOrientation() {
        return orientation;
    }

    /**
     * @throws java.awt.AWTError always — the span belongs to the scroll pane
     */
    public void setMinimum(int min) {
        throw new java.awt.AWTError("Can be set by scrollpane only");
    }

    /** @return always 0; the JDK ignores its own field here for the same reason */
    public int getMinimum() {
        return 0;
    }

    /**
     * @throws java.awt.AWTError always — the span belongs to the scroll pane
     */
    public void setMaximum(int max) {
        throw new java.awt.AWTError("Can be set by scrollpane only");
    }

    /**
     * @return always 0 — the JDK's field default, only ever overwritten by
     *         {@code setSpan}, which needs a laid-out pane we never produce.
     *         Not 1: that is {@code setSpan}'s floor, reachable only after a
     *         layout, so 0 is what an un-laid-out JDK pane actually answers
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * @throws java.awt.AWTError always — the span belongs to the scroll pane
     */
    public void setVisibleAmount(int v) {
        throw new java.awt.AWTError("Can be set by scrollpane only");
    }

    /** @return always 0, for the same reason as {@link #getMaximum} */
    public int getVisibleAmount() {
        return visibleAmount;
    }

    /**
     * Stores the arrow-key delta. Inert: the browser owns wheel and arrow-key
     * scroll deltas and exposes no hook to override them (R_match_swing_errors sub-bucket (c)).
     * The value round-trips so a migrator's own arithmetic over it still works.
     */
    public synchronized void setUnitIncrement(int u) {
        unitIncrement = u;
    }

    public synchronized int getUnitIncrement() {
        return unitIncrement;
    }

    /**
     * Stores the page delta. Inert, as {@link #setUnitIncrement} is.
     *
     * <p>Unlike the JDK, the stored value survives: AWT's {@code setSpan}
     * silently overwrites {@code blockIncrement} with {@code 0.9 * visible}
     * on every layout, and {@code setSpan} never runs here.
     */
    public synchronized void setBlockIncrement(int b) {
        blockIncrement = b;
    }

    public synchronized int getBlockIncrement() {
        return blockIncrement;
    }

    /**
     * Fires an {@link java.awt.event.AdjustmentEvent} on a genuine flip, as
     * the JDK does. Only user code can flip it — the browser gives no
     * scroll-drag start/end signal, and we are not listening for one anyway.
     */
    public void setValueIsAdjusting(boolean b) {
        if (isAdjusting == b) return;
        isAdjusting = b;
        fire(java.awt.event.AdjustmentEvent.TRACK);
    }

    public boolean getValueIsAdjusting() {
        return isAdjusting;
    }

    /**
     * Scrolls this axis to {@code v} and pushes to the browser.
     *
     * <p>No clamping — see the class javadoc. Fires only on a real change,
     * matching the JDK, and fires <i>before</i> nothing: the JDK's
     * {@code setTypedValue} notifies synchronously, and so do we.
     */
    public void setValue(int v) {
        if (v == value) return;
        value = v;
        fire(java.awt.event.AdjustmentEvent.TRACK);
        push();
    }

    public int getValue() {
        return value;
    }

    /**
     * Writes this axis's value through to the peer.
     *
     * <p>Both axes ride one {@code executeJs}, so the sibling's current value
     * goes along unchanged rather than being clobbered to 0.
     */
    private void push() {
        sp.pushScrollPosition();
    }

    private void fire(int type) {
        java.awt.event.AdjustmentListener l = adjustmentListener;
        // Null-guard: unlike the JDK's, our chain starts empty (no PeerFixer).
        if (l == null) return;
        // Fired inline, NOT through EHelper.callSwing: every event here
        // originates in user code calling a setter, so we are already on a
        // Swing-side thread inside whatever envelope brought us here. R_callswing_envelope is the
        // peer->Swing envelope and no peer event is involved; wrapping would
        // additionally throw on a legitimate setValue made off a UI thread.
        l.adjustmentValueChanged(new java.awt.event.AdjustmentEvent(
                this,
                java.awt.event.AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED,
                type,
                value,
                isAdjusting));
    }

    /** Null listener is a documented no-op, as in AWT. */
    public synchronized void addAdjustmentListener(java.awt.event.AdjustmentListener l) {
        if (l == null) return;
        adjustmentListener = java.awt.AWTEventMulticaster.add(adjustmentListener, l);
    }

    /** Null listener is a documented no-op, as in AWT. */
    public synchronized void removeAdjustmentListener(java.awt.event.AdjustmentListener l) {
        if (l == null) return;
        adjustmentListener = java.awt.AWTEventMulticaster.remove(adjustmentListener, l);
    }

    public synchronized java.awt.event.AdjustmentListener[] getAdjustmentListeners() {
        return java.awt.AWTEventMulticaster.getListeners(
                adjustmentListener, java.awt.event.AdjustmentListener.class);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[" + paramString() + "]";
    }

    /** Verbatim JDK format. */
    public String paramString() {
        return ((orientation == java.awt.Adjustable.VERTICAL ? "vertical," : "horizontal,")
                + "[0.." + maximum + "]"
                + ",val=" + value
                + ",vis=" + visibleAmount
                + ",unit=" + unitIncrement
                + ",block=" + blockIncrement
                + ",isAdjusting=" + isAdjusting);
    }
}
