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

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.html.Div;
import com.vaadin.swingbridge.surrogates.awt.ContainerMixin;

/**
 * Test-only surrogate backed by a Vaadin {@link Div} so layout-CSS dispatch
 * paths can be exercised without falling into
 * the "non-div host" guard. Mirrors {@link TestSurrogate}'s shape but extends
 * Div instead of Button.
 */
public class DivContainerSurrogate extends Div implements ContainerMixin {

    /** See {@link TestSurrogate#setEnabled} — same shadowing, same redirect. */
    @Override
    public void setEnabled(boolean enabled) {
        ContainerMixin.super.setEnabled(enabled);
    }
}
