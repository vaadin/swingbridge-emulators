/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JFileChooser
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// ============================================================================
// Emulator-only, NO surrogate: like
// JOptionPane (SD_no_sjoptionpane/D_joptionpane) it composes an internal modal JDialog hosting a Vaadin
// Upload directly — there is no Swing dir-tree to reproduce in a browser.
//
// The upload->temp-File (LOAD) and temp-File->download (SAVE) transfer
// mechanics live in BrowserFileTransfer; JFileChooser reuses them,
// adding the richer surface (int show*Dialog return, file-selection mode,
// javax.swing FileFilter, approve-button text). It can't share by inheritance
// (FileDialog is-a Dialog; JFileChooser is-a JComponent), so park + buttons +
// result promotion stay here while transfer mechanics are delegated.
//
// CONFIRMED DEFERRALS (R_match_swing_errors (c) — WARN-drop, never throw):
//  - setAccessory(JComponent) / embedded-as-panel       -> WARN-drop
//  - DIRECTORIES_ONLY                                    -> WARN on set (no browser dir-upload)
//  - FileSystemView                                       -> not exposed
//  - opaque FileFilter browser pre-narrow                -> server-side enforce only
//    (FileNameExtensionFilter additionally narrows the picker)
// getCurrentDirectory round-trips its field (faithful, no server FS browse).
//
// THE ONE THROW (R_match_swing_errors case (4), D_showdialog_throws): showDialog. Its CUSTOM_DIALOG declares no
// direction, and LOAD and SAVE are different browser machinery that has to be
// committed before the dialog opens, so there is nothing for the emulator to
// pick from — observation could tell us afterwards that the app WROTE, but
// nothing can retroactively upload a file the browser never sent, so LOAD can
// only be served by deciding up front. Only the migrator, reading the call
// site, has that information. @Deprecated is the other half: the throw fires
// only on paths someone walks, the deprecation names every call site at compile
// time. WarnInventoryTests cannot catch this one — a throw is not a WARN.
// ============================================================================

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import vaadinx.BrowserFileTransfer;
import vaadinx.EHelper;
import vaadinx.awt.Component;
import vaadinx.awt.Frame;
import vaadinx.awt.Window;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileView;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Emulator for {@link javax.swing.JFileChooser}. Emulator-only host (no
 * surrogate): {@code showOpenDialog} composes an internal modal {@link JDialog}
 * hosting a Vaadin {@code Upload}, parks the calling virtual thread via the
 * JDialog modal machinery, and returns {@link #APPROVE_OPTION} /
 * {@link #CANCEL_OPTION} once the user uploads + confirms (or cancels).
 * {@code showSaveDialog} picks a name, hands back a per-session temp target via
 * {@link #getSelectedFile()}, and opens the synthetic download affordance.
 * Selection getters return {@code java.io.File}s pointing at per-session temp
 * files. Transfer mechanics are shared with {@link vaadinx.awt.FileDialog} via
 * {@link BrowserFileTransfer}.
 */
public class JFileChooser extends JComponent implements Accessible {

    // --- show*Dialog return values --------------------------------------------
    public static final int CANCEL_OPTION = 1;
    public static final int APPROVE_OPTION = 0;
    public static final int ERROR_OPTION = -1;

    // --- dialog type ----------------------------------------------------------
    public static final int OPEN_DIALOG = 0;
    public static final int SAVE_DIALOG = 1;
    public static final int CUSTOM_DIALOG = 2;

    // --- file selection mode --------------------------------------------------
    public static final int FILES_ONLY = 0;
    public static final int DIRECTORIES_ONLY = 1;
    public static final int FILES_AND_DIRECTORIES = 2;

    // --- action commands ------------------------------------------------------
    // The ActionEvent commands the JDK's own approve/cancel buttons carry, which
    // an app compares against in its ActionListener.
    public static final String CANCEL_SELECTION = "CancelSelection";
    public static final String APPROVE_SELECTION = "ApproveSelection";

    // --- bound property names -------------------------------------------------
    // The JDK's 18 bound-property names, verbatim including the inconsistent
    // casing (some are "FooChangedProperty", some plain "fooChanged") — these are
    // the strings a PropertyChangeListener registers under, so a "tidier" spelling
    // would be a silent behaviour change. The fires themselves landed in
    // D_reverse_fanout_rows, including the pick-time ones doLoad / doSave used to
    // write as fields. Their absence here was a separate, compile-level bug
    // (D_missing_constants): addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, l)
    // did not compile at all.
    public static final String SELECTED_FILE_CHANGED_PROPERTY = "SelectedFileChangedProperty";
    public static final String SELECTED_FILES_CHANGED_PROPERTY = "SelectedFilesChangedProperty";
    public static final String DIRECTORY_CHANGED_PROPERTY = "directoryChanged";
    public static final String MULTI_SELECTION_ENABLED_CHANGED_PROPERTY = "MultiSelectionEnabledChangedProperty";
    public static final String FILE_SYSTEM_VIEW_CHANGED_PROPERTY = "FileSystemViewChanged";
    public static final String FILE_VIEW_CHANGED_PROPERTY = "fileViewChanged";
    public static final String FILE_HIDING_CHANGED_PROPERTY = "FileHidingChanged";
    public static final String FILE_FILTER_CHANGED_PROPERTY = "fileFilterChanged";
    public static final String FILE_SELECTION_MODE_CHANGED_PROPERTY = "fileSelectionChanged";
    public static final String ACCESSORY_CHANGED_PROPERTY = "AccessoryChangedProperty";
    public static final String ACCEPT_ALL_FILE_FILTER_USED_CHANGED_PROPERTY = "acceptAllFileFilterUsedChanged";
    public static final String DIALOG_TITLE_CHANGED_PROPERTY = "DialogTitleChangedProperty";
    public static final String DIALOG_TYPE_CHANGED_PROPERTY = "DialogTypeChangedProperty";
    public static final String APPROVE_BUTTON_TEXT_CHANGED_PROPERTY = "ApproveButtonTextChangedProperty";
    public static final String APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY = "ApproveButtonToolTipTextChangedProperty";
    public static final String APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY = "ApproveButtonMnemonicChangedProperty";
    public static final String CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY = "ChoosableFileFilterChangedProperty";
    public static final String CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY = "ControlButtonsAreShownChangedProperty";

    // --- State ----------------------------------------------------------------
    // Defaults are the JDK's field initialisers. selectedFiles is nullable-for-empty
    // as the JDK's is, because the *event* reports the field: setSelectedFiles(null)
    // fires a null new value on the desktop, and a File[0] here would report a
    // payload real Swing never sends. getSelectedFiles() normalises, as the JDK's does.
    private File currentDirectory;
    private File selectedFile;
    private File[] selectedFiles;
    private boolean multiSelectionEnabled;
    private int fileSelectionMode = FILES_ONLY;
    private int dialogType = OPEN_DIALOG;
    private String dialogTitle;
    private String approveButtonText;
    private String approveButtonToolTipText;
    private int approveButtonMnemonic;
    private JComponent accessory;
    private FileView fileView;
    private boolean controlsShown = true;
    private boolean useFileHiding = true;
    private FileFilter fileFilter;
    private final List<FileFilter> choosableFilters =
            new ArrayList<>();
    private boolean acceptAllFileFilterUsed = true;

    // Per-show scratch: staged is the list BrowserFileTransfer grows as accepted
    // uploads land; approved records the confirm (true) vs cancel/X/ESC (false).
    private List<File> staged = List.of();
    private boolean approved;

    // --- Constructors (REAL — peer is a vestigial Div like JOptionPane; the
    // real UI is the internal JDialog built at show* time) ---------------------

    public JFileChooser() {
        super(Div.class, Div::new);
        // JDK's JFileChooser defaults the current directory to the platform
        // "home" via FileSystemView; we have no server FS to browse, but a
        // non-null getCurrentDirectory() keeps the common
        // getCurrentDirectory().getAbsolutePath() idiom from NPEing.
        this.currentDirectory = new File(System.getProperty("user.home", "."));
    }

    public JFileChooser(String currentDirectoryPath) {
        this();
        if (currentDirectoryPath != null) {
            this.currentDirectory = new File(currentDirectoryPath);
        }
    }

    public JFileChooser(File currentDirectory) {
        this();
        if (currentDirectory != null) {
            this.currentDirectory = currentDirectory;
        }
    }

    // --- Blocking show --------------------------------------------------------

    public int showOpenDialog(Component parent) {
        // Through the setter, as the JDK's showOpenDialog goes, so the type change
        // is announced. Note what setDialogType's guard-first order buys here: on a
        // chooser already OPEN_DIALOG (the default) it returns early and the
        // setApproveButtonText(null) reset never runs, so a label set before this
        // call survives — which is what makes showDialog's triage advice work.
        setDialogType(OPEN_DIALOG);
        return doLoad(parent, approveButtonText != null ? approveButtonText : "Open");
    }

    public int showSaveDialog(Component parent) {
        setDialogType(SAVE_DIALOG);
        return doSave(parent, approveButtonText != null ? approveButtonText : "Save");
    }

    /**
     * Unsupported in the browser, always throws — the sole throw on this class
     * (R_match_swing_errors throw-trigger case (4), D_showdialog_throws). {@code CUSTOM_DIALOG} declares no
     * LOAD/SAVE direction, and the two are different browser mechanisms that
     * must be committed before the dialog opens, so no default is defensible:
     * mapping it to LOAD silently produces an unreachable file whenever the app
     * was naming an output. Nothing is mutated before the throw — the call is a
     * no-op plus an exception. {@code setDialogType(CUSTOM_DIALOG)} itself stays
     * faithful and round-trips; only the show is refused.
     *
     * @deprecated triage the call site to {@link #showOpenDialog} (the app reads
     *             the file) or {@link #showSaveDialog} (the app writes it). To
     *             keep a custom label, call {@link #setDialogType} with the
     *             matching type and only then {@link #setApproveButtonText}:
     *             changing the type resets the label, as in the JDK. The
     *             deprecation is deliberate divergence from the JDK, and is what
     *             makes an untriaged call site visible at <em>compile</em> time
     *             rather than only when someone walks that path.
     * @throws IllegalStateException always, carrying the triage rule.
     */
    @Deprecated
    public int showDialog(Component parent, String approveButtonText) {
        throw new IllegalStateException(SHOW_DIALOG_UNSUPPORTED);
    }

    /** Message of {@link #showDialog}'s throw — it has to teach the fix, so it spells it out. */
    private static final String SHOW_DIALOG_UNSUPPORTED = """
            JFileChooser.showDialog() cannot be emulated: it declares no direction. \
            On the desktop every show* variant merely returns a File and the app does the I/O; \
            in a browser LOAD and SAVE are different mechanisms that must be chosen BEFORE the \
            dialog opens - LOAD puts up an Upload, SAVE allocates a temp target and offers a \
            download link. CUSTOM_DIALOG declares neither, and neither SB-Emulators nor the end user can \
            tell which this call site meant. \
            FIX at this call site, from what the surrounding code does with getSelectedFile(): \
            the app READS it -> showOpenDialog(parent); the app WRITES it -> showSaveDialog(parent). \
            To keep a custom button label, call setDialogType(SAVE_DIALOG) or \
            setDialogType(OPEN_DIALOG) first and setApproveButtonText(..) after it - changing \
            the dialog type resets the label, on the desktop too - then the show call. \
            If the direction is decided at runtime, branch and call the matching variant per branch.\
            """;

    /**
     * LOAD: build a modal JDialog hosting an {@code Upload}, park on it, and
     * promote the staged temp uploads to the selection on approve.
     */
    private int doLoad(Component parent, String approveLabel) {
        approved = false;
        // No pre-show clear of the selection: the JDK leaves the previous pick in
        // place until the user commits, and both exits below commit through
        // setSelectedFiles, so state and events stay in step either way. Clearing
        // here would mutate an observable property with nothing announcing it.

        JDialog dialog = createJDialog(parent);
        // A write, so the dialog's lazy peer and these raw Vaadin components are built on the UI thread.
        dialog.withPeer(p -> {
            Dialog peerDialog = (Dialog) p;

            Button approve = new Button(approveLabel);
            approve.setEnabled(false); // nothing to approve until an upload lands
            Button cancel = new Button("Cancel");

            staged = BrowserFileTransfer.wireUpload(
                    peerDialog, multiSelectionEnabled, buildAccept(), extensionHint(), approve);

            // Raw Vaadin Buttons: dispose() fires the Swing-side WINDOW_CLOSED, so
            // the handlers funnel through EHelper.callSwing per R_callswing_envelope.
            approve.addClickListener(e -> EHelper.callSwing(() -> {
                approved = true;
                dialog.dispose();
            }));
            cancel.addClickListener(e -> EHelper.callSwing(() -> {
                approved = false;
                dialog.dispose();
            }));

            peerDialog.add(new HorizontalLayout(approve, cancel));
        });

        dialog.setVisible(true); // parks the VT until a button / X disposes
        fireDialogIsClosing(dialog);

        // Through setSelectedFiles, which is where the JDK's UI commits a pick, so
        // a migrator's SELECTED_FILE(S)_CHANGED listener fires on the one event that
        // matters for a file chooser. The DIRECTORY_CHANGED cascade comes with it.
        if (approved && !staged.isEmpty()) {
            setSelectedFiles(staged.toArray(new File[0]));
            return APPROVE_OPTION;
        }
        // Cancel / X / ESC — JDK contract: CANCEL_OPTION, selection cleared.
        setSelectedFiles(null);
        return CANCEL_OPTION;
    }

    /**
     * The JDK's {@code showDialog} fires this the moment its modal dialog comes
     * down, carrying the dialog as the old value and {@code null} as the new — the
     * signal an app uses to tear down anything it hung off the chooser. Ours has no
     * single {@code showDialog} to host it (that overload throws, D_showdialog_throws), so both
     * directions fire it at the same point in their own flow.
     */
    private void fireDialogIsClosing(JDialog dialog) {
        firePropertyChange("JFileChooserDialogIsClosingProperty", dialog, null);
    }

    /**
     * SAVE: pick a filename in a modal JDialog (blocks only for the name), hand
     * back a per-session temp target, and open the synthetic download dialog so
     * the user can pull the bytes once their app has written the file. See
     * {@link vaadinx.awt.FileDialog}'s class header for why a "block until
     * written" SAVE would deadlock.
     */
    private int doSave(Component parent, String approveLabel) {
        approved = false;

        JDialog dialog = createJDialog(parent);
        // setSelectedFile supplies the initial name; default to "untitled".
        String initialName = selectedFile != null && !selectedFile.getName().isBlank()
                ? selectedFile.getName() : "untitled";
        final String[] chosenName = { null };
        // A write, so the dialog's lazy peer and these raw Vaadin components are built on the UI thread.
        dialog.withPeer(p -> {
            Dialog peerDialog = (Dialog) p;

            TextField nameField = new TextField("File name");
            nameField.setValue(initialName);

            Button save = new Button(approveLabel);
            Button cancel = new Button("Cancel");

            save.addClickListener(e -> EHelper.callSwing(() -> {
                approved = true;
                chosenName[0] = nameField.getValue();
                dialog.dispose();
            }));
            cancel.addClickListener(e -> EHelper.callSwing(() -> {
                approved = false;
                dialog.dispose();
            }));

            peerDialog.add(nameField, new HorizontalLayout(save, cancel));
        });

        dialog.setVisible(true); // parks the VT for the name pick only
        fireDialogIsClosing(dialog);

        if (!approved) {
            setSelectedFiles(null);
            return CANCEL_OPTION;
        }
        File target = BrowserFileTransfer.newSaveTarget(chosenName[0]);
        setSelectedFiles(new File[] { target });
        BrowserFileTransfer.openDownloadDialog(target, target.getName());
        return APPROVE_OPTION;
    }

    /** Build the modal JDialog owned by {@code parent}'s window ancestor. */
    private JDialog createJDialog(Component parent) {
        String title = dialogTitle != null ? dialogTitle : defaultTitle();
        Window owner = windowAncestor(parent);
        JDialog dialog;
        if (owner instanceof Frame f) {
            dialog = new JDialog(f, title, true);
        } else if (owner instanceof vaadinx.awt.Dialog d) {
            dialog = new JDialog(d, title, true);
        } else {
            dialog = new JDialog((Frame) null, title, true);
        }
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        return dialog;
    }

    private String defaultTitle() {
        return switch (dialogType) {
            case SAVE_DIALOG -> "Save";
            case OPEN_DIALOG -> "Open";
            // CUSTOM_DIALOG is unreachable from the show* path (showDialog throws,
            // D_showdialog_throws) but setDialogType(CUSTOM_DIALOG) still round-trips, so the
            // JDK-shaped switch keeps its arm rather than growing a hole.
            default -> approveButtonText != null ? approveButtonText : "";
        };
    }

    private static Window windowAncestor(Component c) {
        while (c != null) {
            if (c instanceof Window w) return w;
            c = c.getParent();
        }
        return null;
    }

    // Adapt javax.swing FileFilter (File -> boolean) to the shared wiring's
    // (dir, name) -> boolean predicate; null filter accepts all.
    private BiPredicate<File, String> buildAccept() {
        FileFilter f = fileFilter;
        return f == null ? null : (dir, name) -> f.accept(new File(dir, name));
    }

    // FileNameExtensionFilter exposes its extensions, so we can pre-narrow the
    // browser picker; opaque filters can't, so the picker shows everything and
    // the server-side accept() is the only gate.
    private List<String> extensionHint() {
        if (fileFilter instanceof FileNameExtensionFilter ef) {
            return Arrays.asList(ef.getExtensions());
        }
        return null;
    }

    // --- Selection result -----------------------------------------------------

    public File getSelectedFile() {
        return selectedFile;
    }

    public void setSelectedFile(File file) {
        File oldValue = selectedFile;
        selectedFile = file;
        if (selectedFile != null
                && file.isAbsolute()
                && !Objects.equals(file.getParentFile(), currentDirectory)) {
            // The JDK reparents to the file's directory here, asking its
            // FileSystemView whether the current directory is already the parent.
            // We have no FileSystemView (no server FS to browse), so the test is a
            // plain parent comparison — the cascade, and its DIRECTORY_CHANGED
            // event, are what matter and both survive it.
            setCurrentDirectory(file.getParentFile());
        }
        // ensureFileIsVisible is a view concern with no browser counterpart and no
        // SB-Emulators counterpart to call (R_match_swing_errors (b)); the fire below is not.
        firePropertyChange(SELECTED_FILE_CHANGED_PROPERTY, oldValue, selectedFile);
    }

    public File[] getSelectedFiles() {
        return selectedFiles == null ? new File[0] : selectedFiles.clone();
    }

    public void setSelectedFiles(File[] selectedFiles) {
        File[] oldValue = this.selectedFiles;
        if (selectedFiles == null || selectedFiles.length == 0) {
            selectedFiles = null;
            this.selectedFiles = null;
            setSelectedFile(null);
        } else {
            this.selectedFiles = selectedFiles.clone();
            setSelectedFile(this.selectedFiles[0]);
        }
        // The JDK carries the *argument* (nulled for empty), not the stored clone.
        firePropertyChange(SELECTED_FILES_CHANGED_PROPERTY, oldValue, selectedFiles);
    }

    // --- Multi-select (drives Upload.setMaxFiles) -----------------------------

    public boolean isMultiSelectionEnabled() {
        return multiSelectionEnabled;
    }

    public void setMultiSelectionEnabled(boolean b) {
        if (multiSelectionEnabled == b) {
            return;
        }
        boolean oldValue = multiSelectionEnabled;
        multiSelectionEnabled = b;
        firePropertyChange(MULTI_SELECTION_ENABLED_CHANGED_PROPERTY, oldValue, multiSelectionEnabled);
    }

    // --- Current directory (round-trips the field; no server FS browse) -------

    public File getCurrentDirectory() {
        return currentDirectory;
    }

    public void setCurrentDirectory(File dir) {
        File oldValue = currentDirectory;
        // No server-side directory to start a browser picker in, but the getter
        // round-trips so getCurrentDirectory().getXxx() idioms keep working. The
        // JDK's `!dir.exists()` rejection and its walk up to a traversable parent
        // are deliberately not reproduced: they interrogate a file system the
        // browser picker never reads, and dropping them is what lets a path the
        // server has never seen round-trip.
        if (dir == null) {
            dir = new File(System.getProperty("user.home", "."));
        }
        if (currentDirectory != null && currentDirectory.equals(dir)) {
            return;                                  // the JDK's changed-guard
        }
        currentDirectory = dir;
        firePropertyChange(DIRECTORY_CHANGED_PROPERTY, oldValue, currentDirectory);
    }

    // --- File selection mode (FILES only; DIRECTORIES_ONLY unsupported) -------

    public int getFileSelectionMode() {
        return fileSelectionMode;
    }

    public void setFileSelectionMode(int mode) {
        // Guard before validate, as the JDK does — so re-setting the current mode
        // returns early and never reaches the check. Reversing the two would throw
        // where real Swing does not.
        if (fileSelectionMode == mode) {
            return;
        }
        if (mode != FILES_ONLY && mode != DIRECTORIES_ONLY && mode != FILES_AND_DIRECTORIES) {
            throw new IllegalArgumentException("Incorrect Mode for file selection: " + mode);
        }
        int oldValue = fileSelectionMode;
        fileSelectionMode = mode;
        if (mode == DIRECTORIES_ONLY) {
            // No browser equivalent of picking a bare directory handle — the
            // upload path can only deliver files. Stored for round-trip, but the
            // show* path always behaves as a file pick.
            EHelper.onUnimplemented("JFileChooser", "setFileSelectionMode(DIRECTORIES_ONLY pick)", mode);
        }
        firePropertyChange(FILE_SELECTION_MODE_CHANGED_PROPERTY, oldValue, fileSelectionMode);
    }

    public boolean isFileSelectionEnabled() {
        return fileSelectionMode == FILES_ONLY || fileSelectionMode == FILES_AND_DIRECTORIES;
    }

    public boolean isDirectorySelectionEnabled() {
        return fileSelectionMode == DIRECTORIES_ONLY || fileSelectionMode == FILES_AND_DIRECTORIES;
    }

    // --- Dialog title / type --------------------------------------------------

    public String getDialogTitle() {
        return dialogTitle;
    }

    public void setDialogTitle(String dialogTitle) {
        String oldValue = this.dialogTitle;
        this.dialogTitle = dialogTitle;
        // The JDK also retitles a dialog that is already up. Ours is built per
        // show* call from this field, so there is nothing live to retitle.
        firePropertyChange(DIALOG_TITLE_CHANGED_PROPERTY, oldValue, dialogTitle);
    }

    public int getDialogType() {
        return dialogType;
    }

    public void setDialogType(int dialogType) {
        // Guard first, then validate, then the OPEN/SAVE reset — the JDK's order.
        if (this.dialogType == dialogType) {
            return;
        }
        if (dialogType != OPEN_DIALOG && dialogType != SAVE_DIALOG && dialogType != CUSTOM_DIALOG) {
            throw new IllegalArgumentException("Incorrect Dialog Type: " + dialogType);
        }
        int oldValue = this.dialogType;
        this.dialogType = dialogType;
        if (dialogType == OPEN_DIALOG || dialogType == SAVE_DIALOG) {
            setApproveButtonText(null);              // cascades its own event
        }
        firePropertyChange(DIALOG_TYPE_CHANGED_PROPERTY, oldValue, dialogType);
    }

    // --- File filter ----------------------------------------------------------

    public FileFilter getFileFilter() {
        return fileFilter;
    }

    public void setFileFilter(FileFilter filter) {
        FileFilter oldValue = fileFilter;
        fileFilter = filter;
        // The JDK prunes an existing selection the new filter rejects, cascading
        // setSelectedFile(s). Pure Java over File objects, so it survives here.
        if (filter != null) {
            if (isMultiSelectionEnabled() && selectedFiles != null && selectedFiles.length > 0) {
                List<File> kept = new ArrayList<>();
                boolean failed = false;
                for (File file : selectedFiles) {
                    if (filter.accept(file)) {
                        kept.add(file);
                    } else {
                        failed = true;
                    }
                }
                if (failed) {
                    setSelectedFiles(kept.isEmpty() ? null : kept.toArray(new File[0]));
                }
            } else if (selectedFile != null && !filter.accept(selectedFile)) {
                setSelectedFile(null);
            }
        }
        firePropertyChange(FILE_FILTER_CHANGED_PROPERTY, oldValue, fileFilter);
    }

    public void addChoosableFileFilter(FileFilter filter) {
        // The whole body sits inside the guard, as the JDK's does: adding a filter
        // that is already choosable fires nothing.
        if (filter != null && !choosableFilters.contains(filter)) {
            FileFilter[] oldValue = getChoosableFileFilters();
            choosableFilters.add(filter);
            firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
            // Adopted as the active filter only when it is the *first* one — the
            // JDK's size()==1 half of the condition, not merely "none set yet".
            if (fileFilter == null && choosableFilters.size() == 1) {
                setFileFilter(filter);
            }
        }
    }

    public boolean removeChoosableFileFilter(FileFilter f) {
        int index = choosableFilters.indexOf(f);
        if (index < 0) {
            return false;
        }
        if (getFileFilter() == f) {
            // The JDK prefers the accept-all filter here; ours is always null (see
            // getAcceptAllFileFilter's absence below), so the fallback chain starts
            // at its second arm.
            if (index > 0) {
                setFileFilter(choosableFilters.get(0));
            } else if (choosableFilters.size() > 1) {
                setFileFilter(choosableFilters.get(1));
            } else {
                setFileFilter(null);
            }
        }
        FileFilter[] oldValue = getChoosableFileFilters();
        choosableFilters.remove(index);
        firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
        return true;
    }

    public void resetChoosableFileFilters() {
        FileFilter[] oldValue = getChoosableFileFilters();
        setFileFilter(null);
        choosableFilters.clear();
        // The JDK re-adds the accept-all filter when it is in use. There is no
        // accept-all instance here — that filter is a FileChooserUI product and L&F
        // is out (R_match_swing_errors (b)) — so the list ends empty rather than holding one entry.
        firePropertyChange(CHOOSABLE_FILE_FILTER_CHANGED_PROPERTY, oldValue, getChoosableFileFilters());
    }

    public FileFilter[] getChoosableFileFilters() {
        return choosableFilters.toArray(new FileFilter[0]);
    }

    public boolean isAcceptAllFileFilterUsed() {
        return acceptAllFileFilterUsed;
    }

    public void setAcceptAllFileFilterUsed(boolean b) {
        // The browser picker shows all files unless an extension hint narrows it,
        // so this flag has no behavioural counterpart. The JDK's remove/add of the
        // accept-all filter degenerates to nothing here, for the reason given in
        // resetChoosableFileFilters — but the event is still owed, unguarded as the
        // JDK leaves it.
        boolean oldValue = acceptAllFileFilterUsed;
        acceptAllFileFilterUsed = b;
        firePropertyChange(ACCEPT_ALL_FILE_FILTER_USED_CHANGED_PROPERTY, oldValue, acceptAllFileFilterUsed);
    }

    // --- File hiding ----------------------------------------------------------

    public boolean isFileHidingEnabled() {
        return useFileHiding;
    }

    public void setFileHidingEnabled(boolean b) {
        // The browser owns the picker's listing, so hidden-file policy is the OS
        // file dialog's. Unguarded fire, as the JDK leaves it.
        boolean oldValue = useFileHiding;
        useFileHiding = b;
        EHelper.onUnimplemented("JFileChooser", "setFileHidingEnabled(listing)", b);
        firePropertyChange(FILE_HIDING_CHANGED_PROPERTY, oldValue, useFileHiding);
    }

    // --- Accessory / FileView / control buttons (state + event; effect declined) --

    public JComponent getAccessory() {
        return accessory;
    }

    /**
     * @param newAccessory the accessory component. Typed {@code vaadinx.swing}, not
     *     {@code javax.swing}: the JDK declares this signature, so per R_no_vaadin_in_api limb 1 it
     *     must speak the emulator's types. It took the JDK type until D_reverse_fanout_rows, which
     *     made the method impossible to call from import-swapped code at all — a
     *     migrator's {@code JPanel} is a {@code vaadinx.swing.JPanel}.
     */
    public void setAccessory(JComponent newAccessory) {
        JComponent oldValue = accessory;
        accessory = newAccessory;
        EHelper.onUnimplemented("JFileChooser", "setAccessory(render)", newAccessory);
        firePropertyChange(ACCESSORY_CHANGED_PROPERTY, oldValue, accessory);
    }

    public FileView getFileView() {
        return fileView;
    }

    public void setFileView(FileView fileView) {
        FileView oldValue = this.fileView;
        this.fileView = fileView;
        // A FileView supplies icons and type descriptions to the chooser's own file
        // list. The browser renders that list, so nothing consults this.
        EHelper.onUnimplemented("JFileChooser", "setFileView(render)", fileView);
        firePropertyChange(FILE_VIEW_CHANGED_PROPERTY, oldValue, fileView);
    }

    public boolean getControlButtonsAreShown() {
        return controlsShown;
    }

    public void setControlButtonsAreShown(boolean b) {
        if (controlsShown == b) {
            return;
        }
        boolean oldValue = controlsShown;
        controlsShown = b;
        // Our approve/cancel buttons are the dialog's only way out; hiding them
        // would strand the parked virtual thread.
        EHelper.onUnimplemented("JFileChooser", "setControlButtonsAreShown(hide)", b);
        firePropertyChange(CONTROL_BUTTONS_ARE_SHOWN_CHANGED_PROPERTY, oldValue, controlsShown);
    }

    // --- Approve button text / tooltip / mnemonic -----------------------------

    public String getApproveButtonText() {
        return approveButtonText;
    }

    public void setApproveButtonText(String approveButtonText) {
        // Reference inequality, as the JDK writes it: two equal-but-distinct
        // Strings fire, two interned-equal ones do not.
        if (this.approveButtonText == approveButtonText) {
            return;
        }
        String oldValue = this.approveButtonText;
        this.approveButtonText = approveButtonText;
        firePropertyChange(APPROVE_BUTTON_TEXT_CHANGED_PROPERTY, oldValue, approveButtonText);
    }

    public String getApproveButtonToolTipText() {
        return approveButtonToolTipText;
    }

    public void setApproveButtonToolTipText(String toolTipText) {
        if (approveButtonToolTipText == toolTipText) {   // reference guard, as above
            return;
        }
        String oldValue = approveButtonToolTipText;
        approveButtonToolTipText = toolTipText;
        EHelper.onUnimplemented("JFileChooser", "setApproveButtonToolTipText(tooltip)", toolTipText);
        firePropertyChange(APPROVE_BUTTON_TOOL_TIP_TEXT_CHANGED_PROPERTY, oldValue, approveButtonToolTipText);
    }

    public int getApproveButtonMnemonic() {
        return approveButtonMnemonic;
    }

    public void setApproveButtonMnemonic(int mnemonic) {
        if (approveButtonMnemonic == mnemonic) {
            return;
        }
        int oldValue = approveButtonMnemonic;
        approveButtonMnemonic = mnemonic;
        EHelper.onUnimplemented("JFileChooser", "setApproveButtonMnemonic(accelerator)", mnemonic);
        firePropertyChange(APPROVE_BUTTON_MNEMONIC_CHANGED_PROPERTY, oldValue, approveButtonMnemonic);
    }

    public void setApproveButtonMnemonic(char mnemonic) {
        // The JDK upper-cases through the int overload, so the stored value and the
        // event's payload are the virtual-key code, never the char as passed.
        int vk = mnemonic;
        if (vk >= 'a' && vk <= 'z') {
            vk -= ('a' - 'A');
        }
        setApproveButtonMnemonic(vk);
    }

    public AccessibleContext getAccessibleContext() {
        EHelper.onUnimplemented("JFileChooser", "getAccessibleContext");
        return null;
    }
}
