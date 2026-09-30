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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import vaadinx.audit.JdkCounterpart;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the repository's licence state: every file SB-Emulators authors must carry the
 * header of the lane its tree is in, and <em>which</em> header that is is not a matter of
 * taste.
 *
 * <p>The specification this test enforces is {@code PROVENANCE.md} — § Lanes for the
 * tree → licence map, § Headers for the four texts — and the relationship runs that way round
 * on purpose: {@code PROVENANCE.md} is what a lawyer, an auditor or a downstream consumer
 * reads, and this test is the mechanical half that keeps the tree matching it. A failure here
 * means one of the two is wrong — read those sections before deciding which.
 *
 * <p>The rule, in one sentence: a file under {@code emulators/src/main/java} or
 * {@code emulators-printing/src/main/java} whose class name maps onto a real JDK class
 * carries <b>{@code HDR_jdk_derived}</b> — the counterpart's own Oracle notice byte-for-byte,
 * then Vaadin's derived-and-modified block — and everything else SB-Emulators wrote carries
 * its lane's Vaadin header: <b>{@code HDR_vaadin_gpl}</b> under {@link #GPL_LANE},
 * <b>{@code HDR_vaadin_lgpl}</b> under {@code third-party/jcalendar-1.4} (rule 5), and
 * <b>{@code HDR_vaadin_apache}</b> everywhere else ({@link #inApacheLane}) — bar a seed file a
 * migration carried into a testapp stage, which keeps the seed's {@code HDR_vaadin_0bsd}
 * ({@link #keepsSeedHeader}).
 *
 * <p>Both directions are failures, which is the point. A missing Oracle notice on a
 * JDK-derived file breaks the notice-retention term we are relying on; an Oracle notice on a
 * file Oracle did not write is a false attribution, and the reason
 * {@code PROVENANCE.md} § Headers rejects JetBrains Runtime's shortcut of stamping "Oracle
 * designates this particular file…" onto downstream code. Neither is the safe direction.
 *
 * <p>Allow-list-free, and {@link #DERIVED_WITHOUT_NAME} — the escape hatch for JDK expression
 * carried into a class the name rule cannot see — is <b>empty</b>. It exists here and in
 * {@code PROVENANCE.md} § "The exceptions", and rule 3 is what stops the two copies drifting
 * once one of them is non-empty again. Emptiness is also what the Apache lane rests on: the
 * licence boundary a consumer can see is the <em>jar</em>, so a JDK-derived file anywhere under
 * the Apache-2.0 lane would put GPL code inside an Apache-2.0 artifact. Rules 2 and 6 fail
 * from opposite sides on that.
 *
 * <p><b>Two lanes are on a different licence entirely</b>, and rules 5 and 6 guard them rather
 * than {@link #EXCLUDED} merely ignoring them. {@code third-party/jcalendar-1.4} is Vaadin's own
 * code that reproduces JCalendar's API and takes JCalendar's LGPL-2.1, per
 * {@code M1D_addon_upstream_licence}; the GPLv2+CE header is a *failure* there, not just an
 * absence — LGPL-2.1 §3 permits relicensing to the ordinary GPLv2 but not to GPLv2 + Classpath
 * Exception, since the exception grants a linking permission over code that is not ours.
 * The Apache-2.0 lane is every other tree we author, per {@code D_licence_lanes}, and the same shape
 * of negative check applies: a Classpath-exception line there would state GPL
 * terms over a permissive artifact.
 *
 * @see <a href="../../../../../PROVENANCE.md">PROVENANCE.md</a>
 */
class LicenseHeaderTest {

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
     * Build and vendor directories, pruned rather than filtered. {@code .claude} matters more
     * than the speed: it can hold a whole second checkout as a git worktree, and scanning that
     * would report every finding twice at paths that do not exist for the reader.
     */
    private static final Set<String> PRUNED =
            Set.of("target", "node_modules", ".git", ".claude", "node", "generated");

    /**
     * Trees this test does not scan at all, each with the reason. Two kinds are mixed here on
     * purpose, because the question "which of our headers does this file carry?" is the wrong
     * question for both: code SB-Emulators did not author, and code it did author under a
     * licence with a gate of its own. The Apache-2.0 lane is neither — those trees are ours
     * and are scanned like any other, just against a different SPDX line.
     *
     * <p>{@code third-party/jcalendar-1.4} is the second kind. It is Vaadin's own code, but it
     * reproduces JCalendar's API and takes JCalendar's LGPL-2.1 rather than this repository's
     * licence, per {@code M1D_addon_upstream_licence} — so demanding the GPLv2+CE SPDX line on
     * it would assert exactly the wrong terms. {@link #jcalendarCarriesItsOwnLgplHeader()}
     * checks it against its own lane instead; it is not unguarded. The host-app seeds are the same
     * kind, on 0BSD, and {@link #theSeedTreesCarryTheZeroBsdHeader()} is their gate.
     */
    /** The host-app seeds a migrator copies into their own app: the 0BSD lane, rule 7. */
    static final String SEEDS = "guides/1-swing-to-emulators/seed/";

    static final Map<String, String> EXCLUDED = Map.of(
            "testapps/inventory/", "adopted third-party app — upstream author's grant, untouched",
            "testapps/jlawyer-shape/", "hand-built shape of a third-party app, carrying a j-lawyer header",
            "third-party/jgoodies-forms-1.2.1/", "BSD fork — every forked file keeps its upstream header",
            "third-party/jcalendar-1.4/", "Vaadin-authored but LGPL-2.1, upstream's licence — see rule 5",
            SEEDS, "Vaadin-authored but 0BSD, copied into the migrator's own app — see rule 7",
            "ideas/", "throwaway probe code that ships in nothing");

    /** The two trees where the name rule applies; elsewhere a JDK name is coincidence. */
    private static final List<String> EMULATOR_MAIN =
            List.of("emulators/src/main/java/", "emulators-printing/src/main/java/");

    /**
     * {@code HDR_jdk_derived} despite having no JDK <em>name</em> counterpart, because JDK
     * expression was carried into them: a constant table copied wholesale, or a body translated
     * rather than written against the javadoc. Path → the JDK class each derives from.
     *
     * <p>Their 20 sibling listener and adapter types are deliberately absent: those are method
     * signatures and nothing else, which is the API shape this whole project copies by design.
     * {@code PROVENANCE.md} § "The exceptions" argues the line and lists the six files that
     * were examined and left off it — {@code SJTextField} being the closest call.
     *
     * <p><b>It is empty today, and emptiness is the claim</b>, not an oversight: every
     * {@code HDR_jdk_derived} file in the repo is one the name rule finds, which is also what
     * lets the Apache-2.0 lane be a lane at all. Eleven `:surrogates` files were on it and each
     * left by having its carried-over expression removed — the ten {@code S*Event} classes once
     * {@code DelegatedConstantsTest} began emitting their constant tables from the JDK's public
     * runtime API with every value delegated, and {@code SJTextArea} once its
     * {@code append} / {@code insert} / {@code replaceRange} bodies were rewritten as bounds
     * checks over one private splice. What is left in those files — {@code super(source, id);},
     * {@code this.rows = rows;}, a five-constructor delegation chain, a {@code paramString()}
     * signature — is the API shape this project copies by design, the same call already made for
     * the 20 listener siblings. An entry coming back means one of those rewrites was undone;
     * {@code PROVENANCE.md} § "The exceptions" carries the reasoning for each.
     *
     * <p><b>Do not rebuild this list by grepping for "port of".</b> {@code SJTextArea} never
     * admitted its derivation, and was found only by measuring line overlap against the JDK. A
     * self-admission grep silently drops that whole shape of finding.
     */
    private static final Map<String, String> DERIVED_WITHOUT_NAME = Map.ofEntries();

    /** Spells out the one path prefix an entry can have, for whenever this list is non-empty. */
    @SuppressWarnings("unused")
    private static Map.Entry<String, String> derived(String underSurrogates, String jdkClass) {
        return Map.entry("surrogates/src/main/java/com/vaadin/swingbridge/surrogates/" + underSurrogates,
                jdkClass);
    }

    private static final String SPDX = "SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0";
    private static final String APACHE_SPDX = "SPDX-License-Identifier: Apache-2.0";

    /**
     * The GPLv2+CE lane, per {@code PROVENANCE.md} § Lanes and {@code D_licence_lanes}: the
     * sources of the two modules that are a derivative work of OpenJDK, each as a whole jar — not
     * the modules' root docs and poms, which the map puts on Apache-2.0. Named here, rather than
     * the Apache-2.0 lane, for the same reason the parent pom's {@code <licenses>} block is
     * Apache-2.0 and these two override it: Apache-2.0 is the default, and a new module inherits
     * it — in the pom, in the header rule 1 demands, and in {@link JdkSimilarityTest}'s
     * measurement from its first build.
     *
     * <p>A module joins this lane only on evidence — JDK expression carried into it — by one
     * entry here plus its pom's {@code <licenses>} and {@code <resources>} overrides. What
     * decides it is <b>expression, not coupling</b>: how heavily a module <em>uses</em> the
     * emulator API does not enter into it — {@code :sampler} has 433 {@code vaadinx} imports and
     * 50 classes extending emulator types, and is Apache-2.0, because calling or subclassing a type
     * carries none of its expression. {@code D_licence_lanes} carries the reductio of the earlier
     * rule that said otherwise.
     */
    static final List<String> GPL_LANE = List.of("emulators/src/", "emulators-printing/src/");

    /**
     * Whether {@code rel} is in the Apache-2.0 lane: every tree SB-Emulators authored that is
     * neither {@link #GPL_LANE} nor {@link #EXCLUDED} ({@code jcalendar-1.4}'s LGPL lane among
     * them). Unlike {@link #EXCLUDED} these trees are fully scanned — they are ours, they carry our
     * header, it is simply a different one. Rule 6 is the negative half.
     */
    static boolean inApacheLane(String rel) {
        return GPL_LANE.stream().noneMatch(rel::startsWith)
                && EXCLUDED.keySet().stream().noneMatch(rel::startsWith);
    }

    /**
     * Whether {@code rel} is on a permissive lane — Apache-2.0 or the seeds' 0BSD — which is what
     * {@link JdkSimilarityTest} measures: JDK expression in either would be GPL code inside terms
     * that do not carry it.
     */
    static boolean inPermissiveLane(String rel) {
        return inApacheLane(rel) || rel.startsWith(SEEDS);
    }

    /** The SPDX identifier {@code rel}'s lane requires, given the file's {@code header}. */
    private static String expectedSpdx(String rel, String header) {
        if (keepsSeedHeader(rel, header)) {
            return ZERO_BSD_SPDX;
        }
        return inApacheLane(rel) ? APACHE_SPDX : SPDX;
    }

    /**
     * Whether {@code rel} is a seed file carried into a testapp's migrated stage — any stage
     * directory under {@code testapps/<app>/} but {@code swing/} — with its {@code HDR_vaadin_0bsd}
     * intact. A migration copies the seeds into the app and keeps their header, exactly as a
     * migrator's does, and those stages are migration output rather than hand-written, so the
     * Apache-2.0 lane admits the seed's header there instead of demanding a hand-fix after
     * every run. It is still held to the whole 0BSD header, by {@link #zeroBsdProblems}.
     */
    static boolean keepsSeedHeader(String rel, String header) {
        String[] segments = rel.split("/", 4);
        return segments.length == 4 && segments[0].equals("testapps") && !segments[2].equals("swing")
                && header.contains(ZERO_BSD_SPDX);
    }

    private static final Pattern ORACLE_COPYRIGHT = Pattern.compile(
            "Copyright \\(c\\) (\\d{4}), (\\d{4}), Oracle and/or its affiliates\\. All rights reserved\\.");
    private static final String DO_NOT_ALTER = "DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.";
    private static final String CLASSPATH_DESIGNATION =
            "Oracle designates this\n * particular file as subject to the \"Classpath\" exception";
    private static final String ORACLE_ANY = "Oracle and/or its affiliates";

    /** {@code src.zip} lays classes out per module; these are the two SB-Emulators draws from. */
    private static final List<String> JDK_MODULES = List.of("java.desktop", "java.base");

    // --- rule 1 ----------------------------------------------------------------------------

    @Test
    @DisplayName("every SB-Emulators-authored .java file carries a licence header before its package line")
    void everyAuthoredFileCarriesALicenceHeader() {
        List<Path> files = includedFiles();
        // Guard the guard: a walk that silently found nothing would pass forever.
        assertTrue(files.size() > 600, "expected 600+ authored .java files, found " + files.size()
                + " — is the walk broken?");

        Map<String, List<String>> missing = new TreeMap<>();
        for (Path f : files) {
            String rel = rel(f);
            String header = header(read(f));
            List<String> absent = new ArrayList<>();
            if (!header.contains("Copyright 2000-")) {
                absent.add("the `Copyright 2000-YYYY Vaadin Ltd.` line");
            }
            if (!header.contains("Vaadin Ltd.")) {
                absent.add("`Vaadin Ltd.`");
            }
            // The identifier is the lane's, which is the only thing rule 1 reads differently
            // per tree: the copyright line above is Vaadin's house line in every lane.
            String spdx = expectedSpdx(rel, header);
            if (!header.contains(spdx)) {
                absent.add("`" + spdx + "`"
                        + (inApacheLane(rel) ? " — this tree is the Apache-2.0 lane" : ""));
            }
            if (!absent.isEmpty()) {
                missing.put(rel, absent);
            }
        }
        if (!missing.isEmpty()) {
            fail("files with no (or an incomplete) licence header, before the `package` line:\n"
                    + render(missing)
                    + "\n\nEvery file SB-Emulators authors starts with its lane's header, verbatim from"
                    + "\nPROVENANCE.md § Headers — the lane itself is the § Lanes table. If this file is not"
                    + "\nours, exempt its tree in EXCLUDED here and say why in PROVENANCE.md § \"Trees we do"
                    + "\nnot touch\" — do not hand-write a header.");
        }
    }

    // --- rule 2 ----------------------------------------------------------------------------

    @Test
    @DisplayName("HDR_jdk_derived sits on exactly the JDK-derived files, and nowhere else")
    void headerASitsOnExactlyTheJdkDerivedFiles() {
        Map<String, List<String>> wrong = new TreeMap<>();
        int headerACount = 0;
        for (Path f : includedFiles()) {
            String rel = rel(f);
            String header = header(read(f));
            String derivedFrom = expectedDerivedFrom(rel);
            boolean hasOracle = header.contains(ORACLE_ANY);

            if (derivedFrom == null) {
                if (hasOracle) {
                    wrong.put(rel, List.of("carries an Oracle copyright notice, but no JDK class"
                            + " corresponds to it — a false attribution is as wrong as a missing one."
                            + " If JDK expression really was carried into this file, add it to"
                            + " DERIVED_WITHOUT_NAME here and to PROVENANCE.md § \"The exceptions\""));
                }
                continue;
            }

            headerACount++;
            List<String> problems = new ArrayList<>();
            if (!hasOracle) {
                problems.add("needs HDR_jdk_derived (derived from " + derivedFrom + ") but carries no Oracle"
                        + " notice — copy the counterpart's own header from jdk-25-ga, unedited");
            } else {
                if (!ORACLE_COPYRIGHT.matcher(header).find()) {
                    problems.add("its Oracle copyright line does not match the JDK's shape"
                            + " `Copyright (c) YYYY, YYYY, Oracle and/or its affiliates. All rights reserved.`");
                }
                if (!header.contains(DO_NOT_ALTER)) {
                    problems.add("its Oracle block is missing the DO NOT ALTER line — the block goes in"
                            + " byte-for-byte, reflowing included");
                }
                if (!header.contains(CLASSPATH_DESIGNATION)) {
                    problems.add("its Oracle block is missing Oracle's Classpath designation sentence");
                }
                if (header.indexOf(ORACLE_ANY) > header.indexOf("Vaadin Ltd.")) {
                    problems.add("puts Vaadin's block before Oracle's; Oracle's comes first");
                }
            }
            if (!header.contains("derived from OpenJDK's " + derivedFrom + "\n")) {
                problems.add("its Vaadin block must name the class it derives from, as"
                        + " `derived from OpenJDK's " + derivedFrom + "` on one line");
            }
            if (!problems.isEmpty()) {
                wrong.put(rel, problems);
            }
        }
        assertTrue(headerACount > 150, "expected 150+ HDR_jdk_derived files, found " + headerACount
                + " — is the counterpart mapping resolving?");
        if (!wrong.isEmpty()) {
            fail("HDR_jdk_derived / HDR_vaadin_gpl is wrong on these files:\n" + render(wrong)
                    + "\n\nThe rule and both header texts are in PROVENANCE.md § Headers.");
        }
    }

    // --- rule 3 ----------------------------------------------------------------------------

    @Test
    @DisplayName("the by-name exceptions exist and name exactly the class they derive from")
    void theByNameExceptionsExistAndNameTheirClass() {
        Map<String, List<String>> wrong = new TreeMap<>();
        DERIVED_WITHOUT_NAME.forEach((rel, jdkClass) -> {
            Path f = repoRoot.toPath().resolve(rel);
            if (!Files.isRegularFile(f)) {
                wrong.put(rel, List.of("listed as JDK-derived but does not exist — if it was renamed or"
                        + " deleted, update this list and PROVENANCE.md § \"The exceptions\" together"));
                return;
            }
            if (JdkCounterpart.loadOrNull(jdkClass) == null) {
                wrong.put(rel, List.of("names `" + jdkClass + "`, which does not resolve on this JDK"));
                return;
            }
            String header = header(read(f));
            if (!header.contains("derived from OpenJDK's " + jdkClass + "\n")) {
                wrong.put(rel, List.of("must name `" + jdkClass + "` in its Vaadin block"));
            }
        });
        if (!wrong.isEmpty()) {
            fail("the DERIVED_WITHOUT_NAME list and the tree disagree:\n" + render(wrong)
                    + "\n\nThis is the one allow-list in this test, and it is duplicated in"
                    + "\nPROVENANCE.md § \"The exceptions\" so a human auditor can read it. Change both.");
        }
    }

    // --- rule 4 ----------------------------------------------------------------------------

    @Test
    @DisplayName("Oracle years are copied per file, never stamped from a template")
    void oracleYearsAreCopiedPerFileNeverStampedFromATemplate() {
        Map<String, String> oracleLineByFile = new TreeMap<>();
        for (Path f : includedFiles()) {
            String rel = rel(f);
            if (expectedDerivedFrom(rel) == null) {
                continue;
            }
            Matcher m = ORACLE_COPYRIGHT.matcher(header(read(f)));
            if (m.find()) {
                oracleLineByFile.put(rel, m.group(1) + ", " + m.group(2));
            }
        }
        assertTrue(oracleLineByFile.size() > 150,
                "expected 150+ HDR_jdk_derived files, found " + oracleLineByFile.size());

        // The feature-pack mistake, caught head-on: it stamped `1997, 2021` on all 13 of its
        // JDK-derived files. Real per-file copies spread across 40-odd distinct pairs.
        Set<String> distinct = new TreeSet<>(oracleLineByFile.values());
        assertTrue(distinct.size() > 20, "only " + distinct.size() + " distinct Oracle year pairs across "
                + oracleLineByFile.size() + " HDR_jdk_derived files — that is a template, not a per-file copy."
                + " Copy each block from its own counterpart; see PROVENANCE.md § Headers.");

        Path srcZip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
        Assumptions.assumeTrue(Files.isReadable(srcZip),
                "no lib/src.zip on this JDK, so the per-file Oracle blocks cannot be diffed against"
                        + " their counterparts (CI's Temurin ships one)");

        Map<String, List<String>> wrong = new TreeMap<>();
        try (ZipFile zip = new ZipFile(srcZip.toFile())) {
            for (Map.Entry<String, String> e : oracleLineByFile.entrySet()) {
                String rel = e.getKey();
                String jdkClass = expectedDerivedFrom(rel);
                String upstream = leadingComment(sourceOf(zip, jdkClass));
                if (upstream == null) {
                    continue;   // counterpart not in this JDK's src.zip; nothing to diff against
                }
                String ours = leadingComment(read(repoRoot.toPath().resolve(rel)));
                List<String> problems = new ArrayList<>();

                // The notice text itself must be untouched. Compared with the copyright line
                // masked out, because that one line legitimately differs: this JDK is a *build*,
                // and builds disagree with the tag we copied from. Measured 2026-09-08 —
                // JBR 25.0.4 differs from jdk-25-ga on 4 of the 160 files, in both directions
                // (java.awt.GraphicsEnvironment older there, javax.swing.border.LineBorder newer).
                if (!mask(ours).equals(mask(upstream))) {
                    problems.add("its Oracle block is not this JDK's block for " + jdkClass
                            + " — something in the notice was edited or reflowed");
                }
                Matcher oursYears = ORACLE_COPYRIGHT.matcher(ours);
                Matcher upstreamYears = ORACLE_COPYRIGHT.matcher(upstream);
                if (oursYears.find() && upstreamYears.find()
                        && !oursYears.group(1).equals(upstreamYears.group(1))) {
                    // The start year is the file's creation year and does not move between
                    // builds; a mismatch means the block came from somewhere other than this class.
                    problems.add("starts at " + oursYears.group(1) + " where " + jdkClass + " starts at "
                            + upstreamYears.group(1) + " — this block was copied from the wrong class");
                }
                if (!problems.isEmpty()) {
                    wrong.put(rel, problems);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("cannot read " + srcZip, ex);
        }
        if (!wrong.isEmpty()) {
            fail("Oracle blocks that do not match their counterpart:\n" + render(wrong)
                    + "\n\nCopy the block from the counterpart in jdk-25-ga (or this JDK's lib/src.zip)"
                    + "\nwithout editing a character. PROVENANCE.md § Headers says why the end year is"
                    + "\nnot compared and the start year is.");
        }
    }

    // --- rule 5 ----------------------------------------------------------------------------

    @Test
    @DisplayName("the jcalendar add-on carries its own LGPL-2.1 header, and no trace of the GPL one")
    void jcalendarCarriesItsOwnLgplHeader() {
        Path module = repoRoot.toPath().resolve(JCALENDAR);
        Assumptions.assumeTrue(Files.isDirectory(module), JCALENDAR + " is not present");

        assertTrue(Files.isRegularFile(module.resolve("LICENSE")),
                JCALENDAR + "LICENSE is missing. This module does not inherit the repository's"
                        + " licence, so the root LICENSE is not its licence — it ships its own,"
                        + " and the pom's <resources> block copies that file into META-INF/.");

        Map<String, List<String>> wrong = new TreeMap<>();
        List<Path> files = javaFilesUnder(module);
        assertTrue(files.size() >= 4, "expected 4+ .java files under " + JCALENDAR
                + ", found " + files.size() + " — is the walk broken?");
        for (Path f : files) {
            String header = header(read(f));
            List<String> problems = new ArrayList<>();
            if (!header.contains(LGPL_SPDX)) {
                problems.add("is missing `" + LGPL_SPDX + "`");
            }
            if (!header.contains("GNU Lesser General Public License")) {
                problems.add("does not name the GNU Lesser General Public License");
            }
            if (!header.contains("Vaadin Ltd.")) {
                problems.add("is missing Vaadin's copyright line — the code is ours even though"
                        + " the licence is upstream's");
            }
            // The failure that matters most: a new file copy-pasted from a sibling module, or a
            // sweep that treated this tree as part of the GPL lane.
            if (header.contains(SPDX) || header.contains("\"Classpath\" exception")) {
                problems.add("carries the emulator lane's GPLv2+CE header. LGPL-2.1 §3 permits"
                        + " relicensing to the ORDINARY GPLv2 but not to GPLv2+CE, because the"
                        + " Classpath Exception grants a linking permission over code that is not"
                        + " ours to grant it over — so this is not merely the wrong header, it"
                        + " states terms we cannot offer");
            }
            if (header.contains(ORACLE_ANY)) {
                problems.add("carries an Oracle notice; nothing here derives from the JDK");
            }
            if (!problems.isEmpty()) {
                wrong.put(rel(f), problems);
            }
        }
        if (!wrong.isEmpty()) {
            fail("the jcalendar add-on's licence header is wrong on these files:\n" + render(wrong)
                    + "\n\nThis module reproduces JCalendar's API and takes JCalendar's licence"
                    + "\n(LGPL-2.1), not this repository's — see M1D_addon_upstream_licence and"
                    + "\nthird-party/jcalendar-1.4/PROVENANCE.md § Licence. Copy the header from a"
                    + "\nsibling file in the same module, never from another module.");
        }
    }

    private static final String JCALENDAR = "third-party/jcalendar-1.4/";

    // --- rule 7 ----------------------------------------------------------------------------

    /**
     * The seeds are copied into the migrator's own app, where Apache-2.0 would impose our notice
     * terms on their code — so they waive them: 0BSD, a grant with no conditions, per
     * {@code PROVENANCE.md} § {@code HDR_vaadin_0bsd}. The failures that matter are a file copied in
     * from a sibling module with its Apache or GPL header, and the tree's {@code LICENSE} going
     * missing, which leaves the poms and resources with no grant at all.
     */
    @Test
    @DisplayName("the host-app seeds carry the 0BSD header, and no trace of another lane's")
    void theSeedTreesCarryTheZeroBsdHeader() throws IOException {
        Path seeds = repoRoot.toPath().resolve(SEEDS);
        Path license = seeds.resolve("LICENSE");
        assertTrue(Files.isRegularFile(license) && read(license).contains(ZERO_BSD_GRANT),
                SEEDS + "LICENSE is missing or is not the 0BSD text. It is the only grant covering the"
                        + " seed poms and resources, which carry no header of their own.");

        Map<String, List<String>> wrong = new TreeMap<>();
        List<Path> files = javaFilesUnder(seeds);
        assertTrue(files.size() >= 8, "expected 8+ .java files under " + SEEDS
                + ", found " + files.size() + " — is the walk broken?");
        for (Path f : files) {
            List<String> problems = zeroBsdProblems(header(read(f)));
            if (!problems.isEmpty()) {
                wrong.put(rel(f), problems);
            }
        }
        if (!wrong.isEmpty()) {
            fail("seed files with the wrong licence header:\n" + render(wrong)
                    + "\n\nCopy HDR_vaadin_0bsd from a sibling seed file, or from PROVENANCE.md § Headers.");
        }
    }

    /** What is wrong with {@code header} as an {@code HDR_vaadin_0bsd}; empty when nothing is. */
    private static List<String> zeroBsdProblems(String header) {
        List<String> problems = new ArrayList<>();
        if (!header.contains(ZERO_BSD_SPDX)) {
            problems.add("is missing `" + ZERO_BSD_SPDX + "`");
        }
        if (!header.contains(ZERO_BSD_GRANT.replace("\n", "\n * "))) {
            problems.add("does not carry 0BSD's permission sentence verbatim");
        }
        if (!header.contains("Vaadin Ltd.")) {
            problems.add("is missing Vaadin's copyright line");
        }
        if (header.contains("Apache License") || header.contains(SPDX)
                || header.contains("\"Classpath\" exception") || header.contains(ORACLE_ANY)) {
            problems.add("carries another lane's notice. A seed is copied into the migrator's"
                    + " own app, so any condition in its header becomes a condition on their"
                    + " code — the reason this tree is 0BSD at all");
        }
        return problems;
    }

    private static final String ZERO_BSD_SPDX = "SPDX-License-Identifier: 0BSD";
    private static final String ZERO_BSD_GRANT = "Permission to use, copy, modify, and/or distribute this software for any\n"
            + "purpose with or without fee is hereby granted.";
    private static final String LGPL_SPDX = "SPDX-License-Identifier: LGPL-2.1-only";

    // --- rule 6 ----------------------------------------------------------------------------

    @Test
    @DisplayName("the Apache-2.0 lane carries Apache's notice, and no trace of the GPL one")
    void theApacheLaneCarriesApachesNoticeAndNoTraceOfTheGplOne() {
        assertTrue(Files.isRegularFile(repoRoot.toPath().resolve(APACHE_LICENSE_FILE)),
                APACHE_LICENSE_FILE + " is missing at the repo root. The Apache-2.0 modules do not"
                        + " ship the root LICENSE — their poms copy this file into META-INF/ instead,"
                        + " and a header pointing at a text nobody ships grants nothing.");

        Map<String, List<String>> wrong = new TreeMap<>();
        int seen = 0;
        for (Path f : includedFiles()) {
            String rel = rel(f);
            if (!inApacheLane(rel)) {
                continue;
            }
            seen++;
            String header = header(read(f));
            if (keepsSeedHeader(rel, header)) {
                List<String> problems = zeroBsdProblems(header);
                if (!problems.isEmpty()) {
                    wrong.put(rel, problems);
                }
                continue;
            }
            List<String> problems = new ArrayList<>();
            if (!header.contains("Licensed under the Apache License, Version 2.0")) {
                problems.add("does not carry Apache-2.0's own notice — copy HDR_vaadin_apache from"
                        + " PROVENANCE.md § Headers, or from a sibling file in the same module");
            }
            // The failure that matters: a file copy-pasted from :emulators, or a sweep that
            // treated this tree as part of the GPL lane. Either way the jar would then ship
            // classes claiming terms its pom and its META-INF do not offer.
            if (header.contains(SPDX) || header.contains("\"Classpath\" exception")) {
                problems.add("carries the GPLv2+CE header. This module is Apache-2.0"
                        + " (D_licence_lanes), its pom says so and its jar ships"
                        + " " + APACHE_LICENSE_FILE + " rather than LICENSE — a Classpath-exception"
                        + " line here states copyleft terms over a permissive artifact, which is a"
                        + " boundary no consumer can see");
            }
            if (!problems.isEmpty()) {
                wrong.put(rel, problems);
            }
        }
        assertTrue(seen > 150, "expected 150+ files in the Apache lane, found " + seen
                + " — does GPL_LANE " + GPL_LANE + " swallow more than the two emulator modules?");
        if (!wrong.isEmpty()) {
            fail("the Apache-2.0 lane's licence header is wrong on these files:\n" + render(wrong)
                    + "\n\nThese modules are Apache-2.0 per PROVENANCE.md § Lanes. Rule 2 guards the"
                    + "\nother side of the same boundary: an Oracle notice here is a JDK-derived file"
                    + "\ninside a permissive jar, and the fix for that one is never a header edit.");
        }
    }

    private static final String APACHE_LICENSE_FILE = "LICENSE-APACHE-2.0";

    private static List<Path> javaFilesUnder(Path dir) {
        try (var walk = Files.walk(dir)) {
            return walk.filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot walk " + dir, e);
        }
    }

    /** The Oracle copyright line blanked, so a notice can be compared across JDK builds. */
    private static String mask(String oracleBlock) {
        return ORACLE_COPYRIGHT.matcher(oracleBlock).replaceAll("<<COPYRIGHT>>");
    }

    /** {@code jdkClass}' source from {@code src.zip}, or {@code null} if this JDK does not ship it. */
    private static String sourceOf(ZipFile zip, String jdkClass) throws IOException {
        String path = jdkClass.replace('.', '/') + ".java";
        for (String module : JDK_MODULES) {
            ZipEntry entry = zip.getEntry(module + "/" + path);
            if (entry != null) {
                try (var in = zip.getInputStream(entry)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    // --- plumbing --------------------------------------------------------------------------

    /**
     * The JDK class {@code rel} must declare itself derived from, or {@code null} when the file
     * is SB-Emulators' own. Name rule first, then the by-expression exceptions.
     */
    private static String expectedDerivedFrom(String rel) {
        for (String root : EMULATOR_MAIN) {
            if (!rel.startsWith(root)) {
                continue;
            }
            String fqcn = rel.substring(root.length(), rel.length() - ".java".length())
                    .replace('/', '.');
            if (fqcn.endsWith(".package-info")) {
                return null;    // not a class; there is nothing for Class.forName to resolve
            }
            Class<?> counterpart = JdkCounterpart.of(fqcn);
            return counterpart == null ? null : counterpart.getName();
        }
        return DERIVED_WITHOUT_NAME.get(rel);
    }

    /**
     * Everything before the {@code package} declaration — the whole header region, so a file
     * with two comment blocks ({@code HDR_jdk_derived}) is handled the same as one with a single
     * block.
     */
    private static String header(String source) {
        int at = source.indexOf("\npackage ");
        return at < 0 ? source : source.substring(0, at + 1);
    }

    /** The file's first {@code &#47;* … *&#47;} block, or {@code null} if it opens with none. */
    private static String leadingComment(String source) {
        if (source == null) {
            return null;
        }
        String trimmed = source.stripLeading();
        if (!trimmed.startsWith("/*")) {
            return null;
        }
        int end = trimmed.indexOf("*/");
        return end < 0 ? null : trimmed.substring(0, end + 2);
    }

    private List<Path> cached;

    /** Every {@code .java} file in the repo that SB-Emulators authored, sorted for stable output. */
    private synchronized List<Path> includedFiles() {
        if (cached == null) {
            List<Path> found = new ArrayList<>();
            try {
                Files.walkFileTree(repoRoot.toPath(), new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        return PRUNED.contains(dir.getFileName().toString())
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        // Symlinks skipped, not followed — see DecisionIdTest for the AGENTS.md case.
                        if (attrs.isRegularFile() && file.getFileName().toString().endsWith(".java")) {
                            found.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            cached = found.stream()
                    .filter(p -> EXCLUDED.keySet().stream().noneMatch(x -> rel(p).startsWith(x)))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return cached;
    }

    private static String render(Map<String, List<String>> byFile) {
        return byFile.entrySet().stream()
                .map(e -> "  " + e.getKey() + "\n"
                        + new LinkedHashSet<>(e.getValue()).stream()
                        .map(p -> "      - " + p).collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n"));
    }

    /** Lenient UTF-8, matching {@code DecisionIdTest}: one stray byte must not fail the scan. */
    private static String read(Path f) {
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + f, e);
        }
    }

    private String rel(Path f) {
        return repoRoot.toPath().relativize(f).toString().replace(File.separatorChar, '/');
    }
}
