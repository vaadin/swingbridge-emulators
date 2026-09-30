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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces <a href="../../../../../CLAUDE.md">R_no_vaadin_in_api</a>'s <em>second</em> limb
 * over the emulators' protected surface: a JDK hook the emulator exposes must
 * actually be invoked by the path that invokes it in the JDK.
 *
 * <p>An exposed-but-unreachable hook is worse than an absent one. A migrator
 * overrides it, the override compiles, it looks wired, and it silently never
 * runs — no compile error, no WARN, no red test. Found four times by luck before
 * this gate existed.
 *
 * <p>The oracle is the JDK's own bytecode, imported from {@code jrt:/java.desktop}:
 * if {@code javax.swing.J}'s ancestry calls a method of this name from its own
 * bytecode, the JDK has declared it an override point, and something in
 * {@code vaadinx.**} / {@code com.vaadin.swingbridge.**} must call it too. Which JDK class counts as
 * an emulator's counterpart is computed from the package map exactly as
 * {@link R12ProvenanceTest} computes it, so the two limbs cannot drift apart.
 *
 * <p><b>Protected only, and that is a finding rather than a shortcut.</b> The same
 * rule over the public surface reports ~289 names and cannot be made green: SB-Emulators
 * drives a Vaadin peer where the JDK computes geometry, so "the JDK's
 * {@code getColumnName} calls {@code convertColumnIndexToModel} and SB-Emulators' does
 * not" is a different design, not a stranded override. A protected method exists
 * <em>only</em> to be overridden, so an uncalled one is exactly the trap. The
 * public tier's triage is a printed report, not a gate.
 *
 * <p>Unlike limb 1's guard this one cannot be allow-list-free, and
 * D_dead_hook_lint states why: the JDK genuinely calls {@code paintComponent} and
 * SB-Emulators genuinely never will. The two lists below are of <em>mechanisms this
 * project decided not to implement</em>, each carrying its reason, and both are
 * asserted to be live — an entry that stops firing fails the build, because it
 * means the mechanism came into scope.
 */
public class R12DeadHookTest {

    /** Emulated package roots, longest prefix first — the same table limb 1 uses. */
    private static final String[][] JDK_PACKAGE = {
            {"vaadinx.awt", "java.awt"},
            {"vaadinx.swing", "javax.swing"},
            {"vaadinx.util", "java.util"},
            {"vaadinx.text", "java.text"},
    };

    /**
     * JDK <em>caller</em> methods whose call sites do not prove a hook, each with
     * the mechanism it stands for.
     *
     * <p>The list is caller-side by measurement, not by taste. Noise clusters by
     * caller — {@code updateUI} alone proves 19 rows on the public surface — so one
     * entry retires a whole mechanism under one reason, where a target-side list
     * would need an entry per stranded method with no reason shared between them.
     * It also self-invalidates: if L&amp;F ever comes into scope, SB-Emulators starts calling
     * {@code updateUI} and the entry visibly stops firing.
     */
    private static final Map<String, String> SUPPRESSED_CALLER = new LinkedHashMap<>();

    /**
     * Individual hooks that are unreachable on purpose, where the JDK's caller
     * <em>is</em> in scope so no mechanism entry covers them. Keyed
     * {@code emulator.Class#method}.
     */
    private static final Map<String, String> DECLINED_TARGET = new LinkedHashMap<>();

    static {
        SUPPRESSED_CALLER.put("paint",
                "painting is permanently out — R_match_swing_errors sub-bucket (b)");
        SUPPRESSED_CALLER.put("printComponent",
                "component printing is permanently out — R_match_swing_errors sub-bucket (b)");
        SUPPRESSED_CALLER.put("printChildren",
                "component printing is permanently out — R_match_swing_errors sub-bucket (b)");
        SUPPRESSED_CALLER.put("_paintImmediately",
                "the repaint manager has no DOM counterpart — R_match_swing_errors sub-bucket (b)");
        // The paint family calls itself, so suppressing its entry point is not
        // enough: each inner link needs its own entry, and they are all the one
        // mechanism. paintBorder appears here as a caller (AbstractButton's
        // super-call into JComponent's) and below as a target.
        SUPPRESSED_CALLER.put("paintBorder",
                "painting is permanently out — R_match_swing_errors sub-bucket (b)");
        SUPPRESSED_CALLER.put("printBorder",
                "component printing is permanently out — R_match_swing_errors sub-bucket (b)");
        SUPPRESSED_CALLER.put("paintToOffscreen",
                "offscreen buffering has no DOM counterpart — R_match_swing_errors sub-bucket (b)");
        SUPPRESSED_CALLER.put("processKeyEvent",
                "key dispatch is out per D_focus_managers");
        SUPPRESSED_CALLER.put("processKeyBindings",
                "key dispatch is out per D_focus_managers");
        SUPPRESSED_CALLER.put("getPreferredSize",
                "pixel geometry is best-effort — R_layouts_close_enough");
        SUPPRESSED_CALLER.put("getPreferredScrollableViewportSize",
                "pixel geometry is best-effort — R_layouts_close_enough");
        SUPPRESSED_CALLER.put("getScrollableUnitIncrement",
                "pixel geometry is best-effort — R_layouts_close_enough");
        SUPPRESSED_CALLER.put("shouldNativelyFocusHeavyweight",
                "AWT's native focus transfer has no counterpart — D_focus_managers");
        SUPPRESSED_CALLER.put("removeFirstRequest",
                "AWT's native focus-request queue has no counterpart — D_focus_managers");
        SUPPRESSED_CALLER.put("retargetFocusEvent",
                "AWT's focus-event retargeting has no counterpart — D_focus_managers");
        SUPPRESSED_CALLER.put("retargetFocusLost",
                "AWT's focus-event retargeting has no counterpart — D_focus_managers");
        SUPPRESSED_CALLER.put("retargetFocusGained",
                "AWT's focus-event retargeting has no counterpart — D_focus_managers");
        SUPPRESSED_CALLER.put("processSynchronousLightweightTransfer",
                "AWT's synchronous focus transfer has no counterpart — D_focus_managers");
        SUPPRESSED_CALLER.put("processCurrentLightweightRequests",
                "AWT's native focus-request queue has no counterpart — D_focus_managers");

        DECLINED_TARGET.put("vaadinx.awt.KeyboardFocusManager#fireVetoableChange",
                "the vetoable registry stays declined — D_focus_property_registry");
        DECLINED_TARGET.put("vaadinx.swing.text.DefaultCaret#damage",
                "damage takes the caret's view rectangle, which only the browser has — D_emulator_caret");
        DECLINED_TARGET.put("vaadinx.swing.text.DefaultCaret#adjustVisibility",
                "adjustVisibility takes the caret's view rectangle, which only the browser has — D_emulator_caret");
        DECLINED_TARGET.put("vaadinx.swing.text.DefaultCaret#positionCaret",
                "reached from the caret's mouse listener, which is not registered: a mouse bridge costs a"
                        + " round trip per click, and the browser positions the caret — D_emulator_caret");
        DECLINED_TARGET.put("vaadinx.swing.text.DefaultCaret#moveCaret",
                "reached from the caret's mouse listener, which is not registered — see positionCaret");
        DECLINED_TARGET.put("vaadinx.swing.JSlider#updateLabelUIs",
                "the label table renders through SJSlider, not a per-label UI — L&F is out");
        DECLINED_TARGET.put("vaadinx.swing.JSpinner#createEditor",
                "the editor pipeline is drop-and-WARN at setEditor — R_match_swing_errors (c)");
        DECLINED_TARGET.put("vaadinx.swing.AbstractButton#createChangeListener",
                "ButtonModel listener rewiring is the declined effect — see setModel's javadoc");
        DECLINED_TARGET.put("vaadinx.swing.AbstractButton#createActionListener",
                "ButtonModel listener rewiring is the declined effect — see setModel's javadoc");
        DECLINED_TARGET.put("vaadinx.swing.AbstractButton#createItemListener",
                "ButtonModel listener rewiring is the declined effect — see setModel's javadoc");
        DECLINED_TARGET.put("vaadinx.swing.JPopupMenu#firePopupMenuCanceled",
                "the peer's opened-change signal cannot tell an Esc/click-outside cancel "
                        + "from an item-selection close — R_match_swing_errors sub-bucket (a); "
                        + "see installOpenedSync's javadoc");
        DECLINED_TARGET.put("vaadinx.swing.JInternalFrame#setRootPane",
                "the pane is built lazily in getRootPane; routing construction through the "
                        + "setter would fire a 'rootPane' PCE the desktop fires in the ctor, "
                        + "where nobody can hear it — D_dead_hook_lint");
    }

    /**
     * JDK caller methods that are never proof because SB-Emulators has no such path at
     * all. Distinct from {@link #SUPPRESSED_CALLER}: these are not mechanisms
     * SB-Emulators declined, they are mechanisms that do not exist here, so they carry no
     * reason to review and no staleness assertion.
     */
    private static final Set<String> NOT_A_PATH_HERE = Set.of(
            "readObject", "writeObject", "readObjectNoData", "readResolve", "writeReplace",
            "deserializeResources", "compWriteObjectNotify", "<clinit>");

    @Test
    void everyJdkHookTheEmulatorExposesIsReachedFromEmulatorCode() {
        JavaClasses jdk = importJdk();
        JavaClasses emulator = importEmulators();

        // Guard the guard, twice over: a silent zero-class import would pass forever.
        assertTrue(jdk.size() > 4000, "only " + jdk.size() + " java.desktop classes imported — the jrt import is broken");
        assertTrue(emulator.size() > 200, "only " + emulator.size() + " emulator classes imported — build target/classes first");

        // Method references must register as accesses, or every `this::foo`
        // wiring in SB-Emulators reads as a dead hook and this gate is pure noise.
        long methodRefs = 0;
        for (JavaClass c : emulator) {
            for (JavaCodeUnit u : c.getCodeUnits()) methodRefs += u.getMethodReferencesFromSelf().size();
        }
        assertTrue(methodRefs > 100,
                "only " + methodRefs + " method references seen — ArchUnit is not resolving `this::foo`, "
                        + "so every method-reference wiring would read as dead");

        Map<JavaClass, JavaClass> pairs = new LinkedHashMap<>();
        for (JavaClass c : emulator) {
            if (!c.getName().startsWith("vaadinx.")) continue;
            JavaClass j = jdkCounterpart(c, jdk);
            if (j != null) pairs.put(c, j);
        }
        assertTrue(pairs.size() > 100, "only " + pairs.size() + " emulator/JDK pairs resolved — scan is broken");

        OurCalls calls = scanOurCalls(emulator);
        Set<String> firedCallerEntries = new HashSet<>();
        Set<String> firedDeclinedEntries = new HashSet<>();
        List<String> violations = new ArrayList<>();

        for (Map.Entry<JavaClass, JavaClass> e : pairs.entrySet()) {
            JavaClass em = e.getKey();
            Set<String> ancestry = ancestryNames(e.getValue());

            for (JavaMethod m : em.getMethods()) {
                if (!m.getModifiers().contains(JavaModifier.PROTECTED)) continue;
                // A final method cannot be overridden, so no migrator override
                // can be stranded on it. Uncalled-and-final is dead code, which
                // is a different (and much cheaper) problem than limb 2's.
                if (m.getModifiers().contains(JavaModifier.FINAL)) continue;
                if (m.getName().startsWith("lambda$") || m.getName().startsWith("access$")) continue;

                List<String> proof = hookProof(jdk, ancestry, m.getName(), firedCallerEntries);
                if (proof.isEmpty()) continue;
                if (calledByEmulators(calls, em, m.getName())) continue;
                if (calledByInheritedJdkCode(em, proof)) continue;

                String key = em.getName() + "#" + m.getName();
                if (DECLINED_TARGET.containsKey(key)) {
                    firedDeclinedEntries.add(key);
                    continue;
                }
                violations.add(key + "\n"
                        + "        the JDK proves this is a hook: " + proof.get(0) + "\n"
                        + "        nothing in the emulators calls it, so a migrator's override never runs.\n"
                        + "        Either call it from the path the JDK calls it from, or add it to "
                        + "DECLINED_TARGET with the reason it cannot be reached here.");
            }
        }

        System.out.println("R_no_vaadin_in_api limb 2: " + violations.size() + " unreachable hook(s); "
                + firedDeclinedEntries.size() + " declined on purpose, "
                + firedCallerEntries.size() + " caller mechanisms suppressed");

        List<String> stale = new ArrayList<>();
        for (String k : SUPPRESSED_CALLER.keySet()) {
            if (!firedCallerEntries.contains(k)) stale.add("SUPPRESSED_CALLER['" + k + "']");
        }
        for (String k : DECLINED_TARGET.keySet()) {
            if (!firedDeclinedEntries.contains(k)) stale.add("DECLINED_TARGET['" + k + "']");
        }

        if (!violations.isEmpty() || !stale.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            if (!violations.isEmpty()) {
                msg.append("R_no_vaadin_in_api limb 2 — ").append(violations.size())
                        .append(" JDK hook(s) the emulator exposes that nothing in SB-Emulators ever calls:\n\n")
                        .append(violations.stream().map(v -> "    " + v).collect(Collectors.joining("\n\n")));
            }
            if (!stale.isEmpty()) {
                msg.append(violations.isEmpty() ? "" : "\n\n")
                        .append("Stale suppression entries — each fired when it was added and fires no "
                                + "longer, which means the mechanism it excuses came into scope (or the "
                                + "hook it names was renamed). Remove them:\n\n    ")
                        .append(String.join("\n    ", stale));
            }
            fail(msg.toString());
        }
    }

    /** JDK ancestry classes that call [name] on something in that ancestry. */
    private List<String> hookProof(JavaClasses jdk, Set<String> ancestry, String name, Set<String> firedCallerEntries) {
        List<String> proof = new ArrayList<>();
        for (String owner : ancestry) {
            if (!jdk.contain(owner)) continue;
            for (JavaMethod jm : jdk.get(owner).getMethods()) {
                if (!jm.getName().equals(name)) continue;
                for (var access : jm.getAccessesToSelf()) {
                    String from = access.getOrigin().getOwner().getName();
                    String fromMethod = access.getOrigin().getName();
                    // L&F dispatch is permanently out, so a hook only plaf calls
                    // is legitimately dead here. Without this the rule fires on
                    // every BasicXxxUI-driven hook in Swing, which is most of them.
                    if (from.contains(".plaf.")) continue;
                    if (!ancestry.contains(from)) continue;
                    if (NOT_A_PATH_HERE.contains(fromMethod)) continue;
                    if (SUPPRESSED_CALLER.containsKey(fromMethod)) {
                        firedCallerEntries.add(fromMethod);
                        continue;
                    }
                    // Overload delegation is not a hook: addContainerGap(int)
                    // calling addContainerGap(int,int) proves nothing about
                    // whether the JDK treats the name as an override point.
                    if (from.equals(owner) && fromMethod.equals(name)) continue;
                    proof.add(from + "." + fromMethod + " -> " + owner + "." + name);
                }
            }
        }
        return proof;
    }

    /**
     * True when SB-Emulators calls [name] on the emulator class, an emulator supertype or
     * an emulator subtype, <em>from a body that can run</em>.
     *
     * <p>Deliberately loose in SB-Emulators' favour on the class axis — a call to
     * {@code JLabel.paramString} excuses {@code JButton.paramString} — because
     * this gate's job is to catch hooks nothing anywhere reaches, and a false
     * failure costs more than a missed row that the next slice will surface
     * anyway. It is <em>not</em> loose on the reachability axis: see
     * {@link OurCalls}.
     */
    private boolean calledByEmulators(OurCalls calls, JavaClass em, String name) {
        if (calls.reaches(em.getName() + "#" + name)) return true;
        for (JavaClass s : em.getAllRawSuperclasses()) {
            if (calls.reaches(s.getName() + "#" + name)) return true;
        }
        for (JavaClass s : em.getAllSubclasses()) {
            if (calls.reaches(s.getName() + "#" + name)) return true;
        }
        return false;
    }

    /**
     * True when the proof caller lives in a JDK class the emulator genuinely
     * extends or implements, in which case the JDK's own bytecode performs the
     * call and the override really does run.
     *
     * <p>{@code vaadinx.awt.event.ComponentEvent} extends {@code java.awt.AWTEvent},
     * whose {@code toString()} dispatches to the {@code paramString()} override —
     * the same sanction {@link R12ProvenanceTest}'s {@code overridesJdkDeclared}
     * applies on limb 1, and it must walk interfaces as well as superclasses.
     */
    private boolean calledByInheritedJdkCode(JavaClass em, List<String> proof) {
        Set<String> jdkSupers = new HashSet<>();
        for (JavaClass s : em.getAllRawSuperclasses()) {
            if (isJdk(s.getName())) jdkSupers.add(s.getName());
        }
        for (JavaClass i : em.getAllRawInterfaces()) {
            if (isJdk(i.getName())) jdkSupers.add(i.getName());
        }
        if (jdkSupers.isEmpty()) return false;
        for (String p : proof) {
            String caller = p.substring(0, p.lastIndexOf(" -> "));
            if (jdkSupers.contains(caller.substring(0, caller.lastIndexOf('.')))) return true;
        }
        return false;
    }

    private static boolean isJdk(String name) {
        return name.startsWith("java.") || name.startsWith("javax.");
    }

    /**
     * SB-Emulators' own invocation graph, reduced to the two questions limb 2 asks of
     * it: which {@code owner#name} each site invokes, and which of our own code
     * units can run at all.
     *
     * <p>The second half is what makes the first trustworthy — a call site inside
     * a method nothing reaches proves nothing, since the migrator's override
     * would be invoked from a body that never executes.
     *
     * <p><b>Reachable means reachable from an entry point, not called by us:</b> a
     * <em>public</em> method is an entry point whether or not SB-Emulators calls it,
     * because the migrator calls it.
     */
    private record OurCalls(Map<String, Set<String>> sitesByTarget, Set<String> reachable) {
        /** True when some <em>reachable</em> unit of ours invokes {@code owner#name}. */
        boolean reaches(String target) {
            for (String site : sitesByTarget.getOrDefault(target, Set.of())) {
                if (reachable.contains(site)) return true;
            }
            return false;
        }
    }

    private OurCalls scanOurCalls(JavaClasses ours) {
        Map<String, Set<String>> sitesByTarget = new LinkedHashMap<>();
        Map<String, Set<String>> callees = new LinkedHashMap<>();
        Set<String> roots = new LinkedHashSet<>();
        Set<String> units = new LinkedHashSet<>();

        for (JavaClass c : ours) {
            for (JavaCodeUnit u : c.getCodeUnits()) {
                String site = unitKey(c.getName(), u.getName());
                units.add(site);
                if (isEntryPoint(c, u)) roots.add(site);
                for (String target : invocationTargets(u)) {
                    sitesByTarget.computeIfAbsent(target, k -> new HashSet<>()).add(site);
                    callees.computeIfAbsent(site, k -> new HashSet<>()).addAll(dispatchKeys(ours, target));
                }
            }
        }

        Set<String> reachable = new HashSet<>(roots);
        Deque<String> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            for (String next : callees.getOrDefault(queue.poll(), Set.of())) {
                if (reachable.add(next)) queue.add(next);
            }
        }
        reachable.retainAll(units);

        // Guard the graph, not the code: edges that stopped resolving would mark
        // almost everything unreachable and flood the report with rows that are
        // artefacts of a broken scan. One clear failure beats fifty false ones.
        assertTrue(reachable.size() > units.size() * 3 / 4,
                "only " + reachable.size() + " of " + units.size() + " code units reachable — "
                        + "the call graph is not resolving, so every hook would read as dead");
        System.out.println("SB-Emulators call graph: " + reachable.size() + " of " + units.size()
                + " code units reachable from an entry point");
        return new OurCalls(sitesByTarget, reachable);
    }

    /**
     * Every {@code owner#name} this code unit invokes — calls <em>and</em> method
     * references, since {@code addChangeListener(this::fireStateChanged)} wires a
     * hook exactly as a call does and {@code getCallsFromSelf()} alone omits it.
     */
    private static List<String> invocationTargets(JavaCodeUnit u) {
        List<String> out = new ArrayList<>();
        for (var call : u.getCallsFromSelf()) {
            out.add(call.getTargetOwner().getName() + "#" + call.getTarget().getName());
        }
        for (var ref : u.getMethodReferencesFromSelf()) {
            out.add(ref.getTargetOwner().getName() + "#" + ref.getTarget().getName());
        }
        return out;
    }

    /**
     * Where a call to {@code owner#name} can land, over our own hierarchy: the
     * override on any subtype (virtual dispatch), and the inherited body on any
     * supertype (the compiler records the static type, which need not declare
     * it). Same looseness as {@link #calledByEmulators}, and for the same reason.
     */
    private Set<String> dispatchKeys(JavaClasses ours, String target) {
        int hash = target.indexOf('#');
        String owner = target.substring(0, hash);
        String name = target.substring(hash + 1);
        Set<String> out = new HashSet<>();
        out.add(unitKey(owner, name));
        if (!ours.contain(owner)) return out;
        JavaClass c = ours.get(owner);
        for (JavaClass s : c.getAllRawSuperclasses()) out.add(unitKey(s.getName(), name));
        for (JavaClass s : c.getAllSubclasses()) out.add(unitKey(s.getName(), name));
        return out;
    }

    /**
     * A unit anything outside SB-Emulators can start: a constructor or static
     * initializer, a public method (the migrator's own entry point), or an
     * override of something a foreign supertype declares — Vaadin calls
     * {@code onAttach}, and the JDK's {@code AbstractPreferences} calls
     * {@code BridgedPreferences}' {@code *Spi} family, neither of which appears
     * in our bytecode as a call site at all.
     */
    private boolean isEntryPoint(JavaClass c, JavaCodeUnit u) {
        String name = u.getName();
        if (name.equals("<init>") || name.equals("<clinit>")) return true;
        if (u.getModifiers().contains(JavaModifier.PUBLIC)) return true;
        if (name.startsWith("lambda$") || name.startsWith("access$")) return false;
        return overriddenFromForeignCode(c.getName(), name);
    }

    private final Map<String, Boolean> foreignOverrideCache = new HashMap<>();

    private boolean overriddenFromForeignCode(String className, String method) {
        return foreignOverrideCache.computeIfAbsent(className + "#" + method, k -> {
            Class<?> c = loadOrNull(className);
            if (c == null) return false;
            for (Class<?> s : foreignSupertypes(c)) {
                for (java.lang.reflect.Method m : s.getDeclaredMethods()) {
                    if (m.getName().equals(method)) return true;
                }
            }
            return false;
        });
    }

    /** Every supertype of [c] that SB-Emulators did not write — the JDK's, Vaadin's, anyone's. */
    private List<Class<?>> foreignSupertypes(Class<?> c) {
        List<Class<?>> out = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        Deque<Class<?>> todo = new ArrayDeque<>();
        todo.add(c);
        while (!todo.isEmpty()) {
            Class<?> k = todo.poll();
            if (!seen.add(k)) continue;
            if (k != c && !isOurs(k.getName())) out.add(k);
            if (k.getSuperclass() != null) todo.add(k.getSuperclass());
            todo.addAll(List.of(k.getInterfaces()));
        }
        return out;
    }

    private static boolean isOurs(String name) {
        return name.startsWith("vaadinx.") || name.startsWith("com.vaadin.swingbridge.");
    }

    private Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, getClass().getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * {@code owner#name}, with a lambda body folded onto the method enclosing it:
     * {@code lambda$foo$0} is not a unit of its own, it runs exactly when
     * {@code foo} runs, so its call sites are {@code foo}'s and its reachability
     * is {@code foo}'s.
     */
    private static String unitKey(String owner, String name) {
        String n = name;
        while (n.startsWith("lambda$")) {
            n = n.substring("lambda$".length());
            int dollar = n.lastIndexOf('$');
            if (dollar > 0) n = n.substring(0, dollar);
        }
        return owner + "#" + (n.equals("new") ? "<init>" : n);
    }

    private Set<String> ancestryNames(JavaClass j) {
        Set<String> out = new LinkedHashSet<>();
        out.add(j.getName());
        for (JavaClass s : j.getAllRawSuperclasses()) {
            if (!s.getName().equals("java.lang.Object")) out.add(s.getName());
        }
        return out;
    }

    private JavaClass jdkCounterpart(JavaClass c, JavaClasses jdk) {
        for (String[] p : JDK_PACKAGE) {
            if (c.getName().startsWith(p[0] + ".")) {
                String name = p[1] + c.getName().substring(p[0].length());
                return jdk.contain(name) ? jdk.get(name) : null;
            }
        }
        return null;
    }

    /**
     * The JDK's own {@code java.desktop} module.
     *
     * <p>{@code importUrl} on the jrt path, not {@code importPaths} —
     * ArchUnit's {@code Location} demands a {@code file} scheme and
     * rejects a jrt {@code Path} outright. Nor {@code importClasses(…)}
     * by name: that route imports only the named classes, and a caller
     * outside the imported set is invisible, so the call graph comes
     * back silently short.
     */
    private JavaClasses importJdk() {
        try {
            FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
            return new ClassFileImporter()
                    .importUrl(jrt.getPath("/modules/java.desktop").toUri().toURL());
        } catch (Exception e) {
            throw new IllegalStateException("cannot import jrt:/modules/java.desktop", e);
        }
    }

    /**
     * Every module whose bytecode may hold a call site: the emulators under test
     * plus the surrogates, since an emulator hook may well be driven from a
     * surrogate-side listener.
     */
    private JavaClasses importEmulators() {
        Path emulators = classesDirOf(EHelper.class);
        Path root = emulators.getParent().getParent().getParent();
        List<Path> dirs = new ArrayList<>();
        dirs.add(emulators);
        Path surrogates = root.resolve("surrogates").resolve("target").resolve("classes");
        if (surrogates.toFile().isDirectory()) dirs.add(surrogates);
        assertTrue(dirs.size() == 2,
                "expected emulators + surrogates target/classes, found " + dirs
                        + " — run a full `./mvnw -C clean install` first");
        return new ClassFileImporter().importPaths(dirs.toArray(new Path[0]));
    }

    private Path classesDirOf(Class<?> c) {
        try {
            File dir = new File(c.getProtectionDomain().getCodeSource().getLocation().toURI());
            assertTrue(dir.isDirectory(), "expected a directory classpath entry, got " + dir);
            return dir.toPath();
        } catch (Exception e) {
            throw new IllegalStateException("cannot locate the classes directory of " + c, e);
        }
    }

    /**
     * Prints the same scan as a per-name report, ordered by how many emulators
     * each stranded hook appears on. Never fails: it is the triage view for
     * adjudicating a row, where the assertion above is the gate.
     */
    @Test
    void reportProtectedHookCoverage() {
        JavaClasses jdk = importJdk();
        JavaClasses emulator = importEmulators();
        OurCalls calls = scanOurCalls(emulator);
        Map<String, List<String>> byName = new TreeMap<>();

        for (JavaClass em : emulator) {
            if (!em.getName().startsWith("vaadinx.")) continue;
            JavaClass j = jdkCounterpart(em, jdk);
            if (j == null) continue;
            Set<String> ancestry = ancestryNames(j);
            for (JavaMethod m : em.getMethods()) {
                if (!m.getModifiers().contains(JavaModifier.PROTECTED)) continue;
                List<String> proof = hookProof(jdk, ancestry, m.getName(), new HashSet<>());
                if (proof.isEmpty()) continue;
                boolean reached = calledByEmulators(calls, em, m.getName())
                        || calledByInheritedJdkCode(em, proof);
                if (reached) continue;
                byName.computeIfAbsent(m.getName(), k -> new ArrayList<>())
                        .add(em.getName() + (m.getModifiers().contains(JavaModifier.FINAL) ? " (final)" : ""));
            }
        }
        System.out.println("limb 2 triage — " + byName.size() + " protected hook name(s) unreached:");
        byName.forEach((name, on) -> System.out.println("  " + name + "  " + on));
    }
}
