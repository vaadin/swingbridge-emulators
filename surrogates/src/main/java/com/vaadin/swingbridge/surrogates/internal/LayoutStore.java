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

import java.awt.LayoutManager;

/**
 * Per-container slot for Swing's {@code Container.layout}: stores
 * but does not dispatch — the field round-trips so getter/setter pairs are
 * consistent, but no CSS is written and no {@code layoutContainer} is
 * called. Wiring lands when a concrete container surrogate first ships
 * (SD_dropin_stance).
 */
public final class LayoutStore {

    private LayoutManager layout;

    private LayoutStore() {}

    public static LayoutStore of(Component target) {
        LayoutStore existing = ComponentUtil.getData(target, LayoutStore.class);
        if (existing != null) return existing;
        LayoutStore fresh = new LayoutStore();
        ComponentUtil.setData(target, LayoutStore.class, fresh);
        return fresh;
    }

    public LayoutManager get() { return layout; }
    public void set(LayoutManager layout) { this.layout = layout; }
}
