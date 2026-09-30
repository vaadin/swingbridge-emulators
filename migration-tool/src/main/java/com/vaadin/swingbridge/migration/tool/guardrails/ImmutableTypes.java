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

package com.vaadin.swingbridge.migration.tool.guardrails;

import com.vaadin.swingbridge.migration.IntentionallyStatic;

import java.util.Set;

/**
 * The static sweep's immutability rule, in one place.
 *
 * <p>This is the <b>single owner</b> of the type set that {@link IntentionallyStatic}'s javadoc
 * defines in prose. Everything asking "is this {@code static} field provably immutable?" resolves
 * here — {@link GuardrailEngine}'s build-time gate today, {@code StaticSweep}'s worklist report when
 * it lands — because two readers of one rule is a rule that drifts
 * (M1D_static_sweep_tool § {@code Q_one_rule_engine}).
 *
 * <p>The predicate is split from the reading of it on purpose: {@link #isImmutable} takes plain
 * facts about a field's type, not a parsed representation of one, so a caller that reads class files
 * some other way still gets the same verdict.
 */
public final class ImmutableTypes {

    /**
     * Types whose instances cannot change once constructed, named rather than recognised
     * structurally (for which see {@link #isImmutable}).
     *
     * <p>Two entries are not provably immutable and are here on the annotation's narrower claim.
     * The logging facades: nobody reassigns a logger, and no logging call mutates state a migration
     * cares about — drop them and the annotation's own prescription for the
     * {@code static Logger logger = …} idiom, add {@code final}, lands on a {@code final} field of
     * a flagged type. The date-format emulator: it keeps no formatting state on the instance (each
     * {@code format}/{@code parse} runs on a per-session backing cloned from a template that
     * freezes on first use), so the shared-static idiom the import swap is meant to fix passes the
     * gate as the guides promise it will. Its two calendar siblings are deliberately absent —
     * {@code vaadinx.util.Calendar} zones construction and is otherwise a plain thread-confined
     * calendar, so a shared mutable one is a hazard the gate should keep flagging.
     *
     * <p>The {@code vaadinx} name is matched as a string, as the component gate's two are: nothing
     * here depends on {@code :emulators}, and in a module that has none the name resolves to
     * nothing and no field can be of that type.
     */
    @IntentionallyStatic(IntentionallyStatic.Reason.IMMUTABLE_CONSTANT)
    private static final Set<String> NAMED = Set.of(
            "java.lang.String",
            "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
            "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
            "java.math.BigDecimal", "java.math.BigInteger",
            "java.lang.Class",
            "java.awt.Color", "java.awt.Font", "javax.swing.KeyStroke",
            "vaadinx.text.SimpleDateFormat",
            "org.slf4j.Logger", "org.apache.log4j.Logger", "java.util.logging.Logger");

    private ImmutableTypes() {
    }

    /**
     * Whether a field of this type has an immutable <i>referent</i>. Says nothing about {@code final}
     * — the gate needs both, since {@code final} governs whether the reference can change and this
     * governs whether the thing it points at can.
     *
     * <p>A record's components are not checked: a record of mutable parts passes. Accepted — the
     * alternative is a transitive walk the gate deliberately drops, and a holder that reaches a
     * component is already flagged for being of a mutable type.
     *
     * @param typeName fully-qualified, e.g. {@code "java.awt.Color"}
     */
    public static boolean isImmutable(String typeName, boolean primitive, boolean isEnum, boolean isRecord) {
        return primitive || isEnum || isRecord
                || typeName.startsWith("java.time.")
                || NAMED.contains(typeName);
    }

    /** The named half of the rule, for a caller that wants to print or diff it. */
    public static Set<String> namedTypes() {
        return NAMED;
    }
}
