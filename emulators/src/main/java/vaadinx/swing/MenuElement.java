/*
 * Copyright (c) 1997, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.MenuElement
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
 * Port of {@link javax.swing.MenuElement}. Forced to port, rather than reuse
 * the JDK interface per D_whitelist_porting, because every one of its methods traffics in a
 * type SB-Emulators emulates: {@code getComponent()} returns a {@code java.awt.Component}
 * we cannot produce, and the two {@code process*} methods take
 * {@code java.awt.event} events. Reusing the JDK interface made
 * {@link JMenuItem#getComponent()} and {@link JPopupMenu#getComponent()} return
 * {@code null} by construction — a documented dead end, and R_no_vaadin_in_api limb 1's
 * JDK-typed half is what names it as a violation rather than a limitation.
 *
 * <p>{@code MenuSelectionManager} stays the JDK's on the two {@code process*}
 * signatures: SB-Emulators models no menu-selection machinery for it to drive (D_menu_tree), so
 * there is nothing to port it to. See {@link MenuSelectionManager} for the
 * import-swap stub that keeps a migrated file naming the type compiling.
 */
public interface MenuElement {

    void processMouseEvent(vaadinx.awt.event.MouseEvent event, MenuElement[] path, MenuSelectionManager manager);

    void processKeyEvent(vaadinx.awt.event.KeyEvent event, MenuElement[] path, MenuSelectionManager manager);

    void menuSelectionChanged(boolean isIncluded);

    MenuElement[] getSubElements();

    vaadinx.awt.Component getComponent();
}
