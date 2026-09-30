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
import com.vaadin.flow.component.html.Input;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJColorChooser;
import vaadinx.EHelper;
import vaadinx.swing.JColorChooser;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JColorChooser} WARN inventory exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code SHelper.onUnimplemented} fires along
 * the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigate to the Sampler shell, click
 *       "ColorChoosers", simulate a browser pick on the embedded chooser (model
 *       + ChangeListener readout), then drive the blocking {@code showDialog}
 *       button (VT park → OK) and assert it all runs WARN-free.</li>
 *   <li>{@link #inventory_jcolorchooser_api_surface} — a micro-driver over the
 *       WARN-free supported surface. The R_vaadin_first drop paths (setPreviewPanel /
 *       setChooserPanels) are NOT exercised here — they WARN by design and are
 *       covered in the per-class unit tests.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks are wired to a single sink so a stub
 * fire from either layer surfaces.
 */
class ColorChoosersWarnInventoryTest {

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

        Navigate.to("ColorChoosers");
        dump("Step 0b (ColorChoosersPanel swap)", warnings);

        // Demo 1 — exactly one SJColorChooser (the embedded chooser) is in the
        // DOM before the dialog opens. Simulate a browser color pick by writing
        // through the inherited Input value; the ChangeListener updates the
        // readout label. The whole R_swing_is_truth/R_callswing_envelope model-sync chain must run WARN-free.
        SJColorChooser embedded = LocatorJ._get(SJColorChooser.class);
        LocatorJ._setValue((Input) embedded, "#00ff00");
        if (!new Color(0, 255, 0).equals(embedded.getColor())) {
            throw new AssertionError("embedded chooser did not pick up the browser color: "
                    + embedded.getColor());
        }
        dump("Step 1 (embedded chooser browser pick → model + ChangeListener)", warnings);

        // Demo 2 — click "Pick a color…". The emulator JButton's ActionListener
        // runs showDialog under EHelper.callSwing; the internal modal JDialog
        // parks the click VT. _click returns once parked.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Pick a color…")));
        dump("Step 2 (showDialog opens — VT parked)", warnings);

        // Click OK — disposes the dialog, unparks the VT, showDialog returns the
        // pane's color and the handler updates the dialog readout.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("OK")));
        dump("Step 3 (OK dispose → showDialog returns, readout updates)", warnings);

        WarnDump.println();
        WarnDump.println("=== color-choosers user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jcolorchooser_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors --
        new JColorChooser();
        new JColorChooser(Color.RED);
        new JColorChooser(new javax.swing.colorchooser.DefaultColorSelectionModel(Color.GREEN));
        dump("JColorChooser  ctors", warnings);

        // -- Color round-trip (all four setters) --
        JColorChooser cc = new JColorChooser();
        cc.setColor(Color.BLUE);
        cc.getColor();
        cc.setColor(0x10, 0x20, 0x30);
        cc.setColor(0x405060);
        dump("JColorChooser  setColor / getColor round-trip", warnings);

        // -- SelectionModel swap + PCE --
        cc.setSelectionModel(new javax.swing.colorchooser.DefaultColorSelectionModel(Color.MAGENTA));
        cc.getSelectionModel();
        dump("JColorChooser  setSelectionModel round-trip", warnings);

        // -- dragEnabled round-trip (silent) --
        cc.setDragEnabled(true);
        cc.getDragEnabled();
        cc.setDragEnabled(false);
        dump("JColorChooser  setDragEnabled round-trip", warnings);

        // -- ChangeListener add/remove, on the model as in the JDK --
        javax.swing.event.ChangeListener listener = e -> {};
        cc.getSelectionModel().addChangeListener(listener);
        cc.setColor(Color.ORANGE);
        cc.getSelectionModel().removeChangeListener(listener);
        dump("JColorChooser  model ChangeListener add/remove", warnings);

        // -- L&F surface --
        cc.getUIClassID();
        cc.updateUI();
        cc.getUI();
        cc.setUI(null);
        dump("JColorChooser  L&F stubs", warnings);

        WarnDump.println();
        WarnDump.println("=== JColorChooser API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the ColorChoosers exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
