---
name: guide-docfix
description: Read the stumble-list a /guide-migrateapp <app> run left at testapps/<app>/1-emulators/STUMBLES.md and route each finding to its owner — the guides/1-swing-to-emulators/ docs, the hazard table, the spec/decisions backport, or an owner the app's residue file adds (an add-on's MIGRATION.md, the app's PROVENANCE.md). Proposes edits, applies them on approval. Step two of the guide loop, after /guide-migrateapp.
disable-model-invocation: true
argument-hint: "<app>"
---

## Purpose

Step two of the **guide loop**: take the stumble-list `/guide-migrateapp <app>` produced and improve the docs based on what actually
tripped up the (simulated) fresh migrator. Most of the work is **routing**: a stumble is reported
where the migrator hit it, which is often not the file that owns the fix.

## Procedure

`<app>` is the argument — the testapp's directory name under `testapps/` (`crud`, `jlawyer-shape`,
`inventory`).

### 0. Read the app's residue file, if it has one

If `<app>.md` exists beside this file, read it first: it adds owners, routing rules and
required reading this procedure does not carry. Today only [`inventory.md`](./inventory.md) exists —
the adopted app, whose stumbles also land on two add-ons' own docs and on its `PROVENANCE.md`.
`crud` and `jlawyer-shape` are hand-built and need nothing beyond this file. A residue file is data,
kept here for the same reason `/guide-migrateapp`'s are
([D_guide_round_residue](../../../emulators/decisions.md#D_guide_round_residue)).

### 1. Read the stumble-list

Read `testapps/<app>/1-emulators/STUMBLES.md`. If it doesn't exist, tell the user to run
`/guide-migrateapp <app>` first and stop.

Read the `## Not a doc gap` section at the end too — it holds the entries the schema deliberately
can't express (the kit's `agent-prompt.md` defines its three kinds). Those route in step 3, not
through the severity triage.

**A stumble list from before 2026-09-09 is graded against docs that no longer exist.** Phase 0 was a
language-server gate with two branches then, and anything about the exhaustive read, the file/line
counts, the reconciliation-as-precondition, or `checkStaticState()` against pre-migration classes
belongs to a procedure [M1D_lsp_recommended](../../../migration/1-swing-to-emulators/decisions.md#M1D_lsp_recommended)
deleted. Drop those entries rather than routing them, and say how many you dropped. Everything
outside Phase 0 triages normally.

### 2. Triage

Sort the schema'd stumbles by severity tag:
1. `[error]` — docs say something untrue. Always fix.
2. `[doc-contradiction]` — internal inconsistency. Always fix.
3. `[missing]` — the docs did not cover it, so the migrator had to look elsewhere or invent. Fix if the missing info belongs in the docs (most cases); skip if it is reasonable IDE-territory (a method-signature lookup anyone would do anyway). Older stumble lists spell this tag `[reference-needed]`, from when the probe framed it as leaving a read-scope allow-list; triage it the same way.
4. `[guess]` — ambiguous wording. Fix if the wrong guess would mislead; skip if both readings are fine.

Within each tag, group by **owning file** (step 4), not just by severity.

### 3. Route the non-doc entries first, and don't bury them

The three `## Not a doc gap` kinds are not doc fixes at all, and each has a named home:

- **(a) A stage-1 change the agent wanted in `testapps/<app>/swing/**`.** Decide whether it falls
  under a relaxation of invariant 1 in [`testapps/CLAUDE.md`](../../../testapps/CLAUDE.md) — the
  third-party-library one (a call site or two, no behavioural rewrite, same component surface), the
  instrumentation one (makes the migration observable, or makes a swallowed failure honest), or the
  dangling-asset one. If it does and the user approves, the edit lands in `swing/`, and in the app's
  `PROVENANCE.md` too if it has one, naming which relaxation. If it doesn't, it's a gap to route, not
  an edit to make — surface it to the user as such. Never make a stage-1 edit silently; the baseline
  is what makes the stage-2 diff mean anything.
- **(b) An `:emulators` / `:surrogates` / add-on shortfall.** Not docs. Hand it back to the user with
  a one-line note of what's missing — it belongs in a GitHub issue or a `D*`/`SD*` triage, and
  this skill doesn't own any of those.
- **(c) An environment failure.** Not docs either. A build failing in code the agent never touched is
  one of `/build-kit`'s stale-tree modes; name which one if the entry lets you tell, and hand it back.

Report every class explicitly in your summary even when the answer is "no action" — these are
findings the guides cannot absorb, and they are easy to lose under a long list of doc edits.

### 4. Route each doc fix to its owner

**Present tense only.** A stumble is a story about something that went wrong; the fix is the corrected
present-tense statement, **never the story**. No "this used to throw", no "now supported", no "an
earlier version of this guide said", no landed-in dates — the migrator downloaded one version and has
no referent for a past state. Backport the durable *what* to
[`spec.md`](../../../migration/1-swing-to-emulators/spec.md) (§1 states the rule in full) and the *why*
including what it superseded to that folder's `decisions.md`.

| the stumble is about | fix goes in |
|---|---|
| the migration procedure — a phase, the entry-point split, the guardrails, the host app (a seed or its `host-app-*.md`) | `guides/1-swing-to-emulators/`: a *step* in `guide.md`, or the reference that step links (`static-fields.md`, `former-singletons.md`, `build-wiring.md`, `host-app-spring-boot.md`, `host-app-vaadin-boot.md`, `lifecycle.md`, `dates.md`, `runtime-contract.md`, `import-swap-reference.md`, `third-party-libraries.md`, `platform.md`) |
| a **detection pattern** — a hazard grep that missed, over-matched, or is absent | `migration-tool/src/main/resources/META-INF/emul/hazards.tsv` **and** its `<a id="H_…"></a>` bullet in `migration/1-swing-to-emulators/spec.md` — `HazardTableTest` fails the build if you touch only one (M1D_hazard_scan_tool) |
| a fact the spec should carry (see step 5) | `migration/1-swing-to-emulators/spec.md` (or `decisions.md` for a rationale call) |

The app's residue file may add rows. Whether a `guides/` fix is a step or a reference is decided by
`CLAUDE.md` § "`guide.md` is the procedure and nothing else", which is loaded here already and
deliberately not restated.

**A stage-2 pom that is wrong is not a file to patch.** If a stumble shows `1-emulators/pom.xml`
missing a BOM import, an enforcer, a plugin version the build needs and inherits from nothing, the run
copied that pom from its bootstrap's seed — `guides/1-swing-to-emulators/seed/spring-boot/` for
`crud`, `seed/vaadin-boot/` for the others — so the finding is about *the seed or its
`host-app-*.md`*: fix it there, and the next run copies a working pom. The committed pom is derived
output; editing it fixes nothing and is overwritten. What still belongs in the app's own tree is a
dependency of the app's own that stage 1 genuinely needs, and that goes in `testapps/<app>/swing/pom.xml`,
where the copy propagates it.

### 5. Decide backport-to-spec per stumble

For each fix that lands in `guides/`, decide whether to also update `migration/1-swing-to-emulators/spec.md` (or `decisions.md` for rationale calls):

- **Backport** if the fix changes **what** the docs say: a coverage claim, a fact about emulator behavior, a recommended approach, a hazard the spec should know about.
- **`guides/` only** if the fix changes **how** the docs say it: ordering, phrasing, examples, missing cross-link, restructured section.

When in doubt, ask the user — they own the spec/guides split.

### 6. Propose edits

For each fix, present:
- Target file(s) — the owner from step 4, plus the spec/decisions file if backporting.
- Current text (short quote).
- Proposed text.
- One-line justification citing the stumble id.

Group the proposals **by target file**, core guides first, then any owner the residue file added, then
backports, so the user can review one owner at a time.

### 7. Apply on approval

After the user approves (all, some, or none), apply the edits with `Edit`. Do **not** apply without an
explicit go-ahead — the docs are user-facing and the user owns voice/structure decisions. Then run
whatever tests the residue file names for the files you touched.

### 8. Don't auto-archive STUMBLES.md

Leave `testapps/<app>/1-emulators/STUMBLES.md` in place. The next `/guide-migrateapp <app>` replaces the
whole stage-2 tree — the pom included — with the one its run produced, which clears it. If the user
wants to retain a stumble-list across runs, that's their call — copy it elsewhere manually.

## Allowed read scope

Unrestricted within the repo — docfix needs to understand the doc structure, cross-references, and
the spec sources. The strict-scope discipline is the *migration's* job, not docfix's.

## What this skill is NOT

- Not a re-run of the migration — only edits docs (plus, on approval, a stage-1 baseline edit under
  invariant 1).
- Not a place to add new content not motivated by a stumble. If you spot an unrelated doc improvement
  while reading, mention it but don't bundle it in.
- Not the owner of the GitHub issues or the `D*`/`SD*` logs. Emulator shortfalls get handed back,
  not filed.

## See also

- `migration/1-swing-to-emulators/testing.md` — the testing protocol's overall design, including the
  backport heuristic in fuller form.
- [`../guide-migrateapp/SKILL.md`](../guide-migrateapp/SKILL.md) — the half that produces the stumble-list.
- `testapps/CLAUDE.md` — invariant 1 (baseline + its relaxations) and invariant 4 (stumbles live in
  the stage-2 directory).
