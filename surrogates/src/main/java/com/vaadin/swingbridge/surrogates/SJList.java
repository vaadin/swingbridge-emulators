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
import com.vaadin.flow.component.grid.ItemClickEvent;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.Renderer;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.awt.event.SInputEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseListener;
import com.vaadin.swingbridge.surrogates.internal.ListStateStore;
import com.vaadin.swingbridge.surrogates.internal.Registrations;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Surrogate for {@link javax.swing.JList} (SD_sjlist). Extends Vaadin
 * {@link Grid Grid&lt;Integer&gt;} — exactly SJTable's row-index identity,
 * narrowed to a single headerless column. Row indices are the Grid's
 * items, the installed {@link ListModel} is the single source of truth
 * (R_swing_is_truth), and the {@link ListSelectionModel} (also index-based) bridges
 * directly onto Vaadin Grid's Integer-keyed selection.
 *
 * <h2>Why Grid, not VirtualList / ListBox</h2>
 *
 * Grid is the only Vaadin list-shaped component whose native selection
 * covers all three JDK modes through one call ({@link Grid#setSelectionMode}
 * — {@code NONE/SINGLE/MULTI}); {@code VirtualList} carries no selection
 * at all, and {@code ListBox}/{@code MultiSelectListBox} are two separate
 * classes (one per mode) a surrogate can't swap between at runtime. Picking
 * Grid also reuses SJTable's proven {@code ListSelectionModel}↔Grid
 * selection bridge verbatim and rides the existing SJScrollPane auto-scroll
 * guard (which already detects {@code Grid} content) for the universal
 * {@code new JScrollPane(jlist)} idiom. The cost — a checkbox column in
 * multi-select mode — is accepted per R_best_effort_behaviour/R_layouts_close_enough (see SD_sjlist). Duplicate
 * {@code .equals()} elements (which JList permits) are why the items stay
 * {@code Integer} indices, not {@code E}: Vaadin Grid keys items by
 * identity and would collide on equal values.
 *
 * <h2>Vaadin-first rendering (R_vaadin_first)</h2>
 *
 * Surrogate-layer code installs a Vaadin {@link Renderer Renderer&lt;Integer&gt;}
 * via {@link #setRenderer(Renderer)}; the per-cell renderer reads through
 * to {@code model.getElementAt(index)} live. The JDK {@code ListCellRenderer}
 * surface (return type {@code java.awt.Component} clashes with our
 * hierarchy per D_event_port_policy) lives only on the emulator layer ({@code :emulators.JList}),
 * which bridges it into a Vaadin {@code ComponentRenderer} at the boundary
 * (D_jcombobox). Pure-surrogate users without {@link #setRenderer} get a default
 * {@code value.toString()} {@link Span} renderer — fresh-per-call to dodge
 * Vaadin's DOM-adoption diff (D_jcombobox's lesson).
 *
 * <h2>Double-click bridge (locationToIndex)</h2>
 *
 * {@link #addMouseListener(SMouseListener)} overrides the inherited mixin
 * wire (Grid dispatches <em>item</em> clicks, not component clicks, and
 * the item-click carries the row index). It subscribes
 * {@code addItemClickListener} and fires {@link SMouseEvent#MOUSE_CLICKED}
 * carrying the browser's click count — so a double-click arrives as
 * {@code clickCount == 2}, the canonical "double-click to open" idiom.
 * The clicked index is stashed in {@link ListStateStore#clickIndexStash}
 * around the fan-out so {@link #locationToIndex(Point)} resolves correctly
 * during the user's {@code mouseClicked} handler (R_layouts_close_enough — no server-side
 * pixel→index map otherwise). Native Vaadin {@code ItemClickListener}s a
 * stage-3 user adds coexist untouched.
 *
 * <h2>R_vaadin_first drop-and-WARN surface</h2>
 *
 * {@code layoutOrientation} wrap modes (no Vaadin newspaper-flow list),
 * {@code prototypeCellValue} (sizing optimisation), {@code dragEnabled}
 * (cross-cutting DnD). {@code visibleRowCount} /
 * {@code fixedCellWidth} / {@code fixedCellHeight} / cell bounds /
 * {@code indexToLocation} are round-trip-only or null per R_layouts_close_enough (Vaadin Grid
 * virtualises and owns its own sizing).
 */
public class SJList<E> extends Grid<Integer> implements JComponentMixin,
        ListSelectionListener, ListDataListener {

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

    // --- JDK JList layout-orientation constants ---

    public static final int VERTICAL = 0;
    public static final int VERTICAL_WRAP = 1;
    public static final int HORIZONTAL_WRAP = 2;

    /** Stable key on the single value column (one per grid → unique). */
    private static final String COLUMN_KEY = "value";

    // --- Constructors ---

    public SJList() {
        this(new javax.swing.DefaultListModel<E>());
    }

    public SJList(ListModel<E> dataModel) {
        super();
        Objects.requireNonNull(dataModel, "dataModel must be non null");
        _installSwingClass();
        ListStateStore store = store();

        // Install the FetchCallback first — refreshDataProvider() relies on
        // it. Initial fetches see model = null briefly and return empty.
        installFetchCallback();

        // Single headerless column. JList has no header; we leave the
        // Vaadin column header unset (collapses to a minimal row, R_layouts_close_enough).
        addColumn(new ComponentRenderer<>(this::defaultRenderCell)).setKey(COLUMN_KEY);

        // Selection model first (JDK-ish order), then data model.
        store.selectionModel = createDefaultSelectionModel();
        store.selectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        store.selectionModel.addListSelectionListener(this);

        store.model = dataModel;
        dataModel.addListDataListener(this);

        // JList default selection mode is MULTIPLE_INTERVAL_SELECTION → MULTI.
        super.setSelectionMode(Grid.SelectionMode.MULTI);
        installVaadinSelectionListener();

        refreshDataProvider();
    }

    public SJList(E[] listData) {
        this(arrayModel(listData));
    }

    public SJList(Vector<E> listData) {
        this(vectorModel(listData));
    }

    private ListStateStore store() {
        return ListStateStore.of(this);
    }

    protected ListSelectionModel createDefaultSelectionModel() {
        return new DefaultListSelectionModel();
    }

    /**
     * Wrap an array in an immutable anonymous {@link AbstractListModel},
     * matching JDK {@code JList(E[])} — {@code getModel()} returns a
     * non-mutable model in that case.
     */
    private static <E> ListModel<E> arrayModel(E[] listData) {
        Objects.requireNonNull(listData, "listData must be non null");
        return new AbstractListModel<>() {
            @Override public int getSize() { return listData.length; }
            @Override public E getElementAt(int i) { return listData[i]; }
        };
    }

    private static <E> ListModel<E> vectorModel(Vector<E> listData) {
        Objects.requireNonNull(listData, "listData must be non null");
        return new AbstractListModel<>() {
            @Override public int getSize() { return listData.size(); }
            @Override public E getElementAt(int i) { return listData.elementAt(i); }
        };
    }

    // --- Model accessors ---

    @SuppressWarnings("unchecked")
    public ListModel<E> getModel() {
        return (ListModel<E>) store().model;
    }

    public void setModel(ListModel<E> model) {
        Objects.requireNonNull(model, "model must be non null");
        ListStateStore store = store();
        ListModel<?> old = store.model;
        if (old == model) return;
        if (old != null) old.removeListDataListener(this);
        store.model = model;
        model.addListDataListener(this);
        firePropertyChange("model", old, model);
        // Stale indices into the prior model are meaningless against the
        // new one — clear so the selection model never points past the
        // model's size (R_best_effort_behaviour; matches the L&F's effective behaviour).
        store.selectionModel.clearSelection();
        refreshDataProvider();
    }

    public void setListData(E[] listData) {
        setModel(arrayModel(listData));
    }

    public void setListData(Vector<E> listData) {
        setModel(vectorModel(listData));
    }

    private int modelSize() {
        ListModel<?> m = store().model;
        return m == null ? 0 : m.getSize();
    }

    // --- Vaadin-first renderer install (R_vaadin_first) ---

    /**
     * Install a Vaadin {@link Renderer Renderer&lt;Integer&gt;} for the list
     * cells. Replaces the single column. Migrated code at the surrogate
     * stage uses this rather than the JDK {@code ListCellRenderer} surface
     * (which lives only on the emulator layer per D_event_port_policy + R_vaadin_first).
     */
    public void setRenderer(Renderer<Integer> renderer) {
        Objects.requireNonNull(renderer, "renderer must be non null");
        for (Grid.Column<Integer> c : new ArrayList<>(getColumns())) {
            removeColumn(c);
        }
        addColumn(renderer).setKey(COLUMN_KEY);
        refreshDataProvider();
    }

    /**
     * Default cell renderer for pure-surrogate users — reads through to
     * {@code model.getElementAt(index)} live and returns a fresh {@link Span}
     * per call (D_jcombobox DOM-adoption guard).
     */
    private Component defaultRenderCell(Integer rowKey) {
        ListModel<?> m = store().model;
        if (rowKey == null || m == null) return new Span("");
        int idx = rowKey;
        if (idx < 0 || idx >= m.getSize()) return new Span("");
        Object value = m.getElementAt(idx);
        return new Span(value == null ? "" : String.valueOf(value));
    }

    // --- Data provider plumbing (in-memory, model-read-through) ---

    private void installFetchCallback() {
        setItems(this::fetch, this::count);
    }

    private Stream<Integer> fetch(Query<Integer, Void> query) {
        ListModel<?> m = store().model;
        if (m == null) return Stream.empty();
        int size = m.getSize();
        int from = query.getOffset();
        int to = Math.min(from + query.getLimit(), size);
        if (to <= from) return Stream.empty();
        return IntStream.range(from, to).boxed();
    }

    private int count(Query<Integer, Void> query) {
        ListModel<?> m = store().model;
        return m == null ? 0 : m.getSize();
    }

    private void refreshDataProvider() {
        var dp = getDataProvider();
        if (dp != null) dp.refreshAll();
    }

    // --- ListDataListener — model events drive Grid refresh ---

    @Override
    public void intervalAdded(ListDataEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ListDataListener.intervalAdded
        SHelper.runOnOwnerUI(this, () -> {
            // Shift selection indices to track the insert (mirrors the effective
            // L&F behaviour the dropped BasicListUI provided).
            store().selectionModel.insertIndexInterval(e.getIndex0(), e.getIndex1() - e.getIndex0() + 1, true);
            refreshDataProvider();
        });
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ListDataListener.intervalRemoved
        SHelper.runOnOwnerUI(this, () -> {
            store().selectionModel.removeIndexInterval(e.getIndex0(), e.getIndex1());
            refreshDataProvider();
        });
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ListDataListener.contentsChanged
        SHelper.runOnOwnerUI(this, () -> {
            int i0 = e.getIndex0();
            int i1 = e.getIndex1();
            int size = modelSize();
            if (i0 < 0 || i1 < 0 || i0 >= size) {
                refreshDataProvider();
                return;
            }
            var dp = getDataProvider();
            int last = Math.min(i1, size - 1);
            for (int i = i0; i <= last; i++) {
                dp.refreshItem(i);
            }
        });
    }

    // --- Selection model accessors (getSelectionModel clash, per SD_sjtable/SD_sjlist) ---

    /**
     * JDK {@code getSelectionModel(): ListSelectionModel} can't coexist with
     * Vaadin Grid's same-named {@code getSelectionModel(): GridSelectionModel<T>}
     * (unrelated return types). Per R_vaadin_first the Vaadin shape wins at the surrogate;
     * the emulator ({@code :emulators.JList}) keeps the JDK-shaped getter and
     * reaches the model through this differently-named accessor. Same
     * resolution SJTable uses.
     */
    public ListSelectionModel getListSelectionModel() {
        return store().selectionModel;
    }

    /**
     * Current JDK selection mode; used by the emulator for PCE old-value
     * delivery. Reconstructed from the selection model (the source of truth)
     * rather than a redundant shadow field — R_vaadin_first.
     */
    public int getJdkSelectionMode() {
        return store().selectionModel.getSelectionMode();
    }

    public void setSelectionModel(ListSelectionModel selectionModel) {
        Objects.requireNonNull(selectionModel, "selectionModel must be non null");
        ListStateStore store = store();
        ListSelectionModel old = store.selectionModel;
        if (old == selectionModel) return;
        if (old != null) old.removeListSelectionListener(this);
        store.selectionModel = selectionModel;
        selectionModel.addListSelectionListener(this);
        firePropertyChange("selectionModel", old, selectionModel);
    }

    /**
     * JDK selection-mode setter. Validates per JDK contract, updates the
     * underlying {@link ListSelectionModel}, and maps to Vaadin Grid's
     * {@link Grid.SelectionMode}: SINGLE → SINGLE; the two interval modes
     * both map to MULTI (Vaadin Grid has no single-interval analog — the
     * {@link ListSelectionModel} still enforces the JDK contract).
     */
    public void setSelectionMode(int selectionMode) {
        switch (selectionMode) {
            case ListSelectionModel.SINGLE_SELECTION,
                 ListSelectionModel.SINGLE_INTERVAL_SELECTION,
                 ListSelectionModel.MULTIPLE_INTERVAL_SELECTION -> { /* ok */ }
            default -> throw new IllegalArgumentException("invalid selectionMode");
        }
        ListStateStore store = store();
        store.selectionModel.setSelectionMode(selectionMode);
        Grid.SelectionMode vaadinMode = (selectionMode == ListSelectionModel.SINGLE_SELECTION)
                ? Grid.SelectionMode.SINGLE
                : Grid.SelectionMode.MULTI;
        super.setSelectionMode(vaadinMode);
        // Vaadin re-creates the Grid's selection backing on mode swap.
        installVaadinSelectionListener();
        // No "selectionMode" property change: the JDK's JList.setSelectionMode
        // is getSelectionModel().setSelectionMode(mode) — the notification is
        // the model's ListSelectionEvent (SD_property_fanout_audit).
    }

    // --- Selection bridge (ListSelectionModel ↔ Vaadin Grid; R_callswing_envelope) ---

    private void installVaadinSelectionListener() {
        ListStateStore store = store();
        if (store.vaadinSelectionRegistration != null) {
            store.vaadinSelectionRegistration.remove();
        }
        store.vaadinSelectionRegistration = addSelectionListener(event ->
                SHelper.callSwing(this::syncSelectionFromPeer));
    }

    private void syncSelectionFromPeer() {
        ListStateStore store = store();
        if (store.preventPeerEvents) return;
        Set<Integer> selected = getSelectedItems();
        ListSelectionModel sm = store.selectionModel;
        store.preventPeerEvents = true;
        try {
            sm.clearSelection();
            for (int idx : selected) {
                sm.addSelectionInterval(idx, idx);
            }
        } finally {
            store.preventPeerEvents = false;
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ListSelectionListener.valueChanged
        SHelper.runOnOwnerUI(this, () -> {
            if (e.getValueIsAdjusting()) return;
            ListStateStore store = store();
            if (store.preventPeerEvents) return;
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
        int size = modelSize();
        for (int i = min; i <= max && i < size; i++) {
            if (sm.isSelectedIndex(i)) {
                select(i);
            }
        }
    }

    // --- Selection API (route through ListSelectionModel) ---

    public void setSelectedIndex(int index) {
        if (index >= modelSize()) return;
        if (index < 0) {
            clearSelection();
        } else {
            store().selectionModel.setSelectionInterval(index, index);
        }
    }

    public void setSelectedIndices(int[] indices) {
        Objects.requireNonNull(indices);
        ListSelectionModel sm = store().selectionModel;
        sm.clearSelection();
        int size = modelSize();
        for (int i : indices) {
            if (i >= 0 && i < size) sm.addSelectionInterval(i, i);
        }
    }

    public int getSelectedIndex() {
        return store().selectionModel.getMinSelectionIndex();
    }

    public int[] getSelectedIndices() {
        ListSelectionModel sm = store().selectionModel;
        if (sm.isSelectionEmpty()) return new int[0];
        int min = sm.getMinSelectionIndex();
        int max = sm.getMaxSelectionIndex();
        List<Integer> out = new ArrayList<>();
        for (int i = min; i <= max; i++) {
            if (sm.isSelectedIndex(i)) out.add(i);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    public int getMinSelectionIndex() {
        return store().selectionModel.getMinSelectionIndex();
    }

    public int getMaxSelectionIndex() {
        return store().selectionModel.getMaxSelectionIndex();
    }

    public boolean isSelectedIndex(int index) {
        return store().selectionModel.isSelectedIndex(index);
    }

    public boolean isSelectionEmpty() {
        return store().selectionModel.isSelectionEmpty();
    }

    public int getAnchorSelectionIndex() {
        return store().selectionModel.getAnchorSelectionIndex();
    }

    public int getLeadSelectionIndex() {
        return store().selectionModel.getLeadSelectionIndex();
    }

    @SuppressWarnings("unchecked")
    public E getSelectedValue() {
        int i = getMinSelectionIndex();
        if (i < 0 || i >= modelSize()) return null;
        return (E) store().model.getElementAt(i);
    }

    @SuppressWarnings("unchecked")
    public List<E> getSelectedValuesList() {
        ListSelectionModel sm = store().selectionModel;
        ListModel<?> m = store().model;
        if (sm.isSelectionEmpty() || m == null) return List.of();
        int min = sm.getMinSelectionIndex();
        int max = sm.getMaxSelectionIndex();
        List<E> out = new ArrayList<>();
        int size = m.getSize();
        for (int i = min; i <= max; i++) {
            if (sm.isSelectedIndex(i) && i < size) out.add((E) m.getElementAt(i));
        }
        return out;
    }

    /**
     * Select the cell whose model element equals {@code anObject}. {@code null}
     * clears the selection. {@code shouldScroll} drives
     * {@link #ensureIndexIsVisible(int)}.
     */
    public void setSelectedValue(Object anObject, boolean shouldScroll) {
        if (anObject == null) {
            clearSelection();
            return;
        }
        ListModel<?> m = store().model;
        if (m == null) return;
        for (int i = 0, n = m.getSize(); i < n; i++) {
            if (Objects.equals(anObject, m.getElementAt(i))) {
                setSelectedIndex(i);
                if (shouldScroll) ensureIndexIsVisible(i);
                return;
            }
        }
    }

    public void setSelectionInterval(int anchor, int lead) {
        store().selectionModel.setSelectionInterval(anchor, lead);
    }

    public void addSelectionInterval(int anchor, int lead) {
        store().selectionModel.addSelectionInterval(anchor, lead);
    }

    public void removeSelectionInterval(int index0, int index1) {
        store().selectionModel.removeSelectionInterval(index0, index1);
    }

    public void clearSelection() {
        store().selectionModel.clearSelection();
    }

    public boolean getValueIsAdjusting() {
        return store().selectionModel.getValueIsAdjusting();
    }

    public void setValueIsAdjusting(boolean b) {
        store().selectionModel.setValueIsAdjusting(b);
    }

    /** Scrolls so the row at {@code index} is visible. */
    public void ensureIndexIsVisible(int index) {
        if (index >= 0 && index < modelSize()) {
            scrollToIndex(index);
        }
    }

    // --- Mouse bridge: item-click → SMouseEvent + locationToIndex stash ---

    /**
     * Overrides the inherited {@code ComponentMixin} wire — Grid dispatches
     * item clicks (carrying the row index), not generic component clicks.
     * Each {@code SMouseListener} gets two Grid subscriptions, combined into
     * one {@link Registration}: {@code addItemClickListener} fires
     * {@link SMouseEvent#MOUSE_CLICKED} carrying the browser click count
     * (single = 1), and {@code addItemDoubleClickListener} fires it with
     * {@code clickCount == 2} — the canonical double-click-to-open signal.
     * {@code ItemDoubleClickEvent extends ItemClickEvent}, so one
     * translation path serves both. The clicked index is stashed around the
     * fan-out for {@link #locationToIndex(Point)}.
     *
     * <p>A real browser double-click also fires the intervening single click
     * (cc=1), which <em>is</em> AWT's escalation rather than an approximation of
     * it: both deliver a full cc=1 dispatch then a full cc=2 one (measured on
     * both sides, D_mouse_listener_bridge).
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
            ListStateStore store = store();
            int prev = store.clickIndexStash;
            store.clickIndexStash = (e.getItem() == null) ? -1 : e.getItem();
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
                store.clickIndexStash = prev;
            }
        });
    }

    /**
     * Returns the row index currently being click-dispatched (stashed by
     * {@link #addMouseListener}'s item-click wire), or {@code -1} outside a
     * click dispatch. There is no server-side pixel→index map (R_layouts_close_enough), so the
     * stash is the only honest answer; called from a user {@code mouseClicked}
     * handler — the dominant JList idiom — it returns the clicked row.
     */
    public int locationToIndex(Point location) {
        return store().clickIndexStash;
    }

    /** R_layouts_close_enough — no server-side cell coordinates. */
    public Point indexToLocation(int index) {
        return null;
    }

    /** R_layouts_close_enough — no server-side cell bounds. */
    public Rectangle getCellBounds(int index0, int index1) {
        return null;
    }

    // --- No-Vaadin-counterpart knobs: present-but-drop-and-WARN (R_vaadin_first) ---
    //
    // Grid virtualises / auto-sizes and the theme owns row chrome, so none
    // of these has a peer property to bind. Per R_vaadin_first the surrogate is
    // Vaadin-first: getters return JDK defaults, setters log-and-drop (no
    // shadow cache). JDK-faithful round-trip for these lives on the emulator
    // layer (vaadinx.swing.JList field-shadows + fires PCE per R_swing_is_truth) — the
    // asymmetric split SJToolBar (SD_sjtoolbar/D_jtoolbar) and SJSplitPane (SD_sjsplitpane/D_jsplitpane)
    // established. Stage-3 users style via CSS / theme instead.

    public int getLayoutOrientation() {
        return VERTICAL;
    }

    public void setLayoutOrientation(int layoutOrientation) {
        switch (layoutOrientation) {
            case VERTICAL -> { /* the only natively-rendered mode — silent */ }
            case VERTICAL_WRAP, HORIZONTAL_WRAP ->
                    SHelper.onUnimplemented(this, "setLayoutOrientation/wrap", layoutOrientation);
            default -> throw new IllegalArgumentException(
                    "layoutOrientation must be one of: VERTICAL, HORIZONTAL_WRAP or VERTICAL_WRAP");
        }
    }

    public int getVisibleRowCount() {
        return 8;   // JDK default
    }

    public void setVisibleRowCount(int visibleRowCount) {
        SHelper.onUnimplemented(this, "setVisibleRowCount", visibleRowCount);
    }

    public int getFixedCellWidth() {
        return -1;  // JDK default — "derive from renderer"
    }

    public void setFixedCellWidth(int width) {
        SHelper.onUnimplemented(this, "setFixedCellWidth", width);
    }

    public int getFixedCellHeight() {
        return -1;  // JDK default
    }

    public void setFixedCellHeight(int height) {
        SHelper.onUnimplemented(this, "setFixedCellHeight", height);
    }

    public E getPrototypeCellValue() {
        return null;
    }

    public void setPrototypeCellValue(E prototypeCellValue) {
        if (prototypeCellValue != null) {
            SHelper.onUnimplemented(this, "setPrototypeCellValue", prototypeCellValue);
        }
    }

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

    public boolean getDragEnabled() {
        return false;
    }

    public void setDragEnabled(boolean dragEnabled) {
        if (dragEnabled) {
            SHelper.onUnimplemented(this, "setDragEnabled", dragEnabled);
        }
    }

    // --- L&F stub (JDK contract) ---

    public String getUIClassID() {
        return "ListUI";
    }
}
