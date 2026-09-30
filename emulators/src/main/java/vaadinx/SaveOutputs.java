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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * INTERNAL — do not use. Not part of the emulated Swing/AWT API surface and has
 * no stability guarantee.
 *
 * <p>What a SAVE produced: the files an emulated Swing app wrote into the temp
 * directory it was handed, in the order the download dialog lists them
 * ([D_save_binds_download]). Pure — no Vaadin, no session, no I/O beyond the walk — so the
 * question "which files does this save offer, and which one goes on top" is
 * decided and tested on its own, apart from
 * {@link BrowserFileTransfer}'s dialog and transfer mechanics.
 *
 * <p>The invariant everything here rests on: {@link SessionTempFiles#newTransferDir()}
 * hands out a <em>fresh, empty</em> directory per show, so every file found is
 * the app's output by construction — no heuristics, nothing to disambiguate,
 * and no way for one user's save to see another's.
 *
 * @param files     the app's output files, in display order.
 * @param truncated whether {@link #MAX_VISITED_ENTRIES} stopped the walk — the
 *                  dialog says so rather than silently showing a short list.
 */
public record SaveOutputs(List<File> files, boolean truncated) {

    /**
     * Cap on directory entries one walk visits. Counts every entry touched,
     * <em>directories included</em> — a tree of empty directories holds no files
     * and would never trip a file-based cap, yet would be re-walked every
     * 500&nbsp;ms by the download watcher. Counting visits also makes a depth cap
     * redundant: the walk cannot descend past what this budget allows.
     */
    public static final int MAX_VISITED_ENTRIES = 1000;

    private static final SaveOutputs NOTHING = new SaveOutputs(List.of(), false);

    /** Walks {@code dir} under the default {@link #MAX_VISITED_ENTRIES} budget. */
    public static SaveOutputs discover(File dir, String promptedName) {
        return discover(dir, promptedName, MAX_VISITED_ENTRIES);
    }

    /**
     * Breadth-first walk of a SAVE transfer directory.
     *
     * <p>Breadth-first is what makes {@code visitCap} meaningful: you get the
     * shallowest entries, which are the likely outputs, where a depth-first walk
     * could spend the whole budget inside the first subdirectory. The shape that
     * motivates it: an app that builds a large tree and then zips it writes the
     * one file that matters at the root.
     *
     * <p>Ordering is deterministic and <em>stable under a growing file</em>,
     * because the download dialog reconciles rows rather than rebuilding them and
     * a shifting order would move rows under the user's cursor — which is why
     * modification time and size are not sort keys. Files come out ordered by
     * depth (root before nested), then by parent directory, then within a
     * directory by {@link #inFolderOrder}.
     *
     * <p>Symbolic links are neither followed nor offered: a link loop would spin
     * the walk, and there is no case where following one is what the migrated app
     * meant. Directories are traversal only — they never become download rows.
     *
     * @param dir          the transfer directory; null or unreadable yields an
     *                     empty result rather than an error.
     * @param promptedName the name typed at the save prompt, used only for the
     *                     match-first ordering rank; blank means "no signal".
     * @param visitCap     maximum directory entries to visit, counting directories.
     */
    public static SaveOutputs discover(File dir, String promptedName, int visitCap) {
        if (dir == null || !dir.isDirectory()) {
            return NOTHING;
        }
        List<File> found = new ArrayList<>();
        Comparator<Path> inFolder = inFolderOrder(promptedName);
        Deque<Path> queue = new ArrayDeque<>();
        queue.add(dir.toPath());
        boolean truncated = false;
        int visited = 0;

        while (!queue.isEmpty()) {
            Path folder = queue.poll();
            List<Path> entries;
            try (Stream<Path> list = Files.list(folder)) {
                entries = list.sorted(inFolder).toList();
            } catch (IOException | RuntimeException e) {
                // Vanished or unreadable mid-walk (the app is writing under us):
                // skip this folder, keep whatever the rest of the walk finds.
                continue;
            }
            for (Path entry : entries) {
                if (visited++ >= visitCap) {
                    truncated = true;
                    queue.clear();
                    break;
                }
                if (Files.isSymbolicLink(entry)) {
                    continue;
                }
                if (Files.isDirectory(entry)) {
                    queue.add(entry); // FIFO — the next depth level, in parent order
                } else {
                    found.add(entry.toFile());
                }
            }
        }
        return new SaveOutputs(List.copyOf(found), truncated);
    }

    /**
     * Within-a-directory order: how well the file matches the prompted name
     * first, then extension ascending, then file name ascending — both
     * case-insensitive, with a case-sensitive fallback so {@code README} and
     * {@code readme} cannot swap places between polls.
     *
     * <p>Alphabetical alone carries no importance signal; the prompted name is
     * the one signal available, and it is stable because it cannot change as
     * bytes are written. Extension-then-name is what groups a
     * {@code page_1.png … page_20.png} run beside a {@code manifest.txt}.
     */
    private static Comparator<Path> inFolderOrder(String promptedName) {
        String prompt = promptedName == null ? "" : promptedName.toLowerCase(Locale.ROOT).trim();
        String promptStem = stem(prompt);
        return Comparator
                .comparingInt((Path p) -> promptRank(p, prompt, promptStem))
                .thenComparing(p -> extension(fileName(p)).toLowerCase(Locale.ROOT))
                .thenComparing(p -> fileName(p).toLowerCase(Locale.ROOT))
                .thenComparing(SaveOutputs::fileName); // case-sensitive tiebreak
    }

    /**
     * How well a file matches the name typed at the save prompt: 0 = the whole
     * name matches, 1 = the stem matches, 2 = the stem starts with it, 3 =
     * unrelated (also the answer when there is no prompt to match).
     *
     * <p>The rank has to look past the stem: an app writing {@code report.log} /
     * {@code report.tmp} / {@code report.xls} beside a prompt of
     * {@code report.xls} ties every one of them on stem alone, and the extension
     * tiebreak then puts {@code .log} on top — the exact wrong-file-first
     * outcome this rank exists to prevent.
     */
    private static int promptRank(Path p, String prompt, String promptStem) {
        if (promptStem.isEmpty()) {
            return 3;
        }
        String name = fileName(p).toLowerCase(Locale.ROOT);
        if (name.equals(prompt)) {
            return 0;
        }
        String fileStem = stem(name);
        if (fileStem.equals(promptStem)) {
            return 1;
        }
        return fileStem.startsWith(promptStem) ? 2 : 3;
    }

    private static String fileName(Path p) {
        Path name = p.getFileName();
        return name == null ? "" : name.toString();
    }

    /** File name minus its extension; a leading dot is part of the stem, not an extension. */
    private static String stem(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    /** Extension without the dot, {@code ""} when there is none. */
    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1);
    }
}
