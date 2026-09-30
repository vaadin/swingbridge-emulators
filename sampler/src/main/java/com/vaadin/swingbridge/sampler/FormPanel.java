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
import vaadinx.awt.Container;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.Window;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.InputVerifier;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JComponent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPasswordField;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;
import vaadinx.swing.event.AncestorEvent;
import vaadinx.swing.event.AncestorListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;

/**
 * Self-contained registration form demo with non-empty / minimum-length
 * validation, gated Save action driven by an {@link AbstractAction}, and
 * Enter-to-submit wiring via the host frame's {@link JFrame#getRootPane()}
 * default button.
 *
 * <p>Default-button install/uninstall is gated on {@link AncestorListener}:
 * {@code ancestorAdded} walks {@link #getTopLevelAncestor()} to the host
 * frame and binds Save as the root pane's default; {@code ancestorRemoved}
 * clears it. Without the un-bind, switching demos in
 * {@link SamplerFrame#showDemo} would leave the previous default-button
 * binding live — Enter on Home would still trigger Form's Save.
 *
 * <p>This is the canonical "setup-on-show / teardown-on-hide" shape other
 * demos reuse for Timer / SwingWorker teardown.
 */
public class FormPanel extends JPanel {

    public FormPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createTitledBorder("Registration"));

        JTextField nameField = new JTextField(24);
        nameField.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                return !((JTextField) input).getText().trim().isEmpty();
            }
        });

        // No JCheckBox / setEchoChar(0) "show password" toggle: the visual
        // mask state is governed client-side by Vaadin PasswordField's reveal
        // button (eye icon), and there's no server-side API to flip it.
        // setEchoChar reaches nothing observable at all — echoChar is a
        // rendering input the browser never consults. See
        // SJPasswordField.setEchoChar.
        JPasswordField passwordField = new JPasswordField(24);
        JTextArea notes = new JTextArea(4, 24);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);

        JCheckBox acceptTerms = new JCheckBox("I accept the terms");
        JLabel status = new JLabel("Fill in the form and accept the terms to continue.");

        Action saveAction = new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) {
                String name = nameField.getText().trim();
                int pwLen = passwordField.getPassword().length;
                if (name.isEmpty()) {
                    status.setText("Validation: name is required.");
                    nameField.requestFocusInWindow();
                    return;
                }
                if (pwLen < 6) {
                    status.setText("Validation: password must be at least 6 characters.");
                    passwordField.requestFocusInWindow();
                    return;
                }
                String note = notes.getText().trim();
                StringBuilder msg = new StringBuilder("Saved. Hello, ").append(name)
                        .append("! (password length ").append(pwLen).append(")");
                if (!note.isEmpty()) msg.append(" — note: ").append(note);
                status.setText(msg.toString());
            }
        };
        saveAction.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_S);
        saveAction.setEnabled(false);
        JButton save = new JButton(saveAction);

        acceptTerms.addItemListener(e ->
                saveAction.setEnabled(e.getStateChange() == ItemEvent.SELECTED));

        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        fields.add(new JLabel("Name:"));
        fields.add(nameField);
        fields.add(new JLabel("Password:"));
        fields.add(passwordField);
        fields.add(new JLabel("Notes:"));
        fields.add(notes);
        add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        footer.add(acceptTerms);
        footer.add(save);
        add(footer, BorderLayout.SOUTH);
        add(status, BorderLayout.NORTH);

        // Default-button install/uninstall via AncestorListener so the
        // binding doesn't leak across SamplerFrame demo swaps. JDK
        // contract: ancestorAdded fires when this component becomes part
        // of a Window-rooted hierarchy; we walk getTopLevelAncestor() to
        // the JFrame and bind getRootPane().setDefaultButton(save).
        // ancestorRemoved fires before the parent field is cleared (see
        // JComponent.removeNotify), so the same walk still finds the
        // leaving frame.
        addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent e) {
                JFrame f = enclosingJFrame();
                if (f != null) f.getRootPane().setDefaultButton(save);
            }

            @Override
            public void ancestorRemoved(AncestorEvent e) {
                JFrame f = enclosingJFrame();
                if (f != null) f.getRootPane().setDefaultButton(null);
            }

            @Override
            public void ancestorMoved(AncestorEvent e) {
                // Not fired by our wiring (Vaadin has no server-side
                // layout-position event). No-op.
            }
        });
    }

    /**
     * Walks {@link JComponent#getTopLevelAncestor()} (which returns the
     * first {@link Window} ancestor) and narrows to {@link JFrame} —
     * SamplerFrame is a JFrame, so we'll always find one in normal use,
     * but the cast is guarded so a future host swap doesn't NPE.
     */
    private JFrame enclosingJFrame() {
        Container top = getTopLevelAncestor();
        return (top instanceof JFrame f) ? f : null;
    }
}
