# `testapps/inventory`

An inventory-management app — items, categories, vendors, stock queries, transfers between branch
offices, sales, returns — on Hibernate with an embedded H2 database. **Written by a third party who
had never heard of this project**, and adopted here unchanged apart from a seed that fills the
database on first launch: upstream is
[`gtiwari333/java-inventory-management-system-swing-hibernate`](https://github.com/gtiwari333/java-inventory-management-system-swing-hibernate).
Being real code written for the desktop, it brings what a hand-built demo cannot: a service layer,
a database, two third-party Swing libraries, and the habits of an app that never expected to run on
a server.

## Stages

One subdirectory per stage of the migration, so the stages sit side by side and the diff between
two neighbours is exactly what that step of the migration changes:

- [`swing/`](./swing/) — **stage 1**, upstream's desktop app. This is the starting point a
  migration begins from, and it is read-only: the migration must cope with the app as it is.
- `1-emulators/` — **stage 2**, the same source after the `javax.swing.*` → `vaadinx.swing.*`
  import-swap and the entry-point split, compiled against `:emulators` and served in a browser. What
  tripped the migration is logged in its `STUMBLES.md`. *(Named rather than linked: this app travels
  stage 1 only in the distribution kit, where a link here would be a dead one — the kit's worked
  example is the sibling `crud`, which ships both.)*
- `surrogated/` — **stage 3**. *(Not landed yet.)*

## What it exercises

Sixty-five source files and about ten thousand lines: three and a half times the size of
`jlawyer-shape`, and a different kind of app. The screens are forms and grids — `JPanel`, `JLabel`,
`JTextField` and `JButton` by the hundred, `JTable` with the app's own cell renderers and editors,
`JSplitPane`, `JScrollPane`, `JComboBox`, `JRadioButton`, `JCheckBox`, `JPasswordField`,
`JTextArea`, `JFileChooser`, `SwingWorker`, and around fifty `JOptionPane` call sites, which makes
it the densest exercise of modal dialogs of the three apps. The main window swaps function panels
in and out of one frame rather than using tabs; there is no `JTree` and no drag-and-drop.

Two third-party Swing libraries are in use and both must be dealt with during the migration:
[JGoodies Forms](https://www.jgoodies.com/freeware/libraries/forms/) (`FormLayout`, the app's
primary layout, in thirteen files) and [JCalendar](https://toedter.com/jcalendar/) (`JDateChooser`,
in six). The remaining dependencies — Hibernate, H2, the `jxl` Excel writer, commons-lang — have no
UI in them and keep working server-side.

## Run before migration

From this app's `swing/` directory. The app has no SB-Emulators dependencies, so nothing needs to be
installed first:

```
cd swing
./mvnw -C test-compile exec:exec
```

Opens the login window. Needs a display and JDK 21+. The database is **in-memory H2** and is seeded
on start with a handful of categories, vendors and items through the app's own service layer; the
seed prints the row counts on every launch and skips itself when it finds data. The app writes its
log directory relative to the working directory, which the run is configured to keep under
`swing/target/run/`.

For a database that survives a restart, run against **file-backed H2** instead:

```
./mvnw -C clean test-compile exec:exec -Pfile-db
```

`clean` is not optional: a file-backed configuration left in `target/test-classes` by an earlier run
keeps shadowing the in-memory one after the profile is gone. Hibernate logs which one it opened on
every start, as `at URL [jdbc:h2:mem:inventory…]` or `at URL [jdbc:h2:./inventoryApp.db…]`.

To attach a Java agent (for example [swing-mcp](https://github.com/vaadin/swing-mcp)), pass it
through `exec.args`; `%classpath` keeps the classpath the plugin computed:

```
./mvnw -C exec:exec \
  -Dexec.args="-javaagent:/path/to/swing-mcp-agent.jar -classpath %classpath com.ca.ui.Main"
```

## Run after migration

From this app's `1-emulators/` directory. The migrated app resolves SB-Emulators by coordinate, so it
needs either a released version in a repository Maven can reach, or a local `./mvnw -C clean
install` in an SB-Emulators checkout to put the matching SNAPSHOT into `~/.m2`:

```
cd 1-emulators
./mvnw -C test-compile exec:exec
```

Starts an embedded Jetty and prints the port. Open <http://localhost:8080>. The same seed runs and
the same `-Pfile-db` profile exists. Needs JDK 24+ at runtime.

## Sign in

**`ADMIN` / `ADMIN`.** The app creates that user itself whenever no user exists, which on the
in-memory database is every launch. Change it through Tools → Change UserName/Password if you want
to exercise that screen.

## Known quirks the port must keep

The app is ported as it is, bugs included; a migration is judged on whether the browser app behaves
like the desktop app, not on whether it improved it. One quirk is worth knowing in advance because
diagnosing it teaches nothing about the migration:

- **Input validation never shows its status message.** `com.gt.uilib.inputverifier.Validator` takes
  a `Window parent` in all three constructors and assigns it in none, so the "Please enter data
  properly before saving" message has never appeared, on the desktop either. Port it exactly as it
  stands; do not fix it.

Anything else you find is either the app's own and to be kept, or the migration's and to be fixed.

## See also

- **The Migration Guide** — linked from wherever you found this app (the repository README or the
  distribution's welcome README); nothing here links upward.
- The sibling testapps `crud` (the smallest hand-built one, and the guide's worked example) and
  `jlawyer-shape` (the hand-built one with the broader component surface) follow the same staging
  convention.
