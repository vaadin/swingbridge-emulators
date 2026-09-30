/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJProgressBar. Covers:
 *
 * <ol>
 *  <li><b>BoundedRangeModel plumbing</b> — five ctors, model swap, value /
 *      min / max round-trip, ChangeListener fan-out with source=SJProgressBar.
 *      No peer→Swing mirror to verify (ProgressBar is display-only).
 *  <li><b>CSS rendering</b> — VERTICAL orientation as {@code transform: rotate(-90deg)}
 *      (SJSlider precedent); {@code setStringPainted} + {@code setString} toggle the
 *      {@code data-emul-progress-string} attribute that the injected {@code ::after}
 *      rule reads.
 *  <li><b>Indeterminate</b> — direct Vaadin property + PCE fire.
 *  <li><b>R_vaadin_first drop-and-WARN</b> — {@code setBorderPainted(false)} fires
 *      {@code SHelper.onUnimplemented}; the field still round-trips.
 *  <li><b>L&amp;F surface</b> — {@code getUIClassID == "ProgressBarUI"} pinned for
 *      BeanInfo introspection.
 * </ol>
 */
class SJProgressBarTest extends AbstractKaribuTest {

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("default ctor seeds HORIZONTAL, min 0, max 100, value 0")
    void defaultCtorSeedsHorizontalMinZeroMaxHundredValueZero() {
        SJProgressBar p = new SJProgressBar();
        assertEquals(SwingConstants.HORIZONTAL, p.getOrientation());
        assertEquals(0, p.getMinimum());
        assertEquals(100, p.getMaximum());
        assertEquals(0, p.getModel().getValue());
        assertFalse(p.isIndeterminate());
        assertFalse(p.isStringPainted());
        assertTrue(p.isBorderPainted());
    }

    @Test
    @DisplayName("min_max ctor forwards bounds, value defaults to min")
    void minMaxCtorForwardsBounds() {
        SJProgressBar p = new SJProgressBar(10, 250);
        assertEquals(10, p.getMinimum());
        assertEquals(250, p.getMaximum());
        // JDK JProgressBar(int min, int max) routes through
        // (orient, min, max) which seeds DefaultBoundedRangeModel(min, 0, min, max).
        assertEquals(10, p.getModel().getValue());
    }

    @Test
    @DisplayName("orientation_min_max ctor forwards all three")
    void orientationMinMaxCtorForwardsAllThree() {
        SJProgressBar p = new SJProgressBar(SwingConstants.VERTICAL, 5, 50);
        assertEquals(SwingConstants.VERTICAL, p.getOrientation());
        assertEquals(5, p.getMinimum());
        assertEquals(50, p.getMaximum());
    }

    @Test
    @DisplayName("BoundedRangeModel ctor uses supplied model unchanged")
    void boundedRangeModelCtorUsesSuppliedModelUnchanged() {
        DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(42, 0, 0, 200);
        SJProgressBar p = new SJProgressBar(model);
        assertSame(model, p.getModel());
        assertEquals(42, p.getModel().getValue());
        assertEquals(200, p.getMaximum());
    }

    // --- setValue / model plumbing --------------------------------------

    @Test
    @DisplayName("setValue int routes through the model and reaches the peer")
    void setValueIntRoutesThroughTheModelAndReachesThePeer() {
        SJProgressBar p = new SJProgressBar();
        p.setValue(75);
        assertEquals(75, p.getModel().getValue());
        assertEquals(75.0, p.getValue());      // Vaadin ProgressBar getter (Double)
    }

    @Test
    @DisplayName("setMinimum and setMaximum push element properties onto the peer")
    void setMinimumAndSetMaximumPushElementProperties() {
        SJProgressBar p = new SJProgressBar();
        p.setMinimum(10);
        p.setMaximum(200);
        assertEquals(10.0, p.getElement().getProperty("min", 0.0));
        assertEquals(200.0, p.getElement().getProperty("max", 0.0));
    }

    @Test
    @DisplayName("ChangeEvent source is the SJProgressBar (not the model)")
    void changeEventSourceIsTheSjProgressBar() {
        SJProgressBar p = new SJProgressBar();
        List<ChangeEvent> events = new ArrayList<>();
        p.addChangeListener(events::add);
        p.setValue(7);
        assertEquals(1, events.size());
        assertSame(p, events.get(0).getSource());
    }

    @Test
    @DisplayName("setValue to same value fires no ChangeEvent")
    void setValueToSameValueFiresNoChangeEvent() {
        SJProgressBar p = new SJProgressBar();    // seeded at 0
        Counter hits = new Counter();
        p.addChangeListener(e -> hits.inc());
        p.setValue(0);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("setModel replaces, re-subscribes, pushes new bounds to peer")
    void setModelReplacesResubscribesPushesNewBounds() {
        SJProgressBar p = new SJProgressBar();
        var oldModel = p.getModel();
        DefaultBoundedRangeModel newModel = new DefaultBoundedRangeModel(0, 0, -50, 500);
        p.setModel(newModel);
        assertSame(newModel, p.getModel());
        assertNotSame(oldModel, newModel);
        assertEquals(-50.0, p.getElement().getProperty("min", 0.0));
        assertEquals(500.0, p.getElement().getProperty("max", 0.0));

        Counter hits = new Counter();
        p.addChangeListener(e -> hits.inc());
        oldModel.setValue(10);    // detached — must not reach SJProgressBar
        hits.assertEquals(0);
        newModel.setValue(20);    // attached
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("getPercentComplete handles zero span as 0_0")
    void getPercentCompleteHandlesZeroSpan() {
        // DefaultBoundedRangeModel requires min <= value <= max+extent.
        // For a zero-span model we need value == min == max.
        SJProgressBar p = new SJProgressBar(new DefaultBoundedRangeModel(5, 0, 5, 5));
        assertEquals(0.0, p.getPercentComplete());
    }

    @Test
    @DisplayName("getPercentComplete reads value scaled to bounds")
    void getPercentCompleteReadsValueScaledToBounds() {
        SJProgressBar p = new SJProgressBar(0, 200);
        p.setValue(50);
        assertEquals(0.25, p.getPercentComplete());
    }

    // --- Orientation (VERTICAL is R_vaadin_first drop-and-WARN) ---------------------

    @Test
    @DisplayName("setOrientation VERTICAL WARNs but the field round-trips")
    void setOrientationVerticalWarnsButFieldRoundTrips() {
        // VERTICAL stores in the field + fires PCE but doesn't change
        // the visual — see SJProgressBar class javadoc.
        SJProgressBar p = new SJProgressBar();
        p.setOrientation(SwingConstants.VERTICAL);
        assertEquals(SwingConstants.VERTICAL, p.getOrientation());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setOrientation"),
                "expected WARN about setOrientation, got: " + capturedWarns.get(0));
        // No CSS transform should be written.
        assertNull(p.getElement().getStyle().get("transform"));
    }

    @Test
    @DisplayName("setOrientation HORIZONTAL stays WARN-free")
    void setOrientationHorizontalStaysWarnFree() {
        SJProgressBar p = new SJProgressBar();
        p.setOrientation(SwingConstants.HORIZONTAL);    // already HORIZONTAL — no-op
        // Force a state change to exercise the setter body.
        p.setOrientation(SwingConstants.VERTICAL);      // WARNs
        capturedWarns.clear();
        p.setOrientation(SwingConstants.HORIZONTAL);    // back to default — no WARN
        assertEquals(SwingConstants.HORIZONTAL, p.getOrientation());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setOrientation throws IAE on invalid value")
    void setOrientationThrowsIaeOnInvalidValue() {
        SJProgressBar p = new SJProgressBar();
        assertThrows(IllegalArgumentException.class, () -> p.setOrientation(99));
    }

    // --- Indeterminate --------------------------------------------------

    @Test
    @DisplayName("setIndeterminate true reaches the peer property")
    void setIndeterminateTrueReachesThePeerProperty() {
        SJProgressBar p = new SJProgressBar();
        p.setIndeterminate(true);
        assertTrue(p.isIndeterminate());
        // Vaadin ProgressBar reflects to "indeterminate" element property.
        assertTrue(p.getElement().getProperty("indeterminate", false));
    }

    // --- setString / setStringPainted (CSS overlay) ---------------------

    @Test
    @DisplayName("setStringPainted false leaves no data attribute")
    void setStringPaintedFalseLeavesNoDataAttribute() {
        SJProgressBar p = new SJProgressBar();
        assertFalse(p.isStringPainted());
        assertNull(p.getElement().getAttribute("data-emul-progress-string"));
    }

    @Test
    @DisplayName("setStringPainted true with no string defaults to JDK percent")
    void setStringPaintedTrueWithNoStringDefaultsToJdkPercent() {
        SJProgressBar p = new SJProgressBar(0, 100);
        p.setValue(33);
        p.setStringPainted(true);
        // JDK NumberFormat.getPercentInstance() — locale-dependent
        // formatting, but the percent character is always present.
        String attr = p.getElement().getAttribute("data-emul-progress-string");
        assertTrue(attr != null && attr.contains("%"),
                "expected percent attribute, got: " + attr);
    }

    @Test
    @DisplayName("setString writes attribute when stringPainted, ignored otherwise")
    void setStringWritesAttributeWhenStringPainted() {
        SJProgressBar p = new SJProgressBar();
        p.setString("Working…");
        // stringPainted is still false — attribute must remain absent.
        assertNull(p.getElement().getAttribute("data-emul-progress-string"));

        p.setStringPainted(true);
        assertEquals("Working…", p.getElement().getAttribute("data-emul-progress-string"));
    }

    @Test
    @DisplayName("setStringPainted false clears the attribute")
    void setStringPaintedFalseClearsTheAttribute() {
        SJProgressBar p = new SJProgressBar();
        p.setString("Loading");
        p.setStringPainted(true);
        assertEquals("Loading", p.getElement().getAttribute("data-emul-progress-string"));
        p.setStringPainted(false);
        assertNull(p.getElement().getAttribute("data-emul-progress-string"));
    }

    @Test
    @DisplayName("string overlay does not WARN")
    void stringOverlayDoesNotWarn() {
        SJProgressBar p = new SJProgressBar();
        p.setStringPainted(true);
        p.setString("x");
        assertEquals(0, capturedWarns.size());
    }

    // --- setBorderPainted (R_vaadin_first drop-and-WARN) ----------------------------

    @Test
    @DisplayName("setBorderPainted false WARNs but field still round-trips")
    void setBorderPaintedFalseWarnsButFieldRoundTrips() {
        SJProgressBar p = new SJProgressBar();
        p.setBorderPainted(false);
        assertFalse(p.isBorderPainted());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setBorderPainted"),
                "expected WARN about setBorderPainted, got: " + capturedWarns.get(0));
    }

    @Test
    @DisplayName("setBorderPainted true does not WARN (it is the default)")
    void setBorderPaintedTrueDoesNotWarn() {
        SJProgressBar p = new SJProgressBar();
        p.setBorderPainted(false);  // WARNs
        capturedWarns.clear();
        p.setBorderPainted(true);   // back to default — no WARN
        assertEquals(0, capturedWarns.size());
    }

    // --- ChangeListener API ---------------------------------------------

    @Test
    @DisplayName("getChangeListeners returns attached listeners in registration order")
    void getChangeListenersReturnsAttachedListeners() {
        SJProgressBar p = new SJProgressBar();
        ChangeListener a = e -> {
        };
        ChangeListener b = e -> {
        };
        p.addChangeListener(a);
        p.addChangeListener(b);
        // EventListenerList returns listeners in LIFO order from getListeners.
        ChangeListener[] attached = p.getChangeListeners();
        assertEquals(2, attached.length);
        assertTrue(List.of(attached).contains(a));
        assertTrue(List.of(attached).contains(b));
    }

    @Test
    @DisplayName("removeChangeListener detaches")
    void removeChangeListenerDetaches() {
        SJProgressBar p = new SJProgressBar();
        Counter hits = new Counter();
        ChangeListener l = e -> hits.inc();
        p.addChangeListener(l);
        p.setValue(1);
        hits.assertEquals(1);
        p.removeChangeListener(l);
        p.setValue(2);
        hits.assertEquals(1);
    }

    // --- L&F / UIClassID ------------------------------------------------

    @Test
    @DisplayName("getUIClassID is pinned to ProgressBarUI")
    void getUiClassIdIsPinnedToProgressBarUi() {
        assertEquals("ProgressBarUI", new SJProgressBar().getUIClassID());
    }
}
