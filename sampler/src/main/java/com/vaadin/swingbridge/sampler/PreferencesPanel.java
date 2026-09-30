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

import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * {@code java.util.prefs.Preferences} demo. Reads and writes per-user
 * settings through the JDK's own {@code Preferences} API, unchanged from a
 * desktop Swing app: no import swap ({@code java.util.prefs} is not
 * {@code javax.swing}), the localStorage-backed {@code VaadinPreferencesFactory}
 * is selected JVM-wide via SPI. See D_preferences.
 *
 * <p>The panel constructor reads three settings from
 * {@code Preferences.userNodeForPackage(...)} — a display name, a "show tips"
 * flag, and an open-count that increments each visit — and demonstrates
 * persistence: the values survive a full browser-tab reload because they live
 * in the browser's {@code localStorage}. "Save" writes the fields back; "Reset"
 * clears them.
 *
 * <p>Because the panel is built on a demo-swap click (a peer event under
 * {@code EHelper.callSwing}, i.e. a virtual thread), the first pref read here
 * parks once on the {@code localStorage} round-trip and then serves
 * synchronously — the standard EDT-context read rule (D_preferences, twin of SD_browser_timezone). The
 * open-count also makes that warm-up visible: reload the tab and it goes up.
 */
public class PreferencesPanel extends JPanel {

    private final Preferences prefs = Preferences.userNodeForPackage(PreferencesPanel.class);

    private final JTextField displayName = new JTextField(24);
    private final JCheckBox showTips = new JCheckBox("Show tips on startup");
    private final JLabel status = new JLabel(" ");

    public PreferencesPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // First read parks once on the localStorage warm-up (production), then
        // serves synchronously. In-memory under the exit-gate test's test mode.
        displayName.setName("prefs-display-name");
        displayName.setText(prefs.get("displayName", ""));

        showTips.setName("prefs-show-tips");
        showTips.setSelected(prefs.getBoolean("showTips", true));

        int visits = prefs.getInt("visitCount", 0) + 1;
        prefs.putInt("visitCount", visits);
        JLabel visitsLabel = new JLabel(
                "Opened " + visits + " time(s) — persists across browser reloads.");
        visitsLabel.setName("prefs-visit-count");

        status.setName("prefs-status");

        JButton save = new JButton("Save");
        save.setName("prefs-save");
        save.addActionListener(e -> {
            prefs.put("displayName", displayName.getText());
            prefs.putBoolean("showTips", showTips.isSelected());
            try {
                prefs.flush();
            } catch (BackingStoreException ex) {
                status.setText("Save failed: " + ex.getMessage());
                return;
            }
            status.setText("Saved. Reload the browser tab — the values persist.");
        });

        JButton reset = new JButton("Reset");
        reset.setName("prefs-reset");
        reset.addActionListener(e -> {
            prefs.remove("displayName");
            prefs.remove("showTips");
            prefs.remove("visitCount");
            displayName.setText("");
            showTips.setSelected(true);
            status.setText("Cleared. Reload the tab to see defaults return.");
        });

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(save);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(reset);

        add(new JLabel("These settings are stored in the browser's localStorage "
                + "(the web analog of a desktop app's machine-local preferences)."));
        add(Box.createVerticalStrut(8));
        add(new JLabel("Display name:"));
        add(displayName);
        add(Box.createVerticalStrut(4));
        add(showTips);
        add(Box.createVerticalStrut(8));
        add(buttons);
        add(Box.createVerticalStrut(8));
        add(visitsLabel);
        add(Box.createVerticalStrut(4));
        add(status);
    }
}
