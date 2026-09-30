/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.UIManager
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

/**
 * Port of {@link javax.swing.UIManager}. Path 2 (external) of the
 * icon-rendering plan: a
 * hand-picked lookup table maps the {@code OptionPane.*} icon keys
 * to {@link com.vaadin.flow.component.icon.VaadinIcon} glyphs via
 * {@link VaadinIconAdapter}, so dialog-heavy migrated code that pulls
 * icons by key (e.g.
 * {@code JOptionPane.showMessageDialog(parent, msg, "title", PLAIN_MESSAGE,
 * UIManager.getIcon("OptionPane.errorIcon"))}) reaches the same Vaadin
 * glyph the {@link JOptionPane} static-factory path picks internally.
 *
 * <p>L&amp;F machinery is permanently out of scope per R_match_swing_errors sub-bucket (b);
 * {@code UIDefaults}, {@code LookAndFeel} install/lookup, and the
 * {@code addPropertyChangeListener} surface aren't ported. The other
 * typed-getter family ({@link #getColor}, {@link #getFont}, ...) compiles
 * for import-swap but stubs through {@link vaadinx.EHelper#onUnimplemented}
 * so call sites surface in migration logs rather than fail to compile.
 *
 * <p>Unknown icon keys log via {@link vaadinx.EHelper#onUnimplemented} and
 * return {@code null}, matching the JDK contract that
 * {@code UIManager.getIcon} may return null for un-installed keys.
 *
 * <p>The supported key set today is the four {@code OptionPane.*}
 * messageType icons. Adding more keys is purely additive — wire a new
 * entry in {@link #ICON_KEYS} when a future component needs
 * the L&amp;F-key shape (e.g. {@code FileChooser.directoryIcon},
 * {@code Tree.openIcon}). Doing so preemptively violates R_infra_not_surface.
 */
public class UIManager implements java.io.Serializable {

    /**
     * Hand-picked key → glyph table. Keys mirror the JDK
     * {@code BasicLookAndFeel} {@code OptionPane.*} entries; values
     * are the same {@link com.vaadin.flow.component.icon.VaadinIcon}
     * glyphs {@link JOptionPane} picks internally for each
     * messageType — so Path 2 internal (JOptionPane's own dispatch)
     * and Path 2 external (this table) render the same icon for the
     * same semantic message.
     */
    private static final java.util.Map<Object, vaadinx.swing.Icon> ICON_KEYS = java.util.Map.of(
            "OptionPane.errorIcon",
            new VaadinIconAdapter(com.vaadin.flow.component.icon.VaadinIcon.EXCLAMATION_CIRCLE),
            "OptionPane.informationIcon",
            new VaadinIconAdapter(com.vaadin.flow.component.icon.VaadinIcon.INFO_CIRCLE),
            "OptionPane.warningIcon",
            new VaadinIconAdapter(com.vaadin.flow.component.icon.VaadinIcon.WARNING),
            "OptionPane.questionIcon",
            new VaadinIconAdapter(com.vaadin.flow.component.icon.VaadinIcon.QUESTION_CIRCLE));

    /** Public no-arg ctor, mirroring JDK shape. UIManager is used statically; no instance state. */
    public UIManager() {}

    public static vaadinx.swing.Icon getIcon(Object key) {
        vaadinx.swing.Icon hit = ICON_KEYS.get(key);
        if (hit != null) return hit;
        vaadinx.EHelper.onUnimplemented("UIManager", "getIcon", key);
        return null;
    }

    public static vaadinx.swing.Icon getIcon(Object key, java.util.Locale locale) {
        return getIcon(key);
    }

    public static Object get(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "get", key);
        return null;
    }

    public static Object get(Object key, java.util.Locale locale) {
        return get(key);
    }

    public static String getString(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getString", key);
        return null;
    }

    public static String getString(Object key, java.util.Locale locale) {
        return getString(key);
    }

    public static int getInt(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getInt", key);
        return 0;
    }

    public static int getInt(Object key, java.util.Locale locale) {
        return getInt(key);
    }

    public static boolean getBoolean(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getBoolean", key);
        return false;
    }

    public static boolean getBoolean(Object key, java.util.Locale locale) {
        return getBoolean(key);
    }

    /**
     * Answers from {@link LookAndFeelDefaults}' Metal table for the colour keys
     * it covers, and keeps the WARN for the rest.
     *
     * <p>The returned instance is a {@code ColorUIResource}, which is
     * load-bearing rather than incidental: passing it straight back into
     * {@code setBackground} is how a migrated app says "reset me to the
     * default", and the marker is what makes that render as the theme's colour
     * instead of as a pinned literal (D_theme_is_lookandfeel).
     */
    public static java.awt.Color getColor(Object key) {
        Object hit = LookAndFeelDefaults.get(key);
        if (hit instanceof java.awt.Color color) return color;
        vaadinx.EHelper.onUnimplemented("UIManager", "getColor", key);
        return null;
    }

    public static java.awt.Color getColor(Object key, java.util.Locale locale) {
        return getColor(key);
    }

    /**
     * What {@code getColor("control")} answers — Metal's {@code #EEEEEE}, as a
     * {@code ColorUIResource}.
     *
     * <p>Exists so a window's {@code frameInit} can run the JDK's
     * {@code setBackground(UIManager.getColor("control"))} edge without
     * WARNing at itself once per window constructed, which every
     * Sampler {@code WarnInventoryTest} would fail on. Now that
     * {@link #getColor} answers this key from the table the two agree,
     * and the separate method is kept only so that edge stays silent.
     */
    static java.awt.Color controlColor() {
        return (java.awt.Color) LookAndFeelDefaults.get("control");
    }

    public static java.awt.Font getFont(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getFont", key);
        return null;
    }

    public static java.awt.Font getFont(Object key, java.util.Locale locale) {
        return getFont(key);
    }

    public static java.awt.Insets getInsets(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getInsets", key);
        return null;
    }

    public static java.awt.Insets getInsets(Object key, java.util.Locale locale) {
        return getInsets(key);
    }

    public static java.awt.Dimension getDimension(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getDimension", key);
        return null;
    }

    public static java.awt.Dimension getDimension(Object key, java.util.Locale locale) {
        return getDimension(key);
    }

    public static vaadinx.swing.border.Border getBorder(Object key) {
        vaadinx.EHelper.onUnimplemented("UIManager", "getBorder", key);
        return null;
    }

    public static vaadinx.swing.border.Border getBorder(Object key, java.util.Locale locale) {
        return getBorder(key);
    }

    public static Object put(Object key, Object value) {
        vaadinx.EHelper.onUnimplemented("UIManager", "put", key, value);
        return null;
    }

    public static String getLookAndFeel() {
        vaadinx.EHelper.onUnimplemented("UIManager", "getLookAndFeel");
        return null;
    }

    public static void setLookAndFeel(String className) {
        vaadinx.EHelper.onUnimplemented("UIManager", "setLookAndFeel", className);
    }

    public static String getSystemLookAndFeelClassName() {
        vaadinx.EHelper.onUnimplemented("UIManager", "getSystemLookAndFeelClassName");
        return null;
    }

    public static String getCrossPlatformLookAndFeelClassName() {
        vaadinx.EHelper.onUnimplemented("UIManager", "getCrossPlatformLookAndFeelClassName");
        return null;
    }
}
