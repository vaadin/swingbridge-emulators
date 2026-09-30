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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Sets up Karibu-Testing around each test. Subclasses get a live Vaadin UI
 * context with a deterministic {@link BrowserTimeZone} (UTC) pre-installed —
 * production code calls {@link BrowserTimeZone#fetch} from a UI init listener,
 * but MockVaadin has no real browser to round-trip with, and {@code SHelper}'s
 * Date↔LocalDate conversions throw if the cache is empty (SD_browser_timezone). UTC is
 * picked over a DST-bearing zone so tests don't break twice a year.
 *
 * <p>Also owns the stub-WARN capture harness shared by every surrogate test:
 * {@link SHelper#warnHook} is a global mutable static, so installing it in the
 * base's {@link #setupKaribu} and resetting it in {@link #teardownKaribu} guarantees the
 * reset happens even for a subclass that forgets — a leaked hook would
 * otherwise bleed WARNs into the next test. Assert emptiness via
 * {@link #assertNoWarns}, which dumps the offending WARNs on failure.
 *
 * <p>Shared test infrastructure, in Java like every test in the repo (R_java_karibu_tests).
 */
public abstract class AbstractKaribuTest {

    /** WARNs captured via {@link SHelper#warnHook} for the duration of one test. */
    protected final List<String> capturedWarns = new ArrayList<>();

    @BeforeEach
    public void setupKaribu() {
        MockVaadin.setup();
        BrowserTimeZone.setZoneId(ZoneOffset.UTC);
        capturedWarns.clear();
        SHelper.warnHook = capturedWarns::add;
    }

    @AfterEach
    public void teardownKaribu() {
        SHelper.warnHook = msg -> { /* default no-op */ };
        MockVaadin.tearDown();
    }

    /** @see #assertNoWarns(String) */
    protected void assertNoWarns() {
        assertNoWarns(null);
    }

    /**
     * Asserts no stub WARNs fired. Uses {@code assertEquals} against the empty
     * list (not {@code assertTrue(capturedWarns.isEmpty())}) so a failure dumps
     * the offending WARN strings instead of a bare "expected true, was
     * false" — the same readable-failure principle as Karibu's {@code _get} dumps.
     */
    protected void assertNoWarns(String message) {
        Assertions.assertEquals(List.of(), capturedWarns, message);
    }
}
