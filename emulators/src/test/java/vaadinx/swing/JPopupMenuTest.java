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
import com.vaadin.swingbridge.surrogates.SJPopupMenu;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for D_jpopupmenu's JPopupMenu emulator. Covers:
 *
 * <ol>
 *  <li>Peer is SJPopupMenu.</li>
 *  <li>R_leaf_peer_lockdown lock-down: protected (Component peer) ctor is absent.</li>
 *  <li>add / addSeparator / insert / remove / removeAll cascade to a tree push.</li>
 *  <li>getComponentCount reflects the JDK menu tree.</li>
 *  <li>Nested JMenu + JCheckBoxMenuItem checkable nodes push correctly.</li>
 *  <li>setEnabled cascades disabled state to children.</li>
 *  <li>setComponentPopupMenu binds the popup's peer as the host's ContextMenu
 *     target via the surrogate helper; round-trip + null-clear.</li>
 *  <li>show(x,y) binds the target + WARNs on programmatic open.</li>
 *  <li>getUIClassID == "PopupMenuUI".</li>
 *  <li>Browser-driven open/close fires PopupMenuListener + the "visible" bound
 *     property in the JDK's order; listener storage round-trips
 *     (Q_popupmenu_visible_sync).</li>
 * </ol>
 */
class JPopupMenuTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    private void installWarnHook() {
        capturedWarns.clear();
        EHelper.warnHook = capturedWarns::add;
    }

    private void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op */ };
    }

    @Test
    @DisplayName("peer is SJPopupMenu")
    void peerIsSJPopupMenu() {
        assertInstanceOf(SJPopupMenu.class, new JPopupMenu().getPeer());
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected (Component peer) ctor")
    void noPeerTakingCtor() {
        Optional<Constructor<?>> seamCtor = Arrays.stream(JPopupMenu.class.getDeclaredConstructors())
                .filter(ctor -> ctor.getParameterCount() == 1
                        && com.vaadin.flow.component.Component.class
                                .isAssignableFrom(ctor.getParameterTypes()[0]))
                .findFirst();
        assertNull(seamCtor.orElse(null),
                "JPopupMenu is a JDK leaf — protected (Component peer) ctor must be absent "
                        + "per R_leaf_peer_lockdown");
    }

    @Test
    @DisplayName("add returns the item and pushes")
    void addReturnsTheItemAndPushes() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        JMenuItem item = new JMenuItem("Copy");
        assertSame(item, popup.add(item));
        assertEquals(1, sj.getCurrentTree().size());
        assertEquals("Copy", sj.getCurrentTree().get(0).text());
    }

    @Test
    @DisplayName("add(String) builds a JMenuItem")
    void addStringBuildsAMenuItem() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        popup.add("Paste");
        assertEquals("Paste", sj.getCurrentTree().get(0).text());
    }

    @Test
    @DisplayName("addSeparator pushes a separator node")
    void addSeparatorPushesASeparatorNode() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        popup.add("Copy");
        popup.addSeparator();
        popup.add("Delete");
        assertEquals(3, popup.getComponentCount());
        assertEquals(3, sj.getCurrentTree().size());
        assertTrue(sj.getCurrentTree().get(1).separator());
    }

    @Test
    @DisplayName("getComponentCount reflects the JDK tree")
    void componentCountReflectsTheTree() {
        JPopupMenu popup = new JPopupMenu();
        popup.add("A");
        popup.add("B");
        assertEquals(2, popup.getComponentCount());
    }

    @Test
    @DisplayName("insert + remove + removeAll push")
    void insertRemoveRemoveAllPush() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        JMenuItem a = new JMenuItem("A");
        JMenuItem b = new JMenuItem("B");
        popup.add(a);
        popup.insert(b, 0);
        assertEquals("B", sj.getCurrentTree().get(0).text());
        popup.remove(b);
        assertEquals(1, sj.getCurrentTree().size());
        assertEquals("A", sj.getCurrentTree().get(0).text());
        popup.removeAll();
        assertEquals(0, popup.getComponentCount());
        assertEquals(0, sj.getCurrentTree().size());
    }

    @Test
    @DisplayName("nested JMenu pushes children")
    void nestedMenuPushesChildren() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        JMenu export = new JMenu("Export");
        export.add(new JMenuItem("PDF"));
        export.add(new JMenuItem("RTF"));
        popup.add(export);
        assertEquals(1, sj.getCurrentTree().size());
        assertEquals(2, sj.getCurrentTree().get(0).children().size());
    }

    @Test
    @DisplayName("checkbox menu item pushes checkable node")
    void checkboxItemPushesCheckableNode() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        JCheckBoxMenuItem cb = new JCheckBoxMenuItem("Wrap");
        cb.setSelected(true);
        popup.add(cb);
        MenuNode node = sj.getCurrentTree().get(0);
        assertTrue(node.checkable());
        assertTrue(node.checked());
    }

    @Test
    @DisplayName("setEnabled cascades to children in pushed tree")
    void setEnabledCascadesToChildren() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        JMenu export = new JMenu("Export");
        export.add(new JMenuItem("PDF"));
        popup.add(export);
        popup.setEnabled(false);
        MenuNode root = sj.getCurrentTree().get(0);
        assertFalse(root.enabled());
        assertFalse(root.children().get(0).enabled());
    }

    @Test
    @DisplayName("detached item mutations reach the peer only after add")
    void detachedMutationsReachThePeerAfterAdd() {
        JPopupMenu popup = new JPopupMenu();
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        JMenu export = new JMenu("Export");
        export.add(new JMenuItem("PDF"));
        assertEquals(0, sj.getCurrentTree().size());
        popup.add(export);
        assertEquals(1, sj.getCurrentTree().get(0).children().size());
    }

    // --- setComponentPopupMenu cross-cutting (SD_sjpopupmenu / D_jpopupmenu) ------------

    @Test
    @DisplayName("setComponentPopupMenu binds popup peer as host ContextMenu target")
    void setComponentPopupMenuBindsTheTarget() {
        JButton host = new JButton("Right-click me");
        JPopupMenu popup = new JPopupMenu();
        popup.add("Copy");
        host.setComponentPopupMenu(popup);
        assertSame(popup, host.getComponentPopupMenu());
        SJPopupMenu sjPopup = (SJPopupMenu) popup.getPeer();
        // The surrogate helper called sjPopup.setTarget(host.peer).
        assertSame(host.getPeer(), sjPopup.getTarget());
        // Clear.
        host.setComponentPopupMenu(null);
        assertNull(host.getComponentPopupMenu());
        assertNull(sjPopup.getTarget());
    }

    @Test
    @DisplayName("show binds target and WARNs on programmatic open")
    void showBindsTargetAndWarns() {
        installWarnHook();
        try {
            JPopupMenu popup = new JPopupMenu();
            JButton invoker = new JButton("x");
            popup.show(invoker, 10, 20);
            SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
            assertSame(invoker.getPeer(), sj.getTarget());
            assertTrue(capturedWarns.stream()
                    .anyMatch(w -> w.contains("show/open-at-coordinates")));
            assertTrue(popup.isVisible(),
                    "the JDK's show() ends in setVisible(true), so the state moves even though "
                            + "the coordinates and the programmatic open are both declined");
        } finally {
            resetWarnHook();
        }
    }

    @Test
    @DisplayName("getUIClassID is PopupMenuUI")
    void uiClassIdIsPopupMenuUI() {
        assertEquals("PopupMenuUI", new JPopupMenu().getUIClassID());
    }

    // --- Peer→Swing open-state sync (Q_popupmenu_visible_sync in D_jpopupmenu) ---------

    /**
     * Drives the browser path the way the client does: {@code ContextMenuBase}
     * fires its {@code OpenedChangeEvent} from a property-change listener on
     * the element's {@code "opened"} property, so a server-side property write
     * reaches the same seam.
     */
    private static void browserSetsOpened(SJPopupMenu sj, boolean opened) {
        sj.getElement().setProperty("opened", opened);
    }

    @Test
    @DisplayName("browser open/close fires PopupMenuListener + \"visible\" in JDK order")
    void browserOpenCloseFiresListenersInJdkOrder() {
        JPopupMenu popup = new JPopupMenu();
        popup.add("Copy");
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        List<String> log = new ArrayList<>();
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                assertSame(popup, e.getSource());
                log.add("willBecomeVisible");
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                log.add("willBecomeInvisible");
            }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                log.add("canceled");
            }
        });
        popup.addPropertyChangeListener("visible", e -> log.add("visible=" + e.getNewValue()));

        browserSetsOpened(sj, true);
        assertTrue(popup.isVisible(), "the browser's own open writes the JDK's popup-visible bit");
        // JDK setVisible order: PopupMenuListener first, then the bound property.
        assertEquals(List.of("willBecomeVisible", "visible=true"), log);

        browserSetsOpened(sj, false);
        assertFalse(popup.isVisible());
        assertEquals(List.of("willBecomeVisible", "visible=true",
                "willBecomeInvisible", "visible=false"), log,
                "close fires willBecomeInvisible only — canceled is undeliverable "
                        + "(the peer signal can't distinguish cancel from selection-close)");
    }

    @Test
    @DisplayName("setVisible(false) really closes the peer, and fires the JDK's pair exactly once")
    void setVisibleFalseClosesThePeer() {
        // The hide is not a declined effect: ContextMenuBase has close(). Which
        // means the emulator writes the peer's open state, so the OpenedChangeEvent
        // echo has to be guarded — hence exactly one fire of each, not two.
        JPopupMenu popup = new JPopupMenu();
        popup.add("Copy");
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();
        browserSetsOpened(sj, true);
        assertTrue(sj.isOpened());

        List<String> log = new ArrayList<>();
        popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                log.add("willBecomeVisible");
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                log.add("willBecomeInvisible");
            }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                log.add("canceled");
            }
        });
        popup.addPropertyChangeListener("visible", e -> log.add("visible=" + e.getNewValue()));

        popup.setVisible(false);

        assertFalse(sj.isOpened(), "the peer is really closed, not just the Swing-side bit");
        assertFalse(popup.isVisible());
        assertEquals(List.of("willBecomeInvisible", "visible=false"), log);
    }

    @Test
    @DisplayName("a declined programmatic open still keeps the state and fires the JDK's pair")
    void setVisibleTrueKeepsStateWithoutTheEffect() {
        // R_decline_effect_only: Vaadin has no programmatic open, so the popup does
        // not appear — but a migrator reading isVisible() back must see what the JDK
        // would answer (measured true on JDK 25 with no invoker at all).
        JPopupMenu popup = new JPopupMenu();
        popup.add("Copy");
        SJPopupMenu sj = (SJPopupMenu) popup.getPeer();

        popup.setVisible(true);

        assertTrue(popup.isVisible(), "state kept");
        assertFalse(sj.isOpened(), "effect declined — the peer never opened");
    }

    @Test
    @DisplayName("PopupMenuListener storage round-trips; remove unsubscribes")
    void popupMenuListenerStorageRoundTrips() {
        JPopupMenu popup = new JPopupMenu();
        List<String> log = new ArrayList<>();
        javax.swing.event.PopupMenuListener l = new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {
                log.add("visible");
            }
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                log.add("invisible");
            }
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                log.add("canceled");
            }
        };
        popup.addPopupMenuListener(l);
        assertEquals(1, popup.getPopupMenuListeners().length);
        assertSame(l, popup.getPopupMenuListeners()[0]);
        popup.removePopupMenuListener(l);
        assertEquals(0, popup.getPopupMenuListeners().length);
        browserSetsOpened((SJPopupMenu) popup.getPeer(), true);
        assertEquals(List.of(), log, "removed listener no longer fires");
    }
}
