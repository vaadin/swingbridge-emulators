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

package vaadinx.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests for the light Calendar emulator. {@link AbstractKaribuTest} makes the test
 * thread a UI thread with a UTC browser zone; background-thread behavior runs
 * on a fresh {@link Thread}. Several tests set a non-UTC JVM default so a leak to the
 * server zone would be detectable.
 */
class CalendarTest extends AbstractKaribuTest {

    /**
     * Runs {@code block} on a fresh thread and returns its result, rethrowing
     * whatever it threw — a bare {@code Thread} swallows the throwable, which
     * would turn a regression into a silently passing test.
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
        if (r instanceof Throwable thrown) {
            throw thrown instanceof RuntimeException re ? re : new IllegalStateException(thrown);
        }
        @SuppressWarnings("unchecked")
        T result = (T) r;
        return result;
    }

    @Test
    @DisplayName("getInstance uses the browser time zone")
    void getInstanceUsesBrowserZone() {
        assertEquals(0, Calendar.getInstance().getTimeZone().getRawOffset()); // UTC in tests
    }

    @Test
    @DisplayName("getInstance returns a GregorianCalendar (JDK-faithful)")
    void getInstanceReturnsGregorian() {
        assertInstanceOf(GregorianCalendar.class, Calendar.getInstance());
    }

    @Test
    @DisplayName("field constructor interprets the date in the browser zone, not the server zone")
    void fieldConstructorUsesBrowserZone() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00")); // server != user
            // month is 0-based: 5 = June.
            GregorianCalendar c = new GregorianCalendar(2020, 5, 15);
            assertEquals(Date.from(Instant.parse("2020-06-15T00:00:00Z")), c.getTime());
            assertEquals(2020, c.get(Calendar.YEAR));
            assertEquals(Calendar.JUNE, c.get(Calendar.MONTH));
            assertEquals(15, c.get(Calendar.DAY_OF_MONTH));
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    @DisplayName("explicit time zone is honored over the browser default")
    void explicitZoneWinsOverBrowserDefault() {
        assertEquals(3 * 3600 * 1000,
                new GregorianCalendar(TimeZone.getTimeZone("GMT+03:00")).getTimeZone().getRawOffset());
        assertEquals(3 * 3600 * 1000,
                Calendar.getInstance(TimeZone.getTimeZone("GMT+03:00")).getTimeZone().getRawOffset());
    }

    @Test
    @DisplayName("inherited field math still works")
    void inheritedFieldMathWorks() {
        GregorianCalendar c = new GregorianCalendar(2020, Calendar.JANUARY, 31);
        c.add(Calendar.MONTH, 1); // Jan 31 + 1 month → Feb 29 (2020 is a leap year)
        assertEquals(2020, c.get(Calendar.YEAR));
        assertEquals(Calendar.FEBRUARY, c.get(Calendar.MONTH));
        assertEquals(29, c.get(Calendar.DAY_OF_MONTH));
    }

    @Test
    @DisplayName("construction on a background thread inside a context uses the propagated zone")
    void backgroundThreadInsideContextUsesPropagatedZone() {
        EmulatorContext ctx = EmulatorContext.get(); // UTC, captured on the UI thread
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00"));
            int offset = onBackgroundThread(() -> {
                AtomicInteger ref = new AtomicInteger();
                ctx.run(() -> ref.set(new GregorianCalendar().getTimeZone().getRawOffset()));
                return ref.get();
            });
            assertEquals(0, offset); // propagated UTC, not the +9 server default
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    @DisplayName("construction on a bare background thread never throws and uses the server default")
    void bareBackgroundThreadFallsBackToServerDefault() {
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00"));
            int offset = onBackgroundThread(() -> new GregorianCalendar().getTimeZone().getRawOffset());
            assertEquals(9 * 3600 * 1000, offset); // server default, best-effort, no throw
        } finally {
            TimeZone.setDefault(saved);
        }
    }
}
