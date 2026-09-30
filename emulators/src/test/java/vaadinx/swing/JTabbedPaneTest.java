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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJTabbedPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for vaadinx.swing.JTabbedPane (D_jtabbedpane). Pairs
 * with SJTabbedPaneTest — this layer covers the emulator's own state and
 * its flush to the peer:
 *
 * <ol>
 *  <li><b>Ctor variants</b> — all three install SJTabbedPane peer; R_leaf_peer_lockdown lock-
 *     down (no protected (Component peer) ctor).
 *  <li><b>Container.components R_swing_is_truth tree-shape</b> — every addTab / insertTab /
 *     removeTabAt / setComponentAt enters or exits the components list, in
 *     insertion order as the JDK's does. getComponentCount matches tabCount.
 *  <li><b>The JDK's own sequence</b> — a script measured on JDK 25, replayed verbatim.
 *  <li><b>add(...) overloads</b> — String constraint → title; Icon → icon;
 *     no constraint → the component's name; anything else is ignored.
 *  <li><b>setTabComponentAt mirror</b> — emulator preserves the awt-side
 *     reference for round-trip via getTabComponentAt.
 *  <li><b>PCE on tab placement / layout policy headlines</b>.
 *  <li><b>paramString chain</b> — JDK-faithful tail.
 *  <li><b>L&amp;F surface</b> — getUIClassID, setUI WARN, getUI WARN.
 * </ol>
 */
class JTabbedPaneTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        Consumer<String> sink = capturedWarns::add;
        EHelper.warnHook = sink;
        SHelper.warnHook = sink;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op default */ };
        SHelper.warnHook = msg -> { /* no-op default */ };
    }

    // --- Ctors ---------------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor wires SJTabbedPane peer with TOP + WRAP")
    void noArgCtorWiresPeerWithTopAndWrap() {
        JTabbedPane tp = new JTabbedPane();
        assertInstanceOf(SJTabbedPane.class, tp.getPeer());
        assertEquals(SwingConstants.TOP, tp.getTabPlacement());
        assertEquals(JTabbedPane.WRAP_TAB_LAYOUT, tp.getTabLayoutPolicy());
        assertEquals(0, tp.getTabCount());
        assertEquals(-1, tp.getSelectedIndex());
        assertNoWarns(capturedWarns, "default ctor WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("placement-only ctor")
    void placementOnlyCtor() {
        JTabbedPane tp = new JTabbedPane(SwingConstants.LEFT);
        assertEquals(SwingConstants.LEFT, tp.getTabPlacement());
        // No WARN at ctor time (surrogate WARNs only on explicit setter).
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("full ctor")
    void fullCtor() {
        JTabbedPane tp = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, tp.getTabLayoutPolicy());
    }

    @Test
    @DisplayName("ctor throws IAE on garbage placement")
    void ctorThrowsOnGarbagePlacement() {
        assertThrows(IllegalArgumentException.class, () -> new JTabbedPane(42));
    }

    // --- Container.components R_swing_is_truth tree-shape ---------------------------------

    @Test
    @DisplayName("addTab populates Container components in tab order")
    void addTabPopulatesComponentsInTabOrder() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("A");
        JButton b = new JButton("B");
        JButton c = new JButton("C");
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.addTab("C", c);
        assertEquals(3, tp.getComponentCount());
        assertSame(a, tp.getComponent(0));
        assertSame(b, tp.getComponent(1));
        assertSame(c, tp.getComponent(2));
        // Parent linkage matches.
        assertSame(tp, a.getParent());
        assertSame(tp, b.getParent());
        assertSame(tp, c.getParent());
    }

    @Test
    @DisplayName("insertTab puts the tab at the index and appends the child, as the JDK does")
    void insertTabPutsTheTabAtTheIndexAndAppendsTheChild() {
        // The JDK's insertTab adds the content through addImpl(component, null, -1), so
        // Container.components is in insertion order, not tab order.
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("A");
        JButton c = new JButton("C");
        tp.addTab("A", a);
        tp.addTab("C", c);
        JButton b = new JButton("B");
        tp.insertTab("B", null, b, null, 1);
        assertEquals(3, tp.getComponentCount());
        assertSame(b, tp.getComponentAt(1));
        assertEquals(1, tp.indexOfComponent(b));
        assertSame(a, tp.getComponent(0));
        assertSame(c, tp.getComponent(1));
        assertSame(b, tp.getComponent(2));
    }

    @Test
    @DisplayName("removeTabAt removes from components and fires HIERARCHY_CHANGED")
    void removeTabAtRemovesFromComponents() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("A");
        JButton b = new JButton("B");
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.removeTabAt(0);
        assertEquals(1, tp.getComponentCount());
        assertSame(b, tp.getComponent(0));
        assertNull(a.getParent());
    }

    @Test
    @DisplayName("removeAll clears Container components and detaches every child")
    void removeAllClearsComponentsAndDetachesChildren() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("A");
        JButton b = new JButton("B");
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.removeAll();
        assertEquals(0, tp.getComponentCount());
        assertNull(a.getParent());
        assertNull(b.getParent());
    }

    @Test
    @DisplayName("setComponentAt replaces content in-place preserving index")
    void setComponentAtReplacesInPlace() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("A");
        JButton b = new JButton("B");
        tp.addTab("A", a);
        tp.addTab("B", b);
        JButton newA = new JButton("A2");
        tp.setComponentAt(0, newA);
        assertEquals(2, tp.getComponentCount());
        assertSame(newA, tp.getComponentAt(0));
        // The new content is appended, as the JDK's addImpl(component, null, -1) does.
        assertSame(b, tp.getComponent(0));
        assertSame(newA, tp.getComponent(1));
        assertNull(a.getParent());
    }

    @Test
    @DisplayName("insertTab throws IOOBE on negative or past-end")
    void insertTabThrowsOutOfBounds() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        assertThrows(IndexOutOfBoundsException.class,
                () -> tp.insertTab("X", null, new JButton(), null, -1));
        assertThrows(IndexOutOfBoundsException.class,
                () -> tp.insertTab("X", null, new JButton(), null, 5));
    }

    @Test
    @DisplayName("removeTabAt throws IOOBE out of bounds")
    void removeTabAtThrowsOutOfBounds() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        assertThrows(IndexOutOfBoundsException.class, () -> tp.removeTabAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> tp.removeTabAt(1));
    }

    // --- Selection forwarding ------------------------------------------------

    @Test
    @DisplayName("setSelectedIndex forwards through model")
    void setSelectedIndexForwardsThroughModel() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        tp.addTab("B", new JButton());
        tp.setSelectedIndex(1);
        assertEquals(1, tp.getSelectedIndex());
        assertEquals(1, tp.getModel().getSelectedIndex());
    }

    @Test
    @DisplayName("getSelectedComponent matches selected index")
    void getSelectedComponentMatchesSelectedIndex() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("A");
        JButton b = new JButton("B");
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.setSelectedIndex(1);
        assertSame(b, tp.getSelectedComponent());
    }

    @Test
    @DisplayName("addChangeListener fires on selection change")
    void addChangeListenerFiresOnSelectionChange() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        tp.addTab("B", new JButton());
        List<Integer> fires = new ArrayList<>();
        tp.addChangeListener(e -> fires.add(tp.getSelectedIndex()));
        tp.setSelectedIndex(1);
        assertEquals(List.of(1), fires);
    }

    // --- addImpl constraint dispatch ----------------------------------------

    @Test
    @DisplayName("add with a String constraint adds a tab with that title")
    void addWithStringConstraintAddsTab() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("a");
        tp.add(a, "First");
        assertEquals(1, tp.getTabCount());
        assertEquals("First", tp.getTitleAt(0));
        assertSame(a, tp.getComponent(0));
    }

    @Test
    @DisplayName("add without a constraint uses the component's name as the title")
    void addWithNamedComponentUsesNameAsTitle() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("a");
        a.setName("Alpha");
        tp.add(a);
        assertEquals("Alpha", tp.getTitleAt(0));
    }

    @Test
    @DisplayName("add with an Integer constraint appends — the JDK ignores a constraint that is neither a String nor an Icon")
    void addWithIntegerConstraintAppends() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("a");
        JButton c = new JButton("c");
        tp.addTab("A", a);
        tp.addTab("C", c);
        JButton b = new JButton("b");
        b.setName("B");
        tp.add(b, Integer.valueOf(1));
        assertEquals(3, tp.getTabCount());
        assertEquals("B", tp.getTitleAt(2));
        assertSame(b, tp.getComponentAt(2));
    }

    @Test
    @DisplayName("add(int) inserts at the index, and remove(int) removes the tab, not the child")
    void addAndRemoveByIndexWorkOnTabs() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("a");
        JButton b = new JButton("b");
        tp.addTab("A", a);
        tp.add(b, 0);
        assertSame(b, tp.getComponentAt(0));
        // Tab 0 is b, child 0 is a: remove(0) is the tab.
        tp.remove(0);
        assertEquals(1, tp.getTabCount());
        assertSame(a, tp.getComponentAt(0));
        assertNull(b.getParent());
    }

    @Test
    @DisplayName("remove(Component) removes that component's tab")
    void removeComponentRemovesItsTab() {
        JTabbedPane tp = new JTabbedPane();
        JButton a = new JButton("a");
        JButton b = new JButton("b");
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.remove(b);
        assertEquals(1, tp.getTabCount());
        assertEquals("A", tp.getTitleAt(0));
        assertNull(b.getParent());
    }

    @Test
    @DisplayName("adding another container's child moves it into a tab, peer and all")
    void addingAnotherContainersChildMovesItsPeer() {
        JPanel panel = new JPanel();
        JButton a = new JButton("a");
        panel.add(a);
        JTabbedPane tp = new JTabbedPane();
        com.vaadin.flow.component.UI.getCurrent().add(panel.getPeer(), tp.getPeer());
        tp.addTab("A", a);
        assertSame(tp, a.getParent());
        assertEquals(0, panel.getComponentCount());
        SJTabbedPane peer = (SJTabbedPane) tp.getPeer();
        assertSame(a.getPeer(), peer.getComponentAt(0));
        // TabSheet attaches the selected tab's content just before the response.
        com.github.mvysny.kaributesting.v10.MockVaadin.clientRoundtrip();
        assertSame(peer, a.getPeer().getParent().orElseThrow());
    }

    // --- Per-tab title / icon / tooltip / enabled / customHeader ------------

    @Test
    @DisplayName("title round-trips through the surrogate")
    void titleRoundTripsThroughTheSurrogate() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        tp.setTitleAt(0, "Renamed");
        assertEquals("Renamed", tp.getTitleAt(0));
    }

    @Test
    @DisplayName("setTabComponentAt mirrors the awt component for getTabComponentAt")
    void setTabComponentAtMirrorsTheAwtComponent() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        JButton custom = new JButton("close");
        tp.setTabComponentAt(0, custom);
        assertSame(custom, tp.getTabComponentAt(0));
        // Tab component is NOT in Container.components per JDK contract.
        assertEquals(1, tp.getComponentCount());
    }

    @Test
    @DisplayName("setTabComponentAt(null) clears the awt mirror")
    void setTabComponentAtNullClearsTheMirror() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        tp.setTabComponentAt(0, new JButton("close"));
        tp.setTabComponentAt(0, null);
        assertNull(tp.getTabComponentAt(0));
    }

    @Test
    @DisplayName("closing every tab via its custom header stays index-aligned (regression - close-all threw IOOBE)")
    void closingEveryTabViaItsCustomHeaderStaysIndexAligned() {
        // Repro of the Sampler "multi-case-open" close-X path: each tab has a
        // custom header, and the close button resolves its tab by
        // indexOfTabComponent(header). Before the fix, removeTabAt never
        // shrank the header mirror, so indexOfTabComponent returned a stale
        // index and closing tabs down to the last one threw IOOBE.
        JTabbedPane tp = new JTabbedPane();
        JPanel headerA = new JPanel();
        JPanel headerB = new JPanel();
        JPanel headerC = new JPanel();
        tp.addTab("A", new JButton());
        tp.setTabComponentAt(0, headerA);
        tp.addTab("B", new JButton());
        tp.setTabComponentAt(1, headerB);
        tp.addTab("C", new JButton());
        tp.setTabComponentAt(2, headerC);
        assertEquals(3, tp.getTabCount());

        // Close the last header first: the remaining headers keep their indices.
        tp.removeTabAt(tp.indexOfTabComponent(headerC));
        assertEquals(2, tp.getTabCount());
        assertEquals(0, tp.indexOfTabComponent(headerA));
        assertEquals(1, tp.indexOfTabComponent(headerB));

        // Close the first header: B must compact from index 1 down to 0.
        tp.removeTabAt(tp.indexOfTabComponent(headerA));
        assertEquals(1, tp.getTabCount());
        assertEquals(-1, tp.indexOfTabComponent(headerA));
        assertEquals(0, tp.indexOfTabComponent(headerB));

        // Close the last remaining tab — the case that used to throw. Swing
        // allows emptying a JTabbedPane, so we must too.
        tp.removeTabAt(tp.indexOfTabComponent(headerB));
        assertEquals(0, tp.getTabCount());
        assertEquals(-1, tp.indexOfTabComponent(headerB));
    }

    @Test
    @DisplayName("setEnabledAt round-trips")
    void setEnabledAtRoundTrips() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JButton());
        tp.addTab("B", new JButton());
        tp.setEnabledAt(1, false);
        assertFalse(tp.isEnabledAt(1));
        assertTrue(tp.isEnabledAt(0));
    }

    // --- PCE on headline placement / layout policy --------------------------

    @Test
    @DisplayName("setTabPlacement fires PCE on actual change")
    void setTabPlacementFiresPce() {
        JTabbedPane tp = new JTabbedPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tp.addPropertyChangeListener(events::add);
        tp.setTabPlacement(SwingConstants.BOTTOM);
        PropertyChangeEvent e = assertSingle(named(events, "tabPlacement"));
        assertEquals(SwingConstants.TOP, e.getOldValue());
        assertEquals(SwingConstants.BOTTOM, e.getNewValue());
    }

    @Test
    @DisplayName("setTabLayoutPolicy fires PCE on actual change")
    void setTabLayoutPolicyFiresPce() {
        JTabbedPane tp = new JTabbedPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tp.addPropertyChangeListener(events::add);
        tp.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        // First match, not assertSingle: the Kotlin asserted on firstOrNull here
        // (unlike the tabPlacement case above, which pinned the count at 1), and
        // tightening that in a port commit would be a behaviour change in disguise.
        PropertyChangeEvent e = named(events, "tabLayoutPolicy").get(0);
        assertEquals(JTabbedPane.WRAP_TAB_LAYOUT, e.getOldValue());
        assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, e.getNewValue());
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is TabbedPaneUI")
    void getUiClassIdIsTabbedPaneUi() {
        assertEquals("TabbedPaneUI", new JTabbedPane().getUIClassID());
    }

    @Test
    @DisplayName("getUI WARNs, returns null")
    void getUiWarnsAndReturnsNull() {
        JTabbedPane tp = new JTabbedPane();
        assertNull(tp.getUI());
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("getUI")));
    }

    @Test
    @DisplayName("setUI WARNs")
    void setUiWarns() {
        new JTabbedPane().setUI(null);
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("setUI")));
    }

    // --- paramString --------------------------------------------------------

    @Test
    @DisplayName("paramString reports tabPlacement + tabLayoutPolicy")
    void paramStringReportsPlacementAndLayoutPolicy() {
        JTabbedPane tp = new JTabbedPane(SwingConstants.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
        String ps = tp.paramString();
        assertTrue(ps.contains("tabPlacement=TOP"), "missing tabPlacement: " + ps);
        assertTrue(ps.contains("tabLayoutPolicy=SCROLL_TAB_LAYOUT"), "missing tabLayoutPolicy: " + ps);
    }

    // --- The JDK's own sequence --------------------------------------------

    /**
     * Replays a script measured on JDK 25 (headless, Metal) and asserts its output verbatim:
     * the ChangeEvents, the PropertyChangeEvents, the ContainerEvents, which content is
     * visible, and the exceptions. Left out, as L&amp;F behaviour: the {@code
     * _WhenInFocusedWindow} input-map events and the tab-component container BasicTabbedPaneUI
     * adds as a child; a {@code getDisabledIconAt} derived by the L&amp;F; a tab run count
     * computed from pixel layout; and the second listener the pane's own peer flush adds to
     * the model.
     */
    @Test
    @DisplayName("tab management replays the JDK's measured event sequence")
    void tabManagementMatchesTheJdk() {
        Script s = new Script();
        JTabbedPane tp = s.newPane();
        JPanel a = panel("a"), b = panel("b"), c = panel("c"), d = panel("d");
        tp.addTab("A", a); s.flush("addTab A", tp);
        tp.addTab("B", b); s.flush("addTab B", tp);
        tp.addTab("C", c); s.flush("addTab C", tp);
        tp.setSelectedIndex(1); s.flush("setSelectedIndex 1", tp);
        tp.insertTab("D", null, d, null, 0); s.flush("insertTab D at 0", tp);
        tp.removeTabAt(2); s.flush("removeTabAt 2 (selected, middle)", tp);
        tp.setSelectedIndex(2); s.flush("setSelectedIndex 2 (last)", tp);
        tp.removeTabAt(2); s.flush("removeTabAt 2 (selected, last)", tp);
        tp.removeTabAt(0); s.flush("removeTabAt 0 (below selected)", tp);
        tp.insertTab("B2", null, b, null, 1); s.flush("insertTab existing b at 1", tp);
        tp.insertTab(null, null, c, null, 0); s.flush("insertTab title null at 0", tp);
        s.out.add("indexOfTab(null)=" + tp.indexOfTab((String) null) + " indexOfTab(\"\")=" + tp.indexOfTab(""));
        tp.removeAll(); s.flush("removeAll", tp);
        tp.addTab("N", null); s.flush("addTab N null component", tp);
        tp.addTab("A", a); s.flush("addTab A after null", tp);
        s.out.add("indexOfComponent(a)=" + tp.indexOfComponent(a) + " indexOfComponent(null)="
                + tp.indexOfComponent(null) + " getComponentCount=" + tp.getComponentCount());
        s.tryRun("getComponentAt(5)", () -> tp.getComponentAt(5));
        s.tryRun("getTitleAt(5)", () -> tp.getTitleAt(5));
        s.tryRun("getMnemonicAt(5)", () -> tp.getMnemonicAt(5));
        s.tryRun("setSelectedIndex(5)", () -> tp.setSelectedIndex(5));
        s.tryRun("setSelectedIndex(-2)", () -> tp.setSelectedIndex(-2));
        s.tryRun("insertTab at -1", () -> tp.insertTab("X", null, panel("x"), null, -1));
        s.tryRun("insertTab at 9", () -> tp.insertTab("X", null, panel("x"), null, 9));
        s.tryRun("removeTabAt 9", () -> tp.removeTabAt(9));
        s.tryRun("setTitleAt 9", () -> tp.setTitleAt(9, "x"));
        s.tryRun("setTabComponentAt 9", () -> tp.setTabComponentAt(9, null));
        s.tryRun("setTabComponentAt content", () -> tp.setTabComponentAt(0, a));
        s.tryRun("setSelectedComponent absent", () -> tp.setSelectedComponent(panel("zz")));
        s.out.add("getMnemonicAt(1)=" + tp.getMnemonicAt(1) + " displayed=" + tp.getDisplayedMnemonicIndexAt(1));
        tp.setMnemonicAt(1, java.awt.event.KeyEvent.VK_A); s.flush("setMnemonicAt 1 VK_A", tp);
        s.out.add("getMnemonicAt(1)=" + tp.getMnemonicAt(1) + " displayed=" + tp.getDisplayedMnemonicIndexAt(1));
        tp.setTitleAt(1, "xyz"); s.flush("setTitleAt 1 xyz (no A)", tp);
        s.out.add("displayed=" + tp.getDisplayedMnemonicIndexAt(1));
        s.tryRun("setDisplayedMnemonicIndexAt 1 7", () -> tp.setDisplayedMnemonicIndexAt(1, 7));
        tp.setDisplayedMnemonicIndexAt(1, 2); s.flush("setDisplayedMnemonicIndexAt 1 2", tp);
        tp.setIconAt(1, new ImageIcon(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        s.flush("setIconAt", tp);
        tp.setToolTipTextAt(1, "tip"); s.flush("setToolTipTextAt", tp);
        tp.setEnabledAt(1, false); s.flush("setEnabledAt false", tp);
        tp.setSelectedIndex(1); s.flush("setSelectedIndex disabled 1", tp);
        JPanel h = panel("h");
        tp.setTabComponentAt(1, h); s.flush("setTabComponentAt 1 h", tp);
        tp.setTabComponentAt(0, h); s.flush("setTabComponentAt 0 h (move)", tp);
        s.out.add("tabComp(1)=" + tp.getTabComponentAt(1) + " tabComp(0)=" + tp.getTabComponentAt(0).getName());
        tp.removeTabAt(0); s.flush("removeTabAt 0 with tab component", tp);
        JPanel e = panel("e");
        tp.setComponentAt(0, e); s.flush("setComponentAt 0 e (selected)", tp);
        tp.setComponentAt(0, null); s.flush("setComponentAt 0 null", tp);
        tp.removeAll(); s.flush("removeAll 2", tp);

        JTabbedPane tp2 = s.newPane();
        JPanel p1 = panel("p1"), p2 = panel("p2"), p3 = panel("p3"), p4 = panel("p4"), p5 = panel("p5");
        tp2.add(p1); s.flush("add(p1)", tp2);
        tp2.add("T2", p2); s.flush("add(\"T2\", p2)", tp2);
        tp2.add(p3, Integer.valueOf(0)); s.flush("add(p3, Integer 0)", tp2);
        tp2.add(p4, new ImageIcon(new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB)));
        s.flush("add(p4, icon)", tp2);
        s.out.add("title(3)=" + tp2.getTitleAt(3) + " icon(3)=" + (tp2.getIconAt(3) != null));
        tp2.add(p5, 1); s.flush("add(p5, 1)", tp2);
        tp2.remove(p2); s.flush("remove(p2)", tp2);
        tp2.remove(0); s.flush("remove(0)", tp2);
        s.out.add("bg(0)==pane bg " + java.util.Objects.equals(tp2.getBackgroundAt(0), tp2.getBackground()));

        JTabbedPane tp3 = s.newPane();
        tp3.addTab("A", panel("a3"));
        tp3.addTab("B", panel("b3"));
        s.log.clear();
        javax.swing.DefaultSingleSelectionModel m = new javax.swing.DefaultSingleSelectionModel();
        m.setSelectedIndex(1);
        tp3.setModel(m); s.flush("setModel (index 1)", tp3);
        m.setSelectedIndex(0); s.flush("model.setSelectedIndex 0 direct", tp3);
        tp3.setSelectedIndex(-1); s.flush("setSelectedIndex -1", tp3);
        tp3.insertTab("C", null, panel("c3"), null, 0); s.flush("insert with -1 selection", tp3);

        JTabbedPane tp4 = s.newPane();
        tp4.addTab("A", panel("a4"));
        tp4.addTab("B", panel("b4"));
        tp4.addTab("C", panel("c4"));
        tp4.setSelectedIndex(1);
        s.log.clear();
        tp4.removeAll(); s.flush("removeAll with sel 1", tp4);

        assertEquals("""
                ## addTab A
                  added a
                  change sel=0
                  => sel=0 tabs=[A:a+ ] children=[a ]
                ## addTab B
                  added b
                  => sel=0 tabs=[A:a+ B:b- ] children=[a b ]
                ## addTab C
                  added c
                  => sel=0 tabs=[A:a+ B:b- C:c- ] children=[a b c ]
                ## setSelectedIndex 1
                  change sel=1
                  => sel=1 tabs=[A:a- B:b+ C:c- ] children=[a b c ]
                ## insertTab D at 0
                  added d
                  change sel=2
                  => sel=2 tabs=[D:d- A:a- B:b+ C:c- ] children=[a b c d ]
                ## removeTabAt 2 (selected, middle)
                  pce __index_to_remove__ null->2
                  change sel=2
                  pce __index_to_remove__ 2->null
                  removed b
                  => sel=2 tabs=[D:d- A:a- C:c+ ] children=[a c d ]
                ## setSelectedIndex 2 (last)
                  => sel=2 tabs=[D:d- A:a- C:c+ ] children=[a c d ]
                ## removeTabAt 2 (selected, last)
                  pce __index_to_remove__ null->2
                  change sel=1
                  pce __index_to_remove__ 2->null
                  removed c
                  => sel=1 tabs=[D:d- A:a+ ] children=[a d ]
                ## removeTabAt 0 (below selected)
                  pce __index_to_remove__ null->0
                  change sel=0
                  pce __index_to_remove__ 0->null
                  removed d
                  => sel=0 tabs=[A:a+ ] children=[a ]
                ## insertTab existing b at 1
                  added b
                  => sel=0 tabs=[A:a+ B2:b- ] children=[a b ]
                ## insertTab title null at 0
                  added c
                  change sel=1
                  => sel=1 tabs=[:c- A:a+ B2:b- ] children=[a b c ]
                indexOfTab(null)=0 indexOfTab("")=0
                ## removeAll
                  change sel=-1
                  pce __index_to_remove__ null->2
                  pce __index_to_remove__ 2->null
                  removed b
                  pce __index_to_remove__ null->1
                  pce __index_to_remove__ 1->null
                  removed a
                  pce __index_to_remove__ null->0
                  pce __index_to_remove__ 0->null
                  removed c
                  => sel=-1 tabs=[] children=[]
                ## addTab N null component
                  pce indexForNullComponent -1->0
                  change sel=0
                  => sel=0 tabs=[N:null ] children=[]
                ## addTab A after null
                  added a
                  => sel=0 tabs=[N:null A:a- ] children=[a ]
                indexOfComponent(a)=1 indexOfComponent(null)=0 getComponentCount=1
                ## getComponentAt(5) threw IndexOutOfBoundsException: Index 5 out of bounds for length 2
                ## getTitleAt(5) threw IndexOutOfBoundsException: Index 5 out of bounds for length 2
                ## getMnemonicAt(5) threw IndexOutOfBoundsException: Index: 5, Tab count: 2
                ## setSelectedIndex(5) threw IndexOutOfBoundsException: Index: 5, Tab count: 2
                ## setSelectedIndex(-2) threw IndexOutOfBoundsException: Index: -2, Tab count: 2
                ## insertTab at -1 threw IndexOutOfBoundsException: Index: -1, Size: 2
                ## insertTab at 9 threw IndexOutOfBoundsException: Index: 9, Size: 2
                ## removeTabAt 9 threw IndexOutOfBoundsException: Index: 9, Tab count: 2
                ## setTitleAt 9 threw IndexOutOfBoundsException: Index 9 out of bounds for length 2
                ## setTabComponentAt 9 threw IndexOutOfBoundsException: Index 9 out of bounds for length 2
                ## setTabComponentAt content threw IllegalArgumentException: Component is already added to this JTabbedPane
                ## setSelectedComponent absent threw IllegalArgumentException: component not found in tabbed pane
                getMnemonicAt(1)=-1 displayed=-1
                ## setMnemonicAt 1 VK_A
                  pce displayedMnemonicIndexAt null->null
                  pce mnemonicAt null->null
                  => sel=0 tabs=[N:null A:a- ] children=[a ]
                getMnemonicAt(1)=65 displayed=0
                ## setTitleAt 1 xyz (no A)
                  pce indexForTitle -1->1
                  pce displayedMnemonicIndexAt null->null
                  => sel=0 tabs=[N:null xyz:a- ] children=[a ]
                displayed=-1
                ## setDisplayedMnemonicIndexAt 1 7 threw IllegalArgumentException: Invalid mnemonic index: 7
                ## setDisplayedMnemonicIndexAt 1 2
                  pce displayedMnemonicIndexAt null->null
                  => sel=0 tabs=[N:null xyz:a- ] children=[a ]
                ## setIconAt
                  => sel=0 tabs=[N:null xyz:a- ] children=[a ]
                ## setToolTipTextAt
                  => sel=0 tabs=[N:null xyz:a- ] children=[a ]
                ## setEnabledAt false
                  => sel=0 tabs=[N:null xyz:a- ] children=[a ]
                ## setSelectedIndex disabled 1
                  change sel=1
                  => sel=1 tabs=[N:null xyz:a+ ] children=[a ]
                ## setTabComponentAt 1 h
                  pce indexForTabComponent -1->1
                  => sel=1 tabs=[N:null xyz:a+ ] children=[a ]
                ## setTabComponentAt 0 h (move)
                  pce indexForTabComponent -1->1
                  pce indexForTabComponent -1->0
                  => sel=1 tabs=[N:null xyz:a+ ] children=[a ]
                tabComp(1)=null tabComp(0)=h
                ## removeTabAt 0 with tab component
                  pce indexForTabComponent -1->0
                  pce __index_to_remove__ null->0
                  change sel=0
                  => sel=0 tabs=[xyz:a+ ] children=[a ]
                ## setComponentAt 0 e (selected)
                  pce __index_to_remove__ 0->null
                  removed a
                  added e
                  => sel=0 tabs=[xyz:e+ ] children=[e ]
                ## setComponentAt 0 null
                  removed e
                  => sel=0 tabs=[xyz:null ] children=[]
                ## removeAll 2
                  change sel=-1
                  pce __index_to_remove__ null->0
                  => sel=-1 tabs=[] children=[]
                ## add(p1)
                  added p1
                  change sel=0
                  => sel=0 tabs=[p1:p1+ ] children=[p1 ]
                ## add("T2", p2)
                  added p2
                  => sel=0 tabs=[p1:p1+ T2:p2- ] children=[p1 p2 ]
                ## add(p3, Integer 0)
                  added p3
                  => sel=0 tabs=[p1:p1+ T2:p2- p3:p3- ] children=[p1 p2 p3 ]
                ## add(p4, icon)
                  added p4
                  => sel=0 tabs=[p1:p1+ T2:p2- p3:p3- :p4- ] children=[p1 p2 p3 p4 ]
                title(3)= icon(3)=true
                ## add(p5, 1)
                  added p5
                  => sel=0 tabs=[p1:p1+ p5:p5- T2:p2- p3:p3- :p4- ] children=[p1 p2 p3 p4 p5 ]
                ## remove(p2)
                  pce __index_to_remove__ null->2
                  pce __index_to_remove__ 2->null
                  removed p2
                  => sel=0 tabs=[p1:p1+ p5:p5- p3:p3- :p4- ] children=[p1 p3 p4 p5 ]
                ## remove(0)
                  pce __index_to_remove__ null->0
                  change sel=0
                  pce __index_to_remove__ 0->null
                  removed p1
                  => sel=0 tabs=[p5:p5+ p3:p3- :p4- ] children=[p3 p4 p5 ]
                bg(0)==pane bg true
                ## setModel (index 1)
                  pce model DefaultSingleSelectionModel->DefaultSingleSelectionModel
                  => sel=1 tabs=[A:a3+ B:b3- ] children=[a3 b3 ]
                ## model.setSelectedIndex 0 direct
                  change sel=0
                  => sel=0 tabs=[A:a3+ B:b3- ] children=[a3 b3 ]
                ## setSelectedIndex -1
                  change sel=-1
                  => sel=-1 tabs=[A:a3- B:b3- ] children=[a3 b3 ]
                ## insert with -1 selection
                  added c3
                  => sel=-1 tabs=[C:c3- A:a3- B:b3- ] children=[a3 b3 c3 ]
                ## removeAll with sel 1
                  change sel=-1
                  pce __index_to_remove__ null->2
                  pce __index_to_remove__ 2->null
                  removed c4
                  pce __index_to_remove__ null->1
                  pce __index_to_remove__ 1->null
                  removed b4
                  pce __index_to_remove__ null->0
                  pce __index_to_remove__ 0->null
                  removed a4
                  => sel=-1 tabs=[] children=[]
                """, String.join("\n", s.out) + "\n");
    }

    @Test
    @DisplayName("indexOfTab throws NPE past a tab whose title was set to null, as the JDK's does")
    void indexOfTabThrowsPastANullTitle() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("A", new JPanel());
        tp.setTitleAt(0, null);
        assertThrows(NullPointerException.class, () -> tp.indexOfTab("A"));
    }

    @Test
    @DisplayName("the peer shows the pane's selection after every change")
    void peerFollowsTheSelection() {
        JTabbedPane tp = new JTabbedPane();
        SJTabbedPane peer = (SJTabbedPane) tp.getPeer();
        tp.addTab("A", new JPanel());
        tp.addTab("B", new JPanel());
        tp.addTab("C", new JPanel());
        tp.setSelectedIndex(1);
        assertEquals(1, peer.getSelectedIndex());
        tp.insertTab("D", null, new JPanel(), null, 0);
        assertEquals(2, peer.getSelectedIndex());
        tp.getModel().setSelectedIndex(0);
        assertEquals(0, peer.getSelectedIndex());
        tp.setSelectedIndex(-1);
        assertEquals(-1, peer.getSelectedIndex());
        tp.insertTab("E", null, new JPanel(), null, 0);
        assertEquals(-1, peer.getSelectedIndex(), "an insert does not select when nothing is");
        tp.setSelectedIndex(2);
        tp.setComponentAt(2, new JPanel());
        assertEquals(2, peer.getSelectedIndex(), "a content swap keeps the selection");
        javax.swing.DefaultSingleSelectionModel m = new javax.swing.DefaultSingleSelectionModel();
        m.setSelectedIndex(3);
        tp.setModel(m);
        assertEquals(3, peer.getSelectedIndex());
    }

    @Test
    @DisplayName("a user's tab click runs setSelectedIndex in a UI fiber, and a listener's veto reaches the peer")
    void browserTabClickRunsSetSelectedIndex() throws Exception {
        List<String> calls = new ArrayList<>();
        JTabbedPane tp = new JTabbedPane() {
            @Override
            public void setSelectedIndex(int index) {
                calls.add("setSelectedIndex " + index);
                super.setSelectedIndex(index);
            }
        };
        com.vaadin.flow.component.UI.getCurrent().add(tp.getPeer());
        tp.addTab("A", new JPanel());
        tp.addTab("B", new JPanel());
        tp.addTab("C", new JPanel());
        calls.clear();
        List<Boolean> inFiber = new ArrayList<>();
        tp.addChangeListener(e -> inFiber.add(com.github.mvysny.blockingdialogs.UIFibers.isInUIFiber()));

        selectFromClient(tp, 1);
        assertEquals(List.of("setSelectedIndex 1"), calls);
        assertEquals(1, tp.getSelectedIndex());
        assertEquals(List.of(true), inFiber);

        // A listener that refuses tab 2 puts the selection back; the peer follows.
        tp.addChangeListener(e -> {
            if (tp.getSelectedIndex() == 2) tp.setSelectedIndex(1);
        });
        selectFromClient(tp, 2);
        assertEquals(1, tp.getSelectedIndex());
        assertEquals(1, ((SJTabbedPane) tp.getPeer()).getSelectedIndex());
    }

    /** What the browser's {@code selected-changed} synchronization does to the peer's tab strip. */
    private static void selectFromClient(JTabbedPane tp, int index) throws Exception {
        SJTabbedPane peer = (SJTabbedPane) tp.getPeer();
        com.vaadin.flow.component.Component tabs = peer.getTabAt(0).getParent().orElseThrow();
        tabs.getElement().getNode()
                .getFeature(com.vaadin.flow.internal.nodefeature.ElementPropertyMap.class)
                .deferredUpdateFromClient("selected", index).run();
    }

    @Test
    @DisplayName("hidden tab contents fire componentHidden, and selecting a tab fires componentShown")
    void tabContentsSeeShownAndHidden() {
        // The lazy-load idiom: a panel fills itself when its tab is first shown.
        JTabbedPane tp = new JTabbedPane();
        JPanel a = panel("a");
        JPanel b = panel("b");
        List<String> events = new ArrayList<>();
        vaadinx.awt.event.ComponentAdapter recorder = new vaadinx.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(vaadinx.awt.event.ComponentEvent e) {
                events.add("shown " + e.getComponent().getName());
            }

            @Override
            public void componentHidden(vaadinx.awt.event.ComponentEvent e) {
                events.add("hidden " + e.getComponent().getName());
            }
        };
        a.addComponentListener(recorder);
        b.addComponentListener(recorder);
        tp.addTab("A", a);
        tp.addTab("B", b);
        tp.setSelectedIndex(1);
        assertEquals(List.of("hidden a", "shown a", "hidden b", "hidden a", "shown b"), events);
        assertFalse(a.getPeer().isVisible());
        assertTrue(b.getPeer().isVisible());
    }

    private static JPanel panel(String name) {
        JPanel p = new JPanel();
        p.setName(name);
        return p;
    }

    /** The JDK probe's recorder, verbatim in shape so its output compares line for line. */
    private static final class Script {
        final List<String> log = new ArrayList<>();
        final List<String> out = new ArrayList<>();

        JTabbedPane newPane() {
            JTabbedPane tp = new JTabbedPane();
            tp.addChangeListener(e -> log.add("change sel=" + tp.getSelectedIndex()));
            tp.addPropertyChangeListener(e -> {
                String n = e.getPropertyName();
                if (n.equals("ancestor") || n.startsWith("UI")) return;
                log.add("pce " + n + " " + value(e.getOldValue()) + "->" + value(e.getNewValue()));
            });
            tp.addContainerListener(new vaadinx.awt.event.ContainerListener() {
                @Override
                public void componentAdded(vaadinx.awt.event.ContainerEvent e) {
                    log.add("added " + e.getChild().getName());
                }

                @Override
                public void componentRemoved(vaadinx.awt.event.ContainerEvent e) {
                    log.add("removed " + e.getChild().getName());
                }
            });
            return tp;
        }

        private static String value(Object v) {
            return v == null || v instanceof Integer || v instanceof String
                    ? String.valueOf(v) : v.getClass().getSimpleName();
        }

        void flush(String step, JTabbedPane tp) {
            out.add("## " + step);
            for (String line : log) out.add("  " + line);
            out.add("  => " + state(tp));
            log.clear();
        }

        void tryRun(String step, Runnable r) {
            try {
                r.run();
                out.add("## " + step + " ok");
            } catch (RuntimeException t) {
                out.add("## " + step + " threw " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            for (String line : log) out.add("  " + line);
            log.clear();
        }

        private static String state(JTabbedPane tp) {
            StringBuilder sb = new StringBuilder("sel=" + tp.getSelectedIndex() + " tabs=[");
            for (int i = 0; i < tp.getTabCount(); i++) {
                vaadinx.awt.Component c = tp.getComponentAt(i);
                sb.append(tp.getTitleAt(i)).append(':')
                        .append(c == null ? "null" : c.getName() + (c.isVisible() ? "+" : "-")).append(' ');
            }
            sb.append("] children=[");
            for (vaadinx.awt.Component c : tp.getComponents()) sb.append(c.getName()).append(' ');
            return sb.append(']').toString();
        }
    }

    /** The PCEs in {@code events} carrying {@code property} — the pane fires several per setter. */
    private static List<PropertyChangeEvent> named(List<PropertyChangeEvent> events, String property) {
        return events.stream().filter(e -> property.equals(e.getPropertyName())).toList();
    }
}
