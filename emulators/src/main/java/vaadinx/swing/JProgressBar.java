/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JProgressBar
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JProgressBar, rendered by
// com.vaadin.swingbridge.surrogates.SJProgressBar (the value push, the string overlay).
// Same shape as JSlider, whose class javadoc is the canonical commentary: every getter
// answers from emulator-owned state (the shared BoundedRangeModel, or a Swing-side
// field) and never reads the peer, and the ChangeListener subscribes to the model
// directly, so a worker reporting progress hears its ChangeEvent on the worker, as on
// the desktop.

import com.vaadin.swingbridge.surrogates.SJProgressBar;

/** Emulator for {@link javax.swing.JProgressBar}, rendered by {@link SJProgressBar}. */
public class JProgressBar extends vaadinx.swing.JComponent
        implements javax.swing.SwingConstants, javax.accessibility.Accessible, vaadinx.FieldReconciler.Reconcilable {

    /**
     * The listener {@link #createChangeListener} produced, subscribed to the model
     * through {@link #modelRelay}. Protected and named as the JDK's is, so a subclass
     * that reads or replaces it compiles unchanged.
     */
    protected javax.swing.event.ChangeListener changeListener;

    /**
     * {@link #changeListener} wrapped in {@code EHelper.relayModelEvent}, as subscribed to
     * the model; {@code null} when an override of {@link #createChangeListener} returned
     * {@code null}, which subscribes nothing, as in the JDK.
     */
    private javax.swing.event.ChangeListener modelRelay;

    /** The model {@link #modelRelay} is subscribed to, and the surrogate renders. */
    private javax.swing.BoundedRangeModel relayedModel;

    // JDK protected fields, Swing-side truth per D_field_write_reconcile (see JSlider for the
    // canonical commentary): getters read them, setters write-through to the surrogate, and a
    // direct field write is repaired by reconcileFields(). progressString is null until the
    // app sets one — the percent fallback stays the surrogate's (mirrors the JDK's compose).
    protected int orientation;
    protected boolean paintBorder;
    protected javax.swing.BoundedRangeModel model;
    protected java.lang.String progressString;
    protected boolean paintString;

    /** Lazily created by {@link #fireStateChanged}, as in the JDK. */
    protected transient javax.swing.event.ChangeEvent changeEvent;

    // Private in the JDK too, so no reconcile: only setIndeterminate writes it.
    private boolean indeterminate;

    /** The percent format {@link #getString} falls back to; created on first use, as in the JDK. */
    private transient java.text.Format format;

    // Last values pushed to the peer — reconcileFields()'s write-detection baseline.
    private int pushedOrientation;
    private boolean pushedPaintBorder;
    private java.lang.String pushedProgressString;
    private boolean pushedPaintString;

    public JProgressBar() {
        this(HORIZONTAL, 0, 100);
    }

    public JProgressBar(int orientation) {
        this(orientation, 0, 100);
    }

    public JProgressBar(int min, int max) {
        this(HORIZONTAL, min, max);
    }

    /**
     * @throws IllegalArgumentException if {@code min > max} — the model's own check, made first,
     *         as in the JDK — or if {@code orientation} is neither {@code HORIZONTAL} nor {@code VERTICAL}
     */
    public JProgressBar(int orientation, int min, int max) {
        this(new javax.swing.DefaultBoundedRangeModel(min, 0, min, max), checkOrientation(orientation));
    }

    public JProgressBar(javax.swing.BoundedRangeModel brm) {
        this(brm, HORIZONTAL);
    }

    private JProgressBar(javax.swing.BoundedRangeModel brm, int orientation) {
        // Peer lock-down per R_leaf_peer_lockdown: javax.swing.JProgressBar is a leaf in
        // the public Swing hierarchy, and this private ctor is the only one naming the peer.
        super(SJProgressBar.class, () -> {
            SJProgressBar peer = new SJProgressBar(brm);
            peer.setOrientation(orientation);
            return peer;
        });
        // Obtained from createChangeListener() rather than inlined, because the JDK's ctor
        // subscribes that hook's return value and a migrator's override has to reach the
        // same seam (R_no_vaadin_in_api limb 2, D_dead_hook_lint).
        //
        // Relayed: callSwing when a UI is current, so a listener can open a modal; inline
        // on a worker reporting progress, where the JDK fires too, and where a modal takes
        // D_modal_from_background's blocking path. Wrapped here, not in
        // createChangeListener, so an override gets the relay too.
        changeListener = createChangeListener();
        javax.swing.event.ChangeListener hook = changeListener;
        if (hook != null) {
            modelRelay = e -> vaadinx.EHelper.relayModelEvent(() -> hook.stateChanged(e));
        }
        model = brm;
        subscribe(model);
        // The JDK's defaults, which are also the peer's: the write-detection baselines start
        // equal. progressString stays null, the JDK default.
        this.orientation = pushedOrientation = orientation;
        paintBorder = pushedPaintBorder = true;
        paintString = pushedPaintString = false;
        vaadinx.FieldReconciler.register(this);
    }

    private static int checkOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException(orientation + " is not a legal orientation");
        }
        return orientation;
    }

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (model != relayedModel) {
            javax.swing.BoundedRangeModel m = model;
            subscribe(m);
            withPeer(p -> bar().setModel(m));
            vaadinx.FieldReconciler.reportDirectWrite(this, "model", "setModel");
        }
        if (orientation != pushedOrientation) {
            int v = orientation;
            withPeer(p -> bar().setOrientation(v));
            pushedOrientation = orientation;
            vaadinx.FieldReconciler.reportDirectWrite(this, "orientation", "setOrientation");
        }
        if (paintBorder != pushedPaintBorder) {
            boolean v = paintBorder;
            withPeer(p -> bar().setBorderPainted(v));
            pushedPaintBorder = paintBorder;
            vaadinx.FieldReconciler.reportDirectWrite(this, "paintBorder", "setBorderPainted");
        }
        if (paintString != pushedPaintString) {
            boolean v = paintString;
            withPeer(p -> bar().setStringPainted(v));
            pushedPaintString = paintString;
            vaadinx.FieldReconciler.reportDirectWrite(this, "paintString", "setStringPainted");
        }
        if (!java.util.Objects.equals(progressString, pushedProgressString)) {
            java.lang.String v = progressString;
            withPeer(p -> bar().setString(v));
            pushedProgressString = progressString;
            vaadinx.FieldReconciler.reportDirectWrite(this, "progressString", "setString");
        }
    }

    /** Narrow the peer to its SJProgressBar type. Peer is always an SJProgressBar. */
    private SJProgressBar bar() {
        return (SJProgressBar) getPeer();
    }

    /** Moves {@link #modelRelay} from {@link #relayedModel} onto {@code m}. */
    private void subscribe(javax.swing.BoundedRangeModel m) {
        if (relayedModel != null && modelRelay != null) relayedModel.removeChangeListener(modelRelay);
        relayedModel = m;
        if (m != null && modelRelay != null) m.addChangeListener(modelRelay);
    }

    // ---- Swing-side value / min / max API ----
    //
    // Through the model, as in the JDK. None of these touch the peer: the surrogate's
    // model listener renders the change, hopping onto its UI thread when a worker made it
    // (SD_background_model_hop).

    public int getValue() {
        return getModel().getValue();
    }

    public void setValue(int n) {
        getModel().setValue(n);
    }

    public int getMinimum() {
        return getModel().getMinimum();
    }

    public void setMinimum(int minimum) {
        // No PropertyChangeEvent, unlike JSlider: JProgressBar.setMinimum is
        // one line — getModel().setMinimum(n) — and the notification is the
        // model's own ChangeEvent (D_property_fanout_audit).
        getModel().setMinimum(minimum);
    }

    public int getMaximum() {
        return getModel().getMaximum();
    }

    public void setMaximum(int maximum) {
        // No PropertyChangeEvent, unlike JSlider: see setMinimum (D_property_fanout_audit).
        getModel().setMaximum(maximum);
    }

    public javax.swing.BoundedRangeModel getModel() {
        return model;
    }

    public void setModel(javax.swing.BoundedRangeModel newModel) {
        // No PropertyChangeEvent, unlike every other Swing setModel:
        // JProgressBar.setModel fires only ACCESSIBLE_VALUE_PROPERTY, on the
        // AccessibleContext's own listener list, which
        // addPropertyChangeListener on the component never reaches (D_property_fanout_audit).
        if (model == newModel) return;
        model = newModel;
        subscribe(newModel);
        withPeer(p -> bar().setModel(newModel));
    }

    /**
     * The JDK's arithmetic, so an empty range ({@code minimum == maximum}) answers
     * {@code NaN}, as on the desktop.
     */
    public double getPercentComplete() {
        long span = model.getMaximum() - model.getMinimum();
        double currentValue = model.getValue();
        return (currentValue - model.getMinimum()) / span;
    }

    // ---- Indeterminate ----

    public void setIndeterminate(boolean newValue) {
        boolean oldValue = indeterminate;
        indeterminate = newValue;
        withPeer(p -> bar().setIndeterminate(newValue));
        firePropertyChange("indeterminate", oldValue, newValue);
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }

    // ---- Orientation ----

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        // JDK validates before any state change (IAE), so the field is never left holding
        // a value the peer rejected.
        checkOrientation(orientation);
        int old = this.orientation;
        this.orientation = orientation;
        withPeer(p -> bar().setOrientation(orientation));
        pushedOrientation = orientation;
        firePropertyChange("orientation", old, orientation);
    }

    // ---- String / stringPainted (CSS overlay rendering on surrogate) ----

    public boolean isStringPainted() {
        return paintString;
    }

    public void setStringPainted(boolean b) {
        boolean old = paintString;
        paintString = b;
        withPeer(p -> bar().setStringPainted(b));
        pushedPaintString = b;
        firePropertyChange("stringPainted", old, b);
    }

    public String getString() {
        if (progressString != null) return progressString;
        if (format == null) format = java.text.NumberFormat.getPercentInstance();
        return format.format(getPercentComplete());
    }

    public void setString(String s) {
        // JDK: the PCE's old value is the raw field (possibly null), not the composed
        // getString() — reproduced now that the field exists.
        String old = progressString;
        progressString = s;
        withPeer(p -> bar().setString(s));
        pushedProgressString = s;
        firePropertyChange("string", old, s);
    }

    // ---- BorderPainted (R_vaadin_first drop-and-WARN on surrogate; emulator round-trip) ----

    public boolean isBorderPainted() {
        return paintBorder;
    }

    public void setBorderPainted(boolean b) {
        boolean old = paintBorder;
        paintBorder = b;
        withPeer(p -> bar().setBorderPainted(b));
        pushedPaintBorder = b;
        firePropertyChange("borderPainted", old, b);
    }

    // ---- ChangeListener (emulator-side listenerList, source = this) ----
    //
    // Migrated code casts `(JProgressBar) e.getSource()`. The model subscription installed
    // in the ctor fires here, with this emulator as the source.

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.add(javax.swing.event.ChangeListener.class, l);
    }

    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.remove(javax.swing.event.ChangeListener.class, l);
    }

    public javax.swing.event.ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(javax.swing.event.ChangeListener.class);
    }

    protected void fireStateChanged() {
        // JDK shape: the one ChangeEvent instance is lazily cached in the protected
        // changeEvent field (it carries only the source, which never changes).
        if (changeEvent == null) {
            changeEvent = new javax.swing.event.ChangeEvent(this);
        }
        for (javax.swing.event.ChangeListener l :
                listenerList.getListeners(javax.swing.event.ChangeListener.class)) {
            l.stateChanged(changeEvent);
        }
    }

    /** JDK hook: the listener subscribed to the model, which re-fires as this bar. */
    protected javax.swing.event.ChangeListener createChangeListener() {
        return e -> fireStateChanged();
    }

    // ---- L&F stubs ----

    public void updateUI() {
        // L&F swap — Vaadin owns the DOM; no pluggable UI.
    }

    public String getUIClassID() {
        // Kept for BeanInfo-style introspection; matches JDK JProgressBar.
        return "ProgressBarUI";
    }

    public javax.swing.plaf.ProgressBarUI getUI() {
        // No pluggable UI delegate; narrowing to ProgressBarUI would be
        // a bare cast on a stubbed null, so short-circuit here.
        return null;
    }

    public void setUI(javax.swing.plaf.ProgressBarUI ui) {
        // L&F install — no-op per above.
    }

    protected String paramString() {
        // Debug string over the JDK-shaped fields (progressString is the raw field, as in
        // the JDK's paramString — null when the app never set one).
        return super.paramString()
                + ",orientation="
                + (orientation == HORIZONTAL ? "HORIZONTAL" : "VERTICAL")
                + ",paintBorder=" + paintBorder
                + ",paintString=" + paintString
                + ",progressString=" + progressString
                + ",indeterminateString=" + indeterminate;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JProgressBar", "getAccessibleContext");
        return null;
    }
}
