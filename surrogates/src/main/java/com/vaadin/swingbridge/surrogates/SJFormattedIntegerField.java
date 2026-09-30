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
import com.vaadin.flow.component.textfield.IntegerField;

import javax.swing.JFormattedTextField;
import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

/**
 * Surrogate for {@link javax.swing.JFormattedTextField} when the emulator's
 * formatter is a {@link javax.swing.text.NumberFormatter} bound to
 * {@code Integer.class} (D_jformattedtextfield IntegerStrategy). Extends Vaadin
 * {@link IntegerField} directly — peer's {@code Integer getValue()} is the
 * source of truth per [SD_formatted_native_value](../../../../../decisions.md#SD_formatted_native_value);
 * emulator-side coercion to the Object form lives on
 * {@code vaadinx.swing.IntegerStrategy} via
 * {@code SHelper.callSwing}-funneled value-change bridge.
 *
 * <p>R_vaadin_first drop-and-WARN inherited surface (columns / horizontalAlignment) is
 * documented in the JTextField surface duplicated below per SD_formatted_no_base_class — same
 * shape SJFormattedDatePicker takes for non-TextField peers.
 */
public class SJFormattedIntegerField extends IntegerField implements JComponentMixin, EnterClaims.Claimant {

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

    public static final int COMMIT           = JFormattedTextField.COMMIT;
    public static final int COMMIT_OR_REVERT = JFormattedTextField.COMMIT_OR_REVERT;
    public static final int REVERT           = JFormattedTextField.REVERT;
    public static final int PERSIST          = JFormattedTextField.PERSIST;

    private int focusLostBehavior = COMMIT_OR_REVERT;
    private boolean preventPeerEvents;
    private int columns;
    private int horizontalAlignment = javax.swing.SwingConstants.LEADING;
    private final EventListenerList listenerList = new EventListenerList();
    private String actionCommand;

    public SJFormattedIntegerField() {
        super();
        _installSwingClass();
        installValueChangeBridge();
        installEnterAsCommit();
    }

    public SJFormattedIntegerField(Integer initialValue) {
        this();
        if (initialValue != null) {
            super.setValue(initialValue);
        }
    }

    /**
     * Visibility-widening override of {@link IntegerField#validate} for
     * {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public {@code validate()}
     * contract. Same shape SJTextField takes per SD_sjtextfield.
     */
    @Override
    public void validate() {
        super.validate();
    }

    private void installValueChangeBridge() {
        addValueChangeListener(e -> {
            if (e.isFromClient()) EnterClaims.noteClientCommit(this);
            if (preventPeerEvents) return;
            SHelper.callSwing(() -> {
                firePropertyChange("value", e.getOldValue(), e.getValue());
                fireActionPerformed();
            });
        });
    }

    private void installEnterAsCommit() {
        addKeyPressListener(Key.ENTER,
                e -> SHelper.callSwing(this::fireActionPerformed));
    }

    /**
     * Integer-typed convenience setter with the {@link #preventPeerEvents}
     * guard so the value-change bridge doesn't double-fire PCE on the same
     * write. Pure-surrogate users may use the inherited {@code setValue}
     * directly; emulator-side IntegerStrategy goes through this path.
     */
    public void setIntValue(Integer value) {
        Integer old = super.getValue();
        if (old == null ? value == null : old.equals(value)) return;
        preventPeerEvents = true;
        try {
            super.setValue(value);
        } finally {
            preventPeerEvents = false;
        }
        firePropertyChange("value", old, value);
    }

    public int getFocusLostBehavior() {
        return focusLostBehavior;
    }

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

    public synchronized void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public synchronized void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    public synchronized ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

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

    @Override
    public String getUIClassID() {
        return "FormattedTextFieldUI";
    }

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
