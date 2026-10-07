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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjtogglebutton_button_peer's Button-backed SJToggleButton surrogate. Covers:
 *
 * <ol>
 *  <li>Peer is a Vaadin Button (pressed chrome), NOT a Checkbox.
 *  <li>Default model is JToggleButton.ToggleButtonModel; text default "".
 *  <li>setSelected: Item + Change fire, no Action; aria-pressed reflects;
 *      feedback-loop-safe.
 *  <li>doClick: full Item + Change + Action per ToggleButtonModel pulse.
 *  <li>Browser click (ClickNotifier) drives the same sequence.
 *  <li>setModel swap — push wire torn down on old, reinstalled on new.
 *  <li>setText routes through Button.getText; null → "" (R_vaadin_first lossy).
 *  <li>Layout binds for real (inline-flex CSS) — unlike the Checkbox glyph
 *      siblings; R_match_swing_errors IAE still on bad axis.
 *  <li>setIcon works Vaadin-first (Button icon slot) — no WARN.
 *  <li>setAction wiring (NAME, enabled propagation).
 *  <li>Happy-path zero stub WARNs.
 * </ol>
 */
class SJToggleButtonTest extends AbstractKaribuTest {

    private static String ariaPressed(SJToggleButton b) {
        return b.getElement().getAttribute("aria-pressed");
    }

    /** An Action whose body does nothing — only its NAME and enabled state matter here. */
    private static AbstractAction namedAction(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
    }

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("peer is a Vaadin Button not a Checkbox")
    void peerIsAVaadinButtonNotACheckbox() {
        // Widened to Component so the negative instance check is legal —
        // the static SJToggleButton type already excludes Checkbox.
        com.vaadin.flow.component.Component b = new SJToggleButton("Bold");
        assertInstanceOf(Button.class, b);
        assertFalse(b instanceof Checkbox);
    }

    @Test
    @DisplayName("no-arg ctor leaves text empty per R_vaadin_first lossy via HasText")
    void noArgCtorLeavesTextEmpty() {
        assertEquals("", new SJToggleButton().getText());
    }

    @Test
    @DisplayName("string ctor seeds text and actionCommand falls back to it")
    void stringCtorSeedsTextAndActionCommandFallsBack() {
        SJToggleButton b = new SJToggleButton("Bold");
        assertEquals("Bold", b.getText());
        assertEquals("Bold", ((Button) b).getText());
        assertEquals("Bold", b.getActionCommand());
    }

    @Test
    @DisplayName("text + selected ctor seeds both and renders pressed")
    void textPlusSelectedCtorSeedsBothAndRendersPressed() {
        SJToggleButton b = new SJToggleButton("Italic", true);
        assertEquals("Italic", b.getText());
        assertTrue(b.isSelected());
        assertEquals("true", ariaPressed(b));
    }

    @Test
    @DisplayName("default model is JToggleButton ToggleButtonModel")
    void defaultModelIsToggleButtonModel() {
        // ToggleButtonModel gives press→release-while-armed toggle for free;
        // SJButton uses DefaultButtonModel — this must NOT inherit that.
        assertInstanceOf(JToggleButton.ToggleButtonModel.class, new SJToggleButton().getModel());
    }

    @Test
    @DisplayName("UIClassID is ToggleButtonUI")
    void uiClassIdIsToggleButtonUi() {
        assertEquals("ToggleButtonUI", new SJToggleButton().getUIClassID());
    }

    // --- setText ------------------------------------------------------

    @Test
    @DisplayName("setText routes through Button text and fires PCE")
    void setTextRoutesThroughButtonTextAndFiresPce() {
        SJToggleButton b = new SJToggleButton("one");
        List<PropertyChangeEvent> events = new ArrayList<>();
        b.addPropertyChangeListener("text", events::add);
        b.setText("two");
        assertEquals("two", b.getText());
        assertEquals("two", ((Button) b).getText());
        assertEquals(1, events.size());
        assertEquals("one", events.get(0).getOldValue());
        assertEquals("two", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setText null reads back as empty string per R_vaadin_first")
    void setTextNullReadsBackAsEmptyString() {
        SJToggleButton b = new SJToggleButton("seed");
        b.setText(null);
        assertEquals("", b.getText());
    }

    // --- setSelected programmatic ------------------------------------

    @Test
    @DisplayName("setSelected reflects onto aria-pressed")
    void setSelectedReflectsOntoAriaPressed() {
        SJToggleButton b = new SJToggleButton();
        b.setSelected(true);
        assertEquals("true", ariaPressed(b));
        b.setSelected(false);
        assertEquals("false", ariaPressed(b));
    }

    @Test
    @DisplayName("setSelected fires ItemEvent SELECTED and ChangeEvent but not Action")
    void setSelectedFiresItemAndChangeButNotAction() {
        SJToggleButton b = new SJToggleButton("X");
        List<Integer> items = new ArrayList<>();
        Counter changes = new Counter();
        Counter actions = new Counter();
        b.addItemListener(e -> items.add(e.getStateChange()));
        b.addChangeListener(e -> changes.inc());
        b.addActionListener(e -> actions.inc());

        b.setSelected(true);

        assertEquals(List.of(ItemEvent.SELECTED), items);
        assertTrue(changes.get() >= 1);
        actions.assertEquals(0);
    }

    @Test
    @DisplayName("setSelected to same value is a no-op")
    void setSelectedToSameValueIsANoOp() {
        SJToggleButton b = new SJToggleButton();
        Counter hits = new Counter();
        b.addItemListener(e -> hits.inc());
        b.setSelected(false);  // already false
        hits.assertEquals(0);
    }

    // --- doClick (full pulse via ToggleButtonModel) ------------------

    @Test
    @DisplayName("doClick fires Item before Action and all three families fire")
    void doClickFiresItemBeforeAction() {
        SJToggleButton b = new SJToggleButton("toggle");
        List<String> log = new ArrayList<>();
        b.addItemListener(e -> log.add("item:" + e.getStateChange()));
        b.addChangeListener(e -> log.add("change"));
        b.addActionListener(e -> log.add("action:" + e.getActionCommand()));

        b.doClick();

        assertTrue(b.isSelected());
        assertEquals("true", ariaPressed(b));
        int itemIdx = log.indexOf("item:" + ItemEvent.SELECTED);
        int actionIdx = log.indexOf("action:toggle");
        assertTrue(itemIdx >= 0, "Item event missing: " + log);
        assertTrue(actionIdx >= 0, "Action event missing: " + log);
        assertTrue(itemIdx < actionIdx, "Item must fire before Action: " + log);
        assertTrue(log.contains("change"), "Change must fire: " + log);
    }

    @Test
    @DisplayName("doClick on selected toggles back to deselected")
    void doClickOnSelectedTogglesBackToDeselected() {
        SJToggleButton b = new SJToggleButton("t", true);
        List<Integer> items = new ArrayList<>();
        b.addItemListener(e -> items.add(e.getStateChange()));

        b.doClick();

        assertFalse(b.isSelected());
        assertEquals("false", ariaPressed(b));
        assertEquals(List.of(ItemEvent.DESELECTED), items);
    }

    // --- Browser-originated click ------------------------------------

    @Test
    @DisplayName("user click while hosted drives the full event sequence end-to-end")
    void userClickWhileHostedDrivesTheFullEventSequence() {
        SJToggleButton b = new SJToggleButton("Toggle");
        UI.getCurrent().add(b);
        List<Integer> items = new ArrayList<>();
        Counter actions = new Counter();
        b.addItemListener(e -> items.add(e.getStateChange()));
        b.addActionListener(e -> actions.inc());

        LocatorJ._click(LocatorJ._get(Button.class));

        assertTrue(b.isSelected());
        assertEquals("true", ariaPressed(b));
        assertEquals(List.of(ItemEvent.SELECTED), items);
        actions.assertEquals(1);
    }

    // --- ButtonModel swap --------------------------------------------

    @Test
    @DisplayName("setModel installs new model, seeds aria-pressed, tears down old push wire")
    void setModelInstallsNewModelAndTearsDownOldPushWire() {
        SJToggleButton b = new SJToggleButton();
        ButtonModel first = b.getModel();
        ButtonModel second = new JToggleButton.ToggleButtonModel();

        b.setModel(second);

        assertSame(second, b.getModel());
        assertNotSame(first, b.getModel());

        // Old model's push wire detached: mutating it must NOT reach aria-pressed.
        first.setSelected(true);
        assertEquals("false", ariaPressed(b));

        // New model's push wire installed: mutating it DOES reach aria-pressed.
        second.setSelected(true);
        assertEquals("true", ariaPressed(b));
    }

    @Test
    @DisplayName("setModel fires the model PCE")
    void setModelFiresTheModelPce() {
        SJToggleButton b = new SJToggleButton();
        List<PropertyChangeEvent> events = new ArrayList<>();
        b.addPropertyChangeListener("model", events::add);
        b.setModel(new JToggleButton.ToggleButtonModel());
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setModel to a plain DefaultButtonModel still plumbs selection")
    void setModelToAPlainDefaultButtonModelStillPlumbsSelection() {
        SJToggleButton b = new SJToggleButton();
        b.setModel(new DefaultButtonModel());
        b.setSelected(true);
        assertTrue(b.isSelected());
        assertEquals("true", ariaPressed(b));
    }

    // --- Action wiring ------------------------------------------------

    @Test
    @DisplayName("setAction copies NAME into text and registers Action as ActionListener")
    void setActionCopiesNameIntoText() {
        AbstractAction a = namedAction("ActionName");
        SJToggleButton b = new SJToggleButton();
        b.setAction(a);
        assertEquals("ActionName", b.getText());
        assertEquals(1, b.getActionListeners().length);
    }

    @Test
    @DisplayName("Action enabled state propagates to the button")
    void actionEnabledStatePropagatesToTheButton() {
        AbstractAction a = namedAction("X");
        a.setEnabled(false);
        SJToggleButton b = new SJToggleButton();
        b.setAction(a);
        assertFalse(b.isEnabled());
    }

    // --- Layout binds for real (Button host honors inline-flex) ------

    @Test
    @DisplayName("host runs inline-flex with JDK defaults wired")
    void hostRunsInlineFlexWithJdkDefaultsWired() {
        SJToggleButton b = new SJToggleButton();
        Style style = b.getElement().getStyle();
        assertEquals("row", style.get("flex-direction"));
        assertEquals("center", style.get("justify-content"));
        assertEquals("center", style.get("align-items"));
        assertEquals("4px", style.get("gap"));
        assertEquals("nowrap", style.get("white-space"), "a JDK button's caption never wraps");
    }

    @Test
    @DisplayName("setHorizontalAlignment binds to CSS with LEADING canonical readback and no WARN")
    void setHorizontalAlignmentBindsToCss() {
        // Unlike the Checkbox glyph siblings (drop-and-WARN), the Button
        // peer honors host flex, so alignment binds via the mixin (SD_border_css_lossy lossy).
        SJToggleButton b = new SJToggleButton();
        b.setHorizontalAlignment(SwingConstants.LEFT);
        assertEquals(SwingConstants.LEADING, b.getHorizontalAlignment());
        assertEquals("flex-start", b.getElement().getStyle().get("justify-content"));
        assertNoWarns("alignment is wired for the Button peer, not drop-and-WARN");
    }

    @Test
    @DisplayName("setIconTextGap round-trips and writes CSS gap")
    void setIconTextGapRoundTripsAndWritesCssGap() {
        SJToggleButton b = new SJToggleButton();
        b.setIconTextGap(12);
        assertEquals(12, b.getIconTextGap());
        assertEquals("12px", b.getElement().getStyle().get("gap"));
        assertNoWarns();
    }

    @Test
    @DisplayName("setHorizontalAlignment rejects bad axis with IAE per R_match_swing_errors")
    void setHorizontalAlignmentRejectsBadAxis() {
        assertThrows(IllegalArgumentException.class,
                () -> new SJToggleButton().setHorizontalAlignment(SwingConstants.TOP));
    }

    @Test
    @DisplayName("setVerticalAlignment rejects bad axis with IAE per R_match_swing_errors")
    void setVerticalAlignmentRejectsBadAxis() {
        assertThrows(IllegalArgumentException.class,
                () -> new SJToggleButton().setVerticalAlignment(SwingConstants.LEFT));
    }

    // --- Icon works Vaadin-first (Button has an icon slot) -----------

    @Test
    @DisplayName("setIcon ImageIcon installs a Vaadin Image on the peer with no WARN")
    void setIconImageIconInstallsAVaadinImage() {
        // The whole point of the Button peer: an icon slot exists, so an
        // ImageIcon renders (Path 1) — the Checkbox glyph siblings drop it.
        SJToggleButton b = new SJToggleButton();
        b.setIcon(new ImageIcon(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)));
        assertNoWarns("Button peer has an icon slot — ImageIcon should not WARN");
        assertNotNull(b.getIcon());
        assertInstanceOf(Image.class, b.getIcon());
    }

    @Test
    @DisplayName("setIcon null clears the slot and is silent")
    void setIconNullClearsTheSlotAndIsSilent() {
        SJToggleButton b = new SJToggleButton();
        b.setIcon((Icon) null);
        assertNull(b.getIcon());
        assertNoWarns();
    }

    // --- Happy-path zero-WARN ----------------------------------------

    @Test
    @DisplayName("happy-path UI-functional surface is WARN-free")
    void happyPathUiFunctionalSurfaceIsWarnFree() {
        SJToggleButton b = new SJToggleButton("Subscribe");
        UI.getCurrent().add(b);
        b.setActionCommand("subscribe-cmd");
        b.setSelected(true);
        b.doClick();
        b.setSelected(false);
        b.setText("Other");

        assertNoWarns("Warns: " + capturedWarns);
    }
}
