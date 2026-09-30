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

package com.vaadin.swingbridge.surrogates.swing.event;

import java.util.EventListener;

/**
 * Surrogate-side port of {@link javax.swing.event.InternalFrameListener}.
 * Receives {@link SInternalFrameEvent} (source = Vaadin
 * {@link com.vaadin.flow.component.Component}).
 *
 * <p>Server-side-fire-only (SD_listeners_without_analog shape), driven by {@code SJInternalFrame}'s
 * lifecycle (SD_internalframe_fire_seams): {@code internalFrameOpened} on first show,
 * {@code internalFrameClosing} on the close-X, {@code internalFrameClosed}
 * on dispose, {@code internalFrameActivated} / {@code Deactivated} on
 * selection. {@code internalFrameIconified} / {@code Deiconified} stay
 * inert on the surrogate — minimize is emulator-only (SD_no_surrogate_iconify); they remain
 * on the interface so migrated code compiles and so a user overriding one
 * callback via {@link SInternalFrameAdapter} needn't implement the rest.
 */
public interface SInternalFrameListener extends EventListener {
    void internalFrameOpened(SInternalFrameEvent e);
    void internalFrameClosing(SInternalFrameEvent e);
    void internalFrameClosed(SInternalFrameEvent e);
    void internalFrameIconified(SInternalFrameEvent e);
    void internalFrameDeiconified(SInternalFrameEvent e);
    void internalFrameActivated(SInternalFrameEvent e);
    void internalFrameDeactivated(SInternalFrameEvent e);
}
