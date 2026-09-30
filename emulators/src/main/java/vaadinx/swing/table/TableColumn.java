/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.table.TableColumn
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.table;

import java.beans.PropertyChangeListener;
import java.io.Serializable;
import javax.swing.event.SwingPropertyChangeSupport;

/**
 * Port of {@link javax.swing.table.TableColumn}. Forced to port (rather than
 * JDK-reuse via D_whitelist_porting) because its {@code cellRenderer} / {@code headerRenderer}
 * fields are typed {@link TableCellRenderer} — and {@link TableCellRenderer}
 * itself is ported per D_event_port_policy's Component-touching rule (see
 * {@link TableCellRenderer} javadoc). The {@code cellEditor} field is typed
 * {@link TableCellEditor} — the ported editor interface (forced-port by the
 * D_event_port_policy Component return-type clash; see D_jtable_cell_editing in {@code emulators/decisions.md}).
 * The field round-trips
 * for API shape; the JTable Grid.Editor bridge that consumes it has landed (D_jtable_cell_editing).
 *
 * <p>Body ported faithfully (R_swing_is_truth) from JDK's class — fields, ctors, getters,
 * setters, PCE plumbing, width clamping all match the JDK implementation
 * verbatim. {@code createDefaultHeaderRenderer} ports the JDK one-liner
 * (returns a centered {@link DefaultTableCellRenderer}) but drops the
 * {@code UIManager.getBorder("TableHeader.cellBorder")} call — L&amp;F
 * dispatch is permanent sub-bucket (b) per R_match_swing_errors.
 */
@SuppressWarnings("serial") // Same-version serialization only
public class TableColumn implements Serializable {

    public static final String COLUMN_WIDTH_PROPERTY = "columWidth";
    public static final String HEADER_VALUE_PROPERTY = "headerValue";
    public static final String HEADER_RENDERER_PROPERTY = "headerRenderer";
    public static final String CELL_RENDERER_PROPERTY = "cellRenderer";

    protected int modelIndex;
    protected Object identifier;
    protected int width;
    protected int minWidth;
    private int preferredWidth;
    protected int maxWidth;
    protected TableCellRenderer headerRenderer;
    protected Object headerValue;
    protected TableCellRenderer cellRenderer;
    protected TableCellEditor cellEditor;
    protected boolean isResizable;

    /** Deprecated by JDK. Preserved for binary-shape compat. */
    @Deprecated
    protected transient int resizedPostingDisableCount;

    private SwingPropertyChangeSupport changeSupport;

    public TableColumn() {
        this(0);
    }

    public TableColumn(int modelIndex) {
        this(modelIndex, 75, null, null);
    }

    public TableColumn(int modelIndex, int width) {
        this(modelIndex, width, null, null);
    }

    public TableColumn(int modelIndex, int width,
                       TableCellRenderer cellRenderer,
                       TableCellEditor cellEditor) {
        super();
        this.modelIndex = modelIndex;
        preferredWidth = this.width = Math.max(width, 0);

        this.cellRenderer = cellRenderer;
        this.cellEditor = cellEditor;

        minWidth = Math.min(15, this.width);
        maxWidth = Integer.MAX_VALUE;
        isResizable = true;
        resizedPostingDisableCount = 0;
        headerValue = null;
    }

    private void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (changeSupport != null) {
            changeSupport.firePropertyChange(propertyName, oldValue, newValue);
        }
    }

    private void firePropertyChange(String propertyName, int oldValue, int newValue) {
        if (oldValue != newValue) {
            firePropertyChange(propertyName, Integer.valueOf(oldValue), Integer.valueOf(newValue));
        }
    }

    private void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        if (oldValue != newValue) {
            firePropertyChange(propertyName, Boolean.valueOf(oldValue), Boolean.valueOf(newValue));
        }
    }

    public void setModelIndex(int modelIndex) {
        int old = this.modelIndex;
        this.modelIndex = modelIndex;
        firePropertyChange("modelIndex", old, modelIndex);
    }

    public int getModelIndex() {
        return modelIndex;
    }

    public void setIdentifier(Object identifier) {
        Object old = this.identifier;
        this.identifier = identifier;
        firePropertyChange("identifier", old, identifier);
    }

    public Object getIdentifier() {
        return (identifier != null) ? identifier : getHeaderValue();
    }

    public void setHeaderValue(Object headerValue) {
        Object old = this.headerValue;
        this.headerValue = headerValue;
        firePropertyChange("headerValue", old, headerValue);
    }

    public Object getHeaderValue() {
        return headerValue;
    }

    public void setHeaderRenderer(TableCellRenderer headerRenderer) {
        TableCellRenderer old = this.headerRenderer;
        this.headerRenderer = headerRenderer;
        firePropertyChange("headerRenderer", old, headerRenderer);
    }

    public TableCellRenderer getHeaderRenderer() {
        return headerRenderer;
    }

    public void setCellRenderer(TableCellRenderer cellRenderer) {
        TableCellRenderer old = this.cellRenderer;
        this.cellRenderer = cellRenderer;
        firePropertyChange("cellRenderer", old, cellRenderer);
    }

    public TableCellRenderer getCellRenderer() {
        return cellRenderer;
    }

    public void setCellEditor(TableCellEditor cellEditor) {
        TableCellEditor old = this.cellEditor;
        this.cellEditor = cellEditor;
        firePropertyChange("cellEditor", old, cellEditor);
    }

    public TableCellEditor getCellEditor() {
        return cellEditor;
    }

    public void setWidth(int width) {
        int old = this.width;
        this.width = Math.min(Math.max(width, minWidth), maxWidth);
        firePropertyChange("width", old, this.width);
    }

    public int getWidth() {
        return width;
    }

    public void setPreferredWidth(int preferredWidth) {
        int old = this.preferredWidth;
        this.preferredWidth = Math.min(Math.max(preferredWidth, minWidth), maxWidth);
        firePropertyChange("preferredWidth", old, this.preferredWidth);
    }

    public int getPreferredWidth() {
        return preferredWidth;
    }

    public void setMinWidth(int minWidth) {
        int old = this.minWidth;
        this.minWidth = Math.max(Math.min(minWidth, maxWidth), 0);
        if (width < this.minWidth) {
            setWidth(this.minWidth);
        }
        if (preferredWidth < this.minWidth) {
            setPreferredWidth(this.minWidth);
        }
        firePropertyChange("minWidth", old, this.minWidth);
    }

    public int getMinWidth() {
        return minWidth;
    }

    public void setMaxWidth(int maxWidth) {
        int old = this.maxWidth;
        this.maxWidth = Math.max(minWidth, maxWidth);
        if (width > this.maxWidth) {
            setWidth(this.maxWidth);
        }
        if (preferredWidth > this.maxWidth) {
            setPreferredWidth(this.maxWidth);
        }
        firePropertyChange("maxWidth", old, this.maxWidth);
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public void setResizable(boolean isResizable) {
        boolean old = this.isResizable;
        this.isResizable = isResizable;
        firePropertyChange("isResizable", old, this.isResizable);
    }

    public boolean getResizable() {
        return isResizable;
    }

    public void sizeWidthToFit() {
        if (headerRenderer == null) {
            return;
        }
        // JDK reads c.getMinimumSize / getMaximumSize / getPreferredSize on
        // the rendered Component. Our Components stub these to defaults
        // (R_layouts_close_enough), so the resulting widths collapse to the defaults — best-
        // effort R_best_effort_behaviour. Migrated code that calls sizeWidthToFit gets a
        // close-enough answer rather than an NPE.
        vaadinx.awt.Component c = headerRenderer.getTableCellRendererComponent(null,
                getHeaderValue(), false, false, 0, 0);

        setMinWidth(c.getMinimumSize().width);
        setMaxWidth(c.getMaximumSize().width);
        setPreferredWidth(c.getPreferredSize().width);

        setWidth(getPreferredWidth());
    }

    @Deprecated
    public void disableResizedPosting() {
        resizedPostingDisableCount++;
    }

    @Deprecated
    public void enableResizedPosting() {
        resizedPostingDisableCount--;
    }

    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport == null) {
            changeSupport = new SwingPropertyChangeSupport(this);
        }
        changeSupport.addPropertyChangeListener(listener);
    }

    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport != null) {
            changeSupport.removePropertyChangeListener(listener);
        }
    }

    public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
        if (changeSupport == null) {
            return new PropertyChangeListener[0];
        }
        return changeSupport.getPropertyChangeListeners();
    }

    /**
     * As of JDK 1.3, no longer called by the constructor — header rendering
     * lives on {@link JTableHeader#createDefaultRenderer()}. Kept for API
     * compat. Drops the {@code UIManager.getBorder("TableHeader.cellBorder")}
     * line per R_match_swing_errors sub-bucket (b) — L&amp;F dispatch isn't modeled.
     */
    protected TableCellRenderer createDefaultHeaderRenderer() {
        DefaultTableCellRenderer label = new DefaultTableCellRenderer() {
            public vaadinx.awt.Component getTableCellRendererComponent(vaadinx.swing.JTable table, Object value,
                                                                       boolean isSelected, boolean hasFocus,
                                                                       int row, int column) {
                setText((value == null) ? "" : value.toString());
                return this;
            }
        };
        label.setHorizontalAlignment(vaadinx.swing.JLabel.CENTER);
        return label;
    }
}
