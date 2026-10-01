/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.table.JTableHeader
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.table;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import vaadinx.swing.JComponent;
import vaadinx.swing.JTable;
import vaadinx.swing.event.TableColumnModelEvent;
import vaadinx.swing.event.TableColumnModelListener;

/**
 * Port of {@link javax.swing.table.JTableHeader}. Extends our
 * {@link JComponent}; implements the ported
 * {@link TableColumnModelListener} (forced port — see that interface's
 * javadoc).
 *
 * <p>The visible header is rendered by Vaadin Grid itself via the SJTable
 * surrogate; this class is a server-side holder for the
 * table back-pointer + column-model subscription + reordering /
 * resizing-allowed shadow flags. Migrated code that calls
 * {@code header.getTable()} / {@code setReorderingAllowed(false)} round-
 * trips through these fields. Drag tracking
 * ({@code draggedColumn}, {@code resizingColumn}, {@code draggedDistance})
 * is not driven (no server-side drag pipeline) — fields preserved for
 * round-trip but always read as {@code null} / 0.
 */
@SuppressWarnings("serial") // Same-version serialization only
public class JTableHeader extends JComponent implements TableColumnModelListener, Accessible {

    private static final String uiClassID = "TableHeaderUI";

    protected JTable table;
    protected TableColumnModel columnModel;
    protected boolean reorderingAllowed;
    protected boolean resizingAllowed;

    /** Obsolete since JDK 1.3 — preserved for shape compat. */
    protected boolean updateTableInRealTime;

    /** Always {@code null} — no server-side drag tracking. */
    protected transient TableColumn resizingColumn;

    /** Always {@code null} — no server-side drag tracking. */
    protected transient TableColumn draggedColumn;

    /** Always 0 — no server-side drag tracking. */
    protected transient int draggedDistance;

    private TableCellRenderer defaultRenderer;

    public JTableHeader() {
        this(null);
    }

    public JTableHeader(TableColumnModel cm) {
        // Server-side holder peer (the visible header is Vaadin Grid's,
        // not ours). Use an inert Div so the no-arg JComponent ctor's
        // onUnimplemented WARN doesn't fire — the Div is functionally
        // unreachable from migrated code (header.getPeer() returns it,
        // but no normal flow inspects it).
        super(com.vaadin.flow.component.html.Div.class, com.vaadin.flow.component.html.Div::new);

        if (cm == null) {
            cm = createDefaultColumnModel();
        }
        setColumnModel(cm);

        // initializeLocalVars: JDK sets reorderingAllowed=true,
        // resizingAllowed=true, updateTableInRealTime=true,
        // draggedColumn=null, draggedDistance=0, resizingColumn=null.
        // Match.
        reorderingAllowed = true;
        resizingAllowed = true;
        updateTableInRealTime = true;

        // JDK constructor calls updateUI() which installs a TableHeaderUI +
        // a defaultRenderer. We ship a minimal default renderer (a
        // DefaultTableCellRenderer subclass that horizontal-centers) and
        // skip the L&F install per R_match_swing_errors sub-bucket (b).
        defaultRenderer = createDefaultRenderer();
    }

    public void setTable(JTable table) {
        JTable old = this.table;
        this.table = table;
        firePropertyChange("table", old, table);
    }

    public JTable getTable() {
        return table;
    }

    public void setReorderingAllowed(boolean reorderingAllowed) {
        boolean old = this.reorderingAllowed;
        this.reorderingAllowed = reorderingAllowed;
        firePropertyChange("reorderingAllowed", old, reorderingAllowed);
    }

    public boolean getReorderingAllowed() {
        return reorderingAllowed;
    }

    public void setResizingAllowed(boolean resizingAllowed) {
        boolean old = this.resizingAllowed;
        this.resizingAllowed = resizingAllowed;
        firePropertyChange("resizingAllowed", old, resizingAllowed);
    }

    public boolean getResizingAllowed() {
        return resizingAllowed;
    }

    public TableColumn getDraggedColumn() {
        return draggedColumn;
    }

    public int getDraggedDistance() {
        return draggedDistance;
    }

    public TableColumn getResizingColumn() {
        return resizingColumn;
    }

    public void setUpdateTableInRealTime(boolean flag) {
        updateTableInRealTime = flag;
    }

    public boolean getUpdateTableInRealTime() {
        return updateTableInRealTime;
    }

    public void setDefaultRenderer(TableCellRenderer defaultRenderer) {
        this.defaultRenderer = defaultRenderer;
    }

    public TableCellRenderer getDefaultRenderer() {
        return defaultRenderer;
    }

    public int columnAtPoint(java.awt.Point point) {
        // R_layouts_close_enough layout-close-enough: x maps to column width walk via column
        // model. Doesn't honor RTL component orientation since our
        // Component doesn't surface that yet.
        return getColumnModel().getColumnIndexAtX(point.x);
    }

    public java.awt.Rectangle getHeaderRect(int column) {
        // R_layouts_close_enough close-enough: walk widths to compute x; height is the
        // component's own height (0 in our model — Vaadin Grid owns the
        // visible header). Migrated layout code that subtracts header
        // bounds gets a directionally correct answer.
        java.awt.Rectangle r = new java.awt.Rectangle();
        TableColumnModel cm = getColumnModel();
        r.height = getHeight();

        if (column < 0) {
            // x = width = 0 in LTR; we don't track RTL.
        } else if (column >= cm.getColumnCount()) {
            r.x = getWidth();
        } else {
            for (int i = 0; i < column; i++) {
                r.x += cm.getColumn(i).getWidth();
            }
            r.width = cm.getColumn(column).getWidth();
        }
        return r;
    }

    public String getUIClassID() {
        return uiClassID;
    }

    public void setColumnModel(TableColumnModel columnModel) {
        if (columnModel == null) {
            throw new IllegalArgumentException("Cannot set a null ColumnModel");
        }
        TableColumnModel old = this.columnModel;
        if (columnModel != old) {
            if (old != null) {
                old.removeColumnModelListener(this);
            }
            this.columnModel = columnModel;
            columnModel.addColumnModelListener(this);

            firePropertyChange("columnModel", old, columnModel);
            // JDK calls resizeAndRepaint() — no-op in our model (R_layouts_close_enough).
        }
    }

    public TableColumnModel getColumnModel() {
        return columnModel;
    }

    @Override
    public void columnAdded(TableColumnModelEvent e) {
        // JDK calls resizeAndRepaint(); R_layouts_close_enough no-op for us. The SJTable
        // surrogate subscribes to the same column-model
        // events and rebuilds Vaadin Grid columns directly.
    }

    @Override
    public void columnRemoved(TableColumnModelEvent e) {
        // See columnAdded.
    }

    @Override
    public void columnMoved(TableColumnModelEvent e) {
        // See columnAdded.
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
        // See columnAdded.
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) {
        // JDK comments this is intentionally a no-op (header isn't
        // redrawn on selection change in cell-selection mode). Match.
    }

    public void resizeAndRepaint() {
        // No-op (R_layouts_close_enough) — Vaadin Grid handles its own header layout.
    }

    public void setDraggedColumn(TableColumn aColumn) {
        draggedColumn = aColumn;
    }

    public void setDraggedDistance(int distance) {
        draggedDistance = distance;
    }

    public void setResizingColumn(TableColumn aColumn) {
        resizingColumn = aColumn;
    }

    protected TableColumnModel createDefaultColumnModel() {
        return new DefaultTableColumnModel();
    }

    protected TableCellRenderer createDefaultRenderer() {
        DefaultTableCellRenderer label = new DefaultTableCellRenderer();
        label.setHorizontalAlignment(vaadinx.swing.JLabel.CENTER);
        return label;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JTableHeader", "getAccessibleContext");
        return null;
    }
}
