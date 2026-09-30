package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import vaadinx.swing.JPanel;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import vaadinx.awt.BorderLayout;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

public final class CaseTreeView extends JPanel {

    private final CaseStore store;
    private final JTree tree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final List<Consumer<Case>> caseSelectionListeners = new ArrayList<>();

    public CaseTreeView(CaseStore store) {
        super(new BorderLayout());
        this.store = store;
        rootNode = new DefaultMutableTreeNode(store.getRoot());
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(e -> fireCaseSelected());
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent ev)  { selectOnRightClick(ev); }
            @Override public void mouseReleased(MouseEvent ev) { selectOnRightClick(ev); }
        });

        rebuild();

        store.addListener(new CaseStore.Listener() {
            @Override public void treeChanged() { rebuild(); }
            @Override public void caseUpdated(Case c) { treeModel.nodeChanged(findNode(c)); }
            @Override public void documentsChanged(Case c) { /* tree doesn't care */ }
        });

        add(new JScrollPane(tree), BorderLayout.CENTER);
    }

    public JTree getTree() { return tree; }

    /** Selected case, or null if a folder (or nothing) is selected. */
    public Case getSelectedCase() {
        Object u = selectedUserObject();
        return (u instanceof Case) ? (Case) u : null;
    }

    /** Folder that contains the current selection — if a folder is selected, that folder;
     *  if a case is selected, its parent folder; otherwise the store root. */
    public CaseFolder getSelectedFolderOrParent() {
        Object u = selectedUserObject();
        if (u instanceof CaseFolder) return (CaseFolder) u;
        if (u instanceof Case) return store.findFolderOf((Case) u);
        return store.getRoot();
    }

    public void addCaseSelectionListener(Consumer<Case> l) {
        caseSelectionListeners.add(l);
    }

    public void selectCase(Case c) {
        DefaultMutableTreeNode n = findNode(c);
        if (n == null) return;
        TreePath path = new TreePath(n.getPath());
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    private void fireCaseSelected() {
        Case c = getSelectedCase();
        if (c == null) return;
        for (Consumer<Case> l : caseSelectionListeners) l.accept(c);
    }

    private Object selectedUserObject() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode) return ((DefaultMutableTreeNode) last).getUserObject();
        return null;
    }

    private void selectOnRightClick(MouseEvent ev) {
        if (!ev.isPopupTrigger()) return;
        int row = tree.getRowForLocation(ev.getX(), ev.getY());
        if (row >= 0) tree.setSelectionRow(row);
    }

    private void rebuild() {
        Object selectedUser = selectedUserObject();
        rootNode.removeAllChildren();
        populate(rootNode, store.getRoot());
        treeModel.reload();
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        if (selectedUser != null) {
            DefaultMutableTreeNode hit = findNodeByUserObject(rootNode, selectedUser);
            if (hit != null) tree.setSelectionPath(new TreePath(hit.getPath()));
        }
    }

    private void populate(DefaultMutableTreeNode parentNode, CaseFolder folder) {
        for (CaseFolder child : folder.getChildren()) {
            DefaultMutableTreeNode n = new DefaultMutableTreeNode(child);
            parentNode.add(n);
            populate(n, child);
        }
        for (Case c : folder.getCases()) {
            parentNode.add(new DefaultMutableTreeNode(c));
        }
    }

    private DefaultMutableTreeNode findNode(Case c) {
        return findNodeByUserObject(rootNode, c);
    }

    private DefaultMutableTreeNode findNodeByUserObject(DefaultMutableTreeNode start, Object target) {
        Enumeration<?> e = start.depthFirstEnumeration();
        while (e.hasMoreElements()) {
            Object obj = e.nextElement();
            if (obj instanceof DefaultMutableTreeNode n && n.getUserObject() == target) return n;
        }
        return null;
    }
}
