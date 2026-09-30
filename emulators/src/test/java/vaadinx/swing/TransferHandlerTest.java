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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJList;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.ListModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_drag_and_drop drag-and-drop. The ported {@link TransferHandler} family + the {@code DndBridge}
 * translation from Vaadin DnD onto the Swing TransferHandler contract.
 *
 * <p>The Grid-row drag/drop path is driven through {@code DndBridge}'s package-private
 * test seams (Vaadin's GridDragStartEvent / GridDropEvent can't be constructed
 * browserless without coupling to the Grid key-mapper, and Karibu ships no
 * Grid DnD helper) — the seams invoke the same internal handlers the real
 * Vaadin listeners call, so the translation under test is the production path.
 */
class TransferHandlerTest extends AbstractKaribuTest {

    /**
     * A move handler split the JDK-canonical way: importData adds to the drop
     * target's model; exportDone(MOVE) removes from the source's model.
     */
    private static class MoveHandler extends TransferHandler {

        private final IntConsumer onExportDone;

        MoveHandler() {
            this(action -> { });
        }

        MoveHandler(IntConsumer onExportDone) {
            this.onExportDone = onExportDone;
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            Object v = ((JList<?>) c).getSelectedValue();
            return v == null ? null : new StringSelection(v.toString());
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                return false;
            }
            if (!(support.getComponent() instanceof JList<?>)) {
                return false;
            }
            support.setDropAction(MOVE);
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            String value = (String) transferData(support.getTransferable());
            DefaultListModel<String> model =
                    (DefaultListModel<String>) ((JList<String>) support.getComponent()).getModel();
            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            model.add(Math.max(0, Math.min(dl.getIndex(), model.size())), value);
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void exportDone(JComponent source, Transferable data, int action) {
            onExportDone.accept(action);
            if (action == MOVE && data != null) {
                String value = (String) transferData(data);
                DefaultListModel<String> model =
                        (DefaultListModel<String>) ((JList<String>) source).getModel();
                int i = model.indexOf(value);
                if (i >= 0) {
                    model.remove(i);
                }
            }
        }
    }

    /**
     * {@code getTransferData(stringFlavor)} without the two checked exceptions —
     * neither can fire on a flavor the caller has already tested for, and
     * declaring them would spread {@code throws} across every override signature
     * the JDK declares without them.
     */
    private static Object transferData(Transferable t) {
        try {
            return t.getTransferData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JList<String> jlistOf(String... items) {
        DefaultListModel<String> model = new DefaultListModel<>();
        for (String item : items) {
            model.addElement(item);
        }
        JList<String> list = new JList<>(model);
        UI.getCurrent().add(list.getPeer());
        return list;
    }

    @SuppressWarnings("unchecked")
    private static SJList<String> peer(JList<String> list) {
        return (SJList<String>) list.getPeer();
    }

    private static List<String> contents(ListModel<String> model) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            out.add(model.getElementAt(i));
        }
        return out;
    }

    // --- Ported type surface ---------------------------------------------

    @Test
    @DisplayName("action constants match JDK")
    void actionConstantsMatchJdk() {
        assertEquals(0, TransferHandler.NONE);
        assertEquals(1, TransferHandler.COPY);
        assertEquals(2, TransferHandler.MOVE);
        assertEquals(3, TransferHandler.COPY_OR_MOVE);
    }

    @Test
    @DisplayName("base handler exports and imports nothing")
    void baseHandlerExportsAndImportsNothing() {
        TransferHandler h = new TransferHandler();
        assertEquals(TransferHandler.NONE, h.getSourceActions(new JList<String>()));
        assertNull(h.createTransferable(new JList<String>()));
        assertFalse(h.importData(
                new TransferHandler.TransferSupport(new JList<String>(), new StringSelection("x"))));
    }

    @Test
    @DisplayName("property ctor reports COPY source action")
    void propertyCtorReportsCopySourceAction() {
        assertEquals(TransferHandler.COPY, new TransferHandler("text").getSourceActions(new JList<String>()));
    }

    @Test
    @DisplayName("paste-shape TransferSupport is not a drop")
    void pasteShapeTransferSupportIsNotADrop() {
        JList<String> list = new JList<>();
        TransferHandler.TransferSupport support =
                new TransferHandler.TransferSupport(list, new StringSelection("hi"));
        assertFalse(support.isDrop());
        assertSame(list, support.getComponent());
        assertTrue(support.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertThrows(IllegalStateException.class, support::getDropLocation);
    }

    @Test
    @DisplayName("DropLocation getters round-trip")
    void dropLocationGettersRoundTrip() {
        JList.DropLocation jl = new JList.DropLocation(new Point(0, 0), 3, true);
        assertEquals(3, jl.getIndex());
        assertTrue(jl.isInsert());
        JTable.DropLocation jt = new JTable.DropLocation(new Point(0, 0), 5, 0, false, false);
        assertEquals(5, jt.getRow());
        assertFalse(jt.isInsertRow());
    }

    // --- Peer configuration + teardown -----------------------------------

    @Test
    @DisplayName("setDragEnabled + setDropMode + handler arm the Grid peer")
    void dragEnabledPlusDropModePlusHandlerArmThePeer() {
        JList<String> list = jlistOf("a", "b", "c");
        list.setTransferHandler(new MoveHandler());
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);

        assertTrue(peer(list).isRowsDraggable());
        assertEquals(GridDropMode.BETWEEN, peer(list).getDropMode());
    }

    @Test
    @DisplayName("null handler tears the Grid peer down")
    void nullHandlerTearsThePeerDown() {
        JList<String> list = jlistOf("a", "b");
        list.setTransferHandler(new MoveHandler());
        list.setDragEnabled(true);
        list.setDropMode(DropMode.INSERT);
        assertTrue(peer(list).isRowsDraggable());

        list.setTransferHandler(null);
        assertFalse(peer(list).isRowsDraggable());
        assertNull(peer(list).getDropMode());
    }

    @Test
    @DisplayName("config is order-independent - handler before dragEnabled")
    void configIsOrderIndependent() {
        JList<String> list = jlistOf("a");
        list.setTransferHandler(new MoveHandler());   // dragEnabled still false -> not yet a source
        assertFalse(peer(list).isRowsDraggable());
        list.setDragEnabled(true);                    // now it re-arms
        assertTrue(peer(list).isRowsDraggable());
    }

    @Test
    @DisplayName("getTransferHandler round-trips and fires PCE")
    void getTransferHandlerRoundTripsAndFiresPce() {
        JList<String> list = jlistOf("a");
        Counter fired = new Counter();
        list.addPropertyChangeListener("transferHandler", e -> fired.inc());
        MoveHandler h = new MoveHandler();
        list.setTransferHandler(h);
        assertSame(h, list.getTransferHandler());
        assertTrue(fired.get() > 0);
    }

    // --- R_match_swing_errors drop-mode validation -----------------------------------------

    @Test
    @DisplayName("JList rejects unsupported drop modes")
    void jListRejectsUnsupportedDropModes() {
        JList<String> list = jlistOf("a");
        assertThrows(IllegalArgumentException.class, () -> list.setDropMode(DropMode.INSERT_ROWS));
    }

    @Test
    @DisplayName("JTable accepts every drop mode")
    void jTableAcceptsEveryDropMode() {
        JTable table = new JTable();
        UI.getCurrent().add(table.getPeer());
        table.setDropMode(DropMode.INSERT_ROWS); // no throw
        assertEquals(DropMode.INSERT_ROWS, table.getDropMode());
    }

    // --- End-to-end same-UI move (the headline) --------------------------

    @Test
    @DisplayName("dragging a row from list A to list B moves it")
    void draggingARowFromListAToListBMovesIt() {
        JList<String> a = jlistOf("a", "b", "c");
        JList<String> b = jlistOf("x", "y");
        armForMove(new MoveHandler(), a, b);

        // Drag "a" (row 0 of A); drop above row 0 of B.
        DndBridge.simulateGridDragStartForTest(a, List.of(0));
        DndBridge.simulateGridDropForTest(b, 0, GridDropLocation.ABOVE);
        DndBridge.simulateDragEndForTest(a);

        assertEquals(List.of("b", "c"), contents(a.getModel()));
        assertEquals(List.of("a", "x", "y"), contents(b.getModel()));
    }

    @Test
    @DisplayName("drop below a row inserts after it")
    void dropBelowARowInsertsAfterIt() {
        JList<String> a = jlistOf("a");
        JList<String> b = jlistOf("x", "y");
        armForMove(new MoveHandler(), a, b);

        DndBridge.simulateGridDragStartForTest(a, List.of(0));
        DndBridge.simulateGridDropForTest(b, 0, GridDropLocation.BELOW); // after "x"
        DndBridge.simulateDragEndForTest(a);

        assertEquals(List.of("x", "a", "y"), contents(b.getModel()));
    }

    @Test
    @DisplayName("cancelled drag fires exportDone(NONE) and moves nothing")
    void cancelledDragFiresExportDoneNone() {
        int[] lastAction = {-99};
        JList<String> a = jlistOf("a", "b");
        a.setTransferHandler(new MoveHandler(action -> lastAction[0] = action));
        a.setDragEnabled(true);

        DndBridge.simulateGridDragStartForTest(a, List.of(0));
        DndBridge.simulateDragEndForTest(a); // no drop happened

        assertEquals(TransferHandler.NONE, lastAction[0]);
        assertEquals(List.of("a", "b"), contents(a.getModel()));
    }

    @Test
    @DisplayName("drop with no tracked drag is a no-op")
    void dropWithNoTrackedDragIsANoOp() {
        JList<String> b = jlistOf("x", "y");
        b.setTransferHandler(new MoveHandler());
        b.setDropMode(DropMode.INSERT);
        // No simulateGridDragStartForTest — CurrentDrag is empty.
        DndBridge.simulateGridDropForTest(b, 0, GridDropLocation.ABOVE);
        assertEquals(List.of("x", "y"), contents(b.getModel()));
    }

    // --- exportToClipboard bridges to a clipboard ------------------------

    @Test
    @DisplayName("exportToClipboard writes the transferable and signals exportDone")
    void exportToClipboardWritesAndSignalsExportDone() throws Exception {
        int[] action = {-1};
        JList<String> list = jlistOf("doc-1");
        list.setSelectedIndex(0);
        MoveHandler handler = new MoveHandler(a -> action[0] = a);
        Clipboard clip = new Clipboard("test");

        handler.exportToClipboard(list, clip, TransferHandler.MOVE);

        assertEquals("doc-1", clip.getData(DataFlavor.stringFlavor));
        assertEquals(TransferHandler.MOVE, action[0]);
    }

    // --- Generic (non-Grid) component path --------------------------------

    @Test
    @DisplayName("setTransferHandler on a generic component round-trips without error")
    void setTransferHandlerOnAGenericComponentRoundTrips() {
        JPanel panel = new JPanel();
        UI.getCurrent().add(panel.getPeer());
        MoveHandler h = new MoveHandler();
        panel.setTransferHandler(h);
        assertSame(h, panel.getTransferHandler());
        panel.setTransferHandler(null);
        assertNull(panel.getTransferHandler());
    }

    /** Installs {@code handler} on every list and arms it as both drag source and INSERT drop target. */
    @SafeVarargs
    private static void armForMove(TransferHandler handler, JList<String>... lists) {
        for (JList<String> l : lists) {
            l.setTransferHandler(handler);
            l.setDragEnabled(true);
            l.setDropMode(DropMode.INSERT);
        }
    }
}
