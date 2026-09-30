package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.Date;

public final class MainFrame extends JFrame {

    private final CaseStore store;
    private final CaseTreeView treeView;
    private final CaseTabsView tabsView;
    private final DocumentTransferHandler transferHandler;

    private final JLabel statusLeft = new JLabel("Ready.");
    private final JLabel statusRight = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar();

    private final JButton newCaseButton = new JButton("New Case…");
    private final JButton propertiesButton = new JButton("Properties…");
    private final JButton deleteCaseButton = new JButton("Delete");
    private final JButton copyIdButton = new JButton("Copy Case ID");
    private final JButton uploadButton = new JButton("Upload Document…");
    private final JButton longOpButton = new JButton("Simulate Long Op");
    private final JButton invoiceButton = new JButton("Invoice…");
    private final JCheckBoxMenuItem showTreeItem = new JCheckBoxMenuItem("Show case tree", true);

    private JSplitPane outerSplit;

    public MainFrame(CaseStore store) {
        super("JLawyer-shape");
        this.store = store;
        this.treeView = new CaseTreeView(store);
        this.tabsView = new CaseTabsView(store);
        this.transferHandler = new DocumentTransferHandler(store, tabsView::caseOf);

        setJMenuBar(buildMenuBar());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buildToolBar(), BorderLayout.NORTH);

        outerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeView, tabsView);
        outerSplit.setDividerLocation(240);
        outerSplit.setResizeWeight(0.0);
        getContentPane().add(outerSplit, BorderLayout.CENTER);

        getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

        treeView.getTree().setComponentPopupMenu(buildTreePopup());
        // The tree is the drop target for document moves: dragging a document from an
        // open case's list onto a case node here moves it into that case. The tree is
        // always visible, unlike the per-case lists which share a single tabbed pane.
        treeView.getTree().setTransferHandler(transferHandler);
        treeView.getTree().setDropMode(DropMode.ON);
        treeView.addCaseSelectionListener(this::onCaseSelected);
        tabsView.getTabs().addChangeListener(e -> onSelectionChanged());

        store.addListener(new CaseStore.Listener() {
            @Override public void treeChanged() { onSelectionChanged(); refreshStatus(); }
            @Override public void caseUpdated(Case c) { tabsView.refreshTabTitles(); onSelectionChanged(); }
            @Override public void documentsChanged(Case c) { refreshStatus(); }
        });

        onSelectionChanged();
        refreshStatus();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1080, 660));
        pack();
        setLocationRelativeTo(null);
    }

    // ---- menu bar ----

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem newCase = new JMenuItem(new AbstractAction("New case…") {
            @Override public void actionPerformed(ActionEvent e) { onAddCase(); }
        });
        newCase.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
        file.add(newCase);
        JMenuItem upload = new JMenuItem(new AbstractAction("Upload document…") {
            @Override public void actionPerformed(ActionEvent e) { onUpload(); }
        });
        upload.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
        file.add(upload);
        file.addSeparator();
        JMenuItem exit = new JMenuItem(new AbstractAction("Exit") {
            @Override public void actionPerformed(ActionEvent e) { dispose(); System.exit(0); }
        });
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK));
        file.add(exit);
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);
        edit.add(new AbstractAction("Properties…") {
            @Override public void actionPerformed(ActionEvent e) { onProperties(); }
        });
        edit.add(new AbstractAction("Delete case") {
            @Override public void actionPerformed(ActionEvent e) { onDeleteCase(); }
        });
        edit.addSeparator();
        edit.add(new AbstractAction("Copy case ID") {
            @Override public void actionPerformed(ActionEvent e) { onCopyCaseId(); }
        });
        bar.add(edit);

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        showTreeItem.addActionListener(e -> {
            boolean show = showTreeItem.isSelected();
            treeView.setVisible(show);
            outerSplit.resetToPreferredSizes();
        });
        view.add(showTreeItem);
        bar.add(view);

        JMenu tools = new JMenu("Tools");
        tools.setMnemonic(KeyEvent.VK_T);
        tools.add(new AbstractAction("Simulate long operation") {
            @Override public void actionPerformed(ActionEvent e) { onSimulateLongOp(); }
        });
        bar.add(tools);

        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(new AbstractAction("About…") {
            @Override public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(MainFrame.this,
                        "JLawyer-shape\n\n"
                                + "Hand-built JLawyer-shape app exercising the longer-tail\n"
                                + "component additions (JTree, JList, JSplitPane,\n"
                                + "JTabbedPane, JPopupMenu, JFileChooser, JToolBar,\n"
                                + "JEditorPane, JProgressBar, standalone JRadioButton)\n"
                                + "plus clipboard and drag-and-drop.",
                        "About", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        bar.add(help);

        return bar;
    }

    // ---- toolbar ----

    private JToolBar buildToolBar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        tb.setRollover(true);
        tb.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        newCaseButton.addActionListener(e -> onAddCase());
        tb.add(newCaseButton);
        propertiesButton.addActionListener(e -> onProperties());
        tb.add(propertiesButton);
        deleteCaseButton.addActionListener(e -> onDeleteCase());
        tb.add(deleteCaseButton);
        tb.addSeparator();
        copyIdButton.addActionListener(e -> onCopyCaseId());
        tb.add(copyIdButton);
        uploadButton.addActionListener(e -> onUpload());
        tb.add(uploadButton);
        tb.addSeparator();
        longOpButton.addActionListener(e -> onSimulateLongOp());
        tb.add(longOpButton);
        invoiceButton.addActionListener(e -> onOpenInvoice());
        tb.add(invoiceButton);

        return tb;
    }

    // ---- tree popup ----

    private JPopupMenu buildTreePopup() {
        JPopupMenu popup = new JPopupMenu();
        popup.add(new AbstractAction("New case here…") {
            @Override public void actionPerformed(ActionEvent e) { onAddCase(); }
        });
        popup.add(new AbstractAction("Properties…") {
            @Override public void actionPerformed(ActionEvent e) { onProperties(); }
        });
        popup.addSeparator();
        popup.add(new AbstractAction("Open in tab") {
            @Override public void actionPerformed(ActionEvent e) {
                Case c = treeView.getSelectedCase();
                if (c != null) openCase(c);
            }
        });
        popup.addSeparator();
        popup.add(new AbstractAction("Delete case") {
            @Override public void actionPerformed(ActionEvent e) { onDeleteCase(); }
        });
        return popup;
    }

    // ---- status bar ----

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setString("");
        progressBar.setPreferredSize(new Dimension(180, 16));
        progressBar.setVisible(false);
        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        center.add(progressBar, BorderLayout.EAST);
        bar.add(statusLeft, BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        bar.add(statusRight, BorderLayout.EAST);
        return bar;
    }

    // ---- actions ----

    private void onCaseSelected(Case c) {
        openCase(c);
    }

    private void openCase(Case c) {
        CaseView view = tabsView.openCase(c);
        view.getDocsList().setTransferHandler(transferHandler);
        onSelectionChanged();
    }

    private Case currentCase() {
        Case active = tabsView.getActiveCase();
        if (active != null) return active;
        return treeView.getSelectedCase();
    }

    private void onAddCase() {
        CaseFolder folder = treeView.getSelectedFolderOrParent();
        Case seed = new Case("New case", "(client TBD)", Case.Priority.MEDIUM, false, new Date(),
                "<p><i>No notes yet.</i></p>");
        CasePropertiesDialog dlg = new CasePropertiesDialog(this, seed);
        dlg.setVisible(true);
        if (dlg.isAccepted()) {
            seed.copyEditableFieldsFrom(dlg.result());
            folder.addCase(seed);
            store.fireTreeChanged();
            treeView.selectCase(seed);
            openCase(seed);
        }
    }

    private void onProperties() {
        Case c = currentCase();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Select a case first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        CasePropertiesDialog dlg = new CasePropertiesDialog(this, c);
        dlg.setVisible(true);
        if (dlg.isAccepted()) {
            c.copyEditableFieldsFrom(dlg.result());
            store.fireCaseUpdated(c);
            store.fireTreeChanged();
        }
    }

    // Opens the real (unmodified) JLawyer InvoicePositionEntryPanel slice — the
    // Stage-1 source this testbed later import-swaps onto :emulators.
    // Modeless editor; the dialog seeds two sample rows.
    private void onOpenInvoice() {
        new com.jdimension.jlawyer.client.editors.files.InvoiceDialog(this).setVisible(true);
    }

    private void onDeleteCase() {
        Case c = currentCase();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Select a case first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete case \"" + c.getTitle() + "\" (#" + c.getCaseId() + ")?\nAll documents on the case will be lost.",
                "Confirm delete",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) return;
        CaseFolder owner = store.findFolderOf(c);
        if (owner == null) return;
        owner.removeCase(c);
        tabsView.closeCase(c);
        store.fireTreeChanged();
    }

    private void onCopyCaseId() {
        Case c = currentCase();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Select a case first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        cb.setContents(new StringSelection("#" + c.getCaseId()), null);
        statusLeft.setText("Copied #" + c.getCaseId() + " to clipboard.");
    }

    private void onUpload() {
        Case c = currentCase();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Select a case first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Upload document to " + c.getTitle());
        chooser.setMultiSelectionEnabled(true);
        int rc = chooser.showOpenDialog(this);
        if (rc != JFileChooser.APPROVE_OPTION) return;
        File[] files = chooser.getSelectedFiles();
        if (files == null || files.length == 0) {
            File single = chooser.getSelectedFile();
            if (single != null) files = new File[] { single };
        }
        if (files == null || files.length == 0) return;
        for (File f : files) {
            Document d = new Document(f.getName(), guessType(f.getName()), Math.max(f.length(), 0), new Date(),
                    f.getAbsolutePath());
            c.addDocument(d);
        }
        store.fireDocumentsChanged(c);
        statusLeft.setText("Uploaded " + files.length + " document(s) to " + c.getTitle() + ".");
    }

    private static Document.Type guessType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return Document.Type.PDF;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")) return Document.Type.IMAGE;
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".docx") || lower.endsWith(".doc")) return Document.Type.TEXT;
        return Document.Type.OTHER;
    }

    private void onSimulateLongOp() {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Working…");
        setButtonsDuringOp(false);
        statusLeft.setText("Long operation running…");

        Timer t = new Timer(2500, evt -> {
            progressBar.setIndeterminate(false);
            progressBar.setVisible(false);
            setButtonsDuringOp(true);
            statusLeft.setText("Operation complete.");
            JOptionPane.showMessageDialog(this, "Simulated work finished.", "Done", JOptionPane.INFORMATION_MESSAGE);
        });
        t.setRepeats(false);
        t.start();
    }

    private void setButtonsDuringOp(boolean enabled) {
        newCaseButton.setEnabled(enabled);
        propertiesButton.setEnabled(enabled && currentCase() != null);
        deleteCaseButton.setEnabled(enabled && currentCase() != null);
        copyIdButton.setEnabled(enabled && currentCase() != null);
        uploadButton.setEnabled(enabled && currentCase() != null);
        longOpButton.setEnabled(enabled);
    }

    private void onSelectionChanged() {
        boolean hasCase = currentCase() != null;
        propertiesButton.setEnabled(hasCase);
        deleteCaseButton.setEnabled(hasCase);
        copyIdButton.setEnabled(hasCase);
        uploadButton.setEnabled(hasCase);
    }

    private void refreshStatus() {
        int caseCount = store.allCases().size();
        int docCount = 0;
        for (Case c : store.allCases()) docCount += c.getDocuments().size();
        statusRight.setText(caseCount + " cases · " + docCount + " documents");
    }
}
