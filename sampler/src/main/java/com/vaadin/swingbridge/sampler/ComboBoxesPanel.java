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
import vaadinx.awt.GridLayout;
import vaadinx.swing.DefaultListCellRenderer;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import javax.swing.DefaultComboBoxModel;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * JComboBox demo. Non-editable
 * item selection plus an editable combo with custom-value commit, both
 * updating a live readout label via {@link ItemListener}. A custom
 * renderer on the colour combo demonstrates the cell-renderer
 * carve-out (D_jcombobox).
 *
 * <ul>
 *   <li>Non-editable {@link JComboBox} — three colours; selecting one
 *       updates the readout via {@link ItemEvent#SELECTED}.</li>
 *   <li>Editable {@link JComboBox} — three browsers; user can type a
 *       custom value, Enter commits and selects it.</li>
 *   <li>Custom renderer — capitalises and adds a hyphen prefix per
 *       item (model values stored verbatim; only rendered appearance
 *       changes).</li>
 *   <li>Lazily filled {@link JComboBox} — starts empty and loads its items in
 *       {@link PopupMenuListener#popupMenuWillBecomeVisible}, the common Swing
 *       idiom; a second readout counts the popup's opens and closes.</li>
 * </ul>
 */
public class ComboBoxesPanel extends JPanel {

    public ComboBoxesPanel() {
        super(new BorderLayout(8, 8));

        JComboBox<String> colour = new JComboBox<>(new String[] {"red", "green", "blue"});
        // Swing name → peer HTML id, so the WarnInventoryTest can address this
        // combo by identity rather than by position among every combo on screen
        // (the shell's own "Jump to" picker is one of those).
        colour.setName("combo-colour");
        colour.setRenderer(new DefaultListCellRenderer() {
            @Override
            public vaadinx.awt.Component getListCellRendererComponent(
                    vaadinx.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                vaadinx.awt.Component out = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                String s = value == null ? "" : value.toString();
                setText("- " + s.substring(0, 1).toUpperCase() + s.substring(1));
                return out;
            }
        });

        JComboBox<String> browser = new JComboBox<>(
                new DefaultComboBoxModel<>(new String[] {"Chrome", "Firefox", "Safari"}));
        browser.setName("combo-browser");
        browser.setEditable(true);

        JLabel readout = new JLabel(format(colour.getSelectedItem(), browser.getSelectedItem()));
        ItemListener live = e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                readout.setText(format(colour.getSelectedItem(), browser.getSelectedItem()));
            }
        };
        colour.addItemListener(live);
        browser.addItemListener(live);

        JComboBox<String> planet = new JComboBox<>();
        planet.setName("combo-planet");
        JLabel popupReadout = new JLabel("popup: opened 0×, closed 0×");
        popupReadout.setName("popup-readout");
        planet.addPopupMenuListener(new PopupMenuListener() {
            private int opened, closed;

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                if (planet.getItemCount() == 0) {
                    for (String p : new String[] {"Mercury", "Venus", "Earth", "Mars"}) planet.addItem(p);
                }
                opened++;
                update();
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                closed++;
                update();
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }

            private void update() {
                popupReadout.setText("popup: opened " + opened + "×, closed " + closed + "×");
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        controls.add(new JLabel("Colour:"));
        controls.add(colour);
        controls.add(new JLabel("Browser:"));
        controls.add(browser);
        controls.add(new JLabel("Planet:"));
        controls.add(planet);
        add(controls, BorderLayout.CENTER);
        JPanel readouts = new JPanel(new GridLayout(2, 1));
        readouts.add(readout);
        readouts.add(popupReadout);
        add(readouts, BorderLayout.SOUTH);
    }

    private static String format(Object colour, Object browser) {
        return "colour = " + colour + ", browser = " + browser;
    }
}
