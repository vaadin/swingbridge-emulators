# Cancelable tab close — is a `beforeunload` shim worth shipping?

**Status:** brainstorm / not decided. Extracted from the retired
`migration/1-swing-to-emulators/ideas.md` (authored 2026-05-06), which predated this folder.

**Maintainer-facing.** Assumes familiarity with
[D_jframe_as_route](../emulators/decisions.md#D_jframe_as_route) and
[D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle).

---

## The question

Do we offer a `beforeunload`-based "are you sure?" prompt as a substitute for
`WindowListener.windowClosing` → `event.consume()`?

## Where we are

[D_jframe_as_route](../emulators/decisions.md#D_jframe_as_route) rejects in-app cancel on
browser-control grounds — the browser owns the unload prompt, and the app cannot override the user's
choice. So `event.consume()` is a **no-op for tab close**, and the migrator-facing hazard
[`H_cancelable_close`](../migration/1-swing-to-emulators/spec.md#H_cancelable_close) says so, with
"save state proactively rather than confirm-on-close" as the standing recommendation.

## Why it keeps coming back

Many Swing migrators rely on the confirm-on-close idiom for "save before close" flows. A
`beforeunload` shim could trigger the browser's *native* confirm dialog ("Leave site?"): the user's
choice still wins, but the app gets a chance to flush state while the prompt is up.

That is **not the same semantics** — no in-app cancel, only "browser asks user, user decides" — but
it is arguably better than silently ignoring the `consume()`.

## Open

- Is the shim worth shipping at all, or do we firmly recommend "save proactively"?
- If shipped: does a partial emulation of `consume()` violate
  [R_no_silent_improvements](../CLAUDE.md#R_no_silent_improvements) in the other direction — a migrator's
  `event.consume()` that *sometimes* stops the close is harder to reason about than one that never
  does. A shim that only *flushes* (never blocks) and leaves `consume()` a documented no-op may be
  the honest shape.
- Modern browsers heavily restrict `beforeunload` (it requires prior user interaction with the page,
  and the message text is not author-controlled). Worth measuring before designing around it.

**Source:** D_jframe_as_route.
