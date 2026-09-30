/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.TransferHandler
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Ported emulator for javax.swing.TransferHandler. D_drag_and_drop (drag-and-drop):
// the class references vaadinx.swing.JComponent / vaadinx.awt.Component, so
// per D_whitelist_porting/D_event_port_policy it must be ported rather than reused from the JDK — user code
// subclasses it and casts support.getComponent() to a vaadinx.swing.* type.
//
// This is the user-facing Swing surface. The Vaadin wiring (making the peer a
// DragSource / DropTarget, translating drag/drop events into createTransferable
// / canImport / importData / exportDone) lives in the package-private
// DndBridge, which sits in this same package so it can reach the protected
// createTransferable / exportDone the JDK keeps hidden from outside callers
// (the JDK does the same — its DnD machinery is package-private to
// javax.swing). See D_dnd_wiring_paths for the two wiring paths (Grid-row + generic).
//
// Scope (D_dnd_in_app_scope): same-UI in-app transfers only. The Transferable is held
// server-side in a per-UI CurrentDrag holder and handed back at drop; no
// browser dataTransfer marshalling. External / OS-boundary DnD is deferred
// (D_gap_severity_triage sub-bucket (a)).

import vaadinx.awt.Component;

import java.awt.Point;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import vaadinx.awt.event.InputEvent;

/**
 * Emulator for {@link javax.swing.TransferHandler}. Reproduces the Swing
 * transfer-handler API surface against {@code vaadinx.*} component types so
 * import-swapped code subclasses it unchanged. The default (un-subclassed)
 * handler exports/imports nothing — the JDK's property-backed cut/copy/paste
 * default is drop-and-WARN per R_vaadin_first (D_drag_and_drop accepted limitations).
 */
public class TransferHandler implements java.io.Serializable {

    /** No transfer action. */
    public static final int NONE = DnDConstants.ACTION_NONE;
    /** Copy action. */
    public static final int COPY = DnDConstants.ACTION_COPY;
    /** Move action. */
    public static final int MOVE = DnDConstants.ACTION_MOVE;
    /** Copy-or-move action. */
    public static final int COPY_OR_MOVE = DnDConstants.ACTION_COPY_OR_MOVE;
    /** Link/reference action. */
    public static final int LINK = DnDConstants.ACTION_LINK;

    private final String propertyName;

    /** Convenience handler with no property-backed default behaviour. */
    public TransferHandler() {
        this(null);
    }

    /**
     * Property-backed handler. The JDK builds a default Transferable from the
     * named bean property for cut/copy/paste; we store the name for API
     * fidelity but the property-backed default is drop-and-WARN (D_drag_and_drop).
     */
    public TransferHandler(String property) {
        this.propertyName = property;
    }

    /**
     * Bitwise-OR of the actions ({@link #COPY}, {@link #MOVE}, {@link #LINK})
     * supported when exporting from {@code c}. Default {@link #NONE}; the
     * property-backed ctor reports {@link #COPY}.
     */
    public int getSourceActions(JComponent c) {
        return propertyName != null ? COPY : NONE;
    }

    /**
     * Builds the {@link Transferable} representing {@code c}'s current data for
     * export. Default null (nothing to export). Subclasses override.
     */
    protected Transferable createTransferable(JComponent c) {
        return null;
    }

    /**
     * Invoked after an export completes (successfully or not) with the action
     * that was performed. {@link #MOVE} is the source's cue to delete the moved
     * data. Default no-op; subclasses override.
     */
    protected void exportDone(JComponent source, Transferable data, int action) {
        // no-op by default
    }

    /**
     * Whether a drop/paste described by {@code support} can be imported.
     * Default false. Subclasses override.
     */
    public boolean canImport(TransferSupport support) {
        return false;
    }

    /**
     * @deprecated array form retained for API compatibility; the
     * {@link TransferSupport} form is canonical.
     */
    @Deprecated
    public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return false;
    }

    /**
     * Performs the import described by {@code support}, returning whether it
     * succeeded. Default false. Subclasses override.
     */
    public boolean importData(TransferSupport support) {
        return false;
    }

    /**
     * @deprecated component+transferable form retained for API compatibility.
     */
    @Deprecated
    public boolean importData(JComponent comp, Transferable t) {
        return importData(new TransferSupport(comp, t));
    }

    /**
     * Initiates a drag of {@code comp}'s data. In Swing this is called from a
     * component's UI on a drag gesture; here the browser drag gesture drives
     * the Vaadin DragSource which calls {@link #createTransferable} directly,
     * so this entry point is rarely used by migrated code. Best-effort R_best_effort_behaviour: it
     * records the export so a subsequent programmatic flow sees consistent
     * state, but the actual gesture originates in the browser.
     */
    public void exportAsDrag(JComponent comp, InputEvent e, int action) {
        vaadinx.EHelper.onUnimplemented("TransferHandler", "exportAsDrag", comp, action);
    }

    /**
     * Exports {@code comp}'s data to {@code clip}. Bridges to the same
     * {@code WebClipboard} the clipboard slice (D_clipboard) installs.
     */
    public void exportToClipboard(JComponent comp, Clipboard clip, int action) {
        Transferable t = createTransferable(comp);
        if (t == null) return;
        clip.setContents(t, null);
        exportDone(comp, t, action);
    }

    /** @return a fresh copy, {@code (0, 0)} until something sets an offset — as the JDK does */
    public Point getDragImageOffset() {
        return dragImageOffset == null ? new Point(0, 0) : new Point(dragImageOffset);
    }

    /**
     * Stores the offset; the browser renders its own drag image, which is the effect
     * R_decline_effect_only permits dropping.
     *
     * @throws NullPointerException on a null offset, which is what the JDK's own
     *         {@code new Point(p.x, p.y)} does to it
     */
    public void setDragImageOffset(Point p) {
        dragImageOffset = new Point(p.x, p.y);
        vaadinx.EHelper.onNoop("TransferHandler", "setDragImageOffset");
    }

    /** Drag-image offset — stored per R_decline_effect_only; the browser draws its own image. */
    private Point dragImageOffset;

    // ---------------------------------------------------------------------
    // TransferSupport — describes an in-progress import (drop or paste).
    // ---------------------------------------------------------------------

    /**
     * Emulator for {@link javax.swing.TransferHandler.TransferSupport}. Carries
     * the target component, the dragged {@link Transferable}, and (for drops)
     * the drop location + action negotiation. Built by the DnD bridge for drops
     * and by {@link TransferHandler#importData(JComponent, Transferable)} for
     * the paste case.
     */
    public static final class TransferSupport {

        private final Component component;
        private final Transferable transferable;
        private final boolean isDrop;
        private final DropLocation dropLocation;
        private final int sourceDropActions;
        private final int userDropAction;
        private int dropAction;

        /** Paste-shape support: not a drop, no location. */
        public TransferSupport(Component component, Transferable transferable) {
            this(component, transferable, false, null, NONE, NONE);
        }

        /** Drop-shape support — package-private, built by {@link DndBridge}. */
        TransferSupport(Component component, Transferable transferable, boolean isDrop,
                        DropLocation dropLocation, int sourceDropActions, int userDropAction) {
            if (component == null || transferable == null) {
                throw new NullPointerException("component and transferable must be non-null");
            }
            this.component = component;
            this.transferable = transferable;
            this.isDrop = isDrop;
            this.dropLocation = dropLocation;
            this.sourceDropActions = sourceDropActions;
            this.userDropAction = userDropAction;
            this.dropAction = userDropAction;
        }

        /** The drop/paste target component. */
        public Component getComponent() {
            return component;
        }

        public Transferable getTransferable() {
            return transferable;
        }

        public DataFlavor[] getDataFlavors() {
            return transferable.getTransferDataFlavors();
        }

        public boolean isDataFlavorSupported(DataFlavor df) {
            return transferable.isDataFlavorSupported(df);
        }

        /** True when this support describes a drop (vs. a paste). */
        public boolean isDrop() {
            return isDrop;
        }

        /** Component-specific drop location, or null for a paste. */
        public DropLocation getDropLocation() {
            if (!isDrop) {
                throw new IllegalStateException("getDropLocation is only valid for a drop");
            }
            return dropLocation;
        }

        public int getSourceDropActions() {
            return sourceDropActions;
        }

        public int getUserDropAction() {
            return userDropAction;
        }

        public int getDropAction() {
            return dropAction;
        }

        /** Chooses the action to perform among {@link #getSourceDropActions()}. */
        public void setDropAction(int dropAction) {
            this.dropAction = dropAction;
        }

        public void setShowDropLocation(boolean showDropLocation) {
            // Browser/Vaadin draws the drop indicator; the Swing hint is a no-op (R_layouts_close_enough).
            vaadinx.EHelper.onNoop("TransferHandler.TransferSupport", "setShowDropLocation");
        }
    }

    // ---------------------------------------------------------------------
    // DropLocation — base; JList / JTable provide component-specific forms.
    // ---------------------------------------------------------------------

    /**
     * Emulator for {@link javax.swing.TransferHandler.DropLocation}. The pixel
     * drop point is a dummy (R_layouts_close_enough — no server-side coordinates); the meaningful
     * position is the component-specific index carried by the subclasses
     * ({@link JList.DropLocation}, {@link JTable.DropLocation}).
     */
    public static class DropLocation {
        private final Point dropPoint;

        protected DropLocation(Point dropPoint) {
            this.dropPoint = dropPoint == null ? new Point(0, 0) : dropPoint;
        }

        public Point getDropPoint() {
            return new Point(dropPoint);
        }
    }
}
