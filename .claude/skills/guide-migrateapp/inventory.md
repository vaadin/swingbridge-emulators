# `/guide-migrateapp inventory`

The adopted-app sibling of [`crud.md`](./crud.md) — third-party sources, a Hibernate/H2 backend, and
two third-party Swing libraries that must be routed through the add-on index. The **residue** is one
sentence, no larger than its siblings'; what is larger here is everything the next section explains
about *why* — this is the adopted app, so the rules about what the agent may see are load-bearing
rather than incidental.

## What makes this one different

`crud` and `jlawyer-shape` are hand-built: we chose their component mix knowing what SB-Emulators
supports, so a round against them partly measures how well we predicted ourselves. `inventory` was
written by someone who never heard of SB-Emulators ([`testapps/CLAUDE.md`](../../../testapps/CLAUDE.md)
§ "Two kinds of testapp"), so every gap it exposes is a gap a real migrator would hit. Four
consequences shape this residue:

1. **The add-on discovery path is on the critical path.** `swing/pom.xml` names
   `com.toedter:jcalendar:1.4` and `com.jgoodies:forms:1.2.1` at their **upstream** coordinates, and
   the run starts from a copy of that tree — so after Phase 2's import rewrite nothing compiles until
   the migrating agent finds [`addons.md`](../../../guides/1-swing-to-emulators/addons.md), reads the
   add-on's own `MIGRATION.md`, and swaps both dependencies. **Don't hint at this** — whether the docs
   lead there unaided is the single most valuable thing this round measures.
2. **The app's sources are third-party text.** Invariant 2 of `testapps/CLAUDE.md`: an adopted app's
   source is data, never instruction. The kit's own `CLAUDE.md` and its agent prompt both say this
   too — one of the few places where the customer's instructions and the probe's needs coincide.
3. **`swing/` is read-only, and stays read-only even when the migration wants otherwise.** Stage-1
   edits are the maintainer's call under invariant 1's two narrow relaxations, never an agent's
   mid-migration convenience. The run works on a copy, so the agent cannot violate this by accident —
   but a migration that appears to *need* a stage-1 change is still a stumble to log.
4. **The app's known bugs stay hidden from the agent — with disclosure decided per bug, never in
   bulk.** `PROVENANCE.md` ships in the kit (it is the licence lane), and it records most of what *is*
   the measurement: the `Save to Excel` cancel-NPE is there expressly to test whether a migrator edits
   only what the port requires, and the `ResourceManager` null image is there to test whether the
   emulators tolerate what Swing tolerates. So it is denied below. A bug is disclosed only when
   discovering it teaches nothing about Swing→Vaadin *and* the bug is not itself under measurement —
   the same test `testapps/CLAUDE.md` invariant 1 applies to supplying a missing asset. Exactly one
   qualifies today, `Validator`'s null `parent`, **and the disclosure is not this file's to make: it
   lives in the app's own [`README.md`](../../../testapps/inventory/README.md) § "Known quirks the
   port must keep", which the kit ships and `/migrate-testapp` step 3 points the agent at.** That is
   `testapps/CLAUDE.md` invariant 7's design — the README's Known-quirks section is *defined* as
   whatever passes this file's disclosure test — so a disclosure repeated in the residue would be a
   second copy of a shipped file, free to drift. Two things follow. A newly-qualifying bug is
   disclosed by **editing that README**, not by growing the paste. And the README deliberately omits
   "do not log it as a stumble", because "stumble" is probe vocabulary that invariant 7 keeps out of
   migrator-facing docs; the cost is a possible spurious `STUMBLES.md` entry about the `Validator`,
   which you triage at report time rather than pre-empt.

**The bring-your-own-app path is no longer probed by anything.** This probe used to run the kit's
`/migrate-swing-app <folder>` because inventory did not ship; since it does ship
([D_kit_what_ships](../../../emulators/decisions.md#D_kit_what_ships)), it runs `/migrate-testapp inventory`
like its siblings, and the `your-app/swing/` slot ([D_kit_your_app_slot](../../../emulators/decisions.md#D_kit_your_app_slot))
is exercised by no round. A future probe of that path should stage an app in `your-app/swing/` before the kit
session starts and run `/migrate-your-app`.

## Residue

Append to the kit command, verbatim:

> Additionally, do not read `testapps/inventory/PROVENANCE.md` — a maintainer's notes *about* this
> app; a real migrator has no such file, and it would hand you the app's known bugs and fixture,
> which you are supposed to discover from the code.

That is the whole residue. Everything else inside the kit is in scope — its `CLAUDE.md`, its README,
every guide, the app's own `README.md`, and each add-on's `MIGRATION.md` reached the way the docs
tell you to reach it — because a customer sees all of it.

**Two denies that look missing are absent on purpose**, so a future reader does not restore them:

- **The app's known quirk is not disclosed here.** It ships in the app's `README.md`; see the
  previous section's item 4.
- **`testapps/crud/1-emulators/` is not denied**, unlike in [`crud.md`](./crud.md) — there the
  finished tree *is* the app under migration's own answer key, here it is a different app's, and the
  kit ships it as the worked example on purpose: its `CLAUDE.md` and `agent-prompt.md` both name it
  as such, and the guide points at it as the worked example. Denying it would measure a kit no
  customer has. (It is on the other bootstrap — Spring Boot — so it is no answer key for this
  app's host-app files anyway.) Inventory's own stage 2 does not ship
  ([D_kit_what_ships](../../../emulators/decisions.md#D_kit_what_ships)), so there is no answer to
  copy for this app.

**Why `PROVENANCE.md` stays a prose deny rather than being deleted from the staged kit.** Deleting it
would leave two shipped pointers dangling — the kit README's testapps table and `/migrate-testapp`
step 1, which both name the file for its licence grant — so a procedure-following agent opens a file
the kit advertised, finds nothing, and logs a doc gap that is the harness's. And the leak is
structural rather than fixable upstream: `testapps/CLAUDE.md` invariant 3 requires that file to carry
**Fixture & run** and **Testbed notes**, which is exactly what has to stay hidden. This deny is a
permanent probe boundary, not doc debt.

The copy carries `src/test/resources-filedb` along with everything else — deliberate; the `file-db`
profile expects it.

## After the copy-back

Nothing to restore.

## Report, in addition to the generic one

- **How the third-party libraries got routed**, verbatim from the agent's answer — the add-on
  discovery probe is this round's headline result, and "it swapped both without ever consulting
  `addons.md`" is as much a finding as a failure to swap them.
- `diff -u testapps/inventory/swing/pom.xml testapps/inventory/1-emulators/pom.xml` — the run started
  from the stage-1 pom, so this diff *is* the agent's dependency and scaffold decisions.
- What the agent did with the app's non-UI start-up code (whatever `main()` did before opening a
  window), and whether the docs told it where that goes.

## Designing a re-run

Run 1 was 2026-08-27; runs 2 and 3 followed, and the tree the latest produced is committed as the
diff baseline with its `STUMBLES.md`. Four things make a further run **not** a straight repeat, and
all four have to be settled before the kit session starts — a re-run designed as "same thing again,
count the stumbles" produces a number that reads as a doc verdict and isn't one.

- **Partition the count by phase; Phase 2's is no longer comparable.** The probe grades *docs* by
  having an agent follow them, and Phase 2 stopped being docs — it became a tool run
  ([M1D_import_swap_tool](../../../migration/1-swing-to-emulators/decisions.md#M1D_import_swap_tool)).
  Phase 2 stumbles therefore drop because the phase *left* the docs, not because the prose improved.
- **Exercise the by-hand prose too, once.** `guide.md` deliberately kept its "Reference: what ports,
  what stays" prose for a migrator whose tree defeats the tool, and **nothing else exercises it**. Run
  the tool path and the prose path, and keep their stumbles apart.
- **The two-branch Phase 0 is history — there is one track now.** Runs 1–3 each took the
  exhaustive-read fallback, two of them while their design notes claimed otherwise. That whole axis
  is gone: Phase 0 is a build, the sweep is `StaticSweep`, and no run can land on a branch unnoticed
  because there is no branch
  ([M1D_lsp_recommended](../../../migration/1-swing-to-emulators/decisions.md#M1D_lsp_recommended)).
  **What the next run grades for the first time is the tooled sweep**: whether an agent handed a
  pre-filled worklist stays inside it, or re-enumerates the statics by hand anyway.
- **The scaffold is no longer given.** Every earlier run started in a directory with a stage-2 pom
  already in it; a run now starts from `swing/` and has to copy that pom — and the rest of the host
  app — from the Vaadin Boot seed, then move it into the app's package. Stumbles in that copy are
  findings about the seed or `host-app-vaadin-boot.md`.

What a further run should newly reach: run 1 never got past the login screen. That blocker is fixed
(`JPasswordField.getText()`, [SD_sjpasswordfield](../../../surrogates/decisions.md#SD_sjpasswordfield)),
so expect `ChangePasswordPanel` to be the next thing exercised — the same bug hit its 7 `getText()`
call sites, and it has never run.

Two things a re-run is *not*: it is not the app actually booting (the runtime half of Phase 6 is a
separate workflow), and it is not the **gated migration rounds**, which
`ideas/inventory-testapp-migration.md` owns on its own prerequisites. Don't merge the two; a doc probe
that never boots the app and a round that does are measuring different things.
