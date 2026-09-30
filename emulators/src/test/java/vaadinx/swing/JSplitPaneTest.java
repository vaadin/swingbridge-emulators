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
import com.vaadin.swingbridge.surrogates.SJSplitPane;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for vaadinx.swing.JSplitPane (D_jsplitpane). Pairs
 * with SJSplitPaneTest — this layer covers the thin shell's forwarding
 * behaviour and the emulator-only field shadows:
 *
 * <ol>
 *  <li><b>Ctor variants</b> — all five converge on the
 *     (orientation, continuousLayout, left, right) root; SJSplitPane
 *     peer always installed; orientation validation bubbles up.
 *  <li><b>Slot setters</b> — setLeft/setTop/setRight/setBottom field-shadow
 *     the emulator-side vaadinx.awt.Component refs and route through
 *     the peer's slot setters.
 *  <li><b>Emulator-only field shadows</b> — dividerLocation int,
 *     lastDividerLocation, resizeWeight, oneTouchExpandable,
 *     continuousLayout, dividerSize all round-trip + fire PCE without
 *     touching the peer.
 *  <li><b>setDividerLocation(double)</b> — forwards to peer's percent API.
 *  <li><b>resetToPreferredSizes</b> — forwards + resets shadow + fires PCE.
 *  <li><b>addImpl constraint dispatch</b> — LEFT/TOP → left, RIGHT/BOTTOM →
 *     right, DIVIDER and unknown → WARN.
 *  <li><b>L&amp;F surface</b> — getUIClassID == "SplitPaneUI"; getUI/setUI WARN.
 *  <li><b>paramString chain</b> — JDK-faithful tail.
 * </ol>
 */
class JSplitPaneTest extends AbstractKaribuTest {

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

    private static SJSplitPane peerOf(JSplitPane sp) {
        return (SJSplitPane) sp.getPeer();
    }

    // --- Constructors -------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor wires SJSplitPane peer with HORIZONTAL_SPLIT default")
    void noArgCtorWiresPeerWithHorizontalDefault() {
        JSplitPane sp = new JSplitPane();
        assertInstanceOf(SJSplitPane.class, sp.getPeer());
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, sp.getOrientation());
        assertEquals(-1, sp.getDividerLocation(), "JDK sentinel before any set");
        assertEquals(0.0, sp.getResizeWeight(), 0.001, "JDK default");
        assertFalse(sp.isOneTouchExpandable());
        assertFalse(sp.isContinuousLayout());
        assertNoWarns(capturedWarns, "default ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("int-arg ctor accepts VERTICAL_SPLIT")
    void intArgCtorAcceptsVerticalSplit() {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        assertEquals(JSplitPane.VERTICAL_SPLIT, sp.getOrientation());
        assertEquals(JSplitPane.VERTICAL_SPLIT, peerOf(sp).getOrientationAsInt());
    }

    @Test
    @DisplayName("(int, boolean) ctor passes continuousLayout to field shadow")
    void intBooleanCtorPassesContinuousLayout() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
        assertTrue(sp.isContinuousLayout());
    }

    @Test
    @DisplayName("(int, Component, Component) ctor installs both slot occupants")
    void intComponentComponentCtorInstallsBothSlots() {
        JButton left = new JButton("left");
        JButton right = new JButton("right");
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        assertSame(left, sp.getLeftComponent());
        assertSame(right, sp.getRightComponent());
        assertSame(left.getPeer(), peerOf(sp).getPrimaryComponent());
        assertSame(right.getPeer(), peerOf(sp).getSecondaryComponent());
    }

    @Test
    @DisplayName("full ctor sets continuousLayout + both slots")
    void fullCtorSetsContinuousLayoutAndBothSlots() {
        JButton left = new JButton("left");
        JButton right = new JButton("right");
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, left, right);
        assertEquals(JSplitPane.VERTICAL_SPLIT, sp.getOrientation());
        assertTrue(sp.isContinuousLayout());
        assertSame(left, sp.getLeftComponent());
        assertSame(right, sp.getRightComponent());
    }

    @Test
    @DisplayName("int-arg ctor throws IAE on garbage orientation (propagates from peer)")
    void intArgCtorThrowsOnGarbageOrientation() {
        assertThrows(IllegalArgumentException.class, () -> new JSplitPane(42));
    }

    // --- Orientation forwarding --------------------------------------------

    @Test
    @DisplayName("setOrientation forwards to peer")
    void setOrientationForwardsToPeer() {
        JSplitPane sp = new JSplitPane();
        sp.setOrientation(JSplitPane.VERTICAL_SPLIT);
        assertEquals(JSplitPane.VERTICAL_SPLIT, peerOf(sp).getOrientationAsInt());
    }

    // --- Slot setters / getters --------------------------------------------

    @Test
    @DisplayName("setLeftComponent field-shadows emulator ref and forwards to peer")
    void setLeftComponentShadowsAndForwards() {
        JSplitPane sp = new JSplitPane();
        JButton b = new JButton("left");
        sp.setLeftComponent(b);
        assertSame(b, sp.getLeftComponent());
        assertSame(b.getPeer(), peerOf(sp).getPrimaryComponent());
    }

    @Test
    @DisplayName("setTopComponent is an alias for setLeftComponent")
    void setTopComponentIsAnAliasForLeft() {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JButton b = new JButton("top");
        sp.setTopComponent(b);
        assertSame(b, sp.getTopComponent());
        assertSame(b, sp.getLeftComponent());
    }

    @Test
    @DisplayName("setRightComponent field-shadows and forwards to peer secondary")
    void setRightComponentShadowsAndForwards() {
        JSplitPane sp = new JSplitPane();
        JButton b = new JButton("right");
        sp.setRightComponent(b);
        assertSame(b, sp.getRightComponent());
        assertSame(b.getPeer(), peerOf(sp).getSecondaryComponent());
    }

    @Test
    @DisplayName("setLeftComponent null clears emulator ref and peer slot")
    void setLeftComponentNullClearsBoth() {
        JSplitPane sp = new JSplitPane();
        sp.setLeftComponent(new JButton("a"));
        sp.setLeftComponent(null);
        assertNull(sp.getLeftComponent());
        assertNull(peerOf(sp).getPrimaryComponent());
    }

    // --- Container.components tree shape (R_swing_is_truth fidelity, D_jsplitpane_slot_tree_shape) ----------

    @Test
    @DisplayName("slot setters populate Container.components in JDK order")
    void slotSettersPopulateComponentsInJdkOrder() {
        JSplitPane sp = new JSplitPane();
        JButton left = new JButton("left");
        JButton right = new JButton("right");
        sp.setLeftComponent(left);
        sp.setRightComponent(right);
        assertEquals(2, sp.getComponentCount(),
                "Both slot occupants enter Container.components per JDK tree shape (D_jsplitpane_slot_tree_shape)");
        assertSame(left, sp.getComponent(0));
        assertSame(right, sp.getComponent(1));
        assertSame(sp, left.getParent());
        assertSame(sp, right.getParent());
    }

    @Test
    @DisplayName("ctor with both slots pre-populates Container.components")
    void ctorWithBothSlotsPrePopulatesComponents() {
        JButton left = new JButton("L");
        JButton right = new JButton("R");
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        assertEquals(2, sp.getComponentCount());
        assertSame(left, sp.getComponent(0));
        assertSame(right, sp.getComponent(1));
    }

    @Test
    @DisplayName("setLeftComponent replace detaches old and appends new (matches JDK addImpl order)")
    void setLeftComponentReplaceDetachesAndAppends() {
        JSplitPane sp = new JSplitPane();
        JButton origLeft = new JButton("L1");
        JButton right = new JButton("R");
        JButton newLeft = new JButton("L2");
        sp.setLeftComponent(origLeft);
        sp.setRightComponent(right);
        sp.setLeftComponent(newLeft);
        assertEquals(2, sp.getComponentCount());
        assertNull(origLeft.getParent(), "replaced left component detached");
        // JDK addImpl removes the old slot occupant then super.addImpl
        // appends → order becomes [right, newLeft], not [newLeft, right].
        assertSame(right, sp.getComponent(0));
        assertSame(newLeft, sp.getComponent(1));
    }

    @Test
    @DisplayName("setLeftComponent null detaches without re-adding")
    void setLeftComponentNullDetachesWithoutReAdding() {
        JSplitPane sp = new JSplitPane();
        JButton left = new JButton("L");
        sp.setLeftComponent(left);
        sp.setLeftComponent(null);
        assertEquals(0, sp.getComponentCount());
        assertNull(left.getParent());
    }

    @Test
    @DisplayName("addImpl with LEFT constraint maintains Container.components")
    void addImplWithLeftConstraintMaintainsComponents() {
        JSplitPane sp = new JSplitPane();
        JButton left = new JButton("L");
        sp.add(left, JSplitPane.LEFT);
        assertEquals(1, sp.getComponentCount());
        assertSame(left, sp.getComponent(0));
        assertSame(sp, left.getParent());
    }

    // --- Emulator-only field shadows ---------------------------------------

    @Test
    @DisplayName("setDividerLocation(int) round-trips on emulator + fires PCE, no peer touch")
    void setDividerLocationIntRoundTripsAndFiresPce() {
        JSplitPane sp = new JSplitPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        sp.addPropertyChangeListener("dividerLocation", events::add);

        sp.setDividerLocation(240);
        assertEquals(240, sp.getDividerLocation());
        PropertyChangeEvent e = assertSingle(events);
        assertEquals(-1, e.getOldValue());
        assertEquals(240, e.getNewValue());

        // No peer touch — peer's splitter position stays at Vaadin's default.
        assertNull(peerOf(sp).getSplitterPosition(), "peer untouched by int-pixel form");

        // Peer-side WARN should NOT fire — emulator overrides the surrogate
        // setter (we go directly to the field, never reach for peer's drop-WARN).
        assertNoWarns(capturedWarns,
                "emulator's setDividerLocation(int) is round-trip only, zero WARN: " + capturedWarns);
    }

    @Test
    @DisplayName("setDividerLocation(int) pushes prior to lastDividerLocation")
    void setDividerLocationPushesPriorToLast() {
        JSplitPane sp = new JSplitPane();
        sp.setDividerLocation(100);
        sp.setDividerLocation(200);
        assertEquals(100, sp.getLastDividerLocation(), "JDK semantic: previous → last on change");
        assertEquals(200, sp.getDividerLocation());
    }

    @Test
    @DisplayName("setResizeWeight round-trips + PCE (no peer touch)")
    void setResizeWeightRoundTripsAndFiresPce() {
        JSplitPane sp = new JSplitPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        sp.addPropertyChangeListener("resizeWeight", events::add);

        sp.setResizeWeight(0.45);  // JLawyer-shape testbed's inner-split value
        assertEquals(0.45, sp.getResizeWeight(), 0.001);
        PropertyChangeEvent e = assertSingle(events);
        assertEquals(0.0, e.getOldValue());
        assertEquals(0.45, e.getNewValue());

        assertNoWarns(capturedWarns,
                "setResizeWeight is emulator-only field shadow, zero WARN: " + capturedWarns);
    }

    @Test
    @DisplayName("setResizeWeight out-of-range throws IAE")
    void setResizeWeightOutOfRangeThrows() {
        JSplitPane sp = new JSplitPane();
        assertThrows(IllegalArgumentException.class, () -> sp.setResizeWeight(-0.1));
        assertThrows(IllegalArgumentException.class, () -> sp.setResizeWeight(1.1));
    }

    @Test
    @DisplayName("setOneTouchExpandable round-trips + PCE")
    void setOneTouchExpandableRoundTrips() {
        JSplitPane sp = new JSplitPane();
        sp.setOneTouchExpandable(true);
        assertTrue(sp.isOneTouchExpandable());
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("setContinuousLayout round-trips + PCE")
    void setContinuousLayoutRoundTrips() {
        JSplitPane sp = new JSplitPane();
        sp.setContinuousLayout(true);
        assertTrue(sp.isContinuousLayout());
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("setDividerSize round-trips + PCE")
    void setDividerSizeRoundTrips() {
        JSplitPane sp = new JSplitPane();
        sp.setDividerSize(12);
        assertEquals(12, sp.getDividerSize());
        assertNoWarns(capturedWarns);
    }

    @Test
    @DisplayName("setLastDividerLocation round-trips + PCE")
    void setLastDividerLocationRoundTrips() {
        JSplitPane sp = new JSplitPane();
        sp.setLastDividerLocation(150);
        assertEquals(150, sp.getLastDividerLocation());
        assertNoWarns(capturedWarns);
    }

    // --- setDividerLocation(double) forwards to peer -----------------------

    @Test
    @DisplayName("setDividerLocation(double) forwards to peer setSplitterPosition")
    void setDividerLocationDoubleForwardsToPeer() {
        JSplitPane sp = new JSplitPane();
        sp.setDividerLocation(0.4);
        assertEquals(40.0, peerOf(sp).getSplitterPosition(), 0.001);
        // No PCE — emulator's setDividerLocation(double) doesn't update
        // the int field (different semantic per D_jsplitpane).
        assertEquals(-1, sp.getDividerLocation(), "double form doesn't pre-compute int");
    }

    // --- resetToPreferredSizes ---------------------------------------------

    @Test
    @DisplayName("resetToPreferredSizes snaps peer to 50 + resets shadow + fires PCE")
    void resetToPreferredSizesSnapsAndResets() {
        JSplitPane sp = new JSplitPane();
        sp.setDividerLocation(200);  // prime the shadow

        List<PropertyChangeEvent> events = new ArrayList<>();
        sp.addPropertyChangeListener("dividerLocation", events::add);

        sp.resetToPreferredSizes();

        assertEquals(50.0, peerOf(sp).getSplitterPosition(), 0.001);
        assertEquals(-1, sp.getDividerLocation(), "shadow reset to sentinel");
        PropertyChangeEvent e = assertSingle(events);  // PCE fires on shadow reset
        assertEquals(200, e.getOldValue());
        assertEquals(-1, e.getNewValue());
    }

    @Test
    @DisplayName("resetToPreferredSizes on fresh splitter doesn't fire PCE")
    void resetToPreferredSizesOnFreshSplitterFiresNoPce() {
        JSplitPane sp = new JSplitPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        sp.addPropertyChangeListener("dividerLocation", events::add);

        sp.resetToPreferredSizes();  // shadow was -1 → stays -1, no PCE
        assertTrue(events.isEmpty(), "no PCE when shadow was already at sentinel");
    }

    // --- addImpl constraint dispatch ---------------------------------------

    @Test
    @DisplayName("add(c, LEFT) dispatches to setLeftComponent")
    void addWithLeftDispatchesToSetLeftComponent() {
        JSplitPane sp = new JSplitPane();
        JButton b = new JButton("left");
        sp.add(b, JSplitPane.LEFT);
        assertSame(b, sp.getLeftComponent());
    }

    @Test
    @DisplayName("add(c, TOP) dispatches to setLeftComponent (alias)")
    void addWithTopDispatchesToSetLeftComponent() {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JButton b = new JButton("top");
        sp.add(b, JSplitPane.TOP);
        assertSame(b, sp.getLeftComponent());
    }

    @Test
    @DisplayName("add(c, RIGHT) dispatches to setRightComponent")
    void addWithRightDispatchesToSetRightComponent() {
        JSplitPane sp = new JSplitPane();
        JButton b = new JButton("right");
        sp.add(b, JSplitPane.RIGHT);
        assertSame(b, sp.getRightComponent());
    }

    @Test
    @DisplayName("add(c, BOTTOM) dispatches to setRightComponent (alias)")
    void addWithBottomDispatchesToSetRightComponent() {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JButton b = new JButton("bottom");
        sp.add(b, JSplitPane.BOTTOM);
        assertSame(b, sp.getRightComponent());
    }

    @Test
    @DisplayName("add(c, DIVIDER) WARNs")
    void addWithDividerWarns() {
        JSplitPane sp = new JSplitPane();
        sp.add(new JButton("d"), JSplitPane.DIVIDER);
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("DIVIDER")),
                "DIVIDER constraint should WARN — Vaadin owns the splitter: " + capturedWarns);
    }

    @Test
    @DisplayName("add(c) with null constraint slots into first empty position")
    void addWithNullConstraintSlotsIntoFirstEmptyPosition() {
        JSplitPane sp = new JSplitPane();
        JButton a = new JButton("a");
        JButton b = new JButton("b");
        sp.add(a);
        sp.add(b);
        assertSame(a, sp.getLeftComponent());
        assertSame(b, sp.getRightComponent());
        assertNoWarns(capturedWarns,
                "first two null-constraint adds should not WARN: " + capturedWarns);
    }

    @Test
    @DisplayName("add(c) with both slots full WARNs")
    void addWithBothSlotsFullWarns() {
        JSplitPane sp = new JSplitPane();
        sp.add(new JButton("a"));
        sp.add(new JButton("b"));
        sp.add(new JButton("overflow"));
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("no-slot-available")));
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID returns SplitPaneUI")
    void getUiClassIdReturnsSplitPaneUi() {
        assertEquals("SplitPaneUI", new JSplitPane().getUIClassID());
    }

    @Test
    @DisplayName("getUI WARNs (no UI delegate)")
    void getUiWarns() {
        new JSplitPane().getUI();
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("getUI")));
    }

    @Test
    @DisplayName("setUI WARNs (L&F surgery not modelled)")
    void setUiWarns() {
        new JSplitPane().setUI(null);
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("setUI")));
    }

    @Test
    @DisplayName("getMinimumDividerLocation WARNs and returns -1")
    void getMinimumDividerLocationWarns() {
        assertEquals(-1, new JSplitPane().getMinimumDividerLocation());
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("getMinimumDividerLocation")));
    }

    @Test
    @DisplayName("paramString chains tail with split-pane-specific fields")
    void paramStringChainsTailWithSplitPaneFields() {
        JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        sp.setDividerLocation(150);
        sp.setOneTouchExpandable(true);
        sp.setResizeWeight(0.3);
        String ps = sp.paramString();
        assertTrue(ps.contains("dividerLocation=150"));
        assertTrue(ps.contains("oneTouchExpandable=true"));
        assertTrue(ps.contains("orientation=VERTICAL_SPLIT"));
        assertTrue(ps.contains("resizeWeight=0.3"));
    }
}
