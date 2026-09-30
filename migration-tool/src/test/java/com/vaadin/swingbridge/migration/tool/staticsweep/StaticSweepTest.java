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

package com.vaadin.swingbridge.migration.tool.staticsweep;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sweep over {@link Fixtures}' compiled trees, which is the only way to test a class-file reader
 * honestly: every fact asserted here was put in the class file by {@code javac}, not by us.
 */
class StaticSweepTest {

    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS)
    static Path root;

    private static Fixtures.Trees trees;

    @BeforeAll
    static void compileFixtures() throws IOException {
        trees = Fixtures.compile(root);
    }

    /** The stage-1 sweep as a migrator runs it: the app's classes, and nothing else handed in. */
    private static Map<String, SweepReport.Row> stage1() throws IOException {
        return rows(trees.stage1(), List.of());
    }

    /**
     * The resolver is closed here rather than left to the GC: it holds the jars in
     * {@code libDirs} open, and on Windows an open jar is an undeletable one — which shows up as
     * a {@code @TempDir} cleanup failure on the whole class, not as a failure in this test.
     *
     * @param libDirs {@code --lib} directories, empty for the plain stage-1 run
     */
    private static Map<String, SweepReport.Row> rows(Path tree, List<Path> libDirs)
            throws IOException {
        try (Resolver resolver = Resolver.over(List.of(tree), libDirs, List.of())) {
            return rows(tree, resolver);
        }
    }

    private static Map<String, SweepReport.Row> rows(Path tree, Resolver resolver)
            throws IOException {
        Map<String, SweepReport.Row> byKey = new LinkedHashMap<>();
        StaticIndex index = StaticIndex.over(ClassTree.of(List.of(tree)));
        SweepReport.rows(index, resolver).forEach(row -> byKey.put(row.field().key(), row));
        return byKey;
    }

    private static StaticIndex index(Path tree) throws IOException {
        return StaticIndex.over(ClassTree.of(List.of(tree)));
    }

    private static SweepReport.Row row(Map<String, SweepReport.Row> rows, String field) {
        SweepReport.Row row = rows.get("com.acme.app.Holder." + field);
        assertTrue(row != null, "no row for Holder." + field + " in " + rows.keySet());
        return row;
    }

    @Test
    @DisplayName("buckets by the gate's own rule: `final` AND an immutable type, both halves")
    void bucketsByTheGatesRule() throws IOException {
        Map<String, SweepReport.Row> rows = stage1();

        assertEquals(SweepReport.Bucket.CONSTANT, row(rows, "NAME").bucket(), "final String");
        assertEquals(SweepReport.Bucket.CONSTANT, row(rows, "MODE").bucket(), "final enum");
        assertEquals(SweepReport.Bucket.CONSTANT, row(rows, "PAIR").bucket(), "final record");
        // The half a type-only test waves through, and the half a final-only test waves through.
        assertEquals(SweepReport.Bucket.WORKLIST, row(rows, "POOL").bucket(),
                "final, but a Map's contents can change");
        assertEquals(SweepReport.Bucket.WORKLIST, row(rows, "loggedIn").bucket(),
                "an immutable type, but the field is not final");

        assertEquals(SweepReport.Bucket.ENUM_CONSTANT,
                rows.get("com.acme.app.Mode.OPEN").bucket());
        assertEquals(SweepReport.Bucket.SYNTHETIC, rows.get("com.acme.app.Mode.$VALUES").bucket());
        assertEquals(List.of(SweepReport.Bucket.SYNTHETIC), rows.values().stream()
                .filter(r -> r.field().name().startsWith("$SwitchMap$")).map(SweepReport.Row::bucket)
                .distinct().toList(), "the switch-over-enum table is compiler-generated, not state");
    }

    @Test
    @DisplayName("Q_component resolves through the hierarchy — two hops above JFrame still counts")
    void resolvesComponentsThroughTheHierarchy() throws IOException {
        Map<String, SweepReport.Row> rows = stage1();

        SweepReport.Row frame = row(rows, "frame");
        assertEquals(Resolver.Kind.COMPONENT, frame.component().kind());
        assertTrue(frame.component().detail().contains("MainFrame <: java.awt.Component"),
                frame.component().detail());

        assertEquals(Resolver.Kind.PRIMITIVE, row(rows, "loggedIn").component().kind());
        assertEquals(Resolver.Kind.PLAIN, row(rows, "cache").component().kind());
    }

    @Test
    @DisplayName("erasure recovery: a component inside a type argument is found, a raw type is not")
    void recoversComponentTypeArguments() throws IOException {
        Map<String, SweepReport.Row> rows = stage1();

        assertEquals(List.of("JPanel"), row(rows, "panels").typeArgComponents());
        assertEquals(List.of(), row(rows, "cache").typeArgComponents(),
                "a raw Map carries no Signature attribute — the mutable-type rule still flags it");
        assertEquals(SweepReport.Bucket.WORKLIST, row(rows, "cache").bucket());
        assertEquals(List.of(), row(rows, "pool").typeArgComponents(), "Map<String, String>");
    }

    @Test
    @DisplayName("a type that will not load is unresolved, never demoted — and resolves once handed in")
    void unresolvedNeverDemotes() throws IOException {
        Map<String, SweepReport.Row> withheld = stage1();

        // Orphan's own supertype is missing: a NoClassDefFoundError, not a ClassNotFoundException.
        assertEquals(Resolver.Kind.UNRESOLVED, row(withheld, "orphan").component().kind());
        assertEquals(Resolver.Kind.UNRESOLVED, row(withheld, "foreign").component().kind());
        assertEquals(SweepReport.Bucket.WORKLIST, row(withheld, "orphan").bucket(),
                "an unresolved type is read by a human, never assumed harmless");

        Map<String, SweepReport.Row> handedIn = rows(trees.stage1(),
                List.of(trees.libJar().getParent()));
        assertEquals(Resolver.Kind.COMPONENT, row(handedIn, "orphan").component().kind(),
                "--lib puts the dependency jar on the classpath and Orphan is a JPanel two hops up");
    }

    @Test
    @DisplayName("the declaration line comes off the initializer's own putstatic")
    void recoversTheDeclarationLine() throws IOException {
        Map<String, SweepReport.Row> rows = stage1();

        assertEquals(Fixtures.stage1Line("static Map<String, JPanel> panels"),
                row(rows, "panels").field().initLine());
        assertNull(row(rows, "loggedIn").field().initLine(),
                "no initializer, no putstatic in <clinit>, no line — its writers' lines instead");
    }

    @Test
    @DisplayName("read-mostly is a query: a lambda's write shows up, attributed to the synthetic")
    void attributesALambdaWrite() throws IOException {
        Map<String, SweepReport.Row> rows = stage1();

        assertTrue(row(rows, "loggedIn").writers().stream()
                .anyMatch(w -> w.startsWith("lambda$arm$0:")),
                "writers were " + row(rows, "loggedIn").writers());
        assertEquals(List.of(), row(rows, "POOL").writers(),
                "written only by <clinit>, which is initialization rather than mutation");
    }

    @Test
    @DisplayName("Q_method_or_type: the opcode says read or write, and names the field")
    void staticMethodsReportTheirTouches() throws IOException {
        Map<String, List<String>> touches = new LinkedHashMap<>();
        index(trees.stage1()).staticMethods()
                .forEach(m -> touches.put(m.owner() + "." + m.name(), m.touches()));

        assertEquals(List.of("R cache"), touches.get("com.acme.app.Holder.reset"));
        assertEquals(List.of(), touches.get("com.acme.app.Holder.twice"),
                "a pure function of its arguments needs no decision");
        assertEquals(List.of(), touches.get("com.acme.app.Holder.arm"),
                "the write is in the lambda body, a synthetic method — the field's writers column"
                        + " is where it surfaces, which is where a migrator acts on it");
    }

    @Test
    @DisplayName("the static-block heuristic finds Holder's block and not a $SwitchMap$ holder")
    void flagsAnExplicitStaticBlock() throws IOException {
        Map<String, Boolean> blocks = new LinkedHashMap<>();
        index(trees.stage1()).clinitNotes()
                .forEach(note -> blocks.put(note.owner(), note.explicitBlock()));

        assertEquals(Boolean.TRUE, blocks.get("com.acme.app.Holder"), "a static { } with a try");
        assertEquals(Boolean.FALSE, blocks.get("com.acme.app.Switcher$1"),
                "its <clinit> is all NoSuchFieldError handlers and it owns no real static");
    }

    @Test
    @DisplayName("a static nested type is found, and is not mistaken for a static field")
    void findsStaticNestedTypes() throws IOException {
        assertTrue(index(trees.stage1()).nestedStaticTypes().contains("com.acme.app.Holder$Nested"),
                index(trees.stage1()).nestedStaticTypes().toString());
        assertFalse(stage1().containsKey("com.acme.app.Holder.Nested"));
    }

    @Test
    @DisplayName("@IntentionallyStatic is read out of the class file, CLASS retention and all")
    void readsTheAllowlistAnnotation() throws IOException {
        Map<String, SweepReport.Row> rows = stage1();

        assertEquals("JVM_INFRASTRUCTURE", row(rows, "pool").field().allowlistReason());
        assertTrue(row(rows, "pool").field().allowlisted());
        assertNull(row(rows, "cache").field().allowlistReason());
        assertEquals(SweepReport.Bucket.WORKLIST, row(rows, "pool").bucket(),
                "the gate is green on it; the sweep still counts it and shows the reason");
    }

    @Test
    @DisplayName("--diff gives every stage-1 row a fate, and names the statics the migration grew")
    void diffGivesEveryRowAFate(@TempDir Path out) throws IOException {
        String report = run(out, trees.stage1().toString(), "--cp", trees.stage1().toString(),
                "--diff", trees.stage2().toString());

        assertTrue(report.contains("| `Holder.frame` | gone"), report);
        assertTrue(report.contains("| `Holder.loggedIn` | made a constant"), report);
        assertTrue(report.contains(
                "| `Holder.cache` | a verdict was recorded — `@IntentionallyStatic`"
                        + " (`WORLD_GLOBAL_READ_MOSTLY`) |"), report);
        assertTrue(report.contains("| `Holder.panels` | **still unvetted**"), report);
        assertTrue(report.contains("New statics the migration introduced: 1."), report);
        assertTrue(report.contains("`Holder.extra` : Map — **unvetted**"), report);
    }

    @Test
    @DisplayName("the report carries the allowlist rule inline, so it needs no second window")
    void printsTheRuleInline(@TempDir Path out) throws IOException {
        String report = run(out, trees.stage1().toString());

        assertTrue(report.contains("**`IMMUTABLE_CONSTANT`**"), report);
        assertTrue(report.contains("**`WORLD_GLOBAL_READ_MOSTLY`**"), report);
        assertTrue(report.contains("**`JVM_INFRASTRUCTURE`**"), report);
        assertTrue(report.contains("**`COUNTER`**"), report);
        assertTrue(report.contains("`javax.swing.KeyStroke`"), "the named immutable types: " + report);
        assertTrue(report.contains("## The funnel"), report);
        assertTrue(report.contains("## Worklist — "), report);
        assertTrue(report.contains("A progress meter, not a gate."), report);
    }

    @Test
    @DisplayName("every section prints even when empty — checked-and-clear must differ from never-looked")
    void printsEmptySections(@TempDir Path out) throws IOException {
        String report = run(out, trees.stage2().toString());

        assertTrue(report.contains("## `static` nested types — 0"), report);
        assertTrue(report.contains("(none)"), report);
    }

    @Test
    @DisplayName("stale class files are called out — a sweep of yesterday's build is yesterday's worklist")
    void warnsAboutStaleClasses(@TempDir Path out) throws IOException {
        Path source = root.resolve("src-stage1/com/acme/app/Holder.java");
        Files.setLastModifiedTime(source,
                FileTime.from(Instant.now().plus(2, ChronoUnit.HOURS)));

        String report = run(out, trees.stage1().toString(), "--src",
                root.resolve("src-stage1").toString());
        assertTrue(report.contains("**Stale classes.**"), report);

        Files.setLastModifiedTime(source, FileTime.from(Instant.now().minus(1, ChronoUnit.HOURS)));
        assertTrue(run(out, trees.stage1().toString(), "--src", root.resolve("src-stage1").toString())
                .contains("newer than every source file. Fresh."));
    }

    @Test
    @DisplayName("no class files is an error, not an empty worklist reported as success")
    void refusesAnUnbuiltTree(@TempDir Path empty) {
        assertEquals(2, exit(empty.toString()),
                "a sweep that read nothing must not report a clean tree");
        assertEquals(2, exit(), "no arguments prints the usage");
    }

    @Test
    @DisplayName("a --diff tree with no class files is an error — against nothing, every row reads as gone")
    void refusesAnEmptyDiffTree(@TempDir Path out) {
        assertEquals(2, exit(trees.stage1().toString(), "--cp", trees.stage1().toString(),
                "--diff", out.resolve("taget/classes").toString(),
                "--report", out.resolve("diff.md").toString()));
        assertFalse(Files.exists(out.resolve("diff.md")), "no report, so no all-gone one either");
    }

    @Test
    @DisplayName("a --lib that does not exist is a note, not silence — its absence turns rows unresolved")
    void notesAMissingLib(@TempDir Path out) throws IOException {
        Path typo = out.resolve("target/dependecy");
        String report = run(out, trees.stage1().toString(), "--lib", typo.toString());
        assertTrue(report.contains("**Not found:** --lib `" + typo + "` — nothing was read there."), report);
    }

    private static String run(Path out, String... args) throws IOException {
        Path report = out.resolve("static-sweep.md");
        String[] all = new String[args.length + 2];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = "--report";
        all[args.length + 1] = report.toString();
        assertEquals(0, StaticSweep.run(all),
                "a progress meter exits 0 whenever it ran, however full the worklist");
        return Files.readString(report, StandardCharsets.UTF_8);
    }

    private static int exit(String... args) {
        try {
            return StaticSweep.run(args);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
