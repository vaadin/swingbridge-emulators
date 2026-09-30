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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.provider.QuerySortOrder;
import com.vaadin.flow.data.provider.SortDirection;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.awt.event.SInputEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseListener;
import com.vaadin.swingbridge.surrogates.internal.Registrations;
import com.vaadin.swingbridge.surrogates.internal.TableStateStore;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.DefaultListSelectionModel;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Surrogate for {@link javax.swing.JTable} (SD_sjtable). Extends Vaadin
 * {@link Grid Grid&lt;Integer&gt;} — row indices are the Grid's items,
 * which keeps the installed {@link TableModel} as the single source of
 * truth (R_swing_is_truth) and lets {@code setValueAt} updates reflect on next render
 * without snapshot rebuilds.
 *
 * <h2>Vaadin-first rendering (R_vaadin_first)</h2>
 *
 * Surrogate-layer code uses Vaadin's {@link com.vaadin.flow.data.renderer.Renderer Renderer}
 * surface for column rendering — install via {@link #setRenderer(int, Renderer)}.
 * The JDK {@code TableCellRenderer} surface (return type
 * {@code java.awt.Component} clashes with our hierarchy per D_event_port_policy) lives
 * only on the emulator layer ({@code :emulators.JTable}); the emulator
 * translates its ported {@code vaadinx.swing.table.TableCellRenderer}
 * registry into Vaadin renderers and installs via {@link #setRenderer}.
 * Pure-surrogate users without {@link #setRenderer} get a default
 * {@code value.toString()} {@link Span} renderer for every column —
 * matches the SJComboBox precedent (default {@code setItemLabelGenerator}
 * with {@code String.valueOf}).
 *
 * <h2>Row-index identity</h2>
 *
 * Each Vaadin row carries the boxed {@code Integer} index into the model.
 * Per-cell renderer reads {@code model.getValueAt(row, modelCol)} live, so
 * {@code fireTableRowsUpdated} just calls
 * {@code dataProvider.refreshItem(row)} — no array-snapshot resync. The
 * {@link ListSelectionModel} is index-based already, so the bridge to
 * Vaadin Grid's selection (which carries the same Integer keys) is direct.
 *
 * <h2>Listener-list architecture</h2>
 *
 * SJTable IS-A {@link TableModelListener} / {@link TableColumnModelListener} /
 * {@link ListSelectionListener} — JDK JTable's pattern. The surrogate
 * subscribes itself to the installed model, column model, and selection
 * model on install/swap; user code subscribes directly to those models
 * (no surrogate-level convenience listener registration). The internal
 * subscriptions handle (a) column rebuild on structure change, (b) Grid
 * data refresh on row change, (c) Grid selection push on selection-model
 * change. Vaadin → us flows via a {@code addSelectionListener} subscription
 * wrapped in {@link SHelper#callSwing} (R_callswing_envelope) and writes through to
 * {@link ListSelectionModel}, closing the loop.
 *
 * <h2>R_vaadin_first drop-and-WARN surface</h2>
 *
 * Cell-editor surface (R_match_swing_errors sub-bucket (b)),
 * {@code TableRowSorter} / {@code RowFilter} (deferred),
 * {@code dragEnabled} / {@code dropMode} (cross-cutting DnD),
 * column-cell selection (Vaadin Grid is row-only),
 * {@code intercellSpacing} / {@code gridColor} / {@code showHorizontalLines}
 * (Vaadin theme owns chrome), {@code rowHeight} on push (Vaadin auto-
 * heights), printing (permanent sub-bucket (b)).
 */
public class SJTable extends Grid<Integer> implements JComponentMixin,
        TableModelListener, TableColumnModelListener, ListSelectionListener {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    // --- JDK JTable auto-resize-mode constants (migrated code passes these) ---

    public static final int AUTO_RESIZE_OFF = 0;
    public static final int AUTO_RESIZE_NEXT_COLUMN = 1;
    public static final int AUTO_RESIZE_SUBSEQUENT_COLUMNS = 2;
    public static final int AUTO_RESIZE_LAST_COLUMN = 3;
    public static final int AUTO_RESIZE_ALL_COLUMNS = 4;

    // --- Constructors ---

    public SJTable() {
        this(null, null, null);
    }

    public SJTable(TableModel dm) {
        this(dm, null, null);
    }

    public SJTable(TableModel dm, TableColumnModel cm) {
        this(dm, cm, null);
    }

    public SJTable(TableModel dm, TableColumnModel cm, ListSelectionModel sm) {
        super();
        _installSwingClass();
        TableStateStore store = store();

        // Install the FetchCallback first — every subsequent setModel /
        // setColumnModel / setRowSorter triggers refreshAll() on the
        // returned data provider, so it must exist before those run.
        // Initial fetches see model = null and return empty per the
        // null-guard in fetch().
        installFetchCallback();

        // JDK ctor sequence: selectionModel → columnModel → dataModel.
        // Each setter installs our listener subscription; setModel triggers
        // the structure-change tableChanged that rebuilds Vaadin columns.
        if (sm == null) sm = createDefaultSelectionModel();
        setSelectionModel(sm);

        if (cm == null) {
            cm = createDefaultColumnModel();
            // autoCreateColumnsFromModel stays at its store default (true) —
            // setModel below will populate this empty cm from the model.
        } else {
            // User supplied a column model, so don't auto-rebuild.
            store.autoCreateColumnsFromModel = false;
        }
        setColumnModel(cm);

        if (dm == null) dm = createDefaultDataModel();
        setModel(dm);

        // Default Grid selection mode → MULTI to match JDK's
        // MULTIPLE_INTERVAL_SELECTION default. Re-installable per
        // setSelectionMode swap.
        super.setSelectionMode(Grid.SelectionMode.MULTI);
        installVaadinSelectionListener();
    }

    public SJTable(int numRows, int numCols) {
        this(new DefaultTableModel(numRows, numCols));
    }

    public SJTable(Object[][] rowData, Object[] columnNames) {
        this(new DefaultTableModel(rowData, columnNames));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public SJTable(Vector rowData, Vector columnNames) {
        this(new DefaultTableModel((Vector) rowData, (Vector) columnNames));
    }

    private TableStateStore store() {
        return TableStateStore.of(this);
    }

    // --- Factory hooks (JDK protected; subclasses may override) ---

    protected TableModel createDefaultDataModel() {
        return new DefaultTableModel();
    }

    protected TableColumnModel createDefaultColumnModel() {
        return new DefaultTableColumnModel();
    }

    protected ListSelectionModel createDefaultSelectionModel() {
        return new DefaultListSelectionModel();
    }

    // --- Model accessors ---

    public TableModel getModel() {
        return store().model;
    }

    public void setModel(TableModel newModel) {
        Objects.requireNonNull(newModel, "Cannot set a null TableModel");
        TableStateStore store = store();
        TableModel old = store.model;
        if (old == newModel) return;
        if (old != null) old.removeTableModelListener(this);
        store.model = newModel;
        newModel.addTableModelListener(this);
        // JDK behaviour: when autoCreateRowSorter is true, setModel
        // installs a fresh TableRowSorter for the new model. Drop any
        // prior sorter (it was bound to the old model) regardless of
        // the flag to avoid a stale-model reference.
        if (store.rowSorter != null) installRowSorter(null);
        if (store.autoCreateRowSorter) {
            installRowSorter(new TableRowSorter<>(newModel));
        }
        firePropertyChange("model", old, newModel);
        // JDK fires a synthetic structure-change to drive column rebuild
        // and data refresh. Match.
        tableChanged(new TableModelEvent(newModel, TableModelEvent.HEADER_ROW));
    }

    public TableColumnModel getColumnModel() {
        return store().columnModel;
    }

    public void setColumnModel(TableColumnModel newModel) {
        Objects.requireNonNull(newModel, "Cannot set a null TableColumnModel");
        TableStateStore store = store();
        TableColumnModel old = store.columnModel;
        if (old == newModel) return;
        if (old != null) old.removeColumnModelListener(this);
        store.columnModel = newModel;
        newModel.addColumnModelListener(this);
        firePropertyChange("columnModel", old, newModel);
        rebuildVaadinColumnsFromColumnModel();
    }

    /**
     * JDK {@code getSelectionModel(): ListSelectionModel} can't coexist
     * with Vaadin Grid's same-named {@code getSelectionModel(): GridSelectionModel<T>}
     * (different return types, can't override). Per R_vaadin_first the Vaadin shape
     * wins at the surrogate layer; pure-surrogate code reads selection via
     * {@link #setSelectionMode(int)} / {@link #setRowSelectionInterval} /
     * {@link #getSelectedRow} instead. The emulator layer
     * ({@code :emulators.JTable}) keeps the JDK-shaped getter for stage-2
     * import-swap compatibility — and reaches the JDK model through
     * {@link #getListSelectionModel()} below.
     */
    public ListSelectionModel getListSelectionModel() {
        return store().selectionModel;
    }

    /**
     * Accessor for the current JDK selection mode (one of
     * {@code ListSelectionModel.SINGLE_SELECTION /
     * SINGLE_INTERVAL_SELECTION / MULTIPLE_INTERVAL_SELECTION}). Distinct
     * name from any Vaadin Grid getter (Vaadin Grid 25 has no public
     * {@code getSelectionMode()}, but the differing name future-proofs).
     * Used by the emulator layer to read the previous mode for PCE
     * delivery on {@code setSelectionMode(int)} swaps.
     */
    public int getJdkSelectionMode() {
        return store().selectionModel.getSelectionMode();
    }

    public void setSelectionModel(ListSelectionModel newModel) {
        Objects.requireNonNull(newModel, "newModel == null");
        TableStateStore store = store();
        ListSelectionModel old = store.selectionModel;
        if (old == newModel) return;
        if (old != null) old.removeListSelectionListener(this);
        store.selectionModel = newModel;
        newModel.addListSelectionListener(this);
        firePropertyChange("selectionModel", old, newModel);
    }

    // --- Cell access (model-routed via view→model column conversion) ---

    public int getRowCount() {
        return getModel().getRowCount();
    }

    public int getColumnCount() {
        return getColumnModel().getColumnCount();
    }

    public String getColumnName(int column) {
        Object headerValue = getColumnModel().getColumn(column).getHeaderValue();
        if (headerValue == null) {
            return getModel().getColumnName(convertColumnIndexToModel(column));
        }
        return headerValue.toString();
    }

    public Class<?> getColumnClass(int column) {
        return getModel().getColumnClass(convertColumnIndexToModel(column));
    }

    // Coordinate contract (D_jtable_cell_editing): this surrogate is *model*-indexed — its Grid item
    // IS the model row (fetch emits convertRowIndexToModel(viewRow)), so {@code row}
    // here is a model row and only the column is converted. The emulator JTable is the
    // view-indexed (JDK-faithful) layer and converts the row at the delegation seam.
    public Object getValueAt(int row, int column) {
        return getModel().getValueAt(row, convertColumnIndexToModel(column));
    }

    public void setValueAt(Object aValue, int row, int column) {
        getModel().setValueAt(aValue, row, convertColumnIndexToModel(column));
    }

    public boolean isCellEditable(int row, int column) {
        return getModel().isCellEditable(row, convertColumnIndexToModel(column));
    }

    // --- View ↔ model index conversion ---

    public int convertColumnIndexToView(int modelColumnIndex) {
        if (modelColumnIndex < 0) return modelColumnIndex;
        TableColumnModel cm = getColumnModel();
        for (int column = 0; column < cm.getColumnCount(); column++) {
            if (cm.getColumn(column).getModelIndex() == modelColumnIndex) {
                return column;
            }
        }
        return -1;
    }

    public int convertColumnIndexToModel(int viewColumnIndex) {
        return getColumnModel().getColumn(viewColumnIndex).getModelIndex();
    }

    /** Routes through the installed {@link RowSorter} when present; identity otherwise. */
    public int convertRowIndexToView(int modelRowIndex) {
        RowSorter<? extends TableModel> sorter = store().rowSorter;
        return sorter == null ? modelRowIndex : sorter.convertRowIndexToView(modelRowIndex);
    }

    /** Routes through the installed {@link RowSorter} when present; identity otherwise. */
    public int convertRowIndexToModel(int viewRowIndex) {
        RowSorter<? extends TableModel> sorter = store().rowSorter;
        return sorter == null ? viewRowIndex : sorter.convertRowIndexToModel(viewRowIndex);
    }

    // --- Vaadin-first renderer install (R_vaadin_first) ---

    /**
     * Vaadin-first cell renderer install per R_vaadin_first. Replaces the column at
     * {@code viewColumnIndex} with one driven by the supplied Vaadin
     * {@link Renderer Renderer&lt;Integer&gt;}. The header text and key
     * are preserved across the swap. Migrated code at the surrogate stage
     * uses this rather than the JDK {@code TableCellRenderer} surface
     * (which lives only on the emulator layer per D_event_port_policy + R_vaadin_first).
     *
     * <p>Throws {@link IndexOutOfBoundsException} when {@code viewColumnIndex}
     * is out of range — matches Vaadin's column accessors.
     *
     * <p>Swaps the renderer <em>in place</em> via {@link Grid.Column#setRenderer}:
     * the {@code Grid.Column} instance persists, so header / key / sortability /
     * sort property / column order — and any {@code setEditorComponent} — are all
     * preserved automatically, and the swap is churn-free (safe to call
     * repeatedly, e.g. the emulator re-installs renderers on every
     * {@code setDefaultRenderer} / per-column renderer change).
     */
    public void setRenderer(int viewColumnIndex, Renderer<Integer> renderer) {
        List<Grid.Column<Integer>> cols = getColumns();
        if (viewColumnIndex < 0 || viewColumnIndex >= cols.size()) {
            throw new IndexOutOfBoundsException(
                    "viewColumnIndex out of range: " + viewColumnIndex + " (count " + cols.size() + ")");
        }
        cols.get(viewColumnIndex).setRenderer(renderer);
    }

    // --- Default Vaadin renderer (toString-via-model) ---

    /**
     * Default cell renderer used for every column built from the column
     * model. Reads through to {@code model.getValueAt(row, modelCol)} live —
     * no snapshot, no shadow. Returns a fresh {@link Span} per cell so
     * Vaadin's DOM-adoption diff (D_jcombobox's lesson) doesn't mis-route a shared
     * component instance into a single position.
     */
    private Component defaultRenderCell(Integer rowKey, int viewCol) {
        if (rowKey == null) return new Span("");
        int row = rowKey;
        // Defensive: a stale row index past the model's row count can race
        // in if the model shrinks between item-set and render. Bail empty.
        if (row < 0 || row >= getRowCount()) return new Span("");
        Object value = getValueAt(row, viewCol);
        // Boolean column-class → Vaadin Checkbox in read-only mode.
        // Mirrors JTable's JDK BooleanRenderer (centered, disabled
        // checkbox) so pure-surrogate users get the right cell shape
        // without registering a custom renderer. The emulator path
        // installs its own dynamic renderer that funnels through
        // BooleanRenderer + snapshotRendererOutput; this branch only
        // fires when SJTable is driven without an emulator JTable
        // shell on top.
        if (getColumnClass(viewCol) == Boolean.class) {
            com.vaadin.flow.component.checkbox.Checkbox cb =
                    new com.vaadin.flow.component.checkbox.Checkbox(Boolean.TRUE.equals(value));
            cb.setReadOnly(true);
            return cb;
        }
        return new Span(value == null ? "" : String.valueOf(value));
    }

    // --- Column rebuild from column model ---

    /**
     * Rebuild the Vaadin Grid's column list from the current
     * {@link TableColumnModel}. Called from {@link #setColumnModel} and
     * from the column-model listener on add/remove/move events.
     */
    private void rebuildVaadinColumnsFromColumnModel() {
        // Snapshot existing Grid columns first since getColumns is live-ish.
        List<Grid.Column<Integer>> existing = new ArrayList<>(getColumns());
        for (Grid.Column<Integer> c : existing) {
            removeColumn(c);
        }
        TableColumnModel cm = getColumnModel();
        for (int i = 0; i < cm.getColumnCount(); i++) {
            final int viewCol = i;
            TableColumn tc = cm.getColumn(i);
            Grid.Column<Integer> col = addColumn(
                    new ComponentRenderer<>(rowKey -> defaultRenderCell(rowKey, viewCol)));
            // Header: use TableColumn.headerValue when set, else
            // model.getColumnName at the model index.
            Object headerValue = tc.getHeaderValue();
            String headerText;
            if (headerValue != null) {
                headerText = headerValue.toString();
            } else if (store().model != null) {
                headerText = getModel().getColumnName(tc.getModelIndex());
            } else {
                // Called during ctor sequence before setModel — leave the
                // header empty; setModel's structure-change rebuild will
                // populate it from the model.
                headerText = "";
            }
            col.setHeader(headerText);
            // Sortable: sort indicator + click handler enabled. The
            // sortProperty carries the model column index so the
            // FetchCallback's QuerySortOrder→SortKey translation reads it
            // back. Comparator is a no-op — Vaadin's in-memory sort path
            // never runs because we drive the sort via the FetchCallback.
            col.setSortable(true);
            col.setSortProperty(String.valueOf(tc.getModelIndex()));
            col.setComparator((a, b) -> 0);
            // No setKey — JDK permits duplicate modelIndex (per the
            // TableColumn javadoc: "create a new instance with the same
            // modelIndex"). Vaadin Grid requires unique keys, so we let
            // it auto-assign internal ones. setRenderer's key-preservation
            // path tolerates null keys.
        }
        // After columns rebuild, refresh the data provider so the Grid
        // re-fetches with the new column structure.
        refreshDataProvider();
    }

    /**
     * Rebuild the {@link TableColumnModel} from the current {@link TableModel}'s
     * structure. Called when {@code autoCreateColumnsFromModel} is true and
     * the model fires a HEADER_ROW {@link TableModelEvent}.
     */
    public void createDefaultColumnsFromModel() {
        TableColumnModel cm = getColumnModel();
        TableStateStore store = store();
        // Each removeColumn/addColumn fires a TableColumnModelEvent which
        // our listener catches. Suppress with preventPeerEvents to bypass
        // our own listener during the rebuild.
        store.preventPeerEvents = true;
        try {
            while (cm.getColumnCount() > 0) {
                cm.removeColumn(cm.getColumn(0));
            }
            TableModel m = getModel();
            for (int i = 0; i < m.getColumnCount(); i++) {
                TableColumn tc = new TableColumn(i);
                tc.setHeaderValue(m.getColumnName(i));
                cm.addColumn(tc);
            }
        } finally {
            store.preventPeerEvents = false;
        }
        // Rebuild Vaadin columns once after the column model settles.
        rebuildVaadinColumnsFromColumnModel();
    }

    // --- Data provider plumbing (FetchCallback / CountCallback) ---

    /**
     * Install the {@link Grid#setItems(com.vaadin.flow.data.provider.CallbackDataProvider.FetchCallback,
     * com.vaadin.flow.data.provider.CallbackDataProvider.CountCallback) callback-driven data provider}.
     * Vaadin Grid is the sort-trigger UI; the JDK {@link RowSorter} (when
     * installed) is the source of truth for the permutation; the fetch
     * callback reads through both.
     *
     * <p>Per the design pass: when Vaadin Grid issues a {@link Query} with
     * sort orders that differ from the current row sorter's keys, the
     * fetch callback calls {@code rowSorter.setSortKeys(translated)} —
     * which is synchronous in JDK, so {@code convertRowIndexToModel} is
     * correct on the very next line. Compare-then-set is also the natural
     * loop guard: when the {@link RowSorterListener} pushes a programmatic
     * sort change back to Vaadin via {@code grid.sort(...)}, the resulting
     * fetch sees matching keys and skips the re-set.
     */
    private void installFetchCallback() {
        setItems(this::fetch, this::count);
    }

    private Stream<Integer> fetch(Query<Integer, Void> query) {
        TableStateStore store = store();
        TableModel m = store.model;
        if (m == null) return Stream.empty();
        int rowCount = m.getRowCount();
        int from = query.getOffset();
        int to = Math.min(from + query.getLimit(), rowCount);
        if (to <= from) return Stream.empty();

        List<RowSorter.SortKey> queryKeys = translateToJdkSortKeys(query.getSortOrders());

        RowSorter<? extends TableModel> sorter = store.rowSorter;
        if (sorter == null && store.autoCreateRowSorter && !queryKeys.isEmpty()) {
            // JDK behaviour: setModel auto-creates a TableRowSorter when
            // autoCreateRowSorter is true. The flag may have been set
            // after setModel ran (or before any rows arrived), so lazy-
            // create on first sort attempt as a safety net.
            installRowSorter(new TableRowSorter<>(m));
            sorter = store.rowSorter;
        }

        if (sorter != null) {
            // Compare-then-set: the natural loop guard. Vaadin browser-
            // driven sort hits this branch and applies the new keys; the
            // RowSorterListener's grid.sort(...) push hits this branch
            // too but with matching keys, so the setSortKeys is a no-op.
            if (!sorter.getSortKeys().equals(queryKeys)) {
                sorter.setSortKeys(queryKeys);
            }
            // Bound view-row indices in case the sorter's view count is
            // smaller than the model (filter case — deferred, but the
            // bound is safe today).
            int viewMax = Math.min(to, sorter.getViewRowCount());
            return IntStream.range(from, viewMax)
                    .mapToObj(sorter::convertRowIndexToModel);
        }

        // No row sorter — emit indices in model order.
        return IntStream.range(from, to).boxed();
    }

    private int count(Query<Integer, Void> query) {
        TableStateStore store = store();
        TableModel m = store.model;
        if (m == null) return 0;
        RowSorter<? extends TableModel> sorter = store.rowSorter;
        return sorter != null ? sorter.getViewRowCount() : m.getRowCount();
    }

    /**
     * Refresh the data provider so Vaadin Grid re-fetches with the
     * current state. Called from setModel / setColumnModel / structure-
     * change tableChanged / setRowSorter / model insert/delete events.
     * No-op when called during ctor before {@link #installFetchCallback}
     * (the data provider is Vaadin's default empty placeholder; refresh
     * is harmless).
     */
    private void refreshDataProvider() {
        var dp = getDataProvider();
        if (dp != null) dp.refreshAll();
    }

    // --- TableModelListener — model events drive Grid refresh ---

    /**
     * Applies a model change to the sorter, the selection and the Grid, in that order.
     *
     * <p>The installed {@link RowSorter} hears the change first, because nothing else tells
     * it: {@code TableRowSorter} does not listen to its model, it is the table's job to
     * forward every event. Until told, it keeps answering {@code getViewRowCount()} and the
     * index conversions from the rows it last saw, and {@link #count} and {@link #fetch} read
     * through it.
     *
     * <p>The selection needs no re-mapping across the change: it holds model rows
     * (D_jtable_cell_editing), so a re-sort leaves it valid, and inserts and deletes shift it
     * here in model coordinates.
     *
     * <p>Hops onto the owner's UI thread, since a worker filling the model is the
     * ordinary Swing pattern (SD_background_model_hop).
     */
    @Override
    public void tableChanged(TableModelEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: TableModelListener.tableChanged
        SHelper.runOnOwnerUI(this, () -> applyTableChange(e));
    }

    private void applyTableChange(TableModelEvent e) {
        TableStateStore store = store();
        RowSorter<? extends TableModel> sorter = store.rowSorter;
        if (e == null || e.getFirstRow() == TableModelEvent.HEADER_ROW) {
            // Structure change: selection goes, the sorter re-reads the model, then the
            // columns rebuild — whose data-provider refresh already sees the new sorter state.
            clearSelectionAndLeadAnchor();
            if (sorter != null) sorter.modelStructureChanged();
            if (store.autoCreateColumnsFromModel) {
                createDefaultColumnsFromModel();
            } else {
                rebuildVaadinColumnsFromColumnModel();
            }
            return;
        }
        if (sorter != null) notifySorter(sorter, e);
        switch (e.getType()) {
            case TableModelEvent.INSERT -> {
                int first = e.getFirstRow();
                int last = e.getLastRow();
                store().selectionModel.insertIndexInterval(first, last - first + 1, true);
                refreshDataProvider();
            }
            case TableModelEvent.DELETE -> {
                store().selectionModel.removeIndexInterval(e.getFirstRow(), e.getLastRow());
                refreshDataProvider();
            }
            default -> { // UPDATE
                if (e.getLastRow() == Integer.MAX_VALUE
                        || e.getFirstRow() < 0
                        || e.getFirstRow() >= getRowCount()) {
                    // Whole-data change or out-of-range — full re-push.
                    // JDK: JTable.sortedTableChanged treats lastRow == MAX_VALUE
                    // as ALL_CHANGED, which clears the selection. Mirror that
                    // so ListSelectionListeners on the selection model (e.g. a
                    // preview panel bound to selection) refresh after a
                    // fireTableDataChanged().
                    clearSelectionAndLeadAnchor();
                    refreshDataProvider();
                } else {
                    int safeLast = Math.min(e.getLastRow(), getRowCount() - 1);
                    var dp = getDataProvider();
                    Integer protectedRow = store.refreshProtectedRow;
                    for (int row = e.getFirstRow(); row <= safeLast; row++) {
                        // Don't re-render the row whose editor is open (D_jtable_cell_editing Task 2) —
                        // an external/concurrent setValueAt would otherwise yank it.
                        if (protectedRow != null && protectedRow == row) continue;
                        dp.refreshItem(row);
                    }
                }
            }
        }
    }

    /**
     * Tells {@code sorter} which model rows {@code e} touched, through the {@link RowSorter}
     * notification that matches the event's type. A whole-model {@code UPDATE}
     * ({@code lastRow == Integer.MAX_VALUE}, what {@code fireTableDataChanged} sends) is
     * {@code allRowsChanged}; a negative {@code lastRow} reaches to the model's last row.
     */
    private void notifySorter(RowSorter<? extends TableModel> sorter, TableModelEvent e) {
        int firstRow = Math.max(0, e.getFirstRow());
        int lastRow = e.getLastRow() < 0 ? Math.max(0, getRowCount() - 1) : e.getLastRow();
        if (e.getType() == TableModelEvent.INSERT) {
            sorter.rowsInserted(firstRow, lastRow);
        } else if (e.getType() == TableModelEvent.DELETE) {
            sorter.rowsDeleted(firstRow, lastRow);
        } else if (e.getLastRow() == Integer.MAX_VALUE) {
            sorter.allRowsChanged();
        } else if (e.getColumn() == TableModelEvent.ALL_COLUMNS) {
            sorter.rowsUpdated(firstRow, lastRow);
        } else {
            sorter.rowsUpdated(firstRow, lastRow, e.getColumn());
        }
    }

    /**
     * Clears the selection and parks its lead and anchor at {@code -1}, as a JDK table does
     * when its whole model changes — {@code clearSelection()} alone leaves both pointing at
     * rows that may no longer exist. Bracketed in one adjusting run, so a listener that skips
     * adjusting events sees a single change.
     */
    private void clearSelectionAndLeadAnchor() {
        ListSelectionModel sm = store().selectionModel;
        sm.setValueIsAdjusting(true);
        sm.clearSelection();
        sm.setAnchorSelectionIndex(-1);
        sm.setLeadSelectionIndex(-1);
        sm.setValueIsAdjusting(false);
    }

    /**
     * Protect (or, with {@code null}, un-protect) a model row from
     * {@code refreshItem} on UPDATE events. The emulator's cell-editing
     * lifecycle drives this so an external {@code setValueAt} can't re-render
     * the row hosting an open editor (D_jtable_cell_editing Task 2). Machinery, not editing API.
     */
    public void setRefreshProtectedRow(Integer modelRow) {
        store().refreshProtectedRow = modelRow;
    }

    /**
     * Whether an {@code EditorCloseListener} running now was fired because the Grid's data
     * changed — Vaadin closes an open editor on every data change, {@code refreshAll()}
     * included — rather than because the user left the cell. Machinery for the emulator, whose
     * JDK contract keeps an edit open across a model change; not editing API.
     */
    public boolean isEditorClosingForDataChange() {
        return store().editorClosingForDataChange;
    }

    @Override
    protected void onDataProviderChange() {
        TableStateStore store = store();
        boolean was = store.editorClosingForDataChange;
        store.editorClosingForDataChange = true;
        try {
            super.onDataProviderChange();
        } finally {
            store.editorClosingForDataChange = was;
        }
    }

    // --- TableColumnModelListener — column model events drive Grid rebuild ---

    @Override
    public void columnAdded(TableColumnModelEvent e) {
        if (store().preventPeerEvents) return;
        // Allowed by R_tolerate_off_ui_thread because callback from model: TableColumnModelListener.columnAdded
        SHelper.runOnOwnerUI(this, this::rebuildVaadinColumnsFromColumnModel);
    }

    @Override
    public void columnRemoved(TableColumnModelEvent e) {
        if (store().preventPeerEvents) return;
        // Allowed by R_tolerate_off_ui_thread because callback from model: TableColumnModelListener.columnRemoved
        SHelper.runOnOwnerUI(this, this::rebuildVaadinColumnsFromColumnModel);
    }

    @Override
    public void columnMoved(TableColumnModelEvent e) {
        if (store().preventPeerEvents) return;
        if (e.getFromIndex() == e.getToIndex()) return;
        // Allowed by R_tolerate_off_ui_thread because callback from model: TableColumnModelListener.columnMoved
        SHelper.runOnOwnerUI(this, this::rebuildVaadinColumnsFromColumnModel);
    }

    @Override
    public void columnMarginChanged(ChangeEvent e) {
        // R_vaadin_first drop — Vaadin Grid owns column margin / spacing.
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) {
        // R_vaadin_first drop — column selection has no Vaadin counterpart.
    }

    // --- ListSelectionListener — row selection model → Vaadin Grid ---

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        TableStateStore store = store();
        if (store.preventPeerEvents) return;
        // Allowed by R_tolerate_off_ui_thread because callback from model: ListSelectionListener.valueChanged
        SHelper.runOnOwnerUI(this, () -> {
            store.preventPeerEvents = true;
            try {
                pushSelectionToPeer();
            } finally {
                store.preventPeerEvents = false;
            }
        });
    }

    private void pushSelectionToPeer() {
        deselectAll();
        ListSelectionModel sm = store().selectionModel;
        if (sm.isSelectionEmpty()) return;
        int min = sm.getMinSelectionIndex();
        int max = sm.getMaxSelectionIndex();
        int rowCount = getRowCount();
        for (int i = min; i <= max && i < rowCount; i++) {
            if (sm.isSelectedIndex(i)) {
                select(i);
            }
        }
    }

    // --- Vaadin selection → ListSelectionModel write-through (R_callswing_envelope) ---

    private void installVaadinSelectionListener() {
        TableStateStore store = store();
        if (store.vaadinSelectionRegistration != null) {
            store.vaadinSelectionRegistration.remove();
        }
        store.vaadinSelectionRegistration = addSelectionListener(event ->
                SHelper.callSwing(this::syncSelectionFromPeer));
    }

    private void syncSelectionFromPeer() {
        TableStateStore store = store();
        if (store.preventPeerEvents) return;
        Set<Integer> selected = getSelectedItems();
        ListSelectionModel sm = store().selectionModel;
        store.preventPeerEvents = true;
        try {
            // One adjusting run, so a listener that skips adjusting events sees one change
            // per browser selection rather than a clear followed by an add per row.
            sm.setValueIsAdjusting(true);
            sm.clearSelection();
            for (int idx : selected) {
                sm.addSelectionInterval(idx, idx);
            }
            sm.setValueIsAdjusting(false);
        } finally {
            store.preventPeerEvents = false;
        }
    }

    // --- Selection API (route through ListSelectionModel) ---

    public void setRowSelectionInterval(int index0, int index1) {
        store().selectionModel.setSelectionInterval(index0, index1);
    }

    public void addRowSelectionInterval(int index0, int index1) {
        store().selectionModel.addSelectionInterval(index0, index1);
    }

    public void removeRowSelectionInterval(int index0, int index1) {
        store().selectionModel.removeSelectionInterval(index0, index1);
    }

    public void clearSelection() {
        store().selectionModel.clearSelection();
    }

    public int getSelectedRow() {
        return store().selectionModel.getMinSelectionIndex();
    }

    public int[] getSelectedRows() {
        ListSelectionModel sm = store().selectionModel;
        if (sm.isSelectionEmpty()) return new int[0];
        int min = sm.getMinSelectionIndex();
        int max = sm.getMaxSelectionIndex();
        List<Integer> selected = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            if (sm.isSelectedIndex(i)) selected.add(i);
        }
        int[] out = new int[selected.size()];
        for (int i = 0; i < out.length; i++) out[i] = selected.get(i);
        return out;
    }

    public int getSelectedRowCount() {
        return store().selectionModel.getSelectedItemsCount();
    }

    public boolean isRowSelected(int row) {
        return store().selectionModel.isSelectedIndex(row);
    }

    public void selectAll() {
        int n = getRowCount();
        if (n > 0) setRowSelectionInterval(0, n - 1);
    }

    /**
     * JDK selection-mode setter. Validates per JDK contract (throws IAE on
     * unrecognised mode), updates the underlying {@link ListSelectionModel},
     * and maps to Vaadin Grid's {@link Grid.SelectionMode}: SINGLE → SINGLE;
     * the two interval modes both map to MULTI since Vaadin Grid has no
     * single-interval analog. The {@link ListSelectionModel} still enforces
     * the JDK contract; the divergence is browser-side ctrl-click extending
     * past the interval — the model rejects the second selection so the
     * UI reverts.
     */
    public void setSelectionMode(int selectionMode) {
        switch (selectionMode) {
            case ListSelectionModel.SINGLE_SELECTION,
                 ListSelectionModel.SINGLE_INTERVAL_SELECTION,
                 ListSelectionModel.MULTIPLE_INTERVAL_SELECTION -> { /* ok */ }
            default -> throw new IllegalArgumentException("Unrecognized selection mode: " + selectionMode);
        }
        TableStateStore store = store();
        int old = store.selectionModel.getSelectionMode();
        store.selectionModel.setSelectionMode(selectionMode);
        Grid.SelectionMode vaadinMode = (selectionMode == ListSelectionModel.SINGLE_SELECTION)
                ? Grid.SelectionMode.SINGLE
                : Grid.SelectionMode.MULTI;
        super.setSelectionMode(vaadinMode);
        // Vaadin re-creates the Grid's internal selection backing on mode
        // swap — re-install our listener on the fresh selection model.
        installVaadinSelectionListener();
        // No "selectionMode" property change: the JDK's JTable.setSelectionMode
        // clears the selection and delegates to both selection models,
        // firing nothing of its own (SD_property_fanout_audit).
    }

    // --- No-Vaadin-counterpart knobs: present-but-drop-and-WARN (R_vaadin_first) ------
    //
    // Grid is row-only / virtualises / auto-sizes and the theme owns row
    // chrome, so none of the JTable knobs below has a peer property to bind.
    // Per R_vaadin_first the surrogate is Vaadin-first: getters return JDK defaults,
    // setters log-and-drop (no shadow cache). JDK-faithful round-trip for
    // these lives on the emulator layer (:emulators.JTable field shadows +
    // PCE per R_swing_is_truth) — the asymmetric split SJToolBar (SD_sjtoolbar/D_jtoolbar), SJSplitPane
    // (SD_sjsplitpane/D_jsplitpane), SJList (SD_sjlist/D_jlist) established.

    public boolean getRowSelectionAllowed() {
        return true;   // JDK default
    }

    public void setRowSelectionAllowed(boolean rowSelectionAllowed) {
        SHelper.onUnimplemented(this, "setRowSelectionAllowed", rowSelectionAllowed);
    }

    public boolean getColumnSelectionAllowed() {
        return false;  // JDK default — Vaadin Grid is row-only
    }

    public void setColumnSelectionAllowed(boolean columnSelectionAllowed) {
        SHelper.onUnimplemented(this, "setColumnSelectionAllowed", columnSelectionAllowed);
    }

    public boolean getCellSelectionEnabled() {
        return getRowSelectionAllowed() && getColumnSelectionAllowed();
    }

    public void setCellSelectionEnabled(boolean cellSelectionEnabled) {
        SHelper.onUnimplemented(this, "setCellSelectionEnabled", cellSelectionEnabled);
    }

    // --- Column manipulation (TableColumnModel-routed) ---

    public void addColumn(TableColumn aColumn) {
        if (aColumn.getHeaderValue() == null) {
            int modelIdx = aColumn.getModelIndex();
            aColumn.setHeaderValue(getModel().getColumnName(modelIdx));
        }
        getColumnModel().addColumn(aColumn);
    }

    public void removeColumn(TableColumn aColumn) {
        getColumnModel().removeColumn(aColumn);
    }

    public void moveColumn(int columnIndex, int newIndex) {
        getColumnModel().moveColumn(columnIndex, newIndex);
    }

    public TableColumn getColumn(Object identifier) {
        TableColumnModel cm = getColumnModel();
        return cm.getColumn(cm.getColumnIndex(identifier));
    }

    // --- AutoCreate flags ---

    public boolean getAutoCreateColumnsFromModel() {
        return store().autoCreateColumnsFromModel;
    }

    public void setAutoCreateColumnsFromModel(boolean autoCreate) {
        TableStateStore store = store();
        boolean old = store.autoCreateColumnsFromModel;
        if (old == autoCreate) return;
        store.autoCreateColumnsFromModel = autoCreate;
        firePropertyChange("autoCreateColumnsFromModel", old, autoCreate);
        if (autoCreate) {
            // JDK rebuilds immediately when flipped to true.
            createDefaultColumnsFromModel();
        }
    }

    public boolean getAutoCreateRowSorter() {
        return store().autoCreateRowSorter;
    }

    public void setAutoCreateRowSorter(boolean autoCreateRowSorter) {
        TableStateStore store = store();
        boolean old = store.autoCreateRowSorter;
        if (old == autoCreateRowSorter) return;
        store.autoCreateRowSorter = autoCreateRowSorter;
        // JDK behaviour: when flipped to true, install a fresh
        // TableRowSorter for the current model immediately if one isn't
        // already installed. Matches JTable's setAutoCreateRowSorter
        // contract — migrators flipping this on after construction get
        // sorting without an extra setModel call.
        if (autoCreateRowSorter && store.rowSorter == null && store.model != null) {
            installRowSorter(new TableRowSorter<>(store.model));
        }
        firePropertyChange("autoCreateRowSorter", old, autoCreateRowSorter);
    }

    // --- RowSorter accessors + bridge -----------------------------------

    /**
     * Returns the currently installed JDK {@link RowSorter}, or
     * {@code null} when no sort is active. Migrators read this to inspect
     * {@code getSortKeys()} or to call {@code toggleSortOrder(column)}
     * programmatically.
     */
    public RowSorter<? extends TableModel> getRowSorter() {
        return store().rowSorter;
    }

    /**
     * Install a JDK {@link RowSorter} on this table. {@code null} clears
     * sort entirely (subsequent Vaadin Grid header clicks fall back to
     * model order). Detaches our {@link RowSorterListener} from the old
     * sorter and attaches a fresh one to the new — programmatic
     * {@code rowSorter.setSortKeys(...)} calls are bridged back to Vaadin
     * via the listener (which calls {@code grid.sort(translated)} so the
     * Grid's sort indicator stays in sync).
     */
    public void setRowSorter(RowSorter<? extends TableModel> newRowSorter) {
        TableStateStore store = store();
        RowSorter<? extends TableModel> old = store.rowSorter;
        if (old == newRowSorter) return;
        installRowSorter(newRowSorter);
        // Two names, one setter, both carrying the same pair — the JDK's own
        // back-compat doubling (JTable.java:2007-2008), so a listener registered for
        // either name hears it. Dropping the legacy one is a silent break for code
        // that registered under "sorter" (SD_reverse_fanout_rows).
        firePropertyChange("rowSorter", old, newRowSorter);
        firePropertyChange("sorter", old, newRowSorter);
        // Refresh so Vaadin Grid re-fetches with the new (or absent)
        // sort permutation.
        refreshDataProvider();
    }

    /**
     * Attach/detach the {@link RowSorterListener} as part of installing or
     * clearing a row sorter. Shared by {@link #setRowSorter}, {@link #setModel}
     * (when {@code autoCreateRowSorter} is true), and the lazy-create path
     * inside {@link #fetch}.
     */
    private void installRowSorter(RowSorter<? extends TableModel> newRowSorter) {
        TableStateStore store = store();
        if (store.rowSorter != null && store.rowSorterEventListener != null) {
            store.rowSorter.removeRowSorterListener(store.rowSorterEventListener);
        }
        store.rowSorter = newRowSorter;
        if (newRowSorter == null) {
            store.rowSorterEventListener = null;
            return;
        }
        store.rowSorterEventListener = e -> onRowSorterEvent(e);
        newRowSorter.addRowSorterListener(store.rowSorterEventListener);
    }

    /**
     * Bridge programmatic {@code rowSorter.setSortKeys(...)} (or
     * {@code toggleSortOrder(...)}) calls back to Vaadin Grid by calling
     * {@code grid.sort(translated)} so the sort indicator updates and a
     * fetch fires. The {@link #fetch} compare-then-set guard makes that
     * fetch a no-op against the rowSorter (keys already match), so no
     * loop forms.
     *
     * <p>Filter-only changes (e.g. {@code tableRowSorter.setRowFilter(...)})
     * also fire {@link RowSorterEvent.Type#SORTED} but with unchanged
     * sort orders — when {@code gridSortEquals} is true, fall through to
     * {@code dataProvider.refreshAll()} so Vaadin still re-fetches with
     * the new filtered view. Without this, filter-only changes would
     * silently leave the Grid showing stale rows.
     */
    private void onRowSorterEvent(RowSorterEvent e) {
        if (e.getType() != RowSorterEvent.Type.SORT_ORDER_CHANGED
                && e.getType() != RowSorterEvent.Type.SORTED) {
            return;
        }
        RowSorter<? extends TableModel> sorter = store().rowSorter;
        if (sorter == null) return;
        List<GridSortOrder<Integer>> targetVaadin = translateToVaadinSortOrder(sorter.getSortKeys());
        List<GridSortOrder<Integer>> currentVaadin = getSortOrder();
        if (gridSortEquals(currentVaadin, targetVaadin)) {
            // Sort orders unchanged — must be a filter (or model-update)
            // SORTED event. grid.sort with same orders may or may not
            // re-fetch depending on Vaadin's internals; refreshAll is
            // unambiguous.
            refreshDataProvider();
            return;
        }
        // Sort orders changed — grid.sort updates the sort indicator AND
        // triggers a fetch. The compare-then-set in fetch makes the
        // resulting setSortKeys a no-op against the rowSorter.
        sort(targetVaadin);
    }

    /** Translate Vaadin {@link QuerySortOrder} list to JDK {@link RowSorter.SortKey}s. */
    private List<RowSorter.SortKey> translateToJdkSortKeys(List<QuerySortOrder> vaadinOrders) {
        if (vaadinOrders == null || vaadinOrders.isEmpty()) return List.of();
        List<RowSorter.SortKey> out = new ArrayList<>(vaadinOrders.size());
        for (QuerySortOrder qso : vaadinOrders) {
            int modelCol;
            try {
                modelCol = Integer.parseInt(qso.getSorted());
            } catch (NumberFormatException nfe) {
                // Non-numeric sort property — not one we set; skip.
                continue;
            }
            SortOrder dir = qso.getDirection() == SortDirection.ASCENDING
                    ? SortOrder.ASCENDING
                    : SortOrder.DESCENDING;
            out.add(new RowSorter.SortKey(modelCol, dir));
        }
        return out;
    }

    /** Translate JDK {@link RowSorter.SortKey}s to Vaadin {@link GridSortOrder}s. */
    private List<GridSortOrder<Integer>> translateToVaadinSortOrder(List<? extends RowSorter.SortKey> jdkKeys) {
        if (jdkKeys == null || jdkKeys.isEmpty()) return List.of();
        List<GridSortOrder<Integer>> out = new ArrayList<>(jdkKeys.size());
        for (RowSorter.SortKey key : jdkKeys) {
            if (key.getSortOrder() == SortOrder.UNSORTED) continue;
            Grid.Column<Integer> col = findColumnByModelCol(key.getColumn());
            if (col == null) continue;  // column not visible (e.g., removed from columnModel)
            SortDirection dir = key.getSortOrder() == SortOrder.ASCENDING
                    ? SortDirection.ASCENDING
                    : SortDirection.DESCENDING;
            out.add(new GridSortOrder<>(col, dir));
        }
        return out;
    }

    /**
     * Find the Vaadin Grid column whose backing TableColumn has the given
     * model index, or {@code null} when no visible column matches.
     * Vaadin column order matches column-model order at all times (per
     * {@link #rebuildVaadinColumnsFromColumnModel}).
     */
    private Grid.Column<Integer> findColumnByModelCol(int modelCol) {
        TableColumnModel cm = getColumnModel();
        List<Grid.Column<Integer>> gridCols = getColumns();
        for (int viewCol = 0; viewCol < gridCols.size() && viewCol < cm.getColumnCount(); viewCol++) {
            if (cm.getColumn(viewCol).getModelIndex() == modelCol) {
                return gridCols.get(viewCol);
            }
        }
        return null;
    }

    /** Compare two Vaadin sort-order lists for column + direction equality. */
    private static boolean gridSortEquals(List<GridSortOrder<Integer>> a, List<GridSortOrder<Integer>> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            GridSortOrder<Integer> x = a.get(i);
            GridSortOrder<Integer> y = b.get(i);
            if (x.getSorted() != y.getSorted()) return false;
            if (x.getDirection() != y.getDirection()) return false;
        }
        return true;
    }

    // --- Row height / margin / spacing / colour (present-drop, R_vaadin_first) ---

    public int getRowHeight() {
        return 16;     // JDK default
    }

    public void setRowHeight(int rowHeight) {
        SHelper.onUnimplemented(this, "setRowHeight", rowHeight);
    }

    public int getRowHeight(int row) {
        return getRowHeight();
    }

    public void setRowHeight(int row, int rowHeight) {
        SHelper.onUnimplemented(this, "setRowHeight", row, rowHeight);
    }

    public int getRowMargin() {
        return 1;      // JDK default
    }

    public void setRowMargin(int rowMargin) {
        SHelper.onUnimplemented(this, "setRowMargin", rowMargin);
    }

    public Dimension getIntercellSpacing() {
        return new Dimension(1, 1);   // JDK default
    }

    public void setIntercellSpacing(Dimension intercellSpacing) {
        SHelper.onUnimplemented(this, "setIntercellSpacing", intercellSpacing);
    }

    public Color getGridColor() {
        return Color.GRAY;   // JDK default
    }

    public void setGridColor(Color gridColor) {
        SHelper.onUnimplemented(this, "setGridColor", gridColor);
    }

    public boolean getShowHorizontalLines() {
        return true;   // JDK default
    }

    public void setShowHorizontalLines(boolean showHorizontalLines) {
        SHelper.onUnimplemented(this, "setShowHorizontalLines", showHorizontalLines);
    }

    public boolean getShowVerticalLines() {
        return true;   // JDK default
    }

    public void setShowVerticalLines(boolean showVerticalLines) {
        SHelper.onUnimplemented(this, "setShowVerticalLines", showVerticalLines);
    }

    public boolean getShowGrid() {
        return getShowHorizontalLines() && getShowVerticalLines();
    }

    public void setShowGrid(boolean showGrid) {
        SHelper.onUnimplemented(this, "setShowGrid", showGrid);
    }

    // --- Auto-resize mode (present-drop; keep JDK validation) ---

    public int getAutoResizeMode() {
        return AUTO_RESIZE_SUBSEQUENT_COLUMNS;   // JDK default (= 2)
    }

    public void setAutoResizeMode(int mode) {
        switch (mode) {
            case AUTO_RESIZE_OFF, AUTO_RESIZE_NEXT_COLUMN, AUTO_RESIZE_SUBSEQUENT_COLUMNS,
                 AUTO_RESIZE_LAST_COLUMN, AUTO_RESIZE_ALL_COLUMNS -> { /* ok */ }
            default -> throw new IllegalArgumentException("Unrecognized auto-resize mode: " + mode);
        }
        SHelper.onUnimplemented(this, "setAutoResizeMode", mode);
    }

    // --- Selection chrome (present-drop — Vaadin theme owns it) ---

    public Color getSelectionForeground() {
        return null;
    }

    public void setSelectionForeground(Color selectionForeground) {
        SHelper.onUnimplemented(this, "setSelectionForeground", selectionForeground);
    }

    public Color getSelectionBackground() {
        return null;
    }

    public void setSelectionBackground(Color selectionBackground) {
        SHelper.onUnimplemented(this, "setSelectionBackground", selectionBackground);
    }

    // --- Cell rectangle (R_layouts_close_enough close-enough) ---

    public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
        // Approximation: stack column widths from the column model; row
        // height from the JDK default (the emulator owns the real round-trip
        // value). Real pixel values come from the browser at render time
        // which we don't surface. Migrated layout code that subtracts from a
        // parent's bounds gets a directionally correct answer (R_layouts_close_enough).
        Rectangle r = new Rectangle();
        TableColumnModel cm = getColumnModel();
        int rh = getRowHeight();
        r.y = row * rh;
        r.height = rh;
        for (int i = 0; i < column && i < cm.getColumnCount(); i++) {
            r.x += cm.getColumn(i).getWidth();
        }
        if (column >= 0 && column < cm.getColumnCount()) {
            r.width = cm.getColumn(column).getWidth();
        }
        return r;
    }

    // --- Drag / DnD (present-drop) ---

    public boolean getDragEnabled() {
        return false;   // JDK default
    }

    public void setDragEnabled(boolean dragEnabled) {
        if (dragEnabled) {
            SHelper.onUnimplemented(this, "setDragEnabled", dragEnabled);
        }
    }

    // --- Fills viewport (Vaadin Grid sets its own height — present-drop) ---

    public boolean getFillsViewportHeight() {
        return false;   // JDK default
    }

    public void setFillsViewportHeight(boolean fillsViewportHeight) {
        SHelper.onUnimplemented(this, "setFillsViewportHeight", fillsViewportHeight);
    }

    public Dimension getPreferredScrollableViewportSize() {
        return null;
    }

    public void setPreferredScrollableViewportSize(Dimension size) {
        SHelper.onUnimplemented(this, "setPreferredScrollableViewportSize", size);
    }

    // --- Cell editor surface — deferred (R_vaadin_first drop-and-WARN) ---
    //
    // The cell-editor family stays out entirely per scope lock;
    // editCellAt / isEditing / getEditingRow / getEditingColumn surface
    // the canonical "no editor in flight" state. setCellEditor and
    // getDefaultEditor / setDefaultEditor stay on the emulator layer (the
    // ported TableCellEditor types live there); pure-surrogate users
    // don't get cell editing until a click-opens-popover implementation lands.

    public void editCellAt(int row, int column) {
        SHelper.onUnimplemented(this, "editCellAt", row, column);
    }

    public boolean editCellAt(int row, int column, java.util.EventObject e) {
        SHelper.onUnimplemented(this, "editCellAt", row, column, e);
        return false;
    }

    public boolean isEditing() {
        return false;
    }

    public int getEditingRow() {
        return -1;
    }

    public int getEditingColumn() {
        return -1;
    }

    public Component getEditorComponent() {
        return null;
    }

    public void removeEditor() {
        // No editor in flight — silent no-op.
    }

    // --- Mouse bridge: item-click → SMouseEvent + rowAtPoint/columnAtPoint stash ---

    /**
     * Overrides the inherited {@code ComponentMixin} wire — Grid dispatches
     * item clicks carrying the row item + clicked column, not generic
     * component clicks. Each {@code SMouseListener} gets two Grid
     * subscriptions combined into one {@link Registration}:
     * {@code addItemClickListener} fires {@link SMouseEvent#MOUSE_CLICKED}
     * with the browser click count, {@code addItemDoubleClickListener} fires
     * it with {@code clickCount == 2} — the double-click-to-edit/open idiom.
     * The clicked row (the Grid item, consistent with {@link #getSelectedRow}
     * / {@link #getValueAt}) and the view-column index are stashed around the
     * fan-out so {@link #rowAtPoint} / {@link #columnAtPoint} resolve during
     * the user's {@code mouseClicked} handler (R_layouts_close_enough — no pixel→cell map).
     * Mirrors SJList (SD_sjlist); columnAtPoint is the bonus Grid's item-click
     * column affords that SJList didn't need.
     */
    @Override
    public void addMouseListener(SMouseListener l) {
        Objects.requireNonNull(l);
        Registration click = addItemClickListener(e -> dispatchMouseClicked(l, e, e.getClickCount()));
        Registration dbl = addItemDoubleClickListener(e -> dispatchMouseClicked(l, e, 2));
        Registrations.of(this).add(l, Registration.combine(click, dbl));
    }

    private void dispatchMouseClicked(SMouseListener l, ItemClickEvent<Integer> e, int clickCount) {
        SHelper.callSwing(() -> {
            TableStateStore store = store();
            int prevRow = store.clickRowStash;
            int prevCol = store.clickColumnStash;
            store.clickRowStash = (e.getItem() == null) ? -1 : e.getItem();
            store.clickColumnStash = (e.getColumn() == null) ? -1 : getColumns().indexOf(e.getColumn());
            try {
                int modifiers = 0;
                if (e.isShiftKey()) modifiers |= SInputEvent.SHIFT_DOWN_MASK;
                if (e.isCtrlKey())  modifiers |= SInputEvent.CTRL_DOWN_MASK;
                if (e.isAltKey())   modifiers |= SInputEvent.ALT_DOWN_MASK;
                if (e.isMetaKey())  modifiers |= SInputEvent.META_DOWN_MASK;
                int button = switch (e.getButton()) {
                    case 0  -> SMouseEvent.BUTTON1;
                    case 1  -> SMouseEvent.BUTTON2;
                    case 2  -> SMouseEvent.BUTTON3;
                    default -> SMouseEvent.NOBUTTON;
                };
                l.mouseClicked(new SMouseEvent(this, SMouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), modifiers,
                        e.getClientX(), e.getClientY(), clickCount, false, button));
            } finally {
                store.clickRowStash = prevRow;
                store.clickColumnStash = prevCol;
            }
        });
    }

    /**
     * Row index of the cell currently being click-dispatched (stashed by
     * {@link #addMouseListener}'s item-click wire), or {@code -1} outside a
     * dispatch. Consistent with {@link #getSelectedRow} / {@link #getValueAt}
     * — there is no server-side pixel→row map (R_layouts_close_enough). Graduates from the
     * pre-(b) drop-and-WARN.
     */
    public int rowAtPoint(java.awt.Point point) {
        return store().clickRowStash;
    }

    /**
     * View-column index of the cell currently being click-dispatched, or
     * {@code -1} (outside a dispatch, or a row-only click that carries no
     * column). Resolved from the Grid's clicked column (R_layouts_close_enough).
     */
    public int columnAtPoint(java.awt.Point point) {
        return store().clickColumnStash;
    }

    // --- L&F stub (JDK contract) ---

    public String getUIClassID() {
        return "TableUI";
    }
}
