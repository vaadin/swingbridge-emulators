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

package com.vaadin.swingbridge.migration.tool.hazardscan;

import com.vaadin.swingbridge.migration.tool.Reports;
import com.vaadin.swingbridge.migration.tool.Sources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Finds the Swing→emulators migration hazards in a source tree — the mechanical half of migration
 * Phase 1, one pass over the tree per file type.
 *
 * <pre>
 * bin/hazard-scan src --report hazards.md
 * </pre>
 *
 * <p>Options: {@code --report <path>} writes the Markdown report to a file instead of stdout;
 * {@code --list} prints the effective hazard table and exits; {@code --table <tsv>} adds a table, for
 * running against a working copy.
 *
 * <p><b>A finder, not a gate.</b> Many hits are expected and benign — a {@code SimpleDateFormat} is
 * fixed by the import swap alone — so the exit status is 0 whenever the scan ran, matching
 * {@code ImportSwap}'s doctrine. A non-zero status means the tool itself could not run. Nothing here
 * decides anything: the sections are an input to the guide's triage, and the two ⚠silent hazards keep
 * the ArchUnit backstop a source scan cannot replace.
 *
 * <p>The patterns live in {@code META-INF/emul/hazards.tsv} rather than in this class, so they are
 * inspectable ({@code --list}), joinable against spec.md §7 by a test, and extensible by an add-on jar
 * — see {@link Hazards}.
 */
public final class HazardScan {

    private HazardScan() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IOException e) {
            // Says outright that it failed, and names the exception class rather than only
            // getMessage(): a NoSuchFileException / AccessDeniedException message is the bare
            // path, and a lone path on stderr reads like a success line. Exit 2 is the only
            // non-zero this tool has — a finder that ran has nothing to fail about — so a
            // migrator who sees this got no report at all, and the line has to say so.
            System.err.println("hazard-scan: FAILED — the scan did not run; no report was written."
                    + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(2);
        }
    }

    /** The whole tool minus the exit call, so a test can drive the real entry point. */
    static int run(String[] args) throws IOException {
        List<Path> roots = new ArrayList<>();
        List<Path> tables = new ArrayList<>();
        Path reportPath = null;
        boolean listOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--report" -> reportPath = Path.of(args[++i]);
                case "--table" -> tables.add(Path.of(args[++i]));
                case "--list" -> listOnly = true;
                case "--help", "-h" -> {
                    usage();
                    return 0;
                }
                default -> roots.add(Path.of(args[i]));
            }
        }

        Hazards table = Hazards.load(tables);
        if (listOnly) {
            System.out.print(new HazardReport(table, roots, List.of()).renderTable());
            return 0;
        }
        if (roots.isEmpty()) {
            usage();
            return 2;
        }
        // A root that names nothing reads as an empty tree, so a typo'd path would otherwise
        // report every hazard clean. One missing root among several is the guide's own command
        // over an app with no resources folder, and stays a warning; all of them missing means
        // the scan read nothing, and a report of nothing is not a result.
        List<Path> missing = Sources.missing(roots);
        if (missing.size() == roots.size()) {
            System.err.println("hazard-scan: FAILED — none of the given paths exists, so there was"
                    + " nothing to scan; no report was written. Not found: " + missing);
            return 2;
        }
        if (!missing.isEmpty()) {
            System.err.println("hazard-scan: WARNING — not found, nothing read there: " + missing);
        }

        HazardReport report = new HazardReport(table, roots, missing);
        for (Map.Entry<String, List<Hazards.Hazard>> pass : byGlob(table).entrySet()) {
            for (Path root : roots) {
                for (Path file : Sources.under(root, pass.getKey())) {
                    report.filesRead(pass.getKey(), 1);
                    scan(file, pass.getValue(), report);
                }
            }
        }

        String rendered = report.render();
        if (reportPath != null) {
            Reports.write(reportPath, rendered);
            System.out.println("hazard-scan: report written to " + reportPath + " — "
                    + report.sectionsWithHits() + " section(s) with hits to triage");
        } else {
            System.out.print(rendered);
        }
        return 0;
    }

    /**
     * The scanned rows grouped by which files they read, so a tree is walked and each file parsed
     * once per file type rather than once per hazard.
     */
    private static Map<String, List<Hazards.Hazard>> byGlob(Hazards table) {
        Map<String, List<Hazards.Hazard>> out = new LinkedHashMap<>();
        table.rows().stream().filter(h -> !h.manual())
                .forEach(h -> out.computeIfAbsent(h.glob(), k -> new ArrayList<>()).add(h));
        return out;
    }

    /**
     * Matches every pattern against every line.
     *
     * <p>Line by line, as {@code grep -n} does, and not against the whole file: the report's
     * line numbers depend on it, and so do {@code ^} / {@code $} / {@code \b} — a
     * whole-file match would let one pattern span a line break. Decoding is lenient
     * UTF-8 (the {@code String} constructor substitutes where {@code Files.readAllLines}
     * throws), because a legacy Swing app is exactly the kind of tree that still carries a
     * latin-1 source file, and one of those must not abort the scan.
     */
    private static void scan(Path file, List<Hazards.Hazard> hazards, HazardReport report)
            throws IOException {
        String[] lines = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (Hazards.Hazard hazard : hazards) {
                Matcher m = hazard.regex().matcher(line);
                if (m.find()) {
                    report.hit(hazard, file, i + 1, line);
                }
            }
        }
    }

    private static void usage() {
        System.err.println("""
                Usage: bin/hazard-scan <source-dir-or-file>... [--report <path>] [--list] [--table <tsv>]

                Finds the migration hazards from spec.md §7 (plus the §5 import sharp edges) in a Swing
                source tree, grouped by hazard, with a checklist of the ones no scan can catch. A finder,
                not a gate: exit status is 0 whenever the scan ran, and every hit is a candidate to
                triage against the migration guide. Reads its pattern table from every
                META-INF/emul/hazards.tsv on the classpath, so add-on jars contribute rows.
                """);
    }
}
