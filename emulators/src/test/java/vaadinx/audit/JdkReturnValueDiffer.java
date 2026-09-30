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

package vaadinx.audit;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import vaadinx.AppTab;
import vaadinx.MockVirtualThreadAwareServlet;

/**
 * Runs a real JDK and SB-Emulators through the same set-then-read script over every
 * shadowed setter/getter pair, and reports where their answers differ. The JDK is the
 * oracle — the third audit axis, after D_property_fanout_audit and D_owed_events audited
 * what <em>fires</em>: this one audits what <em>returns</em>.
 *
 * <pre>
 * ./mvnw -C -pl emulators test-compile
 * xvfb-run -a ./mvnw -C -pl emulators exec:exec     # report lands in target/jdk-return-value-audit.md
 * </pre>
 *
 * <p><b>This main exits 0 whenever it ran</b> — same contract as {@code :migration-tool}'s
 * mains, so a maintainer sweeping by hand gets a worklist and not a verdict. The
 * <em>gate</em> is {@link JdkReturnValueAuditTest}, which fails on any divergence
 * {@code sanctioned-divergences.tsv} does not already carry a reason for. A divergence is
 * not automatically a bug — {@code setText(null)} reading back as {@code ""} is
 * R_vaadin_first working as designed — which is why that table holds reasons and the gate
 * reads it, rather than an allow-list of names.
 *
 * <h2>A display widens the sweep; it is not needed for soundness</h2>
 * Headless, the JDK oracle refuses to construct {@code Frame} / {@code JDialog} / the AWT
 * widgets at all — some 60 classes drop out, which is why a full sweep wants
 * {@code xvfb-run}. What headless does <em>not</em> do is lie: an oracle answer of
 * {@code HeadlessException} is skipped and counted rather than reported (see
 * {@code oracleWantedADisplay}), so the gate is sound inside Surefire's deliberately
 * headless JVM and the same real rows surface either way. The emulator side needs no
 * display, only the Karibu UI this sets up, because R_callswing_envelope's seams throw
 * without a current UI.
 *
 * <p>Reflects over protected members, so the JVM needs {@code --add-opens} on
 * {@code java.desktop}'s Swing and AWT packages; without them the oracle reports
 * {@code InaccessibleObjectException} instead of a value and every protected pair reads
 * as a divergence. Both {@code :emulators}' {@code surefire} {@code argLine} and the
 * pom's {@code exec-maven-plugin} configuration pass them, which is why the invocation
 * above goes through Maven rather than a bare {@code java}.
 */
public final class JdkReturnValueDiffer {

    /** What one layer answered for one probe value: a value, or the exception that escaped. */
    private record Outcome(Object value, String thrown) {

        @Override
        public String toString() {
            return thrown != null ? "throws " + thrown : render(value);
        }
    }

    /** One disagreement, ready to print. */
    private record Divergence(String label, String valueType, String probe, Outcome jdk, Outcome emulator) {
    }

    private final List<Divergence> divergences = new ArrayList<>();
    private final Map<String, Integer> skippedValueTypes = new TreeMap<>();
    private final Map<String, String> skippedClasses = new LinkedHashMap<>();
    private final SanctionedDivergences sanctioned = SanctionedDivergences.load();
    private final java.util.Set<String> drivenResolutions = new java.util.HashSet<>();
    private final Map<String, String> unconstructedResolutions = new LinkedHashMap<>();
    private int compared;
    private int sanctionedHits;
    private int pairsSeen;
    private final Map<String, Integer> projectedReturnTypes = new TreeMap<>();
    private int comparedByProjection;
    private int oracleNeedsDisplay;

    public static void main(String[] args) throws IOException {
        JdkReturnValueDiffer differ = sweepUnderKaribuUI();
        String report = differ.report();
        System.out.println(report);
        if (args.length > 0) {
            Files.writeString(Path.of(args[0]), report);
            System.out.println("\nwritten to " + args[0]);
        }
    }

    /**
     * Runs a whole sweep, standing up the Karibu UI the emulator side needs and
     * tearing it down again.
     *
     * <pre>{@code
     * JdkReturnValueDiffer differ = JdkReturnValueDiffer.sweepUnderKaribuUI();
     * assertEquals(List.of(), differ.unsanctioned(), differ::report);
     * }</pre>
     */
    static JdkReturnValueDiffer sweepUnderKaribuUI() throws IOException {
        JdkReturnValueDiffer differ = new JdkReturnValueDiffer();
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(new Routes()));
        BrowserTimeZone.setZoneId(ZoneOffset.UTC);
        AppTab.markAppUI(UI.getCurrent());
        try {
            differ.sweep();
        } finally {
            MockVaadin.tearDown();
        }
        return differ;
    }

    /**
     * @return one {@code Class.property(argType)} line per divergence that
     *         {@code sanctioned-divergences.tsv} does not already carry a reason
     *         for — empty when the audit is clean. The full table, with both
     *         layers' answers, is in {@link #report()}.
     */
    List<String> unsanctioned() {
        return divergences.stream().map(d -> d.label() + "(" + d.valueType() + ")").toList();
    }

    private void sweep() throws IOException {
        for (Class<?> emulator : JdkProvenance.emulatorClasses()) {
            if (emulator.isInterface() || emulator.isSynthetic() || Modifier.isAbstract(emulator.getModifiers())) {
                continue;
            }
            Class<?> jdk = JdkProvenance.jdkCounterpart(emulator);
            if (jdk == null || Modifier.isAbstract(jdk.getModifiers())) {
                continue;
            }
            List<JdkProvenance.Pair> pairs = JdkProvenance.shadowedPairs(emulator, jdk);
            if (pairs.isEmpty()) {
                continue;
            }
            for (JdkProvenance.Pair pair : pairs) {
                pairsSeen++;
                comparePair(pair);
            }
        }
    }

    private void comparePair(JdkProvenance.Pair pair) {
        // An inherited pair resolves identically on every subclass that does not override
        // either half, so drive it once — on whichever concrete class reaches it first.
        // A subclass that *does* override resolves to different methods and so is a
        // distinct key, which is how JToggleButton's setSelected still gets its own run.
        String resolution = pair.emulatorSetter().getDeclaringClass().getName()
                + "#" + JdkProvenance.signature(pair.emulatorSetter())
                + "/" + pair.emulatorGetter().getDeclaringClass().getName()
                + "#" + JdkProvenance.signature(pair.emulatorGetter());
        if (drivenResolutions.contains(resolution)) {
            return;
        }
        List<ProbeValues.ValuePair> probes = ProbeValues.forType(pair.valueType());
        if (probes.isEmpty()) {
            // A property of the pair, not of the host, so claim it: every other host
            // reaching this resolution would skip for the same reason and inflate the queue.
            drivenResolutions.add(resolution);
            skippedValueTypes.merge(pair.valueType().getSimpleName(), 1, Integer::sum);
            return;
        }
        for (ProbeValues.ValuePair probe : probes) {
            Object emulatorInstance = construct(pair.emulator());
            Object jdkInstance = construct(pair.jdk());
            if (emulatorInstance == null || jdkInstance == null) {
                // Leave the resolution unclaimed — a later concrete host may reach the same
                // pair and construct. Claiming it here loses the pair silently, which is what
                // hid all seven JComponent state-drop pairs behind ctor-less Box.
                unconstructedResolutions.put(resolution, pair.label() + "(" + pair.valueType().getSimpleName() + ")");
                return;
            }
            drivenResolutions.add(resolution);
            unconstructedResolutions.remove(resolution);
            System.err.println("  " + pair.label() + " := " + probe.label());
            Outcome emulated = drive(emulatorInstance, pair.emulatorSetter(), pair.emulatorGetter(), probe.emulator());
            Outcome real = driveWithWatchdog(jdkInstance, pair.jdkSetter(), pair.jdkGetter(), probe.jdk());
            if (oracleWantedADisplay(real)) {
                oracleNeedsDisplay++;
                continue;
            }
            boolean direct = comparableAcrossLayers(pair.emulatorGetter().getReturnType());
            compared++;
            if (!direct) {
                comparedByProjection++;
                projectedReturnTypes.merge(pair.emulatorGetter().getReturnType().getSimpleName(),
                        1, Integer::sum);
            }
            if (direct ? agree(emulated, real) : agreeByProjection(emulated, real, probe)) {
                continue;
            }
            if (sanctioned.covers(pair.emulatorSetter().getDeclaringClass().getSimpleName(),
                    pair.property(), pair.valueType().getSimpleName())) {
                sanctionedHits++;
                continue;
            }
            divergences.add(new Divergence(pair.label(), pair.valueType().getSimpleName(),
                    probe.label(), real, emulated));
        }
    }

    /**
     * True when the two layers agree <em>as far as a projection can see</em>, for a getter
     * whose answers have no common type to compare.
     *
     * <p>A set-then-read script already knows the expected answer: it is the probe. So
     * rather than an equivalence between a {@code vaadinx.swing.Icon} and a
     * {@code javax.swing.Icon} — one hand-written comparator per type — each layer's
     * answer goes through {@link #project} and the summaries are compared. Nothing is
     * needed per type, and it catches what this axis exists for: {@code null}, or a
     * stranger, where the JDK hands back what you set.
     *
     * <p>What it cannot see, stated because the report counts these as compared:
     * two getters that both hand back a fresh instance project identically, so
     * a copy whose <em>contents</em> diverge passes, and an array is checked
     * only for length. Both are false negatives, never false positives — a
     * projection mismatch is always a real disagreement — so the gate's
     * trustworthiness is intact and only its reach is limited. The report's
     * ranked tail says which types a real comparator would pay for.
     */
    private static boolean agreeByProjection(Outcome emulator, Outcome jdk, ProbeValues.ValuePair probe) {
        if (emulator.thrown() != null || jdk.thrown() != null) {
            return Objects.equals(emulator.thrown(), jdk.thrown());
        }
        return project(emulator.value(), probe.emulator()).equals(project(jdk.value(), probe.jdk()));
    }

    /**
     * One layer's answer, reduced to what means the same thing on both.
     *
     * <pre>
     * null                     -> "null"
     * the instance just set    -> "the value set"          &lt;- what a faithful getter answers
     * an array                 -> "an array of 3"
     * anything else            -> "some other instance"    &lt;- a copy, or a default, or a stranger
     * </pre>
     */
    private static String project(Object value, Object probe) {
        if (value == null) {
            return "null";
        }
        if (value == probe) {
            return "the value set";
        }
        if (value.getClass().isArray()) {
            return "an array of " + java.lang.reflect.Array.getLength(value);
        }
        return "some other instance";
    }

    /**
     * True when a getter's answers can be compared by value across the two layers.
     *
     * <p>A getter returning a type SB-Emulators <em>ports</em> hands back a
     * {@code vaadinx.swing.Icon} on one side and a {@code javax.swing.Icon} on the other:
     * no {@code equals} relates them, and comparing {@code toString()} would report
     * differences that are only ever the class name. Those go to
     * {@link #agreeByProjection} instead of being skipped.
     */
    private static boolean comparableAcrossLayers(Class<?> returnType) {
        return returnType.isPrimitive()
                || returnType == String.class
                || returnType.isEnum()
                || JdkProvenance.reusedFromJdk(returnType);
    }

    private Object construct(Class<?> c) {
        if (skippedClasses.containsKey(c.getName())) {
            return null;
        }
        try {
            Constructor<?> ctor = c.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (Throwable t) {
            skippedClasses.put(c.getName(), rootCause(t));
            return null;
        }
    }

    /**
     * {@link #drive}, on a throwaway daemon thread that is abandoned if it does not finish.
     *
     * <p>Some JDK setters never return by design — a modal {@code Dialog.setVisible(true)}
     * pumps the AWT event queue until something dismisses it, and nothing will. Only the
     * JDK side gets this treatment: the emulator side has to run on the sweeping thread,
     * where Karibu's {@code UI.getCurrent()} lives, and an emulator that blocks here is a
     * finding to chase down from the progress line on stderr rather than something to
     * paper over.
     */
    private static Outcome driveWithWatchdog(Object target, Method setter, Method getter, Object value) {
        Outcome[] slot = new Outcome[1];
        Thread t = new Thread(() -> slot[0] = drive(target, setter, getter, value), "jdk-oracle");
        t.setDaemon(true);
        t.start();
        try {
            t.join(WATCHDOG_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return slot[0] != null ? slot[0] : new Outcome(null, "did not return within " + WATCHDOG_MILLIS + "ms");
    }

    private static final long WATCHDOG_MILLIS = 3_000;

    private static Outcome drive(Object target, Method setter, Method getter, Object value) {
        try {
            setter.setAccessible(true);
            getter.setAccessible(true);
        } catch (Throwable t) {
            return new Outcome(null, "inaccessible:" + t.getClass().getSimpleName());
        }
        try {
            setter.invoke(target, value);
        } catch (InvocationTargetException e) {
            return new Outcome(null, "set→" + e.getCause().getClass().getSimpleName());
        } catch (Throwable t) {
            return new Outcome(null, "set!→" + t.getClass().getSimpleName());
        }
        try {
            return new Outcome(getter.invoke(target), null);
        } catch (InvocationTargetException e) {
            return new Outcome(null, "get→" + e.getCause().getClass().getSimpleName());
        } catch (Throwable t) {
            return new Outcome(null, "get!→" + t.getClass().getSimpleName());
        }
    }

    /**
     * True when the two layers gave the same observable answer.
     *
     * <p>Numbers compare by value, not by box: a {@code short} property whose emulator
     * getter widened to {@code int} is not the divergence this audit is hunting.
     * A throw agrees only with the same exception type, which is R_match_swing_errors'
     * commitment made checkable.
     */
    /**
     * True when the oracle's answer was "I have no display", which is not an
     * answer about the property at all.
     *
     * <p>Without this the sweep would be sound only under {@code xvfb}, and the
     * whole suite runs {@code -Djava.awt.headless=true} on purpose (see the
     * parent pom's {@code argLine}). Headless, six pairs — five
     * {@code setDragEnabled} plus {@code JComboBox.setPopupVisible} — report the
     * JDK throwing {@code HeadlessException} against an emulator that answered
     * normally, and say nothing about SB-Emulators either way.
     *
     * <p>Skipped and counted rather than moved into
     * {@code sanctioned-divergences.tsv}: a row there is a claim about
     * a <em>property</em>, so absorbing "no display" would blind the
     * gate to the real answers those same properties give under a
     * display.
     */
    private static boolean oracleWantedADisplay(Outcome jdk) {
        return jdk.thrown() != null && jdk.thrown().endsWith("HeadlessException");
    }

    private static boolean agree(Outcome a, Outcome b) {
        if (a.thrown() != null || b.thrown() != null) {
            return Objects.equals(a.thrown(), b.thrown());
        }
        if (a.value() instanceof Number x && b.value() instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a.value(), b.value());
    }

    private static String render(Object o) {
        if (o == null) {
            return "null";
        }
        if (o instanceof char[] chars) {
            return "char[]\"" + new String(chars) + "\"";
        }
        if (o.getClass().isArray()) {
            return o.getClass().getSimpleName() + "(len " + java.lang.reflect.Array.getLength(o) + ")";
        }
        return o instanceof String ? "\"" + o + "\"" : String.valueOf(o);
    }

    private static String rootCause(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) {
            r = r.getCause();
        }
        return r.getClass().getSimpleName() + (r.getMessage() != null ? ": " + r.getMessage() : "");
    }

    String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("# JDK return-value audit\n\n");
        sb.append("Oracle: JDK ").append(Runtime.version()).append(", display ")
                .append(java.awt.GraphicsEnvironment.isHeadless() ? "**headless — partial sweep**" : "present")
                .append("\n\n");
        sb.append("| | |\n|---|---:|\n");
        sb.append("| shadowed setter/getter pairs | ").append(pairsSeen).append(" |\n");
        sb.append("| set-then-read comparisons run | ").append(compared).append(" |\n");
        sb.append("| **divergences to triage** | **").append(divergences.size()).append("** |\n");
        sb.append("| divergences already sanctioned | ").append(sanctionedHits).append(" |\n");
        sb.append("| ... of those, compared by projection only | ")
                .append(comparedByProjection).append(" |\n");
        sb.append("| pairs skipped — no probe value for the argument type | ")
                .append(skippedValueTypes.values().stream().mapToInt(Integer::intValue).sum()).append(" |\n");
        sb.append("| pairs skipped — oracle threw HeadlessException | ")
                .append(oracleNeedsDisplay).append(" |\n");
        sb.append("| pairs skipped — no host constructs on both layers | ")
                .append(unconstructedResolutions.size()).append(" |\n\n");

        sb.append("## Divergences\n\n");
        if (divergences.isEmpty()) {
            sb.append("None.\n\n");
        } else {
            sb.append("| property | arg | probe | JDK answers | SB-Emulators answers |\n");
            sb.append("|---|---|---|---|---|\n");
            for (Divergence d : divergences) {
                sb.append("| `").append(d.label()).append("` | ").append(d.valueType())
                        .append(" | `").append(d.probe()).append("` | ").append(d.jdk())
                        .append(" | ").append(d.emulator()).append(" |\n");
            }
            sb.append('\n');
        }

        sb.append("## Argument types with no probe value yet\n\n");
        sb.append("Each line is *n* pairs unlocked by one `ProbeValues` entry.\n\n");
        rank(sb, skippedValueTypes);

        sb.append("\n## Return types compared by projection only\n\n");
        sb.append("Each line is *n* pairs whose answers are checked for null / identity / array\n");
        sb.append("length but not by value — a real comparator for the type would tighten them.\n\n");
        rank(sb, projectedReturnTypes);

        sb.append("\n## Pairs no host could drive\n\n");
        sb.append("Every concrete class reaching these resolved the same pair, and none of them\n");
        sb.append("constructs on both layers — so the probe table cannot reach them at all. A\n");
        sb.append("no-arg constructor on one host, or a display, unlocks the whole line.\n\n");
        if (unconstructedResolutions.isEmpty()) {
            sb.append("None.\n");
        } else {
            unconstructedResolutions.values().stream().sorted()
                    .forEach(label -> sb.append("- `").append(label).append("`\n"));
        }

        sb.append("\n## Classes that would not construct\n\n");
        if (skippedClasses.isEmpty()) {
            sb.append("None.\n");
        } else {
            skippedClasses.forEach((k, v) -> sb.append("- `").append(k).append("` — ").append(v).append('\n'));
        }
        return sb.toString();
    }

    /** Renders a skip tally biggest-first, so the report's tail doubles as a ranked queue. */
    private static void rank(StringBuilder sb, Map<String, Integer> tally) {
        tally.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> sb.append("- ").append(e.getValue())
                        .append(" — `").append(e.getKey()).append("`\n"));
    }

    /**
     * The triaged-and-accepted divergences, read from {@code sanctioned-divergences.tsv} on
     * the test classpath and keyed {@code SimpleName.property(argType)}.
     *
     * <p>A row here asserts a maintainer looked at the divergence and named the rule that
     * permits it — R_vaadin_first's drop-and-WARN, R_layouts_close_enough, a sub-bucket (b)
     * exclusion. It is the file that lets this tool become a build gate: once every row
     * carries a reason, an <em>un</em>sanctioned divergence is by definition a regression.
     *
     * <p>Keyed by argument type as well as property, so sanctioning
     * {@code setFoo(int)} does not silently also sanction the hand-written
     * {@code setFoo(char)} overload beside it.
     */
    record SanctionedDivergences(Map<String, String> reasons) {

        private static final String RESOURCE = "/vaadinx/audit/sanctioned-divergences.tsv";

        static SanctionedDivergences load() {
            Map<String, String> out = new LinkedHashMap<>();
            try (var in = JdkReturnValueDiffer.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("missing " + RESOURCE + " on the test classpath");
                }
                new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                        .map(String::strip)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                        .forEach(l -> {
                            String[] cols = l.split("\t+");
                            if (cols.length < 4) {
                                throw new IllegalStateException(
                                        "malformed row (want class<TAB>property<TAB>argType<TAB>reason): " + l);
                            }
                            out.put(key(cols[0], cols[1], cols[2]), cols[3]);
                        });
            } catch (IOException e) {
                throw new IllegalStateException("cannot read " + RESOURCE, e);
            }
            return new SanctionedDivergences(out);
        }

        boolean covers(String simpleName, String property, String argType) {
            return reasons.containsKey(key(simpleName, property, argType));
        }

        private static String key(String simpleName, String property, String argType) {
            return simpleName + "." + property + "(" + argType + ")";
        }
    }
}
