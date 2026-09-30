/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.event.AncestorEvent
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
 * Port of {@link javax.swing.event.AncestorEvent}. Forced to port (D_event_port_policy) —
 * JDK's ctor signature
 * {@code AncestorEvent(javax.swing.JComponent, int, java.awt.Container, java.awt.Container)}
 * doesn't accept our {@link vaadinx.swing.JComponent} or
 * {@link vaadinx.awt.Container} as its source / ancestor arguments.
 *
 * <p>Constants ({@link #ANCESTOR_ADDED} / {@link #ANCESTOR_REMOVED} /
 * {@link #ANCESTOR_MOVED}) preserved verbatim from JDK so id-arithmetic
 * carries across.
 *
 * <p>Mirrors the shape of
 * {@code com.vaadin.swingbridge.surrogates.awt.event.SAncestorEvent}; the two-layer artefact
 * is stage-divergence per CLAUDE.md ("apparent two-layer artefacts are
 * stage-divergence, not API confusion") — emulator-stage code uses this
 * type, surrogate-stage code uses {@code SAncestorEvent}.
 */
public class AncestorEvent extends java.awt.AWTEvent {

    public static final int ANCESTOR_ADDED   = 1;
    public static final int ANCESTOR_REMOVED = 2;
    public static final int ANCESTOR_MOVED   = 3;

    private final vaadinx.awt.Container ancestor;
    private final vaadinx.awt.Container ancestorParent;

    public AncestorEvent(vaadinx.swing.JComponent source, int id,
                         vaadinx.awt.Container ancestor,
                         vaadinx.awt.Container ancestorParent) {
        super(source, id);
        this.ancestor = ancestor;
        this.ancestorParent = ancestorParent;
    }

    public vaadinx.awt.Container getAncestor() {
        return ancestor;
    }

    public vaadinx.awt.Container getAncestorParent() {
        return ancestorParent;
    }

    public vaadinx.swing.JComponent getComponent() {
        // Source is always a JComponent by construction (ctor signature).
        return (vaadinx.swing.JComponent) getSource();
    }

    @Override
    public String paramString() {
        return switch (id) {
            case ANCESTOR_ADDED   -> "ANCESTOR_ADDED";
            case ANCESTOR_REMOVED -> "ANCESTOR_REMOVED";
            case ANCESTOR_MOVED   -> "ANCESTOR_MOVED";
            default               -> "unknown type";
        };
    }
}
