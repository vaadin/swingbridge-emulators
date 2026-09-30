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

import com.vaadin.flow.component.AbstractSinglePropertyField;
import com.vaadin.flow.component.ClickNotifier;
import com.vaadin.flow.component.Focusable;
import com.vaadin.flow.component.HasLabel;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.shared.SlotUtils;

/**
 * Re-host of Vaadin's {@code vaadin-radio-button} web component as a
 * public Java {@link com.vaadin.flow.component.Component}. Vaadin ships
 * the web component as part of {@code @vaadin/radio-group} and exposes
 * a Java wrapper {@code com.vaadin.flow.component.radiobutton.RadioButton}
 * — but that wrapper is package-private (intended as
 * {@code RadioButtonGroup}'s internal item class only), so it cannot be
 * extended or used standalone. {@link SJRadioButton} needs a real radio
 * button it can subclass, so we re-host the public web component here.
 *
 * <p>Lives in {@code :surrogates} as a plain Vaadin component — no Swing
 * surface, no mixin. General-purpose: any Vaadin code needing a
 * standalone radio button (outside a {@code RadioButtonGroup}) can use
 * it directly. Same precedent {@link LongField} follows for Vaadin's
 * missing long-typed numeric field.
 *
 * <h2>HasValue&lt;Boolean&gt; via {@code checked} property</h2>
 *
 * Extends {@link AbstractSinglePropertyField} bound to the
 * {@code checked} boolean — the same web-component property Vaadin's
 * own (package-private) {@code RadioButton} synchronises via
 * {@code @Synchronize("checked-changed")}. AbstractSinglePropertyField's
 * automatic {@code <property>-changed} subscription wires the same
 * synchronisation without the annotation. Produces a Vaadin
 * {@link com.vaadin.flow.component.HasValue.ValueChangeEvent} on
 * browser-side toggle — SJRadioButton's R_swing_is_truth model sync hooks here.
 *
 * <h2>Label slot (not a property)</h2>
 *
 * Unlike Vaadin {@code Checkbox} (which exposes a {@code label} string
 * property via {@link HasLabel}'s default), {@code vaadin-radio-button}
 * renders its label through a slotted child element. We override
 * {@link HasLabel#setLabel} / {@link HasLabel#getLabel} to manage a
 * single {@link NativeLabel} in the {@code label} slot. Same mechanism
 * Vaadin's internal {@code RadioButton} uses via
 * {@code setLabelComponent} — we surface a string-based setter to match
 * the {@code HasLabel} contract.
 *
 * <h2>Standalone use vs. RadioButtonGroup</h2>
 *
 * Vaadin's design hosts radios exclusively inside a
 * {@code RadioButtonGroup}, which manages selection mutex on the server
 * side and pushes per-element {@code checked} state. A standalone
 * {@link VaadinRadioButton} carries no mutex behaviour — clicking
 * toggles {@code checked} on/off like a checkbox. Group mutex is
 * SJRadioButton's job (via {@code ButtonModel} + emulator-layer
 * {@code vaadinx.swing.ButtonGroup} per D_buttongroup), not the peer's.
 */
@Tag("vaadin-radio-button")
@NpmPackage(value = "@vaadin/radio-group", version = "25.3.0")
@JsModule("@vaadin/radio-group/src/vaadin-radio-button.js")
public class VaadinRadioButton
        extends AbstractSinglePropertyField<VaadinRadioButton, Boolean>
        implements ClickNotifier<VaadinRadioButton>,
                   Focusable<VaadinRadioButton>,
                   HasLabel {

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
     * The single {@link NativeLabel} child occupying the {@code label}
     * slot. Lazily created on first {@link #setLabel(String)} with a
     * non-null/non-empty value; cleared back to null when the label is
     * cleared. Read by {@link #getLabel()} for HasLabel round-trip.
     */
    private NativeLabel labelElement;

    public VaadinRadioButton() {
        super("checked", false, false);
    }

    /**
     * Set the radio button's label. Null and the empty string both clear
     * the label slot; any other value lazily creates a {@link NativeLabel}
     * in the {@code label} slot and writes the text into it. Matches the
     * {@code HasLabel} contract while routing through the slot mechanism
     * the web component actually uses (no top-level {@code label}
     * property exists on {@code vaadin-radio-button}).
     */
    @Override
    public void setLabel(String label) {
        if (label == null || label.isEmpty()) {
            if (labelElement != null) {
                SlotUtils.clearSlot(this, "label");
                labelElement = null;
            }
            return;
        }
        if (labelElement == null) {
            labelElement = new NativeLabel();
            SlotUtils.addToSlot(this, "label", labelElement);
        }
        labelElement.setText(label);
    }

    /**
     * Read back the label set via {@link #setLabel(String)}. Returns
     * {@code null} when no label slot child exists (matches Vaadin
     * {@code Checkbox.getLabel()}'s null-when-unset shape).
     */
    @Override
    public String getLabel() {
        return labelElement != null ? labelElement.getText() : null;
    }
}
