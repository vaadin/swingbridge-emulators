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
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.internal.MenuBarStateStore;
import com.vaadin.swingbridge.surrogates.internal.MenuTreeBuilder;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Surrogate for {@link javax.swing.JMenuBar} (SD_sjmenubar). Extends Vaadin
 * {@link MenuBar} directly and reproduces the Swing menubar surface via a
 * tree-rebuild API that the emulator-side {@code :emulators.swing.JMenuBar}
 * pushes through on every JDK menu-tree mutation.
 *
 * <h2>Why MenuBar, asymmetric split</h2>
 *
 * Vaadin {@link MenuItem}'s ctor is effectively package-private (it requires
 * a Vaadin-internal {@code ContextMenu} owner + content-reset callback), so
 * a surrogate cannot extend it as {@code SJMenuItem extends MenuItem} and
 * substitute it where Vaadin's own {@code addItem} would return one. The
 * smallest surrogate-layer unit that's a true Vaadin Component is therefore
 * the whole bar, not the individual items. R_vaadin_first then says don't ship a
 * parallel {@code SJMenu} / {@code SJMenuItem} surface that exists only to
 * mirror JDK shape — pure-surrogate users compose with
 * {@link MenuBar#addItem(String, com.vaadin.flow.component.ComponentEventListener)}
 * directly.
 *
 * <h2>Tree-rebuild API</h2>
 *
 * The emulator walks its JDK tree and pushes a {@code List<MenuNode>}
 * snapshot via {@link #rebuildFromTree(List)}. The actual Vaadin-side walk
 * lives in the shared {@link MenuTreeBuilder} (SD_sjpopupmenu — same engine
 * {@link SJPopupMenu} uses, keyed on Vaadin's {@code HasMenuItems}). Click
 * handlers funnel through {@link SHelper#callSwing} (R_callswing_envelope).
 *
 * <h2>Top-level separators + mnemonics: drop-and-WARN R_vaadin_first</h2>
 *
 * Vaadin {@code MenuBar} doesn't expose top-level {@code addSeparator()}
 * (separators only land inside a {@code SubMenu}); the rebuild's top-level
 * separator callback logs via {@link SHelper#onUnimplemented} and skips.
 * ({@link SJPopupMenu}'s {@code ContextMenu} peer <em>does</em> support
 * top-level separators — that's the one asymmetry the shared builder's
 * per-caller callback covers.) Top-level mnemonics have no Vaadin
 * counterpart either. Both are R_match_swing_errors sub-bucket (a).
 *
 * <h2>Accelerators</h2>
 *
 * Per-item {@link javax.swing.KeyStroke} accelerators install as UI-scoped
 * {@link com.vaadin.flow.component.Shortcuts} bindings via
 * {@link MenuTreeBuilder#installAccelerators}. Each rebuild tears the old
 * registrations down before installing fresh ones, keyed by JDK
 * {@code KeyStroke} in {@link MenuBarStateStore#acceleratorRegistrations}.
 */
public class SJMenuBar extends MenuBar implements JComponentMixin {

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

    public SJMenuBar() {
        super();
        _installSwingClass();
        addThemeVariants(MenuBarVariant.TERTIARY);
    }

    private MenuBarStateStore store() {
        return MenuBarStateStore.of(this);
    }

    /**
     * Push a fresh JDK menu-tree snapshot through to the Vaadin MenuBar.
     * A snapshot that changes only checked states, handlers or accelerators
     * updates the existing items ({@link MenuTreeBuilder#patchInPlace});
     * anything else tears the tree down and rebuilds it. Accelerators are
     * reinstalled either way.
     *
     * <p>The {@code preventRebuild} re-entrancy guard short-circuits
     * recursive calls — a click handler that runs as part of this rebuild
     * (toggle-item commit, etc.) firing back into the emulator can't
     * cascade into another nested rebuild.
     *
     * <p>{@code null} or empty tree fully clears the menubar.
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
                // Top-level separators have no Vaadin MenuBar counterpart — WARN + skip.
                MenuTreeBuilder.rebuild(this, next,
                        node -> SHelper.onUnimplemented(this, "addSeparator/top-level", node));
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
     * Re-install accelerators on attach. The first {@code rebuildFromTree}
     * call may run before the menubar is attached (e.g. building the bar in
     * a JFrame ctor before {@code setVisible(true)}); the install pass skips
     * silently in that case. This hook re-walks the current tree once the
     * UI exists.
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
     * Convenience accessor for tests + diagnostics — returns the most
     * recently pushed {@link MenuNode} snapshot. Live mutation of the
     * returned list does not affect the rendered tree (snapshot is
     * stored as an immutable copy).
     */
    public List<MenuNode> getCurrentTree() {
        return store().currentTree;
    }

    /**
     * JDK matches {@code "MenuBarUI"}.
     */
    public String getUIClassID() {
        return "MenuBarUI";
    }
}
