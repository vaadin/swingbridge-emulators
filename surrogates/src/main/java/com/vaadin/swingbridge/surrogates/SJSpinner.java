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

import com.vaadin.swingbridge.surrogates.util.BrowserDateUtils;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Surrogate for {@link javax.swing.JSpinner} (SD_sjspinner). Extends Vaadin
 * {@link CustomField} and carries a swappable inner Vaadin field whose
 * concrete type is picked from the installed {@link SpinnerModel}'s
 * value type: {@link IntegerField} for {@code SpinnerNumberModel(Integer)},
 * {@link LongField} for {@code SpinnerNumberModel(Long)},
 * {@link NumberField} for {@code Double}/{@code Float}/{@code BigDecimal}/{@code BigInteger},
 * {@link DatePicker} for {@link SpinnerDateModel}, {@link Select} for
 * {@link SpinnerListModel}, and a read-only {@link TextField} fallback
 * for unknown {@link SpinnerModel} implementations (so a migrated app at
 * least boots instead of crashing at construction).
 *
 * <h2>Why CustomField, not a single leaf primitive</h2>
 *
 * JSpinner's API allows swapping the model to a different-typed one at
 * runtime ({@code spinner.setModel(new SpinnerDateModel())}); no single
 * Vaadin leaf primitive can morph from a numeric input into a date
 * picker into a select. {@code CustomField} is the Vaadin-native vehicle
 * for composing a value-carrying field from swappable child components —
 * on {@link #setModel} we tear down the old inner, add the new one, and
 * rewire the listener chain.
 *
 * <h2>Model flow</h2>
 *
 * The installed {@link SpinnerModel} is the source of truth (R_swing_is_truth). Our
 * {@link ChangeListener} on the model fans out to user
 * {@code ChangeListener}s, pushes the model's value / bounds / step
 * through to the inner field, and calls {@link CustomField#updateValue}
 * so {@code HasValue} observers see the change through Vaadin channels
 * too. Browser-originated edits land on our explicit
 * {@code HasValue} listener on the inner field (wrapped in
 * {@link SHelper#callSwing}, R_callswing_envelope); we coerce the peer-typed value back to
 * the model's type and write it through the model, which fires
 * {@code ChangeEvent} and closes the loop. The standard {@code preventPeerEvents}
 * flag (R_swing_is_truth) guards against peer→model→peer re-entry.
 *
 * <h2>Read-only propagation</h2>
 *
 * {@link #setReadOnly(boolean)} is the user-facing bit; the inner field's
 * read-only state is {@code userReadOnly || fallbackForcesReadOnly}. For
 * unknown-model-type fallback (see {@link #createInnerFieldFor}) the
 * TextField stays locked regardless, so a migrated app can't silently
 * accept unparseable typed input into a model we can't round-trip.
 *
 * <h2>Editor surface</h2>
 *
 * Deferred per [D_emulator_surrogate_split] — {@code getEditor} / {@code setEditor} /
 * {@code createEditor} all WARN and return null; {@code commitEdit} is a
 * no-op (the peer listener already mirrors edits into the model on
 * every value change). Migrated code that reaches through {@code getEditor()}
 * for {@code JFormattedTextField} features will see the WARN.
 */
public class SJSpinner extends CustomField<Object> implements JComponentMixin, EnterClaims.Claimant {

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

    private SpinnerModel spinnerModel;
    private ChangeListener modelListener;

    // The currently installed inner Vaadin field. Cast to HasValue or to
    // a specific concrete type at use sites. Null between construction's
    // super() and the first installModel() — guards accordingly.
    private Component innerField;
    private Registration innerValueReg;

    // True when the inner field is the unknown-model-type TextField
    // fallback. Propagates into isReadOnly()'s effective computation so
    // setReadOnly(false) still leaves the fallback locked.
    private boolean fallbackForcesReadOnly;

    // R_swing_is_truth feedback-loop guard — same pattern as SJSlider / JSpinner.
    // Set during pushModelToPeer so our syncValueFromPeer listener bails
    // instead of re-writing the model and thrashing.
    private boolean preventPeerEvents;

    // Last-applied date format pattern (yyyy-MM-dd / MM/dd/yyyy / …) when
    // the inner is a DatePicker. Stored on the surrogate so a subsequent
    // setModel(SpinnerDateModel) rebuild can re-apply it to the fresh
    // DatePicker — without this, swapping models would silently revert to
    // the locale default. Null means "no pattern set; use DatePicker
    // default". See {@link #setDatePattern(String)}.
    private String datePattern;

    // Swing-side ChangeListener fan-out; source=this so user casts
    // `(SJSpinner) e.getSource()` work. Lazy event allocation.
    private final EventListenerList listenerList = new EventListenerList();

    // ---- Constructors ----

    public SJSpinner() {
        this(new SpinnerNumberModel());
    }

    public SJSpinner(SpinnerModel model) {
        if (model == null) {
            throw new NullPointerException("model can't be null");
        }
        _installSwingClass();
        installModel(model);
    }

    // ---- Model plumbing ----

    /**
     * Attach the supplied model, swap the inner field if its type
     * changed, wire listeners, push current value to the peer, and tell
     * {@link CustomField} to re-read the value. Called from the ctor and
     * from {@link #setModel}.
     */
    private void installModel(SpinnerModel newModel) {
        if (this.spinnerModel != null && modelListener != null) {
            this.spinnerModel.removeChangeListener(modelListener);
        }
        this.spinnerModel = newModel;
        if (modelListener == null) {
            modelListener = e -> fanOutModelChange();
        }
        newModel.addChangeListener(modelListener);
        ensureInnerFieldMatches(newModel);
        pushModelToPeer();
        updateValue();
    }

    /**
     * Ensure the inner Vaadin field matches the model's value type. If
     * the current inner already matches, keep it (preserves any DOM
     * state / focus / styling). Otherwise remove the old inner, build a
     * fresh one, and rewire the peer-listener.
     */
    private void ensureInnerFieldMatches(SpinnerModel m) {
        Class<?> wanted = innerFieldClassFor(m);
        if (innerField != null && innerField.getClass() == wanted) {
            return;
        }
        if (innerField != null) {
            if (innerValueReg != null) {
                innerValueReg.remove();
                innerValueReg = null;
            }
            remove(innerField);
            innerField = null;
            fallbackForcesReadOnly = false;
        }
        CreatedField created = createInnerFieldFor(m);
        innerField = created.field;
        fallbackForcesReadOnly = created.forcesReadOnly;
        add(innerField);
        // Fill the CustomField wrapper: the wrapper is display:flex and the
        // inner field defaults to flex: 0 1 auto, so without this the inner
        // control keeps its intrinsic ~144px even when the CustomField is
        // stretched by a fill layout (GridBag/BorderLayout). Swing stretches
        // a spinner's editor with the spinner, so 100% width matches; a
        // standalone (unstretched) spinner's flex parent still shrinks to the
        // inner's intrinsic size, so this doesn't force an oversized field.
        ((HasSize) innerField).setWidthFull();
        innerValueReg = ((HasValue<?, ?>) innerField).addValueChangeListener(e -> {
            if (e.isFromClient()) EnterClaims.noteClientCommit(this);
            SHelper.callSwing(() -> syncValueFromPeer(e.getValue()));
        });
        applyReadOnlyToInner();
        // Re-apply a previously-set date pattern when the inner is a fresh
        // DatePicker — a setModel(SpinnerDateModel) → SpinnerDateModel swap
        // builds a new DatePicker, which would otherwise lose the i18n.
        if (datePattern != null && innerField instanceof DatePicker dp) {
            applyDatePatternToInner(dp, datePattern);
        }
    }

    private static Class<?> innerFieldClassFor(SpinnerModel m) {
        if (m instanceof SpinnerNumberModel snm) {
            Number n = snm.getNumber();
            if (n instanceof Integer) return IntegerField.class;
            if (n instanceof Long) return LongField.class;
            return NumberField.class;
        }
        if (m instanceof SpinnerDateModel) return DatePicker.class;
        if (m instanceof SpinnerListModel) return Select.class;
        return TextField.class;
    }

    private record CreatedField(Component field, boolean forcesReadOnly) {}

    private CreatedField createInnerFieldFor(SpinnerModel m) {
        if (m instanceof SpinnerNumberModel snm) {
            Number n = snm.getNumber();
            if (n instanceof Integer) {
                IntegerField f = new IntegerField();
                f.setStepButtonsVisible(true);
                return new CreatedField(f, false);
            }
            if (n instanceof Long) {
                LongField f = new LongField();
                f.setStepButtonsVisible(true);
                return new CreatedField(f, false);
            }
            NumberField f = new NumberField();
            f.setStepButtonsVisible(true);
            return new CreatedField(f, false);
        }
        if (m instanceof SpinnerDateModel) {
            return new CreatedField(new DatePicker(), false);
        }
        if (m instanceof SpinnerListModel slm) {
            Select<Object> s = new Select<>();
            s.setItems(listItemsOf(slm));
            return new CreatedField(s, false);
        }
        // Unknown SpinnerModel subtype. Fall back to a read-only TextField
        // showing model.getValue().toString() so the app boots instead of
        // NPE'ing at construction. The lock is intentional and non-negotiable
        // — we can't safely parse arbitrary typed input back for an unknown
        // model. WARN surfaces the gap to migrators.
        SHelper.onUnimplemented(this, "createInnerFieldFor/unknownSpinnerModel", m);
        TextField f = new TextField();
        return new CreatedField(f, true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Object> listItemsOf(SpinnerListModel slm) {
        List list = slm.getList();
        return new ArrayList<>(list);
    }

    // ---- Peer writes (model → inner field) ----

    /**
     * Push the model's current value (and, where applicable, bounds + step)
     * into the inner field. Guarded by {@link #preventPeerEvents} so the
     * inner's resulting value-change event doesn't re-enter
     * {@link #syncValueFromPeer} and thrash the model.
     */
    private void pushModelToPeer() {
        if (innerField == null) return;
        preventPeerEvents = true;
        try {
            Object v = spinnerModel.getValue();
            switch (innerField) {
                case IntegerField f -> {
                    if (spinnerModel instanceof SpinnerNumberModel snm) pushIntegerBounds(f, snm);
                    f.setValue(v instanceof Number n ? n.intValue() : null);
                }
                case LongField f -> {
                    if (spinnerModel instanceof SpinnerNumberModel snm) pushLongBounds(f, snm);
                    f.setValue(v instanceof Number n ? n.longValue() : null);
                }
                case NumberField f -> {
                    if (spinnerModel instanceof SpinnerNumberModel snm) pushDoubleBounds(f, snm);
                    f.setValue(v instanceof Number n ? n.doubleValue() : null);
                }
                case DatePicker dp -> dp.setValue(v instanceof Date d ? BrowserDateUtils.toLocalDate(d) : null);
                case Select<?> s -> {
                    @SuppressWarnings("unchecked")
                    Select<Object> so = (Select<Object>) s;
                    so.setValue(v);
                }
                case TextField tf -> tf.setValue(v == null ? "" : v.toString());
                default -> { /* shouldn't reach — innerFieldClassFor covers all branches */ }
            }
        } finally {
            preventPeerEvents = false;
        }
    }

    private static void pushIntegerBounds(IntegerField f, SpinnerNumberModel snm) {
        Comparable<?> min = snm.getMinimum();
        Comparable<?> max = snm.getMaximum();
        f.setMin(min instanceof Number n ? n.intValue() : Integer.MIN_VALUE);
        f.setMax(max instanceof Number n ? n.intValue() : Integer.MAX_VALUE);
        Number step = snm.getStepSize();
        int stepInt = step == null ? 1 : Math.max(1, step.intValue());
        f.setStep(stepInt);
    }

    private static void pushLongBounds(LongField f, SpinnerNumberModel snm) {
        Comparable<?> min = snm.getMinimum();
        Comparable<?> max = snm.getMaximum();
        f.setMin(min instanceof Number n ? n.longValue() : Long.MIN_VALUE);
        f.setMax(max instanceof Number n ? n.longValue() : Long.MAX_VALUE);
        Number step = snm.getStepSize();
        long stepLong = step == null ? 1L : Math.max(1L, step.longValue());
        f.setStep(stepLong);
    }

    // ---- Date pattern plumbing (item 18 / D_jspinner_editor) ----

    /**
     * Apply a JDK {@link java.text.SimpleDateFormat}-shaped pattern (e.g.
     * {@code "yyyy-MM-dd"}, {@code "MM/dd/yyyy"}) to the underlying Vaadin
     * {@link DatePicker} via {@link DatePicker#setI18n}. No-op when the
     * inner field isn't a DatePicker (the model isn't date-typed).
     *
     * <p>R_layouts_close_enough best-effort: Vaadin's {@code DatePickerI18n.setDateFormat}
     * accepts JDK-shaped patterns directly for the date components
     * {@code yyyy / MM / dd / d / M / yy}; time components in the pattern
     * (e.g. {@code "yyyy-MM-dd HH:mm"}) are dropped silently — Vaadin
     * DatePicker is date-only by construction. Mirrors SD_maskformatter_regex's
     * SJFormattedDatePicker stance.
     *
     * <p>Pattern is also stored on the surrogate so a subsequent
     * {@code setModel(SpinnerDateModel)} swap re-applies it to the freshly-
     * built DatePicker (see {@link #ensureInnerFieldMatches}).
     */
    public void setDatePattern(String pattern) {
        this.datePattern = pattern;
        if (innerField instanceof DatePicker dp) {
            applyDatePatternToInner(dp, pattern);
        }
        // Non-DatePicker inner: pattern stored, takes effect if/when the
        // model swaps to a SpinnerDateModel. No WARN — the user-side flow
        // (JSpinner.setEditor(DateEditor) on a non-date model) is already
        // a JDK contract violation surfaced at the emulator.
    }

    public String getDatePattern() {
        return datePattern;
    }

    private static void applyDatePatternToInner(DatePicker dp, String pattern) {
        if (pattern == null) {
            // Resetting pattern: drop the i18n override so Vaadin reverts
            // to its locale default. Setting null on setI18n is unsafe
            // (setI18n's javadoc: passing null restores defaults — verified
            // safe in Vaadin 25), but we go through a fresh i18n with no
            // overrides for clarity.
            dp.setI18n(new DatePicker.DatePickerI18n());
            return;
        }
        // Strip the time portion (everything from the first space onward)
        // — Vaadin DatePicker has no time component; SD_maskformatter_regex's stance.
        String dateOnly = pattern;
        int sp = dateOnly.indexOf(' ');
        if (sp > 0) dateOnly = dateOnly.substring(0, sp);
        try {
            DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
            i18n.setDateFormats(dateOnly);
            dp.setI18n(i18n);
        } catch (RuntimeException ex) {
            // Vaadin rejects the pattern (some unsupported letter, etc.).
            // R_match_swing_errors sub-bucket (c) — degrade to default, surface the gap.
            SHelper.onUnimplemented(dp, "DatePickerI18n.setDateFormats", pattern, ex.getMessage());
        }
    }

    private static void pushDoubleBounds(NumberField f, SpinnerNumberModel snm) {
        Comparable<?> min = snm.getMinimum();
        Comparable<?> max = snm.getMaximum();
        if (min instanceof Number n) f.setMin(n.doubleValue());
        if (max instanceof Number n) f.setMax(n.doubleValue());
        Number step = snm.getStepSize();
        if (step != null) {
            double s = step.doubleValue();
            if (s > 0) f.setStep(s);
        }
    }

    // ---- Peer reads (inner field → model via R_callswing_envelope) ----

    /**
     * Handler for the inner field's value-change event. Coerces the
     * peer-typed value back to the model's expected type and writes it
     * through the model, which fires {@code ChangeEvent} and closes the
     * loop. Out-of-range writes (model throws IAE) re-sync the peer back
     * to the model's current value so the browser snaps to a valid state.
     */
    private void syncValueFromPeer(Object peerValue) {
        if (preventPeerEvents) return;
        if (peerValue == null) return;
        Object modelValue = coerceToModelType(peerValue);
        if (modelValue == null) return;
        if (Objects.equals(modelValue, spinnerModel.getValue())) return;
        try {
            spinnerModel.setValue(modelValue);
        } catch (IllegalArgumentException ignored) {
            // Out-of-range per the model's constraints — revert the peer
            // to the last-good model value.
            pushModelToPeer();
        }
    }

    /**
     * Translate a value read off the inner Vaadin field into the type the
     * model expects. The DatePicker case unwraps LocalDate → Date via
     * system-default zone (UTC-less, same stance as JDK JSpinner which
     * uses calendar's default timezone). Numeric coercion follows the
     * {@code SpinnerNumberModel}'s current value type — e.g. a
     * {@code NumberField} returning {@code Double} rounds to
     * {@link BigDecimal} if the model is BigDecimal-backed. List / Select
     * values pass through unchanged.
     */
    private Object coerceToModelType(Object peerValue) {
        if (innerField instanceof DatePicker && peerValue instanceof LocalDate ld) {
            return BrowserDateUtils.toDate(ld);
        }
        if (innerField instanceof NumberField && peerValue instanceof Number n
                && spinnerModel instanceof SpinnerNumberModel snm) {
            Number current = snm.getNumber();
            if (current instanceof Float) return n.floatValue();
            if (current instanceof BigDecimal) return BigDecimal.valueOf(n.doubleValue());
            if (current instanceof BigInteger) return BigInteger.valueOf(n.longValue());
            return n.doubleValue();
        }
        return peerValue;
    }

    // ---- Model fan-out ----

    /**
     * Called whenever the model fires {@code ChangeEvent}. When the event
     * originated peer-side, {@link #preventPeerEvents} is set and we skip
     * the redundant push; otherwise push current value through, fire the
     * Swing-side ChangeEvent to user listeners, and tell
     * {@link CustomField} to re-read our value so HasValue observers see
     * the change.
     */
    private void fanOutModelChange() {
        // Allowed by R_tolerate_off_ui_thread because callback from model: SpinnerModel ChangeListener
        SHelper.runOnOwnerUI(this, () -> {
            if (!preventPeerEvents) {
                pushModelToPeer();
            }
            fireStateChanged();
            updateValue();
        });
    }

    // ---- CustomField contract ----

    @Override
    protected Object generateModelValue() {
        // Source of truth (R_swing_is_truth). Null-guard covers the early-construction
        // window between super() and installModel().
        return spinnerModel == null ? null : spinnerModel.getValue();
    }

    @Override
    protected void setPresentationValue(Object newPresentationValue) {
        // Route Binder-driven writes through the model so the single
        // source-of-truth invariant holds. Swallow null (CustomField can
        // call this with null during construction / reset).
        if (newPresentationValue == null) return;
        if (spinnerModel == null) return;
        if (Objects.equals(newPresentationValue, spinnerModel.getValue())) return;
        try {
            spinnerModel.setValue(newPresentationValue);
        } catch (IllegalArgumentException ignored) {
            // Binder won't stack-unwind cleanly on IAE here; drop the
            // update and let validation surface the mismatch instead.
        }
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        // Eclipse JDT's "cannot directly invoke the abstract method setReadOnly" on the next
        // line is a false positive — javac resolves it to the HasValueAndElement default that
        // AbstractField inherits. Don't work around it: a workaround compiles and passes too,
        // so nothing flags the regression.
        super.setReadOnly(readOnly);
        applyReadOnlyToInner();
    }

    /**
     * Effective inner read-only = user-facing bit OR fallback lock.
     * Called after every {@link #setReadOnly(boolean)} and after every
     * inner-field rebuild so the flag doesn't drift across setModel
     * boundaries.
     */
    private void applyReadOnlyToInner() {
        if (innerField == null) return;
        boolean effective = isReadOnly() || fallbackForcesReadOnly;
        ((HasValue<?, ?>) innerField).setReadOnly(effective);
    }

    // ---- JSpinner API surface ----

    /**
     * {@inheritDoc}
     *
     * <p>JSpinner-shape behaviour: route through the model so migrated
     * code sees the same contract as JDK {@link javax.swing.JSpinner} —
     * {@link IllegalArgumentException} on out-of-range,
     * {@link ClassCastException} on wrong type. We deliberately bypass
     * {@code CustomField}'s internal {@code setModelValue} path; the
     * subsequent model {@code ChangeEvent} fans out to
     * {@link #fanOutModelChange} which calls {@link #updateValue} and so
     * keeps CustomField's internal bookkeeping in sync.
     */
    @Override
    public void setValue(Object value) {
        // Construction window: spinnerModel is not yet set when
        // CustomField's own constructor calls setValue(null) to seed the
        // empty value. Fall back to the super path until installModel
        // runs.
        if (spinnerModel == null) {
            super.setValue(value);
            return;
        }
        spinnerModel.setValue(value);
    }

    @Override
    public Object getValue() {
        return spinnerModel == null ? super.getValue() : spinnerModel.getValue();
    }

    public SpinnerModel getModel() {
        return spinnerModel;
    }

    public void setModel(SpinnerModel newModel) {
        if (newModel == null) {
            throw new NullPointerException("model can't be null");
        }
        SpinnerModel old = this.spinnerModel;
        if (old == newModel) return;
        installModel(newModel);
        // JDK JSpinner fires this PCE; migrated MVC wiring may listen on it.
        firePropertyChange("model", old, newModel);
    }

    public Object getNextValue() {
        return spinnerModel.getNextValue();
    }

    public Object getPreviousValue() {
        return spinnerModel.getPreviousValue();
    }

    // ---- ChangeListener fan-out (identical shape to SJSlider) ----

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

    // ---- Editor surface (deferred per D_emulator_surrogate_split) ----
    // JDK JSpinner builds a JSpinner.NumberEditor / DateEditor / ListEditor
    // around a JFormattedTextField. We haven't ported JFormattedTextField
    // and the editor chrome is further :surrogates territory; these stay
    // WARN-and-return-null until a migration slice needs them.

    public Component getEditor() {
        SHelper.onUnimplemented(this, "getEditor");
        return null;
    }

    public void setEditor(Component editor) {
        SHelper.onUnimplemented(this, "setEditor", editor);
    }

    public void commitEdit() {
        // Peer edits already mirror into the model on every value change,
        // so the model is always up-to-date at call time. No-op matches
        // emulator behaviour; the JDK signature's ParseException is dropped
        // (we don't have an editor to parse through).
    }

    /**
     * The editor's rule: Swing's spinner editor is a {@code JFormattedTextField},
     * so Enter commits an edit and only an unedited editor lets it through.
     */
    @Override
    public boolean claimsEnter() {
        return EnterClaims.committedThisRoundTrip(this);
    }

    // ---- L&F stubs ----

    @Override
    public String getUIClassID() {
        return "SpinnerUI";
    }
}
