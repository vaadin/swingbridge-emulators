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

import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import vaadinx.swing.DefaultCellEditor;
import vaadinx.swing.JButton;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTable;
import vaadinx.swing.JTextField;
import vaadinx.swing.table.DefaultTableCellRenderer;

import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.List;

/**
 * JTable demo. Hosts a {@link JTable} over a custom {@link AbstractTableModel}
 * backed by an ArrayList of {@link Person}, plus four {@link JButton}s
 * that mutate the model and sort programmatically, and a
 * {@link JTextField} that installs a case-insensitive regex
 * {@link RowFilter} via the table's {@link TableRowSorter}.
 *
 * <p>Demonstrates:
 * <ul>
 *   <li>{@link AbstractTableModel}'s row mutation surface
 *       ({@code addRow} / {@code removeRow} / {@code setValueAt}) driving
 *       the surrogate's per-row data refresh.</li>
 *   <li>Selection bridge: a JDK {@link javax.swing.event.ListSelectionListener}
 *       attached to {@code table.getSelectionModel()} updates the status
 *       label with the selected row.</li>
 *   <li>A custom {@link DefaultTableCellRenderer} subclass on column 0
 *       (the name column) that uppercases the value via the JDK
 *       mutate-and-return-self contract — exercises the
 *       emulator→surrogate renderer bridge with snapshot.</li>
 *   <li>Sorting via {@code setAutoCreateRowSorter(true)}: column
 *       headers are clickable in the browser to cycle ASC / DESC; the
 *       <i>Sort by Name</i> button drives the sort programmatically;
 *       <i>Clear sort</i> resets via {@code setSortKeys(emptyList)}.</li>
 *   <li>Filtering: each keystroke in the filter field rebuilds a
 *       case-insensitive regex {@link RowFilter} on column 0 and
 *       installs it via the {@link TableRowSorter}. Empty string
 *       clears the filter.</li>
 *   <li>In-cell editing (D_jtable_cell_editing): the fully-editable model + the
 *       {@code Grid.Editor} bridge edit cells in place — a
 *       {@link DefaultCellEditor} {@link JComboBox} editor on Profession
 *       (single-click dropdown), the Boolean checkbox editor on Active
 *       (single click), and the default text editor on the string columns
 *       (double-click); commits flow through {@code setValueAt}.</li>
 * </ul>
 */
public class TablesPanel extends JPanel {

    private final JLabel status = new JLabel("Click a row, or use the buttons.");
    final PersonTableModel model = new PersonTableModel();
    final JTable table = new JTable(model);

    public TablesPanel() {
        super(new BorderLayout(8, 8));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "JTable demo. Buttons mutate the model (addRow / removeRow / "
                        + "setValueAt) and sort programmatically; column headers "
                        + "sort in the browser; the filter field installs a case-"
                        + "insensitive regex RowFilter on the Name column. "
                        + "In-cell editing: double-click a text cell to edit it, "
                        + "single-click Profession for a dropdown or Active for a "
                        + "checkbox; edits commit back through the table model."));

        // Custom renderer on Name (column 0) — JDK mutate-and-return-self
        // pattern. Exercises the snapshot bridge.
        table.getColumnModel().getColumn(0).setCellRenderer(new UppercaseRenderer());

        // In-cell editing (D_jtable_cell_editing): the model is fully editable, so the
        // Grid.Editor bridge lets you edit cells in place. Profession gets a
        // JComboBox editor (single-click dropdown, DefaultCellEditor's
        // clickCountToStart == 1); Active is a checkbox editor (single click);
        // the text columns (Name / Profession-when-typed) edit on double-click.
        JComboBox<String> professionEditor = new JComboBox<>(new String[] {
                "Mathematician", "Cryptographer", "Admiral",
                "Computer Scientist", "Inventor", "Programmer" });
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(professionEditor));

        // Single-row selection — typical CRUD shape.
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        // Auto-create a TableRowSorter for this model — lights up the
        // browser-side sort indicator on each column header. Per SD_sjtable_sorting
        // sorting flows through the FetchCallback inline; no extra
        // listener wiring needed in user code. The sorter is also the
        // place where setRowFilter(...) lives.
        table.setAutoCreateRowSorter(true);

        // Filter UI: text field above the table. Each Document change
        // builds a case-insensitive regex RowFilter on column 0 (Name)
        // and installs it via the TableRowSorter. RowSorterEvent fires
        // → bridge calls dataProvider.refreshAll() → Vaadin re-fetches
        // with the now-filtered view. Empty string clears the filter.
        // (Vaadin's LAZY value-change debounce becomes an unthrottled
        //  per-keystroke fire here — the regex rebuild is cheap and the
        //  cell-count is small enough that the difference is invisible
        //  for the testbed scope.)
        JTextField filterField = new JTextField(20);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(filterField.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(filterField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(filterField.getText()); }
        });

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        filterRow.add(new JLabel("Filter by name:"));
        filterRow.add(filterField);

        // Selection listener via the JDK selection model accessor.
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) {
                status.setText("No row selected.");
            } else {
                // Selected row is the *view* row index after sort.
                // Convert to model row to read from the underlying list.
                int modelRow = table.convertRowIndexToModel(row);
                Person p = model.people.get(modelRow);
                status.setText("Selected view row " + row + " (model row "
                        + modelRow + "): " + p);
            }
        });

        // Double-click a row to "open" it — the canonical JTable idiom,
        // resolving the clicked cell via rowAtPoint / columnAtPoint (the
        // item-click → MouseEvent bridge + click-cell stash).
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    java.awt.Point p = new java.awt.Point(e.getX(), e.getY());
                    int row = table.rowAtPoint(p);
                    int col = table.columnAtPoint(p);
                    if (row >= 0) {
                        status.setText(col >= 0
                                ? "Opened cell [row " + row + ", col " + col + "]: "
                                        + table.getValueAt(row, col)
                                : "Opened row " + row + ": " + table.getValueAt(row, 0));
                    }
                }
            }
        });

        JButton addRow = new JButton("Add row");
        addRow.addActionListener(e ->
                model.addPerson(new Person("New Person", "Programmer", true)));

        JButton removeSelected = new JButton("Remove selected");
        removeSelected.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                model.removePerson(table.convertRowIndexToModel(row));
            }
        });

        JButton promoteSelected = new JButton("Promote selected");
        promoteSelected.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                int modelRow = table.convertRowIndexToModel(row);
                Object current = model.getValueAt(modelRow, 1);
                model.setValueAt("Senior " + current, modelRow, 1);
            }
        });

        JButton sortByName = new JButton("Sort by Name");
        sortByName.addActionListener(e -> table.getRowSorter().toggleSortOrder(0));

        JButton clearSort = new JButton("Clear sort");
        clearSort.addActionListener(e -> table.getRowSorter().setSortKeys(List.of()));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(addRow);
        buttons.add(removeSelected);
        buttons.add(promoteSelected);
        buttons.add(sortByName);
        buttons.add(clearSort);

        // Top region carries header + filter + the table itself; the
        // SOUTH region carries status + buttons. BorderLayout keeps the
        // table free to grow into the CENTER if/when viewport sizing
        // lands (open item on SamplerFrame).
        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(header, BorderLayout.NORTH);
        top.add(filterRow, BorderLayout.CENTER);
        top.add(table, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.CENTER);
        add(bottom, BorderLayout.CENTER);
    }

    @SuppressWarnings("unchecked")
    private void applyFilter(String text) {
        TableRowSorter<TableModel> sorter =
                (TableRowSorter<TableModel>) table.getRowSorter();
        if (text == null || text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            // (?i) for case-insensitive; Pattern.quote escapes regex
            // metacharacters so user typing "C++" doesn't blow up.
            sorter.setRowFilter(RowFilter.regexFilter(
                    "(?i)" + java.util.regex.Pattern.quote(text), 0));
        }
    }

    /** Plain-old data record for the table. */
    record Person(String name, String profession, Boolean active) {
        @Override
        public String toString() {
            return name + " (" + profession + ", " + (active ? "active" : "inactive") + ")";
        }
    }

    /**
     * AbstractTableModel over a mutable ArrayList of {@link Person}.
     * Mirrors the JDK-canonical pattern for mutable list-backed models.
     */
    static final class PersonTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = { "Name", "Profession", "Active" };

        final List<Person> people = new ArrayList<>(List.of(
                new Person("Ada Lovelace", "Mathematician", true),
                new Person("Alan Turing", "Cryptographer", true),
                new Person("Grace Hopper", "Admiral", true),
                new Person("Edsger Dijkstra", "Computer Scientist", true),
                new Person("Tim Berners-Lee", "Inventor", true)));

        @Override public int getRowCount() { return people.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0, 1 -> String.class;
                case 2 -> Boolean.class;
                default -> Object.class;
            };
        }

        @Override
        public Object getValueAt(int row, int column) {
            Person p = people.get(row);
            return switch (column) {
                case 0 -> p.name();
                case 1 -> p.profession();
                case 2 -> p.active();
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            // Fully editable — enables in-cell editing (D_jtable_cell_editing) and programmatic setValueAt.
            return true;
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            Person p = people.get(row);
            Person updated = switch (column) {
                case 0 -> new Person(String.valueOf(aValue), p.profession(), p.active());
                case 1 -> new Person(p.name(), String.valueOf(aValue), p.active());
                case 2 -> new Person(p.name(), p.profession(), (Boolean) aValue);
                default -> p;
            };
            people.set(row, updated);
            fireTableCellUpdated(row, column);
        }

        public void addPerson(Person p) {
            int newRow = people.size();
            people.add(p);
            fireTableRowsInserted(newRow, newRow);
        }

        public void removePerson(int row) {
            if (row < 0 || row >= people.size()) return;
            people.remove(row);
            fireTableRowsDeleted(row, row);
        }
    }

    /**
     * DefaultTableCellRenderer subclass overriding {@code setValue} to
     * uppercase the rendered text. Canonical JDK migration pattern —
     * exercises the emulator→surrogate renderer bridge through the
     * dynamic Vaadin renderer + snapshot path.
     */
    static final class UppercaseRenderer extends DefaultTableCellRenderer {
        @Override
        protected void setValue(Object value) {
            setText(value == null ? "" : String.valueOf(value).toUpperCase());
        }
    }
}
