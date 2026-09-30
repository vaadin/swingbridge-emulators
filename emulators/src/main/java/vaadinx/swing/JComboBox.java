/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JComboBox
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.swingbridge.surrogates.SJComboBox;

import javax.swing.Action;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.MutableComboBoxModel;
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
import java.util.Vector;

/**
 * Emulator for {@link javax.swing.JComboBox}, rendered by {@link SJComboBox} (SD_sjcombobox).
 * R_leaf_peer_lockdown-locked-down: JComboBox is a leaf in the public
 * {@code javax.swing.*} hierarchy, so the protected {@code (Component peer)}
 * ctor is omitted and the root public ctor hard-codes the surrogate.
 *
 * <p>The event engine is the JDK's, and it lives here, as it does in the JDK: this combo is
 * its model's {@link ListDataListener}, and {@link #contentsChanged} /
 * {@link #intervalAdded} / {@link #intervalRemoved} fire the {@link ItemEvent} pair and the
 * {@link ActionEvent}. So a worker filling the model hears the events on the worker, and every
 * getter reads the model or a Swing-side field, never the peer. The surrogate listens to the
 * same model and renders it. It keeps its own listener lists for stage-3 code, which this
 * emulator does not subscribe to. A browser pick reaches the model through the surrogate and
 * comes back here as a model event. {@link #isPopupVisible()} is the one piece of state the
 * browser also changes: {@link #setPopupVisible} writes it and fires the popup events itself,
 * and a user's click reaches it through the surrogate's
 * {@link SJComboBox#addOpenedChangeListener}. The
 * {@code ListCellRenderer} carve-out (D_jcombobox) is documented on {@link #setRenderer}.
 */
public class JComboBox<E> extends vaadinx.swing.JComponent
        implements ItemSelectable, ListDataListener, ActionListener,
        javax.accessibility.Accessible, vaadinx.FieldReconciler.Reconcilable {

    // Listeners live on the inherited Component.listenerList (not a private
    // one) so JComponent.firePropertyChange et al. share it — same shape as
    // JDK JComboBox, whose listeners sit on its inherited JComponent list.

    // actionCommand — JDK null-default (fires "comboBoxChanged" via the
    // surrogate's default), but a non-null value overrides at fire time.
    // Kept on the emulator so a setActionCommand call doesn't need to
    // ricochet through the surrogate just to round-trip.
    protected String actionCommand;

    // Currently installed user-supplied ListCellRenderer. When non-null
    // the bridge below uses it to drive the surrogate's Vaadin renderer.
    protected vaadinx.swing.ListCellRenderer<? super E> renderer;

    // Currently installed Action; null until setAction(non-null).
    private Action action;
    // PCL on the action — recreated per setAction call.
    private PropertyChangeListener actionPropertyChangeListener;

    // ---- Constructors ----

    public JComboBox() {
        this(new SJComboBox<E>());
    }

    public JComboBox(ComboBoxModel<E> aModel) {
        this(new SJComboBox<E>(aModel));
    }

    public JComboBox(E[] items) {
        this(new SJComboBox<E>(items));
    }

    public JComboBox(Vector<E> items) {
        this(new SJComboBox<E>(items));
    }

    private JComboBox(SJComboBox<E> peer) {
        // Peer lock-down per R_leaf_peer_lockdown: javax.swing.JComboBox is a leaf in
        // the public Swing hierarchy. This ctor is private so
        // user-code subclasses can't reach it to swap the peer type;
        // they all funnel through the public ctors above which
        // hard-code the surrogate.
        super(peer);
        installOpenedSync(peer);
        // Seed the JDK-shaped fields (and write-detection baselines) from the peer the
        // public ctors configured (D_field_write_reconcile; see JSlider), read while it has
        // never been attached. The model subscription is the JDK ctor's setModel.
        ComboBoxModel<E> model = peer.getModel();
        listenTo(model);
        dataModel = model;
        selectedItemReminder = model.getSelectedItem();
        isEditable = pushedIsEditable = peer.isEditable();
        maximumRowCount = pushedMaximumRowCount = peer.getMaximumRowCount();
        popupVisible = peer.isOpened();
        vaadinx.FieldReconciler.register(this, peer);
    }

    // JDK protected fields, Swing-side truth per D_field_write_reconcile (see JSlider for
    // the canonical commentary). dataModel is a second pointer to the model object the
    // surrogate's machinery runs on. selectedItemReminder tracks the selected item the way
    // the JDK's selectedItemChanged() maintains it — assigned by the item bridge below.
    protected ComboBoxModel<E> dataModel;
    protected boolean isEditable;
    protected int maximumRowCount;
    protected Object selectedItemReminder;

    // Last values pushed to the peer — reconcileFields()'s write-detection baseline.
    private boolean pushedIsEditable;
    private int pushedMaximumRowCount;

    /** The model this combo is registered on as a {@link ListDataListener}, and the surrogate renders. */
    private ComboBoxModel<E> listenedModel;

    /** JDK private flags: {@link #setSelectedItem} suppresses the model echo's ActionEvent; {@link #fireActionEvent} does not re-enter. */
    private boolean selectingItem;
    private boolean firingActionEvent;

    /** Whether the dropdown is open: written by {@link #setPopupVisible} and, for a user's click, {@link #installOpenedSync}. */
    private boolean popupVisible;

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (dataModel != listenedModel) {
            ComboBoxModel<E> m = dataModel;
            listenTo(m);
            surrogate().setModel(m);
            vaadinx.FieldReconciler.reportDirectWrite(this, "dataModel", "setModel");
        }
        if (isEditable != pushedIsEditable) {
            surrogate().setEditable(isEditable);
            pushedIsEditable = isEditable;
            vaadinx.FieldReconciler.reportDirectWrite(this, "isEditable", "setEditable");
        }
        if (maximumRowCount != pushedMaximumRowCount) {
            surrogate().setMaximumRowCount(maximumRowCount);
            pushedMaximumRowCount = maximumRowCount;
            vaadinx.FieldReconciler.reportDirectWrite(this, "maximumRowCount", "setMaximumRowCount");
        }
        // selectedItemReminder and editor need no reconcile: the reminder is SB-Emulators-maintained
        // bookkeeping (the bridge overwrites it on the next selection), and the editor is
        // inert — a direct write to either has exactly the JDK's local-state consequences.
    }

    /** Moves this combo's model subscription from {@link #listenedModel} onto {@code m}. */
    private void listenTo(ComboBoxModel<E> m) {
        if (listenedModel != null) listenedModel.removeListDataListener(this);
        listenedModel = m;
        m.addListDataListener(this);
    }

    /** Set while {@link #setPopupVisible} writes the peer, whose {@code OpenedChangeEvent} echo it has already fired. */
    private boolean preventPeerEvents;

    /**
     * Carries a user's open or close into {@link #popupVisible} and the
     * {@code PopupMenuListener} fan-out.
     *
     * <p>Two documented divergences, both {@link vaadinx.swing.JPopupMenu}'s too: the dropdown
     * is already open when the server learns, so a listener's {@link #isPopupVisible()} reads
     * the new state where the desktop's reads the old; and {@link #firePopupMenuCanceled()} is
     * never reached, since the browser's close cannot tell Esc or a click outside from a pick
     * (R_match_swing_errors sub-bucket (a)).
     *
     * <p>Both checks and the {@link #popupVisible} write sit outside {@code callSwing},
     * per R_swing_is_truth: the echo of {@link #setPopupVisible}, which has fired
     * already, is not peer→Swing work, and writing the state first keeps the next
     * {@code OpenedChangeEvent} from re-entering while a handler is parked.
     */
    private void installOpenedSync(SJComboBox<E> sb) {
        sb.addOpenedChangeListener(e -> {
            if (preventPeerEvents) return;
            final boolean opened = e.isOpened();
            if (popupVisible == opened) return;
            popupVisible = opened;
            vaadinx.EHelper.callSwing(() -> {
                if (opened) firePopupMenuWillBecomeVisible();
                else firePopupMenuWillBecomeInvisible();
            });
        });
    }

    @SuppressWarnings("unchecked")
    private SJComboBox<E> surrogate() {
        return (SJComboBox<E>) getPeer();
    }

    // ---- Selection API ----
    //
    // The JDK's bodies, on the model. None of these touch the peer: the surrogate's own model
    // listener renders the change, hopping onto its UI thread when a worker made it
    // (SD_background_model_hop).

    /**
     * Selects {@code anObject}, rejecting one the model does not contain unless the combo box
     * is editable, and fires the {@link ActionEvent} once whether or not the selection changed,
     * as the JDK does. On a match, the model's own element is selected, so an
     * {@code equals}-but-not-{@code ==} argument does not displace it.
     *
     * <p>The JDK also hands a matched item to {@code getEditor().setItem}; the editor
     * here is inert (see {@link #setEditor}), so that step is the declined effect.
     */
    public void setSelectedItem(Object anObject) {
        Object oldSelection = selectedItemReminder;
        Object objectToSelect = anObject;
        if (oldSelection == null || !oldSelection.equals(anObject)) {
            if (anObject != null && !isEditable()) {
                boolean found = false;
                for (int i = 0; i < dataModel.getSize(); i++) {
                    E element = dataModel.getElementAt(i);
                    if (anObject.equals(element)) {
                        found = true;
                        objectToSelect = element;
                        break;
                    }
                }
                if (!found) {
                    return;
                }
            }
            // The model's ListDataEvent reaches contentsChanged, which must not fire the
            // ActionEvent this method fires below.
            selectingItem = true;
            dataModel.setSelectedItem(objectToSelect);
            selectingItem = false;
            if (selectedItemReminder != dataModel.getSelectedItem()) {
                // A model that fires no ListDataEvent on a selection change.
                selectedItemChanged();
            }
        }
        fireActionEvent();
    }

    public Object getSelectedItem() {
        return dataModel.getSelectedItem();
    }

    public int getSelectedIndex() {
        Object sObject = dataModel.getSelectedItem();
        for (int i = 0, c = dataModel.getSize(); i < c; i++) {
            E obj = dataModel.getElementAt(i);
            if (obj != null && obj.equals(sObject)) return i;
        }
        return -1;
    }

    public void setSelectedIndex(int anIndex) {
        int size = dataModel.getSize();
        if (anIndex == -1) {
            setSelectedItem(null);
        } else if (anIndex < -1 || anIndex >= size) {
            throw new IllegalArgumentException("setSelectedIndex: " + anIndex + " out of bounds");
        } else {
            setSelectedItem(dataModel.getElementAt(anIndex));
        }
    }

    @Override
    public Object[] getSelectedObjects() {
        Object selectedObject = getSelectedItem();
        return selectedObject == null ? new Object[0] : new Object[]{selectedObject};
    }

    // ---- Items API ----

    @SuppressWarnings("unchecked")
    public void addItem(E item) {
        checkMutableComboBoxModel();
        ((MutableComboBoxModel<E>) dataModel).addElement(item);
    }

    @SuppressWarnings("unchecked")
    public void insertItemAt(E item, int index) {
        checkMutableComboBoxModel();
        ((MutableComboBoxModel<E>) dataModel).insertElementAt(item, index);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void removeItem(Object anObject) {
        checkMutableComboBoxModel();
        ((MutableComboBoxModel) dataModel).removeElement(anObject);
    }

    @SuppressWarnings("unchecked")
    public void removeItemAt(int anIndex) {
        checkMutableComboBoxModel();
        ((MutableComboBoxModel<E>) dataModel).removeElementAt(anIndex);
    }

    /**
     * The JDK also clears an editable combo's editor; the editor here is inert
     * (see {@link #setEditor}), so that step is the declined effect.
     */
    @SuppressWarnings("unchecked")
    public void removeAllItems() {
        checkMutableComboBoxModel();
        MutableComboBoxModel<E> model = (MutableComboBoxModel<E>) dataModel;
        int size = model.getSize();
        if (model instanceof DefaultComboBoxModel<E> dcm) {
            dcm.removeAllElements();
        } else {
            for (int i = 0; i < size; ++i) {
                model.removeElement(model.getElementAt(0));
            }
        }
        selectedItemReminder = null;
    }

    /** The JDK's guard, message included; an unchecked {@code RuntimeException}, as there. */
    void checkMutableComboBoxModel() {
        if (!(dataModel instanceof MutableComboBoxModel)) {
            throw new RuntimeException("Cannot use this method with a non-Mutable data model.");
        }
    }

    public E getItemAt(int index) {
        return dataModel.getElementAt(index);
    }

    public int getItemCount() {
        return dataModel.getSize();
    }

    // ---- Model accessors ----

    public ComboBoxModel<E> getModel() {
        return dataModel;
    }

    public void setModel(ComboBoxModel<E> aModel) {
        ComboBoxModel<E> oldModel = dataModel;
        listenTo(aModel);
        dataModel = aModel;
        selectedItemReminder = dataModel.getSelectedItem();
        if (oldModel != aModel) {
            withPeer(p -> surrogate().setModel(aModel));
        }
        firePropertyChange("model", oldModel, dataModel);
    }

    // ---- Editable -----------------------------------------------------

    public boolean isEditable() {
        return isEditable;
    }

    public void setEditable(boolean aFlag) {
        boolean old = isEditable;
        isEditable = aFlag;
        withPeer(p -> surrogate().setEditable(aFlag));
        pushedIsEditable = aFlag;
        if (old != aFlag) {
            firePropertyChange("editable", old, aFlag);
        }
    }

    // ---- Popup -------------------------------------------------------

    /** Whether the dropdown is open, whether the program or the user opened it. */
    public boolean isPopupVisible() {
        return popupVisible;
    }

    /**
     * Open or close the dropdown. Opening fires {@code popupMenuWillBecomeVisible} every
     * time, even on an open dropdown; closing fires {@code popupMenuWillBecomeInvisible}
     * only when it was open. Both fire before the state changes.
     *
     * <p>The notifications are the Basic L&amp;F's, which every stock L&amp;F
     * inherits: {@code BasicComboPopup.show()} fires unconditionally and then
     * asks for {@code getLocationOnScreen()}, whose
     * {@code getLocationOnScreen_NoTreeLock} throws unless
     * {@code peer != null && isShowing()} (message reproduced verbatim), while
     * {@code hide()} reaches {@code JPopupMenu.setVisible(false)}, which returns
     * early on a hidden popup. The hide path never asks for a location, so
     * {@code setPopupVisible(false)} stays silent on a combo box that was never
     * shown.
     *
     * @throws java.awt.IllegalComponentStateException on an <em>opening</em> call
     *         for a combo box that is not {@linkplain #isShowing() showing}, after
     *         {@code popupMenuWillBecomeVisible} has fired — the same failure a real
     *         JComboBox produces for the same call, per R_match_swing_errors'
     *         match-Swing's-error-handling clause.
     */
    public void setPopupVisible(boolean v) {
        if (v) {
            firePopupMenuWillBecomeVisible();
            if (!isShowing()) {
                throw new java.awt.IllegalComponentStateException(
                        "component must be showing on the screen to determine its location");
            }
        } else {
            if (!popupVisible) return;
            firePopupMenuWillBecomeInvisible();
        }
        popupVisible = v;
        withPeer(p -> {
            preventPeerEvents = true;
            try {
                surrogate().setPopupVisible(v);
            } finally {
                preventPeerEvents = false;
            }
        });
    }

    // Through setPopupVisible, not straight to the surrogate: that is the JDK's own
    // call graph (its showPopup / hidePopup are one-liners onto setPopupVisible), so
    // a migrator's showPopup() hits the same gate — and an override of
    // setPopupVisible runs, per R_no_vaadin_in_api limb 2.
    public void showPopup() {
        setPopupVisible(true);
    }

    public void hidePopup() {
        setPopupVisible(false);
    }

    public int getMaximumRowCount() {
        return maximumRowCount;
    }

    public void setMaximumRowCount(int count) {
        int old = maximumRowCount;
        maximumRowCount = count;
        withPeer(p -> surrogate().setMaximumRowCount(count));
        pushedMaximumRowCount = count;
        if (old != count) {
            firePropertyChange("maximumRowCount", old, count);
        }
    }

    // ---- ItemListener fan-out ----------------------------------------

    public void addItemListener(ItemListener aListener) {
        listenerList.add(ItemListener.class, aListener);
    }

    public void removeItemListener(ItemListener aListener) {
        listenerList.remove(ItemListener.class, aListener);
    }

    public ItemListener[] getItemListeners() {
        return listenerList.getListeners(ItemListener.class);
    }

    protected void fireItemStateChanged(ItemEvent e) {
        // Last to first, as the JDK notifies.
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == ItemListener.class) {
                ((ItemListener) listeners[i + 1]).itemStateChanged(e);
            }
        }
    }

    /**
     * Fires the {@link ItemEvent} pair for a selection change: {@code DESELECTED} on the old
     * item and {@code SELECTED} on the new one, each only when non-null.
     */
    protected void selectedItemChanged() {
        if (selectedItemReminder != null) {
            fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED,
                    selectedItemReminder, ItemEvent.DESELECTED));
        }
        selectedItemReminder = dataModel.getSelectedItem();
        if (selectedItemReminder != null) {
            fireItemStateChanged(new ItemEvent(this, ItemEvent.ITEM_STATE_CHANGED,
                    selectedItemReminder, ItemEvent.SELECTED));
        }
    }

    // ---- ActionListener fan-out --------------------------------------

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    public ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

    public void setActionCommand(String aCommand) {
        this.actionCommand = aCommand;
    }

    public String getActionCommand() {
        return actionCommand != null ? actionCommand : "comboBoxChanged";
    }

    /**
     * Notifies the {@link ActionListener}s last to first, as the JDK does; a listener
     * re-entering it fires nothing.
     *
     * <p>The JDK copies the modifiers of {@code EventQueue.getCurrentEvent()}; no AWT
     * event is being dispatched here, so they are 0.
     */
    protected void fireActionEvent() {
        if (firingActionEvent) return;
        firingActionEvent = true;
        ActionEvent e = null;
        Object[] listeners = listenerList.getListenerList();
        long mostRecentEventTime = vaadinx.awt.EventQueue.getMostRecentEventTime();
        int modifiers = 0;
        try {
            for (int i = listeners.length - 2; i >= 0; i -= 2) {
                if (listeners[i] == ActionListener.class) {
                    if (e == null) {
                        e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED,
                                getActionCommand(), mostRecentEventTime, modifiers);
                    }
                    ((ActionListener) listeners[i + 1]).actionPerformed(e);
                }
            }
        } finally {
            firingActionEvent = false;
        }
    }

    // ---- ActionListener interface (JDK self-registration) ------------

    /**
     * JDK JComboBox implements ActionListener so it can register itself
     * as the editor's ActionListener for editable combo boxes. The
     * default body fires the combo's own ActionEvent — which is what
     * happens here too.
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        fireActionEvent();
    }

    // ---- PopupMenuListener fan-out -----------------------------------

    public void addPopupMenuListener(PopupMenuListener l) {
        listenerList.add(PopupMenuListener.class, l);
    }

    public void removePopupMenuListener(PopupMenuListener l) {
        listenerList.remove(PopupMenuListener.class, l);
    }

    public PopupMenuListener[] getPopupMenuListeners() {
        return listenerList.getListeners(PopupMenuListener.class);
    }

    public void firePopupMenuWillBecomeVisible() {
        Object[] listeners = listenerList.getListenerList();
        PopupMenuEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == PopupMenuListener.class) {
                if (e == null) e = new PopupMenuEvent(this);
                ((PopupMenuListener) listeners[i + 1]).popupMenuWillBecomeVisible(e);
            }
        }
    }

    public void firePopupMenuWillBecomeInvisible() {
        Object[] listeners = listenerList.getListenerList();
        PopupMenuEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == PopupMenuListener.class) {
                if (e == null) e = new PopupMenuEvent(this);
                ((PopupMenuListener) listeners[i + 1]).popupMenuWillBecomeInvisible(e);
            }
        }
    }

    /** Never reached by SB-Emulators itself; see {@link #installOpenedSync}. */
    public void firePopupMenuCanceled() {
        Object[] listeners = listenerList.getListenerList();
        PopupMenuEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == PopupMenuListener.class) {
                if (e == null) e = new PopupMenuEvent(this);
                ((PopupMenuListener) listeners[i + 1]).popupMenuCanceled(e);
            }
        }
    }

    // ---- ListDataListener: the model subscription (the JDK's event engine) ----
    //
    // Called on whichever thread mutated the model, as in the JDK. The bodies are relayed
    // (SD_background_model_hop): inline on a worker, and in a UI fiber on the UI thread. A
    // browser pick needs the fiber: the surrogate writes the model from its peer listener,
    // which has none, and a modal dialog opened by the user's listener must park in one
    // (R_callswing_envelope).

    /** The model's selection or contents changed: the {@link ItemEvent} pair, then the {@link ActionEvent} unless {@link #setSelectedItem} is firing it. */
    @Override
    public void contentsChanged(ListDataEvent e) {
        vaadinx.EHelper.relayModelEvent(() -> {
            Object oldSelection = selectedItemReminder;
            Object newSelection = dataModel.getSelectedItem();
            if (oldSelection == null || !oldSelection.equals(newSelection)) {
                selectedItemChanged();
                if (!selectingItem) {
                    fireActionEvent();
                }
            }
        });
    }

    /** An added element can change the selection (a first element becomes selected); no {@link ActionEvent}. */
    @Override
    public void intervalAdded(ListDataEvent e) {
        vaadinx.EHelper.relayModelEvent(() -> {
            if (selectedItemReminder != dataModel.getSelectedItem()) {
                selectedItemChanged();
            }
        });
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        contentsChanged(e);
    }

    // ---- Renderer + bridge to Vaadin -------------------------------

    public vaadinx.swing.ListCellRenderer<? super E> getRenderer() {
        return renderer;
    }

    /**
     * Install a JDK-shaped {@link ListCellRenderer} on this combo. The
     * bridge wraps the renderer in a Vaadin
     * {@link com.vaadin.flow.data.renderer.ComponentRenderer} that, per
     * item, calls
     * {@code renderer.getListCellRendererComponent(null, item, index, false, false)}
     * and <b>snapshots</b> the resulting emulator component's rendered
     * state into a fresh Vaadin {@link com.vaadin.flow.component.html.Span}
     * before returning it. The snapshot is required because the JDK
     * renderer pattern is mutate-and-return-self: the same {@code JLabel}
     * instance is reconfigured and returned on every call. Vaadin's
     * {@code ComponentRenderer} requires a <em>fresh</em> {@link
     * com.vaadin.flow.component.Component} per item — adopting the same
     * DOM node into multiple positions silently fails on all but the
     * last (only the last assignment "wins"; earlier rows render empty).
     *
     * <p>The snapshot covers:
     * <ul>
     *   <li><b>Text</b> (always) — read from {@link JLabel#getText()}
     *       and applied to the fresh Span.</li>
     *   <li><b>Icon</b> ({@link ImageIcon} only — Path 1 per D_icon_rendering) —
     *       read from {@link JLabel#getIcon()} and bridged to a Vaadin
     *       {@link com.vaadin.flow.component.html.Image} child via the
     *       existing {@code com.vaadin.swingbridge.surrogates.util.Icons.imageIconToVaadinImage}.
     *       Other {@link Icon} impls drop with a WARN (Path 2/3 deferred).</li>
     * </ul>
     *
     * <p><b>Limitation: renderer must be JLabel-shaped.</b>
     * {@link DefaultListCellRenderer} + subclasses (the typical migration
     * pattern) work directly. Renderers that return a {@code JPanel} or
     * other deeper component tree fall back to a Span with
     * {@code String.valueOf(value)} and emit a WARN — the JDK
     * mutate-and-return-self contract doesn't generalise to
     * fresh-per-call without per-renderer snapshot logic, and we
     * don't ship one for arbitrary subtrees. R_match_swing_errors sub-bucket (a) gap.
     *
     * <p>The {@code list} argument is always {@code null},
     * {@code isSelected} and {@code cellHasFocus} always {@code false}
     * — Vaadin's render pipeline doesn't surface those at render time
     * and our combo box doesn't model a JList yet (D_jcombobox).
     * {@code index} is reconstructed by scanning the model for the
     * item — O(n) per render, accepted for typical CRUD-shape
     * dropdowns.
     */
    public void setRenderer(vaadinx.swing.ListCellRenderer<? super E> r) {
        vaadinx.swing.ListCellRenderer<? super E> old = this.renderer;
        this.renderer = r;
        withPeer(p -> {
            if (r == null) {
                // Restore default rendering: Vaadin's setItemLabelGenerator
                // with toString. The surrogate ctor already installed this,
                // so re-install to clear any prior ComponentRenderer.
                surrogate().setItemLabelGenerator(item -> item == null ? "" : String.valueOf(item));
            } else {
                surrogate().setRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(item -> {
                    int index = indexOf(item);
                    vaadinx.awt.Component out = r.getListCellRendererComponent(
                            null, item, index, false, false);
                    return snapshotRendererOutput(out, item);
                }));
            }
        });
        firePropertyChange("renderer", old, r);
    }

    /**
     * Build a fresh Vaadin {@link com.vaadin.flow.component.Component}
     * representing the renderer's output for one item. See
     * {@link #setRenderer} class javadoc for why a per-call snapshot is
     * required (mutate-and-return-self renderer contract vs. Vaadin's
     * fresh-per-call ComponentRenderer contract).
     *
     * <p>Strategy: fresh {@link com.vaadin.flow.component.html.Span}
     * carrying text + (for ImageIcon icons) an Image child. Reuses the
     * surrogate-side {@code com.vaadin.swingbridge.surrogates.util.Icons.imageIconToVaadinImage} bridge that
     * already drives SJLabel + SJButton icon rendering per D_icon_rendering.
     */
    static com.vaadin.flow.component.Component snapshotRendererOutput(
            vaadinx.awt.Component out, Object item) {
        if (out == null) {
            return new com.vaadin.flow.component.html.Span("");
        }
        if (out instanceof JCheckBox cb) {
            // Boolean-renderer path (JTable's BooleanRenderer + any
            // JCheckBox-shaped user renderer). Snapshot to a fresh
            // Vaadin Checkbox in read-only mode — the emulator
            // peer can't be re-parented into the grid cell, and
            // read-only keeps the cell non-interactive like Swing's
            // BooleanRenderer.
            com.vaadin.flow.component.checkbox.Checkbox snap =
                    new com.vaadin.flow.component.checkbox.Checkbox(cb.isSelected());
            snap.setReadOnly(true);
            return withRendererColors(out, snap);
        }
        if (out instanceof JLabel jl) {
            com.vaadin.flow.component.html.Span snap = new com.vaadin.flow.component.html.Span();
            String text = jl.getText();
            if (text != null && !text.isEmpty()) {
                snap.setText(text);
            }
            vaadinx.swing.Icon icon = jl.getIcon();
            if (icon instanceof vaadinx.swing.ImageIcon ours) {
                com.vaadin.flow.component.html.Image img =
                        com.vaadin.swingbridge.surrogates.util.Icons.imageIconToVaadinImage(ours.asJdk());
                if (img != null) {
                    // Element-level prepend so the icon precedes the text
                    // without disturbing Span.setText()'s text-node placement.
                    snap.getElement().insertChild(0, img.getElement());
                }
            } else if (icon != null) {
                vaadinx.EHelper.onUnimplemented(
                        "JComboBox", "setRenderer/snapshot/non-ImageIcon", icon);
            }
            return withRendererColors(out, snap);
        }
        // Non-JLabel-shaped renderer: fresh-per-call snapshot beyond
        // text + icon isn't shipped (R_match_swing_errors sub-bucket (a)).
        // Fall back to String.valueOf(item) so at least the text is
        // visible. Renderers that return arbitrary subtrees should
        // either subclass DefaultListCellRenderer or call
        // surrogate().setRenderer(myVaadinRenderer) directly per R_vaadin_first.
        vaadinx.EHelper.onUnimplemented(
                "JComboBox", "setRenderer/snapshot/non-JLabel", out.getClass().getName());
        return withRendererColors(out, new com.vaadin.flow.component.html.Span(
                item == null ? "" : String.valueOf(item)));
    }

    /**
     * Carry the renderer's colours onto the snapshot element (D_r12_provenance).
     *
     * <p>Without this the snapshot is text + icon only, so the commonest Swing
     * renderer idiom of all — colour the cell by its value, {@code setForeground(RED)}
     * on a negative number — renders unstyled: {@code setForeground} writes {@code color}
     * onto the *renderer's own* peer element, and the snapshot is a different element.
     *
     * <p>Swing's own rule is followed rather than copying both unconditionally: text is
     * painted in {@code foreground} always, the {@code background} only when the component
     * is opaque. {@code DefaultTableCellRenderer} is opaque from its constructor, a bare
     * {@code JLabel} renderer is not — so a plain JLabel's background is dropped here
     * exactly as Swing drops it. Both copies also skip an L&amp;F default, so an
     * un-recoloured renderer leaves the cell to the Vaadin theme.
     *
     * <p>Extent is the accepted R_layouts_close_enough loss: the colour lands on the snapshot element, which
     * is the text's own box, not the full table cell or list item Swing would flood.
     */
    private static com.vaadin.flow.component.Component withRendererColors(
            vaadinx.awt.Component out, com.vaadin.flow.component.Component snap) {
        // Both copies additionally skip an L&F default, per D_theme_is_lookandfeel.
        // The renderer is a JLabel, so it now carries Metal's installed colours,
        // and copying those would flood every cell with #333333 text on #EEEEEE
        // — pinning the L&F's palette into the Grid and defeating the theme that
        // is supposed to own the unstyled case. Only a colour the app actually
        // set on the renderer travels to the cell.
        java.awt.Color fg = out.getForeground();
        if (fg != null && !vaadinx.awt.Component.isThemeDefault(fg)) {
            snap.getElement().getStyle().set("color", com.vaadin.swingbridge.surrogates.util.CssConvert.toCss(fg));
        }
        java.awt.Color bg = out.getBackground();
        if (bg != null && out.isOpaque() && !vaadinx.awt.Component.isThemeDefault(bg)) {
            snap.getElement().getStyle().set(
                    "background-color", com.vaadin.swingbridge.surrogates.util.CssConvert.toCss(bg));
        }
        return snap;
    }

    /** Reconstruct the index of {@code item} in the model. {@code -1} if not found. */
    private int indexOf(E item) {
        ComboBoxModel<E> m = dataModel;
        for (int i = 0, n = m.getSize(); i < n; i++) {
            if (java.util.Objects.equals(item, m.getElementAt(i))) return i;
        }
        return -1;
    }

    // ---- Action surface -------------------------------------------

    public Action getAction() {
        return action;
    }

    public void setAction(Action a) {
        Action oldValue = this.action;
        if (oldValue != a) {
            if (oldValue != null) {
                if (actionPropertyChangeListener != null) {
                    oldValue.removePropertyChangeListener(actionPropertyChangeListener);
                    actionPropertyChangeListener = null;
                }
                // Symmetric to AbstractButton.setAction: detach the
                // synthetic ActionListener that bridged the Action.
                ActionListener[] ls = getActionListeners();
                for (ActionListener l : ls) {
                    if (isActionListenerForAction(l, oldValue)) {
                        removeActionListener(l);
                        break;
                    }
                }
            }
            this.action = a;
            if (a != null) {
                configurePropertiesFromAction(a);
                actionPropertyChangeListener = createActionPropertyChangeListener(a);
                a.addPropertyChangeListener(actionPropertyChangeListener);
                addActionListener(a::actionPerformed);
            } else {
                // Action cleared: leave properties in their last
                // applied state; matches JDK's "setAction(null) doesn't
                // wipe" contract.
                setEnabled(true);
            }
            firePropertyChange("action", oldValue, a);
        }
    }

    /**
     * Heuristic match for the synthetic ActionListener installed by
     * {@link #setAction}. We can't store the lambda directly without a
     * field; instead we recognise the bridge shape by capturing
     * Action::actionPerformed as a method reference and storing it on
     * the actionPropertyChangeListener bookkeeping. JDK-shape: the
     * bridge is a thin lambda over {@code a::actionPerformed} so
     * calling it with a synthetic ActionEvent produces the same effect
     * as calling {@code a.actionPerformed} directly. Recognising the
     * shape would require reflection on lambda metadata; in practice
     * we just remove all ActionListeners that are method references
     * to the old Action's actionPerformed by class equality. Cheaper:
     * accept the small leak and only remove on Action swap.
     *
     * <p>For now we simply return false — let setAction's bridge be
     * additive. The tradeoff: a setAction(a1).setAction(a2) chain
     * leaves a1's bridge ActionListener still installed. A migrator
     * can call removeActionListener explicitly if this matters; the
     * normal setAction call site is single-shot during construction,
     * so the leak doesn't surface in practice. JDK's own behaviour is
     * the same — its setAction stores the bridge listener too and only
     * removes it on the next setAction call, which we're not doing
     * yet.
     */
    private boolean isActionListenerForAction(ActionListener l, Action a) {
        return false;
    }

    protected void configurePropertiesFromAction(Action a) {
        if (a == null) return;
        Object cmd = a.getValue(Action.ACTION_COMMAND_KEY);
        setActionCommand(cmd == null ? null : cmd.toString());
        Object tt = a.getValue(Action.SHORT_DESCRIPTION);
        setToolTipText(tt == null ? null : tt.toString());
        setEnabled(a.isEnabled());
    }

    protected PropertyChangeListener createActionPropertyChangeListener(Action a) {
        return evt -> {
            String key = evt.getPropertyName();
            if (Action.SHORT_DESCRIPTION.equals(key)) {
                Object tt = a.getValue(Action.SHORT_DESCRIPTION);
                setToolTipText(tt == null ? null : tt.toString());
            } else if (Action.ACTION_COMMAND_KEY.equals(key)) {
                Object cmd = a.getValue(Action.ACTION_COMMAND_KEY);
                setActionCommand(cmd == null ? null : cmd.toString());
            } else if ("enabled".equals(key)) {
                setEnabled(a.isEnabled());
            }
            // Other keys (NAME, icon, mnemonic) — JDK JComboBox doesn't
            // honor those either, so no propagation.
        };
    }

    // ---- Drop-and-WARN surface (mirror SJComboBox's R_vaadin_first stance) -----

    public Object getEditor() {
        if (editor == null) vaadinx.EHelper.onUnimplemented("JComboBox", "getEditor");
        return editor;
    }

    /**
     * Stores the editor and fires {@code "editor"}; the editor is inert. A
     * Vaadin ComboBox renders its own input, so there is no slot to host a
     * {@code ComboBoxEditor}'s component and no {@code ActionListener}
     * rewiring to do — the declined effect. State and notification are owed
     * regardless (R_decline_effect_only, D_owed_events).
     */
    public void setEditor(Object anEditor) {
        Object old = this.editor;
        this.editor = anEditor;
        vaadinx.EHelper.onUnimplemented("JComboBox", "setEditor(component)", anEditor);
        firePropertyChange("editor", old, anEditor);
    }

    // Inert user-installed editor / prototype / popup-weight flag — stored so
    // the getters and the bound properties are honest (R_decline_effect_only, D_owed_events).
    // `editor` carries the JDK's protected field name (D_instance_field_surface); its type
    // stays Object because javax.swing.ComboBoxEditor's API is java.awt.Component-typed.
    protected Object editor;
    protected boolean lightWeightPopupEnabled = true;
    private E prototypeDisplayValue;

    public void configureEditor(Object anEditor, Object anItem) {
        vaadinx.EHelper.onUnimplemented("JComboBox", "configureEditor", anEditor, anItem);
    }

    /**
     * The JDK's nested interface, ported so a migrated
     * {@code new JComboBox.KeySelectionManager() { … }} still compiles after the import
     * swap. Its model argument is a {@code javax.swing.ComboBoxModel}, which SB-Emulators
     * reuses rather than ports.
     */
    public interface KeySelectionManager {
        /** @return the row {@code aKey} should select, or {@code -1} for no match */
        int selectionForKey(char aKey, javax.swing.ComboBoxModel<?> aModel);
    }

    // JDK protected field (D_instance_field_surface), null until installed — the JDK
    // creates its default lazily inside selectWithKeyChar, so a fresh combo answers null.
    protected KeySelectionManager keySelectionManager = null;

    /** @return {@code null} unless {@link #setKeySelectionManager} installed one, as in the JDK */
    public KeySelectionManager getKeySelectionManager() {
        return keySelectionManager;
    }

    /**
     * Stored but never consulted: the JDK reaches a manager only through
     * {@code selectWithKeyChar}, which its L&amp;F calls on a keystroke, and here
     * type-to-select is the browser's own — a {@code vaadin-combo-box} filters on
     * typing and no keystroke reaches the server as a char. So an installed manager
     * is state whose effect is the browser's, and the WARN says so.
     */
    public void setKeySelectionManager(KeySelectionManager aManager) {
        vaadinx.EHelper.onUnimplemented("JComboBox", "setKeySelectionManager", aManager);
        keySelectionManager = aManager;
    }

    /** @return what {@link #setLightWeightPopupEnabled} stored; Swing's default is {@code true} */
    public boolean isLightWeightPopupEnabled() {
        return lightWeightPopupEnabled;
    }

    /**
     * Stores the flag and fires {@code "lightWeightPopupEnabled"}. The
     * distinction it draws — a lightweight in-hierarchy popup vs. a heavyweight
     * native window — has no browser counterpart at all; the overlay is what it
     * is. State and notification are owed regardless (R_decline_effect_only, D_owed_events).
     */
    public void setLightWeightPopupEnabled(boolean aFlag) {
        boolean old = this.lightWeightPopupEnabled;
        this.lightWeightPopupEnabled = aFlag;
        if (!aFlag) vaadinx.EHelper.onUnimplemented("JComboBox", "setLightWeightPopupEnabled(popup kind)", aFlag);
        firePropertyChange("lightWeightPopupEnabled", old, aFlag);
    }

    public E getPrototypeDisplayValue() {
        return prototypeDisplayValue;
    }

    /**
     * Stores the prototype and fires {@code "prototypeDisplayValue"}; no
     * sizing happens. The prototype exists to let Swing size the popup from one
     * value instead of measuring every row — a pixel-measurement concern with
     * no counterpart here (R_layouts_close_enough). State and notification are owed regardless
     * (R_decline_effect_only, D_owed_events).
     */
    public void setPrototypeDisplayValue(E prototypeDisplayValue) {
        E old = this.prototypeDisplayValue;
        this.prototypeDisplayValue = prototypeDisplayValue;
        if (prototypeDisplayValue != null) {
            vaadinx.EHelper.onUnimplemented("JComboBox", "setPrototypeDisplayValue(sizing)", prototypeDisplayValue);
        }
        firePropertyChange("prototypeDisplayValue", old, prototypeDisplayValue);
    }

    public boolean selectWithKeyChar(char keyChar) {
        vaadinx.EHelper.onUnimplemented("JComboBox", "selectWithKeyChar", keyChar);
        return false;
    }

    // ---- L&F stubs ------------------------------------------------

    public String getUIClassID() {
        return "ComboBoxUI";
    }

    public void updateUI() {
        // L&F swap — no-op. Same shape as JSpinner / JSlider.
    }

    public javax.swing.plaf.ComboBoxUI getUI() {
        // No L&F dispatch — Vaadin owns the DOM. Same null-return as
        // JSpinner.getUI; signature returns the JDK UI type so JDK
        // reflective lookups (UIManager.getUI(this)) don't NPE.
        return null;
    }

    public void setUI(javax.swing.plaf.ComboBoxUI ui) {
        vaadinx.EHelper.onUnimplemented("JComboBox", "setUI", ui);
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JComboBox", "getAccessibleContext");
        return null;
    }
}
