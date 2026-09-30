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
import com.vaadin.swingbridge.surrogates.MenuNode;
import com.vaadin.swingbridge.surrogates.SJMenuBar;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for D_menu_tree's JRadioButtonMenuItem emulator. Covers:
 *
 * <ol>
 *  <li>R_leaf_peer_lockdown lock-down: protected (Component peer) ctor is absent.</li>
 *  <li>selected round-trip + ItemEvent fan-out (matches JCheckBoxMenuItem
 *     mechanically, no ButtonGroup coordination per SD_sjmenubar).</li>
 *  <li>Two siblings independently selectable — documented gap.</li>
 *  <li>Renders as checkable in the MenuNode tree.</li>
 * </ol>
 */
class JRadioButtonMenuItemTest extends AbstractKaribuTest {

    /** The menu item node at {@code index} under a one-menu bar. */
    private static MenuNode itemNode(SJMenuBar sjbar, int index) {
        return sjbar.getCurrentTree().get(0).children().get(index);
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected (Component peer) ctor")
    void noPeerTakingCtor() {
        Optional<Constructor<?>> seamCtor =
                Arrays.stream(JRadioButtonMenuItem.class.getDeclaredConstructors())
                        .filter(ctor -> ctor.getParameterCount() == 1
                                && com.vaadin.flow.component.Component.class
                                        .isAssignableFrom(ctor.getParameterTypes()[0]))
                        .findFirst();
        assertNull(seamCtor.orElse(null),
                "JRadioButtonMenuItem is a JDK leaf — protected (Component peer) ctor must be "
                        + "absent per R_leaf_peer_lockdown");
    }

    @Test
    @DisplayName("setSelected round-trip + ItemEvent")
    void setSelectedRoundTripAndItemEvent() {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem("Choice");
        Counter fired = new Counter();
        item.addItemListener(e -> fired.inc());
        item.setSelected(true);
        assertTrue(item.isSelected());
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("MenuNode renders as checkable")
    void menuNodeIsCheckable() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Options");
        JRadioButtonMenuItem item = new JRadioButtonMenuItem("R", true);
        bar.add(m);
        m.add(item);
        MenuNode node = itemNode(sjbar, 0);
        assertTrue(node.checkable());
        assertTrue(node.checked());
    }

    @Test
    @DisplayName("two siblings without ButtonGroup are independently selectable")
    void siblingsWithoutAGroupAreIndependent() {
        // Without a group installed, JRadioButtonMenuItem's setSelected
        // doesn't coordinate — both can end up selected. The
        // SD_sjmenubar-documented multi-select gap applied here pre-D_buttongroup; with
        // a group installed the coordination kicks in (see next test).
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A");
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        a.setSelected(true);
        b.setSelected(true);
        assertTrue(a.isSelected());
        assertTrue(b.isSelected());
    }

    @Test
    @DisplayName("ButtonGroup enforces select-one-of-N on setSelected")
    void buttonGroupEnforcesSelectOneOfN() {
        ButtonGroup g = new ButtonGroup();
        JRadioButtonMenuItem a = new JRadioButtonMenuItem("A");
        JRadioButtonMenuItem b = new JRadioButtonMenuItem("B");
        g.add(a);
        g.add(b);
        a.setSelected(true);
        b.setSelected(true);
        assertFalse(a.isSelected());
        assertTrue(b.isSelected());
    }

    @Test
    @DisplayName("ButtonGroup-coordinated click cascades deselect to prior selection")
    void buttonGroupClickCascadesDeselect() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Theme");
        JRadioButtonMenuItem light = new JRadioButtonMenuItem("Light", true);
        JRadioButtonMenuItem dark = new JRadioButtonMenuItem("Dark");
        bar.add(m);
        m.add(light);
        m.add(dark);
        ButtonGroup g = new ButtonGroup();
        g.add(light);
        g.add(dark);

        // Simulate a click on Dark — fires Action; flips state; cascades
        // deselect onto Light through the group.
        itemNode(sjbar, 1).onClick().run();
        assertFalse(light.isSelected());
        assertTrue(dark.isSelected());
    }

    @Test
    @DisplayName("ButtonGroup-coordinated click on selected radio is suppressed")
    void buttonGroupClickOnSelectedIsSuppressed() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Theme");
        JRadioButtonMenuItem light = new JRadioButtonMenuItem("Light", true);
        JRadioButtonMenuItem dark = new JRadioButtonMenuItem("Dark");
        bar.add(m);
        m.add(light);
        m.add(dark);
        ButtonGroup g = new ButtonGroup();
        g.add(light);
        g.add(dark);

        Counter lightAction = new Counter();
        Counter lightItem = new Counter();
        light.addActionListener(e -> lightAction.inc());
        light.addItemListener(e -> lightItem.inc());

        // Click the already-selected Light item — the group vetoes the
        // deselect, so the state stays + no Item event fires. ActionEvent
        // does fire (matches JDK: a click is observed even when the
        // selection state didn't change).
        itemNode(sjbar, 0).onClick().run();
        assertTrue(light.isSelected());
        assertFalse(dark.isSelected());
        lightAction.assertEquals(1);
        lightItem.assertEquals(0);
    }

    @Test
    @DisplayName("simulated click flips state and fires events")
    void simulatedClickFlipsStateAndFires() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Options");
        JRadioButtonMenuItem item = new JRadioButtonMenuItem("Choice");
        Counter fired = new Counter();
        item.addActionListener(e -> fired.inc());
        bar.add(m);
        m.add(item);
        itemNode(sjbar, 0).onClick().run();
        assertTrue(item.isSelected());
        fired.assertEquals(1);
    }

    @Test
    @DisplayName("getUIClassID is RadioButtonMenuItemUI")
    void uiClassIdIsRadioButtonMenuItemUI() {
        assertEquals("RadioButtonMenuItemUI", new JRadioButtonMenuItem().getUIClassID());
    }
}
