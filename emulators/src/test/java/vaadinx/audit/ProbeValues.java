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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The arguments {@link JdkReturnValueDiffer} feeds a shadowed setter — a per-layer pair,
 * because half of them are types SB-Emulators ports.
 *
 * <pre>{@code
 * for (ValuePair v : ProbeValues.forType(Color.class)) {
 *     jdkSetter.invoke(real, v.jdk());
 *     emulSetter.invoke(emul, v.emulator());
 * }
 * }</pre>
 *
 * <p><b>Two values per type wherever possible.</b> A single probe that happens to equal the
 * property's default proves nothing — both layers would answer correctly having stored
 * nothing at all. {@code boolean} is the sharp case: {@code true} alone passes on a
 * property defaulting to {@code true}.
 *
 * <h2>Extending this</h2>
 * A type absent from {@link #VALUES} takes its pairs out of the auditable set — the report
 * counts those as skipped, and the count is the queue. Adding a
 * {@link #reused} entry costs one line where the JDK type is shared ({@code Color},
 * {@code Dimension}); a ported type ({@code Icon}, {@code Component}, {@code JMenuBar})
 * needs {@link #ported} and two constructions that mean the same thing on each layer.
 */
final class ProbeValues {

    private ProbeValues() {
    }

    /**
     * One probe argument, in both layers' vocabularies.
     *
     * @param jdk what the JDK setter is handed
     * @param emulator what the emulator setter is handed — the identical instance for a
     *                 type SB-Emulators reuses, an equivalent one for a type it ports
     */
    record ValuePair(Object jdk, Object emulator, String label) {
    }

    private static final Map<Class<?>, List<ValuePair>> VALUES = new LinkedHashMap<>();

    /**
     * Probe factories for the types SB-Emulators <em>ports</em>, where the two layers need
     * two different instances that mean the same thing.
     *
     * <p>Factories rather than instances for two reasons. A {@code vaadinx.*} component
     * cannot be constructed before there is a Karibu UI, and there is none when this
     * class initialises; and one shared component would be re-parented out of the
     * previous pair's target as each new pair adopted it. A fresh pair per
     * {@link #forType} call settles both.
     */
    private static final Map<Class<?>, Supplier<List<ValuePair>>> PORTED = new LinkedHashMap<>();

    /**
     * Registers a probe factory for a type SB-Emulators ports, keyed by the <em>JDK</em>
     * type — which is what {@code JdkProvenance.Pair.valueType()} reports for both halves.
     *
     * <pre>{@code
     * ported(java.awt.LayoutManager.class, () -> List.of(
     *         new ValuePair(new java.awt.FlowLayout(), new vaadinx.awt.FlowLayout(), "FlowLayout")));
     * }</pre>
     */
    private static void ported(Class<?> jdkType, Supplier<List<ValuePair>> factory) {
        PORTED.put(jdkType, factory);
    }

    /** Registers probe values for a type SB-Emulators reuses, so one instance serves both layers. */
    private static void reused(Class<?> type, Object... values) {
        List<ValuePair> out = new ArrayList<>();
        for (Object v : values) {
            out.add(new ValuePair(v, v, String.valueOf(v)));
        }
        VALUES.put(type, out);
    }

    static {
        reused(boolean.class, true, false);
        reused(Boolean.class, true, false);
        // 3 and 1, not 0 and 1: 0 collides with too many defaults, and a large value
        // trips the range checks on the bounded properties (JSlider, JProgressBar).
        reused(int.class, 3, 1);
        reused(Integer.class, 3, 1);
        reused(long.class, 3L, 1L);
        reused(short.class, (short) 3, (short) 1);
        reused(byte.class, (byte) 3, (byte) 1);
        reused(double.class, 0.5d, 0.25d);
        reused(float.class, 0.5f, 0.25f);
        reused(char.class, 'x', 'y');
        reused(Character.class, 'x', 'y');
        reused(String.class, "ADMIN", "");
        reused(Object.class, "ADMIN", 7);
        reused(Color.class, Color.RED, new Color(0x33, 0x66, 0x99));
        reused(Dimension.class, new Dimension(120, 40), new Dimension(0, 0));
        reused(Point.class, new Point(7, 11), new Point(0, 0));
        reused(Rectangle.class, new Rectangle(7, 11, 120, 40), new Rectangle(0, 0, 0, 0));
        reused(Insets.class, new Insets(1, 2, 3, 4), new Insets(0, 0, 0, 0));
        reused(Font.class, new Font("SansSerif", Font.BOLD, 17), new Font("Serif", Font.PLAIN, 11));

        // Collaborator types, all reused from the JDK per D_whitelist_porting — so the
        // identical instance goes to both layers and identity settles `agree`, which is
        // what makes them one-liners even though most of them define no `equals`.
        reused(javax.swing.Action.class, action("go"), action("stop"));
        reused(javax.swing.BoundedRangeModel.class,
                new javax.swing.DefaultBoundedRangeModel(3, 0, 0, 10),
                new javax.swing.DefaultBoundedRangeModel(1, 0, 0, 10));
        reused(javax.swing.text.Document.class,
                new javax.swing.text.PlainDocument(), new javax.swing.text.PlainDocument());
        reused(javax.swing.ListSelectionModel.class,
                new javax.swing.DefaultListSelectionModel(), new javax.swing.DefaultListSelectionModel());
        // TreeNodes, not Strings: a JTree's DefaultTreeModel holds TreeNodes, and the
        // JDK's layout cache casts a selection path's components to one — so a
        // String-componented path makes the *oracle* throw ClassCastException and
        // reports a divergence that is an artifact of the probe (measured).
        reused(javax.swing.tree.TreePath.class,
                new javax.swing.tree.TreePath(new Object[] {
                        new javax.swing.tree.DefaultMutableTreeNode("root"),
                        new javax.swing.tree.DefaultMutableTreeNode("child")}),
                new javax.swing.tree.TreePath(new javax.swing.tree.DefaultMutableTreeNode("root")));
        // 4x4 rather than 1x1: a degenerate raster is the kind of input an image
        // pipeline special-cases, and headless construction is fine either way.
        reused(java.awt.Image.class,
                new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_RGB));
        reused(java.awt.Shape.class,
                new java.awt.geom.Ellipse2D.Double(0, 0, 120, 40), new Rectangle(7, 11, 120, 40));
        // Not the JVM default on either side, so a setter that stores nothing cannot pass
        // by having its getter fall through to Locale.getDefault().
        reused(java.util.Locale.class, java.util.Locale.GERMANY, java.util.Locale.CANADA_FRENCH);

        reused(java.awt.ComponentOrientation.class,
                java.awt.ComponentOrientation.RIGHT_TO_LEFT, java.awt.ComponentOrientation.LEFT_TO_RIGHT);
        reused(java.awt.Cursor.class,
                java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR),
                java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
        reused(javax.swing.text.EditorKit.class,
                new javax.swing.text.DefaultEditorKit(), new javax.swing.text.StyledEditorKit());
        // Relative, and never touched: a JFileChooser setter that reached the disk would
        // make the sweep depend on the box it runs on.
        reused(java.io.File.class, new java.io.File("probe-dir"), new java.io.File("probe-dir/probe.txt"));
        reused(java.util.Date.class, new java.util.Date(86_400_000L), new java.util.Date(0L));
        reused(java.text.NumberFormat.class,
                java.text.NumberFormat.getPercentInstance(java.util.Locale.GERMANY),
                java.text.NumberFormat.getIntegerInstance(java.util.Locale.US));
        reused(java.text.DateFormatSymbols.class,
                new java.text.DateFormatSymbols(java.util.Locale.GERMANY),
                new java.text.DateFormatSymbols(java.util.Locale.US));
        reused(javax.swing.KeyStroke.class,
                javax.swing.KeyStroke.getKeyStroke("control S"), javax.swing.KeyStroke.getKeyStroke('x'));
        reused(javax.swing.ActionMap.class, new javax.swing.ActionMap(), new javax.swing.ActionMap());
        reused(javax.swing.ButtonModel.class,
                new javax.swing.DefaultButtonModel(), new javax.swing.JToggleButton.ToggleButtonModel());
        reused(javax.swing.SingleSelectionModel.class,
                new javax.swing.DefaultSingleSelectionModel(), new javax.swing.DefaultSingleSelectionModel());
        reused(javax.swing.ComboBoxModel.class,
                new javax.swing.DefaultComboBoxModel<>(new String[] {"a", "b"}),
                new javax.swing.DefaultComboBoxModel<>(new String[] {"c"}));
        reused(javax.swing.ListModel.class,
                listModel("a", "b"), listModel("c"));
        reused(javax.swing.table.TableModel.class,
                new javax.swing.table.DefaultTableModel(2, 3), new javax.swing.table.DefaultTableModel(1, 1));
        reused(javax.swing.tree.TreeModel.class,
                new javax.swing.tree.DefaultTreeModel(new javax.swing.tree.DefaultMutableTreeNode("a")),
                new javax.swing.tree.DefaultTreeModel(new javax.swing.tree.DefaultMutableTreeNode("b")));
        reused(javax.swing.tree.TreeSelectionModel.class,
                new javax.swing.tree.DefaultTreeSelectionModel(),
                new javax.swing.tree.DefaultTreeSelectionModel());
        reused(javax.swing.colorchooser.ColorSelectionModel.class,
                new javax.swing.colorchooser.DefaultColorSelectionModel(Color.RED),
                new javax.swing.colorchooser.DefaultColorSelectionModel(Color.BLUE));
        reused(javax.swing.text.Caret.class,
                new javax.swing.text.DefaultCaret(), new javax.swing.text.DefaultCaret());
        reused(javax.swing.text.Highlighter.class,
                new javax.swing.text.DefaultHighlighter(), new javax.swing.text.DefaultHighlighter());
        reused(javax.swing.text.NavigationFilter.class,
                new javax.swing.text.NavigationFilter(), new javax.swing.text.NavigationFilter());
    }

    static {
        // The ported types, ranked by how many pairs each unlocks. One value apiece: a
        // reference property defaults to null, so a single non-null instance already
        // distinguishes "stored it" from "stored nothing" — which is the whole question
        // JdkReturnValueDiffer.project can ask about these.
        ported(javax.swing.Icon.class, () -> List.of(new ValuePair(
                new javax.swing.ImageIcon(image()), new vaadinx.swing.ImageIcon(image()), "ImageIcon")));
        ported(java.awt.Component.class, () -> List.of(new ValuePair(
                new javax.swing.JLabel("probe"), new vaadinx.swing.JLabel("probe"), "JLabel")));
        ported(java.awt.LayoutManager.class, () -> List.of(new ValuePair(
                new java.awt.FlowLayout(), new vaadinx.awt.FlowLayout(), "FlowLayout")));
        ported(java.awt.Container.class, () -> List.of(new ValuePair(
                new javax.swing.JPanel(), new vaadinx.swing.JPanel(), "JPanel")));
        ported(javax.swing.JComponent.class, () -> List.of(new ValuePair(
                new javax.swing.JPanel(), new vaadinx.swing.JPanel(), "JPanel")));
        ported(javax.swing.JLayeredPane.class, () -> List.of(new ValuePair(
                new javax.swing.JLayeredPane(), new vaadinx.swing.JLayeredPane(), "JLayeredPane")));
        ported(javax.swing.JMenuBar.class, () -> List.of(new ValuePair(
                new javax.swing.JMenuBar(), new vaadinx.swing.JMenuBar(), "JMenuBar")));
        ported(javax.swing.JRootPane.class, () -> List.of(new ValuePair(
                new javax.swing.JRootPane(), new vaadinx.swing.JRootPane(), "JRootPane")));
        ported(javax.swing.TransferHandler.class, () -> List.of(new ValuePair(
                new javax.swing.TransferHandler("text"), new vaadinx.swing.TransferHandler("text"),
                "TransferHandler(\"text\")")));
        ported(javax.swing.border.Border.class, () -> List.of(new ValuePair(
                javax.swing.BorderFactory.createEmptyBorder(1, 2, 3, 4),
                vaadinx.swing.BorderFactory.createEmptyBorder(1, 2, 3, 4), "EmptyBorder")));
        ported(javax.swing.JViewport.class, () -> List.of(new ValuePair(
                new javax.swing.JViewport(), new vaadinx.swing.JViewport(), "JViewport")));
        ported(javax.swing.table.TableCellRenderer.class, () -> List.of(new ValuePair(
                new javax.swing.table.DefaultTableCellRenderer(),
                new vaadinx.swing.table.DefaultTableCellRenderer(), "DefaultTableCellRenderer")));
        ported(javax.swing.ListCellRenderer.class, () -> List.of(new ValuePair(
                new javax.swing.DefaultListCellRenderer(),
                new vaadinx.swing.DefaultListCellRenderer(), "DefaultListCellRenderer")));
    }

    private static java.awt.image.BufferedImage image() {
        return new java.awt.image.BufferedImage(4, 4, java.awt.image.BufferedImage.TYPE_INT_ARGB);
    }

    private static javax.swing.ListModel<String> listModel(String... items) {
        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        for (String item : items) {
            model.addElement(item);
        }
        return model;
    }

    /** An {@code Action} that does nothing, since the differ never fires one. */
    private static javax.swing.Action action(String name) {
        return new javax.swing.AbstractAction(name) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
            }
        };
    }

    /**
     * Probe arguments for {@code jdkType}, or an empty list when the type has none yet.
     *
     * <p>An enum needs no table entry: its own constants are the probe values, and the
     * JDK enum is reused rather than ported, so one constant serves both layers.
     */
    static List<ValuePair> forType(Class<?> jdkType) {
        List<ValuePair> canned = VALUES.get(jdkType);
        if (canned != null) {
            return canned;
        }
        Supplier<List<ValuePair>> factory = PORTED.get(jdkType);
        if (factory != null) {
            return factory.get();
        }
        if (jdkType.isEnum()) {
            List<ValuePair> out = new ArrayList<>();
            for (Object constant : jdkType.getEnumConstants()) {
                out.add(new ValuePair(constant, constant, String.valueOf(constant)));
            }
            return out;
        }
        return List.of();
    }
}
