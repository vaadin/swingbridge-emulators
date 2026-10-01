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

import javax.swing.ButtonModel;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Vector;

/**
 * Emulator port of {@link javax.swing.ButtonGroup}, accepting
 * {@link vaadinx.swing.AbstractButton} subclasses (the JDK class accepts
 * {@code javax.swing.AbstractButton}, which our ported subclasses do not
 * extend). Coordinates the buttons' {@link javax.swing.ButtonModel}s as the
 * JDK's does: {@link #getSelection} is the selected button's model, and
 * {@link #setSelected(javax.swing.ButtonModel, boolean)} deselects the previous
 * selection before selecting the new one.
 *
 * <p>The JDK keeps a model's group on the model, as a {@code javax.swing.ButtonGroup},
 * which this group is not. So membership is recorded on the emulator's
 * {@link JToggleButton.ToggleButtonModel} instead, whose {@code setSelected}
 * consults the group where the JDK's consults its own — which is what makes a
 * browser click on a selected radio button leave it selected. A button whose model
 * is some other {@code ButtonModel} joins the group's bookkeeping but is never
 * consulted, as a {@code JButton}'s {@code DefaultButtonModel} is not in the JDK.
 */
public class ButtonGroup implements Serializable {

    // protected, and a Vector, because the JDK's is both and a subclass names it
    // (D_instance_field_surface). Kept final where the JDK's is not: the visibility
    // audit is about reachability, and letting a subclass null out the group's own
    // storage buys nothing a migrator wants.
    protected final Vector<AbstractButton> buttons = new Vector<>();

    /** The selected button's model, or {@code null} when nothing is selected. */
    ButtonModel selection = null;

    /** Default constructor — empty group, no selection. */
    public ButtonGroup() {
    }

    /**
     * Adds a button; {@code null} is a silent no-op, as in the JDK. A selected newcomer
     * becomes the selection unless there already is one, in which case it is deselected
     * before it joins, so the group does not veto that.
     */
    public void add(AbstractButton b) {
        if (b == null) {
            return;
        }
        buttons.addElement(b);

        if (b.isSelected()) {
            if (selection == null) {
                selection = b.getModel();
            } else {
                b.setSelected(false);
            }
        }

        setGroup(b.getModel(), this);
    }

    /** Removes a button; {@code null} is a silent no-op, as in the JDK. */
    public void remove(AbstractButton b) {
        if (b == null) {
            return;
        }
        buttons.removeElement(b);
        if (b.getModel() == selection) {
            selection = null;
        }
        setGroup(b.getModel(), null);
    }

    /** Clears the selection: afterwards no button in the group is selected. */
    public void clearSelection() {
        if (selection != null) {
            ButtonModel oldSelection = selection;
            selection = null;
            oldSelection.setSelected(false);
        }
    }

    public Enumeration<AbstractButton> getElements() {
        return buttons.elements();
    }

    public ButtonModel getSelection() {
        return selection;
    }

    /**
     * Makes {@code m} the selection when {@code b} is {@code true}, deselecting the previous
     * one first. Deselecting is not done here: {@code false} does nothing.
     */
    public void setSelected(ButtonModel m, boolean b) {
        if (b && m != null && m != selection) {
            ButtonModel oldSelection = selection;
            selection = m;
            if (oldSelection != null) {
                oldSelection.setSelected(false);
            }
            m.setSelected(true);
        }
    }

    public boolean isSelected(ButtonModel m) {
        return (m == selection);
    }

    public int getButtonCount() {
        return buttons.size();
    }

    /** The JDK's {@code model.setGroup(group)}, onto the one model kind that consults a group. */
    private static void setGroup(ButtonModel model, ButtonGroup group) {
        if (model instanceof JToggleButton.ToggleButtonModel toggle) {
            toggle.buttonGroup = group;
        }
    }
}
