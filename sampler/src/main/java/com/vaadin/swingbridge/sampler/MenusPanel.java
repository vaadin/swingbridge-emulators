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
import vaadinx.swing.ButtonGroup;
import vaadinx.swing.JCheckBoxMenuItem;
import vaadinx.swing.JLabel;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuBar;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JPanel;
import vaadinx.swing.JRadioButtonMenuItem;

import javax.swing.KeyStroke;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Menus demo. Exercises
 * D_menu_tree / SD_sjmenubar — JMenuBar / JMenu / JMenuItem / JCheckBoxMenuItem /
 * JRadioButtonMenuItem driving the SJMenuBar surrogate's tree-rebuild
 * push API.
 *
 * <h2>Menubar placement</h2>
 * The Sampler shell {@link SamplerFrame} is annotated {@code @MainWindow}
 * and peers over {@code SJPanel} (InlineStrategy). This panel embeds
 * the JMenuBar as a {@code BorderLayout.NORTH} child of the demo panel
 * itself rather than calling {@code SamplerFrame.setJMenuBar} so the
 * menubar lives within the demo card alongside the readout label — the
 * menu <i>behaviour</i> (drop-down, accelerators, JCheckBox /
 * JRadioButton menu-item state) is identical to a frame-attached
 * placement and is what the testbed validates.
 *
 * <p>Four top-level menus exercise different aspects of the surface:
 * <ul>
 *   <li>{@code File} — leaf items + separator + accelerators
 *       ({@code Ctrl+O} on Open, {@code Ctrl+S} on Save).</li>
 *   <li>{@code Edit} — Cut / Copy / Paste with accelerators.</li>
 *   <li>{@code View} — JCheckBoxMenuItem (Show toolbar) +
 *       JRadioButtonMenuItem chrome (Light / Dark theme, coordinated
 *       through a {@link ButtonGroup} per D_buttongroup — clicking one deselects
 *       the other; clicking the already-selected item is suppressed).</li>
 *   <li>{@code Help} — nested submenu + leaf About item.</li>
 * </ul>
 *
 * <p>A readout {@link JLabel} shows the most recently fired ActionEvent
 * so the user can see clicks land.
 */
public class MenusPanel extends JPanel {

    public MenusPanel() {
        super(new BorderLayout(8, 8));

        JLabel readout = new JLabel("(no menu selection yet)");

        JMenuBar bar = new JMenuBar();

        // ---- File menu ------------------------------------------------
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open");
        open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        open.addActionListener(e -> readout.setText("File → Open"));
        JMenuItem save = new JMenuItem("Save");
        save.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        save.addActionListener(e -> readout.setText("File → Save"));
        file.add(open);
        file.add(save);
        file.addSeparator();
        bar.add(file);

        // ---- Edit menu ------------------------------------------------
        JMenu edit = new JMenu("Edit");
        JMenuItem cut = new JMenuItem("Cut");
        cut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        cut.addActionListener(e -> readout.setText("Edit → Cut"));
        JMenuItem copy = new JMenuItem("Copy");
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copy.addActionListener(e -> readout.setText("Edit → Copy"));
        JMenuItem paste = new JMenuItem("Paste");
        paste.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        paste.addActionListener(e -> readout.setText("Edit → Paste"));
        edit.add(cut);
        edit.add(copy);
        edit.add(paste);
        bar.add(edit);

        // ---- View menu ------------------------------------------------
        JMenu view = new JMenu("View");
        JCheckBoxMenuItem toolbar = new JCheckBoxMenuItem("Show toolbar", true);
        toolbar.addActionListener(e ->
                readout.setText("View → Show toolbar = " + toolbar.getState()));
        view.add(toolbar);
        view.addSeparator();
        JRadioButtonMenuItem lightTheme = new JRadioButtonMenuItem("Light", true);
        lightTheme.addActionListener(e -> readout.setText("View → Light theme"));
        JRadioButtonMenuItem darkTheme = new JRadioButtonMenuItem("Dark");
        darkTheme.addActionListener(e -> readout.setText("View → Dark theme"));
        // ButtonGroup coordination per D_buttongroup — clicking Dark deselects
        // Light through the group's setSelectedButton cascade; clicking
        // the already-selected item is a no-op for selection state but
        // still fires ActionEvent (matches JDK).
        ButtonGroup themeGroup = new ButtonGroup();
        themeGroup.add(lightTheme);
        themeGroup.add(darkTheme);
        view.add(lightTheme);
        view.add(darkTheme);
        bar.add(view);

        // ---- Help menu (nested submenu) -------------------------------
        JMenu help = new JMenu("Help");
        JMenu helpDocs = new JMenu("Documentation");
        JMenuItem readme = new JMenuItem("README");
        readme.addActionListener(e -> readout.setText("Help → Documentation → README"));
        JMenuItem changelog = new JMenuItem("Changelog");
        changelog.addActionListener(e -> readout.setText("Help → Documentation → Changelog"));
        helpDocs.add(readme);
        helpDocs.add(changelog);
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> readout.setText("Help → About"));
        help.add(helpDocs);
        help.addSeparator();
        help.add(about);
        bar.add(help);

        add(bar, BorderLayout.NORTH);
        add(readout, BorderLayout.CENTER);
    }
}
