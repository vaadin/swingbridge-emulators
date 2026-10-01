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

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every public/protected <i>instance</i> field a JDK class declares is reachable on its
 * emulator, at no narrower a visibility.
 *
 * <p>The sibling gate {@code PublicConstantsTest} deliberately scoped itself to
 * {@code static final} (D_missing_constants), which left instance fields ungated — 87 JDK
 * {@code protected} fields had been narrowed to {@code private} and 4 {@code public} ones
 * were absent, all invisible to the suite. The failure mode is D_missing_constants' own:
 * SB-Emulators' bodies read SB-Emulators' <i>own</i> private copy, so the build stays green and the break
 * lands as a compile error in a migrator's subclass — the subclass R_leaf_peer_lockdown
 * promises "works fine". See D_instance_field_surface.
 *
 * <p><b>Two halves.</b> The narrowing half is allow-list-free: it speaks only about fields
 * the emulator already declares, so the state is demonstrably there and a narrower modifier
 * is a reachability bug with nothing to excuse. The absence half runs against
 * {@link #ALLOWED_ABSENT}, a reasoned allow-list of permanent sanctions (unportable type,
 * out-of-scope subsystem, flattened concept — see D_field_write_reconcile for the
 * declare-vs-sanction rule); the check is two-directional, so an entry for a field the
 * emulator meanwhile declared fails as stale. Surrogate-owned fields are declared with the
 * D_field_write_reconcile write-through + reconcile, not sanctioned.
 *
 * <p><b>Value is not checked, and cannot be.</b> An instance field has no value until
 * something is constructed, and constructing an emulator needs a Vaadin UI. Visibility is
 * the whole contract here — which is also why this is a separate class rather than a third
 * assertion bolted onto the constants gate.
 *
 * <p>Not checked either: the field's <i>type</i>. Emulator types legitimately diverge from
 * the JDK's under R_no_vaadin_in_api limb 1 ({@code vaadinx.awt.Component} where the JDK says
 * {@code java.awt.Component}), so a type comparison needs the same prefix mapping applied to
 * every type argument — worth doing, not done here.
 */
public class InstanceFieldSurfaceTest {

    /** Emulated package roots, longest prefix first. */
    private static final List<String[]> JDK_PACKAGES = List.of(
            new String[] { "vaadinx.awt", "java.awt" },
            new String[] { "vaadinx.swing", "javax.swing" },
            new String[] { "vaadinx.util", "java.util" });

    // Shared reasons, so per-field entries stay one line each.
    private static final String LNF = "L&F dispatch is permanently out of scope (R_match_swing_errors sub-bucket (b))";
    private static final String A11Y = "the accessibility subsystem is not emulated — browser a11y comes from the DOM, "
            + "not from AccessibleContext";
    private static final String VIEWPORT_PAINT = "backing-store paint internals; JViewport is an inert structural "
            + "shell — SJScrollPane owns scrolling (D_jscrollpane)";
    private static final String DESKTOP_PROPS = "the desktop-properties subsystem is not emulated (Toolkit is a "
            + "browser-facing facade, D_toolkit_full_surface)";
    private static final String INNER_PLUMBING = "JDK protected inner plumbing class/interface SB-Emulators does not port; "
            + "the machinery it forwards for is browser-owned";

    /**
     * Every JDK public/protected instance field the emulator knowingly does not declare,
     * keyed {@code emulatorClass.fieldName}, each with the reason a migrator's subclass
     * naming it does not compile. Entries citing a step-2 bucket are scheduled work, not
     * open-ended deferral — see the class doc.
     */
    private static final java.util.Map<String, String> ALLOWED_ABSENT = java.util.Map.ofEntries(
            // Permanent sanctions.
            java.util.Map.entry("vaadinx.swing.JComponent.ui", LNF),
            java.util.Map.entry("vaadinx.awt.Component.accessibleContext", A11Y),
            java.util.Map.entry("vaadinx.swing.JColorChooser.accessibleContext", A11Y),
            java.util.Map.entry("vaadinx.swing.JDialog.accessibleContext", A11Y),
            java.util.Map.entry("vaadinx.swing.JFileChooser.accessibleContext", A11Y),
            java.util.Map.entry("vaadinx.swing.JFrame.accessibleContext", A11Y),
            java.util.Map.entry("vaadinx.swing.JWindow.accessibleContext", A11Y),
            java.util.Map.entry("vaadinx.swing.JViewport.isViewSizeSet", VIEWPORT_PAINT),
            java.util.Map.entry("vaadinx.swing.JViewport.lastPaintPosition", VIEWPORT_PAINT),
            java.util.Map.entry("vaadinx.swing.JViewport.backingStore", VIEWPORT_PAINT),
            java.util.Map.entry("vaadinx.swing.JViewport.backingStoreImage", VIEWPORT_PAINT),
            java.util.Map.entry("vaadinx.swing.JViewport.scrollUnderway", VIEWPORT_PAINT),
            java.util.Map.entry("vaadinx.awt.Toolkit.desktopProperties", DESKTOP_PROPS),
            java.util.Map.entry("vaadinx.awt.Toolkit.desktopPropsSupport", DESKTOP_PROPS),
            java.util.Map.entry("vaadinx.awt.GridBagLayout.comptable",
                    "typed Hashtable<java.awt.Component,…> — R_no_vaadin_in_api limb 1 forbids the JDK type, and the "
                            + "emulator's own map is differently keyed"),
            java.util.Map.entry("vaadinx.awt.GridBagLayout.layoutInfo",
                    "GridBagLayoutInfo is package-private in java.awt — the type cannot be named at all"),
            java.util.Map.entry("vaadinx.swing.JTree.selectionRedirector", INNER_PLUMBING),
            java.util.Map.entry("vaadinx.swing.JMenu.popupListener", INNER_PLUMBING),
            java.util.Map.entry("vaadinx.swing.JTable.editorComp",
                    "faithful in-cell editor swap is permanently deferred (R_match_swing_errors sub-bucket (b), "
                            + "D_jtable_cell_editing)"));

    @Test
    public void noEmulatorNarrowsAJdkInstanceField() {
        List<Class<?>> classes = emulatorClasses();
        assertTrue(classes.size() > 200, "only " + classes.size() + " vaadinx classes scanned — scan is broken");

        List<String> narrowed = new ArrayList<>();
        int checked = 0;

        for (Class<?> c : classes) {
            Class<?> jdk = jdkCounterpart(c);
            if (jdk == null) continue;
            for (Field jf : jdk.getDeclaredFields()) {
                if (!isPortableInstanceField(jf)) continue;
                Field sf = findInstanceField(c, jf.getName());
                if (sf == null) continue; // absence is the other test's business
                checked++;
                if (visibilityRank(sf) < visibilityRank(jf)) {
                    narrowed.add(c.getName() + "." + jf.getName()
                            + "\n        " + visibilityName(jf) + " in " + jdk.getName()
                            + ", " + visibilityName(sf) + " in " + sf.getDeclaringClass().getName()
                            + "\n        a migrator's behaviour-subclass names it and will not compile");
                }
            }
        }

        System.out.println("D_instance_field_surface narrowing gate: " + checked
                + " instance field(s) carried at or above the JDK's visibility");

        if (!narrowed.isEmpty()) {
            fail(narrowed.size() + " JDK instance field(s) narrowed by their emulator:\n\n"
                    + String.join("\n\n", narrowed.stream().map(s -> "    " + s).toList())
                    + "\n\nWiden to the JDK's own visibility. The field already exists and is already "
                    + "written, so this is a modifier change, not new state — and per R_leaf_peer_lockdown "
                    + "user-code subclassing for behaviour is supported, which is what reads it.");
        }
    }


    @Test
    public void everyAbsentJdkInstanceFieldIsSanctioned() {
        List<String> unsanctioned = new ArrayList<>();
        java.util.Set<String> absentSeen = new java.util.HashSet<>();

        for (Class<?> c : emulatorClasses()) {
            Class<?> jdk = jdkCounterpart(c);
            if (jdk == null) continue;
            for (Field jf : jdk.getDeclaredFields()) {
                if (!isPortableInstanceField(jf)) continue;
                if (findInstanceField(c, jf.getName()) != null) continue;
                String key = c.getName() + "." + jf.getName();
                absentSeen.add(key);
                if (!ALLOWED_ABSENT.containsKey(key)) {
                    unsanctioned.add(key + "\n        " + visibilityName(jf) + " "
                            + jf.getGenericType().getTypeName() + " in " + jdk.getName());
                }
            }
        }

        List<String> stale = ALLOWED_ABSENT.keySet().stream()
                .filter(k -> !absentSeen.contains(k)).sorted().toList();

        System.out.println("D_instance_field_surface absence gate: " + absentSeen.size()
                + " sanctioned absence(s)");

        if (!unsanctioned.isEmpty()) {
            unsanctioned.sort(String::compareTo);
            fail(unsanctioned.size() + " JDK instance field(s) absent from their emulator without a sanction:\n\n"
                    + String.join("\n\n", unsanctioned.stream().map(s -> "    " + s).toList())
                    + "\n\nEither declare the field (assigned where the JDK assigns it — see "
                    + "D_instance_field_surface) or add an ALLOWED_ABSENT entry whose reason would "
                    + "survive D_gap_severity_triage.");
        }
        if (!stale.isEmpty()) {
            fail(stale.size() + " ALLOWED_ABSENT entr(ies) name a field the emulator now declares "
                    + "(or that no longer maps): " + stale
                    + "\n\nDelete the stale entries — the allow-list must not outlive its gaps.");
        }
    }

    /**
     * Whether this JDK field is one a migrator's subclass could name: an instance field the
     * JDK exposes to subclasses or callers.
     *
     * <p>{@code static} is excluded because {@code PublicConstantsTest} owns it — the two
     * gates stay disjoint so a failure names one owner.
     */
    private static boolean isPortableInstanceField(Field f) {
        int m = f.getModifiers();
        return !f.isSynthetic()
                && !Modifier.isStatic(m)
                && (Modifier.isPublic(m) || Modifier.isProtected(m));
    }

    private static int visibilityRank(Field f) {
        int m = f.getModifiers();
        if (Modifier.isPublic(m)) return 3;
        if (Modifier.isProtected(m)) return 2;
        if (Modifier.isPrivate(m)) return 0;
        return 1; // package-private
    }

    private static String visibilityName(Field f) {
        int m = f.getModifiers();
        if (Modifier.isPublic(m)) return "public";
        if (Modifier.isProtected(m)) return "protected";
        if (Modifier.isPrivate(m)) return "private";
        return "package-private";
    }

    /**
     * The named instance field on this class or any ancestor.
     *
     * <p>Walking up matters for the same reason it does in the constants gate:
     * {@code HTMLEditorKit} / {@code HTMLDocument} extend their JDK counterparts
     * (D_htmleditorkit / D_htmldocument) and inherit every field without declaring one, so
     * declaration-site comparison would report all of those as gaps. Interfaces are not
     * walked — an interface field is implicitly {@code static final} and belongs to the
     * constants gate.
     */
    private static Field findInstanceField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getName().equals(name) && !Modifier.isStatic(f.getModifiers())) return f;
            }
        }
        return null;
    }

    /** The JDK class this emulator emulates, or null when SB-Emulators invented the class. */
    private static Class<?> jdkCounterpart(Class<?> c) {
        String name = c.getName();
        for (String[] p : JDK_PACKAGES) {
            if (!name.startsWith(p[0] + ".")) continue;
            try {
                return Class.forName(p[1] + name.substring(p[0].length()), false,
                        InstanceFieldSurfaceTest.class.getClassLoader());
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    /** Every {@code vaadinx.**} class in the module under test. */
    private static List<Class<?>> emulatorClasses() {
        File dir;
        try {
            dir = new File(EHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IllegalStateException("cannot locate the :emulators classpath entry", e);
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("expected a directory classpath entry for :emulators, got " + dir);
        }
        Path base = dir.toPath();
        List<Class<?>> found = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> base.relativize(p).toString()
                            .replace(".class", "")
                            .replace(File.separatorChar, '.'))
                    .filter(n -> n.startsWith("vaadinx."))
                    .forEach(n -> {
                        try {
                            found.add(Class.forName(n, false, InstanceFieldSurfaceTest.class.getClassLoader()));
                        } catch (Throwable t) {
                            // A class that will not load cannot be compared; the guard-the-guard
                            // count below catches a scan that loses most of them.
                        }
                    });
        } catch (Exception e) {
            throw new IllegalStateException("cannot walk the :emulators classpath", e);
        }
        return found;
    }
}
