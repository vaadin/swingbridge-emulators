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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SChoice;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.event.ComponentAdapter;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for the {@code vaadinx.awt.Choice} emulator — the AWT 1.0 dropdown,
 * not {@code vaadinx.swing.JComboBox}. Beyond the API surface, two things this
 * class is the only place to check: R_no_vaadin_in_api limb 2's call directions (three
 * JDK hooks whose delegation runs the "wrong" way round on purpose), and
 * that a browser pick is re-sourced to the emulator. See D_awt_choice /
 * D_awt_lane.
 */
class ChoiceTest extends AbstractKaribuTest {

    /** Rendered and attached, which Karibu's {@code _setValue} requires. */
    private Choice shown(String... items) {
        Choice c = new Choice();
        for (String item : items) {
            c.add(item);
        }
        JFrame frame = new JFrame();
        frame.add(c);
        frame.setVisible(true);
        return c;
    }

    /** The items currently in {@code c}, read back through AWT's index accessor. */
    private static List<String> items(Choice c) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < c.getItemCount(); i++) {
            out.add(c.getItem(i));
        }
        return out;
    }

    // --- Defaults / items ---------------------------------------------

    @Test
    @DisplayName("a fresh Choice is empty with no selection")
    void aFreshChoiceIsEmptyWithNoSelection() {
        Choice c = new Choice();
        assertEquals(0, c.getItemCount());
        assertEquals(-1, c.getSelectedIndex());
        assertNull(c.getSelectedItem());
        assertNull(c.getSelectedObjects());
    }

    @Test
    @DisplayName("items round-trip through AWT's own names")
    void itemsRoundTripThroughAwtsOwnNames() {
        // The names the surrogate cannot carry: Select.removeAll() removes
        // the non-item slotted children, an exact collision with the
        // opposite meaning, so the AWT family lives here.
        Choice c = new Choice();
        c.add("a");
        c.add("b");
        c.insert("head", 0);
        assertEquals(3, c.getItemCount());
        assertEquals(List.of("head", "a", "b"), items(c));
        c.remove("a");
        assertEquals(List.of("head", "b"), items(c));
        c.remove(0);
        assertEquals(List.of("b"), items(c));
        c.removeAll();
        assertEquals(0, c.getItemCount());
        assertEquals(-1, c.getSelectedIndex());
    }

    @Test
    @DisplayName("the peer is an SChoice, locked down per R_leaf_peer_lockdown")
    void thePeerIsAnSChoiceLockedDownPerRLeafPeerLockdown() {
        Choice c = shown("a");
        assertSame(c.getPeer(), LocatorJ._get(SChoice.class));
    }

    // --- R_no_vaadin_in_api limb 2: the JDK's call directions ------------------------

    @Test
    @DisplayName("getItemCount delegates to countItems, not the other way round")
    void getItemCountDelegatesToCountItemsNotTheOtherWayRound() {
        // JDK: getItemCount() { return countItems(); }. Inverting it would
        // leave a migrator's countItems() override silently dead — the D_r12_provenance
        // failure mode, invisible to both the compiler and WarnInventory.
        vaadinx.Counter hits = new vaadinx.Counter();
        Choice c = new Choice() {
            @Override
            public int countItems() {
                hits.inc();
                return super.countItems();
            }
        };
        c.add("a");
        assertEquals(1, c.getItemCount());
        assertTrue(hits.get() > 0, "getItemCount must route through countItems");
    }

    @Test
    @DisplayName("add delegates to addItem, not the other way round")
    void addDelegatesToAddItemNotTheOtherWayRound() {
        // JDK: add(String) { addItem(item); }.
        List<String> seen = new ArrayList<>();
        Choice c = new Choice() {
            @Override
            public void addItem(String item) {
                seen.add(item);
                super.addItem(item);
            }
        };
        c.add("via-add");
        assertEquals(List.of("via-add"), seen);
    }

    @Test
    @DisplayName("add does not route through insert")
    void addDoesNotRouteThroughInsert() {
        // JDK: both add and insert reach a *private* helper, so an insert
        // override does not intercept add. Faithful in both directions.
        vaadinx.Counter insertHits = new vaadinx.Counter();
        Choice c = new Choice() {
            @Override
            public void insert(String item, int index) {
                insertHits.inc();
                super.insert(item, index);
            }
        };
        c.add("a");
        insertHits.assertEquals(0);
        c.insert("b", 0);
        insertHits.assertEquals(1);
    }

    @Test
    @DisplayName("select by string routes through select by index")
    void selectByStringRoutesThroughSelectByIndex() {
        // JDK: select(String) calls the *public* select(int), so a subclass
        // overriding the latter intercepts both.
        List<Integer> positions = new ArrayList<>();
        Choice c = new Choice() {
            @Override
            public synchronized void select(int pos) {
                positions.add(pos);
                super.select(pos);
            }
        };
        c.add("a");
        c.add("b");
        positions.clear();
        c.select("b");
        assertEquals(List.of(1), positions);
        assertEquals("b", c.getSelectedItem());
    }

    @Test
    @DisplayName("select by an unknown string never reaches select by index")
    void selectByAnUnknownStringNeverReachesSelectByIndex() {
        List<Integer> positions = new ArrayList<>();
        Choice c = new Choice() {
            @Override
            public synchronized void select(int pos) {
                positions.add(pos);
                super.select(pos);
            }
        };
        c.add("a");
        positions.clear();
        c.select("nope");
        assertEquals(List.of(), positions);
    }

    @Test
    @DisplayName("a browser pick reaches a processEvent override, not only processItemEvent")
    void aBrowserPickReachesAProcessEventOverrideNotOnlyProcessItemEvent() {
        // AWT routes dispatchEvent -> processEvent -> processItemEvent, so
        // entering the bridge at the second hop would leave this override
        // compiling, looking wired, and never running on a real selection.
        List<String> seen = new ArrayList<>();
        Choice c = new Choice() {
            @Override
            protected void processEvent(java.awt.AWTEvent e) {
                seen.add("processEvent");
                super.processEvent(e);
            }

            @Override
            protected void processItemEvent(ItemEvent e) {
                seen.add("processItemEvent");
                super.processItemEvent(e);
            }
        };
        c.add("a");
        c.add("b");
        JFrame frame = new JFrame();
        frame.add(c);
        frame.setVisible(true);

        LocatorJ._setValue(LocatorJ._get(SChoice.class), 1);

        assertEquals(List.of("processEvent", "processItemEvent"), seen);
    }

    // --- Events -------------------------------------------------------

    @Test
    @DisplayName("a browser pick is re-sourced to the emulator")
    void aBrowserPickIsReSourcedToTheEmulator() {
        Choice c = shown("a", "b", "c");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);

        LocatorJ._setValue(LocatorJ._get(SChoice.class), 2);

        assertEquals(1, events.size(), "AWT posts no DESELECTED partner; got " + events);
        assertSame(c, events.get(0).getItemSelectable(),
                "migrated code casts (Choice) e.getItemSelectable() — it must not see the surrogate");
        assertEquals(ItemEvent.SELECTED, events.get(0).getStateChange());
        assertEquals("c", events.get(0).getItem());
        assertEquals(2, c.getSelectedIndex());
    }

    @Test
    @DisplayName("a browser pick runs select before posting, as the platform peers do")
    void aBrowserPickRunsSelectBeforePosting() {
        // XChoicePeer and LWChoicePeer both call target.select(i) and then
        // post, so a select(int) override sees the user's picks too.
        List<String> calls = new ArrayList<>();
        Choice c = new Choice() {
            @Override
            public void select(int pos) {
                calls.add("select(" + pos + ")");
                super.select(pos);
            }
        };
        c.add("a");
        c.add("b");
        JFrame frame = new JFrame();
        frame.add(c);
        frame.setVisible(true);
        c.addItemListener(e -> calls.add("listener(" + e.getItem() + ") sees " + c.getSelectedIndex()));
        calls.clear();

        LocatorJ._setValue(LocatorJ._get(SChoice.class), 1);

        assertEquals(List.of("select(1)", "listener(b) sees 1"), calls);
    }

    @Test
    @DisplayName("the peer renders what the emulator holds after every kind of change")
    void thePeerRendersWhatTheEmulatorHolds() {
        Choice c = shown("a", "b", "c");
        SChoice peer = (SChoice) c.getPeer();
        c.select(2);
        c.insert("x", 1);
        c.remove("b");
        c.add("d");
        c.select("d");
        c.remove(0);
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < peer.getItemCount(); i++) {
            rendered.add(peer.getItemAt(i));
        }
        assertEquals(items(c), rendered);
        assertEquals(c.getSelectedIndex(), peer.getSelectedIndex());
        c.removeAll();
        assertEquals(0, peer.getItemCount());
        assertEquals(-1, peer.getSelectedIndex());
    }

    /**
     * Replays a script measured on JDK 25 ({@code java.awt.Choice} under Xvfb, since its
     * constructor refuses a headless JVM) and asserts its output verbatim: which public
     * methods each mutation reaches — the re-selection through {@code select(int)} on insert
     * and remove, and the private helpers that keep {@code add} off {@code insert} and
     * {@code remove(String)} off {@code remove(int)} — the selection each leaves, the reads
     * through {@code getItem}, the exception table, and no {@code ItemEvent} throughout.
     */
    @Test
    @DisplayName("item management replays the JDK's measured script")
    @SuppressWarnings("deprecation") // countItems / addItem are on the measured path
    void itemManagementMatchesTheJdk() {
        List<String> out = new ArrayList<>();
        List<String> log = new ArrayList<>();
        Choice c = new Choice() {
            @Override public void select(int pos) { log.add("select(" + pos + ")"); super.select(pos); }
            @Override public String getItem(int i) { log.add("getItem(" + i + ")"); return super.getItem(i); }
            @Override public int countItems() { log.add("countItems"); return super.countItems(); }
            @Override public void addItem(String s) { log.add("addItem(" + s + ")"); super.addItem(s); }
            @Override public void insert(String s, int i) { log.add("insert(" + s + "," + i + ")"); super.insert(s, i); }
            @Override public void remove(int i) { log.add("remove(" + i + ")"); super.remove(i); }
        };
        java.util.function.Supplier<String> st = () -> {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < c.getItemCount(); i++) sb.append(i == 0 ? "" : ",").append(c.getItem(i));
            return sb.append("] sel=").append(c.getSelectedIndex()).append(" item=").append(c.getSelectedItem()).toString();
        };
        java.util.function.BiConsumer<String, Runnable> tryRun = (label, r) -> {
            try {
                r.run();
                out.add(label + ": ok");
            } catch (RuntimeException e) {
                out.add(label + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
            }
        };
        c.addItemListener(e -> log.add("EVENT " + e.getItem()));
        out.add("fresh: " + c.getSelectedIndex() + " " + c.getSelectedItem() + " " + c.getSelectedObjects());
        c.add("a"); out.add("add a: " + log); log.clear();
        c.add("b"); out.add("add b: " + log); log.clear();
        c.add("c"); out.add("add c: " + log); log.clear();
        c.select(2); out.add("select 2: " + log); log.clear();
        c.insert("x", 1); out.add("insert x,1: " + log); log.clear();
        out.add("  " + st.get()); log.clear();
        c.select(3); log.clear();
        c.insert("y", 99); out.add("insert y,99: " + log); log.clear();
        out.add("  " + st.get()); log.clear();
        c.remove(0); out.add("remove 0 (below sel 3): " + log); log.clear();
        out.add("  " + st.get()); log.clear();
        c.remove(2); out.add("remove 2 (selected): " + log); log.clear();
        out.add("  " + st.get()); log.clear();
        c.remove("y"); out.add("remove y (above sel): " + log); log.clear();
        out.add("  " + st.get()); log.clear();
        c.select("b"); out.add("select b: " + log); log.clear();
        c.select("zzz"); out.add("select zzz: " + log); log.clear();
        c.add("b"); log.clear();
        out.add("dup: " + st.get()); log.clear();
        c.select("b"); out.add("select b dup: " + c.getSelectedIndex() + " " + log); log.clear();
        out.add("getItemCount: " + c.getItemCount() + " " + log); log.clear();
        Object[] so = c.getSelectedObjects();
        out.add("getSelectedObjects: " + so.length + " " + so[0] + " " + log); log.clear();
        out.add("getSelectedItem: " + c.getSelectedItem() + " " + log); log.clear();
        out.add("toString tail: " + c.toString().replaceAll(".*,current", "current") + " " + log); log.clear();
        tryRun.accept("add null", () -> c.add((String) null));
        tryRun.accept("insert -1", () -> c.insert("q", -1));
        tryRun.accept("insert null", () -> c.insert(null, 0));
        tryRun.accept("remove zzz", () -> c.remove("zzz"));
        tryRun.accept("remove 9", () -> c.remove(9));
        tryRun.accept("getItem 9", () -> c.getItem(9));
        tryRun.accept("select 9", () -> c.select(9));
        tryRun.accept("select -1", () -> c.select(-1));
        log.clear();
        out.add("after errors: " + st.get()); log.clear();
        c.removeAll(); out.add("removeAll: " + log + " " + c.getSelectedIndex() + " " + c.getSelectedObjects()); log.clear();
        c.insert("only", 0); out.add("insert into empty: " + log); log.clear();
        c.remove(0); out.add("remove last: " + log + " sel=" + c.getSelectedIndex()); log.clear();

        assertEquals(List.of(
                "fresh: -1 null null",
                "add a: [addItem(a), select(0)]",
                "add b: [addItem(b)]",
                "add c: [addItem(c)]",
                "select 2: [select(2)]",
                "insert x,1: [insert(x,1), select(0)]",
                "  [a,x,b,c] sel=0 item=a",
                "insert y,99: [insert(y,99)]",
                "  [a,x,b,c,y] sel=3 item=c",
                "remove 0 (below sel 3): [remove(0), select(2)]",
                "  [x,b,c,y] sel=2 item=c",
                "remove 2 (selected): [remove(2), select(0)]",
                "  [x,b,y] sel=0 item=x",
                "remove y (above sel): []",
                "  [x,b] sel=0 item=x",
                "select b: [select(1)]",
                "select zzz: []",
                "dup: [x,b,b] sel=1 item=b",
                "select b dup: 1 [select(1)]",
                "getItemCount: 3 [countItems]",
                "getSelectedObjects: 1 b [getItem(1)]",
                "getSelectedItem: b [getItem(1)]",
                "toString tail: current=b] [getItem(1)]",
                "add null: NullPointerException cannot add null item to Choice",
                "insert -1: IllegalArgumentException index less than zero.",
                "insert null: NullPointerException cannot add null item to Choice",
                "remove zzz: IllegalArgumentException item zzz not found in choice",
                "remove 9: ArrayIndexOutOfBoundsException 9 >= 3",
                "getItem 9: ArrayIndexOutOfBoundsException 9 >= 3",
                "select 9: IllegalArgumentException illegal Choice item position: 9",
                "select -1: IllegalArgumentException illegal Choice item position: -1",
                "after errors: [x,b,b] sel=1 item=b",
                "removeAll: [] -1 null",
                "insert into empty: [insert(only,0), select(0)]",
                "remove last: [remove(0)] sel=-1"), out);
    }

    @Test
    @DisplayName("select is silent, the sharpest divergence from JComboBox")
    void selectIsSilentTheSharpestDivergenceFromJComboBox() {
        Choice c = shown("a", "b", "c");
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);
        c.select(2);
        c.select("a");
        assertEquals(0, c.getSelectedIndex());
        assertEquals(List.of(), events, "AWT fires ItemEvents only on user interaction");
    }

    @Test
    @DisplayName("listeners fire first-registered-first, as AWTEventMulticaster does")
    void listenersFireFirstRegisteredFirstAsAwtEventMulticasterDoes() {
        // EventListenerList hands listeners back last-first (the Swing
        // convention), which would silently reverse the order AWT guarantees.
        Choice c = new Choice();
        c.add("a");
        List<Integer> order = new ArrayList<>();
        c.addItemListener(e -> order.add(1));
        c.addItemListener(e -> order.add(2));
        c.addItemListener(e -> order.add(3));

        c.processEvent(new ItemEvent(c, ItemEvent.ITEM_STATE_CHANGED, "a", ItemEvent.SELECTED));

        assertEquals(List.of(1, 2, 3), order);
        assertEquals(3, c.getItemListeners().length);
    }

    @Test
    @DisplayName("listener registration round-trips and null listeners are ignored")
    void listenerRegistrationRoundTripsAndNullListenersAreIgnored() {
        Choice c = new Choice();
        ItemListener l = e -> {
        };
        assertEquals(0, c.getItemListeners().length);
        c.addItemListener(l);
        c.addItemListener(null);
        assertSame(l, assertSingle(c.getItemListeners()));
        assertSame(l, assertSingle(c.getListeners(ItemListener.class)));
        c.removeItemListener(l);
        c.removeItemListener(null);
        assertEquals(0, c.getItemListeners().length);
    }

    @Test
    @DisplayName("a non-ItemEvent falls through to Component's routing")
    void aNonItemEventFallsThroughToComponentsRouting() {
        Choice c = new Choice();
        List<String> hits = new ArrayList<>();
        c.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                hits.add("shown");
            }
        });
        c.processEvent(new ComponentEvent(c, ComponentEvent.COMPONENT_SHOWN));
        assertEquals(List.of("shown"), hits);
    }

    // --- Exceptions propagate from the peer ---------------------------

    @Test
    @DisplayName("the R_match_swing_errors exception table reaches the emulator unchanged")
    void theRMatchSwingErrorsExceptionTableReachesTheEmulatorUnchanged() {
        Choice c = new Choice();
        c.add("a");
        assertEquals("cannot add null item to Choice",
                assertThrows(NullPointerException.class, () -> c.add((String) null)).getMessage());
        assertEquals("index less than zero.",
                assertThrows(IllegalArgumentException.class, () -> c.insert("x", -1)).getMessage());
        assertEquals("illegal Choice item position: 9",
                assertThrows(IllegalArgumentException.class, () -> c.select(9)).getMessage());
        assertEquals("item nope not found in choice",
                assertThrows(IllegalArgumentException.class, () -> c.remove("nope")).getMessage());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> c.getItem(1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> c.remove(1));
    }

    // --- The AWT-leaf-in-a-Swing-tree case ----------------------------

    @Test
    @DisplayName("an AWT Choice drops into a Swing container and renders")
    void anAwtChoiceDropsIntoASwingContainerAndRenders() {
        Choice c = new Choice();
        c.add("metric");
        c.add("imperial");
        JPanel panel = new JPanel();
        panel.add(c);
        JFrame frame = new JFrame();
        frame.add(panel);
        frame.setVisible(true);

        assertSame(panel, c.getParent());
        List<String> picked = new ArrayList<>();
        c.addItemListener(e -> picked.add((String) e.getItem()));
        LocatorJ._setValue(LocatorJ._get(SChoice.class), 1);
        assertEquals(List.of("imperial"), picked);
    }

    @Test
    @DisplayName("inherited Component surface works without JComponent in the chain")
    void inheritedComponentSurfaceWorksWithoutJComponentInTheChain() {
        Choice c = new Choice();
        c.setName("units");
        assertEquals("units", c.getName());
        c.setEnabled(false);
        assertFalse(c.isEnabled());
        c.setVisible(false);
        assertFalse(c.isVisible());
    }

    // --- toString shape / stubs ---------------------------------------

    @Test
    @DisplayName("toString carries the JDK's current= tail")
    void toStringCarriesTheJdksCurrentTail() {
        Choice c = new Choice();
        assertTrue(c.toString().contains(",current=null"), c.toString());
        c.add("metric");
        assertTrue(c.toString().contains(",current=metric"), c.toString());
    }

    @Test
    @DisplayName("addNotify is declared here and chains to super")
    void addNotifyIsDeclaredHereAndChainsToSuper() throws NoSuchMethodException {
        // The JDK allocates the native peer in addNotify; ours is eternal
        // and made in the ctor, so the override exists only so a migrator's
        // `super.addNotify()` idiom lands on Component's implementation
        // rather than on nothing. Assert it is really declared on Choice.
        assertEquals(Choice.class, Choice.class.getMethod("addNotify").getDeclaringClass());
        new Choice().addNotify();
    }
}
