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

import javax.swing.ListModel;
import javax.swing.ListSelectionModel;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.SJList} (SD_sjlist).
 *
 * <p>Holds <em>only</em> R_vaadin_first-legitimate state — UI-functionality source-of-truth
 * (the two models) plus machinery (loop guard, the live Vaadin subscription,
 * the click-dispatch stash). No shadow caches: JList's no-Vaadin-counterpart
 * knobs (layout orientation, visibleRowCount, fixedCell sizes, selection
 * chrome, dragEnabled) round-trip on the <em>emulator</em> layer per R_swing_is_truth, the
 * asymmetric split SJToolBar (SD_sjtoolbar) / SJSplitPane (SD_sjsplitpane) established — the
 * surrogate drops them. The JDK selection-mode int isn't stored either: it
 * is reconstructed from {@link #selectionModel}'s {@code getSelectionMode()}.
 *
 * <p>Source-of-truth fields per R_swing_is_truth:
 * <ul>
 *   <li>{@link #model} — installed {@link ListModel}; the list's elements.</li>
 *   <li>{@link #selectionModel} — installed {@link ListSelectionModel};
 *       index-based selection (the same vocabulary Vaadin Grid's
 *       Integer-keyed selection carries, so the bridge is direct).</li>
 * </ul>
 * Both are JDK types reused per D_event_port_policy — {@code ListDataEvent} /
 * {@code ListSelectionEvent} are {@code Object}-sourced and touch no Component.
 */
public final class ListStateStore {

    /** Source of truth for the list's elements (R_swing_is_truth). Never null after ctor. */
    public ListModel<?> model;

    /** Source of truth for index-based selection state. Never null after ctor. */
    public ListSelectionModel selectionModel;

    /**
     * R_swing_is_truth feedback-loop guard. Set during model→peer pushes (push-selection)
     * and during peer→model write-throughs so the cascade doesn't
     * double-fire. Same shape SJTable / SJComboBox take.
     */
    public boolean preventPeerEvents;

    /**
     * Vaadin Grid {@code SelectionListener} registration that mirrors
     * browser-side selection edits into the {@link #selectionModel}.
     * Re-installed on {@code setSelectionMode} since Vaadin re-creates the
     * Grid's internal selection backing on mode swap.
     */
    public Registration vaadinSelectionRegistration;

    /**
     * Row index of the cell whose item-click is currently being dispatched,
     * or {@code -1} outside a dispatch. Set/cleared around the
     * {@code SMouseListener} fan-out in {@code SJList.addMouseListener}'s
     * item-click wire so {@code SJList.locationToIndex(Point)} (and, by
     * delegation, the emulator's) returns the clicked index during the
     * user's {@code mouseClicked} handler. A field (not a thread-local):
     * the emulator's re-source may drain the dispatch in a UI fiber,
     * and shared state survives that boundary.
     */
    public int clickIndexStash = -1;

    private ListStateStore() {}

    public static ListStateStore of(Component target) {
        ListStateStore existing = ComponentUtil.getData(target, ListStateStore.class);
        if (existing != null) return existing;
        ListStateStore fresh = new ListStateStore();
        ComponentUtil.setData(target, ListStateStore.class, fresh);
        return fresh;
    }
}
