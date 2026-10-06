# Testing the migration docs

How we stress-test the `guides/1-swing-to-emulators/` tree by simulating an actual migration and capturing where the docs fail the user.

**Two rules bound what a fix may do to `guides/`**, and both exist because in-place patching is right
for facts and only ever additive for structure and for narration:

- **Steps vs references.** `guide.md` holds steps; a fix that is a paragraph of rationale, a build-tool
  corner, a table of cases or a hand-rewrite path belongs in the reference that step links.
- **Present tense only.** A stumble is a story about something that went wrong; the fix is the
  corrected present-tense statement, never the story. No "this used to throw", no "the default
  changed", no landed-in dates. The lesson goes to [`spec.md`](./spec.md) (the durable *what*) or
  [`decisions.md`](./decisions.md) (the *why*, including what it superseded) — both maintainer-facing,
  which is where a lesson about the guides belongs.

`/guide-docfix` carries the present-tense rule inline, since that is where it is applied; the
steps-vs-references rule's only home is `CLAUDE.md`, which every session that could edit a guide
already has loaded.

## Approach

Two skills form a tight loop:

1. **`/guide-migrateapp crud`** — migrates `testapps/crud` **through the distribution kit**, running the
   kit's own `/migrate-testapp crud` the way a customer who unzipped a download would. Fallbacks to
   project-internal source are allowed but logged as stumble-list entries.
2. **`/guide-docfix crud`** — reads the stumble-list, proposes edits to `guides/...`, and back-ports content-level fixes here (`spec.md`, `decisions.md`).

Skills live at `.claude/skills/guide-migrateapp/` and `.claude/skills/guide-docfix/`. The round
half is layered: **`/build-kit`** (rebuild the reactor, leave the kit unpacked) → **`/guide-migrateapp <app>`**
(stage the copy, drive the kit's own `/migrate-testapp` from a Claude Code session *rooted at the
kit*, copy back, report), which reads **`guide-migrateapp/<app>.md`** for nothing but that app's residue:
what to deny, what to restore after the copy-back, what to report in addition. Anything general
enough for two apps belongs in the skill; anything a customer should know belongs in the guides, not
in any skill.

**The round is a residue over the kit, not a harness of its own** ([D_guide_round_residue](../../emulators/decisions.md#D_guide_round_residue)).
It builds the kit, runs the kit's own command, and then does two things the customer does not: copies
the finished tree back over `testapps/<app>/1-emulators/` as the committed baseline, and adds a short
deny-list, since the shipped answer keys are what the probe measures. It unzips the kit into a
**temp folder outside any git repository** and drives it from a session opened *in* that folder
rather than from this repository's: the session that types the command is the session that migrates
([D_kit_no_subagent](../../emulators/decisions.md#D_kit_no_subagent)), so it must not be one holding
this repository's context — and outside a repository nothing reaches up into the emulator sources or
the decision logs, and no `.gitignore` of ours applies to the kit's own files
([M1D_lsp_recommended](./decisions.md#M1D_lsp_recommended)). **Everything else the kit ships is in the agent's
scope** — its `CLAUDE.md`, its README, every guide — because a customer sees all of it, and the read
scope below is what that replaced. The whole point is that there is no longer a maintainer prompt and
a customer prompt to keep from drifting: `guides/1-swing-to-emulators/agent-prompt.md` is the single
source. **Any delta a run needs beyond that residue is a finding against the kit**, to report rather
than to encode in the skill.

**One skill pair, one residue file per testapp.** The protocol below is written against `crud`; each
further testbed runs through the same two skills, differing only in what it measures and in the
`<app>.md` residue files beside them:

| app | target | what it probes that the others don't |
|---|---|---|
| `crud` | `testapps/crud` | the CRUD baseline surface — the reference run this document describes |
| `jlawyer-shape` | `testapps/jlawyer-shape` | a larger hand-built surface (JTree / JList / JSplitPane / JTabbedPane / JPopupMenu / JFileChooser / JToolBar / JEditorPane / JProgressBar + clipboard + drag-and-drop) |
| `inventory` | `testapps/inventory` | an **adopted** third-party app: a Hibernate/H2 backend, ~64 files nobody here wrote, and two third-party Swing libraries the migrator must route through [`addons.md`](../../guides/1-swing-to-emulators/addons.md) unaided |

The hand-built apps measure component coverage; the adopted one measures migration realism (see
[`testapps/CLAUDE.md`](../../testapps/CLAUDE.md) § "Two kinds of testapp"). What is specific to the
adopted app lives in its residue files, not here: `guide-migrateapp/inventory.md` holds its deny and why
its known bugs stay hidden, and `guide-docfix/inventory.md` adds the owners its findings reach beyond
core's — an add-on's own `third-party/*/MIGRATION.md` per M1D_addon_migration_docs, the `addons.md`
index, and the app's `PROVENANCE.md`.

## Why a fresh-user simulation

The naïve approach — "ask Claude to migrate the app" — silently compensates for doc gaps with project-internal knowledge (CLAUDE.md, `:emulators` source, prior conversation context). The whole *value* of the round is what gets surfaced when that compensation is forbidden. So the round runs in a subagent with a strict allow-list and a `[reference-needed]` escape hatch that *logs* every fallback rather than letting it slip past.

If the agent finishes the migration without needing `[reference-needed]`, the docs are working. If it logs ten such reads, each one is a candidate doc improvement — possibly inline content, possibly a clearer pointer, possibly nothing (some fallbacks are reasonable IDE-territory).

## Read scope

**The kit defines it now, and that is the point.** The agent works from
`SWINGBRIDGE_HOME` — the unpacked kit — and everything in there is fair game, because a customer sees
all of it: the guides, the kit's own `CLAUDE.md`, its welcome README, each add-on's `MIGRATION.md`,
and the tools it is told to *run* rather than read. What stays out of scope is what a customer does
not have: this repository's `CLAUDE.md` files, the decision logs, the GitHub issues, the sibling
docs in this directory (`spec.md`, `decisions.md`, this file), `emulators/src`, `surrogates/src`,
`loom/src`, `sampler/` and `third-party/*/src`.

**Plus a deny-list of two kinds, and only two.** The kit's shipped answer keys
(`testapps/crud/1-emulators/`), because those are exactly what the probe measures; and, for the
adopted app, its `PROVENANCE.md`, because most of what that file records *is* the measurement. A
customer is welcome to both.

## Stumble-list

Written in the run's own work folder and copied to `testapps/crud/1-emulators/STUMBLES.md` by the
harness residue (ephemeral — replaced by the next round). One entry per confusion, tagged by
severity:

| Tag | Meaning |
| :-- | :------ |
| `[error]` | Docs say something untrue. |
| `[doc-contradiction]` | Two parts of the docs disagree. |
| `[missing]` | The docs did not cover it; the agent had to look elsewhere or invent. |
| `[guess]` | Docs ambiguous; agent picked a plausible reading. |

**The schema is the kit's, in [`agent-prompt.md`](../../guides/1-swing-to-emulators/agent-prompt.md)** —
so the file a customer would send back as feedback and the file this loop consumes are one file. That
is why it survived becoming customer-facing: a migration log of what the guides left unclear is
useful to the person writing it too, not just to us. (`[missing]` is what the older
`[reference-needed]` tag became when "leaving the allow-list" stopped being the framing.)

## Backport heuristic (docfix)

For every approved fix:
- **Always** edit the user-facing target under `guides/...`.
- **Also** edit `migration/1-swing-to-emulators/spec.md` when the fix changes *what* the docs say — a coverage claim, a fact about emulator behavior, a recommended approach, an unrecorded hazard.
- **Edit `decisions.md`** when the stumble reveals a design call that wasn't documented.
- **Skip the backport** when the fix changes *how* the docs say it — ordering, phrasing, examples, missing cross-link, restructured section. The spec doesn't care about prose-level edits.

## Done condition for round

**The kit's, not ours: the migrated app compiles, starts and survives a couple of clicks.** Its build
passes, launching it prints Vaadin Boot's banner with a port, and the agent clicks through two or
three main flows — one of them a modal dialog — without a server-side exception, then hands the URL
to a human. Stopping at "it compiles" is explicitly not enough — the Vaadin wiring the guide's Phase
4 describes (app shell, servlet, init listener) fails at *start-up*, not at compile, so an app that
only compiles has not been migrated. Nor is "it starts": a missing `@Push` or a wrong
`main()`/`mainUI()` split fails on the first modal dialog, not in the boot banner — the 2026-09-10
crud round is what added the click-through, after an agent read the earlier "hand the URL to the
human" as a ban on opening a browser at all and left Phases 4 and 6 unticked. The agent may also give
up gracefully, logging a final stumble that explains the blocker.

This replaced a two-gate `compile`-then-`verify` condition expressed in `-pl … -am` reactor commands.
Both changes came from the same place: the testapps left the reactor, so there is no `-pl` to run,
and the kit had to state a done condition a customer could recognise. Starting is a strictly stronger
bar than `verify` was for the failure modes that matter.

**A UI review stays out of scope.** The click-through is a crash check, not a fidelity check: pixel
offsets, fonts and layout differences are not what the round measures. A systematic runtime
verification — every `testapps/crud/swing/test.md` scenario driven via Playwright / Swing-MCP against
the running emulators app — is a separate workflow, not part of this doc-quality probe.

## Run protocol

```
/guide-migrateapp crud           # → /build-kit, reads guide-migrateapp/crud.md; the kit session writes STUMBLES.md; copy back, summarize
# inspect STUMBLES.md
/guide-docfix crud           # proposes edits, applies on approval
git diff migration/        # review
# commit, repeat next time the docs change
```

To retain a stumble-list across runs (the next round replaces the stage-2 tree wholesale):
```
cp testapps/crud/1-emulators/STUMBLES.md /tmp/stumbles-$(date +%Y%m%d-%H%M).md
```

## Sync targets

**None.** Nothing under `guides/1-swing-to-emulators/` is a snapshot of an upstream source any more.

The Vaadin Boot host-app doc (then `vaadin-boot-scaffold.md`) was one — a copy of the global `vaadin-boot-app` skill under a retargeting
preamble — and the arrangement failed in a way worth recording, because it is what a future "just
snapshot it" proposal has to answer. A skill written for *scaffolding a new app* carries an MCP
preflight, a test stack, a persistence chooser and a set of ask-the-user steps, none of which a
migrator has any use for; the preamble grew to tell the reader to skip roughly 145 lines of the 700
it introduced, one of which opens "This is step zero. Do not skip it." Worse, the two diverged in
both directions until a blind re-sync would lose work either way — at which point the maintenance
benefit the snapshot was meant to buy was already gone — and the parts nobody was reading rotted:
the pom template still configured `exec:java` and its dev-mode line told the reader to run it, which
is exactly the invocation that cannot pass `--add-opens`, so a migrator who copied the template got
an app whose first modal dialog hung.

What replaced it: the host app is a real, copyable tree per bootstrap (`seed/vaadin-boot/`,
`seed/spring-boot/`, each built as shipped under `-Pkit`), the build reasoning is `build-wiring.md`,
and `host-app-vaadin-boot.md` / `host-app-spring-boot.md` are migration-authored references of their
own. **The rule that
falls out: a migrator-facing doc is authored for the migrator, never adopted from a document written
for someone else.** The global skill still exists and is still the right thing for a new Vaadin-Boot
app; it is simply not a source these guides track.

## Gated rounds — the gate ladder is the rubric

A guide-loop round grades the docs and stops at a crash check; a **gated round** drives the *booted*
app against a fixed ladder of gates — the "separate workflow" the done condition above leaves out of
scope. Same tree, different measurement: **don't merge the two exercises.** None has run yet; the
plan for the first ones, on `inventory`, is
[#16](https://github.com/vaadin/swingbridge-emulators/issues/16).

**Decided 2026-08-25: there is no separate scoring scheme.** Each gate emits a comparable figure of
its own, so a round's result is the four readings side by side:

- **Compile gate** — does the import-swapped app build against `:emulators`, and how many distinct
  error classes on the first attempt?
- **Boot gate** — does it reach its first screen (for `inventory`, the login, `ADMIN`/`ADMIN`) in a
  browser?
- **Zero-stub-WARN gate** — the `WarnInventoryTest` shape `:sampler` already uses per-route, applied
  to the app's user paths.
- **Journey gate** — N named journeys (for `inventory`: login → add category → add vendor → add
  item → stock query → transfer → return), captured against the *desktop* app via `swing-mcp` as
  behavioural ground truth, replayed against the migrated web app. It is the capture→review→generate
  pipeline of [#22](https://github.com/vaadin/swingbridge-emulators/issues/22) applied to a
  testapp, which that file's § "Decided" (2026-09-10) makes the place the pipeline is developed: the
  rounds are repeated, so suite-authoring cost amortizes, and the pipeline stays optional, so a round
  can still run "migration pure" without it.

What a score would add on top is weighting and aggregation into one number, and that is the part
with negative value: collapsing "did not compile" and "three stub WARNs" into one figure destroys
exactly the where-did-it-break information the rounds exist to produce, and any weighting between
gates would be invented rather than measured.

**The discipline is recording, not scoring: each round writes all four readings verbatim into its
`STUMBLES.md`, including the ones it did not reach** (a round that fails the compile gate records
the remaining three as *not-reached*, never as absent). An unreported gate is what makes two rounds
incomparable — the lack of a score is not.

## Future work

- **Integration-testing the migrated app.** The guides deliberately say nothing about it: how a
  migrator gains confidence the ported app still behaves like the desktop one is an unbrainstormed
  hole, and a half-answer in a migrator-facing doc is worse than an acknowledged gap. The proposal
  is [#22](https://github.com/vaadin/swingbridge-emulators/issues/22) — capture scenarios off
  the running *desktop* app through [swing-mcp](https://github.com/vaadin/swing-mcp), generate tests
  that replay them against the migrated one, so the expectations come from the app that already
  worked. Its graduation target is a `guides/testing.md` offered as an **optional recommended
  follow-up after the migration**, never a phase inside it.

  Two things are already settled and a doc fix must respect them. **The framework to recommend is
  Vaadin's own Browserless Testing** (`com.vaadin:browserless-test-junit6`, base class
  `BrowserlessTest`) — the official way to test a Vaadin app, and **free for all users since Vaadin
  25.1**, which removed the licence reason a community alternative was once the pragmatic pick; it
  was called *UI Unit Testing* before Vaadin 25 renamed it. **Karibu-Testing stays out of
  migrator-facing docs**: it is what this repo's own suite uses, which is a maintainer choice about
  `:emulators` and `:surrogates`, not a recommendation to a migrator. The done condition stays
  "compiles, starts and survives a click-through".
- **More test apps.** Two further apps run through the pair (see the table above); the remaining gap is a *Sampler-shaped* target — flows built to exercise the emulator surface exhaustively rather than to look like an app.
- **Mirror for step 2.** When `guides/2-emulators-to-surrogates/` exists, build a parallel skill pair (a step-2 `guide-migrateapp` / `guide-docfix`) using the same protocol. The narrow-scope discipline transfers; the work-tree path becomes `testapps/crud/2-surrogates/`.
