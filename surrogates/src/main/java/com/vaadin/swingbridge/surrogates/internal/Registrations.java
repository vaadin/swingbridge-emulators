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

import java.util.EventListener;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified registry of Swing-listener → Vaadin-{@link Registration} pairs.
 * Replaces the per-family {@code *Support} holders (see
 * SD_vaadin_first_binding: Vaadin-first direct-binding stance).
 *
 * <p>Each {@code addXListener} on a mixin creates the matching Vaadin
 * subscription (on {@link com.vaadin.flow.component.FocusNotifier} etc.),
 * calls {@link #add(EventListener, Registration)} with the Swing listener
 * as the key and the Vaadin {@link Registration} as the value, and
 * returns. {@code removeXListener} looks up the Swing listener, calls
 * {@link Registration#remove()}, and drops the entry. {@code getXListeners()}
 * filters the keys by type — {@link LinkedHashMap} preserves insertion
 * order, which matches Swing's listener-array order.
 *
 * <p>One {@link Registrations} per component; concurrent multi-threaded
 * access isn't modelled (Vaadin's session lock serialises UI-thread
 * access, which is where these operations happen).
 *
 * <p>Adding the same listener instance twice collapses to a single entry
 * — the second {@link #add} replaces the first registration and removes
 * the stale one. Swing's contract does let the same listener register
 * twice and fire twice; the current pattern treats that as a programming
 * error we'd rather squash than replicate, and it keeps the map semantics
 * clean. Revisit if a migration target relies on double-fire.
 */
public final class Registrations {

    private final Map<EventListener, Registration> byListener = new LinkedHashMap<>();

    private Registrations() {}

    public static Registrations of(Component target) {
        Registrations existing = ComponentUtil.getData(target, Registrations.class);
        if (existing != null) return existing;
        Registrations fresh = new Registrations();
        ComponentUtil.setData(target, Registrations.class, fresh);
        return fresh;
    }

    /**
     * Register {@code listener} with the Vaadin-side {@code registration}.
     * If the listener was already registered, the previous registration
     * is {@link Registration#remove() removed} first so the callback count
     * stays at one.
     */
    public void add(EventListener listener, Registration registration) {
        Registration previous = byListener.put(listener, registration);
        if (previous != null) previous.remove();
    }

    /**
     * Remove and {@link Registration#remove()} the Vaadin registration for
     * {@code listener}. No-op when the listener wasn't registered.
     */
    public void remove(EventListener listener) {
        Registration reg = byListener.remove(listener);
        if (reg != null) reg.remove();
    }

    /**
     * Return all listeners of type {@code type} in registration order.
     * Used by {@code getXListeners()} accessors on the mixins —
     * {@code getFocusListeners()} passes {@code FocusListener.class},
     * {@code getHierarchyListeners()} passes {@code HierarchyListener.class},
     * and so on.
     */
    @SuppressWarnings("unchecked")
    public <T extends EventListener> T[] getListeners(Class<T> type) {
        return byListener.keySet().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .toArray(n -> (T[]) java.lang.reflect.Array.newInstance(type, n));
    }

    /** Test/diagnostic accessor — total registration count. */
    public int size() { return byListener.size(); }
}
