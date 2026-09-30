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
 * Surrogate-side port of {@link java.awt.event.FocusListener}. Receives
 * {@link SFocusEvent} (source = Vaadin {@link com.vaadin.flow.component.Component}).
 *
 * <p>Under the Vaadin-first stance (SD_vaadin_first_binding), registrations made via
 * {@code ComponentMixin.addFocusListener} wire a Vaadin
 * {@link com.vaadin.flow.component.FocusNotifier} /
 * {@link com.vaadin.flow.component.BlurNotifier} subscription on the peer
 * and dispatch to these callbacks directly — no store-only path.
 */
public interface SFocusListener extends EventListener {
    void focusGained(SFocusEvent e);
    void focusLost(SFocusEvent e);
}
