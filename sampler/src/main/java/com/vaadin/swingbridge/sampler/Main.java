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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.vaadinboot.VaadinBoot;
import org.jetbrains.annotations.NotNull;

public final class Main {
    public static void main(@NotNull String[] args) throws Exception {
        // useVirtualThreadsIfAvailable(false): vaadin-blocking-dialogs' loom runner mounts
        // each UI fiber onto the platform thread holding the VaadinSession lock, and supports
        // platform request threads only (R_match_swing_errors case (10)). Vaadin-Boot 13+
        // defaults to true on JDK 21+; we have to flip it.
        new VaadinBoot()
                .useVirtualThreadsIfAvailable(false)
                .run();
    }
}
