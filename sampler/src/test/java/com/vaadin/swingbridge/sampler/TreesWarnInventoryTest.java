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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JTree;
import vaadinx.swing.tree.DefaultTreeCellRenderer;

import javax.swing.DropMode;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.util.ArrayList;
import java.util.List;

/**
 * TreesPanel (JTree demo, SD_sjtree + D_jtree) WARN inventory exit gate. Both
 * tests fail if any unaccounted {@code EHelper.onUnimplemented} /
 * {@code onUnsupportedPeerShape} fires.
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — drives the route end-to-end: select
 *       first case → add a document → right-click-to-select (the
 *       {@code selectOnRightClick} idiom, via the item-click bridge +
 *       {@code getRowForLocation} stash) → expand all → remove selected.
 *       Exercises the DefaultTreeModel mutation surface, the custom-renderer
 *       snapshot bridge (D_jtree_renderer_registry), the selection re-source, the D_jtree_row_dnd DnD wiring
 *       at ctor time, and the JScrollPane auto-scroll guard around the
 *       TreeGrid peer.</li>
 *   <li>{@link #inventory_jtree_api_surface} — micro-driver over the
 *       supported JTree surface in WARN-free buckets (including the D_jtree_rename_in_place
 *       rename-in-place lifecycle), then the enumerated intentional-WARN paths
 *       (arbitrary {@code setCellEditor} per D_gap_severity_triage sub-bucket (b), willExpand per
 *       sub-bucket (a), type-ahead search, L&F install, renderer selection
 *       chrome per R_vaadin_first) asserted to fire exactly one WARN each.</li>
 * </ol>
 */
class TreesWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Trees");
        dump("Step 0b (TreesPanel swap — incl. D_jtree_row_dnd DnD wiring)", warnings);

        // Reach the emulator JTree via its SJTree peer.
        com.vaadin.swingbridge.surrogates.SJTree sTree = LocatorJ._get(com.vaadin.swingbridge.surrogates.SJTree.class);
        JTree tree = (JTree) vaadinx.EHelper.getEmulator(sTree);
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        if (root.getChildCount() != 2) {
            throw new AssertionError("expected 2 seed cases, got " + root.getChildCount());
        }
        // Panel ctor expands root (model install) + the first case folder:
        // root, smith, Complaint.pdf, Evidence-A.png, estate.
        if (tree.getRowCount() != 5) {
            throw new AssertionError("expected 5 visible rows, got " + tree.getRowCount());
        }
        dump("Step 0c (seed structure)", warnings);

        // Programmatic selection — fires the re-sourced TreeSelectionListener
        // that updates the status label (the fireCaseSelected path).
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Select first case")));
        DefaultMutableTreeNode smith = (DefaultMutableTreeNode) root.getChildAt(0);
        if (tree.getLastSelectedPathComponent() != smith) {
            throw new AssertionError("expected first case selected, got "
                    + tree.getLastSelectedPathComponent());
        }
        dump("Step 1 (select first case)", warnings);

        // Add a document under the selected case — DefaultTreeModel
        // insertNodeInto → treeNodesInserted → subtree refresh.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Add document")));
        if (smith.getChildCount() != 3) {
            throw new AssertionError("expected 3 documents after add, got " + smith.getChildCount());
        }
        dump("Step 2 (add document)", warnings);

        // Right-click-to-select: browser button 2 → BUTTON3 → isPopupTrigger
        // → the panel's selectOnRightClick handler resolves the row via the
        // click stash and selects it. Row 5 = the second case folder
        // (root, smith, doc, doc, new-doc, estate).
        GridKt._clickItem(sTree, 5, 2, false, false, false, false);
        DefaultMutableTreeNode estate = (DefaultMutableTreeNode) root.getChildAt(1);
        if (tree.getLastSelectedPathComponent() != estate) {
            throw new AssertionError("expected right-clicked case selected, got "
                    + tree.getLastSelectedPathComponent());
        }
        dump("Step 3 (right-click row 5 to select)", warnings);

        // Expand all — row-walk expansion; both case folders open.
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Expand all")));
        // root + smith + 3 docs + estate + 3 docs = 9.
        if (tree.getRowCount() != 9) {
            throw new AssertionError("expected 9 visible rows after expand-all, got "
                    + tree.getRowCount());
        }
        dump("Step 4 (expand all)", warnings);

        // Remove the added document.
        DefaultMutableTreeNode added = (DefaultMutableTreeNode) smith.getChildAt(2);
        tree.setSelectionPath(new TreePath(added.getPath()));
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Remove selected")));
        if (smith.getChildCount() != 2) {
            throw new AssertionError("expected 2 documents after remove, got " + smith.getChildCount());
        }
        dump("Step 5 (remove selected)", warnings);

        WarnDump.println();
        WarnDump.println("=== trees user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jtree_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Ctors (all JDK forms) --
        new JTree();                                       // demo model
        new JTree(new Object[] { "a", "b" });
        new JTree(new java.util.Vector<>(List.of("x")));
        new JTree(new java.util.Hashtable<>(java.util.Map.of("k", "v")));
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode folder = new DefaultMutableTreeNode("folder");
        DefaultMutableTreeNode leaf = new DefaultMutableTreeNode("leaf");
        root.add(folder);
        folder.add(leaf);
        new JTree(root);
        new JTree(root, false);
        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree jt = new JTree(model);
        dump("JTree  ctors", warnings);

        // -- Model swap + PCE --
        jt.setModel(new DefaultTreeModel(root));
        jt.getModel();
        jt.setModel(model);
        dump("JTree  setModel / getModel", warnings);

        // -- Selection (paths) --
        TreePath folderPath = new TreePath(new Object[] { root, folder });
        TreePath leafPath = new TreePath(new Object[] { root, folder, leaf });
        jt.setSelectionPath(folderPath);
        jt.getSelectionPath();
        jt.getSelectionPaths();
        jt.addSelectionPath(leafPath);
        jt.addSelectionPaths(new TreePath[] { folderPath });
        jt.removeSelectionPath(leafPath);
        jt.removeSelectionPaths(new TreePath[] { folderPath });
        jt.getSelectionCount();
        jt.isPathSelected(folderPath);
        jt.isSelectionEmpty();
        jt.getLastSelectedPathComponent();
        jt.getLeadSelectionPath();
        jt.clearSelection();
        dump("JTree  selection (paths)", warnings);

        // -- Selection (rows; resolves over the visible walk + RowMapper) --
        jt.expandPath(folderPath);
        jt.setSelectionRow(1);
        jt.setSelectionRows(new int[] { 1, 2 });
        jt.addSelectionRow(0);
        jt.addSelectionRows(new int[] { 2 });
        jt.removeSelectionRow(0);
        jt.removeSelectionRows(new int[] { 0 });
        jt.setSelectionInterval(1, 2);
        jt.addSelectionInterval(0, 0);
        jt.removeSelectionInterval(0, 1);
        jt.getSelectionRows();
        jt.getMinSelectionRow();
        jt.getMaxSelectionRow();
        jt.getLeadSelectionRow();
        jt.isRowSelected(1);
        jt.clearSelection();
        dump("JTree  selection (rows)", warnings);

        // -- Selection model swap (JDK-shaped accessor on the emulator) --
        jt.setSelectionModel(new DefaultTreeSelectionModel());
        jt.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        dump("JTree  selection model swap + mode", warnings);

        // -- Listener registration --
        javax.swing.event.TreeSelectionListener tsl = e -> { };
        jt.addTreeSelectionListener(tsl);
        jt.getTreeSelectionListeners();
        jt.removeTreeSelectionListener(tsl);
        javax.swing.event.TreeExpansionListener tel = new javax.swing.event.TreeExpansionListener() {
            public void treeExpanded(javax.swing.event.TreeExpansionEvent event) { }
            public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) { }
        };
        jt.addTreeExpansionListener(tel);
        jt.getTreeExpansionListeners();
        jt.removeTreeExpansionListener(tel);
        vaadinx.awt.event.MouseAdapter ma = new vaadinx.awt.event.MouseAdapter() { };
        jt.addMouseListener(ma);
        jt.removeMouseListener(ma);
        dump("JTree  listener registration", warnings);

        // -- Expansion --
        jt.expandPath(folderPath);
        jt.collapsePath(folderPath);
        jt.expandRow(1);
        jt.collapseRow(1);
        jt.isExpanded(folderPath);
        jt.isExpanded(0);
        jt.isCollapsed(folderPath);
        jt.isCollapsed(0);
        jt.hasBeenExpanded(folderPath);
        jt.makeVisible(leafPath);
        jt.isVisible(leafPath);
        jt.scrollPathToVisible(leafPath);
        jt.scrollRowToVisible(0);
        jt.getExpandedDescendants(new TreePath(root));
        dump("JTree  expansion", warnings);

        // -- Row ⇄ path queries + hit testing (R_layouts_close_enough — stash/-1/null, no WARN) --
        jt.getRowCount();
        jt.getPathForRow(0);
        jt.getRowForPath(folderPath);
        jt.getRowForLocation(0, 0);
        jt.getPathForLocation(0, 0);
        jt.getClosestPathForLocation(0, 0);
        jt.getClosestRowForLocation(0, 0);
        jt.getPathBounds(folderPath);
        jt.getRowBounds(0);
        dump("JTree  row/path queries + hit testing", warnings);

        // -- rootVisible (peer-delegating) --
        jt.setRootVisible(false);
        jt.isRootVisible();
        jt.setRootVisible(true);
        dump("JTree  rootVisible", warnings);

        // -- No-counterpart knobs (D_jtree_field_shadows R_swing_is_truth field shadows; silent round-trip) --
        jt.setShowsRootHandles(true);
        jt.getShowsRootHandles();
        jt.setRowHeight(24);
        jt.getRowHeight();
        jt.isFixedRowHeight();
        jt.setScrollsOnExpand(false);
        jt.getScrollsOnExpand();
        jt.setToggleClickCount(1);
        jt.getToggleClickCount();
        jt.setVisibleRowCount(10);
        jt.getVisibleRowCount();
        jt.setExpandsSelectedPaths(false);
        jt.getExpandsSelectedPaths();
        jt.setInvokesStopCellEditing(true);
        jt.getInvokesStopCellEditing();
        jt.setLargeModel(true);
        jt.isLargeModel();
        jt.setAnchorSelectionPath(folderPath);
        jt.getAnchorSelectionPath();
        jt.setLeadSelectionPath(folderPath);
        dump("JTree  R_swing_is_truth field-shadow knobs", warnings);

        // -- Renderer round-trip (default + custom) + convertValueToText --
        if (!(jt.getCellRenderer() instanceof DefaultTreeCellRenderer)) {
            throw new AssertionError("default cell renderer should be a DefaultTreeCellRenderer");
        }
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(null);
        renderer.getLeafIcon();
        renderer.setOpenIcon(null);
        renderer.getOpenIcon();
        renderer.setClosedIcon(null);
        renderer.getClosedIcon();
        jt.setCellRenderer(renderer);
        jt.convertValueToText("v", false, false, true, 0, false);
        dump("JTree  cell renderer round-trip", warnings);

        // -- Editing's benign surface (no edit in flight; no WARN) --
        jt.isEditable();
        jt.setEditable(false);
        jt.isPathEditable(folderPath);
        jt.getCellEditor();
        jt.isEditing();
        jt.stopEditing();
        jt.cancelEditing();
        jt.getEditingPath();
        dump("JTree  editing benign surface", warnings);

        // -- Rename-in-place lifecycle (D_jtree_rename_in_place) — real edit, no WARN. --
        jt.setEditable(true);
        jt.expandPath(folderPath);
        jt.startEditingAtPath(folderPath);
        if (!jt.isEditing() || !folderPath.equals(jt.getEditingPath())) {
            throw new AssertionError("expected editing folderPath in flight, got " + jt.getEditingPath());
        }
        jt.cancelEditing();                                // discard, no write-back
        if (jt.isEditing()) throw new AssertionError("expected editing cancelled");
        jt.startEditingAtPath(folderPath);
        boolean stopped = jt.stopEditing();                // commit → valueForPathChanged
        if (!stopped || jt.isEditing()) throw new AssertionError("expected commit to stop editing");
        dump("JTree  rename-in-place (D_jtree_rename_in_place)", warnings);

        // -- DnD config (D_jtree_row_dnd — graduated, no WARN) --
        jt.setDragEnabled(true);
        jt.getDragEnabled();
        jt.setDragEnabled(false);
        jt.setDropMode(DropMode.ON);
        jt.setDropMode(DropMode.INSERT);
        jt.setDropMode(DropMode.ON_OR_INSERT);
        jt.setDropMode(DropMode.USE_SELECTION);
        jt.getDropMode();
        dump("JTree  DnD config", warnings);

        // -- Scrollable interface --
        jt.getPreferredScrollableViewportSize();
        jt.getScrollableUnitIncrement(new java.awt.Rectangle(0, 0, 100, 100),
                javax.swing.SwingConstants.VERTICAL, 1);
        jt.getScrollableBlockIncrement(new java.awt.Rectangle(0, 0, 100, 100),
                javax.swing.SwingConstants.VERTICAL, 1);
        jt.getScrollableTracksViewportWidth();
        jt.getScrollableTracksViewportHeight();
        dump("JTree  Scrollable interface", warnings);

        // -- L&F stubs --
        if (!"TreeUI".equals(jt.getUIClassID())) {
            throw new AssertionError("getUIClassID should be TreeUI");
        }
        jt.updateUI();
        jt.getUI();
        jt.setUI(null);
        dump("JTree  L&F stubs", warnings);

        // -- Intentional-WARN paths --
        // An arbitrary editor component stays out per D_gap_severity_triage sub-bucket (b); only
        // the built-in text rename (D_jtree_rename_in_place) is reproduced, so setCellEditor WARNs.
        jt.setCellEditor(new vaadinx.swing.DefaultCellEditor(new vaadinx.swing.JTextField()));
        assertExactlyOneWarn(warnings, "setCellEditor");

        // The emulator owns expansion, so the vetoable pre-expansion hook fires and is silent.
        javax.swing.event.TreeWillExpandListener twel = new javax.swing.event.TreeWillExpandListener() {
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event) { }
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) { }
        };
        jt.addTreeWillExpandListener(twel);
        jt.removeTreeWillExpandListener(twel);
        dump("JTree  add/removeTreeWillExpandListener (silent)", warnings);

        // Type-ahead node search isn't modelled.
        jt.getNextMatch("a", 0, javax.swing.text.Position.Bias.Forward);
        assertExactlyOneWarn(warnings, "getNextMatch");

        // Renderer selection chrome — the values round-trip and WARN no more.
        // The theme still owns the *painting* (D_theme_is_lookandfeel), but
        // R_decline_effect_only says only the effect was ever droppable, so
        // dropping the value was a violation rather than a documented gap.
        renderer.setTextSelectionColor(java.awt.Color.WHITE);
        renderer.setBackgroundSelectionColor(java.awt.Color.BLUE);
        assertEquals(java.awt.Color.WHITE, renderer.getTextSelectionColor());
        assertEquals(java.awt.Color.BLUE, renderer.getBackgroundSelectionColor());
        dump("DefaultTreeCellRenderer  selection colours (state kept, silent)", warnings);

        WarnDump.println();
        WarnDump.println("=== trees API-surface: WARN-free buckets clean; intentional WARNs accounted ===");
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the Trees exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }

    private static void assertExactlyOneWarn(List<String> warnings, String fragment) {
        long matching = warnings.stream().filter(w -> w.contains(fragment)).count();
        if (warnings.size() != 1 || matching != 1) {
            String msg = "Expected exactly one WARN containing '" + fragment + "', got: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
