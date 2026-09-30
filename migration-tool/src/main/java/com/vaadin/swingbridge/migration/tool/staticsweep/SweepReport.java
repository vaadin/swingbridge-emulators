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

package com.vaadin.swingbridge.migration.tool.staticsweep;

import com.vaadin.swingbridge.migration.IntentionallyStatic;
import com.vaadin.swingbridge.migration.tool.guardrails.ImmutableTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The bucketing rule and the Markdown, which are one class because the buckets exist to be read.
 *
 * <pre>{@code
 * List<Row> rows = SweepReport.rows(index, resolver);
 * String md = new SweepReport(new Swept(label, tree, index, rows), null, resolver, notes).render();
 * }</pre>
 *
 * <p>The report <b>is</b> the sweep's worklist: every mechanical column of the guide's review table
 * pre-filled — kind, declared type, component-or-not, writers, readers, initializer line, the
 * counted constants — and no verdicts, which were never the mechanisable part.
 *
 * <p><b>Every section prints even when empty.</b> A section that vanished on finding nothing would
 * leave the reader unable to tell <i>checked, clear</i> from <i>never looked</i>.
 */
final class SweepReport {

    /**
     * Where a {@code static} field lands. Only {@link #WORKLIST} reaches the decision tree; the
     * other three are counted and shown, never silently dropped, because a count that reconciles
     * against the reader's own {@code javap} is what makes the funnel believable.
     */
    enum Bucket {
        /** Compiler-generated: {@code $VALUES}, {@code $SwitchMap$…}, {@code $assertionsDisabled}. */
        SYNTHETIC,
        /** An enum constant — {@code static final} of an immutable type by construction. */
        ENUM_CONSTANT,
        /** {@code final} <i>and</i> of an immutable type: the gate's own definition of settled. */
        CONSTANT,
        /** Everything else, which is what the sweep is for. */
        WORKLIST
    }

    /**
     * @param writers writes from outside the owner's {@code <clinit>}, each {@code method:line} —
     *     the column that answers {@code WORLD_GLOBAL_READ_MOSTLY} as a query rather than an
     *     impression
     * @param readers the distinct methods that read it, for scope
     */
    record Row(StaticIndex.Field field, Bucket bucket, Resolver.Verdict component,
            List<String> typeArgComponents, List<String> writers, List<String> readers) {
    }

    /** One swept tree: its class files, its facts, and its fields bucketed. */
    record Swept(String label, ClassTree tree, StaticIndex index, List<Row> rows) {

        List<Row> worklist() {
            return rows.stream().filter(r -> r.bucket() == Bucket.WORKLIST).toList();
        }

        Optional<Row> row(String key) {
            return rows.stream().filter(r -> r.field().key().equals(key)).findFirst();
        }
    }

    /** The fate of a stage-1 worklist row at stage 2, which is the sweep's own completeness check. */
    private enum Fate {
        GONE("gone — no such field any more (routed, or its class was removed)"),
        CONSTANT("made a constant — `final` and of an immutable type; the gate passes it"),
        ANNOTATED("a verdict was recorded — `@IntentionallyStatic`"),
        UNVETTED("**still unvetted** — reaches the tree and nothing has been decided");

        private final String description;

        Fate(String description) {
            this.description = description;
        }
    }

    private final Swept sweep;
    private final Swept diff;
    private final Resolver resolver;
    private final List<String> notes;

    /**
     * @param diff the stage-2 tree to compare against, or {@code null}
     * @param notes lines about the run itself — a stale-classes warning, an empty tree — printed
     *     before anything else, because a report over the wrong class files is worse than none
     */
    SweepReport(Swept sweep, Swept diff, Resolver resolver, List<String> notes) {
        this.sweep = sweep;
        this.diff = diff;
        this.resolver = resolver;
        this.notes = notes;
    }

    /**
     * Buckets every {@code static} field the index found.
     *
     * <p>This is the gate's rule, not a second opinion on it: {@code final} governs whether the
     * reference can change and {@link ImmutableTypes} whether the referent can, so a row called
     * {@link Bucket#CONSTANT} here is a row {@code MigrationGuardrails} passes.
     *
     * <p>An {@code @IntentionallyStatic} field stays on the worklist with its reason shown
     * rather than being filtered out. The gate is green on it; the sweep still wants it counted
     * and re-readable.
     */
    static List<Row> rows(StaticIndex index, Resolver resolver) {
        List<Row> rows = new ArrayList<>();
        for (StaticIndex.Field field : index.fields()) {
            Bucket bucket;
            if (field.synthetic()) {
                bucket = Bucket.SYNTHETIC;
            } else if (field.enumConstant()) {
                bucket = Bucket.ENUM_CONSTANT;
            } else if (field.isFinal() && resolver.isImmutable(field)) {
                bucket = Bucket.CONSTANT;
            } else {
                bucket = Bucket.WORKLIST;
            }
            List<StaticIndex.Access> accesses = index.accessesTo(field.key());
            rows.add(new Row(field, bucket,
                    resolver.verdict(field.typeName(), field.primitive()),
                    resolver.componentTypeArguments(field.signature()),
                    writers(field, accesses), readers(field, accesses)));
        }
        return rows;
    }

    private static List<String> writers(StaticIndex.Field field, List<StaticIndex.Access> accesses) {
        return accesses.stream()
                .filter(StaticIndex.Access::write)
                .filter(a -> !(a.inClass().equals(field.owner()) && a.method().equals("<clinit>")))
                .map(a -> qualify(field, a) + (a.line() == null ? "" : ":" + a.line()))
                .distinct().toList();
    }

    private static List<String> readers(StaticIndex.Field field, List<StaticIndex.Access> accesses) {
        return accesses.stream().filter(a -> !a.write()).map(a -> qualify(field, a)).distinct()
                .toList();
    }

    private static String qualify(StaticIndex.Field field, StaticIndex.Access access) {
        return access.inClass().equals(field.owner()) ? access.method()
                : Names.simple(access.inClass()) + "." + access.method();
    }

    String render() {
        StringBuilder out = new StringBuilder();
        out.append("# Static-field sweep — ").append(sweep.label()).append("\n\n");
        notes.forEach(note -> out.append("> ").append(note).append("\n\n"));
        renderInputs(out);
        renderFunnel(out);
        renderWorklist(out);
        renderConstants(out);
        renderStaticMethods(out);
        renderClinits(out);
        renderNestedTypes(out);
        renderDiff(out);
        renderRule(out);
        out.append("\nDone. Worklist rows: ").append(sweep.worklist().size())
                .append(". Every row is a decision to make, not a failure.\n");
        return out.toString();
    }

    private void renderInputs(StringBuilder out) {
        out.append("- class files read: ").append(sweep.tree().classFileCount()).append('\n');
        out.append("- classpath entries for type resolution: ")
                .append(resolver.classpath().size()).append('\n');
        if (diff != null) {
            out.append("- compared against: ").append(diff.label()).append(" (")
                    .append(diff.tree().classFileCount()).append(" class files)\n");
        }
        out.append("""

                **A progress meter, not a gate.** Every column here is mechanical — read off the field
                tables, the `Signature` attributes and the `getstatic` / `putstatic` operands of the
                classes the compiler already resolved. The verdicts are yours: this report says what is
                there, `MigrationGuardrails` is what later refuses to build without them.
                """);
    }

    private void renderFunnel(StringBuilder out) {
        Map<Bucket, Long> counts = sweep.rows().stream()
                .collect(Collectors.groupingBy(Row::bucket, LinkedHashMap::new, Collectors.counting()));
        long componentTyped = sweep.worklist().stream().filter(r -> r.component().component()).count();
        long allowlisted = sweep.worklist().stream().filter(r -> r.field().allowlisted()).count();
        long typeArgs = sweep.rows().stream().filter(r -> !r.typeArgComponents().isEmpty()).count();
        long touching = sweep.index().staticMethods().stream()
                .filter(m -> !m.touches().isEmpty()).count();
        long blocks = sweep.index().clinitNotes().stream()
                .filter(StaticIndex.ClinitNote::explicitBlock).count();

        out.append("\n## The funnel\n\n| | count |\n|---|---:|\n");
        out.append("| `static` fields, total | ").append(sweep.rows().size()).append(" |\n");
        out.append("| … synthetic (`$VALUES`, `$SwitchMap$…`, `$assertionsDisabled`) | ")
                .append(counts.getOrDefault(Bucket.SYNTHETIC, 0L)).append(" |\n");
        out.append("| … enum constants | ").append(counts.getOrDefault(Bucket.ENUM_CONSTANT, 0L))
                .append(" |\n");
        out.append("| … constants (`final` **and** of an immutable type) | ")
                .append(counts.getOrDefault(Bucket.CONSTANT, 0L)).append(" |\n");
        out.append("| … **worklist** (reach the decision tree) | ")
                .append(counts.getOrDefault(Bucket.WORKLIST, 0L)).append(" |\n");
        out.append("| …… of which the declared type is a component | ").append(componentTyped)
                .append(" |\n");
        out.append("| …… of which already carry `@IntentionallyStatic` | ").append(allowlisted)
                .append(" |\n");
        out.append("| fields with a component in a *type argument* | ").append(typeArgs)
                .append(" |\n");
        out.append("| `static` methods (non-synthetic) | ")
                .append(sweep.index().staticMethods().size()).append(" |\n");
        out.append("| … whose body touches one of the app's own statics | ").append(touching)
                .append(" |\n");
        out.append("| `static` nested types | ").append(sweep.index().nestedStaticTypes().size())
                .append(" |\n");
        out.append("| classes with a `<clinit>` | ").append(sweep.index().clinitNotes().size())
                .append(" |\n");
        out.append("| … whose `<clinit>` looks like an explicit `static { }` block | ").append(blocks)
                .append(" |\n");
    }

    private void renderWorklist(StringBuilder out) {
        List<Row> worklist = sweep.worklist();
        out.append("\n## Worklist — ").append(worklist.size())
                .append(" row(s)\n\nOne row per `static` field that reaches the decision tree.\n\n");
        if (worklist.isEmpty()) {
            out.append("(none — every `static` field is a constant, an enum constant or"
                    + " compiler-generated)\n");
            return;
        }
        out.append("| # | class | field | type | `final` | component | line |"
                + " writers outside `<clinit>` | readers | allowlisted |\n");
        out.append("|---:|---|---|---|---|---|---:|---|---|---|\n");
        int i = 0;
        for (Row row : worklist) {
            StaticIndex.Field field = row.field();
            out.append("| ").append(++i)
                    .append(" | ").append(Names.simple(field.owner()))
                    .append(" | `").append(field.name()).append('`')
                    .append(" | ").append(type(row))
                    .append(" | ").append(field.isFinal() ? "final" : "")
                    .append(" | ").append(component(row))
                    .append(" | ").append(field.initLine() == null ? "" : field.initLine())
                    .append(" | ").append(cap(row.writers(), 6))
                    .append(" | ").append(cap(row.readers(), 6))
                    .append(" | ").append(allowlist(field))
                    .append(" |\n");
        }
        out.append("""

                Reading the columns: **`final`** and **type** together are the immutability half of
                the gate's rule — a `final` field of a mutable type still reaches the tree. **line**
                is the declaration, recovered from the initializer's own `putstatic`; a field with no
                initializer has none, and its writers' lines are where to start instead. **writers
                outside `<clinit>`** is `WORLD_GLOBAL_READ_MOSTLY` asked as a query: an empty cell
                means nothing writes it after initialization, and a non-empty one refutes the claim
                before anyone reads the code. A write from a lambda is attributed to the synthetic
                `lambda$method$0`, which names the enclosing method.
                """);
        List<Row> components = worklist.stream().filter(r -> r.component().component()).toList();
        List<Row> typeArgs = sweep.rows().stream().filter(r -> !r.typeArgComponents().isEmpty())
                .toList();
        if (!components.isEmpty() || !typeArgs.isEmpty()) {
            out.append("\n### Component-typed — the tree's one prohibition\n\n");
            for (Row row : components) {
                out.append("- `").append(row.field().owner()).append('.')
                        .append(row.field().name()).append("` : ")
                        .append(Names.simple(row.field().typeName())).append(" — ")
                        .append(row.component().detail()).append('\n');
            }
            for (Row row : typeArgs) {
                out.append("- `").append(row.field().owner()).append('.')
                        .append(row.field().name()).append("` holds a component in a type argument: ")
                        .append(String.join(", ", row.typeArgComponents())).append('\n');
            }
            out.append("\nA component belongs to a UI, not to the JVM. These are resolved at use"
                    + " time, never held; the guardrail gate refuses the build otherwise.\n");
        }
    }

    private void renderConstants(StringBuilder out) {
        List<Row> constants = sweep.rows().stream().filter(r -> r.bucket() == Bucket.CONSTANT)
                .toList();
        out.append("\n## Constants — ").append(constants.size())
                .append(" row(s), counted rather than dropped\n\n");
        out.append("`final` and of an immutable type, so the gate passes them and the tree does not"
                + " need them. Listed because the count is what reconciles this report against your"
                + " own `javap`.\n\n");
        if (constants.isEmpty()) {
            out.append("(none)\n");
            return;
        }
        for (Row row : constants) {
            out.append("- `").append(Names.simple(row.field().owner())).append('.')
                    .append(row.field().name()).append("` : ")
                    .append(Names.simple(row.field().typeName()));
            if (row.field().constantValue() != null) {
                out.append(" = ").append(shorten(row.field().constantValue()));
            }
            if (!row.writers().isEmpty()) {
                // A final field cannot be reassigned, so a write outside <clinit> means an
                // interface constant or a nest-mate trick — worth an eyebrow, not a verdict.
                out.append("  **written by ").append(String.join(", ", row.writers())).append("**");
            }
            out.append('\n');
        }
    }

    private void renderStaticMethods(StringBuilder out) {
        List<StaticIndex.StaticMethod> touching = sweep.index().staticMethods().stream()
                .filter(m -> !m.touches().isEmpty()).toList();
        out.append("\n## `static` methods that touch the app's own statics — ").append(touching.size())
                .append(" of ").append(sweep.index().staticMethods().size()).append("\n\n");
        out.append("`Q_method_or_type`: a `static` *method* is only a problem when its body reaches"
                + " `static` *state*. The operand of each `getstatic` / `putstatic` names the field"
                + " and says read or write, so this is the answer rather than a references list to"
                + " read. Every other `static` method is a pure function of its arguments and needs"
                + " no decision.\n\n");
        if (touching.isEmpty()) {
            out.append("(none)\n");
            return;
        }
        for (StaticIndex.StaticMethod method : touching) {
            out.append("- `").append(Names.simple(method.owner())).append('.').append(method.name())
                    .append("`").append(method.line() == null ? "" : " (line " + method.line() + ")")
                    .append(" — ").append(String.join(", ", method.touches())).append('\n');
        }
    }

    private void renderClinits(StringBuilder out) {
        List<StaticIndex.ClinitNote> interesting = sweep.index().clinitNotes().stream()
                .filter(n -> n.explicitBlock() || !n.invokes().isEmpty()).toList();
        out.append("\n## Static initializers — ").append(interesting.size()).append(" worth a look\n\n");
        out.append("What runs at class load, which is the `G_construction` timing signal: a"
                + " `SessionFactory` or a `ZoneId.systemDefault()` here happens before any user"
                + " exists. A field initializer and an explicit `static { }` block compile to the"
                + " same `<clinit>`, so the block flag is a heuristic (exception handlers in a"
                + " `<clinit>` whose class owns a real `static`) — it finds a block with a `try` and"
                + " misses one without. The `putstatic` targets are exact either way, and those are"
                + " the fields a block configures.\n\n");
        if (interesting.isEmpty()) {
            out.append("(none)\n");
            return;
        }
        for (StaticIndex.ClinitNote note : interesting) {
            out.append("- `").append(Names.simple(note.owner())).append('`')
                    .append(note.explicitBlock() ? " — **has an explicit `static { }` block**" : "");
            if (!note.invokes().isEmpty()) {
                out.append(note.explicitBlock() ? "; invokes " : " — invokes ")
                        .append(String.join(", ", cappedList(note.invokes(), 8)));
            }
            out.append('\n');
        }
    }

    private void renderNestedTypes(StringBuilder out) {
        List<String> nested = sweep.index().nestedStaticTypes();
        out.append("\n## `static` nested types — ").append(nested.size()).append("\n\n");
        out.append("`Q_method_or_type` again, one level up: a `static` nested *type* is not shared"
                + " state, and the tree waves it through. Listed so a `static class` in the source"
                + " is not mistaken for a `static` field.\n\n");
        if (nested.isEmpty()) {
            out.append("(none)\n");
            return;
        }
        nested.forEach(name -> out.append("- `").append(name).append("`\n"));
    }

    private void renderDiff(StringBuilder out) {
        if (diff == null) {
            return;
        }
        out.append("\n## Completeness diff against ").append(diff.label()).append("\n\n");
        out.append("The closing check of the sweep, as a diff rather than as two counts: every row"
                + " of the worklist above, and what became of it.\n\n");
        out.append("| stage-1 row | fate |\n|---|---|\n");
        Map<Fate, Integer> tally = new LinkedHashMap<>();
        for (Row row : sweep.worklist()) {
            Optional<Row> after = diff.row(row.field().key());
            Fate fate;
            String detail = "";
            if (after.isEmpty()) {
                fate = Fate.GONE;
            } else if (after.get().bucket() != Bucket.WORKLIST) {
                fate = Fate.CONSTANT;
            } else if (after.get().field().allowlisted()) {
                fate = Fate.ANNOTATED;
                detail = " (`" + after.get().field().allowlistReason() + "`)";
            } else {
                fate = Fate.UNVETTED;
            }
            tally.merge(fate, 1, Integer::sum);
            out.append("| `").append(Names.simple(row.field().owner())).append('.')
                    .append(row.field().name()).append("` | ").append(fate.description)
                    .append(detail).append(" |\n");
        }
        if (sweep.worklist().isEmpty()) {
            out.append("| (the worklist was empty) | nothing to trace |\n");
        }

        List<Row> introduced = diff.worklist().stream()
                .filter(r -> sweep.row(r.field().key()).isEmpty()).toList();
        out.append("\n**New statics the migration introduced: ").append(introduced.size())
                .append(".** ");
        out.append("A migrated app grows some legitimately — a servlet's error id, a route"
                + " constant — and each still owes the same verdict.\n\n");
        for (Row row : introduced) {
            out.append("- `").append(Names.simple(row.field().owner())).append('.')
                    .append(row.field().name()).append("` : ")
                    .append(Names.simple(row.field().typeName()))
                    .append(row.field().allowlisted()
                            ? " — `" + row.field().allowlistReason() + "`"
                            : " — **unvetted**")
                    .append('\n');
        }
        out.append("\n**A field matched by name.** A row whose class was renamed or moved shows as"
                + " *gone* here and again under the new statics — the two lines are the same field,"
                + " and nothing in the class files ties them together.\n");
    }

    private void renderRule(StringBuilder out) {
        out.append("\n## The rule this report applies\n\n");
        out.append("A `static` field is settled when it is **`final` and of an immutable type**, or"
                + " when it carries **`@IntentionallyStatic`** with one of four reasons. Both halves"
                + " matter: `final` governs whether the reference can change, the type whether the"
                + " thing it points at can. Printed here so the rule needs no second window —"
                + " `@IntentionallyStatic`'s own javadoc is its canonical definition.\n\n");
        for (IntentionallyStatic.Reason reason : IntentionallyStatic.Reason.values()) {
            out.append("- **`").append(reason.name()).append("`** — ").append(REASONS.get(reason))
                    .append('\n');
        }
        out.append("\nA field fitting none of them gets routed to session or tab scope rather than"
                + " argued over, and `note()` is worth writing wherever the claim is not evident"
                + " from the declaration — always for `COUNTER`.\n\n");
        out.append("Immutable types, named (structural: primitives, enums, records, `java.time.*`):"
                + "\n\n");
        out.append(ImmutableTypes.namedTypes().stream().sorted().map(t -> "`" + t + "`")
                .collect(Collectors.joining(", "))).append('\n');
    }

    /**
     * One line each, so the report stands alone in a terminal. {@link IntentionallyStatic.Reason}'s
     * javadoc is the definition; these are reminders of it.
     */
    private static final Map<IntentionallyStatic.Reason, String> REASONS = Map.of(
            IntentionallyStatic.Reason.IMMUTABLE_CONSTANT,
            "a constant of a mutable type that is never mutated (`List.of(…)`, a write-once array)."
                    + " Evidence: no write outside initialization, and no mutating call.",
            IntentionallyStatic.Reason.WORLD_GLOBAL_READ_MOSTLY,
            "state global to *all users* and read-mostly — configuration, reference data. Does it"
                    + " come from, or feed, a resource outside the process? A cache written on save"
                    + " is not this; the writers column refutes it.",
            IntentionallyStatic.Reason.JVM_INFRASTRUCTURE,
            "a process-level service rather than data: a connection pool, a `SessionFactory`, a"
                    + " logger, a thread pool. The *factory* belongs here; a *current session* does"
                    + " not.",
            IntentionallyStatic.Reason.COUNTER,
            "a monotonic counter or sequence where sharing across users is the feature, so gaps and"
                    + " interleaving are fine. Move it to session scope when a user must see a"
                    + " contiguous private sequence.");

    private static String type(Row row) {
        StaticIndex.Field field = row.field();
        String rendered = "`" + Names.simple(field.typeName()) + "`";
        if (!row.typeArgComponents().isEmpty()) {
            rendered += "<" + String.join(", ", row.typeArgComponents()) + ">";
        }
        return rendered;
    }

    private static String component(Row row) {
        String rendered = row.component().render();
        return row.typeArgComponents().isEmpty() ? rendered
                : rendered + ", **type arg: " + String.join(", ", row.typeArgComponents()) + "**";
    }

    private static String allowlist(StaticIndex.Field field) {
        if (!field.allowlisted()) {
            return "";
        }
        return "`" + field.allowlistReason() + "`"
                + (field.allowlistNote().isEmpty() ? "" : " — " + field.allowlistNote());
    }

    private static String cap(List<String> values, int limit) {
        return values.isEmpty() ? "none" : String.join(", ", cappedList(values, limit));
    }

    private static List<String> cappedList(List<String> values, int limit) {
        if (values.size() <= limit) {
            return values;
        }
        List<String> capped = new ArrayList<>(values.subList(0, limit));
        capped.add("+" + (values.size() - limit) + " more");
        return capped;
    }

    private static String shorten(String value) {
        return value.length() > 40 ? value.substring(0, 40) + "…" : value;
    }
}
