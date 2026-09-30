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
import vaadinx.swing.JCheckBoxMenuItem;
import vaadinx.swing.JLabel;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPopupMenu;

/**
 * Popup-menu demo (D_jpopupmenu / SD_sjpopupmenu). Exercises
 * {@link JPopupMenu} driving the {@link com.vaadin.swingbridge.surrogates.SJPopupMenu}
 * surrogate's {@code ContextMenu} peer, attached to host components via
 * {@link vaadinx.swing.JComponent#setComponentPopupMenu} (the Swing-flavoured
 * helper over Vaadin's native {@code ContextMenu.setTarget}).
 *
 * <p>Two right-click targets:
 * <ul>
 *   <li>A "document" label with a file-actions popup: Open / Save +
 *       separator + nested Export submenu (PDF / RTF) + separator + Delete.
 *       Top-level separators render here (the ContextMenu peer supports them,
 *       unlike the MenuBar — SD_sjpopupmenu).</li>
 *   <li>A "view options" label with a JCheckBoxMenuItem popup (Word wrap).</li>
 * </ul>
 *
 * <p>A readout {@link JLabel} shows the most recently fired ActionEvent.
 * Right-click (or long-press) on a target opens its popup natively — no
 * {@code show(x,y)} call (which would WARN per SD_sjpopupmenu's accepted gap).
 */
public class PopupMenusPanel extends JPanel {

    public PopupMenusPanel() {
        super(new BorderLayout(8, 8));

        JLabel readout = new JLabel("(right-click a target below)");

        JPanel targets = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 16));

        // ---- File-actions popup on a "document" label ----------------
        JLabel doc = new JLabel("Document.txt — right-click for actions");
        JPopupMenu filePopup = new JPopupMenu();
        JMenuItem open = new JMenuItem("Open");
        open.addActionListener(e -> readout.setText("Open"));
        JMenuItem save = new JMenuItem("Save");
        save.addActionListener(e -> readout.setText("Save"));
        filePopup.add(open);
        filePopup.add(save);
        filePopup.addSeparator();
        JMenu export = new JMenu("Export");
        JMenuItem pdf = new JMenuItem("PDF");
        pdf.addActionListener(e -> readout.setText("Export → PDF"));
        JMenuItem rtf = new JMenuItem("RTF");
        rtf.addActionListener(e -> readout.setText("Export → RTF"));
        export.add(pdf);
        export.add(rtf);
        filePopup.add(export);
        filePopup.addSeparator();
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> readout.setText("Delete"));
        filePopup.add(delete);
        doc.setComponentPopupMenu(filePopup);

        // ---- Checkbox popup on a "view options" label ----------------
        JLabel view = new JLabel("View options — right-click");
        JPopupMenu viewPopup = new JPopupMenu();
        JCheckBoxMenuItem wrap = new JCheckBoxMenuItem("Word wrap", true);
        wrap.addActionListener(e -> readout.setText("Word wrap = " + wrap.getState()));
        viewPopup.add(wrap);
        view.setComponentPopupMenu(viewPopup);

        targets.add(doc);
        targets.add(view);

        add(targets, BorderLayout.NORTH);
        add(readout, BorderLayout.CENTER);
    }
}
