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

package com.vaadin.swingbridge.testapps.crud.swing;

import javax.swing.AbstractAction;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.ButtonGroup;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBoxMenuItem;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuBar;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JOptionPane;
import vaadinx.swing.JPanel;
import vaadinx.swing.JRadioButtonMenuItem;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JSeparator;
import vaadinx.swing.JTable;
import vaadinx.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import vaadinx.awt.BorderLayout;
import java.awt.Dimension;
import vaadinx.awt.FlowLayout;
import java.awt.event.ActionEvent;
import vaadinx.awt.event.KeyEvent;
import vaadinx.swing.MainWindow;

@MainWindow
public final class MainFrame extends JFrame {

    private final EmployeeStore store;
    private final EmployeeTableModel tableModel;
    private final JTable table;
    private final EmployeePreviewPanel preview = new EmployeePreviewPanel();

    private final JButton editButton = new JButton("Edit…");
    private final JButton deleteButton = new JButton("Delete");
    private final JToggleButton favoritesOnly = new JToggleButton("Favorites only");
    private final JLabel statusLabel = new JLabel(" ");
    private final JCheckBoxMenuItem showPreview = new JCheckBoxMenuItem("Show preview", true);

    private JPanel previewHost;

    public MainFrame(EmployeeStore store) {
        super("Employees — Swing CRUD");
        this.store = store;
        this.tableModel = new EmployeeTableModel(store);

        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onSelectionChanged();
        });

        setJMenuBar(buildMenuBar());

        previewHost = new JPanel(new BorderLayout());
        previewHost.add(preview, BorderLayout.CENTER);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(buildToolbar(), BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(previewHost, BorderLayout.EAST);
        getContentPane().add(buildStatusBar(), BorderLayout.SOUTH);

        store.addListener(this::refreshStatus);
        refreshStatus();
        onSelectionChanged();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(900, 520));
        pack();
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic(KeyEvent.VK_F);
        JMenuItem newItem = new JMenuItem(new AbstractAction("New employee…") {
            @Override public void actionPerformed(ActionEvent e) { onAdd(); }
        });
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK));
        file.add(newItem);
        file.addSeparator();
        JMenuItem exit = new JMenuItem(new AbstractAction("Exit") {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });
        exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK));
        file.add(exit);
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.setMnemonic(KeyEvent.VK_E);
        edit.add(new AbstractAction("Edit selected…") {
            @Override public void actionPerformed(ActionEvent e) { onEdit(); }
        });
        edit.add(new AbstractAction("Delete selected") {
            @Override public void actionPerformed(ActionEvent e) { onDelete(); }
        });
        bar.add(edit);

        JMenu view = new JMenu("View");
        view.setMnemonic(KeyEvent.VK_V);
        showPreview.addActionListener(e -> previewHost.setVisible(showPreview.isSelected()));
        view.add(showPreview);
        view.add(new JSeparator());
        ButtonGroup densityGroup = new ButtonGroup();
        JRadioButtonMenuItem dDefault = new JRadioButtonMenuItem("Default density", true);
        JRadioButtonMenuItem dCompact = new JRadioButtonMenuItem("Compact");
        JRadioButtonMenuItem dRoomy = new JRadioButtonMenuItem("Roomy");
        densityGroup.add(dDefault);
        densityGroup.add(dCompact);
        densityGroup.add(dRoomy);
        dDefault.addActionListener(e -> table.setRowHeight(20));
        dCompact.addActionListener(e -> table.setRowHeight(16));
        dRoomy.addActionListener(e -> table.setRowHeight(28));
        view.add(dDefault);
        view.add(dCompact);
        view.add(dRoomy);
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.setMnemonic(KeyEvent.VK_H);
        help.add(new AbstractAction("About…") {
            @Override public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(MainFrame.this,
                        "Employees — Swing CRUD\nA CRUD testbed of small subset of Swing components.",
                        "About", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        bar.add(help);

        return bar;
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        bar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JButton add = new JButton("Add…");
        add.addActionListener(e -> onAdd());
        bar.add(add);
        bar.add(editButton);
        editButton.addActionListener(e -> onEdit());
        bar.add(deleteButton);
        deleteButton.addActionListener(e -> onDelete());

        bar.add(separator());

        favoritesOnly.addActionListener(e -> {
            tableModel.setFavoritesOnly(favoritesOnly.isSelected());
            refreshStatus();
        });
        bar.add(favoritesOnly);

        return bar;
    }

    private static JSeparator separator() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(2, 22));
        return s;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        bar.add(statusLabel, BorderLayout.WEST);
        return bar;
    }

    private void onSelectionChanged() {
        Employee selected = currentSelection();
        preview.show(selected);
        editButton.setEnabled(selected != null);
        deleteButton.setEnabled(selected != null);
    }

    private Employee currentSelection() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return store.get(tableModel.toStoreIndex(modelRow));
    }

    private int currentSelectionStoreIndex() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return -1;
        int modelRow = table.convertRowIndexToModel(viewRow);
        return tableModel.toStoreIndex(modelRow);
    }

    private void onAdd() {
        EmployeeEditDialog dlg = new EmployeeEditDialog(this, "New employee", new Employee());
        dlg.setVisible(true);
        if (dlg.isAccepted()) {
            store.add(dlg.result());
        }
    }

    private void onEdit() {
        int storeIndex = currentSelectionStoreIndex();
        if (storeIndex < 0) return;
        EmployeeEditDialog dlg = new EmployeeEditDialog(this, "Edit employee", store.get(storeIndex));
        dlg.setVisible(true);
        if (dlg.isAccepted()) {
            store.update(storeIndex, dlg.result());
        }
    }

    private void onDelete() {
        int storeIndex = currentSelectionStoreIndex();
        if (storeIndex < 0) return;
        Employee e = store.get(storeIndex);
        int answer = JOptionPane.showConfirmDialog(this,
                "Delete \"" + e.getName() + "\"?",
                "Confirm delete", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            store.remove(storeIndex);
        }
    }

    private void refreshStatus() {
        int total = store.size();
        int shown = tableModel.getRowCount();
        if (shown == total) {
            statusLabel.setText(total + " employees");
        } else {
            statusLabel.setText(shown + " of " + total + " employees (filtered)");
        }
    }
}
