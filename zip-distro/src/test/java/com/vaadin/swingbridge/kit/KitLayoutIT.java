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

package com.vaadin.swingbridge.kit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the assembled kit, over the unpacked copy the assembly leaves in
 * {@code target/unzipped/}. Runs in the default build: these are the checks that catch a
 * doc link, a placeholder or a shipped path drifting out of what the descriptor says.
 *
 * <p>Three of them are worth naming for why they exist rather than what they assert.
 * <b>The link check</b> is the end-to-end half of {@code :emulators}' {@code GuideLinkScopeTest}:
 * that one says a guide may only reach a path the kit ships, this one says the path is
 * actually there — so the pair needs no shared list to agree, and adding a fileSet without
 * adding it to that test's allow-list (or the reverse) fails on one side or the other.
 * <b>The slot check</b> keeps a {@code *.placeholder} from lingering beside the real artefact
 * once it lands, which is how a placeholder rots; it also writes the list of unfilled slots
 * to {@code target/placeholders.txt}, so "still a placeholder in this release" reaches the
 * release notes generated rather than remembered. <b>The maintainer-brief check</b> guards
 * the one file whose accidental inclusion would be most costly: this repository's own
 * {@code CLAUDE.md} is an agent brief full of decision-log pointers and emulator-author
 * rules, and shipping it in place of the kit's would point a customer's agent at a
 * repository they do not have. <b>The tools/lib check</b> is there for the one thing in the kit
 * that regresses without a symptom: the {@code *:*} exclusions on the three swap-table
 * dependencies, which are all that keep Vaadin out of a folder whose tools are supposed to
 * need nothing but a JDK.
 *
 * <p><b>The gitignore check</b> is the one that asks a general question instead of a listed one:
 * a fileSet copies a directory as it stands on the machine that ran the build, so anything
 * gitignored in a working tree — IDE state, a {@code target/}, a stray database file — ships from
 * a developer's clone and not from CI, which makes the zip's contents depend on who built it. It
 * has already cost a migration round: {@code testapps/*&#47;swing/.project} made {@code jdtls} adopt
 * the customer's copy as an Eclipse project and compile into its {@code target/}, and the migration
 * met a build failure it had not caused (2026-09-09 round). The descriptor's per-fileSet excludes
 * are the fix; this check is what saves the next fileSet from having to remember them.
 */
class KitLayoutIT {

    private static final Path KIT = Path.of(System.getProperty("kit.root"));
    private static final Path ZIP = Path.of(System.getProperty("kit.zip"));
    private static final Path SLOTS = Path.of(System.getProperty("kit.slots"));
    private static final String VERSION = System.getProperty("kit.version");

    private static final Pattern LINK = Pattern.compile("]\\(([^)\\s]+)\\)");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");

    @Test
    @DisplayName("the kit carries every path its docs, skills and commands name")
    void theKitCarriesEveryPathItsDocsSkillsAndCommandsName() {
        List<String> expected = List.of(
                "README.md",
                "CLAUDE.md",
                "LICENSE",
                "tools/README.md",
                // tools/ redistributes ArchUnit and slf4j-api verbatim, so their attribution has
                // to arrive in the kit too. It is not maintained here: both files come out of
                // :migration-tool's own zip, and DistZipIT is what fails if a jar reaches lib/
                // without a row. These two lines only assert that the unpack carried them over.
                "tools/THIRD-PARTY.md",
                "tools/licenses/APACHE-2.0.txt",
                // The kit carries more than one licence (README § "Licences"): the root LICENSE is
                // Apache-2.0, tools/LICENSE (the same text, out of the tool zip) covers the tool
                // jars, and the one GPLv2+CE jar brings its text beside it.
                "tools/LICENSE",
                "tools/lib/swingbridge-emulators-LICENSE.txt",
                "tools/bin/hazard-scan",
                "tools/bin/hazard-scan.cmd",
                "tools/bin/import-swap",
                "tools/bin/import-swap.cmd",
                "guides/1-swing-to-emulators/guide.md",
                "guides/1-swing-to-emulators/static-fields.md",
                "guides/1-swing-to-emulators/former-singletons.md",
                "guides/1-swing-to-emulators/lifecycle.md",
                "guides/1-swing-to-emulators/build-wiring.md",
                "guides/1-swing-to-emulators/host-app-spring-boot.md",
                "guides/1-swing-to-emulators/host-app-vaadin-boot.md",
                // The host-app seeds a migrator copies, one per bootstrap. Shipped as real files
                // rather than as listings inside a guide, because a listing rots unrun: the
                // template these replaced still configured exec:java, which cannot pass the
                // --add-opens flag that keeps modal dialogs from deadlocking. -Pkit builds each.
                "guides/1-swing-to-emulators/seed/vaadin-boot/example-pom.xml",
                "guides/1-swing-to-emulators/seed/vaadin-boot/example-build.gradle.kts",
                "guides/1-swing-to-emulators/seed/vaadin-boot/src/main/java/com/example/app/Main.java",
                "guides/1-swing-to-emulators/seed/spring-boot/example-pom.xml",
                "guides/1-swing-to-emulators/seed/spring-boot/src/main/java/com/example/app/Main.java",
                "guides/1-swing-to-emulators/import-swap-reference.md",
                "guides/1-swing-to-emulators/dates.md",
                "guides/1-swing-to-emulators/runtime-contract.md",
                "guides/1-swing-to-emulators/third-party-libraries.md",
                "guides/1-swing-to-emulators/platform.md",
                "guides/1-swing-to-emulators/addons.md",
                "guides/1-swing-to-emulators/agent-prompt.md",
                "third-party/jcalendar-1.4/MIGRATION.md",
                // Both add-ons' LICENSE files travel, because neither is under the kit's root
                // one: each carries its upstream library's licence (M1D_addon_upstream_licence),
                // LGPL-2.1 here and BSD next door. A reader who found only the root Apache-2.0
                // would conclude the wrong terms for both.
                "third-party/jcalendar-1.4/LICENSE",
                "third-party/jgoodies-forms-1.2.1/MIGRATION.md",
                "third-party/jgoodies-forms-1.2.1/LICENSE",
                "testapps/crud/README.md",
                "testapps/crud/swing/pom.xml",
                "testapps/crud/swing/mvnw",
                "testapps/crud/1-emulators/pom.xml",
                "testapps/jlawyer-shape/README.md",
                "testapps/jlawyer-shape/swing/pom.xml",
                "testapps/inventory/README.md",
                "testapps/inventory/PROVENANCE.md",
                "testapps/inventory/swing/pom.xml",
                "testapps/inventory/swing/mvnw",
                ".claude/skills/migrate-testapp/SKILL.md",
                ".claude/skills/migrate-swing-app/SKILL.md",
                ".claude/skills/migrate-your-app/SKILL.md",
                "your-app/swing/README.md");

        List<String> missing = expected.stream().filter(it -> !Files.exists(KIT.resolve(it))).toList();
        if (!missing.isEmpty()) {
            fail("the assembled kit is missing:\n  " + String.join("\n  ", missing)
                    + "\n\nEvery one of these is named by the welcome README, a skill, or a command in"
                    + "\nthe guides. Add the fileSet to src/main/assembly/dist.xml.");
        }
    }

    @Test
    @DisplayName("each licence file holds the text its location claims")
    void eachLicenceFileHoldsTheTextItsLocationClaims() throws IOException {
        // The repository's two root texts ship under swapped names — its LICENSE-APACHE-2.0 as the
        // kit's LICENSE, its LICENSE beside the emulators jar — so a descriptor edit that mirrors
        // the repository's names instead would put GPL text at the kit root, silently.
        for (String apache : List.of("LICENSE", "tools/LICENSE")) {
            assertTrue(Files.readString(KIT.resolve(apache)).contains("Apache License"),
                    apache + " is not the Apache-2.0 text — everything Vaadin wrote in the kit is"
                            + " on that lane (PROVENANCE.md § Lanes)");
        }
        // The seeds are copied into the migrator's own app, so their licence is the one grant in
        // the kit that must carry no conditions at all (PROVENANCE.md § HDR_vaadin_0bsd).
        String seeds = Files.readString(KIT.resolve("guides/1-swing-to-emulators/seed/LICENSE"));
        assertTrue(seeds.contains("0BSD") && seeds.contains("Permission to use, copy, modify, and/or distribute")
                        && !seeds.contains("Apache License"),
                "guides/1-swing-to-emulators/seed/LICENSE is not the 0BSD text of the seeds beside it");
        String gpl = Files.readString(KIT.resolve("tools/lib/swingbridge-emulators-LICENSE.txt"));
        assertTrue(gpl.contains("GNU General Public License") && gpl.contains("\"CLASSPATH\" EXCEPTION"),
                "tools/lib/swingbridge-emulators-LICENSE.txt is not the GPLv2+CE text of the jar"
                        + " beside it");
    }

    @Test
    @DisplayName("the shipped CLAUDE.md is the kit's brief, not the repository's")
    void theShippedClaudeMdIsTheKitsBriefNotTheRepositorys() throws IOException {
        String shipped = Files.readString(KIT.resolve("CLAUDE.md"));
        assertTrue(shipped.startsWith("# SwingBridge Emulators migration kit"),
                "the kit's CLAUDE.md does not start with the kit brief's own heading — has"
                        + " src/main/kit/kit-CLAUDE.md's destName mapping been lost?");
        if (shipped.contains("Hard rules") || shipped.contains("decisions.md")) {
            fail("the kit's CLAUDE.md reads like the repository's maintainer brief."
                    + "\n\nThe kit ships a short agent-facing brief of its own: the layout, the two"
                    + "\nplaceholders, the three skills. Decision-log pointers and emulator-author"
                    + "\nrules point a customer's agent at a repository they do not have.");
        }
    }

    @Test
    @DisplayName("every slot is filled exactly once — the real artefact or its placeholder, never both")
    void everySlotIsFilledExactlyOnce() throws IOException {
        List<String> slots = Files.readAllLines(SLOTS).stream()
                .map(String::trim)
                .filter(it -> !it.isEmpty() && !it.startsWith("#"))
                .toList();
        assertTrue(!slots.isEmpty(), "no slots declared in " + SLOTS + " — is the file readable?");

        List<String> problems = new ArrayList<>();
        List<String> unfilled = new ArrayList<>();
        for (String slot : slots) {
            boolean real = Files.exists(KIT.resolve(slot));
            boolean placeholder = Files.exists(KIT.resolve(slot + ".placeholder"));
            if (real && placeholder) {
                problems.add(slot + ": both the artefact and its placeholder ship — delete the placeholder");
            } else if (!real && !placeholder) {
                problems.add(slot + ": neither the artefact nor a placeholder ships — write the placeholder, or drop the slot");
            } else if (placeholder) {
                unfilled.add(slot);
            }
        }

        // A placeholder for something no longer promised is the other way this rots.
        for (Path stray : placeholders()) {
            String slot = KIT.relativize(stray).toString().replace('\\', '/');
            slot = slot.substring(0, slot.length() - ".placeholder".length());
            if (!slots.contains(slot)) {
                problems.add(slot + ".placeholder ships but names no declared slot — add it to kit-slots.txt or delete it");
            }
        }

        Path report = Path.of(System.getProperty("kit.zip")).getParent().resolve("placeholders.txt");
        Files.writeString(report, unfilled.isEmpty()
                ? "This release fills every slot.\n"
                : String.join("\n", unfilled) + "\n");

        if (!problems.isEmpty()) {
            fail("kit slots:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("every relative link in the shipped docs resolves inside the kit")
    void everyRelativeLinkInTheShippedDocsResolvesInsideTheKit() {
        Set<String> broken = new LinkedHashSet<>();
        for (Path doc : shippedMarkdown()) {
            Matcher m = LINK.matcher(stripFences(read(doc)));
            while (m.find()) {
                String target = m.group(1);
                if (target.startsWith("http") || target.startsWith("#") || target.startsWith("mailto:")) {
                    continue;
                }
                String pathPart = target.split("#", 2)[0];
                if (pathPart.isEmpty()) {
                    continue;
                }
                Path resolved = doc.getParent().resolve(pathPart).normalize();
                if (!resolved.startsWith(KIT)) {
                    broken.add(rel(doc) + " → " + target + " (escapes the kit root)");
                } else if (!Files.exists(resolved)) {
                    broken.add(rel(doc) + " → " + target + " (not shipped)");
                }
            }
        }
        if (!broken.isEmpty()) {
            fail("links a downloaded kit cannot follow:\n  " + String.join("\n  ", broken)
                    + "\n\nEither ship the target (a fileSet in dist.xml, plus LINKABLE in"
                    + "\n:emulators' GuideLinkScopeTest) or make the link absolute.");
        }
    }

    @Test
    @DisplayName("tools/lib carries the engine and the three swap tables, and nothing Vaadin-shaped")
    void toolsLibCarriesTheEngineAndTheThreeSwapTables() throws IOException {
        List<String> jars;
        try (var list = Files.list(KIT.resolve("tools/lib"))) {
            jars = list.map(it -> it.getFileName().toString()).sorted().toList();
        }
        List<String> expected = List.of(
                "swingbridge-migration-tool-" + VERSION + ".jar",
                "swingbridge-migration-annotations-" + VERSION + ".jar",
                "swingbridge-emulators-" + VERSION + ".jar",
                "swingbridge-emulators-jcalendar-1.4-" + VERSION + ".jar",
                "swingbridge-emulators-jgoodies-forms-1.2.1-" + VERSION + ".jar");
        List<String> missing = expected.stream().filter(it -> !jars.contains(it)).toList();
        if (!missing.isEmpty()) {
            fail("tools/lib is missing:\n  " + String.join("\n  ", missing)
                    + "\n\nit holds " + jars
                    + "\n\nThe first two arrive inside :migration-tool's distribution zip; the other"
                    + "\nthree are this module's own dependencies, and each one is a swap table"
                    + "\nImportSwap unions off the classpath.");
        }

        // The exclusions holding is the thing that regresses silently: without them the emulators
        // jar drags all of Vaadin into a kit whose tools are supposed to need only a JDK.
        List<String> vaadin = jars.stream()
                .filter(it -> it.matches("(?i).*(flow|vaadin).*") && !it.startsWith("swingbridge-"))
                .toList();
        assertTrue(vaadin.isEmpty(), "tools/lib carries " + vaadin
                + " — the *:* exclusions on the swap-table dependencies have stopped holding.");
    }

    @Test
    @DisplayName("the kit ships nothing this repository's .gitignore covers")
    void theKitShipsNothingGitIgnores() throws IOException, InterruptedException {
        // The kit mirrors the repository's geometry, so a shipped path IS the repo path git can be
        // asked about. tools/ is the one tree that does not mirror — it is :migration-tool's own
        // distribution zip, unpacked, whose launchers are tracked at migration-tool/src/main/dist/bin
        // and would answer the root `bin` rule from a path they do not live at.
        List<String> shipped = walk(it -> true).stream()
                .map(KitLayoutIT::rel)
                .filter(it -> !it.startsWith("tools/"))
                .toList();
        assertTrue(shipped.size() > 100, "only " + shipped.size() + " files in the kit — is it assembled?");

        List<String> ignored = gitCheckIgnore(shipped);
        if (!ignored.isEmpty()) {
            fail("the kit ships " + ignored.size() + " file(s) this repository ignores:\n  "
                    + String.join("\n  ", ignored)
                    + "\n\nA fileSet copies a directory as it stands on the machine that built it, so"
                    + "\nthese ship from a developer's tree and not from CI — the zip's contents depend"
                    + "\non who ran the build. Add the pattern to the fileSet's <excludes> in"
                    + "\nsrc/main/assembly/dist.xml.");
        }
    }

    /**
     * The subset of {@code paths} that this repository's ignore rules cover.
     *
     * <p>{@code --no-index} is the point: without it a tracked file reads as not-ignored, and the
     * question here is what the rules say, not what history happens to contain.
     */
    private static List<String> gitCheckIgnore(List<String> paths) throws IOException, InterruptedException {
        Path repo = Path.of("..").toAbsolutePath().normalize();
        assumeTrue(Files.isDirectory(repo.resolve(".git")), repo + " is not a git worktree");

        Process git = new ProcessBuilder("git", "check-ignore", "--no-index", "--stdin")
                .directory(repo.toFile())
                .redirectErrorStream(false)
                .start();
        try (var out = git.getOutputStream()) {
            out.write(String.join("\n", paths).getBytes(StandardCharsets.UTF_8));
        }
        List<String> ignored = new ArrayList<>(new String(git.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).lines().filter(it -> !it.isBlank()).toList());
        int exit = git.waitFor();
        // 0 = something matched, 1 = nothing did. Anything else is git failing to answer, which is a
        // broken check rather than a clean kit — the one thing this must not report as a pass.
        assertTrue(exit == 0 || exit == 1, "git check-ignore exited " + exit
                + " — the check could not run, so it is not evidence the kit is clean.");
        ignored.sort(String::compareTo);
        return ignored;
    }

    @Test
    @DisplayName("the zip unpacks into one versioned directory, not into the current one")
    void theZipUnpacksIntoOneVersionedDirectory() throws IOException {
        assertTrue(Files.isRegularFile(ZIP), "no zip at " + ZIP);
        try (ZipFile zip = new ZipFile(ZIP.toFile())) {
            Set<String> roots = zip.stream()
                    .map(it -> it.getName().split("/", 2)[0])
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            assertTrue(roots.size() == 1,
                    "the zip has " + roots.size() + " top-level entries " + roots
                            + " — unzipping it would litter the user's current directory.");
            assertTrue(roots.iterator().next().startsWith("swingbridge-emulators-"),
                    "the zip's root directory is " + roots + ", which does not name the product and"
                            + " its version.");
        }
    }

    private static List<Path> placeholders() {
        return walk(it -> it.getFileName().toString().endsWith(".placeholder"));
    }

    private static List<Path> shippedMarkdown() {
        List<Path> docs = walk(it -> it.getFileName().toString().endsWith(".md"));
        assertTrue(docs.size() >= 10, "only " + docs.size() + " markdown files in the kit — is it assembled?");
        return docs;
    }

    private static List<Path> walk(java.util.function.Predicate<Path> keep) {
        try (var walk = Files.walk(KIT)) {
            return walk.filter(Files::isRegularFile).filter(keep).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Blanks fenced code blocks: a shell or YAML sample may contain something link-shaped. */
    private static String stripFences(String text) {
        StringBuilder out = new StringBuilder();
        boolean inFence = false;
        for (String line : text.split("\n", -1)) {
            if (FENCE.matcher(line).matches()) {
                inFence = !inFence;
                out.append('\n');
            } else {
                out.append(inFence ? "" : line).append('\n');
            }
        }
        return out.toString();
    }

    private static String read(Path p) {
        try {
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + p, e);
        }
    }

    private static String rel(Path p) {
        return KIT.relativize(p).toString().replace('\\', '/');
    }
}
