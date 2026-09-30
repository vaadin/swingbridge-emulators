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

import javax.swing.DefaultSingleSelectionModel;
import javax.swing.SingleSelectionModel;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.SJTabbedPane} (SD_sjtabbedpane).
 *
 * <p>What lives here per R_vaadin_first: things Vaadin's {@link com.vaadin.flow.component.tabs.TabSheet}
 * has no counterpart for — the JDK {@link SingleSelectionModel} facade,
 * the {@link ChangeListener} fan-out list, the parallel-indexed
 * {@link TabMeta} list (per-tab title/icon/tooltip/customComponent
 * shadow needed for the children-based render strategy — see SD_sjtabbedpane for
 * why we never use {@code Tab.setLabel}), the {@code preventPeerEvents}
 * loop guard, and headline shadows for {@code tabPlacement} +
 * {@code tabLayoutPolicy} that round-trip without a Vaadin counterpart
 * (only TOP placement is actually delivered; SCROLL policy is silently
 * accepted; WRAP/LEFT/RIGHT/BOTTOM WARN).
 *
 * <p>Selection itself lives on Vaadin TabSheet (R_vaadin_first source-of-truth); the
 * {@code model} below is a JDK-faithful pass-through whose state is kept
 * in lockstep with the peer via the bridge wired in
 * {@code SJTabbedPane}'s ctor.
 */
public final class TabbedPaneStateStore {

    /**
     * One entry per peer tab, parallel-indexed with the underlying TabSheet's
     * tab strip. Rebuilt by {@code renderTabHeader(i)} into the Vaadin Tab's
     * children whenever any of these fields mutate.
     */
    public static final class TabMeta {
        public String title;
        public javax.swing.Icon icon;
        public String tooltip;
        /** User-supplied custom header component (JDK {@code setTabComponentAt}). When non-null, wins over title/icon for visual rendering; title still drives ariaLabel + getTitleAt readback. */
        public Component tabComponent;

        public TabMeta(String title, javax.swing.Icon icon, String tooltip) {
            this.title = title;
            this.icon = icon;
            this.tooltip = tooltip;
        }
    }

    public final List<TabMeta> tabMetas = new ArrayList<>();

    /** JDK-faithful model facade. Default {@link DefaultSingleSelectionModel}; swappable via {@code SJTabbedPane.setModel}, which rewires {@link #modelListener}. */
    public SingleSelectionModel model = new DefaultSingleSelectionModel();

    /** Internal listener installed on {@link #model} that pushes to peer + re-fires through {@link #changeListeners}. */
    public ChangeListener modelListener;

    public final List<ChangeListener> changeListeners = new ArrayList<>();

    /**
     * Loop guard: set true around any peer-write so the resulting
     * SelectedChangeEvent listener bails. Pattern matches SJComboBox's
     * peer-push protection.
     */
    public boolean preventPeerEvents;

    /** Headline shadow — JDK default TOP. Round-trips; only TOP is delivered. */
    public int tabPlacement = javax.swing.SwingConstants.TOP;

    /** Headline shadow — JDK default WRAP_TAB_LAYOUT. Round-trips; only SCROLL is delivered (silently). */
    public int tabLayoutPolicy;

    private TabbedPaneStateStore() {}

    public static TabbedPaneStateStore of(Component target) {
        TabbedPaneStateStore existing = ComponentUtil.getData(target, TabbedPaneStateStore.class);
        if (existing != null) return existing;
        TabbedPaneStateStore fresh = new TabbedPaneStateStore();
        ComponentUtil.setData(target, TabbedPaneStateStore.class, fresh);
        return fresh;
    }
}
