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

import com.vaadin.swingbridge.surrogates.AbstractKaribuTest;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link BrowserDateUtils} converts the way {@link java.text.SimpleDateFormat} /
 * {@link java.util.GregorianCalendar} do in the browser zone, so a date picker and the
 * app's own date code agree on every {@code Date} — including the pre-standard-time
 * dates where {@code java.util.TimeZone} and {@code java.time.ZoneId} disagree.
 */
class BrowserDateUtilsTest extends AbstractKaribuTest {

    /** Both LMT directions: west of the standard meridian, east of it, and on it. */
    private static final List<String> ZONES = List.of(
            "America/Los_Angeles", "America/New_York", "Asia/Kolkata",
            "Pacific/Auckland", "Europe/Berlin", "Europe/Prague", "Europe/London", "UTC");

    private static final List<LocalDate> DATES = List.of(
            LocalDate.of(1815, 12, 10), LocalDate.of(1867, 11, 7), LocalDate.of(1899, 6, 1),
            LocalDate.of(1950, 6, 1), LocalDate.of(1990, 5, 5), LocalDate.of(2026, 3, 29));

    private static SimpleDateFormat isoIn(ZoneId zone) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(TimeZone.getTimeZone(zone));
        return fmt;
    }

    @Test
    @DisplayName("toDate formats as the same day, toLocalDate reads the day SimpleDateFormat parses")
    void agreesWithSimpleDateFormat() throws Exception {
        for (String zoneId : ZONES) {
            ZoneId zone = ZoneId.of(zoneId);
            BrowserTimeZone.setZoneId(zone);
            SimpleDateFormat fmt = isoIn(zone);
            for (LocalDate ld : DATES) {
                assertEquals(ld.toString(), fmt.format(BrowserDateUtils.toDate(ld)), zoneId + " " + ld);
                assertEquals(ld, BrowserDateUtils.toLocalDate(fmt.parse(ld.toString())), zoneId + " " + ld);
            }
        }
    }

    @Test
    @DisplayName("round-trips LocalDate -> Date -> LocalDate in every zone")
    void roundTrips() {
        for (String zoneId : ZONES) {
            BrowserTimeZone.setZoneId(ZoneId.of(zoneId));
            for (LocalDate ld : DATES) {
                assertEquals(ld, BrowserDateUtils.toLocalDate(BrowserDateUtils.toDate(ld)), zoneId);
            }
        }
    }

    @Test
    @DisplayName("before the 1582 cutover the LocalDate carries SimpleDateFormat's Julian label")
    void julianLabelBeforeCutover() throws Exception {
        BrowserTimeZone.setZoneId(ZoneId.of("Europe/Prague"));
        Date d = isoIn(BrowserTimeZone.get()).parse("1500-03-01");
        assertEquals(LocalDate.of(1500, 3, 1), BrowserDateUtils.toLocalDate(d));
        assertEquals(d, BrowserDateUtils.toDate(LocalDate.of(1500, 3, 1)));
    }

    @Test
    @DisplayName("BC years map onto LocalDate's proleptic year numbering")
    void bcYears() {
        BrowserTimeZone.setZoneId(ZoneId.of("Europe/Berlin"));
        for (LocalDate ld : List.of(LocalDate.of(0, 6, 1), LocalDate.of(-43, 3, 15))) {
            assertEquals(ld, BrowserDateUtils.toLocalDate(BrowserDateUtils.toDate(ld)));
        }
    }

    @Test
    @DisplayName("null in, null out")
    void nulls() {
        assertNull(BrowserDateUtils.toDate(null));
        assertNull(BrowserDateUtils.toLocalDate(null));
    }
}
