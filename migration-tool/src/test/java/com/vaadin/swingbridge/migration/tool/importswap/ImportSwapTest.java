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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tool's entry point over paths that name nothing; the rewrite itself is {@link SwapperTest}'s. */
class ImportSwapTest {

    /** A one-row table, so the test needs no emulators jar on its classpath. */
    private static Path table(Path dir) throws IOException {
        Path tsv = dir.resolve("ported-types.tsv");
        Files.writeString(tsv, "javax.swing.JButton\tvaadinx.swing.JButton\n", StandardCharsets.UTF_8);
        return tsv;
    }

    @Test
    @DisplayName("a root that does not exist is named in the report, and the rest is still swapped")
    void namesAMissingRoot(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "import javax.swing.JButton;\nclass App {}\n",
                StandardCharsets.UTF_8);
        Path typo = dir.resolve("scr");
        Path report = dir.resolve("import-swap.md");

        assertEquals(0, ImportSwap.run(new String[]{src.toString(), typo.toString(),
                "--table", table(dir).toString(), "--report", report.toString()}));
        String out = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(out.contains("**not found, nothing rewritten there:** [`" + typo + "`]"), out);
        assertTrue(out.contains("- files rewritten: 1"), out);
    }

    @Test
    @DisplayName("every root missing is a refusal — \"rewritten: nothing\" reads as an already-swapped app")
    void refusesWhenNoRootExists(@TempDir Path dir) throws IOException {
        Path report = dir.resolve("import-swap.md");
        assertEquals(2, ImportSwap.run(new String[]{dir.resolve("scr").toString(),
                "--table", table(dir).toString(), "--report", report.toString()}));
        assertFalse(Files.exists(report));
    }
}
