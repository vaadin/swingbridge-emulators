/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import com.vaadin.flow.server.streams.FileDownloadHandler;
import com.vaadin.flow.server.streams.InputStreamDownloadHandler;
import com.vaadin.flow.server.streams.UploadHandler;

/**
 * INTERNAL — do not use. Not part of the emulated Swing/AWT API surface and has
 * no stability guarantee.
 *
 * <p>Shared browser file-transfer mechanics behind the two file-pick hosts —
 * {@link vaadinx.awt.FileDialog} and {@link vaadinx.swing.JFileChooser}. Both
 * bridge the {@code java.io.File} semantic gap the same way: a LOAD stages a
 * browser {@code Upload} into a per-session temp file; a SAVE hands back a temp
 * target path and offers a synthetic, non-modal download affordance once the
 * app has written it. The hosts differ in how they park (FileDialog is-a modal
 * {@link vaadinx.awt.Dialog}; JFileChooser composes an internal modal
 * {@link vaadinx.swing.JDialog} JOptionPane-style) and in their result shape,
 * so park + buttons + result promotion stay host-side; only the transfer
 * mechanics live here. Which files a SAVE actually produced — the walk of the
 * transfer directory and the order its results are shown in — is
 * {@link SaveOutputs}, kept apart because it is pure and has no Vaadin in it.
 */
public final class BrowserFileTransfer {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(BrowserFileTransfer.class);

    private BrowserFileTransfer() {
    }

    /**
     * Build the LOAD content — a browser {@link Upload} plus a server-side
     * filter-rejection notice — into {@code peerDialog}, and return the live
     * list each accepted upload stages into. The caller adds its own
     * approve/cancel buttons (labels and dispose target differ per host) and
     * reads the returned list once the modal park unwinds.
     *
     * <p>Each part is written to a per-session temp file, then the real
     * {@code accept} predicate runs against the real staged file: matches are
     * added to the list and {@code approveButton} is enabled; non-matches are
     * deleted with a notice. We can't avoid the transfer (the browser uploads
     * on selection) but we refuse to hand the app a file the filter rejects,
     * matching the desktop "can't pick it" contract. {@code acceptedExtensions},
     * when non-null, pre-narrows the browser picker (best-effort UX only — the
     * authoritative gate is always the server-side {@code accept}).
     *
     * @param peerDialog         the Vaadin dialog hosting the pick; Upload +
     *                           notice are appended to it.
     * @param multi              whether multiple files may be staged.
     * @param accept             {@code (dir, name) -> keep?}; null accepts all.
     * @param acceptedExtensions extensions (no leading dot) for the browser
     *                           accept-attribute hint; null skips the hint.
     * @param approveButton      enabled once the first file stages.
     * @return the synchronized, growing list of staged temp files (read side
     *         runs on a different request thread than the upload callback).
     */
    public static List<File> wireUpload(Dialog peerDialog, boolean multi,
            BiPredicate<File, String> accept, List<String> acceptedExtensions,
            Button approveButton) {
        List<File> staged = Collections.synchronizedList(new java.util.ArrayList<>());

        Path uploadDir = SessionTempFiles.newTransferDir();

        Upload upload = new Upload();
        upload.setMaxFiles(multi ? Integer.MAX_VALUE : 1);
        if (acceptedExtensions != null && !acceptedExtensions.isEmpty()) {
            // setAcceptedFileExtensions mandates ".ext" tokens;
            // FileNameExtensionFilter hands back bare extensions, so prefix a dot.
            String[] types = acceptedExtensions.stream()
                    .map(ext -> ext.startsWith(".") ? ext : "." + ext)
                    .toArray(String[]::new);
            upload.setAcceptedFileExtensions(types);
        }

        // Feedback channel for server-side filter rejections. Vaadin's native
        // Upload error UI only covers BROWSER-side accept-attribute rejection,
        // not a file the UploadHandler already accepted, so we surface our own.
        Span notice = new Span();

        // Modern streams UploadHandler — toFile writes each part to a temp File
        // chosen by the FileFactory, then runs the success callback via UI.access
        // (under the session lock) once the bytes are on disk. That deferred
        // callback — NOT Upload's AllFinishedEvent, which fires earlier — is the
        // real "file is ready" signal, so we stage / filter / touch the UI here.
        upload.setUploadHandler(UploadHandler.toFile(
                (metadata, savedFile) -> {
                    if (accept == null || accept.test(uploadDir.toFile(), savedFile.getName())) {
                        staged.add(savedFile);
                        approveButton.setEnabled(true);
                    } else {
                        savedFile.delete();
                        notice.setText("\"" + savedFile.getName()
                                + "\" doesn't match the file filter and was ignored.");
                    }
                },
                metadata -> uploadDir.resolve(safeName(metadata.fileName())).toFile()));

        peerDialog.add(upload, notice);
        return staged;
    }

    /**
     * Allocate a fresh per-session temp target for a SAVE under {@code name};
     * the app writes to it after the host's modal name-pick returns. We don't
     * create the file — the app's stream does — we only own the directory.
     *
     * @return the temp {@link File} the app should write to.
     */
    public static File newSaveTarget(String name) {
        Path saveDir = SessionTempFiles.newTransferDir();
        return saveDir.resolve(safeName(name)).toFile();
    }

    /**
     * The synthetic, non-modal download affordance — a side-channel the emulated
     * Swing app has no handle to (like an accessibility overlay). Opened once the
     * SAVE name-pick returns, before the app has written anything.
     *
     * <p><b>Bound to the directory, not to {@code target} (D_save_binds_download).</b> Swing never
     * promised the app writes to exactly the path it was handed: deriving is
     * routine ({@code path + ".xls"}, an extension swap, several files from one
     * pick, a library appending its own suffix), and every derivation lands a
     * <em>sibling</em>. Binding to the predicted path therefore offered a file
     * that was often never written while the real output sat beside it,
     * unreachable. So the watcher walks {@code target}'s parent — the fresh,
     * empty, per-show directory from {@link SessionTempFiles#newTransferDir()} —
     * and offers a link per file it finds, under the name the app actually
     * produced rather than the name the user typed.
     *
     * <p>The server still can't know when the app has finished writing (the write
     * may be async / background), so the human stays the completion oracle: the
     * size readouts auto-refresh on the shared per-{@link UiScheduler} cadence and
     * push live (Push is mandatory, so no manual-refresh fallback is needed), and
     * an early click that grabs a partial file is recoverable by waiting and
     * clicking again. Rows are keyed by relative path and reconciled rather than
     * rebuilt, so a library that writes {@code report.xls.tmp} and renames it
     * renders the way watching the folder in a file manager does.
     *
     * @param target the path handed to the app; only its parent directory is
     *               watched, and only its name seeds the match-first ordering.
     * @param name   the name the user typed, for the dialog title and ordering.
     */
    public static void openDownloadDialog(File target, String name) {
        UI ui = UI.getCurrent();
        File dir = target.getParentFile();
        Path root = dir == null ? null : dir.toPath();

        Dialog dl = new Dialog();
        dl.setModality(ModalityMode.MODELESS);
        dl.setHeaderTitle("Download " + name);

        Span warning = new Span("Files your app writes appear here as it produces them. "
                + "Click a link once your app has finished writing that file — clicking too early "
                + "downloads a partial file, so just wait and click again.");
        Span empty = new Span("No files available for download yet.");
        Span truncatedNotice = new Span();
        truncatedNotice.setVisible(false);
        VerticalLayout rows = new VerticalLayout();
        rows.setPadding(false);
        rows.setSpacing(false);

        // Keyed by path relative to the transfer dir, so a row survives its
        // file growing and only appears/disappears when the file does.
        Map<String, DownloadRow> byPath = new LinkedHashMap<>();
        AtomicBoolean everFound = new AtomicBoolean();

        Runnable refresh = () -> {
            SaveOutputs outputs = SaveOutputs.discover(dir, name);
            List<String> order = new ArrayList<>();
            for (File file : outputs.files()) {
                String key = root == null ? file.getName() : root.relativize(file.toPath()).toString();
                order.add(key);
                DownloadRow row = byPath.get(key);
                if (row == null) {
                    row = new DownloadRow(file, key);
                    byPath.put(key, row);
                }
                row.refreshSize();
            }
            byPath.keySet().retainAll(order);
            rows.removeAll();
            order.forEach(key -> rows.add(byPath.get(key).layout));

            boolean any = !order.isEmpty();
            everFound.compareAndSet(false, any);
            empty.setVisible(!any);
            truncatedNotice.setVisible(outputs.truncated());
            if (outputs.truncated()) {
                truncatedNotice.setText("Only the first " + SaveOutputs.MAX_VISITED_ENTRIES
                        + " entries were scanned — some files your app wrote may not be listed.");
            }
        };
        refresh.run(); // usually empty: the app writes after setVisible returns

        ScheduledFuture<?> watch = UiScheduler.scheduleWithFixedDelay(ui.getSession(), () -> {
            try {
                ui.access(refresh::run);
            } catch (RuntimeException e) {
                // ui.access throws UIDetachedException once the UI is gone — drop
                // the race-window callback. (This watcher targets the specific
                // download dialog's UI, so it doesn't re-home across an F5; the
                // dialog-close listener below is the authoritative cancel.)
            }
        }, 500, 500, TimeUnit.MILLISECONDS);

        Button close = new Button("Close", e -> dl.close());

        // Stop the watch once the dialog closes (Close button, X, ESC). The
        // per-session scheduler is not torn down on UI detach, so this listener
        // is the authoritative cancel. cancel(false) lets an in-flight poll
        // finish; the task takes no further work after that.
        dl.addOpenedChangeListener(e -> {
            if (!e.isOpened()) {
                watch.cancel(false);
                if (!everFound.get()) {
                    // The one case D_save_binds_download cannot fix — the app wrote outside the
                    // directory it was handed — made detectable instead of
                    // silent. There is no "app is done" signal, so the close is
                    // the only honest moment to say it: the human gave up.
                    // Deliberately a plain log, NOT EHelper.onUnimplemented —
                    // this is a diagnostic, not a stub, and must not land in the
                    // WARN inventory the exit gates assert on.
                    log.warn("Save produced no downloadable file: nothing was written into {}. "
                            + "The app most likely wrote to a path of its own instead of the File "
                            + "handed back by the file dialog — in a browser only files under that "
                            + "directory can be offered to the user.", dir);
                }
            }
        });

        dl.add(new VerticalLayout(warning, empty, truncatedNotice, rows));
        dl.getFooter().add(close);
        dl.open();
    }

    /** One discovered output file: a stable link + a live size readout. */
    private static final class DownloadRow {
        private final File file;
        private final Span size = new Span();
        private final HorizontalLayout layout;

        DownloadRow(File file, String relativePath) {
            this.file = file;
            Span mark = new Span();
            // The browser sanitises path separators out of the download hint, so
            // the suggestion is the base name the app produced; the link text
            // carries the relative path so a nested file still reads correctly.
            FileDownloadHandler handler = DownloadHandler.forFile(file, file.getName());
            // whenComplete already wraps the callback in UI.access; the
            // "Downloaded" flip shows live under @Push, else on the next
            // round-trip (best-effort).
            handler.whenComplete(ok -> mark.setText(Boolean.TRUE.equals(ok) ? "Downloaded ✓" : ""));
            Anchor link = new Anchor(handler, relativePath);
            link.getElement().setAttribute("download", true);
            this.layout = new HorizontalLayout(link, size, mark);
        }

        void refreshSize() {
            size.setText(sizeText(file));
        }
    }

    /**
     * In-memory variant of {@link #openDownloadDialog(File, String)} for content
     * that is fully produced before the dialog opens (e.g. the virtual PDF
     * printer in {@code :emulators-printing}). The completeness dance of the
     * file variant — size watcher, partial-download caveat — doesn't apply:
     * the bytes are final, so the dialog is just a download link.
     */
    public static void openDownloadDialog(byte[] content, String name) {
        Dialog dl = new Dialog();
        dl.setModality(ModalityMode.MODELESS);
        dl.setHeaderTitle("Download " + name);

        Span sizeLabel = new Span("Size: " + content.length + " bytes");

        Span downloadedMark = new Span();
        // null content type → the handler derives it from the file name.
        InputStreamDownloadHandler handler = DownloadHandler.fromInputStream(
                event -> new DownloadResponse(new ByteArrayInputStream(content),
                        name, null, content.length));
        // whenComplete already wraps the callback in UI.access; the "Downloaded"
        // flip shows live under @Push, else on the next round-trip (best-effort).
        handler.whenComplete(ok -> downloadedMark.setText(Boolean.TRUE.equals(ok) ? "Downloaded ✓" : ""));
        Anchor link = new Anchor(handler, "Download");
        link.getElement().setAttribute("download", true);

        Button close = new Button("Close", e -> dl.close());

        dl.add(new VerticalLayout(sizeLabel, link, downloadedMark));
        dl.getFooter().add(close);
        dl.open();
    }

    /** Honest "current bytes on disk" cue for the download dialog. */
    public static String sizeText(File target) {
        return target.exists() ? "Current size: " + target.length() + " bytes" : "Not written yet.";
    }

    /**
     * Browsers send a bare filename, but defend against path traversal in a
     * crafted multipart filename before using it to open a server-side file.
     */
    public static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "untitled";
        }
        String base = new File(fileName).getName(); // strip any directory part
        return base.isBlank() || base.equals("..") ? "untitled" : base;
    }
}
