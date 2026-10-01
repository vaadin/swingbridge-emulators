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
 * This file is derived from OpenJDK's javax.swing.JSlider
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.swingbridge.surrogates.SJSlider;

/**
 * Emulator for {@link javax.swing.JSlider}, rendered by {@link SJSlider} (SD_sjslider).
 *
 * <p>Every getter answers from emulator-owned state and never reads the peer, so it works
 * on any thread without a hop. Value, minimum, maximum and extent live in the
 * {@link javax.swing.BoundedRangeModel}, which the emulator and the surrogate share: the
 * emulator reads and writes the model as the JDK does, and the surrogate renders it from
 * its own model listener. {@link #changeListener} subscribes to the model directly, as
 * the JDK's does, so a worker moving the model hears its {@code ChangeEvent} on the
 * worker. The JDK's protected fields and the private flags are Swing-side fields per
 * D_field_write_reconcile, pushed to the surrogate through {@code withPeer}. The surrogate
 * keeps its own copies, because it renders and snaps from them.
 * R_leaf_peer_lockdown-locked-down (JDK leaf — private peer ctor).
 */
public class JSlider extends vaadinx.swing.JComponent
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

    // JDK protected fields, Swing-side truth per D_field_write_reconcile: getters read
    // them, setters write them first and then push the peer. The surrogate keeps its own
    // live copies (they drive rendering + server-side snapping) fed by the write-through;
    // a direct field write is repaired by reconcileFields(). sliderModel is a second
    // pointer to the one model object the surrogate holds — not a second copy.
    protected javax.swing.BoundedRangeModel sliderModel;
    protected int majorTickSpacing;
    protected int minorTickSpacing;
    protected boolean snapToTicks;
    protected int orientation;

    /** Lazily created by {@link #fireStateChanged}, as in the JDK. */
    protected transient javax.swing.event.ChangeEvent changeEvent;

    // Private in the JDK too, so no reconcile: only the setters write them.
    private boolean isInverted;
    private boolean paintTicks;
    private boolean paintTrack = true;
    private boolean paintLabels;
    @SuppressWarnings("rawtypes")
    private java.util.Dictionary labelTable;

    // Last values pushed to the peer — reconcileFields()'s write-detection baseline.
    // The model needs no shadow: the peer's own reference is the baseline there.
    private int pushedMajorTickSpacing;
    private int pushedMinorTickSpacing;
    private boolean pushedSnapToTicks;
    private int pushedOrientation;

    public JSlider() {
        this(HORIZONTAL, 0, 100, 50);
    }

    public JSlider(int orientation) {
        this(orientation, 0, 100, 50);
    }

    public JSlider(int min, int max) {
        this(HORIZONTAL, min, max, (min + max) / 2);
    }

    public JSlider(int min, int max, int value) {
        this(HORIZONTAL, min, max, value);
    }

    /**
     * @throws IllegalArgumentException if {@code orientation} is neither {@code HORIZONTAL} nor
     *         {@code VERTICAL}, or if {@code min <= value <= max} does not hold — the model's own check
     */
    public JSlider(int orientation, int min, int max, int value) {
        this(checkOrientation(orientation), new javax.swing.DefaultBoundedRangeModel(value, 0, min, max));
    }

    public JSlider(javax.swing.BoundedRangeModel brm) {
        this(HORIZONTAL, brm);
    }

    private JSlider(int orientation, javax.swing.BoundedRangeModel brm) {
        // Peer lock-down per R_leaf_peer_lockdown: javax.swing.JSlider is a leaf in the
        // public Swing hierarchy, and this private ctor is the only one naming the peer.
        super(SJSlider.class, () -> {
            SJSlider peer = new SJSlider(brm);
            peer.setOrientation(orientation);
            return peer;
        });
        // Obtained from createChangeListener() rather than inlined, because the JDK's ctor
        // subscribes that hook's return value and a migrator's override has to reach the
        // same seam (R_no_vaadin_in_api limb 2, D_dead_hook_lint).
        changeListener = createChangeListener();
        // Relayed: callSwing when a UI is current (a browser drag), so a listener can open
        // a modal; inline on a worker, where the JDK fires too. Wrapped here, not in
        // createChangeListener, so an override gets the relay too.
        javax.swing.event.ChangeListener hook = changeListener;
        if (hook != null) {
            modelRelay = e -> vaadinx.EHelper.relayModelEvent(() -> hook.stateChanged(e));
        }
        sliderModel = brm;
        subscribe(sliderModel);
        // The JDK's defaults, which are also the peer's: the write-detection baselines start equal.
        this.orientation = pushedOrientation = orientation;
        majorTickSpacing = pushedMajorTickSpacing = 0;
        minorTickSpacing = pushedMinorTickSpacing = 0;
        snapToTicks = pushedSnapToTicks = false;
        vaadinx.FieldReconciler.register(this);
    }

    private static int checkOrientation(int orientation) {
        if (orientation != HORIZONTAL && orientation != VERTICAL) {
            throw new IllegalArgumentException("orientation must be one of: VERTICAL, HORIZONTAL");
        }
        return orientation;
    }

    /**
     * D_field_write_reconcile repair hook — compares each JDK-shaped protected field against
     * what was last pushed to the peer and re-pushes on mismatch, logging the ERROR. Final:
     * the reconcile is framework plumbing, not a JDK hook a subclass should retarget.
     */
    @Override
    public final void reconcileFields() {
        if (sliderModel != relayedModel) {
            javax.swing.BoundedRangeModel m = sliderModel;
            subscribe(m);
            withPeer(p -> slider().setModel(m));
            vaadinx.FieldReconciler.reportDirectWrite(this, "sliderModel", "setModel");
        }
        if (orientation != pushedOrientation) {
            int v = orientation;
            withPeer(p -> slider().setOrientation(v));
            pushedOrientation = orientation;
            vaadinx.FieldReconciler.reportDirectWrite(this, "orientation", "setOrientation");
        }
        if (majorTickSpacing != pushedMajorTickSpacing) {
            int v = majorTickSpacing;
            withPeer(p -> slider().setMajorTickSpacing(v));
            pushedMajorTickSpacing = majorTickSpacing;
            vaadinx.FieldReconciler.reportDirectWrite(this, "majorTickSpacing", "setMajorTickSpacing");
        }
        if (minorTickSpacing != pushedMinorTickSpacing) {
            int v = minorTickSpacing;
            withPeer(p -> slider().setMinorTickSpacing(v));
            pushedMinorTickSpacing = minorTickSpacing;
            vaadinx.FieldReconciler.reportDirectWrite(this, "minorTickSpacing", "setMinorTickSpacing");
        }
        if (snapToTicks != pushedSnapToTicks) {
            boolean v = snapToTicks;
            withPeer(p -> slider().setSnapToTicks(v));
            pushedSnapToTicks = snapToTicks;
            vaadinx.FieldReconciler.reportDirectWrite(this, "snapToTicks", "setSnapToTicks");
        }
    }

    /** Narrow the peer to its SJSlider type. */
    private SJSlider slider() {
        return (SJSlider) getPeer();
    }

    /** Moves {@link #modelRelay} from {@link #relayedModel} onto {@code m}. */
    private void subscribe(javax.swing.BoundedRangeModel m) {
        if (relayedModel != null && modelRelay != null) relayedModel.removeChangeListener(modelRelay);
        relayedModel = m;
        if (m != null && modelRelay != null) m.addChangeListener(modelRelay);
    }

    // ---- Swing-side value / min / max / extent API ----
    //
    // Through the model, as in the JDK. None of these touch the peer: the surrogate's
    // model listener renders the change, hopping onto its UI thread when a worker made it
    // (SD_background_model_hop).

    public int getValue() {
        return getModel().getValue();
    }

    public void setValue(int n) {
        javax.swing.BoundedRangeModel m = getModel();
        if (m.getValue() == n) return;
        m.setValue(n);
    }

    public int getMinimum() {
        return getModel().getMinimum();
    }

    public void setMinimum(int minimum) {
        int old = getModel().getMinimum();
        getModel().setMinimum(minimum);
        firePropertyChange("minimum", old, minimum);
    }

    public int getMaximum() {
        return getModel().getMaximum();
    }

    public void setMaximum(int maximum) {
        int old = getModel().getMaximum();
        getModel().setMaximum(maximum);
        firePropertyChange("maximum", old, maximum);
    }

    public int getExtent() {
        return getModel().getExtent();
    }

    public void setExtent(int extent) {
        getModel().setExtent(extent);
    }

    public boolean getValueIsAdjusting() {
        return getModel().getValueIsAdjusting();
    }

    public void setValueIsAdjusting(boolean b) {
        getModel().setValueIsAdjusting(b);
    }

    public javax.swing.BoundedRangeModel getModel() {
        return sliderModel;
    }

    public void setModel(javax.swing.BoundedRangeModel newModel) {
        javax.swing.BoundedRangeModel old = sliderModel;
        if (old == newModel) return;
        sliderModel = newModel;
        subscribe(newModel);
        withPeer(p -> slider().setModel(newModel));
        firePropertyChange("model", old, newModel);
    }

    // ---- orientation / inverted ----

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        // JDK validates before any state change (checkOrientation → IAE), so the field is
        // never left holding a value the peer rejected.
        checkOrientation(orientation);
        int old = this.orientation;
        this.orientation = orientation;
        withPeer(p -> slider().setOrientation(orientation));
        pushedOrientation = orientation;
        firePropertyChange("orientation", old, orientation);
    }

    public boolean getInverted() {
        return isInverted;
    }

    public void setInverted(boolean b) {
        boolean old = isInverted;
        isInverted = b;
        withPeer(p -> slider().setInverted(b));
        firePropertyChange("inverted", old, b);
    }

    // ---- tick / label / track (deferred to upstream ticket #9181 in SJSlider) ----
    //
    // The field is stored here and the PCE fires here; the surrogate gets the value
    // pushed and emits the rendering WARN once, so it is not double-fired here.

    public boolean getPaintTicks() {
        return paintTicks;
    }

    public void setPaintTicks(boolean b) {
        boolean old = paintTicks;
        paintTicks = b;
        withPeer(p -> slider().setPaintTicks(b));
        firePropertyChange("paintTicks", old, b);
    }

    public boolean getPaintTrack() {
        return paintTrack;
    }

    public void setPaintTrack(boolean b) {
        boolean old = paintTrack;
        paintTrack = b;
        withPeer(p -> slider().setPaintTrack(b));
        firePropertyChange("paintTrack", old, b);
    }

    public boolean getPaintLabels() {
        return paintLabels;
    }

    public void setPaintLabels(boolean b) {
        boolean old = paintLabels;
        paintLabels = b;
        withPeer(p -> slider().setPaintLabels(b));
        firePropertyChange("paintLabels", old, b);
    }

    public boolean getSnapToTicks() {
        return snapToTicks;
    }

    public void setSnapToTicks(boolean b) {
        boolean old = snapToTicks;
        snapToTicks = b;
        withPeer(p -> slider().setSnapToTicks(b));
        pushedSnapToTicks = b;
        firePropertyChange("snapToTicks", old, b);
    }

    public int getMajorTickSpacing() {
        return majorTickSpacing;
    }

    public void setMajorTickSpacing(int n) {
        int old = majorTickSpacing;
        majorTickSpacing = n;
        withPeer(p -> slider().setMajorTickSpacing(n));
        pushedMajorTickSpacing = n;
        firePropertyChange("majorTickSpacing", old, n);
    }

    public int getMinorTickSpacing() {
        return minorTickSpacing;
    }

    public void setMinorTickSpacing(int n) {
        int old = minorTickSpacing;
        minorTickSpacing = n;
        withPeer(p -> slider().setMinorTickSpacing(n));
        pushedMinorTickSpacing = n;
        firePropertyChange("minorTickSpacing", old, n);
    }

    @SuppressWarnings("rawtypes")
    public java.util.Dictionary getLabelTable() {
        return labelTable;
    }

    @SuppressWarnings("rawtypes")
    public void setLabelTable(java.util.Dictionary labels) {
        java.util.Dictionary old = labelTable;
        labelTable = labels;
        withPeer(p -> slider().setLabelTable(labels));
        firePropertyChange("labelTable", old, labels);
    }

    /**
     * Logs the rendering deferral WARN and returns an empty table, since tick labels do
     * not render (SD_sjslider). An empty table satisfies the JDK's
     * {@code Hashtable<Integer, JComponent>} value type trivially.
     */
    public java.util.Hashtable<java.lang.Integer, vaadinx.swing.JComponent> createStandardLabels(int increment) {
        vaadinx.EHelper.onUnimplemented("JSlider", "createStandardLabels", increment);
        return new java.util.Hashtable<>();
    }

    /** Two-argument form of {@link #createStandardLabels(int)}, with the same deferral. */
    public java.util.Hashtable<java.lang.Integer, vaadinx.swing.JComponent> createStandardLabels(int increment, int start) {
        vaadinx.EHelper.onUnimplemented("JSlider", "createStandardLabels", increment, start);
        return new java.util.Hashtable<>();
    }

    /**
     * JDK walks labelTable and calls updateUI on each JComponent value.
     * Our labelTable is always empty (setLabelTable is deferred at the
     * surrogate layer), so the walk has nothing to do.
     */
    protected void updateLabelUIs() {
        // no-op per above
    }

    // ---- ChangeListener (emulator-side listenerList, source = this) ----
    //
    // Migrated code casts `(JSlider) e.getSource()`. The model subscription installed in
    // the ctor fires here, with this emulator as the source.

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

    /** JDK hook: the listener subscribed to the model, which re-fires as this slider. */
    protected javax.swing.event.ChangeListener createChangeListener() {
        return e -> fireStateChanged();
    }

    // ---- L&F stubs ----

    public void updateUI() {
        // L&F swap — Vaadin owns the DOM; no pluggable UI.
    }

    public java.lang.String getUIClassID() {
        // Kept for BeanInfo-style introspection; matches JDK JSlider.
        return "SliderUI";
    }

    public javax.swing.plaf.SliderUI getUI() {
        // No pluggable UI delegate. JComponent.getUI returns
        // ComponentUI (the super type); narrowing to SliderUI would
        // be a bare cast on a stubbed null, so short-circuit here.
        return null;
    }

    public void setUI(javax.swing.plaf.SliderUI ui) {
        // L&F install — no-op per above.
    }

    public void setFont(java.awt.Font f) {
        // JDK JSlider's override invalidates layout; neither we nor
        // the browser need that (R_layouts_close_enough). Chain to super for the
        // Component-side CSS write.
        super.setFont(f);
    }

    public boolean imageUpdate(java.awt.Image img, int infoflags, int x, int y, int w, int h) {
        // AWT animated-image callback — browser doesn't drive painting
        // this way. Chain to super's Component default (returns false).
        return super.imageUpdate(img, infoflags, x, y, w, h);
    }

    protected java.lang.String paramString() {
        // Debug string over the Swing-side fields.
        return super.paramString()
                + ",isInverted=" + isInverted
                + ",majorTickSpacing=" + majorTickSpacing
                + ",minorTickSpacing=" + minorTickSpacing
                + ",orientation="
                + (orientation == HORIZONTAL ? "HORIZONTAL" : "VERTICAL")
                + ",paintLabels=" + paintLabels
                + ",paintTicks=" + paintTicks
                + ",paintTrack=" + paintTrack
                + ",snapToTicks=" + snapToTicks;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JSlider", "getAccessibleContext");
        return null;
    }

}
