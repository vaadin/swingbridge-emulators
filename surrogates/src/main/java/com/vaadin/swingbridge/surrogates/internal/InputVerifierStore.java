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

import javax.swing.InputVerifier;

/**
 * Per-component slot for the surrogate's installed {@link InputVerifier}
 * plus the matching Vaadin {@code BlurNotifier} {@link Registration} that
 * drives it. Unified into one store so {@code setInputVerifier} can swap
 * verifier and listener atomically: previous registration tears down before
 * the new one installs, so the user-listener fan-out stays at one verify
 * call per blur regardless of how many times the verifier is replaced.
 *
 * <p>Carve-out justification (R_vaadin_first): InputVerifier drives UI behaviour
 * (blur-time validation, focus restoration), not API round-trip — same
 * "UI-functionality bucket" as ButtonModel storage on SJButton. The
 * Registration tracking is the surrogate's own subscription bookkeeping
 * (analogous to {@link Registrations} for listener-typed APIs). Single
 * replaceable slot, not a list, so the slot lives outside
 * {@link Registrations}' EventListener-keyed map.
 */
public final class InputVerifierStore {

    private InputVerifier verifier;
    private Registration blurReg;

    private InputVerifierStore() {}

    public static InputVerifierStore of(Component target) {
        InputVerifierStore existing = ComponentUtil.getData(target, InputVerifierStore.class);
        if (existing != null) return existing;
        InputVerifierStore fresh = new InputVerifierStore();
        ComponentUtil.setData(target, InputVerifierStore.class, fresh);
        return fresh;
    }

    public InputVerifier getVerifier() {
        return verifier;
    }

    /**
     * Swap to {@code newVerifier} with {@code newReg} as its Vaadin blur
     * subscription. Removes the previous registration first; either side
     * may be {@code null} (null verifier with null reg means "uninstall").
     */
    public void set(InputVerifier newVerifier, Registration newReg) {
        if (blurReg != null) blurReg.remove();
        this.verifier = newVerifier;
        this.blurReg = newReg;
    }
}
