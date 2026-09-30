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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code AGENTS.md} is a byte-identical copy of {@code CLAUDE.md}, kept for the tools that read
 * the former name. A copy rather than a symlink, because a default Git-for-Windows checkout turns
 * a symlink into a one-line text file holding the link target; and a copy rather than a pointer,
 * because such a tool loads the file it finds as the whole brief. The cost of a copy is drift,
 * which this test turns into a build failure that names the first differing line.
 */
class AgentsBriefTest {

    private static final Path REPO_ROOT = findRepoRoot();

    private static Path findRepoRoot() {
        for (File dir = new File(".").getAbsoluteFile(); dir != null; dir = dir.getParentFile()) {
            if (new File(dir, "DECISION-ID-MAP.md").isFile()) {
                return dir.toPath();
            }
        }
        throw new IllegalStateException("cannot locate the repo root — no DECISION-ID-MAP.md in any parent of "
                + new File(".").getAbsolutePath());
    }

    @Test
    @DisplayName("AGENTS.md is a regular file, not a symlink")
    void agentsFileIsARegularFile() {
        assertFalse(Files.isSymbolicLink(REPO_ROOT.resolve("AGENTS.md")),
                "AGENTS.md must be a real file: a default Git-for-Windows checkout turns a symlink"
                        + " into a one-line text file holding the link target");
    }

    @Test
    @DisplayName("AGENTS.md is byte-identical to CLAUDE.md")
    void agentsFileMatchesClaudeFile() throws IOException {
        List<String> claude = Files.readAllLines(REPO_ROOT.resolve("CLAUDE.md"), StandardCharsets.UTF_8);
        List<String> agents = Files.readAllLines(REPO_ROOT.resolve("AGENTS.md"), StandardCharsets.UTF_8);
        int lines = Math.min(claude.size(), agents.size());
        int firstDifference = lines + 1;
        for (int i = 0; i < lines; i++) {
            if (!claude.get(i).equals(agents.get(i))) {
                firstDifference = i + 1;
                break;
            }
        }
        boolean identical = claude.equals(agents);
        assertTrue(identical, "AGENTS.md has drifted from CLAUDE.md, first at line " + firstDifference
                + ". CLAUDE.md is the copy to edit; then `cp CLAUDE.md AGENTS.md` and commit both.");
    }
}
