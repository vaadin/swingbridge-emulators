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

import com.vaadin.flow.component.progressbar.ProgressBar;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJProgressBar;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

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

class JProgressBarTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        // The emulator surface routes WARN-emitting calls through to the
        // surrogate (e.g. setBorderPainted false), so the surrogate-side
        // EHelper.warnHook needs to be installed alongside the emulator's.
        Consumer<String> sink = capturedWarns::add;
        EHelper.warnHook = sink;
        SHelper.warnHook = sink;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op default */ };
        SHelper.warnHook = msg -> { /* no-op default */ };
    }

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("default ctor seeds horizontal 0-100 with value 0")
    void defaultCtorSeedsHorizontalRange() {
        JProgressBar p = new JProgressBar();
        assertInstanceOf(ProgressBar.class, p.getPeer());
        assertInstanceOf(SJProgressBar.class, p.getPeer());
        assertEquals(SwingConstants.HORIZONTAL, p.getOrientation());
        assertEquals(0, p.getMinimum());
        assertEquals(100, p.getMaximum());
        // No-arg routes through (orient=HORIZONTAL, min=0, max=100) so
        // model value defaults to min=0.
        assertEquals(0, p.getValue());
        assertFalse(p.isIndeterminate());
        assertFalse(p.isStringPainted());
        assertTrue(p.isBorderPainted());
    }

    @Test
    @DisplayName("min/max ctor forwards bounds, value defaults to min (JDK)")
    void minMaxCtorForwardsBounds() {
        JProgressBar p = new JProgressBar(10, 250);
        assertEquals(10, p.getMinimum());
        assertEquals(250, p.getMaximum());
        assertEquals(10, p.getValue());
    }

    @Test
    @DisplayName("orientation/min/max ctor forwards all three")
    void orientationMinMaxCtorForwardsAll() {
        JProgressBar p = new JProgressBar(SwingConstants.VERTICAL, 5, 50);
        assertEquals(SwingConstants.VERTICAL, p.getOrientation());
        assertEquals(5, p.getMinimum());
        assertEquals(50, p.getMaximum());
        assertEquals(5, p.getValue());
    }

    @Test
    @DisplayName("an empty range reads as NaN percent, as in the JDK")
    void emptyRangeIsNaN() {
        // JDK 25, measured: getPercentComplete() divides by the zero span.
        JProgressBar p = new JProgressBar(5, 5);
        assertTrue(Double.isNaN(p.getPercentComplete()));
    }

    @Test
    @DisplayName("BoundedRangeModel ctor uses supplied model")
    void modelCtorUsesSuppliedModel() {
        DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(42, 0, 0, 200);
        JProgressBar p = new JProgressBar(model);
        assertSame(model, p.getModel());
        assertEquals(42, p.getValue());
    }

    // --- Model plumbing -------------------------------------------------

    @Test
    @DisplayName("setValue writes through the model and reaches the Vaadin peer")
    void setValueReachesThePeer() {
        JProgressBar p = new JProgressBar();
        p.setValue(75);
        assertEquals(75, p.getValue());
        assertEquals(75.0, ((ProgressBar) p.getPeer()).getValue());
    }

    @Test
    @DisplayName("setMinimum and setMaximum push element properties onto the peer")
    void boundsPushElementProperties() {
        JProgressBar p = new JProgressBar();
        p.setMinimum(10);
        p.setMaximum(200);
        ProgressBar pb = (ProgressBar) p.getPeer();
        assertEquals(10.0, pb.getElement().getProperty("min", 0.0));
        assertEquals(200.0, pb.getElement().getProperty("max", 0.0));
    }

    @Test
    @DisplayName("ChangeEvent source is the JProgressBar emulator, not the surrogate")
    void changeEventSourceIsTheEmulator() {
        // Migrated code casts `(JProgressBar) e.getSource()` — must see
        // the emulator. The bridge installed in the ctor re-sources.
        JProgressBar p = new JProgressBar();
        List<ChangeEvent> events = new ArrayList<>();
        p.addChangeListener(events::add);
        p.setValue(7);
        assertSame(p, assertSingle(events).getSource());
    }

    @Test
    @DisplayName("setValue to same value fires no ChangeEvent")
    void setValueToSameValueIsSilent() {
        JProgressBar p = new JProgressBar();    // seeded at 0
        Counter hits = new Counter();
        p.addChangeListener(e -> hits.inc());
        p.setValue(0);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("getPercentComplete reflects model state")
    void percentCompleteReflectsModel() {
        JProgressBar p = new JProgressBar(0, 200);
        p.setValue(50);
        assertEquals(0.25, p.getPercentComplete());
    }

    // --- Indeterminate --------------------------------------------------

    @Test
    @DisplayName("setIndeterminate reaches the peer property and fires PCE")
    void setIndeterminateReachesPeerAndFires() {
        JProgressBar p = new JProgressBar();
        List<PropertyChangeEvent> events = new ArrayList<>();
        p.addPropertyChangeListener("indeterminate", events::add);
        p.setIndeterminate(true);
        assertTrue(p.isIndeterminate());
        assertTrue(((ProgressBar) p.getPeer()).getElement().getProperty("indeterminate", false));
        assertEquals(1, events.size());
    }

    // --- Orientation (VERTICAL is R_vaadin_first drop-and-WARN) ---------------------

    @Test
    @DisplayName("setOrientation VERTICAL WARNs but the field round-trips")
    void verticalOrientationWarnsButRoundTrips() {
        JProgressBar p = new JProgressBar();
        p.setOrientation(SwingConstants.VERTICAL);
        assertEquals(SwingConstants.VERTICAL, p.getOrientation());
        String warn = assertSingle(capturedWarns);
        assertTrue(warn.contains("setOrientation"),
                "expected WARN about setOrientation, got: " + warn);
    }

    @Test
    @DisplayName("setOrientation throws IAE on invalid value (D_never_fail_on_gaps fail-fast)")
    void setOrientationRejectsGarbage() {
        JProgressBar p = new JProgressBar();
        assertThrows(IllegalArgumentException.class, () -> p.setOrientation(99));
    }

    // --- String / stringPainted ----------------------------------------

    @Test
    @DisplayName("setStringPainted with setString writes the data attribute on the peer")
    void stringPaintedWritesDataAttribute() {
        JProgressBar p = new JProgressBar();
        p.setString("Working…");
        p.setStringPainted(true);
        assertEquals("Working…",
                ((ProgressBar) p.getPeer()).getElement().getAttribute("data-emul-progress-string"));
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getString defaults to JDK percent format when no string is set")
    void stringDefaultsToPercent() {
        JProgressBar p = new JProgressBar(0, 100);
        p.setValue(50);
        assertTrue(p.getString().contains("%"),
                "expected percent default, got: " + p.getString());
    }

    // --- BorderPainted (R_vaadin_first drop-and-WARN) ------------------------------

    @Test
    @DisplayName("setBorderPainted false WARNs but the field still round-trips")
    void borderPaintedFalseWarnsButRoundTrips() {
        JProgressBar p = new JProgressBar();
        p.setBorderPainted(false);
        assertFalse(p.isBorderPainted());
        String warn = assertSingle(capturedWarns);
        assertTrue(warn.contains("setBorderPainted"),
                "expected WARN about setBorderPainted, got: " + warn);
    }

    // --- L&F stubs ------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is pinned to ProgressBarUI")
    void uiClassIdIsProgressBarUI() {
        assertEquals("ProgressBarUI", new JProgressBar().getUIClassID());
    }

    @Test
    @DisplayName("getUI returns null (no pluggable L&F)")
    void getUiReturnsNull() {
        JProgressBar p = new JProgressBar();
        // setUI is a no-op (R_layouts_close_enough — Vaadin owns the DOM).
        p.setUI(null);
        assertNull(p.getUI());
    }
}
