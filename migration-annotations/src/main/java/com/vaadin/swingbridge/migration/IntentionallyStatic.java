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

package com.vaadin.swingbridge.migration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code static} field that went through the migration's static sweep and is
 * <b>correct as a static</b> — application scope, shared by every user, deliberately:
 *
 * <pre>{@code
 * @IntentionallyStatic(JVM_INFRASTRUCTURE)
 * private static final SessionFactory sessionFactory = buildFactory();
 *
 * @IntentionallyStatic(value = COUNTER, note = "IDs are internal; gaps are fine")
 * private static int idSequence = 1000;
 * }</pre>
 *
 * <p>The sweep's build-time gate flags every {@code static} field that is not provably
 * immutable, and this annotation is its allowlist. A flagged field with neither an
 * annotation nor a new home is an <b>unfinished sweep</b> — which is the whole point, since
 * a mis-scoped {@code static} compiles, boots, logs no warning, and surfaces in production
 * as another user's data.
 *
 * <p>Add it only <b>after</b> running the decision tree and reaching application scope.
 * Annotating is cheaper than routing, which is exactly why {@link #value()} is mandatory and
 * closed: if none of the four reasons fits, the field does not belong here — route it.
 *
 * <p>Changes nothing at runtime. It is a claim, addressed to a reviewer and to a lint — the lint
 * being {@code com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails} (artifact
 * {@code com.vaadin.swingbridge:swingbridge-migration-guardrails}, test scope), which implements the rule this javadoc
 * defines and carries a fixture per category below.
 *
 * <p><b>What passes with no annotation</b> — both conditions, since {@code final}
 * governs whether the <i>reference</i> can change and the type governs whether the
 * <i>referent</i> can: the field is {@code final}, <i>and</i> its type is effectively
 * immutable (primitive or box, {@code String}, {@code BigDecimal}, {@code BigInteger},
 * {@code Class}, an {@code enum}, a {@code java.time} type, an immutable {@code record},
 * {@code Color}, {@code Font},
 * {@code KeyStroke}, {@code vaadinx.text.SimpleDateFormat}, a logging facade — {@code org.slf4j.Logger},
 * {@code org.apache.log4j.Logger}, {@code java.util.logging.Logger}). Roughly three in
 * five statics in a real app pass here untouched; over-migration is a large diff that
 * fixes nothing.
 * <p><b>The logging facades and the date-format emulator are the entries that are not
 * provably immutable</b>, and the claim made for them is narrower.
 * <p>{@code vaadinx.text.SimpleDateFormat} keeps no formatting state on the instance: every
 * {@code format}/{@code parse} runs on a per-session backing cloned from a configuration
 * template that freezes on first use, and reconfiguring a frozen one off a background thread
 * throws rather than rewriting a shared formatter for every user. A shared instance is
 * therefore safe in the only direction this rule cares about, which is why the import swap
 * is the whole fix for the classic {@code static final SimpleDateFormat} idiom and why the
 * migration guides tell the migrator in three places to keep that field exactly as written.
 * Leave the type off and each of those three sentences becomes false at this gate. Note the
 * narrowness: {@code vaadinx.util.Calendar} / {@code vaadinx.util.GregorianCalendar} do
 * <i>not</i> join it — those zone their construction and are otherwise plain
 * thread-confined calendars, so a shared mutable one stays flagged, and the answer for it is
 * to unshare rather than to annotate.
 * <p>The logging facades' claim: nobody reassigns a logger, and no logging call mutates
 * state a migration cares about. They earn the place by measurement — the log4j-era
 * {@code static Logger logger = …} idiom omits {@code final} and appears once per class, so
 * loggers ran between a quarter and three-fifths of the non-{@code final} statics counted
 * in the app we measured. Leave them off and the fix prescribed just below lands on a
 * {@code final} field of a flagged type, so the rule contradicts itself on the commonest
 * shape it sees.
 * <p>Everything else is flagged. <b>Any non-{@code final} static, whatever its type</b> —
 * {@code public static boolean isLoggedIn} is a primitive and is also the sharpest
 * cross-user bug the sweep hunts (one login logs everybody in). Where the field is really
 * a constant that lost its modifier — the {@code static Logger logger = …} idiom — add
 * {@code final} rather than annotating, which lands it on the passing side above. And
 * <b>a {@code final} field of a mutable
 * type</b>: arrays, collections, containers by <i>type argument</i>
 * ({@code static final Map<String, JPanel>}), models, {@code ButtonGroup},
 * {@code Action}, renderers, or an app-authored object holding any of those.
 * <p>Transitive reachability is deliberately not part of the rule: a holder that reaches
 * a component is already flagged for being a mutable type, so the walk buys nothing here.
 *
 * <p>This list is <b>not</b> the sweep's module-classification list, though it shares
 * type names — a trap worth knowing, because a reader arriving from Step 0 will assume
 * they match. There, {@code Dimension} / {@code Insets} / {@code Point} /
 * {@code Rectangle} count as ordinary value objects, because the question is whether a
 * <i>module</i> is UI-bearing and mutability is beside the point. Here mutability is the
 * entire question, and all four expose public mutable fields — so
 * {@code static final Insets PAD} is flagged, and any caller can repad every user's UI.
 *
 * @see Reason
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface IntentionallyStatic {

    /** Why the field is correct at application scope; one of four, because a free-text
     *  justification would make the allowlist unreviewable. */
    Reason value();

    /**
     * @return evidence for the reason, or {@code ""}. Worth writing wherever the claim is not
     *     self-evident from the declaration — always for {@link Reason#COUNTER}, whose verdict
     *     rests on a judgement about the app rather than a property of the field.
     */
    String note() default "";

    /**
     * The four sanctioned reasons to leave a field at application scope, one per terminal
     * verdict of the sweep's decision tree. Closed on purpose: a field fitting none of them
     * gets routed instead of argued over.
     */
    enum Reason {

        /**
         * A constant of a mutable type that is never mutated — {@code List.of(…)}, an array
         * written once and only read.
         *
         * <p>Evidence: no write reference outside initialization, and no mutating call on the
         * object. If either exists, this is the wrong reason.
         */
        IMMUTABLE_CONSTANT,

        /**
         * State global to <b>all users</b> rather than to the app, and <b>read-mostly</b>:
         * immutable configuration, read-only reference data. The tree's test — does the state
         * come <i>from</i>, or <i>feed</i>, a resource outside the process (the database, the
         * classpath, deployment config)?
         *
         * <p>Read-mostly is a query, not an impression: no write reference outside
         * initialization, counting the field's own initializer, a {@code static { }} block and
         * a guarded lazy-init ({@code if (x == null) x = …}) as initialization and everything
         * else as mutation.
         *
         * <p><b>A cache the app writes on save is not this</b> — the write query refutes the
         * claim. Such a cache is still correct shared and stale per-tab, so it stays at
         * application scope; it just belongs under {@link #JVM_INFRASTRUCTURE}, or with the
         * sharing fixed at its source.
         */
        WORLD_GLOBAL_READ_MOSTLY,

        /**
         * A process-level service rather than data: a connection pool, a Hibernate
         * {@code SessionFactory}, a logger, a thread pool. Correct as a static for the same
         * reason it was on the desktop, and the migration leaves it alone.
         *
         * <p>Note the boundary — the <i>factory</i> belongs here; a <i>current session</i> or
         * <i>current transaction</i> does not, being per-user or per-call.
         */
        JVM_INFRASTRUCTURE,

        /**
         * A monotonic counter, sequence or accumulator, where sharing across users is the
         * <i>feature</i>: it behaves like a database sequence, so gaps and interleaving are
         * fine. The opposite default from user-specific state.
         *
         * <p>The exception, and why {@link #note()} is worth writing here: move it to session
         * scope when a user must see a <i>contiguous private</i> sequence — the number is shown
         * to them as "your record #1, #2, #3" and must not skip.
         */
        COUNTER
    }
}
