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
 * Surrogate-side port of {@link java.awt.event.MouseListener}. Identical to
 * AWT's five-method shape (mouseClicked / mousePressed / mouseReleased /
 * mouseEntered / mouseExited) with the event parameter retyped to
 * {@link SMouseEvent} — its source is a Vaadin {@link com.vaadin.flow.component.Component},
 * not an AWT one. Use {@link SMouseAdapter} for partial overrides.
 *
 * <p>Today's wire fires {@code mouseClicked} only — backed by Vaadin's
 * {@code ClickNotifier}. The press / release / enter / exit overloads are
 * part of the JDK contract and exist for migrated user code that implements
 * them, but never fire under the current surrogate-layer subscription.
 */
public interface SMouseListener extends EventListener {
    void mouseClicked(SMouseEvent e);
    void mousePressed(SMouseEvent e);
    void mouseReleased(SMouseEvent e);
    void mouseEntered(SMouseEvent e);
    void mouseExited(SMouseEvent e);
}
