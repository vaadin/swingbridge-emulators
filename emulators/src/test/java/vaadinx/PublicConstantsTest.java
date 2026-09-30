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
import java.lang.reflect.Field;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every {@code static final} constant a JDK class declares <em>or inherits</em> exists on its
 * emulator, with the same value — inherited ones included because the compiler resolves
 * {@code SwingUtilities.CENTER} and {@code JInternalFrame.EXIT_ON_CLOSE} through the interface the
 * JDK class implements, and an emulator that dropped that interface loses them all at once.
 *
 * <p>The gap this closes is invisible to every other test in the suite, because an
 * absent constant cannot be exercised: {@code AbstractButton.TEXT_CHANGED_PROPERTY},
 * {@code Frame.MAXIMIZED_BOTH} and {@code Component.CENTER_ALIGNMENT} were all missing while
 * the whole suite stayed green, and the failure lands as a compile error in the
 * <em>migrator's</em> tree after the import swap. SB-Emulators' own bodies reach for
 * {@code java.awt.Frame.ICONIFIED}, which is exactly why nothing here noticed. See
 * D_missing_constants in {@code emulators/decisions.md}.
 *
 * <p>Modelled on {@code R12ProvenanceTest}, including its allow-list-free stance: the pairs
 * are computed by mapping {@code vaadinx.awt.Foo} onto {@code java.awt.Foo}, so a new emulator
 * joins the gate by existing. {@link #SANCTIONED_OMISSIONS} is the one concession — a
 * short list, each entry a constant that <em>cannot</em> exist here rather than one that
 * merely does not.
 */
class PublicConstantsTest {

    /** Emulated package roots, longest prefix first. */
    private static final Map<String, String> JDK_PACKAGE = new LinkedHashMap<>();

    static {
        JDK_PACKAGE.put("vaadinx.awt", "java.awt");
        JDK_PACKAGE.put("vaadinx.swing", "javax.swing");
        JDK_PACKAGE.put("vaadinx.util", "java.util");
    }

    /**
     * Constants the JDK declares that SB-Emulators deliberately does not, {@code Class.field} to
     * reason. Kept minimal on purpose: a growing list here means the gate is being
     * argued with rather than satisfied.
     */
    private static final Map<String, String> SANCTIONED_OMISSIONS = Map.of(
            // Both are the JDK's private image-loading rig: a headless AWT Component
            // built solely to construct the MediaTracker that ImageIcon blocks on while
            // a GIF decodes. SB-Emulators loads images through the browser, ports no
            // MediaTracker, and per R_no_vaadin_in_api limb 1 would have to type `component` as
            // vaadinx.awt.Component — for which there is nothing to construct.
            "ImageIcon.component", "JDK image-loading internals; no MediaTracker port",
            "ImageIcon.tracker", "JDK image-loading internals; no MediaTracker port");

    @Test
    @DisplayName("every JDK static final constant exists on its emulator with the same value")
    void everyJdkStaticFinalConstantExistsOnItsEmulatorWithTheSameValue() {
        List<Class<?>> classes = emulatorClasses();
        // Guard the guard: a silent zero-class scan would pass forever.
        assertTrue(classes.size() > 200, "only " + classes.size() + " vaadinx classes scanned — scan is broken");

        List<String> missing = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        int checked = 0;
        int unreadable = 0;
        Set<String> sanctioned = new LinkedHashSet<>();

        for (Class<?> c : classes) {
            Class<?> jdk = jdkCounterpart(c);
            if (jdk == null) {
                continue;
            }
            for (Field jf : jdkConstants(jdk)) {
                String key = jdk.getSimpleName() + "." + jf.getName();
                if (SANCTIONED_OMISSIONS.containsKey(key)) {
                    sanctioned.add(key);
                    continue;
                }
                Field sf = findConstant(c, jf.getName());
                if (sf == null) {
                    missing.add(c.getName() + "." + jf.getName() + " : " + jf.getType().getSimpleName()
                            + "\n        " + jdk.getName()
                            + (jf.getDeclaringClass() == jdk
                                    ? " declares it"
                                    : " inherits it from " + jf.getDeclaringClass().getName()
                                            + " — implement that interface rather than re-declaring the constants")
                            + ", so an import-swapped call site names it and will not compile");
                    continue;
                }
                checked++;
                // Reading a *protected* JDK field would need the java.desktop
                // package opened, which the build does not do — presence is all
                // this gate can assert for those. Public ones carry their value.
                Object jv = readStatic(jf);
                Object sv = readStatic(sf);
                if (jv == null || sv == null) {
                    unreadable++;
                } else if (!sameValue(jv, sv)) {
                    mismatched.add(c.getName() + "." + jf.getName() + " = " + sv
                            + ", but " + jdk.getName() + "." + jf.getName() + " = " + jv);
                }
            }
        }

        System.out.println(
                "D_missing_constants constants gate: " + checked + " constant(s) matched "
                        + "(" + unreadable + " presence-only — protected, unreadable without --add-opens), "
                        + sanctioned.size() + " sanctioned omission(s)");

        // A stale reason is worse than none: it claims a decision nobody made.
        Set<String> stale = new LinkedHashSet<>(SANCTIONED_OMISSIONS.keySet());
        stale.removeAll(sanctioned);
        assertTrue(stale.isEmpty(),
                "SANCTIONED_OMISSIONS names " + String.join(", ", stale)
                        + " which the JDK no longer declares — drop the entr" + (stale.size() == 1 ? "y" : "ies"));

        if (!missing.isEmpty() || !mismatched.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (!missing.isEmpty()) {
                sb.append(missing.size()).append(" JDK constant(s) absent from their emulator:\n\n");
                sb.append(missing.stream().map(it -> "    " + it).collect(Collectors.joining("\n\n")));
                sb.append("\n\n");
            }
            if (!mismatched.isEmpty()) {
                sb.append(mismatched.size()).append(" constant(s) with the wrong value:\n\n");
                sb.append(mismatched.stream().map(it -> "    " + it).collect(Collectors.joining("\n")));
                sb.append("\n\n");
            }
            sb.append("Add the constant with the JDK's own value, or — only when it genuinely cannot "
                    + "exist here — add it to SANCTIONED_OMISSIONS with a reason.");
            fail(sb.toString());
        }
    }

    /**
     * Whether the emulator's constant carries the JDK's value.
     *
     * <p>Enum constants are compared by name: {@code vaadinx.awt.Dialog.ModalityType} is a
     * <em>separate</em> enum from the JDK's, so its members can never be {@code ==} to theirs,
     * and a same-named member is as equal as the port allows. The rest compare
     * directly — that is where the off-by-one in {@code HIERARCHY_LAST} showed up.
     */
    private static boolean sameValue(Object jdkValue, Object emulatorValue) {
        if (jdkValue instanceof Enum<?> jdkEnum && emulatorValue instanceof Enum<?> emulatorEnum) {
            return jdkEnum.name().equals(emulatorEnum.name());
        }
        return jdkValue.equals(emulatorValue);
    }

    private static boolean isConstant(Field f) {
        int m = f.getModifiers();
        return Modifier.isStatic(m) && Modifier.isFinal(m)
                && (Modifier.isPublic(m) || Modifier.isProtected(m));
    }

    /**
     * The JDK class's constants as the compiler resolves them on that class: its own public and
     * protected declarations, plus every public one inherited from a superclass or interface.
     * {@code getFields()} alone would miss the protected ones; {@code getDeclaredFields()} alone
     * misses the inherited ones — a whole interface's worth at a time.
     */
    private static List<Field> jdkConstants(Class<?> jdk) {
        LinkedHashMap<String, Field> byName = new LinkedHashMap<>();
        for (Field f : jdk.getDeclaredFields()) {
            if (!f.isSynthetic() && isConstant(f)) {
                byName.put(f.getName(), f);
            }
        }
        for (Field f : jdk.getFields()) {
            if (isConstant(f)) {
                byName.putIfAbsent(f.getName(), f);
            }
        }
        return new ArrayList<>(byName.values());
    }

    /** The field's value, or null when the JVM will not let us read it unopened. */
    private static Object readStatic(Field f) {
        try {
            if (!Modifier.isPublic(f.getModifiers())) {
                f.setAccessible(true);
            }
            return f.get(null);
        } catch (RuntimeException | ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * The named constant on this class or any ancestor.
     *
     * <p>Walking up matters: {@code HTMLEditorKit} and {@code HTMLDocument} extend their JDK
     * counterparts (D_htmleditorkit / D_htmldocument) and so inherit every constant without declaring
     * one, and {@code Scrollbar} takes {@code HORIZONTAL} / {@code VERTICAL} from
     * {@code java.awt.Adjustable}. Declaration-site comparison would report all of those
     * as gaps.
     */
    private static Field findConstant(Class<?> c, String name) {
        if (c == null) {
            return null;
        }
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(name) && isConstant(f)) {
                return f;
            }
        }
        for (Class<?> i : c.getInterfaces()) {
            Field found = findConstant(i, name);
            if (found != null) {
                return found;
            }
        }
        return findConstant(c.getSuperclass(), name);
    }

    /** The JDK class this emulator emulates, or null when SB-Emulators invented the class. */
    private Class<?> jdkCounterpart(Class<?> c) {
        String name = c.getName();
        for (Map.Entry<String, String> prefix : JDK_PACKAGE.entrySet()) {
            if (name.startsWith(prefix.getKey() + ".")) {
                try {
                    return Class.forName(prefix.getValue() + name.substring(prefix.getKey().length()),
                            false, getClass().getClassLoader());
                } catch (Throwable e) {
                    return null;
                }
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
        // An explicit loop rather than a stream: `.toList()` into List<Class<?>>
        // is a capture conversion javac accepts and JDT rejects, so the stream
        // form builds under Maven and breaks the IDE.
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : walk.toList()) {
                if (!p.toString().endsWith(".class")) {
                    continue;
                }
                String name = toClassName(base.relativize(p));
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

    private static String toClassName(Path relative) {
        String s = relative.toString();
        return s.substring(0, s.length() - ".class".length()).replace(File.separatorChar, '.');
    }
}
