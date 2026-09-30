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

import com.vaadin.flow.component.datepicker.DatePicker;

import javax.swing.JFormattedTextField;
import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.util.Date;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

/**
 * Surrogate for {@link javax.swing.JFormattedTextField} when the emulator's
 * formatter selects {@link com.vaadin.swingbridge.surrogates.SJFormattedDatePicker}'s peer
 * family per [SD_sjformatted_family](../../../../../decisions.md#SD_sjformatted_family). Extends Vaadin
 * {@link DatePicker} directly (is-a) so the rendered UI is the native
 * Vaadin date picker — calendar popup + locale-driven typed-input parsing
 * — rather than a plain text field with format validation.
 *
 * <h2>R_swing_is_truth sync model — value source-of-truth is the peer</h2>
 *
 * <p>Per [SD_formatted_native_value](../../../../../decisions.md#SD_formatted_native_value) the surrogate's source
 * of truth for the value is the peer's native getter
 * ({@code DatePicker.getValue() : LocalDate}) — no shadow {@code Object}
 * field. {@link #getValue()} returns {@code BrowserDateUtils.toDate(super.getValue())}
 * (Date for JDK round-trip); {@link #setValue(Object)} accepts a JDK
 * {@link Date} (or {@link LocalDate} pass-through, or {@code null}),
 * coerces, and writes through to the peer.
 *
 * <p>{@link java.beans.PropertyChangeEvent}{@code ("value", oldDate, newDate)}
 * fires on every value change — both programmatic
 * ({@code setValue(Date)}) and peer-originated (browser date pick). Wired
 * via {@code DatePicker}'s {@link com.vaadin.flow.component.HasValue.ValueChangeEvent}
 * subscription, funneled through {@link SHelper#callSwing} per R_callswing_envelope. The PCE
 * carries Date values on both ends; the LocalDate→Date conversion runs
 * inside the Vaadin listener so PropertyChangeListeners see JDK types.
 *
 * <h2>ActionListener fan-out on Enter + value commit</h2>
 *
 * <p>JDK JFormattedTextField inherits ActionListener-on-Enter from
 * JTextField — for the SJFormattedDatePicker peer the natural
 * "commit" signals are (a) Enter on the input subfield and (b) browser
 * date pick (value-change). Both fire {@link ActionEvent} with
 * {@code source = this} and {@code actionCommand = ""} (no defined
 * action command for date input; pure-surrogate users can override
 * with {@link #setActionCommand(String)} if migrated logic needs one).
 *
 * <h2>R_vaadin_first drop-and-WARN surface</h2>
 *
 * <ul>
 *   <li>{@link #setColumns} / {@link #getColumns} — DatePicker has its
 *       own width sizing; setColumns has no peer concept. Field-shadow
 *       round-trip with WARN on non-zero set per
 *       [SD_formatted_no_base_class](../../../../../decisions.md#SD_formatted_no_base_class) (JTextField surface
 *       duplicated, drop-and-WARN where Vaadin lacks the concept).</li>
 *   <li>{@link #setHorizontalAlignment} — same as SJTextField's stance
 *       (Vaadin DatePicker shadow DOM doesn't expose host text-align).
 *       R_match_swing_errors IAE on bad axis preserved; non-LEADING values WARN.</li>
 *   <li>L&amp;F getUI / setUI — WARN-and-noop per the surrogate-wide stance
 *       (Vaadin owns the DOM).</li>
 * </ul>
 *
 * <p>{@code focusLostBehavior} + the {@code COMMIT} / {@code REVERT}
 * constants live inline rather than in a shared {@code SJFormattedFieldMixin}:
 * the surface is too small across the formatted-field family to warrant the
 * abstraction (R_infra_not_surface, [SD_sjformattedfieldmixin](../../../../../decisions.md#SD_sjformattedfieldmixin)).
 */
public class SJFormattedDatePicker extends DatePicker implements JComponentMixin, EnterClaims.Claimant {

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

    /** Mirrors {@link JFormattedTextField#COMMIT}. */
    public static final int COMMIT           = JFormattedTextField.COMMIT;
    public static final int COMMIT_OR_REVERT = JFormattedTextField.COMMIT_OR_REVERT;
    public static final int REVERT           = JFormattedTextField.REVERT;
    public static final int PERSIST          = JFormattedTextField.PERSIST;

    /** JDK default per JFormattedTextField javadoc. Field-shadow round-trip. */
    private int focusLostBehavior = COMMIT_OR_REVERT;

    /**
     * R_swing_is_truth feedback-loop guard for the LocalDate↔Date round-trip.
     * Programmatic {@link #setValue(Object)} sets this true before
     * {@code super.setValue(LocalDate)} so the inner ValueChange listener
     * doesn't re-fire {@code PropertyChangeEvent} on the same write.
     */
    private boolean preventPeerEvents;

    /**
     * Field-shadow for {@link #setColumns} / {@link #getColumns}. JDK JTextField
     * surface inherited from the JFormattedTextField lineage; DatePicker has
     * no peer concept, so only the round-trip survives.
     */
    private int columns;

    /** Field-shadow for {@link #setHorizontalAlignment} — same drop-and-WARN. */
    private int horizontalAlignment = javax.swing.SwingConstants.LEADING;

    /** Type-keyed listener list for ActionListener fan-out (Enter / value-pick). */
    private final EventListenerList listenerList = new EventListenerList();

    /** Action command. Public setter on JTextField but no public getter; exposed here for round-trip. */
    private String actionCommand;

    public SJFormattedDatePicker() {
        super();
        _installSwingClass();
        installValueChangeBridge();
        // No separate Enter-wiring step — Vaadin DatePicker doesn't implement
        // KeyNotifier, and Enter on the input already commits the calendar
        // popup (Vaadin native), which fires ValueChange, which routes
        // through {@link #installValueChangeBridge}'s fireActionPerformed.
    }

    public SJFormattedDatePicker(Date initialValue) {
        this();
        if (initialValue != null) {
            super.setValue(BrowserDateUtils.toLocalDate(initialValue));
        }
    }

    /**
     * Visibility-widening override of {@link DatePicker#validate} to satisfy
     * {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public {@code validate()}
     * contract. Vaadin DatePicker declares the inherited {@code validate()}
     * as {@code protected} (input validation, not Swing's layout cycle).
     * Same shape SJTextField takes per SD_sjtextfield.
     */
    @Override
    public void validate() {
        super.validate();
    }

    /**
     * Subscribe to peer-side ValueChangeEvent. Browser date picks (and
     * programmatic LocalDate writes from pure-surrogate users that bypass
     * our Object-typed setter) fire here; we coerce to Date and emit
     * {@link java.beans.PropertyChangeEvent}("value", oldDate, newDate).
     * Programmatic {@link #setValue(Object)} sets {@link #preventPeerEvents}
     * true around the peer write so it self-fires the PCE; this listener
     * bails to avoid double-fire.
     */
    private void installValueChangeBridge() {
        addValueChangeListener(e -> {
            if (e.isFromClient()) EnterClaims.noteClientCommit(this);
            if (preventPeerEvents) return;
            Date oldDate = BrowserDateUtils.toDate(e.getOldValue());
            Date newDate = BrowserDateUtils.toDate(e.getValue());
            SHelper.callSwing(() -> {
                firePropertyChange("value", oldDate, newDate);
                fireActionPerformed();
            });
        });
    }

    // --- Date-typed convenience (no Object setValue / getValue) ----------

    /**
     * R_vaadin_first + Vaadin generics: the inherited {@link DatePicker#getValue()
     * super.getValue()} returns {@link LocalDate}; an {@code Object getValue()}
     * override would erasure-clash with {@code HasValue<C, V>.getValue()}.
     * Pure-surrogate users read {@code super.getValue()} directly; emulator
     * code uses these convenience accessors plus {@link BrowserDateUtils#toLocalDate}
     * / {@link BrowserDateUtils#toDate} for Date↔LocalDate coercion at the boundary
     * per [SD_formatted_native_value](../../../../../decisions.md#SD_formatted_native_value) (peer's native type is
     * the source of truth).
     */
    public Date getDateValue() {
        return BrowserDateUtils.toDate(super.getValue());
    }

    /**
     * Date-typed setter that funnels through the peer's LocalDate setter
     * with {@link #preventPeerEvents} guard so the value-change bridge
     * doesn't double-fire {@code PropertyChangeEvent("value", ...)}. The
     * PCE fires here once with both old and new as JDK {@link Date} so
     * PropertyChangeListeners see consistent JDK types regardless of the
     * write origin.
     */
    public void setDateValue(Date value) {
        LocalDate target = BrowserDateUtils.toLocalDate(value);
        LocalDate oldLocal = super.getValue();
        if (oldLocal == null ? target == null : oldLocal.equals(target)) return;
        Date oldDate = BrowserDateUtils.toDate(oldLocal);
        Date newDate = BrowserDateUtils.toDate(target);
        preventPeerEvents = true;
        try {
            super.setValue(target);
        } finally {
            preventPeerEvents = false;
        }
        firePropertyChange("value", oldDate, newDate);
    }

    // --- focus-lost-behavior round-trip ----------------------------------

    public int getFocusLostBehavior() {
        return focusLostBehavior;
    }

    /**
     * Round-trip storage for the JDK {@code focusLostBehavior} int. The
     * actual focus-lost commit logic lives on the emulator-side strategy
     * (D_jformattedtextfield) — the surrogate stores the int so {@code getFocusLostBehavior}
     * round-trips and emulator code can read it back.
     */
    public void setFocusLostBehavior(int behavior) {
        if (behavior != COMMIT && behavior != COMMIT_OR_REVERT
                && behavior != REVERT && behavior != PERSIST) {
            throw new IllegalArgumentException(
                    "setFocusLostBehavior must be one of: JFormattedTextField.COMMIT, "
                            + "COMMIT_OR_REVERT, PERSIST or REVERT");
        }
        this.focusLostBehavior = behavior;
        // No "focusLostBehavior" property change: the JDK's
        // JFormattedTextField.setFocusLostBehavior validates the argument,
        // assigns the field and returns (SD_property_fanout_audit).
    }

    // --- ActionListener fan-out -------------------------------------------

    public synchronized void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public synchronized void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    public synchronized ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

    /**
     * Fire ActionEvent with source = this. actionCommand defaults to ""
     * (no natural text representation for a date — formatter-driven
     * stringification lives emulator-side).
     */
    protected void fireActionPerformed() {
        String cmd = (actionCommand != null) ? actionCommand : "";
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, cmd);
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            l.actionPerformed(e);
        }
    }

    public void setActionCommand(String command) {
        this.actionCommand = command;
    }

    // --- JTextField surface duplicated (drop-and-WARN per SD_formatted_no_base_class) ---------

    public int getColumns() {
        return columns;
    }

    /**
     * Field-shadow round-trip only — DatePicker has no columns concept.
     * Per [SD_formatted_no_base_class](../../../../../decisions.md#SD_formatted_no_base_class), the JTextField surface
     * is duplicated per surrogate and drops where Vaadin lacks the concept.
     * Negative throws IAE per R_match_swing_errors.
     */
    public void setColumns(int columns) {
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        int old = this.columns;
        if (old == columns) return;
        this.columns = columns;
        if (columns != 0) {
            SHelper.onUnimplemented(this, "setColumns", columns);
        }
        // No "columns" property change — see SJTextField.setColumns (SD_property_fanout_audit).
    }

    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(int alignment) {
        if (alignment != javax.swing.SwingConstants.LEFT
                && alignment != javax.swing.SwingConstants.CENTER
                && alignment != javax.swing.SwingConstants.RIGHT
                && alignment != javax.swing.SwingConstants.LEADING
                && alignment != javax.swing.SwingConstants.TRAILING) {
            throw new IllegalArgumentException("horizontalAlignment");
        }
        int old = this.horizontalAlignment;
        if (old == alignment) return;
        this.horizontalAlignment = alignment;
        if (alignment != javax.swing.SwingConstants.LEADING) {
            SHelper.onUnimplemented(this, "setHorizontalAlignment", alignment);
        }
        firePropertyChange("horizontalAlignment", old, alignment);
    }

    // --- L&F class id -----------------------------------------------------

    @Override
    public String getUIClassID() {
        return "FormattedTextFieldUI";
    }

    // --- Accessibility (deferred per surrogate-wide stance) --------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }

    /** The {@link SJFormattedTextField#claimsEnter} rule: Enter commits an edit and only an unedited field lets it through. */
    @Override
    public boolean claimsEnter() {
        return EnterClaims.committedThisRoundTrip(this);
    }
}
