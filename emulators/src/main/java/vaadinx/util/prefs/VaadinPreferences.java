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

import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

/**
 * A {@code java.util.prefs.Preferences} node backed by the browser's
 * {@code localStorage} — the web-faithful analog of Swing's machine-local
 * preferences store (Windows registry / {@code ~/.java/.userPrefs/} / macOS
 * plist). Selected JVM-wide by {@link VaadinPreferencesFactory} through the
 * standard {@code PreferencesFactory} SPI, so migrated code keeps calling
 * {@code Preferences.userNodeForPackage(...)} unchanged. See D_preferences.
 *
 * <p>Extending {@link AbstractPreferences} means the tree walk,
 * {@code keys()}/{@code childrenNames()}/{@code node(path)}/{@code removeNode()},
 * and the {@code PreferenceChangeListener}/{@code NodeChangeListener} fan-out
 * all come from the JDK. This class implements only the nine backing
 * {@code *Spi} methods, delegating storage to the per-UI {@link PrefsCache}.
 *
 * <p>The class is {@code public} only so tests and diagnostics can name the
 * type; every constructor is non-public, so user code cannot instantiate a node
 * directly (it obtains nodes via {@code Preferences.userRoot()} et al.).
 *
 * <p>Change listeners fire for local mutations (this UI) via
 * {@link AbstractPreferences}; a mutation in another browser tab does not fire
 * here — the documented multi-tab gap (D_preferences).
 */
public final class VaadinPreferences extends AbstractPreferences {

    private final PrefsCache cache;

    /** Root-node constructor, bound to the current UI's cache. Called by {@link PrefsCache#root()}. */
    VaadinPreferences(PrefsCache cache) {
        super(null, "");
        this.cache = cache;
    }

    /** Child-node constructor; inherits the parent's cache binding. */
    private VaadinPreferences(VaadinPreferences parent, String name) {
        super(parent, name);
        this.cache = parent.cache;
    }

    // Every Spi op warms the cache first: reads must never see a half-populated
    // tree, and a put must not overwrite a node blob whose other keys we haven't
    // read back from localStorage yet.

    @Override
    protected String getSpi(String key) {
        cache.ensureLoaded();
        return cache.get(absolutePath(), key);
    }

    @Override
    protected void putSpi(String key, String value) {
        cache.ensureLoaded();
        cache.put(absolutePath(), key, value);
    }

    @Override
    protected void removeSpi(String key) {
        cache.ensureLoaded();
        cache.remove(absolutePath(), key);
    }

    @Override
    protected String[] keysSpi() throws BackingStoreException {
        cache.ensureLoaded();
        return cache.keys(absolutePath());
    }

    @Override
    protected String[] childrenNamesSpi() throws BackingStoreException {
        cache.ensureLoaded();
        return cache.childrenNames(absolutePath());
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return new VaadinPreferences(this, name);
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
        cache.ensureLoaded();
        cache.removeNode(absolutePath());
    }

    // localStorage writes are eager (fire-and-forget per mutation), so there is
    // nothing buffered to flush and nothing external to pull back in. syncSpi's
    // "re-read from store" is a no-op by the same multi-tab reasoning as D_preferences —
    // a storage-event bridge would be the place to make it meaningful.
    @Override
    protected void syncSpi() throws BackingStoreException {
        // no-op — see class javadoc / D_preferences
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        // no-op — writes already persisted eagerly
    }

    /**
     * Always {@code true}. Overridden so we don't inherit
     * {@link AbstractPreferences#isUserNode()}'s default, which compares against
     * {@code Preferences.userRoot()} — that would re-enter our factory and
     * require a current UI just to answer a boolean.
     */
    @Override
    public boolean isUserNode() {
        return true;
    }
}
