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
 * This file is derived from OpenJDK's javax.swing.JRadioButtonMenuItem
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JRadioButtonMenuItem per D_menu_tree / SD_sjmenubar.
// R_leaf_peer_lockdown-locked-down: JDK leaf in javax.swing.*. Renders identically to
// JCheckBoxMenuItem at the surrogate level (Vaadin setCheckable(true));
// ButtonGroup coordination (the "select-one-of-N" surface) lands via D_buttongroup
// — setSelected and makeOnClick consult the installed group at the top
// of their bodies. Closes the SD_sjmenubar multi-select gap.

/** Emulator for {@link javax.swing.JRadioButtonMenuItem}. R_leaf_peer_lockdown-locked. */
public class JRadioButtonMenuItem extends vaadinx.swing.JMenuItem {

    /** Selection state. JDK default false. */
    private boolean selected;

    public JRadioButtonMenuItem() {
        super();
    }

    public JRadioButtonMenuItem(java.lang.String text) {
        super(text);
    }

    public JRadioButtonMenuItem(vaadinx.swing.Icon icon) {
        super(icon);
    }

    public JRadioButtonMenuItem(java.lang.String text, vaadinx.swing.Icon icon) {
        super(text, icon);
    }

    public JRadioButtonMenuItem(java.lang.String text, boolean selected) {
        super(text);
        this.selected = selected;
    }

    public JRadioButtonMenuItem(vaadinx.swing.Icon icon, boolean selected) {
        super(icon);
        this.selected = selected;
    }

    public JRadioButtonMenuItem(java.lang.String text, vaadinx.swing.Icon icon, boolean selected) {
        super(text, icon);
        this.selected = selected;
    }

    public JRadioButtonMenuItem(javax.swing.Action a) {
        super(a);
    }

    @Override
    public boolean isSelected() {
        return selected;
    }

    @Override
    public void setSelected(boolean b) {
        // ButtonGroup veto per D_buttongroup: when in a group, group.setSelected
        // re-targets selection (cascading deselect to the prior member),
        // and group.isSelected re-reads the group's verdict — so trying
        // to deselect-the-current via setSelected(false) ends up keeping
        // it selected (matches JDK DefaultButtonModel.setSelected flow).
        b = consultButtonGroup(b);
        if (this.selected == b) return;
        this.selected = b;
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
        // Same shape as JCheckBoxMenuItem — flip state then fire the
        // Item + Change + Action sequence. ButtonGroup coordination per
        // D_buttongroup lands at the top of the body: consult the group on the
        // proposed flip, then mutate fields only if the group's verdict
        // differs from current. ActionEvent fires unconditionally so
        // an observer-only consumer (no ItemListener) still sees the
        // click — matches JDK shape where DefaultButtonModel fires
        // Action regardless of whether the group veto suppressed
        // ItemEvent.
        // Body wrapped in vaadinx.EHelper.callSwing per R_callswing_envelope / D_callswing_loom — see
        // JMenuItem.makeOnClick javadoc for the SJMenuBar-inline-callSwing
        // hand-off reason.
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
        return "RadioButtonMenuItemUI";
    }
}
