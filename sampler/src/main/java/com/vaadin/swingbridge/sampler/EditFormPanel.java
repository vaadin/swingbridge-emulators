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
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JDialog;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;

import javax.swing.WindowConstants;

/**
 * CRUD edit-form demo. {@link JButton}s open a {@link JDialog} that
 * hosts an editable form with two fields and OK / Cancel.
 *
 * <p>Two routes through here exercise different parts of the dialog
 * surface:
 * <ul>
 *   <li><b>"Edit (modal)"</b>: blocking-modal show. JButton's click
 *       handler runs inside {@code EHelper.callSwing} (R_callswing_envelope) on a
 *       virtual thread; {@code dialog.setVisible(true)} parks the VT
 *       until OK / Cancel disposes; the handler then reads the result
 *       inline.</li>
 *   <li><b>"Edit (modeless)"</b>: non-blocking show. The handler
 *       returns immediately; the result flows back via a
 *       {@link vaadinx.awt.event.WindowAdapter} that fires on
 *       {@code DISPOSE_ON_CLOSE}.</li>
 * </ul>
 */
public class EditFormPanel extends JPanel {

    private final JLabel status = new JLabel("Click 'Edit' to open the modal form.");

    // Persistent state edited by the dialog.
    private String name = "Ada Lovelace";
    private boolean active = true;

    public EditFormPanel() {
        super(new BorderLayout(8, 8));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "CRUD edit-form host. The 'Edit' buttons open a modal "
                        + "JDialog with two form fields and OK / Cancel."));
        header.add(new JLabel(
                "The blocking variant parks its UI fiber until the dialog closes; the modeless "
                        + "variant uses a windowClosed listener for the result."));

        JButton editModal = new JButton("Edit (modal, blocking)");
        editModal.addActionListener(e -> openEditDialog(true));

        JButton editModeless = new JButton("Edit (modeless, non-blocking)");
        editModeless.addActionListener(e -> openEditDialog(false));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(editModal);
        buttons.add(editModeless);

        add(header, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private void openEditDialog(boolean modal) {
        JDialog dialog = new JDialog((vaadinx.awt.Frame) null, "Edit record", modal);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JTextField nameField = new JTextField(name, 24);
        JCheckBox activeBox = new JCheckBox("Active", active);

        JPanel form = new JPanel();
        form.setLayout(new FlowLayout());
        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(activeBox);

        // Result holder — true if OK was pressed, false (default) on
        // Cancel / X / ESC.
        boolean[] confirmed = { false };

        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            confirmed[0] = true;
            this.name = nameField.getText();
            this.active = activeBox.isSelected();
            dialog.dispose();
        });

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel();
        footer.setLayout(new FlowLayout());
        footer.add(ok);
        footer.add(cancel);

        dialog.setLayout(new BorderLayout());
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        if (modal) {
            // Blocking-modal show: this thread (a VT inside the
            // JButton's callSwing envelope) parks here until dispose()
            // releases the modal latch.
            dialog.setVisible(true);
            // setVisible returned → dialog is closed → confirmed[0] +
            // this.name / this.active reflect the user's choice.
            status.setText(confirmed[0]
                    ? "Saved (modal): name=" + name + ", active=" + active
                    : "Cancelled (modal).");
        } else {
            // Modeless show: returns immediately; result arrives via
            // the windowClosed listener.
            dialog.addWindowListener(new vaadinx.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(vaadinx.awt.event.WindowEvent e) {
                    status.setText(confirmed[0]
                            ? "Saved (modeless): name=" + name + ", active=" + active
                            : "Cancelled (modeless).");
                }
            });
            dialog.setVisible(true);
        }
    }
}
