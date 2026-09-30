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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SaveOutputs} — the SAVE transfer-directory walk (D_save_binds_download). Pure: no Karibu, no
 * dialog, no Vaadin. This is the part that decides <em>which</em> files a save offers
 * and <em>in what order</em>, which is why it lives apart from {@code BrowserFileTransfer}
 * and is tested directly rather than through the download dialog's reconcile.
 *
 * <p>The invariant under all of it: {@code SessionTempFiles.newTransferDir} hands out a
 * fresh, empty directory per show, so every file found here is the app's output
 * by construction.
 */
class SaveOutputsTest {

    @TempDir
    Path dir;

    private void write(String relative) {
        write(relative, "x");
    }

    private void write(String relative, String content) {
        try {
            Path f = dir.resolve(relative);
            Files.createDirectories(f.getParent());
            Files.writeString(f, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> discover() {
        return discover("untitled", 1000);
    }

    private List<String> discover(String promptedName) {
        return discover(promptedName, 1000);
    }

    /** Discovered files as paths relative to the transfer dir, in display order. */
    private List<String> discover(String promptedName, int cap) {
        return SaveOutputs.discover(dir.toFile(), promptedName, cap)
                .files().stream()
                .map(it -> dir.relativize(it.toPath()).toString())
                .toList();
    }

    @Test
    @DisplayName("an untouched transfer directory yields nothing, and is not truncated")
    void anUntouchedTransferDirectoryYieldsNothingAndIsNotTruncated() {
        SaveOutputs outputs = SaveOutputs.discover(dir.toFile(), "untitled", 1000);
        assertEquals(List.of(), outputs.files());
        assertFalse(outputs.truncated());
    }

    @Test
    @DisplayName("a null or non-existent directory yields nothing rather than failing")
    void aNullOrNonExistentDirectoryYieldsNothingRatherThanFailing() {
        assertEquals(List.of(), SaveOutputs.discover(null, "x", 1000).files());
        File gone = new File(dir.toFile(), "never-created");
        assertEquals(List.of(), SaveOutputs.discover(gone, "x", 1000).files());
    }

    @Test
    @DisplayName("the sibling case — app appends an extension to the path it was handed")
    void theSiblingCaseAppAppendsAnExtensionToThePathItWasHanded() {
        // The inventory testapp's shape: getAbsolutePath() + ".xls".
        write("untitled.xls");
        assertEquals(List.of("untitled.xls"), discover("untitled"));
    }

    @Test
    @DisplayName("root files come before nested ones — breadth first")
    void rootFilesComeBeforeNestedOnesBreadthFirst() {
        write("deep/nested/inner.txt");
        write("top.txt");
        write("deep/middle.txt");
        assertEquals(List.of("top.txt", "deep/middle.txt", "deep/nested/inner.txt"), discover());
    }

    @Test
    @DisplayName("the zip-beside-a-tree case puts the file that matters first")
    void theZipBesideATreeCasePutsTheFileThatMattersFirst() {
        // App builds a large tree, then zips it: the zip is at the root, so
        // breadth-first surfaces it above everything the tree contains.
        for (int i = 0; i < 12; i++) {
            write("work/part-" + i + ".tmp");
        }
        write("work/sub/deeper.bin");
        write("output.zip");
        assertEquals("output.zip", discover("output").get(0));
    }

    @Test
    @DisplayName("the prompted name sorts first — whole name, then stem, then prefix")
    void thePromptedNameSortsFirstWholeNameThenStemThenPrefix() {
        write("aaa-unrelated.txt");   // alphabetically first, but unrelated
        write("report_2.txt");        // prefix match on the stem
        write("report.txt");          // exact stem match
        assertEquals(
                List.of("report.txt", "report_2.txt", "aaa-unrelated.txt"),
                discover("report.txt"));
    }

    @Test
    @DisplayName("the prompted name beats alphabetical-by-extension, which has no importance signal")
    void thePromptedNameBeatsAlphabeticalByExtensionWhichHasNoImportanceSignal() {
        // Extension-ascending alone puts .log on top, and so does a stem-only
        // match rank — all three stems are "report". Only ranking the whole name
        // first surfaces the file the user actually asked for.
        write("report.log");
        write("report.tmp");
        write("report.xls");
        assertEquals(
                List.of("report.xls", "report.log", "report.tmp"),
                discover("report.xls"));
    }

    @Test
    @DisplayName("unrelated files sort by extension then name, case-insensitively")
    void unrelatedFilesSortByExtensionThenNameCaseInsensitively() {
        write("Beta.txt");
        write("alpha.txt");
        write("gamma.bin");
        assertEquals(List.of("gamma.bin", "alpha.txt", "Beta.txt"), discover("zzz"));
    }

    @Test
    @DisplayName("names differing only in case get a stable, case-sensitive tiebreak")
    void namesDifferingOnlyInCaseGetAStableCaseSensitiveTiebreak() {
        write("README");
        write("readme");
        // Deterministic either way — what matters is that repeated walks agree,
        // since rows are reconciled and a flapping order would move them.
        assertEquals(discover("zzz"), discover("zzz"));
        assertEquals(List.of("README", "readme"), discover("zzz"));
    }

    @Test
    @DisplayName("the cap counts directories, not just files, and reports truncation")
    void theCapCountsDirectoriesNotJustFilesAndReportsTruncation() throws IOException {
        // A tree of empty directories holds no files: a file-based cap would
        // never trip and the walk would re-crawl it every 500ms.
        for (int i = 0; i < 20; i++) {
            Files.createDirectories(dir.resolve("empty-" + i));
        }
        SaveOutputs outputs = SaveOutputs.discover(dir.toFile(), "x", 5);
        assertTrue(outputs.truncated(), "visiting 20 directories under a cap of 5 must truncate");
        assertEquals(List.of(), outputs.files());
    }

    @Test
    @DisplayName("truncation stops the walk but keeps what was already found")
    void truncationStopsTheWalkButKeepsWhatWasAlreadyFound() {
        for (int i = 0; i < 10; i++) {
            write("file-" + i + ".txt");
        }
        SaveOutputs outputs = SaveOutputs.discover(dir.toFile(), "x", 4);
        assertTrue(outputs.truncated());
        assertEquals(4, outputs.files().size());
    }

    @Test
    @DisplayName("symlinks are neither followed nor offered")
    void symlinksAreNeitherFollowedNorOffered() {
        write("real/target.txt");
        try {
            Files.createSymbolicLink(dir.resolve("loop"), dir);
            Files.createSymbolicLink(dir.resolve("shortcut.txt"), dir.resolve("real/target.txt"));
        } catch (Exception e) {
            return; // platform without symlink permission — nothing to assert
        }
        // Terminates (a followed `loop` would recurse) and offers only the real file.
        assertEquals(List.of("real/target.txt"), discover("target"));
    }
}
