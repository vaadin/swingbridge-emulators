# The shape of `withPeer`, and which leaks we accept

**Status:** open, for a brainstorm. Opened 2026-09-28, right after
[D_attach_aware_hop](../emulators/decisions.md#D_attach_aware_hop) put every emulator peer *write*
through `vaadinx.awt.Component.withPeer(Consumer)`. Everything below is what that sweep, done by three
parallel forks plus a reflective gate, found and left open. Supersedes
`ideas/component-construction-off-ui-thread.md`, whose open threads are §6–§8 here.

**Maintainer-facing.** Read first: [R_tolerate_off_ui_thread](../CLAUDE.md#hard-rules),
[D_sync_ui_hop](../emulators/decisions.md#D_sync_ui_hop),
[D_attach_aware_hop](../emulators/decisions.md#D_attach_aware_hop),
[SD_background_model_hop](../surrogates/decisions.md#SD_background_model_hop).

**Where things stand.** Three hop seams exist:

| seam | target | used for |
|---|---|---|
| `withPeer(Consumer)` → `SHelper.runOnSession` | the component's captured session's lock, then the UI owning the peer's tree; inline when no session is known | every emulator peer write |
| `withPeerOnLiveUI(boolean, Consumer)` → `EHelper.runInUIThread` | the session's live UI, via `EmulatorContext`; throws without a context (case (7)) | a body needing a current UI while detached: icons, overlay shows |
| `SHelper.runOnOwnerUI` directly, surrogate-side | as the first | only inside a Swing-model callback, marked and gated by `ModelCallbackHopMarkerTest` (SD_background_model_hop) |

The gate is `vaadinx.OffUiThreadWriteTest`. **Reads are not wrapped:** getters stop reading the
peer instead ([D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state)).

**The longer-term direction** is [vaadin-ui-thread-only.md](./vaadin-ui-thread-only.md): no Vaadin
component touched off the UI thread at all, with the surrogates as a locked, batched renderer. Its
answers would reshape everything below, so keep the two files consistent as either moves.

---

## 1. Reads — graduated

Decided and done: [D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state).

## 2. One seam or two — `Q_one_seam`

The `withPeer` / `withPeerOnLiveUI` split makes every call site answer "does this body need a UI
while detached?". The forks answered it by reading the body, and several were unsure:

- `FileDialog`'s content build makes a Vaadin `Upload` while the dialog is detached — does `Upload`
  need a UI at construction?
- `JMenuBar` / `JPopupMenu`'s `pushTree` rebuilds the menu, icons included, under plain `withPeer`: a
  detached menu with an `ImageIcon` rebuilt off-thread has no UI for the image.
- `JFrame.setTitle` → `strategy.afterTitleSet`: does `InlineStrategy` read `UI.getCurrent()`?

**Direction agreed 2026-10-01: neither seam survives as it is.** The answer is
[vaadin-ui-thread-only.md](./vaadin-ui-thread-only.md) § "The mechanism": one `withPeer` that runs a
body locked with the peer's UI current or queues it on the component's detached island, drained at
the write that attaches the island. How the brainstorm got there, since each step rules something
out:
- *One seam that falls back* (current UI, else captured session, else the context's live UI, else
  inline unlocked with a one-time WARN) fails on the last leg: `new Image(bytes, …)` resolves its URL
  against `UI.getCurrent()` in its constructor (D_sync_ui_hop's measurement), so an icon body there
  still throws, three frames inside Vaadin. Today's `withPeerOnLiveUI` hides this only because case
  (7) throws first. The same hole exists in `runOnSession`'s `UIDetachedException` branch.
- *Deferring just `setSrc` to attach* (a `LazyImage` in `:emulators`) would fix icons with a
  provable reorder — one exclusive writer of `src`, which nobody but the browser reads — but it is
  per-case reasoning about what Vaadin does inside a body, which is the thing the agreed rule refuses
  to depend on.
- *Classifying "needs a UI" per body* was the problem statement. Every surrogate needs one in the
  ideal state, so the answer is to always give it one.

## 3. The leak taxonomy — `Q_acceptable_leaks`

What the sweep let through or introduced, and which of it we accept:

- **(a) User code under the lock.** A wrapped push makes a surrogate or Vaadin fire listeners
  inside the hop, on the worker, with the lock held and a UI current but outside a UI fiber. A modal
  opened there takes `Dialog`'s UI-thread park path and throws. Sites found:
  - `JInternalFrame`: `setSelected` → ACTIVATED, `dispose` → CLOSED, `setVisible` → OPENED;
  - the `JTable` sorter events inside `tableChanged`;
  - `JTree`'s `startEditingAtPath` → `setSelectionPath`;
  - model swaps that clear the selection (`setModel` on `JList` / `JTree` / `JTabbedPane`).

  SD_background_model_hop accepted the model-fan-out version of this on purpose ("the whole fan-out
  hops"), with `relayModelEvent` making the emulator's listeners safe. The question is whether that
  generalises, or whether each of these wants its relay. Programmatic-setter notifications that moved
  Swing-side with D_emulator_owned_state already fire on the caller's thread before the hop, which
  took those out of this list.
- **(b) A monitor held while waiting for the session lock.** JDK-`synchronized` AWT setters now wait
  on the session lock inside their monitor: `Label.setAlignment`, `List`'s mutators,
  `Scrollbar.setLineIncrement` / `setPageIncrement`, `Checkbox.setCheckboxGroup`. If the UI thread
  enters the same monitor at that moment, the two deadlock. It needs a migrator race on one
  component, but the shape is new. It is the loom rule's "no monitor across a park", one layer down.
  The fix is to move the hop out of the monitor: a `synchronized` block for the state, and the hop
  after it, which the JDK signature allows.
- **(c) I/O under the lock.** `JEditorPane.setPage` is left *unwrapped* because `editor.setPage`
  fetches over the network. It needs splitting: fetch outside, push inside.
- **(d) A body that needs a UI while detached, under plain `withPeer`.** §2's list.
- **(e) Accessor escape.** Nothing stops a new method calling `surrogate().setX(...)` outside a hop;
  only the reflective gate catches it, and only for sampleable setters. §4's structural gate would
  close it.
- **(f) Constructor-time writes and listener registrations**, left unwrapped. They are safe while
  the peer has never been attached (no state tree, no lock), which is true of every constructor. Keep
  that, or wrap them for a uniform rule?
- **(h) User listeners on a model the surrogate shares (found 2026-09-29, not measured).** `JSlider`,
  `JSpinner`, `JList`, `JComboBox` and `JTree` share a JDK model with their surrogate, which writes a
  browser change into it through its inline `SHelper.callSwing`. The emulator's own listener relays,
  so `slider.addChangeListener` runs in a UI fiber; a listener the migrator put on the model itself
  (`slider.getModel().addChangeListener`) does not, and a modal opened there cannot park.
  `JColorChooser` and `JTabbedPane` keep a model of their own and are not exposed. The fix is their
  shape per component (D_emulator_owned_state's first trap), or a surrogate-side `callSwing` that
  starts a fiber, which SD_sframe rules out. First measure how common model-level listeners are in
  the testapps.
- **(g) Seams outside `Component`.** `DndBridge.reconfigure`, `GroupLayout.removeLayoutComponent`
  and `CssEmittingLayoutManager.layoutContainer` hop through the (now public) `withPeer` of the
  component concerned. `KeyboardFocusManager`'s `FocusTracker.setFocusOwner(null)` is not a
  `Component` and is unwrapped.

## 4. The gate — `Q_gate_shape`

`OffUiThreadWriteTest` measures behaviour: attach, call from a worker, catch the assertion. What
it cannot reach:
- windows (showing one poisons the next attach);
- setters whose argument type has no sample;
- two-argument setters;
- direct model mutation (`model.addRow` from a worker), which only the per-component
  `*BackgroundModelTest` pairs catch.

It also had to run a fresh Karibu UI per type, because an unlocked write corrupts the state tree it
lands in, and the corruption surfaces at some later, unrelated attach.

**The structural gate, not built.** A build-time check that an emulator getter never calls
`getPeer()` / `surrogate()`, and that `getPeer()` is called only inside a `withPeer` body, would be
allow-list-free, would close §3 (e), and would replace both `vaadinx.EmulatorOwnedStateTest`'s
per-component list and the source scan D_emulator_owned_state rests on. `:emulators` compiles at
release 21, so `java.lang.classfile` is not available to its tests; ArchUnit (already in
`:migration-tool`) or a test-scope ASM would be. Open: which methods count as getters, and how a
lambda passed to `withPeer` is told apart from a direct call. The two gates are complementary: the
structural one proves the call goes through the seam, the behavioural one proves the seam hops.

## 5. Cost — `Q_hop_cost`

Every off-thread write is one session-lock acquisition, and with automatic Push one `unlock` → push.
A worker building a 30-field form off-thread pushes 30 times. `SwingUtilities.invokeAndWait` works
from a worker since D_invoke_from_background, and is the migrator's batching escape. `runOnSession`
has no slow-hop WARN, where `runInUIThread` has one — add it? Nothing is measured yet.

## 6. `Q_drop_log_level` — carried over

`runInUIThread` logs its no-live-UI drop at DEBUG, and D_sync_ui_hop calls that intended.
R_tolerate_off_ui_thread limb 3 says WARN, and the sibling branch (the session gone while waiting for
the lock) does WARN.
- For DEBUG: every ordinary tab close hits it once per peer write.
- For WARN: otherwise the migrator never learns an update was discarded.

`runOnSession` never drops, so this now concerns only the live-UI seam. Decide, then align the rule or
the entry.

## 7. Strategy knob, and the alternative shapes — carried over

- **A configurable direct / sync / enqueue hop stays parked** (D_sync_ui_hop). What survives is a
  support escape hatch: one system-property `if` in the seam, for a customer hitting a hang. Build it
  when someone needs it.
- **Deferral** is now the agreed direction, as a queue per detached island rather than a flush of
  Swing state ([vaadin-ui-thread-only.md](./vaadin-ui-thread-only.md) § "The mechanism", which says
  why that queue has no execution order to invent).
- **A `data:` URI in `Icons.imageIconToVaadinImage`** instead of a `StreamResource` would take icons
  off the live-UI seam entirely. Moot under the queue, which gives every body a UI; it stays only as a
  caching trade-off (every icon's bytes inlined, uncached).

## 8. Acceptance

On `testapps/inventory` (`1-emulators/` keeps upstream's Save bodies in `doInBackground()` as a
testbed), Item Entry → New → Save reaches `Validator.validate()`, which prints
`validating ... <errorCount>`. Transfer → Save and Return → Save do their saves. **Item Entry
observed 2026-09-28**, after §1's `JDateChooser` pilot. Save paints the six empty required fields
pink and leaves the preselected purchase date alone. It shows the validator's warning from the
worker, which blocks until OK, and then prints `validating ... 6`. Transfer and Return are not yet
observed.

## 9. Graduation

Graduate once §2–§4 are decided and §8 is observed. The answers go to
D_attach_aware_hop (the seam shape, the read policy, the gate) and to R_tolerate_off_ui_thread (anything the leak taxonomy makes a
rule); then delete this file.
