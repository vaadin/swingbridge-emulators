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

package vaadinx.awt;

import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SList;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for the {@code vaadinx.awt.List} emulator — the AWT 1.0 scrolling list
 * box, not {@code vaadinx.swing.JList}. Beyond the API surface, three things this
 * class is the only place to check: R_no_vaadin_in_api limb 2's call directions (nine JDK
 * hooks whose delegation runs modern→deprecated on purpose), the {@code rows} /
 * {@code visibleIndex} shadows the peer cannot answer, and that a browser event is
 * re-sourced to the emulator. See D_awt_list.
 *
 * <p>Note what this package's own {@code List} type does to the imports: it shadows
 * {@code java.util.List}, so this file deliberately imports neither and spells
 * collection types out where it needs them. {@code List} unqualified is the emulator.
 *
 * <p>Deprecation is suppressed file-wide rather than per test: nine of AWT 1.0's
 * names are the *implementations* here, and pinning that is most of the suite.
 */
@SuppressWarnings("deprecation")
class ListTest extends AbstractKaribuTest {

    private static final String SELECTED_INDICES =
            "[...event.target.selectedOptions].map(o=>o.index).join(',')";

    /** Rendered and attached, so the peer's element is live. */
    private List shown(String... items) {
        return shown(4, false, items);
    }

    private List shown(int rows, boolean multiple, String... items) {
        List l = new List(rows, multiple);
        for (String item : items) {
            l.add(item);
        }
        JFrame frame = new JFrame();
        frame.add(l);
        frame.setVisible(true);
        return l;
    }

    private static Element peerElement(List l) {
        return ((SList) l.getPeer()).getElement();
    }

    /** Fires the {@code change} the browser would, carrying the joined indices. */
    private static void dispatchChange(List l, int... indices) {
        var data = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        data.put(SELECTED_INDICES, IntStream.of(indices)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining(",")));
        Element e = peerElement(l);
        e.getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(e, "change", data));
    }

    private static void dispatchDoubleClick(List l, int index) {
        var data = tools.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        data.put("event.target.index", index);
        Element e = peerElement(l);
        e.getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(e, "dblclick", data));
    }

    // --- ctors, peer, shadows ------------------------------------------

    @Test
    @DisplayName("the ctor chain and the rows substitution match the JDK")
    void theCtorChainAndTheRowsSubstitutionMatchTheJdk() {
        assertEquals(4, new List().getRows(), "List() is List(0, false), and 0 means four");
        assertEquals(4, new List(0).getRows());
        assertEquals(7, new List(7).getRows());
        // No validation, and no setter — a negative row count survives verbatim.
        assertEquals(-3, new List(-3).getRows());
        assertFalse(new List().isMultipleMode());
        assertTrue(new List(4, true).isMultipleMode());
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down - every instance peers over an SList")
    void rLeafPeerLockdownLockDownEveryInstancePeersOverAnSList() {
        assertInstanceOf(SList.class, new List().getPeer());
        // Including through a user-code subclass: there is no protected
        // (Component peer) ctor to swap the peer through.
        class MyList extends List {
            MyList() {
                super(6, true);
            }
        }
        assertInstanceOf(SList.class, new MyList().getPeer());
        assertEquals("6", ((SList) new MyList().getPeer()).getElement().getAttribute("size"));
    }

    @Test
    @DisplayName("getRows is the JDK value while the peer's size is floored at 2")
    void getRowsIsTheJdkValueWhileThePeersSizeIsFlooredAt2() {
        // The floor is a rendering concern only; it must not leak into the API.
        List l = new List(1);
        assertEquals(1, l.getRows());
        assertEquals("2", ((SList) l.getPeer()).getElement().getAttribute("size"));
    }

    @Test
    @DisplayName("visibleIndex is a shadow that records even an out-of-range makeVisible")
    void visibleIndexIsAShadowThatRecordsEvenAnOutOfRangeMakeVisible() {
        List l = shown("a", "b");
        assertEquals(-1, l.getVisibleIndex());
        l.makeVisible(1);
        assertEquals(1, l.getVisibleIndex());
        l.makeVisible(99);
        assertEquals(99, l.getVisibleIndex());
    }

    // --- R_no_vaadin_in_api limb 2: the deprecated names are the implementations --------

    @Test
    @DisplayName("each deprecated alias reaches the same body as its modern name")
    void eachDeprecatedAliasReachesTheSameBodyAsItsModernName() {
        // Drive one name, assert the other's effect — the check no WARN
        // inventory can make, because both compile either way round.
        List l = shown("a", "b", "c");
        assertEquals(3, l.countItems());
        assertEquals(l.getItemCount(), l.countItems());

        l.addItem("d");                       // deprecated → same body as add
        assertEquals(4, l.getItemCount());
        l.addItem("head", 0);
        assertEquals("head", l.getItem(0));

        l.delItem(0);                         // deprecated → same body as remove(int)
        assertEquals("a", l.getItem(0));
        l.delItems(0, 1);
        assertEquals(2, l.getItemCount());

        l.clear();                            // deprecated → same body as removeAll
        assertEquals(0, l.getItemCount());

        List m = shown(4, true, "x", "y");
        m.setMultipleSelections(false);       // deprecated → same body as setMultipleMode
        assertFalse(m.isMultipleMode());
        assertFalse(m.allowsMultipleSelections());
        m.select(0);
        assertTrue(m.isSelected(0));
        assertEquals(m.isIndexSelected(0), m.isSelected(0));
    }

    @Test
    @DisplayName("a subclass override of a deprecated name intercepts the modern one")
    void aSubclassOverrideOfADeprecatedNameInterceptsTheModernOne() {
        // The failure mode R_no_vaadin_in_api limb 2 exists to prevent: an override that
        // compiles, reads as wired, and never runs.
        java.util.List<String> reached = new ArrayList<>();
        List l = new List(4, false) {
            @Override
            public int countItems() {
                reached.add("countItems");
                return super.countItems();
            }

            @Override
            public void clear() {
                reached.add("clear");
                super.clear();
            }

            @Override
            public void delItems(int start, int end) {
                reached.add("delItems");
                super.delItems(start, end);
            }

            @Override
            public void addItem(String item, int index) {
                reached.add("addItem");
                super.addItem(item, index);
            }
        };
        l.add("a");            // → addItem(String) → addItem(String, int)
        l.getItemCount();      // → countItems()
        l.remove(0);           // → delItem(int) → delItems(int, int)
        l.removeAll();         // → clear()
        assertEquals(java.util.List.of("addItem", "countItems", "delItems", "clear"), reached);
    }

    @Test
    @DisplayName("getSelectedItems is composed from getSelectedIndexes and getItem")
    void getSelectedItemsIsComposedFromGetSelectedIndexesAndGetItem() {
        java.util.List<String> reached = new ArrayList<>();
        List l = new List(4, true) {
            @Override
            public String getItem(int index) {
                reached.add("getItem");
                return super.getItem(index);
            }
        };
        JFrame frame = new JFrame();
        frame.add(l);
        frame.setVisible(true);
        l.add("a");
        l.add("b");
        l.select(0);
        l.select(1);
        assertArrayEquals(new String[]{"a", "b"}, l.getSelectedItems());
        assertEquals(2, reached.size(), "one getItem per selected index, as in the JDK");
        // getSelectedObjects delegates to getSelectedItems, same array shape.
        assertArrayEquals(new Object[]{"a", "b"}, l.getSelectedObjects());
    }

    @Test
    @DisplayName("the size chain honours the JDK's rows guard")
    void theSizeChainHonoursTheJdksRowsGuard() {
        java.util.List<String> reached = new ArrayList<>();
        List l = new List(5, false) {
            @Override
            public Dimension preferredSize(int rows) {
                reached.add("preferredSize(int)");
                return super.preferredSize(rows);
            }
        };
        l.getPreferredSize();
        assertEquals(java.util.List.of("preferredSize(int)"), reached, "rows > 0 reaches the (int) form");

        reached.clear();
        List negative = new List(-3, false) {
            @Override
            public Dimension preferredSize(int rows) {
                reached.add("preferredSize(int)");
                return super.preferredSize(rows);
            }
        };
        negative.getPreferredSize();
        assertEquals(java.util.List.of(), reached, "rows <= 0 deliberately skips the (int) form");
    }

    // --- R_match_swing_errors exception fidelity -------------------------------------------

    @Test
    @DisplayName("bounds failures carry the JDK's types")
    void boundsFailuresCarryTheJdksTypes() {
        List l = shown("a", "b");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.getItem(2));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.getItem(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.remove(5));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.delItem(5));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.replaceItem("z", 9));
    }

    @Test
    @DisplayName("remove of an absent item carries the JDK's own message")
    void removeOfAnAbsentItemCarriesTheJdksOwnMessage() {
        List l = shown("a");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> l.remove("nope"));
        assertEquals("item nope not found in list", e.getMessage());
    }

    @Test
    @DisplayName("delItems mutates before throwing when start is negative")
    void delItemsMutatesBeforeThrowingWhenStartIsNegative() {
        List l = shown("a", "b", "c");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> l.delItems(-1, 2));
        assertEquals(0, l.getItemCount(), "AWT's downward loop already emptied it");
    }

    @Test
    @DisplayName("add never throws - null becomes empty and a bad index appends")
    void addNeverThrowsNullBecomesEmptyAndABadIndexAppends() {
        List l = shown("a");
        // Cast required, and faithfully so: Component.add(PopupMenu) makes a
        // bare add(null) ambiguous on real java.awt.List too.
        l.add((String) null);
        assertEquals("", l.getItem(1));
        l.add("z", 99);
        assertEquals("z", l.getItem(2));
        l.add("y", -5);
        assertEquals("y", l.getItem(3));
    }

    @Test
    @DisplayName("replaceItem is remove-then-add, so the row loses its selection")
    void replaceItemIsRemoveThenAddSoTheRowLosesItsSelection() {
        List l = shown("a", "b", "c");
        l.select(1);
        assertEquals(1, l.getSelectedIndex());
        l.replaceItem("B", 1);
        assertEquals("B", l.getItem(1));
        assertEquals(3, l.getItemCount());
        assertEquals(-1, l.getSelectedIndex());
    }

    // --- selection --------------------------------------------------------

    @Test
    @DisplayName("getSelectedIndex is -1 with two rows selected, and so getSelectedItem is null")
    void getSelectedIndexIsMinus1WithTwoRowsSelectedAndSoGetSelectedItemIsNull() {
        List l = shown(4, true, "a", "b", "c");
        l.select(0);
        assertEquals(0, l.getSelectedIndex());
        assertEquals("a", l.getSelectedItem());
        l.select(2);
        assertEquals(-1, l.getSelectedIndex());
        assertNull(l.getSelectedItem());
        assertArrayEquals(new int[]{0, 2}, l.getSelectedIndexes());
    }

    @Test
    @DisplayName("getSelectedObjects is empty, not null, when nothing is selected")
    void getSelectedObjectsIsEmptyNotNullWhenNothingIsSelected() {
        // The opposite of Choice and Checkbox — per class, every time.
        assertArrayEquals(new Object[]{}, shown("a").getSelectedObjects());
        assertArrayEquals(new String[]{}, shown("a").getSelectedItems());
        assertArrayEquals(new int[]{}, shown("a").getSelectedIndexes());
    }

    // --- the JDK's two selection rule sets ----------------------------------

    /** Runs the measured script over one list; {@code displayed} realises it on a frame first. */
    private static java.util.List<String> selectionScript(boolean displayed) {
        java.util.List<String> out = new ArrayList<>();
        java.util.List<String> log = new ArrayList<>();
        List l = new List(4, false) {
            @Override public synchronized void select(int i) { log.add("select(" + i + ")"); super.select(i); }
            @Override public synchronized int[] getSelectedIndexes() { log.add("gsi"); return super.getSelectedIndexes(); }
            @Override public String getItem(int i) { log.add("getItem(" + i + ")"); return super.getItem(i); }
            @Override public synchronized void delItems(int s, int e) { log.add("delItems(" + s + "," + e + ")"); super.delItems(s, e); }
            @Override public synchronized void addItem(String s, int i) { log.add("addItem(" + s + "," + i + ")"); super.addItem(s, i); }
            @Override public synchronized void remove(int i) { log.add("remove(" + i + ")"); super.remove(i); }
        };
        l.addItemListener(e -> log.add("ITEM " + e.getItem() + "/" + e.getStateChange()));
        l.addActionListener(e -> log.add("ACTION " + e.getActionCommand()));
        if (displayed) {
            Frame f = new Frame();
            f.add(l);
            f.addNotify();
        }
        java.util.function.Supplier<String> st = () -> java.util.Arrays.toString(l.getItems()) + " sel="
                + java.util.Arrays.toString(l.getSelectedIndexes()) + " idx=" + l.getSelectedIndex()
                + " multi=" + l.isMultipleMode();
        java.util.function.BiConsumer<String, Runnable> tryRun = (label, r) -> {
            try {
                r.run();
                out.add(label + ": ok");
            } catch (RuntimeException e) {
                out.add(label + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
        };
        for (String s : new String[] {"a", "b", "c", "d"}) l.add(s);
        log.clear();
        out.add("filled: " + st.get()); log.clear();
        l.select(2); out.add("select 2: " + log); log.clear();
        l.select(1); out.add("single select 1: " + st.get()); log.clear();
        l.select(9); out.add("select 9: " + st.get()); log.clear();
        l.select(-1); out.add("select -1: " + st.get()); log.clear();
        l.select(1); log.clear();
        l.add("x", 0); out.add("insert x,0 (before sel 1): " + st.get()); log.clear();
        l.remove(0); out.add("remove 0 (before sel): " + log + " " + st.get()); log.clear();
        l.remove(1); out.add("remove 1 (selected): " + st.get()); log.clear();
        l.select(2); log.clear();
        l.remove(0); out.add("remove 0 below sel 2: " + st.get()); log.clear();
        l.deselect(0); out.add("deselect unselected 0: " + st.get()); log.clear();
        l.deselect(1); out.add("deselect selected 1: " + st.get()); log.clear();
        l.add((String) null); out.add("add null: " + st.get()); log.clear();
        l.add("y", -5); out.add("add y,-5: " + st.get()); log.clear();
        l.add("z", 99); out.add("add z,99: " + st.get()); log.clear();
        l.select(0); log.clear();
        l.replaceItem("R", 0); out.add("replaceItem R,0: " + log + " " + st.get()); log.clear();
        l.select(2); log.clear();
        out.add("getSelectedItem: " + l.getSelectedItem() + " " + log); log.clear();
        out.add("getSelectedItems: " + java.util.Arrays.toString(l.getSelectedItems()) + " " + log); log.clear();
        out.add("isIndexSelected(2): " + l.isIndexSelected(2) + " " + log); log.clear();
        out.add("paramString tail: " + l.toString().replaceAll(".*,selected", "selected") + " " + log); log.clear();
        l.setMultipleMode(true); out.add("multi on: " + st.get()); log.clear();
        l.select(0); l.select(3); out.add("multi select 0,3: " + st.get()); log.clear();
        l.select(3); out.add("multi reselect 3: " + st.get()); log.clear();
        l.remove(1); out.add("multi remove 1: " + st.get()); log.clear();
        l.deselect(0); out.add("multi deselect 0: " + st.get()); log.clear();
        l.select(0); log.clear();
        out.add("multi getSelectedItem: " + l.getSelectedItem() + " objs=" + l.getSelectedObjects().length); log.clear();
        l.setMultipleMode(false); out.add("multi off: " + st.get()); log.clear();
        tryRun.accept("remove zz", () -> l.remove("zz"));
        tryRun.accept("remove 99", () -> l.remove(99));
        tryRun.accept("getItem 99", () -> l.getItem(99));
        tryRun.accept("delItems(-1,1)", () -> l.delItems(-1, 1));
        log.clear();
        out.add("after errors: " + st.get()); log.clear();
        l.makeVisible(42); out.add("visibleIndex: " + l.getVisibleIndex()); log.clear();
        l.removeAll(); out.add("removeAll: " + st.get()); log.clear();
        out.add("rows: " + new List().getRows() + " " + new List(-3).getRows() + " " + new List(1).getRows());
        return out;
    }

    /**
     * Replays a script measured on JDK 25 ({@code java.awt.List} under Xvfb) with the list
     * never realised, and asserts its output verbatim. With no peer the JDK's own bodies run:
     * the selection array is appended to and never adjusted, so after an insert or remove it
     * names other rows, and a multi → single flip keeps every entry.
     */
    @Test
    @DisplayName("a peerless list replays the JDK's measured script")
    void aPeerlessListMatchesTheJdk() {
        assertEquals(java.util.List.of(
                "filled: [a, b, c, d] sel=[] idx=-1 multi=false",
                "select 2: [select(2)]",
                "single select 1: [a, b, c, d] sel=[1] idx=1 multi=false",
                "select 9: [a, b, c, d] sel=[9] idx=9 multi=false",
                "select -1: [a, b, c, d] sel=[-1] idx=-1 multi=false",
                "insert x,0 (before sel 1): [x, a, b, c, d] sel=[1] idx=1 multi=false",
                "remove 0 (before sel): [remove(0), delItems(0,0)] [a, b, c, d] sel=[1] idx=1 multi=false",
                "remove 1 (selected): [a, c, d] sel=[1] idx=1 multi=false",
                "remove 0 below sel 2: [c, d] sel=[2] idx=2 multi=false",
                "deselect unselected 0: [c, d] sel=[2] idx=2 multi=false",
                "deselect selected 1: [c, d] sel=[2] idx=2 multi=false",
                "add null: [c, d, ] sel=[2] idx=2 multi=false",
                "add y,-5: [c, d, , y] sel=[2] idx=2 multi=false",
                "add z,99: [c, d, , y, z] sel=[2] idx=2 multi=false",
                "replaceItem R,0: [remove(0), delItems(0,0), addItem(R,0)] [R, d, , y, z] sel=[0] idx=0 multi=false",
                "getSelectedItem:  [gsi, getItem(2)]",
                "getSelectedItems: [] [gsi, getItem(2)]",
                "isIndexSelected(2): true [gsi]",
                "paramString tail: selected=] [gsi, getItem(2)]",
                "multi on: [R, d, , y, z] sel=[2] idx=2 multi=true",
                "multi select 0,3: [R, d, , y, z] sel=[2, 0, 3] idx=-1 multi=true",
                "multi reselect 3: [R, d, , y, z] sel=[2, 0, 3] idx=-1 multi=true",
                "multi remove 1: [R, , y, z] sel=[2, 0, 3] idx=-1 multi=true",
                "multi deselect 0: [R, , y, z] sel=[2, 3] idx=-1 multi=true",
                "multi getSelectedItem: null objs=3",
                "multi off: [R, , y, z] sel=[2, 3, 0] idx=-1 multi=false",
                "remove zz: IllegalArgumentException item zz not found in list",
                "remove 99: ArrayIndexOutOfBoundsException 99 >= 4",
                "getItem 99: ArrayIndexOutOfBoundsException 99 >= 4",
                "delItems(-1,1): ArrayIndexOutOfBoundsException Array index out of range: -1",
                "after errors: [y, z] sel=[2, 3, 0] idx=-1 multi=false",
                "visibleIndex: 42",
                "removeAll: [] sel=[] idx=-1 multi=false",
                "rows: 4 -3 1"), selectionScript(false));
    }

    /**
     * The same script with the list realised on a frame first, as measured: now the platform
     * peer's rules hold the selection ({@code XListPeer}'s) — it shifts with inserts and
     * removes, stays sorted in multiple mode, and a multi → single flip keeps only the row
     * with the location cursor.
     */
    @Test
    @DisplayName("a displayed list replays the JDK's measured script")
    void aDisplayedListMatchesTheJdk() {
        assertEquals(java.util.List.of(
                "filled: [a, b, c, d] sel=[] idx=-1 multi=false",
                "select 2: [select(2)]",
                "single select 1: [a, b, c, d] sel=[1] idx=1 multi=false",
                "select 9: [a, b, c, d] sel=[9] idx=9 multi=false",
                "select -1: [a, b, c, d] sel=[-1] idx=-1 multi=false",
                "insert x,0 (before sel 1): [x, a, b, c, d] sel=[2] idx=2 multi=false",
                "remove 0 (before sel): [remove(0), delItems(0,0)] [a, b, c, d] sel=[1] idx=1 multi=false",
                "remove 1 (selected): [a, c, d] sel=[] idx=-1 multi=false",
                "remove 0 below sel 2: [c, d] sel=[1] idx=1 multi=false",
                "deselect unselected 0: [c, d] sel=[1] idx=1 multi=false",
                "deselect selected 1: [c, d] sel=[] idx=-1 multi=false",
                "add null: [c, d, ] sel=[] idx=-1 multi=false",
                "add y,-5: [c, d, , y] sel=[] idx=-1 multi=false",
                "add z,99: [c, d, , y, z] sel=[] idx=-1 multi=false",
                "replaceItem R,0: [remove(0), delItems(0,0), addItem(R,0)] [R, d, , y, z] sel=[] idx=-1 multi=false",
                "getSelectedItem:  [gsi, getItem(2)]",
                "getSelectedItems: [] [gsi, getItem(2)]",
                "isIndexSelected(2): true [gsi]",
                "paramString tail: selected=] [gsi, getItem(2)]",
                "multi on: [R, d, , y, z] sel=[2] idx=2 multi=true",
                "multi select 0,3: [R, d, , y, z] sel=[0, 2, 3] idx=-1 multi=true",
                "multi reselect 3: [R, d, , y, z] sel=[0, 2, 3] idx=-1 multi=true",
                "multi remove 1: [R, , y, z] sel=[0, 1, 2] idx=-1 multi=true",
                "multi deselect 0: [R, , y, z] sel=[1, 2] idx=-1 multi=true",
                "multi getSelectedItem: null objs=3",
                "multi off: [R, , y, z] sel=[0] idx=0 multi=false",
                "remove zz: IllegalArgumentException item zz not found in list",
                "remove 99: ArrayIndexOutOfBoundsException 99 >= 4",
                "getItem 99: ArrayIndexOutOfBoundsException 99 >= 4",
                "delItems(-1,1): ArrayIndexOutOfBoundsException Array index out of range: -1",
                "after errors: [y, z] sel=[0] idx=0 multi=false",
                "visibleIndex: 42",
                "removeAll: [] sel=[] idx=-1 multi=false",
                "rows: 4 -3 1"), selectionScript(true));
    }

    @Test
    @DisplayName("a browser click moves the model and the location cursor, as XListPeer's does")
    void aBrowserClickMovesTheModelAndTheLocationCursor() {
        List l = shown(4, true, "a", "b", "c", "d");
        l.select(0);
        dispatchChange(l, 0, 3); // the user adds row 3
        assertArrayEquals(new int[] {0, 3}, l.getSelectedIndexes());
        // The flip keeps the row the user's click left the cursor on.
        l.setMultipleMode(false);
        assertArrayEquals(new int[] {3}, l.getSelectedIndexes());
        assertArrayEquals(new int[] {3}, ((SList) l.getPeer()).getSelectedIndexes());
    }

    @Test
    @DisplayName("the peer renders what the emulator holds, one row at a time")
    void thePeerRendersWhatTheEmulatorHolds() {
        List l = shown(4, true, "a", "b", "c");
        SList peer = (SList) l.getPeer();
        l.select(2);
        l.add("x", 1);
        l.replaceItem("B", 2);
        l.select(0);
        assertArrayEquals(l.getItems(), peer.getItemsArray());
        assertArrayEquals(l.getSelectedIndexes(), peer.getSelectedIndexes());
        l.removeAll();
        assertEquals(0, peer.getItemCount());
    }

    // --- events -------------------------------------------------------------

    @Test
    @DisplayName("a browser selection arrives re-sourced at the emulator with the Integer index")
    void aBrowserSelectionArrivesReSourcedAtTheEmulatorWithTheIntegerIndex() {
        List l = shown(4, true, "a", "b", "c");
        java.util.List<ItemEvent> events = new ArrayList<>();
        l.addItemListener(events::add);
        dispatchChange(l, 1);
        ItemEvent e = assertSingle(events);
        assertSame(l, e.getSource(), "not the surrogate");
        assertSame(l, e.getItemSelectable());
        assertEquals(1, e.getItem());
        assertInstanceOf(Integer.class, e.getItem());
        assertEquals(ItemEvent.SELECTED, e.getStateChange());
    }

    @Test
    @DisplayName("a double-click arrives as an ActionEvent commanded by the item text")
    void aDoubleClickArrivesAsAnActionEventCommandedByTheItemText() {
        List l = shown("Mercury", "Venus");
        java.util.List<ActionEvent> events = new ArrayList<>();
        l.addActionListener(events::add);
        dispatchDoubleClick(l, 1);
        ActionEvent e = assertSingle(events);
        assertSame(l, e.getSource());
        assertEquals("Venus", e.getActionCommand());
    }

    @Test
    @DisplayName("the peer bridge enters at processEvent, not processItemEvent")
    void thePeerBridgeEntersAtProcessEventNotProcessItemEvent() {
        // D_awt_dead_hooks's lane-wide finding: skipping the first hop leaves a migrator's
        // processEvent override compiling, looking wired, and never running.
        java.util.List<String> reached = new ArrayList<>();
        List l = new List(4, true) {
            @Override
            protected void processEvent(java.awt.AWTEvent e) {
                reached.add("processEvent:" + e.getClass().getSimpleName());
                super.processEvent(e);
            }

            @Override
            protected void processItemEvent(ItemEvent e) {
                reached.add("processItemEvent");
                super.processItemEvent(e);
            }

            @Override
            protected void processActionEvent(ActionEvent e) {
                reached.add("processActionEvent");
                super.processActionEvent(e);
            }
        };
        JFrame frame = new JFrame();
        frame.add(l);
        frame.setVisible(true);
        l.add("a");
        dispatchChange(l, 0);
        dispatchDoubleClick(l, 0);
        assertEquals(
                java.util.List.of(
                        "processEvent:ItemEvent", "processItemEvent",
                        "processEvent:ActionEvent", "processActionEvent"),
                reached);
    }

    @Test
    @DisplayName("processEvent routes anything else to super")
    void processEventRoutesAnythingElseToSuper() {
        // A ComponentEvent is neither Item nor Action; it must fall through
        // without reaching either hook.
        java.util.List<String> reached = new ArrayList<>();
        class Probe extends List {
            Probe() {
                super(4, false);
            }

            @Override
            protected void processItemEvent(ItemEvent e) {
                reached.add("item");
            }

            @Override
            protected void processActionEvent(ActionEvent e) {
                reached.add("action");
            }

            void feed(java.awt.AWTEvent e) {
                processEvent(e);
            }
        }
        Probe probe = new Probe();
        probe.feed(new ComponentEvent(probe, ComponentEvent.COMPONENT_SHOWN));
        assertEquals(java.util.List.of(), reached);
    }

    @Test
    @DisplayName("every programmatic path is silent")
    void everyProgrammaticPathIsSilent() {
        List l = shown(4, true, "a", "b", "c");
        java.util.List<Object> seen = new ArrayList<>();
        l.addItemListener(seen::add);
        l.addActionListener(seen::add);
        l.select(0);
        l.deselect(0);
        l.add("d");
        l.remove(0);
        l.replaceItem("z", 0);
        l.setMultipleMode(false);
        l.makeVisible(0);
        l.removeAll();
        assertEquals(java.util.List.of(), seen);
    }

    @Test
    @DisplayName("null listeners are ignored and the typed getters read Component's list")
    void nullListenersAreIgnoredAndTheTypedGettersReadComponentsList() {
        List l = new List();
        l.addItemListener(null);
        l.addActionListener(null);
        assertEquals(0, l.getItemListeners().length);
        assertEquals(0, l.getActionListeners().length);
        ItemListener il = e -> {
        };
        ActionListener al = e -> {
        };
        l.addItemListener(il);
        l.addActionListener(al);
        assertSame(il, assertSingle(l.getItemListeners()));
        assertSame(al, assertSingle(l.getActionListeners()));
        // Component's shared listenerList is what backs them.
        assertSame(il, assertSingle(l.getListeners(ItemListener.class)));
        assertSame(al, assertSingle(l.getListeners(ActionListener.class)));
        l.removeItemListener(il);
        l.removeActionListener(al);
        assertEquals(0, l.getItemListeners().length);
        assertEquals(0, l.getActionListeners().length);
    }

    // --- integration ---------------------------------------------------------

    @Test
    @DisplayName("an AWT List drops into a Swing JPanel inside a JFrame")
    void anAwtListDropsIntoASwingJPanelInsideAJFrame() {
        List l = new List(4, true);
        l.add("a");
        l.add("b");
        JPanel panel = new JPanel();
        panel.add(l);
        JFrame frame = new JFrame();
        frame.add(panel);
        frame.setVisible(true);
        assertSame(panel, l.getParent());
        // The inherited vaadinx.awt.Component surface, with no JComponent in
        // the chain.
        l.setName("planets");
        assertEquals("planets", l.getName());
        l.setEnabled(false);
        assertFalse(l.isEnabled());
    }

    @Test
    @DisplayName("paramString carries AWT's tail")
    void paramStringCarriesAwtsTail() {
        List l = shown("a", "b");
        assertTrue(l.toString().contains(",selected=null"));
        l.select(1);
        assertTrue(l.toString().contains(",selected=b"));
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        java.util.List<String> warns = new ArrayList<>();
        Consumer<String> previous = EHelper.warnHook;
        EHelper.warnHook = warns::add;
        try {
            assertNull(new List().getAccessibleContext());
        } finally {
            EHelper.warnHook = previous;
        }
        assertTrue(assertSingle(warns).contains("getAccessibleContext"));
    }
}
