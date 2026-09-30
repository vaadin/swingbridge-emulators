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
 * This file is derived from OpenJDK's java.awt.event.ComponentEvent
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
 * Port of {@link java.awt.event.ComponentEvent}. AWT's version types its
 * source as {@code java.awt.Component} and {@code vaadinx.awt.Component}
 * isn't castable to that (D_event_port_policy). We extend {@code java.awt.AWTEvent} — reused
 * unchanged per D_whitelist_porting since AWTEvent's own source type is {@code Object} — and
 * replace only the Component-typed fields and accessors.
 *
 * <p>Nothing in the current slice actually fires ComponentEvent; the class
 * exists so {@link ContainerEvent} has the right parent. The id-range
 * constants are kept at AWT's values so any user code that compares them to
 * AWT constants still works.
 */
public class ComponentEvent extends java.awt.AWTEvent {

    public static final int COMPONENT_FIRST = 100;
    public static final int COMPONENT_LAST = 103;
    public static final int COMPONENT_MOVED = COMPONENT_FIRST;
    public static final int COMPONENT_RESIZED = 1 + COMPONENT_FIRST;
    public static final int COMPONENT_SHOWN = 2 + COMPONENT_FIRST;
    public static final int COMPONENT_HIDDEN = 3 + COMPONENT_FIRST;

    public ComponentEvent(vaadinx.awt.Component source, int id) {
        super(source, id);
    }

    /**
     * Returns the originating component. Cast to {@code vaadinx.awt.Component}
     * is safe because the constructor is the only entry point and requires
     * exactly that type.
     */
    public vaadinx.awt.Component getComponent() {
        return (vaadinx.awt.Component) getSource();
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case COMPONENT_MOVED -> "COMPONENT_MOVED";
            case COMPONENT_RESIZED -> "COMPONENT_RESIZED";
            case COMPONENT_SHOWN -> "COMPONENT_SHOWN";
            case COMPONENT_HIDDEN -> "COMPONENT_HIDDEN";
            default -> "unknown type";
        };
        return typeStr;
    }
}
