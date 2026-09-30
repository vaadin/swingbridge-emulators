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

package com.vaadin.swingbridge.surrogates.awt.event;

import java.util.EventListener;

/**
 * Surrogate-side port of {@link java.awt.event.HierarchyListener}. Single
 * callback — fires on attach, detach, and ancestor hierarchy changes.
 *
 * <p>Under the Vaadin-first stance (SD_vaadin_first_binding), registrations wire a Vaadin
 * attach/detach subscription on the peer; the dispatched
 * {@link SHierarchyEvent} carries {@link SHierarchyEvent#PARENT_CHANGED} /
 * {@link SHierarchyEvent#DISPLAYABILITY_CHANGED} /
 * {@link SHierarchyEvent#SHOWING_CHANGED} flags computed from the
 * attach/detach transition.
 */
public interface SHierarchyListener extends EventListener {
    void hierarchyChanged(SHierarchyEvent e);
}
