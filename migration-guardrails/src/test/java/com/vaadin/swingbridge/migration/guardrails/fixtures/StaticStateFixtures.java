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

package com.vaadin.swingbridge.migration.guardrails.fixtures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vaadin.swingbridge.migration.IntentionallyStatic;

import java.awt.Color;
import java.awt.Font;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.KeyStroke;

import vaadinx.text.SimpleDateFormat;
import vaadinx.util.Calendar;

import static com.vaadin.swingbridge.migration.IntentionallyStatic.Reason.COUNTER;
import static com.vaadin.swingbridge.migration.IntentionallyStatic.Reason.IMMUTABLE_CONSTANT;

/**
 * Fixtures for the static-state gate, one per verdict it can reach. Each is imported on its own
 * ({@code importClasses(StaticStateFixtures.LoginFlag.class)}) so a test names the shape it means.
 *
 * <p>None of these is ever instantiated or initialized — ArchUnit reads the class file.
 */
public final class StaticStateFixtures {

    private StaticStateFixtures() {
    }

    /** The {@code public static boolean isLoggedIn} shape — one login logs everybody in. */
    public static final class LoginFlag {
        public static boolean loggedIn;
    }

    /** The log4j-era idiom, once per class: a constant that never got its modifier. */
    public static final class LooseLogger {
        public static Logger log = LoggerFactory.getLogger(LooseLogger.class);
    }

    /** The same field after the one-keystroke fix {@link IntentionallyStatic} prescribes. */
    public static final class FixedLogger {
        public static final Logger LOG = LoggerFactory.getLogger(FixedLogger.class);
    }

    /** A final field of a mutable type: the reference is pinned, the contents are not. */
    public static final class SharedCache {
        public static final Map<String, String> CACHE = new HashMap<>();
    }

    /** What the gate must let through once a field has been through the sweep. */
    public static final class Vetted {
        private static final long serialVersionUID = 1L;
        public static final String NAME = "vetted";
        @IntentionallyStatic(value = COUNTER, note = "IDs are internal; gaps are fine")
        public static final AtomicInteger SEQ = new AtomicInteger();
        @IntentionallyStatic(IMMUTABLE_CONSTANT)
        public static final String[] COLUMNS = { "a", "b" };
    }

    /**
     * One {@code static final} field per category {@link IntentionallyStatic}'s javadoc lists as
     * passing unannotated. The javadoc is the canonical list and this fixture is what pins it: a
     * category dropped from the checker's set reddens the build here.
     */
    @SuppressWarnings("deprecation") // the date emulator is transitional scaffolding by design
    public static final class JavadocCategories {
        public static final int PRIMITIVE = 1;
        public static final Integer BOX = 1;
        public static final String TEXT = "x";
        public static final BigDecimal RATE = BigDecimal.ONE;
        public static final BigInteger COUNT = BigInteger.ONE;
        public static final Class<?> TYPE = String.class;
        public static final Instant STAMP = Instant.EPOCH;
        public static final LocalDate DAY = LocalDate.EPOCH;
        public static final Currency CURRENCY = Currency.EUR;
        public static final Money PRICE = new Money(1);
        public static final Color HIGHLIGHT = Color.RED;
        public static final Font LABEL_FONT = new Font("Dialog", Font.PLAIN, 12);
        public static final KeyStroke SHORTCUT = KeyStroke.getKeyStroke('a');
        public static final Logger SLF4J = LoggerFactory.getLogger(JavadocCategories.class);
        public static final java.util.logging.Logger JUL =
                java.util.logging.Logger.getLogger("fixture");
        public static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd");
    }

    /**
     * The date emulators' two halves, which the gate must keep apart: the formatter passes as a
     * shared static, its calendar sibling does not. The guides tell the migrator to keep the first
     * field exactly as the import swap left it and to unshare the second, so a well-meaning
     * "the date emulators are all browser-zoned, add them together" edit has to redden something.
     */
    @SuppressWarnings("deprecation") // ditto
    public static final class SharedCalendar {
        public static final Calendar SHARED = Calendar.getInstance();
    }

    /** A swept app with nothing left at application scope — the empty-{@code should()} case. */
    public static final class NoStatics {
        private final String name = "instance state only";

        public String getName() {
            return name;
        }
    }

    /** Fails all three gates at once, for the aggregation test. */
    public static final class FailsEverything {
        public static vaadinx.swing.JButton LEAKED;

        public static void boom() {
            System.exit(1);
        }
    }

    public enum Currency { EUR, USD }

    public record Money(long cents) {
    }
}
