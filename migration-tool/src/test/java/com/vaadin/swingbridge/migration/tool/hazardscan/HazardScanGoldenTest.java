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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pins the scan's hit <em>count</em> per section over the inventory testapp's pristine stage-1 tree.
 *
 * <p>What this catches is a pattern that quietly widens or narrows. {@code D_showdialog_throws}'s
 * {@code Detect:} was verified by hand "against the inventory testapp's two call sites and nothing
 * else"; this makes that a standing assertion, and would catch e.g. a static-frame pattern that
 * starts matching every {@code Calendar.getInstance()}.
 *
 * <p><b>Counts, not lines</b>, and per section rather than per file: an unrelated edit to the testapp
 * moves line numbers around without changing what the scan found, and reddening on that would train
 * the next maintainer to regenerate without reading. A count change is worth reading.
 *
 * <p>Regenerate with
 * {@code ./mvnw -C -pl migration-tool test -Dtest=HazardScanGoldenTest -Demul.regenerate=true}
 * and read the diff before committing it.
 */
class HazardScanGoldenTest {

    private static final Path TREE = Path.of("../testapps/inventory/swing/src/main/java");

    private static final Path GOLDEN = Path.of("src/test/resources/hazard-scan-inventory.golden");

    /** {@code ## `H_id` — label} followed, two lines down, by {@code (clean)} or {@code N hits}. */
    private static final Pattern SECTION =
            Pattern.compile("(?m)^## `(H_\\w+)` — (.*)$\\n\\n.*\\n\\n(\\(clean\\)|\\d+ hits?)$");

    @Test
    @DisplayName("the scan finds exactly what it found last time over testapps/inventory/swing")
    void hitCountsMatchTheGolden(@TempDir Path dir) throws IOException {
        assumeTrue(Files.isDirectory(TREE), TREE + " not reachable from this working directory");

        Path report = dir.resolve("hazards.md");
        HazardScan.run(new String[]{TREE.toString(), "--report", report.toString()});
        String counted = counts(Files.readString(report, StandardCharsets.UTF_8));

        if (Boolean.getBoolean("emul.regenerate")) {
            Files.createDirectories(GOLDEN.getParent());
            Files.writeString(GOLDEN, counted, StandardCharsets.UTF_8);
            System.out.println("regenerated " + GOLDEN.toAbsolutePath() + " — read the diff, then commit");
            return;
        }
        if (!Files.exists(GOLDEN)) {
            fail(GOLDEN + " is missing. Regenerate with -Demul.regenerate=true and commit it.");
        }
        // Line endings normalised on read: .gitattributes pins LF in the working tree, and this
        // is the belt to that braces — a CRLF checkout would otherwise fail here with a diff that
        // prints identically, \r being invisible.
        String committed = Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!committed.equals(counted)) {
            fail("the hazard scan's per-section hit counts changed:\n\n  committed:\n"
                    + indent(committed) + "\n  now:\n" + indent(counted)
                    + "\nIf the pattern change was deliberate, regenerate with -Demul.regenerate=true.");
        }
    }

    /** One {@code id<TAB>label<TAB>count} line per scanned section, in report order. */
    private static String counts(String report) {
        List<String> lines = new ArrayList<>();
        Matcher m = SECTION.matcher(report);
        while (m.find()) {
            String hits = m.group(3).equals("(clean)") ? "0" : m.group(3).replaceAll("\\D+", "");
            lines.add(m.group(1) + "\t" + m.group(2) + "\t" + hits);
        }
        assertTrue(lines.size() > 15, "only parsed " + lines.size() + " sections out of the report — "
                + "the renderer's shape changed and this parser did not:\n" + report);
        return String.join("\n", lines) + "\n";
    }

    private static String indent(String text) {
        return text.lines().map(l -> "    " + l + "\n").reduce("", String::concat);
    }
}
