# Static-field categorization — the `static` sweep

Reference for [Phase 1](./guide.md#S_triage_reports)'s *"sweep all `static`
declarations"*. A desktop Swing app is a single JVM serving one user, so `static` state is
free — it's the app's private global scratchpad. A server serves **every** user from one JVM,
so each `static` field is now shared across all of them until you prove otherwise. This doc turns
that "prove otherwise" into a lookup: [classify your modules](#step-0--classify-your-modules-before-you-touch-a-field),
[build the worklist](#step-1--build-the-worklist), then run the [decision tree](#the-decision-tree) on
every field in it and match against a [worked example](#worked-examples). The two steps before the tree
are what keep it affordable — they cut one measured app from 152 `static` hits to 13 fields worth asking
about.

Two alternatives are deliberately *not* on offer, so don't go looking for them: a per-user
classloader (which is what a screen-streaming product buys you, and is not available inside one
Vaadin app), and a "leave the statics, they're mostly read-only" pass — *mostly* is what the
decision tree below exists to replace with an answer per field.

## Conventions

Two placeholders appear in the commands on this page. Substitute both with real absolute paths before
running anything — an agent driving this guide is handed them already substituted.

- **`SWINGBRIDGE_HOME`** — the folder holding the SwingBridge Emulators kit: this file's own
  grandparent, i.e. the directory you unzipped, or your clone of the repository.
- **`MIGRATED_APP_FOLDER`** — the root of the app you are migrating.

## The three scopes

A migrated app has exactly **three** legitimate places for what used to be a `static` field,
named by their *sharing boundary*, narrowest first. **Tab is the default**; the other two are
demotions you justify per field (see [the tree](#the-decision-tree)):

| Scope | Lifetime | Distinct per tab? | Survives F5? | Mechanism today | Use for |
|---|---|---|---|---|---|
| **tab** | one running app instance | ✅ | ✅ | a [`FormerSingletons`](./former-singletons.md) holder, reached via `AppInstance` | the desktop "app singletons" — the frame tree + its per-app state |
| **session** | one user, all their tabs | ❌ | ✅ | `VaadinSession` attribute | that user's private data / identity / connection |
| **application** | whole deployment, all users | ❌ | ✅ | a `static` singleton | genuinely-global, read-mostly infrastructure |

And **one name that is not a scope you target:**

- ~~**UI scope**~~ — a **trap.** A `UI` *looks* like "one per tab," so it tempts you as the home
  for per-tab app state. But a browser **F5 destroys the `UI`** and builds a fresh one
  (`@PreserveOnRefresh` then teleports the component tree across). Anything hung off UI lifetime —
  a field cached against `UI.getCurrent()`, a `@UIScope` holder — is silently **nuked on every
  reload**. Never store app state there. When you want "one running app, for as long as this tab
  lives," that's **tab scope**.

> **tab ≈ session, today — but don't store it that way.** SB-Emulators runs **one app per session**:
> a second tab is curtained, not run, so the tab-scoped app instance and the session currently
> **coincide**. Keep the two *concepts* distinct — it's the correct mental model, and if SB-Emulators ever relaxes
> to concurrent tabs, the tab-scoped fields re-home to a real per-tab scope while the session-scoped ones
> stay put. **Tab-scoped state does not go in a `VaadinSession` attribute:** it goes in your
> [`FormerSingletons`](./former-singletons.md) holder, reached through `AppInstance`, which already stores
> it per browser tab ([vaadin-tab-scope](https://github.com/mvysny/vaadin-tab-scope)) and resolves it in
> places a session attribute reads fine but a hand-rolled tab lookup does not — your own
> `WINDOW_CLOSING` cleanup, most of all. Reach for `VaadinSession` for genuinely **session**-scoped state
> — and **never** for UI scope.

### The hard prohibition — a component is prototype- or tab-scoped, and nothing else

**A Vaadin/emulator component (a `JFrame`, `JPanel`, any `JComponent`, a model bound into one, a
`Registration`) must never be held at a scope *wider* than the component itself — never in a `static`
field, never in a session attribute — nor in a UI-scoped holder, which is the opposite problem
(narrower, and destroyed on F5).** Components do not use the three-scope table above — they have their own two-scope rule,
and the table's session and application rows are not among them. A component is either:

- **prototype-scoped** — a **fresh instance every time** it is needed: a dialog opened per click, a
  panel built per navigation, an editor built per row. There is nothing to store; construct it at
  use and let it go. This is the right answer far more often than migrators expect, because on the
  desktop a long-lived frame made reuse free.
- **tab-scoped** — it belongs to the **tab's app-instance component tree**, built in `mainUI()` /
  the frame ctor, and **the tree itself is the storage**: a plain *instance* field on the component
  that owns it, reachable from the root. That is not a cache and not a scope holder — a component
  keeping its own children in instance fields is how every Swing and Vaadin component is written,
  and it is exactly where a `static JPanel bodyPanel` should land. A *second* reference to it is fine
  **only at tab scope** — a [`FormerSingletons`](./former-singletons.md) member, which is where a
  `static` that other classes read from lands. What you must not do is hold it at a wider scope.

**Never session-scoped, never singleton/application-scoped.** Those two rows exist for *data*, not
for components.

Code that isn't in the tree reaches a tab-scoped component either through the
[`FormerSingletons`](./former-singletons.md) holder — a tab-scoped reference, so it survives the F5
teleport still pointing at the same live instance — or by **look-up at use-time** (`UI.getCurrent()`, or
the active-UI pointer the `@MainWindow` route maintains). What breaks is a reference stored at a *wider*
scope, which is shared across users, or at UI scope, which F5 destroys. Where the reach is behind a
`static` accessor, the accessor can stay: see
[the accessor worked example](#a-static-accessor-over-global-state--keep-the-accessor-rewrite-its-body).

- **`static`** leaks the frame across every user — a hard cross-user bug (user B sees user A's frame).
- **UI-scoped cache** is nuked outright on F5 (the `UI` is destroyed; the tree moves to a fresh one).
- **session attribute** is the subtle one — and the rule is firm anyway: **don't.** It doesn't
  *crash* today (`@PreserveOnRefresh` preserves the same instance across F5, and the one-app-per-session
  curtain keeps it from being attached to two UIs at once) — but that's safe *by policy accident*,
  not by design. A component belongs to a UI, not a session; stashing it there couples your app to
  the at-most-one-tab invariant and reintroduces the classic
  [session-scoped-view failure](https://mvysny.github.io/session-scoped-route/) — the same instance
  yanked between two UIs — the moment concurrent tabs land. Don't write code that's correct only
  because of the curtain.

The mechanical floor under this rule is the **static-state gate of `MigrationGuardrails`**
([guide Phase 6 step 6](./guide.md#phase-6--post-migration-verification)), which flags every `static`
field that is not provably immutable and not [allowlisted](#the-allowlist-annotation) — so it catches
`static MainFrame FRAME` (which a name-based grep for `static .*JFrame` misses), and it catches the
`static` POJO holding a component too, without any transitive analysis, because the POJO is a mutable
type. What it still cannot see is a component stashed in a `VaadinSession` attribute: ArchUnit cannot
inspect call-argument values, so that stays a review item.

## Step 0 — classify your modules before you touch a field

A module with **no dependency on any component type** proves, transitively, that none of its fields
holds a component — which discharges the most expensive question in the tree for every field it
contains, in one check. On a large app with a real service layer that is most of your statics, and it
is what keeps this sweep affordable at all.

**How to classify, in two phases.** Phase one needs no build, which is why it goes first — the ArchUnit
rule below needs compiled classes, and this pass is what tells you *what to swap*, so it cannot wait on
the swap compiling.

1. **List your modules.** Maven: every `pom.xml` that is not `<packaging>pom</packaging>` (aggregators
   hold no code). Gradle: the `include(...)` lines in `settings.gradle[.kts]`.
2. **Grep each module's own sources** (nearest-ancestor pom wins, so a parent doesn't absorb its
   children's files) for these package tokens:

   ```
   javax\.swing|java\.awt\.(event|dnd|datatransfer)|java\.applet
   |com\.jgoodies|com\.toedter|org\.jdesktop|org\.swinglabs|net\.miginfocom
   ```

   A hit means the module is UI-bearing. Grep the **token, not the `import` line** — that catches
   fully-qualified use without an import (`javax.swing.JPanel x = …`) for free, and its only false
   positives are javadoc and comments, which cost you one look. Grep every language the module compiles,
   not just `*.java`.
3. **Close over your dependency graph** (`mvn dependency:tree` / `gradle :m:dependencies`): a module that
   depends on a UI-bearing module is UI-bearing too, unless you prove otherwise. Step 3 is not
   belt-and-braces — see the third bullet below for what step 2 structurally cannot see.

Three things keep it honest:

- **It prunes the tree; it doesn't end it.** `Q_user_specific` still runs per field, in **every** module —
  UI-free ones included. A `static User currentUser` in a pure service module is the cross-user leak this
  whole page exists to catch, and "that module has no Swing in it" does not exempt it. The pre-pass makes
  the tree shorter, not optional. Service-layer
  statics are routinely per-user: a `SessionUtils.currentSession` (as against the perfectly fine
  `sessionFactory`), a `static User` security holder, a cache implicitly keyed by whoever logged in, a
  runtime-mutable "current tenant" or working directory. `Q_component` is the *only* question the
  module boundary answers.
- **Verify it; don't take the claim.** Desktop "service layers" leak UI constantly — a service that
  pops a `JOptionPane` on failure, or holds the main frame to parent its dialogs. Run an ArchUnit rule
  rather than trusting the module's name. When it *fails*, its output is your UI-leak list, which is
  worth having on its own.
- **The discriminator is component types, not the `java.awt` package.** A module using `Color`,
  `Dimension` or `Insets` is still UI-free — those are ordinary value objects. Which is why bare
  `java.awt` is not in the phase-one regex, and why you resolve it by rule rather than by feel: an
  **explicit** `java.awt.X` classifies on `X` — value objects (`Color`, `Font`, `Dimension`, `Insets`,
  `Point`, `Rectangle`, `geom.*`, `image.*`, `BufferedImage` included) are UI-free, anything else is not —
  while a **wildcard** `import java.awt.*` tells you nothing and sends you to grep that module for AWT
  component names. Expect wildcards: one real app we measured had 25 files importing `java.awt.*`.
  `Toolkit` is on neither list; it's a desktop-platform assumption (spec §7), not a component.
- **A module can be full of components and still grep clean**, if its classes extend a Swing type declared
  in *another* module. One app we migrated has 13 panels extending an app-internal
  `AbstractFunctionPanel` — and it is `AbstractFunctionPanel`, not the panels, that extends `JPanel`. Split
  across modules, those 13 files contain no Swing token at all. That is what step 3 is for, and why it
  errs toward including a module: a wasted look costs minutes, a missed `static` ships a cross-user leak.

Your existing service layer is an **asset** here: it is the evidence. Nothing in this migration asks
you to dissolve it.

## The allowlist annotation

A field the tree routes to **application** scope — it stays `static` — is marked, so that "we swept
this" is visible in the source rather than remembered:

```java
import com.vaadin.swingbridge.migration.IntentionallyStatic;
import static com.vaadin.swingbridge.migration.IntentionallyStatic.Reason.JVM_INFRASTRUCTURE;

@IntentionallyStatic(JVM_INFRASTRUCTURE)
private static final SessionFactory sessionFactory = buildFactory();
```

The reason is a constant of the nested `IntentionallyStatic.Reason` enum, so the bare name needs the
static import; the four reasons are listed below. The two imports are the same in every app.

```xml
<dependency>
    <groupId>com.vaadin.swingbridge</groupId>
    <artifactId>swingbridge-migration-annotations</artifactId>
    <version>${swingbridge-emulators.version}</version>
</dependency>
```

**Compile scope — the default, and what you want here**, because your own `src/main` sources are what
carry the annotation. [Phase 6 step 6](./guide.md#phase-6--post-migration-verification) lists this
artefact directly beneath `migration-guardrails <scope>test</scope>`, and copying the neighbour's scope
along with it puts the annotation somewhere the fields that need it cannot see. SB-Emulators publishes **no BOM**,
so the `<version>` is not optional either — it is the same `${swingbridge-emulators.version}` property each add-on's
`MIGRATION.md` uses.

**`com.vaadin.swingbridge.migration.IntentionallyStatic`'s javadoc is the canonical definition** of which fields pass the
gate untouched, which are flagged, and what each of the four reasons claims — it is the same definition
the build gate enforces, so this page points at it rather than restating it. Read it in your IDE; that is
the whole reason it lives there instead of here.

***Watch out: on a SNAPSHOT kit there is probably no javadoc to read.*** Sources and javadoc jars are
attached by the **release** build only, so an artifact whose version ends in `-SNAPSHOT` normally resolves
with neither, and no IDE can show you the definition above. If your `~/.m2` has them anyway, they are left
over from an earlier release-profile install — compare their dates with the main jar's before trusting them
over the summary below. The summary below is the working definition there — and if you need
more than the summary, `javap -p` over the resolved jar gives you the members, or read the annotation's
source in the SB-Emulators checkout that installed it.

The headline, because you need it to read the tree: a field passes with no annotation only if it is
`final` **and** its type is immutable — both, since `final` governs whether the *reference* can change
and the type governs whether the *referent* can.

**The reason is mandatory and there are exactly four**, one per terminal verdict of the tree.
Annotating is cheaper than routing, so a bare marker would get rubber-stamped; a closed set refuses
"because I said so", and if no reason fits, the field does not belong at application scope. The
annotations are also the review artefact — a reviewer reads them instead of the codebase, and their
counts per reason are a signal a single total would hide.

| reason | what it claims | the trap |
|---|---|---|
| `IMMUTABLE_CONSTANT` | a constant of a mutable type that is never mutated — `List.of(…)`, an array written once and only read | no write reference outside initialization **and** no mutating call on the object; if either exists this is the wrong reason |
| `WORLD_GLOBAL_READ_MOSTLY` | global to **all users** rather than to the app, and read-mostly: immutable config, read-only reference data | a cache the app writes on save is **not** this — the write query refutes it; such a cache is still correct shared, so it stays at application scope under `JVM_INFRASTRUCTURE` |
| `JVM_INFRASTRUCTURE` | a process-level **service** rather than data: connection pool, Hibernate `SessionFactory`, logger, thread pool | the *factory* belongs here; a *current session* or *current transaction* does not, being per-user or per-call |
| `COUNTER` | a monotonic counter / sequence / accumulator where sharing across users is the **feature** — it behaves like a database sequence, so gaps and interleaving are fine | move it to session scope when a user must see a **contiguous private** sequence ("your record #1, #2, #3" must not skip). Always write a `note` here — the verdict rests on a judgement about your app, not on a property of the field |

`note()` is optional and defaults to `""`; write it wherever the claim is not self-evident from the
declaration. Two details from the javadoc that the tree needs and this table can't hold: the
**logging facades** (`org.slf4j.Logger` and friends) pass unannotated despite not being provably
immutable, and where a field is really a constant that lost its modifier — the `static Logger logger = …`
idiom — the fix is to **add `final`**, not to annotate. And `Dimension` / `Insets` / `Point` / `Rectangle`
are **flagged** here (they expose public mutable fields), even though
[Step 0](#step-0--classify-your-modules-before-you-touch-a-field) treats them as ordinary value objects —
different question, different answer.

The artefact has **no dependencies**, deliberately: your UI-free service modules annotate their
allowlisted statics too, and
[Step 0](#step-0--classify-your-modules-before-you-touch-a-field) classifies a module UI-bearing if it
depends on one that is. A jar that dragged in Vaadin would make annotating a service module change that
module's classification.

The gate that enforces this javadoc is `com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails`
(`com.vaadin.swingbridge:swingbridge-migration-guardrails`, test scope) — see
[guide Phase 6 step 6](./guide.md#phase-6--post-migration-verification) for the six lines that install
it. Its own test suite carries a fixture per category listed above, so the two cannot drift apart.

## Step 1 — build the worklist

`grep -rn '\bstatic\b'` over a real app is mostly noise, and the noise is not uniform. Measured on one
64-file / ~9,800-LoC app: **152 hits → 47 field declarations → 13 that reach the tree → 2 that end up in
the holder.** Each kind of hit discharges by a different rule, so this step is not "find the statics" — it is
**sort them by declaration kind and write down the counts.**

**Expect the holder to be the smallest number in the funnel, by a lot.** 47 fields shrink to 13 because
`serialVersionUID`, string, colour and model constants are allowlisted outright (below). The 13 then
yield only 2 holder members, because reaching the tree is not the same as landing in the holder: on that
app, 4 fields only needed making `final`, 2 took [`@IntentionallyStatic`](#the-allowlist-annotation), 1
went to session scope, 3 became plain instance fields on the component that builds them ("the tree is
the storage", below), and 1 dissolved into a local. Six of the 13 were component-typed and **still** only
two of those became members — if your holder is coming out roughly as large as your reached-the-tree
count, you are probably parking owned widgets in it instead of letting the tree hold them.

Five buckets:

| bucket | where it goes |
|---|---|
| **fields** | [the decision tree](#the-decision-tree), one row each |
| **`static { }` blocks** | [`former-singletons.md` § `static { }` blocks](./former-singletons.md#static---blocks) — triaged, not routed |
| **methods** | `Q_method_or_type`: safe *unless* the body reads or writes a static field, in which case the field routes and the method keeps its signature |
| **nested types** (`static class` / `enum` / `interface` / `record`) | discharged — a `static` nested type is a top-level type in disguise. ***Watch out:*** discharged *by this sweep*, not "harmless" — a nested class can still be a cell editor with its own lifecycle bugs; it just isn't a scope question. Its own `static` fields are separate rows here |
| **`import static`** | noise |

**Run the tool over your compiled classes; its report is the worklist.** Not a regex, and not your
language server's document symbols either — both classify text, while the class files carry the
compiler's own answer:

```bash
SWINGBRIDGE_HOME/tools/bin/static-sweep \
    MIGRATED_APP_FOLDER/target/classes \
    --src MIGRATED_APP_FOLDER/src/main/java \
    --report MIGRATED_APP_FOLDER/swingbridge-migration/reports/static-sweep.md
```

`--src` warns when your build has gone stale; add `--lib MIGRATED_APP_FOLDER/target/dependency` (after
`mvn dependency:copy-dependencies`) or `--cp` if your app has dependencies to resolve against.

It sorts every `static` field into the buckets above and prints the reaching-the-tree ones as a table
with the mechanical columns already filled: declared type, whether that type is a **component**
(subclasses included — resolved by loading the type and asking the JVM, not by matching a name),
generic type arguments erasure would have hidden, the declaration line, the methods that **write** the
field outside initialization, and the methods that read it. It also answers `Q_method_or_type` in its
own section — a `static` method's `getstatic` / `putstatic` operands *name* the field its body touches
and say whether it reads or writes it — and counts the constants bucket rather than dropping it.

The error budget is why this is a tool and Step 0's partition is a grep. Step 0 tolerates a false
positive: including one module too many costs you a look. Step 1's output **is** the worklist, and a
field missing from it is never looked at again. On the app whose funnel is quoted above, the tool's
worklist came out at **20 rows: the 13 that reach the tree, plus 7 the hand-built table had missed**
(a fixture class's `static final Date`s) and that the Phase 6 gate later caught anyway.

Two things it does not do, so you know what is left. It gives **no verdicts** — which `Reason` a row
claims, whether state is session- or tab-scoped, whether a counter must stay contiguous. And a type it
cannot load is reported **unresolved** rather than guessed at; those rows stay on the worklist and you
read the declaring class. That is the whole residue: on the measured app, three real decisions out of
thirteen routed rows.

Two rules for the list itself:

- **Group by declaring class, not by field.** The tree runs per field, but two of the operations don't:
  `Q_method_or_type` needs the class's *other* statics in view to answer "does this body touch one", and a
  `static { }` block is triaged statement-by-statement into the fields it configures. Sweep one class at a
  time.
- **You may filter constants out; you may not drop them.** `serialVersionUID`, string constants and
  model constants are the bulk of the field bucket and none of them reach the tree, so filtering them
  keeps the list readable. Which fields those are is defined in one place — the
  [`@IntentionallyStatic` javadoc](#the-allowlist-annotation), the same definition the build gate
  enforces — so don't re-derive it here. Record the filtered set **as a counted bucket** rather than
  silently shortening the list: a count you can reconcile is what turns "did we get them all?" into a
  check, and the [completeness diff](./former-singletons.md#checking-you-finished) needs a number to diff
  against.

**Why the class files and not a grep:** the sweep's unit is a *field declaration*, and text search
cannot tell one from a `static` method, a local, or the word inside a comment — so a grep worklist is
both too long and, where a declaration spans lines, short in the one place it matters. The field table
in a class file has neither problem: it is what the compiler recorded.

**When you are done, diff rather than count.** Re-run the same tool over the stage-1 snapshot
[Phase 1 keeps](./guide.md#S_run_static_sweep), with `--diff` pointed at the migrated tree's
`target/classes`, and every row above comes back with a fate — *gone*, *made a constant*, *annotated*, or
**still unvetted**, plus any `static` the migration itself introduced. That is
[the completeness check](./former-singletons.md#checking-you-finished) as one table instead of two
counts you compare by eye.

## The decision tree

Run for each `static` hit:

```
Q_method_or_type. Is it a static *method* or nested *type* (not a field / not a
    static block)?
    └─ YES → does its body read or write a static field?
             └─ NO → SAFE, leave it. A `static Date ymd(...)` helper, a `static`
                     factory, a `static final class` — none hold cross-request
                     state.
             └─ YES → route the FIELD through the questions below first, then
                     come back. The METHOD may stay static: rewrite its body to
                     resolve from the field's new scope, and every call site
                     stays untouched. That is the cheapest correct migration and
                     idiomatic Vaadin. Drop any memoization — the accessor
                     FINDS, it never CREATES. See the accessor worked example.
                     Only READERS keep their `static`. A static method that
                     BUILDS AND ASSIGNS a field the tree routes to an instance
                     field ("the tree is the storage") becomes an INSTANCE
                     method too: there is no scope left to resolve from,
                     because the new home is `this`. Its call sites do change —
                     which is fine while they are all inside the owning class,
                     and is a reason to check that they are.
             Its OWN body, one level — do not chase callees. Every static is
             its own worklist row, so a helper whose callee touches a static
             field needs no analysis: the callee and the field each route on
             their own.
             (Exception: a static method that only ever runs before any app
             instance exists may stay static as-is. Read that precisely —
             see "Before boot" below; in an unsplit Swing app it does not
             mean what it looks like.)
    └─ NO (a field, a static-final field initialiser, or a static block)
             → Q_component.

Q_component. Is the field's OWN declared type a Vaadin/emulator *component*
    (JFrame, JPanel, any JComponent, a model bound into one, a Registration)?
    Its own type only — do NOT work out what it transitively reaches here.
    That question is asked once, later, and only where it changes a verdict:
    see "The demotion guard" below.
    └─ YES → the component is PROTOTYPE- or TAB-scoped; the `static` goes either
             way. Prototype: drop the field, construct at use. Tab: build it in
             mainUI()/the frame ctor and hold it in a plain INSTANCE field on
             its owning component — the tree is the storage. Code outside the
             tree looks it up at use-time (UI.getCurrent(), or the active-UI
             pointer) rather than keeping a second reference, so the framework
             can teleport the tree on F5. NEVER static, NEVER session, NEVER a
             UI-scoped field. (See the prohibition above.)
    └─ NO → Q_user_specific.

Q_user_specific. Is the state *user-specific* — different per logged-in user, or
    holding a user's private data / identity / connection?
    └─ YES → SESSION SCOPE (VaadinSession attribute) — subject to THE
             DEMOTION GUARD below. Rule of thumb, and it is deliberately
             loose: anything that smells of a user and is not a component
             goes to session.
    └─ NO → Q_world_global.

Q_world_global. Is the state global to *all users* rather than to *the app* —
    and read-MOSTLY? Immutable config, read-only reference data, a connection
    pool, a JVM-level service. The test: does its state come FROM, or FEED, a
    resource outside the process (the DB, the classpath, deployment config)?
    └─ YES → APPLICATION SCOPE — may stay a static singleton, subject to THE
             DEMOTION GUARD below. Confirm read-MOSTLY *mechanically*, not by
             impression: no WRITE reference outside initialization. See
             "Read-mostly is a query, not a judgement" below. (A cache the app
             WRITES on save fails that test — see the worked example.)
    └─ AMBIGUOUS (a counter / sequence / accumulator) → Q_counter.
    └─ NO → TAB SCOPE. This is the DEFAULT, not a last resort: `static` meant
             JVM-global on the desktop, and the JVM's boundary is now the tab.
             A field that reached here is the app's own global — it belongs to
             one running app instance.
             HOW: it becomes a member of your app's one FormerSingletons
             holder, read through a `static` reader left on (or added to) the
             field's original class, so call sites stay `Owner.getX()`. Full
             pattern, incl. initializers and `static { }` blocks:
             former-singletons.md.

Q_counter. Special case — see the counter worked example below.
```

### The demotion guard

**Before you route a field to *session* or *application*, confirm it holds no component — this time
transitively.** If it does, it is **tab**: the [prohibition](#the-hard-prohibition--a-component-is-prototype--or-tab-scoped-and-nothing-else)
outranks both demotions.

This is the only place the transitive question is worth asking, and asking it here rather than at
`Q_component` is what keeps it cheap. Everywhere else it changes no verdict: a field that transitively
reaches a component and matches nothing else falls through to **tab** on its own, which is exactly what
`Q_component` would have said — the whole point of the tab default, and why
[`G_lint_blind`](#g_lint_blind--invisible-to-a-type-based-lint) can tell you not to bother proving
reachability. The one shape where it *does* matter is a holder that reaches both a component and user
identity (`static AppContext CTX` carrying the logged-in user *and* the main frame): `Q_user_specific`
would send that to a session attribute, putting a component at session scope, silently.

So the walk is needed for the minority of fields being demoted, not for all of them. Two shortcuts make
even those cheap:

- **[Step 0](#step-0--classify-your-modules-before-you-touch-a-field) already answers most of it.** If
  the field's declared type and everything it holds live in modules classified UI-free, it cannot reach a
  component. That is the pre-pass doing double duty.
- **When you cannot tell, do not demote.** Tab is the safe-wrong answer here for the same reason it is
  the fall-through.

### Read-mostly is a query, not a judgement

`Q_world_global` asks whether the state is read-**mostly**, and on the desktop the source carries no
evidence of what the author meant. Don't reconstruct the intent — measure the writes. **Read-mostly means
no write reference outside initialization**, and [Step 1](#step-1--build-the-worklist)'s report already
answers it: the *writers outside `<clinit>`* column is exactly that query, with a line per write.
Classify each against [`Reason.WORLD_GLOBAL_READ_MOSTLY`'s javadoc](#the-allowlist-annotation), which is
where the three initialization shapes are defined.

There is nothing to substitute here and no judgement to make about *whether* a write happened: a
`putstatic` is a write, and the column is a list of them. An empty cell means nothing writes the field
after initialization. What remains yours is reading the writes you were handed — a guarded lazy-init
counts as initialization, and only the declaration can tell you it is one. Note that a write from a
lambda is attributed to the synthetic method the compiler generated, `lambda$loginSuccess$0`, which
names the enclosing method.

That is what turns `static Map<Integer,Customer> CACHE` written on save from a gotcha you have to notice
into a query result. It errs in one direction, deliberately: a lazily-loaded immutable config map is
written once, inside a guard — but a lazy loader spread over a few methods may not read as guarded, and
then the field demotes to tab and gets rebuilt per app instance. Wasteful, correct, and the same
safe-wrong direction the fall-through was chosen for.

### "Before boot" — only meaningful after the `main()` split

`Q_method_or_type`'s exception spares a static method that "only ever runs before any app instance
exists". In an **unsplit Swing app that describes nothing**, because `main()` *is* the boot — so matching
the exception against your stage-1 source catches code that is about to become per-user, which is the
trap. It is only well-defined against the shape [Phase 3](./guide.md#phase-3--entry-point-setup) produces:

- **`main()`** — process-level, once per JVM, no user and no UI. A method reached only from here genuinely
  runs before boot, and the exception applies.
- **`mainUI()`** / the `@MainWindow` frame's constructor — once per **app instance**, per user, per tab.
  Anything reached from here is emphatically not pre-boot, and the exception must not apply. This is where
  most of a Swing `main()`'s body lands.

The sweep runs in Phase 1 and the split happens in Phase 3, so you are judging by the call site's
*intended* destination. **If you cannot tell which side it lands on, don't take the exception** — route the
field and move on.

A `static { }` block is the easier half and needs none of this: it runs once per classloader, so it holds
no per-user state whatever the split does. Its *timing* is a separate hazard — arbitrary, and possibly
inside a user's first request — covered by
[`G_construction`](#g_construction--runs-before-a-ui-exists).

**Read the tail of that tree carefully — the default changed.** The nodes and their order are exactly
as before; what changed is the **last** line. A field that matches nothing now routes to **tab**, not
to "may stay static" — so application scope is the exception you have to *argue*, with the evidence
`Q_world_global` asks for. The reason is not that statics are wicked; it is which way you want
to be wrong. Guess "tab" about a field that was genuinely global and you rebuild a cache per tab — a
memory cost you can measure. Guess "static" about a field that was the app's own and you ship a
cross-user leak that compiles, boots, passes every gate, and surfaces in production as another user's
data. Every verdict above the fall-through is unchanged; only the residue moves, and the residue is
exactly where nobody was sure.

## The catalogue — every shape you'll meet

Grouped by **what the `static` holds**, because that is what decides the routing. Each row is
routable from the declaration alone. Where a shape needs code to be clear, the row points at a
[worked example](#worked-examples) below.

**Most of your statics are in the last group and need nothing.** Measured across this project's
migration testbeds, **roughly three in five `static` fields are immutable constants** that route
nowhere. Over-migration is a real failure mode — a large, risky diff that fixes nothing — so read
[`G_benign`](#g_benign--genuinely-fine-leave-them-alone) before you start, not after.

### `G_windows` — the app's singleton windows

| id | shape | verdict |
|---|---|---|
| `C_frame_singleton` | `private static AppFrame _instance;` + `getInstance()` | **tab** — holder member; keep the accessor, [rewrite its body](#a-static-accessor-over-global-state--keep-the-accessor-rewrite-its-body) |
| `C_bare_static_frame` | `public static JFrame mainFrame;` assigned in `main()` | **tab** — holder member; *create* the reader it never had |
| `C_reused_dialog` | `private static AboutDialog about;` — built once, re-`setVisible(true)` | **prototype** — construct per open |
| `C_cached_filechooser` | `private static JFileChooser chooser = new JFileChooser();` | **prototype** — construct at use |

The last two look like caching and are really *desktop motives that no longer exist*. A reused dialog
was kept to avoid rebuild cost and to preserve its position and size between shows; in a browser the
window position is not yours to remember. A cached `JFileChooser` existed to remember the last
directory, and there is no directory. **Drop the caching rather than porting it** — otherwise you
spend effort preserving a behaviour that cannot happen.

### `G_global_widgets` — one widget, poked from everywhere

Every row here routes the same way: **tab** — a holder member, read through a `static` reader on the
field's original class. See [`former-singletons.md`](./former-singletons.md).

| id | shape | what it was for |
|---|---|---|
| `C_status_widget` | `public static JLabel statusBar;` | `statusBar.setText(...)` from any class, any depth |
| `C_log_pane` | `static JTextArea logArea;` | `logArea.append(...)` as a poor-man's logger, often off-thread |
| `C_progress_bar` | `static JProgressBar progress;` | a long-running task reporting progress |
| `C_nav_container` | `static JPanel bodyPanel;` | the swap target — `bodyPanel.removeAll(); add(next)` |
| `C_menu_bar` | `private static JMenuBar MAIN_MENU_BAR;` | enable/disable items by permission from anywhere |
| `C_tool_bar` | `static JPanel toolBarPanel;` | same, for buttons |
| `C_tabbed_pane` | `static JTabbedPane tabs;` | "open X in a new tab" called from unrelated code |
| `C_desktop_pane` | `static JDesktopPane desktop;` | MDI — `desktop.add(new SomeInternalFrame())` from anywhere |
| `C_shared_combo` | `static JComboBox<Vendor> cboVendor;` | one lookup combo refreshed when its table changes |
| `C_main_table` | `static JTable mainTable;` | refresh-after-save, from the dialog that saved |

On a server every one of these is the same cross-user bug: two users share the widget, so B's status
message lands in A's window — and for `C_nav_container`, B's navigation replaces A's screen.

**Tab scope is the routing; whether it needs a holder *member* depends on the readers.** Every row here
is a widget *poked from everywhere*, and that outside reader is what makes the holder member necessary.
A field of the same shape with no reader outside the class that builds it — a `private static JPanel
bodyPanel` only its own frame touches — is tab-scoped just the same, but the tree is already the
storage: it becomes a plain instance field on that frame and no member. **Match on the readers, not on
the declaration.** On the measured app, three of these very shapes (`C_menu_bar`, `C_nav_container`,
`C_tool_bar`) resolved that way and never reached the holder.

### `G_lint_blind` — invisible to a type-based lint

An ArchUnit rule that resolves the type hierarchy catches `static MainFrame FRAME`. These get past it,
because none of them *is* a component by type. **They still route to tab**, and that is the point of
the tab default: you do not have to prove a field reaches a component to route it safely. The one
exception is a field you are about to *demote* to session or application — see
[the demotion guard](#the-demotion-guard).

| id | shape | verdict |
|---|---|---|
| `C_static_model` | `static DefaultTableModel model;` / list / combo / tree | **tab** |
| `C_button_group` | `static ButtonGroup group;` | **tab** — it holds every button added to it |
| `C_static_action` | `public static final Action SAVE = new AbstractAction(){…};` | **tab** — `enabled` is per-user, and the closure usually captures a component. Has an initializer → [lazy accessor](./former-singletons.md#fields-with-an-initializer) |
| `C_static_renderer` | `static DefaultTableCellRenderer r = new …;` | **tab**, same |
| `C_transitive_pojo` | `static AppContext CTX;` where `AppContext` has component fields | **tab** |
| `C_static_collection` | `static Map<String, JPanel> CACHE;` / `static List<JFrame> OPEN;` | **tab** — unless it is genuinely world-global reference data, in which case `Q_world_global` |
| `C_self_registry` | `static List<MyPanel> ALL = new ArrayList<>();` + `ALL.add(this)` in the ctor | **tab** — and see the note below |

`C_static_model` is the nastiest of the set and worth its own moment: it is invisible to a type-based
lint, it cross-talks between users, **and** it leaks components — a `JList` registers itself as a
`ListDataListener` on its model, so a static model retains a listener pointing at every user's list
that ever bound to it. Two bugs, no type-based gate, and a declaration that looks innocuous.

`C_self_registry` is the one case where routing to tab **fixes less than it looks like**. It stops the
registry from accumulating every *user's* components — which was an unbounded leak for the life of the
server — but it still grows without limit *within* one long-lived tab. The scope fix is real and worth
making; the lifetime bug is your app's, and it was there on the desktop too. Note it and move on, or
fix it properly while you are in there.

### `G_user_state` — genuinely per-user

Only identity and private data belong here. **Working state does not** — two tabs of the same app
should have independent selections and independent timers, and putting those in the session makes one
tab's actions steer the other's.

| id | shape | verdict |
|---|---|---|
| `C_login_flag` | `public static boolean isLoggedIn;` | **session** — one login logs everybody in |
| `C_current_user` | `static User currentUser;` / `static String username` | **session** — every user is whoever logged in last |
| `C_selection_state` | `static int selectedCustomerId;` | **tab** — it is the app instance's working state, not the person's; B's selection must not steer A's next save |
| `C_static_timer` | `static javax.swing.Timer timer;` | **tab** — the app's timer, not the user's identity. A `static` one is one user's timer firing into another's UI |

### `G_construction` — runs before a UI exists

| id | shape | verdict |
|---|---|---|
| `C_static_initializer` | `static final JPanel P = new JPanel();`, or a `static { }` block building widgets | **tab**, as a [lazy accessor](./former-singletons.md#fields-with-an-initializer); blocks get [triaged](./former-singletons.md#static---blocks) |
| `C_main_bootstrap` | `public static void main(String[])` building the frame into a static | the entry point itself — replaced by `@MainWindow` + the route |
| `C_early_read` | a `static` initializer reading `Preferences` or the browser zone | [defer to a UI-thread call site](#static-initializer-reading-the-browser-zone-or-preferences--defer-to-a-ui-thread-call-site) |

**`C_static_initializer` has no runtime symptom.** Constructing a Swing emulator with no UI present
does not warn and does not fail — it quietly succeeds, and the widget is then shared by every user of
your server with no sign of trouble until two people use the app at once. Only `JSpinner` with a
`SpinnerDateModel` and `Timer` throw, both with a message naming the cause. A `JFrame` built at class
load is silently half-built: its shutdown handler never registers, so it never runs.

**They are rarer than they sound.** Across three migration testbeds there were **two** `static { }`
blocks in total, both benign, and **no** UI field with an initializer. Do not go hunting; route the
one you find.

### `G_benign` — genuinely fine, leave them alone

The largest group, and the one that most needs saying out loud: **routing these away produces a large,
risky diff that fixes nothing.**

| id | shape | verdict |
|---|---|---|
| `C_ui_constant` | `static final Color` / `Font` / `Dimension` / `Insets` / `KeyStroke` / `String` | fine — immutable value objects |
| `C_static_icon` | `static final ImageIcon LOGO = new ImageIcon(…);` | fine — not a component, shareable, read-only |
| `C_static_border` | `static final Border THIN = BorderFactory.create…;` | fine — becomes CSS, no per-user state |
| `C_static_logger` | `static Logger logger = Logger.getLogger(Foo.class);` | fine — but **add the missing `final`**; see below |
| `C_static_helper` | `static String fmt(Date d)` with no static-field access | fine — `Q_method_or_type` answers this |
| `C_static_pool` | `static ExecutorService POOL` / a Hibernate `SessionFactory` | fine at application scope |
| `C_counter` | `private static int idSequence = 1000;` | application, usually — [see the example](#monotonic-counter--id-sequence--application-usually-may-stay-static) |

**`C_static_logger` deserves the callout** because it defeats the obvious lint. The log4j-era idiom
declares the field *without* `final`, so a rule of "flag every non-`final` static" flags one per class
— in the testbed we measured, loggers were **4 of the 14** non-final statics, nearly a third of the
noise. The gate resolves it at both ends, and you need both or it contradicts itself: **add the missing
`final`** (a one-keystroke edit, and it is what the field always meant), and the gate counts a logging
facade — `org.slf4j.Logger`, `org.apache.log4j.Logger`, `java.util.logging.Logger` — as an immutable
type, so the fixed field passes with no annotation. A logger that stays non-`final` is still flagged,
and should be: the gate cannot tell it from `public static boolean isLoggedIn`. Loggers are the one
entry on that immutable-type list not provably immutable; the claim is only that nobody reassigns one
and no logging call mutates state a migration cares about.

One trap hiding in this group: **`static final DateFormat` / `SimpleDateFormat` is not benign** — it is
mutable and not thread-safe, and one EDT meant a desktop app never noticed. Here the import swap fixes
it for you (the field stays as written); it is called out because it is a real concurrency bug that the
migration exposes rather than creates. One wrinkle if the field is **declared** as `DateFormat`
(`static final DateFormat FMT = new SimpleDateFormat(…)`, a common shape): the swap rewrites the
initializer but not the declared type, and the gate reads the declared type — a plain
`java.text.DateFormat` really is mutable, so it is flagged. Narrow the declaration to
`vaadinx.text.SimpleDateFormat` and it passes unannotated.

## Worked examples

Migrators reason by matching a field against an example far more reliably than against an abstract
rule, so the shapes that need code get one here. Find the one that looks like yours — or start from
[the catalogue](#the-catalogue--every-shape-youll-meet) and follow its link.

### A component in a `static` (or session) field → **prototype or tab tree** (the prohibition)

```java
// BEFORE (Swing): the classic "stash the main frame" idiom
public class App { public static MainFrame FRAME; }          // ← static component

// ALSO WRONG under SB-Emulators: moving it to the session doesn't fix it
session.setAttribute(MainFrame.class, frame);                // ← session component
```

Both are wrong (see [the prohibition](#the-hard-prohibition--a-component-is-prototype--or-tab-scoped-and-nothing-else)).
Pick the component's real scope:

```java
// PROTOTYPE — a dialog/panel needed per click or per navigation: no field at all.
void onEdit(Row row) { new EditDialog(row).setVisible(true); }

// TAB — part of the app's one component tree: a plain INSTANCE field on its owner.
public class MainFrame extends JFrame {
    private JPanel bodyPanel;              // ← was `static JPanel bodyPanel`
}
```

If other classes reached that `static` — which is usually *why* it was `static` — the instance field
is not enough on its own, and the second reference goes in the tab-scoped holder:

```java
// FormerSingletons — one member per former static
public JPanel mainFrameBodyPanel;

// MainFrame, where the field is assigned
bodyPanel = new JPanel();
FormerSingletons.get().mainFrameBodyPanel = bodyPanel;

// MainFrame, keeping the name callers already use
public static JPanel getBodyPanel() { return FormerSingletons.get().mainFrameBodyPanel; }
```

Note what this handles that a look-up expression cannot: the **write**. A `static` assigned from a
constructor is a *registration*, and there is no "find it in the tree" expression that absorbs one.
Full pattern — naming, initializers, `static { }` blocks, what must not go in:
[`former-singletons.md`](./former-singletons.md).

The instance field is **not** a cache — it is the tree, and the tree is where a tab-scoped component
lives. What the prohibition forbids is a second reference held at a *wider* scope: the `static`, the
session attribute. (The UI-scoped holder is the opposite failure — narrower, and destroyed on F5.) A
second reference at **tab** scope is fine, and that is what a [`FormerSingletons`](./former-singletons.md)
member is.

### A `static` accessor over global state → **keep the accessor, rewrite its body**

The single most common shape in a desktop Swing app, and the one the tree's `Q_method_or_type` used to wave through:

```java
// BEFORE (Swing): a static field plus its accessor
public class MainFrame extends JFrame {
    private static MainFrame _instance;                                 // ← Q_component routes this away
    private MainFrame() { ... }                                         // ← private: the accessor is the only way in
    public static MainFrame getInstance() {
        if (_instance == null) _instance = new MainFrame();
        return _instance;
    }
}
```

`Q_component` takes the **field**. The **method can stay exactly where it is** — rewrite its body to look the
frame up at use-time (guide [Phase 1 step 3](./guide.md#phase-1--pre-flight)) instead of reading a
field, and **every `MainFrame.getInstance()` call site in the app is untouched**. One edit instead of
N. The same shape covers the session case, where the holder genuinely *is* the session:

```java
public static User getCurrentUser() {
    final VaadinSession s = VaadinSession.getCurrent();
    if (s == null) throw new IllegalStateException("getCurrentUser() off the UI thread");
    return (User) s.getAttribute(User.class);
}
```

This is idiomatic vanilla Vaadin, not a migration hack: a `static` getter that resolves from
`UI.getCurrent()` / `VaadinSession.getCurrent()` and throws when there is none is a normal pattern.
Two rules keep it honest:

- **The accessor finds; it never creates.** Delete the `if (_instance == null) …` line. A
  tab-scoped component is built once in `mainUI()` / the frame ctor; the accessor only resolves it.
  The method's *shape* survives the migration unchanged, which is precisely what makes it easy to
  keep the memoization by accident — and a memoizing accessor is the original leak with extra steps.
- **Widen the constructor, and register inside it.** The memoizing idiom nearly always comes with a
  **`private` constructor** — the accessor was the only way in — so the moment the accessor stops
  creating, nothing can: `mainUI()` lives in another package. Widen it to `public` and have it assign
  the holder member as its first statement, which is what makes the accessor genuinely find-only:

  ```java
  public MainFrame() {                                 // ← was private
      FormerSingletons.get().mainFrame = this;         // ← the registration the accessor used to hide
      // ... your existing constructor
  }
  ```

  This is the one edit in the transform that changes an access modifier, and it is invisible in a
  BEFORE/AFTER of the accessor alone.
- **Throw when the scope can't be resolved.** A null `UI` / `VaadinSession` means the call came from
  somewhere with no user context (a background thread, a static initializer). Failing loudly turns
  that into a one-line fix at the call site; returning null or a fresh instance turns it into a bug
  that surfaces as someone else's data.

Note the guardrail agrees with this pattern: the component check inspects *fields*, so a
`static` method that **returns** a component is correctly not flagged.

### User-specific config / identity → **session**

```java
public class Session { public static User currentUser; }      // ← per-user, was JVM-global on desktop
public class Db { public static Connection userConn; }        // ← a user's own connection
```

On the desktop these were global because the JVM served one user. On the server they're the
textbook session-scope case — `VaadinSession.getCurrent().setAttribute(...)`. A `static currentUser`
would show every user the *first* logged-in user's identity.

**A primitive has no class to key on** — `getAttribute(User.class)` has no `boolean` analogue — so
`C_login_flag`'s `public static boolean isLoggedIn` uses a String key, and gets a **writer shim as well
as a reader**, since the field was assigned as well as read:

```java
public class AppFrame extends JFrame {
    private static final String LOGGED_IN = AppFrame.class.getName() + ".isLoggedIn";

    public  static boolean isLoggedIn()         { return Boolean.TRUE.equals(session().getAttribute(LOGGED_IN)); }
    private static void setLoggedIn(boolean b)  { session().setAttribute(LOGGED_IN, b); }

    private static VaadinSession session() {
        final VaadinSession s = VaadinSession.getCurrent();
        if (s == null) throw new IllegalStateException("isLoggedIn() with no VaadinSession — called off the UI thread?");
        return s;
    }
}
```

Qualifying the key with the owning class is what keeps two former statics of the same name apart; a bare
`"isLoggedIn"` is a collision waiting for the second one. Note the reader's `Boolean.TRUE.equals(...)`
— a missing attribute reads as `null`, and unboxing it would NPE where the desktop's `boolean` was
simply `false`.

**Where `VaadinSession.getCurrent()` resolves.** The session is current in more places than the `UI` is,
and the difference matters when you are deciding whether the shim above can throw on you:

- **Yes** — on the UI thread; **inside `EHelper.callSwing`**, the virtual-thread envelope every peer →
  Swing callback runs in, which re-establishes the current session on its thread, so any listener body
  is fine; and **throughout the `WINDOW_CLOSING` shutdown dispatch**, where the session is still current
  even though the `UI` is not. That last one is the same reach
  [`former-singletons.md`](./former-singletons.md#what-still-throws) promises for the holder.
- **No** — `SwingWorker.doInBackground` and your own threads. `EmulatorContext` exposes the *zone* on a
  worker, never a *current* session, so read what you need on the UI thread and pass it in. And not at
  class-load or in `main()` before boot.

Those are the same two dead zones as the holder's, so a field routed to session and one routed to tab
are reachable from exactly the same places — you do not have to track two different availability rules.

### Application-wide immutable config / shared cache → **application** (may stay static)

```java
public static final String APP_VERSION = "3.2.1";            // immutable
public static final Map<String,Country> COUNTRIES = load();  // read-only reference data
public static final HikariDataSource POOL = buildPool();     // the pool, not a user's connection
```

Genuinely global, read-mostly, no user-visible per-user state. Leave static. **Watch the boundary
twice:**

- a "cache" that's actually keyed implicitly by the current user — a `static Map` populated with the
  logged-in user's rows — is the user-specific case in disguise, and belongs in session scope;
- a cache the app **writes on save** is not read-mostly and is the one place where routing it *away*
  from static costs correctness rather than memory:

```java
static Map<Integer,Customer> CACHE;          // read on load, WRITTEN in saveCustomer()
```

Shared, that cache is correct. Per-tab, tab A saves and tab B keeps serving the old row — a staleness
bug that looks like data loss. So *read-mostly* is the criterion, not "it's a cache": if writes flow
through it, keep it application-scoped (and make it thread-safe, which the single-EDT desktop never
had to).

### Monotonic counter / ID sequence → **application, usually** (may stay static)

```java
private static int idSequence = 1000;
private static synchronized int nextId() { return ++idSequence; }
```

Neither clearly user-scoped nor a cache. **May stay static if cross-tab / cross-user ID interleaving
is acceptable** — which it usually is, because it behaves exactly like a database sequence (globally
monotonic; gaps and interleaving are fine). Move it to session scope **only** if a tab must see a
*contiguous private* ID space (rare — e.g. IDs are shown to the user as "your record #1, #2, #3" and
must not skip). Note this is the *opposite* default from the user-specific case: a counter's
"shared across users" property is a feature, not a leak.

**The exception is a counter that numbers per-tab data.** The rule above assumes the IDs end up
somewhere every user shares — a database, a file, a server-side store. If the rows it numbers live and
die with the tab (an in-memory store the frame builds in `mainUI()`), the IDs never meet another tab's,
so sharing buys nothing and costs the desktop's behaviour: a second tab's first record becomes #1007
instead of #1001. Put the counter wherever that data lives — usually [tab scope](./former-singletons.md),
next to the store.

### Static initializer reading the browser zone or Preferences → **defer to a UI-thread call site**

The `static` keyword isn't the hazard here — the **class-load call site** is. A field or block that
runs at class-load, before any UI exists, can't read per-user browser state:

- **`Preferences` from a `static` initializer / `main()`.** `Preferences.userRoot()` is
  `localStorage`-backed and needs a live UI on first read — it throws `IllegalStateException` at
  class-load. Move the read into UI-thread code (the typical "restore window bounds in the frame
  ctor" shape is already safe).
- **`java.time` zone read at class-load** — `LocalDate.now()`, `ZoneId.systemDefault()`, a
  `DateTimeFormatter` built at class-load. `java.time` is **not** emulated, so these silently read
  the *server* zone. Construct/read at use time, passing `BrowserTimeZone.get()` as the zone —
  unless the read is reached only from `main()`, where no browser zone exists and the server zone is
  the intended answer ([`dates.md`](./dates.md), third
  residual; calendar dates still move to midday there).

**Not a hazard:** a `static final SimpleDateFormat` field. `vaadinx.text.SimpleDateFormat` keeps no
formatting state on the instance — each `format` / `parse` runs on a per-session backing — so the
swap fixes the zone timing *and* the thread-safety, and the field passes the Phase 6 gate
unannotated. Keep it exactly as written.

**A `static` `Calendar` / `GregorianCalendar` is a different case.** That emulator zones its
*construction* and is otherwise a plain thread-confined calendar, so a shared mutable one stays the
hazard it already was on the desktop — unshare it (construct one per use) rather than annotating it.
Full recipe: [`dates.md`](./dates.md).
