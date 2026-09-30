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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static vaadinx.TestAssertions.assertSingle;

class JDesktopPaneTest extends AbstractKaribuTest {

    private final List<String> warns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        warns.clear();
        EHelper.warnHook = warns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> {
        };
    }

    @Test
    @DisplayName("add tracks the frame without DOM-attaching it")
    void addTracksFrameWithoutAttaching() {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame frame = new JInternalFrame("Doc");
        desktop.add(frame);
        assertSame(frame, assertSingle(desktop.getAllFrames()));
        // The frame escapes to an overlay — it is NOT a child of the desktop's
        // container (add-routing intercept, D_jdesktoppane_holder).
        assertEquals(0, desktop.getComponentCount());
    }

    @Test
    @DisplayName("remove drops the frame and clears selection")
    void removeDropsFrameAndClearsSelection() {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame frame = new JInternalFrame("Doc");
        desktop.add(frame);
        desktop.setSelectedFrame(frame);
        desktop.remove(frame);
        assertEquals(0, desktop.getAllFrames().length);
        assertNull(desktop.getSelectedFrame());
    }

    @Test
    @DisplayName("getAllFramesInLayer filters by layer")
    void getAllFramesInLayerFiltersByLayer() {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame a = new JInternalFrame("A");
        JInternalFrame b = new JInternalFrame("B");
        // Add, then tag layers explicitly: an `add(comp, layer)` call with an int
        // layer binds to Container's add(Component, int) *index* overload, not to
        // the add(Component, Object) constraint overload a layer is meant to reach
        // — so drive setLayer directly here rather than relying on that resolution.
        desktop.add(a);
        desktop.add(b);
        desktop.setLayer(b, JLayeredPane.PALETTE_LAYER);
        assertArrayEquals(new JInternalFrame[] {a},
                desktop.getAllFramesInLayer(JLayeredPane.DEFAULT_LAYER));
        assertArrayEquals(new JInternalFrame[] {b},
                desktop.getAllFramesInLayer(JLayeredPane.PALETTE_LAYER));
    }

    @Test
    @DisplayName("selectedFrame round-trips")
    void selectedFrameRoundTrips() {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame frame = new JInternalFrame("Doc");
        desktop.add(frame);
        desktop.setSelectedFrame(frame);
        assertSame(frame, desktop.getSelectedFrame());
    }

    @Test
    @DisplayName("LIVE drag mode is silent, OUTLINE WARNs")
    void outlineDragModeWarns() {
        JDesktopPane desktop = new JDesktopPane();
        desktop.setDragMode(JDesktopPane.LIVE_DRAG_MODE);
        assertNoWarns(warns);
        desktop.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
        assertEquals(JDesktopPane.OUTLINE_DRAG_MODE, desktop.getDragMode());
        assertFalse(warns.isEmpty(), "OUTLINE_DRAG_MODE has no browser counterpart — should WARN");
    }
}
