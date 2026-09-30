# `testapps/crud`

A small employee-CRUD app: a table of employees with a preview pane, an edit dialog, a menu bar and
a status bar. Hand-written for this project as the smallest app that touches every component a
typical business Swing app uses, so it is the first thing to migrate and the worked example the
migration guide points at.

## Stages

One subdirectory per stage of the migration, so the stages sit side by side and the diff between
two neighbours is exactly what that step of the migration changes:

- [`swing/`](./swing/) — **stage 1**, the pure-Swing desktop app. No SB-Emulators dependencies. This is the
  starting point a migration begins from.
- [`1-emulators/`](./1-emulators/) — **stage 2**, the same source after the `javax.swing.*` →
  `vaadinx.swing.*` import-swap and the entry-point split, compiled against `:emulators` and served
  in a browser. The body of every view is character-for-character identical to `swing/`; only the
  import lines and the entry point differ. What tripped the migration is logged in
  `1-emulators/STUMBLES.md` in this repository; the distribution kit does not ship it.
- `surrogated/` — **stage 3**, the view-by-view rewrite onto `:surrogates`. *(Not landed yet.)*

## What it exercises

`MainFrame` is a list / detail / edit-dialog CRUD shell: a `JTable` of employees with a row sorter
and a selection-driven preview pane, a toolbar with `JButton` / `JToggleButton` filter, a full
`JMenuBar` (mnemonics, accelerators, `JCheckBoxMenuItem`, a `JRadioButtonMenuItem` group,
`JSeparator`), a status bar, a modal `EmployeeEditDialog` over `JDialog` with `JTextField` /
`JPasswordField` / `JTextArea` / `JCheckBox` / `JComboBox` / `JSpinner` (number and date) /
`JSlider`, and `JOptionPane.showMessageDialog` / `showConfirmDialog`. Layouts: `BorderLayout`,
`BoxLayout`, `GridBagLayout`, with `JScrollPane` around the table.

The happy-path journeys through the app, written so a person or an agent can follow them step by
step, are in [`swing/test.md`](./swing/test.md). They start from the seeded data and apply to
either stage.

## Run before migration

From this app's `swing/` directory. The pure-Swing app has no SB-Emulators dependencies, so nothing
needs to be installed first:

```
cd swing
./mvnw -C compile exec:exec
```

Opens a desktop window titled `Employees — Swing CRUD`, pre-seeded with four employees. Needs a
display and JDK 21+.

To attach a Java agent (for example [swing-mcp](https://github.com/vaadin/swing-mcp), which exposes
the running app's accessibility tree to an agent without any source change), build the runnable jar
and launch it directly, so the agent lands on the app JVM only and not on Maven:

```
cd swing
./mvnw -C package
java -javaagent:/absolute/path/to/agent.jar -jar target/testapp-crud-swing-1.0-SNAPSHOT.jar
```

Avoid `JAVA_TOOL_OPTIONS` for this: the JDK reads it on every JVM start, so the agent would end up
in Maven and its forked workers too.

## Run after migration

From this app's `1-emulators/` directory. The migrated app resolves SB-Emulators by coordinate, so it
needs either a released version in a repository Maven can reach, or a local `./mvnw -C clean
install` in an SB-Emulators checkout to put the matching SNAPSHOT into `~/.m2`:

```
cd 1-emulators
./mvnw -C spring-boot:run
```

Starts an embedded Tomcat on port 8080. Open <http://localhost:8080>. The same four employees are
seeded. Needs JDK 24+ at runtime.

To build and launch the executable jar instead — an ordinary `package`, with no profile to
remember, because `build-frontend` sits in the default build the way Vaadin's own Spring Boot
starter has it:

```
cd 1-emulators
./mvnw -C clean package
java -jar target/testapp-crud-1-emulators-1.0-SNAPSHOT.jar
```

The `--add-opens java.base/java.lang=ALL-UNNAMED` flag is not optional: the virtual-thread executor
behind every blocking modal dialog reflects into `java.base/java.lang`, and without it the app
refuses to start at all, with an `InaccessibleObjectException` naming that package. Neither line
above spells it, because both carry it for you: `./mvnw -C spring-boot:run` through the pom's
`<jvmArguments>`, and the jar through an `Add-Opens` entry in its manifest, which `java -jar`
honours. A `java -cp` launch ignores the manifest, so there the flag goes on the command line.

## Sign in

No sign-in. The app opens straight on the employee table.

## Known quirks the port must keep

None known. The app was written to be unremarkable: if something looks wrong after migration, it is
the migration, not the app.

## See also

- [`swing/test.md`](./swing/test.md) — the happy-path journeys, usable as a before/after checklist.
- **The Migration Guide** — this app is its worked example. Linked from wherever you found this
  app (the repository README or the distribution's welcome README); nothing here links upward.
- The sibling testapps `jlawyer-shape` (a broader hand-built one) and `inventory` (an adopted
  third-party one) follow the same staging convention.
