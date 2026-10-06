# Lazy peers: what is left after the sweep

**Status:** open, opened 2026-10-01. The mechanism is built and every emulator is lazy
([D_lazy_peers](../emulators/decisions.md#D_lazy_peers), which also holds the ten rules a peer
write follows); this is the worklist for what remains. Rule numbers below are D_lazy_peers'.

**Maintainer-facing.** Read first: [D_lazy_peers](../emulators/decisions.md#D_lazy_peers),
[R_tolerate_off_ui_thread](../CLAUDE.md#hard-rules),
[D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state),
[D_attach_aware_hop](../emulators/decisions.md#D_attach_aware_hop).

## The worklist

1. **Audit the positional writes against rule 9** across the emulators: every `withPeer` body that
   takes an index, row or node, checked for what it does when that position is gone by drain time.
   Only `JTree.scrollPathToVisible` is known safe.
2. **Convert the inbound listeners to rule 10, auditing for Vaadin's own server-side changes.**
   About a dozen guard on `isFromClient()` today and about a dozen on `preventPeerEvents` alone.
   Before converting each of the latter, find what server-side state change the peer makes *by
   itself* that the listener currently hears — e.g. `TabSheet` selecting a neighbour when the
   selected tab is removed — and make sure the emulator computes it the JDK's way instead, or the
   filter turns a heard change into a silent emulator/browser disagreement.

## Divergences noticed on the way, left as found

- `new JTable(Object[][], Object[])` builds a `DefaultTableModel` where the JDK builds an
  `AbstractTableModel` over the arrays.
- `JCheckBox` / `JRadioButton` skip the JDK ctor's `setBorderPainted(false)` /
  `setHorizontalAlignment(LEADING)`.
- `JTree(null)` throws where the JDK accepts a null model.
- A few render-time callbacks (`JList`, `JTree`, `JTable` cell renderers, `JTree.onPeerToggle`)
  still read the surrogate — they run on the peer, so they are not reaches, but they are not
  emulator-owned state either.

## After the sweep

- **Share the suite-wide gate, or not.** `vaadinx.PeersOnUIThreadGate` runs in `:emulators` only.
  The rest of the reactor measured zero off-thread builds (2026-10-02), but nothing keeps it so:
  `:emulators` has no test-jar, and a per-module copy needs a header per licence lane (the
  jgoodies fork's tree takes no Vaadin file at all). Options: share it (a test-jar carrying the
  one class), copy it, or leave `:emulators` as the only gate. Still to decide with it: the fate of
  `:surrogates`' `DetachedOffUiThreadTest`, which constructs and mutates every surrogate on a plain
  thread and now pins an assumption nothing relies on — shrink it to what still touches a surrogate
  unlocked (`SHelper.runOnSession`'s inline fallbacks), or delete it.
- **Coalescing, starting with text.** A text component queues a full-text snapshot per Document
  change, so a never-shown log area appended to N times holds N copies — quadratic. A newer
  snapshot wholly supersedes an older one, which makes it the easy first case; the general case
  needs per-setter knowledge. The 1000-entry WARN covers it until then.
- **Retire the eager ctor** in `Component` once nothing in SB-Emulators calls it.
- **`getPeer()` leaves the migrator's surface** for `withPeer` (agreed); kept as a test accessor that
  drains and asserts a UI. **An attaching-write API for embedding an emulator in vanilla Vaadin**
  (`EHelper.addTo(HasComponents, Component)`), since `withPeer(p -> layout.add(p))` on a detached
  emulator never runs — see [mixed-emulator-vaadin-trees.md](./mixed-emulator-vaadin-trees.md) §1. The
  host app's route mount is the same seam.
- **Open, not yet measured:** a surrogate's own `onAttach` runs before the emulator's drain listener,
  so a nested peer with queued writes meets attach with its pre-drain state.
  `JTable.installEditorComponents` reads `comp.getPeer()` outside a write — browser-driven today.
- **Docs that still describe the synchronous seam** and change once this closes: R_swing_is_truth
  (the `preventPeerEvents` guard is no longer the primary echo filter; `isFromClient()` is, per
  rule 10); SD_toggle_checkbox_first_cut's Validation paragraph (still says the emulator's events
  come through a bridged surrogate pulse); R_no_vaadin_in_api (names `getPeer` as the sanctioned
  accessor), R_tolerate_off_ui_thread limb 2 (names it as the chokepoint) and limb 3 (`withPeer` is
  synchronous — it now queues), R_match_swing_errors case (7) (writes no longer need a context),
  D_attach_aware_hop, D_no_context_throws, D_sync_ui_hop, SD_sjbutton, `emulators/architecture.md`
  § "The peer field" / § "Peer instantiation" / § "Threading", and CLAUDE.md § Current scope (most
  emulators "rendered by a surrogate peer while owning their state" stays true, the concurrency
  paragraph does not).
