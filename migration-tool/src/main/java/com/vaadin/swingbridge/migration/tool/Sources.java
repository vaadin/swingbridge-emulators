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

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.stream.Stream;

/**
 * The source-tree walk the tools run: every file under a root matching a glob, build output pruned.
 *
 * <pre>{@code
 * Sources.under(Path.of("src/main/java"), "*.java");
 * Sources.under(Path.of("src/main/resources"), "*.{html,htm}");
 * }</pre>
 *
 * <p>The glob matches the file <em>name</em>, not the path, so {@code *.java} means what a migrator
 * reading the invocation expects it to mean.
 *
 * <p>Lives in {@code com.vaadin.swingbridge.migration.tool} rather than in any tool's package because more than one tool
 * uses it — that is the whole membership rule for this package. A class only one tool uses belongs in that
 * tool's package, however generic it looks: {@code Lexed}, a Java-source lexer, sits in
 * {@code importswap} for exactly that reason.
 *
 * <p>{@code public} only so the tool subpackages can reach it; jar-internal like everything
 * else here, and not part of any surface a migrator calls.
 */
public final class Sources {

    private Sources() {
    }

    /**
     * @param root a directory to walk, or a single file to take as-is
     * @param glob matched against each file name, in {@link FileSystems#getDefault()} glob syntax
     * @return the matching files, sorted; empty when the root does not exist
     */
    public static List<Path> under(Path root, String glob) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
        if (Files.isRegularFile(root)) {
            return matcher.matches(root.getFileName()) ? List.of(root) : List.of();
        }
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .filter(p -> !isBuildOutput(root, p))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The roots that name nothing — neither a file nor a directory — in argument order.
     * {@link #under} reads such a root as empty, which is right for an app that genuinely has no
     * {@code src/main/resources}; this is how a tool tells that apart from a typo and says so.
     */
    public static List<Path> missing(List<Path> roots) {
        return roots.stream().filter(root -> !Files.exists(root)).toList();
    }

    /**
     * Whether Maven's build output is on the path <em>below the root</em>.
     *
     * <p>Two things this gets deliberately right. It compares path <em>elements</em> rather
     * than searching the rendered string for {@code "/target/"}, because a Windows path
     * separates with backslashes and a migrator there would otherwise scan their own
     * build output. And it looks only below the root, not at the whole path: where the
     * root itself sits under a directory called {@code target} — which the distribution
     * kit's own development layout does, since an unpacked kit lives in
     * {@code zip-distro/target/} — pruning the absolute path would prune the entire tree
     * and report "0 files scanned" over an app that is all Swing. The root is the
     * caller's explicit choice; only what the walk *finds* is ours to skip.
     */
    private static boolean isBuildOutput(Path root, Path p) {
        for (Path element : root.relativize(p)) {
            if (element.toString().equals("target")) {
                return true;
            }
        }
        return false;
    }
}
