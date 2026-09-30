/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.DefaultListCellRenderer
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
 * Port of {@link javax.swing.DefaultListCellRenderer}. Extends
 * {@link JLabel} and renders the value via {@code String.valueOf}
 * (matches JDK's default behaviour of showing the model value's
 * {@code toString}). The selection / focus chrome that JDK applies via
 * UIManager defaults stays as drop-and-WARN per R_vaadin_first — Vaadin owns the
 * highlighted-cell styling on the dropdown side, and our renderer's
 * inline-CSS shadow wouldn't reach the rendered DOM in any case (the
 * Vaadin combo box renders renderer output inside its own light DOM
 * scope).
 *
 * <p>Existence of this class makes
 * {@code DefaultListCellRenderer.UIResource} (a JDK marker subclass for
 * UIManager defaults) unnecessary at the migrated-app level — UIManager
 * dispatch isn't modeled per R_match_swing_errors sub-bucket (b).
 */
public class DefaultListCellRenderer extends JLabel implements ListCellRenderer<Object> {

    public DefaultListCellRenderer() {
        super();
    }

    @Override
    public vaadinx.awt.Component getListCellRendererComponent(
            JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        // JDK's body sets text from value (via list-renderer-model
        // contract — we simplify to String.valueOf since we don't have
        // a JList model to read through), then applies UIManager
        // selection colours when isSelected is true. We skip the colour
        // application: Vaadin owns the dropdown highlight and host CSS
        // wouldn't reach into the renderer's rendered DOM scope anyway.
        // R_vaadin_first drop-and-WARN baseline accepted.
        setText(value == null ? "" : String.valueOf(value));
        return this;
    }
}
