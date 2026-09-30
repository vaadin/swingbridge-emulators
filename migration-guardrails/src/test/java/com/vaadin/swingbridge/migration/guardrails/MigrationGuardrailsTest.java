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

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.migration.guardrails.fixtures.ComponentFixtures;
import com.vaadin.swingbridge.migration.guardrails.fixtures.ExitFixtures;
import com.vaadin.swingbridge.migration.guardrails.fixtures.StaticStateFixtures;
import com.vaadin.swingbridge.migration.guardrails.fixtures.anonymous.MainWithAnonymousExiter;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule that silently matches nothing looks exactly like a clean app, so every gate is checked
 * against a fixture that must fail it as well as one that must pass.
 */
class MigrationGuardrailsTest {

    private static MigrationGuardrails over(Class<?>... fixtures) {
        return MigrationGuardrails.forImportedClasses(new ClassFileImporter().importClasses(fixtures));
    }

    private static String failureOf(MigrationGuardrails guardrails) {
        return assertThrows(AssertionError.class, guardrails::run).getMessage();
    }

    // --- the tripwire under everything else ------------------------------------------------------

    @Test
    void rootsResolvingNoClassesFailLoudly() {
        // Without this, every rule's allowEmptyShould(true) makes a mistyped root package pass
        // vacuously, forever — and every entry point has to trip it, not only run().
        final List<Consumer<MigrationGuardrails>> entryPoints = List.of(
                MigrationGuardrails::run,
                MigrationGuardrails::checkStaticState,
                MigrationGuardrails::checkStaticComponents,
                MigrationGuardrails::checkJvmExit);
        for (var entryPoint : entryPoints) {
            final var guardrails = new MigrationGuardrails("no.such.pkg", "nor.this.one");
            final var failure = assertThrows(AssertionError.class, () -> entryPoint.accept(guardrails));
            assertTrue(failure.getMessage().contains("no.such.pkg"), failure.getMessage());
            assertTrue(failure.getMessage().contains("nor.this.one"), failure.getMessage());
        }
    }

    @Test
    void thisModulePassesItsOwnGuardrails() {
        // The one test that drives the real importer — package scan plus DO_NOT_INCLUDE_TESTS, which
        // is why it sees this module's src/main and none of the fixtures next door.
        assertDoesNotThrow(() -> new MigrationGuardrails("com.vaadin.swingbridge.migration.guardrails").run());
    }

    // --- static state ----------------------------------------------------------------------------

    @Test
    void flagsANonFinalStaticOfAnImmutableType() {
        assertTrue(failureOf(over(StaticStateFixtures.LoginFlag.class)).contains("is not final"));
    }

    @Test
    void flagsAFinalStaticOfAMutableType() {
        assertTrue(failureOf(over(StaticStateFixtures.SharedCache.class))
                .contains("has mutable type java.util.Map"));
    }

    @Test
    void flagsTheLooseLoggerIdiomAndAcceptsTheFixedOne() {
        // The two halves have to agree: the gate flags `static Logger log = …`, @IntentionallyStatic
        // says to add `final` rather than annotate, and the fixed field passes only because a logging
        // facade counts as an immutable type. Drop the facades and the prescribed fix fails the gate.
        assertTrue(failureOf(over(StaticStateFixtures.LooseLogger.class)).contains("is not final"));
        assertDoesNotThrow(() -> over(StaticStateFixtures.FixedLogger.class).run());
    }

    @Test
    void acceptsEveryCategoryTheAnnotationsJavadocLists() {
        assertDoesNotThrow(() -> over(StaticStateFixtures.JavadocCategories.class).run());
    }

    @Test
    void splitsTheDateEmulatorsTheWayTheGuidesDo() {
        // The formatter half is in JavadocCategories above: it holds no formatting state on the
        // instance, so the shared-static idiom the import swap is meant to fix passes unannotated,
        // as three places in the guides promise. The calendar half must not follow it in — that one
        // only zones its construction, and the migration answer for a shared mutable Calendar is to
        // unshare it. Adding both together is the tempting edit this pins shut.
        assertTrue(failureOf(over(StaticStateFixtures.SharedCalendar.class))
                .contains("has mutable type vaadinx.util.Calendar"));
    }

    @Test
    void acceptsAllowlistedFields() {
        // Also the retention check: @IntentionallyStatic is RetentionPolicy.CLASS, and this passes
        // only if ArchUnit reads it out of the bytecode. Under SOURCE the allowlist would be
        // invisible and the gate would flag every annotated field instead.
        assertDoesNotThrow(() -> over(StaticStateFixtures.Vetted.class).run());
    }

    @Test
    void acceptsAPackageWithNoStaticsAtAll() {
        // The empty-should case: `fields().that().areStatic()` matches nothing here, and a swept app
        // is entitled to look like this.
        assertDoesNotThrow(() -> over(StaticStateFixtures.NoStatics.class).run());
    }

    @Test
    void skipsCompilerSyntheticStatics() {
        // Package rather than named classes, so javac's own $VALUES holder and $SwitchMap$ table come
        // along — neither is declared in the fixture's source, and both would otherwise be flagged.
        final var synthetics = MigrationGuardrails.forImportedClasses(new ClassFileImporter()
                .importPackages("com.vaadin.swingbridge.migration.guardrails.fixtures.synthetics"));
        assertDoesNotThrow(synthetics::run);
    }

    // --- components ------------------------------------------------------------------------------

    @Test
    void flagsAStaticEmulatorComponent() {
        assertThrows(AssertionError.class,
                over(ComponentFixtures.LeakyEmulator.class)::checkStaticComponents);
    }

    @Test
    void flagsAStaticVaadinComponent() {
        // The stage-3/4 half of the by-name match: no vaadinx type in sight.
        assertThrows(AssertionError.class,
                over(ComponentFixtures.LeakyVaadin.class)::checkStaticComponents);
    }

    // --- JVM exit --------------------------------------------------------------------------------

    @Test
    void flagsAnExitOutsideMain() {
        assertTrue(failureOf(over(ExitFixtures.Exiter.class)).contains("System.exit"));
    }

    @Test
    void mainMethodExitPassesOnlyUnderTheRelaxation() {
        assertThrows(AssertionError.class, over(ExitFixtures.MainExiter.class)::checkJvmExit);
        assertDoesNotThrow(() -> over(ExitFixtures.MainExiter.class)
                .allowSystemExitInMainMethods().checkJvmExit());
    }

    @Test
    void aLambdaInsideMainIsNotMain() {
        assertThrows(AssertionError.class, () -> over(ExitFixtures.LambdaInMainExiter.class)
                .allowSystemExitInMainMethods().checkJvmExit());
    }

    @Test
    void allowSystemExitInNamesAnExactClass() {
        final String name = ExitFixtures.AllowlistedClassExiter.class.getName();
        assertThrows(AssertionError.class,
                over(ExitFixtures.AllowlistedClassExiter.class)::checkJvmExit);
        assertDoesNotThrow(() -> over(ExitFixtures.AllowlistedClassExiter.class)
                .allowSystemExitIn(name).checkJvmExit());
        // An anonymous class inside main is neither main nor the class the migrator named: Main$1 is
        // not Main, and both relaxations are on here.
        final var anonymous = MigrationGuardrails.forImportedClasses(new ClassFileImporter()
                        .importPackages("com.vaadin.swingbridge.migration.guardrails.fixtures.anonymous"))
                .allowSystemExitInMainMethods()
                .allowSystemExitIn(MainWithAnonymousExiter.class.getName());
        assertThrows(AssertionError.class, anonymous::checkJvmExit);
    }

    // --- aggregation -----------------------------------------------------------------------------

    @Test
    void runReportsAllThreeGatesAtOnce() {
        // Not short-circuiting is the point: a first-failure throw serialises the migrator's worklist
        // into fix-statics, re-run, *now* discover the System.exit.
        final String report = failureOf(over(StaticStateFixtures.FailsEverything.class));
        assertTrue(report.contains("provably immutable"), report);
        assertTrue(report.contains("no static field may hold"), report);
        assertTrue(report.contains("System.exit"), report);
    }
}
