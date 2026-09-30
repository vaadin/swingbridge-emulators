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

import com.vaadin.flow.component.Component;

import javax.swing.KeyStroke;
import java.util.Collections;
import java.util.List;

/**
 * Surrogate-internal descriptor consumed by {@link SJMenuBar#rebuildFromTree}
 * (SD_sjmenubar). The emulator-side {@code :emulators.swing.JMenuBar} walks its JDK
 * menu tree on every mutation, builds a {@code List<MenuNode>}, and pushes
 * it through the surrogate's tree-rebuild API. Pure-surrogate users never
 * see this type — they compose with Vaadin {@code MenuBar.addItem} directly
 * per [R_vaadin_first](../../CLAUDE.md).
 *
 * <p>Lives in {@code com.vaadin.swingbridge.surrogates} (not {@code :emulators}) per [SD_shelper_statics](decisions.md):
 * the descriptor is the surrogate's input, so it lives on the surrogate
 * side of the {@code :emulators → :surrogates} boundary.
 *
 * <h2>Field semantics</h2>
 *
 * <ul>
 *   <li>{@code text} — the rendered label. Empty string for icon-only
 *       items. {@code null} matches JDK's icon-only ctor convention.</li>
 *   <li>{@code icon} — pre-converted Vaadin {@link Component} (typically
 *       a {@code com.vaadin.flow.component.html.Image} from
 *       {@link com.vaadin.swingbridge.surrogates.util.Icons#imageIconToVaadinImage}). The emulator runs the
 *       conversion before populating the descriptor; the surrogate just
 *       passes the resulting Component to Vaadin's
 *       {@code MenuBar.addItem(Component, listener)} overload.</li>
 *   <li>{@code enabled} — drives Vaadin {@code MenuItem.setEnabled}.</li>
 *   <li>{@code separator} — when {@code true}, the rebuild walk emits
 *       {@code subMenu.addSeparator()} (or drops + WARNs at the top level
 *       per SD_sjmenubar's accepted gap). All other fields ignored.</li>
 *   <li>{@code checkable} — drives Vaadin {@code MenuItem.setCheckable}.</li>
 *   <li>{@code checked} — drives Vaadin {@code MenuItem.setChecked} when
 *       {@code checkable} is {@code true}.</li>
 *   <li>{@code accelerator} — JDK {@link KeyStroke}. The surrogate's
 *       rebuild path translates via {@link com.vaadin.swingbridge.surrogates.util.KeyConvert#toVaadinKeyBinding}
 *       and installs a UI-scoped {@code Shortcuts.addShortcutListener}.
 *       Unmappable keystrokes log + skip browser install but the
 *       emulator-side field round-trip survives.</li>
 *   <li>{@code onClick} — fires the JDK ActionEvent on the emulator side.
 *       The surrogate wraps invocation in {@link SHelper#callSwing} (R_callswing_envelope
 *       funnel for blocking-dialog work). {@code null} means
 *       "non-clickable" — used for top-level {@code JMenu} containers
 *       whose click opens a submenu (Vaadin handles that natively, no
 *       handler needed).</li>
 *   <li>{@code children} — nested submenu items. Empty for leaf
 *       {@code JMenuItem}s; populated for {@code JMenu}s.</li>
 * </ul>
 *
 * <p>Immutable record — every rebuild builds fresh nodes.
 */
public record MenuNode(
        String text,
        Component icon,
        boolean enabled,
        boolean separator,
        boolean checkable,
        boolean checked,
        KeyStroke accelerator,
        Runnable onClick,
        List<MenuNode> children) {

    public MenuNode {
        // Defensive: defensive copy + null-coerce children so the rebuild
        // walk doesn't NPE on a null list. The descriptor stays immutable
        // across rebuilds (stored via SD_shelper_statics module direction); a hostile or
        // accidentally-null field shouldn't poison the rebuild loop.
        children = children == null ? Collections.emptyList() : List.copyOf(children);
    }

    /** Convenience: a separator node — only the separator flag is meaningful. */
    public static MenuNode ofSeparator() {
        return new MenuNode(null, null, true, true, false, false, null, null, Collections.emptyList());
    }

    /**
     * Convenience: a leaf {@code JMenuItem}-shape node with text +
     * enabled + onClick. Other fields default ({@code null} icon /
     * accelerator, no checkable, empty children).
     */
    public static MenuNode ofItem(String text, boolean enabled, Runnable onClick) {
        return new MenuNode(text, null, enabled, false, false, false, null, onClick, Collections.emptyList());
    }

    /**
     * Convenience: a {@code JMenu}-shape node with text + enabled +
     * children. {@code onClick} is null (Vaadin auto-handles submenu open
     * on parent click).
     */
    public static MenuNode ofMenu(String text, boolean enabled, List<MenuNode> children) {
        return new MenuNode(text, null, enabled, false, false, false, null, null, children);
    }
}
