# Decisions — Swing to emulators

> **IDs are slugs, not numbers.** Entries are identified by a content-derived slug
> (`M1D_static_taxonomy`) with an explicit `<a id="…"></a>` anchor, so numbering never has to be
> allocated — and never clashes across worktrees. Old numeric IDs map at
> [DECISION-ID-MAP.md](../../DECISION-ID-MAP.md).

> **Licence.** This log is Apache-2.0, like every doc in the repository
> ([`PROVENANCE.md` § Lanes](../../PROVENANCE.md#lanes)). JDK source, javadoc, Oracle notices and
> third-party licence text quoted here remain under their own terms; quoting them relicenses
> nothing.


ADR-lite log for the Swing-to-emulators migration step. IDs `M1D_import_swap`, `M1D_runtime_contract`, … never renumber or reuse; content is edited in place as understanding evolves; fully-superseded entries shrink to a one-line pointer at the superseding ID rather than being deleted (preserves cross-refs from code, commits, and sibling docs). Same conventions as [`../../emulators/decisions.md`](../../emulators/decisions.md) (`D*`) and [`../../surrogates/decisions.md`](../../surrogates/decisions.md) (`SD*`).

Entries here are migration-step decisions framed from the migrator's POV. When a decision originated as a project-rule or technical decision elsewhere (an `R*` rule in CLAUDE.md, a `D*` entry in `:emulators`, an `SD*` entry in `:surrogates`), the source is cross-referenced — not duplicated. Edit content here when the migrator-facing framing shifts; edit the source for technical detail.

**Cross-ref convention.** Prefixed IDs are unique project-wide and used **bare**, without file qualifiers — `D*` lives in [`../../emulators/decisions.md`](../../emulators/decisions.md), `SD*` in [`../../surrogates/decisions.md`](../../surrogates/decisions.md), `R*` in [`../../CLAUDE.md`](../../CLAUDE.md) §"Hard rules", `M1D*` in this file. Document and section references (architecture.md, README files) keep their file links.

<a id="M1D_import_swap"></a>
## M1D_import_swap — Import-swap, not binary drop-in

The migration technique is source-level: rewrite `javax.swing.*` imports to `vaadinx.swing.*` and recompile. Compiled Swing jars must be recompiled against `:emulators`; binary drop-in is not supported. The migrator needs source access (or a recompilation path) for every Swing-touching dependency.

**Why:** the technique and the two rejected alternatives are D_import_swap_target and D_awt_peer_hooking_rejected. What matters at the migrator's end: the swap is source-level, so **every** Swing-touching dependency needs a recompilation path — a jar you cannot rebuild is a blocker, not a rough edge.

**Source:** D_import_swap_target. [CLAUDE.md §"Terminology"](../../CLAUDE.md) describes the technique.

**Implication for migrators:** any Swing dependency without source access is a blocker. Pre-rewritten forks of common Swing libraries are no longer an open question — the add-on model is M1D_addon_packaging, the fork-vs-reimplement triage M1D_swap_vs_reimplement, and two add-ons ship today (JCalendar 1.4, JGoodies Forms 1.2.1).

<a id="M1D_runtime_contract"></a>
## M1D_runtime_contract — Runtime contract: WARN on missing, throw on programming errors

Incomplete emulation never causes the host app to fail outright. Methods we haven't implemented log a WARN via `EHelper.onUnimplemented(class, method, args)` and return a sensible default (no-op, default instance, empty value).

Throws fire only on:
- **Inputs Swing itself rejects.** `getComponent(invalidIndex)` → `ArrayIndexOutOfBoundsException`. `setValue(null)` on `SpinnerNumberModel` → `IllegalArgumentException`. Adding a component to its own ancestor → `IllegalArgumentException`. Same exception type Swing would throw under the equivalent observable failure.
- **Enumerated vaadin-loom rescue cases.** `SwingWorker.get()` from the EDT, `SwingUtilities.invokeAndWait` from the EDT — each would have deadlocked under Swing too. Throwing surfaces the bug instead of hanging the request.
- **`EXIT_ON_CLOSE` close-attempt on a DialogStrategy `JFrame`.** Silent log-and-noop was rejected as hiding program-lifetime intent. InlineStrategy maps to session-close; see M1D_mainwindow_split.
- **`Date`↔`LocalDate` conversion without a browser time zone.** `SHelper.toLocalDate` / `SHelper.toDate` — reached through `SpinnerDateModel` and `SJFormattedDatePicker` — throw when `BrowserTimeZone.fetch()` was never called from a UI init listener. A silent UTC fallback would hand back dates quietly an hour off; the wiring oversight is a one-line fix. See SD_browser_timezone.
- **`JFileChooser.showDialog(parent, approveText)`.** `CUSTOM_DIALOG` declares no load/save direction, and in a browser upload and download are different mechanisms that must be picked *before* the dialog opens. Deprecated and throws unconditionally; call `showOpenDialog` / `showSaveDialog` instead. See D_showdialog_throws.
- **A `MainWindowRoute` attaching on a UI where `SwingBridgeEmulatorsBootstrap` never ran.** The app forgot the one SPI line; the route is the earliest place that is knowable, and one throw there beats three late failures elsewhere. See M1D_bootstrap_canonical.

The throw set is deliberately small and enumerated — five cases, maintained in R_match_swing_errors. Future additions land case-by-case.

**Why:** the dual contract and its rationale are D_never_fail_on_gaps and D_gap_severity_triage; R_match_swing_errors is where the enumerated throw set is maintained, so check it rather than this list if they ever disagree. What matters at the migrator's end: the two channels mean different things — **WARN logs are gap inventory, throws are bugs** — so a log full of WARNs is a migration proceeding normally, while a single throw is something to go and fix.

**Source:** D_never_fail_on_gaps, D_gap_severity_triage, R_match_swing_errors.

**Implication for migrators:** WARN logs are the migration progress signal — every WARN is a method the migrator's code calls that `:emulators` hasn't built. Triage them per the buckets in [`spec.md`](./spec.md) §4. Throws are real bugs (in the migrator's code or the migrator's threading), not in `:emulators`.

<a id="M1D_import_rewrite_scope"></a>
## M1D_import_rewrite_scope — Import-rewrite scope

Component-hierarchy classes (`java.awt.Component`, `java.awt.Container`, all `JComponent` subclasses) and Component-touching interfaces (`Icon`, `Border`, `LayoutManager`, `LayoutManager2`) port to `vaadinx.awt.*` / `vaadinx.swing.*`. `java.awt.EventQueue` also ports — carve-out from the Component-hierarchy rule, justified by the silent-failure rule below.

Data types (`Color`, `Font`, `Dimension`, `Point`, `Rectangle`, `Insets`), event sources from `Object` (`ActionEvent`, `ChangeEvent`, `ItemEvent`), action infrastructure (`Action`, `AbstractAction`, `ActionListener`), and models (`DefaultListModel`, `DefaultComboBoxModel`, `SpinnerNumberModel`, `DefaultTableModel`, …) **stay on the JDK**.

**Why:** the underlying rule is "port what would silently fail server-side; leave what works unchanged on the JDK." Component-hierarchy classes have no AWT peer to render against — the rendering target is the browser via Vaadin. `EventQueue.invokeLater` posts to an AWT EventQueue thread that doesn't exist server-side; the runnable would queue and never run. These classes need ported counterparts that bottom out in Vaadin equivalents (browser-rendered components, `UI.access`-driven scheduling). Data types, models, and `Object`-source events work unchanged: `DefaultTableModel`'s data and listener fan-out behave identically wherever it runs; `Color` is a value, not infrastructure; `ActionEvent` carries no Component reference. Limiting the port to silent-failure classes keeps the surface tight and gives future "should we port X?" questions a principled criterion (`Toolkit.beep()` and `Robot` are candidates if a migration target needs them; ad-hoc "port whatever overlaps `SwingUtilities`" was rejected). Inside the `EventQueue` port, the four static EDT methods carry working semantics — `isDispatchThread`, `invokeLater`, `invokeAndWait` delegate to `vaadinx.swing.SwingUtilities` and bottom out in `UI.getCurrent()`; `getMostRecentEventTime` returns `System.currentTimeMillis()` as the closest faithful approximation absent a real event pump. The remaining JDK surface (instance dispatch machinery, `postEvent`, `peekEvent`, `dispatchEvent`, `getCurrentEvent`, `push` / `pop`, `createSecondaryLoop`) is present so migrated code compiles, but each method WARNs via `EHelper.onUnimplemented` and returns a safe default — no AWT EventQueue dispatch thread exists server-side, so the dispatch contract can't be honoured. This matches M1D_runtime_contract: WARN on missing, never crash. An earlier draft kept the class `final` with a private ctor and let unported instance surface produce compile errors; the WARN-stub version was preferred so incidental references — typically defensive code, not load-bearing dispatch — don't block the migration. The same shape applies to `vaadinx.swing.SwingUtilities`: EDT primitives + parent-walk helpers + Rectangle math are faithful; pixel / paint / accessibility / L&F surface WARN-stubs and returns safe defaults.

**Source:** D_whitelist_porting.

**Implication for migrators:** the import-rewrite is mechanical but partial. A regex `s/javax\.swing\./vaadinx.swing./` does most of the work; data types and models stay on `java.awt` / `javax.swing.*` (the few `javax.swing` non-`J*` types — models, BorderFactory, etc.) and don't change. The migrator's IDE optimize-imports pass after the swap should be near no-op for the JDK side. `java.awt.EventQueue` references rewrite to `vaadinx.awt.EventQueue` along with the Component-hierarchy classes.

<a id="M1D_event_port_policy"></a>
## M1D_event_port_policy — Component-touching events port wholesale

All AWT event types that reference Component (`ComponentEvent`, `ContainerEvent`, `KeyEvent`, `MouseEvent`, `MouseWheelEvent`, `FocusEvent`, `WindowEvent`, `InputMethodEvent`, `HierarchyEvent`) port to `vaadinx.awt.event.*`. The corresponding listener interfaces port too (`ComponentListener`, `KeyListener`, `MouseListener`, `MouseMotionListener`, …).

**Why:** the typing argument and the full ported set are D_event_port_policy. What matters at the migrator's end: a ported event is a *different type* from its JDK original, so `instanceof java.awt.event.MouseEvent` against a `vaadinx.awt.event.MouseEvent` returns `false` — the one place the import-swap fails **silently** rather than at compile time.

**Source:** D_event_port_policy.

**Implication for migrators:** `instanceof` checks on these event types must rewrite too — this is a sharp edge. Catch at compile time by removing `java.awt.event.*` imports first; the compiler then flags every reference.

<a id="M1D_single_ui_per_session"></a>
## M1D_single_ui_per_session — Single Vaadin UI per session; per-tab JFrame instance

One running app per `VaadinSession`. The `@MainWindow` route runs `mainUI()` once for the browser tab that first loads the app, and that tab's `UI` becomes the session's live app UI; a second tab in the same session is *curtained* — `MainWindowRoute.onAppAlreadyActive()` renders "already open in another browser tab" instead of running a second copy (the active-UI pointer, [D_active_ui_pointer](../../emulators/decisions.md#D_active_ui_pointer)). `main()` never runs per tab at all — it is pre-boot, once per deployment ([`lifecycle.md`](../../guides/1-swing-to-emulators/lifecycle.md)). Every separate session (a different user, a different browser) runs its own `mainUI()` and gets its own JFrame instance. Multiple top-level windows in one app (multiple JFrames) map to multiple UIs and are not yet supported.

**Why:** Vaadin's UI model is one UI per browser tab. Mapping multiple JFrames to a single UI requires a routing/window-manager layer we haven't designed; mapping one JFrame to multiple UIs requires per-UI state copies. Single-UI-per-session is the smallest contract that works for stage-2.

**Source:** D_single_ui_per_session, D_jframe_as_route, D_active_ui_pointer.

**Implication for migrators:** the static-field "main frame" pattern (`MainFrame.getInstance()`, `App.frame`, `static final JFrame INSTANCE = ...`) silently breaks across **users** — a `static` is JVM-wide, so the second session's `mainUI()` overwrites the first's frame, and one user's listeners end up pointing at another user's frame. Grep for static field assignments of JFrame subclasses and route each per M1D_static_taxonomy (a component is prototype- or tab-scoped, never static and never a session attribute) before migration. Apps with multiple top-level windows are deferred for now.

<a id="M1D_mainwindow_split"></a>
## M1D_mainwindow_split — `@MainWindow` + `MainWindowRoute` + `main()` / `mainUI()` split

The migrator annotates their main `JFrame` subclass with `@MainWindow` and pastes a one-class route scaffold extending `MainWindowRoute` (owning `@Route("")` + `@PreserveOnRefresh` + the `bootstrap()` call into `mainUI()`). The `@MainWindow` annotation is what lets the JFrame ctor find the bootstrapping route to render inline; there is no framework-side auto-scan. The migrator's existing `public static void main(String[] args)` splits into:

- `main(String[])` — process-level entry; runs once per JVM. Logging setup, command-line parsing, config loading, and one-shot work against the shared database or filesystem (schema, seed, first-run bootstrap account).
- `mainUI()` — launch-level entry; runs once per browser tab — not once per `UI`: F5 builds a fresh `UI`, but `@PreserveOnRefresh` keeps the route and `MainWindowRoute`'s per-instance `bootstrapped` gate, so `mainUI()` does not re-run. Constructs the JFrame, calls `setVisible(true)`. Anything that recorded an app *launch* (an audit row, a per-launch counter) belongs here — one launch is now one tab. No `args` — Vaadin's per-UI bootstrap has no JVM-args channel. The migrator-facing routing table — every start-up hook in run order, and what each can reach — is [`lifecycle.md`](../../guides/1-swing-to-emulators/lifecycle.md).

`EXIT_ON_CLOSE` close-op resolution differs by `FrameStrategy`:
- **DialogStrategy** (most JFrames): close-attempt throws `IllegalStateException` — user code calling `frame.dispose()` on a dialog-rendered frame is a programming error in this context.
- **InlineStrategy** (the `@MainWindow` JFrame, rendered inline as the route's content): `EXIT_ON_CLOSE` maps to session-close. The user's session ends; the JVM keeps running for other users.

**Why:** Vaadin's request lifecycle and Swing's `main()` thread don't share a clock. Splitting the entry point lets process-level work happen at JVM startup while UI construction defers to UI activation (per-tab). `@MainWindow` is migrator-owned-route + JFrame-ctor-lookup (not framework auto-scan) to keep `:emulators` framework-agnostic about how the migrator's app wires its routes — the migrator owns `@Route`, `@PreserveOnRefresh`, and the bootstrap call; the framework only provides the abstract `MainWindowRoute` base + the `@MainWindow` annotation as the JFrame↔route handshake.

`EXIT_ON_CLOSE → session-close` (rather than `System.exit`) is critical: one stray `System.exit(0)` from a migrated `WindowListener` would kill every user's session, not just the caller's.

**Exit-side transform — port the main window's `WINDOW_CLOSING` handler to a dialog-free session-destroy hook.** A desktop app's `WINDOW_CLOSING`/`windowClosing` listener commonly *prompts* ("unsaved changes — really quit?") and gates cleanup (close a TCP client, flush, mark offline) on the answer. On a **real** browser tab-close the user is gone, so there is no UI to host that confirm dialog — the emulator's shutdown-path modal park throws an actionable "shutting down" `IllegalStateException` ([D_shutdown_lifecycle](../../emulators/decisions.md#D_shutdown_lifecycle) / `vaadinx.awt.Dialog.parkUntilClose`) rather than showing it, which would abort the handler *before* the cleanup behind the dialog. So the reliable port is to lift the *server-side cleanup* out of `WINDOW_CLOSING` into a Vaadin session-destroy hook, **dropping the confirm** (no user to confirm on close). This pairs with M1D_static_taxonomy's singleton taxonomy — the singleton the desktop app closed from `WINDOW_CLOSING` (e.g. an app-scoped TCP client) is closed from the session-destroy hook instead. SB-Emulators' runtime still fires `WINDOW_CLOSING` best-effort for un-ported apps; this transform is what makes the cleanup actually run (and, once [tab-scope#3](https://github.com/mvysny/vaadin-tab-scope/issues/3) lands, run promptly). A natural mechanical port for the [local-LLM stage-2 target](../../ideas/local-llm-migration-target.md); full framing (incl. the rejected drop-`@PreserveOnRefresh` alternative and the ~85–91 % beacon-reliability ceiling) in [D_shutdown_lifecycle](../../emulators/decisions.md#D_shutdown_lifecycle).

**Source:** D_jframe_as_route, [`emulators/architecture.md` §"Main window route"](../../emulators/architecture.md).

**Implication for migrators:** the split is the one mandatory hand edit in the migration. `System.exit(0)` is hostile in a Vaadin context — see [`spec.md`](./spec.md) §7 hazards and [M1D_guardrails_artifact](#M1D_guardrails_artifact) for the build-time gate that enforces it.

<a id="M1D_no_silent_improvements"></a>
## M1D_no_silent_improvements — Target Swing's contract exactly; no silent improvements

Where Swing's behaviour is documented or de-facto, `:emulators` reproduces it. Where Swing is buggy or undefined, `:emulators` doesn't silently "fix" it — a forgotten `revalidate()` still looks broken; undefined dispatch order isn't stabilised behind the migrator's back; `null` arguments that Swing accepts-and-NPEs-later are not "improved" to throw early.

**Why:** R_no_silent_improvements, which took this entry's two-directional argument when it was promoted to a hard rule. What matters at the migrator's end: debugging stays symmetric — a bug that reproduced on Swing reproduces here in the same way, so what you already know about your own app keeps its value.

**Source:** [R_no_silent_improvements](../../CLAUDE.md#R_no_silent_improvements) (the rule), [D_no_silent_improvements](../../emulators/decisions.md#D_no_silent_improvements) (its rationale), R_best_effort_behaviour.

**Implication for migrators:** debugging is symmetric — if it broke on Swing it'll break here in the same way. Tests that worked under Swing should keep working; tests that depended on undefined behaviour need rewriting (and would have been brittle either way). Don't expect `:emulators` to be "Swing but better" — expect "Swing's contract, in a browser."

<a id="M1D_layout_close_enough"></a>
## M1D_layout_close_enough — Layout is close-enough; pixel reads return dummies

Layouts approximate Swing's output via Vaadin / CSS. Not pixel-accurate (R_layouts_close_enough). Reading `getPreferredSize()`, `getBounds()`, `getWidth()`, `getHeight()`, `getLocation()`, `getSize()` returns dummy values, not rendered geometry.

**Why:** Vaadin's layout is browser-side CSS; the JVM doesn't know the rendered geometry without a round-trip. Synchronous pixel queries from a Vaadin request thread would require asking the browser and waiting — incompatible with the request model. Dummy values are safer than throwing for the (very common) read-without-acting case (toString() implementations, debug logging, defensive code that records a `getBounds()` snapshot it never reads back). Code that *acts* on the dummies (positioning popups absolutely, scrolling to a computed offset, drag-and-drop hit-testing) needs rewrite.

Pixel-accurate layout is out of scope — not deferred, but not planned at all, per [D_pixel_layout_not_planned](../../emulators/decisions.md#D_pixel_layout_not_planned). Code that acts on the dummies needs rewriting; nothing is coming that would make it unnecessary.

**Concrete dummy contract — zero, never `null`.** `getBounds()`/`bounds()` → `new Rectangle()` (0,0,0,0); `getSize()` → `new Dimension()` (0×0); `getLocation()`/`location()` → `new Point()` (0,0); `getWidth/Height/X/Y` → `0`. The `(rv)` buffer overloads zero-fill the caller's argument and return it (AWT's write-into-buffer contract). Zero — not a fabricated `1×1` — because it is exactly what real Swing returns for a *realized-but-not-yet-laid-out* component: code robust against Swing's own pre-layout state stays robust here, and per M1D_no_silent_improvements we don't invent a value Swing never emits (a nonzero dummy can't help rendering anyway — nothing in the CSS render path reads these getters; they are pure read-outs to user code). `null` is rejected: Swing's `getBounds()` is never null, and it NPEs the very defensive reads (toString, logging) the dummy exists to serve. Each read still WARNs — the WARN, not the value, is the migration signal.

**Deferred — layout-keyed lazy-init.** A component that defers work (e.g. a DB fetch) until it is *sized* — keying on `componentResized`, a `setBounds` override, or polling `getWidth() > 0` — never triggers server-side: there is no layout pass to assign child bounds, so `COMPONENT_RESIZED` fires only for top-level `Window`s (real browser resize), not children. Rare in practice — the idiomatic "load when shown" hooks (`addNotify`, `AncestorListener.ancestorAdded`, `HierarchyListener` SHOWING_CHANGED, first `setVisible`) all fire faithfully, and the common geometry-keyed instances live in custom paint, which is out of scope (R_match_swing_errors(b)). If a real target needs the tail, the fix is a one-shot synthetic `COMPONENT_RESIZED` on attach (piggybacking the existing attach→`addNotify` hook), **not** a fabricated nonzero geometry read.

**Source:** R_layouts_close_enough, [`emulators/architecture.md`](../../emulators/architecture.md).

**Implication for migrators:** layout-dependent logic (custom popup positioning, manual scrolling math, hit-testing against `getBounds()`) needs rewrite to Vaadin equivalents (CSS-based positioning, Vaadin layout components, native browser scroll). Defensive reads (logging, toString) are fine. Code that just lays things out and lets the layout manager position them is fine — that's the common case.

<a id="M1D_custom_layoutmanager"></a>
## M1D_custom_layoutmanager — Custom `LayoutManager`: WARN + vertical fallback by default; `CssEmittingLayoutManager` to opt into faithful CSS

A hand-rolled `LayoutManager` does pixel `setBounds` arithmetic driven by child sizes the browser only knows after render — there is no synchronous measurement to feed it server-side, so we can't run it. Two paths:

- **Default (Option A) — WARN + vertical stack.** A container whose installed layout is *not* a `vaadinx.awt.CssEmittingLayoutManager` gets a one-shot `EHelper.onUnimplemented` WARN (re-armed by `setLayout`) and a readable `flex-column` fallback from `Container.doLayout`. No throw — Swing itself doesn't throw when a custom layout misbehaves (M1D_no_silent_improvements / R_layouts_close_enough), so neither do we. The app runs; the WARN surfaces the gap.
- **Opt-in (Option B) — `CssEmittingLayoutManager`.** The public seam a migrator implements to teach the emulator a custom layout by *emitting CSS* (`containerCss` + optional `childCss`) rather than pixel arithmetic — expressing the layout's *intent* in flex/grid, which sidesteps the measurement problem. The framework's default `layoutContainer` routes both hooks through `EHelper.applyContainerCss`/`applyChildCss`, so migrator code never touches `EHelper` or a Vaadin `Element`. All six built-ins (`FlowLayout`, `BoxLayout`, `GridLayout`, `BorderLayout`, `GridBagLayout`, `GroupLayout`) implement it (dogfooding validated the API — GridBag/Group compute per-child CSS in `containerCss` and read it back in `childCss` per the call-order contract; Border keeps its eager add-time `grid-area`).

**Why not throw, why not silent.** Throw was rejected (R_layouts_close_enough files layout divergence as a *minor* WARN-bucket gap, not one of the enumerated throw triggers). The prior silent no-op was rejected as a debugging trap (M1D_no_silent_improvements) — the app looked laid-out-wrong with no signal.

**Why not runtime async measurement.** Running the real layout via a browser round-trip (ResizeObserver → measure → push absolute bounds) buys faithfulness at high cost (async jank, intrinsic-size fidelity, feedback loops); with A ("runs") and B ("faithful when someone invests") covering the space, it's parked near-permanently. Custom layout is also a natural first "island" for the [local-LLM migration target](../../ideas/local-llm-migration-target.md): the local model falls back to A, a human or cloud model authors the B artifact.

**Accepted limitation.** B expresses CSS *intent* — a layout that genuinely needs runtime sibling measurement (e.g. "align B to the tallest of A/C") has no pure-CSS form and is handed back to the migrator (their island, or rewrite the view). B delegates such layouts, it doesn't solve them.

**Source:** R_layouts_close_enough, M1D_no_silent_improvements; the worked example + call-order contract live in the `vaadinx.awt.CssEmittingLayoutManager` javadoc.

**Implication for migrators:** grep `implements LayoutManager` / `LayoutManager2`. A custom layout still compiles and runs — it just renders as a vertical stack (with a WARN) until you either rewrite it to a built-in (`FlowLayout`/`BorderLayout`/…) or implement `CssEmittingLayoutManager` to map its intent to CSS. MigLayout-shaped third-party layouts are a single `CssEmittingLayoutManager` mapping.

<a id="M1D_static_taxonomy"></a>
## M1D_static_taxonomy — Static/singleton migration taxonomy: three scopes, component prohibition, decision tree

The migrator's own `static` state is the deepest desktop→web hazard: a desktop app is one JVM
serving one user, so `static` is a free private global; a server serves every user from one JVM, so
each `static` field is shared across all of them until proven otherwise. We give the migrator a
fixed taxonomy rather than case-by-case advice. A migrated app has exactly **three legitimate
scopes**, named by sharing boundary, narrowest→widest:

- **tab** — one running app instance (distinct per tab, survives F5). The desktop "app + its singletons + its component tree" maps here.
- **session** — one user, shared across all their tabs (`VaadinSession` attribute).
- **application** — one instance for the whole deployment, all users. Realized as a `static` singleton; *"application scope"* and *"singleton"* name the same thing (boundary vs. mechanism).

A migrator runs a five-node decision tree per `static` hit (`Q_method_or_type` → safe *unless* its body
touches a static field, in which case the field routes and the method may stay static with a
rewritten body; `Q_component` → prototype or tab tree; `Q_user_specific` → session; `Q_world_global`
read-mostly → application/static, with `Q_counter` its ambiguous limb; **everything else → tab**). The
migrator-facing tree + worked examples live in
[`../../guides/1-swing-to-emulators/static-fields.md`](../../guides/1-swing-to-emulators/static-fields.md); the
`static` sweep is guide Phase 1 and spec §7 ("Static frame singletons" + "Multi-tab" hazards).

**The default is tab scope; application scope is the exception that must be argued.**
<a id="R_tab_is_the_new_jvm"></a>`R_tab_is_the_new_jvm`: on the desktop `static` meant *JVM-global*, and the JVM's boundary is now the
**tab** — one running app instance — so a `static` field routes to tab scope unless what it holds was
global to *all users* rather than to *the app*. This is a **flip of the tree's terminal verdict — not a
reorder, and not a rewrite of any node**: the nodes and their order are untouched, `Q_world_global`
(renamed from `Q_app_wide`, since under this framing "app-wide" now means *tab*-wide and the old name
says the opposite of what the node asks) and `Q_counter` were always the allowlist, and every
confidently-matched verdict is exactly what it was. What changes is the **direction of the error under
uncertainty**. The prior tree fell through to *"may stay static"*, so an unsure migrator — or an unsure
[local-LLM agent](../../ideas/local-llm-migration-target.md) — produced a silent cross-user leak;
inverted, the same uncertainty produces an over-scoped field, whose worst case is a rebuilt cache and
a memory cost. Choose the default whose failure is loud and cheap.

The framing also names *why* the middle is ambiguous at all, which is what makes the allowlist
principled rather than a taste list: on the desktop "this JVM" and "all users" were the **same set**,
so the author never had to distinguish them and **the source carries no evidence which was meant**.
The usable heuristic for reconstructing it — *does the field's state come from, or feed, a resource
outside the process?* Reference data loaded from the DB or classpath, a connection pool, deployment
config → world-global. Otherwise → app-global, i.e. the tab. Counters are the named exception: their
state is in-process, but they feed one that isn't.

Two allowlist qualifiers are load-bearing and survive the flip **verbatim**, because for them
over-scoping costs *correctness*, not performance — this is the boundary of the "loud and cheap"
argument above and must not be smoothed away:

- **`Q_world_global` says read-*mostly*, and means it.** A write-through shared cache
  (`static Map<Integer,Customer> CACHE` written on save) is correct shared and **stale** per-tab: tab A
  saves, tab B still reads the old row. "It's a cache" is not the allowlist criterion; *read-mostly* is.
- **`Q_counter` stays reachable before the fall-through.** Tab-scoping an ID sequence yields duplicate
  IDs across tabs — data corruption, not a perf regression. Its default (stays static, behaves like a
  DB sequence) is unchanged.

**The component prohibition is now the general rule's special case, not an exception to it.** With the
default at tab scope, "a component is prototype- or tab-scoped" no longer stands apart from the
taxonomy — it is the same default, plus the additional statement that a component may take **neither
demotion**: not `Q_world_global` (a shared component is a hard cross-user bug), not `Q_user_specific`
(a component belongs to a UI, not a session). Data may be demoted out of tab scope on evidence;
components may not.

**Accepted cost — context-bound reads multiply (`C_offthread_reachability`).** Verified 2026-08-25:
`TabScope.getCurrent()` throws unless `UI.getCurrent()` is non-null, and `vaadinx.EmulatorContext`
deliberately leaves both the current UI and the current session `null` inside
`SwingWorker.doInBackground`. So a former static read from a background thread throws. Session scope
has the **identical** hole today — this is not a tab-vs-session distinction — but the inversion routes
far more fields into context-bound storage, turning a rare stumble into a common one. The mitigation
vehicle is `EmulatorContext`, which already carries the browser zone onto worker threads. **Decided
against** (2026-08-25): the read throws and the recipe is hoist-to-EDT — read what you need before
starting the work and pass it in. Widening `EmulatorContext` would make reading shared mutable UI state
from a background thread the frictionless option, in a class whose javadoc is emphatic about not granting
session access off-thread. See [M1D_former_singletons](#M1D_former_singletons).

**Where the routed fields actually live** is [M1D_former_singletons](#M1D_former_singletons):
one `FormerSingletons` holder per app, read through a `static` shim on each field's original class. A
tree that routes a field to "tab" and stops is not actionable, so treat the two entries as one decision
read in two parts — this one says *where*, M1D_former_singletons says *in what*.

**Step 0 of the sweep is a module pre-pass, not a per-field question.** A Maven module with **no
dependency on any component type** *proves* `Q_component` = NO transitively for every field it contains,
which discharges the single most expensive part of the sweep — and it is what makes the flipped default
affordable at all on a large app, where the cost is proportional to the static count and most statics
live in a UI-free service layer. Classify the modules first; then run the (now shorter) tree per field
in the UI-bearing ones.

**The classification itself is mechanical, in two phases, and the first needs no build.** This matters
for sequencing: the ArchUnit verification below needs compiled classes, and at stage-2 day zero the app
may not compile against `:emulators` yet — so the pass that *scopes the swap* cannot be the one that
depends on the swap having happened. Phase one is a source grep for a fixed package list
(`javax.swing`, `java.awt.event` / `.dnd` / `.datatransfer`, `java.applet`, the third-party Swing
namespaces — `com.jgoodies`, `com.toedter`, `org.jdesktop`, `org.swinglabs`, `net.miginfocom` — and
`vaadinx.` so the pass is re-runnable after the swap), run per module against that module's own sources
only, nearest-ancestor-pom wins. **Grep the package token, not the `import` line**: an unanchored match
picks up fully-qualified usage without an import (`javax.swing.JPanel x = …`) for free, and its only
false positives come from javadoc and comments, where the penalty is one wasted look — where an
import-anchored regex is more precise in the direction that hurts. Phase two closes the result over the
module **dependency graph** (`mvn dependency:tree` / `gradle :m:dependencies`): a module that depends on
a Swing-bearing module is itself a candidate unless proven otherwise. Phase two is not belt-and-braces —
it covers the case the grep structurally cannot see, below.

Three constraints keep it honest:

- **It prunes the tree; it does not end it.** `Q_user_specific` still runs per field, because
  service-layer statics are routinely per-user: `SessionUtils.currentSession` (against the perfectly fine
  `sessionFactory`), a `static User` security holder, a cache implicitly keyed by the logged-in user, a
  runtime-mutable "current tenant" or working directory. `Q_component` is the *only* question the module
  boundary answers.
- **Verify; do not take the claim.** Desktop "service layers" leak UI constantly — a service that pops a
  `JOptionPane` on failure, or holds the main frame to parent its dialogs. Run an ArchUnit rule rather
  than trusting the module's name, and note that when it *fails*, its output is the UI-leak list, which
  is useful migration information in its own right.
- **The discriminator is component types, not the `java.awt` package.** A service module using `Color` /
  `Dimension` / `Insets` is still UI-free — those are JDK-reused per D_whitelist_porting. Which is why bare `java.awt`
  cannot join phase one's decisive list, and the resolution has to stay mechanical rather than becoming a
  judgement: an **explicit** `java.awt.X` classifies by `X` against a value-object allowlist (`Color`,
  `Font`, `Dimension`, `Insets`, `Point`, `Rectangle`, `geom.*`, `image.*` — `BufferedImage` is imaging,
  not UI), while a **wildcard** `import java.awt.*` classifies nothing at all and escalates to grepping
  that module for AWT component simple names. The wildcard is not a corner: `testapps/inventory` has 25
  files importing `java.awt.*`. `Toolkit` is deliberately left off both lists — it is a desktop-platform
  assumption, which is [M1D_platform_checks_shellouts](#M1D_platform_checks_shellouts)'s
  lane, not a component.
- **A module can hold components with zero Swing imports**, by extending a Swing type declared in another
  module — which is why phase two exists. `testapps/inventory` is the shape: 13 panels extend an
  app-internal `AbstractFunctionPanel`, and it is `AbstractFunctionPanel` that extends `JPanel`. Split
  across modules, the panels' module greps clean. The dependency closure fails in the safe direction:
  over-inclusion costs a look, under-inclusion is a silently-missed static, which is this hazard's whole
  failure mode. Same reason phase one greps `*.java` **and** whatever else the module compiles — a Gradle
  subproject's Kotlin or Groovy sources are not exempt from holding a `static`.

**What the Swing-bearing set is *not* a filter for.** It answers `Q_component`, and only that.
`Q_user_specific` still runs over every static in every module, UI-free ones included — a
`static User currentUser` in a pure service module is the cross-user leak the taxonomy exists to catch,
and reading the pre-pass as "only Swing-bearing modules need a scope rewrite" would wave it through. The
pre-pass shortens the tree; it does not choose who runs it.

Stage 2 preserves the app's shape, so an existing service layer is an **asset** here (it is the evidence)
and is never something the migration dissolves.

**Four refinements from auditing the tree against the mechanical bar (2026-08-26).** The tree was written
to be run by a human; re-read as instructions for a 30B agent, four nodes left work undone. None changes a
verdict — the point is where the work is spent and which questions are answerable at all.

- **`Q_component` asks for transitive reachability and does not use it.** The node asked *"does the field
  hold, **or transitively reach**, a component?"* — the tree's most expensive question, and under the
  flipped default it changes no verdict on the fall-through path: a field that transitively reaches a
  component and matches nothing else lands on **tab** anyway, which is what `Q_component` would have said.
  `G_lint_blind` says so out loud to the migrator (*"you do not have to prove a field reaches a component
  to route it safely"*), which makes the node's own phrasing the outlier. It is **not** dead, though, and
  deletion was the first draft of this refinement: it earns its keep as a guard on the two *demotions* — a
  holder reaching both a component and user identity (`static AppContext CTX`) matches `Q_user_specific` and
  would be routed to a session attribute, putting a component at session scope, silently. So the question
  **relocates** rather than disappears: `Q_component` now asks about the field's own declared type, and the
  transitive walk becomes a precondition on routing to session or application. Same strength, asked of the
  minority of fields being demoted instead of all of them — and it reconciles the tree with
  `Q_lint_extension`'s decision to drop the transitive lint *tier*, which had left the tree demanding
  rigour of the agent that the mechanical gate deliberately does not supply, with no tool and no bound on
  the walk. Step 0 also does double duty here: a field whose type closure stays inside UI-free modules
  cannot reach a component.
- **`Q_world_global`'s "read-mostly" stops being a judgement.** This entry already concedes the source
  carries no evidence of which meaning the author intended. The reconstruction heuristic stays, but the
  qualifier is now answered by **query**: read-mostly means *no write reference outside initialization*,
  with three write shapes classified as initialization (the field's own initializer, a `static { }` block,
  a guarded lazy-init) and everything else counting as mutation. That converts the write-through-cache trap
  from a prose *watch out* into a find-references result, which is exactly the kind of step the
  [LSP prerequisite](#M1D_lsp_prerequisite)
  buys. It over-demotes one shape — a lazy loader spread across methods that does not read as guarded, whose
  field then rebuilds per app instance — in the safe-wrong direction the fall-through was chosen for.
- **`Q_user_specific` gets no mechanical answer, and the guidance now says so.** No type distinguishes
  `public static boolean isLoggedIn` from a legitimate app-wide flag, so the honest output is a loose rule
  rather than a false procedure: **anything that smells of a user and is not a component goes to session.**
  Worth recording *why* this node is the one that most needs M1D_static_taxonomy's human review-gate: it is the only node
  where the flip's loud-and-cheap argument does not hold. Everywhere else a wrong guess costs a rebuilt
  cache; here wrong-toward-static ships "one login logs everybody in" and wrong-toward-tab ships "your
  second tab is not logged in" — quieter, still a bug, not a memory cost. (The flip does help: tab is the
  safer of the two wrongs. It is not *right*.) One heuristic is worth trying and not yet worth prescribing:
  classify by the field's **writers** rather than its type — a write site whose enclosing method also touches
  credentials, a user object or the session is a strong signal, and it catches `testapps/inventory`'s
  `handleLogin` / `handleLogOut` case. Left as a grep suggestion until a second app confirms it.
- **`Q_method_or_type`'s "before boot" exception was underspecified in a way that inverts it.** It spared a
  static method whose body "only ever runs from a static initializer or `main()` before boot" — and pointed
  the reader at `Q_counter`, which is about ID sequences and says nothing about pre-boot code. Worse than a
  stray pointer: **in an unsplit Swing app the clause describes nothing**, because `main()` *is* the boot, so
  an agent matching it against stage-1 source catches precisely the code that is about to become per-user.
  The concept only exists after Phase 3's split — `main()` is process-level and once per JVM, `mainUI()` /
  the `@MainWindow` ctor is once per app instance per user, and most of a Swing `main()`'s body lands in the
  latter. Since the sweep is Phase 1 and the split is Phase 3, the migrator judges by the call site's
  *intended* destination, and the tie-break is stated: if you cannot tell which side it lands on, do not take
  the exception. The `static { }` half needs none of this — once per classloader, so no per-user state
  whatever the split does; its arbitrary *timing* is `G_construction`'s hazard, not a scope question.

The same audit fixed the tree's locality, which was implicit and cost the agent work: `Q_method_or_type`
asks about a method's **own** body, one level, no callee chasing — correct by construction, because every
static is its own worklist row, so a helper whose callee touches a static field needs no analysis at all.
The callee and the field each route on their own.

**Landed separately from the same design pass**, recorded so this entry is not read as covering it: the
lint and its marker annotation, which is what makes `C_login_flag` —
`public static boolean isLoggedIn`, invisible to every type-based gate — lintable at all. Shipped as
`@IntentionallyStatic` + `noUnvettedStaticState`, and the *mutability-based* three-tier rule drafted here
was wrong: see [M1D_static_sweep_allowlist](#M1D_static_sweep_allowlist) for the two-dimensional rule that
replaced it.

**Hard prohibition: a component is prototype- or tab-scoped, and nothing else.** The three scopes
above are for *data*. A Vaadin/emulator component (frame, panel, any `JComponent`, a model bound into
one, a `Registration`) uses its own two-scope rule: **prototype** — a fresh instance every time it is
needed (a dialog per click, a panel per navigation), no field at all; or **tab** — part of the tab's
app-instance component tree, built in `mainUI()`/the ctor and held in a plain **instance** field on
its owning component, teleported across UIs on F5 by the framework. **Never session-scoped, never
singleton/application-scoped**, and never in a UI-scoped holder.

*The tree is the storage.* An instance field holding a child is not a cache and not a scope holder —
it is how every Swing and Vaadin component is written, and it is where a `static JPanel bodyPanel`
belongs. What the prohibition forbids is a second reference **at a scope wider than the
component's own**: the `static` and the session attribute are wider; the UI-scoped holder is
narrower-and-destroyed (the D_session_scoped_pools orphan bug). A **tab**-scoped second reference is sound, because tab
scope matches the app instance's own lifetime — the component survives F5 as the same instance under
`@PreserveOnRefresh`, so the reference stays valid rather than dangling — and that is what makes
[M1D_former_singletons](#M1D_former_singletons)'s
holder legal rather than a carve-out: the earlier phrasing banned by *location* what should be banned
by *scope*. That phrasing's predecessor ("a component lives in no
field", "never cache the reference anywhere") over-reached further still, into instance fields, leaving
the most common demotion — `static` → instance — without an answer, which is a real failure mode: a migrator
reading it literally tries to rebuild a mutated child at every use. Code outside the tree reaches a
tab-scoped component either by **look-up at use-time** (`UI.getCurrent()` / the active-UI pointer) or
through the **tab-scoped holder** — never by a reference held at a wider scope.

**A `static` accessor over that state may stay static — only its body changes.** `Q_method_or_type` originally
returned "static method → SAFE, leave it", which conflates *stays static* with *needs no work* and
waves through the single most common shape in a desktop Swing app: `MainFrame.getInstance()`, a
static field plus its accessor. `Q_component` takes the **field**; the **method** keeps its signature and its
call sites, and its body becomes a scope look-up (the frame from the tab's tree, a `User` from the
`VaadinSession`). This is the cheapest correct migration — one edit rather than one per call site —
and it is idiomatic vanilla Vaadin rather than a migration hack: a `static` getter resolving from
`UI.getCurrent()` and throwing when there is none is a normal pattern. It also preserves call-site
shape, which is exactly the lever
[`../../ideas/local-llm-migration-target.md`](../../ideas/local-llm-migration-target.md) depends on.
Two constraints make it safe, and both are stated in the migrator-facing worked example:

- **The accessor finds; it never creates.** The memoizing `if (_instance == null)` line must go. The
  method's *shape* is unchanged by the migration, which is precisely what makes it easy to keep the
  memoization by accident — and a memoizing accessor is the original leak with extra steps.
- **Throw when the scope can't be resolved**, rather than returning null or a fresh instance. This is
  migrator code, so R_match_swing_errors's enumerated-throw list does not govern it; the reasoning is R_match_swing_errors's all the same
  — a silently-wrong result surfaces as another user's data, while a loud failure is a one-line fix
  at the call site.

The `noComponentInStaticField` guardrail already agrees with this pattern: it inspects **fields**, so
a `static` method *returning* a component is correctly not flagged.

**Why:**
- **`static` component** leaks the frame across every user — a hard cross-user bug.
- **UI-scoped component** is nuked on F5 (the `UI` is destroyed; the tree moves to a fresh one) — the D_session_scoped_pools orphan bug.
- **session-attribute component** doesn't crash today (`@PreserveOnRefresh` preserves the same instance across F5 — [D_mainwindow_route](../../emulators/decisions.md#D_mainwindow_route); the one-app-per-session curtain — [D_active_ui_pointer](../../emulators/decisions.md#D_active_ui_pointer) — keeps it off two UIs at once) but is an anti-pattern regardless: a component belongs to a UI, not a session, and session-stashing reintroduces the [session-scoped-view failure](https://mvysny.github.io/session-scoped-route/) the moment concurrent tabs land. We do not write code correct only by the curtain's grace.

**Rejected alternatives.**
- **UI scope as the per-tab home** — rejected; it *looks* per-tab but F5 destroys the `UI`. Tab scope (window-name-keyed, F5-surviving) is the real per-tab scope; today it's approximated by a `VaadinSession` attribute under one-app-per-session (M1D_single_ui_per_session/D_active_ui_pointer), never by UI scope.
- **Classloader-per-session isolation** (auto-scoping every `static` without code change) — rejected: the migration must change the code, not fake JVM-global statics per session.
- **Two-pole "user-scoped → session, else may stay static"** (the prior guidance) — kept giving no crisp verdict for the middle (the monotonic counter). `Q_counter`'s default is **stays static** (behaves like a DB sequence), the opposite of the user-specific default — a distinction the two-pole rule couldn't express.
- **Keeping the fall-through at application scope** (the tree's prior terminal verdict) — rejected on error direction. It is the *same* verdict for every field a node matches, so nothing is bought by it; what it changes is only the unmatched residue, where it converts "the migrator wasn't sure" into a silent cross-user leak. Since the residue is precisely where an LLM-driven port spends its uncertainty, the default has to be the one whose failure is a rebuilt cache rather than another user's data.
- **A fourth "tab" node appended to the tree** (ask `Q_tab` last, keep the old fall-through) — rejected as a distinction without a difference that reads as one: a node the migrator reaches only by exhausting the others is a fall-through wearing a question mark, and it invites the reading that tab scope needs *evidence* the way the two demotions do. Flipping the terminal verdict says the same thing and says it once.
- **Trusting a module's name or its package layout** for the Step-0 pre-pass, rather than resolving its
  dependencies — rejected: a "service" module that imports `JOptionPane` for its error path is the common
  case, not the exotic one, and the whole value of the pre-pass is that it *proves* something. An
  unverified claim proves nothing and silently waves through the fields it was meant to discharge.
- **Hoisting `Q_world_global` ahead of `Q_user_specific`** (front-load the allowlist, so the tree reads "allowlist, demote, else tab") — rejected: it lets a cache implicitly keyed by the logged-in user match the read-mostly limb and terminate at application scope, which is the exact leak the tree exists to prevent. Asking `Q_user_specific` first pulls per-user state out before the allowlist can claim it, and it makes the amendment smaller — only the last line moves.

**Detection is split — mechanical floor + human review-gate.** The static sweep is the one migration
hazard that fails **silently** (a mis-scoped field compiles, boots, passes the WARN inventory, then
leaks cross-user under load), so it can't ride the escalate-on-stuck protocol. The `Q_component`
direct-component case gets a guaranteed build-time net — `MigrationGuardrails.checkStaticComponents()`
in the `migration-guardrails` module (M1D_guardrails_artifact; installed by guide Phase 6 step 6) —
which resolves the type hierarchy (catches `static MainFrame FRAME`, which a name-grep misses). Everything the lint can't see (transitive components, collections, session attributes, and
all of `Q_user_specific` / `Q_world_global` / `Q_counter`) rides a **human review-gate** over the finite per-field verdict table, not the
stuck-signal. **The floor is expected to move**: under the inverted default the rule can become
mutability-based rather than type-based (non-`final` static → flag; `static final` of a mutable or
container type → flag; `static final` of an immutable type → pass), which is a *closed* rule with an
*open* exception list instead of today's open rule with a closed catch list — it fails safe, it makes
`C_login_flag` lintable where no type-based rule can, and it lets the transitive-reachability tier be
dropped rather than built. **One refinement is already forced by measurement:** the first tier cannot be
"every non-`final` static", because the log4j-era `static Logger logger = Logger.getLogger(Foo.class)`
idiom omits `final` and appears once per class — in `testapps/inventory` loggers were **4 of the 14**
non-final statics, so a naive tier 1 spends a third of its noise budget on them and teaches migrators to
ignore it. Resolved the other way round from the draft, at
[M1D_static_sweep_allowlist](#M1D_static_sweep_allowlist): the non-`final` tier stays absolute, and the
logging facades join the *immutable-type* set instead, so the fix is to add `final` and the fixed field
then passes. Local-LLM routing: the whole sweep is **sensitive** (scopes the app's own user data —
on-prem only) and **⚠silent** — see [`../../ideas/local-llm-migration-target.md`](../../ideas/local-llm-migration-target.md).

**Coupling to multi-tab — decided for alpha, re-opens only under option 3.** SB-Emulators is committed to
one-app-per-session (D_active_ui_pointer / multi-tab option 1) for the alpha, so the taxonomy targets today's
reality (tab ≈ session). Only if SB-Emulators ever relaxes to genuinely concurrent tabs
([multi-tab option 3](../../ideas/multi-tab-and-session-scoping.md)) do the application-scope verdicts
(shared cache, counter) get re-examined — a per-tab app instance changes what "shared across the
session" means. That is the *only* multi-tab dependency; nothing here waits on it.

**Convergence gate.** Every `static` shape the guide and spec discuss must route to the same
home static-fields.md prescribes (component→tab-tree, user→session, global-read-mostly→static,
counter→static, early-read→defer, **anything else→tab**). Re-check the routing whenever the
migrator-facing static-hazard wording changes — a doc that sends a field somewhere the taxonomy
forbids is the actionable bug. The fall-through is now part of what converges: a doc that ends its
advice at "may stay static" without the tab default has drifted, even though every example in it may
be individually correct.

**Open residual (low priority).** Whether *other* thread-unsafe, session-affine JDK helpers with no
emulator (`Matcher`, `NumberFormat`, `MessageFormat`) warrant the per-session-backing treatment D_date_emulators
gave the date helpers — emulator-internal, or a documented session-store pattern with an `SHelper`
convenience. None has surfaced in a migration target yet.

**Source:** D_single_ui_per_session (static Swing registries are UI-scoped), D_session_scoped_pools/D_active_ui_pointer, D_date_emulators (per-session emulator backing precedent), [session-scoped route](https://mvysny.github.io/session-scoped-route/), [vaadin-tab-scope](https://github.com/mvysny/vaadin-tab-scope).

**Implication for migrators:** the `static` sweep is a Phase 1 pre-flight pass, but it cannot *complete*
there — every field routed to tab is assigned in `mainUI()`, so the transform lands with the Phase 3
`main()`/`mainUI()` split, not just the `Q_component` cases as this entry originally said. Phase 1 is
where you run the tree and tag; Phase 3 is where the tags become edits; Phase 6 adds the guardrails.
Step 0's module classification runs before any of it.

<a id="M1D_addon_packaging"></a>
## M1D_addon_packaging — Third-party Swing library add-ons: one module per library, upstream identity in the artifactId, `vaadinx.<vendor>` namespace, zero core changes

A third-party Swing library's API can be provided by a **standalone jar that depends on `:emulators`
and plugs in with no change to SB-Emulators core**, so a migrated app's `com.jgoodies.` / `com.toedter.`
imports rewrite like every other import and keep compiling. Two exist — `swingbridge-emulators-jcalendar-1.4` and
`swingbridge-emulators-jgoodies-forms-1.2.1` — and the structure they establish is the structure a third one follows.

**The tree.** `third-party/`, flat, one Maven module per library. Flat because the extra
`jgoodies-forms/1.2.1/` level only pays for two upstream versions side by side, which cannot happen
— one app is on one Forms version and two forks never share a classpath. Its own tree rather than a
sibling of `:emulators-printing`, because that module emulates a *JDK* API and is SB-Emulators-authored
throughout, while these provide a *third-party library's* API and may carry foreign code under a
foreign licence. **One module per library is non-negotiable**: the licences diverge, and a licence
boundary that isn't a module boundary is not a boundary. The tree is about whose *API*, not whose
*code* — a module is either a **fork** (upstream sources, rewritten imports, upstream licence) or a
**reimplementation** (SB-Emulators-authored, inheriting the project licence), each module's `PROVENANCE.md` says which, and the migrator
cannot tell the difference because the import rule is identical either way.

**Coordinates: `com.vaadin.swingbridge:swingbridge-emulators-jgoodies-forms-1.2.1:<SB-Emulators version>`.** The artifactId carries the
*upstream* version because that is *which API surface you get* — a migrator on Forms 1.2.1 cannot use
a fork of 1.9 (`FormFactory` → `FormSpecs`), and no Maven version field can express that. `<version>`
carries SB-Emulators', the add-on's own release train. **This is also the answer to the maintenance-lane
question**: pinning upstream's version into the artifactId turns "each fork tracks upstream releases"
into a *new-module decision made on demand*. `swingbridge-emulators-jgoodies-forms-1.2.1` is frozen by construction and
tracks nothing; a 1.9 migrator gets a new module when someone needs one. An archived upstream
(JCalendar, archived 2019) is therefore no different a commitment from a live one.

**Namespace: `vaadinx.<vendor>.*`, mirroring upstream below an SB-Emulators-owned root.** So the migrator's
rule is a pure prefix substitution and a fork's per-file diff against upstream is the package line
plus the import lines. **Same-FQN was rejected**, and it earns a long answer because it is the
seductive alternative and keeps coming back: fork the library, keep the class names, swap only a
coordinate, and the add-on looks like a *drop-in* replacement rather than one more prefix rule. It
does not merely cost more than the import rewrite — it cannot be a drop-in at all:

- **The types change, so the class can never *be* the upstream class.** The forked `FormLayout`
  `implements vaadinx.awt.LayoutManager2` over `vaadinx.awt.Component` / `Container` — an interface
  upstream's `FormLayout` does not implement and, since SB-Emulators ports the whole Component hierarchy
  (M1D_import_rewrite_scope), cannot. Same-FQN would preserve the *name* of a type whose contract
  changed underneath it, which is exactly the silently-wrong shape R_no_vaadin_in_api limb 2 and
  R_no_silent_improvements exist to refuse. Any migrator call site that passes or implements one of
  those types has to change regardless, and the error lands on a line whose import still reads
  `com.jgoodies`, so it reads as "upstream broke" rather than "I am on the fork". The name buys
  nothing where the work actually is.
- **The only population same-FQN would help is already unreachable.** It helps exactly one group:
  consumers whose *bytecode* already names `com.jgoodies.forms.layout.FormLayout` and that cannot be
  recompiled — say a third-party jar built against Forms. But that jar's bytecode also names
  `javax.swing.JPanel`, and SB-Emulators deliberately does not shadow `javax.swing` (M1D_import_swap,
  D_import_swap_target), so it is broken before the add-on reaches the classpath. Everything that
  *can* be recompiled must touch its import lines anyway to reach `vaadinx.swing`, so the extra
  prefix rule rides along in a pass the migrator is already running.
- **Classpath roulette, in exchange for that nothing.** Same-FQN needs `<exclusions>` on the real jar
  in every consumer pom — more migrator work than the import rewrite it was meant to avoid — and
  wherever one is forgotten, jar order decides which `FormLayout` wins, with no compile error and no
  warning. Under `vaadinx.jgoodies` both jars coexist harmlessly. It also forfeits the subset
  boundary below, whose whole strength is that an SB-Emulators-owned namespace means no jar anywhere can supply
  the missing symbol.

The prize was always "you don't port FormLayout's ~2000 lines", never "you don't touch the import
line", and the fork collects it in full under a `vaadinx.` package.
`vaadinx.thirdparty.<vendor>` was rejected as longer for no gain, and versioning the package
(`forms121`) as preventing a collision that cannot occur.

**Subset boundary is enforced by the namespace: ship no class file for the unported surface, and no
stub that throws.** An unported class is then a *compile* error naming the exact missing symbol
(`package vaadinx.jgoodies.forms.builder does not exist`) — earlier and more precise than
`NoClassDefFoundError`, and strictly better than a stub, because the namespace is SB-Emulators-owned so no jar
anywhere can supply the symbol. The boundary is a property of the namespace rather than a promise in
a document.

**How a subset is derived — the method, reusable for every port.** (0) Pre-flight the jar per M1D_swap_vs_reimplement
to pick the strategy. (1) Seed from the target app's real call sites. (2) Copy pristine upstream
sources for the seed set. (3) Rewrite imports. (4) **Compile, and let `cannot find symbol` compute
the closure** — each error names the next class to pull in. (5) Stop: anything the compiler never
demanded is out. (6) **Re-run the pre-flight greps over the closure that actually landed**, because
the seed set is chosen but the closure is not. Steps 0 and 6 exist because the compiler's blind spot
is precisely the runtime hazards — `Graphics` paint, pixel `setBounds`, `FontMetrics`/`Toolkit`
measurement all compile perfectly and fail or lie later. Evidence that the method earns its keep: the
Forms closure came out larger than predicted (`FormSpecParser`, `PrototypeSize`, `LayoutMap`,
`FormUtils`, `LayoutStyle`), and step 6 is what caught `PrototypeSize.maximumSize` measuring with
`FontMetrics` and `LayoutStyle` branching on the server's OS name.

**Dependency direction is one-way and is an invariant.** An add-on depends on `:emulators` (and
`:surrogates` when it needs a peer); **nothing in SB-Emulators core knows an add-on exists**. A core change
made to accommodate an add-on is the model reporting a failure, not a chore. Both ports held to it:
their commits touch only `third-party/**` plus documentation. The extension surface they needed was
already public — `vaadinx.awt.CssEmittingLayoutManager` (picked up by `instanceof`, no registration
step and so no R_no_spi_selfregister SPI hazard), `Container.peerContentElement()`, `EHelper.onUnimplemented`,
`vaadinx.awt.LayoutManager`/`LayoutManager2`, and the protected `JComponent(Component peer)` ctor.

**The one place a core rule bent the add-on rather than the reverse, and it generalises.** Upstream
`JDateChooser` extends `JPanel`, but the `JPanel` emulator is R_leaf_peer_lockdown-locked, leaving `JComponent` as the
reachable base. Harmless for that library; **R_leaf_peer_lockdown lock-down redirects add-on components to
`JComponent` in general**, so a third-party class whose users rely on its being a `JPanel` needs
either a different base or a lock-down carve-out. Expect this to be the recurring friction, not
missing hooks.

**Reactor: default modules, no profile.** Two small modules cost nothing to build, and a profile is a
foot-gun — a fork drifts against a `vaadinx.awt` change and CI stays green because nobody built it.
Revisit at ~10 modules.

**Tests are ours, not upstream's.** Upstream Forms' suite asserts *pixel arithmetic*, precisely what
we deliberately do not reproduce (R_layouts_close_enough, and there is no synchronous measurement server-side), so a
ported suite would fail by design and teach nothing. Assert the emitted CSS instead — the actual
contract.

**Provenance and licence lane, per module:** a root `LICENSE`, a `PROVENANCE.md` (upstream
coordinate, source artifact + checksum, fork-vs-reimplementation, subset, what changed), and a pom
`<licenses>` block overriding the parent's Apache-2.0 (Maven replaces rather than merges).
Source provenance is the **Maven Central `-sources` jar pinned by checksum**, not an SCM tag — the
published artifact is what a migrator's own build resolves, so it is the honest baseline for a diff.

> **The licence limb of this entry is superseded by
> [M1D_addon_upstream_licence](#M1D_addon_upstream_licence) (2026-09-21): an add-on takes its
> upstream library's licence, fork or reimplementation alike.** What this entry used to say — that
> for a reimplementation the licence question is *dissolved* rather than solved (no upstream code,
> so no derivative work), subject to a ⚠️ gate re-opened before publishing — was true as a *finding*
> and is retained in `third-party/jcalendar-1.4/PROVENANCE.md`. It is no longer what picks the
> licence, because the finding's shelf life is the port's size and LGPL-2.1 §3 does not authorise
> the GPLv2+CE the reimplementation route was inheriting. **The fork-vs-reimplementation
> distinction itself is untouched** and still decides whose copyright notice sits on which file —
> everything else in this entry stands.

**Not signed off yet — three of four exit criteria are met.** Both modules build, the two-line
migrator recipe holds, and **a real app now consumes both add-ons by coordinate**: a doc-only
migrator agent found `addons.md` unaided and swapped `com.toedter:jcalendar` /
`com.jgoodies:forms` for `swingbridge-emulators-jcalendar-1.4` / `swingbridge-emulators-jgoodies-forms-1.2.1` in
`testapps/inventory/1-emulators`, which then compiles, verifies and boots (2026-08-27; the tree and
its `STUMBLES.md` are committed, `testapps/inventory/README.md` is the app). What is still open is
the pair of gates that need the app *rendered*: the 13 `FormLayout` panels laid out structurally
correctly per R_layouts_close_enough, and a zero-stub-WARN pass over the form routes. Run 1 stopped
at the login screen, so neither has a verdict — and `FormCss`'s dlu calibration (corrected to 1.03
from a measurement, see that module's `PROVENANCE.md`) gets its first browser judgement at the same
time. Don't let the gap turn into a claim.

**Source:** M1D_import_swap (import-swap is the technique), M1D_custom_layoutmanager (the `CssEmittingLayoutManager` seam an add-on
implements), R_layouts_close_enough, R_vaadin_first, R_leaf_peer_lockdown, R_no_spi_selfregister; per-module detail in `third-party/*/PROVENANCE.md`.

**Implication for migrators:** a library with an add-on costs a coordinate swap and one prefix rule
(M1D_addon_migration_docs); a library without one is triaged per M1D_swap_vs_reimplement. Nothing about an add-on needs wiring — no SPI,
no registration, no init listener.

<a id="M1D_swap_vs_reimplement"></a>
## M1D_swap_vs_reimplement — Swap-vs-reimplement is decided from the jar, before fetching sources; JGoodies Forms swaps and augments, JCalendar is reimplemented by preference

**A library port is itself a stage-2 migration.** The library's sources go through exactly the
import-swap app code does, hit exactly the same hazards, and face exactly the per-view choice M1D_import_swap
already gives migrators. So the first question is not "how do we package this" but **"what does an
import-swap actually produce for this library?"** — and that is decidable from the published jar,
before fetching a single source file:

```
javap -p  <every class>   | grep -E 'paint|Graphics'                        # custom painting
javap -p -c <suspects>    | grep -E 'setBounds|FontMetrics|Toolkit|getScreenResolution'
```

| import-swap produces | signature in the jar | strategy |
|---|---|---|
| **compiles, runs, good** | no custom paint, no pixel/measurement dependency | **pure swap** |
| **compiles, runs, wrong** | pixel `LayoutManager` arithmetic; `FontMetrics` / `Toolkit.getScreenResolution()`; heavy `Graphics` paint | **swap + augment** — replace the offending internals — or reimplement if the augment exceeds a rewrite |
| **compiles, runs, but bad** | a widget hand-built from primitives whose *idiom* is wrong for the web | **reimplement** over the Vaadin-native control |

Custom paint is R_match_swing_errors sub-bucket (b), so it WARNs and drops. Pixel arithmetic and `FontMetrics` /
`Toolkit` measurement are the dangerous row: they compile perfectly and then fail or lie, because
there is no synchronous measurement server-side and a servlet JVM is headless. **The compiler
surfaces neither**, which is why this pass exists. Row three is not a failure — the swap *works* and
we reject it anyway — so it must be argued per library, never asserted.

**JGoodies Forms → swap + augment.** No painting in the subset, but two things compile and then
misbehave: `FormLayout.layoutContainer` is `setBounds` pixel arithmetic (swapped as-is it gets M1D_custom_layoutmanager's
WARN + vertical stack, which across 13 form panels is the *structural* failure R_layouts_close_enough forbids), and
`AbstractUnitConverter` calls `Toolkit.getScreenResolution()`, which **throws `HeadlessException`**
in a servlet JVM. So the fork's value is **the spec parser and the spec model; the layout engine is
what we throw away.** `ColumnSpec.decode("left:max(65dlu;default):grow")` is a real grammar with real
edge cases and exactly the fiddly work worth inheriting. What landed:

- `FormLayout` implements `CssEmittingLayoutManager` and emits CSS Grid. **Gap specs stay real
  tracks** — in Forms a gap *is* a spec in the array, so `grid-column: 8` lands on track 8 with zero
  renumbering and a constraint string translates verbatim; collapsing gaps into a `gap` property
  would force renumbering and could not express asymmetric gaps.
- **The unit converter is amputated, not fixed.** `AbstractUnitConverter` / `DefaultUnitConverter`
  are not vendored at all; `CssUnitConverter` WARNs and returns 0 from every pixel method, and
  `PrototypeSize.maximumSize` is stubbed. `Size.maximumSize` itself is *kept*, along with
  `FormLayout`'s `Measure` machinery, so the upstream diff stays at package + import lines. The
  engine therefore survives as dead code, but the code inside it that could *lie* does not.
- **Dialog units become font-relative**: dluX → `ch`, dluY → `em`, with an empirical `0.88`
  calibration because `ch` measures "0" while a dluX is a quarter of the *average* character. That
  tracks the browser's real font at any zoom, where a baked pixel constant is right at exactly one —
  arguably more faithful to what dlu was *for*. The calibration constant is **not yet validated in a
  browser**; it is the one number to revisit if forms come out consistently too wide or too narrow.
- `LayoutStyle` is pinned to the Windows style (upstream reads the OS name, which on a server
  describes the wrong machine; `setCurrent` still overrides), and column/row groups,
  `CellConstraints` insets and `honorsVisibility` are dropped with a WARN — all three need measured
  content widths CSS never exposes.

**JCalendar → reimplement, by preference and not by block.** Measured, nothing blocks the swap: the
whole drawing surface is three `paint`/`paintComponent` overrides, of which only
`JDayChooser$DecoratorButton` and one anonymous sibling sit inside `JDateChooser`'s closure,
decorating weekday-header cells — under R_match_swing_errors(b) they WARN, drop, and cost a background colour. What the
swap *produces* is the objection: `JDayChooser` builds its grid from ~49 `JButton`s, so each date
field becomes ~49 Vaadin Buttons rendering a hand-drawn calendar, several per view, on a platform
with a native date picker — a ceiling that does not move with effort, because it is the Swing
widget's own design. Against that, the API surface a real app uses is four members, so the
reimplementation is smaller than the swap's supporting-class closure. **The honest counter, recorded
because it may win for the next library:** a swap is mechanical while a reimplementation is code we
own and must keep correct — the size ratio is what justifies it, and it is recomputed per library,
not assumed.

**Source:** M1D_custom_layoutmanager, R_match_swing_errors sub-bucket (b), R_layouts_close_enough; the migrator-facing form of the triage table lives in
[`../../guides/1-swing-to-emulators/guide.md`](../../guides/1-swing-to-emulators/guide.md) §"Third-party Swing
libraries"; per-port detail in each module's `PROVENANCE.md`.

**Implication for migrators:** run the two `javap` greps over each Swing-touching dependency *before*
planning the work. A library that passes both is a swap-and-recompile; one that touches `FontMetrics`
or `Toolkit` will compile and then misbehave, and no test run tells you which you have.

<a id="M1D_addon_migration_docs"></a>
## M1D_addon_migration_docs — An add-on ships its own migration addendum; core holds only an index, keyed by the upstream coordinate

An agent migrating an app unaided walks its dependency list and decides, per Swing-touching library,
whether to replace it with an add-on, vendor and migrate it, or abort. It can only choose "replace"
if it knows the add-on exists — so **something in core must be an index**, and the per-add-on detail
must be reachable from it. Carrying that detail *in* the core guide failed twice over: the import
rule ended up stated in four places (one of them wrong, offering a blanket `com.jgoodies.` rewrite
that mangles JGoodies Looks imports), and landing a third add-on would have meant editing core — the
code direction stayed one-way while the documentation direction did not.

**Split by drift rate, not by generic-vs-specific.**

- **Core owns what changes when *SB-Emulators' set of add-ons* changes**:
  [`../../guides/1-swing-to-emulators/addons.md`](../../guides/1-swing-to-emulators/addons.md), one row per
  add-on, **keyed by the upstream coordinate** — the agent's query is "the app's pom has
  `com.toedter:jcalendar:1.4`, is there an add-on?", so ours is the answer, not the key. A row carries
  enough to *decide* and deliberately not enough to *execute*: coordinate, what it replaces, one
  clause of what you get. Adding an add-on appends a row and touches nothing else.
- **The add-on owns what changes when *the add-on* changes**: `MIGRATION.md` at its module root,
  authoritative, holding the rules, the unported surface and the divergences. Core never restates it,
  so core cannot drift against it.

**The addendum ships twice: at the module root, and inside the jar at `META-INF/emul/MIGRATION.md`**
(one `<resource>` block, one copy in git). The in-jar copy is not the discovery channel — at the
moment an agent needs the instructions the add-on is not a dependency yet, so it resolves the
coordinate from the index first. Its value is **offline**: in an air-gapped shop the jar comes from an
internal mirror, so `mvn dependency:copy` + `unzip -p` yields the instructions with no doc site, no
checkout and nothing leaving the premises — directly serving the local-LLM target — and the
instructions are version-exact by construction. `addons.md` carries that recipe once, generically,
along with the rule to **read it, not vendor it** (a copy committed into the app's tree goes stale
silently).

**Seven fixed headings, free-form prose inside:** *Dependency swap*, *Version match*, *Import
rewrite*, *Not ported*, *Beyond the import swap*, *Known divergence*, *Verify*. Fixed so locating a
rule is mechanical; free-form so a port can say what it needs. **"Not ported" and "Beyond the import
swap" stay in the file even when empty**, with an explicit *none* — an agent must be able to tell
"nothing to do here" from "nobody wrote this section". *Beyond the import swap* is load-bearing
rather than decorative: `JDateChooser` no longer being a `JPanel` is a source edit, and Forms'
`LayoutStyle` pin and installed unit converter are library-wide statics in M1D_static_taxonomy's sense.

**An unported method whose removal changes what the user sees belongs in *Known divergence* as well
as in *Not ported*.** The two sections answer different questions: *Not ported* says the symbol is
gone and the compile error is expected, which reads as a pure API gap and licenses deleting the call
site; *Known divergence* says what deleting it costs. `JDateChooser.getDateEditor()` is the worked
example — upstream code calls it to disable typing while leaving the calendar button live, a Vaadin
date picker has no separable editor, and the field a migrator deletes the call from stays typeable.
A missing symbol the compiler catches is cheap; a behaviour that quietly comes back is not.

**The addendum is written for the weakest reader in the pipeline** — a local model holding this file
and nothing else. So: no rule whose only statement is a cross-reference (SD_browser_timezone becomes "add this line
to your `META-INF/services/…` file"); applicability stated first, so a Forms 1.9 app bails in the
first paragraph; the subset-boundary compile error quoted verbatim, so the agent recognises it as
expected rather than as its own mistake and starts inventing fixes; imperatives with a subject and no
"etc."; and a closing *Verify* block of greps that must come back empty, which turns "follow these
instructions" into "reach this state". Legal notices and the version line are **deliberately
repeated** into it, because it is read with nothing else around it. Each module's `README.md` shrinks
to a signpost: what the module is, fork-vs-reimplementation, and pointers.

**Drift gate — one `MigrationDocTest` per add-on module.** It asserts that the addendum reached its
`META-INF/emul` copy, that all seven headings are present, that the two never-silent sections carry
content, and that `addons.md` names both this module's artifactId and the upstream coordinate it
replaces. **The dependency is inverted on purpose**: the *add-on* checks core's index, so core stays
ignorant and a forgotten index row fails the add-on's own build instead of shipping an add-on no
migrator can discover. Two details worth keeping: it reads its own `target/classes` rather than the
classpath, because `META-INF` entries collide across dependency jars and the first hit wins (the
first draft's `LICENSE` assertion found Apache's text); and it is **duplicated per module rather than
shared**, since a common helper would have to live in core — the wrong direction — or in a module of
its own, which is more machinery than the assertions it holds.

**Open residual (`Q_addon_rows`, content half) — the add-on now has a *second* migrator-facing
channel, and their contents have not been reconciled.** M1D_import_swap_tool gave each add-on a declared
`META-INF/emul/ported-types.tsv`, so an add-on's *Import rewrite* section and its table rows now say
overlapping things in two places — exactly the drift shape this entry split by rate to avoid. Two
questions, both unexamined: can a table row carry what the *Import rewrite* heading currently carries
in prose (the tool already derives the "no mapping for `…builder.PanelBuilder`" line, which is *Not
ported* stated mechanically), and if so who authors it — the row, with the section shrinking to a
pointer, or the section, with the row staying a bare mapping? Nothing is wrong today; the risk is the
ordinary one of two hand-written statements of one fact.

**Source:** M1D_addon_packaging (the packaging model this documents the consumption of), M1D_import_swap, M1D_import_swap_tool (the declared add-on table that is the second channel above), R_no_spi_selfregister; the gate lives
in each module's `src/test/**/MigrationDocTest`.

**Implication for migrators:** start at `addons.md`, then follow the add-on's own `MIGRATION.md`. The
guide covers only libraries with no add-on.

---

<a id="M1D_single_instance_guards"></a>
## M1D_single_instance_guards — Single-instance guards are deleted at stage 2: the session is the instance, and a kept guard inverts into an outage

A desktop app's "refuse to start twice" guard — `ServerSocket` on a fixed port with a `localhost`
probe, a `.lock` file / `FileLock` / PID file, `SingleInstanceService`, a named OS mutex, an RMI
registry bind — is **deleted** during Phase 3's `main()` split, together with its call site and the
raise-the-running-window half (`setState(Frame.NORMAL)` / `toFront()` / `requestFocus()`) it usually
carries. The startup work sitting beside it stays.

**Two reasons, and the migrator only intuits the first.**

1. **Redundant.** A Vaadin session gets one navigated live app UI ([D_session_scoped_pools](../../emulators/decisions.md#D_session_scoped_pools))
   and the app's lifetime is tied to its browser tab ([D_shutdown_lifecycle](../../emulators/decisions.md)). The
   guarantee the guard enforced exists for free, at the scope that means something on the web.
2. **Kept, it becomes a self-inflicted outage.** This is the load-bearing half. The guard assumes its
   resource is per app *instance*; after migration the port or lock file is per *JVM*, shared by every
   user. The first session takes it; every later session's probe then **succeeds**, concludes "another
   instance is running", and executes the `System.exit(0)` branch — terminating the server and every
   other user's session. The failure arrives with the second user, in production, and reads as a
   crash rather than as a migration defect.

**Scope shift is the underlying point**, and it is the same one [M1D_static_taxonomy](#M1D_static_taxonomy)
makes about static state: desktop code owns the machine, migrated code owns a session. Multiple
sessions are multiple *users* — the point of a web app, not a condition to defend against. A guard is
M1D_static_taxonomy's taxonomy applied to a *resource* rather than a field, which is why it needs saying separately:
a migrator auditing static fields will not think of a socket as state.

**Not a candidate for translation.** Nothing replaces the guard — no session-scoped port, no
per-session lock. There is nothing left for it to prevent.

**Recognise by shape, not by name.** The class is called `AppStarter` in the app that prompted this,
and there is no convention to grep for; the recognisable parts are the fixed port number, the
probe-then-bind sequence, the lock-file dance, and a boolean whose true branch calls `System.exit(0)`.
Migrator-facing version: guide step `S_delete_single_instance_guard` + `lifecycle.md` § "How your app ends".

**Found by** `testapps/inventory`, whose `AppStarter` binds `0.0.0.0:45433`, probes `localhost` first,
and un-minimizes the frame on every accepted connection (`Main:80` runs `System.exit(0)` when the probe
succeeds) — recorded as finding F-2 in that app's `PROVENANCE.md`, where it was accepted as a *trust*
question (nothing is read from the socket) before its *migration* consequence was understood.

---

<a id="M1D_platform_checks_shellouts"></a>
## M1D_platform_checks_shellouts — One machine became two: platform checks stay, shell-outs are classified by intent, and intent beats what happens on the dev box

A desktop app owns **one** machine. A migrated app has **two** — the server running the code and the
browser in front of the user — so every call that touched "the machine" gets re-asked *which one?*
That question is the migration work; the call itself usually isn't.

**Platform checks are not migration items.** `SystemUtils.IS_OS_WINDOWS`,
`System.getProperty("os.name")`, `File.separator` compile and evaluate fine, and now describe the
**server**, which is a real thing to describe. The check stays; what it *gates* gets classified:
look-and-feel → ignore (out of scope, WARNs); native code for a **server-side** service → leave, the
customer can keep running the server on Windows; native code for hardware **at the user's end**
(fingerprint reader, smartcard, scanner, serial port) → genuinely broken, because the device is no
longer on the machine running the code and no server-side substitute exists. Only the third row is
work, and it needs a browser API, a local agent, or removal from the feature list. Same triage for
`System.loadLibrary` / JNI / JNA, `java.awt.Desktop`, `SystemTray`, `Robot`.

**`Runtime.exec` is classified by intent, not by command.** *Backend intent* — a converter, an
archiver, a DB tool, a report generator — stays as-is, with two easily-missed checks: the working
directory differs under a servlet, and the binary must exist on the *server* (a Windows-only
invocation on a Linux host is now broken, which is where the platform triage returns). *UI intent* —
the command exists to show something to a human — must be re-homed, because the human is in a browser.
Recognisable by shape rather than by name: `cmd.exe /c start`, `rundll32 url.dll,FileProtocolHandler`,
`explorer.exe`, `xdg-open`, `open`, launching a browser binary, any `Desktop.getDesktop().*`. The
replacement varies by *sub*-intent (document → download; URL → `Page.open` / `Anchor`; print → the
[D_printing](../../emulators/decisions.md#D_printing)
virtual PDF printer; mail → `mailto:`), which is why the guide carries a table rather than a rule.

**Migrate the intent, not what happens on the dev box.** The load-bearing half, and the reason this is
a decision rather than a checklist line. A Windows-only feature ported on a Linux workstation fails
locally for reasons that have nothing to do with the migration, and "it doesn't work on my machine
anyway" is a licence to quietly delete every platform-conditional feature in the app. The migrating
agent reads what the code was *for* and ports that. Corollary for our own testbeds: a dangling feature
gets its missing asset supplied so it *has* a defined outcome to reproduce
([testapps/CLAUDE.md](../../testapps/CLAUDE.md) invariant 1's third relaxation) — placed where the
app's own code looks for it, never where the migration will need it, since "where does this asset live
in a web app?" is the migrator's question to answer.

**A document-opening shell-out is two migrations, and the second is the one that gets forgotten.** The
call becomes a download; the **file** has to move onto the classpath (or `webapp/`), because the
relative path it resolved against the process working directory now means somewhere inside the
deployment rather than next to an installed app. Grep the file name, not just the call.

**Source:** M1D_import_swap (import-swap is the technique), M1D_static_taxonomy (the same one-machine→session scope shift,
applied to state rather than to resources), M1D_single_instance_guards (the same shift applied to a single-instance guard),
D_printing. Migrator-facing: guide step `S_triage_platform` + `platform.md`; detection: hazard row `H_platform_shellout`.

**Found by** `testapps/inventory`: 14 `SystemUtils.IS_OS_WINDOWS` sites that all gate the same
look-and-feel line (13 of them in per-panel dev `main()` methods that nothing calls after Phase 3), and
one `Runtime.getRuntime().exec("cmd.exe /c start " + "help.pdf")` behind Help → Read Manual — a clear
UI intent whose asset upstream never shipped.

<a id="M1D_former_singletons"></a>
## M1D_former_singletons — `FormerSingletons`: one tab-scoped holder for everything the sweep routes to tab, read through a static shim on the field's original class

[M1D_static_taxonomy](#M1D_static_taxonomy)'s
flipped fall-through sends most of a desktop app's `static` state to **tab** scope, and then owes an
answer it did not have: *tab scope is where — concretely, in code the migrator writes?* This is that
answer. One class per migrated app, `FormerSingletons`, holding one member per former `static`, resolved
per app instance, with every read reaching it through a `static` reader left on (or added to) the field's
original class.

The migrator-facing spec — the class, the transform table, naming, contracts, initializer handling,
`static { }` triage, what must **not** go in, the stage-3 exit — is
[`../../guides/1-swing-to-emulators/former-singletons.md`](../../guides/1-swing-to-emulators/former-singletons.md).
This entry is the rationale and the rejected alternatives.

**Why a holder at all, rather than a look-up API.** The obvious alternative was a *downward* look-up —
`find(Class)` / `findNamed(String, Class)` over the app's component tree, which is where this design
started. It answers *"where is it?"* and nothing else, and **roughly half the problem is writes**: a
`static` assigned from a constructor (`static JButton OK_BUTTON; … OK_BUTTON = ok;` in a dialog ctor) is
a *registration*, and no look-up expression can absorb one. A holder covers both halves by construction,
because every `static` that is ever read was assigned somewhere, and that assignment site is exactly the
setter. The look-up API is left as a residue for components that need reaching and never had a `static`
at all — small enough that it may never be built.

**Where the holder lives: `vaadinx.AppInstance`, a lifetime, with tab scope as its storage.** The layer
is not a convenience wrapper over `TabScope` and should not be read as one. `AppInstance` names a
*guarantee* — **state that lives exactly as long as one running app instance, reachable from everywhere
that app runs, including its own shutdown** — and tab scope is how that guarantee is met today. The
migrator's `FormerSingletons.get()` is a one-liner over it:

```java
public static FormerSingletons get() {
    return AppInstance.get(FormerSingletons.class, FormerSingletons::new);
}
```

**Why the layer is structural, not stylistic.** `TabScope.getCurrent()` requires a current `UI`, and that
is not an oversight to fix upstream: a session may hold *N* tab scopes, and the library has no notion of
which one is "the app", so off the UI thread there is nothing for it to resolve *to*. That fact exists one
layer up — [D_active_ui_pointer](../../emulators/decisions.md#D_active_ui_pointer)'s
one-app-per-session, recorded in `vaadinx.AppTab`. Teaching `TabScope` to answer without a UI would mean
pushing a single-app assumption into a general-purpose multi-tab library, which is wrong for that library.
So the missing ingredient is **app identity**, SB-Emulators owns it, and `AppInstance` is where owning it shows up
in the API.

Three further reasons the resolver is SB-Emulators' rather than each app's. **The store stays private** — and
under this framing that is the *contract*, not merely prudence: if
[multi-tab option 3](../../ideas/multi-tab-and-session-scoping.md) ever lands, "one running app instance"
may stop mapping 1:1 onto a browser tab, and a lifetime-named API survives that where "the tab-scope
holder" would have to be re-taught to every migrated app. (It also keeps a choice that was got wrong twice
in one afternoon's drafting revisable at one line per app.) **The contexts are not discoverable.** `TabScope.getCurrent()` requires a current `UI`, and the one place a
migrated app is most likely to read former global state — its `WINDOW_CLOSING` cleanup — has none:
`JFrame.dispatchShutdownClosing` runs request-less on the tab-scope reaper thread or the session-destroy
thread (the same fact [R_callswing_envelope](../../CLAUDE.md#hard-rules)'s inline-shutdown branch exists for), so a
hand-rolled `getValues()` call throws in exactly the handler that flushes caches and saves preferences.
And **the plumbing is SB-Emulators' to do**: `vaadinx.AppTab` already carries the app's identity per session and
is the natural place for teardown resolution to land.

`AppInstance.get` resolves in three steps, and only the first is the store:

- **a current `UI`** → `TabScope.getCurrent().getValues()`.
- **no `UI`, but a current `VaadinSession`** → the same map, through a handle on `AppTab`. This is the
  teardown window — both shutdown paths qualify, since tab-scope's reap runs under `session.access` and
  the session-destroy backstop has the session by definition.
- **neither** → throw. That is `SwingWorker.doInBackground`, and it stays a throw on purpose (below).

**Bootstrap is not too early**, which is the other end of the window and easy to assume wrong. `TabScope.setup`'s init listener runs *"before any route or layout is created or initialized"* (verified against tab-scope 0.2, 2026-08-25), so the scope is ready from the `@MainWindow` route's constructor onward — the entire span in which an app builds its component tree and assigns holder members. The only contexts that precede it are the ones that throw anyway: a `static` initializer at class-load, and a raw background thread.

**`AppTab` holds the values map, not the scope.** Resolving the scope during teardown and then calling
`getValues()` would work — `TabScope.close()` fires its destroy listeners *before* nulling `values` — but
only on the tab-close path. On the session-destroy backstop, tab-scope's own session-destroy listener and
`JFrame`'s are two listeners whose relative firing order nothing pins, so a `getValues()` read there is a
race against `values = null`. Holding the map object directly removes the ordering question rather than
reasoning about it: the map is a value *inside* the scope's `Attributes`, so it outlives the scope's
reference to it. The cost is one field on `AppTab`, populated when `AppInstance` first creates the map.

**Why no session fallback, and what that costs in tests.** The earlier draft of this entry kept a
`VaadinSession`-attribute fallback for one reason: browserless Karibu, where `SwingBridgeEmulatorsBootstrap` skips
`TabScope.setup` under `MockService`, so no scope exists. **That reason is wrong** — measured
2026-08-25. Karibu fakes `ExtendedClientDetails` by default, so `TabScope.init`'s
`retrieveExtendedClientDetails` callback fires and the scope is created browserlessly; `JFrameTabCloseTest`
already drives the entire handshake that way. Dropping the fallback is not merely simpler, it is *better*:
a session fallback would have every migrated app's test suite exercising a store production never uses,
which is the one thing a test store must not do. What it costs instead is `SwingBridgeEmulatorsBootstrap`'s browserless
skip, which has to go (and takes `activateUnderMockForTesting` with it, since the seam it opens becomes
the default). Measured three times on `:emulators` with the skip disabled: on tab-scope **0.2** it cost
two failures, on **0.3** one, and after that one's fixture fix **zero in 1795** — at which point the
removal shipped (2026-08-25), ahead of the resolver it was a prerequisite for.

- `ToolkitTest` — asserted the DEFAULT fallback while *inheriting* the "browser has not reported in"
  precondition from the fixture, so a populated ECD (1846×939) silently moved it onto the populated
  branch. Fixed by establishing the precondition explicitly rather than by re-baselining the numbers:
  the two fallback tests now null the UI's `ExtendedClientDetails` themselves. A test whose premise
  depends on what *else* the session happened to wire is the bug; the assertion was right.
- `JFrameMainWindowTest`, the `EXIT_ON_CLOSE` dispose case — **fixed upstream in tab-scope 0.3**, and it
  was never ours. `updateOrphaned()` did `uis.removeIf(UI::isClosing)`, evicting a closing-but-attached
  UI that `remove()` then required, and `cleanupOrphans()` sweeps *every* scope in the session, so merely
  opening a second tab armed the throw in the first. Filed with a standalone Karibu repro as
  [vaadin-tab-scope#5](https://github.com/mvysny/vaadin-tab-scope/issues/5); SB-Emulators is on 0.3, and the test
  passes with the skip disabled. Worth keeping the entry: it is the reason this measurement was taken
  twice, and the impact varied by who drove the detach — routed to the session `ErrorHandler` (destroy
  listeners still fired, measured) on one path, propagated to the caller on the `EXIT_ON_CLOSE` one.

**Why the reads go through a shim on the original class.** A bare `public static final JPanel P` forces
its call sites to change under *any* scheme — a field read cannot become a holder read without becoming
a method call — so `Toolbar.getP()` is free relative to `FormerSingletons.get().toolbarP`, and buys
three things: the call site keeps naming the class the author wrote, the holder's internal shape
(field vs. lazy accessor) never leaks, and the two read recipes collapse into one — *keep the accessor
where it existed, create it where it did not*. That last point retires what was nearly a special case:
`MainFrame.getInstance()` **is** the shim, already written, so the frame needs no separate rule.

**Why a field that carried an initializer becomes a lazy accessor.** Measured, not reasoned
(2026-08-25): an emulator constructed with **no current UI** does not WARN and does not produce an
unattachable peer — **it simply succeeds**, so `static final JPanel P = new JPanel()` quietly becomes one
panel shared by every user with no runtime signal at any point. (`JSpinner(SpinnerDateModel)` and `Timer`
are the only two that throw, both with pointer-at-cause messages; and a class-load `JFrame` is silently
half-built, since `registerForShutdown` returns early with no current UI, so it never registers for
`WINDOW_CLOSING`.) Two consequences: the lint is the **only** gate for this shape, and *timing is not a
safety constraint* — so the init site is chosen for ordering, re-entrancy and locality alone. A lazy
per-field accessor reproduces class-load semantics exactly (per-field, on first touch), so a cross-class
dependency — `B`'s initializer touching `A.P` — resolves itself as it always did.

**Why the `static { }` block needs no replacement barrier.** Triage by what it touches: most blocks touch
nothing tab-scoped and are left alone (over-migrating them is M1D_static_taxonomy's `G_benign` failure mode); a block
that configures its class's own static UI fields is *the rest of those fields' initializers*, written
separately only because a Java field initializer is one expression, so each statement folds into the lazy
accessor of the field it configures. After that, **nothing tab-scoped is left in the block** — it is empty
or benign. The barrier is not rebuilt, it is relocated per field.

**Rejected alternatives.**
- **A `VaadinSession`-attribute fallback behind the tab scope** — rejected, after two earlier drafts of
  this entry adopted it on successively wrong reasoning. Draft one claimed a shutdown handler cannot reach
  the tab scope at all; that mistook a limitation of one *accessor* for a property of the *scope*. Draft
  two conceded that and kept the fallback for browserless Karibu, which is also wrong — Karibu fakes
  `ExtendedClientDetails`, so the scope exists there (see *Why no session fallback* above). It is defensible
  in the abstract, since under [D_active_ui_pointer](../../emulators/decisions.md#D_active_ui_pointer)
  the session *is* the tab. It is wrong in practice because the only context that would ever take the
  fallback is a test, so it buys a second store whose sole users are the runs meant to validate the first.
  The history is left standing rather than trimmed: two plausible arguments for a second store, both
  false, is the strongest argument for the store being private and SB-Emulators-owned.
- **Resolving the scope during teardown and calling `getValues()`** instead of holding the map — rejected
  on the destroy-listener ordering race described above. Recorded because it is the shape the reachability
  measurement first suggested, and it is correct on the tab-close path; it fails only on the backstop.
- **Eager initialization in the holder's constructor** (or a tab-init listener, or `mainUI()`) — rejected:
  it collapses N independent lazy class-initializers into one eager sequence, making the migrator derive
  the topological order and get it wrong **silently**. It also opens a second bug — an initializer calling
  `FormerSingletons.get()` re-enters before the holder is stored in its scope, and either recurses or
  builds a second holder. A lazy accessor leaves the holder's constructor empty, so neither can arise.
- **Extracting a `static { }` block to a guarded `staticInitXyz()`** called from the class's constructor
  and its static entry points — rejected: it reimplements the JVM's class-initialization barrier in user
  code, and the set of entry points obliged to call it is unbounded (every static method, every static
  field read, every constructor). Unwritable, and uncheckable by any lint.
- **One holder per feature, or per functional area** — rejected as a split axis; see *Why the split axis is
  the module* above. It is the axis that reads most natural to a human reviewer and is the least tractable
  mechanically, which is the trap: nothing about it is compiler-checkable, so two agents sweeping the same
  app produce two different partitions, and a member with two plausible homes has no tiebreak.
- **Accessor pairs throughout the holder** — rejected for the plain case: public fields give one textual
  substitution rule for reads *and* writes, nothing to author per field, and a member count that diffs
  directly against the lint's flagged set. The one thing accessors might buy — throw-on-unset — is wrong
  here, because M1D_static_taxonomy's throw rule governs **scope** resolution, not **value** presence: an unset `static`
  NPE'd on the desktop too, at the same use site, and stage 2 replicates rather than fixes.
- **Routing root-reachable components to an instance field on the `@MainWindow` frame** instead (the tree
  is the storage; derived-from-the-tree cannot go stale) — rejected: the most common shape in a desktop
  Swing app is the frame singleton, and making the most common case the exception is bad guidance design,
  especially for the [local-LLM target](../../ideas/local-llm-migration-target.md) where every branch is a
  chance to route wrong. The staleness worry does not survive inspection either, though no longer for the reason first given
  here: the registry used to be visibility-based, so `setVisible(false)` dropped the frame while the
  desktop `static` held on. [D_window_registry](../../emulators/decisions.md#D_window_registry)
  closed that divergence — `Frame.getFrames()` is creation-keyed now and keeps a hidden frame — so the
  two simply agree, and the rejection rests on its primary ground alone.

**One holder per app; a split needs a compile fact, not a size judgement.** The row this closes
(`Q_holder_granularity`) had leaned on "probably a per-app-size judgement the addendum states rather than
decides", and that lean fails this design's own bar: *"is this app large enough to warrant splitting?"* is
exactly what the [local-LLM agent](../../ideas/local-llm-migration-target.md) cannot answer, and will
answer inconsistently. So the rule is stated, and its trigger is something the agent can *read*:

- **Default — one holder**, in the module holding `main()` / the `@MainWindow` frame. Never split for size.
- **Split only when one holder cannot compile** without adding a module dependency edge, or without
  dragging component types into a module M1D_static_taxonomy's Step 0 just classified UI-free. Then **one per module**,
  `<Module>FormerSingletons`.
- **Placement is then mechanical**, so no member can land in two: a member goes in the holder of the module
  its original `static` lived in.

Three facts make this cheap to get right and cheap to revisit. **Granularity is free at the storage
layer** — `AppInstance.get` keys on the `Class`, so N holders are N keys in one `Attributes` and a split
needs nothing built. **It is reversible at zero call-site cost**, because every read goes through the shim
on the original class, so splitting or merging later touches holders and shims and never a caller. And
**the unwieldy-single-class scenario is close to hypothetical**: measured on `testapps/inventory` — 64
files, ~9,800 LoC — the tree yields **2 holder members**: `appFrame` (from `AppFrame._instance` and
`Main.gui`, two statics that held the same frame) and `appFrameCurrentWindow`. **Six** of the fields
reaching the tree were component-typed, and only those two became members: `menuBar` / `bodyPanel` /
`toolBarPanel` have no reader outside the frame that builds them, so the tree's own *the tree is the
storage* clause makes them plain instance fields on `AppFrame`, and `Main.gui` dissolved into a local.
`isLoggedIn` routed to session; `debug` and the three loggers only needed making `final`;
`stringConstantsMap` and `sessionFactory` took `@IntentionallyStatic`; `serialVersionUID`, string and
colour constants and model constants all pass. **The component-typed count is the one that misleads** —
it is the number a migrator's grep produces, it is three times the holder count here, and quoting it as
the holder count is what makes the worked example contradict the rule it illustrates. Guidance that
dwells on splitting invites splitting for a problem most apps do not have.

**Why the split axis is the module and not the feature.** A module boundary is compiler-enforced and
mechanically readable — *which module is this class in* — and it is also the only thing that can *force* a
split, since one holder must reference every member's type and may have nowhere to live that sees them all.
A feature boundary is subjective, drifts, and admits members that plausibly belong to two; an LLM cannot
decide "billing concern or inventory concern" reliably, and every such branch is a chance to route wrong.
`testapps/inventory` is the case where the two axes disagree and the compile fact settles it: `com.gt.uilib`
(a would-be library) and `com.ca` (the app) read like two holders on a feature axis, and are one jar.

**Under a split, the holder set needs no marker annotation and no naming convention** — it is
`grep -rn "AppInstance.get("`, because the registration site *is* the enumeration, and a holder absent from
that grep is a holder nothing resolves. So the
[completeness diff](../../guides/1-swing-to-emulators/former-singletons.md#checking-you-finished) survives
splitting for free, and this row stays independent of the still-open lint marker annotation
(`Q_lint_extension`) that it would otherwise have had to wait for.

**Residual, flagged rather than solved.** The rule assumes the module graph has a natural sink. An app whose
UI types are spread across sibling modules with no common downstream module gets neither a single holder nor
a clean per-module split. Left until a real app shows that shape.

**Prerequisite.** M1D_static_taxonomy forbids "a second, out-of-tree reference" to a component, and every component
member of the holder is exactly that. The rule needs restating rather than carving out: **no second
reference at a scope *wider* than the component's own** — `static` and session are wider, UI is
narrower-and-destroyed (the D_session_scoped_pools orphan bug), and tab matches the app instance's own lifetime, so the
component survives F5 as the same instance under `@PreserveOnRefresh` and the reference stays valid
rather than dangling. Applied to M1D_static_taxonomy and to `static-fields.md`'s prohibition.

**Accepted cost.** A holder member pointing at a *prototype* component (dialog #2's ctor overwrites
dialog #1's button) faithfully reproduces the original's last-constructed-wins behaviour and pins one
detached component per tab — bounded by tab close rather than by JVM exit, so strictly better than the
desktop managed, but worth saying so nobody reads it as a leak the migration introduced.

**`SwingWorker.doInBackground` throws, and that is the answer rather than a gap.** With the shutdown
window closed by `AppTab`'s handle, the worker thread is the one remaining context that cannot resolve the
holder — `VaadinSession.getCurrent()` is null there by design. The recipe is **hoist the read to the EDT
before the worker starts and pass the value in**, which is a rule the migrator (or the local-LLM agent)
can apply per site without a mechanism to learn. The alternative — extending `vaadinx.EmulatorContext` to
carry the app handle the way it carries the browser zone — was rejected: one edit, but it widens a class
whose javadoc is emphatic about *not* granting session access off-thread, and it would make the wrong
thing (reading shared mutable UI state from a background thread) the frictionless one. A throw here is the
same shape as R_match_swing_errors's other pointer-at-cause throws: a one-line fix at the call site, loudly located.

**Status.** Adopted as the pattern; the spec is written and unvalidated. It graduates from
"pattern" to "prescribed" when `testapps/inventory`'s first migration round exercises it.
`vaadinx.AppInstance` **shipped 2026-08-25**, with the `AppTab` values handle, over the unconditional
`SwingBridgeEmulatorsBootstrap` tab-scope wiring that landed just before it. Covered by `AppInstanceTest` (one instance per app,
one per tab, F5 survival, the no-UI teardown resolution, and the three throws) and by a
`JFrameShutdownTest` case that reads app-instance state from an actual `WINDOW_CLOSING` handler — the
property the whole design exists for, and the one a hand-rolled `getValues()` would fail. Both were
mutation-checked against a no-op `rememberValues` to confirm they can fail. **The name is deliberate, not
incidental:** an earlier draft called it `AppScope`, which collides with the taxonomy's *application*
scope — the widest of M1D_static_taxonomy's three, where this is the narrowest. `AppInstance` takes its wording from
the tab row's own Lifetime cell in [`static-fields.md`](../../guides/1-swing-to-emulators/static-fields.md#the-three-scopes)
("one running app instance"), so the API and the taxonomy name the same thing. The taxonomy row stays
called **tab**, since it is named by sharing boundary; the API is named by lifetime. Holder granularity is
**closed** (one per app, split only on a compile fact — above). The two `static { }` cases that stay
the migrator's judgement are stated as such on
[`former-singletons.md` § `static { }` blocks](../../guides/1-swing-to-emulators/former-singletons.md#static---blocks)
— deliberately left to judgement rather than open.

**The holder is internal storage; the accessor is the API.** Exactly one accessor per member touches
`FormerSingletons` — the static getter that replaced the original `static` field — and every other call
site in the migrated app goes through that getter, naming the holder nowhere. This is stated as a rule
because it is what makes the sweep *mechanical*: with the accessor written once, every existing reference
is a rename (`AppFrame.currentWindow` → `AppFrame.getCurrentWindow()`) rather than a per-reference
judgement about tab scope, and the getter keeping its `static` is already `Q_method_or_type`'s reader
carve-out rather than a new exception. The second-order effect is the one that settles arguments: once
every reader is behind an accessor, **moving a member between its owning component and this holder is a
one-file change**, so the question that surfaces most often — *a widget built by one parent but read from
outside it: does the outside reader force a holder member?* — does not have to be answered correctly the
first time. It does not force one; put the state on the natural owner where there is one (tab scope comes
free, since the owner is itself resolved per tab through the holder) and in the holder where no class owns
it. Recorded from run 3 of `/migrate-inventory-dryrun` (2026-09-04), stumble 2, where
`AppFrame.currentWindow` had exactly this shape and the page's letter and its "smallest number in the
funnel" guidance pointed opposite ways.

**Source:** M1D_static_taxonomy (the taxonomy this completes), M1D_single_instance_guards (single-instance guards), D_session_scoped_pools/D_active_ui_pointer (session-scoped
pools, one app per session), [vaadin-tab-scope](https://github.com/mvysny/vaadin-tab-scope).

<a id="M1D_lsp_prerequisite"></a>
## M1D_lsp_prerequisite — A resolving Java model is a hard prerequisite for the migration: an agent-driven port stops without an LSP, the gate is evidence rather than a claim, and the stop is an emitted artefact plus one named continuation

> **Gate half superseded by [M1D_lsp_recommended](#M1D_lsp_recommended) (2026-09-09).** The
> prohibition below — no sweep verdict from pattern-matching over unread text — stands and is
> discharged by [M1D_static_sweep_tool](#M1D_static_sweep_tool)'s class-file reader. The LSP gate,
> its three checks, its failure taxonomy and the kit's `/lsp-preflight` are gone; the "check 3
> non-functional" observation this entry records was a Claude Code artefact on gitignored files,
> not a `jdtls` capability gap. Kept in full for the reasoning that still holds.

The `static` sweep is the migration's one **silent** hazard — a mis-scoped field compiles, boots, passes
the WARN inventory, and leaks cross-user under load
([M1D_static_taxonomy](#M1D_static_taxonomy)'s
detection split exists for exactly that). Two of its steps cannot be done safely with a regex, so the
tooling that makes them possible becomes a **prerequisite of the procedure**, not a recommendation inside
it: an agent-driven migration with no connected language server stops and tells the migrator to install
one. `jdtls` is named as the reference choice; any LSP that resolves types qualifies. Migrator-facing
at the time: guide.md's Phase 0 and the checklist's, both of which
[M1D_lsp_recommended](#M1D_lsp_recommended) rewrote into *build the app you are about to migrate*.

**Which steps force it, and why a grep is not a substitute.**
[Step 1](../../guides/1-swing-to-emulators/static-fields.md#step-1--build-the-worklist) needs
kind-per-declaration — field vs. method vs. nested type vs. `static { }` — and `Q_component` /
`Q_method_or_type` need *type resolution* ("is this declared type a component, subclasses included",
"does this body touch a static field"). Both degrade into confident guesses rather than into errors.
The measurement that made this concrete: the funnel on `testapps/inventory` is **152 `static` hits → 47
field declarations → 13 reaching the tree → 2 holder members**, and the regex that produced those numbers
is not one anybody should trust on an unread app — it miscounted two of the 152 (a javadoc line and a
method whose parameter list wraps, both read as fields) and breaks on multi-line declarations, generic
methods (`static <T> T of(…)`) and annotated fields. A classifier whose
correctness is per-app-unfalsifiable is the thing the
[local-LLM target](../../ideas/local-llm-migration-target.md) must not be built on. **Since
[M1D_static_sweep_tool](#M1D_static_sweep_tool), Step 1 and these two verdicts come from the stage-1
class files, not from the LSP** — the compiler's own answer, which is neither a regex nor a language
server; the paragraph above is why a regex is refused, and it still is.

**The error budgets differ between the two pre-tree steps, which is why the tools differ.** Step 0's
module partition tolerates false positives — over-including a module costs one look — so a grep plus a
dependency closure is correct there, and its two phases are deliberately build-free-then-verified. Step 1's
output **is** the worklist, and a field missing from it is never revisited, so only a real parse is
acceptable. Same sweep, opposite failure asymmetries; stating it this way is what keeps the LSP requirement
from reading as blanket tooling maximalism.

**The gate demands output, not a self-report** — and this is the part that makes it a gate at all. "Confirm
the Java LSP is connected" is a checkbox an LLM ticks and moves past, so the migrator (or agent) must
produce three results: document symbols for one of their own UI files showing declaration *kinds*, a
definition/hover on a Swing type reference landing in the JDK, and references to one `static` field coming
back with its known call sites. **The second check is the load-bearing one.**
An unimported project returns *perfect* document symbols, because kinds come from the parse alone — so a
wired classpath and an unwired one are indistinguishable until something asks for a type. A gate that only
proves the server answered would pass in exactly the state that corrupts every `Q_component` verdict.

**The third check exists because capability is per-request, not per-server** — added after a `crud` round
(2026-09-09) passed both original checks and then found `findReferences` and `goToDefinition`
non-functional in the same `jdtls` session that answered `documentSymbol` and `hover` fully, hover's
`Source:` line naming the Maven project (the strongest form of check 2). The gate was green and the step it
protects was unavailable, so the run fell back to grep-plus-read and said so. **Its failure branch is the
one that does not stop the migration**, which is not an inconsistency with the ruling above: checks 1 and 2
carry the questions that fail *silently* into a wrong worklist, while check 3 carries only
`Q_world_global`'s read-mostly query — where `grep -rn` plus reading every hit is sound at small scale, and
where a miss demotes a field to tab scope, the safe-wrong direction the tree's fall-through was already
chosen for. Naming the substitute is what keeps it from being improvised, which is the failure mode the
whole gate is shaped against.

**Placed at Phase 0 and reasserted at Step 1, deliberately.** A gate sited only where it is needed fails
by *substitution*: the agent reaches Step 1, finds no LSP, reasons "I'll grep instead", and proceeds — the
precise outcome the ruling exists to prevent. Front-loading also spares the migrator work, since LSP is
load-bearing past the sweep (find-references is what makes M1D_former_singletons's "rewrite the accessor body, leave the
call sites" checkable, and what scopes the import rewrite).

**Front-loading moves *where* the substitution happens, not *whether* — because "stop" is not an action.**
The 2026-08-27 `testapps/inventory` probe reached Phase 0 with no language server and did not stop. It
improvised: read all 64 files in full rather than grepping, answer `Q_component` from the declared type
plus the class hierarchy in the same tree, then reconcile the worklist against `javac` and the Phase 6
lint. It also *worked* — 13/13 against a pre-committed golden table, with the agent's own `javap` count
(49 static fields) agreeing with the lint. That is the substitution failure this ruling names one
paragraph up, occurring at Phase 0 instead of at Step 1. The cause is that the gate terminated in a
non-action: an agent handed "stop" with no branch does not halt, it invents one. It did not disobey —
there was nothing to obey. A gate whose failure branch is absent does not prevent guessing; it makes the
guessing **undocumented**, which is strictly worse than a guess we specified and costed.

**The prohibition and the mechanism are separable, and only the first is absolute.** The prohibition:
Step 1's kind-per-declaration and the `Q_component` / `Q_method_or_type` verdicts may never come from
pattern-matching over text nobody read. That is the silent, cross-user, per-app-unfalsifiable failure, and
it does not relax by an inch. An LSP is the *mechanism* that discharges it at **bounded** cost — it is not
the only thing that discharges it. Note precisely what the probe did: reading 64 files in full is not a
grep, it is a slow, expensive, non-monotonic resolver that satisfies the prohibition while blowing the
budget. A rule welded to one mechanism cannot say that, so it left the honest fallback and the forbidden
one equally unmentioned, and the agent picked between them unaided.

**The failure branch, in four parts.**
- **The stop is emitted, not merely declared.** On failure the agent writes a blocker artefact — which of
  the two evidence checks failed, what to install, the exact checks to re-run — and halts. That is
  something an agent can perform; "stop" is not.
- **The anti-substitution clause is the sentence that was missing.** *If the gate fails you may not invent
  a substitute; the only continuation is the one named below.* The doc's silence is what licensed run 1's
  improvisation, so the fix is a sentence, not a stronger adjective on the existing one.
- **The two failures are different failures with different remedies.** No language server at all → install
  one. Check 1 passes and check 2 fails (project not imported, no classpath) → import the project or run
  one build so the server has a classpath. The second is both the **likelier** failure in practice and the
  load-bearing one this ruling already singles out, and collapsing the pair into a single "fix that before
  proceeding" hands the more common failure the vaguer branch.
- **One named continuation.** With no resolving model reachable, the sweep proceeds by running
  [M1D_static_sweep_tool](#M1D_static_sweep_tool)'s `StaticSweep` over the **pre-migration** class files
  — the worklist it prints is the worklist, on any run, LSP or not — and then reading **the declaring
  classes of the WORKLIST rows** in full for the verdicts (6 classes on `testapps/inventory`, against the
  65 files the earlier continuation demanded). Never by grep-classification. The tool's `--diff` against
  the migrated classes is the completeness check. Before the tool existed the continuation was "read
  every file in the UI-bearing partition, then reconcile against `MigrationGuardrails.checkStaticState()`
  on the pre-migration classes" — kept here only for what it taught: `run()` was never usable as that
  precondition, because the component check resolves `vaadinx.awt.Component` /
  `com.vaadin.flow.component.Component` **by name** and passes vacuously on a pre-swap tree, the same
  shape of false evidence the gate's check 2 exists to prevent. Naming the strong substitute is also what
  keeps a weak one from being improvised in its place — the failure mode is not that agents proceed, it
  is that they proceed by whatever occurs to them.

**No file-count ceiling, but a declared count.** The fallback **self-scopes**: exhaustive reading is
affordable exactly on the small apps where installing `jdtls` reads as overkill, and unaffordable exactly
where the sweep is dangerous, so a published threshold would add a number without adding a constraint.
What it does need is a tripwire for the failure it genuinely has — an agent's attention or context filling
part-way through and degrading silently rather than erroring — so the agent states the file and LoC count
of the UI-bearing partition **before** starting. A declared count is falsifiable against Step 0's own
output; a retrospective "it fitted" is not. One 64-file / ~9,800-LoC data point licenses a worked example,
not a threshold.

**The `jdtls` footgun is called out because it is SB-Emulators' own scar.** JDT compiles with its own compiler into
whatever output directories it is given and rejects some code `javac` accepts — the standing `SJSpinner:476`
false positive, which surfaces as two `SJSpinnerTest` errors and a `:surrogates` failure whenever Eclipse
has built the module (CLAUDE.md § Building). Pointed at a project's `target/` or `build/`, an LSP session
can hand the migrator the same poisoned-output-directory failure, which reads as a bug in their migration
rather than in their toolchain. Mitigation is one line — a workspace data directory outside the project,
and a `clean` build after any IDE/LSP session — and it has to be *said*, because the SB-Emulators-side rule that
already absorbs it does not exist in the migrator's repo.

**Scoped so the rule stays true.** What is required is programmatic access to a *resolving Java model*; for
a human in IntelliJ or Eclipse, the IDE is one, reached through a different interface. The refusal therefore
binds an **agent-driven** migration rather than migration as such — a human can do the Phase 2 import
rewrite by hand and be fine. What is unacceptable for either is running the `static` sweep off `grep` alone.
Overstating it as "no LSP, no migration, ever" would invite a migrator with a working IDE to read the whole
rule as fussiness and discount the parts that matter.

**Rejected alternatives.**
- **Recommend rather than require** — rejected: the sweep's failure mode is silent and cross-user, so a
  recommendation converts into "the agent did its best with grep" and the resulting scope errors are
  indistinguishable from a completed sweep. This is the one hazard where the mechanical floor cannot be
  softened.
- **Gate only at Step 1**, where the need arises — rejected on the substitution failure above.
- **Keep the branchless stop** — rejected on evidence rather than on argument: it is what run 1 ran into,
  and what it produced was an unrecorded substitute, not a halt. The choice was never stop-vs-allow; it was
  whether the improvisation is one we named and priced.
- **Let the fallback be "grep, but carefully"** — rejected: that is the recommend-rather-than-require
  alternative above wearing a hedge, and it fails the prohibition rather than the budget. The permitted
  fallback is more expensive than the LSP, not less, which is the property that keeps it from being chosen
  for convenience.
- **Publish a file/LoC ceiling for the fallback** (say "≤100 files") — rejected: derived from one app, and
  the ceiling that actually binds is the agent's context, which no source-side number measures. The
  declared count plus the mandatory reconciliation covers the same ground falsifiably.
- **Accept a self-reported check** ("LSP: connected") — rejected: unfalsifiable from the artefact, and the
  most likely thing an agent gets wrong is precisely the classpath half, which no self-report distinguishes.
- **Require a full build instead of an LSP** (use `javac`/ArchUnit output as the model) — rejected for
  sequencing: the compiled model is what Step 1 *reconciles against* at the end, but at stage-2 day zero the
  app may not compile against `:emulators` at all, so the pass that scopes the swap cannot depend on the
  swap building. Both are kept, in that order — two independent enumerations agreeing is the sweep's only
  completeness evidence. **This rejection is about the *post-swap* build, and it does not reach the
  pre-swap one** — the app being migrated is by definition a running Swing app, so its stage-1 classes
  exist on day zero, and [M1D_static_sweep_tool](#M1D_static_sweep_tool) reads the sweep's worklist off
  them (kinds and declared types straight from the compiler; `getstatic` / `putstatic` naming the field
  `Q_method_or_type` asks about). That narrows this ruling to what an LSP is still load-bearing for —
  the entry says what, and leaves `Q_lsp_still_hard` open.
- **Ship an SB-Emulators-side static-analysis tool** so no external LSP is needed — rejected as scope: it is a Java
  parser and classpath resolver, which is what an LSP already is, and SB-Emulators would own a second one.

**Source:** M1D_static_taxonomy (the taxonomy and its silent-failure argument), M1D_former_singletons (the holder whose completeness diff
this feeds), CLAUDE.md § Building (the JDT output-directory hazard),
[local-llm-migration-target.md](../../ideas/local-llm-migration-target.md) (the "mechanical for a 30B model"
bar the ruling is measured against), `testapps/inventory/1-emulators/STUMBLES.md` stumble 1 (the run that
walked through the branchless stop, and the substitute it improvised).


<a id="M1D_static_sweep_allowlist"></a>
## M1D_static_sweep_allowlist — SB-Emulators ships the static sweep's allowlist annotation, in a zero-dependency artefact, with a mandatory reason from a closed set

The [inverted lint](#M1D_static_taxonomy)
needs an allowlist marker, and a marker needs a type. `com.vaadin.swingbridge.migration.IntentionallyStatic` ships in a new
module, `migration-annotations`, and the annotation's **javadoc is the canonical definition** of the gate's
rule — which fields pass untouched, which are flagged, what each reason claims. Migrator-facing:
[`static-fields.md` § The allowlist annotation](../../guides/1-swing-to-emulators/static-fields.md#the-allowlist-annotation).

**Why SB-Emulators ships it rather than each app declaring its own one-liner.** The class definition is worth
nothing; three other things are.

- **The javadoc is the one doc that cannot go unfetched.** `Q_taxonomy_doc_home` chose two migrator pages
  over four on exactly this criterion — *an unfetched page is a silent routing failure*. A javadoc is read
  through the tooling the migrator already has open at the moment of the decision, and with
  [M1D_lsp_prerequisite](#M1D_lsp_prerequisite)
  making an LSP mandatory, the agent reads it through the same channel it uses for everything else. It also
  closes a hallucination surface: *what is the allowlist annotation called* is precisely the question a 30B
  model answers differently on each run.
- **It is the precondition for anything downstream being shared.** The ArchUnit rule references the
  annotation *by type*. Per-project annotations mean the rule can never be shared either, so both get
  copy-pasted and both drift.
- **It is the fix for a drift that had already happened.** The rule *"effectively-immutable types pass"* was
  being stated in three places at once — Step 1's filter-but-count, Step 3's write-classification table, and
  the unwritten lint tier list. Those are now references to the javadoc.

**Two-dimensional, because the type-only rule was wrong.** The design draft had the discriminator as
*mutability of the type, with `final` as corroboration rather than the test*. `public static boolean
isLoggedIn` refutes it — `boolean` is maximally immutable, and the field is the sharpest cross-user bug the
sweep hunts. `final` governs whether the **reference** can change; the type governs whether the
**referent** can; a field needs both. So: any non-`final` static is flagged whatever its type, a `final`
field of a mutable type is flagged, and only `final` + immutable type passes. The log4r-era
`static Logger logger = …` idiom (4 of 14 non-final statics in `testapps/inventory`) is flagged and the
right fix is to add `final`, not to annotate — which turns what looked like the rule's noise floor into a
one-keystroke correct edit.

**A logging facade counts as an immutable type, because otherwise the prescribed fix fails the prescribed
rule.** As first shipped, "add `final`" produced a `final` field of a type absent from the
passes-untouched list — i.e. a `final` field of a mutable type, which the same javadoc flags. The two
halves contradicted each other on the single commonest static in a Swing app, and the contradiction was
found by an agent following the docs (the 2026-08-27 `testapps/inventory` stage-2 probe, stumble 13),
which is the strongest evidence available that a migrator hits it too. So `org.slf4j.Logger`,
`org.apache.log4j.Logger` and `java.util.logging.Logger` join the immutable-type set, and the "add
`final`" advice now lands on the passing side. **The non-`final` half does not relax**: a logger left
without `final` is still flagged, because no gate can distinguish it from `public static boolean
isLoggedIn`, and the fix is a keystroke.

Two things worth stating plainly. **The facades are the one entry on that list that is not provably
immutable** — the claim is narrower than for `String`: nobody reassigns a logger, and no logging call
mutates state a migration cares about. And **the measurement disagrees with itself across runs and the
conclusion survives either way**: 4 of 14 non-`final` statics in the count this entry was drafted on,
3 of 5 in the probe's own count of the same app's migrated tree. A quarter or three-fifths, a gate that
flags them spends most of its noise budget on the one shape whose fix is mechanical, and migrators learn
to ignore it. (Which of those funnels is right is a separate open question about the worked numbers, not
about this rule.)

The set now lives in code in exactly one shared place — `MigrationGuardrails`' `IMMUTABLE_TYPES` in
`migration-guardrails` (M1D_guardrails_artifact), with `flagsTheLooseLoggerIdiomAndAcceptsTheFixedOne`
pinning both halves, so removing the facades reddens the build rather than silently re-opening the
contradiction.

This also makes dropping `Q_lint_extension`'s transitive tier clearly right rather than merely affordable:
`static final AppContext CTX` is flagged for `AppContext` being a mutable type, so the expensive
reachability walk buys nothing. (It survives only as Step 3's demotion guard, where it changes a verdict.)

**A mandatory reason from a closed set of four**, one per terminal verdict of the tree —
`IMMUTABLE_CONSTANT`, `WORLD_GLOBAL_READ_MOSTLY`, `JVM_INFRASTRUCTURE`, `COUNTER`. This is the answer to the
one real objection to shipping a marker at all: an allowlist is cheaper than routing, so a bare marker gets
rubber-stamped. A closed enum refuses "because I said so", is greppable and countable per reason (a signal a
single total hides), and gives a field with no matching reason nowhere to hide — it does not belong at
application scope. Free-text was rejected: it makes the allowlist unreviewable, which is the property the
annotation exists to create. `note()` stays optional, for evidence.

**`RetentionPolicy.CLASS`, and it is load-bearing.** ArchUnit reads bytecode, so `SOURCE` would make the
entire allowlist invisible — the gate would flag every annotated field, a gate that looks installed and
is not, which is this project's signature failure shape. `RUNTIME` implies reflection nobody needs.
Verified by mutation rather than by reasoning: flipping the annotation to `SOURCE` and rebuilding fails
the guardrails' own `acceptsAllowlistedFields` (and, in `testapps/crud`, both genuinely-allowlisted
fields). Worth recording
that the *first* attempt at that mutation check passed — the annotation is baked into the **consumer's**
bytecode at the consumer's compile time, so rebuilding only `migration-annotations` proved nothing. The
stale-artifact trap CLAUDE.md warns about, met while testing a claim about staleness.

**Zero dependencies, and that is the module's contract rather than its tidiness.** A migrated app's
**UI-free service modules annotate their allowlisted statics too** — M1D_static_taxonomy's Step 0 is explicit that
`Q_user_specific` runs everywhere — and Step 0 classifies a module UI-bearing if it *depends on* one. Put
the annotation in `:emulators` and a service module that adds the jar to annotate one field trips Step 0's
own dependency closure, inverting the classification the sweep rests on. So the module must stay resolvable
by a module that has never heard of Vaadin: no Vaadin types, no emulator types, no third-party jars. It is a
reasonable home for future annotations and dependency-free helpers a migrated app carries in its own
source, under that one invariant.

**`testapps/crud` is the worked reference, by accident and usefully.** The gate flags exactly two of crud's
three statics — `static final String[] COLUMNS` (a `final` field of a mutable type; arrays are mutable) and
`static final AtomicInteger ERROR_ID` — and both are legitimately allowlistable, as `IMMUTABLE_CONSTANT` and
`COUNTER`. So the app demonstrates two of the four reasons on real code, while the fixtures for each tier
— including the one proving the retention works — live with the gate in `migration-guardrails`.

**Rejected alternatives.**
- **Each app declares its own marker** — rejected: the annotation is the cheap part; a shared *type* is what
  lets the rule, the tier list and the javadoc be shared, and copy-paste is how the three-way drift above
  happened.
- **Put it in `:emulators`** — rejected on the Step 0 dependency-closure inversion above. The alternative
  fix, a documented carve-out in Step 0's closure, is worse: an exception to a mechanical rule is how
  mechanical rules stop being mechanical.
- **Ship the ArchUnit rule as a library class too** — rejected at the time: it would mean an artefact
  depending on ArchUnit (test-scope), and it was not where the drift was. The tier list becomes canonical
  the moment it lives in one resolvable javadoc; the rule was to stay a copy-paste snippet that
  *references* the javadoc, with the stated revisit trigger *"if the snippet itself starts drifting
  between projects."* **The trigger fired 2026-09-02** — the two copies in the reactor disagree, neither
  matches this javadoc, and the printed snippet does not compile — and the rejection is overturned by
  M1D_guardrails_artifact: a test-scope `migration-guardrails` module, the artefact-depending-on-ArchUnit
  cost accepted because the alternative was a gate that looked installed and was not.
- **A bare marker with no reason** — rejected on rubber-stamping (above).
- **Free-text justification instead of an enum** — rejected: unreviewable in bulk, and it makes the
  annotation a comment with syntax.
- **`RetentionPolicy.RUNTIME`** — rejected as implying a reflective use nobody has; `CLASS` is exactly what
  a bytecode-reading lint needs.
- **Naming it `@AppScoped`** — rejected twice over: it collides with M1D_former_singletons's own naming trap, where
  *application* scope is the taxonomy's widest row, and `@ApplicationScoped` collides with Jakarta/CDI.
  `@IntentionallyStatic` follows `FormerSingletons`' precedent of a name that reads as a claim needing
  evidence rather than a decoration.

**Status.** Shipped 2026-08-26; logging facades added to the immutable-type set 2026-09-02.
`migration-annotations` builds in the reactor. The gate and its fixtures moved out of
`testapps/crud/1-emulators` into the `migration-guardrails` module the same day
(M1D_guardrails_artifact), where they are `MigrationGuardrails` + `MigrationGuardrailsTest` (16 tests),
mutation-checked against `SOURCE` retention. The component check is kept alongside the static-state one
for its sharper message, though the latter subsumes it.

**Source:** M1D_static_taxonomy (the inverted lint this completes, and Step 0's closure that constrains the module),
M1D_former_singletons (`FormerSingletons`, the naming precedent, and the completeness diff this feeds), M1D_lsp_prerequisite (the LSP that
makes a javadoc the reliable delivery channel).

<a id="M1D_import_swap_tool"></a>
## M1D_import_swap_tool — Phase 2 is a tool, not a rule; its swap table is derived in core and declared by add-ons

Every import-rewrite instruction Phase 2 carried was written for *explicit single-type* imports, and
real apps do not import that way — `testapps/inventory/swing` has `import javax.swing.*;` in 32 of 65
files and `import java.awt.*;` in 25, 23 files carrying both. A wildcard cannot be "reverted per
carve-out": `import java.awt.*` holds ported types (`Component`, `BorderLayout`, `Frame`) *and*
stay-JDK ones (`Color`, `Font`, `Insets`) on one line, so no edit to that line is right for both sets
and the guide's central mechanic had no defined behaviour on ~40% of a typical app's files. The answer
is not a better rule: **the phase stops being a rule.** `:migration-tool` is a zero-dependency
runnable jar that an agent following the guide runs over the tree.

**The guide keeps its per-type prose, reframed.** Phase 2's ~60 lines of what-ports-what-stays were
the source of truth and are now the *derived explanation*: a `Reference: what ports, what stays`
section serving two jobs the table cannot — reviewing the tool's diff (knowing *why* `GridBagLayout`
moves while `GridBagConstraints` stays makes the diff readable) and the by-hand path for a migrator
who does not run the tool. Deleting them was the alternative; printing the derived table verbatim into
the guide was the other, rejected because it is stale the moment an emulator lands, which is the drift
the generated tsv exists to prevent.

**Token level is the whole design, not a first cut.** The resolver needs only "scan tokens" plus
`Class.forName`; no AST, no LSP, no classpath, no compiling file, no app build. Measured over all
three testapps' graded stage-2 trees, not one residue case was a judgement call *about an import* —
the differences were all "a later phase owns this" or "the add-on table has no rows". So there is
deliberately **no v2 promised**; the one gap regex cannot close is a simple name shadowed by a
declaration the tree pre-scan cannot see (a local variable, or a member type inherited from a
superclass — JLS 6.4.1), which is reported rather than guessed.

**Two independent passes, and the fully-qualified one carries a real app's bulk.**

- **Pass 1 — in-code fully-qualified references.** A table-driven prefix swap:
  `new javax.swing.JTextField()` → `new vaadinx.swing.JTextField()`. This is the *easier* pass, not a
  stretch goal — no wildcard to resolve, no collision analysis, and a fully-qualified name involves no
  import so JLS 7.5.1 cannot bite. It is also the pass that matters most on real source: every
  NetBeans/Matisse form app writes widgets fully-qualified with no Swing import at all
  (`jlawyer-shape`'s `InvoicePositionEntryPanel` has 87 such references and 16 imports, none Swing), so
  an import-only tool is a **silent no-op** on that whole file class.
- **Pass 2 — imports.** Resolve each referenced simple name the way javac resolved it when the file
  last compiled, then emit exactly the imports the file needs.

The passes cannot invalidate each other — a qualified reference needs no import before or after the
swap — so each is testable alone.

**Delete the on-demand import; do not keep it and out-rank it.** A single-type import beats an
on-demand one (JLS 7.5.1), so keeping `import java.awt.*;` and adding `import vaadinx.awt.Frame;`
would also be meaning-preserving, with a smaller diff and no need to re-emit stay-JDK types. It was
rejected on **loudness**: keeping the wildcard turns every reference the token scan *misses* into a
silent bind to the JDK type, where deleting it turns the same miss into `cannot find symbol`. That is
the same doctrine as D_showdialog_throws and R_match_swing_errors' major-gap trigger — a mechanical
fix at a named site beats a silently-wrong default — and it is what makes a regex-level tool
defensible at all. Deletion is confined to packages the tool can enumerate, which is derived
(`Class.forName` on a row in that package), not listed: an add-on's upstream is absent by design, so
its wildcard is kept and its ported types redirected by out-ranking single-type imports instead.

**Emission is where the one silent failure lives, and it needs a whole-tree pre-scan.** "Bias to
keep" is sound for *cleanup* — an unused import compiles — and false for *emission*: an import the
file did not ask for either duplicate-collides (loud) or **shadows a same-package sibling** (silent).
An app with its own `com.acme.ui.Panel` plus `import java.awt.*` means its own class; emitting
`import vaadinx.awt.Panel;` would redirect every use with no diagnostic. Hence `SiblingIndex`, a regex
pre-scan of the tree. Measured: 117 app-declared types across the three testapps collide with none of
the 155 core rows — so the corpus cannot validate the mitigation, which is precisely why it is
implemented rather than deferred until evidence appears.

**The core table is a golden file; add-on tables are declared.** `META-INF/emul/ported-types.tsv` in
`:emulators` is checked in, and `PortedTypesTableTest` regenerates it by reflection (the
`R12ProvenanceTest` prefix-map + `Class.forName` recipe) and fails on any difference — so a new
emulator reddens the build until the table is regenerated. Chosen over a `generate-resources` step for
two properties that step lacks: the table stays **greppable without a build** (the migration agent
reads it directly) and a row appearing or vanishing is **visible in the diff** of the commit that
caused it.

**No exclusion list exists, and that is the strongest half of the no-drift argument.** The derivation
maps a `vaadinx` name onto its JDK package and `Class.forName`s it, so a class with no JDK namesake
drops out on its own: every SB-Emulators-invented type (`EHelper`, `AppInstance`, `RteHtmlCodec`, …) and — the
case that looked like it needed a policy — all six of `vaadinx.util.prefs`, which is SB-Emulators'
`PreferencesFactory` *implementation* and not a swap target at all (a migrated app's prefs code keeps
its `java.util.prefs` imports untouched). Measured: **155 rows derived, 59 classes excluded**, none of
it hand-maintained. Not-a-swap-target is therefore a property of the derivation rather than a list that
can rot. Add-on rows cannot be derived the same way — an add-on *replaces* its upstream, so
`Class.forName("com.jgoodies.forms.layout.FormLayout")` fails and there is nothing to reflect against
— so each add-on ships its own tsv and the tool unions every copy on the classpath. Core still does
not know add-ons exist, which extends M1D_addon_packaging's one-way direction to the table. Each
add-on's own `PortedTypesTableTest` is the drift guard that replaces derivation: every public type it
ships must be a row or on an explicit SB-Emulators-invented list, no row may dangle, and **the tsv must be on
the classpath** — that last check exists because naming `<resources>` in a pom replaces Maven's
default `src/main/resources` entry, which silently kept both add-on tables out of their jars on the
first attempt.

**Three ways to assemble that classpath, and the first is now the recommended one.** The tool ships
its own `bin/` + `lib/` distribution zip ([D_kit_tool_zip](../../emulators/decisions.md#D_kit_tool_zip)),
where `lib/*` *is* the classpath and adding a table means dropping a jar in — the distribution kit
hands that over with the three table jars already there; a migrator whose own build already declares
`:emulators` and their add-ons can get the same list from `mvn dependency:build-classpath`; and a
`dependency:copy` into any folder plus `java -cp "<folder>/*"` still works for anyone who wants it.
**The invariant across all three is that the classpath is never empty by accident**, which is why
`java -jar` is wrong in every one of them: `-jar` ignores `-cp`, so the union comes back empty.
`PortedTypes.load` makes that case loud rather than silent — an empty union throws, naming the
missing resource and the fix, and `ImportSwap` exits 2 — and the report prints its table's row count
and its source jars so a *partial* classpath (core rows, no add-on rows) is legible too.

A visible bonus: the tables make add-on **sparsity** legible. `jgoodies-forms` ships no `builder`
package, so upstream's heavily used `PanelBuilder` / `ButtonBarBuilder` / `DefaultFormBuilder` have no
rows, and `jcalendar` ships one type out of upstream's ~10. The tool reports
"no mapping for `com.jgoodies.forms.builder.PanelBuilder`" — derived by cutting non-loadable rows to a
two-segment add-on root, so an unrelated dependency the app keeps (`com.formdev.flatlaf.*`) and a JDK
stay-type (`javax.swing.WindowConstants`) both stay silent.

**The LLM drives; the tool assists.** Scope is what is decidable from the table plus a file's own
imports. Out on principle rather than effort: `System.exit` routing, the static sweep,
`JFileChooser.showDialog` direction, `FormerSingletons`. The report is the deliverable, in Markdown for
LLM *and* human consumption (a second machine-readable format would have no reader), and it names what
was **declined and why**, since a tool that silently succeeds gives its driver nothing to verify.
**Exit status is 0 whenever the tool ran, declined files included** — a decline is expected output to
triage, and a non-zero status there misreports it as a broken tool and invites wrapping it in a CI gate
it is not.

**Rejected alternatives.**
- **A canonical import block the migrator pastes verbatim** — kept only as the documented no-tool
  fallback. It needs a `List`/`Timer` carve-out (two single-type imports of one simple name is a hard
  compile error, JLS 7.5.1) and a cleanup pass, and it structurally cannot rewrite in-code qualified
  references. Taking the cleanup in-house collapsed it into the per-file variant anyway, since "which
  simple names does this file reference" is the same computation.
- **An external cleanup pass** (`google-java-format --fix-imports-only`, OpenRewrite
  `RemoveUnusedImports`) — the latter needs a type-attributed AST and therefore a compiling file, wrong
  for a paste-first order; and doing it in-house needs to be right in only one direction, since keeping
  an unused import compiles.
- **Reflecting over `:emulators` at tool runtime** instead of reading the tsv — rejected: it drags
  Vaadin onto the tool's classpath and hides the table behind a build.
- **Deriving add-on rows from a test-scoped dependency on upstream** — rejected as coupling an add-on's
  build to the artifact it exists to replace, against each module's provenance lane.

**Status.** Shipped 2026-09-01. `:migration-tool` in the reactor, 16 tests. `GradedTreeValidationTest`
compares the tool's output against all three committed stage-2 trees by *resolved binding* rather than
text (the graded run used `vaadinx.swing.*` wildcards where the tool emits single-type imports; a
textual diff scores dozens of false failures): **91 files compared, 67 exact, 24 differing, 0
disagreements** — no shared binding resolves differently. Nor was any of the 24 a judgement call
*about an import*: every residue belonged to a different phase (a later phase deleted the code, a
semantic rewrite introduced a type, the add-on table had no rows), so **Phase 2 is judgement-free**
(`Q_phase_fully_mechanical`) — the first guide phase to become so, and on a typical app most of the
diff. End-to-end, the tool's output on the pristine
stage-1 trees compiles against `:emulators`: `crud` (7 files) and `jlawyer-shape` (20) clean, `inventory`
(65) down to 4 errors, all of them documented `swingbridge-emulators-jcalendar` method gaps (`getDateEditor`,
`getDateFormatString`) already named in that add-on's MIGRATION.md and hand-resolved in the graded tree.
The tool also catches what the graded human run dropped: `crud`'s `EmployeeEditDialog` kept
`import java.util.Calendar;` through a graded migration — harmless there (constant-only use) but exactly
the silent class, and a tool does not get bored.

**Amended by M1D_hazard_scan_tool**, which put Phase 1's hazard scan in the same jar as a second
`main` — the same shape one phase earlier (walk a tree, apply a classpath-loaded table, render grouped
Markdown, never gate), and the last step of the guide's mechanical floor that was not in the jar.

**Source:** M1D_addon_packaging (the one-way direction the add-on tables extend), M1D_addon_migration_docs (the
addendum the "no mapping" line points at), M1D_custom_layoutmanager (the layout seam a swapped
`LayoutManager` lands on), M1D_lsp_prerequisite (the tooling gate whose "stop" branch the paste-block
fallback answers).

<a id="M1D_hazard_scan_tool"></a>
## M1D_hazard_scan_tool — Phase 1's hazard scan is Java in `:migration-tool`, and its patterns have one home that a test joins against spec.md §7

Phase 1's hazard finder shipped as `migration/out/1-swing-to-emulators/hazard-scan.sh`: ~100 lines of
bash, 17 `grep -rEn` calls, one per §7 hazard plus the two §5 import sharp edges, always exit 0. It is
now `com.vaadin.swingbridge.migration.tool.hazardscan.HazardScan`, a second `main` in the jar the guide's Phase 2 already
runs, reading its patterns from `META-INF/emul/hazards.tsv`. The script is deleted.

**Three independent pulls all resolved the same way, which is what made this a decision rather than a
chore.**

- **Windows.** The distribution kit ([D_distribution_zip](../../emulators/decisions.md#D_distribution_zip))
  had an open question with two bad answers: a `.cmd`/PowerShell twin — a *third* hand-synced copy of
  the patterns — or a stated WSL prerequisite, a whole subsystem install for one grep. Java dominates
  both: the migrator already has a JDK 24+ and already runs the Phase 2 tool the same way. The kit's
  prerequisites line loses "bash or WSL" entirely.
- **Drift.** §7's `Detect:` lines and the script's regexes were two hand-maintained copies with **no
  check between them**, and the script header apologised for it in prose ("kept in sync by hand — a
  ~15-line label-consistency test is the noted-but-unbuilt drift backstop"). They had already
  diverged, benignly (the script carried the richer `System\.exit|Runtime\.getRuntime\(\)\.(exit|halt)|…`
  where the spec said `System\.exit`) and not so benignly: the §7 *Date/time zone drift* bullet carried
  **no `Detect:` at all** while the script ran three greps for it, so `out/` shipped detect patterns
  its own source lacked. One table plus one test closes both directions.
- **Add-on rows.** `PortedTypes.load` already unions every `META-INF/emul/ported-types.tsv` on the
  classpath so an add-on contributes swap rows without core knowing it exists
  (M1D_addon_packaging). A hazard table in the same shape gets that property for free: an add-on's
  `MIGRATION.md` watch-out that is grep-shaped can become a row in *its* jar, surfaced by the one scan
  the agent already runs, instead of prose the agent must remember to apply.

**The join is by slug, not by prose, and that is the whole drift argument.** Every hazard carries an
`H_` id in exactly two places: an `<a id="H_…"></a>` on its spec.md bullet, and the tsv's first column.
`HazardTableTest` then checks both directions exactly — every row names a bullet that exists, and every
bullet whose `Detect:` clause says *grep* has a scanned row (while one whose `Detect:` says "only
surfaces at runtime" must have a `manual` row instead, so it cannot fall out of the report). Word-matching
labels against bold headings was the first draft and was rejected as too fuzzy to trust: it passes or
fails on which words survived a rewording. `DecisionIdTest` already proved the slug shape works here, and
buys the same trade — exactness for typo-ability, with a test as the other half.

**One table holds the whole scan, manual rows included.** A hazard with no grep signal
(`H_modal_from_listener`, `H_multi_tab`) is a three-column row that renders as a closing
checklist. In bash the manual checks lived in a trailing heredoc, which is exactly the shape that gets tidied
away by someone refactoring the code. The same reasoning keeps **clean sections printed rather than
omitted**: the report doubles as the phase's worklist, and a hazard that vanishes when it finds nothing
leaves its reader unable to tell "checked, clear" from "never looked".

**Retired patterns survive as `#` comments above where their row was.** Why there is no bare
`getInstance()` pattern (it matches `Calendar.getInstance()`; the subclass-typed holder needs the
ArchUnit gate, which resolves the type hierarchy); why `HTMLDocument` *reads* are no longer scanned
(D_htmldocument made them resolve); why `Toolkit` / `GraphicsEnvironment` are gone
([D_graphics_environment](../../emulators/decisions.md#D_graphics_environment) — a swap-table row
retires the hazard, and scanning for one that cannot occur trains the migrator to ignore the scan).
Those comments are the only record of a *deliberate absence*, and are exactly what the next maintainer
needs before "adding one back".

**Evidence the port changed no match.** A one-off parity harness ran `hazard-scan.sh` and `HazardScan`
over all three `testapps/*/swing` trees and diffed the `file:line` hit sets per section:
**51 section comparisons, 51 identical, 0 differences.** That is the only thing that could establish
the POSIX-ERE → `java.util.regex` port faithful (`[[:space:]]` → `\s` was the one real edit; `\b`,
alternation, `+` and escaped parens carry over), and it was deliberately *not* kept as a test — it
depends on the artefact being deleted. `HazardScanGoldenTest` holds the line afterwards, pinning
hit **counts per section** over `testapps/inventory/swing`: counts rather than lines, so an unrelated
testapp edit does not redden it, and per section rather than per file, so what reddens is a pattern
that quietly widened or narrowed. It also turns D_showdialog_throws's hand verification
("`\.showDialog\s*\(`, verified against the inventory testapp's two call sites and nothing else") into a
standing assertion — the golden says 2.

**Matching is per line, as `grep -n` is.** Not a detail: the report's line numbers depend on it, and so
do `^` / `$` / `\b` — a whole-file match would let `\.showDialog\s*\(` span a line break, since Java's
`\s` matches a newline. Pinned by its own test.

**The one detection gap the port closed rather than ported.** `H_html_pane_links`' evidence is in the
app's *HTML resources* (`href=` values outside Flow's `http`/`https`/`mailto`/`ftp` allowlist, which
arrive with the attribute stripped and stop being links), and bash left that as a manual step because
file-type dispatch cost more than the grep. In Java it is one more walk, so the tsv grew an optional
fifth column naming the file glob, and the hazard now has two rows: the Java panes and the resources.
This is the only ⚠silent §7 hazard a scan *could* reach and did not.

**Rejected alternatives.**
- **A `.cmd` / PowerShell twin, or a WSL prerequisite** — both are Pull 1's two bad answers above.
- **Keep the script and add a consistency test over its source** — parseable, and it fixes drift
  without fixing Windows or add-on rows. It also leaves the patterns in a file no test can *run* them
  from, so a `PatternSyntaxException` shape stays undetectable.
- **A subcommand on `ImportSwap`, or a dispatcher `Main`** (`Q_entry_point`) — rejected for the second
  `main`, which is the smallest change and already reads correctly in the invocation everything ships
  (`java -cp … com.vaadin.swingbridge.migration.tool.hazardscan.HazardScan`, never `-jar`, which ignores `-cp` and would
  leave the tables invisible). The manifest keeps `ImportSwap` as `Main-Class`; Phase 2 is the phase
  that earns it. (A dispatcher was re-proposed and re-rejected when the distribution zip landed: two
  hand-written five-line launchers per platform are less code than a dispatcher and read better at
  the call site — [D_kit_tool_zip](../../emulators/decisions.md#D_kit_tool_zip).)
- **Compiling the patterns into the class** — that is the shell script's failure re-lit in Java: no
  `--list`, nothing for a test to join against spec.md, no add-on contribution, and a pattern change
  needs an SB-Emulators release.
- **A plain grouped-text renderer beside the Markdown one** (`Q_report_format`) — Markdown only.
  It reads fine in a terminal, and two renderers is surface with no second consumer, the same argument
  M1D_import_swap_tool made for the swap report.
- **An `*LLM:*` overlay column** (`Q_llm_overlay_column`) — not v1, and held as loosely as the overlay
  itself ([local-llm-migration-target.md](../../ideas/local-llm-migration-target.md)). The report already
  names each section's `H_` id, which is the hook such a column would hang on; if it lands, the
  consistency test checks it too, because a second copy of the overlay is the thing this entry exists
  to prevent.
- **Authoring an add-on hazard row now** (`Q_addon_rows`) — the classpath union is implemented, per
  R_infra_not_surface's "build the mechanism whole when you touch it"; no row is authored until an
  add-on's `MIGRATION.md` has a grep-shaped watch-out that earns one. Mechanism, not need.

**One bug fixed on the way, and it belonged to `ImportSwap` regardless.** Its file walk skipped build
output with `p.toString().contains("/target/")`, which never matches a backslash path — so a migrator
on Windows had their own `target/` rewritten. Both tools now share `Sources.under(root, glob)`, which
compares path *elements*.

**Status.** Shipped 2026-09-02. `HazardScan` + `Hazards` + `HazardReport` in
`com.vaadin.swingbridge.migration.tool.hazardscan`, 21 rows (18 scanned, 3 manual), 11 tests in `:migration-tool` (27
total). `hazard-scan.sh` deleted; spec.md §7 and §5 carry the 19 `H_` anchors, and §5's two sharp edges
became bullets to have somewhere to put them.

**Package layout, once there were two tools.** `com.vaadin.swingbridge.migration.tool.importswap` and
`com.vaadin.swingbridge.migration.tool.hazardscan`, with the root `com.vaadin.swingbridge.migration.tool` holding **only what both use** —
today just `Sources`. The rule is *shared by two tools*, not *looks reusable*: `Lexed`, a Java-source
lexer, stays in `importswap` because only the swap calls it, and moving it up on the argument that a
scan might one day want to skip comments would be the utilities-drawer failure this split exists to
avoid. `Sources` is the module's only `public` non-`main` class, and only because a subpackage must
reach it.

**Source:** M1D_import_swap_tool (the tool stance this amends, and the jar this shares),
M1D_addon_packaging (the classpath-union property the hazard table inherits),
[D_graphics_environment](../../emulators/decisions.md#D_graphics_environment) (what retires a hazard
from the scan), [D_showdialog_throws](../../emulators/decisions.md#D_showdialog_throws) (the hand
verification the golden made standing).

<a id="M1D_bootstrap_canonical"></a>
## M1D_bootstrap_canonical — `SwingBridgeEmulatorsBootstrap` is the one init listener a migrated app wires, and `MainWindowRoute` throws when it never ran

A migrated app registers `vaadinx.swing.app.SwingBridgeEmulatorsBootstrap` — one line in its own
`META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener`, or a Spring `@Bean` — and
writes **no `VaadinServiceInitListener` of its own for SB-Emulators' needs**. The guide's hand-rolled
`AppServiceInitListener` is deleted, not demoted. `MainWindowRoute.onAttach` throws
`IllegalStateException` when SwingBridgeEmulatorsBootstrap never ran for the attaching UI, naming the SPI file.

**The source tree already said this; only the migrator-facing `out/` tree lagged.** spec.md step 4
registers SwingBridgeEmulatorsBootstrap and calls it critical-path, R_no_spi_selfregister calls it "the single
mandatory init listener", and Sampler plus all three testapps carry exactly that one line.
`out/guide.md` and `out/checklist.md` were the drift, and drift with teeth:

- **The guide's listener was a stale copy of an older SwingBridgeEmulatorsBootstrap.** SwingBridgeEmulatorsBootstrap had four legs then —
  `BrowserTimeZone.fetch()` (SD_browser_timezone), `FocusTracker.install()` (SD_focus_tracker),
  the `emul/emulator-theme.css` stylesheet
  ([D_theme_is_lookandfeel](../../emulators/decisions.md#D_theme_is_lookandfeel)), and
  `TabScope.setup` + destroy listener → `AppTab.onTabClosed`
  ([D_shutdown_lifecycle](../../emulators/decisions.md#D_shutdown_lifecycle)). The guide's copy had
  the first. Measured 2026-09-02: `grep -n "emulator-theme\|FocusTracker\|TabScope" guide.md` → zero
  hits. The theme leg arrived *after* the copy was written, which is the whole argument — a copy
  misses every future leg the same way, silently.
- **The guide contradicted itself.** Its `FormerSingletons` recipe (M1D_former_singletons) stores the
  holder in the **tab scope**, which exists only because SwingBridgeEmulatorsBootstrap calls `TabScope.setup`. An app
  following Phase 4 as printed could not run Phase 1's holder; `AppInstance.get()` throws with a
  message naming SwingBridgeEmulatorsBootstrap, which is how the migrator eventually learned the truth.
- **Registering both is harmless but pointless.** `BrowserTimeZone.fetch()` returns early once the
  session holds a zone, so the worst case was a second `ExtendedClientDetails` round-trip per UI. The
  docs therefore need no double-registration warning — they need to stop offering the second listener.

**No narrow variant.** There is no "listener for an app that wants only the zone", because no such app
exists: every one of its legs is needed by any app that renders a `MainWindowRoute`. If
SwingBridgeEmulatorsBootstrap ever does not fit a case, that reopens this decision; patching or copying its body is the
trap, not the fix.

**The marker is per-UI `ComponentUtil` data set by the UI-init leg, not `TabScope` presence.** The tab
scope is created inside an `ExtendedClientDetails` callback and, without `@PreserveOnRefresh`
(recommended, not mandatory), may not exist when the route attaches — a false positive. UI init
listeners run synchronously at UI creation, before any route attaches, so the data marker is
deterministic. The stored value is the listener instance itself, because `ComponentUtil`'s typed slot
takes the key class's own type; only its presence is read, via `SwingBridgeEmulatorsBootstrap.isWired(UI)`. The check
is `onAttach`'s first statement, ahead of `super.onAttach` and any `AppTab` mutation, so the F5 rebind
path is covered too — a wired app never trips it there, since the fresh UI's init listener re-sets the
marker. `AppInstance.get()`'s and `BrowserTimeZone.get()`'s existing throws stay as defence in depth
for code that runs before any route, or outside a `MainWindowRoute` app.

**Spring shape is `@Bean`, not `@Component`.** SwingBridgeEmulatorsBootstrap is `final` and library-owned, so the app
cannot annotate it; guide.md said `@Component` and is corrected. An app's *own* extra listener, for non-SB-Emulators reasons, can of course be a `@Component`.

**Rejected: docs-only.** Fixing `out/` and leaving the runtime silent was the cheap option, and it
loses the case it exists for — an app that skips the line fails three separate ways, late and far from
the cause (a date input throwing on construction, focus quietly wrong, no shutdown on tab close). One
throw at the route, at first load, naming the file to create, is the same trade as the
`Date`↔`LocalDate` case in R_match_swing_errors: a silently-wrong default versus a mechanical one-line
fix at a known site. It is that rule's fifth enumerated throw case.

**Status.** Shipped 2026-09-02. `SwingBridgeEmulatorsBootstrap.isWired` + the marker, the `MainWindowRoute` check,
`MainWindowRouteWiringTest` (throws unwired / bootstraps wired). `AppTabTest`'s hand-built second UI
sets the marker directly rather than firing every init listener — the tab-scope handshake would cost
that fixture its bare-UI property.

**Source:** R_no_spi_selfregister (the app-wires-it rule this enforces), R_match_swing_errors (the
enumerated throw set this joins), M1D_former_singletons (the tab-scoped holder the guide's copy could
not have supported), [D_shutdown_lifecycle](../../emulators/decisions.md#D_shutdown_lifecycle),
[D_theme_is_lookandfeel](../../emulators/decisions.md#D_theme_is_lookandfeel), SD_browser_timezone,
SD_focus_tracker (the four legs).

<a id="M1D_guardrails_artifact"></a>
## M1D_guardrails_artifact — the migration guardrails ship as a test-scope artifact with a fluent checker, and every relaxation names its target

The three build-time guardrails of Phase 6 step 6 — every `static` field provably immutable or
`@IntentionallyStatic`, no component in a `static` field, no JVM exit — ship as one class,
`com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails`, in a new module `migration-guardrails` the migrator
adds in **test scope**. Phase 6 step 6 becomes one `@Test`:

```java
new MigrationGuardrails("com.myapp").allowSystemExitInMainMethods().run();
```

ArchUnit is the engine and a private implementation detail: the migrator's source imports one SB-Emulators type
and no `com.tngtech.*`, so SB-Emulators' ArchUnit version stops being a compat surface on their build.

**The class becomes a facade over `:migration-tool`** per
[`Q_one_rule_engine`](#M1D_static_sweep_tool): the rule engine and ArchUnit move there, so `StaticSweep`
and this gate answer one question with one implementation instead of two readers of one rule. Everything
this entry guarantees is unchanged by that — same class, same fluent surface, same test scope, same
`com.tngtech.*`-free import list — which is why the facade is kept rather than the module folded away.
What the move *does* change is which artifact is permanent: this one's **throw** is what makes the sweep
stay done, where the tool's `main` prints and exits 0 because a non-empty worklist mid-migration is the
expected state.

**Why a library class now, when M1D_static_sweep_allowlist rejected exactly this.** That entry rejected
it on two grounds — an artefact depending on ArchUnit, and *"it is not where the drift was"* — and
closed with *"revisit if the snippet itself starts drifting between projects."* It has, and in every
way a snippet can:

- **The printed snippet does not compile.** `guide.md`'s block calls `beProvablyImmutableOrAllowlisted()`
  and never defines it, leaves `fields()` unimported, carries the immutable-type set as prose, and lists
  only the ArchUnit coordinate though the rule references `@IntentionallyStatic` by type. The gate the
  same bullet calls *"the one that has to pass before you call the sweep done"* is the one part of the
  block a migrator cannot paste and run — a gate that looks installed and is not, the failure shape the
  allowlist entry itself used to justify `RetentionPolicy.CLASS`.
- **The two copies in the reactor disagree, and neither matches the canonical definition.** Measured
  2026-09-02: `testapps/crud` recognises records but not `java.time`; `testapps/inventory` recognises
  `java.time` but not records; the annotation's javadoc, which M1D_static_sweep_allowlist made canonical,
  lists both. inventory adds `java.lang.Class`, crud lists `vaadinx.swing.KeyStroke` — a type that does
  not exist anywhere in SB-Emulators. inventory's copy is derived output, which makes it *better* evidence, not
  worse: it is what the documented procedure produces in the hands of an agent following it. The agent
  re-derived the set from prose, got a different set, and had to invent two details unaided (skip
  compiler-synthetic fields; `allowEmptyShould(true)`) that the docs never state.
- **The reference implementation lives in a directory contractually wiped.** `IMMUTABLE_TYPES`, its nine
  self-tests and the `SOURCE`-retention mutation check all sit in `testapps/crud/1-emulators/src`, which
  `/guide-migrateapp crud` deletes and `testapps/CLAUDE.md` marks as derived — *"a hand-edit inside
  `1-emulators/src` survives exactly until the next run."* Nothing has been lost only because crud has
  not been re-run since they landed. A module is the only fix for that, independent of the drift.

So ground (b) is false and ground (a) survives only as a test-scope dependency in a **new** module — not
in `:emulators`, and not in `:migration-annotations`, whose zero-dependency contract is untouched (the
dependency points guardrails → annotations, never back). A UI-free service module that adds the
guardrails in test scope pulls ArchUnit and the annotations and nothing Vaadin, so M1D_static_taxonomy's
Step 0 classification is safe.

**Design, each point a *because*.**

- **Root packages are a required varargs constructor argument, never defaulted.** Absent a package the
  importer scans the whole classpath and reports mutable statics inside Vaadin and the JDK; a gate too
  noisy to keep gets deleted. Varargs because inventory needed three roots (`com.ca`, `com.gt`,
  `com.vaadin.swingbridge.fixture`) — *"leaving any of them out would make the rules pass by not looking."*
- **An import that resolves no classes fails loudly; only then are empty `should()`s allowed.** ArchUnit
  fails an empty `that()` set by default, so the two `no…()` rules and the `fields()` rule all need
  `allowEmptyShould(true)` (a fully swept app may have zero statics). But blanket `allowEmptyShould`
  hides the one empty case that matters — a mistyped root package, which then passes vacuously forever.
  The checker imports once, throws when the roots resolved nothing (naming them), and relaxes the
  empty-should on all three rules after that.
- **Every relaxation is addressed to a named target; there are no global booleans.** A blanket
  `acceptSystemExits()` is strictly worse than what the guide already prescribed (*"narrow the rule's
  package or exclude that one class"*): flipped for the one `Main.main()`, it lets a `System.exit` in a
  UI action handler through forever. Hence `allowSystemExitInMainMethods()` and
  `allowSystemExitIn(String... classes)`. **The generalizable half:** a relaxation surface names its
  target for the same reason `@IntentionallyStatic`'s reason is a closed enum rather than free text — a
  global off-switch is cheaper than annotating a field, which re-opens the rubber-stamping objection the
  allowlist entry settled. So **the static gate has no off-switch at all**; `@IntentionallyStatic` is
  already its per-field hatch.
- **The JVM-exit gate is a blanket ban on the direct call, not a transitive-reachability analysis.**
  `noClasses().should().callMethod(System.exit / Runtime.exit / Runtime.halt)`. The earlier sketch was
  an annotation processor failing the build when `System.exit` was *reachable* from a `@MainWindow`
  class's call graph, which would have needed a third-party analysis engine (Error Prone was the
  candidate) and inherited its precision limits. Unnecessary, because **on a server there is no
  legitimate JVM exit anywhere** — not just on paths reachable from the UI. That makes a direct-call
  ban simultaneously *complete* for the hazard and free of the call-graph machinery, and it is why
  the check needs nothing beyond the engine the other two rules already brought. (Reachability
  survives elsewhere for a different job: M1D_static_taxonomy's Step 3 demotion guard, where it
  changes a verdict.)
- **"In main methods" means the body of `public static void main(String[])` and nothing else.** A lambda
  inside `main` is *not* main — deliberately, because `invokeLater(() -> … System.exit(0))` in `main` is
  precisely the UI-reachable exit the gate exists to catch. `allowSystemExitIn` matches the exact class
  name; `Main$1` is not `Main`. **Implementation note found while building it:** the lambda half does not
  fall out of "it compiles to a synthetic method" the way the draft assumed — ArchUnit attributes an
  access declared in a lambda to the *enclosing* code unit, so such a call arrives at the relaxation
  wearing `main` as its origin and passes unless `JavaAccess.isDeclaredInLambda()` is tested explicitly.
  The anonymous-class case *does* fall out of the class name, as drafted.
- **`run()` aggregates and never short-circuits.** Three separate `@Test`s gave three verdicts per run;
  a `run()` that threw on the first failing rule would serialise the worklist (fix statics, re-run, *now*
  discover `System.exit`). Every check runs, every violation is collected, one `AssertionError` carries
  the combined report — `AssertionError` because every test framework reads it as a failure and it is
  what ArchUnit's own `check()` throws, which is what keeps the class test-library-agnostic.
- **The individual checks stay callable** (`checkStaticState()`, `checkStaticComponents()`,
  `checkJvmExit()`) so a migrator wanting three named CI results writes three one-liners. `run()` is the
  default path.
- **Component types are matched by name, and both are checked.** `assignableTo("vaadinx.awt.Component")`
  and `assignableTo("com.vaadin.flow.component.Component")` — a `JFrame` field is only the first, an
  `SJPanel` or stock `Button` field only the second. By name because a by-class reference would make the
  module depend on `:emulators`, re-creating the dependency-closure inversion that kept the annotation out
  of `:emulators`; in a UI-free module the names resolve to nothing and the rule trivially passes, which
  is the right answer there. **This makes the module permanent, not stage-2:** at stage 4 `vaadinx` is
  gone but "no component in a static field" is still Vaadin hygiene, and `noJvmExit` was never
  Swing-specific. The javadoc says so; the name stays, because the app still went through a migration.
- **`noComponentInStaticField` has no flag.** The guide's *"keep both only while mid-sweep"* was about
  wanting the sharper message, not cost; once the sweep is clean the rule never fires. Always on.
- **The immutable-type set is written from `@IntentionallyStatic`'s javadoc, and additions go
  javadoc-first.** Primitives, enums and records recognised structurally; `java.time.*` by prefix; the
  boxes, `String`, `BigDecimal`, `Color`, `Font`, `javax.swing.KeyStroke` and the three logging facades by
  name. Two entries both hand copies carried but the javadoc did not — `java.math.BigInteger` and
  `java.lang.Class` — are admitted on the same claim as `BigDecimal` (no mutating API) and the javadoc is
  updated in the same change. A self-test pins every javadoc category, so the two cannot drift apart
  again without reddening the build. The phantom `vaadinx.swing.KeyStroke` is dropped.
  - **`vaadinx.text.SimpleDateFormat` joined the set on 2026-09-09, and its calendar siblings did not.**
    The `crud` round failed the gate on `static final SimpleDateFormat DOB_FORMAT` while three places in
    the guides — `static-fields.md`'s `G_benign` trap and its "not a hazard" note, `guide.md`'s
    "Date and time handling" §1 — told the migrator to keep that field exactly as the import swap left
    it. The docs were right: the emulator holds no formatting state on the instance (each
    `format`/`parse` runs on a per-session backing cloned from a template that freezes on first use, and
    reconfiguring a frozen one off a background thread throws rather than rewriting a shared formatter
    for every user), so a shared static is safe in the only direction this rule judges — the logging
    facades' narrower claim, one class further along. Left off, the gate contradicted the guides on the
    single date idiom every Swing app has. **`vaadinx.util.Calendar` / `GregorianCalendar` stay flagged
    on purpose**: that emulator zones its *construction* and its own javadoc disclaims the rest in bold,
    so a shared mutable calendar is still the hazard `guide.md` step 6 says to unshare. The old "not a
    hazard" note had lumped all three together and is split in the same change; a `SharedCalendar`
    fixture pins the flagged half, so the tempting "add the date emulators together" edit reddens.
    A declared `java.text.DateFormat` field is also still flagged, correctly — the swap rewrites the
    initializer, not the declaration — and the guide now says to narrow the declared type.
- **Compiler-synthetic fields are skipped** — an enum's `$VALUES` and a `$SwitchMap$` table are static,
  array-typed and unroutable. Both probe-invented details now live where the migrator cannot forget them.
- **Naming.** `migration-guardrails` follows `migration-annotations` (named for what it contains) and is
  the guide's own word for Phase 6 step 6. `MigrationGuardrails` reads as a claim needing evidence, the
  precedent `FormerSingletons` and `@IntentionallyStatic` set. Package `com.vaadin.swingbridge.migration.guardrails`, not
  `com.vaadin.swingbridge.migration`, so two jars never share a package.

**Rejected alternatives.**
- **Inline the working rule into the guide** (`Q_archunit_rule_inline`) — rejected: it fixes the
  compile failure and none of the drift; inlining verbatim from crud would have reproduced crud's own
  gaps (`java.time`, `allowEmptyShould`, the phantom type).
- **Put it in `:migration-tool`** — rejected, and worth recording so it is not retried: that module's
  pom scopes it to *"never `System.exit` routing, the static sweep, or anything else needing scope
  judgement"*; its tools are finders that exit 0 whenever they ran, while `noUnvettedStaticState` is
  definitionally a gate; it has a zero-runtime-dependency contract ArchUnit would break; and its mains
  run once and are discarded, whereas these rules must redden the migrator's CI forever.
- **A base class the migrator `extends`** — rejected: dropping an inherited `@Test` needs an override
  with an empty body, which reports as a *passing* test. The gate-that-isn't shape again.
- **ArchUnit's `@ArchTest` static-field idiom** — rejected: hard-requires `archunit-junit5`, is
  annotation-heavy, and is the shape M1D_static_sweep_allowlist's hallucination-surface argument warns
  about for a 30B model; a fluent builder is discoverable by LSP completion from the type name alone.
- **An `ArchRule`-constants API** — rejected: leaks ArchUnit onto the migrator's build as a compat
  surface. A private engine is swappable later — `java.lang.classfile` is a candidate once the module's
  compile release can be 24, which it now is (M1D_static_sweep_tool) — and which would take the
  engine to zero dependencies if the component and JVM-exit checks ever come off a class-file reader
  too.
- **An `immutable-types.tsv` travelling in `:migration-annotations`** — rejected: a library class puts
  the set in one place by construction; a table is a second copy needing a join test.
- **A global `acceptSystemExits()`** — rejected above; the relaxation-surface rule is the point.
- **Stage-2-only lifetime** — rejected above; costs nothing today and shapes the javadoc.

**Status.** Ruled and shipped 2026-09-02. The module is `migration-guardrails`
(`com.vaadin.swingbridge:swingbridge-migration-guardrails`, published like the other libraries), holding
`com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails` and a 16-test suite: the empty-roots tripwire on all four
entry points, one fixture per verdict of each gate, a fixture pinning every immutable-type category
`@IntentionallyStatic`'s javadoc lists, the compiler-synthetic skip (imported as a package so javac's
`$VALUES` and `$SwitchMap$` classes come along), the lambda-in-`main` and anonymous-class relaxation
limits, and the three-reports-in-one aggregation. Both consumers are the six-line block —
`testapps/crud/1-emulators` (one root) and `testapps/inventory/1-emulators` (three roots, the varargs
proof) — and `grep -rn IMMUTABLE_TYPES --include=*.java` now returns one hit. Guide Phase 6 step 6 prints
the block and the two coordinates, and names no ArchUnit version. ArchUnit went to 1.5.0 in the same
round.

**Source:** M1D_static_sweep_allowlist (the rejection whose revisit trigger fired, the closed-enum
reasoning the relaxation rule generalizes, and the `RetentionPolicy.CLASS` mutation check the module's
`Vetted` fixture carries forward), M1D_static_taxonomy (Step 0's closure, which by-name matching
respects), M1D_lsp_prerequisite (the discoverability bar the builder is measured against),
`ideas/local-llm-migration-target.md`.


<a id="M1D_static_sweep_tool"></a>
## M1D_static_sweep_tool — the static sweep's worklist comes from the pre-swap class files, read with the JDK's own class-file API, not from an LSP

The static sweep's three mechanical questions —
[Step 1](../../guides/1-swing-to-emulators/static-fields.md#step-1--build-the-worklist)'s kind-per-declaration,
`Q_component` ("is this field's declared type a component, subclasses included") and `Q_method_or_type`
("does this static method's body touch a static field") — are answered off the **stage-1 class files**
by a third `main` in `:migration-tool`, `com.vaadin.swingbridge.migration.tool.staticsweep.StaticSweep`. It matches the
existing two exactly: zero runtime dependencies, a finder rather than a gate, exit 0 whenever it ran,
Markdown output that *is* the agent's worklist, invoked with `-cp` and a class name. Migrator-facing:
static-fields.md Step 1 and guide.md Phase 1 / Phase 0's fallback.

**Why the pre-swap build, when [M1D_lsp_prerequisite](#M1D_lsp_prerequisite) rejected "require a build".**
That rejection was about the *post-swap* build — at stage-2 day zero the app may not compile against
`:emulators`. The app being migrated is by definition a running Swing app, so its stage-1 classes exist
before a single import is rewritten, and the rejection was never applied to them. Read from those, the
sweep's answers are not merely as good as an LSP's but better: kinds and declared types come from the
compiler, so there is no multi-line, generic-method or annotated-field breakage to worry about;
`getstatic` / `putstatic` operands **name the field** a method body touches, where an LSP returns
references and leaves read-vs-write to the reader; and the `Signature` attribute carries the type
arguments erasure was feared to lose.

**The API, and the one requirement it brings.** `java.lang.classfile` — [JEP 484](https://openjdk.org/jeps/484),
final in JDK 24 — parses fields (descriptor, `Signature`, `ConstantValue`, `RuntimeInvisibleAnnotations`,
which is where a `CLASS`-retention `@IntentionallyStatic` lives), the `getstatic` / `putstatic` /
`invoke*` instructions per method with their `LineNumberTable`, and `InnerClasses` for static nested
types. The hierarchy question uses the JVM's own resolver: `Class.forName(name, false, loader)` over a
`URLClassLoader` on the app's class directories plus whatever dependency jars are handed in, then
`java.awt.Component.isAssignableFrom`. `initialize = false` means no app code runs (measured: no Hibernate
boot, no seed), and a class that will not load is reported **unresolved**, never guessed. The requirement:
**`javac --release 21` rejects `java.lang.classfile`** (checked; `--release 24` compiles clean), so
`:migration-tool` overrides the reactor's `maven.compiler.release` to **24**. This is the one module where
the release differs from the reactor's, for one reason stated in its pom: the jar is *run*, never
compiled against, and JDK 24+ is already SB-Emulators' floor for build and runtime (CLAUDE.md). The class files
being swept may be of any version — a Java 8 app's classes read fine. Rejected: a fourth module at 24
(leaves the two source tools at 21 for nobody's benefit); parsing `javap` output (a second copy of a
format nobody promised to keep stable); ASM (a dependency, which the module's contract forbids).

**Measured 2026-09-04**, on three stage-1 trees and one stage-2 tree, buckets by the guardrail's own rule
(`final` *and* immutable type → CONSTANT, else WORKLIST):

| | inventory stage 1 | crud | jlawyer-shape | inventory stage 2 |
|---|---:|---:|---:|---:|
| static fields / synthetic / enum constants | 72 / 11 / 7 | 7 / 1 / 3 | 22 / 5 / 7 | 60 / 11 / 7 |
| CONSTANT / **WORKLIST** | 34 / **20** | 1 / **2** | 5 / **5** | 39 / **3** |
| WORKLIST rows whose declared type is a component | 6 | 0 | 0 | 0 |
| fields with a component in a *type argument* | 0 | 0 | 0 | 0 |
| static methods / touching an app static | 120 / 21 | 10 / 1 | 16 / 6 | 123 / 18 |

Every number with a prior measurement reconciled exactly. 72 − 11 − 7 = 54 = Step 1's hand-corrected 47
plus `com.vaadin.swingbridge.fixture.Seed`'s 7 (the 65th file, outside the 64-file count). **The 20 inventory rows are the
13 M1D_former_singletons enumerates, one for one, plus `Seed`'s seven `static final Date`s** — which the
pre-run verdict table missed and run 1's Phase 6 gate then flagged, so the bytecode worklist was more
complete than the golden table and agreed with the gate that caught the omission. Component-typed
**6/6**, including `currentWindow : AbstractFunctionPanel`, an app class two hops above `JPanel` — and
still 6/6 with no dependency jars on the classpath, since that hierarchy passes through the JDK. Nested
static types 5/5 against source. Stage 2's 60 − 11 = **49** is the run-1 agent's own `javap` count,
explained; its three surviving rows all carry the annotation in bytecode, reproducing the guardrail's
green. Three things the probe found that the idea had not claimed: **read-mostly becomes a query
result** (writers outside `<clinit>` per field, with lines — `isLoggedIn ← loginSuccess:77,
handleLogOut:259` refutes application scope before anyone reads the code; `sessionFactory ← none`;
`Case.idSequence ← nextId:12` is the `Q_counter` shape); **source location mostly recovers** (a field
initializer's `putstatic` in `<clinit>` carries the declaration line — 13/20 rows, the exact lines; the
rest have `SourceFile` plus their writers' lines); and **the completeness diff falls out for free** —
the same tool over stage-2 classes gives every stage-1 row a fate (routed / made `final` / annotated /
still unvetted), which is [former-singletons.md's closing check](../../guides/1-swing-to-emulators/former-singletons.md#checking-you-finished)
as a diff rather than as two counts. Also found, outside this entry's scope: `jlawyer-shape/1-emulators`
has five rows the rule flags, none annotated, and no `MigrationGuardrailsTest` — the graded tree predates
the artefact.

**Known limits, each a statable reason rather than a guess.** Explicit `static { }` blocks and field
initializers compile to the same `<clinit>`; the heuristic that worked (exception handlers in a
`<clinit>` whose class owns a non-synthetic static — after excluding `$SwitchMap$` holders, whose
`<clinit>` is all `NoSuchFieldError` handlers) found the app's one block and would miss a block without a
`try`. The routing information survives regardless: the guide triages a block into the fields it
configures, and `<clinit>`'s `putstatic` targets are exactly those. Raw `static Map CACHE` and wildcards
defeat the `Signature` recovery — the rule flags them anyway (mutable type), so the demotion guard stays
a read for those rows. A write from a lambda is attributed to `lambda$setWindow$0`, the enclosing method's
name in the synthetic. `<clinit>` invoke lists (the `G_construction` timing signal — `SessionUtils` builds
its `SessionFactory` at class-load) see one level; `Seed`'s `ZoneId.systemDefault()` sits inside an app
helper, so the scan descends into app-owned callees. Stale class files are the migrator's version of
CLAUDE.md § Building's lesson: the tool prints class-file count and newest class mtime against newest
source mtime, and the guide says `mvn compile` first.

**Is this the "second parser" M1D_lsp_prerequisite refused to own? No.** That rejection is of "a Java
parser and classpath resolver, which is what an LSP already is". The tool has no grammar in it and
nothing to keep in step with the language: the compiler already parsed and resolved, and the tool reads
the result — a field table, a descriptor, opcodes. The `Class.forName` half is the JVM's resolver, not
SB-Emulators'. The rejection stands for a *source* tool and does not reach a bytecode one.

**What this narrows in M1D_lsp_prerequisite — and what it leaves.** The prohibition is untouched: Step 1
and the `Q_component` / `Q_method_or_type` verdicts may never come from pattern-matching over text nobody
read, and bytecode is the compiler's answer, not pattern-matching. What changes is the *mechanism*
sentence and the fallback: Step 1 no longer needs the LSP at all; the no-LSP continuation shrinks from
"read every file in the UI-bearing partition" to "run the tool, then read the declaring classes of the
WORKLIST rows" (6 classes instead of 65 files on inventory — and that reading is for verdicts, which
were never mechanisable); the two-enumerations reconciliation becomes the `--diff` against stage 2; and
the `javap -p` recipe in Phase 0's fallback is replaced by the tool. **Phase 0's gate stays**, on a
narrowed justification: with Phase 2 tooled (M1D_import_swap_tool) and the sweep tooled here, the LSP's
remaining load-bearing uses are reading `@IntentionallyStatic`'s javadoc through the tooling the agent
already has open (M1D_static_sweep_allowlist's argument) and navigation during the Phase 3–5 hand edits —
neither a *silent* hazard. The tool's report prints the allowlist rule and the four `Reason`s inline so
the first stops depending on an LSP. Whether the gate should then drop from hard to recommended was left
open as **`Q_lsp_still_hard`** — **resolved 2026-09-09 by [M1D_lsp_recommended](#M1D_lsp_recommended):
recommended, not a gate.** The evidence that flipped it was not the next round's worklist
discipline but three rounds making zero LSP calls after Phase 0, plus the discovery that their
"references dead" observation was a Claude Code gitignore artefact. The tool this entry decides is
therefore no longer a narrowing of the gate but its replacement, which moves the hand-off from
nice-to-have to blocking.

**One list, one reader — `Q_immutable_types_home`.** The immutable-type set lived in
`MigrationGuardrails.IMMUTABLE_TYPES` (private) with `IntentionallyStatic`'s javadoc as its prose
definition; the tool needs the same set and must not drift from it. Rejected: moving the set into
`:migration-annotations`, which is one annotation and nothing else by contract. **Superseded in its
second half by `Q_one_rule_engine` below:** an earlier draft kept the set in both modules and had a test
in `:migration-guardrails` assert the two copies equal, in `PortedTypesTableTest`'s golden-file style.
With the rule engine consolidated there is one copy and one reader, so that test is deleted rather than
maintained — an equality check earns its keep only where two copies genuinely have to exist.

**Landed 2026-09-04 as a code constant, not a tsv, and that is a deviation worth stating.** The set is
`com.vaadin.swingbridge.migration.tool.guardrails.ImmutableTypes` in `:migration-tool`, beside a name-and-flags predicate
(`isImmutable(typeName, primitive, isEnum, isRecord)`) that takes plain facts rather than a parsed
representation, so a future reader that does not use ArchUnit gets the same verdict. A
`META-INF/emul/immutable-types.tsv` was planned by analogy with the module's other two tables and is
**deferred, not forgotten**: those are tables because add-ons *contribute rows to them* across the
classpath, and nothing contributes immutable types. What a tsv would still buy is greppability for the
migration agent, which is real but weaker than a loader plus a golden test costs today. Revisit when
`StaticSweep` needs to print the rule in its report — that is the first caller with a reason to want it
as data.

<a id="Q_one_rule_engine"></a>
**One rule engine, two terminal behaviours — `Q_one_rule_engine`.** `StaticSweep` and
`MigrationGuardrails` ask the *same* question — is this `static` field provably immutable, else
allowlisted — for opposite purposes, and an early sketch had them answer it twice: the tool reading
class files with the JDK's class-file API, the gate reading them through ArchUnit. Two readers of one
rule is the drift risk the whole `Q_immutable_types_home` paragraph exists to avoid, reintroduced one
level up. So the implementation consolidates into **`:migration-tool`**, which takes a dependency on
ArchUnit, and `:migration-guardrails` depends on the tool.

**Landed 2026-09-04**, ahead of `StaticSweep` and independently of it.
`com.vaadin.swingbridge.migration.tool.guardrails.GuardrailEngine` holds the three rules and the ArchUnit reader;
`com.vaadin.swingbridge.migration.tool.guardrails.ImmutableTypes` holds the shared predicate;
`com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails` is a delegating facade of the shape it always had. The
engine exposes `violations()` — returning the reports and **judging nothing**, which is what a printing
`main` needs — and the facade's `run()` is the throw. `:migration-tool`'s dependency tree is now
`archunit` → `slf4j-api` plus the zero-dependency `migration-annotations`: no Vaadin, no `:emulators`,
no `:surrogates`, so the contract restated below holds as stated rather than as intended. Acceptance was
the guardrails suite passing **unchanged** through the facade (16 tests, fixtures covering each `Reason`
category and both exit relaxations) — behaviour equivalence is the only evidence that could show the
rule had drifted while being moved.

**That is a precision fix to the tool's dependency contract, not a relaxation of it.** The contract
reads "zero runtime dependencies", but the hazard it names is specific — a dependency on `:emulators`
"would drag Vaadin onto the classpath and hide the swap table behind a build". ArchUnit trips neither
half: it hides no table, and it has no bearing on M1D_static_taxonomy Step 0's module classification,
since no migrated app depends on the tool. The contract is therefore restated as **no dependency on
`:emulators`, `:surrogates` or anything Vaadin** — which is what "zero" was a rough proxy for, and the
proxy is what made this look forbidden.

**`:migration-guardrails` stays, as a facade.** Deleting it and pointing migrators at `:migration-tool`
directly would work mechanically and would cost M1D_guardrails_artifact's two guarantees: the migrator
imports one SB-Emulators type and no `com.tngtech.*`, and the artifact is test-scope by consumption. The facade
keeps both — same class, same `allowSystemExitInMainMethods()` / `allowSystemExitIn(...)` surface — while
the duplication disappears behind it. ArchUnit is transitively on the migrator's *test* classpath either
way, exactly as today, so nothing about their build changes.

**The two terminals are the point, and neither absorbs the other.** `StaticSweep`'s `main` is a
*progress meter* run repeatedly **during** the migration: it prints the worklist to stdout and exits 0,
because during the sweep a non-empty worklist is the expected state, not a failure. `MigrationGuardrails.run()`
is the *gate* **after** it: it throws, and it is what makes the sweep stay done — a `static` field added
six months later is caught by the gate and by nothing else. Collapsing the gate into a printer was
considered and rejected for that reason; M1D_guardrails_artifact's point that the gate is **permanent
rather than stage-2** (its component match is by name, so it outlives `vaadinx` entirely) is what the
throw protects.

**Two consequences to carry into the implementation.** The `-cp` invocation grows by ArchUnit and
`slf4j-api`, so nobody should be assembling it by hand — which is what happened:
[D_kit_tool_zip](../../emulators/decisions.md#D_kit_tool_zip) has the module package its own
`bin/` + `lib/` zip, so the classpath is a folder wildcard the shipped launcher fills in and a
shaded tools jar was never needed. **`-cp` survives regardless** — for `ImportSwap` the classpath
*is* the configuration, and `java -jar` ignores `-cp`, leaving the add-on tables invisible. (ArchUnit
is 4.6 MB of that zip's 4.7, dwarfing the 60 KB tool. Noted, not weighed: download size is not a
constraint for the tool zip, so ArchUnit's bulk is no argument for splitting the rule engine back
out — D_kit_tool_zip § *Accepted costs*.) And the module-level sentence "finders/assistants that exit 0 whenever they ran" no
longer covers everything in the module: the contracts are per **entry point** — mains exit 0, the gate
facade throws — not per module.

**Not decided here:** whether the component and JVM-exit checks could also come off the class-file
reader and let ArchUnit go entirely. Both are what ArchUnit is genuinely good at — the component check
resolves the type hierarchy through subclasses, and the exit check needs origin-code-unit granularity,
which is precisely what lets `allowSystemExitInMainMethods()` permit an exit in `main`'s body while
still flagging a lambda inside it. Revisit once `StaticSweep` exists and its reader's reach is known.

**What the tool leaves to judgement, measured.** Its report pre-fills every mechanical column of
M1D_static_taxonomy's review-gate table (kind, declared type, component-or-not, writers, readers,
initializer line, constants bucket); the agent supplies the terminal verdicts. On inventory the residue
after the tool has spoken is three real decisions out of 13 rows: `isLoggedIn` (application scope
refuted by its writers; session vs tab), and which `Reason` `stringConstantsMap` (writes only inside its
own accessor — the guarded-lazy-init shape) and `sessionFactory` (no writers, `<clinit>`-built) claim.
The six component-typed rows are the prohibition; `debug` and the three loggers are "add `final`".

**Source:** M1D_lsp_prerequisite (the ruling this narrows, and the rejection it re-reads), M1D_static_taxonomy
(the tree whose mechanical nodes this answers), M1D_former_singletons (the 13-row enumeration the probe
reproduced), M1D_guardrails_artifact (the bucket rule mirrored, and the list `Q_immutable_types_home`
shares), M1D_import_swap_tool / M1D_hazard_scan_tool (the two `main`s this one matches), CLAUDE.md
§ Building and the JDK 24+ mandate. **Landed 2026-09-09** as
`com.vaadin.swingbridge.migration.tool.staticsweep`, with `migration-tool/README.md` § "Phase 1 — `StaticSweep`" as the
invocation, the example report and the pinned per-testapp counts; the probe and its hand-off idea file
are deleted.

<a id="M1D_pre_boot_server_zone"></a>
## M1D_pre_boot_server_zone — Pre-boot date code keeps the server zone; only calendar dates move to midday

Code reached only from `main()` — seeding, schema setup, a first-run bootstrap — runs before any user
exists, so there is no browser zone to read. The question is what it should use instead, and the answer
had been "name an explicit zone: UTC by default, midday for calendar dates." That is reversed here: **it
keeps the server zone**, and only the *time of day* changes.

**Why the server zone is right rather than merely tolerable.** The ["Date and time
handling"](../../guides/1-swing-to-emulators/dates.md) chapter opens by calling the
server zone the bug, and pre-boot that premise is suspended twice over. There is no browser to disagree
with, and the deployment class these apps land in is intranet — the app stays in-house, often in one
building, so the server's zone *is* its users' zone. UTC is predictable but it surprises the person
deploying, and it makes the seed the odd one out: everything the app computes at runtime uses the browser
zone, which in that deployment equals the server's. It is also the only answer requiring no edit, so the
alternative asks a migrator to change working code into a value less aligned with the rest of the app.

**Why midday survives the reversal.** Zone and time-of-day were bolted together and only one of them is
about zone choice. `atStartOfDay` stores an instant that renders a day early for any browser west of
wherever it was built — still true when "wherever" is the server. In a single-zone deployment midday
costs exactly nothing (`atStartOfDay` and `atTime(12, 0)` render as the identical calendar day), and it
is the one part of pre-boot date handling that is cheap now and expensive later, because the value is
**stored**. When it starts biting — a second office, a user connecting from home, a laptop back from a
trip — the zone is a one-line code change while midnight-stored rows are a data migration. So the
general "revisit when it bites" principle applies to the zone and deliberately not to the time.

**Bucket 1 is not tagged, and that is the load-bearing half.** `vaadinx.text.SimpleDateFormat` /
`vaadinx.util.Calendar` reached from `main()` do **not** throw: with no session and no `EmulatorContext`
they take the same path as a background thread, WARN once, and format in the server zone (measured
2026-09-04 against `EmulatorContext.currentTimeZone()`, whose only throw is the UI-thread
SD_browser_timezone case). So "swap the import, change nothing" holds for pre-boot code too, and such
seams get **no** `// TODO[browser-tz]:` marker — the fix would land in a shared date utility called from
both the seed and the UI, which is a refactor bought for no gain. Bucket 3 is marked because its same
decision costs one argument at the seam. The advice differs by *price*, not by correctness, and the docs
say so; a migrator seeding one row with `Calendar` and one with `LocalDate` otherwise reads it as an
oversight.

**Accepted consequence.** A shared helper answers in two zones — `getCurrentFiscalYear()` returns the
server's answer to the seed and the browser's to a UI thread. Correct on both paths, and near a year
boundary a seeded row can legitimately disagree with a freshly-computed one. Stated in the guide because
it otherwise costs an afternoon.

**Revisit trigger.** The app stops being single-zone: a second site, routine off-site access, or users
whose browsers are deliberately set elsewhere. At that point the zone becomes a real decision per value
(a fiscal calendar may want head office's zone, not the server's), and the midday convention is what
keeps the already-written rows from needing to move.

**Source:** run 3 of `/migrate-inventory-dryrun` (2026-09-04), stumble 1 — a bucket-1 seam
(`DateTimeUtils.getCurrentFiscalYear()`) reached from both the UI thread and the seed, which the buckets
did not cover; supersedes the UTC/midday text landed by run 2's docfix the same week.

<a id="M1D_addon_starter_status"></a>
## M1D_addon_starter_status — The shipped add-ons are starter prototypes, shipped as-is with no widening planned

The two add-ons in
`third-party/` — `swingbridge-emulators-jcalendar-1.4` and `swingbridge-emulators-jgoodies-forms-1.2.1` — are **starter
prototypes**: free to use under their per-module licences (LGPL-2.1 and BSD, each its upstream's), bounded to what one real
migration ([`testapps/inventory`](../../testapps/inventory)) needed, and **shipped as-is — no
widening is planned**. If that ever changes, this entry is edited in place and the status lines it
put into the migrator-facing docs (listed below) are revised in the same commit.

**What "starter prototype" commits to.** The surface is frozen at what ships. Each add-on's
`MIGRATION.md` already lists what is in and what is out, and per
[`M1D_addon_migration_docs`](#M1D_addon_migration_docs) the boundary is a compile error naming the
missing symbol — that stays the contract, and requests to widen an add-on are parked rather than
worked. "Starter" does not shrink what exists: inventory's usage is
the floor, not the ceiling, so a port that already covers more than the testapp uses keeps it.
Bugfixes to the shipped surface are ordinary maintenance, not development, and are not frozen.

**Why the label is "starter", not "demo".** "Demo" warns off the production use the licence allows
and the stage-2 guide relies on (a migrated inventory runs on these two jars); "community" / "core"
imply something complete, which a deliberately bounded port is not. "Starter" says bounded-but-real.
The word "prototype" is used alongside it here and in the status lines for the one thing "starter"
does not say on its own: that the bound is not currently moving.

**Why it is stated up front rather than left to the compile error.** The bundled inventory migration
works because it fits the starter; a migrator's own app may hit the boundary on its first
`JDateChooser` call. A reader who finds that out from a compile error after choosing the route reads
the add-on as bait. So the status is said before they start — in the add-on index, in each
`MIGRATION.md`, in README's licence section and in COMPARISON.md's "claims this project does not
make" — and not only here.

**If full-coverage add-ons are ever built, three constraints apply** — the price of the starter
being an on-ramp rather than a trap. They are recorded here so the shape is known in advance; none
is a commitment that full add-ons will exist.

- *Package-compatible superset.* A full add-on keeps the starter's packages and signatures exactly
  (`vaadinx.toedter.calendar.JDateChooser`, `vaadinx.jgoodies.forms.*`), so upgrading is pom-only —
  no import rewrite, no recompilation surprises. The starter's `ported-types.tsv` rows and public API
  are a subset of the full add-on's. Superset by construction, not discipline: the full add-on's build
  compiles the open starter sources plus the extras into one jar, so there is one copy of every
  starter file; the starter's API is frozen after release, which this entry already does.
- *Distinct coordinates.* Starter and full never share `groupId:artifactId`, so a pom holds one or
  the other and Maven's nearest-wins never silently picks the starter over the full one. Same
  `vaadinx.*` packages, different coordinates, is the deliberate shape.
- *Licence lane carries over, and it is upstream's either way.* Both starters carry the licence of
  the library whose API they reproduce, per
  [M1D_addon_upstream_licence](#M1D_addon_upstream_licence) — jcalendar **LGPL-2.1**, jgoodies
  **BSD** — so a full-coverage port that does reach for upstream source changes nothing about the
  licence it ships under. That is most of the point of the rule: the lane no longer moves when
  coverage grows. Upstream's terms travel with forked files regardless of how a module is
  distributed.

A full add-on would be a separate product built outside this repo — the one-way direction of
[`M1D_addon_packaging`](#M1D_addon_packaging) (core never learns add-ons exist) is what makes a
drop-in replacement possible at all, and nothing in core changes for it.

**Where the status is stated.** [`addons.md`](../../guides/1-swing-to-emulators/addons.md) (the index),
each add-on's `MIGRATION.md`, [README.md § License](../../README.md#license),
[COMPARISON.md § Claims this project does not make](../../COMPARISON.md#claims-this-project-does-not-make),
and CLAUDE.md's add-on paragraph. All say the same three things — free, bounded, not being
widened — and point here for the why.

**Revisit trigger.** A decision to build full-coverage add-ons, which would turn the constraints
above into requirements and add an "upgrading to the full add-on" section to each starter's
`MIGRATION.md`; a decision to let add-on work resume on its own priorities instead drops the
frozen-surface line.

<a id="M1D_lsp_recommended"></a>
## M1D_lsp_recommended — The language server is a recommendation, not a gate; every check the gate ran comes off the stage-1 class files, and the "check 3 is dead" evidence was a Claude Code gitignore artefact

Decided 2026-09-09 (maintainer, on a Fable measurement session), resolving `Q_lsp_still_hard` from
[M1D_static_sweep_tool](#M1D_static_sweep_tool) and **superseding the gate half of
[M1D_lsp_prerequisite](#M1D_lsp_prerequisite)** — its prohibition (no static-sweep verdict from
pattern-matching over text nobody read) stands untouched; what falls is the mechanism sentence that
welded the prohibition to a connected LSP, the three-check evidence ritual, its three-way failure
taxonomy, and the kit's `/lsp-preflight`. **Implemented 2026-09-09**, in four commits: `StaticSweep`
first (M1D_static_sweep_tool), then the kit and its skills, then guide.md's Phase 0 / static-fields.md
Step 1 / former-singletons.md / checklist.md, then the guide-loop harness.

**Decision, in three sentences.** Phase 0 becomes *compile the stage-1 app and run `StaticSweep`
over its class files*; that output is the worklist, on every run, with or without an LSP. An IDE or
language server is **recommended** for navigation during the Phase 3–5 hand edits, named in one
paragraph with the one caveat below, and the agent decides per use whether to reach for it. Nothing
in the guides, the kit or the guide-loop harness gates on it, checks for it, or loads it on the
migrator's behalf.

**Why the gate goes — the argument from what it protected.** M1D_lsp_prerequisite required the LSP
for three mechanical questions: kind-per-declaration, `Q_component`, and read-mostly via references.
M1D_static_sweep_tool answers all three from the stage-1 class files, each **more** reliably (kinds
and declared types from the compiler; `getstatic`/`putstatic` naming the field *and* the direction,
where a references list leaves read-vs-write to the reader). With the sweep tooled and Phase 2
tooled (M1D_import_swap_tool), the LSP's remaining uses are reading a javadoc the tool now prints
inline and navigating hand edits — M1D_static_sweep_tool's own words, "neither a silent hazard". A
gate that protects nothing silent is ceremony, and this one cost a great deal: the kit's
`/lsp-preflight` skill, the deleted-then-reversed `migrator` agent, the no-subagent ruling's original
rationale, a kit-rooted second session for the rounds, a `jdtls` install as the customer's first
prerequisite, and roughly 140 lines of Phase 0 that three rounds spent their first quarter-hour on.

**Why the gate goes — the argument from what it measured.** Every kit-rooted round (`crud`,
`jlawyer-shape`, `inventory`) recorded check 3 red, took the substitute, and then made **no further
LSP call** for the rest of the migration; `inventory` made 5 calls total, all in Phase 0, and
enumerated its statics with the grep the guide forbids while `documentSymbol` had just passed. So the
gate's own outcome predicted nothing about the tool's use, which is the property a gate cannot lack.
And the red itself was false. Eleven probes on 2026-09-09 — a plain stdio LSP client and the real
Claude Code client, both against `jdtls` 1.60, fresh workspace per run unless noted — found the
cause: **the Claude Code `LSP` tool drops every result location whose file is gitignored.** Hover carries no location and survives; `findReferences`,
`goToDefinition` and `workspaceSymbol` return locations and come back empty. The rounds' kit sat at
`zip-distro/target/unzipped/…`, which this repository ignores. The controlled pair, same folder,
same client, same server, same workspace:

| `.gitignore` two directories up | `findReferences` | `goToDefinition` |
|---|---|---|
| `target/` ignored | 0, three attempts over 60 s | none |
| that line deleted (files untracked, not ignored) | 16 in 4 files | found |

The plain client under the same `target/` answered 9 references in 2 files 17 s after launch (so
`jdtls` was never at fault), the Claude Code client on a non-git copy answered the same with zero
retries (so the client is fine outside a repo), and a mid-session copy with a duplicate artifactId
still answered (so the kit skills' copy step was not the cause either). Every "observed with `jdtls`
under Claude Code" sentence in the guide was observed on ignored files. Two side findings stay
relevant to the harness: the `jdtls` launcher keys its workspace on the working directory's
**basename** (`sha1("swingbridge-emulators-0.1-SNAPSHOT")` is the kit workspace), so every kit
rebuild reuses one workspace; and a workspace remembering projects whose `.project` files a rebuild
deleted hangs a fresh client at `initialize` outright.

**What "recommended" says, and the one caveat it carries.** One paragraph, in guide.md where Phase 0
was and in the kit README's prerequisites: *an IDE or a Java language server helps you navigate
during the hand edits; `javac` is the authority on types, and the sweep tool is the authority on
statics — neither depends on it.* Plus the caveat this session paid for: *in Claude Code, a file that
git ignores has no locations — hover works, everything that returns a position returns nothing; if
you unzipped this kit into an ignored folder of your repository, move it or expect that.* No
"try the LSP first, fall back to grep": the sweep never touches the LSP, so there is nothing to fall
back from, and the prohibition is enforced by the tool's existence rather than by a check.

**What changes downstream.** The kit drops `/lsp-preflight` and the `jdtls` prerequisite; the three
migrate skills lose their step 4; `D_kit_no_subagent` keeps its visibility reason and loses its
tool-locality one; `D_kit_lsp_preflight`'s measurements stand as history with the spawn and the
pre-flight both gone. The round stops running from a kit inside this repository: `/build-kit`
unpacks the **zip artifact** into a temp folder outside any git repository, for two reasons that are
now one fact each — no `.gitignore` of any subsystem can reach it (the maintainer's framing: "no
danger from gitignore in some other subsystem"), and the migrating agent cannot walk up to
`../../..` and find the emulator sources or the decision logs it is supposed not to have. Because
the workspace is keyed on the basename, `/build-kit` also removes
`~/.cache/jdtls/jdtls-$(sha1 of the kit folder name)` after unpacking, so a rebuilt kit never
inherits the previous build's project records — that is the C/C2 hang, and it is the customer's
condition (one unzip, fresh workspace) restored.

**Rejected alternatives.**
- **Keep the gate, fix the check-3 paragraph to name the gitignore cause** — rejected: it repairs
  the one check that was wrong and leaves a gate whose remaining checks protect nothing the tool
  does not already protect better. M1D_lsp_prerequisite's own "recommend rather than require"
  rejection rested on "the sweep converts into the agent's best grep"; with `StaticSweep` the sweep
  converts into the tool's output, and the rejection's premise is gone.
- **Keep `/lsp-preflight` as an optional environment check** — rejected: a skill that proves a
  recommendation is available is a gate with softer wording, and the migrate skills would go on
  running it first. A customer who wants to know whether their LSP works can hover something.
- **Run the sweep off the LSP when present, off bytecode when not** — rejected as two readers of one
  rule, the drift `Q_one_rule_engine` exists to prevent; bytecode is the stronger answer anyway.
- **Report the gitignore behaviour upstream and wait** — orthogonal; worth doing, changes nothing
  here, since the gate was ceremony before the cause was known.

**Revisit trigger.** A round or a customer report where an agent with the sweep tool in hand still
mis-scopes a `static` in a way an LSP query would have caught — that is the evidence
M1D_lsp_prerequisite's silent-hazard argument would need to come back, and none of the eleven probes
or three rounds produced it.

**Source:** the maintainer's two recommendations (2026-09-09: "drop the requirement or turn it into
a recommendation, and leave it up to the agent"; "all checks previously done by the LSP should be done
by our migration-tool") and the temp-folder proposal; M1D_lsp_prerequisite (the ruling superseded,
and its own separable prohibition); M1D_static_sweep_tool (`Q_lsp_still_hard`, and the tool that
discharges the prohibition); D_kit_lsp_preflight, D_kit_no_subagent (the kit machinery this retires);
the three `testapps/*/1-emulators/STUMBLES.md` Phase-0 records, now read as harness evidence.

---

<a id="M1D_addon_upstream_licence"></a>
## M1D_addon_upstream_licence — an add-on takes its upstream library's licence

**Decided 2026-09-21** (maintainer, in the licence-lanes review that also produced the `:surrogates`
similarity measurement). Supersedes the **licence limb** of
[`M1D_addon_packaging`](#M1D_addon_packaging) — *"a reimplementation … inherit[s] the project
licence"* — and nothing else in it.

**Decision.** A `third-party/` add-on is licensed under **the licence of the upstream library whose
API it reproduces**, whether the module is a *fork* or a *reimplementation*. The
fork-vs-reimplementation distinction survives intact and still decides **whose copyright notice sits
on which file** (a fork keeps upstream's headers verbatim; a reimplementation carries Vaadin's
copyright under upstream's licence terms). It simply stops deciding the licence.

Concretely: `third-party/jgoodies-forms-1.2.1` stays **BSD** (no change — it already complied by the
fork route), and `third-party/jcalendar-1.4` moves from the inherited GPLv2+CE to **LGPL-2.1-only**.

### Why — and the decisive reason is not caution

1. **LGPL-2.1 §3 makes the inherited licence conditionally invalid, not merely over-permissive.**
   §3 permits relicensing an LGPL work under the **ordinary** GPLv2. It does **not** authorise
   GPLv2 **+ Classpath Exception**, because the CE grants a linking permission over code that would
   not be ours to grant it over. So for `jcalendar`, the inherited project licence was valid *only*
   in the future where the port never becomes derivative; LGPL-2.1 is valid in both. This is the
   argument that settled it, and it generalises to any weak-copyleft upstream.
2. **A reimplementation's cleanliness is a function of how little was ported, and the lane is
   decided at the moment that is least representative** — module birth, when the port is smallest
   and the analysis most favourable. Nobody re-runs the analysis on widening. That is a process
   defect as much as a legal one, and pre-paying the licence removes the question from the growth
   path entirely. (The maintainer's framing: *"the JCalendar port is so small anyone could do it —
   today. But if we port more of JCalendar, GPLv2+CE will start biting at some point."*)
3. **It costs the migrator nothing, by construction rather than by luck.** An add-on exists only to
   satisfy imports the app already had, so its only consumers are apps that already shipped the
   upstream library under the upstream licence. The LGPL conversation has therefore already happened
   at every consumer of `swingbridge-emulators-jcalendar-1.4`. This holds for every add-on, present and
   future, which is what makes it a rule rather than a judgement about one module.
4. **It removes a per-module legal judgement from a recurring process.** Add-ons are a pattern.
   `M1D_addon_packaging`'s "the licence question is *dissolved* rather than solved" was true and
   still needed a ⚠️ gate and a named owner per module; this needs neither.

**It was free when decided, and that window was closing.** Nothing had been published — no
`com.vaadin.swingbridge:*` artifact existed on Maven Central and `-Prelease` had never run
(verified 2026-09-21) — so no licence in the repository had been granted to anyone and all of it was
changeable at will. After first publish, changing it needs the consent of everyone holding a copy,
which in practice means never. Adoption cost one module.

### What it does *not* mean

- **It is not a finding that `jcalendar` is derivative.** It is not, the analysis in that module's
  `PROVENANCE.md` is unchanged and still believed, and the entry there says so explicitly. Taking
  the licence is what makes the artifact correct whether or not that reading holds — which is also
  what closes the ⚠️ publishing gate `M1D_addon_packaging` recorded against it.
- **It is not a substitute for provenance discipline.** Pre-paying the licence says nothing about
  *which files carry whose copyright notice*, which is the `HDR_jdk_derived` / `HDR_vaadin_gpl` question all over
  again. Each module's `PROVENANCE.md` still records how the code was made.
- **It does not touch SB-Emulators' own modules.** `:emulators` is GPLv2+CE because it *is* a
  derivative of OpenJDK ([D_gplv2_ce_relicense](../../emulators/decisions.md#D_gplv2_ce_relicense));
  that finding stands on its own method and is unaffected.

### The open edge: a strong-copyleft upstream

The rule is clean for permissive and weak-copyleft upstreams — BSD, MIT, LGPL, EPL, MPL. A
**GPL-without-exception** upstream would break it: the add-on links Apache-2.0 Vaadin Flow, which is
the incompatible direction, and we would have no Classpath Exception of our own to rescue it. The
escape hatch would be *"upstream's licence, plus a linking exception where our own copyright permits
and the build requires"* — and the tension is recorded rather than papered over: **if the module
really were derivative, we had no right to add that exception**, so the hatch partly defeats the
insurance the rule exists to buy. Neither existing add-on triggers this, and it is to be decided
when one does, not pre-emptively.

### The cost: it taxes our own code to insure upstream's

An add-on's **Vaadin-specific share usually grows faster than its upstream-derived share**:
`JDateChooser` is three upstream members and the rest is peer wiring to `SJFormattedDatePicker`,
which is entirely ours. Under this rule that novel work goes under upstream's licence to insure
three members — irrelevant at 125 lines, real at 2,000. The fix is the one the architecture already
wants: **keep reusable machinery down in `:surrogates` / `:emulators` and let the add-on stay a thin
API shim.** The rule makes fat add-ons expensive, which is pressure in the right direction.

### What LGPL-2.1 asks of a migrated app, since a migrator will ask

On **distribution**, §6 requires that the recipient be able to relink against a modified build of
the library, plus permission to reverse-engineer for debugging those modifications. The Vaadin Boot
host app [`host-app-vaadin-boot.md`](../../guides/1-swing-to-emulators/host-app-vaadin-boot.md)
hands out satisfies this for free: its assembly ships every dependency as its own jar under `lib/`
(`<unpack>false</unpack>`), and nothing in `guides/` shades or relocates. **Hosting is not
distribution** — an app served over a network is not conveyed to its users, so a cloud-hosted
deployment triggers §6 not at all. LGPL-2.1 has no remote-network-interaction clause; that is
AGPLv3 §13, and nothing SB-Emulators ships is AGPL.

### Where the state lives

`third-party/<module>/LICENSE` (the text, also shipped into the jar's `META-INF/` by the module's own
`<resources>` block — **not** the repository root's, which would state the wrong terms inside the
jar), the module's pom `<licenses>` override, its `PROVENANCE.md` § Licence, its migrator-facing
`MIGRATION.md`, and `vaadinx.LicenseHeaderTest` rule 5, which fails the build if a file in the
jcalendar tree carries the repository's GPLv2+CE header instead of its own.

<a id="M1D_spring_bootloader_only"></a>
## M1D_spring_bootloader_only — Spring Boot is a bootloader only: former singletons stay on vaadin-tab-scope on every lane, and a Spring-based Swing app is unsupported for now

**Decision.** On the Spring Boot lane the migration **introduces no beans**. Spring Boot hosts the
servlet (auto-configured by `:emulators-spring`, per
[D_library_servlet](../../emulators/decisions.md#D_library_servlet)) and nothing else: everything the
static sweep routes to tab scope goes to `FormerSingletons` over `vaadinx.AppInstance` exactly as on
Vaadin Boot ([M1D_former_singletons](#M1D_former_singletons)), and no `vaadin-spring` scope
annotation appears in migrated code. **A Swing app that is already built on Spring is currently
unsupported** — on either lane.

**Why one mechanism for both lanes.** It keeps the bootstrap choice a bootstrap choice. Nothing
needs building for it: `TabScope.setup` runs inside `SwingBridgeEmulatorsBootstrap`, which both
lanes register (the Spring one through the auto-configuration), so tab scope already behaves the same
under Tomcat as under Jetty. A migrated app's code — and the guide's path through
[`former-singletons.md`](../../guides/1-swing-to-emulators/former-singletons.md) — is then identical
whichever host it runs in, and the migrator can switch hosts without retouching a former singleton.

**Why not a `vaadin-spring` scope.** Until shown otherwise, `vaadin-spring` offers **no first-class
tab scope**, and what comes nearest is a workaround with known holes:

- `@UIScope` dies on F5, since Flow recreates the `UI` — the trap
  [`static-fields.md`](../../guides/1-swing-to-emulators/static-fields.md#the-three-scopes) names for
  UI-scoped holders.
- `@VaadinSessionScope` is the wrong boundary: session, not tab, and it has no tab-close teardown,
  which is what `vaadinx.AppTab` hangs D_shutdown_lifecycle's shutdown off.
- `@RouteScope` + `@RouteScopeOwner` on a `@PreserveOnRefresh` layout survives F5 and navigation, but
  per the maintainer's survey ([Vaadin UI scope](https://mvysny.github.io/vaadin-ui-scope/)): re-entering
  the URL by hand recreates the scope unless `partialMatch = true`; the owner layout has to detach its
  content from the element tree by hand to avoid tree corruption; and the scope is not available during
  UI init at all, because the window name it keys on arrives asynchronously (Flow issue #13468).

**Rejected: a custom Spring `Scope` backed by `TabScope`.** Roughly thirty lines in `:emulators-spring`,
and the one option that would give Spring a real tab scope — but the only code that would ever
resolve through it is the migrator's own beans, which is exactly the population this entry declares
unsupported. Nothing on the supported path would consume it.

**Why a Spring-based Swing app is unsupported.** A desktop `ApplicationContext` serves one user,
because there is one JVM per user; on a server it serves every user at once, so **a singleton bean
holding per-user state leaks it between users** — a former singleton in bean form. Unlike a `static`,
**no SB-Emulators gate sees it**: bean state lives in instance fields, so neither the static sweep nor
`MigrationGuardrails` has anything to flag, and a migrated Spring app can pass every guardrail while
one user's open document shows in another's session. The faithful remedy — one `ApplicationContext`
per tab, as tab scope is one JVM per tab — is unresearched and is parked in
[`spring-context-per-tab.md`](../../ideas/spring-context-per-tab.md).

**Unsupported, not halted.** The migration still runs, since a DI container is not a stop (the
earlier "detect Spring, halt the agent" gate was withdrawn unbuilt and stays withdrawn). What is
withdrawn now is the *promise*: the consent fork's "full support" for a Spring app already on Boot 4
/ Framework 7, and for the fork's Vaadin Boot arm, no longer holds, because both arms carry the
per-user-bean hazard the fork was never about. The fork still says which host fits which Spring
version.

**Where this is stated, all of which must change together** when the support position changes:
[`guide.md` § What won't migrate](../../guides/1-swing-to-emulators/guide.md#what-wont-migrate),
[`build-wiring.md` § Which bootstrap?](../../guides/1-swing-to-emulators/build-wiring.md), and
[`host-app-spring-boot.md`](../../guides/1-swing-to-emulators/host-app-spring-boot.md)'s three
constraints.

<a id="M1D_bootstrap_choice"></a>
## M1D_bootstrap_choice — The guides are bootstrap-agnostic: one procedure, a host-app doc and a built seed per bootstrap, Spring Boot recommended, the choice made at Phase 0

**Decision.** The migration guide never names a bootstrap in its shared steps. Everything that
differs between Spring Boot and Vaadin Boot — the host-app classes, their resources, the build file,
the run command — lives in two places per bootstrap: a **seed**, `guides/1-swing-to-emulators/seed/<bootstrap>/`,
which the migrator copies in Phase 3 (`S_copy_seed`), and a **host-app doc**,
[`host-app-spring-boot.md`](../../guides/1-swing-to-emulators/host-app-spring-boot.md) /
[`host-app-vaadin-boot.md`](../../guides/1-swing-to-emulators/host-app-vaadin-boot.md), which says
what each seed file is for and which parts of it are not a free choice. **Spring Boot is the
recommendation** for an app with no DI container, because it is Vaadin's own default; Vaadin Boot is
a first-class lane, not a fallback. The choice is made at **Phase 0** (`S_choose_bootstrap`), from
the table in [`build-wiring.md` § Which bootstrap?](../../guides/1-swing-to-emulators/build-wiring.md#BW_bootstrap).

**Terminology, kept apart on purpose.** *The bootstrap* is the library that starts the servlet
container and hands Vaadin its request threads. *The host app* is the scaffold the migrated views
live in (`Main`, `AppShell`, `AppRoute`, the Vaadin Boot `AppServlet`, `AppErrorHandler`, the
resources). "The Boot" was the first candidate for the former and was rejected: that both options
happen to be named Boot breaks at a third lane — a WAR into WildFly has no Boot — and the shape is
sold partly on a third lane being reachable.

**Why agnostic rather than Spring-shaped.** Measured before the change: eight of the fourteen guide
docs named neither bootstrap, and the residue in the three shared ones was about sixteen lines, so the
seam already existed and only needed naming. A Spring-shaped guide would also have left every
Vaadin Boot testapp exercising the non-default lane, and so the recommended lane untested; with an
agnostic guide all three testapps run the same procedure and differ only in the seed, so the split
**covers** both bootstraps — `crud` on Spring Boot, `inventory` and `jlawyer-shape` on Vaadin Boot,
pinned by `:migration-tool`'s `TestappBootstrapLaneTest`. It also makes the recommendation one
paragraph in the chooser, reversible by editing that paragraph, instead of the shape of the doc tree.
This replaces the earlier plan of "swap the primary host doc, keep the alternative whole", whose
objection to "one doc, two lanes inside" was to *interleaving*, not to pluggable per-lane files.
**A third lane gets cheaper, not free** — Jakarta EE / a plain WAR into WebSphere or WildFly would be
a new seed and host-app doc rather than a rewrite, but it is the lane where the `--add-opens` flag
leaves the app entirely (`CATALINA_OPTS`) and where there is no `main()` for the entry-point split to
land in, so it is the one a guide can help with least.

**Why Spring Boot is recommended, and why the Spring rows lean the other way.** Spring Boot is what
start.vaadin.com hands out, what Vaadin's documentation and tooling assume, and what Vaadin support
sees; recommending otherwise puts the migrated app off the beaten path from day one, whatever the
technical merits of the lighter host. But Vaadin 25 pins Spring Boot 4 / Framework 7, so an app
*already built on Spring 6 or older* would have to upgrade Spring before the import swap — two
migrations at once — and on Vaadin Boot it keeps its Spring untouched (Spring 5 at
`maven.compiler.release` 21, `BW_spring5_bytecode`). The recommendation and the Spring advice
address disjoint populations, and the chooser's table says so, so a migrator with a Spring 5 app does
not read them as pointing opposite ways. Every Spring row is unsupported either way, per
[M1D_spring_bootloader_only](#M1D_spring_bootloader_only) — the table is host advice, not a support
promise.

**Why the choice sits at Phase 0.** Phase 3 is `cp` and edit, the most mechanical stretch of the
guide; a *"pick your bootstrap"* there is a judgement arriving mid-procedure, which an unattended
agent resolves silently and which then makes a run measure a lane nobody chose. So it is asked
before anything is edited, and the kit's skills settle it up front: `/migrate-testapp` pins each
shipped testapp's lane, and `/migrate-your-app` / `/migrate-swing-app` ask once, defaulting to the
chooser's answer and saying which they took when nobody is there to answer.

**The launch shape is asked with the bootstrap, and the fat jar is recommended** (2026-09-24,
maintainer). On Spring Boot the packaged app is launched either as a fat jar (`java -jar`) or as a
plain jar on a classpath of the deployer's own (`java -cp`), and the two treat the `--add-opens`
flag differently: the `java` launcher honours an `Add-Opens` manifest attribute under `-jar` and
ignores it under `-cp` — the launcher's asymmetry, not Spring Boot's. Measured 2026-09-21 with one
class reflecting into `ThreadBuilders$VirtualThreadBuilder` from a jar carrying the attribute: `-jar`
opens, `-cp` on the same jar throws `InaccessibleObjectException`, `-cp` plus `JDK_JAVA_OPTIONS`
opens; `spring-boot:repackage` preserves the attribute. So:

- **The fat jar is the recommendation**: Spring Boot's own default, and the only shape that carries
  the flag inside the artifact, where no launch line or container environment can lose it.
- **The Spring seed pom ships the `Add-Opens` manifest entry by default.** It is what makes the
  recommended launch line flag-free, and it is harmless under `-cp` — whose launch line carries the
  flag itself. The objection that it is invisible magic, silently inert under `-cp`, is met by
  asking the question rather than by leaving the entry out: a migrator who chose `-cp` was told
  where their flag goes.
- **The question is asked at Phase 0, beside the bootstrap one**, although the answer changes
  nothing in the migration itself — only the launch line handed over at the end. One up-front
  interview with defaults costs one question and never stops a run mid-way; an unattended agent
  takes the fat jar and says so. Vaadin Boot needs no such question: its packaged launcher is a
  `-cp` script that already carries the flag.

**Why seeds, and why built.** The host-app files ship as real trees rather than listings, for the
reason `KitLayoutIT` already gave for the example build files: **a listing rots unrun** (the template
those replaced still configured `exec:java`). It also serves the local-LLM stage-2 target — `cp -r`
is mechanical, "create this file with this content" is judgement. Four shape decisions:

- **A fixed, compilable package, `com.example.app`**, rather than a `<package>` token: a token cannot
  be built, and an unbuilt seed is a listing with a different file extension. The rename it costs is
  folded into a copy the migrator performs anyway, into a source tree that already has a package.
- **The build file is `example-pom.xml`, never `pom.xml`**, and no file under `guides/` may be named
  like a live build file (`SeedTreeTest`). A `pom.xml` inside the kit is a project to the language
  server before the migrator has touched anything — the 2026-09-09 round met exactly that, as a
  build failure it had not caused, from a shipped `.project` file.
- **Two complete trees, with the shared classes kept byte-identical by a test** (`AppShell`,
  `AppRoute`), rather than a shared tree plus per-lane overlays — `cp -r` stays one command and
  drift is still impossible.
- **Gated in two halves.** The default build runs `SeedTreeTest` (version pins against the reactor,
  byte-identity, the main class each build file names); `-Pkit` has `:zip-distro`'s invoker build each
  *shipped* seed from its `example-pom.xml`, which for the Spring seed boots the context through the
  `SpringWiringTest` the migrator keeps. The invoker is handed the file in place, so the rename a
  migrator performs is not itself exercised — a lighter gate than a copy-rename-boot IT, taken because
  it needs no new harness.

**Two per-lane ordered lists are not a second tick-list.** The host-app docs carry steps of their
own, and `checklist.md` was deleted in 2026-09 for being a second copy of the procedure. The
difference is the reason that rule exists: `checklist.md` duplicated *the same* procedure and drifted
from it, while the two host-app docs are disjoint and a migrator follows exactly one.

**What the library side contributes** is [D_library_servlet](../../emulators/decisions.md#D_library_servlet):
without it, the one hand-written class that is entirely bootstrap-specific (the servlet) would have
thinned the guide's most dangerous step to *"write whatever your bootstrap's doc says"*. With it,
that step is a two-line subclass on Vaadin Boot and nothing on Spring Boot. If that hand-off were
ever abandoned, the step would need rethinking.

**Sampler stays on Vaadin Boot** ([`sampler/description.md`](../../sampler/description.md)) — a
testbed, not a template.

**Where the recommendation and the lanes are stated, all of which must change together:**
[`build-wiring.md` § Which bootstrap?](../../guides/1-swing-to-emulators/build-wiring.md#BW_bootstrap),
`guide.md`'s `S_choose_bootstrap`, the kit skills (`migrate-testapp`'s lane pins,
`migrate-your-app` / `migrate-swing-app`'s ask, which covers the launch shape too),
[`host-app-spring-boot.md` § Getting the flag to the launched JVM](../../guides/1-swing-to-emulators/host-app-spring-boot.md#SBS_launch)
and the Spring seed pom's manifest entry, `TestappBootstrapLaneTest`'s lane map, and
[`testapps/CLAUDE.md`](../../testapps/CLAUDE.md) § "Two kinds of testapp".

<a id="M1D_error_handler_default"></a>
## M1D_error_handler_default — Every migrated app installs an `ErrorHandler`, and one with no desktop handler gets a ref-numbered `Notification`

**Decision.** The guide's Phase 4 `AppErrorHandler` is installed by every migrated app, including
one whose Swing original had no uncaught-exception handler at all. Its default body logs the
throwable with a sequence number and, when a UI is current, shows a `Notification` quoting only that
number ("An internal error occurred (ref #42). Contact support."). The number comes from a plain
`AtomicInteger` instance field; the SPI or the Spring context creates one listener per deployment,
so the field already has deployment lifetime and needs no `static`. The rule the migrator applies is
**exactly one user-facing signal**: an app whose desktop handler already showed the user something
ports that body in place of the `Notification`; an app whose handler only logged ports the logging
and keeps the `Notification`.

**Why this is not a silent improvement.** SB-Emulators' *emulation* is untouched and stays
faithful: [D_uncaught_handler_chain](../../emulators/decisions.md#D_uncaught_handler_chain) measured
that a desktop EDT throw with no handler prints to `System.err` and shows the user nothing, which is
what Vaadin's `DefaultErrorHandler` does too. `AppErrorHandler` is the migrator's own code, and the
guide says in the open what it adds, so [R_no_silent_improvements](../../CLAUDE.md#R_no_silent_improvements)
— which governs what SB-Emulators does behind the migrator's back — does not reach it.

**Why showing something is right: the diagnostic channel moved to another machine.** "Nothing
shown" meant something different on the desktop. There, `System.err` was on the user's own machine —
a console window, a log file beside the app, something a help desk could collect. Behind a browser
the log is on the operator's server, and the user has no way to see it at all. The same observable
behaviour, *nothing happens*, now hides the failure from the one person who saw it. The toast is a
replacement for that lost channel, not a gloss on top of it.

**Why the ref number is load-bearing.** It is what connects the two machines: the user quotes the
number, support greps the server log for it and finds the stack trace. A bare "an error occurred"
tells the user something broke and gives nobody a way to find it.

**Constraints the listing keeps, and why.** The stack trace never reaches the browser — traces leak
class names, paths, credentials in messages and library versions useful for CVE matching. The
`Notification` sits behind `UI.getCurrent() != null`, because an app that also keeps a JVM-wide
handler for its non-UI threads (the second half of [`H_uncaught_handler`](./spec.md#H_uncaught_handler)'s
split) runs error-handling code where no UI is current, and an unguarded UI call would then throw
from inside the error handler. There the log line is the only signal, as it was on the desktop.
