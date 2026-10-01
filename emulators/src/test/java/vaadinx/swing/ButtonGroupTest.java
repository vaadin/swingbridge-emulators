/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.awt.event.ItemEvent;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for D_buttongroup — vaadinx.swing.ButtonGroup
 * select-one-of-N coordination across ported AbstractButton subclasses.
 * Covers the API surface only; click-driven and bridge-driven coordination
 * is exercised in JRadioButtonMenuItemTest / JCheckBoxMenuItemTest /
 * JToggleButtonTest extensions.
 */
class ButtonGroupTest extends AbstractKaribuTest {

    @Test
    @DisplayName("empty group has no selection and zero count")
    void emptyGroupIsEmpty() {
        ButtonGroup g = new ButtonGroup();
        assertEquals(0, g.getButtonCount());
        assertFalse(g.getElements().hasMoreElements());
    }

    @Test
    @DisplayName("add null is a silent no-op")
    void addNullIsANoOp() {
        ButtonGroup g = new ButtonGroup();
        g.add(null);
        assertEquals(0, g.getButtonCount());
    }

    @Test
    @DisplayName("remove null is a silent no-op")
    void removeNullIsANoOp() {
        ButtonGroup g = new ButtonGroup();
        g.remove(null);
        assertEquals(0, g.getButtonCount());
    }

    @Test
    @DisplayName("add stores button + getElements enumerates in order")
    void addStoresAndEnumeratesInOrder() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A");
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        JRadioButtonMenuItem c = new JRadioButtonMenuItem("C");
        g.add(a);
        g.add(b);
        g.add(c);
        assertEquals(3, g.getButtonCount());
        List<AbstractButton> list = Collections.list(g.getElements());
        assertSame(a, list.get(0));
        assertSame(b, list.get(1));
        assertSame(c, list.get(2));
    }

    @Test
    @DisplayName("selecting one of N deselects the others")
    void selectingOneDeselectsTheOthers() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A");
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        JRadioButtonMenuItem c = new JRadioButtonMenuItem("C");
        g.add(a);
        g.add(b);
        g.add(c);
        a.setSelected(true);
        assertTrue(a.isSelected());
        assertFalse(b.isSelected());
        assertFalse(c.isSelected());

        b.setSelected(true);
        assertFalse(a.isSelected());
        assertTrue(b.isSelected());
        assertFalse(c.isSelected());

        c.setSelected(true);
        assertFalse(a.isSelected());
        assertFalse(b.isSelected());
        assertTrue(c.isSelected());
    }

    @Test
    @DisplayName("cannot deselect the currently-selected member by setSelected(false)")
    void cannotDeselectTheCurrentSelection() {
        // JDK behaviour: ButtonGroup.setSelected(model, false) is a no-op,
        // and the button's setSelected then re-reads group.isSelected
        // (which still says true for the lone selection) and keeps itself
        // selected. Suppresses click-to-deselect-current.
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        g.add(a);
        g.add(b);
        a.setSelected(false);
        assertTrue(a.isSelected(),
                "Group invariant — selected radio cannot be deselected by setSelected(false)");
    }

    @Test
    @DisplayName("clearSelection deselects the current selection and fires DESELECTED")
    void clearSelectionFiresDeselected() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        g.add(a);
        g.add(b);
        Counter aDeselected = new Counter();
        a.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.DESELECTED) {
                aDeselected.inc();
            }
        });
        g.clearSelection();
        assertFalse(a.isSelected());
        assertFalse(b.isSelected());
        aDeselected.assertEquals(1);
    }

    @Test
    @DisplayName("ctor-time selected becomes group selection if first")
    void firstPreSelectedWins() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        g.add(a);
        assertTrue(a.isSelected());
        // Now add a second pre-selected button — the group should force
        // it off, since the at-most-one invariant outranks the newcomer.
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B", true);
        g.add(b);
        assertTrue(a.isSelected());
        assertFalse(b.isSelected());
    }

    @Test
    @DisplayName("remove tears down membership and clears selection if removed was selected")
    void removeTearsDownMembership() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        g.add(a);
        g.add(b);
        g.remove(a);
        assertEquals(1, g.getButtonCount());
        // After removal, b can be set selected freely (a is no longer
        // policed by the group, so its state isn't disturbed).
        b.setSelected(true);
        assertTrue(b.isSelected());
        assertTrue(a.isSelected(), "Removed-from-group button keeps its prior state");
    }

    @Test
    @DisplayName("remove when removed was not selected leaves selection intact")
    void removeOfUnselectedLeavesSelection() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        g.add(a);
        g.add(b);
        g.remove(b);
        assertTrue(a.isSelected());
        assertEquals(1, g.getButtonCount());
    }

    @Test
    @DisplayName("getSelection is the selected button's model")
    void getSelectionIsTheSelectedModel() {
        ButtonGroup g = new ButtonGroup();
        assertNull(g.getSelection());
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        g.add(a);
        assertSame(a.getModel(), g.getSelection());
    }

    @Test
    @DisplayName("isSelected(ButtonModel) compares with the selection, so null matches an empty group")
    void isSelectedModelComparesWithTheSelection() {
        ButtonGroup g = new ButtonGroup();
        // JDK 25, measured: an empty group's selection is null, and so is the argument.
        assertTrue(g.isSelected(null));
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        g.add(a);
        assertTrue(g.isSelected(a.getModel()));
        assertFalse(g.isSelected(null));
    }

    @Test
    @DisplayName("setSelected(ButtonModel, true) moves the selection; false does nothing")
    void setSelectedModelMovesTheSelection() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A", true);
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        g.add(a);
        g.add(b);
        g.setSelected(b.getModel(), true);
        assertFalse(a.isSelected());
        assertTrue(b.isSelected());
        g.setSelected(b.getModel(), false);
        assertTrue(b.isSelected(), "deselecting is not the group's to do");
    }

    @Test
    @DisplayName("an unselected JButton joins the group without becoming its selection")
    void plainButtonNeverBecomesTheSelection() {
        // Its DefaultButtonModel never consults the group, as in the JDK; the button
        // counts in getButtonCount but is not the selection.
        ButtonGroup g = new ButtonGroup();
        JButton jb = new JButton("Plain");
        g.add(jb);
        assertEquals(1, g.getButtonCount());
        assertNull(g.getSelection());
    }

    @Test
    @DisplayName("mixed JCheckBoxMenuItem + JRadioButtonMenuItem coordinates as one group")
    void mixedMembershipCoordinates() {
        // JDK allows mixed-type membership. Both subclass selectSelected
        // overrides consult the group, so both participate in the
        // exclusion invariant.
        ButtonGroup g = new ButtonGroup();
        JCheckBoxMenuItem cb = new JCheckBoxMenuItem("Check");
        JRadioButtonMenuItem rb = new JRadioButtonMenuItem("Radio");
        g.add(cb);
        g.add(rb);
        cb.setSelected(true);
        rb.setSelected(true);
        assertFalse(cb.isSelected());
        assertTrue(rb.isSelected());
    }
}
