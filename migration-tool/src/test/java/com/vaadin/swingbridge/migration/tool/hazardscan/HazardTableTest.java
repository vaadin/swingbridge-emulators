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

package com.vaadin.swingbridge.migration.tool.hazardscan;

import org.junit.jupiter.api.BeforeAll;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Joins the hazard table against spec.md §7 / §5 by {@code H_} slug, in both directions.
 *
 * <p>This is the drift backstop the retired {@code hazard-scan.sh} apologised for not having. Its
 * patterns and the spec's {@code Detect:} lines were two hand-maintained copies with no check
 * between them, and they had already diverged — the script carried the richer {@code System.exit}
 * pattern, and the spec's timezone bullet carried no {@code Detect:} at all while the scan ran three
 * greps for it.
 *
 * <p><b>By slug, not by prose.</b> Word-matching a section label against a bold heading was the
 * first design and is too fuzzy to trust: a reworded label passes or fails on which words survived.
 * A slug carried in two places — an {@code <a id="H_…"></a>} on the bullet, a first column in the
 * tsv — is exact, survives rewording, and reddens on a typo. Same trade {@code DecisionIdTest}
 * makes for {@code D_*} / {@code SD_*} / {@code R_*}.
 */
class HazardTableTest {

    private static final Path SPEC = Path.of("../migration/1-swing-to-emulators/spec.md");

    /** A §7 / §5 bullet that carries a hazard anchor. One bullet is one line in spec.md. */
    private static final Pattern ANCHORED_BULLET =
            Pattern.compile("(?m)^- <a id=\"(H_\\w+)\"></a>(.*)$");

    private static Hazards table;

    @BeforeAll
    static void loadTable() throws IOException {
        // No --table: the classpath copy is the one a migrator gets, and an absent one is the
        // failure mode that kept both add-on swap tables out of their jars (M1D_import_swap_tool).
        table = Hazards.load(List.of());
    }

    @Test
    @DisplayName("every pattern in the table compiles")
    void everyPatternCompiles() {
        // Hazards.load compiles each regex as it parses, so reaching here is the assertion; what is
        // left to check is that it found something to compile. The one thing between an ERE-ism
        // left over from the shell script and a PatternSyntaxException on a migrator's machine.
        assertTrue(table.rows().size() > 15, "table looks empty: " + table.rows().size() + " rows");
        assertTrue(table.rows().stream().anyMatch(h -> !h.manual()), "no scanned rows at all");
        assertTrue(table.rows().stream().anyMatch(Hazards.Hazard::manual), "no manual rows at all");
    }

    @Test
    @DisplayName("every table row names a hazard spec.md still defines")
    void everyRowResolvesToASpecBullet() throws IOException {
        Map<String, String> bullets = specBullets();
        List<String> dangling = table.rows().stream()
                .map(Hazards.Hazard::id)
                .distinct()
                .filter(id -> !bullets.containsKey(id))
                .toList();
        if (!dangling.isEmpty()) {
            fail("hazards.tsv rows name hazards with no <a id=\"…\"></a> anchor in " + SPEC + ": "
                    + dangling + "\n\nEither the spec bullet was renamed — fix the row's id — or the "
                    + "hazard was retired, in which case delete the row and say why in a # comment "
                    + "above where it was.");
        }
    }

    @Test
    @DisplayName("a bullet whose Detect: names a grep has a scanned row, and one that does not has none")
    void everyDetectGrepHasARowAndViceVersa() throws IOException {
        Map<String, String> bullets = specBullets();
        Set<String> scanned = new LinkedHashSet<>();
        Set<String> manual = new LinkedHashSet<>();
        table.rows().forEach(h -> (h.manual() ? manual : scanned).add(h.id()));

        List<String> problems = new ArrayList<>();
        bullets.forEach((id, text) -> {
            boolean claimsGrep = claimsAGrep(text);
            if (claimsGrep && !scanned.contains(id)) {
                problems.add(id + " — its *Detect:* names a grep, but no scanned row implements it");
            }
            if (!claimsGrep && scanned.contains(id)) {
                problems.add(id + " — a scanned row exists, but the bullet's *Detect:* does not name a "
                        + "grep; state the pattern in the spec or make the row manual");
            }
            if (!claimsGrep && !scanned.contains(id) && !manual.contains(id)) {
                problems.add(id + " — anchored in the spec but absent from the table entirely; a "
                        + "hazard with no scan signal still gets a `manual` row so it cannot fall "
                        + "out of the report");
            }
        });
        if (!problems.isEmpty()) {
            fail("hazards.tsv and " + SPEC + " disagree:\n  " + String.join("\n  ", problems));
        }
    }

    @Test
    @DisplayName("no hazard id is anchored twice")
    void noHazardIdIsAnchoredTwice() throws IOException {
        String spec = Files.readString(SPEC, StandardCharsets.UTF_8);
        Map<String, Integer> seen = new LinkedHashMap<>();
        Matcher m = Pattern.compile("<a id=\"(H_\\w+)\"></a>").matcher(spec);
        while (m.find()) {
            seen.merge(m.group(1), 1, Integer::sum);
        }
        List<String> dupes = seen.entrySet().stream().filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey).toList();
        assertEquals(List.of(), dupes, "hazard ids anchored more than once in " + SPEC);
    }

    /** Hazard id → the whole text of its spec.md bullet. */
    private static Map<String, String> specBullets() throws IOException {
        assumeTrue(Files.exists(SPEC), SPEC + " not reachable from this working directory");
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ANCHORED_BULLET.matcher(Files.readString(SPEC, StandardCharsets.UTF_8));
        while (m.find()) {
            out.put(m.group(1), m.group(2));
        }
        assertTrue(out.size() > 15, "only " + out.size() + " anchored bullets found — is the scan broken?");
        return out;
    }

    /**
     * Whether a bullet promises a source-scan signal.
     *
     * <p>Deliberately literal: the bullet has to say {@code grep} inside its {@code Detect:} clause,
     * which ends where the next italic tag ({@code *Fix:*}, {@code *LLM:*}) begins. "only surfaces
     * at runtime" and "check that recipe step 4 is wired" are {@code Detect:} lines too, and they
     * mean the opposite — and a {@code grep} mentioned in a bullet's *Fix* prose is not a claim that
     * the hazard has a detection pattern.
     */
    private static boolean claimsAGrep(String bulletText) {
        int detect = bulletText.indexOf("*Detect");
        if (detect < 0) {
            return false;
        }
        int end = bulletText.length();
        for (String next : List.of("*Fix", "*LLM")) {
            int at = bulletText.indexOf(next, detect + 1);
            if (at >= 0) {
                end = Math.min(end, at);
            }
        }
        return bulletText.substring(detect, end).contains("grep");
    }
}
