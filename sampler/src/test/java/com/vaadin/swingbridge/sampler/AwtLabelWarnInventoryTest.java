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
import com.vaadin.swingbridge.surrogates.SLabel;
import vaadinx.EHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.Label} WARN inventory exit gate — D_awt_label / SD_slabel (see
 * {@code ideas/awt-widgets.md}). Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "Label" leaf,
 *       asserts four {@link SLabel} peers (the three fixed alignments plus the
 *       live one), and clicks through {@code setAlignment} + {@code setText}.</li>
 *   <li>{@link #inventory_awt_label_api_surface} — micro-driver over the whole
 *       {@code java.awt.Label} surface: three ctors, text including null, all
 *       three alignments, {@code addNotify} and {@code toString}. An exhaustive
 *       bucket — the class has nothing else.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtLabelWarnInventoryTest {

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

        Navigate.to("Label");
        dump("Step 0b (AwtLabelPanel swap)", warnings);

        // Four SLabel peers: the three fixed alignments plus the live one. The
        // Swing JLabels in the demo captions peer on SJLabel, so the count
        // isolates the AWT lane.
        LocatorJ._find(SLabel.class, spec -> spec.withCount(4));
        dump("Step 1 (4 SLabel peers found)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Cycle alignment")));
        dump("Step 2 (setAlignment + setText)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT Label user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_awt_label_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors --
        new vaadinx.awt.Label();
        new vaadinx.awt.Label("Total");
        new vaadinx.awt.Label(null);
        new vaadinx.awt.Label("Total", vaadinx.awt.Label.RIGHT);
        dump("Label  ctors (no-arg + text + null text + text/alignment)", warnings);

        // -- text --
        vaadinx.awt.Label l = new vaadinx.awt.Label("Total");
        l.getText();
        l.setText("Total: 42");
        l.setText(null);
        l.setText("Total");
        dump("Label  text round-trip (including null)", warnings);

        // -- alignment --
        l.getAlignment();
        l.setAlignment(vaadinx.awt.Label.LEFT);
        l.setAlignment(vaadinx.awt.Label.CENTER);
        l.setAlignment(vaadinx.awt.Label.RIGHT);
        l.getAlignment();
        dump("Label  all three alignments + readback", warnings);

        // -- peer lifecycle + toString shape --
        l.addNotify();
        l.toString();
        dump("Label  addNotify + toString (paramString)", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.Label API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT Label exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
