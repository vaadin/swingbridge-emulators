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
 * This file is derived from OpenJDK's javax.swing.JTree
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JTree. Thin
// delegating shell over com.vaadin.swingbridge.surrogates.SJTree, which carries the
// TreeGrid<Object> + TreeModel-as-source-of-truth + lazy
// HierarchicalDataProvider + TreeSelectionModel path-bridge + expansion
// bridge + item-click→SMouseEvent wire (SD_sjtree).
//
// Two-layer pattern matches JList / JTable (D_jtree_two_layer_resource): the emulator re-sources
// the surrogate's events into its own listenerList with the source rebound
// to `this`. Three re-source bridges installed in the ctor:
//   1. TreeSelectionListener — a handler on the surrogate's
//      TreeSelectionModel re-fires through fireValueChanged with
//      TreeSelectionEvent.cloneWithSource(this), so
//      tree.addTreeSelectionListener sees source = this JTree (the
//      CaseTreeView.fireCaseSelected path). setSelectionModel moves the
//      handler to the new model.
//   2. Expansion — owned here, not bridged: the JDK's expandedState table,
//      setExpandedState (TreeWillExpand veto, then TreeExpansion) and
//      TreeModelHandler are this class's, and rows are a walk of the model
//      under that table, so no expansion or row getter reads the peer. The
//      table is flushed to the TreeGrid after every change
//      (syncPeerExpansion); a browser toggle comes back through TreeGrid's
//      client-originated expand/collapse events into setExpandedState, so a
//      veto re-collapses the node.
//   3. SMouseListener — the surrogate's item-click → MOUSE_PRESSED /
//      MOUSE_RELEASED / MOUSE_CLICKED (carrying the browser button, so
//      right-click → isPopupTrigger()) re-sources to vaadinx.awt
//      MouseEvents dispatched via processMouseEvent; getRowForLocation
//      reads the surrogate's click stash (the CaseTreeView
//      selectOnRightClick idiom).
//
// Renderer bridge (D_jtree_renderer_registry): the JDK TreeCellRenderer surface lives here; the
// ctor swaps the surrogate's hierarchy-column componentProvider function
// once (SD_sjtree_hierarchy_render — the function seam preserves HierarchyColumnComponentRenderer's
// vaadin-grid-tree-toggle chrome, which a renderer re-install would drop).
// The closure resolves the current cellRenderer per render call and
// snapshots its output via JComboBox.snapshotRendererOutput.
//
// R_leaf_peer_lockdown lock-down: javax.swing.JTree is a leaf in the public Swing
// hierarchy, so the protected (Component peer) ctor is omitted; all public
// ctors funnel through the private (SJTree) ctor.

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.shared.Registration;
import com.vaadin.swingbridge.surrogates.SJTree;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseListener;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.RowMapper;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Emulator for {@link javax.swing.JTree} — a thin delegating shell over its
 * {@link SJTree} peer; R_leaf_peer_lockdown-locked-down (JDK leaf — private peer ctor).
 * Implements {@link javax.swing.Scrollable} and
 * {@link javax.accessibility.Accessible} to match the JDK interface set.
 */
public class JTree extends vaadinx.swing.JComponent
        implements javax.swing.Scrollable, javax.accessibility.Accessible, vaadinx.FieldReconciler.Reconcilable {

    // --- JDK bound-property names (migrated PCE listeners filter on these) ---

    public static final String CELL_RENDERER_PROPERTY = "cellRenderer";
    public static final String TREE_MODEL_PROPERTY = "model";
    public static final String ROOT_VISIBLE_PROPERTY = "rootVisible";
    public static final String SHOWS_ROOT_HANDLES_PROPERTY = "showsRootHandles";
    public static final String ROW_HEIGHT_PROPERTY = "rowHeight";
    public static final String CELL_EDITOR_PROPERTY = "cellEditor";
    public static final String EDITABLE_PROPERTY = "editable";
    public static final String LARGE_MODEL_PROPERTY = "largeModel";
    public static final String SELECTION_MODEL_PROPERTY = "selectionModel";
    public static final String VISIBLE_ROW_COUNT_PROPERTY = "visibleRowCount";
    public static final String INVOKES_STOP_CELL_EDITING_PROPERTY = "invokesStopCellEditing";
    public static final String SCROLLS_ON_EXPAND_PROPERTY = "scrollsOnExpand";
    public static final String TOGGLE_CLICK_COUNT_PROPERTY = "toggleClickCount";
    public static final String LEAD_SELECTION_PATH_PROPERTY = "leadSelectionPath";
    public static final String ANCHOR_SELECTION_PATH_PROPERTY = "anchorSelectionPath";
    public static final String EXPANDS_SELECTED_PATHS_PROPERTY = "expandsSelectedPaths";

    /** Currently installed JDK-shaped cell renderer; seeded with DefaultTreeCellRenderer. */
    protected vaadinx.swing.tree.TreeCellRenderer cellRenderer;

    /**
     * Handler installed on the surrogate's selection model that re-sources
     * selection events to the emulator's own listenerList with source =
     * this JTree (the JDK {@code JTree.TreeSelectionRedirector} shape), and
     * then plays the part {@code BasicTreeUI} plays in keeping the anchor and
     * lead paths in step — see {@link #syncAnchorAndLeadFromSelection()}.
     */
    private final TreeSelectionListener selectionHandler = e ->
            vaadinx.EHelper.relayModelEvent(() -> {
                fireValueChanged((TreeSelectionEvent) e.cloneWithSource(JTree.this));
                syncAnchorAndLeadFromSelection();
            });

    /**
     * Writes the anchor and then the lead path from the selection model's lead —
     * {@code BasicTreeUI}'s job in the JDK, done from some thirty sites, and
     * nobody's here otherwise, L&amp;F dispatch being out of scope
     * (R_match_swing_errors sub-bucket (b)).
     *
     * <p>Its call site is <em>after</em> {@code fireValueChanged} because on
     * JDK 25 a {@code TreeSelectionListener} runs first and reads the
     * <em>previous</em> lead (measured); reproducing that stale read is
     * R_no_silent_improvements.
     *
     * <p>Faithful for a single selection only: the JDK's shift- and
     * ctrl-gestures anchor on the <em>old</em> lead, which needs the
     * gesture rather than the resulting selection, so a range
     * selection lands both paths on the model's lead
     * (R_match_swing_errors sub-bucket (c)).
     */
    private void syncAnchorAndLeadFromSelection() {
        TreePath lead = getSelectionModel().getLeadSelectionPath();
        setAnchorSelectionPath(lead);
        setLeadSelectionPath(lead);
    }

    /**
     * RowMapper installed on the selection model so its row-based queries
     * ({@code getSelectionRows}, {@code getMinSelectionRow}, …) resolve over
     * the surrogate's visible-node walk — the role the JDK's layout cache
     * plays for BasicTreeUI.
     */
    private final RowMapper rowMapper = paths -> {
        if (paths == null) return null;
        int[] rows = new int[paths.length];
        for (int i = 0; i < paths.length; i++) {
            rows[i] = getRowForPath(paths[i]);
        }
        return rows;
    };

    /**
     * The JDK's toggled-paths table: every path ever expanded or collapsed, mapped to whether
     * it is expanded now. {@link #isExpanded(TreePath)} walks it up the ancestors, and the
     * visible rows are a walk of the model under it.
     */
    private transient Hashtable<TreePath, Boolean> expandedState = new Hashtable<>();

    /**
     * The nodes this tree last told the peer, or heard from it, are expanded: the baseline
     * {@link #syncPeerExpansion} diffs {@link #expandedState} against.
     */
    private final Set<Object> peerExpanded = new HashSet<>();

    /** The JDK hook's listener, subscribed to {@link #treeModel}; keeps {@link #expandedState} in step with the model. */
    protected transient javax.swing.event.TreeModelListener treeModelListener;

    // --- Ctors (all JDK forms; R_leaf_peer_lockdown: funnel through the private peer ctor) ---

    /** JDK shape: shows the sample demo model. */
    public JTree() {
        this(getDefaultTreeModel());
    }

    public JTree(Object[] value) {
        this(createTreeModel(value));
        setRootVisible(false);
        setShowsRootHandles(true);
    }

    public JTree(Vector<?> value) {
        this(createTreeModel(value));
        setRootVisible(false);
        setShowsRootHandles(true);
    }

    public JTree(Hashtable<?, ?> value) {
        this(createTreeModel(value));
        setRootVisible(false);
        setShowsRootHandles(true);
    }

    public JTree(TreeNode root) {
        this(root, false);
    }

    public JTree(TreeNode root, boolean asksAllowsChildren) {
        this(new DefaultTreeModel(root, asksAllowsChildren));
    }

    public JTree(TreeModel newModel) {
        this(new SJTree(newModel));
    }

    /** Private ctor: R_leaf_peer_lockdown lock-down (JDK leaf — no protected (Component) ctor). */
    private JTree(SJTree peer) {
        super(peer);
        this.cellRenderer = new vaadinx.swing.tree.DefaultTreeCellRenderer();
        installEmulatorVaadinRenderer();
        installSurrogateBridges();
        installEditorBridge();
        // Seed the JDK-shaped fields (and write-detection baselines) from the peer the
        // public ctors configured (D_field_write_reconcile; see JSlider), read while it has
        // never been attached.
        treeModel = peer.getModel();
        selectionModel = peer.getTreeSelectionModel();
        rootVisible = pushedRootVisible = peer.isRootVisible();
        // The JDK ctor's setModel: subscribe the model handler, mark a non-leaf root expanded.
        treeModelListener = createTreeModelListener();
        if (treeModelListener != null) treeModel.addTreeModelListener(treeModelListener);
        expandRootIfNotLeaf();
        syncPeerExpansion();
        vaadinx.FieldReconciler.register(this, peer);
    }

    // JDK protected fields, Swing-side truth per D_field_write_reconcile (see JSlider for
    // the canonical commentary). Both models are second pointers to the objects the
    // surrogate's machinery runs on; rootVisible write-throughs to the data-provider reshape.
    protected transient TreeModel treeModel;
    protected transient TreeSelectionModel selectionModel;
    protected boolean rootVisible;

    // Last value pushed to the peer — reconcileFields()'s write-detection baseline.
    private boolean pushedRootVisible;

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (treeModel != surrogate().getModel()) {
            TreeModel written = treeModel;
            treeModel = surrogate().getModel();
            setModel(written);
            vaadinx.FieldReconciler.reportDirectWrite(this, "treeModel", "setModel");
        }
        if (selectionModel != surrogate().getTreeSelectionModel()) {
            // Route through the emulator's own setter — it rewires selectionHandler +
            // rowMapper, which a raw surrogate push would leave on the old model. The field
            // is first pointed back at the still-installed model so the setter unhooks the
            // right instance (the direct write left the field ahead of the wiring).
            TreeSelectionModel written = selectionModel;
            selectionModel = surrogate().getTreeSelectionModel();
            setSelectionModel(written);
            vaadinx.FieldReconciler.reportDirectWrite(this, "selectionModel", "setSelectionModel");
        }
        if (rootVisible != pushedRootVisible) {
            surrogate().setRootVisible(rootVisible);
            pushedRootVisible = rootVisible;
            syncPeerExpansion();
            vaadinx.FieldReconciler.reportDirectWrite(this, "rootVisible", "setRootVisible");
        }
    }

    private SJTree surrogate() {
        return (SJTree) getPeer();
    }

    // --- JDK static model builders ---

    /**
     * JDK's sample model ({@code javax.swing.JTree.getDefaultTreeModel()}):
     * the "JTree" root with colors / sports / food children.
     */
    protected static TreeModel getDefaultTreeModel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("JTree");

        DefaultMutableTreeNode colors = new DefaultMutableTreeNode("colors");
        root.add(colors);
        colors.add(new DefaultMutableTreeNode("blue"));
        colors.add(new DefaultMutableTreeNode("violet"));
        colors.add(new DefaultMutableTreeNode("red"));
        colors.add(new DefaultMutableTreeNode("yellow"));

        DefaultMutableTreeNode sports = new DefaultMutableTreeNode("sports");
        root.add(sports);
        sports.add(new DefaultMutableTreeNode("basketball"));
        sports.add(new DefaultMutableTreeNode("soccer"));
        sports.add(new DefaultMutableTreeNode("football"));
        sports.add(new DefaultMutableTreeNode("hockey"));

        DefaultMutableTreeNode food = new DefaultMutableTreeNode("food");
        root.add(food);
        food.add(new DefaultMutableTreeNode("hot dogs"));
        food.add(new DefaultMutableTreeNode("pizza"));
        food.add(new DefaultMutableTreeNode("ravioli"));
        food.add(new DefaultMutableTreeNode("bananas"));

        return new DefaultTreeModel(root);
    }

    /**
     * JDK's {@code createTreeModel} shape: wrap the entries under a synthetic
     * "root" node (hidden by the calling ctor's {@code setRootVisible(false)}).
     * Nested arrays / Vectors / Hashtables recurse — the eager equivalent of
     * the JDK's lazy {@code DynamicUtilTreeNode} expansion (R_best_effort_behaviour).
     */
    protected static TreeModel createTreeModel(Object value) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        addChildrenFor(root, value);
        return new DefaultTreeModel(root, false);
    }

    private static void addChildrenFor(DefaultMutableTreeNode parent, Object value) {
        if (value instanceof Object[] array) {
            for (Object child : array) addChildFor(parent, child);
        } else if (value instanceof Vector<?> vector) {
            for (Object child : vector) addChildFor(parent, child);
        } else if (value instanceof Map<?, ?> map) {  // JDK accepts Hashtable
            for (Object key : map.keySet()) addChildFor(parent, key);
        }
    }

    private static void addChildFor(DefaultMutableTreeNode parent, Object child) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(child);
        parent.add(node);
        addChildrenFor(node, child);
    }

    // --- D_jtree_renderer_registry renderer bridge (function seam per SD_sjtree_hierarchy_render) ---

    /**
     * Swap the surrogate's hierarchy-column componentProvider once. The
     * closure reads {@link #cellRenderer} live, so {@link #setCellRenderer}
     * only needs to refresh — no re-install, and the tree-toggle chrome
     * survives (it lives in HierarchyColumnComponentRenderer's template).
     */
    private void installEmulatorVaadinRenderer() {
        surrogate().setHierarchyComponentProvider(this::renderCellViaEmulator);
    }

    private com.vaadin.flow.component.Component renderCellViaEmulator(Object node) {
        if (node == null) return new Span("");
        if (cellRenderer == null) {
            return new Span(convertValueToText(node, false, false, false, -1, false));
        }
        SJTree s = surrogate();
        boolean isSelected = s.getSelectedItems().contains(node);
        TreePath path = s.getPathForNode(node);
        boolean expanded = path != null && Boolean.TRUE.equals(expandedState.get(path));
        TreeModel m = getModel();
        boolean leaf = m != null && m.isLeaf(node);
        // O(visible rows) per cell; bounded by TreeGrid's viewport paging.
        int row = getRowForPath(path);
        vaadinx.awt.Component out = cellRenderer.getTreeCellRendererComponent(
                this, node, isSelected, expanded, leaf, row, /*hasFocus*/ false);
        return JComboBox.snapshotRendererOutput(out, node);
    }

    private void installSurrogateBridges() {
        // (0) This class sources its own MOUSE_* below, off the surrogate's Grid
        //     item-click wire, which also stashes the row for getRowForLocation.
        //     Keep Component's generic DOM bridge out, or every click arrives twice.
        suppressMouseBridge();
        // (1) Selection re-source — source rebound to this JTree.
        surrogate().getTreeSelectionModel().addTreeSelectionListener(selectionHandler);
        surrogate().getTreeSelectionModel().setRowMapper(rowMapper);
        // (2) Browser toggles. The peer has already expanded or collapsed the node; the JDK's
        //     path runs here as for a click on a desktop toggle, veto included, and the flush
        //     undoes a vetoed one. Server-side expand / collapse are this class's own pushes.
        surrogate().addExpandListener(e -> {
            if (!e.isFromClient()) return;
            vaadinx.EHelper.callSwing(() -> onPeerToggle(e.getItems(), true));
        });
        surrogate().addCollapseListener(e -> {
            if (!e.isFromClient()) return;
            vaadinx.EHelper.callSwing(() -> onPeerToggle(e.getItems(), false));
        });
        // (3) Mouse re-source — press/release/click carrying the browser
        //     button, so isPopupTrigger() + getRowForLocation serve the
        //     selectOnRightClick idiom.
        surrogate().addMouseListener(new SMouseListener() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                redispatch(e, vaadinx.awt.event.MouseEvent.MOUSE_CLICKED);
            }

            @Override
            public void mousePressed(SMouseEvent e) {
                redispatch(e, vaadinx.awt.event.MouseEvent.MOUSE_PRESSED);
            }

            @Override
            public void mouseReleased(SMouseEvent e) {
                redispatch(e, vaadinx.awt.event.MouseEvent.MOUSE_RELEASED);
            }

            @Override
            public void mouseEntered(SMouseEvent e) { /* not produced by the item-click wire */ }

            @Override
            public void mouseExited(SMouseEvent e) { /* not produced by the item-click wire */ }
        });
    }

    /**
     * The clicked path, while a mouse event it caused is being dispatched: what stands
     * in for the pixel hit test of {@link #getClosestPathForLocation} (R_layouts_close_enough).
     * Held for the whole dispatch, so a handler that parks on a modal dialog still sees it
     * after resuming.
     */
    private TreePath clickPathStash;

    private void redispatch(SMouseEvent e, int id) {
        // Read inside the surrogate's item-click dispatch, the one place its stash holds the node.
        TreePath clicked = surrogate().getPathForLocation(e.getX(), e.getY());
        vaadinx.EHelper.callSwing(() -> {
            TreePath previous = clickPathStash;
            clickPathStash = clicked;
            try {
                processMouseEvent(new vaadinx.awt.event.MouseEvent(JTree.this, id,
                        e.getWhen(), e.getModifiersEx(), e.getX(), e.getY(),
                        e.getClickCount(), e.isPopupTrigger(), e.getButton()));
            } finally {
                clickPathStash = previous;
            }
        });
    }

    /**
     * Wire the surrogate's inherited {@code TreeGrid.Editor} for rename-in-place
     * (D_jtree_rename_in_place). A binding-less {@link Binder} satisfies Vaadin's "editor needs a
     * binder" requirement (it throws otherwise); non-buffered so a blur
     * closes-and-commits. A double-click opens the editor when the tree is
     * {@link #isEditable}. Per-open Escape/Enter keys are wired in
     * {@link #startEditingAtPath} (the JTable B9 finding).
     */
    private void installEditorBridge() {
        SJTree s = surrogate();
        s.getEditor().setBinder(new Binder<>());
        s.getEditor().setBuffered(false);
        s.getEditor().addCloseListener(e -> vaadinx.EHelper.callSwing(this::onVaadinEditorClosed));
        s.getEditor().addCancelListener(e -> vaadinx.EHelper.callSwing(this::onVaadinEditorCancelled));
        // Double-click a node opens the rename editor — browser-faithful trigger.
        // Desktop Swing uses a select-then-slow-click timer instead; double-click
        // is an accepted R_best_effort_behaviour divergence, and collision-free because TreeGrid
        // expands on the toggle arrow, not on a row double-click (gate-b spike).
        s.addItemDoubleClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            if (!isEditable() || e.getItem() == null) return;
            TreePath path = s.getPathForNode(e.getItem());
            if (path != null) startEditingAtPath(path);
        }));
    }

    // --- Model accessors ---

    public TreeModel getModel() {
        return treeModel;
    }

    public void setModel(TreeModel newModel) {
        clearSelection();
        TreeModel old = treeModel;
        if (old != null && treeModelListener != null) old.removeTreeModelListener(treeModelListener);
        treeModel = newModel;
        clearToggledPaths();
        if (newModel != null) {
            if (treeModelListener == null) treeModelListener = createTreeModelListener();
            if (treeModelListener != null) newModel.addTreeModelListener(treeModelListener);
            expandRootIfNotLeaf();
        }
        withPeer(p -> surrogate().setModel(newModel));
        syncPeerExpansion();
        firePropertyChange(TREE_MODEL_PROPERTY, old, newModel);
    }

    /** The JDK's {@code setModel} step: a non-leaf root is marked expanded, whether or not it is visible. */
    private void expandRootIfNotLeaf() {
        Object treeRoot = treeModel.getRoot();
        if (treeRoot != null && !treeModel.isLeaf(treeRoot)) {
            expandedState.put(new TreePath(treeRoot), Boolean.TRUE);
        }
    }

    // --- Cell renderer (JDK shape; bridges to Vaadin via the surrogate) ---

    public vaadinx.swing.tree.TreeCellRenderer getCellRenderer() {
        return cellRenderer;
    }

    public void setCellRenderer(vaadinx.swing.tree.TreeCellRenderer x) {
        vaadinx.swing.tree.TreeCellRenderer old = this.cellRenderer;
        this.cellRenderer = x;
        // The dynamic closure reads the field live — refresh repaints.
        withPeer(p -> surrogate().getDataProvider().refreshAll());
        firePropertyChange(CELL_RENDERER_PROPERTY, old, x);
    }

    /**
     * The JDK-overridable node-to-text formatting hook the default
     * renderer's text flows through. Subclasses overriding this (a common
     * migration pattern) take effect on every rendered cell.
     */
    public String convertValueToText(Object value, boolean selected, boolean expanded,
                                     boolean leaf, int row, boolean hasFocus) {
        if (value != null) {
            String sValue = value.toString();
            if (sValue != null) return sValue;
        }
        return "";
    }

    // --- rootVisible (delegates — drives the surrogate's data-provider reshaping) ---

    public boolean isRootVisible() {
        return rootVisible;
    }

    public void setRootVisible(boolean rootVisible) {
        boolean old = this.rootVisible;
        if (old == rootVisible) return;
        this.rootVisible = rootVisible;
        withPeer(p -> surrogate().setRootVisible(rootVisible));
        pushedRootVisible = rootVisible;
        // The surrogate re-expands a shown root on its own; put the peer back in step.
        syncPeerExpansion();
        firePropertyChange(ROOT_VISIBLE_PROPERTY, old, rootVisible);
    }

    // --- Selection model ---

    public TreeSelectionModel getSelectionModel() {
        // The surrogate can't carry the JDK-shaped name: TreeGrid inherits
        // Grid.getSelectionModel(): GridSelectionModel (SD_sjtree_selection_bridge). The emulator
        // isn't a Grid, so the JDK shape (and the field) lives here.
        return selectionModel;
    }

    public void setSelectionModel(TreeSelectionModel selectionModel) {
        TreeSelectionModel old = this.selectionModel;
        if (old == selectionModel) return;
        old.removeTreeSelectionListener(selectionHandler);
        old.setRowMapper(null);
        withPeer(p -> surrogate().setSelectionModel(selectionModel));
        // The surrogate substitutes a default for null (as the JDK's EmptySelectionModel
        // does), so the field tracks what actually got installed, not the argument.
        TreeSelectionModel installed = surrogate().getTreeSelectionModel();
        this.selectionModel = installed;
        installed.addTreeSelectionListener(selectionHandler);
        installed.setRowMapper(rowMapper);
        firePropertyChange(SELECTION_MODEL_PROPERTY, old, installed);
    }

    // --- Selection API (path-based; route through the selection model) ---

    public void setSelectionPath(TreePath path) {
        getSelectionModel().setSelectionPath(path);
    }

    public void setSelectionPaths(TreePath[] paths) {
        getSelectionModel().setSelectionPaths(paths);
    }

    public void addSelectionPath(TreePath path) {
        getSelectionModel().addSelectionPath(path);
    }

    public void addSelectionPaths(TreePath[] paths) {
        getSelectionModel().addSelectionPaths(paths);
    }

    public void removeSelectionPath(TreePath path) {
        getSelectionModel().removeSelectionPath(path);
    }

    public void removeSelectionPaths(TreePath[] paths) {
        getSelectionModel().removeSelectionPaths(paths);
    }

    public TreePath getSelectionPath() {
        return getSelectionModel().getSelectionPath();
    }

    public TreePath[] getSelectionPaths() {
        return getSelectionModel().getSelectionPaths();
    }

    public int getSelectionCount() {
        return getSelectionModel().getSelectionCount();
    }

    public boolean isPathSelected(TreePath path) {
        return getSelectionModel().isPathSelected(path);
    }

    public boolean isSelectionEmpty() {
        return getSelectionModel().isSelectionEmpty();
    }

    public void clearSelection() {
        getSelectionModel().clearSelection();
    }

    public Object getLastSelectedPathComponent() {
        TreePath p = getSelectionModel().getSelectionPath();
        return p == null ? null : p.getLastPathComponent();
    }

    /**
     * @return this tree's own lead path, <em>not</em> the selection model's —
     *         they are two stores and the JDK keeps them apart: measured on
     *         JDK 25, {@code setLeadSelectionPath(pathNotInTheTree)} moves this
     *         one and leaves the model's where it was. What holds them in step
     *         on an ordinary selection change is
     *         {@link #syncAnchorAndLeadFromSelection()}.
     */
    public TreePath getLeadSelectionPath() {
        return leadPath;
    }

    // --- Selection API (row-based; resolves over the surrogate's visible walk) ---

    public void setSelectionRow(int row) {
        setSelectionRows(new int[]{row});
    }

    public void setSelectionRows(int[] rows) {
        if (rows == null) return;
        List<TreePath> paths = new ArrayList<>(rows.length);
        for (int row : rows) {
            TreePath p = getPathForRow(row);
            if (p != null) paths.add(p);
        }
        // JDK: rows that resolve to no path simply drop out of the set.
        getSelectionModel().setSelectionPaths(paths.toArray(new TreePath[0]));
    }

    public void addSelectionRow(int row) {
        TreePath p = getPathForRow(row);
        if (p != null) addSelectionPath(p);
    }

    public void addSelectionRows(int[] rows) {
        if (rows == null) return;
        for (int row : rows) addSelectionRow(row);
    }

    public void removeSelectionRow(int row) {
        TreePath p = getPathForRow(row);
        if (p != null) removeSelectionPath(p);
    }

    public void removeSelectionRows(int[] rows) {
        if (rows == null) return;
        for (int row : rows) removeSelectionRow(row);
    }

    public void setSelectionInterval(int index0, int index1) {
        setSelectionRows(rowRange(index0, index1));
    }

    public void addSelectionInterval(int index0, int index1) {
        addSelectionRows(rowRange(index0, index1));
    }

    public void removeSelectionInterval(int index0, int index1) {
        removeSelectionRows(rowRange(index0, index1));
    }

    private static int[] rowRange(int index0, int index1) {
        int from = Math.min(index0, index1);
        int to = Math.max(index0, index1);
        int[] rows = new int[to - from + 1];
        for (int i = 0; i < rows.length; i++) rows[i] = from + i;
        return rows;
    }

    /** Resolved through the installed {@link RowMapper} (the visible-node walk). */
    public int[] getSelectionRows() {
        return getSelectionModel().getSelectionRows();
    }

    public int getMinSelectionRow() {
        return getSelectionModel().getMinSelectionRow();
    }

    public int getMaxSelectionRow() {
        return getSelectionModel().getMaxSelectionRow();
    }

    public int getLeadSelectionRow() {
        TreePath lead = getLeadSelectionPath();
        return lead == null ? -1 : getRowForPath(lead);
    }

    public boolean isRowSelected(int row) {
        return getSelectionModel().isRowSelected(row);
    }

    // --- TreeSelectionListener convenience (JDK re-source to listenerList) ---

    public void addTreeSelectionListener(TreeSelectionListener tsl) {
        listenerList.add(TreeSelectionListener.class, tsl);
    }

    public void removeTreeSelectionListener(TreeSelectionListener tsl) {
        listenerList.remove(TreeSelectionListener.class, tsl);
    }

    public TreeSelectionListener[] getTreeSelectionListeners() {
        return listenerList.getListeners(TreeSelectionListener.class);
    }

    protected void fireValueChanged(TreeSelectionEvent e) {
        for (TreeSelectionListener l : listenerList.getListeners(TreeSelectionListener.class)) {
            l.valueChanged(e);
        }
    }

    // --- Expansion (the JDK's expandedState, owned here; flushed to the peer) ---

    public void expandPath(TreePath path) {
        TreeModel model = getModel();
        if (path != null && model != null && !model.isLeaf(path.getLastPathComponent())) {
            setExpandedState(path, true);
        }
    }

    public void collapsePath(TreePath path) {
        setExpandedState(path, false);
    }

    public void expandRow(int row) {
        expandPath(getPathForRow(row));
    }

    public void collapseRow(int row) {
        collapsePath(getPathForRow(row));
    }

    /** Whether {@code path} and every one of its ancestors is expanded. */
    public boolean isExpanded(TreePath path) {
        if (path == null) return false;
        Boolean value;
        do {
            value = expandedState.get(path);
            if (value == null || !value) return false;
        } while ((path = path.getParentPath()) != null);
        return true;
    }

    /** The node at {@code row}'s own expanded flag; its ancestors are expanded, or it would have no row. */
    public boolean isExpanded(int row) {
        TreePath path = getPathForRow(row);
        if (path != null) {
            Boolean value = expandedState.get(path);
            return value != null && value;
        }
        return false;
    }

    public boolean isCollapsed(TreePath path) {
        return !isExpanded(path);
    }

    public boolean isCollapsed(int row) {
        return !isExpanded(row);
    }

    /** Whether {@code path} was ever expanded or collapsed, as the JDK's toggled-paths table records. */
    public boolean hasBeenExpanded(TreePath path) {
        return path != null && expandedState.get(path) != null;
    }

    public void makeVisible(TreePath path) {
        if (path != null) {
            TreePath parentPath = path.getParentPath();
            if (parentPath != null) {
                expandPath(parentPath);
            }
        }
    }

    public boolean isVisible(TreePath path) {
        if (path != null) {
            TreePath parentPath = path.getParentPath();
            if (parentPath != null) return isExpanded(parentPath);
            return true;
        }
        return false;
    }

    /**
     * Expands ancestors (firing TreeExpansionEvents) then scrolls, when the path is then
     * visible; R_layouts_close_enough — no pixel guarantee.
     */
    public void scrollPathToVisible(TreePath path) {
        if (path == null) return;
        makeVisible(path);
        if (!isVisible(path) || isHiddenRoot(path)) return;
        withPeer(p -> {
            try {
                surrogate().scrollToItem(path.getLastPathComponent());
            } catch (RuntimeException ex) {
                // Not reachable in the current tree: the JDK scrolls to nothing here too.
            }
        });
    }

    public void scrollRowToVisible(int row) {
        scrollPathToVisible(getPathForRow(row));
    }

    /**
     * The expanded, visible descendants of {@code parent}, excluding {@code parent}; empty
     * when there are none, {@code null} when {@code parent} is not expanded. Read from the
     * toggled-paths table, so in no particular order, as in the JDK.
     */
    public Enumeration<TreePath> getExpandedDescendants(TreePath parent) {
        if (!isExpanded(parent)) return null;
        Vector<TreePath> elements = null;
        Enumeration<TreePath> toggledPaths = expandedState.keys();
        while (toggledPaths.hasMoreElements()) {
            TreePath path = toggledPaths.nextElement();
            Boolean value = expandedState.get(path);
            if (path != parent && value != null && value
                    && parent.isDescendant(path) && isVisible(path)) {
                if (elements == null) elements = new Vector<>();
                elements.addElement(path);
            }
        }
        if (elements == null) return Collections.enumeration(Collections.<TreePath>emptySet());
        return elements.elements();
    }

    /**
     * The JDK's expansion engine. Expands every collapsed ancestor first, each vetoable by a
     * {@link TreeWillExpandListener} and announced to the {@link TreeExpansionListener}s, then
     * expands or collapses {@code path} itself the same way; a veto stops the walk where it
     * lands. Collapsing moves a selection inside the collapsed subtree onto {@code path}.
     * Every change is flushed to the peer.
     */
    protected void setExpandedState(TreePath path, boolean state) {
        if (path == null) return;
        try {
            java.util.Deque<TreePath> stack = new java.util.ArrayDeque<>();
            TreePath parentPath = path.getParentPath();
            while (parentPath != null) {
                if (isExpanded(parentPath)) {
                    parentPath = null;
                } else {
                    stack.push(parentPath);
                    parentPath = parentPath.getParentPath();
                }
            }
            while (!stack.isEmpty()) {
                parentPath = stack.pop();
                if (!isExpanded(parentPath)) {
                    try {
                        fireTreeWillExpand(parentPath);
                    } catch (ExpandVetoException eve) {
                        return;
                    }
                    expandedState.put(parentPath, Boolean.TRUE);
                    fireTreeExpanded(parentPath);
                }
            }
            if (!state) {
                Boolean cValue = expandedState.get(path);
                if (cValue != null && cValue) {
                    try {
                        fireTreeWillCollapse(path);
                    } catch (ExpandVetoException eve) {
                        return;
                    }
                    expandedState.put(path, Boolean.FALSE);
                    fireTreeCollapsed(path);
                    if (removeDescendantSelectedPaths(path, false) && !isPathSelected(path)) {
                        // A descendant was selected, select the parent.
                        addSelectionPath(path);
                    }
                }
            } else {
                Boolean cValue = expandedState.get(path);
                if (cValue == null || !cValue) {
                    try {
                        fireTreeWillExpand(path);
                    } catch (ExpandVetoException eve) {
                        return;
                    }
                    expandedState.put(path, Boolean.TRUE);
                    fireTreeExpanded(path);
                }
            }
        } finally {
            syncPeerExpansion();
        }
    }

    /** A toggle the user made in the browser, which the peer has already applied. */
    private void onPeerToggle(java.util.Collection<Object> items, boolean expanded) {
        for (Object item : items) {
            if (expanded) peerExpanded.add(item);
            else peerExpanded.remove(item);
            TreePath path = surrogate().getPathForNode(item);
            if (path == null) continue;
            if (expanded) expandPath(path);
            else collapsePath(path);
        }
        // A veto left the table unchanged; the flush takes the peer back to it.
        syncPeerExpansion();
    }

    /** Clears the toggled-paths table; fires nothing, as in the JDK. */
    protected void clearToggledPaths() {
        expandedState.clear();
    }

    /** Removes every toggled path under each of {@code toRemove}, the paths themselves included. */
    protected void removeDescendantToggledPaths(Enumeration<TreePath> toRemove) {
        if (toRemove != null) {
            while (toRemove.hasMoreElements()) {
                Enumeration<?> descendants = getDescendantToggledPaths(toRemove.nextElement());
                if (descendants != null) {
                    while (descendants.hasMoreElements()) {
                        expandedState.remove(descendants.nextElement());
                    }
                }
            }
        }
    }

    /** The toggled paths under {@code parent}, {@code parent} included. */
    protected Enumeration<TreePath> getDescendantToggledPaths(TreePath parent) {
        if (parent == null) return null;
        Vector<TreePath> descendants = new Vector<>();
        Enumeration<TreePath> nodes = expandedState.keys();
        while (nodes.hasMoreElements()) {
            TreePath path = nodes.nextElement();
            if (parent.isDescendant(path)) descendants.addElement(path);
        }
        return descendants.elements();
    }

    /** Removes the selected paths under {@code path}; {@code true} if there were any. */
    protected boolean removeDescendantSelectedPaths(TreePath path, boolean includePath) {
        TreePath[] toRemove = getDescendantSelectedPaths(path, includePath);
        if (toRemove != null) {
            getSelectionModel().removeSelectionPaths(toRemove);
            return true;
        }
        return false;
    }

    private TreePath[] getDescendantSelectedPaths(TreePath path, boolean includePath) {
        TreeSelectionModel sm = getSelectionModel();
        TreePath[] selPaths = sm != null ? sm.getSelectionPaths() : null;
        if (selPaths == null) return null;
        boolean shouldRemove = false;
        for (int counter = selPaths.length - 1; counter >= 0; counter--) {
            if (selPaths[counter] != null && path.isDescendant(selPaths[counter])
                    && (!path.equals(selPaths[counter]) || includePath)) {
                shouldRemove = true;
            } else {
                selPaths[counter] = null;
            }
        }
        return shouldRemove ? selPaths : null;
    }

    /** The {@link TreeModelHandler} the JDK subscribes to the model; an override may return its own, or {@code null} for none. */
    protected javax.swing.event.TreeModelListener createTreeModelListener() {
        return new TreeModelHandler();
    }

    /**
     * Keeps {@link #expandedState} in step with the model, as the JDK's does: a new root
     * clears the table, a changed or removed subtree loses its toggled paths, and the
     * selection leaves nodes that are gone. Runs on the thread that changed the model; the
     * table changes are flushed to the peer.
     */
    protected class TreeModelHandler implements javax.swing.event.TreeModelListener {

        protected TreeModelHandler() {
        }

        @Override
        public void treeNodesChanged(javax.swing.event.TreeModelEvent e) {
        }

        @Override
        public void treeNodesInserted(javax.swing.event.TreeModelEvent e) {
        }

        @Override
        public void treeStructureChanged(javax.swing.event.TreeModelEvent e) {
            if (e == null) return;
            TreePath parent = treePathOf(e);
            if (parent == null) return;
            if (parent.getPathCount() == 1) {
                // New root, remove everything.
                clearToggledPaths();
                Object treeRoot = treeModel.getRoot();
                if (treeRoot != null && !treeModel.isLeaf(treeRoot)) {
                    expandedState.put(parent, Boolean.TRUE);
                }
            } else if (expandedState.get(parent) != null) {
                Vector<TreePath> toRemove = new Vector<>(1);
                boolean isExpanded = isExpanded(parent);
                toRemove.addElement(parent);
                removeDescendantToggledPaths(toRemove.elements());
                if (isExpanded) {
                    TreeModel model = getModel();
                    if (model == null || model.isLeaf(parent.getLastPathComponent())) {
                        collapsePath(parent);
                    } else {
                        expandedState.put(parent, Boolean.TRUE);
                    }
                }
            }
            removeDescendantSelectedPaths(parent, false);
            syncPeerExpansion();
        }

        @Override
        public void treeNodesRemoved(javax.swing.event.TreeModelEvent e) {
            if (e == null) return;
            TreePath parent = treePathOf(e);
            Object[] children = e.getChildren();
            if (children == null) return;
            Vector<TreePath> toRemove = new Vector<>(Math.max(1, children.length));
            for (int counter = children.length - 1; counter >= 0; counter--) {
                TreePath rPath = parent.pathByAddingChild(children[counter]);
                if (expandedState.get(rPath) != null) toRemove.addElement(rPath);
            }
            if (toRemove.size() > 0) removeDescendantToggledPaths(toRemove.elements());
            TreeModel model = getModel();
            if (model == null || model.isLeaf(parent.getLastPathComponent())) {
                expandedState.remove(parent);
            }
            if (parent != null) {
                for (int counter = children.length - 1; counter >= 0; counter--) {
                    removeDescendantSelectedPaths(parent.pathByAddingChild(children[counter]), true);
                }
            }
            syncPeerExpansion();
        }
    }

    /** The event's path, or the root's when it carries none (a whole-tree event). */
    private TreePath treePathOf(javax.swing.event.TreeModelEvent e) {
        TreePath path = e.getTreePath();
        if (path == null && treeModel != null) {
            Object root = treeModel.getRoot();
            if (root != null) path = new TreePath(root);
        }
        return path;
    }

    /** Whether {@code path} is the model root while it is hidden, which is no row and no Vaadin item. */
    private boolean isHiddenRoot(TreePath path) {
        return path.getParentPath() == null && !isRootVisible();
    }

    /**
     * Flushes {@link #expandedState} to the peer: expands the nodes the table has expanded
     * and the peer has not, and collapses the reverse, diffed against {@link #peerExpanded}.
     * The root's state is always pushed, since the surrogate expands a shown root on its own.
     * A flush of current state rather than a replay of calls, so it may run any number of
     * times.
     */
    private void syncPeerExpansion() {
        Set<Object> want = new HashSet<>();
        for (Map.Entry<TreePath, Boolean> entry : expandedState.entrySet()) {
            if (entry.getValue() && !isHiddenRoot(entry.getKey())) {
                want.add(entry.getKey().getLastPathComponent());
            }
        }
        List<Object> expand = new ArrayList<>();
        List<Object> collapse = new ArrayList<>();
        for (Object node : want) if (!peerExpanded.contains(node)) expand.add(node);
        for (Object node : peerExpanded) if (!want.contains(node)) collapse.add(node);
        TreeModel m = getModel();
        Object root = m == null ? null : m.getRoot();
        if (root != null && isRootVisible() && !m.isLeaf(root)) {
            if (want.contains(root)) {
                if (!expand.contains(root)) expand.add(root);
            } else if (!collapse.contains(root)) {
                collapse.add(root);
            }
        }
        peerExpanded.clear();
        peerExpanded.addAll(want);
        if (expand.isEmpty() && collapse.isEmpty()) return;
        withPeer(p -> {
            SJTree s = surrogate();
            if (!collapse.isEmpty()) s.collapse(collapse);
            if (!expand.isEmpty()) s.expand(expand);
        });
    }

    // --- TreeExpansionListener convenience (re-source to listenerList) ---

    public void addTreeExpansionListener(TreeExpansionListener tel) {
        listenerList.add(TreeExpansionListener.class, tel);
    }

    public void removeTreeExpansionListener(TreeExpansionListener tel) {
        listenerList.remove(TreeExpansionListener.class, tel);
    }

    public TreeExpansionListener[] getTreeExpansionListeners() {
        return listenerList.getListeners(TreeExpansionListener.class);
    }

    /** Notifies the {@link TreeExpansionListener}s last to first, as the JDK does. */
    public void fireTreeExpanded(TreePath path) {
        Object[] listeners = listenerList.getListenerList();
        TreeExpansionEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeExpansionListener.class) {
                if (e == null) e = new TreeExpansionEvent(this, path);
                ((TreeExpansionListener) listeners[i + 1]).treeExpanded(e);
            }
        }
    }

    /** Notifies the {@link TreeExpansionListener}s last to first, as the JDK does. */
    public void fireTreeCollapsed(TreePath path) {
        Object[] listeners = listenerList.getListenerList();
        TreeExpansionEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeExpansionListener.class) {
                if (e == null) e = new TreeExpansionEvent(this, path);
                ((TreeExpansionListener) listeners[i + 1]).treeCollapsed(e);
            }
        }
    }

    // --- TreeWillExpandListener (fired by setExpandedState, veto honoured) ---

    /**
     * Fired before every expansion and collapse, programmatic or the user's, and a veto
     * holds, as in the JDK. For a toggle in the browser the peer has already opened or closed
     * the node by then; a veto closes or reopens it again, so the user may see it flicker.
     */
    public void addTreeWillExpandListener(TreeWillExpandListener tel) {
        listenerList.add(TreeWillExpandListener.class, tel);
    }

    public void removeTreeWillExpandListener(TreeWillExpandListener tel) {
        listenerList.remove(TreeWillExpandListener.class, tel);
    }

    public TreeWillExpandListener[] getTreeWillExpandListeners() {
        return listenerList.getListeners(TreeWillExpandListener.class);
    }

    /** Notifies the {@link TreeWillExpandListener}s last to first, as the JDK does; the first veto stops it. */
    public void fireTreeWillExpand(TreePath path) throws ExpandVetoException {
        Object[] listeners = listenerList.getListenerList();
        TreeExpansionEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeWillExpandListener.class) {
                if (e == null) e = new TreeExpansionEvent(this, path);
                ((TreeWillExpandListener) listeners[i + 1]).treeWillExpand(e);
            }
        }
    }

    /** Notifies the {@link TreeWillExpandListener}s last to first, as the JDK does; the first veto stops it. */
    public void fireTreeWillCollapse(TreePath path) throws ExpandVetoException {
        Object[] listeners = listenerList.getListenerList();
        TreeExpansionEvent e = null;
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeWillExpandListener.class) {
                if (e == null) e = new TreeExpansionEvent(this, path);
                ((TreeWillExpandListener) listeners[i + 1]).treeWillCollapse(e);
            }
        }
    }

    // --- Row ⇄ path queries (a walk of the model under expandedState) ---
    //
    // The JDK asks its L&F's layout cache; there is none here, so the rows are what that cache
    // would hold: the root when shown, then depth first into every expanded node. Recomputed
    // per call from the live model, so O(visible rows).

    /** The visible paths in row order. */
    private List<TreePath> visiblePaths() {
        List<TreePath> out = new ArrayList<>();
        TreeModel m = getModel();
        Object root = m == null ? null : m.getRoot();
        if (root == null) return out;
        TreePath rootPath = new TreePath(root);
        if (isRootVisible()) out.add(rootPath);
        if (!m.isLeaf(root) && Boolean.TRUE.equals(expandedState.get(rootPath))) {
            addVisibleChildren(m, rootPath, out);
        }
        return out;
    }

    private void addVisibleChildren(TreeModel m, TreePath parent, List<TreePath> out) {
        Object node = parent.getLastPathComponent();
        for (int i = 0, n = m.getChildCount(node); i < n; i++) {
            Object child = m.getChild(node, i);
            TreePath path = parent.pathByAddingChild(child);
            out.add(path);
            if (!m.isLeaf(child) && Boolean.TRUE.equals(expandedState.get(path))) {
                addVisibleChildren(m, path, out);
            }
        }
    }

    public int getRowCount() {
        return visiblePaths().size();
    }

    public TreePath getPathForRow(int row) {
        List<TreePath> visible = visiblePaths();
        return row < 0 || row >= visible.size() ? null : visible.get(row);
    }

    /** {@code -1} when an ancestor is collapsed, so the path has no row. */
    public int getRowForPath(TreePath path) {
        if (path == null) return -1;
        return visiblePaths().indexOf(path);
    }

    /**
     * The clicked row during a user mouse handler's dispatch (the
     * {@code selectOnRightClick} idiom), -1 otherwise, whatever the coordinates:
     * there is no server-side pixel→row map (R_layouts_close_enough). The call
     * graph is the JDK's, so an override of any method it reaches runs.
     */
    public int getRowForLocation(int x, int y) {
        return getRowForPath(getPathForLocation(x, y));
    }

    /** The clicked path during a mouse handler's dispatch, null otherwise — see {@link #getRowForLocation}. */
    public TreePath getPathForLocation(int x, int y) {
        TreePath closestPath = getClosestPathForLocation(x, y);
        if (closestPath != null) {
            // The JDK tests (x, y) against these bounds. Without view geometry the click
            // stands in for the test, but an override still runs.
            getPathBounds(closestPath);
        }
        return closestPath;
    }

    /** The clicked path during a mouse handler's dispatch, null otherwise — see {@link #getRowForLocation}. */
    public TreePath getClosestPathForLocation(int x, int y) {
        return clickPathStash;
    }

    public int getClosestRowForLocation(int x, int y) {
        return getRowForPath(getClosestPathForLocation(x, y));
    }

    /** R_layouts_close_enough — no server-side cell bounds. */
    public Rectangle getPathBounds(TreePath path) {
        return null;
    }

    /** R_layouts_close_enough — no server-side cell bounds. */
    public Rectangle getRowBounds(int row) {
        return null;
    }

    // --- No-Vaadin-counterpart knobs: emulator-only R_swing_is_truth field shadows (D_jtree_field_shadows) ---
    //
    // TreeGrid owns real layout / toggle chrome / scroll physics, so these
    // have no peer property. They round-trip on the emulator (JDK-faithful,
    // R_swing_is_truth) + fire PCE; the surrogate drops them per R_vaadin_first — the asymmetric
    // R_swing_is_truth-shadow split (SJToolBar D_jtoolbar / JSplitPane D_jsplitpane / JList D_jlist). The
    // sizing/chrome shadows round-trip silently (R_layouts_close_enough); `editable` is the one
    // that's load-bearing — it arms the D_jtree_rename_in_place rename-in-place editor.

    protected boolean showsRootHandles;
    protected int rowHeight = 16;
    protected boolean scrollsOnExpand = true;
    protected int toggleClickCount = 2;
    protected int visibleRowCount = 20;
    protected boolean editable;
    protected boolean invokesStopCellEditing;
    private boolean expandsSelectedPaths = true;
    protected boolean largeModel;
    private TreePath anchorPath;
    private TreePath leadPath;

    public boolean getShowsRootHandles() {
        return showsRootHandles;
    }

    public void setShowsRootHandles(boolean newValue) {
        boolean old = showsRootHandles;
        if (old == newValue) return;
        showsRootHandles = newValue;
        firePropertyChange(SHOWS_ROOT_HANDLES_PROPERTY, old, newValue);
    }

    public int getRowHeight() {
        return rowHeight;
    }

    public void setRowHeight(int rowHeight) {
        int old = this.rowHeight;
        if (old == rowHeight) return;
        this.rowHeight = rowHeight;
        firePropertyChange(ROW_HEIGHT_PROPERTY, old, rowHeight);
    }

    /** JDK: fixed row height means a positive {@code rowHeight}. */
    public boolean isFixedRowHeight() {
        return rowHeight > 0;
    }

    public boolean getScrollsOnExpand() {
        return scrollsOnExpand;
    }

    public void setScrollsOnExpand(boolean newValue) {
        boolean old = scrollsOnExpand;
        if (old == newValue) return;
        scrollsOnExpand = newValue;
        firePropertyChange(SCROLLS_ON_EXPAND_PROPERTY, old, newValue);
    }

    public int getToggleClickCount() {
        return toggleClickCount;
    }

    public void setToggleClickCount(int clickCount) {
        int old = toggleClickCount;
        if (old == clickCount) return;
        toggleClickCount = clickCount;
        firePropertyChange(TOGGLE_CLICK_COUNT_PROPERTY, old, clickCount);
    }

    public int getVisibleRowCount() {
        return visibleRowCount;
    }

    public void setVisibleRowCount(int newCount) {
        int old = visibleRowCount;
        if (old == newCount) return;
        visibleRowCount = newCount;
        firePropertyChange(VISIBLE_ROW_COUNT_PROPERTY, old, newCount);
    }

    public boolean getExpandsSelectedPaths() {
        return expandsSelectedPaths;
    }

    public void setExpandsSelectedPaths(boolean newValue) {
        boolean old = expandsSelectedPaths;
        if (old == newValue) return;
        expandsSelectedPaths = newValue;
        firePropertyChange(EXPANDS_SELECTED_PATHS_PROPERTY, old, newValue);
    }

    public boolean getInvokesStopCellEditing() {
        return invokesStopCellEditing;
    }

    public void setInvokesStopCellEditing(boolean newValue) {
        boolean old = invokesStopCellEditing;
        if (old == newValue) return;
        invokesStopCellEditing = newValue;
        firePropertyChange(INVOKES_STOP_CELL_EDITING_PROPERTY, old, newValue);
    }

    public boolean isLargeModel() {
        return largeModel;
    }

    public void setLargeModel(boolean newValue) {
        boolean old = largeModel;
        if (old == newValue) return;
        largeModel = newValue;
        firePropertyChange(LARGE_MODEL_PROPERTY, old, newValue);
    }

    public TreePath getAnchorSelectionPath() {
        return anchorPath;
    }

    public void setAnchorSelectionPath(TreePath newPath) {
        TreePath old = anchorPath;
        anchorPath = newPath;
        firePropertyChange(ANCHOR_SELECTION_PATH_PROPERTY, old, newPath);
    }

    public void setLeadSelectionPath(TreePath newPath) {
        TreePath old = leadPath;
        leadPath = newPath;
        firePropertyChange(LEAD_SELECTION_PATH_PROPERTY, old, newPath);
    }

    // --- Editing — rename-in-place over the inherited TreeGrid.Editor (D_jtree_rename_in_place) ---
    //
    // Mirrors the JTable cell-editor bridge (D_jtable_cell_editing): the surrogate's inherited
    // Grid.Editor is the overlay + open mechanism (wired in installEditorBridge),
    // a text field seeds from convertValueToText, and commit writes back through
    // TreeModel.valueForPathChanged — which the surrogate already turns into a
    // treeNodesChanged → refreshItem repaint. Only the DefaultTreeCellEditor
    // text-rename behaviour is reproduced; an arbitrary editor component
    // (setCellEditor) stays out per D_gap_severity_triage sub-bucket (b). While the editor is open
    // the cell's hierarchy indent + toggle are absent (the editor component
    // replaces the HierarchyColumnComponentRenderer cell) — cosmetic, edit-only,
    // R_layouts_close_enough close-enough.

    private TreePath editingPath;
    private TextField editorField;
    private Registration escapeCancelShortcut;
    private Registration enterCommitShortcut;

    public boolean isEditable() {
        return editable;
    }

    /**
     * Arms rename-in-place editing — double-click a node, or call
     * {@link #startEditingAtPath}. {@code false} disarms it. Round-trips per R_swing_is_truth
     * + fires the {@code "editable"} PCE.
     */
    public void setEditable(boolean flag) {
        boolean old = editable;
        if (old == flag) return;
        editable = flag;
        firePropertyChange(EDITABLE_PROPERTY, old, flag);
    }

    /** JDK subclass hook — defaults to {@link #isEditable()}. */
    public boolean isPathEditable(TreePath path) {
        return isEditable();
    }

    /**
     * @return the editor {@link #setCellEditor} installed, or {@code null} —
     *     rename-in-place is driven internally and never routes through one
     */
    public vaadinx.swing.tree.TreeCellEditor getCellEditor() {
        return cellEditor;
    }

    /**
     * Stores the editor and fires {@code "cellEditor"}; the editor is inert.
     * Installing an arbitrary editor component stays out per D_gap_severity_triage sub-bucket
     * (b), and the built-in text rename ({@link #setEditable}) is the only
     * editing reproduced — that is the declined effect. State and notification
     * are owed regardless (R_decline_effect_only, D_owed_events).
     */
    public void setCellEditor(vaadinx.swing.tree.TreeCellEditor cellEditor) {
        vaadinx.swing.tree.TreeCellEditor old = this.cellEditor;
        this.cellEditor = cellEditor;
        if (cellEditor != null) {
            vaadinx.EHelper.onUnimplemented("JTree", "setCellEditor(edit)", cellEditor);
        }
        firePropertyChange(CELL_EDITOR_PROPERTY, old, cellEditor);
    }

    // Inert user-installed editor — stored so the getter and the bound
    // property are honest (R_decline_effect_only, D_owed_events).
    protected vaadinx.swing.tree.TreeCellEditor cellEditor;

    public boolean isEditing() {
        return editingPath != null;
    }

    public TreePath getEditingPath() {
        return editingPath;
    }

    /**
     * Selects {@code path} and opens a text editor on its node, seeded from
     * {@link #convertValueToText}. No-op unless the tree is {@link #isEditable
     * editable} and {@link #isPathEditable} allows the path. Any in-flight edit
     * commits first (JDK terminate-by-commit).
     */
    public void startEditingAtPath(TreePath path) {
        if (path == null || !isEditable() || !isPathEditable(path)) return;
        if (editingPath != null) stopEditing();

        // Expand ancestors so the node is a live Grid item.
        makeVisible(path);
        Object node = path.getLastPathComponent();
        TreeModel m = getModel();
        boolean leaf = m != null && m.isLeaf(node);
        boolean expanded = !leaf && Boolean.TRUE.equals(expandedState.get(path));
        int row = getRowForPath(path);
        // The rest of the edit start is peer work (D_attach_aware_hop).
        withPeer(p -> {
            SJTree s = surrogate();
            s.setSelectionPath(path);

            TextField tf = new TextField();
            tf.setValue(convertValueToText(node, true, expanded, leaf, row, true));
            tf.getElement().getThemeList().add("small");   // fit the row height (JTable B5)
            editorField = tf;
            editingPath = path;

            s.getColumns().get(0).setEditorComponent(tf);
            // Enter commits, Escape cancels — grid-scoped, live only during the edit.
            // Vaadin's built-in editor keys don't fire for a programmatic
            // single-column setEditorComponent (JTable B9); both are peer→Swing
            // callbacks, so they funnel through callSwing per R_callswing_envelope.
            enterCommitShortcut = Shortcuts.addShortcutListener(
                    s, () -> vaadinx.EHelper.callSwing(this::commitEdit), Key.ENTER).listenOn(s);
            escapeCancelShortcut = Shortcuts.addShortcutListener(
                    s, () -> vaadinx.EHelper.callSwing(this::cancelEdit), Key.ESCAPE).listenOn(s);
            s.getEditor().editItem(node);
        });
    }

    /** Commits the in-flight edit (write-back via {@code valueForPathChanged}); {@code true} if editing was in progress. */
    public boolean stopEditing() {
        if (editingPath == null) return false;
        commitEdit();
        return true;
    }

    /** Cancels the in-flight edit with no write-back. */
    public void cancelEditing() {
        cancelEdit();
    }

    /** Vaadin editor closed (blur / programmatic) — commit the in-flight rename if any. */
    private void onVaadinEditorClosed() {
        commitEdit();
    }

    /** Vaadin editor cancelled — discard the in-flight rename if any. */
    private void onVaadinEditorCancelled() {
        cancelEdit();
    }

    /**
     * Read the editor text and write it back through
     * {@code TreeModel.valueForPathChanged} — the surrogate turns the resulting
     * {@code treeNodesChanged} into a cell repaint. Tears the editor down first
     * so the {@code closeEditor} re-entry sees "not editing".
     */
    private void commitEdit() {
        if (editingPath == null) return;
        TreePath path = editingPath;
        String value = editorField.getValue();
        cleanupEdit();
        TreeModel m = getModel();
        if (m != null) m.valueForPathChanged(path, value);
    }

    private void cancelEdit() {
        if (editingPath == null) return;
        cleanupEdit();
    }

    /** Remove the edit shortcuts, clear the column's editor component, close the Grid editor, reset state. */
    private void cleanupEdit() {
        withPeer(p -> {
            if (enterCommitShortcut != null) { enterCommitShortcut.remove(); enterCommitShortcut = null; }
            if (escapeCancelShortcut != null) { escapeCancelShortcut.remove(); escapeCancelShortcut = null; }
            // Reset state BEFORE closeEditor so the close-listener re-entry no-ops.
            editingPath = null;
            editorField = null;
            SJTree s = surrogate();
            s.getColumns().get(0).setEditorComponent((com.vaadin.flow.component.Component) null);
            if (s.getEditor().isOpen()) s.getEditor().closeEditor();
        });
    }

    // --- Keyboard search (no server-side type-ahead; WARN) ---

    /** WARN — type-ahead node search isn't modelled. */
    public TreePath getNextMatch(String prefix, int startingRow,
                                 javax.swing.text.Position.Bias bias) {
        vaadinx.EHelper.onUnimplemented("JTree", "getNextMatch", prefix, startingRow, bias);
        return null;
    }

    // --- Drag and drop (D_jtree_row_dnd — TreeGrid-row path per D_drag_and_drop) ---

    private boolean dragEnabled;

    public boolean getDragEnabled() {
        return dragEnabled;
    }

    public void setDragEnabled(boolean b) {
        // D_jtree_row_dnd: graduated from the D_jtree_field_shadows WARN. dragEnabled is the JDK-faithful
        // round-trip flag (R_swing_is_truth); flipping it (re)wires the TreeGrid-row drag
        // source through DndBridge, which reads this flag back.
        // No PropertyChangeEvent: JTree.setDragEnabled checks and assigns;
        // dragEnabled is not a bound property (D_property_fanout_audit).
        if (dragEnabled == b) return;
        dragEnabled = b;
        vaadinx.swing.DndBridge.reconfigure(this);
    }

    /** Drop mode (D_drag_and_drop/D_jtree_row_dnd). Default {@code USE_SELECTION} matches JDK JTree. */
    private javax.swing.DropMode dropMode = javax.swing.DropMode.USE_SELECTION;

    public final javax.swing.DropMode getDropMode() {
        return dropMode;
    }

    public final void setDropMode(javax.swing.DropMode dropMode) {
        // JDK JTree supports USE_SELECTION / ON / INSERT / ON_OR_INSERT;
        // reject the rest with the same exception type Swing throws (R_match_swing_errors).
        if (dropMode != null) {
            switch (dropMode) {
                case USE_SELECTION, ON, INSERT, ON_OR_INSERT -> { }
                default -> throw new IllegalArgumentException(
                        dropMode + ": Unsupported drop mode for tree");
            }
        }
        this.dropMode = dropMode;
        vaadinx.swing.DndBridge.reconfigure(this);
    }

    /**
     * Drop location for a JTree drop (D_jtree_row_dnd — emulator for
     * {@code javax.swing.JTree.DropLocation}). {@link #getPath()} is the
     * target path; {@link #getChildIndex()} is the insert index within the
     * path's node ({@code -1} for a drop <em>on</em> the node,
     * {@code DropMode.ON}). The pixel drop point is a dummy (R_layouts_close_enough — no
     * server-side coordinates).
     */
    public static final class DropLocation extends vaadinx.swing.TransferHandler.DropLocation {
        private final TreePath path;
        private final int index;

        DropLocation(Point p, TreePath path, int index) {
            super(p);
            this.path = path;
            this.index = index;
        }

        public TreePath getPath() {
            return path;
        }

        public int getChildIndex() {
            return index;
        }
    }

    // --- Scrollable (JDK interface; R_layouts_close_enough close-enough) ---

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        int h = (getRowHeight() > 0 ? getRowHeight() : 16) * Math.max(1, getVisibleRowCount());
        return new Dimension(256, h);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
                ? (getRowHeight() > 0 ? getRowHeight() : 16)
                : 20;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == javax.swing.SwingConstants.VERTICAL
                ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // --- L&F stubs ---

    public String getUIClassID() {
        return "TreeUI";
    }

    public void updateUI() {
        // L&F swap — no-op. Same shape as JTable / JList.
    }

    public javax.swing.plaf.TreeUI getUI() {
        return null;
    }

    public void setUI(javax.swing.plaf.TreeUI ui) {
        if (ui != null) vaadinx.EHelper.onUnimplemented("JTree", "setUI", ui);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JTree", "getAccessibleContext");
        return null;
    }
}
