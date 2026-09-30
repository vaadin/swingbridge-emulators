/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.MenuSelectionManager
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import vaadinx.EHelper;

/**
 * Emulator for {@link javax.swing.MenuSelectionManager} — present so migrated
 * code that names the type still compiles after the import swap, WARN-stubbed
 * throughout because SB-Emulators models no menu-selection machinery to manage.
 *
 * <p><b>Why a stub rather than JDK reuse.</b> Its whole API traffics in
 * {@link MenuElement}, which SB-Emulators ports, so the JDK class could not accept a
 * ported element as an argument. Same forcing as {@link MenuElement}'s own
 * port. The behaviour it would manage — a selected menu path driven by mouse
 * and key traversal — is Vaadin's own: {@code SJMenuBar} plus
 * {@code MenuTreeBuilder} rebuild the whole Vaadin menu tree from the emulator
 * hierarchy and the browser owns highlight and traversal from there (D_menu_tree).
 *
 * <p><b>What that leaves is more than a stub.</b> The selected path itself is pure
 * bookkeeping, so {@link #setSelectedPath} really keeps it, really calls each element's
 * {@code menuSelectionChanged}, and really fires — the declined effect is the *highlight*,
 * one layer down in each element (R_decline_effect_only). Nothing inside SB-Emulators ever
 * calls it, so a migrator driving menus by hand is the only writer; the mouse and key
 * processing that would drive it on the desktop stays WARN-stubbed, since those events
 * never reach the server.
 */
public class MenuSelectionManager {

    private static final MenuSelectionManager INSTANCE = new MenuSelectionManager();

    // JDK protected fields (D_instance_field_surface). Registration is state and
    // is owed even though nothing here ever changes the selection — a declared
    // listenerList that addChangeListener ignored would lie to a subclass, so
    // the listener surface is fully implemented; only the selection-path
    // machinery below stays a declined effect.
    protected javax.swing.event.EventListenerList listenerList = new javax.swing.event.EventListenerList();
    protected transient javax.swing.event.ChangeEvent changeEvent = null;

    /** Mirrors {@link javax.swing.MenuSelectionManager#defaultManager()} — one shared instance. */
    public static MenuSelectionManager defaultManager() {
        return INSTANCE;
    }

    /** The selected path, in root-to-leaf order — the JDK's own {@code Vector}, by another name. */
    private final java.util.List<MenuElement> selection = new java.util.ArrayList<>();

    /**
     * Replaces the selected path, walking the JDK's body: keep the shared prefix, tell each
     * dropped element it left the selection, tell each added one it joined, then fire.
     *
     * <p>Pure bookkeeping and notification, so both land (R_decline_effect_only) even
     * though nothing in SB-Emulators ever calls this: the browser owns menu highlight
     * and traversal ({@code SJMenuBar} + {@code MenuTreeBuilder} rebuild the Vaadin
     * tree, D_menu_tree). What is declined is the *rendering* of a selection — which
     * is each element's own {@code menuSelectionChanged}, called here and WARNing there.
     *
     * @param path {@code null} is treated as an empty path, as in the JDK
     */
    public void setSelectedPath(MenuElement[] path) {
        MenuElement[] wanted = path == null ? new MenuElement[0] : path;
        int shared = 0;
        while (shared < wanted.length && shared < selection.size()
                && selection.get(shared) == wanted[shared]) {
            shared++;
        }
        for (int i = selection.size() - 1; i >= shared; i--) {
            selection.remove(i).menuSelectionChanged(false);
        }
        for (int i = shared; i < wanted.length; i++) {
            if (wanted[i] != null) {
                selection.add(wanted[i]);
                wanted[i].menuSelectionChanged(true);
            }
        }
        fireStateChanged();
    }

    public MenuElement[] getSelectedPath() {
        return selection.toArray(new MenuElement[0]);
    }

    public void clearSelectedPath() {
        setSelectedPath(null);
    }

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.add(javax.swing.event.ChangeListener.class, l);
    }

    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.remove(javax.swing.event.ChangeListener.class, l);
    }

    public javax.swing.event.ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(javax.swing.event.ChangeListener.class);
    }

    /**
     * The JDK's own walk, and it reaches less far here than there: a {@code JMenu}
     * owns no {@code JPopupMenu} (D_menu_tree rebuilds the Vaadin tree instead), so
     * {@code getSubElements()} is empty and the walk stops at the root. It widens on
     * its own if that structural gap ever closes.
     *
     * @return true if {@code c} is the component of any element under the selected path's root
     */
    public boolean isComponentPartOfCurrentMenu(vaadinx.awt.Component c) {
        return !selection.isEmpty() && isComponentPartOfCurrentMenu(selection.get(0), c);
    }

    private boolean isComponentPartOfCurrentMenu(MenuElement root, vaadinx.awt.Component c) {
        if (root == null) {
            return false;
        }
        if (root.getComponent() == c) {
            return true;
        }
        for (MenuElement child : root.getSubElements()) {
            if (isComponentPartOfCurrentMenu(child, c)) {
                return true;
            }
        }
        return false;
    }

    public void processMouseEvent(vaadinx.awt.event.MouseEvent event) {
        EHelper.onUnimplemented("MenuSelectionManager", "processMouseEvent", event);
    }

    public void processKeyEvent(vaadinx.awt.event.KeyEvent e) {
        EHelper.onUnimplemented("MenuSelectionManager", "processKeyEvent", e);
    }

    /** @return null — no component-to-path mapping exists without a selected path. */
    public vaadinx.awt.Component componentForPoint(vaadinx.awt.Component source, java.awt.Point sourcePoint) {
        EHelper.onUnimplemented("MenuSelectionManager", "componentForPoint", source, sourcePoint);
        return null;
    }

    /**
     * JDK-faithful fan-out over {@link #listenerList}. Nothing in SB-Emulators calls it
     * (the selection never changes server-side), but a subclass that does gets
     * the JDK's delivery.
     */
    protected void fireStateChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == javax.swing.event.ChangeListener.class) {
                if (changeEvent == null) changeEvent = new javax.swing.event.ChangeEvent(this);
                ((javax.swing.event.ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }
}
