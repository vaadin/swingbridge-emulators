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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the migrator-facing {@code guides/} tree against links and placeholders that
 * only work inside this repository.
 *
 * <p>{@code guides/} ships verbatim in the distribution kit, at that same path
 * (D_distribution_zip) — the kit mirrors the repo's geometry precisely so that these
 * relative links keep resolving after the unzip. What the kit does <em>not</em> ship is the
 * maintainer surface: {@code CLAUDE.md}, the decision logs, the module READMEs, the
 * testapps other than {@code crud}. A link from a guide into any of those is a dead link
 * in the download, and — the reason it is worth a test rather than a link-checker — it is a
 * persona leak even where it resolves, since a migrator has no business being sent to a
 * decision log. Fix such a link by stating the rationale in place, or by pointing at an
 * absolute GitHub URL under a "further reading" note.
 *
 * <p>The placeholder check is the same failure in another spelling. A command in a guide
 * says {@code SWINGBRIDGE_HOME/tools/bin/import-swap …}, and a reader who cannot tell what that stands
 * for is looking at a dead link with no brackets around it — so every document that uses a
 * placeholder must declare it, each document being read standalone.
 *
 * <p>The kit's own integration test re-checks link resolution over the <em>shipped</em>
 * tree, which is the end-to-end version of this and needs no shared list to agree with:
 * this test says a guide may only reach a shipped path, that one says the path is there.
 *
 * <p>Two further checks exist because a step in {@code guide.md} is a {@code - [ ]} line the
 * migrator ticks in their own copy of this folder, made with one {@code cp -r}. That copy
 * is why a link may not leave the folder even to something the kit ships — in the copy, a
 * relative path out of it resolves against the migrated app instead — and the {@code S_}
 * slug on each step line is what a stumble log cites, so a duplicated or missing one
 * silently breaks the citation. Fragments are resolved rather than merely stripped: moving
 * a chapter out of {@code guide.md} used to leave every inbound {@code #anchor} dangling
 * with nothing going red.
 */
class GuideLinkScopeTest {

    private final File repoRoot = findRepoRoot();

    private static File findRepoRoot() {
        for (File dir = new File(".").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            if (new File(dir, "DECISION-ID-MAP.md").isFile()) {
                return dir;
            }
        }
        throw new IllegalStateException("cannot locate the repo root — no DECISION-ID-MAP.md in any parent of "
                + new File(".").getAbsolutePath());
    }

    /**
     * Repo-relative paths a guide may link into. {@code guides/} alone: a step directory is
     * copied whole into the app being migrated, so a link that leaves it resolves against
     * the wrong tree there — write the path out under {@code SWINGBRIDGE_HOME} instead, which
     * the placeholder check then makes the document declare.
     */
    private static final List<String> LINKABLE = List.of("guides");

    private static final List<String> PLACEHOLDERS = List.of("SWINGBRIDGE_HOME", "MIGRATED_APP_FOLDER");

    private static final Pattern LINK = Pattern.compile("]\\(([^)\\s]+)\\)");
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,}).*$");
    /** An {@code <a id="…">} anchor, the only kind that survives a heading reword. */
    private static final Pattern ANCHOR = Pattern.compile("<a id=\"([^\"]+)\"");
    private static final Pattern HEADING = Pattern.compile("^#{1,6} +(.*?)\\s*$", Pattern.MULTILINE);
    /** A step line in a phase: a checkbox carrying the slug a stumble log cites. */
    private static final Pattern STEP = Pattern.compile("^ *- \\[[ x]] +(.*)$", Pattern.MULTILINE);

    @Test
    @DisplayName("every relative link in guides/ resolves, and points at something the kit ships")
    void everyRelativeLinkInGuidesResolvesAndPointsAtSomethingTheKitShips() {
        List<File> docs = guideDocs();
        assertTrue(docs.size() >= 7, "expected the whole guides tree, found " + docs.size() + " docs");

        Map<String, List<String>> bad = new TreeMap<>();
        for (File doc : docs) {
            Matcher m = LINK.matcher(stripFences(read(doc)));
            while (m.find()) {
                String target = m.group(1);
                if (target.startsWith("http") || target.startsWith("#") || target.startsWith("mailto:")) {
                    continue;
                }
                String[] parts = target.split("#", 2);
                String pathPart = parts[0];
                if (pathPart.isEmpty()) {
                    continue;
                }
                Path resolved = doc.toPath().getParent().resolve(pathPart).normalize();
                String rel = rel(resolved);
                String why = null;
                if (!Files.exists(resolved)) {
                    why = "does not exist";
                } else if (LINKABLE.stream().noneMatch(it -> rel.equals(it) || rel.startsWith(it + "/"))) {
                    why = "leaves the step directory (" + rel + ") — write the path out under SWINGBRIDGE_HOME";
                } else if (parts.length == 2 && !anchorsOf(resolved.toFile()).contains(parts[1])) {
                    why = "no such anchor in " + rel;
                }
                if (why != null) {
                    bad.computeIfAbsent(target + " — " + why, k -> new ArrayList<>()).add(rel(doc.toPath()));
                }
            }
        }
        if (!bad.isEmpty()) {
            fail("links in guides/ that a downloaded kit cannot follow:\n" + render(bad)
                    + "\n\nState the rationale in place and drop the link, or use an absolute"
                    + "\nhttps://github.com/vaadin/swingbridge-emulators/blob/main/… URL under a"
                    + "\n\"further reading\" note. Shipping a new path means adding it to the kit's"
                    + "\nassembly descriptor and to LINKABLE in this test.");
        }
    }

    @Test
    @DisplayName("every placeholder a guide uses is declared in that guide's own Conventions block")
    void everyPlaceholderAGuideUsesIsDeclaredInThatGuidesOwnConventionsBlock() {
        Map<String, List<String>> undeclared = new TreeMap<>();
        for (File doc : guideDocs()) {
            String text = read(doc);
            for (String placeholder : PLACEHOLDERS) {
                if (text.contains(placeholder) && !declares(text, placeholder)) {
                    undeclared.computeIfAbsent(placeholder, k -> new ArrayList<>()).add(rel(doc.toPath()));
                }
            }
        }
        if (!undeclared.isEmpty()) {
            fail("placeholders used but not declared in the using document:\n" + render(undeclared)
                    + "\n\nEach document is read standalone, so repeat the Conventions block verbatim"
                    + "\nrather than relying on guide.md's. An undeclared placeholder is a dead link"
                    + "\nwithout the brackets.");
        }
    }

    @Test
    @DisplayName("every guide.md step is a checkbox carrying exactly one unique S_ slug")
    void everyGuideStepIsACheckboxCarryingExactlyOneUniqueSlug() {
        File guide = new File(repoRoot, "guides/1-swing-to-emulators/guide.md");
        assertTrue(guide.isFile(), "no guide.md at " + guide);

        Map<String, List<String>> bad = new TreeMap<>();
        Map<String, Integer> seen = new TreeMap<>();
        Matcher m = STEP.matcher(stripFences(read(guide)));
        while (m.find()) {
            String line = m.group(1);
            List<String> slugs = new ArrayList<>();
            Matcher a = ANCHOR.matcher(line);
            while (a.find()) {
                slugs.add(a.group(1));
            }
            String label = line.length() > 60 ? line.substring(0, 60) + "…" : line;
            if (slugs.size() != 1) {
                bad.computeIfAbsent(slugs.size() + " slugs on one step, want 1", k -> new ArrayList<>()).add(label);
                continue;
            }
            String slug = slugs.get(0);
            if (!slug.startsWith("S_")) {
                bad.computeIfAbsent("slug does not start with S_: " + slug, k -> new ArrayList<>()).add(label);
            }
            if (seen.merge(slug, 1, Integer::sum) > 1) {
                bad.computeIfAbsent("slug used more than once: " + slug, k -> new ArrayList<>()).add(label);
            }
        }
        assertTrue(seen.size() >= 20, "expected the whole phase list, found " + seen.size() + " steps");
        if (!bad.isEmpty()) {
            fail("guide.md steps that cannot be cited by slug:\n" + render(bad)
                    + "\n\nEvery `- [ ]` step carries one inline <a id=\"S_…\"></a>: the migrator ticks"
                    + "\ntheir own copy of the guide, and a stumble log cites the step by that slug."
                    + "\nSlugs are stable once published — coin a new one rather than reusing.");
        }
    }

    /**
     * Every {@code <a id="…">} anchor in the document, plus the GitHub-style slug of every
     * heading. Both kinds are link targets, and a guide uses both.
     */
    private static List<String> anchorsOf(File doc) {
        List<String> out = new ArrayList<>();
        String text = read(doc);
        Matcher a = ANCHOR.matcher(text);
        while (a.find()) {
            out.add(a.group(1));
        }
        Matcher h = HEADING.matcher(text);
        while (h.find()) {
            out.add(githubSlug(h.group(1)));
        }
        return out;
    }

    /**
     * GitHub's heading-anchor rule: strip markup, lowercase, drop everything but word
     * characters, spaces and hyphens, then spaces to hyphens. An em dash leaves a double
     * hyphen, which is why the phase anchors read {@code #phase-1--pre-flight}.
     */
    private static String githubSlug(String heading) {
        String text = heading.replaceAll("<[^>]*>", "")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replace("`", "")
                .replace("*", "")
                .replace("_", "");
        return text.toLowerCase()
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit} -]", "")
                .replace(' ', '-');
    }

    /**
     * A declaration is the Conventions block's own bullet for the placeholder — bold and
     * followed by an em dash. Matching the shape rather than a heading keeps the check
     * indifferent to where in the document the block sits.
     */
    private static boolean declares(String text, String placeholder) {
        return text.contains("**`" + placeholder + "`** —");
    }

    /** The guides tree, one level of step directories deep. */
    private List<File> guideDocs() {
        List<File> out = new ArrayList<>();
        try (var walk = Files.walk(new File(repoRoot, "guides").toPath())) {
            walk.filter(it -> it.getFileName().toString().endsWith(".md"))
                    .map(Path::toFile)
                    .sorted()
                    .forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /**
     * Blanks out fenced code blocks, keeping line count intact. A shell or YAML sample may
     * legitimately contain something shaped like a link, and a build gate that trips on a
     * code sample teaches authors to work around it.
     */
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

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + f, e);
        }
    }

    private String rel(Path p) {
        return repoRoot.toPath().relativize(p).toString().replace(File.separatorChar, '/');
    }

    private static String render(Map<String, List<String>> byKey) {
        return byKey.entrySet().stream()
                .map(e -> "  " + e.getKey() + " — in " + String.join(", ", e.getValue()))
                .collect(Collectors.joining("\n"));
    }
}
