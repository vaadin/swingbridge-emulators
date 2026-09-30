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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.slider.IntegerSlider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JSliderTest extends AbstractKaribuTest {

    private static IntegerSlider peerOf(JSlider s) {
        return (IntegerSlider) s.getPeer();
    }

    @Test
    @DisplayName("default ctor seeds horizontal 0-100 with value 50")
    void defaultCtorSeedsMidpoint() {
        JSlider s = new JSlider();
        assertInstanceOf(IntegerSlider.class, s.getPeer());
        assertEquals(SwingConstants.HORIZONTAL, s.getOrientation());
        assertEquals(0, s.getMinimum());
        assertEquals(100, s.getMaximum());
        assertEquals(50, s.getValue());
        assertEquals(0, s.getExtent());
        assertFalse(s.getValueIsAdjusting());
    }

    @Test
    @DisplayName("min/max ctor computes value as midpoint via integer divide")
    void minMaxCtorFloorsTheMidpoint() {
        // JDK computes (min + max) / 2 with int floor — matches Swing.
        JSlider s = new JSlider(10, 21);
        assertEquals(15, s.getValue());  // (10 + 21) / 2 = 15
    }

    @Test
    @DisplayName("orientation/min/max/value ctor forwards all four args")
    void fourArgCtorForwardsEverything() {
        JSlider s = new JSlider(SwingConstants.VERTICAL, 5, 50, 20);
        assertEquals(SwingConstants.VERTICAL, s.getOrientation());
        assertEquals(5, s.getMinimum());
        assertEquals(50, s.getMaximum());
        assertEquals(20, s.getValue());
    }

    @Test
    @DisplayName("BoundedRangeModel ctor uses supplied model unchanged")
    void modelCtorUsesTheSuppliedModel() {
        // Validates the core bet: JDK SpinnerModel/BoundedRangeModel
        // APIs are reused straight, not reimplemented.
        DefaultBoundedRangeModel model = new DefaultBoundedRangeModel(42, 0, 0, 200);
        JSlider s = new JSlider(model);
        assertSame(model, s.getModel());
        assertEquals(42, s.getValue());
        assertEquals(200, s.getMaximum());
    }

    @Test
    @DisplayName("setValue writes through the model and reaches the peer")
    void setValueReachesThePeer() {
        JSlider s = new JSlider();
        s.setValue(75);
        assertEquals(75, s.getValue());
        assertEquals(75, peerOf(s).getValue());
    }

    @Test
    @DisplayName("setMinimum and setMaximum push element properties onto the peer")
    void boundsPushElementProperties() {
        // Slider.setMinDouble/setMaxDouble are package-private — we
        // reach via getElement().setProperty. Assert both properties
        // land on the peer element.
        JSlider s = new JSlider();
        s.setMinimum(10);
        s.setMaximum(200);
        IntegerSlider slider = peerOf(s);
        assertEquals(10.0, slider.getElement().getProperty("min", 0.0));
        assertEquals(200.0, slider.getElement().getProperty("max", 0.0));
    }

    @Test
    @DisplayName("peer-originated value change mirrors into the model")
    void peerValueChangeMirrorsIntoTheModel() {
        // R_swing_is_truth peer → setter path: setting the Vaadin Slider's value
        // (simulating drag or arrow-key input in the browser) updates
        // JSlider.value via the shared model.
        JSlider s = new JSlider();
        peerOf(s).setValue(33);
        assertEquals(33, s.getValue());
    }

    @Test
    @DisplayName("setValue does not cause infinite feedback loop")
    void setValueDoesNotLoop() {
        // Regression for preventPeerEvents: setValue → push to peer →
        // peer fires ValueChange synchronously → our listener would
        // re-enter the model setter without the guard. One ChangeEvent
        // per call is the contract.
        JSlider s = new JSlider();
        Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        s.setValue(10);
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("ChangeEvent source is the JSlider emulator, not the model")
    void changeEventSourceIsTheEmulator() {
        // Swing's JSlider re-sources ChangeEvents: migrated code casts
        // `(JSlider) e.getSource()` and must see the emulator.
        JSlider s = new JSlider();
        List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);
        s.setValue(7);
        assertSame(s, assertSingle(events).getSource());
    }

    @Test
    @DisplayName("setValue to same value fires no ChangeEvent")
    void setValueToSameValueIsSilent() {
        // DefaultBoundedRangeModel's own equality check skips redundant
        // fires; JSlider rides that.
        JSlider s = new JSlider();
        s.setValue(50);  // default already 50
        Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        s.setValue(50);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("setExtent round-trips through the model")
    void setExtentRoundTrips() {
        // Vaadin Slider has no extent concept (single handle); the
        // model field is the source of truth for BoundedRangeModel
        // contracts (value + extent <= maximum).
        JSlider s = new JSlider(0, 100, 40);
        s.setExtent(20);
        assertEquals(20, s.getExtent());
    }

    @Test
    @DisplayName("setOrientation throws IAE on invalid value")
    void setOrientationRejectsGarbage() {
        // D_never_fail_on_gaps: match Swing's own failure mode on programming errors.
        JSlider s = new JSlider();
        assertThrows(IllegalArgumentException.class, () -> s.setOrientation(99));
    }

    @Test
    @DisplayName("setOrientation VERTICAL rotates the peer via CSS transform, no WARN")
    void verticalRotatesViaCssTransform() {
        // SJSlider implements vertical via `transform: rotate(-90deg)` on the
        // host element. Field round-trips; the Vaadin peer carries the
        // transform so the browser renders rotated. No WARN.
        JSlider s = new JSlider();
        List<String> warns = new ArrayList<>();
        EHelper.warnHook = warns::add;
        try {
            s.setOrientation(SwingConstants.VERTICAL);
            assertEquals(SwingConstants.VERTICAL, s.getOrientation());
            assertEquals("rotate(-90deg)", s.getPeer().getElement().getStyle().get("transform"));
            assertNoWarns(warns);
        } finally {
            EHelper.warnHook = msg -> { };
        }
    }

    @Test
    @DisplayName("setModel replaces the model and re-subscribes the ChangeListener")
    void setModelReSubscribes() {
        // Old model's mutations should no longer fire on the slider;
        // new model's should. Verifies teardown + setup inside installModel.
        JSlider s = new JSlider();
        BoundedRangeModel oldModel = s.getModel();
        DefaultBoundedRangeModel newModel = new DefaultBoundedRangeModel(0, 0, 0, 500);
        s.setModel(newModel);
        assertSame(newModel, s.getModel());
        assertNotSame(oldModel, newModel);

        Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        oldModel.setValue(10);    // detached — must not reach JSlider
        hits.assertEquals(0);
        newModel.setValue(20);    // attached — must reach JSlider
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setModel pushes new min and max to the peer")
    void setModelPushesBounds() {
        // Installing a model whose min/max differ from the peer's
        // current values reconciles them via pushMinMaxToPeer.
        JSlider s = new JSlider();
        s.setModel(new DefaultBoundedRangeModel(0, 0, -50, 500));
        IntegerSlider slider = peerOf(s);
        assertEquals(-50.0, slider.getElement().getProperty("min", 0.0));
        assertEquals(500.0, slider.getElement().getProperty("max", 0.0));
    }

    @Test
    @DisplayName("tick and label setters round-trip and fire PCE")
    void tickAndLabelSettersRoundTrip() {
        // Bare-minimum stance (D_emulator_surrogate_split): field-only storage, WARN when
        // feature is enabled, but PCE still fires so BeanInfo-style
        // introspection works for migrated code.
        JSlider s = new JSlider();
        List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener(events::add);
        EHelper.warnHook = msg -> { };  // swallow expected WARNs
        try {
            s.setMajorTickSpacing(20);
            s.setMinorTickSpacing(5);
            s.setPaintTicks(true);
            s.setPaintLabels(true);
            s.setSnapToTicks(true);
            s.setInverted(true);
        } finally {
            EHelper.warnHook = msg -> { };
        }
        assertEquals(20, s.getMajorTickSpacing());
        assertEquals(5, s.getMinorTickSpacing());
        assertTrue(s.getPaintTicks());
        assertTrue(s.getPaintLabels());
        assertTrue(s.getSnapToTicks());
        assertTrue(s.getInverted());
        // Six setters fired six PCEs.
        assertEquals(
                List.of("majorTickSpacing", "minorTickSpacing", "paintTicks",
                        "paintLabels", "snapToTicks", "inverted"),
                events.stream().map(PropertyChangeEvent::getPropertyName).toList());
    }

    @Test
    @DisplayName("getChangeListeners reflects add and remove")
    void changeListenersReflectAddAndRemove() {
        JSlider s = new JSlider();
        ChangeListener l = e -> { };
        s.addChangeListener(l);
        assertTrue(Arrays.asList(s.getChangeListeners()).contains(l));
        s.removeChangeListener(l);
        assertFalse(Arrays.asList(s.getChangeListeners()).contains(l));
    }

    @Test
    @DisplayName("getUIClassID is SliderUI")
    void uiClassIdIsSliderUI() {
        assertEquals("SliderUI", new JSlider().getUIClassID());
    }

    @Test
    @DisplayName("user drag while hosted drives ChangeListener end-to-end")
    void userDragDrivesChangeListener() {
        // End-to-end: JSlider inside a visible JFrame; writing the
        // Vaadin Slider's value simulates a browser-side drag; the
        // Swing ChangeListener sees the event with source=JSlider.
        JSlider s = new JSlider(0, 100, 0);
        JFrame frame = new JFrame();
        frame.add(s);
        frame.setVisible(true);

        List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);

        LocatorJ._setValue(LocatorJ._get(IntegerSlider.class), 65);

        assertEquals(65, s.getValue());
        assertSame(s, assertSingle(events).getSource());
    }
}
