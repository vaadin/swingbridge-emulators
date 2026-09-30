package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.WindowConstants;
import javax.swing.text.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;

public final class CasePropertiesDialog extends JDialog {

    private final Case working;
    private boolean accepted = false;

    private final JTextField titleField = new JTextField(28);
    private final JTextField clientField = new JTextField(28);
    private final JRadioButton priorityLow = new JRadioButton("Low");
    private final JRadioButton priorityMed = new JRadioButton("Medium");
    private final JRadioButton priorityHigh = new JRadioButton("High");
    private final JCheckBox archivedField = new JCheckBox("Archived");
    private final JSpinner dateOpenedField;
    private final JFormattedTextField caseIdField;
    private final JComboBox<String> jurisdictionField = new JComboBox<>(new String[] {
            "Federal", "State — California", "State — New York", "State — Texas", "Other"
    });

    public CasePropertiesDialog(JFrame owner, Case initial) {
        super(owner, "Case Properties", true);
        this.working = new Case(initial);

        Date min = ymd(1990, 1, 1);
        Date max = ymd(2100, 12, 31);
        Date current = working.getDateOpened() == null ? new Date() : working.getDateOpened();
        SpinnerDateModel dateModel = new SpinnerDateModel(current, min, max, Calendar.DAY_OF_MONTH);
        dateOpenedField = new JSpinner(dateModel);
        dateOpenedField.setEditor(new JSpinner.DateEditor(dateOpenedField, "yyyy-MM-dd"));

        NumberFormat fmt = NumberFormat.getIntegerInstance();
        fmt.setGroupingUsed(false);
        NumberFormatter idFormatter = new NumberFormatter(fmt);
        idFormatter.setValueClass(Integer.class);
        idFormatter.setMinimum(0);
        idFormatter.setMaximum(99_999);
        idFormatter.setAllowsInvalid(false);
        caseIdField = new JFormattedTextField(idFormatter);
        caseIdField.setColumns(8);
        caseIdField.setValue(working.getCaseId());
        caseIdField.setEditable(false); // Case ID is immutable post-creation, displayed read-only.

        ButtonGroup priorityGroup = new ButtonGroup();
        priorityGroup.add(priorityLow);
        priorityGroup.add(priorityMed);
        priorityGroup.add(priorityHigh);

        loadFromBean();

        setContentPane(buildContent());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(form, g, row++, "Case ID", caseIdField);
        addRow(form, g, row++, "Title", titleField);
        addRow(form, g, row++, "Client", clientField);
        addRow(form, g, row++, "Jurisdiction", jurisdictionField);
        addRow(form, g, row++, "Priority", priorityPanel());
        addRow(form, g, row++, "Date opened", dateOpenedField);
        addRow(form, g, row++, "", archivedField);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(this::onOk);
        cancel.addActionListener(this::onCancel);
        buttons.add(ok);
        buttons.add(cancel);
        getRootPane().setDefaultButton(ok);

        root.add(form, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        return root;
    }

    private JPanel priorityPanel() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.add(priorityLow);
        p.add(priorityMed);
        p.add(priorityHigh);
        return p;
    }

    private static void addRow(JPanel form, GridBagConstraints g, int row, String label, Component field) {
        g.gridx = 0; g.gridy = row;
        g.weightx = 0; g.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), g);
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, g);
    }

    private void loadFromBean() {
        titleField.setText(working.getTitle());
        clientField.setText(working.getClient());
        switch (working.getPriority()) {
            case LOW -> priorityLow.setSelected(true);
            case MEDIUM -> priorityMed.setSelected(true);
            case HIGH -> priorityHigh.setSelected(true);
        }
        archivedField.setSelected(working.isArchived());
        if (working.getDateOpened() != null) dateOpenedField.setValue(working.getDateOpened());
    }

    private void onOk(ActionEvent e) {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Title must not be empty.", "Validation", JOptionPane.ERROR_MESSAGE);
            titleField.requestFocusInWindow();
            return;
        }
        String client = clientField.getText().trim();
        if (client.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Client must not be empty.", "Validation", JOptionPane.ERROR_MESSAGE);
            clientField.requestFocusInWindow();
            return;
        }
        working.setTitle(title);
        working.setClient(client);
        working.setPriority(selectedPriority());
        working.setArchived(archivedField.isSelected());
        working.setDateOpened((Date) dateOpenedField.getValue());
        accepted = true;
        dispose();
    }

    private void onCancel(ActionEvent e) {
        accepted = false;
        dispose();
    }

    private Case.Priority selectedPriority() {
        if (priorityHigh.isSelected()) return Case.Priority.HIGH;
        if (priorityLow.isSelected()) return Case.Priority.LOW;
        return Case.Priority.MEDIUM;
    }

    public boolean isAccepted() { return accepted; }
    public Case result() { return working; }

    private static Date ymd(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
}
