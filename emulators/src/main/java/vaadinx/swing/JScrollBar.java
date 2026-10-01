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
 * This file is derived from OpenJDK's javax.swing.JScrollBar
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
 * Emulator for {@link javax.swing.JScrollBar}. Per
 * <a href="../../../emulators/decisions.md#D_viewport_scrollbar_shadows">D_viewport_scrollbar_shadows</a>,
 * a <strong>shadow JDK component</strong>: emulator-only, no surrogate
 * counterpart. Vaadin's {@link com.vaadin.flow.component.orderedlayout.Scroller}
 * web component owns scrollbar rendering internally — there is no separate
 * Vaadin "scrollbar" object server-side. JScrollBar here exists to give
 * migrated code that calls
 * {@code scrollPane.getVerticalScrollBar().setUnitIncrement(16)} a non-null
 * object that round-trips its own state without NPE — a common pattern in
 * L&amp;F-aware migrated code.
 *
 * <h2>Full BoundedRangeModel bookkeeping</h2>
 *
 * The JDK contract treats JScrollBar as a model object with
 * {@code value} / {@code minimum} / {@code maximum} / {@code extent} +
 * {@code unitIncrement} / {@code blockIncrement}. The emulator stores all
 * of these so user code's setters round-trip through their getters.
 *
 * <h2>Listeners stored but never fire</h2>
 *
 * {@link #addAdjustmentListener} stores the listener; no Vaadin source
 * fires {@link java.awt.event.AdjustmentEvent}s for scroll position
 * without a custom DOM listener stack (R_match_swing_errors sub-bucket (a) — graduate when
 * a migration target asks).
 */
public class JScrollBar extends vaadinx.swing.JComponent
        implements java.awt.Adjustable, javax.accessibility.Accessible {

    // Not final: setOrientation stores, as the JDK's does. Nothing renders from it
    // — the peer is an inert Div — so the whole cost of the swap is the field write,
    // and refusing it would only make getOrientation() lie (R_decline_effect_only).
    protected int orientation;

    // The JDK's protected model field IS the storage, holding a real
    // DefaultBoundedRangeModel exactly as the JDK's does (D_field_write_reconcile). Because
    // nothing renders this scrollbar, the field needs no peer push and no write-detection:
    // every accessor reads the field at use time, so even a direct `sb.model = other` write
    // behaves as it does in real Swing. Model defaults match the JDK's no-arg JScrollBar:
    // value=0, extent=10, min=0, max=100.
    protected javax.swing.BoundedRangeModel model;
    protected int unitIncrement = 1;
    protected int blockIncrement = 10;

    public JScrollBar() {
        this(java.awt.Adjustable.VERTICAL, 0, 10, 0, 100);
    }

    public JScrollBar(int orientation) {
        this(orientation, 0, 10, 0, 100);
    }

    public JScrollBar(int orientation, int value, int extent, int min, int max) {
        super(Div.class, Div::new);  // inert peer — no DOM rendering
        if (orientation != java.awt.Adjustable.HORIZONTAL && orientation != java.awt.Adjustable.VERTICAL) {
            throw new IllegalArgumentException("orientation must be HORIZONTAL or VERTICAL");
        }
        this.orientation = orientation;
        this.model = new javax.swing.DefaultBoundedRangeModel(value, extent, min, max);
    }

    public javax.swing.BoundedRangeModel getModel() {
        return model;
    }

    public void setModel(javax.swing.BoundedRangeModel newModel) {
        // The JDK also rewires its internal AdjustmentEvent-forwarding ChangeListener here;
        // SB-Emulators has none to rewire (listeners stored but never fire, see the class doc).
        javax.swing.BoundedRangeModel old = model;
        model = newModel;
        firePropertyChange("model", old, newModel);
    }

    // --- Adjustable + JDK orientation -----------------------------------

    @Override
    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        if (orientation != java.awt.Adjustable.HORIZONTAL && orientation != java.awt.Adjustable.VERTICAL) {
            throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
        int oldValue = this.orientation;
        this.orientation = orientation;
        // Outside the JDK's own changed test, which gates only revalidate() and the
        // accessibility event. Not that a listener can tell: PropertyChangeSupport
        // filters equal values before dispatch, so an unchanged set is silent
        // anyway — the same reason the JDK hand-fires (null, null) elsewhere.
        firePropertyChange("orientation", oldValue, orientation);
        if (orientation != oldValue) {
            // Nothing draws this scrollbar (D_viewport_scrollbar_shadows: the Scroller web component owns
            // scrollbar rendering), so a re-orient has no pixels to move.
            vaadinx.EHelper.onUnimplemented("JScrollBar", "setOrientation(render)", orientation);
        }
    }

    // --- BoundedRangeModel value / range / extent ----------------------

    @Override
    public int getValue() {
        return model.getValue();
    }

    @Override
    public void setValue(int value) {
        // DefaultBoundedRangeModel clamps to [min, max-extent], as the JDK's setValue relies on.
        // Listeners stored but never fire per D_viewport_scrollbar_shadows — even programmatic
        // setValue doesn't fan out AdjustmentEvents (no event source paired with it). User
        // code that relies on programmatic setValue → AdjustmentListener
        // delivery would need rewrite (R_match_swing_errors sub-bucket (a)).
        model.setValue(value);
    }

    @Override
    public int getMinimum() {
        return model.getMinimum();
    }

    @Override
    public void setMinimum(int minimum) {
        // The model re-clamps value/maximum to maintain the BoundedRangeModel invariants.
        model.setMinimum(minimum);
    }

    @Override
    public int getMaximum() {
        return model.getMaximum();
    }

    @Override
    public void setMaximum(int maximum) {
        model.setMaximum(maximum);
    }

    public int getVisibleAmount() {
        return model.getExtent();
    }

    public void setVisibleAmount(int extent) {
        model.setExtent(extent);
    }

    @Override
    public int getUnitIncrement() {
        return unitIncrement;
    }

    @Override
    public void setUnitIncrement(int unitIncrement) {
        int oldValue = this.unitIncrement;
        this.unitIncrement = unitIncrement;
        firePropertyChange("unitIncrement", oldValue, unitIncrement);
    }

    @Override
    public int getBlockIncrement() {
        return blockIncrement;
    }

    @Override
    public void setBlockIncrement(int blockIncrement) {
        int oldValue = this.blockIncrement;
        this.blockIncrement = blockIncrement;
        firePropertyChange("blockIncrement", oldValue, blockIncrement);
    }

    public boolean getValueIsAdjusting() {
        return model.getValueIsAdjusting();
    }

    public void setValueIsAdjusting(boolean adjusting) {
        model.setValueIsAdjusting(adjusting);
    }

    /**
     * Atomic setter for value / extent / min / max — JDK contract: re-clamps
     * coherently rather than four independent setMinimum/setMaximum/...
     * calls (which can transiently violate invariants).
     */
    public void setValues(int newValue, int newExtent, int newMin, int newMax) {
        model.setRangeProperties(newValue, newExtent, newMin, newMax, model.getValueIsAdjusting());
    }

    // --- AdjustmentListener (stored but never fires) -------------------

    @Override
    public void addAdjustmentListener(java.awt.event.AdjustmentListener l) {
        // Stored for round-trip; never fires per D_viewport_scrollbar_shadows. Vaadin Scroller's
        // web component owns scrollbar interaction; no server-side
        // AdjustmentEvent source.
        if (l != null) listenerList.add(java.awt.event.AdjustmentListener.class, l);
    }

    @Override
    public void removeAdjustmentListener(java.awt.event.AdjustmentListener l) {
        listenerList.remove(java.awt.event.AdjustmentListener.class, l);
    }

    public java.awt.event.AdjustmentListener[] getAdjustmentListeners() {
        return listenerList.getListeners(java.awt.event.AdjustmentListener.class);
    }

    // --- L&F surface ----------------------------------------------------

    public String getUIClassID() {
        return "ScrollBarUI";
    }

    public void updateUI() {
        // L&F swap — no-op.
    }

    public javax.swing.plaf.ScrollBarUI getUI() {
        vaadinx.EHelper.onUnimplemented("JScrollBar", "getUI");
        return null;
    }

    public void setUI(javax.swing.plaf.ScrollBarUI ui) {
        vaadinx.EHelper.onUnimplemented("JScrollBar", "setUI", ui);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JScrollBar", "getAccessibleContext");
        return null;
    }
}
