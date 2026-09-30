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

import com.vaadin.swingbridge.surrogates.util.BrowserDateUtils;
import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JFormattedTextField;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import javax.swing.text.DateFormatter;
import javax.swing.text.MaskFormatter;
import javax.swing.text.NumberFormatter;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * JFormattedTextField demo — one row per formatter family, each showing how
 * the ctor-time formatter picks the Vaadin peer (D_jformattedtextfield strategy dispatch + SD_sjformatted_family
 * surrogate family). Every row mirrors its live value into a status label via
 * {@code addPropertyChangeListener("value", …)}:
 *
 * <pre>
 *   Date of birth: [ 2026-06-08 ▾ ]   value = …    DateFormatter    → SJFormattedDatePicker (DatePicker)
 *   Notes:         [ … ]              value = …    (no formatter)   → SJFormattedTextField  (TextField)
 *   Age:           [ 42 ]            value = 42    NumberFormatter(Integer) → SJFormattedIntegerField
 *   Account #:     [ 1234567890123 ] value = …     NumberFormatter(Long)    → SJFormattedLongField
 *   Salary:        [ 75000.5 ]       value = …     NumberFormatter(Double)  → SJFormattedNumberField
 *   Phone:         [ (415) 555-0100] value = …     MaskFormatter    → SJFormattedTextField + pattern attr
 * </pre>
 *
 * <p>{@code setValue(Object)} routes through the formatter (or
 * {@code Object.toString} for the no-formatter row) into the peer's String
 * value; Enter triggers {@code commitEdit} → {@code stringToValue} →
 * {@code setValue(parsed)}. The mask row converts {@code "(###) ###-####"} to
 * an anchored browser regex — partial coverage per R_match_swing_errors (c).
 */
public class FormattedFieldsPanel extends JPanel {

    public FormattedFieldsPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("Formatted fields"));

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));

        // Date row — DateFormatter → DatePicker peer per D_jformattedtextfield / SD_sjformatted_family.
        JFormattedTextField dob = new JFormattedTextField(new DateFormatter(
                new SimpleDateFormat("yyyy-MM-dd")));
        dob.setValue(BrowserDateUtils.dateOf(2026, 5, 8));
        JLabel dobStatus = new JLabel("value = " + dob.getValue());
        dob.addPropertyChangeListener("value", e ->
                dobStatus.setText("value = " + e.getNewValue()));
        rows.add(makeRow("Date of birth:", dob, dobStatus));

        rows.add(Box.createVerticalStrut(8));

        // No-formatter row — DefaultFormattedStrategy → SJFormattedTextField.
        // setValue stringifies via Object.toString (no formatter consultation
        // path); Enter triggers commitEdit which (without a formatter)
        // round-trips the displayed text as the value.
        JFormattedTextField notes = new JFormattedTextField();
        notes.setValue("type and press Enter");
        JLabel notesStatus = new JLabel("value = " + notes.getValue());
        notes.addPropertyChangeListener("value", e ->
                notesStatus.setText("value = " + e.getNewValue()));
        rows.add(makeRow("Notes:", notes, notesStatus));

        rows.add(Box.createVerticalStrut(8));

        // Integer row — NumberFormatter(Integer) → IntegerStrategy →
        // SJFormattedIntegerField (Vaadin IntegerField).
        NumberFormatter intFormatter = new NumberFormatter(NumberFormat.getIntegerInstance());
        intFormatter.setValueClass(Integer.class);
        JFormattedTextField age = new JFormattedTextField(intFormatter);
        age.setValue(42);
        JLabel ageStatus = new JLabel("value = " + age.getValue());
        age.addPropertyChangeListener("value", e ->
                ageStatus.setText("value = " + e.getNewValue()));
        rows.add(makeRow("Age:", age, ageStatus));

        rows.add(Box.createVerticalStrut(8));

        // Long row — NumberFormatter(Long) → LongStrategy →
        // SJFormattedLongField. Use a real Long-precision-needing value
        // to demonstrate why Long is distinct from Integer.
        NumberFormatter longFormatter = new NumberFormatter(NumberFormat.getIntegerInstance());
        longFormatter.setValueClass(Long.class);
        JFormattedTextField account = new JFormattedTextField(longFormatter);
        account.setValue(1_234_567_890_123L);
        JLabel accountStatus = new JLabel("value = " + account.getValue());
        account.addPropertyChangeListener("value", e ->
                accountStatus.setText("value = " + e.getNewValue()));
        rows.add(makeRow("Account #:", account, accountStatus));

        rows.add(Box.createVerticalStrut(8));

        // Double row — NumberFormatter (no value class) → NumberStrategy →
        // SJFormattedNumberField (Vaadin NumberField).
        NumberFormatter doubleFormatter = new NumberFormatter(NumberFormat.getNumberInstance());
        doubleFormatter.setValueClass(Double.class);
        JFormattedTextField salary = new JFormattedTextField(doubleFormatter);
        salary.setValue(75000.50);
        JLabel salaryStatus = new JLabel("value = " + salary.getValue());
        salary.addPropertyChangeListener("value", e ->
                salaryStatus.setText("value = " + e.getNewValue()));
        rows.add(makeRow("Salary:", salary, salaryStatus));

        rows.add(Box.createVerticalStrut(8));

        // Mask row — MaskFormatter → MaskStrategy → SJFormattedTextField
        // with browser pattern attribute. Phone number "(###) ###-####"
        // converts to a regex anchored ^...$.
        MaskFormatter phoneFormatter = makePhoneMask();
        JFormattedTextField phone = new JFormattedTextField(phoneFormatter);
        phone.setValue("(415) 555-0100");
        JLabel phoneStatus = new JLabel("value = " + phone.getValue());
        phone.addPropertyChangeListener("value", e ->
                phoneStatus.setText("value = " + e.getNewValue()));
        rows.add(makeRow("Phone:", phone, phoneStatus));

        add(rows, BorderLayout.NORTH);
    }

    private static MaskFormatter makePhoneMask() {
        try {
            return new MaskFormatter("(###) ###-####");
        } catch (ParseException e) {
            // The literal mask string above is well-formed — this catch
            // block exists only because MaskFormatter's ctor declares it.
            throw new AssertionError("phone mask should always parse", e);
        }
    }

    private static JPanel makeRow(String label, JFormattedTextField field, JLabel status) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(new JLabel(label));
        row.add(field);
        row.add(status);
        return row;
    }
}
