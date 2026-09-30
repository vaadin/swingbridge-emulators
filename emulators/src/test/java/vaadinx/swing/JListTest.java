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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJList;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ListSelectionEvent;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Emulator tests for {@code JList} over
 * {@code com.vaadin.swingbridge.surrogates.SJList} (SD_sjlist + D_jlist). The surrogate carries the
 * Grid / model / selection / double-click plumbing — that coverage lives
 * in {@code com.vaadin.swingbridge.surrogates.SJListTest}. These focus on the emulator's job:
 * JDK-shape delegation, two-layer event re-sourcing to the emulator
 * (ListSelectionListener + MouseListener), the JDK ListCellRenderer
 * bridge, and R_leaf_peer_lockdown lock-down.
 */
class JListTest extends AbstractKaribuTest {

    @SuppressWarnings("unchecked")
    private static <E> SJList<E> peerOf(JList<E> list) {
        return (SJList<E>) list.getPeer();
    }

    private static <E> SJList<E> attachPeer(JList<E> list) {
        SJList<E> p = peerOf(list);
        UI.getCurrent().add(p);
        return p;
    }

    /** One recorded mouse click: how many clicks, and what row the list resolved. */
    private record Click(int clickCount, int rowAtOrigin) {
    }

    /** The rendered text of the list's cell at {@code index}. */
    private static String cellText(SJList<String> peer, int index) {
        return ((Span) GridKt._getCellComponent(peer, index, "value")).getText();
    }

    // --- Peer + R_leaf_peer_lockdown ----------------------------------------------------

    @Test
    @DisplayName("peer is SJList per surrogate-first thin-down")
    void peerIsSJList() {
        assertInstanceOf(SJList.class, new JList<String>().getPeer());
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected ctor on the JDK leaf")
    void noProtectedCtorOnTheLeaf() {
        List<Constructor<?>> protectedCtors = Arrays.stream(JList.class.getDeclaredConstructors())
                .filter(ctor -> Modifier.isProtected(ctor.getModifiers()))
                .toList();
        assertTrue(protectedCtors.isEmpty(),
                "R_leaf_peer_lockdown: no protected ctor allowed on JDK-leaf JList; got: "
                        + protectedCtors);
    }

    // --- Ctors + model -------------------------------------------------

    @Test
    @DisplayName("array ctor seeds the model")
    void arrayCtorSeedsTheModel() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        assertEquals(3, list.getModel().getSize());
        assertEquals("b", list.getModel().getElementAt(1));
    }

    @Test
    @DisplayName("setListData replaces the model")
    void setListDataReplacesTheModel() {
        JList<String> list = new JList<>(new String[] {"a"});
        list.setListData(new String[] {"x", "y"});
        assertEquals(2, list.getModel().getSize());
    }

    @Test
    @DisplayName("setModel fires model PCE")
    void setModelFiresPce() {
        JList<String> list = new JList<>(new String[] {"a"});
        List<PropertyChangeEvent> seen = new ArrayList<>();
        list.addPropertyChangeListener("model", seen::add);
        DefaultListModel<String> m = new DefaultListModel<>();
        m.addElement("z");
        list.setModel(m);
        assertSame(m, assertSingle(seen).getNewValue());
    }

    // --- Selection delegation ------------------------------------------

    @Test
    @DisplayName("selection accessors delegate to the surrogate")
    void selectionAccessorsDelegate() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c", "d"});
        attachPeer(list);
        list.setSelectedIndices(new int[] {1, 3});
        assertEquals(Set.of(1, 3),
                Arrays.stream(list.getSelectedIndices()).boxed().collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of("b", "d"), list.getSelectedValuesList());
        list.setSelectedIndex(2);
        assertEquals("c", list.getSelectedValue());
    }

    @Test
    @DisplayName("setSelectionMode round-trips and fires no bound property")
    void setSelectionModeFiresNoPce() {
        // JList.setSelectionMode is one line — getSelectionModel()
        // .setSelectionMode(mode) — and the notification is the selection
        // model's ListSelectionEvent, not a PropertyChangeEvent (D_property_fanout_audit).
        JList<String> list = new JList<>(new String[] {"a", "b"});
        List<PropertyChangeEvent> seen = new ArrayList<>();
        list.addPropertyChangeListener("selectionMode", seen::add);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertEquals(ListSelectionModel.SINGLE_SELECTION, list.getSelectionMode());
        assertEquals(0, seen.size());
    }

    // --- Two-layer ListSelectionListener re-source ----------------------

    @Test
    @DisplayName("addListSelectionListener fires with source rebound to the emulator")
    void selectionListenerSourceIsTheEmulator() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        attachPeer(list);
        List<ListSelectionEvent> events = new ArrayList<>();
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                events.add(e);
            }
        });
        list.setSelectedIndex(1);
        assertFalse(events.isEmpty(), "selection change must reach the emulator's listener");
        assertSame(list, events.get(events.size() - 1).getSource(),
                "source must be rebound to the emulator JList");
    }

    @Test
    @DisplayName("setSelectionModel moves the re-source handler")
    void setSelectionModelMovesTheHandler() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        attachPeer(list);
        List<ListSelectionEvent> events = new ArrayList<>();
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                events.add(e);
            }
        });
        list.setSelectionModel(new DefaultListSelectionModel());
        list.setSelectedIndex(2);
        assertFalse(events.isEmpty(),
                "after model swap the handler must still fire on the new model");
        assertSame(list, events.get(events.size() - 1).getSource());
    }

    // --- ListCellRenderer bridge ---------------------------------------

    @Test
    @DisplayName("default cell renderer is DefaultListCellRenderer and renders the value")
    void defaultCellRendererRendersTheValue() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        SJList<String> peer = attachPeer(list);
        assertInstanceOf(DefaultListCellRenderer.class, list.getCellRenderer());
        assertEquals("a", cellText(peer, 0));
        assertEquals("c", cellText(peer, 2));
    }

    @Test
    @DisplayName("custom cell renderer drives the rendered cell (snapshot bridge)")
    void customCellRendererDrivesTheCell() {
        JList<String> list = new JList<>(new String[] {"a", "b"});
        SJList<String> peer = attachPeer(list);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public vaadinx.awt.Component getListCellRendererComponent(
                    JList<?> l, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                setText(value == null ? "" : value.toString().toUpperCase(Locale.ROOT));
                return this;
            }
        });
        assertEquals("A", cellText(peer, 0));
        assertEquals("B", cellText(peer, 1));
    }

    // --- Two-layer MouseListener re-source (double-click idiom) ---------

    @Test
    @DisplayName("double-click re-sources to a MouseEvent with clickCount 2 and locationToIndex")
    void doubleClickReSourcesToTheEmulator() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        SJList<String> peer = attachPeer(list);
        List<Click> seen = new ArrayList<>();
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                seen.add(new Click(e.getClickCount(), list.locationToIndex(new Point(0, 0))));
                assertSame(list, e.getSource(), "MouseEvent source must be the emulator JList");
            }
        });
        GridKt._doubleClickItem(peer, 1);
        assertEquals(List.of(new Click(2, 1)), seen);
    }

    @Test
    @DisplayName("locationToIndex still answers the clicked row after the handler parks on a modal dialog")
    void locationToIndexSurvivesAParkedHandler() throws InterruptedException {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        SJList<String> peer = attachPeer(list);
        List<Integer> seen = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(null, "Open?");
                seen.add(list.locationToIndex(e.getPoint()));
                finished.countDown();
            }
        });

        GridKt._clickItem(peer, 1);
        Button ok = assertSingle(LocatorJ._find(Button.class, spec -> spec.withText("OK")));
        EHelper.callSwing(() -> LocatorJ._click(ok));

        assertTrue(finished.await(5, TimeUnit.SECONDS), "the handler did not resume");
        assertEquals(List.of(1), seen);
    }

    /**
     * {@code BasicListUI.locationToIndex}, measured on JDK 25: a null location throws, and
     * the answer is the closest cell, so a point past the last row is the last row and an
     * empty list answers {@code -1}.
     */
    @Test
    @DisplayName("locationToIndex answers the closest cell and throws on null, as BasicListUI's")
    void locationToIndexIsBasicListUIs() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addAll(List.of("a", "b", "c"));
        JList<String> list = new JList<>(model);
        SJList<String> peer = attachPeer(list);
        List<Integer> seen = new ArrayList<>();
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                seen.add(list.locationToIndex(e.getPoint()));
                model.removeRange(1, 2);
                seen.add(list.locationToIndex(e.getPoint()));
                model.clear();
                seen.add(list.locationToIndex(e.getPoint()));
            }
        });

        GridKt._clickItem(peer, 2);

        assertEquals(List.of(2, 0, -1), seen);
        assertEquals(-1, list.locationToIndex(new Point(0, 0)), "outside a click dispatch");
        assertThrows(NullPointerException.class, () -> list.locationToIndex(null));
    }

    @Test
    @DisplayName("single-click re-sources with clickCount 1")
    void singleClickReSourcesWithCountOne() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        SJList<String> peer = attachPeer(list);
        List<Integer> counts = new ArrayList<>();
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                counts.add(e.getClickCount());
            }
        });
        GridKt._clickItem(peer, 2);
        assertEquals(List.of(1), counts);
    }

    // --- Layout / sizing delegation ------------------------------------

    @Test
    @DisplayName("no-counterpart knobs round-trip on the emulator (R_swing_is_truth field shadow) with PCE")
    void noCounterpartKnobsRoundTrip() {
        // The surrogate drops these (R_vaadin_first); the emulator owns JDK-faithful
        // round-trip. See SJListTest for the surrogate-drop assertions.
        JList<String> list = new JList<>(new String[] {"a"});
        List<String> seen = new ArrayList<>();
        list.addPropertyChangeListener(e -> seen.add(e.getPropertyName()));

        list.setVisibleRowCount(10);
        list.setFixedCellWidth(120);
        list.setFixedCellHeight(22);
        list.setSelectionForeground(Color.BLUE);
        list.setSelectionBackground(Color.YELLOW);

        assertEquals(10, list.getVisibleRowCount());
        assertEquals(120, list.getFixedCellWidth());
        assertEquals(22, list.getFixedCellHeight());
        assertEquals(Color.BLUE, list.getSelectionForeground());
        assertEquals(Color.YELLOW, list.getSelectionBackground());
        assertTrue(seen.containsAll(List.of(
                "visibleRowCount", "fixedCellWidth", "fixedCellHeight",
                "selectionForeground", "selectionBackground")));

        // layoutOrientation round-trips too; VERTICAL is default so set a
        // value that fires PCE only if changed.
        assertEquals(JList.VERTICAL, list.getLayoutOrientation());
    }

    // --- Scrollable interface ------------------------------------------

    @Test
    @DisplayName("Scrollable methods return sane values")
    void scrollableMethodsAreSane() {
        JList<String> list = new JList<>(new String[] {"a", "b"});
        list.setFixedCellHeight(20);
        list.setVisibleRowCount(4);
        Dimension pref = list.getPreferredScrollableViewportSize();
        assertEquals(20 * 4, pref.height);
        assertEquals(20, list.getScrollableUnitIncrement(
                new Rectangle(0, 0, 100, 100), SwingConstants.VERTICAL, 1));
    }

    @Test
    @DisplayName("getUIClassID is ListUI")
    void uiClassIdIsListUI() {
        assertEquals("ListUI", new JList<String>().getUIClassID());
    }

    // --- The JDK's own bodies, measured (D_emulator_owned_state) ---

    @Test
    @DisplayName("selection, model and listener events match the JDK's script")
    void selectionMatchesTheJdk() {
        // Replays a headless JDK 25 script; every expected line below is what it printed.
        JList<String> l = new JList<>(new String[] {"a", "b", "c", "b"});
        List<String> log = new ArrayList<>();
        l.addPropertyChangeListener(e -> log.add("PCE " + e.getPropertyName()));
        l.addListSelectionListener(e -> log.add("LSE " + e.getFirstIndex() + "-" + e.getLastIndex()
                + " adj=" + e.getValueIsAdjusting() + " src=" + (e.getSource() == l)));
        l.setSelectedIndex(1);
        log.add("sel=" + l.getSelectedIndex() + " val=" + l.getSelectedValue());
        l.setSelectedIndex(-1);
        log.add("after setSelectedIndex(-1) sel=" + l.getSelectedIndex());
        l.setSelectedIndex(9);
        log.add("after setSelectedIndex(9) sel=" + l.getSelectedIndex());
        l.setSelectedValue("zz", false);
        log.add("after setSelectedValue(zz) sel=" + l.getSelectedIndex());
        l.setSelectedValue("b", false);
        log.add("after setSelectedValue(b) sel=" + l.getSelectedIndex());
        l.setSelectedValue("b", false);
        log.add("again b sel=" + l.getSelectedIndex());
        l.setSelectedIndices(new int[] {3, -1, 0, 7});
        log.add("indices=" + Arrays.toString(l.getSelectedIndices()) + " values=" + l.getSelectedValuesList()
                + " min=" + l.getMinSelectionIndex() + " max=" + l.getMaxSelectionIndex()
                + " anchor=" + l.getAnchorSelectionIndex() + " lead=" + l.getLeadSelectionIndex());
        l.setModel(new DefaultListModel<>());
        log.add("after setModel sel=" + Arrays.toString(l.getSelectedIndices()));
        l.setListData(new String[] {"x", "y"});
        log.add("after setListData size=" + l.getModel().getSize());
        l.setSelectedIndex(1);
        l.setListData(new java.util.Vector<>(List.of("p")));
        log.add("after setListData(Vector) sel=" + l.getSelectedIndex());
        l.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        log.add("mode=" + l.getSelectionMode());
        DefaultListSelectionModel sm = new DefaultListSelectionModel();
        l.setSelectionModel(sm);
        sm.setSelectionInterval(0, 0);
        log.add("after new sm sel=" + l.getSelectedIndex());

        assertEquals(List.of(
                "LSE 1-1 adj=false src=true",
                "sel=1 val=b",
                "after setSelectedIndex(-1) sel=1",
                "after setSelectedIndex(9) sel=1",
                "after setSelectedValue(zz) sel=1",
                "after setSelectedValue(b) sel=1",
                "again b sel=1",
                "LSE 1-1 adj=false src=true",
                "LSE 1-3 adj=false src=true",
                "LSE 0-3 adj=false src=true",
                "indices=[0, 3] values=[a, b] min=0 max=3 anchor=0 lead=0",
                "PCE model",
                "LSE 0-3 adj=false src=true",
                "after setModel sel=[]",
                "PCE model",
                "after setListData size=2",
                "LSE 0-1 adj=false src=true",
                "PCE model",
                "LSE 1-1 adj=false src=true",
                "after setListData(Vector) sel=-1",
                "mode=0",
                "PCE selectionModel",
                "LSE 0-0 adj=false src=true",
                "after new sm sel=0"), log);
    }

    @Test
    @DisplayName("null models throw the JDK's IllegalArgumentException, and the no-arg model is the JDK's")
    void nullAndDefaultModelsMatchTheJdk() {
        assertEquals("dataModel must be non null", assertThrows(IllegalArgumentException.class,
                () -> new JList<String>((javax.swing.ListModel<String>) null)).getMessage());
        JList<String> l = new JList<>();
        assertEquals("model must be non null",
                assertThrows(IllegalArgumentException.class, () -> l.setModel(null)).getMessage());
        assertEquals("selectionModel must be non null",
                assertThrows(IllegalArgumentException.class, () -> l.setSelectionModel(null)).getMessage());
        assertEquals("invalid selectionMode",
                assertThrows(IllegalArgumentException.class, () -> l.setSelectionMode(7)).getMessage());
        // Not a DefaultListModel: a migrated cast to one fails here as it does on the desktop.
        assertInstanceOf(AbstractListModel.class, l.getModel());
        assertFalse(l.getModel() instanceof DefaultListModel);
        assertEquals("No Data Model", assertThrows(IndexOutOfBoundsException.class,
                () -> l.getModel().getElementAt(0)).getMessage());
        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, l.getSelectionMode());
    }

    @Test
    @DisplayName("createSelectionModel is the JDK's hook, and its model drives the Grid")
    void createSelectionModelIsReached() {
        DefaultListSelectionModel custom = new DefaultListSelectionModel();
        JList<String> l = new JList<>(new String[] {"a", "b"}) {
            @Override
            protected ListSelectionModel createSelectionModel() {
                return custom;
            }
        };
        assertSame(custom, l.getSelectionModel());
        SJList<String> peer = attachPeer(l);
        assertSame(custom, peer.getListSelectionModel());
        l.setSelectedIndex(1);
        assertEquals(Set.of(1), peer.getSelectedItems());
    }

    @Test
    @DisplayName("the selection handler registers on the first addListSelectionListener, as the JDK's does")
    void selectionHandlerIsLazy() {
        // Measured on JDK 25: [direct-after, list, direct-before].
        JList<String> l = new JList<>(new String[] {"a", "b"});
        List<String> order = new ArrayList<>();
        l.getSelectionModel().addListSelectionListener(e -> order.add("direct-before"));
        l.addListSelectionListener(e -> order.add("list"));
        l.getSelectionModel().addListSelectionListener(e -> order.add("direct-after"));
        l.setSelectedIndex(0);
        assertEquals(List.of("direct-after", "list", "direct-before"), order);
    }

    @Test
    @DisplayName("inserting and removing rows shifts the selection, as the JDK's list UI does")
    void modelMutationShiftsTheSelection() {
        // Measured on JDK 25: the shift fires "LSE 1-2" both times.
        DefaultListModel<String> dm = new DefaultListModel<>();
        dm.addElement("a");
        dm.addElement("b");
        dm.addElement("c");
        JList<String> s = new JList<>(dm);
        attachPeer(s);
        s.setSelectedIndex(1);
        List<String> log = new ArrayList<>();
        s.addListSelectionListener(e -> log.add("LSE " + e.getFirstIndex() + "-" + e.getLastIndex()));
        dm.add(0, "z");
        log.add("sel=" + s.getSelectedIndex());
        dm.remove(2);
        log.add("sel=" + s.getSelectedIndex());
        assertEquals(List.of("LSE 1-2", "sel=2", "LSE 1-2", "sel=-1"), log);
    }

    @Test
    @DisplayName("a browser selection reaches the list's listener inside a UI fiber, so it can open a modal dialog")
    void browserSelectionRunsInAUiFiber() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c"});
        SJList<String> peer = attachPeer(list);
        List<Boolean> inFiber = new ArrayList<>();
        list.addListSelectionListener(e -> inFiber.add(com.github.mvysny.blockingdialogs.UIFibers.isInUIFiber()));
        // The Grid's selection listener, which a browser selection fires too.
        peer.select(1);
        assertEquals(1, list.getSelectedIndex());
        assertFalse(inFiber.isEmpty());
        assertTrue(inFiber.stream().allMatch(b -> b), inFiber.toString());
    }
}
