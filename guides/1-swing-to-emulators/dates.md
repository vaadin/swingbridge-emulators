# Date and time handling

Reference for [Phase 1](./guide.md#S_triage_reports)'s date bucketing and
[Phase 6](./guide.md#S_resolve_tz_markers)'s marker resolution.

Swing apps run in the user's local JVM time zone; a Vaadin server runs in its own zone while users sit in arbitrary browser zones. Any conversion of an instant to or from *field form* (year/month/day/hour — formatting, parsing, `now()`) drifts accordingly, and the bug is invisible on a dev machine whose browser and server share a zone. Three kinds of date code, three handlings.

## 1. `SimpleDateFormat` / `Calendar` / `GregorianCalendar` — import-swap, zone handled for you

These are import-swap emulators — the swap is the whole fix, and the browser zone comes with it:

| From | To |
|---|---|
| `java.text.SimpleDateFormat` | `vaadinx.text.SimpleDateFormat` |
| `java.util.Calendar` | `vaadinx.util.Calendar` |
| `java.util.GregorianCalendar` | `vaadinx.util.GregorianCalendar` |

Swap the import and leave the code shape alone — `fmt.format(date)`, `fmt.parse(s)`, `Calendar.getInstance()`, `cal.set(y, m, d); cal.getTime()`, `Calendar.YEAR`, `new GregorianCalendar(2020, 5, 15)` all keep working. The emulator defaults to the **browser** zone (the server-side analog of the desktop JVM default), so the wall-date matches what the user expects. Notes:

- **A `static final SimpleDateFormat` field is fine**, and passes the [static gate](./guide.md#S_guardrails) unannotated. Construction never reads the zone (it is deferred to the first `format`/`parse`), and the emulator is stateless and thread-safe under the session lock — so the classic shared-formatter idiom is safe to keep.
- **An explicit `setTimeZone(...)` / `getInstance(tz)` wins.** The browser zone is only the default when you set none.
- **Field-id constants keep JDK's values.** `vaadinx.util.Calendar` / `GregorianCalendar` subclass `java.util.Calendar`, so `Calendar.DAY_OF_MONTH`, `YEAR`, `MONTH`, … are the identical `int`s — safe to pass into a stay-JDK model such as `SpinnerDateModel(date, min, max, Calendar.DAY_OF_MONTH)`.
- **Forgot `BrowserTimeZone.fetch()`?** On a UI thread the first `format`/`parse` (SimpleDateFormat) or the construction (Calendar) throws `IllegalStateException` with a pointer at the cause — wire the [init listener](./guide.md#S_register_bootstrap).
- **Reached from `main()`? Nothing throws — you get the *server* zone, and that is the intended answer.** With no session and no `EmulatorContext` these emulators fall back to the server default and log one WARN (whose text says "background thread"; `main()` is not one but takes the same path). A seed or a schema step has no user, so there is no browser zone to want — the [third residual](#3-javatime-and-browserdateutils--the-residuals) says why that is right rather than tolerated. **Do not tag bucket-1 code with `// TODO[browser-tz]:`** even so: the fix would land in a shared date utility called from both sides, a refactor bought for no gain, where bucket 3's costs one argument at the seam. If one pre-boot value has a business zone of its own, `Calendar.getInstance(TimeZone.getTimeZone("UTC"))` at that call site is the hatch.
- **A consequence to expect:** a shared helper called from both sides answers in two zones — `getCurrentFiscalYear()` gives the *server*'s answer to your seed and the *browser*'s to a UI thread. Correct on both paths, and near a year boundary a seeded row can legitimately disagree with a freshly-computed one.
- **`@Deprecated` on purpose.** These are transitional; they compile with a deprecation warning that nudges a later rewrite to `java.time` (stage 3). Ignore the warnings during stage 2.

## 2. Background threads — carry the context with `EmulatorContext`

The browser zone lives on the UI thread. On a background thread the emulator can't find it and falls back to the **server** zone (with a once-per-thread WARN). `SwingWorker` handles this for you — `doInBackground` already runs with the user's zone.

**This is not only about dates.** The same context is what lets a background thread reach the browser at all, so on a thread that carries none, *updating a component throws* rather than quietly doing nothing ([`runtime-contract.md`](./runtime-contract.md)). One wrap fixes both, and the rest of this section is that wrap.

**Your own executor gets it with one line, where you build the pool:**

```java
this.pool = vaadinx.EmulatorContext.wrap(Executors.newFixedThreadPool(4));   // once, at startup
pool.submit(() -> { ... });                                                  // ordinary submits
```

`wrap` returns a view over your executor — yours stays usable, nothing is shut down for you — and captures the context **per submit, on the submitting thread**, so wrapping at startup (where there is no session yet) is exactly right. A submit from a thread that has no context of its own just passes through.

**A thread you create by hand** has no construction to wrap, so capture and apply it yourself:

```java
vaadinx.EmulatorContext ctx = vaadinx.EmulatorContext.get();   // on the UI thread
new Thread(() -> ctx.run(() -> {
    // SimpleDateFormat / Calendar here now use the user's browser zone
})).start();
```

`ctx.run(...)` / `ctx.call(...)` never make a session "current" — `VaadinSession.getCurrent()` stays null in the body. If you don't carry the context, dates on that thread use the server zone; that is fine when they aren't user-facing (log timestamps, internal keys).

## 3. `java.time` and `BrowserDateUtils` — the residuals

- **`java.time` is not emulated.** `LocalDate.now()`, `LocalDateTime.now()`, `DateTimeFormatter.ofPattern(...)` without `.withZone(...)`, and `ZoneId.systemDefault()` read the *server* zone. Pass the browser zone explicitly: `LocalDate.now(BrowserTimeZone.get())`, `formatter.withZone(BrowserTimeZone.get())`. On a background thread, `BrowserTimeZone.get()` needs the zone captured on the UI thread first — `EmulatorContext.get().zone()` hands you the `ZoneId` to thread through.
- **`BrowserDateUtils` is the hand-written / stage-3 vocabulary.** For direct `Date`↔`LocalDate` work, `com.vaadin.swingbridge.surrogates.util.BrowserDateUtils.dateOf(y, m, d)` / `toLocalDate(Date)` / `toDate(LocalDate)` / `formatAsISODate(Date)` / `formatAsDate(Date, pattern)` are `ZoneId`-based and match the DatePicker surrogates exactly. Prefer these when rewriting a view toward Vaadin, rather than reaching back for a `SimpleDateFormat`.
- **Reached only from `main()`? Keep the server zone — and move the time to midday.** Pre-boot there is no browser to disagree with, so `systemDefault()` is the right answer rather than one you tolerate: `BrowserTimeZone.get()` throws there and there is no UI thread to capture an `EmulatorContext` from, and a migrated Swing app is typically intranet-deployed, where the server's zone *is* its users'. Leave the zone as your code already has it.

  What *is* worth changing is the time of day, and only for **calendar dates** — a date meant as "10 April 2026" rather than as an instant:

  ```java
  // BEFORE — midnight; renders a day early for any browser west of the server
  Date.from(LocalDate.of(2026, 4, 10).atStartOfDay(ZoneId.systemDefault()).toInstant())
  // AFTER — same zone, midday, so every browser within ±11 h shows the same calendar day
  Date.from(LocalDate.of(2026, 4, 10).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant())
  ```

  Midday is the one pre-boot detail that is cheap now and expensive later, because the value gets **stored**: the day a second office opens or a laptop comes back from a trip, the zone is a one-line change while midnight-stored rows are a data migration. It costs nothing in a single-zone deployment, where both render as the same calendar day.

  A named business zone instead of the server's is fine where the data genuinely has one (a fiscal calendar fixed to head office). Move the code to a UI-thread call site only if it is really per-user work that `main()` happened to host — which a seed is not. **Resolve the marker with a comment recording that the server zone is intended**, not necessarily with an edit: a zone is being chosen either way, and writing down which one is what makes the choice reviewable the day the deployment stops being single-zone.

## Two things NOT to do

- **Don't hand-wrap `Calendar` / `SimpleDateFormat` with `TimeZone.getTimeZone(BrowserTimeZone.get())`.** The emulator already applies the zone, consistently; adding your own `java.util.TimeZone` wrapper is redundant. Let the emulator do it.
- **Don't share a mutable `Calendar` across threads.** A `static` / shared `Calendar` mutated from more than one thread has no correct multi-user-server semantics (it was racy on the desktop too, only masked by single-threaded EDT use). Unshare it — construct one per use.

## Bucketing your own code

The [hazard scan](./guide.md#S_run_hazard_scan) finds the date code for you and labels each hit with
its bucket, so this is a reading job rather than a grepping one. Two things it cannot decide:

- **A `new Date()` used only as an opaque instant** — comparisons, `getTime()` arithmetic, a
  timestamp column — needs nothing at all. Only a conversion to or from *field form* can drift.
- **Which hits are worth a marker.** Tag the hand-work residuals — buckets 2 and 3, and a shared
  `Calendar` — with `// TODO[browser-tz]:`. Bucket 1 gets no marker even when `main()` reaches it,
  per the note above.

**Don't resolve the markers yet**: `BrowserTimeZone.get()` and the `BrowserDateUtils.*` helpers throw until the
[init listener](./guide.md#S_register_bootstrap) is wired, so resolution is
[Phase 6](./guide.md#S_resolve_tz_markers).

## See also

- [`lifecycle.md`](./lifecycle.md) — why `main()` cannot reach a browser zone, and what that costs.
- [`import-swap-reference.md`](./import-swap-reference.md) — the three bucket-1 rows in the swap table.
