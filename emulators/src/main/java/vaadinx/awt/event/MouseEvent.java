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
 * This file is derived from OpenJDK's java.awt.event.MouseEvent
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
 * Port of {@link java.awt.event.MouseEvent}. Extends our {@link InputEvent}.
 *
 * <p>Carries both the component-relative (x,y) and the screen-absolute
 * (xAbs,yAbs) coordinates — AWT only derived absolute lazily from the peer
 * graphics device, which we don't have, so we require them at construction.
 */
public class MouseEvent extends InputEvent {

    public static final int MOUSE_FIRST = 500;
    public static final int MOUSE_LAST = 507;
    public static final int MOUSE_CLICKED = MOUSE_FIRST;
    public static final int MOUSE_PRESSED = 1 + MOUSE_FIRST;
    public static final int MOUSE_RELEASED = 2 + MOUSE_FIRST;
    public static final int MOUSE_MOVED = 3 + MOUSE_FIRST;
    public static final int MOUSE_ENTERED = 4 + MOUSE_FIRST;
    public static final int MOUSE_EXITED = 5 + MOUSE_FIRST;
    public static final int MOUSE_DRAGGED = 6 + MOUSE_FIRST;
    public static final int MOUSE_WHEEL = 7 + MOUSE_FIRST;

    public static final int NOBUTTON = 0;
    public static final int BUTTON1 = 1;
    public static final int BUTTON2 = 2;
    public static final int BUTTON3 = 3;

    private int x;
    private int y;
    private final int xAbs;
    private final int yAbs;
    private final int clickCount;
    private final boolean popupTrigger;
    private final int button;

    public MouseEvent(vaadinx.awt.Component source, int id, long when, int modifiers,
                      int x, int y, int xAbs, int yAbs, int clickCount,
                      boolean popupTrigger, int button) {
        super(source, id, when, modifiers);
        this.x = x;
        this.y = y;
        this.xAbs = xAbs;
        this.yAbs = yAbs;
        this.clickCount = clickCount;
        this.popupTrigger = popupTrigger;
        this.button = button;
    }

    public MouseEvent(vaadinx.awt.Component source, int id, long when, int modifiers,
                      int x, int y, int clickCount, boolean popupTrigger, int button) {
        // Absolute coords default to the component-local ones — the peer
        // doesn't give us screen coordinates cheaply, and callers that don't
        // care about absXY typically also don't care about the distinction.
        this(source, id, when, modifiers, x, y, x, y, clickCount, popupTrigger, button);
    }

    public MouseEvent(vaadinx.awt.Component source, int id, long when, int modifiers,
                      int x, int y, int clickCount, boolean popupTrigger) {
        this(source, id, when, modifiers, x, y, clickCount, popupTrigger, NOBUTTON);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getXOnScreen() {
        return xAbs;
    }

    public int getYOnScreen() {
        return yAbs;
    }

    public java.awt.Point getPoint() {
        return new java.awt.Point(x, y);
    }

    public java.awt.Point getLocationOnScreen() {
        return new java.awt.Point(xAbs, yAbs);
    }

    public void translatePoint(int dx, int dy) {
        this.x += dx;
        this.y += dy;
    }

    public int getClickCount() {
        return clickCount;
    }

    public boolean isPopupTrigger() {
        return popupTrigger;
    }

    public int getButton() {
        return button;
    }

    /** "Button1+Shift" etc. Delegates to AWT — constants are compatible. */
    public static String getMouseModifiersText(int modifiers) {
        return java.awt.event.MouseEvent.getMouseModifiersText(modifiers);
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case MOUSE_PRESSED -> "MOUSE_PRESSED";
            case MOUSE_RELEASED -> "MOUSE_RELEASED";
            case MOUSE_CLICKED -> "MOUSE_CLICKED";
            case MOUSE_ENTERED -> "MOUSE_ENTERED";
            case MOUSE_EXITED -> "MOUSE_EXITED";
            case MOUSE_MOVED -> "MOUSE_MOVED";
            case MOUSE_DRAGGED -> "MOUSE_DRAGGED";
            case MOUSE_WHEEL -> "MOUSE_WHEEL";
            default -> "unknown type";
        };
        return typeStr + ",(" + x + "," + y + "),absolute(" + xAbs + "," + yAbs
                + "),button=" + button + ",clickCount=" + clickCount;
    }
}
