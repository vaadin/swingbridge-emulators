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
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/**
 * Caret + selection demo (SD_caret_selection). Three things worth seeing move:
 *
 * <ul>
 *   <li><b>Server → browser.</b> The buttons call {@code select} /
 *       {@code setCaretPosition} / {@code replaceSelection}, and the browser's
 *       own caret and selection follow.</li>
 *   <li><b>Browser → server.</b> The readout is driven by a
 *       {@link javax.swing.event.CaretListener}, so clicking or drag-selecting
 *       in the field updates it — that is the {@code emul-selection} bridge
 *       reporting a caret the *user* moved.</li>
 *   <li><b>The auto-advance idiom.</b> Two fixed-width fields whose
 *       {@link PlainDocument} filters jump focus once full, which is the shape
 *       the inventory testapp's {@code NumberFormatDocument} has and the
 *       reason this mechanism was built. The focus half of that idiom — asking
 *       the focus manager who owns focus, then transferring — has its own
 *       {@code Focus} route (D_focus_managers); this one moves the caret with the focus.</li>
 * </ul>
 */
public class CaretPanel extends JPanel {

    public CaretPanel() {
        super(new BorderLayout(8, 8));

        JTextField field = new JTextField("The quick brown fox", 30);
        JLabel readout = new JLabel();
        field.addCaretListener(e -> readout.setText(describe(e.getDot(), e.getMark())));
        readout.setText(describe(field.getCaretPosition(), field.getCaretPosition()));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        row.add(new JLabel("Text:"));
        row.add(field);
        row.add(readout);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(button("Select \"quick\"", () -> field.select(4, 9)));
        buttons.add(button("Caret to start", () -> field.setCaretPosition(0)));
        buttons.add(button("Caret to end", () -> field.setCaretPosition(field.getText().length())));
        buttons.add(button("Select all", field::selectAll));
        buttons.add(button("Replace selection with \"slow\"", () -> field.replaceSelection("slow")));

        JPanel advance = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        JTextField areaCode = new JTextField(3);
        JTextField number = new JTextField(7);
        areaCode.setDocument(new AdvanceOnFull(3, number));
        number.setDocument(new AdvanceOnFull(7, null));
        advance.add(new JLabel("Phone (auto-advance at 3 digits):"));
        advance.add(areaCode);
        advance.add(number);

        JTextArea notes = new JTextArea("Selection works the same in a text area.\nTry it.", 4, 40);
        JPanel areaRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        areaRow.add(notes);
        areaRow.add(button("Select line 2", () -> notes.select(
                notes.getText().indexOf('\n') + 1, notes.getText().length())));

        JPanel stack = new JPanel();
        stack.setLayout(new vaadinx.swing.BoxLayout(stack, vaadinx.swing.BoxLayout.Y_AXIS));
        stack.add(row);
        stack.add(buttons);
        stack.add(advance);
        stack.add(areaRow);

        add(new JLabel("Caret & selection"), BorderLayout.NORTH);
        add(stack, BorderLayout.CENTER);
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private static String describe(int dot, int mark) {
        return dot == mark
                ? "caret at " + dot + ", nothing selected"
                : "caret at " + dot + ", selection " + Math.min(dot, mark) + ".." + Math.max(dot, mark);
    }

    /**
     * The inventory app's {@code NumberFormatDocument} shape: digits only, and
     * once the field is full, move on.
     */
    private static final class AdvanceOnFull extends PlainDocument {

        private final int maxLength;
        private final JTextField next;

        AdvanceOnFull(int maxLength, JTextField next) {
            this.maxLength = maxLength;
            this.next = next;
        }

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            String digits = str.replaceAll("\\D", "");
            int room = maxLength - getLength();
            if (room <= 0) return;
            super.insertString(offs, digits.length() > room ? digits.substring(0, room) : digits, a);
            if (getLength() == maxLength && next != null) {
                next.requestFocus();
                next.setCaretPosition(next.getText().length());
            }
        }
    }
}
