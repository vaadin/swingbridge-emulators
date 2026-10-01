# Vaadin components touched on the UI thread only — surrogates as a locked renderer

**Status:** open, a direction taken, its mechanism agreed in a brainstorm on 2026-10-01 (§
"The mechanism") but not built. Opened 2026-09-28. The interim design, decided the same day, is
*eager peers built on any thread, and a session capture per component to hop through*. This file is
how SB-Emulators gets from there to **zero Vaadin exposure to background threads**.

**The rule this aims at, in one sentence:** a surrogate's UI state is touched only with the session
locked and its UI current, no exceptions — models may be mutated from a background thread, but their
surrogate listeners hop before touching UI state (SD_background_model_hop). No call site is classified
by reading what Vaadin does inside it, because what Vaadin does changes release by release.

**Maintainer-facing.** Read first: [R_tolerate_off_ui_thread](../CLAUDE.md#hard-rules),
[D_sync_ui_hop](../emulators/decisions.md#D_sync_ui_hop),
[D_attach_aware_hop](../emulators/decisions.md#D_attach_aware_hop),
[D_emulator_surrogate_split](../emulators/decisions.md#D_emulator_surrogate_split),
[D_surrogate_first](../emulators/decisions.md#D_surrogate_first). Sibling:
[withpeer-shape.md](./withpeer-shape.md), whose seam this would reshape.

## The goal, and why

**Treat Vaadin components as a thread-unsafe rendering layer, touched only with the UI locked.**
Today a migrator's background thread constructs Vaadin components, and mutates the never-attached
ones, directly. That is sound only because a never-attached Flow node has no state tree and no lock
check. That is an inference from how Flow happens to be built today, not a contract Vaadin documents.
A later Flow release could add a thread check, lazy initialisation that reaches for `UI.getCurrent()`,
or a shared cache, and it would break exactly here. The failure would be intermittent, silent in
production (assertions off), and far from its cause, which is the worst failure class this project
has. The user's framing: surrogates are the emulators' *renderer*. A renderer may render in batches
and late, and the emulators may grow the fat they need to answer without it.

Known Vaadin exposure off the UI thread today, all of which must reach zero:
- **Surrogate construction on a worker.** Emulators build their peer eagerly in their constructor,
  on the migrator's thread; inventory's `Validator` builds `JDialog`s in `doInBackground()`.
- **Writes to never-attached peers.** `withPeer` runs them inline.
- ~~**Peer reads.**~~ Gone: no emulator getter reads the peer
  ([D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state)).
- **The hop's own attach check** read `StateNode.owner` unlocked (and `getUI()` is worse:
  `Composite.getElement()` lazily runs `initContent()`). Fixed by the interim session capture, which
  reads the node only under the lock. Still unlocked: the session capture itself registers an attach
  listener on a never-attached peer, off the UI thread.

## What stands in the way — each must be solved, not waived

These are the objections raised on 2026-09-28 against deferring peer construction. The direction is
kept regardless; each one is a design problem to solve.

1. **The surrogate is the state holder, not only the renderer.** R_vaadin_first / D_surrogate_first
   put the `ButtonModel`, `Document`, selection models, caret dot and mark, the `JComboBox` item
   list, the `JTable` sorter wiring and more in the surrogate or its mixin store. With no peer yet,
   getters have nothing to read, so the emulator must own that state itself. **`Q_state_owner` —
   answered** by [D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state): every
   emulator owns its state, the JDK's models and fields under the JDK's names, and no getter reads
   the peer. The Vaadin-free core is mostly the JDK's own models, which the surrogate listens to, so
   stage 3 reads the same model rather than a second copy — except where sharing proved wrong and the
   surrogate keeps a private model the emulator flushes into (`JTabbedPane`, `JColorChooser`). What
   this leaves for the direction here: the surrogate still keeps its own copy of the scalar state for
   stage-3 code, so the two layers can drift, and the answer to that is still open.
2. **Notifications fire synchronously, before the mutator returns** (R_decline_effect_only).
   `JComboBox.addItem` fires its `ItemEvent`, and a `ButtonModel` change its `ChangeListener`s,
   before returning. Today the surrogate drives most of those fan-outs. A delayed renderer cannot, so
   the event engine has to live Swing-side too. **`Q_event_engine` — answered** by
   D_emulator_owned_state's second rule: the engine lives where the JDK puts it. That is the model's own fan-out for a `BoundedRangeModel`, and the
   component for `JComboBox`, whose `ItemEvent` / `ActionEvent` engine is a `ListDataListener` on
   its model (ported so, [D_jcombobox](../emulators/decisions.md#D_jcombobox)). A programmatic set then notifies on the caller's thread
   before any hop, which also takes these listeners out from under the session lock
   ([withpeer-shape.md](./withpeer-shape.md) §3 (a)).
3. **Model listeners.** Surrogates subscribe to the Swing models they are given. A model mutated
   before the peer exists must still be reflected when it materialises: a full state sync from the
   models, not a replay. **`Q_model_resync`.**
4. **Attach needs the peers.** `container.add(child)` is `peer.add(childPeer)`, and a tree attaches
   because its root peer joins a UI. Deferred peers mean a subtree materialises at attach, on the UI
   thread, from Swing-side state. **`Q_materialise_at_attach`**: what triggers it, and how deep.
5. **A queue of calls invents an execution order** (D_sync_ui_hop's rejection,
   R_no_silent_improvements). **`Q_flush_not_queue` — direction agreed: a queue after all, scoped so
   it cannot reorder** (§ "The mechanism"). The alternative was a *flush of current Swing state*, a
   `renderTo(peer)` per emulator, about 50 of them. What D_sync_ui_hop rejected was `UI.access()`:
   queued writes interleaving with inline writes on an *attached* component. A queue that exists only
   while a component has no UI, and drains before any newer call runs, has no inline write to
   interleave with.
6. **Some effects cannot be late.** A modal shown from a worker must be on screen before the worker
   blocks (D_modal_from_background). Clipboard, preferences and client-details round-trips wait on
   the browser. **`Q_sync_points` — falls out of the mechanism:** a show is an attaching write, so it
   drains the window's island before it parks.
7. **Browser → Swing state still flows through the peer.** Typed text, a moved caret, a user
   selection or a dragged divider arrive as peer events on the UI thread. With emulator-owned state
   the peer listener writes the emulator field under `callSwing`, as today, but now it is the only
   writer path. That needs checking against R_swing_is_truth's `preventPeerEvents` echo guards,
   whose shape changes when the push is deferred. **`Q_inbound_path`.**
8. **`getPeer()` is public API.** Migrators hand it to vanilla Vaadin layouts, as R_no_vaadin_in_api
   sanctions, and Karibu reaches through it. A lazily built peer means `getPeer()` materialises it,
   and on the UI thread only, so it must throw, or hop, when called from a worker.
   **`Q_getpeer_contract` — direction agreed** in § "The mechanism": `withPeer(Consumer)`, returning
   `void`, is the public access, and `getPeer()` leaves the migrator's surface.
9. **Scale and doctrine.** About 50 emulators and 40 surrogates. The "most emulators are a thin
   delegating shell" statement in `CLAUDE.md` § Current scope, D_surrogate_first's ordering policy
   and D_emulator_surrogate_split all change, so this lands as decisions, not only as code.
   **`Q_migration_path`**: can it go component by component behind the current seam, and which
   component proves the shape first? `JLabel` or `JTextField` is the smallest one with real state.

## The mechanism — queue per island, drain at the attaching write

Agreed 2026-10-01, not built. It answers `Q_flush_not_queue`, `Q_materialise_at_attach`,
`Q_sync_points` and `Q_getpeer_contract`, and supersedes the seam split in
[withpeer-shape.md](./withpeer-shape.md) §2.

- **A surrogate call runs now only if the lock can be taken with the peer's UI current**: inline
  when the caller already holds it, otherwise a hop through the captured session, as `withPeer` does
  today. **Otherwise it is queued** — a peer never attached, or one whose UI has closed (detached
  after attach re-queues rather than running under the session lock alone, which closes the
  `UIDetachedException` branch of `SHelper.runOnSession`, where a body runs with no UI current).
- **One queue per detached island.** Adding a detached child to a detached parent appends the
  child's queue to the parent's. Every child entry predates the `add`, and entries on unrelated
  components commute, so the merged order stays causal.
- **Once queued, always queued until drained:** a later call on the island appends even if a UI has
  meanwhile become available.
- **The drain point is the write that attaches the island** — an attached parent's `add`, or a
  window show — which always runs locked with a UI current. No attach listener; the drain lives in
  the seam.
- **Construction is queued too.** The surrogate is instantiated by the first queued entry as it is
  processed, so no Vaadin constructor runs off the UI thread. An emulator never written to before
  attach gets its peer when the attaching write asks for it.
- **`withPeer(Consumer)` returning `void` is the only public access** — nothing comes back out of a
  body, so the pure-sink rule below is in the type. Emulators can afford it since no getter reads the
  peer (D_emulator_owned_state). Sampler's one public use (`TabbedPanesPanel`'s theme variant on a
  tab-close button) is a pure sink and converts as is. Internal structure (`parentPeer.add(childPeer)`)
  needs the child's peer inside the parent's body: an internal accessor legal only during a hop or
  drain, asserting the lock and a current UI. That assertion becomes R_tolerate_off_ui_thread
  limb 2's narrow waist in place of `checkUIThread()`; keep the one-time hygiene WARN, since the
  Swing-side state is still the migrator's own race.
- **A body touches its own peer only.** `emulator.withPeer(p -> vaadinLayout.add(p))` on a detached
  emulator waits for an attach that only the body itself would cause, and so never runs, silently.
  Embedding an emulator in vanilla Vaadin therefore needs its own API that *is* the attaching write
  (lock, drain, add), e.g. `EHelper.addTo(HasComponents, Component)`; the host app's route mount is
  the same seam. See [mixed-emulator-vaadin-trees.md](./mixed-emulator-vaadin-trees.md) §1.
- **Invariant: a queued call is a pure sink.** Its effect must stay invisible to Swing until attach,
  which holds when its notification already fired Swing-side (D_emulator_owned_state's second rule).
  Enforced mechanically, not by reading Vaadin: reaching `callSwing` or any Swing listener during a
  drain is a bug, and the drain asserts it. The known violators move Swing-side before they can be
  queued: [withpeer-shape.md](./withpeer-shape.md) §3 (a)'s list.
- **Growth: WARN once per island at 1000 entries**, naming the emulator class, with the stack of the
  enqueue that crossed it — that stack names the offending loop. A hidden panel a `Timer` updates
  for an hour is the shape; coalescing is later work, since it needs per-setter knowledge.
- **Tests.** `:emulators`' tests call `getPeer()` 410 times in 93 files, and the add-ons' and
  sampler's tests about 15 more. Either a test-only accessor that drains and returns the peer
  (`EmulatorTesting.peerOf(c)`), or `getPeer()` kept with that contract — drain, assert the lock and
  a current UI, throw otherwise — and documented as for tests. Under MockVaadin a UI is current, so
  either drains inline and leaves the corpus's bare-peer attach shape working. Undecided, and a
  naming question rather than a design one.
- **Docs that change:** R_no_vaadin_in_api names `getPeer` as the sanctioned peer accessor;
  R_tolerate_off_ui_thread limb 2 names it as the chokepoint; R_match_swing_errors case (7) shrinks
  to shows and blocking calls, since writes no longer need an `EmulatorContext`.

## Cheap first steps

- **`Q_vaadin_contract`**: before designing, ask the Flow team, or read the Flow source and
  changelog, what — if anything — Vaadin guarantees about constructing and mutating detached
  components off the UI thread. If a guarantee exists, the urgency drops; if it doesn't, write the
  finding into D_attach_aware_hop as the known dependency it is.
- The pin exists: `DetachedOffUiThreadTest` in `:surrogates` constructs and mutates every surrogate
  on a plain thread with no service, session or UI. It goes red on the Vaadin upgrade that breaks the
  assumption, instead of a migrator finding out. Showing a window is excluded, since that attaches
  it.

## Graduation

Graduate once the mechanism is built and `Q_model_resync`, `Q_inbound_path` and `Q_migration_path`
are decided, as decisions superseding the interim session-capture design; then delete this file.
