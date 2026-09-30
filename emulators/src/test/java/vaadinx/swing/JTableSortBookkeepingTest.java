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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.component.UI;
import com.vaadin.swingbridge.surrogates.SJTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import javax.swing.DefaultListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.RowSorterEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_jtable_selection — the emulator {@link JTable}'s sort bookkeeping, as the JDK's
 * {@code SortManager} does it: the selection model holds <i>view</i> rows and is re-mapped
 * through the sorter on a re-sort and on a model change, the editing row follows its row the
 * same way, and {@code sorterChanged} runs for every sorter — including the one
 * {@code autoCreateRowSorter} installs.
 */
class JTableSortBookkeepingTest extends AbstractKaribuTest {

    /** Model rows (model order): 0=Charlie, 1=Alice, 2=Bob. */
    private static DefaultTableModel model() {
        return new DefaultTableModel(
                new Object[][] {{"Charlie"}, {"Alice"}, {"Bob"}}, new Object[] {"name"});
    }

    /** Auto-sorted ascending by name → view order: Alice(m1), Bob(m2), Charlie(m0). */
    private static JTable sortedTable(TableModel m) {
        JTable table = new JTable(m);
        table.setAutoCreateRowSorter(true);
        sortBy(table, SortOrder.ASCENDING);
        return table;
    }

    private static void sortBy(JTable table, SortOrder order) {
        table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, order)));
    }

    /** The surrogate, whose selection holds model rows — what the Grid shows. */
    private static SJTable peer(JTable table) {
        return (SJTable) table.getPeer();
    }

    private static List<ListSelectionEvent> finalSelectionEvents(JTable table) {
        List<ListSelectionEvent> events = new ArrayList<>();
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                events.add(e);
            }
        });
        return events;
    }

    // --- sorterChanged reaches every sorter (D_jtable_selection) ---

    @Test
    @DisplayName("sorterChanged runs for the sorter autoCreateRowSorter installs")
    void sorterChangedRunsForAnAutoCreatedSorter() {
        List<RowSorterEvent> seen = new ArrayList<>();
        JTable table = new JTable(model()) {
            @Override
            public void sorterChanged(RowSorterEvent e) {
                seen.add(e);
                super.sorterChanged(e);
            }
        };
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        assertFalse(seen.isEmpty(), "a subclass's sorterChanged override must be on the path");
    }

    @Test
    @DisplayName("setAutoCreateRowSorter(true) installs through setRowSorter, as the JDK's does")
    void autoCreateGoesThroughSetRowSorter() {
        JTable table = new JTable(model());
        List<String> fired = new ArrayList<>();
        table.addPropertyChangeListener(e -> fired.add(e.getPropertyName()));
        table.setAutoCreateRowSorter(true);
        assertNotNull(table.getRowSorter());
        assertEquals(List.of("rowSorter", "sorter", "autoCreateRowSorter"), fired);
    }

    @Test
    @DisplayName("setModel re-creates the auto sorter over the new model")
    void setModelRecreatesTheAutoSorter() {
        JTable table = new JTable(model());
        table.setAutoCreateRowSorter(true);
        RowSorter<? extends TableModel> first = table.getRowSorter();
        DefaultTableModel next = model();
        table.setModel(next);
        assertNotSame(first, table.getRowSorter());
        assertSame(next, table.getRowSorter().getModel());
    }

    // --- the selection model holds view rows (D_jtable_selection) ---

    @Test
    @DisplayName("the selection model holds view rows under a sorter; the Grid holds the model row")
    void selectionModelHoldsViewRows() {
        JTable table = sortedTable(model());
        table.setRowSelectionInterval(0, 0);                          // Alice
        assertEquals(0, table.getSelectionModel().getMinSelectionIndex());
        assertEquals(Set.of(1), peer(table).getSelectedItems());
    }

    @Test
    @DisplayName("a re-sort keeps the selection on its model row, and tells the listeners")
    void reSortKeepsTheSelectionOnItsRow() {
        JTable table = sortedTable(model());
        table.setRowSelectionInterval(0, 0);                          // Alice, view 0
        List<ListSelectionEvent> events = finalSelectionEvents(table);

        sortBy(table, SortOrder.DESCENDING);                          // Charlie, Bob, Alice

        assertEquals(2, table.getSelectedRow());
        assertEquals("Alice", table.getValueAt(table.getSelectedRow(), 0));
        assertFalse(events.isEmpty(), "the JDK re-selects the moved row, which fires");
        assertEquals(Set.of(1), peer(table).getSelectedItems());
    }

    @Test
    @DisplayName("a deleted row above the selection shifts it; deleting the selected row drops it")
    void modelDeletesShiftOrDropTheSelection() {
        DefaultTableModel m = model();
        JTable table = sortedTable(m);
        table.setRowSelectionInterval(1, 1);                          // Bob (m2)

        m.removeRow(1);                                               // Alice → view: Bob, Charlie
        assertEquals(0, table.getSelectedRow());
        assertEquals("Bob", table.getValueAt(0, 0));
        assertEquals(Set.of(1), peer(table).getSelectedItems());      // Bob is model row 1 now

        m.removeRow(1);                                               // Bob
        assertEquals(-1, table.getSelectedRow());
        assertTrue(peer(table).getSelectedItems().isEmpty());
    }

    @Test
    @DisplayName("a browser selection arrives in the selection model as a view row, once")
    void browserSelectionArrivesAsAViewRow() {
        JTable table = sortedTable(model());
        List<ListSelectionEvent> events = finalSelectionEvents(table);
        peer(table).select(2);                                        // the Grid selects model row 2, Bob
        assertEquals(1, table.getSelectedRow());
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("a selection model you install holds view rows")
    void installedSelectionModelHoldsViewRows() {
        JTable table = sortedTable(model());
        DefaultListSelectionModel mine = new DefaultListSelectionModel();
        table.setSelectionModel(mine);
        table.setRowSelectionInterval(2, 2);                          // Charlie, model row 0
        assertTrue(mine.isSelectedIndex(2));
        assertEquals(Set.of(0), peer(table).getSelectedItems());
    }

    @Test
    @DisplayName("setRowSelectionInterval past the last row throws IllegalArgumentException")
    void outOfRangeSelectionThrows() {
        JTable table = sortedTable(model());
        assertThrows(IllegalArgumentException.class, () -> table.setRowSelectionInterval(0, 3));
    }

    // --- the editing row follows its row (D_jtable_selection) ---

    @Test
    @DisplayName("a re-sort under an open editor moves the editing row, and the commit lands on its row")
    void reSortMovesTheEditingRow() {
        DefaultTableModel m = model();
        JTable table = sortedTable(m);
        table.editCellAt(0, 0);                                       // Alice (m1)

        sortBy(table, SortOrder.DESCENDING);                          // Charlie, Bob, Alice
        assertTrue(table.isEditing());
        assertEquals(2, table.getEditingRow());

        JTextField field = (JTextField) table.getEditorComponent();
        field.setText("Alicia");
        field.postActionEvent();
        assertEquals("Alicia", m.getValueAt(1, 0));
        assertEquals("Charlie", m.getValueAt(0, 0));
    }

    @Test
    @DisplayName("deleting the edited row cancels the edit without committing")
    void deletingTheEditedRowCancelsTheEdit() {
        DefaultTableModel m = model();
        JTable table = sortedTable(m);
        table.editCellAt(0, 0);                                       // Alice (m1)
        ((JTextField) table.getEditorComponent()).setText("typed");

        m.removeRow(1);

        assertFalse(table.isEditing());
        assertEquals(-1, table.getEditingRow());
        assertEquals(List.of("Charlie", "Bob"), List.of(m.getValueAt(0, 0), m.getValueAt(1, 0)));
    }

    @Test
    @DisplayName("deleting a row above the edited one keeps the edit open on the shifted row")
    void deletingARowAboveKeepsTheEditOnItsRow() {
        DefaultTableModel m = model();
        JTable table = sortedTable(m);
        UI.getCurrent().add(table.getPeer());
        table.editCellAt(1, 0);                                       // Bob (m2)
        MockVaadin.clientRoundtrip();                                 // the Grid editor opens
        assertEquals(2, peer(table).getEditor().getItem());

        m.removeRow(0);                                               // Charlie → Bob is model row 1
        MockVaadin.clientRoundtrip();                                 // the editor re-opens on it

        assertTrue(table.isEditing(), "the data change that closed the Grid editor must not end the edit");
        assertEquals(1, table.getEditingRow());                       // view: Alice, Bob
        assertEquals(1, peer(table).getEditor().getItem());
        JTextField field = (JTextField) table.getEditorComponent();
        field.setText("Robert");
        field.postActionEvent();
        assertEquals("Robert", m.getValueAt(1, 0));
        assertEquals("Alice", m.getValueAt(0, 0));
    }
}
