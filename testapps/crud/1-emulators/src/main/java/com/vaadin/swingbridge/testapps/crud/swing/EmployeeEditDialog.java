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

import vaadinx.swing.BorderFactory;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JDialog;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JOptionPane;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPasswordField;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JSlider;
import vaadinx.swing.JSpinner;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.WindowConstants;
import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import java.awt.GridBagConstraints;
import vaadinx.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import vaadinx.util.Calendar;
import java.util.Date;

public final class EmployeeEditDialog extends JDialog {

    private final Employee working;
    private boolean accepted = false;

    private final JTextField nameField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JTextArea bioField = new JTextArea(4, 24);
    private final JCheckBox activeField = new JCheckBox("Active");
    private final JComboBox<Employee.Role> roleField = new JComboBox<>(Employee.Role.values());
    private final JSpinner levelField = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JSlider ratingField = new JSlider(0, 100, 50);
    private final JSpinner dobField;

    public EmployeeEditDialog(JFrame owner, String title, Employee initial) {
        super(owner, title, true);
        this.working = new Employee(initial);

        Date min = ymd(1800, 1, 1);
        Date max = ymd(2100, 12, 31);
        Date current = working.getDateOfBirth() == null ? new Date() : working.getDateOfBirth();
        SpinnerDateModel dateModel = new SpinnerDateModel(current, min, max, Calendar.DAY_OF_MONTH);
        dobField = new JSpinner(dateModel);
        dobField.setEditor(new JSpinner.DateEditor(dobField, "yyyy-MM-dd"));

        bioField.setLineWrap(true);
        bioField.setWrapStyleWord(true);

        ratingField.setMajorTickSpacing(25);
        ratingField.setMinorTickSpacing(5);
        ratingField.setPaintTicks(true);
        ratingField.setPaintLabels(true);

        loadFromBean();

        setContentPane(buildContent());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(findDefaultButton());
        pack();
        setLocationRelativeTo(owner);
    }

    private JButton defaultButton;

    private JButton findDefaultButton() { return defaultButton; }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, g, row++, "Name", nameField);
        addRow(form, g, row++, "Password", passwordField);
        addRow(form, g, row++, "Role", roleField);
        addRow(form, g, row++, "Level", levelField);
        addRow(form, g, row++, "Rating", ratingField);
        addRow(form, g, row++, "Date of birth", dobField);
        addRow(form, g, row++, "", activeField);
        addRow(form, g, row++, "Bio", new JScrollPane(bioField));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(this::onOk);
        cancel.addActionListener(this::onCancel);
        buttons.add(ok);
        buttons.add(cancel);
        defaultButton = ok;

        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private static void addRow(JPanel form, GridBagConstraints g, int row, String label, vaadinx.awt.Component field) {
        g.gridx = 0; g.gridy = row;
        g.weightx = 0; g.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, g);
    }

    private void loadFromBean() {
        nameField.setText(working.getName());
        passwordField.setText(working.getPassword());
        bioField.setText(working.getBio());
        activeField.setSelected(working.isActive());
        roleField.setSelectedItem(working.getRole());
        levelField.setValue(working.getLevel());
        ratingField.setValue(working.getRating());
        if (working.getDateOfBirth() != null) {
            dobField.setValue(working.getDateOfBirth());
        }
    }

    private void onOk(ActionEvent e) {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name must not be empty.", "Validation", JOptionPane.ERROR_MESSAGE);
            nameField.requestFocusInWindow();
            return;
        }
        char[] pw = passwordField.getPassword();
        if (pw.length == 0 && roleField.getSelectedItem() != Employee.Role.GUEST) {
            JOptionPane.showMessageDialog(this, "Password must not be empty for non-GUEST roles.", "Validation", JOptionPane.ERROR_MESSAGE);
            passwordField.requestFocusInWindow();
            return;
        }
        working.setName(name);
        working.setPassword(new String(pw));
        working.setBio(bioField.getText());
        working.setActive(activeField.isSelected());
        working.setRole((Employee.Role) roleField.getSelectedItem());
        working.setLevel((Integer) levelField.getValue());
        working.setRating(ratingField.getValue());
        working.setDateOfBirth((Date) dobField.getValue());
        accepted = true;
        dispose();
    }

    private void onCancel(ActionEvent e) {
        accepted = false;
        dispose();
    }

    public boolean isAccepted() { return accepted; }

    public Employee result() { return working; }

    private static Date ymd(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
}
