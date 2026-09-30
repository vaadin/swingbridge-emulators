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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The hazard scan's deliverable: a Markdown account of every hazard the table knows, hit or clean,
 * for the agent driving the migration to triage against the guide.
 *
 * <p><b>Clean sections are printed, not omitted.</b> The report doubles as the Phase 1 worklist — a
 * hazard that vanishes when it finds nothing gives its reader no way to tell "checked, clear" from
 * "never looked", which is the difference between a scoped migration and a hopeful one.
 *
 * <p>Hits go in fenced blocks in {@code grep -n} shape ({@code path:line:text}) rather than inline
 * code, so a source line containing a backtick cannot break the rendering.
 */
final class HazardReport {

    private final Hazards table;
    private final List<Path> roots;

    /** The roots that name nothing, so the header can say "not found" rather than "clean". */
    private final List<Path> missing;

    /** Hazard → its hits, in scan order. Every row gets an entry, so a clean one renders as clean. */
    private final Map<Hazards.Hazard, List<String>> hits = new LinkedHashMap<>();

    /**
     * Glob → how many files were read under it. Every scanned glob is seeded at zero, so a migrator
     * who pointed the scan at sources only still sees {@code 0 × *.{html,htm}} and learns that the
     * resource half found nothing to look at rather than that it did not run.
     */
    private final Map<String, Integer> read = new TreeMap<>();

    HazardReport(Hazards table, List<Path> roots, List<Path> missing) {
        this.table = table;
        this.roots = roots;
        this.missing = missing;
        table.rows().stream().filter(h -> !h.manual()).forEach(h -> {
            hits.put(h, new ArrayList<>());
            read.putIfAbsent(h.glob(), 0);
        });
    }

    void filesRead(String glob, int count) {
        read.merge(glob, count, Integer::sum);
    }

    void hit(Hazards.Hazard hazard, Path file, int line, String text) {
        hits.get(hazard).add(file + ":" + line + ":" + stripTrailing(text));
    }

    /** True when at least one hazard matched — the count the closing line reports. */
    int sectionsWithHits() {
        return (int) hits.values().stream().filter(h -> !h.isEmpty()).count();
    }

    String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Migration hazard scan\n\n");
        sb.append("- roots: ").append(roots.stream().map(p -> "`" + p + "`").toList()).append('\n');
        if (!missing.isEmpty()) {
            sb.append("- **not found, nothing read there:** ")
                    .append(missing.stream().map(p -> "`" + p + "`").toList())
                    .append(" — fine if the app has no such folder; a typo otherwise\n");
        }
        sb.append("- files read: ").append(String.join(", ",
                read.entrySet().stream().map(e -> e.getValue() + " × `" + e.getKey() + "`").toList()));
        sb.append('\n');
        sb.append("- hazard table: ").append(table.rows().size()).append(" rows from ")
                .append(String.join(", ", table.sources())).append('\n');
        sb.append("- sections with hits: ").append(sectionsWithHits()).append(" of ")
                .append(hits.size()).append("\n\n");
        sb.append("**This is a finder, not a gate.** Every hit is a candidate to triage against "
                + "`guides/1-swing-to-emulators/guide.md`, not a failure — false positives are "
                + "expected (a `SimpleDateFormat` is fixed by the import swap alone). Each section names "
                + "the spec.md hazard it comes from; the two ⚠silent ones also have an ArchUnit backstop "
                + "in Phase 6.\n");

        hits.forEach((hazard, found) -> {
            sb.append("\n## `").append(hazard.id()).append("` — ").append(hazard.label()).append("\n\n");
            sb.append("spec.md ").append(hazard.section()).append(" · files `").append(hazard.glob())
                    .append("` · pattern `").append(hazard.regex().pattern()).append("`\n\n");
            if (found.isEmpty()) {
                sb.append("(clean)\n");
            } else {
                sb.append(found.size()).append(found.size() == 1 ? " hit\n\n" : " hits\n\n");
                sb.append("```text\n");
                found.forEach(line -> sb.append(line).append('\n'));
                sb.append("```\n");
            }
        });

        List<Hazards.Hazard> manual = table.rows().stream().filter(Hazards.Hazard::manual).toList();
        if (!manual.isEmpty()) {
            sb.append("\n## Manual checks — no scan signal\n\n");
            manual.forEach(h -> sb.append("- `").append(h.id()).append("` (").append(h.section())
                    .append(") — ").append(h.label()).append('\n'));
        }

        sb.append("\nDone. Sections with hits: ").append(sectionsWithHits())
                .append(". Every hit is a candidate to triage, not a failure.\n");
        return sb.toString();
    }

    /** {@code --list}: the effective table and where each row came from, without unzipping a jar. */
    String renderTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Hazard table\n\n");
        sb.append("- ").append(table.rows().size()).append(" rows from ")
                .append(String.join(", ", table.sources())).append("\n\n");
        sb.append("| id | section | files | pattern | label |\n|---|---|---|---|---|\n");
        for (Hazards.Hazard h : table.rows()) {
            sb.append("| `").append(h.id()).append("` | ").append(h.section()).append(" | ")
                    .append(h.manual() ? "—" : "`" + h.glob() + "`").append(" | ")
                    .append(h.manual() ? "—" : "`" + h.regex().pattern().replace("|", "\\|") + "`")
                    .append(" | ").append(h.label()).append(" |\n");
        }
        return sb.toString();
    }

    private static String stripTrailing(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
