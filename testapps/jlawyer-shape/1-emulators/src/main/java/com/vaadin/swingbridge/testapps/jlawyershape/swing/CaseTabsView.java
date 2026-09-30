package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import vaadinx.swing.BorderFactory;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JList;
import vaadinx.swing.JTabbedPane;
import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import java.awt.Insets;
import java.util.HashMap;
import java.util.Map;

public final class CaseTabsView extends JPanel {

    private final CaseStore store;
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
    private final Map<Case, CaseView> openCases = new HashMap<>();

    public CaseTabsView(CaseStore store) {
        super(new BorderLayout());
        this.store = store;
        add(tabs, BorderLayout.CENTER);
    }

    public JTabbedPane getTabs() { return tabs; }

    /** Open a case in a new tab, or focus the existing tab if already open. Returns the per-case view either way. */
    public CaseView openCase(Case c) {
        CaseView existing = openCases.get(c);
        if (existing != null) {
            tabs.setSelectedComponent(existing);
            return existing;
        }
        CaseView view = new CaseView(store, c);
        openCases.put(c, view);
        int index = tabs.getTabCount();
        tabs.addTab(c.getTitle(), view);
        tabs.setTabComponentAt(index, buildTabHeader(c, view));
        tabs.setSelectedIndex(index);
        return view;
    }

    /** Reverse lookup: which case owns the given documents JList? Used by the DnD handler. */
    public Case caseOf(JList<Document> list) {
        for (Map.Entry<Case, CaseView> e : openCases.entrySet()) {
            if (e.getValue().getDocsList() == list) return e.getKey();
        }
        return null;
    }

    public void closeCase(Case c) {
        CaseView view = openCases.remove(c);
        if (view == null) return;
        int idx = tabs.indexOfComponent(view);
        if (idx >= 0) tabs.removeTabAt(idx);
    }

    public Case getActiveCase() {
        vaadinx.awt.Component sel = tabs.getSelectedComponent();
        if (!(sel instanceof CaseView)) return null;
        return ((CaseView) sel).getCase();
    }

    public void refreshTabTitles() {
        for (Map.Entry<Case, CaseView> e : openCases.entrySet()) {
            int idx = tabs.indexOfComponent(e.getValue());
            if (idx >= 0) tabs.setTabComponentAt(idx, buildTabHeader(e.getKey(), e.getValue()));
        }
    }

    private JPanel buildTabHeader(Case c, CaseView view) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
        JLabel title = new JLabel(c.getTitle());
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        JButton close = new JButton("×");
        close.setMargin(new Insets(0, 4, 0, 4));
        close.setFocusable(false);
        close.setToolTipText("Close tab");
        close.addActionListener(e -> closeCase(c));
        header.add(title);
        header.add(close);
        return header;
    }
}
