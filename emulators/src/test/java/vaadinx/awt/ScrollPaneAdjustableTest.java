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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.AWTError;
import java.awt.Adjustable;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for {@code vaadinx.awt.ScrollPaneAdjustable} — the per-axis value model
 * {@code ScrollPane} hands out. Ported rather than JDK-reused because the JDK class
 * is {@code final} with a package-private ctor. Covers:
 *
 * <ol>
 *  <li>The three {@code AWTError} setters, message verbatim, and {@code getMinimum}'s
 *      hard zero.
 *  <li>JDK field defaults on a fresh instance.
 *  <li>{@code setValue} firing exactly once on change and never on a no-change re-set.
 *  <li>The no-clamp divergence, asserted directly rather than inferred.
 *  <li>{@code setValueIsAdjusting} firing on flip only, and surviving an empty
 *      listener chain — we have no {@code PeerFixer}, so the chain starts null.
 *  <li>{@code AWTEventMulticaster} add / remove / null-no-op.
 *  <li>{@code paramString}'s exact JDK format.
 * </ol>
 *
 * <p>See D_awt_scrollpane.
 */
class ScrollPaneAdjustableTest extends AbstractKaribuTest {

    /** A pane with a child, so {@code setScrollPosition} is legal where used. */
    private ScrollPane paneWithChild() {
        ScrollPane pane = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
        pane.add(new Panel());
        return pane;
    }

    private ScrollPaneAdjustable vAdj() {
        return (ScrollPaneAdjustable) paneWithChild().getVAdjustable();
    }

    // --- The range is not yours to set ---------------------------------

    @Test
    @DisplayName("setMinimum setMaximum setVisibleAmount all throw AWTError verbatim")
    void setMinimumSetMaximumSetVisibleAmountAllThrowAwtErrorVerbatim() {
        ScrollPaneAdjustable a = vAdj();
        List<Consumer<ScrollPaneAdjustable>> ops = List.of(
                it -> it.setMinimum(0),
                it -> it.setMaximum(100),
                it -> it.setVisibleAmount(50));
        for (Consumer<ScrollPaneAdjustable> op : ops) {
            AWTError e = assertThrows(AWTError.class, () -> op.accept(a));
            assertEquals("Can be set by scrollpane only", e.getMessage());
        }
    }

    @Test
    @DisplayName("getMinimum hard-returns zero")
    void getMinimumHardReturnsZero() {
        // The JDK's own comment says it ignores its own field here.
        assertEquals(0, vAdj().getMinimum());
    }

    // --- Defaults -------------------------------------------------------

    @Test
    @DisplayName("a fresh adjustable carries the JDK's field defaults")
    void aFreshAdjustableCarriesTheJdksFieldDefaults() {
        ScrollPaneAdjustable a = vAdj();
        assertEquals(0, a.getValue());
        assertEquals(0, a.getMinimum());
        // 0, NOT setSpan's floor of 1: setSpan only runs after a layout, and
        // an un-laid-out JDK pane answers 0 here too.
        assertEquals(0, a.getMaximum());
        assertEquals(0, a.getVisibleAmount());
        assertEquals(1, a.getUnitIncrement());
        assertEquals(1, a.getBlockIncrement());
        assertFalse(a.getValueIsAdjusting());
    }

    @Test
    @DisplayName("orientation is whatever the pane constructed it with")
    void orientationIsWhateverThePaneConstructedItWith() {
        ScrollPane pane = paneWithChild();
        assertEquals(Adjustable.VERTICAL, pane.getVAdjustable().getOrientation());
        assertEquals(Adjustable.HORIZONTAL, pane.getHAdjustable().getOrientation());
    }

    // --- setValue + events ---------------------------------------------

    @Test
    @DisplayName("setValue fires exactly one TRACK event, sourced at the adjustable")
    void setValueFiresExactlyOneTrackEventSourcedAtTheAdjustable() {
        ScrollPaneAdjustable a = vAdj();
        List<AdjustmentEvent> events = new ArrayList<>();
        a.addAdjustmentListener(events::add);
        a.setValue(200);
        AdjustmentEvent e = assertSingle(events);
        assertSame(a, e.getAdjustable(), "the JDK sources the event at the Adjustable, not the pane");
        assertEquals(AdjustmentEvent.ADJUSTMENT_VALUE_CHANGED, e.getID());
        assertEquals(AdjustmentEvent.TRACK, e.getAdjustmentType());
        assertEquals(200, e.getValue());
        assertFalse(e.getValueIsAdjusting());
    }

    @Test
    @DisplayName("re-setting the same value fires nothing")
    void reSettingTheSameValueFiresNothing() {
        ScrollPaneAdjustable a = vAdj();
        a.setValue(200);
        List<AdjustmentEvent> events = new ArrayList<>();
        a.addAdjustmentListener(events::add);
        a.setValue(200);
        assertTrue(events.isEmpty(), "the JDK fires only on a real change; got " + events);
    }

    // --- The no-clamp divergence ----------------------------------------

    @Test
    @DisplayName("setValue does not clamp — the deliberate divergence")
    void setValueDoesNotClampTheDeliberateDivergence() {
        // A faithful clamp is min(max(v, minimum), maximum - visibleAmount)
        // = min(max(v, 0), 0) = 0, forever, since setSpan never runs. That
        // would make setScrollPosition permanently dead. So we store verbatim
        // and let the browser clamp on arrival. See D_awt_scrollpane.
        ScrollPaneAdjustable a = vAdj();
        a.setValue(200);
        assertEquals(200, a.getValue());
        // And the inconsistency that buys, on the record rather than latent:
        assertEquals(0, a.getMaximum(),
                "value can exceed maximum here; the JDK's clamp makes that impossible");
    }

    // --- valueIsAdjusting ------------------------------------------------

    @Test
    @DisplayName("setValueIsAdjusting fires on flip only")
    void setValueIsAdjustingFiresOnFlipOnly() {
        ScrollPaneAdjustable a = vAdj();
        List<AdjustmentEvent> events = new ArrayList<>();
        a.addAdjustmentListener(events::add);
        a.setValueIsAdjusting(true);
        assertEquals(1, events.size());
        assertTrue(events.get(0).getValueIsAdjusting());
        a.setValueIsAdjusting(true);
        assertEquals(1, events.size(), "no flip, no event");
        a.setValueIsAdjusting(false);
        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("firing with an empty listener chain does not NPE")
    void firingWithAnEmptyListenerChainDoesNotNpe() {
        // The JDK's chain can never be null — its ctor adds the internal
        // PeerFixer. Ours starts empty, so every fire site null-guards.
        ScrollPaneAdjustable a = vAdj();
        a.setValue(200);
        a.setValueIsAdjusting(true);
        assertEquals(200, a.getValue());
        assertTrue(a.getValueIsAdjusting());
    }

    // --- Multicaster ------------------------------------------------------

    @Test
    @DisplayName("add remove and null-no-op through getAdjustmentListeners")
    void addRemoveAndNullNoOpThroughGetAdjustmentListeners() {
        ScrollPaneAdjustable a = vAdj();
        assertEquals(0, a.getAdjustmentListeners().length);
        AdjustmentListener l1 = e -> {
        };
        AdjustmentListener l2 = e -> {
        };
        a.addAdjustmentListener(l1);
        a.addAdjustmentListener(l2);
        assertEquals(2, a.getAdjustmentListeners().length);
        a.removeAdjustmentListener(l1);
        assertSame(l2, assertSingle(a.getAdjustmentListeners()));
        // Null is a documented no-op both ways, not an NPE.
        a.addAdjustmentListener(null);
        a.removeAdjustmentListener(null);
        assertEquals(1, a.getAdjustmentListeners().length);
    }

    // --- paramString ------------------------------------------------------

    @Test
    @DisplayName("paramString matches the JDK format exactly")
    void paramStringMatchesTheJdkFormatExactly() {
        ScrollPane pane = paneWithChild();
        ScrollPaneAdjustable v = (ScrollPaneAdjustable) pane.getVAdjustable();
        v.setValue(30);
        v.setUnitIncrement(4);
        v.setBlockIncrement(40);
        assertEquals("vertical,[0..0],val=30,vis=0,unit=4,block=40,isAdjusting=false", v.paramString());
        ScrollPaneAdjustable h = (ScrollPaneAdjustable) pane.getHAdjustable();
        assertTrue(h.paramString().startsWith("horizontal,"), h.paramString());
        assertTrue(v.toString().contains("vertical,"), v.toString());
    }
}
