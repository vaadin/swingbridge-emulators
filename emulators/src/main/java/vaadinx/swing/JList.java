/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JList
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.swingbridge.surrogates.SJList;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Objects;
import java.util.Vector;

/**
 * Emulator for {@link javax.swing.JList}, over an {@link SJList} peer that renders the
 * Grid&lt;Integer&gt; (SD_sjlist). R_leaf_peer_lockdown-locked-down (JDK leaf — private
 * peer ctor). Implements {@link javax.swing.Scrollable} and
 * {@link javax.accessibility.Accessible} to match the JDK interface set.
 *
 * <p>The list owns its state, as the JDK's does: {@code dataModel} and
 * {@code selectionModel} are this class's fields, the selection methods are the JDK's
 * bodies over them, and no getter reads the peer
 * (D_emulator_owned_state). The surrogate shares both model objects,
 * pushed to it through {@code withPeer}: it renders the model, mirrors the selection onto
 * the Grid, and writes a browser selection into the selection model. It also shifts the
 * selection when the model inserts or removes rows, which is the JDK's
 * {@code BasicListUI} behaviour, not {@code JList}'s. The selection reaches this class's
 * listeners the JDK's way: a {@code ListSelectionHandler} on the selection model,
 * registered by the first {@link #addListSelectionListener}.
 *
 * <p>Item clicks come from a ctor-installed bridge re-sourcing the surrogate's
 * {@code SMouseListener} with source rebound to {@code this}, so
 * {@code addMouseListener} + {@code getClickCount()==2} + {@code locationToIndex}
 * works as written. The JDK {@link ListCellRenderer} surface lives here; a
 * dynamic Vaadin renderer on the surrogate resolves it per render call and
 * snapshots the result (D_event_port_policy / D_jcombobox / R_vaadin_first). See {@link #installSurrogateBridges} /
 * {@link #renderCellViaEmulator}.
 */
public class JList<E> extends vaadinx.swing.JComponent
        implements javax.swing.Scrollable, javax.accessibility.Accessible {

    // --- JDK JList layout-orientation constants ---

    public static final int VERTICAL = 0;
    public static final int VERTICAL_WRAP = 1;
    public static final int HORIZONTAL_WRAP = 2;

    /** Currently installed JDK-shaped cell renderer; seeded with DefaultListCellRenderer. */
    private vaadinx.swing.ListCellRenderer<? super E> cellRenderer;

    private ListModel<E> dataModel;
    private ListSelectionModel selectionModel;

    /**
     * The JDK's {@code ListSelectionHandler}: re-fires the selection model's events with this
     * list as the source. {@code null} until the first {@link #addListSelectionListener}, as
     * in the JDK, which decides where it sits among the selection model's own listeners.
     *
     * <p>Relayed rather than fired inline: a browser selection reaches the model from
     * the surrogate's peer listener, which has no UI fiber of its own, and a modal dialog
     * opened by the user's listener needs one (R_callswing_envelope).
     */
    private ListSelectionListener selectionListener;

    // --- Ctors ---

    public JList() {
        this(new AbstractListModel<E>() {
              public int getSize() { return 0; }
              public E getElementAt(int i) { throw new IndexOutOfBoundsException("No Data Model"); }
            });
    }

    /**
     * The root ctor, and the only one naming the peer: R_leaf_peer_lockdown lock-down (JDK
     * leaf — no protected (Component) ctor).
     *
     * @throws IllegalArgumentException if {@code dataModel} is null, as in the JDK
     */
    public JList(ListModel<E> dataModel) {
        super(SJList.class, peerFactory(dataModel));
        this.dataModel = dataModel;
        // The JDK's hook, so a subclass's override is reached.
        selectionModel = createSelectionModel();
        this.cellRenderer = new DefaultListCellRenderer();
        // This class sources its own MOUSE_CLICKED, off the surrogate's Grid item-click
        // wire, which also reports the clicked index for locationToIndex. Keep
        // Component's generic DOM bridge out, or every click arrives twice.
        suppressMouseBridge();
        dataModel.addListDataListener(selectionAdjuster);
        ListSelectionModel installed = selectionModel;
        withPeer(p -> {
            surrogate().setSelectionAdjustedByOwner(true);
            surrogate().setSelectionModel(installed);
            installEmulatorVaadinRenderer();
            installSurrogateBridges();
        });
    }

    public JList(E[] listData) {
        this(new AbstractListModel<E>() {
                public int getSize() { return listData.length; }
                public E getElementAt(int i) { return listData[i]; }
            });
    }

    public JList(Vector<E> listData) {
        this(new AbstractListModel<E>() {
                public int getSize() { return listData.size(); }
                public E getElementAt(int i) { return listData.elementAt(i); }
            });
    }

    /**
     * The JDK UI's list-data handler, which shifts the selection across the model's inserts and
     * removals: here, on the thread the model fired on, so the selection is right before the
     * peer exists and the moment a worker's {@code remove} returns.
     */
    private final ListDataListener selectionAdjuster = new ListDataListener() {
        @Override
        public void intervalAdded(ListDataEvent e) {
            int minIndex = Math.min(e.getIndex0(), e.getIndex1());
            int maxIndex = Math.max(e.getIndex0(), e.getIndex1());
            ListSelectionModel sm = getSelectionModel();
            if (sm != null) sm.insertIndexInterval(minIndex, maxIndex - minIndex + 1, true);
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            ListSelectionModel sm = getSelectionModel();
            if (sm != null) sm.removeIndexInterval(e.getIndex0(), e.getIndex1());
        }

        @Override
        public void contentsChanged(ListDataEvent e) {
        }
    };

    /** The JDK's null check, run now: the surrogate, whose own check throws NPE, is built only once a UI is current. */
    @SuppressWarnings("rawtypes")
    private static <E> java.util.function.Supplier<SJList> peerFactory(ListModel<E> dataModel) {
        if (dataModel == null) {
            throw new IllegalArgumentException("dataModel must be non null");
        }
        return () -> new SJList<E>(dataModel);
    }

    @SuppressWarnings("unchecked")
    private SJList<E> surrogate() {
        return (SJList<E>) getPeer();
    }

    /**
     * Install the dynamic Vaadin renderer on the surrogate's single column.
     * The closure reads {@link #cellRenderer} live, so
     * {@link #setCellRenderer} only needs to refresh — no re-install.
     */
    private void installEmulatorVaadinRenderer() {
        surrogate().setRenderer(new ComponentRenderer<>(this::renderCellViaEmulator));
    }

    private com.vaadin.flow.component.Component renderCellViaEmulator(Integer rowKey) {
        if (rowKey == null) return new Span("");
        int idx = rowKey;
        ListModel<E> m = getModel();
        if (m == null || idx < 0 || idx >= m.getSize()) return new Span("");
        E value = m.getElementAt(idx);
        if (cellRenderer == null) {
            return new Span(value == null ? "" : String.valueOf(value));
        }
        boolean isSelected = isSelectedIndex(idx);
        vaadinx.awt.Component out = cellRenderer.getListCellRendererComponent(
                this, value, idx, isSelected, false);
        return JComboBox.snapshotRendererOutput(out, value);
    }

    private void installSurrogateBridges() {
        // Mouse re-source — surrogate MOUSE_CLICKED → vaadinx MouseEvent
        //     dispatched via processMouseEvent, with the clicked index held for
        //     locationToIndex.
        surrogate().addMouseListener(new SMouseAdapter() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                // Read inside the surrogate's item-click dispatch, the one place its stash holds the index.
                int clicked = surrogate().locationToIndex(e.getPoint());
                vaadinx.EHelper.callSwing(() -> {
                    int previous = clickIndexStash;
                    clickIndexStash = clicked;
                    try {
                        processMouseEvent(new vaadinx.awt.event.MouseEvent(JList.this,
                                vaadinx.awt.event.MouseEvent.MOUSE_CLICKED,
                                e.getWhen(), e.getModifiersEx(), e.getX(), e.getY(),
                                e.getClickCount(), e.isPopupTrigger(), e.getButton()));
                    } finally {
                        clickIndexStash = previous;
                    }
                });
            }
        });
    }

    /**
     * The clicked index, while a mouse event it caused is being dispatched: what stands in
     * for the L&amp;F's pixel hit test in {@link #locationToIndex} (R_layouts_close_enough).
     * Held for the whole dispatch, so a handler that parks on a modal dialog still sees it
     * after resuming.
     */
    private int clickIndexStash = -1;

    // --- Model accessors ---

    public ListModel<E> getModel() {
        return dataModel;
    }

    public void setModel(ListModel<E> model) {
        if (model == null) {
            throw new IllegalArgumentException("model must be non null");
        }
        ListModel<E> oldValue = dataModel;
        oldValue.removeListDataListener(selectionAdjuster);
        dataModel = model;
        model.addListDataListener(selectionAdjuster);
        firePropertyChange("model", oldValue, dataModel);
        clearSelection();
        withPeer(p -> surrogate().setModel(model));
    }

    public void setListData(final E[] listData) {
        setModel (
            new AbstractListModel<E>() {
                public int getSize() { return listData.length; }
                public E getElementAt(int i) { return listData[i]; }
            }
        );
    }

    public void setListData(final Vector<E> listData) {
        setModel (
            new AbstractListModel<E>() {
                public int getSize() { return listData.size(); }
                public E getElementAt(int i) { return listData.elementAt(i); }
            }
        );
    }

    // --- Cell renderer (JDK shape; bridges to Vaadin via the surrogate) ---

    public vaadinx.swing.ListCellRenderer<? super E> getCellRenderer() {
        return cellRenderer;
    }

    public void setCellRenderer(vaadinx.swing.ListCellRenderer<? super E> cellRenderer) {
        vaadinx.swing.ListCellRenderer<? super E> old = this.cellRenderer;
        this.cellRenderer = cellRenderer;
        // The dynamic Vaadin renderer reads the field live — refresh repaints.
        withPeer(p -> {
            var dp = surrogate().getDataProvider();
            if (dp != null) dp.refreshAll();
        });
        firePropertyChange("cellRenderer", old, cellRenderer);
    }

    // --- Selection model ---

    /** Returns a fresh {@code DefaultListSelectionModel}; a subclass overrides this to install its own. */
    protected ListSelectionModel createSelectionModel() {
        return new DefaultListSelectionModel();
    }

    public ListSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void setSelectionModel(ListSelectionModel selectionModel) {
        if (selectionModel == null) {
            throw new IllegalArgumentException("selectionModel must be non null");
        }

        /* Remove the forwarding ListSelectionListener from the old
         * selectionModel, and add it to the new one, if necessary.
         */
        if (selectionListener != null) {
            this.selectionModel.removeListSelectionListener(selectionListener);
            selectionModel.addListSelectionListener(selectionListener);
        }

        ListSelectionModel oldValue = this.selectionModel;
        this.selectionModel = selectionModel;
        withPeer(p -> surrogate().setSelectionModel(selectionModel));
        firePropertyChange("selectionModel", oldValue, selectionModel);
    }

    public void setSelectionMode(int selectionMode) {
        // The model validates and throws the JDK's IAE; the push then switches the Grid
        // between single and multi select. No PropertyChangeEvent: the model's
        // ListSelectionEvent is the notification (D_property_fanout_audit).
        getSelectionModel().setSelectionMode(selectionMode);
        withPeer(p -> surrogate().setSelectionMode(selectionMode));
    }

    public int getSelectionMode() {
        return getSelectionModel().getSelectionMode();
    }

    // --- Selection API: the JDK's bodies over the selection model ---

    public int getAnchorSelectionIndex() {
        return getSelectionModel().getAnchorSelectionIndex();
    }

    public int getLeadSelectionIndex() {
        return getSelectionModel().getLeadSelectionIndex();
    }

    public int getMinSelectionIndex() {
        return getSelectionModel().getMinSelectionIndex();
    }

    public int getMaxSelectionIndex() {
        return getSelectionModel().getMaxSelectionIndex();
    }

    public boolean isSelectedIndex(int index) {
        return getSelectionModel().isSelectedIndex(index);
    }

    public boolean isSelectionEmpty() {
        return getSelectionModel().isSelectionEmpty();
    }

    public void clearSelection() {
        getSelectionModel().clearSelection();
    }

    public void setSelectionInterval(int anchor, int lead) {
        getSelectionModel().setSelectionInterval(anchor, lead);
    }

    public void addSelectionInterval(int anchor, int lead) {
        getSelectionModel().addSelectionInterval(anchor, lead);
    }

    public void removeSelectionInterval(int index0, int index1) {
        getSelectionModel().removeSelectionInterval(index0, index1);
    }

    public void setValueIsAdjusting(boolean b) {
        getSelectionModel().setValueIsAdjusting(b);
    }

    public boolean getValueIsAdjusting() {
        return getSelectionModel().getValueIsAdjusting();
    }

    public int[] getSelectedIndices() {
        return getSelectionModel().getSelectedIndices();
    }

    /** An index below 0 does not clear the selection: {@code DefaultListSelectionModel} ignores it, as in the JDK. */
    public void setSelectedIndex(int index) {
        if (index >= getModel().getSize()) {
            return;
        }
        getSelectionModel().setSelectionInterval(index, index);
    }

    public void setSelectedIndices(int[] indices) {
        ListSelectionModel sm = getSelectionModel();
        sm.clearSelection();
        int size = getModel().getSize();
        for (int i : indices) {
            if (i < size) {
                sm.addSelectionInterval(i, i);
            }
        }
    }

    public java.util.List<E> getSelectedValuesList() {
        ListModel<E> dm = getModel();
        int[] selectedIndices = getSelectedIndices();

        if (selectedIndices.length > 0) {
            int size = dm.getSize();
            if (selectedIndices[0] >= size) {
                return java.util.Collections.emptyList();
            }
            java.util.List<E> selectedItems = new java.util.ArrayList<E>();
            for (int i : selectedIndices) {
                if (i >= size)
                    break;
                selectedItems.add(dm.getElementAt(i));
            }
            return selectedItems;
        }
        return java.util.Collections.emptyList();
    }

    public int getSelectedIndex() {
        return getMinSelectionIndex();
    }

    public E getSelectedValue() {
        int i = getMinSelectionIndex();
        return ((i == -1) || (i >= getModel().getSize())) ? null :
                getModel().getElementAt(i);
    }

    /**
     * A value that is already selected, or that the model does not hold, leaves the
     * selection as it is: the JDK's fallback {@code setSelectedIndex(-1)} is ignored by the
     * selection model.
     */
    public void setSelectedValue(Object anObject,boolean shouldScroll) {
        if(anObject == null)
            clearSelection();
        else if(!anObject.equals(getSelectedValue())) {
            int i,c;
            ListModel<E> dm = getModel();
            for(i=0,c=dm.getSize();i<c;i++)
                if(anObject.equals(dm.getElementAt(i))){
                    setSelectedIndex(i);
                    if(shouldScroll)
                        ensureIndexIsVisible(i);
                    return;
                }
            setSelectedIndex(-1);
        }
    }

    /** Scrolls the Grid to the row; the JDK's {@code getCellBounds} route has no server-side geometry here. */
    public void ensureIndexIsVisible(int index) { withPeer(p -> surrogate().ensureIndexIsVisible(index)); }

    // --- ListSelectionListener: the JDK's ListSelectionHandler re-source ---

    public void addListSelectionListener(ListSelectionListener listener) {
        if (selectionListener == null) {
            selectionListener = e -> vaadinx.EHelper.relayModelEvent(() -> fireSelectionValueChanged(
                    e.getFirstIndex(), e.getLastIndex(), e.getValueIsAdjusting()));
            getSelectionModel().addListSelectionListener(selectionListener);
        }

        listenerList.add(ListSelectionListener.class, listener);
    }

    public void removeListSelectionListener(ListSelectionListener listener) {
        listenerList.remove(ListSelectionListener.class, listener);
    }

    public ListSelectionListener[] getListSelectionListeners() {
        return listenerList.getListeners(ListSelectionListener.class);
    }

    protected void fireSelectionValueChanged(int firstIndex, int lastIndex,
                                             boolean isAdjusting)
    {
        Object[] listeners = listenerList.getListenerList();
        ListSelectionEvent e = null;

        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ListSelectionListener.class) {
                if (e == null) {
                    e = new ListSelectionEvent(this, firstIndex, lastIndex,
                                               isAdjusting);
                }
                ((ListSelectionListener)listeners[i+1]).valueChanged(e);
            }
        }
    }

    // --- Hit testing (R_layouts_close_enough: no server-side cell geometry) ---

    /**
     * The clicked row during a mouse handler's dispatch, {@code -1} otherwise, whatever the
     * coordinates: there is no server-side pixel→cell map (R_layouts_close_enough).
     *
     * <p>As {@code BasicListUI}'s, a null location throws and the answer is the closest
     * cell: a list shrunk while the handler was parked on a dialog answers its last row.
     */
    public int locationToIndex(Point location) {
        Objects.requireNonNull(location, "location");
        if (clickIndexStash < 0) {
            return -1;
        }
        int size = getModel().getSize();
        return size == 0 ? -1 : Math.min(clickIndexStash, size - 1);
    }

    public Point indexToLocation(int index) {
        return null;
    }

    public Rectangle getCellBounds(int index0, int index1) {
        return null;
    }

    // --- No-Vaadin-counterpart knobs: emulator-only R_swing_is_truth field shadows ---
    //
    // Grid virtualises / auto-sizes / theme owns chrome, so these have no
    // peer property. They round-trip on the emulator (JDK-faithful, R_swing_is_truth) +
    // fire PCE; the surrogate drops them per R_vaadin_first (SD_sjlist). Asymmetric R_swing_is_truth-shadow
    // pattern per SJToolBar (D_jtoolbar) / JSplitPane (D_jsplitpane). `dragEnabled(true)`
    // and wrap orientations WARN (real feature gaps); the rest stay silent
    // (R_layouts_close_enough sizing/chrome — the value round-trips but isn't visually honored).

    private int layoutOrientation = VERTICAL;
    private int visibleRowCount = 8;
    private int fixedCellWidth = -1;
    private int fixedCellHeight = -1;
    private Color selectionForeground;
    private Color selectionBackground;
    private boolean dragEnabled;

    public int getLayoutOrientation() { return layoutOrientation; }
    public void setLayoutOrientation(int layoutOrientation) {
        switch (layoutOrientation) {
            case VERTICAL -> { /* natively rendered */ }
            case VERTICAL_WRAP, HORIZONTAL_WRAP ->
                    // Round-trips per R_swing_is_truth, but Vaadin renders single-column only.
                    vaadinx.EHelper.onUnimplemented("JList", "setLayoutOrientation/wrap", layoutOrientation);
            default -> throw new IllegalArgumentException(
                    "layoutOrientation must be one of: VERTICAL, HORIZONTAL_WRAP or VERTICAL_WRAP");
        }
        int old = this.layoutOrientation;
        this.layoutOrientation = layoutOrientation;
        if (old != layoutOrientation) firePropertyChange("layoutOrientation", old, layoutOrientation);
    }

    public int getVisibleRowCount() { return visibleRowCount; }
    public void setVisibleRowCount(int visibleRowCount) {
        int v = Math.max(0, visibleRowCount);
        int old = this.visibleRowCount;
        if (old == v) return;
        this.visibleRowCount = v;
        firePropertyChange("visibleRowCount", old, v);
    }

    public int getFixedCellWidth() { return fixedCellWidth; }
    public void setFixedCellWidth(int width) {
        int old = fixedCellWidth;
        if (old == width) return;
        fixedCellWidth = width;
        firePropertyChange("fixedCellWidth", old, width);
    }

    public int getFixedCellHeight() { return fixedCellHeight; }
    public void setFixedCellHeight(int height) {
        int old = fixedCellHeight;
        if (old == height) return;
        fixedCellHeight = height;
        firePropertyChange("fixedCellHeight", old, height);
    }

    public E getPrototypeCellValue() { return prototypeCellValue; }
    /**
     * Fires {@code "prototypeCellValue"} on this emulator in addition to
     * delegating. The surrogate fires on <em>its own</em> listener list, which
     * a migrator's {@code addPropertyChangeListener} on the JList never reaches
     * — the two-layer fan-out is independent by design (D_owed_events).
     */
    public void setPrototypeCellValue(E prototypeCellValue) {
        E old = this.prototypeCellValue;
        this.prototypeCellValue = prototypeCellValue;
        withPeer(p -> surrogate().setPrototypeCellValue(prototypeCellValue));
        firePropertyChange("prototypeCellValue", old, prototypeCellValue);
    }

    /**
     * The prototype cell value, held here rather than read back off the surrogate.
     *
     * <p>The surrogate drops it under R_vaadin_first — cell width comes from CSS, so there
     * is no Vaadin property to write — and the emulator layer owes the opposite duty under
     * R_decline_effect_only: the sizing effect may go, the state may not. Delegating the
     * getter made the emulator inherit the surrogate's stance across the layer boundary the
     * two rules deliberately disagree on.
     */
    private E prototypeCellValue;

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

    public boolean getDragEnabled() { return dragEnabled; }
    public void setDragEnabled(boolean b) {
        // D_drag_and_drop: graduated from drop-and-WARN. dragEnabled is the JDK-faithful
        // round-trip flag (R_swing_is_truth); flipping it (re)wires the Grid-row drag source
        // through DndBridge, which reads this flag back.
        // No PropertyChangeEvent: JList.setDragEnabled headless-checks and
        // assigns; dragEnabled is not a bound property (D_property_fanout_audit).
        if (dragEnabled == b) return;
        dragEnabled = b;
        vaadinx.swing.DndBridge.reconfigure(this);
    }

    /** Drop mode (D_drag_and_drop). Default {@code USE_SELECTION} matches JDK JList. */
    private javax.swing.DropMode dropMode = javax.swing.DropMode.USE_SELECTION;

    public final javax.swing.DropMode getDropMode() { return dropMode; }

    public final void setDropMode(javax.swing.DropMode dropMode) {
        // JDK JList supports USE_SELECTION / ON / INSERT / ON_OR_INSERT and rejects the
        // four ROWS/COLS modes (measured on JDK 25); same exception type per
        // R_match_swing_errors.
        if (dropMode != null) {
            switch (dropMode) {
                case USE_SELECTION, ON, INSERT, ON_OR_INSERT -> { }
                default -> throw new IllegalArgumentException(
                        dropMode + ": Unsupported drop mode for list");
            }
        }
        this.dropMode = dropMode;
        vaadinx.swing.DndBridge.reconfigure(this);
    }

    /**
     * Drop location for a JList drop (D_drag_and_drop — emulator for
     * {@code javax.swing.JList.DropLocation}). {@link #getIndex()} is the model
     * index; {@link #isInsert()} distinguishes an insert-between
     * ({@code DropMode.INSERT}) from a drop-on ({@code DropMode.ON}). The pixel
     * drop point is a dummy (R_layouts_close_enough — no server-side coordinates).
     */
    public static final class DropLocation extends vaadinx.swing.TransferHandler.DropLocation {
        private final int index;
        private final boolean isInsert;

        DropLocation(Point p, int index, boolean isInsert) {
            super(p);
            this.index = index;
            this.isInsert = isInsert;
        }

        public int getIndex() { return index; }
        public boolean isInsert() { return isInsert; }
    }

    // --- Visible-index hints (R_layouts_close_enough — no server-side viewport metrics) ---

    public int getFirstVisibleIndex() {
        return getModel().getSize() == 0 ? -1 : 0;
    }

    public int getLastVisibleIndex() {
        return getModel().getSize() - 1;
    }

    // --- Scrollable (JDK interface; R_layouts_close_enough close-enough) ---

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        int w = getFixedCellWidth() > 0 ? getFixedCellWidth() : 256;
        int cellH = getFixedCellHeight() > 0 ? getFixedCellHeight() : 16;
        return new Dimension(w, cellH * Math.max(1, getVisibleRowCount()));
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
                ? (getFixedCellHeight() > 0 ? getFixedCellHeight() : 16)
                : 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getLayoutOrientation() == VERTICAL;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // --- L&F stubs ---

    public String getUIClassID() {
        return "ListUI";
    }

    public void updateUI() {
        // L&F swap — no-op. Same shape as JTable / JComboBox.
    }

    public javax.swing.plaf.ListUI getUI() {
        return null;
    }

    public void setUI(javax.swing.plaf.ListUI ui) {
        if (ui != null) vaadinx.EHelper.onUnimplemented("JList", "setUI", ui);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JList", "getAccessibleContext");
        return null;
    }
}
