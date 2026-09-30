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
// R_leaf_peer_lockdown-locked-down: JDK leaf in javax.swing.*. The MenuNode it produces
// flags checkable=true + checked=getState(). The rebuild's click handler
// flips selected server-side, fires ItemEvent + ChangeEvent, then
// triggers another rebuild (accepted O(n) churn — typical CRUD menus
// don't construct mega-menubars that toggle constantly).

/** Emulator for {@link javax.swing.JCheckBoxMenuItem}. R_leaf_peer_lockdown-locked. */
public class JCheckBoxMenuItem extends vaadinx.swing.JMenuItem {

    /**
     * Selection state. JDK default is false. The MenuNode
     * {@code checked} slot reads from this field; user code reading
     * back through {@link #getState} / {@link #isSelected} sees this
     * value. Mutated in-place by the rebuild's click handler.
     */
    private boolean selected;

    public JCheckBoxMenuItem() {
        super();
    }

    public JCheckBoxMenuItem(java.lang.String text) {
        super(text);
    }

    public JCheckBoxMenuItem(vaadinx.swing.Icon icon) {
        super(icon);
    }

    public JCheckBoxMenuItem(java.lang.String text, vaadinx.swing.Icon icon) {
        super(text, icon);
    }

    public JCheckBoxMenuItem(java.lang.String text, boolean b) {
        super(text);
        this.selected = b;
    }

    public JCheckBoxMenuItem(java.lang.String text, vaadinx.swing.Icon icon, boolean b) {
        super(text, icon);
        this.selected = b;
    }

    public JCheckBoxMenuItem(javax.swing.Action a) {
        super(a);
    }

    public boolean getState() {
        return selected;
    }

    public void setState(boolean state) {
        setSelected(state);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean b) {
        // ButtonGroup veto per D_buttongroup: when in a group, the group's
        // setSelected coordination decides the effective state. For
        // a JCheckBoxMenuItem in a group (rare but JDK-legal), the
        // group enforces select-one-of-N just like for radio items.
        b = consultButtonGroup(b);
        if (this.selected == b) return;
        this.selected = b;
        // Fire the JDK event sequence: ItemEvent (SELECTED / DESELECTED)
        // then ChangeEvent. ActionEvent does NOT fire from setSelected
        // — only from a click (programmatic via doClick or browser-side
        // via the rebuild's click handler).
        java.awt.event.ItemEvent e = new java.awt.event.ItemEvent(
                this,
                java.awt.event.ItemEvent.ITEM_STATE_CHANGED,
                this,
                b ? java.awt.event.ItemEvent.SELECTED
                  : java.awt.event.ItemEvent.DESELECTED);
        fireItemStateChanged(e);
        fireStateChanged();
        notifyTreeMutated();
    }

    @Override
    boolean isCheckableNode() {
        return true;
    }

    @Override
    boolean isCheckedNode() {
        return selected;
    }

    @Override
    Runnable makeOnClick() {
        // Browser-side click on a JCheckBoxMenuItem flips state, fires
        // Item + Change + Action, then bubbles a fresh rebuild so the
        // surrogate's MenuItem.setChecked flag matches the new state on
        // the next render. Server-side setSelected calls notifyTreeMutated
        // already; we still re-snapshot here because the rebuild's click
        // handler runs through EHelper.callSwing — preventRebuild guards
        // any cascade.
        //
        // ButtonGroup veto per D_buttongroup: same shape as setSelected — consult
        // the group on the proposed new state before mutating fields.
        // ActionEvent fires unconditionally on click (matches JDK: a
        // group-vetoed click still gets the ActionEvent because the
        // user did press the item, even if its selection state stays
        // the same — this is what lets a click-to-deselect-current
        // suppression still notify ActionListener observers).
        // Body wrapped in vaadinx.EHelper.callSwing per R_callswing_envelope / D_callswing_loom — see
        // JMenuItem.makeOnClick javadoc for the SJMenuBar-inline-callSwing
        // hand-off reason. Wrapping at this seam (not inside fire*) keeps
        // the whole user-visible event sequence on the same VT.
        return () -> vaadinx.EHelper.callSwing(() -> {
            boolean newState = consultButtonGroup(!selected);
            if (this.selected != newState) {
                this.selected = newState;
                java.awt.event.ItemEvent ie = new java.awt.event.ItemEvent(
                        this,
                        java.awt.event.ItemEvent.ITEM_STATE_CHANGED,
                        this,
                        newState ? java.awt.event.ItemEvent.SELECTED
                                 : java.awt.event.ItemEvent.DESELECTED);
                fireItemStateChanged(ie);
                fireStateChanged();
            }
            fireActionPerformed(new java.awt.event.ActionEvent(
                    this,
                    java.awt.event.ActionEvent.ACTION_PERFORMED,
                    getActionCommand(),
                    System.currentTimeMillis(),
                    0));
            notifyTreeMutated();
        });
    }

    @Override
    public java.lang.String getUIClassID() {
        return "CheckBoxMenuItemUI";
    }
}
