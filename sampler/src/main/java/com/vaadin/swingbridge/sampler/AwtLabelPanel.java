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
import vaadinx.awt.GridLayout;
import vaadinx.awt.Label;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.Color;

/**
 * {@link vaadinx.awt.Label} — AWT 1.0 static text, not {@link JLabel}.
 * Decisions: {@code D_awt_label} / {@code SD_slabel}; the lane's rationale:
 * {@code ideas/awt-widgets.md}.
 *
 * <ol>
 *   <li><b>Alignment</b> — three labels pinned to LEFT / CENTER / RIGHT with a
 *       tinted background so the box they align within is visible, plus one
 *       label whose text and alignment are driven live. The alignment constants
 *       here are AWT's own (0/1/2), which collide numerically with
 *       {@code SwingConstants}'.</li>
 * </ol>
 */
public class AwtLabelPanel extends JPanel {

    public AwtLabelPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Backgrounds are set because an AWT Label has no setBorder (that's
        // JComponent's); without them the label fills its region invisibly
        // and alignment looks like it does nothing.
        JPanel aligned = new JPanel(new GridLayout(3, 1, 0, 2));
        aligned.add(tinted(new Label("LEFT (AWT alignment 0)", Label.LEFT)));
        aligned.add(tinted(new Label("CENTER (AWT alignment 1)", Label.CENTER)));
        aligned.add(tinted(new Label("RIGHT (AWT alignment 2)", Label.RIGHT)));

        Label live = tinted(new Label("aligned LEFT", Label.LEFT));
        JButton cycle = new JButton("Cycle alignment");
        cycle.addActionListener(e -> {
            int next = (live.getAlignment() + 1) % 3;
            live.setAlignment(next);
            live.setText("aligned " + switch (next) {
                case Label.CENTER -> "CENTER";
                case Label.RIGHT -> "RIGHT";
                default -> "LEFT";
            });
        });
        JPanel liveRow = new JPanel();
        liveRow.add(cycle);
        JPanel labels = new JPanel(new BorderLayout(0, 6));
        labels.add(aligned, BorderLayout.NORTH);
        labels.add(live, BorderLayout.CENTER);
        labels.add(liveRow, BorderLayout.SOUTH);
        add(demoSection("Demo 1 — java.awt.Label: the three alignments, live setText / setAlignment", labels));
    }

    /** Tint the label's background so the region it aligns its text within is visible. */
    private static Label tinted(Label label) {
        label.setBackground(new Color(0xEE, 0xEE, 0xF5));
        return label;
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
