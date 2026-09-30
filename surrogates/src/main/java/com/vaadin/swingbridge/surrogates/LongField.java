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

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.textfield.AbstractNumberField;
import com.vaadin.flow.function.SerializableFunction;

/**
 * Long-valued sibling of Vaadin's {@code IntegerField}. Extends
 * {@link AbstractNumberField} with a {@link Long} type parameter and a
 * {@link Long#parseLong} parser; shares the {@code vaadin-integer-field}
 * web component so the client side still rejects decimals and shows the
 * familiar spinner-arrow chrome.
 *
 * <p>Lives in {@code :surrogates} as a plain Vaadin component — no Swing
 * surface, no mixin. Introduced to back {@link SJSpinner} when the
 * installed {@code SpinnerNumberModel} carries a {@link Long} value
 * without falling back to {@code NumberField}'s {@code double}-mantissa
 * rounding, but general-purpose: any Vaadin code needing a long-typed
 * numeric field can use it directly.
 *
 * <p>Client-side precision: values up to 2<sup>53</sup> round-trip
 * exactly through the browser's native numeric input (JS numbers are
 * IEEE-754 doubles; integer-valued doubles stay exact up to 2<sup>53</sup>).
 * Longs outside that range display with mantissa rounding on the wire,
 * but the server-side parser (unchanged {@link Long#parseLong} on the
 * string form delivered via {@code _inputElementValue}) sees the exact
 * text the browser rendered, so round-trip precision depends on how
 * browsers format long integers — which today is exact for all values
 * the browser accepts via {@code input[type=number]}.
 *
 * <p>Absolute min/max passed to the superclass use
 * {@link Long#MIN_VALUE} / {@link Long#MAX_VALUE} cast to {@code double}
 * (precision-lossy at the extremes, but the superclass removes those
 * properties from the DOM immediately after initialisation — they only
 * matter as signal defaults until a caller explicitly
 * {@link #setMin(long) setMin} / {@link #setMax(long) setMax}).
 */
@Tag("vaadin-integer-field")
@NpmPackage(value = "@vaadin/integer-field", version = "25.3.0")
@JsModule("@vaadin/integer-field/src/vaadin-integer-field.js")
public class LongField extends AbstractNumberField<LongField, Long> {

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

    private static final SerializableFunction<String, Long> PARSER = s -> {
        if (s == null || s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    };

    private static final SerializableFunction<Long, String> FORMATTER =
            v -> v == null ? "" : v.toString();

    public LongField() {
        super(PARSER, FORMATTER, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public LongField(String label) {
        this();
        setLabel(label);
    }

    /**
     * Sets the minimum value for the field. Mirrors {@code IntegerField.setMin}
     * but with a {@code long} argument. Routes to {@code AbstractNumberField}'s
     * {@code double}-typed setter — precision is exact within
     * [-2<sup>53</sup>, 2<sup>53</sup>] and lossy beyond that.
     */
    public void setMin(long min) {
        super.setMin(min);
    }

    public long getMin() {
        return (long) getMinDouble();
    }

    public void setMax(long max) {
        super.setMax(max);
    }

    public long getMax() {
        return (long) getMaxDouble();
    }

    /**
     * Sets the spinner-arrow step. Mirrors {@code IntegerField.setStep}'s
     * positive-only contract — zero / negative throws IAE, matching the
     * web component's expectation that step is strictly positive.
     */
    public void setStep(long step) {
        if (step <= 0) {
            throw new IllegalArgumentException(
                    "The step cannot be less or equal to zero.");
        }
        super.setStep(step);
    }

    public long getStep() {
        return (long) getStepDouble();
    }
}
