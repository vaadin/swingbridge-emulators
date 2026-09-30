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

package com.vaadin.swingbridge.testapps.crud.swing;

import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSeparator;
import vaadinx.awt.BorderLayout;
import java.awt.Dimension;
import vaadinx.text.SimpleDateFormat;

public final class EmployeePreviewPanel extends JPanel {

    private static final SimpleDateFormat DOB_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final String EMPTY = " ";

    private final JLabel nameValue = new JLabel(EMPTY);
    private final JLabel roleValue = new JLabel(EMPTY);
    private final JLabel levelValue = new JLabel(EMPTY);
    private final JLabel ratingValue = new JLabel(EMPTY);
    private final JLabel activeValue = new JLabel(EMPTY);
    private final JLabel favoriteValue = new JLabel(EMPTY);
    private final JLabel dobValue = new JLabel(EMPTY);
    private final JLabel bioValue = new JLabel(EMPTY);

    public EmployeePreviewPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Preview"));
        setPreferredSize(new Dimension(280, 0));

        JPanel rows = new JPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        rows.add(row("Name", nameValue));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Role", roleValue));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Level", levelValue));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Rating", ratingValue));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Active", activeValue));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Favorite", favoriteValue));
        rows.add(Box.createVerticalStrut(4));
        rows.add(row("Date of birth", dobValue));
        rows.add(Box.createVerticalStrut(8));
        rows.add(new JSeparator());
        rows.add(Box.createVerticalStrut(8));
        rows.add(row("Bio", bioValue));

        add(rows, BorderLayout.NORTH);
    }

    private static JPanel row(String label, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        JLabel key = new JLabel(label + ":");
        key.setPreferredSize(new Dimension(110, key.getPreferredSize().height));
        p.add(key, BorderLayout.WEST);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    public void show(Employee e) {
        if (e == null) {
            nameValue.setText(EMPTY);
            roleValue.setText(EMPTY);
            levelValue.setText(EMPTY);
            ratingValue.setText(EMPTY);
            activeValue.setText(EMPTY);
            favoriteValue.setText(EMPTY);
            dobValue.setText(EMPTY);
            bioValue.setText(EMPTY);
            return;
        }
        nameValue.setText(e.getName());
        roleValue.setText(e.getRole().name());
        levelValue.setText(Integer.toString(e.getLevel()));
        ratingValue.setText(e.getRating() + "/100");
        activeValue.setText(e.isActive() ? "yes" : "no");
        favoriteValue.setText(e.isFavorite() ? "yes" : "no");
        dobValue.setText(e.getDateOfBirth() == null ? EMPTY : DOB_FORMAT.format(e.getDateOfBirth()));
        bioValue.setText("<html><body style='width:230px'>" + escape(e.getBio()) + "</body></html>");
    }

    private static String escape(String s) {
        if (s == null || s.isEmpty()) return "&nbsp;";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
