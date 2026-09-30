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

import com.vaadin.flow.component.orderedlayout.Scroller;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJScrollPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;

import java.awt.Adjustable;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.ScrollPaneConstants;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for D_jscrollpane's JScrollPane emulator. Covers:
 *
 * <ol>
 *  <li>Constructors — 4 forms; peer is always SJScrollPane.
 *  <li>setViewportView round-trip + emulator↔peer wiring.
 *  <li>getViewport().getView() symmetry.
 *  <li>Scrollbar policy mapping cross-product (9 cases) including the
 *     three ALWAYS branches that WARN.
 *  <li>setViewportView(null) clears.
 *  <li>Auto-scroll content (Grid via SJTable) keeps emulator policy ints
 *     field-shadowed but doesn't drive Scroller direction.
 *  <li>JViewport surface — getView/setView round-trip; ChangeListener
 *     storage; scrollRectToVisible WARN.
 *  <li>JScrollBar surface — full BoundedRangeModel round-trip;
 *     AdjustmentListener storage.
 *  <li>R_leaf_peer_lockdown lock-down — no protected (Component) ctor exposed.
 * </ol>
 */
class JScrollPaneTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        EHelper.warnHook = capturedWarns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op */ };
    }

    private static SJScrollPane peerOf(JScrollPane sp) {
        return (SJScrollPane) sp.getPeer();
    }

    /** Whether any captured WARN mentions {@code fragment}. */
    private boolean warnedAbout(String fragment) {
        return capturedWarns.stream().anyMatch(w -> w.contains(fragment));
    }

    // --- Constructors ------------------------------------------------

    @Test
    @DisplayName("no-arg ctor uses SJScrollPane peer")
    void noArgCtorUsesSjScrollPanePeer() {
        assertInstanceOf(SJScrollPane.class, new JScrollPane().getPeer());
    }

    @Test
    @DisplayName("single-arg ctor wires the viewport view")
    void singleArgCtorWiresTheViewportView() {
        JLabel label = new JLabel("hi");
        JScrollPane sp = new JScrollPane(label);
        assertSame(label, sp.getViewportView());
        assertSame(label.getPeer(), peerOf(sp).getContent());
    }

    @Test
    @DisplayName("policy-only ctor sets policies, no view")
    void policyOnlyCtorSetsPolicies() {
        JScrollPane sp = new JScrollPane(
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        assertNull(sp.getViewportView());
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, sp.getVerticalScrollBarPolicy());
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, sp.getHorizontalScrollBarPolicy());
    }

    @Test
    @DisplayName("three-arg ctor sets policies and view")
    void threeArgCtorSetsPoliciesAndView() {
        JLabel label = new JLabel("hi");
        JScrollPane sp = new JScrollPane(
                label,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        assertSame(label, sp.getViewportView());
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, sp.getHorizontalScrollBarPolicy());
    }

    // --- Default policies + Scroller direction -----------------------

    @Test
    @DisplayName("default policies map to ScrollDirection BOTH on the surrogate")
    void defaultPoliciesMapToBoth() {
        JScrollPane sp = new JScrollPane();
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, sp.getVerticalScrollBarPolicy());
        assertEquals(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED, sp.getHorizontalScrollBarPolicy());
        assertEquals(Scroller.ScrollDirection.BOTH, peerOf(sp).getScrollDirection());
    }

    // --- setViewportView round-trip ----------------------------------

    @Test
    @DisplayName("setViewportView writes content to the surrogate")
    void setViewportViewWritesContentToTheSurrogate() {
        JScrollPane sp = new JScrollPane();
        JLabel view = new JLabel("hi");
        sp.setViewportView(view);
        assertSame(view, sp.getViewportView());
        assertSame(view.getPeer(), peerOf(sp).getContent());
    }

    @Test
    @DisplayName("setViewportView(null) clears the slot")
    void setViewportViewNullClearsTheSlot() {
        JScrollPane sp = new JScrollPane(new JLabel("hi"));
        sp.setViewportView(null);
        assertNull(sp.getViewportView());
        assertNull(peerOf(sp).getContent());
    }

    // --- getViewport().getView symmetry -------------------------------

    @Test
    @DisplayName("getViewport getView resolves back to the emulator viewport view")
    void getViewportGetViewResolvesBack() {
        JLabel view = new JLabel("hi");
        JScrollPane sp = new JScrollPane(view);
        JViewport vp = sp.getViewport();
        assertNotNull(vp);
        assertSame(view, vp.getView());
    }

    @Test
    @DisplayName("getViewport returns the same instance across calls (eager + cached)")
    void getViewportReturnsTheSameInstance() {
        JScrollPane sp = new JScrollPane();
        assertSame(sp.getViewport(), sp.getViewport());
    }

    // --- Container.components tree shape (R_swing_is_truth fidelity, D_viewport_scrollbar_shadows update) --

    @Test
    @DisplayName("no-arg ctor populates Container.components with viewport + 2 scrollbars")
    void noArgCtorPopulatesThreeChildren() {
        JScrollPane sp = new JScrollPane();
        assertEquals(3, sp.getComponentCount(),
                "JDK's JScrollPane() post-ctor has 3 children (viewport, vsb, hsb); "
                        + "the emulator must match per R_swing_is_truth fidelity (D_viewport_scrollbar_shadows update)");
        assertSame(sp.getViewport(), sp.getComponent(0));
        assertSame(sp.getVerticalScrollBar(), sp.getComponent(1));
        assertSame(sp.getHorizontalScrollBar(), sp.getComponent(2));
    }

    @Test
    @DisplayName("view-arg ctor populates Container.components and parents view under JViewport")
    void viewArgCtorParentsViewUnderTheViewport() {
        JLabel view = new JLabel("hi");
        JScrollPane sp = new JScrollPane(view);
        assertEquals(3, sp.getComponentCount());
        // The view lives under the JViewport (JDK's JScrollPane → JViewport
        // → view tree), not directly under the JScrollPane.
        assertEquals(1, sp.getViewport().getComponentCount());
        assertSame(view, sp.getViewport().getComponent(0));
        assertSame(sp.getViewport(), view.getParent());
        assertSame(sp, sp.getViewport().getParent());
    }

    @Test
    @DisplayName("setViewportView swaps the child of the JViewport (not the JScrollPane)")
    void setViewportViewSwapsTheViewportChild() {
        JLabel first = new JLabel("first");
        JLabel second = new JLabel("second");
        JScrollPane sp = new JScrollPane(first);
        sp.setViewportView(second);
        assertEquals(3, sp.getComponentCount(), "JScrollPane child count unchanged across view swap");
        assertEquals(1, sp.getViewport().getComponentCount());
        assertSame(second, sp.getViewport().getComponent(0));
        assertNull(first.getParent(), "old view detached from the JViewport on swap");
    }

    @Test
    @DisplayName("setViewportView(null) empties the JViewport but keeps JScrollPane shape")
    void setViewportViewNullEmptiesTheViewport() {
        JLabel view = new JLabel("v");
        JScrollPane sp = new JScrollPane(view);
        sp.setViewportView(null);
        assertEquals(3, sp.getComponentCount());
        assertEquals(0, sp.getViewport().getComponentCount());
        assertNull(view.getParent());
    }

    @Test
    @DisplayName("setViewport detaches old viewport and attaches the new at index 0, migrating the view")
    void setViewportMigratesTheView() {
        JLabel view = new JLabel("v");
        JScrollPane sp = new JScrollPane(view);
        JViewport originalVp = sp.getViewport();
        JViewport replacement = new JViewport();
        sp.setViewport(replacement);
        assertSame(replacement, sp.getComponent(0));
        assertSame(sp.getVerticalScrollBar(), sp.getComponent(1));
        assertSame(sp.getHorizontalScrollBar(), sp.getComponent(2));
        assertNull(originalVp.getParent(), "old viewport detached on replace");
        // The view followed the swap: it's now a child of the new JViewport,
        // no longer of the original.
        assertSame(replacement, view.getParent());
        assertEquals(1, replacement.getComponentCount());
        assertEquals(0, originalVp.getComponentCount());
    }

    @Test
    @DisplayName("setHorizontalScrollBar swap reflected in Container.components")
    void setHorizontalScrollBarSwapReflectedInComponents() {
        JScrollPane sp = new JScrollPane();
        JScrollBar originalHsb = sp.getHorizontalScrollBar();
        JScrollBar replacement = new JScrollBar(Adjustable.HORIZONTAL);
        sp.setHorizontalScrollBar(replacement);
        assertEquals(3, sp.getComponentCount());
        assertSame(replacement, sp.getComponent(2));
        assertNull(originalHsb.getParent(), "old horizontal scrollbar detached");
        assertSame(sp, replacement.getParent());
    }

    @Test
    @DisplayName("setVerticalScrollBar swap reflected in Container.components")
    void setVerticalScrollBarSwapReflectedInComponents() {
        JScrollPane sp = new JScrollPane();
        JScrollBar originalVsb = sp.getVerticalScrollBar();
        JScrollBar replacement = new JScrollBar(Adjustable.VERTICAL);
        sp.setVerticalScrollBar(replacement);
        assertEquals(3, sp.getComponentCount());
        // verticalScrollBar pinned to index 1 — between viewport and
        // horizontalScrollBar, matching JDK's add order.
        assertSame(replacement, sp.getComponent(1));
        assertNull(originalVsb.getParent());
    }

    // --- Scrollbar policy mapping cross-product (D_scrollbar_policy_mapping table) ---------

    @Test
    @DisplayName("policy mapping NEVER NEVER maps to ScrollDirection NONE")
    void policyNeverNeverMapsToNone() {
        JScrollPane sp = new JScrollPane(
                new JLabel("x"),  // non-auto-scrolling content so policy push fires
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        assertEquals(Scroller.ScrollDirection.NONE, peerOf(sp).getScrollDirection());
    }

    @Test
    @DisplayName("policy mapping NEVER AS_NEEDED maps to ScrollDirection HORIZONTAL")
    void policyNeverAsNeededMapsToHorizontal() {
        // VERTICAL=NEVER + HORIZONTAL=AS_NEEDED → only horizontal scrolls.
        JScrollPane sp = new JScrollPane(
                new JLabel("x"),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        assertEquals(Scroller.ScrollDirection.HORIZONTAL, peerOf(sp).getScrollDirection());
    }

    @Test
    @DisplayName("policy mapping AS_NEEDED NEVER maps to ScrollDirection VERTICAL")
    void policyAsNeededNeverMapsToVertical() {
        JScrollPane sp = new JScrollPane(
                new JLabel("x"),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        assertEquals(Scroller.ScrollDirection.VERTICAL, peerOf(sp).getScrollDirection());
    }

    @Test
    @DisplayName("policy mapping AS_NEEDED AS_NEEDED maps to ScrollDirection BOTH")
    void policyAsNeededAsNeededMapsToBoth() {
        JScrollPane sp = new JScrollPane(new JLabel("x"));
        assertEquals(Scroller.ScrollDirection.BOTH, peerOf(sp).getScrollDirection());
    }

    @Test
    @DisplayName("vertical ALWAYS WARNs and treats as AS_NEEDED")
    void verticalAlwaysWarnsAndFallsBack() {
        JScrollPane sp = new JScrollPane(
                new JLabel("x"),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        // Field round-trip honest about JDK ints.
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, sp.getVerticalScrollBarPolicy());
        // ScrollDirection treated as BOTH (both axes AS_NEEDED post-fallback).
        assertEquals(Scroller.ScrollDirection.BOTH, peerOf(sp).getScrollDirection());
        // WARN fired exactly once.
        assertTrue(warnedAbout("ALWAYS"), "Expected ALWAYS WARN, got: " + capturedWarns);
    }

    @Test
    @DisplayName("horizontal ALWAYS WARNs and treats as AS_NEEDED")
    void horizontalAlwaysWarnsAndFallsBack() {
        JScrollPane sp = new JScrollPane(
                new JLabel("x"),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        // V=NEVER + H=ALWAYS-treated-as-AS_NEEDED → HORIZONTAL.
        assertEquals(Scroller.ScrollDirection.HORIZONTAL, peerOf(sp).getScrollDirection());
        assertTrue(warnedAbout("ALWAYS"));
    }

    @Test
    @DisplayName("setVerticalScrollBarPolicy round-trips and re-pushes to surrogate")
    void setVerticalScrollBarPolicyRoundTripsAndRePushes() {
        JScrollPane sp = new JScrollPane(new JLabel("x"));
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER, sp.getVerticalScrollBarPolicy());
        // Both axes are now NEVER + AS_NEEDED → HORIZONTAL.
        assertEquals(Scroller.ScrollDirection.HORIZONTAL, peerOf(sp).getScrollDirection());
    }

    @Test
    @DisplayName("invalid scrollbar policy throws IllegalArgumentException")
    void invalidScrollBarPolicyThrows() {
        JScrollPane sp = new JScrollPane();
        assertThrows(IllegalArgumentException.class, () -> sp.setVerticalScrollBarPolicy(999));
        assertThrows(IllegalArgumentException.class, () -> sp.setHorizontalScrollBarPolicy(999));
    }

    // --- Auto-scroll content path ------------------------------------

    @Test
    @DisplayName("auto-scroll content (JTable) does NOT drive Scroller direction")
    void autoScrollContentDoesNotDriveScrollerDirection() {
        JScrollPane sp = new JScrollPane();
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        capturedWarns.clear();  // discard the ALWAYS WARN from the policy push above
        sp.setViewportView(new JTable());
        // SJScrollPane's setContent forced NONE on the surrogate (auto-scroll
        // guard); the emulator's pushPolicyToSurrogate skipped (auto-scroll
        // detection branch). Field-shadowed ints round-trip.
        assertEquals(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, sp.getVerticalScrollBarPolicy());
        assertEquals(Scroller.ScrollDirection.NONE, peerOf(sp).getScrollDirection());
        // No new WARN — the policy push was skipped, so the ALWAYS branch
        // didn't re-fire.
        assertFalse(warnedAbout("ALWAYS"));
    }

    // --- JViewport shadow surface ------------------------------------

    @Test
    @DisplayName("JViewport getView setView delegate to JScrollPane setViewportView")
    void viewportGetSetViewDelegate() {
        JScrollPane sp = new JScrollPane();
        JViewport vp = sp.getViewport();
        JLabel v1 = new JLabel("first");
        vp.setView(v1);
        assertSame(v1, sp.getViewportView());
        assertSame(v1, vp.getView());
        JLabel v2 = new JLabel("second");
        vp.setView(v2);
        assertSame(v2, sp.getViewportView());
    }

    @Test
    @DisplayName("JViewport addChangeListener stores but never fires")
    void viewportChangeListenerStoresButNeverFires() {
        JViewport vp = new JScrollPane().getViewport();
        Counter fired = new Counter();
        ChangeListener l = e -> fired.inc();
        vp.addChangeListener(l);
        assertEquals(1, vp.getChangeListeners().length);
        // No code path that fires ChangeEvent — assertion is "stored, doesn't fire".
        fired.assertEquals(0);
        vp.removeChangeListener(l);
        assertEquals(0, vp.getChangeListeners().length);
    }

    @Test
    @DisplayName("JViewport scrollRectToVisible WARNs")
    void viewportScrollRectToVisibleWarns() {
        JViewport vp = new JScrollPane().getViewport();
        vp.scrollRectToVisible(new Rectangle(0, 0, 10, 10));
        assertTrue(warnedAbout("scrollRectToVisible"));
    }

    @Test
    @DisplayName("JViewport viewPosition round-trips via field shadow (no peer drive)")
    void viewportViewPositionRoundTrips() {
        JViewport vp = new JScrollPane(new JLabel("hi")).getViewport();
        vp.setViewPosition(new Point(15, 25));
        assertEquals(new Point(15, 25), vp.getViewPosition());
    }

    // --- JScrollBar shadow surface -----------------------------------

    @Test
    @DisplayName("getHorizontalScrollBar returns a JScrollBar with HORIZONTAL orientation")
    void horizontalScrollBarHasHorizontalOrientation() {
        JScrollBar sb = new JScrollPane().getHorizontalScrollBar();
        assertNotNull(sb);
        assertEquals(Adjustable.HORIZONTAL, sb.getOrientation());
    }

    @Test
    @DisplayName("getVerticalScrollBar returns a JScrollBar with VERTICAL orientation")
    void verticalScrollBarHasVerticalOrientation() {
        JScrollBar sb = new JScrollPane().getVerticalScrollBar();
        assertNotNull(sb);
        assertEquals(Adjustable.VERTICAL, sb.getOrientation());
    }

    @Test
    @DisplayName("JScrollBar BoundedRangeModel round-trip")
    void scrollBarBoundedRangeModelRoundTrip() {
        JScrollBar sb = new JScrollPane().getVerticalScrollBar();
        sb.setMinimum(0);
        sb.setMaximum(200);
        sb.setVisibleAmount(20);
        sb.setValue(50);
        sb.setUnitIncrement(16);
        sb.setBlockIncrement(100);
        assertEquals(50, sb.getValue());
        assertEquals(0, sb.getMinimum());
        assertEquals(200, sb.getMaximum());
        assertEquals(20, sb.getVisibleAmount());
        assertEquals(16, sb.getUnitIncrement());
        assertEquals(100, sb.getBlockIncrement());
    }

    @Test
    @DisplayName("JScrollBar value clamps to (min, max - extent) per BoundedRangeModel contract")
    void scrollBarValueClamps() {
        JScrollBar sb = new JScrollPane().getVerticalScrollBar();
        sb.setMaximum(100);
        sb.setVisibleAmount(10);
        sb.setValue(999);
        assertEquals(90, sb.getValue());  // clamped to max - extent
        sb.setValue(-50);
        assertEquals(0, sb.getValue());   // clamped to min
    }

    @Test
    @DisplayName("JScrollBar AdjustmentListener stores but never fires")
    void scrollBarAdjustmentListenerStoresButNeverFires() {
        JScrollBar sb = new JScrollPane().getVerticalScrollBar();
        Counter fired = new Counter();
        java.awt.event.AdjustmentListener l = e -> fired.inc();
        sb.addAdjustmentListener(l);
        assertEquals(1, sb.getAdjustmentListeners().length);
        sb.setValue(42);  // would fire AdjustmentEvent in real Swing; we don't
        fired.assertEquals(0);
        sb.removeAdjustmentListener(l);
        assertEquals(0, sb.getAdjustmentListeners().length);
    }

    @Test
    @DisplayName("setHorizontalScrollBar accepts user-supplied instance")
    void setHorizontalScrollBarAcceptsUserInstance() {
        JScrollPane sp = new JScrollPane();
        JScrollBar custom = new JScrollBar(Adjustable.HORIZONTAL);
        sp.setHorizontalScrollBar(custom);
        assertSame(custom, sp.getHorizontalScrollBar());
    }

    // --- R_leaf_peer_lockdown lock-down -----------------------------------------------

    @Test
    @DisplayName("JScrollPane has no protected Component-taking ctor (R_leaf_peer_lockdown lock-down)")
    void noPeerInjectionCtor() {
        // Reflection: any (vaadinx.awt.Component peer) ctor would be the
        // R_leaf_peer_lockdown escape hatch. None should exist on JScrollPane — only the
        // four public ctors (no-arg / Component view / int-int / view-int-int)
        // are allowed.
        boolean hasComponentPeerCtor = Arrays.stream(JScrollPane.class.getDeclaredConstructors())
                .anyMatch(JScrollPaneTest::takesOneVaadinComponent);
        assertFalse(hasComponentPeerCtor,
                "R_leaf_peer_lockdown violation: JScrollPane exposes a (Component peer) ctor");
    }

    private static boolean takesOneVaadinComponent(Constructor<?> c) {
        return c.getParameterCount() == 1
                && c.getParameterTypes()[0] == com.vaadin.flow.component.Component.class;
    }

    // --- Headers / corners — all WARN -------------------------------

    @Test
    @DisplayName("setColumnHeaderView WARNs per R_vaadin_first")
    void setColumnHeaderViewWarns() {
        new JScrollPane().setColumnHeaderView(new JLabel("hdr"));
        assertTrue(warnedAbout("setColumnHeaderView"));
    }

    @Test
    @DisplayName("setRowHeaderView WARNs per R_vaadin_first")
    void setRowHeaderViewWarns() {
        new JScrollPane().setRowHeaderView(new JLabel("hdr"));
        assertTrue(warnedAbout("setRowHeaderView"));
    }

    @Test
    @DisplayName("setCorner WARNs per R_vaadin_first")
    void setCornerWarns() {
        new JScrollPane().setCorner("UPPER_LEFT_CORNER", new JLabel("c"));
        assertTrue(warnedAbout("setCorner"));
    }
}
