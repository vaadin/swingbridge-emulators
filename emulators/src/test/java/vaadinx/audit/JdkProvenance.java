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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import vaadinx.EHelper;

/**
 * Answers "which emulator methods speak the JDK's own signatures, and which of those
 * are a set-then-read pair?" — the address book {@link JdkReturnValueDiffer} walks.
 *
 * <pre>{@code
 * for (Class<?> c : JdkProvenance.emulatorClasses()) {
 *     Class<?> jdk = JdkProvenance.jdkCounterpart(c);          // null => SB-Emulators invented it
 *     for (Pair p : JdkProvenance.shadowedPairs(c, jdk)) { ... }
 * }
 * }</pre>
 *
 * <p>Provenance is <em>computed</em> — map {@code vaadinx.awt.Foo} onto {@code java.awt.Foo},
 * then ask the JDK class whether it declares a matching signature — so nothing here is a
 * list that can rot. Parameter types are mapped through {@link #toJdk} before comparison,
 * because a faithful emulator signature says {@code vaadinx.awt.Component} exactly where
 * the JDK says {@code java.awt.Component} (R_no_vaadin_in_api).
 */
final class JdkProvenance {

    private JdkProvenance() {
    }

    /**
     * A shadowed setter paired with its shadowed reader, on both layers.
     *
     * @param property the bean property name, with the {@code set}/{@code get}/{@code is} stripped
     */
    record Pair(Class<?> emulator, Class<?> jdk, String property,
                Method emulatorSetter, Method emulatorGetter,
                Method jdkSetter, Method jdkGetter) {

        /** The setter's argument type, as the JDK declares it — the key into {@link ProbeValues}. */
        Class<?> valueType() {
            return jdkSetter.getParameterTypes()[0];
        }

        /**
         * Where to go and fix it: the class that <em>declares</em> the setter, plus the
         * concrete class it was driven on when those differ — an inherited pair's bug lives
         * in the base, not in whichever subclass the sweep happened to reach it through.
         */
        String label() {
            Class<?> owner = emulatorSetter.getDeclaringClass();
            String via = owner.equals(emulator) ? "" : " (via " + emulator.getSimpleName() + ")";
            return owner.getSimpleName() + "." + property + via;
        }
    }

    /**
     * The JDK class this emulator emulates, or {@code null} when SB-Emulators invented the class.
     *
     * @see JdkCounterpart the shared map, which {@code vaadinx.LicenseHeaderTest} reads too
     */
    static Class<?> jdkCounterpart(Class<?> c) {
        return JdkCounterpart.of(c);
    }

    /** {@code t} with every emulated type replaced by its JDK original, arrays included. */
    static Class<?> toJdk(Class<?> t) {
        if (t.isArray()) {
            Class<?> element = toJdk(t.getComponentType());
            return element == t.getComponentType() ? t : Array.newInstance(element, 0).getClass();
        }
        Class<?> jdk = jdkCounterpart(t);
        return jdk != null ? jdk : t;
    }

    /**
     * True when {@code t} is a JDK type SB-Emulators leaves alone ({@code Color}, {@code Dimension},
     * {@code ActionEvent}) rather than ports, so one instance serves both layers and
     * {@code equals} compares across them.
     */
    static boolean reusedFromJdk(Class<?> t) {
        String n = t.getName();
        return (n.startsWith("java.") || n.startsWith("javax.")) && !emulatedJdkTypes().contains(n);
    }

    /**
     * Every shadowed {@code setFoo}/{@code getFoo} pair on {@code emulator}, ordered by property.
     *
     * <p>Both halves must shadow independently: an emulator-invented setter paired with a
     * JDK-shadowing getter has no JDK call to diff against, so it is not a pair.
     *
     * <p>One pair per setter <em>overload</em>, not per property. Collapsing overloads
     * to one setter is what let {@code JLabel.setDisplayedMnemonic(char)} — which
     * recursed into itself where the {@code int} form was correct — stay invisible
     * while its sibling passed. The convenience overload is where the interesting
     * divergences hide, because it is the one whose body is hand-written rather
     * than generated.
     */
    static List<Pair> shadowedPairs(Class<?> emulator, Class<?> jdk) {
        Map<String, List<Method>> setters = new LinkedHashMap<>();
        Map<String, Method> getters = new LinkedHashMap<>();
        for (Method m : effectiveApi(emulator)) {
            String property = property(m.getName());
            if (property == null || jdkShadowed(jdk, m) == null) {
                continue;
            }
            if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
                setters.computeIfAbsent(property, k -> new ArrayList<>()).add(m);
            } else if (m.getParameterCount() == 0 && m.getReturnType() != void.class) {
                getters.put(property, m);
            }
        }
        List<Pair> out = new ArrayList<>();
        for (Map.Entry<String, List<Method>> e : setters.entrySet()) {
            Method getter = getters.get(e.getKey());
            if (getter == null) {
                continue;
            }
            Method jdkGetter = jdkShadowed(jdk, getter);
            for (Method setter : e.getValue()) {
                out.add(new Pair(emulator, jdk, e.getKey(), setter, getter,
                        jdkShadowed(jdk, setter), jdkGetter));
            }
        }
        out.sort(Comparator.comparing(Pair::property).thenComparing(p -> p.valueType().getName()));
        return out;
    }

    /**
     * Every public/protected method callable on {@code emulator}, inherited ones included,
     * with a subclass override shadowing the supertype's declaration.
     *
     * <p>Declared-only was the harness's blind spot, and a wide one: the abstract bases carry
     * most of the surface — {@code Component} 71 shadowed readers, {@code JComponent} 45,
     * {@code AbstractButton} 36, {@code JTextComponent} 29 — and none of them construct, so
     * every pair they declare went untested. {@code AbstractButton.setSelected} was a no-op
     * where a real {@code JButton} answers {@code true}, and nothing could see it.
     */
    private static List<Method> effectiveApi(Class<?> emulator) {
        Map<String, Method> resolved = new LinkedHashMap<>();
        for (Class<?> t = emulator; t != null && t.getName().startsWith("vaadinx."); t = t.getSuperclass()) {
            for (Method m : t.getDeclaredMethods()) {
                if (m.isSynthetic() || m.isBridge() || !isVisibleApi(m.getModifiers())) {
                    continue;
                }
                // First writer wins, walking down-up, so an override beats what it overrides.
                resolved.putIfAbsent(signature(m), m);
            }
        }
        return new ArrayList<>(resolved.values());
    }

    /** Name plus erased parameter types — the identity an override shares with what it overrides. */
    static String signature(Method m) {
        return m.getName() + Arrays.stream(m.getParameterTypes())
                .map(Class::getName).collect(Collectors.joining(",", "(", ")"));
    }

    /** The JDK method {@code m} shadows, walking superclasses, or {@code null} if the JDK has no such signature. */
    static Method jdkShadowed(Class<?> jdk, Method m) {
        Class<?>[] wanted = java.util.Arrays.stream(m.getParameterTypes())
                .map(JdkProvenance::toJdk).toArray(Class[]::new);
        for (Class<?> t = jdk; t != null; t = t.getSuperclass()) {
            for (Method j : t.getDeclaredMethods()) {
                if (j.getName().equals(m.getName())
                        && isVisibleApi(j.getModifiers())
                        && java.util.Arrays.equals(j.getParameterTypes(), wanted)) {
                    return j;
                }
            }
        }
        return null;
    }

    /** The bean property {@code methodName} accesses, or {@code null} if it is not an accessor shape. */
    static String property(String methodName) {
        if (methodName.startsWith("set") || methodName.startsWith("get")) {
            return methodName.length() > 3 ? methodName.substring(3) : null;
        }
        if (methodName.startsWith("is")) {
            return methodName.length() > 2 ? methodName.substring(2) : null;
        }
        return null;
    }

    private static boolean isVisibleApi(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    /**
     * Every {@code vaadinx.**} class in {@code :emulators}, loaded from whichever classpath
     * entry produced {@link EHelper}.
     */
    static List<Class<?>> emulatorClasses() throws IOException {
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
                    .map(JdkProvenance::loadOrNull)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Class::getName))
                    .collect(Collectors.toList());
        }
    }

    private static Set<String> emulated;

    /** JDK class names an emulator resolves to — the inverse of {@link #jdkCounterpart} over the surface. */
    private static synchronized Set<String> emulatedJdkTypes() {
        if (emulated == null) {
            Set<String> out = new LinkedHashSet<>();
            try {
                for (Class<?> c : emulatorClasses()) {
                    Class<?> jdk = jdkCounterpart(c);
                    if (jdk != null) {
                        out.add(jdk.getName());
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("cannot scan :emulators to invert the type map", e);
            }
            emulated = out;
        }
        return emulated;
    }

    private static Class<?> loadOrNull(String name) {
        return JdkCounterpart.loadOrNull(name);
    }

    private static String toClassName(Path relative) {
        String s = relative.toString();
        return s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}
