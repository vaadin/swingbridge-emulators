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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The hazard table: what a Swing→emulators port has to look at before it starts, and how to find it.
 *
 * <pre>{@code
 * Hazards h = Hazards.load(List.of());
 * h.rows();       // => every hazard, scanned and manual, in table order
 * h.sources();    // => which jars contributed them
 * }</pre>
 *
 * <p>Rows are unioned across every {@code META-INF/emul/hazards.tsv} on the classpath, exactly as
 * {@code PortedTypes} unions swap tables, so an add-on jar whose {@code MIGRATION.md} carries a
 * grep-shaped watch-out can contribute it to the one scan the migrator already runs. Core ships the
 * only table today; the union is the mechanism, not a promise that anything uses it yet.
 *
 * <p>Immutable.
 *
 * <p>The table is the single home of the patterns. They were duplicated by hand into a shell
 * script and into spec.md §7's {@code Detect:} lines, which had already drifted apart in
 * the benign direction; {@code HazardTableTest} now joins the two by {@code H_} slug.
 */
final class Hazards {

    static final String RESOURCE = "META-INF/emul/hazards.tsv";

    /**
     * One hazard's detection rule.
     *
     * @param id       the {@code H_} slug this row shares with its spec.md bullet's anchor; several
     *                 rows may carry one id when a hazard has more than one signal
     * @param section  where the bullet lives — {@code §7} or {@code §5}
     * @param label    the section heading in the report, tags included
     * @param regex    matched against each line; null for a manual row, which renders as a checklist
     *                 item instead. The absence of a pattern is what makes a row manual — the
     *                 hazard still belongs to its spec section, and saying {@code manual} there
     *                 instead would lose which one.
     * @param glob     which files to read it over, e.g. {@code *.java}; null exactly when regex is
     */
    record Hazard(String id, String section, String label, Pattern regex, String glob) {

        boolean manual() {
            return regex == null;
        }
    }

    private final List<Hazard> rows;
    private final List<String> sources;

    private Hazards(List<Hazard> rows, List<String> sources) {
        this.rows = Collections.unmodifiableList(rows);
        this.sources = Collections.unmodifiableList(sources);
    }

    /**
     * Loads and unions every table on the classpath, plus any explicitly named file.
     *
     * @param extra paths given with {@code --table}, for running the tool against a working copy
     * @throws IOException if a named file is unreadable or malformed, or if no rows were found at
     *                     all — an empty table would scan nothing and report a clean tree
     */
    static Hazards load(List<Path> extra) throws IOException {
        List<Hazard> rows = new ArrayList<>();
        List<String> sources = new ArrayList<>();

        Enumeration<URL> found = Hazards.class.getClassLoader().getResources(RESOURCE);
        while (found.hasMoreElements()) {
            URL url = found.nextElement();
            try (InputStream in = url.openStream()) {
                int n = parseInto(new String(in.readAllBytes(), StandardCharsets.UTF_8), url.toString(), rows);
                sources.add(url + " (" + n + " rows)");
            }
        }
        for (Path p : extra) {
            int n = parseInto(Files.readString(p, StandardCharsets.UTF_8), p.toString(), rows);
            sources.add(p + " (" + n + " rows)");
        }

        if (rows.isEmpty()) {
            throw new IOException("no " + RESOURCE + " found on the classpath and none given with --table. "
                    + "Put the swingbridge-migration-tool jar on the classpath, or pass --table "
                    + "migration-tool/src/main/resources/" + RESOURCE);
        }
        return new Hazards(rows, sources);
    }

    /**
     * Parses one table.
     *
     * <p>Tab-separated, {@code #} comments and blank lines skipped. Three fields ({@code id,
     * section, label}) is a manual row; four adds the regex, scanned over {@code *.java}; five names
     * the glob instead.
     */
    private static int parseInto(String tsv, String origin, List<Hazard> into) throws IOException {
        int n = 0;
        int lineNo = 0;
        for (String line : tsv.split("\n")) {
            lineNo++;
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            String[] f = line.split("\t", -1);
            if (f.length < 3 || f.length > 5) {
                throw new IOException(origin + ":" + lineNo + ": expected 3-5 tab-separated fields, got "
                        + f.length + " — " + line);
            }
            String regex = f.length >= 4 ? f[3].trim() : "";
            String glob = f.length == 5 ? f[4].trim() : "*.java";
            if (regex.isEmpty() && f.length >= 4) {
                throw new IOException(origin + ":" + lineNo + ": row has a regex column but it is empty; "
                        + "drop the column to declare a manual row — " + line);
            }
            try {
                into.add(new Hazard(f[0].trim(), f[1].trim(), f[2].trim(),
                        regex.isEmpty() ? null : Pattern.compile(regex),
                        regex.isEmpty() ? null : glob));
            } catch (PatternSyntaxException e) {
                throw new IOException(origin + ":" + lineNo + ": " + e.getMessage(), e);
            }
            n++;
        }
        return n;
    }

    /** Every hazard, in table order — the order the report renders them in. */
    List<Hazard> rows() {
        return rows;
    }

    /** Where the rows came from, for the report's provenance line. */
    List<String> sources() {
        return sources;
    }
}
