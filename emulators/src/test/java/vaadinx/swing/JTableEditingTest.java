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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.ShortcutsKt;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.DataChangeEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.table.TableCellEditor;

import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.table.DefaultTableModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Slice 2a of the JTable editing pass (D_jtable_cell_editing): the Grid.Editor bridge —
 * single-column editing driven through the Swing editor lifecycle (empty
 * placeholder Binder; value via getTableCellEditorComponent / getCellEditorValue).
 * Commit is exercised through the field's own Enter ({@code postActionEvent}), which
 * fires {@code editingStopped} → {@code setValueAt}; the double-click trigger is the next slice.
 */
class JTableEditingTest extends AbstractKaribuTest {

    private static JTable stringTable() {
        return new JTable(new DefaultTableModel(
                new Object[][] {{"a", "b"}}, new Object[] {"c0", "c1"}));
    }

    private static JTable intTable() {
        return new JTable(new DefaultTableModel(
                new Object[][] {{1, 2}}, new Object[] {"c0", "c1"}) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return Integer.class;
            }
        });
    }

    /** The table's Grid peer, item-typed as the Grid helpers want it. */
    @SuppressWarnings("unchecked")
    private static Grid<Object> gridOf(JTable table) {
        return (Grid<Object>) table.getPeer();
    }

    /** Items reported through a DataRefreshEvent, in order — the Task-2 protection probe. */
    private static List<Object> recordRefreshes(Grid<Object> grid) {
        List<Object> refreshed = new ArrayList<>();
        grid.getDataProvider().addDataProviderListener(ev -> {
            if (ev instanceof DataChangeEvent.DataRefreshEvent<?> refresh) {
                refreshed.add(refresh.getItem());
            }
        });
        return refreshed;
    }

    @Test
    @DisplayName("editCellAt opens editing on the clicked column and fills the whole row")
    void editCellAtFillsTheWholeRow() {
        JTable table = stringTable();
        Grid<Object> grid = gridOf(table);
        assertTrue(table.editCellAt(0, 1, null));
        assertTrue(table.isEditing());
        assertEquals(0, table.getEditingRow());
        assertEquals(1, table.getEditingColumn());
        assertNotNull(table.getEditorComponent());
        // B2/B4: every column of the edited row gets an editor component (clicked =
        // real editor, others = read-only display) so no cell blanks out.
        assertNotNull(grid.getColumns().get(0).getEditorComponent());
        assertNotNull(grid.getColumns().get(1).getEditorComponent());
        // All cleared on teardown.
        table.removeEditor();
        assertNull(grid.getColumns().get(0).getEditorComponent());
        assertNull(grid.getColumns().get(1).getEditorComponent());
    }

    @Test
    @DisplayName("commit via field Enter writes back through setValueAt")
    void enterCommitsThroughSetValueAt() {
        JTable table = stringTable();
        table.editCellAt(0, 0);
        JTextField field = (JTextField) table.getEditorComponent();
        field.setText("edited");
        field.postActionEvent();                     // Enter → editingStopped → commit
        assertEquals("edited", table.getValueAt(0, 0));
        assertFalse(table.isEditing());
    }

    @Test
    @DisplayName("Integer column coerces the typed String on commit")
    void integerColumnCoercesOnCommit() {
        JTable table = intTable();
        table.editCellAt(0, 0);
        JTextField field = (JTextField) table.getEditorComponent();
        field.setText("42");
        field.postActionEvent();
        assertEquals(Integer.valueOf(42), table.getValueAt(0, 0));    // Integer, coerced
        assertFalse(table.isEditing());
    }

    @Test
    @DisplayName("editCellAt refuses a non-editable cell")
    void editCellAtRefusesNonEditable() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a"}}, new Object[] {"c0"}) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        });
        assertFalse(table.editCellAt(0, 0, null));
        assertFalse(table.isEditing());
    }

    @Test
    @DisplayName("removeEditor discards without writing back and clears the editor component")
    void removeEditorDiscards() {
        JTable table = stringTable();
        Grid<Object> grid = gridOf(table);
        table.editCellAt(0, 0);
        ((JTextField) table.getEditorComponent()).setText("discardme");
        table.removeEditor();
        assertEquals("a", table.getValueAt(0, 0));   // unchanged
        assertFalse(table.isEditing());
        assertNull(grid.getColumns().get(0).getEditorComponent());  // cleared on cleanup
    }

    @Test
    @DisplayName("single click starts a clickCountToStart-1 editor but not a text cell")
    void singleClickStartsOnlyClickCountOneEditors() {
        JTable table = stringTable();
        Grid<Object> grid = gridOf(table);

        // Column 0 gets a combo editor (clickCountToStart == 1): single click edits.
        table.getColumnModel().getColumn(0).setCellEditor(
                new DefaultCellEditor(new JComboBox<>(new String[] {"a", "x"})));
        GridKt._clickItem(grid, 0, grid.getColumns().get(0));
        assertTrue(table.isEditing());
        assertEquals(0, table.getEditingColumn());
        table.removeEditor();

        // Column 1 is a plain text cell (GenericEditor, clickCountToStart == 2):
        // single click does NOT edit.
        GridKt._clickItem(grid, 0, grid.getColumns().get(1));
        assertFalse(table.isEditing());
    }

    @Test
    @DisplayName("single click on a Boolean cell opens no editor — it self-edits via a live checkbox (B3)")
    void booleanCellSelfEdits() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{true}}, new Object[] {"flag"}) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return Boolean.class;
            }
        });
        Grid<Object> grid = gridOf(table);
        GridKt._clickItem(grid, 0, grid.getColumns().get(0));
        // Boolean columns are edited by their live checkbox render, not Grid.Editor,
        // so a click must NOT open the (racy) editor.
        assertFalse(table.isEditing());
    }

    @Test
    @DisplayName("double-click on a specific column starts text editing")
    void doubleClickStartsTextEditing() {
        // Verifies the double-click → edit path for a clickCountToStart==2 (text) cell,
        // which needs a real column on the double-click event — hence the column overload
        // rather than the bare _doubleClickItem(row), which reports a null column.
        JTable table = stringTable();
        Grid<Object> grid = gridOf(table);
        UI.getCurrent().add(grid);
        GridKt._doubleClickItem(grid, 0, grid.getColumns().get(1));
        assertTrue(table.isEditing());
        assertEquals(1, table.getEditingColumn());
    }

    @Test
    @DisplayName("switching cells commits the in-flight edit, not discards it (B6)")
    void switchingCellsCommits() {
        JTable table = stringTable();
        table.editCellAt(0, 0, null);
        ((JTextField) table.getEditorComponent()).setText("committed");
        table.editCellAt(0, 1, null);                 // switch → must commit col 0, not discard
        assertEquals("committed", table.getValueAt(0, 0));
        assertEquals(1, table.getEditingColumn());
    }

    @Test
    @DisplayName("Escape cancels an open editor without committing (B9)")
    void escapeCancelsWithoutCommitting() {
        JTable table = stringTable();
        UI.getCurrent().add(table.getPeer());          // shortcut needs the Grid attached
        table.editCellAt(0, 0);
        JTextField field = (JTextField) table.getEditorComponent();
        field.setText("changed");
        assertTrue(table.isEditing());
        // The B9 fix registers an Escape shortcut listenOn(grid); fire it.
        ShortcutsKt._fireShortcut(table.getPeer(), Key.ESCAPE);
        assertFalse(table.isEditing());                // editor torn down
        assertEquals("a", table.getValueAt(0, 0));     // reverted — NOT committed
    }

    @Test
    @DisplayName("Escape fires editingCanceled on the cell editor (B9)")
    void escapeFiresEditingCanceled() {
        JTable table = stringTable();
        UI.getCurrent().add(table.getPeer());
        TableCellEditor editor = table.getCellEditor(0, 0);
        List<ChangeEvent> canceled = new ArrayList<>();
        editor.addCellEditorListener(new CellEditorListener() {
            @Override
            public void editingStopped(ChangeEvent e) {
            }

            @Override
            public void editingCanceled(ChangeEvent e) {
                canceled.add(e);
            }
        });
        table.editCellAt(0, 0);
        ShortcutsKt._fireShortcut(table.getPeer(), Key.ESCAPE);
        assertEquals(1, canceled.size());           // faithful: CellEditorListeners see the cancel
    }

    @Test
    @DisplayName("external setValueAt on the actively-edited row does not refresh it (Task 2)")
    void editedRowIsProtectedFromRefresh() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][] {{"a"}, {"b"}, {"c"}}, new Object[] {"c0"});
        JTable table = new JTable(model);
        List<Object> refreshed = recordRefreshes(gridOf(table));

        table.editCellAt(1, 0);                      // open editor on row 1 → protected
        refreshed.clear();

        model.setValueAt("B!", 1, 0);                // external update on the edited row
        model.setValueAt("C!", 2, 0);                // external update on a different row

        assertFalse(refreshed.contains(1), "edited row must not be refreshed under the open editor");
        assertTrue(refreshed.contains(2), "non-edited rows still refresh normally");
        assertTrue(table.isEditing());               // editor survived
    }

    @Test
    @DisplayName("protection is released after the edit ends (Task 2)")
    void protectionIsReleasedAfterTheEdit() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][] {{"a"}, {"b"}}, new Object[] {"c0"});
        JTable table = new JTable(model);
        List<Object> refreshed = recordRefreshes(gridOf(table));

        table.editCellAt(0, 0);
        table.removeEditor();                        // ends the edit → protection cleared
        refreshed.clear();

        model.setValueAt("A!", 0, 0);                // now the row refreshes normally again
        assertTrue(refreshed.contains(0));
    }

    @Test
    @DisplayName("starting a new edit switches the edited column and re-fills the row")
    void newEditSwitchesTheColumn() {
        JTable table = stringTable();
        Grid<Object> grid = gridOf(table);
        table.editCellAt(0, 0);
        assertEquals(0, table.getEditingColumn());
        table.editCellAt(0, 1);          // switch the edited column
        assertEquals(1, table.getEditingColumn());
        assertTrue(table.isEditing());
        // Row stays fully filled; only the edited column changed.
        assertNotNull(grid.getColumns().get(0).getEditorComponent());
        assertNotNull(grid.getColumns().get(1).getEditorComponent());
    }
}
