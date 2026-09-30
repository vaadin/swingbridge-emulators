# `:emulators`

Runtime emulation of the Swing API on top of Vaadin 25. The library a migrating Swing app compiles against once `javax.swing.*` imports are rewritten to `vaadinx.swing.*` — every emulator class is a thin Swing-shaped shell over a real Vaadin component (its *peer*).

This is the **stage-2 API** in the migration arc described in [`../CLAUDE.md` §"Migration arc"](../CLAUDE.md): mechanical import-swap, recompile, the app runs in a browser, code shape stays Swing-shaped. Stage-3 (`:surrogates`) is opt-in per view once you want Vaadin-shaped code with Swing-flavoured helpers.

## Depend

Maven:

```xml
<dependency>
    <groupId>com.vaadin.swingbridge</groupId>
    <artifactId>swingbridge-emulators</artifactId>
    <version>&lt;version&gt;</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("com.vaadin.swingbridge:swingbridge-emulators:<version>")
}
```

`:emulators` pulls `:surrogates` (api) and the blocking-dialog machinery, [vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs) (implementation), transitively. All of it is pure Java with no Kotlin on the consumer classpath ([R_pure_java_library](../CLAUDE.md), [D_kotlin_retired](./decisions.md#D_kotlin_retired)).

**Bytecode targets Java 21, but the JVM must be 24+.** The 21 target is there so your existing sources keep compiling unchanged; the runtime floor is higher because a modal dialog's virtual thread may park inside a `synchronized` region, which pins the carrier thread before [JEP 491](https://openjdk.org/jeps/491) (JDK 24) and deadlocks. Apps that exercise modal dialogs (`JOptionPane.showMessageDialog`, blocking `JDialog`, `SwingUtilities.invokeAndWait`) also need `--add-opens java.base/java.lang=ALL-UNNAMED` at runtime: the virtual-thread runner reaches one JDK-internal constructor to run its threads on Vaadin's request thread ([D_callswing_loom](./decisions.md#D_callswing_loom)).

## Use

Three steps, one-time per app:

1. Rewrite imports: `javax.swing.*` → `vaadinx.swing.*`, `java.awt.*` Component-hierarchy imports → `vaadinx.awt.*`. Data types (`Color`, `Font`, `Dimension`, `ActionEvent`, `ActionListener`, `Action`, models) stay on the JDK — see [architecture.md §"Events: reuse vs. port"](./architecture.md#events-reuse-vs-port) and [D_whitelist_porting](./decisions.md).
2. Split your `main()` into a pre-UI half (services, db, `VaadinBoot.create().run()`) and a per-UI `mainUI()` half that constructs the main `JFrame`. Annotate the main frame with `@vaadinx.swing.MainWindow` and paste the `MainWindowRoute` scaffold — full recipe in [architecture.md §"Main window route"](./architecture.md#main-window-route).
3. Recompile against `:emulators` and run inside a Vaadin 25 app shell with `@Push` on the `AppShell`.

Import-swap, not binary-drop-in — you need source access for any Swing dependency you compile in. See [D_import_swap_target](./decisions.md).

## Component surface

`JFrame` + `JDialog` + `JWindow` + `JOptionPane` + `JColorChooser` + `JFileChooser` + `JButton` + `JPanel` + `JLabel` + `JTextField` + `JPasswordField` + `JTextArea` + `JFormattedTextField` + `JEditorPane` + `JTextPane` + `JScrollPane` + `JToolBar` + `JSplitPane` + `JTabbedPane` + `JCheckBox` + `JToggleButton` + `JRadioButton` + `JSlider` + `JSpinner` + `JProgressBar` + `JComboBox` + `JMenuBar` + `JMenu` + `JMenuItem` + `JCheckBoxMenuItem` + `JRadioButtonMenuItem` + `JPopupMenu` + `JSeparator` + `JTable` + `JList` + `JTree` + `JDesktopPane` + `JInternalFrame` + `Box` + `Box.Filler` + `JScrollBar` + `JViewport` + `FlowLayout` + `BorderLayout` + `BoxLayout` + `GridLayout` + `GridBagLayout` + `GroupLayout`, the AWT 1.0 widgets `Button`, `Label`, `Choice`, `Checkbox` + `CheckboxGroup`, `Panel`, `Scrollbar`, `List`, `ScrollPane` and `FileDialog`, plus border / focus / mnemonic / keyboard-action / `setAction` / tooltip plumbing. Concurrency primitives: `Timer`, `SwingWorker`, `SwingUtilities`. The per-component caveats live with the list in [`../CLAUDE.md` §"Current scope"](../CLAUDE.md#current-scope).

Cross-cutting platform capabilities: clipboard (`vaadinx.awt.Toolkit` — [D_clipboard](./decisions.md)), drag-and-drop (`vaadinx.swing.TransferHandler` — [D_drag_and_drop](./decisions.md)), and `java.util.prefs.Preferences` — fully supported with zero code change (no import swap; plugged in under the JDK `PreferencesFactory` SPI, backed by the browser's `localStorage` — [D_preferences](./decisions.md#D_preferences)).

Everything outside the supported surface is stubbed: a method call logs WARN via `vaadinx.EHelper.onUnimplemented` and returns a sensible default — never throws. Programming errors that real Swing throws on (bad index, orphan-component state, unsupported `SpinnerModel` shape, …) we throw on too, with the same exception type. See [`../CLAUDE.md` §"Hard rules" R_match_swing_errors](../CLAUDE.md) and [D_gap_severity_triage](./decisions.md).

A typical CRUD app is the calibration target for what counts as an acceptable gap.

## See also

- [architecture.md](./architecture.md) — package layout, peer field, setter pattern, layouts, threading, main-window route, borders, actions/keybindings/mnemonics.
- [decisions.md](./decisions.md) — decision log with rationale and accepted limitations (`D_import_swap_target` … `D_jframe_as_route`).
- [`../surrogates/README.md`](../surrogates/README.md) — the stage-3 API for views you've finished rewriting.
- [`../sampler/README.md`](../sampler/README.md) — browser-runnable demo of every supported component.
- [`../CLAUDE.md`](../CLAUDE.md) — project rules, current scope.
