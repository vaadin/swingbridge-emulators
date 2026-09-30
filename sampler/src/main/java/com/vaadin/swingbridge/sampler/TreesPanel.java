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

import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JTree;
import vaadinx.swing.TransferHandler;
import vaadinx.swing.tree.DefaultTreeCellRenderer;

import javax.swing.DropMode;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * JTree demo (SD_sjtree + D_jtree) — the "left-pane case-folder
 * hierarchy" shape the JLawyer-shape testbed exercises
 * ({@code CaseTreeView}). Hosts a single-selection {@link JTree} over a
 * {@link DefaultTreeModel} of case folders and their documents, wrapped in a
 * {@link JScrollPane} (whose auto-scroll guard recognises the TreeGrid peer),
 * with:
 *
 * <ul>
 *   <li>a custom {@link DefaultTreeCellRenderer} subclass prefixing folders
 *       and documents differently — exercises the emulator→surrogate
 *       hierarchy-column snapshot bridge (D_jtree_renderer_registry) including the leaf /
 *       expanded flags;</li>
 *   <li>a {@link javax.swing.event.TreeSelectionListener} via
 *       {@code addTreeSelectionListener} updating the status label — the
 *       {@code CaseTreeView.fireCaseSelected} path;</li>
 *   <li>a {@link MouseAdapter} implementing right-click-to-select via
 *       {@code isPopupTrigger()} + {@code getRowForLocation(x, y)} +
 *       {@code setSelectionRow} — the {@code selectOnRightClick} idiom;</li>
 *   <li>Add / Remove buttons mutating the model through
 *       {@code DefaultTreeModel.insertNodeInto / removeNodeFromParent};</li>
 *   <li>TreeGrid-row drag-to-reparent (D_jtree_row_dnd): {@code setDragEnabled(true)} +
 *       {@code DropMode.ON} + a {@link TransferHandler} whose
 *       {@code importData} re-parents the dragged document onto the folder
 *       it was dropped on;</li>
 *   <li>rename-in-place (D_jtree_rename_in_place): {@code setEditable(true)} + a double-click (or
 *       the Rename button → {@code startEditingAtPath}) opens a text editor on
 *       the node; Enter/blur commits through {@code valueForPathChanged},
 *       Escape cancels.</li>
 * </ul>
 */
public class TreesPanel extends JPanel {

    private final JLabel status = new JLabel("Select a folder or document; right-click also selects.");
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Cases");
    final DefaultTreeModel model = new DefaultTreeModel(root);
    final JTree tree = new JTree(model);
    private int counter = 0;

    public TreesPanel() {
        super(new BorderLayout(8, 8));

        DefaultMutableTreeNode smith = caseFolder("Case 2026-001 Smith v. Jones",
                "Complaint.pdf", "Evidence-A.png");
        DefaultMutableTreeNode estate = caseFolder("Case 2026-002 Estate of Brown",
                "Will-2019.pdf", "Probate-Filing.docx", "Inventory.xlsx");
        root.add(smith);
        root.add(estate);

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new CaseNodeRenderer());
        tree.setEditable(true); // rename-in-place: double-click a node, or the Rename button (D_jtree_rename_in_place)
        tree.expandPath(new TreePath(new Object[]{root, smith}));

        // Selection → status label, source rebound to the JTree (D_jtree_two_layer_resource).
        tree.addTreeSelectionListener(e -> {
            Object node = tree.getLastSelectedPathComponent();
            status.setText(node == null ? "Nothing selected."
                    : "Selected: " + node + (isFolder(node) ? " (folder)" : " (document)"));
        });

        // Right-click-to-select — the CaseTreeView.selectOnRightClick idiom:
        // isPopupTrigger + getRowForLocation resolve through the surrogate's
        // click stash, valid during this dispatch.
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeSelectOnRightClick(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeSelectOnRightClick(e);
            }

            private void maybeSelectOnRightClick(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row >= 0) {
                    tree.setSelectionRow(row);
                    status.setText("Right-clicked row " + row + ": "
                            + tree.getLastSelectedPathComponent());
                }
            }
        });

        // D_jtree_row_dnd drag-to-reparent: drag a document row onto a case folder.
        tree.setTransferHandler(new ReparentHandler());
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "JTree demo (case-folder hierarchy). Single selection; "
                        + "right-click selects too; custom renderer prefixes folders "
                        + "vs documents; Add / Remove mutate the DefaultTreeModel; "
                        + "drag a document onto another folder to re-parent it; "
                        + "double-click a node (or Rename) to rename it in place."));

        JButton add = new JButton("Add document");
        add.addActionListener(e -> {
            DefaultMutableTreeNode target = selectedFolderOrParent();
            if (target == null) {
                status.setText("Select a folder (or document in it) first.");
                return;
            }
            counter++;
            DefaultMutableTreeNode doc = new DefaultMutableTreeNode("New-Document-" + counter + ".pdf");
            model.insertNodeInto(doc, target, target.getChildCount());
            tree.scrollPathToVisible(new TreePath(doc.getPath()));
        });

        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            Object node = tree.getLastSelectedPathComponent();
            if (node instanceof DefaultMutableTreeNode n && n != root) {
                model.removeNodeFromParent(n);
            }
        });

        JButton selectFirst = new JButton("Select first case");
        selectFirst.addActionListener(e -> {
            if (root.getChildCount() > 0) {
                DefaultMutableTreeNode first = (DefaultMutableTreeNode) root.getChildAt(0);
                tree.setSelectionPath(new TreePath(first.getPath()));
            }
        });

        JButton expandAll = new JButton("Expand all");
        expandAll.addActionListener(e -> {
            // Row-walk expansion — each expand can grow the visible row count.
            for (int row = 0; row < tree.getRowCount(); row++) {
                tree.expandRow(row);
            }
        });

        JButton rename = new JButton("Rename selected");
        rename.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) {
                status.setText("Select a node first, then Rename (or double-click a node).");
                return;
            }
            tree.startEditingAtPath(path);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(selectFirst);
        buttons.add(expandAll);
        buttons.add(rename);

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private static DefaultMutableTreeNode caseFolder(String name, String... documents) {
        DefaultMutableTreeNode folder = new DefaultMutableTreeNode(name);
        for (String doc : documents) {
            folder.add(new DefaultMutableTreeNode(doc));
        }
        return folder;
    }

    private static boolean isFolder(Object node) {
        return node instanceof DefaultMutableTreeNode n && !n.isLeaf();
    }

    /** The selected folder, or the selected document's folder; null when nothing useful is selected. */
    private DefaultMutableTreeNode selectedFolderOrParent() {
        Object node = tree.getLastSelectedPathComponent();
        if (!(node instanceof DefaultMutableTreeNode n)) return null;
        if (n == root || !n.isLeaf()) return n;
        return (DefaultMutableTreeNode) n.getParent();
    }

    /**
     * DefaultTreeCellRenderer subclass distinguishing folders from documents —
     * the JDK mutate-and-return-self pattern, exercising the D_jtree_renderer_registry snapshot
     * bridge with the leaf / expanded render flags.
     */
    static final class CaseNodeRenderer extends DefaultTreeCellRenderer {
        @Override
        public vaadinx.awt.Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setText((leaf ? "📄 " : (expanded ? "📂 " : "📁 ")) + value);
            return this;
        }
    }

    /**
     * D_jtree_row_dnd TreeGrid-row drag-to-reparent. {@code createTransferable} exports
     * the selected document; {@code importData} reads the drop's
     * {@link JTree.DropLocation} and moves the dragged node under the target
     * folder ({@code DropMode.ON} → {@code childIndex == -1}; an insert drop
     * carries the index). MOVE is modelled inside {@code importData} via
     * {@code DefaultTreeModel.removeNodeFromParent + insertNodeInto} (one
     * atomic re-parent), so {@code exportDone} has nothing left to delete.
     */
    private final class ReparentHandler extends TransferHandler {
        private DefaultMutableTreeNode dragged;

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            Object node = tree.getLastSelectedPathComponent();
            if (!(node instanceof DefaultMutableTreeNode n) || n == root || !n.isLeaf()) {
                return null; // only documents drag
            }
            dragged = n;
            return new NodeTransferable(n.toString());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            return dragged != null
                    && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                    && support.getDropLocation() instanceof JTree.DropLocation;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath path = dl.getPath();
            if (path == null) return false;
            Object target = path.getLastPathComponent();
            if (!(target instanceof DefaultMutableTreeNode t) || t == dragged) return false;
            DefaultMutableTreeNode folder = t.isLeaf() && t.getParent() != null
                    ? (DefaultMutableTreeNode) t.getParent() : t;
            if (folder == dragged.getParent()) {
                status.setText("Already in " + folder);
                return false;
            }
            model.removeNodeFromParent(dragged);
            int index = dl.getChildIndex() < 0 ? folder.getChildCount()
                    : Math.min(dl.getChildIndex(), folder.getChildCount());
            model.insertNodeInto(dragged, folder, index);
            status.setText("Moved " + dragged + " to " + folder);
            return true;
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            dragged = null; // re-parent already happened in importData
        }
    }

    /** Minimal string-flavored transferable carrying the dragged document name. */
    private static final class NodeTransferable implements Transferable {
        private final String name;

        NodeTransferable(String name) {
            this.name = name;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.stringFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return name;
        }
    }
}
