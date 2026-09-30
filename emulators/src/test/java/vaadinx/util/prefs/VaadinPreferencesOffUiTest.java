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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The JVM-scoped fall-through (D_prefs_scope_split). SB-Emulators' {@code PreferencesFactory} SPI is selected
 * JVM-wide, so {@code Preferences} callers with no active {@code EmulatorContext} — Vaadin's
 * commercial-component license checker, other libraries, a {@code static} initializer,
 * {@code main()} before {@code VaadinBoot.run()} — must NOT be routed to a user's browser
 * {@code localStorage} and must NOT throw. They land in the process-local in-memory
 * {@link JvmLocalPreferences} instead. No {@code @BeforeEach} Karibu setup here, so the test
 * thread has no {@code VaadinSession} / {@code EmulatorContext}, matching that shape.
 */
class VaadinPreferencesOffUiTest {

    @Test
    @DisplayName("userRoot with no EmulatorContext routes to the in-memory JVM store, not localStorage")
    void userRootOffContextIsJvmLocal() {
        // This is exactly what com.vaadin.pro.licensechecker.History does when an
        // RTE-backed JEditorPane renders: userNodeForPackage(...).putLong/getLong
        // off any UI. It must round-trip in-memory, never throw, never park.
        Preferences node = Preferences.userNodeForPackage(VaadinPreferencesOffUiTest.class);
        node.putLong("lastCheck", 12345L);
        assertEquals(12345L, node.getLong("lastCheck", -1L));

        // It is the JVM-local tree, not a browser-backed VaadinPreferences.
        assertInstanceOf(JvmLocalPreferences.class, Preferences.userRoot(),
                "off-context userRoot should be the JVM-local store");
    }

    @Test
    @DisplayName("absent key returns the supplied default")
    void absentKeyReturnsDefault() {
        Preferences node = Preferences.userNodeForPackage(VaadinPreferencesOffUiTest.class);
        assertNull(node.get("never-written-" + System.nanoTime(), null));
    }

    @Test
    @DisplayName("systemRoot stays usable with no UI (inert, no browser round-trip)")
    void systemRootStaysUsableWithNoUI() {
        // systemRoot is deferred/inert and needs no UI — it must not throw.
        Preferences.systemRoot().get("anything", "default");
    }
}
