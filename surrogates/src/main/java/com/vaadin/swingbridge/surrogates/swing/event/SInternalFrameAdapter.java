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

/**
 * Empty implementation of {@link SInternalFrameListener} — extend and
 * override only the callbacks you care about. Mirrors JDK's
 * {@code javax.swing.event.InternalFrameAdapter}.
 */
public abstract class SInternalFrameAdapter implements SInternalFrameListener {
    @Override public void internalFrameOpened(SInternalFrameEvent e) {}
    @Override public void internalFrameClosing(SInternalFrameEvent e) {}
    @Override public void internalFrameClosed(SInternalFrameEvent e) {}
    @Override public void internalFrameIconified(SInternalFrameEvent e) {}
    @Override public void internalFrameDeiconified(SInternalFrameEvent e) {}
    @Override public void internalFrameActivated(SInternalFrameEvent e) {}
    @Override public void internalFrameDeactivated(SInternalFrameEvent e) {}
}
