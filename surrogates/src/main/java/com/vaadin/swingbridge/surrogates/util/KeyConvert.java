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

package com.vaadin.swingbridge.surrogates.util;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyLocation;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.swingbridge.surrogates.awt.event.SInputEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyEvent;

import java.util.List;
import java.util.Set;

/**
 * Translation between Vaadin's {@code Key} / {@code KeyModifier} /
 * {@code KeyLocation} world and AWT's {@code VK_*} keycodes, modifier masks,
 * and key-location constants. Two directions with distinct purposes:
 *
 * <ul>
 *   <li><b>Inbound (Vaadin → AWT)</b> — {@link #vaadinKeyToVK}, {@link #keyCharFrom},
 *       {@link #keyModifiersToMask}, {@link #keyLocationToAwt}. Used by
 *       {@code ComponentMixin.addKeyListener} to bridge Vaadin's
 *       {@code KeyDown}/{@code Press}/{@code Up} events into {@link SKeyEvent}.
 *       Matches against {@code event.key} (letters as {@code "a"}/{@code "A"},
 *       named keys as {@code "Enter"}/{@code "ArrowLeft"}) — the shift-resolved
 *       identifier {@code KeyboardEvent.getKey()} surfaces. This direction is
 *       surrogate-only; the emulator has no inbound counterpart.</li>
 *   <li><b>Outbound (AWT → Vaadin)</b> — {@link #vkToVaadinKey},
 *       {@link #toVaadinKeyBinding}, {@link VaadinKeyBinding}. Used for
 *       accelerator/mnemonic install ({@code registerKeyboardAction}, menu
 *       accelerators, button mnemonics). Produces {@code event.code}-shaped
 *       {@link Key}s ({@code "KeyA"}/{@code "Digit5"}) because Vaadin
 *       {@code Shortcuts} match physical keys. <b>Shared by both modules</b> —
 *       {@code :emulators} calls straight through here for its own
 *       {@code registerKeyboardAction} — a single shared copy in the
 *       SD_shelper_statics-allowed direction ({@code :emulators → :surrogates}), not a
 *       per-module twin.</li>
 * </ul>
 *
 * <p>See SD_shelper_statics (module direction) and SD_key_events (the intentional {@code event.code}/
 * {@code event.key} split on the inbound side, which is <em>not</em>
 * deduplicated because the emulator has no inbound table).
 */
public final class KeyConvert {

    private KeyConvert() {}

    // --- Inbound: Vaadin Key (event.key) → AWT VK_* / char / mask / location ---

    /**
     * Vaadin {@link Key} (as received on {@link com.vaadin.flow.component.internal.KeyboardEvent#getKey()})
     * → AWT {@code VK_*} keycode. Returns {@link SKeyEvent#VK_UNDEFINED} when
     * the incoming key doesn't map to any known {@code VK_*}; per Swing's
     * convention that's the right signal for "unknown key", not a failure.
     *
     * <p>Matches against {@code key.getKeys().get(0)} — the first DOM-level
     * identifier carried by the {@link Key}. For incoming events Vaadin
     * populates this from the browser's {@code event.key}, so letters come
     * through as {@code "a"}/{@code "A"} (the shift state is reflected in
     * the modifier mask, not the keycode — VK_A is the same either way).
     */
    public static int vaadinKeyToVK(Key key) {
        if (key == null) return SKeyEvent.VK_UNDEFINED;
        List<String> keys = key.getKeys();
        if (keys == null || keys.isEmpty()) return SKeyEvent.VK_UNDEFINED;
        String k = keys.get(0);
        if (k == null || k.isEmpty()) return SKeyEvent.VK_UNDEFINED;

        // Single-character ASCII: letters/digits are the hot path; other
        // printable chars fall into the named-symbol switch below.
        if (k.length() == 1) {
            char c = k.charAt(0);
            char upper = Character.toUpperCase(c);
            if (upper >= 'A' && upper <= 'Z') return upper;  // VK_A..VK_Z == 'A'..'Z'
            if (c >= '0' && c <= '9')         return c;      // VK_0..VK_9 == '0'..'9'
            switch (c) {
                case ' ': return SKeyEvent.VK_SPACE;
                case ',': return SKeyEvent.VK_COMMA;
                case '-': return SKeyEvent.VK_MINUS;
                case '.': return SKeyEvent.VK_PERIOD;
                case '/': return SKeyEvent.VK_SLASH;
                case ';': return SKeyEvent.VK_SEMICOLON;
                case '=': return SKeyEvent.VK_EQUALS;
                case '[': return SKeyEvent.VK_OPEN_BRACKET;
                case ']': return SKeyEvent.VK_CLOSE_BRACKET;
                case '\\': return SKeyEvent.VK_BACK_SLASH;
                case '`': return SKeyEvent.VK_BACK_QUOTE;
                case '\'': return SKeyEvent.VK_QUOTE;
                default:
                    // Unknown printable — ask AWT for its best guess.
                    int ec = java.awt.event.KeyEvent.getExtendedKeyCodeForChar(c);
                    return ec == 0 ? SKeyEvent.VK_UNDEFINED : ec;
            }
        }

        // Multi-char named keys. Vaadin uses DOM event.key values, which
        // are stable cross-browser (see W3C UI Events key-values list).
        return switch (k) {
            case "Enter"       -> SKeyEvent.VK_ENTER;
            case "Escape"      -> SKeyEvent.VK_ESCAPE;
            case "Backspace"   -> SKeyEvent.VK_BACK_SPACE;
            case "Tab"         -> SKeyEvent.VK_TAB;
            case "Delete"      -> SKeyEvent.VK_DELETE;
            case "ArrowLeft"   -> SKeyEvent.VK_LEFT;
            case "ArrowRight"  -> SKeyEvent.VK_RIGHT;
            case "ArrowUp"     -> SKeyEvent.VK_UP;
            case "ArrowDown"   -> SKeyEvent.VK_DOWN;
            case "Home"        -> SKeyEvent.VK_HOME;
            case "End"         -> SKeyEvent.VK_END;
            case "PageUp"      -> SKeyEvent.VK_PAGE_UP;
            case "PageDown"    -> SKeyEvent.VK_PAGE_DOWN;
            case "Insert"      -> SKeyEvent.VK_INSERT;
            case "ContextMenu" -> SKeyEvent.VK_CONTEXT_MENU;
            case "Pause"       -> SKeyEvent.VK_PAUSE;
            case "Help"        -> SKeyEvent.VK_HELP;
            case "CapsLock"    -> SKeyEvent.VK_CAPS_LOCK;
            case "NumLock"     -> SKeyEvent.VK_NUM_LOCK;
            case "ScrollLock"  -> SKeyEvent.VK_SCROLL_LOCK;
            case "PrintScreen" -> SKeyEvent.VK_PRINTSCREEN;
            case "Shift"       -> SKeyEvent.VK_SHIFT;
            case "Control"     -> SKeyEvent.VK_CONTROL;
            case "Alt"         -> SKeyEvent.VK_ALT;
            case "AltGraph"    -> SKeyEvent.VK_ALT_GRAPH;
            case "Meta", "OS"  -> SKeyEvent.VK_META;
            case "F1"  -> SKeyEvent.VK_F1;
            case "F2"  -> SKeyEvent.VK_F2;
            case "F3"  -> SKeyEvent.VK_F3;
            case "F4"  -> SKeyEvent.VK_F4;
            case "F5"  -> SKeyEvent.VK_F5;
            case "F6"  -> SKeyEvent.VK_F6;
            case "F7"  -> SKeyEvent.VK_F7;
            case "F8"  -> SKeyEvent.VK_F8;
            case "F9"  -> SKeyEvent.VK_F9;
            case "F10" -> SKeyEvent.VK_F10;
            case "F11" -> SKeyEvent.VK_F11;
            case "F12" -> SKeyEvent.VK_F12;
            case "F13" -> SKeyEvent.VK_F13;
            case "F14" -> SKeyEvent.VK_F14;
            case "F15" -> SKeyEvent.VK_F15;
            case "F16" -> SKeyEvent.VK_F16;
            case "F17" -> SKeyEvent.VK_F17;
            case "F18" -> SKeyEvent.VK_F18;
            case "F19" -> SKeyEvent.VK_F19;
            case "F20" -> SKeyEvent.VK_F20;
            case "F21" -> SKeyEvent.VK_F21;
            case "F22" -> SKeyEvent.VK_F22;
            case "F23" -> SKeyEvent.VK_F23;
            case "F24" -> SKeyEvent.VK_F24;
            default    -> SKeyEvent.VK_UNDEFINED;
        };
    }

    /**
     * Return the effective {@code keyChar} for a Vaadin {@link Key}: the
     * first character when the key's primary identifier is single-char
     * (letters, digits, punctuation), or {@link SKeyEvent#CHAR_UNDEFINED}
     * for named keys (Enter, Shift, F1, …). Browser {@code keypress} events
     * only fire for character-producing keys, so KEY_TYPED dispatches will
     * always see a defined char here; KEY_PRESSED / KEY_RELEASED see
     * CHAR_UNDEFINED for action keys per AWT's convention.
     */
    public static char keyCharFrom(Key key) {
        if (key == null) return SKeyEvent.CHAR_UNDEFINED;
        List<String> keys = key.getKeys();
        if (keys == null || keys.isEmpty()) return SKeyEvent.CHAR_UNDEFINED;
        String k = keys.get(0);
        if (k == null || k.length() != 1) return SKeyEvent.CHAR_UNDEFINED;
        return k.charAt(0);
    }

    /**
     * Vaadin {@link KeyModifier} set → AWT {@code *_DOWN_MASK} bitmask. Covers
     * the five modifiers the browser exposes — Shift / Control / Alt /
     * AltGraph / Meta. Any unknown modifier in the incoming set is ignored
     * silently (Vaadin's enum is closed, so there's nothing to warn about).
     */
    public static int keyModifiersToMask(Set<KeyModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return 0;
        int mask = 0;
        if (modifiers.contains(KeyModifier.SHIFT))     mask |= SInputEvent.SHIFT_DOWN_MASK;
        if (modifiers.contains(KeyModifier.CONTROL))   mask |= SInputEvent.CTRL_DOWN_MASK;
        if (modifiers.contains(KeyModifier.ALT))       mask |= SInputEvent.ALT_DOWN_MASK;
        if (modifiers.contains(KeyModifier.ALT_GRAPH)) mask |= SInputEvent.ALT_GRAPH_DOWN_MASK;
        if (modifiers.contains(KeyModifier.META))      mask |= SInputEvent.META_DOWN_MASK;
        return mask;
    }

    /**
     * Vaadin {@link KeyLocation} → AWT {@code KEY_LOCATION_*}. The two enums
     * line up one-for-one on meaning but use different integer values —
     * Vaadin's STANDARD=0 aligns with AWT's KEY_LOCATION_STANDARD=1, etc.
     * A {@code null} input falls back to {@code KEY_LOCATION_UNKNOWN} so
     * call sites constructing synthetic {@link SKeyEvent}s without location
     * info still get a sensible default.
     */
    public static int keyLocationToAwt(KeyLocation location) {
        if (location == null) return SKeyEvent.KEY_LOCATION_UNKNOWN;
        return switch (location) {
            case STANDARD -> SKeyEvent.KEY_LOCATION_STANDARD;
            case LEFT     -> SKeyEvent.KEY_LOCATION_LEFT;
            case RIGHT    -> SKeyEvent.KEY_LOCATION_RIGHT;
            case NUMPAD   -> SKeyEvent.KEY_LOCATION_NUMPAD;
        };
    }

    // --- Outbound: AWT VK_* / KeyStroke → Vaadin Key (event.code) -------
    //     Shared with :emulators (registerKeyboardAction). Vaadin Shortcuts
    //     match physical keys, hence event.code-shaped "KeyA"/"Digit5".

    /**
     * Inverse of {@link #vaadinKeyToVK(Key)}: map an AWT {@code VK_*} code to
     * a Vaadin {@link Key}. Returns {@code null} when no stable mapping
     * exists — callers treat null as "can't install this binding" rather
     * than synthesising a bogus Key. Covers the letter / digit ranges
     * (VK_A..VK_Z → {@code "KeyA".."KeyZ"}, VK_0..VK_9 → {@code "Digit0".."Digit9"}
     * — the codes browsers emit on {@code event.code}) plus the named-key
     * switch used for accelerator install (Enter, Escape, arrows, F-keys,
     * …). Intended for outbound wiring (mnemonic install, registerKeyboardAction);
     * the inverse direction {@link #vaadinKeyToVK} matches against
     * {@code event.key} instead because that's what Vaadin's inbound
     * KeyNotifier surfaces.
     */
    public static Key vkToVaadinKey(int vk) {
        return switch (vk) {
            case java.awt.event.KeyEvent.VK_ENTER      -> Key.ENTER;
            case java.awt.event.KeyEvent.VK_ESCAPE     -> Key.ESCAPE;
            case java.awt.event.KeyEvent.VK_SPACE      -> Key.SPACE;
            case java.awt.event.KeyEvent.VK_BACK_SPACE -> Key.BACKSPACE;
            case java.awt.event.KeyEvent.VK_TAB        -> Key.TAB;
            case java.awt.event.KeyEvent.VK_DELETE     -> Key.DELETE;
            case java.awt.event.KeyEvent.VK_LEFT       -> Key.ARROW_LEFT;
            case java.awt.event.KeyEvent.VK_RIGHT      -> Key.ARROW_RIGHT;
            case java.awt.event.KeyEvent.VK_UP         -> Key.ARROW_UP;
            case java.awt.event.KeyEvent.VK_DOWN       -> Key.ARROW_DOWN;
            case java.awt.event.KeyEvent.VK_HOME       -> Key.HOME;
            case java.awt.event.KeyEvent.VK_END        -> Key.END;
            case java.awt.event.KeyEvent.VK_PAGE_UP    -> Key.PAGE_UP;
            case java.awt.event.KeyEvent.VK_PAGE_DOWN  -> Key.PAGE_DOWN;
            case java.awt.event.KeyEvent.VK_F1         -> Key.F1;
            case java.awt.event.KeyEvent.VK_F2         -> Key.F2;
            case java.awt.event.KeyEvent.VK_F3         -> Key.F3;
            case java.awt.event.KeyEvent.VK_F4         -> Key.F4;
            case java.awt.event.KeyEvent.VK_F5         -> Key.F5;
            case java.awt.event.KeyEvent.VK_F6         -> Key.F6;
            case java.awt.event.KeyEvent.VK_F7         -> Key.F7;
            case java.awt.event.KeyEvent.VK_F8         -> Key.F8;
            case java.awt.event.KeyEvent.VK_F9         -> Key.F9;
            case java.awt.event.KeyEvent.VK_F10        -> Key.F10;
            case java.awt.event.KeyEvent.VK_F11        -> Key.F11;
            case java.awt.event.KeyEvent.VK_F12        -> Key.F12;
            default -> {
                if (vk >= java.awt.event.KeyEvent.VK_A && vk <= java.awt.event.KeyEvent.VK_Z) {
                    yield Key.of("Key" + (char) ('A' + (vk - java.awt.event.KeyEvent.VK_A)));
                }
                if (vk >= java.awt.event.KeyEvent.VK_0 && vk <= java.awt.event.KeyEvent.VK_9) {
                    yield Key.of("Digit" + (char) ('0' + (vk - java.awt.event.KeyEvent.VK_0)));
                }
                yield null;
            }
        };
    }

    /**
     * Pair of Vaadin {@link Key} + modifier set resolved from a
     * {@link javax.swing.KeyStroke}. {@link #key} is non-null when the
     * keystroke's keyCode maps onto a DOM key our table covers; null
     * callers should skip shortcut install and log so migration logs
     * surface the gap. Shared by both modules — {@code :emulators}
     * consumes it directly for {@code registerKeyboardAction}.
     */
    public record VaadinKeyBinding(Key key, KeyModifier[] modifiers) {}

    /**
     * Translate {@link javax.swing.KeyStroke} → Vaadin
     * {@link com.vaadin.flow.component.Shortcuts}-shaped pair. Returns
     * {@code null} when the keystroke's keyCode isn't covered by
     * {@link #vkToVaadinKey(int)} — caller should
     * {@code onUnimplemented} and skip browser-side install. Both
     * modern (*_DOWN_MASK) and legacy ({@code SHIFT_MASK}, …) modifier
     * bits accepted; AWT's KeyStroke factories produced either over the
     * years and migrated code may mix them.
     */
    @SuppressWarnings("deprecation") // legacy *_MASK acceptance is deliberate, see javadoc
    public static VaadinKeyBinding toVaadinKeyBinding(javax.swing.KeyStroke stroke) {
        if (stroke == null) return null;
        Key key = vkToVaadinKey(stroke.getKeyCode());
        if (key == null) return null;
        java.util.EnumSet<KeyModifier> mods = java.util.EnumSet.noneOf(KeyModifier.class);
        int mask = stroke.getModifiers();
        if ((mask & (java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_MASK)) != 0) {
            mods.add(KeyModifier.SHIFT);
        }
        if ((mask & (java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.CTRL_MASK)) != 0) {
            mods.add(KeyModifier.CONTROL);
        }
        if ((mask & (java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.ALT_MASK)) != 0) {
            mods.add(KeyModifier.ALT);
        }
        if ((mask & (java.awt.event.InputEvent.META_DOWN_MASK | java.awt.event.InputEvent.META_MASK)) != 0) {
            mods.add(KeyModifier.META);
        }
        return new VaadinKeyBinding(key, mods.toArray(new KeyModifier[0]));
    }
}
