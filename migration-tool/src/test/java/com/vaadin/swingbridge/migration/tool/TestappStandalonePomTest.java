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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the three properties that make a testapp stage buildable the way a migrator
 * builds it: no parent, the Maven wrapper beside the pom, and version pins that match
 * SB-Emulators' own.
 *
 * <p>The testapps left the reactor so that a migration run has to write what
 * the guide and its host-app seeds tell a migrator to write, rather than inheriting it
 * (D_distribution_zip). The cost of that is three-way duplication: with no parent to
 * inherit from, each stage-2 pom carries its own {@code swingbridge-emulators.version} and
 * {@code vaadin.version}, and a version bump in the root pom leaves them behind — silently,
 * because the stale pin resolves fine against a {@code ~/.m2} that still holds the old
 * SNAPSHOT. This test is the guard that makes the duplication affordable; without it, the
 * first symptom is a shipped kit whose example app pins a version Central does not have.
 *
 * <p>Lives here rather than in {@code :emulators} because this module's golden tests already
 * read the testapps off disk, so the relative path out of a module directory is already a
 * thing this suite knows how to do.
 */
class TestappStandalonePomTest {

    static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    /** Every stage directory, discovered rather than listed — a new app is covered for free. */
    private static List<Path> stageDirs() {
        List<Path> out = new ArrayList<>();
        try (var apps = Files.list(REPO_ROOT.resolve("testapps"))) {
            for (Path app : apps.filter(Files::isDirectory).sorted().toList()) {
                try (var stages = Files.list(app)) {
                    stages.filter(it -> Files.isRegularFile(it.resolve("pom.xml")))
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
    @DisplayName("every testapp stage pom is standalone — no parent to inherit the scaffold from")
    void everyTestappStagePomIsStandalone() {
        List<Path> stages = stageDirs();
        assertTrue(stages.size() >= 6, "expected at least six stage directories, found " + stages.size());

        List<String> offenders = new ArrayList<>();
        for (Path stage : stages) {
            if (!children(parse(stage.resolve("pom.xml")), "parent").isEmpty()) {
                offenders.add(rel(stage));
            }
        }
        if (!offenders.isEmpty()) {
            fail("testapp poms with a <parent>: " + offenders
                    + "\n\nA testapp must build the way a migrator's app builds — from its own"
                    + "\ndirectory, with everything the migration guide says to write actually"
                    + "\nwritten. Inheriting swingbridge-emulators-parent hands the run the BOM import, the"
                    + "\nversions, the enforcer and the surefire config, which is what the run is"
                    + "\nsupposed to be measuring.");
        }
    }

    @Test
    @DisplayName("every testapp stage carries the Maven wrapper, so the kit's prerequisites stay one JDK")
    void everyTestappStageCarriesTheMavenWrapper() {
        List<String> missing = new ArrayList<>();
        for (Path stage : stageDirs()) {
            for (String needed : List.of("mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties")) {
                if (!Files.isRegularFile(stage.resolve(needed))) {
                    missing.add(rel(stage) + "/" + needed);
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("testapp stages missing the Maven wrapper:\n  " + String.join("\n  ", missing)
                    + "\n\nThe kit ships these trees to people who have a JDK and nothing else."
                    + "\nCopy them from the repo root: mvnw, mvnw.cmd, .mvn/wrapper/.");
        }
    }

    @Test
    @DisplayName("every testapp version pin equals the root pom's")
    void everyTestappVersionPinEqualsTheRootPoms() {
        Document root = parse(REPO_ROOT.resolve("pom.xml"));
        String reactorVersion = text(children(root, "version").get(0));
        String reactorVaadin = property(root, "vaadin.version");

        List<String> drifted = new ArrayList<>();
        for (Path stage : stageDirs()) {
            Document pom = parse(stage.resolve("pom.xml"));
            check(drifted, rel(stage), "swingbridge-emulators.version",
                    property(pom, "swingbridge-emulators.version"), reactorVersion);
            check(drifted, rel(stage), "vaadin.version",
                    property(pom, "vaadin.version"), reactorVaadin);
        }
        if (!drifted.isEmpty()) {
            fail("testapp poms pinning a version the reactor no longer builds:\n  "
                    + String.join("\n  ", drifted)
                    + "\n\nA stale pin resolves against a ~/.m2 that still holds the old SNAPSHOT,"
                    + "\nso it fails first for a stranger who unzipped the kit. Bump the pins with"
                    + "\nthe root pom's <version>, or with its vaadin.version.");
        }
    }

    /** A stage that declares neither property is a stage-1 app with no SB-Emulators dependency. */
    private static void check(List<String> drifted, String stage, String name, String actual, String expected) {
        if (actual != null && !actual.equals(expected)) {
            drifted.add(stage + ": " + name + " is " + actual + ", root pom says " + expected);
        }
    }

    static String property(Document pom, String name) {
        List<Element> properties = children(pom, "properties");
        if (properties.isEmpty()) {
            return null;
        }
        List<Element> found = children(properties.get(0), name);
        return found.isEmpty() ? null : text(found.get(0));
    }

    /**
     * Direct children by tag name. Not {@code getElementsByTagName}, which would reach a
     * {@code <version>} nested in a dependency and answer the wrong question.
     */
    static List<Element> children(Node parent, String tag) {
        Node from = parent instanceof Document doc ? doc.getDocumentElement() : parent;
        List<Element> out = new ArrayList<>();
        NodeList kids = from.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            if (kids.item(i) instanceof Element e && e.getTagName().equals(tag)) {
                out.add(e);
            }
        }
        return out;
    }

    static String text(Element e) {
        return e.getTextContent().trim();
    }

    static Document parse(Path pom) {
        try {
            // Namespace-unaware on purpose: tag names then match as written, and a pom's
            // default namespace would otherwise force a prefix on every lookup here.
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse " + pom, e);
        }
    }

    static String rel(Path p) {
        return REPO_ROOT.relativize(p).toString().replace(File.separatorChar, '/');
    }
}
