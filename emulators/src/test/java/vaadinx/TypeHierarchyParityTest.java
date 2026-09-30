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
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every emulator sits where its JDK counterpart sits: it implements the swapped form of each
 * interface the JDK type declares, and extends the swapped form of the JDK type's superclass.
 *
 * <p>A dropped interface is a defect the rest of the suite cannot see. It breaks
 * {@code instanceof} silently, it makes the emulator uncompilable against a migrator's
 * {@code implements}/parameter declaration, and — when it carries constants — it takes those
 * with it, which is the symptom {@code PublicConstantsTest} catches. That test found 532 gaps
 * that turned out to be three dropped interfaces (D_missing_constants); this one looks at the
 * cause instead of the symptom, and so also sees the interfaces that carry no constants at all
 * — {@code RootPaneContainer}, {@code CellEditorListener}, {@code Accessible}. See
 * D_hierarchy_parity.
 *
 * <p>Allow-list-free like {@code R12ProvenanceTest} / {@code PublicConstantsTest}: pairs are
 * computed by mapping {@code vaadinx.awt.Foo} onto {@code java.awt.Foo}, so a new emulator
 * joins the gate by existing. {@link #SANCTIONED_OMISSIONS} and
 * {@link #SANCTIONED_SUPERCLASSES} are the concession, and each fails on a stale entry.
 *
 * <p>The reverse direction — interfaces an emulator implements that its JDK type does not — is
 * printed, not gated: {@code vaadinx.FieldReconciler.Reconcilable} and
 * {@code vaadinx.awt.CssEmittingLayoutManager} are SB-Emulators-invented types, free by provenance per
 * R_no_vaadin_in_api, and {@code Serializable} on the focus managers is Vaadin session state.
 */
class TypeHierarchyParityTest {

    /** Emulated package roots → the JDK package each mirrors. */
    private static final Map<String, String> JDK_PACKAGE = new LinkedHashMap<>();

    /** The inverse, for resolving a JDK type onto its {@code vaadinx} port when one exists. */
    private static final Map<String, String> EMULATOR_PACKAGE = new LinkedHashMap<>();

    static {
        JDK_PACKAGE.put("vaadinx.awt", "java.awt");
        JDK_PACKAGE.put("vaadinx.swing", "javax.swing");
        JDK_PACKAGE.put("vaadinx.util", "java.util");
        JDK_PACKAGE.forEach((emulator, jdk) -> EMULATOR_PACKAGE.put(jdk, emulator));
    }

    /**
     * Interfaces a JDK type declares that its emulator deliberately does not, keyed
     * {@code emulator + " : " + interface}. Kept minimal on purpose — a growing list here
     * means the gate is being argued with rather than satisfied.
     */
    private static final Map<String, String> SANCTIONED_OMISSIONS = Map.of(
            // D_focus_managers: key-event dispatch is R_match_swing_errors sub-bucket (b), and both
            // interfaces reference java.awt.Component — honouring them means porting two
            // interfaces for methods that could only WARN. Absent on purpose, so a migrated
            // `implements KeyEventDispatcher` fails at compile time naming the class rather
            // than silently never dispatching.
            "vaadinx.awt.KeyboardFocusManager : java.awt.KeyEventDispatcher",
            "D_focus_managers: key dispatch is out of scope; absent so it fails at compile time",
            "vaadinx.awt.KeyboardFocusManager : java.awt.KeyEventPostProcessor",
            "D_focus_managers: key dispatch is out of scope; absent so it fails at compile time");

    /** Same shape, for the extends relationship. */
    private static final Map<String, String> SANCTIONED_SUPERCLASSES = Map.of(
            // D_focus_managers flattens the JDK's four-class focus chain to two, so the
            // JDK's DefaultKeyboardFocusManager rung does not exist here.
            "vaadinx.swing.FocusManager : java.awt.DefaultKeyboardFocusManager",
            "D_focus_managers: the four-class JDK chain is flattened to two");

    @Test
    @DisplayName("every interface a JDK type declares is implemented by its emulator, in ported form")
    void everyInterfaceAJdkTypeDeclaresIsImplementedByItsEmulator() {
        List<String> missing = new ArrayList<>();
        Set<String> sanctioned = new LinkedHashSet<>();
        TreeMap<String, List<String>> extra = new TreeMap<>();
        int checked = 0;

        for (Pair pair : pairs()) {
            for (Class<?> i : ownInterfaces(pair.jdk)) {
                if (!Modifier.isPublic(i.getModifiers())) {
                    continue;
                }
                String key = pair.emulator.getName() + " : " + i.getName();
                if (SANCTIONED_OMISSIONS.containsKey(key)) {
                    sanctioned.add(key);
                    continue;
                }
                checked++;
                Class<?> port = portOrNull(i);
                Class<?> expected = port != null ? port : i;
                if (expected.isAssignableFrom(pair.emulator)) {
                    continue;
                }
                missing.add(pair.emulator.getName() + " does not implement " + expected.getName()
                        + "\n        " + pair.jdk.getName() + " declares " + i.getName()
                        + (port != null
                                ? " — implement the port " + port.getName()
                                        + ", not the JDK interface (R_no_vaadin_in_api limb 1)"
                                : ""));
            }
            // Informational only — see the class javadoc.
            Set<String> jdkNames = new LinkedHashSet<>();
            for (Class<?> i : allInterfaces(pair.jdk)) {
                jdkNames.add(i.getName());
            }
            for (Class<?> i : allInterfaces(pair.emulator)) {
                if (Modifier.isPublic(i.getModifiers())
                        && !jdkNames.contains(i.getName()) && !jdkNames.contains(jdkName(i))) {
                    extra.computeIfAbsent(i.getName(), k -> new ArrayList<>()).add(pair.emulator.getName());
                }
            }
        }

        System.out.println("D_hierarchy_parity interface gate: " + checked + " interface(s) matched, "
                + sanctioned.size() + " sanctioned omission(s)");
        extra.forEach((i, on) -> System.out.println(
                "  (informational) " + on.size() + " emulator(s) implement " + i + ", their JDK types do not"));

        assertNoStaleEntries(SANCTIONED_OMISSIONS, sanctioned, "SANCTIONED_OMISSIONS");
        if (!missing.isEmpty()) {
            fail(missing.size() + " interface(s) a JDK type declares but its emulator does not implement:\n\n"
                    + missing.stream().map(it -> "    " + it).collect(Collectors.joining("\n\n"))
                    + "\n\nImplement it — the members are usually already there, so the fix is the "
                    + "`implements` word. Only when the interface genuinely cannot exist here, add it "
                    + "to SANCTIONED_OMISSIONS with a reason.");
        }
    }

    @Test
    @DisplayName("no emulator implements a JDK interface that has a vaadinx port")
    void noEmulatorImplementsAJdkInterfaceThatHasAVaadinxPort() {
        List<String> wrong = new ArrayList<>();
        for (Pair pair : pairs()) {
            for (Class<?> i : allInterfaces(pair.emulator)) {
                Class<?> port = portOrNull(i);
                if (port != null && port != i && i.isAssignableFrom(pair.emulator)) {
                    wrong.add(pair.emulator.getName() + " implements " + i.getName()
                            + ", but " + port.getName() + " is the port");
                }
            }
        }
        // Zero today, and this test is the only reason it stays zero: reusing a JDK interface
        // forces its JDK types onto the emulator's public surface just as hard as extending a
        // JDK class does, which is what left `JTable implements
        // javax.swing.event.TableColumnModelListener` taking events it could never receive
        // (D_r12_jdk_typed_half).
        if (!wrong.isEmpty()) {
            fail(wrong.size() + " emulator(s) implement a JDK interface where a port exists:\n\n"
                    + wrong.stream().map(it -> "    " + it).collect(Collectors.joining("\n"))
                    + "\n\nSwap the `implements` onto the port; its members are the ported types.");
        }
    }

    @Test
    @DisplayName("every emulator sits under the ported form of its JDK type's superclass")
    void everyEmulatorSitsUnderThePortedFormOfItsJdkTypesSuperclass() {
        List<String> mismatched = new ArrayList<>();
        Set<String> sanctioned = new LinkedHashSet<>();

        for (Pair pair : pairs()) {
            Class<?> jdkSuper = pair.jdk.getSuperclass();
            // A non-public JDK superclass is unreachable from outside its package, so there is
            // nothing an emulator could sit under — javax.swing.GroupLayout.Group extends the
            // private GroupLayout.Spring. Same filter the pair scan applies to counterparts.
            if (jdkSuper == null || jdkSuper == Object.class || !isVisible(jdkSuper)) {
                continue;
            }
            String key = pair.emulator.getName() + " : " + jdkSuper.getName();
            if (SANCTIONED_SUPERCLASSES.containsKey(key)) {
                sanctioned.add(key);
                continue;
            }
            Class<?> expected = portOrNull(jdkSuper) != null ? portOrNull(jdkSuper) : jdkSuper;
            if (!expected.isAssignableFrom(pair.emulator)) {
                mismatched.add(pair.emulator.getName() + " extends " + pair.emulator.getSuperclass().getName()
                        + "\n        " + pair.jdk.getName() + " extends " + jdkSuper.getName()
                        + " — expected assignable to " + expected.getName());
            }
        }

        assertNoStaleEntries(SANCTIONED_SUPERCLASSES, sanctioned, "SANCTIONED_SUPERCLASSES");
        if (!mismatched.isEmpty()) {
            fail(mismatched.size() + " emulator(s) sitting at the wrong place in the hierarchy:\n\n"
                    + mismatched.stream().map(it -> "    " + it).collect(Collectors.joining("\n\n"))
                    + "\n\nRe-parent the emulator, or — when the JDK rung deliberately does not "
                    + "exist here — add it to SANCTIONED_SUPERCLASSES with a reason.");
        }
    }

    /** A stale reason is worse than none: it claims a decision nobody made. */
    private static void assertNoStaleEntries(Map<String, String> sanctions, Set<String> hit, String name) {
        Set<String> stale = new LinkedHashSet<>(sanctions.keySet());
        stale.removeAll(hit);
        assertTrue(stale.isEmpty(),
                name + " names " + String.join(", ", stale)
                        + " which the JDK no longer declares — drop the entr" + (stale.size() == 1 ? "y" : "ies"));
    }

    private record Pair(Class<?> emulator, Class<?> jdk) {
    }

    /** Every emulator with a JDK counterpart reachable by package prefix. */
    private List<Pair> pairs() {
        List<Pair> pairs = new ArrayList<>();
        for (Class<?> c : emulatorClasses()) {
            if (c.isAnonymousClass() || c.isSynthetic()) {
                continue;
            }
            Class<?> jdk = jdkCounterpart(c);
            // A non-public/protected counterpart is a name collision, not a counterpart —
            // vaadinx.swing.JTable$1 against javax.swing.JTable$1 and friends.
            if (jdk == null || !isVisible(jdk)) {
                continue;
            }
            pairs.add(new Pair(c, jdk));
        }
        // Guard the guard: a silent zero-pair scan would pass forever.
        assertTrue(pairs.size() > 150, "only " + pairs.size() + " emulator/JDK pairs found — scan is broken");
        return pairs;
    }

    private static boolean isVisible(Class<?> c) {
        return Modifier.isPublic(c.getModifiers()) || Modifier.isProtected(c.getModifiers());
    }

    /**
     * The interfaces this type itself declares, transitively through their superinterfaces but
     * <em>not</em> through its superclass — a superclass's interfaces are that class's own row.
     */
    private static Set<Class<?>> ownInterfaces(Class<?> c) {
        Set<Class<?>> acc = new LinkedHashSet<>();
        collect(c, acc);
        return acc;
    }

    private static Set<Class<?>> allInterfaces(Class<?> c) {
        Set<Class<?>> acc = new LinkedHashSet<>();
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            collect(k, acc);
        }
        return acc;
    }

    private static void collect(Class<?> c, Set<Class<?>> acc) {
        for (Class<?> i : c.getInterfaces()) {
            if (acc.add(i)) {
                collect(i, acc);
            }
        }
    }

    /** This {@code vaadinx} type's name in JDK terms, for the informational reverse pass. */
    private static String jdkName(Class<?> emulator) {
        String n = emulator.getName();
        for (Map.Entry<String, String> e : JDK_PACKAGE.entrySet()) {
            if (n.startsWith(e.getKey() + ".")) {
                return e.getValue() + n.substring(e.getKey().length());
            }
        }
        return n;
    }

    /** The {@code vaadinx} port of this JDK type, or null when SB-Emulators reuses the JDK's. */
    private Class<?> portOrNull(Class<?> jdk) {
        String n = jdk.getName();
        for (Map.Entry<String, String> e : EMULATOR_PACKAGE.entrySet()) {
            if (n.startsWith(e.getKey() + ".")) {
                return loadOrNull(e.getValue() + n.substring(e.getKey().length()));
            }
        }
        return null;
    }

    /** The JDK class this emulator emulates, or null when SB-Emulators invented the class. */
    private Class<?> jdkCounterpart(Class<?> c) {
        String name = c.getName();
        for (Map.Entry<String, String> prefix : JDK_PACKAGE.entrySet()) {
            if (name.startsWith(prefix.getKey() + ".")) {
                return loadOrNull(prefix.getValue() + name.substring(prefix.getKey().length()));
            }
        }
        return null;
    }

    /** Every {@code vaadinx.**} class in the module under test. */
    private List<Class<?>> emulatorClasses() {
        File dir;
        try {
            dir = new File(EHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("expected a directory classpath entry for :emulators, got " + dir);
        }
        Path base = dir.toPath();
        // An explicit loop rather than a stream, for PublicConstantsTest's reason: `.toList()`
        // into List<Class<?>> is a capture conversion javac accepts and JDT rejects.
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : walk.toList()) {
                if (!p.toString().endsWith(".class")) {
                    continue;
                }
                String s = base.relativize(p).toString();
                String name = s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.');
                if (!name.startsWith("vaadinx.")) {
                    continue;
                }
                Class<?> loaded = loadOrNull(name);
                if (loaded != null) {
                    found.add(loaded);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, getClass().getClassLoader());
        } catch (Throwable e) {
            return null;
        }
    }
}
