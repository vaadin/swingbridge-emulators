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
import vaadinx.swing.JSeparator;

import javax.swing.SwingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Standalone {@link JSeparator} (D_jseparator / SD_sjseparator) WARN inventory exit gate.
 * Both tests fail if any {@code onUnimplemented} fires along the asserted
 * path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "Separators", asserts the three demos construct WARN-free,
 *       finds the expected SJSeparator peers, and clicks the Demo 3 flip
 *       button so the live {@code setOrientation} path runs WARN-free
 *       end-to-end.</li>
 *   <li>{@link #inventory_jseparator_api_surface} — micro-driver over the
 *       JSeparator / SJSeparator WARN-free surface: both ctors, orientation
 *       round-trip, L&amp;F {@code getUIClassID} / {@code updateUI}.
 *       Expected-WARN paths ({@code getAccessibleContext}, surrogate
 *       {@code setUI}) live in the per-class unit tests.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire
 * from either layer surfaces.
 */
class SeparatorsWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
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

        Navigate.to("Separators");
        dump("Step 0b (SeparatorsPanel swap)", warnings);

        // Four standalone SJSeparator peers: two horizontal (Demo 1), one
        // vertical (Demo 2), one flip separator (Demo 3, HORIZONTAL at
        // start). Asserting count guards against a demo being dropped.
        List<com.vaadin.swingbridge.surrogates.SJSeparator> seps =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJSeparator.class, spec -> spec.withCount(4));
        dump("Step 1 (4 SJSeparator peers found)", warnings);

        long vertical = seps.stream()
                .filter(s -> s.getOrientation() == SwingConstants.VERTICAL).count();
        if (vertical != 1) {
            throw new AssertionError("Expected exactly 1 VERTICAL separator, got " + vertical);
        }
        dump("Step 2 (orientation mix: 3 H + 1 V)", warnings);

        // Demo 3 — click the flip button, exercising the live
        // setOrientation forwarding + emulator PCE path.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Flip orientation")));
        dump("Step 3 (Demo 3: flip orientation)", warnings);

        WarnDump.println();
        WarnDump.println("=== separators user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jseparator_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors --
        new JSeparator();
        new JSeparator(SwingConstants.HORIZONTAL);
        new JSeparator(SwingConstants.VERTICAL);
        dump("JSeparator  ctors (no-arg + both orientations)", warnings);

        // -- Orientation round-trip --
        JSeparator s = new JSeparator();
        s.getOrientation();
        s.setOrientation(SwingConstants.VERTICAL);
        s.setOrientation(SwingConstants.HORIZONTAL);
        dump("JSeparator  orientation round-trip", warnings);

        // -- L&F surface (WARN-free portion only — getAccessibleContext
        //    WARNs and is covered in the per-class unit tests) --
        s.getUIClassID();
        s.updateUI();
        dump("JSeparator  L&F (getUIClassID + updateUI)", warnings);

        WarnDump.println();
        WarnDump.println("=== JSeparator API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the Separators exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
