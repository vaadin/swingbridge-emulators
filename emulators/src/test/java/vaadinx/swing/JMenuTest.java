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

import javax.swing.AbstractAction;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import java.awt.event.ActionEvent;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for D_menu_tree's JMenu emulator. Covers:
 *
 * <ol>
 *  <li>R_leaf_peer_lockdown lock-down: protected (Component peer) ctor is absent.</li>
 *  <li>Children list — add / insert / remove / removeAll.</li>
 *  <li>Items + separators in the pushed tree.</li>
 *  <li>Action-driven add(Action).</li>
 *  <li>Mutations bubble through parent chain to push the tree.</li>
 *  <li>setText / setEnabled fire PCE per R_swing_is_truth.</li>
 *  <li>Popup / MenuListener drop-and-WARN.</li>
 *  <li>isTopLevelMenu reflects parent.</li>
 * </ol>
 */
class JMenuTest extends AbstractKaribuTest {

    /** The pushed children of the bar's single menu. */
    private static List<MenuNode> pushedChildren(SJMenuBar sjbar) {
        return sjbar.getCurrentTree().get(0).children();
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected (Component peer) ctor")
    void noPeerTakingCtor() {
        Optional<Constructor<?>> seamCtor = Arrays.stream(JMenu.class.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1
                        && com.vaadin.flow.component.Component.class
                                .isAssignableFrom(ctor.getParameterTypes()[0]))
                .findFirst();
        assertNull(seamCtor.orElse(null),
                "JMenu is a JDK leaf — protected (Component peer) ctor must be absent "
                        + "per R_leaf_peer_lockdown");
    }

    @Test
    @DisplayName("add(item) returns the item")
    void addReturnsTheItem() {
        JMenu m = new JMenu("File");
        JMenuItem item = new JMenuItem("Save");
        assertSame(item, m.add(item));
    }

    @Test
    @DisplayName("getItemCount counts children including separators")
    void itemCountIncludesSeparators() {
        JMenu m = new JMenu("File");
        m.add(new JMenuItem("Open"));
        m.addSeparator();
        m.add(new JMenuItem("Save"));
        assertEquals(3, m.getItemCount());
    }

    @Test
    @DisplayName("getItem returns JMenuItem children, null for separators")
    void getItemIsNullForSeparators() {
        JMenu m = new JMenu("File");
        JMenuItem open = new JMenuItem("Open");
        m.add(open);
        m.addSeparator();
        assertSame(open, m.getItem(0));
        assertNull(m.getItem(1));
    }

    @Test
    @DisplayName("mutations bubble to root JMenuBar push")
    void mutationsBubbleToTheBarPush() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        bar.add(m);
        m.add(new JMenuItem("Save"));
        // The bubble from JMenu → JMenuBar pushes a new tree.
        assertEquals(1, pushedChildren(sjbar).size());
        assertEquals("Save", pushedChildren(sjbar).get(0).text());
    }

    @Test
    @DisplayName("addSeparator pushes a separator MenuNode")
    void addSeparatorPushesASeparatorNode() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        bar.add(m);
        m.add(new JMenuItem("Open"));
        m.addSeparator();
        m.add(new JMenuItem("Exit"));
        List<MenuNode> children = pushedChildren(sjbar);
        assertEquals(3, children.size());
        assertFalse(children.get(0).separator());
        assertTrue(children.get(1).separator());
        assertFalse(children.get(2).separator());
    }

    @Test
    @DisplayName("add(Action) builds a JMenuItem from the action")
    void addActionBuildsAMenuItem() {
        AbstractAction a = new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) {
                /* no-op */
            }
        };
        JMenu m = new JMenu("File");
        JMenuItem item = m.add(a);
        assertEquals("Save", item.getText());
    }

    @Test
    @DisplayName("insert(item, pos) places at the given index")
    void insertPlacesAtIndex() {
        JMenu m = new JMenu("File");
        JMenuItem a = new JMenuItem("A");
        JMenuItem b = new JMenuItem("B");
        JMenuItem c = new JMenuItem("C");
        m.add(a);
        m.add(c);
        m.insert(b, 1);
        assertSame(a, m.getItem(0));
        assertSame(b, m.getItem(1));
        assertSame(c, m.getItem(2));
    }

    @Test
    @DisplayName("remove(item) clears parent + bubbles tree push")
    void removeClearsParentAndBubbles() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        JMenuItem item = new JMenuItem("Save");
        bar.add(m);
        m.add(item);
        m.remove(item);
        assertEquals(0, m.getItemCount());
        assertEquals(0, pushedChildren(sjbar).size());
    }

    @Test
    @DisplayName("removeAll clears children + bubbles")
    void removeAllClearsAndBubbles() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        bar.add(m);
        m.add(new JMenuItem("A"));
        m.add(new JMenuItem("B"));
        m.removeAll();
        assertEquals(0, m.getItemCount());
        assertEquals(0, pushedChildren(sjbar).size());
    }

    @Test
    @DisplayName("setText fires PCE + bubbles")
    void setTextFiresPceAndBubbles() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        bar.add(m);
        AtomicBoolean fired = new AtomicBoolean(false);
        m.addPropertyChangeListener("text", e -> fired.set(true));
        m.setText("FileX");
        assertTrue(fired.get());
        assertEquals("FileX", sjbar.getCurrentTree().get(0).text());
    }

    @Test
    @DisplayName("popup setters drop-and-WARN")
    void popupSettersDropAndWarn() {
        JMenu m = new JMenu("File");
        // No exception; non-default true value WARNs.
        m.isPopupMenuVisible();      // false default
        m.setPopupMenuVisible(true); // WARN, no exception
    }

    @Test
    @DisplayName("menu listener add drops with WARN")
    void menuListenerAddDropsWithWarn() {
        JMenu m = new JMenu("File");
        m.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        // Just confirms no crash; actual listener never fires (R_match_swing_errors (a) gap).
    }

    @Test
    @DisplayName("isTopLevelMenu reflects parent")
    void isTopLevelMenuReflectsParent() {
        JMenuBar bar = new JMenuBar();
        JMenu top = new JMenu("File");
        JMenu nested = new JMenu("Nested");
        bar.add(top);
        top.add(nested);
        assertTrue(top.isTopLevelMenu());
        assertFalse(nested.isTopLevelMenu());
    }

    @Test
    @DisplayName("getUIClassID is MenuUI")
    void uiClassIdIsMenuUI() {
        assertEquals("MenuUI", new JMenu().getUIClassID());
    }
}
