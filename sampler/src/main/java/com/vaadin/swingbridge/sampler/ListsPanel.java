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
import vaadinx.swing.DefaultListCellRenderer;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JList;
import vaadinx.swing.JPanel;
import vaadinx.swing.JScrollPane;

import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;

/**
 * JList demo — the "per-case document list" shape. Hosts a
 * single-selection {@link JList} over a {@link DefaultListModel} of
 * document names, wrapped in a {@link JScrollPane} (whose auto-scroll
 * guard recognises the Grid peer, so no stacked scrollbars), with:
 *
 * <ul>
 *   <li>a custom {@link DefaultListCellRenderer} subclass prefixing each
 *       row — exercises the emulator→surrogate renderer-snapshot bridge;</li>
 *   <li>a {@link javax.swing.event.ListSelectionListener} on the selection
 *       model updating the status label;</li>
 *   <li>a {@link MouseAdapter} implementing double-click-to-open via
 *       {@code getClickCount() == 2} + {@code locationToIndex(point)} —
 *       the dominant JList interaction;</li>
 *   <li>Add / Remove buttons mutating the model.</li>
 * </ul>
 */
public class ListsPanel extends JPanel {

    private final JLabel status = new JLabel("Select a document, or double-click to open it.");
    final DefaultListModel<String> model = new DefaultListModel<>();
    final JList<String> list = new JList<>(model);
    private int counter = 0;

    public ListsPanel() {
        super(new BorderLayout(8, 8));

        model.addElement("Complaint.pdf");
        model.addElement("Evidence-A.png");
        model.addElement("Witness-Statement.docx");
        model.addElement("Invoice-2026-05.pdf");

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);
        list.setCellRenderer(new DocumentRenderer());

        // Selection → status label, read via the JDK selection model accessor.
        list.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = list.getSelectedIndex();
            status.setText(idx < 0 ? "No document selected."
                    : "Selected: " + model.get(idx));
        });

        // Double-click-to-open — the canonical JList idiom. getClickCount()==2
        // plus locationToIndex(point) resolve through the surrogate's
        // item-click bridge + click-index stash.
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = list.locationToIndex(new java.awt.Point(e.getX(), e.getY()));
                    if (idx >= 0) {
                        status.setText("Opened: " + model.get(idx));
                    }
                }
            }
        });

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "JList demo (per-case document list). Single selection; "
                        + "double-click a row to open it; custom renderer prefixes "
                        + "each item; Add / Remove mutate the DefaultListModel."));

        JButton add = new JButton("Add document");
        add.addActionListener(e -> {
            counter++;
            model.addElement("New-Document-" + counter + ".pdf");
        });

        JButton remove = new JButton("Remove selected");
        remove.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0) model.remove(idx);
        });

        JButton selectFirst = new JButton("Select first");
        selectFirst.addActionListener(e -> {
            if (model.size() > 0) list.setSelectedIndex(0);
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(add);
        buttons.add(remove);
        buttons.add(selectFirst);

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    /**
     * DefaultListCellRenderer subclass that prefixes each document name —
     * the JDK mutate-and-return-self pattern, exercising the
     * emulator→surrogate renderer bridge through the snapshot path.
     */
    static final class DocumentRenderer extends DefaultListCellRenderer {
        @Override
        public vaadinx.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setText("Doc: " + value);
            return this;
        }
    }
}
