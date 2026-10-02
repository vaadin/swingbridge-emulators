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
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;

import java.util.Collection;

/**
 * Surrogate for {@link javax.swing.JWindow}: an undecorated top-level
 * window — splash screens, toast/notification popups, heavyweight-popup
 * vehicles. JDK shape is {@code JWindow extends java.awt.Window
 * implements RootPaneContainer}: no title, no chrome, no close gestures,
 * no {@code defaultCloseOperation} (JWindow doesn't implement
 * {@code WindowConstants}), no menubar slot. Accordingly this class
 * extends {@link SWindow} directly — NOT {@link SFrame}, whose
 * title / close-X / close-op state a JWindow must not carry.
 *
 * <h2>Ctor defaults (the entire JWindow delta over SWindow)</h2>
 *
 * <ul>
 *   <li><b>Undecorated always</b> — {@link SWindow#setUndecoratedChrome(boolean)}
 *       is on unconditionally: no header, no content padding, no border
 *       radius. Box-shadow kept per the documented R_layouts_close_enough divergence.</li>
 *   <li><b>Modeless</b> — a splash/toast must never block the page.
 *       Vaadin Dialog defaults to modal; the ctor flips to
 *       {@link ModalityMode#MODELESS}.</li>
 *   <li><b>No user close gestures</b> — ESC / outside-click dismissal is
 *       disarmed. A real JWindow has no chrome to close it from;
 *       programmatic {@code setVisible(false)} / {@code dispose()} are
 *       the only close paths, which also means no peer-originated
 *       {@code WINDOW_CLOSING} ever fires here.</li>
 *   <li><b>Not user-movable / resizable</b> — no title bar to drag, no
 *       resize grips. Vaadin Dialog's defaults (draggable=false,
 *       resizable=false) already match; unlike SFrame's ctor, nothing
 *       is seeded.</li>
 * </ul>
 *
 * <h2>Placement</h2>
 *
 * The typical JWindow is explicitly placed: {@code setLocationRelativeTo(null)}
 * for a centered splash (the Dialog default — a no-op that also clears
 * prior placement), {@link #setBounds(int, int, int, int)} for a
 * corner toast. Both live on {@link SWindow}'s geometry surface mapping
 * 1:1 to the overlay's top/left/width/height.
 *
 * <h2>RootPane / contentPane scaffolding</h2>
 *
 * Third consumer of the shared {@link RootPaneScaffold} (with
 * {@link SJFrame} / {@link SJDialog}) — the composition dedup SD_sjdialog's
 * third-consumer trigger anticipated. Public surface mirrors JDK
 * JWindow's RootPaneContainer subset: content pane routing, lazy root
 * pane, layered / glass pass-throughs. The scaffold's menubar slot
 * stays dormant here — JWindow has no {@code setJMenuBar}.
 */
public class SJWindow extends SWindow {

    /**
     * Shared RootPaneContainer engine — see {@link RootPaneScaffold} and
     * {@link SJFrame}'s field of the same name. Field initializer runs
     * after the SWindow ctor chain; the overrides null-guard for any
     * super-ctor-time add that could land before it.
     */
    private final RootPaneScaffold scaffold =
            new RootPaneScaffold(this, this::createRootPane);

    /** Ownerless window. */
    public SJWindow() {
        this(null);
    }

    /**
     * With owner (any {@link SWindow} — frame, dialog, or window — per
     * JDK's owner-chain shape). Null owner is a top-level window.
     */
    public SJWindow(SWindow owner) {
        super(owner);
        // The four JWindow-defining defaults — see class javadoc.
        setUndecoratedChrome(true);
        setModality(ModalityMode.MODELESS);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
    }

    // ---- Content pane (RootPaneContainer surface, mirrors SJFrame) ----

    /** Lazy-creates if never accessed. Never returns null. */
    public Div getContentPane() {
        return scaffold.getContentPane();
    }

    /**
     * Swap the content pane. JDK contract: null throws
     * {@link java.awt.IllegalComponentStateException}; the mirror-into-
     * rootPane invariant holds across swaps. Carried by
     * {@link RootPaneScaffold}.
     */
    public void setContentPane(Div newPane) {
        scaffold.setContentPane(newPane);
    }

    // ---- add / remove routing (mirrors SJFrame) ----
    //
    // Scaffold-null guard covers super-ctor-time adds that could land
    // before the scaffold field initializer runs.

    @Override
    public void add(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContent(components);
        } else {
            super.add(components);
        }
    }

    @Override
    public void addComponentAtIndex(int index, Component component) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContentAtIndex(index, component);
        } else {
            super.addComponentAtIndex(index, component);
        }
    }

    @Override
    public void remove(Component... components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    @Override
    public void remove(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    @Override
    public void removeAll() {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeAllFromContent();
        } else {
            super.removeAll();
        }
    }

    // ---- Root pane (mirrors SJFrame) ---------------------------------

    @Override
    public SJRootPane getRootPane() {
        return scaffold.getRootPane();
    }

    /**
     * Replaces the root pane, as the JDK's protected {@code setRootPane} does: the outgoing
     * one leaves this window and {@code root} becomes its one child, holding the content pane
     * from then on. Public so the emulator layer can hand down the peer of the root pane it
     * built itself, which is how one root pane serves both layers.
     *
     * @param root {@code null} leaves the window without a root pane, as the JDK allows
     */
    public void setRootPane(SJRootPane root) {
        scaffold.setRootPane(root);
    }

    /**
     * Subclass hook matching JDK JWindow's {@code createRootPane}.
     * Called once on first {@link #getRootPane()} read.
     */
    protected SJRootPane createRootPane() {
        return new SJRootPane();
    }

    // ---- Layered / glass pane pass-throughs (mirrors SJFrame) --------

    public Component getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(Component layered) {
        getRootPane().setLayeredPane(layered);
    }

    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(Component glass) {
        getRootPane().setGlassPane(glass);
    }

    /** Convenience accessor: {@code getRootPane().getDefaultButton()}. */
    public Button getDefaultButton() {
        return getRootPane().getDefaultButton();
    }
}
