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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.internal.MenuBarStateStore;
import com.vaadin.swingbridge.surrogates.internal.MenuTreeBuilder;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Surrogate for {@link javax.swing.JPopupMenu} (SD_sjpopupmenu). Extends Vaadin
 * {@link ContextMenu} directly and reproduces the Swing popup-menu surface
 * via the same tree-rebuild engine {@link SJMenuBar} uses — {@code MenuBar},
 * {@code ContextMenu}, and {@code SubMenu} all implement Vaadin's
 * {@code HasMenuItems}, so the {@link MenuTreeBuilder} walk is shared above
 * the peer type.
 *
 * <h2>Asymmetric split (same as SD_sjmenubar)</h2>
 *
 * The menu-item family ({@code JMenuItem} / {@code JMenu} /
 * {@code JCheckBoxMenuItem} / {@code JRadioButtonMenuItem} / {@code JSeparator})
 * stays emulator-only — no per-item surrogate classes — for the identical
 * R_vaadin_first reason SD_sjmenubar declined them: Vaadin {@code MenuItem}'s ctor needs internal
 * collaborators. Pure-surrogate users compose with the inherited
 * {@link ContextMenu#addItem(String, com.vaadin.flow.component.ComponentEventListener)}
 * directly; the emulator-side {@code :emulators.swing.JPopupMenu} pushes a
 * {@link MenuNode} snapshot via {@link #rebuildFromTree(List)}.
 *
 * <h2>Attaching to a component</h2>
 *
 * On the surrogate layer, attach via the inherited
 * {@link ContextMenu#setTarget(com.vaadin.flow.component.Component)} — that's
 * what the Swing-flavoured {@code JComponentMixin.setComponentPopupMenu(SJPopupMenu)}
 * helper (SD_sjpopupmenu) calls underneath. {@code ContextMenu}'s default open trigger
 * is right-click / long-press, which matches Swing's right-click context menu.
 *
 * <h2>Top-level separators render</h2>
 *
 * Unlike {@link SJMenuBar} (whose {@code MenuBar} peer has no top-level
 * {@code addSeparator}), {@code ContextMenu} supports separators at the root,
 * so the shared builder's top-level separator callback maps straight to
 * {@link ContextMenu#addSeparator()}.
 *
 * <h2>Accelerators</h2>
 *
 * Per-item {@link javax.swing.KeyStroke} accelerators install as UI-scoped
 * shortcuts via {@link MenuTreeBuilder#installAccelerators}, torn down on
 * every rebuild — same machinery as {@link SJMenuBar}.
 */
public class SJPopupMenu extends ContextMenu implements JComponentMixin {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    public SJPopupMenu() {
        super();
        _installSwingClass();
    }

    private MenuBarStateStore store() {
        return MenuBarStateStore.of(this);
    }

    /**
     * Push a fresh JDK menu-tree snapshot through to the Vaadin
     * {@link ContextMenu}. A snapshot that changes only checked states,
     * handlers or accelerators updates the existing items
     * ({@link MenuTreeBuilder#patchInPlace}); anything else tears the tree
     * down and rebuilds it. Top-level separators render via
     * {@link ContextMenu#addSeparator()}.
     *
     * <p>The {@code preventRebuild} re-entrancy guard short-circuits
     * recursive calls. {@code null} / empty tree fully clears the menu.
     */
    public void rebuildFromTree(List<MenuNode> tree) {
        MenuBarStateStore state = store();
        if (state.preventRebuild) {
            return;
        }
        state.preventRebuild = true;
        try {
            for (Registration r : state.acceleratorRegistrations.values()) {
                r.remove();
            }
            state.acceleratorRegistrations.clear();
            List<MenuNode> next = tree == null ? List.of() : List.copyOf(tree);
            if (!MenuTreeBuilder.patchInPlace(state.currentTree, next, getItems())) {
                removeAll();
                // ContextMenu supports top-level separators — render them.
                MenuTreeBuilder.rebuild(this, next, node -> addSeparator());
            }
            state.currentTree = next;

            UI ui = getUI().orElse(null);
            if (ui != null) {
                Map<MenuNode, MenuItem> leafToPeer = new HashMap<>();
                MenuTreeBuilder.recoverLeafMap(next, getItems(), leafToPeer);
                MenuTreeBuilder.installAccelerators(ui, this, leafToPeer, state.acceleratorRegistrations);
            }
            // No UI yet — accelerators install on first attach via onAttach.
        } finally {
            state.preventRebuild = false;
        }
    }

    /**
     * Clears the Vaadin {@link MenuItem} tree. A {@code ContextMenu} root
     * separator is an {@code <hr>} that doesn't appear in {@link #getItems()},
     * so the recovery walk skips separators without stepping the peer index —
     * the same alignment {@link MenuTreeBuilder#recoverLeafMap} relies on.
     */
    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        MenuBarStateStore state = store();
        if (state.currentTree.isEmpty()) return;
        if (!state.acceleratorRegistrations.isEmpty()) return;  // already installed
        Map<MenuNode, MenuItem> leafToPeer = new HashMap<>();
        MenuTreeBuilder.recoverLeafMap(state.currentTree, getItems(), leafToPeer);
        MenuTreeBuilder.installAccelerators(attachEvent.getUI(), this, leafToPeer, state.acceleratorRegistrations);
    }

    /**
     * Convenience accessor for tests + diagnostics — the most recently
     * pushed {@link MenuNode} snapshot (immutable copy).
     */
    public List<MenuNode> getCurrentTree() {
        return store().currentTree;
    }

    /**
     * JDK matches {@code "PopupMenuUI"}.
     */
    public String getUIClassID() {
        return "PopupMenuUI";
    }
}
