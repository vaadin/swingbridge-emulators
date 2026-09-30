/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.FocusManager
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-written emulator (not generated). Statics-only cap on
// vaadinx.awt.KeyboardFocusManager — see that class's header for why the
// JDK's four-class chain flattens to two (D_focus_managers).

/**
 * Emulator for {@link javax.swing.FocusManager}: four statics and one constant,
 * exactly as the JDK class is. Every query and command migrated code calls —
 * {@code getFocusOwner}, {@code focusNextComponent}, … — is inherited from
 * {@link vaadinx.awt.KeyboardFocusManager}, which documents them.
 *
 * <p><b>Concrete, where the JDK's is abstract</b> (a deliberate R_swing_is_truth deviation):
 * {@link #getCurrentManager()} has to hand out an instance, and the JDK's
 * concrete subclass {@code DefaultFocusManager} is not ported.
 *
 */
public class FocusManager extends vaadinx.awt.KeyboardFocusManager {

    public static final String FOCUS_MANAGER_CLASS_PROPERTY = "FocusManagerClassName";

    /**
     * The shared stateless facade. Safe as a static because it holds no state —
     * per-UI focus state lives in {@code com.vaadin.swingbridge.surrogates.FocusTracker}; see
     * {@link vaadinx.awt.KeyboardFocusManager}'s class javadoc.
     */
    private static final FocusManager CURRENT = new FocusManager();

    protected FocusManager() {}

    /** The shared stateless facade; never {@code null}. */
    public static FocusManager getCurrentManager() {
        return CURRENT;
    }

    /**
     * WARN and ignore, same call as
     * {@link vaadinx.awt.KeyboardFocusManager#setCurrentKeyboardFocusManager}:
     * a user subclass's only overridable hooks are the ones this hierarchy
     * WARNs on, so honouring the swap would imply more works than does.
     */
    public static void setCurrentManager(FocusManager manager) {
        vaadinx.EHelper.onUnimplemented("FocusManager", "setCurrentManager", manager);
    }

    /**
     * Deprecated since 1.4 in the JDK and a no-op there for decades; there was
     * never a Swing focus manager here to disable.
     */
    @Deprecated
    public static void disableSwingFocusManager() {
        vaadinx.EHelper.onNoop("FocusManager", "disableSwingFocusManager");
    }

    /**
     * Always {@code true} — matching the JDK, where the flag has been pinned
     * true since 1.4. Code gating on it takes the branch it takes on a modern
     * JVM.
     */
    @Deprecated
    public static boolean isFocusManagerEnabled() {
        return true;
    }
}
