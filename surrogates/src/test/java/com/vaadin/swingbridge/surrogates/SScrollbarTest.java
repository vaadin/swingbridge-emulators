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
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter;
import tools.jackson.databind.node.JsonNodeFactory;

import java.awt.Adjustable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SScrollbar surrogate — the AWT 1.0 {@code java.awt.Scrollbar},
 * not {@code JScrollBar}. Covers:
 *
 * <ol>
 *  <li>Ctor defaults, including AWT's VERTICAL default.
 *  <li>{@code setValues}' five-step clamping table, which is most of the class.
 *  <li>{@code getMaximum} reconstructing from the peer's {@code max} + {@code visibleAmount}.
 *  <li>The orientation bijection, its CSS, and the IAE message.
 *  <li>{@code step} staying 1 so the whole band stays reachable.
 *  <li>The increments' clamp-to-1 with no browser lever behind them.
 *  <li>{@code valueIsAdjusting} driven from the browser edge.
 *  <li>Zero stub WARNs across the happy path.
 * </ol>
 *
 * <p>Karibu note the event tests rest on: {@code _setValue(v)} marks the change
 * {@code isFromClient = true}, a plain server-side {@code setValue} does not — so the
 * browser edge is exactly testable browserlessly.
 */
class SScrollbarTest extends AbstractKaribuTest {

    // --- Ctors --------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor is AWT's vertical 0-10-0-100")
    void noArgCtorIsAwtsVerticalDefaults() {
        SScrollbar s = new SScrollbar();
        // VERTICAL, unlike every Swing slider — the fact that drives the peer
        // choice, so it is worth asserting rather than assuming.
        assertEquals(Adjustable.VERTICAL, s.getAwtOrientation());
        assertEquals(0, s.getIntValue());
        assertEquals(10, s.getVisibleAmount());
        assertEquals(0, s.getMinimum());
        assertEquals(100, s.getMaximum());
    }

    @Test
    @DisplayName("orientation ctor keeps the other four defaults")
    void orientationCtorKeepsTheOtherFourDefaults() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL);
        assertEquals(Adjustable.HORIZONTAL, s.getAwtOrientation());
        assertEquals(10, s.getVisibleAmount());
        assertEquals(100, s.getMaximum());
    }

    @Test
    @DisplayName("five-arg ctor seeds every field")
    void fiveArgCtorSeedsEveryField() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 30, 5, 10, 60);
        assertEquals(30, s.getIntValue());
        assertEquals(5, s.getVisibleAmount());
        assertEquals(10, s.getMinimum());
        assertEquals(60, s.getMaximum());
    }

    @Test
    @DisplayName("illegal ctor orientation throws AWT's exact message")
    void illegalCtorOrientationThrowsAwtsExactMessage() {
        // NO_ORIENTATION is a constant on the very interface Scrollbar
        // implements, and is still not legal here.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new SScrollbar(Adjustable.NO_ORIENTATION));
        assertEquals("illegal scrollbar orientation", e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new SScrollbar(-1));
        assertThrows(IllegalArgumentException.class, () -> new SScrollbar(7, 0, 10, 0, 100));
    }

    // --- setValues: the clamping table --------------------------------

    @Test
    @DisplayName("value clamps into the band, not into min-max")
    void valueClampsIntoTheBand() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        // The reachable top is maximum - visibleAmount, never maximum.
        s.setIntValue(999);
        assertEquals(90, s.getIntValue());
        s.setIntValue(-5);
        assertEquals(0, s.getIntValue());
    }

    @Test
    @DisplayName("visibleAmount clamps to the span then up to 1")
    void visibleAmountClampsToTheSpanThenUpToOne() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        s.setVisibleAmount(500);
        assertEquals(100, s.getVisibleAmount());
        s.setVisibleAmount(0);
        assertEquals(1, s.getVisibleAmount());
        s.setVisibleAmount(-7);
        assertEquals(1, s.getVisibleAmount());
    }

    @Test
    @DisplayName("setValues forces maximum one above minimum")
    void setValuesForcesMaximumOneAboveMinimum() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        s.setValues(0, 10, 40, 40);
        assertEquals(40, s.getMinimum());
        assertEquals(41, s.getMaximum());
        // visible then clamps to the 1-wide span, and value to the band's floor.
        assertEquals(1, s.getVisibleAmount());
        assertEquals(40, s.getIntValue());
    }

    @Test
    @DisplayName("setMaximum below minimum drags minimum down with it")
    void setMaximumBelowMinimumDragsMinimumDown() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 50, 5, 40, 100);
        // AWT's pre-step: minimum is rewritten to newMaximum - 1 *before* the
        // funnel clamps anything.
        s.setMaximum(20);
        assertEquals(19, s.getMinimum());
        assertEquals(20, s.getMaximum());
    }

    @Test
    @DisplayName("minimum MAX_VALUE is pinned one below it")
    void minimumMaxValueIsPinnedOneBelowIt() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        s.setMinimum(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE - 1, s.getMinimum());
        // maximum <= minimum then forces maximum up by one.
        assertEquals(Integer.MAX_VALUE, s.getMaximum());
    }

    @Test
    @DisplayName("maximum MIN_VALUE is nudged up by one")
    void maximumMinValueIsNudgedUpByOne() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        s.setMaximum(Integer.MIN_VALUE);
        assertEquals(Integer.MIN_VALUE + 1, s.getMaximum());
    }

    @Test
    @DisplayName("a span wider than MAX_VALUE is capped")
    void aSpanWiderThanMaxValueIsCapped() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        s.setValues(0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MIN_VALUE, s.getMinimum());
        assertEquals(Integer.MIN_VALUE + Integer.MAX_VALUE, s.getMaximum());
    }

    @Test
    @DisplayName("setValues never throws on any extreme")
    void setValuesNeverThrowsOnAnyExtreme() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL);
        s.setValues(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        s.setValues(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        // No assertion beyond "it returned" — AWT's contract here is that the
        // arithmetic absorbs everything rather than throwing.
    }

    // --- the peer's max carries maximum - visibleAmount ---------------

    @Test
    @DisplayName("getMaximum reconstructs from the peer's max plus visibleAmount")
    void getMaximumReconstructsFromThePeersMax() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        // SD_sscrollbar's storage choice, visible in the DOM: max is the band's top.
        assertEquals(90.0, s.getMax());
        assertEquals(100, s.getMaximum());
        s.setVisibleAmount(40);
        assertEquals(60.0, s.getMax());
        assertEquals(100, s.getMaximum());
    }

    @Test
    @DisplayName("a zero value is written to the DOM explicitly, not left to the browser")
    void aZeroValueIsWrittenToTheDomExplicitly() {
        // Regression guard for a browser-only defect: value 0 equals
        // RangeInput's own initial value, so setValue writes nothing, and an
        // <input type=range> with no value attribute renders its thumb at the
        // midpoint of its range. AWT's default ctor is exactly this case.
        SScrollbar s = new SScrollbar();
        assertEquals(0.0, s.getElement().getProperty("value", -1.0));
        s.setIntValue(30);
        assertEquals(30.0, s.getElement().getProperty("value", -1.0));
    }

    @Test
    @DisplayName("minimum reaches the peer as min")
    void minimumReachesThePeerAsMin() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 5, 100);
        assertEquals(5.0, s.getMin());
    }

    // --- orientation ---------------------------------------------------

    @Test
    @DisplayName("vertical writes the standardized writing-mode, horizontal clears it")
    void verticalWritesTheStandardizedWritingMode() {
        // The load-bearing CSS: Vaadin's setOrientation alone renders a square
        // blob on current Chromium, and `transform: rotate` (SJSlider's
        // mechanism) would leave the layout box unrotated.
        SScrollbar s = new SScrollbar(Adjustable.VERTICAL);
        assertEquals("vertical-lr", s.getStyle().get("writing-mode"));
        assertEquals(RangeInput.Orientation.VERTICAL, s.getOrientation());

        s.setAwtOrientation(Adjustable.HORIZONTAL);
        assertNull(s.getStyle().get("writing-mode"));
        assertEquals(RangeInput.Orientation.HORIZONTAL, s.getOrientation());
    }

    @Test
    @DisplayName("setAwtOrientation to the current value returns before the legality check")
    void setAwtOrientationToTheCurrentValueReturnsEarly() {
        SScrollbar s = new SScrollbar(Adjustable.VERTICAL);
        s.setAwtOrientation(Adjustable.VERTICAL);   // no-op, no throw
        assertEquals(Adjustable.VERTICAL, s.getAwtOrientation());
    }

    @Test
    @DisplayName("illegal setAwtOrientation throws AWT's message")
    void illegalSetAwtOrientationThrowsAwtsMessage() {
        SScrollbar s = new SScrollbar(Adjustable.VERTICAL);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> s.setAwtOrientation(Adjustable.NO_ORIENTATION));
        assertEquals("illegal scrollbar orientation", e.getMessage());
        assertEquals(Adjustable.VERTICAL, s.getAwtOrientation());
    }

    // --- step stays 1 --------------------------------------------------

    @Test
    @DisplayName("step is 1 and a non-default unitIncrement does not change it")
    void stepIsOneAndUnitIncrementDoesNotChangeIt() {
        // Writing unitIncrement into step would quantize the value space:
        // with step 16 on a 0-90 band, 37 reads back 32 and the top 90 reads
        // back 80. The band's reachability wins over an observable arrow
        // delta (R_match_swing_errors(c)).
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        assertEquals(1.0, s.getStep());
        s.setUnitIncrement(16);
        assertEquals(1.0, s.getStep());
        s.setIntValue(37);
        assertEquals(37, s.getIntValue());
        s.setIntValue(90);
        assertEquals(90, s.getIntValue());
    }

    // --- increments ----------------------------------------------------

    @Test
    @DisplayName("increments clamp up to 1 and round-trip")
    void incrementsClampUpToOneAndRoundTrip() {
        SScrollbar s = new SScrollbar();
        assertEquals(1, s.getUnitIncrement());
        assertEquals(10, s.getBlockIncrement());
        s.setUnitIncrement(16);
        assertEquals(16, s.getUnitIncrement());
        s.setUnitIncrement(0);
        assertEquals(1, s.getUnitIncrement());
        s.setBlockIncrement(-5);
        assertEquals(1, s.getBlockIncrement());
    }

    // --- valueIsAdjusting ----------------------------------------------

    @Test
    @DisplayName("a from-client value change sets adjusting, the change event clears it")
    void aFromClientValueChangeSetsAdjusting() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        UI.getCurrent().add(s);
        assertFalse(s.getValueIsAdjusting());

        LocatorJ._setValue(LocatorJ._get(RangeInput.class), 42.0);
        assertTrue(s.getValueIsAdjusting());
        assertEquals(42, s.getIntValue());

        fireDomChange(s);
        assertFalse(s.getValueIsAdjusting());
    }

    @Test
    @DisplayName("a server-side write leaves adjusting alone")
    void aServerSideWriteLeavesAdjustingAlone() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 100);
        s.setIntValue(42);
        assertFalse(s.getValueIsAdjusting());
    }

    @Test
    @DisplayName("setValueIsAdjusting round-trips as the advisory write it is")
    void setValueIsAdjustingRoundTrips() {
        SScrollbar s = new SScrollbar();
        s.setValueIsAdjusting(true);
        assertTrue(s.getValueIsAdjusting());
    }

    /**
     * Dispatch the browser's own {@code change} event, exactly as Flow would on
     * drag-end. Karibu's {@code _setValue} covers the value half; the commit half is
     * a raw DOM event with no value payload, which is the whole reason
     * {@code valueIsAdjusting} needs it.
     */
    private static void fireDomChange(SScrollbar s) {
        s.getElement().getNode()
                .getFeature(ElementListenerMap.class)
                .fireEvent(
                        new DomEvent(
                                s.getElement(), "change",
                                JsonNodeFactory.instance.objectNode()));
    }

    // --- inherited mixin surface ---------------------------------------

    @Test
    @DisplayName("the ComponentMixin surface works on this non-JComponent host")
    void theComponentMixinSurfaceWorksOnThisNonJComponentHost() {
        SScrollbar s = new SScrollbar();
        s.setName("zoom-bar");
        assertEquals("zoom-bar", s.getName());
        s.setEnabled(false);
        assertFalse(s.isEnabled());
        s.setEnabled(true);
        assertTrue(s.isEnabled());
        // Declared on this class precisely so ComponentMixin's instanceof
        // guards find them rather than WARNing.
        // These three WARN on a host that is not a HasTooltip / KeyNotifier /
        // ClickNotifier, which is why SScrollbar declares all three.
        s.setToolTipText("Zoom");
        assertEquals("Zoom", s.getToolTipText());
        s.addKeyListener(new SKeyAdapter() {
        });
        s.addMouseListener(new SMouseAdapter() {
        });
        assertNoWarns("HasTooltip / KeyNotifier / ClickNotifier are declared on SScrollbar");
    }

    @Test
    @DisplayName("paramString carries AWT's tail with no equals on the orientation")
    void paramStringCarriesAwtsTail() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 30, 5, 10, 60);
        assertEquals("val=30,vis=5,min=10,max=60,horz,isAdjusting=false", s.paramString());
        assertEquals(
                "val=0,vis=10,min=0,max=100,vert,isAdjusting=false",
                new SScrollbar().paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        assertNull(new SScrollbar().getAccessibleContext());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("getAccessibleContext"));
    }

    // --- Exit gate ----------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SScrollbar s = new SScrollbar(Adjustable.HORIZONTAL, 0, 10, 0, 255);
        s.setName("zoom");
        s.setEnabled(true);
        s.setToolTipText("Zoom");
        UI.getCurrent().add(s);
        s.addValueChangeListener(e -> {
        });
        s.addKeyListener(new SKeyAdapter() {
        });
        s.addMouseListener(new SMouseAdapter() {
        });
        LocatorJ._setValue(LocatorJ._get(RangeInput.class), 128.0);
        s.setIntValue(200);
        s.setVisibleAmount(20);
        s.setMinimum(10);
        s.setMaximum(300);
        s.setUnitIncrement(4);
        s.setBlockIncrement(40);
        s.setAwtOrientation(Adjustable.VERTICAL);
        s.paramString();
        assertNoWarns("no stub WARNs expected from SScrollbar's happy path");
    }
}
