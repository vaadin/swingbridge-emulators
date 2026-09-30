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

package com.vaadin.swingbridge.migration.tool.importswap;

import com.vaadin.swingbridge.migration.tool.Reports;
import com.vaadin.swingbridge.migration.tool.Sources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a Swing source tree's {@code java.awt} / {@code javax.swing} references onto their
 * {@code vaadinx} emulators — the mechanical half of migration stage 2.
 *
 * <pre>
 * bin/import-swap src/main/java --report import-swap.md
 * </pre>
 *
 * <p>That launcher is {@code java -cp "lib/*"} plus this class name — the shape the distribution
 * zip ships (D_kit_tool_zip). {@code -jar} is always wrong here: it ignores {@code -cp}, and the
 * swap table lives in the jars on that classpath.</p>
 *
 * <p>Options: {@code --report <path>} writes the Markdown report to a file instead of stdout;
 * {@code --dry-run} reports without editing; {@code --table <tsv>} adds a swap table, for running
 * without the emulators jar on the classpath.
 *
 * <p><b>This tool assists; it does not own the migration.</b> Its scope is what is decidable from the
 * swap table plus a file's own imports. {@code System.exit} routing, the static sweep,
 * {@code JFileChooser.showDialog} direction and every other judgement call stay with the agent
 * following the migration guide — the report names what was left behind so that triage has an input.
 *
 * <p>Exit status is 0 whenever the tool ran, declined files included: a decline is expected output
 * for the driver to triage, not a failure. A non-zero status means the tool itself could not run.
 */
public final class ImportSwap {

    private ImportSwap() {
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (IOException e) {
            // Says outright that it failed, and names the exception class rather than only
            // getMessage(): a NoSuchFileException / AccessDeniedException message is the bare
            // path, and a lone path on stderr reads like a success line. Unlike hazard-scan this
            // one cannot claim it changed nothing — a mid-walk failure leaves the files it had
            // already reached rewritten — so it points at the recovery instead, which is a
            // re-run: the rewrite is idempotent and tolerates a half-swapped tree by design.
            System.err.println("import-swap: FAILED — did not run to completion; no report was"
                    + " written and part of the tree may already be rewritten. Re-running is safe."
                    + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.exit(2);
        }
    }

    /** The whole tool minus the exit call, so a test can drive the real entry point. */
    static int run(String[] args) throws IOException {
        List<Path> roots = new ArrayList<>();
        List<Path> tables = new ArrayList<>();
        Path reportPath = null;
        boolean dryRun = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--report" -> reportPath = Path.of(args[++i]);
                case "--table" -> tables.add(Path.of(args[++i]));
                case "--dry-run" -> dryRun = true;
                case "--help", "-h" -> {
                    usage();
                    return 0;
                }
                default -> roots.add(Path.of(args[i]));
            }
        }
        if (roots.isEmpty()) {
            usage();
            return 2;
        }
        // A root that names nothing reads as an empty tree, and "Rewritten: nothing" over a typo'd
        // path looks like an already-swapped app — the one phase that edits files, silently not
        // having edited any. Every root missing is a refusal; some missing, a warning.
        List<Path> missing = Sources.missing(roots);
        if (missing.size() == roots.size()) {
            System.err.println("import-swap: FAILED — none of the given paths exists, so nothing"
                    + " was rewritten and no report was written. Not found: " + missing);
            return 2;
        }
        if (!missing.isEmpty()) {
            System.err.println("import-swap: WARNING — not found, nothing rewritten there: " + missing);
        }

        PortedTypes table = PortedTypes.load(tables);
        SiblingIndex siblings = SiblingIndex.scan(roots);
        Swapper swapper = new Swapper(table, siblings);
        Report report = new Report(table, missing);

        for (Path root : roots) {
            for (Path file : Sources.under(root, "*.java")) {
                report.scanned();
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Swapper.Result result = swapper.rewrite(source);
                report.file(file.toString(), result);
                if (result.changed() && !dryRun) {
                    Files.writeString(file, result.text(), StandardCharsets.UTF_8);
                }
            }
        }

        String rendered = report.render();
        if (reportPath != null) {
            Reports.write(reportPath, rendered);
            System.out.println("import-swap: report written to " + reportPath
                    + (report.hasDeclines() ? " — it has declined residue to triage" : ""));
        } else {
            System.out.print(rendered);
        }
        return 0;
    }

    private static void usage() {
        System.err.println("""
                Usage: bin/import-swap <source-dir-or-file>... [--report <path>] [--dry-run] [--table <tsv>]

                Rewrites java.awt / javax.swing / java.util / java.text references onto their vaadinx
                emulators: imports, and in-code fully-qualified references. Reads its swap table from
                every META-INF/emul/ported-types.tsv on the classpath, so add-on jars contribute rows —
                the launcher is `java -cp "lib/*"`, and dropping a jar in lib/ adds its rows.
                """);
    }
}
