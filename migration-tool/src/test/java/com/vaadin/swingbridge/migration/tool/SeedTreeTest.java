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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.REPO_ROOT;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.children;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.parse;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.property;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.rel;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.text;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The default-build half of the gate on {@code guides/1-swing-to-emulators/seed/<lane>/}, the host-app
 * trees a migrator copies into their app. {@code :zip-distro}'s {@code -Pkit} profile is the other
 * half: it builds each shipped seed from its {@code example-pom.xml}, which this module cannot, since
 * the seeds resolve SB-Emulators by coordinate.
 *
 * <p>What is checked here is what goes stale with no build failure: the version pins, which resolve
 * against an old {@code ~/.m2} SNAPSHOT long after the reactor moved on; the classes both lanes
 * share, which two copies let drift; and the main class each build file names.
 */
class SeedTreeTest {

    private static final Path SEEDS = REPO_ROOT.resolve("guides/1-swing-to-emulators/seed");

    /** Identical on every lane: neither mentions the bootstrap. */
    private static final List<String> SHARED = List.of(
            "src/main/java/com/example/app/AppShell.java",
            "src/main/java/com/example/app/AppRoute.java");

    private static final String MAIN_CLASS = "com.example.app.Main";

    private static final Pattern GRADLE_VAL = Pattern.compile("^val (\\w+) = \"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern GRADLE_VAADIN_PLUGIN = Pattern.compile("id\\(\"com\\.vaadin\"\\) version \"([^\"]+)\"");

    private static List<Path> lanes() {
        try (var dirs = Files.list(SEEDS)) {
            List<Path> out = dirs.filter(Files::isDirectory).sorted().toList();
            assertTrue(out.size() >= 2, "expected a seed per bootstrap lane under " + rel(SEEDS) + ", found " + out);
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("every seed build file pins the versions the reactor builds")
    void everySeedPinEqualsTheRootPoms() {
        Document root = parse(REPO_ROOT.resolve("pom.xml"));
        String reactor = text(children(root, "version").get(0));
        String vaadin = property(root, "vaadin.version");
        String springBoot = property(root, "spring-boot.version");

        List<String> drifted = new ArrayList<>();
        for (Path lane : lanes()) {
            Path pomFile = lane.resolve("example-pom.xml");
            assertTrue(Files.isRegularFile(pomFile), rel(lane) + " has no example-pom.xml");
            Document pom = parse(pomFile);
            check(drifted, pomFile, "swingbridge-emulators.version", property(pom, "swingbridge-emulators.version"), reactor);
            check(drifted, pomFile, "vaadin.version", property(pom, "vaadin.version"), vaadin);
            String declaredSpringBoot = property(pom, "spring-boot.version");
            if (declaredSpringBoot != null) {
                check(drifted, pomFile, "spring-boot.version", declaredSpringBoot, springBoot);
            }

            Path gradle = lane.resolve("example-build.gradle.kts");
            if (Files.isRegularFile(gradle)) {
                String kts = read(gradle);
                check(drifted, gradle, "swingbridgeEmulatorsVersion", gradleVal(kts, "swingbridgeEmulatorsVersion"), reactor);
                check(drifted, gradle, "vaadinVersion", gradleVal(kts, "vaadinVersion"), vaadin);
                Matcher plugin = GRADLE_VAADIN_PLUGIN.matcher(kts);
                check(drifted, gradle, "the com.vaadin plugin", plugin.find() ? plugin.group(1) : null, vaadin);
            }
        }
        if (!drifted.isEmpty()) {
            fail("seed build files pinning a version the reactor no longer builds:\n  "
                    + String.join("\n  ", drifted)
                    + "\n\nA stale pin resolves against a ~/.m2 that still holds the old SNAPSHOT, so it"
                    + "\nfails first for a stranger who unzipped the kit. Bump it with the root pom.");
        }
    }

    @Test
    @DisplayName("the host-app classes every lane shares are byte-identical across the seeds")
    void sharedSeedFilesAreByteIdentical() throws IOException {
        List<Path> lanes = lanes();
        List<String> diverged = new ArrayList<>();
        for (String shared : SHARED) {
            Path first = lanes.get(0).resolve(shared);
            assertTrue(Files.isRegularFile(first), rel(first) + " is missing");
            for (Path lane : lanes.subList(1, lanes.size())) {
                Path other = lane.resolve(shared);
                if (!Files.isRegularFile(other) || !Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(other))) {
                    diverged.add(rel(other) + " differs from " + rel(first));
                }
            }
        }
        if (!diverged.isEmpty()) {
            fail("shared seed files that have drifted apart:\n  " + String.join("\n  ", diverged)
                    + "\n\nThese classes name no bootstrap, so a fix to one belongs in every lane's copy.");
        }
    }

    @Test
    @DisplayName("every seed build file launches the seed's own Main")
    void everyBuildFileNamesTheSeedsOwnMain() {
        List<String> wrong = new ArrayList<>();
        for (Path lane : lanes()) {
            if (!Files.isRegularFile(lane.resolve("src/main/java/" + MAIN_CLASS.replace('.', '/') + ".java"))) {
                wrong.add(rel(lane) + ": no " + MAIN_CLASS);
            }
            for (String buildFile : List.of("example-pom.xml", "example-build.gradle.kts")) {
                Path file = lane.resolve(buildFile);
                if (Files.isRegularFile(file) && !read(file).contains(MAIN_CLASS)) {
                    wrong.add(rel(file) + " does not name " + MAIN_CLASS);
                }
            }
        }
        if (!wrong.isEmpty()) {
            fail("seed build files that would launch a class the seed does not have:\n  "
                    + String.join("\n  ", wrong));
        }
    }

    /**
     * The recommended Spring Boot launch is {@code java -jar} with no flag on the line, which works
     * only because the jar's manifest carries {@code Add-Opens}. Losing the entry is silent until
     * the packaged app refuses to start in production.
     */
    @Test
    @DisplayName("the Spring seed's jar manifest carries the Add-Opens entry the fat-jar launch relies on")
    void springSeedManifestCarriesAddOpens() {
        String pom = read(SEEDS.resolve("spring-boot/example-pom.xml"));
        assertTrue(pom.contains("<artifactId>maven-jar-plugin</artifactId>")
                        && pom.contains("<Add-Opens>java.base/java.lang</Add-Opens>"),
                "seed/spring-boot/example-pom.xml no longer puts Add-Opens java.base/java.lang in the"
                        + " jar manifest, so `java -jar` on the packaged app fails with an"
                        + " InaccessibleObjectException (M1D_bootstrap_choice, launch shape).");
    }

    /**
     * A live {@code pom.xml} inside the kit is a project to the language server before the migrator
     * has touched anything, and it compiles into the kit — the 2026-09-09 round met exactly that as
     * a build failure it had not caused. Hence {@code example-pom.xml}.
     */
    @Test
    @DisplayName("nothing under guides/ is named like a live build file")
    void nothingUnderGuidesIsALiveBuildFile() throws IOException {
        List<String> live;
        try (var walk = Files.walk(REPO_ROOT.resolve("guides"))) {
            live = walk.filter(Files::isRegularFile)
                    .filter(p -> List.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle.kts")
                            .contains(p.getFileName().toString()))
                    .map(TestappStandalonePomTest::rel)
                    .toList();
        }
        if (!live.isEmpty()) {
            fail("live build files under guides/:\n  " + String.join("\n  ", live)
                    + "\n\nName them example-pom.xml / example-build.gradle.kts, so no tool imports the"
                    + "\nkit's copy as a project. The migrator renames it when they copy it.");
        }
    }

    private static void check(List<String> drifted, Path file, String name, String actual, String expected) {
        if (actual == null || !actual.equals(expected)) {
            drifted.add(rel(file) + ": " + name + " is " + actual + ", root pom says " + expected);
        }
    }

    private static String gradleVal(String kts, String name) {
        Matcher m = GRADLE_VAL.matcher(kts);
        while (m.find()) {
            if (m.group(1).equals(name)) {
                return m.group(2);
            }
        }
        return null;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
