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
 * Surrogate-side port of {@link java.awt.event.WindowFocusListener}.
 * Receives {@link SWindowEvent} for {@link SWindowEvent#WINDOW_GAINED_FOCUS} /
 * {@link SWindowEvent#WINDOW_LOST_FOCUS}.
 *
 * <p>Always inert today — we don't track browser tab focus (no isFocused
 * server-side signal, no dispatch path in {@code SFrame.setVisible}).
 * Storage via {@code EventListenerList} matches JDK shape; firing can
 * be wired when a migration target needs it.
 */
public interface SWindowFocusListener extends EventListener {
    void windowGainedFocus(SWindowEvent e);
    void windowLostFocus(SWindowEvent e);
}
