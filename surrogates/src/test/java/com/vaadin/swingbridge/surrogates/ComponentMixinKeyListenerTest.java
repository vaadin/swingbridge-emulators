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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.KeyDownEvent;
import com.vaadin.flow.component.KeyNotifier;
import com.vaadin.flow.component.KeyPressEvent;
import com.vaadin.flow.component.KeyUpEvent;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;
import com.vaadin.swingbridge.surrogates.awt.event.SInputEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyListener;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SD_key_events: {@code ComponentMixin.addKeyListener} wires Vaadin {@code KeyNotifier}'s
 * keydown/keypress/keyup subscriptions onto a single {@code SKeyListener}.
 * {@link TestSurrogate} extends {@code Button} (not a {@code KeyNotifier}) which exercises
 * the WARN-and-skip branch — see {@code ComponentMixinTest}. This file uses a
 * Div-backed fixture that explicitly implements {@code KeyNotifier} so the
 * successful wiring path is covered without dragging in TextField's
 * value-validation surface.
 */
class ComponentMixinKeyListenerTest extends AbstractKaribuTest {

    /**
     * Div is not a KeyNotifier by default, but {@code KeyNotifier}'s default
     * methods work on any {@link com.vaadin.flow.component.Component}, so
     * declaring it in the interface list is enough to opt in.
     */
    private static class KeySurrogate extends Div implements ComponentMixin, KeyNotifier {
    }

    /** Collects into {@code seen} through the keyPressed arm — the shape most tests here want. */
    private static void onKeyPressed(KeySurrogate s, List<SKeyEvent> seen) {
        s.addKeyListener(new SKeyAdapter() {
            @Override
            public void keyPressed(SKeyEvent e) {
                seen.add(e);
            }
        });
    }

    // --- Happy path: Vaadin events route to SKeyListener ------------

    @Test
    @DisplayName("keydown routes to keyPressed with VK_A and keyChar 'a'")
    void keydownRoutesToKeyPressedWithVkAAndKeyCharA() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        onKeyPressed(s, seen);
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "a"));
        assertEquals(1, seen.size());
        SKeyEvent e = seen.get(0);
        assertEquals(SKeyEvent.KEY_PRESSED, e.getID());
        assertEquals(SKeyEvent.VK_A, e.getKeyCode());
        assertEquals('a', e.getKeyChar());
        assertSame(s, e.getSource());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("keyup routes to keyReleased with named key Enter")
    void keyupRoutesToKeyReleasedWithNamedKeyEnter() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        s.addKeyListener(new SKeyAdapter() {
            @Override
            public void keyReleased(SKeyEvent e) {
                seen.add(e);
            }
        });
        ComponentUtil.fireEvent(s, new KeyUpEvent(s, "Enter"));
        assertEquals(1, seen.size());
        assertEquals(SKeyEvent.KEY_RELEASED, seen.get(0).getID());
        assertEquals(SKeyEvent.VK_ENTER, seen.get(0).getKeyCode());
        // Named keys have no keyChar — reported as CHAR_UNDEFINED.
        assertEquals(SKeyEvent.CHAR_UNDEFINED, seen.get(0).getKeyChar());
    }

    @Test
    @DisplayName("keypress routes to keyTyped with VK_UNDEFINED and defined keyChar")
    void keypressRoutesToKeyTypedWithVkUndefinedAndDefinedKeyChar() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        s.addKeyListener(new SKeyAdapter() {
            @Override
            public void keyTyped(SKeyEvent e) {
                seen.add(e);
            }
        });
        ComponentUtil.fireEvent(s, new KeyPressEvent(s, "x"));
        assertEquals(1, seen.size());
        SKeyEvent e = seen.get(0);
        // KEY_TYPED invariant: keyCode = VK_UNDEFINED, keyChar defined.
        assertEquals(SKeyEvent.KEY_TYPED, e.getID());
        assertEquals(SKeyEvent.VK_UNDEFINED, e.getKeyCode());
        assertEquals('x', e.getKeyChar());
    }

    @Test
    @DisplayName("arrow key maps to VK_LEFT without keyChar")
    void arrowKeyMapsToVkLeftWithoutKeyChar() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        onKeyPressed(s, seen);
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "ArrowLeft"));
        assertEquals(SKeyEvent.VK_LEFT, seen.get(0).getKeyCode());
        assertEquals(SKeyEvent.CHAR_UNDEFINED, seen.get(0).getKeyChar());
        assertTrue(seen.get(0).isActionKey());
    }

    @Test
    @DisplayName("F-keys map across the full range")
    void fKeysMapAcrossTheFullRange() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        onKeyPressed(s, seen);
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "F1"));
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "F12"));
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "F24"));
        assertEquals(SKeyEvent.VK_F1, seen.get(0).getKeyCode());
        assertEquals(SKeyEvent.VK_F12, seen.get(1).getKeyCode());
        assertEquals(SKeyEvent.VK_F24, seen.get(2).getKeyCode());
    }

    // --- Modifier bitmask mapping ----------------------------------

    @Test
    @DisplayName("Shift+Ctrl modifiers populate DOWN_MASK bits")
    void shiftPlusCtrlModifiersPopulateDownMaskBits() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        onKeyPressed(s, seen);
        // (source, fromClient, key, code, location, ctrl, shift, alt, meta, repeat, composing)
        ComponentUtil.fireEvent(
                s, new KeyDownEvent(s, false, "a", "KeyA", 0,
                        /* ctrl */ true, /* shift */ true, false, false, false, false));
        SKeyEvent e = seen.get(0);
        assertTrue(e.isShiftDown());
        assertTrue(e.isControlDown());
        assertFalse(e.isAltDown());
        assertFalse(e.isMetaDown());
        assertEquals(
                SInputEvent.SHIFT_DOWN_MASK | SInputEvent.CTRL_DOWN_MASK,
                e.getModifiersEx());
    }

    @Test
    @DisplayName("Meta+Alt modifiers populate DOWN_MASK bits")
    void metaPlusAltModifiersPopulateDownMaskBits() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        onKeyPressed(s, seen);
        ComponentUtil.fireEvent(
                s, new KeyDownEvent(s, false, "s", "KeyS", 0,
                        false, false, /* alt */ true, /* meta */ true, false, false));
        assertTrue(seen.get(0).isAltDown());
        assertTrue(seen.get(0).isMetaDown());
        assertEquals(
                SInputEvent.ALT_DOWN_MASK | SInputEvent.META_DOWN_MASK,
                seen.get(0).getModifiersEx());
    }

    // --- Location mapping ------------------------------------------

    @Test
    @DisplayName("Shift LEFT location maps to KEY_LOCATION_LEFT")
    void shiftLeftLocationMapsToKeyLocationLeft() {
        KeySurrogate s = new KeySurrogate();
        List<SKeyEvent> seen = new ArrayList<>();
        onKeyPressed(s, seen);
        ComponentUtil.fireEvent(
                s, new KeyDownEvent(s, false, "Shift", "ShiftLeft", /* location */ 1,
                        false, true, false, false, false, false));
        assertEquals(SKeyEvent.VK_SHIFT, seen.get(0).getKeyCode());
        assertEquals(SKeyEvent.KEY_LOCATION_LEFT, seen.get(0).getKeyLocation());
    }

    // --- Registration / deregistration -----------------------------

    @Test
    @DisplayName("removeKeyListener drops all three Vaadin subscriptions")
    void removeKeyListenerDropsAllThreeVaadinSubscriptions() {
        KeySurrogate s = new KeySurrogate();
        List<String> seen = new ArrayList<>();
        SKeyListener listener = new SKeyListener() {
            @Override
            public void keyTyped(SKeyEvent e) {
                seen.add("typed");
            }

            @Override
            public void keyPressed(SKeyEvent e) {
                seen.add("pressed");
            }

            @Override
            public void keyReleased(SKeyEvent e) {
                seen.add("released");
            }
        };
        s.addKeyListener(listener);
        s.removeKeyListener(listener);
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "a"));
        ComponentUtil.fireEvent(s, new KeyPressEvent(s, "a"));
        ComponentUtil.fireEvent(s, new KeyUpEvent(s, "a"));
        assertEquals(0, seen.size());
    }

    @Test
    @DisplayName("getKeyListeners preserves registration order")
    void getKeyListenersPreservesRegistrationOrder() {
        KeySurrogate s = new KeySurrogate();
        SKeyAdapter l1 = new SKeyAdapter() {
        };
        SKeyAdapter l2 = new SKeyAdapter() {
        };
        s.addKeyListener(l1);
        s.addKeyListener(l2);
        SKeyListener[] got = s.getKeyListeners();
        assertEquals(2, got.length);
        assertSame(l1, got[0]);
        assertSame(l2, got[1]);
    }

    @Test
    @DisplayName("two listeners both fire in registration order")
    void twoListenersBothFireInRegistrationOrder() {
        KeySurrogate s = new KeySurrogate();
        List<String> calls = new ArrayList<>();
        s.addKeyListener(new SKeyAdapter() {
            @Override
            public void keyPressed(SKeyEvent e) {
                calls.add("first");
            }
        });
        s.addKeyListener(new SKeyAdapter() {
            @Override
            public void keyPressed(SKeyEvent e) {
                calls.add("second");
            }
        });
        ComponentUtil.fireEvent(s, new KeyDownEvent(s, "a"));
        assertEquals(List.of("first", "second"), calls);
    }

    // --- SKeyEvent invariant ---------------------------------------

    @Test
    @DisplayName("KEY_TYPED rejects defined keyCode per AWT invariant")
    void keyTypedRejectsDefinedKeyCode() {
        assertThrows(IllegalArgumentException.class, () -> new SKeyEvent(
                new KeySurrogate(), SKeyEvent.KEY_TYPED, 0L, 0,
                SKeyEvent.VK_A, 'a', SKeyEvent.KEY_LOCATION_UNKNOWN));
    }

    @Test
    @DisplayName("KEY_TYPED rejects CHAR_UNDEFINED per AWT invariant")
    void keyTypedRejectsCharUndefined() {
        assertThrows(IllegalArgumentException.class, () -> new SKeyEvent(
                new KeySurrogate(), SKeyEvent.KEY_TYPED, 0L, 0,
                SKeyEvent.VK_UNDEFINED, SKeyEvent.CHAR_UNDEFINED,
                SKeyEvent.KEY_LOCATION_UNKNOWN));
    }

    @Test
    @DisplayName("KEY_PRESSED accepts CHAR_UNDEFINED and defined keyCode")
    void keyPressedAcceptsCharUndefinedAndDefinedKeyCode() {
        SKeyEvent e = new SKeyEvent(
                new KeySurrogate(), SKeyEvent.KEY_PRESSED, 0L,
                SInputEvent.SHIFT_DOWN_MASK,
                SKeyEvent.VK_F5, SKeyEvent.CHAR_UNDEFINED,
                SKeyEvent.KEY_LOCATION_UNKNOWN);
        assertEquals(SKeyEvent.VK_F5, e.getKeyCode());
        assertTrue(e.isShiftDown());
        assertTrue(e.isActionKey());
    }

    // --- Non-KeyNotifier peer: WARN + skip wiring ------------------

    @Test
    @DisplayName("non-KeyNotifier peer WARNs and does not wire")
    void nonKeyNotifierPeerWarnsAndDoesNotWire() {
        TestSurrogate s = new TestSurrogate();  // extends Button — not a KeyNotifier
        List<SKeyEvent> seen = new ArrayList<>();
        s.addKeyListener(new SKeyAdapter() {
            @Override
            public void keyPressed(SKeyEvent e) {
                seen.add(e);
            }
        });
        assertEquals(1, capturedWarns.size());
        assertTrue(
                capturedWarns.get(0).startsWith(
                        "Unimplemented TestSurrogate.addKeyListener/non-KeyNotifier-peer("),
                "got: " + capturedWarns.get(0));
        // No listener registered — keyListeners stays empty.
        assertEquals(0, s.getKeyListeners().length);
    }
}
