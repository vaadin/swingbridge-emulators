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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import vaadinx.swing.table.TableColumnModel;

import javax.swing.table.DefaultTableModel;

import java.awt.ComponentOrientation;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * {@link JTable#rowAtPoint} / {@link JTable#columnAtPoint}: the JDK's bodies, with the cell the
 * browser clicked standing in for their pixel arithmetic (D_jtable_thin_shell).
 */
class JTableHitTestTest extends AbstractKaribuTest {

    private static DefaultTableModel model() {
        return new DefaultTableModel(new Object[][] {{"c", 3}, {"a", 1}, {"b", 2}}, new Object[] {"Name", "Qty"});
    }

    /** The table's Grid peer, attached to the UI. */
    @SuppressWarnings("unchecked")
    private static Grid<Object> attachGrid(JTable table) {
        Grid<Object> grid = (Grid<Object>) table.getPeer();
        UI.getCurrent().add(grid);
        return grid;
    }

    @Test
    @DisplayName("a click answers the clicked cell in view coordinates, and -1 outside the dispatch")
    void clickAnswersTheViewCell() {
        JTable table = new JTable(model());
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        Grid<Object> grid = attachGrid(table);
        List<String> seen = new ArrayList<>();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                seen.add(e.getClickCount() + " [" + row + ", " + column + "] " + table.getValueAt(row, column));
            }
        });

        // Model row 0 ("c", 3) sorts last.
        GridKt._doubleClickItem(grid, 2, grid.getColumns().get(1));

        assertEquals(List.of("2 [2, 1] 3"), seen);
        assertEquals(-1, table.rowAtPoint(new Point(0, 0)));
        assertEquals(-1, table.columnAtPoint(new Point(0, 0)));
    }

    /**
     * The call graph measured on JDK 25: {@code rowAtPoint} reads {@code getRowHeight} then
     * range-checks against {@code getRowCount}; {@code columnAtPoint} reads the orientation, then
     * the column model. A null point throws from both.
     */
    @Test
    @DisplayName("rowAtPoint and columnAtPoint run the JDK's call graph")
    void hitTestRunsTheJdksCallGraph() {
        List<String> log = new ArrayList<>();
        JTable table = new JTable(model()) {
            @Override public int getRowHeight() { log.add("getRowHeight"); return super.getRowHeight(); }
            @Override public int getRowCount() { log.add("getRowCount"); return super.getRowCount(); }
            @Override public TableColumnModel getColumnModel() { log.add("getColumnModel"); return super.getColumnModel(); }
            @Override public ComponentOrientation getComponentOrientation() { log.add("getComponentOrientation"); return super.getComponentOrientation(); }
        };
        Grid<Object> grid = attachGrid(table);
        List<String> seen = new ArrayList<>();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                log.clear();
                seen.add("row " + table.rowAtPoint(e.getPoint()) + " " + log);
                log.clear();
                seen.add("column " + table.columnAtPoint(e.getPoint()) + " " + log);
            }
        });

        GridKt._clickItem(grid, 1, grid.getColumns().get(0));

        assertEquals(List.of(
                "row 1 [getRowHeight, getRowCount]",
                "column 0 [getComponentOrientation, getColumnModel]"), seen);
        assertThrows(NullPointerException.class, () -> table.rowAtPoint(null));
        assertThrows(NullPointerException.class, () -> table.columnAtPoint(null));
    }

    @Test
    @DisplayName("a row removed during the dispatch answers -1, as the JDK's range check does")
    void shrunkTableAnswersMinusOne() {
        DefaultTableModel model = model();
        JTable table = new JTable(model);
        Grid<Object> grid = attachGrid(table);
        List<Integer> seen = new ArrayList<>();
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                seen.add(table.rowAtPoint(e.getPoint()));
                model.removeRow(2);
                seen.add(table.rowAtPoint(e.getPoint()));
            }
        });

        GridKt._clickItem(grid, 2, grid.getColumns().get(0));

        assertEquals(List.of(2, -1), seen);
    }

    @Test
    @DisplayName("the clicked cell survives the handler parking on a modal dialog")
    void cellSurvivesAParkedHandler() throws InterruptedException {
        JTable table = new JTable(model());
        Grid<Object> grid = attachGrid(table);
        List<String> seen = new CopyOnWriteArrayList<>();
        CountDownLatch finished = new CountDownLatch(1);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(null, "Open?");
                seen.add(table.rowAtPoint(e.getPoint()) + ", " + table.columnAtPoint(e.getPoint()));
                finished.countDown();
            }
        });

        GridKt._clickItem(grid, 1, grid.getColumns().get(1));
        Button ok = assertSingle(LocatorJ._find(Button.class, spec -> spec.withText("OK")));
        EHelper.callSwing(() -> LocatorJ._click(ok));

        assertTrue(finished.await(5, TimeUnit.SECONDS), "the handler did not resume");
        assertEquals(List.of("1, 1"), seen);
    }
}
