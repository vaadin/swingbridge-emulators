# Multi-tab & session scoping — where we are, and where we could go

**Status:** brainstorm / not decided. Captures the design space around the
one-app-per-session invariant while it's fresh (post-D_session_scoped_pools/D_callswing_loom/D_active_ui_pointer). No commitment;
a future design pass picks a direction.

**Maintainer-facing.** Assumes familiarity with [D_session_scoped_pools](../emulators/decisions.md#D_session_scoped_pools) (session-scoped Timer/SwingWorker), [D_callswing_loom](../emulators/decisions.md#D_callswing_loom) (UI fibers), [D_active_ui_pointer](../emulators/decisions.md#D_active_ui_pointer) (active-UI pointer + the second-tab curtain, which that entry calls "multi-tab option 1" in a numbering of its own; the options below are numbered afresh).

---

## 1. Where we are

SB-Emulators maps the desktop's "one JVM runs one Swing app" onto "one **session** runs one app", and a second tab opening the app is curtained by `MainWindowRoute.onAppAlreadyActive()` rather than run — [D_active_ui_pointer](../emulators/decisions.md#D_active_ui_pointer), which also records the positions weighed against it. Timer / SwingWorker pools are session-scoped and follow the app across an F5 through the `AppTab` pointer ([D_session_scoped_pools](../emulators/decisions.md#D_session_scoped_pools)); a parked UI fiber follows its dialog's peer ([D_callswing_loom](../emulators/decisions.md#D_callswing_loom)); app-instance state — the window registry, a migrated app's former singletons — lives in `vaadinx.AppInstance`, tab-scoped over [vaadin-tab-scope](https://github.com/mvysny/vaadin-tab-scope) ([D_window_registry](../emulators/decisions.md#D_window_registry), [D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle)). The migrator's mirror-image problem — their own `static` state is per-JVM where the desktop made it per-app-instance — is settled for today's invariant by the static sweep ([M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy)), and is the reason the two halves cannot be designed apart.

### Current limitations (accepted for alpha)

1. **A second tab can't use the app at all** — it only shows "already open in another tab." No way to *move* the app to the new tab or *run* it there.
2. **The curtain doesn't auto-recover.** If the active tab later closes, the curtained tab stays curtained until its next attach (manual refresh). No push/poll nudge. A real tab close ends in `session.close()` ([D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle)), so the common path self-heals; what survives is the **transient window** between the real close and the grace-delayed `session.close()`, when a new tab is falsely curtained against an app that's already gone. Resolving it (a stale-pointer / curtain-recovery nudge) belongs to this doc's design pass, since it's the curtain's own recovery story — not the shutdown path's.
3. **No "take over here."** Neither teleport-here nor restart-here (§3 / §4 below) is built; `onAppAlreadyActive` is a static message.
4. **Genuinely concurrent tabs are unsupported by construction.** Two live app instances in one session would share all the session-scoped singletons (timer pools, worker pools, the `AppTab` pointer) — so we forbid it rather than corrupt state. Karibu (since 2.7.1) models the F5 lifecycle faithfully, but not two live UIs at once, so this still needs a real browser to test.

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
- Session-scoped machinery (timer/worker pools) is untouched — it already resolves the live UI lazily via the pointer (D_session_scoped_pools), and a parked UI fiber follows its anchor (D_callswing_loom), so both follow the move for free. **This is the big win: we already did the hard part.**

**Pros.**
- State-preserving — the user keeps their exact app state (open dialogs, form contents, selection).
- Reuses the D_active_ui_pointer pointer + D_session_scoped_pools/D_callswing_loom lazy resolution; minimal new machinery.
- Keeps the one-app invariant fully intact — still exactly one live app, it just relocates.

**Cons / risks.**
- Cross-UI element move is the same operation Flow only does under the tightly-controlled F5 path; doing it on demand may hit Flow guardrails (`StateNode` "can't move a node from one state tree to another" unless removed first — the `ReplacedViaPreserveOnRefresh` escape hatch is F5-specific). Needs a spike to confirm Flow allows/tolerates a manual cross-UI move.
- The old tab is now dead weight (curtained). Fine, but the UX of "your app jumped to the other tab" can surprise.
- Blocking-dialog continuations parked on the *old* UI: a parked fiber resumes on whatever UI its anchor is attached to — but the *overlay* (dialog DOM) must move with the tree. That's the same overlay-teleport path as F5, so it should ride along; `BlockingDialogF5Test`'s `JOptionPane`-across-F5 test exercises the F5 version.
- **A cross-tab move silently loses — and then *splits* — the app-instance store.** Everything in `AppInstance` (the D_window_registry `WindowRegistry`, every migrated-app former-singletons holder) lives in the *old* tab's `TabScope`, keyed by that tab's window name. F5 keeps the window name; a cross-tab teleport does not. Worse than losing it: after the move, `AppInstance.values()` resolves the *new* tab's fresh `Attributes` and starts re-registering it via `AppTab.rememberValues` — so half the state answers empty while new writes land in the new store, a silent split rather than a clean loss. Any "Open here" implementation must migrate the `Attributes` (copy or repoint) as part of the move, in the same breath as `markAppUI`.

---

## 4. Option 2 — Re-create components in the destination tab

**Idea.** Don't move anything; re-run the app's entry (`mainUI()` / `bootstrap()`) in the destination tab to build a *fresh* component tree there, and retire the old tab.

**Mechanics.**
- `onAppAlreadyActive()` offers "Restart here." Clicking it re-runs `bootstrap()` on the new UI, points the active-UI pointer at it, and curtains/closes the old tab.
- Still one app instance at a time (the old one is discarded), so session-scoped singletons remain valid — but their *state* is now out of sync with a freshly-built tree (a timer that was counting, a worker mid-flight).

**Pros.**
- Simpler than a cross-UI move — no Flow element-tree surgery; just runs the normal entry path.
- No dependence on `moveElementsFrom` guardrails.

**Cons / risks.**
- **State is lost** — the user's open dialogs, unsaved form data, selection are gone. For a migrated line-of-business app this is often unacceptable.
- **Singleton/tree desync.** Session-scoped singletons (timer pools, worker pools, app-level singletons the migrator left session- or JVM-wide) still reference or were populated by the *old* tree. Re-running `bootstrap()` may double-register, leak, or resurrect stale state unless the app's entry is idempotent — which migrated Swing `main()` methods generally are **not**.
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
| **Tab** (window-name-keyed) | ✅ (survives F5 same-tab) | ✅ | the app-instance scope |

The key realization: **UI scope is not the answer** even though it's "per tab," because a UI is destroyed and recreated on every F5 (that's what D_session_scoped_pools/D_active_ui_pointer spend all their effort surviving). What multi-tab needs is a **tab scope**: keyed by the browser *window name* (stable across F5 within the same tab, distinct across tabs). That primitive exists already — `vaadinx.AppInstance` over vaadin-tab-scope, which the window registry and migrated apps' former singletons use — so option 3 becomes "re-scope the remaining session-scoped machinery onto it."

### What breaks, and where it would move

- **Blocking-dialog parking** (D_callswing_loom) — nothing to move: a parked UI fiber has no pool, and resumes on whatever UI its anchor (the dialog's peer) is attached to, which is already per app instance.
- **Timer / SwingWorker pools** (D_session_scoped_pools) — session-scoped. → tab-scoped. `onSessionLiveUI` → "on this app-instance's live UI."
- **Active-UI pointer** (D_active_ui_pointer) — session attribute. → one pointer per tab scope (trivially subsumed: tab scope *is* the per-tab identity).
- **Static Swing registries** (`JFrame.getFrames()`) — already tab-scoped ([D_window_registry](../emulators/decisions.md#D_window_registry)); nothing to move.
- **`BrowserTimeZone`** — session-scoped, but genuinely machine-global; can stay session-scoped (or tab, harmless).
- **Preferences** — `localStorage`, browser-global; shared across tabs by design (matches desktop "same user prefs"). Leave as-is.
- **Clipboard / DnD** — per-interaction / per-UI; largely fine, revisit.
- **EXIT_ON_CLOSE / session-close semantics** — currently "close the session." With N apps per session, "exit" must mean "close *this* app instance / tab," not log out every tab. This is the ambiguity flagged in `emulators/decisions.md` (D_close_operation_dispatch EXIT_ON_CLOSE → session-close, D_active_ui_pointer one-app-per-session) — resolving option 3 forces resolving it.
- **Static/singleton *migration* rules** — the migrator's own `static` app state is the deeper hazard: with concurrent apps in one JVM/session, user-code statics leak across tabs. The static sweep ([M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy), migrator reference [`static-fields.md`](../guides/1-swing-to-emulators/static-fields.md)) targets today's one-app-per-session reality (tab ≈ session); its *application-scope* verdicts (shared cache, monotonic counter) are the piece *tightly coupled* to option 3 — a per-tab app instance would re-open what "shared across the session" means, so re-examine them here if option 3 is ever taken.

### Pros / cons

**Pros.** True multi-window feel (a Swing user can have several app windows = several tabs). No curtain. Matches power-user expectations.

**Cons / risks.**
- Largest blast radius — touches every session-scoped subsystem.
- User-code statics become a correctness hazard across tabs (can't be fixed by SB-Emulators alone; needs migration rules + possibly classloader isolation, which we've ruled out).
- More surface for subtle cross-tab state bleed; harder to test (needs real-browser multi-tab tests).

---

## 6. Cross-cutting notes

- **Option 1 is the cheapest *good* option** given what we already built — the D_active_ui_pointer pointer + D_session_scoped_pools/D_callswing_loom lazy resolution mean "move the app to another tab" is mostly "call `moveElementsFrom` + repoint," if Flow tolerates the on-demand cross-UI move. Worth a spike first.
- **Option 3 is the "right" long-term answer** if SB-Emulators ever targets genuinely multi-window apps, but it's a large, multi-slice effort gated on re-scoping the session-scoped machinery and on the static-singleton migration rules.
- **Option 2 is the fallback**, not a primary choice — state loss + singleton desync make it a poor default.
- **Concurrent multi-tab needs a real browser to test.** Karibu models the single-UI F5 teleport (overlays and the transient two-UI window, since karibu-testing 2.7.1), but not two live UIs in one session at once — so the on-demand cross-UI move in Options 1/3 still wants a real-browser spike.

## 7. Open questions for the design pass

1. Does Flow permit an **on-demand** `moveElementsFrom` between two live UIs outside the F5 path? (Option 1 spike.)
2. Can the Timer/SwingWorker pools and the active-UI pointer move from session scope onto `AppInstance`'s tab scope cleanly? (Option 3 enabler.)
3. What should **EXIT_ON_CLOSE** mean under multi-tab — close the tab, or the session? (Forces the D_gap_severity_triage-case-2 final shape.)
4. What are the **migration rules for user-code statics** under concurrent tabs? (Coupled to option 3. Detection exists for today's rules — `StaticSweep` and `MigrationGuardrails` — and would carry the new verdicts.)
5. Is state-preserving move (opt 1) enough for the target apps, or is concurrent multi-window (opt 3) a real requirement for the JLawyer-class targets?
