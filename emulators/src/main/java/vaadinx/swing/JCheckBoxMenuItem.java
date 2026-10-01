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
 * This file is derived from OpenJDK's javax.swing.JCheckBoxMenuItem
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JCheckBoxMenuItem per D_menu_tree / SD_sjmenubar.
// R_leaf_peer_lockdown-locked-down: JDK leaf in javax.swing.*. Selection is the
// JDK's: a JToggleButton.ToggleButtonModel, installed by the root ctor, so a
// click (JMenuItem.makeOnClick → doClick) flips it and fires Change + Item +
// Action from the model. The MenuNode it produces flags checkable=true +
// checked=getState(); a selection change re-renders the tree (accepted O(n)
// churn — typical CRUD menus don't construct mega-menubars that toggle constantly).

/** Emulator for {@link javax.swing.JCheckBoxMenuItem}. R_leaf_peer_lockdown-locked. */
public class JCheckBoxMenuItem extends vaadinx.swing.JMenuItem {

    public JCheckBoxMenuItem() {
        this(null, null, false);
    }

    public JCheckBoxMenuItem(vaadinx.swing.Icon icon) {
        this(null, icon, false);
    }

    public JCheckBoxMenuItem(java.lang.String text) {
        this(text, null, false);
    }

    public JCheckBoxMenuItem(javax.swing.Action a) {
        this();
        setAction(a);
    }

    public JCheckBoxMenuItem(java.lang.String text, vaadinx.swing.Icon icon) {
        this(text, icon, false);
    }

    public JCheckBoxMenuItem(java.lang.String text, boolean b) {
        this(text, null, b);
    }

    public JCheckBoxMenuItem(java.lang.String text, vaadinx.swing.Icon icon, boolean b) {
        super(text, icon);
        setModel(new JToggleButton.ToggleButtonModel());
        setSelected(b);
    }

    public boolean getState() {
        return isSelected();
    }

    public void setState(boolean b) {
        setSelected(b);
    }

    @Override
    boolean isCheckableNode() {
        return true;
    }

    @Override
    boolean isCheckedNode() {
        return isSelected();
    }

    @Override
    public java.lang.String getUIClassID() {
        return "CheckBoxMenuItemUI";
    }
}
