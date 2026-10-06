# Local-LLM migration target — design emulators so a local model can do the swing→emulators port

**Status:** a **"try,"** held loosely (see [*The "try" and islands*](#the-try-and-islands)) — not a
hard commitment, but scoped and partly built. Brainstormed 2026-07-16; the target has a concrete
shape (a struggle-list + escalation protocol, **not** an eval). Its mechanical guardrails are built
and live in their permanent homes (see [*What is built*](#what-is-built)); what is open is the
target itself, still to be brainstormed in a dedicated session, and the release-gated probe.

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
4. **The grep list** — one runnable finder (`HazardScan`) unioning every §7 `Detect:` line, grouped by hazard.
   Converts "remember to look for the FQN / `instanceof` edges" into "run these N greps." It's
   **complete over precise**: accept false positives (a comment match, dismissed in seconds); a false
   *negative* on a silent edge ships a bug invisibly.
5. **A tool that owns a whole phase** — the strongest form of the lever, and the first phase to reach
   it: the import swap is now `:migration-tool`, not prose the agent applies. **This is the target's
   best evidence so far, and it is measured rather than argued**: Phase 2 is judgement-free
   ([M1D_import_swap_tool](../migration/1-swing-to-emulators/decisions.md#M1D_import_swap_tool) §
   Status has the graded-tree comparison). Phases 1 and 3–6 do not move. For this target the win is
   that a model too small to be trusted with 60 lines of shadowing rules now needs none of it.

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
no capability makes a silent verdict safe unreviewed): a build-time lint as the mechanical floor, plus
a human review-gate over the finite per-field verdict table. That handling is decided and built — the
detection split in [M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy),
the lint in [M1D_guardrails_artifact](../migration/1-swing-to-emulators/decisions.md#M1D_guardrails_artifact),
the migrator reference [static-fields.md](../guides/1-swing-to-emulators/static-fields.md).

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

## What is built

The mechanical guardrails, each in its permanent home:

- **The import-swap tool** — [M1D_import_swap_tool](../migration/1-swing-to-emulators/decisions.md#M1D_import_swap_tool).
- **Colocation tags** — the per-hazard `LLM:` overlay behind a legend in
  [spec.md §7](../migration/1-swing-to-emulators/spec.md). The migrator-facing
  [static-fields.md](../guides/1-swing-to-emulators/static-fields.md) stays model-agnostic (no LLM
  tags), same as the guide; the overlay graduates there only if the target firms up past "try."
- **`Q_component` lint + `System.exit` ban** — `MigrationGuardrails`,
  [M1D_guardrails_artifact](../migration/1-swing-to-emulators/decisions.md#M1D_guardrails_artifact).
- **The grep list** — `HazardScan`,
  [M1D_hazard_scan_tool](../migration/1-swing-to-emulators/decisions.md#M1D_hazard_scan_tool), whose
  `Q_llm_overlay_column` is held as loosely as the overlay itself.

## Open

- **The target itself** — undecided; to be brainstormed in a dedicated session.
- **The release-gated local-LLM probe**: run a candidate model against `testapps/crud/swing`
  decoupled from sources, and diff against the golden `1-emulators/`.
