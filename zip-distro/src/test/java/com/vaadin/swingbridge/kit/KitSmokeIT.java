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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the kit's migration tool the way the guides tell a migrator to run it: the shipped
 * {@code tools/bin/import-swap} launcher, over the shipped example app.
 *
 * <p>What this proves that {@link KitLayoutIT} cannot. First, that <b>the swap table the tool
 * sees is the unioned one</b> — core's rows out of the emulators jar plus one add-on table
 * each, all three from {@code tools/lib/} — which is the whole reason those jars are in the
 * kit; a layout check can see the files, only a run can see them being read. Second, that a
 * <b>shipped script actually runs on this OS</b>: the launcher and its wildcard classpath are
 * the kit's only executable surface. {@code :migration-tool}'s own {@code DistZipIT} covers
 * the {@code .cmd} twin on Windows and the loud failure when no table is present.
 *
 * <p>In the default build, and cheap: a JVM start against jars the same {@code install} put
 * in place. It needs no network and no Maven, which is the property the kit's README now
 * promises its readers.
 */
class KitSmokeIT {

    private static final Path KIT = Path.of(System.getProperty("kit.root"));

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    @Test
    @DisplayName("the shipped tool rewrites the shipped example app's imports off a unioned table")
    void theShippedToolRewritesTheShippedExampleApps() throws Exception {
        Path work = KIT.getParent().resolve("smoke/crud");
        copyTree(KIT.resolve("testapps/crud/swing/src"), work.resolve("src"));
        Path report = work.resolve("import-swap.md");

        String output = run(work.resolve("src/main/java").toString(), "--dry-run", "--report", report.toString());

        assertTrue(Files.isRegularFile(report),
                "the tool wrote no report. Its own output was:\n" + output);
        String md = Files.readString(report);

        // The provenance line names each table source with its row count. Three sources is the
        // assertion that matters: the kit ships three table-carrying jars, and a tools/lib that
        // lost one would still rewrite core types and silently leave every add-on type behind.
        String tableLine = md.lines().filter(it -> it.startsWith("- swap table:")).findFirst()
                .orElseThrow(() -> new AssertionError("the report has no swap-table line:\n" + md));
        int sources = tableLine.split(" rows\\)", -1).length - 1;
        assertEquals(3, sources,
                "the swap table came from " + sources + " source(s), not the three jars in"
                        + " tools/lib. The report's header said:\n" + header(md));
        assertTrue(md.contains("swingbridge-emulators"), "no emulators table in:\n" + header(md));
        assertTrue(md.contains("jcalendar"), "no jcalendar table in:\n" + header(md));
        assertTrue(md.contains("jgoodies-forms"), "no jgoodies-forms table in:\n" + header(md));

        assertTrue(md.contains("javax.swing") || md.contains("vaadinx.swing"),
                "the report names no Swing type, so nothing was matched in an app that is all"
                        + " Swing. The report said:\n" + md);
    }

    /** Runs {@code tools/bin/import-swap} through the shell, so the shipped script is what executes. */
    private static String run(String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        if (WINDOWS) {
            command.add("cmd");
            command.add("/c");
            command.add(KIT.resolve("tools/bin/import-swap.cmd").toString());
        } else {
            command.add("sh");
            command.add(KIT.resolve("tools/bin/import-swap").toString());
        }
        command.addAll(List.of(args));

        Process p = new ProcessBuilder(command)
                .directory(KIT.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(5, TimeUnit.MINUTES), "timed out: " + String.join(" ", command));
        assertEquals(0, p.exitValue(),
                "the kit's own tool invocation failed — this is the command the guide prints:\n  "
                        + String.join(" ", command) + "\n\n" + output);
        return output;
    }

    /** The report's header block, which is where every assertion here reads its evidence. */
    private static String header(String markdown) {
        return markdown.lines().takeWhile(it -> !it.startsWith("##")).reduce("", (a, b) -> a + b + "\n");
    }

    private static void copyTree(Path from, Path to) throws IOException {
        if (Files.exists(to)) {
            try (var walk = Files.walk(to)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        try (var walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path target = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target);
                }
            }
        }
        assertTrue(Files.exists(to), "nothing copied from " + from);
        assertTrue(!List.of(to.toFile().list()).isEmpty(), "copied an empty tree from " + from);
    }
}
