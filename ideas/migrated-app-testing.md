# Integration-testing a migrated app: capture the scenarios off the desktop, replay them in the browser

**Status:** proposal, partially brainstormed (the *where it runs* question is settled below; the
*what a scenario is* question is not). Opened 2026-09-10 when the stage-2 guide review found
`host-app-vaadin-boot.md` (then `vaadin-boot-scaffold.md`) teaching Karibu-Testing to migrators — a test stack inherited from a
scaffold-a-new-app skill, in a document that otherwise never mentioned testing. The maintainer's call:
**strip testing from the guides rather than half-answer it**, and treat the answer as its own piece of
work. Absorbed the earlier `generated-karibu-verification.md` sketch (authored 2026-05-06, itself
extracted from the retired `migration/1-swing-to-emulators/ideas.md`) on 2026-09-10; its title
predated both the Vaadin 25.1 licence change and the Browserless Testing rename, and its one decision
is re-decided in § "Decided" below.

**Graduation target:** `guides/testing.md` — note the path, deliberately *not* under
`guides/1-swing-to-emulators/`. This is not a phase of the import swap; it is what you do once the app
runs, and it stays useful at stage 3 when `vaadinx` is gone. The stage-2 guide mentions it once, at the
end, as an **optional recommended follow-up**.

**Not in scope: unit testing, and not the guardrails either.** The
[migration guardrails](../guides/1-swing-to-emulators/guide.md#S_guardrails) are a build-time gate that
stays exactly where it is — three ArchUnit rules that keep the `static` sweep done. They happen to run
under JUnit, which is why the example build files carry JUnit and surefire; that is wiring, not a
testing story, and nothing here changes it.

## The proposal

**One question is worth answering: does view X still do what it did on the desktop?** The migrator's
own suite cannot answer it — a Swing app's tests are either model/service tests that never touched
Swing (they pass unchanged and say nothing about the port) or UI tests driving Swing components on an
EDT that no longer exists. Hand-written tests against the migrated app cannot answer it either,
because the expectation would be written *after* the migration, from the migrated code, by whoever
just migrated it.

So derive the expectation from the **original**:

1. **Capture** a set of scenarios off the running *desktop* app, before or during the migration, via
   [vaadin/swing-mcp](https://github.com/vaadin/swing-mcp) — which already drives a real Swing app
   and can observe it. A scenario is a journey: open this view, type this, click that, see this.
2. **Review the captured scenarios by hand** — load-bearing, not optional polish. Trusted as
   captured, the suite verifies "the migration preserved what swing-mcp happened to record", not "the
   migration preserved Swing behaviour". The reviewer adds the journeys the capture missed, prunes
   noise, and signs the set off as a faithful behavioural spec. (This is also where § "What the
   eventual doc has to settle"'s first question — what a scenario *is* — gets answered in practice.)
3. **Generate** test files that replay each scenario against the *migrated* app.
4. **Run** them as an ordinary part of the migrated app's build.

That is the whole shape, and it is what makes this worth doing rather than just recommending a
framework: the expectations come from the app that already worked.

## Decided (2026-09-10): optional for every migration, developed on our testapps

Two claims that used to travel together, and only one of them survives.

**Manual testing stays the bar.** A stage-2 migration — a migrator's, or one of our testapp round
rounds — is *done* when the app compiles, starts, and its WARN inventory is triaged. The pipeline
never becomes a condition for that. This is one rule with two consequences: the stage-2 guide ends at
a running app and offers the pipeline as a recommended follow-up (§ "Constraints" below, *it must not
become a phase*), and **the internal guide-loop protocol keeps its current scenario intact** — "migrate
pure, no testing overhead" is what `/guide-migrateapp` does today and keeps doing. Once the pipeline exists
the protocol **grows a second scenario**, "migrate, then carry on into the tests", layered on top
of the first rather than replacing it; the two are run and reported separately, so a stumble in the
testing half never muddies the migration half's STUMBLES.md.

**"Real-migration-only, never on our own testapps" is withdrawn.** The 2026-07-06 version of this
decision (recorded when the jlawyer-shape stage-2 walkthrough closed, and cited from TODO.md's
reconciliation note) said the pipeline only becomes a live question on a *real* migrated app and is
weighed per target against the agent time it costs. That reasoning assumed a one-shot migration; it
also made the pipeline undevelopable, because capture-and-replay needs the desktop original and the
migrated target *side by side, under our control*, and `testapps/<app>/swing/` next to
`testapps/<app>/1-emulators/` is the only place we have that pair — a migrator has that pair,
never us. So the pipeline is **developed and exercised on the testapps**, `inventory`
being the one large enough (~64 files, sixty-odd UI classes) to say anything about affordability;
`crud` proves the mechanics only. Three things follow for the testapp lane:

- **Capture is easy for us and hard for a migrator, so the testapp lane tests the mechanics, not
  the load-bearing question.** A `swing/` stage is always runnable on Xvfb with the seeded fixture the
  adopt skill already set up; a migrator's desktop app is only certainly runnable *before* their
  migration starts. § "What the eventual doc has to settle" keeps *when capture happens* as the
  question the testapps cannot answer.
- **A checked-in generated suite on a testapp is a regression net over the emulators themselves.**
  `-Pkit` already builds the shipped testapp stages in CI, so captured desktop behaviour of a real
  app would run against every emulator change. That is a second reason to want it on the testapps,
  and it answers "does generated mean checked in" for *our* lane: yes.
- **`swing/` stays pristine.** Capture reads the desktop app and must not instrument its source
  (`testapps/CLAUDE.md`). Attaching via agent or classpath is fine; a hook that has to be compiled
  into the app is an invariant violation, not a migration convenience.

The local-LLM stage-2 target's "test generation is out of scope" line
([local-llm-migration-target.md § "Out of scope"](./local-llm-migration-target.md#out-of-scope)) is
unaffected: optional stays optional, and the pipeline is cloud-level work either way.

## Which framework the generated tests target

**Recommend Vaadin's own Browserless Testing, not Karibu-Testing.** Three reasons, and the second is
the one that changed recently:

- **It is the official way to test a Vaadin app**, so a migrator ends the migration on the same path
  every other Vaadin team is on — which matters more here than usual, since the whole point of the
  arc is that they become an ordinary Vaadin app.
- **It is free for all users as of Vaadin 25.1.** It previously required a commercial TestBench
  subscription, which is the historical reason a community alternative was the pragmatic pick. That
  reason is gone.
- **It was called UI Unit Testing** and was renamed in Vaadin 25 — worth knowing, because the older
  name is what most search results and most of our own conversations still use.

The concrete shape for a migrated Vaadin-Boot app, which is the non-Spring case:

```xml
<dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>browserless-test-junit6</artifactId>
    <scope>test</scope>
</dependency>
```

Extend `BrowserlessTest` (no `@SpringBootTest`, no other test-framework dependency needed), and
`navigate(MyView.class)` gets you the view. **Its component-query API is the part that matters for a
migrated app**: a ported Swing view keeps its widgets in private fields of a `JFrame` subclass, often
unreferenced from anywhere a test can reach, and querying the component tree is exactly the escape
hatch that shape needs.

Two neighbours, for the record. **TestBench end-to-end** still requires a commercial subscription and
is the right lane for a handful of golden paths through a real browser, not for per-view coverage.
**Karibu-Testing** is the community ancestor of the browserless approach and what SB-Emulators' own
suite uses internally; that is a maintainer choice about *this* repo and not a recommendation to
migrators. If it comes back into a migrator-facing story at all, it will be because a generated suite
needed something browserless testing cannot express.

## What the eventual doc has to settle

- **What a captured scenario is, concretely.** A click script? A sequence of assertions about visible
  state? The answer decides whether generation is mechanical or an LLM job, and whether a scenario
  survives a view being restyled.
- **What happens when the emulators are legitimately different.** `R_layouts_close_enough` and the
  WARN umbrella say outright that some divergence is intended — pixel geometry, fonts, a dropped
  visual flag. A replay that fails on divergence-by-design produces suites migrators delete. The
  capture has to record *behaviour*, not appearance, or the comparison has to know what to ignore.
- **Whether the tests target emulator or peer types.** A test written against `vaadinx.swing.JButton`
  has to be rewritten at stage 3; one written against the Vaadin peer survives but reaches through a
  seam stage 2 tells the migrator not to think about
  ([the emulator/surrogate split](../emulators/decisions.md#D_emulator_surrogate_split)). Browserless
  testing locates *Vaadin* components, which may settle this by construction — worth checking early,
  because it is the difference between a suite that survives the arc and one that does not.
- **When capture happens.** Before the migration is when the desktop app still runs, and is also when
  the migrator has least appetite for extra steps. A capture step nobody performs makes the whole
  proposal moot, so this may be the load-bearing question rather than a detail.
- **Affordability at scale.** The inventory testapp is ~64 files and sixty-odd UI classes. Whatever
  this proposes has to be worth doing on that, not on `crud` — and § "Decided" makes `inventory` the
  place it is measured.
- **Whether "generated" means checked in — for the migrator.** A generated suite the migrator then
  hand-edits is a one-shot scaffold; one they regenerate is a fixture that has to stay stable across
  a restyle. (For our testapps § "Decided" already answers yes.)
- **What the second round scenario reports.** The pure-migration scenario writes STUMBLES.md against
  the guide; the migrate-then-test scenario needs its own report shape so a testing stumble is filed
  against `guides/testing.md`, never against the stage-2 guide it did not exercise.

## Constraints any answer inherits

- **The test JVM needs `--add-opens java.base/java.lang=ALL-UNNAMED`** and the virtual-thread-aware
  session lock, or anything driving a modal dialog deadlocks in tests exactly as it would in
  production. Both are already in `example-pom.xml` / `example-build.gradle.kts` and explained in
  `guides/1-swing-to-emulators/build-wiring.md`, so this wiring is already paid for.
- **JUnit 6 is Vaadin 25's default**, and the `junit-bom`-before-`vaadin-bom` import ordering is a
  real trap the example poms already handle. A JUnit 4 suite needs the Vintage engine.
- **`guides/testing.md` will be migrator-facing**, so the link-scope and present-tense rules apply
  (`GuideLinkScopeTest`; `migration/1-swing-to-emulators/spec.md`). If it grows `- [ ]` steps they
  need `S_` slugs — worth deciding first whether it is a procedure at all or a discussion.
- **It must not become a phase.** The stage-2 guide's value is that it ends at a running app.

## Related

- [inventory-testapp-migration.md](./inventory-testapp-migration.md) — its *journey gate* is this
  pipeline applied to `inventory`, and its repeated-rounds argument is what first re-opened the
  real-migration-only decision § "Decided" now withdraws.
- [local-llm-migration-target.md](./local-llm-migration-target.md) — keeps test generation out of the
  local-LLM target; consistent with § "Decided".
- [vaadin/swing-mcp](https://github.com/vaadin/swing-mcp) — the capture half, and a sibling
  SwingBridge feature rather than a third-party tool.
- `migration/1-swing-to-emulators/testing.md` — confusingly named and *not* this: that file is the
  guide-loop protocol for testing **the docs**. Worth renaming if this proposal lands.
