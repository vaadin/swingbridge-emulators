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

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the permissive lanes (Apache-2.0, and the seeds' 0BSD) free of carried-over JDK expression,
 * by measuring every file {@link LicenseHeaderTest#inPermissiveLane} admits against the running JDK's own {@code lib/src.zip}.
 * {@code LicenseHeaderTest} checks what a file <em>says</em> about its provenance; this checks what
 * it <em>contains</em> — the only control that works when an agent can reproduce a JDK body from
 * memory without ever opening {@code src.zip}.
 *
 * <p>Two gates, both absolute rather than a percentage, since a percentage red-flags every small
 * event class whose few lines are plumbing:
 * <ul>
 *   <li><b>Body run</b> below {@link #BODY_RUN_LIMIT}: the longest run of consecutive method-body
 *       statements that also sits consecutively in <em>one</em> JDK file, weighted by
 *       identifier/number tokens. API shape is excluded on both sides before comparing —
 *       signatures, fields and constants, {@code this(…)} / {@code super(…)} delegation,
 *       {@code this.x = x;}, and one-statement bodies (accessors, {@code listenerList.add(…)}) —
 *       because an implementor must write those verbatim.</li>
 *   <li><b>No comment</b> of 30+ characters identical to a JDK comment. Copied code drags its
 *       comments along, and nothing written independently matches one.</li>
 * </ul>
 * Single-file concentration and shared string literals are printed as information, not gated.
 *
 * <p>A failure lists the run. Rewrite that code against the published javadoc and observed
 * behaviour; if it is genuinely forced by the API, say why in {@code PROVENANCE.md} § "The
 * exceptions" and narrow the exclusions here rather than raising the limit.
 *
 * <p>Blind spots, on purpose: it detects <b>copying, not paraphrase</b> — a body kept in
 * the JDK's structure with renamed locals scores as original; and string-literal
 * <b>contents are stripped</b> before comparing, so copied message text (which
 * {@code R_match_swing_errors} often wants anyway) never scores here and appears only in the
 * informational literal count. The {@link #canaryEmulatorsStillScore() canary} asserts that
 * known ports in {@code :emulators} score above the limit, so a broken parser or an empty
 * corpus fails instead of passing clean. {@code D_similarity_gate} records the calibration.
 */
class JdkSimilarityTest {

    /**
     * A body run of this weight or more fails. The lane peaks at 17 ({@code SScrollbar}'s clamp,
     * Swing's behaviour reproduced on purpose), and everything outside {@code :surrogates} at 10;
     * the canaries score 34 to 381.
     */
    static final int BODY_RUN_LIMIT = 20;

    private static final int MIN_COMMENT = 30;

    private static final List<String> CORPUS_PREFIXES = List.of(
            "java.desktop/javax/swing/", "java.desktop/java/awt/",
            "java.base/java/util/", "java.base/java/text/");

    /** Known ports; each must score at or above {@link #BODY_RUN_LIMIT}. */
    private static final List<String> CANARIES = List.of(
            "emulators/src/main/java/vaadinx/swing/table/DefaultTableColumnModel.java",
            "emulators/src/main/java/vaadinx/swing/table/TableColumn.java",
            "emulators/src/main/java/vaadinx/awt/GraphicsConfiguration.java",
            "emulators/src/main/java/vaadinx/awt/BorderLayout.java",
            "emulators/src/main/java/vaadinx/awt/CheckboxGroup.java");

    /**
     * Pruned directory names, matching {@code LicenseHeaderTest}'s — {@code .claude} because it
     * can hold a whole second checkout as a git worktree.
     */
    private static final Set<String> PRUNED =
            Set.of("target", "node_modules", ".git", ".claude", "node", "generated");

    private static final Pattern STR = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern CHR = Pattern.compile("'(?:\\\\.|[^'\\\\])*'");
    private static final Pattern WS = Pattern.compile("\\s+");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_$][A-Za-z_$0-9]*|[0-9]+");
    /** {@code this.x = x;} — a parameter stored in its same-named field, API shape in any codebase. */
    private static final Pattern PARAM_STORE = Pattern.compile("this\\.(\\w+) = \\1;");

    private static File repoRoot;
    private static List<String> jdkFiles;
    /** Normalised body statement → every (file, position) it occurs at. */
    private static Map<String, List<int[]>> jdkUnits;
    private static Set<String> jdkComments;
    private static Set<String> jdkLiterals;
    private static Pattern surrogateNames;

    @BeforeAll
    static void loadCorpus() throws IOException {
        repoRoot = findRepoRoot();
        Path srcZip = Path.of(System.getProperty("java.home"), "lib", "src.zip");
        if (!Files.isRegularFile(srcZip)) {
            String msg = "no JDK sources at " + srcZip + " — install your JDK's source package"
                    + " (e.g. openjdk-NN-source) to run the Apache-lane similarity gate";
            if (System.getenv("CI") != null) {
                fail(msg + "; on CI this gate must run");
            }
            Assumptions.abort(msg);
        }
        Map<String, String> sources = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(srcZip.toFile())) {
            for (ZipEntry e : zip.stream().toList()) {
                String n = e.getName();
                if (n.endsWith(".java") && CORPUS_PREFIXES.stream().anyMatch(n::startsWith)) {
                    sources.put(n, new String(zip.getInputStream(e).readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        jdkFiles = new ArrayList<>(sources.keySet());
        jdkUnits = new HashMap<>();
        jdkComments = new HashSet<>();
        jdkLiterals = new HashSet<>();
        Map<String, List<String>> units = bodyUnits(sources, Pattern.compile("(?!)"));
        for (int id = 0; id < jdkFiles.size(); id++) {
            String name = jdkFiles.get(id);
            List<String> u = units.getOrDefault(name, List.of());
            for (int i = 0; i < u.size(); i++) {
                jdkUnits.computeIfAbsent(u.get(i), k -> new ArrayList<>()).add(new int[]{id, i});
            }
            Lexed lexed = lex(sources.get(name));
            jdkComments.addAll(lexed.comments());
            jdkLiterals.addAll(lexed.literals());
        }
        assertTrue(jdkFiles.size() > 1000 && jdkUnits.size() > 50_000,
                "corpus too small (" + jdkFiles.size() + " files, " + jdkUnits.size()
                        + " statements) — is " + srcZip + " a real JDK source archive?");
        surrogateNames = surrogateNames();
    }

    // --- the gates ---------------------------------------------------------------------------

    @Test
    @DisplayName("no Apache-lane file carries a contiguous run of JDK method-body statements")
    void apacheLaneCarriesNoContiguousJdkBody() {
        List<Measured> files = measureApacheLane();
        report(files);
        List<String> over = files.stream().filter(m -> m.run().weight() >= BODY_RUN_LIMIT)
                .map(m -> m.path() + " — weight " + m.run().weight() + ", " + m.run().units().size()
                        + " statements matching " + m.run().jdkFile() + ":\n        "
                        + String.join("\n        ", m.run().units()))
                .toList();
        if (!over.isEmpty()) {
            fail(over.size() + " Apache-lane file(s) reach body-run weight " + BODY_RUN_LIMIT
                    + " — JDK expression inside a permissive artifact (D_licence_lanes). Rewrite"
                    + " against the javadoc and observed behaviour; see JdkSimilarityTest's javadoc.\n    "
                    + String.join("\n    ", over));
        }
    }

    @Test
    @DisplayName("no Apache-lane file carries a comment identical to a JDK comment")
    void apacheLaneCarriesNoJdkComment() {
        Map<String, List<String>> hits = new TreeMap<>();
        for (Path f : apacheLaneFiles()) {
            List<String> matched = lex(read(f)).comments().stream().filter(jdkComments::contains).toList();
            if (!matched.isEmpty()) {
                hits.put(rel(f), matched);
            }
        }
        if (!hits.isEmpty()) {
            fail("JDK comment text in the Apache lane — copied code drags its comments along:\n    "
                    + hits.entrySet().stream()
                    .map(e -> e.getKey() + ":\n        " + String.join("\n        ", e.getValue()))
                    .collect(Collectors.joining("\n    ")));
        }
    }

    @Test
    @DisplayName("canary: known emulator ports still score above both gates")
    void canaryEmulatorsStillScore() {
        List<String> blind = new ArrayList<>();
        for (String c : CANARIES) {
            Run run = measure(Path.of(repoRoot.getPath(), c)).run();
            if (run.weight() < BODY_RUN_LIMIT) {
                blind.add(c + " scores " + run.weight());
            }
        }
        long commentHits = CANARIES.stream()
                .flatMap(c -> lex(read(Path.of(repoRoot.getPath(), c))).comments().stream())
                .filter(jdkComments::contains).count();
        assertTrue(blind.isEmpty(), "the body-run gate has gone blind — ports that must score >= "
                + BODY_RUN_LIMIT + " do not: " + blind);
        assertTrue(commentHits > 0, "the comment gate has gone blind — the canaries share no"
                + " comment with the JDK any more");
    }

    // --- measurement -------------------------------------------------------------------------

    record Run(int weight, String jdkFile, List<String> units) {}

    record Measured(String path, Run run, int concentration, int literals) {}

    private List<Measured> measureApacheLane() {
        List<Path> files = apacheLaneFiles();
        assertTrue(files.size() > 250, "expected 250+ Apache-lane files, found " + files.size());
        Map<String, String> sources = new LinkedHashMap<>();
        files.forEach(f -> sources.put(rel(f), read(f)));
        Map<String, List<String>> units = bodyUnits(sources, surrogateNames);
        return sources.keySet().stream()
                .map(p -> measured(p, units.getOrDefault(p, List.of()), sources.get(p))).toList();
    }

    private Measured measure(Path file) {
        String rel = rel(file);
        String src = read(file);
        return measured(rel, bodyUnits(Map.of(rel, src), surrogateNames).getOrDefault(rel, List.of()), src);
    }

    private static Measured measured(String path, List<String> units, String src) {
        Run best = new Run(0, null, List.of());
        Map<Long, Integer> prev = new HashMap<>();
        for (int i = 0; i < units.size(); i++) {
            Map<Long, Integer> cur = new HashMap<>();
            int w = weight(units.get(i));
            for (int[] at : jdkUnits.getOrDefault(units.get(i), List.of())) {
                int run = prev.getOrDefault(key(at[0], at[1] - 1), 0) + w;
                cur.put(key(at[0], at[1]), run);
                if (run > best.weight()) {
                    int start = i;
                    for (int acc = run; acc > 0; start--) {
                        acc -= weight(units.get(start));
                    }
                    best = new Run(run, jdkFiles.get(at[0]), units.subList(start + 1, i + 1));
                }
            }
            prev = cur;
        }
        Map<Integer, Integer> perJdkFile = new HashMap<>();
        for (String u : new HashSet<>(units)) {
            jdkUnits.getOrDefault(u, List.of()).stream().map(at -> at[0]).distinct()
                    .forEach(f -> perJdkFile.merge(f, weight(u), Integer::sum));
        }
        int concentration = perJdkFile.values().stream().max(Integer::compare).orElse(0);
        int literals = (int) lex(src).literals().stream().filter(jdkLiterals::contains).count();
        return new Measured(path, best, concentration, literals);
    }

    private static long key(int file, int pos) {
        return ((long) file << 32) | (pos & 0xffffffffL);
    }

    private static int weight(String unit) {
        return (int) IDENT.matcher(unit).results().count();
    }

    private static void report(List<Measured> files) {
        StringBuilder sb = new StringBuilder("JdkSimilarityTest — Apache lane, informational:\n");
        sb.append("  top body runs (gate at ").append(BODY_RUN_LIMIT).append("):\n");
        files.stream().sorted(Comparator.comparingInt((Measured m) -> m.run().weight()).reversed()).limit(5)
                .forEach(m -> sb.append("    ").append(m.run().weight()).append("  ").append(m.path())
                        .append(" <- ").append(m.run().jdkFile()).append('\n'));
        sb.append("  top single-JDK-file concentration (not gated):\n");
        files.stream().sorted(Comparator.comparingInt(Measured::concentration).reversed()).limit(5)
                .forEach(m -> sb.append("    ").append(m.concentration()).append("  ").append(m.path()).append('\n'));
        sb.append("  multi-word string literals shared with the JDK (not gated — blind spot): ")
                .append(files.stream().mapToInt(Measured::literals).sum()).append('\n');
        System.out.print(sb);
    }

    // --- normalisation -------------------------------------------------------------------------

    /**
     * Each file's method-body statements in source order, flattened (a compound statement becomes
     * its header plus its children) and normalised: whitespace collapsed, literal contents
     * stripped, SB-Emulators names mapped onto the JDK's.
     */
    private static Map<String, List<String>> bodyUnits(Map<String, String> sources, Pattern renames) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        Map<String, List<String>> out = new HashMap<>();
        List<Map.Entry<String, String>> all = new ArrayList<>(sources.entrySet());
        // Batched so a whole corpus of trees is never alive at once.
        for (int from = 0; from < all.size(); from += 200) {
            List<Source> batch = all.subList(from, Math.min(all.size(), from + 200)).stream()
                    .map(e -> new Source(e.getKey(), e.getValue())).toList();
            // javac hands the file objects back wrapped, so match them up by URI, not by cast.
            Map<URI, String> names = batch.stream().collect(Collectors.toMap(Source::toUri, s -> s.name));
            JavacTask task = (JavacTask) javac.getTask(null, null, new DiagnosticCollector<>(),
                    List.of("-proc:none"), null, batch);
            try {
                for (CompilationUnitTree cu : task.parse()) {
                    List<String> units = new ArrayList<>();
                    cu.getTypeDecls().forEach(t -> members(t, units));
                    out.put(names.get(cu.getSourceFile().toUri()),
                            units.stream().map(u -> normalise(u, renames))
                                    .filter(u -> weight(u) > 1 && !PARAM_STORE.matcher(u).matches()).toList());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }

    private static void members(Tree type, List<String> units) {
        if (!(type instanceof ClassTree c)) {
            return;
        }
        for (Tree m : c.getMembers()) {
            if (m instanceof ClassTree) {
                members(m, units);
            } else if (m instanceof BlockTree init) {
                addBody(init.getStatements(), units);
            } else if (m instanceof MethodTree method && method.getBody() != null) {
                List<? extends StatementTree> body = method.getBody().getStatements();
                if (!body.isEmpty() && isDelegation(body.get(0))) {
                    body = body.subList(1, body.size());
                }
                addBody(body, units);
            }
        }
    }

    /** Adds a body's flattened statements, unless it is one statement — the API-shape case. */
    private static void addBody(List<? extends StatementTree> body, List<String> units) {
        List<String> flat = new ArrayList<>();
        body.forEach(s -> flatten(s, flat));
        if (flat.stream().filter(u -> weight(u) > 1).count() > 1) {
            units.addAll(flat);
        }
    }

    private static boolean isDelegation(StatementTree s) {
        return s instanceof ExpressionStatementTree e
                && e.getExpression() instanceof MethodInvocationTree call
                && call.getMethodSelect() instanceof IdentifierTree id
                && (id.getName().contentEquals("this") || id.getName().contentEquals("super"));
    }

    private static void flatten(Tree s, List<String> out) {
        switch (s) {
            case BlockTree b -> b.getStatements().forEach(x -> flatten(x, out));
            case IfTree t -> {
                out.add("if " + t.getCondition());
                flatten(t.getThenStatement(), out);
                if (t.getElseStatement() != null) {
                    flatten(t.getElseStatement(), out);
                }
            }
            case WhileLoopTree t -> {
                out.add("while " + t.getCondition());
                flatten(t.getStatement(), out);
            }
            case DoWhileLoopTree t -> {
                flatten(t.getStatement(), out);
                out.add("while " + t.getCondition());
            }
            case ForLoopTree t -> {
                out.add("for (" + t.getInitializer() + "; " + t.getCondition() + "; " + t.getUpdate() + ")");
                flatten(t.getStatement(), out);
            }
            case EnhancedForLoopTree t -> {
                out.add("for (" + t.getVariable() + " : " + t.getExpression() + ")");
                flatten(t.getStatement(), out);
            }
            case SwitchTree t -> {
                out.add("switch " + t.getExpression());
                t.getCases().forEach(c -> flatten(c, out));
            }
            case CaseTree t -> {
                out.add("case " + t.getLabels());
                if (t.getStatements() != null) {
                    t.getStatements().forEach(x -> flatten(x, out));
                } else if (t.getBody() != null) {
                    flatten(t.getBody(), out);
                }
            }
            case TryTree t -> {
                t.getResources().forEach(r -> out.add("try " + r));
                flatten(t.getBlock(), out);
                for (CatchTree c : t.getCatches()) {
                    out.add("catch " + c.getParameter());
                    flatten(c.getBlock(), out);
                }
                if (t.getFinallyBlock() != null) {
                    flatten(t.getFinallyBlock(), out);
                }
            }
            case SynchronizedTree t -> {
                out.add("synchronized " + t.getExpression());
                flatten(t.getBlock(), out);
            }
            case LabeledStatementTree t -> flatten(t.getStatement(), out);
            case ClassTree t -> { }
            case EmptyStatementTree t -> { }
            default -> out.add(s.toString());
        }
    }

    private static String normalise(String unit, Pattern renames) {
        String u = WS.matcher(unit).replaceAll(" ").trim();
        u = STR.matcher(u).replaceAll("\"\"");
        u = CHR.matcher(u).replaceAll("''");
        u = u.replace("com.vaadin.swingbridge.surrogates.awt", "java.awt")
                .replace("com.vaadin.swingbridge.surrogates.swing", "javax.swing")
                .replace("com.vaadin.swingbridge.surrogates", "javax.swing")
                .replace("vaadinx.swing", "javax.swing").replace("vaadinx.awt", "java.awt");
        return renames.matcher(u).replaceAll(r -> r.group().substring(1));
    }

    /** {@code SJButton} → {@code JButton}: the surrogates' own type names, and nothing else named S…. */
    private static Pattern surrogateNames() {
        try (Stream<Path> walk = Files.walk(Path.of(repoRoot.getPath(), "surrogates", "src", "main", "java"))) {
            String alternation = walk.map(p -> p.getFileName().toString())
                    .filter(n -> n.matches("S[A-Z]\\w*\\.java"))
                    .map(n -> n.substring(0, n.length() - 5))
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .collect(Collectors.joining("|"));
            return Pattern.compile("\\b(?:" + alternation + ")\\b");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    record Lexed(List<String> comments, List<String> literals) {}

    /**
     * Comments (normalised lines of {@value #MIN_COMMENT}+ characters) and multi-word string
     * literals, from the {@code package} line on — the licence header above it quotes GPLv2, whose
     * wording is in every Oracle notice too.
     */
    static Lexed lex(String src) {
        int pkg = src.indexOf("\npackage ");
        String s = pkg >= 0 ? src.substring(pkg + 1) : src;
        List<String> comments = new ArrayList<>();
        List<String> literals = new ArrayList<>();
        int n = s.length();
        for (int i = 0; i < n; ) {
            char c = s.charAt(i);
            if (s.startsWith("//", i)) {
                int end = s.indexOf('\n', i);
                end = end < 0 ? n : end;
                addComment(s.substring(i + 2, end), comments);
                i = end;
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                end = end < 0 ? n : end;
                for (String line : s.substring(i + 2, end).split("\n")) {
                    addComment(line, comments);
                }
                i = end + 2;
            } else if (s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end;
                addLiteral(s.substring(i + 3, end), literals);
                i = end + 3;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c && s.charAt(j) != '\n') {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                if (c == '"') {
                    addLiteral(s.substring(i + 1, Math.min(j, n)), literals);
                }
                i = j + 1;
            } else {
                i++;
            }
        }
        return new Lexed(comments, literals);
    }

    private static void addComment(String line, List<String> out) {
        String t = WS.matcher(line.strip().replaceFirst("^\\*+", "")).replaceAll(" ").strip();
        if (t.length() >= MIN_COMMENT) {
            out.add(t);
        }
    }

    private static void addLiteral(String lit, List<String> out) {
        String t = WS.matcher(lit).replaceAll(" ").strip();
        if (t.length() >= 12 && t.contains(" ")) {
            out.add(t);
        }
    }

    private static final class Source extends SimpleJavaFileObject {
        final String name;
        private final String text;

        Source(String name, String text) {
            super(URI.create("string:///" + name.replace(' ', '_')), Kind.SOURCE);
            this.name = name;
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    // --- files ---------------------------------------------------------------------------------

    /**
     * Every {@code .java} file under a {@code src/} directory in the Apache-2.0 lane. The lane is
     * the default rather than a list, so this walks the whole repository and lets
     * {@link LicenseHeaderTest#inPermissiveLane} decide — which is what puts a new module under this
     * gate from its first build.
     */
    private static List<Path> apacheLaneFiles() {
        List<Path> out = new ArrayList<>();
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
                    String rel = rel(file);
                    if (attrs.isRegularFile() && rel.endsWith(".java") && rel.contains("/src/")
                            && LicenseHeaderTest.inPermissiveLane(rel)) {
                        out.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String rel(Path f) {
        return repoRoot.toPath().relativize(f.toAbsolutePath()).toString().replace(File.separatorChar, '/');
    }

    private static String read(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static File findRepoRoot() {
        for (File dir = new File(".").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            if (new File(dir, "DECISION-ID-MAP.md").isFile()) {
                return dir;
            }
        }
        throw new IllegalStateException("cannot locate the repo root from " + new File(".").getAbsolutePath());
    }
}
