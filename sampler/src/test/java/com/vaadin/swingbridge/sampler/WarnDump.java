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

/**
 * Console output of the WARN-inventory exit gates, off by default. Every gate prints its
 * banner and the stub WARNs it collected through here; on a routine build that is some three
 * hundred lines of noise for suites that pass, and a failing gate already carries its WARN
 * list in the assertion message. Run with {@code -Dsampler.dump=true} to see the banners.
 */
final class WarnDump {

    private static final boolean ENABLED = Boolean.getBoolean("sampler.dump");

    private WarnDump() {
    }

    static void println() {
        if (ENABLED) {
            System.out.println();
        }
    }

    static void println(String line) {
        if (ENABLED) {
            System.out.println(line);
        }
    }

    static void println(Object line) {
        if (ENABLED) {
            System.out.println(line);
        }
    }
}
