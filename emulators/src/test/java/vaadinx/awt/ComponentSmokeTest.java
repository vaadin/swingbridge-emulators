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

package vaadinx.awt;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
// Single-type-import: shadows the vaadinx.awt.List emulator in this package.
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reflectively invokes every public/protected method on {@link Component} with a
 * small set of argument variants, asserting that none throw. Not a correctness
 * test — a safety net that catches NPEs, unhandled nulls, and similar
 * regressions across the whole Component surface.
 */
class ComponentSmokeTest extends AbstractKaribuTest {

    private final List<Map<Class<?>, Object>> variants = List.of(
            // Variant 0: nulls for object params, zero/false for primitives — null-handling probe.
            Map.of(),
            // Variant 1: typical values.
            Map.of(
                    String.class, "test",
                    Color.class, Color.BLUE,
                    Font.class, new Font("Dialog", Font.PLAIN, 12),
                    Dimension.class, new Dimension(100, 50),
                    Locale.class, Locale.ENGLISH,
                    Cursor.class, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                    ComponentOrientation.class, ComponentOrientation.RIGHT_TO_LEFT),
            // Variant 2: edge-case values — semi-transparent color, styled font, zero size.
            Map.of(
                    String.class, "",
                    Color.class, new Color(128, 128, 128, 128),
                    Font.class, new Font("Serif", Font.BOLD | Font.ITALIC, 24),
                    Dimension.class, new Dimension(0, 0),
                    Cursor.class, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR),
                    ComponentOrientation.class, ComponentOrientation.LEFT_TO_RIGHT));

    /**
     * SB-Emulators-invented seams, skipped here on purpose. The "survives a null argument"
     * contract mirrors the JDK's own null handling for JDK-shaped API a migrator can call;
     * a method with no JDK counterpart has no such contract to mirror, and swallowing a
     * null callback would hide a bug in SB-Emulators' own code rather than a migrator's.
     * Matched by name, since an overload of an invented name is invented too.
     */
    private static final java.util.Set<String> INVENTED_SEAMS = java.util.Set.of("withPeer", "withPeerOnLiveUI");

    @Test
    @DisplayName("every public and protected method survives invocation")
    void everyPublicAndProtectedMethodSurvivesInvocation() {
        // Attach to a parent Container with a locale set so getLocale doesn't hit
        // the orphan IllegalComponentStateException branch. D_never_fail_on_gaps allows
        // Swing's own failure modes through; the smoke test intentionally dodges them.
        Container parent = new Container();
        parent.setLocale(Locale.ENGLISH);
        Component c = new Component(new Div()) {
        };
        parent.add(c);
        List<Method> methods = Arrays.stream(Component.class.getDeclaredMethods())
                .filter(it -> Modifier.isPublic(it.getModifiers()) || Modifier.isProtected(it.getModifiers()))
                .filter(it -> !Modifier.isStatic(it.getModifiers()))
                .filter(it -> !it.isSynthetic() && !it.isBridge())
                .filter(it -> !INVENTED_SEAMS.contains(it.getName()))
                .toList();

        for (int idx = 0; idx < variants.size(); idx++) {
            Map<Class<?>, Object> variant = variants.get(idx);
            for (Method method : methods) {
                method.setAccessible(true);
                Class<?>[] paramTypes = method.getParameterTypes();
                Object[] args = new Object[paramTypes.length];
                for (int i = 0; i < args.length; i++) {
                    args[i] = argFor(paramTypes[i], variant, idx);
                }
                try {
                    method.invoke(c, args);
                } catch (InvocationTargetException e) {
                    String params = Arrays.stream(paramTypes)
                            .map(Class::getSimpleName)
                            .collect(Collectors.joining(", "));
                    throw new AssertionError(
                            "variant " + idx + ": " + method.getName() + "(" + params + ") threw " + e.getCause(),
                            e.getCause());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }

    private Object argFor(Class<?> type, Map<Class<?>, Object> variant, int variantIdx) {
        if (variant.containsKey(type)) {
            return variant.get(type);
        }
        if (type == int.class) {
            return List.of(0, 42, -1).get(variantIdx);
        }
        if (type == long.class) {
            return List.of(0L, 42L, -1L).get(variantIdx);
        }
        if (type == float.class) {
            return List.of(0f, 1f, -1f).get(variantIdx);
        }
        if (type == double.class) {
            return List.of(0.0, 1.0, -1.0).get(variantIdx);
        }
        if (type == byte.class) {
            return List.of((byte) 0, (byte) 1, (byte) -1).get(variantIdx);
        }
        if (type == short.class) {
            return List.of((short) 0, (short) 1, (short) -1).get(variantIdx);
        }
        if (type == char.class) {
            return List.of('a', 'Z', '\0').get(variantIdx);
        }
        if (type == boolean.class) {
            return List.of(false, true, false).get(variantIdx);
        }
        return null;
    }
}
