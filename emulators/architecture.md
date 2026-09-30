# Architecture

## Package layout

```
vaadinx.awt.*    — Component / Container hierarchy ported from java.awt.*
vaadinx.swing.*  — JComponent subclasses ported from javax.swing.*
vaadinx          — static helpers (EHelper.onUnimplemented, formatting)
```

Each emulated class mirrors its JDK counterpart in name and public API. The inheritance tree mirrors Swing/AWT: `vaadinx.swing.JButton` → `vaadinx.swing.AbstractButton` → `vaadinx.swing.JComponent` → `vaadinx.awt.Container` → `vaadinx.awt.Component`.

We re-implement `java.awt.*` ourselves rather than extending the real JDK classes — extending the real ones drags in peer/headless/toolkit internals we don't want.

**Porting is whitelist-only.** Only the Component hierarchy plus the interfaces that reference Component (`Icon`, `Border`, `LayoutManager`, `LayoutManager2`) are ported. Data types (`Color`, `Font`, `Dimension`, `Insets`, `Graphics`, `Image`), events whose source is `Object` (`ActionEvent`), listeners that don't expose a typed Component (`ActionListener`, `Action`), and models (`ListModel`, `TableModel`, …) are reused straight from the JDK. See D_whitelist_porting.

## The peer field

Every `vaadinx.awt.Component` holds a single protected final `peer`: the Vaadin component it controls.

```java
// vaadinx.awt.Component
protected final com.vaadin.flow.component.Component peer;

protected Component(com.vaadin.flow.component.Component peer) {
    this.peer = peer;
    vaadinx.EHelper.onCreated(peer, this);  // register the 1:1 mapping
}
```

All Swing API methods (`setEnabled`, `setText`, `addActionListener`, …) forward to the peer.

## 1:1 peer ↔ emulator mapping

The relationship is bidirectional. The emulator holds its peer in `this.peer`; the peer carries the emulator back via Vaadin's per-component data slot (`ComponentUtil.setData(peer, vaadinx.awt.Component.class, this)`), established by `EHelper.onCreated` in the constructor above.

Reverse lookup uses `EHelper.getEmulator(peer)`, which **throws** when no emulator has been registered — an internal invariant check, not a violation of D_never_fail_on_gaps (the never-throw rule is for user-facing Swing API, not internal plumbing).

**Why it matters.** When `getParent()` lands, it walks the peer's Vaadin parent and calls `getEmulator(...)` to find the enclosing `Container` emulator. Constructing a fresh emulator there would produce two emulators for one peer, diverging on state (name, listeners, size, locale, …). One canonical emulator per peer, always looked up, never re-created. See D_peer_emulator_mapping.

## Peer instantiation

Constructor injection — each leaf class decides which Vaadin peer it wants and passes it up the chain:

```java
public class JButton extends AbstractButton {
    public JButton()             { this(new com.vaadin.flow.component.button.Button()); }
    public JButton(String text)  { this(new com.vaadin.flow.component.button.Button(text)); }
    protected JButton(com.vaadin.flow.component.Component peer) { super(peer); }
}

public class JToggleButton extends JButton {
    public JToggleButton() { super(new com.vaadin.flow.component.checkbox.Checkbox()); }
}
```

Keeps `peer` final, avoids "overridable method in constructor" traps, and lets library subclasses override the peer type while user subclasses (`class MyButton extends JButton`) inherit the default peer transparently.

**Known limitation.** If a third-party Swing subclass (JGoodies-style) demands capabilities the default Vaadin peer can't provide, we may need to swap the peer type — refactor when we get there.

## Stub policy: `vaadinx.EHelper.onUnimplemented`

Any unimplemented method calls:

```java
vaadinx.EHelper.onUnimplemented("ClassName", "methodName", arg0, arg1, …);
```

Single static entry point on the dedicated `vaadinx.EHelper` class. Lives there — not on `Component` — so `vaadinx.awt.Component` can be regenerated from `java.awt.Component` without clobbering the plumbing (D_ehelper_statics).

Formatting rules for the varargs:
- `null` renders as `null`.
- Classes in `java.lang.*` (String, boxed primitives, Class) render via `toString()`.
- Everything else renders as `getClass().getSimpleName()` — so an `ActionEvent` arg shows up as `ActionEvent`, not its verbose default toString.

Contract:
- Void methods: no-op after the log.
- Getters: return a sensible default (`null`, `0`, `false`, empty collection).
- Never throw.

**Deliberate no-ops** (`vaadinx.EHelper.onNoop("ClassName", "method")`, DEBUG log only). For Swing API that's redundant in the Vaadin world — framework does it, or the concept doesn't apply:
- Layout invalidation: `validate`, `invalidate`, `revalidate`, `doLayout`, `layout`.
- Repaint: all four `repaint(...)` overloads — browser repaints on DOM change.
- AWT peer lifecycle: `addNotify`, `removeNotify` — our peer is final, no dynamic create/destroy. `Container` still overrides both to walk children (D_best_effort_behaviour: user subclasses may override and expect the recursive call), and for ordinary components the self-call bottoms out in `onNoop` at `Component`, driven by Vaadin attach/detach. **`Window` is the exception** and carries a real `displayable` bit that `addNotify` sets and `removeNotify` clears, with `pack()` / `show()` / `dispose()` reaching them as the JDK does — see [D_window_displayable](./decisions.md#D_window_displayable).
- `UIManager` / `LookAndFeel` APIs — apps set a L&F at startup; must not throw.

Methods that *look* like no-ops but stay as `onUnimplemented` (WARN): `paint(Graphics)` and friends (user code may be doing custom painting — silent no-op hides breakage); listener `add*`/`remove*` for unported event hierarchies (accepting a registration that never fires is worse than warning).

**Unsupported peer shape** (`vaadinx.EHelper.onUnsupportedPeerShape("ClassName", "situation")`, ERROR log, returns). For situations where the emulation is fundamentally unable to honor the request and doing nothing is the safest response — e.g. setting a `LayoutManager` on a container whose peer isn't a Div (D_layout_css_on_content). Louder than `onUnimplemented`'s WARN because the user has stepped into genuinely broken territory, not just unimplemented territory.

## Hand-finished setter pattern

When a setter graduates from `onUnimplemented` stub to real implementation, it follows this shape:

```java
public void setFoo(Foo arg) {
    Foo oldFoo = this.foo;                   // 1. capture old
    this.foo = arg;                          // 2. mutate field
    applyToPeer(arg);                        // 3. apply to DOM (style / attr), if any
    firePropertyChange("foo", oldFoo, arg);  // 4. fire with Swing's property name
}
```

Use Swing's property name verbatim (`"foreground"`, `"componentOrientation"`, `"preferredSize"`) so existing `PropertyChangeListener` code keeps working. `firePropertyChange` and the `add/remove/getPropertyChangeListener` family delegate to a single `java.beans.PropertyChangeSupport` held by `Component` — it already handles null names, equal old/new skipping, and null-listener no-ops, matching AWT's own delegation.

### Swing property → peer DOM

| Swing setter | Applied to peer |
|---|---|
| `setForeground(Color)` | style `color` |
| `setBackground(Color)` | style `background-color` |
| `setFont(Font)` | styles `font-family` + `font-size` + `font-weight` + `font-style` |
| `setPreferredSize(Dimension)` | styles `width` + `height` |
| `setMinimumSize(Dimension)` | styles `min-width` + `min-height` |
| `setMaximumSize(Dimension)` | styles `max-width` + `max-height` |
| `setLocale(Locale)` | attribute `lang=<language-tag>` |
| `setComponentOrientation(…)` | attribute `dir=ltr` / `dir=rtl` |
| `setCursor(Cursor)` | style `cursor` — predefined types only; custom cursors clear the style |
| `setName(String)` | — (field only) |

`null` / `ComponentOrientation.UNKNOWN` removes the style/attribute — CSS inheritance takes over, matching Swing's "inherit from parent" semantics for `null`.

***`setPreferredSize` on the `@MainWindow` JFrame is silent-swallowed by default.*** `JFrame` overrides `setPreferredSize` under InlineStrategy: the field shadows so `getPreferredSize()` round-trips (R_swing_is_truth), but the peer is re-pinned `width:100% / height:100%` over the parent table's pixel write. The browser is the WM; like a tiling WM, it ignores the requested size silently. `setResizable(false) + setPreferredSize + pack()` is the opt-out for fixed-size main windows. Per [D_inline_route_sizing](./decisions.md#D_inline_route_sizing).

`Color` → CSS via `EHelper.toCss`: `rgb(r,g,b)` if opaque, `rgba(…)` otherwise. Font family → CSS via `EHelper.toCssFontFamily`: AWT logical names (`Dialog`, `Serif`, `SansSerif`, `Monospaced`, `DialogInput`) map to CSS generics; everything else is quoted with a `sans-serif` fallback.

## Threading

Swing has one Event Dispatch Thread. Under SB-Emulators the "EDT" is **a UI fiber under the session lock**: every peer→Swing callback — a click, a value change, a timer fire, a `SwingWorker` callback — runs through `EHelper.callSwing`, which starts it as a virtual thread of [vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs) on the request thread that holds the session lock ([D_callswing_loom](./decisions.md#D_callswing_loom), R_callswing_envelope). `SwingUtilities.isEventDispatchThread()` is therefore `UI.getCurrent() != null`: a fiber has a current UI, a thread the app spawned itself does not.

| Swing | SB-Emulators |
|---|---|
| `SwingUtilities.invokeLater(r)` | on the EDT, `UI.access(…)` → `callSwing(r)`; from a background thread, delivered to the session's live UI through the thread's `EmulatorContext` (`EHelper.onSessionLiveUI`), and dropped once the session has no live UI left |
| `EventQueue.invokeLater(r)` | same |
| `SwingUtilities.invokeAndWait(r)` | background threads only (the EDT throws, as Swing would deadlock there): `r` runs in a UI fiber on the live UI and the caller waits until it has finished, including any modal dialog it opens ([D_invoke_from_background](./decisions.md#D_invoke_from_background)) |
| a `Timer` fire, a `SwingWorker`'s `process` / `done` | delivered like `invokeLater` from a background thread; the scheduler and the worker pool are session-scoped, so they survive an F5 ([D_session_scoped_pools](./decisions.md#D_session_scoped_pools)); workers run on platform threads, because a virtual thread created inside a fiber inherits the fiber runner's scheduler ([D_worker_platform_threads](./decisions.md#D_worker_platform_threads)) |
| a Swing→peer write from any thread | synchronous: `Component.withPeer` runs the body inline when a UI is current, otherwise under the lock of the session the component captured — never through `UI.access()` ([D_attach_aware_hop](./decisions.md#D_attach_aware_hop), [D_sync_ui_hop](./decisions.md#D_sync_ui_hop), R_tolerate_off_ui_thread) |

A thread with neither a current UI nor an `EmulatorContext` — a bare `new Thread`, an unwrapped executor — cannot reach the EDT at all and throws; the fix is `EmulatorContext.wrap(executor)` at the submit site ([D_no_context_throws](./decisions.md#D_no_context_throws)).

## Single-UI assumption

One app per `VaadinSession`, with one live `UI` running it at a time. Static Swing registries (`JFrame.getFrames()`, etc.) scope to the app instance's **browser tab** — `vaadinx.WindowRegistry`, per [D_window_registry](./decisions.md#D_window_registry), which is the `AppContext` analogue and survives an F5 where UI scope would not. The invariant is enforced at attach: the first tab's `MainWindowRoute` becomes the session's `AppTab`, a second tab opening the app is curtained rather than run, and an F5 rebinds the pointer to the new UI so a running `Timer` or `SwingWorker` delivers there ([D_active_ui_pointer](./decisions.md#D_active_ui_pointer), [D_session_scoped_pools](./decisions.md#D_session_scoped_pools)). Additional top-level Swing windows are fine — they render as overlays of that one UI; several *tabs* running the same app are not a supported shape.

## Blocking dialogs

A modal dialog blocks for real, in the browser as under Karibu. `Dialog.setVisible(true)` and everything composed over it (`JOptionPane.show*`, `JFileChooser.show*`, `JColorChooser.showDialog`) parks the calling UI fiber through `EHelper.awaitModal` → `UIFibers.parkAndAwait`, anchored to the dialog's peer, with the session lock released: the request that opened the dialog completes, Push carries the dialog to the browser, and the click that closes it completes the park's future, so the fiber resumes as an access task of the session and the call returns the answer. `callSwing` then drains the session's access queue, which is what makes a click's consequences settle before the next user action, as they do on one EDT ([D_callswing_loom](./decisions.md#D_callswing_loom), [D_blocking_dialogs_library](./decisions.md#D_blocking_dialogs_library), [D_joptionpane](./decisions.md#D_joptionpane)). From a background thread carrying an `EmulatorContext` the same call shows the dialog on the UI thread and blocks only the caller, which is the desktop's own shape ([D_modal_from_background](./decisions.md#D_modal_from_background)).

Four things follow, and each is a rule rather than a detail:

- **Push is mandatory**: a park's release is what flushes the dialog, and only Push carries it to the browser.
- **JDK 24+ at runtime**: before [JEP 491](https://openjdk.org/jeps/491), a virtual thread parking inside a `synchronized` block pins its carrier — the request thread, holding the session lock — and the click that would unpark it can never arrive. Any monitor above a modal call triggers it, including ones in the migrator's own code, so `SwingBridgeEmulatorsBootstrap` refuses an older runtime at servlet init.
- **The session lock is wrapped** in `VirtualThreadAwareLock` by the servlet base classes (`SwingBridgeVaadinServlet`, `SwingBridgeSpringServlet`), and the runner's `SessionLockCheck` refuses a session without it ([D_vt_aware_session_lock](./decisions.md#D_vt_aware_session_lock)); request threads must be platform threads ([D_virtual_request_threads](./decisions.md#D_virtual_request_threads)).
- **Every park goes through `parkAndAwait`** — the modal park and the three browser round-trips (preferences, clipboard, client details, via `EHelper.awaitBrowserRoundTrip`) — never a bare `future.get()` or `latch.await()` inside a fiber, and **no monitor is held across a park**, since a second fiber entering that monitor would keep the session lock while it waits. A wait nobody will answer — the tab closed, the session destroyed — throws `BrowserSessionClosedError` (R_match_swing_errors case (8)), and `callSwing` ends the fiber quietly on it.

Under Karibu-Testing the same machinery runs: every test setup routes the session lock through the wrapper (`MockVirtualThreadAwareServlet`), so a test that clicks OK on a parked `JOptionPane` exercises the real park and resume.

## Events: reuse vs. port

| JDK type | Strategy | Why |
|---|---|---|
| `java.awt.event.ActionEvent` / `ActionListener` | Reuse as-is | Source is `Object` — we pass a `vaadinx.swing.JButton` as source; user code casts. |
| `javax.swing.Action` | Reuse as-is | API doesn't reference Component. |
| `ComponentEvent` + subclasses (`MouseEvent`, `KeyEvent`, `FocusEvent`, `WindowEvent`, …) | Must port | Constructor demands a `java.awt.Component` source; `getComponent()` returns `java.awt.Component`. A `vaadinx.*` source can't be cast. |

The whole Component-related event surface is ported under `vaadinx.awt.event.*` as one-off scaffolding, keeping AWT's constant values and extending `java.awt.AWTEvent` unchanged, so an `instanceof java.awt.event.*` check in migrated code rewrites to the ported type along with the imports ([D_event_port_policy](./decisions.md#D_event_port_policy)). Which of those events a given emulator fires is recorded in that emulator's own decision entry.

## Layouts

Built-in `LayoutManager` implementations translate Swing layout semantics to CSS and apply it to the container's own `peerContentElement()`. No extra wrapper peer is introduced — the layout reconfigures the container it was installed on. See D_layout_css_on_content.

Two dispatch points:

- `layoutContainer(Container)` — applies container-wide CSS (`display: grid`, `grid-template-areas`, `display: flex; flex-wrap: wrap`, …). Called from `doLayout()` / `validate()`; idempotent.
- `addLayoutComponent(comp, constraint)` — applies per-child CSS (`grid-area: north`, `order: …`) as each child is registered.

Both go through `EHelper.applyContainerCss(element, layoutName, css)`, which inspects the target element's tag: for a plain `<div>` it writes the CSS; otherwise it calls `EHelper.onUnsupportedPeerShape(...)` (ERROR log) and refuses. The seam takes the raw `Element` rather than the Container; `Container.peerContentElement()` is `public` (D_peer_content_element_public) so cross-package layouts (`vaadinx.swing.BoxLayout`, whose JDK package is `javax.swing` not `java.awt`) can resolve the dispatch element. Same-package layouts (`vaadinx.awt.FlowLayout` / `vaadinx.awt.BorderLayout`) reach in identically. Setting a `LayoutManager` on a `JButton`-like component therefore does not break rendering outright — children are still added to the peer, they just don't get laid out. Doing that in Swing is also a bad idea.

Built-in layouts (`FlowLayout`, later `BorderLayout`, …) are **hand-written rather than generator-emitted**: the generator's root-class detection assumes Component-hierarchy ancestry and would attach a `peer` field to a LayoutManager, which has none. The whitelist still maps them (`java.awt.FlowLayout → vaadinx.awt.FlowLayout`) so that any future regeneration of a class whose signature references a layout rewrites the FQN correctly.

**`Window.show()` triggers the validate pass.** Real Swing's `Window.show()` — which is where `setVisible(true)` lands, per [D_visibility_direction](./decisions.md#D_visibility_direction) — implicitly runs a pack/validate cycle before showing; we do the validate half (pack is pixel-sizing, out of scope per R_layouts_close_enough), above the already-visible guard where the JDK keeps `validateUnconditionally()`. The `validate()` call walks the subtree and fires each installed LayoutManager's `layoutContainer(Container)` — which is the point where CSS actually lands on a peer Div. Without it, a JPanel with a default FlowLayout would render as plain block stacking until user code called `validate()` by hand. This is the trigger that makes D_layout_css_on_content real end-to-end.

Rough mapping (each CSS block applied by the named LayoutManager):

| Swing | CSS applied to `peerContentElement()` | Per-child CSS |
|---|---|---|
| `BorderLayout` | `display: grid` + `grid-template-areas` | `grid-area: north/south/east/west/center` |
| `FlowLayout` | `display: flex; flex-wrap: wrap` | — |
| `BoxLayout` | `display: flex; flex-direction: row`/`column` | — |
| `GridLayout` | `display: grid; grid-template-columns/rows: repeat(N, 1fr)` | — |
| `GridBagLayout`, `null` | close-enough, permanently (D_pixel_layout_not_planned) |  |

Best-effort, not pixel-perfect (R_layouts_close_enough).

A custom `LayoutManager` that doesn't implement `vaadinx.awt.CssEmittingLayoutManager` (a hand-rolled pixel layout) can't be translated — no synchronous child measurement server-side. `Container.doLayout` WARNs once and applies a readable vertical `flex-column` fallback (M1D_custom_layoutmanager); it doesn't throw (R_layouts_close_enough). A migrator maps a custom layout to CSS by implementing `CssEmittingLayoutManager` (`containerCss` + optional `childCss`, the same seam every built-in uses); the interface's default `layoutContainer` routes both through `EHelper.applyContainerCss` / `applyChildCss`. The built-ins compute per-child CSS either eagerly at add time (BorderLayout's `grid-area`) or in `containerCss`-then-`childCss` (GridBag/Group, per the interface's call-order contract).

## JFrame content pane

`JFrame` owns a single child `Container` (Div-backed) as its *content pane*. `frame.add(comp)`, `frame.setLayout(mgr)`, and `frame.remove(comp)` redirect into the content pane via the standard Swing `rootPaneCheckingEnabled` flag — so migrated code that uses the content-pane idiom (`frame.getContentPane().add(...)`) and code that adds directly (`frame.add(...)`) both route to the same place.

`JFrame.getRootPane()` returns the frame's **one and only child**, and the pane chain under it is the JDK's ([D_rootpane_containment](./decisions.md#D_rootpane_containment), superseding D_rootpane_holder's holder stance):

```
JFrame → JRootPane → { glassPane, JLayeredPane → { JMenuBar, contentPane } }
```

Note the menu bar belongs to the **layered** pane, not the root pane — the JDK's own containment. `RootLayout`'s y-stacking is reproduced as CSS on the surrogate chain (root pane a flex column, layered pane `display: contents`), measured a layout no-op versus the old flattened DOM. `JRootPane.setDefaultButton(JButton)` installs an Enter shortcut via `Shortcuts.addShortcutListener` with the button's peer as lifecycle owner — Enter anywhere in the UI triggers the button's click while it's attached. The glass pane is real: `setVisible(true)` curtains the window body and swallows mouse input (D_glasspane_structural/SD_glasspane_structural). Layered-pane **z-ordering** stays deferred (`moveToFront/Back` are no-ops) — Vaadin renders in DOM order and `JInternalFrame` renders as a Dialog overlay instead (D_internal_frames).

`LayoutManager` dispatch lands on the content pane (where `setLayout` was installed), so `EHelper.applyContainerCss` writes CSS to the content pane's Div element. The Dialog peer's overlay chrome stays untouched.

## Main window route

A migrated app's main `JFrame` renders **inline** as a Vaadin route's content, not as a centered Dialog. `JFrame`'s emulator picks one of two `FrameStrategy` peers at construction time, pinned for the instance's lifetime. See [D_jframe_as_route](./decisions.md#D_jframe_as_route) for the decision rationale.

### `@MainWindow` annotation + scaffolded route

`vaadinx.swing.MainWindow` (`@Inherited`, `@Target(TYPE)`, `@Retention(RUNTIME)`) marks the migrator's main JFrame class. Discovery is **lazy via the JFrame ctor** — a package-private `AtomicReference<Class<?>> MAIN_WINDOW_CLASS` on `JFrame` is CAS-exchanged with `getClass()` whenever a `@MainWindow`-annotated frame is constructed. Tab #2 instantiating the same class hits the CAS no-op; two different `@MainWindow` classes throw `IllegalStateException` on the second's first ctor.

`:emulators` ships a thin abstract base class `vaadinx.swing.app.MainWindowRoute extends Div`:

```java
public abstract class MainWindowRoute extends Div {
    @Override protected void onAttach(AttachEvent e) {
        if (e.isInitialAttach()) bootstrap();
    }
    protected abstract void bootstrap();
}
```

The migrator pastes a one-class scaffold into their app, owning `@Route` / `@PreserveOnRefresh` / the bootstrap call:

```java
@Route("") @PreserveOnRefresh
public class AppRoute extends MainWindowRoute {
    @Override protected void bootstrap() { MyApp.mainUI(); }
}
```

`main()` splits into a pre-UI half (services, db pools, config, `VaadinBoot.create().run()`) and a per-UI `mainUI()` half (instantiate the `@MainWindow` JFrame and call `setVisible(true)`). One-time manual edit, documented in the migration guide.

### `FrameStrategy` peer dispatch

Package-private interface alongside `vaadinx.swing.JFrame`:

```java
interface FrameStrategy {
    com.vaadin.flow.component.Component createPeer();
    void setVisible(JFrame f, boolean v);
    void setTitle(JFrame f, String t);
    void dispose(JFrame f);
}
```

`pickStrategy(Class<?>)` returns `InlineStrategy` if the class carries `@MainWindow`, `DialogStrategy` otherwise. Both strategies are stateless singletons. `JFrame`'s body stays a thin dispatcher (`strategy.setVisible(this, v)` etc.); per-frame state lives on the `JFrame` instance.

| | `DialogStrategy` (regular JFrame) | `InlineStrategy` (`@MainWindow` JFrame) |
|---|---|---|
| Peer type | `SJFrame` (extends Vaadin Dialog) | `SJPanel` (extends Vaadin Div) |
| `setVisible(true)` | `dialog.setOpened(true)` | attach peer as child of route's div |
| `setVisible(false)` | `dialog.setOpened(false)` | detach peer from route's div |
| `setTitle(s)` | Dialog header | `UI.getCurrent().getPage().setTitle(s)` |
| `dispose()` | close + remove from JFrame registry | detach + close-op dispatch (next section) |
| Peer-originated close trigger | header X / ESC / outside-click on Dialog | browser tab close (no chrome to click) |

A modal `JDialog` with no owner renders as a non-modal Dialog peer when an `@MainWindow` JFrame is present, so the inline main frame stays interactable behind it.

R_leaf_peer_lockdown lock-down still holds: the protected `(Component peer)` ctor stays omitted; user-code subclasses of an `@MainWindow` JFrame inherit the strategy from `@Inherited` annotation walk. See [R_leaf_peer_lockdown](../CLAUDE.md) for the framework-canonical-peer-selection carve-out and [D_frame_strategy](./decisions.md#D_frame_strategy) for the rationale.

### Close-operation dispatch (InlineStrategy)

InlineStrategy has no close button (it's a div, not a Dialog overlay), so available close events shift:

| Event | Behaviour |
|---|---|
| Browser tab close | Fire `WINDOW_CLOSING` → consult `defaultCloseOperation` → `WINDOW_CLOSED`. Listeners run synchronously for cleanup but cannot cancel the close (browser controls unload). |
| `frame.dispose()` | Fire `WINDOW_CLOSED`, detach, and **close the Vaadin session if that took the app's last displayable window** — whatever the close operation ([D_auto_shutdown](./decisions.md#D_auto_shutdown)). |
| `frame.setVisible(false)` | Just detach. No `WINDOW_CLOSING`, no default-op consult. |

Other close ops on InlineStrategy: `DISPOSE_ON_CLOSE` detaches the peer; `HIDE_ON_CLOSE` is `setVisible(false)`; `DO_NOTHING_ON_CLOSE` is ignored. `WINDOW_CLOSING` listeners that throw during tab close are caught via the session `ErrorHandler` and tear-down continues.

Ending the app on the last window's dispose is AWT's own auto-shutdown, reproduced at session scope ([D_auto_shutdown](./decisions.md#D_auto_shutdown)) — it is what resolves [D_gap_severity_triage](./decisions.md#D_gap_severity_triage) case 2 for the inline path, and it superseded an `EXIT_ON_CLOSE`-only bend that consulted the close operation. A running `javax.swing.Timer` holds the app open; DialogStrategy keeps the throw for peer-originated Dialog close per [D_jframe_as_route](./decisions.md#D_jframe_as_route).

### Subproject placement

All in `:emulators`. `:surrogates` is not touched — `SJFrame` and `SJPanel` already exist; the strategy just chooses which to instantiate.

| Piece | Location |
|---|---|
| `@MainWindow` annotation | `vaadinx.swing.MainWindow` |
| `MainWindowRoute` base class | `vaadinx.swing.app.MainWindowRoute` |
| `FrameStrategy` + `DialogStrategy` + `InlineStrategy` | package-private alongside `vaadinx.swing.JFrame` |
| AtomicReference + ctor hook + close-op dispatch | inside `vaadinx.swing.JFrame` |
| Session-destroy → `WINDOW_CLOSING` listener | small helper in `vaadinx.swing` |

## Borders

`vaadinx.swing.border.*` ports the common border classes (`AbstractBorder`, `EmptyBorder`, `LineBorder`, `MatteBorder`, `CompoundBorder`, `TitledBorder`, `BevelBorder`, `EtchedBorder`, `SoftBevelBorder`); `vaadinx.swing.BorderFactory` mirrors the JDK static surface. `JComponent.setBorder(Border)` dispatches through `AbstractBorder.applyCss(Element)` — each concrete border writes its own CSS (padding for `EmptyBorder`, `border` shorthand for `LineBorder`, per-side `border-top/right/bottom/left` for `MatteBorder`, `inset/outset/groove/ridge` for `BevelBorder`/`EtchedBorder`). `AbstractBorder.clearBorderCss(Element)` is called before each install so a previous border's keys don't leak across swaps. Custom Borders that extend `Border` directly round-trip through `getBorder()` but don't render — a WARN surfaces the gap so migrators know.

`TitledBorder` renders the inner border via `applyCss` and tags the element with `data-vaadinx-titled-border-title="<title>"` + `position: relative`. The visible legend label is produced by a theme CSS rule that picks the attribute up via `attr()`:

```css
[data-vaadinx-titled-border-title]::before {
    content: attr(data-vaadinx-titled-border-title);
    position: absolute; top: -0.6em; left: 12px;
    background: var(--lumo-base-color, white);
    padding: 0 4px; font-size: 0.85em; line-height: 1;
}
```

`:sampler` ships this rule in its `webapp/styles.css` (used by the registration-form route); apps wanting a different look override it in their own theme. Injecting a DOM legend child would shift every peer's child index and fight `Container`'s children list; injecting a `<style>` tag per border would duplicate the rule across instances — the theme-attribute split keeps the library minimal (one data-attribute write per border) while letting theming stay in theming's domain.

## Actions, keybindings, and mnemonics

`AbstractButton.setAction(Action)` + `JTextField.setAction(Action)` follow JDK's shape: `configurePropertiesFromAction` copies Action values to the button/field (NAME/ACTION_COMMAND_KEY/MNEMONIC_KEY/SMALL_ICON/SHORT_DESCRIPTION/enabled for buttons; enabled/SHORT_DESCRIPTION/ACTION_COMMAND_KEY for text fields), a `PropertyChangeListener` on the Action keeps them in sync on later mutations, and click/Enter dispatch routes through the listenerList (which includes the Action, registered with an `isListener` dedup guard). `SHORT_DESCRIPTION` propagates through `JComponent.setToolTipText` which drives `com.vaadin.flow.component.shared.HasTooltip` on the peer.

`JComponent.registerKeyboardAction` / `getInputMap(int)` / `getActionMap` / `setInputMap(int, InputMap)` / `setActionMap(ActionMap)` are all wired to `javax.swing.InputMap` + `javax.swing.ActionMap` instances (lazy per condition: `WHEN_FOCUSED` / `WHEN_ANCESTOR_OF_FOCUSED_COMPONENT` / `WHEN_IN_FOCUSED_WINDOW`). Each registration installs a `Shortcuts.addShortcutListener` on the peer, scoped per condition (no `listenOn` for `WHEN_IN_FOCUSED_WINDOW`, `listenOn(peer)` for the other two). `AbstractButton.setMnemonic(int)` auto-installs an `Alt+<key>` WHEN_IN_FOCUSED_WINDOW accelerator via the same path, so `Action.MNEMONIC_KEY` propagation through `configurePropertiesFromAction` lights up the accelerator for free. KeyStroke translation lives in `com.vaadin.swingbridge.surrogates.util.KeyConvert.toVaadinKeyBinding` (shared with `:surrogates` per surrogates SD_key_events) — covers letters, digits, F1–F12, common named keys; unmappable codes WARN and skip the browser install while still storing the InputMap entry so server-side queries keep working.

## Focus and InputVerifier

`JComponent.requestFocus()` / `requestFocus(boolean)` / `requestFocusInWindow()` / `requestFocusInWindow(boolean)` / `grabFocus()` delegate to `peer.focus()` when the peer implements `com.vaadin.flow.component.Focusable`. Non-focusable peers (plain Divs, etc.) WARN once. `JComponent.setInputVerifier(vaadinx.swing.InputVerifier)` stores the verifier and installs a peer `BlurNotifier` listener that calls `shouldYieldFocus(this)` on browser-originated blur — returning false, the listener re-calls `peer.focus()` to restore focus. `Container.setFocusTraversalKeys` is silent on an empty set (= browser-default Tab traversal, which is what we do natively) and WARNs only on non-empty (R_infra_not_surface: we don't model custom traversal keys yet).

`InputVerifier` is ported under `vaadinx.swing.InputVerifier` per D_event_port_policy — `verify(JComponent)` references Component, so it can't be reused unchanged.

## Sliders and spinners (bare-minimum per D_emulator_surrogate_split)

`JSlider` and `JSpinner` are the first components covered by the D_emulator_surrogate_split bare-minimum stance: enough surface to exercise the JDK-model-reuse bet (BoundedRangeModel / SpinnerNumberModel), full Swing coverage deferred until `:surrogates` ships the matching surrogates.

**`JSlider`.** Peer is `com.vaadin.flow.component.slider.IntegerSlider` (a stable Vaadin 25.2 component — the earlier `Slider` peer was gated on the `sliderComponent` feature flag, which was retired when the component graduated; no `vaadin-featureflags.properties` opt-in is needed). A JDK `DefaultBoundedRangeModel` (or a user-supplied `BoundedRangeModel`) is the source of truth; all int getters/setters delegate through it. A single `ChangeListener` attached to the model fans out to user-registered `ChangeListener`s (with `source=this`, so `(JSlider) e.getSource()` casts work) and pushes the current value to the peer under `preventPeerEvents`. The peer's `ValueChangeListener` mirrors browser-side drags back into the model via `EHelper.callSwing`; the resulting model event re-enters the same listener, hits the guard, and only the Swing-side fan-out runs. Min/max push through `IntegerSlider`'s public `setMin`/`setMax`. Vertical orientation, tick/label painting, inverted rendering, and `Dictionary` label tables are stubbed: fields round-trip and fire `PropertyChangeEvent`s, setters log `onUnimplemented` only when the gap would be visible (e.g. `setPaintTicks(true)`), so the common horizontal case stays silent.

**`JSpinner`.** Peer is `com.vaadin.flow.component.textfield.IntegerField` with `stepButtonsVisible=true` so the up/down chrome reaches the browser. Only `SpinnerNumberModel` seeded with Integer value + Integer stepSize is supported — `getNextValue` / `getPreviousValue` / `setValue` all delegate to the model unchanged, validating the "reuse JDK models unchanged" bet for the spinner case. Null min/max on the model map to `Integer.MIN_VALUE` / `Integer.MAX_VALUE` on the peer (IntegerField has no unbounded sentinel; R_best_effort_behaviour best-effort). Anything outside the supported lane — `SpinnerListModel`, `SpinnerDateModel`, a `SpinnerNumberModel` with Double/Long value or step, a `setValue(non-Integer)` call — throws `UnsupportedOperationException` at the boundary with a message pointing the migrator at either a compatible model shape or the `:surrogates` escape hatch (D_emulator_surrogate_split). The editor surface (`getEditor` / `setEditor` / `createEditor` / `commitEdit`) stays as `onUnimplemented` stubs — full editor chrome needs `JFormattedTextField` underneath and the editor variant classes (`JSpinner.NumberEditor` / `DateEditor` / `ListEditor`) are explicit `:surrogates` territory.

**Shared R_swing_is_truth shape.** Both components follow an identical peer↔model sync: single `ChangeListener` attached to the JDK model in `installModel`, `fanOut` re-sources Swing `ChangeEvent`s (source=emulator), `preventPeerEvents` guards against the feedback loop when the listener's peer push triggers the peer's `ValueChangeListener` synchronously. `setModel` tears down the old subscription before installing the new one and pushes the new model's bounds/value through the same path.

## Stub generator

Standalone Maven module `:generator` (D_generator_own_module — lives outside `:emulators` so it still runs when `:emulators` won't compile). Invoked via:

```
./mvnw -C -pl generator compile exec:java -Dexec.arguments="<fully.qualified.Class>,<output-dir>"
```

What it does:

1. Reflects over a target JDK class listed in `MAPPING`.
2. Enumerates `getDeclaredConstructors()` and `getDeclaredMethods()`, filtering to public/protected, dropping synthetic/bridge.
3. Emits a `vaadinx.*` skeleton with identical signatures, each body forwarding to `vaadinx.EHelper.onUnimplemented(...)` and returning a sensible default.

Key mechanisms:

- **Scope: Component-hierarchy only.** The generator is designed for `Component` / `Container` / `JComponent` descendants. Non-Component-hierarchy ports (`FlowLayout`, `BorderLayout`, other LayoutManagers) are hand-written — the root-class detection would emit a `peer` field on any class whose JDK superclass isn't in `MAPPING`, which is exactly wrong for a LayoutManager. Adding such classes to `MAPPING` is still useful for cross-class signature rewriting, just don't regenerate them.
- **Whitelist (`MAPPING`).** Only classes listed there get ported; every other type reference in signatures is preserved as the original JDK FQN. Keeps the surface small and predictable (D_whitelist_porting).
- **`SKIP_INTERFACES` — empty.** The set drops a JDK interface from emitted `implements` clauses, for the case where its method signatures require a `java.awt.Component` / `Container` / `Window` type a `vaadinx.*` class can't satisfy. **It is a last resort, not a convenience, and nothing is in it:** the right answer to that case is to *port* the interface and add a `MAPPING` row, so the emitted clause names the port. Every member it ever held was a mistake. `MenuContainer` and `ImageObserver` named no mapped type at all, and ImageObserver's absence cost all 64 Component-derived emulators their eight image flags — a dropped interface takes its constants with it, so the emulator stops compiling the migrator's `Xxx.CONSTANT` (D_missing_constants, gated by `PublicConstantsTest`). `javax.swing.RootPaneContainer` *did* name mapped types and was still wrong: it is `vaadinx.swing.RootPaneContainer` now, implemented by `JFrame` / `JDialog` / `JWindow` / `JInternalFrame`, and its absence had bent `SwingUtilities.getRootPane` into an `instanceof` chain that answered `null` for a `JFrame` (D_hierarchy_parity). A dropped interface carrying no constants is invisible to the constants gate; `TypeHierarchyParityTest` is what sees it.
- **Package-private interface filter.** Non-public interfaces (e.g. `TransferHandler.HasGetTransferHandler`) are silently dropped — they can't be implemented from outside their package anyway.
- **Root-class detection.** When the target's JDK superclass isn't in `MAPPING` (i.e., `java.awt.Component` extending `Object`), the generator emits the `peer` field on the class itself and stores the argument directly in the protected peer ctor (`this.peer = peer`) instead of forwarding to `super`. Every other mapped class chains to super as normal.
- **Dropped signatures.** Methods/ctors referencing `java.awt.peer.*` or nested-class types (names containing `$`) are skipped. Errs on the side of dropping; humans hand-add if needed.

Generated output is a starting point. Hand-implement methods the current slice needs. Running the generator again overwrites the output — helpers live in `vaadinx.EHelper`, not in the generated class, so nothing is lost.

## Testing

- Maven module `:emulators` — library (`src/main/java`, pure Java).
- Tests: Karibu-Testing + JUnit, in `src/test/java` (Karibu's `LocatorJ` static API) — R_java_karibu_tests. Karibu mounts Vaadin routes and drives UI state directly; since every `vaadinx.awt.Component` exposes a real Vaadin peer, Karibu's lookups work through the peer.
- `AbstractKaribuTest` sets up `MockVaadin` before each test and tears down after; test classes extend it. Smoke tests simply instantiate the component under `MockVaadin`.

### Reaching Karibu from Java

Karibu-Testing is a Kotlin library and most of its documentation shows the extension-function DSL. Every function this repo uses has an idiomatic Java form, audited against karibu-testing-v24 2.7.2 with `javap` (D_kotlin_retired):

- **`LocatorJ` is the head of the API** and covers the common lookups directly: `_get` / `_find` / `_click` / `_setValue` / `_fireValueChange`, plus the assertions under their Java names — `_expectNone` → `_assertNone`, `_expect` → `_assert`, `_expectEditableByUser` → `assertEditableByUser`. The Kotlin search-spec lambda maps onto a `Consumer<SearchSpecJ<T>>`: `_get<Button> { text = "OK" }` is `LocatorJ._get(Button.class, spec -> spec.withText("OK"))`.
- **The tail is callable as plain statics**, because a Kotlin top-level function compiles to a static on `<File>Kt`: `GridKt._clickItem` / `_doubleClickItem` / `_getCellComponent` / `_getRootItems` / `_select` / `_rowSequence`, `UploadKt._upload`, `DownloadKt._download`, `BasicUtilsKt._fireEvent`. Defaults survive — those files are `@JvmOverloads`-annotated, so `GridKt._clickItem(grid, 0)` works.
- **A Kotlin `object` singleton is plain statics**: `KaribuConfig.getWindowName()`, `MockBrowser.getCurrentWindowName()`. The `INSTANCE` field also compiles but JDT flags it.
- **Two shapes need care.** `@JvmOverloads` drops only *trailing* parameters, so a Kotlin call that names a later argument and defaults an earlier one has no Java overload at all (`MockBrowser.newTab(path = …)` — pass the window name explicitly). And `Routes(routes = setOf(…))` is the 3-arg ctor `(routes, errorRoutes, skipPwaInit)` from Java.
- **One function has no Java-shaped return.** `TreeGrid._rowSequence()` hands back a `kotlin.sequences.Sequence`; `SequencesKt.toList(GridKt._rowSequence(tree, null))` materialises it. Filed upstream as [karibu-testing#214](https://github.com/mvysny/karibu-testing/issues/214).
