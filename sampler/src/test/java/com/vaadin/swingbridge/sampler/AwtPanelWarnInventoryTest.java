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
import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.GridLayout;
import vaadinx.awt.Panel;
import vaadinx.awt.event.ContainerEvent;
import vaadinx.awt.event.ContainerListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link vaadinx.awt.Panel} WARN inventory exit gate — D_awt_panel / SD_no_spanel (see
 * {@code D_awt_lane}). Every test fails if any
 * {@code onUnimplemented} fires along the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the AWT "Panel" leaf and
 *       drives the {@code ContainerListener} demo's add / remove controls.</li>
 *   <li>{@link #inventory_awt_panel_api_surface} — micro-driver over the
 *       surface a migrator actually touches on a Panel. <b>Deliberately not
 *       claimed exhaustive:</b> {@code java.awt.Panel}'s own four members are
 *       trivial, but everything else is inherited {@code vaadinx.awt.Container},
 *       and a documented chunk of Container legitimately WARNs — see the
 *       exclusion list on the method.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks feed one sink so a stub fire from
 * either layer surfaces.
 */
class AwtPanelWarnInventoryTest {

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

        Navigate.to("Panel");
        dump("Step 0b (AwtPanelPanel swap)", warnings);

        // Ten SButton peers across Demos 1-2 (six in the FlowLayout Panel, four
        // in each of the default-vs-null pair is eight — so 6 + 4 + 4 = 14).
        // A deliberate tripwire: the count fails if a demo is dropped.
        LocatorJ._find(com.vaadin.swingbridge.surrogates.SButton.class, spec -> spec.withCount(14));
        dump("Step 1 (14 AWT SButton peers across Demos 1-2)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add a Button")));
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add a Button")));
        dump("Step 2 (componentAdded ×2 through the ContainerListener)", warnings);

        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Remove last")));
        dump("Step 3 (componentRemoved through the ContainerListener)", warnings);

        // Remove-on-empty must be silent too: the demo guards on the count, so
        // this exercises the guard rather than Container's throw path.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Remove last")));
        dump("Step 4 (remove down to empty)", warnings);

        WarnDump.println();
        WarnDump.println("=== AWT Panel user-path WARN total: " + warnings.size() + " ===");
    }

    /**
     * The Container surface a Panel migrator uses. <b>Excluded on purpose</b>,
     * each a documented WARN whose inclusion would fail the gate for a
     * non-regression: {@code getAccessibleContext} (R_match_swing_errors sub-bucket (b));
     * {@code getComponentAt} ×2 / {@code locate} / {@code findComponentAt} ×2
     * and {@code getMousePosition} (pixel hit-testing — the server has no
     * browser geometry); the five focus-traversal setters (tier-3 focus is
     * permanently out per D_focus_managers); cross-container {@code setComponentZOrder};
     * and {@code setLayout} with a non-{@code CssEmittingLayoutManager}
     * (M1D_custom_layoutmanager's WARN + vertical-stack fallback).
     */
    @Test
    void inventory_awt_panel_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors, including the null-layout case --
        new Panel();
        new Panel(new FlowLayout());
        new Panel(null);
        dump("Panel  both ctors + Panel(null)", warnings);

        // -- setLayout / getLayout over the built-in managers and null --
        Panel p = new Panel();
        p.getLayout();
        p.setLayout(new BorderLayout(4, 4));
        p.setLayout(new GridLayout(2, 2));
        p.setLayout(null);
        p.setLayout(new FlowLayout());
        dump("Panel  setLayout / getLayout over FlowLayout, BorderLayout, GridLayout, null", warnings);

        // -- all five add overloads --
        vaadinx.awt.Component a = new vaadinx.awt.Label("a");
        vaadinx.awt.Component b = new vaadinx.awt.Label("b");
        vaadinx.awt.Component c = new vaadinx.awt.Label("c");
        vaadinx.awt.Component d = new vaadinx.awt.Label("d");
        vaadinx.awt.Component e = new vaadinx.awt.Label("e");
        Panel host = new Panel(new BorderLayout());
        host.add(a);
        host.add(b, BorderLayout.NORTH);
        host.add(c, 0);
        host.add("Center", d);
        host.add(e, BorderLayout.SOUTH, 1);
        dump("Panel  all five add overloads", warnings);

        // -- child access --
        host.getComponent(0);
        host.getComponents();
        host.getComponentCount();
        host.countComponents();
        host.isAncestorOf(a);
        host.getComponentZOrder(a);
        host.setComponentZOrder(a, 1);
        dump("Panel  child access + intra-container Z-order", warnings);

        // -- ContainerListener, with a real listener attached --
        ContainerListener listener = new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent ev) {}

            @Override
            public void componentRemoved(ContainerEvent ev) {}
        };
        host.addContainerListener(listener);
        host.getContainerListeners();
        host.add(new vaadinx.awt.Label("f"));
        host.remove(0);
        host.removeContainerListener(listener);
        dump("Panel  ContainerListener add / fire / remove", warnings);

        // -- removal --
        host.remove(a);
        host.removeAll();
        dump("Panel  remove(Component) / removeAll", warnings);

        // -- layout lifecycle --
        host.getInsets();
        host.insets();
        host.invalidate();
        host.doLayout();
        host.validate();
        host.layout();
        dump("Panel  insets + invalidate / doLayout / validate / layout()", warnings);

        // -- size hints (dummy values per R_layouts_close_enough, but not WARNs) --
        host.getPreferredSize();
        host.getMinimumSize();
        host.getMaximumSize();
        host.getAlignmentX();
        host.getAlignmentY();
        dump("Panel  size / alignment hints", warnings);

        // -- paint recursion bottoms out in onNoop, which does not feed warnHook --
        host.paintComponents(null);
        host.printComponents(null);
        dump("Panel  paintComponents / printComponents", warnings);

        // -- peer lifecycle + toString shape --
        host.addNotify();
        host.toString();
        dump("Panel  addNotify + toString (Container's layout= tail)", warnings);

        WarnDump.println();
        WarnDump.println("=== java.awt.Panel API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the AWT Panel exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
