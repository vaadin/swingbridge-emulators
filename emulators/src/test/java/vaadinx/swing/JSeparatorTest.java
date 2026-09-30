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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJSeparator;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import javax.swing.SwingConstants;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Exit gate for vaadinx.swing.JSeparator (D_jseparator). Pairs with SJSeparatorTest
 * — this layer covers the thin shell's forwarding behaviour:
 *
 * <ol>
 *  <li><b>Ctor variants</b> — SJSeparator peer always installed; no-arg
 *     defaults to HORIZONTAL; int-arg passes through; garbage IAE
 *     bubbles up from the peer per R_match_swing_errors.</li>
 *  <li><b>Orientation forwarding</b> — setOrientation flips the peer's CSS;
 *     getOrientation reads back through the peer.</li>
 *  <li><b>Emulator-side PCE</b> — a listener added to the JSeparator fires on
 *     orientation change (independent-layer, SD_sjslider/SD_sjspinner), no-op on equal.</li>
 *  <li><b>L&amp;F surface</b> — getUIClassID == "SeparatorUI"; getAccessibleContext
 *     WARNs + null.</li>
 * </ol>
 */
class JSeparatorTest extends AbstractKaribuTest {

    private final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        capturedWarns.clear();
        Consumer<String> sink = capturedWarns::add;
        EHelper.warnHook = sink;
        SHelper.warnHook = sink;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { /* no-op default */ };
        SHelper.warnHook = msg -> { /* no-op default */ };
    }

    // --- Constructors -------------------------------------------------------

    @Test
    @DisplayName("no-arg ctor wires SJSeparator peer with HORIZONTAL default")
    void noArgCtorWiresPeerWithHorizontalDefault() {
        JSeparator s = new JSeparator();
        assertInstanceOf(SJSeparator.class, s.getPeer());
        assertEquals(SwingConstants.HORIZONTAL, s.getOrientation());
        assertNoWarns(capturedWarns, "default ctor should be WARN-free: " + capturedWarns);
    }

    @Test
    @DisplayName("int-arg ctor accepts VERTICAL and passes through to peer")
    void intArgCtorPassesVerticalThrough() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        assertEquals(SwingConstants.VERTICAL, s.getOrientation());
        assertEquals(SwingConstants.VERTICAL, ((SJSeparator) s.getPeer()).getOrientation());
    }

    @Test
    @DisplayName("int-arg ctor throws IAE on garbage orientation")
    void intArgCtorThrowsOnGarbage() {
        assertThrows(IllegalArgumentException.class, () -> new JSeparator(42));
    }

    // --- Orientation forwarding --------------------------------------------

    @Test
    @DisplayName("setOrientation forwards to peer")
    void setOrientationForwardsToPeer() {
        JSeparator s = new JSeparator();
        s.setOrientation(SwingConstants.VERTICAL);
        SJSeparator peer = (SJSeparator) s.getPeer();
        assertEquals(SwingConstants.VERTICAL, peer.getOrientation());
        assertEquals("1px", peer.getElement().getStyle().get("min-width"));
    }

    // --- Emulator-side PCE --------------------------------------------------

    @Test
    @DisplayName("setOrientation fires emulator-side PCE on change and no-op on equal")
    void setOrientationFiresPceOnChangeOnly() {
        JSeparator s = new JSeparator();
        List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener(events::add);

        s.setOrientation(SwingConstants.VERTICAL);
        PropertyChangeEvent pce = assertSingle(events.stream()
                .filter(e -> "orientation".equals(e.getPropertyName())).toList());
        assertEquals(SwingConstants.HORIZONTAL, pce.getOldValue());
        assertEquals(SwingConstants.VERTICAL, pce.getNewValue());

        events.clear();
        s.setOrientation(SwingConstants.VERTICAL);
        assertTrue(events.stream().noneMatch(e -> "orientation".equals(e.getPropertyName())),
                "no-op on equal orientation should fire no PCE");
    }

    @Test
    @DisplayName("setOrientation throws IAE on garbage and keeps the orientation")
    void setOrientationThrowsOnGarbage() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.setOrientation(99));
        assertEquals("orientation must be one of: VERTICAL, HORIZONTAL", e.getMessage());
        assertEquals(SwingConstants.VERTICAL, s.getOrientation());
        assertEquals(SwingConstants.VERTICAL, ((SJSeparator) s.getPeer()).getOrientation());
    }

    @Test
    @DisplayName("a separator is not focusable and paramString ends with its orientation, as in the JDK")
    void notFocusableAndParamStringIsTheJdks() {
        JSeparator s = new JSeparator();
        assertFalse(s.isFocusable());
        assertTrue(s.paramString().endsWith(",orientation=HORIZONTAL"), s.paramString());
        s.setOrientation(SwingConstants.VERTICAL);
        assertTrue(s.paramString().endsWith(",orientation=VERTICAL"), s.paramString());
    }

    // --- L&F surface --------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is SeparatorUI")
    void uiClassIdIsSeparatorUI() {
        assertEquals("SeparatorUI", new JSeparator().getUIClassID());
    }

    @Test
    @DisplayName("getAccessibleContext is deferred WARN plus null")
    void accessibleContextWarnsAndReturnsNull() {
        assertNull(new JSeparator().getAccessibleContext());
        assertTrue(capturedWarns.stream().anyMatch(w -> w.contains("getAccessibleContext")));
    }
}
