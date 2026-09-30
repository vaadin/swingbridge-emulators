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
import vaadinx.swing.BoxLayout;
import vaadinx.swing.FocusManager;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;
import vaadinx.swing.text.JTextComponent;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

/**
 * Focus demo (D_focus_managers). Three things worth seeing:
 *
 * <ul>
 *   <li><b>Who has focus.</b> The readout is driven by
 *       {@link vaadinx.awt.event.FocusListener}s and reports what
 *       {@link FocusManager#getCurrentManager()} answers, so clicking or Tabbing
 *       between the fields updates it from the browser.</li>
 *   <li><b>Moving focus on.</b> The buttons transfer focus forward and backward
 *       from the current owner, and the browser's caret follows.</li>
 *   <li><b>The auto-advance idiom.</b> The three phone fields carry the
 *       inventory testapp's {@code NumberFormatDocument} shape verbatim — fill
 *       one and focus jumps to the next without touching Tab.</li>
 * </ul>
 */
public class FocusPanel extends JPanel {

    public FocusPanel() {
        super(new BorderLayout(8, 8));

        JLabel readout = new JLabel();

        JTextField areaCode = named(new JTextField(3), "areaCode");
        JTextField prefix = named(new JTextField(3), "prefix");
        JTextField line = named(new JTextField(4), "line");
        areaCode.setDocument(new AdvanceOnFull(3));
        prefix.setDocument(new AdvanceOnFull(3));
        line.setDocument(new AdvanceOnFull(4));

        JTextField note = named(new JTextField("a plain field, in the same traversal cycle", 30), "note");
        JTextField optedOut = named(new JTextField("setFocusable(false) — traversal skips me", 30), "optedOut");
        optedOut.setFocusable(false);

        for (JTextField field : new JTextField[] {areaCode, prefix, line, note, optedOut}) {
            field.addFocusListener(new vaadinx.awt.event.FocusListener() {
                @Override
                public void focusGained(vaadinx.awt.event.FocusEvent e) {
                    readout.setText(describeOwner());
                }

                @Override
                public void focusLost(vaadinx.awt.event.FocusEvent e) {
                    readout.setText(describeOwner());
                }
            });
        }
        readout.setText(describeOwner());

        JPanel phone = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        phone.add(new JLabel("Phone (auto-advances when each part is full):"));
        phone.add(areaCode);
        phone.add(prefix);
        phone.add(line);

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        fields.add(note);
        fields.add(optedOut);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(button("Focus the area code", areaCode::requestFocus));
        buttons.add(button("Transfer focus", () -> onOwner(owner -> owner.transferFocus())));
        buttons.add(button("Transfer focus backward", () -> onOwner(owner -> owner.transferFocusBackward())));
        buttons.add(button("Who has focus?", () -> readout.setText(describeOwner())));
        buttons.add(button("Clear focus",
                () -> FocusManager.getCurrentManager().clearFocusOwner()));

        JPanel status = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        status.add(new JLabel("Focus owner:"));
        status.add(readout);

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(status);
        stack.add(phone);
        stack.add(fields);
        stack.add(buttons);

        add(new JLabel("Focus & traversal"), BorderLayout.NORTH);
        add(stack, BorderLayout.CENTER);
    }

    /** Runs {@code action} on the focus owner, or says there isn't one. */
    private static void onOwner(java.util.function.Consumer<vaadinx.awt.Component> action) {
        vaadinx.awt.Component owner = FocusManager.getCurrentManager().getFocusOwner();
        if (owner != null) action.accept(owner);
    }

    /** What the readout shows: the owner's Swing name, plus the window it sits in. */
    private static String describeOwner() {
        FocusManager fm = FocusManager.getCurrentManager();
        vaadinx.awt.Component owner = fm.getFocusOwner();
        if (owner == null) return "nothing focused";
        String name = owner.getName() == null ? owner.getClass().getSimpleName() : owner.getName();
        vaadinx.awt.Window window = fm.getFocusedWindow();
        return name + (window == null ? "" : " (in " + window.getClass().getSimpleName() + ")");
    }

    private static JTextField named(JTextField field, String name) {
        field.setName(name);
        return field;
    }

    private static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * The inventory app's {@code NumberFormatDocument}, reduced to its focus
     * behaviour: digits only, and once full, move on. The focus-manager
     * round-trip is how a {@link PlainDocument} finds its own component at all —
     * it holds no reference to one.
     */
    private static final class AdvanceOnFull extends PlainDocument {

        private final int maxLength;

        AdvanceOnFull(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void insertString(int offs, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            String digits = str.replaceAll("\\D", "");
            int room = maxLength - getLength();
            if (room <= 0) return;
            super.insertString(offs, digits.length() > room ? digits.substring(0, room) : digits, a);
            if (getLength() < maxLength) return;
            FocusManager fm = FocusManager.getCurrentManager();
            vaadinx.awt.Component owner = fm.getFocusOwner();
            if (owner instanceof JTextComponent text && text.getDocument() == this) {
                owner.transferFocus();
            }
        }
    }
}
