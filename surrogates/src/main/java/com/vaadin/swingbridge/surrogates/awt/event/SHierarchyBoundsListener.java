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
 * Surrogate-side port of {@link java.awt.event.HierarchyBoundsListener}.
 * Receives {@link SHierarchyEvent#ANCESTOR_MOVED} /
 * {@link SHierarchyEvent#ANCESTOR_RESIZED} transitions on the ancestor
 * chain.
 *
 * <p>{@code ComponentMixin.addHierarchyBoundsListener} is Bucket B
 * {@link com.vaadin.swingbridge.surrogates.SHelper#onNoop} under SD_listeners_without_analog — Vaadin doesn't
 * surface ancestor pixel moves/resizes to the server, and R_layouts_close_enough's
 * close-enough layout stance means this listener would never fire even
 * if wired. The type is kept for registration-shape compatibility with
 * migrated code.
 */
public interface SHierarchyBoundsListener extends EventListener {
    void ancestorMoved(SHierarchyEvent e);
    void ancestorResized(SHierarchyEvent e);
}
