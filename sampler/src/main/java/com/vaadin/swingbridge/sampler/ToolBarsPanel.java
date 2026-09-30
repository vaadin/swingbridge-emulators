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
import vaadinx.swing.JToolBar;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingConstants;
import java.awt.Insets;
import java.awt.event.ActionEvent;

/**
 * {@link JToolBar} demo. Three sub-demos stacked
 * vertically; each exercises a different slice of the surface so the
 * exit-gate test can assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Horizontal toolbar</b> — three action buttons separated by
 *       {@link JToolBar#addSeparator} thin bars, then an
 *       {@link JToolBar#add(Action)} button wired via the JDK
 *       {@link Action} contract. Mirrors the JLawyer-shape top toolbar.
 *       Sets the toolbar-typical {@code setFloatable(false) +
 *       setRollover(true)} pair to validate the emulator-only field
 *       shadow path (D_jtoolbar §"Asymmetric R_swing_is_truth shadow").</li>
 *   <li><b>Vertical toolbar</b> — same row of action buttons but
 *       {@link SwingConstants#VERTICAL VERTICAL}, exercises the CSS
 *       {@code flex-direction: column} path and perpendicular
 *       (horizontal) separators.</li>
 *   <li><b>Toolbar with margin</b> — {@link JToolBar#setMargin} writes a
 *       CSS padding box around the action row; mirrors JLawyer's
 *       {@code setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4))}
 *       idiom but exercises the margin path instead so both surfaces
 *       are demoed.</li>
 * </ol>
 *
 * <p>A readout {@link JLabel} below each toolbar shows the most recent
 * button-click action command, so a tester can see clicks land
 * end-to-end through the surrogate.
 */
public class ToolBarsPanel extends JPanel {

    public ToolBarsPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — horizontal toolbar, JLawyer-shape.
        JLabel status1 = new JLabel("Status: (click a toolbar button)");
        JToolBar horizontal = new JToolBar();
        horizontal.setFloatable(false);
        horizontal.setRollover(true);
        horizontal.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        horizontal.add(navButton("New", status1));
        horizontal.add(navButton("Edit", status1));
        horizontal.add(navButton("Delete", status1));
        horizontal.addSeparator();
        horizontal.add(navButton("Copy ID", status1));
        horizontal.add(navButton("Upload", status1));
        horizontal.addSeparator();
        // add(Action) — JDK convenience that wraps the Action in a JButton.
        Action longOp = new AbstractAction("Long op") {
            @Override
            public void actionPerformed(ActionEvent e) {
                status1.setText("Status: clicked " + e.getActionCommand()
                        + " (via add(Action))");
            }
        };
        horizontal.add(longOp);
        add(demoSection("Demo 1 — horizontal toolbar (JLawyer-shape)",
                horizontal, status1));
        add(Box.createVerticalStrut(8));

        // Demo 2 — vertical toolbar exercising the orientation flip.
        JLabel status2 = new JLabel("Status: (click a vertical toolbar button)");
        JToolBar vertical = new JToolBar(SwingConstants.VERTICAL);
        vertical.setFloatable(false);
        vertical.add(navButton("Top", status2));
        vertical.add(navButton("Up", status2));
        vertical.addSeparator();
        vertical.add(navButton("Down", status2));
        vertical.add(navButton("Bottom", status2));
        // Wrap the vertical toolbar inside a horizontally-oriented row so
        // it doesn't visually consume the whole demo width.
        JPanel verticalRow = new JPanel(new BorderLayout(8, 0));
        verticalRow.add(vertical, BorderLayout.WEST);
        verticalRow.add(status2, BorderLayout.CENTER);
        add(demoSection("Demo 2 — vertical toolbar (orientation=VERTICAL)",
                verticalRow, null));
        add(Box.createVerticalStrut(8));

        // Demo 3 — setMargin → CSS padding round-trip.
        JLabel status3 = new JLabel("Status: (click a margin-toolbar button)");
        JToolBar margined = new JToolBar();
        margined.setFloatable(false);
        margined.setMargin(new Insets(4, 12, 4, 12));
        margined.add(navButton("Action A", status3));
        margined.add(navButton("Action B", status3));
        add(demoSection("Demo 3 — setMargin → CSS padding",
                margined, status3));
    }

    private static JButton navButton(String label, JLabel status) {
        JButton b = new JButton(label);
        b.addActionListener(e -> status.setText("Status: clicked " + label));
        return b;
    }

    private static JPanel demoSection(String labelText, JComponent body, JComponent footer) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        if (footer != null) wrap.add(footer, BorderLayout.SOUTH);
        return wrap;
    }
}
