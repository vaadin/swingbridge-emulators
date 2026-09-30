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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SJTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JTable} loaded from a {@code doInBackground()}-shaped worker
 * (SD_background_model_hop). The table is its model's listener, as the JDK's is, so its
 * selection bookkeeping and the migrator's listeners run on the worker; the surrogate hops
 * the Grid refresh onto the UI thread.
 */
class JTableBackgroundModelTest extends AbstractKaribuTest {

    /** Runs {@code body} on a thread carrying this session's {@link EmulatorContext}, off the Karibu lock. */
    private static void runOnWorker(Runnable body) {
        EmulatorContext ctx = EmulatorContext.get();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "table-worker");
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("the worker never returned");
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
    }

    /** Records the non-adjusting selection events: the selected view row, and the UI current. */
    private static List<Integer> recordSelection(JTable table, List<UI> uis) {
        List<Integer> seen = new CopyOnWriteArrayList<>();
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            seen.add(table.getSelectedRow());
            uis.add(UI.getCurrent());
        });
        return seen;
    }

    private static DefaultTableModel abc() {
        return new DefaultTableModel(new Object[][]{{"a"}, {"b"}, {"c"}}, new Object[]{"col"});
    }

    private static SJTable peerOf(JTable table) {
        return (SJTable) table.getPeer();
    }

    @Test
    @DisplayName("constructed and loaded on a worker, never attached: fires on the worker, as the JDK does")
    void detachedTableBuiltOnWorker() {
        AtomicReference<JTable> built = new AtomicReference<>();
        List<UI> uis = new CopyOnWriteArrayList<>();
        AtomicReference<List<Integer>> seen = new AtomicReference<>();

        runOnWorker(() -> {
            DefaultTableModel model = abc();
            JTable table = new JTable(model);
            table.setRowSelectionInterval(2, 2);
            seen.set(recordSelection(table, uis));
            model.removeRow(0);
            model.addRow(new Object[]{"d"});
            built.set(table);
        });

        assertEquals(List.of(1), seen.get());
        assertNull(uis.get(0));
        JTable table = built.get();
        assertEquals(3, table.getRowCount());
        assertEquals("c", table.getValueAt(table.getSelectedRow(), 0));
        assertEquals(3, GridKt._size(peerOf(table)));
        assertEquals(Set.of(1), peerOf(table).getSelectedItems());
    }

    @Test
    @DisplayName("attached, loaded on a worker through the model: fires on the worker, the Grid still follows")
    void attachedTableLoadedThroughModel() {
        DefaultTableModel model = abc();
        JTable table = new JTable(model);
        UI.getCurrent().add(table.getPeer());
        table.setRowSelectionInterval(1, 1);
        List<UI> uis = new CopyOnWriteArrayList<>();
        List<Integer> seen = recordSelection(table, uis);
        List<UI> modelListenerUis = new CopyOnWriteArrayList<>();
        model.addTableModelListener(e -> modelListenerUis.add(UI.getCurrent()));

        runOnWorker(() -> {
            model.insertRow(0, new Object[]{"z"});
            model.addRow(new Object[]{"y"});
        });

        assertEquals(List.of(2), seen);
        assertNull(uis.get(0), "the JDK fires model events on the mutating thread");
        assertEquals(2, modelListenerUis.size());
        assertNull(modelListenerUis.get(0));
        assertEquals("b", table.getValueAt(table.getSelectedRow(), 0));
        assertEquals(5, GridKt._size(peerOf(table)));
        assertEquals(Set.of(2), peerOf(table).getSelectedItems(), "the Grid follows the shifted selection");
    }

    @Test
    @DisplayName("attached and sorted: a worker's insert re-sorts, and the selection keeps its row")
    void attachedSortedTableLoadedThroughModel() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{{"Charlie"}, {"Alice"}, {"Bob"}}, new Object[]{"name"});
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        UI.getCurrent().add(table.getPeer());
        table.setRowSelectionInterval(1, 1); // Bob, model row 2
        List<UI> uis = new CopyOnWriteArrayList<>();
        List<Integer> seen = recordSelection(table, uis);

        runOnWorker(() -> model.addRow(new Object[]{"Aaron"}));

        assertEquals(List.of(2), seen, "Aaron sorts first, pushing Bob one view row down");
        assertNull(uis.get(0));
        assertEquals("Bob", table.getValueAt(table.getSelectedRow(), 0));
        assertEquals(4, GridKt._size(peerOf(table)));
        assertEquals(Set.of(2), peerOf(table).getSelectedItems(), "the Grid selection is in model rows");
    }
}
