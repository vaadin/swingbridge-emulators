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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * Assertions the JUnit surface doesn't carry, kept to the shapes this suite
 * actually repeats. Static-import the member, not the type:
 * {@code import static vaadinx.TestAssertions.assertSingle;}
 *
 * <p>Java lane deliberately, like {@link Counter} beside it: shared test
 * infrastructure lives where javac can see it (R_java_karibu_tests), whatever
 * language the callers are in.
 */
public final class TestAssertions {

    private TestAssertions() {
    }

    /**
     * Asserts {@code array} holds exactly one element and returns it —
     * {@code assertSame(l, assertSingle(b.getActionListeners()))} rather than an
     * {@code assertEquals(1, ….length)} paired with a {@code […][0]} index.
     *
     * <p>The pairing is what this exists to stop coming apart. Kotlin's
     * {@code .single()} asserted the cardinality as a side effect of reading the
     * element, so a mechanical port that reaches for {@code [0]} alone drops half
     * the assertion and still passes — the silent-weakening shape the Java port
     * has to watch for. One call keeps both halves together, and the failure
     * message names the array's element type and dumps the surplus, which a bare
     * {@code assertEquals} on the length cannot.
     *
     * @return the sole element, for the caller to assert on further
     */
    public static <T> T assertSingle(T[] array) {
        Assertions.assertNotNull(array, "expected an array holding one element, got null");
        if (array.length != 1) {
            Assertions.fail("expected exactly one " + array.getClass().getComponentType().getSimpleName()
                    + ", got " + array.length + ": " + Arrays.toString(array));
        }
        return array[0];
    }

    /** @see #assertSingle(Object[]) */
    public static <T> T assertSingle(Collection<T> collection) {
        Assertions.assertNotNull(collection, "expected a collection holding one element, got null");
        if (collection.size() != 1) {
            Assertions.fail("expected exactly one element, got " + collection.size() + ": " + collection);
        }
        Iterator<T> it = collection.iterator();
        return it.next();
    }
}
