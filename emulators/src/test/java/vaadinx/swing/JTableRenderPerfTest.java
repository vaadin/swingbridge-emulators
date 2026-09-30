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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.renderer.TextRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.swing.table.DefaultTableCellRenderer;
import vaadinx.swing.table.TableCellRenderer;

import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * D_jtable_cell_editing rendering-perf refactor: default text columns render via a cheap
 * {@code TextRenderer} (no component per cell); Boolean / custom /
 * {@code DefaultTableCellRenderer}-subclass columns keep the {@code ComponentRenderer}
 * snapshot path. A renderer change flips a column between the two (re-installed
 * in place via {@code Grid.Column.setRenderer}).
 */
class JTableRenderPerfTest extends AbstractKaribuTest {

    private static Grid<?> grid(JTable t) {
        return (Grid<?>) t.getPeer();
    }

    /** The renderer Vaadin currently has installed on {@code table}'s column {@code i}. */
    private static Object renderer(JTable table, int i) {
        return grid(table).getColumns().get(i).getRenderer();
    }

    @Test
    @DisplayName("plain default columns render as TextRenderer, not a component per cell")
    void plainDefaultColumnsUseTextRenderer() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a", "b"}}, new Object[] {"c0", "c1"}));
        assertInstanceOf(TextRenderer.class, renderer(table, 0));
        assertInstanceOf(TextRenderer.class, renderer(table, 1));
    }

    @Test
    @DisplayName("Boolean column keeps a ComponentRenderer (checkbox glyph)")
    void booleanColumnKeepsComponentRenderer() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{true}}, new Object[] {"flag"}) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return Boolean.class;
            }
        });
        assertInstanceOf(ComponentRenderer.class, renderer(table, 0));
    }

    @Test
    @DisplayName("setDefaultRenderer flips a column to ComponentRenderer and back")
    void setDefaultRendererFlipsColumn() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a"}}, new Object[] {"c0"}));
        assertInstanceOf(TextRenderer.class, renderer(table, 0));         // plain default → text

        TableCellRenderer custom =
                (t, value, isSelected, hasFocus, row, column) -> new JLabel("custom:" + value);
        table.setDefaultRenderer(Object.class, custom);                   // Object.class → custom
        assertInstanceOf(ComponentRenderer.class, renderer(table, 0));    // flipped to component

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
        assertInstanceOf(TextRenderer.class, renderer(table, 0));         // flipped back to text
    }

    @Test
    @DisplayName("per-column setCellRenderer flips to ComponentRenderer via PCE re-install")
    void perColumnCellRendererFlipsColumn() {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{"a"}}, new Object[] {"c0"}));
        assertInstanceOf(TextRenderer.class, renderer(table, 0));
        table.getColumnModel().getColumn(0).setCellRenderer(
                (t, value, isSelected, hasFocus, row, column) -> new JLabel("x:" + value));
        assertInstanceOf(ComponentRenderer.class, renderer(table, 0));    // cellRenderer PCE → re-install
    }
}
