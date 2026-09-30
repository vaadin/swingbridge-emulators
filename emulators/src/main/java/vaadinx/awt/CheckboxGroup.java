/*
 * Copyright (c) 1995, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.CheckboxGroup
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator. Not a Component and not a peer holder — the whole
// class is a pointer at one Checkbox plus the re-selection dance below.
// Ported because its entire signature surface traffics in vaadinx.awt.Checkbox,
// which is exactly what forced javax.swing.ButtonGroup → vaadinx.swing.ButtonGroup
// under D_buttongroup. Decisions: D_awt_checkbox / SD_scheckbox.

/**
 * Emulator for {@link java.awt.CheckboxGroup} — the AWT 1.0 radio-button
 * mutex. Grouping is what turns a {@link Checkbox} into a radio button, both
 * in behaviour and in glyph:
 *
 * <pre>{@code
 * CheckboxGroup units = new CheckboxGroup();
 * Checkbox metric   = new Checkbox("metric", true, units);   // renders as a radio
 * Checkbox imperial = new Checkbox("imperial", false, units);
 * units.setSelectedCheckbox(imperial);   // silent: no ItemEvent, either box
 * }</pre>
 *
 * <p>Nothing here fires events and nothing here throws: a box belonging to
 * another group is a documented silent no-op, and {@code null} legally
 * deselects everything (R_match_swing_errors).
 *
 * <p>Lives in {@code :emulators} rather than {@code :surrogates} because
 * there is nothing Vaadin-shaped for it to be — no element, no rendering, no
 * {@code HasValue} — and D_buttongroup_browser_click already settled the analogous question for
 * {@code ButtonGroup}: group mutex is an emulator-layer concern, and
 * {@link com.vaadin.swingbridge.surrogates.SCheckbox} carries no awareness of any group.
 *
 * <p>{@link #getSelectedCheckbox} returns a {@code vaadinx.awt.Checkbox},
 * not the JDK type. Irreducible, and the same divergence D_buttongroup_cross_package
 * accepted for {@code ButtonGroup}: migrated code compiles unchanged
 * after the import swap.
 */
public class CheckboxGroup implements java.io.Serializable {

    private static final long serialVersionUID = 3729780091441768983L;

    // Private where the JDK's is package-private: nothing outside needs it
    // (Checkbox goes through getSelectedCheckbox), and a package-private
    // field of this name would shadow the getSelectedCheckbox/
    // setSelectedCheckbox property pair for same-package Kotlin — silently
    // routing `group.selectedCheckbox = box` around setCurrent entirely.
    private Checkbox selectedCheckbox;

    public CheckboxGroup() {
    }

    public Checkbox getSelectedCheckbox() {
        // Delegates to the deprecated name, not the other way round — that is
        // the JDK's direction, so a subclass overriding getCurrent() stays on
        // the invoked path (R_no_vaadin_in_api limb 2).
        return getCurrent();
    }

    /** @deprecated as of JDK 1.1, replaced by {@link #getSelectedCheckbox()} — but this is the implementation, not the wrapper */
    @Deprecated
    public Checkbox getCurrent() {
        return selectedCheckbox;
    }

    public void setSelectedCheckbox(Checkbox box) {
        setCurrent(box);
    }

    /**
     * Moves the selection, forcing the outgoing box off and the incoming box
     * on.
     *
     * <p>The two directions deliberately use different setters, and the
     * pointer moves before either. The outgoing box goes through the
     * public {@link Checkbox#setState}, which re-enters this group's
     * check but finds the pointer already moved, so its
     * cannot-deselect veto does not fire; the incoming box goes
     * through the package-private {@code setStateInternal}, which
     * skips group logic entirely. Using one setter for both
     * directions either recurses or leaves a stale selection.
     *
     * @param box null deselects everything; a box belonging to a different
     *        group is ignored — silently, as AWT does, and deliberately not an
     *        {@code IllegalArgumentException} (R_match_swing_errors)
     * @deprecated as of JDK 1.1, replaced by {@link #setSelectedCheckbox} — but
     *             this is the implementation, not the wrapper
     */
    @Deprecated
    public synchronized void setCurrent(Checkbox box) {
        if (box != null && box.group != this) {
            return;
        }
        Checkbox oldChoice = this.selectedCheckbox;
        this.selectedCheckbox = box;
        if (oldChoice != null && oldChoice != box && oldChoice.group == this) {
            oldChoice.setState(false);
        }
        if (box != null && oldChoice != box && !box.getState()) {
            box.setStateInternal(true);
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + "[selectedCheckbox=" + selectedCheckbox + "]";
    }
}
