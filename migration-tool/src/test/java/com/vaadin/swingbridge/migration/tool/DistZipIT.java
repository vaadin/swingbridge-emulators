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

package com.vaadin.swingbridge.migration.tool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the shipped launchers out of the assembled distribution zip, over a real Swing source
 * tree — the only place the scripts, the wildcard classpath and the zip's layout exist to be
 * tested. On Windows CI this is what exercises the {@code .cmd} twins, which no Linux run can
 * (D_kit_tool_zip).
 *
 * <p>Two contracts here are load-bearing elsewhere and so are pinned rather than described.
 * {@code lib/} carries the <b>engine only</b>: nothing Vaadin-shaped may appear in it, since
 * the whole reason this module has no {@code :emulators} dependency is that a table travels
 * with the emulators jar instead. And {@code import-swap} with no table on its classpath
 * <b>fails loudly</b> — exit 2, with a message naming the fix — which is what makes shipping
 * a tableless zip to Central safe; the with-table run belongs to the kit's {@code KitSmokeIT},
 * where the emulators jar is there to be found.
 */
class DistZipIT {

    private static final Path ZIP = Path.of(System.getProperty("dist.zip"));
    private static final Path UNPACK = Path.of(System.getProperty("dist.unpack"));
    private static final String VERSION = System.getProperty("dist.version");
    private static final Path REPO = Path.of(System.getProperty("repo.root"));

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    /** The unpacked root, {@code <unpack>/swingbridge-migration-tool-<version>}. */
    private static Path dist;

    @BeforeAll
    static void unzip() throws IOException {
        assertTrue(Files.isRegularFile(ZIP), "no zip at " + ZIP + " — did the assembly run?");
        if (Files.exists(UNPACK)) {
            try (var walk = Files.walk(UNPACK)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        try (ZipFile zip = new ZipFile(ZIP.toFile())) {
            for (ZipEntry entry : zip.stream().toList()) {
                Path target = UNPACK.resolve(entry.getName()).normalize();
                assertTrue(target.startsWith(UNPACK), "zip entry escapes the unpack dir: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var in = zip.getInputStream(entry)) {
                        Files.copy(in, target);
                    }
                }
            }
        }
        dist = UNPACK.resolve("swingbridge-migration-tool-" + VERSION);
    }

    @Test
    @DisplayName("the zip unpacks into one versioned directory carrying bin/ and lib/")
    void theZipUnpacksIntoOneVersionedDirectory() throws IOException {
        try (ZipFile zip = new ZipFile(ZIP.toFile())) {
            Set<String> roots = zip.stream()
                    .map(it -> it.getName().split("/", 2)[0])
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            assertEquals(1, roots.size(),
                    "the zip has " + roots.size() + " top-level entries " + roots
                            + " — unzipping it would litter the user's current directory.");
            assertEquals("swingbridge-migration-tool-" + VERSION, roots.iterator().next(),
                    "the zip's root directory does not name the tool and its version.");
        }

        List<String> expected = List.of(
                "README.md",
                "bin/hazard-scan",
                "bin/hazard-scan.cmd",
                "bin/import-swap",
                "bin/import-swap.cmd",
                "bin/static-sweep",
                "bin/static-sweep.cmd");
        List<String> missing = expected.stream().filter(it -> !Files.isRegularFile(dist.resolve(it))).toList();
        assertTrue(missing.isEmpty(), "the zip is missing: " + missing);
    }

    @Test
    @DisplayName("lib/ carries the engine and nothing Vaadin-shaped")
    void libCarriesTheEngineAndNothingVaadinShaped() throws IOException {
        List<String> jars;
        try (var list = Files.list(dist.resolve("lib"))) {
            jars = list.map(it -> it.getFileName().toString()).sorted().toList();
        }
        List<String> wanted = List.of(
                "swingbridge-migration-tool-" + VERSION + ".jar",
                "swingbridge-migration-annotations-" + VERSION + ".jar");
        List<String> missing = wanted.stream().filter(it -> !jars.contains(it)).toList();
        assertTrue(missing.isEmpty(), "lib/ is missing " + missing + "; it holds " + jars);
        assertTrue(jars.stream().anyMatch(it -> it.startsWith("archunit-")), "no archunit jar in lib/: " + jars);
        assertTrue(jars.stream().anyMatch(it -> it.startsWith("slf4j-api-")), "no slf4j-api jar in lib/: " + jars);

        // Not tidiness: this module's whole no-:emulators contract is that a swap table travels
        // with the emulators jar. A Vaadin jar here means that contract broke somewhere upstream.
        List<String> vaadin = jars.stream().filter(it -> it.matches("(?i).*(flow|vaadin).*")).toList();
        assertTrue(vaadin.isEmpty(), "lib/ carries " + vaadin + " — the tool zip ships the engine only.");
        assertEquals(4, jars.size(), "lib/ should hold exactly the engine's four jars, but holds " + jars);
    }

    /**
     * The zip redistributes third-party jars verbatim and publishes to Central on its own, so the
     * attribution has to travel with it: Apache-2.0 §4(a) wants a copy of the licence handed on,
     * BSD-3 and MIT want their notices reproduced, and our own two jars are Apache-2.0 too, so
     * {@code LICENSE} is that text rather than the emulator core's GPL one. Gated rather than described because the failure is silent — a new dependency lands in
     * {@code lib/} through an ordinary POM edit, ships unattributed, and is discovered by whoever
     * reads the zip rather than by whoever built it.
     */
    @Test
    @DisplayName("every third-party jar in lib/ is attributed, and the licence texts ship with it")
    void everyThirdPartyJarInLibIsAttributed() throws IOException {
        for (String required : List.of("LICENSE", "THIRD-PARTY.md", "licenses/APACHE-2.0.txt")) {
            assertTrue(Files.isRegularFile(dist.resolve(required)),
                    "the zip ships no " + required + " — it redistributes third-party jars, so the"
                            + " licence texts have to travel with them.");
        }
        for (String apacheText : List.of("LICENSE", "licenses/APACHE-2.0.txt")) {
            String apache = Files.readString(dist.resolve(apacheText), StandardCharsets.UTF_8);
            assertTrue(apache.contains("Apache License") && apache.contains("END OF TERMS AND CONDITIONS"),
                    apacheText + " is not the Apache-2.0 text — lib/'s own two jars are Apache-2.0"
                            + " (D_licence_lanes), so LICENSE must not state GPL terms over them.");
        }

        String attribution = Files.readString(dist.resolve("THIRD-PARTY.md"), StandardCharsets.UTF_8);
        List<String> jars;
        try (var list = Files.list(dist.resolve("lib"))) {
            jars = list.map(it -> it.getFileName().toString()).sorted().toList();
        }
        // "slf4j-api-2.0.18.jar" -> "slf4j-api": the version is what THIRD-PARTY.md deliberately
        // does not repeat, so the artifact id is what it can be joined on.
        List<String> unattributed = jars.stream()
                .filter(it -> !it.startsWith("swingbridge-"))
                .map(it -> it.replaceFirst("\\.jar$", "").replaceFirst("-\\d.*$", ""))
                .filter(it -> !attribution.contains(it))
                .toList();
        assertTrue(unattributed.isEmpty(), "lib/ carries " + unattributed + ", which THIRD-PARTY.md"
                + " does not name. Add a row there (and its licence text, if it is a licence the zip"
                + " does not already ship) before this dependency reaches anyone.");
    }

    @Test
    @DisplayName("the shipped hazard-scan runs and writes its worklist")
    void theShippedHazardScanRunsAndWritesItsWorklist() throws Exception {
        Path report = UNPACK.resolve("hazards.md");
        Result r = run("hazard-scan", REPO.resolve("testapps/crud/swing/src/main/java").toString(),
                "--report", report.toString());

        assertEquals(0, r.exit(), "hazard-scan exited " + r.exit() + ":\n" + r.output());
        assertTrue(Files.isRegularFile(report), "no report written. The tool said:\n" + r.output());
        String md = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(md.startsWith("# Migration hazard scan"), "not a hazard report:\n" + md);
        // Clean sections are printed rather than omitted, so this section is there either way —
        // which is what makes it a table-was-loaded assertion rather than an app-content one.
        assertTrue(md.contains("## `H_system_exit`"),
                "the report names no H_system_exit section, so the hazard table did not load:\n" + md);
    }

    /**
     * Over this module's <b>own</b> class files, for two reasons: they are the one class tree
     * guaranteed to exist at this phase (the testapps left the reactor, so their {@code target/} may
     * be empty or absent), and the sweep of a tree that carries a real {@code @IntentionallyStatic}
     * proves the annotation is read out of bytecode by the shipped jar rather than only by the
     * unit tests' compiler.
     */
    @Test
    @DisplayName("the shipped static-sweep reads a real class tree, annotation and all")
    void theShippedStaticSweepReadsARealClassTree() throws Exception {
        Path report = UNPACK.resolve("static-sweep.md");
        Result r = run("static-sweep", REPO.resolve("migration-tool/target/classes").toString(),
                "--report", report.toString());

        assertEquals(0, r.exit(), "static-sweep exited " + r.exit() + ":\n" + r.output());
        assertTrue(Files.isRegularFile(report), "no report written. The tool said:\n" + r.output());
        String md = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(md.startsWith("# Static-field sweep"), "not a sweep report:\n" + md);
        assertTrue(md.contains("## The funnel"), md);
        assertTrue(md.contains("`IMMUTABLE_CONSTANT`"),
                "the report does not print the allowlist rule, which it must carry inline:\n" + md);
        assertTrue(md.contains("| ImmutableTypes | `NAMED` |"),
                "ImmutableTypes.NAMED is a @IntentionallyStatic field in the very tree just swept,"
                        + " so it must appear on the worklist with its reason:\n" + md);
    }

    @Test
    @DisplayName("the shipped import-swap fails loudly with no swap table in lib/")
    void theShippedImportSwapFailsLoudlyWithNoSwapTable() throws Exception {
        Result r = run("import-swap", REPO.resolve("testapps/crud/swing/src/main/java").toString(),
                "--dry-run");

        assertEquals(2, r.exit(),
                "a tableless import-swap must fail loudly — a silent success is indistinguishable"
                        + " from an app that needed no changes. It exited " + r.exit() + ":\n" + r.output());
        assertTrue(r.output().contains("no META-INF/emul/ported-types.tsv"),
                "the failure does not name the missing table, so it does not name the fix:\n" + r.output());
    }

    private record Result(int exit, String output) {
    }

    /** Runs a shipped launcher through the shell, so the zip's own script is what executes. */
    private static Result run(String script, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        if (WINDOWS) {
            command.add("cmd");
            command.add("/c");
            command.add(dist.resolve("bin").resolve(script + ".cmd").toString());
        } else {
            command.add("sh");
            command.add(dist.resolve("bin").resolve(script).toString());
        }
        command.addAll(List.of(args));

        Process p = new ProcessBuilder(command)
                .directory(dist.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(5, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            fail("timed out: " + String.join(" ", command));
        }
        return new Result(p.exitValue(), output);
    }
}
