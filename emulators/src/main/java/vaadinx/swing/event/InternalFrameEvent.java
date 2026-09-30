/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.event.InternalFrameEvent
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.event;

/**
 * Port of {@link javax.swing.event.InternalFrameEvent}. Source retyped
 * from {@code javax.swing.JInternalFrame} to {@link vaadinx.swing.JInternalFrame}
 * (D_event_port_policy — the JDK ctor won't accept our emulator type as its source).
 *
 * <p>Constants preserved verbatim from JDK so id-arithmetic carries
 * across. Extends {@link java.awt.AWTEvent} directly, mirroring JDK's
 * {@code InternalFrameEvent extends AWTEvent} (unlike WindowEvent, which
 * JDK roots at ComponentEvent).
 *
 * <p>Mirrors {@code com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent}; the
 * two-layer artefact is stage-divergence per CLAUDE.md — emulator-stage
 * code uses this type, surrogate-stage code uses {@code SInternalFrameEvent}.
 */
public class InternalFrameEvent extends java.awt.AWTEvent {

    public static final int INTERNAL_FRAME_FIRST       = 25549;
    public static final int INTERNAL_FRAME_LAST        = 25555;
    public static final int INTERNAL_FRAME_OPENED      = INTERNAL_FRAME_FIRST;
    public static final int INTERNAL_FRAME_CLOSING     = 1 + INTERNAL_FRAME_FIRST;
    public static final int INTERNAL_FRAME_CLOSED      = 2 + INTERNAL_FRAME_FIRST;
    public static final int INTERNAL_FRAME_ICONIFIED   = 3 + INTERNAL_FRAME_FIRST;
    public static final int INTERNAL_FRAME_DEICONIFIED = 4 + INTERNAL_FRAME_FIRST;
    public static final int INTERNAL_FRAME_ACTIVATED   = 5 + INTERNAL_FRAME_FIRST;
    public static final int INTERNAL_FRAME_DEACTIVATED = 6 + INTERNAL_FRAME_FIRST;

    public InternalFrameEvent(vaadinx.swing.JInternalFrame source, int id) {
        super(source, id);
    }

    /** The internal frame that originated this event. */
    public vaadinx.swing.JInternalFrame getInternalFrame() {
        return (source instanceof vaadinx.swing.JInternalFrame f) ? f : null;
    }

    @Override
    public String paramString() {
        return switch (getID()) {
            case INTERNAL_FRAME_OPENED      -> "INTERNAL_FRAME_OPENED";
            case INTERNAL_FRAME_CLOSING     -> "INTERNAL_FRAME_CLOSING";
            case INTERNAL_FRAME_CLOSED      -> "INTERNAL_FRAME_CLOSED";
            case INTERNAL_FRAME_ICONIFIED   -> "INTERNAL_FRAME_ICONIFIED";
            case INTERNAL_FRAME_DEICONIFIED -> "INTERNAL_FRAME_DEICONIFIED";
            case INTERNAL_FRAME_ACTIVATED   -> "INTERNAL_FRAME_ACTIVATED";
            case INTERNAL_FRAME_DEACTIVATED -> "INTERNAL_FRAME_DEACTIVATED";
            default                         -> "unknown type";
        };
    }
}
