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
import vaadinx.awt.Checkbox;
import vaadinx.awt.CheckboxGroup;
import vaadinx.awt.GridLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.event.ItemEvent;
import java.util.Arrays;

/**
 * {@link vaadinx.awt.Checkbox} + {@link CheckboxGroup} — one widget rendering as
 * two, not {@link vaadinx.swing.JCheckBox} / {@link vaadinx.swing.JRadioButton}.
 * Decisions: {@code D_awt_checkbox} / {@code SD_scheckbox}; the lane's rationale:
 * {@code D_awt_lane}.
 *
 * <ol>
 *   <li><b>The silence of {@code setState}</b> — two ungrouped boxes whose
 *       {@code ItemListener} writes every event into a log, beside a Swing
 *       button that flips one of them programmatically. The payoff is what does
 *       <em>not</em> happen: AWT posts an {@code ItemEvent} only for a browser
 *       toggle, the direct inverse of {@code JCheckBox}. Plus a
 *       {@code getSelectedObjects()} readout, {@code null} rather than
 *       {@code []} when unchecked.</li>
 *   <li><b>{@code CheckboxGroup}: grouped boxes render as radios</b> — one click
 *       yields exactly one SELECTED line and nothing for the box that turned off
 *       (Swing's {@code ButtonGroup} would fire DESELECTED); re-clicking the
 *       selected radio yields no line at all and bounces back;
 *       {@code setSelectedCheckbox} moves the selection silently; and a fourth
 *       box joins and leaves the group live, which is where the glyph flip is
 *       visible.</li>
 * </ol>
 */
public class AwtCheckboxPanel extends JPanel {

    public AwtCheckboxPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1 — ungrouped. The teaching point is the event that doesn't
        // arrive: setState is silent where JCheckBox's setSelected fires.
        Checkbox verbose = new Checkbox("Verbose logging");
        Checkbox autosave = new Checkbox("Autosave", true);
        JLabel boxEvents = new JLabel("no ItemEvent yet — tick a box");
        JLabel boxState = new JLabel(checkboxState(verbose, autosave));
        java.awt.event.ItemListener logItem = e -> {
            boxEvents.setText("ItemEvent: getItem()=" + e.getItem()
                    + ", stateChange="
                    + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED"));
            boxState.setText(checkboxState(verbose, autosave));
        };
        verbose.addItemListener(logItem);
        autosave.addItemListener(logItem);

        JButton flipSilently = new JButton("setState(!state) — fires nothing");
        flipSilently.addActionListener(e -> {
            verbose.setState(!verbose.getState());
            boxState.setText(checkboxState(verbose, autosave));
        });
        JPanel boxRow = new JPanel();
        boxRow.add(verbose);
        boxRow.add(autosave);
        boxRow.add(flipSilently);
        JPanel boxReadouts = new JPanel(new GridLayout(2, 1, 0, 2));
        boxReadouts.add(boxEvents);
        boxReadouts.add(boxState);
        JPanel boxes = new JPanel(new BorderLayout(0, 6));
        boxes.add(boxRow, BorderLayout.NORTH);
        boxes.add(boxReadouts, BorderLayout.SOUTH);
        add(demoSection("Demo 1 — java.awt.Checkbox: only the browser posts ItemEvents; "
                + "getSelectedObjects() is null when unchecked", boxes));
        add(Box.createVerticalStrut(8));

        // Demo 2 — CheckboxGroup. Every line of the log is a fact about AWT
        // that ButtonGroup gets differently, so the log is the demo.
        CheckboxGroup unitGroup = new CheckboxGroup();
        Checkbox metric = new Checkbox("metric", true, unitGroup);
        Checkbox imperial = new Checkbox("imperial", false, unitGroup);
        Checkbox nautical = new Checkbox("nautical", false, unitGroup);
        Checkbox joiner = new Checkbox("joins the group on demand");

        JLabel groupEvents = new JLabel("no ItemEvent yet — pick a unit");
        JLabel groupReadout = new JLabel(groupState(unitGroup));
        java.awt.event.ItemListener logGroupItem = e -> {
            groupEvents.setText("ItemEvent on " + e.getItem() + ": "
                    + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED")
                    + " — and nothing on the box that turned off");
            groupReadout.setText(groupState(unitGroup));
        };
        metric.addItemListener(logGroupItem);
        imperial.addItemListener(logGroupItem);
        nautical.addItemListener(logGroupItem);
        joiner.addItemListener(logGroupItem);

        JButton selectSilent = new JButton("setSelectedCheckbox(nautical) — fires nothing");
        selectSilent.addActionListener(e -> {
            unitGroup.setSelectedCheckbox(nautical);
            groupReadout.setText(groupState(unitGroup));
        });
        JButton toggleMembership = new JButton("Join / leave the group");
        toggleMembership.addActionListener(e -> {
            joiner.setCheckboxGroup(joiner.getCheckboxGroup() == null ? unitGroup : null);
            joiner.setLabel(joiner.getCheckboxGroup() == null
                    ? "joins the group on demand" : "in the group — now a radio");
            groupReadout.setText(groupState(unitGroup));
        });

        JPanel radios = new JPanel();
        radios.add(metric);
        radios.add(imperial);
        radios.add(nautical);
        radios.add(joiner);
        JPanel groupControls = new JPanel();
        groupControls.add(selectSilent);
        groupControls.add(toggleMembership);
        JPanel groupReadouts = new JPanel(new GridLayout(2, 1, 0, 2));
        groupReadouts.add(groupEvents);
        groupReadouts.add(groupReadout);
        JPanel group = new JPanel(new BorderLayout(0, 6));
        group.add(radios, BorderLayout.NORTH);
        group.add(groupControls, BorderLayout.CENTER);
        group.add(groupReadouts, BorderLayout.SOUTH);
        add(demoSection("Demo 2 — CheckboxGroup: grouped boxes render as radios, one event per "
                + "click, none for the sibling, none at all on a re-click", group));
    }

    private static String checkboxState(Checkbox verbose, Checkbox autosave) {
        return "verbose.getState() = " + verbose.getState()
                + ", verbose.getSelectedObjects() = "
                + Arrays.toString(verbose.getSelectedObjects())
                + ", autosave.getState() = " + autosave.getState();
    }

    private static String groupState(CheckboxGroup group) {
        Checkbox selected = group.getSelectedCheckbox();
        return "getSelectedCheckbox() = " + (selected == null ? "null" : selected.getLabel());
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
