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
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JColorChooser;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.Color;

/**
 * {@link JColorChooser} demo. Two sub-demos stacked
 * vertically; each exercises a different slice so the exit-gate test can
 * assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Embedded chooser</b> — a {@link JColorChooser} added inline (the
 *       browser {@code <input type="color">} swatch). A ChangeListener on its
 *       selection model updates a readout label live, so the ColorSelectionModel
 *       source-of-truth + peer→model R_swing_is_truth/R_callswing_envelope sync is visible.</li>
 *   <li><b>Blocking {@code showDialog}</b> — a button whose ActionListener calls
 *       {@link JColorChooser#showDialog}, which parks the click VT on the
 *       internal modal JDialog until OK / Cancel. Mirrors the canonical Swing
 *       "pick a color" idiom and exercises the UI-fiber park path (Sampler's
 *       AppShell has {@code @Push}).</li>
 * </ol>
 */
public class ColorChoosersPanel extends JPanel {

    private Color current = Color.RED;

    public ColorChoosersPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — embedded chooser with a live readout.
        JColorChooser chooser = new JColorChooser(Color.RED);
        JLabel embeddedReadout = new JLabel(describe(chooser.getColor()));
        chooser.getSelectionModel().addChangeListener(
                e -> embeddedReadout.setText(describe(chooser.getColor())));
        JPanel embedded = new JPanel(new BorderLayout(0, 4));
        embedded.add(chooser, BorderLayout.CENTER);
        embedded.add(embeddedReadout, BorderLayout.SOUTH);
        add(demoSection("Demo 1 — embedded chooser (live readout)", embedded));
        add(Box.createVerticalStrut(8));

        // Demo 2 — blocking showDialog.
        JLabel dialogReadout = new JLabel("No color picked yet.");
        JButton pick = new JButton("Pick a color…");
        pick.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choose a color", current);
            if (chosen != null) {
                current = chosen;
                dialogReadout.setText("Picked " + describe(chosen));
            } else {
                dialogReadout.setText("Pick cancelled.");
            }
        });
        JPanel dialogRow = new JPanel(new BorderLayout(8, 0));
        dialogRow.add(pick, BorderLayout.WEST);
        dialogRow.add(dialogReadout, BorderLayout.CENTER);
        add(demoSection("Demo 2 — blocking JColorChooser.showDialog", dialogRow));
    }

    private static String describe(Color c) {
        return String.format("#%02x%02x%02x (r=%d, g=%d, b=%d)",
                c.getRed(), c.getGreen(), c.getBlue(), c.getRed(), c.getGreen(), c.getBlue());
    }

    private static JPanel demoSection(String labelText, vaadinx.swing.JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
