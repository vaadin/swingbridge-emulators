# Developing SwingBridge Emulators

This is the **maintainer** entry point — for working on SwingBridge Emulators (**SB-Emulators** for short, as these docs call it) itself. If you're a **migrator** (porting a Swing app *with* SB-Emulators), start at [README.md](./README.md) instead.

Project rules, terminology, the migration arc, and the live scope live in [CLAUDE.md](./CLAUDE.md) — read it first.

## Build

Requires **JDK 24+**, and JDK 21 will not be supported while `callSwing` runs on
virtual threads: a virtual thread parking inside a `synchronized` region pins its
carrier pre-[JEP 491](https://openjdk.org/jeps/491), and that carrier is the request
thread holding the session lock, so any `synchronized` block above a modal call
deadlocks deterministically. The mechanism, the measurement, and why a backport is
infeasible are in [CLAUDE.md](./CLAUDE.md) (the "JDK 24+ is mandatory" paragraph).
CI runs the build on JDK 24 and 25.

```
./mvnw -C verify
```

**The testapps are not in the reactor.** Each stage directory under `testapps/` has a standalone
pom and its own `mvnw`, and is built from its own directory — the way a migrator builds their app,
resolving SB-Emulators by coordinate out of `~/.m2`. A plain `verify` therefore does not compile or
test them. `clean install` does, in place, once the reactor has installed (`-Dtestapps.skip` skips
it; keep the default — a stale testapp `target/` once passed for a library regression); `-Pkit`
additionally builds the *shipped* copies out of the assembled distribution kit, which is what CI
runs. See [D_distribution_zip](./emulators/decisions.md#D_distribution_zip) for
why reactor membership was the thing to sever.

### Run commands

- **Full build:** `./mvnw -C verify`; `./mvnw -C clean install` also rebuilds every testapp stage in
  place (~45 s more)
- **Full build the way CI runs it** (adds the kit's testapp builds and smoke test):
  `./mvnw -C install -Pkit`
- **Run an SB-Emulators-dependent app** (sampler — needs sibling libs in `~/.m2`):
  `./mvnw -C clean install -DskipTests && ./mvnw -C -pl sampler exec:exec`. The
  `clean install` prepends so the run uses fresh code — the `-pl` run doesn't
  rebuild SB-Emulators modules itself. Apps run via `exec:exec` (a **forked** JVM) rather
  than `exec:java`, because the blocking-dialog runner needs `--add-opens java.base/java.lang=ALL-UNNAMED`,
  a JVM *startup* flag that `exec:java` (running inside Maven's own JVM) can't apply.
- **Run a testapp**, either stage, from its own directory — the same verb before and after
  migration: `cd testapps/crud/swing && ./mvnw -C compile exec:exec`. A `1-emulators` stage needs
  `./mvnw -C clean install -DskipTests` in the repo root first, to put the matching SNAPSHOT in
  `~/.m2`. Each app's `README.md` carries its exact commands and credentials.
- **Assemble the distribution kit:** `./mvnw -C -pl zip-distro package` — writes
  `zip-distro/target/swingbridge-emulators-<version>-dist.zip` and, beside it, the same tree
  unpacked at `zip-distro/target/unzipped/swingbridge-emulators-<version>/`, which is what the
  guide-loop skills use as `SWINGBRIDGE_HOME`. **The kit now resolves reactor artifacts** — the tool's
  own `dist` zip plus the three swap-table jars — so `-pl zip-distro` alone needs a prior
  `install`; add `-am`, or just run `./mvnw -C clean install`. `verify` adds the layout, link,
  placeholder and shipped-launcher checks; `-Pkit` adds the shipped-testapp builds and nothing else.
- **Single-module test:** `./mvnw -C -pl surrogates test` (SB-Emulators-dependent modules need `clean install` first).
- **Generator** (pure reflection, no loom → plain `exec:java`):
  `./mvnw -C -pl generator compile exec:java -Dexec.arguments="javax.swing.JButton,emulators/src/main/java"`.
- **Production build:** `./mvnw -C -Pproduction -pl sampler -am package` (runs Vaadin `build-frontend`).
- **Releases go through Vaadin's release infrastructure, never by hand.** Nobody runs
  `deploy -Prelease` from a laptop: the infrastructure holds the GPG credentials and runs the
  build that publishes to Central. What that build *is* lives in this repository, so it is still
  ours to keep releasable. Two switches, deliberately different: the `release` **property**
  (`-Drelease`) is this pom's own — it takes the non-published modules `generator`, `sampler` and
  `zip-distro` (the parent pom's `default` profile) out of the reactor and turns on the
  `packaging` profile, which attaches the source and javadoc jars Central requires; the `release`
  **profile** (`-Prelease`) is `com.vaadin:vaadin-parent`'s,
  which signs and **auto-publishes** (a green deploy is the release on Central, with no manual
  confirm step and no undo). A javadoc error fails the release and an ordinary build runs no
  javadoc, so check the javadoc before handing a version over:
  `./mvnw -C clean package javadoc:jar -DskipTests -Ddoclint=all,-missing`. Run it from `clean`: the javadoc plugin
  re-jars its previous `apidocs/` when only a source file changed, so an incremental run can hide
  an error. The non-published modules' own POMs also no-op the source/javadoc/gpg/deploy plugins
  via skip properties. **Nine
  modules publish**: `migration-annotations`, `migration-guardrails`, `migration-tool`,
  `surrogates`, `emulators`, `emulators-printing`, `emulators-spring`, and **both
  third-party add-ons**.
  The add-ons publish because everything a migrated pom can be told to reference must be
  resolvable — the add-on `MIGRATION.md`s tell an app to depend on
  `com.vaadin.swingbridge:swingbridge-emulators-jcalendar-1.4`, and since the distribution kit ships no
  library jar to build against ([D_kit_tool_zip](./emulators/decisions.md#D_kit_tool_zip)) a
  coordinate that is not on Central has nothing to fall back on. `migration-tool` publishes **two
  artifacts**: its jar and the `dist`-classified distribution zip the kit's `tools/` is unpacked
  from, which is also the "I came by coordinate, not by kit" download. Both are attached by the
  module's normal `package`, so a release build needs no extra wiring.
- **The distribution kit is a GitHub Release asset, not a Central artifact.** Attach
  `zip-distro/target/swingbridge-emulators-<version>-dist.zip` to the release, with a
  `.sha256` beside it, and paste `zip-distro/target/placeholders.txt` into the release notes —
  it is the generated list of slots this release does not fill. **Only ever hand out a release
  build:** a SNAPSHOT kit resolves from the `~/.m2` of the machine that built it and is useless
  anywhere else.

### Measuring against the real thing

R_swing_is_truth means the JDK's behaviour decides, and repeatedly the JDK's actual behaviour has
turned out to differ from a careful reading of its source. So measure. Each of these traps has cost
real time, and each makes a probe report the **opposite** of the truth rather than simply failing:

- **A real-JDK Swing probe needs the display handed to it explicitly.** The shell inherits neither
  `DISPLAY` nor `XAUTHORITY`, so `new Frame()` throws `HeadlessException` and a headless run measures
  nothing at all. Pass both: `DISPLAY=:0 XAUTHORITY=/run/user/1000/.mutter-Xwaylandauth.*` (the
  cookie's name has a random suffix — glob it).
- **AWT's event queue is asynchronous, so drain it before asserting.** A probe that posts a window
  event and reads its listener immediately sees nothing *in both branches*, and so "proves" the JDK
  never fires the event. `EventQueue.invokeAndWait(() -> {})` first. This is what the
  `WINDOW_CLOSED` gate in [D_window_registry](./emulators/decisions.md#D_window_registry) turned on.
- **In a live browser, sample no earlier than ~400ms after opening a dialog.** Vaadin's open
  animation runs under `transform: scale(0.95)` with the overlay still `position: relative`, so the
  first sample reports the animation, not the window — measured 232 × 70 for a 360 × 110 splash,
  which reads exactly like a sizing bug.
- **Drive a click and its measurement from one in-browser script, not two tool calls.** Several
  Sampler demos self-dispose on a `Timer` (1.25s, 2.5s) — shorter than a click→screenshot round-trip,
  so the window is gone before the second call runs.
- **Karibu sees no CSS.** Anything whose claim is a computed style, a rendered rect, or a shadow-part
  selector matching has to be measured in a real browser; a green suite says nothing about it. Four
  `::part()` rules in `swindow.css` had silently stopped matching for a whole Vaadin minor.
- **Reproduce against a fresh `clean install` before believing an isolated failure.** An isolated
  `-pl` run reporting a failure the full suite doesn't have is usually a stale `~/.m2` artifact, not
  a bug in your work — the "debug fiction" the [§Build](#build) rule exists to prevent.

### Standing audits

Two tools diff `:emulators` against a real JDK on the return-value axis, per
[D_return_value_audit](./emulators/decisions.md#D_return_value_audit). Both mains exit 0 whenever
they ran — their output is a worklist to triage, not a verdict:

- **Return-value differ** (dynamic). The **gate** is `vaadinx.audit.JdkReturnValueAuditTest`, which
  runs in the ordinary build and fails on a divergence nobody has written a reason for. To make one
  legal, add a row to `emulators/src/test/resources/vaadinx/audit/sanctioned-divergences.tsv`
  naming the rule that permits it — a row is a claim, not a mute button.

  **Run the wider sweep by hand from time to time — the gate is sound but not complete.**
  `./mvnw -C -pl emulators test-compile && xvfb-run -a ./mvnw -C -pl emulators exec:exec`, report
  at `emulators/target/jdk-return-value-audit.md`. A display buys ~150 more comparisons, because
  headless the JDK oracle will not construct `Frame` / `JDialog` / the AWT widgets at all and
  their pairs drop out — so this run can surface findings `JdkReturnValueAuditTest` structurally
  cannot, and currently does. The gate is still trustworthy on what it does reach: an oracle that
  answers `HeadlessException` is skipped rather than reported, so it never reddens over the
  display's absence.
- **State-drop sweep** (static, bytecode, no display, report-only):
  `./mvnw -C -pl emulators exec:exec -Daudit.main=vaadinx.audit.StateDropSweep`.

### Build gotchas

- **`junit-bom` must be imported before `vaadin-bom`** in the parent's
  `<dependencyManagement>` (first-declared wins). Vaadin's `vaadin-bom` pins
  `junit-jupiter-api` to an older 6.0.x but leaves `junit-jupiter-engine`
  unmanaged, so the JUnit set splits (api 6.0.x / engine 6.1.x) and Surefire's
  strict-alignment check fails with `NoClassDefFoundError … not properly aligned`.
  junit-bom first → the whole set aligns.
- **Karibu browserless tests do not need `prepare-frontend`** — all suites pass
  without it; only the Vaadin apps run it (in the default lifecycle).
- **Vaadin bundles (`src/main/bundles/`) are git-ignored, not committed.**
  `vaadin-rich-text-editor-flow` (commercial, backs `JEditorPane`) is absent from
  Vaadin's precompiled default bundle, so any *production* build rebuilds a custom
  `prod.bundle`. SB-Emulators ships no production artifact and CI runs dev-mode `verify`
  (never builds it), so committing the ~1MB blob would only add version-bump churn.

## Subprojects

- [`:emulators`](./emulators/README.md) — the library migrators compile against (pure Java, `src/main/java`). Reproduces the Swing API surface faithfully on Vaadin peers (has-a). Tests use [Karibu-Testing](https://github.com/mvysny/karibu-testing), in Java under `src/test/java` (R_java_karibu_tests). **Stage-2 API.**
- [`:surrogates`](./surrogates/README.md) — Vaadin-component subclasses (is-a) that reproduce full Swing functionality for individual components, used by `:emulators` as peers once a given surrogate lands. Evolves in concert with `:emulators` per [D_surrogate_first](./emulators/decisions.md#D_surrogate_first); new emulator components wait for their surrogate to land here first, and the surrogate must be fully implemented within R_vaadin_first's capability cap before the emulator thins down. See [D_emulator_surrogate_split](./emulators/decisions.md#D_emulator_surrogate_split) for the original split rationale. **Stage-3 API.**
- [`:sampler`](./sampler/README.md) — internal Vaadin-Boot app that demos every emulated component in a browser. Per-route `WarnInventoryTest`s assert zero stub WARNs along the user path — the exit gate. Sampler is a validation testbed, **not** a typical Swing app and not a support contract; see [sampler/description.md](./sampler/description.md).
- [`:testapps:crud`](./testapps/crud/README.md) — small employee-CRUD reference app written across migration-arc stages: [`swing/`](./testapps/crud/swing/) (stage 1, pure Swing) and [`1-emulators/`](./testapps/crud/1-emulators/) (stage 2, post-import-swap on `:emulators` — a CRUD worked example). The stage-3 `surrogated/` sibling is not landed yet.
- `:generator` — reflective stub generator. It reflects over a whitelisted JDK class and emits a `vaadinx.*` skeleton whose methods forward to `vaadinx.EHelper.onUnimplemented`. It lives outside `:emulators` so it still runs when `:emulators` won't compile (the usual state when expanding scope). Generated output is a starting point, not the finished class:

  ```
  ./mvnw -C -pl generator compile exec:java -Dexec.arguments="javax.swing.JButton,emulators/src/main/java"
  ```

## Licensing

A module's default licence is **Apache-2.0**, declared once in `swingbridge-emulators-parent`'s `<licenses>`
block (with [`LICENSE-APACHE-2.0`](./LICENSE-APACHE-2.0) shipped into every jar by its
`<resources>` block) and inherited by every module that does not override it. **Four override
it**: `:emulators` and `:emulators-printing` are **GPLv2 + Classpath Exception** — the same licence
as OpenJDK, whose derivative work they are ([`LICENSE`](./LICENSE) at the repo root is OpenJDK's
own file, verbatim from tag `jdk-25-ga`) — and the two add-ons take their upstream library's. So a
new module needs no licence block of its own. Adding one *replaces* the parent's rather than
merging with it, so declare one only when the module genuinely differs — and know *why* it differs
before you do.

**The lanes**, mapped tree-by-tree in [`PROVENANCE.md` § Lanes](./PROVENANCE.md#lanes), which is the
authoritative copy:

| path | licence |
|---|---|
| `emulators/src/`, `emulators-printing/src/` | **GPLv2+CE** ([`LICENSE`](./LICENSE)) |
| `:surrogates`, `:emulators-spring`, `:sampler`, `:migration-annotations`, `:migration-guardrails`, `:migration-tool`, `:zip-distro`, `:generator`, `testapps/crud` | **Apache-2.0** ([`LICENSE-APACHE-2.0`](./LICENSE-APACHE-2.0)) |
| the docs (root `*.md`, the emulator modules' own READMEs, `architecture.md` and decision log, `guides/`, `migration/`, `ideas/`), `.claude/skills/`, `.github/`, the poms — and anything the map does not name | **Apache-2.0** |
| `guides/1-swing-to-emulators/seed/` — the host-app seeds a migrator copies into their own app | **0BSD** ([`seed/LICENSE`](./guides/1-swing-to-emulators/seed/LICENSE)) |
| `third-party/jcalendar-1.4` | **LGPL-2.1** |
| `third-party/jgoodies-forms-1.2.1`, `testapps/inventory`, `testapps/jlawyer-shape` | upstream's own |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`, wherever they sit | the ASF's Apache-2.0, header kept |

Third-party text quoted in an Apache-2.0 doc — JDK source and javadoc in the decision logs, Oracle's
notice in `PROVENANCE.md` — keeps its own terms; each decision log says so at its head.

**Why the emulator core is on that licence and not a permissive one:** it is a derivative work
of OpenJDK. It reproduces the `javax.swing` / `java.awt` API, and it was written with OpenJDK's
source open as the specification — that is the *method*, written down as `R_swing_is_truth` and
`R_decline_effect_only`, not something that happened by accident. So it is **not clean-room, must
never be described as clean-room**, and takes the upstream terms rather than choosing its own. The
full argument and the alternatives rejected:
[D_gplv2_ce_relicense](./emulators/decisions.md#D_gplv2_ce_relicense), including the
[similarity measurement](./emulators/decisions.md#D_gplv2_ce_measurement) behind it.

**Why most modules are not on it:** the licence follows the derivation, and outside the two emulator
modules there is none to follow — `:surrogates` had eleven JDK-derived files and each was rewritten
until it had none; the rest never had any, and each was measured before it moved. What made the move possible is that the
boundary a consumer can see is the **jar**: the pom and the headers have to agree, which is why
each module's move was a full header sweep rather than a line in a pom, and why the lane a new
module inherits is gated on what it contains — `LicenseHeaderTest` demands the Apache header and
`JdkSimilarityTest` measures it from its first build.
[D_licence_lanes](./emulators/decisions.md#D_licence_lanes) has the composition argument (an
Apache-2.0 module either side of the GPL core is fine because it links rather than derives) and the
per-module evidence; [D_similarity_gate](./emulators/decisions.md#D_similarity_gate) is the build
check (`JdkSimilarityTest`) that keeps the lane free of JDK expression once a module is on it.

**The test is expression, not coupling**, and that is the first thing to establish about a
candidate. Two questions decide it: does the tree carry **JDK expression** (an Oracle notice owed —
what `:surrogates`' eleven files had to clear), and does it carry **emulator source** copied in,
forked, or shipped as a modified copy? A module answering no to both may be permissive whichever way
its dependencies run, because linking in either direction is what the Classpath Exception permits.
How *heavily* a module uses the API does not enter into it: `:sampler` has 433 `vaadinx` imports and
50 classes extending emulator types, carries not one line of emulator expression, and **is on the
permissive lane**. An earlier, stricter rule here said "never subclass an emulator type" and was
retracted — it would have made every migrated app derived too, since they all extend
`vaadinx.swing.JFrame`.

**What this means day to day** — the rule is
[`R_gpl_provenance`](./CLAUDE.md#R_gpl_provenance), the specification is
[`PROVENANCE.md`](./PROVENANCE.md) § Headers, and the gate is
`emulators/src/test/java/vaadinx/LicenseHeaderTest.java`:

- **Every `.java` file we author opens with its lane's header**, and there are five.
  **`HDR_jdk_derived`** — the JDK counterpart's own Oracle notice byte-for-byte, then Vaadin's
  derived-and-modified block — goes on a file whose class maps onto a JDK class, or into which a JDK
  method body was ported. **`HDR_vaadin_gpl`** — Vaadin's GPLv2+CE notice with the Classpath
  exception inline — on everything else in the GPL lane (`emulators/`, `emulators-printing/`).
  **`HDR_vaadin_lgpl`** under `third-party/jcalendar-1.4`, **`HDR_vaadin_0bsd`** under
  `guides/1-swing-to-emulators/seed/` (an unconditional grant, since those files become the
  migrator's own code), and **`HDR_vaadin_apache`** on every other tree we author. Do not hand-write
  any of them; copy from `PROVENANCE.md` § Headers, or from a sibling file in the same module —
  never from another module, which is how a header ends up in the wrong lane.
- **The Oracle block is copied per file, never templated.** Its years differ from file to file (42
  distinct pairs across the 160 today), and `vaadinx.LicenseHeaderTest` rule 4 diffs each block
  against the counterpart in this JDK's `lib/src.zip`. The `:generator` reads the block itself, so a
  freshly generated emulator arrives with the right header.
- **Editing a file in a later year bumps its Vaadin end year** (`Copyright 2000-YYYY Vaadin Ltd.`) —
  that year is GPLv2 §2(a)'s "date of change", and the line is byte-identical to `vaadin/flow`'s, so
  the company-wide annual sweep covers the rest.
- **Never paste third-party code into one of our files.** Linking is exactly what the Classpath
  Exception is for; copying Apache-2.0 / BSD / EPL / commercial text into a GPLv2 file is a different
  act, and a dependency's licence does not authorise it. Vaadin-owned code is the exception — the
  copyright holder may relicense its own work — but note it in a comment.

Two trees are not ours to license at all, and each says so in its own `PROVENANCE.md` — distinct
from the Apache lane above, which *is* ours and is simply on other terms:

- **`third-party/` add-ons carry the licence of the upstream library whose API they reproduce**,
  fork or reimplementation alike
  ([M1D_addon_upstream_licence](./migration/1-swing-to-emulators/decisions.md#M1D_addon_upstream_licence)).
  A licence boundary that isn't a module boundary is not a boundary — hence one module per library
  ([M1D_addon_packaging](./migration/1-swing-to-emulators/decisions.md#M1D_addon_packaging)). Today:
  `swingbridge-emulators-jgoodies-forms-1.2.1` is **BSD** (a fork) and `swingbridge-emulators-jcalendar-1.4` is
  **LGPL-2.1** (a reimplementation that takes upstream's terms anyway). Each overrides the parent's
  `<licenses>` block and ships **its own** `LICENSE` into its jar's `META-INF/` — not the repo
  root's, which would state the wrong terms inside the jar. The fork-vs-reimplementation
  distinction still decides whose copyright notice sits on which *file*; it no longer decides the
  licence. Why the rule changed: a reimplementation's cleanliness is a function of how little was
  ported, so the old "the licence question is dissolved" route was deciding at the moment the
  analysis was most favourable — and LGPL-2.1 §3 does not authorise the GPLv2+CE it was inheriting,
  since the Classpath Exception grants a linking permission over code that isn't ours.
- **`testapps/`** holds *adopted* third-party apps whose terms are the upstream author's, not ours —
  `testapps/inventory/` runs on an informal credit-required grant captured verbatim in its
  `PROVENANCE.md`. The root `LICENSE` does not relicense them; honour the grant recorded there
  before redistributing anything from that tree — the distribution kit does, by shipping that
  `PROVENANCE.md` beside the sources and their `@author` headers intact
  ([D_kit_what_ships](./emulators/decisions.md#D_kit_what_ships)). Their headers are left exactly as upstream wrote
  them — **including the files that have none**, since adding a Vaadin header to a file Vaadin did
  not author is a false claim. Their poms are standalone and restore only Maven's default resource
  directory, so no licence of ours travels inside their jars. `testapps/crud/` is ours and follows the ordinary
  rule.

## Documentation map

- [CLAUDE.md](./CLAUDE.md) — project rules (the `R_*` hard rules), terminology, personas, migration arc, current scope.
- [emulators/architecture.md](./emulators/architecture.md) — package layout, peer field, setter pattern, layouts, threading, main-window route.
- [emulators/decisions.md](./emulators/decisions.md) — `:emulators` decision log (`D_import_swap_target` … `D_toolkit_full_surface`). ADR-lite; IDs are stable and never reused.
- [surrogates/architecture.md](./surrogates/architecture.md) — handoff contract with `:emulators`, mixin mechanism, state-holder table.
- [surrogates/decisions.md](./surrogates/decisions.md) — `:surrogates` decision log (`SD_mixin_mechanism` … `SD_sjtree`).
- [sampler/description.md](./sampler/description.md) — Sampler's full role definition + exit-gate inventory.
- [vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs) — the blocking-dialog library `EHelper.callSwing` runs on ([D_blocking_dialogs_library](./emulators/decisions.md#D_blocking_dialogs_library)), and the `@Push` requirement.
