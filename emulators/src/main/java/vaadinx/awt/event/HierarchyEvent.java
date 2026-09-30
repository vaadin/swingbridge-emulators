/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.event.HierarchyEvent
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
 * Port of {@link java.awt.event.HierarchyEvent}. In AWT this extends
 * {@code AWTEvent} directly (not ComponentEvent), and we do the same — the
 * source is typed as Object on AWTEvent, so our Component fits.
 *
 * <p>Carries the "changed" component (the one whose parent moved), the
 * "changedParent" (its new parent at event time), and a bitmask of what
 * changed. {@code Component.fireHierarchyEvent} dispatches it with all three.
 */
public class HierarchyEvent extends java.awt.AWTEvent {

    public static final int HIERARCHY_FIRST = 1400;
    public static final int HIERARCHY_CHANGED = HIERARCHY_FIRST;
    public static final int ANCESTOR_MOVED = 1 + HIERARCHY_FIRST;
    public static final int ANCESTOR_RESIZED = 2 + HIERARCHY_FIRST;
    // Derived from ANCESTOR_RESIZED as the JDK does, not spelled as a literal: the
    // hard-coded 1401 it used to be excluded ANCESTOR_RESIZED from every
    // `id >= HIERARCHY_FIRST && id <= HIERARCHY_LAST` range check an app writes.
    public static final int HIERARCHY_LAST = ANCESTOR_RESIZED;

    public static final int PARENT_CHANGED = 0x1;
    public static final int DISPLAYABILITY_CHANGED = 0x2;
    public static final int SHOWING_CHANGED = 0x4;

    private final vaadinx.awt.Component changed;
    private final vaadinx.awt.Container changedParent;
    private final long changeFlags;

    public HierarchyEvent(vaadinx.awt.Component source, int id, vaadinx.awt.Component changed,
                          vaadinx.awt.Container changedParent, long changeFlags) {
        super(source, id);
        this.changed = changed;
        this.changedParent = changedParent;
        this.changeFlags = changeFlags;
    }

    public HierarchyEvent(vaadinx.awt.Component source, int id, vaadinx.awt.Component changed,
                          vaadinx.awt.Container changedParent) {
        this(source, id, changed, changedParent, 0);
    }

    public vaadinx.awt.Component getComponent() {
        return (vaadinx.awt.Component) getSource();
    }

    public vaadinx.awt.Component getChanged() {
        return changed;
    }

    public vaadinx.awt.Container getChangedParent() {
        return changedParent;
    }

    public long getChangeFlags() {
        return changeFlags;
    }

    @Override
    public String paramString() {
        String typeStr = switch (id) {
            case ANCESTOR_MOVED -> "ANCESTOR_MOVED";
            case ANCESTOR_RESIZED -> "ANCESTOR_RESIZED";
            case HIERARCHY_CHANGED -> "HIERARCHY_CHANGED";
            default -> "unknown type";
        };
        return typeStr + "(" + changeFlags + ")";
    }
}
