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
 * Surrogate-side port of {@link java.awt.event.WindowListener}. Receives
 * {@link SWindowEvent} (source = Vaadin {@link com.vaadin.flow.component.Component}).
 *
 * <p>Non-focus, non-state ids dispatch here — {@code windowOpened},
 * {@code windowClosing}, {@code windowClosed}, {@code windowIconified},
 * {@code windowDeiconified}, {@code windowActivated}, {@code windowDeactivated}.
 * Focus ids route to {@link SWindowFocusListener}, state to
 * {@link SWindowStateListener}; id → method dispatch lives in
 * {@code SFrame.processWindowEvent}.
 *
 * <p>Iconify / deiconify / activate / deactivate are always inert under
 * our Dialog-backed peer (no OS window manager in a browser tab). They
 * remain on the interface so migrated code compiles and so a user
 * overriding {@code windowOpened} / {@code windowClosing} doesn't have to
 * implement the unused callbacks — use {@link SWindowAdapter}.
 */
public interface SWindowListener extends EventListener {
    void windowOpened(SWindowEvent e);
    void windowClosing(SWindowEvent e);
    void windowClosed(SWindowEvent e);
    void windowIconified(SWindowEvent e);
    void windowDeiconified(SWindowEvent e);
    void windowActivated(SWindowEvent e);
    void windowDeactivated(SWindowEvent e);
}
