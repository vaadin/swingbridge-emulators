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

/**
 * Per-component slot for Swing's {@code Component.name}. Stored separately
 * from Vaadin's {@code id} on purpose (SD_dropin_stance): Vaadin's id is a DOM/CSS
 * identifier with rendering side effects; Swing's name is a programmatic
 * label with no visible effect.
 */
public final class NameStore {

    private String name;

    private NameStore() {}

    public static NameStore of(Component target) {
        NameStore existing = ComponentUtil.getData(target, NameStore.class);
        if (existing != null) return existing;
        NameStore fresh = new NameStore();
        ComponentUtil.setData(target, NameStore.class, fresh);
        return fresh;
    }

    public String get() {
        return name;
    }

    public void set(String name) {
        this.name = name;
    }
}
