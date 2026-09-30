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

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.shared.HasTooltip;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.awt.Container;
import vaadinx.awt.Frame;
import vaadinx.awt.Window;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import javax.swing.InputMap;
import javax.swing.KeyStroke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JComponentTest extends AbstractKaribuTest {

    /** The inline style value the peer element carries for {@code property}, or null. */
    private static String style(vaadinx.awt.Component c, String property) {
        return c.getPeer().getElement().getStyle().get(property);
    }

    private static boolean hasClass(vaadinx.awt.Component c, String cssClass) {
        return c.getPeer().getElement().getClassList().contains(cssClass);
    }

    /**
     * Runs {@code body} with the WARN hook capturing into a fresh list, restoring
     * the no-op hook afterwards, and hands the captured WARNs to the caller.
     */
    private static List<String> capturingWarns(Runnable body) {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;
        try {
            body.run();
        } finally {
            EHelper.warnHook = msg -> { };
        }
        return warnings;
    }

    /** Fires the browser-originated blur the peer would report on a real DOM blur. */
    private static void blurFromClient(JTextField tf) {
        TextField peer = (TextField) tf.getPeer();
        ComponentUtil.fireEvent(peer, new BlurNotifier.BlurEvent<>(peer, true));
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new TestJComponent();
    }

    @Test
    @DisplayName("setOpaque round-trips and isOpaque reports the stored value")
    void setOpaqueRoundTrips() {
        // Swing's JComponent flips AWT's opaque=true default to false;
        // verify the initial state plus both directions of the setter so
        // paramString and any opaque-gated user code see consistent values.
        TestJComponent c = new TestJComponent();
        assertFalse(c.isOpaque());

        c.setOpaque(true);
        assertTrue(c.isOpaque());

        c.setOpaque(false);
        assertFalse(c.isOpaque());
    }

    /** One observed transition of a boolean bound property. */
    private record BooleanChange(boolean oldValue, boolean newValue) {
    }

    @Test
    @DisplayName("setOpaque fires PropertyChangeEvent only on actual change")
    void setOpaqueFiresOnlyOnActualChange() {
        // Swing contract: no-op set doesn't fire. Matters for listeners
        // counting transitions.
        TestJComponent c = new TestJComponent();
        List<BooleanChange> hits = new ArrayList<>();
        c.addPropertyChangeListener("opaque",
                e -> hits.add(new BooleanChange((Boolean) e.getOldValue(), (Boolean) e.getNewValue())));

        c.setOpaque(false); // already false — no event
        c.setOpaque(true);
        c.setOpaque(true);  // idempotent — no event
        c.setOpaque(false);

        assertEquals(List.of(new BooleanChange(false, true), new BooleanChange(true, false)), hits);
    }

    @Test
    @DisplayName("a background on a non-opaque component paints nothing")
    void aBackgroundOnANonOpaqueComponentPaintsNothing() {
        // D_theme_is_lookandfeel: the opaque gate runs in BOTH directions, so a
        // background set while non-opaque writes no CSS. This reproduces Swing's
        // paper cut — label.setBackground(GREEN) on a non-opaque JLabel paints
        // nothing — rather than silently improving on it (R_no_silent_improvements).
        // TestJComponent has no entry in the L&F defaults table, so nothing
        // installs an opaque default and it starts non-opaque.
        TestJComponent c = new TestJComponent();
        c.setBackground(new Color(255, 0, 0));
        assertNull(style(c, "background-color"));

        c.setOpaque(true);

        // Turning it opaque re-evaluates the same rule and the colour lands.
        assertEquals("rgb(255,0,0)", style(c, "background-color"));
    }

    @Test
    @DisplayName("setOpaque false clears the app-set background")
    void setOpaqueFalseClearsTheAppSetBackground() {
        TestJComponent c = new TestJComponent();
        c.setOpaque(true);
        c.setBackground(new Color(0, 128, 0));
        assertEquals("rgb(0,128,0)", style(c, "background-color"));

        c.setOpaque(false);

        // Removed rather than written as "transparent": background-color does
        // not inherit in CSS, so the two render identically, and removing keeps
        // the declaration out of the way of a stylesheet rule.
        assertNull(style(c, "background-color"));
    }

    @Test
    @DisplayName("an opaque structural component takes the default fill class")
    void anOpaqueStructuralComponentTakesTheDefaultFillClass() {
        // The visible half of D_theme_is_lookandfeel, and the fix for the glass
        // pane that started it: an opaque JPanel with no app-set colour now
        // carries a class the shipped stylesheet resolves to
        // var(--vaadin-background-color) — an opaque token, so it curtains.
        JPanel p = new JPanel();
        assertTrue(hasClass(p, "emul-bg-default"));
        assertNull(style(p, "background-color"));

        // An app-set colour replaces the default rather than layering over it:
        // inline wins the cascade, and leaving the class on would be dead CSS.
        p.setBackground(new Color(255, 0, 0));
        assertFalse(hasClass(p, "emul-bg-default"));
        assertEquals("rgb(255,0,0)", style(p, "background-color"));

        // Non-opaque paints nothing at all, by either mechanism.
        p.setOpaque(false);
        assertFalse(hasClass(p, "emul-bg-default"));
        assertNull(style(p, "background-color"));
    }

    @Test
    @DisplayName("a themed widget peer takes no default fill")
    void aThemedWidgetPeerTakesNoDefaultFill() {
        // vaadin-button paints its own surface, so the theme is its L&F and
        // there is nothing for SB-Emulators to supply. Metal marks JButton opaque, so
        // this is the predicate declining rather than the opaque gate.
        JButton b = new JButton();
        assertTrue(b.isOpaque());
        assertFalse(hasClass(b, "emul-bg-default"));
        assertFalse(hasClass(b, "emul-bg-chrome"));
    }

    @Test
    @DisplayName("a toolbar takes the chrome tint, not the surface fill")
    void aToolbarTakesTheChromeTint() {
        // Measured in a browser: the base token is invisible on a toolbar
        // because it matches the content behind it; the container token renders
        // the raised strip a migrated app expects.
        JToolBar t = new JToolBar();
        assertTrue(hasClass(t, "emul-bg-chrome"));
        assertFalse(hasClass(t, "emul-bg-default"));
    }

    @Test
    @DisplayName("an installed L&F default is not written as a literal")
    void anInstalledLookAndFeelDefaultIsNotWrittenAsALiteral() {
        // The marker at work: JPanel installs Metal's #EEEEEE as a
        // ColorUIResource, so getBackground() is non-null (no NPE for
        // getBackground().darker()) but no inline CSS pins Metal's grey and the
        // theme still shows through.
        JPanel p = new JPanel();
        assertEquals(new Color(238, 238, 238), p.getBackground());
        assertTrue(p.isOpaque());
        assertNull(style(p, "background-color"));

        // The same RGB set by the app IS a literal — value-equality could not
        // tell these apart, the UIResource marker can.
        p.setBackground(new Color(238, 238, 238));
        assertEquals("rgb(238,238,238)", style(p, "background-color"));
    }

    @Test
    @DisplayName("setOpaque true clears background-color when getBackground is null")
    void setOpaqueTrueClearsBackgroundWhenNoneSet() {
        // Fresh component, no background set, no parent — getBackground
        // returns null. setOpaque(true) removes any prior CSS.
        TestJComponent c = new TestJComponent();
        c.getPeer().getElement().getStyle().set("background-color", "transparent");

        c.setOpaque(true);

        assertNull(style(c, "background-color"));
    }

    @Test
    @DisplayName("setOpaque round-trip preserves emulator background field")
    void setOpaqueRoundTripPreservesTheBackgroundField() {
        // Emulator-side field shadow round-trips even through opaque
        // toggling — getBackground still returns the user's color after
        // setOpaque(false), unlike the surrogate (R_vaadin_first lossy).
        TestJComponent c = new TestJComponent();
        Color red = new Color(255, 0, 0);
        c.setBackground(red);
        c.setOpaque(false);
        assertEquals(red, c.getBackground());
        c.setOpaque(true);
        assertEquals(red, c.getBackground());
        // Opaque=true re-applied the field's color to CSS.
        assertEquals("rgb(255,0,0)", style(c, "background-color"));
    }

    @Test
    @DisplayName("putClientProperty and getClientProperty round-trip")
    void clientPropertyRoundTrips() {
        TestJComponent c = new TestJComponent();
        assertNull(c.getClientProperty("k"));

        c.putClientProperty("k", "v");
        assertEquals("v", c.getClientProperty("k"));

        // Null key on get is tolerated (Swing's JComponent matches this).
        assertNull(c.getClientProperty(null));
    }

    @Test
    @DisplayName("putClientProperty with null value removes the mapping")
    void putClientPropertyNullValueRemovesTheMapping() {
        // Swing contract — a null value is the documented way to unset,
        // distinct from "never set" (both report null on get).
        TestJComponent c = new TestJComponent();
        c.putClientProperty("k", "v");
        c.putClientProperty("k", null);
        assertNull(c.getClientProperty("k"));
    }

    @Test
    @DisplayName("putClientProperty rejects a null key")
    void putClientPropertyRejectsANullKey() {
        // Real Swing NPEs on null keys; D_never_fail_on_gaps carves out this kind of
        // programming error from the never-throw rule.
        assertThrows(NullPointerException.class, () -> new TestJComponent().putClientProperty(null, "v"));
    }

    /** One observed client-property PCE, as (property, old, new). */
    private record PropertyFire(String property, Object oldValue, Object newValue) {
    }

    @Test
    @DisplayName("putClientProperty fires PropertyChangeEvent named after the key")
    void putClientPropertyFiresNamedAfterTheKey() {
        // Swing fires on key.toString(); String keys are the common case,
        // so a listener registered for that property name sees the write.
        TestJComponent c = new TestJComponent();
        List<PropertyFire> hits = new ArrayList<>();
        c.addPropertyChangeListener("tag",
                e -> hits.add(new PropertyFire(e.getPropertyName(), e.getOldValue(), e.getNewValue())));

        c.putClientProperty("tag", "first");
        c.putClientProperty("tag", "second");
        c.putClientProperty("tag", "second"); // idempotent — no event
        c.putClientProperty("tag", null);

        assertEquals(
                List.of(
                        new PropertyFire("tag", null, "first"),
                        new PropertyFire("tag", "first", "second"),
                        new PropertyFire("tag", "second", null)),
                hits);
    }

    @Test
    @DisplayName("isLightweightComponent returns false for Windows, true for everything else")
    void isLightweightComponentSplitsOnWindow() {
        // Swing's dichotomy: Windows have peers, everything else is painted
        // by the parent. Our Windows back onto Dialog peers, everything
        // else lives inside a parent's element — same split.
        assertTrue(JComponent.isLightweightComponent(new TestJComponent()));
        assertTrue(JComponent.isLightweightComponent(new Container()));
        assertFalse(JComponent.isLightweightComponent(new Frame()));
    }

    @Test
    @DisplayName("getTopLevelAncestor walks parents until a Window")
    void getTopLevelAncestorWalksUntilAWindow() {
        // Swing pattern for "am I attached to a window yet?" and for
        // JOptionPane-style positioning. Null if not under a Window —
        // we return that honestly, no synthesized fallback.
        TestJComponent leaf = new TestJComponent();
        Container middle = new Container();
        Frame frame = new Frame();

        assertNull(leaf.getTopLevelAncestor());

        middle.add(leaf);
        assertNull(leaf.getTopLevelAncestor());

        frame.add(middle);
        assertSame(frame, leaf.getTopLevelAncestor());
    }

    @Test
    @DisplayName("honest-default getters return Swing's documented defaults")
    void honestDefaultGettersReturnSwingDefaults() {
        // Smoke guard for the honest-default pass — catches regressions
        // if anyone flips one of these to onUnimplemented in the future.
        TestJComponent c = new TestJComponent();
        assertFalse(c.isDoubleBuffered());
        assertTrue(c.isOptimizedDrawingEnabled());
        assertFalse(c.isManagingFocus());
        assertFalse(c.isPaintingTile());
        assertFalse(c.isPaintingForPrint());
        assertFalse(c.getInheritsPopupMenu());
        assertFalse(c.getAutoscrolls());
        assertTrue(c.isRequestFocusEnabled());
        assertTrue(c.getVerifyInputWhenFocusTarget());
        assertEquals(0.5f, c.getAlignmentX());
        assertEquals(0.5f, c.getAlignmentY());
        assertEquals(-1, c.getBaseline(100, 20));
        assertEquals(0, c.getDebugGraphicsOptions());
    }

    @Test
    @DisplayName("paramString appends opaque and doubleBuffered")
    void paramStringAppendsOpaqueAndDoubleBuffered() {
        // JComponent's paramString contract is "super's output plus
        // JComponent's own fields" — opaque flips with setOpaque so the
        // default-false path and a post-setOpaque-true path both get
        // the honest value.
        TestJComponent c = new TestJComponent();
        String before = c.exposedParamString();
        assertTrue(before.contains("opaque=false"), before);
        assertTrue(before.contains("doubleBuffered=false"), before);

        c.setOpaque(true);
        assertTrue(c.exposedParamString().contains("opaque=true"));
    }

    @Test
    @DisplayName("getTopLevelAncestor walks up to the enclosing Window")
    void getTopLevelAncestorWalksUpToTheEnclosingWindow() {
        // Only one Window can appear in a containment chain — Container.addImpl
        // rejects nesting one, as AWT does (see ContainerTest), so the
        // "nearest vs outermost Window" distinction cannot arise here or in
        // real Swing. Nesting depth below the Window is what's worth asserting.
        TestJComponent leaf = new TestJComponent();
        JPanel mid = new JPanel();
        Window window = new Frame();

        mid.add(leaf);
        window.add(mid);

        assertSame(window, leaf.getTopLevelAncestor());
    }

    // --- focus + InputVerifier

    @Test
    @DisplayName("requestFocus on a Focusable-peer JComponent fires no WARN")
    void requestFocusOnAFocusablePeerIsWarnFree() {
        // JButton's peer is a Vaadin Button which implements Focusable.
        // Wiring should silently delegate to peer.focus() — no stub WARN.
        List<String> warnings = capturingWarns(() -> new JButton("Go").requestFocus());
        assertNoWarns(warnings, warnings.toString());
    }

    @Test
    @DisplayName("requestFocusInWindow returns true on a Focusable peer")
    void requestFocusInWindowReturnsTrueOnAFocusablePeer() {
        // Contract: true when the request was delivered to the peer.
        // Browser acceptance isn't knowable server-side; matches JDK's
        // "true means we tried, not that focus landed" contract.
        assertTrue(new JButton("Go").requestFocusInWindow());
    }

    @Test
    @DisplayName("grabFocus on a Focusable peer fires no WARN")
    void grabFocusOnAFocusablePeerIsWarnFree() {
        List<String> warnings = capturingWarns(() -> new JButton("Go").grabFocus());
        assertNoWarns(warnings, warnings.toString());
    }

    @Test
    @DisplayName("requestFocus on a non-Focusable peer logs WARN once")
    void requestFocusOnANonFocusablePeerWarnsOnce() {
        // JPanel's peer is a plain Div — not Focusable. Migrations
        // requesting focus on a container surface the gap via WARN.
        List<String> warnings = capturingWarns(() -> new JPanel().requestFocus());
        assertTrue(assertSingle(warnings).contains("non-focusable-peer"), warnings.toString());
    }

    @Test
    @DisplayName("setInputVerifier round-trips through getInputVerifier")
    void setInputVerifierRoundTrips() {
        JTextField tf = new JTextField();
        assertNull(tf.getInputVerifier());
        InputVerifier v = new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                return true;
            }
        };
        tf.setInputVerifier(v);
        assertSame(v, tf.getInputVerifier());
        tf.setInputVerifier(null);
        assertNull(tf.getInputVerifier());
    }

    /** An InputVerifier that always passes, recording every call and its argument. */
    private static final class RecordingVerifier extends InputVerifier {
        int hits;
        JComponent receivedInput;

        @Override
        public boolean verify(JComponent input) {
            hits++;
            receivedInput = input;
            return true;
        }
    }

    @Test
    @DisplayName("setInputVerifier installs peer blur listener that calls shouldYieldFocus")
    void setInputVerifierInstallsABlurListener() {
        JTextField tf = new JTextField("hello");
        RecordingVerifier v = new RecordingVerifier();
        tf.setInputVerifier(v);

        // Simulate a browser-originated blur on the peer. fromClient=true
        // matches what Vaadin fires on a real DOM blur event.
        blurFromClient(tf);

        assertEquals(1, v.hits);
        assertSame(tf, v.receivedInput);  // JDK contract: pass the source
    }

    @Test
    @DisplayName("replacing the InputVerifier detaches the old blur listener")
    void replacingTheInputVerifierDetachesTheOldListener() {
        JTextField tf = new JTextField();
        RecordingVerifier oldV = new RecordingVerifier();
        RecordingVerifier newV = new RecordingVerifier();
        tf.setInputVerifier(oldV);
        tf.setInputVerifier(newV);

        blurFromClient(tf);

        assertEquals(0, oldV.hits);
        assertEquals(1, newV.hits);
    }

    @Test
    @DisplayName("setInputVerifier null removes the blur listener")
    void setInputVerifierNullRemovesTheBlurListener() {
        JTextField tf = new JTextField();
        RecordingVerifier v = new RecordingVerifier();
        tf.setInputVerifier(v);
        tf.setInputVerifier(null);

        blurFromClient(tf);

        assertEquals(0, v.hits);
    }

    @Test
    @DisplayName("setInputVerifier fires inputVerifier PropertyChangeEvent")
    void setInputVerifierFiresPce() {
        JTextField tf = new JTextField();
        List<PropertyChangeEvent> events = new ArrayList<>();
        tf.addPropertyChangeListener("inputVerifier", events::add);

        RecordingVerifier v = new RecordingVerifier();
        tf.setInputVerifier(v);

        assertSame(v, assertSingle(events).getNewValue());
    }

    // --- Tooltip wiring via HasTooltip

    @Test
    @DisplayName("setToolTipText round-trips via getToolTipText")
    void setToolTipTextRoundTrips() {
        JButton jb = new JButton("Go");
        assertNull(jb.getToolTipText());
        jb.setToolTipText("Hover me");
        assertEquals("Hover me", jb.getToolTipText());
        jb.setToolTipText(null);
        assertNull(jb.getToolTipText());
    }

    @Test
    @DisplayName("setToolTipText drives the peer via HasTooltip when supported")
    void setToolTipTextDrivesThePeer() {
        // Button implements com.vaadin.flow.component.shared.HasTooltip —
        // end-to-end sanity that the tooltip actually reaches the DOM
        // slot (Vaadin's Tooltip carries a getText() we can verify
        // against).
        JButton jb = new JButton("Go");
        jb.setToolTipText("Click to submit");
        assertEquals("Click to submit", ((HasTooltip) jb.getPeer()).getTooltip().getText());
    }

    @Test
    @DisplayName("setToolTipText fires ToolTipText PCE")
    void setToolTipTextFiresPce() {
        JButton jb = new JButton();
        List<PropertyChangeEvent> events = new ArrayList<>();
        jb.addPropertyChangeListener("ToolTipText", events::add);

        jb.setToolTipText("hint");

        assertEquals("hint", assertSingle(events).getNewValue());
    }

    @Test
    @DisplayName("setToolTipText stores under TOOL_TIP_TEXT_KEY and the PCE is putClientProperty's")
    void setToolTipTextStoresUnderTheClientPropertyKey() {
        // The JDK's setToolTipText has no fire of its own: it calls
        // putClientProperty(TOOL_TIP_TEXT_KEY, text), and putClientProperty
        // fires key.toString(). Routing rather than duplicating is what makes
        // getClientProperty see the tooltip, as on the desktop (D_property_fanout_audit, R_no_vaadin_in_api limb 2).
        JButton jb = new JButton();
        List<PropertyChangeEvent> events = new ArrayList<>();
        jb.addPropertyChangeListener("ToolTipText", events::add);

        jb.setToolTipText("hint");

        assertEquals("hint", jb.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY));
        assertEquals(1, events.size());
        assertNull(events.get(0).getOldValue());

        // Clearing removes the mapping, exactly as putClientProperty(null) does.
        jb.setToolTipText(null);
        assertNull(jb.getClientProperty(JComponent.TOOL_TIP_TEXT_KEY));
        assertNull(jb.getToolTipText());
        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("setEnabled fires the bound enabled property at JComponent level")
    void setEnabledFiresTheBoundPropertyAtJComponentLevel() {
        // The fire lives here, not on vaadinx.awt.Component: the JDK's
        // java.awt.Component.setEnabled fires nothing bound and
        // JComponent.setEnabled is the override that adds it (D_property_fanout_audit).
        // ComponentVisibilityEnabledTest pins the AWT-level silence.
        JButton jb = new JButton("Go");
        List<PropertyChangeEvent> events = new ArrayList<>();
        jb.addPropertyChangeListener("enabled", events::add);

        jb.setEnabled(false);
        jb.setEnabled(false);   // no change — equality dedupe suppresses
        jb.setEnabled(true);

        assertEquals(2, events.size());
        assertEquals(true, events.get(0).getOldValue());
        assertEquals(false, events.get(0).getNewValue());
        assertEquals(false, events.get(1).getOldValue());
        assertEquals(true, events.get(1).getNewValue());
    }

    @Test
    @DisplayName("setToolTipText equal value is a no-op")
    void setToolTipTextEqualValueIsANoOp() {
        JButton jb = new JButton("Go");
        jb.setToolTipText("Hover me");
        List<PropertyChangeEvent> events = new ArrayList<>();
        jb.addPropertyChangeListener("ToolTipText", events::add);

        jb.setToolTipText("Hover me");  // same value

        assertTrue(events.isEmpty());
    }

    // --- InputMap / ActionMap / registerKeyboardAction

    @Test
    @DisplayName("getInputMap no-arg routes to WHEN_FOCUSED")
    void getInputMapNoArgRoutesToWhenFocused() {
        TestJComponent c = new TestJComponent();
        assertSame(c.getInputMap(JComponent.WHEN_FOCUSED), c.getInputMap());
    }

    @Test
    @DisplayName("getInputMap returns distinct maps per condition")
    void getInputMapReturnsDistinctMapsPerCondition() {
        TestJComponent c = new TestJComponent();
        InputMap focused = c.getInputMap(JComponent.WHEN_FOCUSED);
        InputMap ancestor = c.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        InputMap window = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        // All non-null, all distinct instances.
        assertNotSame(focused, ancestor);
        assertNotSame(ancestor, window);
        assertNotSame(focused, window);
    }

    @Test
    @DisplayName("getInputMap is lazily stable - second call returns same instance")
    void getInputMapIsLazilyStable() {
        TestJComponent c = new TestJComponent();
        InputMap first = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        InputMap second = c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        assertSame(first, second);
    }

    @Test
    @DisplayName("getInputMap rejects invalid condition per R_match_swing_errors")
    void getInputMapRejectsAnInvalidCondition() {
        assertThrows(IllegalArgumentException.class, () -> new TestJComponent().getInputMap(999));
    }

    @Test
    @DisplayName("getActionMap is lazy and stable")
    void getActionMapIsLazyAndStable() {
        TestJComponent c = new TestJComponent();
        assertSame(c.getActionMap(), c.getActionMap());
    }

    @Test
    @DisplayName("registerKeyboardAction stores InputMap and ActionMap entries")
    void registerKeyboardActionStoresBothMapEntries() {
        TestJComponent c = new TestJComponent();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
        ActionListener listener = e -> { /* noop */ };
        c.registerKeyboardAction(listener, stroke, JComponent.WHEN_IN_FOCUSED_WINDOW);

        assertEquals(JComponent.WHEN_IN_FOCUSED_WINDOW, c.getConditionForKeyStroke(stroke));
        // getActionForKeyStroke resolves InputMap → key → ActionMap → Action.
        // Returns the ActionStandin; actionPerformed delegates to our listener.
        assertNotNull(c.getActionForKeyStroke(stroke));
    }

    @Test
    @DisplayName("getConditionForKeyStroke returns UNDEFINED for unbound")
    void getConditionForKeyStrokeReturnsUndefinedForUnbound() {
        TestJComponent c = new TestJComponent();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_S, 0);
        assertEquals(JComponent.UNDEFINED_CONDITION, c.getConditionForKeyStroke(stroke));
    }

    @Test
    @DisplayName("unregisterKeyboardAction removes the binding")
    void unregisterKeyboardActionRemovesTheBinding() {
        TestJComponent c = new TestJComponent();
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        c.registerKeyboardAction(noopListener(), stroke, JComponent.WHEN_FOCUSED);
        assertEquals(JComponent.WHEN_FOCUSED, c.getConditionForKeyStroke(stroke));

        c.unregisterKeyboardAction(stroke);
        assertEquals(JComponent.UNDEFINED_CONDITION, c.getConditionForKeyStroke(stroke));
        assertNull(c.getActionForKeyStroke(stroke));
    }

    @Test
    @DisplayName("getRegisteredKeyStrokes returns union across conditions")
    void getRegisteredKeyStrokesReturnsTheUnion() {
        TestJComponent c = new TestJComponent();
        KeyStroke s1 = KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);
        KeyStroke s2 = KeyStroke.getKeyStroke(KeyEvent.VK_B, 0);
        KeyStroke s3 = KeyStroke.getKeyStroke(KeyEvent.VK_C, 0);
        c.registerKeyboardAction(noopListener(), s1, JComponent.WHEN_FOCUSED);
        c.registerKeyboardAction(noopListener(), s2, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        c.registerKeyboardAction(noopListener(), s3, JComponent.WHEN_IN_FOCUSED_WINDOW);

        assertEquals(Set.of(s1, s2, s3), Set.copyOf(Arrays.asList(c.getRegisteredKeyStrokes())));
    }

    @Test
    @DisplayName("resetKeyboardActions clears every map")
    void resetKeyboardActionsClearsEveryMap() {
        TestJComponent c = new TestJComponent();
        KeyStroke s = KeyStroke.getKeyStroke(KeyEvent.VK_X, 0);
        c.registerKeyboardAction(noopListener(), s, JComponent.WHEN_IN_FOCUSED_WINDOW);

        c.resetKeyboardActions();

        assertEquals(0, c.getRegisteredKeyStrokes().length);
        assertEquals(JComponent.UNDEFINED_CONDITION, c.getConditionForKeyStroke(s));
    }

    @Test
    @DisplayName("setInputMap replacement removes the old binding's shortcut")
    void setInputMapReplacementRemovesTheOldShortcut() {
        // Regression: installing a stroke, then wiping the InputMap, must
        // not leave a stale Vaadin shortcut firing in the browser.
        TestJComponent c = new TestJComponent();
        KeyStroke s = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK);
        c.registerKeyboardAction(noopListener(), s, JComponent.WHEN_FOCUSED);

        c.setInputMap(JComponent.WHEN_FOCUSED, new InputMap());
        assertEquals(JComponent.UNDEFINED_CONDITION, c.getConditionForKeyStroke(s));
    }

    @Test
    @DisplayName("unmappable KeyStroke logs WARN but still stores InputMap entry")
    void unmappableKeyStrokeWarnsButStillStores() {
        // Punctuation keys aren't in our VK→Key table; the Vaadin
        // shortcut install is skipped, but the InputMap/ActionMap entry
        // still lands so getActionForKeyStroke keeps working on the
        // server side.
        TestJComponent c = new TestJComponent();
        KeyStroke unmappable = KeyStroke.getKeyStroke(KeyEvent.VK_SEMICOLON, 0);
        List<String> warnings = capturingWarns(
                () -> c.registerKeyboardAction(noopListener(), unmappable, JComponent.WHEN_FOCUSED));

        assertTrue(warnings.stream().anyMatch(w -> w.contains("unmappable-keystroke")), warnings.toString());
        assertEquals(JComponent.WHEN_FOCUSED, c.getConditionForKeyStroke(unmappable));
    }

    // --- VetoableChangeListener (wired) -----

    @Test
    @DisplayName("addVetoableChangeListener registers and fireVetoableChange dispatches")
    void vetoableChangeListenerDispatches() throws PropertyVetoException {
        TestJComponent c = new TestJComponent();
        List<PropertyChangeEvent> received = new ArrayList<>();
        c.addVetoableChangeListener(received::add);

        c.exposedFireVetoableChange("opacity", 0.5f, 1.0f);

        PropertyChangeEvent e = assertSingle(received);
        assertEquals("opacity", e.getPropertyName());
        assertEquals(0.5f, e.getOldValue());
        assertEquals(1.0f, e.getNewValue());
        assertSame(c, e.getSource());
    }

    /** One forward-or-rollback firing seen by a vetoable listener. */
    private record VetoableFire(Object oldValue, Object newValue) {
    }

    @Test
    @DisplayName("vetoing listener throws PropertyVetoException with rollback")
    void vetoingListenerThrowsWithRollback() {
        TestJComponent c = new TestJComponent();
        List<VetoableFire> seen = new ArrayList<>();
        c.addVetoableChangeListener(e -> seen.add(new VetoableFire(e.getOldValue(), e.getNewValue())));
        c.addVetoableChangeListener(e -> {
            throw new PropertyVetoException("nope", e);
        });

        assertThrows(PropertyVetoException.class, () -> c.exposedFireVetoableChange("x", "old", "new"));
        // First listener sees forward + rollback firings.
        assertEquals(
                List.of(new VetoableFire("old", "new"), new VetoableFire("new", "old")),
                seen);
    }

    @Test
    @DisplayName("removeVetoableChangeListener stops further callbacks")
    void removeVetoableChangeListenerStopsCallbacks() throws PropertyVetoException {
        TestJComponent c = new TestJComponent();
        Counter hits = new Counter();
        VetoableChangeListener l = e -> hits.inc();
        c.addVetoableChangeListener(l);
        c.removeVetoableChangeListener(l);
        c.exposedFireVetoableChange("x", 1, 2);
        hits.assertEquals(0);
    }

    @Test
    @DisplayName("getVetoableChangeListeners returns registered listeners in order")
    void getVetoableChangeListenersReturnsThemInOrder() {
        TestJComponent c = new TestJComponent();
        VetoableChangeListener a = e -> { };
        VetoableChangeListener b = e -> { };
        c.addVetoableChangeListener(a);
        c.addVetoableChangeListener(b);
        assertEquals(List.of(a, b), Arrays.asList(c.getVetoableChangeListeners()));
    }

    /** An ActionListener that does nothing — the keyboard-action tests only care about the binding. */
    private static ActionListener noopListener() {
        return (ActionEvent e) -> { };
    }

    /**
     * Concrete JComponent subclass so tests can instantiate and reach the
     * protected paramString. Mirrors WindowTest.TestWindow in shape.
     */
    private static class TestJComponent extends JComponent {

        String exposedParamString() {
            return paramString();
        }

        void exposedFireVetoableChange(String name, Object old, Object value) throws PropertyVetoException {
            fireVetoableChange(name, old, value);
        }
    }
}
