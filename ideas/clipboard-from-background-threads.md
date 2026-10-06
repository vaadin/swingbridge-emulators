# Clipboard access from a background thread

**Status:** open design, nothing implemented. Opened 2026-09-18 out of a sweep of error messages that
told migrators to reach for `UI.access(...)`. The messages were fixed
(`4f0cdf2`, `18eacfc`); the clipboard turned out to be the one site where the message was not the
problem. §2 is what the code does today, read rather than measured; §3 is the proposed shape; §4 is
three findings that make it bigger than a guard swap; §5 is what has to be decided first.

**Maintainer-facing.** Assumes [R_tolerate_off_ui_thread](../CLAUDE.md#hard-rules),
[R_decline_effect_only](../CLAUDE.md#hard-rules),
[D_clipboard](../emulators/decisions.md#D_clipboard) and its sub-entries,
[D_sync_ui_hop](../emulators/decisions.md#D_sync_ui_hop),
[D_no_context_throws](../emulators/decisions.md#D_no_context_throws) and
[D_preferences](../emulators/decisions.md#D_preferences).

Sibling: [D_modal_from_background](../emulators/decisions.md#D_modal_from_background) settled the same
family one level up — R_match_swing_errors case (8) now says what a blocked-on-the-browser caller does
when the browser is gone (`BrowserSessionClosedError`, thrown before the park rather than after), and
the clipboard is a member of that family rather than a separate problem.

---

## 1. The claim

**A migrated app that copies to the clipboard from a `SwingWorker` — or from its own wrapped
executor — throws today, and the machinery to make it work already exists and is already used by
`java.util.prefs`.**

"Copy this report to the clipboard" inside a `doInBackground` is ordinary Swing. On the desktop it
works. R_tolerate_off_ui_thread commits stage 2 to coping with exactly this, and
[D_preferences](../emulators/decisions.md#D_preferences) already solved the identical problem for a
different browser-backed resource.

## 2. What the code does today

Two guards, both in `WebClipboard.assertUiAndVt`, applied to every op:

- **no current UI → throw.** `Toolkit.getSystemClipboard()` (`Toolkit.java:157`) throws the same way
  one level earlier, because the instance is keyed per UI via `ComponentUtil.setData(ui, …)` and the
  helper JS is injected into that UI's `Page` on first access
  ([D_clipboard_helper_js](../emulators/decisions.md#D_clipboard_helper_js)).
- **non-virtual thread → throw.** Both legs park on the `executeJs` promise —
  `setContents` and `getContents`, both through `awaitJs` → `EHelper.awaitBrowserRoundTrip`
  ([D_clipboard_vt_park](../emulators/decisions.md#D_clipboard_vt_park)).

**The rationale D_clipboard_vt_park gives for the guards is the assumption that has since been
retired**: *"clipboard ops in real apps run from button-click ActionListeners and menu handlers, both
of which spawn a VT via `callSwing`"*. That is exactly what R_tolerate_off_ui_thread stopped
assuming.

**Contrast with `java.util.prefs`, which has the same shape and does not throw.** The routing happens
at the *handle*: `VaadinPreferencesFactory.userRoot()` hands a background caller carrying a
context a `BridgedPreferences`, which wraps every read and write in `EHelper.callOnLiveUISync`. `PrefsCache.current()`'s UI guard sits on the other
branch — session current *and* locked, i.e. a UI thread — so a worker never reaches it. One family,
two answers, and that is the finding more than the throw itself.

**Worth noting that Preferences solved the handle problem by returning a different implementation per
caller context, and the clipboard probably cannot copy that.** `Toolkit.getSystemClipboard()` hands
back one `Clipboard` that app code holds and reuses, so a per-call-site type does not survive being
cached in a field — which is why §3 branches inside one instance instead. (`Preferences.userRoot()`
cached in a `static` has the same hole; the stage-2 static sweep is what covers it there.)

**Read, not measured.** No test in the repo calls the clipboard from a `doInBackground`
(`Q_reproduce_clipboard`) — confirm the throw before building on this reading, the same debt the
modal sibling carries as `Q_reproduce_first`.

## 3. The proposed shape

Same destination as Preferences, reached by a different route for the handle reason just noted —
one instance that branches per op, rather than a different implementation per caller context:

1. **`Toolkit.getSystemClipboard()` loses its UI guard.** `java.awt.Toolkit` imposes no threading
   requirement for *obtaining* a clipboard — the round-trip is on the ops — so the guard is
   unfaithful as well as inconvenient. The instance moves to **session scope**, resolved through
   `EmulatorContext.getOrNull().session()`. No context at all → throw naming
   `EmulatorContext.wrap(...)`, reusing D_no_context_throws' message shape.
2. **Helper-JS injection moves from handout to op** — `ui.getPage().addJavaScript(...)` inside the
   body, where a UI is current. `addJavaScript` is idempotent per `Page`, which D_clipboard_helper_js
   already relies on. Not merely tidiness: a session-scoped instance that injected at handout would
   never inject into the UI a `@PreserveOnRefresh` teleport creates.
3. **`assertUiAndVt` becomes a branch, not a deletion.** UI already current (the ActionListener case)
   → inline, byte-for-byte today. Otherwise → `EHelper.callOnLiveUISync(session, body)`, which
   resolves the live UI, takes the lock, and runs the body under `callSwing`. Both limbs of the old
   guard become properties of *where the body runs* rather than of where it was called from.

## 4. Three findings

**(a) The caller need not be a virtual thread, and that is what makes this worth more than a message
fix.** The VT limb is about the thread that parks on the `executeJs` promise. Under
`callOnLiveUISync` that is the body's thread inside `callSwing`; the caller only blocks on a
`CountDownLatch` with a 30s backstop (`EHelper.LIVE_UI_SYNC_TIMEOUT_SECONDS`). So a migrator's
`EmulatorContext.wrap(Executors.newFixedThreadPool(4))` — **platform** threads — gains clipboard
access, which it has no route to today and which a `SwingWorker`-only fix would not have delivered.

`callOnLiveUISync`'s javadoc says *"The caller must be a parkable (virtual) thread; a `SwingWorker`
worker thread is."* That is true of the **UI** thread, which must not block its own drain, and
over-stated for a background platform thread. `Q_platform_caller`: **this is the design's
load-bearing claim and the first thing to test**, not to assert — a wrapped fixed pool doing a
clipboard round-trip end to end. If it is wrong, the shape still works for `SwingWorker` and the
scope shrinks to that.

**(b) `callOnLiveUISync` is generic machinery wearing Preferences' clothes.** Four of its messages
hardcode *"a background preferences access"* and *"See D_prefs_scope_split"*, and its javadoc example
is a prefs read. A clipboard call site would produce a timeout message telling the migrator their
preferences are broken. `Q_label_param`: it wants a caller-supplied label before the second caller
arrives — cheap now, much cheaper than after a third.

**(c) Its no-live-UI throw is already R_match_swing_errors case (8) in everything but the type.**
Blocking caller, browser gone, nothing to return — D_sync_ui_hop's criterion exactly. Per the
sibling's `Q_exception_type` it should be `vaadinx.BrowserSessionClosedError extends AWTError`, so a
worker's `catch (Exception e)` cannot swallow it. `Q_type_scope`: doing it here is the better end
state (one answer for the whole family) but drags the existing Preferences path along and touches
D_prefs_scope_split's contract, so it may belong with the case-(8) entry instead.

## 5. What has to be decided first

- `Q_instance_scope` — **session-scoped instance, or resolve a fresh one per op?** Session scope
  keeps `setTestMode` sticky, which both test classes depend on, and is coherent under one-app-per-
  session ([D_session_scoped_pools](../emulators/decisions.md#D_session_scoped_pools)). But the
  clipboard then outlives any one UI, which is a scope change rather than a refactor, and
  [D_clipboard_test_mode](../emulators/decisions.md#D_clipboard_test_mode) describes the flag as
  per-instance where per-instance today means per-UI.
- `Q_type_scope` — whether (c) lands in this slice or with case (8).
- `Q_platform_caller` — (a), measured before anything is built on it.
- `Q_reproduce_clipboard` — §2's reading confirmed by a failing test first.

## 6. What flips

Two tests assert today's behaviour and would invert:
`WebClipboardTest.getSystemClipboardWithNoUiThrowsIllegalStateException` and
`ToolkitTest.getSystemClipboardWithNoUiThrows`.

`D_clipboard_vt_park` needs rewriting rather than amending — its decision and its rationale both rest
on the retired assumption. `D_clipboard_helper_js` needs one clause changed, since it pins the
injection to *"`Toolkit.getSystemClipboard()`'s per-UI lazy-init path"*; its actual argument (why the
`@JavaScript` annotation was a dead end, why `context://` rather than Vite) is unaffected.
`D_clipboard`'s own summary line survives untouched.

## 7. Acceptance

A `SwingWorker.doInBackground` that calls `Toolkit.getDefaultToolkit().getSystemClipboard()` and then
`setContents` / `getContents` round-trips through the browser and returns, with no guard thrown and
no change at the call site. The same from a task submitted to an `EmulatorContext.wrap`-ed platform
pool (`Q_platform_caller`). With the tab closed, both fail loud rather than hanging — which type
depends on `Q_type_scope`.
