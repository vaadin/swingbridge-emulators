# Is `BrowserTimeZone.get()` safe in the `@MainWindow` frame constructor on a *cold* session?

**Status:** open, needs a probe under a real browser (Fable's lane). Extracted from the retired
`migration/1-swing-to-emulators/ideas.md`.

**Maintainer-facing.** Assumes
[SD_browser_timezone](../surrogates/decisions.md#SD_browser_timezone) and
[M1D_bootstrap_canonical](../migration/1-swing-to-emulators/decisions.md#M1D_bootstrap_canonical).

---

## The question

Surfaced while writing [`lifecycle.md`](../guides/1-swing-to-emulators/lifecycle.md) (2026-09-03) and
deliberately kept out of its hook table: **row 6 — the `@MainWindow` frame ctor — claims only the
`callSwing` envelope, not the browser zone.**

`BrowserTimeZone.fetch()` caches the zone on the session, and when Vaadin's
`ExtendedClientDetails` is still unpopulated (`screenWidth == -1` is the sentinel) it refreshes
**asynchronously** — `details.refresh(d -> setZoneId(extractZoneId(d)))` in
`BrowserTimeZone.fetch()`. Whether the zone is in place by the time `bootstrap()` → the frame ctor
runs is therefore **round-trip timing, which reading the code cannot settle.**

`lifecycle.md` row 6 currently sends `Preferences` reads and "restore window bounds" to that row
without promising the zone.

## Why it matters

`BrowserTimeZone.get()` **throws** on an empty cache by design (a programming error per
SD_browser_timezone, not a soft gap). So if the zone can be absent in a cold-session frame ctor, a
migrator who puts a date-formatting call there gets an `IllegalStateException` on first load only —
the worst shape: invisible on a warm reload, invisible on the dev machine after the first hit.

## The probe

Real browser, **cold session** (fresh incognito / cleared session), a `@MainWindow` frame whose ctor
calls `BrowserTimeZone.get()`. Does it throw?

## If it can be absent

It is a hazard with no current home in the docs. Three places it goes:

1. [`lifecycle.md`](../guides/1-swing-to-emulators/lifecycle.md) row 6 — the "safe here" column gains
   an explicit *not the browser zone* caveat.
2. A [spec.md §7](../migration/1-swing-to-emulators/spec.md) `H_` bullet.
3. A `hazards.tsv` row, **if** it has a grep signal (probably
   `BrowserTimeZone` / `SHelper.toLocalDate` inside a `@MainWindow` class — worth checking whether
   that is expressible token-level, since `HazardScan` has no AST).

If it *cannot* be absent, the finding is still worth having: row 6's column gains the zone, and this
file is deleted.
