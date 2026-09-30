---
name: guide-migrateapp
description: Step one of the guide loop — run a real migration of one bundled testapp (crud, jlawyer-shape, inventory) from the current guides, through the distribution kit exactly as a customer would — build the kit, stage the copy, run the kit's own /migrate-testapp from a Claude Code session rooted at the kit, copy the result back as the committed baseline, report. Run to find where the guides fail, so its output is replaced every round rather than kept; each app's residue lives in <app>.md beside this file. No argument or `help` lists the apps. Feeds /guide-docfix <app>.
disable-model-invocation: true
argument-hint: "<app> | help"
---

## Purpose

Step one of the **guide loop**, whose step two is `/guide-docfix <app>`: run a real migration of one
testapp by following the current migrator-facing guides, and capture every point where they failed as
a stumble-list entry that feeds `/guide-docfix <app>`. One pass through both steps is a **round**.

**The migration is real, and it runs *through the customer's own instructions*; what it is for is the
stumbles.** Its output is replaced wholesale by the next round, never curated. You do
not write a migration prompt: you build the kit, and run its `/migrate-testapp <app>` the way someone
who unzipped a download would ([D_guide_round_residue](../../../emulators/decisions.md#D_guide_round_residue)).
Everything that is not that is **harness residue**, and there is deliberately very little of it: this
skill's copy-back, and whatever the app's residue file hands you (a deny-list, a restore step).
**Any further delta you find yourself wanting between this procedure and the kit's own instructions
is a finding against the kit, not a rule to add here.**

## Procedure

`<app>` is the argument — the testapp's directory name under `testapps/`.

### Help — no argument, or `help`

With no argument, or with `help`, **print the help below and stop** — build nothing, stage nothing.
Do the same, prefixed with one line naming the unknown app, when `<app>` has no residue file (step 0).

The app list comes from the residue files, not from memory: every `<app>.md` beside this file is one
migratable app, and its `## What this app probes` section's first sentence is its one-line summary
(for `inventory.md`, which has no such section, the file's opening sentence). Then print:

```
Usage: /guide-migrateapp <app>

Drives one bundled testapp through the distribution kit the way a customer would:
rebuilds the reactor, unzips the kit outside any git repository, stages the copy,
then has YOU run the kit's own /migrate-testapp <app> in a second terminal rooted
at the kit. The result is copied back as testapps/<app>/1-emulators/ (replaced
wholesale) and the stumbles are counted.

Apps:
  <app>  <one-line summary>
  ...

Next: /guide-docfix <app> applies the stumbles the run found.
```

### 0. Read the app's residue file

Read `<app>.md` beside this file — [`crud.md`](./crud.md),
[`jlawyer-shape.md`](./jlawyer-shape.md), [`inventory.md`](./inventory.md). It holds everything this
procedure does not: what the app probes, the **residue** text for step 3, anything to restore after
step 4, and what to report in addition in step 5. No file for `<app>` is a stop (with the help above):
a testapp becomes migratable here by gaining one, never by improvising its residue.

**One skill with per-app files, not a skill per app.** This skill carries `disable-model-invocation`
— a round rebuilds the reactor and needs the maintainer at a second terminal, so no model should
start one — and the harness enforces that flag on a skill-to-skill call too, so a per-app skill
cannot hand off to it. The residue is data, so it lives here as data
([D_guide_round_residue](../../../emulators/decisions.md#D_guide_round_residue)).

### 1. Build the kit

Run `/build-kit`. It rebuilds the reactor and ends by naming `SWINGBRIDGE_HOME` — the kit
**unzipped into a fresh temp folder outside any git repository**. A non-zero build is a stop; do not
proceed on a stale kit, and make sure nothing else builds this tree while the run lasts. Use the
path it reports for the rest of this procedure; `$KIT` below is that path.

### 2. Stage the copy — before the kit session starts

```bash
KIT=<the path /build-kit reported>
mkdir -p "$KIT/work" && rm -rf "$KIT/work/<app>"
cp -r "$KIT/testapps/<app>/swing" "$KIT/work/<app>"
```

This is the kit README's own recommended first step, not residue: a language server, if one is
running, scans for projects when Claude Code starts, and a project copied into place afterwards is a
*non-project file* to it (measured 2026-09-08). Nothing gates on that any more, but staging first is
still what the README tells a customer to do. The kit's skill keeps an existing copy that is still
identical to `swing/`, so it finds this one and moves on.

### 3. Run the customer's own command, from a session rooted at the kit

**Not from this session.** The kit's migrate skills do the migration **in the session the command was
typed into**, not in a subagent
([D_kit_no_subagent](../../../emulators/decisions.md#D_kit_no_subagent)) — so the session that
migrates is the session you type into, and this one has read this skill, the residue rationale and
the testing framing, none of which a customer's session holds. The temp kit carries the other half:
it sits outside any git repository, so the migrating agent cannot walk up into this repo's emulator
sources or decision logs, and no `.gitignore` of ours reaches its files.

So open a second terminal:

```bash
cd "$KIT" && claude
```

and in it type `/migrate-testapp <app>`, followed on the same line by the app's residue **verbatim**
— that session is the one that migrates, so the deny-list reaches it directly, without anyone
paraphrasing the kit's prompt. The kit's skill will offer to run the desktop app first; decline
(no display). Then let it run: the migration, then the report. Do not paraphrase the kit's
instructions and do not substitute your own prompt — the whole point is that the maintainer runs
what ships.

### 4. Copy back

The agent worked in `$KIT/work/<app>/`, a temp folder nothing preserves. Copy the result in as the
committed baseline:

```bash
rm -rf testapps/<app>/1-emulators/src testapps/<app>/1-emulators/STUMBLES.md
cp -r "$KIT/work/<app>/src" testapps/<app>/1-emulators/src
cp "$KIT/work/<app>/STUMBLES.md" testapps/<app>/1-emulators/
cp "$KIT/work/<app>/pom.xml" testapps/<app>/1-emulators/pom.xml
```

**If the app's residue file names anything to restore after this copy, do it now** — the copy is a
wholesale overwrite, so a file the repository needs that the migration had no reason to produce (a
licence notice on a vendored third-party file, for instance) is gone until it is put back.

The pom is migration output like everything else (`testapps/CLAUDE.md`, "the stages after `swing/`
are output"). Then check it still builds where it now lives: `cd testapps/<app>/1-emulators && ./mvnw -C verify`.
If the agent's pom does not build standalone (a missing BOM import, no enforcer, an inherited plugin
version it never wrote), **that is a finding about the seed it copied, or its `host-app-*.md`**, not something to patch
silently — it is precisely the instruction the testapps left the reactor to expose.

### 5. Report

Briefly, without re-narrating the agent's work:

- Did the migrated app compile and start? (The kit's exit criterion, not a project milestone.)
- Count of stumbles by `evidence` tag, e.g. `2 [error], 0 [doc-contradiction], 5 [missing], 3 [guess]`,
  plus the `## Not a doc gap` entries.
- Whatever the app's residue file asks for in addition.
- **Any delta you had to introduce beyond the residue** — that is a finding about the kit and belongs
  in the report, not in a skill.
- Suggest `/guide-docfix <app>` to apply the findings.

## What this is NOT

- Not a migration you keep — `testapps/<app>/1-emulators/` is replaced wholesale on every round, so
  nothing in it is curated by hand.
- Not a build / test gate — "compiles, starts and survives a couple of clicks" is the kit's done
  condition, not a project milestone.
- Not a runtime probe — the agent's click-through is a crash check; systematic UI journeys against
  the running app (`testapps/<app>/swing/test.md`) are a separate workflow. This one hands the URL
  to a human and stops.
- Not a place to fix the guides — that is `/guide-docfix <app>`.
- Not idempotent across doc edits — running twice on the same docs may produce different
  stumble-lists; agents wander.

## See also

- `migration/1-swing-to-emulators/testing.md` — the testing protocol's overall design. Don't paste
  it into the kit session — it would leak the testing framing to an agent that is supposed to be a
  customer.
- [D_guide_round_residue](../../../emulators/decisions.md#D_guide_round_residue) — why a round is a
  residue over the kit;
  [M1D_lsp_recommended](../../../migration/1-swing-to-emulators/decisions.md#M1D_lsp_recommended) —
  why the kit is unzipped outside a repository, and why there is no tooling gate left to report on.
