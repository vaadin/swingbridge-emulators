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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.slider.IntegerSlider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SwingConstants;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjslider's SJSlider surrogate. Covers three layers:
 *
 * <ol>
 *  <li><b>BoundedRangeModel plumbing migrated down from :emulators.JSlider</b>
 *      — constructor variants, model-swap, ChangeListener fan-out with
 *      source=SJSlider, R_swing_is_truth feedback-loop guard, R_callswing_envelope peer→model mirror.
 *  <li><b>CSS rendering</b> — orientation (VERTICAL as transform rotate),
 *      inverted (scaleX), composition of both. setPaintTrack moves to the
 *      deferred bucket (vaadin-slider #9181).
 *  <li><b>Server-side snapToTicks</b> — the pure-logic slice we <em>can</em>
 *      implement without upstream support.
 * </ol>
 *
 * Plus smoke tests that JComponentMixin lands correctly on SJSlider
 * (border, client property, tooltip, getUIClassID pinned to "SliderUI").
 */
class SJSliderTest extends AbstractKaribuTest {

    /**
     * Simulate a browser-originated value change by writing through the
     * inherited Vaadin {@link IntegerSlider#setValue}, which fires the
     * ValueChangeListener and drives the peer→model R_callswing_envelope path (snap included).
     *
     * <p>The cast is load-bearing: SJSlider's own {@code setValue(int)} overload
     * is a model-direct write that bypasses the peer listener, so the static
     * type has to be narrowed to the peer's before the call binds.
     */
    private static void dragPeerTo(SJSlider s, int n) {
        ((IntegerSlider) s).setValue(Integer.valueOf(n));
    }

    /** Collects PCEs into a list a test can assert over. */
    private static List<PropertyChangeEvent> recordPces(SJSlider s) {
        final List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener(events::add);
        return events;
    }

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("default ctor seeds HORIZONTAL, min 0, max 100, value 50")
    void defaultCtorSeedsDefaults() {
        final SJSlider s = new SJSlider();
        assertEquals(SwingConstants.HORIZONTAL, s.getOrientation());
        assertEquals(0, s.getMinimum());
        assertEquals(100, s.getMaximum());
        assertEquals(50, s.getModel().getValue());
        assertEquals(0, s.getExtent());
        assertFalse(s.getValueIsAdjusting());
    }

    @Test
    @DisplayName("min-max ctor computes value as integer midpoint")
    void minMaxCtorComputesMidpoint() {
        final SJSlider s = new SJSlider(10, 21);
        assertEquals(15, s.getModel().getValue());  // (10 + 21) / 2 = 15 (int floor)
    }

    @Test
    @DisplayName("four-arg ctor forwards all four args")
    void fourArgCtorForwardsAll() {
        final SJSlider s = new SJSlider(SwingConstants.VERTICAL, 5, 50, 20);
        assertEquals(SwingConstants.VERTICAL, s.getOrientation());
        assertEquals(5, s.getMinimum());
        assertEquals(50, s.getMaximum());
        assertEquals(20, s.getModel().getValue());
    }

    @Test
    @DisplayName("BoundedRangeModel ctor uses supplied model unchanged")
    void modelCtorUsesSuppliedModel() {
        final BoundedRangeModel model = new DefaultBoundedRangeModel(42, 0, 0, 200);
        final SJSlider s = new SJSlider(model);
        assertSame(model, s.getModel());
        assertEquals(42, s.getModel().getValue());
        assertEquals(200, s.getMaximum());
    }

    // --- setValue / model plumbing --------------------------------------

    @Test
    @DisplayName("setValue int routes through the model and reaches the peer")
    void setValueReachesPeer() {
        final SJSlider s = new SJSlider();
        s.setValue(75);
        assertEquals(75, s.getModel().getValue());
        assertEquals(75, s.getValue().intValue());  // Vaadin IntegerSlider getter (Integer)
    }

    @Test
    @DisplayName("setMinimum and setMaximum push element properties onto the peer")
    void boundsPushElementProperties() {
        final SJSlider s = new SJSlider();
        s.setMinimum(10);
        s.setMaximum(200);
        assertEquals(10.0, s.getElement().getProperty("min", 0.0));
        assertEquals(200.0, s.getElement().getProperty("max", 0.0));
    }

    @Test
    @DisplayName("peer-originated value change mirrors into the model via R_callswing_envelope callSwing")
    void peerValueChangeMirrorsIntoModel() {
        // A peer write simulates a browser-side drag. The
        // ValueChangeListener fires, SHelper.callSwing routes to
        // syncValueFromPeer, which updates the model.
        final SJSlider s = new SJSlider();
        dragPeerTo(s, 33);
        assertEquals(33, s.getModel().getValue());
    }

    @Test
    @DisplayName("setValue does not cause infinite feedback loop (R_swing_is_truth preventPeerEvents)")
    void setValueDoesNotLoop() {
        final SJSlider s = new SJSlider();
        final Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        s.setValue(10);
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("ChangeEvent source is the SJSlider (not the model)")
    void changeEventSourceIsSurrogate() {
        final SJSlider s = new SJSlider();
        final List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);
        s.setValue(7);
        assertEquals(1, events.size());
        assertSame(s, events.get(0).getSource());
    }

    @Test
    @DisplayName("setValue to same value fires no ChangeEvent")
    void setValueSameFiresNothing() {
        final SJSlider s = new SJSlider();  // seeded at 50
        final Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        s.setValue(50);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("setExtent round-trips through the model")
    void setExtentRoundTrips() {
        final SJSlider s = new SJSlider(0, 100, 40);
        s.setExtent(20);
        assertEquals(20, s.getExtent());
    }

    @Test
    @DisplayName("setModel replaces and re-subscribes, pushes new bounds to peer")
    void setModelReSubscribes() {
        final SJSlider s = new SJSlider();
        final BoundedRangeModel oldModel = s.getModel();
        final BoundedRangeModel newModel = new DefaultBoundedRangeModel(0, 0, -50, 500);
        s.setModel(newModel);
        assertSame(newModel, s.getModel());
        assertNotSame(oldModel, newModel);
        assertEquals(-50.0, s.getElement().getProperty("min", 0.0));
        assertEquals(500.0, s.getElement().getProperty("max", 0.0));

        final Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        oldModel.setValue(10);    // detached — must not reach SJSlider
        hits.assertEquals(0);
        newModel.setValue(20);    // attached
        hits.assertEquals(1);
    }

    // --- Orientation (CSS transform) ------------------------------------

    @Test
    @DisplayName("setOrientation VERTICAL writes CSS transform rotate(-90deg)")
    void verticalWritesRotate() {
        final SJSlider s = new SJSlider();
        s.setOrientation(SwingConstants.VERTICAL);
        assertEquals("rotate(-90deg)", s.getElement().getStyle().get("transform"));
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setOrientation HORIZONTAL clears the transform when previously vertical")
    void horizontalClearsTransform() {
        final SJSlider s = new SJSlider();
        s.setOrientation(SwingConstants.VERTICAL);
        s.setOrientation(SwingConstants.HORIZONTAL);
        assertNull(s.getElement().getStyle().get("transform"));
    }

    @Test
    @DisplayName("setOrientation throws IAE on invalid value")
    void badOrientationThrows() {
        final SJSlider s = new SJSlider();
        assertThrows(IllegalArgumentException.class, () -> s.setOrientation(99));
    }

    @Test
    @DisplayName("setOrientation fires PCE with old and new")
    void orientationFiresPce() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        s.setOrientation(SwingConstants.VERTICAL);
        assertEquals(1, events.size());
        assertEquals("orientation", events.get(0).getPropertyName());
        assertEquals(SwingConstants.HORIZONTAL, events.get(0).getOldValue());
        assertEquals(SwingConstants.VERTICAL, events.get(0).getNewValue());
    }

    // --- Inverted (CSS scaleX) ------------------------------------------

    @Test
    @DisplayName("setInverted writes CSS scaleX(-1)")
    void invertedWritesScaleX() {
        final SJSlider s = new SJSlider();
        s.setInverted(true);
        assertEquals("scaleX(-1)", s.getElement().getStyle().get("transform"));
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("VERTICAL plus inverted compose both transforms into one rule")
    void verticalAndInvertedCompose() {
        final SJSlider s = new SJSlider();
        s.setOrientation(SwingConstants.VERTICAL);
        s.setInverted(true);
        assertEquals("rotate(-90deg) scaleX(-1)", s.getElement().getStyle().get("transform"));
    }

    @Test
    @DisplayName("setInverted back to false clears the transform")
    void invertedFalseClearsTransform() {
        final SJSlider s = new SJSlider();
        s.setInverted(true);
        s.setInverted(false);
        assertNull(s.getElement().getStyle().get("transform"));
    }

    @Test
    @DisplayName("setInverted fires PCE")
    void invertedFiresPce() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        s.setInverted(true);
        assertEquals(1, events.size());
        assertEquals("inverted", events.get(0).getPropertyName());
    }

    // --- snapToTicks (server-side rounding) -----------------------------

    @Test
    @DisplayName("snapToTicks with majorTickSpacing snaps peer-originated values")
    void snapToMajorTicks() {
        // Browser writes 47; major spacing is 20; nearest tick is 40.
        // The snap happens in syncValueFromPeer, so the model records 40.
        final SJSlider s = new SJSlider(0, 100, 0);
        s.setMajorTickSpacing(20);
        s.setSnapToTicks(true);
        dragPeerTo(s, 47);  // simulate browser drag
        assertEquals(40, s.getModel().getValue());
    }

    @Test
    @DisplayName("snapToTicks prefers minorTickSpacing when both set (JDK priority)")
    void snapPrefersMinorTicks() {
        final SJSlider s = new SJSlider(0, 100, 0);
        s.setMajorTickSpacing(20);
        s.setMinorTickSpacing(5);
        s.setSnapToTicks(true);
        dragPeerTo(s, 47);  // minor ticks at 0, 5, 10, …; nearest is 45
        assertEquals(45, s.getModel().getValue());
    }

    @Test
    @DisplayName("snapToTicks disabled — no rounding to tick grid")
    void snapDisabledPassesThrough() {
        final SJSlider s = new SJSlider(0, 100, 0);
        s.setMajorTickSpacing(20);
        // snapToTicks defaults to false
        dragPeerTo(s, 47);
        assertEquals(47, s.getModel().getValue());  // no tick snap — value passes through
    }

    @Test
    @DisplayName("snapToTicks ignored when no tick spacing is set")
    void snapWithoutSpacingIsNoop() {
        final SJSlider s = new SJSlider(0, 100, 0);
        s.setSnapToTicks(true);
        // Neither spacing set; snap is a no-op per JDK contract.
        dragPeerTo(s, 47);
        assertEquals(47, s.getModel().getValue());
    }

    @Test
    @DisplayName("snapToTicks respects non-zero minimum (grid anchored at minimum)")
    void snapAnchoredAtMinimum() {
        // Minimum = 3 means the tick grid starts at 3, 8, 13, 18, 23, ...
        // A peer write of 20 should snap to 18 (the nearest tick), not 20.
        final SJSlider s = new SJSlider(3, 100, 3);
        s.setMinorTickSpacing(5);
        s.setSnapToTicks(true);
        dragPeerTo(s, 20);
        assertEquals(18, s.getModel().getValue());
    }

    // --- Tick-spacing setters (non-visual; PCE but no WARN) -------------

    @Test
    @DisplayName("majorTickSpacing and minorTickSpacing round-trip and fire PCE with no WARN")
    void tickSpacingRoundTripsWithoutWarn() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        s.setMajorTickSpacing(20);
        s.setMinorTickSpacing(5);
        assertEquals(20, s.getMajorTickSpacing());
        assertEquals(5, s.getMinorTickSpacing());
        assertEquals(List.of("majorTickSpacing", "minorTickSpacing"),
                events.stream().map(PropertyChangeEvent::getPropertyName).toList());
        assertEquals(0, capturedWarns.size());
    }

    // --- Deferred paint/label setters (WARN + PCE + field round-trip) ---

    @Test
    @DisplayName("setPaintTicks(true) WARNs with upstream-ticket pointer, stores field, fires PCE")
    void paintTicksTrueWarns() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        s.setPaintTicks(true);
        assertTrue(s.getPaintTicks());
        assertEquals(1, events.size());
        assertEquals("paintTicks", events.get(0).getPropertyName());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setPaintTicks"));
    }

    @Test
    @DisplayName("setPaintTicks(false) does not WARN — default value")
    void paintTicksFalseIsSilent() {
        final SJSlider s = new SJSlider();
        s.setPaintTicks(false);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setPaintTrack(false) WARNs — the non-default direction needs rendering support")
    void paintTrackFalseWarns() {
        final SJSlider s = new SJSlider();
        s.setPaintTrack(false);
        assertFalse(s.getPaintTrack());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setPaintTrack"));
    }

    @Test
    @DisplayName("setPaintLabels(true) WARNs and fires PCE")
    void paintLabelsTrueWarns() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        s.setPaintLabels(true);
        assertTrue(s.getPaintLabels());
        assertEquals(1, events.size());
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("setLabelTable(non-null) WARNs, stores field, fires PCE")
    void labelTableWarns() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        final Hashtable<Integer, String> labels = new Hashtable<>();
        labels.put(0, "min");
        labels.put(100, "max");
        s.setLabelTable(labels);
        assertSame(labels, s.getLabelTable());
        assertEquals(1, events.size());
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("createStandardLabels WARNs and returns empty Hashtable")
    void createStandardLabelsWarns() {
        final SJSlider s = new SJSlider();
        final Hashtable<?, ?> result = s.createStandardLabels(20);
        assertNotNull(result);
        assertEquals(0, result.size());
        assertEquals(1, capturedWarns.size());
    }

    // --- ChangeListener management --------------------------------------

    @Test
    @DisplayName("getChangeListeners reflects add and remove")
    void changeListenersReflectAddRemove() {
        final SJSlider s = new SJSlider();
        final ChangeListener l = e -> { };
        s.addChangeListener(l);
        assertTrue(Arrays.asList(s.getChangeListeners()).contains(l));
        s.removeChangeListener(l);
        assertFalse(Arrays.asList(s.getChangeListeners()).contains(l));
    }

    // --- JComponentMixin surface on SJSlider (smoke tests) ---------------

    @Test
    @DisplayName("JComponentMixin border round-trips via CSS")
    void borderRoundTripsViaCss() {
        final SJSlider s = new SJSlider();
        s.setBorder(new LineBorder(Color.RED, 2));
        assertEquals("2px solid rgb(255,0,0)", s.getElement().getStyle().get("border"));
    }

    @Test
    @DisplayName("JComponentMixin client properties round-trip")
    void clientPropertiesRoundTrip() {
        final SJSlider s = new SJSlider();
        s.putClientProperty("theme", "dark");
        assertEquals("dark", s.getClientProperty("theme"));
    }

    @Test
    @DisplayName("JComponentMixin setToolTipText fires ToolTipText PCE")
    void toolTipTextFiresPce() {
        final SJSlider s = new SJSlider();
        final List<PropertyChangeEvent> events = recordPces(s);
        s.setToolTipText("Drag to change");
        assertEquals("Drag to change", s.getToolTipText());
        assertEquals(1, events.size());
        assertEquals("ToolTipText", events.get(0).getPropertyName());
    }

    // --- L&F pinning ----------------------------------------------------

    @Test
    @DisplayName("getUIClassID is SliderUI (JDK parity for BeanInfo)")
    void uiClassIdIsSliderUi() {
        assertEquals("SliderUI", new SJSlider().getUIClassID());
    }

    // --- End-to-end in a UI --------------------------------------------

    @Test
    @DisplayName("SJSlider attaches to UI")
    void attachesToUi() {
        // As of Vaadin 25.2 the Slider component is GA — the prior
        // `com.vaadin.experimental.sliderComponent` feature flag and its
        // ExperimentalFeatureException are gone, so no flag is needed to attach.
        final SJSlider s = new SJSlider(0, 100, 0);
        UI.getCurrent().add(s);
        LocatorJ._get(IntegerSlider.class);  // located as a plain Vaadin IntegerSlider — SJSlider is-a IntegerSlider
    }

    @Test
    @DisplayName("user drag while hosted drives the ChangeListener end-to-end")
    void userDragDrivesChangeListener() {
        final SJSlider s = new SJSlider(0, 100, 0);
        UI.getCurrent().add(s);

        final List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);

        LocatorJ._setValue(LocatorJ._get(IntegerSlider.class), 65);

        assertEquals(65, s.getModel().getValue());
        assertEquals(1, events.size());
        assertSame(s, events.get(0).getSource());
    }
}
