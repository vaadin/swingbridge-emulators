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
 * This file is derived from OpenJDK's javax.swing.JViewport
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.html.Div;

/**
 * Emulator for {@link javax.swing.JViewport}. Per
 * <a href="../../../emulators/decisions.md#D_viewport_scrollbar_shadows">D_viewport_scrollbar_shadows</a>,
 * a <strong>shadow JDK component</strong>: emulator-only, no surrogate
 * counterpart. Vaadin's {@link com.vaadin.flow.component.orderedlayout.Scroller}
 * has no separate viewport object — {@code Scroller.getContent()} IS the
 * viewport view, and the DOM rendering of "viewport bounds" is owned by
 * the Scroller's web component. JViewport here exists to give migrated
 * code that calls {@code scrollPane.getViewport().getView()} or
 * {@code scrollPane.getViewport().addChangeListener(...)} a non-null
 * object that round-trips its own state without NPE.
 *
 * <p>R_leaf_peer_lockdown-locked-down: no protected {@code (Component peer)} ctor exposed.
 * Constructed only via the package-private {@link #JViewport(JScrollPane)}
 * ctor used by {@link JScrollPane#getViewport}.
 *
 * <h2>Round-trip-only fields</h2>
 *
 * Most viewport state ({@code extentSize} / {@code viewPosition} /
 * {@code viewSize}) field-shadows but never drives the peer — Vaadin
 * Scroller doesn't expose these as server-side concepts. Field round-trip
 * is honest about JDK fidelity (user code reading back what it set sees
 * its values); visible behaviour is "no scroll-position events fire."
 *
 * <h2>Listeners stored but never fire</h2>
 *
 * {@link #addChangeListener} stores the listener in
 * {@link javax.swing.event.EventListenerList} for round-trip; no Vaadin
 * source fires {@link javax.swing.event.ChangeEvent}s for scroll position
 * without a custom DOM listener stack (R_match_swing_errors sub-bucket (a) — graduate when
 * a migration target asks).
 */
public class JViewport extends vaadinx.swing.JComponent implements javax.accessibility.Accessible {

    /**
     * Owning JScrollPane (back-reference for {@link #getView} /
     * {@link #setView} round-trip through the JScrollPane's
     * {@link JScrollPane#setViewportView}). Set at ctor time, may be
     * re-bound via {@link #bindOwner} when JDK code calls
     * {@code scrollPane.setViewport(otherViewport)}.
     */
    private JScrollPane owner;

    // Round-trip-only field shadows — no Vaadin counterpart per D_viewport_scrollbar_shadows.
    private java.awt.Dimension extentSize;
    private java.awt.Point viewPosition = new java.awt.Point(0, 0);
    private java.awt.Dimension viewSize;
    private int scrollMode = BLIT_SCROLL_MODE;

    // JDK's three scrollMode constants — replicated verbatim so user code
    // that branches on them rounds-trips.
    public static final int BLIT_SCROLL_MODE = 1;
    public static final int BACKINGSTORE_SCROLL_MODE = 2;
    public static final int SIMPLE_SCROLL_MODE = 0;

    /**
     * Construct a JViewport bound to the given owning JScrollPane.
     * Package-private — user code reaches JViewport via
     * {@link JScrollPane#getViewport}, never directly.
     */
    JViewport(JScrollPane owner) {
        // Inert Div peer — JViewport doesn't render to the DOM, but every
        // Component-hierarchy emulator needs a peer for the
        // EHelper.onCreated registry / Container parent chain to work.
        super(Div.class, Div::new);
        this.owner = owner;
    }

    /**
     * Public no-arg ctor for migrated code that constructs a JViewport
     * directly (rare but possible; some L&amp;F-aware code does
     * {@code scrollPane.setViewport(new JViewport())}). Owner is unbound
     * until the JViewport is attached to a JScrollPane via
     * {@code setViewport}, at which point {@link #bindOwner} is called.
     */
    public JViewport() {
        super(Div.class, Div::new);
        this.owner = null;
    }

    /** Package-private rebind hook used by {@link JScrollPane#setViewport}. */
    void bindOwner(JScrollPane owner) {
        this.owner = owner;
    }

    /**
     * Package-private hook for {@link JScrollPane#setViewportView} to insert
     * the view as a child of this JViewport via {@link vaadinx.awt.Container}'s
     * slot-child seam. Needed because {@code Container.addSlotChild} is
     * {@code protected} in {@code vaadinx.awt} and {@code JScrollPane} can't
     * call it on a sibling-subclass reference. R_swing_is_truth tree shape: JScrollPane
     * → JViewport → view, matching JDK.
     */
    void attachView(vaadinx.awt.Component view) {
        addSlotChild(view, -1);
    }

    /** Mirror of {@link #attachView} for view detachment. */
    void detachView(vaadinx.awt.Component view) {
        removeSlotChild(view);
    }

    // --- View accessor (the canonical "what's in the viewport") ---------

    /**
     * The wrapped emulator component, resolved through the owning
     * {@link JScrollPane}'s {@code getViewportView}. Returns {@code null}
     * if no owner is bound or no view is set.
     */
    public vaadinx.awt.Component getView() {
        if (owner != null) {
            return owner.getViewportView();
        }
        return getComponentCount() > 0 ? getComponent(0) : null;
    }

    /**
     * Replace the wrapped component — through the owning {@link JScrollPane}'s
     * {@link JScrollPane#setViewportView} when one is bound, and otherwise into
     * this viewport's own child list, which is where the JDK keeps it either
     * way ({@code getView()} is its {@code getComponent(0)}).
     *
     * <p>An unbound viewport therefore still holds what it was given, scrolling
     * being the effect R_decline_effect_only lets go and the view being state
     * it does not.
     */
    public void setView(vaadinx.awt.Component view) {
        if (owner != null) {
            owner.setViewportView(view);
            return;
        }
        for (int i = getComponentCount() - 1; i >= 0; i--) {
            detachView(getComponent(i));
        }
        if (view != null) {
            attachView(view);
        }
    }

    // --- Round-trip-only viewport state ---------------------------------

    public java.awt.Dimension getExtentSize() {
        if (extentSize != null) return new java.awt.Dimension(extentSize);
        // JDK default: derive from view's preferred size if no explicit
        // extent set. We don't have pixel sizes — return zero rather than
        // throwing.
        return new java.awt.Dimension(0, 0);
    }

    public void setExtentSize(java.awt.Dimension newExtent) {
        // No PropertyChangeEvent: JViewport.setExtentSize resizes and calls
        // fireStateChanged(). "extentSize" is not a bound property (D_property_fanout_audit); the
        // ChangeListener list is the notification, and it stays silent here
        // for the reason addChangeListener documents.
        this.extentSize = newExtent == null ? null : new java.awt.Dimension(newExtent);
    }

    /** The origin while no view is set, as in the JDK — there is nothing to ask. */
    public java.awt.Point getViewPosition() {
        if (getView() == null) return new java.awt.Point(0, 0);
        return new java.awt.Point(viewPosition);
    }

    /**
     * Store the scroll offset, or ignore it while the viewport holds no view.
     *
     * <p><b>The no-view gate is the JDK's, and it is not a nicety.</b>
     * {@code javax.swing.JViewport} keeps <em>no</em> position field: it
     * writes the offset onto the view's own location, negated, and
     * {@code getViewPosition()} reads it back off the view — so with no
     * view the write is dropped and the getter answers {@code (0,0)}.
     * SB-Emulators answered back whatever was set, on a viewport that
     * held nothing (D_return_value_audit). The field stays because
     * {@code vaadinx.awt.Component} drops {@code setLocation}
     * (R_layouts_close_enough), so the view cannot hold the value for us;
     * with a view present the observable round trip is the same either
     * way. A {@code null} point NPEs here exactly where the JDK's
     * {@code p.x} read does.
     */
    public void setViewPosition(java.awt.Point p) {
        // R_layouts_close_enough — no server-side scroll coordinates; field-shadow + WARN-skip
        // the peer write. User code reads back what it set; no actual scroll
        // happens (the browser owns scroll position).
        if (getView() == null) return;
        this.viewPosition = new java.awt.Point(p.x, p.y);
    }

    /** As {@link #getViewPosition}, the origin while no view is set. */
    public java.awt.Dimension getViewSize() {
        if (getView() == null) return new java.awt.Dimension(0, 0);
        if (viewSize != null) return new java.awt.Dimension(viewSize);
        return new java.awt.Dimension(0, 0);
    }

    /** Same no-view gate and same reason as {@link #setViewPosition}. */
    public void setViewSize(java.awt.Dimension newSize) {
        if (getView() == null) return;
        this.viewSize = new java.awt.Dimension(newSize.width, newSize.height);
    }

    public java.awt.Rectangle getViewRect() {
        // JDK: rectangle of the viewport's current visible area in view
        // coordinates. We field-shadow position + extent; the rect is the
        // composition. R_layouts_close_enough close-enough.
        java.awt.Dimension extent = getExtentSize();
        return new java.awt.Rectangle(viewPosition.x, viewPosition.y, extent.width, extent.height);
    }

    public int getScrollMode() {
        return scrollMode;
    }

    /**
     * Stores the mode with no peer effect — Vaadin owns scroll rendering.
     *
     * <p>Deliberately accepts a mode outside the three JDK constants, because the
     * JDK does: {@code setScrollMode(99)} then {@code getScrollMode()} answers
     * 99 on JDK 25. Rejecting it would be the kind of unrequested improvement
     * R_no_silent_improvements forbids — a migrated app that got away with a
     * junk mode on the desktop must get away with it here.
     */
    public void setScrollMode(int mode) {
        this.scrollMode = mode;
    }

    // --- ChangeListener (stored but never fires) -----------------------

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        // Stored for round-trip; never fires per D_viewport_scrollbar_shadows. Vaadin Scroller has
        // no server-side scroll-position event without a custom DOM listener
        // stack — R_match_swing_errors sub-bucket (a).
        if (l != null) listenerList.add(javax.swing.event.ChangeListener.class, l);
    }

    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.remove(javax.swing.event.ChangeListener.class, l);
    }

    public javax.swing.event.ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(javax.swing.event.ChangeListener.class);
    }

    // --- Scroll helpers (R_layouts_close_enough — field round-trip, no peer effect) --------

    public void scrollRectToVisible(java.awt.Rectangle contentRect) {
        // R_layouts_close_enough — no server-side coordinates; the browser owns scroll position.
        // WARN so migration logs surface dependency on this method.
        vaadinx.EHelper.onUnimplemented("JViewport", "scrollRectToVisible", contentRect);
    }

    public java.awt.Insets getInsets() {
        // JDK's Viewport.getInsets returns ZERO_INSETS unconditionally
        // (Container.getInsets is overridden). Match the contract.
        return new java.awt.Insets(0, 0, 0, 0);
    }

    public java.awt.Insets getInsets(java.awt.Insets insets) {
        if (insets == null) insets = new java.awt.Insets(0, 0, 0, 0);
        insets.top = insets.bottom = insets.left = insets.right = 0;
        return insets;
    }

    // --- L&F surface ----------------------------------------------------

    public String getUIClassID() {
        return "ViewportUI";
    }

    public void updateUI() {
        // L&F swap — no-op.
    }

    public javax.swing.plaf.ViewportUI getUI() {
        vaadinx.EHelper.onUnimplemented("JViewport", "getUI");
        return null;
    }

    public void setUI(javax.swing.plaf.ViewportUI ui) {
        vaadinx.EHelper.onUnimplemented("JViewport", "setUI", ui);
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JViewport", "getAccessibleContext");
        return null;
    }
}
