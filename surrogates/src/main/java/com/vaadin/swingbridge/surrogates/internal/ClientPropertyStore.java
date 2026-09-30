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

import java.util.HashMap;
import java.util.Map;

/**
 * Per-component client-property map backing
 * {@code JComponentMixin.putClientProperty} / {@code getClientProperty}.
 * Swing's contract: writing {@code null} removes the mapping, and the
 * mixin fires {@code PropertyChangeEvent} named after {@code key.toString()}
 * when the value actually changes. Storage is a plain
 * {@link HashMap} — lookups aren't on any hot path.
 */
public final class ClientPropertyStore {

    private final Map<Object, Object> properties = new HashMap<>();

    private ClientPropertyStore() {}

    public static ClientPropertyStore of(Component target) {
        ClientPropertyStore existing = ComponentUtil.getData(target, ClientPropertyStore.class);
        if (existing != null) return existing;
        ClientPropertyStore fresh = new ClientPropertyStore();
        ComponentUtil.setData(target, ClientPropertyStore.class, fresh);
        return fresh;
    }

    /** Returns the current value for {@code key}, or {@code null} when unset. */
    public Object get(Object key) {
        if (key == null) return null;
        return properties.get(key);
    }

    /**
     * Writes {@code value}, or removes the mapping when {@code value} is
     * {@code null}. Returns the previous value (also {@code null} when
     * unset). Callers use the old value to decide whether to fire PCE.
     */
    public Object put(Object key, Object value) {
        if (value == null) {
            return properties.remove(key);
        }
        return properties.put(key, value);
    }
}
