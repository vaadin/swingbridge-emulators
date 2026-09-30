# Vaadin components touched on the UI thread only — surrogates as a locked renderer

**Status:** open, a direction taken, not yet designed. Opened 2026-09-28. The interim design,
decided the same day, is *eager peers built on any thread, and a session capture per component to
hop through*. This file is how SB-Emulators gets from there to **zero Vaadin exposure to background
threads**.

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
   R_no_silent_improvements). Batched and late rendering has to be a *flush of current Swing state*
   into the peer, not a replay of recorded calls. That requires every emulator to be able to render
   itself from its state alone: effectively a `renderTo(peer)` per emulator. **`Q_flush_not_queue`.**
6. **Some effects cannot be late.** A modal shown from a worker must be on screen before the worker
   blocks (D_modal_from_background). Clipboard, preferences and client-details round-trips wait on
   the browser. So the renderer needs defined synchronous flush points, and those paths flush before
   they park. **`Q_sync_points`.**
7. **Browser → Swing state still flows through the peer.** Typed text, a moved caret, a user
   selection or a dragged divider arrive as peer events on the UI thread. With emulator-owned state
   the peer listener writes the emulator field under `callSwing`, as today, but now it is the only
   writer path. That needs checking against R_swing_is_truth's `preventPeerEvents` echo guards,
   whose shape changes when the push is deferred. **`Q_inbound_path`.**
8. **`getPeer()` is public API.** Migrators hand it to vanilla Vaadin layouts, as R_no_vaadin_in_api
   sanctions, and Karibu reaches through it. A lazily built peer means `getPeer()` materialises it,
   and on the UI thread only, so it must throw, or hop, when called from a worker. **`Q_getpeer_contract`.**
9. **Scale and doctrine.** About 50 emulators and 40 surrogates. The "most emulators are a thin
   delegating shell" statement in `CLAUDE.md` § Current scope, D_surrogate_first's ordering policy
   and D_emulator_surrogate_split all change, so this lands as decisions, not only as code.
   **`Q_migration_path`**: can it go component by component behind the current seam, and which
   component proves the shape first? `JLabel` or `JTextField` is the smallest one with real state.

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

Graduate when `Q_state_owner`, `Q_event_engine`, `Q_flush_not_queue`, `Q_sync_points` and
`Q_getpeer_contract` are decided, as decisions superseding the interim session-capture design; then
delete this file.
