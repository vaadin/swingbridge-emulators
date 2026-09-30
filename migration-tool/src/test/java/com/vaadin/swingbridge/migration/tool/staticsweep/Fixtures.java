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

import com.vaadin.swingbridge.migration.IntentionallyStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * The three little trees the sweep is tested against, compiled by {@code javac} at test time.
 *
 * <pre>{@code
 * Fixtures.Trees trees = Fixtures.compile(tempDir);
 * ClassTree.of(List.of(trees.stage1()));               // the app before migration
 * Resolver.over(List.of(trees.stage1()), …, …);         // trees.lib() withheld → UNRESOLVED
 * }</pre>
 *
 * <p><b>Compiled rather than checked in, for two reasons a source root cannot satisfy.</b> The
 * {@linkplain Resolver.Kind#UNRESOLVED unresolved} verdict needs a supertype that is genuinely
 * missing, so {@code Foreign} gets a tree of its own and is then withheld; and {@code --diff}
 * matches rows by fully-qualified name, so the before-and-after trees must be the <i>same</i>
 * package in two directories. Everything is {@code --release 21}, older than this module's own,
 * because a swept app's class files are whatever the migrator's build emits.
 */
final class Fixtures {

    /** {@code Foreign} lives here and nowhere else, so withholding this tree withholds the type. */
    private static final String FOREIGN = """
            package com.acme.lib;

            public class Foreign extends javax.swing.JPanel {
            }
            """;

    /**
     * The class every field-level assertion is about. Kept as one readable declaration list, whose
     * line numbers the test looks up with {@link #stage1Line}.
     */
    private static final String HOLDER = """
            package com.acme.app;

            import com.acme.lib.Foreign;
            import com.vaadin.swingbridge.migration.IntentionallyStatic;

            import java.util.HashMap;
            import java.util.Map;

            import javax.swing.JPanel;

            public class Holder {

                static MainFrame frame;
                static Map<String, JPanel> panels = new HashMap<>();
                @SuppressWarnings("rawtypes")
                static Map cache = new HashMap();
                static boolean loggedIn;
                static Orphan orphan;
                static Foreign foreign;
                static final String NAME = "fixture";
                static final Mode MODE = Mode.OPEN;
                static final Pair PAIR = new Pair("a", "b");
                static final Map<String, String> POOL = new HashMap<>();
                @IntentionallyStatic(IntentionallyStatic.Reason.JVM_INFRASTRUCTURE)
                static Map<String, String> pool = new HashMap<>();

                static {
                    try {
                        panels.put("main", new JPanel());
                    } catch (RuntimeException e) {
                        cache.clear();
                    }
                }

                static void arm() {
                    Runnable armed = () -> loggedIn = true;
                    armed.run();
                }

                static void reset() {
                    cache.clear();
                }

                static int twice(int value) {
                    return value * 2;
                }

                static class Nested {
                }
            }
            """;

    private static final Map<String, String> STAGE_1 = new LinkedHashMap<>();

    static {
        STAGE_1.put("com/acme/app/AppFrame.java", """
                package com.acme.app;

                public class AppFrame extends javax.swing.JFrame {
                }
                """);
        STAGE_1.put("com/acme/app/MainFrame.java", """
                package com.acme.app;

                public class MainFrame extends AppFrame {
                }
                """);
        STAGE_1.put("com/acme/app/Orphan.java", """
                package com.acme.app;

                public class Orphan extends com.acme.lib.Foreign {
                }
                """);
        STAGE_1.put("com/acme/app/Mode.java", """
                package com.acme.app;

                public enum Mode {
                    OPEN, CLOSED
                }
                """);
        STAGE_1.put("com/acme/app/Pair.java", """
                package com.acme.app;

                public record Pair(String left, String right) {
                }
                """);
        STAGE_1.put("com/acme/app/Switcher.java", """
                package com.acme.app;

                public class Switcher {

                    public static int rank(Mode mode) {
                        switch (mode) {
                            case OPEN:
                                return 1;
                            default:
                                return 0;
                        }
                    }
                }
                """);
        STAGE_1.put("com/acme/app/Holder.java", HOLDER);
    }

    /** The same package, migrated: some rows routed, some settled, one grown. */
    private static final Map<String, String> STAGE_2 = Map.of("com/acme/app/Holder.java", """
            package com.acme.app;

            import com.vaadin.swingbridge.migration.IntentionallyStatic;

            import java.util.HashMap;
            import java.util.Map;

            import javax.swing.JPanel;

            public class Holder {

                static Map<String, JPanel> panels = new HashMap<>();
                @IntentionallyStatic(IntentionallyStatic.Reason.WORLD_GLOBAL_READ_MOSTLY)
                static Map cache = new HashMap();
                static final boolean loggedIn = false;
                static Map<String, String> extra = new HashMap<>();
            }
            """);

    /**
     * @param lib the withheld tree, {@code Foreign} only
     * @param libJar the same tree as a jar, for {@code --lib}
     * @param stage1 the app before migration
     * @param stage2 the app after it
     */
    record Trees(Path lib, Path libJar, Path stage1, Path stage2) {
    }

    private Fixtures() {
    }

    static Trees compile(Path root) throws IOException {
        Path lib = compile(root, "lib", Map.of("com/acme/lib/Foreign.java", FOREIGN), List.of());
        Path stage1 = compile(root, "stage1", STAGE_1, List.of(lib));
        Path stage2 = compile(root, "stage2", STAGE_2, List.of());
        return new Trees(lib, jar(root.resolve("libs").resolve("foreign.jar"), lib), stage1, stage2);
    }

    /** The 1-based line in {@code Holder.java} whose text contains {@code snippet}. */
    static int stage1Line(String snippet) {
        String[] lines = HOLDER.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(snippet)) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException("no line of Holder.java contains " + snippet);
    }

    private static Path compile(Path root, String name, Map<String, String> sources,
            List<Path> classpath) throws IOException {
        Path sourceDir = root.resolve("src-" + name);
        Path classesDir = root.resolve(name);
        Files.createDirectories(classesDir);
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = sourceDir.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue(), StandardCharsets.UTF_8);
            files.add(file.toString());
        }

        List<String> args = new ArrayList<>(List.of("--release", "21", "-d", classesDir.toString(),
                "-classpath", cp(classpath)));
        args.addAll(files);

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("no system Java compiler — the tests need a JDK, not a JRE");
        }
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int status = javac.run(null, null, diagnostics, args.toArray(new String[0]));
        if (status != 0) {
            throw new IllegalStateException("the " + name + " fixture did not compile:\n"
                    + diagnostics.toString(StandardCharsets.UTF_8));
        }
        return classesDir;
    }

    /**
     * The annotation jar comes from the loaded class rather than from
     * {@code java.class.path}, which under Surefire is often a booter jar whose real entries are
     * in a manifest.
     */
    private static String cp(List<Path> extra) {
        List<String> entries = new ArrayList<>();
        try {
            entries.add(Path.of(IntentionallyStatic.class.getProtectionDomain().getCodeSource()
                    .getLocation().toURI()).toString());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the migration-annotations artifact", e);
        }
        extra.forEach(p -> entries.add(p.toString()));
        return String.join(java.io.File.pathSeparator, entries);
    }

    private static Path jar(Path jarPath, Path classesDir) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarPath));
                Stream<Path> walk = Files.walk(classesDir)) {
            for (Path file : walk.filter(Files::isRegularFile).toList()) {
                out.putNextEntry(new JarEntry(
                        classesDir.relativize(file).toString().replace('\\', '/')));
                out.write(Files.readAllBytes(file));
                out.closeEntry();
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return jarPath;
    }
}
