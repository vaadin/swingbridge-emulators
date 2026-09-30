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

/**
 * Empty implementation of {@link SWindowListener} +
 * {@link SWindowFocusListener} + {@link SWindowStateListener} — extend
 * and override only the callbacks you care about. Mirrors JDK's single
 * {@code WindowAdapter} covering all three window listener interfaces.
 */
public abstract class SWindowAdapter
        implements SWindowListener, SWindowFocusListener, SWindowStateListener {

    @Override public void windowOpened(SWindowEvent e) {}
    @Override public void windowClosing(SWindowEvent e) {}
    @Override public void windowClosed(SWindowEvent e) {}
    @Override public void windowIconified(SWindowEvent e) {}
    @Override public void windowDeiconified(SWindowEvent e) {}
    @Override public void windowActivated(SWindowEvent e) {}
    @Override public void windowDeactivated(SWindowEvent e) {}

    @Override public void windowGainedFocus(SWindowEvent e) {}
    @Override public void windowLostFocus(SWindowEvent e) {}

    @Override public void windowStateChanged(SWindowEvent e) {}
}
