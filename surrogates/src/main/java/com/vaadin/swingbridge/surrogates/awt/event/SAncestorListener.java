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
 * Surrogate-side port of {@link javax.swing.event.AncestorListener}.
 * Receives {@link SAncestorEvent}s on attach / detach / layout-position
 * changes of the listened component or any of its ancestors.
 *
 * <p>{@code ancestorMoved} fires under JDK Swing when an ancestor's
 * layout position changes; Vaadin doesn't surface that server-side, so
 * the surrogate-side wiring in {@code JComponentMixin.addAncestorListener}
 * never fires it. {@code ancestorAdded} / {@code ancestorRemoved} fire
 * on Vaadin attach / detach.
 */
public interface SAncestorListener extends EventListener {
    void ancestorAdded(SAncestorEvent event);
    void ancestorRemoved(SAncestorEvent event);
    void ancestorMoved(SAncestorEvent event);
}
