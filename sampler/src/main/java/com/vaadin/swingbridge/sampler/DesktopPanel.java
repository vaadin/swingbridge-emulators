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
import vaadinx.swing.JButton;
import vaadinx.swing.JDesktopPane;
import vaadinx.swing.JInternalFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.event.InternalFrameAdapter;
import vaadinx.swing.event.InternalFrameEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link JDesktopPane} + {@link JInternalFrame} demo (D_internal_frames). Each internal
 * frame renders as a decorated non-modal Vaadin Dialog overlay — draggable
 * by its title bar, resizable by its grips, closable via the header ✕ —
 * escaping the desktop-pane bounds and stacking in overlay order.
 *
 * <p>"Open frame" spawns a fresh {@link JInternalFrame} with the full set of
 * title-bar buttons (minimize / maximize / close, like Swing's
 * {@code BasicInternalFrameTitlePane}). An
 * {@link vaadinx.swing.event.InternalFrameListener} on each frame drives the
 * status line (open / activate / close) and the <b>minimized taskbar</b>
 * along the bottom: because a browser has no desktop-icon strip to render an
 * iconified frame (D_internalframe_minimize, the one emulator/surrogate coverage divergence),
 * this demo turns each {@code INTERNAL_FRAME_ICONIFIED} into a "Restore"
 * chip and each {@code DEICONIFIED} back off — so the full minimize
 * round-trip is visible and the emulator-only iconify events are exercised
 * end-to-end. Every path here stays WARN-free (the exit-gate assertion):
 * minimize is emulator-complete, not a stub.
 */
public class DesktopPanel extends JPanel {

    private final JDesktopPane desktop = new JDesktopPane();
    private final JLabel status = new JLabel("No frame opened yet.");
    private final JPanel taskbar = new JPanel();
    private final Map<JInternalFrame, JButton> restoreChips = new LinkedHashMap<>();
    private int frameCounter;

    public DesktopPanel() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        JButton open = new JButton("Open frame");
        open.addActionListener(e -> openFrame());
        controls.add(open);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(status);

        taskbar.setLayout(new BoxLayout(taskbar, BoxLayout.X_AXIS));
        taskbar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        taskbar.add(new JLabel("Minimized: "));

        add(controls, BorderLayout.NORTH);
        add(desktop, BorderLayout.CENTER);
        add(taskbar, BorderLayout.SOUTH);
    }

    private void openFrame() {
        int n = ++frameCounter;
        JInternalFrame frame = new JInternalFrame(
                "Document " + n, /*resizable*/ true, /*closable*/ true,
                /*maximizable*/ true, /*iconifiable*/ true);

        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        body.add(new JLabel("Internal frame #" + n
                + " — use the title-bar buttons to minimize / maximize / close."),
                BorderLayout.CENTER);
        frame.setContentPane(body);
        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameOpened(InternalFrameEvent e) {
                status.setText("Frame " + n + " opened.");
            }
            @Override public void internalFrameActivated(InternalFrameEvent e) {
                status.setText("Frame " + n + " activated.");
            }
            @Override public void internalFrameIconified(InternalFrameEvent e) {
                status.setText("Frame " + n + " minimized.");
                addRestoreChip(frame, n);
            }
            @Override public void internalFrameDeiconified(InternalFrameEvent e) {
                status.setText("Frame " + n + " restored.");
                removeRestoreChip(frame);
            }
            @Override public void internalFrameClosed(InternalFrameEvent e) {
                status.setText("Frame " + n + " closed.");
                removeRestoreChip(frame);
            }
        });

        desktop.add(frame);
        frame.setBounds(24 + n * 24, 24 + n * 24, 320, 180);
        frame.setVisible(true);
        selectQuietly(frame);
    }

    private void setIconQuietly(JInternalFrame frame, boolean icon) {
        try {
            frame.setIcon(icon);
        } catch (java.beans.PropertyVetoException vetoed) {
            // No veto listener installed here.
        }
    }

    private void selectQuietly(JInternalFrame frame) {
        try {
            frame.setSelected(true);
            desktop.setSelectedFrame(frame);
        } catch (java.beans.PropertyVetoException vetoed) {
            // No veto listener installed here.
        }
    }

    /** Add a "Restore" chip to the taskbar for a just-iconified frame. */
    private void addRestoreChip(JInternalFrame frame, int n) {
        if (restoreChips.containsKey(frame)) return;
        JButton chip = new JButton("Restore: Document " + n);
        chip.addActionListener(e -> setIconQuietly(frame, false));
        restoreChips.put(frame, chip);
        taskbar.add(chip);
    }

    /** Remove a frame's taskbar chip when it's restored or closed. */
    private void removeRestoreChip(JInternalFrame frame) {
        JButton chip = restoreChips.remove(frame);
        if (chip != null) {
            taskbar.remove(chip);
        }
    }
}
