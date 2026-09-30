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
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSeparator;

import javax.swing.SwingConstants;

/**
 * Standalone {@link JSeparator} demo (D_jseparator / SD_sjseparator). Three sub-demos, each
 * exercising a different slice so the exit-gate test can assert the
 * user path emits no stub WARNs:
 *
 * <ol>
 *   <li><b>Horizontal dividers between form rows</b> — a
 *       {@link BoxLayout#Y_AXIS} column with {@code new JSeparator()}
 *       hairlines between label rows; each fills the column width via
 *       {@code align-self:stretch}. The everyday standalone use.</li>
 *   <li><b>Vertical divider between side-by-side regions</b> —
 *       {@code new JSeparator(VERTICAL)} between two labels in a
 *       {@link BorderLayout} row, filling the row height.</li>
 *   <li><b>Live orientation flip</b> — a button toggles
 *       {@link JSeparator#setOrientation} on a separator held in a sized
 *       cell; a readout {@link JLabel} shows the current
 *       {@link JSeparator#getOrientation} so a tester sees the flip land
 *       end-to-end.</li>
 * </ol>
 */
public class SeparatorsPanel extends JPanel {

    public SeparatorsPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — horizontal separators between form rows.
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(new JLabel("General"));
        rows.add(new JSeparator());
        rows.add(new JLabel("Address"));
        rows.add(new JSeparator());
        rows.add(new JLabel("Notes"));
        add(demoSection("Demo 1 — horizontal dividers between form rows", rows));
        add(Box.createVerticalStrut(8));

        // Demo 2 — vertical separator between two side-by-side regions.
        JPanel cols = new JPanel(new BorderLayout(8, 0));
        cols.add(new JLabel("Left region"), BorderLayout.WEST);
        cols.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.CENTER);
        cols.add(new JLabel("Right region"), BorderLayout.EAST);
        add(demoSection("Demo 2 — vertical divider between side-by-side regions", cols));
        add(Box.createVerticalStrut(8));

        // Demo 3 — live orientation flip.
        JSeparator flip = new JSeparator();
        JLabel readout = new JLabel("orientation = HORIZONTAL");
        JButton toggle = new JButton("Flip orientation");
        toggle.addActionListener(e -> {
            boolean nowH = flip.getOrientation() == SwingConstants.HORIZONTAL;
            flip.setOrientation(nowH ? SwingConstants.VERTICAL : SwingConstants.HORIZONTAL);
            readout.setText("orientation = " + (nowH ? "VERTICAL" : "HORIZONTAL"));
        });
        JPanel flipCell = new JPanel(new BorderLayout(0, 4));
        flipCell.add(toggle, BorderLayout.NORTH);
        flipCell.add(flip, BorderLayout.CENTER);
        flipCell.add(readout, BorderLayout.SOUTH);
        add(demoSection("Demo 3 — live setOrientation flip", flipCell));
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
