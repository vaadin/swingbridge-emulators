# Lazy peers: the remaining sweep

**Status:** in progress, opened 2026-10-01. The mechanism of
[vaadin-ui-thread-only.md](./vaadin-ui-thread-only.md) § "The mechanism" is built and partly rolled
out; this file is the worklist for the rest, and the rules the first batches taught. Graduates with
that file.

**Maintainer-facing.** Read first: [R_tolerate_off_ui_thread](../CLAUDE.md#hard-rules),
[D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state),
[D_attach_aware_hop](../emulators/decisions.md#D_attach_aware_hop).

## Where it stands

- **Step 1, the queue** — `vaadinx.awt.PeerWriteQueue`. A `withPeer` write runs inline with a UI
  current, hops when the peer is attached and its UI open, and otherwise queues; the queue drains
  at attach, at `getPeer()` with a UI current, and before any later write. `withPeerOnLiveUI`
  delegates to `withPeer`. `EHelper.callSwing` asserts a drained write never reaches it (pure sink).
- **Step 2, lazy construction** — `vaadinx.awt.Component(Class<P>, Supplier<P>)` beside the eager
  `(Component peer)` ctor. The peer is built by the first write, `getPeer()` or attaching add that
  has a UI current. Type checks before it exists answer from the declared type (`peerIs`, package
  `vaadinx.awt` only). A raw `getPeer()` with no UI still builds it, off-thread as before, reported
  once per JVM by `EHelper.onPeerBuiltOffUIThread` and counted in `EHelper.peersBuiltOffUIThread()`.
- **Lazy today:** `JLabel`, `JPanel`, `JSeparator`, `JScrollBar`, `JViewport`, `JLayeredPane`,
  `JTableHeader`, `Box`, `Box.Filler`, `Container()` (so AWT `Panel`), AWT `Label`, `Button`,
  `Checkbox`, `Choice`, `List`, `Scrollbar`, `ScrollPane`, and the button family — `JButton`,
  `JToggleButton`, `JCheckBox`, `JRadioButton`, `JMenuItem`, `JMenu`, `JCheckBoxMenuItem`,
  `JRadioButtonMenuItem`, and the shared-model components `JSlider`, `JProgressBar`, `JSpinner`,
  `JComboBox`, `JList`, `JTree`, and the text family — `JTextField`, `JPasswordField`,
  `JTextArea`, `JFormattedTextField` (all five peers), `JEditorPane`, `JTextPane`, and `JScrollPane`,
  `JSplitPane`, `JToolBar`, `JTabbedPane`, `JColorChooser`, `JMenuBar`, `JPopupMenu`,
  `JDesktopPane`, `JOptionPane`, `JFileChooser`, a standalone `JRootPane`. `vaadinx.LazyPeerTest` builds and configures each on a
  bare worker and asserts the counter did not move; **add every newly lazy emulator to its map.**
- **A menu tree's snapshot is taken inside the write** (`JMenuBar` / `JPopupMenu.pushTree`), so the
  peer shows the tree's state at the drain and the items' icons become Vaadin `Image`s with a UI current.
- **`FieldReconciler.register` anchors on the UI**, not the peer, so an emulator whose peer is
  not built yet still gets its bootstrap check.
- **The button family owns its `ButtonModel`** ([D_emulator_button_model](../emulators/decisions.md#D_emulator_button_model)):
  the surrogate is handed the emulator's model and renders it, `doClick` / `setSelected` never
  reach the peer, and `ButtonGroup` coordinates models as the JDK's does.
- **JTable owns its sorter and columns** (`notifySorter(ModelChange)`, `createDefaultColumnsFromModel`,
  `SJTable.setSorterNotifiedByOwner`), so a queued write no longer holds back what Swing reads. It is
  still eager.

## Rules the sweep taught — check each when converting an emulator

1. **A guard a peer→Swing listener reads is set inside the write, never around it.** Around it, the
   flag is cleared before a queued write drains, and the echo it suppresses gets through
   (JTable's `peerSelectionMuted`; the drain assertion caught it).
2. **A peer type check goes inside the write**: `withPeer(p -> { if (p instanceof X x) … })`, not
   `if (getPeer() instanceof X x) withPeer(…)`. The outside form reaches the peer to ask. Where the
   branch has to be outside (an `else` that WARNs), the protected `peerIs(X.class)` answers from
   the declared type without building.
3. **Another component's peer is read inside the write** that uses it:
   `withPeer(p -> surrogate().setContent(view.getPeer()))`, never a local captured before. Same for
   Vaadin components the write creates (`new Div()`).
4. **No read of surrogate state after a write** — the write may not have run. Whatever the emulator
   reads back has to be its own (D_emulator_owned_state); JTable's sorter and `getRowSorter()` were
   the cases.
5. **Constructor-time listener bridges and initial pushes are writes** — wrap them in one `withPeer`
   (the AWT widgets' `installPeerBridge`).
6. **A peer constructor that validates arguments needs the check made eagerly in the emulator**, or a
   lazy peer defers the throw. Check before converting. `ScrollPane` checks inside a static factory
   passed to `super(...)`, so nothing is half-built; `Scrollbar` checks after it.
7. **A lambda in `super(type, () -> new SX(arg))` captures constructor parameters only**, so
   argument-derived peer config is computed inside the lambda.
8. **Build the model the JDK's constructor builds, and hand it to the surrogate** — never read
   a model or a default back out of a peer the constructor configured. The JDK's defaults seed
   the fields and the reconcile baselines; a model change the JDK's *UI* reacts to (a list's
   selection shift) is the emulator's to do, with an `…ByOwner` flag telling the surrogate to
   stand down (`SJTable.setSorterNotifiedByOwner`, `SJList.setSelectionAdjustedByOwner`).

## The worklist, in order

1. **Windows** — `Window`, `Frame`, `Dialog`, `JWindow`, `JFrame`, `JDialog`, and `JInternalFrame`
   (an overlay `Dialog`): shows go through `EHelper.runInUIThread`, which finds a UI through the
   `EmulatorContext`; `JFrame` picks its peer by `@MainWindow` (D_frame_strategy), so the declared
   type is a choice too. Two things ride on it: a window's `JRootPane` shares its surrogate's
   `SJRootPane` (the eager protected ctor), and the blocking choosers (`JColorChooser.showDialog` /
   `createDialog`, `JFileChooser`'s open/save) compose raw Vaadin buttons into the dialog's peer.
2. **`JTable`** — its private `JTable(SJTable)` ctor reads the model, columns and flags back out of the
   surrogate the public ctors built. It has to build them itself, as the JDK's ctor does.
3. **The non-leaf emulators' protected `(Component peer)` ctors** (D_peer_ctor_injection) — give each a
   lazy overload for its subclasses; the eager one stays for a migrator's own subclass.

## After the sweep

- **A suite-wide gate**: fail the build when `EHelper.peersBuiltOffUIThread()` is above zero at the end
  of a module's tests, so a new raw reach reddens instead of WARNing. Worth adding as soon as most of
  the surface is lazy; `ModalFromBackgroundThreadTest` and `JTabbedPaneBackgroundModelTest` are the
  suite's existing off-thread builders.
- **Retire the eager ctor** in `Component` once nothing in SB-Emulators calls it.
- **`getPeer()` leaves the migrator's surface** for `withPeer` (agreed); kept as a test accessor that
  drains and asserts a UI. **An attaching-write API for embedding an emulator in vanilla Vaadin**
  (`EHelper.addTo(HasComponents, Component)`), since `withPeer(p -> layout.add(p))` on a detached
  emulator never runs — see [mixed-emulator-vaadin-trees.md](./mixed-emulator-vaadin-trees.md) §1. The
  host app's route mount is the same seam.
- **Open, not yet measured:** a surrogate's own `onAttach` runs before the emulator's drain listener,
  so a nested peer with queued writes meets attach with its pre-drain state.
  `JTable.installEditorComponents` reads `comp.getPeer()` outside a write — browser-driven today.
- **Docs to change at graduation:** R_no_vaadin_in_api (names `getPeer` as the sanctioned accessor),
  R_tolerate_off_ui_thread limb 2 (names it as the chokepoint) and limb 3 (`withPeer` is synchronous —
  it now queues), R_match_swing_errors case (7) (writes no longer need a context), D_attach_aware_hop,
  D_no_context_throws, D_sync_ui_hop, SD_sjbutton, CLAUDE.md § Current scope (most emulators "rendered
  by a surrogate peer while owning their state" stays true, the concurrency paragraph does not).
