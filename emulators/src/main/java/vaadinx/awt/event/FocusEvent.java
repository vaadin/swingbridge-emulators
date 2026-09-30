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
 * This file is derived from OpenJDK's java.awt.event.FocusEvent
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
 * Port of {@link java.awt.event.FocusEvent}. Fired on focus gained/lost
 * transitions; our dispatch is a stub today but the type surface is ported
 * so peer-originating focus events can eventually land here (D_event_port_policy → D_callswing_funnel).
 */
public class FocusEvent extends ComponentEvent {

    public static final int FOCUS_FIRST = 1004;
    public static final int FOCUS_LAST = 1005;
    public static final int FOCUS_GAINED = FOCUS_FIRST;
    public static final int FOCUS_LOST = 1 + FOCUS_FIRST;

    /** Reflects AWT's Cause enum values. */
    public enum Cause {
        UNKNOWN,
        MOUSE_EVENT,
        TRAVERSAL,
        TRAVERSAL_UP,
        TRAVERSAL_DOWN,
        TRAVERSAL_FORWARD,
        TRAVERSAL_BACKWARD,
        ROLLBACK,
        UNEXPECTED,
        ACTIVATION,
        CLEAR_GLOBAL_FOCUS_OWNER
    }

    private final boolean temporary;
    private final vaadinx.awt.Component opposite;
    private final Cause cause;

    public FocusEvent(vaadinx.awt.Component source, int id, boolean temporary,
                      vaadinx.awt.Component opposite, Cause cause) {
        super(source, id);
        this.temporary = temporary;
        this.opposite = opposite;
        this.cause = cause == null ? Cause.UNKNOWN : cause;
    }

    public FocusEvent(vaadinx.awt.Component source, int id, boolean temporary,
                      vaadinx.awt.Component opposite) {
        this(source, id, temporary, opposite, Cause.UNKNOWN);
    }

    public FocusEvent(vaadinx.awt.Component source, int id, boolean temporary) {
        this(source, id, temporary, null, Cause.UNKNOWN);
    }

    public FocusEvent(vaadinx.awt.Component source, int id) {
        this(source, id, false, null, Cause.UNKNOWN);
    }

    public boolean isTemporary() {
        return temporary;
    }

    public vaadinx.awt.Component getOppositeComponent() {
        return opposite;
    }

    public Cause getCause() {
        return cause;
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case FOCUS_GAINED -> "FOCUS_GAINED";
            case FOCUS_LOST -> "FOCUS_LOST";
            default -> "unknown type";
        };
        return typeStr + (temporary ? ",temporary" : ",permanent") + ",cause=" + cause;
    }
}
