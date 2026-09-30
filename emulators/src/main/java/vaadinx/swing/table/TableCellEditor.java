/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.table.TableCellEditor
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.table;

import vaadinx.awt.Component;
import vaadinx.swing.JTable;

/**
 * Port of {@link javax.swing.table.TableCellEditor}. Ports (rather than
 * JDK-reuses per D_whitelist_porting) because {@link #getTableCellEditorComponent} returns
 * {@link Component} — the D_event_port_policy return-type clash between {@code java.awt.Component}
 * and {@code vaadinx.awt.Component}. Same forced-port reason as the tree-side
 * {@link vaadinx.swing.tree.TreeCellEditor}, but unlike that one (a doc-stub,
 * since JTree in-cell editing is deferred) this interface is load-bearing: the
 * JTable Grid.Editor bridge invokes {@link #getTableCellEditorComponent}
 * to obtain the editing component for a cell (see D_jtable_cell_editing in {@code emulators/decisions.md}).
 *
 * <p>{@link vaadinx.swing.DefaultCellEditor} is the stock implementation (text
 * field / combo box / check box), mirroring {@link javax.swing.DefaultCellEditor}.
 */
public interface TableCellEditor extends javax.swing.CellEditor {

    /**
     * Returns the component to use for editing the cell at {@code row}/{@code column},
     * seeded with {@code value}. Called by the JTable editing bridge when a cell
     * enters edit mode; the returned component's peer is what the underlying Vaadin
     * {@code Grid.Editor} shows.
     */
    Component getTableCellEditorComponent(JTable table, Object value,
                                          boolean isSelected, int row, int column);
}
