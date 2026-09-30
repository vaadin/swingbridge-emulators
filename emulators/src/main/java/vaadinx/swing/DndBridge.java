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
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.dnd.DragSource;
import com.vaadin.flow.component.dnd.DropTarget;
import com.vaadin.flow.component.dnd.EffectAllowed;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dnd.GridDropLocation;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import com.vaadin.flow.shared.Registration;

import javax.swing.DropMode;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.Point;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Translates the Swing {@link TransferHandler} contract onto Vaadin's native
 * drag-and-drop (D_drag_and_drop). Lives in {@code vaadinx.swing} (not a sub-package) so it
 * can reach {@link TransferHandler}'s protected {@code createTransferable} /
 * {@code exportDone} — the JDK keeps its own DnD machinery package-private to
 * {@code javax.swing} for exactly this reason.
 *
 * <p>Two wiring paths (D_dnd_wiring_paths), selected by peer type:
 * <ul>
 *   <li><b>Grid-row</b> — JList / JTable peers are {@code Grid<Integer>},
 *       JTree's peer is {@code TreeGrid<Object>} (D_jtree_row_dnd); uses Grid's built-in
 *       row DnD ({@code setRowsDraggable} / {@code setDropMode} / drag-start
 *       / drop / drag-end listeners). The drop's row index / node +
 *       {@link GridDropLocation} become a {@link JList.DropLocation} /
 *       {@link JTable.DropLocation} / {@link JTree.DropLocation} (the
 *       JTree path reconstructed via the surrogate's parent map).</li>
 *   <li><b>Generic</b> — any other peer uses {@link DragSource} /
 *       {@link DropTarget} configured on the peer directly.</li>
 * </ul>
 *
 * <p>All peer→Swing callback bodies run inside {@link vaadinx.EHelper#callSwing}
 * (R_callswing_envelope) — {@code importData} may pop a modal dialog that must VT-park. The
 * dragged {@link Transferable} is held in a per-UI {@link CurrentDrag} holder
 * (D_dnd_in_app_scope, same-UI scope). Per-component teardown state ({@link Wiring}) is stored
 * on the peer via {@link ComponentUtil#setData}.
 */
final class DndBridge {

    private DndBridge() {}

    /** Per-peer registration bookkeeping, so a re-config or null-handler tears down cleanly. */
    private static final class Wiring {
        final List<Registration> registrations = new ArrayList<>();
    }

    /**
     * (Re)configures DnD for an emulator after its {@code transferHandler},
     * {@code dragEnabled}, or {@code dropMode} changed. Idempotent: tears down
     * any prior wiring first, then installs fresh wiring iff a handler is set.
     */
    static void reconfigure(JComponent emu) {
        Component peer = emu.getPeer();
        // The wiring is peer writes, reachable from a background thread through
        // setDragEnabled / setTransferHandler / setDropMode (D_attach_aware_hop).
        emu.withPeer(p -> {
            teardown(peer);

            TransferHandler handler = emu.getTransferHandler();
            if (handler == null) return;

            Wiring w = new Wiring();
            if (peer instanceof Grid<?> grid) {
                configureGrid(emu, grid, handler, w);
            } else {
                configureGeneric(emu, peer, handler, w);
            }
            ComponentUtil.setData(peer, Wiring.class, w);
        });
    }

    private static void teardown(Component peer) {
        Wiring prior = ComponentUtil.getData(peer, Wiring.class);
        if (prior != null) {
            prior.registrations.forEach(Registration::remove);
            ComponentUtil.setData(peer, Wiring.class, null);
        }
        if (peer instanceof Grid<?> grid) {
            grid.setRowsDraggable(false);
            grid.setDropMode(null);
        } else {
            DragSource.configure(peer).setDraggable(false);
            DropTarget.configure(peer).setActive(false);
        }
    }

    // --- Grid-row path (JList / JTable / JTree) ----------------------------

    @SuppressWarnings("unchecked")
    private static void configureGrid(JComponent emu, Grid<?> rawGrid, TransferHandler handler, Wiring w) {
        // JList / JTable peers are Grid<Integer> (rows), JTree's peer is
        // TreeGrid<Object> (nodes, D_jtree_row_dnd) — the bridge handles items as
        // plain Objects and re-types per emulator in the helpers below.
        Grid<Object> grid = (Grid<Object>) rawGrid;

        boolean dragSource = isDragSource(emu, handler);
        grid.setRowsDraggable(dragSource);
        if (dragSource) {
            w.registrations.add(grid.addDragStartListener(e -> {
                List<Object> dragged = e.getDraggedItems();
                vaadinx.EHelper.callSwing(() -> onDragStart(emu, handler, dragged));
            }));
            w.registrations.add(grid.addDragEndListener(e ->
                    vaadinx.EHelper.callSwing(() -> onDragEnd(emu))));
        }

        // Drop target: a component with a handler is always a potential drop
        // target (canImport gates each drop); default DropMode -> BETWEEN.
        grid.setDropMode(mapDropMode(dropModeOf(emu)));
        w.registrations.add(grid.addDropListener(e -> {
            Object target = e.getDropTargetItem().orElse(null);
            GridDropLocation loc = e.getDropLocation();
            vaadinx.EHelper.callSwing(() -> onGridDrop(emu, handler, target, loc));
        }));
    }

    private static void onGridDrop(JComponent emu, TransferHandler handler,
                                   Object targetItem, GridDropLocation loc) {
        CurrentDrag cd = CurrentDrag.get();
        if (cd == null) return; // not a tracked same-UI drag (or external — deferred per D_dnd_in_app_scope)
        TransferHandler.DropLocation dl = buildDropLocation(emu, targetItem, loc);
        deliverDrop(emu, handler, cd, dl);
    }

    /**
     * Maps the Grid drop item + drop side onto a Swing component DropLocation —
     * a row index for JList / JTable, a {@link TreePath} + child index for
     * JTree (D_jtree_row_dnd).
     */
    private static TransferHandler.DropLocation buildDropLocation(JComponent emu, Object targetItem,
                                                                  GridDropLocation loc) {
        if (emu instanceof JTree tree) {
            return buildTreeDropLocation(tree, targetItem, loc);
        }
        Point pt = new Point(0, 0); // R_layouts_close_enough — no server-side pixel coordinates
        int rowCount = modelRowCount(emu);
        Integer targetRow = (Integer) targetItem;
        int index;
        boolean insert;
        if (targetRow == null || loc == GridDropLocation.EMPTY) {
            index = rowCount;
            insert = true;
        } else {
            switch (loc) {
                case ABOVE -> { index = targetRow; insert = true; }
                case BELOW -> { index = targetRow + 1; insert = true; }
                default -> { index = targetRow; insert = false; } // ON_TOP
            }
        }
        if (emu instanceof JTable) {
            return new JTable.DropLocation(pt, index, 0, insert, false);
        }
        return new JList.DropLocation(pt, index, insert);
    }

    /**
     * D_jtree_row_dnd: TreeGrid drop node + side → {@link JTree.DropLocation}. ON_TOP is
     * a drop <em>on</em> the node ({@code childIndex == -1}, the JDK
     * {@code DropMode.ON} shape); ABOVE / BELOW insert into the node's parent
     * at the node's model index (the path reconstructed via the surrogate's
     * parent map). An empty-area drop appends under the (possibly hidden)
     * model root.
     */
    private static TransferHandler.DropLocation buildTreeDropLocation(JTree tree, Object targetNode,
                                                                      GridDropLocation loc) {
        Point pt = new Point(0, 0); // R_layouts_close_enough — no server-side pixel coordinates
        com.vaadin.swingbridge.surrogates.SJTree s = (com.vaadin.swingbridge.surrogates.SJTree) tree.getPeer();
        TreeModel model = tree.getModel();
        if (targetNode == null || loc == GridDropLocation.EMPTY) {
            Object root = model == null ? null : model.getRoot();
            TreePath rootPath = root == null ? null : s.getPathForNode(root);
            int count = root == null ? -1 : model.getChildCount(root);
            return new JTree.DropLocation(pt, rootPath, count);
        }
        TreePath targetPath = s.getPathForNode(targetNode);
        if (loc == GridDropLocation.ON_TOP) {
            return new JTree.DropLocation(pt, targetPath, -1);
        }
        TreePath parentPath = targetPath == null ? null : targetPath.getParentPath();
        if (parentPath == null) {
            // ABOVE/BELOW the visible root has no parent slot — treat as ON (R_best_effort_behaviour).
            return new JTree.DropLocation(pt, targetPath, -1);
        }
        int index = model.getIndexOfChild(parentPath.getLastPathComponent(), targetNode);
        if (loc == GridDropLocation.BELOW) {
            index++;
        }
        return new JTree.DropLocation(pt, parentPath, index);
    }

    private static int modelRowCount(JComponent emu) {
        if (emu instanceof JList<?> l) {
            return l.getModel() == null ? 0 : l.getModel().getSize();
        }
        if (emu instanceof JTable t) {
            return t.getRowCount();
        }
        return 0;
    }

    // --- Generic path (any other peer) ------------------------------------

    private static void configureGeneric(JComponent emu, Component peer, TransferHandler handler, Wiring w) {
        boolean dragSource = isDragSource(emu, handler);
        DragSource<? extends Component> ds = DragSource.configure(peer);
        ds.setDraggable(dragSource);
        if (dragSource) {
            ds.setEffectAllowed(mapEffectAllowed(handler.getSourceActions(emu)));
            w.registrations.add(ds.addDragStartListener(e ->
                    vaadinx.EHelper.callSwing(() -> onDragStart(emu, handler, List.of()))));
            w.registrations.add(ds.addDragEndListener(e ->
                    vaadinx.EHelper.callSwing(() -> onDragEnd(emu))));
        }
        DropTarget<? extends Component> dt = DropTarget.configure(peer);
        dt.setActive(true);
        w.registrations.add(dt.addDropListener(e ->
                vaadinx.EHelper.callSwing(() -> onGenericDrop(emu, handler))));
    }

    private static void onGenericDrop(JComponent emu, TransferHandler handler) {
        CurrentDrag cd = CurrentDrag.get();
        if (cd == null) return;
        TransferHandler.DropLocation dl = new TransferHandler.DropLocation(new Point(0, 0));
        deliverDrop(emu, handler, cd, dl);
    }

    // --- Shared drag-start / drop / drag-end ------------------------------

    private static void onDragStart(JComponent emu, TransferHandler handler, List<?> draggedItems) {
        syncSelectionToDragged(emu, draggedItems);
        Transferable t = handler.createTransferable(emu);
        if (t == null) return;
        CurrentDrag.set(new CurrentDrag(emu, handler, t, handler.getSourceActions(emu)));
    }

    /** Drives canImport → importData → exportDone on a successful same-UI drop. */
    private static void deliverDrop(JComponent target, TransferHandler targetHandler,
                                    CurrentDrag cd, TransferHandler.DropLocation dl) {
        int userAction = preferredAction(cd.sourceActions);
        TransferHandler.TransferSupport support = new TransferHandler.TransferSupport(
                target, cd.transferable, true, dl, cd.sourceActions, userAction);
        if (!targetHandler.canImport(support)) return;
        if (targetHandler.importData(support)) {
            cd.imported = true;
            // exportDone runs on the source handler with the negotiated action,
            // letting a MOVE delete the source data (the JDK contract).
            cd.handler.exportDone(cd.source, cd.transferable, support.getDropAction());
        }
    }

    private static void onDragEnd(JComponent emu) {
        CurrentDrag cd = CurrentDrag.get();
        if (cd != null && cd.source == emu) {
            if (!cd.imported) {
                // Drag cancelled / dropped nowhere: JDK fires exportDone(NONE).
                cd.handler.exportDone(cd.source, cd.transferable, TransferHandler.NONE);
            }
            CurrentDrag.clear();
        }
    }

    /** Selects the dragged rows/nodes in the source so {@code createTransferable} reads them. */
    private static void syncSelectionToDragged(JComponent emu, List<?> draggedItems) {
        if (draggedItems == null || draggedItems.isEmpty()) return;
        if (emu instanceof JList<?> l) {
            l.setSelectedIndices(draggedItems.stream().mapToInt(o -> (Integer) o).toArray());
        } else if (emu instanceof JTable t) {
            t.clearSelection();
            for (Object row : draggedItems) t.addRowSelectionInterval((Integer) row, (Integer) row);
        } else if (emu instanceof JTree tree) {
            // D_jtree_row_dnd: dragged TreeGrid items are nodes — reconstruct their paths.
            com.vaadin.swingbridge.surrogates.SJTree s = (com.vaadin.swingbridge.surrogates.SJTree) tree.getPeer();
            TreePath[] paths = draggedItems.stream()
                    .map(s::getPathForNode)
                    .filter(Objects::nonNull)
                    .toArray(TreePath[]::new);
            tree.setSelectionPaths(paths);
        }
    }

    // --- Config readers + mappings (D_dnd_semantic_mappings) ---------------------------------

    private static boolean isDragSource(JComponent emu, TransferHandler handler) {
        if (emu instanceof JList<?> l) return l.getDragEnabled();
        if (emu instanceof JTable t) return t.getDragEnabled();
        if (emu instanceof JTree tr) return tr.getDragEnabled();
        return handler.getSourceActions(emu) != TransferHandler.NONE;
    }

    private static DropMode dropModeOf(JComponent emu) {
        if (emu instanceof JList<?> l) return l.getDropMode();
        if (emu instanceof JTable t) return t.getDropMode();
        if (emu instanceof JTree tr) return tr.getDropMode();
        return null;
    }

    /** Swing {@link DropMode} → Vaadin {@link GridDropMode}; null/USE_SELECTION → BETWEEN. */
    private static GridDropMode mapDropMode(DropMode mode) {
        if (mode == null) return GridDropMode.BETWEEN;
        return switch (mode) {
            case ON -> GridDropMode.ON_TOP;
            case ON_OR_INSERT, ON_OR_INSERT_ROWS, ON_OR_INSERT_COLS -> GridDropMode.ON_TOP_OR_BETWEEN;
            default -> GridDropMode.BETWEEN; // INSERT, INSERT_ROWS, INSERT_COLS, USE_SELECTION
        };
    }

    /** TransferHandler source-action bitmask → Vaadin {@link EffectAllowed}. */
    private static EffectAllowed mapEffectAllowed(int actions) {
        boolean copy = (actions & TransferHandler.COPY) != 0;
        boolean move = (actions & TransferHandler.MOVE) != 0;
        boolean link = (actions & TransferHandler.LINK) != 0;
        if (copy && move) return EffectAllowed.COPY_MOVE;
        if (move) return EffectAllowed.MOVE;
        if (copy) return EffectAllowed.COPY;
        if (link) return EffectAllowed.LINK;
        return EffectAllowed.NONE;
    }

    /** Best-effort drop action when no browser modifier reaches us: MOVE > COPY > LINK. */
    private static int preferredAction(int sourceActions) {
        if ((sourceActions & TransferHandler.MOVE) != 0) return TransferHandler.MOVE;
        if ((sourceActions & TransferHandler.COPY) != 0) return TransferHandler.COPY;
        if ((sourceActions & TransferHandler.LINK) != 0) return TransferHandler.LINK;
        return TransferHandler.NONE;
    }

    // --- Test seams (D_drag_and_drop validation) --------------------------------------
    //
    // Vaadin's GridDragStartEvent / GridDropEvent can't be constructed in a
    // browserless test without coupling to the Grid key-mapper internals, and
    // Karibu ships no Grid DnD helper. These seams stand in for the
    // un-constructable event objects only — they invoke the exact same
    // internal handlers (onDragStart / onGridDrop) the real Vaadin listeners
    // call, so the translation logic under test is the production path.

    static void simulateGridDragStartForTest(JComponent source, List<?> draggedItems) {
        vaadinx.EHelper.callSwing(() -> onDragStart(source, source.getTransferHandler(), draggedItems));
    }

    static void simulateGridDropForTest(JComponent target, Object targetItem, GridDropLocation loc) {
        vaadinx.EHelper.callSwing(() -> onGridDrop(target, target.getTransferHandler(), targetItem, loc));
    }

    static void simulateDragEndForTest(JComponent source) {
        vaadinx.EHelper.callSwing(() -> onDragEnd(source));
    }
}
