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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exit gate for D_menu_tree's JMenuBar emulator. Covers:
 *
 * <ol>
 *  <li>Peer is SJMenuBar.</li>
 *  <li>R_leaf_peer_lockdown lock-down: protected (Component peer) ctor is absent.</li>
 *  <li>add / remove / removeAll cascade through to a tree push.</li>
 *  <li>getMenuCount + getMenu(int) walk the JDK tree.</li>
 *  <li>setEnabled cascades disabled state to children.</li>
 *  <li>Detached items don't push (no peer rebuild) until added.</li>
 * </ol>
 */
class JMenuBarTest extends AbstractKaribuTest {

    @Test
    @DisplayName("peer is SJMenuBar")
    void peerIsSJMenuBar() {
        JMenuBar bar = new JMenuBar();
        assertInstanceOf(SJMenuBar.class, bar.getPeer());
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected (Component peer) ctor")
    void noPeerTakingCtor() {
        // No ctor takes a single Vaadin Component arg.
        Optional<Constructor<?>> seamCtor = Arrays.stream(JMenuBar.class.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1
                        && com.vaadin.flow.component.Component.class.isAssignableFrom(ctor.getParameterTypes()[0]))
                .findFirst();
        assertNull(seamCtor.orElse(null),
                "JMenuBar is a JDK leaf — protected (Component peer) ctor must be absent "
                        + "per R_leaf_peer_lockdown");
    }

    @Test
    @DisplayName("add returns the menu")
    void addReturnsTheMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu m = new JMenu("File");
        assertSame(m, bar.add(m));
    }

    @Test
    @DisplayName("getMenuCount + getMenu walk the JDK tree")
    void menuCountAndGetMenuWalkTheTree() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenu edit = new JMenu("Edit");
        bar.add(file);
        bar.add(edit);
        assertEquals(2, bar.getMenuCount());
        assertSame(file, bar.getMenu(0));
        assertSame(edit, bar.getMenu(1));
    }

    @Test
    @DisplayName("add pushes tree to peer")
    void addPushesTreeToPeer() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        bar.add(new JMenu("File"));
        assertEquals(1, sjbar.getCurrentTree().size());
        assertEquals("File", sjbar.getCurrentTree().get(0).text());
    }

    @Test
    @DisplayName("remove pushes tree to peer")
    void removePushesTreeToPeer() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        bar.add(m);
        bar.remove(m);
        assertEquals(0, sjbar.getCurrentTree().size());
    }

    @Test
    @DisplayName("removeAll clears children + pushes")
    void removeAllClearsAndPushes() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        bar.add(new JMenu("File"));
        bar.add(new JMenu("Edit"));
        bar.removeAll();
        assertEquals(0, bar.getMenuCount());
        assertEquals(0, sjbar.getCurrentTree().size());
    }

    @Test
    @DisplayName("setMenu by index swaps + pushes")
    void setMenuByIndexSwapsAndPushes() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu a = new JMenu("A");
        JMenu b = new JMenu("B");
        bar.add(a);
        bar.setMenu(0, b);
        assertSame(b, bar.getMenu(0));
        assertEquals("B", sjbar.getCurrentTree().get(0).text());
    }

    @Test
    @DisplayName("setEnabled cascades to descendants in pushed tree")
    void setEnabledCascadesInPushedTree() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        m.add(new JMenuItem("Save"));
        bar.add(m);
        bar.setEnabled(false);
        // The cascaded enabled flag is in the pushed snapshot.
        MenuNode rootNode = sjbar.getCurrentTree().get(0);
        assertFalse(rootNode.enabled());
        assertFalse(rootNode.children().get(0).enabled());
    }

    @Test
    @DisplayName("getComponentIndex finds the menu")
    void getComponentIndexFindsTheMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu a = new JMenu("A");
        JMenu b = new JMenu("B");
        bar.add(a);
        bar.add(b);
        assertEquals(0, bar.getComponentIndex(a));
        assertEquals(1, bar.getComponentIndex(b));
        assertEquals(-1, bar.getComponentIndex(new JMenu("Other")));
    }

    @Test
    @DisplayName("getUIClassID is MenuBarUI")
    void uiClassIdIsMenuBarUI() {
        assertEquals("MenuBarUI", new JMenuBar().getUIClassID());
    }

    @Test
    @DisplayName("getMenu(out-of-range) throws IndexOutOfBoundsException")
    void getMenuOutOfRangeThrows() {
        JMenuBar bar = new JMenuBar();
        assertThrows(IndexOutOfBoundsException.class, () -> bar.getMenu(0));
    }

    @Test
    @DisplayName("add(null) silently no-ops per JDK shape")
    void addNullIsANoOp() {
        JMenuBar bar = new JMenuBar();
        assertNull(bar.add((JMenu) null));
        assertEquals(0, bar.getMenuCount());
    }

    @Test
    @DisplayName("detached menu mutations don't reach the peer")
    void detachedMenuMutationsDoNotPush() {
        JMenuBar bar = new JMenuBar();
        SJMenuBar sjbar = (SJMenuBar) bar.getPeer();
        JMenu m = new JMenu("File");
        // Mutate before adding — no parent yet, no push.
        m.add(new JMenuItem("Save"));
        // sjbar.currentTree empty until explicit push or attach via add.
        assertEquals(0, sjbar.getCurrentTree().size());
        bar.add(m);
        // After add, the push picks up the prior mutation.
        assertEquals(1, sjbar.getCurrentTree().get(0).children().size());
    }
}
