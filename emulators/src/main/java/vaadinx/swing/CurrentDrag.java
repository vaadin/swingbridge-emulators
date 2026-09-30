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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;

import java.awt.datatransfer.Transferable;

/**
 * Per-UI holder for the in-progress drag (D_dnd_in_app_scope). Set at drag-start with the
 * {@link Transferable} that {@link TransferHandler#createTransferable} produced,
 * read by the drop target's listener, cleared at drag-end. This is the
 * server-side "same-UI drag data" mechanism — mirrors the shared
 * {@code draggedItem} field Vaadin's own cross-grid DnD example captures at
 * {@code DragStart} and consumes at {@code Drop}. Stored on the {@link UI} via
 * {@link ComponentUtil#setData}, so it is scoped to one browser tab and never
 * leaks across UIs.
 */
final class CurrentDrag {

    final JComponent source;
    final TransferHandler handler;
    final Transferable transferable;
    final int sourceActions;
    /** Set true once a drop target successfully imports, so drag-end skips the NONE exportDone. */
    boolean imported;

    CurrentDrag(JComponent source, TransferHandler handler, Transferable transferable, int sourceActions) {
        this.source = source;
        this.handler = handler;
        this.transferable = transferable;
        this.sourceActions = sourceActions;
    }

    static void set(CurrentDrag cd) {
        UI ui = UI.getCurrent();
        if (ui != null) ComponentUtil.setData(ui, CurrentDrag.class, cd);
    }

    static CurrentDrag get() {
        UI ui = UI.getCurrent();
        return ui == null ? null : ComponentUtil.getData(ui, CurrentDrag.class);
    }

    static void clear() {
        UI ui = UI.getCurrent();
        if (ui != null) ComponentUtil.setData(ui, CurrentDrag.class, null);
    }
}
