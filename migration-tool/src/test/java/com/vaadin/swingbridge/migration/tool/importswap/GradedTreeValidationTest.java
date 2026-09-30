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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the tool over each testapp's pristine stage-1 tree and compares the result against the
 * human-graded stage-2 tree committed beside it.
 *
 * <p>Comparison is by <b>resolved binding</b> — simple name → the {@code vaadinx} type it resolves
 * to — never by text. The graded run rewrote {@code javax.swing.*} to {@code vaadinx.swing.*} where
 * this tool emits single-type imports; both compile and mean the same thing, so a textual diff scores
 * dozens of false failures.
 *
 * <p>The assertion is narrow on purpose: a binding both trees have must <b>agree</b>. Bindings only
 * one side has are the residue classes the design already catalogued (add-on types with no table
 * rows, code a later migration phase deleted, semantic rewrites the migration introduced) and are
 * printed for inspection rather than failed on — they are not import-phase judgement, and pinning
 * them here would freeze the graded trees as a spec they were never meant to be.
 */
class GradedTreeValidationTest {

    private static final Path TABLE =
            Path.of("../emulators/src/main/resources/META-INF/emul/ported-types.tsv");

    private static final List<String> APPS = List.of("crud", "jlawyer-shape", "inventory");

    private static final Pattern IMPORT =
            Pattern.compile("(?m)^[ \t]*import[ \t]+(static[ \t]+)?([\\w.]+(?:\\.\\*)?)[ \t]*;");

    private static final Pattern QUALIFIED_VAADINX = Pattern.compile("(?<![\\w.$])(vaadinx(?:\\.\\w+)+)");

    private static final Pattern SIMPLE_NAME = Pattern.compile("(?<![\\w.$])([A-Z][\\w$]*)");

    @Test
    @DisplayName("every binding the tool and the graded stage-2 tree share, they agree on")
    void toolAgreesWithGradedTrees() throws IOException {
        assumeTrue(Files.exists(TABLE), "swap table not built yet");
        PortedTypes table = PortedTypes.load(List.of(TABLE));
        Set<String> emulatorNames = new LinkedHashSet<>(table.byDescendingLength().values());

        int compared = 0;
        int exact = 0;
        List<String> disagreements = new ArrayList<>();
        Map<String, Integer> onlyTool = new TreeMap<>();
        Map<String, Integer> onlyGraded = new TreeMap<>();

        for (String app : APPS) {
            Path stage1 = Path.of("../testapps", app, "swing/src/main/java");
            Path stage2 = Path.of("../testapps", app, "1-emulators/src/main/java");
            assumeTrue(Files.isDirectory(stage1) && Files.isDirectory(stage2), "testapp " + app + " missing");

            SiblingIndex siblings = SiblingIndex.scan(List.of(stage1));
            Swapper swapper = new Swapper(table, siblings);

            for (Path source : javaFiles(stage1)) {
                Path graded = stage2.resolve(stage1.relativize(source));
                if (!Files.exists(graded)) {
                    continue;
                }
                compared++;
                String original = Files.readString(source, StandardCharsets.UTF_8);
                Swapper.Result r = swapper.rewrite(original);
                Map<String, String> mine = bindings(r.changed() ? r.text() : original, emulatorNames);
                Map<String, String> theirs = bindings(Files.readString(graded, StandardCharsets.UTF_8),
                        emulatorNames);

                boolean agrees = true;
                for (Map.Entry<String, String> e : mine.entrySet()) {
                    String other = theirs.get(e.getKey());
                    if (other != null && !other.equals(e.getValue())) {
                        disagreements.add(source + ": " + e.getKey() + " — tool says " + e.getValue()
                                + ", graded says " + other);
                        agrees = false;
                    }
                }
                mine.forEach((k, v) -> {
                    if (!theirs.containsKey(k)) {
                        onlyTool.merge(v, 1, Integer::sum);
                    }
                });
                theirs.forEach((k, v) -> {
                    if (!mine.containsKey(k)) {
                        onlyGraded.merge(v, 1, Integer::sum);
                    }
                });
                if (agrees && mine.equals(theirs)) {
                    exact++;
                }
            }
        }

        System.out.println("graded-tree validation: " + compared + " files compared, " + exact
                + " exact, " + (compared - exact) + " differing, " + disagreements.size() + " disagreements");
        System.out.println("  bindings only the tool has (by type):");
        onlyTool.forEach((k, v) -> System.out.println("    " + v + "x " + k));
        System.out.println("  bindings only the graded tree has (by type):");
        onlyGraded.forEach((k, v) -> System.out.println("    " + v + "x " + k));

        assertTrue(compared > 80, "only " + compared + " files compared — the harness is not finding the trees");
        assertFalse(exact == 0, "no file matched at all — the tool or the comparison is broken");
        assertTrue(disagreements.isEmpty(),
                "the tool and the graded tree resolve the same name to different types:\n    "
                        + String.join("\n    ", disagreements));
    }

    /**
     * Simple name → the {@code vaadinx} type it resolves to in this file, however that file expresses
     * it: a single-type import, an on-demand {@code vaadinx.x.*} import, or an in-code
     * fully-qualified reference.
     */
    private static Map<String, String> bindings(String source, Set<String> emulatorNames) {
        String code = Lexed.of(source).codeOnly();
        Map<String, String> out = new LinkedHashMap<>();
        List<String> wildcards = new ArrayList<>();

        Matcher imports = IMPORT.matcher(code);
        while (imports.find()) {
            if (imports.group(1) != null) {
                continue;
            }
            String fqn = imports.group(2);
            if (!fqn.startsWith("vaadinx.")) {
                continue;
            }
            if (fqn.endsWith(".*")) {
                wildcards.add(fqn.substring(0, fqn.length() - 2));
            } else {
                out.put(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
            }
        }

        Matcher qualified = QUALIFIED_VAADINX.matcher(code);
        while (qualified.find()) {
            String fqn = qualified.group(1);
            if (emulatorNames.contains(fqn)) {
                out.putIfAbsent(fqn.substring(fqn.lastIndexOf('.') + 1), fqn);
            }
        }

        if (!wildcards.isEmpty()) {
            Matcher names = SIMPLE_NAME.matcher(code);
            while (names.find()) {
                String name = names.group(1);
                if (out.containsKey(name)) {
                    continue;
                }
                for (String pkg : wildcards) {
                    if (emulatorNames.contains(pkg + "." + name)) {
                        out.put(name, pkg + "." + name);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
