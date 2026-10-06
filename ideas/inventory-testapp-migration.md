# Inventory testapp — the gated migration rounds

**Maintainer-facing.** What is left of the original adoption-and-migration plan: the **gated
behavioural rounds**, which have not run. Everything else has graduated — the app itself is
[`testapps/inventory/README.md`](../testapps/inventory/README.md) (scale, Swing surface,
dependency routing, non-SB-Emulators hazards) and [`PROVENANCE.md`](../testapps/inventory/PROVENANCE.md)
(upstream, reduction, trust, fixture, ported bugs); the doc-quality probe is
[`/guide-migrateapp inventory`](../.claude/skills/guide-migrateapp/inventory.md), which owns its
own re-run design; the add-on model's remaining exit criteria are
[M1D_addon_packaging](../migration/1-swing-to-emulators/decisions.md#M1D_addon_packaging); and the
gate ladder a round is graded on — compile, boot, zero-stub-WARN, journey, recorded side by side
with no score — is
[`migration/1-swing-to-emulators/testing.md` § "Gated rounds"](../migration/1-swing-to-emulators/testing.md#gated-rounds--the-gate-ladder-is-the-rubric).

**What has happened, so the open items are read against it.** Guide-loop round 1 (2026-08-27)
produced the first stage-2 tree that compiles, verifies and *boots*: zero server-side exceptions,
zero browser console errors, a 5-line WARN inventory (all R_layouts_close_enough geometry), the
static sweep 13/13 against a pre-written golden table, and 17 doc stumbles — **all since
discharged** ([`1-emulators/STUMBLES.md`](../testapps/inventory/1-emulators/STUMBLES.md) § Status).
It stopped at the login screen, on an SB-Emulators defect since fixed
([SD_sjpasswordfield](../surrogates/decisions.md#SD_sjpasswordfield)). Past login, the app has so far
only been *looked at*, not driven against the ladder: the 2026-10-06 side-by-side comparison
([inventory-visual-comparison.md](./inventory-visual-comparison.md)) opened every screen on seeded
data with no server exception, and graded no gate.

## Still open

1. **The rounds themselves.** No round has run. Round 1 is unblocked — no prerequisite is
   outstanding.
2. **The journey gate's sub-question: hand-written and replayed, or generated per the capture
   pipeline?** Deliberately deferred until after the first couple of rounds. The three cheaper gates
   discriminate without it; what the journeys should *assert* is worth knowing from where a round
   actually goes wrong rather than guessing in advance. The hard part — byte-identical seeded state
   with nameable ids and dates — is already done (`PROVENANCE.md` § "What the seed puts in the
   database"). Hand-written is the cheap answer and the one consistent with the sibling testapps.
3. **Two findings that need *traffic*, not another compile.** Both are why a round is a different
   instrument from a guide-loop round, and both are described in
   [`testapps/inventory/README.md`](../testapps/inventory/README.md) § "Non-SB-Emulators hazards":
   the DB layer's session leaks (`find` has no `finally`, `runQuery` touches `session` before
   `startOperation()`), and whether the 13 `FormLayout` panels actually *look* right — a
   confirmation pass in front of a human, which is also M1D_addon_packaging's last open exit
   criterion. The second has a first answer — structurally wrong, per
   [inventory-visual-comparison.md](./inventory-visual-comparison.md) — and is that file's worklist
   until fixed.
4. **Notify the upstream author, as a courtesy — not yet.** Not required by the CopyLeft grant, and
   nothing is published or distributed yet, so there is nothing for them to look at. **Notify when
   the project goes open-source**, in the same pre-publication pass that re-opens
   M1D_addon_packaging's reimplementation-is-not-derivative gate (deferred for the same reason, and
   recorded in each `third-party/*/PROVENANCE.md`).

## Graduation

When the rounds have run, the residue lands as `M1D*` entries in
[`../migration/1-swing-to-emulators/decisions.md`](../migration/1-swing-to-emulators/decisions.md)
(technique + hazards), the guide in `../guides/1-swing-to-emulators/` (anything migrator-facing),
`testapps/inventory/README.md` + `1-emulators/STUMBLES.md` (the app's own story), and `D*`/`SD*`
for any emulator/surrogate gap a round forces closed. Then delete this file.

## See also

- [`../testapps/inventory/README.md`](../testapps/inventory/README.md) — the app: why it is here,
  what it exercises, how its dependencies were routed, the hazards it carries.
- [`../.claude/skills/guide-migrateapp/inventory.md`](../.claude/skills/guide-migrateapp/inventory.md)
  — the doc probe, its § "Designing a re-run", and the rebuild-the-reactor gate every run starts with.
- [`../migration/1-swing-to-emulators/testing.md`](../migration/1-swing-to-emulators/testing.md) —
  the guide-loop protocol's design, and the gate ladder the rounds are graded on.
- [`local-llm-migration-target.md`](./local-llm-migration-target.md) — pre-rewritten forks as
  lever #3; this app's two real forks make it concrete.
