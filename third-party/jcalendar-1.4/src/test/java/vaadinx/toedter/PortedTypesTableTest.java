/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * This module reproduces the API of JCalendar 1.4 (com.toedter:jcalendar),
 * which upstream licenses under the GNU Lesser General Public License, and
 * takes that licence rather than the rest of this repository's. See
 * PROVENANCE.md beside this module for why, and M1D_addon_upstream_licence in
 * migration/1-swing-to-emulators/decisions.md for the rule it follows.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1 only,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public
 * License for more details (a copy is in the LICENSE file beside this module,
 * and inside this jar at META-INF/LICENSE).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package vaadinx.toedter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.toedter.calendar.JDateChooser;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every type this add-on ships is either mapped in {@code META-INF/emul/ported-types.tsv} or named
 * here as SB-Emulators-invented. Add a class without doing one or the other and this test fails.
 *
 * <p>The table is what the migration import-swap tool reads to rewrite a migrated app's
 * {@code com.toedter} imports, and it is unioned off the classpath alongside {@code :emulators}' own
 * — so core never learns that add-ons exist (M1D_addon_packaging).
 *
 * <p>Unlike core's table this one is <b>declared, not derived</b>: an add-on replaces its upstream,
 * so {@code Class.forName("com.toedter.calendar.JDateChooser")} fails here and there is nothing to
 * reflect against. This test is what replaces that derivation as the drift guard.
 */
class PortedTypesTableTest {

    private static final Path TABLE = Path.of("src/main/resources/META-INF/emul/ported-types.tsv");

    /** Classes with no upstream counterpart, so no row. This add-on is a clean reimplementation of one type. */
    private static final Set<String> EMULATOR_INVENTED = Set.of();

    @Test
    @DisplayName("every shipped type is either mapped in the swap table or declared SB-Emulators-invented")
    void everyTypeIsAccountedFor() throws IOException {
        Set<String> mapped = mappedTypes();
        assertTrue(!mapped.isEmpty(), "no rows read from " + TABLE);

        List<String> unaccounted = shippedTypes().stream()
                .filter(name -> !mapped.contains(name) && !EMULATOR_INVENTED.contains(name))
                .sorted()
                .toList();

        if (!unaccounted.isEmpty()) {
            fail(unaccounted.size() + " type(s) this add-on ships are in neither the swap table nor the "
                    + "SB-Emulators-invented list, so the import-swap tool will silently not rewrite a migrator's "
                    + "reference to them:\n    " + String.join("\n    ", unaccounted)
                    + "\n\nAdd a row to " + TABLE + " naming the upstream type it replaces, or add it to "
                    + "EMULATOR_INVENTED if it has no upstream counterpart.");
        }
    }

    @Test
    @DisplayName("no swap-table row points at a type this add-on does not ship")
    void noDanglingRows() throws IOException {
        Set<String> shipped = shippedTypes();
        List<String> dangling = mappedTypes().stream().filter(t -> !shipped.contains(t)).sorted().toList();
        if (!dangling.isEmpty()) {
            fail("swap-table row(s) name a type this add-on does not ship, so the tool would rewrite a "
                    + "migrator's import onto a class that is not there:\n    "
                    + String.join("\n    ", dangling));
        }
    }

    @Test
    @DisplayName("the swap table actually ships — it is on the classpath, not just in the source tree")
    void tableIsOnTheClasspath() throws IOException {
        var url = PortedTypesTableTest.class.getClassLoader().getResource("META-INF/emul/ported-types.tsv");
        assertTrue(url != null, TABLE + " exists in the source tree but is not on the classpath. Naming "
                + "<resources> in the pom replaces Maven's default src/main/resources entry — restore it, "
                + "or the tool finds no rows for this add-on and silently rewrites nothing.");
        assertTrue(new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8).contains("\t"),
                "the shipped table has no rows");
    }

    /** The right-hand column: the add-on types the table claims to provide. */
    private static Set<String> mappedTypes() throws IOException {
        return Files.readAllLines(TABLE, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.startsWith("#") && !l.isBlank() && l.contains("\t"))
                .map(l -> l.substring(l.indexOf('\t') + 1).trim())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> shippedTypes() throws IOException {
        File dir;
        try {
            dir = new File(JDateChooser.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("unreadable classpath entry for this add-on", e);
        }
        Path base = dir.toPath();
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> base.relativize(p).toString())
                    .map(s -> s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.'))
                    .filter(n -> n.startsWith("vaadinx.") && !n.contains("$"))
                    .filter(PortedTypesTableTest::isPublic)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static boolean isPublic(String fqn) {
        try {
            return Modifier.isPublic(Class.forName(fqn, false,
                    PortedTypesTableTest.class.getClassLoader()).getModifiers());
        } catch (Throwable t) {
            return false;
        }
    }
}
