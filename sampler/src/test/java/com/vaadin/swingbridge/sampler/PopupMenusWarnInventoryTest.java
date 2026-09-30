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
import vaadinx.swing.JCheckBoxMenuItem;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JPopupMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Popup-menu view + JPopupMenu API surface exit gate (D_jpopupmenu / SD_sjpopupmenu).
 * Both tests fail if any unexpected {@code EHelper.onUnimplemented}
 * fires during the asserted path.
 *
 * <ol>
 *   <li>{@link #inventory_popupmenus_user_path} — {@link PopupMenusPanel}
 *       end-to-end (construction with {@code setComponentPopupMenu} on two
 *       hosts, simulated click on every popup leaf via {@link MenuNode#onClick()}).</li>
 *   <li>{@link #inventory_popupmenus_api_surface} — micro-driver over the
 *       JPopupMenu API, then the enumerated intentional-WARN paths
 *       (programmatic open + listener families with no Vaadin counterpart).</li>
 * </ol>
 */
class PopupMenusWarnInventoryTest {

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
    }

    @Test
    void inventory_popupmenus_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("PopupMenus");
        // The panel ctor builds two JPopupMenus, populates them (leaf items,
        // a nested Export submenu, top-level separators, a JCheckBoxMenuItem)
        // and attaches each via setComponentPopupMenu — the full user path of
        // wiring a context menu. None of that may fire a stub WARN. (Vaadin
        // attaches the ContextMenu peers as virtual children of the target
        // labels, which Karibu's component lookup doesn't traverse, so the
        // per-leaf click exercise lives in the API-surface driver below +
        // the SJPopupMenu / JPopupMenu unit suites instead.)
        dump("Step 1 (PopupMenusPanel swap — setComponentPopupMenu on two hosts)", warnings);

        WarnDump.println();
        WarnDump.println("=== popupmenus user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_popupmenus_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Bucket 9a: JPopupMenu construction + children --
        JPopupMenu popup = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        JMenuItem save = new JMenuItem("Save");
        popup.add(open);
        popup.add(save);
        popup.add("Paste");
        popup.addSeparator();
        popup.getComponentCount();
        popup.getComponentIndex(open);
        dump("9a  JPopupMenu construction + children", warnings);

        // -- Bucket 9b: insert / remove / removeAll --
        JMenuItem first = new JMenuItem("First");
        popup.insert(first, 0);
        popup.remove(first);
        popup.remove(0);
        dump("9b  insert / remove", warnings);

        // -- Bucket 9c: nested JMenu --
        JMenu export = new JMenu("Export");
        export.add(new JMenuItem("PDF"));
        export.add(new JMenuItem("RTF"));
        popup.add(export);
        dump("9c  nested submenu", warnings);

        // -- Bucket 9d: JCheckBoxMenuItem in a popup --
        JCheckBoxMenuItem wrap = new JCheckBoxMenuItem("Wrap", true);
        popup.add(wrap);
        wrap.setState(false);
        wrap.getState();
        dump("9d  checkbox menu item", warnings);

        // -- Bucket 9e: label round-trip --
        popup.setLabel("ctx");
        popup.getLabel();
        dump("9e  label round-trip", warnings);

        // -- Bucket 9f: setComponentPopupMenu attach / round-trip / clear --
        JButton host = new JButton("host");
        host.setComponentPopupMenu(popup);
        host.getComponentPopupMenu();
        host.setComponentPopupMenu(null);
        dump("9f  setComponentPopupMenu attach + round-trip + clear", warnings);

        // -- Bucket 9g: silent (onNoop) paths --
        popup.pack();
        popup.setLightWeightPopupEnabled(false);
        popup.isLightWeightPopupEnabled();
        popup.menuSelectionChanged(false);
        dump("9g  silent onNoop paths", warnings);

        // -- Bucket 9h: getUIClassID --
        if (!"PopupMenuUI".equals(popup.getUIClassID())) {
            throw new AssertionError("getUIClassID should be PopupMenuUI");
        }
        dump("9h  getUIClassID", warnings);

        // -- Intentional-WARN paths (no Vaadin counterpart per SD_sjpopupmenu) --
        // Only the *open* is declined. show() drops the coordinates and then ends
        // in the JDK's own setVisible(true), so it accounts for both WARNs.
        JButton invoker = new JButton("inv");
        popup.show(invoker, 10, 20);
        assertWarnsExactly(warnings, "show/open-at-coordinates", "setVisible(true)/programmatic-open");

        // The hide is not declined: ContextMenuBase has close(), so this really closes.
        popup.setVisible(false);
        dump("9i  setVisible(false) closes the peer, WARN-free", warnings);

        popup.setVisible(true);               // programmatic open
        assertExactlyOneWarn(warnings, "setVisible(true)/programmatic-open");
        popup.setVisible(false);
        dump("9j  setVisible(false) again", warnings);

        // -- Bucket 9i: PopupMenuListener registration — WARN-free since the
        // OpenedChangeEvent peer→Swing sync landed (Q_popupmenu_visible_sync in D_jpopupmenu) --
        javax.swing.event.PopupMenuListener pl = new javax.swing.event.PopupMenuListener() {
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        };
        popup.addPopupMenuListener(pl);
        if (popup.getPopupMenuListeners().length != 1) {
            throw new AssertionError("expected 1 registered PopupMenuListener");
        }
        popup.removePopupMenuListener(pl);
        if (popup.getPopupMenuListeners().length != 0) {
            throw new AssertionError("expected 0 registered PopupMenuListeners after remove");
        }
        dump("9i  PopupMenuListener add/get/remove", warnings);

        WarnDump.println();
        WarnDump.println("=== popupmenus API-surface: WARN-free buckets clean; intentional WARNs accounted ===");
    }

    /** Like {@link #assertExactlyOneWarn}, for a call whose declined effects are more than one. */
    private static void assertWarnsExactly(List<String> warnings, String... fragments) {
        boolean ok = warnings.size() == fragments.length;
        for (String fragment : fragments) {
            ok &= warnings.stream().filter(w -> w.contains(fragment)).count() == 1;
        }
        if (!ok) {
            String msg = "Expected exactly one WARN per " + List.of(fragments) + ", got: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }

    private static void assertExactlyOneWarn(List<String> warnings, String fragment) {
        long matching = warnings.stream().filter(w -> w.contains(fragment)).count();
        if (warnings.size() != 1 || matching != 1) {
            String msg = "Expected exactly one WARN containing '" + fragment + "', got: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
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
                    + " stub WARN(s) fired — regression in the popupmenus exit gate: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
