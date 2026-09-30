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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.vaadin.swingbridge.migration.tool.Sources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan's shape, driven through its real entry point over a tree small enough to read. */
class HazardScanTest {

    /** Runs the tool as a migrator does and returns the report it wrote. */
    private static String scan(Path root) throws IOException {
        Path report = root.resolve("hazards.md");
        assertEquals(0, HazardScan.run(new String[]{root.toString(), "--report", report.toString()}),
                "the scan is a finder, not a gate — it exits 0 whenever it ran");
        return Files.readString(report, StandardCharsets.UTF_8);
    }

    private static void write(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a hit is reported at its own line number, grep-style")
    void reportsHitsPerLine(@TempDir Path dir) throws IOException {
        write(dir, "src/App.java", """
                package com.acme;

                class App {
                    void quit() {
                        System.exit(0);
                    }
                }
                """);
        String out = scan(dir);
        // The report prints the migrator's own paths, so the separator is theirs too — `\` on
        // Windows. Normalise here rather than in the tool: a path a migrator cannot paste back
        // into their own shell would be the wrong fix for a test's convenience.
        assertTrue(out.replace('\\', '/').contains("src/App.java:5:        System.exit(0);"), out);
        assertTrue(out.contains("## `H_system_exit`"), out);
        assertTrue(out.contains("sections with hits: 1 of "), out);
    }

    @Test
    @DisplayName("a pattern cannot span a line break — matching is per line, as grep -n is")
    void doesNotMatchAcrossLines(@TempDir Path dir) throws IOException {
        // `\.showDialog\s*\(` — \s matches a newline, so a whole-file match would find this.
        write(dir, "src/Split.java", """
                class Split {
                    void go() {
                        chooser.showDialog
                                (parent, "Pick");
                    }
                }
                """);
        String out = scan(dir);
        assertTrue(section(out, "H_showdialog_direction").contains("(clean)"), out);
    }

    @Test
    @DisplayName("a root that does not exist is named in the report, not read as a clean tree")
    void namesAMissingRoot(@TempDir Path dir) throws IOException {
        write(dir, "src/App.java", "class App {}\n");
        Path report = dir.resolve("hazards.md");
        Path typo = dir.resolve("src/main/resorces");
        assertEquals(0, HazardScan.run(new String[]{dir.resolve("src").toString(), typo.toString(),
                "--report", report.toString()}), "one missing root among several still scans the rest");
        String out = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(out.contains("**not found, nothing read there:** [`" + typo + "`]"), out);
    }

    @Test
    @DisplayName("every root missing is a refusal — a report over nothing reads as all clean")
    void refusesWhenNoRootExists(@TempDir Path dir) throws IOException {
        Path report = dir.resolve("hazards.md");
        assertEquals(2, HazardScan.run(new String[]{dir.resolve("nope").toString(),
                "--report", report.toString()}));
        assertFalse(Files.exists(report), "no report, so no clean-looking one either");
    }

    @Test
    @DisplayName("a hazard with no hits is printed as clean, not omitted")
    void printsCleanSections(@TempDir Path dir) throws IOException {
        write(dir, "src/Empty.java", "class Empty {}\n");
        String out = scan(dir);
        assertTrue(section(out, "H_unswapped_printing").contains("(clean)"), out);
        assertTrue(out.contains("sections with hits: 0 of "), out);
    }

    @Test
    @DisplayName("the manual hazards render as a checklist, so they cannot fall out of the report")
    void rendersManualChecklist(@TempDir Path dir) throws IOException {
        String out = scan(dir);
        assertTrue(out.contains("## Manual checks — no scan signal"), out);
        assertTrue(out.contains("`H_modal_from_listener`"), out);
        assertTrue(out.contains("`H_multi_tab`"), out);
    }

    @Test
    @DisplayName("HTML resources are scanned for hrefs Flow's allowlist will strip")
    void scansHtmlResourcesForNonWebHrefs(@TempDir Path dir) throws IOException {
        write(dir, "res/help/index.html", """
                <html><body>
                <a href="topics.html">Topics</a>
                <a href="https://vaadin.com">Vaadin</a>
                <a href="mailto:x@y.z">Mail</a>
                <a href="#intro">Intro</a>
                </body></html>
                """);
        String out = scan(dir);
        assertTrue(out.contains("index.html:2:"), out);
        assertTrue(out.contains("index.html:5:"), out);
        assertFalse(out.contains("index.html:3:"), out);
        assertFalse(out.contains("index.html:4:"), out);
    }

    @Test
    @DisplayName("a --report path whose directory does not exist yet is created, not refused")
    void createsTheReportsParentDirectory(@TempDir Path dir) throws IOException {
        // The guide writes into MIGRATED_APP_FOLDER/target/, and Phase 1 runs before the app's
        // first build — so on a freshly-copied tree that directory does not exist. This exited 2
        // printing the bare path, which reads like a success line; a round lost a detour to it.
        write(dir, "src/App.java", "class App { void q() { System.exit(0); } }\n");
        Path report = dir.resolve("target/hazards.md");
        assertEquals(0, HazardScan.run(new String[]{dir.toString(), "--report", report.toString()}));
        assertTrue(Files.readString(report, StandardCharsets.UTF_8).contains("H_system_exit"));
    }

    @Test
    @DisplayName("build output is skipped by path element, so a Windows path is skipped too")
    void skipsBuildOutput(@TempDir Path dir) throws IOException {
        write(dir, "target/generated-sources/Gen.java", "class Gen { void q() { System.exit(1); } }\n");
        assertEquals(List.of(), Sources.under(dir, "*.java"));
    }

    /** The rendered report from the first {@code ## `H_…`} heading with this id to the next heading. */
    private static String section(String report, String id) {
        int start = report.indexOf("## `" + id + "`");
        assertTrue(start >= 0, "no section for " + id + " in:\n" + report);
        int next = report.indexOf("\n## ", start);
        return report.substring(start, next < 0 ? report.length() : next);
    }
}
