# The shape of the peer seam — what the queue left open

**Status:** open. The seam's mechanism is decided and built: every emulator peer write goes through
`withPeer` into a per-emulator queue, and every peer is built lazily
([D_lazy_peers](../emulators/decisions.md#D_lazy_peers); why a queue, the seam shapes it replaced and
the accepted hop cost: [D_peer_queue_shape](../emulators/decisions.md#D_peer_queue_shape)). Reads
never touch the peer ([D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state)).
This file is what that left open.

**Maintainer-facing.** Read first: [R_tolerate_off_ui_thread](../CLAUDE.md#hard-rules),
[D_lazy_peers](../emulators/decisions.md#D_lazy_peers),
[D_attach_aware_hop](../emulators/decisions.md#D_attach_aware_hop),
[SD_background_model_hop](../surrogates/decisions.md#SD_background_model_hop). Siblings:
[lazy-peer-sweep.md](./lazy-peer-sweep.md) (the conversion worklist, and the docs that still describe
the synchronous seam), [mixed-emulator-vaadin-trees.md](./mixed-emulator-vaadin-trees.md).

**Where things stand.** Three hop seams exist:

| seam | target | used for |
|---|---|---|
| `withPeer(Consumer)` → `PeerWriteQueue` | inline with a UI current; a synchronous hop onto the peer's owner UI when attached and open; otherwise queued until attach | every emulator peer write (`withPeerOnLiveUI` is a synonym) |
| `EHelper.runInUIThread(boolean, Runnable)` | the session's live UI, via `EmulatorContext`; throws without a context (case (7)) | window shows (`Window.applyVisibleToPeer`) |
| `SHelper.runOnOwnerUI` directly, surrogate-side | the peer's owner UI | only inside a Swing-model callback, marked and gated by `ModelCallbackHopMarkerTest` (SD_background_model_hop) |

The gates are `vaadinx.OffUiThreadWriteTest` (a write from a worker hops), `vaadinx.LazyPeerTest` and
`vaadinx.PeersOnUIThreadGate` (no peer is built off the UI thread).

---

## 1. The `getPeer()` contract — `Q_getpeer_contract`

**Direction agreed 2026-10-01, not built:** `withPeer(Consumer)`, returning `void`, is the only
public access to a peer, and `getPeer()` leaves the migrator's surface. Nothing comes back out of a
body, so the pure-sink rule a queued write must obey is in the type. What that needs:

- **An internal accessor for structure** (`parentPeer.add(childPeer)` needs the child's peer inside
  the parent's body), legal only during a hop or a drain, asserting the lock and a current UI. That
  assertion becomes R_tolerate_off_ui_thread limb 2's narrow waist in place of `checkUIThread()`;
  keep the one-time hygiene WARN, since the Swing-side state is still the migrator's own race.
- **An attaching-write API for embedding** an emulator in vanilla Vaadin, e.g.
  `EHelper.addTo(HasComponents, Component)` (lock, drain, add): `emulator.withPeer(p ->
  vaadinLayout.add(p))` on a detached emulator waits for an attach only the body itself would cause,
  and so never runs, silently. The host app's route mount is the same seam. See
  [mixed-emulator-vaadin-trees.md](./mixed-emulator-vaadin-trees.md) §1.
- **Sampler's one public use** (`TabbedPanesPanel`'s theme variant on a tab-close button) is a pure
  sink and converts as is.
- **Tests.** `:emulators`' tests call `getPeer()` ~460 times in 94 files, and the add-ons' and
  Sampler's tests about 15 more. Either a test-only accessor that drains and returns the peer
  (`EmulatorTesting.peerOf(c)`), or `getPeer()` kept with that contract — drain, assert the lock and
  a current UI, throw otherwise — and documented as for tests. Under MockVaadin a UI is current, so
  either drains inline and leaves the corpus's bare-peer attach shape working. Undecided, and a
  naming question rather than a design one.
- **Docs that change with it:** R_no_vaadin_in_api names `getPeer` as the sanctioned peer accessor;
  R_tolerate_off_ui_thread limb 2 names it as the chokepoint.

## 2. Two copies of scalar state — `Q_state_owner`'s residue

D_emulator_owned_state makes the emulator own its state, and where a JDK model is shared the
surrogate reads the same model. But a surrogate still keeps its own copy of the *scalar* state for
stage-3 code, so the two layers can drift. Open: whether that matters, and what keeps them aligned.

## 3. The leak taxonomy — `Q_acceptable_leaks`

What the hop still lets through, and which of it we accept:

- **(a) User code inside a peer write.** A push makes a surrogate fire listeners that the emulator
  relays to user code, inside the hop — on the worker, with the lock held and a UI current but
  outside a UI fiber, so a modal opened there takes `Dialog`'s UI-thread park path and throws. On a
  *queued* write it is worse: the drain runs it, which breaks the pure-sink invariant, and the drain
  assertion catches it only when the path reaches `EHelper.callSwing`. Sites still in this shape:
  - `JInternalFrame`: `setSelected` → ACTIVATED / DEACTIVATED, `dispose` → CLOSED, `setVisible` →
    OPENED, all fired by the surrogate and relayed by `installPeerRelay`;
  - `JTree.startEditingAtPath`, which calls the surrogate's `setSelectionPath` inside its write.

  SD_background_model_hop accepted the model-fan-out version of this on purpose ("the whole fan-out
  hops"), with `relayModelEvent` making the emulator's listeners safe. The question is whether that
  generalises, or whether each of these moves its notification Swing-side, as the programmatic
  setters did under D_emulator_owned_state.
- **(b) A monitor held while waiting for the session lock.** D_emulator_owned_state's trap settles
  the rule (write under the monitor, flush after it is released), and `Label`, `List`, `Choice` and
  `Scrollbar` follow it. The one site left is `Checkbox.setCheckboxGroup`, which calls `withPeer`
  inside `synchronized (this)`.
- **(e) Accessor escape.** Nothing stops a new method calling `surrogate().setX(...)` outside a
  write; only the reflective gate catches it, and only for sampleable setters. §4's structural gate
  would close it.
- **(g) Seams outside `Component`.** `KeyboardFocusManager.clearFocusOwner`'s
  `FocusTracker.setFocusOwner(null)` is not a `Component` write and is unwrapped.
- **(h) User listeners on a model the surrogate shares (found 2026-09-29, not measured).** `JSlider`,
  `JProgressBar`, `JSpinner`, `JList`, `JComboBox` and `JTree` share a JDK model with their
  surrogate, which writes a browser change into it through its inline `SHelper.callSwing`. The
  emulator's own listener relays, so `slider.addChangeListener` runs in a UI fiber; a listener the
  migrator put on the model itself (`slider.getModel().addChangeListener`) does not, and a modal
  opened there cannot park. `JColorChooser` and `JTabbedPane` keep a model of their own and are not
  exposed. The fix is their shape per component (D_emulator_owned_state's first trap), or a
  surrogate-side `callSwing` that starts a fiber, which SD_sframe rules out. First measure how common
  model-level listeners are in the testapps.

## 4. The structural gate — `Q_gate_shape`

`OffUiThreadWriteTest` measures behaviour: attach, call from a worker, catch the assertion. What it
cannot reach:
- windows (showing one poisons the next attach);
- setters whose argument type has no sample;
- two-argument setters;
- direct model mutation (`model.addRow` from a worker), which only the per-component
  `*BackgroundModelTest` pairs catch.

It also has to run a fresh Karibu UI per type, because an unlocked write corrupts the state tree it
lands in, and the corruption surfaces at some later, unrelated attach.

**The structural gate, not built.** A build-time check that an emulator getter never calls
`getPeer()` / `surrogate()`, and that `getPeer()` is called only inside a `withPeer` body, would be
allow-list-free, would close §3 (e), and would replace both `vaadinx.EmulatorOwnedStateTest`'s
per-component list and the source scan D_emulator_owned_state rests on. `:emulators` compiles at
release 21, so `java.lang.classfile` is not available to its tests; ArchUnit (already in
`:migration-tool`) or a test-scope ASM would be. Open: which methods count as getters, and how a
lambda passed to `withPeer` is told apart from a direct call. The two gates are complementary: the
structural one proves the call goes through the seam, the behavioural one proves the seam hops.

## 5. `Q_drop_log_level`

`runInUIThread` logs its no-live-UI drop at DEBUG, and D_sync_ui_hop calls that intended.
R_tolerate_off_ui_thread limb 3 says WARN, and the sibling branch (the session gone while waiting for
the lock) does WARN.
- For DEBUG: every ordinary tab close hits it.
- For WARN: otherwise the migrator never learns an update was discarded.

Peer writes queue rather than drop, so this now concerns only a non-modal window show. Decide, then
align the rule or the entry.

## 6. A support escape hatch

A configurable direct / sync / enqueue strategy stays parked (D_sync_ui_hop). What survives is a
support escape hatch: one system-property `if` in the seam, for a customer hitting a hang. Build it
when someone needs it.

## 7. Acceptance

On `testapps/inventory` (`1-emulators/` keeps upstream's Save bodies in `doInBackground()` as a
testbed), Item Entry → New → Save reaches `Validator.validate()`, which prints
`validating ... <errorCount>`. Transfer → Save and Return → Save do their saves. **Item Entry
observed 2026-09-28**: Save paints the six empty required fields pink and leaves the preselected
purchase date alone, shows the validator's warning from the worker, which blocks until OK, and then
prints `validating ... 6`. Transfer and Return are not yet observed, and none of it has been
re-observed since the peers went lazy.

## Graduation

Graduate once §1–§4 are decided and §7 is observed. The answers go to D_lazy_peers /
D_peer_queue_shape (the seam shape, the gate) and to R_tolerate_off_ui_thread (anything the leak
taxonomy makes a rule); then delete this file.
