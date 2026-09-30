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
import com.vaadin.swingbridge.surrogates.SButton;
import vaadinx.EHelper;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.Button} WARN inventory exit gate — D_awt_button / SD_sbutton (see
 * {@code ideas/awt-widgets.md}). Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell, clicks
 *       the AWT "Button" leaf, asserts the panel's four {@link SButton} peers,
 *       and clicks through the actionCommand-falls-back-to-label rule, an
 *       explicit command, the AWT-beside-Swing residue case, and live
 *       {@code setLabel} / {@code setEnabled}.</li>
 *   <li>{@link #inventory_awt_button_api_surface} — micro-driver over the whole
 *       {@code java.awt.Button} API minus the expected-WARN
 *       {@code getAccessibleContext}, which lives in the per-class unit tests.
 *       Twelve methods is the entire class, so this bucket really is
 *       exhaustive.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtButtonWarnInventoryTest {

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

        Navigate.to("Button");
        dump("Step 0b (AwtButtonPanel swap)", warnings);

        // Four SButton peers: two in Demo 1, one in Demo 2, one in Demo 3.
        // Asserting the count guards against a demo being dropped. The Swing
        // JButtons in Demos 2 and 3 peer on SJButton, so they don't show up
        // here — which is itself the point: SButton and SJButton stay distinct
        // types (SD_mixin_mechanism).
        LocatorJ._find(SButton.class, spec -> spec.withCount(4));
        dump("Step 1 (4 SButton peers found)", warnings);

        // Demo 1 — the command-falls-back-to-label rule, then an explicit
        // command, both through a real browser click.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Plain AWT Button")));
        dump("Step 2 (Demo 1: click with fallback actionCommand)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("With actionCommand")));
        dump("Step 3 (Demo 1: click with explicit actionCommand)", warnings);

        // Demo 2 — the residue case: AWT leaf and Swing sibling in one panel.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("java.awt.Button")));
        dump("Step 4 (Demo 2: AWT Button beside a JButton)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("javax.swing.JButton")));
        dump("Step 5 (Demo 2: the Swing sibling)", warnings);

        // Demo 3 — live setLabel / setEnabled push to the peer.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Toggle label")));
        dump("Step 6 (Demo 3: setLabel)", warnings);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Toggle enabled")));
        dump("Step 7 (Demo 3: setEnabled)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT Button user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_awt_button_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors --
        new vaadinx.awt.Button();
        new vaadinx.awt.Button("OK");
        new vaadinx.awt.Button(null);
        dump("Button  ctors (no-arg + label + null label)", warnings);

        // -- label --
        vaadinx.awt.Button b = new vaadinx.awt.Button("Save");
        b.getLabel();
        b.setLabel("Save as");
        b.setLabel(null);
        b.setLabel("Save");
        dump("Button  label round-trip (including null)", warnings);

        // -- actionCommand --
        b.getActionCommand();
        b.setActionCommand("save-file");
        b.getActionCommand();
        b.setActionCommand(null);
        dump("Button  actionCommand round-trip + fallback", warnings);

        // -- listeners + dispatch --
        ActionListener l = e -> {};
        b.addActionListener(l);
        b.getActionListeners();
        b.getListeners(ActionListener.class);
        b.removeActionListener(l);
        // processEvent / processActionEvent are protected, so they can't be
        // driven from here; vaadinx.awt.ButtonTest covers both (same package)
        // including the peel-ActionEvent-off-first routing.
        dump("Button  ActionListener add/remove/query", warnings);

        // -- peer lifecycle + toString shape --
        b.addNotify();
        b.toString();
        dump("Button  addNotify + toString (paramString)", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.Button API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT Button exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
