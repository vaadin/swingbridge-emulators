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

import com.vaadin.flow.server.VaadinSession;
import vaadinx.EHelper;

import java.util.prefs.AbstractPreferences;

/**
 * A localStorage-backed {@code Preferences} node reachable from a
 * <b>background thread</b> — a {@code SwingWorker.doInBackground()} that reads
 * or writes app preferences (D_prefs_scope_split). It is the sibling of {@link VaadinPreferences}
 * for the case where there is no current UI/lock on the calling thread but the
 * thread carries the owning {@link VaadinSession} via a propagated
 * {@code vaadinx.EmulatorContext}.
 *
 * <p>Each {@code *Spi} op runs its {@link PrefsCache} body inside
 * {@link EHelper#callOnLiveUISync} — which resolves the session's single live
 * UI, acquires the lock, hops onto the loom carrier (so the first read can park
 * on the {@code executeJs} localStorage round-trip), and returns the value to
 * the worker thread. So the migrator's ordinary synchronous
 * {@code Preferences.userRoot()...get(...)} call in {@code doInBackground} just
 * works. If no single live UI is reachable (tab/session closed, or genuine
 * multi-tab) the bridge throws — surfacing as the worker's
 * {@code ExecutionException} rather than silently returning stale values (D_prefs_scope_split).
 *
 * <p>Unlike {@link VaadinPreferences}, this holds no {@link PrefsCache} field:
 * the cache is per-UI and can only be obtained inside the bridge (where a UI is
 * current), so each op resolves {@code PrefsCache.current()} within
 * {@code callOnLiveUISync}.
 */
public final class BridgedPreferences extends AbstractPreferences {

    private final VaadinSession session;

    /** Root-node ctor, bound to the worker's owning session. Built by {@link VaadinPreferencesFactory}. */
    BridgedPreferences(VaadinSession session) {
        super(null, "");
        this.session = session;
    }

    /** Child-node ctor; inherits the parent's session binding. */
    private BridgedPreferences(BridgedPreferences parent, String name) {
        super(parent, name);
        this.session = parent.session;
    }

    @Override
    protected String getSpi(String key) {
        return EHelper.callOnLiveUISync(session, () -> {
            PrefsCache c = PrefsCache.current();
            c.ensureLoaded();
            return c.get(absolutePath(), key);
        });
    }

    @Override
    protected void putSpi(String key, String value) {
        EHelper.callOnLiveUISync(session, () -> {
            PrefsCache c = PrefsCache.current();
            c.ensureLoaded();
            c.put(absolutePath(), key, value);
            return null;
        });
    }

    @Override
    protected void removeSpi(String key) {
        EHelper.callOnLiveUISync(session, () -> {
            PrefsCache c = PrefsCache.current();
            c.ensureLoaded();
            c.remove(absolutePath(), key);
            return null;
        });
    }

    @Override
    protected String[] keysSpi() {
        return EHelper.callOnLiveUISync(session, () -> {
            PrefsCache c = PrefsCache.current();
            c.ensureLoaded();
            return c.keys(absolutePath());
        });
    }

    @Override
    protected String[] childrenNamesSpi() {
        return EHelper.callOnLiveUISync(session, () -> {
            PrefsCache c = PrefsCache.current();
            c.ensureLoaded();
            return c.childrenNames(absolutePath());
        });
    }

    @Override
    protected void removeNodeSpi() {
        EHelper.callOnLiveUISync(session, () -> {
            PrefsCache.current().removeNode(absolutePath());
            return null;
        });
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return new BridgedPreferences(this, name);
    }

    /** Writes are eager (each {@code put}/{@code remove} flushes to localStorage). */
    @Override
    protected void flushSpi() {
    }

    /** localStorage is the store; nothing to reconcile server-side. */
    @Override
    protected void syncSpi() {
    }
}
