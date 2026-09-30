/*
 * Copyright (c) 1995, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's java.awt.FileDialog
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// ============================================================================
// File-IO (see D_file_dialogs). The upload->temp-File (LOAD) and temp-File->download
// (SAVE) transfer mechanics live in vaadinx.BrowserFileTransfer; JFileChooser
// reuses them. The full FileDialog surface is implemented (LOAD, SAVE,
// multi-select, FilenameFilter, mode); setDirectory is an accept-and-ignore
// WARN-noop (no server-side dir to browse, R_match_swing_errors (c)).
//
// DESIGN:
//  - FileDialog extends vaadinx.awt.Dialog, so setVisible(true) parks the
//    calling virtual thread on the inherited modal latch (Dialog#parkUntilClose)
//    and an OK/Cancel handler unparks via dispose() — same machinery JOptionPane
//    / modal JDialog already use. FileDialog is ALWAYS modal in AWT, so the
//    ctors force modal=true.
//  - LOAD: dialog hosts a Vaadin Upload; on succeeded -> write bytes to a
//    per-session temp dir -> getFile()/getFiles()/getDirectory() return the
//    temp paths; OK disposes -> unpark. Temp files cleaned on session close.
//  - SAVE: a TWO-dialog design. (1) the FileDialog's own modal peer hosts a
//    filename TextField + Save/Cancel and blocks ONLY for the name pick — Save
//    returns getFile()=name + a temp target path, Cancel returns null. (2) once
//    Save returns, the app writes the file (sync or async); meanwhile a SEPARATE
//    non-modal "synthetic" Vaadin dialog — invisible to the Swing app, like an
//    a11y overlay — offers a real download link the user clicks once their app
//    signals the write is done. We deliberately do NOT try to detect "fully
//    written" (impossible in general: the write may be a background job); the
//    human is the oracle, with a warning + an auto-refreshing size readout
//    (polled on the per-UI vaadinx.UiScheduler, pushed live) as honest cues.
//    Blocking until "written" instead would DEADLOCK — the write lives after
//    setVisible returns, so a park there would wedge the very write the user is
//    waiting to download. Faithful divergence from desktop (R_best_effort_behaviour/R_vaadin_first): desktop
//    saves silently to a chosen path; we make the user pull the file afterward.
// ============================================================================

import java.io.File;
import java.util.List;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

/**
 * Emulator for {@link java.awt.FileDialog}. Thin shell over the modal
 * {@link vaadinx.awt.Dialog} machinery; LOAD bridges the {@code java.io.File}
 * semantic gap via a browser {@code Upload} staged to a per-session temp file.
 */
public class FileDialog extends vaadinx.awt.Dialog {

    /** This FileDialog is for reading a file (browser upload). */
    public static final int LOAD = 0;
    /** This FileDialog is for writing a file (browser download). */
    public static final int SAVE = 1;

    // --- State the ctors populate directly (kept real so construction works) ---
    // mode is field-backed and round-trips through get/setMode; the LOAD/SAVE
    // behaviour hangs off it but the field itself is trivial state.
    private int mode = LOAD;
    // file / dir / files hold the resolved LOAD result; populated by
    // finalizeResult() once the modal unparks.
    private String file;
    private String dir;
    private File[] files = new File[0];
    private java.io.FilenameFilter filter;
    private boolean multipleMode;

    // Per-show LOAD scratch: staged is the (synchronized) list the
    // BrowserFileTransfer upload wiring grows as each accepted upload lands;
    // re-assigned per show by buildLoadContent. approved records OK (true) vs
    // Cancel / X / ESC (false).
    private List<File> staged = List.of();
    private boolean approved;
    // SAVE: the filename the user confirmed in the (modal) FileDialog, read out
    // in finalizeResult to build the temp target + the synthetic download dialog.
    private String chosenSaveName;

    // --- Constructors (REAL — must construct + be modal so park works) --------

    public FileDialog(vaadinx.awt.Frame parent) {
        this(parent, "", LOAD);
    }

    public FileDialog(vaadinx.awt.Frame parent, String title) {
        this(parent, title, LOAD);
    }

    public FileDialog(vaadinx.awt.Frame parent, String title, int mode) {
        super(parent, title, true); // FileDialog is always modal
        this.mode = checkMode(mode);
    }

    public FileDialog(vaadinx.awt.Dialog parent) {
        this(parent, "", LOAD);
    }

    public FileDialog(vaadinx.awt.Dialog parent, String title) {
        this(parent, title, LOAD);
    }

    public FileDialog(vaadinx.awt.Dialog parent, String title, int mode) {
        super(parent, title, true); // FileDialog is always modal
        this.mode = checkMode(mode);
    }

    // java.awt.FileDialog rejects illegal modes with IllegalArgumentException;
    // that's a genuine Swing-throws case (R_match_swing_errors), not a WIP gap, so it stays real.
    private static int checkMode(int mode) {
        if (mode != LOAD && mode != SAVE) {
            throw new IllegalArgumentException("illegal file dialog mode");
        }
        return mode;
    }

    // --- Mode (REAL — trivial field, ctors depend on it) ----------------------

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = checkMode(mode);
    }

    // --- File / directory selection result (WIP — built by the LOAD crunch) ---

    public String getFile() {
        // AWT contract: null when the dialog was cancelled / not yet shown.
        return file;
    }

    public void setFile(String file) {
        // AWT: sets the initial/selected filename. In SAVE this pre-fills the
        // filename field (and becomes the download's suggested name); in LOAD
        // the browser file picker ignores it. Stored in the same field getFile()
        // reads — overwritten by the user's choice once the dialog is shown.
        this.file = file;
    }

    public File[] getFiles() {
        // AWT contract: empty (non-null) array when cancelled / not yet shown.
        return files.clone();
    }

    public String getDirectory() {
        // The per-show temp dir holding the upload(s), so migrated code that
        // does new File(getDirectory(), getFile()) reconstitutes the path.
        return dir;
    }

    /**
     * The picker is the browser's and starts wherever the browser decides, so
     * the directory is a hint nothing reads — it is the peer push that drops
     * (R_decline_effect_only). It also holds only until the next show: the field
     * is the one {@code getDirectory()} answers, and a completed LOAD overwrites
     * it with the per-show temp dir, which is AWT's shape too — there the peer
     * writes back the directory the user actually browsed to.
     *
     * @param dir {@code ""} is stored as {@code null}, as AWT's own body normalises it
     */
    public void setDirectory(String dir) {
        vaadinx.EHelper.onUnimplemented("FileDialog", "setDirectory", dir);
        this.dir = (dir != null && dir.isEmpty()) ? null : dir;
    }

    // --- Multi-select (Upload-backed; drives setMaxFiles in buildLoadContent) -

    public boolean isMultipleMode() {
        return multipleMode;
    }

    public void setMultipleMode(boolean multipleMode) {
        this.multipleMode = multipleMode;
    }

    // --- Filename filter (enforced server-side on each uploaded file) ---------
    //
    // java.awt.FilenameFilter is an opaque (dir, name) -> boolean callback, so
    // there's nothing to read accept-types out of for the browser picker (and
    // probing it with synthetic names is unsafe — a content-sniffing filter
    // rejects the probe). Instead we run the real filter against each real
    // uploaded file in buildLoadContent's success callback: matches stage,
    // non-matches are discarded with a notice. Faithful in outcome (the app
    // never receives a file the filter would reject), if not in mechanism (the
    // browser picker can't be pre-narrowed). SAVE ignores the filter — no picker.

    public java.io.FilenameFilter getFilenameFilter() {
        return filter;
    }

    public void setFilenameFilter(java.io.FilenameFilter filter) {
        this.filter = filter;
    }

    // --- Blocking modal show: build content, park, resolve result ------------

    /**
     * Populate the modal dialog with mode-appropriate content (LOAD: a browser
     * {@code Upload} + OK/Cancel; SAVE: a filename {@code TextField} +
     * Save/Cancel), then delegate to {@link vaadinx.awt.Dialog#setVisible(boolean)}
     * — which, since FileDialog is always modal, parks the calling virtual
     * thread until a button handler or a peer-originated close disposes the
     * dialog. After the park unwinds, {@link #finalizeResult()} promotes the
     * outcome to the result fields (and, for SAVE, opens the synthetic download
     * dialog). Requires a virtual-thread (EHelper.callSwing) context per the
     * inherited modal-park contract.
     *
     * <p>The blocking applies to the <em>name pick</em> only, not to writing:
     * SAVE returns as soon as the user clicks Save, so the app can write the
     * file (synchronously or on a background thread) with no park in the way —
     * the user pulls the bytes from the separate, non-modal download dialog
     * whenever the write is done. See the class header for why a "block until
     * written" SAVE would deadlock.
     */
    @Override
    public void setVisible(boolean b) {
        if (b) {
            withPeer(p -> {
                if (mode == SAVE) {
                    buildSaveContent();
                } else {
                    buildLoadContent();
                }
            });
        }
        super.setVisible(b);
        if (b) {
            finalizeResult();
        }
    }

    private void buildLoadContent() {
        approved = false;
        file = null;
        dir = null;
        files = new File[0];

        com.vaadin.flow.component.dialog.Dialog peerDialog =
                (com.vaadin.flow.component.dialog.Dialog) getPeer();
        peerDialog.removeAll();

        Button ok = new Button("OK");
        ok.setEnabled(false); // nothing to approve until at least one upload lands
        Button cancel = new Button("Cancel");

        // FilenameFilter is an opaque (dir, name) -> boolean — adapt it straight
        // through to the shared upload wiring. AWT's filter exposes no extension
        // list, so there's no browser-picker accept-hint to pass (null).
        java.util.function.BiPredicate<File, String> accept =
                filter == null ? null : filter::accept;
        staged = vaadinx.BrowserFileTransfer.wireUpload(peerDialog, multipleMode, accept, null, ok);

        // OK / Cancel dispose the dialog, unparking the VT blocked in
        // super.setVisible(true). dispose() fires WINDOW_CLOSED (a Swing-side
        // event), so the handlers funnel through EHelper.callSwing per R_callswing_envelope — these
        // are raw Vaadin Buttons and don't get the envelope for free the way
        // emulator JButtons do.
        ok.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            approved = true;
            dispose();
        }));
        cancel.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            approved = false;
            dispose();
        }));

        peerDialog.add(new HorizontalLayout(ok, cancel));
    }

    // --- SAVE: modal filename pick (this dialog), then a synthetic download ---

    private void buildSaveContent() {
        approved = false;
        chosenSaveName = null;

        com.vaadin.flow.component.dialog.Dialog peerDialog =
                (com.vaadin.flow.component.dialog.Dialog) getPeer();
        peerDialog.removeAll();

        TextField nameField = new TextField("File name");
        // AWT: setFile() supplies the initial name; default to "untitled".
        nameField.setValue(file != null && !file.isBlank() ? file : "untitled");

        Button save = new Button("Save");
        Button cancel = new Button("Cancel");

        // Save / Cancel dispose the dialog, unparking the VT blocked in
        // super.setVisible(true). dispose() fires WINDOW_CLOSED (a Swing-side
        // event), so the handlers funnel through EHelper.callSwing per R_callswing_envelope.
        save.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            approved = true;
            chosenSaveName = nameField.getValue();
            dispose();
        }));
        cancel.addClickListener(e -> vaadinx.EHelper.callSwing(() -> {
            approved = false;
            dispose();
        }));

        HorizontalLayout buttons = new HorizontalLayout(save, cancel);
        peerDialog.add(nameField, buttons);
    }

    private void finalizeResult() {
        if (mode == SAVE) {
            finalizeSaveResult();
        } else {
            finalizeLoadResult();
        }
    }

    private void finalizeLoadResult() {
        if (approved && !staged.isEmpty()) {
            files = staged.toArray(new File[0]);
            file = files[0].getName();
            dir = files[0].getParent();
        } else {
            // Cancelled (Cancel / X / ESC) — AWT contract: getFile() == null.
            files = new File[0];
            file = null;
            dir = null;
        }
    }

    private void finalizeSaveResult() {
        if (!approved) {
            // Cancelled — AWT contract: getFile() == null, app writes nothing.
            files = new File[0];
            file = null;
            dir = null;
            return;
        }
        // Hand back a temp path in a fresh per-show dir; the app writes to it
        // after setVisible returns. We don't create the file — the app's
        // FileOutputStream does — we only own the directory. The synthetic,
        // non-modal download affordance (with the auto-watching size readout)
        // lets the user pull the bytes once their app has finished writing.
        File target = vaadinx.BrowserFileTransfer.newSaveTarget(chosenSaveName);
        file = target.getName();
        dir = target.getParent();
        files = new File[] { target };
        vaadinx.BrowserFileTransfer.openDownloadDialog(target, file);
    }
}
