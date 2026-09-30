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

import com.vaadin.flow.component.html.Div;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SKeyEvent#isActionKey()} answers exactly what {@link java.awt.event.KeyEvent} answers.
 *
 * <p>Two arms, because each catches what the other cannot. The sweep is the drift guard: it
 * reddens if anyone replaces the delegation with a hand-maintained list, which is the only
 * plausible edit here and the one that looks harmless. The spot-checks are the behavioural
 * pin, and they hold whatever the implementation does — they name keys no browser can send,
 * which is exactly why a list went unnoticed being wrong about them.
 *
 * <p>Mirrors {@code vaadinx.awt.event.KeyEventActionKeyTest}; the two layers must agree with
 * AWT and therefore with each other.
 */
class SKeyEventActionKeyTest {

    private static SKeyEvent pressed(int keyCode) {
        return new SKeyEvent(new Div(), SKeyEvent.KEY_PRESSED, 0L, 0, keyCode,
                SKeyEvent.CHAR_UNDEFINED, SKeyEvent.KEY_LOCATION_UNKNOWN);
    }

    private static boolean awtSays(int keyCode) {
        @SuppressWarnings("serial")
        java.awt.Component src = new java.awt.Component() {
        };
        return new java.awt.event.KeyEvent(src, java.awt.event.KeyEvent.KEY_PRESSED, 0L, 0,
                keyCode, java.awt.event.KeyEvent.CHAR_UNDEFINED,
                java.awt.event.KeyEvent.KEY_LOCATION_UNKNOWN).isActionKey();
    }

    @Test
    @DisplayName("agrees with java.awt.event.KeyEvent across the whole keycode space")
    void agreesWithAwtAcrossTheKeycodeSpace() {
        List<String> disagreements = new ArrayList<>();
        for (int kc = 0; kc <= 0xFFFF; kc++) {
            boolean ours = pressed(kc).isActionKey();
            if (ours != awtSays(kc)) {
                disagreements.add(String.format("0x%X (%s): we say %s",
                        kc, java.awt.event.KeyEvent.getKeyText(kc), ours));
            }
        }
        assertEquals(List.of(), disagreements,
                "isActionKey() must answer what AWT answers — our VK_* values are AWT's, so"
                        + " AWT's answer is correct by definition");
    }

    @Test
    @DisplayName("the input-method and Sun editing keys are action keys")
    void inputMethodAndSunEditingKeysAreActionKeys() {
        assertTrue(pressed(SKeyEvent.VK_KANA).isActionKey(), "VK_KANA");
        assertTrue(pressed(SKeyEvent.VK_KANJI).isActionKey(), "VK_KANJI");
        assertTrue(pressed(SKeyEvent.VK_COPY).isActionKey(), "VK_COPY");
        assertTrue(pressed(SKeyEvent.VK_UNDO).isActionKey(), "VK_UNDO");
        assertTrue(pressed(SKeyEvent.VK_BEGIN).isActionKey(), "VK_BEGIN");
    }

    @Test
    @DisplayName("character keys are not action keys, navigation and function keys are")
    void characterKeysAreNotActionKeysButNavigationKeysAre() {
        assertFalse(pressed(SKeyEvent.VK_A).isActionKey(), "VK_A");
        assertFalse(pressed(SKeyEvent.VK_SPACE).isActionKey(), "VK_SPACE");
        assertFalse(pressed(SKeyEvent.VK_ENTER).isActionKey(), "VK_ENTER");
        assertTrue(pressed(SKeyEvent.VK_F5).isActionKey(), "VK_F5");
        assertTrue(pressed(SKeyEvent.VK_LEFT).isActionKey(), "VK_LEFT");
    }
}
