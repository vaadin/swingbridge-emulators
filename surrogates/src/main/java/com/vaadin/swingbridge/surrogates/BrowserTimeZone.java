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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ExtendedClientDetails;
import com.vaadin.flow.server.VaadinSession;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Surrogate-side cache for the browser's IANA time zone, populated from
 * Vaadin's {@link ExtendedClientDetails} during UI init and read back by
 * {@link com.vaadin.swingbridge.surrogates.util.BrowserDateUtils#toLocalDate} / {@link com.vaadin.swingbridge.surrogates.util.BrowserDateUtils#toDate} on every
 * {@code Date} ↔ {@code LocalDate} round-trip.
 *
 * <p>Migrators MUST call {@link #fetch()} from a UI init listener before
 * any view that uses {@code SJSpinner(SpinnerDateModel)} or
 * {@code SJFormattedDatePicker} is reached. {@link #get()} throws
 * {@link IllegalStateException} if the cache is empty — a programming
 * error (R_match_swing_errors, D_gap_severity_triage case-1 family, SD_browser_timezone), not a soft incompleteness. Failing
 * loud here is much easier to diagnose than dates silently rendering an
 * hour off because the server JVM's time zone diverged from the user's
 * browser.
 *
 * <p>Slimmed Java port of
 * <a href="https://github.com/mvysny/karibu-tools/blob/main/karibu-tools/src/main/kotlin/BrowserTimeZone.kt">karibu-tools'
 * {@code BrowserTimeZone.kt}</a>: we cache the derived {@link ZoneId}
 * rather than the whole {@code ExtendedClientDetails}, since the surrogate
 * layer only ever reads the zone, and tests install a deterministic zone
 * via {@link #setZoneId(ZoneId)} without fabricating a fake ECD.
 */
public final class BrowserTimeZone {

    private BrowserTimeZone() {}

    /**
     * Reads the current UI's cached {@link ExtendedClientDetails}; if
     * already populated by Vaadin (placeholder values mean the browser
     * hasn't responded yet — Vaadin uses {@code screenWidth == -1} as the
     * sentinel), derives the {@link ZoneId} synchronously. Otherwise
     * triggers {@link ExtendedClientDetails#refresh} and stores the zone
     * in the callback. No-op if the session already has a stored zone
     * (typical on a refresh through {@code @PreserveOnRefresh}, or in
     * tests where {@link #setZoneId} was called directly).
     *
     * <p>Call this from a UI init listener — once per session is enough.
     */
    public static void fetch() {
        if (VaadinSession.getCurrent().getAttribute(ZoneId.class) != null) return;
        ExtendedClientDetails details = UI.getCurrent().getPage().getExtendedClientDetails();
        if (details.getScreenWidth() != -1) {
            setZoneId(extractZoneId(details));
        } else {
            details.refresh(d -> setZoneId(extractZoneId(d)));
        }
    }

    /**
     * Returns the cached browser {@link ZoneId}. Throws if the cache is
     * empty — {@link com.vaadin.swingbridge.surrogates.util.BrowserDateUtils#toLocalDate} / {@link com.vaadin.swingbridge.surrogates.util.BrowserDateUtils#toDate} trust
     * this to be populated, so a missing zone is a programming error
     * (forgot to call {@link #fetch()} during UI init).
     *
     * <p><b>Convert a {@code Date} through
     * {@link com.vaadin.swingbridge.surrogates.util.BrowserDateUtils}, not
     * {@code date.toInstant().atZone(get())}.</b> The DatePicker peers convert
     * with {@code java.util.TimeZone}'s arithmetic, which for pre-1900 dates
     * differs from this {@link ZoneId}'s local mean time by up to a day. Pure
     * {@code java.time} seams (e.g. {@link java.time.format.DateTimeFormatter#withZone}
     * over a {@code LocalDate}) can take this value directly.
     */
    public static ZoneId get() {
        ZoneId zone = getOrNull();
        if (zone == null) {
            throw new IllegalStateException(
                    "BrowserTimeZone not populated — call BrowserTimeZone.fetch() "
                            + "from a UI init listener before any view using "
                            + "SJSpinner(SpinnerDateModel) or SJFormattedDatePicker "
                            + "is reached. See SD_browser_timezone.");
        }
        return zone;
    }

    /**
     * Lenient variant of {@link #get()}: returns the cached browser
     * {@link ZoneId}, or {@code null} when none is stored or no session is
     * current — never throws. For callers that can fall back to a default zone
     * rather than failing, e.g. {@code vaadinx.EmulatorContext} snapshotting the
     * zone on a UI thread for background-thread propagation.
     *
     * <p><b>Lock-safe.</b> Reading {@link VaadinSession#getAttribute} requires the
     * session lock, so on a session-associated thread that doesn't hold it (e.g.
     * Vaadin's license checker on a Jetty request-pool thread, or a
     * {@code SwingWorker} reaching back through its session handle) this
     * <b>briefly acquires the lock, reads, and releases</b> rather than throwing
     * "Cannot access state … without locking" — honoring the never-throw contract
     * and returning the real zone instead of a fallback. The session lock is
     * reentrant, so the {@code hasLock()} fast path avoids a redundant re-lock
     * when we already hold it.
     */
    public static ZoneId getOrNull() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return null;
        }
        if (session.hasLock()) {
            return session.getAttribute(ZoneId.class);
        }
        session.lock();
        try {
            return session.getAttribute(ZoneId.class);
        } finally {
            session.unlock();
        }
    }

    /**
     * Stores the given zone in the current session. Used internally by the
     * {@link #fetch()} callback; tests use it directly to install a
     * deterministic zone without making a real browser round-trip.
     */
    public static void setZoneId(ZoneId zone) {
        VaadinSession.getCurrent().setAttribute(ZoneId.class, zone);
    }

    /**
     * Mirrors karibu-tools' {@code ExtendedClientDetails.timeZone}: prefer
     * the IANA {@code timeZoneId} (carries DST history, important for
     * historical dates); fall back to the raw {@code timezoneOffset} in
     * milliseconds when only a fixed offset is available.
     */
    private static ZoneId extractZoneId(ExtendedClientDetails d) {
        String tzId = d.getTimeZoneId();
        if (tzId != null && !tzId.isBlank()) {
            return ZoneId.of(tzId);
        }
        return ZoneOffset.ofTotalSeconds(d.getTimezoneOffset() / 1000);
    }
}
