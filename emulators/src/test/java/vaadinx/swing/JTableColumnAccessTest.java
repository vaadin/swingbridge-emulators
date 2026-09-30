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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import javax.swing.table.DefaultTableModel;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JTable's column and cell accessors run the JDK's bodies over the table's own models —
 * the column model, the TableModel and the row sorter — so a column move, a header
 * value or a {@code setModelIndex} is seen exactly as the JDK sees it. Each expected
 * line below was printed by the same script on a headless JDK 25.
 */
class JTableColumnAccessTest extends AbstractKaribuTest {

    /** Model rows: 0=c/3/true, 1=a/1/false, 2=b/2/true; column 0 is read-only. */
    private static DefaultTableModel model() {
        return new DefaultTableModel(new Object[][] {
                {"c", 3, true}, {"a", 1, false}, {"b", 2, true}}, new Object[] {"Name", "Qty", "Ok"}) {
            @Override
            public Class<?> getColumnClass(int c) {
                return c == 1 ? Integer.class : c == 2 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int col) {
                return col != 0;
            }
        };
    }

    private static String rect(Rectangle r) {
        return r.x + "," + r.y + "," + r.width + "x" + r.height;
    }

    @Test
    @DisplayName("column and cell accessors match the JDK's script")
    void accessorsMatchTheJdk() {
        DefaultTableModel m = model();
        JTable t = new JTable(m);
        List<String> log = new ArrayList<>();
        log.add("count=" + t.getColumnCount() + " name0=" + t.getColumnName(0)
                + " class1=" + t.getColumnClass(1).getSimpleName());
        t.getColumnModel().getColumn(0).setHeaderValue("Header");
        log.add("after header: name0=" + t.getColumnName(0));
        t.moveColumn(0, 2);
        log.add("after move: name0=" + t.getColumnName(0) + " name2=" + t.getColumnName(2)
                + " v(0,0)=" + t.getValueAt(0, 0) + " v(0,2)=" + t.getValueAt(0, 2)
                + " edit(0,2)=" + t.isCellEditable(0, 2) + " edit(0,0)=" + t.isCellEditable(0, 0));
        log.add("toView(0)=" + t.convertColumnIndexToView(0) + " toModel(0)=" + t.convertColumnIndexToModel(0)
                + " toView(-1)=" + t.convertColumnIndexToView(-1) + " toModel(-1)=" + t.convertColumnIndexToModel(-1)
                + " toView(9)=" + t.convertColumnIndexToView(9));
        t.getColumnModel().getColumn(0).setModelIndex(0);
        log.add("after setModelIndex: v(0,0)=" + t.getValueAt(0, 0) + " name0=" + t.getColumnName(0));
        t.getColumnModel().getColumn(0).setModelIndex(1);
        t.setAutoCreateRowSorter(true);
        t.getRowSorter().toggleSortOrder(0);
        log.add("sorted: rowCount=" + t.getRowCount() + " v(0,2)=" + t.getValueAt(0, 2)
                + " rowToModel(0)=" + t.convertRowIndexToModel(0) + " rowToView(0)=" + t.convertRowIndexToView(0));
        t.setValueAt("z", 0, 2);
        log.add("after setValueAt(view 0): model(1,0)=" + m.getValueAt(1, 0));
        t.setRowSorter(null);
        log.add("unsorted: rowToModel(1)=" + t.convertRowIndexToModel(1) + " rowToView(5)=" + t.convertRowIndexToView(5));

        assertEquals(List.of(
                "count=3 name0=Name class1=Integer",
                "after header: name0=Name",
                "after move: name0=Qty name2=Name v(0,0)=3 v(0,2)=c edit(0,2)=false edit(0,0)=true",
                "toView(0)=2 toModel(0)=1 toView(-1)=-1 toModel(-1)=-1 toView(9)=-1",
                "after setModelIndex: v(0,0)=c name0=Name",
                "sorted: rowCount=3 v(0,2)=a rowToModel(0)=1 rowToView(0)=2",
                "after setValueAt(view 0): model(1,0)=z",
                "unsorted: rowToModel(1)=1 rowToView(5)=5"), log);
    }

    @Test
    @DisplayName("an out-of-range view column throws the JDK's exception")
    void outOfRangeColumnThrows() {
        JTable t = new JTable(model());
        assertEquals("9 >= 3", assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> t.convertColumnIndexToModel(9)).getMessage());
        assertEquals("9 >= 3", assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> t.getColumnName(9)).getMessage());
    }

    @Test
    @DisplayName("getCellRect matches the JDK's, in range and out of it")
    void cellRectMatchesTheJdk() {
        JTable t = new JTable(model());
        assertEquals("75,16,75x16", rect(t.getCellRect(1, 1, true)));
        assertEquals("75,16,74x15", rect(t.getCellRect(1, 1, false)));
        assertEquals("0,0,75x0", rect(t.getCellRect(-1, 0, true)));
        assertEquals("0,0,75x0", rect(t.getCellRect(9, 0, false)));
        assertEquals("0,0,0x16", rect(t.getCellRect(0, -1, false)));
        assertEquals("0,0,0x16", rect(t.getCellRect(0, 9, true)));
        t.setRowHeight(20);
        t.getColumnModel().getColumn(0).setWidth(40);
        assertEquals("40,40,74x19", rect(t.getCellRect(2, 1, false)));
    }

    @Test
    @DisplayName("the preferred viewport size defaults to the JDK's 450x400")
    void preferredViewportSizeDefault() {
        assertEquals(new Dimension(450, 400), new JTable().getPreferredScrollableViewportSize());
    }
}
