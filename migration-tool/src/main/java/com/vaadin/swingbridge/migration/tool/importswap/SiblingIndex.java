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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Which top-level type names each package of the migrated app declares for itself.
 *
 * <pre>{@code
 * SiblingIndex idx = SiblingIndex.scan(List.of(Path.of("src/main/java")));
 * idx.declares("com.acme.ui", "Panel");   // => true — the app has its own Panel
 * }</pre>
 *
 * <p>This closes the one <em>silent</em> failure the emit side has. A type in the same package beats
 * an on-demand import (JLS 6.4.1), so a file that says {@code import java.awt.*;} and uses
 * {@code Panel} means the app's own {@code Panel} — and emitting {@code import vaadinx.awt.Panel;}
 * would redirect every use of it with no diagnostic anywhere. Every other mis-emission the tool could
 * make is a compile error; this one is not, which is why it gets a whole-tree pre-scan.
 *
 * <p>Immutable once scanned.
 *
 * <p>Regex over declarations rather than a parser: the question is only "does this package
 * declare this simple name", and a top-level declaration is the one construct that is
 * reliably at the start of a line in real source.
 */
final class SiblingIndex {

    /** A type declaration, at any nesting — over-collecting is the safe direction here. */
    private static final Pattern DECLARATION =
            Pattern.compile("\\b(?:class|interface|enum|record|@interface)\\s+([A-Z][\\w$]*)");

    private static final Pattern PACKAGE = Pattern.compile("(?m)^[ \t]*package[ \t]+([\\w.]+)[ \t]*;");

    private final Map<String, Set<String>> byPackage = new HashMap<>();

    static SiblingIndex scan(List<Path> roots) throws IOException {
        SiblingIndex index = new SiblingIndex();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    index.add(Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        }
        return index;
    }

    private void add(String source) {
        String code = Lexed.of(source).codeOnly();
        Matcher pkg = PACKAGE.matcher(code);
        String name = pkg.find() ? pkg.group(1) : "";
        Set<String> into = byPackage.computeIfAbsent(name, k -> new HashSet<>());
        Matcher decl = DECLARATION.matcher(code);
        while (decl.find()) {
            into.add(decl.group(1));
        }
    }

    boolean declares(String packageName, String simpleName) {
        return byPackage.getOrDefault(packageName, Set.of()).contains(simpleName);
    }

    int typeCount() {
        return byPackage.values().stream().mapToInt(Set::size).sum();
    }
}
