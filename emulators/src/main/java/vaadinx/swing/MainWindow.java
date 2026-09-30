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

package vaadinx.swing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link JFrame} subclass as the migrated app's main window. The
 * annotated frame renders <strong>inline</strong> as the content of a
 * {@link vaadinx.swing.app.MainWindowRoute}-extending Vaadin route, peered
 * over {@link com.vaadin.swingbridge.surrogates.SJPanel} (Div-backed) instead of the regular
 * {@link com.vaadin.swingbridge.surrogates.SJFrame} (Dialog-backed). At most one
 * {@code @MainWindow}-annotated class may be instantiated per JVM —
 * detected lazily via the JFrame ctor's CAS check.
 *
 * <p>Per <a href="../../../emulators/decisions.md#D_jframe_as_route">D_jframe_as_route</a>:
 * <ul>
 *     <li>{@link Inherited} — user-code subclasses inherit the annotation
 *         from their {@code @MainWindow}-annotated parent.
 *     <li>{@link Target}{@code (TYPE)} — class-level only.
 *     <li>{@link Retention}{@code (RUNTIME)} — visible to the ctor's
 *         {@code isAnnotationPresent} check.
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * @MainWindow
 * public class MyMainFrame extends JFrame { ... }
 *
 * public static void mainUI() {
 *     new MyMainFrame().setVisible(true);
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MainWindow {
}
