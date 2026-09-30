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
import com.vaadin.flow.component.ShortcutRegistration;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-component slots for Swing's three condition-scoped {@link InputMap}s
 * and single {@link ActionMap}, plus the Vaadin
 * {@link ShortcutRegistration} map that backs
 * {@code JComponentMixin.registerKeyboardAction}. Each slot is lazily
 * allocated on first access so a component that never touches its
 * keymaps pays nothing.
 *
 * <p>Browser-side shortcut wiring (SD_vaadin_first_binding): maps populated via {@code registerKeyboardAction}
 * drive a real Vaadin {@link com.vaadin.flow.component.Shortcuts}
 * registration on the surrogate; {@link #installedShortcuts} keys the
 * registration by {@code (condition, KeyStroke)} so unregister /
 * setInputMap-replacement can tear it down precisely.
 */
public final class InputMapStore {

    /** Identity key for an installed (condition, KeyStroke) shortcut. */
    public record ShortcutKey(int condition, KeyStroke keyStroke) {}

    private InputMap whenFocused;
    private InputMap whenAncestor;
    private InputMap whenInWindow;
    private ActionMap actionMap;
    private Map<ShortcutKey, ShortcutRegistration> installedShortcuts;

    private InputMapStore() {}

    public static InputMapStore of(Component target) {
        InputMapStore existing = ComponentUtil.getData(target, InputMapStore.class);
        if (existing != null) return existing;
        InputMapStore fresh = new InputMapStore();
        ComponentUtil.setData(target, InputMapStore.class, fresh);
        return fresh;
    }

    /** Lazy-allocate the {@code WHEN_FOCUSED} map. */
    public InputMap getWhenFocused() {
        if (whenFocused == null) whenFocused = new InputMap();
        return whenFocused;
    }

    /** Lazy-allocate the {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT} map. */
    public InputMap getWhenAncestor() {
        if (whenAncestor == null) whenAncestor = new InputMap();
        return whenAncestor;
    }

    /** Lazy-allocate the {@code WHEN_IN_FOCUSED_WINDOW} map. */
    public InputMap getWhenInWindow() {
        if (whenInWindow == null) whenInWindow = new InputMap();
        return whenInWindow;
    }

    /** Raw accessor — returns {@code null} when never allocated. Used by query paths that mustn't materialise empty maps. */
    public InputMap rawWhenFocused() { return whenFocused; }
    /** See {@link #rawWhenFocused()}. */
    public InputMap rawWhenAncestor() { return whenAncestor; }
    /** See {@link #rawWhenFocused()}. */
    public InputMap rawWhenInWindow() { return whenInWindow; }

    /** Replace the {@code WHEN_FOCUSED} map (caller-provided; may be {@code null} to clear). */
    public void setWhenFocused(InputMap map) { this.whenFocused = map; }
    /** See {@link #setWhenFocused(InputMap)}. */
    public void setWhenAncestor(InputMap map) { this.whenAncestor = map; }
    /** See {@link #setWhenFocused(InputMap)}. */
    public void setWhenInWindow(InputMap map) { this.whenInWindow = map; }

    /** Lazy-allocate the single {@link ActionMap}. */
    public ActionMap getActionMap() {
        if (actionMap == null) actionMap = new ActionMap();
        return actionMap;
    }

    /** Raw accessor — returns {@code null} when never allocated. */
    public ActionMap rawActionMap() { return actionMap; }

    /** Replace the {@link ActionMap} (caller-provided; may be {@code null} to clear). */
    public void setActionMap(ActionMap map) { this.actionMap = map; }

    /**
     * Lazy-allocate the (condition, KeyStroke) → {@link ShortcutRegistration}
     * map. Insertion order matters for tests asserting consistent iteration.
     */
    public Map<ShortcutKey, ShortcutRegistration> shortcuts() {
        if (installedShortcuts == null) installedShortcuts = new LinkedHashMap<>();
        return installedShortcuts;
    }

    /** Raw accessor — returns {@code null} when no shortcut was ever installed. */
    public Map<ShortcutKey, ShortcutRegistration> rawShortcuts() {
        return installedShortcuts;
    }
}
