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
import vaadinx.EHelper;

import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

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

    /**
     * Runs {@code body} with the WARN hook capturing, restoring the prior hook.
     * The three drop-and-WARN tests below share the shape.
     */
    private static List<String> capturingWarns(Runnable body) {
        List<String> warned = new ArrayList<>();
        Consumer<String> prior = EHelper.warnHook;
        EHelper.warnHook = warned::add;
        try {
            body.run();
        } finally {
            EHelper.warnHook = prior;
        }
        return warned;
    }

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
    @DisplayName("getSelection drops to WARN and returns null per R_match_swing_errors (c)")
    void getSelectionWarnsAndReturnsNull() {
        ButtonGroup g = new ButtonGroup();
        g.add(new JRadioButtonMenuItem("A", true));
        List<String> warned = capturingWarns(() -> assertNull(g.getSelection()));
        assertTrue(warned.stream().anyMatch(
                w -> w.contains("ButtonGroup") && w.contains("getSelection")));
    }

    @Test
    @DisplayName("isSelected(ButtonModel) drops to WARN and returns false")
    void isSelectedModelWarnsAndReturnsFalse() {
        ButtonGroup g = new ButtonGroup();
        List<String> warned = capturingWarns(() -> assertFalse(g.isSelected(null)));
        assertTrue(warned.stream().anyMatch(
                w -> w.contains("ButtonGroup") && w.contains("isSelected")));
    }

    @Test
    @DisplayName("setSelected(ButtonModel, boolean) drops to WARN")
    void setSelectedModelWarns() {
        ButtonGroup g = new ButtonGroup();
        List<String> warned = capturingWarns(() -> g.setSelected(null, true));
        assertTrue(warned.stream().anyMatch(
                w -> w.contains("ButtonGroup") && w.contains("setSelected")));
    }

    @Test
    @DisplayName("JButton in group never becomes selected (no selection state)")
    void plainButtonNeverBecomesTheSelection() {
        // JButton's setSelected is onNoop — adding it to a group is
        // shape-only. AbstractButton.isSelected returns false (also
        // onNoop). Group.add proceeds; the button counts in
        // getButtonCount but never becomes the selection.
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
