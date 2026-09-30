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
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps {@code META-INF/emul/ported-types.tsv} — the swap table the migration import-swap tool
 * reads — in step with the emulators actually on disk. The file is checked in and this test
 * regenerates it by reflection and fails on any difference:
 *
 * <pre>
 * java.awt.BorderLayout	vaadinx.awt.BorderLayout
 * java.awt.Button	vaadinx.awt.Button
 * ...
 * &lt;JDK type&gt;            &lt;its emulator&gt;
 * </pre>
 *
 * <p>When a new emulator lands, this test goes red; regenerate with
 * {@code ./mvnw -C -pl emulators test -Dtest=PortedTypesTableTest -Demul.regenerate=true}
 * and commit the diff.
 *
 * <p>Rows are <em>derived</em>, never listed: map {@code vaadinx.awt.Foo} onto {@code java.awt.Foo}
 * and keep the pair only if that JDK class exists. So every SB-Emulators-invented class ({@code EHelper},
 * the {@code vaadinx.util.prefs} SPI implementation) drops out with no exclusion list to maintain —
 * the same recipe {@link R12ProvenanceTest} uses, and the property that makes the table
 * drift-proof rather than merely current.
 *
 * <p>Golden file rather than a {@code generate-resources} step, which would compute the
 * same rows but leave the table invisible until a build ran. Checked in, it is greppable
 * by the migration agent without building SB-Emulators, and a row appearing or vanishing shows up
 * in the diff of the commit that caused it.
 */
class PortedTypesTableTest {

    /** Path of the table, both inside the jar and — resolved against the module dir — in the source tree. */
    private static final String TABLE = "META-INF/emul/ported-types.tsv";

    private static final Path SOURCE = Path.of("src/main/resources", TABLE);

    /** Emulated package roots, longest prefix first so {@code vaadinx.util.prefs} beats {@code vaadinx.util}. */
    private static final List<String[]> JDK_PACKAGE = List.of(
            new String[] {"vaadinx.awt", "java.awt"},
            new String[] {"vaadinx.swing", "javax.swing"},
            new String[] {"vaadinx.util", "java.util"},
            new String[] {"vaadinx.text", "java.text"});

    @Test
    @DisplayName("the checked-in ported-types table matches what the emulators on disk derive to")
    void tableMatchesDerivedRows() throws IOException {
        String derived = render(derive());

        if (Boolean.getBoolean("emul.regenerate")) {
            Files.createDirectories(SOURCE.getParent());
            Files.writeString(SOURCE, derived, StandardCharsets.UTF_8);
            System.out.println("regenerated " + SOURCE.toAbsolutePath() + " — commit the diff");
            return;
        }

        if (!Files.exists(SOURCE)) {
            fail(SOURCE + " is missing. Regenerate with -Demul.regenerate=true and commit it.");
        }
        // Line endings normalised on read, as HazardScanGoldenTest does: .gitattributes pins LF in
        // the working tree, and without this a CRLF checkout fails here with an invisible diff.
        String committed = Files.readString(SOURCE, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!committed.equals(derived)) {
            fail(TABLE + " is out of step with the emulators on disk:\n\n" + diff(committed, derived)
                    + "\n\nRegenerate with -Demul.regenerate=true and commit the diff.");
        }
    }

    /**
     * Every JDK type an emulator emulates, as JDK name → emulator name, sorted by JDK name.
     *
     * <p>Top-level public classes only. A nested type needs no row of its own: both tool passes
     * match the longest table prefix, so {@code java.awt.Component.BaselineResizeBehavior} rewrites
     * off {@code java.awt.Component}'s row, and a nested type the emulator lacks then fails to
     * compile — loudly, which is the intended shape.
     */
    private static TreeMap<String, String> derive() throws IOException {
        List<Class<?>> classes = emulatorClasses();
        assertTrue(classes.size() > 200, "only " + classes.size() + " vaadinx classes scanned — scan is broken");

        TreeMap<String, String> rows = new TreeMap<>();
        for (Class<?> c : classes) {
            if (!Modifier.isPublic(c.getModifiers()) || c.getName().contains("$")) {
                continue;
            }
            Class<?> jdk = jdkCounterpart(c);
            if (jdk != null) {
                rows.put(jdk.getName(), c.getName());
            }
        }
        assertTrue(rows.size() > 100, "only " + rows.size() + " rows derived — derivation is broken");
        return rows;
    }

    private static String render(TreeMap<String, String> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by PortedTypesTableTest — do not hand-edit.\n");
        sb.append("# Regenerate: ./mvnw -C -pl emulators test -Dtest=PortedTypesTableTest -Demul.regenerate=true\n");
        sb.append("# <JDK type>\\t<emulator type>. Top-level public classes only; nested types\n");
        sb.append("# rewrite off their enclosing type's row by longest-prefix match.\n");
        rows.forEach((jdk, vaadinx) -> sb.append(jdk).append('\t').append(vaadinx).append('\n'));
        return sb.toString();
    }

    /** The added/removed rows, so a red build names the emulator that landed rather than dumping 155 lines. */
    private static String diff(String committed, String derived) {
        List<String> was = dataLines(committed);
        List<String> now = dataLines(derived);
        List<String> out = new ArrayList<>();
        now.stream().filter(l -> !was.contains(l)).forEach(l -> out.add("    + " + l));
        was.stream().filter(l -> !now.contains(l)).forEach(l -> out.add("    - " + l));
        return out.isEmpty() ? "    (only the header comment differs)" : String.join("\n", out);
    }

    private static List<String> dataLines(String tsv) {
        return tsv.lines().filter(l -> !l.startsWith("#") && !l.isBlank()).collect(Collectors.toList());
    }

    /** The JDK class this emulator emulates, or null when SB-Emulators invented the class. */
    private static Class<?> jdkCounterpart(Class<?> c) {
        String name = c.getName();
        for (String[] p : JDK_PACKAGE) {
            if (name.startsWith(p[0] + ".")) {
                return loadOrNull(p[1] + name.substring(p[0].length()));
            }
        }
        return null;
    }

    private static Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, PortedTypesTableTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Every {@code vaadinx.**} class in the module under test, off {@link EHelper}'s classpath entry. */
    private static List<Class<?>> emulatorClasses() throws IOException {
        File dir;
        try {
            dir = new File(EHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("unreadable classpath entry for :emulators", e);
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("expected a directory classpath entry for :emulators, got " + dir);
        }
        Path base = dir.toPath();
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> toClassName(base.relativize(p)))
                    .filter(n -> n.startsWith("vaadinx."))
                    .map(PortedTypesTableTest::loadOrNull)
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        }
    }

    private static String toClassName(Path relative) {
        String s = relative.toString();
        return s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}
