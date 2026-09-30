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
import vaadinx.awt.Component;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTabbedPane;
import vaadinx.swing.JTextArea;

/**
 * {@link JTabbedPane} demo. Two sub-demos stacked
 * vertically; each exercises a different slice of the surface so the
 * exit-gate test can assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Plain tabs</b> — three tabs with title + content. Selection
 *       cycle via the user clicking the headers. Mirrors the
 *       JLawyer-shape "case detail" tab strip.</li>
 *   <li><b>Multi-case-open shape</b> — tabs with custom header components
 *       (title + close X) via {@link JTabbedPane#setTabComponentAt}. Each
 *       close button calls {@code tabs.remove(tab)} on click — the
 *       canonical "multiple case files open simultaneously" UX in
 *       JLawyer and similar case-management apps.</li>
 * </ol>
 */
public class TabbedPanesPanel extends JPanel {

    public TabbedPanesPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(demoSection("Demo 1 — plain tabs (case detail shape)", buildPlainTabs()));
        add(Box.createVerticalStrut(12));

        add(demoSection("Demo 2 — multi-case-open with close buttons (JLawyer shape)",
                buildClosableTabs()));
    }

    private JTabbedPane buildPlainTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", panelWith("Case overview content"));
        tabs.addTab("Parties", panelWith("Plaintiff + defendant listing"));
        tabs.addTab("Documents", panelWith("Documents attached to the case"));
        tabs.setSelectedIndex(0);
        return tabs;
    }

    private JTabbedPane buildClosableTabs() {
        JTabbedPane tabs = new JTabbedPane();
        addClosableTab(tabs, "Case 2024-A-001", "Address dispute — initial filing");
        addClosableTab(tabs, "Case 2024-B-042", "Contract review — draft v3");
        addClosableTab(tabs, "Case 2024-C-117", "Employment grievance");
        tabs.setSelectedIndex(0);
        return tabs;
    }

    /**
     * Build a custom header (title + small "x" button) and install it on the
     * given tabbed pane. The close button removes the tab on click — the
     * canonical multi-case-open close-tab UX.
     */
    private void addClosableTab(JTabbedPane tabs, String title, String body) {
        tabs.addTab(title, panelWith(body));
        int index = tabs.getTabCount() - 1;
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.add(new JLabel(title));
        JButton close = new JButton("x");
        // Emulator-stage styling: the default Vaadin button peer renders the
        // single "x" as a full-size button, which dwarfs the tab title. Reach
        // through to the Vaadin peer (getPeer() is public for exactly this
        // kind of styling/UX iteration) and apply the tertiary-inline variant
        // (ButtonVariant.LUMO_TERTIARY_INLINE → theme="tertiary-inline"), which
        // strips the surrounding whitespace/chrome so it sits inline next to
        // the label like a real close affordance. No Swing API expresses this,
        // so the peer is the seam.
        ((com.vaadin.flow.component.button.Button) close.getPeer())
                .addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_TERTIARY_INLINE);
        close.addActionListener(e -> {
            int idx = tabs.indexOfTabComponent(header);
            if (idx != -1) tabs.removeTabAt(idx);
        });
        header.add(close);
        tabs.setTabComponentAt(index, header);
    }

    private static JPanel panelWith(String text) {
        JPanel p = new JPanel(new BorderLayout());
        JTextArea ta = new JTextArea(text);
        ta.setEditable(false);
        ta.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(ta, BorderLayout.CENTER);
        return p;
    }

    private static JPanel demoSection(String labelText, Component body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
