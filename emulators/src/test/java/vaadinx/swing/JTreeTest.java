/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJTree;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import vaadinx.swing.tree.DefaultTreeCellRenderer;

import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.DropMode;
import javax.swing.SwingConstants;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Emulator tests for the thin {@code JTree} shell over
 * {@code com.vaadin.swingbridge.surrogates.SJTree} (SD_sjtree + D_jtree). The surrogate carries the
 * TreeGrid / data-provider / selection-bridge / expansion / click-stash
 * plumbing — that coverage lives in {@code com.vaadin.swingbridge.surrogates.SJTreeTest}. These focus
 * on the emulator's job: JDK-shape delegation, two-layer event re-sourcing
 * (TreeSelectionListener + TreeExpansionListener + MouseListener), the JDK
 * TreeCellRenderer bridge, R_leaf_peer_lockdown lock-down, R_swing_is_truth field shadows, and the D_jtree_row_dnd
 * TreeGrid-row DnD path.
 */
class JTreeTest extends AbstractKaribuTest {

    /**
     * <pre>
     * root
     * ├── colors ── blue, red
     * └── sports ── soccer
     * </pre>
     */
    private static class Fixture {
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

    private static SJTree peerOf(JTree tree) {
        return (SJTree) tree.getPeer();
    }

    private static SJTree attachPeer(JTree tree) {
        SJTree p = peerOf(tree);
        UI.getCurrent().add(p);
        return p;
    }

    /** The rendered text of the tree column's cell at {@code row}. */
    private static String cellText(SJTree peer, int row) {
        return ((Span) GridKt._getCellComponent(peer, row, "tree")).getText();
    }

    /** The user objects of the peer's visible root items, in order. */
    private static List<Object> rootUserObjects(SJTree peer) {
        return GridKt._getRootItems(peer).stream()
                .map(item -> ((DefaultMutableTreeNode) item).getUserObject())
                .toList();
    }

    // --- Peer + R_leaf_peer_lockdown --------------------------------------------------------

    @Test
    @DisplayName("peer is SJTree per surrogate-first thin-down")
    void peerIsSjTree() {
        assertInstanceOf(SJTree.class, new JTree().getPeer());
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected ctor on the JDK leaf")
    void noProtectedCtorOnTheJdkLeaf() {
        List<java.lang.reflect.Constructor<?>> protectedCtors =
                Arrays.stream(JTree.class.getDeclaredConstructors())
                        .filter(c -> java.lang.reflect.Modifier.isProtected(c.getModifiers()))
                        .toList();
        assertTrue(protectedCtors.isEmpty(),
                "R_leaf_peer_lockdown: no protected ctor allowed on JDK-leaf JTree; got: " + protectedCtors);
    }

    // --- Ctors (all JDK forms) ----------------------------------------------

    @Test
    @DisplayName("no-arg ctor installs the JDK demo model")
    void noArgCtorInstallsTheDemoModel() {
        JTree tree = new JTree();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        assertEquals("JTree", root.getUserObject());
        assertEquals(3, root.getChildCount());
        assertTrue(tree.isRootVisible());
    }

    @Test
    @DisplayName("TreeNode ctor wraps in a DefaultTreeModel")
    void treeNodeCtorWrapsInADefaultTreeModel() {
        Fixture f = new Fixture();
        JTree tree = new JTree((TreeNode) f.root);
        assertInstanceOf(DefaultTreeModel.class, tree.getModel());
        assertSame(f.root, tree.getModel().getRoot());
    }

    @Test
    @DisplayName("TreeNode + asksAllowsChildren ctor passes the flag through")
    void treeNodeAsksAllowsChildrenCtor() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.root, true);
        assertTrue(((DefaultTreeModel) tree.getModel()).asksAllowsChildren());
    }

    @Test
    @DisplayName("array ctor wraps entries under a hidden synthetic root with root handles")
    void arrayCtorWrapsUnderAHiddenRoot() {
        JTree tree = new JTree(new Object[]{"a", "b"});
        attachPeer(tree);
        assertFalse(tree.isRootVisible());
        assertTrue(tree.getShowsRootHandles());
        assertEquals(List.of("a", "b"), rootUserObjects(peerOf(tree)));
    }

    @Test
    @DisplayName("vector and hashtable ctors wrap entries the same way")
    void vectorAndHashtableCtors() {
        JTree v = new JTree(new Vector<>(List.of("x", "y")));
        attachPeer(v);
        assertEquals(2, GridKt._getRootItems(peerOf(v)).size());

        JTree h = new JTree(new Hashtable<>(Map.of("k", "v")));
        attachPeer(h);
        assertEquals(List.of("k"), rootUserObjects(peerOf(h)));
    }

    @Test
    @DisplayName("nested arrays recurse into subtrees")
    void nestedArraysRecurse() {
        JTree tree = new JTree(new Object[]{"flat", new Object[]{"p", "q"}});
        attachPeer(tree);
        List<Object> top = GridKt._getRootItems(peerOf(tree));
        assertEquals(2, top.size());
        assertEquals(2, ((DefaultMutableTreeNode) top.get(1)).getChildCount());
    }

    // --- Model delegation -----------------------------------------------------

    @Test
    @DisplayName("setModel delegates to the surrogate and fires model PCE")
    void setModelDelegatesAndFiresPce() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        List<PropertyChangeEvent> seen = new ArrayList<>();
        tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, seen::add);
        Fixture f2 = new Fixture();
        tree.setModel(f2.model);
        assertSame(f2.model, tree.getModel());
        assertSame(f2.model, peerOf(tree).getModel());
        assertSame(f2.model, assertSingle(seen).getNewValue());
    }

    // --- Two-layer TreeSelectionListener re-source (D_jtree_two_layer_resource.1) --------------------

    @Test
    @DisplayName("addTreeSelectionListener fires with source rebound to the emulator")
    void treeSelectionListenerSourceIsTheEmulator() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        List<TreeSelectionEvent> events = new ArrayList<>();
        tree.addTreeSelectionListener(events::add);
        tree.setSelectionPath(f.path(f.root, f.colors));
        assertFalse(events.isEmpty(), "selection change must reach the emulator's listener");
        TreeSelectionEvent last = events.get(events.size() - 1);
        assertSame(tree, last.getSource(), "source must be rebound to the emulator JTree");
        assertSame(f.colors, last.getPath().getLastPathComponent());
    }

    @Test
    @DisplayName("setSelectionModel moves the re-source handler to the new model")
    void setSelectionModelMovesTheResourceHandler() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        List<TreeSelectionEvent> events = new ArrayList<>();
        tree.addTreeSelectionListener(events::add);
        DefaultTreeSelectionModel fresh = new DefaultTreeSelectionModel();
        tree.setSelectionModel(fresh);
        tree.setSelectionPath(f.path(f.root, f.sports));
        assertFalse(events.isEmpty(), "after model swap the handler must still fire");
        assertSame(tree, events.get(events.size() - 1).getSource());
        assertSame(fresh, tree.getSelectionModel());
    }

    @Test
    @DisplayName("getSelectionModel is JDK-shaped and reads through the surrogate")
    void getSelectionModelReadsThroughTheSurrogate() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        // The surrogate can't carry the JDK name (Grid.getSelectionModel clash,
        // SD_sjtree_selection_bridge); the emulator reads through getTreeSelectionModel().
        assertSame(peerOf(tree).getTreeSelectionModel(), tree.getSelectionModel());
    }

    @Test
    @DisplayName("selection delegation — paths and rows")
    void selectionDelegationPathsAndRows() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        tree.expandPath(f.path(f.root, f.colors));
        // visible: root=0, colors=1, blue=2, red=3, sports=4.
        tree.setSelectionRow(2);
        assertSame(f.blue, tree.getLastSelectedPathComponent());
        assertEquals(1, tree.getSelectionCount());
        assertTrue(tree.isRowSelected(2), "row queries resolve via the installed RowMapper");
        assertEquals(2, tree.getMinSelectionRow());
        assertEquals(2, tree.getMaxSelectionRow());
        assertArrayEquals(new int[]{2}, tree.getSelectionRows());

        tree.addSelectionInterval(3, 4);
        assertEquals(3, tree.getSelectionCount());
        tree.removeSelectionInterval(3, 3);
        assertEquals(2, tree.getSelectionCount());
        tree.clearSelection();
        assertTrue(tree.isSelectionEmpty());
    }

    // --- Two-layer TreeExpansionListener re-source (D_jtree_two_layer_resource.2) ---------------------

    @Test
    @DisplayName("expansion events re-source with the emulator as source")
    void expansionEventsResourceToTheEmulator() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        List<TreeExpansionEvent> expanded = new ArrayList<>();
        List<TreeExpansionEvent> collapsed = new ArrayList<>();
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent event) {
                expanded.add(event);
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event) {
                collapsed.add(event);
            }
        });
        tree.expandPath(f.path(f.root, f.colors));
        assertFalse(expanded.isEmpty());
        TreeExpansionEvent lastExpanded = expanded.get(expanded.size() - 1);
        assertSame(tree, lastExpanded.getSource());
        assertSame(f.colors, lastExpanded.getPath().getLastPathComponent());

        tree.collapsePath(f.path(f.root, f.colors));
        assertSame(tree, assertSingle(collapsed).getSource());
    }

    @Test
    @DisplayName("expansion delegation — rows, makeVisible, hasBeenExpanded")
    void expansionDelegation() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        assertEquals(3, tree.getRowCount());
        tree.expandRow(1);  // colors
        assertEquals(5, tree.getRowCount());
        assertTrue(tree.isExpanded(1));
        assertTrue(tree.isExpanded(f.path(f.root, f.colors)));
        tree.collapseRow(1);
        assertTrue(tree.isCollapsed(f.path(f.root, f.colors)));
        assertEquals(3, tree.getRowCount());

        tree.makeVisible(f.path(f.root, f.colors, f.blue));
        assertTrue(tree.isVisible(f.path(f.root, f.colors, f.blue)));

        tree.scrollPathToVisible(f.path(f.root, f.sports, f.soccer));
        assertTrue(tree.isExpanded(f.path(f.root, f.sports)));

        List<TreePath> descendants = new ArrayList<>();
        tree.getExpandedDescendants(f.path(f.root)).asIterator().forEachRemaining(descendants::add);
        assertTrue(descendants.stream().anyMatch(p -> p.getLastPathComponent() == f.root));
    }

    /** Records will-expand / expansion events as {@code "will+node"}, {@code "exp node"}, …; vetoes expanding {@code veto}. */
    private static List<String> recordExpansion(JTree tree, Object veto) {
        List<String> ev = new ArrayList<>();
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent e) throws ExpandVetoException {
                ev.add("will+" + e.getPath().getLastPathComponent());
                if (e.getPath().getLastPathComponent() == veto) throw new ExpandVetoException(e);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent e) {
                ev.add("will-" + e.getPath().getLastPathComponent());
            }
        });
        tree.addTreeExpansionListener(new TreeExpansionListener() {
            @Override
            public void treeExpanded(TreeExpansionEvent e) {
                ev.add("exp " + e.getPath().getLastPathComponent());
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent e) {
                ev.add("col " + e.getPath().getLastPathComponent());
            }
        });
        return ev;
    }

    /** A toggle the user makes in the browser: TreeGrid's own user-originated expand or collapse. */
    private static void browserToggle(SJTree peer, Object node, boolean expand) {
        try {
            java.lang.reflect.Method m = com.vaadin.flow.component.treegrid.TreeGrid.class.getDeclaredMethod(
                    expand ? "expand" : "collapse", java.util.Collection.class, boolean.class);
            m.setAccessible(true);
            m.invoke(peer, List.of(node), true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("expansion, veto, selection and model removal behave as JDK 25 does, same script")
    void expansionMatchesTheJdk() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        List<String> ev = recordExpansion(tree, f.sports);
        List<String> out = new ArrayList<>();

        out.add("rows0=" + tree.getRowCount());
        tree.setSelectionPath(f.path(f.root, f.colors, f.blue));
        tree.expandPath(f.path(f.root, f.colors));
        ev.add("|");
        out.add("rows1=" + tree.getRowCount() + " row(blue)=" + tree.getRowForPath(f.path(f.root, f.colors, f.blue)));
        tree.collapsePath(f.path(f.root, f.colors));
        ev.add("|");
        out.add("sel after collapse=" + tree.getSelectionPath());
        tree.expandPath(f.path(f.root, f.sports));
        ev.add("|");
        out.add("sports expanded=" + tree.isExpanded(f.path(f.root, f.sports))
                + " hasBeen=" + tree.hasBeenExpanded(f.path(f.root, f.sports)));
        tree.collapsePath(f.path(f.root, f.colors, f.blue));
        ev.add("|");
        out.add("colors expanded after collapse(blue)=" + tree.isExpanded(f.path(f.root, f.colors)));
        out.add("hasBeen(colors)=" + tree.hasBeenExpanded(f.path(f.root, f.colors)));
        f.model.removeNodeFromParent(f.colors);
        out.add("hasBeen(colors) after remove=" + tree.hasBeenExpanded(f.path(f.root, f.colors))
                + " rows=" + tree.getRowCount());
        out.add(ev.toString());
        tree.setRootVisible(false);
        out.add("rows hidden root=" + tree.getRowCount() + " row0=" + tree.getPathForRow(0));

        // javax.swing.JTree, same fixture and calls, JDK 25 headless.
        assertEquals(List.of(
                "rows0=3",
                "rows1=5 row(blue)=2",
                "sel after collapse=[root, colors]",
                "sports expanded=false hasBeen=false",
                "colors expanded after collapse(blue)=true",
                "hasBeen(colors)=true",
                "hasBeen(colors) after remove=false rows=2",
                "[will+colors, exp colors, |, will-colors, col colors, |, will+sports, |, will+colors, exp colors, |]",
                "rows hidden root=1 row0=[root, sports]"), out);
    }

    @Test
    @DisplayName("the peer follows every programmatic expansion and collapse")
    void thePeerFollowsProgrammaticExpansion() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);

        tree.expandPath(f.path(f.root, f.colors));
        assertTrue(peer.isExpanded(f.colors));
        tree.collapsePath(f.path(f.root, f.colors));
        assertFalse(peer.isExpanded(f.colors));
    }

    @Test
    @DisplayName("a browser expand runs the JDK path: will-expand, expanded, and the rows follow")
    void aBrowserExpandRunsTheJdkPath() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        List<String> ev = recordExpansion(tree, null);

        browserToggle(peer, f.colors, true);

        assertEquals(List.of("will+colors", "exp colors"), ev);
        assertTrue(tree.isExpanded(f.path(f.root, f.colors)));
        assertEquals(5, tree.getRowCount());

        browserToggle(peer, f.colors, false);
        assertEquals(List.of("will+colors", "exp colors", "will-colors", "col colors"), ev);
        assertFalse(tree.isExpanded(f.path(f.root, f.colors)));
        assertEquals(3, tree.getRowCount());
    }

    @Test
    @DisplayName("a vetoed browser expand is undone in the peer")
    void aVetoedBrowserExpandIsUndone() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        List<String> ev = recordExpansion(tree, f.sports);

        browserToggle(peer, f.sports, true);

        assertEquals(List.of("will+sports"), ev);
        assertFalse(tree.isExpanded(f.path(f.root, f.sports)));
        assertFalse(peer.isExpanded(f.sports), "the veto must close the node the browser opened");
        assertEquals(3, tree.getRowCount());
    }

    // --- TreeCellRenderer bridge (D_jtree_renderer_registry) -----------------------------------------

    @Test
    @DisplayName("default cell renderer is DefaultTreeCellRenderer and renders the node text")
    void defaultCellRendererRendersTheNodeText() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        assertInstanceOf(DefaultTreeCellRenderer.class, tree.getCellRenderer());
        assertEquals("root", cellText(peer, 0));
        assertEquals("colors", cellText(peer, 1));
    }

    @Test
    @DisplayName("custom cell renderer drives the rendered cell (snapshot bridge)")
    void customCellRendererDrivesTheRenderedCell() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        List<Object> seenTrees = new ArrayList<>();
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public vaadinx.awt.Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                seenTrees.add(tree);
                setText((leaf ? "leaf" : "dir") + ":" + value);
                return this;
            }
        });
        assertEquals("dir:colors", cellText(peer, 1));
        assertSame(tree, seenTrees.get(seenTrees.size() - 1),
                "the JTree instance passes as the tree arg (down-cast support)");
        tree.expandRow(1);
        assertEquals("leaf:blue", cellText(peer, 2));
    }

    @Test
    @DisplayName("setCellRenderer fires cellRenderer PCE")
    void setCellRendererFiresPce() {
        JTree tree = new JTree(new Fixture().model);
        attachPeer(tree);
        List<PropertyChangeEvent> seen = new ArrayList<>();
        tree.addPropertyChangeListener(JTree.CELL_RENDERER_PROPERTY, seen::add);
        tree.setCellRenderer(new DefaultTreeCellRenderer());
        assertEquals(1, seen.size());
    }

    @Test
    @DisplayName("convertValueToText override flows through the default renderer")
    void convertValueToTextOverrideFlowsThrough() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model) {
            @Override
            public String convertValueToText(Object value, boolean selected, boolean expanded,
                                             boolean leaf, int row, boolean hasFocus) {
                return "[" + value + "]";
            }
        };
        SJTree peer = attachPeer(tree);
        assertEquals("[colors]", cellText(peer, 1));
    }

    // --- Mouse re-source (D_jtree_two_layer_resource.3 — the selectOnRightClick idiom) -----------------

    /** What a mousePressed handler could observe about the click that produced it. */
    private static final class PressRecorder extends MouseAdapter {
        private final JTree tree;
        int row = -2;
        TreePath path;
        boolean popupTrigger;
        Object source;

        PressRecorder(JTree tree) {
            this.tree = tree;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            source = e.getSource();
            popupTrigger = e.isPopupTrigger();
            row = tree.getRowForLocation(e.getX(), e.getY());
            path = tree.getPathForLocation(e.getX(), e.getY());
        }
    }

    @Test
    @DisplayName("right-click re-sources with isPopupTrigger and getRowForLocation resolves")
    void rightClickResourcesWithPopupTrigger() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        PressRecorder pressed = new PressRecorder(tree);
        tree.addMouseListener(pressed);
        GridKt._clickItem(peer, 2, 2);  // row "sports", browser button 2 = right
        assertSame(tree, pressed.source, "MouseEvent source must be the emulator JTree");
        assertTrue(pressed.popupTrigger);
        assertEquals(2, pressed.row);
        assertSame(f.sports, pressed.path.getLastPathComponent());
        // Stash cleared outside the dispatch.
        assertEquals(-1, tree.getRowForLocation(0, 0));
        assertNull(tree.getPathForLocation(0, 0));
    }

    /**
     * The location getters' call graph, measured on JDK 25: {@code getRowForLocation} →
     * {@code getPathForLocation} → {@code getClosestPathForLocation} → {@code getPathBounds}
     * → {@code getRowForPath}, and {@code getClosestRowForLocation} skipping the bounds.
     * The rows are the emulator's own walk, never the peer's.
     */
    @Test
    @DisplayName("the location getters run the JDK's call graph over the emulator's rows")
    void locationGettersRunTheJdksCallGraph() {
        Fixture f = new Fixture();
        List<String> log = new ArrayList<>();
        JTree tree = new JTree(f.model) {
            @Override public TreePath getPathForLocation(int x, int y) { log.add("getPathForLocation"); return super.getPathForLocation(x, y); }
            @Override public TreePath getClosestPathForLocation(int x, int y) { log.add("getClosestPathForLocation"); return super.getClosestPathForLocation(x, y); }
            @Override public int getRowForPath(TreePath p) { log.add("getRowForPath"); return super.getRowForPath(p); }
            @Override public java.awt.Rectangle getPathBounds(TreePath p) { log.add("getPathBounds"); return super.getPathBounds(p); }
        };
        SJTree peer = attachPeer(tree);
        List<String> seen = new ArrayList<>();
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                log.clear();
                seen.add("row " + tree.getRowForLocation(e.getX(), e.getY()) + " " + log);
                log.clear();
                seen.add("closest " + tree.getClosestRowForLocation(e.getX(), e.getY()) + " " + log);
            }
        });

        GridKt._clickItem(peer, 2, 0);

        assertEquals(List.of(
                "row 2 [getPathForLocation, getClosestPathForLocation, getPathBounds, getRowForPath]",
                "closest 2 [getClosestPathForLocation, getRowForPath]"), seen);
    }

    @Test
    @DisplayName("left-click re-sources press-release-click with clickCount 1")
    void leftClickResourcesPressReleaseClick() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        List<Integer> ids = new ArrayList<>();
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                ids.add(e.getID());
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                ids.add(e.getID());
                assertEquals(1, e.getClickCount());
                assertFalse(e.isPopupTrigger());
            }
        });
        GridKt._clickItem(peer, 1);
        assertEquals(
                List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED),
                ids);
    }

    // --- Row ⇄ path delegation ----------------------------------------------------

    @Test
    @DisplayName("row-path queries delegate over a partially collapsed tree")
    void rowPathQueriesDelegate() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        tree.expandPath(f.path(f.root, f.colors));
        assertEquals(5, tree.getRowCount());
        assertSame(f.blue, tree.getPathForRow(2).getLastPathComponent());
        assertEquals(4, tree.getRowForPath(f.path(f.root, f.sports)));
        assertEquals(-1, tree.getRowForPath(f.path(f.root, f.sports, f.soccer)),
                "collapsed ancestor hides the node");
        assertNull(tree.getRowBounds(0), "R_layouts_close_enough — no server-side cell bounds");
        assertNull(tree.getPathBounds(f.path(f.root)));
    }

    // --- rootVisible delegation ------------------------------------------------

    @Test
    @DisplayName("setRootVisible delegates to the peer and fires PCE")
    void setRootVisibleDelegatesAndFiresPce() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        List<PropertyChangeEvent> seen = new ArrayList<>();
        tree.addPropertyChangeListener(JTree.ROOT_VISIBLE_PROPERTY, seen::add);
        tree.setRootVisible(false);
        assertFalse(tree.isRootVisible());
        assertFalse(peer.isRootVisible());
        assertEquals(List.of(f.colors, f.sports), GridKt._getRootItems(peer));
        assertEquals(1, seen.size());
    }

    // --- No-counterpart knobs (D_jtree_field_shadows R_swing_is_truth field shadows) ----------------------------

    @Test
    @DisplayName("no-counterpart knobs round-trip on the emulator with PCE")
    void noCounterpartKnobsRoundTripWithPce() {
        JTree tree = new JTree(new Fixture().model);
        List<String> seen = new ArrayList<>();
        tree.addPropertyChangeListener(e -> seen.add(e.getPropertyName()));

        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setScrollsOnExpand(false);
        tree.setToggleClickCount(1);
        tree.setVisibleRowCount(10);
        tree.setExpandsSelectedPaths(false);
        tree.setInvokesStopCellEditing(true);
        tree.setLargeModel(!tree.isLargeModel());

        assertTrue(tree.getShowsRootHandles());
        assertEquals(24, tree.getRowHeight());
        assertTrue(tree.isFixedRowHeight());
        assertFalse(tree.getScrollsOnExpand());
        assertEquals(1, tree.getToggleClickCount());
        assertEquals(10, tree.getVisibleRowCount());
        assertFalse(tree.getExpandsSelectedPaths());
        assertTrue(tree.getInvokesStopCellEditing());
        assertTrue(tree.isLargeModel());
        assertTrue(seen.containsAll(List.of(
                JTree.SHOWS_ROOT_HANDLES_PROPERTY, JTree.ROW_HEIGHT_PROPERTY,
                JTree.SCROLLS_ON_EXPAND_PROPERTY, JTree.TOGGLE_CLICK_COUNT_PROPERTY,
                JTree.VISIBLE_ROW_COUNT_PROPERTY, JTree.EXPANDS_SELECTED_PATHS_PROPERTY,
                JTree.INVOKES_STOP_CELL_EDITING_PROPERTY, JTree.LARGE_MODEL_PROPERTY)));
    }

    @Test
    @DisplayName("lead and anchor selection paths round-trip with PCE")
    void leadAndAnchorSelectionPathsRoundTrip() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        List<String> seen = new ArrayList<>();
        tree.addPropertyChangeListener(e -> seen.add(e.getPropertyName()));
        tree.setAnchorSelectionPath(f.path(f.root, f.colors));
        tree.setLeadSelectionPath(f.path(f.root, f.sports));
        assertSame(f.colors, tree.getAnchorSelectionPath().getLastPathComponent());
        assertTrue(seen.containsAll(List.of(
                JTree.ANCHOR_SELECTION_PATH_PROPERTY, JTree.LEAD_SELECTION_PATH_PROPERTY)));
    }

    // --- Editing (D_jtree_rename_in_place — rename-in-place over TreeGrid.Editor) ---------------------

    /** One {@code valueForPathChanged} write-back the recording model observed. */
    private record Rename(TreePath path, Object newValue) {
    }

    @Test
    @DisplayName("rename-in-place commits through valueForPathChanged - cancel and disarm are no-writeback")
    void renameInPlaceCommitsThroughValueForPathChanged() {
        Fixture f = new Fixture();
        // Recording model to observe the commit's write-back.
        List<Rename> renamed = new ArrayList<>();
        DefaultTreeModel model = new DefaultTreeModel(f.root) {
            @Override
            public void valueForPathChanged(TreePath path, Object newValue) {
                renamed.add(new Rename(path, newValue));
                super.valueForPathChanged(path, newValue);
            }
        };
        JTree tree = new JTree(model);
        attachPeer(tree);
        List<String> seen = new ArrayList<>();
        tree.addPropertyChangeListener(JTree.EDITABLE_PROPERTY, e -> seen.add(e.getPropertyName()));

        assertFalse(tree.isEditing());
        assertNull(tree.getEditingPath());

        tree.setEditable(true);
        assertTrue(tree.isEditable());
        assertTrue(seen.contains(JTree.EDITABLE_PROPERTY));
        assertTrue(tree.isPathEditable(f.path(f.root)));

        // Open + commit: stopEditing writes back the (seeded) node text.
        TreePath colorsPath = f.path(f.root, f.colors);
        tree.startEditingAtPath(colorsPath);
        assertTrue(tree.isEditing());
        assertEquals(colorsPath, tree.getEditingPath());
        assertTrue(tree.stopEditing());
        assertFalse(tree.isEditing());
        assertNull(tree.getEditingPath());
        // seed == convertValueToText(colors)
        assertEquals(List.of(new Rename(colorsPath, "colors")), renamed);

        // Cancel: no further write-back.
        tree.startEditingAtPath(colorsPath);
        assertTrue(tree.isEditing());
        tree.cancelEditing();
        assertFalse(tree.isEditing());
        assertNull(tree.getEditingPath());
        assertEquals(1, renamed.size());

        // Disarmed → startEditingAtPath is a no-op.
        tree.setEditable(false);
        tree.startEditingAtPath(colorsPath);
        assertFalse(tree.isEditing());
    }

    @Test
    @DisplayName("setCellEditor WARNs and stays inert, but round-trips and fires (D_gap_severity_triage b, D_owed_events)")
    void setCellEditorRoundTripsAndFiresWhileInert() {
        // The editor is inert — arbitrary editor components stay out per D_gap_severity_triage
        // sub-bucket (b) — but R_decline_effect_only lets the emulator decline the *effect* only:
        // the value round-trips through its own getter and "cellEditor" fires,
        // as JTree.setCellEditor does in the JDK.
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        assertNull(tree.getCellEditor());   // none installed yet
        List<PropertyChangeEvent> events = new ArrayList<>();
        tree.addPropertyChangeListener(JTree.CELL_EDITOR_PROPERTY, events::add);

        DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        tree.setCellEditor(editor);  // WARNs; drives no editing

        assertEquals(editor, tree.getCellEditor());
        PropertyChangeEvent e = assertSingle(events);
        assertNull(e.getOldValue());
        assertEquals(editor, e.getNewValue());
    }

    // --- DnD (D_jtree_row_dnd — TreeGrid-row path per D_drag_and_drop) ---------------------------------

    /** Records every drop's JTree.DropLocation; imports nothing else. */
    private static class RecordingTreeHandler extends TransferHandler {
        final List<JTree.DropLocation> drops = new ArrayList<>();
        int exported;

        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            Object node = ((JTree) c).getLastSelectedPathComponent();
            return node == null ? null : new StringSelection(node.toString());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support) {
            drops.add((JTree.DropLocation) support.getDropLocation());
            return true;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            exported++;
        }
    }

    @Test
    @DisplayName("setDragEnabled wires TreeGrid row drag once a handler is present")
    void setDragEnabledWiresRowDrag() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        SJTree peer = attachPeer(tree);
        tree.setTransferHandler(new RecordingTreeHandler());
        assertFalse(peer.isRowsDraggable(), "dragEnabled=false → no drag source (JDK contract)");
        tree.setDragEnabled(true);
        assertTrue(tree.getDragEnabled());
        assertTrue(peer.isRowsDraggable());
        tree.setDragEnabled(false);
        assertFalse(peer.isRowsDraggable());
    }

    @Test
    @DisplayName("setDropMode validates the JTree-supported set")
    void setDropModeValidates() {
        JTree tree = new JTree(new Fixture().model);
        attachPeer(tree);
        assertEquals(DropMode.USE_SELECTION, tree.getDropMode());
        tree.setDropMode(DropMode.ON);
        assertEquals(DropMode.ON, tree.getDropMode());
        assertThrows(IllegalArgumentException.class, () -> tree.setDropMode(DropMode.INSERT_ROWS));
    }

    @Test
    @DisplayName("drop ON_TOP delivers a DropLocation on the node with childIndex -1")
    void dropOnTopDeliversNodeDropLocation() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        RecordingTreeHandler handler = new RecordingTreeHandler();
        tree.setTransferHandler(handler);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);

        DndBridge.simulateGridDragStartForTest(tree, List.of(f.blue));
        DndBridge.simulateGridDropForTest(tree, f.colors, GridDropLocation.ON_TOP);
        DndBridge.simulateDragEndForTest(tree);

        JTree.DropLocation dl = assertSingle(handler.drops);
        assertSame(f.colors, dl.getPath().getLastPathComponent());
        assertEquals(-1, dl.getChildIndex());
        assertEquals(1, handler.exported);
    }

    @Test
    @DisplayName("drop ABOVE and BELOW insert into the parent at the node's model index")
    void dropAboveAndBelowInsertIntoTheParent() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        RecordingTreeHandler handler = new RecordingTreeHandler();
        tree.setTransferHandler(handler);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.INSERT);

        DndBridge.simulateGridDragStartForTest(tree, List.of(f.blue));
        DndBridge.simulateGridDropForTest(tree, f.sports, GridDropLocation.ABOVE);
        DndBridge.simulateGridDropForTest(tree, f.sports, GridDropLocation.BELOW);
        DndBridge.simulateDragEndForTest(tree);

        assertEquals(2, handler.drops.size());
        JTree.DropLocation above = handler.drops.get(0);
        assertSame(f.root, above.getPath().getLastPathComponent());
        assertEquals(1, above.getChildIndex(), "sports is root's child #1; ABOVE inserts at its index");
        JTree.DropLocation below = handler.drops.get(1);
        assertSame(f.root, below.getPath().getLastPathComponent());
        assertEquals(2, below.getChildIndex());
    }

    @Test
    @DisplayName("drag start syncs the dragged node into the selection for createTransferable")
    void dragStartSyncsTheDraggedNodeIntoTheSelection() {
        Fixture f = new Fixture();
        JTree tree = new JTree(f.model);
        attachPeer(tree);
        tree.setTransferHandler(new RecordingTreeHandler());
        tree.setDragEnabled(true);

        DndBridge.simulateGridDragStartForTest(tree, List.of(f.sports));
        assertSame(f.sports, tree.getLastSelectedPathComponent(),
                "dragged node selected so createTransferable reads it");
        DndBridge.simulateDragEndForTest(tree);
    }

    // --- Scrollable + L&F stubs ----------------------------------------------------

    @Test
    @DisplayName("Scrollable methods return sane values")
    void scrollableMethodsReturnSaneValues() {
        JTree tree = new JTree(new Fixture().model);
        tree.setRowHeight(20);
        tree.setVisibleRowCount(5);
        assertEquals(20 * 5, tree.getPreferredScrollableViewportSize().height);
        assertEquals(20, tree.getScrollableUnitIncrement(
                new Rectangle(0, 0, 100, 100), SwingConstants.VERTICAL, 1));
        assertFalse(tree.getScrollableTracksViewportWidth());
        assertFalse(tree.getScrollableTracksViewportHeight());
    }

    @Test
    @DisplayName("getUIClassID is TreeUI")
    void getUiClassIdIsTreeUi() {
        assertEquals("TreeUI", new JTree().getUIClassID());
    }
}
