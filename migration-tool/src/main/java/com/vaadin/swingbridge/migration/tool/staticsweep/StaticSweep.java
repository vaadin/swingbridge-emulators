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

package com.vaadin.swingbridge.migration.tool.staticsweep;

import com.vaadin.swingbridge.migration.tool.Reports;
import com.vaadin.swingbridge.migration.tool.Sources;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the static sweep's worklist from a Swing app's <b>own compiled classes</b> — the mechanical
 * half of the guide's Phase 1 and of {@code static-fields.md} Step 1.
 *
 * <pre>
 * bin/static-sweep target/classes --lib target/dependency --report target/static-sweep.md
 * </pre>
 *
 * <p>Options: {@code --lib <dir>} puts every jar in a directory on the resolution classpath and
 * {@code --cp <path>} takes the entries directly (both repeatable, both optional — a dependency's
 * absence costs only precision, and the report says which types went unresolved);
 * {@code --report <path>} writes the Markdown to a file instead of stdout; {@code --diff <dir>}
 * gives every row its fate in a second tree, which is the sweep's completeness check once the
 * migration has run; {@code --src <dir>} warns when the classes are older than the sources.
 *
 * <p><b>Point it at the app you have not migrated yet.</b> A Swing app under migration builds by
 * definition, so its stage-1 classes exist on day zero — this is not the post-swap build the guide
 * cannot assume.
 *
 * <p><b>A progress meter, not a gate.</b> Exit 0 whenever the sweep ran, because a non-empty
 * worklist mid-migration is the expected state; {@code MigrationGuardrails} is the gate that throws.
 * The loud failure is <b>no class files at all</b>, in either tree — exit 2 naming the build to run,
 * since an empty worklist over an unbuilt tree is a silent success, and a diff against an empty one
 * reports every row gone. A path that names nothing elsewhere ({@code --lib}, {@code --src}) is a
 * note at the top of the report, since the sweep still ran.
 *
 * <p>And no verdicts, deliberately: which {@code Reason} a row claims, whether {@code isLoggedIn} is
 * session or tab scope, whether a counter must stay contiguous — those need the app's intent.
 */
public final class StaticSweep {

    private StaticSweep() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IOException e) {
            // Names the exception class, not only getMessage(): a NoSuchFileException's message is
            // the bare path, and a lone path on stderr reads like a success line.
            System.err.println("static-sweep: FAILED — the sweep did not run; no report was written."
                    + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(2);
        }
    }

    /** The whole tool minus the exit call, so a test can drive the real entry point. */
    static int run(String[] args) throws IOException {
        List<Path> classDirs = new ArrayList<>();
        List<Path> libDirs = new ArrayList<>();
        List<Path> cpEntries = new ArrayList<>();
        List<Path> srcDirs = new ArrayList<>();
        Path reportPath = null;
        Path diffDir = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--lib" -> libDirs.add(Path.of(args[++i]));
                case "--cp" -> {
                    for (String entry : args[++i].split(java.io.File.pathSeparator, -1)) {
                        if (!entry.isBlank()) {
                            cpEntries.add(Path.of(entry));
                        }
                    }
                }
                case "--report" -> reportPath = Path.of(args[++i]);
                case "--diff" -> diffDir = Path.of(args[++i]);
                case "--src" -> srcDirs.add(Path.of(args[++i]));
                case "--help", "-h" -> {
                    usage();
                    return 0;
                }
                default -> classDirs.add(Path.of(args[i]));
            }
        }

        if (classDirs.isEmpty()) {
            usage();
            return 2;
        }

        try (Resolver resolver = Resolver.over(classDirs, libDirs, cpEntries)) {
            SweepReport.Swept sweep = sweep(label(classDirs), classDirs, resolver);
            if (sweep.tree().classFileCount() == 0) {
                System.err.println("static-sweep: no .class files under " + label(classDirs)
                        + " — the sweep reads the app's own compiled classes, so build it first"
                        + " (`mvn compile`, or the app's own build) and point this at the output"
                        + " directory. Reporting an empty worklist over an unbuilt tree would be a"
                        + " silent success, so this is an error instead.");
                return 2;
            }

            SweepReport.Swept diff = null;
            if (diffDir != null) {
                // Its own resolver: the migrated tree's types resolve against the migrated tree's
                // dependencies, and its component supertype is vaadinx.awt.Component rather than
                // java.awt.Component.
                try (Resolver diffResolver = Resolver.over(List.of(diffDir), libDirs, cpEntries)) {
                    diff = sweep(diffDir.toString(), List.of(diffDir), diffResolver);
                }
                if (diff.tree().classFileCount() == 0) {
                    System.err.println("static-sweep: no .class files under the --diff tree " + diffDir
                            + " — build the migrated app and point --diff at its output directory."
                            + " Diffed against nothing, every row would read as gone: a finished"
                            + " sweep, reported over a typo.");
                    return 2;
                }
            }

            List<String> notes = notFound(classDirs, libDirs, cpEntries, srcDirs);
            notes.addAll(notes(sweep, srcDirs));
            String rendered = new SweepReport(sweep, diff, resolver, notes).render();
            if (reportPath != null) {
                Reports.write(reportPath, rendered);
                System.out.println("static-sweep: report written to " + reportPath + " — "
                        + sweep.worklist().size() + " row(s) on the worklist");
            } else {
                System.out.print(rendered);
            }
            return 0;
        }
    }

    private static SweepReport.Swept sweep(String label, List<Path> classDirs, Resolver resolver)
            throws IOException {
        ClassTree tree = ClassTree.of(classDirs);
        StaticIndex index = StaticIndex.over(tree);
        return new SweepReport.Swept(label, tree, index, SweepReport.rows(index, resolver));
    }

    /**
     * Every path argument that names nothing, as a report note and a stderr line. Each is read as
     * empty, which leaves the sweep running but short: a missing {@code --lib} turns rows
     * unresolved, a missing {@code --src} skips the staleness check.
     */
    private static List<String> notFound(List<Path> classDirs, List<Path> libDirs,
            List<Path> cpEntries, List<Path> srcDirs) {
        List<String> notes = new ArrayList<>();
        notFound("class directory", classDirs, notes);
        notFound("--lib", libDirs, notes);
        notFound("--cp entry", cpEntries, notes);
        notFound("--src", srcDirs, notes);
        return notes;
    }

    private static void notFound(String what, List<Path> paths, List<String> notes) {
        for (Path path : Sources.missing(paths)) {
            System.err.println("static-sweep: WARNING — " + what + " not found, nothing read there: " + path);
            notes.add("**Not found:** " + what + " `" + path + "` — nothing was read there.");
        }
    }

    /**
     * The migrator's version of CLAUDE.md § Building's lesson: a sweep over yesterday's class files
     * reports yesterday's worklist, and the failure looks like a disagreement with the source rather
     * than like a stale build.
     */
    private static List<String> notes(SweepReport.Swept sweep, List<Path> srcDirs)
            throws IOException {
        List<String> notes = new ArrayList<>();
        long newestSource = 0;
        for (Path dir : srcDirs) {
            for (Path file : Sources.under(dir, "*.java")) {
                newestSource = Math.max(newestSource,
                        java.nio.file.Files.getLastModifiedTime(file).toMillis());
            }
        }
        if (newestSource > 0 && newestSource > sweep.tree().newestClassMillis()) {
            notes.add("**Stale classes.** The newest source file (" + when(newestSource)
                    + ") is newer than the newest class file (" + when(sweep.tree().newestClassMillis())
                    + "). Rebuild and re-run — everything below describes the older code.");
        } else if (sweep.tree().newestClassMillis() > 0) {
            notes.add("Newest class file: " + when(sweep.tree().newestClassMillis())
                    + (srcDirs.isEmpty()
                            ? " (pass `--src <dir>` to have this checked against your sources)"
                            : ", newer than every source file. Fresh."));
        }
        return notes;
    }

    private static String when(long millis) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static String label(List<Path> classDirs) {
        return classDirs.stream().map(Path::toString).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static void usage() {
        System.err.println("""
                Usage: bin/static-sweep <classes-dir>... [--lib <dir>] [--cp <path>]
                                        [--report <path>] [--diff <classes-dir>] [--src <dir>]

                Builds the static sweep's worklist from an app's compiled classes: every `static`
                field with its type, its writers and readers, whether its type is a component, and
                which bucket it lands in. Point it at the *pre-migration* app's build output — its
                classes exist before a single import is rewritten.

                A progress meter, not a gate: exit 0 whenever the sweep ran, and every row is a
                decision to make rather than a failure. The gate is MigrationGuardrails, which your
                migrated app's own test suite runs.

                  --lib <dir>     every *.jar in <dir> joins the resolution classpath (repeatable)
                  --cp <path>     classpath entries, separated as your platform separates them
                  --report <path> write the Markdown report to a file instead of stdout
                  --diff <dir>    also report what became of each row in a second tree
                  --src <dir>     warn when the class files are older than the sources
                """);
    }
}
