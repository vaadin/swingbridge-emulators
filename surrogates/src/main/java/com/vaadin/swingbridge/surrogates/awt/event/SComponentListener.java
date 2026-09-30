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
 * Surrogate-side port of {@link java.awt.event.ComponentListener}. Receives
 * {@link SComponentEvent} (source = Vaadin {@link com.vaadin.flow.component.Component}).
 *
 * <p>Registrations are stored but no event currently fires (SD_event_port_stance).
 * Wiring lands when a downstream slice surfaces the corresponding Vaadin
 * lifecycle events (resize, attach/detach).
 */
public interface SComponentListener extends EventListener {
    void componentResized(SComponentEvent e);
    void componentMoved(SComponentEvent e);
    void componentShown(SComponentEvent e);
    void componentHidden(SComponentEvent e);
}
