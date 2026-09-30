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

package com.vaadin.swingbridge.surrogates.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.HasMenuItems;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.util.KeyConvert;
import com.vaadin.swingbridge.surrogates.MenuNode;

import javax.swing.KeyStroke;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Shared tree-rebuild engine for the menu surrogates (SD_sjpopupmenu). Both
 * {@link com.vaadin.swingbridge.surrogates.SJMenuBar} (peer = Vaadin {@code MenuBar}) and
 * {@link com.vaadin.swingbridge.surrogates.SJPopupMenu} (peer = Vaadin {@code ContextMenu})
 * translate the emulator-pushed {@link MenuNode} tree into a Vaadin
 * {@code MenuItem} tree the same way: {@code MenuBar}, {@code ContextMenu},
 * and {@code SubMenu} all implement {@link HasMenuItems}
 * ({@code addItem(String|Component, listener) -> MenuItem}, items carry
 * {@code getSubMenu() -> SubMenu}), so the walk is byte-identical above the
 * peer type.
 *
 * <p>The one asymmetry is the top-level separator: {@code addSeparator()}
 * lives on {@code ContextMenuBase} / {@code SubMenuBase}, not on
 * {@link HasMenuItems}. {@code MenuBar} has no top-level {@code addSeparator}
 * (SD_sjmenubar drops + WARNs); {@code ContextMenu} does (SD_sjpopupmenu renders it). So
 * {@link #rebuild} takes a per-caller {@code topSeparator} callback — the
 * caller supplies "WARN + skip" or "{@code ctx.addSeparator()}". Sub-menu
 * separators are uniform ({@code SubMenu.addSeparator()} is always present)
 * and handled inline.
 *
 * <p>A push that changes only checked states, click handlers or accelerators
 * is applied to the existing items by {@link #patchInPlace} rather than
 * rebuilt. A checkable item's click pushes a new tree in the same round-trip,
 * and a rebuild would detach the item Vaadin is toggling: Vaadin's queued
 * checked-state update for it then throws in the browser
 * (vaadin/flow-components#10227; SD_menu_patch_in_place).
 *
 * <p>Stateless static utility — all per-component state (accelerator
 * registrations, current tree, re-entrancy guard) lives in the caller's
 * {@link MenuBarStateStore}.
 */
public final class MenuTreeBuilder {

    private MenuTreeBuilder() {}

    /**
     * Walk {@code tree} into {@code root}. Separator nodes at the top level are
     * delegated to {@code topSeparator}; all other nodes build via
     * {@link #addNode}. The caller recovers the accelerator map afterwards with
     * {@link #recoverLeafMap}, the same walk the in-place path uses.
     */
    public static void rebuild(HasMenuItems root, List<MenuNode> tree,
                               Consumer<MenuNode> topSeparator) {
        for (MenuNode node : tree) {
            if (node.separator()) {
                topSeparator.accept(node);
                continue;
            }
            MenuItem peerItem = addNode(root, node);
            peerItem.setEnabled(node.enabled());
            if (node.checkable()) {
                peerItem.setCheckable(true);
                peerItem.setChecked(node.checked());
            }
            if (!node.children().isEmpty()) {
                buildSubMenu(peerItem.getSubMenu(), node.children());
            }
        }
    }

    /**
     * Recurse into a {@link SubMenu}. Separators land cleanly here
     * ({@code SubMenu.addSeparator()} exists for both peer families), so
     * no per-caller callback is needed below the top level.
     */
    private static void buildSubMenu(SubMenu parent, List<MenuNode> nodes) {
        for (MenuNode node : nodes) {
            if (node.separator()) {
                parent.addSeparator();
                continue;
            }
            MenuItem peerItem = addNode(parent, node);
            peerItem.setEnabled(node.enabled());
            if (node.checkable()) {
                peerItem.setCheckable(true);
                peerItem.setChecked(node.checked());
            }
            if (!node.children().isEmpty()) {
                buildSubMenu(peerItem.getSubMenu(), node.children());
            }
        }
    }

    /**
     * Add a single node to {@code target}, picking the
     * {@code addItem(Component, ...)} overload when an icon is present (so
     * icon + text render together) and {@code addItem(String, ...)}
     * otherwise. The click listener funnels through {@link SHelper#callSwing}
     * (R_callswing_envelope); a {@code null} onClick (top-level {@code JMenu} container,
     * Vaadin auto-opens its submenu) gets a no-op handler so {@code addItem}
     * doesn't NPE.
     *
     * <p>The listener runs whatever {@link ClickTarget} the item holds,
     * so {@link #patchInPlace} can rebind a kept item to the new
     * tree's handler.
     */
    private static MenuItem addNode(HasMenuItems target, MenuNode node) {
        ClickTarget click = new ClickTarget(node.onClick());
        ComponentEventListener<ClickEvent<MenuItem>> listener = e -> {
            Runnable onClick = click.onClick;
            if (onClick != null) SHelper.callSwing(onClick);
        };
        MenuItem item = node.icon() != null
                ? target.addItem(buildIconAndText(node.icon(), node.text()), listener)
                : target.addItem(node.text() == null ? "" : node.text(), listener);
        ComponentUtil.setData(item, ClickTarget.class, click);
        return item;
    }

    /** The handler a built item's click listener runs. */
    private static final class ClickTarget {
        Runnable onClick;

        ClickTarget(Runnable onClick) {
            this.onClick = onClick;
        }
    }

    /**
     * Applies {@code next} to the items {@code current} built, without
     * rebuilding, when the two trees differ only in checked states, click
     * handlers or accelerators. Checkable items get {@code setChecked}; every
     * item is rebound to its new handler. The caller reinstalls accelerators.
     *
     * <p>Text and enabled stay on the rebuild path: a {@code MenuBar}
     * re-renders its root buttons for theme, class and tooltip
     * changes only ({@code MenuBarRootItem}), so an in-place write
     * there would leave the button stale. Icons compare by identity,
     * which holds only because the emulator reuses its converted icon
     * while the Swing {@code Icon} is unchanged.
     *
     * @param peers the root's items, as its {@code getItems()} returns them
     * @return {@code false}, with nothing touched, when anything else differs —
     *         text, icon, enabled, checkable, or the shape — and the caller
     *         must rebuild
     */
    public static boolean patchInPlace(List<MenuNode> current, List<MenuNode> next,
                                       List<MenuItem> peers) {
        if (!patchable(current, next, peers)) return false;
        patch(next, peers);
        return true;
    }

    private static boolean patchable(List<MenuNode> current, List<MenuNode> next,
                                     List<MenuItem> peers) {
        if (current.size() != next.size()) return false;
        int peerIndex = 0;
        for (int i = 0; i < next.size(); i++) {
            MenuNode was = current.get(i);
            MenuNode now = next.get(i);
            if (was.separator() != now.separator()) return false;
            // Separators build no MenuItem, so they don't step the peer index.
            if (now.separator()) continue;
            if (peerIndex >= peers.size()) return false;
            MenuItem peer = peers.get(peerIndex++);
            if (!Objects.equals(was.text(), now.text())
                    || was.icon() != now.icon()
                    || was.enabled() != now.enabled()
                    || was.checkable() != now.checkable()
                    || was.children().size() != now.children().size()) {
                return false;
            }
            if (!now.children().isEmpty()
                    && !patchable(was.children(), now.children(), peer.getSubMenu().getItems())) {
                return false;
            }
        }
        return peerIndex == peers.size();
    }

    private static void patch(List<MenuNode> next, List<MenuItem> peers) {
        int peerIndex = 0;
        for (MenuNode node : next) {
            if (node.separator()) continue;
            MenuItem peer = peers.get(peerIndex++);
            ClickTarget click = ComponentUtil.getData(peer, ClickTarget.class);
            if (click != null) click.onClick = node.onClick();
            if (node.checkable()) peer.setChecked(node.checked());
            if (!node.children().isEmpty()) patch(node.children(), peer.getSubMenu().getItems());
        }
    }

    /**
     * Build a {@link Span} with the icon first and the text second so
     * Vaadin's {@code addItem(Component, ...)} renders both. Mirrors
     * SJLabel / SJButton's icon+text composition per SD_sjlabel.
     */
    public static Component buildIconAndText(Component icon, String text) {
        Span wrapper = new Span();
        wrapper.getStyle().set("display", "inline-flex");
        wrapper.getStyle().set("align-items", "center");
        wrapper.getStyle().set("gap", "0.4em");
        wrapper.add(icon);
        if (text != null && !text.isEmpty()) {
            wrapper.add(new Span(text));
        }
        return wrapper;
    }

    /**
     * Install a UI-scoped Vaadin shortcut for every leaf node carrying an
     * accelerator. Each shortcut fires the leaf's {@code onClick} directly
     * (same path as a click on the item). Resulting {@link Registration}s
     * are stored in {@code registrations} keyed by the JDK {@link KeyStroke}
     * so the next rebuild can tear them down. Unmappable keystrokes log via
     * {@link SHelper#onUnimplemented} and skip browser install.
     */
    public static void installAccelerators(UI ui, Component owner,
                                           Map<MenuNode, MenuItem> leafToPeer,
                                           Map<KeyStroke, Registration> registrations) {
        for (Map.Entry<MenuNode, MenuItem> entry : leafToPeer.entrySet()) {
            MenuNode node = entry.getKey();
            KeyStroke stroke = node.accelerator();
            if (stroke == null) continue;
            KeyConvert.VaadinKeyBinding binding = KeyConvert.toVaadinKeyBinding(stroke);
            if (binding == null) {
                SHelper.onUnimplemented(owner, "setAccelerator/unmappable-VK", stroke);
                continue;
            }
            Runnable onClick = node.onClick();
            Key key = binding.key();
            KeyModifier[] mods = binding.modifiers();
            ShortcutRegistration reg;
            if (mods == null || mods.length == 0) {
                reg = Shortcuts.addShortcutListener(ui, () -> SHelper.callSwing(onClick), key);
            } else {
                reg = Shortcuts.addShortcutListener(ui, () -> SHelper.callSwing(onClick), key, mods);
            }
            registrations.put(stroke, reg);
        }
    }

    /**
     * Walk parallel JDK {@link MenuNode} and Vaadin {@link MenuItem} trees
     * to recover the leaf-to-peer map after attach (the original rebuild's
     * {@code MenuItem} references aren't addressable later). Both trees are
     * isomorphic since the same rebuild built them. Separator nodes never
     * produce a {@code MenuItem} (top-level {@code MenuBar} drops them;
     * {@code ContextMenu} / {@code SubMenu} insert an {@code <hr>} that
     * doesn't appear in {@code getItems()}), so skip them without stepping
     * the peer index in both cases.
     */
    public static void recoverLeafMap(List<MenuNode> nodes, List<MenuItem> peers,
                                      Map<MenuNode, MenuItem> map) {
        int peerIndex = 0;
        for (MenuNode node : nodes) {
            if (node.separator()) continue;
            if (peerIndex >= peers.size()) break;
            recoverLeafMapDepth(node, peers.get(peerIndex++), map);
        }
    }

    private static void recoverLeafMapDepth(MenuNode node, MenuItem peer,
                                            Map<MenuNode, MenuItem> map) {
        if (node.children().isEmpty()) {
            if (node.onClick() != null) map.put(node, peer);
            return;
        }
        List<MenuItem> subPeers = peer.getSubMenu().getItems();
        int peerIndex = 0;
        for (MenuNode child : node.children()) {
            if (child.separator()) continue;
            if (peerIndex >= subPeers.size()) break;
            recoverLeafMapDepth(child, subPeers.get(peerIndex++), map);
        }
    }
}
