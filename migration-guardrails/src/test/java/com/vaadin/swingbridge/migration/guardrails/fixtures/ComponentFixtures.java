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

package com.vaadin.swingbridge.migration.guardrails.fixtures;

import com.vaadin.flow.component.button.Button;

import vaadinx.swing.JButton;

/**
 * Fixtures for the component gate — one per component type it matches by name. Both fields are
 * declared with a <i>subtype</i>, since resolving the hierarchy is the whole reason the gate exists
 * alongside a {@code grep 'static .*JFrame'}.
 */
public final class ComponentFixtures {

    private ComponentFixtures() {
    }

    /** The stand-in for {@code App.FRAME}: a stage-2 emulator component at JVM scope. */
    public static final class LeakyEmulator {
        public static JButton leaked;
    }

    /** The same leak after the app reaches stage 3 or 4 — the emulator name is gone, the bug isn't. */
    public static final class LeakyVaadin {
        public static Button leaked;
    }
}
