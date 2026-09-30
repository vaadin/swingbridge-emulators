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

package com.vaadin.swingbridge.surrogates.util;

import com.vaadin.swingbridge.surrogates.BrowserTimeZone;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Browser-zone bridge between the legacy {@link java.util.Date} world and
 * {@link java.time.LocalDate}. Every conversion here interprets a {@code Date}
 * against the <em>browser's</em> time zone (cached in {@link BrowserTimeZone}),
 * not the server JVM's {@code ZoneId.systemDefault()}.
 *
 * <p>These are a <b>compatibility bridge, not the preferred path</b>. The
 * migration arc past the {@code vaadinx.text.SimpleDateFormat} /
 * {@code vaadinx.util.Calendar} emulators is for code to move <em>toward</em>
 * modern {@code java.time} ({@link LocalDate} / {@link DateTimeFormatter})
 * directly; reach for these methods only where the app genuinely can't be
 * upgraded (spaghetti, old libraries, no in-house knowledge). Where you do use
 * them, use them at <em>every</em> {@code Date}↔wall-date seam so the whole
 * codebase shares one zone interpretation — the surrogate DatePicker peers use
 * these exact calls internally.
 *
 * <p>Shared contract of every method: returns {@code null} on null input, and
 * throws {@link IllegalStateException} if {@link BrowserTimeZone#fetch()} was not
 * called during UI init (R_match_swing_errors programming-error contract, SD_browser_timezone).
 *
 * <p>The arithmetic is a default {@link java.util.GregorianCalendar}'s in
 * {@code TimeZone.getTimeZone(BrowserTimeZone.get())} — what
 * {@code SimpleDateFormat} and {@code Calendar} run — so a picked date prints as
 * the date picked. {@code date.toInstant().atZone(zoneId)} is <em>not</em>
 * equivalent before 1900: {@code ZoneId} applies local mean time (Los Angeles
 * 1867: {@code -07:52:58}), {@code TimeZone} today's offset ({@code -08:00}), and
 * midnight in one is the previous day in the other. Before 1582-10-15 the
 * {@code LocalDate} carries the Julian label {@code SimpleDateFormat} prints.
 */
public final class BrowserDateUtils {

    private BrowserDateUtils() {}

    /**
     * Convert a {@link java.util.Date} to a {@link java.time.LocalDate} via the
     * browser's time zone — the inverse of {@link #toDate}. The browser zone,
     * not the server JVM's {@code ZoneId.systemDefault()}, is the one that
     * matters: a {@code Date} the user picks via a DatePicker peer is
     * interpreted in the browser's locale, and rendering it back must reverse
     * that mapping.
     */
    public static LocalDate toLocalDate(Date d) {
        if (d == null) return null;
        GregorianCalendar cal = browserCalendar();
        cal.setTime(d);
        int yearOfEra = cal.get(Calendar.YEAR);
        int year = cal.get(Calendar.ERA) == GregorianCalendar.BC ? 1 - yearOfEra : yearOfEra;
        return LocalDate.of(year, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * Convert a {@link java.time.LocalDate} to a {@link java.util.Date} at
     * start-of-day in the browser's time zone — the inverse of
     * {@link #toLocalDate}. Lossy for sub-day precision: any time-of-day
     * collapses to 00:00 across a round-trip through {@code LocalDate} (the
     * JSpinner / JFormattedTextField + DatePicker limitation per SD_sjspinner / SD_sjformatted_family).
     */
    public static Date toDate(LocalDate ld) {
        if (ld == null) return null;
        GregorianCalendar cal = browserCalendar();
        int year = ld.getYear();
        cal.set(Calendar.ERA, year <= 0 ? GregorianCalendar.BC : GregorianCalendar.AD);
        cal.set(Calendar.YEAR, year <= 0 ? 1 - year : year);
        cal.set(Calendar.MONTH, ld.getMonthValue() - 1);
        cal.set(Calendar.DAY_OF_MONTH, ld.getDayOfMonth());
        return cal.getTime();
    }

    /** A cleared, default-cutover calendar in the browser's zone, as {@code TimeZone} sees it. */
    private static GregorianCalendar browserCalendar() {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone(BrowserTimeZone.get()));
        cal.clear();
        return cal;
    }

    /**
     * Convenience for {@code toDate(LocalDate.of(year, month, day))} — midnight
     * on the given wall-date in the browser's time zone. {@code month} is
     * 1-indexed (January = 1), matching {@link java.time.LocalDate#of(int, int, int)}
     * and unlike the 0-indexed {@link java.util.Calendar} convention. Use at
     * every field-form {@code (y, m, d) → Date} seam (SpinnerDateModel bounds,
     * hardcoded seed dates) in place of the Swing-era
     * {@code cal.set(year, month - 1, day); cal.getTime();} idiom.
     */
    public static Date dateOf(int year, int month, int day) {
        return toDate(LocalDate.of(year, month, day));
    }

    /**
     * Format a {@link java.util.Date} as an ISO-8601 {@code "yyyy-MM-dd"} string
     * (browser-zone wall-date) — the read-only-display replacement for the
     * Swing-era {@code new SimpleDateFormat("yyyy-MM-dd").format(date)}. Matches
     * what a DatePicker peer renders for the same {@code Date} (both go through
     * {@link #toLocalDate}).
     */
    public static String formatAsISODate(Date d) {
        if (d == null) return null;
        return toLocalDate(d).toString();
    }

    /**
     * Format a {@link java.util.Date} with a {@link java.time.format.DateTimeFormatter}
     * pattern (browser-zone wall-date) — for when {@link #formatAsISODate} isn't
     * enough, e.g. a locale-friendly {@code "dd MMM yyyy"}. The pattern goes
     * straight to {@link java.time.format.DateTimeFormatter#ofPattern(String)},
     * whose {@code java.time} grammar differs subtly from legacy
     * {@link java.text.SimpleDateFormat} (e.g. {@code uuuu} vs {@code yyyy} for
     * year-of-era); throws {@link IllegalArgumentException} if malformed.
     */
    public static String formatAsDate(Date d, String pattern) {
        if (d == null) return null;
        return DateTimeFormatter.ofPattern(pattern).format(toLocalDate(d));
    }
}
