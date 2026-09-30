# Local-LLM migration target — design emulators so a local model can do the swing→emulators port

**Status:** a **"try,"** held loosely (see [*The "try" and islands*](#the-try-and-islands)) — not a
hard commitment, but scoped and partly built. Brainstormed 2026-07-16; the target has a concrete
shape (a struggle-list + escalation protocol, **not** an eval), and its four mechanical guardrails
are implemented (see [*Status & where it lives*](#status--where-it-lives)).

**Maintainer-facing.** Touches the stage-2 migration mechanics
([`migration/1-swing-to-emulators/`](../migration/1-swing-to-emulators/)) and the
[migration arc](../CLAUDE.md#migration-arc). Closely coupled to the static-sweep taxonomy ([M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy) / migrator reference [static-fields.md](../guides/1-swing-to-emulators/static-fields.md)) and
[multi-tab-and-session-scoping.md](./multi-tab-and-session-scoping.md) — the static/singleton sweep
is the piece singled out as special (see [*The static sweep*](#the-static-sweep--the-one-silent-hazard)).

---

## The idea

**Design `:emulators` so that a *local* LLM — not a cloud frontier model — can migrate a Swing app
onto the emulator stage.** The migration agent is assumed wired to good tooling: a Java **LSP**
(types, call hierarchy, find-usages), the **Vaadin MCP** (component APIs / styling), and potentially
the **Maven MCP** (version checks). Given that scaffolding, the model's own reasoning budget should
be enough for the stage-2 port.

**Concrete target model:** Qwen3.6 **35B-A3B** MoE ([qwen.ai](https://qwen.ai/blog?id=qwen3.6-35b-a3b);
~3.3B active params, agentic-coding-tuned Qwen3-Coder-30B-A3B-Instruct with 256K native context),
**or a ~30B dense model of equivalent capability.** The point isn't the checkpoint — it's "a model a
privacy-conscious enterprise can run on its own hardware," not a cloud API.

## Why — privacy-concerned enterprise customers

The primary driver: enterprises with private source code that will not "leak" to a cloud agent. If
the stage-2 port *requires* a cloud frontier model, those customers are locked out of the mechanical
path entirely. A local-runnable target keeps the whole codebase on-premise.

## Why there's nothing to validate

The choice of local-LLM vs. cloud vs. human is a **cost and/or code-can't-go-to-cloud decision, not
a capability gate.** The target is tied to no coverage %, and no eval a model must "pass" — the
migration rules are set beforehand and the migration just has to process.

"Nothing to validate" is **rigorous, not hand-wavy**: import-swap + emulator faithfulness ⇒ behavior
preserved *by construction*, except at the finite, enumerated set of seams where import-swap provably
doesn't preserve it — [spec.md §7](../migration/1-swing-to-emulators/spec.md) + the static sweep. So
correctness reduces to **hazard-triage completeness** (checkable by grep + the WARN inventory), not
to an unmeasurable "did the port work." The validation surface is closed and known in advance. The
work then cleaves cleanly:

- **Finding hazards = mechanical.** Grep + WARN inventory produce the worklist; any model — or a
  shell script — does it.
- **Resolving hazards = the struggle-list.** Some resolutions are mechanical (`System.exit` →
  `dispose()`; the main window's `WINDOW_CLOSING` handler → a dialog-free session-destroy hook,
  stripping the confirm and keeping the server-side cleanup — [M1D_mainwindow_split](../migration/1-swing-to-emulators/decisions.md#M1D_mainwindow_split)); some need judgment (the static "hard middle", a custom `LayoutManager` → CSS mapping,
  component-print → server-PDF redesign).

## The design lever — convert agent-judgment into mechanical gates

Make a *small* model succeed by **taking work off the agent** — not by making it smarter, but by
moving each judgment call into something that runs deterministically regardless of model capability.
Five instances of the one lever, all model-capability-independent:

1. **Faithful emulators** — the more faithfully the emulator behaves like real Swing, the less the
   agent reasons about behavior. (The project objective already, per CLAUDE.md "Current scope" — so
   the target rides it for free on the emulator side.)
2. **Build-time lints** (ArchUnit) — turn silent/easily-missed hazards into compile-fails the model
   can't skip: the *component-in-`static`/session* check and a blanket `System.exit` ban.
3. **Pre-rewritten forks** — turn "fork a sprawling third-party lib" islands into a dependency-swap.
   A **cloud** agent can build the generic ones (SwingX-scale) *once* — they carry no customer secret
   (open source), so it's fine even for the strictest privacy customer, and local migrations then
   just import-swap. Fork-priority ranked by **island-severity**, not popularity.
4. **The grep list** — a runnable script unioning every §7 `Detect:` line, grouped by hazard.
   Converts "remember to look for the FQN / `instanceof` edges" into "run these N greps." It's
   **complete over precise**: accept false positives (a comment match, dismissed in seconds); a false
   *negative* on a silent edge ships a bug invisibly.
5. **A tool that owns a whole phase** — the strongest form of the lever, and the first phase to reach
   it: the import swap is now `:migration-tool`, not prose the agent applies
   ([M1D_import_swap_tool](../migration/1-swing-to-emulators/decisions.md#M1D_import_swap_tool)).
   **This is the target's best evidence so far, and it is measured rather than argued.** Running the
   tool's algorithm against all three testapps' human-graded stage-2 trees: 91 files, 67 exact,
   24 differing, **0 disagreements** — and not one of the 24 was a judgement call *about an import*.
   Every residue case belonged to a *different* phase (a later phase deleted the code; the agent
   introduced a type during a semantic rewrite; the add-on table had no rows). So **Phase 2 is
   judgement-free** (`Q_phase_fully_mechanical`, answered) — the first phase to become so, and a phase that on a typical app
   is most of the diff. Phases 1 and 3–6 do not move. Two second-order wins worth naming, because both
   were paid for by the same tool: a model too small to be trusted with 60 lines of shadowing rules now
   needs none of it, and the tool **catches what a graded human run dropped** (`crud`'s
   `EmployeeEditDialog` kept `import java.util.Calendar;` — harmless there, exactly the silent class,
   and a tool does not get bored).

The question for each hard edit: **"can this be reshaped into a rule a 30B model applies reliably, or
a gate that applies it *for* the model?"** If yes, it's on the mechanical path. If not, it routes by
the matrix below.

## Routing: difficulty and sensitivity

When a hazard isn't mechanical, two orthogonal axes (difficulty × sensitivity) decide who handles it. Sensitivity is
**per-fragment, not per-codebase**:

| | secret-free | sensitive |
|---|---|---|
| **mechanical** | local LLM | local LLM |
| **judgment** | **cloud agent** (customer's option) | local attempt → **human** on the stuck-signal |

- Cloud builds generic pieces (a SwingX emulator/fork) once — no customer secret, so fine for privacy
  customers; local then import-swaps.
- The customer may route their *own* secret-free fragments to cloud — a custom `LayoutManager` is
  generic UI plumbing ("put these boxes here", not business rules), so it lands in the cloud-OK cell.
- The **only** true human residue is **hard ∧ sensitive** — far smaller than "all islands."

Cross-check: the two marquee hazards sit at opposite corners. Custom `LayoutManager` = generic →
cloud-friendly. The **static sweep** = the app's own data-scoping/domain semantics → **sensitive**
*and* silent → the worst corner (can't safely cloud it, can't catch it with the stuck-signal), which
independently reconfirms its human review-gate.

## The static sweep — the one silent hazard

The static/singleton sweep is the riskiest piece — **not because it's the hardest, but because its
wrong resolution is the only *silent* one.** A mis-scoped field (a `static` that should have been
session) compiles, boots, and passes the WARN inventory, then leaks across users under load in
production. Every *other* hazard fails loud (compile error, WARN, or throw), so the stuck-signal
catches them; the static sweep the model never gets stuck on — it gets it wrong and moves on. So it
gets its own handling class, **review-gated regardless of model** (even frontier output deserves it —
no capability makes a silent verdict safe unreviewed):

- **A build-time lint** (ArchUnit) flags any `static` field / session attribute assignable to
  `vaadinx.awt.Component` — the mechanical floor under the loudest-consequence, most-common case.
- **A human review-gate** on the finite per-field verdict table (`Q_user_specific` / `Q_world_global` / `Q_counter` of the tree): the model
  emits one row per `static` field + reasoning; a human signs off. Dozens of rows, minutes to review
  — the cheap cousin of the (deferred) test-gen "reviewed markdown" pattern, spent exactly where
  silent cross-user bugs hide.

The three detection tiers are chosen by loud-vs-silent, and the silent one is why the guaranteed tier
exists:

| Tier | Role | Guaranteed? |
|---|---|---|
| **grep list** | surface every hazard's candidate sites | ✅ always (zero-dependency) |
| **LSP** | semantic refinement (real-vs-false, transitive reach) | ⚠️ best-effort — never load-bearing (may be absent/stale/unwired) |
| **ArchUnit lint** | the guaranteed semantic check for the one silent hazard | ✅ runs in the build |

Grep alone can't do this job: the canonical leak is `static MainFrame FRAME` where `MainFrame extends
JFrame` — the declaration names `MainFrame`, not `JFrame`, so a name-based grep skips it, but ArchUnit
resolves the hierarchy from bytecode and flags it. The decision tree + worked examples live in the
migrator reference [static-fields.md](../guides/1-swing-to-emulators/static-fields.md);
rationale + rejected alternatives in [M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy).

## The fallback protocol — struggle-list, stuck-signal, islands

**The struggle-list** = difficulty × sensitivity tags **colocated on the §7 hazards + the static-field
tree** (no separate list — it would drift from §7). Phrased **escalate-on-signal**, never "these
always go to a human": a weaker model escalates more, a 256 GB / next-year model escalates less, and
**the list itself never needs editing** — only escalation frequency changes. Its per-item handoff doc
stays useful whoever got stuck (it isn't local-LLM-specific), which is what future-proofs it against
model improvement.

**The stuck-signal** — what tells the migrator to stop the local model and hand off — is anchored on
the stage's own concrete checkpoints, not a vibe:
1. compile-error count not monotonically decreasing across ~3 iterations, or the same file rewritten
   ~3× with no net progress;
2. doesn't boot;
3. WARN inventory not shrinking.

When it fires: stop, snapshot the diff, route the residue by the matrix.

### The "try" and islands

Held loosely, with two escape hatches — reshaped by the brainstorm *away* from a coverage budget:

1. **Revisit-and-relax (per step).** If a stage-2 step genuinely *requires* cloud-level intelligence,
   re-require cloud for that step. But the *default* handling of a hard step is escalate-to-human on
   the stuck-signal, so wholesale retargeting is rare.
2. **Islands are discovered, not budgeted.** No target coverage %. A weird app-specific fragment the
   local model can't port surfaces *in the field* via the stuck-signal, then routes to cloud (if
   secret-free) or a human (if sensitive). As local-LLM capability grows, islands shrink on their own
   — which is why we don't set them in stone.

## Out of scope

- **Stage-3 (surrogate rewrite) — fully deferred.** No faithfulness theorem exists for it (surrogates
  reproduce UI *functionality*, not API — R_vaadin_first), so it needs real validation regardless of model — which
  drags the deferred test-generation problem onto the critical path. Local LLM unsupported there by
  default; cloud or humans; decision postponed to [its own session](./migrating-to-surrogates.md).
  Nobody is locked out — stage-2 already delivers the running web app, so a privacy customer does
  stage-3 by hand, incrementally, on top.
- **Behavioral test generation** (capture off the desktop app, replay against the migrated one) —
  **cloud-level-intelligence-required** for now, and optional for every migration per
  [migrated-app-testing.md § "Decided"](./migrated-app-testing.md). Tests in general are not part of
  the local-LLM stage-2 target.

## Status & where it lives

The mechanical guardrails are built; each lives in its permanent home, this doc just points:

- **The import-swap tool** — `:migration-tool`, per
  [M1D_import_swap_tool](../migration/1-swing-to-emulators/decisions.md#M1D_import_swap_tool);
  migrator-facing invocation in
  [guide.md Phase 2](../guides/1-swing-to-emulators/guide.md). Unlike the other four this one
  *removes* a phase from the docs rather than gating it, which is why it also changes what a probe run
  measures — a Phase 2 stumble count now drops because the phase left the docs, not because the prose
  improved, so a re-run has to partition its count by phase and exercise the retained by-hand prose
  separately. Stated where a re-run is designed: `.claude/skills/guide-migrateapp/inventory.md` §"Designing
  a re-run".

- **Colocation tags** — [spec.md §7](../migration/1-swing-to-emulators/spec.md) (per-hazard `LLM:`
  difficulty × sensitivity + ⚠silent, behind a legend) carries the static sweep's overlay on its
  "Static frame singletons" + "Multi-tab" entries, and [M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy)'s
  detection-split paragraph states the mechanical-floor-vs-review-gate routing. The migrator-facing
  [static-fields.md](../guides/1-swing-to-emulators/static-fields.md) stays model-agnostic (no
  LLM tags), same as the guide/checklist; the overlay graduates there only if the target firms up
  past "try."
- **`Q_component` lint + `System.exit` ban** — `MigrationGuardrails` in
  [`:migration-guardrails`](../migration-guardrails), installed by
  [guide.md Phase 6 step 6](../guides/1-swing-to-emulators/guide.md) as a six-line test.
  Shipped as a **published test-scope artifact** since 2026-09-02
  ([M1D_guardrails_artifact](../migration/1-swing-to-emulators/decisions.md#M1D_guardrails_artifact)),
  which supersedes the earlier copy-paste-rules form — the discoverability bar this doc sets is what
  the fluent builder was measured against. Covers the direct/blanket cases; transitive / collection /
  session-attribute cases stay in the review-gate.
- **`hazardscan.HazardScan`** — the consolidated finder in [`:migration-tool`](../migration-tool),
  §7 `Detect:` lines + the §5 import sharp edges, in a table `HazardTableTest` joins bidirectionally
  with §7 by `H_` slug (M1D_hazard_scan_tool). A finder (exits 0), not a gate. Its report names each
  section's `H_` id, which is the hook an overlay column would hang on if `Q_llm_overlay_column`
  ever lands — held as loosely as the overlay itself.
- **Open:** the release-gated local-LLM probe (run a candidate model against `testapps/crud/swing`
  decoupled from sources, diff against the golden `1-emulators/`) — formerly tracked in `TODO.md`,
  the only remaining item.
