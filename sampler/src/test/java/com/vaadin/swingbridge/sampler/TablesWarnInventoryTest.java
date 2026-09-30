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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JTable;
import vaadinx.swing.table.DefaultTableCellRenderer;
import vaadinx.swing.table.DefaultTableColumnModel;
import vaadinx.swing.table.JTableHeader;
import vaadinx.swing.table.TableCellRenderer;
import vaadinx.swing.table.TableColumn;
import vaadinx.swing.table.TableColumnModel;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

/**
 * TablesView (JTable demo) WARN inventory exit gate. Both
 * tests fail if any {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape}
 * fires.
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — drives the route end-to-end:
 *       click "Add row" → programmatic selection → click "Promote
 *       selected" → click "Remove selected". Exercises the
 *       AbstractTableModel mutation surface, the renderer-bridge
 *       {@code dataProvider.refreshItem} path, and the selection-model
 *       wiring.</li>
 *   <li>{@link #inventory_jtable_api_surface} — micro-driver over the
 *       JTable surface: ctors, model swap + PCE, selection mode round-
 *       trip, default renderer registry, column manipulation,
 *       view↔model index conversion, JTableHeader accessors, all the
 *       round-trip-only shadow getters/setters.</li>
 * </ol>
 */
class TablesWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Tables");
        dump("Step 0b (TablesPanel swap)", warnings);

        // The Sampler's content area now hosts a TablesPanel; reach the
        // emulator JTable via its SJTable peer (Karibu locates the
        // surrogate as Grid<Integer>) → EHelper.getEmulator → JTable.
        // The model is then accessible via JTable.getModel and downcast
        // to TablesPanel's package-private PersonTableModel.
        com.vaadin.swingbridge.surrogates.SJTable sTable = LocatorJ._get(com.vaadin.swingbridge.surrogates.SJTable.class);
        JTable table = (JTable) vaadinx.EHelper.getEmulator(sTable);
        TablesPanel.PersonTableModel model =
                (TablesPanel.PersonTableModel) table.getModel();

        // Click "Add row" — appends to the model, fires tableRowsInserted,
        // surrogate's tableChanged INSERT branch pushes items to the peer.
        Button addBtn = LocatorJ._get(Button.class, spec -> spec.withText("Add row"));
        LocatorJ._click(addBtn);
        dump("Step 1 (add row)", warnings);

        // Programmatically select row 0 — simulates a user clicking the
        // grid row. The selection-model listener fires valueChanged, which
        // updates the status Span and pushes selection to the Grid.
        table.setRowSelectionInterval(0, 0);
        dump("Step 2 (select row 0)", warnings);

        // Double-click row 0 to "open" it — exercises the item-double-click
        // → MouseEvent bridge (clickCount=2) + rowAtPoint/columnAtPoint
        // resolution in TablesPanel's handler (D_jtable/SD_sjtable backport, (b)).
        GridKt._doubleClickItem(sTable, 0, 1, false, false, false, false);
        dump("Step 2b (double-click row 0 to open)", warnings);

        // Click "Promote selected" — reads the selected row, calls
        // model.setValueAt → fireTableCellUpdated → surrogate UPDATE
        // branch refreshes that single row via dataProvider.refreshItem.
        Button promoteBtn = LocatorJ._get(Button.class, spec -> spec.withText("Promote selected"));
        LocatorJ._click(promoteBtn);
        dump("Step 3 (promote selected)", warnings);

        // Click "Remove selected" — model.removePerson → fireTableRowsDeleted
        // → surrogate DELETE branch adjusts selection + re-pushes items.
        Button removeBtn = LocatorJ._get(Button.class, spec -> spec.withText("Remove selected"));
        LocatorJ._click(removeBtn);
        dump("Step 4 (remove selected)", warnings);

        // Programmatic sort path via the "Sort by Name" button. The
        // table is constructed with autoCreateRowSorter=true so a fresh
        // TableRowSorter is already installed; toggleSortOrder(0) fires
        // RowSorterEvent → bridge calls grid.sort(translated) → Vaadin
        // re-fetches → fetch's compare-then-set is a no-op (rowSorter
        // already has the keys). convertRowIndexToView/Model route
        // through the rowSorter on subsequent reads.
        Button sortByNameBtn = LocatorJ._get(Button.class, spec -> spec.withText("Sort by Name"));
        LocatorJ._click(sortByNameBtn);
        dump("Step 5 (sort by name — first toggle, ASC)", warnings);

        // Second click: ASC → DESC. Same bridge flow.
        LocatorJ._click(sortByNameBtn);
        dump("Step 6 (sort by name — second toggle, DESC)", warnings);

        // "Clear sort" button: rowSorter.setSortKeys(empty) → bridge
        // calls grid.sort(emptyList), Vaadin clears its sort indicator.
        Button clearSortBtn = LocatorJ._get(Button.class, spec -> spec.withText("Clear sort"));
        LocatorJ._click(clearSortBtn);
        dump("Step 7 (clear sort)", warnings);

        // Filter path: type into the filter TextField → sorter.setRowFilter
        // → RowSorterEvent SORTED with unchanged sort orders → bridge falls
        // through to dataProvider.refreshAll() → Vaadin re-fetches with
        // rowSorter.getViewRowCount() reflecting the filter.
        com.vaadin.flow.component.textfield.TextField filterField =
                LocatorJ._get(com.vaadin.flow.component.textfield.TextField.class);
        // Filter for "Alan" — earlier steps removed Ada (selected → remove
        // in step 4 took her out), so she's no longer in the model; Alan
        // is still present and matches uniquely.
        com.github.mvysny.kaributesting.v10.LocatorJ._setValue(filterField, "Alan");
        if (table.getRowSorter().getViewRowCount() != 1) {
            throw new AssertionError(
                    "Expected 1 view row matching 'Alan', got "
                            + table.getRowSorter().getViewRowCount());
        }
        dump("Step 8 (filter by 'Alan')", warnings);

        // Clear filter — empty TextField value triggers setRowFilter(null).
        com.github.mvysny.kaributesting.v10.LocatorJ._setValue(filterField, "");
        if (table.getRowSorter().getViewRowCount() != model.getRowCount()) {
            throw new AssertionError(
                    "After clearing filter, view row count should equal model row count, got "
                            + table.getRowSorter().getViewRowCount() + " vs "
                            + model.getRowCount());
        }
        dump("Step 9 (clear filter)", warnings);

        // In-cell editing (D_jtable_cell_editing). Edit the Name cell (col 0, text) in
        // place and commit via the field's Enter — confirm it wrote back through
        // the model. Then open the Profession JComboBox editor (col 1) and the
        // Active checkbox editor (col 2) and tear them down — exercising the
        // text / combo / checkbox editor paths under the WARN inventory.
        table.editCellAt(0, 0, null);
        vaadinx.swing.JTextField cellEditor = (vaadinx.swing.JTextField) table.getEditorComponent();
        cellEditor.setText("Edited Name");
        cellEditor.postActionEvent(); // Enter → editingStopped → setValueAt
        if (!"Edited Name".equals(table.getValueAt(0, 0))) {
            throw new AssertionError("In-cell edit did not commit: " + table.getValueAt(0, 0));
        }
        dump("Step 10 (in-cell edit Name → commit)", warnings);

        table.editCellAt(0, 1, null); // Profession → JComboBox editor
        if (!table.isEditing()) throw new AssertionError("Profession combo editor did not open");
        table.removeEditor();
        dump("Step 11 (open + close Profession combo editor)", warnings);

        table.editCellAt(0, 2, null); // Active → Boolean checkbox editor
        if (!table.isEditing()) throw new AssertionError("Active checkbox editor did not open");
        table.removeEditor();
        dump("Step 12 (open + close Active checkbox editor)", warnings);

        WarnDump.println();
        WarnDump.println("=== tables user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jtable_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Fixture: no-arg JTable --
        JTable jt = new JTable();
        dump("JTable  new JTable()", warnings);

        // -- Ctors taking various model combinations --
        DefaultTableModel dm = new DefaultTableModel(
                new Object[][] { { "a", 1 }, { "b", 2 } },
                new Object[] { "X", "Y" });
        new JTable(dm);
        dump("JTable  new JTable(TableModel)", warnings);

        TableColumnModel cm = new DefaultTableColumnModel();
        TableColumn c0 = new TableColumn(0);
        c0.setHeaderValue("Col0");
        cm.addColumn(c0);
        TableColumn c1 = new TableColumn(1);
        c1.setHeaderValue("Col1");
        cm.addColumn(c1);
        new JTable(dm, cm);
        dump("JTable  new JTable(TableModel, TableColumnModel)", warnings);

        ListSelectionModel sm = new DefaultListSelectionModel();
        new JTable(dm, cm, sm);
        dump("JTable  new JTable(TableModel, TableColumnModel, ListSelectionModel)", warnings);

        new JTable(3, 4);
        dump("JTable  new JTable(int, int)", warnings);

        new JTable(new Object[][] { { "a", 1 }, { "b", 2 } }, new Object[] { "X", "Y" });
        dump("JTable  new JTable(Object[][], Object[])", warnings);

        // -- Model swap + PCE --
        jt.setModel(dm);
        jt.getModel();
        dump("JTable  setModel + getModel", warnings);

        // -- Column model swap --
        TableColumnModel cm2 = new DefaultTableColumnModel();
        TableColumn cc0 = new TableColumn(0);
        cc0.setHeaderValue("Alpha");
        cm2.addColumn(cc0);
        TableColumn cc1 = new TableColumn(1);
        cc1.setHeaderValue("Beta");
        cm2.addColumn(cc1);
        jt.setColumnModel(cm2);
        jt.getColumnModel();
        dump("JTable  setColumnModel + getColumnModel", warnings);

        // -- Selection model swap --
        ListSelectionModel sm2 = new DefaultListSelectionModel();
        jt.setSelectionModel(sm2);
        jt.getSelectionModel();
        dump("JTable  setSelectionModel + getSelectionModel", warnings);

        // -- Selection mode round-trip --
        jt.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jt.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        jt.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        dump("JTable  setSelectionMode round-trip", warnings);

        // -- Selection state --
        jt.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        jt.setRowSelectionInterval(0, 1);
        jt.getSelectedRow();
        jt.getSelectedRows();
        jt.getSelectedRowCount();
        jt.isRowSelected(0);
        jt.addRowSelectionInterval(0, 0);
        jt.removeRowSelectionInterval(1, 1);
        jt.clearSelection();
        jt.selectAll();
        dump("JTable  selection state accessors", warnings);

        // -- Cell access --
        jt.getRowCount();
        jt.getColumnCount();
        jt.getColumnName(0);
        jt.getColumnClass(0);
        jt.getValueAt(0, 0);
        jt.setValueAt("z", 0, 0);
        jt.isCellEditable(0, 0);
        dump("JTable  cell access", warnings);

        // -- View ↔ model index conversion --
        jt.convertColumnIndexToModel(0);
        jt.convertColumnIndexToView(0);
        jt.convertRowIndexToModel(0);
        jt.convertRowIndexToView(0);
        dump("JTable  index conversion", warnings);

        // -- Default renderer registry round-trip --
        TableCellRenderer originalObjectRenderer = jt.getDefaultRenderer(Object.class);
        DefaultTableCellRenderer custom = new DefaultTableCellRenderer();
        jt.setDefaultRenderer(Number.class, custom);
        TableCellRenderer fetched = jt.getDefaultRenderer(Integer.class);  // walks superclass
        if (fetched != custom) {
            // Not strictly an emulator-WARN concern, but ensures the
            // superclass walk works as documented.
            throw new AssertionError(
                    "default renderer superclass walk failed: expected " + custom + " got " + fetched);
        }
        jt.setDefaultRenderer(Number.class, null);  // remove
        if (originalObjectRenderer == null) {
            throw new AssertionError("Object.class default renderer should be seeded by ctor");
        }
        dump("JTable  default renderer registry round-trip", warnings);

        // -- Per-column TableCellRenderer install --
        TableColumn firstCol = jt.getColumnModel().getColumn(0);
        firstCol.setCellRenderer(custom);
        jt.getCellRenderer(0, 0);
        firstCol.setCellRenderer(null);  // back to default-by-class
        dump("JTable  per-column TableCellRenderer install", warnings);

        // -- Column manipulation --
        TableColumn newCol = new TableColumn(0);
        jt.addColumn(newCol);
        jt.getColumn(newCol.getIdentifier());
        jt.removeColumn(newCol);
        dump("JTable  column manipulation", warnings);

        // -- Auto-create flags --
        jt.getAutoCreateColumnsFromModel();
        jt.setAutoCreateColumnsFromModel(true);
        jt.getAutoCreateRowSorter();
        jt.setAutoCreateRowSorter(false);  // start from no-auto state
        jt.setRowSorter(null);              // clear any prior sorter
        if (jt.getRowSorter() != null) {
            throw new AssertionError("setRowSorter(null) should clear");
        }
        // Flip autoCreate on — JDK lazy-installs a TableRowSorter for the
        // current model when there isn't one already.
        jt.setAutoCreateRowSorter(true);
        if (jt.getRowSorter() == null) {
            throw new AssertionError("setAutoCreateRowSorter(true) should auto-install a TableRowSorter");
        }
        // setAutoCreateRowSorter(false) does NOT clear the installed sorter
        // per JDK contract — it just disables future auto-creation.
        jt.setAutoCreateRowSorter(false);
        if (jt.getRowSorter() == null) {
            throw new AssertionError("setAutoCreateRowSorter(false) should not clear an installed sorter");
        }
        dump("JTable  auto-create flags + lazy install", warnings);

        // -- Row sorter round-trip --
        // Replace with a user-supplied sorter.
        javax.swing.table.TableRowSorter<javax.swing.table.TableModel> sorter =
                new javax.swing.table.TableRowSorter<>(jt.getModel());
        jt.setRowSorter(sorter);
        if (jt.getRowSorter() != sorter) {
            throw new AssertionError("setRowSorter / getRowSorter round-trip failed");
        }
        sorter.toggleSortOrder(0);
        // convertRowIndex* should now route through the sorter.
        jt.convertRowIndexToView(0);
        jt.convertRowIndexToModel(0);
        sorter.setSortKeys(java.util.List.of());  // clear sort
        jt.setRowSorter(null);
        if (jt.getRowSorter() != null) {
            throw new AssertionError("setRowSorter(null) should clear");
        }
        dump("JTable  row sorter round-trip", warnings);

        // -- TableHeader accessors --
        JTableHeader hdr = jt.getTableHeader();
        if (hdr == null) throw new AssertionError("getTableHeader returned null");
        hdr.setReorderingAllowed(false);
        hdr.getReorderingAllowed();
        hdr.setResizingAllowed(false);
        hdr.getResizingAllowed();
        hdr.getDraggedColumn();
        hdr.getResizingColumn();
        hdr.getDraggedDistance();
        jt.setTableHeader(hdr);
        dump("JTable  JTableHeader accessors", warnings);

        // -- Row height / margin / spacing / colour: emulator R_swing_is_truth field-shadow
        //    round-trip (the surrogate drops these per R_vaadin_first — see SJTableTest). --
        jt.setRowHeight(20);
        jt.setRowMargin(2);
        jt.setIntercellSpacing(new Dimension(2, 2));
        jt.setGridColor(Color.RED);
        jt.setShowHorizontalLines(false);
        jt.setShowVerticalLines(false);
        if (jt.getRowHeight() != 20 || jt.getRowMargin() != 2
                || !new Dimension(2, 2).equals(jt.getIntercellSpacing())
                || !Color.RED.equals(jt.getGridColor())
                || jt.getShowHorizontalLines() || jt.getShowVerticalLines()) {
            throw new AssertionError("emulator must round-trip the no-counterpart knobs (R_swing_is_truth)");
        }
        jt.setShowGrid(true);
        if (!jt.getShowGrid()) throw new AssertionError("setShowGrid(true) must round-trip");
        dump("JTable  row height / margin / spacing / colour round-trip", warnings);

        // -- Auto-resize mode --
        jt.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        jt.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        jt.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        jt.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        jt.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        jt.getAutoResizeMode();
        dump("JTable  auto-resize mode round-trip", warnings);

        // -- Selection chrome (round-trip-only) --
        jt.setSelectionForeground(Color.BLUE);
        jt.getSelectionForeground();
        jt.setSelectionBackground(Color.YELLOW);
        jt.getSelectionBackground();
        dump("JTable  selection chrome round-trip", warnings);

        // -- Cell rect (R_layouts_close_enough close-enough) --
        jt.getCellRect(0, 0, true);
        dump("JTable  getCellRect", warnings);

        // -- Scrollable interface --
        jt.getScrollableUnitIncrement(new java.awt.Rectangle(0, 0, 100, 100),
                javax.swing.SwingConstants.VERTICAL, 1);
        jt.getScrollableBlockIncrement(new java.awt.Rectangle(0, 0, 100, 100),
                javax.swing.SwingConstants.VERTICAL, 1);
        jt.getScrollableTracksViewportHeight();
        jt.getScrollableTracksViewportWidth();
        jt.setFillsViewportHeight(true);
        jt.getFillsViewportHeight();
        jt.setPreferredScrollableViewportSize(new Dimension(400, 300));
        jt.getPreferredScrollableViewportSize();
        dump("JTable  Scrollable interface", warnings);

        // -- Cell-row-allowed flags --
        jt.setRowSelectionAllowed(true);
        jt.getRowSelectionAllowed();
        jt.setColumnSelectionAllowed(false);
        jt.getColumnSelectionAllowed();
        // setCellSelectionEnabled fires onUnimplemented per design — skip.
        jt.getCellSelectionEnabled();
        dump("JTable  row/column-allowed flags", warnings);

        // -- L&F stubs --
        jt.getUIClassID();
        jt.updateUI();
        dump("JTable  L&F stubs", warnings);

        // -- Editor surface (deferred — only call the no-op
        //    accessors that don't WARN) --
        jt.isEditing();
        jt.getEditingRow();
        jt.getEditingColumn();
        jt.getEditorComponent();
        jt.removeEditor();
        jt.getCellEditor();
        jt.getDefaultEditor(String.class);
        dump("JTable  editor surface (no-op accessors)", warnings);

        // -- doLayout / sizeColumnsToFit (R_layouts_close_enough) --
        jt.doLayout();
        jt.sizeColumnsToFit(0);
        dump("JTable  layout no-ops", warnings);

        WarnDump.println();
        WarnDump.println("=== JTable API-surface WARN total: " + warnings.size() + " ===");
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the Tables exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
