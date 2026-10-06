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
import vaadinx.awt.Choice;
import vaadinx.awt.GridLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.event.ItemEvent;
import java.util.Arrays;

/**
 * {@link vaadinx.awt.Choice} — the AWT 1.0 dropdown, not
 * {@link vaadinx.swing.JComboBox}. Decisions: {@code D_awt_choice} / {@code SD_schoice}; the
 * lane's rationale: {@code D_awt_lane}.
 *
 * <ol>
 *   <li><b>{@code java.awt.Choice} vs {@code JComboBox}</b> — the four
 *       divergences a re-skin of {@code SJComboBox} would have papered over,
 *       each with a live readout: {@code select()} fires nothing, a browser pick
 *       fires one SELECTED and no DESELECTED partner, inserting at or before the
 *       selection re-selects index 0, and {@code getSelectedObjects()} reads
 *       back {@code null} rather than {@code []} on an empty Choice. The item
 *       list carries a deliberate duplicate, which is the case index identity
 *       exists to handle.</li>
 * </ol>
 */
public class AwtChoicePanel extends JPanel {

    public AwtChoicePanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // The teaching content is the divergence set, not the widget: every
        // readout below reads differently than the same code on a JComboBox
        // would.
        Choice units = new Choice();
        units.add("metric");
        units.add("imperial");
        // Deliberate duplicate: two equal Strings would collapse to one key
        // under Vaadin's KeyMapper, which is why the peer's items are the
        // indices rather than the Strings. Both entries stay selectable.
        units.add("imperial");
        units.add("nautical");

        JLabel eventReadout = new JLabel("no ItemEvent yet — pick from the dropdown");
        JLabel choiceReadout = new JLabel(choiceState(units));
        units.addItemListener(e -> {
            eventReadout.setText("ItemEvent: stateChange="
                    + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED")
                    + ", getItem()=" + e.getItem()
                    + " — and no partner event");
            choiceReadout.setText(choiceState(units));
        });

        JButton selectSilently = new JButton("select(2) — fires nothing");
        selectSilently.addActionListener(e -> {
            units.select(2);
            choiceReadout.setText(choiceState(units));
        });
        JButton insertHead = new JButton("insert(\"New\", 0)");
        insertHead.addActionListener(e -> {
            units.insert("New", 0);
            choiceReadout.setText(choiceState(units));
        });
        JButton removeHead = new JButton("remove(0)");
        removeHead.addActionListener(e -> {
            // AWT throws on an out-of-range position, so a real app guards
            // exactly like this rather than relying on a silent no-op.
            if (units.getItemCount() == 0) {
                choiceReadout.setText("empty Choice — nothing to remove");
                return;
            }
            units.remove(0);
            choiceReadout.setText(choiceState(units));
        });
        JButton clearAll = new JButton("removeAll()");
        clearAll.addActionListener(e -> {
            units.removeAll();
            choiceReadout.setText(choiceState(units));
        });

        JPanel choiceControls = new JPanel();
        choiceControls.add(selectSilently);
        choiceControls.add(insertHead);
        choiceControls.add(removeHead);
        choiceControls.add(clearAll);
        JPanel choiceReadouts = new JPanel(new GridLayout(2, 1, 0, 2));
        choiceReadouts.add(eventReadout);
        choiceReadouts.add(choiceReadout);
        JPanel choicePanel = new JPanel(new BorderLayout(0, 6));
        choicePanel.add(units, BorderLayout.NORTH);
        choicePanel.add(choiceControls, BorderLayout.CENTER);
        choicePanel.add(choiceReadouts, BorderLayout.SOUTH);
        add(demoSection("Demo 1 — java.awt.Choice: select() is silent, one SELECTED, "
                + "insert re-selects 0, getSelectedObjects() is null when empty", choicePanel));
    }

    /**
     * The three selection reads a migrator compares against JComboBox.
     * {@code getSelectedObjects()} renders as {@code null} on an empty
     * Choice where Swing's would render as {@code []}.
     */
    private static String choiceState(Choice c) {
        return "getSelectedIndex() = " + c.getSelectedIndex()
                + ", getSelectedItem() = " + c.getSelectedItem()
                + ", getSelectedObjects() = " + Arrays.toString(c.getSelectedObjects());
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
