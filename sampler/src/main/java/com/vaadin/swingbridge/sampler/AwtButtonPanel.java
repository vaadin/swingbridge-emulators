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
import vaadinx.awt.Button;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

/**
 * {@link vaadinx.awt.Button} — the AWT 1.0 push button, not {@link JButton}.
 * Decisions: {@code D_awt_button} / {@code SD_sbutton}; the lane's rationale and
 * status: {@code D_awt_lane}.
 *
 * <p>The scenario the AWT lane exists for is <b>AWT residue in an otherwise
 * Swing app</b>: a long-lived application ported forward from AWT 1.0 keeps a
 * {@code java.awt.Button} in a corner, and at stage 2 that is a hard compile
 * error with no workaround short of rewriting the view.
 *
 * <ol>
 *   <li><b>Click → ActionListener</b> — an AWT Button whose listener reads
 *       {@code getActionCommand()} into a readout label. Shows the AWT
 *       command-falls-back-to-label rule: the first button never sets a command
 *       and still reports its label.</li>
 *   <li><b>AWT and Swing side by side</b> — an AWT {@code Button} and a Swing
 *       {@code JButton} in one {@link JPanel}, both driving the same readout.
 *       The residue case: the AWT leaf adds, renders and clicks like any
 *       JComponent sibling even though there is no {@code JComponent} in its
 *       chain.</li>
 *   <li><b>Live setLabel / setEnabled</b> — the mutators a migrated app
 *       actually calls, driven from a Swing button so the AWT peer's
 *       server→browser push is visible end-to-end.</li>
 * </ol>
 */
public class AwtButtonPanel extends JPanel {

    public AwtButtonPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — click → ActionListener, actionCommand fallback vs explicit.
        JLabel readout = new JLabel("no action yet");
        Button plain = new Button("Plain AWT Button");
        plain.addActionListener(e ->
                readout.setText("actionCommand = " + e.getActionCommand()));
        Button commanded = new Button("With actionCommand");
        commanded.setActionCommand("explicit-command");
        commanded.addActionListener(e ->
                readout.setText("actionCommand = " + e.getActionCommand()));
        JPanel clicks = new JPanel(new BorderLayout(8, 4));
        JPanel clickRow = new JPanel();
        clickRow.add(plain);
        clickRow.add(commanded);
        clicks.add(clickRow, BorderLayout.NORTH);
        clicks.add(readout, BorderLayout.SOUTH);
        add(demoSection("Demo 1 — click fires ActionListener; command falls back to the label", clicks));
        add(Box.createVerticalStrut(8));

        // Demo 2 — AWT residue sitting in a Swing container.
        JLabel mixedReadout = new JLabel("nothing clicked");
        Button awtSibling = new Button("java.awt.Button");
        awtSibling.addActionListener(e -> mixedReadout.setText("clicked: AWT Button"));
        JButton swingSibling = new JButton("javax.swing.JButton");
        swingSibling.addActionListener(e -> mixedReadout.setText("clicked: Swing JButton"));
        JPanel mixedRow = new JPanel();
        mixedRow.add(awtSibling);
        mixedRow.add(swingSibling);
        JPanel mixed = new JPanel(new BorderLayout(8, 4));
        mixed.add(mixedRow, BorderLayout.NORTH);
        mixed.add(mixedReadout, BorderLayout.SOUTH);
        add(demoSection("Demo 2 — an AWT Button and a Swing JButton in one panel", mixed));
        add(Box.createVerticalStrut(8));

        // Demo 3 — live setLabel / setEnabled on the AWT Button.
        Button target = new Button("Rename me");
        JLabel stateReadout = new JLabel("label = Rename me, enabled = true");
        JButton rename = new JButton("Toggle label");
        rename.addActionListener(e -> {
            boolean renamed = "Renamed".equals(target.getLabel());
            target.setLabel(renamed ? "Rename me" : "Renamed");
            stateReadout.setText("label = " + target.getLabel()
                    + ", enabled = " + target.isEnabled());
        });
        JButton toggleEnabled = new JButton("Toggle enabled");
        toggleEnabled.addActionListener(e -> {
            target.setEnabled(!target.isEnabled());
            stateReadout.setText("label = " + target.getLabel()
                    + ", enabled = " + target.isEnabled());
        });
        JPanel controls = new JPanel();
        controls.add(rename);
        controls.add(toggleEnabled);
        JPanel mutate = new JPanel(new BorderLayout(0, 4));
        mutate.add(target, BorderLayout.NORTH);
        mutate.add(controls, BorderLayout.CENTER);
        mutate.add(stateReadout, BorderLayout.SOUTH);
        add(demoSection("Demo 3 — live setLabel / setEnabled", mutate));
    }

    private static JPanel demoSection(String labelText, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
