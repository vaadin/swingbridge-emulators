/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.event.MouseWheelEvent
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
 * Port of {@link java.awt.event.MouseWheelEvent}. Extends our
 * {@link MouseEvent} — carries scroll type, scroll amount, integer rotation,
 * and high-precision rotation (smooth-scrolling wheels).
 */
public class MouseWheelEvent extends MouseEvent {

    public static final int WHEEL_UNIT_SCROLL = 0;
    public static final int WHEEL_BLOCK_SCROLL = 1;

    private final int scrollType;
    private final int scrollAmount;
    private final int wheelRotation;
    private final double preciseWheelRotation;

    public MouseWheelEvent(vaadinx.awt.Component source, int id, long when, int modifiers,
                           int x, int y, int clickCount, boolean popupTrigger,
                           int scrollType, int scrollAmount, int wheelRotation) {
        this(source, id, when, modifiers, x, y, 0, 0, clickCount, popupTrigger,
             scrollType, scrollAmount, wheelRotation, wheelRotation);
    }

    public MouseWheelEvent(vaadinx.awt.Component source, int id, long when, int modifiers,
                           int x, int y, int xAbs, int yAbs, int clickCount,
                           boolean popupTrigger, int scrollType, int scrollAmount,
                           int wheelRotation) {
        this(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger,
             scrollType, scrollAmount, wheelRotation, wheelRotation);
    }

    public MouseWheelEvent(vaadinx.awt.Component source, int id, long when, int modifiers,
                           int x, int y, int xAbs, int yAbs, int clickCount,
                           boolean popupTrigger, int scrollType, int scrollAmount,
                           int wheelRotation, double preciseWheelRotation) {
        super(source, id, when, modifiers, x, y, xAbs, yAbs, clickCount, popupTrigger, MouseEvent.NOBUTTON);
        this.scrollType = scrollType;
        this.scrollAmount = scrollAmount;
        this.wheelRotation = wheelRotation;
        this.preciseWheelRotation = preciseWheelRotation;
    }

    public int getScrollType() {
        return scrollType;
    }

    public int getScrollAmount() {
        return scrollAmount;
    }

    public int getWheelRotation() {
        return wheelRotation;
    }

    public double getPreciseWheelRotation() {
        return preciseWheelRotation;
    }

    /**
     * How far each click of the wheel should scroll if the current scroll
     * type is WHEEL_UNIT_SCROLL. AWT returns a computed value from the
     * underlying component's scroll unit; we return the raw scrollAmount *
     * rotation so callers get the same "total units" semantics.
     */
    public int getUnitsToScroll() {
        return scrollAmount * wheelRotation;
    }

    @Override
    public String paramString() {
        String scrollTypeStr = switch (scrollType) {
            case WHEEL_UNIT_SCROLL -> "WHEEL_UNIT_SCROLL";
            case WHEEL_BLOCK_SCROLL -> "WHEEL_BLOCK_SCROLL";
            default -> "unknown scroll type";
        };
        return super.paramString() + "," + scrollTypeStr
                + ",scrollAmount=" + scrollAmount
                + ",wheelRotation=" + wheelRotation
                + ",preciseWheelRotation=" + preciseWheelRotation;
    }
}
