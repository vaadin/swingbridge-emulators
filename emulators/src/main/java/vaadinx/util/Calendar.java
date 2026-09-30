/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.util.Calendar
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.util;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Import-swap emulator for {@link java.util.Calendar}
 * ({@code java.util.Calendar} → {@code vaadinx.util.Calendar}). Hosts the
 * {@code getInstance(...)} factories; the concrete work is done by
 * {@link GregorianCalendar}, which {@code getInstance} returns (matching the
 * JDK, where {@code Calendar.getInstance()} hands back a {@code GregorianCalendar}
 * in the common locales).
 *
 * <p><b>Why this exists.</b> On the desktop one JVM serves one user, so
 * {@code Calendar.getInstance()} / {@code new GregorianCalendar(...)} pick up
 * {@code TimeZone.getDefault()} — which <em>is</em> the user's zone. On the
 * server the default is the <em>server's</em> zone, so a migrated
 * {@code cal.set(y, m, d); cal.getTime()} silently computes the wrong instant
 * (server midnight, not the user's). This emulator defaults construction to the
 * user's <b>browser</b> zone instead, via
 * {@link vaadinx.EmulatorContext#currentTimeZone()} — reusing the same
 * resolution the {@code SimpleDateFormat} emulator uses: browser zone on a UI
 * thread (throwing SD_browser_timezone if {@code BrowserTimeZone.fetch()} was never called),
 * the propagated zone on a background thread carrying an
 * {@link vaadinx.EmulatorContext}, or the server default (with a WARN) on a bare
 * background thread. An explicitly supplied {@link TimeZone} always wins.
 *
 * <p><b>Not a facade — a thin zone-defaulting subclass.</b> Unlike the
 * {@code SimpleDateFormat} emulator (which routes through a hidden per-session
 * backing), this holds no extra state and overrides no behavior beyond the
 * construction-time zone: every {@code get}/{@code set}/{@code add}/{@code roll}
 * is inherited {@link java.util.GregorianCalendar} field math, unchanged. The
 * returned object is a plain, thread-confined Calendar.
 *
 * <p><b>It does NOT make a shared Calendar thread-safe.</b> A {@code Calendar}
 * stashed in a {@code static}/shared field and mutated from multiple threads is
 * unsound on a multi-user server — as it already was racy on the desktop, only
 * masked by single-threaded EDT use. The migration answer for that shape is to
 * <em>unshare</em> it (construct one per use); it is flagged for manual fixing,
 * not silently emulated. This emulator targets the dominant one-off idiom.
 *
 * <p>The zone is applied via {@link TimeZone#getTimeZone(java.time.ZoneId)} — the
 * same arithmetic the {@code DatePicker}-backed surrogates convert with, so a date
 * built here shows its own day in a picker in every year, pre-1900 included.
 *
 * <p>Emulator-only — no surrogate; not a Component (D_timer_swingworker sibling of
 * {@code SimpleDateFormat} / {@code SwingWorker}).
 *
 * @deprecated Transitional scaffolding produced by the import-swap. It keeps
 * migrated {@code Calendar} code running with the right zone; hand-written and
 * stage-3 code should use {@code java.time} (e.g. {@code LocalDate}/{@code ZonedDateTime}
 * with {@code com.vaadin.swingbridge.surrogates.BrowserTimeZone.get()}) or
 * {@code com.vaadin.swingbridge.surrogates.SHelper}, which are immutable and {@code ZoneId}-consistent.
 */
@Deprecated
public abstract class Calendar extends java.util.GregorianCalendar {

    // Protected forwarding ctors — reached only via GregorianCalendar's super(...)
    // calls (this class is abstract). They carry the "raw" JDK zone/locale;
    // GregorianCalendar layers the browser-zone default on top.

    protected Calendar() {
        super();
    }

    protected Calendar(TimeZone zone) {
        super(zone);
    }

    protected Calendar(Locale aLocale) {
        super(aLocale);
    }

    protected Calendar(TimeZone zone, Locale aLocale) {
        super(zone, aLocale);
    }

    // Factories — mirror java.util.Calendar.getInstance(...). Each returns a
    // browser-zoned GregorianCalendar (or an explicitly-zoned one).

    public static Calendar getInstance() {
        return new GregorianCalendar();
    }

    public static Calendar getInstance(TimeZone zone) {
        return new GregorianCalendar(zone);
    }

    public static Calendar getInstance(Locale aLocale) {
        return new GregorianCalendar(aLocale);
    }

    public static Calendar getInstance(TimeZone zone, Locale aLocale) {
        return new GregorianCalendar(zone, aLocale);
    }
}
