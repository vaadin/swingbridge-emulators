# `:migration-tool`

The three runnable tools a Swing→emulators migration uses, one per **mechanical** phase of the
[stage-2 guide](../guides/1-swing-to-emulators/guide.md):

| phase | class | what it does |
|---|---|---|
| 1 — pre-flight | `com.vaadin.swingbridge.migration.tool.hazardscan.HazardScan` | finds the known migration hazards in a source tree and writes them up grouped by hazard |
| 1 — `static` sweep | `com.vaadin.swingbridge.migration.tool.staticsweep.StaticSweep` | builds the `static`-field worklist from the app's compiled classes |
| 2 — import rewrite | `com.vaadin.swingbridge.migration.tool.importswap.ImportSwap` | rewrites `java.awt` / `javax.swing` / `java.util` / `java.text` references onto their `vaadinx` emulators |

All three **exit 0 whenever they ran**. Their output is an input to triage, never a verdict — none is
a build gate, and a non-zero status means the tool itself could not run (bad arguments, no table, no
class files, an unreadable file, or no path it was given that exists — a typo'd root otherwise reads
as an empty tree and reports as clean). A path that names nothing *beside* ones that do is a warning,
on stderr and at the top of the report, since the run still happened. The two source tools are driven by a tab-separated table read off
the classpath rather than by compiled-in knowledge, so for them **the classpath is the
configuration**; the sweep needs no table, because the compiler already resolved its input.

The module also hosts one thing that is *not* a tool: `guardrails.GuardrailEngine` +
`guardrails.ImmutableTypes`, the rule engine that `:migration-guardrails` fronts. **The contracts
here are per entry point, not per module** — a `main` prints and exits 0; the gate facade throws.

This README is how to *use* them. Why they are shaped this way lives in the decision log:
[M1D_import_swap_tool](../migration/1-swing-to-emulators/decisions.md#M1D_import_swap_tool),
[M1D_hazard_scan_tool](../migration/1-swing-to-emulators/decisions.md#M1D_hazard_scan_tool),
[M1D_static_sweep_tool](../migration/1-swing-to-emulators/decisions.md#M1D_static_sweep_tool).

## Getting the tool

The module packages itself as a distribution zip beside its jar — `bin/` + `lib/`, run with
`java -cp "lib/*"` — attached as the `dist` classifier, so it is one download:

```bash
mvn -q dependency:copy -DoutputDirectory=. \
    -Dartifact=com.vaadin.swingbridge:swingbridge-migration-tool:0.1-SNAPSHOT:zip:dist
unzip swingbridge-migration-tool-0.1-SNAPSHOT-dist.zip
cd swingbridge-migration-tool-0.1-SNAPSHOT
```

That gives you `bin/hazard-scan`, `bin/static-sweep` and `bin/import-swap`, plus `.cmd` twins for Windows — five-line
scripts resolving their own directory, honouring `JAVA_HOME` if set. **A JDK is the whole
prerequisite.** The distribution kit ships this same zip unpacked at `tools/`, with the three
table-carrying jars already in `lib/`.

> **The zip ships the engine; the swap tables travel with the jars they describe.** Phase 1 works
> out of the box — its pattern table is inside the tool's jar. Phase 2 reads every
> `META-INF/emul/ported-types.tsv` in `lib/`, so drop in `:emulators` plus one jar per
> [add-on](../guides/1-swing-to-emulators/addons.md) your app uses:
>
> ```bash
> mvn -q dependency:copy -DoutputDirectory=lib \
>     -Dartifact=com.vaadin.swingbridge:swingbridge-emulators:0.1-SNAPSHOT
> # one more per add-on, e.g.:
> # -Dartifact=com.vaadin.swingbridge:swingbridge-emulators-jgoodies-forms-1.2.1:0.1-SNAPSHOT
> ```
>
> Until one is there, `import-swap` **exits 2 with a message naming the fix** — a loud failure, not
> a run that rewrote nothing and reported success. **`-cp`, never `java -jar`:** `-jar` ignores
> `-cp`, which is exactly how you would turn that loud failure back into a silent one. If your build
> file already declares `:emulators` and your add-ons, `mvn dependency:build-classpath` gives you
> the same list in one step.

---

## Phase 1 — `HazardScan`

```bash
bin/hazard-scan src/main/java src/main/resources --report target/hazards.md
```

| option | effect |
|---|---|
| `--report <path>` | write the Markdown report to a file instead of stdout |
| `--list` | print the effective pattern table and exit |
| `--table <tsv>` | add a table, for running against a working copy |

**Point it at your resources too, not just your sources.** One hazard's evidence is in the app's own
HTML files — a relative `href` that Flow's allowlist will strip, leaving a help set whose navigation
is silently gone.

### Example output

Over `testapps/inventory/swing`, abbreviated:

````markdown
# Migration hazard scan

- roots: [`testapps/inventory/swing/src/main`]
- files read: 65 × `*.java`, 0 × `*.{html,htm}`
- hazard table: 21 rows from jar:file:/…/migration-tool-0.1-SNAPSHOT.jar!/META-INF/emul/hazards.tsv (21 rows)
- sections with hits: 8 of 18

**This is a finder, not a gate.** Every hit is a candidate to triage …

## `H_system_exit` — System.exit / Runtime exit-halt  [ArchUnit-gated]

spec.md §7 · files `*.java` · pattern `System\.exit|Runtime\.getRuntime\(\)\.(exit|halt)|\.(exit|halt)\([0-9]`

2 hits

```text
…/com/ca/ui/Main.java:84:                System.exit(0);
…/com/gt/uilib/components/button/ExitButton.java:44:            System.exit(0);
```

## `H_custom_layoutmanager` — Custom LayoutManager  [→ cloud-OK]

spec.md §7 · files `*.java` · pattern `implements +LayoutManager2?|…`

(clean)

…

## Manual checks — no scan signal

- `H_modal_from_listener` (§7) — Modal dialogs from listeners: verify recipe step 4 (@Push + the servlet's VirtualThreadAwareLock) is wired.
- `H_multi_tab` (§7) — Multi-tab / user-scoped singletons: run the static-field decision tree + the ArchUnit review-gate.

Done. Sections with hits: 8. Every hit is a candidate to triage, not a failure.
````

Three properties of that report worth knowing before you read one:

- **False positives are expected and fine.** Seven `SimpleDateFormat` hits are not seven problems —
  that hazard is fixed by Phase 2's import swap alone, and the section exists so you can check it off.
- **Clean sections are printed, not omitted**, so the report doubles as the phase's worklist. A
  hazard that vanished when it found nothing would leave you unable to tell *checked, clear* from
  *never looked*.
- **The hazards no scan can catch are a closing checklist**, because a hazard with no grep signal is
  exactly the one that gets forgotten.

### The pattern table

`--list` renders it without unzipping anything:

```
| id | section | files | pattern | label |
|---|---|---|---|---|
| `H_system_exit` | §7 | `*.java` | `System\.exit\|…` | System.exit / Runtime exit-halt  [ArchUnit-gated] |
| `H_html_pane_links` | §7 | `*.{html,htm}` | `href\s*=\s*["'](?!https?:\|mailto:\|ftp:)` | HTML pane links — non-web href in an HTML resource  [⚠silent] |
| `H_multi_tab` | §7 | — | — | Multi-tab / user-scoped singletons: run the static-field decision tree … |
```

Core's copy is [`src/main/resources/META-INF/emul/hazards.tsv`](src/main/resources/META-INF/emul/hazards.tsv);
every `META-INF/emul/hazards.tsv` on the classpath is unioned, so an add-on jar can contribute a
grep-shaped watch-out to the same scan. Row format, and the `#` comments recording why a pattern was
*retired*, are in that file's header.

**Editing a pattern is a two-file edit.** Each hazard carries an `H_` slug in the tsv's first column
*and* on an `<a id="H_…"></a>` anchor over its bullet in
[spec.md §7](../migration/1-swing-to-emulators/spec.md); `HazardTableTest` joins them in both
directions and fails the build if you touch only one.

---

## Phase 1 — `StaticSweep`

```bash
bin/static-sweep target/classes --lib target/dependency --report target/static-sweep.md
```

| option | effect |
|---|---|
| `--lib <dir>` | every `*.jar` in a directory joins the resolution classpath (repeatable) |
| `--cp <path>` | classpath entries directly, separated as your platform separates them (repeatable) |
| `--report <path>` | write the Markdown report to a file instead of stdout |
| `--diff <classes-dir>` | also report what became of each row in a second tree |
| `--src <dir>` | warn when the class files are older than the sources |

**Point it at the app you have not migrated yet.** A Swing app under migration builds by definition,
so its stage-1 classes exist on day zero — which is why the sweep needs neither a language server nor
the post-swap build the guide cannot assume. What comes off those class files beats a language
server's answer rather than matching it: kinds and declared types come from the compiler; a
`getstatic` operand *names* the field a body touches, where a references list leaves read-versus-write
to the reader; and the `Signature` attribute still carries the type arguments erasure was feared to
lose. `mvn compile` first — the sweep reads class files, and `--src` is there because a sweep of
yesterday's build is yesterday's worklist.

Handing in the app's dependencies (`--lib target/dependency`, after
`mvn dependency:copy-dependencies`) is optional and costs only precision: a type that will not load
is reported **unresolved** and is never demoted out of the worklist. Nothing in the app runs —
classes are loaded with `initialize = false`, so a `<clinit>` that would boot Hibernate or seed a
database is read as a type and nothing more.

### Example output

Over `testapps/inventory/swing/target/classes`, abbreviated:

````markdown
# Static-field sweep — testapps/inventory/swing/target/classes

> Newest class file: 2026-09-09 18:55 (pass `--src <dir>` to have this checked against your sources)

- class files read: 86
- classpath entries for type resolution: 1

## The funnel

| | count |
|---|---:|
| `static` fields, total | 73 |
| … synthetic (`$VALUES`, `$SwitchMap$…`, `$assertionsDisabled`) | 12 |
| … enum constants | 7 |
| … constants (`final` **and** of an immutable type) | 34 |
| … **worklist** (reach the decision tree) | 20 |
| …… of which the declared type is a component | 6 |
…

## Worklist — 20 row(s)

| # | class | field | type | `final` | component | line | writers outside `<clinit>` | readers | allowlisted |
|---:|---|---|---|---|---|---:|---|---|---|
| 1 | Main | `gui` | `AppFrame` |  | **YES** (AppFrame <: java.awt.Component) |  | setUpAndShowGui:23 | Main$1.componentResized, … |  |
| 5 | AppFrame | `currentWindow` | `AbstractFunctionPanel` |  | **YES** (AbstractFunctionPanel <: java.awt.Component) |  | <init>:59, lambda$5:215 | setWindow, ExitButton.handleExit |  |
| 6 | AppFrame | `isLoggedIn` | `boolean` |  | no (primitive) | 30 | loginSuccess:77, handleLogOut:259 | setWindow, lambda$2 |  |
| 14 | Seed | `PURCHASE_1` | `Date` | final | no | 51 | none | seed |  |
…

## `static` methods that touch the app's own statics — 20 of 120

- `SessionUtils.getSession` (line 43) — R sessionFactory
- `AppFrame.loginSuccess` (line 77) — W isLoggedIn, R logger
…

## Static initializers — 6 worth a look

- `SessionUtils` — **has an explicit `static { }` block**; invokes StandardServiceRegistryBuilder.configure,
  MetadataSources.buildMetadata, Metadata.buildSessionFactory …
````

Four things about that report worth knowing before you read one:

- **`currentWindow` and `isLoggedIn` are answered, not just listed.** `isLoggedIn ←
  loginSuccess:77, handleLogOut:259` refutes `WORLD_GLOBAL_READ_MOSTLY` before anyone opens the
  file; `sessionFactory ← none` supports `JVM_INFRASTRUCTURE`; a lambda's write shows up as
  `lambda$5:215`, naming the enclosing method.
- **The declaration line mostly recovers** — a field initializer's `putstatic` inside `<clinit>`
  carries it (13 of inventory's 20 rows). A field with no initializer has none, and its writers'
  lines are where to start.
- **Every bucket is counted, not dropped.** The synthetic and enum-constant counts are what let the
  funnel reconcile against your own `javap -p`: inventory's stage-2 `60 − 11 = 49` is exactly the
  number one migration run arrived at by hand.
- **The residue is small and it is the interesting part.** On inventory, after the tool has spoken,
  three of the thirteen routed rows are real decisions: whether `isLoggedIn` is session or tab
  scope, and which `Reason` `stringConstantsMap` (writes only inside its own accessor) and
  `sessionFactory` (no writers, `<clinit>`-built) claim.

Pinned counts, re-run by hand on each round rather than asserted by a unit test (the testapps are
not reactor modules, so their `target/` is not there to depend on):

| | inventory stage 1 | crud | jlawyer-shape |
|---|---:|---:|---:|
| `static` fields / synthetic / enum constants | 73 / 12 / 7 | 7 / 1 / 3 | 22 / 5 / 7 |
| constants / **worklist** | 34 / **20** | 1 / **2** | 5 / **5** |
| worklist rows whose declared type is a component | 6 | 0 | 0 |
| `static` methods / touching an app static | 120 / 20 | 10 / 1 | 16 / 6 |

### `--diff` is the closing check

Once the migration has run, the same tool over the migrated tree gives every stage-1 row a fate:

```bash
bin/static-sweep <swing>/target/classes --diff <migrated>/target/classes --report target/diff.md
```

```markdown
| `Main.gui` | gone — no such field any more (routed, or its class was removed) |
| `SessionUtils.sessionFactory` | a verdict was recorded — `@IntentionallyStatic` (`JVM_INFRASTRUCTURE`) |
| `AppFrame.debug` | made a constant — `final` and of an immutable type; the gate passes it |
| `Holder.panels` | **still unvetted** — reaches the tree and nothing has been decided |

**New statics the migration introduced: 1.**
- `AppServlet.ERROR_ID` : AtomicInteger — `COUNTER`
```

That is [former-singletons.md's "checking you finished"](../guides/1-swing-to-emulators/former-singletons.md)
as a diff rather than as two counts you compare by eye.

### What it does not do

- **No verdicts.** Which `Reason` a row claims, whether a counter must stay contiguous, whether
  state is session- or tab-scoped: those need the app's intent. The report pre-fills every
  mechanical column of the review-gate table and stops.
- **No transitive reachability.** A `static` holder of an immutable-looking type whose *contents*
  reach a component is not chased; the type-level rule flags the holder and a human reads it. Nor
  are a record's components checked.
- **`static { }` only as a heuristic.** A field initializer and an explicit block compile to the
  same `<clinit>`, so a block is *inferred* from exception handlers in a `<clinit>` whose class owns
  a real `static` — it finds a block with a `try` and misses one without. The `putstatic` targets
  are exact either way, and those are the fields a block configures.
- **A raw `static Map` or a wildcard recovers no type arguments** — there is no `Signature`
  attribute to read. Both are of a mutable type, so the row is flagged anyway.
- **`--diff` matches by fully-qualified name.** A field whose class was renamed or moved shows as
  *gone* and again under the new statics; the report says so where it says it.
- **An array of components is reported here and passes the gate.** `static JPanel[]` is judged by
  its element type in this report, while `MigrationGuardrails`' component rule matches the raw type
  and so lets it through. Worth knowing when the two disagree.
- **`<clinit>` invokes are one level deep** through app-owned callees — enough to see a
  `SessionFactory` built in a helper, not a call graph.

---

## Phase 2 — `ImportSwap`

```bash
bin/import-swap src/main/java --dry-run --report target/import-swap.md
```

Read the report, then drop `--dry-run` to apply.

| option | effect |
|---|---|
| `--report <path>` | write the Markdown report to a file instead of stdout |
| `--dry-run` | report without editing a single file |
| `--table <tsv>` | feed a swap table directly, if you would rather not resolve the jars |

Point it at the **source root**, not the module root. It is **idempotent** (a second run changes
nothing) and **tolerant of a half-swapped tree**, so re-running after hand edits is safe.

### Example output

`--dry-run` over `testapps/crud/swing`, abbreviated:

```markdown
# Import-swap report

- files scanned: 7
- files rewritten: 5
- swap table: 158 rows from jar:file:/…/emulators-0.1-SNAPSHOT.jar!/META-INF/emul/ported-types.tsv (158 rows)
- files with declined residue: 0

## Rewritten

### `testapps/crud/swing/src/main/java/com/vaadin/swingbridge/testapps/crud/swing/EmployeeEditDialog.java`

- java.awt.Component -> vaadinx.awt.Component (1 in-code reference)
- javax.swing.JButton -> vaadinx.swing.JButton (import)
- javax.swing.JOptionPane -> vaadinx.swing.JOptionPane (import)
- java.awt.BorderLayout -> vaadinx.awt.BorderLayout (import)
…
```

On a wildcard-importing app the entries look different, because deleting an on-demand import means
re-emitting everything it supplied — including the stay-JDK types:

```markdown
### `testapps/inventory/swing/src/main/java/com/ca/ui/Main.java`

- java.awt.event.ComponentAdapter -> vaadinx.awt.event.ComponentAdapter (1 in-code reference)
- java.awt.Frame -> vaadinx.awt.Frame (1 in-code reference)
- deleted `import javax.swing.*;` — replaced by single-type imports
- deleted `import java.awt.*;` — replaced by single-type imports
- added `import java.awt.Dimension;`
- added `import vaadinx.awt.EventQueue;`
```

### The declined section is the point

The tool **declines rather than guesses**. An import it cannot parse, or a simple name that two
deleted wildcards both supply, leaves the whole file untouched with the reason in the report — as
does a type under an add-on's package root that no add-on on the classpath maps:

```markdown
- files with declined residue: 1

## Declined — triage these

### `com/acme/Form.java`

- no mapping for `com.jgoodies.forms.builder.PanelBuilder` — it is under an SB-Emulators add-on's package
  root, but no add-on on the classpath ships a counterpart for it. The add-on replaces its upstream
  rather than depending on it, so this import will not resolve; see the add-on's
  META-INF/emul/MIGRATION.md for what it does cover.
```

That is a real limitation reported *now* rather than a compile error found later — the add-on ships
no `builder` package, and no rewrite would have been right.

### What it does not do

Scope is what is decidable from the swap table plus a file's own imports. **Out on principle, not on
effort:** `System.exit` routing, the `static` sweep, `JFileChooser.showDialog` direction,
`FormerSingletons`. Those are the guide's, and yours. Two more limits worth stating:

- **No LSP, no AST, no compiling code.** Names resolve from the file's own imports plus
  `Class.forName`, with a whole-tree pre-scan for your app's own type names (so an
  `import java.awt.*` beside your own `com.acme.ui.Panel` does not get redirected).
- **One gap regex cannot close:** a simple name shadowed by a declaration the pre-scan cannot see —
  a local variable, or a member type inherited from a superclass (JLS 6.4.1). That is reported, not
  guessed at.

---

## Maintaining this module

```bash
./mvnw -C -pl migration-tool clean install
```

That also assembles the distribution zip (`src/main/assembly/dist.xml` over `src/main/dist/`) and
runs `DistZipIT`, which unzips it and drives the shipped launchers over `testapps/crud/swing` and
over this module's own class files. Both are in the **default** build: the zip is what the
distribution kit's `tools/` is unpacked from, and the scripts have nowhere else to be tested. A
Windows CI job runs the same IT so the `.cmd` twins are executed by something other than a reader.

> **This module compiles at `--release 24`, alone in the reactor.** `StaticSweep` reads class files
> with `java.lang.classfile` ([JEP 484](https://openjdk.org/jeps/484), final in JDK 24), and
> `javac --release 21` rejects the package outright. Nothing downstream notices — the jar is *run*,
> never compiled against, and JDK 24+ is already the project's floor for build and runtime. What
> *does* notice is an IDE: Eclipse/m2e refuses the module with **"release 24 is not found in the
> system"** unless its own JDT is running on a JDK 24+, and older JDT builds cannot compile the
> package at all. Maven on the command line is the authority here; if your IDE has been building
> into `target/`, `clean` before believing a test failure (CLAUDE.md § Building).

### Package layout

```
com.vaadin.swingbridge.migration.tool             only what MORE THAN ONE tool uses — Sources (the file walk), Reports
├── importswap                 ImportSwap, Swapper, SourceFile, SiblingIndex, PortedTypes, Report, Lexed
├── hazardscan                 HazardScan, Hazards, HazardReport
├── staticsweep                StaticSweep, ClassTree, StaticIndex, Resolver, SweepReport, Names
└── guardrails                 GuardrailEngine, ImmutableTypes — the rule engine, not a tool
```

**One package per tool; the root package is not a utilities drawer.** A class only one tool uses stays
in that tool's package however generic it looks — `Lexed`, a Java-source lexer that strips comments
and string literals, sits in `importswap` because only the swap needs it. Move a class up when a
second tool actually calls it, not when it starts to look reusable. `Sources` is `public` for exactly
that reason and no other; everything else here is package-private, and nothing in this jar is a
surface a migrator compiles against.

Both tables are guarded, and both guards are the reason the tables can be trusted without reading
them:

| table | guard | regenerate |
|---|---|---|
| `ported-types.tsv` (in `:emulators`) | `PortedTypesTableTest` re-derives it by reflection; a new emulator reddens the build | `./mvnw -C -pl emulators test -Dtest=PortedTypesTableTest -Demul.regenerate=true` |
| `hazards.tsv` (here) | `HazardTableTest` joins it against spec.md §7/§5 by `H_` slug, both directions | hand-edited — the test tells you which half you forgot |

Two more tests pin behaviour against real trees: `GradedTreeValidationTest` compares `ImportSwap`'s
output against all three committed stage-2 testapps by *resolved binding* rather than text, and
`HazardScanGoldenTest` pins the scan's hit **counts per section** over `testapps/inventory/swing`
(counts, so an unrelated testapp edit does not redden it; per section, so a pattern that quietly
widened does):

```bash
./mvnw -C -pl migration-tool test -Dtest=HazardScanGoldenTest -Demul.regenerate=true
```

Read that diff before committing it — a changed count is the signal, not a chore.
