# `swingbridge-emulators-jcalendar-1.4` — provenance

**Strategy: clean reimplementation, not a fork.** No upstream source is copied into this module.
It reproduces JCalendar 1.4's `JDateChooser` *API* on top of SB-Emulators' own date-picker surrogate.

**Licensed [LGPL-2.1-only](./LICENSE) — upstream's licence, not SB-Emulators' own.** Reimplementing
rather than forking means the module carries no LGPL *obligation*; it takes the licence anyway, per
[M1D_addon_upstream_licence](../../migration/1-swing-to-emulators/decisions.md#M1D_addon_upstream_licence).
See § Licence below for why, and for what that asks of an app linking it (very little, and nothing
at all if the app is hosted rather than shipped).

## Upstream, for identification only

- **Project:** [toedter/jcalendar](https://github.com/toedter/jcalendar), by Kai Toedter
- **Coordinate the migrated app depends on:** `com.toedter:jcalendar:1.4`
- **Upstream licence:** LGPL 2.1
- **Status:** archived 2019-03-29, so 1.4 is effectively terminal.

**Nothing from that artifact is present here** — no sources, no classes, no resources. The upstream
jar was read once with `javap` to measure the API surface and the painting surface; that
measurement is recorded in
[M1D_swap_vs_reimplement](../../migration/1-swing-to-emulators/decisions.md), and no bytecode was decompiled.

## Licence: LGPL-2.1, upstream's — **not** the project's

**This module is licensed [LGPL-2.1-only](./LICENSE), the licence of the library whose API it
reproduces**, and not SB-Emulators' own GPLv2 + Classpath Exception. Its pom carries a `<licenses>`
override, and its `<resources>` block ships *this* `LICENSE` into the jar's `META-INF/` rather than
the repository root's. The rule is
[M1D_addon_upstream_licence](../../migration/1-swing-to-emulators/decisions.md#M1D_addon_upstream_licence):
**an add-on takes its upstream library's licence**, whether it is a fork or a reimplementation.

Note this is *not* because the module was found to be derivative. It was not, and the analysis below
is unchanged and still believed. The licence is taken anyway, for three reasons:

- **LGPL-2.1 §3 is the decisive one.** §3 lets you relicense an LGPL work under the **ordinary**
  GPLv2. It does **not** authorise GPLv2 **+ Classpath Exception**, because the CE grants a linking
  permission over code that would not be ours to grant it over — and still less a permissive
  licence. So either licence this module could otherwise take — the parent pom's Apache-2.0, or the
  emulator core's GPLv2+CE — is valid only in the future where this port never becomes derivative,
  while LGPL-2.1 is valid in both. Picking the one that survives either outcome cost nothing, because nothing had been
  published when the choice was made.
- **The cleanliness of a reimplementation is a function of how little was ported, and that is a
  moving target.** The lane was decided at module birth, when the port was smallest and the analysis
  most favourable; nobody re-runs the analysis on widening. Pre-paying the licence removes the
  question from the growth path. (This module is frozen at its current coverage per
  [M1D_addon_starter_status](../../migration/1-swing-to-emulators/decisions.md#M1D_addon_starter_status),
  so the growth case is hypothetical *here* — but "frozen" is a policy, not a law.)
- **It costs a migrator nothing.** The only apps that resolve `swingbridge-emulators-jcalendar-1.4` are apps
  whose Swing source imported `com.toedter.calendar.*` — i.e. apps that already shipped
  `com.toedter:jcalendar:1.4` under LGPL-2.1. The LGPL conversation has already happened at every
  consumer of this module, by construction.

**`-only`, not `-or-later`, and that is a deliberate conservative choice**: upstream's own files were
not read (nothing here is copied from them), so which of the two forms upstream grants has not been
verified. `-only` is valid under either. If upstream is confirmed to say "or, at your option, any
later version", loosening this is a one-line change in four headers and the pom.

**What LGPL-2.1 asks of an app that links this jar**, stated plainly because a migrator will ask:
on **distribution**, §6 requires that the recipient be able to relink against a modified version of
this library, plus permission to reverse-engineer for debugging those modifications. The
Vaadin-Boot scaffold the migration guide hands out satisfies this for free — its assembly ships
every dependency as its own jar under `lib/` (`<unpack>false</unpack>`, no shading), so a modified
`swingbridge-emulators-jcalendar-1.4.jar` can simply be dropped in. Note also that **hosting is not
distribution**: an app served over a network is not conveyed to its users, so a cloud-hosted
migrated app triggers no §6 obligation at all. LGPL-2.1 has no remote-network-interaction clause
(that is AGPLv3 §13, and nothing here is AGPL).

### The reimplementation analysis, unchanged

This is what the licence choice above deliberately does *not* rely on, kept because it is still the
accurate description of how the module was made — and because if it is ever challenged, this is the
record.

- **The module is one file, 125 lines.** The upstream-derived surface is three members —
  `DATE_PROPERTY = "date"`, `getDate()`, `setDate(Date)`. Everything else is peer wiring to
  `SJFormattedDatePicker`. Nothing upstream was copied; the jar was read once with `javap` to
  measure the API and the painting surface, and no bytecode was decompiled.
- **The reasoning that an API-compatible reimplementation is not a derivative work**
  (*Google v. Oracle*, US 2021; *SAS v. WPL*, EU 2012) is **a reading, not a ruling**, and the
  module does not rest on it: under LGPL-2.1 the artifact is correctly licensed whether the reading
  holds or not.
- **The project applies one rule in both directions.** `:emulators` reads OpenJDK's source as its
  written specification, so SB-Emulators accepts derivative status and takes OpenJDK's own licence
  ([D_gplv2_ce_relicense](../../emulators/decisions.md#D_gplv2_ce_relicense)). Here only the public
  API surface was read, so the *finding* differs — and the *licence* lands in the same place anyway,
  which is the point of M1D_addon_upstream_licence.
- **Not clean-room, and it must not be called that** — there was no wall between whoever read the
  API and whoever wrote the code, and [R_gpl_provenance](../../CLAUDE.md#R_gpl_provenance) rule 1
  bans the claim project-wide. The accurate wording is the one at the top of this file:
  reimplemented from the public API surface, no upstream source or bytecode copied.
- **The name is compatibility, not endorsement.** `swingbridge-emulators-jcalendar-1.4` and
  `vaadinx.toedter.calendar` carry the upstream project's and author's names because a migrated
  app's imports do. Neither JCalendar nor Kai Toedter endorses, reviewed or supports this module —
  the same stance the sibling fork takes under BSD's third clause, stated here because LGPL has no
  equivalent clause to make it automatic.

## Why reimplemented rather than import-swapped

Not because the swap was blocked — it wasn't. Measured, JCalendar's whole drawing surface is three
`paint`/`paintComponent` overrides, of which only `JDayChooser$DecoratorButton` (and one anonymous
sibling) sits inside `JDateChooser`'s closure, decorating weekday-header cells. Under R_match_swing_errors sub-bucket
(b) those WARN, drop, and cost a background colour. The swap would have compiled and run.

What the swap *produces* is the objection: `JDayChooser` builds its grid from ~49 `JButton`s, so
each date field would render as ~49 Vaadin Buttons in a hand-drawn calendar, several per view, on a
platform that has a native date picker. That ceiling doesn't move with effort — it is the Swing
widget's own design. Reproducing the API over `SJFormattedDatePicker` is smaller (the whole
supporting-class closure evaporates) and renders as the control the app actually wants.

This is a judgement about the *result*, not a technical block, and it should be re-argued per
library rather than generalised.

## Subset

Only what the migration target uses. See [`README.md`](./README.md) for the list and for what
happens when a migrator reaches for something outside it.
