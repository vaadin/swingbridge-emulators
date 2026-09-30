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
import com.vaadin.flow.data.provider.hierarchy.AbstractBackEndHierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.awt.event.SInputEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseListener;
import com.vaadin.swingbridge.surrogates.internal.Registrations;
import com.vaadin.swingbridge.surrogates.internal.TreeStateStore;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Surrogate for {@link javax.swing.JTree} (SD_sjtree). Extends Vaadin
 * {@link TreeGrid TreeGrid&lt;Object&gt;} — the JDK {@link TreeModel}'s node
 * objects are the Vaadin items, the installed model is the single source of
 * truth (R_swing_is_truth), and a custom lazy {@link AbstractBackEndHierarchicalDataProvider}
 * reads {@code getRoot}/{@code getChild}/{@code getChildCount}/{@code isLeaf}
 * live (children fetched only when a node expands). The third Grid-family
 * surrogate after SJTable (SD_sjtable) and SJList (SD_sjlist).
 *
 * <h2>Node-Object identity (SD_sjtree_node_identity)</h2>
 *
 * Items are keyed on the model's node objects directly — JDK's
 * {@code TreeModel} contract is node-as-{@code Object}, and the
 * SJTable/SJList {@code Integer}-index dodge can't transfer (a hierarchical
 * provider must navigate parent⇄child; a flat index is unstable under
 * expand/collapse). {@code DefaultMutableTreeNode} doesn't override
 * {@code equals}, so the dominant case is identity-keyed. <b>Accepted R_best_effort_behaviour
 * limitation:</b> a custom model returning value-{@code equals} nodes (the
 * same {@code String} at two positions) collides in Vaadin's key-mapper;
 * documented, not defended.
 *
 * <h2>Parent map (SD_sjtree_lazy_provider)</h2>
 *
 * Every {@code fetchChildrenFromBackEnd} records child→parent in an
 * identity map. Not an R_vaadin_first shadow cache: the data provider's
 * {@code getParent}/{@code getItemIndex} overrides (required by
 * {@link TreeGrid#scrollToItem} for a non-in-memory provider) and the
 * selection bridge's node→{@link TreePath} reconstruction both need to walk
 * up, and the JDK {@code TreeModel} has no {@code getParent}.
 *
 * <h2>Selection bridge (SD_sjtree_selection_bridge)</h2>
 *
 * JDK {@link TreeSelectionModel} is path-set–based, Grid selection is
 * item-set–based. {@code SJTree} IS-A {@link TreeSelectionListener} on the
 * installed model (JDK→peer push); a Vaadin selection listener reconstructs
 * {@code TreePath}s via the parent map and writes through
 * {@code setSelectionPaths} (peer→JDK), both guarded by
 * {@code preventPeerEvents}. <b>{@code getSelectionModel()} clashes with the
 * inherited {@code Grid.getSelectionModel(): GridSelectionModel}</b> —
 * exactly SJTable's clash — so the JDK-shaped accessor here is
 * {@link #getTreeSelectionModel()}; the emulator keeps the JDK name. Mode
 * changes are observed via the model's {@code "selectionMode"} PCE (JDK code
 * calls {@code tree.getSelectionModel().setSelectionMode(...)} — there is no
 * {@code JTree.setSelectionMode}).
 *
 * <h2>Hierarchy-column render seam (SD_sjtree_hierarchy_render)</h2>
 *
 * One {@code addComponentHierarchyColumn} installs at ctor time; its closure
 * reads {@link TreeStateStore#componentProvider} live. TreeGrid's
 * expand/collapse toggle chrome lives in
 * {@code HierarchyColumnComponentRenderer}'s template, so the renderer is
 * never re-installed — the emulator's D_jtree_renderer_registry JDK-{@code TreeCellRenderer}
 * bridge swaps the function via {@link #setHierarchyComponentProvider}.
 * Pure-surrogate users get a fresh-per-call {@link Span} of the node's text
 * (D_jcombobox DOM-adoption guard).
 *
 * <h2>R_vaadin_first drop-and-WARN surface</h2>
 *
 * {@code addTreeWillExpandListener} (no Vaadin pre-expansion vetoable hook —
 * D_gap_severity_triage sub-bucket (a)); in-cell editing (sub-bucket (b), the JTable
 * cell-editor precedent). Round-trip-only knobs ({@code showsRootHandles},
 * {@code rowHeight}, {@code toggleClickCount}, …) live emulator-side per the
 * asymmetric R_swing_is_truth-shadow split (SD_sjtoolbar/SD_sjsplitpane/SD_sjlist) and aren't present here at all.
 */
public class SJTree extends TreeGrid<Object> implements JComponentMixin,
        TreeModelListener, TreeSelectionListener {

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

    /** Stable key on the single hierarchy column (one per grid → unique). */
    private static final String COLUMN_KEY = "tree";

    // --- Constructors ---

    /** Pure-surrogate convenience: a single empty root, hidden. */
    public SJTree() {
        this(new DefaultTreeModel(new DefaultMutableTreeNode()));
        setRootVisible(false);
    }

    public SJTree(TreeModel model) {
        super();
        Objects.requireNonNull(model, "model must be non null");
        _installSwingClass();
        TreeStateStore store = store();

        // SD_sjtree_hierarchy_render: the single hierarchy column, installed once. The closure
        // reads the componentProvider function live — render behaviour swaps
        // at function level, never at renderer level (the tree-toggle chrome
        // lives in HierarchyColumnComponentRenderer's template).
        store.componentProvider = this::defaultRenderCell;
        addComponentHierarchyColumn(node -> store().componentProvider.apply(node))
                .setKey(COLUMN_KEY);

        // Selection model first (JDK-ish order), then data model.
        store.selectionModel = createDefaultSelectionModel();
        store.selectionModel.addTreeSelectionListener(this);
        store.selectionModeListener = e -> {
            if ("selectionMode".equals(e.getPropertyName())) {
                applySelectionModeToGrid();
            }
        };
        store.selectionModel.addPropertyChangeListener(store.selectionModeListener);
        applySelectionModeToGrid();

        store.model = model;
        model.addTreeModelListener(this);

        // SD_sjtree_lazy_provider: lazy live-read provider over the JDK model.
        setDataProvider(new ModelDataProvider());

        // JDK JTree marks the root expanded on model install, so the root's
        // children are visible out of the box.
        expandRootPerJdk();
    }

    private TreeStateStore store() {
        return TreeStateStore.of(this);
    }

    protected TreeSelectionModel createDefaultSelectionModel() {
        return new DefaultTreeSelectionModel();
    }

    // --- Model accessors ---

    public TreeModel getModel() {
        return store().model;
    }

    public void setModel(TreeModel model) {
        Objects.requireNonNull(model, "model must be non null");
        TreeStateStore store = store();
        TreeModel old = store.model;
        if (old == model) return;
        if (old != null) old.removeTreeModelListener(this);
        store.model = model;
        model.addTreeModelListener(this);
        store.parentMap.clear();
        firePropertyChange("model", old, model);
        // Stale paths into the prior model are meaningless against the new
        // one (R_best_effort_behaviour; matches the JDK UI delegate's effective reset).
        store.selectionModel.clearSelection();
        getDataProvider().refreshAll();
        expandRootPerJdk();
    }

    /** JDK JTree expands the root on model install (its initial expanded-state seed). */
    private void expandRootPerJdk() {
        TreeStateStore store = store();
        if (store.rootVisible && store.model != null) {
            Object root = store.model.getRoot();
            if (root != null && !store.model.isLeaf(root)) {
                expand(root);
            }
        }
    }

    // --- rootVisible (data-provider-level reshaping; SD_sjtree_lazy_provider) ---

    public boolean isRootVisible() {
        return store().rootVisible;
    }

    /**
     * {@code false} hides the model root: its children become Vaadin
     * top-level items. The only {@code rootVisible} behaviour reproduced
     * (R_best_effort_behaviour) — the root-handle-on-top-level nuance is {@code setShowsRootHandles},
     * an emulator-side visual shadow per D_jtree_field_shadows.
     */
    public void setRootVisible(boolean rootVisible) {
        TreeStateStore store = store();
        boolean old = store.rootVisible;
        if (old == rootVisible) return;
        store.rootVisible = rootVisible;
        firePropertyChange("rootVisible", old, rootVisible);
        getDataProvider().refreshAll();
        expandRootPerJdk();
    }

    // --- SD_sjtree_hierarchy_render render seam ---

    /**
     * Swap the hierarchy-column's per-node component factory. The installed
     * column closure reads the function live, so no Vaadin renderer
     * re-installs — TreeGrid's expand/collapse toggle chrome survives. The
     * emulator's JDK-{@code TreeCellRenderer} bridge (D_jtree_renderer_registry) is the canonical
     * caller; stage-3 users may install their own factory directly.
     */
    public void setHierarchyComponentProvider(Function<Object, Component> provider) {
        Objects.requireNonNull(provider, "provider must be non null");
        store().componentProvider = provider;
        getDataProvider().refreshAll();
    }

    /**
     * Default cell factory — fresh {@link Span} of {@link #convertValueToText}
     * per call (D_jcombobox DOM-adoption guard).
     */
    private Component defaultRenderCell(Object node) {
        return new Span(convertValueToText(node));
    }

    /**
     * Node-to-text conversion for the default render path. The JDK's
     * overridable {@code JTree.convertValueToText} hook lives on the
     * <em>emulator</em> (D_jtree_renderer_registry) — this surrogate-side conversion only feeds
     * the pure-surrogate default cell.
     */
    public String convertValueToText(Object node) {
        return node == null ? "" : String.valueOf(node);
    }

    // --- SD_sjtree_lazy_provider: lazy live-read HierarchicalDataProvider over the JDK model ---

    private List<Object> roots() {
        TreeStateStore store = store();
        TreeModel m = store.model;
        if (m == null || m.getRoot() == null) return List.of();
        Object root = m.getRoot();
        return store.rootVisible ? List.of(root) : childrenOf(root);
    }

    /** Live child read; records child→parent in the identity map (SD_sjtree_lazy_provider). */
    private List<Object> childrenOf(Object parent) {
        TreeStateStore store = store();
        TreeModel m = store.model;
        if (m == null) return List.of();
        int n = m.getChildCount(parent);
        List<Object> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Object child = m.getChild(parent, i);
            store.parentMap.put(child, parent);
            out.add(child);
        }
        return out;
    }

    private final class ModelDataProvider
            extends AbstractBackEndHierarchicalDataProvider<Object, Void> {

        @Override
        protected Stream<Object> fetchChildrenFromBackEnd(HierarchicalQuery<Object, Void> query) {
            Object parent = query.getParent();
            List<Object> children = parent == null ? roots() : childrenOf(parent);
            return children.stream().skip(query.getOffset()).limit(query.getLimit());
        }

        @Override
        public int getChildCount(HierarchicalQuery<Object, Void> query) {
            TreeModel m = store().model;
            if (m == null) return 0;
            Object parent = query.getParent();
            return parent == null ? roots().size() : m.getChildCount(parent);
        }

        @Override
        public boolean hasChildren(Object item) {
            TreeModel m = store().model;
            return m != null && !m.isLeaf(item);
        }

        /**
         * Required by {@link TreeGrid#scrollToItem} (the default throws for a
         * non-in-memory provider). Vaadin-level parent: {@code null} for
         * top-level items — which includes children of a hidden root.
         */
        @Override
        public Object getParent(Object item) {
            TreeStateStore store = store();
            TreeModel m = store.model;
            if (m == null) return null;
            Object root = m.getRoot();
            if (item == root) return null;
            Object parent = modelParentOf(item);
            return (parent == root && !store.rootVisible) ? null : parent;
        }

        /**
         * Required by {@link TreeGrid#scrollToItem} (the default throws for a
         * non-in-memory provider). NESTED hierarchy format: the index within
         * the parent's children — exactly {@code TreeModel.getIndexOfChild}.
         */
        @Override
        public int getItemIndex(Object item, HierarchicalQuery<Object, Void> query) {
            TreeModel m = store().model;
            if (m == null) return -1;
            Object parent = query.getParent();
            if (parent == null) {
                List<Object> roots = roots();
                for (int i = 0; i < roots.size(); i++) {
                    if (roots.get(i) == item) return i;
                }
                return -1;
            }
            return m.getIndexOfChild(parent, item);
        }
    }

    // --- Parent walk + path reconstruction (SD_sjtree_lazy_provider/SD_sjtree_selection_bridge) ---

    /**
     * The node's model parent — parent-map first (hot path), live model DFS
     * as the cold fallback (a fresh map after {@code treeStructureChanged} /
     * {@code setModel}). {@code null} for the root or an unknown node.
     */
    private Object modelParentOf(Object node) {
        TreeStateStore store = store();
        TreeModel m = store.model;
        if (m == null || node == m.getRoot()) return null;
        Object parent = store.parentMap.get(node);
        if (parent != null) return parent;
        TreePath p = searchPath(m.getRoot(), node);
        if (p != null && p.getParentPath() != null) {
            parent = p.getParentPath().getLastPathComponent();
            store.parentMap.put(node, parent);
            return parent;
        }
        return null;
    }

    /** Identity-keyed DFS through the live model for {@code target}'s path. */
    private TreePath searchPath(Object from, Object target) {
        if (from == null) return null;
        Deque<TreePath> stack = new ArrayDeque<>();
        stack.push(new TreePath(from));
        TreeModel m = store().model;
        while (!stack.isEmpty()) {
            TreePath path = stack.pop();
            Object node = path.getLastPathComponent();
            if (node == target) return path;
            if (!m.isLeaf(node)) {
                int n = m.getChildCount(node);
                for (int i = 0; i < n; i++) {
                    stack.push(path.pathByAddingChild(m.getChild(node, i)));
                }
            }
        }
        return null;
    }

    /**
     * Reconstruct the full JDK {@link TreePath} (always rooted at the model
     * root, even when {@code rootVisible} is false) for a node. Parent-map
     * walk with a live-model DFS fallback; {@code null} when the node isn't
     * reachable from the current model.
     */
    public TreePath getPathForNode(Object node) {
        TreeModel m = store().model;
        if (m == null || node == null) return null;
        Object root = m.getRoot();
        if (node == root) return new TreePath(root);
        Deque<Object> chain = new ArrayDeque<>();
        Object cursor = node;
        while (cursor != null && cursor != root) {
            chain.push(cursor);
            cursor = modelParentOf(cursor);
        }
        if (cursor != root) return null;
        Object[] parts = new Object[chain.size() + 1];
        parts[0] = root;
        int i = 1;
        while (!chain.isEmpty()) parts[i++] = chain.pop();
        return new TreePath(parts);
    }

    // --- TreeModelListener — model events drive Grid refresh (SD_sjtree_is_model_listener) ---
    //
    // The surrogate subscribes itself to the installed model (the JDK UI-
    // delegate pattern); no surrogate-level add/removeTreeModelListener
    // convenience — users go through tree.getModel().addTreeModelListener
    // directly, exactly SD_sjtable_is_model_listener.

    @Override
    public void treeNodesChanged(TreeModelEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: TreeModelListener.treeNodesChanged
        SHelper.runOnOwnerUI(this, () -> {
            Object[] children = e.getChildren();
            if (children == null) {
                // Root changed (DefaultTreeModel.nodeChanged(root) shape).
                refreshNode(lastComponentOf(e));
                return;
            }
            for (Object child : children) {
                refreshNode(child);
            }
        });
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: TreeModelListener.treeNodesInserted
        SHelper.runOnOwnerUI(this, () -> {
            refreshSubtree(lastComponentOf(e));
        });
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: TreeModelListener.treeNodesRemoved
        SHelper.runOnOwnerUI(this, () -> {
            TreeStateStore store = store();
            Object[] removed = e.getChildren();
            if (removed != null) {
                for (Object child : removed) {
                    store.parentMap.remove(child);
                }
            }
            refreshSubtree(lastComponentOf(e));
        });
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: TreeModelListener.treeStructureChanged
        SHelper.runOnOwnerUI(this, () -> {
            store().parentMap.clear();
            getDataProvider().refreshAll();
            expandRootPerJdk();
        });
    }

    private static Object lastComponentOf(TreeModelEvent e) {
        TreePath p = e.getTreePath();
        return p == null ? null : p.getLastPathComponent();
    }

    /** Cell repaint, structure intact. Hidden-root targets widen to refreshAll. */
    private void refreshNode(Object node) {
        if (node == null) return;
        if (isHiddenRoot(node)) {
            getDataProvider().refreshAll();
        } else {
            getDataProvider().refreshItem(node);
        }
    }

    /** Subtree re-fetch under {@code parent} (insert/remove shape). */
    private void refreshSubtree(Object parent) {
        if (parent == null) return;
        if (isHiddenRoot(parent)) {
            // The hidden root isn't a Vaadin item — its children are the
            // top level, so the whole provider refreshes.
            getDataProvider().refreshAll();
        } else {
            getDataProvider().refreshItem(parent, true);
        }
    }

    private boolean isHiddenRoot(Object node) {
        TreeStateStore store = store();
        return !store.rootVisible && store.model != null && node == store.model.getRoot();
    }

    // --- Selection model accessors (getSelectionModel clash, per SD_sjtable/SD_sjtree_selection_bridge) ---

    /**
     * JDK {@code getSelectionModel(): TreeSelectionModel} can't coexist with
     * Vaadin Grid's inherited {@code getSelectionModel(): GridSelectionModel<T>}
     * (unrelated return types). Per R_vaadin_first the Vaadin shape wins at the surrogate;
     * the emulator ({@code :emulators.JTree}) keeps the JDK-shaped getter and
     * reaches the model through this differently-named accessor. Same
     * resolution SJTable / SJList use.
     */
    public TreeSelectionModel getTreeSelectionModel() {
        return store().selectionModel;
    }

    /**
     * Install a different selection model. {@code null} installs a fresh
     * {@link DefaultTreeSelectionModel} (R_best_effort_behaviour close-enough for the JDK's
     * {@code EmptySelectionModel} substitution).
     */
    public void setSelectionModel(TreeSelectionModel selectionModel) {
        if (selectionModel == null) {
            selectionModel = createDefaultSelectionModel();
        }
        TreeStateStore store = store();
        TreeSelectionModel old = store.selectionModel;
        if (old == selectionModel) return;
        if (old != null) {
            old.removeTreeSelectionListener(this);
            old.removePropertyChangeListener(store.selectionModeListener);
        }
        store.selectionModel = selectionModel;
        selectionModel.addTreeSelectionListener(this);
        selectionModel.addPropertyChangeListener(store.selectionModeListener);
        applySelectionModeToGrid();
        firePropertyChange("selectionModel", old, selectionModel);
        // The peer reflects the new model's selection state.
        store.preventPeerEvents = true;
        try {
            pushSelectionToPeer();
        } finally {
            store.preventPeerEvents = false;
        }
    }

    /**
     * Map the JDK selection mode onto Vaadin Grid's:
     * {@code SINGLE_TREE_SELECTION} → SINGLE; both contiguous modes → MULTI
     * (the JDK model still enforces its contract server-side; browser
     * multi-select past a contiguous model silently reconciles — the SJList
     * interval-mode divergence, R_best_effort_behaviour). Re-installs the Vaadin selection
     * listener: Grid re-creates its selection backing on mode swap.
     */
    private void applySelectionModeToGrid() {
        int mode = store().selectionModel.getSelectionMode();
        Grid.SelectionMode vaadinMode = (mode == TreeSelectionModel.SINGLE_TREE_SELECTION)
                ? Grid.SelectionMode.SINGLE
                : Grid.SelectionMode.MULTI;
        super.setSelectionMode(vaadinMode);
        installVaadinSelectionListener();
    }

    // --- Selection bridge (TreeSelectionModel ⇄ Vaadin TreeGrid; SD_sjtree_selection_bridge, R_callswing_envelope) ---

    private void installVaadinSelectionListener() {
        TreeStateStore store = store();
        if (store.vaadinSelectionRegistration != null) {
            store.vaadinSelectionRegistration.remove();
        }
        store.vaadinSelectionRegistration = addSelectionListener(event ->
                SHelper.callSwing(this::syncSelectionFromPeer));
    }

    private void syncSelectionFromPeer() {
        TreeStateStore store = store();
        if (store.preventPeerEvents) return;
        Set<Object> selected = getSelectedItems();
        store.preventPeerEvents = true;
        try {
            if (selected.isEmpty()) {
                store.selectionModel.clearSelection();
                return;
            }
            List<TreePath> paths = new ArrayList<>(selected.size());
            for (Object node : selected) {
                TreePath p = getPathForNode(node);
                if (p != null) paths.add(p);
            }
            store.selectionModel.setSelectionPaths(paths.toArray(new TreePath[0]));
        } finally {
            store.preventPeerEvents = false;
        }
    }

    /** JDK→peer push: the installed {@link TreeSelectionModel} changed. */
    @Override
    public void valueChanged(TreeSelectionEvent e) {
        // Allowed by R_tolerate_off_ui_thread because callback from model: TreeSelectionListener.valueChanged
        SHelper.runOnOwnerUI(this, () -> {
            TreeStateStore store = store();
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
        TreePath[] paths = store().selectionModel.getSelectionPaths();
        if (paths == null) return;
        for (TreePath p : paths) {
            select(p.getLastPathComponent());
        }
    }

    // --- Selection API (route through the TreeSelectionModel) ---

    public void setSelectionPath(TreePath path) {
        store().selectionModel.setSelectionPath(path);
    }

    public void setSelectionPaths(TreePath[] paths) {
        store().selectionModel.setSelectionPaths(paths);
    }

    public void addSelectionPath(TreePath path) {
        store().selectionModel.addSelectionPath(path);
    }

    public void removeSelectionPath(TreePath path) {
        store().selectionModel.removeSelectionPath(path);
    }

    public TreePath getSelectionPath() {
        return store().selectionModel.getSelectionPath();
    }

    public TreePath[] getSelectionPaths() {
        return store().selectionModel.getSelectionPaths();
    }

    public int getSelectionCount() {
        return store().selectionModel.getSelectionCount();
    }

    public boolean isPathSelected(TreePath path) {
        return store().selectionModel.isPathSelected(path);
    }

    public boolean isSelectionEmpty() {
        return store().selectionModel.isSelectionEmpty();
    }

    public void clearSelection() {
        store().selectionModel.clearSelection();
    }

    public Object getLastSelectedPathComponent() {
        TreePath p = store().selectionModel.getSelectionPath();
        return p == null ? null : p.getLastPathComponent();
    }

    // --- Expansion bridge (SD_sjtree_expansion) ---

    /** Ensures the node at {@code path} is expanded and its ancestors are too (JDK shape). */
    public void expandPath(TreePath path) {
        if (path == null) return;
        TreeModel m = store().model;
        if (m == null) return;
        List<Object> toExpand = new ArrayList<>();
        for (Object part : path.getPath()) {
            if (!m.isLeaf(part) && !isHiddenRoot(part)) {
                toExpand.add(part);
            }
        }
        if (!toExpand.isEmpty()) expand(toExpand);
    }

    /** Collapses the node at {@code path} (ancestors stay as-is, JDK shape). */
    public void collapsePath(TreePath path) {
        if (path == null) return;
        Object node = path.getLastPathComponent();
        if (!isHiddenRoot(node)) {
            collapse(List.of(node));
        }
    }

    /**
     * JDK contract: true only when the node is expanded <em>and</em> every
     * ancestor is expanded (i.e. the expansion is actually displayed).
     */
    public boolean isExpanded(TreePath path) {
        if (path == null) return false;
        Object[] parts = path.getPath();
        for (Object part : parts) {
            if (!isHiddenRoot(part) && !isExpanded(part)) return false;
        }
        return true;
    }

    public boolean isCollapsed(TreePath path) {
        return !isExpanded(path);
    }

    /** Expands the path's ancestors so the node itself is displayable. */
    public void makeVisible(TreePath path) {
        if (path == null) return;
        TreePath parent = path.getParentPath();
        if (parent != null) expandPath(parent);
    }

    /** True when every ancestor of {@code path} is expanded. */
    public boolean isVisible(TreePath path) {
        if (path == null) return false;
        TreePath parent = path.getParentPath();
        return parent == null || isExpanded(parent);
    }

    /**
     * Expand ancestors (via the public {@code expand()}, firing the JDK
     * {@code TreeExpansionEvent} fan-out) then {@link #scrollToItem} — D_jtree_row_path_bridge:
     * {@code scrollToItem}'s own built-in ancestor expansion fires no
     * {@code ExpandEvent} and would silently skip listeners. No pixel scroll
     * guarantee (R_layouts_close_enough).
     */
    public void scrollPathToVisible(TreePath path) {
        if (path == null) return;
        makeVisible(path);
        Object node = path.getLastPathComponent();
        if (!isHiddenRoot(node)) {
            try {
                scrollToItem(node);
            } catch (RuntimeException ex) {
                // Node not reachable in the current tree — JDK silently
                // ignores unknown paths here too (R_best_effort_behaviour).
            }
        }
    }

    /**
     * Expanded descendants of {@code parent} (including {@code parent}
     * itself, JDK shape); {@code null} when {@code parent} isn't expanded.
     * Reflects TreeGrid's lazy expansion state: descendants of
     * never-expanded nodes report collapsed (R_best_effort_behaviour).
     */
    public List<TreePath> getExpandedDescendants(TreePath parent) {
        if (parent == null || !isExpanded(parent)) return null;
        List<TreePath> out = new ArrayList<>();
        collectExpanded(parent, out);
        return out;
    }

    private void collectExpanded(TreePath path, List<TreePath> out) {
        out.add(path);
        TreeModel m = store().model;
        Object node = path.getLastPathComponent();
        if (m == null || m.isLeaf(node)) return;
        int n = m.getChildCount(node);
        for (int i = 0; i < n; i++) {
            Object child = m.getChild(node, i);
            store().parentMap.put(child, node);
            if (!m.isLeaf(child) && isExpanded(child)) {
                collectExpanded(path.pathByAddingChild(child), out);
            }
        }
    }

    /**
     * Per-registration TreeExpansion wire: one Vaadin expand + one collapse
     * subscription per Swing listener, combined into one {@link Registration}
     * (R_vaadin_first's per-registration shape). Fires for both client and programmatic
     * expands — matching JDK's {@code expandPath} firing semantics.
     */
    public void addTreeExpansionListener(TreeExpansionListener l) {
        Objects.requireNonNull(l);
        Registration ex = addExpandListener(event -> SHelper.callSwing(() -> {
            for (Object item : event.getItems()) {
                l.treeExpanded(new TreeExpansionEvent(this, getPathForNode(item)));
            }
        }));
        Registration col = addCollapseListener(event -> SHelper.callSwing(() -> {
            for (Object item : event.getItems()) {
                l.treeCollapsed(new TreeExpansionEvent(this, getPathForNode(item)));
            }
        }));
        Registrations.of(this).add(l, Registration.combine(ex, col));
    }

    public void removeTreeExpansionListener(TreeExpansionListener l) {
        Registrations.of(this).remove(l);
    }

    public TreeExpansionListener[] getTreeExpansionListeners() {
        return Registrations.of(this).getListeners(TreeExpansionListener.class);
    }

    /**
     * R_vaadin_first drop-and-WARN — D_gap_severity_triage sub-bucket (a) blocked-upstream: Vaadin has no
     * pre-expansion vetoable hook, so the listener would never fire. The
     * emulator owns expansion and fires it (D_jtree_row_path_bridge); here it drops.
     */
    public void addTreeWillExpandListener(TreeWillExpandListener l) {
        SHelper.onUnimplemented(this, "addTreeWillExpandListener", l);
    }

    /** See {@link #addTreeWillExpandListener}. */
    public void removeTreeWillExpandListener(TreeWillExpandListener l) {
        SHelper.onUnimplemented(this, "removeTreeWillExpandListener", l);
    }

    // --- Row-based API over the visible-node walk (SD_sjtree_row_api; R_layouts_close_enough) ---

    /**
     * The flattened <em>visible</em> (expanded) node order — a DFS from the
     * top level descending only into expanded nodes. Matches what TreeGrid
     * displays; recomputed per call (live model read, R_swing_is_truth).
     */
    private List<Object> visibleNodes() {
        List<Object> out = new ArrayList<>();
        for (Object root : roots()) {
            walkVisible(root, out);
        }
        return out;
    }

    private void walkVisible(Object node, List<Object> out) {
        out.add(node);
        TreeModel m = store().model;
        if (m == null || m.isLeaf(node) || !isExpanded(node)) return;
        for (Object child : childrenOf(node)) {
            walkVisible(child, out);
        }
    }

    public int getRowCount() {
        return visibleNodes().size();
    }

    public TreePath getPathForRow(int row) {
        List<Object> visible = visibleNodes();
        if (row < 0 || row >= visible.size()) return null;
        return getPathForNode(visible.get(row));
    }

    /** -1 when an ancestor is collapsed (the node isn't visible). */
    public int getRowForPath(TreePath path) {
        if (path == null) return -1;
        return getRowForNode(path.getLastPathComponent());
    }

    /** Visible-row index of a node by identity; -1 when not visible. */
    public int getRowForNode(Object node) {
        List<Object> visible = visibleNodes();
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i) == node) return i;
        }
        return -1;
    }

    // --- Pixel hit-testing via the click stash (SD_sjtree_row_api; R_layouts_close_enough) ---

    /**
     * Returns the visible-row index currently being click-dispatched
     * (stashed by {@link #addMouseListener}'s item-click wire), or
     * {@code -1} outside a dispatch. There is no server-side pixel→row map
     * (R_layouts_close_enough) — called from a user {@code mousePressed}/{@code mouseClicked}
     * handler (the {@code CaseTreeView.selectOnRightClick} idiom) it
     * returns the clicked row.
     */
    public int getRowForLocation(int x, int y) {
        return store().clickRowStash;
    }

    /** Path twin of {@link #getRowForLocation}. */
    public TreePath getPathForLocation(int x, int y) {
        Object node = store().clickNodeStash;
        return node == null ? null : getPathForNode(node);
    }

    /** Same stash answer — we have no "closest" notion without pixels (R_layouts_close_enough). */
    public TreePath getClosestPathForLocation(int x, int y) {
        return getPathForLocation(x, y);
    }

    /** See {@link #getClosestPathForLocation}. */
    public int getClosestRowForLocation(int x, int y) {
        return getRowForLocation(x, y);
    }

    // --- Mouse bridge: item-click → SMouseEvent press/release/click ---

    /**
     * Overrides the inherited {@code ComponentMixin} wire — TreeGrid
     * dispatches <em>item</em> clicks (carrying the node), not generic
     * component clicks. Each item-click dispatches the
     * {@code MOUSE_PRESSED} → {@code MOUSE_RELEASED} → {@code MOUSE_CLICKED}
     * sequence (best-effort R_best_effort_behaviour: the browser reports a click, not the raw
     * press/release stream), carrying the browser button so right-click →
     * {@link SMouseEvent#isPopupTrigger()}. {@code popupTrigger} is set on
     * both press and release for a right-click — we can't know the host
     * platform's convention, and firing on both serves listeners written
     * for either (the {@code CaseTreeView.selectOnRightClick} idiom listens
     * on both; the duplicate select is idempotent). The clicked node + its
     * visible row are stashed around the fan-out for
     * {@link #getRowForLocation}/{@link #getPathForLocation}.
     */
    @Override
    public void addMouseListener(SMouseListener l) {
        Objects.requireNonNull(l);
        Registration click = addItemClickListener(e -> dispatchMouse(l, e, e.getClickCount()));
        Registration dbl = addItemDoubleClickListener(e -> dispatchMouse(l, e, 2));
        Registrations.of(this).add(l, Registration.combine(click, dbl));
    }

    private void dispatchMouse(SMouseListener l, ItemClickEvent<Object> e, int clickCount) {
        SHelper.callSwing(() -> {
            TreeStateStore store = store();
            Object prevNode = store.clickNodeStash;
            int prevRow = store.clickRowStash;
            store.clickNodeStash = e.getItem();
            store.clickRowStash = e.getItem() == null ? -1 : getRowForNode(e.getItem());
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
                boolean popupTrigger = button == SMouseEvent.BUTTON3;
                long when = System.currentTimeMillis();
                int x = e.getClientX();
                int y = e.getClientY();
                l.mousePressed(new SMouseEvent(this, SMouseEvent.MOUSE_PRESSED,
                        when, modifiers, x, y, clickCount, popupTrigger, button));
                l.mouseReleased(new SMouseEvent(this, SMouseEvent.MOUSE_RELEASED,
                        when, modifiers, x, y, clickCount, popupTrigger, button));
                l.mouseClicked(new SMouseEvent(this, SMouseEvent.MOUSE_CLICKED,
                        when, modifiers, x, y, clickCount, popupTrigger, button));
            } finally {
                store.clickNodeStash = prevNode;
                store.clickRowStash = prevRow;
            }
        });
    }

    // --- L&F stub (JDK contract) ---

    public String getUIClassID() {
        return "TreeUI";
    }
}
