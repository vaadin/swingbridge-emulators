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
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.REPO_ROOT;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.children;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.parse;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.rel;
import static com.vaadin.swingbridge.migration.tool.TestappStandalonePomTest.text;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pins each testapp's migrated stages to its bootstrap lane — {@code crud} on Spring Boot,
 * {@code inventory} and {@code jlawyer-shape} on Vaadin Boot — so the three together keep
 * covering both lanes.
 *
 * <p>A tripwire, not a style rule: every stage after {@code swing/} is derived, rewritten
 * wholesale (pom included) by each {@code /guide-migrateapp} run following the guide
 * ({@code testapps/CLAUDE.md}). The guide names no bootstrap and the kit's {@code /migrate-testapp}
 * pins each app's lane, so a run whose agent took the other one moves the app silently; this makes
 * that red, and likewise a hand "alignment" of crud with the other two.
 *
 * <p>A lane is recognised by a dependency only that lane has, among the pom's direct
 * {@code <dependencies>}: {@code swingbridge-emulators-spring} or {@code vaadin-boot}.
 * A pom with both fails on either lane.
 */
class TestappBootstrapLaneTest {

    enum Lane {
        SPRING_BOOT("swingbridge-emulators-spring"),
        VAADIN_BOOT("vaadin-boot");

        final String markerArtifactId;

        Lane(String markerArtifactId) {
            this.markerArtifactId = markerArtifactId;
        }
    }

    /** Per app, not per stage: every stage after {@code swing/} is on the app's one lane. */
    private static final Map<String, Lane> LANES = Map.of(
            "crud", Lane.SPRING_BOOT,
            "inventory", Lane.VAADIN_BOOT,
            "jlawyer-shape", Lane.VAADIN_BOOT);

    /** Every migrated stage — each stage directory with a pom, except the pristine {@code swing/}. */
    private static List<Path> migratedStages() {
        List<Path> out = new ArrayList<>();
        try (var apps = Files.list(REPO_ROOT.resolve("testapps"))) {
            for (Path app : apps.filter(Files::isDirectory).sorted().toList()) {
                try (var stages = Files.list(app)) {
                    stages.filter(it -> !it.getFileName().toString().equals("swing"))
                            .filter(it -> Files.isRegularFile(it.resolve("pom.xml")))
                            .sorted()
                            .forEach(out::add);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    @Test
    @DisplayName("every migrated testapp stage stays on its app's bootstrap lane")
    void everyMigratedStageStaysOnItsLane() {
        List<Path> stages = migratedStages();
        assertTrue(stages.size() >= 3, "expected at least three migrated stages, found " + stages.size());

        List<String> offenders = new ArrayList<>();
        for (Path stage : stages) {
            String app = stage.getParent().getFileName().toString();
            Lane expected = LANES.get(app);
            if (expected == null) {
                offenders.add(rel(stage) + ": no lane declared for testapp '" + app
                        + "' — add it to LANES in " + TestappBootstrapLaneTest.class.getSimpleName());
                continue;
            }
            Set<Lane> found = lanesOf(parse(stage.resolve("pom.xml")));
            if (!found.equals(EnumSet.of(expected))) {
                offenders.add(rel(stage) + ": expected " + expected + ", pom is on " + found);
            }
        }
        if (!offenders.isEmpty()) {
            fail("testapp stages off their bootstrap lane:\n  " + String.join("\n  ", offenders)
                    + "\n\ncrud is the Spring Boot lane; inventory and jlawyer-shape are Vaadin Boot."
                    + "\nThat split is how the testapps cover both bootstraps. If a /guide-migrateapp run"
                    + "\njust rewrote a stage, its agent took the other bootstrap at the guide's Phase 0"
                    + "\nstep: restore the stage from git, and check the lane pins in the kit's"
                    + "\n/migrate-testapp skill (testapps/CLAUDE.md, \"Two kinds of testapp\").");
        }
    }

    private static Set<Lane> lanesOf(Document pom) {
        Set<String> artifactIds = new HashSet<>();
        for (Element dependencies : children(pom, "dependencies")) {
            for (Element dependency : children(dependencies, "dependency")) {
                for (Element artifactId : children(dependency, "artifactId")) {
                    artifactIds.add(text(artifactId));
                }
            }
        }
        Set<Lane> out = EnumSet.noneOf(Lane.class);
        for (Lane lane : Lane.values()) {
            if (artifactIds.contains(lane.markerArtifactId)) {
                out.add(lane);
            }
        }
        return out;
    }
}
