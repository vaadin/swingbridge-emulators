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
import com.vaadin.flow.component.checkbox.Checkbox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SCheckbox surrogate — the AWT 1.0 {@code java.awt.Checkbox},
 * not {@code JCheckBox}. Covers:
 *
 * <ol>
 *  <li>Ctors and the R_vaadin_first lossy label round-trip (both directions of it).
 *  <li>The AWT event rule: only a from-client toggle posts an ItemEvent.
 *  <li>{@code getSelectedObjects}' null-or-single convention.
 *  <li>The radio look as a DOM-backed theme name, appearance only.
 *  <li>processItemEvent as the single dispatch funnel (subclass hook).
 *  <li>The inherited ComponentMixin surface works on a non-JComponent host.
 *  <li>Zero stub WARNs across the happy path.
 * </ol>
 *
 * <p>Karibu note that the event tests rest on: {@code _setValue(v)} sets
 * {@code isFromClient = true}, while a plain server-side {@code setValue}/{@code setState}
 * does not — so the from-client gate is exactly testable browserlessly.
 */
class SCheckboxTest extends AbstractKaribuTest {

    /** The rendered peer, as Karibu locates it. */
    private static Checkbox peer() {
        return LocatorJ._get(Checkbox.class);
    }

    // --- Ctors / label ------------------------------------------------

    @Test
    @DisplayName("no-arg ctor is unchecked with no label")
    void noArgCtorIsUncheckedWithNoLabel() {
        SCheckbox c = new SCheckbox();
        assertFalse(c.getState());
        // An untouched Vaadin label property reads null — the inverse of the
        // set-null case below, and the reason the emulator shadows the field.
        assertNull(c.getLabel());
    }

    @Test
    @DisplayName("string ctor seeds the label")
    void stringCtorSeedsTheLabel() {
        assertEquals("Verbose", new SCheckbox("Verbose").getLabel());
    }

    @Test
    @DisplayName("two-arg ctor seeds both label and state")
    void twoArgCtorSeedsBothLabelAndState() {
        SCheckbox c = new SCheckbox("Verbose", true);
        assertEquals("Verbose", c.getLabel());
        assertTrue(c.getState());
    }

    @Test
    @DisplayName("setLabel null reads back as empty per R_vaadin_first lossy")
    void setLabelNullReadsBackAsEmpty() {
        // Vaadin's label property can't hold null. AWT's null survives on the
        // emulator's field shadow, not here.
        SCheckbox c = new SCheckbox("x");
        c.setLabel(null);
        assertEquals("", c.getLabel());
    }

    @Test
    @DisplayName("setLabel reaches the rendered Vaadin caption")
    void setLabelReachesTheRenderedVaadinCaption() {
        SCheckbox c = new SCheckbox("before");
        UI.getCurrent().add(c);
        c.setLabel("after");
        assertEquals("after", peer().getLabel());
    }

    // --- state --------------------------------------------------------

    @Test
    @DisplayName("setState round-trips through the Vaadin checked property")
    void setStateRoundTripsThroughTheVaadinCheckedProperty() {
        SCheckbox c = new SCheckbox("x");
        c.setState(true);
        assertTrue(c.getState());
        assertEquals(true, c.getValue());
        c.setState(false);
        assertFalse(c.getState());
    }

    @Test
    @DisplayName("state reaches the rendered element")
    void stateReachesTheRenderedElement() {
        SCheckbox c = new SCheckbox("x");
        UI.getCurrent().add(c);
        c.setState(true);
        assertEquals(true, peer().getValue());
    }

    // --- The AWT event rule -------------------------------------------

    @Test
    @DisplayName("browser toggle fires one ItemEvent with source=SCheckbox")
    void browserToggleFiresOneItemEvent() {
        SCheckbox c = new SCheckbox("Verbose");
        UI.getCurrent().add(c);
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);

        LocatorJ._setValue(peer(), true);

        assertEquals(1, events.size());
        assertSame(c, events.get(0).getSource());
        assertSame(c, events.get(0).getItemSelectable());
        assertEquals("Verbose", events.get(0).getItem());
        assertEquals(ItemEvent.SELECTED, events.get(0).getStateChange());
        assertEquals(ItemEvent.ITEM_STATE_CHANGED, events.get(0).getID());
    }

    @Test
    @DisplayName("browser un-toggle fires DESELECTED")
    void browserUnToggleFiresDeselected() {
        SCheckbox c = new SCheckbox("Verbose", true);
        UI.getCurrent().add(c);
        List<Integer> changes = new ArrayList<>();
        c.addItemListener(e -> changes.add(e.getStateChange()));

        LocatorJ._setValue(peer(), false);

        assertEquals(List.of(ItemEvent.DESELECTED), changes);
    }

    @Test
    @DisplayName("setState fires nothing — the AWT rule that inverts Swing")
    void setStateFiresNothing() {
        // The single most important fact in the slice. In Swing,
        // setSelected drives a ButtonModel fan-out and an ItemEvent lands;
        // in AWT only the toolkit posts, so a programmatic change is silent.
        SCheckbox c = new SCheckbox("Verbose");
        UI.getCurrent().add(c);
        List<ItemEvent> events = new ArrayList<>();
        c.addItemListener(events::add);

        c.setState(true);
        c.setValue(false);
        c.setState(true);

        assertEquals(List.of(), events);
    }

    // --- getSelectedObjects -------------------------------------------

    @Test
    @DisplayName("getSelectedObjects is null when unchecked and single when checked")
    void getSelectedObjectsIsNullWhenUncheckedAndSingleWhenChecked() {
        // AWT's convention here — java.awt.List deliberately differs (SList), so
        // this must not be copied across.
        SCheckbox c = new SCheckbox("Verbose");
        assertNull(c.getSelectedObjects());
        c.setState(true);
        assertEquals(List.of("Verbose"), List.of(c.getSelectedObjects()));
    }

    // --- radio look ---------------------------------------------------

    @Test
    @DisplayName("radioLook round-trips through the theme name")
    void radioLookRoundTripsThroughTheThemeName() {
        SCheckbox c = new SCheckbox("metric");
        assertFalse(c.isRadioLook());
        c.setRadioLook(true);
        assertTrue(c.isRadioLook());
        assertTrue(c.getElement().getThemeList().contains("emul-radio"));
        c.setRadioLook(false);
        assertFalse(c.isRadioLook());
        assertFalse(c.getElement().getThemeList().contains("emul-radio"));
    }

    @Test
    @DisplayName("radioLook is appearance only — the box still un-checks")
    void radioLookIsAppearanceOnly() {
        // Mutual exclusion is the emulator's job (D_buttongroup_browser_click). A standalone
        // surrogate in radio look behaves like the checkbox it is.
        SCheckbox c = new SCheckbox("metric", true);
        c.setRadioLook(true);
        UI.getCurrent().add(c);
        List<Integer> changes = new ArrayList<>();
        c.addItemListener(e -> changes.add(e.getStateChange()));

        LocatorJ._setValue(peer(), false);

        assertFalse(c.getState());
        assertEquals(List.of(ItemEvent.DESELECTED), changes);
    }

    // --- ItemListener fan-out -----------------------------------------

    @Test
    @DisplayName("addRemoveItemListener round-trips through getItemListeners")
    void addRemoveItemListenerRoundTrips() {
        SCheckbox c = new SCheckbox();
        ItemListener l = e -> {
        };
        assertEquals(0, c.getItemListeners().length);
        c.addItemListener(l);
        assertEquals(1, c.getItemListeners().length);
        assertSame(l, c.getItemListeners()[0]);
        c.removeItemListener(l);
        assertEquals(0, c.getItemListeners().length);
    }

    @Test
    @DisplayName("null listeners are ignored rather than stored")
    void nullListenersAreIgnoredRatherThanStored() {
        SCheckbox c = new SCheckbox("x");
        c.addItemListener(null);
        c.removeItemListener(null);
        UI.getCurrent().add(c);
        LocatorJ._setValue(peer(), true);   // must not throw
        assertEquals(0, c.getItemListeners().length);
    }

    @Test
    @DisplayName("listeners fire in AWT's first-registered-first order")
    void listenersFireInFirstRegisteredFirstOrder() {
        SCheckbox c = new SCheckbox("x");
        UI.getCurrent().add(c);
        List<String> order = new ArrayList<>();
        c.addItemListener(e -> order.add("a"));
        c.addItemListener(e -> order.add("b"));
        c.addItemListener(e -> order.add("c"));

        LocatorJ._setValue(peer(), true);

        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    @DisplayName("processItemEvent is the single funnel a subclass can intercept")
    void processItemEventIsTheSingleFunnel() {
        List<Integer> seen = new ArrayList<>();
        SCheckbox c = new SCheckbox("Tap") {
            @Override
            protected void processItemEvent(ItemEvent e) {
                seen.add(e.getStateChange());
                super.processItemEvent(e);
            }
        };
        List<Integer> delivered = new ArrayList<>();
        c.addItemListener(e -> delivered.add(e.getStateChange()));
        UI.getCurrent().add(c);

        LocatorJ._setValue(peer(), true);

        assertEquals(List.of(ItemEvent.SELECTED), seen);
        assertEquals(List.of(ItemEvent.SELECTED), delivered);
    }

    // --- Inherited ComponentMixin surface -----------------------------

    @Test
    @DisplayName("ComponentMixin works on this non-JComponent host")
    void componentMixinWorksOnThisNonJComponentHost() {
        SCheckbox c = new SCheckbox("x");
        c.setName("verbose-box");
        assertEquals("verbose-box", c.getName());
        assertEquals("verbose-box", c.getElement().getAttribute("data-swing-name"));

        c.setForeground(Color.RED);
        assertEquals(Color.RED, c.getForeground());

        c.setEnabled(false);
        assertFalse(c.isEnabled());

        c.validate();   // widened to public over Vaadin's protected validate()
    }

    @Test
    @DisplayName("paramString omits the label clause when never set")
    void paramStringOmitsTheLabelClauseWhenNeverSet() {
        assertEquals("state=false", new SCheckbox().paramString());
        assertEquals("label=Verbose,state=true", new SCheckbox("Verbose", true).paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        assertNull(new SCheckbox().getAccessibleContext());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("getAccessibleContext"));
    }

    // --- Exit gate ----------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SCheckbox c = new SCheckbox("Verbose");
        c.setName("verbose-box");
        c.setEnabled(true);
        c.setRadioLook(true);
        UI.getCurrent().add(c);
        c.addItemListener(e -> {
        });
        LocatorJ._setValue(peer(), true);
        c.setState(false);
        c.setLabel("Quiet");
        c.getSelectedObjects();
        assertNoWarns("no stub WARNs expected from SCheckbox's happy path");
    }
}
