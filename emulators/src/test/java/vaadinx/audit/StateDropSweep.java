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

import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import vaadinx.EHelper;

/**
 * Finds emulator setters that shadow a JDK signature, WARN, and then store the value
 * <em>nowhere</em> — while a shadowed reader sits beside them answering a constant.
 * R_decline_effect_only lets the effect go and not the state, so each row is a getter
 * that lies about what was set.
 *
 * <pre>
 * ./mvnw -C -pl emulators test-compile
 * ./mvnw -C -pl emulators exec:exec -Daudit.main=vaadinx.audit.StateDropSweep
 * </pre>
 *
 * <p>Report-only and exits 0, like {@link JdkReturnValueDiffer}, whose static counterpart
 * this is. The differ is the better instrument where it reaches — it compares against a real
 * JDK rather than against a rule — but it can only test what it can construct and drive, and
 * 208 pairs are currently out of its reach for want of a probe value or a comparable return
 * type. This reads bytecode instead, so it sees all of them.
 *
 * <p>Rows are grouped, and the two large families are called out because they are policy
 * rather than bugs: {@code setUI} is L&amp;F dispatch (R_match_swing_errors sub-bucket (b))
 * and the focus managers are D_focus_managers' declared exclusions. What is left after those
 * is the worklist.
 *
 * <p>A setter that WARNs may still store the value and be warning about the effect
 * alone, which is correct and common — so warning is not the signal. Writing no
 * field <em>and</em> delegating to no other setter is.
 */
public final class StateDropSweep {

    private static final Set<String> WARN_HELPERS =
            Set.of("com.vaadin.swingbridge.surrogates.SHelper", "vaadinx.EHelper");

    /** Families that are declared policy, not findings — reported apart so they stop drowning the rest. */
    private record Family(String name, java.util.function.Predicate<String> matches, String reason) {
    }

    private static final List<Family> FAMILIES = List.of(
            new Family("L&F dispatch", n -> n.startsWith("setUI") || n.equals("setLookAndFeel")
                    || n.equals("setDefaultLookAndFeelDecorated"),
                    "R_match_swing_errors sub-bucket (b) — L&F dispatch is permanently out of scope"),
            new Family("focus management", n -> n.startsWith("setGlobal")
                    || n.equals("setCurrentManager") || n.equals("setCurrentKeyboardFocusManager"),
                    "D_focus_managers — the managers are stateless facades over a per-UI focus pointer"),
            new Family("geometry", n -> n.equals("setBounds") || n.equals("setLocation")
                    || n.equals("setSize") || n.equals("setShape"),
                    "R_layouts_close_enough — dummy bounds are stated policy (D_pixel_layout_not_planned)"));

    public static void main(String[] args) throws IOException {
        JavaClasses classes = new ClassFileImporter().importUrl(codeSource());

        Map<String, List<String>> findings = new TreeMap<>();
        Map<String, List<String>> families = new TreeMap<>();
        int warnSetters = 0;

        for (JavaClass c : classes) {
            if (!c.getPackageName().startsWith("vaadinx")) {
                continue;
            }
            Class<?> jdk = JdkProvenance.jdkCounterpart(loadOrNull(c.getName()));
            if (jdk == null) {
                continue;
            }
            Set<String> readable = shadowedReadableProperties(c, jdk);
            for (JavaMethod m : c.getMethods()) {
                if (!m.getName().startsWith("set") || m.getRawParameterTypes().size() != 1) {
                    continue;
                }
                String property = JdkProvenance.property(m.getName());
                if (property == null || !readable.contains(property)
                        || !declaresName(jdk, m.getName())) {
                    continue;
                }
                if (m.getMethodCallsFromSelf().stream().noneMatch(call -> isWarnHelper(call.getTarget()))) {
                    continue;
                }
                warnSetters++;
                if (storesTheValue(m)) {
                    continue;
                }
                String row = m.getName() + "(" + m.getRawParameterTypes().get(0).getSimpleName() + ")";
                Family family = FAMILIES.stream().filter(f -> f.matches().test(m.getName()))
                        .findFirst().orElse(null);
                (family == null ? findings : families)
                        .computeIfAbsent(family == null ? c.getSimpleName()
                                : family.name() + " — " + family.reason(), k -> new ArrayList<>())
                        .add(c.getSimpleName() + "." + row);
            }
        }

        System.out.println("# Emulator state-drop sweep\n");
        System.out.println("shadowed setters that WARN                    : " + warnSetters);
        System.out.println("... storing the value nowhere                 : "
                + (count(findings) + count(families)));
        System.out.println("... of those, declared policy                 : " + count(families));
        System.out.println("... **worklist**                              : " + count(findings) + "\n");

        System.out.println("## Worklist — a shadowed reader answers a constant here\n");
        findings.forEach((owner, rows) -> {
            System.out.println("### " + owner);
            rows.stream().sorted().forEach(r -> System.out.println("- `" + r + "`"));
            System.out.println();
        });

        System.out.println("## Declared policy, not findings\n");
        families.forEach((owner, rows) -> {
            System.out.println("### " + owner);
            rows.stream().sorted().forEach(r -> System.out.println("- `" + r + "`"));
            System.out.println();
        });
    }

    private static int count(Map<String, List<String>> m) {
        return m.values().stream().mapToInt(List::size).sum();
    }

    /** Properties this class exposes a JDK-shadowing zero-arg reader for. */
    private static Set<String> shadowedReadableProperties(JavaClass c, Class<?> jdk) {
        Set<String> out = new HashSet<>();
        for (JavaMethod m : c.getMethods()) {
            if (!m.getRawParameterTypes().isEmpty() || m.getRawReturnType().getName().equals("void")) {
                continue;
            }
            String property = JdkProvenance.property(m.getName());
            if (property != null && declaresName(jdk, m.getName())) {
                out.add(property);
            }
        }
        return out;
    }

    /**
     * True when the value survives the call — written to a field, handed to another
     * setter whose own row covers it, or put in the client-property map, which is
     * where the JDK itself keeps several properties ({@code setNextFocusableComponent}).
     */
    private static boolean storesTheValue(JavaMethod m) {
        boolean writesAField = m.getFieldAccesses().stream()
                .anyMatch(fa -> fa.getAccessType() == JavaFieldAccess.AccessType.SET);
        boolean delegates = m.getMethodCallsFromSelf().stream()
                .anyMatch(call -> (call.getTarget().getName().startsWith("set")
                        || call.getTarget().getName().equals("putClientProperty"))
                        && !isWarnHelper(call.getTarget()));
        return writesAField || delegates;
    }

    private static boolean isWarnHelper(MethodCallTarget t) {
        return WARN_HELPERS.contains(t.getOwner().getName())
                && (t.getName().equals("onUnimplemented") || t.getName().equals("onNoop"));
    }

    private static boolean declaresName(Class<?> jdk, String method) {
        for (Class<?> t = jdk; t != null; t = t.getSuperclass()) {
            for (java.lang.reflect.Method m : t.getDeclaredMethods()) {
                if (m.getName().equals(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, StateDropSweep.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    /** The classpath entry {@link EHelper} came from — a directory in a Maven build, a jar in a consumer's. */
    private static java.net.URL codeSource() throws IOException {
        java.net.URL url = EHelper.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            Path path = Path.of(url.toURI());
            if (!Files.exists(path)) {
                throw new IllegalStateException("no such classpath entry for :emulators: " + path);
            }
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("unreadable classpath entry for :emulators", e);
        }
        return url;
    }

    private StateDropSweep() {
    }
}
