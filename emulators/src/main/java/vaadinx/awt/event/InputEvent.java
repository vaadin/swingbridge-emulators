/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's java.awt.event.InputEvent
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt.event;

/**
 * Port of {@link java.awt.event.InputEvent}. Abstract parent of
 * {@link MouseEvent} and {@link KeyEvent}. Extends our {@link ComponentEvent}
 * — AWT does the same — so the {@code instanceof} chain from our leaf event
 * up to ComponentEvent still walks through InputEvent as users expect.
 *
 * <p>Modifier constants (the {@code *_DOWN_MASK} set) have identical values to
 * AWT's so bit arithmetic carries across. The legacy {@code *_MASK} set
 * (deprecated since JDK 9) is kept for user code that hasn't migrated yet.
 *
 * <p>Static utilities delegate to {@code java.awt.event.InputEvent} — constants
 * are compatible, so there's no point re-deriving the logic.
 *
 * <h2>Consuming an event marks it; it does not suppress anything</h2>
 *
 * {@link #consume()} / {@link #isConsumed()} are ported faithfully and the flag
 * round-trips across listeners exactly as in Swing — the "listener A consumes,
 * listener B checks" idiom works unchanged. What consuming <b>cannot</b> do here
 * is cancel the platform's default action: in Swing, consuming a
 * {@code KEY_PRESSED} in a text field stops the character being inserted,
 * whereas the browser has already inserted it by the time the keystroke reaches
 * the server. Nothing in this emulator runs <i>after</i> listener notification
 * for a consume to gate — see {@code Component.processKeyEvent} — so the flag is
 * read-back state, not a veto. R_match_swing_errors sub-bucket (c), documented rather than WARNed:
 * the storage half is exactly faithful, so an {@code onUnimplemented} on every
 * {@code consume()} call would be both wrong and deafening. See D_event_consume.
 */
public abstract class InputEvent extends ComponentEvent {

    // Legacy modifier flags (deprecated in JDK — kept for user code compat).
    @Deprecated public static final int SHIFT_MASK = 1;
    @Deprecated public static final int CTRL_MASK = 1 << 1;
    @Deprecated public static final int META_MASK = 1 << 2;
    @Deprecated public static final int ALT_MASK = 1 << 3;
    @Deprecated public static final int ALT_GRAPH_MASK = 1 << 5;
    @Deprecated public static final int BUTTON1_MASK = 1 << 4;
    @Deprecated public static final int BUTTON2_MASK = ALT_MASK;
    @Deprecated public static final int BUTTON3_MASK = META_MASK;

    // Modern extended modifier flags — these are what user code should use.
    public static final int SHIFT_DOWN_MASK = 1 << 6;
    public static final int CTRL_DOWN_MASK = 1 << 7;
    public static final int META_DOWN_MASK = 1 << 8;
    public static final int ALT_DOWN_MASK = 1 << 9;
    public static final int BUTTON1_DOWN_MASK = 1 << 10;
    public static final int BUTTON2_DOWN_MASK = 1 << 11;
    public static final int BUTTON3_DOWN_MASK = 1 << 12;
    public static final int ALT_GRAPH_DOWN_MASK = 1 << 13;

    private final long when;
    private final int modifiers;

    protected InputEvent(vaadinx.awt.Component source, int id, long when, int modifiers) {
        super(source, id);
        this.when = when;
        this.modifiers = modifiers;
    }

    public long getWhen() {
        return when;
    }

    /**
     * Legacy modifiers (deprecated). Real AWT stored two fields; we store the
     * extended set and return it unchanged — user code checking specific bits
     * should use {@link #getModifiersEx()} with the {@code *_DOWN_MASK}
     * constants anyway.
     */
    @Deprecated
    public int getModifiers() {
        return modifiers;
    }

    public int getModifiersEx() {
        return modifiers;
    }

    public boolean isShiftDown() {
        return (modifiers & SHIFT_DOWN_MASK) != 0;
    }

    public boolean isControlDown() {
        return (modifiers & CTRL_DOWN_MASK) != 0;
    }

    public boolean isMetaDown() {
        return (modifiers & META_DOWN_MASK) != 0;
    }

    public boolean isAltDown() {
        return (modifiers & ALT_DOWN_MASK) != 0;
    }

    public boolean isAltGraphDown() {
        return (modifiers & ALT_GRAPH_DOWN_MASK) != 0;
    }

    /**
     * Marks this event consumed. Public here, as in the JDK — {@code AWTEvent}
     * declares it {@code protected} and conditional on the event id, and
     * {@code InputEvent} widens it and drops the condition.
     *
     * <p>Sets the flag and nothing more; see the class javadoc for what that
     * does and does not buy you.
     */
    @Override
    public void consume() {
        consumed = true;
    }

    /** @return whether {@link #consume()} has been called on this event */
    @Override
    public boolean isConsumed() {
        return consumed;
    }

    /**
     * Bit mask for a 1-indexed button (1 → BUTTON1_DOWN_MASK, …). Delegates
     * to AWT — the constants match, and any bounds handling stays centralised.
     */
    public static int getMaskForButton(int button) {
        return java.awt.event.InputEvent.getMaskForButton(button);
    }

    /**
     * Localised "Shift+Ctrl+Button1" string for a modifiersEx value. Delegates
     * to AWT — constants match and the formatting is locale-aware.
     */
    public static String getModifiersExText(int modifiers) {
        return java.awt.event.InputEvent.getModifiersExText(modifiers);
    }
}
