# Architecture

`:surrogates` holds classes that extend Vaadin components directly to reproduce full Swing component behaviour — the is-a side of the split. `:emulators` is the has-a side: it reproduces the Swing API surface faithfully on Vaadin peers, and uses a surrogate as its peer for any component `:surrogates` has covered. Per [D_surrogate_first](../emulators/decisions.md#D_surrogate_first), new emulator components wait for their surrogate to land here first; `:surrogates` drives its own ordering independently. See [D_emulator_surrogate_split](../emulators/decisions.md#D_emulator_surrogate_split) for the original split rationale, [D_surrogate_first](../emulators/decisions.md#D_surrogate_first) for the current ordering policy, and [SD_vaadin_first_binding](./decisions.md#SD_vaadin_first_binding) for the module's operative Vaadin-first stance.

## Handoff contract with `:emulators`

Once `:surrogates` ships a surrogate for component X, the matching emulator switches its peer from the bare Vaadin component it currently uses (e.g. `IntegerField` for `JSpinner`) to the surrogate (e.g. a Spinner-surrogate extending `IntegerField`). Two constraints keep this drop-in:

1. **Surrogate extends the emulator's current peer type, or a compatible supertype.** `:emulators`' peer constructors take `com.vaadin.flow.component.Component`; a surrogate extending `IntegerField` is-a `Component` and satisfies any peer-specific calls the emulator makes (e.g. `setStepButtonsVisible`) without the emulator needing to reshape.
2. **Swing behaviour is *default* behaviour, not an opt-in mode.** A surrogate used as a plain Vaadin component (without `:emulators` on top) renders and behaves like a richer Vaadin component; used as a peer, it also closes the emulator's bare-minimum gaps. "Richer Vaadin component" covers both leaf primitives (e.g. `SJSlider extends Slider` directly) and composites (e.g. a surrogate extending `CustomField` with a swappable inner field) — whichever shape most faithfully matches the Swing class's contract. Some Swing APIs (JSpinner's runtime `setModel` across Integer/Double/Date/List) can't be reproduced on a single leaf primitive and naturally land as composites. No flag toggling either way.

Architectural direction is one-way: `:emulators → :surrogates`. A surrogate has no compile-time visibility of `vaadinx.*` and carries no awareness of the Swing API surface above it. The handoff works precisely because of this discipline.

## Package layout

```
com.vaadin.swingbridge.surrogates           — concrete surrogates (SWindow, SFrame, SJSlider, SJSpinner, …) + SHelper
com.vaadin.swingbridge.surrogates.awt       — mixins for java.awt.* (ComponentMixin, ContainerMixin)
com.vaadin.swingbridge.surrogates.awt.event — ported AWT events whose source is a Vaadin Component
com.vaadin.swingbridge.surrogates.swing     — mixins for javax.swing.* (JComponentMixin)
com.vaadin.swingbridge.surrogates.internal  — state-holder classes attached via ComponentUtil.setData
```

Mixins and ported events mirror their Swing-source package position: `com.vaadin.swingbridge.surrogates.awt.ComponentMixin` ↔ `java.awt.Component`, `com.vaadin.swingbridge.surrogates.awt.event.SComponentEvent` ↔ `java.awt.event.ComponentEvent`, `com.vaadin.swingbridge.surrogates.swing.JComponentMixin` ↔ `javax.swing.JComponent`. Concrete surrogates stay flat at `com.vaadin.swingbridge.surrogates.*` — the `S` / `SJ` prefix already encodes AWT-vs-Swing origin (`S` for `java.awt.*` sources like `SFrame`, `SJ` for `javax.swing.*` sources like `SJFrame` / `SJSlider` / `SJSpinner` / `SJRootPane`). Naming conventions live in [SD_naming](./decisions.md#SD_naming).

## The mixin mechanism

A shared base class is impossible — surrogates extend different Vaadin classes and Java has no multiple inheritance. Mixin interfaces with default methods give the same composability without that constraint. A concrete surrogate writes `class SButton extends Button implements ComponentMixin {}` and inherits the full Swing API surface for free. See [SD_mixin_mechanism](./decisions.md#SD_mixin_mechanism) for the mechanism rationale and the mixin class javadoc (`ComponentMixin`, `ContainerMixin`, `JComponentMixin`) for per-method implementation choices.

`ComponentMixin extends HasEnabled, HasSize` — Vaadin's `Component` is an abstract class and can't be extended by an interface; `HasEnabled` + `HasSize` are the closest interface constraints. Both transitively extend `HasElement`, giving default methods access to `getElement()`. Nearly all Vaadin components implement both; surrogates of classes that don't can't implement `ComponentMixin`.

Java prefers a class's concrete method over an interface default, so when a Vaadin component already provides a Swing-shape method (e.g. `setVisible(boolean)`), nothing changes. The mixin's default kicks in only for Swing-named methods Vaadin doesn't have (`setName`, `addPropertyChangeListener`, …) or for `instanceof`-gated delegations to Vaadin capability interfaces (`HasTooltip`, `Focusable`).

## State holders

Mixin default methods can't hold fields, so stateful concerns live in dedicated holder classes under `com.vaadin.swingbridge.surrogates.internal`, attached via `ComponentUtil.setData` and looked up lazily through a static `Holder.of(target)` accessor. Under SD_vaadin_first_binding these survive only for concepts Vaadin genuinely doesn't have.

| Holder | Purpose |
|---|---|
| `PceSupport` | `add/remove/firePropertyChangeListener` — JDK `PropertyChangeSupport` delegate, no Vaadin analog. SD_auto_pce auto-fires PCE on mapped setters, so the listener storage has Vaadin-driven producers behind it (the surrogate's own setter pipeline). |
| `NameStore` | `setName/getName` — Swing `name` ≠ Vaadin `id` (DOM/CSS effects diverge). |
| `LayoutStore` | `setLayout/getLayout` on `ContainerMixin` — stored inert, no Swing-LayoutManager analog. |
| `ClientPropertyStore` | `putClientProperty/getClientProperty` on `JComponentMixin` — pure Swing concept. |
| `InputMapStore` | Three lazy-alloc `InputMap`s + one `ActionMap` for `JComponentMixin.getInputMap(condition)`. Browser-side shortcut wiring deferred. |
| `InputVerifierStore` | Single replaceable slot for `JComponentMixin.setInputVerifier`'s installed `InputVerifier` plus the matching `BlurNotifier` `Registration`. Atomic swap-and-teardown so verifier replacement doesn't stack blur dispatches. |
| `Registrations` | Unified `Map<EventListener, Registration>` for every AWT listener family that wires a Vaadin subscription (SD_vaadin_first_binding). |

## API partition

Every mixin method sits in one of three buckets per [SD_api_three_buckets](./decisions.md#SD_api_three_buckets):

- **Bucket A — implemented**: routes through Vaadin (or a store when no Vaadin analog). The mixin class javadoc lists the concrete membership per surface.
- **Bucket B — deliberate no-op** via `SHelper.onNoop`: redundant by design under Vaadin's render model (paint pipeline, `validate`/`revalidate`, AWT peer lifecycle, L&F dispatch). DEBUG-level, doesn't feed `warnHook`.
- **Bucket C — unimplemented stub** via `SHelper.onUnimplemented`: behaviour we'd need to build (location, mouse + input-method listeners, `registerKeyboardAction`, focus traversal). WARN-level, feeds `warnHook` so testapp-level inventory gates catch regressions.

A fourth category — **excluded entirely** — exists only for signature clashes with Vaadin's hierarchy API (`getParent`, `add(Component)`, `getComponents()`, `isAncestorOf(Component)`, `JComponent.getUI()`). Vaadin's wins because the surrogate *is* a Vaadin component.

`com.vaadin.swingbridge.surrogates.SHelper` mirrors `vaadinx.EHelper` locally (per [SD_shelper_statics](./decisions.md#SD_shelper_statics) — module direction forbids the reverse dependency); same WARN/DEBUG contract, same never-throw guarantee.

## Events: reuse vs. port

JDK events whose source is typed as `Object` (`ActionEvent`, `ChangeEvent`, `PropertyChangeEvent`) are reused unchanged — a Vaadin `Component` is a valid source. JDK events whose constructor demands `java.awt.Component` are ported to `com.vaadin.swingbridge.surrogates.awt.event.S*` with the source retyped to `com.vaadin.flow.component.Component`. Their **constant surfaces are identical to the JDK's and are not written out here**: each constant delegates to its counterpart (`VK_A = java.awt.event.KeyEvent.VK_A`) and the whole block is emitted by `DelegatedConstantsTest`, so a new port adds itself to that test's target list rather than transcribing a table — see [SD_event_port_stance](./decisions.md#SD_event_port_stance).

The current port set is what's in `com.vaadin.swingbridge.surrogates.awt.event/`. Port stance: eager when a slice wires firing, lazy otherwise — see [SD_event_port_stance](./decisions.md#SD_event_port_stance). `SKeyEvent` + `SInputEvent` port per [SD_key_events](./decisions.md#SD_key_events); `SWindowEvent` + `SWindowListener` / `SWindowFocusListener` / `SWindowStateListener` / `SWindowAdapter` port alongside `SFrame` per [SD_sframe](./decisions.md#SD_sframe); `SMouseEvent` + `SMouseListener` / `SMouseAdapter` port alongside the partial `addMouseListener` wire on Vaadin `ClickNotifier` (`MOUSE_CLICKED` only — motion / wheel / press / release / enter / exit have no Vaadin counterpart and stay drop-and-WARN with documented gap).

## Shadow-root injection (styling and eventing shadow-DOM content)

A Vaadin component that renders *our* content into its own shadow root cannot be
reached from outside it: `::part(x) h2` styles the part element, never its shadow
descendants, and a click inside retargets so a host-level listener sees the host as
`event.target`. Both are solved by going *into* the shadow root — inject a
`<style>` element for per-instance content CSS (already confined to that instance;
no `theme`-attribute scoping needed), and attach delegated listeners there, or read
the real target off `composedPath()`. Injection must be re-applied on every attach,
since Vaadin re-renders a `@PreserveOnRefresh` tree after F5 without replaying
`executeJs`. Both halves are Karibu-invisible and must be browser-verified.

`SJEditorPane` is the worked example — [SD_add_css_rule](./decisions.md#SD_add_css_rule)
(styling, incl. the selector rewriting that confines rules to the content element)
and [SD_hyperlink_listener](./decisions.md#SD_hyperlink_listener)
(events).

## Testing

Karibu-Testing — every test is Java, under `src/test/java` (project R_java_karibu_tests). Karibu sees the surrogate directly as the Vaadin component it extends — no `peer` field to reach through. Two throwaway test surrogates (`TestSurrogate extends Button implements ContainerMixin`, `TestJSurrogate extends Button implements JComponentMixin`) exercise the mixin mechanism until `:emulators`' D_emulator_surrogate_split seeds the first concrete one.

## Build dependency note

`jakarta.servlet:jakarta.servlet-api` is `compileOnly` because `VaadinSession`'s interface (used by `JComponentMixin`'s default-locale statics) transitively references `jakarta.servlet.http.HttpSessionBindingListener`. Provided by the servlet container at runtime.
