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

package vaadinx;

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
 * Note that {@code EHelper.callSwing} runs its callback on a virtual thread and
 * drains synchronously, so a counter bumped from inside one is still only ever
 * touched by one thread at a time.
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
