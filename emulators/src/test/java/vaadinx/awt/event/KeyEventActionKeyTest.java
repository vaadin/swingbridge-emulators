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

package vaadinx.awt.event;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.Component;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KeyEvent#isActionKey()} answers exactly what {@link java.awt.event.KeyEvent} answers.
 *
 * <p>Two arms, because each catches what the other cannot. The sweep is the drift guard: it
 * reddens if anyone replaces the delegation with a hand-maintained list, which is the only
 * plausible edit here and the one that looks harmless. The spot-checks are the behavioural
 * pin, and they hold whatever the implementation does — they name keys no browser can send,
 * which is exactly why a list went unnoticed being wrong about them.
 */
class KeyEventActionKeyTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    private KeyEvent pressed(int keyCode) {
        return new KeyEvent(component(), KeyEvent.KEY_PRESSED, 0L, 0, keyCode,
                KeyEvent.CHAR_UNDEFINED);
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
        assertTrue(pressed(KeyEvent.VK_KANA).isActionKey(), "VK_KANA");
        assertTrue(pressed(KeyEvent.VK_KANJI).isActionKey(), "VK_KANJI");
        assertTrue(pressed(KeyEvent.VK_COPY).isActionKey(), "VK_COPY");
        assertTrue(pressed(KeyEvent.VK_UNDO).isActionKey(), "VK_UNDO");
        assertTrue(pressed(KeyEvent.VK_BEGIN).isActionKey(), "VK_BEGIN");
    }

    @Test
    @DisplayName("character keys are not action keys, navigation and function keys are")
    void characterKeysAreNotActionKeysButNavigationKeysAre() {
        assertFalse(pressed(KeyEvent.VK_A).isActionKey(), "VK_A");
        assertFalse(pressed(KeyEvent.VK_SPACE).isActionKey(), "VK_SPACE");
        assertFalse(pressed(KeyEvent.VK_ENTER).isActionKey(), "VK_ENTER");
        assertTrue(pressed(KeyEvent.VK_F5).isActionKey(), "VK_F5");
        assertTrue(pressed(KeyEvent.VK_LEFT).isActionKey(), "VK_LEFT");
    }
}
