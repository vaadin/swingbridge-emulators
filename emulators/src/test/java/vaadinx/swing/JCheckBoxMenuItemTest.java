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

import com.github.mvysny.kaributesting.v10.ContextMenuKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.MenuNode;
import com.vaadin.swingbridge.surrogates.SJMenuBar;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import javax.swing.event.ChangeEvent;

import java.awt.event.ItemEvent;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for D_menu_tree's JCheckBoxMenuItem emulator. Covers:
 *
 * <ol>
 *  <li>R_leaf_peer_lockdown lock-down: protected (Component peer) ctor is absent.</li>
 *  <li>selected / state round-trip; setState alias.</li>
 *  <li>ItemEvent + ChangeEvent fan-out on setSelected.</li>
 *  <li>MenuNode flags checkable=true + checked tracks selected.</li>
 *  <li>Click handler flips state and fires Item + Change + Action.</li>
 * </ol>
 */
class JCheckBoxMenuItemTest extends AbstractKaribuTest {

    /** The first (and only) menu item node under a one-menu bar. */
    private static MenuNode itemNode(SJMenuBar sjbar, int index) {
        return sjbar.getCurrentTree().get(0).children().get(index);
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected (Component peer) ctor")
    void noPeerTakingCtor() {
        Optional<Constructor<?>> seamCtor =
                Arrays.stream(JCheckBoxMenuItem.class.getDeclaredConstructors())
                        .filter(ctor -> ctor.getParameterCount() == 1
                                && com.vaadin.flow.component.Component.class
                                        .isAssignableFrom(ctor.getParameterTypes()[0]))
                        .findFirst();
        assertNull(seamCtor.orElse(null),
                "JCheckBoxMenuItem is a JDK leaf — protected (Component peer) ctor must be absent "
                        + "per R_leaf_peer_lockdown");
    }

    @Test
    @DisplayName("setState alias for setSelected")
    void setStateIsAnAliasForSetSelected() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("On");
        item.setState(true);
        assertTrue(item.getState());
        assertTrue(item.isSelected());
    }

    @Test
    @DisplayName("ctor with state seeds field")
    void ctorWithStateSeedsField() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("On", true);
        assertTrue(item.getState());
    }

    @Test
    @DisplayName("setSelected fires ItemEvent")
    void setSelectedFiresItemEvent() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("On");
        List<ItemEvent> events = new ArrayList<>();
        item.addItemListener(events::add);
        item.setSelected(true);
        assertEquals(ItemEvent.SELECTED, assertSingle(events).getStateChange());
    }

    @Test
    @DisplayName("setSelected fires ChangeEvent")
    void setSelectedFiresChangeEvent() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("On");
        List<ChangeEvent> events = new ArrayList<>();
        item.addChangeListener(events::add);
        item.setSelected(true);
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setSelected with same value is a no-op")
    void setSelectedWithSameValueIsANoOp() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("On", true);
        Counter itemFired = new Counter();
        Counter changeFired = new Counter();
        item.addItemListener(e -> itemFired.inc());
        item.addChangeListener(e -> changeFired.inc());
        item.setSelected(true);
        itemFired.assertEquals(0);
        changeFired.assertEquals(0);
    }

    @Test
    @DisplayName("MenuNode flags checkable + checked from state")
    void menuNodeFlagsCheckableAndChecked() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Options");
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("Toggle", true);
        bar.add(m);
        m.add(item);
        MenuNode node = itemNode(sjbar, 0);
        assertTrue(node.checkable());
        assertTrue(node.checked());
    }

    @Test
    @DisplayName("simulated click flips state and fires Item Change Action")
    void simulatedClickFlipsStateAndFiresAll() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Options");
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("Toggle");
        Counter actionFired = new Counter();
        Counter itemFired = new Counter();
        Counter changeFired = new Counter();
        item.addActionListener(e -> actionFired.inc());
        item.addItemListener(e -> itemFired.inc());
        item.addChangeListener(e -> changeFired.inc());
        bar.add(m);
        m.add(item);
        // Simulate the rebuild's click bridge — flips state.
        itemNode(sjbar, 0).onClick().run();
        assertTrue(item.getState());
        actionFired.assertEquals(1);
        itemFired.assertEquals(1);
        // JDK 25's count, measured: armed, pressed, the selection, released, disarmed.
        changeFired.assertEquals(5);
    }

    @Test
    @DisplayName("inherited isCheckable from MenuNode is true")
    void inheritedCheckableIsTrue() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("X");
        // Indirect — via collectChildren when added to a menu.
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("M");
        bar.add(m);
        m.add(item);
        assertTrue(itemNode(sjbar, 0).checkable());
    }

    @Test
    @DisplayName("getUIClassID is CheckBoxMenuItemUI")
    void uiClassIdIsCheckBoxMenuItemUI() {
        assertEquals("CheckBoxMenuItemUI", new JCheckBoxMenuItem().getUIClassID());
    }

    @Test
    @DisplayName("default state is false")
    void defaultStateIsFalse() {
        assertFalse(new JCheckBoxMenuItem("X").getState());
    }

    @Test
    @DisplayName("ButtonGroup-coordinated click cascades deselect to prior selection")
    void buttonGroupClickCascadesDeselect() {
        // JDK allows JCheckBoxMenuItem in a ButtonGroup (rare but legal);
        // the group enforces at-most-one just like for radio items.
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("Options");
        JCheckBoxMenuItem a = new JCheckBoxMenuItem("A", true);
        JCheckBoxMenuItem b = new JCheckBoxMenuItem("B");
        bar.add(m);
        m.add(a);
        m.add(b);
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);
        itemNode(sjbar, 1).onClick().run();
        assertFalse(a.getState());
        assertTrue(b.getState());
    }

    /** The rendered Vaadin item at {@code index} under the bar's first menu. */
    private static MenuItem renderedItem(SJMenuBar sjbar, int index) {
        return sjbar.getItems().get(0).getSubMenu().getItems().get(index);
    }

    /**
     * A browser click must not rebuild the menu: that would detach the item
     * Vaadin is toggling, and Vaadin's queued checked-state update for it
     * then throws in the browser (vaadin/flow-components#10227).
     */
    @Test
    @DisplayName("a browser click keeps the rendered items and checks the clicked one")
    void browserClickKeepsRenderedItems() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        UI.getCurrent().add(sjbar);
        JMenu m = new JMenu("View");
        JCheckBoxMenuItem toolbar = new JCheckBoxMenuItem("Show toolbar");
        bar.add(m);
        m.add(toolbar);
        MenuItem rendered = renderedItem(sjbar, 0);

        ContextMenuKt._click(sjbar, rendered);

        assertTrue(toolbar.getState());
        assertSame(rendered, renderedItem(sjbar, 0));
        assertTrue(rendered.isChecked());
    }

    @Test
    @DisplayName("toggling an item keeps a sibling's rendered icon, so the menu updates in place")
    void toggleKeepsSiblingIcon() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("View");
        JMenuItem zoom = new JMenuItem("Zoom", new VaadinIconAdapter(VaadinIcon.SEARCH_PLUS));
        JCheckBoxMenuItem toolbar = new JCheckBoxMenuItem("Show toolbar");
        bar.add(m);
        m.add(zoom);
        m.add(toolbar);
        MenuItem renderedZoom = renderedItem(sjbar, 0);

        toolbar.setSelected(true);

        assertSame(renderedZoom, renderedItem(sjbar, 0));
        assertTrue(renderedItem(sjbar, 1).isChecked());
    }
}
