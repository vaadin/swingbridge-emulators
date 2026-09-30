# Emulating SWT — pure SWT, no OSGi

**Status: brainstorm. No commitment, no code, nothing decided.** Scoped against a mixed
**Swing + SWT + Eclipse RCP** application as the driving scenario. This file
covers the *toolkit* question only — "does the emulator approach transfer from Swing to SWT?" — and
deliberately assumes **plain SWT on a flat classpath, no OSGi, no Workbench**. The Eclipse
RCP / RAP / RWT / OSGi half is a genuinely different problem and lives in
[eclipse-rcp-rap-osgi.md](./eclipse-rcp-rap-osgi.md). Read this one first: everything there
presupposes the SWT layer here.

**Maintainer-facing.** Nothing here is a promise to anyone.

## The application shape being reasoned about

The driving scenario, at the granularity the design actually turns on:

| part | share of client code | toolkit | shape |
|---|---|---|---|
| RCP shell + ~50–60 views | ~60–70% | SWT (RCP window, perspectives, views) | tables, trees, forms — i.e. JFace viewers |
| Swing content *inside* some views | the rarer case | Swing embedded in SWT | custom-drawn domain visualisations |
| planning / scheduling | 30–40% | Swing only, separate windows | heavily customized gantt framework |

Two properties matter more than the percentages:

1. **The gantt/planning part separates cleanly** and can go its own way. That half is
   SB-Emulators' existing home turf and needs no new thinking.
2. **The SWT half is JFace-viewer-shaped, not custom-paint-shaped.** "Tables, trees and forms" is
   the single best-case description we could have hoped for — see
   [the JFace lever](#the-big-lever-jface-is-pure-java). If it had said "custom-drawn Canvas
   editors and GEF diagrams", the answer would be no.

## Bottom line

**The emulator approach transfers, and SWT is in several concrete ways an *easier* target than
Swing.** That is not a rhetorical flourish — it falls out of SWT's design: no pluggable L&F, no
MVC model layer, immutable style bits fixed at construction, a parent handed to every constructor,
and a *hard* thread contract the toolkit itself enforces. Four of SB-Emulators' most expensive mechanisms
either shrink or disappear.

Against that, three cost centres are real and one is a genuine unknown:

- **Custom drawing (`GC` + `PaintListener` + `Canvas` subclasses) is pervasive in SWT code** in a
  way `paintComponent` never was in Swing. SB-Emulators permanently excludes user-authored component paint
  (R_match_swing_errors sub-bucket (b)); in SWT that exclusion bites much harder.
- **`org.eclipse.swt.custom`** — `StyledText` in particular is not a widget, it is a text-editor
  engine (Eclipse's own editor is built on it). `CTabFolder`, `ScrolledComposite`, `SashForm`,
  `TableEditor`/`TreeEditor`/`ControlEditor` are all commonly used and none are cheap.
- **The manual dispose lifecycle and the `Device`-bound graphics resources** (`Color`, `Font`,
  `Image`, `GC`, `Region`) have no counterpart in the Swing port, where `java.awt.Color` /
  `Font` / `Dimension` are simply reused from the JDK unchanged (see CLAUDE.md §Packages).
- **The unknown: the user-written event loop.** `while (!shell.isDisposed()) if
  (!display.readAndDispatch()) display.sleep();` is *the* SWT idiom, and unlike Swing it lives in
  **application code**, not inside the JDK. Making a user-written spin loop park on a virtual
  thread is a UI-fiber problem we have adjacent experience with (D_callswing_loom/D_joptionpane modal parking) but have
  never solved in this exact shape — and vaadin-blocking-dialogs holds the session lock through any
  wait but its own park, so a loop that `sleep()`s would freeze the session unless `sleep` parks. It is one bounded mechanism, and it is the first thing any
  prototype must attack.

## Why SWT is a friendlier emulation target than Swing

Point by point, against SB-Emulators' actual pain.

**1. No Look-and-Feel.** SB-Emulators already drops L&F dispatch permanently (R_match_swing_errors (b)). In SWT there is
nothing to drop — no `ComponentUI`, no `BasicButtonUI`, no `updateUI()`, no `UIManager` defaults
table. An entire category of "the migrator's code touches an API we chose not to have" disappears.

**2. No model layer.** SWT `Table` owns its `TableItem`s directly; there is no `TableModel`,
`ListModel`, `Document`, `Caret`, `ButtonModel`, `SpinnerModel`, `TreeModel`, `Highlighter`,
`InputMap`/`ActionMap`, `Action`. A very large share of SB-Emulators' emulator surface *is* those models
and their event fan-outs. SWT widgets are thin: state plus `Listener`s. Per widget, the emulation
cost is materially lower.

**3. Style bits are immutable and known at construction time.** `new Button(parent, SWT.PUSH |
SWT.FLAT)`, `new Text(parent, SWT.MULTI | SWT.WRAP)`, `new Table(parent, SWT.VIRTUAL | SWT.CHECK)`.
SB-Emulators' ctor-time peer dispatch — the `JFormattedTextField` five-way (D_jformattedtextfield) and `JToggleButton` (D_jradiobutton)
— is an *exception* it had to engineer around; in SWT it is the **normal case**, sanctioned by the
toolkit's own contract. `SWT.PUSH` → `Button`, `SWT.CHECK` → `Checkbox`, `SWT.RADIO` →
`RadioButton`, `SWT.ARROW`/`SWT.TOGGLE` → styled `Button`. Style bits cannot be changed later, so
there is no "the peer must mutate its type" hazard at all — the class of problem R_leaf_peer_lockdown's lock-down
exists to fence off.

**4. Parent-in-constructor.** Every SWT widget is created into its parent. No detached-component
window, no `addNotify`/`removeNotify` dance, no "component created but not yet in a hierarchy"
state, and reparenting is either forbidden or explicit (`setParent`, and only on some platforms).
SB-Emulators spends real complexity on the Swing hierarchy being mutable and late-bound; SWT hands us the
tree at construction.

**5. A hard, toolkit-enforced thread contract.** `checkWidget()` throws
`SWTException(ERROR_THREAD_INVALID_ACCESS)` on off-UI-thread access. This is a *gift* under R_match_swing_errors:
where Swing lets wrong-thread access silently corrupt state (so SB-Emulators must guess whether to warn or
throw), SWT already throws, so **matching native behaviour and doing the strict thing are the same
action**. Same for `ERROR_WIDGET_DISPOSED`. The R_match_swing_errors triage that dominates every SB-Emulators component
design is largely pre-decided by the SWT spec.

**6. `SWT.VIRTUAL` is Vaadin's lazy `DataProvider`, natively.** `table.setItemCount(n)` +
`SWT.SetData` listener is a callback-driven windowed data source — the same shape as Vaadin
`Grid`'s lazy `DataProvider` (`countCallback` + `fetchCallback`). Large-table apps *already wrote
their code in the shape Vaadin wants*. This is a better semantic match than anything in the
`JTable`/`TableModel` port.

**7. The layouts map better.** SWT's `GridLayout` + `GridData` (with `horizontalSpan`,
`grabExcessHorizontalSpace`, `exclude`) is close to a description of CSS Grid; `FillLayout` and
`RowLayout` are flexbox; `FormLayout`'s attachments are percentage/anchor-based. Compare
`GridBagLayout`, which SB-Emulators has to emulate through a pixel engine's worth of constraint arithmetic.
`vaadinx.awt.CssEmittingLayoutManager` (M1D_custom_layoutmanager) is the seam and it transfers directly — SWT's
`Layout` abstract class (`layout(Composite, boolean)` / `computeSize`) plays the same role as
`LayoutManager`.

**8. Dialogs are already a small closed set.** `MessageBox`, `FileDialog`, `DirectoryDialog`,
`ColorDialog`, `FontDialog`, `PrintDialog` — six classes, all of them native-dialog wrappers with
a `open()` that returns a value. SB-Emulators has precedent for every one (`JOptionPane` per D_joptionpane,
`vaadinx.awt.FileDialog`, `JColorChooser`, the D_printing PDF printer). No `JOptionPane`-scale
option-pane API to reproduce.

## Where it is harder

**Custom drawing.** `PaintListener` + `GC` is how SWT applications do anything the widget set
doesn't cover, and the widget set covers less than Swing's. `Canvas`, `GC.drawLine/fillGradient
Rectangle/drawText`, `Image` double-buffering, `SWT.NO_BACKGROUND`, owner-draw tables
(`SWT.MeasureItem`/`EraseItem`/`PaintItem`). All of this is R_match_swing_errors (b) — permanently out — and unlike
Swing that exclusion is likely to be *felt* by a real app. Mitigations worth thinking about, none
decided: (a) recognise the handful of decorative idioms (gradient background, focus border,
rounded frame) and substitute CSS; (b) offer a documented escape hatch — a `Canvas` whose
`PaintListener` is *not* emulated but whose peer is a `<canvas>` the migrator can retarget by hand
with a Vaadin-side renderer; (c) count the occurrences in the target app's code and decide from data.
Note the asymmetry with the [D_printing](../emulators/decisions.md) precedent: SB-Emulators *does* support
hand-crafted `Printable.print(Graphics)` because it is a one-shot static render, and a
`PaintListener` that draws a static decoration is arguably the same animal. A `GC`-to-SVG spike is
not obviously hopeless; it is just a separate project.

**`org.eclipse.swt.custom`.** Ranked by "how much does it hurt":

| class | cost | note |
|---|---|---|
| `StyledText` | very high | a full text-editor engine: line styling, ranges, bidi, folding, caret, word wrap, `LineStyleListener`. If the app uses it as an *editor*, this is a project of its own. If it uses it read-only for coloured text, `SJEditorPane`'s RTE-subset codec precedent (D_jtextpane/`RteHtmlCodec`) is the model. |
| `CTabFolder` / `CTabItem` | medium | Vaadin `Tabs`/`TabSheet`; the custom-drawn chrome (curved tabs, close buttons, gradients) is CSS. Widely used — RCP's own view stacks are CTabFolder. |
| `TableEditor` / `TreeEditor` / `ControlEditor` | medium-high | "put an arbitrary widget on top of this cell". The SWT analogue of the `JTable` cell-editor problem SB-Emulators classifies as (b). Vaadin `Grid` has editors but not "arbitrary control positioned over a cell". Likely (b) + a documented replacement idiom. |
| `ScrolledComposite` | low-medium | subsumed by Vaadin scrolling, like `JViewport`/`JScrollBar` are today (bare-`Div` bookkeeping shells). |
| `SashForm` | low | Vaadin `SplitLayout`. |
| `CCombo`, `CLabel`, `StackLayout`, `TableCursor`, `TableTree` (dead) | low | mostly styling variants. |

**The dispose lifecycle.** `widget.dispose()`, `isDisposed()`, `DisposeListener`, disposal
cascading to children, `ERROR_WIDGET_DISPOSED` on any post-dispose access. Mechanically simple
(one flag, one guard in a `checkWidget()` equivalent, one cascade) but it must be *everywhere*, on
every method, which is precisely the kind of blanket contract a generator should emit rather than a
human. Note it also has a positive side: dispose gives us an unambiguous detach signal, cleaner
than the Swing `removeNotify` inference SB-Emulators does today (D_shutdown_lifecycle).

**Graphics resources are ported, not reused.** `org.eclipse.swt.graphics.{Color, Font, Image, GC,
Region, Cursor, FontData, ImageData, Rectangle, Point}` — `Color`/`Font`/`Image` take a `Device`
and are disposable, so they cannot be the JDK's. This inverts CLAUDE.md's "everything else is
reused unchanged from the JDK" rule: in the SWT port, the *whole* graphics package is ours.
`Rectangle`/`Point`/`FontData` are trivial POJOs; `Image` is the interesting one (loading from
`ImageData`/streams → a Vaadin `StreamResource`, with `getImageData()` read-back being the lossy
direction, cf. R_vaadin_first). `GC` is the wall (above).

**Widget-per-item tables and trees.** `TableItem`/`TreeItem`/`TableColumn`/`MenuItem` are
`Widget`s, and app code holds and mutates them (`item.setText(1, "x")`,
`item.setBackground(...)`, `item.setData(myPojo)`). They must *not* each get a Vaadin peer —
they're rows in a `Grid`. So they become bookkeeping shells with no peer of their own, the pattern
SB-Emulators already uses for `table.JTableHeader` and the `JMenuItem` family (peer on a bare `Span`,
rendered by the parent's tree-rebuild). `item.setData(pojo)` → the Grid's bean; the
`Grid`'s `DataProvider` is fed from the item list. Non-trivial but it is a *known* pattern here,
which is the point of doing SWT second rather than first.

**Native escape hatches.** `OLE`/ActiveX (Windows COM embedding — unmappable, and if the app uses
it, that view is a rewrite), `Program.launch`, `Tray`/`TrayItem`, `Browser` (an embedded browser
inside a browser — an `<iframe>`, mostly fine, though `evaluate()`/`BrowserFunction` round-trips
are interesting), `Printer`/`GC`-based printing (→ D_printing's PDF printer), `Clipboard`/`DND` (SB-Emulators has
both as cross-cutting capabilities already), `internal_new_GC` and the `handle` field (any app
touching those is doing something we cannot follow).

## The technique changes: package shadowing, not import-swap

This is the biggest structural difference and it deserves its own decision when the time comes.

SB-Emulators does **import-swap** (`javax.swing.*` → `vaadinx.swing.*`) for one reason: `javax.swing` lives
in the JDK's `java.desktop` module, and shadowing a platform module is a fight we decline
(CLAUDE.md: "Not binary-compat — we do not shadow the JDK's `javax.swing` module").

**`org.eclipse.swt` is not in the JDK.** It is an ordinary jar
(`org.eclipse.platform:org.eclipse.swt:3.134.0` on Central, plus per-platform fragments). Nothing
stops us from publishing an artifact that exports the same packages and letting the app swap one
dependency coordinate.

| | shadowing (`org.eclipse.swt.*` is ours) | import-swap (`vaadinx.swt.*`) |
|---|---|---|
| migrator's source change | **none** — swap a dependency | rewrite every import, in app code *and* in every library that talks SWT |
| JFace, Forms, third-party SWT libs | **work as published binaries** | each needs a source fork + rewrite (the `third-party/` lane, but at JFace scale) |
| platform fragment jars (`swt-win32-x86_64`…) | disappear; ours is platform-free | same |
| side-by-side with real SWT in one JVM | **impossible** (one package owner per classloader) | possible |
| incremental view-by-view migration | no — whole-app cutover | yes |
| the "which layer am I on" prompt at the stage-2→3 boundary | **gone** — no import line to tell you | present by construction |
| IDE/compile-time confusion risk | high (types look identical, behaviour differs) | low |
| the local-LLM stage-2 port ([local-llm-migration-target.md](./local-llm-migration-target.md)) | **mostly moot** — there is no mechanical rewrite left to do | the same lever as Swing |

**Leaning: shadow.** The JFace row alone decides it — see the next section. But note what it costs:
SB-Emulators' whole four-stage migration arc (CLAUDE.md §Migration arc) is built on the import line being
the migrator's layer marker, and shadowing erases that. The SWT story would need its own arc
description, probably: *stage 2 = swap the dependency, everything runs; stage 3 = rewrite a view at
a time onto surrogates/Vaadin, dropping SWT imports as you go.* That is arguably a **better** arc
— stage 2 becomes a build-file edit instead of a codebase-wide rewrite — and worth designing
deliberately rather than inheriting.

Coordinate convention, following the `third-party/` precedent (artifactId carries the *upstream*
API version, `<version>` carries SB-Emulators'): `swingbridge-swt-3.134`.

**Open hazard:** a shadowed `org.eclipse.swt` on the classpath alongside a real one (transitively
dragged in by some library's dependency) is a silent, confusing failure. Needs a build-time
enforcer rule, like the root pom's existing `maven-enforcer-plugin` usage.

## The big lever: JFace is pure Java

`org.eclipse.jface.*` — `TableViewer`, `TreeViewer`, `IContentProvider`, `ILabelProvider`,
`ViewerComparator`, `ViewerFilter`, `Dialog`/`TitleAreaDialog`, `Wizard`/`WizardPage`,
`IAction`/`ActionContributionItem`, `ImageRegistry`/`ColorRegistry`/`FontRegistry`, JFace
DataBinding, `FieldAssist` — is **plain Java sitting on the SWT API**. It creates `Table`s, adds
`Listener`s, sets `TableItem` text. If our SWT is faithful, JFace runs on it *unmodified*.

This is the same insight as "RCP introduces no components of its own", one layer down, and it is
the reason the "tables, trees and forms" description is good news: those 50–60 views
almost certainly build their tables through `TableViewer` + a `ContentProvider`, not by poking
`TableItem`s. The emulation target is the *narrow* API underneath the viewers.

**Existence proof, and it is a strong one:** Eclipse RAP has shipped `org.eclipse.rap.jface` — a
fork of JFace over a non-native SWT implementation — for two decades, currently at RAP 4.7.0
(June 2026, quarterly with the Eclipse simultaneous release). Somebody has already established
that JFace-over-a-remoted-SWT works. That they had to *fork* it rather than reuse the binary is
also data: see the caveats.

Caveats, none fatal, all worth measuring:

- JFace measures text with `GC` in places (column autosizing, `Dialog.convertWidthInCharsToPixels`
  and friends). A `GC` that answers `textExtent()` with a plausible font-metric estimate rather
  than throwing is probably enough — the same "close-enough, cosmetically" latitude as R_layouts_close_enough. This
  is an argument for `GC` having a *partial* implementation (measuring yes, drawing no) rather
  than being wholly (b).
- `ImageRegistry`/`ColorRegistry`/`FontRegistry` are typically initialised in a plugin `Activator`
  and are process-global — the documented single-sourcing gotcha in RAP land, and a tenancy issue
  in ours. Belongs to the RCP file, not here, but it leaks into plain-SWT apps too.
- `org.eclipse.ui.forms` (`FormToolkit`, `Section`, `ExpandableComposite`, `ScrolledForm`,
  `FormText`, hyperlinks) — the scenario says "forms", so assume it's used. Mostly pure Java, but
  its chrome is custom-drawn with `GC` (gradients, twisties, section borders) and `FormText` is a
  custom-drawn markup renderer. Prime candidate for a `third-party/`-style fork module where the
  drawing is replaced by CSS — exactly the `swingbridge-emulators-jgoodies-forms-1.2.1` playbook
  ([M1D_addon_packaging](../migration/1-swing-to-emulators/decisions.md)).
- **Licence lane.** SB-Emulators is GPLv2 + Classpath Exception; SWT/JFace/Forms are EPL.
  *Reimplementing* an API is clean and inherits the project licence; *forking source* creates an EPL
  module. The `third-party/` per-module licence lane already exists (the BSD JGoodies fork) so the
  mechanism is there — but "no EPL source pasted into an SB-Emulators module" needs to be a hard rule
  from day one of any prototype, and the packaging should make it structurally hard to violate. Note
  the relicensing sharpened this, it did not soften it: R_gpl_provenance rule 4 already forbids the
  paste repo-wide, and EPL-into-GPLv2 is a worse mismatch than EPL-into-Apache was.

## The mixed-app dividend: `SWT_AWT` becomes almost free

Natively, embedding Swing inside SWT (`SWT_AWT.new_Frame(Composite)`) is the worst part of a mixed
app: two event loops, two native windows, flicker, z-order and focus bugs, resize races,
platform-specific breakage, and a long history of "works on Windows, hangs on GTK".

**In the emulated world all of that evaporates**, because there is no native window on either side
— an emulated SWT `Composite` and an emulated Swing `JPanel` are both just Vaadin components in
one component tree, driven by one thread model:

- `SWT_AWT.new_Frame(composite)` → a `vaadinx.awt.Frame` whose peer is attached into the
  composite's peer element. One method, structurally.
- `SWT_AWT.new_Shell(display, canvas)` (the inverse — SWT inside Swing) → symmetric.
- Two event loops collapse into one: SB-Emulators' `EHelper.callSwing` envelope (R_callswing_envelope) plus a `Display`-side
  equivalent, both over the same UI-fiber runner and the same Vaadin session lock. Cross-toolkit
  `asyncExec`/`invokeLater` is just a queue hop.
- Focus, z-order, sizing, repaint: DOM concerns, i.e. solved by construction.

**This is the strongest single argument for the SB-Emulators approach over the obvious alternative
(Eclipse RAP for the SWT half).** RAP cannot render Swing content at all; a view containing a
Swing map or a train-schedule widget has no path under RAP short of a rewrite. Under SB-Emulators, the two
toolkits are the same runtime. The scenario's "in some rarer cases we show Swing content inside the
view" is precisely the case that only this approach covers — and it should be measured early,
because if it is 3 views out of 60 the argument weakens, and if it is 20 it is decisive.

## Where SwingBridge Streamer fits — and why SWT is genuinely harder for it

Streamer does not really support SWT today, so the asymmetry
is worth stating: it is technical, not a matter of priorities.

[SwingBridge Streamer](https://vaadin.com/docs/latest/tools/modernization-toolkit/swing-bridge) runs the
migrator's **unmodified** Swing app server-side and streams its rendered UI to the browser. That
works because **Swing is pure Java painting into an offscreen image** — a headless JVM is the whole
requirement. **SWT is the opposite: a thin binding over native platform widgets** (GTK / Win32 /
Cocoa) with real OS window handles. Running it server-side needs a display and a toolkit *per user*
— Xvfb + GTK, in practice a container per session — which is a different operational model
(per-user processes, memory, lifecycle) rather than a missing feature. Streaming an SWT app that way
would also still hand the browser an image, not Vaadin components.

Two consequences for how we answer:

- **For the SWT half, the emulator route has no pixel-streaming shortcut behind it.** Whatever we
  say about SWT, we cannot fall back on "and Streamer covers the rest" the way we can for Swing
  (where Streamer genuinely covers `Graphics2D` painting and third-party toolkits that SB-Emulators
  excludes).
- **The routes still compose usefully for this shape**: Streamer on the Swing/gantt half
  *now*, giving something in a browser early, while the SWT/RCP question is being prototyped.
  That is the documented composition — Streamer first, then port view-by-view.

## What would be reused vs. what is new

Reused essentially as-is — this is why SWT-second is much cheaper than SWT-first:

| existing SB-Emulators machinery | serves SWT how |
|---|---|
| vaadin-blocking-dialogs' UI fibers (D_callswing_loom/D_joptionpane) | `syncExec`, `readAndDispatch`/`sleep`, modal `Dialog.open()` |
| `vaadinx.awt.CssEmittingLayoutManager` (M1D_custom_layoutmanager) | SWT `Layout` subclasses → CSS Grid/flex |
| `EHelper.onUnimplemented` + R_match_swing_errors triage + `WarnInventoryTest` exit gates | the whole incompleteness discipline, unchanged |
| `EHelper.callSwing` R_callswing_envelope envelope | peer→toolkit callback funnel; SWT needs the same with `Display` semantics |
| border→CSS (`vaadinx.swing.border.*`), tooltips, focus/`InputVerifier`, `Shortcuts` | SWT equivalents are *simpler* (no `InputMap`) |
| per-session/tab lifecycle, `AppTab`, tab-close detection (D_shutdown_lifecycle, D_session_scoped_pools) | `Display` and `Shell` scoping |
| `java.util.prefs` → localStorage (D_preferences) | Eclipse prefs need the same trick, different API (RCP file) |
| clipboard, DnD, PDF printing (D_printing) | SWT `Clipboard`/`DND`/`Printer` sit on the same capabilities |
| `:generator` | reflect over `org.eclipse.swt.widgets.*`, emit stubs — SWT's flat, model-free API is a *better* generator target than Swing's |
| Karibu test harness + R_java_karibu_tests Java tests | unchanged |
| Sampler pattern + per-route exit gates | an `swt-sampler`, same role |

Genuinely new work: `Display` + the event loop; the dispose lifecycle; the graphics package;
style-bit peer dispatch (easy, just new); widget-per-item `Table`/`Tree`/`Menu` bridging;
`SWT_AWT`; the shadowing packaging.

Module sketch (not a proposal, a sketch): `:swt-emulators` exporting `org.eclipse.swt.*`, depending
on vaadin-blocking-dialogs and on `:surrogates` where a surrogate already carries the behaviour. Whether SWT needs
its own `:swt-surrogates` or peers directly on stock Vaadin is an open question — R_vaadin_first's surrogates
are defined as *Swing*-flavoured, and much of SWT's behaviour is thin enough that a direct
Vaadin peer may be right. Note `:emulators` and `:swt-emulators` must be co-installable in one app
(that is the whole point) which means no shared mutable statics between them and one shared
`EHelper`-level session/thread substrate.

## The event loop — the one thing a prototype must settle first

Sketch, to be falsified:

- `Display` is **session-scoped**, resolved the way SB-Emulators already resolves per-session state
  (cf. D_session_scoped_pools's session-scoped pools). `Display.getDefault()` / `getCurrent()` return the session's
  display. More than one navigated live UI per session → throw, same rule as D_session_scoped_pools.
- The "UI thread" is the UI fiber a callback runs in while holding the Vaadin session lock — one
  per callback, so `Display.getThread()` needs a stable identity of its own to return; `checkWidget()` compares against it and throws the real
  `SWTException(ERROR_THREAD_INVALID_ACCESS)` otherwise. R_match_swing_errors-compliant *and* faithful.
- `asyncExec(r)` → enqueue + `UI.access`. `syncExec(r)` → if on the UI thread, run inline;
  otherwise enqueue and park the caller until done (this is `invokeAndWait`, which SB-Emulators has).
- `timerExec(ms, r)` → the D_timer_swingworker session-scoped scheduler, already built for `javax.swing.Timer`.
- **`readAndDispatch()`** → drain one pending item, return `true`; return `false` when the queue is
  empty. **`sleep()`** → park the virtual thread until the queue becomes non-empty or a
  `timerExec` fires. Then the canonical `while (!shell.isDisposed()) { if
  (!display.readAndDispatch()) display.sleep(); }` becomes a virtual thread that parks and wakes on
  browser events — structurally the same trick as D_callswing_loom's modal parking, but driven by a loop the
  *migrator* wrote rather than one we control. Unknowns: what wakes `sleep()` (browser event
  arrival must signal the queue), whether the Vaadin session lock can be released across the park
  (it must be, or nothing else in the session can run), and re-entrancy when a nested loop opens
  inside a callback (modal dialog inside a modal dialog — R_callswing_envelope's nested-`callSwing` inline rule is
  the precedent).
- `Shell.open()` on a modal shell: SWT's modality is *cooperative* (the app spins the loop), so
  there is nothing to emulate beyond making the loop park. Arguably simpler than Swing modality.

If this works, everything else is grinding through widgets. If it doesn't, the whole thing is in
trouble — so spike it standalone, before any widget work.

## A plausible first-slice scope

Shaped by "50–60 JFace-viewer views", i.e. deliberately not Sampler-exhaustive:

`Display`, `Shell`, `Composite`, `Group`, `Label`, `Button` (PUSH/CHECK/RADIO/TOGGLE), `Text`
(SINGLE/MULTI/PASSWORD/READ_ONLY), `Combo`/`CCombo`, `Table` + `TableColumn` + `TableItem`
(incl. `SWT.VIRTUAL`, `SWT.CHECK`), `Tree` + `TreeItem`, `TabFolder`/`CTabFolder`, `SashForm`,
`ScrolledComposite`, `ToolBar`/`ToolItem`, `Menu`/`MenuItem` (bar + popup), `Spinner`, `Scale`,
`Slider`, `ProgressBar`, `DateTime`, `Link`, `Sash`, `Separator`, `MessageBox`, `FileDialog`,
`DirectoryDialog`, `ColorDialog`, `FontDialog`; layouts `FillLayout`, `RowLayout`, `GridLayout`,
`FormLayout`, `StackLayout`; `Event`/`Listener`/`TypedEvent` + the typed-listener family;
`graphics.{Color, Font, FontData, Image, ImageData, Point, Rectangle}` and a **measuring-only**
`GC`; `Clipboard`/`DND`; `SWT_AWT`.

Excluded in the first slice: `StyledText`, drawing `GC`/`PaintListener`/`Canvas`, owner-draw
tables, `TableEditor`/`TreeEditor`, `Browser`, `OLE`, `Tray`, `Printer`, accessibility,
`Region`/`Cursor`/`Transform`, `internal_*`.

## Effort — how to talk about it honestly

The off-the-cuff figure for this is "SWT is as big as AWT/Swing, so another ~4 months". Refining
that:

- **Class-count-wise SWT is smaller than AWT+Swing**, and per-widget *behaviour* is much thinner
  (no models, no L&F, no `Action`/`InputMap`). Measure it rather than assert it: `javap` over
  `org.eclipse.platform:org.eclipse.swt:3.134.0` and `org.eclipse.jface:*`, exactly the way the
  JGoodies triage was measured against its published jar
  ([M1D_addon_packaging](../migration/1-swing-to-emulators/decisions.md)). **Measure before stating
  a number.**
- **~4 months for the first-slice scope above is plausible**, and the JFace lever means that slice
  buys disproportionately more app coverage than the equivalent Swing slice did — because the app's
  view code sits on viewers, not on widgets.
- **It is not plausible for** `StyledText`-as-editor, `GC` drawing parity, owner-draw tables, or
  `TableEditor`. Those must be named as excluded up front, with their occurrence count in the
  target app's codebase as the deciding datum.
- Second-system discount is real but bounded: the infrastructure table above is genuinely reusable,
  the new-work list is genuinely new.

## Open questions

1. Shadow `org.eclipse.swt` or import-swap to `vaadinx.swt`? (Leaning: shadow. Needs a decision
   before any code, because it determines whether JFace/Forms are dependencies or fork modules.)
2. If we shadow, what replaces the migration arc's import-line layer marker, and does stage 3
   (rewrite onto Vaadin) even want an intermediate "surrogates" layer for SWT?
3. Does SWT get `:swt-surrogates`, reuse `:surrogates`, or peer straight onto stock Vaadin?
4. Can `Display.sleep()` park while releasing the Vaadin session lock, and what wakes it? (The
   prototype question.)
5. One `Display` per session or per UI/tab? What is a second browser tab of an SWT app — a second
   `Display`, or the same one with two `Shell`s? (SB-Emulators has this settled for Swing;
   [multi-tab-and-session-scoping.md](./multi-tab-and-session-scoping.md) applies.)
6. `GC`: measuring-only (needed by JFace) vs. fully (b)? Where exactly is the line, and does
   `textExtent` guessing break JFace layouts badly enough to matter under R_layouts_close_enough?
7. `PaintListener`: pure (b) + WARN, or an explicit documented escape hatch onto a real
   `<canvas>`/SVG the migrator retargets by hand? Does the D_printing "hand-crafted static render is
   fine" carve-out extend here?
8. `StyledText`: (b) outright, or read-only-styled-text via the `RteHtmlCodec` (D_jtextpane) precedent?
9. `TableEditor`/`TreeEditor`: (b) with a documented Vaadin-editor replacement idiom, or attempt it?
10. `Table`/`Tree` with widget-per-item semantics: how far does `item.setData`/`setBackground`/
    per-cell fonts go before we're rebuilding a `Grid` renderer per cell?
11. `Image` from `ImageData`/`InputStream` → `StreamResource`: what does `getImageData()` return
    (R_vaadin_first round-trip loss), and does any real code round-trip it?
12. `org.eclipse.ui.forms`: fork module (EPL lane) or excluded? Measure usage first.
13. Licence: is a GPLv2+CE SB-Emulators comfortable shipping EPL fork modules alongside, and what does legal
    want to see? (Reimplementation ≠ forking; keep them in separate modules.)
14. Do `:emulators` and `:swt-emulators` share one `EHelper`-level substrate (session, threading,
    tab lifecycle) — and if so does that substrate need to be extracted into its own module?
15. Testbed: is there an open-source pure-SWT app worth adopting under `testapps/` (via the
    [`adopt-testapp`](../.claude/skills/adopt-testapp/SKILL.md) skill) to measure against something
    other than a hand-built app?

## The smallest useful prototype

Roughly 2–3 focused weeks, ordered so that failure comes early:

1. **`Display` + event loop spike, no widgets.** `Shell`, `Button`, `asyncExec`/`syncExec`, and a
   real `while (!shell.isDisposed()) readAndDispatch()/sleep()` main loop, running in a browser
   with Push. Plus a `MessageBox.open()` inside a click handler (nested loop). **If this fails,
   stop.**
2. **`SWT_AWT` in the same page**: a `vaadinx.swing.JPanel` with a `JButton` inside an SWT
   `Composite`, and a Swing click that calls back into an SWT widget. Proves the mixed-app claim —
   the one thing no other SWT-to-web route reaches.
3. **One JFace view, unmodified JFace binary**: `TableViewer` over a `Table` with a
   `ContentProvider`/`LabelProvider`, sorted and filtered, on `GridLayout`. Proves the lever.
4. **Then and only then**, count: run the import/usage inventory over the target app's codebase (the
   checklist in [eclipse-rcp-rap-osgi.md](./eclipse-rcp-rap-osgi.md#the-measurement-that-decides-everything))
   and turn the estimate into arithmetic.

## What is known and what is not

- **Known: the emulator approach transfers**, and there are concrete reasons SWT is a somewhat
  *easier* toolkit to emulate than Swing — no look-and-feel, no model layer, a stricter thread
  contract, and layouts that map onto CSS more directly. JFace rides on top of the emulated SWT
  rather than needing its own port, which is where the leverage is.
- **Known: the mixed case is what only this approach reaches.** Swing content inside SWT views is
  something a conventional SWT-to-web path (Eclipse RAP) cannot render at all, and that the
  emulated approach gets nearly for free.
- **Known: the exclusions.** Custom-drawn content (`GC`/`PaintListener`/`Canvas`), `StyledText`
  used as an editor, owner-draw tables, in-cell editors. Occurrence counts from the target app
  decide whether they are islands or the whole view layer — that is what the inventory is for.
- **Unknown: the Eclipse RCP shell**, a separate and much less certain question — see the companion
  file. It does not belong bundled into an SWT answer.
- **The routes compose**: Streamer on a Swing half gives something in a browser early without
  prejudicing the eventual target, while the SWT question keeps being prototyped. SWT itself has no
  equivalent shortcut, for the reasons above.
