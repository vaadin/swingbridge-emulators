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
import vaadinx.awt.FileDialog;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.Frame;
import vaadinx.swing.JButton;
import vaadinx.swing.JFileChooser;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import javax.swing.filechooser.FileNameExtensionFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * File-IO demo. Shows the browser-upload-backed AWT {@link FileDialog}
 * LOAD path: the dialog hosts a Vaadin {@code Upload}, the user picks file(s),
 * the click VT parks on the modal latch, and the emulator stages the uploaded
 * bytes into a per-session temp dir so the migrated Swing code reads them back
 * as ordinary {@link java.io.File}s ({@code getName()} / {@code length()} /
 * {@code new FileInputStream(f)}).
 *
 * <p>Both file-pick hosts are wired: the AWT {@link FileDialog} (LOAD single /
 * multi, SAVE) and the Swing {@link JFileChooser} (showOpenDialog with an
 * extension filter, showSaveDialog). They share the same browser-transfer
 * machinery — JFileChooser composes an internal modal JDialog hosting the
 * upload, returning {@code APPROVE_OPTION} / {@code CANCEL_OPTION}.
 */
public class FileDialogsPanel extends JPanel {

    private final JLabel status = new JLabel("Open a file to see its details here.");

    public FileDialogsPanel() {
        super(new BorderLayout(8, 8));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        header.add(new JLabel(
                "AWT FileDialog. LOAD hosts a browser upload, staged to a per-session "
                        + "temp dir and read back as java.io.File. SAVE picks a name (modal), "
                        + "then a separate non-modal dialog offers a download link once the app "
                        + "has written the file."));

        // --- AWT FileDialog, single-file LOAD (working) ---
        JButton openOne = new JButton("Open file");
        openOne.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) null, "Open document", FileDialog.LOAD);
            fd.setVisible(true); // parks the click VT until OK / Cancel / X
            String name = fd.getFile();
            if (name == null) {
                status.setText("Open cancelled.");
                return;
            }
            File f = new File(fd.getDirectory(), name);
            status.setText("Opened " + name + " (" + f.length() + " bytes) — " + preview(f));
        });

        // --- AWT FileDialog, multi-select LOAD (working) ---
        JButton openMany = new JButton("Open files (multi-select)");
        openMany.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) null, "Open documents", FileDialog.LOAD);
            fd.setMultipleMode(true);
            fd.setVisible(true);
            File[] files = fd.getFiles();
            if (files.length == 0) {
                status.setText("Open cancelled.");
                return;
            }
            StringBuilder sb = new StringBuilder("Opened " + files.length + " file(s): ");
            for (int i = 0; i < files.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(files[i].getName()).append(" (").append(files[i].length()).append("B)");
            }
            status.setText(sb.toString());
        });

        // --- AWT FileDialog, SAVE (working) ---
        JButton saveOne = new JButton("Save file");
        saveOne.addActionListener(e -> {
            FileDialog fd = new FileDialog((Frame) null, "Save report", FileDialog.SAVE);
            fd.setFile("report.txt");
            fd.setVisible(true); // parks for the name pick only, then returns
            String name = fd.getFile();
            if (name == null) {
                status.setText("Save cancelled.");
                return;
            }
            // The app writes to the returned temp target; the separate download
            // dialog (already on screen) serves it once the user clicks.
            File f = new File(fd.getDirectory(), name);
            try {
                Files.writeString(f.toPath(),
                        "Sample report generated server-side at request time.\nSecond line.\n");
                status.setText("Wrote " + name + " (" + f.length()
                        + " bytes). Click Download in the popup to fetch it.");
            } catch (IOException ex) {
                status.setText("Write failed: " + ex.getMessage());
            }
        });

        // --- Swing JFileChooser, open with an extension filter (working) ---
        JButton chooserOpen = new JButton("Open with JFileChooser");
        chooserOpen.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Choose a text file");
            fc.setFileFilter(new FileNameExtensionFilter("Text & CSV", "txt", "csv"));
            int rc = fc.showOpenDialog(null); // parks the click VT until approve / cancel
            if (rc != JFileChooser.APPROVE_OPTION) {
                status.setText("JFileChooser open cancelled.");
                return;
            }
            File f = fc.getSelectedFile();
            status.setText("JFileChooser opened " + f.getName() + " (" + f.length()
                    + " bytes) — " + preview(f));
        });

        // --- Swing JFileChooser, save (working) ---
        JButton chooserSave = new JButton("Save with JFileChooser");
        chooserSave.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("notes.txt"));
            int rc = fc.showSaveDialog(null); // parks for the name pick only
            if (rc != JFileChooser.APPROVE_OPTION) {
                status.setText("JFileChooser save cancelled.");
                return;
            }
            File f = fc.getSelectedFile();
            try {
                Files.writeString(f.toPath(), "Notes written via JFileChooser.\n");
                status.setText("Wrote " + f.getName() + " (" + f.length()
                        + " bytes). Click Download in the popup to fetch it.");
            } catch (IOException ex) {
                status.setText("Write failed: " + ex.getMessage());
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        buttons.add(openOne);
        buttons.add(openMany);
        buttons.add(saveOne);
        buttons.add(chooserOpen);
        buttons.add(chooserSave);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(buttons, BorderLayout.NORTH);

        add(header, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    /** First line of a text upload, for an at-a-glance "we really read the bytes" cue. */
    private static String preview(File f) {
        try {
            String text = Files.readString(f.toPath()).replaceAll("\\s+", " ").trim();
            return text.length() > 60 ? "\"" + text.substring(0, 60) + "…\"" : "\"" + text + "\"";
        } catch (IOException | RuntimeException ex) {
            return "(binary or unreadable as text)";
        }
    }
}
