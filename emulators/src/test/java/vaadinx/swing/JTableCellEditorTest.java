/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.table.TableCellEditor;

import javax.swing.table.DefaultTableModel;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 1 of the JTable cell-editing pass (D_jtable_cell_editing): editor <b>resolution +
 * coercion</b>, exercised with no {@code Grid.Editor} in play. Covers the default-editor
 * registry (superclass walk, {@code setDefaultEditor} override), the ported
 * {@code GenericEditor}/{@code NumberEditor}/{@code BooleanEditor}, the {@code String}→columnClass
 * coercion including the {@code getColumnClass == Object} gotcha, bad-input
 * refuse-to-commit, and {@code getCellEditor}/{@code setCellEditor}/{@code prepareEditor}.
 * Interactive open (editCellAt / isEditing / …) is Slice 2.
 */
class JTableCellEditorTest extends AbstractKaribuTest {

    /** A one-cell table whose column reports {@code columnClass} from getColumnClass. */
    private static JTable tableWithColumnClass(Class<?> columnClass, Object value) {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][] {{value}}, new Object[] {"col"}) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnClass;
            }
        };
        return new JTable(model);
    }

    // --- Default-editor registry resolution ---

    @Test
    @DisplayName("getDefaultEditor resolves by class with superclass walk")
    void getDefaultEditorWalksSuperclasses() {
        JTable table = new JTable(new DefaultTableModel(1, 1));
        assertInstanceOf(JTable.GenericEditor.class, table.getDefaultEditor(Object.class));
        assertInstanceOf(JTable.NumberEditor.class, table.getDefaultEditor(Integer.class)); // Integer -> Number
        assertInstanceOf(JTable.NumberEditor.class, table.getDefaultEditor(Double.class));
        assertInstanceOf(JTable.BooleanEditor.class, table.getDefaultEditor(Boolean.class));
        assertInstanceOf(JTable.GenericEditor.class, table.getDefaultEditor(StringBuilder.class)); // Object fallback
    }

    @Test
    @DisplayName("setDefaultEditor override wins and null removes")
    void setDefaultEditorOverrideAndRemoval() {
        JTable table = new JTable(new DefaultTableModel(1, 1));
        DefaultCellEditor custom = new DefaultCellEditor(new JTextField());
        table.setDefaultEditor(Integer.class, custom);
        assertSame(custom, table.getDefaultEditor(Integer.class));
        table.setDefaultEditor(Integer.class, null);
        assertInstanceOf(JTable.NumberEditor.class, table.getDefaultEditor(Integer.class)); // back to default
    }

    @Test
    @DisplayName("getCellEditor prefers the column's own editor over the default")
    void columnEditorWinsOverDefault() {
        JTable table = tableWithColumnClass(Object.class, "x");
        DefaultCellEditor colEd = new DefaultCellEditor(new JTextField());
        table.getColumnModel().getColumn(0).setCellEditor(colEd);
        assertSame(colEd, table.getCellEditor(0, 0));
    }

    // --- Coercion (D_jtable_cell_editing) ---

    @Test
    @DisplayName("Integer column coerces typed String to Integer via getCellEditorValue")
    void integerColumnCoercesTypedString() {
        JTable table = tableWithColumnClass(Integer.class, 1);
        TableCellEditor editor = table.getCellEditor(0, 0);
        assertInstanceOf(JTable.NumberEditor.class, editor);
        JTextField field = (JTextField) editor.getTableCellEditorComponent(table, 1, false, 0, 0);
        field.setText("42");
        assertTrue(editor.stopCellEditing());
        assertEquals(Integer.valueOf(42), editor.getCellEditorValue());
    }

    @Test
    @DisplayName("Object column round-trips as String — the getColumnClass gotcha")
    void objectColumnRoundTripsAsString() {
        JTable table = tableWithColumnClass(Object.class, "x");
        TableCellEditor editor = table.getCellEditor(0, 0);
        assertInstanceOf(JTable.GenericEditor.class, editor);
        JTextField field = (JTextField) editor.getTableCellEditorComponent(table, "x", false, 0, 0);
        field.setText("42");
        assertTrue(editor.stopCellEditing());
        assertEquals("42", editor.getCellEditorValue());          // String, not Integer
        assertInstanceOf(String.class, editor.getCellEditorValue());
    }

    @Test
    @DisplayName("bad input refuses to commit")
    void badInputRefusesToCommit() {
        JTable table = tableWithColumnClass(Integer.class, 1);
        TableCellEditor editor = table.getCellEditor(0, 0);
        JTextField field = (JTextField) editor.getTableCellEditorComponent(table, 1, false, 0, 0);
        field.setText("abc");
        assertFalse(editor.stopCellEditing());                    // Integer("abc") throws -> refuse
        assertNull(editor.getCellEditorValue());                  // nothing committed
    }

    @Test
    @DisplayName("Boolean editor yields Boolean")
    void booleanEditorYieldsBoolean() {
        JTable table = new JTable(new DefaultTableModel(1, 1));
        TableCellEditor editor = table.getDefaultEditor(Boolean.class);
        assertInstanceOf(JTable.BooleanEditor.class, editor);
        editor.getTableCellEditorComponent(table, true, false, 0, 0);
        assertEquals(Boolean.TRUE, editor.getCellEditorValue());
    }

    // --- setCellEditor / getCellEditor round-trip + PCE ---

    @Test
    @DisplayName("setCellEditor stores and fires tableCellEditor PCE")
    void setCellEditorFiresPce() {
        JTable table = new JTable(new DefaultTableModel(1, 1));
        List<PropertyChangeEvent> pces = new ArrayList<>();
        table.addPropertyChangeListener("tableCellEditor", pces::add);
        DefaultCellEditor ed = new DefaultCellEditor(new JTextField());
        table.setCellEditor(ed);
        assertSame(ed, table.getCellEditor());                    // no-arg getCellEditor()
        assertEquals(1, pces.size());
    }

    @Test
    @DisplayName("prepareEditor seeds the editor component from the cell value")
    void prepareEditorSeedsComponent() {
        JTable table = tableWithColumnClass(Object.class, "hi");
        TableCellEditor editor = table.getCellEditor(0, 0);
        JTextField comp = (JTextField) table.prepareEditor(editor, 0, 0);
        assertEquals("hi", comp.getText());
    }
}
