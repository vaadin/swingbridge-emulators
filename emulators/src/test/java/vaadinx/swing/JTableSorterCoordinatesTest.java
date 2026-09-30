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

import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_jtable_cell_editing — the emulator {@link JTable} public row API is <i>view</i>-indexed
 * (JDK-faithful); the surrogate is <i>model</i>-indexed (its Grid item IS the model row). With a
 * {@link RowSorter} active, view row ≠ model row, so these tests pin the conversion at
 * the seam: value/selection/edit access by view row must reach the right model row,
 * and the textbook {@code convertRowIndexToModel(getSelectedRow())} idiom must NOT
 * double-convert.
 */
class JTableSorterCoordinatesTest extends AbstractKaribuTest {

    /** A sorted table paired with the model behind it, since every test asserts on both. */
    private record Fixture(JTable table, DefaultTableModel model) {
    }

    /** Model rows (model order): 0=Charlie/3, 1=Alice/1, 2=Bob/2. */
    private static DefaultTableModel model() {
        return new DefaultTableModel(
                new Object[][] {
                        {"Charlie", 3},
                        {"Alice", 1},
                        {"Bob", 2},
                },
                new Object[] {"name", "n"}) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Integer.class : String.class;
            }
        };
    }

    /** Table sorted ascending by name → view order: Alice(m1), Bob(m2), Charlie(m0). */
    private static Fixture sortedTable() {
        DefaultTableModel m = model();
        JTable table = new JTable(m);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(m);
        table.setRowSorter(sorter);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        // Sanity: the sorter permutation is what we expect.
        assertEquals(1, table.convertRowIndexToModel(0));
        assertEquals(2, table.convertRowIndexToModel(1));
        assertEquals(0, table.convertRowIndexToModel(2));
        return new Fixture(table, m);
    }

    @Test
    @DisplayName("getValueAt is view-indexed under a sorter")
    void getValueAtIsViewIndexed() {
        JTable table = sortedTable().table();
        assertEquals("Alice", table.getValueAt(0, 0));    // view 0 → model 1
        assertEquals("Bob", table.getValueAt(1, 0));      // view 1 → model 2
        assertEquals("Charlie", table.getValueAt(2, 0));  // view 2 → model 0
    }

    @Test
    @DisplayName("getSelectedRow returns a view row — no double-conversion trap")
    void getSelectedRowIsViewIndexed() {
        Fixture f = sortedTable();
        JTable table = f.table();
        table.setRowSelectionInterval(0, 0);              // select the top view row (Alice)
        assertEquals(0, table.getSelectedRow());          // view row, not the model row (1)
        // The idiom migrated code writes: convert the view selection to a model row.
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        assertEquals(1, modelRow);
        assertEquals("Alice", f.model().getValueAt(modelRow, 0));
        // And the view-indexed shortcut agrees.
        assertEquals("Alice", table.getValueAt(table.getSelectedRow(), 0));
    }

    @Test
    @DisplayName("getSelectedRows returns view rows in ascending order")
    void getSelectedRowsAreViewRows() {
        JTable table = sortedTable().table();
        table.setRowSelectionInterval(0, 0);              // Alice → view 0
        table.addRowSelectionInterval(2, 2);              // Charlie → view 2
        assertArrayEquals(new int[] {0, 2}, table.getSelectedRows());
    }

    @Test
    @DisplayName("editing a view row commits to the correct model row")
    void editingAViewRowHitsTheRightModelRow() {
        Fixture f = sortedTable();
        JTable table = f.table();
        DefaultTableModel m = f.model();
        table.editCellAt(0, 0);                           // edit the top view row (Alice = model 1)
        JTextField editor = (JTextField) table.getEditorComponent();
        editor.setText("Alicia");
        editor.postActionEvent();
        assertEquals("Alicia", m.getValueAt(1, 0));       // model row 1 updated
        assertEquals("Charlie", m.getValueAt(0, 0));      // other model rows untouched
        assertEquals("Bob", m.getValueAt(2, 0));
        assertEquals("Alicia", table.getValueAt(0, 0));   // and reads back view-indexed
    }

    @Test
    @DisplayName("isCellEditable and isRowSelected are view-indexed")
    void editabilityAndSelectionAreViewIndexed() {
        JTable table = sortedTable().table();
        table.setRowSelectionInterval(0, 0);
        assertTrue(table.isRowSelected(0));               // view 0 (Alice/model 1) is selected
        assertFalse(table.isRowSelected(2));              // view 2 (Charlie/model 0) is not
        assertTrue(table.isCellEditable(0, 0));
    }

    @Test
    @DisplayName("without a sorter view equals model (identity)")
    void withoutASorterViewEqualsModel() {
        JTable table = new JTable(model());
        assertEquals("Charlie", table.getValueAt(0, 0));  // no sorter → view 0 == model 0
        table.setRowSelectionInterval(1, 1);
        assertEquals(1, table.getSelectedRow());
    }
}
