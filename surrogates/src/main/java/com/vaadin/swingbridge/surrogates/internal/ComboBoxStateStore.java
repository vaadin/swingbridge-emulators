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

import javax.swing.ComboBoxModel;
import javax.swing.event.EventListenerList;
import javax.swing.event.ListDataListener;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.SJComboBox} (SD_sjcombobox).
 *
 * <p>Scoped per R_vaadin_first: the model + its bridge listener registration + the
 * surrogate-level fan-out lists belong here because they have no Vaadin
 * counterpart we can read back. Selection state itself does <em>not</em>
 * live here — the installed {@link ComboBoxModel#getSelectedItem()} is
 * the source of truth (R_swing_is_truth) and Vaadin's {@code getValue()} mirrors it on
 * the peer.
 */
public final class ComboBoxStateStore {

    /** Source of truth for items + selection (R_swing_is_truth). Never null after ctor. */
    public ComboBoxModel<?> model;

    /**
     * The {@link ListDataListener} we attach to {@link #model} to push
     * items into the Vaadin peer and to fan out the model's
     * {@link javax.swing.event.ListDataEvent}s to user listeners. Held
     * so {@code setModel} can detach from the old model before attaching
     * to the new one.
     */
    public ListDataListener modelListener;

    /**
     * R_swing_is_truth feedback-loop guard. Set during model→peer pushes and during
     * fire-side updates; checked by the peer ValueChangeListener and the
     * customValue listener so they bail instead of re-writing the model.
     */
    public boolean preventPeerEvents;

    /**
     * Editable shadow. Vaadin's {@code ComboBox.isAllowCustomValue()}
     * round-trips, but pairing the bit with {@link #customValueRegistration}
     * here keeps the swap atomic — flipping editable also tears down /
     * installs the customValue subscription.
     */
    public boolean editable;

    /**
     * {@link Registration} for the Vaadin {@code addCustomValueSetListener}
     * subscription that commits typed-but-unmatched input into the model; null
     * when not editable. Recreated on every {@code setEditable(true)} / model
     * swap so a stale registration can't fire against the wrong model.
     */
    public Registration customValueRegistration;

    /**
     * {@link Registration} for the Vaadin ValueChangeListener that mirrors
     * browser-side selection edits into the model. One-shot install per
     * surrogate lifetime — survives model swaps (the Vaadin {@code HasValue}
     * channel doesn't re-source on model change, only its items do); stored so
     * a teardown path could detach it.
     */
    public Registration valueChangeRegistration;

    /**
     * Type-keyed listener lists for {@link java.awt.event.ItemListener} /
     * {@link java.awt.event.ActionListener} /
     * {@link javax.swing.event.PopupMenuListener} /
     * {@link javax.swing.event.ListDataListener} surrogate-level fan-out.
     */
    public final EventListenerList listenerList = new EventListenerList();

    /**
     * No Vaadin analog. JDK actionCommand drives the {@link java.awt.event.ActionEvent}
     * payload delivered to user ActionListeners on selection commit.
     * Defaults to {@code "comboBoxChanged"} — JDK's own constant for
     * non-edit selection commits.
     */
    public String actionCommand = "comboBoxChanged";

    /** Currently installed {@link javax.swing.Action}; null when none. */
    public javax.swing.Action action;

    /** PCL on {@link #action} that propagates per-property mutations back to the surrogate. */
    public java.beans.PropertyChangeListener actionPropertyChangeListener;

    /**
     * {@code maximumRowCount} — JDK default 8. Mirrors Vaadin's
     * {@code setPageSize}; we read it back from a stored field rather
     * than from {@code peer.getPageSize()} since the two map cleanly.
     */
    public int maximumRowCount = 8;

    /**
     * Cached previous selected item, used by the {@link java.awt.event.ItemEvent}
     * fan-out to deliver the JDK-canonical DESELECTED-prev + SELECTED-new
     * pair on every selection change. Initialized to the model's initial
     * selection in the surrogate ctor; updated as part of every commit.
     */
    public Object lastSelected;

    private ComboBoxStateStore() {}

    public static ComboBoxStateStore of(Component target) {
        ComboBoxStateStore existing = ComponentUtil.getData(target, ComboBoxStateStore.class);
        if (existing != null) return existing;
        ComboBoxStateStore fresh = new ComboBoxStateStore();
        ComponentUtil.setData(target, ComboBoxStateStore.class, fresh);
        return fresh;
    }
}
