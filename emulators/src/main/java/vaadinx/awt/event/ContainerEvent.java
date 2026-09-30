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
 * This file is derived from OpenJDK's java.awt.event.ContainerEvent
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
 * Port of {@link java.awt.event.ContainerEvent}. Fired by
 * {@link vaadinx.awt.Container} when a child is added or removed so user
 * {@link ContainerListener}s get the events they expect (R_swing_is_truth).
 *
 * <p>Extends {@link ComponentEvent} — AWT does the same — so any code that
 * walks the event hierarchy via {@code instanceof ComponentEvent} keeps
 * working across the port.
 */
public class ContainerEvent extends ComponentEvent {

    public static final int CONTAINER_FIRST = 300;
    public static final int CONTAINER_LAST = 301;
    public static final int COMPONENT_ADDED = CONTAINER_FIRST;
    public static final int COMPONENT_REMOVED = 1 + CONTAINER_FIRST;

    private final vaadinx.awt.Component child;

    public ContainerEvent(vaadinx.awt.Container source, int id, vaadinx.awt.Component child) {
        super(source, id);
        this.child = child;
    }

    public vaadinx.awt.Container getContainer() {
        // Source is always a Container by construction.
        return (vaadinx.awt.Container) getSource();
    }

    public vaadinx.awt.Component getChild() {
        return child;
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case COMPONENT_ADDED -> "COMPONENT_ADDED";
            case COMPONENT_REMOVED -> "COMPONENT_REMOVED";
            default -> "unknown type";
        };
        return typeStr + ",child=" + (child == null ? "null" : child.getName());
    }
}
