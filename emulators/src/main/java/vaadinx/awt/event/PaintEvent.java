/*
 * Copyright (c) 1996, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.event.PaintEvent
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
 * Port of {@link java.awt.event.PaintEvent}. Relevant for a possible
 * canvas-painting future: a remote canvas component can receive PaintEvents,
 * slow-rate-fire them to Vaadin, and user overrides of {@code paint()} still
 * fire through our event dispatch.
 *
 * <p>AWT calls this "semi-public" — users don't typically subscribe to it,
 * but it's part of the event hierarchy so we port it for completeness.
 */
public class PaintEvent extends ComponentEvent {

    public static final int PAINT_FIRST = 800;
    public static final int PAINT_LAST = 801;
    public static final int PAINT = PAINT_FIRST;
    public static final int UPDATE = PAINT_FIRST + 1;

    private java.awt.Rectangle updateRect;

    public PaintEvent(vaadinx.awt.Component source, int id, java.awt.Rectangle updateRect) {
        super(source, id);
        this.updateRect = updateRect;
    }

    public java.awt.Rectangle getUpdateRect() {
        return updateRect;
    }

    public void setUpdateRect(java.awt.Rectangle updateRect) {
        this.updateRect = updateRect;
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case PAINT -> "PAINT";
            case UPDATE -> "UPDATE";
            default -> "unknown type";
        };
        return typeStr + ",updateRect=" + updateRect;
    }
}
