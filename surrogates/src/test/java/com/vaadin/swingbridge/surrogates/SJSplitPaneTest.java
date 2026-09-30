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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SJSplitPane (SD_sjsplitpane). Covers:
 *
 * <ol>
 *  <li><b>Ctors + orientation validation</b> — no-arg defaults to
 *      HORIZONTAL_SPLIT; int-arg accepts HORIZONTAL_SPLIT/VERTICAL_SPLIT,
 *      throws IAE on garbage.
 *  <li><b>Orientation flip</b> — setOrientation(int) translates to Vaadin
 *      Orientation enum, fires "orientation" PCE.
 *  <li><b>Slot setters</b> — setLeftComponent / setTopComponent /
 *      setRightComponent / setBottomComponent map to addToPrimary /
 *      addToSecondary with replace semantics; null clears.
 *  <li><b>setDividerLocation(double)</b> — proportional 0–1 maps to
 *      setSplitterPosition * 100; getDividerLocation returns -1 sentinel.
 *  <li><b>resetToPreferredSizes</b> — snaps to 50/50 (best-effort R_best_effort_behaviour).
 *  <li><b>No-counterpart cluster (present-but-drops)</b> — six setters
 *      (setDividerLocation int, setResizeWeight, setOneTouchExpandable,
 *      setContinuousLayout, setDividerSize, setLastDividerLocation)
 *      WARN + drop; getters return JDK defaults.
 *  <li><b>Sentinel getters</b> — getMinimumDividerLocation /
 *      getMaximumDividerLocation WARN + return -1.
 *  <li><b>L&amp;F surface</b> — getUIClassID == "SplitPaneUI"; setUI WARNs.
 *  <li><b>data-swing-class stamp</b>.
 * </ol>
 */
class SJSplitPaneTest extends AbstractKaribuTest {

    // --- Ctors + orientation validation -------------------------------------

    @Test
    @DisplayName("no-arg ctor defaults to HORIZONTAL_SPLIT")
    void noArgCtorDefaultsToHorizontalSplit() {
        SJSplitPane sp = new SJSplitPane();
        assertEquals(SJSplitPane.HORIZONTAL_SPLIT, sp.getOrientationAsInt());
        assertEquals(SplitLayout.Orientation.HORIZONTAL, sp.getOrientation());
        assertNoWarns("no-arg ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("int-arg ctor accepts HORIZONTAL_SPLIT")
    void intArgCtorAcceptsHorizontalSplit() {
        SJSplitPane sp = new SJSplitPane(SJSplitPane.HORIZONTAL_SPLIT);
        assertEquals(SJSplitPane.HORIZONTAL_SPLIT, sp.getOrientationAsInt());
    }

    @Test
    @DisplayName("int-arg ctor accepts VERTICAL_SPLIT")
    void intArgCtorAcceptsVerticalSplit() {
        SJSplitPane sp = new SJSplitPane(SJSplitPane.VERTICAL_SPLIT);
        assertEquals(SJSplitPane.VERTICAL_SPLIT, sp.getOrientationAsInt());
        assertEquals(SplitLayout.Orientation.VERTICAL, sp.getOrientation());
    }

    @Test
    @DisplayName("int-arg ctor throws IAE on garbage orientation")
    void intArgCtorThrowsIaeOnGarbageOrientation() {
        assertThrows(IllegalArgumentException.class, () -> new SJSplitPane(42));
    }

    // --- Orientation flip ---------------------------------------------------

    @Test
    @DisplayName("setOrientation(int) flips Vaadin orientation")
    void setOrientationIntFlipsVaadinOrientation() {
        SJSplitPane sp = new SJSplitPane();  // HORIZONTAL_SPLIT
        sp.setOrientation(SJSplitPane.VERTICAL_SPLIT);
        assertEquals(SJSplitPane.VERTICAL_SPLIT, sp.getOrientationAsInt());
        assertEquals(SplitLayout.Orientation.VERTICAL, sp.getOrientation());
        sp.setOrientation(SJSplitPane.HORIZONTAL_SPLIT);
        assertEquals(SJSplitPane.HORIZONTAL_SPLIT, sp.getOrientationAsInt());
    }

    @Test
    @DisplayName("setOrientation fires PCE on change, no-op on equal")
    void setOrientationFiresPceOnChangeNoOpOnEqual() {
        SJSplitPane sp = new SJSplitPane();
        List<PropertyChangeEvent> events = new ArrayList<>();
        sp.addPropertyChangeListener(events::add);

        sp.setOrientation(SJSplitPane.VERTICAL_SPLIT);
        List<PropertyChangeEvent> o = events.stream()
                .filter(it -> "orientation".equals(it.getPropertyName()))
                .toList();
        assertEquals(1, o.size(), "exactly one PCE on actual change");
        assertEquals(SJSplitPane.HORIZONTAL_SPLIT, o.get(0).getOldValue());
        assertEquals(SJSplitPane.VERTICAL_SPLIT, o.get(0).getNewValue());

        // No-op on equal state.
        events.clear();
        sp.setOrientation(SJSplitPane.VERTICAL_SPLIT);
        assertTrue(events.stream().noneMatch(it -> "orientation".equals(it.getPropertyName())),
                "no-op on equal orientation should fire no PCE");
    }

    @Test
    @DisplayName("setOrientation throws IAE on garbage")
    void setOrientationThrowsIaeOnGarbage() {
        SJSplitPane sp = new SJSplitPane();
        assertThrows(IllegalArgumentException.class, () -> sp.setOrientation(99));
    }

    // --- Slot setters -------------------------------------------------------

    @Test
    @DisplayName("setLeftComponent maps to addToPrimary")
    void setLeftComponentMapsToAddToPrimary() {
        SJSplitPane sp = new SJSplitPane();
        Button b = new Button("left");
        sp.setLeftComponent(b);
        assertSame(b, sp.getLeftComponent());
        assertSame(b, sp.getPrimaryComponent());
    }

    @Test
    @DisplayName("setTopComponent is an alias for setLeftComponent")
    void setTopComponentIsAnAliasForSetLeftComponent() {
        SJSplitPane sp = new SJSplitPane(SJSplitPane.VERTICAL_SPLIT);
        Button b = new Button("top");
        sp.setTopComponent(b);
        assertSame(b, sp.getTopComponent());
        assertSame(b, sp.getLeftComponent());
    }

    @Test
    @DisplayName("setRightComponent maps to addToSecondary")
    void setRightComponentMapsToAddToSecondary() {
        SJSplitPane sp = new SJSplitPane();
        Button b = new Button("right");
        sp.setRightComponent(b);
        assertSame(b, sp.getRightComponent());
        assertSame(b, sp.getSecondaryComponent());
    }

    @Test
    @DisplayName("setBottomComponent is an alias for setRightComponent")
    void setBottomComponentIsAnAliasForSetRightComponent() {
        SJSplitPane sp = new SJSplitPane(SJSplitPane.VERTICAL_SPLIT);
        Button b = new Button("bottom");
        sp.setBottomComponent(b);
        assertSame(b, sp.getBottomComponent());
    }

    @Test
    @DisplayName("setLeftComponent replaces existing primary occupant")
    void setLeftComponentReplacesExistingPrimaryOccupant() {
        SJSplitPane sp = new SJSplitPane();
        Button a = new Button("a");
        Button b = new Button("b");
        sp.setLeftComponent(a);
        sp.setLeftComponent(b);
        assertSame(b, sp.getLeftComponent());
    }

    @Test
    @DisplayName("setLeftComponent null clears the primary slot")
    void setLeftComponentNullClearsThePrimarySlot() {
        SJSplitPane sp = new SJSplitPane();
        sp.setLeftComponent(new Button("a"));
        sp.setLeftComponent(null);
        assertNull(sp.getLeftComponent());
    }

    // --- setDividerLocation(double) → setSplitterPosition * 100 -------------

    @Test
    @DisplayName("setDividerLocation(double) translates to percent")
    void setDividerLocationDoubleTranslatesToPercent() {
        SJSplitPane sp = new SJSplitPane();
        sp.setDividerLocation(0.3);
        assertEquals(30.0, sp.getSplitterPosition(), 0.001);
        sp.setDividerLocation(0.75);
        assertEquals(75.0, sp.getSplitterPosition(), 0.001);
    }

    @Test
    @DisplayName("getDividerLocation returns -1 sentinel (round-trip lives on emulator)")
    void getDividerLocationReturnsMinusOneSentinel() {
        SJSplitPane sp = new SJSplitPane();
        assertEquals(-1, sp.getDividerLocation());
        sp.setDividerLocation(0.4);
        // Even after setDividerLocation(double), the surrogate-side
        // sentinel stays — int round-trip is an emulator-only concern.
        assertEquals(-1, sp.getDividerLocation());
    }

    // --- resetToPreferredSizes → 50/50 --------------------------------------

    @Test
    @DisplayName("resetToPreferredSizes snaps splitter to 50 percent")
    void resetToPreferredSizesSnapsSplitterTo50Percent() {
        SJSplitPane sp = new SJSplitPane();
        sp.setDividerLocation(0.2);
        sp.resetToPreferredSizes();
        assertEquals(50.0, sp.getSplitterPosition(), 0.001);
        // Best-effort R_best_effort_behaviour — zero-WARN (it's a real impl, not drop-and-WARN).
        assertNoWarns("resetToPreferredSizes is best-effort real impl, not WARN: " + capturedWarns);
    }

    // --- No-counterpart cluster — present-but-drops -------------------------

    @Test
    @DisplayName("setDividerLocation(int) WARNs and drops")
    void setDividerLocationIntWarnsAndDrops() {
        SJSplitPane sp = new SJSplitPane();
        sp.setDividerLocation(240);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setDividerLocation(int)")),
                "int-pixel form should WARN per SD_sjsplitpane blocked-upstream: " + capturedWarns);
    }

    @Test
    @DisplayName("setResizeWeight WARNs and drops and getter returns JDK default 0_0")
    void setResizeWeightWarnsAndDrops() {
        SJSplitPane sp = new SJSplitPane();
        sp.setResizeWeight(0.45);
        assertEquals(0.0, sp.getResizeWeight(), 0.001,
                "no Store shadow; getter returns JDK default");
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setResizeWeight")));
    }

    @Test
    @DisplayName("setOneTouchExpandable WARNs and drops and getter returns false")
    void setOneTouchExpandableWarnsAndDrops() {
        SJSplitPane sp = new SJSplitPane();
        sp.setOneTouchExpandable(true);
        assertFalse(sp.isOneTouchExpandable());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setOneTouchExpandable")));
    }

    @Test
    @DisplayName("setContinuousLayout WARNs and drops and getter returns false")
    void setContinuousLayoutWarnsAndDrops() {
        SJSplitPane sp = new SJSplitPane();
        sp.setContinuousLayout(true);
        assertFalse(sp.isContinuousLayout());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setContinuousLayout")));
    }

    @Test
    @DisplayName("setDividerSize WARNs and drops and getter returns 0")
    void setDividerSizeWarnsAndDrops() {
        SJSplitPane sp = new SJSplitPane();
        sp.setDividerSize(20);
        assertEquals(0, sp.getDividerSize());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setDividerSize")));
    }

    @Test
    @DisplayName("setLastDividerLocation WARNs and drops and getter returns -1")
    void setLastDividerLocationWarnsAndDrops() {
        SJSplitPane sp = new SJSplitPane();
        sp.setLastDividerLocation(100);
        assertEquals(-1, sp.getLastDividerLocation());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setLastDividerLocation")));
    }

    // --- Sentinel min/max getters ------------------------------------------

    @Test
    @DisplayName("getMinimumDividerLocation WARNs and returns -1")
    void getMinimumDividerLocationWarnsAndReturnsMinusOne() {
        SJSplitPane sp = new SJSplitPane();
        assertEquals(-1, sp.getMinimumDividerLocation());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("getMinimumDividerLocation")));
    }

    @Test
    @DisplayName("getMaximumDividerLocation WARNs and returns -1")
    void getMaximumDividerLocationWarnsAndReturnsMinusOne() {
        SJSplitPane sp = new SJSplitPane();
        assertEquals(-1, sp.getMaximumDividerLocation());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("getMaximumDividerLocation")));
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID returns SplitPaneUI")
    void getUiClassIdReturnsSplitPaneUi() {
        assertEquals("SplitPaneUI", new SJSplitPane().getUIClassID());
    }

    @Test
    @DisplayName("setUI WARNs (L&F surgery not modelled)")
    void setUiWarns() {
        SJSplitPane sp = new SJSplitPane();
        sp.setUI(null);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setUI")));
    }

    @Test
    @DisplayName("updateUI is a no-op")
    void updateUiIsANoOp() {
        SJSplitPane sp = new SJSplitPane();
        sp.updateUI();
        assertNoWarns("updateUI is L&F-noop, zero-WARN: " + capturedWarns);
    }

    // --- data-swing-class stamp ---------------------------------------------

    @Test
    @DisplayName("data-swing-class stamped via _installSwingClass")
    void dataSwingClassStamped() {
        SJSplitPane sp = new SJSplitPane();
        assertEquals("SJSplitPane", sp.getElement().getAttribute("data-swing-class"));
    }
}
