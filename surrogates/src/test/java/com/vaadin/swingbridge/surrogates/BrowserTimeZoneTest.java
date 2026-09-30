/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link BrowserTimeZone#getOrNull}'s lock-safety contract (D_prefs_scope_split Part 1).
 * {@link AbstractKaribuTest} makes the test thread a locked UI thread with UTC
 * installed via {@code setZoneId}.
 */
class BrowserTimeZoneTest extends AbstractKaribuTest {

    @Test
    @DisplayName("getOrNull on a locked UI thread returns the stored zone")
    void getOrNullOnALockedUiThreadReturnsTheStoredZone() {
        assertEquals(ZoneOffset.UTC, BrowserTimeZone.getOrNull());
    }

    @Test
    @DisplayName("getOrNull briefly locks and returns the zone on an unlocked session")
    void getOrNullBrieflyLocksAndReturnsTheZoneOnAnUnlockedSession() {
        // The license-checker / background-thread shape: a current session we do
        // not hold the lock on. getOrNull() must not throw "Cannot access state …
        // without locking"; it briefly locks, reads, and returns the real zone.
        VaadinSession session = VaadinSession.getCurrent();
        session.unlock();
        try {
            assertEquals(ZoneOffset.UTC, BrowserTimeZone.getOrNull());
        } finally {
            session.lock();   // restore for MockVaadin.tearDown()
        }
    }

    @Test
    @DisplayName("getOrNull returns null when there is no current session")
    void getOrNullReturnsNullWhenThereIsNoCurrentSession() throws Throwable {
        // A fresh thread has no VaadinSession.getCurrent().
        AtomicReference<Object> slot = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                slot.set(BrowserTimeZone.getOrNull());
            } catch (Throwable e) {
                slot.set(e);
            }
        });
        t.start();
        t.join();
        Object r = slot.get();
        if (r instanceof Throwable throwable) {
            throw throwable;
        }
        assertNull(r, "no current session → null, no throw");
    }
}
