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

import com.vaadin.flow.component.button.Button;
import com.vaadin.swingbridge.surrogates.awt.ContainerMixin;

/**
 * Throwaway test-only surrogate — the mixin mechanism needs an exerciser
 * even where no concrete surrogate exists. Extending {@link Button} picks a
 * Vaadin component that implements both {@link com.vaadin.flow.component.HasEnabled}
 * (so {@link ContainerMixin}'s {@link com.vaadin.flow.component.HasEnabled} supertype is
 * satisfied) and {@link com.vaadin.flow.component.shared.HasTooltip} / {@link com.vaadin.flow.component.Focusable}
 * (so the mixin's tooltip / focus delegation paths can be tested).
 *
 * <p>Implementing {@link ContainerMixin} (which extends {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin})
 * gives this one class the full mixin surface in a single test
 * fixture.
 */
public class TestSurrogate extends Button implements ContainerMixin {

    /**
     * Java's "classes beat interfaces" rule shadows the mixin chain's
     * setEnabled with the one Vaadin's {@code Component} declares. Redirect
     * explicitly so auto-PCE (SD_auto_pce) still fires — the same redirect
     * {@link SJButton#setEnabled} needs for the same reason.
     */
    @Override
    public void setEnabled(boolean enabled) {
        ContainerMixin.super.setEnabled(enabled);
    }
}
