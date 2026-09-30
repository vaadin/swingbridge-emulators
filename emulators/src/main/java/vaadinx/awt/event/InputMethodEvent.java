/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.event.InputMethodEvent
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
 * Port of {@link java.awt.event.InputMethodEvent}. Niche — used by IME
 * text-composition pipelines. Ported for completeness since
 * {@link vaadinx.awt.Component} exposes input-method listener methods and
 * the type surface needs a consistent root.
 *
 * <p>Sub-objects ({@link java.text.AttributedCharacterIterator},
 * {@link java.awt.font.TextHitInfo}) have no Component references, so we
 * reuse them unchanged from the JDK per D_whitelist_porting.
 *
 * <p>{@link #consume()} / {@link #isConsumed()} are public here as in the JDK,
 * with the same caveat as {@link InputEvent}: the flag round-trips but vetoes
 * nothing, since the browser has already acted. See D_event_consume.
 */
public class InputMethodEvent extends java.awt.AWTEvent {

    public static final int INPUT_METHOD_FIRST = 1100;
    public static final int INPUT_METHOD_LAST = 1101;
    public static final int INPUT_METHOD_TEXT_CHANGED = INPUT_METHOD_FIRST;
    public static final int CARET_POSITION_CHANGED = 1 + INPUT_METHOD_FIRST;

    private final long when;
    private final java.text.AttributedCharacterIterator text;
    private final int committedCharacterCount;
    private final java.awt.font.TextHitInfo caret;
    private final java.awt.font.TextHitInfo visiblePosition;

    public InputMethodEvent(vaadinx.awt.Component source, int id, long when,
                            java.text.AttributedCharacterIterator text,
                            int committedCharacterCount,
                            java.awt.font.TextHitInfo caret,
                            java.awt.font.TextHitInfo visiblePosition) {
        super(source, id);
        this.when = when;
        this.text = text;
        this.committedCharacterCount = committedCharacterCount;
        this.caret = caret;
        this.visiblePosition = visiblePosition;
    }

    public InputMethodEvent(vaadinx.awt.Component source, int id,
                            java.text.AttributedCharacterIterator text,
                            int committedCharacterCount,
                            java.awt.font.TextHitInfo caret,
                            java.awt.font.TextHitInfo visiblePosition) {
        this(source, id, java.awt.EventQueue.getMostRecentEventTime(), text,
             committedCharacterCount, caret, visiblePosition);
    }

    public InputMethodEvent(vaadinx.awt.Component source, int id,
                            java.awt.font.TextHitInfo caret,
                            java.awt.font.TextHitInfo visiblePosition) {
        this(source, id, 0L, null, 0, caret, visiblePosition);
    }

    public long getWhen() {
        return when;
    }

    public java.text.AttributedCharacterIterator getText() {
        return text;
    }

    public int getCommittedCharacterCount() {
        return committedCharacterCount;
    }

    public java.awt.font.TextHitInfo getCaret() {
        return caret;
    }

    public java.awt.font.TextHitInfo getVisiblePosition() {
        return visiblePosition;
    }

    /**
     * Marks this event consumed. Public here, as in the JDK — {@code AWTEvent}
     * declares it {@code protected}; {@code InputMethodEvent} widens it and
     * drops the id condition.
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

    @Override
    public String paramString() {
        return switch (id) {
            case INPUT_METHOD_TEXT_CHANGED -> "INPUT_METHOD_TEXT_CHANGED";
            case CARET_POSITION_CHANGED -> "CARET_POSITION_CHANGED";
            default -> "unknown type";
        };
    }
}
