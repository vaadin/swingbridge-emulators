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
 * This file is derived from OpenJDK's javax.swing.JRadioButton
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JRadioButton per D_jradiobutton.
//
// JRadioButton is a public Swing leaf (no public class in javax.swing.*
// extends it, ignoring javax.swing.plaf.* per R_leaf_peer_lockdown). R_leaf_peer_lockdown stance applies:
// the protected (Component peer) ctor is omitted, the root public ctor
// hard-codes the canonical peer as SJRadioButton.
// User-code subclassing for behaviour inherits the locked peer and works
// fine; user-code subclassing for custom rendering (paintComponent
// override, pluggable BasicRadioButtonUI) is out of scope per R_match_swing_errors
// sub-bucket (b).
//
// Inherits ALL behaviour from vaadinx.swing.JToggleButton: the emulator-owned
// ToggleButtonModel with its ButtonGroup consult, the model-driven event
// fan-out and doClick, and setText's HasLabel routing. SJRadioButton renders
// that model and pulses it on a browser click, through AbstractButtonMixin.

/** Emulator for {@link javax.swing.JRadioButton}. */
public class JRadioButton extends vaadinx.swing.JToggleButton implements javax.accessibility.Accessible {

    public JRadioButton() {
        this(null, null, false);
    }

    public JRadioButton(vaadinx.swing.Icon icon) {
        this(null, icon, false);
    }

    public JRadioButton(vaadinx.swing.Icon icon, boolean selected) {
        this(null, icon, selected);
    }

    public JRadioButton(java.lang.String text) {
        this(text, null, false);
    }

    public JRadioButton(java.lang.String text, boolean selected) {
        this(text, null, selected);
    }

    public JRadioButton(java.lang.String text, vaadinx.swing.Icon icon) {
        this(text, icon, false);
    }

    public JRadioButton(javax.swing.Action action) {
        // JDK pattern: this(); setAction(a). Chaining to the no-arg ctor
        // means the model is installed once; setAction
        // does NAME / MNEMONIC / ICON / COMMAND / ENABLED copy afterwards.
        this();
        setAction(action);
    }

    public JRadioButton(java.lang.String text, vaadinx.swing.Icon icon, boolean selected) {
        // Root ctor — R_leaf_peer_lockdown lock-down: peer is hard-coded to SJRadioButton,
        // which renders the ToggleButtonModel JToggleButton's ctor installs.
        super(com.vaadin.swingbridge.surrogates.SJRadioButton.class, com.vaadin.swingbridge.surrogates.SJRadioButton::new);
        initToggle(text, icon, selected);
    }

    public java.lang.String getUIClassID() {
        return "RadioButtonUI";
    }

    public void updateUI() {
        // L&F swap — same rationale as JToggleButton.updateUI. Our "UI"
        // is the Vaadin peer, which isn't pluggable.
    }

    protected java.lang.String paramString() {
        // JDK JRadioButton.paramString is essentially empty (returns
        // AbstractButton's via JToggleButton.paramString). Chain so the
        // toString shape matches.
        return super.paramString();
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JRadioButton", "getAccessibleContext");
        return null;
    }
}
