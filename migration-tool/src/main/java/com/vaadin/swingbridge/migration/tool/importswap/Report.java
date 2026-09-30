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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The tool's deliverable: a Markdown account of what was rewritten and what was declined, for the
 * agent driving the migration to fold into {@code STUMBLES.md} and for a maintainer to read directly.
 *
 * <pre>{@code
 * Report r = new Report(table, List.of());
 * r.file(path, result);
 * System.out.print(r.render());
 * }</pre>
 *
 * <p>One format for both readers. A second machine-readable rendering would be maintenance cost with
 * no reader — the consumer is an LLM, which reads Markdown natively.
 *
 * <p>The <em>declined</em> section is the point of the report. A tool that silently succeeds gives its
 * driver nothing to verify or hand off, and the residue classes are exactly what the migration guide
 * triages next.
 */
final class Report {

    private record Entry(String path, List<String> changes, List<String> declines) {
    }

    private final PortedTypes table;
    private final List<Path> missing;
    private final List<Entry> entries = new ArrayList<>();
    private int scanned;
    private int rewritten;

    /** {@code missing}: the roots that name nothing, so the header can say "not found". */
    Report(PortedTypes table, List<Path> missing) {
        this.table = table;
        this.missing = missing;
    }

    void scanned() {
        scanned++;
    }

    void file(String path, Swapper.Result result) {
        if (result.changed()) {
            rewritten++;
        }
        if (!result.changes().isEmpty() || !result.declines().isEmpty()) {
            entries.add(new Entry(path, result.changes(), result.declines()));
        }
    }

    boolean hasDeclines() {
        return entries.stream().anyMatch(e -> !e.declines().isEmpty());
    }

    String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Import-swap report\n\n");
        sb.append("- files scanned: ").append(scanned).append('\n');
        if (!missing.isEmpty()) {
            sb.append("- **not found, nothing rewritten there:** ")
                    .append(missing.stream().map(p -> "`" + p + "`").toList()).append('\n');
        }
        sb.append("- files rewritten: ").append(rewritten).append('\n');
        sb.append("- swap table: ").append(table.size()).append(" rows from ")
                .append(String.join(", ", table.sources())).append('\n');

        List<Entry> declined = entries.stream().filter(e -> !e.declines().isEmpty()).toList();
        sb.append("- files with declined residue: ").append(declined.size()).append("\n\n");

        if (!declined.isEmpty()) {
            sb.append("## Declined — triage these\n\n");
            for (Entry e : declined) {
                sb.append("### `").append(e.path()).append("`\n\n");
                e.declines().forEach(d -> sb.append("- ").append(d).append('\n'));
                sb.append('\n');
            }
        }

        sb.append("## Rewritten\n\n");
        List<Entry> changed = entries.stream().filter(e -> !e.changes().isEmpty()).toList();
        if (changed.isEmpty()) {
            sb.append("Nothing. Either the tree is already swapped, or no file references an emulated "
                    + "type — check the swap-table row count above before believing the second reading.\n");
        }
        for (Entry e : changed) {
            sb.append("### `").append(e.path()).append("`\n\n");
            e.changes().forEach(c -> sb.append("- ").append(c).append('\n'));
            sb.append('\n');
        }
        return sb.toString();
    }
}
