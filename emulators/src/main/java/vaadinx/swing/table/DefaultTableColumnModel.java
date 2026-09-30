/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.table.DefaultTableColumnModel
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.table;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Vector;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import vaadinx.swing.event.TableColumnModelEvent;
import vaadinx.swing.event.TableColumnModelListener;

/**
 * Port of {@link javax.swing.table.DefaultTableColumnModel}. Body ported
 * faithfully (R_swing_is_truth) — Vector storage, EventListenerList fan-out, lazy-cached
 * total-width recompute, the implicit listener loop where the column model
 * subscribes to its own selection model and re-fires on
 * {@code columnSelectionChanged}, and the JDK identity-comparison oddity in
 * {@code propertyChange} (uses {@code ==} on interned property names) — all
 * preserved verbatim.
 *
 * <p>References the ported {@link TableColumn} (cell-renderer field type)
 * and ported {@link vaadinx.swing.event.TableColumnModelEvent} /
 * {@link vaadinx.swing.event.TableColumnModelListener} (forced ports per
 * D_event_port_policy — see those classes' javadoc). Selection model + ChangeEvent +
 * ListSelectionEvent stay JDK types per D_whitelist_porting/D_event_port_policy.
 */
@SuppressWarnings("serial") // Same-version serialization only
public class DefaultTableColumnModel implements TableColumnModel,
        PropertyChangeListener, ListSelectionListener, Serializable {

    protected Vector<TableColumn> tableColumns;
    protected ListSelectionModel selectionModel;
    protected int columnMargin;
    protected EventListenerList listenerList = new EventListenerList();
    protected transient ChangeEvent changeEvent = null;
    protected boolean columnSelectionAllowed;
    protected int totalColumnWidth;

    public DefaultTableColumnModel() {
        super();

        tableColumns = new Vector<TableColumn>();
        setSelectionModel(createSelectionModel());
        setColumnMargin(1);
        invalidateWidthCache();
        setColumnSelectionAllowed(false);
    }

    public void addColumn(TableColumn aColumn) {
        if (aColumn == null) {
            throw new IllegalArgumentException("Object is null");
        }

        tableColumns.addElement(aColumn);
        aColumn.addPropertyChangeListener(this);
        invalidateWidthCache();

        fireColumnAdded(new TableColumnModelEvent(this, 0, getColumnCount() - 1));
    }

    public void removeColumn(TableColumn column) {
        int columnIndex = tableColumns.indexOf(column);

        if (columnIndex != -1) {
            if (selectionModel != null) {
                selectionModel.removeIndexInterval(columnIndex, columnIndex);
            }

            column.removePropertyChangeListener(this);
            tableColumns.removeElementAt(columnIndex);
            invalidateWidthCache();

            fireColumnRemoved(new TableColumnModelEvent(this, columnIndex, 0));
        }
    }

    public void moveColumn(int columnIndex, int newIndex) {
        if ((columnIndex < 0) || (columnIndex >= getColumnCount()) ||
                (newIndex < 0) || (newIndex >= getColumnCount())) {
            throw new IllegalArgumentException("moveColumn() - Index out of range");
        }

        TableColumn aColumn;

        if (columnIndex == newIndex) {
            fireColumnMoved(new TableColumnModelEvent(this, columnIndex, newIndex));
            return;
        }
        aColumn = tableColumns.elementAt(columnIndex);

        tableColumns.removeElementAt(columnIndex);
        boolean selected = selectionModel.isSelectedIndex(columnIndex);
        selectionModel.removeIndexInterval(columnIndex, columnIndex);

        tableColumns.insertElementAt(aColumn, newIndex);
        selectionModel.insertIndexInterval(newIndex, 1, true);
        if (selected) {
            selectionModel.addSelectionInterval(newIndex, newIndex);
        } else {
            selectionModel.removeSelectionInterval(newIndex, newIndex);
        }

        fireColumnMoved(new TableColumnModelEvent(this, columnIndex, newIndex));
    }

    public void setColumnMargin(int newMargin) {
        if (newMargin != columnMargin) {
            columnMargin = newMargin;
            fireColumnMarginChanged();
        }
    }

    public int getColumnCount() {
        return tableColumns.size();
    }

    public Enumeration<TableColumn> getColumns() {
        return tableColumns.elements();
    }

    public int getColumnIndex(Object identifier) {
        if (identifier == null) {
            throw new IllegalArgumentException("Identifier is null");
        }

        Enumeration<TableColumn> enumeration = getColumns();
        TableColumn aColumn;
        int index = 0;

        while (enumeration.hasMoreElements()) {
            aColumn = enumeration.nextElement();
            if (identifier.equals(aColumn.getIdentifier())) {
                return index;
            }
            index++;
        }
        throw new IllegalArgumentException("Identifier not found");
    }

    public TableColumn getColumn(int columnIndex) {
        return tableColumns.elementAt(columnIndex);
    }

    public int getColumnMargin() {
        return columnMargin;
    }

    public int getColumnIndexAtX(int x) {
        if (x < 0) {
            return -1;
        }
        int cc = getColumnCount();
        for (int column = 0; column < cc; column++) {
            x = x - getColumn(column).getWidth();
            if (x < 0) {
                return column;
            }
        }
        return -1;
    }

    public int getTotalColumnWidth() {
        if (totalColumnWidth == -1) {
            recalcWidthCache();
        }
        return totalColumnWidth;
    }

    public void setSelectionModel(ListSelectionModel newModel) {
        if (newModel == null) {
            throw new IllegalArgumentException("Cannot set a null SelectionModel");
        }

        ListSelectionModel oldModel = selectionModel;

        if (newModel != oldModel) {
            if (oldModel != null) {
                oldModel.removeListSelectionListener(this);
            }

            selectionModel = newModel;
            newModel.addListSelectionListener(this);
        }
    }

    public ListSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void setColumnSelectionAllowed(boolean flag) {
        columnSelectionAllowed = flag;
    }

    public boolean getColumnSelectionAllowed() {
        return columnSelectionAllowed;
    }

    public int[] getSelectedColumns() {
        if (selectionModel != null) {
            return selectionModel.getSelectedIndices();
        }
        return new int[0];
    }

    public int getSelectedColumnCount() {
        if (selectionModel != null) {
            return selectionModel.getSelectedItemsCount();
        }
        return 0;
    }

    public void addColumnModelListener(TableColumnModelListener x) {
        listenerList.add(TableColumnModelListener.class, x);
    }

    public void removeColumnModelListener(TableColumnModelListener x) {
        listenerList.remove(TableColumnModelListener.class, x);
    }

    public TableColumnModelListener[] getColumnModelListeners() {
        return listenerList.getListeners(TableColumnModelListener.class);
    }

    protected void fireColumnAdded(TableColumnModelEvent e) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TableColumnModelListener.class) {
                ((TableColumnModelListener) listeners[i + 1]).columnAdded(e);
            }
        }
    }

    protected void fireColumnRemoved(TableColumnModelEvent e) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TableColumnModelListener.class) {
                ((TableColumnModelListener) listeners[i + 1]).columnRemoved(e);
            }
        }
    }

    protected void fireColumnMoved(TableColumnModelEvent e) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TableColumnModelListener.class) {
                ((TableColumnModelListener) listeners[i + 1]).columnMoved(e);
            }
        }
    }

    protected void fireColumnSelectionChanged(ListSelectionEvent e) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TableColumnModelListener.class) {
                ((TableColumnModelListener) listeners[i + 1]).columnSelectionChanged(e);
            }
        }
    }

    protected void fireColumnMarginChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TableColumnModelListener.class) {
                if (changeEvent == null) {
                    changeEvent = new ChangeEvent(this);
                }
                ((TableColumnModelListener) listeners[i + 1]).columnMarginChanged(changeEvent);
            }
        }
    }

    public <T extends EventListener> T[] getListeners(Class<T> listenerType) {
        return listenerList.getListeners(listenerType);
    }

    public void propertyChange(PropertyChangeEvent evt) {
        String name = evt.getPropertyName();

        // JDK uses == on interned strings here — preserve verbatim per R_swing_is_truth.
        // String literals get interned so the comparison works in practice;
        // if the property name comes from elsewhere this branch is missed,
        // matching JDK's behavior exactly.
        if (name == "width" || name == "preferredWidth") {
            invalidateWidthCache();
            fireColumnMarginChanged();
        }
    }

    public void valueChanged(ListSelectionEvent e) {
        fireColumnSelectionChanged(e);
    }

    protected ListSelectionModel createSelectionModel() {
        return new DefaultListSelectionModel();
    }

    protected void recalcWidthCache() {
        Enumeration<TableColumn> enumeration = getColumns();
        totalColumnWidth = 0;
        while (enumeration.hasMoreElements()) {
            totalColumnWidth += enumeration.nextElement().getWidth();
        }
    }

    private void invalidateWidthCache() {
        totalColumnWidth = -1;
    }
}
