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

import com.vaadin.flow.component.orderedlayout.Scroller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SScrollPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import java.awt.AWTError;
import java.awt.Adjustable;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.AdjustmentEvent;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for the {@code vaadinx.awt.ScrollPane} emulator. Covers:
 *
 * <ol>
 *  <li>Ctor chaining, R_leaf_peer_lockdown peer lock-down through a user subclass.
 *  <li>The single-child rule, including AWT's remove-before-throw wart.
 *  <li>The deliberate {@code addToPanel} divergence — {@code getComponent(0)} is the child.
 *  <li>Every row of the exception table, messages verbatim.
 *  <li>{@code setScrollPosition} / {@code getScrollPosition} round-trip in one request.
 *  <li>The {@code doLayout()} -&gt; {@code layout()} reachability guard (R_no_vaadin_in_api limb 2).
 *  <li>Geometry answering the JDK's own un-realized values, WARN-free.
 *  <li>The {@code SCROLLBARS_ALWAYS == Adjustable.VERTICAL} constant trap.
 * </ol>
 *
 * <p>See D_awt_scrollpane / SD_sscrollpane.
 */
class ScrollPaneTest extends AbstractKaribuTest {

    private JFrame show(ScrollPane sp) {
        JFrame frame = new JFrame();
        frame.add(sp);
        frame.setVisible(true);
        return frame;
    }

    // --- Ctors + R_leaf_peer_lockdown ----------------------------------------------------

    @Test
    @DisplayName("no-arg ctor chains to AS_NEEDED")
    void noArgCtorChainsToAsNeeded() {
        assertEquals(ScrollPane.SCROLLBARS_AS_NEEDED, new ScrollPane().getScrollbarDisplayPolicy());
    }

    @Test
    @DisplayName("peer is an SScrollPane through a plain instance and a user subclass")
    void peerIsAnSScrollPaneThroughAPlainInstanceAndAUserSubclass() {
        assertInstanceOf(SScrollPane.class, new ScrollPane().getPeer());
        // R_leaf_peer_lockdown: the type-system seam a subclass could swap peers through is
        // closed — there is no protected (Component peer) ctor.
        class MyPane extends ScrollPane {
            MyPane() {
                super(ScrollPane.SCROLLBARS_NEVER);
            }
        }
        MyPane mine = new MyPane();
        SScrollPane peer = assertInstanceOf(SScrollPane.class, mine.getPeer());
        assertEquals(Scroller.ScrollDirection.NONE, peer.getScrollDirection());
    }

    @Test
    @DisplayName("an illegal policy throws out of super() leaving nothing half-built")
    void anIllegalPolicyThrowsOutOfSuperLeavingNothingHalfBuilt() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new ScrollPane(3));
        assertEquals("illegal scrollbar display policy", e.getMessage());
    }

    @Test
    @DisplayName("SCROLLBARS_ALWAYS is numerically Adjustable VERTICAL — the silent trap")
    void scrollbarsAlwaysIsNumericallyAdjustableVerticalTheSilentTrap() {
        // new ScrollPane(Adjustable.VERTICAL) compiles and means ALWAYS. The
        // safe direction is ScrollPaneConstants, whose 20-22 / 30-32 are
        // disjoint and therefore throw.
        assertEquals(ScrollPane.SCROLLBARS_ALWAYS, Adjustable.VERTICAL);
        assertEquals(ScrollPane.SCROLLBARS_AS_NEEDED, Adjustable.HORIZONTAL);
        assertThrows(IllegalArgumentException.class,
                () -> new ScrollPane(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS));
    }

    // --- Single child ----------------------------------------------------

    @Test
    @DisplayName("a second add evicts the first — count never exceeds one")
    void aSecondAddEvictsTheFirstCountNeverExceedsOne() {
        ScrollPane sp = new ScrollPane();
        Panel a = new Panel();
        Panel b = new Panel();
        sp.add(a);
        assertEquals(1, sp.getComponentCount());
        sp.add(b);
        assertEquals(1, sp.getComponentCount());
        assertSame(b, sp.getComponent(0));
        assertNull(a.getParent(), "the evicted child is detached");
    }

    @Test
    @DisplayName("add at index 1 removes the existing child and THEN throws")
    void addAtIndex1RemovesTheExistingChildAndThenThrows() {
        // AWT's body order, verbatim and observable: remove(0) runs before the
        // index check, so the pane is left empty by a call that threw.
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        assertEquals(1, sp.getComponentCount());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> sp.add(new Panel(), null, 1));
        assertEquals("position greater than 0", e.getMessage());
        assertEquals(0, sp.getComponentCount(), "the wart: the pane is left empty by a throwing call");
    }

    @Test
    @DisplayName("getComponent(0) is the child itself, not a wrapping Panel")
    void getComponent0IsTheChildItselfNotAWrappingPanel() {
        // The deliberate divergence from java.awt.ScrollPane.addImpl, which
        // routes a lightweight child through addToPanel and so answers a
        // java.awt.Panel here. More permissive than the JDK, where the
        // corresponding cast throws ClassCastException. See D_awt_scrollpane.
        ScrollPane sp = new ScrollPane();
        JPanel child = new JPanel();
        sp.add(child);
        assertSame(child, sp.getComponent(0));
        assertSame(sp, child.getParent());
        // And the peer really is in the Scroller's content slot.
        assertSame(child.getPeer(), ((SScrollPane) sp.getPeer()).getContent());
    }

    @Test
    @DisplayName("remove clears the peer content slot")
    void removeClearsThePeerContentSlot() {
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        sp.remove(0);
        assertEquals(0, sp.getComponentCount());
        assertNull(((SScrollPane) sp.getPeer()).getContent());
    }

    @Test
    @DisplayName("removeAll routes through the remove override and clears the slot")
    void removeAllRoutesThroughTheRemoveOverrideAndClearsTheSlot() {
        // Container.removeAll loops on remove(int), so virtual dispatch reaches
        // our override. Pinned because a future removeAll that unlinked
        // `components` directly would leave Scroller.getContent() dangling.
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        sp.removeAll();
        assertEquals(0, sp.getComponentCount());
        assertNull(((SScrollPane) sp.getPeer()).getContent());
    }

    @Test
    @DisplayName("remove out of range throws ArrayIndexOutOfBoundsException")
    void removeOutOfRangeThrowsArrayIndexOutOfBoundsException() {
        ScrollPane sp = new ScrollPane();
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> sp.remove(5));
    }

    @Test
    @DisplayName("adding the pane to itself throws AWT's message")
    void addingThePaneToItselfThrowsAwtsMessage() {
        ScrollPane sp = new ScrollPane();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> sp.add(sp));
        assertEquals("adding component to itself", e.getMessage());
    }

    // --- Scroll position --------------------------------------------------

    @Test
    @DisplayName("setScrollPosition round-trips within the same request")
    void setScrollPositionRoundTripsWithinTheSameRequest() {
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        sp.setScrollPosition(10, 200);
        assertEquals(new Point(10, 200), sp.getScrollPosition());
        sp.setScrollPosition(new Point(0, 0));
        assertEquals(new Point(0, 0), sp.getScrollPosition());
    }

    @Test
    @DisplayName("setScrollPosition fires up to two AdjustmentEvents")
    void setScrollPositionFiresUpToTwoAdjustmentEvents() {
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        java.util.List<AdjustmentEvent> events = new ArrayList<>();
        sp.getVAdjustable().addAdjustmentListener(events::add);
        sp.getHAdjustable().addAdjustmentListener(events::add);
        sp.setScrollPosition(10, 200);
        assertEquals(2, events.size());
        // Only the axis that actually changed fires on the next call.
        events.clear();
        sp.setScrollPosition(10, 300);
        assertEquals(300, assertSingle(events).getValue());
    }

    @Test
    @DisplayName("scroll position without a child NPEs with AWT's message")
    void scrollPositionWithoutAChildNpEsWithAwtsMessage() {
        ScrollPane sp = new ScrollPane();
        assertEquals("child is null",
                assertThrows(NullPointerException.class, sp::getScrollPosition).getMessage());
        assertEquals("child is null",
                assertThrows(NullPointerException.class, () -> sp.setScrollPosition(0, 0)).getMessage());
    }

    @Test
    @DisplayName("a null Point NPEs before the child check")
    void aNullPointNpEsBeforeTheChildCheck() {
        // p.x is dereferenced first, so this NPEs (without a message) even on
        // an empty pane. Order matters and is AWT's.
        ScrollPane sp = new ScrollPane();
        NullPointerException e = assertThrows(NullPointerException.class, () -> sp.setScrollPosition(null));
        assertTrue(e.getMessage() == null || !e.getMessage().contains("child is null"),
                "got: " + e.getMessage());
    }

    @Test
    @DisplayName("re-adding a child re-pushes the offset rather than losing it")
    void reAddingAChildRePushesTheOffsetRatherThanLosingIt() {
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        sp.setScrollPosition(0, 200);
        sp.add(new Panel());
        // Swapping content resets the browser's scrollTop; the adjustables
        // still hold the value and addImpl pushes it again.
        assertEquals(new Point(0, 200), sp.getScrollPosition());
    }

    // --- Layout is nailed shut ---------------------------------------------

    @Test
    @DisplayName("setLayout always throws AWTError, null included")
    void setLayoutAlwaysThrowsAwtErrorNullIncluded() {
        ScrollPane sp = new ScrollPane();
        assertEquals(
                "ScrollPane controls layout",
                assertThrows(AWTError.class, () -> sp.setLayout(new FlowLayout())).getMessage());
        assertThrows(AWTError.class, () -> sp.setLayout(null));
        assertNull(sp.getLayout());
    }

    @Test
    @DisplayName("doLayout reaches layout — the R_no_vaadin_in_api limb-2 guard")
    @SuppressWarnings("deprecation") // layout() is the AWT-1.0 hook whose reachability is the subject
    void doLayoutReachesLayoutTheRNoVaadinInApiLimb2Guard() {
        // The assertion that would have caught the dead hooks D_r12_provenance's sweep
        // found. Our Container.doLayout only falls through to layout() when
        // there is no LayoutManager, which is always true here — this pins the
        // hook live by construction rather than by that coincidence.
        Counter reached = new Counter();
        ScrollPane sp = new ScrollPane() {
            @Override
            public void layout() {
                reached.inc();
                super.layout();
            }
        };
        sp.doLayout();
        reached.assertEquals(1);
        // And through the real attach path, not only a direct call.
        show(sp);
        assertTrue(reached.get() >= 1);
    }

    // --- Geometry ------------------------------------------------------------

    @Test
    @DisplayName("geometry answers the JDK's own un-realized values and WARNs for none")
    void geometryAnswersTheJdksOwnUnRealizedValuesAndWarnsForNone() {
        java.util.List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            ScrollPane sp = new ScrollPane();
            // The JDK ctor seeds width = height = 100 and its insets are zero.
            assertEquals(new Dimension(100, 100), sp.getViewportSize());
            // The JDK returns 0 for both while peer == null.
            assertEquals(0, sp.getHScrollbarHeight());
            assertEquals(0, sp.getVScrollbarWidth());
            assertNoWarns(warns, "dummy geometry and JDK geometry agree here; got " + warns);
        } finally {
            EHelper.warnHook = msg -> {
            };
        }
    }

    // --- Adjustables ----------------------------------------------------------

    @Test
    @DisplayName("getVAdjustable is declared Adjustable but is a ScrollPaneAdjustable")
    void getVAdjustableIsDeclaredAdjustableButIsAScrollPaneAdjustable() {
        // The JDK deliberately declares the wider type "to maintain backward
        // compatibility"; the migrator's cast has to keep working.
        ScrollPane sp = new ScrollPane();
        Adjustable v = sp.getVAdjustable();
        assertInstanceOf(ScrollPaneAdjustable.class, v);
        assertEquals(Adjustable.VERTICAL, v.getOrientation());
        assertEquals(Adjustable.HORIZONTAL, sp.getHAdjustable().getOrientation());
        assertSame(sp.getVAdjustable(), sp.getVAdjustable(), "same instance every call, as in AWT");
    }

    // --- Wheel + misc ----------------------------------------------------------

    @Test
    @DisplayName("wheel scrolling defaults true and round-trips, WARNing only on false")
    void wheelScrollingDefaultsTrueAndRoundTripsWarningOnlyOnFalse() {
        java.util.List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            ScrollPane sp = new ScrollPane();
            assertTrue(sp.isWheelScrollingEnabled());
            sp.setWheelScrollingEnabled(false);
            assertFalse(sp.isWheelScrollingEnabled());
            assertTrue(assertSingle(warns).contains("setWheelScrollingEnabled"),
                    "false is R_match_swing_errors(c) drop-and-WARN; got " + warns);
            warns.clear();
            sp.setWheelScrollingEnabled(true);
            assertTrue(warns.isEmpty(), "true is free — the browser wheel-scrolls natively");
        } finally {
            EHelper.warnHook = msg -> {
            };
        }
    }

    @Test
    @DisplayName("paramString carries the JDK's four fields")
    void paramStringCarriesTheJdksFourFields() {
        ScrollPane sp = new ScrollPane();
        String empty = sp.paramString();
        assertTrue(empty.contains(",ScrollPosition=(0,0)"), empty);
        assertTrue(empty.contains(",ScrollbarDisplayPolicy=as-needed"), empty);
        assertTrue(empty.contains(",wheelScrollingEnabled=true"), empty);
        assertTrue(empty.contains(",Insets=(0,0,0,0)"), empty);
        sp.add(new Panel());
        sp.setScrollPosition(5, 15);
        assertTrue(sp.paramString().contains(",ScrollPosition=(5,15)"), sp.paramString());
        assertTrue(new ScrollPane(ScrollPane.SCROLLBARS_NEVER).paramString().contains("=never"));
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        java.util.List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            assertNull(new ScrollPane().getAccessibleContext());
            assertTrue(assertSingle(warns).contains("getAccessibleContext"));
        } finally {
            EHelper.warnHook = msg -> {
            };
        }
    }

    @Test
    @DisplayName("a ScrollPane nests inside a Swing JPanel inside a JFrame")
    void aScrollPaneNestsInsideASwingJPanelInsideAJFrame() {
        ScrollPane sp = new ScrollPane();
        sp.add(new Panel());
        JPanel panel = new JPanel();
        panel.add(sp);
        JFrame frame = new JFrame();
        frame.add(panel);
        frame.setVisible(true);
        assertSame(panel, sp.getParent());
        // Walk up: the AWT pane is a first-class citizen of the Swing tree.
        Component top = sp;
        while (top.getParent() != null) {
            top = top.getParent();
        }
        assertSame(frame, top);
    }

    // --- Exit gate --------------------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        java.util.List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            ScrollPane sp = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
            show(sp);
            sp.add(new Panel());
            sp.getVAdjustable().addAdjustmentListener(e -> {
            });
            sp.setScrollPosition(0, 200);
            sp.getScrollPosition();
            sp.getViewportSize();
            sp.getHScrollbarHeight();
            sp.getVScrollbarWidth();
            sp.getScrollbarDisplayPolicy();
            sp.getVAdjustable().setUnitIncrement(4);
            sp.getHAdjustable().setBlockIncrement(40);
            sp.isWheelScrollingEnabled();
            sp.doLayout();
            sp.addNotify();
            sp.paramString();
            sp.remove(0);
            assertNoWarns(warns, "no stub WARNs expected from ScrollPane's happy path; got " + warns);
        } finally {
            EHelper.warnHook = msg -> {
            };
        }
    }
}
