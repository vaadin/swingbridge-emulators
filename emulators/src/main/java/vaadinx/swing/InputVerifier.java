/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.InputVerifier
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
 * Port of {@link javax.swing.InputVerifier}. Abstract class user code
 * subclasses to validate a field before focus leaves it; we port rather
 * than reuse the JDK class because {@code verify(JComponent)} references
 * Component (D_whitelist_porting/D_event_port_policy — types touching the Component hierarchy must live
 * in vaadinx so our emulators can be passed through).
 *
 * <p>Wired in by {@link JComponent#setInputVerifier}: the peer's blur
 * listener calls {@link #shouldYieldFocus} on focus-lost; a false return
 * restores focus to the component so the user corrects the field before
 * moving on. The two-arg overload lets a verifier inspect the target of
 * the focus change — in a Vaadin context the target isn't always known
 * (browser focus may go to a non-peer element), so the default falls
 * through to the single-arg form.
 */
public abstract class InputVerifier {

    /**
     * Check whether the input in {@code input} is valid. Returning
     * {@code false} prevents focus from leaving the component.
     */
    public abstract boolean verify(vaadinx.swing.JComponent input);

    /**
     * Called by {@link JComponent}'s focus plumbing when focus would
     * leave {@code input}. JDK's default delegates to {@link #verify};
     * override if you want to allow focus to move even when verify
     * fails (e.g. to let the user cancel out via Escape).
     */
    public boolean shouldYieldFocus(vaadinx.swing.JComponent input) {
        return verify(input);
    }

    /**
     * Two-arg overload that takes the focus target. JDK's default calls
     * the single-arg {@link #shouldYieldFocus} and drops {@code target}
     * — matching so migration code overriding either signature works.
     */
    public boolean shouldYieldFocus(vaadinx.swing.JComponent source,
                                    vaadinx.swing.JComponent target) {
        return shouldYieldFocus(source);
    }
}
