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

package com.vaadin.swingbridge.migration.guardrails;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.vaadin.swingbridge.migration.IntentionallyStatic;
import com.vaadin.swingbridge.migration.tool.guardrails.GuardrailEngine;

import java.util.List;

/**
 * The three build-time gates a Swing-to-web migration has to pass, as one test:
 *
 * <pre>{@code
 * class MigrationGuardrailsTest {
 *     @Test
 *     void guardrails() {
 *         new MigrationGuardrails("com.myapp")
 *                 .allowSystemExitInMainMethods()
 *                 .run();
 *     }
 * }
 * }</pre>
 *
 * <p>{@code run()} checks all three and reports every violation at once:
 *
 * <ul>
 *   <li><b>No unvetted static state</b> — every {@code static} field is {@code final} <i>and</i> of
 *       an immutable type, or carries {@link IntentionallyStatic} with a reason. On a server a
 *       mutable static is shared by every user, and the failure is silent.</li>
 *   <li><b>No component in a {@code static} field</b> — a component belongs to a UI, not to the JVM.
 *       Catches {@code static MainFrame FRAME} (where {@code MainFrame extends JFrame}) by resolving
 *       the type hierarchy, which a {@code grep 'static .*JFrame'} misses.</li>
 *   <li><b>No JVM exit</b> — {@code System.exit} / {@code Runtime.exit} / {@code Runtime.halt} kill
 *       every user's session, not just the caller's window.</li>
 * </ul>
 *
 * <p>Relaxations name their target, so none of them is an off-switch: {@link
 * #allowSystemExitInMainMethods()} covers the process-level {@code main} and nothing reachable from
 * the UI, {@link #allowSystemExitIn(String...)} names exact classes. The static gate has no
 * relaxation at all — {@link IntentionallyStatic} is already its per-field hatch.
 *
 * <p>Add the module in <b>test scope</b>; ArchUnit is the engine and never reaches your runtime.
 * Nothing here is Swing-specific enough to retire once the emulators are gone: "no component in a
 * static field" and "no JVM exit" stay true of the pure-Vaadin app the migration ends at.
 *
 * <p>{@link IntentionallyStatic}'s javadoc is the canonical definition of the static gate —
 * which types count as immutable, what passes unannotated, and which of the four reasons to
 * claim. This class implements that definition; it does not restate it.
 *
 * <p>A <b>facade</b> over {@link GuardrailEngine} in {@code :migration-tool}, which owns the
 * rules and the ArchUnit dependency so that {@code StaticSweep}'s worklist and this gate cannot
 * answer the same question two ways (M1D_static_sweep_tool § {@code Q_one_rule_engine}). The
 * facade is what the migrator sees, and it is kept rather than folded away because it is what
 * makes this artifact's promises true: one SB-Emulators type imported, no {@code com.tngtech.*}, test
 * scope by consumption. The division of labour is the <i>terminal</i> — the engine returns the
 * violations and judges nothing, {@link #run()} throws on them, and the tool's `main` prints
 * them and exits 0.
 * <p>Root packages are required and never defaulted: absent one the importer scans the whole
 * classpath and reports mutable statics inside Vaadin and the JDK, and a gate that noisy gets
 * deleted. For the same reason an import resolving <i>no</i> classes fails loudly rather than
 * passing vacuously — the three rules all allow an empty {@code should()} (a fully swept app can
 * have zero statics), so a mistyped root package would otherwise pass forever.
 */
public final class MigrationGuardrails {

    private final GuardrailEngine engine;

    /**
     * @param rootPackage the app's own root package, e.g. {@code "com.myapp"}
     * @param moreRootPackages further roots — an in-tree library, a fixture package. Leaving one out
     *     makes the rules pass by not looking at it; naming one wider than you own (a bare
     *     {@code "SB-Emulators"} over a {@code com.vaadin.swingbridge.fixture} package) sweeps somebody else's classes in.
     */
    public MigrationGuardrails(String rootPackage, String... moreRootPackages) {
        final String[] roots = new String[moreRootPackages.length + 1];
        roots[0] = rootPackage;
        System.arraycopy(moreRootPackages, 0, roots, 1, moreRootPackages.length);
        this.engine = new GuardrailEngine(roots);
    }

    private MigrationGuardrails(GuardrailEngine engine) {
        this.engine = engine;
    }

    /**
     * Test seam: runs the rules over classes imported by the caller, bypassing the package scan (and
     * with it {@code DO_NOT_INCLUDE_TESTS}, which would skip a fixture living in test output).
     */
    static MigrationGuardrails forImportedClasses(JavaClasses classes) {
        return new MigrationGuardrails(GuardrailEngine.forImportedClasses(classes));
    }

    /**
     * Permits {@code System.exit} in the body of a {@code public static void main(String[])} and
     * nowhere else.
     *
     * <p>A lambda inside {@code main} compiles to a synthetic method and is <b>not</b> covered —
     * deliberately, since {@code invokeLater(() -> … System.exit(0))} is exactly the UI-reachable
     * exit the gate exists to catch.
     */
    public MigrationGuardrails allowSystemExitInMainMethods() {
        engine.allowSystemExitInMainMethods();
        return this;
    }

    /**
     * Permits JVM exit anywhere in the named classes.
     *
     * @param fullyQualifiedClassNames matched exactly — {@code "com.myapp.Main"} does not cover
     *     {@code com.myapp.Main$1}, so an anonymous class stays gated
     */
    public MigrationGuardrails allowSystemExitIn(String... fullyQualifiedClassNames) {
        engine.allowSystemExitIn(fullyQualifiedClassNames);
        return this;
    }

    /**
     * Runs all three checks, collects every violation, and throws one {@link AssertionError} with
     * the combined report.
     *
     * <p>Never short-circuits: a first-failure throw would serialise the worklist into fix-statics,
     * re-run, <i>now</i> discover the {@code System.exit}.
     *
     * @throws AssertionError if any check found a violation, or the roots resolved no classes
     */
    public void run() {
        final List<String> violations = engine.violations();
        if (!violations.isEmpty()) {
            throw new AssertionError(String.join("\n\n", violations));
        }
    }

    /** @throws AssertionError if a {@code static} field is neither provably immutable nor allowlisted */
    public void checkStaticState() {
        engine.checkStaticState();
    }

    /** @throws AssertionError if a {@code static} field's type is a component */
    public void checkStaticComponents() {
        engine.checkStaticComponents();
    }

    /** @throws AssertionError if any code unit terminates the JVM outside the permitted places */
    public void checkJvmExit() {
        engine.checkJvmExit();
    }
}
