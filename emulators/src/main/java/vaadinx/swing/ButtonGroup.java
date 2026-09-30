/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.ButtonGroup
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Emulator port of {@link javax.swing.ButtonGroup}, accepting
 * {@link vaadinx.swing.AbstractButton} subclasses (the JDK class accepts
 * {@code javax.swing.AbstractButton}, which our ported subclasses do not
 * extend). Per D_buttongroup, coordination operates on the ported buttons directly;
 * {@link javax.swing.ButtonModel}-taking JDK methods are preserved in
 * shape so import-swap compiles, but drop-and-WARN at runtime (R_match_swing_errors sub-bucket
 * (c) — {@code AbstractButton} stores selection directly, so there is no
 * model to return; migrators iterate via {@link #getElements} +
 * {@code button.isSelected()}).
 *
 * <p>Coordination mirrors JDK {@code DefaultButtonModel.setSelected}: a
 * selection-bearing subclass (JToggleButton, JCheckBoxMenuItem,
 * JRadioButtonMenuItem) consults its installed group at the top of
 * {@code setSelected} / {@code makeOnClick} via the package-private
 * {@link #setSelectedButton} / {@link #isSelectedButton} hooks. The group
 * flips its selection slot and cascades {@code setSelected(false)} to the
 * previously-selected sibling; a deselect request on the current selection
 * is a no-op (JDK's suppression of click-to-deselect a radio). Browser-click
 * coordination on JToggleButton-in-group rides the SJToggleButton→emulator
 * bridge per D_buttongroup_browser_click.
 */
public class ButtonGroup implements Serializable {

    /**
     * Buttons in the group, in insertion order. Matches JDK's protected
     * {@code Vector<AbstractButton>} field by element class — only ours
     * holds {@link vaadinx.swing.AbstractButton}, not
     * {@link javax.swing.AbstractButton}. Made package-private so tests
     * can introspect; JDK exposes it as protected, which we don't need
     * since this class is non-final but doesn't have a paired UI / impl
     * subclass that mutates the list directly.
     */
    // protected, and a Vector, because the JDK's is both and a subclass names it
    // (D_instance_field_surface). Kept final where the JDK's is not: the visibility
    // audit is about reachability, and letting a subclass null out the group's own
    // storage buys nothing a migrator wants.
    protected final Vector<AbstractButton> buttons = new Vector<>();

    /**
     * The currently-selected button in the group, or {@code null} when
     * nothing is selected. Stored as {@code AbstractButton} rather than
     * {@code ButtonModel} because :emulators flattened the model surface
     * (selection lives directly on JToggleButton / JCheckBoxMenuItem /
     * JRadioButtonMenuItem fields, not on a {@code DefaultButtonModel}).
     */
    private AbstractButton selectedButton;

    /** Default constructor — empty group, no selection. */
    public ButtonGroup() {
    }

    /**
     * Add a button to the group. JDK contract: a null argument is a
     * silent no-op (real Swing's body short-circuits on null without
     * NPE). If the button is already selected when added, it becomes
     * the group's selection — unless another button is already
     * selected, in which case the newly-added button is forced
     * deselected to maintain the at-most-one invariant.
     */
    public void add(AbstractButton b) {
        if (b == null) return;
        buttons.addElement(b);
        b.setButtonGroup(this);
        if (b.isSelected()) {
            if (selectedButton == null) {
                selectedButton = b;
            } else {
                // Force the newcomer off — group invariant beats the
                // newcomer's preconfigured state. Same shape JDK takes
                // (DefaultButtonModel.setGroup → group.setSelected).
                b.setSelected(false);
            }
        }
    }

    /**
     * Remove a button from the group. JDK contract: a null argument is
     * a silent no-op. If the removed button was the selection, the
     * group's selection clears.
     */
    public void remove(AbstractButton b) {
        if (b == null) return;
        buttons.removeElement(b);
        b.setButtonGroup(null);
        if (b == selectedButton) {
            selectedButton = null;
        }
    }

    /**
     * Clear the current selection. The previously-selected button
     * (if any) is told to deselect via its own {@code setSelected(false)}
     * — which fires Item + Change events, just as a JDK radio's
     * deselection would. The group's selection slot clears before the
     * cascade so the outgoing button's own setSelected re-consult of
     * the group sees a null selection (avoiding the JDK-no-op-on-false
     * branch from interfering).
     */
    public void clearSelection() {
        if (selectedButton != null) {
            AbstractButton old = selectedButton;
            selectedButton = null;
            old.setSelected(false);
        }
    }

    /**
     * Enumerate the buttons in insertion order. Returns
     * {@code Enumeration<AbstractButton>} where {@code AbstractButton}
     * is {@link vaadinx.swing.AbstractButton} — JDK's signature is
     * {@code Enumeration<javax.swing.AbstractButton>}, an irreducible
     * cross-package divergence (D_buttongroup_cross_package). Migrators iterating with a
     * {@code for (vaadinx.swing.AbstractButton b : Collections.list(group.getElements()))}
     * loop work without changes; cross-cast attempts to JDK
     * AbstractButton would fail, but that pattern doesn't survive an
     * import-swap anyway.
     */
    public Enumeration<AbstractButton> getElements() {
        return buttons.elements();
    }

    /** Number of buttons in the group. */
    public int getButtonCount() {
        return buttons.size();
    }

    /**
     * JDK contract: returns the selected button's {@code ButtonModel}.
     * :emulators doesn't model {@code ButtonModel}, so this returns null
     * with a WARN per R_match_swing_errors sub-bucket (c). Migrators relying on
     * {@code group.getSelection()} chained dereferences (e.g.
     * {@code group.getSelection().isSelected()}) will NPE — documented
     * gap; use {@link #getElements} + per-button {@code isSelected()}
     * instead.
     */
    public javax.swing.ButtonModel getSelection() {
        vaadinx.EHelper.onUnimplemented("ButtonGroup", "getSelection");
        return null;
    }

    /**
     * JDK contract: true if the given model is the group's selection.
     * Drop-and-WARN — :emulators doesn't model ButtonModel, so the
     * argument has no canonical match against our {@code selectedButton}
     * slot. Always returns false. Use {@link AbstractButton#isSelected}
     * directly. R_match_swing_errors sub-bucket (c).
     */
    public boolean isSelected(javax.swing.ButtonModel m) {
        vaadinx.EHelper.onUnimplemented("ButtonGroup", "isSelected", m);
        return false;
    }

    /**
     * JDK contract: when {@code b} is true and {@code m} is not the
     * current selection, set it as the new selection (deselecting the
     * old one). Drop-and-WARN — :emulators doesn't model ButtonModel.
     * Use the package-private {@link #setSelectedButton(AbstractButton, boolean)}
     * coordination hook from inside the ported AbstractButton subclasses
     * instead. R_match_swing_errors sub-bucket (c).
     */
    public void setSelected(javax.swing.ButtonModel m, boolean b) {
        vaadinx.EHelper.onUnimplemented("ButtonGroup", "setSelected", m, b);
    }

    // ------------------------------------------------------------------
    // Package-private coordination hooks called from AbstractButton
    // subclasses' setSelected / makeOnClick. These are the ported
    // counterparts of JDK's group.setSelected(model, b) /
    // group.isSelected(model) — the same shape the JDK uses when
    // coordinating a DefaultButtonModel.setSelected through a group.
    // ------------------------------------------------------------------

    /**
     * Coordination entry point — called from a button's setSelected /
     * makeOnClick when the button has this group installed. Mirrors JDK
     * {@code ButtonGroup.setSelected(ButtonModel, boolean)}: when
     * {@code b} is true and {@code requester} differs from the current
     * selection, it becomes the new selection and the prior selection
     * (if any) cascades to {@code setSelected(false)}. When {@code b}
     * is false, this is a no-op — the requester's own setSelected then
     * re-reads {@link #isSelectedButton} (which still returns true if
     * the requester was the selection), discovers that the group
     * vetoed the deselection, and keeps itself selected.
     */
    void setSelectedButton(AbstractButton requester, boolean b) {
        if (b && requester != null && requester != selectedButton) {
            AbstractButton oldSelection = selectedButton;
            selectedButton = requester;
            if (oldSelection != null) {
                // Cascade the deselection. The recursive call lands in
                // the old button's setSelected(false) — its group
                // consult sees b=false and is a no-op (per the branch
                // above), then b becomes whatever the group says it
                // is via isSelectedButton(old) — false, since
                // selectedButton has already moved on. So the old
                // button's own state machine proceeds with b=false and
                // fires DESELECTED.
                oldSelection.setSelected(false);
            }
        }
    }

    /**
     * Coordination read-back — called from a button's setSelected /
     * makeOnClick AFTER {@link #setSelectedButton} to discover the
     * group's verdict on whether the button should end up selected.
     * Mirrors JDK {@code ButtonGroup.isSelected(ButtonModel)}.
     */
    boolean isSelectedButton(AbstractButton b) {
        return b != null && b == selectedButton;
    }
}
