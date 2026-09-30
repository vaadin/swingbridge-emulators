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

package vaadinx.text;

import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link SimpleDateFormat} emulator. {@link AbstractKaribuTest} pre-installs a
 * live Vaadin session with a UTC {@link com.vaadin.swingbridge.surrogates.BrowserTimeZone}, so the test
 * thread is a "UI thread"; background-thread behaviour is exercised by running on
 * a fresh {@link Thread} (no {@code VaadinSession.getCurrent()}).
 */
@SuppressWarnings("deprecation") // SimpleDateFormat itself is the deprecated type under test
class SimpleDateFormatTest extends AbstractKaribuTest {

    /** Fixed instant: 1970-01-01T00:00:00Z. */
    private final Date epoch = new Date(0);

    /**
     * Runs {@code block} on a fresh thread (no current VaadinSession) and returns its
     * result, rethrowing any exception on the caller thread so {@code assertThrows} works.
     */
    private static <T> T onBackgroundThread(Supplier<T> block) {
        AtomicReference<Object> slot = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                slot.set(block.get());
            } catch (Throwable e) {
                slot.set(e);
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Object r = slot.get();
        if (r instanceof Throwable throwable) {
            throw throwable instanceof RuntimeException re ? re : new IllegalStateException(throwable);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) r;
        return typed;
    }

    /** The void form — nothing to carry back from the background thread. */
    private static void onBackgroundThread(Runnable block) {
        onBackgroundThread(() -> {
            block.run();
            return null;
        });
    }

    @Test
    @DisplayName("background thread genuinely has no current session")
    void backgroundThreadGenuinelyHasNoCurrentSession() {
        assertNull(onBackgroundThread(VaadinSession::getCurrent));
    }

    // --- Construction ----------------------------------------------------

    @Test
    @DisplayName("construction never touches the session or a zone (STUMBLE-2)")
    void constructionNeverTouchesTheSessionOrAZone() {
        // Constructing on a background thread with no session must not throw —
        // this is the `static final SimpleDateFormat` class-load case.
        String out = onBackgroundThread(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(epoch));
        assertFalse(out.isEmpty());
    }

    // --- Zone selection --------------------------------------------------

    @Test
    @DisplayName("formatting under a session uses the browser time zone")
    void formattingUnderASessionUsesTheBrowserTimeZone() {
        // BrowserTimeZone is UTC in tests.
        assertEquals("1970-01-01 00:00:00",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(epoch));
    }

    @Test
    @DisplayName("date-time patterns work (what SHelper structurally cannot do)")
    void dateTimePatternsWork() {
        assertEquals("00:00:00 01/01/1970",
                new SimpleDateFormat("HH:mm:ss dd/MM/yyyy").format(epoch));
    }

    @Test
    @DisplayName("background thread falls back to the server default zone")
    void backgroundThreadFallsBackToTheServerDefaultZone() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+05:00"));
            String out = onBackgroundThread(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(epoch));
            // +05:00 default, NOT the UTC browser zone — proves the two paths
            // pick different zones, and the bg path matches the original code's
            // JVM-default behaviour on that thread.
            assertEquals("1970-01-01 05:00:00", out);
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    @DisplayName("explicit setTimeZone wins over the browser-zone default")
    void explicitSetTimeZoneWinsOverTheBrowserZoneDefault() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // Configure on a background thread (no session) → writes the template,
        // marks the zone explicit; must survive into the session backing.
        onBackgroundThread(() -> sdf.setTimeZone(TimeZone.getTimeZone("GMT+02:00")));
        assertEquals("1970-01-01 02:00:00", sdf.format(epoch));
    }

    // --- Throw / degrade matrix -----------------------------------------

    @Test
    @DisplayName("session present but zone unfetched throws (SD_browser_timezone)")
    void sessionPresentButZoneUnfetchedThrows() {
        VaadinSession.getCurrent().setAttribute(ZoneId.class, null);
        assertThrows(IllegalStateException.class,
                () -> new SimpleDateFormat("yyyy-MM-dd").format(epoch));
    }

    @Test
    @DisplayName("background formatting inside an EmulatorContext uses the propagated browser zone")
    void backgroundFormattingInsideAnEmulatorContextUsesThePropagatedBrowserZone() {
        EmulatorContext ctx = EmulatorContext.get(); // UTC, captured on the UI thread
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00")); // server != user
            String out = onBackgroundThread(() -> {
                AtomicReference<String> ref = new AtomicReference<>();
                ctx.run(() -> ref.set(sdf.format(epoch)));
                return ref.get();
            });
            assertEquals("1970-01-01 00:00:00", out); // propagated UTC, not the +09:00 server zone
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    @DisplayName("formatting on a background thread never throws even without a zone")
    void formattingOnABackgroundThreadNeverThrowsEvenWithoutAZone() {
        // No session at all → best-effort, server zone, no throw.
        String out = onBackgroundThread(() -> new SimpleDateFormat("yyyy-MM-dd").format(epoch));
        assertFalse(out.isEmpty());
    }

    @Test
    @DisplayName("reconfiguring from a background thread after first use throws")
    void reconfiguringFromABackgroundThreadAfterFirstUseThrows() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.format(epoch); // first use on the UI thread → freezes the template
        assertThrows(IllegalStateException.class,
                () -> onBackgroundThread(() -> sdf.setLenient(false)));
    }

    // --- Configuration routing ------------------------------------------

    @Test
    @DisplayName("no-session config writes the template and replays into each backing")
    void noSessionConfigWritesTheTemplateAndReplaysIntoEachBacking() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        // Background + not yet used → template write (the static-init window).
        onBackgroundThread(() -> sdf.setLenient(false));
        // Session use builds a backing cloned from the template.
        assertFalse(sdf.isLenient());
    }

    @Test
    @DisplayName("UI-thread config mutates this session's backing")
    void uiThreadConfigMutatesThisSessionsBacking() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        assertTrue(sdf.isLenient()); // JDK default
        sdf.setLenient(false);
        assertFalse(sdf.isLenient());
    }

    // --- Round-trip ------------------------------------------------------

    @Test
    @DisplayName("parse round-trips under the browser zone")
    void parseRoundTripsUnderTheBrowserZone() throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        assertEquals(0L, sdf.parse("1970-01-01 00:00:00").getTime());
    }

    // --- Leak safety -----------------------------------------------------

    @Test
    @DisplayName("a dropped transient formatter is not pinned by the session")
    void aDroppedTransientFormatterIsNotPinnedByTheSession() throws InterruptedException {
        // A formatter created + used once, then dropped (the `new SimpleDateFormat`
        // -in-a-loop shape), must be GC-eligible — the per-session backing registry
        // keys it weakly. With a strong-keyed map this fails: the session pins it.
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.format(epoch); // registers a session backing keyed by this emulator
        WeakReference<SimpleDateFormat> ref = new WeakReference<>(sdf);
        sdf = null;
        for (int i = 0; i < 50; i++) {
            System.gc();
            if (ref.get() == null) {
                break;
            }
            Thread.sleep(10);
        }
        assertNull(ref.get(),
                "a dropped formatter must be collectible — the session must not strong-pin it");
    }

    // --- Override-completeness (zone poison) ----------------------------

    @Test
    @DisplayName("format and parse ignore the decoy inherited state")
    void formatAndParseIgnoreTheDecoyInheritedState() throws ParseException {
        // The inherited java.text.SimpleDateFormat captures the JVM default zone at
        // construction. Forcing that default to differ from the browser zone poisons
        // the decoy: any un-overridden format/parse path that fell through to the
        // inherited (server-zoned) state would render +14h, while our backing
        // re-zones every operation to the browser UTC. Forcing +14 (rather than
        // trusting the assertion alone) means the guard holds even when CI's default
        // zone is already UTC. Reflection-nulling the protected `calendar` field
        // would be the literal poison but needs --add-opens java.base/java.text;
        // a divergent default zone targets the same leak with no module fuss.
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+14:00"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            assertEquals("1970-01-01 00:00:00", sdf.format(epoch));
            assertEquals(0L, sdf.parse("1970-01-01 00:00:00").getTime());
        } finally {
            TimeZone.setDefault(saved);
        }
    }
}
