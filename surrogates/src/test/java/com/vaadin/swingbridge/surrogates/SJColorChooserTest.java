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

import com.vaadin.flow.component.html.Input;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.colorchooser.DefaultColorSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjcolorchooser's SJColorChooser surrogate. Covers:
 *
 * <ol>
 *  <li><b>ColorSelectionModel plumbing</b> — ctor variants seed the model,
 *      setColor routes through the model, model-swap re-subscribes + fires PCE.
 *  <li><b>Peer bridge</b> — setColor pushes a #rrggbb hex to the Input {@code value};
 *      a browser-originated value change writes back through the model and
 *      fires a ChangeEvent with source=SJColorChooser; the preventPeerEvents
 *      guard blocks the feedback loop.
 *  <li><b>Alpha loss</b> — a translucent model color pushes opaque hex to the peer.
 *  <li><b>R_vaadin_first drops</b> — setPreviewPanel / setChooserPanels WARN but round-trip.
 *  <li><b>JComponentMixin smoke</b> — getUIClassID pinned to "ColorChooserUI".
 * </ol>
 */
class SJColorChooserTest extends AbstractKaribuTest {

    /**
     * Simulate a browser-originated color pick by writing through the inherited
     * Vaadin {@link Input#setValue}, which fires the ValueChangeListener and drives
     * the peer→model R_callswing_envelope path — mirrors SJSlider's {@code dragPeerTo}.
     *
     * <p>Kotlin had this as an extension on SJColorChooser; Java takes the receiver first.
     */
    private static void pickPeerColor(SJColorChooser cc, String hex) {
        ((Input) cc).setValue(hex);
    }

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("default ctor installs a DefaultColorSelectionModel")
    void defaultCtorInstallsADefaultColorSelectionModel() {
        SJColorChooser cc = new SJColorChooser();
        assertInstanceOf(DefaultColorSelectionModel.class, cc.getSelectionModel());
        assertEquals("color", cc.getType());
        // JDK DefaultColorSelectionModel default is white.
        assertEquals(Color.white, cc.getColor());
    }

    @Test
    @DisplayName("color ctor seeds the initial color")
    void colorCtorSeedsTheInitialColor() {
        SJColorChooser cc = new SJColorChooser(Color.RED);
        assertEquals(Color.RED, cc.getColor());
        assertEquals("#ff0000", ((Input) cc).getValue());
    }

    @Test
    @DisplayName("null color ctor falls back to white")
    void nullColorCtorFallsBackToWhite() {
        SJColorChooser cc = new SJColorChooser((Color) null);
        assertEquals(Color.white, cc.getColor());
    }

    @Test
    @DisplayName("model ctor uses the supplied model as source of truth")
    void modelCtorUsesTheSuppliedModelAsSourceOfTruth() {
        DefaultColorSelectionModel model = new DefaultColorSelectionModel(Color.GREEN);
        SJColorChooser cc = new SJColorChooser(model);
        assertSame(model, cc.getSelectionModel());
        assertEquals(Color.GREEN, cc.getColor());
    }

    // --- setColor round-trip -------------------------------------------

    @Test
    @DisplayName("setColor writes through the model and pushes hex to the peer")
    void setColorWritesThroughTheModelAndPushesHexToThePeer() {
        SJColorChooser cc = new SJColorChooser();
        cc.setColor(new Color(0x12, 0x34, 0x56));
        assertEquals(new Color(0x12, 0x34, 0x56), cc.getSelectionModel().getSelectedColor());
        assertEquals("#123456", ((Input) cc).getValue());
        assertNoWarns("supported path must be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("setColor int rgb overloads route through the model")
    void setColorIntRgbOverloadsRouteThroughTheModel() {
        SJColorChooser cc = new SJColorChooser();
        cc.setColor(0x10, 0x20, 0x30);
        assertEquals(new Color(0x10, 0x20, 0x30), cc.getColor());
        cc.setColor(0x405060);
        assertEquals(new Color(0x40, 0x50, 0x60), cc.getColor());
    }

    // --- ChangeListener fan-out ----------------------------------------

    @Test
    @DisplayName("setColor fires ChangeEvent with source = surrogate")
    void setColorFiresChangeEventWithSourceSurrogate() {
        SJColorChooser cc = new SJColorChooser();
        List<ChangeEvent> events = new ArrayList<>();
        cc.addChangeListener(events::add);
        cc.setColor(Color.BLUE);
        assertEquals(1, events.size());
        assertSame(cc, events.get(0).getSource());
    }

    @Test
    @DisplayName("removeChangeListener stops delivery")
    void removeChangeListenerStopsDelivery() {
        SJColorChooser cc = new SJColorChooser();
        Counter count = new Counter();
        ChangeListener l = e -> count.inc();
        cc.addChangeListener(l);
        cc.setColor(Color.BLUE);
        cc.removeChangeListener(l);
        cc.setColor(Color.GREEN);
        count.assertEquals(1);
        assertEquals(0, cc.getChangeListeners().length);
    }

    // --- Browser edit path (R_callswing_envelope) + feedback guard (R_swing_is_truth) ------------------

    @Test
    @DisplayName("browser value change writes model and fires ChangeEvent")
    void browserValueChangeWritesModelAndFiresChangeEvent() {
        SJColorChooser cc = new SJColorChooser(Color.BLACK);
        List<ChangeEvent> events = new ArrayList<>();
        cc.addChangeListener(events::add);

        pickPeerColor(cc, "#ff8800");

        assertEquals(new Color(0xff, 0x88, 0x00), cc.getColor());
        assertTrue(events.stream().anyMatch(it -> it.getSource() == cc),
                "expected ChangeEvent with source=SJColorChooser, got "
                        + events.stream().map(ChangeEvent::getSource).toList());
    }

    @Test
    @DisplayName("no feedback loop - one model write per browser edit")
    void noFeedbackLoopOneModelWritePerBrowserEdit() {
        SJColorChooser cc = new SJColorChooser(Color.BLACK);
        Counter count = new Counter();
        cc.addChangeListener(e -> count.inc());
        pickPeerColor(cc, "#010203");
        // Exactly one fan-out: the model write from syncColorFromPeer, whose
        // pushColorToPeer runs under preventPeerEvents so the re-entrant peer
        // value change is swallowed.
        count.assertEquals(1);
        assertEquals(new Color(1, 2, 3), cc.getColor());
    }

    @Test
    @DisplayName("unparseable peer hex is ignored")
    void unparseablePeerHexIsIgnored() {
        SJColorChooser cc = new SJColorChooser(Color.RED);
        pickPeerColor(cc, "not-a-color");
        // Model unchanged, no throw.
        assertEquals(Color.RED, cc.getColor());
    }

    // --- Model swap -----------------------------------------------------

    @Test
    @DisplayName("setSelectionModel swaps model, re-subscribes, fires PCE")
    void setSelectionModelSwapsModelResubscribesFiresPce() {
        SJColorChooser cc = new SJColorChooser(Color.RED);
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cc.addPropertyChangeListener("selectionModel", pces::add);

        DefaultColorSelectionModel fresh = new DefaultColorSelectionModel(Color.GREEN);
        cc.setSelectionModel(fresh);

        assertSame(fresh, cc.getSelectionModel());
        assertEquals(Color.GREEN, cc.getColor());
        assertEquals("#00ff00", ((Input) cc).getValue());
        assertEquals(1, pces.size());

        // Fan-out now tracks the new model, not the old one.
        List<ChangeEvent> events = new ArrayList<>();
        cc.addChangeListener(events::add);
        fresh.setSelectedColor(Color.BLUE);
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setSelectionModel same instance is a no-op")
    void setSelectionModelSameInstanceIsANoOp() {
        SJColorChooser cc = new SJColorChooser();
        var model = cc.getSelectionModel();
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cc.addPropertyChangeListener("selectionModel", pces::add);
        cc.setSelectionModel(model);
        assertTrue(pces.isEmpty());
    }

    // --- Alpha loss (SD_sjcolorchooser) ---------------------------------------------

    @Test
    @DisplayName("translucent model color pushes opaque hex to the peer")
    void translucentModelColorPushesOpaqueHexToThePeer() {
        SJColorChooser cc = new SJColorChooser();
        cc.setColor(new Color(0x11, 0x22, 0x33, 0x80)); // 50% alpha
        // The model retains the alpha the user set...
        assertEquals(0x80, cc.getColor().getAlpha());
        // ...but the peer sees only the opaque RGB.
        assertEquals("#112233", ((Input) cc).getValue());
    }

    // --- dragEnabled: silent field round-trip --------------------------

    @Test
    @DisplayName("setDragEnabled round-trips without WARN")
    void setDragEnabledRoundTripsWithoutWarn() {
        SJColorChooser cc = new SJColorChooser();
        assertFalse(cc.getDragEnabled());
        cc.setDragEnabled(true);
        assertTrue(cc.getDragEnabled());
        assertNoWarns();
    }

    // --- R_vaadin_first drop-and-WARN surface --------------------------------------

    @Test
    @DisplayName("setPreviewPanel WARNs but round-trips")
    void setPreviewPanelWarnsButRoundTrips() {
        SJColorChooser cc = new SJColorChooser();
        JPanel panel = new JPanel();
        cc.setPreviewPanel(panel);
        assertSame(panel, cc.getPreviewPanel());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setPreviewPanel")));
    }

    @Test
    @DisplayName("chooser panel setters WARN but round-trip")
    void chooserPanelSettersWarnButRoundTrip() {
        SJColorChooser cc = new SJColorChooser();
        // addChooserPanel + getChooserPanels round-trip through the field list.
        assertEquals(0, cc.getChooserPanels().length);
        cc.setChooserPanels(new AbstractColorChooserPanel[0]);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setChooserPanels")));
    }

    // --- JComponentMixin smoke -----------------------------------------

    @Test
    @DisplayName("getUIClassID is ColorChooserUI")
    void getUiClassIdIsColorChooserUi() {
        assertEquals("ColorChooserUI", new SJColorChooser().getUIClassID());
    }

    @Test
    @DisplayName("distinct instances have distinct models")
    void distinctInstancesHaveDistinctModels() {
        SJColorChooser a = new SJColorChooser();
        SJColorChooser b = new SJColorChooser();
        assertNotSame(a.getSelectionModel(), b.getSelectionModel());
    }
}
