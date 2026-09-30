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

import org.junit.jupiter.api.Assertions;

/**
 * A mutable tally a lambda can bump — Java's stand-in for the captured
 * {@code var n = 0} that Kotlin tests used, without the {@code int[] n = {0}}
 * array-cell trick that reads as a puzzle at every call site.
 *
 * <p>Counting, not holding: the overwhelmingly common shape in this suite is
 * "a listener fired, count how often", so the API is {@link #inc()} /
 * {@link #get()} rather than get-and-set. A test that needs to capture a
 * <em>value</em> from a lambda wants a different type; add one when the first
 * such call site exists (R_infra_not_surface).
 *
 * <p>Deliberately not thread-safe and deliberately not an
 * {@code AtomicInteger}: nothing here is concurrent, and reaching for an atomic
 * would claim a memory-model guarantee these tests neither need nor honour.
 *
 * <p>A twin of {@code vaadinx.Counter} in {@code :emulators}' test lane, same
 * simple name on purpose — the two test lanes are read side by side, and
 * {@code :surrogates} cannot depend on {@code :emulators} to share one.
 */
public final class Counter {

    private int n;

    /** Bumps the tally. The whole point of the class — usable from a lambda. */
    public void inc() {
        n++;
    }

    public int get() {
        return n;
    }

    /**
     * Asserts the tally — {@code fires.assertEquals(2)} rather than
     * {@code assertEquals(2, fires.get())}.
     *
     * <p>The one assertion this class carries, because counting to a number is
     * what essentially every call site does. Anything else — a range, a
     * relation, a message — goes through JUnit against {@link #get()}; this is a
     * convenience on the dominant case, not the start of an assertion API.
     */
    public void assertEquals(int expected) {
        Assertions.assertEquals(expected, n);
    }

    /** Renders as the bare number, so an assertion failure reads sensibly. */
    @Override
    public String toString() {
        return Integer.toString(n);
    }
}
