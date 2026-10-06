# Migrating to surrogates (step 2) — deferred, needs tackling

**Status:** deferred / not started. Placeholder so it isn't lost. No commitment; a
future pass picks it up.

**Maintainer-facing.** The emulators→surrogates transition is migration step 2 (see
[migration arc](../CLAUDE.md#migration-arc), stage 2→3): view-by-view rewrite of
`:emulators` views to `:surrogates`, where code shape becomes Vaadin-shaped.

## What's parked

Step 1 (swing→emulators) is done and its machinery lives under
[`migration/1-swing-to-emulators/`](../migration/1-swing-to-emulators/) (sources) +
[`guides/1-swing-to-emulators/`](../guides/1-swing-to-emulators/) (the migrator-facing
guide). Step 2 has an **empty slot**: no `migration/2-emulators-to-surrogates/` sources
folder, no `guides/2-emulators-to-surrogates/` guide yet.

Also parked with it: the **stage-3 sibling `testapps/jlawyer-shape/surrogated/`** — a
one-session-agent rewrite of the stage-2 `1-emulators/` app onto `:surrogates`, which is
the natural round target for step 2.

## The loop to run when we pick it up

Same **guide loop** that produced step 1's guide:

1. **Spec** — draft `migration/2-emulators-to-surrogates/spec.md` (+ `decisions.md`
   with `M2D_` slugs, per the [migration README](../migration/README.md)). No per-step
   `ideas.md` — loose ideas go in this folder, one file per idea.
2. **Guide** — hand-author `guides/2-emulators-to-surrogates/guide.md` from the spec
   (procedure only, references beside it — the split CLAUDE.md §"See also" describes
   for step 1).
3. **Round** — run an agent through the guide against a real target (the
   `jlawyer-shape/surrogated/` rewrite), recording every stumble.
4. **Backport** — fold the stumbles into the guide and the spec/decisions;
   repeat until a round runs clean.
