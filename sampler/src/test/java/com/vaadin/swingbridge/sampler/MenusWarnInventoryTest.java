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
import com.vaadin.swingbridge.surrogates.MenuNode;
import com.vaadin.swingbridge.surrogates.SJMenuBar;
import vaadinx.EHelper;
import vaadinx.swing.ButtonGroup;
import vaadinx.swing.JCheckBoxMenuItem;
import vaadinx.swing.JFrame;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuBar;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JRadioButtonMenuItem;
import vaadinx.swing.JSeparator;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menus view + menubar API surface exit gate (D_menu_tree / SD_sjmenubar).
 * Both tests fail if any {@code EHelper.onUnimplemented} fires during
 * the asserted path.
 *
 * <ol>
 *   <li>{@link #inventory_menus_user_path} — {@link MenusView}
 *       end-to-end (construction, accelerator install, simulated
 *       click on each item via {@link MenuNode#onClick()}).</li>
 *   <li>{@link #inventory_menus_api_surface} — micro-driver over the
 *       JMenuBar / JMenu / JMenuItem / JCheckBoxMenuItem /
 *       JRadioButtonMenuItem API on an isolated fixture.</li>
 * </ol>
 */
class MenusWarnInventoryTest {

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
    void inventory_menus_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Menus");
        dump("Step 0b (MenusPanel swap — JMenuBar attaches inline)", warnings);

        // Locate the Vaadin MenuBar that the SJMenuBar surrogate
        // extends. There should be exactly one in the rendered DOM.
        List<com.vaadin.flow.component.menubar.MenuBar> menuBars =
                LocatorJ._find(com.vaadin.flow.component.menubar.MenuBar.class, spec -> spec.withCount(1));
        SJMenuBar bar = (SJMenuBar) menuBars.get(0);
        dump("Step 1 (MenuBar lookup)", warnings);

        // Walk the JDK menu tree the rebuild snapshot exposes; fire
        // each leaf's onClick to simulate browser-driven clicks. This
        // is the same Runnable SJMenuBar wires into Vaadin's
        // ClickEvent handler — exercising every accelerator path.
        clickEveryLeaf(bar.getCurrentTree());
        dump("Step 2 (click every menu item)", warnings);

        // ButtonGroup coordination assertion (D_buttongroup): the View menu's
        // Light/Dark JRadioButtonMenuItem pair is wrapped in a
        // ButtonGroup. After clickEveryLeaf walked Light then Dark
        // (insertion order), Dark should be the lone selection: the
        // group cascaded a deselect onto Light when Dark was clicked.
        MenuNode viewMenu = bar.getCurrentTree().get(2);
        // View children: [0]=toolbar (checkbox), [1]=separator,
        // [2]=Light (radio), [3]=Dark (radio).
        if (!"View".equals(viewMenu.text())) {
            throw new AssertionError("Expected View menu at index 2, got " + viewMenu.text());
        }
        MenuNode lightNode = viewMenu.children().get(2);
        MenuNode darkNode = viewMenu.children().get(3);
        if (!lightNode.checkable() || !darkNode.checkable()) {
            throw new AssertionError("Light and Dark must render checkable");
        }
        if (lightNode.checked()) {
            throw new AssertionError("Light must be deselected after Dark click — ButtonGroup cascade failed");
        }
        if (!darkNode.checked()) {
            throw new AssertionError("Dark must be selected after click");
        }

        WarnDump.println();
        WarnDump.println("=== menus user-path WARN total: " + warnings.size() + " ===");
    }

    private static void clickEveryLeaf(List<MenuNode> nodes) {
        for (MenuNode n : nodes) {
            if (n.separator()) continue;
            if (!n.children().isEmpty()) {
                clickEveryLeaf(n.children());
                continue;
            }
            if (n.onClick() != null) {
                n.onClick().run();
            }
        }
    }

    @Test
    void inventory_menus_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        dump("Fixture setup", warnings);

        // -- Bucket 17a: JMenuBar core --
        JMenuBar bar = new JMenuBar();
        dump("17a  new JMenuBar", warnings);

        JMenu file = new JMenu("File");
        bar.add(file);
        bar.getMenu(0);
        bar.getMenuCount();
        bar.getComponentIndex(file);
        dump("17a  JMenuBar.add + accessors", warnings);

        // -- Bucket 17b: JMenu children API --
        JMenuItem open = new JMenuItem("Open");
        JMenuItem save = new JMenuItem("Save");
        JMenuItem exit = new JMenuItem("Exit");
        file.add(open);
        file.add(save);
        file.addSeparator();
        file.add(exit);
        file.getItemCount();
        file.getItem(0);
        file.isTopLevelMenu();
        dump("17b  JMenu children + accessors", warnings);

        // -- Bucket 17c: accelerator round-trip --
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        open.getAccelerator();
        dump("17c  accelerator round-trip", warnings);

        // -- Bucket 17d: ActionListener / ItemListener / ChangeListener --
        java.awt.event.ActionListener al = e -> {};
        open.addActionListener(al);
        open.getActionListeners();
        open.removeActionListener(al);
        dump("17d  ActionListener add/get/remove", warnings);

        // -- Bucket 17e: text / actionCommand / enabled round-trip --
        save.setText("Save As");
        save.getText();
        save.setActionCommand("save-cmd");
        save.getActionCommand();
        save.setEnabled(false);
        save.isEnabled();
        save.setEnabled(true);
        dump("17e  text + actionCommand + enabled round-trip", warnings);

        // -- Bucket 17f: JCheckBoxMenuItem --
        JMenu view = new JMenu("View");
        bar.add(view);
        JCheckBoxMenuItem toolbar = new JCheckBoxMenuItem("Show toolbar", true);
        view.add(toolbar);
        toolbar.setState(false);
        toolbar.getState();
        toolbar.setSelected(true);
        toolbar.isSelected();
        java.awt.event.ItemListener il = e -> {};
        toolbar.addItemListener(il);
        toolbar.removeItemListener(il);
        javax.swing.event.ChangeListener cl = e -> {};
        toolbar.addChangeListener(cl);
        toolbar.removeChangeListener(cl);
        dump("17f  JCheckBoxMenuItem round-trip + listeners", warnings);

        // -- Bucket 17g: JRadioButtonMenuItem --
        JRadioButtonMenuItem light = new JRadioButtonMenuItem("Light", true);
        JRadioButtonMenuItem dark = new JRadioButtonMenuItem("Dark");
        view.add(light);
        view.add(dark);
        light.setSelected(false);
        light.isSelected();
        dump("17g  JRadioButtonMenuItem round-trip", warnings);

        // -- Bucket 17h: JFrame.setJMenuBar slot --
        frame.setJMenuBar(bar);
        frame.getJMenuBar();
        frame.setJMenuBar(null);
        frame.setJMenuBar(bar);
        dump("17h  JFrame.setJMenuBar slot", warnings);

        // -- Bucket 17i: nested submenu --
        JMenu help = new JMenu("Help");
        JMenu docs = new JMenu("Documentation");
        docs.add(new JMenuItem("README"));
        docs.add(new JMenuItem("Changelog"));
        help.add(docs);
        help.addSeparator();
        help.add(new JMenuItem("About"));
        bar.add(help);
        dump("17i  nested submenu", warnings);

        // -- Bucket 17j: JSeparator standalone surface --
        JSeparator sep = new JSeparator();
        sep.getOrientation();
        sep.setOrientation(JSeparator.VERTICAL);
        sep.getOrientation();
        dump("17j  JSeparator round-trip", warnings);

        // -- Frame attach --
        frame.setVisible(true);
        dump("17k  frame.setVisible(true)", warnings);

        WarnDump.println();
        WarnDump.println("=== menus API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the menus exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
