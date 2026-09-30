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
import vaadinx.swing.JProgressBar;

import javax.swing.DefaultBoundedRangeModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JProgressBar} WARN inventory exit gate. Both
 * tests fail if any {@code EHelper.onUnimplemented} fires along the
 * asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "ProgressBars", and asserts {@link ProgressBarsPanel}
 *       constructs two demos (determinate / indeterminate) without any
 *       stub fire. Validates the BoundedRangeModel sync, indeterminate
 *       property write-through, and string overlay (data attribute) all
 *       wire WARN-free.</li>
 *   <li>{@link #inventory_jprogressbar_api_surface} — micro-driver over
 *       the JProgressBar / SJProgressBar API buckets. The expected-WARN
 *       paths (R_vaadin_first {@code setBorderPainted(false)}, {@code setOrientation(VERTICAL)})
 *       are explicitly NOT exercised here — those are covered in the
 *       per-class unit tests. Goal is to lock in the WARN-free supported
 *       surface as a regression gate.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks are wired to a single sink so
 * a stub fire from either layer surfaces.
 */
class ProgressBarsWarnInventoryTest {

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

        Navigate.to("ProgressBars");
        dump("Step 0b (ProgressBarsPanel swap)", warnings);

        // Two SJProgressBar peers should now be in the DOM (one per demo).
        // The instance count is asserted explicitly so a future refactor that
        // accidentally drops a demo trips this gate.
        List<com.vaadin.swingbridge.surrogates.SJProgressBar> bars =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJProgressBar.class, spec -> spec.withCount(2));
        dump("Step 1 (2 SJProgressBar peers found)", warnings);

        // Exactly one bar should be in indeterminate mode (Demo 2).
        long indeterminateCount = bars.stream()
                .filter(b -> b.isIndeterminate()).count();
        if (indeterminateCount != 1) {
            throw new AssertionError(
                    "Expected 1 indeterminate bar, got indeterminate="
                            + indeterminateCount);
        }
        dump("Step 2 (indeterminate placement asserted)", warnings);

        // Exercise the "Step +10" button on Demo 1's determinate bar:
        // ActionListener fires under EHelper.callSwing → setValue → model
        // ChangeEvent → emulator fanout. Asserts the BoundedRangeModel
        // sync chain runs WARN-free under a real click.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Step +10")));
        dump("Step 3 (Step +10 click)", warnings);

        WarnDump.println();
        WarnDump.println("=== progressbars user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jprogressbar_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors -- (the (int orientation) and (orientation, min, max)
        //    ctors with VERTICAL are exercised in the per-class unit tests
        //    since VERTICAL itself WARNs; the inventory only walks the
        //    WARN-free HORIZONTAL paths.)
        new JProgressBar();
        dump("JProgressBar  new JProgressBar()", warnings);

        new JProgressBar(10, 250);
        dump("JProgressBar  new JProgressBar(int min, int max)", warnings);

        new JProgressBar(javax.swing.SwingConstants.HORIZONTAL, 0, 200);
        dump("JProgressBar  new JProgressBar(int orientation, int min, int max)", warnings);

        new JProgressBar(new DefaultBoundedRangeModel(50, 0, 0, 100));
        dump("JProgressBar  new JProgressBar(BoundedRangeModel)", warnings);

        // -- Value / min / max round-trip via the model --
        JProgressBar p = new JProgressBar(0, 100);
        p.setValue(40);
        p.getValue();
        p.setMinimum(10);
        p.setMaximum(200);
        p.getMinimum();
        p.getMaximum();
        p.getPercentComplete();
        dump("JProgressBar  value / min / max round-trip", warnings);

        // -- Model swap --
        p.setModel(new DefaultBoundedRangeModel(0, 0, -10, 90));
        p.getModel();
        dump("JProgressBar  setModel round-trip", warnings);

        // -- Indeterminate round-trip --
        p.setIndeterminate(true);
        p.isIndeterminate();
        p.setIndeterminate(false);
        dump("JProgressBar  setIndeterminate round-trip", warnings);

        // -- Orientation: only HORIZONTAL is WARN-free; VERTICAL is R_vaadin_first
        //    drop-and-WARN per SJProgressBar javadoc and exercised in
        //    the per-class unit tests, not here.
        p.setOrientation(javax.swing.SwingConstants.HORIZONTAL);
        p.getOrientation();
        dump("JProgressBar  setOrientation(HORIZONTAL) round-trip", warnings);

        // -- String / stringPainted round-trip (CSS ::after overlay) --
        p.setStringPainted(true);
        p.isStringPainted();
        p.setString("Loading 50%");
        p.getString();
        p.setString(null);   // back to percent default
        p.getString();
        p.setStringPainted(false);
        dump("JProgressBar  setString / setStringPainted round-trip", warnings);

        // -- BorderPainted: only the true setting is WARN-free; false WARNs.
        //    Exercise the default-true round-trip here. The false WARN is
        //    covered in JProgressBarTest, not the inventory gate.
        p.setBorderPainted(true);
        p.isBorderPainted();
        dump("JProgressBar  setBorderPainted(true) round-trip", warnings);

        // -- ChangeListener add/get/remove --
        javax.swing.event.ChangeListener listener = e -> {};
        p.addChangeListener(listener);
        p.getChangeListeners();
        p.removeChangeListener(listener);
        dump("JProgressBar  ChangeListener add/get/remove", warnings);

        // -- L&F surface --
        p.getUIClassID();
        p.updateUI();
        p.getUI();
        p.setUI(null);
        dump("JProgressBar  L&F stubs", warnings);

        WarnDump.println();
        WarnDump.println("=== JProgressBar API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the ProgressBars exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
