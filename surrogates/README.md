# `:surrogates`

Vaadin component subclasses that reproduce the full UI functionality of their Swing counterparts. `SJButton extends Button`, `SJTextField extends TextField`, `SJSlider extends Slider`, `SFrame extends Dialog`, … — each one is-a Vaadin component that also fires the Swing events, drives the Swing model, and exposes Swing-flavoured helpers a migrator expects (mnemonics, `ButtonModel`, `setAction`, tooltips, borders).

This is the **stage-3 API** in the migration arc described in [`../CLAUDE.md` §"Migration arc"](../CLAUDE.md): once a view has been rewritten away from `vaadinx.swing.*` to direct Vaadin, `:surrogates` is what it imports. `:emulators` uses surrogates as its peers internally — same classes, two roles.

## Depend

Maven:

```xml
<dependency>
    <groupId>com.vaadin.swingbridge</groupId>
    <artifactId>swingbridge-surrogates</artifactId>
    <version>&lt;version&gt;</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("com.vaadin.swingbridge:swingbridge-surrogates:<version>")
}
```

Pure Java, no Kotlin on the consumer classpath ([R_pure_java_library](../CLAUDE.md), [D_kotlin_retired](../emulators/decisions.md#D_kotlin_retired)). Requires Vaadin 25, and a **JDK 24+ at runtime** even though the bytecode targets Java 21 — see [`../emulators/README.md` §"Depend"](../emulators/README.md#depend) for why the two differ.

If your app already pulls `:emulators`, you have `:surrogates` transitively (`api` dependency) — no separate declaration needed.

## Use

Two valid wiring shapes per [SD_vaadin_first_binding](./decisions.md):

**Use a surrogate directly as a richer Vaadin component.** Drop `SJButton` into a layout exactly like `Button`; the extra Swing surface (`addActionListener`, `setMnemonic`, `setAction`, `setToolTipText`, `setBorder`, `addPropertyChangeListener`) is available as inherited methods.

```java
SJButton ok = new SJButton("OK");
ok.addActionListener(e -> save());
ok.setMnemonic('O');
layout.add(ok);
```

**Subclass a surrogate** when you'd otherwise subclass the Vaadin component. Inheritance is the same shape as plain Vaadin; you get the Swing helpers for free.

A surrogate's behaviour is *default* behaviour, not an opt-in mode — there's no flag to flip and no "Swing mode" to enable. See [architecture.md §"Handoff contract with `:emulators`"](./architecture.md#handoff-contract-with-emulators).

## Component surface

`SFrame` + `SJFrame` + `SJDialog` + `SJButton` + `SJToggleButton` + `SJCheckBox` + `SJPanel` + `SJLabel` + `SJTextField` + `SJPasswordField` + `SJTextArea` + `SJSlider` + `SJSpinner` + `SJComboBox` + `SJMenuBar` + `SJTable` + `SJRootPane` + `SWindow`. Mixins for the inherited `Component` / `Container` / `JComponent` surface live under `com.vaadin.swingbridge.surrogates.awt` / `com.vaadin.swingbridge.surrogates.swing` — see the package layout in [architecture.md §"Package layout"](./architecture.md#package-layout).

What round-trips and what doesn't is governed by R_vaadin_first in [`../CLAUDE.md`](../CLAUDE.md): UI functionality is reproduced fully, but Swing API methods that have no Vaadin counterpart drop their value with a WARN — `setText(null)` may read back as `""`, `setBorder` round-trips lossy through CSS, etc. The surrogate is "fat in functionality, thin in API completeness" by design. New per-method gaps land via [SD_vaadin_first_binding](./decisions.md) and the per-class triage in [SD_border_css_lossy](./decisions.md)+.

Surrogate-first ordering policy ([D_surrogate_first](../emulators/decisions.md)) means new emulator components wait for a surrogate to land here first; `:surrogates` drives its own ordering.

## Test

```
./mvnw -C -pl surrogates test
```

Karibu-Testing reaches the surrogate as the Vaadin component it extends — no `peer` field to walk through (contrast `:emulators` tests, which reach through `peer`).

## See also

- [architecture.md](./architecture.md) — handoff contract with `:emulators`, mixin mechanism, state holders, API partition (Bucket A/B/C), events port stance.
- [decisions.md](./decisions.md) — decision log with rationale and accepted limitations (`SD_mixin_mechanism` … `SD_no_sjoptionpane`).
- [`../emulators/README.md`](../emulators/README.md) — the stage-2 API your views start on before rewriting to `:surrogates`.
- [`../sampler/README.md`](../sampler/README.md) — browser-runnable demo; surrogates render whenever the corresponding emulator is exercised.
- [`../CLAUDE.md`](../CLAUDE.md) — project rules (R_vaadin_first in particular for the Vaadin-first stance), current scope.
