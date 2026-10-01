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

import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.internal.ButtonStateStore;
import com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Surrogate for {@link javax.swing.JRadioButton}.
 * Extends {@link VaadinRadioButton} (our re-host of the public
 * {@code vaadin-radio-button} web component, since Vaadin's own
 * {@code RadioButton} Java wrapper is package-private) so it reaches
 * the migrator either through the {@code :emulators} {@code JRadioButton}
 * as peer, or as a standalone Vaadin radio button in its own right.
 *
 * <h2>Mirrors SJToggleButton's override surface (SD_toggle_checkbox_first_cut precedent)</h2>
 *
 * JDK's {@code JRadioButton extends JToggleButton} — both use
 * {@link JToggleButton.ToggleButtonModel}, both deliver ButtonModel-driven
 * Item / Change / Action fan-out, both render a toggleable widget that
 * fires {@code ValueChange} on browser-side click. The only difference
 * is the chosen peer (Vaadin {@code Checkbox} vs.
 * {@link VaadinRadioButton}) and the rendered glyph (square check vs.
 * circular radio). The peer hierarchy is parallel-not-shared (Vaadin
 * {@code Checkbox} and our {@link VaadinRadioButton} have no common
 * ancestor below {@code Component}), so this surrogate duplicates
 * SJToggleButton's overrides verbatim — same constraint that drove SD_sjpasswordfield
 * (SJPasswordField duplicating SJTextField).
 *
 * <h2>ButtonGroup mutex lives in the model it is given (D_emulator_button_model)</h2>
 *
 * The surrogate carries no awareness of any {@code ButtonGroup}. Under the
 * emulator-side {@code JRadioButton} + {@code vaadinx.swing.ButtonGroup}, this
 * surrogate renders a model whose {@code setSelected} consults the group: the
 * group deselects the previous selection's model, and that sibling's model→peer
 * push wire drives {@code checked=false} under {@link #preventPeerEvents} — the
 * same path as a programmatic {@code setSelected} from any other caller. A click
 * the model declines (the lone selection) is re-shown as the model's state.
 *
 * <p>Standalone (no group): clicks toggle on/off like a checkbox per the
 * underlying ToggleButtonModel pulse. The "radio stays selected when
 * clicked while already-selected" behaviour is a ButtonGroup-vetoes-deselect
 * effect that only materialises once an emulator-layer group is wired
 * around it.
 *
 * <h2>Override surface against {@link AbstractButtonMixin}</h2>
 *
 * Identical to SJToggleButton's list: {@link #installButtonBindings} (use
 * ToggleButtonModel + ValueChange wire), {@link #setModel} (extra
 * model→peer push), {@link #setText} / {@link #getText} (route through
 * peer's label slot, normalise null→""), {@link #setEnabled} (Java
 * "classes beat interfaces" redirect), layout setters drop-and-WARN
 * (shadow DOM doesn't honor host flex), {@link #getUIClassID} returns
 * {@code "RadioButtonUI"}.
 */
public class SJRadioButton extends VaadinRadioButton implements AbstractButtonMixin {

    /**
     * R_swing_is_truth feedback-loop guard. Set before writing to the peer (this
     * VaadinRadioButton's {@code setValue}) so the peer's
     * {@code ValueChangeListener} bails instead of round-tripping back
     * into the model. Same shape as SJToggleButton's identically-named
     * field.
     */
    private boolean preventPeerEvents;

    /**
     * Teardown handle for the model→peer push ItemListener installed in
     * {@link #setModel(ButtonModel)}. Same shape as SJToggleButton's
     * field of the same name — null when no model is installed.
     */
    private Registration peerSyncRegistration;

    public SJRadioButton() {
        this((String) null, false);
    }

    public SJRadioButton(String text) {
        this(text, false);
    }

    public SJRadioButton(String text, boolean selected) {
        super();
        _installSwingClass();
        installButtonBindings();
        if (text != null) setText(text);
        if (selected) setSelected(true);
    }

    public SJRadioButton(Icon icon) {
        this((String) null, false);
        setIcon(icon);
    }

    public SJRadioButton(Icon icon, boolean selected) {
        this((String) null, selected);
        setIcon(icon);
    }

    public SJRadioButton(String text, Icon icon) {
        this(text, false);
        setIcon(icon);
    }

    public SJRadioButton(String text, Icon icon, boolean selected) {
        this(text, selected);
        setIcon(icon);
    }

    public SJRadioButton(javax.swing.Action action) {
        // JDK pattern: this(); setAction(a). Chaining to the no-arg ctor
        // means installButtonBindings fires once and setAction does the
        // NAME / MNEMONIC / ICON / COMMAND / ENABLED copy afterwards.
        this();
        setAction(action);
    }

    /**
     * Override of the mixin's default. Same three differences from the
     * default as SJToggleButton: ToggleButtonModel as default,
     * {@code ValueChangeListener} on the peer (HasValue&lt;Boolean&gt;)
     * for browser-originated toggle, no inline-flex CSS seed (the
     * radio-button shadow DOM doesn't honor host {@code flex-*} either).
     * Idempotent.
     */
    @Override
    public void installButtonBindings() {
        ButtonStateStore s = ButtonStateStore.of(this);
        if (s.model != null) return;
        setModel(new JToggleButton.ToggleButtonModel());
        addValueChangeListener(e -> {
            if (preventPeerEvents) return;
            boolean newValue = Boolean.TRUE.equals(e.getValue());
            ButtonModel m = getModel();
            if (m == null || m.isSelected() == newValue) return;
            SHelper.callSwing(this::synthesizePeerClick);
            // A model that declines the toggle — a ButtonGroup keeping its lone selection —
            // leaves the glyph showing the click; show the model's state instead.
            if (m.isSelected() != newValue) {
                pushSelectedToPeer(m.isSelected());
            }
        });
    }

    /**
     * Override of {@link AbstractButtonMixin#setModel} to additionally
     * install a model→peer push ItemListener. Mirrors SJToggleButton's
     * identically-shaped override; catches direct
     * {@code myModel.setSelected(...)} mutations in addition to
     * mixin-routed ones.
     */
    @Override
    public void setModel(ButtonModel newModel) {
        if (peerSyncRegistration != null) {
            peerSyncRegistration.remove();
            peerSyncRegistration = null;
        }
        AbstractButtonMixin.super.setModel(newModel);
        if (newModel != null) {
            ItemListener pushToPeer = e -> {
                if (preventPeerEvents) return;
                boolean v = e.getStateChange() == ItemEvent.SELECTED;
                if (Boolean.TRUE.equals(getValue()) == v) return;
                // Allowed by R_tolerate_off_ui_thread because callback from model: ButtonModel ItemListener
                SHelper.runOnOwnerUI(this, () -> pushSelectedToPeer(v));
            };
            newModel.addItemListener(pushToPeer);
            peerSyncRegistration = () -> newModel.removeItemListener(pushToPeer);
            pushSelectedToPeer(newModel.isSelected());
        }
    }

    /** Shows {@code selected} on the glyph without the write echoing back as a click. */
    private void pushSelectedToPeer(boolean selected) {
        if (Boolean.TRUE.equals(getValue()) == selected) return;
        preventPeerEvents = true;
        try {
            setValue(selected);
        } finally {
            preventPeerEvents = false;
        }
    }

    // --- Text dispatch (mixin declares getText/setText abstract) -----
    //
    // VaadinRadioButton.setLabel manages a NativeLabel in the "label" slot.
    // Null reads back as null per HasLabel's contract; we normalise to ""
    // on the surrogate-facing getText per R_vaadin_first lossy (matches SJToggleButton).

    @Override
    public String getText() {
        String label = getLabel();
        return label == null ? "" : label;
    }

    @Override
    public void setText(String text) {
        String old = getText();
        setLabel(text == null ? "" : text);
        firePropertyChange("text", old, text);
    }

    /**
     * Java "classes beat interfaces" rule shadows the mixin chain's
     * setEnabled (which carries SD_auto_pce auto-PCE + propagates to model)
     * with {@code HasEnabled}'s default. Redirect explicitly so the
     * mixin chain runs.
     */
    @Override
    public void setEnabled(boolean enabled) {
        AbstractButtonMixin.super.setEnabled(enabled);
    }

    // --- Layout — drop-and-WARN per SD_toggle_checkbox_first_cut precedent. Validate per R_match_swing_errors
    // (IAE on invalid SwingConstants) then drop on non-default; default
    // value is silent. The vaadin-radio-button shadow DOM doesn't honor
    // host inline-flex CSS, same as Checkbox.

    @Override
    public int getHorizontalAlignment() {
        return SwingConstants.CENTER;  // JDK AbstractButton default
    }

    @Override
    public void setHorizontalAlignment(int alignment) {
        validateHorizontalKey(alignment, "horizontalAlignment");
        if (alignment != SwingConstants.CENTER) {
            SHelper.onUnimplemented(this, "setHorizontalAlignment", alignment);
        }
    }

    @Override
    public int getVerticalAlignment() {
        return SwingConstants.CENTER;  // JDK AbstractButton default
    }

    @Override
    public void setVerticalAlignment(int alignment) {
        validateVerticalKey(alignment, "verticalAlignment");
        if (alignment != SwingConstants.CENTER) {
            SHelper.onUnimplemented(this, "setVerticalAlignment", alignment);
        }
    }

    @Override
    public int getHorizontalTextPosition() {
        return SwingConstants.TRAILING;  // JDK AbstractButton default
    }

    @Override
    public void setHorizontalTextPosition(int pos) {
        validateHorizontalKey(pos, "horizontalTextPosition");
        if (pos != SwingConstants.TRAILING) {
            SHelper.onUnimplemented(this, "setHorizontalTextPosition", pos);
        }
    }

    @Override
    public int getVerticalTextPosition() {
        return SwingConstants.CENTER;  // JDK AbstractButton default
    }

    @Override
    public void setVerticalTextPosition(int pos) {
        validateVerticalKey(pos, "verticalTextPosition");
        if (pos != SwingConstants.CENTER) {
            SHelper.onUnimplemented(this, "setVerticalTextPosition", pos);
        }
    }

    @Override
    public int getIconTextGap() {
        return 4;  // JDK AbstractButton default
    }

    @Override
    public void setIconTextGap(int gap) {
        if (gap != 4) {
            SHelper.onUnimplemented(this, "setIconTextGap", gap);
        }
    }

    @Override
    public Insets getMargin() {
        return null;  // JDK AbstractButton default
    }

    private static void validateHorizontalKey(int key, String name) {
        if (key == SwingConstants.LEFT || key == SwingConstants.CENTER
                || key == SwingConstants.RIGHT || key == SwingConstants.LEADING
                || key == SwingConstants.TRAILING) return;
        throw new IllegalArgumentException(name);
    }

    private static void validateVerticalKey(int key, String name) {
        if (key == SwingConstants.TOP || key == SwingConstants.CENTER
                || key == SwingConstants.BOTTOM) return;
        throw new IllegalArgumentException(name);
    }

    // --- L&F class id ---

    @Override
    public String getUIClassID() {
        return "RadioButtonUI";
    }

    /**
     * JDK JRadioButton's paramString is essentially empty (returns
     * AbstractButton's). Mirrors SJToggleButton's empty body.
     */
    protected String paramString() {
        return "";
    }

    // --- Accessibility (deferred per emulator stance) ----------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
