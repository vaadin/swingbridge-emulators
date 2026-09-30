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

import javax.swing.plaf.ColorUIResource;

import java.util.HashMap;
import java.util.Map;

/**
 * The colour half of a Look &amp; Feel, as a constant table — Metal's values,
 * installed the way {@code LookAndFeel.installColorsAndFont} installs them, per
 * <a href="../../../../../../../emulators/decisions.md#D_theme_is_lookandfeel">D_theme_is_lookandfeel</a>.
 *
 * <p>Values are real {@link ColorUIResource} instances, and that type is the
 * whole point: the marker is what lets the renderer tell an L&amp;F default it may
 * replace with a theme token from a colour the app actually asked for. An
 * app-set {@code new Color(238,238,238)} is a plain {@code Color} and renders
 * literally; the identical value arriving from here does not.
 *
 * <p>Metal rather than GTK because Metal is the JDK's own cross-platform default
 * on every platform including Linux — GTK engages only on an explicit
 * {@code setLookAndFeel(getSystemLookAndFeelClassName())} and draws its values
 * from the user's desktop theme, which is neither deterministic nor testable.
 *
 * <p>This is emulation of observable app state, not L&amp;F <em>dispatch</em>:
 * pluggable {@code ComponentUI} delegates, {@code updateUI} swapping renderers
 * and {@code setLookAndFeel} stay permanently out of scope per
 * R_match_swing_errors sub-bucket (b).
 *
 * <p>Keys follow the JDK's own {@code "Panel.background"} shape, so
 * {@link UIManager#getColor} answers from the same table a component installs
 * from and a migrator reading an L&amp;F key by hand gets the value their Swing
 * app saw.
 */
final class LookAndFeelDefaults {

    /** Metal's {@code "control"} — the chrome grey behind nearly every component. */
    private static final ColorUIResource CONTROL = new ColorUIResource(238, 238, 238);

    /** Metal's data-surface white: text fields, tables, lists, trees, the desktop. */
    private static final ColorUIResource WINDOW = new ColorUIResource(255, 255, 255);

    /**
     * Metal's near-black text colour. The JDK's own value is a
     * {@code sun.swing.PrintColorUIResource}, which is not instantiable from
     * here; it extends {@code ColorUIResource}, so the marker test is unaffected
     * and a plain one carries the same RGB.
     */
    private static final ColorUIResource TEXT = new ColorUIResource(51, 51, 51);

    /**
     * Metal's slider/progress accent. Not a text colour: on {@code JProgressBar}
     * this is the filled bar and on {@code JSlider} the filled track, which is
     * why neither may route its foreground through the generic CSS {@code color}
     * write.
     */
    private static final ColorUIResource ACCENT = new ColorUIResource(163, 184, 204);

    private static final Map<Object, Object> DEFAULTS = new HashMap<>();

    /**
     * Emulator class simple name → the L&amp;F key prefix its UI delegate would
     * use. The JDK derives this inside each {@code BasicXxxUI}; reproducing it
     * as a table avoids inventing a naming rule that would silently mis-key a
     * class whose prefix does not match its type name.
     */
    private static final Map<String, String> PREFIXES = new HashMap<>();

    static {
        // Chrome group — Metal's "control" grey.
        control("Panel", "Button", "CheckBox", "RadioButton", "ToggleButton", "Viewport",
                "ScrollPane", "ComboBox", "Spinner", "SplitPane", "ToolBar", "MenuBar",
                "MenuItem", "Menu", "PopupMenu", "ScrollBar", "InternalFrame", "OptionPane",
                "Label", "TableHeader");
        // Data surfaces — Metal's "window" white.
        window("TextField", "PasswordField", "FormattedTextField", "TextArea", "EditorPane",
                "TextPane", "Table", "List", "Tree", "DesktopPane");
        // The two whose foreground is a fill, not text.
        DEFAULTS.put("Slider.background", CONTROL);
        DEFAULTS.put("Slider.foreground", ACCENT);
        DEFAULTS.put("ProgressBar.background", CONTROL);
        DEFAULTS.put("ProgressBar.foreground", ACCENT);
        DEFAULTS.put("Separator.background", WINDOW);
        DEFAULTS.put("Separator.foreground", new ColorUIResource(99, 130, 191));
        DEFAULTS.put("TabbedPane.background", new ColorUIResource(184, 207, 229));
        DEFAULTS.put("TabbedPane.foreground", TEXT);
        // Bare L&F keys a migrator may read directly.
        DEFAULTS.put("control", CONTROL);
        DEFAULTS.put("window", WINDOW);
        DEFAULTS.put("controlText", TEXT);
        DEFAULTS.put("textText", TEXT);

        // Metal's opaque defaults. Absent means false — JComponent's own default.
        opaque("Panel", "Button", "CheckBox", "RadioButton", "ToggleButton", "Viewport",
                "ScrollPane", "ComboBox", "Slider", "ProgressBar", "Spinner", "SplitPane",
                "ToolBar", "MenuBar", "MenuItem", "PopupMenu", "ScrollBar", "InternalFrame",
                "OptionPane", "TextField", "PasswordField", "FormattedTextField", "TextArea",
                "EditorPane", "TextPane", "Table", "List", "Tree", "DesktopPane", "TableHeader");
        // Measured non-opaque under Metal, and each is load-bearing: a JLabel with a
        // background set paints nothing, which is the paper cut R_no_silent_improvements
        // says to reproduce rather than fix.
        //   Label, Menu, TabbedPane, Separator  -> false (colours installed, never painted)
        //   LayeredPane, RootPane, Box          -> false, and no colours installed at all

        prefix("JPanel", "Panel");
        prefix("JButton", "Button");
        prefix("JCheckBox", "CheckBox");
        prefix("JRadioButton", "RadioButton");
        prefix("JToggleButton", "ToggleButton");
        prefix("JLabel", "Label");
        prefix("JViewport", "Viewport");
        prefix("JScrollPane", "ScrollPane");
        prefix("JScrollBar", "ScrollBar");
        prefix("JComboBox", "ComboBox");
        prefix("JSlider", "Slider");
        prefix("JProgressBar", "ProgressBar");
        prefix("JSpinner", "Spinner");
        prefix("JSplitPane", "SplitPane");
        prefix("JTabbedPane", "TabbedPane");
        prefix("JToolBar", "ToolBar");
        prefix("JMenuBar", "MenuBar");
        prefix("JMenuItem", "MenuItem");
        prefix("JMenu", "Menu");
        prefix("JCheckBoxMenuItem", "CheckBoxMenuItem");
        prefix("JRadioButtonMenuItem", "RadioButtonMenuItem");
        prefix("JPopupMenu", "PopupMenu");
        prefix("JSeparator", "Separator");
        prefix("JInternalFrame", "InternalFrame");
        prefix("JOptionPane", "OptionPane");
        prefix("JDesktopPane", "DesktopPane");
        prefix("JTextField", "TextField");
        prefix("JPasswordField", "PasswordField");
        prefix("JFormattedTextField", "FormattedTextField");
        prefix("JTextArea", "TextArea");
        prefix("JEditorPane", "EditorPane");
        prefix("JTextPane", "TextPane");
        prefix("JTable", "Table");
        prefix("JTableHeader", "TableHeader");
        prefix("JList", "List");
        prefix("JTree", "Tree");
    }

    private LookAndFeelDefaults() {}

    private static void control(String... prefixes) {
        for (String p : prefixes) {
            DEFAULTS.put(p + ".background", CONTROL);
            DEFAULTS.put(p + ".foreground", TEXT);
        }
    }

    private static void window(String... prefixes) {
        for (String p : prefixes) {
            DEFAULTS.put(p + ".background", WINDOW);
            DEFAULTS.put(p + ".foreground", TEXT);
        }
    }

    private static void opaque(String... prefixes) {
        for (String p : prefixes) {
            DEFAULTS.put(p + ".opaque", Boolean.TRUE);
        }
    }

    private static void prefix(String simpleName, String key) {
        PREFIXES.put(simpleName, key);
    }

    /** The raw table, for {@link UIManager}'s typed getters. Returns null for an unknown key. */
    static Object get(Object key) {
        return DEFAULTS.get(key);
    }

    /**
     * The L&amp;F key prefix for {@code type}, found by walking up the emulator
     * hierarchy the way the JDK falls back to the nearest ancestor that has a UI
     * delegate — so a migrator's {@code class MyPanel extends JPanel} installs
     * {@code Panel}'s colours rather than none.
     *
     * @return null when nothing in the chain has a prefix, in which case no
     *         colours are installed and {@code getBackground()} keeps walking to
     *         the parent as {@code java.awt.Component} does
     */
    static String prefixFor(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            String hit = PREFIXES.get(c.getSimpleName());
            if (hit != null) return hit;
        }
        return null;
    }

    static java.awt.Color background(Class<?> type) {
        String p = prefixFor(type);
        return p == null ? null : (java.awt.Color) DEFAULTS.get(p + ".background");
    }

    static java.awt.Color foreground(Class<?> type) {
        String p = prefixFor(type);
        return p == null ? null : (java.awt.Color) DEFAULTS.get(p + ".foreground");
    }

    static boolean opaque(Class<?> type) {
        String p = prefixFor(type);
        return p != null && Boolean.TRUE.equals(DEFAULTS.get(p + ".opaque"));
    }
}
