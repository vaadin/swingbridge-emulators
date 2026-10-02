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
  has a UI current. Type checks before it exists answer from the declared type (the protected
  `peerIs`). A raw `getPeer()` with no UI still builds it, off-thread as before, reported once per
  JVM by `EHelper.onPeerBuiltOffUIThread` and counted in `EHelper.peersBuiltOffUIThread()`.
- **Lazy today — every emulator.** `vaadinx.LazyPeerTest` builds and configures each on a bare
  worker and asserts the counter did not move; **add every newly lazy emulator to its map.**
  - AWT: `Container()` (so `Panel`), `Label`, `Button`, `Checkbox`, `Choice`, `List`, `Scrollbar`,
    `ScrollPane`.
  - Structural: `JLabel`, `JPanel`, `JSeparator`, `JScrollBar`, `JViewport`, `JLayeredPane`,
    `JTableHeader`, `Box`, `Box.Filler`.
  - Buttons and menus: `JButton`, `JToggleButton`, `JCheckBox`, `JRadioButton`, `JMenuItem`, `JMenu`,
    `JCheckBoxMenuItem`, `JRadioButtonMenuItem`, `JMenuBar`, `JPopupMenu`.
  - Shared models: `JSlider`, `JProgressBar`, `JSpinner`, `JComboBox`, `JList`, `JTree`, `JTable`.
  - Text: `JTextField`, `JPasswordField`, `JTextArea`, `JFormattedTextField` (all five peers),
    `JEditorPane`, `JTextPane`.
  - Containers and leaves: `JScrollPane`, `JSplitPane`, `JToolBar`, `JTabbedPane`, `JColorChooser`,
    `JDesktopPane`, `JOptionPane`, `JFileChooser`, a standalone `JRootPane`.
  - Windows: `Window`, `Frame`, `Dialog` (so `FileDialog`), `JWindow`, `JFrame` (both strategies;
    `FrameStrategy.peerType()` is the declared type), `JDialog`, `JInternalFrame` and its
    `JDesktopIcon`.
- **Lazy protected ctors** (D_peer_ctor_injection, the eager one beside each): `Component`,
  `Container`, `JComponent`, `AbstractButton`, `JToggleButton`, `JMenuItem`, `JTextComponent`,
  `JTextField`, `JEditorPane`, `JLabel`, `Window` (with and without an owner), `Frame`, `Dialog`.
  `JRootPane`'s eager protected ctor is gone — a public-hierarchy leaf, and nothing used it once
  the windows stopped wrapping their surrogate's root pane.

### Progress log

- **2026-10-01** — eight commits on `main` (`70dd01c`, then `4050166` … `9f37f98`): the AWT widgets
  and structural leaves, `ScrollPane`, the button family, the shared-model components, the text
  family, the remaining containers and leaves, `JTable`. Each passed a clean reactor build.
  What each batch changed beyond "lazy", so it is findable later:
  - **The button family owns its `ButtonModel`** ([D_emulator_button_model](../emulators/decisions.md#D_emulator_button_model)):
    the JDK's `Handler` is the whole event fan-out, the surrogate is handed the emulator's model and
    renders it, `doClick` / `setSelected` / the action command never reach the peer, menu items click
    through `doClick(0)`, and `ButtonGroup` is the JDK's over models (its membership lives on the public
    `vaadinx.swing.JToggleButton.ToggleButtonModel`). `AbstractButtonMixin.setModel` now takes the
    model's enabled state, as the JDK does. Deliberately not on the model: the mnemonic.
  - **Shared-model components build the JDK ctor's model** and hand it over (rule 8);
    `FieldReconciler.register` anchors on the UI, not the peer. `JList` does `BasicListUI`'s selection
    shift itself, on the model's thread, and `SJList.setSelectionAdjustedByOwner` stands the surrogate
    down; `SJList.setSelectionModel` now shows the new model's selection (so do `SJCheckBox` /
    `SJRadioButton.setModel`).
  - **Text**: `JFormattedTextField` sets its Document text Swing-side, as the JDK formatter's
    `install` does — the §3 (a) echo in [withpeer-shape.md](./withpeer-shape.md) is closed
    (D_formatted_strategy_interface). `JTextPane`'s browser-edit listener now reads
    `preventPeerEvents` before `callSwing`; the drain assertion caught it. `peerIs` became protected.
  - **Containers and leaves**: menu snapshots are taken inside the write (`JMenuBar` / `JPopupMenu.pushTree`).
  - **`JTable`** runs the JDK ctor's sequence (column model, selection model, data model, each
    through its setter) over a surrogate that starts with no columns; the JDK's
    `createDefaultColumnModel` / `createDefaultDataModel` hooks exist and are reached. The selection
    mirror and the renderer install wait for a data model. It also owns its sorter and columns
    (`SJTable.setSorterNotifiedByOwner`).
- **2026-10-02** — the windows. Beyond "lazy":
  - **The root pane is built by the emulator and handed down** (D_rootpane_containment):
    `createRootPane()` returns a fresh `JRootPane`, and `setRootPane` passes its `SJRootPane` peer to
    the new public `setRootPane` on `SJFrame` / `SJDialog` / `SJWindow` / `SJInternalFrame`
    (`RootPaneScaffold.setRootPane`). It used to wrap the surrogate's own root pane, which reached
    the window's peer from the constructor.
  - Owner surrogates are read inside the peer factory (`unwrapSWindow` / `unwrapSFrame` /
    `windowOwnerToSFrame`), on the UI thread; the window's `OpenedChangeListener` bridge and
    `JInternalFrame`'s relay and header handlers are writes (rule 5).
  - `JFrame` / `JDialog.setDefaultCloseOperation` validate in the emulator, as the JDK does, rather
    than relying on the surrogate's throw (rule 6). `DialogStrategy.disposePeer` writes through
    `withPeer`; `InlineStrategy` still touches its `SJPanel` directly, always with a UI current.
  - The choosers' raw Vaadin buttons are built inside a write on the dialog (rule 3), so a blocking
    `JColorChooser.showDialog` / `JFileChooser` show from a worker drains them at the show.
- **Left as found, noticed on the way:** `new JTable(Object[][], Object[])` builds a
  `DefaultTableModel` where the JDK builds an `AbstractTableModel` over the arrays; `JCheckBox` /
  `JRadioButton` skip the JDK ctor's `setBorderPainted(false)` / `setHorizontalAlignment(LEADING)`;
  `JTree(null)` throws where the JDK accepts a null model; a few render-time callbacks (`JList`,
  `JTree`, `JTable` cell renderers, `JTree.onPeerToggle`) still read the surrogate — they run on the
  peer, so they are not reaches, but they are not emulator-owned state either.

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
9. **One shape per model, and a queued position may be stale** (`Q_model_resync`). A model reaches
   its surrogate *either* by the surrogate being built from it at drain (it reads the current state,
   then listens) *or* by the emulator's own listener queuing a write per change into a surrogate that
   starts empty (`JTable`'s columns) — never both, or every pre-attach change lands twice. And a
   queued write that carries a model position (`ensureIndexIsVisible`, `scrollToItem`,
   `setRowHeight(row, …)`) drains against the model as it is *then*, not as it was when queued: it
   must tolerate an index or node that no longer exists, dropping the effect without throwing
   (R_decline_effect_only), as `JTree.scrollPathToVisible` does.
10. **A peer→Swing listener filters on `isFromClient()` first** (`Q_inbound_path`), wherever its
    event carries it (value-change, click, focus, blur): a server-originated peer event is our own
    write's echo or the surrogate rendering the emulator's model, never news. `preventPeerEvents`
    stays behind it as defence in depth, and value equality behind that for the echoes that do come
    from the client (`JTextPane` / `JEditorPane`'s init handshake). A DOM-event listener has no
    server origin and keeps the flag alone.

## The worklist, in order

1. **Audit the positional writes against rule 9** across the already-lazy emulators: every
   `withPeer` body that takes an index, row or node, checked for what it does when that position is
   gone by drain time. Only `JTree.scrollPathToVisible` is known safe.
2. **Convert the inbound listeners to rule 10, auditing for Vaadin's own server-side changes.**
   About a dozen guard on `isFromClient()` today and about a dozen on `preventPeerEvents` alone.
   Before converting each of the latter, find what server-side state change the peer makes *by
   itself* that the listener currently hears — e.g. `TabSheet` selecting a neighbour when the
   selected tab is removed — and make sure the emulator computes it the JDK's way instead, or the
   filter turns a heard change into a silent emulator/browser disagreement.

## After the sweep

- **The suite-wide gate — landed in `:emulators` (2026-10-02).** `vaadinx.PeersOnUIThreadGate`, a
  Jupiter extension autodetected through `junit-platform.properties`, fails every test (and every
  class, for `@BeforeAll` / `@AfterAll`) during which `EHelper.peersBuiltOffUIThread()` moved, with
  the reach's stack as the cause (`EHelper.lastPeerBuiltOffUIThread()`). The first run found only
  `CheckUIThreadTest`'s probe, built with its UI already nulled; `ModalFromBackgroundThreadTest`
  and `JTabbedPaneBackgroundModelTest` stopped building off-thread when the windows went lazy.
  **Measured over the whole reactor the same day: zero off-thread builds** in `:sampler`,
  `:emulators-printing`, `:emulators-spring`, `:migration-guardrails` and both add-ons, so the gate
  is not installed there yet — `:emulators` has no test-jar, and a per-module copy needs a header
  per licence lane (the jgoodies fork's tree takes no Vaadin file at all). Open: share it (a
  test-jar carrying the one class), copy it, or leave `:emulators` as the only gate. Still to
  decide with it: the fate of `DetachedOffUiThreadTest`
  ([vaadin-ui-thread-only.md](./vaadin-ui-thread-only.md) § "Cheap first steps").
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
- **Docs to change at graduation:** R_swing_is_truth (the `preventPeerEvents` guard is no longer
  the primary echo filter; `isFromClient()` is, per rule 10); SD_toggle_checkbox_first_cut's Validation paragraph (still says
  the emulator's events come through a bridged surrogate pulse); R_no_vaadin_in_api (names `getPeer` as the sanctioned accessor),
  R_tolerate_off_ui_thread limb 2 (names it as the chokepoint) and limb 3 (`withPeer` is synchronous —
  it now queues), R_match_swing_errors case (7) (writes no longer need a context), D_attach_aware_hop,
  D_no_context_throws, D_sync_ui_hop, SD_sjbutton, CLAUDE.md § Current scope (most emulators "rendered
  by a surrogate peer while owning their state" stays true, the concurrency paragraph does not).
