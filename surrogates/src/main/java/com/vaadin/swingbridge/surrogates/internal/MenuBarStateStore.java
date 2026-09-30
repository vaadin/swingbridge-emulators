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
import com.vaadin.swingbridge.surrogates.MenuNode;

import javax.swing.KeyStroke;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.SJMenuBar} (SD_sjmenubar).
 *
 * <p>Scoped per R_vaadin_first: tracks bookkeeping the Vaadin {@code MenuBar} has no
 * counterpart for. Pure rendering state lives on Vaadin (the
 * {@code MenuItem} tree built by every {@code rebuildFromTree} call); this
 * holder carries the cross-rebuild pieces — accelerator
 * {@link Registration}s that must tear down on the next rebuild, and the
 * last-pushed {@link MenuNode} tree for diagnostics + idempotency checks.
 */
public final class MenuBarStateStore {

    /**
     * Last snapshot pushed via {@code rebuildFromTree}; empty at construction.
     * For diagnostics + tests asserting the tree-rebuild API saw exact items —
     * not a rendering source of truth (Vaadin's {@code MenuBar.getItems()} is).
     */
    public List<MenuNode> currentTree = new ArrayList<>();

    /**
     * Per-{@link KeyStroke} {@link Registration}s for installed UI-scoped
     * Vaadin shortcuts. Torn down at the start of every rebuild; re-installed
     * during the second walk over the new tree.
     */
    public final Map<KeyStroke, Registration> acceleratorRegistrations = new HashMap<>();

    /**
     * Re-entrancy guard. {@code rebuildFromTree} sets this before walking
     * the snapshot and clears it in {@code finally}. Click handlers that
     * fire inside a toggle commit can check this and short-circuit the
     * emulator-side rebuild push, avoiding feedback into the click that
     * triggered them. Same shape SJSpinner / SJSlider take per SD_sjslider / SD_sjspinner.
     */
    public boolean preventRebuild;

    private MenuBarStateStore() {}

    public static MenuBarStateStore of(Component target) {
        MenuBarStateStore existing = ComponentUtil.getData(target, MenuBarStateStore.class);
        if (existing != null) return existing;
        MenuBarStateStore fresh = new MenuBarStateStore();
        ComponentUtil.setData(target, MenuBarStateStore.class, fresh);
        return fresh;
    }
}
