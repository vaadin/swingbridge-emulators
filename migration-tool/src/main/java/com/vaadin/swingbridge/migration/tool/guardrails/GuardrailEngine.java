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

package com.vaadin.swingbridge.migration.tool.guardrails;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.vaadin.swingbridge.migration.IntentionallyStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * The three migration guardrails, as rules over imported classes: no unvetted {@code static} state,
 * no component in a {@code static} field, no JVM exit.
 *
 * <p><b>This is the implementation, not the migrator's entry point.</b> A migrated app calls
 * {@code com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails}, a facade over this class in a test-scope
 * artifact, so the migrator imports one SB-Emulators type and no {@code com.tngtech.*}
 * (M1D_guardrails_artifact). The engine lives here because {@code StaticSweep} asks the same
 * immutability question for the opposite purpose — a worklist during the migration rather than a gate
 * after it — and answering it twice is what makes a rule drift
 * (M1D_static_sweep_tool § {@code Q_one_rule_engine}). The shared half is {@link ImmutableTypes}.
 *
 * <p><b>The two terminals do not collapse into each other.</b> {@link #violations} returns the
 * report and judges nothing, which is what a `main` printing a worklist needs — mid-sweep a non-empty
 * list is the expected state, not a failure. The facade's {@code run()} throws on the same list,
 * which is what makes the sweep <i>stay</i> done once a {@code static} field is added months later.
 *
 * <p>This class never depends on {@code :emulators}, {@code :surrogates} or anything Vaadin: the
 * component types are matched <b>by name</b>, so a migrated app's UI-free service module can install
 * the gate without a Vaadin dependency flipping its Step-0 classification, and there the names
 * resolve to nothing and the rule trivially passes.
 */
public final class GuardrailEngine {

    /**
     * Matched by name rather than by class, so neither this module nor the facade ever depends on
     * {@code :emulators}. See the class javadoc.
     */
    private static final String EMULATOR_COMPONENT = "vaadinx.awt.Component";

    private static final String VAADIN_COMPONENT = "com.vaadin.flow.component.Component";

    private final String rootDescription;
    private final Supplier<JavaClasses> source;
    private final Set<String> systemExitAllowedIn = new LinkedHashSet<>();
    private boolean systemExitAllowedInMain;
    private JavaClasses imported;

    /**
     * @param rootPackages the app's own roots. Leaving one out makes the rules pass by not looking at
     *     it; naming one wider than you own sweeps somebody else's classes into your result.
     */
    public GuardrailEngine(String... rootPackages) {
        this.rootDescription = String.join(", ", rootPackages);
        this.source = () -> new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(rootPackages);
    }

    private GuardrailEngine(String rootDescription, Supplier<JavaClasses> source) {
        this.rootDescription = rootDescription;
        this.source = source;
    }

    /**
     * Runs the rules over classes the caller imported, bypassing the package scan — and with it
     * {@code DO_NOT_INCLUDE_TESTS}, which would skip a fixture living in test output.
     */
    public static GuardrailEngine forImportedClasses(JavaClasses classes) {
        return new GuardrailEngine("the supplied classes", () -> classes);
    }

    /**
     * Permits {@code System.exit} in the body of a {@code public static void main(String[])} and
     * nowhere else. A lambda inside {@code main} is <b>not</b> covered — deliberately, since
     * {@code invokeLater(() -> … System.exit(0))} is exactly the UI-reachable exit the gate exists
     * to catch.
     */
    public GuardrailEngine allowSystemExitInMainMethods() {
        systemExitAllowedInMain = true;
        return this;
    }

    /**
     * Permits JVM exit anywhere in the named classes.
     *
     * @param fullyQualifiedClassNames matched exactly — {@code "com.myapp.Main"} does not cover
     *     {@code com.myapp.Main$1}, so an anonymous class stays gated
     */
    public GuardrailEngine allowSystemExitIn(String... fullyQualifiedClassNames) {
        systemExitAllowedIn.addAll(Arrays.asList(fullyQualifiedClassNames));
        return this;
    }

    /**
     * Every violation of all three rules, each entry a rendered failure report.
     *
     * <p>Never short-circuits: stopping at the first would serialise the worklist into fix-statics,
     * re-run, <i>now</i> discover the {@code System.exit}.
     *
     * @return empty when the app passes. **Judges nothing** — a caller printing a worklist wants the
     *     list, a caller gating a build throws on it.
     * @throws AssertionError only if the roots resolved no classes at all, which is a mistyped root
     *     rather than a finding
     */
    public List<String> violations() {
        final JavaClasses classes = importedClasses();
        final List<String> reports = new ArrayList<>();
        for (ArchRule rule : List.of(staticStateRule(), staticComponentsRule(), jvmExitRule())) {
            final EvaluationResult result = rule.evaluate(classes);
            if (result.hasViolation()) {
                reports.add(result.getFailureReport().toString());
            }
        }
        return reports;
    }

    /** @throws AssertionError if a {@code static} field is neither provably immutable nor allowlisted */
    public void checkStaticState() {
        staticStateRule().check(importedClasses());
    }

    /** @throws AssertionError if a {@code static} field's type is a component */
    public void checkStaticComponents() {
        staticComponentsRule().check(importedClasses());
    }

    /** @throws AssertionError if any code unit terminates the JVM outside the permitted places */
    public void checkJvmExit() {
        jvmExitRule().check(importedClasses());
    }

    private JavaClasses importedClasses() {
        if (imported == null) {
            imported = source.get();
        }
        if (imported.isEmpty()) {
            throw new AssertionError("MigrationGuardrails resolved no classes from " + rootDescription
                    + " — check the spelling, and that the app's main classes are on the test classpath");
        }
        return imported;
    }

    private ArchRule staticStateRule() {
        return fields().that().areStatic()
                .should(beProvablyImmutableOrAllowlisted())
                .as("every static field must be provably immutable (final, of an immutable type) "
                        + "or carry @IntentionallyStatic with a reason")
                .because("on a server a mutable static is shared by every user, and the failure is "
                        + "silent — it compiles, boots, and leaks cross-user under load")
                .allowEmptyShould(true);
    }

    private ArchRule staticComponentsRule() {
        final DescribedPredicate<JavaClass> component =
                assignableTo(EMULATOR_COMPONENT).or(assignableTo(VAADIN_COMPONENT));
        return noFields().that().areStatic()
                .should().haveRawType(component)
                .as("no static field may hold a " + EMULATOR_COMPONENT + " or a " + VAADIN_COMPONENT)
                .because("a component belongs to a UI, not to the JVM — a static reference leaks it "
                        + "across every user; resolve it at use time via UI.getCurrent()")
                .allowEmptyShould(true);
    }

    private ArchRule jvmExitRule() {
        return classes()
                .should(notTerminateTheJvm())
                .as("no class may call System.exit / Runtime.exit / Runtime.halt")
                .because("on a server this kills the JVM and every user's session — close the frame "
                        + "(dispose()) or the session (UI.getCurrent().getSession().close()) instead")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaField> beProvablyImmutableOrAllowlisted() {
        return new ArchCondition<>("be provably immutable, or carry @IntentionallyStatic") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                // Compiler-generated statics are not the app's state: an enum's $VALUES array and the
                // $SwitchMap$ table a switch-over-enum emits are both static, both of a mutable array
                // type, and neither is anything a migrator can route.
                if (field.getModifiers().contains(JavaModifier.SYNTHETIC)
                        || field.isAnnotatedWith(IntentionallyStatic.class)) {
                    return;
                }
                final boolean isFinal = field.getModifiers().contains(JavaModifier.FINAL);
                final JavaClass type = field.getRawType();
                final boolean immutableType = ImmutableTypes.isImmutable(
                        type.getName(), type.isPrimitive(), type.isEnum(), type.isRecord());
                // Both halves: `final` governs whether the reference can change, the type whether the
                // referent can. A type-only test waves `public static boolean isLoggedIn` through.
                if (isFinal && immutableType) {
                    return;
                }
                final String why = !isFinal && !immutableType
                        ? "is neither final nor of an immutable type"
                        : !isFinal ? "is not final"
                        : "has mutable type " + type.getName();
                events.add(SimpleConditionEvent.violated(field,
                        "static field " + field.getFullName() + " " + why
                                + " and is not allowlisted with @IntentionallyStatic"));
            }
        };
    }

    /**
     * Written as a custom condition rather than {@code noClasses().should().callMethod(…)} so the
     * <i>origin</i> code unit is in hand — which is what both relaxations are addressed to.
     */
    private ArchCondition<JavaClass> notTerminateTheJvm() {
        return new ArchCondition<>("not call System.exit / Runtime.exit / Runtime.halt") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    if (!isJvmExit(call) || isPermitted(call)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(call, call.getDescription()
                            + " — this kills the JVM and every user's session"));
                }
            }
        };
    }

    private static boolean isJvmExit(JavaMethodCall call) {
        final String owner = call.getTargetOwner().getName();
        final String method = call.getName();
        return ("java.lang.System".equals(owner) && "exit".equals(method))
                || ("java.lang.Runtime".equals(owner)
                        && ("exit".equals(method) || "halt".equals(method)));
    }

    /**
     * The whole class is allowlisted, lambdas included; the {@code main} relaxation is not, and
     * ArchUnit is why the lambda needs saying twice. It reports an access declared in a lambda
     * against the <i>enclosing</i> method, so a {@code System.exit} in a lambda inside {@code main}
     * arrives here with {@code main} as its origin and would pass without the
     * {@link JavaMethodCall#isDeclaredInLambda()} test.
     */
    private boolean isPermitted(JavaMethodCall call) {
        if (systemExitAllowedIn.contains(call.getOrigin().getOwner().getName())) {
            return true;
        }
        return systemExitAllowedInMain
                && !call.isDeclaredInLambda()
                && isMainMethod(call.getOrigin());
    }

    private static boolean isMainMethod(JavaCodeUnit origin) {
        return origin.isMethod()
                && "main".equals(origin.getName())
                && origin.getModifiers().contains(JavaModifier.PUBLIC)
                && origin.getModifiers().contains(JavaModifier.STATIC)
                && "void".equals(origin.getRawReturnType().getName())
                && origin.getRawParameterTypes().size() == 1
                && isStringArray(origin.getRawParameterTypes().get(0));
    }

    /** ArchUnit names an array type in JVM form ({@code [Ljava.lang.String;}), so match structurally. */
    private static boolean isStringArray(JavaClass type) {
        return type.isArray() && "java.lang.String".equals(type.getBaseComponentType().getName());
    }
}
