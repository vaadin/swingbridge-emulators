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
 * This file is derived from OpenJDK's javax.swing.event.TableColumnModelEvent
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.event;

import vaadinx.swing.table.TableColumnModel;

/**
 * Port of {@link javax.swing.event.TableColumnModelEvent}. Forced to port
 * (rather than JDK-reuse via D_event_port_policy) because the JDK ctor signature
 * {@code TableColumnModelEvent(javax.swing.table.TableColumnModel, int, int)}
 * would not accept our ported {@link vaadinx.swing.table.TableColumnModel}
 * as its source argument — and our {@link vaadinx.swing.table.DefaultTableColumnModel}
 * is the firing party.
 *
 * <p>Body identical to JDK's class — just changes the source type to our
 * ported {@code TableColumnModel}.
 */
public class TableColumnModelEvent extends java.util.EventObject {

    /** The index of the column from where it was moved or removed. */
    protected int fromIndex;

    /** The index of the column to where it was moved or added. */
    protected int toIndex;

    public TableColumnModelEvent(TableColumnModel source, int from, int to) {
        super(source);
        fromIndex = from;
        toIndex = to;
    }

    public int getFromIndex() {
        return fromIndex;
    }

    public int getToIndex() {
        return toIndex;
    }
}
