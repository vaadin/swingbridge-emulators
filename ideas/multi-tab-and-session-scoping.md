# Multi-tab & session scoping — where we are, and where we could go

**Status:** brainstorm / not decided. Captures the design space around the
one-app-per-session invariant while it's fresh (post-D_session_scoped_pools/D_callswing_loom/D_active_ui_pointer). No commitment;
a future design pass picks a direction.

**Maintainer-facing.** Assumes familiarity with [D_session_scoped_pools](../emulators/decisions.md#D_session_scoped_pools) (session-scoped Timer/SwingWorker), [D_callswing_loom](../emulators/decisions.md#D_callswing_loom) (session-scoped loom executor), [D_active_ui_pointer](../emulators/decisions.md#D_active_ui_pointer) (active-UI pointer + multi-tab option 1).

---

## 1. Where we are

### The invariant

SB-Emulators translates the desktop model "one JVM runs one Swing app" into "one **session** runs one app." A Swing app is a set of singletons, static registries (`JFrame.getFrames()`), a running EDT, timers, and background workers — all implicitly JVM-global. Mapping JVM-global → session-global is *almost* clean, and it's the invariant the whole runtime is built on:

- **Timer / SwingWorker pools** are session-scoped ([D_session_scoped_pools](../emulators/decisions.md#D_session_scoped_pools)).
- **Blocking-dialog parking** has no per-session pool any more: a parked UI fiber follows its anchor (the dialog's peer) to whatever UI it is attached to ([D_callswing_loom](../emulators/decisions.md#D_callswing_loom)).
- **`BrowserTimeZone`** is session-scoped; **Preferences** is browser-local (`localStorage`).
- **Static Swing registries** (`JFrame.getFrames()`) are **tab-scoped** ([D_window_registry](../emulators/decisions.md#D_window_registry), amending [D_single_ui_per_session](../emulators/decisions.md#D_single_ui_per_session)'s UI scope) — the first of this doc's option-3 predictions to land ahead of option 3 itself.

### The active-UI solution (D_active_ui_pointer)

The one place the "session = one app" invariant meets reality is **which UI the app is currently live in**, because an F5 `@PreserveOnRefresh` teleports the whole component tree from the old UI to a fresh one. We resolve this with an explicit **active-UI pointer**:

- `vaadinx.AppTab` — a session attribute holding the app's live UI (and its browser tab). `AppTab.markAppUI(ui)` binds it; `AppTab.forSession(session).liveUI()` reads it.
- `MainWindowRoute.onAttach` maintains it: first load and each F5 rebind bind the attaching UI; a *second tab* (fresh route instance while the app is already live) does **not** run a second copy — it renders `onAppAlreadyActive()` (a curtain).
- `EHelper.singleLiveUI(session)` resolves the `AppTab` first; Timer/SwingWorker delivery and blocking-dialog resume all follow the app across an F5 by reading it.

This is **multi-tab option 1**: one app per session, a second tab is redirected/curtained rather than running its own copy.

### Current limitations (accepted for alpha)

1. **A second tab can't use the app at all** — it only shows "already open in another tab." No way to *move* the app to the new tab or *run* it there.
2. **The curtain doesn't auto-recover.** If the active tab later closes, the curtained tab stays curtained until its next attach (manual refresh). No push/poll nudge. **Close-handling gotcha (see [D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle)):** the shutdown design makes a *real* tab-close end in `session.close()`, so the common path self-heals — session gone → new tab gets a fresh app. What survives is the **transient window** between the real close and the grace-delayed `session.close()`: the session is briefly alive with the active-UI pointer still aimed at the dead UI, so a new tab opened *inside that window* is falsely curtained against an app that's already gone. Resolving it (a stale-pointer / curtain-recovery nudge) belongs to this doc's design pass, since it's the curtain's own recovery story — not the shutdown path's.
3. **No "take over here."** Options 2/3 of the original sketch (teleport-here / restart-here) aren't built; `onAppAlreadyActive` is a static message.
4. **Genuinely concurrent tabs are unsupported by construction.** Two live app instances in one session would share all the session-scoped singletons (executor, timer pools, static registries) — so we forbid it rather than corrupt state.

The **testing caveat** (mostly closed): before karibu-testing 2.7.1, Karibu's `page.reload()` didn't faithfully model Flow's F5 lifecycle (no overlay teleport, no transient two-UI window). karibu-testing 2.7.1 ([karibu-testing#207](https://github.com/mvysny/karibu-testing/issues/207), PR #208) closed that — `page.reload()` now teleports overlays and reproduces the two-live-UI window, so the F5 lifecycle is validatable in-test. Only genuinely concurrent multi-tab behaviour (item 4 above) still needs a real browser.

### The migrator-facing half

This doc is about *our* scoping. The migrator has a mirror-image problem in *their* code, and it is the reason the two can't be designed apart: one JVM now serves every tab and every user, so their in-process state (collections in `static` fields, a file handle cached at startup, a "current logged-in user" reference) is per-JVM where the desktop made it per-app-instance — the inversion of the assumption a business app is built on.

That half is **settled for today's invariant**: it is the static sweep, ruled at [M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy) with the migrator-facing tree in [`static-fields.md`](../guides/1-swing-to-emulators/static-fields.md), and stated as a hazard at [spec.md `H_multi_tab`](../migration/1-swing-to-emulators/spec.md#H_multi_tab). Three positions were weighed before D_active_ui_pointer picked one — *document and warn* (spec notes the per-tab behaviour, migrator handles divergence case-by-case), *prevent* (deny/curtain a second tab), and *allow with an explicit shared-state helper* (ship a session-scoped registry the migrator opts into). We took **prevent**; the third is what option 3 below would need, and §5's last bullet is where its app-scope verdicts get re-opened.

---

## 2. The core tension

The web gives **N tabs per session**; the desktop model assumes **one app instance**. Every session-scoped singleton is correct *only* while there is one app. The moment a second tab wants to participate, we must answer: does the second tab get the *same* app (move/share) or its *own* app (re-create), and if its own — what is the correct scope for the "singletons" that were session-scoped?

Three families of answers.

---

## 3. Option 1 — Teleport components to the destination tab (preserve state)

**Idea.** Keep one app instance, but let a second tab *claim* it: move the live component tree (and the active-UI pointer) from the currently-active UI onto the requesting tab's UI, preserving all component state. The old tab goes to a "moved to another tab" curtain.

This is exactly what Flow's `@PreserveOnRefresh` does on F5 (`UIInternals.moveElementsFrom`) — but triggered **on demand, cross-tab**, instead of automatically on same-tab refresh.

**Mechanics.**
- `onAppAlreadyActive()` offers a "Open here" action. Clicking it, on the new UI, calls something like `newUI.getInternals().moveElementsFrom(AppTab.forSession(session).liveUI())`, re-binds the `AppTab` (`markAppUI`), and curtains the old UI.
- Session-scoped machinery (executor, timer/worker pools) is untouched — it already resolves the live UI lazily via the pointer (D_session_scoped_pools/D_callswing_loom), so it follows the move for free. **This is the big win: we already did the hard part.**

**Pros.**
- State-preserving — the user keeps their exact app state (open dialogs, form contents, selection).
- Reuses the D_active_ui_pointer pointer + D_session_scoped_pools/D_callswing_loom lazy resolution; minimal new machinery.
- Keeps the one-app invariant fully intact — still exactly one live app, it just relocates.

**Cons / risks.**
- Cross-UI element move is the same operation Flow only does under the tightly-controlled F5 path; doing it on demand may hit Flow guardrails (`StateNode` "can't move a node from one state tree to another" unless removed first — the `ReplacedViaPreserveOnRefresh` escape hatch is F5-specific). Needs a spike to confirm Flow allows/tolerates a manual cross-UI move.
- The old tab is now dead weight (curtained). Fine, but the UX of "your app jumped to the other tab" can surprise.
- Blocking-dialog continuations parked on the *old* UI's VT: the executor is session-scoped so it survives, and `awaitModal` rebinds `UI.getCurrent()` on resume — but the *overlay* (dialog DOM) must move with the tree. That's the same overlay-teleport path, so it should ride along; now unit-testable since karibu-testing 2.7.1 ([karibu#207](https://github.com/mvysny/karibu-testing/issues/207) / PR #208) teleports overlays across `page.reload()` — `BlockingDialogF5Test`'s `JOptionPane`-across-F5 E2E exercises exactly this.
- **A cross-tab move silently loses — and then *splits* — the app-instance store.** Everything in `AppInstance` (the D_window_registry `WindowRegistry`, every migrated-app former-singletons holder) lives in the *old* tab's `TabScope`, keyed by that tab's window name. F5 keeps the window name; a cross-tab teleport does not. Worse than losing it: after the move, `AppInstance.values()` resolves the *new* tab's fresh `Attributes` and starts re-registering it via `AppTab.rememberValues` — so half the state answers empty while new writes land in the new store, a silent split rather than a clean loss. Any "Open here" implementation must migrate the `Attributes` (copy or repoint) as part of the move, in the same breath as `markAppUI`.

---

## 4. Option 2 — Re-create components in the destination tab

**Idea.** Don't move anything; re-run the app's entry (`mainUI()` / `bootstrap()`) in the destination tab to build a *fresh* component tree there, and retire the old tab.

**Mechanics.**
- `onAppAlreadyActive()` offers "Restart here." Clicking it re-runs `bootstrap()` on the new UI, points the active-UI pointer at it, and curtains/closes the old tab.
- Still one app instance at a time (the old one is discarded), so session-scoped singletons remain valid — but their *state* is now out of sync with a freshly-built tree (a timer that was counting, a worker mid-flight, static registries holding the old tree's frames).

**Pros.**
- Simpler than a cross-UI move — no Flow element-tree surgery; just runs the normal entry path.
- No dependence on `moveElementsFrom` guardrails.

**Cons / risks.**
- **State is lost** — the user's open dialogs, unsaved form data, selection are gone. For a migrated line-of-business app this is often unacceptable.
- **Singleton/tree desync.** Session-scoped singletons (timer pools, static `JFrame.getFrames()`, app-level model singletons) still reference or were populated by the *old* tree. Re-running `bootstrap()` may double-register, leak, or resurrect stale state unless the app's entry is idempotent — which migrated Swing `main()` methods generally are **not**.
- Really a special case of "restart the app," which fights the whole point of `@PreserveOnRefresh` (preserve, don't rebuild).

**Verdict sketch.** Cheapest to build, worst for the user; probably only viable as a fallback when a move fails.

---

## 5. Option 3 — Relax the invariant: allow genuinely concurrent tabs

**Idea.** Drop "one app per session." Let each tab run its own app instance concurrently. This is the biggest change and needs its own brainstorming session; the point here is to enumerate **what breaks and what scope each broken thing needs.**

### The scoping problem is the whole problem

Everything session-scoped assumed one app. With N concurrent apps per session, each session-scoped singleton must be re-scoped to **one app instance**. The candidate scopes:

| Scope | Survives F5? | Distinct per tab? | Verdict |
|---|---|---|---|
| **Session** | ✅ | ❌ (shared across tabs) | today's scope — wrong granularity for multi-tab |
| **UI** | ❌ (destroyed on F5) | ✅ | too short-lived — this is exactly the D_session_scoped_pools orphan bug |
| **Tab** (window-name-keyed) | ✅ (survives F5 same-tab) | ✅ | **the missing primitive** |

The key realization: **UI scope is not the answer** even though it's "per tab," because a UI is destroyed and recreated on every F5 (that's what D_session_scoped_pools/D_active_ui_pointer spend all their effort surviving). What we'd want for multi-tab is a **tab scope**: keyed by the browser *window name* (stable across F5 within the same tab, distinct across tabs), i.e. the same key `@PreserveOnRefresh`'s preserved-chain cache already uses. This is the "app instance" scope.

**Action item:** evaluate <https://github.com/mvysny/vaadin-tab-scope-example> as a ready-made tab-scoping primitive. If it gives a clean "per-tab, F5-surviving" scope, option 3 becomes "re-scope the session-scoped machinery onto it."

### What breaks, and where it would move

- **Blocking-dialog parking** (D_callswing_loom) — nothing to move: a parked UI fiber has no pool, and resumes on whatever UI its anchor (the dialog's peer) is attached to, which is already per app instance.
- **Timer / SwingWorker pools** (D_session_scoped_pools) — session-scoped. → tab-scoped. `onSessionLiveUI` → "on this app-instance's live UI."
- **Active-UI pointer** (D_active_ui_pointer) — session attribute. → one pointer per tab scope (trivially subsumed: tab scope *is* the per-tab identity).
- **Static Swing registries** (`JFrame.getFrames()`) — ✅ **done, [D_window_registry](../emulators/decisions.md#D_window_registry)**. Tab-scoped, exactly as predicted here and for the reason given: they must survive F5 but stay per-app-instance, and UI scope loses them on F5. Landed early because the Window/Frame/JFrame call-graph audit needed a real registry for its own reasons; the scoping choice was already settled by this row. Worth noting the extra argument the audit turned up: AWT's own public registry is a per-`AppContext` attribute map, not a JVM static, so tab scope *replicates* it rather than approximating it.
- **`BrowserTimeZone`** — session-scoped, but genuinely machine-global; can stay session-scoped (or tab, harmless).
- **Preferences** — `localStorage`, browser-global; shared across tabs by design (matches desktop "same user prefs"). Leave as-is.
- **Clipboard / DnD** — per-interaction / per-UI; largely fine, revisit.
- **EXIT_ON_CLOSE / session-close semantics** — currently "close the session." With N apps per session, "exit" must mean "close *this* app instance / tab," not log out every tab. This is the ambiguity flagged in `emulators/decisions.md` (D_close_operation_dispatch EXIT_ON_CLOSE → session-close, D_active_ui_pointer one-app-per-session) — resolving option 3 forces resolving it.
- **Static/singleton *migration* rules** — the migrator's own `static` app state (caches, singletons, `static` component refs) is the deeper hazard: with concurrent apps in one JVM/session, user-code statics leak across tabs. This landed as the static-sweep taxonomy — [M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy) (rationale) + migrator reference [`static-fields.md`](../guides/1-swing-to-emulators/static-fields.md) (decision tree + worked examples). It targets today's one-app-per-session reality (tab ≈ session); the *application-scope* verdicts (shared cache, monotonic counter) are the piece *tightly coupled* to option 3 — a per-tab app instance would re-open what "shared across the session" means, so re-examine them here if option 3 is ever taken.

### Pros / cons

**Pros.** True multi-window feel (a Swing user can have several app windows = several tabs). No curtain. Matches power-user expectations.

**Cons / risks.**
- Largest blast radius — touches every session-scoped subsystem.
- User-code statics become a correctness hazard across tabs (can't be fixed by SB-Emulators alone; needs migration rules + possibly classloader isolation, which we've ruled out).
- More surface for subtle cross-tab state bleed; harder to test (needs the tab-scope primitive + real-browser multi-tab tests).

---

## 6. Cross-cutting notes

- **Option 1 is the cheapest *good* option** given what we already built — the D_active_ui_pointer pointer + D_session_scoped_pools/D_callswing_loom lazy resolution mean "move the app to another tab" is mostly "call `moveElementsFrom` + repoint," if Flow tolerates the on-demand cross-UI move. Worth a spike first.
- **Option 3 is the "right" long-term answer** if SB-Emulators ever targets genuinely multi-window apps, but it's a large, multi-slice effort gated on a solid tab-scope primitive and the static-singleton migration rules.
- **Option 2 is the fallback**, not a primary choice — state loss + singleton desync make it a poor default.
- The single-UI **F5/teleport path is now testable in Karibu** — karibu-testing 2.7.1 ([karibu#207](https://github.com/mvysny/karibu-testing/issues/207) / PR #208) reproduces the overlay teleport and the transient two-UI window. What still needs a real browser (or the deferred real-browser e2e harness) is genuinely **concurrent multi-tab** DOM behaviour — two live UIs in one session at once, which Karibu doesn't model — so the on-demand cross-UI move in Options 1/3 still wants a real-browser spike.

## 7. Open questions for the design pass

1. Does Flow permit an **on-demand** `moveElementsFrom` between two live UIs outside the F5 path? (Option 1 spike.)
2. Does <https://github.com/mvysny/vaadin-tab-scope-example> give a clean F5-surviving per-tab scope we can hang the executor/timer/worker pools on? (Option 3 enabler.)
3. What should **EXIT_ON_CLOSE** mean under multi-tab — close the tab, or the session? (Forces the D_gap_severity_triage-case-2 final shape.)
4. What are the **migration rules for user-code statics** under concurrent tabs, and can we detect violations? (Coupled to option 3.)
5. Is state-preserving move (opt 1) enough for the target apps, or is concurrent multi-window (opt 3) a real requirement for the JLawyer-class targets?
