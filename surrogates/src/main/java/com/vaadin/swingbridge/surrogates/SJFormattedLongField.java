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

import javax.swing.JFormattedTextField;
import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

/**
 * Surrogate for {@link javax.swing.JFormattedTextField} when the emulator's
 * formatter is a {@link javax.swing.text.NumberFormatter} bound to
 * {@code Long.class} (D_jformattedtextfield LongStrategy). Extends the surrogate-local
 * {@link LongField} (Long-typed sibling of Vaadin's IntegerField, ported
 * under SD_sjspinner to close the Long precision gap for SJSpinner).
 *
 * <p>Mirrors {@link SJFormattedIntegerField}'s shape — see that surrogate
 * for design notes; this one differs only in the peer's value type
 * (Long vs Integer).
 */
public class SJFormattedLongField extends LongField implements JComponentMixin, EnterClaims.Claimant {

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

    public SJFormattedLongField() {
        super();
        _installSwingClass();
        installValueChangeBridge();
        installEnterAsCommit();
    }

    public SJFormattedLongField(Long initialValue) {
        this();
        if (initialValue != null) {
            super.setValue(initialValue);
        }
    }

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

    public void setLongValue(Long value) {
        Long old = super.getValue();
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
