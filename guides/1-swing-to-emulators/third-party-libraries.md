# Third-party Swing libraries

Reference for [Phase 1](./guide.md#S_triage_dependencies)'s dependency triage.

Your own code isn't the only thing importing `javax.swing` — your dependencies do too, and the same rule applies to them as to your own code: **a compiled jar that references `java.awt` / `javax.swing` cannot be used as-is.** There is no binary shim. Every Swing-touching dependency needs a recompilation path.

The good news is that this is usually a much smaller problem than it looks. In the real app that drove this work, four alarming-looking Swing libraries reduced to *one* real job, one small island, and two that vanished.

## Triage each library first — the answer is decidable from its jar

Before planning any work, ask what an import-swap of that library would actually produce. Two greps over its classes answer it in a couple of minutes:

```bash
unzip -o thelib.jar -d /tmp/thelib && cd /tmp/thelib
javap -p $(find . -name '*.class') | grep -E 'paint|Graphics'                 # custom painting
javap -p -c <suspect classes> | grep -E 'setBounds|FontMetrics|Toolkit|getScreenResolution'
```

| what you find | what an import-swap produces | what to do |
|---|---|---|
| neither pattern | **it works** | swap it — recompile the library's sources against `:emulators` and move on |
| `setBounds` arithmetic, `FontMetrics`, `Toolkit.getScreenResolution()` | **compiles, then misbehaves** — there is no synchronous child measurement server-side, and `getScreenResolution()` throws `HeadlessException` when headless | swap *and* replace the offending internals (a `CssEmittingLayoutManager`, a unit converter), or rewrite the affected views |
| a widget hand-built from dozens of primitives (a calendar grid of `JButton`s, a custom-painted gauge) | **works, and looks wrong** — the fidelity ceiling is the desktop widget itself | reimplement its API over the native Vaadin control, or drop the feature |

Neither the compiler nor a test run will surface the middle row for you: `Toolkit` and `FontMetrics` calls compile perfectly and fail at runtime. That's the whole reason this pass exists.

## Two libraries already have an add-on

JCalendar 1.4 and JGoodies Forms 1.2.1 are pre-built: a jar that provides the library's API on top of
`:emulators`, so your app keeps compiling after the import swap instead of needing the library ported
by hand. Coordinates, what each one covers, and how to read an add-on's own migration instructions
are in **[`addons.md`](./addons.md)** — check it before you triage any library by hand.

The per-add-on instructions deliberately do not live in these guides. They ship with the add-on, so
they change when it changes and this guide does not have to be re-opened when SB-Emulators gains another one.

**An add-on is thinner than the library it replaces, and [Phase 2](./guide.md#S_run_import_swap)'s report is how you find the gaps.**
Put the add-on's jar on the tool's classpath and it rewrites the types that add-on covers; for anything
else under the *same package root* it emits a "no mapping for …" line. That turns coverage sparsity into
a list you read up front — JGoodies Forms' `builder` package (`PanelBuilder`, `ButtonBarBuilder`,
`DefaultFormBuilder`) has no counterpart, and JCalendar's add-on ships `JDateChooser` alone — rather
than a series of compile errors.

Two limits on that, both worth knowing before you rely on it:

- **The classpath is what makes a root known.** The tool recognises `com.jgoodies` as an add-on root only
  because the jgoodies add-on's table told it so. Leave that jar off and its imports get no line at all —
  silence, then a compile error. So add every add-on you intend to use *before* reading the report, and
  treat a suspiciously clean report on a library-heavy app as a missing jar rather than good news.
- **It answers *which types*, never *which methods*.** A mapped type can still be missing members —
  `JDateChooser.getDateEditor()` and `getDateFormatString()` are the ones a real app hits first. The
  add-on's own `MIGRATION.md` enumerates those, and they surface as ordinary compile errors in
  [Phase 5](./guide.md#S_build).

## Libraries with no add-on — the three states

For everything else there are exactly three outcomes. Decide per library, not per app:

- **Source available and the library is small** (MigLayout core, a single-purpose widget lib). Vendor it, point [Phase 2](./guide.md#S_run_import_swap)'s tool at its source root exactly as at your own, recompile against `:emulators`. A custom `LayoutManager` is a single `CssEmittingLayoutManager` mapping; a custom-painted widget is a rewrite or a drop.
- **Source available but the library is huge** (SwingX is the canonical case — large, sprawling, partly abandoned). Recompiling is possible in principle and a project of its own in practice. Usually cheaper: count the call sites first. SwingX in the real migration target turned out to be *one* call, so the whole dependency was deleted rather than forked.
- **Closed-source binary.** No path. Get source from the vendor, or rewrite the views that use it to drop the dependency.

**Look-and-Feel libraries are a special case: just delete them.** JGoodies Looks, Substance, FlatLaf and friends only exist to restyle Swing, and L&F dispatch is permanently out of scope — styling is Vaadin + CSS after the migration. Drop the dependency and the `UIManager.setLookAndFeel(...)` calls with it.

## See also

- [`addons.md`](./addons.md) — the add-on index, and each add-on's own `MIGRATION.md`.
- [`import-swap-reference.md`](./import-swap-reference.md) — what the swap does to a library you vendor.
