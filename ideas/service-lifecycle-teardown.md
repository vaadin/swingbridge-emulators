# Service lifecycle & teardown — porting JVM shutdown hooks

**Status:** brainstorm / not decided. Captures the design for mapping a Swing app's
*teardown* code (JVM shutdown hooks, window-close disconnect, `finalize`) onto SB-Emulators'
session lifecycle. Open questions remain; a future design pass picks the final helper
shape and lands the decision-log entries.

**Maintainer-facing.** Assumes familiarity with the `main()`/`mainUI()` split and the
static/singleton taxonomy ([M1D_static_taxonomy](../migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy)
+ migrator reference [`static-fields.md`](../guides/1-swing-to-emulators/static-fields.md)),
[D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle)
(session-destroy → `WINDOW_CLOSING` + tab-close detector), and the session-scoping of
SB-Emulators' own runtime ([D_callswing_loom](../emulators/decisions.md#D_callswing_loom)
loom executor, [D_session_scoped_pools](../emulators/decisions.md#D_session_scoped_pools)
Timer/SwingWorker pools).

---

## 1. The scenario & the gap

A Swing app holds a stateful backend connection (CORBA / JMS / a DB session / a raw TCP
socket) opened at startup and torn down when the app quits. The migration guide already
answers where such a service is *constructed* (M1D_static_taxonomy + the `main()`/`mainUI()` split) and
how to handle *exit initiation* (`System.exit` / EXIT_ON_CLOSE → session-close). It says
**nothing about teardown** — `addShutdownHook` appears nowhere in `migration/`.

This matters because teardown, unlike `System.exit`, cannot be made transparent: `Runtime`
is not in the Component hierarchy ([D_whitelist_porting](../emulators/decisions.md)) and is a `java.lang`
class we can't shadow, so a migrator's `Runtime.getRuntime().addShutdownHook(...)` survives
the import swap **as a real JVM shutdown hook** — a mandatory hand-edit, exactly like
`System.exit`.

## 2. Where teardown lives in real Swing apps

The *construction* point and *teardown* point are usually different code. Construction is
covered by M1D_static_taxonomy; the teardown shapes we must handle:

- **A) `WindowListener.windowClosing` / `windowClosed` on the main frame** — the classic.
  Often paired with a confirm dialog + `System.exit`.
- **B) JVM shutdown hook** — `Runtime.getRuntime().addShutdownHook(new Thread(svc::disconnect))`.
  Disproportionately common in exactly these apps: CORBA/JMS/DB-pool code cares about clean
  teardown, and a hook catches SIGTERM/Ctrl-C paths that `windowClosing` misses.
- **C) Explicit Quit/Exit menu action** — `svc.disconnect(); System.exit(0);`.
- **D) Container shutdown** — Spring `registerShutdownHook()` + `@PreDestroy` /
  `DisposableBean`, Guice, PicoContainer. Bottoms out in a JVM shutdown hook. **Out of
  scope** — a Swing app already built on Spring is unsupported for now
  ([M1D_spring_bootloader_only](../migration/1-swing-to-emulators/decisions.md#M1D_spring_bootloader_only));
  a per-tab context closed on tab close is the candidate remedy, in
  [`spring-context-per-tab.md`](./spring-context-per-tab.md).
- **E) `finalize()`** — legacy, effectively dead; WARN or ignore.

## 3. What SB-Emulators already handles

- **Construction placement** — M1D_static_taxonomy + `main()`/`mainUI()` split: per-user service →
  construct in `mainUI()`; genuinely-global → `main()`.
- **Exit initiation** — `System.exit` / EXIT_ON_CLOSE → session-close (D_close_operation_dispatch), with the
  no-JVM-exit ArchUnit guardrail.
- **Teardown shape A works almost for free** — D_shutdown_lifecycle fires `WINDOW_CLOSING` on
  session-destroy, so a migrated `windowClosing`/`windowClosed` disconnect handler *already
  runs* on the way out, **provided the disconnect is pure-backend** (no live UI needed).
- **The teardown seam already exists internally** — SB-Emulators' own per-session machinery (loom
  executor D_callswing_loom, Timer/SwingWorker pools D_session_scoped_pools, `SessionTempFiles`) all tear down via
  `service.addSessionDestroyListener(...)`. That is the proven precedent the migrator's
  teardown should reuse.

**The gap is shutdown hooks (B/D) and `finalize` (E).** Left unedited, a hook is dangerous
in proportion to where it was registered:

- **Registered from `mainUI()` / per-session code** (the common case — the service is
  per-user): a *new JVM shutdown hook every tab open*, none firing until server stop.
  Unbounded accumulation; each hook pins a dead session's service so the session never GCs;
  at server shutdown they all fire at once against long-dead state. The sharp bug.
- **Registered from `main()` for a process-global resource** (a shared pool): *correct
  as-is* — fires at server shutdown, matching the resource's process-global lifetime.

## 4. The mapping

**JVM shutdown hook → session-destroy listener, scope-triaged like M1D_static_taxonomy.** This is an
extension of the M1D_static_taxonomy taxonomy to the *teardown* axis — same two scopes, applied to
disposal instead of storage:

| Shutdown hook releases… | Maps to | Action |
|---|---|---|
| a **per-user** resource (this user's TCP/CORBA connection, backend session, license seat) | **session-destroy** | rewrite to session-scoped teardown (the helper below) |
| a **process-global** resource (shared pool, JVM daemon, lock file) | **JVM shutdown hook** | keep as-is (correct; fires at server stop) |

Session-destroy is the right target: it fires on *every* "this user's app is gone" path —
tab close (`AppTab.onTabClosed` → `session.close()`), EXIT_ON_CLOSE dispose (D_close_operation_dispatch), explicit
logout, **and session idle-timeout**. The timeout path is a *bonus over Swing*: an app that
leaked its connection on `kill -9` now gets clean teardown on timeout.

### The helper (decided this session)

Rather than make every migrator hand-roll a `SessionDestroyListener` + resolve the current
session — a prime "wiring you can't grep for," and exactly the kind of thing the aspirational
local-LLM stage-2 target wants lifted off the agent — provide a one-liner:

```java
// Swing:
Runtime.getRuntime().addShutdownHook(new Thread(svc::disconnect));
// SB-Emulators — called from mainUI() or a frame ctor; resolves the current session,
//       registers a self-removing session-destroy teardown:
AppLifecycle.onAppExit(svc::disconnect);
```

- **Home:** `vaadinx.swing.app.AppLifecycle` (decided — `EHelper` is too low-level for a
  migrator-facing surface; `AppLifecycle` sits with the other migrator-facing app wiring).
- **Impl:** callbacks ride the session-scoped **shutdown coordinator** (§7), not a
  standalone listener — so they run under `markShuttingDown`, in a defined order after the
  frame's `WINDOW_CLOSING`, and fire on *every* session-destroy path. Runs once per session.
- **Global case:** left as bare `Runtime.getRuntime().addShutdownHook(...)` for now (decided)
  — no `AppLifecycle.onServerExit(...)` sugar until a real case asks for it.

### Detection

Add an `addShutdownHook` grep to the hazard-scan alongside the existing `System.exit`
guardrail, routing each hit through the scope triage above. An ArchUnit-style flag is a
maybe (open `Q_runtime_detection`).

## 5. Caveats to bake into the guide

1. **Teardown is eventually-correct, not prompt.** Per
   [D_shutdown_lifecycle](../emulators/decisions.md)'s prompt-shutdown latency limitation, a plain tab-close now fires
   `WINDOW_CLOSING` promptly (~60 s, via the tab-scope reaper + beacon handler), but the
   *`VaadinSession`-destroy* that backend teardown hooks fire on still defers to the container
   HTTP-session timeout — `session.close()` from the request-less reaper thread only sets state
   `CLOSING`, and `fireSessionDestroy` runs only at `requestEnd`. A backend expecting a *prompt* drop
   (seat/lock/license) will see the connection linger minutes. Accepted for now (open `Q_teardown_latency`).
2. **No live UI at teardown.** The disconnect must be pure-backend — no `callSwing`, no
   component access. Fine for "close the socket," a trap for anything touching Swing.
3. **Don't disconnect a static singleton's shared connection per-session.** One static
   service + one hook in Swing becomes N connections (one per session) but a hook still
   referencing the static — the static-singleton hazard (M1D_static_taxonomy) again. Teardown must operate
   on the session's *own* instance.

## 6. Decisions taken this session

- **`Q_helper`:** `AppLifecycle.onAppExit(Runnable)` in `vaadinx.swing.app`. Global case
  stays bare `addShutdownHook`.
- **`Q_di_containers`:** out of scope. `Q_scope_mapping` was answered 2026-09-23
  ([M1D_spring_bootloader_only](../migration/1-swing-to-emulators/decisions.md#M1D_spring_bootloader_only)):
  no `vaadin-spring` scope stands in for the tab-scoped holder, Spring Boot is a bootloader only, and
  a Swing app already built on Spring is unsupported for now. The earlier hope here —
  `@VaadinSessionScope` + `@PreDestroy` as the DI-context-per-session — went with it: session is
  the wrong boundary and has no tab-close teardown. The teardown story that would bring DI apps back
  into scope is a per-tab context closed from the tab-scope destroy listener, researched in
  [`spring-context-per-tab.md`](./spring-context-per-tab.md) (`Q_teardown`).

  **What is settled, 2026-09-22: it is out of scope without being a stop.** The provisional gate
  below — detect Spring, declare the migration unsupported, halt the porting agent — **is
  withdrawn, unbuilt.** Detecting a DI container now presents the **bootstrap chooser's
  Spring rows** ([M1D_bootstrap_choice](../migration/1-swing-to-emulators/decisions.md#M1D_bootstrap_choice)):
  the version delta is stated, and the migrator picks Spring Boot after a Spring upgrade or Vaadin
  Boot with their Spring left alone. Out-of-scope teardown means *we do not wire your
  container's lifecycle for you*; it never meant *we refuse to run*. **2026-09-23:** "unsupported"
  is back, for a new reason (per-user state in singleton beans, invisible to every gate) — but
  still not a halt: the migration runs, it just carries no support promise.
- **`Q_teardown_ordering` — execution environment & ordering:** resolved. `onAppExit` callbacks are
  **backend-only leaves** riding a generalized D_shutdown_lifecycle **shutdown coordinator**; the full design
  is §7 below.

## 7. Teardown execution contract & the shutdown coordinator (`Q_teardown_ordering` resolved)

### The execution environment (from the D_shutdown_lifecycle code)

A teardown callback runs in SB-Emulators' existing session-destroy dispatch:

- **Thread & lock:** the session-destroy thread, synchronously, **under the session lock**
  (Vaadin holds it during `SessionDestroyEvent`). Not a UI/EDT thread.
- **No live UI:** `UI.getCurrent()` is null; `EHelper.singleLiveUI` resolves to null (the
  app's tab scope is being/already destroyed).
- **`isShuttingDown()` is already true.** D_shutdown_lifecycle sets `markShuttingDown` at the *top* of the
  dispatch precisely so the browser-round-trip Swing seams — modal-dialog park,
  `SwingWorker.get()`, `invokeAndWait` — throw an actionable "shutting down"
  `IllegalStateException` instead of hanging on a browser that's gone. **The `Q_teardown_ordering` guardrail is
  already built.**
- **SB-Emulators' own pools tear down via separate, lazy, self-removing listeners** (loom executor
  `close()`, `UiScheduler`/`SwingWorker` `shutdownNow()`, `SessionTempFiles`) — registered on
  first use, so their order relative to any independent listener is **nondeterministic**.

### The callback contract: a backend-only leaf

`onAppExit(Runnable)` (and the existing `WINDOW_CLOSING` handlers) are **leaves** — same
spirit as JFrame's documented "cleanup only, no dialogs" `WINDOW_CLOSING` contract:

- **May:** touch its own resource, call the backend, save/release/log, do blocking I/O.
- **May not:** touch Swing components, use `callSwing` / `invokeLater` / `invokeAndWait`, rely
  on a Swing `Timer` / `SwingWorker` still firing, or assume a live UI.

Because the callback is a leaf, the **nondeterministic ordering vs SB-Emulators' internal pools does
not matter** — the callback never touches them, so we deliberately do *not* define a total
order over all session-destroy work. This is `Q_teardown_ordering`'s real answer: not "warn that the EDT is gone"
but "the shutting-down flag already throws on the round-trip seams, and the leaf contract
covers the rest."

### The mechanism: generalize D_shutdown_lifecycle into a session-scoped shutdown coordinator (decided)

Today the D_shutdown_lifecycle dispatch is installed only when a `@MainWindow` frame is built
(`JFrame.registerForShutdown`) and does one thing (fire `WINDOW_CLOSING`). Generalize it into a
session-scoped **shutdown coordinator** that any of these feed, installed on-demand by
whichever fires first:

- `JFrame.registerForShutdown` registers the `WINDOW_CLOSING` step (as today).
- `AppLifecycle.onAppExit(Runnable)` appends a teardown callback.

On session-destroy the coordinator runs, under `markShuttingDown`, in **two phases**:

1. **Maybe-fire `WINDOW_CLOSING`** — *visibility-gated*, keeping D_shutdown_lifecycle's existing dedup (skip if
   the main window already went invisible via a programmatic `dispose()` / Quit menu that
   already ran `WINDOW_CLOSED`).
2. **Always-drain `onAppExit` callbacks** — **outside** the visibility gate, because the
   Quit-menu path is *exactly* when the service must disconnect. Fires on every
   session-destroy path (tab-close detector, EXIT_ON_CLOSE dispose, logout, idle timeout).

Order within the coordinator: `markShuttingDown` → frame `WINDOW_CLOSING` (frame-level) →
`onAppExit` callbacks (app/service-level). Frame-closes-then-app-releases matches the Swing
mental model. This makes `onAppExit` **self-sufficient** — it works with or without a
`@MainWindow` frame, and regardless of call-site timing relative to frame construction.

**Robustness:**

- **Per-callback catch-log-WARN-continue.** A throwing disconnect must not abort the rest of
  teardown (other callbacks, SB-Emulators' pool cleanup). No `ErrorHandler` routing — there's no UI;
  just log.
- **Blocking is the migrator's problem (document-only, decided).** A hanging CORBA/TCP
  disconnect holds the session lock and the destroy thread and can wedge Vaadin's session
  cleanup. For now we only **document** "keep teardown short; use your own bounded timeout."
  A soft-timeout wrapper was considered and deferred (revisit if it bites — server-side twin
  of `Q_teardown_latency`). Fire-and-forget on a scratch thread is rejected: it loses the lock/ordering
  guarantees and races a half-torn-down world.

## 8. Open questions for the design pass

- **`Q_runtime_detection`.** Runtime detection of the dangerous per-tab
  `addShutdownHook`-in-`mainUI` pattern (WARN on the Nth registration), or is grep + guide
  enough? *Open.*
- **`Q_teardown_latency`.** Prompt-teardown latency (caveat 1) — is ~300 s lingering acceptable
  for seat/license backends, or does it argue for wiring the unload beacon more aggressively?
  See [D_shutdown_lifecycle](../emulators/decisions.md)'s prompt-shutdown latency limitation. *Accepted for now;
  revisit if it bites.*

## 9. Next steps (when this graduates)

- **Route DI usage to the consent fork — *not* a hard stop.** (Rewritten 2026-09-22; the previous
  version of this step made a DI hit an unsupported hard-stop that halted the porting agent. It was
  never built — no `springframework` row exists in
  `migration-tool/src/main/resources/META-INF/emul/hazards.tsv`, no §7 bullet in
  [`spec.md`](../migration/1-swing-to-emulators/spec.md), nothing in the guide — so this is a
  proposal withdrawn rather than a shipped gate retracted. Keep the paragraph: the grep list is
  reusable and the reversal is worth not re-deriving.)

  The grep list stands (`org.springframework.*`, `@Autowired` / `@Component` / `@Service` /
  `@Bean` / `@PreDestroy` / `DisposableBean`, `ApplicationContext`, `com.google.inject.*` /
  `@Inject` / Guice `Module`, PicoContainer, CDI `@ApplicationScoped`, …). What changes is the
  **verdict**: a hit prints the chooser's Spring advice (`build-wiring.md` § Which bootstrap?) and
  "unsupported", and the migration continues either way.

  Mechanically this is **one `H_` row in `hazards.tsv` plus its `<a id="H_…"></a>` bullet in
  spec.md §7**, which `HazardTableTest` joins in both directions so neither can be edited alone. It
  has a real scan signal (the imports), so it is a scanned row rather than one of the `manual`
  closing-checklist rows. **Phase 1 is the right home and not only by convenience** — it is one step after the
  bootstrap choice, which M1D_bootstrap_choice put at Phase 0 so it lands well ahead of Phase 3; a
  hit that contradicts the lane already chosen can say so while the choice is still cheap to redo.
- **Generalize D_shutdown_lifecycle into the session-scoped shutdown coordinator** (§7): lift the
  once-per-session dispatch out of `JFrame.registerForShutdown` into a coordinator with a
  callback list, install-on-demand, two-phase drain (visibility-gated `WINDOW_CLOSING`, then
  unconditional `onAppExit`), per-callback catch-log-continue. Keep D_shutdown_lifecycle's dedup and
  `markShuttingDown` semantics intact. When landed, fold the generalization back into D_shutdown_lifecycle's
  decision-log entry.
- Land `AppLifecycle.onAppExit(Runnable)` in `:emulators` (`vaadinx.swing.app`) on top of the
  coordinator, tested via Karibu session-destroy (fires with and without `@MainWindow`; fires
  on the dispose/Quit path; catch-continue on a throwing callback).
- Extend M1D_static_taxonomy (or a sibling `M1D*`) with the teardown axis; add a guide hazard section +
  `addShutdownHook` grep to the hazard-scan.
- Resolve `Q_runtime_detection` and `Q_teardown_latency`.
