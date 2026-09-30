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
 * Surrogate-side port of {@link java.awt.event.MouseAdapter}. Empty
 * implementations of every {@link SMouseListener} method so user code can
 * override only the ones it cares about (typically just
 * {@link #mouseClicked}).
 */
public abstract class SMouseAdapter implements SMouseListener {
    @Override public void mouseClicked(SMouseEvent e) {}
    @Override public void mousePressed(SMouseEvent e) {}
    @Override public void mouseReleased(SMouseEvent e) {}
    @Override public void mouseEntered(SMouseEvent e) {}
    @Override public void mouseExited(SMouseEvent e) {}
}
