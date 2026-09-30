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

package vaadinx.awt;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.UploadKt;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * LOAD-path behaviour for the {@link FileDialog} emulator.
 * Drives the blocking modal show the same way the JOptionPane / JDialog tests do:
 * call {@code FileDialog.setVisible} inside {@link EHelper#callSwing} so it parks on the
 * inherited modal latch, interact with the dialog from the test thread, then
 * unpark via an OK/Cancel click in another callSwing.
 */
class FileDialogTest extends AbstractKaribuTest {

    private FileDialog newLoad(boolean multiple) {
        FileDialog fd = new FileDialog((Frame) null, "Open", FileDialog.LOAD);
        fd.setMultipleMode(multiple);
        return fd;
    }

    private FileDialog newLoad() {
        return newLoad(false);
    }

    /** The dialog's button carrying {@code text}, looked up on the test thread. */
    private static Button button(String text) {
        return LocatorJ._get(Button.class, spec -> spec.withText(text));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("illegal mode rejected like AWT")
    void illegalModeRejectedLikeAwt() {
        assertThrows(IllegalArgumentException.class, () -> new FileDialog((Frame) null, "x", 99));
    }

    @Test
    @DisplayName("modal show from non-VT context throws ISE")
    void modalShowFromNonVtContextThrowsIse() {
        // Mirrors the JOptionPane park guard: a blocking show must be reached
        // from an EHelper.callSwing UI fiber; the bare test thread fails fast at
        // parkUntilClose's UIFibers.checkInUIFiber().
        assertThrows(IllegalStateException.class, () -> newLoad().setVisible(true));
    }

    @Test
    @DisplayName("OK is disabled until a file is uploaded")
    void okIsDisabledUntilAFileIsUploaded() throws InterruptedException {
        FileDialog fd = newLoad();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });
        // Parked, dialog open. OK not yet clickable — nothing selected.
        Button ok = button("OK");
        assertFalse(ok.isEnabled(), "OK should be disabled before any upload");
        // Clean up the parked VT. Look the button up on the test thread (Karibu
        // lookups need the test-thread UI context), click inside callSwing.
        Button cancel = button("Cancel");
        EHelper.callSwing(() -> LocatorJ._click(cancel));
        assertTrue(finished.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("single-file LOAD stages an uploaded temp File readable by app code")
    void singleFileLoadStagesAnUploadedTempFileReadableByAppCode() throws InterruptedException, IOException {
        FileDialog fd = newLoad();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });

        UploadKt._upload(LocatorJ._get(Upload.class), "hello.txt", "text/plain", bytes("hello world"));
        Button ok = button("OK");
        EHelper.callSwing(() -> LocatorJ._click(ok));
        assertTrue(finished.await(5, TimeUnit.SECONDS), "show did not return after OK");

        assertEquals("hello.txt", fd.getFile());
        File only = assertSingle(fd.getFiles());
        assertEquals("hello.txt", only.getName());
        // The whole point of the temp-File bridge: app code reads it as a real file.
        assertTrue(only.exists());
        assertEquals("hello world", Files.readString(only.toPath()));
        // new File(getDirectory(), getFile()) reconstitutes the path.
        assertEquals(only.getAbsolutePath(), new File(fd.getDirectory(), fd.getFile()).getAbsolutePath());
    }

    @Test
    @DisplayName("multi-select LOAD stages every uploaded file")
    void multiSelectLoadStagesEveryUploadedFile() throws InterruptedException {
        FileDialog fd = newLoad(true);
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });

        Upload upload = LocatorJ._get(Upload.class);
        UploadKt._upload(upload, "a.txt", "text/plain", bytes("aaa"));
        UploadKt._upload(upload, "b.txt", "text/plain", bytes("bbb"));
        Button ok = button("OK");
        EHelper.callSwing(() -> LocatorJ._click(ok));
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        Set<String> names = java.util.Arrays.stream(fd.getFiles()).map(File::getName).collect(Collectors.toSet());
        assertEquals(Set.of("a.txt", "b.txt"), names);
        assertEquals("a.txt", fd.getFile()); // first selected
    }

    @Test
    @DisplayName("cancel yields the AWT cancelled contract")
    void cancelYieldsTheAwtCancelledContract() throws InterruptedException {
        FileDialog fd = newLoad();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });

        // Even with bytes uploaded, cancelling discards the selection.
        UploadKt._upload(LocatorJ._get(Upload.class), "ignored.txt", "text/plain", bytes("x"));
        Button cancel = button("Cancel");
        EHelper.callSwing(() -> LocatorJ._click(cancel));
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        assertNull(fd.getFile());
        assertEquals(0, fd.getFiles().length);
    }

    // --- SAVE: modal name-pick (this dialog) + synthetic download dialog ------

    @Test
    @DisplayName("SAVE returns the chosen name and opens a download affordance")
    void saveReturnsTheChosenNameAndOpensADownloadAffordance() throws InterruptedException, IOException {
        FileDialog fd = new FileDialog((Frame) null, "Save", FileDialog.SAVE);
        fd.setFile("report.txt");
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });

        // Modal name-pick dialog is open, pre-filled from setFile; user renames.
        TextField nameField = LocatorJ._get(TextField.class);
        assertEquals("report.txt", nameField.getValue());
        nameField.setValue("renamed.txt");

        Button save = button("Save");
        EHelper.callSwing(() -> LocatorJ._click(save));
        assertTrue(finished.await(5, TimeUnit.SECONDS), "SAVE did not return after Save click");

        // FileDialog stays AWT-faithful: hidden after return, result = the choice.
        assertFalse(fd.isVisible());
        assertEquals("renamed.txt", fd.getFile());
        File target = new File(fd.getDirectory(), fd.getFile());
        assertEquals("renamed.txt", target.getName());

        // The separate, non-modal synthetic dialog is up — but empty, because
        // D_save_binds_download binds it to the transfer DIRECTORY and the app writes only after
        // the modal name-pick returns. Shared helper, so AWT SAVE gets the same
        // sibling-tolerant behaviour JFileChooser does.
        LocatorJ._assertNone(Anchor.class);

        // A derived sibling — not the path we handed the app — is what gets offered.
        Files.writeString(new File(target.getAbsolutePath() + ".bak").toPath(), "the report body");
        Thread.sleep(700);
        MockVaadin.runUIQueue();
        assertEquals(List.of("renamed.txt.bak"),
                LocatorJ._find(Anchor.class).stream().map(Anchor::getText).toList());
    }

    @Test
    @DisplayName("SAVE cancel yields the AWT cancelled contract and no download dialog")
    void saveCancelYieldsTheAwtCancelledContractAndNoDownloadDialog() throws InterruptedException {
        FileDialog fd = new FileDialog((Frame) null, "Save", FileDialog.SAVE);
        fd.setFile("report.txt");
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });

        Button cancel = button("Cancel");
        EHelper.callSwing(() -> LocatorJ._click(cancel));
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        assertNull(fd.getFile());
        assertEquals(0, fd.getFiles().length);
        LocatorJ._assertNone(Anchor.class); // no download affordance on cancel
    }

    // --- FilenameFilter (enforced server-side on each uploaded file) ----------

    @Test
    @DisplayName("FilenameFilter rejects non-matching uploads with a notice, stages matches")
    void filenameFilterRejectsNonMatchingUploadsWithANoticeStagesMatches() throws InterruptedException {
        FileDialog fd = newLoad(true);
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            fd.setVisible(true);
            finished.countDown();
        });

        Upload upload = LocatorJ._get(Upload.class);
        Button ok = button("OK");

        // Non-matching upload: discarded, OK stays disabled, notice names it.
        UploadKt._upload(upload, "photo.png", "image/png", bytes("x"));
        assertFalse(ok.isEnabled(), "OK must stay disabled when only rejected files uploaded");
        // (Upload has its own internal drop-label Span, so match the notice by text.)
        assertTrue(LocatorJ._find(Span.class).stream().anyMatch(it -> it.getText().contains("photo.png")),
                "notice should name the rejected file");

        // Matching upload: staged, OK enabled.
        UploadKt._upload(upload, "notes.txt", "text/plain", bytes("hello"));
        assertTrue(ok.isEnabled());

        EHelper.callSwing(() -> LocatorJ._click(ok));
        assertTrue(finished.await(5, TimeUnit.SECONDS));

        // Only the matching file was handed to the app.
        assertEquals(List.of("notes.txt"),
                java.util.Arrays.stream(fd.getFiles()).map(File::getName).toList());
        assertEquals("notes.txt", fd.getFile());
    }

    @Test
    @DisplayName("filename filter round-trips and setDirectory is an accepted noop")
    void filenameFilterRoundTripsAndSetDirectoryIsAnAcceptedNoop() {
        FileDialog fd = newLoad();
        FilenameFilter filt = (dir, n) -> n.endsWith(".csv");
        fd.setFilenameFilter(filt);
        assertSame(filt, fd.getFilenameFilter());
        fd.setDirectory("/ignored/on/the/server"); // WARN-noop — must not throw
    }
}
