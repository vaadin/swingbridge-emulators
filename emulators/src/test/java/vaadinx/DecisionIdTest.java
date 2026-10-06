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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the decision-ID scheme itself: every {@code D_*} / {@code SD_*} / {@code M1D_*} / {@code R_*}
 * reference in the repo must resolve to an anchor that actually exists, and no
 * anchor may be defined twice.
 *
 * <p>The scheme exists because monotonic numbering could not survive worktrees —
 * two branches each append "the next" {@code D94} and neither is wrong until they
 * meet. Slugs move the collision to where it belongs: two branches clash only
 * when they have decided the same thing. But a slug buys that at the cost of
 * <em>typo-ability</em> — {@code D_surrogate_frist} is a reference nothing catches, where
 * {@code D94} at least had a shape you could eyeball. This test is the other half of
 * the trade, and without it the scheme is strictly worse than numbering.
 *
 * <p>Deliberately allow-list-free, like {@code R12ProvenanceTest}: the set of valid
 * IDs is <em>derived</em> from the {@code <a id="…"></a>} anchors in the decision logs, so
 * adding an entry needs no edit here.
 *
 * <p>Sub-question ids ({@code Q_…}) are prose inside a decision entry, not anchors, so
 * they get a weaker rule: one cited from Java must at least occur in a decision log.
 * Code has cited sub-questions that lived only in a since-deleted idea note, and
 * nothing said so until a reader went looking.
 *
 * <p>Ideas live in GitHub issues, outside this scan, so a forward reference to a slug no
 * decision has earned yet belongs there rather than in the repository.
 *
 * <p>Three exemptions, each for a reason that is not tidiness:
 * {@code DECISION-ID-MAP.md} is the translation table and must keep saying the old
 * numbers; any {@code generated} directory holds Vaadin's minified frontend bundles,
 * whose mangled identifiers collide with every ID shape here; and this file is
 * excluded from itself, since a test about ID syntax cannot avoid writing IDs.
 */
class DecisionIdTest {

    private final File repoRoot = findRepoRoot();

    private static File findRepoRoot() {
        for (File dir = new File(".").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            if (new File(dir, "DECISION-ID-MAP.md").isFile()) {
                return dir;
            }
        }
        throw new IllegalStateException("cannot locate the repo root — no DECISION-ID-MAP.md in any parent of "
                + new File(".").getAbsolutePath());
    }

    /**
     * The files that may <em>define</em> an ID. Anything else may only reference one.
     * {@code PROVENANCE.md} is in the list for one prefix: it is where the {@code HDR_*} header
     * slugs live, the licence headers being specified there rather than in a decision log.
     */
    private final List<File> logs = List.of(
            "emulators/decisions.md",
            "surrogates/decisions.md",
            "migration/1-swing-to-emulators/decisions.md",
            "CLAUDE.md",
            "PROVENANCE.md").stream().map(it -> new File(repoRoot, it)).toList();

    private static final Set<String> SCANNED = Set.of("java", "kt", "md", "xml", "py", "js", "css", "sh");

    private static final Set<String> PRUNED =
            Set.of("target", "node_modules", ".git", ".claude", "node", "generated");

    // HDR_ is the licence-header set (PROVENANCE.md § Headers), on the scheme for the same reason
    // the decision ids are: a slug is silently mistypeable, and these are cited from ~50 places.
    // It is HDR_ and not H_ because H_ belongs to the migration hazards, which carry it in
    // spec.md's anchors and in hazards.tsv's first column.
    private static final Pattern ANCHOR = Pattern.compile("<a id=\"((?:HDR_|M1D_|SD_|D_|R_)\\w+)\"></a>");
    private static final Pattern TOKEN = Pattern.compile("\\b((?:HDR_|M1D_|SD_|D_|R_)\\w+)\\b");
    private static final Pattern FRAGMENT = Pattern.compile("#((?:HDR_|M1D_|SD_|D_|R_)[\\w-]+)");
    /** A sub-question id, cited from code; a decision entry mentions it in prose. */
    private static final Pattern QUESTION = Pattern.compile("\\bQ_[a-z][a-z0-9_]*\\b");

    /** Old-scheme IDs. Only the map that translates them may still say them. */
    private static final Pattern LEGACY =
            Pattern.compile("\\b(?:M1D|SD|D)\\d+[a-z]?\\b|\\bR(?:1[0-3]|[1-9])\\b");

    @Test
    @DisplayName("every decision-ID reference resolves to a defined anchor")
    void everyDecisionIdReferenceResolvesToADefinedAnchor() {
        Set<String> defined = definedAnchors();
        // Guard the guard: a silent zero-anchor scan would pass forever.
        assertTrue(defined.size() > 300, "expected 300+ anchors, found " + defined.size() + " — is the scan broken?");

        Map<String, List<String>> dangling = new TreeMap<>();
        for (File f : sources()) {
            if (isTheMap(f)) {
                continue;
            }
            Matcher m = TOKEN.matcher(read(f));
            while (m.find()) {
                String id = m.group(1);
                if (!defined.contains(id)) {
                    dangling.computeIfAbsent(id, k -> new ArrayList<>()).add(rel(f));
                }
            }
        }
        if (!dangling.isEmpty()) {
            fail("decision IDs referenced but never defined:\n"
                    + render(dangling, "  %s — %s")
                    + "\n\nDefine it with an <a id=\"…\"></a> anchor in a decision log, or fix the typo.");
        }
    }

    @Test
    @DisplayName("no decision ID is defined twice")
    void noDecisionIdIsDefinedTwice() {
        Map<String, List<String>> seen = new TreeMap<>();
        for (File f : sources()) {
            Matcher m = ANCHOR.matcher(read(f));
            while (m.find()) {
                seen.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(rel(f));
            }
        }
        Map<String, List<String>> dupes = new TreeMap<>();
        seen.forEach((id, where) -> {
            if (where.size() > 1) {
                dupes.put(id, where);
            }
        });
        if (!dupes.isEmpty()) {
            fail("decision IDs defined more than once — the collision slugs exist to prevent:\n"
                    + render(dupes, "  %s — %s"));
        }
    }

    @Test
    @DisplayName("every link fragment points at a real anchor")
    void everyLinkFragmentPointsAtARealAnchor() {
        Set<String> defined = definedAnchors();
        Map<String, List<String>> broken = new TreeMap<>();
        for (File f : sources()) {
            Matcher m = FRAGMENT.matcher(read(f));
            while (m.find()) {
                String frag = m.group(1);
                if (!defined.contains(frag)) {
                    broken.computeIfAbsent(frag, k -> new ArrayList<>()).add(rel(f));
                }
            }
        }
        if (!broken.isEmpty()) {
            fail("link fragments with no matching anchor:\n" + render(broken, "  #%s — %s"));
        }
    }

    @Test
    @DisplayName("the old numeric scheme is gone everywhere but the map that translates it")
    void theOldNumericSchemeIsGoneEverywhereButTheMapThatTranslatesIt() {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (File f : sources()) {
            if (isTheMap(f)) {
                continue;
            }
            // CLAUDE.md's one pointer sentence names the old range on purpose.
            String text = f.getName().equals("CLAUDE.md")
                    ? read(f).lines().filter(it -> !it.contains("DECISION-ID-MAP.md")).collect(Collectors.joining("\n"))
                    : read(f);
            Matcher m = LEGACY.matcher(text);
            while (m.find()) {
                offenders.computeIfAbsent(m.group(), k -> new ArrayList<>()).add(rel(f));
            }
        }
        if (!offenders.isEmpty()) {
            fail("old numeric decision IDs are back:\n"
                    + render(offenders, "  %s — %s")
                    + "\n\nUse the slug. DECISION-ID-MAP.md translates the old numbers.");
        }
    }

    /** Renders one failure line per id, de-duplicating the file list as the Kotlin did. */
    private static String render(Map<String, List<String>> byId, String format) {
        return byId.entrySet().stream()
                .map(e -> String.format(format, e.getKey(),
                        String.join(", ", new LinkedHashSet<>(e.getValue()))))
                .collect(Collectors.joining("\n"));
    }

    private Set<String> definedAnchors() {
        Set<String> out = new LinkedHashSet<>();
        for (File log : logs) {
            Matcher m = ANCHOR.matcher(read(log));
            while (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    @Test
    @DisplayName("every sub-question id cited from Java occurs in a decision log")
    void everySubQuestionIdCitedFromJavaOccursInADecisionLog() {
        Set<String> inLogs = new LinkedHashSet<>();
        for (File log : logs) {
            Matcher m = QUESTION.matcher(read(log));
            while (m.find()) {
                inLogs.add(m.group());
            }
        }
        Map<String, List<String>> dangling = new TreeMap<>();
        for (File f : sources()) {
            if (!f.getName().endsWith(".java")) {
                continue;
            }
            Matcher m = QUESTION.matcher(read(f));
            while (m.find()) {
                if (!inLogs.contains(m.group())) {
                    dangling.computeIfAbsent(m.group(), k -> new ArrayList<>()).add(rel(f));
                }
            }
        }
        assertTrue(dangling.isEmpty(), "sub-question ids cited from Java that no decision log mentions"
                + " — cite the decision entry that covers the point instead, or add the question to it:\n"
                + dangling);
    }

    /**
     * Every scannable file under the repo root, with the build / vendor
     * directories pruned rather than filtered — walking {@code node_modules} and
     * then discarding it costs seconds on every run.
     */
    private List<File> sources() {
        List<File> found = new ArrayList<>();
        try {
            Files.walkFileTree(repoRoot.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return PRUNED.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // Symlinks are skipped, not followed, and AGENTS.md is skipped by name: it is
                    // a byte-identical copy of CLAUDE.md (AgentsBriefTest keeps it so), and
                    // reading it would report every rule slug in CLAUDE.md as "defined twice".
                    if (!attrs.isRegularFile()) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = file.getFileName().toString();
                    if (name.equals("AGENTS.md")) {
                        return FileVisitResult.CONTINUE;
                    }
                    int dot = name.lastIndexOf('.');
                    String extension = dot < 0 ? "" : name.substring(dot + 1);
                    if (SCANNED.contains(extension) && !name.equals("DecisionIdTest.java")) {
                        found.add(file.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    /**
     * Lenient UTF-8, matching Kotlin's {@code File.readText()}: this walks the whole
     * repo, so one file with a stray byte must not fail the scan.
     * {@code Files.readString} throws on malformed input where the {@code String} ctor
     * substitutes.
     */
    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + f, e);
        }
    }

    private String rel(File f) {
        return repoRoot.toPath().relativize(f.toPath()).toString();
    }

    private static boolean isTheMap(File f) {
        return f.getName().equals("DECISION-ID-MAP.md");
    }
}
