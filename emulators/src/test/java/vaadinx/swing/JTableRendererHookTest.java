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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.table.DefaultTableCellRenderer;
import vaadinx.swing.table.TableCellRenderer;

import javax.swing.table.DefaultTableModel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * D_r12_provenance, both limbs. {@code prepareRenderer} is a JDK hook, so it must be
 * <i>reachable</i> — the cell render path calls through it, and a migrated subclass's override
 * therefore runs — and the colours a renderer or such an override sets must
 * survive the per-call snapshot into the rendered cell.
 *
 * <p>The shape under test is {@code testapps/inventory}'s {@code BetterJTable}, which overrides
 * {@code prepareRenderer} for row striping: before D_r12_provenance that override could not compile,
 * and would have been dead code if it had.
 */
class JTableRendererHookTest extends AbstractKaribuTest {

    /** A renderer that returns a JLabel, so the column stays on the ComponentRenderer (snapshot) path. */
    private final TableCellRenderer labelRenderer =
            (table, value, isSelected, hasFocus, row, column) -> new JLabel("v:" + value);

    private static Component cellOf(JTable table, int modelRow, int col) {
        @SuppressWarnings("unchecked")
        ComponentRenderer<Component, Integer> r =
                (ComponentRenderer<Component, Integer>) ((Grid<?>) table.getPeer())
                        .getColumns().get(col).getRenderer();
        return r.createComponent(modelRow);
    }

    @Test
    @DisplayName("prepareRenderer override runs on the render path")
    void prepareRendererIsReachedOnTheRenderPath() {
        List<Integer> seen = new ArrayList<>();
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a"}, {"b"}}, new Object[] {"c0"})) {
            @Override
            public vaadinx.awt.Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                seen.add(row);
                vaadinx.awt.Component c = super.prepareRenderer(renderer, row, column);
                c.setForeground(Color.RED);
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(labelRenderer);

        Component cell = cellOf(table, 0, 0);

        assertEquals(List.of(0), seen, "the render path must call through prepareRenderer");
        assertEquals("rgb(255,0,0)", cell.getElement().getStyle().get("color"),
                "the override's foreground must reach the rendered cell");
    }

    @Test
    @DisplayName("prepareRenderer sees the view row, so striping alternates")
    void prepareRendererSeesTheViewRow() {
        // Striping is the whole reason the JDK hands the renderer a *view* row.
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a"}, {"b"}}, new Object[] {"c0"})) {
            @Override
            public vaadinx.awt.Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
                JComponent c = (JComponent) super.prepareRenderer(renderer, row, column);
                c.setOpaque(true);
                c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(240, 240, 240));
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(labelRenderer);

        assertEquals("rgb(255,255,255)",
                cellOf(table, 0, 0).getElement().getStyle().get("background-color"));
        assertEquals("rgb(240,240,240)",
                cellOf(table, 1, 0).getElement().getStyle().get("background-color"));
    }

    @Test
    @DisplayName("the commonest idiom - colour by value in the renderer - reaches the cell")
    void colourByValueInTheRendererReachesTheCell() {
        // A DefaultTableCellRenderer subclass that colours on value. The subclass
        // (not the exact class) is what keeps the column on the snapshot path.
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{-5}, {7}}, new Object[] {"amount"}));
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public vaadinx.awt.Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                vaadinx.awt.Component c = super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, column);
                setForeground((Integer) value < 0 ? Color.RED : Color.BLACK);
                return c;
            }
        });

        assertEquals("rgb(255,0,0)", cellOf(table, 0, 0).getElement().getStyle().get("color"));
        assertEquals("rgb(0,0,0)", cellOf(table, 1, 0).getElement().getStyle().get("color"));
    }

    @Test
    @DisplayName("snapshot carries foreground always but background only when opaque")
    void snapshotCarriesForegroundAlwaysBackgroundOnlyWhenOpaque() {
        // Swing's own rule: text paints in foreground unconditionally, the
        // background only for an opaque component. DefaultTableCellRenderer is
        // opaque from its ctor; a bare JLabel is not.
        DefaultTableCellRenderer opaque = new DefaultTableCellRenderer();
        opaque.setForeground(Color.RED);
        opaque.setBackground(Color.BLUE);
        Component snapOpaque = JComboBox.snapshotRendererOutput(opaque, "x");
        assertEquals("rgb(255,0,0)", snapOpaque.getElement().getStyle().get("color"));
        assertEquals("rgb(0,0,255)", snapOpaque.getElement().getStyle().get("background-color"));

        JLabel transparent = new JLabel("x");
        transparent.setForeground(Color.RED);
        transparent.setBackground(Color.BLUE);
        assertFalse(transparent.isOpaque(), "a bare JLabel is non-opaque, as in Swing");
        Component snapTransparent = JComboBox.snapshotRendererOutput(transparent, "x");
        assertEquals("rgb(255,0,0)", snapTransparent.getElement().getStyle().get("color"));
        assertNull(snapTransparent.getElement().getStyle().get("background-color"),
                "a non-opaque renderer's background is dropped, as Swing drops it");
    }

    @Test
    @DisplayName("an uncoloured renderer leaves the cell unstyled")
    void unColouredRendererLeavesCellUnstyled() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a"}}, new Object[] {"c0"}));
        table.getColumnModel().getColumn(0).setCellRenderer(labelRenderer);
        Component cell = cellOf(table, 0, 0);
        assertNull(cell.getElement().getStyle().get("color"));
        assertNull(cell.getElement().getStyle().get("background-color"));
    }
}
