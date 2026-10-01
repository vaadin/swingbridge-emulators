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
import vaadinx.swing.ButtonGroup;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JRadioButton;

import java.awt.event.ItemEvent;

/**
 * {@link JRadioButton} demo. Three sub-demos stacked
 * vertically; each exercises a different slice of the surface so the
 * exit-gate test can assert "user-path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Standalone</b> — a {@link JRadioButton} with no group. Toggles
 *       like a checkbox; an {@link java.awt.event.ItemListener} flips a
 *       readout label so the user-facing event fan-out is visible.</li>
 *   <li><b>Group of three (Priority)</b> — three radios in a single
 *       {@link ButtonGroup} (Low / Medium / High). Standard radio mutex:
 *       clicking one cascades a DESELECTED to the prior selection via
 *       D_buttongroup, through each button's own model. Pre-selecting Medium
 *       in the ctor demos the group's "newcomer-already-selected"
 *       invariant.</li>
 *   <li><b>Mixed JCheckBox + JRadioButton</b> — one ButtonGroup containing
 *       two JRadioButtons and one JCheckBox. Every member's model is a
 *       ToggleButtonModel, so the group sees all three and the mutex works
 *       across types (D_emulator_button_model).</li>
 * </ol>
 */
public class RadioButtonsPanel extends JPanel {

    public RadioButtonsPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — standalone (no group). Toggles like a checkbox; the
        // ItemListener flips a readout so user-facing event fan-out is
        // visible. "Radio stays selected when clicked" is a group-veto
        // effect — without a group, ToggleButtonModel toggles, matching
        // JDK behaviour.
        JLabel standaloneReadout = new JLabel("Subscribe: off");
        JRadioButton subscribe = new JRadioButton("Subscribe to updates");
        subscribe.addItemListener(e -> standaloneReadout.setText(
                "Subscribe: " + (e.getStateChange() == ItemEvent.SELECTED ? "on" : "off")));
        JPanel standalone = new JPanel();
        standalone.setLayout(new BoxLayout(standalone, BoxLayout.X_AXIS));
        standalone.add(subscribe);
        standalone.add(Box.createHorizontalStrut(12));
        standalone.add(standaloneReadout);
        add(demoSection("Demo 1 — standalone (no ButtonGroup; toggles like a checkbox)", standalone));
        add(Box.createVerticalStrut(8));

        // Demo 2 — group of three. Standard radio mutex via ButtonGroup.
        // Medium pre-selected in the ctor demonstrates the group's
        // newcomer-already-selected invariant: the first add wins the
        // slot, subsequent adds of already-selected newcomers force off.
        JLabel priorityReadout = new JLabel("Priority: Medium");
        JRadioButton low = new JRadioButton("Low");
        JRadioButton medium = new JRadioButton("Medium", true);
        JRadioButton high = new JRadioButton("High");
        ButtonGroup priorityGroup = new ButtonGroup();
        priorityGroup.add(low);
        priorityGroup.add(medium);
        priorityGroup.add(high);
        java.awt.event.ItemListener priorityListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                JRadioButton rb = (JRadioButton) e.getSource();
                priorityReadout.setText("Priority: " + rb.getText());
            }
        };
        low.addItemListener(priorityListener);
        medium.addItemListener(priorityListener);
        high.addItemListener(priorityListener);
        JPanel priority = new JPanel();
        priority.setLayout(new BoxLayout(priority, BoxLayout.X_AXIS));
        priority.add(low);
        priority.add(Box.createHorizontalStrut(4));
        priority.add(medium);
        priority.add(Box.createHorizontalStrut(4));
        priority.add(high);
        priority.add(Box.createHorizontalStrut(12));
        priority.add(priorityReadout);
        add(demoSection("Demo 2 — group of three (Priority via ButtonGroup)", priority));
        add(Box.createVerticalStrut(8));

        // Demo 3 — mixed JCheckBox + JRadioButton in one group. Both
        // types use AbstractButtonMixin, so the group's mutex is honoured
        // across types (D_abstractbutton_mixin_dispatch) — the JCheckBox isn't the sole "live" member.
        JLabel mixedReadout = new JLabel("Mode: (none selected)");
        JCheckBox cbOff = new JCheckBox("Off");
        JRadioButton rbAuto = new JRadioButton("Auto");
        JRadioButton rbManual = new JRadioButton("Manual");
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(cbOff);
        modeGroup.add(rbAuto);
        modeGroup.add(rbManual);
        java.awt.event.ItemListener modeListener = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String label;
                Object src = e.getSource();
                if (src instanceof JRadioButton rb) label = rb.getText();
                else if (src instanceof JCheckBox cb) label = cb.getText();
                else label = "?";
                mixedReadout.setText("Mode: " + label);
            }
        };
        cbOff.addItemListener(modeListener);
        rbAuto.addItemListener(modeListener);
        rbManual.addItemListener(modeListener);
        JPanel mixed = new JPanel();
        mixed.setLayout(new BoxLayout(mixed, BoxLayout.X_AXIS));
        mixed.add(cbOff);
        mixed.add(Box.createHorizontalStrut(4));
        mixed.add(rbAuto);
        mixed.add(Box.createHorizontalStrut(4));
        mixed.add(rbManual);
        mixed.add(Box.createHorizontalStrut(12));
        mixed.add(mixedReadout);
        add(demoSection("Demo 3 — mixed JCheckBox + JRadioButton in one ButtonGroup (D_abstractbutton_mixin_dispatch unlock)", mixed));
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
