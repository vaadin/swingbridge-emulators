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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.AbstractPreferences;

/**
 * A process-global, in-memory {@code Preferences} tree — the JVM-scoped branch
 * {@link VaadinPreferencesFactory} takes when no {@link vaadinx.EmulatorContext}
 * is active on the calling thread (D_prefs_scope_split). It exists because SB-Emulators'
 * {@code PreferencesFactory} SPI is selected <em>JVM-wide</em>, so callers that
 * have nothing to do with a migrated Swing app — most concretely Vaadin's own
 * commercial-component license checker
 * ({@code com.vaadin.pro.licensechecker.History}, which reads/writes
 * {@code Preferences.userNodeForPackage(...)} off any UI when an RTE-backed
 * {@code JEditorPane} renders) — also land here. Routing those to the browser's
 * {@code localStorage} is wrong (no UI to reach, and it isn't the user's data),
 * so they get this instead: a plain heap tree, no browser round-trip, no UI
 * requirement, never throws.
 *
 * <p><b>Ephemeral by design.</b> Nothing persists across JVM restarts. For the
 * license checker that means its "last check" cache starts cold each boot (it
 * re-validates rather than throttling — correct, just unthrottled). In-memory is
 * chosen over delegating to the JDK's file store precisely so a
 * <em>mis</em>classified app-pref write (migrated code on a raw background thread
 * that never propagated its {@link vaadinx.EmulatorContext}) is lost to a
 * process-local heap rather than silently persisted to the <em>server's</em>
 * shared {@code ~/.java/.userPrefs} — the wrong machine, and cross-user on a
 * deployed server. See D_prefs_scope_split.
 *
 * <p>Thread-safe: {@link AbstractPreferences} holds each node's lock across its
 * {@code *Spi} calls, and the per-node maps are {@link ConcurrentHashMap} so the
 * cross-node child registry is safe too.
 */
public final class JvmLocalPreferences extends AbstractPreferences {

    private static final JvmLocalPreferences USER_ROOT = new JvmLocalPreferences(null, "");

    /** The single process-global user root. */
    static JvmLocalPreferences root() {
        return USER_ROOT;
    }

    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<String, JvmLocalPreferences> children = new ConcurrentHashMap<>();

    private JvmLocalPreferences(JvmLocalPreferences parent, String name) {
        super(parent, name);
    }

    @Override
    protected void putSpi(String key, String value) {
        values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
        return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
        values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
        JvmLocalPreferences parent = (JvmLocalPreferences) parent();
        if (parent != null) {
            parent.children.remove(name());
        }
    }

    @Override
    protected String[] keysSpi() {
        return values.keySet().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() {
        return children.keySet().toArray(new String[0]);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return children.computeIfAbsent(name, n -> new JvmLocalPreferences(this, n));
    }

    /** No backing store to flush to — writes are already in the heap map. */
    @Override
    protected void flushSpi() {
    }

    /** No backing store to reconcile with — the heap map is the whole store. */
    @Override
    protected void syncSpi() {
    }
}
