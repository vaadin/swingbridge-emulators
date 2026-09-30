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

package vaadinx.swing;

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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour for the {@link JFileChooser} emulator. Drives the
 * blocking modal show like the FileDialog / JOptionPane tests: call the
 * show*Dialog inside {@link EHelper#callSwing} so the internal JDialog parks on its
 * modal latch, interact from the test thread, then unpark via an approve/cancel
 * click in another callSwing. The returned int is captured once the park unwinds.
 */
class JFileChooserTest extends AbstractKaribuTest {

    /** Runs {@code show} (which parks) on a VT, returning the result it produced once unparked. */
    private static int runShow(IntSupplier show, Runnable interact) throws InterruptedException {
        AtomicInteger result = new AtomicInteger(JFileChooser.ERROR_OPTION);
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            result.set(show.getAsInt());
            finished.countDown();
        });
        interact.run();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "show*Dialog did not return");
        return result.get();
    }

    /**
     * Lets the D_save_binds_download directory watcher tick (500 ms cadence) and drain the
     * resulting {@code ui.access} command, then read back the offered links in
     * display order. The watcher is the production refresh path, so the test
     * drives it rather than reaching past it.
     */
    private static List<String> pollDownloadLinks() throws InterruptedException {
        Thread.sleep(700);
        MockVaadin.runUIQueue();
        return LocatorJ._find(Anchor.class).stream().map(Anchor::getText).toList();
    }

    private static void clickButton(String text) {
        Button btn = LocatorJ._get(Button.class, spec -> spec.withText(text));
        EHelper.callSwing(() -> LocatorJ._click(btn));
    }

    /** {@code Files.readString} / {@code writeString} without the checked exception at each site. */
    private static String read(File f) {
        try {
            return Files.readString(f.toPath());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(File f, String body) {
        try {
            Files.writeString(f.toPath(), body);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("modal show from non-VT context throws ISE")
    void modalShowFromNonVtContextThrows() {
        assertThrows(IllegalStateException.class, () -> new JFileChooser().showOpenDialog(null));
    }

    @Test
    @DisplayName("approve is disabled until a file is uploaded")
    void approveIsDisabledUntilAFileIsUploaded() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            chooser.showOpenDialog(null);
            finished.countDown();
        });

        Button open = LocatorJ._get(Button.class, spec -> spec.withText("Open"));
        assertFalse(open.isEnabled(), "Open should be disabled before any upload");

        clickButton("Cancel");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("single-file open stages a temp File readable by app code")
    void singleFileOpenStagesATempFile() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        int rc = runShow(() -> chooser.showOpenDialog(null), () -> {
            UploadKt._upload(LocatorJ._get(Upload.class), "hello.txt", "text/plain", utf8("hello world"));
            clickButton("Open");
        });
        assertEquals(JFileChooser.APPROVE_OPTION, rc);

        File f = chooser.getSelectedFile();
        assertEquals("hello.txt", f.getName());
        assertTrue(f.exists());
        assertEquals("hello world", read(f));
        assertEquals(1, chooser.getSelectedFiles().length);
        // current directory follows the selection (the temp dir).
        assertEquals(f.getParentFile().getAbsolutePath(), chooser.getCurrentDirectory().getAbsolutePath());
    }

    @Test
    @DisplayName("multi-select open stages every uploaded file")
    void multiSelectOpenStagesEveryUploadedFile() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        int rc = runShow(() -> chooser.showOpenDialog(null), () -> {
            Upload upload = LocatorJ._get(Upload.class);
            UploadKt._upload(upload, "a.txt", "text/plain", utf8("aaa"));
            UploadKt._upload(upload, "b.txt", "text/plain", utf8("bbb"));
            clickButton("Open");
        });
        assertEquals(JFileChooser.APPROVE_OPTION, rc);
        assertEquals(Set.of("a.txt", "b.txt"), names(chooser.getSelectedFiles()));
        assertEquals("a.txt", chooser.getSelectedFile().getName()); // first selected
    }

    @Test
    @DisplayName("cancel yields CANCEL_OPTION and clears the selection")
    void cancelYieldsCancelOptionAndClearsSelection() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        int rc = runShow(() -> chooser.showOpenDialog(null), () -> {
            UploadKt._upload(LocatorJ._get(Upload.class), "ignored.txt", "text/plain", utf8("x"));
            clickButton("Cancel");
        });
        assertEquals(JFileChooser.CANCEL_OPTION, rc);
        assertNull(chooser.getSelectedFile());
        assertEquals(0, chooser.getSelectedFiles().length);
    }

    // --- showDialog: the one throw (R_match_swing_errors case (4), D_showdialog_throws) ------------------------

    @Test
    @DisplayName("showDialog always throws, and the message teaches both replacements")
    @SuppressWarnings("deprecation") // the deprecation IS the thing under test
    void showDialogAlwaysThrowsWithATeachingMessage() {
        JFileChooser chooser = new JFileChooser();
        // No VT envelope needed: it throws before any park, which is itself the
        // point — nothing about the emulator's state or the UI is touched.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> chooser.showDialog(null, "Select Save location"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("showOpenDialog(parent)"), "message must name the LOAD replacement: " + msg);
        assertTrue(msg.contains("showSaveDialog(parent)"), "message must name the SAVE replacement: " + msg);
        assertTrue(msg.contains("setApproveButtonText"), "message must say how to keep the label: " + msg);
    }

    @Test
    @DisplayName("showDialog throws for the null-label form too, and inside a VT envelope")
    @SuppressWarnings("deprecation") // the deprecation IS the thing under test
    void showDialogThrowsForTheNullLabelFormToo() throws InterruptedException {
        // The JDK only flips to CUSTOM_DIALOG when the label is non-null, so this
        // form carries whatever direction the app declared — we refuse it anyway
        // (D_showdialog_throws: a rare honoured path is a rare untested path).
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogType(JFileChooser.SAVE_DIALOG);
        assertThrows(IllegalStateException.class, () -> chooser.showDialog(null, null));

        AtomicReference<Throwable> thrown = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            try {
                new JFileChooser().showDialog(null, "Attach");
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                finished.countDown();
            }
        });
        assertTrue(finished.await(5, TimeUnit.SECONDS), "showDialog did not return");
        assertInstanceOf(IllegalStateException.class, thrown.get(), "expected ISE");
        LocatorJ._assertNone(Upload.class); // no dialog was ever built
    }

    @Test
    @DisplayName("showDialog mutates nothing before throwing")
    @SuppressWarnings("deprecation") // the deprecation IS the thing under test
    void showDialogMutatesNothingBeforeThrowing() {
        JFileChooser chooser = new JFileChooser();
        chooser.setApproveButtonText("Existing");
        chooser.setDialogType(JFileChooser.OPEN_DIALOG);
        assertThrows(IllegalStateException.class, () -> chooser.showDialog(null, "Select Save location"));
        assertEquals("Existing", chooser.getApproveButtonText());
        assertEquals(JFileChooser.OPEN_DIALOG, chooser.getDialogType());
        assertNull(chooser.getSelectedFile());
    }

    @Test
    @DisplayName("setDialogType(CUSTOM_DIALOG) still round-trips — the throw is on the show, not the type")
    void setDialogTypeCustomStillRoundTrips() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogType(JFileChooser.CUSTOM_DIALOG);
        assertEquals(JFileChooser.CUSTOM_DIALOG, chooser.getDialogType());
    }

    // --- SAVE: modal name-pick + synthetic download dialog --------------------

    @Test
    @DisplayName("save returns a temp target and opens a download affordance")
    void saveReturnsATempTargetAndOpensADownloadAffordance() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("report.txt"));
        int rc = runShow(() -> chooser.showSaveDialog(null), () -> {
            TextField nameField = LocatorJ._get(TextField.class);
            assertEquals("report.txt", nameField.getValue()); // pre-filled from setSelectedFile
            nameField.setValue("renamed.txt");
            clickButton("Save");
        });
        assertEquals(JFileChooser.APPROVE_OPTION, rc);

        File target = chooser.getSelectedFile();
        assertEquals("renamed.txt", target.getName());

        // D_save_binds_download: the download binds to the transfer DIRECTORY, which is still
        // empty — the app writes after the modal name-pick returns.
        LocatorJ._assertNone(Anchor.class);
        LocatorJ._get(Span.class, spec -> spec.withText("No files available for download yet."));

        write(target, "the report body");
        assertEquals(List.of("renamed.txt"), pollDownloadLinks());
    }

    @Test
    @DisplayName("the download follows a derived sibling, not the path we handed the app")
    void theDownloadFollowsADerivedSibling() throws InterruptedException {
        // The inventory testapp's shape: write to getAbsolutePath() + ".xls".
        // The old binding pointed at `export` and streamed zero bytes while the
        // real workbook sat beside it, unreachable (D_save_binds_download).
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("export"));
        int rc = runShow(() -> chooser.showSaveDialog(null), () -> clickButton("Save"));
        assertEquals(JFileChooser.APPROVE_OPTION, rc);

        File handed = chooser.getSelectedFile();
        write(new File(handed.getAbsolutePath() + ".xls"), "workbook bytes");
        assertFalse(handed.exists(), "the path we handed the app was never written");

        // Offered anyway, under the name the app actually produced.
        assertEquals(List.of("export.xls"), pollDownloadLinks());
    }

    @Test
    @DisplayName("several files from one save are all offered, root before nested")
    void severalFilesFromOneSaveAreAllOffered() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("report"));
        assertEquals(JFileChooser.APPROVE_OPTION,
                runShow(() -> chooser.showSaveDialog(null), () -> clickButton("Save")));

        File dir = chooser.getSelectedFile().getParentFile();
        new File(dir, "pages").mkdirs();
        write(new File(dir, "pages/page_1.png"), "p1");
        write(new File(dir, "report.zip"), "zipped");

        assertEquals(List.of("report.zip", "pages/page_1.png"), pollDownloadLinks());
    }

    @Test
    @DisplayName("save cancel yields CANCEL_OPTION and no download dialog")
    void saveCancelYieldsCancelOptionAndNoDownload() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        int rc = runShow(() -> chooser.showSaveDialog(null), () -> clickButton("Cancel"));
        assertEquals(JFileChooser.CANCEL_OPTION, rc);
        assertNull(chooser.getSelectedFile());
        LocatorJ._assertNone(Anchor.class); // no download affordance on cancel
    }

    // --- FileFilter -----------------------------------------------------------

    @Test
    @DisplayName("opaque FileFilter is enforced server-side, rejects non-matching uploads")
    void opaqueFileFilterIsEnforcedServerSide() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.getName().endsWith(".txt");
            }

            @Override
            public String getDescription() {
                return "Text";
            }
        });
        chooser.setMultiSelectionEnabled(true);

        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            chooser.showOpenDialog(null);
            finished.countDown();
        });

        Upload upload = LocatorJ._get(Upload.class);
        Button open = LocatorJ._get(Button.class, spec -> spec.withText("Open"));

        UploadKt._upload(upload, "photo.png", "image/png", utf8("x"));
        assertFalse(open.isEnabled(), "Open stays disabled when only rejected files uploaded");
        assertTrue(LocatorJ._find(Span.class).stream().anyMatch(s -> s.getText().contains("photo.png")),
                "notice should name the rejected file");

        UploadKt._upload(upload, "notes.txt", "text/plain", utf8("hello"));
        assertTrue(open.isEnabled());

        clickButton("Open");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(List.of("notes.txt"), namesInOrder(chooser.getSelectedFiles()));
    }

    @Test
    @DisplayName("FileNameExtensionFilter pre-narrows the browser picker")
    void fileNameExtensionFilterPreNarrowsThePicker() throws InterruptedException {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text & CSV", "txt", "csv"));
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            chooser.showOpenDialog(null);
            finished.countDown();
        });

        assertEquals(Set.of(".txt", ".csv"), Set.copyOf(LocatorJ._get(Upload.class).getAcceptedFileExtensions()));

        clickButton("Cancel");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
    }

    // --- Round-trip getters + deferrals ---------------------------------------

    @Test
    @DisplayName("current directory is non-null and round-trips")
    void currentDirectoryIsNonNullAndRoundTrips() {
        JFileChooser chooser = new JFileChooser();
        assertNotNull(chooser.getCurrentDirectory(), "default current dir must be non-null");
        File dir = new File("/some/where");
        chooser.setCurrentDirectory(dir);
        assertEquals(dir, chooser.getCurrentDirectory());
    }

    @Test
    @DisplayName("dialog type validation matches Swing")
    void dialogTypeValidationMatchesSwing() {
        assertThrows(IllegalArgumentException.class, () -> new JFileChooser().setDialogType(99));
    }

    @Test
    @DisplayName("DIRECTORIES_ONLY is stored but warns (no browser dir pick)")
    void directoriesOnlyIsStoredButWarns() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // WARN-drop, must not throw
        assertEquals(JFileChooser.DIRECTORIES_ONLY, chooser.getFileSelectionMode());
    }

    @Test
    @DisplayName("accessory is an accepted noop")
    void accessoryIsAnAcceptedNoop() {
        JFileChooser chooser = new JFileChooser();
        chooser.setAccessory(null); // WARN-drop, must not throw
        assertNull(chooser.getAccessory());
    }

    private static Set<String> names(File[] files) {
        return Set.copyOf(namesInOrder(files));
    }

    private static List<String> namesInOrder(File[] files) {
        return Arrays.stream(files).map(File::getName).toList();
    }
}
