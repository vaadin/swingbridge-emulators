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

import com.vaadin.flow.component.checkbox.Checkbox;
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
 * Surrogate for {@link javax.swing.JCheckBox} (SD_toggle_checkbox_first_cut, restructured SD_sjtogglebutton_button_peer).
 * Extends Vaadin {@link Checkbox} directly (is-a), rendering a checkbox
 * glyph — JCheckBox's actual Swing look. Reaches the migrator either
 * through {@code :emulators.JCheckBox} as peer, or as a richer-than-stock
 * Vaadin checkbox in its own right.
 *
 * <h2>Standalone, not {@code extends SJToggleButton}</h2>
 *
 * SJCheckBox stands alone over {@link Checkbox} + {@link AbstractButtonMixin}
 * rather than extending {@code SJToggleButton}, and duplicates the
 * Checkbox-peer toggle surface: SJToggleButton peers over a
 * {@link com.vaadin.flow.component.button.Button} for pressed chrome (SD_sjtogglebutton_button_peer),
 * so there is no shared Checkbox-peer basis to inherit. Same
 * parallel-not-shared duplication {@link SJRadioButton} carries (SD_sjpasswordfield) —
 * Vaadin {@code Checkbox}, {@code Button}, and our {@code VaadinRadioButton}
 * share no ancestor below {@code Component}, so a toggle over each needs its
 * own peer-bound wiring.
 *
 * <h2>Override surface against {@link AbstractButtonMixin}</h2>
 *
 * <ul>
 *   <li><b>{@link #installButtonBindings}</b> — installs
 *       {@link JToggleButton.ToggleButtonModel} (toggle semantics) and a
 *       {@code HasValue<Boolean>} {@code ValueChangeListener} (covers
 *       mouse AND keyboard-Space through one path); skips the mixin's
 *       inline-flex CSS seed (Checkbox shadow DOM doesn't honor host
 *       flex).</li>
 *   <li><b>{@link #setModel}</b> — adds a model→peer push ItemListener
 *       mirroring {@code model.isSelected()} into {@code Checkbox.setValue}
 *       under {@link #preventPeerEvents}.</li>
 *   <li><b>{@link #setText} / {@link #getText}</b> — Checkbox is
 *       {@code HasLabel}, not {@code HasText}; route through
 *       {@code setLabel}/{@code getLabel}, normalise {@code null → ""}
 *       (R_vaadin_first lossy).</li>
 *   <li><b>Layout setters</b> — validate per R_match_swing_errors (IAE on invalid
 *       {@link SwingConstants}) then drop-and-WARN on non-default; shadow
 *       DOM doesn't honor host inline-flex, so silent CSS would deceive.</li>
 *   <li><b>{@link #setEnabled}</b> — explicit redirect into the mixin
 *       chain, so the choice is pinned in source rather than resting on
 *       interface-default specificity.</li>
 *   <li><b>{@code setIcon}</b> — <em>not</em> overridden. The mixin's
 *       default WARN-drops on the non-{@code Button} branch (Checkbox has
 *       no icon slot).</li>
 * </ul>
 *
 * <h2>JCheckBox-specific surface</h2>
 *
 * {@code borderPaintedFlat} (drop-and-WARN per R_vaadin_first — no Vaadin counterpart),
 * {@code getUIClassID() == "CheckBoxUI"}, and the JCheckBox {@code paramString}
 * tail. The emulator-side {@code :emulators.JCheckBox} keeps its own
 * {@code borderPaintedFlat} field + PCE for full JDK round-trip per R_swing_is_truth.
 *
 * <h2>Browser-originated toggle wiring</h2>
 *
 * The {@code ValueChangeListener} guards on {@link #preventPeerEvents}
 * (echo from our own push) and {@code model.isSelected() == newValue}
 * (idempotency), then funnels through {@link SHelper#callSwing}{@code
 * (this::synthesizePeerClick)} so user-click and {@code doClick} deliver
 * identical Item + Change + Action sequences.
 */
public class SJCheckBox extends Checkbox implements AbstractButtonMixin {

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

    /**
     * R_swing_is_truth feedback-loop guard. Set before writing to the peer (this
     * Checkbox's {@code setValue}) so the peer's {@code ValueChangeListener}
     * bails instead of round-tripping back into the model.
     */
    private boolean preventPeerEvents;

    /**
     * Teardown handle for the model→peer push ItemListener installed in
     * {@link #setModel(ButtonModel)}. Null when no model is installed.
     */
    private Registration peerSyncRegistration;

    public SJCheckBox() {
        this((String) null, false);
    }

    public SJCheckBox(String text) {
        this(text, false);
    }

    public SJCheckBox(String text, boolean selected) {
        super();
        _installSwingClass();
        installButtonBindings();
        if (text != null) setText(text);
        if (selected) setSelected(true);
    }

    public SJCheckBox(Icon icon) {
        this((String) null, false);
        setIcon(icon);
    }

    public SJCheckBox(Icon icon, boolean selected) {
        this((String) null, selected);
        setIcon(icon);
    }

    public SJCheckBox(String text, Icon icon) {
        this(text, false);
        setIcon(icon);
    }

    public SJCheckBox(String text, Icon icon, boolean selected) {
        this(text, selected);
        setIcon(icon);
    }

    public SJCheckBox(javax.swing.Action action) {
        // JDK pattern: this(); setAction(a).
        this();
        setAction(action);
    }

    /**
     * Visibility-widening override required by {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s
     * public {@code validate()} contract — Vaadin {@link Checkbox} declares
     * the inherited {@code validate()} as {@code protected} (input
     * validation, not Swing's layout cycle). Same SD_toggle_checkbox_first_cut / SD_sjtextfield / SD_sjpasswordfield
     * precedent; delegates to Checkbox's super.
     */
    @Override
    public void validate() {
        super.validate();
    }

    /**
     * Override of the mixin's default: {@link JToggleButton.ToggleButtonModel}
     * as default model, a {@code HasValue<Boolean> ValueChangeListener}
     * (covers mouse-click AND keyboard-Space without double-firing) instead
     * of the {@code ClickNotifier} wire, and skips the inline-flex CSS seed
     * (Checkbox shadow DOM doesn't honor host flex). Idempotent.
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
        });
    }

    /**
     * Override of {@link AbstractButtonMixin#setModel} to additionally
     * install a model→peer push ItemListener that mirrors
     * {@code model.isSelected()} into {@code Checkbox.setValue} under
     * {@link #preventPeerEvents}. Catches direct
     * {@code myModel.setSelected(...)} mutations, not just mixin-routed ones.
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
                SHelper.runOnOwnerUI(this, () -> {
                    preventPeerEvents = true;
                    try {
                        setValue(v);
                    } finally {
                        preventPeerEvents = false;
                    }
                });
            };
            newModel.addItemListener(pushToPeer);
            peerSyncRegistration = () -> newModel.removeItemListener(pushToPeer);
        }
    }

    // --- Text dispatch — Checkbox is HasLabel, not HasText. JDK "text"
    // maps to Vaadin's "label". Normalise null → "" (R_vaadin_first lossy).

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
     * Routes into the mixin chain so {@code model.setEnabled(...)} stays in
     * sync and SD_auto_pce's auto-PCE fires.
     *
     * <p>Not forced by a "classes beat interfaces" shadow — Vaadin
     * {@code Checkbox} declares no {@code setEnabled} of its own, so
     * {@code AbstractButtonMixin}'s default would win on specificity
     * anyway. Kept to pin the choice in source, and because the
     * sibling {@link SButton} really does need it (Vaadin
     * {@code Button} declares one).
     */
    @Override
    public void setEnabled(boolean enabled) {
        AbstractButtonMixin.super.setEnabled(enabled);
    }

    // --- Layout — drop-and-WARN (shadow DOM doesn't honor host inline-flex
    // CSS). Validate per R_match_swing_errors (IAE on invalid SwingConstants) then drop on
    // non-default; default value is silent.

    @Override
    public int getHorizontalAlignment() {
        return SwingConstants.CENTER;
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
        return SwingConstants.CENTER;
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
        return SwingConstants.TRAILING;
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
        return SwingConstants.CENTER;
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
        return 4;
    }

    @Override
    public void setIconTextGap(int gap) {
        if (gap != 4) {
            SHelper.onUnimplemented(this, "setIconTextGap", gap);
        }
    }

    @Override
    public Insets getMargin() {
        return null;
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

    // --- JCheckBox-specific surface ----------------------------------

    /**
     * R_vaadin_first drop-and-WARN. Vaadin Checkbox owns its chrome — no counterpart
     * for "paint border only when in toolbar". Default {@code false} is
     * silent; {@code true} WARNs and drops. No PCE (no state, no change).
     */
    public void setBorderPaintedFlat(boolean b) {
        if (b) {
            SHelper.onUnimplemented(this, "setBorderPaintedFlat", b);
        }
    }

    /** Returns the JDK default; no shadow store per R_vaadin_first. */
    public boolean isBorderPaintedFlat() {
        return false;
    }

    @Override
    public String getUIClassID() {
        return "CheckBoxUI";
    }

    /**
     * JDK JCheckBox.paramString appends {@code ",borderPaintedFlat=<bool>"}.
     * We always report {@code false} (the JDK default) since the surrogate
     * doesn't store the value per R_vaadin_first.
     */
    protected String paramString() {
        return ",borderPaintedFlat=false";
    }

    // --- Accessibility (deferred per emulator stance) ----------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
