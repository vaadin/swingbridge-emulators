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
 * This file is derived from OpenJDK's javax.swing.table.TableCellRenderer
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.table;

/**
 * Port of {@link javax.swing.table.TableCellRenderer}. Returns
 * {@link vaadinx.awt.Component} so the renderer hands back our emulator's
 * Component hierarchy — JDK's {@code java.awt.Component} return type is
 * incompatible with our hierarchy and is the central reason this interface
 * is ported wholesale rather than reused (D_event_port_policy + the cell-renderer carve-out).
 * Mirrors the
 * {@link vaadinx.swing.ListCellRenderer} stance for the same reason.
 *
 * <p>The {@code table} parameter is typed {@link vaadinx.swing.JTable} and
 * arrives differently from the two bridges (see {@code @param table}). A
 * renderer that doesn't read the table (the common-case
 * {@link DefaultTableCellRenderer} pattern: just format the value) works
 * unchanged; one reading {@code table.getSelectionForeground()} or similar
 * NPEs in the pure-surrogate path where {@code table} is {@code null}.
 */
public interface TableCellRenderer {

    /**
     * Build the rendering component for {@code value}.
     *
     * @param table         the rendering host — emulator JTable in
     *                      emulator-flow, {@code null} from pure-surrogate
     *                      contexts.
     * @param value         the cell value to render. {@code null} is valid.
     * @param isSelected    true if the cell is to be rendered with the
     *                      selection highlighted.
     * @param hasFocus      true if the cell has keyboard focus. Vaadin Grid
     *                      has no per-cell focus signal at render time;
     *                      the SJTable bridge always passes {@code false}
     *                      (R_vaadin_first sub-bucket (c)).
     * @param row           the row index of the cell being drawn. When
     *                      drawing a column header, the value is -1.
     * @param column        the column index of the cell being drawn.
     */
    vaadinx.awt.Component getTableCellRendererComponent(vaadinx.swing.JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column);
}
