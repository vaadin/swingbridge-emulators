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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridMultiSelectionModel;
import com.vaadin.flow.component.grid.GridSingleSelectionModel;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.provider.DataChangeEvent;
import com.vaadin.flow.data.provider.DataChangeEvent.DataRefreshEvent;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalDataProvider;
import com.vaadin.flow.data.provider.hierarchy.HierarchicalQuery;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import kotlin.sequences.SequencesKt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Surrogate tests for {@link SJTree} (SD_sjtree) — the TreeGrid&lt;Object&gt; +
 * TreeModel-as-source-of-truth + lazy {@link HierarchicalDataProvider}
 * + TreeSelectionModel path-bridge + expansion bridge + item-click →
 * SMouseEvent wire. Emulator delegation / re-source lives in
 * {@code vaadinx.swing.JTreeTest}.
 */
class SJTreeTest extends AbstractKaribuTest {

    /**
     * <pre>
     * root
     * ├── colors ── blue, red
     * └── sports ── soccer
     * </pre>
     */
    private static final class Fixture {
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        final DefaultMutableTreeNode colors = new DefaultMutableTreeNode("colors");
        final DefaultMutableTreeNode blue = new DefaultMutableTreeNode("blue");
        final DefaultMutableTreeNode red = new DefaultMutableTreeNode("red");
        final DefaultMutableTreeNode sports = new DefaultMutableTreeNode("sports");
        final DefaultMutableTreeNode soccer = new DefaultMutableTreeNode("soccer");
        final DefaultTreeModel model = new DefaultTreeModel(root);

        Fixture() {
            root.add(colors);
            colors.add(blue);
            colors.add(red);
            root.add(sports);
            sports.add(soccer);
        }

        TreePath path(Object... nodes) {
            return new TreePath(nodes);
        }
    }

    private static SJTree attach(SJTree tree) {
        UI.getCurrent().add(tree);
        return tree;
    }

    /**
     * Karibu's visible-row walk, materialised. {@code _rowSequence} is the one
     * helper in Karibu's surface with no Java-shaped form — it returns a
     * {@code kotlin.sequences.Sequence}, so the stdlib facade does the
     * conversion (karibu-testing#214 asks for a List-returning alternative).
     */
    private static List<Object> visibleRows(SJTree tree) {
        return SequencesKt.toList(GridKt._rowSequence(tree, null));
    }

    /** The single {@link DataRefreshEvent} among the recorded events. */
    private static DataRefreshEvent<?> singleRefresh(List<DataChangeEvent<?>> events) {
        final List<DataRefreshEvent<?>> refreshes = new ArrayList<>();
        for (DataChangeEvent<?> e : events) {
            if (e instanceof DataRefreshEvent<?> r) {
                refreshes.add(r);
            }
        }
        assertEquals(1, refreshes.size(), "expected exactly one refresh event, got " + events);
        return refreshes.get(0);
    }

    /** Subscribes to the tree's data provider and returns the list events land in. */
    private static List<DataChangeEvent<?>> recordDataEvents(SJTree tree) {
        final List<DataChangeEvent<?>> events = new ArrayList<>();
        tree.getDataProvider().addDataProviderListener(events::add);
        return events;
    }

    @SuppressWarnings("unchecked")
    private static ComponentRenderer<?, Object> hierarchyRenderer(SJTree tree) {
        final Grid.Column<Object> column = tree.getColumnByKey("tree");
        assertNotNull(column, "the hierarchy column must be keyed \"tree\"");
        return (ComponentRenderer<?, Object>) column.getRenderer();
    }

    // --- Ctors + model ---------------------------------------------------

    @Test
    @DisplayName("model ctor installs the model and expands the root per JDK")
    void modelCtorExpandsRoot() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        assertSame(f.model, tree.getModel());
        assertTrue(tree.isExpanded(f.root));
        assertEquals(List.of(f.root), GridKt._getRootItems(tree));
    }

    @Test
    @DisplayName("no-arg ctor installs an empty hidden-root DefaultTreeModel")
    void noArgCtorHiddenRoot() {
        final SJTree tree = attach(new SJTree());
        assertInstanceOf(DefaultTreeModel.class, tree.getModel());
        assertFalse(tree.isRootVisible());
        assertEquals(List.of(), GridKt._getRootItems(tree));
    }

    @Test
    @DisplayName("surrogate subscribes itself to the installed model (UI-delegate pattern)")
    void surrogateSubscribesToModel() {
        final Fixture f = new Fixture();
        final SJTree tree = new SJTree(f.model);
        assertTrue(Arrays.asList(f.model.getTreeModelListeners()).contains(tree));
    }

    @Test
    @DisplayName("setModel swaps the listener subscription and clears selection")
    void setModelSwapsSubscription() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.setSelectionPath(f.path(f.root, f.colors));
        assertEquals(1, tree.getSelectionCount());

        final Fixture f2 = new Fixture();
        tree.setModel(f2.model);
        assertSame(f2.model, tree.getModel());
        assertFalse(Arrays.asList(f.model.getTreeModelListeners()).contains(tree),
                "old model must lose the subscription");
        assertTrue(Arrays.asList(f2.model.getTreeModelListeners()).contains(tree));
        assertEquals(0, tree.getSelectionCount(), "stale paths into the prior model are meaningless");
        assertEquals(List.of(f2.root), GridKt._getRootItems(tree));
    }

    // --- SD_sjtree_lazy_provider: lazy live-read data provider ------------------------------

    @Test
    @DisplayName("children are fetched lazily — only when a node expands")
    void childrenFetchLazily() {
        final Fixture f = new Fixture();
        final List<Object> fetched = new ArrayList<>();
        final DefaultTreeModel counting = new DefaultTreeModel(f.root) {
            @Override
            public Object getChild(Object parent, int index) {
                fetched.add(parent);
                return super.getChild(parent, index);
            }
        };
        final SJTree tree = attach(new SJTree(counting));
        visibleRows(tree);  // force a full visible-row fetch
        assertTrue(fetched.contains(f.root), "root is expanded per JDK, so its children fetch");
        assertFalse(fetched.contains(f.colors), "collapsed node's children must not fetch");

        tree.expand(f.colors);
        visibleRows(tree);
        assertTrue(fetched.contains(f.colors), "expansion triggers the lazy fetch");
    }

    @Test
    @DisplayName("data provider getParent walks the parent map")
    void dataProviderGetParent() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        visibleRows(tree);
        assertSame(f.root, tree.getDataProvider().getParent(f.colors));
        assertNull(tree.getDataProvider().getParent(f.root), "root has no Vaadin parent");
    }

    @Test
    @DisplayName("data provider getParent returns null for a hidden root's children")
    void dataProviderGetParentHiddenRoot() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.setRootVisible(false);
        visibleRows(tree);
        assertNull(tree.getDataProvider().getParent(f.colors),
                "hidden-root children are Vaadin top-level");
    }

    @Test
    @DisplayName("data provider getItemIndex resolves via TreeModel getIndexOfChild")
    void dataProviderGetItemIndex() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final HierarchicalDataProvider<Object, ?> dp = tree.getDataProvider();
        assertEquals(1, dp.getItemIndex(f.sports, new HierarchicalQuery<>(null, f.root)));
        assertEquals(0, dp.getItemIndex(f.root, new HierarchicalQuery<>(null, null)));
    }

    // --- SD_sjtree_is_model_listener: TreeModel events drive Grid refresh ------------------------

    @Test
    @DisplayName("treeNodesChanged refreshes the changed node only")
    void treeNodesChangedRefreshesNode() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final List<DataChangeEvent<?>> events = recordDataEvents(tree);
        f.blue.setUserObject("navy");
        f.model.nodeChanged(f.blue);
        final DataRefreshEvent<?> refresh = singleRefresh(events);
        assertSame(f.blue, refresh.getItem());
        assertFalse(refresh.isRefreshChildren());
    }

    @Test
    @DisplayName("treeNodesInserted re-fetches the parent subtree and the new child renders")
    void treeNodesInsertedRefetchesParent() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.expand(f.colors);
        final List<DataChangeEvent<?>> events = recordDataEvents(tree);

        final DefaultMutableTreeNode green = new DefaultMutableTreeNode("green");
        f.model.insertNodeInto(green, f.colors, 0);

        final DataRefreshEvent<?> refresh = singleRefresh(events);
        assertSame(f.colors, refresh.getItem());
        assertTrue(refresh.isRefreshChildren());
        assertTrue(visibleRows(tree).contains(green));
    }

    @Test
    @DisplayName("treeNodesRemoved prunes the removed node")
    void treeNodesRemovedPrunes() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.expand(f.colors);
        visibleRows(tree);

        f.model.removeNodeFromParent(f.blue);
        assertFalse(visibleRows(tree).contains(f.blue));
        assertNull(tree.getPathForNode(f.blue), "removed node is no longer reachable");
    }

    @Test
    @DisplayName("treeStructureChanged refreshes everything")
    void treeStructureChangedRefreshesAll() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final List<DataChangeEvent<?>> events = recordDataEvents(tree);
        f.model.nodeStructureChanged(f.root);
        assertTrue(events.stream().anyMatch(e -> !(e instanceof DataRefreshEvent)),
                "structure change must refreshAll");
    }

    // --- SD_sjtree_selection_bridge: selection bridge ------------------------------------------

    @Test
    @DisplayName("JDK to peer — setSelectionPath pushes to Grid selection")
    void jdkToPeerSelection() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.getTreeSelectionModel().setSelectionPath(f.path(f.root, f.colors));
        assertEquals(Set.of(f.colors), tree.getSelectedItems());
    }

    @Test
    @DisplayName("peer to JDK — Grid select reconstructs the TreePath")
    void peerToJdkSelection() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        visibleRows(tree);  // populate the parent map
        GridKt._select(tree, f.sports);
        final TreePath p = tree.getSelectionPath();
        assertNotNull(p);
        assertEquals(List.of(f.root, f.sports), Arrays.asList(p.getPath()));
        assertSame(f.sports, tree.getLastSelectedPathComponent());
    }

    @Test
    @DisplayName("selection round-trip does not infinite-loop")
    void selectionRoundTripTerminates() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        visibleRows(tree);
        tree.setSelectionPath(f.path(f.root, f.colors));
        GridKt._select(tree, f.sports);
        assertEquals(Set.of(f.sports), tree.getSelectedItems());
        assertEquals(1, tree.getSelectionCount());
        assertSame(f.sports, tree.getSelectionPath().getLastPathComponent());
    }

    @Test
    @DisplayName("selection convenience API routes through the TreeSelectionModel")
    void selectionConvenienceApi() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.addSelectionPath(f.path(f.root, f.colors));
        tree.addSelectionPath(f.path(f.root, f.sports));
        assertEquals(2, tree.getSelectionCount());
        assertTrue(tree.isPathSelected(f.path(f.root, f.colors)));
        tree.removeSelectionPath(f.path(f.root, f.colors));
        assertEquals(1, tree.getSelectionCount());
        tree.clearSelection();
        assertTrue(tree.isSelectionEmpty());
        assertNull(tree.getLastSelectedPathComponent());
    }

    @Test
    @DisplayName("default mode DISCONTIGUOUS maps to Grid MULTI, SINGLE_TREE_SELECTION to SINGLE")
    void selectionModeMapping() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        assertEquals(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION,
                tree.getTreeSelectionModel().getSelectionMode());
        assertInstanceOf(GridMultiSelectionModel.class, tree.getSelectionModel());

        // JDK idiom: tree.getSelectionModel().setSelectionMode(...) — observed
        // via the model's "selectionMode" PCE, there is no JTree.setSelectionMode.
        tree.getTreeSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        assertInstanceOf(GridSingleSelectionModel.class, tree.getSelectionModel());
    }

    @Test
    @DisplayName("selection bridge survives a selection-mode swap")
    void selectionBridgeSurvivesModeSwap() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        visibleRows(tree);
        tree.getTreeSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        GridKt._select(tree, f.colors);
        assertSame(f.colors, tree.getSelectionPath().getLastPathComponent());
    }

    @Test
    @DisplayName("setSelectionModel swaps the model and the bridge follows")
    void setSelectionModelSwaps() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        visibleRows(tree);
        final DefaultTreeSelectionModel fresh = new DefaultTreeSelectionModel();
        tree.setSelectionModel(fresh);
        assertSame(fresh, tree.getTreeSelectionModel());
        GridKt._select(tree, f.colors);
        assertSame(f.colors, fresh.getSelectionPath().getLastPathComponent());
        // JDK→peer through the new model too.
        fresh.setSelectionPath(f.path(f.root, f.sports));
        assertEquals(Set.of(f.sports), tree.getSelectedItems());
    }

    @Test
    @DisplayName("setSelectionModel null installs a fresh default")
    void setSelectionModelNullInstallsDefault() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.setSelectionModel(null);
        assertInstanceOf(DefaultTreeSelectionModel.class, tree.getTreeSelectionModel());
    }

    // --- rootVisible (SD_sjtree_lazy_provider reshaping) -------------------------------------

    @Test
    @DisplayName("setRootVisible false promotes the root's children to top level")
    void rootVisibleReshapesTopLevel() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        assertEquals(List.of(f.root), GridKt._getRootItems(tree));
        tree.setRootVisible(false);
        assertEquals(List.of(f.colors, f.sports), GridKt._getRootItems(tree));
        tree.setRootVisible(true);
        assertEquals(List.of(f.root), GridKt._getRootItems(tree));
    }

    // --- SD_sjtree_expansion: expansion bridge -------------------------------------------

    @Test
    @DisplayName("expandPath expands the node and its ancestors and fires treeExpanded")
    void expandPathExpandsAncestors() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.collapse(f.root);
        final List<TreePath> expanded = new ArrayList<>();
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                expanded.add(event.getPath());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
            }
        });
        tree.expandPath(f.path(f.root, f.colors));
        assertTrue(tree.isExpanded(f.path(f.root, f.colors)));
        assertTrue(tree.isExpanded(f.path(f.root)));
        assertTrue(expanded.stream().anyMatch(p -> p.getLastPathComponent() == f.colors));
        assertTrue(expanded.stream().anyMatch(p -> p.getLastPathComponent() == f.root));
    }

    @Test
    @DisplayName("collapsePath fires treeCollapsed and leaves ancestors expanded")
    void collapsePathFiresCollapsed() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.expandPath(f.path(f.root, f.colors));
        final List<TreePath> collapsed = new ArrayList<>();
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                collapsed.add(event.getPath());
            }
        });
        tree.collapsePath(f.path(f.root, f.colors));
        assertEquals(1, collapsed.size());
        assertSame(f.colors, collapsed.get(0).getLastPathComponent());
        assertTrue(tree.isExpanded(f.path(f.root)), "ancestors stay as-is per JDK");
    }

    @Test
    @DisplayName("removeTreeExpansionListener tears the wire down")
    void removeTreeExpansionListener() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final Counter count = new Counter();
        final TreeExpansionListener l = new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                count.inc();
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
            }
        };
        tree.addTreeExpansionListener(l);
        tree.expandPath(f.path(f.root, f.colors));
        final int after = count.get();
        assertTrue(after > 0);
        tree.removeTreeExpansionListener(l);
        tree.collapsePath(f.path(f.root, f.colors));
        tree.expandPath(f.path(f.root, f.colors));
        assertEquals(after, count.get());
    }

    @Test
    @DisplayName("isExpanded path requires every ancestor expanded (JDK contract)")
    void isExpandedPathRequiresAncestors() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.expand(f.colors);
        tree.collapse(f.root);
        assertTrue(tree.isExpanded(f.colors), "node-level expansion state is kept");
        assertFalse(tree.isExpanded(f.path(f.root, f.colors)), "but the path is not displayed");
        assertTrue(tree.isCollapsed(f.path(f.root, f.colors)));
    }

    @Test
    @DisplayName("makeVisible expands ancestors only")
    void makeVisibleExpandsAncestorsOnly() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.collapse(f.root);
        tree.makeVisible(f.path(f.root, f.colors, f.blue));
        assertTrue(tree.isVisible(f.path(f.root, f.colors, f.blue)));
        assertFalse(tree.isExpanded(f.blue), "the node itself is not expanded");
    }

    @Test
    @DisplayName("getExpandedDescendants reflects lazy expansion state")
    void getExpandedDescendants() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.expandPath(f.path(f.root, f.colors));
        final List<TreePath> expanded = tree.getExpandedDescendants(f.path(f.root));
        assertNotNull(expanded);
        assertEquals(Set.of(f.root, f.colors),
                expanded.stream().map(TreePath::getLastPathComponent).collect(Collectors.toSet()));
        tree.collapse(f.root);
        assertNull(tree.getExpandedDescendants(f.path(f.root)));
    }

    @Test
    @DisplayName("addTreeWillExpandListener drops and WARNs (no Vaadin vetoable hook)")
    void treeWillExpandListenerDrops() {
        final Fixture f = new Fixture();
        final SJTree tree = new SJTree(f.model);
        // D_gap_severity_triage sub-bucket (a): stores nowhere, never fires, must not throw.
        final TreeWillExpandListener l = new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) {
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        };
        tree.addTreeWillExpandListener(l);
        tree.removeTreeWillExpandListener(l);
    }

    // --- SD_sjtree_row_api: visible-row walk --------------------------------------------

    @Test
    @DisplayName("row API addresses the flattened visible order across collapsed subtrees")
    void rowApiFlattensVisibleOrder() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        // root expanded, both children collapsed: root, colors, sports.
        assertEquals(3, tree.getRowCount());
        assertSame(f.colors, tree.getPathForRow(1).getLastPathComponent());
        assertEquals(-1, tree.getRowForPath(f.path(f.root, f.colors, f.blue)),
                "collapsed ancestor hides the node");

        tree.expand(f.colors);
        // root, colors, blue, red, sports.
        assertEquals(5, tree.getRowCount());
        assertEquals(2, tree.getRowForPath(f.path(f.root, f.colors, f.blue)));
        assertSame(f.sports, tree.getPathForRow(4).getLastPathComponent());
        assertNull(tree.getPathForRow(5));
        assertNull(tree.getPathForRow(-1));
    }

    @Test
    @DisplayName("row API respects rootVisible false")
    void rowApiRespectsHiddenRoot() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.setRootVisible(false);
        // top level: colors, sports.
        assertEquals(2, tree.getRowCount());
        assertSame(f.colors, tree.getPathForRow(0).getLastPathComponent());
        // Paths stay rooted at the (hidden) model root.
        assertEquals(List.of(f.root, f.colors), Arrays.asList(tree.getPathForRow(0).getPath()));
    }

    // --- Mouse bridge + click stash (SD_sjtree_row_api) ----------------------------------

    @Test
    @DisplayName("item click dispatches press-release-click and the stash resolves the row")
    void itemClickDispatchesAndStashes() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final List<Integer> ids = new ArrayList<>();
        final int[] stashedRow = { -2 };
        final TreePath[] stashedPath = { null };
        tree.addMouseListener(new SMouseAdapter() {
            @Override
            public void mousePressed(SMouseEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void mouseReleased(SMouseEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void mouseClicked(SMouseEvent e) {
                ids.add(e.getID());
                stashedRow[0] = tree.getRowForLocation(e.getX(), e.getY());
                stashedPath[0] = tree.getPathForLocation(e.getX(), e.getY());
            }
        });
        GridKt._clickItem(tree, 1);  // "colors"
        assertEquals(
                List.of(SMouseEvent.MOUSE_PRESSED, SMouseEvent.MOUSE_RELEASED, SMouseEvent.MOUSE_CLICKED),
                ids);
        assertEquals(1, stashedRow[0]);
        assertSame(f.colors, stashedPath[0].getLastPathComponent());
        // Stash cleared after the dispatch.
        assertEquals(-1, tree.getRowForLocation(0, 0));
        assertNull(tree.getPathForLocation(0, 0));
    }

    @Test
    @DisplayName("right-click carries BUTTON3 and isPopupTrigger")
    void rightClickCarriesButton3() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final List<SMouseEvent> seen = new ArrayList<>();
        tree.addMouseListener(new SMouseAdapter() {
            @Override
            public void mousePressed(SMouseEvent e) {
                seen.add(e);
            }
        });
        GridKt._clickItem(tree, 2, 2);  // row "sports", browser button 2 = right
        assertEquals(1, seen.size());
        final SMouseEvent e = seen.get(0);
        assertEquals(SMouseEvent.BUTTON3, e.getButton());
        assertTrue(e.isPopupTrigger());
    }

    // --- SD_sjtree_hierarchy_render: hierarchy-column render seam ----------------------------------

    @Test
    @DisplayName("default cell render is a fresh Span of the node text")
    void defaultCellRenderIsFreshSpan() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final ComponentRenderer<?, Object> renderer = hierarchyRenderer(tree);
        final Component cell1 = renderer.createComponent(f.blue);
        final Component cell2 = renderer.createComponent(f.blue);
        final Span span1 = assertInstanceOf(Span.class, cell1);
        assertEquals("blue", span1.getText());
        assertNotSame(cell1, cell2, "fresh per call — D_jcombobox DOM-adoption guard");
    }

    @Test
    @DisplayName("setHierarchyComponentProvider swaps render behaviour without re-installing the renderer")
    void hierarchyComponentProviderIsFunctionLevel() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        final Grid.Column<Object> column = tree.getColumnByKey("tree");
        assertNotNull(column);
        final Object rendererBefore = column.getRenderer();
        tree.setHierarchyComponentProvider(node -> new Span("custom:" + node));
        assertSame(rendererBefore, column.getRenderer(),
                "function-level seam: the Vaadin renderer never re-installs");
        final Span cell = assertInstanceOf(Span.class, hierarchyRenderer(tree).createComponent(f.blue));
        assertEquals("custom:blue", cell.getText());
    }

    // --- Misc -----------------------------------------------------------------

    @Test
    @DisplayName("scrollPathToVisible expands ancestors")
    void scrollPathToVisibleExpandsAncestors() {
        final Fixture f = new Fixture();
        final SJTree tree = attach(new SJTree(f.model));
        tree.collapse(f.root);
        tree.scrollPathToVisible(f.path(f.root, f.colors, f.blue));
        assertTrue(tree.isExpanded(f.path(f.root, f.colors)));
    }

    @Test
    @DisplayName("getUIClassID is TreeUI")
    void uiClassIdIsTreeUi() {
        assertEquals("TreeUI", new SJTree().getUIClassID());
    }
}
