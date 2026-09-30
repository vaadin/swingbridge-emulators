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
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JList;
import vaadinx.swing.JPanel;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.TransferHandler;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.ListSelectionModel;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Drag-and-drop demo (D_drag_and_drop) — the "drag a document between two
 * case lists" shape the JLawyer-shape testbed exercises. Two single-selection
 * {@link JList}s wired with a shared {@link TransferHandler} via
 * {@code setDragEnabled(true)} + {@code setDropMode(DropMode.INSERT)} +
 * {@code setTransferHandler}; in a real browser the user drags a row from one
 * list onto the other and the document moves (MOVE: the source loses it). The
 * {@code createTransferable} / {@code canImport} / {@code importData} /
 * {@code exportDone} flow is the JDK-canonical split — importData adds to the
 * drop target's model, exportDone removes from the source's.
 *
 * <p>The "Move selected" buttons are a no-drag affordance (keyboard users /
 * the WARN-inventory exit gate, which can't fire a browser drag gesture); they
 * mutate the same models directly.
 */
public class DragAndDropPanel extends JPanel {

    private final DefaultListModel<String> leftModel = new DefaultListModel<>();
    private final DefaultListModel<String> rightModel = new DefaultListModel<>();
    final JList<String> left = new JList<>(leftModel);
    final JList<String> right = new JList<>(rightModel);
    private final JLabel status = new JLabel("Drag a document from one list to the other to move it.");

    public DragAndDropPanel() {
        super(new BorderLayout(8, 8));

        leftModel.addElement("Complaint.pdf");
        leftModel.addElement("Evidence-A.png");
        leftModel.addElement("Witness-Statement.docx");
        rightModel.addElement("Invoice-2026-05.pdf");

        DocumentMoveHandler handler = new DocumentMoveHandler();
        configure(left, handler);
        configure(right, handler);

        JPanel lists = new JPanel();
        lists.setLayout(new BoxLayout(lists, BoxLayout.X_AXIS));
        lists.add(titled("Case A — documents", left));
        lists.add(Box.createHorizontalStrut(12));
        lists.add(titled("Case B — documents", right));

        JButton moveRight = new JButton("Move selected →");
        moveRight.addActionListener(e -> move(left, right));
        JButton moveLeft = new JButton("← Move selected");
        moveLeft.addActionListener(e -> move(right, left));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(moveLeft);
        buttons.add(moveRight);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "Drag-and-drop demo. Drag a document between the two lists "
                        + "(MOVE — the source list loses it). The buttons do the same "
                        + "move without a drag gesture."));

        add(header, BorderLayout.NORTH);
        add(lists, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void configure(JList<String> list, TransferHandler handler) {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(8);
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(handler);
    }

    private JPanel titled(String title, JList<String> list) {
        JPanel p = new JPanel(new BorderLayout(4, 4));
        p.add(new JLabel(title), BorderLayout.NORTH);
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        return p;
    }

    private void move(JList<String> from, JList<String> to) {
        int idx = from.getSelectedIndex();
        if (idx < 0) {
            status.setText("Select a document first.");
            return;
        }
        DefaultListModel<String> fromModel = (DefaultListModel<String>) from.getModel();
        DefaultListModel<String> toModel = (DefaultListModel<String>) to.getModel();
        String doc = fromModel.get(idx);
        fromModel.remove(idx);
        toModel.addElement(doc);
        status.setText("Moved: " + doc);
    }

    /**
     * Moves a document name (carried as a {@code stringFlavor} Transferable)
     * between document lists. Mirrors the JLawyer-shape testbed's
     * {@code DocumentTransferHandler}, minus the case-store indirection.
     */
    static final class DocumentMoveHandler extends TransferHandler {

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            Object selected = ((JList<?>) c).getSelectedValue();
            return selected == null ? null : new StringSelection(selected.toString());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            if (!(support.getComponent() instanceof JList)) return false;
            support.setDropAction(MOVE);
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                String doc = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                JList<String> targetList = (JList<String>) support.getComponent();
                DefaultListModel<String> model = (DefaultListModel<String>) targetList.getModel();
                int index = model.getSize();
                if (support.isDrop() && support.getDropLocation() instanceof JList.DropLocation dl) {
                    index = Math.min(Math.max(dl.getIndex(), 0), model.getSize());
                }
                model.add(index, doc);
                return true;
            } catch (UnsupportedFlavorException | IOException ex) {
                return false;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void exportDone(JComponent source, Transferable data, int action) {
            if (action != MOVE || data == null) return;
            try {
                String doc = (String) data.getTransferData(DataFlavor.stringFlavor);
                DefaultListModel<String> model = (DefaultListModel<String>) ((JList<String>) source).getModel();
                int i = model.indexOf(doc);
                if (i >= 0) model.remove(i);
            } catch (UnsupportedFlavorException | IOException ex) {
                // best-effort R_best_effort_behaviour
            }
        }
    }
}
