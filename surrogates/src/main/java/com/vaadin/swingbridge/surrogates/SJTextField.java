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

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.swingbridge.surrogates.swing.text.JTextComponentMixin;

import javax.swing.Action;
import javax.swing.SwingConstants;
import javax.swing.event.EventListenerList;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import com.vaadin.swingbridge.surrogates.util.CssConvert;

/**
 * Surrogate for {@link javax.swing.JTextField} (SD_sjtextfield). Extends Vaadin
 * {@link TextField} directly (is-a). Reaches the migrator either through
 * {@code :emulators.JTextField} as peer, or as a richer-than-stock Vaadin
 * component in its own right. First concrete consumer of
 * {@link JTextComponentMixin}; future SJPasswordField + SJTextArea
 * follow the same shape.
 *
 * <h2>Override surface against {@link JTextComponentMixin}</h2>
 *
 * <ul>
 *   <li><b>{@link #validate}</b> — visibility-widening override required
 *       by {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public
 *       {@code validate()} contract. Vaadin {@link TextField} declares
 *       the inherited {@code validate()} as {@code protected} (input
 *       validation, not Swing's layout cycle). Same shape SJToggleButton
 *       takes per SD_toggle_checkbox_first_cut.</li>
 * </ul>
 *
 * <h2>JTextField-specific surface</h2>
 *
 * <ul>
 *   <li><b>{@code columns}</b> — {@code setColumns(int)} writes
 *       {@code peer.setWidth} of a {@link CssConvert#columnsToCssWidth}
 *       character-column hint per R_layouts_close_enough close-enough; negative
 *       throws IAE per R_match_swing_errors.</li>
 *   <li><b>{@code horizontalAlignment}</b> — drop-and-WARN per SD_sjtextfield
 *       (Vaadin TextField shadow DOM doesn't honor host text-align).
 *       R_match_swing_errors IAE on bad axis preserved.</li>
 *   <li><b>ActionListener wiring on Enter</b> — Vaadin
 *       {@code addKeyPressListener(Key.ENTER, ...)} → fires
 *       {@link ActionEvent} with source = this and actionCommand
 *       falling back to text. Listener list lives on the concrete (not
 *       in the mixin store) since JTextArea doesn't have
 *       ActionListener support.</li>
 *   <li><b>{@code setAction(Action)}</b> — narrow propagation per JDK:
 *       {@code enabled} / {@code SHORT_DESCRIPTION} /
 *       {@code ACTION_COMMAND_KEY} only. Doesn't copy {@code NAME}
 *       (would overwrite form data) or icons or mnemonics.</li>
 *   <li><b>{@code getUIClassID() == "TextFieldUI"}</b>.</li>
 * </ul>
 */
public class SJTextField extends TextField implements JTextComponentMixin, EnterClaims.Claimant {

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

    /** Swing default: {@code columns=0} means "no preferred width". */
    private int columns;

    /** Swing default: {@code horizontalAlignment = LEADING}. */
    private int horizontalAlignment = SwingConstants.LEADING;

    /**
     * Public setter, no public getter on JTextField (Swing quirk). Used
     * to seed outgoing ActionEvent payload; null falls back to current
     * text per JDK.
     */
    private String actionCommand;

    /**
     * Type-keyed listener list for ActionListener fan-out. Lives on the
     * concrete since JTextArea doesn't have ActionListener support and
     * a future {@code SJTextArea extends TextArea implements JTextComponentMixin}
     * shouldn't carry this surface. Same shape SJButton's listenerList
     * lives on AbstractButtonMixin's store but there it's shared across
     * three button-lineage consumers.
     */
    private final EventListenerList listenerList = new EventListenerList();

    /** Currently installed {@link Action}; null when none. */
    private Action action;

    /** Listener on {@link #action} that propagates per-property mutations. */
    private PropertyChangeListener actionPropertyChangeListener;

    public SJTextField() {
        this(null, null, 0);
    }

    public SJTextField(String text) {
        this(null, text, 0);
    }

    public SJTextField(int columns) {
        this(null, null, columns);
    }

    public SJTextField(String text, int columns) {
        this(null, text, columns);
    }

    public SJTextField(Document doc, String text, int columns) {
        // Root public ctor — all others funnel here. Order matters:
        // installTextComponentBindings (mixin's R_swing_is_truth sync wiring) MUST
        // run before any setText / setDocument so the listener is live
        // when the initial text reaches the Document. JTextField-specific
        // wiring (Enter → ActionEvent) installs after.
        super();
        _installSwingClass();
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        installTextComponentBindings();
        installEnterWiring();
        if (doc != null) {
            setDocument(doc);
        }
        this.columns = columns;
        applyColumnsToPeer();
        if (text != null) {
            setText(text);
        }
    }

    /**
     * Wire Enter → ActionEvent fan-out. Same shape :emulators.JTextField
     * uses (Vaadin Key.ENTER addKeyPressListener); this lives only on
     * SJTextField since JTextArea's Enter inserts a newline rather than
     * submitting (that's why this isn't lifted into the mixin).
     */
    private void installEnterWiring() {
        addKeyPressListener(Key.ENTER,
                e -> SHelper.callSwing(this::fireActionPerformed));
    }

    /**
     * Visibility-widening override of {@link TextField#validate} to
     * satisfy {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public
     * {@code validate()} contract. Delegates to super so Vaadin's
     * input-validation behaviour is preserved. Same SJToggleButton
     * pattern per SD_toggle_checkbox_first_cut (method-name clash between Swing's "validate the
     * layout" and Vaadin's "validate the input value" — same name,
     * different concerns).
     */
    @Override
    public void validate() {
        super.validate();
    }

    // --- Columns ------------------------------------------------------

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        int old = this.columns;
        if (old == columns) return;
        this.columns = columns;
        applyColumnsToPeer();
        // No "columns" property change: the JDK's JTextField.setColumns (and
        // JTextArea's) assigns the field and calls invalidate(). It is a layout
        // hint, not a bound property (SD_property_fanout_audit).
    }

    /**
     * Set peer width from the columns hint; see {@link CssConvert#columnsToCssWidth}.
     * Behind the {@code D_layout_owns_child_sizing} variable because {@code columns} is
     * a <em>preferred</em> width in Swing (pref size via FontMetrics), so a parent
     * layout that sizes the axis itself — a {@code GridBagLayout} cell with
     * {@code fill=HORIZONTAL}, a {@code BorderLayout} NORTH region — overrides it
     * exactly as it overrides {@code setPreferredSize}.
     */
    private void applyColumnsToPeer() {
        setWidth(com.vaadin.swingbridge.surrogates.util.LayoutCss.prefWidth(CssConvert.columnsToCssWidth(columns)));
    }

    // --- Horizontal alignment (drop-and-WARN per SD_sjtextfield) ---------------

    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(int alignment) {
        // R_match_swing_errors IAE on bad axis preserved.
        if (alignment != SwingConstants.LEFT && alignment != SwingConstants.CENTER
                && alignment != SwingConstants.RIGHT && alignment != SwingConstants.LEADING
                && alignment != SwingConstants.TRAILING) {
            throw new IllegalArgumentException("horizontalAlignment");
        }
        int old = this.horizontalAlignment;
        if (old == alignment) return;
        this.horizontalAlignment = alignment;
        // Vaadin TextField shadow DOM doesn't honor host text-align — drop
        // and WARN on non-default per R_vaadin_first. Default LEADING is silent so
        // setAction's clear-to-default sweep doesn't trip WARNs.
        if (alignment != SwingConstants.LEADING) {
            SHelper.onUnimplemented(this, "setHorizontalAlignment", alignment);
        }
        firePropertyChange("horizontalAlignment", old, alignment);
    }

    // --- ActionListener fan-out --------------------------------------

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
     * Fire ActionEvent with source = this and actionCommand falling back
     * to current text. EventListenerList.getListeners returns LIFO order
     * already; iterate directly. Public entry point for harness code is
     * {@link #postActionEvent}.
     */
    protected void fireActionPerformed() {
        String cmd = (actionCommand != null) ? actionCommand : getText();
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, cmd);
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            l.actionPerformed(e);
        }
    }

    public void setActionCommand(String command) {
        this.actionCommand = command;
    }

    /** Public synthesizer matching JDK — useful when not Enter-triggered. */
    public void postActionEvent() {
        fireActionPerformed();
    }

    // --- Action wiring (narrow per SD_sjtextfield / JDK JTextField) ------------

    public Action getAction() {
        return action;
    }

    public void setAction(Action a) {
        Action oldValue = this.action;
        if (oldValue == null ? a == null : oldValue.equals(a)) return;
        this.action = a;
        if (oldValue != null) {
            removeActionListener(oldValue);
            if (actionPropertyChangeListener != null) {
                oldValue.removePropertyChangeListener(actionPropertyChangeListener);
                actionPropertyChangeListener = null;
            }
        }
        configurePropertiesFromAction(a);
        if (a != null) {
            if (!isActionListener(a)) {
                addActionListener(a);
            }
            actionPropertyChangeListener = createActionPropertyChangeListener(a);
            a.addPropertyChangeListener(actionPropertyChangeListener);
        }
        firePropertyChange("action", oldValue, a);
    }

    private boolean isActionListener(ActionListener a) {
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            if (l == a) return true;
        }
        return false;
    }

    /**
     * JDK JTextField's narrow Action property set: {@code enabled} /
     * {@code SHORT_DESCRIPTION} / {@code ACTION_COMMAND_KEY}. No NAME
     * propagation (would overwrite form data); no icons or mnemonics.
     */
    protected void configurePropertiesFromAction(Action a) {
        setEnabled(a == null || a.isEnabled());
        setToolTipText(a == null ? null : (String) a.getValue(Action.SHORT_DESCRIPTION));
        setActionCommand(a == null ? null : (String) a.getValue(Action.ACTION_COMMAND_KEY));
    }

    protected PropertyChangeListener createActionPropertyChangeListener(Action a) {
        return new TextFieldActionPropertyChangeListener(this, a);
    }

    private static final class TextFieldActionPropertyChangeListener
            implements PropertyChangeListener {
        private final SJTextField field;
        private final Action action;

        TextFieldActionPropertyChangeListener(SJTextField field, Action action) {
            this.field = field;
            this.action = action;
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            field.actionPropertyChanged(action, e.getPropertyName());
        }
    }

    protected void actionPropertyChanged(Action a, String propertyName) {
        if ("enabled".equals(propertyName)) {
            setEnabled(a.isEnabled());
        } else if (Action.SHORT_DESCRIPTION.equals(propertyName)) {
            setToolTipText((String) a.getValue(Action.SHORT_DESCRIPTION));
        } else if (Action.ACTION_COMMAND_KEY.equals(propertyName)) {
            setActionCommand((String) a.getValue(Action.ACTION_COMMAND_KEY));
        }
        // NAME / MNEMONIC_KEY / icons intentionally dropped per JDK
        // JTextField's narrower-than-AbstractButton contract.
    }

    /**
     * Enter posts an {@code ActionEvent} when anyone listens for one — Swing's
     * {@code notify-field-accept}, enabled by {@code hasActionListener()}. An
     * {@link #setAction} counts: it installs the action as a listener.
     */
    @Override
    public boolean claimsEnter() {
        return getActionListeners().length > 0;
    }

    // --- L&F class id ------------------------------------------------

    @Override
    public String getUIClassID() {
        return "TextFieldUI";
    }

    /**
     * JDK JTextField.paramString chains AbstractButton-style; we skip
     * the full chain (Vaadin Component.toString is the honest host-side
     * equivalent) and report the JTextField-specific tail.
     */
    protected String paramString() {
        return "columns=" + columns
                + ",command=" + (actionCommand == null ? "" : actionCommand)
                + ",horizontalAlignment=" + horizontalAlignment;
    }

    // --- Accessibility (deferred per surrogate-wide stance) ----------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
