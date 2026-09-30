/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.tree.TreeCellRenderer
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.tree;

/**
 * Port of {@link javax.swing.tree.TreeCellRenderer}. Returns
 * {@link vaadinx.awt.Component} so the renderer hands back our emulator's
 * Component hierarchy — JDK's {@code java.awt.Component} return type is
 * incompatible with our hierarchy and is the central reason this interface
 * is ported wholesale rather than reused (D_event_port_policy + the cell-renderer carve-out;
 * mirrors {@link vaadinx.swing.ListCellRenderer} and
 * {@link vaadinx.swing.table.TableCellRenderer}).
 *
 * <p>The {@code tree} parameter is typed {@link vaadinx.swing.JTree}; the
 * D_jtree_renderer_registry bridge passes the emulator JTree, so renderers down-casting or
 * reading {@code tree.convertValueToText(...)} (the
 * {@link DefaultTreeCellRenderer} pattern) work as written. {@code null}
 * is tolerated for renderers invoked from a pure-surrogate context.
 */
public interface TreeCellRenderer {

    /**
     * Build the rendering component for {@code value}.
     *
     * @param tree     the rendering host — the emulator {@code JTree} in
     *                 emulator flow, {@code null} from pure-surrogate
     *                 contexts.
     * @param value    the node to render. {@code null} is valid.
     * @param selected whether the node is currently selected.
     * @param expanded whether the node is currently expanded.
     * @param leaf     whether the node is a leaf per the model.
     * @param row      the node's visible-row index, or {@code -1} when the
     *                 node isn't currently displayed.
     * @param hasFocus always {@code false} from the D_jtree_renderer_registry bridge — Vaadin
     *                 TreeGrid has no per-cell focus signal at render time
     *                 (R_vaadin_first sub-bucket (c)).
     */
    vaadinx.awt.Component getTreeCellRendererComponent(vaadinx.swing.JTree tree, Object value,
                                                       boolean selected, boolean expanded,
                                                       boolean leaf, int row, boolean hasFocus);
}
