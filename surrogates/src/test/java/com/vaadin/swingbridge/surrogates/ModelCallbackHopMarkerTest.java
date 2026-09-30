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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A surrogate hops onto its UI thread only inside a callback from a Swing model:
 * R_tolerate_off_ui_thread's surrogate limb, gated over {@code src/main}.
 *
 * <p>Surrogates are Vaadin components and are manipulated from the UI thread. The one exception
 * is a model a background thread mutates directly, whose event reaches the surrogate without
 * passing any emulator method (SD_background_model_hop). So every {@code SHelper.runOnOwnerUI}
 * call site carries, on the line above it, the marker naming the model callback it runs in. A
 * surrogate <em>setter</em> never hops; the emulator's {@code withPeer} does that.
 */
class ModelCallbackHopMarkerTest {

    private static final Pattern MARKER = Pattern.compile(
            "^\\s*// Allowed by R_tolerate_off_ui_thread because callback from model: \\S.*$");

    @Test
    @DisplayName("every SHelper.runOnOwnerUI call site is marked as a model callback")
    void everyHopIsAMarkedModelCallback() throws IOException {
        Path root = Path.of("src/main/java");
        List<String> unmarked = new ArrayList<>();
        int sites = 0;
        List<Path> sources;
        try (Stream<Path> s = Files.walk(root)) {
            sources = s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        for (Path file : sources) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (!line.contains("SHelper.runOnOwnerUI(") || line.startsWith("*") || line.startsWith("//")) {
                    continue;
                }
                sites++;
                if (i == 0 || !MARKER.matcher(lines.get(i - 1)).matches()) {
                    unmarked.add(root.relativize(file) + ":" + (i + 1));
                }
            }
        }
        assertTrue(sites > 0, "found no SHelper.runOnOwnerUI call sites at all — has the helper moved?");
        assertEquals(List.of(), unmarked, "SHelper.runOnOwnerUI is only for callbacks from a Swing model. "
                + "Mark the line above with '// Allowed by R_tolerate_off_ui_thread because callback from "
                + "model: <the listener>', or, for a surrogate setter, hop emulator-side with withPeer "
                + "instead");
    }
}
