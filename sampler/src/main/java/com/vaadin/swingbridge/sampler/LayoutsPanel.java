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
import vaadinx.awt.GridBagLayout;
import vaadinx.awt.GridLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.GroupLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSeparator;
import vaadinx.swing.JTextField;
import vaadinx.swing.LayoutStyle.ComponentPlacement;

import javax.swing.SwingConstants;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.Insets;

/**
 * Layout demo — exercises the built-in layouts ({@link BorderLayout},
 * {@link FlowLayout}, {@link BoxLayout}, {@link GridBagLayout},
 * {@link GridLayout}, {@link GroupLayout}) plus the canonical {@link Box}
 * helpers.
 *
 * <p>Seven sub-demos stacked under a {@code BoxLayout(Y_AXIS)} master panel:
 * <ol>
 *   <li><b>BorderLayout five compass regions</b> — the canonical Swing
 *       layout: NORTH/SOUTH bands span the width, WEST/EAST fill the
 *       height between them, CENTER absorbs the slack. Hosted at a fixed
 *       height so the {@code auto 1fr auto} row track actually
 *       distributes (D_layout_css_on_content).</li>
 *   <li><b>Vertical Box with struts</b> — three labelled rows with
 *       {@link Box#createVerticalStrut} between them. Mirrors the
 *       Swing-on-Vaadin CRUD-app preview-panel shape.</li>
 *   <li><b>Vertical glue pushing a button to the bottom</b> — the
 *       canonical "two pieces split by glue" pattern.</li>
 *   <li><b>Horizontal Box with rigid area + horizontal glue</b> — text
 *       field, fixed-size separator rigid area, button pushed to the
 *       right by glue.</li>
 *   <li><b>GridBagLayout form</b> — label + field per row, the canonical
 *       Swing form shape. Exercises the REMAINDER end-of-row idiom +
 *       weighted second column (label column auto-sized, field column
 *       absorbs slack).</li>
 *   <li><b>GridLayout keypad</b> — a 3×3 grid of equal cells (D_gridlayout), each
 *       button resized to fill its cell.</li>
 *   <li><b>GroupLayout form</b> — the NetBeans "Free Design" shape (D_grouplayout):
 *       baseline-aligned label+field rows, a full-width separator, and a
 *       right-aligned button, reconstructed into a CSS grid from the two
 *       axis group trees.</li>
 * </ol>
 */
public class LayoutsPanel extends JPanel {

    public LayoutsPanel() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel master = new JPanel();
        master.setLayout(new BoxLayout(master, BoxLayout.Y_AXIS));

        master.add(buildLabel("Demo 1 — BorderLayout: the five compass regions (NORTH/SOUTH/EAST/WEST/CENTER)"));
        master.add(buildBorderLayoutRegions());
        master.add(Box.createVerticalStrut(16));

        master.add(buildLabel("Demo 2 — Vertical BoxLayout with struts between rows"));
        master.add(buildVerticalWithStruts());
        master.add(Box.createVerticalStrut(16));

        master.add(buildLabel("Demo 3 — Vertical glue pushing a button to the bottom"));
        master.add(buildVerticalGlue());
        master.add(Box.createVerticalStrut(16));

        master.add(buildLabel("Demo 4 — Horizontal BoxLayout with rigid area + horizontal glue"));
        master.add(buildHorizontalGlue());
        master.add(Box.createVerticalStrut(16));

        master.add(buildLabel("Demo 5 — GridBagLayout form (REMAINDER row-end idiom + weighted field column)"));
        master.add(buildGridBagForm());
        master.add(Box.createVerticalStrut(16));

        master.add(buildLabel("Demo 6 — GridLayout keypad (equal cells, 3×3 with 4px gaps)"));
        master.add(buildGridLayoutKeypad());
        master.add(Box.createVerticalStrut(16));

        master.add(buildLabel("Demo 7 — GroupLayout form (NetBeans free-design shape: baseline rows, "
                + "a full-width separator, a right-aligned button)"));
        master.add(buildGroupLayoutForm());

        add(master, BorderLayout.CENTER);
    }

    private static JLabel buildLabel(String text) {
        JLabel l = new JLabel(text);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    private static JPanel buildBorderLayoutRegions() {
        // The canonical BorderLayout shape: NORTH/SOUTH span the full width as
        // bands, WEST/EAST fill the height between them, and CENTER absorbs all
        // remaining slack. 8px hgap/vgap opens gaps between the five regions.
        JPanel host = new JPanel(new BorderLayout(8, 8));
        host.setBorder(BorderFactory.createTitledBorder("BorderLayout(8, 8) — five regions"));

        host.add(regionCell("NORTH", new Color(0xE3, 0xF2, 0xFD)), BorderLayout.NORTH);
        host.add(regionCell("WEST", new Color(0xF1, 0xF8, 0xE9)), BorderLayout.WEST);
        host.add(regionCell("CENTER (absorbs the slack)", new Color(0xFF, 0xF8, 0xE1)), BorderLayout.CENTER);
        host.add(regionCell("EAST", new Color(0xF1, 0xF8, 0xE9)), BorderLayout.EAST);
        host.add(regionCell("SOUTH", new Color(0xE3, 0xF2, 0xFD)), BorderLayout.SOUTH);

        // Bounded height so the grid's `auto 1fr auto` row track distributes:
        // NORTH pins to the top, SOUTH to the bottom, CENTER stretches between.
        // Without it the 1fr center row collapses to content height and the
        // regions read as a plain vertical stack.
        host.peerContentElement().getStyle().set("height", "200px");
        return host;
    }

    private static JLabel regionCell(String text, Color background) {
        JLabel cell = new JLabel(text, SwingConstants.CENTER);
        cell.setOpaque(true);
        cell.setBackground(background);
        cell.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return cell;
    }

    private static JPanel buildVerticalWithStruts() {
        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createTitledBorder("Vertical struts (4px each)"));

        rows.add(row("Name", "Ada"));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Role", "Admin"));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Level", "10"));
        return rows;
    }

    private static JPanel buildVerticalGlue() {
        // Fixed-size container so the glue has room to actually push.
        JPanel host = new JPanel(new BorderLayout());
        host.setBorder(BorderFactory.createTitledBorder("Vertical glue (160px tall)"));

        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.add(new JLabel("Top of the column"));
        inner.add(Box.createVerticalGlue());
        inner.add(new JButton("Bottom button (pushed by glue)"));

        // Inline-CSS height so the glue has somewhere to expand into.
        inner.peerContentElement().getStyle().set("height", "160px");
        host.add(inner, BorderLayout.CENTER);
        return host;
    }

    private static JPanel buildHorizontalGlue() {
        JPanel host = new JPanel();
        host.setLayout(new BoxLayout(host, BoxLayout.X_AXIS));
        host.setBorder(BorderFactory.createTitledBorder("Horizontal glue + 16px rigid spacer"));

        host.add(new JLabel("Search:"));
        host.add(Box.createRigidArea(new java.awt.Dimension(8, 0)));
        host.add(new JTextField(16));
        host.add(Box.createHorizontalGlue());
        host.add(new JButton("Go"));
        return host;
    }

    private static JPanel row(String key, String value) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        p.add(new JLabel(key + ":"));
        p.add(new JLabel(value));
        return p;
    }

    private static JPanel buildGridBagForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("label + field per row"));

        // Reused constraint object — clone-on-add per JDK contract means
        // post-add mutation doesn't bleed back into stored state. Same
        // pattern real-world Swing GridBag forms use.
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 3, 3, 3);
        g.anchor = GridBagConstraints.WEST;

        addFormRow(form, g, 0, "Name", new JTextField(20));
        addFormRow(form, g, 1, "Role", new JTextField(20));
        addFormRow(form, g, 2, "Level", new JTextField(20));
        return form;
    }

    private static void addFormRow(JPanel form, GridBagConstraints g, int row, String label, vaadinx.awt.Component field) {
        // Label column: zero weightx, fill NONE — sizes to its intrinsic width.
        g.gridx = 0; g.gridy = row;
        g.weightx = 0; g.fill = GridBagConstraints.NONE;
        form.add(new JLabel(label), g);
        // Field column: weightx=1, fill HORIZONTAL — absorbs slack.
        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, g);
    }

    private static JPanel buildGridLayoutKeypad() {
        // GridLayout divides the container into equal cells and resizes every
        // child to fill its cell. rows fixed at 3; columns derived from the 9
        // children → 3×3. Each button stretches to an identical cell (D_gridlayout).
        JPanel keypad = new JPanel(new GridLayout(3, 3, 4, 4));
        keypad.setBorder(BorderFactory.createTitledBorder("GridLayout(3, 3, 4, 4) — 9 equal cells"));
        for (int i = 1; i <= 9; i++) {
            keypad.add(new JButton(Integer.toString(i)));
        }
        return keypad;
    }

    private static JPanel buildGroupLayoutForm() {
        // The canonical NetBeans "Free Design" shape GroupLayout produces: two
        // label+field rows baseline-aligned, a separator spanning the full
        // width, and a trailing button. Exercises the two-axis group trees the
        // emulator reconstructs into a CSS grid (D_grouplayout): sequential (rows) over
        // parallel (columns / baseline rows), a direct parallel child that
        // spans its band (the separator), and a per-component TRAILING override
        // (the button).
        JPanel form = new JPanel();
        GroupLayout gl = new GroupLayout(form);
        form.setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);
        form.setBorder(BorderFactory.createTitledBorder("GroupLayout — baseline rows + spanning separator"));

        JLabel nameLabel = new JLabel("Name:");
        JTextField nameField = new JTextField(16);
        JLabel emailLabel = new JLabel("Email:");
        JTextField emailField = new JTextField(16);
        JSeparator separator = new JSeparator();
        JButton saveButton = new JButton("Save");

        gl.setHorizontalGroup(
            gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(separator)
                .addGroup(gl.createSequentialGroup()
                    .addGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(nameLabel)
                        .addComponent(emailLabel))
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(nameField)
                        .addComponent(emailField)))
                // Right-align the button across the full form width (its column
                // band spans both columns; TRAILING → justify-self: end).
                .addComponent(saveButton, GroupLayout.Alignment.TRAILING)
        );
        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(nameField))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(emailLabel)
                    .addComponent(emailField))
                .addPreferredGap(ComponentPlacement.UNRELATED)
                .addComponent(separator)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(saveButton)
        );
        return form;
    }
}
