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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.BackingStoreException;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the localStorage-backed {@link Preferences} implementation through the
 * JDK's own {@code Preferences} static API — which also verifies our
 * {@link VaadinPreferencesFactory} is the JVM-selected SPI factory (D_preferences).
 *
 * <p>Runs in {@link VaadinPreferencesFactory#setTestMode} so the per-UI {@code PrefsCache}
 * stays warm-and-empty and in-memory: no {@code executeJs} round-trip, no
 * virtual-thread requirement. A current UI is still required, so the
 * off-context throw contract stays exercised (see {@link VaadinPreferencesOffUiTest}).
 */
class VaadinPreferencesTest extends AbstractKaribuTest {

    @BeforeEach
    void enableTestMode() {
        VaadinPreferencesFactory.setTestMode(true);
    }

    @AfterEach
    void disableTestMode() {
        VaadinPreferencesFactory.setTestMode(false);
    }

    @Test
    @DisplayName("factory selection — userRoot is a VaadinPreferences")
    void userRootIsAVaadinPreferences() {
        assertInstanceOf(VaadinPreferences.class, Preferences.userRoot());
    }

    @Test
    @DisplayName("session current but unlocked routes to the JVM-local store "
            + "(license-checker case, D_prefs_scope_split)")
    void unlockedSessionRoutesToJvmLocal() {
        // Reproduces com.vaadin.pro.licensechecker.History: a Preferences access
        // on a thread that HAS a current VaadinSession (and UI) but does NOT
        // hold the session lock. Routing into PrefsCache here would throw
        // Vaadin's "Cannot access state ... without locking"; instead it must
        // fall through to the in-memory JvmLocalPreferences.
        VaadinSession session = VaadinSession.getCurrent();
        session.unlock();
        try {
            Preferences node = Preferences.userNodeForPackage(VaadinPreferencesTest.class);
            node.putLong("lastCheck", 999L);                    // the checker writes...
            assertEquals(999L, node.getLong("lastCheck", -1L));  // ...and reads back
            assertInstanceOf(JvmLocalPreferences.class, Preferences.userRoot());
        } finally {
            session.lock();   // restore for teardown
        }
    }

    @Test
    @DisplayName("put then get round-trips")
    void putThenGetRoundTrips() {
        Preferences p = Preferences.userRoot();
        p.put("greeting", "hello");
        assertEquals("hello", p.get("greeting", "default"));
    }

    @Test
    @DisplayName("get returns the supplied default for an absent key")
    void getReturnsDefaultForAbsentKey() {
        assertEquals("fallback", Preferences.userRoot().get("missing", "fallback"));
    }

    @Test
    @DisplayName("typed accessors round-trip")
    void typedAccessorsRoundTrip() {
        Preferences p = Preferences.userRoot().node("typed");
        p.putInt("width", 1024);
        p.putBoolean("maximized", true);
        p.putLong("lastRun", 42L);
        p.putDouble("ratio", 1.5);
        assertEquals(1024, p.getInt("width", 0));
        assertTrue(p.getBoolean("maximized", false));
        assertEquals(42L, p.getLong("lastRun", 0L));
        assertEquals(1.5, p.getDouble("ratio", 0.0));
    }

    @Test
    @DisplayName("typed accessor falls back to default on malformed stored value")
    void typedAccessorFallsBackOnMalformedValue() {
        Preferences p = Preferences.userRoot().node("typed");
        p.put("width", "not-a-number");
        assertEquals(800, p.getInt("width", 800));
    }

    @Test
    @DisplayName("userNodeForPackage round-trips")
    void userNodeForPackageRoundTrips() {
        Preferences p = Preferences.userNodeForPackage(VaadinPreferencesTest.class);
        p.put("lastDir", "/home/joe/docs");
        assertEquals("/home/joe/docs",
                Preferences.userNodeForPackage(VaadinPreferencesTest.class).get("lastDir", ""));
    }

    @Test
    @DisplayName("keys lists the node's stored keys")
    void keysListsStoredKeys() throws BackingStoreException {
        Preferences p = Preferences.userRoot().node("keys-test");
        p.put("a", "1");
        p.put("b", "2");
        assertEquals(Set.of("a", "b"), Set.of(p.keys()));
    }

    @Test
    @DisplayName("child node hierarchy and childrenNames")
    void childNodeHierarchy() throws BackingStoreException {
        Preferences root = Preferences.userRoot();
        root.node("editor/fonts").put("family", "monospaced");
        assertTrue(Arrays.asList(root.childrenNames()).contains("editor"));
        assertTrue(Arrays.asList(root.node("editor").childrenNames()).contains("fonts"));
        assertEquals("monospaced", root.node("editor/fonts").get("family", ""));
    }

    @Test
    @DisplayName("remove drops a single key")
    void removeDropsASingleKey() {
        Preferences p = Preferences.userRoot().node("removal");
        p.put("k", "v");
        p.remove("k");
        assertEquals("gone", p.get("k", "gone"));
    }

    @Test
    @DisplayName("removeNode drops the whole node")
    void removeNodeDropsTheWholeNode() throws BackingStoreException {
        Preferences root = Preferences.userRoot();
        root.node("doomed").put("k", "v");
        assertTrue(root.nodeExists("doomed"));
        root.node("doomed").removeNode();
        assertFalse(root.nodeExists("doomed"));
    }

    @Test
    @DisplayName("PreferenceChangeListener fires on put")
    void preferenceChangeListenerFiresOnPut() throws InterruptedException {
        Preferences p = Preferences.userRoot().node("listener");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PreferenceChangeEvent> seen = new AtomicReference<>();
        p.addPreferenceChangeListener(ev -> {
            seen.set(ev);
            latch.countDown();
        });
        p.put("theme", "dark");
        // AbstractPreferences dispatches change events on its own daemon thread.
        assertTrue(latch.await(2, TimeUnit.SECONDS), "change event did not fire");
        assertEquals("theme", seen.get().getKey());
        assertEquals("dark", seen.get().getNewValue());
    }

    @Test
    @DisplayName("systemRoot put WARNs and reads back empty")
    void systemRootPutWarnsAndReadsBackEmpty() throws BackingStoreException {
        List<String> warns = new ArrayList<>();
        Consumer<String> prior = EHelper.warnHook;
        EHelper.warnHook = warns::add;
        try {
            Preferences sys = Preferences.systemRoot();
            sys.put("shared", "x");
            assertEquals("empty", sys.get("shared", "empty"));
            assertEquals(0, sys.keys().length);
        } finally {
            EHelper.warnHook = prior;
        }
        assertTrue(warns.stream().anyMatch(w -> w.contains("SystemPreferences")),
                "expected a systemRoot WARN, got: " + warns);
    }
}
