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
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSplitPane;
import vaadinx.swing.JTextArea;

/**
 * {@link JSplitPane} demo. Three sub-demos stacked
 * vertically; each exercises a different slice of the surface so the
 * exit-gate test can assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Horizontal split</b> — {@link JSplitPane#HORIZONTAL_SPLIT}
 *       with two text-area panes. Mirrors the JLawyer-shape outer split
 *       (tree / tabs).</li>
 *   <li><b>Vertical split</b> — {@link JSplitPane#VERTICAL_SPLIT} with
 *       two text-area panes. Mirrors the JLawyer-shape inner split
 *       (docs list / notes).</li>
 *   <li><b>Nested split</b> — outer horizontal containing inner vertical
 *       on the right side. Mirrors the JLawyer-shape full layout shape:
 *       left tree, right tabs-with-stacked-panes.</li>
 * </ol>
 */
public class SplitPanesPanel extends JPanel {

    public SplitPanesPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — horizontal split.
        JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                pane("Left pane\n(HORIZONTAL_SPLIT)"),
                pane("Right pane\n(side-by-side)"));
        horizontal.setDividerLocation(0.4);  // proportional form — real impl
        add(demoSection("Demo 1 — HORIZONTAL_SPLIT (side-by-side)", horizontal));
        add(Box.createVerticalStrut(12));

        // Demo 2 — vertical split.
        JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                pane("Top pane\n(VERTICAL_SPLIT)"),
                pane("Bottom pane\n(stacked)"));
        vertical.setDividerLocation(0.5);
        add(demoSection("Demo 2 — VERTICAL_SPLIT (stacked)", vertical));
        add(Box.createVerticalStrut(12));

        // Demo 3 — nested. Outer horizontal, right side is an inner vertical.
        JSplitPane innerVertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                pane("Inner top\n(notes)"),
                pane("Inner bottom\n(documents)"));
        innerVertical.setDividerLocation(0.5);
        JSplitPane outer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                pane("Outer left\n(case tree)"),
                innerVertical);
        outer.setDividerLocation(0.3);
        add(demoSection("Demo 3 — Nested (JLawyer shape: outer H, inner V)", outer));
    }

    private static JTextArea pane(String text) {
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        ta.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        return ta;
    }

    private static JPanel demoSection(String labelText, JSplitPane body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
