# Migration

Home for all migration-domain thinking — specs, ideas and decisions. **Maintainer-facing throughout**: the migrator-facing guides they feed live one level up, in [`../guides/`](../guides/). Covers the two transitions in scope (Swing→emulators, emulators→surrogates) and references the out-of-scope endpoint (surrogates→Vaadin). The migration arc overview lives in [`../CLAUDE.md`](../CLAUDE.md) §"Migration arc"; this folder is where the per-step thinking happens.

## Structure

```
migration/                              ← maintainer-facing: how we worked it out
├── README.md                           ← this file
├── 1-swing-to-emulators/               ← step 1 sources (current focus)
│   ├── spec.md                         ← the worked-out brainstorm
│   ├── testing.md                      ← the guide-loop protocol over the guides
│   └── decisions.md                    ← ADR-lite log, M1D* IDs
└── 2-emulators-to-surrogates/          ← step 2 (later)

guides/                                 ← migrator-facing, hand-authored from the sources
└── 1-swing-to-emulators/
    ├── guide.md                        ← the procedure: six phases of tickable steps, nothing else
    ├── static-fields.md                ← the `static` sweep: scopes, decision tree, catalogue
    ├── former-singletons.md            ← the tab-scoped holder the sweep routes fields into
    ├── lifecycle.md                    ← start-up hooks in run order; how the app ends
    ├── import-swap-reference.md        ← the swap table in prose, for reviewing the tool's diff
    ├── dates.md                        ← the three date/time buckets
    ├── build-wiring.md                 ← bootstrap choice, dependencies, plugins, JVM flags
    ├── host-app-*.md                   ← the host app per bootstrap: its seed, running, packaging
    ├── seed/<bootstrap>/               ← that host app as real files a migrator copies
    ├── example-pom.xml                 ← a working migrated-app pom, for the migrator to copy
    ├── example-build.gradle.kts        ← the same for Gradle
    ├── runtime-contract.md             ← what WARNs, what throws
    ├── third-party-libraries.md        ← triaging a Swing dependency with no add-on
    ├── platform.md                     ← OS checks, shell-outs, native code
    ├── addons.md                       ← index of third-party library add-ons
    └── agent-prompt.md                 ← the migration prompt an agent is handed

The guide is the only document with `- [ ]` steps: the migrator copies the folder into their app and
ticks there, so there is no second tick-list to drift against it (a `checklist.md` was that second
copy until 2026-09-10, and had drifted in four places).
```

Step folders are the **sources** — internal scratch where we work things out together. `../guides/` is the **migrator-facing** tree, hand-authored from the sources, **not built**. The two trees are siblings at the repo root rather than nested, because the persona split is the top-level fact about them and because `guides/` ships verbatim in the distribution kit at that very path (D_distribution_zip) — a doc under `guides/` may only link to what the kit also ships.

**Runnable tools are not artifacts of either tree.** Step 1's two mechanical phases each have one, and both live in the [`:migration-tool`](../migration-tool) reactor module — `importswap.ImportSwap` (Phase 2) and `hazardscan.HazardScan` (Phase 1), each driven by a tab-separated table it reads off its classpath. `guides/` holds the prose that invokes them. The hazard scan used to be a checked-in `hazard-scan.sh` here; it moved so its patterns could have one home that a test joins against spec.md §7 (M1D_hazard_scan_tool).

Per-step brainstorming is **deliberately split, not shared**: step 2 builds on a finished step 1, the problems differ, and focus wins over cross-step DRY. Revisit only if real repetition emerges.

**Loose ideas do not live here — they live in [GitHub issues](https://github.com/vaadin/swingbridge-emulators/issues) labelled `enhancement`, one issue per idea.** A step folder holds only the *worked-out* material: `spec.md` (settled understanding), `decisions.md` (ruled, with rationale), `testing.md` (the protocol). Step 1 originally carried an `ideas.md` too; it was split per idea and retired, and step 2 gets no equivalent. One home for ideas is the point — two drift, and an idea that graduates has one place to be closed in.

## Steps

Numbered after the migration arc:

1. **swing-to-emulators** (current focus) — mechanical import-swap (`javax.swing.*` → `vaadinx.swing.*`) and recompile against `:emulators`. Code shape stays Swing-shaped.
2. **emulators-to-surrogates** (later) — view-by-view rewrite of `:emulators` views to `:surrogates`. Code shape becomes Vaadin-shaped.
3. **surrogates-to-vaadin** (out of project scope) — final rewrite from `:surrogates` toward stock Vaadin components.

## Decision ID convention

Each step's `decisions.md` uses prefix `M<step>D<n>` — `M1D_import_swap`, `M1D_runtime_contract`, … for step 1; `M2D1`, … for step 2. IDs never renumber or reuse; superseded entries shrink to a one-line pointer at the superseding ID. Same conventions as `:emulators/decisions.md` (`D*` IDs) and `:surrogates/decisions.md` (`SD*` IDs).

**Cross-refs use bare prefixed IDs.** Each prefix is uniquely sourced project-wide — `D*` from `:emulators/decisions.md`, `SD*` from `:surrogates/decisions.md`, `R*` from CLAUDE.md "Hard rules", `M<step>D*` from this folder — so refs in prose appear unqualified (`see D_never_fail_on_gaps`, `per R_match_swing_errors`, `M1D_mainwindow_split covers this`). Document and section references (architecture.md, README files) keep their file links.
