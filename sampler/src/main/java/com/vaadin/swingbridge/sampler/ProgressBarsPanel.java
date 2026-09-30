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
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JProgressBar;

/**
 * {@link JProgressBar} demo. Two sub-demos stacked
 * vertically; each exercises a different slice of the surface so the
 * exit-gate test can assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Determinate</b> — a 0–100 bar driven by Step/Reset buttons.
 *       Sub-demos the model write-through path plus
 *       {@code stringPainted + getString()} percent-default rendering
 *       via the {@code data-emul-progress-string} CSS overlay.</li>
 *   <li><b>Indeterminate</b> — a bar with {@code setIndeterminate(true)}
 *       and a custom {@code setString("Working…")}. Mirrors the
 *       jlawyer-shape testbed's status-bar idiom.</li>
 * </ol>
 *
 * <p>VERTICAL orientation is intentionally not demoed: {@code setOrientation(VERTICAL)}
 * is R_vaadin_first drop-and-WARN per the SJProgressBar javadoc — the visual stays
 * horizontal regardless. A CSS-rotate approach doesn't work: rotated
 * layout boxes collapse in any non-fixed-size container
 * (BorderLayout.WEST, BoxLayout, …). Revisit when a real migration
 * target needs it.
 */
public class ProgressBarsPanel extends JPanel {

    public ProgressBarsPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — determinate with stringPainted (percent default).
        JProgressBar determinate = new JProgressBar(0, 100);
        determinate.setStringPainted(true);
        determinate.setValue(33);
        JPanel determinateRow = new JPanel(new BorderLayout(8, 0));
        determinateRow.add(determinate, BorderLayout.CENTER);
        JButton step = new JButton("Step +10");
        step.addActionListener(e -> {
            int v = determinate.getValue();
            determinate.setValue(Math.min(determinate.getMaximum(), v + 10));
        });
        JButton reset = new JButton("Reset");
        reset.addActionListener(e -> determinate.setValue(0));
        JPanel determinateButtons = new JPanel();
        determinateButtons.setLayout(new BoxLayout(determinateButtons, BoxLayout.X_AXIS));
        determinateButtons.add(step);
        determinateButtons.add(Box.createHorizontalStrut(4));
        determinateButtons.add(reset);
        determinateRow.add(determinateButtons, BorderLayout.EAST);
        add(demoSection("Demo 1 — determinate (stringPainted; percent default)", determinateRow));
        add(Box.createVerticalStrut(8));

        // Demo 2 — indeterminate with custom string ("Working…"). Mirrors
        // the jlawyer-shape testbed's status-bar pattern.
        JProgressBar indeterminate = new JProgressBar();
        indeterminate.setIndeterminate(true);
        indeterminate.setStringPainted(true);
        indeterminate.setString("Working…");
        add(demoSection("Demo 2 — indeterminate (setString \"Working…\")", indeterminate));
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
