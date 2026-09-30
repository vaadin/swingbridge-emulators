package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import vaadinx.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import vaadinx.swing.JEditorPane;
import vaadinx.swing.JLabel;
import vaadinx.swing.JList;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JOptionPane;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPopupMenu;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import vaadinx.swing.SwingUtilities;
import vaadinx.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import vaadinx.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

public final class CaseView extends JPanel {

    private final CaseStore store;
    private final Case theCase;
    private final DefaultListModel<Document> docsModel = new DefaultListModel<>();
    private final JList<Document> docsList = new JList<>(docsModel);
    private final JEditorPane notesPane = new JEditorPane();
    private final JLabel headerLabel = new JLabel();

    public CaseView(CaseStore store, Case theCase) {
        super(new BorderLayout(0, 6));
        this.store = store;
        this.theCase = theCase;
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        add(buildHeader(), BorderLayout.NORTH);

        JSplitPane innerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        innerSplit.setResizeWeight(0.45);
        innerSplit.setBorder(null);
        innerSplit.setTopComponent(buildDocsPane());
        innerSplit.setBottomComponent(buildNotesPane());
        add(innerSplit, BorderLayout.CENTER);

        refreshHeader();
        refreshDocs();
        refreshNotes();

        store.addListener(new CaseStore.Listener() {
            @Override public void treeChanged() { /* irrelevant */ }
            @Override public void caseUpdated(Case c) {
                if (c == theCase) { refreshHeader(); refreshNotes(); }
            }
            @Override public void documentsChanged(Case c) {
                if (c == theCase) refreshDocs();
            }
        });
    }

    public Case getCase() { return theCase; }
    public JList<Document> getDocsList() { return docsList; }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 2));
        headerLabel.setHorizontalAlignment(SwingConstants.LEADING);
        header.add(headerLabel, BorderLayout.CENTER);
        return header;
    }

    private JScrollPane buildDocsPane() {
        docsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        docsList.setVisibleRowCount(8);
        docsList.setCellRenderer(new DocumentCellRenderer());
        docsList.setDragEnabled(true);
        docsList.setDropMode(DropMode.INSERT);
        docsList.setComponentPopupMenu(buildDocsPopup());
        JScrollPane scroll = new JScrollPane(docsList);
        scroll.setPreferredSize(new Dimension(0, 180));
        scroll.setBorder(BorderFactory.createTitledBorder("Documents — drag onto a case in the tree to move"));
        return scroll;
    }

    private JScrollPane buildNotesPane() {
        notesPane.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
        notesPane.setEditable(false);
        notesPane.setBackground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(notesPane);
        scroll.setBorder(BorderFactory.createTitledBorder("Notes"));
        return scroll;
    }

    private JPopupMenu buildDocsPopup() {
        JPopupMenu popup = new JPopupMenu();

        JMenuItem open = new JMenuItem("Open (simulated)");
        open.addActionListener(e -> {
            Document d = docsList.getSelectedValue();
            if (d == null) return;
            JOptionPane.showMessageDialog(this,
                    "Would open: " + d.getName() + "\n\nPath hint: " + d.getPathHint() + "\nSize: " + d.formattedSize(),
                    "Open document",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        popup.add(open);

        JMenuItem copyPath = new JMenuItem("Copy path-hint");
        copyPath.addActionListener(e -> {
            Document d = docsList.getSelectedValue();
            if (d == null) return;
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new StringSelection(d.getPathHint()), null);
        });
        popup.add(copyPath);

        popup.addSeparator();

        JMenuItem delete = new JMenuItem("Delete document");
        delete.addActionListener(e -> {
            Document d = docsList.getSelectedValue();
            if (d == null) return;
            int answer = JOptionPane.showConfirmDialog(this,
                    "Delete \"" + d.getName() + "\" from this case?",
                    "Confirm delete document",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) return;
            theCase.removeDocument(d);
            store.fireDocumentsChanged(theCase);
        });
        popup.add(delete);

        return popup;
    }

    private void refreshHeader() {
        String priority = switch (theCase.getPriority()) {
            case LOW -> "<span style='color:#888'>Low</span>";
            case MEDIUM -> "<span style='color:#0066aa'>Medium</span>";
            case HIGH -> "<span style='color:#aa2200'><b>High</b></span>";
        };
        String archived = theCase.isArchived() ? " · <i>archived</i>" : "";
        headerLabel.setText("<html><b>" + escape(theCase.getTitle()) + "</b>"
                + " &nbsp; <span style='color:#666'>#" + theCase.getCaseId() + "</span>"
                + " &nbsp; · &nbsp; " + escape(theCase.getClient())
                + " &nbsp; · &nbsp; " + priority + archived + "</html>");
    }

    private void refreshDocs() {
        Document selected = docsList.getSelectedValue();
        docsModel.clear();
        for (Document d : theCase.getDocuments()) docsModel.addElement(d);
        if (selected != null && theCase.getDocuments().contains(selected)) {
            docsList.setSelectedValue(selected, true);
        }
    }

    private void refreshNotes() {
        notesPane.setText(theCase.getNotes() == null ? "" : theCase.getNotes());
        SwingUtilities.invokeLater(() -> notesPane.setCaretPosition(0));
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Cell renderer for documents: shows name, size, and a type-prefix glyph. */
    private static final class DocumentCellRenderer extends vaadinx.swing.DefaultListCellRenderer {
        @Override
        public vaadinx.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean isSelected, boolean cellHasFocus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Document d) {
                String typeGlyph = switch (d.getType()) {
                    case PDF -> "[PDF]";
                    case IMAGE -> "[IMG]";
                    case TEXT -> "[TXT]";
                    case OTHER -> "[FILE]";
                };
                l.setText(typeGlyph + " " + d.getName() + " — " + d.formattedSize());
            }
            return l;
        }
    }
}
