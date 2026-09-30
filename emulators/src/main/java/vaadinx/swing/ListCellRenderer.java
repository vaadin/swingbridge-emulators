/*
 * Copyright (c) 1997, 2005, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.ListCellRenderer
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
 * Port of {@link javax.swing.ListCellRenderer}. Returns
 * {@link vaadinx.awt.Component} so the renderer can hand back our
 * emulator's component hierarchy — JDK's {@code java.awt.Component}
 * return type is incompatible with our hierarchy and is the central
 * reason this interface is ported wholesale rather than reused (D_event_port_policy +
 * the cell-renderer carve-out).
 *
 * <p>The first parameter is {@code vaadinx.swing.JList<? extends E>} per D_jlist
 * (superseding D_jcombobox's placeholder {@code Object}), matching the JDK signature
 * — so a JDK-faithful renderer override compiles unchanged after the import
 * swap. It arrives differently from the two bridges (see {@code @param list});
 * a renderer that reads selection / model state off the list needs the
 * {@code JList} bridge, while the common-case value-formatting renderer (the
 * {@link DefaultListCellRenderer} pattern) works under both.
 */
public interface ListCellRenderer<E> {

    /**
     * Build the rendering component for {@code value}.
     *
     * @param list           the rendering host: the {@code JList} instance
     *                       from the {@code :emulators.JList} bridge, or
     *                       {@code null} from the {@code :emulators.JComboBox}
     *                       bridge (a combo box has no JList).
     * @param value          the model value to render.
     * @param index          the index of the cell, or {@code -1} when
     *                       rendering the selected value (e.g. in the
     *                       JComboBox button face).
     * @param isSelected     whether the cell is currently selected. The
     *                       JComboBox bridge passes {@code false}; full
     *                       selected-cell highlighting is browser-driven.
     * @param cellHasFocus   whether the cell has keyboard focus. The
     *                       JComboBox bridge passes {@code false}.
     */
    vaadinx.awt.Component getListCellRendererComponent(
            JList<? extends E> list, E value, int index, boolean isSelected, boolean cellHasFocus);
}
