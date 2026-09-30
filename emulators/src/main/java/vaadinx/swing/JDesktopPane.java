/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.JDesktopPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JDesktopPane (D_internal_frames). Emulator-only
// (no surrogate — SD_no_sjoptionpane-shape): once its JInternalFrames render as Dialog
// overlays that escape the desktop's bounds, the desktop pane itself only
// renders a background, so there is no fat surrogate to build. It reuses
// JLayeredPane's Div peer as the desktop background and does the frame
// bookkeeping (getAllFrames / selectedFrame) emulator-side.
//
// The one deliberate divergence from container add-routing: add(frame)
// records the frame but does NOT DOM-attach it — the frame's overlay opens
// on frame.setVisible(true), independent of the desktop's DOM subtree.
// This is what "frames escape desktop bounds" means concretely.

/** Emulator for {@link javax.swing.JDesktopPane}. Emulator-only holder over a Div background. */
public class JDesktopPane extends vaadinx.swing.JLayeredPane
        implements javax.accessibility.Accessible {

    public static final int LIVE_DRAG_MODE = 0;
    public static final int OUTLINE_DRAG_MODE = 1;

    // Frames added to this desktop, in add order. Frames render as overlays,
    // so they are tracked here rather than as DOM children of the peer.
    private final java.util.List<JInternalFrame> frames = new java.util.ArrayList<>();

    private JInternalFrame selectedFrame;

    // DesktopManager stored but inert — no OS-style drag/iconify management;
    // Vaadin drives drag/resize natively on the Dialog overlay.
    private javax.swing.DesktopManager desktopManager;

    private int dragMode = LIVE_DRAG_MODE;

    public JDesktopPane() {
        super();
        // Mark the background so it's recognisable / styleable as a desktop.
        getPeer().getElement().getClassList().add("emul-desktoppane");
    }

    // ---- Add-routing intercept (frames escape to overlays) --------------

    @Override
    protected void addImpl(vaadinx.awt.Component comp, Object constraints, int index) {
        if (comp instanceof JInternalFrame f) {
            // Track the frame; do NOT DOM-attach — its Dialog overlay opens
            // on f.setVisible(true), escaping the desktop's DOM subtree.
            if (!frames.contains(f)) {
                if (index < 0 || index >= frames.size()) {
                    frames.add(f);
                } else {
                    frames.add(index, f);
                }
            }
            // Stands in for the parent link this intercept does not create —
            // JInternalFrame.isShowing() needs it, and the JDK's own
            // getDesktopPane() finds the desktop by walking parents.
            f.desktop = this;
            // Honour a layer constraint if one was passed (JLayeredPane
            // add(comp, Integer) shape), for getAllFramesInLayer round-trip.
            if (constraints instanceof Integer layer) {
                setLayer(f, layer.intValue());
            }
            return;
        }
        super.addImpl(comp, constraints, index);
    }

    @Override
    public void remove(vaadinx.awt.Component comp) {
        if (comp instanceof JInternalFrame f) {
            frames.remove(f);
            f.desktop = null;
            if (selectedFrame == f) selectedFrame = null;
            return;
        }
        super.remove(comp);
    }

    // ---- Frame accessors ------------------------------------------------

    /** All internal frames on this desktop, in add order. */
    public JInternalFrame[] getAllFrames() {
        return frames.toArray(new JInternalFrame[0]);
    }

    /** Internal frames assigned to the given layer. */
    public JInternalFrame[] getAllFramesInLayer(int layer) {
        java.util.List<JInternalFrame> out = new java.util.ArrayList<>();
        for (JInternalFrame f : frames) {
            if (getLayer(f) == layer) out.add(f);
        }
        return out.toArray(new JInternalFrame[0]);
    }

    /** The currently selected (active) frame, or null. */
    public JInternalFrame getSelectedFrame() {
        return selectedFrame;
    }

    /**
     * Record the selected frame. Bookkeeping only — this does not drive
     * {@code frame.setSelected} (that would loop through the frame's own
     * selection path); callers select the frame via {@code frame.setSelected}
     * and the desktop tracks the result, matching JDK's split.
     */
    public void setSelectedFrame(JInternalFrame f) {
        this.selectedFrame = f;
    }

    // ---- Drag mode (Vaadin drags natively) ------------------------------

    public int getDragMode() {
        return dragMode;
    }

    /**
     * Stored for round-trip; has no effect — Vaadin's Dialog overlay drags
     * live regardless, and there is no outline-drag rendering to switch to
     * (R_layouts_close_enough). WARN only on the non-default OUTLINE mode so the round-trip
     * default set doesn't spam logs.
     */
    public void setDragMode(int dragMode) {
        int oldDragMode = this.dragMode;
        this.dragMode = dragMode;
        if (dragMode == OUTLINE_DRAG_MODE) {
            vaadinx.EHelper.onUnimplemented("JDesktopPane", "setDragMode(outline)", dragMode);
        }
        firePropertyChange("dragMode", oldDragMode, this.dragMode);
    }

    // ---- DesktopManager (stored, inert) ---------------------------------

    public javax.swing.DesktopManager getDesktopManager() {
        return desktopManager;
    }

    /**
     * Stored for round-trip but never consulted — frame iconify / maximize /
     * drag are driven by the emulator + Vaadin overlay directly, not through
     * a DesktopManager (D_jdesktoppane_holder). WARN so migrated code installing a custom
     * manager surfaces in logs.
     */
    public void setDesktopManager(javax.swing.DesktopManager d) {
        javax.swing.DesktopManager oldValue = desktopManager;
        desktopManager = d;
        vaadinx.EHelper.onUnimplemented("JDesktopPane", "setDesktopManager(dispatch)", d);
        firePropertyChange("desktopManager", oldValue, desktopManager);
    }

    // ---- L&F / misc -----------------------------------------------------

    public void updateUI() {
        // L&F swap — no-op (same shape as JPanel.updateUI).
    }

    public String getUIClassID() {
        return "DesktopPaneUI";
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JDesktopPane", "getAccessibleContext");
        return null;
    }
}
