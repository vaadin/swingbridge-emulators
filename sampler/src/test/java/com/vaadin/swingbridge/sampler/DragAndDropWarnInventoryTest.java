/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.dnd.GridDropMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JList;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTable;
import vaadinx.swing.TransferHandler;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

/**
 * DragAndDropPanel (D_drag_and_drop) WARN inventory exit gate. Both tests
 * fail if any {@code EHelper.onUnimplemented} / {@code onUnsupported} /
 * {@code onUnsupportedPeerShape} fires.
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the route, swaps to the
 *       DnD panel, asserts both list peers are armed (rows-draggable + drop
 *       mode mapped), then drives the no-drag "Move selected" affordance and
 *       asserts the document moved. The actual browser drag gesture +
 *       createTransferable / importData / exportDone translation is covered
 *       behaviorally in {@code vaadinx.swing.TransferHandlerTest}.</li>
 *   <li>{@link #inventory_dnd_api_surface} — micro-drives the graduated DnD
 *       surface: JList / JTable {@code setDragEnabled} / {@code setDropMode} /
 *       {@code setTransferHandler} (+ teardown), generic JComponent
 *       {@code setTransferHandler}, and the ported {@link TransferHandler} /
 *       {@code TransferSupport} public surface.</li>
 * </ol>
 */
class DragAndDropWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
    }

    @Test
    @SuppressWarnings("unchecked")
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("DnD");
        dump("Step 0b (DragAndDropPanel swap)", warnings);

        // Two SJList peers — DOM order is left-added-first.
        List<com.vaadin.swingbridge.surrogates.SJList> peers =
                LocatorJ._find(com.vaadin.swingbridge.surrogates.SJList.class, spec -> spec.withCount(2));
        com.vaadin.swingbridge.surrogates.SJList<Object> leftPeer = peers.get(0);
        com.vaadin.swingbridge.surrogates.SJList<Object> rightPeer = peers.get(1);
        JList<String> left = (JList<String>) EHelper.getEmulator(leftPeer);
        JList<String> right = (JList<String>) EHelper.getEmulator(rightPeer);

        // Both lists armed: drag source + drop target (INSERT -> BETWEEN).
        for (com.vaadin.swingbridge.surrogates.SJList<Object> p : List.of(leftPeer, rightPeer)) {
            if (!p.isRowsDraggable()) {
                throw new AssertionError("list peer should be rows-draggable");
            }
            if (p.getDropMode() != GridDropMode.BETWEEN) {
                throw new AssertionError("expected BETWEEN drop mode, got " + p.getDropMode());
            }
        }
        dump("Step 1 (both lists armed for DnD)", warnings);

        int leftBefore = left.getModel().getSize();
        int rightBefore = right.getModel().getSize();

        // No-drag move affordance: select left row 0, click "Move selected →".
        left.setSelectedIndex(0);
        LocatorJ._click(LocatorJ._get(Button.class, spec -> spec.withText("Move selected →")));
        if (left.getModel().getSize() != leftBefore - 1 || right.getModel().getSize() != rightBefore + 1) {
            throw new AssertionError("expected one document moved left→right, got left="
                    + left.getModel().getSize() + " right=" + right.getModel().getSize());
        }
        dump("Step 2 (move selected left→right)", warnings);

        WarnDump.println();
        WarnDump.println("=== DnD user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_dnd_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        TransferHandler handler = new DragAndDropPanel.DocumentMoveHandler();

        // -- JList drag/drop config + teardown --
        JList<String> jl = new JList<>(new String[] { "a", "b", "c" });
        jl.setDragEnabled(true);
        jl.getDragEnabled();
        jl.setDropMode(javax.swing.DropMode.USE_SELECTION);
        jl.setDropMode(javax.swing.DropMode.ON);
        jl.setDropMode(javax.swing.DropMode.INSERT);
        jl.getDropMode();
        jl.setTransferHandler(handler);
        jl.getTransferHandler();
        jl.setDragEnabled(false);
        jl.setTransferHandler(null); // teardown
        dump("JList  drag/drop config + teardown", warnings);

        // -- JTable drag/drop config (accepts every DropMode) --
        JTable jt = new JTable();
        jt.setDragEnabled(true);
        jt.setDropMode(javax.swing.DropMode.INSERT_ROWS);
        jt.setDropMode(javax.swing.DropMode.ON_OR_INSERT);
        jt.getDropMode();
        jt.setTransferHandler(handler);
        jt.getTransferHandler();
        jt.setTransferHandler(null);
        dump("JTable  drag/drop config", warnings);

        // -- Generic component (non-Grid peer) --
        JPanel panel = new JPanel();
        panel.setTransferHandler(handler);
        panel.getTransferHandler();
        panel.setTransferHandler(null);
        dump("JPanel  generic setTransferHandler round-trip", warnings);

        // -- TransferHandler public surface --
        TransferHandler base = new TransferHandler();
        base.getSourceActions(jl);
        new TransferHandler("text").getSourceActions(jl);
        TransferHandler.TransferSupport support =
                new TransferHandler.TransferSupport(jl, new StringSelection("doc"));
        base.canImport(support);
        base.importData(support);
        dump("TransferHandler  public surface", warnings);

        // -- TransferSupport public surface (paste shape) --
        support.isDrop();
        support.getComponent();
        support.getTransferable();
        support.getDataFlavors();
        support.isDataFlavorSupported(DataFlavor.stringFlavor);
        support.getSourceDropActions();
        support.getUserDropAction();
        support.setDropAction(TransferHandler.MOVE);
        support.getDropAction();
        support.setShowDropLocation(true); // onNoop — must not trip the gate
        dump("TransferSupport  public surface", warnings);

        WarnDump.println();
        WarnDump.println("=== DnD API-surface WARN total: " + warnings.size() + " ===");
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the DnD exit gate: " + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
