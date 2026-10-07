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
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for the SButton surrogate — the AWT 1.0 {@code java.awt.Button}, not
 * {@code JButton}. Covers:
 *
 * <ol>
 *  <li>Ctors + the R_vaadin_first lossy {@code setLabel(null)} → {@code ""} round-trip.
 *  <li>actionCommand round-trip and its falls-back-to-label rule.
 *  <li>ActionListener fan-out with source=SButton on a real peer click.
 *  <li>processActionEvent as the single dispatch funnel (subclass hook).
 *  <li>The inherited ComponentMixin surface works on a non-JComponent host.
 *  <li>Zero stub WARNs across the happy path.
 * </ol>
 */
class SButtonTest extends AbstractKaribuTest {

    // --- Ctors / label ------------------------------------------------

    @Test
    @DisplayName("no-arg ctor leaves the label empty per R_vaadin_first lossy via HasText")
    void noArgCtorLeavesTheLabelEmpty() {
        // Vaadin Button.getText returns "" for an empty textNode. The
        // emulator vaadinx.awt.Button preserves AWT's null at its own
        // field shadow; the surrogate deliberately doesn't shadow it.
        assertEquals("", new SButton().getLabel());
    }

    @Test
    @DisplayName("string ctor seeds the label")
    void stringCtorSeedsTheLabel() {
        assertEquals("Quit", new SButton("Quit").getLabel());
    }

    @Test
    @DisplayName("the label never wraps, as AWT paints it")
    void theLabelNeverWraps() {
        assertEquals("nowrap", new SButton("Add Item").getElement().getStyle().get("white-space"));
    }

    @Test
    @DisplayName("setLabel null renders blank rather than NPEing the peer")
    void setLabelNullRendersBlankRatherThanNpeingThePeer() {
        // Vaadin's setText NPEs on null; setLabel maps it to "" so a
        // migrated app calling setLabel(null) renders a blank button
        // instead of blowing up.
        SButton b = new SButton("x");
        b.setLabel(null);
        assertEquals("", b.getLabel());
    }

    @Test
    @DisplayName("setLabel reaches the rendered Vaadin caption")
    void setLabelReachesTheRenderedVaadinCaption() {
        SButton b = new SButton("before");
        UI.getCurrent().add(b);
        b.setLabel("after");
        assertEquals("after", LocatorJ._get(Button.class).getText());
    }

    // --- actionCommand ------------------------------------------------

    @Test
    @DisplayName("actionCommand falls back to the label when never set")
    void actionCommandFallsBackToTheLabelWhenNeverSet() {
        assertEquals("OK", new SButton("OK").getActionCommand());
    }

    @Test
    @DisplayName("setActionCommand overrides the label fallback")
    void setActionCommandOverridesTheLabelFallback() {
        SButton b = new SButton("Save");
        b.setActionCommand("save-file");
        assertEquals("save-file", b.getActionCommand());
    }

    @Test
    @DisplayName("clearing actionCommand restores the label fallback")
    void clearingActionCommandRestoresTheLabelFallback() {
        // AWT's rule is "null means fall back", not "null means empty" —
        // a migrated app that resets the command must get the label back.
        SButton b = new SButton("Save");
        b.setActionCommand("save-file");
        b.setActionCommand(null);
        assertEquals("Save", b.getActionCommand());
    }

    // --- ActionListener fan-out ---------------------------------------

    @Test
    @DisplayName("user click on attached button fires ActionListener with source=SButton")
    void userClickOnAttachedButtonFiresActionListenerWithSourceSButton() {
        SButton b = new SButton("Go");
        UI.getCurrent().add(b);
        List<ActionEvent> events = new ArrayList<>();
        b.addActionListener(events::add);

        LocatorJ._click(LocatorJ._get(Button.class));

        assertEquals(1, events.size());
        assertSame(b, events.get(0).getSource());
        assertEquals("Go", events.get(0).getActionCommand());
        assertEquals(ActionEvent.ACTION_PERFORMED, events.get(0).getID());
    }

    @Test
    @DisplayName("click carries the explicit actionCommand once set")
    void clickCarriesTheExplicitActionCommandOnceSet() {
        SButton b = new SButton("Go");
        b.setActionCommand("go-cmd");
        UI.getCurrent().add(b);
        List<String> commands = new ArrayList<>();
        b.addActionListener(e -> commands.add(e.getActionCommand()));

        LocatorJ._click(LocatorJ._get(Button.class));

        assertEquals(List.of("go-cmd"), commands);
    }

    @Test
    @DisplayName("addRemoveActionListener round-trips through getActionListeners")
    void addRemoveActionListenerRoundTripsThroughGetActionListeners() {
        SButton b = new SButton();
        ActionListener l = e -> {
        };
        assertEquals(0, b.getActionListeners().length);
        b.addActionListener(l);
        assertEquals(1, b.getActionListeners().length);
        assertSame(l, b.getActionListeners()[0]);
        b.removeActionListener(l);
        assertEquals(0, b.getActionListeners().length);
    }

    @Test
    @DisplayName("null listeners are ignored rather than stored")
    void nullListenersAreIgnoredRatherThanStored() {
        // AWT silently no-ops on null; storing one would NPE at dispatch.
        SButton b = new SButton("x");
        b.addActionListener(null);
        b.removeActionListener(null);
        UI.getCurrent().add(b);
        LocatorJ._click(LocatorJ._get(Button.class));   // must not throw
        assertEquals(0, b.getActionListeners().length);
    }

    @Test
    @DisplayName("processActionEvent is the single funnel a subclass can intercept")
    void processActionEventIsTheSingleFunnelASubclassCanIntercept() {
        // The AWT idiom: override processActionEvent, inspect, call super.
        // If the peer click bypassed it, the override would never see the
        // browser-originated action.
        List<String> seen = new ArrayList<>();
        SButton b = new SButton("Tap") {
            @Override
            protected void processActionEvent(ActionEvent e) {
                seen.add(e.getActionCommand());
                super.processActionEvent(e);
            }
        };
        List<String> delivered = new ArrayList<>();
        b.addActionListener(e -> delivered.add(e.getActionCommand()));
        UI.getCurrent().add(b);

        LocatorJ._click(LocatorJ._get(Button.class));

        assertEquals(List.of("Tap"), seen);
        assertEquals(List.of("Tap"), delivered);
    }

    @Test
    @DisplayName("self-updating caption end-to-end")
    void selfUpdatingCaptionEndToEnd() {
        SButton b = new SButton("Press me");
        b.addActionListener(e -> b.setLabel("Pressed"));
        UI.getCurrent().add(b);

        assertEquals("Press me", LocatorJ._get(Button.class).getText());
        LocatorJ._click(LocatorJ._get(Button.class));
        assertEquals("Pressed", LocatorJ._get(Button.class).getText());
    }

    // --- Inherited ComponentMixin surface -----------------------------

    @Test
    @DisplayName("ComponentMixin works on this non-JComponent host")
    void componentMixinWorksOnThisNonJComponentHost() {
        // The point of the AWT lane: ComponentMixin is AWT-level, so a
        // surrogate for a plain java.awt.Component leaf picks up name /
        // colour / enabled without any JComponentMixin in the chain.
        SButton b = new SButton("x");
        b.setName("quit-button");
        assertEquals("quit-button", b.getName());
        assertEquals("quit-button", b.getElement().getAttribute("data-swing-name"));

        b.setForeground(Color.RED);
        assertEquals(Color.RED, b.getForeground());

        b.setEnabled(false);
        assertFalse(b.isEnabled());
    }

    @Test
    @DisplayName("paramString reports the label")
    void paramStringReportsTheLabel() {
        assertEquals("label=Quit", new SButton("Quit").paramString());
    }

    @Test
    @DisplayName("getAccessibleContext WARNs and returns null")
    void getAccessibleContextWarnsAndReturnsNull() {
        assertNull(new SButton().getAccessibleContext());
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("getAccessibleContext"));
    }

    // --- Exit gate ----------------------------------------------------

    @Test
    @DisplayName("happy path fires no stub WARNs")
    void happyPathFiresNoStubWarns() {
        SButton b = new SButton("Go");
        b.setActionCommand("go");
        b.setName("go-button");
        b.setEnabled(true);
        UI.getCurrent().add(b);
        b.addActionListener(e -> {
        });
        LocatorJ._click(LocatorJ._get(Button.class));
        b.setLabel("Done");
        assertNoWarns("no stub WARNs expected from SButton's happy path");
    }
}
