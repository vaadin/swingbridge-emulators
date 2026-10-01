/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JTable
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JTable, peered on
// com.vaadin.swingbridge.surrogates.SJTable, which renders the Grid<Integer> (SD_sjtable).
// The table owns its state: the TableModel, the view-row selection model, the row sorter
// and the ported column model are this class's, and no getter reads the peer
// (D_emulator_owned_state).
//
// Two-layer column-model bridge (D_jtable_column_model_bridge):
//   - emulator owns a vaadinx.swing.table.TableColumnModel (ported, user-
//     facing).
//   - surrogate owns a javax.swing.table.TableColumnModel (JDK, Grid-
//     facing).
//   - one listener bridge keeps the two in sync, ported → JDK: every
//     mutation of the ported model, this table's own
//     createDefaultColumnsFromModel included, translates to a JDK op on the
//     surrogate (which fires JDK events that drive Vaadin column rebuild).
//     The other direction runs once, in the ctor (rebuildPortedColumnsFromJdk),
//     guarded by preventColumnLoop per R_swing_is_truth.
//
// Renderer bridge (D_event_port_policy + D_jcombobox + R_vaadin_first): emulator carries a per-class
// TableCellRenderer registry + per-column TableColumn.getCellRenderer
// fallback. Vaadin Renderers installed on the surrogate via
// SJTable.setRenderer wrap a closure that:
//   1. resolves the current TableCellRenderer for this cell via emulator
//      lookup (TableColumn.cellRenderer || default-by-column-class fallback);
//   2. invokes renderer.getTableCellRendererComponent(this, value,
//      isSelected, false, row, viewCol);
//   3. snapshots the result into a fresh Vaadin Span (mirrors JComboBox
//      D_jcombobox — same-instance-returned-multiple-times defeats Vaadin's DOM-
//      adoption diff).
// The Vaadin Renderer is dynamic — no re-install needed when user changes
// renderers on TableColumns or setDefaultRenderer.
//
// R_leaf_peer_lockdown lock-down: javax.swing.JTable is a leaf in the public Swing
// hierarchy, so the protected (Component peer) ctor is omitted; all public
// ctors funnel through the private (SJTable) ctor.

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.swingbridge.surrogates.SJTable;
import vaadinx.swing.event.TableColumnModelEvent;
import vaadinx.swing.event.TableColumnModelListener;
import vaadinx.swing.table.DefaultTableCellRenderer;
import vaadinx.swing.table.DefaultTableColumnModel;
import vaadinx.swing.table.JTableHeader;
import vaadinx.swing.table.TableCellEditor;
import vaadinx.swing.table.TableCellRenderer;
import vaadinx.swing.table.TableColumn;
import vaadinx.swing.table.TableColumnModel;

import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Emulator for {@link javax.swing.JTable}, over an {@link SJTable} peer;
 * R_leaf_peer_lockdown-locked-down (JDK leaf — private peer ctor).
 * Two-layer column model with bridge listeners; per-class TableCellRenderer
 * registry on the emulator, dynamic Vaadin renderers on the surrogate.
 */
public class JTable extends vaadinx.swing.JComponent
        implements TableModelListener, ListSelectionListener,
        vaadinx.swing.event.TableColumnModelListener,
        javax.swing.event.CellEditorListener, javax.swing.event.RowSorterListener,
        javax.accessibility.Accessible, javax.swing.Scrollable, vaadinx.FieldReconciler.Reconcilable {

    // --- JDK auto-resize-mode constants ---

    public static final int AUTO_RESIZE_OFF = 0;
    public static final int AUTO_RESIZE_NEXT_COLUMN = 1;
    public static final int AUTO_RESIZE_SUBSEQUENT_COLUMNS = 2;
    public static final int AUTO_RESIZE_LAST_COLUMN = 3;
    public static final int AUTO_RESIZE_ALL_COLUMNS = 4;

    // --- State ---

    /**
     * User-facing column model (ported types). Mirror of the surrogate's
     * JDK column model; bridge listeners keep them in sync.
     */
    protected TableColumnModel columnModel;

    /** Listener installed on {@link #columnModel} that translates ported events to JDK ops on the surrogate. */
    private TableColumnModelListener portedToJdkBridge;

    /**
     * Loop guard for the ported↔JDK column-model sync. Set during a one-
     * direction push so the reverse listener bails. Same R_swing_is_truth shape as
     * SJTable's preventPeerEvents.
     */
    private boolean preventColumnLoop;

    /** Per-column-class fallback renderer registry (ported types). Walks superclass chain on lookup. */
    protected final Map<Class<?>, TableCellRenderer> defaultRenderersByColumnClass = new HashMap<>();

    /** Per-column-class default cell-editor registry (ported types). Walks superclass chain on lookup. Mirrors {@link #defaultRenderersByColumnClass}. */
    protected final Map<Class<?>, TableCellEditor> defaultEditorsByColumnClass = new HashMap<>();

    /** The cell editor in flight during editing (JDK {@code cellEditor}); {@code null} when not editing. */
    protected TableCellEditor cellEditor;

    /**
     * Empty placeholder Binder for the inherited {@code Grid.Editor} (D_jtable_cell_editing).
     * Vaadin's editor throws without a binder, but needs no bindings — value
     * flows through the Swing editor lifecycle ({@code getTableCellEditorComponent}
     * seeds, {@code getCellEditorValue} commits), not through the binder.
     */
    private final Binder<Integer> editorBinder = new Binder<>();

    /** View row/column being edited; {@code -1} when not editing. */
    protected int editingRow = -1;
    protected int editingColumn = -1;

    /** The seeded editing component in flight (its peer is the column's editor component); {@code null} when not editing. */
    private vaadinx.awt.Component editingComponent;

    /**
     * Escape-cancels-the-edit shortcut, registered on open and removed on cleanup
     * (B9). Vaadin's built-in Grid-editor Escape handling doesn't fire for our
     * programmatic single-column {@code setEditorComponent}, so we wire our own.
     */
    private com.vaadin.flow.component.ShortcutRegistration escapeCancelShortcut;

    /** Lazy {@link JTableHeader} holder. */
    protected JTableHeader tableHeader;

    /**
     * No-Vaadin-counterpart JDK knobs round-trip on the emulator only
     * (R_swing_is_truth-faithful) + fire PCE. The surrogate drops them per R_vaadin_first (SD_sjtable) —
     * the asymmetric R_swing_is_truth-shadow split SJToolBar (D_jtoolbar) / JSplitPane (D_jsplitpane) /
     * JList (D_jlist) established. Defaults match JDK JTable. `dragEnabled(true)`
     * and `setCellSelectionEnabled` WARN (real gaps); the rest round-trip
     * silently (R_layouts_close_enough sizing/chrome).
     */
    protected boolean rowSelectionAllowed = true;
    private boolean columnSelectionAllowed = false;
    protected int rowHeight = 16;
    protected int rowMargin = 1;
    protected Color gridColor = Color.GRAY;
    protected boolean showHorizontalLines = true;
    protected boolean showVerticalLines = true;
    protected int autoResizeMode = AUTO_RESIZE_SUBSEQUENT_COLUMNS;
    protected Color selectionForeground;
    protected Color selectionBackground;
    private boolean dragEnabled;
    private boolean fillsViewportHeight;
    // JDK field name — shorter than its own accessor pair (D_instance_field_surface). The
    // default is the one JDK initializeLocalVars installs.
    protected Dimension preferredViewportSize = new Dimension(450, 400);

    // --- Ctors ---

    public JTable() {
        this(new SJTable());
    }

    public JTable(TableModel dm) {
        this(new SJTable(dm));
    }

    public JTable(TableModel dm, TableColumnModel cm) {
        this(new SJTable(dm, jdkMirrorOf(cm)));
        // User supplied a ported column model — replace our default with theirs.
        if (cm != null) swapColumnModel(cm);
    }

    public JTable(TableModel dm, TableColumnModel cm, ListSelectionModel sm) {
        // sm holds view rows, so it is this table's model rather than the surrogate's, which
        // holds model rows and keeps its own default (see setSelectionModel).
        this(new SJTable(dm, jdkMirrorOf(cm)), sm);
        if (cm != null) swapColumnModel(cm);
    }

    public JTable(int numRows, int numCols) {
        this(new SJTable(numRows, numCols));
    }

    public JTable(Object[][] rowData, Object[] columnNames) {
        this(new SJTable(rowData, columnNames));
    }

    @SuppressWarnings("rawtypes")
    public JTable(Vector rowData, Vector columnNames) {
        this(new SJTable((Vector) rowData, (Vector) columnNames));
    }

    private JTable(SJTable peer) {
        this(peer, null);
    }

    /**
     * Private ctor: R_leaf_peer_lockdown lock-down (JDK leaf — no protected (Component) ctor).
     *
     * @param sm the view-row selection model, or {@code null} for {@link #createDefaultSelectionModel()}'s
     */
    private JTable(SJTable peer, ListSelectionModel sm) {
        super(peer);
        // The table listens to its own model, as the JDK's does, and drives the surrogate's
        // handling from tableChanged — so the sort bookkeeping runs around the moment the
        // sorter hears the change, and a subclass's tableChanged override is on the path.
        // First, because every accessor below reads the model through dataModel.
        peer.getModel().removeTableModelListener(peer);
        listenToModel(peer.getModel());
        // The table creates its own columns and tells its own sorter about model changes, as
        // the JDK's does, on the thread the model fired on; the surrogate only renders them, so
        // a write queued until attach never holds back what Swing reads (D_emulator_owned_state).
        // Seeded from the peer first: the public ctors configured it as the JDK's would.
        autoCreateColumnsFromModel = peer.getAutoCreateColumnsFromModel();
        peer.setAutoCreateColumnsFromModel(false);
        peer.setSorterNotifiedByOwner(true);

        // Seed the default renderer registry. JDK seeds Object/Number/Date/
        // Boolean/Icon/ImageIcon — Object + Boolean ship; the rest ride the
        // Object fallback (R_match_swing_errors sub-bucket (a) for now). Boolean mirrors
        // JDK's BooleanRenderer: a centered, disabled JCheckBox reflecting
        // the value. A fresh instance per render — Vaadin Grid's DOM
        // adoption can't reuse a shared peer across cells.
        defaultRenderersByColumnClass.put(Object.class, new DefaultTableCellRenderer());
        defaultRenderersByColumnClass.put(Boolean.class, (table, value, isSelected, hasFocus, row, column) -> {
            JCheckBox cb = new JCheckBox();
            cb.setSelected(Boolean.TRUE.equals(value));
            cb.setHorizontalAlignment(SwingConstants.CENTER);
            cb.setEnabled(false);
            return cb;
        });

        // Seed the default cell editors, mirroring JDK JTable.createDefaultEditors():
        // Object -> GenericEditor (String-ctor coercion), Number -> NumberEditor,
        // Boolean -> BooleanEditor. getCellEditorValue coerces String -> columnClass
        // (D_jtable_cell_editing) so migrated code reading it back gets the typed value.
        defaultEditorsByColumnClass.put(Object.class, new GenericEditor());
        defaultEditorsByColumnClass.put(Number.class, new NumberEditor());
        defaultEditorsByColumnClass.put(Boolean.class, new BooleanEditor());

        // Build the emulator's ported column model from the surrogate's JDK one
        // (which the SJTable ctor already populated via createDefaultColumnsFromModel).
        this.columnModel = new DefaultTableColumnModel();
        rebuildPortedColumnsFromJdk();

        // Install the two bridge listeners.
        this.portedToJdkBridge = new TableColumnModelListener() {
            // Each mirrors one event in one write, the surrogate's columns read only inside it:
            // a write queued until attach meets them as the earlier queued writes left them.
            @Override public void columnAdded(TableColumnModelEvent e) {
                if (preventColumnLoop) return;
                javax.swing.table.TableColumn mirror = jdkMirrorOf(columnModel.getColumn(e.getToIndex()));
                withPeer(p -> surrogate().getColumnModel().addColumn(mirror));
            }
            @Override public void columnRemoved(TableColumnModelEvent e) {
                if (preventColumnLoop) return;
                int idx = e.getFromIndex();
                withPeer(p -> {
                    javax.swing.table.TableColumnModel jdk = surrogate().getColumnModel();
                    if (idx < jdk.getColumnCount()) jdk.removeColumn(jdk.getColumn(idx));
                });
            }
            @Override public void columnMoved(TableColumnModelEvent e) {
                if (preventColumnLoop) return;
                if (e.getFromIndex() == e.getToIndex()) return;
                withPeer(p -> surrogate().getColumnModel().moveColumn(e.getFromIndex(), e.getToIndex()));
            }
            @Override public void columnMarginChanged(ChangeEvent e) {
                // Bridge isn't critical — Vaadin Grid owns column margin.
            }
            @Override public void columnSelectionChanged(ListSelectionEvent e) {
                // Column selection R_vaadin_first drop — Vaadin Grid is row-only.
            }
        };
        this.columnModel.addColumnModelListener(portedToJdkBridge);

        // No JDK→ported listener bridge: the surrogate never creates columns of its own
        // (its autoCreateColumnsFromModel is off), so after this sync every column
        // change starts on the ported model. setColumnModel and
        // createDefaultColumnsFromModel re-install the emulator renderers once the
        // bridge has mirrored their changes.

        // Install the dynamic Vaadin renderer on every existing column.
        installEmulatorVaadinRenderers();

        // This class sources its own MOUSE_CLICKED just below, off the surrogate's
        // Grid item-click wire, which also reports the cell for rowAtPoint /
        // columnAtPoint. Keep Component's generic DOM bridge out, or every click
        // arrives twice.
        suppressMouseBridge();

        // Mouse re-source (D_jtable (b)): the surrogate's MOUSE_CLICKED (driven by
        // Grid item-click / item-double-click, carrying the row + column) →
        // a vaadinx MouseEvent dispatched via processMouseEvent, with the clicked
        // cell held for rowAtPoint / columnAtPoint, so table.addMouseListener +
        // e.getClickCount()==2 + rowAtPoint/columnAtPoint (the double-click-to-edit
        // idiom) works.
        surrogate().addMouseListener(new com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter() {
            @Override
            public void mouseClicked(com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent e) {
                // Read inside the surrogate's item-click dispatch, the one place its stash holds
                // the cell. The Grid item is a model row; the pixel under the click is a view row.
                int row = toViewRow(surrogate().rowAtPoint(e.getPoint()));
                int column = surrogate().columnAtPoint(e.getPoint());
                vaadinx.EHelper.callSwing(() -> {
                    int previousRow = clickRowStash;
                    int previousColumn = clickColumnStash;
                    clickRowStash = row;
                    clickColumnStash = column;
                    try {
                        processMouseEvent(new vaadinx.awt.event.MouseEvent(
                                JTable.this, vaadinx.awt.event.MouseEvent.MOUSE_CLICKED,
                                e.getWhen(), e.getModifiersEx(), e.getX(), e.getY(),
                                e.getClickCount(), e.isPopupTrigger(), e.getButton()));
                    } finally {
                        clickRowStash = previousRow;
                        clickColumnStash = previousColumn;
                    }
                });
            }
        });

        // Cell-editing bridge (D_jtable_cell_editing). The inherited Grid.Editor is the overlay +
        // open mechanism; a binding-less Binder satisfies Vaadin's "editor needs a
        // binder" requirement (it throws otherwise), while value seed/commit runs
        // through the Swing editor lifecycle. Non-buffered so the editor closes on
        // blur/Escape. Single-column: setEditorComponent is set per open on the
        // edited column only (see editCellAt). Close → commit (blur), cancel →
        // discard (Escape) — both funnel through the Swing editor per R_callswing_envelope.
        surrogate().getEditor().setBinder(editorBinder);
        surrogate().getEditor().setBuffered(false);
        surrogate().getEditor().addCancelListener(e -> vaadinx.EHelper.callSwing(this::onVaadinEditorCancelled));
        surrogate().getEditor().addCloseListener(e -> {
            // Vaadin closes its editor on every data change; a JDK edit survives a model
            // change, so this close commits nothing and the Grid editor comes back on its row.
            if (surrogate().isEditorClosingForDataChange()) {
                scheduleGridEditorReopen();
                return;
            }
            vaadinx.EHelper.callSwing(this::onVaadinEditorClosed);
        });

        // Editing trigger — the JTable clickCountToStart rule: a double-click starts
        // editing any editable cell; a single click starts it only for editors with
        // clickCountToStart == 1 (checkbox / combo). Column comes from the event.
        surrogate().addItemClickListener(e -> vaadinx.EHelper.callSwing(
                () -> startEditFromClick(e.getItem(), e.getColumn(), e.getClickCount())));
        surrogate().addItemDoubleClickListener(e -> vaadinx.EHelper.callSwing(
                () -> startEditFromClick(e.getItem(), e.getColumn(), 2)));

        // Two selection models (D_jtable_selection): this table's holds view rows, the JDK
        // contract; the surrogate's holds model rows and drives the Grid. Browser selections
        // arrive on the surrogate's and are pulled across; everything else is pushed to it.
        peer.getListSelectionModel().addListSelectionListener(peerSelectionBridge);
        setSelectionModel(sm != null ? sm : createDefaultSelectionModel());

        vaadinx.FieldReconciler.register(this, peer);
    }

    /**
     * Makes this table the listener on {@code model}. The surrogate registers itself when it
     * installs a model and would otherwise apply every change a second time, outside this
     * table's sort bookkeeping, so whoever hands it a model removes it again — in the same
     * write, since that write may run later than this.
     */
    private void listenToModel(TableModel model) {
        if (listenedModel != null) listenedModel.removeTableModelListener(this);
        dataModel = listenedModel = model;
        model.addTableModelListener(this);
    }

    /** The model this table is registered on — what {@link #reconcileFields} detects a direct {@link #dataModel} write against. */
    private TableModel listenedModel;

    /**
     * Returns the default selection model object, which is a {@code DefaultListSelectionModel}.
     * A subclass can override this to return a different selection model object.
     */
    protected ListSelectionModel createDefaultSelectionModel() {
        return new javax.swing.DefaultListSelectionModel();
    }

    // JDK protected fields, Swing-side truth per D_field_write_reconcile (see JSlider for
    // the canonical commentary). Both models are second pointers to the objects the
    // surrogate's machinery runs on (columnModel, the ported third sibling, was already
    // carried). autoCreateColumnsFromModel is this table's alone, read where the JDK reads it,
    // so a direct write needs no repair.
    protected TableModel dataModel;
    protected ListSelectionModel selectionModel;
    protected boolean autoCreateColumnsFromModel;

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (dataModel != listenedModel) {
            // Same work as setModel's body: push, then rebuild the ported column model and
            // re-install the Vaadin renderers the surrogate's rebuild replaced.
            TableModel written = dataModel;
            dataModel = listenedModel;
            setModel(written);
            vaadinx.FieldReconciler.reportDirectWrite(this, "dataModel", "setModel");
        }
        if (selectionModel != listenedSelectionModel) {
            ListSelectionModel written = selectionModel;
            selectionModel = listenedSelectionModel;
            setSelectionModel(written);
            vaadinx.FieldReconciler.reportDirectWrite(this, "selectionModel", "setSelectionModel");
        }
    }

    private SJTable surrogate() {
        return (SJTable) getPeer();
    }

    /**
     * Build a JDK {@link javax.swing.table.DefaultTableColumnModel} from a
     * ported {@link TableColumnModel}. Used to seed the surrogate when a
     * user-supplied ported column model is passed to the ctor — the
     * surrogate keeps the JDK mirror, the emulator stores the user's
     * ported reference and bridges between them.
     */
    private static javax.swing.table.TableColumnModel jdkMirrorOf(TableColumnModel ported) {
        if (ported == null) return null;
        javax.swing.table.DefaultTableColumnModel jdk = new javax.swing.table.DefaultTableColumnModel();
        for (int i = 0; i < ported.getColumnCount(); i++) {
            jdk.addColumn(jdkMirrorOf(ported.getColumn(i)));
        }
        return jdk;
    }

    /**
     * Build a JDK {@link javax.swing.table.TableColumn} from a ported one.
     * Copies modelIndex / headerValue / width / minWidth / maxWidth /
     * preferredWidth / identifier / resizable. The cellRenderer field is
     * not copied — the JDK column on the surrogate side never reads it
     * (the surrogate ignores TableCellRenderer per R_vaadin_first).
     */
    private static javax.swing.table.TableColumn jdkMirrorOf(TableColumn ported) {
        javax.swing.table.TableColumn jdk = new javax.swing.table.TableColumn(
                ported.getModelIndex(), ported.getWidth());
        jdk.setHeaderValue(ported.getHeaderValue());
        jdk.setIdentifier(ported.getIdentifier());
        jdk.setMinWidth(ported.getMinWidth());
        jdk.setMaxWidth(ported.getMaxWidth());
        jdk.setPreferredWidth(ported.getPreferredWidth());
        jdk.setResizable(ported.getResizable());
        return jdk;
    }

    /**
     * Replace the emulator's ported column model with a user-supplied one
     * after the surrogate has been seeded with the JDK mirror. Used by
     * the JTable(TableModel, TableColumnModel) ctor — super(new SJTable(...
     * jdkMirrorOf(cm))) passed the mirror to the surrogate, but we want
     * the user's ported reference to survive as our public-facing column
     * model so listener registrations on it work. Tears down the default
     * ported model + bridges installed in the private ctor and re-attaches
     * to the user's reference.
     */
    private void swapColumnModel(TableColumnModel newPortedCm) {
        if (newPortedCm == columnModel) return;
        // Detach bridges from the old (default) ported model.
        if (columnModel != null && portedToJdkBridge != null) {
            columnModel.removeColumnModelListener(portedToJdkBridge);
        }
        this.columnModel = newPortedCm;
        // Re-attach the same listener (it closes over our state, not over
        // the old model reference).
        if (portedToJdkBridge != null) {
            columnModel.addColumnModelListener(portedToJdkBridge);
        }
        // No structural rebuild — the surrogate already has the JDK mirror
        // matching this ported model.
        firePropertyChange("columnModel", null, newPortedCm);
    }

    /**
     * Rebuild the ported {@link #columnModel} from the surrogate's JDK column model: the
     * constructor's one sync, after the {@code SJTable} ctor built its columns the way the
     * matching JDK ctor would. Every later structure change is this table's own
     * {@link #createDefaultColumnsFromModel}, mirrored the other way.
     *
     * <p>Wraps mutations in {@link #preventColumnLoop} so the
     * portedToJdkBridge doesn't echo our writes back into the surrogate.
     */
    private void rebuildPortedColumnsFromJdk() {
        preventColumnLoop = true;
        try {
            while (columnModel.getColumnCount() > 0) {
                columnModel.removeColumn(columnModel.getColumn(0));
            }
            javax.swing.table.TableColumnModel jdk = surrogate().getColumnModel();
            for (int i = 0; i < jdk.getColumnCount(); i++) {
                javax.swing.table.TableColumn jTc = jdk.getColumn(i);
                TableColumn pTc = new TableColumn(jTc.getModelIndex(), jTc.getWidth());
                pTc.setHeaderValue(jTc.getHeaderValue());
                pTc.setIdentifier(jTc.getIdentifier());
                pTc.setMinWidth(jTc.getMinWidth());
                pTc.setMaxWidth(jTc.getMaxWidth());
                pTc.setPreferredWidth(jTc.getPreferredWidth());
                pTc.setResizable(jTc.getResizable());
                watchCellRenderer(pTc);
                columnModel.addColumn(pTc);
            }
        } finally {
            preventColumnLoop = false;
        }
        // After the ported model settles, re-install Vaadin renderers on
        // the surrogate (column count or order may have changed).
        installEmulatorVaadinRenderers();
    }

    /**
     * A per-column cell-renderer change can flip the column between the text and component
     * render paths (D_jtable_cell_editing), so it re-installs the renderers and refreshes.
     * (No loop: installEmulatorVaadinRenderers touches only the surrogate's Vaadin columns,
     * never the ported TableColumn's cellRenderer.)
     */
    private void watchCellRenderer(TableColumn column) {
        column.addPropertyChangeListener(evt -> {
            if ("cellRenderer".equals(evt.getPropertyName())) {
                withPeer(p -> {
                    installEmulatorVaadinRenderers();
                    var dp = surrogate().getDataProvider();
                    if (dp != null) dp.refreshAll();
                });
            }
        });
    }

    /**
     * Creates default columns for this table from the data model, as the JDK's does: removes
     * every column and adds one per model column. The column bridge mirrors each change onto
     * the surrogate, so a column-model listener hears the JDK's remove and add events.
     */
    public void createDefaultColumnsFromModel() {
        TableModel m = getModel();
        if (m != null) {
            // Remove any current columns
            TableColumnModel cm = getColumnModel();
            while (cm.getColumnCount() > 0) {
                cm.removeColumn(cm.getColumn(0));
            }

            // Create new columns from the data model info
            for (int i = 0; i < m.getColumnCount(); i++) {
                TableColumn newColumn = new TableColumn(i);
                watchCellRenderer(newColumn);
                addColumn(newColumn);
            }
        }
        installEmulatorVaadinRenderers();
    }

    /**
     * Install one Vaadin renderer per surrogate column, choosing the cheapest
     * faithful renderer per column (D_jtable_renderer_registry + the D_jtable_cell_editing rendering-perf refactor):
     * <ul>
     *   <li><b>Plain-default text columns</b> ({@link #isPlainTextColumn}) get a
     *       {@link TextRenderer} — plain text, <em>no component per cell</em> (the
     *       Grid scroll-perf win).</li>
     *   <li><b>Boolean / custom / {@code DefaultTableCellRenderer}-subclass columns</b>
     *       get the dynamic {@link ComponentRenderer} that re-resolves the
     *       {@link TableCellRenderer} on every render and snapshots it — so a
     *       renderer change on such a column takes effect without re-install.</li>
     * </ul>
     * A renderer change that flips a column <em>between</em> the two paths
     * (default ↔ custom) re-runs this method — cheap now that
     * {@link SJTable#setRenderer} swaps in place ({@code Grid.Column.setRenderer})
     * without churning the column. Re-run from {@link #setDefaultRenderer} and the
     * per-column {@code cellRenderer} PCE (see {@link #rebuildPortedColumnsFromJdk}).
     */
    private void installEmulatorVaadinRenderers() {
        withPeer(p -> {
            SJTable sb = surrogate();
            int n = sb.getColumnModel().getColumnCount();
            for (int i = 0; i < n; i++) {
                final int viewCol = i;
                if (isPlainTextColumn(viewCol)) {
                    sb.setRenderer(viewCol, new TextRenderer<>(rowKey -> textForCell(rowKey, viewCol)));
                } else {
                    sb.setRenderer(viewCol, new ComponentRenderer<>(
                            rowKey -> renderCellViaEmulator(rowKey, viewCol)));
                }
            }
        });
    }

    /**
     * True when the column renders as plain text — its resolved renderer is a
     * bare {@link DefaultTableCellRenderer} (exact class, not a subclass) and the
     * column class isn't {@code Boolean} (which renders a checkbox glyph). Such
     * cells skip {@link ComponentRenderer} for a cheap {@link TextRenderer}.
     */
    private boolean isPlainTextColumn(int viewCol) {
        if (getColumnClass(viewCol) == Boolean.class) return false;
        TableCellRenderer r = getCellRenderer(0, viewCol);
        return r != null && r.getClass() == DefaultTableCellRenderer.class;
    }

    /** Cell text for the {@link TextRenderer} path — matches {@code DefaultTableCellRenderer}'s {@code value.toString()}. */
    private String textForCell(Integer rowKey, int viewCol) {
        if (rowKey == null) return "";
        int row = rowKey;
        if (row < 0 || row >= surrogate().getRowCount()) return "";
        // rowKey is the Grid item = model row; go straight to the model-indexed
        // surrogate (the public getValueAt would view→model convert it again). (D_jtable_cell_editing)
        Object v = surrogate().getValueAt(row, viewCol);
        return v == null ? "" : String.valueOf(v);
    }

    /**
     * Per-cell render path used by the emulator's dynamic Vaadin renderer.
     * Resolves the {@link TableCellRenderer} via the column's cellRenderer
     * field then the default-by-column-class fallback, calls it with
     * current cell state, snapshots the result.
     *
     * <p>The result is made <b>click-transparent</b> ({@code pointer-events: none}):
     * a rendered cell is display only — interaction goes through the Grid (row
     * select, click-to-edit). A read-only Checkbox glyph (Boolean column) otherwise
     * swallows the pointer event, so the Grid never fires item-click and
     * {@code editCellAt} / selection silently fails on that cell (B3).
     */
    private com.vaadin.flow.component.Component renderCellViaEmulator(Integer rowKey, int viewCol) {
        // Editable Boolean cells render as a LIVE interactive checkbox that writes to the
        // model on toggle (B3) — bypassing the racy single-click Grid.Editor for checkboxes
        // (a single click both selects the row and opens the editor; they raced). It stays
        // interactive (NOT pointer-events:none) so it receives the click and toggles;
        // isFromClient guards the value-change so the setValueAt→refresh re-render can't loop.
        // Non-editable Boolean cells + all other columns fall through to the display render.
        // rowKey / brow / row below are the Grid item = *model* row, so all model
        // access here goes straight to the model-indexed surrogate rather than the
        // emulator's view-indexed public accessors, which would convert again (D_jtable_cell_editing).
        if (rowKey != null && rowKey >= 0 && rowKey < surrogate().getRowCount()
                && getColumnClass(viewCol) == Boolean.class && surrogate().isCellEditable(rowKey, viewCol)) {
            final int brow = rowKey;
            var live = new com.vaadin.flow.component.checkbox.Checkbox(
                    Boolean.TRUE.equals(surrogate().getValueAt(brow, viewCol)));
            live.addValueChangeListener(e -> {
                if (!e.isFromClient()) return;
                vaadinx.EHelper.callSwing(() -> surrogate().setValueAt(e.getValue(), brow, viewCol));
            });
            return live;
        }

        com.vaadin.flow.component.Component out;
        if (rowKey == null || rowKey < 0 || rowKey >= surrogate().getRowCount()) {
            out = new Span("");
        } else {
            int row = rowKey;  // model row (Grid item)
            TableCellRenderer r = getCellRenderer(row, viewCol);
            Object value = surrogate().getValueAt(row, viewCol);
            if (r == null) {
                out = new Span(value == null ? "" : String.valueOf(value)); // no renderer → value.toString()
            } else {
                // Through prepareRenderer, not straight to the renderer, so a migrated
                // subclass's override of that JDK hook is on the render path (D_r12_provenance) —
                // emulating Swing's call hierarchy, not just its API. It is view-indexed
                // (JDK contract), so convert model→view on the way in and accept the
                // round-trip back to the model inside: two sorter conversions per cell,
                // the price of the hook being reachable.
                vaadinx.awt.Component rendered = prepareRenderer(r, toViewRow(row), viewCol);
                out = JComboBox.snapshotRendererOutput(rendered, value);
            }
        }
        out.getElement().getStyle().set("pointer-events", "none");
        return out;
    }

    // --- Model accessors ---

    public TableModel getModel() {
        return dataModel;
    }

    public void setModel(TableModel newModel) {
        if (newModel == null) {
            throw new IllegalArgumentException("Cannot set a null TableModel");
        }
        if (dataModel != newModel) {
            TableModel old = dataModel;
            // The surrogate swaps its own model, dropping any sorter (SD_sjtable_sorting), and
            // registers on it; this table takes the listener over, in the same write.
            onPeerSelectionMuted(() -> {
                surrogate().setModel(newModel);
                newModel.removeTableModelListener(surrogate());
            });
            listenToModel(newModel);
            if (sortManager != null) {
                sortManager.dispose();
                sortManager = null;
            }
            // The JDK's own structure change: clears the selection and re-creates the columns.
            tableChanged(new TableModelEvent(newModel, TableModelEvent.HEADER_ROW));
            firePropertyChange("model", old, newModel);
            if (getAutoCreateRowSorter()) {
                setRowSorter(new javax.swing.table.TableRowSorter<TableModel>(newModel));
            }
        }
    }

    public TableColumnModel getColumnModel() {
        return columnModel;
    }

    public void setColumnModel(TableColumnModel newModel) {
        java.util.Objects.requireNonNull(newModel, "Cannot set a null TableColumnModel");
        TableColumnModel old = columnModel;
        if (old == newModel) return;
        // Detach bridge from old ported model.
        if (old != null && portedToJdkBridge != null) {
            old.removeColumnModelListener(portedToJdkBridge);
        }
        // Mirrored here, not in the write: a write queued until attach must carry the columns as
        // they are now.
        javax.swing.table.TableColumnModel mirror = jdkMirrorOf(newModel);
        withPeer(p -> surrogate().setColumnModel(mirror));
        // Now adopt the user's reference + re-attach our bridge.
        this.columnModel = newModel;
        if (portedToJdkBridge != null) {
            columnModel.addColumnModelListener(portedToJdkBridge);
        }
        // Re-install Vaadin renderers on the surrogate's freshly-rebuilt columns.
        installEmulatorVaadinRenderers();
        firePropertyChange("columnModel", old, newModel);
    }

    /** The row selection model, in <em>view</em> rows (JDK contract, D_jtable_selection). */
    public ListSelectionModel getSelectionModel() {
        return selectionModel;
    }

    /**
     * Installs the row selection model. It holds <em>view</em> rows, as the JDK's does; the
     * table re-maps it through the sorter when rows re-sort or the model changes, and mirrors
     * it onto the surrogate's model-row selection that drives the Grid (D_jtable_selection).
     */
    public void setSelectionModel(ListSelectionModel newModel) {
        if (newModel == null) {
            throw new IllegalArgumentException("Cannot set a null SelectionModel");
        }
        ListSelectionModel oldModel = selectionModel;
        if (newModel != oldModel) {
            if (oldModel != null) {
                oldModel.removeListSelectionListener(this);
            }
            selectionModel = listenedSelectionModel = newModel;
            newModel.addListSelectionListener(this);
            firePropertyChange("selectionModel", oldModel, newModel);
            pushSelectionToPeer();
        }
    }

    /** The selection model this table listens to — {@link #reconcileFields}' baseline for {@link #selectionModel}. */
    private ListSelectionModel listenedSelectionModel;

    // --- Cell access ---

    /** The number of rows the table shows — the sorter's view count when one is installed (JDK contract). */
    public int getRowCount() {
        javax.swing.RowSorter<?> sorter = getRowSorter();
        if (sorter != null) {
            return sorter.getViewRowCount();
        }
        return getModel().getRowCount();
    }
    // The JDK's bodies, over this table's own models (D_emulator_owned_state):
    // no getter here reads the peer. The surrogate's Grid item is a *model* row
    // (D_jtable_cell_editing), so render code that already holds one talks to surrogate() directly.
    // Note getColumnName asks the model and ignores the column's header value, as the JDK's does.
    public int getColumnCount() {
        return getColumnModel().getColumnCount();
    }

    public String getColumnName(int column) {
        return getModel().getColumnName(convertColumnIndexToModel(column));
    }

    public Class<?> getColumnClass(int column) {
        return getModel().getColumnClass(convertColumnIndexToModel(column));
    }

    public Object getValueAt(int row, int column) {
        return getModel().getValueAt(convertRowIndexToModel(row),
                                     convertColumnIndexToModel(column));
    }

    public void setValueAt(Object aValue, int row, int column) {
        getModel().setValueAt(aValue, convertRowIndexToModel(row),
                              convertColumnIndexToModel(column));
    }

    public boolean isCellEditable(int row, int column) {
        return getModel().isCellEditable(convertRowIndexToModel(row),
                                         convertColumnIndexToModel(column));
    }

    // --- View ↔ model index conversion ---

    public int convertColumnIndexToView(int modelColumnIndex) {
        if (modelColumnIndex < 0) {
            return modelColumnIndex;
        }
        TableColumnModel cm = getColumnModel();
        for (int column = 0; column < cm.getColumnCount(); column++) {
            if (cm.getColumn(column).getModelIndex() == modelColumnIndex) {
                return column;
            }
        }
        return -1;
    }

    public int convertColumnIndexToModel(int viewColumnIndex) {
        if (viewColumnIndex < 0) {
            return viewColumnIndex;
        }
        return getColumnModel().getColumn(viewColumnIndex).getModelIndex();
    }

    public int convertRowIndexToView(int modelRowIndex) {
        javax.swing.RowSorter<?> sorter = getRowSorter();
        if (sorter != null) {
            return sorter.convertRowIndexToView(modelRowIndex);
        }
        return modelRowIndex;
    }

    public int convertRowIndexToModel(int viewRowIndex) {
        javax.swing.RowSorter<?> sorter = getRowSorter();
        if (sorter != null) {
            return sorter.convertRowIndexToModel(viewRowIndex);
        }
        return viewRowIndex;
    }

    /**
     * View→model row conversion at the public-API boundary (D_jtable_cell_editing), guarding the
     * sentinel {@code -1} (no selection / no cell) since {@code TableRowSorter}'s
     * conversion throws on an out-of-range index. Identity when no sorter is active.
     */
    private int toModelRow(int viewRow) { return viewRow < 0 ? viewRow : convertRowIndexToModel(viewRow); }

    /** Model→view row conversion for values coming back out of the model-indexed surrogate (D_jtable_cell_editing); guards {@code -1}. */
    private int toViewRow(int modelRow) { return modelRow < 0 ? modelRow : convertRowIndexToView(modelRow); }

    // --- Renderer registry (ported TableCellRenderer) ---

    public TableCellRenderer getDefaultRenderer(Class<?> columnClass) {
        if (columnClass == null) return null;
        Class<?> c = columnClass;
        while (c != null) {
            TableCellRenderer r = defaultRenderersByColumnClass.get(c);
            if (r != null) return r;
            c = c.getSuperclass();
        }
        return defaultRenderersByColumnClass.get(Object.class);
    }

    public void setDefaultRenderer(Class<?> columnClass, TableCellRenderer renderer) {
        if (columnClass == null) return;
        if (renderer == null) {
            defaultRenderersByColumnClass.remove(columnClass);
        } else {
            defaultRenderersByColumnClass.put(columnClass, renderer);
        }
        // A default-renderer change can flip a column between the text and
        // component render paths, so re-install (in-place, churn-free) then refresh.
        withPeer(p -> {
            installEmulatorVaadinRenderers();
            var dp = surrogate().getDataProvider();
            if (dp != null) dp.refreshAll();
        });
    }

    /**
     * JDK contract: column-specific renderer when the
     * {@link TableColumn#getCellRenderer()} is non-null, else fallback
     * to the {@link #getDefaultRenderer(Class)} for the column's class.
     */
    public TableCellRenderer getCellRenderer(int row, int column) {
        TableColumn tc = columnModel.getColumn(column);
        TableCellRenderer r = tc.getCellRenderer();
        if (r == null) r = getDefaultRenderer(getColumnClass(column));
        return r;
    }

    /**
     * Prepares the renderer's component for a cell, seeded with the cell value
     * and selection state (JDK contract). Symmetric with {@link #prepareEditor}.
     *
     * <p>Returns an <b>emulator</b> component per R_no_vaadin_in_api — the renderer's own output,
     * unsnapshotted, so a migrated subclass override receives the same thing
     * {@code java.awt.Component}-typed JDK code expects and can post-tweak it.
     * The render path calls through here (D_r12_provenance) rather than reaching for the
     * renderer directly, so such an override actually runs.
     */
    public vaadinx.awt.Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Object value = getValueAt(row, column);
        boolean isSelected = isRowSelected(row);
        return renderer.getTableCellRendererComponent(this, value, isSelected, false, row, column);
    }

    // --- Header (ported JTableHeader holder) ---

    public JTableHeader getTableHeader() {
        if (tableHeader == null) {
            tableHeader = createDefaultTableHeader();
        }
        return tableHeader;
    }

    public void setTableHeader(JTableHeader tableHeader) {
        JTableHeader old = this.tableHeader;
        if (old == tableHeader) return;
        this.tableHeader = tableHeader;
        firePropertyChange("tableHeader", old, tableHeader);
    }

    protected JTableHeader createDefaultTableHeader() {
        JTableHeader h = new JTableHeader(columnModel);
        h.setTable(this);
        return h;
    }

    // --- Selection API ---

    // The selection model holds view rows, as the JDK's does, so these are the JDK's
    // bodies; the surrogate's model-row selection follows through valueChanged (D_jtable_selection).

    public void setRowSelectionInterval(int index0, int index1) {
        selectionModel.setSelectionInterval(boundRow(index0), boundRow(index1));
    }
    public void addRowSelectionInterval(int index0, int index1) {
        selectionModel.addSelectionInterval(boundRow(index0), boundRow(index1));
    }
    public void removeRowSelectionInterval(int index0, int index1) {
        selectionModel.removeSelectionInterval(boundRow(index0), boundRow(index1));
    }
    public void clearSelection() {
        selectionModel.clearSelection();
        columnModel.getSelectionModel().clearSelection();
    }
    public int getSelectedRow() { return selectionModel.getMinSelectionIndex(); }
    public int[] getSelectedRows() { return selectionModel.getSelectedIndices(); }
    public int getSelectedRowCount() { return selectionModel.getSelectedItemsCount(); }
    public boolean isRowSelected(int row) { return selectionModel.isSelectedIndex(row); }

    public void selectAll() {
        // If I'm currently editing, then I should stop editing
        if (isEditing()) {
            removeEditor();
        }
        // Rows only: column selection is row-only Grid territory (R_vaadin_first).
        int rowCount = getRowCount();
        if (rowCount > 0 && getRowSelectionAllowed()) {
            ListSelectionModel selModel = selectionModel;
            selModel.setValueIsAdjusting(true);
            int oldLead = getAdjustedIndex(selModel.getLeadSelectionIndex());
            int oldAnchor = getAdjustedIndex(selModel.getAnchorSelectionIndex());
            setRowSelectionInterval(0, rowCount - 1);
            // this is done to restore the anchor and lead
            setLeadAnchorWithoutSelection(selModel, oldLead, oldAnchor);
            selModel.setValueIsAdjusting(false);
        }
    }

    private int boundRow(int row) {
        if (row < 0 || row >= getRowCount()) {
            throw new IllegalArgumentException("Row index out of range");
        }
        return row;
    }

    private int getAdjustedIndex(int index) {
        return index < getRowCount() ? index : -1;
    }

    /**
     * Moves {@code model}'s lead to {@code lead} and its anchor to {@code anchor} without
     * changing which rows are selected; {@code lead == -1} parks both, {@code anchor == -1}
     * takes the lead's.
     */
    private static void setLeadAnchorWithoutSelection(ListSelectionModel model, int lead, int anchor) {
        if (anchor == -1) {
            anchor = lead;
        }
        if (lead == -1) {
            model.setAnchorSelectionIndex(-1);
            model.setLeadSelectionIndex(-1);
        } else {
            // Re-applying the lead row's own state is what moves the lead there.
            if (model.isSelectedIndex(lead)) {
                model.addSelectionInterval(lead, lead);
            } else {
                model.removeSelectionInterval(lead, lead);
            }
            model.setAnchorSelectionIndex(anchor);
        }
    }

    /** Clears both selections and parks their lead and anchor at {@code -1}, as a whole-model change does. */
    private void clearSelectionAndLeadAnchor() {
        ListSelectionModel columnSelection = columnModel.getSelectionModel();
        selectionModel.setValueIsAdjusting(true);
        columnSelection.setValueIsAdjusting(true);
        clearSelection();
        selectionModel.setAnchorSelectionIndex(-1);
        selectionModel.setLeadSelectionIndex(-1);
        columnSelection.setAnchorSelectionIndex(-1);
        columnSelection.setLeadSelectionIndex(-1);
        selectionModel.setValueIsAdjusting(false);
        columnSelection.setValueIsAdjusting(false);
    }

    public void setSelectionMode(int selectionMode) {
        // No PropertyChangeEvent: JTable.setSelectionMode clears the selection
        // and delegates to both selection models, whose ListSelectionEvents
        // are the notification (D_property_fanout_audit).
        clearSelection();
        getSelectionModel().setSelectionMode(selectionMode);
        getColumnModel().getSelectionModel().setSelectionMode(selectionMode);
        // The Grid's own mode (single vs multi) follows through the surrogate.
        onPeerSelectionMuted(() -> surrogate().setSelectionMode(selectionMode));
    }

    // --- The view-row ↔ model-row selection bridge (D_jtable_selection) ---

    /** Pulls a browser selection off the surrogate's model-row selection model. */
    private final ListSelectionListener peerSelectionBridge = e -> {
        // Our own pushes and the surrogate's in-change bookkeeping are echoes, not browser
        // selections — dropped here, before the envelope (R_swing_is_truth).
        if (this.peerSelectionMuted || this.changingModel || e.getValueIsAdjusting()) return;
        vaadinx.EHelper.callSwing(this::pullSelectionFromPeer);
    };

    /** Set while this table writes the surrogate's selection, so {@link #peerSelectionBridge} ignores the echo. */
    private boolean peerSelectionMuted;

    /** Set while this table writes its own selection from the surrogate's, so {@link #valueChanged} does not push it straight back. */
    private boolean pullingPeerSelection;

    /**
     * Also the hop: every write through here is a peer write (D_attach_aware_hop). The mute is
     * set inside the write, so a write queued until attach is still muted when it drains.
     */
    private void onPeerSelectionMuted(Runnable body) {
        withPeer(p -> {
            boolean was = peerSelectionMuted;
            peerSelectionMuted = true;
            try {
                body.run();
            } finally {
                peerSelectionMuted = was;
            }
        });
    }

    /**
     * Mirrors the view selection onto the surrogate's model-row selection, which drives the
     * Grid. Skipped mid model change ({@link #tableChanged} pushes once at its end) and when
     * the model rows are already the ones selected — a re-sort moves view rows, not model
     * rows, and a no-op push would still churn the Grid's selection.
     */
    private void pushSelectionToPeer() {
        if (changingModel || pullingPeerSelection) return;
        int viewRowCount = getRowCount();
        int[] modelRows = java.util.Arrays.stream(selectionModel.getSelectedIndices())
                .filter(v -> v < viewRowCount)
                .map(this::convertRowIndexToModel)
                .sorted()
                .toArray();
        onPeerSelectionMuted(() -> {
            // Compared inside the write: a queued one meets the surrogate's selection as it is
            // when it drains, not as it was when it was made.
            ListSelectionModel peerSm = surrogate().getListSelectionModel();
            if (java.util.Arrays.equals(modelRows, peerSm.getSelectedIndices())) return;
            peerSm.setValueIsAdjusting(true);
            peerSm.clearSelection();
            for (int m : modelRows) {
                peerSm.addSelectionInterval(m, m);
            }
            peerSm.setValueIsAdjusting(false);
        });
    }

    /** Writes a browser selection, which arrives as model rows, into the view selection. */
    private void pullSelectionFromPeer() {
        int modelRowCount = getModel().getRowCount();
        int[] viewRows = java.util.Arrays.stream(surrogate().getListSelectionModel().getSelectedIndices())
                .filter(m -> m < modelRowCount)
                .map(this::convertRowIndexToView)
                .filter(v -> v >= 0)
                .sorted()
                .toArray();
        if (java.util.Arrays.equals(viewRows, selectionModel.getSelectedIndices())) return;
        pullingPeerSelection = true;
        try {
            if (viewRows.length == 1) {
                // A plain click: one event, lead and anchor on the row, as the JDK's click handler leaves them.
                selectionModel.setSelectionInterval(viewRows[0], viewRows[0]);
            } else {
                selectionModel.setValueIsAdjusting(true);
                selectionModel.clearSelection();
                for (int v : viewRows) {
                    selectionModel.addSelectionInterval(v, v);
                }
                selectionModel.setValueIsAdjusting(false);
            }
        } finally {
            pullingPeerSelection = false;
        }
    }

    public boolean getRowSelectionAllowed() { return rowSelectionAllowed; }
    public void setRowSelectionAllowed(boolean v) {
        boolean old = rowSelectionAllowed;
        if (old == v) return;
        rowSelectionAllowed = v;
        firePropertyChange("rowSelectionAllowed", old, v);
    }
    public boolean getColumnSelectionAllowed() { return columnSelectionAllowed; }
    public void setColumnSelectionAllowed(boolean v) {
        boolean old = columnSelectionAllowed;
        if (old == v) return;
        columnSelectionAllowed = v;
        firePropertyChange("columnSelectionAllowed", old, v);
    }
    public boolean getCellSelectionEnabled() { return rowSelectionAllowed && columnSelectionAllowed; }
    /**
     * Vaadin Grid has no per-cell selection — WARN, but round-trip the two
     * backing flags per JDK so getCellSelectionEnabled stays honest, and fire
     * {@code "cellSelectionEnabled"} (D_owed_events).
     *
     * <p>The JDK keeps a {@code cellSelectionEnabled} field that
     * {@code getCellSelectionEnabled()} never reads — that getter derives from
     * the two allowed-flags — so its only purpose is to supply this event's old
     * value, and it can drift from the derived answer. Reproduced as the JDK
     * has it rather than as it reads.
     */
    public void setCellSelectionEnabled(boolean v) {
        vaadinx.EHelper.onUnimplemented("JTable", "setCellSelectionEnabled", v);
        setRowSelectionAllowed(v);
        setColumnSelectionAllowed(v);
        boolean old = this.cellSelectionEnabled;
        this.cellSelectionEnabled = v;
        firePropertyChange("cellSelectionEnabled", old, v);
    }

    // Supplies setCellSelectionEnabled's old value and nothing else — the
    // getter derives from rowSelectionAllowed && columnSelectionAllowed, as the
    // JDK's does. Not dead: the JDK's own field is used exactly this way.
    protected boolean cellSelectionEnabled = false;

    // --- Column manipulation ---

    public void addColumn(TableColumn aColumn) {
        if (aColumn.getHeaderValue() == null) {
            int modelIdx = aColumn.getModelIndex();
            aColumn.setHeaderValue(getModel().getColumnName(modelIdx));
        }
        columnModel.addColumn(aColumn);
    }

    public void removeColumn(TableColumn aColumn) {
        columnModel.removeColumn(aColumn);
    }

    public void moveColumn(int columnIndex, int newIndex) {
        columnModel.moveColumn(columnIndex, newIndex);
    }

    public TableColumn getColumn(Object identifier) {
        return columnModel.getColumn(columnModel.getColumnIndex(identifier));
    }

    // --- AutoCreate flags ---

    public boolean getAutoCreateColumnsFromModel() { return autoCreateColumnsFromModel; }
    public void setAutoCreateColumnsFromModel(boolean autoCreateColumnsFromModel) {
        if (this.autoCreateColumnsFromModel != autoCreateColumnsFromModel) {
            boolean old = this.autoCreateColumnsFromModel;
            this.autoCreateColumnsFromModel = autoCreateColumnsFromModel;
            if (autoCreateColumnsFromModel) {
                createDefaultColumnsFromModel();
            }
            firePropertyChange("autoCreateColumnsFromModel", old, autoCreateColumnsFromModel);
        }
    }

    /**
     * JDK {@code autoCreateRowSorter}. Held here rather than on the surrogate, so the sorter it
     * creates is installed through {@link #setRowSorter} as the JDK's is — which is what puts
     * it under this table's {@link SortManager} and a subclass's {@code setRowSorter} override
     * on the path.
     */
    private boolean autoCreateRowSorter;

    public boolean getAutoCreateRowSorter() { return autoCreateRowSorter; }
    public void setAutoCreateRowSorter(boolean autoCreateRowSorter) {
        boolean oldValue = this.autoCreateRowSorter;
        this.autoCreateRowSorter = autoCreateRowSorter;
        if (autoCreateRowSorter) {
            setRowSorter(new javax.swing.table.TableRowSorter<TableModel>(getModel()));
        }
        firePropertyChange("autoCreateRowSorter", oldValue, autoCreateRowSorter);
    }

    public javax.swing.RowSorter<? extends TableModel> getRowSorter() {
        return (sortManager != null) ? sortManager.sorter : null;
    }

    /**
     * Installs {@code sorter} and clears the selection. The table listens to it through a
     * {@link SortManager}, as the JDK's does, so {@link #sorterChanged} runs for every sorter
     * however it was installed (R_no_vaadin_in_api limb 2); the surrogate drives the Grid off
     * the same instance (SD_sjtable_sorting).
     */
    public void setRowSorter(javax.swing.RowSorter<? extends TableModel> sorter) {
        javax.swing.RowSorter<? extends TableModel> oldRowSorter = null;
        if (sortManager != null) {
            oldRowSorter = sortManager.sorter;
            sortManager.dispose();
            sortManager = null;
        }
        clearSelectionAndLeadAnchor();
        onPeerSelectionMuted(() -> surrogate().setRowSorter(sorter));
        if (sorter != null) {
            sortManager = new SortManager(sorter);
        }
        // Two names, both the JDK's: "rowSorter" is the documented one and
        // "sorter" the legacy alias it also fires (D_owed_events).
        firePropertyChange("rowSorter", oldRowSorter, sorter);
        firePropertyChange("sorter", oldRowSorter, sorter);
    }

    /**
     * The current sorter re-sorted, or re-filtered. Re-maps the selection and the editing row
     * through the sort, as the JDK does (D_jtable_selection); the Grid follows on its own,
     * since the surrogate listens to the same sorter.
     *
     * <p>A browser header click reaches here from inside the Grid's data fetch, which
     * runs while the response is written and outside {@code EHelper.callSwing} — so a
     * {@code ListSelectionListener} fired by that re-map cannot open a modal dialog.
     */
    public void sorterChanged(javax.swing.event.RowSorterEvent e) {
        if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED) {
            JTableHeader header = getTableHeader();
            if (header != null) {
                header.repaint();
            }
        } else if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORTED) {
            sorterChanged = true;
            if (!ignoreSortChange) {
                sortedTableChanged(e, null);
            }
        }
    }

    // --- Sort bookkeeping: the JDK's SortManager, less the variable row heights R_layouts_close_enough drops ---

    private SortManager sortManager;

    /** True while the sorter is being told of a model change, so {@link #sorterChanged} leaves the re-map to {@link #sortedTableChanged}. */
    private boolean ignoreSortChange;

    /** Whether the sorter re-sorted during the change in flight — the JDK's flag, set by {@link #sorterChanged}. */
    private boolean sorterChanged;

    /**
     * Keeps the view selection meaningful across a re-sort or a model change: caches it in
     * model rows before the sorter hears the change and re-applies it in view rows after.
     */
    private final class SortManager {
        final javax.swing.RowSorter<? extends TableModel> sorter;

        // Selection, in terms of the model. This is lazily created
        // as needed.
        private ListSelectionModel modelSelection;
        private int modelLeadIndex;
        // Set to true while in the process of changing the selection.
        // If this is true the selection change is ignored.
        private boolean syncingSelection;
        // Temporary cache of selection, in terms of model. This is only used
        // if we don't need the full weight of modelSelection.
        private int[] lastModelSelection;

        SortManager(javax.swing.RowSorter<? extends TableModel> sorter) {
            this.sorter = sorter;
            sorter.addRowSorterListener(JTable.this);
        }

        void dispose() {
            sorter.removeRowSorterListener(JTable.this);
        }

        /** Invoked when the underlying model has completely changed. */
        void allChanged() {
            modelLeadIndex = -1;
            modelSelection = null;
        }

        /** Invoked when the selection, on the view, has changed. */
        void viewSelectionChanged(ListSelectionEvent e) {
            if (!syncingSelection && modelSelection != null) {
                modelSelection = null;
            }
        }

        /**
         * Invoked when either the table model has changed, or the RowSorter
         * has changed. This is invoked prior to notifying the sorter of the
         * change.
         */
        void prepareForChange(javax.swing.event.RowSorterEvent sortEvent, ModelChange change) {
            if (getUpdateSelectionOnSort()) {
                cacheSelection(sortEvent, change);
            }
        }

        private void cacheSelection(javax.swing.event.RowSorterEvent sortEvent, ModelChange change) {
            if (sortEvent != null) {
                // sort order changed. If modelSelection is null and filtering
                // is enabled we need to cache the selection in terms of the
                // underlying model, this will allow us to correctly restore
                // the selection even if rows are filtered out.
                if (modelSelection == null && sorter.getViewRowCount() != getModel().getRowCount()) {
                    modelSelection = new javax.swing.DefaultListSelectionModel();
                    ListSelectionModel viewSelection = getSelectionModel();
                    int min = viewSelection.getMinSelectionIndex();
                    int max = viewSelection.getMaxSelectionIndex();
                    int modelIndex;
                    for (int viewIndex = min; viewIndex <= max; viewIndex++) {
                        if (viewSelection.isSelectedIndex(viewIndex)) {
                            modelIndex = convertRowIndexToModel(sortEvent, viewIndex);
                            if (modelIndex != -1) {
                                modelSelection.addSelectionInterval(modelIndex, modelIndex);
                            }
                        }
                    }
                    modelIndex = convertRowIndexToModel(sortEvent, viewSelection.getLeadSelectionIndex());
                    setLeadAnchorWithoutSelection(modelSelection, modelIndex, modelIndex);
                } else if (modelSelection == null) {
                    // Sorting changed, haven't cached selection in terms
                    // of model and no filtering. Temporarily cache selection.
                    cacheModelSelection(sortEvent);
                }
            } else if (change.allRowsChanged) {
                // All the rows have changed, chuck any cached selection.
                modelSelection = null;
            } else if (modelSelection != null) {
                // Table changed, reflect changes in cached selection model.
                switch (change.type) {
                    case TableModelEvent.DELETE:
                        modelSelection.removeIndexInterval(change.startModelIndex, change.endModelIndex);
                        break;
                    case TableModelEvent.INSERT:
                        modelSelection.insertIndexInterval(change.startModelIndex, change.length, true);
                        break;
                    default:
                        break;
                }
            } else {
                // table changed, but haven't cached rows, temporarily
                // cache them.
                cacheModelSelection(null);
            }
        }

        private void cacheModelSelection(javax.swing.event.RowSorterEvent sortEvent) {
            lastModelSelection = convertSelectionToModel(sortEvent);
            modelLeadIndex = convertRowIndexToModel(sortEvent, selectionModel.getLeadSelectionIndex());
        }

        /**
         * Invoked when either the table has changed or the sorter has changed
         * and after the sorter has been notified. If necessary this will
         * reapply the selection.
         */
        void processChange(boolean sorterChanged, ModelChange change) {
            if (sorterChanged) {
                restoreSelection(change);
            }
        }

        /** Restores the selection from that in terms of the model. */
        private void restoreSelection(ModelChange change) {
            syncingSelection = true;
            if (lastModelSelection != null) {
                restoreSortingSelection(lastModelSelection, modelLeadIndex, change);
                lastModelSelection = null;
            } else if (modelSelection != null) {
                ListSelectionModel viewSelection = getSelectionModel();
                viewSelection.setValueIsAdjusting(true);
                viewSelection.clearSelection();
                int min = modelSelection.getMinSelectionIndex();
                int max = modelSelection.getMaxSelectionIndex();
                int viewIndex;
                for (int modelIndex = min; modelIndex <= max; modelIndex++) {
                    if (modelSelection.isSelectedIndex(modelIndex)) {
                        viewIndex = convertRowIndexToView(modelIndex);
                        if (viewIndex != -1) {
                            viewSelection.addSelectionInterval(viewIndex, viewIndex);
                        }
                    }
                }
                // Restore the lead
                int viewLeadIndex = modelSelection.getLeadSelectionIndex();
                if (viewLeadIndex != -1 && !modelSelection.isSelectionEmpty()) {
                    viewLeadIndex = convertRowIndexToView(viewLeadIndex);
                }
                setLeadAnchorWithoutSelection(viewSelection, viewLeadIndex, viewLeadIndex);
                viewSelection.setValueIsAdjusting(false);
            }
            syncingSelection = false;
        }
    }

    /**
     * A {@code TableModelEvent} in model coordinates, precomputed, as the sort bookkeeping reads
     * it: start and end model row, type, length, and whether every row changed.
     */
    private final class ModelChange {
        final int startModelIndex;
        final int endModelIndex;
        final int type;
        final int modelRowCount;
        final int length;
        final boolean allRowsChanged;
        final TableModelEvent event;

        ModelChange(TableModelEvent e) {
            event = e;
            startModelIndex = Math.max(0, e.getFirstRow());
            modelRowCount = getModel().getRowCount();
            endModelIndex = e.getLastRow() < 0 ? Math.max(0, modelRowCount - 1) : e.getLastRow();
            length = endModelIndex - startModelIndex + 1;
            type = e.getType();
            allRowsChanged = (e.getLastRow() == Integer.MAX_VALUE);
        }
    }

    /** JDK {@code updateSelectionOnSort}: whether a re-sort keeps the selection on the same model rows. Default {@code true}. */
    private boolean updateSelectionOnSort = true;

    public boolean getUpdateSelectionOnSort() { return updateSelectionOnSort; }

    public void setUpdateSelectionOnSort(boolean update) {
        if (updateSelectionOnSort != update) {
            updateSelectionOnSort = update;
            firePropertyChange("updateSelectionOnSort", !update, update);
        }
    }

    /**
     * Invoked when {@link #sorterChanged} is invoked, or when {@link #tableChanged} is invoked
     * and sorting is enabled. {@code sortedEvent} is {@code null} for a model change, {@code e}
     * for a re-sort.
     */
    private void sortedTableChanged(javax.swing.event.RowSorterEvent sortedEvent, TableModelEvent e) {
        int editingModelIndex = -1;
        ModelChange change = (e != null) ? new ModelChange(e) : null;

        if ((change == null || !change.allRowsChanged) && this.editingRow != -1) {
            editingModelIndex = convertRowIndexToModel(sortedEvent, this.editingRow);
        }

        sortManager.prepareForChange(sortedEvent, change);

        if (e != null) {
            notifySorter(change);
            peerTableChanged(e);
            if (change.type != TableModelEvent.UPDATE) {
                // If the Sorter is unsorted we will not have received
                // notification, force treating insert/delete as a change.
                sorterChanged = true;
            }
        } else {
            sorterChanged = true;
        }

        sortManager.processChange(sorterChanged, change);

        if (sorterChanged) {
            // Update the editing row
            if (this.editingRow != -1) {
                int newIndex = (editingModelIndex == -1) ? -1 : convertRowIndexToView(editingModelIndex, change);
                restoreSortingEditingRow(newIndex);
            }
        }

        // Check if lead/anchor need to be reset.
        if (change != null && change.allRowsChanged) {
            clearSelectionAndLeadAnchor();
        }
    }

    /**
     * Tells the sorter which model rows a change touched, as the JDK's table does, on the
     * thread the model fired on. {@code SORTED} events the sorter fires meanwhile only mark
     * {@link #sorterChanged}; the re-map is this change's to do. The surrogate, which drives
     * the Grid off the same sorter, hears the change afterwards and does not tell it again
     * (SD_sjtable_sorting).
     */
    private void notifySorter(ModelChange change) {
        try {
            ignoreSortChange = true;
            sorterChanged = false;
            switch(change.type) {
            case TableModelEvent.UPDATE:
                if (change.event.getLastRow() == Integer.MAX_VALUE) {
                    sortManager.sorter.allRowsChanged();
                } else if (change.event.getColumn() ==
                           TableModelEvent.ALL_COLUMNS) {
                    sortManager.sorter.rowsUpdated(change.startModelIndex,
                                       change.endModelIndex);
                } else {
                    sortManager.sorter.rowsUpdated(change.startModelIndex,
                                       change.endModelIndex,
                                       change.event.getColumn());
                }
                break;
            case TableModelEvent.INSERT:
                sortManager.sorter.rowsInserted(change.startModelIndex,
                                    change.endModelIndex);
                break;
            case TableModelEvent.DELETE:
                sortManager.sorter.rowsDeleted(change.startModelIndex,
                                   change.endModelIndex);
                break;
            }
        } finally {
            ignoreSortChange = false;
        }
    }

    /** Restores the selection after a model event/sort order changes. All coordinates are in terms of the model. */
    private void restoreSortingSelection(int[] selection, int lead, ModelChange change) {
        // Convert the selection from model to view
        for (int i = selection.length - 1; i >= 0; i--) {
            selection[i] = convertRowIndexToView(selection[i], change);
        }
        lead = convertRowIndexToView(lead, change);

        // Check for the common case of no change in selection for 1 row
        if (selection.length == 0 || (selection.length == 1 && selection[0] == getSelectedRow())) {
            return;
        }

        // And apply the new selection
        selectionModel.setValueIsAdjusting(true);
        selectionModel.clearSelection();
        for (int i = selection.length - 1; i >= 0; i--) {
            if (selection[i] != -1) {
                selectionModel.addSelectionInterval(selection[i], selection[i]);
            }
        }
        setLeadAnchorWithoutSelection(selectionModel, lead, lead);
        selectionModel.setValueIsAdjusting(false);
    }

    /**
     * Restores the editing row after a model event/sort order change: cancels the edit when its
     * row is gone, otherwise moves {@link #editingRow}. The Grid editor follows through
     * {@link #scheduleGridEditorReopen}, since the data change that moved the row closed it.
     */
    private void restoreSortingEditingRow(int editingRow) {
        if (editingRow == -1) {
            // Editing row no longer being shown, cancel editing
            TableCellEditor editor = getCellEditor();
            if (editor != null) {
                // First try and cancel
                editor.cancelCellEditing();
                if (getCellEditor() != null) {
                    // CellEditor didn't cede control, forcefully
                    // remove it
                    removeEditor();
                }
            }
        } else {
            this.editingRow = editingRow;
        }
    }

    /**
     * Brings the Grid editor back after a data change closed it with a Swing edit still open,
     * on the model row {@link #editingRow} names by then — decided just before the response,
     * once the change (and any re-map of the editing row) has settled. One re-open per
     * response however many refreshes closed it.
     */
    private void scheduleGridEditorReopen() {
        if (cellEditor == null || gridEditorReopenScheduled) return;
        withPeer(p -> surrogate().getUI().ifPresent(ui -> {
            gridEditorReopenScheduled = true;
            ui.beforeClientResponse(surrogate(), context -> {
                gridEditorReopenScheduled = false;
                if (cellEditor == null || editingRow < 0 || editingRow >= getRowCount()
                        || surrogate().getEditor().isOpen()) return;
                installEditorComponents(editingRow, editingColumn, editingComponent);
                Integer item = itemForRow(editingRow);
                surrogate().setRefreshProtectedRow(item);
                surrogate().getEditor().editItem(item);
            });
        }));
    }

    /** Set while a {@link #scheduleGridEditorReopen} is pending for this response. */
    private boolean gridEditorReopenScheduled;

    /**
     * Converts a model index to view index. This is called when the sorter or model changes and
     * sorting is enabled.
     *
     * @param change the model change in flight, or {@code null} for a re-sort
     */
    private int convertRowIndexToView(int modelIndex, ModelChange change) {
        if (modelIndex < 0) {
            return -1;
        }
        if (change != null && modelIndex >= change.startModelIndex) {
            if (change.type == TableModelEvent.INSERT) {
                if (modelIndex + change.length >= change.modelRowCount) {
                    return -1;
                }
                return sortManager.sorter.convertRowIndexToView(modelIndex + change.length);
            } else if (change.type == TableModelEvent.DELETE) {
                if (modelIndex <= change.endModelIndex) {
                    // deleted
                    return -1;
                } else {
                    if (modelIndex - change.length >= change.modelRowCount) {
                        return -1;
                    }
                    return sortManager.sorter.convertRowIndexToView(modelIndex - change.length);
                }
            }
            // else, updated
        }
        if (modelIndex >= getModel().getRowCount()) {
            return -1;
        }
        return sortManager.sorter.convertRowIndexToView(modelIndex);
    }

    /** Converts the selection to model coordinates. This is used when the model changes or the sorter changes. */
    private int[] convertSelectionToModel(javax.swing.event.RowSorterEvent e) {
        int[] selection = getSelectedRows();
        for (int i = selection.length - 1; i >= 0; i--) {
            selection[i] = convertRowIndexToModel(e, selection[i]);
        }
        return selection;
    }

    private int convertRowIndexToModel(javax.swing.event.RowSorterEvent e, int viewIndex) {
        if (e != null) {
            if (e.getPreviousRowCount() == 0) {
                return viewIndex;
            }
            // range checking handled by RowSorterEvent
            return e.convertPreviousRowIndexToModel(viewIndex);
        }
        // Make sure the viewIndex is valid
        if (viewIndex < 0 || viewIndex >= getRowCount()) {
            return -1;
        }
        return convertRowIndexToModel(viewIndex);
    }

    // --- Row height / margin / spacing / colour (emulator R_swing_is_truth field shadows) ---

    public int getRowHeight() { return rowHeight; }
    public void setRowHeight(int rowHeight) {
        int old = this.rowHeight;
        if (old == rowHeight) return;
        this.rowHeight = rowHeight;
        firePropertyChange("rowHeight", old, rowHeight);
    }
    public int getRowHeight(int row) { return rowHeight; }
    public void setRowHeight(int row, int rowHeight) { withPeer(p -> surrogate().setRowHeight(row, rowHeight)); }

    public int getRowMargin() { return rowMargin; }
    public void setRowMargin(int rowMargin) {
        int old = this.rowMargin;
        if (old == rowMargin) return;
        this.rowMargin = rowMargin;
        firePropertyChange("rowMargin", old, rowMargin);
    }

    public Dimension getIntercellSpacing() { return new Dimension(getColumnModel().getColumnMargin(), rowMargin); }
    /**
     * Splits the pair the way the JDK does — height to {@link #setRowMargin},
     * width to the column model's {@code columnMargin} — so the notification
     * is the {@code "rowMargin"} PCE plus the column model's
     * {@code COLUMN_MARGIN_CHANGED} ChangeEvent. {@code "intercellSpacing"} is
     * not a bound property (D_property_fanout_audit), and there is no field behind it: the getter
     * recomposes the Dimension from the two margins, as the JDK's does.
     */
    public void setIntercellSpacing(Dimension intercellSpacing) {
        setRowMargin(intercellSpacing.height);
        getColumnModel().setColumnMargin(intercellSpacing.width);
    }

    public Color getGridColor() { return gridColor; }
    public void setGridColor(Color gridColor) {
        Color old = this.gridColor;
        if (java.util.Objects.equals(old, gridColor)) return;
        this.gridColor = gridColor;
        firePropertyChange("gridColor", old, gridColor);
    }

    public boolean getShowHorizontalLines() { return showHorizontalLines; }
    public void setShowHorizontalLines(boolean v) {
        boolean old = showHorizontalLines;
        if (old == v) return;
        showHorizontalLines = v;
        firePropertyChange("showHorizontalLines", old, v);
    }

    public boolean getShowVerticalLines() { return showVerticalLines; }
    public void setShowVerticalLines(boolean v) {
        boolean old = showVerticalLines;
        if (old == v) return;
        showVerticalLines = v;
        firePropertyChange("showVerticalLines", old, v);
    }

    public boolean getShowGrid() { return showHorizontalLines && showVerticalLines; }
    public void setShowGrid(boolean v) {
        setShowHorizontalLines(v);
        setShowVerticalLines(v);
    }

    public int getAutoResizeMode() { return autoResizeMode; }
    public void setAutoResizeMode(int mode) {
        switch (mode) {
            case AUTO_RESIZE_OFF, AUTO_RESIZE_NEXT_COLUMN, AUTO_RESIZE_SUBSEQUENT_COLUMNS,
                 AUTO_RESIZE_LAST_COLUMN, AUTO_RESIZE_ALL_COLUMNS -> { /* ok */ }
            default -> throw new IllegalArgumentException("Unrecognized auto-resize mode: " + mode);
        }
        int old = autoResizeMode;
        if (old == mode) return;
        autoResizeMode = mode;
        firePropertyChange("autoResizeMode", old, mode);
    }

    public Color getSelectionForeground() { return selectionForeground; }
    public void setSelectionForeground(Color c) {
        Color old = selectionForeground;
        if (java.util.Objects.equals(old, c)) return;
        selectionForeground = c;
        firePropertyChange("selectionForeground", old, c);
    }

    public Color getSelectionBackground() { return selectionBackground; }
    public void setSelectionBackground(Color c) {
        Color old = selectionBackground;
        if (java.util.Objects.equals(old, c)) return;
        selectionBackground = c;
        firePropertyChange("selectionBackground", old, c);
    }

    /**
     * The JDK's geometry over this table's nominal state: {@link #getRowHeight()} and the
     * column model's widths and margins. It is not where the browser drew the cell
     * (R_layouts_close_enough), and a row or column out of range reads {@code getWidth()} /
     * {@code getHeight()}, which are unimplemented and answer 0.
     */
    public Rectangle getCellRect(int row, int column, boolean includeSpacing) {
        Rectangle r = new Rectangle();
        boolean valid = true;
        if (row < 0) {
            // y = height = 0;
            valid = false;
        }
        else if (row >= getRowCount()) {
            r.y = getHeight();
            valid = false;
        }
        else {
            // The JDK's rowModel (variable row heights) is not carried, so every row is uniform.
            r.height = getRowHeight(row);
            r.y = row * r.height;
        }

        if (column < 0) {
            if( !getComponentOrientation().isLeftToRight() ) {
                r.x = getWidth();
            }
            // otherwise, x = width = 0;
            valid = false;
        }
        else if (column >= getColumnCount()) {
            if( getComponentOrientation().isLeftToRight() ) {
                r.x = getWidth();
            }
            // otherwise, x = width = 0;
            valid = false;
        }
        else {
            TableColumnModel cm = getColumnModel();
            for (int i = 0; i < column; i++) {
                r.x += cm.getColumn(i).getWidth();
            }
            // Table columns are laid out from right to left when component
            // orientation is set to ComponentOrientation.RIGHT_TO_LEFT,
            // adjust the x coordinate for this case.
            final int columnWidth = cm.getColumn(column).getWidth();
            if (!getComponentOrientation().isLeftToRight()) {
                r.x = getWidth() - r.x - columnWidth;
            }
            r.width = columnWidth;
        }

        if (valid && !includeSpacing) {
            // Bound the margins by their associated dimensions to prevent
            // returning bounds with negative dimensions.
            int rm = Math.min(getRowMargin(), r.height);
            int cm = Math.min(getColumnModel().getColumnMargin(), r.width);
            // This is not the same as grow(), it rounds differently.
            r.setBounds(r.x + cm/2, r.y + rm/2, r.width - cm, r.height - rm);
        }
        return r;
    }

    // --- Drag / DnD (emulator R_swing_is_truth field shadow, WARN on true) ---

    public boolean getDragEnabled() { return dragEnabled; }
    public void setDragEnabled(boolean v) {
        // D_drag_and_drop: graduated from drop-and-WARN. dragEnabled is the JDK-faithful
        // round-trip flag (R_swing_is_truth); flipping it (re)wires the Grid-row drag source
        // through DndBridge, which reads this flag back.
        // No PropertyChangeEvent: JTable.setDragEnabled checks and assigns;
        // dragEnabled is not a bound property (D_property_fanout_audit).
        if (dragEnabled == v) return;
        dragEnabled = v;
        vaadinx.swing.DndBridge.reconfigure(this);
    }

    /** Drop mode (D_drag_and_drop). Default {@code USE_SELECTION} matches JDK JTable. */
    private javax.swing.DropMode dropMode = javax.swing.DropMode.USE_SELECTION;

    public final javax.swing.DropMode getDropMode() { return dropMode; }

    public final void setDropMode(javax.swing.DropMode dropMode) {
        // JTable accepts every DropMode value (unlike JList), so no R_match_swing_errors reject.
        this.dropMode = dropMode;
        vaadinx.swing.DndBridge.reconfigure(this);
    }

    /**
     * Drop location for a JTable drop (D_drag_and_drop — emulator for
     * {@code javax.swing.JTable.DropLocation}). Carries the target row/column
     * and whether the drop inserts between rows/columns. The column is always 0
     * + non-insert (R_layouts_close_enough — Grid row DnD has no per-column drop position); the
     * pixel drop point is a dummy.
     */
    public static final class DropLocation extends vaadinx.swing.TransferHandler.DropLocation {
        private final int row;
        private final int col;
        private final boolean isInsertRow;
        private final boolean isInsertColumn;

        DropLocation(java.awt.Point p, int row, int col, boolean isInsertRow, boolean isInsertColumn) {
            super(p);
            this.row = row;
            this.col = col;
            this.isInsertRow = isInsertRow;
            this.isInsertColumn = isInsertColumn;
        }

        public int getRow() { return row; }
        public int getColumn() { return col; }
        public boolean isInsertRow() { return isInsertRow; }
        public boolean isInsertColumn() { return isInsertColumn; }
    }

    public boolean getFillsViewportHeight() { return fillsViewportHeight; }
    public void setFillsViewportHeight(boolean v) {
        boolean old = fillsViewportHeight;
        if (old == v) return;
        fillsViewportHeight = v;
        firePropertyChange("fillsViewportHeight", old, v);
    }

    public Dimension getPreferredScrollableViewportSize() { return preferredViewportSize; }
    public void setPreferredScrollableViewportSize(Dimension size) {
        // No PropertyChangeEvent: the JDK setter is a bare field assignment
        // (D_property_fanout_audit). No equality guard either, for the same reason.
        preferredViewportSize = size;
    }

    // --- Cell editor surface. Resolution + coercion wired (D_jtable_cell_editing, Slice 1);
    //     interactive open (editCellAt / isEditing / getEditing* / editor
    //     component) lands with the Grid.Editor bridge (D_jtable_cell_editing, Slice 2). ---

    public void editCellAt(int row, int column) {
        editCellAt(row, column, null);
    }

    /**
     * Starts single-column editing (D_jtable_cell_editing): resolves the cell's editor, seeds its
     * component, sets it as the editor component on <em>only</em> the clicked
     * column, and opens the inherited Grid.Editor. Value flows through the Swing
     * editor lifecycle — commit ({@code editingStopped}) writes back via
     * {@link #setValueAt}; cancel ({@code editingCanceled}) discards.
     *
     * <p>{@code row} is a <em>view</em> row (JDK contract). All model access here
     * goes through the view-indexed public accessors ({@link #getValueAt} /
     * {@link #isCellEditable} / {@link #setValueAt}), and {@code editingRow} is
     * stored as the view row; only {@code itemForRow} and the editor-row snapshot
     * loop convert to the model row the Grid item carries (D_jtable_cell_editing).
     */
    public boolean editCellAt(int row, int column, java.util.EventObject e) {
        if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) return false;
        if (!isCellEditable(row, column)) return false;
        TableCellEditor ed = getCellEditor(row, column);
        if (ed == null || !ed.isCellEditable(e)) return false;

        // Commit any in-flight edit before starting a new one — Swing terminates
        // the current editor by committing it, not discarding (B6). stopCellEditing
        // fires editingStopped → commitActiveEdit → cleanupEdit (nulls cellEditor);
        // the following cleanupEdit is then a no-op, or discards if the commit was
        // refused (bad input).
        if (cellEditor != null) cellEditor.stopCellEditing();
        cleanupEdit();

        // Through prepareEditor, not straight to the editor, so a subclass override
        // of that JDK hook is on the edit path (R_no_vaadin_in_api second limb, D_r12_provenance) — the call
        // below was byte-for-byte prepareEditor's body.
        vaadinx.awt.Component comp = prepareEditor(ed, row, column);
        if (comp == null) return false;

        withPeer(p -> installEditorComponents(row, column, comp));

        this.cellEditor = ed;
        this.editingRow = row;
        this.editingColumn = column;
        this.editingComponent = comp;
        ed.addCellEditorListener(this);

        Integer item = itemForRow(row);
        withPeer(p -> openGridEditor(item));
        return true;
    }

    /** The peer half of {@link #editCellAt}. */
    private void openGridEditor(Integer item) {
        // Protect the edited row's model row from an external setValueAt re-render
        // while the editor is open (Task 2). Cleared in cleanupEdit.
        surrogate().setRefreshProtectedRow(item);
        // Escape cancels the in-flight edit (B9). Vaadin's built-in Grid-editor
        // Escape handling doesn't fire for our programmatic single-column
        // setEditorComponent, so wire an explicit Escape shortcut scoped to the Grid,
        // active only during the edit (removed in cleanupEdit). The keydown→cancel is
        // a peer→Swing callback, so it funnels through callSwing per R_callswing_envelope.
        escapeCancelShortcut = com.vaadin.flow.component.Shortcuts.addShortcutListener(
                surrogate(),
                () -> vaadinx.EHelper.callSwing(this::cancelActiveEdit),
                com.vaadin.flow.component.Key.ESCAPE);
        escapeCancelShortcut.listenOn(surrogate());
        surrogate().getEditor().editItem(item);
    }

    /**
     * Gives every Grid column an editor component for view row {@code row}. Vaadin renders
     * ONLY editor-component cells in an edit-mode row (B2/B4): the edited column gets
     * {@code comp}, the others a read-only display snapshot (reusing the render path) —
     * otherwise the row's other cells blank out while editing. All are cleared on cleanup.
     * Only the edited column is editable.
     */
    private void installEditorComponents(int row, int column, vaadinx.awt.Component comp) {
        var editCols = surrogate().getColumns();
        for (int j = 0; j < editCols.size(); j++) {
            com.vaadin.flow.component.Component cell;
            if (j == column) {
                cell = comp.getPeer();
            } else {
                // Non-edited cells: disabled for a clear "off right now" affordance, and
                // click-transparent so a click reaches the Grid → item-click → editCellAt to
                // switch cells (B7). Force pointer-events:none here because a Boolean column's
                // live checkbox render (B3) is interactive and wouldn't otherwise be transparent.
                cell = renderCellViaEmulator(Integer.valueOf(toModelRow(row)), j);
                if (cell instanceof com.vaadin.flow.component.HasEnabled he) {
                    he.setEnabled(false);
                }
                cell.getElement().getStyle().set("pointer-events", "none");
            }
            // Compact "small" theme so the editor fits the row height rather than growing it (B5).
            cell.getElement().getThemeList().add("small");
            editCols.get(j).setEditorComponent(cell);
        }
    }

    public boolean isEditing() { return cellEditor != null; }
    /** The row being edited as a <em>view</em> row (JDK contract), or {@code -1} (D_jtable_cell_editing). */
    public int getEditingRow() { return editingRow; }
    public int getEditingColumn() { return editingColumn; }
    public vaadinx.awt.Component getEditorComponent() { return editingComponent; }

    /** Tears down the in-flight edit without writing back (JDK contract: no commit). */
    public void removeEditor() { discardActiveEdit(); }

    /**
     * Starts editing from a client click on the given Grid item + column, gated
     * by the cell editor's {@code clickCountToStart} (the JTable trigger rule).
     * The column is resolved positionally (no stable keys). No-op if the item /
     * column don't resolve, the cell isn't editable, or the click count is below
     * the editor's threshold.
     */
    private void startEditFromClick(Object item, com.vaadin.flow.component.grid.Grid.Column<?> column, int clickCount) {
        if (!(item instanceof Integer) || column == null) return;
        int col = surrogate().getColumns().indexOf(column);
        if (col < 0) return;
        // The Grid item is the *model* row; convert to a view row so we enter the
        // view-indexed public editing path (isCellEditable / editCellAt) once (D_jtable_cell_editing).
        int row = toViewRow((Integer) item);
        if (!isCellEditable(row, col)) return;
        // Editable Boolean cells self-edit via their live checkbox render (B3) — don't
        // open the racy single-click Grid.Editor for them.
        if (getColumnClass(col) == Boolean.class) return;
        TableCellEditor ed = getCellEditor(row, col);
        if (ed == null) return;
        int start = (ed instanceof DefaultCellEditor) ? ((DefaultCellEditor) ed).getClickCountToStart() : 1;
        if (clickCount >= start) editCellAt(row, col, null);
    }

    /**
     * The Grid item (the boxed <em>model</em> row index the fetch emits) for a
     * <em>view</em> row: converts through the {@code RowSorter} when one is active,
     * identity otherwise (D_jtable_cell_editing). Feeds {@code Grid.Editor.editItem}.
     */
    private Integer itemForRow(int viewRow) {
        return Integer.valueOf(toModelRow(viewRow));
    }

    // --- CellEditorListener: the table listens to its own in-flight editor, as the JDK's ---
    // editCellAt does (editor.addCellEditorListener(this)). Public on JTable in the JDK too,
    // so a subclass can override either half and stay on the edit path.

    /** Editing finished — commit the value. */
    public void editingStopped(ChangeEvent e) {
        commitActiveEdit();
    }

    /** Editing abandoned — tear down without writing back. */
    public void editingCanceled(ChangeEvent e) {
        discardActiveEdit();
    }

    /** Commit: read the (coerced) editor value and write it back, then tear down. */
    private void commitActiveEdit() {
        if (cellEditor == null) return;  // guard: already handled / re-entry
        int row = editingRow, col = editingColumn;
        Object value = cellEditor.getCellEditorValue();
        cleanupEdit();
        setValueAt(value, row, col);
    }

    /** Discard: tear down without writing back. */
    private void discardActiveEdit() {
        if (cellEditor == null) return;
        cleanupEdit();
    }

    /** Vaadin editor closed (blur / programmatic) — commit the in-flight Swing edit if any. */
    private void onVaadinEditorClosed() {
        if (cellEditor != null) cellEditor.stopCellEditing();  // → editingStopped → commitActiveEdit
    }

    /** Vaadin editor cancelled — cancel the in-flight Swing edit if any. */
    private void onVaadinEditorCancelled() {
        cancelActiveEdit();
    }

    /**
     * Cancel the in-flight edit (Escape key per B9, or a Vaadin editor-cancel):
     * drives {@code cancelCellEditing} so the editor's own {@code CellEditorListener}s
     * fire {@code editingCanceled} → {@link #discardActiveEdit} (no write-back).
     */
    private void cancelActiveEdit() {
        if (cellEditor != null) cellEditor.cancelCellEditing();
    }

    /** Remove the active listener, clear the column's editor component, close the Grid editor, reset state. */
    private void cleanupEdit() {
        if (cellEditor != null) {
            cellEditor.removeCellEditorListener(this);
        }
        // Tear down the Escape-cancel shortcut (B9) — it's live only during an edit.
        if (escapeCancelShortcut != null) {
            com.vaadin.flow.component.ShortcutRegistration shortcut = escapeCancelShortcut;
            withPeer(p -> shortcut.remove());
            escapeCancelShortcut = null;
        }
        // Reset state BEFORE closeEditor so the close listener sees "not editing" and no-ops.
        cellEditor = null;
        editingComponent = null;
        editingRow = -1;
        editingColumn = -1;
        withPeer(p -> {
            // We set editor components on ALL columns of the edited row (B2/B4), so clear all.
            for (var c : surrogate().getColumns()) {
                c.setEditorComponent((com.vaadin.flow.component.Component) null);
            }
            // Drop the refresh protection (Task 2) so the commit's own setValueAt
            // (which runs right after cleanupEdit in commitActiveEdit) renders normally.
            surrogate().setRefreshProtectedRow(null);
            if (surrogate().getEditor().isOpen()) {
                surrogate().getEditor().closeEditor();
            }
        });
    }

    /**
     * JDK contract: default editor for a column class, walking the superclass
     * chain then falling back to the {@code Object.class} editor. Mirrors
     * {@link #getDefaultRenderer(Class)}.
     */
    public TableCellEditor getDefaultEditor(Class<?> columnClass) {
        if (columnClass == null) return null;
        Class<?> c = columnClass;
        while (c != null) {
            TableCellEditor e = defaultEditorsByColumnClass.get(c);
            if (e != null) return e;
            c = c.getSuperclass();
        }
        return defaultEditorsByColumnClass.get(Object.class);
    }

    public void setDefaultEditor(Class<?> columnClass, TableCellEditor editor) {
        if (columnClass == null) return;
        if (editor == null) {
            defaultEditorsByColumnClass.remove(columnClass);
        } else {
            defaultEditorsByColumnClass.put(columnClass, editor);
        }
    }

    /**
     * JDK contract: the column's own {@link TableColumn#getCellEditor()} when
     * set, else the {@link #getDefaultEditor(Class)} for the column class.
     * Mirrors {@link #getCellRenderer(int, int)}.
     */
    public TableCellEditor getCellEditor(int row, int column) {
        TableColumn tc = columnModel.getColumn(column);
        TableCellEditor e = tc.getCellEditor();
        if (e == null) e = getDefaultEditor(getColumnClass(column));
        return e;
    }

    /** The editor in flight during editing (JDK {@code cellEditor}); {@code null} when not editing. */
    public TableCellEditor getCellEditor() { return cellEditor; }

    public void setCellEditor(TableCellEditor anEditor) {
        TableCellEditor old = this.cellEditor;
        this.cellEditor = anEditor;
        firePropertyChange("tableCellEditor", old, anEditor);
    }

    /**
     * Prepares the editor's component for a cell, seeded with the cell value
     * (JDK contract). The interactive open path (editCellAt / isEditing / …)
     * lands with the Grid.Editor bridge (D_jtable_cell_editing, Slice 2); resolution + prepare
     * are wired now.
     */
    public vaadinx.awt.Component prepareEditor(TableCellEditor editor, int row, int column) {
        Object value = getValueAt(row, column);
        boolean isSelected = isRowSelected(row);
        return editor.getTableCellEditorComponent(this, value, isSelected, row, column);
    }

    // --- Ported default cell editors (JDK JTable.GenericEditor / NumberEditor /
    //     BooleanEditor). getCellEditorValue coerces String -> columnClass so
    //     migrated code reading it back gets the typed value (D_jtable_cell_editing). ---

    /**
     * Shared {@code String -> columnClass} coercion (D_jtable_cell_editing). Mirrors JDK
     * {@code JTable.GenericEditor}'s reflective {@code Type(String)} constructor,
     * including the {@code Object -> String} passthrough gotcha (an un-overridden
     * {@code getColumnClass} leaves values as {@code String}, not the numeric
     * type). Used by {@link GenericEditor} on cell-edit commit
     * ({@link GenericEditor#stopCellEditing}).
     *
     * @throws Exception when {@code text} can't build the target type (e.g.
     *         {@code "abc"} for Integer); callers surface this as refuse-to-commit.
     */
    static Object coerceToColumnClass(String text, Class<?> columnClass) throws Exception {
        Class<?> type = (columnClass == Object.class) ? String.class : columnClass;
        return type.getConstructor(String.class).newInstance(text);
    }

    /**
     * Port of the (non-public) {@code javax.swing.JTable.GenericEditor} — the
     * default {@code Object.class} editor. A text field whose committed String
     * is coerced to the column class via {@link #coerceToColumnClass}. On bad
     * input {@code stopCellEditing} returns {@code false} (JDK also paints a red
     * border; dropped per R_layouts_close_enough as pure visual).
     */
    static class GenericEditor extends DefaultCellEditor {
        private Class<?> columnClass = Object.class;
        private Object value;

        GenericEditor() {
            super(new JTextField());
        }

        @Override
        public vaadinx.awt.Component getTableCellEditorComponent(JTable table, Object v,
                boolean isSelected, int row, int column) {
            this.value = null;
            this.columnClass = table.getColumnClass(column);
            return super.getTableCellEditorComponent(table, v, isSelected, row, column);
        }

        @Override
        public boolean stopCellEditing() {
            String s = (String) super.getCellEditorValue();
            try {
                value = coerceToColumnClass(s, columnClass);
            } catch (Exception e) {
                return false;
            }
            return super.stopCellEditing();
        }

        @Override
        public Object getCellEditorValue() {
            return value;
        }
    }

    /**
     * Port of the (non-public) {@code javax.swing.JTable.NumberEditor} — a
     * {@link GenericEditor} for {@code Number.class} columns. JDK right-aligns
     * the field; alignment is R_vaadin_first drop-and-WARN visual, so coercion (inherited
     * unchanged) is the only behaviour. Kept as a distinct type for
     * {@code getDefaultEditor(Number.class)} shape-parity with JDK.
     */
    static class NumberEditor extends GenericEditor {
        NumberEditor() {
            super();
        }
    }

    /**
     * Port of the (non-public) {@code javax.swing.JTable.BooleanEditor} — the
     * default {@code Boolean.class} editor over a check box. JDK centers the
     * box; alignment is dropped per R_layouts_close_enough. {@code getCellEditorValue} returns a
     * {@code Boolean} via {@link DefaultCellEditor}'s check-box delegate.
     */
    static class BooleanEditor extends DefaultCellEditor {
        BooleanEditor() {
            super(new JCheckBox());
        }
    }

    // --- Sorting: getRowSorter / setRowSorter live next to setAutoCreateRowSorter above. ---

    // --- TableModelListener / ListSelectionListener / TableColumnModelListener
    //     (JDK contract: JTable IS-A all three; bodies forward through emulator
    //     as JDK does. The surrogate already handles the structural work.)

    /**
     * Invoked when this table's {@code TableModel} generates a {@code TableModelEvent}, which
     * is in model coordinates. The table is the model's listener, as the JDK's is: it adjusts
     * the view selection and the editing row — through the sort bookkeeping when a sorter is
     * installed — and hands the change to the surrogate, which tells the sorter and refreshes
     * the Grid. A subclass overriding this without calling {@code super} stops the table
     * following its model, as it would in the JDK.
     */
    @Override
    public void tableChanged(TableModelEvent e) {
        boolean outermost = !changingModel;
        changingModel = true;
        try {
            applyTableChange(e);
        } finally {
            changingModel = !outermost;
        }
        if (outermost) {
            pushSelectionToPeer();
        }
    }

    /**
     * Set while a model change is being applied: the view selection moves more than once
     * meanwhile and the surrogate's shifts on its own, so the mirror onto the surrogate waits
     * for the end of {@link #tableChanged}.
     */
    private boolean changingModel;

    private void applyTableChange(TableModelEvent e) {
        if (e == null || e.getFirstRow() == TableModelEvent.HEADER_ROW) {
            // The whole thing changed
            clearSelectionAndLeadAnchor();
            // Auto-created columns are about to be replaced, and the JDK's columnRemoved
            // stops an edit in flight — commit, else cancel — before they go.
            if (getAutoCreateColumnsFromModel() && isEditing() && !getCellEditor().stopCellEditing()) {
                getCellEditor().cancelCellEditing();
            }
            if (sortManager != null) {
                try {
                    ignoreSortChange = true;
                    sortManager.sorter.modelStructureChanged();
                } finally {
                    ignoreSortChange = false;
                }
                sortManager.allChanged();
            }
            // The surrogate clears its own selection and rebuilds its Vaadin columns from the
            // columns it has; then the new ones arrive through the column bridge.
            peerTableChanged(e);
            if (getAutoCreateColumnsFromModel()) {
                createDefaultColumnsFromModel();
            } else {
                installEmulatorVaadinRenderers();
            }
            return;
        }

        if (sortManager != null) {
            sortedTableChanged(null, e);
            return;
        }

        int start = Math.max(0, e.getFirstRow());
        if (e.getType() == TableModelEvent.INSERT) {
            // Adjust the selection to account for the new rows.
            int end = e.getLastRow() < 0 ? getRowCount() - 1 : e.getLastRow();
            selectionModel.insertIndexInterval(start, end - start + 1, true);
        } else if (e.getType() == TableModelEvent.DELETE) {
            // Adjust the selection to account for the removed rows.
            int end = e.getLastRow() < 0 ? getRowCount() - 1 : e.getLastRow();
            selectionModel.removeIndexInterval(start, end);
        } else if (e.getLastRow() == Integer.MAX_VALUE) {
            clearSelectionAndLeadAnchor();
        }
        peerTableChanged(e);
    }

    /**
     * Hands a model change, which this table has already told its sorter about, to the
     * surrogate: it shifts its own model-row selection and refreshes the Grid. The shift is an
     * echo of this change rather than browser input, so the write runs muted
     * ({@link #onPeerSelectionMuted}).
     */
    private void peerTableChanged(TableModelEvent e) {
        onPeerSelectionMuted(() -> surrogate().tableChanged(e));
    }

    /**
     * Invoked when the row selection changes. Keeps the sort bookkeeping's cached selection
     * honest and mirrors the change onto the surrogate's model-row selection, which is what
     * the Grid shows — the JDK's repaint, in this table's terms.
     */
    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (sortManager != null) {
            sortManager.viewSelectionChanged(e);
        }
        if (e.getValueIsAdjusting()) {
            return;
        }
        pushSelectionToPeer();
    }

    // The three column-model callbacks take the PORTED TableColumnModelEvent
    // (R_no_vaadin_in_api limb 1). JDK-typed, they were unreachable twice over: no ported
    // column model can produce a javax.swing.event.TableColumnModelEvent to
    // call them with, and `getColumnModel().addColumnModelListener(table)` —
    // the JDK idiom that registers a JTable on its own column model — did not
    // even compile, since the ported model wants a ported listener.
    @Override
    public void columnAdded(vaadinx.swing.event.TableColumnModelEvent e) { /* See class javadoc — the structural work is the surrogate's. */ }
    @Override
    public void columnRemoved(vaadinx.swing.event.TableColumnModelEvent e) { /* See class javadoc. */ }
    @Override
    public void columnMoved(vaadinx.swing.event.TableColumnModelEvent e) { /* See class javadoc. */ }
    @Override
    public void columnMarginChanged(ChangeEvent e) { /* R_vaadin_first drop. */ }
    @Override
    public void columnSelectionChanged(ListSelectionEvent e) { /* R_vaadin_first drop. */ }

    // --- Layout (JDK Scrollable interface) ---

    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        // JDK default = row height for vertical, column width estimate
        // for horizontal. R_layouts_close_enough close-enough.
        return orientation == javax.swing.SwingConstants.VERTICAL ? getRowHeight() : 100;
    }

    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    public boolean getScrollableTracksViewportWidth() { return false; }
    public boolean getScrollableTracksViewportHeight() { return false; }

    public void doLayout() {
        // R_layouts_close_enough layout-close-enough — Vaadin Grid handles its own layout.
    }

    public void sizeColumnsToFit(int resizingColumn) {
        // R_layouts_close_enough close-enough. JDK adjusts column widths under autoResizeMode;
        // we don't drive that.
    }

    public void sizeColumnsToFit(boolean lastColumnOnly) {
        // Deprecated since JDK 1.4 — preserved for shape compat.
    }

    // --- Hit testing (R_layouts_close_enough: no server-side cell geometry) ---

    /**
     * The clicked cell, while a mouse event it caused is being dispatched: what stands in for
     * the pixel arithmetic of {@link #rowAtPoint} / {@link #columnAtPoint}, in view
     * coordinates. Held for the whole dispatch, so a handler that parks on a modal dialog
     * still sees it after resuming.
     */
    private int clickRowStash = -1;
    private int clickColumnStash = -1;

    /**
     * The clicked view row during a mouse handler's dispatch, {@code -1} otherwise, whatever
     * the coordinates. The rest is the JDK's body, so a table shrunk while the handler was
     * parked answers {@code -1}.
     */
    public int rowAtPoint(java.awt.Point point) {
        java.util.Objects.requireNonNull(point, "point");
        // The JDK divides point.y by this. Without view geometry the click stands in for the
        // division, but an override still runs.
        getRowHeight();
        int result = clickRowStash;
        if (result < 0) {
            return -1;
        } else if (result >= getRowCount()) {
            return -1;
        } else {
            return result;
        }
    }

    /**
     * The clicked view column during a mouse handler's dispatch, {@code -1} otherwise (or for
     * a row-only click) — see {@link #rowAtPoint}.
     */
    public int columnAtPoint(java.awt.Point point) {
        java.util.Objects.requireNonNull(point, "point");
        // The JDK mirrors point.x for a right-to-left table through getWidth(), a WARN stub
        // here; the click is a view column already, so only the orientation read stays.
        getComponentOrientation().isLeftToRight();
        int column = clickColumnStash;
        return column >= 0 && column < getColumnModel().getColumnCount() ? column : -1;
    }

    // --- Print (permanent sub-bucket (b)) ---

    public boolean print() {
        vaadinx.EHelper.onUnimplemented("JTable", "print");
        return false;
    }

    public boolean print(javax.swing.JTable.PrintMode printMode) {
        vaadinx.EHelper.onUnimplemented("JTable", "print", printMode);
        return false;
    }

    // --- L&F stubs ---

    public String getUIClassID() {
        return "TableUI";
    }

    public void updateUI() {
        // L&F swap — no-op. Same shape as JComboBox / JSpinner.
    }

    public javax.swing.plaf.TableUI getUI() {
        return null;
    }

    public void setUI(javax.swing.plaf.TableUI ui) {
        if (ui != null) vaadinx.EHelper.onUnimplemented("JTable", "setUI", ui);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JTable", "getAccessibleContext");
        return null;
    }
}
