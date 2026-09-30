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
import com.vaadin.swingbridge.surrogates.SJPopupMenu;

/**
 * State holder for {@code JComponentMixin.setComponentPopupMenu} (SD_sjpopupmenu).
 *
 * <p>Carries the single {@link SJPopupMenu} reference a component has had
 * installed as its context popup. The reference is <em>backed by</em> a live
 * Vaadin binding — {@code setComponentPopupMenu} calls
 * {@link com.vaadin.flow.component.contextmenu.ContextMenu#setTarget} on the
 * popup with this component as the target — so storing it satisfies R_vaadin_first's
 * "storage backed by a Vaadin counterpart" test (it drives the context-menu
 * UI, like an installed {@code Action} or {@code ButtonModel}); it is not a
 * round-trip-only shadow cache.
 *
 * <p>Lazily attached per-component via {@link ComponentUtil} data, same shape
 * as {@link MenuBarStateStore} / {@link InputMapStore}.
 */
public final class ComponentPopupStore {

    /** The currently installed component popup, or {@code null}. */
    public SJPopupMenu popup;

    private ComponentPopupStore() {}

    public static ComponentPopupStore of(Component target) {
        ComponentPopupStore existing = ComponentUtil.getData(target, ComponentPopupStore.class);
        if (existing != null) return existing;
        ComponentPopupStore fresh = new ComponentPopupStore();
        ComponentUtil.setData(target, ComponentPopupStore.class, fresh);
        return fresh;
    }
}
