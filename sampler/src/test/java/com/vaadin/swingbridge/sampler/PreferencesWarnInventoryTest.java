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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.util.prefs.VaadinPreferencesFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@code java.util.prefs.Preferences} WARN inventory exit gate (D_preferences). Both tests
 * fail if any {@code EHelper.onUnimplemented} / {@code SHelper.onUnimplemented}
 * fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigate to the Sampler shell, click
 *       "Preferences" (which reads the userRoot-family settings on panel build),
 *       then Save (put + flush) and Reset (remove), all WARN-free.</li>
 *   <li>{@link #inventory_preferences_api_surface} — a micro-driver over the
 *       WARN-free userRoot surface (get/put, typed accessors, keys,
 *       childrenNames, child nodes, remove, flush, removeNode). {@code systemRoot}
 *       is NOT exercised here — it WARNs by design and is covered in the
 *       {@code :emulators} unit test.</li>
 * </ol>
 *
 * <p>Runs in {@link VaadinPreferencesFactory#setTestMode test mode} so the per-UI
 * cache stays in-memory (no browser round-trip) — MockVaadin has no real
 * localStorage to warm from.
 */
class PreferencesWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
        VaadinPreferencesFactory.setTestMode(true);
    }

    @AfterEach
    void tearDown() {
        VaadinPreferencesFactory.setTestMode(false);
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        // Building the panel reads userRoot-family prefs (get / getBoolean /
        // getInt + putInt for the visit counter) on the demo-swap VT.
        Navigate.to("Preferences");
        dump("Step 0b (PreferencesPanel build → reads settings)", warnings);

        // Save — put + putBoolean + flush.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Save")));
        dump("Step 1 (Save → put + flush)", warnings);

        // Reset — remove three keys.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Reset")));
        dump("Step 2 (Reset → remove)", warnings);

        WarnDump.println();
        WarnDump.println("=== preferences user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_preferences_api_surface() throws BackingStoreException {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        Preferences root = Preferences.userRoot();
        root.put("name", "value");
        root.get("name", "def");
        root.remove("name");
        dump("Preferences  put / get / remove", warnings);

        Preferences node = Preferences.userNodeForPackage(PreferencesWarnInventoryTest.class);
        node.putInt("width", 1024);
        node.getInt("width", 0);
        node.putBoolean("maximized", true);
        node.getBoolean("maximized", false);
        node.putLong("ts", 42L);
        node.getLong("ts", 0L);
        node.putDouble("ratio", 1.5);
        node.getDouble("ratio", 0.0);
        dump("Preferences  typed accessors", warnings);

        node.keys();
        node.node("child").put("k", "v");
        node.childrenNames();
        node.flush();
        node.node("child").removeNode();
        dump("Preferences  keys / childrenNames / child node / flush / removeNode", warnings);

        WarnDump.println();
        WarnDump.println("=== preferences API-surface WARN total across buckets above ===");
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the Preferences exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
