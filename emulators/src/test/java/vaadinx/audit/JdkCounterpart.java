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

import java.util.List;

/**
 * The one mapping from an emulator's name to the JDK class it emulates —
 * {@code vaadinx.awt.Button} → {@code java.awt.Button}, longest package prefix first.
 *
 * <p>Two unrelated gates rest on this map and must not disagree about it:
 * {@link JdkProvenance} uses it to find the signatures the return-value audit diffs, and
 * {@code vaadinx.LicenseHeaderTest} uses it to decide which files carry Oracle's copyright
 * header. A second copy of the four package pairs would let a file be JDK-derived for one
 * gate and Vaadin-authored for the other, which is a licence bug rather than a test bug —
 * hence one class, deliberately public, rather than a private constant in each.
 *
 * <p>Name-based as well as class-based, because the licence gate walks <em>files</em>: it has
 * a path and no loaded class, and a file whose class fails to load must not silently read as
 * "SB-Emulators invented this".
 */
public final class JdkCounterpart {

    private JdkCounterpart() {
    }

    /** One emulated package root and the JDK package it maps onto. */
    private record PackageMapping(String vaadinx, String jdk) {
    }

    /** Longest prefix first, so {@code vaadinx.util.prefs} beats {@code vaadinx.util}. */
    private static final List<PackageMapping> JDK_PACKAGE = List.of(
            new PackageMapping("vaadinx.awt", "java.awt"),
            new PackageMapping("vaadinx.swing", "javax.swing"),
            new PackageMapping("vaadinx.util", "java.util"),
            new PackageMapping("vaadinx.text", "java.text"));

    /**
     * The JDK class name {@code vaadinxName} maps onto, or {@code null} when it sits in no
     * emulated package. A non-null answer says only that the name maps — not that the class
     * exists; {@link #of(String)} answers that.
     */
    public static String mappedName(String vaadinxName) {
        for (PackageMapping p : JDK_PACKAGE) {
            if (vaadinxName.startsWith(p.vaadinx() + ".")) {
                return p.jdk() + vaadinxName.substring(p.vaadinx().length());
            }
        }
        return null;
    }

    /** The JDK class this emulator emulates, or {@code null} when SB-Emulators invented the class. */
    public static Class<?> of(Class<?> c) {
        return of(c.getName());
    }

    /** As {@link #of(Class)}, from a name — for callers holding a file path rather than a class. */
    public static Class<?> of(String vaadinxName) {
        String jdk = mappedName(vaadinxName);
        return jdk == null ? null : loadOrNull(jdk);
    }

    /** {@code Class.forName} without initialization, {@code null} rather than throwing. */
    public static Class<?> loadOrNull(String name) {
        try {
            return Class.forName(name, false, JdkCounterpart.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }
}
