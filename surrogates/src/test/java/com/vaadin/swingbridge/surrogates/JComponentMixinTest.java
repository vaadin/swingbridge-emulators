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

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SAncestorEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SAncestorListener;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;
import com.vaadin.swingbridge.surrogates.util.BorderCss;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.InputMap;
import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.border.BevelBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.border.TitledBorder;

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
 * The {@link JComponentMixin} surface — the JComponent-level additions over
 * {@link ComponentMixinTest}'s AWT base — under the Vaadin-first stance (SD_vaadin_first_binding),
 * driven through {@link TestJSurrogate}:
 *
 * <ul>
 *  <li><b>Bucket A</b> — client properties, opaque, tooltip PCE on
 *      {@code "ToolTipText"}, border round-trip through CSS (LineBorder /
 *      EmptyBorder / MatteBorder / BevelBorder / EtchedBorder / CompoundBorder;
 *      TitledBorder via a ::before pseudo-element), InputMap/ActionMap lazy
 *      alloc + keyboard-action dispatch on Vaadin Shortcuts, InputVerifier via
 *      BlurNotifier, AncestorListener, constant-default getters, static locale
 *      accessors via VaadinSession, topLevelAncestor (first Dialog ancestor, SD_top_level_ancestor).
 *  <li><b>Bucket B</b> — paintImmediately / updateUI / setUI / getUIClassID /
 *      getComponentGraphics / reshape, the paint hints (setAutoscrolls,
 *      setInheritsPopupMenu, setRequestFocusEnabled, setVerifyInputWhenFocusTarget,
 *      setDoubleBuffered, setDebugGraphicsOptions, setAlignmentX/Y), and the
 *      ghost process*Event hooks — all onNoop, zero WARNs.
 *  <li><b>Bucket C</b> — vetoable listeners and transfer handler WARN (R_vaadin_first
 *      drop-and-WARN); setComponentPopupMenu binds a ContextMenu target (SD_sjpopupmenu).
 * </ul>
 */
class JComponentMixinTest extends AbstractKaribuTest {

    /** Subscribes an unfiltered PCE listener and returns the list events land in. */
    private static List<PropertyChangeEvent> recordPces(JComponentMixin s) {
        final List<PropertyChangeEvent> received = new ArrayList<>();
        s.addPropertyChangeListener(received::add);
        return received;
    }

    /** The PCEs among {@code received} that carry the given property name. */
    private static List<PropertyChangeEvent> named(List<PropertyChangeEvent> received, String property) {
        return received.stream().filter(e -> property.equals(e.getPropertyName())).toList();
    }

    /** An InputVerifier that accepts everything and tallies each call. */
    private static InputVerifier countingVerifier(Counter hits) {
        return new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                hits.inc();
                return true;
            }
        };
    }

    /** An InputVerifier that accepts everything and counts nothing. */
    private static InputVerifier acceptingVerifier() {
        return new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                return true;
            }
        };
    }

    /** Records the id of every ancestor callback the listener sees. */
    private static List<Integer> recordAncestorIds(TestJSurrogate s) {
        final List<Integer> ids = new ArrayList<>();
        s.addAncestorListener(new SAncestorListener() {
            @Override
            public void ancestorAdded(SAncestorEvent event) {
                ids.add(event.getID());
            }

            @Override
            public void ancestorRemoved(SAncestorEvent event) {
                ids.add(event.getID());
            }

            @Override
            public void ancestorMoved(SAncestorEvent event) {
                ids.add(event.getID());
            }
        });
        return ids;
    }

    /** An ancestor listener whose three callbacks all do nothing. */
    private static SAncestorListener inertAncestorListener() {
        return new SAncestorListener() {
            @Override
            public void ancestorAdded(SAncestorEvent event) {
            }

            @Override
            public void ancestorRemoved(SAncestorEvent event) {
            }

            @Override
            public void ancestorMoved(SAncestorEvent event) {
            }
        };
    }

    /** The {@code <style data-emul="border-title">} elements attached to the UI tree. */
    private static List<Element> borderTitleStyles(UI ui) {
        return ui.getElement().getChildren()
                .filter(e -> "style".equals(e.getTag())
                        && "border-title".equals(e.getAttribute("data-emul")))
                .toList();
    }

    // --- Client properties (ClientPropertyStore) ----------------------

    @Test
    @DisplayName("setEnabled fires the enabled PCE that is JComponent's, not Component's")
    void setEnabledFiresJComponentPce() {
        // The bound "enabled" property lives on JComponent.setEnabled, which
        // overrides java.awt.Component's (that one fires nothing bound). So the
        // fire sits on JComponentMixin, mirroring where the JDK puts it, and
        // ComponentMixin stays silent — asserted in ComponentMixinTest (SD_property_fanout_audit).
        final TestJSurrogate s = new TestJSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.setEnabled(false);

        final List<PropertyChangeEvent> enabled = named(received, "enabled");
        assertEquals(1, enabled.size(), received.toString());
        assertEquals(true, enabled.get(0).getOldValue());
        assertEquals(false, enabled.get(0).getNewValue());
    }

    @Test
    @DisplayName("a window surrogate suppresses the enabled PCE, not being a JComponent")
    void windowSurrogateSuppressesEnabledPce() {
        // SWindow takes JComponentMixin for its helper surface (border, tooltip,
        // name), but java.awt.Window is not a JComponent — nor are Frame,
        // JFrame, JDialog, JWindow under it. One SWindow override covers all
        // five (SD_property_fanout_audit).
        final SWindow w = new SWindow();
        final List<PropertyChangeEvent> received = recordPces(w);

        w.setEnabled(false);

        assertEquals(List.of(), named(received, "enabled"), received.toString());
        assertFalse(w.isEnabled());
    }

    @Test
    @DisplayName("putClientProperty round-trips and fires PCE on key toString")
    void clientPropertyRoundTripsAndFires() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.putClientProperty("theme", "dark");
        assertEquals("dark", s.getClientProperty("theme"));
        assertEquals(1, received.size());
        assertEquals("theme", received.get(0).getPropertyName());
        assertNull(received.get(0).getOldValue());
        assertEquals("dark", received.get(0).getNewValue());
    }

    @Test
    @DisplayName("putClientProperty with null value removes the mapping")
    void clientPropertyNullRemoves() {
        final TestJSurrogate s = new TestJSurrogate();
        s.putClientProperty("theme", "dark");
        s.putClientProperty("theme", null);
        assertNull(s.getClientProperty("theme"));
    }

    @Test
    @DisplayName("putClientProperty rejects null key")
    void clientPropertyNullKeyThrows() {
        final TestJSurrogate s = new TestJSurrogate();
        assertThrows(NullPointerException.class, () -> s.putClientProperty(null, "x"));
    }

    @Test
    @DisplayName("getClientProperty with null key returns null (not NPE)")
    void getClientPropertyNullKeyIsNull() {
        assertNull(new TestJSurrogate().getClientProperty(null));
    }

    @Test
    @DisplayName("putClientProperty with equal value does not fire PCE")
    void clientPropertyEqualValueIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.putClientProperty("theme", "dark");
        final List<PropertyChangeEvent> received = recordPces(s);
        s.putClientProperty("theme", "dark");
        assertEquals(0, received.size());
    }

    // --- Opaque (CSS background-color is the source of truth) -------

    @Test
    @DisplayName("isOpaque default is false — CSS background-color unset")
    void isOpaqueDefaultsFalse() {
        assertFalse(new TestJSurrogate().isOpaque());
    }

    @Test
    @DisplayName("setOpaque false after setBackground fires opaque PCE")
    void setOpaqueFalseFiresPce() {
        // Under the pure-CSS model, the only direction that produces an
        // opaque PCE is true→false: setOpaque(false) writes
        // background-color: transparent, which flips isOpaque from
        // true (CSS has a color) to false. The reverse direction
        // (setOpaque(true)) can't flip false→true without a real color
        // in CSS, so a bg-less setOpaque(true) is silent. Documented
        // lossy regime per R_vaadin_first.
        final TestJSurrogate s = new TestJSurrogate();
        s.setBackground(new Color(0, 0, 255));
        assertTrue(s.isOpaque());
        final List<PropertyChangeEvent> received = recordPces(s);
        s.setOpaque(false);
        assertFalse(s.isOpaque());
        assertEquals("transparent", s.getElement().getStyle().get("background-color"));
        final List<PropertyChangeEvent> opaqueEvents = named(received, "opaque");
        assertEquals(1, opaqueEvents.size());
        assertEquals(true, opaqueEvents.get(0).getOldValue());
        assertEquals(false, opaqueEvents.get(0).getNewValue());
    }

    @Test
    @DisplayName("setOpaque true without background reads back false per R_vaadin_first lossy")
    void setOpaqueTrueWithoutBackgroundIsLossy() {
        // Documented lossy direction on JComponentMixin.setOpaque: bg-less
        // setOpaque(true) leaves CSS unset (we don't model UIManager L&F
        // default fill), and isOpaque() reads CSS back, so the user's
        // intent doesn't round-trip. Migrators wanting strict JDK
        // round-trip set background first or stay on the emulator layer.
        final TestJSurrogate s = new TestJSurrogate();
        s.setOpaque(true);
        assertFalse(s.isOpaque());
    }

    @Test
    @DisplayName("setOpaque with equal value does not fire PCE")
    void setOpaqueEqualValueIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);
        s.setOpaque(false);  // already false (CSS unset)
        assertEquals(0, received.size());
    }

    @Test
    @DisplayName("setBackground flips isOpaque to true per R_vaadin_first lossy direction")
    void setBackgroundFlipsOpaque() {
        // Documented lossy direction: CSS background-color drives both
        // setBackground/getBackground AND isOpaque, so setting a real
        // color flips isOpaque even though JDK keeps the two
        // independent. Migrated code that needs strict JDK semantics
        // reads isOpaque from the emulator layer.
        final TestJSurrogate s = new TestJSurrogate();
        assertFalse(s.isOpaque());
        s.setBackground(new Color(255, 0, 0));
        assertTrue(s.isOpaque());
    }

    // --- setOpaque drives CSS background-color -----------------------

    @Test
    @DisplayName("setOpaque true writes background-color from getBackground")
    void setOpaqueTrueWritesBackground() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBackground(new Color(255, 0, 0));
        assertEquals("rgb(255,0,0)", s.getElement().getStyle().get("background-color"));

        s.setOpaque(true);

        // Re-asserted from getBackground (which reads CSS) — same value.
        assertEquals("rgb(255,0,0)", s.getElement().getStyle().get("background-color"));
    }

    @Test
    @DisplayName("setOpaque false writes background-color transparent")
    void setOpaqueFalseWritesTransparent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBackground(new Color(0, 128, 0));

        s.setOpaque(false);  // default is already false but the no-op
                             // short-circuits — flip to true first.
        s.setOpaque(true);
        s.setOpaque(false);

        assertEquals("transparent", s.getElement().getStyle().get("background-color"));
    }

    @Test
    @DisplayName("setOpaque true clears background-color when getBackground is null")
    void setOpaqueTrueWithNullBackground() {
        final TestJSurrogate s = new TestJSurrogate();
        // No background set — getBackground returns null.
        s.setOpaque(true);
        assertNull(s.getElement().getStyle().get("background-color"));
    }

    @Test
    @DisplayName("setOpaque false then true loses the previous color per R_vaadin_first lossy")
    void opaqueFlipLosesColor() {
        // R_vaadin_first lossy direction documented on JComponentMixin.setOpaque:
        // surrogate doesn't shadow-store the user's color, so a flip
        // through false→true loses it. Migrators on the surrogate layer
        // re-assert setBackground after flipping back to true.
        final TestJSurrogate s = new TestJSurrogate();
        s.setBackground(new Color(255, 0, 0));
        s.setOpaque(true);
        s.setOpaque(false);   // CSS = transparent
        s.setOpaque(true);    // getBackground reads "transparent" → null
        assertNull(s.getElement().getStyle().get("background-color"));
        assertNull(s.getBackground());
    }

    // --- ToolTipText override (PCE on "ToolTipText") ----------------

    @Test
    @DisplayName("setToolTipText fires PCE named ToolTipText with capital T")
    void toolTipTextPceName() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.setToolTipText("Click me");
        assertEquals("Click me", s.getToolTipText());
        assertEquals(1, received.size());
        assertEquals(JComponentMixin.TOOL_TIP_TEXT_KEY, received.get(0).getPropertyName());
        assertEquals("ToolTipText", received.get(0).getPropertyName());  // literal spelling
        assertNull(received.get(0).getOldValue());
        assertEquals("Click me", received.get(0).getNewValue());
    }

    @Test
    @DisplayName("setToolTipText drives HasTooltip on the peer")
    void toolTipTextDrivesPeer() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setToolTipText("Peer reachable");
        assertEquals("Peer reachable", s.getTooltip().getText());
    }

    // --- Border round-trip through CSS -------------------------------

    @Test
    @DisplayName("LineBorder round-trip via CSS")
    void lineBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new LineBorder(Color.RED, 2));
        assertEquals("2px solid rgb(255,0,0)", s.getElement().getStyle().get("border"));
        final LineBorder back = assertInstanceOf(LineBorder.class, s.getBorder());
        assertEquals(Color.RED, back.getLineColor());
        assertEquals(2, back.getThickness());
    }

    @Test
    @DisplayName("LineBorder rounded round-trips via border-radius")
    void roundedLineBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new LineBorder(Color.BLACK, 3, true));
        assertEquals("6px", s.getElement().getStyle().get("border-radius"));
        final LineBorder back = assertInstanceOf(LineBorder.class, s.getBorder());
        assertTrue(back.getRoundedCorners());
    }

    @Test
    @DisplayName("EmptyBorder round-trips via padding")
    void emptyBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new EmptyBorder(1, 2, 3, 4));
        assertEquals("1px 4px 3px 2px", s.getElement().getStyle().get("padding"));
        final EmptyBorder back = assertInstanceOf(EmptyBorder.class, s.getBorder());
        assertEquals(new Insets(1, 2, 3, 4), back.getBorderInsets());
    }

    @Test
    @DisplayName("MatteBorder round-trips via per-side border longhands")
    void matteBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new MatteBorder(1, 2, 3, 4, Color.BLUE));
        final com.vaadin.flow.dom.Style style = s.getElement().getStyle();
        assertEquals("1px", style.get("border-top-width"));
        assertEquals("4px", style.get("border-right-width"));
        assertEquals("3px", style.get("border-bottom-width"));
        assertEquals("2px", style.get("border-left-width"));
        final MatteBorder back = assertInstanceOf(MatteBorder.class, s.getBorder());
        assertEquals(Color.BLUE, back.getMatteColor());
        assertEquals(new Insets(1, 2, 3, 4), back.getBorderInsets());
    }

    @Test
    @DisplayName("BevelBorder LOWERED round-trips as inset")
    void bevelBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new BevelBorder(BevelBorder.LOWERED, Color.GRAY, Color.DARK_GRAY));
        assertTrue(s.getElement().getStyle().get("border").contains("inset"));
        final BevelBorder back = assertInstanceOf(BevelBorder.class, s.getBorder());
        assertEquals(BevelBorder.LOWERED, back.getBevelType());
    }

    @Test
    @DisplayName("EtchedBorder LOWERED round-trips as groove")
    void etchedBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new EtchedBorder(EtchedBorder.LOWERED, Color.GRAY, Color.DARK_GRAY));
        assertTrue(s.getElement().getStyle().get("border").contains("groove"));
        final EtchedBorder back = assertInstanceOf(EtchedBorder.class, s.getBorder());
        assertEquals(EtchedBorder.LOWERED, back.getEtchType());
    }

    @Test
    @DisplayName("CompoundBorder LineBorder outer + EmptyBorder inner round-trips")
    void compoundBorderRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new CompoundBorder(new LineBorder(Color.BLACK, 1), new EmptyBorder(5, 5, 5, 5)));
        assertNotNull(s.getElement().getStyle().get("border"));
        assertNotNull(s.getElement().getStyle().get("padding"));
        final CompoundBorder back = assertInstanceOf(CompoundBorder.class, s.getBorder());
        assertInstanceOf(LineBorder.class, back.getOutsideBorder());
        assertInstanceOf(EmptyBorder.class, back.getInsideBorder());
    }

    @Test
    @DisplayName("TitledBorder writes data-emul-border-title attribute and inherits inner border CSS")
    void titledBorderWritesAttribute() {
        // Pseudo-element rendering wired via the session-injected ::before
        // rule + a data-attribute carrying the title text. JDK's
        // TitledBorder(String) populates the inner border via UIManager
        // defaults (typically a LineBorder), which our recursive
        // applyBorderCss picks up.
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new TitledBorder("Title"));
        assertEquals(0, capturedWarns.size(), "TitledBorder does not WARN");
        assertEquals("Title", s.getElement().getAttribute("data-emul-border-title"));
        assertNotNull(s.getElement().getStyle().get("border"),
                "inner border (or 1px fallback) should write some CSS");
    }

    @Test
    @DisplayName("TitledBorder with explicit LineBorder inner uses the inner's CSS")
    void titledBorderUsesInnerCss() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new TitledBorder(new LineBorder(Color.BLACK, 2), "Outer"));
        assertEquals(0, capturedWarns.size());
        assertEquals("2px solid rgb(0,0,0)", s.getElement().getStyle().get("border"));
        assertEquals("Outer", s.getElement().getAttribute("data-emul-border-title"));
    }

    @Test
    @DisplayName("ensureTitledBorderStyleInjected attaches a style element to the UI tree, surviving @PreserveOnRefresh")
    void titledBorderStyleIsAttachedToTree() {
        // Regression: the rule was previously injected via Page.executeJs
        // (one-shot dispatch) and later Page.addStyleSheet (sent-once per
        // UI lifetime). Both fail under @PreserveOnRefresh — the preserved
        // UI doesn't replay one-shot dispatches to the post-reload DOM.
        // Fix attaches a <style> Element to the UI tree; Vaadin re-renders
        // the tree on every page render, so the CSS survives reload.
        final UI ui = UI.getCurrent();
        assertEquals(0, borderTitleStyles(ui).size(),
                "precondition: no style element from a prior test");

        BorderCss.ensureTitledBorderStyleInjected(ui);

        final List<Element> styleEls = borderTitleStyles(ui);
        assertEquals(1, styleEls.size(),
                "must attach exactly one <style data-emul=\"border-title\"> element");
        assertTrue(styleEls.get(0).getTextRecursively().contains("data-emul-border-title"),
                "style element must contain the ::before rule, was: "
                        + styleEls.get(0).getTextRecursively());

        // Idempotency: second call does not duplicate the element.
        BorderCss.ensureTitledBorderStyleInjected(ui);
        assertEquals(1, borderTitleStyles(ui).size(),
                "second call must not duplicate the <style> element");
    }

    @Test
    @DisplayName("setBorder null clears all border keys")
    void setBorderNullClears() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new LineBorder(Color.BLACK, 2));
        s.setBorder(null);
        assertNull(s.getElement().getStyle().get("border"));
        assertNull(s.getBorder());
    }

    @Test
    @DisplayName("setBorder fires border PCE")
    void setBorderFiresPce() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);
        s.setBorder(new LineBorder(Color.BLACK, 1));
        assertEquals(1, received.size());
        assertEquals("border", received.get(0).getPropertyName());
    }

    // --- Insets derived from border ----------------------------------

    @Test
    @DisplayName("getInsets reads from installed border")
    void insetsFromBorder() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new EmptyBorder(10, 20, 30, 40));
        assertEquals(new Insets(10, 20, 30, 40), s.getInsets());
    }

    @Test
    @DisplayName("getInsets with no border returns zero")
    void insetsWithoutBorderAreZero() {
        assertEquals(new Insets(0, 0, 0, 0), new TestJSurrogate().getInsets());
    }

    @Test
    @DisplayName("getInsets(Insets) fills the arg")
    void insetsFillsArg() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setBorder(new EmptyBorder(1, 2, 3, 4));
        final Insets target = new Insets(9, 9, 9, 9);
        assertSame(target, s.getInsets(target));
        assertEquals(new Insets(1, 2, 3, 4), target);
    }

    // --- InputMap / ActionMap lazy alloc -----------------------------

    @Test
    @DisplayName("getInputMap default is WHEN_FOCUSED and is lazily allocated")
    void inputMapDefaultIsWhenFocused() {
        final TestJSurrogate s = new TestJSurrogate();
        final InputMap im = s.getInputMap();
        assertNotNull(im);
        assertSame(im, s.getInputMap(JComponentMixin.WHEN_FOCUSED));
    }

    @Test
    @DisplayName("getInputMap distinguishes all three conditions")
    void inputMapPerCondition() {
        final TestJSurrogate s = new TestJSurrogate();
        final InputMap f = s.getInputMap(JComponentMixin.WHEN_FOCUSED);
        final InputMap a = s.getInputMap(JComponentMixin.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        final InputMap w = s.getInputMap(JComponentMixin.WHEN_IN_FOCUSED_WINDOW);
        assertNotSame(f, a);
        assertNotSame(a, w);
        assertNotSame(f, w);
    }

    @Test
    @DisplayName("getInputMap rejects bad condition")
    void inputMapBadConditionThrows() {
        final TestJSurrogate s = new TestJSurrogate();
        assertThrows(IllegalArgumentException.class, () -> s.getInputMap(99));
    }

    @Test
    @DisplayName("setInputMap round-trips")
    void setInputMapRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        final InputMap replacement = new InputMap();
        s.setInputMap(JComponentMixin.WHEN_FOCUSED, replacement);
        assertSame(replacement, s.getInputMap(JComponentMixin.WHEN_FOCUSED));
    }

    @Test
    @DisplayName("getActionMap is lazily allocated")
    void actionMapIsLazy() {
        final TestJSurrogate s = new TestJSurrogate();
        assertNotNull(s.getActionMap());
        assertSame(s.getActionMap(), s.getActionMap());
    }

    // --- InputVerifier (wired via BlurNotifier) ----------------------

    @Test
    @DisplayName("setInputVerifier round-trips through getInputVerifier")
    void inputVerifierRoundTrips() {
        final TestJSurrogate s = new TestJSurrogate();
        assertNull(s.getInputVerifier());
        final InputVerifier v = acceptingVerifier();
        s.setInputVerifier(v);
        assertSame(v, s.getInputVerifier());
        assertEquals(0, capturedWarns.size());
        s.setInputVerifier(null);
        assertNull(s.getInputVerifier());
    }

    @Test
    @DisplayName("setInputVerifier installs peer blur listener that calls shouldYieldFocus")
    void inputVerifierInstallsBlurListener() {
        final TestJSurrogate s = new TestJSurrogate();
        final Counter hits = new Counter();
        s.setInputVerifier(countingVerifier(hits));

        ComponentUtil.fireEvent(s, new BlurNotifier.BlurEvent<>(s, true));

        hits.assertEquals(1);
    }

    @Test
    @DisplayName("replacing the InputVerifier detaches the old blur listener")
    void replacingInputVerifierDetachesOld() {
        final TestJSurrogate s = new TestJSurrogate();
        final Counter oldHits = new Counter();
        final Counter newHits = new Counter();
        s.setInputVerifier(countingVerifier(oldHits));
        s.setInputVerifier(countingVerifier(newHits));

        ComponentUtil.fireEvent(s, new BlurNotifier.BlurEvent<>(s, true));

        oldHits.assertEquals(0);
        newHits.assertEquals(1);
    }

    @Test
    @DisplayName("setInputVerifier null tears down the blur listener")
    void inputVerifierNullTearsDown() {
        final TestJSurrogate s = new TestJSurrogate();
        final Counter hits = new Counter();
        s.setInputVerifier(countingVerifier(hits));
        s.setInputVerifier(null);

        ComponentUtil.fireEvent(s, new BlurNotifier.BlurEvent<>(s, true));

        hits.assertEquals(0);
    }

    @Test
    @DisplayName("setInputVerifier fires inputVerifier PropertyChangeEvent")
    void inputVerifierFiresPce() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener("inputVerifier", events::add);

        final InputVerifier v = acceptingVerifier();
        s.setInputVerifier(v);

        assertEquals(1, events.size());
        assertSame(v, events.get(0).getNewValue());
    }

    @Test
    @DisplayName("same-instance set is a no-op (no PCE, no re-install)")
    void sameInputVerifierIsNoop() {
        final TestJSurrogate s = new TestJSurrogate();
        final InputVerifier v = acceptingVerifier();
        s.setInputVerifier(v);
        final List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener("inputVerifier", events::add);

        s.setInputVerifier(v);  // same instance — equality short-circuit

        assertEquals(0, events.size());
    }

    // --- Static locale API via VaadinSession -------------------------

    @Test
    @DisplayName("getDefaultLocale reads VaadinSession locale")
    void defaultLocaleReadsSession() {
        VaadinSession.getCurrent().setLocale(Locale.FRENCH);
        assertEquals(Locale.FRENCH, JComponentMixin.getDefaultLocale());
    }

    @Test
    @DisplayName("setDefaultLocale writes VaadinSession locale")
    void defaultLocaleWritesSession() {
        JComponentMixin.setDefaultLocale(Locale.GERMAN);
        assertEquals(Locale.GERMAN, VaadinSession.getCurrent().getLocale());
    }

    @Test
    @DisplayName("isLightweightComponent returns true")
    void isLightweightComponentIsTrue() {
        assertTrue(JComponentMixin.isLightweightComponent(new TestJSurrogate()));
    }

    // --- Hit test + visible rect ------------------------------------

    @Test
    @DisplayName("contains returns true for point within size")
    void containsChecksSize() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setSize(100, 50);
        assertTrue(s.contains(10, 20));
        assertFalse(s.contains(-1, 0));
        assertFalse(s.contains(100, 50));
    }

    @Test
    @DisplayName("getVisibleRect returns current size")
    void visibleRectMatchesSize() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setSize(77, 88);
        assertEquals(new Rectangle(0, 0, 77, 88), s.getVisibleRect());
    }

    @Test
    @DisplayName("computeVisibleRect fills the arg")
    void computeVisibleRectFillsArg() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setSize(50, 40);
        final Rectangle r = new Rectangle(1, 2, 3, 4);
        s.computeVisibleRect(r);
        assertEquals(new Rectangle(0, 0, 50, 40), r);
    }

    // --- Fill-the-arg getters ----------------------------------------

    @Test
    @DisplayName("getBounds(Rectangle) fills the arg")
    void getBoundsFillsArg() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setSize(10, 20);
        final Rectangle rv = new Rectangle(9, 9, 9, 9);
        assertSame(rv, s.getBounds(rv));
        assertEquals(new Rectangle(0, 0, 10, 20), rv);
    }

    @Test
    @DisplayName("getSize(Dimension) fills the arg")
    void getSizeFillsArg() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setSize(11, 22);
        final Dimension d = new Dimension(0, 0);
        assertSame(d, s.getSize(d));
        assertEquals(new Dimension(11, 22), d);
    }

    // --- Constant-default getters ------------------------------------

    @Test
    @DisplayName("constant-default getters match Swing JComponent defaults")
    void constantDefaultGetters() {
        final TestJSurrogate s = new TestJSurrogate();
        assertFalse(s.isValidateRoot());
        assertTrue(s.isOptimizedDrawingEnabled());
        assertTrue(s.isRequestFocusEnabled());
        assertTrue(s.getVerifyInputWhenFocusTarget());
        assertFalse(s.isDoubleBuffered());
        assertFalse(s.getAutoscrolls());
        assertFalse(s.getInheritsPopupMenu());
        assertEquals(0, s.getDebugGraphicsOptions());
        assertFalse(s.isPaintingTile());
        assertFalse(s.isPaintingForPrint());
        assertEquals(0.5f, s.getAlignmentX());
        assertEquals(0.5f, s.getAlignmentY());
        assertEquals(-1, s.getBaseline(100, 100));
    }

    @Test
    @DisplayName("null-default getters match Swing JComponent defaults")
    void nullDefaultGetters() {
        final TestJSurrogate s = new TestJSurrogate();
        assertNull(s.getComponentPopupMenu());
        assertNull(s.getTransferHandler());
        assertNull(s.getNextFocusableComponent());
        assertNull(s.getToolTipLocation(null));
        assertNull(s.getPopupLocation(null));
        assertNull(s.createToolTip());
        assertEquals(0, capturedWarns.size());
    }

    // --- Bucket B: paint-family onNoop -------------------------------

    @Test
    @DisplayName("Bucket B JComponent-only paint family emits no WARN")
    void jComponentPaintFamilyIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.paintImmediately(0, 0, 1, 1);
        s.paintImmediately(new Rectangle(0, 0, 1, 1));
        assertNull(s.getComponentGraphics(null));
        s.updateUI();
        // getUI() is excluded from the mixin — clashes with Vaadin's Component.getUI().
        s.setUI(null);
        assertNull(s.getUIClassID());
        s.reshape(0, 0, 10, 10);  // deprecated alias for setBounds
        assertEquals(0, capturedWarns.size());
    }

    // --- Bucket B: paint hints with no Vaadin counterpart ------------
    //
    // Plain Bucket B onNoop per SD_api_three_buckets: the setter accepts any value; the
    // getter always returns the JDK default.

    @Test
    @DisplayName("setAutoscrolls is silent (Bucket B)")
    void setAutoscrollsIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setAutoscrolls(false);
        s.setAutoscrolls(true);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setInheritsPopupMenu is silent (Bucket B)")
    void setInheritsPopupMenuIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setInheritsPopupMenu(false);
        s.setInheritsPopupMenu(true);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setComponentPopupMenu binds the popup as ContextMenu target, round-trips, no WARN (SD_sjpopupmenu)")
    void componentPopupMenuBindsTarget() {
        final TestJSurrogate s = new TestJSurrogate();
        assertNull(s.getComponentPopupMenu());
        final SJPopupMenu popup = new SJPopupMenu();
        s.setComponentPopupMenu(popup);
        assertEquals(popup, s.getComponentPopupMenu());
        assertEquals(s, popup.getTarget());
        // Re-target a fresh popup; the prior one detaches.
        final SJPopupMenu popup2 = new SJPopupMenu();
        s.setComponentPopupMenu(popup2);
        assertEquals(popup2, s.getComponentPopupMenu());
        assertEquals(s, popup2.getTarget());
        assertNull(popup.getTarget());
        // Clear.
        s.setComponentPopupMenu(null);
        assertNull(s.getComponentPopupMenu());
        assertNull(popup2.getTarget());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setRequestFocusEnabled is silent (Bucket B)")
    void setRequestFocusEnabledIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setRequestFocusEnabled(true);
        s.setRequestFocusEnabled(false);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setVerifyInputWhenFocusTarget is silent (Bucket B)")
    void setVerifyInputWhenFocusTargetIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setVerifyInputWhenFocusTarget(true);
        s.setVerifyInputWhenFocusTarget(false);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setDoubleBuffered is silent (Bucket B)")
    void setDoubleBufferedIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setDoubleBuffered(false);
        s.setDoubleBuffered(true);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setDebugGraphicsOptions is silent (Bucket B)")
    void setDebugGraphicsOptionsIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setDebugGraphicsOptions(0);
        s.setDebugGraphicsOptions(1);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setAlignmentX is silent (Bucket B)")
    void setAlignmentXIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setAlignmentX(0.5f);
        s.setAlignmentX(0.7f);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setAlignmentY is silent (Bucket B)")
    void setAlignmentYIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setAlignmentY(0.5f);
        s.setAlignmentY(0.7f);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("process_Event ghost hooks are silent (Bucket B)")
    void processEventGhostHooksAreSilent() {
        // The methods don't consult their event arg under onNoop; passing
        // null avoids the JDK event constructors that demand a real
        // java.awt.Component source (TestJSurrogate is a Vaadin Component).
        final TestJSurrogate s = new TestJSurrogate();
        s.processKeyEvent(null);
        s.processComponentKeyEvent(null);
        s.processMouseEvent(null);
        s.processMouseMotionEvent(null);
        s.processKeyBinding(null, null, JComponentMixin.WHEN_FOCUSED, true);
        assertEquals(0, capturedWarns.size());
    }

    // --- Keyboard-action dispatch ------------------------------------

    @Test
    @DisplayName("registerKeyboardAction wires Vaadin Shortcut and is silent")
    void registerKeyboardActionWiresShortcut() {
        // registerKeyboardAction installs a real Vaadin Shortcut on the
        // surrogate and stores the binding in InputMap + ActionMap. No WARN
        // expected on a mappable keystroke.
        final TestJSurrogate s = new TestJSurrogate();
        final KeyStroke ks = KeyStroke.getKeyStroke("ENTER");
        s.registerKeyboardAction(e -> { }, ks, JComponentMixin.WHEN_FOCUSED);
        assertEquals(0, capturedWarns.size());
        // InputMap entry landed.
        assertEquals(JComponentMixin.WHEN_FOCUSED, s.getConditionForKeyStroke(ks));
        // ActionMap resolves back to the listener.
        assertNotNull(s.getActionForKeyStroke(ks));
    }

    @Test
    @DisplayName("unregisterKeyboardAction removes from InputMap, ActionMap, and shortcut registry")
    void unregisterKeyboardActionRemovesAll() {
        final TestJSurrogate s = new TestJSurrogate();
        final KeyStroke ks = KeyStroke.getKeyStroke("ENTER");
        s.registerKeyboardAction(e -> { }, ks, JComponentMixin.WHEN_FOCUSED);

        s.unregisterKeyboardAction(ks);

        assertEquals(JComponentMixin.UNDEFINED_CONDITION, s.getConditionForKeyStroke(ks));
        assertNull(s.getActionForKeyStroke(ks));
        assertEquals(0, s.getRegisteredKeyStrokes().length);
    }

    @Test
    @DisplayName("resetKeyboardActions clears all conditions plus the action map")
    void resetKeyboardActionsClearsAll() {
        final TestJSurrogate s = new TestJSurrogate();
        s.registerKeyboardAction(e -> { }, KeyStroke.getKeyStroke("ENTER"),
                JComponentMixin.WHEN_FOCUSED);
        s.registerKeyboardAction(e -> { }, KeyStroke.getKeyStroke("ESCAPE"),
                JComponentMixin.WHEN_IN_FOCUSED_WINDOW);

        s.resetKeyboardActions();

        assertEquals(0, s.getRegisteredKeyStrokes().length);
    }

    @Test
    @DisplayName("getRegisteredKeyStrokes returns the union across conditions")
    void registeredKeyStrokesUnion() {
        final TestJSurrogate s = new TestJSurrogate();
        final KeyStroke ksA = KeyStroke.getKeyStroke("F1");
        final KeyStroke ksB = KeyStroke.getKeyStroke("F2");
        s.registerKeyboardAction(e -> { }, ksA, JComponentMixin.WHEN_FOCUSED);
        s.registerKeyboardAction(e -> { }, ksB, JComponentMixin.WHEN_IN_FOCUSED_WINDOW);

        assertEquals(Set.of(ksA, ksB), Set.copyOf(Arrays.asList(s.getRegisteredKeyStrokes())));
    }

    @Test
    @DisplayName("setInputMap replacement tears down stale Vaadin shortcuts")
    void setInputMapTearsDownStaleShortcuts() {
        // setInputMap can swap a map without carrying the old strokes
        // forward — the corresponding Vaadin shortcuts must go too.
        final TestJSurrogate s = new TestJSurrogate();
        final KeyStroke ks = KeyStroke.getKeyStroke("ENTER");
        s.registerKeyboardAction(e -> { }, ks, JComponentMixin.WHEN_FOCUSED);
        assertNotNull(s.getActionForKeyStroke(ks));

        s.setInputMap(JComponentMixin.WHEN_FOCUSED, new InputMap());

        // InputMap entry gone (replacement was empty), ActionMap entry still there
        // (ActionMap persists across InputMap swaps in JDK), shortcut torn down.
        assertEquals(JComponentMixin.UNDEFINED_CONDITION, s.getConditionForKeyStroke(ks));
    }

    @Test
    @DisplayName("unmappable KeyStroke logs onUnimplemented but still stores InputMap entry")
    void unmappableKeyStrokeStillStores() {
        // A keystroke whose keyCode isn't in vkToVaadinKey's table — the
        // browser-side install fails (WARN) but the InputMap / ActionMap
        // entries land so server-side query paths keep working.
        final TestJSurrogate s = new TestJSurrogate();
        final KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0);  // F24 isn't mapped
        s.registerKeyboardAction(e -> { }, ks, JComponentMixin.WHEN_FOCUSED);

        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).contains("unmappable-keystroke"));
        assertNotNull(s.getActionForKeyStroke(ks),
                "InputMap/ActionMap entry should land even when shortcut install skips");
    }

    // --- AncestorListener --------------------------------------------

    @Test
    @DisplayName("addAncestorListener fires ANCESTOR_ADDED on attach and is silent")
    void ancestorListenerFiresOnAttach() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<SAncestorEvent> events = new ArrayList<>();
        s.addAncestorListener(new SAncestorListener() {
            @Override
            public void ancestorAdded(SAncestorEvent event) {
                events.add(event);
            }

            @Override
            public void ancestorRemoved(SAncestorEvent event) {
                events.add(event);
            }

            @Override
            public void ancestorMoved(SAncestorEvent event) {
                events.add(event);
            }
        });

        UI.getCurrent().add(s);

        assertEquals(0, capturedWarns.size(), "addAncestorListener does not WARN");
        assertEquals(1, events.size());
        assertEquals(SAncestorEvent.ANCESTOR_ADDED, events.get(0).getID());
        assertEquals(s, events.get(0).getComponent());
    }

    @Test
    @DisplayName("addAncestorListener fires ANCESTOR_REMOVED on detach")
    void ancestorListenerFiresOnDetach() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<Integer> ids = recordAncestorIds(s);
        UI.getCurrent().add(s);
        UI.getCurrent().remove(s);

        assertEquals(List.of(SAncestorEvent.ANCESTOR_ADDED, SAncestorEvent.ANCESTOR_REMOVED), ids);
    }

    @Test
    @DisplayName("removeAncestorListener stops further callbacks")
    void removeAncestorListenerStops() {
        final TestJSurrogate s = new TestJSurrogate();
        final List<SAncestorEvent> events = new ArrayList<>();
        final SAncestorListener l = new SAncestorListener() {
            @Override
            public void ancestorAdded(SAncestorEvent event) {
                events.add(event);
            }

            @Override
            public void ancestorRemoved(SAncestorEvent event) {
                events.add(event);
            }

            @Override
            public void ancestorMoved(SAncestorEvent event) {
                events.add(event);
            }
        };
        s.addAncestorListener(l);
        s.removeAncestorListener(l);

        UI.getCurrent().add(s);
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("getAncestorListeners reflects add and remove")
    void ancestorListenersReflectAddRemove() {
        final TestJSurrogate s = new TestJSurrogate();
        assertEquals(0, s.getAncestorListeners().length);
        final SAncestorListener l = inertAncestorListener();
        s.addAncestorListener(l);
        assertEquals(1, s.getAncestorListeners().length);
        assertSame(l, s.getAncestorListeners()[0]);
        s.removeAncestorListener(l);
        assertEquals(0, s.getAncestorListeners().length);
    }

    // --- VetoableChangeListener (R_vaadin_first drop-and-WARN at surrogate stage) -

    @Test
    @DisplayName("addVetoableChangeListener WARNs (R_vaadin_first drop-and-WARN)")
    void vetoableChangeListenerWarns() {
        final TestJSurrogate s = new TestJSurrogate();
        s.addVetoableChangeListener(e -> { });
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("setTransferHandler WARNs")
    void transferHandlerWarns() {
        final TestJSurrogate s = new TestJSurrogate();
        s.setTransferHandler(null);  // setter logs regardless — migration signal
        // Actually the setter warns because transferHandler isn't modelled.
        // A stricter warn-only-on-non-null would also be defensible; check
        // current behavior: WARN regardless.
        assertEquals(1, capturedWarns.size());
    }

    @Test
    @DisplayName("getTopLevelAncestor returns null for detached surrogate, no WARN")
    void topLevelAncestorDetachedIsNull() {
        final TestJSurrogate s = new TestJSurrogate();
        assertNull(s.getTopLevelAncestor());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getTopLevelAncestor returns null when attached under UI with no Dialog, no WARN")
    void topLevelAncestorWithoutDialogIsNull() {
        final TestJSurrogate s = new TestJSurrogate();
        UI.getCurrent().add(s);
        assertNull(s.getTopLevelAncestor());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getTopLevelAncestor returns the enclosing Dialog, no WARN")
    void topLevelAncestorIsEnclosingDialog() {
        final Dialog dialog = new Dialog();
        final TestJSurrogate s = new TestJSurrogate();
        dialog.add(s);
        dialog.open();
        assertSame(dialog, s.getTopLevelAncestor());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getRootPane returns null when detached, no WARN")
    void rootPaneDetachedIsNull() {
        final TestJSurrogate s = new TestJSurrogate();
        assertNull(s.getRootPane());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getRootPane returns null when no SJRootPane in ancestor chain, no WARN")
    void rootPaneWithoutAncestorIsNull() {
        final TestJSurrogate s = new TestJSurrogate();
        UI.getCurrent().add(s);
        assertNull(s.getRootPane());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getRootPane returns the enclosing SJRootPane")
    void rootPaneIsEnclosingRootPane() {
        final SJRootPane rp = new SJRootPane();
        final TestJSurrogate s = new TestJSurrogate();
        rp.add(s);
        assertSame(rp, s.getRootPane());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getRootPane returns the SJRootPane through an SJFrame")
    void rootPaneThroughFrame() {
        // SJFrame holds its SJRootPane non-structurally; the walk doesn't
        // climb to it from a child of the frame's content pane, since
        // SJFrame's SJRootPane isn't structurally above the contentPane.
        // Instead, attach the surrogate directly under the SJFrame's
        // SJRootPane via getRootPane().add to verify the walk lands.
        final SJFrame frame = new SJFrame();
        final TestJSurrogate s = new TestJSurrogate();
        frame.getRootPane().add(s);
        assertSame(frame.getRootPane(), s.getRootPane());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("scrollRectToVisible emits no WARN")
    void scrollRectToVisibleIsSilent() {
        final TestJSurrogate s = new TestJSurrogate();
        UI.getCurrent().add(s);
        s.scrollRectToVisible(new Rectangle(0, 0, 10, 10));
        assertEquals(0, capturedWarns.size());
    }
}
