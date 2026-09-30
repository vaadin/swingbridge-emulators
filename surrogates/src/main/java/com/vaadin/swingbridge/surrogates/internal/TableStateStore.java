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

package com.vaadin.swingbridge.surrogates.internal;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.shared.Registration;

import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.event.RowSorterListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.SJTable} (SD_sjtable).
 *
 * <p>Holds <em>only</em> R_vaadin_first-legitimate state — UI-functionality
 * source-of-truth (the three models + the row sorter), the behaviour-gating
 * auto-create flags, and machinery (loop guard, live registrations). No
 * shadow caches: JTable's no-Vaadin-counterpart knobs (rowHeight, rowMargin,
 * intercellSpacing, gridColor, show*Lines, autoResizeMode, selection chrome,
 * row/columnSelectionAllowed, dragEnabled, fillsViewportHeight,
 * preferredScrollableViewportSize) round-trip on the <em>emulator</em> layer
 * per R_swing_is_truth — the asymmetric split SJToolBar (SD_sjtoolbar) / SJSplitPane (SD_sjsplitpane) /
 * SJList (SD_sjlist) established. The JDK selection-mode int isn't stored either:
 * it is reconstructed from {@link #selectionModel}'s {@code getSelectionMode()}.
 *
 * <p>Source-of-truth fields per R_swing_is_truth:
 * <ul>
 *   <li>{@link #model} — installed {@link TableModel}; rows × columns × cells.</li>
 *   <li>{@link #columnModel} — installed {@link TableColumnModel}; column order, widths, headers.</li>
 *   <li>{@link #selectionModel} — installed row {@link ListSelectionModel}.</li>
 *   <li>{@link #rowSorter} — installed {@link RowSorter}; sort permutation + filter.</li>
 * </ul>
 * All JDK types reused per D_whitelist_porting/D_event_port_policy (no Component-touching public members).
 */
public final class TableStateStore {

    /** Source of truth for cell values (R_swing_is_truth). Never null after ctor. */
    public TableModel model;

    /** Source of truth for column order + per-column metadata. Never null after ctor. */
    public TableColumnModel columnModel;

    /** Source of truth for row selection state. Never null after ctor. */
    public ListSelectionModel selectionModel;

    /**
     * R_swing_is_truth feedback-loop guard. Set during model→peer pushes (push-items,
     * push-selection) and during peer→model write-throughs so the cascade
     * doesn't double-fire. Same shape SJComboBox / SJSlider take.
     */
    public boolean preventPeerEvents;

    /**
     * Row index / view-column index of the cell whose item-click is
     * currently being dispatched, or {@code -1} outside a dispatch.
     * Set/cleared around the {@code SMouseListener} fan-out in
     * {@code SJTable.addMouseListener}'s item-click wire so
     * {@code rowAtPoint} / {@code columnAtPoint} resolve during the user's
     * {@code mouseClicked} handler (R_layouts_close_enough — no server-side pixel→cell map).
     * Fields, not thread-locals: the emulator re-source may drain the
     * dispatch in a UI fiber, and shared state survives that boundary.
     */
    public int clickRowStash = -1;
    public int clickColumnStash = -1;

    /**
     * Model row currently protected from {@code dataProvider.refreshItem}, or
     * {@code null} when none. Machinery (sibling to {@link #preventPeerEvents}),
     * not editing API: the emulator's cell-editing lifecycle sets this to the
     * open editor's model row and clears it on teardown, so an external /
     * concurrent {@code setValueAt} UPDATE on that row (a Timer, another action)
     * can't re-render the row out from under the in-flight editor (D_jtable_cell_editing Task 2).
     */
    public Integer refreshProtectedRow;

    /**
     * True while the Grid reacts to a data change — the window in which Vaadin closes an open
     * editor. Machinery (sibling to {@link #refreshProtectedRow}): lets the emulator tell that
     * close from the user leaving the cell.
     */
    public boolean editorClosingForDataChange;

    /**
     * JDK default true. Drives whether {@link #model} structure changes
     * (HEADER_ROW events) trigger a column rebuild from the model. Gates
     * real behaviour (UI-functionality, not a round-trip shadow).
     */
    public boolean autoCreateColumnsFromModel = true;

    /**
     * JDK default false. When true, {@code setModel} auto-installs a fresh
     * {@link javax.swing.table.TableRowSorter} for the new model — gates
     * real behaviour (lights up the Vaadin Grid sort indicator).
     */
    public boolean autoCreateRowSorter = false;

    /**
     * Currently installed JDK row sorter, or {@code null} when no sort is
     * active. Single source of truth for sort permutation —
     * {@code convertRowIndexToView/Model} reads through it; the Vaadin
     * Grid {@code FetchCallback} reads through it. JDK-reused per D_event_port_policy.
     */
    public RowSorter<? extends TableModel> rowSorter;

    /**
     * Listener installed on {@link #rowSorter} that bridges programmatic
     * {@code rowSorter.setSortKeys(...)} calls back into the Vaadin Grid's
     * sort indicator + triggers a re-fetch via {@code grid.sort(...)}.
     * Tracked here so {@code setRowSorter} swap can detach cleanly.
     */
    public RowSorterListener rowSorterEventListener;

    /**
     * Vaadin Grid {@code SelectionListener} registration that mirrors
     * browser-side selection edits into the {@link #selectionModel}.
     * Re-installed on {@code setSelectionMode} since Vaadin re-creates the
     * Grid's internal selection backing on mode swap.
     */
    public Registration vaadinSelectionRegistration;

    private TableStateStore() {}

    public static TableStateStore of(Component target) {
        TableStateStore existing = ComponentUtil.getData(target, TableStateStore.class);
        if (existing != null) return existing;
        TableStateStore fresh = new TableStateStore();
        ComponentUtil.setData(target, TableStateStore.class, fresh);
        return fresh;
    }
}
