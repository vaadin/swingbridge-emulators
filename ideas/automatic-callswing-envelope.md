# Automatic `callSwing` envelope around every client RPC

**Status:** brainstorm, opened 2026-10-02. Nothing decided, nothing built.

**Maintainer-facing.** Read first: [R_callswing_envelope](../CLAUDE.md#hard-rules),
[R_swing_is_truth](../CLAUDE.md#hard-rules) (the "read the guard before `callSwing`" limb),
`D_callswing_funnel`, `D_callswing_loom`, `D_library_servlet`.

## The idea

Today every peer listener whose job is to fire a Swing event must remember to wrap its body in
`EHelper.callSwing`. R_callswing_envelope calls 100% coverage "the contract, not an optimisation":
one forgotten wrapper is one button whose `dialog.show()` throws. That coverage rests on discipline:
184 call sites in 50 files of `emulators/src/main`, plus `relayModelEvent` bridges.

Instead, SB-Emulators could install its own `UidlRequestHandler` whose `ServerRpcHandler` runs each
client→server invocation inside `callSwing`. Every DOM event, property sync, `@ClientCallable`,
return-channel result and `ui-navigate` would then arrive already inside a UI fiber. The explicit
call sites would become re-entrant no-ops, since nested `callSwing` runs inline when
`UIFibers.isInUIFiber()`.

**The install site already exists:** `SwingBridgeVaadinServlet`'s service, and its Spring twin
`SwingBridgeSpringServlet` (`D_library_servlet`). A migrator declares neither, so nothing new lands in
their source, and R_no_spi_selfregister is not touched.

## The hook, as measured in Flow 25.3.0 bytecode

- `UidlRequestHandler.createRpcHandler()` is `protected`. It is the seam vaadin-tab-scope already uses.
- `ServerRpcHandler.getInvocationHandlers()` is `protected` and returns the
  `Map<String, RpcInvocationHandler>`. **Decorating each handler is the clean cut.** The alternative,
  overriding `handleRpc`, would wrap a whole request.
- `RpcInvocationHandler.handle(UI, JsonNode)` returns `Optional<Runnable>`. `handleInvocations` makes
  two passes. First it applies **every** property sync and collects the runnables they return, which
  fire value-change listeners. Then it runs those runnables. Then it calls `handleInvocationData` for
  each non-sync invocation, inside `observeInvocation`, which routes exceptions to `callErrorHandler`.
  A decorator therefore wraps two things: the returned `Runnable`, and the `handle` call of the
  non-sync types. The sync apply itself is a pure state write and can stay on the request thread.
- **Granularity must be per invocation, never per request.** A request routinely carries several
  invocations, such as a value sync plus a click. If the whole `handleRpc` were one fiber, a modal
  parked in invocation 1 would hold invocations 2…n until the dialog closed. Swing does not do that:
  a modal pumps a secondary event loop, and later events keep flowing. Per-invocation wrapping
  behaves like today: invocation 1 parks, `runUntilPark` returns, and invocation 2 proceeds.
- **Per invocation is *more* Swing-faithful than today's per listener.** All the Vaadin listeners of
  one DOM event share one fiber, so a modal in the first one holds back the second, which is what
  the EDT does to two listeners on one event. Today each listener spawns its own fiber, so the
  second one runs while the first is parked.

## Answer to "are there places where we do *not* want `callSwing`?"

**For RPC-originated code, no hard "must not" turned up.** A sweep of `emulators/`,
`emulators-printing/`, `surrogates/` and `third-party/` found that every deliberate skip either
stays harmless inside a fiber or does not originate from an RPC at all:

- **Echo guards.** 8 listeners check `preventPeerEvents` before entering `callSwing`, 1 checks
  `peerSelectionMuted`, and 7 check `!isFromClient()`. Inside a fiber they still drop the echo. The
  reason they must read the flag first is that `callSwing` throws when no UI is current. On an RPC
  path a UI is always current, so that reason disappears there. It stays on server-originated paths.
- **Round-trip results** (`PrefsCache:188`, `WebClipboard:223`, `BrowserToolkitInfo:253`). These
  only complete a plain `CompletableFuture`. Inside a fiber, the woken fiber resumes as an access
  task, and the envelope's own `runPendingAccessTasks` drains it. A modal's OK click already works
  this way.
- **Bookkeeping that is not RPC-originated:** the attach drain in `PeerWriteQueue`, the
  `beforeClientResponse` callbacks (`FieldReconciler`, caret flush, `JTable` editor reopen), and
  the UI-init listener. The wrapper never reaches them.
- **Teardown** (`AppTab.onTabClosed` → `dispatchShutdownClosing`) is deliberately inline. It runs on
  the tab-scope reaper thread, in session destroy, or in the request-driven sweep. The unload beacon
  is `ServerRpcHandler.handleUnloadBeaconRequest`, not an invocation, so per-invocation wrapping
  skips it on its own.

**The soft concerns are real, though. None of them is a must-not, but each needs an answer:**

- `Q_framework_frames`: **Vaadin's own frames would sit on the parked fiber's stack.** Today the
  fiber stack holds only Swing-side code: the listener returns to Flow at once and Flow finishes its
  dispatch. Wrapped at the invocation level, a park freezes `EventRpcHandler` →
  `ElementListenerMap` → `ComponentEventBus` → … in the middle of dispatch, and they resume in a
  later access task. The worst case is `ui-navigate` (see below): a park inside
  `MainWindowRoute.onAttach` → `bootstrap()` freezes `Router` mid-navigation and mid-attach-cascade,
  while other requests carry on. Before relying on this, verify that Flow tolerates a half-finished
  invocation resumed later: navigation-in-progress state, attach event ordering, and
  `InvocationEvents.ended`.
- `Q_non_emulator_views`: **the wrapper would also catch views that never asked for it.** Stage-3
  surrogate views coexist with emulator views in one app, and so do plain Vaadin views and
  third-party add-ons. All of them would run their listeners on a virtual thread. Vaadin's
  `CurrentInstance`s propagate, but nothing else does. Spring Security's `SecurityContextHolder`
  (`MODE_THREADLOCAL` by default), `RequestContextHolder`, the MDC and thread-bound transactions
  would all go missing. Today only emulator listeners pay that cost. Options:
  - wrap only invocations whose target node resolves to a component with an emulator
    (`EHelper.getEmulator`) on its ancestor chain; navigation has no target node, so it needs its
    own rule;
  - wrap only on a UI marked by `AppTab`;
  - accept the cost and document it.
- `Q_epilogue_cadence`: `callSwing`'s epilogue (`runPendingAccessTasks`,
  `FieldReconciler.reconcileAll`, `AutoShutdown.checkAppOver`) would run once per invocation, where
  today it runs once per outermost listener envelope. That is probably closer to an EDT event
  boundary, but it is a behaviour change.

## Gaps the wrapper would close for free

The sweep found code that reaches user Swing listeners from a browser event with **no fiber
today**. It also found one that has since been fixed, and that one is the evidence for the
discipline argument:

- **`JInternalFrame`** had zero `callSwing` calls. A `JOptionPane` in `internalFrameClosing` (close-X
  or ESC), or in a listener reached from the header's minimize or maximize, threw from
  `Dialog.parkUntilClose`'s `checkInUIFiber`. Its three peer→Swing entry points are now wrapped and
  gated by `JInternalFrameTest`'s `*ClickListenerCanPark` tests. The gap went unnoticed because the
  callbacks arrive through surrogate hooks (`SInternalFrameListener`, `setIconifyHandler`) rather
  than a Vaadin listener, which is exactly the shape a per-listener rule misses.
- **Sampler's route** (`SamplerRoute:42`) runs `new SamplerFrame().setVisible(true)` on the request
  thread. `ui-navigate` is a UIDL event RPC, so a wrapped navigation would put `bootstrap()` in a
  fiber. A `main()` that shows a login `JOptionPane` before its frame would then work without
  `invokeLater`, but see `Q_framework_frames`.
- **Surrogate listeners that fire S-events without `SHelper.callSwing`** (inline anyway):
  `ComponentMixin` component, focus and hierarchy listeners, `JTextComponentMixin`'s
  `emul-selection` → `fireCaretUpdate`, and `FocusTracker` focusin/focusout → `FocusOwnerListener`.
  Today they reach a fiber only when an emulator relay sits downstream.

## Where the wrapper does not reach (explicit `callSwing` stays)

- **Karibu.** `_click`, `_setValue` and `_fireDomEvent` fire server-side and never pass through
  `ServerRpcHandler`. This is the big one. Tests would run without the envelope, or with a
  different envelope than production, unless one of these happens:
  - every Karibu interaction is wrapped in a test helper;
  - Karibu-Testing grows an event-dispatch interception hook (same author);
  - the explicit call sites stay, which makes the wrapper a production-only safety net and produces
    exactly the prod/test granularity split above.

  Migrators' own Karibu suites (`ideas/migrated-app-testing.md`) inherit the same choice.
  `Q_karibu`.
- **`@Push(transport = Transport.WEBSOCKET)`.** `PushHandler`'s receive callback hardcodes
  `new ServerRpcHandler()`, so client→server messages over the websocket bypass any custom
  `UidlRequestHandler`. The default `WEBSOCKET_XHR` sends client→server over XHR, as does
  `LONG_POLLING`, so the default is safe. A migrator who sets `WEBSOCKET` would lose the envelope
  silently. That needs either a refusal at startup (R_match_swing_errors-case-shaped) or a Flow
  change. `Q_websocket_transport`.
- **Server-originated events:** attach/detach (`Component.java:350-353`), programmatic focus moves,
  Tabs autoselect, opened-change from a server-side `dispose()`, and `relayModelEvent` from
  background threads. These are nested in an RPC only when an RPC caused them.
- **Access tasks**, which are already funnelled centrally: `invokeLater`, `Timer`, `SwingWorker`,
  `BridgedPreferences`, and parked fibers resuming.
- **Stream requests:** the upload success callback (`BrowserFileTransfer:144`) runs the user's
  `FileFilter` / `FilenameFilter` under `UI.access` with no fiber.

So the call sites would shrink from ~184 scattered ones to a handful of central entry points. The
rule would change from "every listener" to "every non-RPC entry point". The second set is small and
closed, which is the property R_match_swing_errors' case list values.

## Collision: vaadin-tab-scope already replaces the UIDL handler

`TabScope.installTabCloseBeacon(handlers)` replaces the entry whose `getClass() ==
UidlRequestHandler.class` (exact match) with `TabScopeUidlRequestHandler` →
`TabScopeServerRpcHandler`, which overrides `handleUnloadBeaconRequest`. Sampler wires it
(`SamplerServlet:53-58`). If SB-Emulators installs first, tab-scope finds no stock handler, logs a
WARN and loses the beacon. If tab-scope installs first, ours replaces nothing. `:emulators` already
depends on tab-scope, so two options are open:
- subclass `TabScopeServerRpcHandler`, which couples the library servlet to the beacon;
- give tab-scope a composable hook upstream (same author).

`Q_tabscope_compose`.

## Open questions

- `Q_framework_frames`: does Flow tolerate a parked invocation resuming in a later access task,
  especially navigation and attach? Measure before anything else.
- `Q_non_emulator_views`: should the wrapper be scoped to emulator-backed targets, and how does
  navigation fit into that scope?
- `Q_karibu`: keep the explicit call sites (wrapper as safety net) or make Karibu go through the
  same envelope?
- `Q_websocket_transport`: refuse `Transport.WEBSOCKET` at startup, or fix upstream?
- `Q_tabscope_compose`: subclass, or add an upstream hook?
- `Q_epilogue_cadence`: is once-per-invocation the right epilogue boundary?
- `Q_rule_rewrite`: if adopted, R_callswing_envelope becomes "every client RPC runs in the envelope;
  the enumerated non-RPC entry points funnel explicitly". The "read the guard before `callSwing`"
  limb of R_swing_is_truth would then apply to server-originated paths only.
