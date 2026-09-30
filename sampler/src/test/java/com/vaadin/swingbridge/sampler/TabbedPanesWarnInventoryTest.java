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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTabbedPane;

import javax.swing.SwingConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JTabbedPane} WARN inventory exit gate. Both
 * tests fail if any {@code EHelper.onUnimplemented} fires along the
 * asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "TabbedPanes", asserts both demos construct WARN-free
 *       (plain tabs + multi-case-open with custom close-button headers).</li>
 *   <li>{@link #inventory_jtabbedpane_api_surface} — micro-driver over
 *       the JTabbedPane / SJTabbedPane API buckets in the WARN-free set:
 *       all three ctors, addTab variants, insertTab, removeTabAt,
 *       setSelectedIndex (+ model-direct), setTitleAt / setToolTipTextAt /
 *       setEnabledAt, setTabComponentAt (+ null clear), indexOf* lookups,
 *       getComponentAt, setTabLayoutPolicy(SCROLL) silent-accept,
 *       addImpl String/Integer/null dispatch, paramString tail.
 *       Expected-WARN paths (setTabPlacement(non-TOP),
 *       setTabLayoutPolicy(WRAP), setModel, setMnemonicAt,
 *       setBackgroundAt/ForegroundAt, setDisabledIconAt, getBoundsAt,
 *       indexAtLocation, getUI/setUI) live in the per-class unit tests.</li>
 * </ol>
 */
class TabbedPanesWarnInventoryTest {

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

        Navigate.to("TabbedPanes");
        dump("Step 0b (TabbedPanesPanel swap)", warnings);

        // Two SJTabbedPane peers expected: 1 plain + 1 closable.
        List<com.vaadin.swingbridge.surrogates.SJTabbedPane> tabbedPanes =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJTabbedPane.class, spec -> spec.withCount(2));
        dump("Step 1 (2 SJTabbedPane peers found)", warnings);

        // Verify the closable demo has 3 tabs initially.
        com.vaadin.swingbridge.surrogates.SJTabbedPane closable = tabbedPanes.get(1);
        if (closable.getTabCount() != 3) {
            throw new AssertionError(
                    "Expected closable demo to have 3 tabs, got " + closable.getTabCount());
        }
        dump("Step 2 (closable demo has 3 tabs)", warnings);

        // Click a tab's close button and verify the tab is removed. The
        // closable demo's header is a JPanel containing a JLabel + a close
        // JButton("x"). Scope the lookup to the first tab so the "x" button
        // is unambiguous — _get then asserts exactly-one and dumps the tree
        // if the header structure ever changes.
        com.vaadin.flow.component.Component firstTab = closable.getTabAt(0);
        com.vaadin.flow.component.button.Button closeButton = LocatorJ._get(
                firstTab, com.vaadin.flow.component.button.Button.class,
                spec -> spec.withText("x"));
        LocatorJ._click(closeButton);
        if (closable.getTabCount() != 2) {
            throw new AssertionError(
                    "Expected 2 tabs after close-click, got " + closable.getTabCount());
        }
        dump("Step 3 (close button drops a tab — 2 remain)", warnings);

        WarnDump.println();
        WarnDump.println("=== TabbedPanes user-path WARN total: "
                + warnings.size() + " ===");
    }

    @Test
    void inventory_jtabbedpane_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors (3 variants, all defaults silent) --
        new JTabbedPane();
        new JTabbedPane(SwingConstants.TOP);
        new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        dump("JTabbedPane  ctors (3 variants)", warnings);

        // -- addTab variants --
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton("a"));
        tp.addTab("B", null, new JButton("b"));
        tp.addTab("C", null, new JButton("c"), "tip-C");
        dump("JTabbedPane  addTab variants", warnings);

        // -- insertTab + bounds-OK indices --
        tp.insertTab("M", null, new JButton("m"), null, 1);
        tp.insertTab("N", null, new JButton("n"), null, tp.getTabCount());
        dump("JTabbedPane  insertTab", warnings);

        // -- removeTabAt + removeAll --
        tp.removeTabAt(0);
        JTabbedPane tp2 = new JTabbedPane();
        tp2.addTab("X", new JButton()); tp2.addTab("Y", new JButton());
        tp2.removeAll();
        dump("JTabbedPane  removeTabAt + removeAll", warnings);

        // -- Selection cycle --
        tp.setSelectedIndex(0);
        tp.getSelectedIndex();
        tp.setSelectedIndex(1);
        tp.getSelectedComponent();
        tp.setSelectedComponent(tp.getComponentAt(0));
        // model-direct path
        tp.getModel().setSelectedIndex(2);
        dump("JTabbedPane  selection cycle", warnings);

        // -- Per-tab title / icon / tooltip / enabled --
        tp.setTitleAt(0, "Renamed");
        tp.getTitleAt(0);
        tp.setIconAt(0, null);  // null is silent
        tp.setToolTipTextAt(0, "tip");
        tp.getToolTipTextAt(0);
        tp.setEnabledAt(0, false);
        tp.isEnabledAt(0);
        tp.setEnabledAt(0, true);
        dump("JTabbedPane  per-tab title/icon/tooltip/enabled", warnings);

        // -- setTabComponentAt (custom + null clear) --
        JPanel custom = new JPanel();
        custom.add(new JLabel("custom"));
        tp.setTabComponentAt(0, custom);
        tp.getTabComponentAt(0);
        tp.setTabComponentAt(0, null);
        dump("JTabbedPane  setTabComponentAt", warnings);

        // -- setComponentAt (replace content under tab) --
        tp.setComponentAt(0, new JButton("new"));
        dump("JTabbedPane  setComponentAt", warnings);

        // -- Index lookups --
        tp.indexOfTab("Renamed");
        tp.indexOfComponent(tp.getComponentAt(0));
        tp.indexOfTabComponent(custom);
        tp.getComponentAt(0);
        tp.getTabCount();
        tp.getTabRunCount();
        dump("JTabbedPane  index lookups", warnings);

        // -- ChangeListener round-trip --
        javax.swing.event.ChangeListener listener = e -> { /* observe */ };
        tp.addChangeListener(listener);
        tp.removeChangeListener(listener);
        tp.getChangeListeners();
        dump("JTabbedPane  ChangeListener round-trip", warnings);

        // -- Tab placement (TOP only — silent) + layout policy (SCROLL silent) --
        tp.setTabPlacement(SwingConstants.TOP);
        tp.getTabPlacement();
        tp.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tp.getTabLayoutPolicy();
        dump("JTabbedPane  tab placement TOP + layout policy SCROLL", warnings);

        // -- addImpl constraint dispatch (String + null + Integer) --
        JTabbedPane tp3 = new JTabbedPane();
        tp3.add(new JButton("a"), "Alpha");
        JButton namedB = new JButton("b");
        namedB.setName("Bravo");
        tp3.add(namedB);
        JButton namedC = new JButton("c");
        namedC.setName("Charlie");
        tp3.add(namedC, Integer.valueOf(1));
        dump("JTabbedPane  addImpl String/null/Integer", warnings);

        // -- L&F surface (WARN-free portion only) --
        tp.getUIClassID();
        tp.updateUI();
        dump("JTabbedPane  L&F (getUIClassID + updateUI)", warnings);

        WarnDump.println();
        WarnDump.println("=== JTabbedPane API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the TabbedPanes exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
