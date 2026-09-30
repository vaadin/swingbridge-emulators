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

import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.beans.PropertyChangeListener;
import java.util.IdentityHashMap;
import java.util.function.Function;

/**
 * State holder for {@link com.vaadin.swingbridge.surrogates.SJTree} (SD_sjtree).
 *
 * <p>Holds <em>only</em> R_vaadin_first-legitimate state — UI-functionality source-of-truth
 * (the two models), navigation machinery (the identity parent map backing
 * Vaadin's {@code getParent}/{@code getItemIndex} contract + the SD_sjtree_selection_bridge
 * selection-path bridge), the SD_sjtree_hierarchy_render hierarchy-column render seam, behaviour
 * gates and live registrations. No round-trip shadow caches: JTree's
 * no-Vaadin-counterpart knobs ({@code showsRootHandles}, {@code rowHeight},
 * {@code toggleClickCount}, {@code visibleRowCount}, …) round-trip on the
 * <em>emulator</em> layer per R_swing_is_truth — the asymmetric split SJToolBar (SD_sjtoolbar) /
 * SJSplitPane (SD_sjsplitpane) / SJList (SD_sjlist) established.
 *
 * <p>Source-of-truth fields per R_swing_is_truth:
 * <ul>
 *   <li>{@link #model} — installed {@link TreeModel}; the tree's nodes.</li>
 *   <li>{@link #selectionModel} — installed {@link TreeSelectionModel};
 *       path-set selection state (bridged onto Vaadin TreeGrid's item-set
 *       selection per SD_sjtree_selection_bridge).</li>
 * </ul>
 * Both are JDK types reused per D_event_port_policy — the entire {@code javax.swing.tree.*}
 * model/path/selection surface touches no {@code java.awt.Component}.
 */
public final class TreeStateStore {

    /** Source of truth for the tree's nodes (R_swing_is_truth). Never null after ctor. */
    public TreeModel model;

    /** Source of truth for path-set selection state. Never null after ctor. */
    public TreeSelectionModel selectionModel;

    /**
     * Listener installed on {@link #selectionModel} that watches the
     * {@code "selectionMode"} PCE ({@code DefaultTreeSelectionModel} fires it
     * from {@code setSelectionMode}) and re-maps the Vaadin Grid selection
     * mode. JDK code idiomatically calls
     * {@code tree.getSelectionModel().setSelectionMode(...)} — there is no
     * {@code JTree.setSelectionMode} — so the surrogate must observe the
     * model, not a setter of its own. Moved across {@code setSelectionModel}
     * swaps.
     */
    public PropertyChangeListener selectionModeListener;

    /**
     * child → parent identity map, recorded on every
     * {@code fetchChildrenFromBackEnd} walk (SD_sjtree_lazy_provider). Legitimate machinery,
     * not an R_vaadin_first shadow cache: Vaadin's {@code HierarchicalDataProvider}
     * requires {@code getParent}/{@code getItemIndex} for
     * {@code TreeGrid.scrollToItem}, and the SD_sjtree_selection_bridge selection bridge needs
     * node→{@code TreePath} reconstruction — the JDK {@code TreeModel} has
     * no {@code getParent}, so this map is the only way to walk up.
     * Identity-keyed to match SD_sjtree_node_identity's node-identity stance.
     */
    public final IdentityHashMap<Object, Object> parentMap = new IdentityHashMap<>();

    /**
     * SD_sjtree_hierarchy_render hierarchy-column render seam. The single
     * {@code addComponentHierarchyColumn} installed in the SJTree ctor reads
     * this function live per render call — TreeGrid's expand/collapse toggle
     * chrome lives in {@code HierarchyColumnComponentRenderer}'s template, so
     * the renderer itself is never re-installed; swapping render behaviour
     * (the emulator's D_jtree_renderer_registry JDK-{@code TreeCellRenderer} bridge) swaps this
     * function instead. Defaults to a fresh-per-call {@code Span} of the
     * node's text (D_jcombobox DOM-adoption guard).
     */
    public Function<Object, Component> componentProvider;

    /**
     * Whether the model root renders as a Vaadin top-level item (JDK default
     * true). {@code false} reshapes the data provider's root level: the root
     * hides and its children become top-level (SD_sjtree_lazy_provider). Drives real data-
     * provider behaviour — a gate, not a round-trip shadow.
     */
    public boolean rootVisible = true;

    /**
     * R_swing_is_truth feedback-loop guard. Set during selection pushes in both directions
     * (JDK model → Grid, Grid → JDK model) so the cascade doesn't double-fire.
     * Same shape SJTable / SJList take.
     */
    public boolean preventPeerEvents;

    /**
     * Node / visible-row-index of the item whose click is currently being
     * dispatched, or {@code null}/{@code -1} outside a dispatch. Set/cleared
     * around the {@code SMouseListener} fan-out in
     * {@code SJTree.addMouseListener}'s item-click wire so
     * {@code getRowForLocation} / {@code getPathForLocation} resolve during
     * the user's {@code mousePressed}/{@code mouseClicked} handler (R_layouts_close_enough — no
     * server-side pixel→row map; the {@code CaseTreeView.selectOnRightClick}
     * idiom). Fields, not thread-locals: the emulator re-source may drain the
     * dispatch in a UI fiber, and shared state survives that boundary
     * (SD_sjlist's stash rationale).
     */
    public Object clickNodeStash;
    public int clickRowStash = -1;

    /**
     * Vaadin Grid {@code SelectionListener} registration that mirrors
     * browser-side selection edits into the {@link #selectionModel}.
     * Re-installed on every selection-mode re-map since Vaadin re-creates the
     * Grid's internal selection backing on mode swap (SD_sjtable_selection_bridge/SD_sjlist).
     */
    public Registration vaadinSelectionRegistration;

    private TreeStateStore() {}

    public static TreeStateStore of(Component target) {
        TreeStateStore existing = ComponentUtil.getData(target, TreeStateStore.class);
        if (existing != null) return existing;
        TreeStateStore fresh = new TreeStateStore();
        ComponentUtil.setData(target, TreeStateStore.class, fresh);
        return fresh;
    }
}
