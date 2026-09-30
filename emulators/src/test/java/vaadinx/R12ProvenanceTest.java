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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces <a href="../../../../../CLAUDE.md">R_no_vaadin_in_api</a>'s first limb over the whole
 * {@code :emulators} public surface: a method or field whose signature the JDK also
 * declares must speak JDK types, never Vaadin ones.
 *
 * <p>The test that decides this is <b>provenance</b> — <em>"does the JDK define this
 * signature?"</em> — and it is computed, not listed: map {@code vaadinx.awt.Foo} onto
 * {@code java.awt.Foo}, then ask the JDK class whether it declares a member of that
 * name. Anything the JDK has no counterpart for ({@code EHelper}, the
 * {@code *Strategy.createPeer} family, {@code getPeer} itself) is free to speak Vaadin and
 * classifies as such automatically.
 *
 * <p>Being allow-list-free is the point.
 * <a href="../../../../../emulators/decisions.md">D_r12_provenance</a> deferred exactly this check on
 * the grounds that its exception list would encode the <em>by-name</em> framing D_r12_provenance
 * rejected, and rot. Deriving the exceptions from the JDK removes the list, and
 * with it the objection.
 */
class R12ProvenanceTest {

    /** A fully-qualified class name inside a {@code Type.getTypeName()} rendering, nested types included. */
    private static final Pattern FQN =
            Pattern.compile("(?:[a-z][a-zA-Z0-9_]*\\.)+[A-Z][a-zA-Z0-9_$]*");

    /** One emulated package root and the JDK package it maps onto. */
    private record PackageMapping(String vaadinx, String jdk) {
    }

    /** Emulated package roots, longest prefix first so {@code vaadinx.util.prefs} beats {@code vaadinx.util}. */
    private static final List<PackageMapping> JDK_PACKAGE = List.of(
            new PackageMapping("vaadinx.awt", "java.awt"),
            new PackageMapping("vaadinx.swing", "javax.swing"),
            new PackageMapping("vaadinx.util", "java.util"),
            new PackageMapping("vaadinx.text", "java.text"));

    @Test
    @DisplayName("no emulator member that shadows a JDK signature exposes a Vaadin type")
    void noShadowingMemberExposesAVaadinType() throws IOException {
        List<Class<?>> classes = emulatorClasses();
        // Guard the guard: a silent zero-class scan would pass forever.
        assertTrue(classes.size() > 200, "only " + classes.size() + " vaadinx classes scanned — scan is broken");

        List<String> violations = new ArrayList<>();
        int inventedCount = 0;
        int notInJdk = 0;

        for (Class<?> c : classes) {
            Class<?> jdk = jdkCounterpart(c);
            for (Hit member : publicSurfaceMentioningVaadin(c)) {
                if (jdk == null) {
                    inventedCount++;
                } else if (declaresMemberNamed(jdk, member.name())) {
                    violations.add(c.getName() + "." + member.name() + " : " + member.sig() + "\n"
                            + "        " + jdk.getName() + " declares '" + member.name()
                            + "' — the signature is the JDK's, so its types must be too");
                } else {
                    notInJdk++;
                }
            }
        }

        System.out.println(
                "R_no_vaadin_in_api limb 1: " + violations.size() + " violation(s); sanctioned by provenance — "
                        + inventedCount + " on classes with no JDK counterpart, "
                        + notInJdk + " on members the JDK class does not declare");
        if (!violations.isEmpty()) {
            fail("R_no_vaadin_in_api limb 1 — " + violations.size()
                    + " emulator member(s) shadow a JDK signature but expose a "
                    + "Vaadin type. Either give the member the JDK's own type (vaadinx.awt.Component, not "
                    + "com.vaadin.flow.component.Component), or rename it so it is no longer the JDK's "
                    + "signature:\n\n" + indented(violations));
        }
    }

    @Test
    @DisplayName("no emulator member that shadows a JDK signature names a JDK type SB-Emulators emulates")
    void noShadowingMemberNamesAnEmulatedJdkType() throws IOException {
        List<Class<?>> classes = emulatorClasses();
        assertTrue(classes.size() > 200, "only " + classes.size() + " vaadinx classes scanned — scan is broken");

        Map<String, String> emulated = emulatedJdkTypes(classes);
        assertTrue(emulated.size() > 100,
                "only " + emulated.size() + " emulated JDK types resolved — scan is broken");

        List<String> violations = new ArrayList<>();
        int inventedCount = 0;
        int notInJdk = 0;
        int forcedByOverride = 0;

        for (Class<?> c : classes) {
            Class<?> jdk = jdkCounterpart(c);
            for (Hit member : publicSurfaceNamingEmulatedJdkTypes(c, emulated)) {
                if (jdk == null) {
                    inventedCount++;
                } else if (!declaresMemberNamed(jdk, member.name())) {
                    notInJdk++;
                } else if (overridesJdkDeclared(c, member.method())) {
                    forcedByOverride++;
                } else {
                    violations.add(c.getName() + "." + member.name() + " : " + member.sig() + "\n"
                            + "        names " + String.join(", ", member.jdkTypes())
                            + " where SB-Emulators emulates "
                            + member.jdkTypes().stream().map(emulated::get).collect(Collectors.joining(", "))
                            + " — the signature is the JDK's, "
                            + "so a migrated call site can only supply the vaadinx type");
                }
            }
        }

        System.out.println(
                "R_no_vaadin_in_api limb 1 (JDK-typed half): " + violations.size() + " violation(s); sanctioned — "
                        + inventedCount + " on classes with no JDK counterpart, "
                        + notInJdk + " on members the JDK class does not declare, "
                        + forcedByOverride + " forced by overriding a JDK-declared method");
        if (!violations.isEmpty()) {
            fail("R_no_vaadin_in_api limb 1 — " + violations.size()
                    + " emulator member(s) shadow a JDK signature but name a JDK "
                    + "type that SB-Emulators emulates. After the import swap a migrator holds the vaadinx type and "
                    + "cannot call these at all. Either give the member the vaadinx counterpart, or rename it "
                    + "so it is no longer the JDK's signature:\n\n" + indented(violations));
        }
    }

    private static String indented(List<String> violations) {
        return violations.stream().map(v -> "    " + v).collect(Collectors.joining("\n\n"));
    }

    /**
     * Every JDK type SB-Emulators emulates, as JDK name → {@code vaadinx} name.
     *
     * <p>Computed by inverting {@link #jdkCounterpart} over the scanned surface, so
     * "SB-Emulators emulates this" means literally "a {@code vaadinx} class resolves to it" —
     * no second package table to drift out of step with the first.
     */
    private static Map<String, String> emulatedJdkTypes(List<Class<?>> classes) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Class<?> c : classes) {
            Class<?> jdk = jdkCounterpart(c);
            if (jdk != null) {
                out.put(jdk.getName(), c.getName());
            }
        }
        return out;
    }

    /** A flagged member: its name, its signature, the emulated JDK types it names, and its handle if a method. */
    private record Hit(String name, String sig, List<String> jdkTypes, Method method) {
    }

    /**
     * Public and protected methods and fields whose signature names a JDK type
     * SB-Emulators emulates — the mirror image of {@link #publicSurfaceMentioningVaadin}, and
     * the shape {@code JFileChooser.setAccessory} had: typed {@code javax.swing.JComponent},
     * so a migrated {@code vaadinx.swing.JPanel} could not be passed at all and the
     * method was uncallable rather than merely wrong.
     */
    private static List<Hit> publicSurfaceNamingEmulatedJdkTypes(Class<?> c, Map<String, String> emulated) {
        List<Hit> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic() || m.isBridge() || !isVisibleApi(m.getModifiers())) {
                continue;
            }
            String sig = genericSignature(m);
            List<String> hit = jdkTypesNamed(sig, emulated);
            if (!hit.isEmpty()) {
                out.add(new Hit(m.getName(), sig, hit, m));
            }
        }
        for (Field f : c.getDeclaredFields()) {
            if (f.isSynthetic() || !isVisibleApi(f.getModifiers())) {
                continue;
            }
            String sig = f.getGenericType().getTypeName();
            List<String> hit = jdkTypesNamed(sig, emulated);
            if (!hit.isEmpty()) {
                out.add(new Hit(f.getName(), sig, hit, null));
            }
        }
        // Constructors are out of scope for the same reason as in the other
        // half: D_peer_ctor_injection's protected peer ctor is the sanctioned seam.
        return out;
    }

    /**
     * The emulated JDK type names a signature mentions, deduplicated.
     *
     * <p>Extracts fully-qualified names and <em>looks them up</em>, rather than
     * testing each map key with {@code contains} — {@code javax.swing.JMenu} is a
     * prefix of {@code javax.swing.JMenuBar}, so a substring test reports
     * the wrong type on every menu-bar signature.
     */
    private static List<String> jdkTypesNamed(String sig, Map<String, String> emulated) {
        List<String> out = new ArrayList<>();
        Matcher m = FQN.matcher(sig);
        while (m.find()) {
            String name = m.group();
            if (emulated.containsKey(name) && !out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /**
     * True when {@code m} implements or overrides a method a JDK-package supertype
     * declares, in which case the JDK types are forced by the language and not
     * SB-Emulators' choice.
     *
     * <p>Computed, not listed. Two shapes it sanctions: the classes that
     * deliberately <em>extend</em> their JDK counterpart ({@code HTMLEditorKit},
     * {@code StyleSheet}, {@code HTMLDocument} — D_htmleditorkit / D_htmldocument), and any JDK <em>interface</em> SB-Emulators
     * reuses rather than ports. Interfaces have to be walked too, or the
     * sanction reads as an accident — {@code javax.swing.MenuElement}'s
     * {@code getComponent()} was JDK-typed for exactly this reason until it was
     * ported, and a scan that only walks superclasses cannot tell that case
     * from a free choice.
     */
    private static boolean overridesJdkDeclared(Class<?> c, Method m) {
        if (m == null) {
            return false;
        }
        if (walkForDeclaration(c.getSuperclass(), m)) {
            return true;
        }
        for (Class<?> i : c.getInterfaces()) {
            if (walkForDeclaration(i, m)) {
                return true;
            }
        }
        return false;
    }

    private static boolean walkForDeclaration(Class<?> t, Method m) {
        if (t == null) {
            return false;
        }
        if (declaredByJdkType(t, m)) {
            return true;
        }
        if (walkForDeclaration(t.getSuperclass(), m)) {
            return true;
        }
        for (Class<?> i : t.getInterfaces()) {
            if (walkForDeclaration(i, m)) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaredByJdkType(Class<?> t, Method m) {
        if (!t.getName().startsWith("java.") && !t.getName().startsWith("javax.")) {
            return false;
        }
        return Arrays.stream(t.getDeclaredMethods())
                .anyMatch(d -> d.getName().equals(m.getName())
                        && Arrays.equals(d.getParameterTypes(), m.getParameterTypes()));
    }

    /**
     * Public and protected methods and fields whose signature mentions a
     * {@code com.vaadin} type.
     *
     * <p>Reads the <em>generic</em> types, so a {@code List<com.vaadin…>} counts.
     * Raw-type inspection (what a bytecode-level tool sees) would
     * miss {@code EHelper.getDialogs}, which is exactly the shape that hides
     * a Vaadin type inside a JDK-looking signature.
     */
    private static List<Hit> publicSurfaceMentioningVaadin(Class<?> c) {
        List<Hit> out = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            // Bridge/synthetic members are the compiler's, not ours to judge.
            if (m.isSynthetic() || m.isBridge() || !isVisibleApi(m.getModifiers())) {
                continue;
            }
            String sig = genericSignature(m);
            if (sig.contains("com.vaadin")) {
                out.add(new Hit(m.getName(), sig, List.of(), m));
            }
        }
        for (Field f : c.getDeclaredFields()) {
            if (f.isSynthetic() || !isVisibleApi(f.getModifiers())) {
                continue;
            }
            String sig = f.getGenericType().getTypeName();
            if (sig.contains("com.vaadin")) {
                out.add(new Hit(f.getName(), sig, List.of(), null));
            }
        }
        // Constructors are deliberately out of scope: the protected
        // `(com.vaadin.flow.component.Component peer)` ctor *is* D_peer_ctor_injection's peer
        // seam, sanctioned by R_leaf_peer_lockdown as the way a subclass supplies its peer.
        return out;
    }

    private static boolean isVisibleApi(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String genericSignature(Method m) {
        return m.getGenericReturnType().getTypeName() + " ("
                + Arrays.stream(m.getGenericParameterTypes())
                        .map(Type::getTypeName).collect(Collectors.joining(", "))
                + ")";
    }

    /** The JDK class this emulator emulates, or null when SB-Emulators invented the class. */
    private static Class<?> jdkCounterpart(Class<?> c) {
        String name = c.getName();
        for (PackageMapping p : JDK_PACKAGE) {
            if (name.startsWith(p.vaadinx() + ".")) {
                return loadOrNull(p.jdk() + name.substring(p.vaadinx().length()));
            }
        }
        return null;
    }

    private static Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, R12ProvenanceTest.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean declaresMemberNamed(Class<?> c, String name) {
        boolean here = Arrays.stream(c.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals(name) && isVisibleApi(m.getModifiers()))
                || Arrays.stream(c.getDeclaredFields())
                .anyMatch(f -> f.getName().equals(name) && isVisibleApi(f.getModifiers()));
        // Walk up: an inherited JDK hook is still the JDK's signature —
        // JComponent.paramString is Component's, and overriding it in an
        // emulator subclass makes it no less JDK-shaped.
        return here || (c.getSuperclass() != null && declaresMemberNamed(c.getSuperclass(), name));
    }

    /**
     * Every {@code vaadinx.**} class in the module under test, loaded from whichever
     * classpath entry produced {@link EHelper} so this works from a directory build
     * and from a jar alike.
     */
    private static List<Class<?>> emulatorClasses() throws IOException {
        File dir;
        try {
            dir = new File(EHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("unreadable classpath entry for :emulators", e);
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("expected a directory classpath entry for :emulators, got " + dir);
        }
        Path base = dir.toPath();
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> toClassName(base.relativize(p)))
                    .filter(n -> n.startsWith("vaadinx."))
                    .map(R12ProvenanceTest::loadOrNull)
                    .filter(c -> c != null)
                    .collect(Collectors.toList());
        }
    }

    private static String toClassName(Path relative) {
        String s = relative.toString();
        return s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}
