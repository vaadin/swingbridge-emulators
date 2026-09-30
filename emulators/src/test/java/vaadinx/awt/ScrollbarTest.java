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
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SScrollbar;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.JComponent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.Adjustable;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for the {@code vaadinx.awt.Scrollbar} emulator — the AWT 1.0 scrollbar,
 * not {@code vaadinx.swing.JScrollBar}. Most of the file is about the three things
 * that make this widget unlike the AWT leaves before it: the value model's
 * clamping band, the JDK call graph running backwards through the deprecated
 * AWT-1.0 names, and an {@code AdjustmentEvent} whose source is a typed interface
 * the peer cannot implement. See D_awt_scrollbar / SD_sscrollbar.
 */
class ScrollbarTest extends AbstractKaribuTest {

    /** Attaches {@code bar} to a visible frame so Karibu will accept from-client input. */
    private JFrame show(Scrollbar bar) {
        JFrame frame = new JFrame();
        frame.add(bar);
        frame.setVisible(true);
        return frame;
    }

    /** The rendered {@code <input type=range>} of the single scrollbar under test. */
    private static RangeInput peer() {
        return LocatorJ._get(RangeInput.class);
    }

    // --- Ctors --------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor is AWT's vertical 0-10-0-100")
    void noArgCtorIsAwtsVertical0100100() {
        Scrollbar s = new Scrollbar();
        assertEquals(Scrollbar.VERTICAL, s.getOrientation());
        assertEquals(0, s.getValue());
        assertEquals(10, s.getVisibleAmount());
        assertEquals(0, s.getMinimum());
        assertEquals(100, s.getMaximum());
    }

    @Test
    @DisplayName("the constants match Adjustable's and SwingConstants'")
    void theConstantsMatchAdjustablesAndSwingConstants() {
        // No collision here, unlike java.awt.Label's alignment set (SD_slabel) —
        // AWT, Adjustable and SwingConstants all agree on 0/1.
        assertEquals(Adjustable.HORIZONTAL, Scrollbar.HORIZONTAL);
        assertEquals(Adjustable.VERTICAL, Scrollbar.VERTICAL);
        assertEquals(javax.swing.SwingConstants.HORIZONTAL, Scrollbar.HORIZONTAL);
        assertEquals(javax.swing.SwingConstants.VERTICAL, Scrollbar.VERTICAL);
    }

    @Test
    @DisplayName("five-arg ctor seeds every field")
    void fiveArgCtorSeedsEveryField() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 30, 5, 10, 60);
        assertEquals(Scrollbar.HORIZONTAL, s.getOrientation());
        assertEquals(30, s.getValue());
        assertEquals(5, s.getVisibleAmount());
        assertEquals(10, s.getMinimum());
        assertEquals(60, s.getMaximum());
    }

    @Test
    @DisplayName("an illegal ctor orientation throws out of super, leaving nothing half-built")
    void anIllegalCtorOrientationThrowsOutOfSuperLeavingNothingHalfBuilt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Scrollbar(Adjustable.NO_ORIENTATION));
        assertEquals("illegal scrollbar orientation", e.getMessage());
    }

    @Test
    @DisplayName("the peer is an SScrollbar, locked down per R_leaf_peer_lockdown")
    void thePeerIsAnSScrollbarLockedDownPerRLeafPeerLockdown() {
        assertInstanceOf(SScrollbar.class, new Scrollbar().getPeer());
        // Both orientations ride one peer type — no D_frame_strategy-style dispatch.
        assertInstanceOf(SScrollbar.class, new Scrollbar(Scrollbar.HORIZONTAL).getPeer());
    }

    // --- the value model ------------------------------------------------

    @Test
    @DisplayName("setValue clamps into the band, not into min-max")
    void setValueClampsIntoTheBandNotIntoMinMax() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        s.setValue(999);
        assertEquals(90, s.getValue());
        s.setValue(-5);
        assertEquals(0, s.getValue());
    }

    @Test
    @DisplayName("setMaximum below minimum drags minimum down with it")
    void setMaximumBelowMinimumDragsMinimumDownWithIt() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 50, 5, 40, 100);
        s.setMaximum(20);
        assertEquals(19, s.getMinimum());
        assertEquals(20, s.getMaximum());
    }

    @Test
    @DisplayName("setValues is the funnel every value mutator routes through")
    void setValuesIsTheFunnelEveryValueMutatorRoutesThrough() {
        // R_no_vaadin_in_api limb 2: a migrator overriding setValues to tighten clamping must
        // see every setter's traffic, so the four singles cannot write the peer
        // directly.
        List<String> calls = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100) {
            @Override
            public void setValues(int value, int visible, int minimum, int maximum) {
                calls.add(value + "/" + visible + "/" + minimum + "/" + maximum);
                super.setValues(value, visible, minimum, maximum);
            }
        };
        s.setValue(20);
        s.setVisibleAmount(5);
        s.setMinimum(2);
        // The first call is the constructor's: the JDK seeds the model through the funnel too.
        assertEquals(List.of("0/10/0/100", "20/10/0/100", "20/5/0/100", "20/5/2/100"), calls);
    }

    @Test
    @DisplayName("setMaximum also reaches setValues, after AWT's own pre-steps")
    void setMaximumAlsoReachesSetValuesAfterAwtsOwnPreSteps() {
        List<String> calls = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 50, 5, 40, 100) {
            @Override
            public void setValues(int value, int visible, int minimum, int maximum) {
                calls.add(value + "/" + visible + "/" + minimum + "/" + maximum);
                super.setValues(value, visible, minimum, maximum);
            }
        };
        calls.clear(); // the constructor's own setValues call
        s.setMaximum(20);
        // The minimum rewrite happens before the funnel, so the funnel sees 19.
        assertEquals(List.of("50/5/19/20"), calls);
    }

    /** The state line the JDK script printed after each step. */
    private static String state(Scrollbar s) {
        return s.getValue() + "/" + s.getVisibleAmount() + "/" + s.getMinimum() + "/" + s.getMaximum()
                + " o=" + s.getOrientation() + " unit=" + s.getUnitIncrement() + " block=" + s.getBlockIncrement()
                + " adj=" + s.getValueIsAdjusting();
    }

    /**
     * Replays a script measured on JDK 25 ({@code java.awt.Scrollbar} under Xvfb, since its
     * constructor refuses a headless JVM) and asserts its output verbatim: the clamping at the
     * int extremes, the constructor running an overriding {@code setValues}, the single
     * setters passing fields rather than calling getters, {@code setMaximum}'s pre-step
     * writing {@code minimum} past a {@code setValues} that does not call super, the
     * deprecated names carrying the increments, and no {@code AdjustmentEvent} for any
     * programmatic write.
     */
    @Test
    @DisplayName("the value model replays the JDK's measured script")
    @SuppressWarnings("deprecation") // the AWT-1.0 names are on the measured path
    void valueModelMatchesTheJdk() {
        List<String> out = new ArrayList<>();
        List<String> log = new ArrayList<>();
        out.add("default: " + state(new Scrollbar()));
        out.add("clamped ctor: " + state(new Scrollbar(Scrollbar.HORIZONTAL, 200, 0, 50, 10)));
        out.add("MAX ctor: " + state(new Scrollbar(Scrollbar.HORIZONTAL, 5, 5, Integer.MAX_VALUE, Integer.MIN_VALUE)));
        out.add("span ctor: " + state(new Scrollbar(Scrollbar.HORIZONTAL, 0, Integer.MAX_VALUE, Integer.MIN_VALUE,
                Integer.MAX_VALUE)));

        Scrollbar funnel = new Scrollbar(Scrollbar.HORIZONTAL, 3, 4, 1, 50) {
            @Override public void setValues(int v, int vis, int min, int max) {
                log.add("setValues(" + v + "," + vis + "," + min + "," + max + ")");
                super.setValues(v, vis, min, max);
            }
            @Override public int getVisibleAmount() { log.add("getVisibleAmount"); return super.getVisibleAmount(); }
            @Override public int getMinimum() { log.add("getMinimum"); return super.getMinimum(); }
            @Override public int getMaximum() { log.add("getMaximum"); return super.getMaximum(); }
            @Override public int getValue() { log.add("getValue"); return super.getValue(); }
        };
        out.add("ctor calls: " + log);
        log.clear();
        funnel.setValue(10);
        funnel.setMinimum(2);
        funnel.setMaximum(40);
        funnel.setVisibleAmount(6);
        out.add("setter calls: " + log);
        log.clear();

        boolean[] muted = {false};
        Scrollbar mute = new Scrollbar(Scrollbar.HORIZONTAL, 50, 5, 40, 100) {
            @Override public void setValues(int v, int vis, int min, int max) {
                log.add("setValues(" + v + "," + vis + "," + min + "," + max + ")");
                if (!muted[0]) super.setValues(v, vis, min, max);
            }
        };
        muted[0] = true;
        log.clear();
        mute.setMaximum(20);
        out.add("muted setMaximum(20): " + log + " -> " + state(mute));
        log.clear();

        Scrollbar o = new Scrollbar(Scrollbar.HORIZONTAL);
        try {
            o.setOrientation(42);
        } catch (IllegalArgumentException e) {
            out.add("setOrientation(42): IAE " + e.getMessage());
        }
        o.setOrientation(Scrollbar.HORIZONTAL);
        o.setOrientation(Scrollbar.VERTICAL);
        out.add("after orientation: " + state(o));
        try {
            new Scrollbar(2);
        } catch (IllegalArgumentException e) {
            out.add("ctor(2): IAE " + e.getMessage());
        }

        Scrollbar inc = new Scrollbar() {
            @Override public void setLineIncrement(int v) { log.add("setLine(" + v + ")"); super.setLineIncrement(v); }
            @Override public int getLineIncrement() { log.add("getLine"); return super.getLineIncrement(); }
            @Override public void setPageIncrement(int v) { log.add("setPage(" + v + ")"); super.setPageIncrement(v); }
            @Override public int getPageIncrement() { log.add("getPage"); return super.getPageIncrement(); }
            @Override public int getVisible() { log.add("getVisible"); return super.getVisible(); }
        };
        inc.setUnitIncrement(0);
        inc.setBlockIncrement(-4);
        out.add("increments: unit=" + inc.getUnitIncrement() + " block=" + inc.getBlockIncrement()
                + " vis=" + inc.getVisibleAmount() + " calls=" + log);
        log.clear();

        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        s.addAdjustmentListener(e -> log.add("L1:" + e.getValue()));
        s.addAdjustmentListener(e -> log.add("L2:" + e.getValue()));
        s.setValueIsAdjusting(true);
        s.setValue(30);
        s.setValues(40, 5, 0, 100);
        s.setMinimum(5);
        s.setMaximum(90);
        s.setVisibleAmount(20);
        s.setOrientation(Scrollbar.VERTICAL);
        s.setUnitIncrement(3);
        out.add("programmatic events: " + log + " state " + state(s));

        assertEquals(List.of(
                "default: 0/10/0/100 o=1 unit=1 block=10 adj=false",
                "clamped ctor: 50/1/50/51 o=0 unit=1 block=10 adj=false",
                "MAX ctor: 2147483646/1/2147483646/2147483647 o=0 unit=1 block=10 adj=false",
                "span ctor: -2147483648/2147483647/-2147483648/-1 o=0 unit=1 block=10 adj=false",
                "ctor calls: [setValues(3,4,1,50)]",
                "setter calls: [setValues(10,4,1,50), setValues(10,4,2,50), setValues(10,4,2,40), setValues(10,6,2,40)]",
                "muted setMaximum(20): [setValues(50,5,19,20)] -> 50/5/19/100 o=0 unit=1 block=10 adj=false",
                "setOrientation(42): IAE illegal scrollbar orientation",
                "after orientation: 0/10/0/100 o=1 unit=1 block=10 adj=false",
                "ctor(2): IAE illegal scrollbar orientation",
                "increments: unit=1 block=1 vis=10 calls=[setLine(0), setPage(-4), getLine, getPage, getVisible]",
                "programmatic events: [] state 40/20/5/90 o=1 unit=3 block=10 adj=true"), out);
    }

    // --- the deprecated names are the real implementations -------------

    @Test
    @DisplayName("getVisibleAmount routes through the deprecated getVisible")
    @SuppressWarnings("deprecation") // getVisible is the AWT-1.0 name carrying the body
    void getVisibleAmountRoutesThroughTheDeprecatedGetVisible() {
        List<String> calls = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 7, 0, 100) {
            @Override
            public int getVisible() {
                calls.add("getVisible");
                return super.getVisible();
            }
        };
        assertEquals(7, s.getVisibleAmount());
        assertEquals(List.of("getVisible"), calls);
    }

    @Test
    @DisplayName("the unit increment pair routes through setLineIncrement and getLineIncrement")
    @SuppressWarnings("deprecation") // the AWT-1.0 pair is what carries the behaviour here
    void theUnitIncrementPairRoutesThroughSetLineIncrementAndGetLineIncrement() {
        // Backwards from every other deprecated pair in the tree: in the JDK
        // the AWT-1.0 names carry the behaviour. A migrator's setLineIncrement
        // override must see modern-name traffic (R_no_vaadin_in_api limb 2, D_r12_provenance's shape).
        List<String> calls = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL) {
            @Override
            public void setLineIncrement(int v) {
                calls.add("setLine(" + v + ")");
                super.setLineIncrement(v);
            }

            @Override
            public int getLineIncrement() {
                calls.add("getLine");
                return super.getLineIncrement();
            }
        };
        s.setUnitIncrement(16);
        assertEquals(16, s.getUnitIncrement());
        assertEquals(List.of("setLine(16)", "getLine"), calls);
    }

    @Test
    @DisplayName("the block increment pair routes through setPageIncrement and getPageIncrement")
    @SuppressWarnings("deprecation") // as above — the AWT-1.0 pair is the implementation
    void theBlockIncrementPairRoutesThroughSetPageIncrementAndGetPageIncrement() {
        List<String> calls = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL) {
            @Override
            public void setPageIncrement(int v) {
                calls.add("setPage(" + v + ")");
                super.setPageIncrement(v);
            }

            @Override
            public int getPageIncrement() {
                calls.add("getPage");
                return super.getPageIncrement();
            }
        };
        s.setBlockIncrement(25);
        assertEquals(25, s.getBlockIncrement());
        assertEquals(List.of("setPage(25)", "getPage"), calls);
    }

    @Test
    @DisplayName("increments clamp up to 1 through either name")
    @SuppressWarnings("deprecation") // exercises both the modern and AWT-1.0 spellings
    void incrementsClampUpTo1ThroughEitherName() {
        Scrollbar s = new Scrollbar();
        s.setUnitIncrement(0);
        assertEquals(1, s.getUnitIncrement());
        s.setLineIncrement(-3);
        assertEquals(1, s.getLineIncrement());
        s.setBlockIncrement(-9);
        assertEquals(1, s.getBlockIncrement());
    }

    // --- orientation ---------------------------------------------------

    @Test
    @DisplayName("setOrientation flips the peer and stays int-typed per R_no_vaadin_in_api")
    void setOrientationFlipsThePeerAndStaysIntTypedPerRNoVaadinInApi() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL);
        s.setOrientation(Scrollbar.VERTICAL);
        assertEquals(Scrollbar.VERTICAL, s.getOrientation());
        // The Vaadin enum never surfaces on this class's API; check through
        // the peer that it did move.
        assertEquals(RangeInput.Orientation.VERTICAL, ((SScrollbar) s.getPeer()).getOrientation());
    }

    @Test
    @DisplayName("an illegal setOrientation throws and leaves the old value")
    void anIllegalSetOrientationThrowsAndLeavesTheOldValue() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL);
        assertThrows(IllegalArgumentException.class, () -> s.setOrientation(42));
        assertEquals(Scrollbar.HORIZONTAL, s.getOrientation());
    }

    // --- the AdjustmentEvent bridge -------------------------------------

    @Test
    @DisplayName("a browser value change fires TRACK, sourced at the emulator")
    void aBrowserValueChangeFiresTrackSourcedAtTheEmulator() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        show(s);
        List<AdjustmentEvent> events = new ArrayList<>();
        s.addAdjustmentListener(events::add);

        LocatorJ._setValue(peer(), 42.0);

        AdjustmentEvent e = assertSingle(events);
        // The cast migrated code writes. The surrogate could not have produced
        // this: AdjustmentEvent's source is typed java.awt.Adjustable, which
        // SScrollbar cannot implement (SD_sscrollbar).
        assertSame(s, e.getAdjustable());
        assertSame(s, e.getSource());
        assertEquals(AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, e.getID());
        // TRACK unconditionally — nothing tells us which gesture it was.
        assertEquals(AdjustmentEvent.TRACK, e.getAdjustmentType());
        assertEquals(42, e.getValue());
        assertTrue(e.getValueIsAdjusting());
        assertEquals(42, s.getValue());
    }

    @Test
    @DisplayName("a programmatic write fires nothing")
    void aProgrammaticWriteFiresNothing() {
        // The inverse of vaadinx.swing.JScrollBar, whose BoundedRangeModel
        // fires on every write. AWT's setValue javadoc says so outright.
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        show(s);
        List<AdjustmentEvent> events = new ArrayList<>();
        s.addAdjustmentListener(events::add);

        s.setValue(30);
        s.setValues(40, 5, 0, 100);
        s.setMinimum(5);
        s.setMaximum(90);
        s.setVisibleAmount(20);

        assertEquals(List.of(), events);
    }

    @Test
    @DisplayName("drag-end delivers the non-adjusting event the commit idiom needs")
    void dragEndDeliversTheNonAdjustingEventTheCommitIdiomNeeds() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100);
        show(s);
        List<Boolean> adjusting = new ArrayList<>();
        s.addAdjustmentListener(e -> adjusting.add(e.getValueIsAdjusting()));

        LocatorJ._setValue(peer(), 42.0);
        fireDomChange(s);

        // The browser's `change` carries no value, so it produces no Vaadin
        // value-change event — without the emulator's own DOM hook the
        // "commit when isAdjusting goes false" idiom would never fire.
        assertEquals(List.of(true, false), adjusting);
        assertFalse(s.getValueIsAdjusting());
    }

    @Test
    @DisplayName("a browser drag runs the public setters before posting, as WScrollbarPeer does")
    void aBrowserDragRunsThePublicSettersBeforePosting() {
        // Rule 4 of the emulator-owned-state rollout: the drag step is
        // WScrollbarPeer.postAdjustmentEvent (setValueIsAdjusting, setValue,
        // post), the drag end its dragEnd (setValueIsAdjusting(false), post —
        // no setValue).
        List<String> calls = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100) {
            @Override
            public void setValue(int value) {
                calls.add("setValue(" + value + ")");
                super.setValue(value);
            }

            @Override
            public void setValueIsAdjusting(boolean b) {
                calls.add("setValueIsAdjusting(" + b + ")");
                super.setValueIsAdjusting(b);
            }
        };
        show(s);
        s.addAdjustmentListener(e -> calls.add("listener(" + e.getValue() + "," + e.getValueIsAdjusting()
                + ") sees " + s.getValue() + "/" + s.getValueIsAdjusting()));

        LocatorJ._setValue(peer(), 42.0);
        fireDomChange(s);

        assertEquals(List.of("setValueIsAdjusting(true)", "setValue(42)", "listener(42,true) sees 42/true",
                "setValueIsAdjusting(false)", "listener(42,false) sees 42/false"), calls);
    }

    @Test
    @DisplayName("processEvent is the entry hop, processAdjustmentEvent the dispatch funnel")
    void processEventIsTheEntryHopProcessAdjustmentEventTheDispatchFunnel() {
        // R_no_vaadin_in_api limb 2 / D_awt_dead_hooks: the bridge must enter at processEvent, or a
        // migrator's override compiles, looks wired, and never runs.
        List<String> hops = new ArrayList<>();
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 100) {
            @Override
            protected void processEvent(java.awt.AWTEvent e) {
                hops.add("processEvent");
                super.processEvent(e);
            }

            @Override
            protected void processAdjustmentEvent(AdjustmentEvent e) {
                hops.add("processAdjustmentEvent");
                super.processAdjustmentEvent(e);
            }
        };
        show(s);
        s.addAdjustmentListener(e -> hops.add("listener"));

        LocatorJ._setValue(peer(), 15.0);

        assertEquals(List.of("processEvent", "processAdjustmentEvent", "listener"), hops);
    }

    @Test
    @DisplayName("processAdjustmentEvent null is a no-op")
    void processAdjustmentEventNullIsANoOp() {
        Scrollbar s = new Scrollbar();
        s.addAdjustmentListener(e -> {
            throw new AssertionError("must not dispatch");
        });
        s.processAdjustmentEvent(null);
    }

    @Test
    @DisplayName("listener add and remove no-op on null")
    void listenerAddAndRemoveNoOpOnNull() {
        Scrollbar s = new Scrollbar();
        s.addAdjustmentListener(null);
        s.removeAdjustmentListener(null);
        assertEquals(0, s.getAdjustmentListeners().length);
    }

    @Test
    @DisplayName("getAdjustmentListeners reports registration order and honours remove")
    void getAdjustmentListenersReportsRegistrationOrderAndHonoursRemove() {
        Scrollbar s = new Scrollbar();
        AdjustmentListener first = e -> {
        };
        AdjustmentListener second = e -> {
        };
        s.addAdjustmentListener(first);
        s.addAdjustmentListener(second);
        // AWT dispatches first-registered-first (D_awt_dead_hooks).
        assertEquals(List.of(first, second), Arrays.asList(s.getAdjustmentListeners()));
        assertEquals(List.of(first, second), Arrays.asList(s.getListeners(AdjustmentListener.class)));
        s.removeAdjustmentListener(first);
        assertSame(second, assertSingle(s.getAdjustmentListeners()));
    }

    // --- valueIsAdjusting -----------------------------------------------

    @Test
    @DisplayName("setValueIsAdjusting round-trips through the peer")
    void setValueIsAdjustingRoundTripsThroughThePeer() {
        Scrollbar s = new Scrollbar();
        s.setValueIsAdjusting(true);
        assertTrue(s.getValueIsAdjusting());
        assertTrue(((SScrollbar) s.getPeer()).getValueIsAdjusting());
    }

    // --- inherited Component surface ------------------------------------

    @Test
    @DisplayName("it is an AWT Component with no JComponent in the chain")
    void itIsAnAwtComponentWithNoJComponentInTheChain() {
        Object s = new Scrollbar();
        assertInstanceOf(Component.class, s);
        // A java.awt leaf, so no JComponent surface (no border, no tooltip PCE,
        // no client properties) — checked reflectively because the compiler
        // already knows the cast is impossible.
        assertFalse(JComponent.class.isInstance(s));
        // Adjustable is the interface the AdjustmentEvent source demands.
        assertInstanceOf(Adjustable.class, s);
    }

    @Test
    @DisplayName("it drops into a Swing container hierarchy")
    void itDropsIntoASwingContainerHierarchy() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL);
        JPanel panel = new JPanel();
        panel.add(s);
        JFrame frame = new JFrame();
        frame.add(panel);
        assertSame(panel, s.getParent());
        assertSame(frame, panel.getTopLevelAncestor());
    }

    @Test
    @DisplayName("paramString carries AWT's tail with no equals on the orientation")
    void paramStringCarriesAwtsTailWithNoEqualsOnTheOrientation() {
        Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 30, 5, 10, 60);
        String str = s.paramString();
        assertTrue(str.endsWith(",val=30,vis=5,min=10,max=60,horz,isAdjusting=false"), str);
        assertTrue(new Scrollbar().paramString().endsWith(",vert,isAdjusting=false"));
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            assertNull(new Scrollbar().getAccessibleContext());
            assertTrue(assertSingle(warns).contains("getAccessibleContext"));
        } finally {
            EHelper.warnHook = msg -> {
            };
        }
    }

    // --- Exit gate ------------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 0, 10, 0, 255);
            show(s);
            s.addAdjustmentListener(e -> {
            });
            LocatorJ._setValue(peer(), 128.0);
            fireDomChange(s);
            s.setValue(200);
            s.setVisibleAmount(20);
            s.setMinimum(10);
            s.setMaximum(300);
            s.setUnitIncrement(4);
            s.setBlockIncrement(40);
            s.setOrientation(Scrollbar.VERTICAL);
            s.setValueIsAdjusting(false);
            s.addNotify();
            s.paramString();
            assertNoWarns(warns, "no stub WARNs expected from Scrollbar's happy path; got " + warns);
        } finally {
            EHelper.warnHook = msg -> {
            };
        }
    }

    /** Dispatch the browser's own drag-end {@code change}, exactly as Flow would. */
    private void fireDomChange(Scrollbar s) {
        Element element = s.getPeer().getElement();
        element.getNode()
                .getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(element, "change",
                        tools.jackson.databind.node.JsonNodeFactory.instance.objectNode()));
    }
}
