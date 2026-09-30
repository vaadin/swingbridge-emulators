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

package com.vaadin.swingbridge.migration.tool.importswap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites one file's Swing references onto their emulators, in two independent passes:
 *
 * <pre>{@code
 * Swapper.Result r = new Swapper(table, siblings).rewrite(source);
 * r.text();       // the rewritten source, or null when nothing changed
 * r.changes();    // "javax.swing.JButton -> vaadinx.swing.JButton (import)"
 * r.declines();   // what it refused to touch, and why
 * }</pre>
 *
 * <p><b>Pass 1 — in-code fully-qualified references.</b> A prefix swap driven by a table lookup:
 * {@code new javax.swing.JTextField()} becomes {@code new vaadinx.swing.JTextField()}. This carries
 * the bulk of a form-designer-generated app, where every widget is written fully-qualified and the
 * import block mentions no Swing at all.
 *
 * <p><b>Pass 2 — imports.</b> Resolves each referenced simple name the way javac resolved it when
 * the file last compiled, then emits exactly the imports the file needs. On-demand imports of
 * rewritable packages are deleted and replaced by single-type imports — for stay-JDK types too — so
 * that a reference the scan <em>misses</em> becomes a compile error rather than a silent bind to the
 * JDK type. That loudness is the property the whole approach rests on.
 *
 * <p>The passes are independent: a fully-qualified reference needs no import before or after the
 * swap, so neither pass can invalidate the other's work.
 *
 * <p>Nothing is guessed. Where meaning cannot be preserved — an unreadable import, a simple name two
 * deleted wildcards both supply — the whole file is left untouched and the reason reported, rather
 * than the tool doing its best with part of it.
 */
final class Swapper {

    /** A candidate type reference: an uppercase-initial identifier not reached through a dot. */
    private static final Pattern SIMPLE_NAME = Pattern.compile("(?<![\\w.$])([A-Z][\\w$]*)");

    /** Javadoc references, which keep an import alive even though the type appears in no code. */
    private static final Pattern DOC_REFERENCE =
            Pattern.compile("\\{@link(?:plain)?\\s+([A-Z][\\w$]*)|@(?:see|throws|exception)\\s+([A-Z][\\w$]*)");

    private final PortedTypes table;
    private final SiblingIndex siblings;

    /** JDK FQN pattern → emulator FQN, longest JDK name first. Compiled once, not per file. */
    private final Map<Pattern, String> qualifiedPatterns;

    private final Map<String, Boolean> enumerable = new HashMap<>();

    private final Set<String> addonRoots;

    Swapper(PortedTypes table, SiblingIndex siblings) {
        this.table = table;
        this.siblings = siblings;
        this.addonRoots = table.addonRoots();
        Map<Pattern, String> patterns = new LinkedHashMap<>();
        table.byDescendingLength().forEach((jdk, emulator) -> patterns.put(
                Pattern.compile("(?<![\\w.$])" + Pattern.quote(jdk) + "(?![\\w$])"), emulator));
        this.qualifiedPatterns = patterns;
    }

    /** What one file's rewrite did, or declined to do. */
    record Result(String text, List<String> changes, List<String> declines) {

        boolean changed() {
            return text != null;
        }
    }

    /** Pass 2's outcome: the new text, or a refusal that discards pass 1's work too. */
    private record ImportPass(String text, boolean declineWholeFile) {
    }

    Result rewrite(String source) {
        SourceFile file = SourceFile.parse(source);
        List<String> changes = new ArrayList<>();
        List<String> declines = new ArrayList<>();

        if (file.unparsableImport() != null) {
            declines.add("left untouched — cannot read the import `" + file.unparsableImport()
                    + "`; the JLS allows whitespace and newlines inside an import that this tool does "
                    + "not parse. Rewrite it onto one line and re-run.");
            return new Result(null, changes, declines);
        }

        String afterPass1 = rewriteQualifiedReferences(file, changes);
        // Re-parse so pass 2's reference scan reads the body pass 1 rewrote. Pass 1 never touches an
        // import line, so line numbering is unchanged.
        ImportPass pass2 = rewriteImports(SourceFile.parse(afterPass1), changes, declines);
        if (pass2.declineWholeFile()) {
            return new Result(null, List.of(), declines);
        }

        String result = pass2.text() != null ? pass2.text() : afterPass1;
        return new Result(result.equals(source) ? null : result, changes, declines);
    }

    // ---------------------------------------------------------------- pass 1

    private String rewriteQualifiedReferences(SourceFile file, List<String> changes) {
        String scanned = Lexed.of(file.source()).codeAndComments();
        Set<Integer> importLines = new LinkedHashSet<>();
        file.imports().forEach(i -> importLines.add(i.lineIndex()));
        int[] lineOf = lineIndexPerOffset(file.source());

        List<int[]> spans = new ArrayList<>();
        Map<Integer, String> replacements = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (Map.Entry<Pattern, String> row : qualifiedPatterns.entrySet()) {
            Matcher m = row.getKey().matcher(scanned);
            while (m.find()) {
                if (importLines.contains(lineOf[m.start()]) || overlaps(spans, m.start(), m.end())) {
                    continue;
                }
                spans.add(new int[] {m.start(), m.end()});
                replacements.put(m.start(), row.getValue());
                counts.merge(scanned.substring(m.start(), m.end()) + " -> " + row.getValue(), 1, Integer::sum);
            }
        }
        if (replacements.isEmpty()) {
            return file.source();
        }
        counts.forEach((k, n) -> changes.add(k + " (" + n + " in-code reference" + (n == 1 ? "" : "s") + ")"));
        return applyReplacements(file.source(), spans, replacements);
    }

    private static boolean overlaps(List<int[]> spans, int from, int to) {
        return spans.stream().anyMatch(s -> from < s[1] && s[0] < to);
    }

    private static String applyReplacements(String source, List<int[]> spans, Map<Integer, String> replacements) {
        List<int[]> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(s -> s[0]));
        StringBuilder sb = new StringBuilder();
        int at = 0;
        for (int[] span : sorted) {
            sb.append(source, at, span[0]).append(replacements.get(span[0]));
            at = span[1];
        }
        return sb.append(source.substring(at)).toString();
    }

    private static int[] lineIndexPerOffset(String source) {
        int[] out = new int[source.length() + 1];
        int line = 0;
        for (int i = 0; i < source.length(); i++) {
            out[i] = line;
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        out[source.length()] = line;
        return out;
    }

    // ---------------------------------------------------------------- pass 2

    private ImportPass rewriteImports(SourceFile file, List<String> changes, List<String> declines) {
        Set<String> referenced = referencedSimpleNames(file);

        List<String> kept = new ArrayList<>();
        List<String> deletedWildcards = new ArrayList<>();
        Set<String> coveredNames = new LinkedHashSet<>();
        boolean touched = false;

        for (SourceFile.Import imp : file.imports()) {
            if (imp.isStatic()) {
                kept.add(imp.text());
                String owner = imp.onDemand() ? imp.fqn() : imp.fqn().substring(0, imp.fqn().lastIndexOf('.'));
                if (table.emulatorFor(owner) != null) {
                    declines.add("kept `" + imp.text().strip() + "` — a static import of an emulated type. "
                            + "The JDK member it names still resolves and still holds the same value, so the "
                            + "file compiles; swap it by hand if the emulator reimplements that member.");
                }
                continue;
            }
            if (imp.onDemand()) {
                if (table.isRewritablePackage(imp.fqn()) && canEnumerate(imp.fqn())) {
                    deletedWildcards.add(imp.fqn());
                    changes.add("deleted `import " + imp.fqn() + ".*;` — replaced by single-type imports");
                    touched = true;
                } else {
                    kept.add(imp.text());
                }
                continue;
            }
            String swapped = table.swapFqn(imp.fqn());
            coveredNames.add(imp.simpleName());
            if (swapped != null) {
                kept.add(imp.text().replace(imp.fqn(), swapped));
                changes.add(imp.fqn() + " -> " + swapped + " (import)");
                touched = true;
            } else {
                kept.add(imp.text());
                if (addonRoots.stream().anyMatch(root -> imp.fqn().startsWith(root + "."))) {
                    declines.add("no mapping for `" + imp.fqn() + "` — it is under an SB-Emulators add-on's package "
                            + "root, but no add-on on the classpath ships a counterpart for it. The add-on "
                            + "replaces its upstream rather than depending on it, so this import will not "
                            + "resolve; see the add-on's META-INF/emul/MIGRATION.md for what it does cover.");
                }
            }
        }

        Set<String> emitted = new TreeSet<>();
        for (String name : referenced) {
            if (coveredNames.contains(name) || siblings.declares(file.packageName(), name)) {
                continue;
            }
            List<String> from = deletedWildcards.stream().filter(p -> exists(p, name)).toList();
            if (from.isEmpty()) {
                continue;
            }
            if (from.size() > 1) {
                declines.add("left untouched — `" + name + "` is supplied by both `" + from.get(0) + ".*` and `"
                        + from.get(1) + ".*`, so this tool cannot tell which the file meant (javac could not "
                        + "either, unless one was also imported explicitly). Import it explicitly and re-run.");
                return new ImportPass(null, true);
            }
            String fqn = from.get(0) + "." + name;
            String swapped = table.swapFqn(fqn);
            emitted.add("import " + (swapped != null ? swapped : fqn) + ";");
        }

        if (!touched && emitted.isEmpty()) {
            return new ImportPass(null, false);
        }
        emitted.forEach(line -> changes.add("added `" + line + "`"));
        return new ImportPass(rebuild(file, kept, emitted), false);
    }

    /**
     * Simple names the file references, from code and from javadoc alike.
     *
     * <p>Javadoc counts because dropping an import a {@code {@link}} still needs turns a working doc
     * reference into a dangling one — silent, where every other miss here is loud.
     */
    private Set<String> referencedSimpleNames(SourceFile file) {
        Lexed lex = Lexed.of(file.source());
        Set<Integer> importLines = new LinkedHashSet<>();
        file.imports().forEach(i -> importLines.add(i.lineIndex()));
        int[] lineOf = lineIndexPerOffset(file.source());

        Set<String> out = new LinkedHashSet<>();
        Matcher code = SIMPLE_NAME.matcher(lex.codeOnly());
        while (code.find()) {
            if (!importLines.contains(lineOf[code.start()])) {
                out.add(code.group(1));
            }
        }
        Matcher doc = DOC_REFERENCE.matcher(lex.commentsOnly());
        while (doc.find()) {
            out.add(doc.group(1) != null ? doc.group(1) : doc.group(2));
        }
        return out;
    }

    /**
     * True when this package's contents are visible to the tool, so deleting its on-demand import is
     * safe.
     *
     * <p>A JDK package always is. An add-on package is not — the add-on <em>replaces</em> its
     * upstream, so upstream is on no classpath here and the stay-upstream types could not be re-added
     * after a deletion. There the wildcard is kept and the ported types are redirected by single-type
     * imports, which outrank it.
     */
    private boolean canEnumerate(String pkg) {
        return enumerable.computeIfAbsent(pkg, p -> table.byDescendingLength().keySet().stream()
                .filter(fqn -> fqn.startsWith(p + ".") && fqn.indexOf('.', p.length() + 1) < 0)
                .anyMatch(Swapper::loadable));
    }

    private static boolean exists(String pkg, String simpleName) {
        return loadable(pkg + "." + simpleName);
    }

    private static boolean loadable(String fqn) {
        try {
            Class.forName(fqn, false, Swapper.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Splices the new import block back in, keeping the original lines in their original order so the
     * migrator's diff shows only the migration.
     */
    private static String rebuild(SourceFile file, List<String> kept, Set<String> emitted) {
        List<String> block = new ArrayList<>(kept);
        Set<String> present = new LinkedHashSet<>();
        kept.forEach(l -> present.add(l.strip()));
        emitted.stream().filter(l -> !present.contains(l)).forEach(block::add);

        List<String> out = new ArrayList<>(file.lines().subList(0, file.importBlockStart()));
        // A file that had no imports gets a blank line before the block it is about to grow one.
        if (file.imports().isEmpty() && !block.isEmpty()) {
            out.add("");
        }
        out.addAll(block);
        out.addAll(file.lines().subList(file.importBlockEnd(), file.lines().size()));
        return String.join("\n", out);
    }
}
