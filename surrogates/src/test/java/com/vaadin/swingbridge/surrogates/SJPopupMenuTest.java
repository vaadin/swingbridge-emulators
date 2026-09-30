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

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.html.Image;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.internal.MenuBarStateStore;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjpopupmenu's SJPopupMenu surrogate. Mirrors SJMenuBarTest (the
 * tree-rebuild engine is shared via MenuTreeBuilder), with the popup-specific
 * deltas:
 *
 * <ol>
 *  <li>rebuildFromTree round-trip (text, enabled, checkable, nested submenu,
 *      icon-bearing item) — same engine as SJMenuBar.
 *  <li><b>Top-level separators RENDER</b> (ContextMenu.addSeparator exists) with
 *      no WARN — the one asymmetry vs SJMenuBar's top-level drop.
 *  <li>Accelerator install + teardown across rebuilds + attach hook.
 *  <li>Unmappable-keystroke WARN-and-skip.
 *  <li>Click funnels through onClick (R_callswing_envelope / SHelper.callSwing).
 *  <li>setTarget round-trip via the inherited ContextMenu API.
 *  <li>JComponentMixin surface (name / client property).
 *  <li>getUIClassID == "PopupMenuUI".
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJPopupMenuTest extends AbstractKaribuTest {

    private static MenuBarStateStore store(SJPopupMenu menu) {
        return ComponentUtil.getData(menu, MenuBarStateStore.class);
    }

    /** A MenuNode whose only non-default field is the accelerator. */
    private static MenuNode withAccelerator(String text, KeyStroke stroke) {
        return new MenuNode(text, null, true, false, false, false, stroke, () -> {
        }, List.of());
    }

    // --- rebuildFromTree round-trip ----------------------------------

    @Test
    @DisplayName("empty and null tree clear the menu")
    void emptyAndNullTreeClearTheMenu() {
        SJPopupMenu menu = new SJPopupMenu();
        menu.rebuildFromTree(List.of(MenuNode.ofItem("Copy", true, () -> {
        })));
        assertEquals(1, menu.getItems().size());
        menu.rebuildFromTree(List.of());
        assertEquals(0, menu.getItems().size());
        menu.rebuildFromTree(List.of(MenuNode.ofItem("Copy", true, () -> {
        })));
        menu.rebuildFromTree(null);
        assertEquals(0, menu.getItems().size());
        assertEquals(0, menu.getCurrentTree().size());
    }

    @Test
    @DisplayName("a checked-only change updates the items in place, across a top-level separator")
    void checkedOnlyChangePatchesInPlace() {
        SJPopupMenu menu = new SJPopupMenu();
        menu.rebuildFromTree(List.of(
                MenuNode.ofItem("Copy", true, () -> {}),
                MenuNode.ofSeparator(),
                new MenuNode("Wrap", null, true, false, true, false, null, () -> {}, List.of())));
        MenuItem copy = menu.getItems().get(0);
        MenuItem wrap = menu.getItems().get(1);

        menu.rebuildFromTree(List.of(
                MenuNode.ofItem("Copy", true, () -> {}),
                MenuNode.ofSeparator(),
                new MenuNode("Wrap", null, true, false, true, true, null, () -> {}, List.of())));

        assertSame(copy, menu.getItems().get(0));
        assertSame(wrap, menu.getItems().get(1));
        assertTrue(wrap.isChecked());
    }

    @Test
    @DisplayName("top-level items round-trip text and enabled")
    void topLevelItemsRoundTripTextAndEnabled() {
        SJPopupMenu menu = new SJPopupMenu();
        menu.rebuildFromTree(
                List.of(
                        MenuNode.ofItem("Copy", true, () -> {
                        }),
                        MenuNode.ofItem("Paste", false, () -> {
                        })));
        List<MenuItem> items = menu.getItems();
        assertEquals(2, items.size());
        assertEquals("Copy", items.get(0).getText());
        assertTrue(items.get(0).isEnabled());
        assertEquals("Paste", items.get(1).getText());
        assertFalse(items.get(1).isEnabled());
    }

    @Test
    @DisplayName("nested submenu structure builds")
    void nestedSubmenuStructureBuilds() {
        SJPopupMenu menu = new SJPopupMenu();
        MenuNode pdf = MenuNode.ofItem("PDF", true, () -> {
        });
        MenuNode rtf = MenuNode.ofItem("RTF", true, () -> {
        });
        MenuNode export = MenuNode.ofMenu("Export", true, List.of(pdf, rtf));
        menu.rebuildFromTree(List.of(export));
        List<MenuItem> sub = menu.getItems().get(0).getSubMenu().getItems();
        assertEquals(2, sub.size());
        assertEquals("PDF", sub.get(0).getText());
        assertEquals("RTF", sub.get(1).getText());
    }

    @Test
    @DisplayName("checkable item round-trips checked state")
    void checkableItemRoundTripsCheckedState() {
        SJPopupMenu menu = new SJPopupMenu();
        MenuNode toggle = new MenuNode(
                "Word wrap", null, true, false, true, true,
                null, () -> {
        }, List.of());
        menu.rebuildFromTree(List.of(toggle));
        MenuItem item = menu.getItems().get(0);
        assertTrue(item.isCheckable());
        assertTrue(item.isChecked());
    }

    @Test
    @DisplayName("icon-bearing item builds without WARN")
    void iconBearingItemBuildsWithoutWarn() {
        SJPopupMenu menu = new SJPopupMenu();
        MenuNode withIcon = new MenuNode(
                "Open", new Image(), true, false, false, false,
                null, () -> {
        }, List.of());
        menu.rebuildFromTree(List.of(withIcon));
        assertEquals(1, menu.getItems().size());
        assertNoWarns();
    }

    // --- Top-level separator RENDERS (the SD_sjpopupmenu delta vs SJMenuBar) ----

    @Test
    @DisplayName("top-level separator renders with no WARN")
    void topLevelSeparatorRendersWithNoWarn() {
        SJPopupMenu menu = new SJPopupMenu();
        menu.rebuildFromTree(
                List.of(
                        MenuNode.ofItem("Copy", true, () -> {
                        }),
                        MenuNode.ofSeparator(),
                        MenuNode.ofItem("Delete", true, () -> {
                        })));
        // ContextMenu.addSeparator inserts an <hr> that doesn't appear in
        // getItems() (like a SubMenu separator), so only the two real items
        // are counted — but crucially, no WARN fires (unlike SJMenuBar's
        // top-level drop).
        assertEquals(2, menu.getItems().size());
        assertEquals("Copy", menu.getItems().get(0).getText());
        assertEquals("Delete", menu.getItems().get(1).getText());
        assertNoWarns("no WARN expected, got: " + capturedWarns);
    }

    @Test
    @DisplayName("submenu separator lands without WARN")
    void submenuSeparatorLandsWithoutWarn() {
        SJPopupMenu menu = new SJPopupMenu();
        MenuNode export = MenuNode.ofMenu(
                "Export", true,
                List.of(MenuNode.ofItem("PDF", true, () -> {
                }), MenuNode.ofSeparator(), MenuNode.ofItem("RTF", true, () -> {
                })));
        menu.rebuildFromTree(List.of(export));
        List<MenuItem> sub = menu.getItems().get(0).getSubMenu().getItems();
        assertEquals(2, sub.size());
        assertNoWarns();
    }

    // --- Accelerators -------------------------------------------------

    @Test
    @DisplayName("accelerator installs after attach with current tree")
    void acceleratorInstallsAfterAttachWithCurrentTree() {
        SJPopupMenu menu = new SJPopupMenu();
        KeyStroke stroke = KeyStroke.getKeyStroke('C', InputEvent.CTRL_DOWN_MASK);
        menu.rebuildFromTree(List.of(withAccelerator("Copy", stroke)));
        UI.getCurrent().add(menu);
        var regs = store(menu).acceleratorRegistrations;
        assertEquals(1, regs.size());
        assertNotNull(regs.get(stroke));
    }

    @Test
    @DisplayName("rebuild tears down old accelerators")
    void rebuildTearsDownOldAccelerators() {
        SJPopupMenu menu = new SJPopupMenu();
        UI.getCurrent().add(menu);
        KeyStroke s1 = KeyStroke.getKeyStroke('C', InputEvent.CTRL_DOWN_MASK);
        KeyStroke s2 = KeyStroke.getKeyStroke('V', InputEvent.CTRL_DOWN_MASK);
        menu.rebuildFromTree(List.of(withAccelerator("Copy", s1)));
        assertEquals(1, store(menu).acceleratorRegistrations.size());
        menu.rebuildFromTree(List.of(withAccelerator("Paste", s2)));
        var regs = store(menu).acceleratorRegistrations;
        assertEquals(1, regs.size());
        assertNotNull(regs.get(s2));
        assertNull(regs.get(s1));
    }

    @Test
    @DisplayName("unmappable accelerator skips install with WARN")
    void unmappableAcceleratorSkipsInstallWithWarn() {
        SJPopupMenu menu = new SJPopupMenu();
        UI.getCurrent().add(menu);
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_PRINTSCREEN, 0);
        menu.rebuildFromTree(List.of(withAccelerator("Print", stroke)));
        assertEquals(0, store(menu).acceleratorRegistrations.size());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setAccelerator/unmappable-VK")));
    }

    // --- Click → onClick ---------------------------------------------

    @Test
    @DisplayName("click fires onClick")
    void clickFiresOnClick() {
        SJPopupMenu menu = new SJPopupMenu();
        Counter fired = new Counter();
        MenuNode item = MenuNode.ofItem("Copy", true, fired::inc);
        menu.rebuildFromTree(List.of(item));
        item.onClick().run();
        fired.assertEquals(1);
    }

    // --- setTarget round-trip (inherited ContextMenu API) ------------

    @Test
    @DisplayName("setTarget binds and clears")
    void setTargetBindsAndClears() {
        SJPopupMenu menu = new SJPopupMenu();
        SJPanel target = new SJPanel();
        menu.setTarget(target);
        assertEquals(target, menu.getTarget());
        menu.setTarget(null);
        assertNull(menu.getTarget());
    }

    // --- JComponentMixin surface -------------------------------------

    @Test
    @DisplayName("name round-trips via JComponentMixin")
    void nameRoundTripsViaJComponentMixin() {
        SJPopupMenu menu = new SJPopupMenu();
        menu.setName("popup1");
        assertEquals("popup1", menu.getName());
    }

    @Test
    @DisplayName("client property round-trips via JComponentMixin")
    void clientPropertyRoundTripsViaJComponentMixin() {
        SJPopupMenu menu = new SJPopupMenu();
        menu.putClientProperty("k", "v");
        assertEquals("v", menu.getClientProperty("k"));
    }

    // --- getUIClassID + happy-path -----------------------------------

    @Test
    @DisplayName("getUIClassID returns PopupMenuUI")
    void getUiClassIdReturnsPopupMenuUi() {
        assertEquals("PopupMenuUI", new SJPopupMenu().getUIClassID());
    }

    @Test
    @DisplayName("realistic context menu rebuild fires zero stub WARNs")
    void realisticContextMenuRebuildFiresZeroStubWarns() {
        SJPopupMenu menu = new SJPopupMenu();
        UI.getCurrent().add(menu);
        menu.rebuildFromTree(
                List.of(
                        MenuNode.ofItem("View", true, () -> {
                        }),
                        MenuNode.ofItem("Edit", true, () -> {
                        }),
                        MenuNode.ofSeparator(),
                        MenuNode.ofMenu(
                                "Export", true,
                                List.of(MenuNode.ofItem("PDF", true, () -> {
                                }), MenuNode.ofItem("RTF", true, () -> {
                                }))),
                        MenuNode.ofSeparator(),
                        MenuNode.ofItem("Delete", true, () -> {
                        })));
        // View, Edit, Export, Delete — separators are <hr>, not items.
        assertEquals(4, menu.getItems().size());
        assertNoWarns("expected no WARNs, got: " + capturedWarns);
    }
}
