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
 * Surrogate-side port of {@link java.awt.event.ContainerListener}. Receives
 * {@link SContainerEvent}.
 *
 * <p>{@code ContainerMixin.addContainerListener} is Bucket B {@link com.vaadin.swingbridge.surrogates.SHelper#onNoop}
 * under SD_listeners_without_analog — Vaadin 25 surfaces no child-list mutation event and a
 * generic mixin can't observe its own {@code HasComponents.add} /
 * {@code remove} calls. A concrete container surrogate can wire firing
 * itself by overriding those methods; this type exists for that path.
 */
public interface SContainerListener extends EventListener {
    void componentAdded(SContainerEvent e);
    void componentRemoved(SContainerEvent e);
}
