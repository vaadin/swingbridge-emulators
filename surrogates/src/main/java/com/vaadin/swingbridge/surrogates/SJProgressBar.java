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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;
import com.vaadin.swingbridge.surrogates.util.VaadinUtils;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.text.NumberFormat;
import java.util.Objects;

/**
 * Surrogate for {@link javax.swing.JProgressBar}.
 * Extends Vaadin {@link ProgressBar} directly (is-a) so it reaches the
 * migrator either through the {@code :emulators} {@code JProgressBar}
 * as peer, or as a richer-than-stock Vaadin component in its own right.
 *
 * <h2>Model flow (SJSlider precedent)</h2>
 *
 * The installed {@link BoundedRangeModel} is the source of truth. A
 * single {@link ChangeListener} on the model fans out to user
 * ChangeListeners and pushes value changes to the peer (this
 * ProgressBar's own element). Unlike SJSlider, Vaadin ProgressBar is
 * display-only — the browser can't drag a progress bar back, so there
 * is no peer-originated value-change to mirror, no R_callswing_envelope
 * {@code SHelper.callSwing} wrapping, and no {@code preventPeerEvents}
 * feedback-loop guard (the loop it defends against can't physically
 * happen).
 *
 * <h2>VERTICAL: drop-and-WARN (R_vaadin_first)</h2>
 *
 * {@code VERTICAL} is field-stored and the PCE fires, but the visual
 * stays horizontal. CSS {@code transform: rotate(-90deg)} was tried
 * first (SJSlider precedent) and rejected: a transform only rotates
 * the element's <em>visual</em>; its layout box keeps its pre-rotation
 * dimensions, so dropping a rotated ProgressBar into any non-fixed-size
 * container (BorderLayout.WEST, BoxLayout column, …) collapses it to
 * zero width because Vaadin ProgressBar has no intrinsic width — it
 * relies on its parent to stretch it. Making VERTICAL truly work needs
 * either a wrapper element with explicitly-sized rotated dimensions
 * (incompatible with surrogate is-a-ProgressBar — user code calls
 * {@link #getElement()} and expects the bar itself) or a shadow-DOM-aware
 * rewrite of Vaadin's internal {@code width: calc(progress * 100%)}
 * fill machinery. Neither is JLawyer-needed; revisit if a migration
 * target surfaces a real VERTICAL JProgressBar.
 *
 * <h2>{@code setString} / {@code setStringPainted} via CSS overlay</h2>
 *
 * Vaadin {@code <vaadin-progress-bar>} has no per-bar text slot. We
 * render the user-set string (or the JDK percent default) through a
 * {@code ::after} pseudo-element reading a {@code data-emul-progress-string}
 * attribute on the host. The CSS rule is injected once per UI via
 * {@link #ensureProgressStringStyleInjected}; SJProgressBar just
 * toggles the attribute. Centered text crossing the fill boundary may
 * have low contrast on the filled half — accepted R_layouts_close_enough close-enough,
 * refined if a real-app migration target complains.
 *
 * <h2>Threading</h2>
 *
 * UI-thread-confined, with one exception: the installed {@link BoundedRangeModel} may be
 * mutated from any thread. A worker reporting progress hops onto this bar's UI thread for
 * each model change and blocks until the fan-out has run, so the peer write and every
 * ChangeListener run on the UI thread; while the bar is detached, inline on the worker
 * (SD_background_model_hop):
 *
 * <pre>{@code
 * SJProgressBar bar = new SJProgressBar(0, files.size());
 * layout.add(bar);
 * executor.submit(() -> {
 *     for (int i = 0; i < files.size(); i++) {
 *         importFile(files.get(i));
 *         bar.getModel().setValue(i + 1);
 *     }
 * });
 * }</pre>
 */
public class SJProgressBar extends ProgressBar implements JComponentMixin {

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

    // ---- BoundedRangeModel state (source of truth, R_swing_is_truth) ----

    private BoundedRangeModel barModel;
    private ChangeListener modelListener;

    // EventListenerList for ChangeListener fan-out. Same shape as
    // SJSlider's — lazy event allocation, type-keyed arrays.
    private final EventListenerList listenerList = new EventListenerList();

    // ---- Swing-side appearance state ----
    //
    // Field-stored regardless of whether the visual effect is wired,
    // so BeanInfo-style introspection round-trips for migrated code
    // that probes its own state.

    private int orientation = SwingConstants.HORIZONTAL;
    private boolean stringPainted;
    private String progressString;     // null = render the JDK percent default
    private boolean borderPainted = true;

    // ---- Constructors ----
    //
    // Five ctors, mirroring the JDK JProgressBar surface. The three-arg
    // (orientation, min, max) form is the root — orientation validation,
    // model installation, and the percent format defaulting all happen
    // in one place.

    public SJProgressBar() {
        this(SwingConstants.HORIZONTAL, 0, 100);
    }

    public SJProgressBar(int orientation) {
        this(orientation, 0, 100);
    }

    public SJProgressBar(int min, int max) {
        this(SwingConstants.HORIZONTAL, min, max);
    }

    public SJProgressBar(int orientation, int min, int max) {
        super();
        _installSwingClass();
        // Validate orientation before any mutable state touches — D_never_fail_on_gaps.
        setOrientation(orientation);
        // JDK JProgressBar's (orient, min, max) form seeds the model
        // with value=min (DefaultBoundedRangeModel(min, 0, min, max)).
        // The no-arg / (orient)-only forms route through here with
        // min=0, so value still defaults to 0 for the JDK-default range.
        installModel(new DefaultBoundedRangeModel(min, 0, min, max));
    }

    public SJProgressBar(BoundedRangeModel brm) {
        super();
        _installSwingClass();
        installModel(brm);
    }

    // ---- Model plumbing ----
    //
    // No peer listener: Vaadin ProgressBar is display-only.
    // No preventPeerEvents guard: with no peer→Swing path, the feedback
    // loop SJSlider defends against can't form.

    private void installModel(BoundedRangeModel newModel) {
        if (this.barModel != null && modelListener != null) {
            this.barModel.removeChangeListener(modelListener);
        }
        this.barModel = newModel;
        if (modelListener == null) {
            modelListener = e -> fanOutModelChangeOnUI();
        }
        newModel.addChangeListener(modelListener);
        super.setMin(newModel.getMinimum());
        super.setMax(newModel.getMaximum());
        pushValueToPeer(newModel.getValue());
        updateStringOverlay();
    }

    /** The model may be mutated off the UI thread (SD_background_model_hop). */
    private void fanOutModelChangeOnUI() {
        // Allowed by R_tolerate_off_ui_thread because callback from model: BoundedRangeModel ChangeListener
        SHelper.runOnOwnerUI(this, this::fanOutModelChange);
    }

    /**
     * Dispatch one ChangeEvent (source=this) to every registered user
     * ChangeListener, push the model's current value to the peer, and
     * refresh the string overlay (percent default may have shifted).
     */
    private void fanOutModelChange() {
        pushRangeToPeer();
        pushValueToPeer(barModel.getValue());
        updateStringOverlay();
        fireStateChanged();
    }

    private void pushValueToPeer(int value) {
        super.setValue(value);
    }

    /**
     * Mirrors the model's range onto the peer. Here rather than in {@link #setMinimum} /
     * {@link #setMaximum} so that a change made on the model directly reaches the peer too.
     */
    private void pushRangeToPeer() {
        if (getMin() != barModel.getMinimum()) super.setMin(barModel.getMinimum());
        if (getMax() != barModel.getMaximum()) super.setMax(barModel.getMaximum());
    }

    // ---- Swing-style value / min / max API ----
    //
    // getValue() clashes with Vaadin ProgressBar's primitive double
    // getValue() on return type — Vaadin-first wins per SD_vaadin_first_binding. Users who
    // need the int route through getModel().getValue(). setValue(int)
    // coexists with super.setValue(double) as an overload.

    public void setValue(int n) {
        barModel.setValue(n);
    }

    public int getMinimum() {
        return barModel.getMinimum();
    }

    /**
     * Fires no {@code "minimum"} property change: the JDK's
     * {@code JProgressBar.setMinimum} is {@code getModel().setMinimum(n)} and
     * the notification is the model's {@code ChangeEvent} (SD_property_fanout_audit). Contrast
     * {@code JSlider}, whose own {@code setMinimum} does fire.
     */
    public void setMinimum(int minimum) {
        barModel.setMinimum(minimum);
    }

    public int getMaximum() {
        return barModel.getMaximum();
    }

    /** Fires no {@code "maximum"} property change — see {@link #setMinimum} (SD_property_fanout_audit). */
    public void setMaximum(int maximum) {
        barModel.setMaximum(maximum);
    }

    public BoundedRangeModel getModel() {
        return barModel;
    }

    /**
     * Fires no {@code "model"} property change. {@code JProgressBar} is the
     * exception in its family: {@code AbstractButton}, {@code JSlider},
     * {@code JTable}, {@code JList}, {@code JTree}, {@code JComboBox} and
     * {@code JSpinner} all fire {@code "model"}, but
     * {@code JProgressBar.setModel} fires only
     * {@code AccessibleContext.ACCESSIBLE_VALUE_PROPERTY} — on the accessible
     * context's own listener list, which {@code addPropertyChangeListener} here
     * never reaches (SD_property_fanout_audit).
     */
    public void setModel(BoundedRangeModel newModel) {
        if (this.barModel == newModel) return;
        installModel(newModel);
    }

    /**
     * Fraction in {@code [0.0, 1.0]} indicating model progress. A span of zero
     * ({@code max == min}) reads as zero, so the overlay shows {@code 0%}; the JDK
     * answers {@code NaN} there.
     */
    public double getPercentComplete() {
        long span = (long) barModel.getMaximum() - (long) barModel.getMinimum();
        if (span <= 0) return 0.0;
        return (double) (barModel.getValue() - barModel.getMinimum()) / span;
    }

    // ---- Indeterminate (Vaadin direct + PCE) ----

    @Override
    public void setIndeterminate(boolean indeterminate) {
        boolean old = isIndeterminate();
        super.setIndeterminate(indeterminate);
        if (old != indeterminate) {
            firePropertyChange("indeterminate", old, indeterminate);
        }
    }

    // ---- Orientation (R_vaadin_first drop-and-WARN on VERTICAL — see class javadoc) ----

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
        // VERTICAL field-round-trips and fires PCE, but the visual stays
        // horizontal — see class javadoc for the layout-box reasoning.
        if (orientation == SwingConstants.VERTICAL) {
            SHelper.onUnimplemented(this, "setOrientation", "VERTICAL");
        }
        firePropertyChange("orientation", old, orientation);
    }

    // ---- setString / setStringPainted (CSS ::after overlay) ----

    public boolean isStringPainted() {
        return stringPainted;
    }

    public void setStringPainted(boolean b) {
        boolean old = this.stringPainted;
        if (old == b) return;
        this.stringPainted = b;
        updateStringOverlay();
        firePropertyChange("stringPainted", old, b);
    }

    /**
     * The string to render when {@link #isStringPainted()} is true.
     * Defaults to the JDK percent format ({@code "33%"}) when no string
     * is set — mirrors {@code JProgressBar.getString} which composes
     * the locale-aware percent via {@link NumberFormat#getPercentInstance}.
     */
    public String getString() {
        if (progressString != null) return progressString;
        return NumberFormat.getPercentInstance().format(getPercentComplete());
    }

    public void setString(String s) {
        String old = this.progressString;
        if (Objects.equals(old, s)) return;
        this.progressString = s;
        updateStringOverlay();
        firePropertyChange("string", old, s);
    }

    /**
     * Push the current string state onto the host element. When
     * {@link #stringPainted} is true, the {@code data-emul-progress-string}
     * attribute carries the value; otherwise it's cleared so the
     * pseudo-element selector won't match. The one-shot per-UI CSS rule
     * is injected on the first call.
     */
    private static final String PROGRESS_STRING_CSS = """
            vaadin-progress-bar[data-emul-progress-string] {
                position: relative;
            }
            vaadin-progress-bar[data-emul-progress-string]::after {
                content: attr(data-emul-progress-string);
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                font-size: 0.875em;
                line-height: 1;
                pointer-events: none;
                white-space: nowrap;
            }
            """;

    private static final String PROGRESS_STRING_STYLE_KEY = "emul.progress-string-style-element";

    /**
     * Ensure the {@code vaadin-progress-bar[data-emul-progress-string]::after}
     * rule is installed on the current page so the text overlay can render.
     * Thin wrapper over {@link VaadinUtils#injectStyleOnce} (works under
     * {@code @PreserveOnRefresh}, no-op when no UI is current). Component-local:
     * this {@code ::after} overlay is used only by SJProgressBar, so it lives
     * here rather than in a shared helper.
     */
    private static void ensureProgressStringStyleInjected(UI ui) {
        VaadinUtils.injectStyleOnce(ui, PROGRESS_STRING_STYLE_KEY, "progress-string", PROGRESS_STRING_CSS);
    }

    private void updateStringOverlay() {
        ensureProgressStringStyleInjected(UI.getCurrent());
        if (stringPainted) {
            getElement().setAttribute("data-emul-progress-string", getString());
        } else {
            getElement().removeAttribute("data-emul-progress-string");
        }
    }

    // ---- setBorderPainted (R_vaadin_first drop-and-WARN; field round-trips) ----

    public boolean isBorderPainted() {
        return borderPainted;
    }

    public void setBorderPainted(boolean b) {
        boolean old = this.borderPainted;
        if (old == b) return;
        this.borderPainted = b;
        // Swing default is true; the false setting is the one without
        // a Vaadin counterpart.
        if (!b) SHelper.onUnimplemented(this, "setBorderPainted", b);
        firePropertyChange("borderPainted", old, b);
    }

    // ---- ChangeListener fan-out ----
    //
    // Source=this so user casts `(SJProgressBar) e.getSource()` work.
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
     * listener. Mirrors SJSlider's same-named hook — modelListener is
     * created directly in installModel; an override wouldn't reach our
     * code path today, but a migrated subclass calling
     * {@code super.createChangeListener()} gets the real listener.
     */
    protected ChangeListener createChangeListener() {
        if (modelListener == null) {
            modelListener = e -> fanOutModelChangeOnUI();
        }
        return modelListener;
    }

    // ---- L&F / UIClassID ----

    /**
     * JDK JProgressBar returns {@code "ProgressBarUI"}. We don't drive
     * a pluggable {@code ComponentUI} (R_layouts_close_enough — Vaadin owns the DOM), but
     * keep the ID so migrated code that introspects via BeanInfo still
     * finds the expected value.
     */
    @Override
    public String getUIClassID() {
        return "ProgressBarUI";
    }
}
