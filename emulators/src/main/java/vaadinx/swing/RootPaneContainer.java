/*
 * Copyright (c) 1998, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.RootPaneContainer
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

/**
 * Port of {@link javax.swing.RootPaneContainer} — the contract of a component whose single
 * child is a {@link JRootPane}, so a caller holding one can reach its content, layered and
 * glass panes without knowing whether it is a frame, a dialog, a window or an internal frame.
 *
 * <p>Forced to port, rather than reuse the JDK interface per D_whitelist_porting, for
 * {@link MenuElement}'s reason: five of its seven members traffic in types SB-Emulators emulates
 * ({@code JRootPane}, {@code Container}, {@code JLayeredPane}, {@code Component}), so an
 * emulator implementing the JDK's copy would owe it {@code java.awt.Container} overloads no
 * import-swapped call site can produce, and would be answering with panes it cannot construct
 * (R_no_vaadin_in_api limb 1). Nothing about the interface is unsatisfiable — only reusing the
 * JDK's copy is. See D_hierarchy_parity.
 *
 * <p>{@link SwingUtilities#getRootPane} is the reason this is worth porting rather than
 * documenting as absent: without it that method degenerates into an {@code instanceof} chain
 * over the concrete window classes, which is how it came to answer {@code null} for a
 * {@code JFrame} handed to it directly.
 */
public interface RootPaneContainer {

    JRootPane getRootPane();

    void setContentPane(vaadinx.awt.Container contentPane);

    vaadinx.awt.Container getContentPane();

    void setLayeredPane(JLayeredPane layeredPane);

    JLayeredPane getLayeredPane();

    vaadinx.awt.Component getGlassPane();

    void setGlassPane(vaadinx.awt.Component glassPane);
}
