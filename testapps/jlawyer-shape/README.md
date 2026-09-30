# `testapps/jlawyer-shape`

A case-management app shaped after [j-lawyer](https://www.j-lawyer.org/): a tree of case folders,
each case open in its own tab with a document list, a notes preview and a properties dialog, on a
hand-built in-memory backend. Hand-written for this project to exercise the longer-tail Swing
components that a small CRUD app never reaches, without the size or the EJB backend of the real
thing.

**Licence: AGPL-3.0**, and not by choice of shape. One real j-lawyer source file — the invoice
position panel — plus three of its icons are vendored here verbatim, so this app is a work based on
an AGPL-3.0 program and is conveyed under that licence as a whole. Everything around them is
Vaadin-authored, including the mock classes sitting in j-lawyer package names so that panel compiles
unmodified. The licence text is [`LICENSE`](./LICENSE); which file is whose, and what the licence
requires of anyone redistributing or hosting this app, is [`PROVENANCE.md`](./PROVENANCE.md). Note
this is *not* SwingBridge Emulators' own licence: the library is GPLv2 with the Classpath Exception,
and nothing here propagates to it.

## Stages

One subdirectory per stage of the migration, so the stages sit side by side and the diff between
two neighbours is exactly what that step of the migration changes:

- [`swing/`](./swing/) — **stage 1**, the pure-Swing desktop app. No dependencies outside the JDK.
  This is the starting point a migration begins from.
- `1-emulators/` — **stage 2**, the same source after the `javax.swing.*` → `vaadinx.swing.*`
  import-swap and the entry-point split, compiled against `:emulators` and served in a browser.
  *(Named rather than linked: this app travels stage 1 only in the distribution kit, where a link
  here would be a dead one — the kit's worked example is the sibling `crud`, which ships both.)*
- `surrogated/` — **stage 3**, the view-by-view rewrite onto `:surrogates`. *(Not landed yet.)*

## What it exercises

Beyond the CRUD baseline (`JFrame`, `JDialog`, `JOptionPane`, the `JMenuBar` family, `JButton`,
`JTextField`, `JTextArea`, `JCheckBox`, `JComboBox`, `JSpinner`, `JFormattedTextField`, `JLabel`,
`JPanel`, `JScrollPane`, `BoxLayout`, `BorderLayout`, `GridBagLayout`), which shows up across every
view, the app is built around the components a bigger desktop app leans on:

- `JTree` — the case folder hierarchy
- `JList` — the documents within a case
- `JSplitPane` — master/detail layout, horizontal and vertical nesting
- `JTabbedPane` — several cases open at once
- `JPopupMenu` — right-click context menus on the tree and the list
- `JFileChooser` — document upload
- `JToolBar` — the top action bar
- `JEditorPane` — HTML notes preview
- `JProgressBar` — status-bar feedback for simulated long operations
- standalone `JRadioButton` — the case priority selector in the properties dialog

Plus two cross-cutting capabilities, both taken from how the real app uses them:

- **Clipboard** — copy case ID, copy a document path hint, via `Toolkit.getSystemClipboard()` +
  `Transferable` + `StringSelection`.
- **Drag-and-drop** — drag a document from one case's list into another case's list, via
  `TransferHandler` and a custom `DataFlavor`.

Printing is deliberately not modelled.

## Run before migration

From this app's `swing/` directory. The pure-Swing app has no SB-Emulators dependencies, so nothing
needs to be installed first:

```
cd swing
./mvnw -C compile exec:exec
```

Opens a desktop window with a few seeded cases and documents. Needs a display and JDK 21+.

## Run after migration

From this app's `1-emulators/` directory. The migrated app resolves SB-Emulators by coordinate, so it
needs either a released version in a repository Maven can reach, or a local `./mvnw -C clean
install` in an SB-Emulators checkout to put the matching SNAPSHOT into `~/.m2`:

```
cd 1-emulators
./mvnw -C compile exec:exec
```

Starts an embedded Jetty and prints the port. Open <http://localhost:8080>. The same cases and
documents are seeded. Needs JDK 24+ at runtime.

## Sign in

No sign-in. The app opens straight on the case tree.

## Known quirks the port must keep

None known. The app was written to be unremarkable: if something looks wrong after migration, it is
the migration, not the app.

## See also

- **The Migration Guide** — linked from wherever you found this app (the repository README or the
  distribution's welcome README); nothing here links upward.
- The sibling testapps `crud` (the smaller hand-built one, and the guide's worked example) and
  `inventory` (an adopted third-party one) follow the same staging convention.
