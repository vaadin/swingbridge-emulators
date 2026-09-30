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

import vaadinx.awt.FlowLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JToggleButton;

import javax.swing.AbstractAction;
import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;

/**
 * Button family demo. Surface:
 * JButton + JCheckBox + JToggleButton with action listeners, item
 * listeners, and an Action whose NAME PCE round-trips through the
 * driving button.
 *
 * <ul>
 *   <li>JButton "Click me" — ActionListener increments a counter
 *       label.</li>
 *   <li>JButton "Disable" driven by an Action — toggles the first
 *       button's enabled state and re-labels itself via Action.NAME PCE
 *       round-trip ("Disable" ↔ "Enable").</li>
 *   <li>JCheckBox "Notify on click" — ItemListener flips the readout.</li>
 *   <li>JToggleButton "Bold" — fires ItemEvent; mnemonic Alt+B routed
 *       through Vaadin Shortcuts.</li>
 * </ul>
 */
public class ButtonsPanel extends JPanel {

    public ButtonsPanel() {
        super(new FlowLayout(FlowLayout.LEADING, 8, 8));

        JLabel readout = new JLabel("Clicks: 0");
        int[] count = {0};

        JButton clickMe = new JButton("Click me");
        clickMe.addActionListener(e -> {
            count[0]++;
            readout.setText("Clicks: " + count[0]);
        });

        Action enableToggle = new AbstractAction("Disable") {
            @Override
            public void actionPerformed(ActionEvent e) {
                clickMe.setEnabled(!clickMe.isEnabled());
                putValue(Action.NAME, clickMe.isEnabled() ? "Disable" : "Enable");
            }
        };
        JButton enableButton = new JButton(enableToggle);

        JCheckBox notifyBox = new JCheckBox("Notify on click");
        notifyBox.addItemListener(e ->
                readout.setText("Notify: " + (e.getStateChange() == ItemEvent.SELECTED)));

        JToggleButton boldToggle = new JToggleButton("Bold");
        boldToggle.setMnemonic(KeyEvent.VK_B);
        boldToggle.addItemListener(e -> {
            String prefix = e.getStateChange() == ItemEvent.SELECTED ? "**" : "";
            readout.setText(prefix + "Clicks: " + count[0] + prefix);
        });

        add(clickMe);
        add(enableButton);
        add(notifyBox);
        add(boldToggle);
        add(readout);
    }
}
