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

import com.vaadin.flow.component.slider.IntegerSlider;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.util.Dictionary;
import java.util.Hashtable;

/**
 * Surrogate for {@link javax.swing.JSlider} (SD_sjslider). Extends Vaadin
 * {@link IntegerSlider} directly (is-a) so it reaches the migrator either
 * through the {@code :emulators} {@code JSlider} as peer, or as a
 * richer-than-stock Vaadin component in its own right.
 *
 * <p>Vaadin's typed {@link IntegerSlider} (value type {@link Integer}) is the
 * natural peer for JSlider's int-valued model — a {@code Double}-typed peer
 * would need lossy casts on every value/min/max push.
 *
 * <h2>Model flow (reused from {@code :emulators.JSlider} under SD_sjslider)</h2>
 *
 * The installed {@link BoundedRangeModel} is the source of truth. A single
 * {@link ChangeListener} on the model fans out to user ChangeListeners and
 * pushes value changes to the peer (this Slider's own element) under the
 * {@link #preventPeerEvents} R_swing_is_truth guard. The peer's ValueChangeListener
 * mirrors browser-originated edits back into the model via
 * {@link SHelper#callSwing} (R_callswing_envelope seam); the resulting model ChangeEvent
 * re-enters {@link #fanOutModelChange}, hits the preventPeerEvents guard,
 * and only the Swing-side fan-out runs — no second peer write.
 *
 * <h2>CSS-only rendering (orientation / inverted)</h2>
 *
 * {@code VERTICAL} rotates the host element {@code -90deg}. {@code inverted}
 * flips the horizontal axis via {@code scaleX(-1)}. Combinations compose
 * through {@link #applyTransformCss()} — a single {@code transform} rule
 * per setter so neither knob leaks the other's state when changed.
 *
 * <h2>Server-side snapping ({@code snapToTicks})</h2>
 *
 * When {@link #snapToTicks} is enabled and a non-zero tick spacing is
 * installed, browser-originated values round to the nearest tick before
 * reaching the model. {@link #minorTickSpacing} wins over
 * {@link #majorTickSpacing} when both are set — matches JDK JSlider's
 * snap priority.
 *
 * <h2>Deferred rendering (upstream ticket)</h2>
 *
 * Tick marks, value labels, and track-hiding all need addressable CSS
 * parts or slots on {@code vaadin-slider} that the component doesn't
 * currently expose. Filed as
 * <a href="https://github.com/vaadin/flow-components/issues/9181">vaadin/flow-components#9181</a>.
 * Until upstream lands, {@link #setPaintTicks(boolean)},
 * {@link #setPaintLabels(boolean)}, {@link #setPaintTrack(boolean)},
 * {@link #setLabelTable(Dictionary)}, and {@link #createStandardLabels(int)}
 * field-round-trip, fire their PCE, but log a WARN on visual-effect
 * arguments (true / non-null). The deferral is scoped to rendering only —
 * {@link #snapToTicks} and the tick-spacing fields work for their
 * non-visual uses (snap logic, BeanInfo introspection).
 */
public class SJSlider extends IntegerSlider implements JComponentMixin {

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

    // ---- BoundedRangeModel state (source of truth) ----

    private BoundedRangeModel sliderModel;
    private ChangeListener modelListener;

    // R_swing_is_truth feedback-loop guard — identical pattern to JSlider / JToggleButton.
    // Set before writing to the peer (this Slider's setValue) so the
    // peer listener bails instead of round-tripping back into the model.
    private boolean preventPeerEvents;

    // javax.swing.EventListenerList for ChangeListener fan-out. Same
    // shape as AbstractButton's listenerList — lazy event allocation,
    // type-keyed arrays. Allocated eagerly; weight is negligible.
    private final EventListenerList listenerList = new EventListenerList();

    // ---- Swing-side appearance state ----
    //
    // Field-stored regardless of whether we can render the effect, so
    // BeanInfo-style introspection (`getPaintTicks()` round-trip) works
    // for migrated code that probes its own state before writing.

    private int orientation = SwingConstants.HORIZONTAL;
    private boolean paintTicks;
    private boolean paintTrack = true;
    private boolean paintLabels;
    private boolean snapToTicks;
    private boolean inverted;
    private int majorTickSpacing;
    private int minorTickSpacing;
    @SuppressWarnings("rawtypes")
    private Dictionary labelTable;

    // ---- Constructors ----
    //
    // The four-arg (orientation, min, max, value) form is the root. All
    // others delegate to it so orientation validation, peer wiring, and
    // model installation happen in one place.

    public SJSlider() {
        this(SwingConstants.HORIZONTAL, 0, 100, 50);
    }

    public SJSlider(int orientation) {
        this(orientation, 0, 100, 50);
    }

    public SJSlider(int min, int max) {
        // JDK computes midpoint with int floor — preserve.
        this(SwingConstants.HORIZONTAL, min, max, (min + max) / 2);
    }

    public SJSlider(int min, int max, int value) {
        this(SwingConstants.HORIZONTAL, min, max, value);
    }

    public SJSlider(int orientation, int min, int max, int value) {
        super(min, max);
        _installSwingClass();
        initPeerListener();
        // Validation throws IAE before we touch any mutable state —
        // matches Swing's fail-fast contract (D_never_fail_on_gaps).
        setOrientation(orientation);
        installModel(new DefaultBoundedRangeModel(value, 0, min, max));
    }

    public SJSlider(BoundedRangeModel brm) {
        super(brm.getMinimum(), brm.getMaximum());
        _installSwingClass();
        initPeerListener();
        installModel(brm);
    }

    // ---- Peer listener (Vaadin → model via R_callswing_envelope) ----

    private void initPeerListener() {
        // Peer → Swing: a browser-originated drag / arrow-key fires
        // ValueChangeListener carrying the new Integer. Funneled through
        // SHelper.callSwing so the blocking-dialog seam (D_blocking_dialogs_deferred)
        // lands in one place when it arrives.
        addValueChangeListener(e -> SHelper.callSwing(
                () -> syncValueFromPeer(e.getValue())));
    }

    // ---- Model plumbing ----

    /**
     * Attach the supplied model, wire the fan-out listener, push the
     * model's current value through to the peer. Also called on
     * setModel — teardown of the previous subscription happens here so
     * the single entry point is self-contained.
     */
    private void installModel(BoundedRangeModel brm) {
        if (sliderModel != null && modelListener != null) {
            sliderModel.removeChangeListener(modelListener);
        }
        sliderModel = brm;
        if (modelListener == null) {
            modelListener = e -> fanOutModelChange();
        }
        brm.addChangeListener(modelListener);
        pushValueToPeer(brm.getValue());
    }

    /**
     * Dispatch one ChangeEvent (source=this) to every registered user
     * ChangeListener and push the model's current value to the peer.
     * Called whenever the model fires — user-code setValue and peer
     * mirror writes both land here.
     */
    private void fanOutModelChange() {
        // Allowed by R_tolerate_off_ui_thread because callback from model: BoundedRangeModel ChangeListener
        SHelper.runOnOwnerUI(this, () -> {
            pushRangeToPeer();
            pushValueToPeer(sliderModel.getValue());
            fireStateChanged();
        });
    }

    /**
     * Mirrors the model's range onto the peer. Here rather than in {@link #setMinimum} /
     * {@link #setMaximum} so that a change made on the model directly reaches the peer too.
     */
    private void pushRangeToPeer() {
        if (getMin() != sliderModel.getMinimum()) super.setMin(sliderModel.getMinimum());
        if (getMax() != sliderModel.getMaximum()) super.setMax(sliderModel.getMaximum());
    }

    private void pushValueToPeer(int value) {
        preventPeerEvents = true;
        try {
            // IntegerSlider.setValue clamps into [min, max] internally — safe.
            super.setValue(value);
        } finally {
            preventPeerEvents = false;
        }
    }

    private void syncValueFromPeer(Integer value) {
        if (preventPeerEvents) return;
        if (value == null) return;
        int newValue = value;
        if (snapToTicks) {
            newValue = snap(newValue);
        }
        if (sliderModel.getValue() == newValue) {
            // Value-change that snaps back to the same int — but the peer
            // may be holding an un-snapped value. Push the snapped int
            // back so the browser and model agree.
            if (value != newValue) {
                pushValueToPeer(newValue);
            }
            return;
        }
        // Writing through the model fires its ChangeEvent, which our
        // modelListener picks up and fans out + pushes to peer.
        sliderModel.setValue(newValue);
    }

    /**
     * JDK snap priority: minorTickSpacing wins when both are set.
     * Rounding is always relative to minimum (so an odd minimum
     * snaps cleanly onto the tick grid). Result is clamped into
     * [minimum, maximum].
     */
    private int snap(int value) {
        int spacing = minorTickSpacing > 0 ? minorTickSpacing : majorTickSpacing;
        if (spacing <= 0) return value;
        int min = sliderModel.getMinimum();
        int max = sliderModel.getMaximum();
        int snapped = min + (int) Math.round((double) (value - min) / spacing) * spacing;
        if (snapped < min) snapped = min;
        if (snapped > max) snapped = max;
        return snapped;
    }

    // ---- Swing-style value / min / max / extent API ----
    //
    // getValue() clashes with Vaadin IntegerSlider's Integer getValue() on
    // return type (int is not a subtype of Integer, so no covariant override)
    // — Vaadin-first wins (SD_vaadin_first_binding signature-clash rule). Users who need the int
    // route through getModel().getValue(). setValue(int) coexists with the
    // inherited setValue(Integer) as an overload.

    /**
     * Int-valued setValue — overload of {@link IntegerSlider}'s inherited
     * {@code setValue(Integer)}. Routes through the model so the single
     * ChangeListener path handles both user-code mutations and
     * peer-originated ones.
     */
    public void setValue(int n) {
        sliderModel.setValue(n);
    }

    public int getMinimum() {
        return sliderModel.getMinimum();
    }

    public void setMinimum(int minimum) {
        int old = sliderModel.getMinimum();
        sliderModel.setMinimum(minimum);
        firePropertyChange("minimum", old, minimum);
    }

    public int getMaximum() {
        return sliderModel.getMaximum();
    }

    public void setMaximum(int maximum) {
        int old = sliderModel.getMaximum();
        sliderModel.setMaximum(maximum);
        firePropertyChange("maximum", old, maximum);
    }

    public int getExtent() {
        return sliderModel.getExtent();
    }

    public void setExtent(int extent) {
        // Model-only. Vaadin Slider has a single handle; "range covered
        // by the thumb" visual has no peer counterpart. The field
        // round-trips so BoundedRangeModel's contract stays honest
        // (value + extent <= maximum) for user code that queries.
        sliderModel.setExtent(extent);
    }

    public boolean getValueIsAdjusting() {
        return sliderModel.getValueIsAdjusting();
    }

    public void setValueIsAdjusting(boolean b) {
        // Vaadin Slider fires ValueChangeListener continuously during
        // drag but doesn't expose a server-side "drag in progress" bit
        // we can map to this flag. Model round-trip only — R_best_effort_behaviour
        // best-effort.
        sliderModel.setValueIsAdjusting(b);
    }

    public BoundedRangeModel getModel() {
        return sliderModel;
    }

    public void setModel(BoundedRangeModel newModel) {
        BoundedRangeModel old = this.sliderModel;
        if (old == newModel) return;
        installModel(newModel);
        // Peer's min/max may differ from the new model's — push them
        // through IntegerSlider's public setters.
        super.setMin(newModel.getMinimum());
        super.setMax(newModel.getMaximum());
        firePropertyChange("model", old, newModel);
    }

    // ---- Orientation (HORIZONTAL / VERTICAL via CSS transform) ----

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        if (orientation != SwingConstants.HORIZONTAL && orientation != SwingConstants.VERTICAL) {
            throw new IllegalArgumentException(
                    "orientation must be one of: VERTICAL, HORIZONTAL");
        }
        int old = this.orientation;
        if (old == orientation) return;
        this.orientation = orientation;
        applyTransformCss();
        firePropertyChange("orientation", old, orientation);
    }

    // ---- Inverted (CSS scaleX(-1)) ----

    public boolean getInverted() {
        return inverted;
    }

    public void setInverted(boolean b) {
        boolean old = this.inverted;
        if (old == b) return;
        this.inverted = b;
        applyTransformCss();
        firePropertyChange("inverted", old, b);
    }

    /**
     * Compose orientation + inverted into a single CSS {@code transform}
     * rule so neither setter leaks the other's state. Written as a whole
     * each time — cheaper than diffing which bit changed, and avoids the
     * "scaleX stuck because rotate was cleared" class of bug.
     */
    private void applyTransformCss() {
        StringBuilder sb = new StringBuilder();
        if (orientation == SwingConstants.VERTICAL) {
            sb.append("rotate(-90deg)");
        }
        if (inverted) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("scaleX(-1)");
        }
        if (sb.length() == 0) {
            getElement().getStyle().remove("transform");
        } else {
            getElement().getStyle().set("transform", sb.toString());
        }
    }

    // ---- Tick spacing (field storage; drives snapToTicks) ----

    public int getMajorTickSpacing() {
        return majorTickSpacing;
    }

    public void setMajorTickSpacing(int n) {
        int old = this.majorTickSpacing;
        if (old == n) return;
        this.majorTickSpacing = n;
        // No WARN — the spacing field round-trip is non-visual. Paint
        // rendering is gated on setPaintTicks(true), which WARNs.
        firePropertyChange("majorTickSpacing", old, n);
    }

    public int getMinorTickSpacing() {
        return minorTickSpacing;
    }

    public void setMinorTickSpacing(int n) {
        int old = this.minorTickSpacing;
        if (old == n) return;
        this.minorTickSpacing = n;
        firePropertyChange("minorTickSpacing", old, n);
    }

    // ---- snapToTicks (server-side rounding) ----

    public boolean getSnapToTicks() {
        return snapToTicks;
    }

    public void setSnapToTicks(boolean b) {
        boolean old = this.snapToTicks;
        if (old == b) return;
        this.snapToTicks = b;
        firePropertyChange("snapToTicks", old, b);
    }

    // ---- Deferred rendering (upstream ticket #9181) ----
    //
    // Tick marks, value labels, and track hiding all require addressable
    // CSS parts or slots on vaadin-slider that the component doesn't
    // currently expose. Fields round-trip + PCE fires so BeanInfo-style
    // probes work; a WARN on the visual-effect argument (true for the
    // paint flags, non-null for labelTable) surfaces the gap to migrators.
    // Upstream: https://github.com/vaadin/flow-components/issues/9181

    public boolean getPaintTicks() {
        return paintTicks;
    }

    public void setPaintTicks(boolean b) {
        boolean old = this.paintTicks;
        if (old == b) return;
        this.paintTicks = b;
        if (b) SHelper.onUnimplemented(this, "setPaintTicks", b);
        firePropertyChange("paintTicks", old, b);
    }

    public boolean getPaintTrack() {
        return paintTrack;
    }

    public void setPaintTrack(boolean b) {
        boolean old = this.paintTrack;
        if (old == b) return;
        this.paintTrack = b;
        // paintTrack defaults to true in Swing; the *false* setting is
        // the one that needs rendering support we don't have.
        if (!b) SHelper.onUnimplemented(this, "setPaintTrack", b);
        firePropertyChange("paintTrack", old, b);
    }

    public boolean getPaintLabels() {
        return paintLabels;
    }

    public void setPaintLabels(boolean b) {
        boolean old = this.paintLabels;
        if (old == b) return;
        this.paintLabels = b;
        if (b) SHelper.onUnimplemented(this, "setPaintLabels", b);
        firePropertyChange("paintLabels", old, b);
    }

    @SuppressWarnings("rawtypes")
    public Dictionary getLabelTable() {
        return labelTable;
    }

    @SuppressWarnings("rawtypes")
    public void setLabelTable(Dictionary labels) {
        Dictionary old = this.labelTable;
        this.labelTable = labels;
        if (labels != null) SHelper.onUnimplemented(this, "setLabelTable", labels);
        firePropertyChange("labelTable", old, labels);
    }

    /**
     * JDK builds a Hashtable of JLabel-per-major-tick. Rendering is
     * deferred (see class-level note); return an empty table so callers
     * iterating it don't NPE. Raw type matches JDK's own signature.
     */
    @SuppressWarnings("rawtypes")
    public Hashtable createStandardLabels(int increment) {
        SHelper.onUnimplemented(this, "createStandardLabels", increment);
        return new Hashtable();
    }

    /**
     * Two-arg overload. Same deferral as {@link #createStandardLabels(int)}.
     */
    @SuppressWarnings("rawtypes")
    public Hashtable createStandardLabels(int increment, int start) {
        SHelper.onUnimplemented(this, "createStandardLabels", increment, start);
        return new Hashtable();
    }

    // ---- ChangeListener fan-out ----
    //
    // Source=this so user casts `(SJSlider) e.getSource()` work as expected.
    // Lazy event allocation — one ChangeEvent per fire, shared across
    // listeners since the type carries no payload.

    public void addChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
    }

    public void removeChangeListener(ChangeListener l) {
        listenerList.remove(ChangeListener.class, l);
    }

    public ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(ChangeListener.class);
    }

    protected void fireStateChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : listenerList.getListeners(ChangeListener.class)) {
            l.stateChanged(event);
        }
    }

    /**
     * JDK hook for subclasses that want to swap the model-attached
     * listener. We allocate {@link #modelListener} directly in
     * {@link #installModel}; an override wouldn't reach our code path
     * today, but we expose this so a migrated subclass can call
     * {@code super.createChangeListener()} and get the real listener.
     */
    protected ChangeListener createChangeListener() {
        if (modelListener == null) {
            modelListener = e -> fanOutModelChange();
        }
        return modelListener;
    }

    // ---- L&F / UIClassID ----

    /**
     * JDK JSlider returns {@code "SliderUI"}. We don't drive a pluggable
     * {@code ComponentUI} (R_layouts_close_enough — Vaadin owns the DOM), but keep the ID so
     * migrated code that introspects via BeanInfo still finds the
     * expected value.
     */
    @Override
    public String getUIClassID() {
        return "SliderUI";
    }
}
