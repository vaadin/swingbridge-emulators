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
 * This file is derived from OpenJDK's java.util.GregorianCalendar
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.util;

import vaadinx.EmulatorContext;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Import-swap emulator for {@link java.util.GregorianCalendar}
 * ({@code java.util.GregorianCalendar} → {@code vaadinx.util.GregorianCalendar}).
 * The zone-defaulting workhorse behind {@link Calendar}: constructors that don't
 * take an explicit {@link TimeZone} default to the user's browser zone via
 * {@link EmulatorContext#currentTimeZone()}; every other behavior is inherited
 * {@link java.util.GregorianCalendar} field math, unchanged.
 *
 * <p>See {@link Calendar} for the full rationale, the throw/degrade zone matrix,
 * and the shared-Calendar hazard.
 *
 * @deprecated see {@link Calendar}.
 */
@Deprecated
public class GregorianCalendar extends Calendar {

    public GregorianCalendar() {
        super();
        zoneToBrowser();
    }

    /** Explicit zone — honored, never overridden by the browser default. */
    public GregorianCalendar(TimeZone zone) {
        super(zone);
    }

    public GregorianCalendar(Locale aLocale) {
        super(aLocale);
        zoneToBrowser();
    }

    /** Explicit zone — honored, never overridden by the browser default. */
    public GregorianCalendar(TimeZone zone, Locale aLocale) {
        super(zone, aLocale);
    }

    public GregorianCalendar(int year, int month, int dayOfMonth) {
        this(year, month, dayOfMonth, 0, 0, 0);
    }

    public GregorianCalendar(int year, int month, int dayOfMonth, int hourOfDay, int minute) {
        this(year, month, dayOfMonth, hourOfDay, minute, 0);
    }

    public GregorianCalendar(int year, int month, int dayOfMonth, int hourOfDay, int minute, int second) {
        super();
        // Set the browser zone BEFORE the date fields: the fields are interpreted
        // in the calendar's zone when the time is (lazily) computed, so the zone
        // must already be in place. Mirrors JDK GregorianCalendar(y,m,d,...),
        // which zeroes MILLISECOND.
        zoneToBrowser();
        set(year, month, dayOfMonth, hourOfDay, minute, second);
        set(MILLISECOND, 0);
    }

    private void zoneToBrowser() {
        setTimeZone(EmulatorContext.currentTimeZone());
    }
}
