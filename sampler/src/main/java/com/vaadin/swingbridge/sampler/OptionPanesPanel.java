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
import vaadinx.swing.JLabel;
import vaadinx.swing.JOptionPane;
import vaadinx.swing.JPanel;

/**
 * JOptionPane demo. {@link JButton}s open the four blocking
 * static factories ({@link JOptionPane#showMessageDialog},
 * {@link JOptionPane#showConfirmDialog},
 * {@link JOptionPane#showInputDialog},
 * {@link JOptionPane#showOptionDialog}); each click parks the calling
 * UI fiber ({@code EHelper.callSwing}) until OK / Yes / No / Cancel / X
 * dispatch a value.
 */
public class OptionPanesPanel extends JPanel {

    private final JLabel status = new JLabel("Click a button to show a JOptionPane dialog.");

    public OptionPanesPanel() {
        super(new BorderLayout(8, 8));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "Each button opens a blocking JOptionPane dialog. The "
                        + "result is read inline after the dialog closes — "
                        + "the calling UI fiber parks, the session lock released, "
                        + "until OK / Yes / No / Cancel / X dispatch a value."));

        JButton showMessage = new JButton("Show message");
        showMessage.addActionListener(e -> {
            JOptionPane.showMessageDialog(null, "All saved.", "Info", JOptionPane.INFORMATION_MESSAGE);
            status.setText("Message dialog dismissed.");
        });

        JButton showConfirm = new JButton("Show confirm");
        showConfirm.addActionListener(e -> {
            int ret = JOptionPane.showConfirmDialog(
                    null, "Save changes?", "Confirm", JOptionPane.YES_NO_CANCEL_OPTION);
            status.setText("Confirm result: " + describeConfirm(ret));
        });

        JButton showInput = new JButton("Show input");
        showInput.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(null, "Your name?");
            status.setText("Input result: " + (name == null ? "(cancelled)" : name));
        });

        JButton showOption = new JButton("Show option");
        showOption.addActionListener(e -> {
            Object[] options = { "Save", "Discard", "Keep editing" };
            int ret = JOptionPane.showOptionDialog(
                    null,
                    "You have unsaved changes.",
                    "Confirm",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]);
            status.setText("Option result: " + (ret == JOptionPane.CLOSED_OPTION
                    ? "(closed)" : options[ret]));
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(showMessage);
        buttons.add(showConfirm);
        buttons.add(showInput);
        buttons.add(showOption);

        add(header, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private static String describeConfirm(int ret) {
        return switch (ret) {
            case JOptionPane.YES_OPTION -> "Yes";
            case JOptionPane.NO_OPTION -> "No";
            case JOptionPane.CANCEL_OPTION -> "Cancel";
            case JOptionPane.CLOSED_OPTION -> "Closed";
            default -> "?(" + ret + ")";
        };
    }
}
