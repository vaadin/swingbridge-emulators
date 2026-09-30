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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The swap table: which JDK types SB-Emulators emulates, and under what name.
 *
 * <pre>{@code
 * PortedTypes t = PortedTypes.fromClasspath(List.of());
 * t.emulatorFor("javax.swing.JButton");   // => "vaadinx.swing.JButton"
 * t.emulatorFor("java.awt.Color");        // => null — stays JDK
 * t.packages();                           // => [java.awt, java.awt.event, javax.swing, ...]
 * }</pre>
 *
 * <p>Rows are unioned across every {@code META-INF/emul/ported-types.tsv} on the classpath, so an
 * add-on jar contributes its own rows ({@code com.jgoodies.forms.layout.FormLayout} →
 * {@code vaadinx.jgoodies.forms.layout.FormLayout}) without SB-Emulators core knowing add-ons exist.
 *
 * <p>Immutable.
 *
 * <p>Add-on rows cannot be derived the way core's are — an add-on <em>replaces</em> its
 * upstream rather than depending on it, so the upstream class is not on any classpath to
 * reflect over. Declaring them in the same file format is what lets one lookup serve both.
 */
final class PortedTypes {

    static final String RESOURCE = "META-INF/emul/ported-types.tsv";

    /** JDK FQN → emulator FQN. */
    private final Map<String, String> rows;

    /** Packages holding at least one ported type — the set whose on-demand imports are rewritten. */
    private final Set<String> packages;

    private PortedTypes(Map<String, String> rows) {
        this.rows = Collections.unmodifiableMap(rows);
        Set<String> pkgs = new LinkedHashSet<>();
        rows.keySet().forEach(fqn -> pkgs.add(fqn.substring(0, fqn.lastIndexOf('.'))));
        this.packages = Collections.unmodifiableSet(pkgs);
    }

    /**
     * Loads and unions every table on the classpath, plus any explicitly named file.
     *
     * @param extra paths given with {@code --table}, for running the tool without the emulators jar
     * @throws IOException if a named file is unreadable, or if no rows were found at all — an empty
     *                     table would silently rewrite nothing and report success
     */
    static PortedTypes load(List<Path> extra) throws IOException {
        Map<String, String> rows = new TreeMap<>();
        List<String> sources = new ArrayList<>();

        Enumeration<URL> found = PortedTypes.class.getClassLoader().getResources(RESOURCE);
        while (found.hasMoreElements()) {
            URL url = found.nextElement();
            try (InputStream in = url.openStream()) {
                int n = parseInto(new String(in.readAllBytes(), StandardCharsets.UTF_8), rows);
                sources.add(url + " (" + n + " rows)");
            }
        }
        for (Path p : extra) {
            int n = parseInto(Files.readString(p, StandardCharsets.UTF_8), rows);
            sources.add(p + " (" + n + " rows)");
        }

        if (rows.isEmpty()) {
            throw new IOException("no " + RESOURCE + " found on the classpath and none given with --table. "
                    + "Put the swingbridge-emulators jar on the classpath, or pass --table "
                    + "emulators/src/main/resources/" + RESOURCE);
        }
        PortedTypes t = new PortedTypes(rows);
        t.sources.addAll(sources);
        return t;
    }

    private final List<String> sources = new ArrayList<>();

    /** Where the rows came from, for the report's provenance line. */
    List<String> sources() {
        return Collections.unmodifiableList(sources);
    }

    private static int parseInto(String tsv, Map<String, String> into) {
        int n = 0;
        for (String line : tsv.split("\n")) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            into.put(line.substring(0, tab).trim(), line.substring(tab + 1).trim());
            n++;
        }
        return n;
    }

    /** The emulator for a JDK type, or null when SB-Emulators does not emulate it. */
    String emulatorFor(String jdkFqn) {
        return rows.get(jdkFqn);
    }

    /**
     * Rewrites a fully-qualified reference, nested types included, or returns null if nothing maps.
     *
     * <pre>{@code
     * swapFqn("javax.swing.JButton");                        // => "vaadinx.swing.JButton"
     * swapFqn("java.awt.Component.BaselineResizeBehavior");  // => "vaadinx.awt.Component.BaselineResizeBehavior"
     * swapFqn("java.awt.Color");                             // => null
     * }</pre>
     */
    String swapFqn(String fqn) {
        String direct = rows.get(fqn);
        if (direct != null) {
            return direct;
        }
        // A nested type rides its enclosing type's row: strip trailing segments until one matches.
        for (int dot = fqn.lastIndexOf('.'); dot > 0; dot = fqn.lastIndexOf('.', dot - 1)) {
            String enclosing = rows.get(fqn.substring(0, dot));
            if (enclosing != null) {
                return enclosing + fqn.substring(dot);
            }
        }
        return null;
    }

    /** True when this package holds at least one ported type, i.e. its on-demand import is ours to rewrite. */
    boolean isRewritablePackage(String pkg) {
        return packages.contains(pkg);
    }

    Set<String> packages() {
        return packages;
    }

    /**
     * The group-level roots of every add-on that contributed rows — {@code com.jgoodies},
     * {@code com.toedter}.
     *
     * <p>What this is for: an import under such a root that the table does <em>not</em> map cannot be
     * left alone the way an unmapped JDK type can. A JDK stay-type still exists on the migrated app's
     * classpath; an add-on's upstream does not, because the add-on replaced it. So
     * {@code com.jgoodies.forms.builder.PanelBuilder} — upstream API this add-on ships no counterpart
     * for — is a hole the migrator must be told about, not silence.
     *
     * <p>An add-on root is a row whose own class is absent from this classpath, cut to two
     * segments. Two is a heuristic on group-id shape, and the cost of it being wrong is a
     * report line too many or too few, never a wrong rewrite.
     */
    Set<String> addonRoots() {
        Set<String> roots = new LinkedHashSet<>();
        for (String jdkFqn : rows.keySet()) {
            if (loadable(jdkFqn)) {
                continue;
            }
            int firstDot = jdkFqn.indexOf('.');
            int secondDot = firstDot < 0 ? -1 : jdkFqn.indexOf('.', firstDot + 1);
            if (secondDot > 0) {
                roots.add(jdkFqn.substring(0, secondDot));
            }
        }
        return roots;
    }

    private static boolean loadable(String fqn) {
        try {
            Class.forName(fqn, false, PortedTypes.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    int size() {
        return rows.size();
    }

    /** Every row, longest JDK name first — the order pass 1 applies them in. */
    Map<String, String> byDescendingLength() {
        Map<String, String> out = new LinkedHashMap<>();
        rows.keySet().stream()
                .sorted((a, b) -> b.length() != a.length() ? b.length() - a.length() : a.compareTo(b))
                .forEach(k -> out.put(k, rows.get(k)));
        return out;
    }
}
