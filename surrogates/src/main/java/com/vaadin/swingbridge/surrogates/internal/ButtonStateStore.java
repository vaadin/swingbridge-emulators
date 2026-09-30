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

package com.vaadin.swingbridge.surrogates.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.shared.Registration;

import javax.swing.event.EventListenerList;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin}.
 *
 * <p>Scoped per R_vaadin_first: UI-functional state or state the store-less path
 * genuinely can't reconstruct lives here. Setters for the rest — six
 * state-conditional icons, border-painted / content-area-filled /
 * focus-painted / rollover-enabled, margin, displayedMnemonicIndex,
 * multiClickThreshhold — route through {@code SHelper.onUnimplemented}
 * and drop the value; their getters return the JDK default.
 *
 * <p><b>Layout fields are <em>not</em> stored here.</b> The four
 * alignment / text-position setters and {@code iconTextGap} write CSS
 * inline on the host (Vaadin Button is already {@code display: inline-flex}
 * per Lumo) and the matching getters lossy-parse CSS back — same shape
 * as {@code setBorder} / {@code getBorder} per SD_border_css_lossy. R_vaadin_first explicitly
 * accepts the LEADING ↔ LEFT and "vertical-text-position-non-CENTER
 * masks horizontal-text-position" lossy round-trips; storing them just
 * to dodge the loss would be the "shadow cache so the setter
 * round-trips through its own getter" R_vaadin_first forbids.
 *
 * <p>{@code text} is <em>not</em> stored: setText/getText were lifted
 * to abstract on {@link com.vaadin.swingbridge.surrogates.swing.AbstractButtonMixin}, so
 * each concrete surrogate routes through its parent Vaadin component's
 * {@link com.vaadin.flow.component.HasText} (e.g. SJButton's inherited
 * {@code Button.getText()} reads {@code textNode}). The R_vaadin_first lossy
 * {@code setText(null) → getText() == ""} round-trip is the contract
 * on the surrogate side; the emulator layer preserves null on its own
 * field shadow per R_swing_is_truth.
 */
public final class ButtonStateStore {

    /** No Vaadin analog; drives ActionEvent payload. */
    public String actionCommand;

    /** No Vaadin analog; affects {@code setAction} NAME propagation. */
    public boolean hideActionText;

    /** Shortcuts is write-only; VK round-trip + install state. */
    public int mnemonic = 0;

    /** Handle to tear down the previous {@code Shortcuts} install. */
    public Registration mnemonicRegistration;

    /** Currently installed {@link javax.swing.Action}; null when none. */
    public javax.swing.Action action;

    /** Listener on {@code action} that routes per-property mutations back to the button. */
    public java.beans.PropertyChangeListener actionPropertyChangeListener;

    /** Source of truth for armed/pressed/selected/enabled — drives all three fire-families. */
    public javax.swing.ButtonModel model;

    /**
     * Single teardown handle for the three bridge listeners (ChangeListener
     * / ActionListener / ItemListener) the mixin installs on {@link #model}
     * to re-fire model events at the button level. {@code remove()} detaches
     * all three. Bridge listeners are internal glue, not user-supplied API
     * — see class javadoc for why this isn't in {@link Registrations}.
     */
    public Registration modelInstall;

    /** Type-keyed listener lists for ActionListener / ChangeListener / ItemListener fan-out. */
    public final EventListenerList listenerList = new EventListenerList();

    private ButtonStateStore() {}

    public static ButtonStateStore of(Component target) {
        ButtonStateStore existing = ComponentUtil.getData(target, ButtonStateStore.class);
        if (existing != null) return existing;
        ButtonStateStore fresh = new ButtonStateStore();
        ComponentUtil.setData(target, ButtonStateStore.class, fresh);
        return fresh;
    }
}
