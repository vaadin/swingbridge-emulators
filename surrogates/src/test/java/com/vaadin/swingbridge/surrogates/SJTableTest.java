/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for SJTable's TableModelListener bridge that aren't
 * covered by another surrogate's exit gate.
 *
 * <p>The selection-clear assertion mirrors real-Swing behaviour: when an
 * AbstractTableModel fires fireTableDataChanged() (TableModelEvent with
 * firstRow=0, lastRow=Integer.MAX_VALUE, type=UPDATE), JTable's
 * sortedTableChanged path treats this as ALL_CHANGED and clears the
 * selection. An R_swing_is_truth-faithful surrogate must do the same so ListSelectionListeners
 * bound to the selection model (e.g. a preview panel that re-reads on
 * selection change) get the corresponding event.
 */
class SJTableTest extends AbstractKaribuTest {

    /** clickCount, rowAtPoint, columnAtPoint — Java's stand-in for the Kotlin Triple. */
    private record Click(int clickCount, int row, int column) {
    }

    @Test
    @DisplayName("fireTableDataChanged clears selection and notifies listeners")
    void fireTableDataChangedClearsSelectionAndNotifiesListeners() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"a"}, {"b"}, {"c"}}, new Object[]{"col"});
        SJTable table = new SJTable(model);

        table.getListSelectionModel().setSelectionInterval(1, 1);
        assertEquals(1, table.getSelectedRow());

        List<ListSelectionEvent> events = new ArrayList<>();
        table.getListSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                events.add(e);
            }
        });

        model.fireTableDataChanged();

        assertEquals(-1, table.getSelectedRow(), "fireTableDataChanged must clear selection (JDK parity)");
        assertTrue(table.getListSelectionModel().isSelectionEmpty());
        assertFalse(events.isEmpty(), "ListSelectionListeners must be notified of the cleared selection");
    }

    @Test
    @DisplayName("fireTableRowsUpdated for subset preserves selection")
    void fireTableRowsUpdatedForSubsetPreservesSelection() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"a"}, {"b"}, {"c"}}, new Object[]{"col"});
        SJTable table = new SJTable(model);

        table.getListSelectionModel().setSelectionInterval(1, 1);
        assertEquals(1, table.getSelectedRow());

        model.fireTableRowsUpdated(1, 1);

        assertEquals(1, table.getSelectedRow(), "subset UPDATE must not clear selection");
    }

    @Test
    @DisplayName("no-counterpart knobs drop on the surrogate (emulator owns round-trip)")
    void noCounterpartKnobsDropOnTheSurrogate() {
        // R_vaadin_first cleanup: the surrogate is Vaadin-first, no shadow cache. These
        // setters log-and-drop; getters return JDK defaults. The emulator
        // (:emulators.JTable) owns the JDK-faithful round-trip per R_swing_is_truth.
        SJTable table = new SJTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        table.setRowHeight(40);
        table.setGridColor(Color.RED);
        table.setShowGrid(false);
        table.setDragEnabled(true);
        table.setSelectionForeground(Color.BLUE);
        table.setAutoResizeMode(SJTable.AUTO_RESIZE_OFF);
        table.setRowSelectionAllowed(false);

        assertEquals(16, table.getRowHeight());
        assertEquals(Color.GRAY, table.getGridColor());
        assertTrue(table.getShowGrid());
        assertFalse(table.getDragEnabled());
        assertNull(table.getSelectionForeground());
        assertEquals(SJTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS, table.getAutoResizeMode());
        assertTrue(table.getRowSelectionAllowed());
    }

    @Test
    @DisplayName("jdkSelectionMode is reconstructed from the selection model (no shadow)")
    void jdkSelectionModeIsReconstructedFromTheSelectionModel() {
        SJTable table = new SJTable(new DefaultTableModel(new Object[][]{{"a"}}, new Object[]{"col"}));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assertEquals(ListSelectionModel.SINGLE_SELECTION, table.getJdkSelectionMode());
        assertEquals(ListSelectionModel.SINGLE_SELECTION, table.getListSelectionModel().getSelectionMode());
        // A fresh model (default MULTIPLE_INTERVAL) is reflected immediately.
        table.setSelectionModel(new DefaultListSelectionModel());
        assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, table.getJdkSelectionMode());
    }

    /** Model order: 0=Charlie, 1=Alice, 2=Bob, 3=Dave. */
    private static DefaultTableModel names() {
        return new DefaultTableModel(
                new Object[][]{{"Charlie"}, {"Alice"}, {"Bob"}, {"Dave"}}, new Object[]{"name"});
    }

    /** The names the Grid shows, top to bottom — each Grid item is a model row. */
    private static List<Object> shownNames(SJTable table) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < GridKt._size(table); i++) {
            out.add(table.getModel().getValueAt(GridKt._get(table, i), 0));
        }
        return out;
    }

    @Test
    @DisplayName("a model that shrinks under an unsorted row sorter shrinks the Grid")
    void shrinkingModelShrinksTheGridUnderAnUnsortedSorter() {
        List<String> rows = new ArrayList<>(List.of("a", "b", "c", "d"));
        AbstractTableModel model = new AbstractTableModel() {
            @Override public int getRowCount() { return rows.size(); }
            @Override public int getColumnCount() { return 1; }
            @Override public Object getValueAt(int row, int column) { return rows.get(row); }
        };
        SJTable table = new SJTable(model);
        table.setAutoCreateRowSorter(true);
        assertEquals(4, GridKt._size(table));

        // The shape a filtering or deleting app uses: replace the data, fire a whole-model change.
        rows.subList(2, 4).clear();
        model.fireTableDataChanged();

        assertEquals(2, GridKt._size(table), "the sorter must hear the change, or it keeps counting 4 rows");
        assertEquals(2, table.getRowSorter().getViewRowCount());
    }

    @Test
    @DisplayName("rows deleted and inserted after a sort land in sorted order")
    void rowsDeletedAndInsertedAfterASortStaySorted() {
        DefaultTableModel model = names();
        SJTable table = new SJTable(model);
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(0, SortOrder.ASCENDING)));
        assertEquals(List.of("Alice", "Bob", "Charlie", "Dave"), shownNames(table));

        model.removeRow(1);                            // Alice
        assertEquals(List.of("Bob", "Charlie", "Dave"), shownNames(table));

        model.addRow(new Object[]{"Aaron"});
        assertEquals(List.of("Aaron", "Bob", "Charlie", "Dave"), shownNames(table));
    }

    @Test
    @DisplayName("fireTableDataChanged parks the selection's lead and anchor at -1")
    void fireTableDataChangedResetsLeadAndAnchor() {
        DefaultTableModel model = names();
        SJTable table = new SJTable(model);
        table.getListSelectionModel().setSelectionInterval(1, 2);
        assertEquals(2, table.getListSelectionModel().getLeadSelectionIndex());

        model.fireTableDataChanged();

        assertEquals(-1, table.getListSelectionModel().getLeadSelectionIndex());
        assertEquals(-1, table.getListSelectionModel().getAnchorSelectionIndex());
    }

    @Test
    @DisplayName("double-click + single-click bridge resolves rowAtPoint and columnAtPoint")
    void doubleClickPlusSingleClickBridgeResolvesRowAtPointAndColumnAtPoint() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"a", "b"}, {"c", "d"}}, new Object[]{"X", "Y"});
        SJTable table = new SJTable(model);
        UI.getCurrent().add(table);
        List<Click> seen = new ArrayList<>();
        table.addMouseListener(new SMouseAdapter() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                seen.add(new Click(e.getClickCount(),
                        table.rowAtPoint(new Point(0, 0)), table.columnAtPoint(new Point(0, 0))));
            }
        });
        // Single click on row 1, column Y (view col 1) → clickCount 1, row 1, col 1.
        GridKt._clickItem(table, 1, table.getColumns().get(1), 1, false, false, false, false);
        // Double click on row 0 → clickCount 2, row 0.
        GridKt._doubleClickItem(table, 0, 1, false, false, false, false);
        assertEquals(2, seen.size());
        assertEquals(new Click(1, 1, 1), seen.get(0));
        assertEquals(2, seen.get(1).clickCount(), "double-click must surface clickCount=2");
        assertEquals(0, seen.get(1).row(), "rowAtPoint must resolve to the clicked row");
        // Stash cleared after the dispatch.
        assertEquals(-1, table.rowAtPoint(new Point(0, 0)));
        assertEquals(-1, table.columnAtPoint(new Point(0, 0)));
    }
}
