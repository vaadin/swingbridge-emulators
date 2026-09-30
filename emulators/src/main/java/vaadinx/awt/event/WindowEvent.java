/*
 * Copyright (c) 1996, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.event.WindowEvent
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
 * Port of {@link java.awt.event.WindowEvent}. Source is always a
 * {@link vaadinx.awt.Window} — our WindowListener et al. take this event.
 */
public class WindowEvent extends ComponentEvent {

    public static final int WINDOW_FIRST = 200;
    public static final int WINDOW_LAST = 209;
    public static final int WINDOW_OPENED = WINDOW_FIRST;
    public static final int WINDOW_CLOSING = 1 + WINDOW_FIRST;
    public static final int WINDOW_CLOSED = 2 + WINDOW_FIRST;
    public static final int WINDOW_ICONIFIED = 3 + WINDOW_FIRST;
    public static final int WINDOW_DEICONIFIED = 4 + WINDOW_FIRST;
    public static final int WINDOW_ACTIVATED = 5 + WINDOW_FIRST;
    public static final int WINDOW_DEACTIVATED = 6 + WINDOW_FIRST;
    public static final int WINDOW_GAINED_FOCUS = 7 + WINDOW_FIRST;
    public static final int WINDOW_LOST_FOCUS = 8 + WINDOW_FIRST;
    public static final int WINDOW_STATE_CHANGED = 9 + WINDOW_FIRST;

    private final vaadinx.awt.Window opposite;
    private final int oldState;
    private final int newState;

    public WindowEvent(vaadinx.awt.Window source, int id, vaadinx.awt.Window opposite,
                       int oldState, int newState) {
        super(source, id);
        this.opposite = opposite;
        this.oldState = oldState;
        this.newState = newState;
    }

    public WindowEvent(vaadinx.awt.Window source, int id, vaadinx.awt.Window opposite) {
        this(source, id, opposite, 0, 0);
    }

    public WindowEvent(vaadinx.awt.Window source, int id, int oldState, int newState) {
        this(source, id, null, oldState, newState);
    }

    public WindowEvent(vaadinx.awt.Window source, int id) {
        this(source, id, null, 0, 0);
    }

    public vaadinx.awt.Window getWindow() {
        return (vaadinx.awt.Window) getSource();
    }

    public vaadinx.awt.Window getOppositeWindow() {
        return opposite;
    }

    public int getOldState() {
        return oldState;
    }

    public int getNewState() {
        return newState;
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case WINDOW_OPENED -> "WINDOW_OPENED";
            case WINDOW_CLOSING -> "WINDOW_CLOSING";
            case WINDOW_CLOSED -> "WINDOW_CLOSED";
            case WINDOW_ICONIFIED -> "WINDOW_ICONIFIED";
            case WINDOW_DEICONIFIED -> "WINDOW_DEICONIFIED";
            case WINDOW_ACTIVATED -> "WINDOW_ACTIVATED";
            case WINDOW_DEACTIVATED -> "WINDOW_DEACTIVATED";
            case WINDOW_GAINED_FOCUS -> "WINDOW_GAINED_FOCUS";
            case WINDOW_LOST_FOCUS -> "WINDOW_LOST_FOCUS";
            case WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED";
            default -> "unknown type";
        };
        return typeStr;
    }
}
