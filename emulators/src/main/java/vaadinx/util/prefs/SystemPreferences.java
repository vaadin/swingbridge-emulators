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

package vaadinx.util.prefs;

import vaadinx.EHelper;

import java.util.prefs.AbstractPreferences;

/**
 * The deferred {@code systemRoot()} tree. Swing's system preferences are
 * machine-wide (shared by every user of the host); {@code localStorage} is
 * per-browser and structurally cannot represent "shared across all users of the
 * deployment", so this node stores nothing: writes {@code WARN} via
 * {@link EHelper#onUnimplemented} and reads return empty. A server-side shared
 * store is real scope for a surface almost no app touches — pulled in only if a
 * migration target needs it (R_match_swing_errors minor gap; see D_preferences).
 *
 * <p>{@code userRoot()} / {@code userNodeForPackage(...)} — the case real apps
 * actually use — are fully backed by {@link VaadinPreferences}.
 *
 * <p>Unlike {@link VaadinPreferences}, this tree is inert (no browser round-trip)
 * and therefore needs no UI context — a JVM-global singleton root suffices.
 */
final class SystemPreferences extends AbstractPreferences {

    static final SystemPreferences ROOT = new SystemPreferences(null, "");

    private SystemPreferences(SystemPreferences parent, String name) {
        super(parent, name);
    }

    @Override
    protected String getSpi(String key) {
        return null;
    }

    @Override
    protected void putSpi(String key, String value) {
        EHelper.onUnimplemented("vaadinx.util.prefs.SystemPreferences",
                "put (systemRoot deferred — localStorage is per-browser, cannot represent "
                        + "machine-wide preferences; use userRoot/userNodeForPackage)",
                key, value);
    }

    @Override
    protected void removeSpi(String key) {
        // nothing stored, nothing to remove
    }

    @Override
    protected String[] keysSpi() {
        return new String[0];
    }

    @Override
    protected String[] childrenNamesSpi() {
        return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return new SystemPreferences(this, name);
    }

    @Override
    protected void removeNodeSpi() {
        // nothing stored
    }

    @Override
    protected void syncSpi() {
        // nothing stored
    }

    @Override
    protected void flushSpi() {
        // nothing stored
    }

    @Override
    public boolean isUserNode() {
        return false;
    }
}
