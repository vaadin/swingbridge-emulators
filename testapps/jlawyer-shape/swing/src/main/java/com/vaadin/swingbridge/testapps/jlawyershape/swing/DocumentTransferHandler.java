package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.function.Function;

/**
 * TransferHandler that moves a {@link Document} from a case's documents {@link JList}
 * onto a different {@link Case}. The drag source is a JList whose model holds
 * {@code Document} entries; the drop target is the always-visible case {@link JTree}
 * (drop the document onto the destination case node).
 *
 * <p>The drop target is the tree rather than another case's list because open cases
 * live in tabs of a single {@code JTabbedPane} — only one case list is on screen at a
 * time, so a list-to-list drag could never reach a different case. The tree shows
 * every case and is always visible, so it is a reachable drop target regardless of
 * which case tab is active. List-to-list is still honored (via {@code caseLookup}) for
 * the rare case where two lists are co-visible.
 *
 * Two carrier flavors are advertised: a JVM-internal {@code Document} flavor (for the
 * in-app drag) and a plaintext flavor (so a drag to an external editor pastes the
 * document's path-hint).
 */
public final class DocumentTransferHandler extends TransferHandler {

    public static final DataFlavor DOCUMENT_FLAVOR;

    static {
        DataFlavor f;
        try {
            // Single-arg ctor parses the ";class=..." parameter from the MIME type and may
            // fail to load the named class — keep the checked catch even though the class
            // is obviously present at static-init time, since the alternative two-arg ctor
            // doesn't expose that failure path.
            f = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + Document.class.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        DOCUMENT_FLAVOR = f;
    }

    private final CaseStore store;
    private final Function<JList<Document>, Case> caseLookup;

    public DocumentTransferHandler(CaseStore store, Function<JList<Document>, Case> caseLookup) {
        this.store = store;
        this.caseLookup = caseLookup;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JList<?> list = (JList<?>) c;
        Object selected = list.getSelectedValue();
        if (!(selected instanceof Document)) return null;
        return new DocumentTransferable((Document) selected);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDataFlavorSupported(DOCUMENT_FLAVOR)) return false;
        if (targetCaseOf(support) == null) return false;
        support.setDropAction(MOVE);
        return true;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        try {
            Document doc = (Document) support.getTransferable().getTransferData(DOCUMENT_FLAVOR);
            Case targetCase = targetCaseOf(support);
            if (targetCase == null) return false;
            Case sourceCase = findOwner(doc);
            if (sourceCase == null || sourceCase == targetCase) return false;
            sourceCase.removeDocument(doc);
            targetCase.addDocument(doc);
            store.fireDocumentsChanged(sourceCase);
            store.fireDocumentsChanged(targetCase);
            return true;
        } catch (UnsupportedFlavorException | IOException e) {
            return false;
        }
    }

    /**
     * Resolves the destination case from the drop target: the case node under a tree
     * drop, or (legacy list-to-list path) the case the dropped-on list represents.
     * Returns null when the drop is not over a case — which also makes {@code canImport}
     * reject it, so the browser/Swing shows a no-drop cursor over folders.
     */
    @SuppressWarnings("unchecked")
    private Case targetCaseOf(TransferSupport support) {
        JComponent comp = (JComponent) support.getComponent();
        if (comp instanceof JTree) {
            if (!support.isDrop()) return null;
            TreePath path = ((JTree.DropLocation) support.getDropLocation()).getPath();
            if (path == null) return null;
            Object last = path.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Case c) {
                return c;
            }
            return null;
        }
        if (comp instanceof JList) {
            return caseLookup.apply((JList<Document>) comp);
        }
        return null;
    }

    private Case findOwner(Document d) {
        for (Case c : store.allCases()) {
            if (c.getDocuments().contains(d)) return c;
        }
        return null;
    }

    private static final class DocumentTransferable implements Transferable {
        private final Document doc;

        DocumentTransferable(Document doc) { this.doc = doc; }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DOCUMENT_FLAVOR, DataFlavor.stringFlavor };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DOCUMENT_FLAVOR.equals(flavor) || DataFlavor.stringFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (DOCUMENT_FLAVOR.equals(flavor)) return doc;
            if (DataFlavor.stringFlavor.equals(flavor)) return doc.getPathHint();
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
