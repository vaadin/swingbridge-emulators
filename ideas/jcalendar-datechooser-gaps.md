# `swingbridge-emulators-jcalendar-1.4` — the two `JDateChooser` members a real app hit

**Status:** hand-off, not yet implemented — and **parked**: it widens a starter add-on, which
[M1D_addon_starter_status](../migration/1-swing-to-emulators/decisions.md#M1D_addon_starter_status)
freezes, so picking it up means revising that entry first. Found by inventory's guide-loop round 2
(2026-09-04) as the **only place that migration lost behaviour** (recorded then as a "Not a doc gap"
entry in that round's `STUMBLES.md`, since replaced by later rounds). The docs were right — [`MIGRATION.md`](../third-party/jcalendar-1.4/MIGRATION.md) § "Not
ported" lists both by name — so this is an artefact gap, and the question is whether the add-on's
four-member surface ([M1D_swap_vs_reimplement](../migration/1-swing-to-emulators/decisions.md#M1D_swap_vs_reimplement))
should grow by two.

## What the app calls

Four sites, two members, all in [`testapps/inventory/swing`](../testapps/inventory/swing):

| member | site | what it was for |
|---|---|---|
| `getDateEditor().setEnabled(false)` | `ItemEntryPanel.enableDisableComponents`, 3× (`:252`, `:262`, `:273` — the `NONE` / `CREATE` / `MODIFY` states) | make the chooser's **text half** read-only so the user must pick from the calendar; the popup button stays live. `setEnabled(false)` on the whole chooser is not a substitute — `CREATE` / `MODIFY` need date entry. |
| `getDateFormatString()` | `Validator.validationCriteria` (`Validator.java:182`) | feed a `SimpleDateFormat`, then `format(inputDate)` inside a try/catch — whose only observable failure is a `null` date, since every non-null `Date` formats |

The run-2 tree **dropped the three `getDateEditor` calls** (the migrated app lets the user type the
date as well as pick it) and **reduced the validator to `return inputDate != null;`** (same answer at
every call site). The first is a real behavioural loss; the second is not.

## Why the surface is four members today

The add-on is a clean reimplementation over `SJFormattedDatePicker`, not a fork: JCalendar's
`JDateChooser` is a `JFormattedTextField` editor plus a popup `JCalendar`, and a Vaadin `DatePicker`
is one widget with no separately addressable editor. M1D_swap_vs_reimplement chose the narrow
surface so the addendum could say "compile error naming the missing symbol" instead of shipping
methods that WARN. That stance is right for `getJCalendar()` / `getCalendarButton()` — there is no
calendar and no button to hand out. The two members here are different: each has a Vaadin-shaped
*effect* worth measuring before deciding.

## `getDateFormatString()` / `setDateFormatString(String)` — likely cheap

Upstream's default pattern is `DateFormat.getDateInstance(MEDIUM, locale).toPattern()`, and the
getter returns whatever the last `setDateFormatString` stored. Candidate: store the pattern as
emulator-side state (R_decline_effect_only — state and notification are free), default it from the
browser locale the same way upstream defaulted from the JVM locale, and **decline the effect** —
`DatePicker` renders in the browser's own locale format, so `setDateFormatString` changes what the
getter returns and not what the user sees. That is the honest shape: the migrator's `Validator`
keeps compiling and behaving, and the addendum states the one divergence.

Open: does `setDateFormatString` fire upstream's `dateFormatString` property change? Read the JDK-era
source (`com.toedter.calendar.JDateChooser` 1.4) before writing the setter — per
R_decline_effect_only the notification is part of the spec, not a nicety.

## `getDateEditor()` — needs a measurement first

Upstream returns an `IDateEditor` (the `JTextFieldDateEditor`, a `JFormattedTextField` subclass).
The app's use — `setEnabled(false)` on it — means *typing off, picking on*. Three candidates, in
the order to try:

1. **`DatePicker.setAllowedCharPattern(<matches nothing>)`** while the picker stays enabled.
   `HasAllowedCharPattern` filters keystrokes client-side; a pattern that admits no character makes
   the text half inert and leaves the calendar popup working. **Measure it:** does the overlay still
   open on click, and does the value stay editable through it? Does paste get through? If yes on
   both, `getDateEditor()` can return a minimal `IDateEditor`-shaped handle whose
   `setEnabled(false)` flips that pattern and whose `setEnabled(true)` clears it — and whose other
   `IDateEditor` members (`getUiComponent`, `setDate`, `getDate`, `setDateFormatString`, …) route back
   to the chooser or WARN. Porting `IDateEditor` itself is what this costs; check whether its
   signatures mention a Swing type (`JComponent getUiComponent()` does — so the ported interface
   has to say `vaadinx.swing.JComponent`, R_no_vaadin_in_api).
2. **`setReadOnly(true)`** — rejected in advance: read-only disables the popup too, which is
   exactly the whole-chooser `setEnabled(false)` the app could not use.
3. **Ship nothing and leave the compile error** — today's answer. Acceptable only if (1) fails the
   measurement; the addendum's *Known divergence* already says what dropping the call costs (the
   field stays typeable), so nothing more is owed there.

## Steps, in order

1. Probe candidate (1) on a bare `SJFormattedDatePicker` under Karibu + one manual browser check.
2. Land `getDateFormatString` / `setDateFormatString` with a test in
   `third-party/jcalendar-1.4/src/test` (state round-trip, default from browser locale, property
   change if upstream fires one).
3. Land `getDateEditor()` per the probe's outcome; `IDateEditor` ported if (1) works.
4. Update `MIGRATION.md` § "Not ported" — remove the shipped members — and § "Known divergence":
   replace the `getDateEditor()` bullet, and state the new divergences (format string is not
   rendered; the editor handle is a façade over one `DatePicker`). The
   add-on's `MigrationDocTest` and `PortedTypesTableTest` gate the rest.
5. Re-run `/guide-migrateapp inventory`; the expectation is `ItemEntryPanel` and `Validator` compile
   without hand edits.
6. Delete this file.

**Commit shape:** one commit per member is fine, but (2) and (3) each carry their addendum edit
with them — the addendum is the add-on's contract, and a jar that ships a method its `MIGRATION.md`
says does not exist is the drift M1D_addon_migration_docs exists to prevent.
