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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonModel;
import javax.swing.DefaultButtonModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;

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
 * Exit gate for SD_sjbutton's SJButton surrogate, R_vaadin_first-trimmed. Covers:
 *
 * <ol>
 *  <li>Constructors + defaultCapable / isDefaultButton / UIClassID.
 *  <li>Text via element-property round-trip; setText(null) returns "" (R_vaadin_first).
 *  <li>actionCommand round-trip (UI-functional, store).
 *  <li>ActionListener fan-out with source=SJButton for doClick + peer click.
 *  <li>ChangeListener fan-out on armed/pressed transitions.
 *  <li>ButtonModel swap — listeners re-wired, old model's listeners
 *      detached, "model" PCE fires.
 *  <li>setAction wiring — NAME / ACTION_COMMAND_KEY / enabled propagation
 *      + Action.actionPerformed on click.
 *  <li>Mnemonic Shortcuts install/teardown (drives UI).
 *  <li>Alignment IAE validation per SwingConstants (R_match_swing_errors still applies).
 *  <li>R_vaadin_first drop-and-WARN setters — non-default values WARN, default values
 *      are silent; getters return the JDK default.
 *  <li>Happy-path zero stub WARNs across the UI-functional surface.
 * </ol>
 */
class SJButtonTest extends AbstractKaribuTest {

    /** Collects PCEs for one named property. */
    private static List<PropertyChangeEvent> recordPces(SJButton b, String property) {
        final List<PropertyChangeEvent> events = new ArrayList<>();
        b.addPropertyChangeListener(property, events::add);
        return events;
    }

    /** An Action whose body does nothing — only its NAME/enabled/command matter. */
    private static AbstractAction inertAction(String name) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
    }

    private static ImageIcon imageIcon(int size) {
        return new ImageIcon(new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB));
    }

    private static Style style(SJButton b) {
        return b.getElement().getStyle();
    }

    // --- Ctors / defaults --------------------------------------------

    @Test
    @DisplayName("no-arg ctor leaves text empty per R_vaadin_first lossy via HasText")
    void noArgCtorTextIsEmpty() {
        // Mixin reads via Vaadin Button.getText (HasText) which returns
        // "" for an empty textNode. R_vaadin_first documents this as the canonical
        // lossy direction — emulator-layer JButton preserves null at its
        // own field shadow. Was null pre-Track-A when the mixin read
        // getProperty("text"); the property-based read missed Vaadin
        // Button's actual rendered text (latent bug fixed by the
        // HasText switch).
        assertEquals("", new SJButton().getText());
    }

    @Test
    @DisplayName("string ctor seeds text and actionCommand falls back to it")
    void stringCtorSeedsTextAndCommand() {
        final SJButton b = new SJButton("OK");
        assertEquals("OK", b.getText());
        assertEquals("OK", b.getActionCommand());
    }

    @Test
    @DisplayName("setText reaches Vaadin Button textNode (rendered caption)")
    void setTextReachesTextNode() {
        // Regression for the latent bug pre-fix: mixin used to write
        // element.setProperty("text", ...) which Vaadin Button doesn't
        // read for rendering — round-trip via getProperty matched but
        // the browser caption stayed empty. Mixin's setText is now
        // abstract; SJButton.setText calls super.setText (Vaadin
        // Button.setText → textSupport → rendered) and then fires PCE.
        final SJButton b = new SJButton();
        b.setText("Save");
        // getText reads from textNode (Button.getText, satisfies the
        // abstract method via inheritance). If setText wrote to the
        // wrong place, this would return "" or null.
        assertEquals("Save", b.getText());
    }

    @Test
    @DisplayName("setText fires text PCE with old and new values")
    void setTextFiresPce() {
        final SJButton b = new SJButton("old");
        final List<PropertyChangeEvent> events = recordPces(b, "text");
        b.setText("new");
        assertEquals(1, events.size());
        assertEquals("old", events.get(0).getOldValue());
        assertEquals("new", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setActionCommand overrides the text fallback")
    void setActionCommandOverridesText() {
        final SJButton b = new SJButton("Save");
        b.setActionCommand("save-file");
        assertEquals("save-file", b.getActionCommand());
    }

    @Test
    @DisplayName("defaultCapable defaults to true and fires PCE on change")
    void defaultCapableFiresPce() {
        final SJButton b = new SJButton();
        assertTrue(b.isDefaultCapable());
        final List<PropertyChangeEvent> events = recordPces(b, "defaultCapable");
        b.setDefaultCapable(false);
        assertFalse(b.isDefaultCapable());
        assertEquals(1, events.size());
        assertEquals(false, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("isDefaultButton is false — SJRootPane owns default-button identity")
    void isDefaultButtonIsFalse() {
        assertFalse(new SJButton().isDefaultButton());
    }

    @Test
    @DisplayName("UIClassID is ButtonUI")
    void uiClassIdIsButtonUi() {
        assertEquals("ButtonUI", new SJButton().getUIClassID());
    }

    // --- Text / element round-trip (R_vaadin_first Vaadin-first) -----------------

    @Test
    @DisplayName("setText updates peer and fires text PCE")
    void setTextUpdatesPeer() {
        final SJButton b = new SJButton("old");
        final List<PropertyChangeEvent> events = recordPces(b, "text");

        b.setText("new");

        assertEquals("new", b.getText());
        assertEquals("new", ((Button) b).getText()); // Vaadin HasText read
        assertEquals(1, events.size());
        assertEquals("old", events.get(0).getOldValue());
        assertEquals("new", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setText null reads back as empty string per R_vaadin_first")
    void setTextNullReadsEmpty() {
        // R_vaadin_first accepted loss — null can't round-trip through Vaadin's
        // element text property, so getText returns "" after setText(null).
        final SJButton b = new SJButton("initial");
        b.setText(null);
        assertEquals("", b.getText());
    }

    @Test
    @DisplayName("label aliases delegate to text")
    void labelAliasesText() {
        final SJButton b = new SJButton();
        b.setLabel("Done");
        assertEquals("Done", b.getText());
        assertEquals("Done", b.getLabel());
    }

    // --- ActionListener (programmatic + peer click) ------------------

    @Test
    @DisplayName("doClick fires ActionEvent with source=SJButton and actionCommand")
    void doClickFiresActionEvent() {
        final List<ActionEvent> events = new ArrayList<>();
        final SJButton b = new SJButton("Go");
        b.addActionListener(events::add);

        b.doClick();

        assertEquals(1, events.size());
        assertSame(b, events.get(0).getSource());
        assertEquals("Go", events.get(0).getActionCommand());
    }

    @Test
    @DisplayName("user click on attached Button fires ActionListener with source=SJButton")
    void userClickFiresActionListener() {
        final SJButton b = new SJButton("Click");
        UI.getCurrent().add(b);

        final List<ActionEvent> events = new ArrayList<>();
        b.addActionListener(events::add);

        LocatorJ._click(LocatorJ._get(Button.class));

        assertEquals(1, events.size());
        assertSame(b, events.get(0).getSource());
        assertEquals("Click", events.get(0).getActionCommand());
    }

    @Test
    @DisplayName("removeActionListener stops further events")
    void removeActionListenerStopsEvents() {
        final List<ActionEvent> events = new ArrayList<>();
        final SJButton b = new SJButton();
        final ActionListener l = events::add;
        b.addActionListener(l);
        b.removeActionListener(l);

        b.doClick();

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("getActionListeners reflects add and remove")
    void actionListenersReflectAdd() {
        final SJButton b = new SJButton();
        assertEquals(0, b.getActionListeners().length);
        final ActionListener l = e -> { /* noop */ };
        b.addActionListener(l);
        assertEquals(1, b.getActionListeners().length);
        assertSame(l, b.getActionListeners()[0]);
    }

    // --- ChangeListener (model armed/pressed transitions) ------------

    @Test
    @DisplayName("doClick fires stateChanged on registered ChangeListener")
    void doClickFiresChangeEvents() {
        final List<ChangeEvent> hits = new ArrayList<>();
        final SJButton b = new SJButton();
        b.addChangeListener(hits::add);

        b.doClick();

        // Lower bound, not an exact count: a single doClick drives several
        // DefaultButtonModel transitions (armed / pressed / released) and
        // the coalescing is a model-internal detail — pinning the exact
        // number would couple the test to JDK model internals. What matters
        // is that the fan-out reached the listener and every event is
        // sourced from the button.
        assertFalse(hits.isEmpty(), "doClick should drive at least one ChangeEvent");
        assertTrue(hits.stream().allMatch(e -> e.getSource() == b),
                "every ChangeEvent must be sourced from the button");
    }

    // --- ButtonModel swap --------------------------------------------

    @Test
    @DisplayName("default model is DefaultButtonModel and non-null")
    void defaultModelIsDefaultButtonModel() {
        final SJButton b = new SJButton();
        assertNotNull(b.getModel());
        assertInstanceOf(DefaultButtonModel.class, b.getModel());
    }

    @Test
    @DisplayName("setModel rewires listeners and fires model PCE")
    void setModelRewiresListeners() {
        final SJButton b = new SJButton();
        final ButtonModel oldModel = b.getModel();
        final ButtonModel newModel = new DefaultButtonModel();

        final List<PropertyChangeEvent> events = recordPces(b, "model");

        b.setModel(newModel);

        assertSame(newModel, b.getModel());
        assertNotSame(oldModel, b.getModel());
        assertEquals(1, events.size());
        assertSame(oldModel, events.get(0).getOldValue());
        assertSame(newModel, events.get(0).getNewValue());

        final List<ActionEvent> hits = new ArrayList<>();
        b.addActionListener(hits::add);
        newModel.setArmed(true);
        newModel.setPressed(true);
        newModel.setPressed(false);
        assertEquals(1, hits.size());
        assertSame(b, hits.get(0).getSource());
    }

    @Test
    @DisplayName("setModel detaches listeners from the old model")
    void setModelDetachesOldModel() {
        final SJButton b = new SJButton();
        final ButtonModel oldModel = b.getModel();
        b.setModel(new DefaultButtonModel());

        final List<ActionEvent> hits = new ArrayList<>();
        b.addActionListener(hits::add);

        oldModel.setArmed(true);
        oldModel.setPressed(true);
        oldModel.setPressed(false);

        assertTrue(hits.isEmpty(), "old model should be detached");
    }

    // --- setAction ---------------------------------------------------

    @Test
    @DisplayName("setAction propagates NAME and ACTION_COMMAND_KEY")
    void setActionPropagatesNameAndCommand() {
        final SJButton b = new SJButton();
        final Action a = inertAction("Save");
        a.putValue(Action.ACTION_COMMAND_KEY, "save");
        b.setAction(a);
        assertEquals("Save", b.getText());
        assertEquals("save", b.getActionCommand());
    }

    @Test
    @DisplayName("setAction with enabled=false disables the button")
    void setActionDisabledDisablesButton() {
        final SJButton b = new SJButton("x");
        final Action a = inertAction("y");
        a.setEnabled(false);
        b.setAction(a);
        assertFalse(b.isEnabled());
    }

    @Test
    @DisplayName("doClick dispatches to Action_actionPerformed")
    void doClickDispatchesToAction() {
        final List<ActionEvent> calls = new ArrayList<>();
        final Action a = new AbstractAction("Go") {
            @Override
            public void actionPerformed(ActionEvent e) {
                calls.add(e);
            }
        };
        final SJButton b = new SJButton();
        b.setAction(a);

        b.doClick();

        assertEquals(1, calls.size());
        assertSame(b, calls.get(0).getSource());
    }

    @Test
    @DisplayName("setAction null clears the backing action")
    void setActionNullClears() {
        final SJButton b = new SJButton();
        b.setAction(inertAction("Go"));
        b.setAction(null);
        assertNull(b.getAction());
    }

    @Test
    @DisplayName("setAction same action is a no-op (no action PCE)")
    void setActionSameIsNoop() {
        final SJButton b = new SJButton();
        final Action a = inertAction("Go");
        b.setAction(a);
        final List<PropertyChangeEvent> events = recordPces(b, "action");
        b.setAction(a);
        assertTrue(events.isEmpty());
    }

    // --- Mnemonic ----------------------------------------------------

    @Test
    @DisplayName("setMnemonic fires mnemonic PCE")
    void setMnemonicFiresPce() {
        final SJButton b = new SJButton("Save");
        final List<PropertyChangeEvent> events = recordPces(b, "mnemonic");

        b.setMnemonic(KeyEvent.VK_S);

        assertEquals(KeyEvent.VK_S, b.getMnemonic());
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("setMnemonic zero clears the installed mnemonic")
    void setMnemonicZeroClears() {
        final SJButton b = new SJButton("Save");
        b.setMnemonic(KeyEvent.VK_S);
        b.setMnemonic(0);
        assertEquals(0, b.getMnemonic());
    }

    @Test
    @DisplayName("setMnemonic char uppercases ASCII letters to VK form")
    void setMnemonicCharUppercases() {
        final SJButton b = new SJButton("Save");
        b.setMnemonic('s');
        assertEquals(KeyEvent.VK_S, b.getMnemonic());
    }

    // --- Alignment: R_match_swing_errors IAE preserved; R_vaadin_first default-vs-change behavior --

    @Test
    @DisplayName("setHorizontalAlignment rejects non-SwingConstants value with IAE")
    void horizontalAlignmentRejectsVerticalKey() {
        final SJButton b = new SJButton();
        assertThrows(IllegalArgumentException.class, () -> b.setHorizontalAlignment(SwingConstants.TOP));
    }

    @Test
    @DisplayName("setVerticalAlignment rejects non-SwingConstants value with IAE")
    void verticalAlignmentRejectsHorizontalKey() {
        final SJButton b = new SJButton();
        assertThrows(IllegalArgumentException.class,
                () -> b.setVerticalAlignment(SwingConstants.LEADING));
    }

    @Test
    @DisplayName("alignment getters return JDK defaults")
    void alignmentGettersReturnJdkDefaults() {
        final SJButton b = new SJButton();
        assertEquals(SwingConstants.CENTER, b.getHorizontalAlignment());
        assertEquals(SwingConstants.CENTER, b.getVerticalAlignment());
        assertEquals(SwingConstants.TRAILING, b.getHorizontalTextPosition());
        assertEquals(SwingConstants.CENTER, b.getVerticalTextPosition());
    }

    @Test
    @DisplayName("host element runs as inline-flex with JDK defaults wired")
    void hostElementFlexDefaults() {
        // Post-Track-A SJLabel-pattern lift: alignment / text-position /
        // iconTextGap drive the host's flex CSS. Vaadin Button is already
        // display:inline-flex per Lumo, so we only write flex-direction /
        // justify-content / align-items / gap.
        final SJButton b = new SJButton();
        final Style s = style(b);
        // CENTER text-position + TRAILING horizontal → row direction.
        assertEquals("row", s.get("flex-direction"));
        // CENTER horizontal alignment → center.
        assertEquals("center", s.get("justify-content"));
        // CENTER vertical alignment → center.
        assertEquals("center", s.get("align-items"));
        // Default gap 4px.
        assertEquals("4px", s.get("gap"));
    }

    @Test
    @DisplayName("setHorizontalAlignment writes justify-content with LEADING canonical readback")
    void horizontalAlignmentWritesJustifyContent() {
        // R_vaadin_first lossy round-trip: LEFT and LEADING both write `flex-start`,
        // and the getter reads back as LEADING (canonical) — same shape
        // as setBorder/getBorder per SD_border_css_lossy.
        final SJButton b = new SJButton();
        b.setHorizontalAlignment(SwingConstants.LEFT);
        assertEquals(SwingConstants.LEADING, b.getHorizontalAlignment());
        assertEquals("flex-start", style(b).get("justify-content"));
        assertNoWarns("alignment is wired now, not Bucket C");
    }

    @Test
    @DisplayName("setHorizontalAlignment fires PCE with canonical CSS-readback values")
    void horizontalAlignmentPceIsCanonical() {
        // R_vaadin_first lossy round-trip: PCE values match what the getter returns,
        // not what the user passed. RIGHT writes flex-end; the readback
        // canonical is TRAILING.
        final SJButton b = new SJButton();
        final List<PropertyChangeEvent> events = recordPces(b, "horizontalAlignment");
        b.setHorizontalAlignment(SwingConstants.RIGHT);
        assertEquals(1, events.size());
        assertEquals(SwingConstants.CENTER, events.get(0).getOldValue());
        assertEquals(SwingConstants.TRAILING, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("setVerticalAlignment writes align-items")
    void verticalAlignmentWritesAlignItems() {
        final SJButton b = new SJButton();
        b.setVerticalAlignment(SwingConstants.TOP);
        assertEquals(SwingConstants.TOP, b.getVerticalAlignment());
        assertEquals("flex-start", style(b).get("align-items"));
    }

    @Test
    @DisplayName("setHorizontalTextPosition LEADING flips to row-reverse")
    void horizontalTextPositionLeadingReverses() {
        final SJButton b = new SJButton();
        b.setHorizontalTextPosition(SwingConstants.LEADING);
        assertEquals("row-reverse", style(b).get("flex-direction"));
    }

    @Test
    @DisplayName("setVerticalTextPosition non-CENTER promotes to column flex")
    void verticalTextPositionPromotesToColumn() {
        final SJButton b = new SJButton();
        b.setVerticalTextPosition(SwingConstants.TOP);
        assertEquals("column-reverse", style(b).get("flex-direction"));

        b.setVerticalTextPosition(SwingConstants.BOTTOM);
        assertEquals("column", style(b).get("flex-direction"));
    }

    @Test
    @DisplayName("vertical text-position non-CENTER overrides horizontal direction")
    void verticalTextPositionOverridesHorizontal() {
        final SJButton b = new SJButton();
        b.setHorizontalTextPosition(SwingConstants.LEADING);  // would set row-reverse
        b.setVerticalTextPosition(SwingConstants.BOTTOM);     // promotes to column
        assertEquals("column", style(b).get("flex-direction"));
    }

    @Test
    @DisplayName("setIconTextGap round-trips and writes CSS gap")
    void iconTextGapWritesCssGap() {
        final SJButton b = new SJButton();
        b.setIconTextGap(12);
        assertEquals(12, b.getIconTextGap());
        assertEquals("12px", style(b).get("gap"));
    }

    // --- Icon (Path 1: Vaadin-first per SD_vaadin_first_binding; non-ImageIcon drops) ----

    @Test
    @DisplayName("setIcon ImageIcon installs Vaadin Image on peer with no WARN per Path 1")
    void setIconImageIconInstallsImage() {
        // Path 1: ImageIcon's raster
        // encodes as PNG and lands as a Vaadin <img> child of the peer.
        final SJButton b = new SJButton();
        final ImageIcon ii = imageIcon(2);
        ii.setDescription("logo");
        b.setIcon(ii);
        assertNoWarns("Path 1 ImageIcon should not WARN");
        final Component installed = b.getIcon();  // Vaadin Button.getIcon — returns Component
        assertNotNull(installed, "ImageIcon raster should reach the peer as a Vaadin Image");
        assertInstanceOf(Image.class, installed);
    }

    @Test
    @DisplayName("setIcon null clears the peer icon and is silent")
    void setIconNullClearsSlot() {
        final SJButton b = new SJButton();
        b.setIcon((Icon) null);
        assertNull(b.getIcon());
        assertNoWarns();
    }

    @Test
    @DisplayName("setIcon ImageIcon then null round-trips the slot")
    void setIconThenNullRoundTrips() {
        final SJButton b = new SJButton();
        b.setIcon(imageIcon(1));
        assertNotNull(b.getIcon());
        b.setIcon((Icon) null);
        assertNull(b.getIcon());
        assertNoWarns();
    }

    @Test
    @DisplayName("setIcon non-ImageIcon WARNs and clears the slot pending Path 2-3")
    void setIconCustomIconWarns() {
        // Path 2 (UIManager → VaadinIcon) and Path 3 (offscreen-paint)
        // are deferred — a custom Icon impl drops with a WARN today.
        final Icon custom = new Icon() {
            @Override
            public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 4;
            }

            @Override
            public int getIconHeight() {
                return 4;
            }
        };
        final SJButton b = new SJButton();
        b.setIcon(custom);
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("setIcon"));
        assertNull(b.getIcon(), "non-ImageIcon should drop the slot, not leave a stale icon");
    }

    @Test
    @DisplayName("setIcon fires icon PCE with Vaadin Component-typed values per R_vaadin_first")
    void setIconFiresComponentTypedPce() {
        // R_vaadin_first lossy round-trip: PCE fires, but values are Vaadin Component
        // (or null), not JDK Icon — surrogate doesn't shadow-store the
        // user's Icon ref. newValue is the installed Vaadin Image.
        final SJButton b = new SJButton();
        final List<PropertyChangeEvent> events = recordPces(b, "icon");

        b.setIcon(imageIcon(3));

        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());
        assertInstanceOf(Image.class, events.get(0).getNewValue());
    }

    // --- R_vaadin_first drop-and-WARN setters ------------------------------------

    @Test
    @DisplayName("state-conditional icon getters always return null per R_vaadin_first")
    void stateConditionalIconGettersReturnNull() {
        final SJButton b = new SJButton();
        // Path 1 only lifts plain setIcon — the six state-conditional
        // setters stay drop-and-WARN, getters keep returning null.
        b.setIcon(imageIcon(1));
        assertNull(b.getPressedIcon());
        assertNull(b.getSelectedIcon());
        assertNull(b.getDisabledIcon());
        assertNull(b.getDisabledSelectedIcon());
        assertNull(b.getRolloverIcon());
        assertNull(b.getRolloverSelectedIcon());
    }

    @Test
    @DisplayName("visual-flag setters WARN only on non-default")
    void visualFlagsWarnOnlyOnNonDefault() {
        final SJButton b = new SJButton();
        // JDK defaults: borderPainted/contentAreaFilled/focusPainted=true, rolloverEnabled=false
        b.setBorderPainted(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(true);
        b.setRolloverEnabled(false);
        assertNoWarns("defaults should be silent");

        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        assertEquals(4, capturedWarns.size(), "non-default values should each WARN");
    }

    @Test
    @DisplayName("margin, iconTextGap, multiClickThreshhold getters return JDK defaults")
    void jdkDefaultGetters() {
        final SJButton b = new SJButton();
        assertNull(b.getMargin());
        assertEquals(4, b.getIconTextGap());
        assertEquals(0L, b.getMultiClickThreshhold());
    }

    @Test
    @DisplayName("setMultiClickThreshhold negative throws IAE per R_match_swing_errors")
    void multiClickThreshholdNegativeThrows() {
        final SJButton b = new SJButton();
        assertThrows(IllegalArgumentException.class, () -> b.setMultiClickThreshhold(-1L));
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex negative-invalid throws IAE per R_match_swing_errors")
    void displayedMnemonicIndexNegativeThrows() {
        final SJButton b = new SJButton();
        assertThrows(IllegalArgumentException.class, () -> b.setDisplayedMnemonicIndex(-2));
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex out-of-range throws IAE per R_match_swing_errors")
    void displayedMnemonicIndexOutOfRangeThrows() {
        final SJButton b = new SJButton("abc");
        assertThrows(IllegalArgumentException.class, () -> b.setDisplayedMnemonicIndex(5));
    }

    @Test
    @DisplayName("setDisplayedMnemonicIndex -1 is silent, valid non-(-1) WARNs per R_vaadin_first")
    void displayedMnemonicIndexWarnsOnlyWhenSet() {
        final SJButton b = new SJButton("abc");
        b.setDisplayedMnemonicIndex(-1);
        assertNoWarns();

        b.setDisplayedMnemonicIndex(1);
        assertEquals(1, capturedWarns.size());
    }

    // --- Happy-path zero-WARN -----------------------------------------

    @Test
    @DisplayName("full SJButton happy path fires no stub WARNs")
    void happyPathIsWarnFree() {
        // R_vaadin_first-scoped happy path: exercise UI-functional surface that does
        // NOT go through drop-and-WARN setters.
        final SJButton b = new SJButton("Hello");
        b.setActionCommand("hi");
        b.addActionListener(e -> { /* noop */ });
        b.addChangeListener(e -> { /* noop */ });
        b.setText("Bye");
        b.setMnemonic(KeyEvent.VK_B);
        UI.getCurrent().add(b);
        b.doClick();

        assertNoWarns("no stub WARNs expected from SD_sjbutton's R_vaadin_first-scoped happy path");
    }
}
