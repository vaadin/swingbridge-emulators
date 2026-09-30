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

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.internal.ComboBoxStateStore;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.MutableComboBoxModel;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.ItemSelectable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Vector;

/**
 * Surrogate for {@link javax.swing.JComboBox} (SD_sjcombobox). Extends Vaadin
 * {@link ComboBox} and reproduces the Swing JComboBox UI functionality:
 * model-driven items + selection, {@link ItemListener} pair fan-out on
 * selection commit, {@link ActionListener} fan-out on commit,
 * {@link PopupMenuListener} fan-out on every open and close, the user's included
 * ({@link #addOpenedChangeListener}),
 * editable mode wired to Vaadin's {@code allowCustomValue}, and
 * {@code maximumRowCount} mirroring Vaadin's {@code pageSize}.
 *
 * <h2>Why ComboBox, not a leaf primitive</h2>
 *
 * Vaadin {@link ComboBox} is the only native dropdown selector that
 * supports filterable item lists, custom values (typed input commit),
 * and a configurable popup. It cleanly carries JDK JComboBox's contract
 * for both editable and non-editable modes. Vaadin's {@code Select<T>}
 * was rejected: it doesn't allow custom values, has no filter UI, and
 * its popup is not page-sized.
 *
 * <h2>Source of truth</h2>
 *
 * The installed {@link ComboBoxModel} is the source of truth for items
 * and selected item (R_swing_is_truth) — JDK reuses {@link DefaultComboBoxModel}, so
 * migrated user code that constructs / manipulates a model directly
 * keeps working unchanged. Our {@link ListDataListener} on the model
 * snapshots items into the peer via {@link ComboBox#setItems(java.util.Collection)}
 * on every list change (Vaadin offers no incremental add API), and
 * pushes the model's current selection through {@link ComboBox#setValue}.
 * Browser-originated edits funnel back through an explicit
 * {@code addValueChangeListener} subscription wrapped in
 * {@link SHelper#callSwing} (R_callswing_envelope) and write through
 * {@link ComboBoxModel#setSelectedItem(Object)}, which fires the model's
 * {@link ListDataEvent#CONTENTS_CHANGED} and closes the loop. The
 * standard {@code preventPeerEvents} flag (R_swing_is_truth) guards against
 * peer→model→peer re-entry.
 *
 * <h2>ItemEvent fan-out</h2>
 *
 * On every selection commit the surrogate fans out the JDK-canonical
 * pair: {@link ItemEvent#DESELECTED} on the previously-selected item
 * (when non-null) followed by {@link ItemEvent#SELECTED} on the
 * newly-selected item (when non-null). The {@link ActionEvent} fires
 * after the pair, matching JDK JComboBox's commit sequencing.
 * {@link ComboBoxStateStore#lastSelected} is the rolling cursor.
 *
 * <h2>Editable mode</h2>
 *
 * {@link #setEditable(boolean)} maps to Vaadin's
 * {@link ComboBox#setAllowCustomValue(boolean)}. When enabled, a
 * {@code customValueSetListener} subscription commits the typed string
 * back into the model via {@code model.setSelectedItem(typedString)} —
 * the model's {@link ListDataEvent} then drives the usual fan-out. The
 * full {@link javax.swing.ComboBoxEditor} surface (custom editor
 * components, {@code selectAll}, separate editor ActionListener) is R_vaadin_first
 * drop-and-WARN — Vaadin owns the typed-input UX inside the combobox
 * and there's no semantic-cleanly-mappable counterpart for a custom
 * editor component.
 *
 * <h2>Renderer</h2>
 *
 * Vaadin-first per R_vaadin_first. Users at the surrogate layer install Vaadin
 * {@link com.vaadin.flow.data.renderer.Renderer} instances directly via
 * {@link ComboBox#setRenderer} (or {@code setItemLabelGenerator} for
 * label-only customisation). The JDK {@link javax.swing.ListCellRenderer}
 * surface lives only on the {@code :emulators.JComboBox} layer and
 * bridges into a Vaadin renderer at the boundary — surrogate-layer code
 * sees Vaadin idioms only. The default rendering uses Vaadin's built-in
 * {@code String.valueOf} item-label generator (matching JDK's "model
 * value via toString" default), so a fresh SJComboBox renders sanely
 * with zero configuration.
 *
 * <h2>R_vaadin_first drop-and-WARN surface</h2>
 *
 * <ul>
 *   <li>{@link javax.swing.ComboBoxEditor} (full editor port).</li>
 *   <li>{@code KeySelectionManager} (browser handles type-to-search).</li>
 *   <li>{@code lightWeightPopupEnabled} (Swing-internal popup chrome).</li>
 *   <li>{@code prototypeDisplayValue} (sizing hint).</li>
 *   <li>{@code selectWithKeyChar} (subsumed by browser type-to-search).</li>
 * </ul>
 *
 * <p>UI-thread-confined, with one exception: the installed {@link ComboBoxModel} may be
 * mutated from any thread. A worker filling it hops onto this combo's UI thread for each
 * model event and blocks until the fan-out has run, so every listener here — and the peer
 * write — runs on the UI thread; while the combo is detached, inline on the worker
 * (SD_background_model_hop):
 *
 * <pre>{@code
 * DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
 * layout.add(new SJComboBox<>(model));
 * executor.submit(() -> repository.findAllNames().forEach(model::addElement));
 * }</pre>
 */
public class SJComboBox<T> extends ComboBox<T> implements JComponentMixin, ItemSelectable {

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

    // ---- Constructors ----

    public SJComboBox() {
        this(new DefaultComboBoxModel<>());
    }

    @SuppressWarnings("unchecked")
    public SJComboBox(ComboBoxModel<T> model) {
        if (model == null) {
            throw new NullPointerException("model can't be null");
        }
        _installSwingClass();
        ComboBoxStateStore store = ComboBoxStateStore.of(this);
        store.lastSelected = model.getSelectedItem();
        installModel((ComboBoxModel<Object>) (ComboBoxModel<?>) model);
        installValueChangeBridge();
        installOpenedBridge();
        // Default rendering: Vaadin's setItemLabelGenerator with toString
        // matches JDK JComboBox's "model value via toString" default.
        setItemLabelGenerator(item -> item == null ? "" : String.valueOf(item));
    }

    public SJComboBox(T[] items) {
        this(new DefaultComboBoxModel<>(items));
    }

    public SJComboBox(Vector<T> items) {
        this(new DefaultComboBoxModel<>(items));
    }

    private ComboBoxStateStore store() {
        return ComboBoxStateStore.of(this);
    }

    @SuppressWarnings("unchecked")
    private ComboBoxModel<Object> model() {
        return (ComboBoxModel<Object>) store().model;
    }

    // ---- Model plumbing ----

    /**
     * Attach the supplied model, install our {@link ListDataListener},
     * push current items + selection to the peer, and seed the
     * {@link ComboBoxStateStore#lastSelected} cursor. Called from the
     * ctor and from {@link #setModel}.
     */
    private void installModel(ComboBoxModel<Object> newModel) {
        ComboBoxStateStore store = store();
        if (store.model != null && store.modelListener != null) {
            store.model.removeListDataListener(store.modelListener);
        }
        store.model = newModel;
        if (store.modelListener == null) {
            store.modelListener = new ListDataListener() {
                @Override public void intervalAdded(ListDataEvent e) { onModelEventOnUI(e); }
                @Override public void intervalRemoved(ListDataEvent e) { onModelEventOnUI(e); }
                @Override public void contentsChanged(ListDataEvent e) { onModelEventOnUI(e); }
            };
        }
        newModel.addListDataListener(store.modelListener);
        pushItemsToPeer();
        pushSelectionToPeer();
    }

    /**
     * Snapshot the model's items into the peer. Called on every
     * {@link ListDataEvent} since Vaadin's {@code ComboBox} has no
     * incremental add API; the whole list rebuilds. Idempotent for
     * Vaadin (it diffs internally), but still O(n) per change.
     */
    @SuppressWarnings("unchecked")
    private void pushItemsToPeer() {
        ComboBoxStateStore store = store();
        ComboBoxModel<Object> m = model();
        List<T> snapshot = new ArrayList<>(m.getSize());
        for (int i = 0; i < m.getSize(); i++) {
            snapshot.add((T) m.getElementAt(i));
        }
        store.preventPeerEvents = true;
        try {
            setItems(snapshot);
        } finally {
            store.preventPeerEvents = false;
        }
    }

    /**
     * Push the model's current selection through to Vaadin's
     * {@code setValue}. Guarded by {@code preventPeerEvents} so the
     * peer's resulting ValueChangeEvent doesn't re-enter our
     * write-through path.
     */
    @SuppressWarnings("unchecked")
    private void pushSelectionToPeer() {
        ComboBoxStateStore store = store();
        Object sel = model().getSelectedItem();
        store.preventPeerEvents = true;
        try {
            super.setValue((T) sel);
        } finally {
            store.preventPeerEvents = false;
        }
    }

    /** The model may be mutated off the UI thread (SD_background_model_hop). */
    private void onModelEventOnUI(ListDataEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: ComboBoxModel ListDataListener
        SHelper.runOnOwnerUI(this, () -> onModelEvent(e));
    }

    /**
     * Single fan-out path for all three {@link ListDataListener}
     * callbacks. JDK fires {@code CONTENTS_CHANGED} with index −1 for
     * selection changes and with non-negative indices for item
     * mutations; we treat the index −1 case as a selection-only commit
     * and the other cases as items-changed. Both paths fire the
     * surrogate's own ListDataListener fan-out (matching
     * {@link javax.swing.JComboBox}'s {@code listDataListener}-as-pass-through
     * idiom for migrated MVC code that listens to the combo's events
     * rather than the model's).
     */
    private void onModelEvent(ListDataEvent e) {
        boolean selectionOnly =
                e.getType() == ListDataEvent.CONTENTS_CHANGED
                        && e.getIndex0() == -1 && e.getIndex1() == -1;
        if (selectionOnly) {
            commitSelection();
        } else {
            // Items changed; refresh the peer's full list and re-push
            // selection (the new list may not contain the selected
            // item — Vaadin handles that by clearing the peer-side
            // value automatically, which is fine; the model still
            // holds the user's prior selection).
            pushItemsToPeer();
            pushSelectionToPeer();
        }
        // Fire the surrogate's own ListDataListener fan-out — matches
        // JDK JComboBox forwarding model events to its own list-data
        // listener registration.
        fireListDataEvent(e);
    }

    /**
     * Commit a selection change: update {@code lastSelected}, push to
     * the peer, fire the {@link ItemEvent} pair (DESELECTED-prev +
     * SELECTED-new), then fire the {@link ActionEvent}.
     *
     * <p>Re-entrancy: when this is called from the
     * {@link ListDataListener} (i.e. user code or our own
     * {@code setSelectedItem} path drove the change), we still need to
     * push to the peer — the model fired but the Vaadin combo box
     * hasn't received the new value yet. When this is called from the
     * peer's {@link AbstractField.ComponentValueChangeEvent} handler,
     * {@code preventPeerEvents} is already set so the
     * {@link #pushSelectionToPeer} call is a guard-bailed no-op.
     */
    private void commitSelection() {
        ComboBoxStateStore store = store();
        Object prev = store.lastSelected;
        Object next = model().getSelectedItem();
        if (Objects.equals(prev, next)) return;
        store.lastSelected = next;
        pushSelectionToPeer();
        fireItemPair(prev, next);
        fireActionEvent();
    }

    // ---- Peer ValueChange bridge (browser → model) ----

    private void installValueChangeBridge() {
        ComboBoxStateStore store = store();
        store.valueChangeRegistration = addValueChangeListener(e ->
                SHelper.callSwing(() -> syncSelectionFromPeer(e.getValue())));
    }

    /**
     * Handler for the peer's {@code ValueChangeEvent}. Writes the new
     * value through the model, which fires {@link ListDataEvent} and
     * closes the loop via {@link #onModelEvent}. The
     * {@code preventPeerEvents} guard during model writes is set inside
     * {@link #commitSelection}'s {@link #pushSelectionToPeer} call, so
     * the resulting peer set is suppressed and the model's view of the
     * world stays single-sourced.
     */
    private void syncSelectionFromPeer(Object peerValue) {
        ComboBoxStateStore store = store();
        if (store.preventPeerEvents) return;
        if (Objects.equals(peerValue, model().getSelectedItem())) return;
        // Set on the model — its CONTENTS_CHANGED listener drives the
        // fan-out, including the peer push. Avoids double-source.
        store.preventPeerEvents = true;
        try {
            model().setSelectedItem(peerValue);
        } finally {
            store.preventPeerEvents = false;
        }
        // The model's ListDataListener fan-out drives commitSelection.
    }

    // ---- Selection API ----

    /**
     * Select an item, rejecting one the model does not contain unless the combo
     * box is editable, then route through the model so its ListDataEvent fan-out
     * drives the ItemEvent pair and the ActionEvent.
     *
     * <p>The rejection is {@code javax.swing.JComboBox.setSelectedItem}'s own —
     * <em>not</em> the model's, which is where SB-Emulators looked for it and did
     * not find it: {@link DefaultComboBoxModel} does permit any value, so routing
     * straight to the model left {@code getSelectedItem()} answering an item that
     * was never in the list, where a real non-editable JComboBox answers
     * {@code null} (D_return_value_audit). A {@code null} argument bypasses the
     * check, as it does in the JDK.
     *
     * <p>On a match, the <em>model's</em> element is selected rather than
     * the argument — the JDK substitutes it deliberately, so an
     * {@code equals}-but-not-{@code ==} argument does not displace the
     * instance the model holds.
     */
    public void setSelectedItem(Object anObject) {
        Object objectToSelect = anObject;
        ComboBoxModel<Object> m = model();
        if (anObject != null && !isEditable()) {
            boolean found = false;
            for (int i = 0, n = m.getSize(); i < n; i++) {
                Object element = m.getElementAt(i);
                if (anObject.equals(element)) {
                    found = true;
                    objectToSelect = element;
                    break;
                }
            }
            if (!found) return;
        }
        m.setSelectedItem(objectToSelect);
    }

    public Object getSelectedItem() {
        return model().getSelectedItem();
    }

    public int getSelectedIndex() {
        ComboBoxModel<Object> m = model();
        Object sel = m.getSelectedItem();
        if (sel == null) return -1;
        for (int i = 0, n = m.getSize(); i < n; i++) {
            if (Objects.equals(sel, m.getElementAt(i))) return i;
        }
        return -1;
    }

    public void setSelectedIndex(int anIndex) {
        ComboBoxModel<Object> m = model();
        int size = m.getSize();
        if (anIndex < -1 || anIndex >= size) {
            throw new IllegalArgumentException("setSelectedIndex: " + anIndex + " out of bounds");
        }
        m.setSelectedItem(anIndex == -1 ? null : m.getElementAt(anIndex));
    }

    /**
     * {@link java.awt.ItemSelectable#getSelectedObjects()} contract:
     * returns a single-element array when an item is selected, an
     * empty array when none. JDK uses {@code Object[]} (not {@code T[]})
     * because the interface predates generics — preserved verbatim.
     */
    public Object[] getSelectedObjects() {
        Object sel = getSelectedItem();
        return sel == null ? new Object[0] : new Object[]{sel};
    }

    @Override
    public void setValue(T value) {
        // Two windows where this MUST fall through to super and skip the
        // model write:
        //
        //   (a) Construction window. Vaadin's ComboBox super-ctor calls
        //       setItems which itself calls setValue(null) before our
        //       ctor body has installed the model. store.model is null
        //       — fall back so super's internal bookkeeping completes.
        //
        //   (b) Inside a model→peer push. When pushItemsToPeer or
        //       pushSelectionToPeer runs, preventPeerEvents is true and
        //       Vaadin's setItems internally calls setValue(null) on
        //       this. Routing that through model.setSelectedItem would
        //       corrupt the model's selection mid-push and double-fire
        //       ListDataListener forwarders.
        //
        // Outside both windows, route through the model so HasValue.setValue
        // and the JDK setSelectedItem path converge — keeps ItemListener /
        // ActionListener fan-out consistent regardless of which API was
        // called. Same shape as SJSpinner.setValue per SD_sjspinner.
        ComboBoxStateStore store = store();
        if (store.model == null || store.preventPeerEvents) {
            super.setValue(value);
            return;
        }
        setSelectedItem(value);
    }

    // ---- Items mutation (delegate to MutableComboBoxModel where applicable) ----

    /**
     * Add an item via the model. Throws {@link IllegalStateException}
     * if the installed model isn't a {@link MutableComboBoxModel} —
     * matches JDK JComboBox's "you must use a MutableComboBoxModel"
     * runtime check (R_match_swing_errors).
     */
    public void addItem(T item) {
        ComboBoxModel<Object> m = model();
        if (!(m instanceof MutableComboBoxModel<?>)) {
            throw new IllegalStateException(
                    "Cannot add an item to a non-Mutable ComboBoxModel: " + m.getClass().getName());
        }
        ((MutableComboBoxModel<Object>) m).addElement(item);
    }

    public void insertItemAt(T item, int index) {
        ComboBoxModel<Object> m = model();
        if (!(m instanceof MutableComboBoxModel<?>)) {
            throw new IllegalStateException(
                    "Cannot insert an item into a non-Mutable ComboBoxModel: " + m.getClass().getName());
        }
        ((MutableComboBoxModel<Object>) m).insertElementAt(item, index);
    }

    public void removeItem(Object anObject) {
        ComboBoxModel<Object> m = model();
        if (!(m instanceof MutableComboBoxModel<?>)) {
            throw new IllegalStateException(
                    "Cannot remove an item from a non-Mutable ComboBoxModel: " + m.getClass().getName());
        }
        ((MutableComboBoxModel<Object>) m).removeElement(anObject);
    }

    public void removeItemAt(int anIndex) {
        ComboBoxModel<Object> m = model();
        if (!(m instanceof MutableComboBoxModel<?>)) {
            throw new IllegalStateException(
                    "Cannot remove an item from a non-Mutable ComboBoxModel: " + m.getClass().getName());
        }
        ((MutableComboBoxModel<Object>) m).removeElementAt(anIndex);
    }

    public void removeAllItems() {
        ComboBoxModel<Object> m = model();
        if (m instanceof DefaultComboBoxModel<?> dcm) {
            // Fast path — JDK's own implementation calls this directly.
            dcm.removeAllElements();
            return;
        }
        if (!(m instanceof MutableComboBoxModel<?>)) {
            throw new IllegalStateException(
                    "Cannot clear a non-Mutable ComboBoxModel: " + m.getClass().getName());
        }
        MutableComboBoxModel<Object> mcm = (MutableComboBoxModel<Object>) m;
        // Iterate from the tail to avoid index shifting under removal.
        for (int i = mcm.getSize() - 1; i >= 0; i--) {
            mcm.removeElementAt(i);
        }
    }

    @SuppressWarnings("unchecked")
    public T getItemAt(int index) {
        ComboBoxModel<Object> m = model();
        if (index < 0 || index >= m.getSize()) return null;
        return (T) m.getElementAt(index);
    }

    public int getItemCount() {
        return model().getSize();
    }

    // ---- Model accessors ----

    @SuppressWarnings("unchecked")
    public ComboBoxModel<T> getModel() {
        return (ComboBoxModel<T>) store().model;
    }

    @SuppressWarnings("unchecked")
    public void setModel(ComboBoxModel<T> newModel) {
        if (newModel == null) {
            throw new NullPointerException("model can't be null");
        }
        ComboBoxModel<?> old = store().model;
        if (old == newModel) return;
        ComboBoxStateStore store = store();
        store.lastSelected = newModel.getSelectedItem();
        installModel((ComboBoxModel<Object>) (ComboBoxModel<?>) newModel);
        // JDK fires "model" PCE so MVC wiring stays in sync.
        firePropertyChange("model", old, newModel);
    }

    // ---- Editable mode ----

    public boolean isEditable() {
        return store().editable;
    }

    public void setEditable(boolean aFlag) {
        ComboBoxStateStore store = store();
        if (store.editable == aFlag) return;
        store.editable = aFlag;
        setAllowCustomValue(aFlag);
        if (aFlag) {
            store.customValueRegistration = addCustomValueSetListener(e ->
                    SHelper.callSwing(() -> commitCustomValue(e.getDetail())));
        } else if (store.customValueRegistration != null) {
            store.customValueRegistration.remove();
            store.customValueRegistration = null;
        }
        firePropertyChange("editable", !aFlag, aFlag);
    }

    /**
     * Commit a typed-but-unmatched user input. We funnel it through the
     * model as the new selected item — matches JDK JComboBox's editable
     * mode where the editor's typed value becomes the selected item on
     * Enter even when it doesn't appear in the model. Unlike JDK we
     * don't add the typed value to the model's item list automatically;
     * migrated code that wants insert-on-commit can listen for
     * ActionEvent and add to the model itself.
     */
    private void commitCustomValue(String detail) {
        ComboBoxStateStore store = store();
        if (store.preventPeerEvents) return;
        // Coerce to T — only safe when T is String (the typical editable
        // ComboBox<String> case). For non-String T the cast WARNs and
        // bails since we can't parse arbitrary types from a string here.
        try {
            model().setSelectedItem((Object) detail);
        } catch (ClassCastException cce) {
            SHelper.onUnimplemented(this, "commitCustomValue/non-String-T", detail);
        }
    }

    // ---- Popup / page size ----

    public int getMaximumRowCount() {
        return store().maximumRowCount;
    }

    public void setMaximumRowCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("maximumRowCount must be >= 1");
        }
        ComboBoxStateStore store = store();
        int old = store.maximumRowCount;
        if (old == count) return;
        store.maximumRowCount = count;
        setPageSize(count);
        firePropertyChange("maximumRowCount", old, count);
    }

    public boolean isPopupVisible() {
        return isOpened();
    }

    /** {@link #setOpened}; the {@link PopupMenuListener}s hear it through {@link #addOpenedChangeListener}, as they hear a user's click. */
    public void setPopupVisible(boolean v) {
        setOpened(v);
    }

    public void showPopup() {
        setPopupVisible(true);
    }

    public void hidePopup() {
        setPopupVisible(false);
    }

    /**
     * Listens for the dropdown opening or closing, whichever side did it: the
     * {@code opened-changed} event {@code DatePicker} and {@code Select} expose and
     * {@link ComboBox} lacks.
     *
     * <pre>{@code
     * combo.addOpenedChangeListener(e -> {
     *     if (e.isOpened() && e.isFromClient()) reloadItems();
     * });
     * }</pre>
     */
    public Registration addOpenedChangeListener(ComponentEventListener<OpenedChangeEvent> listener) {
        return addListener(OpenedChangeEvent.class, listener);
    }

    /** Fired when {@link #isOpened()} changes; {@link #isFromClient()} is {@code true} when the user opened or closed it. */
    public static class OpenedChangeEvent extends ComponentEvent<SJComboBox<?>> {
        private final boolean opened;

        public OpenedChangeEvent(SJComboBox<?> source, boolean fromClient) {
            super(source, fromClient);
            this.opened = source.isOpened();
        }

        /** The state the dropdown changed to, captured when fired. */
        public boolean isOpened() {
            return opened;
        }
    }

    /**
     * Fires {@link OpenedChangeEvent} off the browser-synchronized {@code opened}
     * property, and drives the {@link PopupMenuListener} fan-out from it.
     */
    private void installOpenedBridge() {
        getElement().addPropertyChangeListener("opened", e -> {
            // Flow reports a first setOpened(false), absent → false, as a change.
            if (Boolean.TRUE.equals(e.getOldValue()) == isOpened()) return;
            fireEvent(new OpenedChangeEvent(this, e.isUserOriginated()));
        });
        addOpenedChangeListener(e -> SHelper.callSwing(() -> {
            if (e.isOpened()) firePopupMenuWillBecomeVisible();
            else firePopupMenuWillBecomeInvisible();
        }));
    }

    // ---- ItemListener fan-out ----

    public void addItemListener(ItemListener l) {
        store().listenerList.add(ItemListener.class, l);
    }

    public void removeItemListener(ItemListener l) {
        store().listenerList.remove(ItemListener.class, l);
    }

    public ItemListener[] getItemListeners() {
        return store().listenerList.getListeners(ItemListener.class);
    }

    /**
     * Fire the JDK-canonical {@link ItemEvent#DESELECTED} on
     * {@code prev} (when non-null) followed by {@link ItemEvent#SELECTED}
     * on {@code next} (when non-null). Source rebound to {@code this}
     * so migrated code casting {@code (JComboBox) e.getSource()} sees
     * the surrogate. {@code stateChange} reflects what the listener
     * receives; SELECTED == 1, DESELECTED == 2.
     */
    private void fireItemPair(Object prev, Object next) {
        EventListenerList ll = store().listenerList;
        ItemListener[] listeners = ll.getListeners(ItemListener.class);
        if (listeners.length == 0) return;
        if (prev != null) {
            ItemEvent e = new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, prev, ItemEvent.DESELECTED);
            for (ItemListener l : listeners) l.itemStateChanged(e);
        }
        if (next != null) {
            ItemEvent e = new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED, next, ItemEvent.SELECTED);
            for (ItemListener l : listeners) l.itemStateChanged(e);
        }
    }

    // ---- ActionListener fan-out ----

    public void addActionListener(ActionListener l) {
        store().listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        store().listenerList.remove(ActionListener.class, l);
    }

    public ActionListener[] getActionListeners() {
        return store().listenerList.getListeners(ActionListener.class);
    }

    public String getActionCommand() {
        return store().actionCommand;
    }

    public void setActionCommand(String aCommand) {
        store().actionCommand = aCommand;
    }

    /**
     * Fire {@link ActionEvent} to all registered ActionListeners.
     * Source = this; command = current actionCommand;
     * timestamp = now; modifiers = 0 (no input device).
     */
    protected void fireActionEvent() {
        ActionListener[] listeners = store().listenerList.getListeners(ActionListener.class);
        if (listeners.length == 0) return;
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                store().actionCommand, System.currentTimeMillis(), 0);
        for (ActionListener l : listeners) l.actionPerformed(e);
    }

    // ---- PopupMenuListener fan-out ----

    public void addPopupMenuListener(PopupMenuListener l) {
        store().listenerList.add(PopupMenuListener.class, l);
    }

    public void removePopupMenuListener(PopupMenuListener l) {
        store().listenerList.remove(PopupMenuListener.class, l);
    }

    public PopupMenuListener[] getPopupMenuListeners() {
        return store().listenerList.getListeners(PopupMenuListener.class);
    }

    protected void firePopupMenuWillBecomeVisible() {
        PopupMenuListener[] listeners = store().listenerList.getListeners(PopupMenuListener.class);
        if (listeners.length == 0) return;
        PopupMenuEvent e = new PopupMenuEvent(this);
        for (PopupMenuListener l : listeners) l.popupMenuWillBecomeVisible(e);
    }

    protected void firePopupMenuWillBecomeInvisible() {
        PopupMenuListener[] listeners = store().listenerList.getListeners(PopupMenuListener.class);
        if (listeners.length == 0) return;
        PopupMenuEvent e = new PopupMenuEvent(this);
        for (PopupMenuListener l : listeners) l.popupMenuWillBecomeInvisible(e);
    }

    // ---- ListDataListener fan-out (model events forwarded) ----

    public void addListDataListener(ListDataListener l) {
        store().listenerList.add(ListDataListener.class, l);
    }

    public void removeListDataListener(ListDataListener l) {
        store().listenerList.remove(ListDataListener.class, l);
    }

    /**
     * Forward a model {@link ListDataEvent} to the surrogate's own
     * {@link ListDataListener} registrations. JDK JComboBox does the
     * same — it implements {@link ListDataListener} on the model and
     * re-fires through its own listener registry. Source rebound to
     * the surrogate's model (matches JDK source in the forwarded event).
     */
    protected void fireListDataEvent(ListDataEvent original) {
        ListDataListener[] listeners = store().listenerList.getListeners(ListDataListener.class);
        if (listeners.length == 0) return;
        ListDataEvent e = new ListDataEvent(original.getSource(), original.getType(),
                original.getIndex0(), original.getIndex1());
        switch (original.getType()) {
            case ListDataEvent.INTERVAL_ADDED -> {
                for (ListDataListener l : listeners) l.intervalAdded(e);
            }
            case ListDataEvent.INTERVAL_REMOVED -> {
                for (ListDataListener l : listeners) l.intervalRemoved(e);
            }
            default -> {
                for (ListDataListener l : listeners) l.contentsChanged(e);
            }
        }
    }

    // ---- Action surface ----

    /**
     * Install (or clear) a JDK {@link Action}. Matches JDK JComboBox's
     * narrow action surface: enabled / SHORT_DESCRIPTION (tooltip) /
     * ACTION_COMMAND_KEY are honored; NAME / icon / mnemonic are not
     * (JDK JComboBox doesn't surface those — combo boxes don't carry
     * label text like buttons do). The Action's
     * {@code actionPerformed} is invoked alongside our own
     * ActionListener fan-out on every selection commit, with source
     * rebound to {@code this}.
     */
    public void setAction(Action a) {
        ComboBoxStateStore store = store();
        Action old = store.action;
        if (old == a) return;
        if (old != null && store.actionPropertyChangeListener != null) {
            old.removePropertyChangeListener(store.actionPropertyChangeListener);
            // Detach the synthetic ActionListener that bridged the Action
            // into the surrogate's fan-out chain (lookup by identity is
            // O(n) but ActionListener lists are typically tiny).
            for (ActionListener l : getActionListeners()) {
                if (l == store.actionPropertyChangeListener) {
                    removeActionListener(l);
                    break;
                }
            }
        }
        store.action = a;
        store.actionPropertyChangeListener = null;
        if (a != null) {
            applyActionProperties(a);
            // Bridge: when the user picks an item, dispatch through the
            // Action too. Wraps in ActionListener so getActionListeners()
            // contains it (matches JDK's contract that setAction adds to
            // the listener list).
            ActionListener bridge = a::actionPerformed;
            addActionListener(bridge);
            // Re-fetch a PCL slot so detach works symmetrically. The
            // PCL routes Action mutations back into the combo's state.
            PropertyChangeListener pcl = evt -> applyActionProperty(a, evt.getPropertyName());
            store.actionPropertyChangeListener = pcl;
            a.addPropertyChangeListener(pcl);
        }
        firePropertyChange("action", old, a);
    }

    public Action getAction() {
        return store().action;
    }

    private void applyActionProperties(Action a) {
        applyActionProperty(a, Action.SHORT_DESCRIPTION);
        applyActionProperty(a, Action.ACTION_COMMAND_KEY);
        // Action.enabled isn't a Map key but propagates via Action.isEnabled.
        setEnabled(a.isEnabled());
    }

    private void applyActionProperty(Action a, String key) {
        if (key == null) return;
        switch (key) {
            case Action.SHORT_DESCRIPTION -> {
                Object tt = a.getValue(Action.SHORT_DESCRIPTION);
                setTooltipText(tt == null ? null : tt.toString());
            }
            case Action.ACTION_COMMAND_KEY -> {
                Object cmd = a.getValue(Action.ACTION_COMMAND_KEY);
                setActionCommand(cmd == null ? null : cmd.toString());
            }
            case "enabled" -> setEnabled(a.isEnabled());
            default -> {
                /* JDK JComboBox honors only the three above. NAME / icon /
                 * mnemonic / accelerator-key all ignored — combo boxes
                 * don't carry label-style chrome. */
            }
        }
    }

    // ---- R_vaadin_first drop-and-WARN surface ----

    public Object getEditor() {
        SHelper.onUnimplemented(this, "getEditor");
        return null;
    }

    public void setEditor(Object anEditor) {
        SHelper.onUnimplemented(this, "setEditor", anEditor);
    }

    public void configureEditor(Object anEditor, Object anItem) {
        SHelper.onUnimplemented(this, "configureEditor", anEditor, anItem);
    }

    public Object getKeySelectionManager() {
        SHelper.onUnimplemented(this, "getKeySelectionManager");
        return null;
    }

    public void setKeySelectionManager(Object aManager) {
        SHelper.onUnimplemented(this, "setKeySelectionManager", aManager);
    }

    public boolean isLightWeightPopupEnabled() {
        // JDK default = true. R_vaadin_first drop-and-WARN: Vaadin owns the popup
        // chrome and there's no host-side toggle to honor.
        return true;
    }

    public void setLightWeightPopupEnabled(boolean aFlag) {
        if (aFlag) {
            // No-op when the user requests JDK's default — silent matches
            // the migrated app's expectation.
            return;
        }
        SHelper.onUnimplemented(this, "setLightWeightPopupEnabled", aFlag);
    }

    public T getPrototypeDisplayValue() {
        SHelper.onUnimplemented(this, "getPrototypeDisplayValue");
        return null;
    }

    public void setPrototypeDisplayValue(T prototypeDisplayValue) {
        // R_vaadin_first: Vaadin combo box doesn't surface a prototype-driven width
        // hint — accepted-and-dropped per R_vaadin_first. Setter WARNs only on
        // non-null (avoids noise when user code clears the prototype).
        if (prototypeDisplayValue != null) {
            SHelper.onUnimplemented(this, "setPrototypeDisplayValue", prototypeDisplayValue);
        }
    }

    public boolean selectWithKeyChar(char keyChar) {
        // R_vaadin_first: subsumed by Vaadin's browser-driven type-to-search.
        SHelper.onUnimplemented(this, "selectWithKeyChar", keyChar);
        return false;
    }

    // ---- Visibility-widening override (SD_toggle_checkbox_first_cut / SD_sjtextfield precedent) ----

    /**
     * Visibility-widening override of {@link com.vaadin.flow.component.combobox.ComboBoxBase#validate}
     * (Vaadin declares it {@code protected} for input validation) to
     * satisfy {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public
     * {@code validate()} contract. Delegates to super so Vaadin's
     * input-validation behaviour is preserved.
     */
    @Override
    public void validate() {
        super.validate();
    }

    // ---- L&F stub ----

    public String getUIClassID() {
        return "ComboBoxUI";
    }
}
