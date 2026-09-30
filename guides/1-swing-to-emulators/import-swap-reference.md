# Import swap reference — what ports, what stays

Reference for [Phase 2](./guide.md#S_run_import_swap). **A tool-driven migration does not need to
read this page.** `import-swap` reads the swap table out of the jars on its classpath, so every
bucket below is a lookup it already does.

Two jobs bring you here:

- **Reviewing the tool's diff.** The buckets are surprising in places, and knowing *why*
  `GridBagLayout` moves while `GridBagConstraints` doesn't makes the diff readable.
- **Rewriting by hand**, if you are not running the tool.

**Everything below is the swap table written out in prose.**

If you *are* rewriting by hand, two traps first, because both are compile errors rather than warnings:

- **`List` and `Timer` collide.** `vaadinx.awt.List` vs `java.util.List`, and `vaadinx.swing.Timer` vs `java.util.Timer`. Two single-type imports of one simple name is a hard error (JLS 7.5.1). In a Swing app `Timer` is almost always the Swing one and `List` almost always the collection — but check per file.
- **Deleting a wildcard is the safe direction.** If you replace `import java.awt.*;` with explicit imports, a type you forget becomes `cannot find symbol`. If you *keep* the wildcard and add `vaadinx` imports beside it, a type you forget silently keeps resolving to the JDK class — a bug you find at runtime, not at compile time.

## What ports to `vaadinx.*`

Six families. The table is the rule; the notes under it are the places the rule surprises people.

| from | to |
|---|---|
| `javax.swing.*` | `vaadinx.swing.*` |
| `java.awt.Component` / `Container`, the window classes `Window` / `Frame` / `Dialog` / `FileDialog`, the layout managers `BorderLayout` / `FlowLayout` / `GridLayout` / `GridBagLayout` | `vaadinx.awt.*` |
| `java.awt.event.*` types that touch Component, plus their listeners and adapters | `vaadinx.awt.event.*` |
| `java.awt.GraphicsEnvironment` / `GraphicsDevice` / `GraphicsConfiguration` | `vaadinx.awt.*` |
| `java.awt.EventQueue`, `java.awt.Toolkit` | `vaadinx.awt.*` |
| `java.text.SimpleDateFormat`, `java.util.Calendar` / `GregorianCalendar` | `vaadinx.text.*` / `vaadinx.util.*` |

**`javax.swing` — the non-`J*` members are the ones a narrow regex misses.** `BorderFactory`, the
icon types `Icon` / `ImageIcon`, `Box` / `Box.Filler` and its static factories (`createVerticalStrut`,
`createHorizontalGlue`, …), `BoxLayout`, `ButtonGroup`, `SwingUtilities`, and the concurrency
primitives `Timer` / `SwingWorker` all port.

- Concrete `Icon` impls port with the rule, so `new ImageIcon(getClass().getResource(...))` fed into
  `JButton.setIcon` compiles unchanged — which is what GUI-builder forms emit.
- `SwingUtilities` brings the EDT primitives (`invokeLater`, `invokeAndWait`, `isEventDispatchThread`),
  the parent-walk helpers and the `Rectangle` math. `invokeAndWait` from the EDT throws, per the
  [throw contract](./runtime-contract.md).
- `Timer` and `SwingWorker` port **despite having no `J*` prefix**, unlike the same-shaped *models*,
  which stay JDK. Verify these two by name after any narrow-regex rewrite.

**`java.awt` — the window classes are the easiest to miss.** They usually arrive via
`import java.awt.*` rather than by name, and they are what a helper's signature takes:
`new Validator(Window parent)`, `MyDialog(Frame owner, …)`. Left on the JDK, each becomes a compile
error the moment a `vaadinx.swing.JFrame` / `JDialog` is passed in — which is every call site.

- **`GridBagConstraints` stays JDK.** Only the layout-manager class moves; its constraints sidekick
  is a pure data carrier.
- **`BoxLayout` / `GroupLayout` / `LayoutStyle` port under the `javax.swing` rule**, not this one —
  they live in that package. `GroupLayout` and `LayoutStyle` are what NetBeans/Matisse forms
  generate, so expect them in any builder-authored app.
- These types also appear as fully-qualified references *inside* code — signatures, casts, generic
  bounds. That is the tool's first pass; the by-hand rule is a *Watch out* below.

**`java.awt.event` — the ported classes carry their constants with them.** Every ported class
re-exposes its JDK counterpart's public static constants, declared and inherited: `KeyEvent.VK_*`,
`*_DOWN_MASK`, `Component.CENTER_ALIGNMENT`, `Frame.MAXIMIZED_BOTH`, `JFrame.EXIT_ON_CLOSE`,
`SwingUtilities.CENTER`.

- **Never qualify a constant back to the JDK** (`java.awt.Component.CENTER_ALIGNMENT`): it drags the
  JDK type into ported code, and the constant is already on the emulator. Each ported class's
  constant set is diffed against the real JDK class by a test, so a missing one is a bug to report
  rather than something to qualify around.

**The screen classes present the browser viewport as a one-screen display.**
`getLocalGraphicsEnvironment().getMaximumWindowBounds()` and `getCenterPoint()` answer in viewport
pixels, so the maximize-to-screen idiom ports by import swap alone.

- **Grep for these even if you ran the tool**, because the un-swapped failure has two shapes and one
  is silent. The screen queries throw `HeadlessException` wherever they are called — often a frame
  constructor, so the app dies at start-up. But `GraphicsEnvironment.isHeadless()` **returns `true`
  on a server**, so an `if (!GraphicsEnvironment.isHeadless()) { … }` guard quietly skips the UI
  inside it with nothing in any log. The emulator reports `false`: a browser is a display.

**`EventQueue` keeps the static EDT surface and WARNs on the rest.** `isDispatchThread`,
`invokeLater`, `invokeAndWait` and `getMostRecentEventTime` carry working semantics; the instance
dispatch surface — `postEvent`, `peekEvent`, `dispatchEvent`, `push` / `pop`, `createSecondaryLoop` —
compiles and WARNs at runtime. `Toolkit` ports too and has a *Watch out* of its own below.

**The date/time classes are browser-zoned emulators**, so the swap alone fixes the
server-vs-browser drift for `format` / `parse` and `getInstance` / `new GregorianCalendar(...)`.

- They live in **different packages than `javax.swing`**, so a `javax.swing` regex never touches
  them. The tool has rows for all three.
- The case a hand rewrite most often misses is **an explicit `import java.util.Calendar;` used only
  for a constant** (`Calendar.DAY_OF_MONTH`). It is invisible — no compile error, no WARN, just
  dates in the server's zone. Grep `\bSimpleDateFormat\b` / `\bCalendar\b` /
  `\bGregorianCalendar\b` and check every hit.
- `java.time.*` **stays JDK** and still needs `BrowserTimeZone.get()` passed by hand;
  background-thread date code needs `EmulatorContext`. Full recipe in [`dates.md`](./dates.md).

## What stays JDK

- Data types: `Color`, `Font`, `Dimension`, `Point`, `Rectangle`, `Insets`
- `java.awt.GridBagConstraints` — pure data carrier. Only the *layout-manager class* `GridBagLayout` moves to `vaadinx.awt`; its constraints sidekick stays JDK. Keep `import java.awt.GridBagConstraints;` even when `import vaadinx.awt.GridBagLayout;` replaces the layout import beside it.
- `Object`-source events: `ActionEvent`, `ChangeEvent`, `ItemEvent`. *(`ActionEvent` lives in `java.awt.event.*` alongside `KeyEvent` / `MouseEvent` / … which *do* port — easy to lump; check the bucket per event type, not per import package.)*
- Action infrastructure: `Action`, `AbstractAction`, `ActionListener`
- Models: `DefaultListModel`, `DefaultComboBoxModel`, `SpinnerNumberModel`, `DefaultTableModel`, `ListSelectionModel`, `SpinnerDateModel`, …
- Constants holders / enums: `WindowConstants`, `SwingConstants`, `KeyStroke`, `DropMode` (a plain enum argument — `vaadinx.swing` components accept it unchanged; there is no `vaadinx.swing.DropMode`)
- Sub-packages `javax.swing.event.*`, `javax.swing.table.*`, `javax.swing.text.*`, and `javax.swing.tree.*` (e.g. `EventListenerList`, `AbstractTableModel`, `DefaultTableModel`, `NumberFormatter`, `Document` / `PlainDocument`, `AttributeSet`, `BadLocationException`, `DefaultMutableTreeNode`, `DefaultTreeModel`, `TreePath`, `TreeSelectionModel`) — these carry listener-list utilities, models, formatters, value types and exceptions that don't reference `Component`. Stay JDK by default.

  *The exception is a **rule**, not a list: a sub-package type ports if it **is** a `Component` or if its own signatures **mention** one.* The types that satisfy it today, all of which port to the matching `vaadinx.swing.*` sub-package:

  | sub-package | ports |
  |---|---|
  | `javax.swing.text` | `JTextComponent` — the base of the ported `JTextField` / `JTextArea` / `JPasswordField`, so `x instanceof JTextComponent` against a `vaadinx.awt.Component` is an inconvertible-types error if you leave it JDK |
  | `javax.swing.table` | `JTableHeader`, `TableColumn`, `TableColumnModel`, `DefaultTableColumnModel`, `TableCellRenderer`, `TableCellEditor`, `DefaultTableCellRenderer` |
  | `javax.swing.tree` | `TreeCellRenderer`, `TreeCellEditor`, `DefaultTreeCellRenderer` |
  | `javax.swing.text.html` | `HTMLEditorKit`, `StyleSheet` — see the HTML watch-out below, which also explains why `HTMLDocument` is the one case you deliberately leave on the JDK import |

  The table types are the ones that bite without warning: `vaadinx.swing.JTable.getColumnModel()` hands back a `vaadinx.swing.table.TableColumnModel`, so a `javax.swing.table.TableColumnModel tcm = getColumnModel();` fails with *"incompatible types"* — and the whole `TableColumn` / renderer / editor chain follows it across. See the cell-renderer watch-out below.
- Anything in `java.awt.*` not enumerated under "ports" above (`RenderingHints`, …). *(`Toolkit` is **not** in this bucket — it ports; see the `Toolkit` watch-out below.)*

**Why a regex can't do this, and what the tool does instead.** `s/javax\.swing\./vaadinx.swing./` is too broad — it rewrites the constants holders and sub-packages above — and narrowing it to `javax.swing.J*` is too narrow, missing `ButtonGroup` / `Timer` / `SwingWorker` / `BorderFactory`. There is no regex between the two, because the ported and stay-JDK sets are not distinguishable by name shape; they are a *list*. The tool reads that list from the swap table, so each of the buckets above is a lookup rather than a rule to get right. The lists in this section are the derived explanation of that table — useful for reviewing the diff, and necessary if you rewrite by hand.

The two lists are disjoint by simple name, which is what makes a table lookup sufficient: there is no `vaadinx.swing.WindowConstants` and no `vaadinx.awt.Color`, so nothing has to choose.

## Watch out

***Watch out:*** `ButtonGroup` ports to `vaadinx.swing.*` even though it has no `J*` prefix and *feels* like an "action-coordination holder" alongside `Action` / `AbstractAction` / `ActionListener` (which stay JDK). It must operate on the ported `AbstractButton` subclasses (`vaadinx.swing.JRadioButtonMenuItem`, …), so it has to move with them — leaving `import javax.swing.ButtonGroup;` will compile-fail at `group.add(...)` once the buttons have ported. The tool has a row for it. It is a `J*`-narrowed regex that misses it, so this is the first thing to check on a hand rewrite.

***Watch out: JDK-stay tokens without a `J*` prefix.*** Several stay-JDK classes live at the top level of `javax.swing` alongside the `J*` components, so the broad regex `s/javax\.swing\./vaadinx.swing./` rewrites them; they aren't in a sub-package the narrow `javax.swing.J*` filter could exclude either. **The tool leaves every one of them alone — none has a table row** — so this list is here to make the diff reviewable, and as the hand-rewrite checklist. After a hand rewrite, grep your `vaadinx.swing.` imports for any of:

- **Action infrastructure** (stays JDK): `Action`, `AbstractAction`, `ActionListener`
- **Constants holders / enums** (stay JDK): `WindowConstants`, `SwingConstants`, `KeyStroke`, `DropMode`
  - *But a `SwingConstants` value accessed **through a ported component class** — `JTextField.RIGHT`, `JLabel.CENTER`, `SwingConstants.HORIZONTAL` referenced as `JSlider.HORIZONTAL` — ports **with the class**: `vaadinx.swing.JTextField.RIGHT` resolves (the ported `JTextField implements javax.swing.SwingConstants`, so it re-exposes the identical constant). Only a **bare** `SwingConstants.RIGHT` reference stays JDK. The narrow `javax.swing.J*` regex rewrites `javax.swing.JTextField.RIGHT` correctly on its own — leaving it as `javax.swing.JTextField.RIGHT` would strand a JDK component type inside ported code.*
- **Top-level models** (stay JDK): `ListSelectionModel`, `SpinnerNumberModel`, `SpinnerDateModel`, `DefaultListModel`, `DefaultComboBoxModel`, `DefaultTableModel`
- **Cell-editor bases** (stay JDK): `CellEditor`, `AbstractCellEditor` — they carry a listener list and no `Component`, and there is no `vaadinx.swing.AbstractCellEditor`. This is the pairing to keep straight in a hand-written table editor: the *interface* `javax.swing.table.TableCellEditor` **ports** (it hands out a `Component`), while the abstract base you extend stays JDK. The two meet, because the ported `TableCellEditor` is a subinterface of the JDK's `CellEditor` and `AbstractCellEditor` implements exactly that — so `class MyEditor extends javax.swing.AbstractCellEditor implements vaadinx.swing.table.TableCellEditor` is the correct, and compiling, shape. *(Don't generalise from `vaadinx.swing.DefaultCellEditor`, which does port — it holds a component.)*

Each hit is a mis-rewrite — revert that import back to `javax.swing.`. Skipping this check costs ~20 cryptic "cannot find symbol: vaadinx.swing.KeyStroke" compile errors on a typical app.

***Watch out:*** `instanceof` checks on the ported event types must rewrite too. `event instanceof java.awt.event.ComponentEvent` returns `false` against a `vaadinx.awt.event.ComponentEvent` — by design. The tool handles both halves: it rewrites a fully-qualified `instanceof` directly, and because it *deletes* `import java.awt.event.*;` rather than leaving it beside the new imports, any simple-name reference it did not account for becomes a compile error instead of silently binding to the JDK type. On a hand rewrite, delete those wildcards first for the same reason.

***Watch out:*** Fully-qualified `java.awt.Component` / `java.awt.Container` references inside code (method signatures, casts, generic bounds) are **not** import statements, so no optimize-imports pass will touch them — this is the tool's first pass, and on GUI-builder-generated code it is where most of the work is. The rule it applies: a fully-qualified reference rewrites exactly when its type ports, and JDK-stay types are left alone. Example: a signature `static void addRow(java.awt.Component f, java.awt.Insets m)` rewrites to `static void addRow(vaadinx.awt.Component f, java.awt.Insets m)` — `Component` rewrites; `Insets` stays JDK. By hand, grep `java.awt.Component\b` / `java.awt.Container\b` and rewrite each inline.

***Watch out: `java.awt.Toolkit` ports — it's the clipboard / drag-and-drop / screen-info entry
point.*** The swap is plain, *not* a shape change: `vaadinx.awt.Toolkit` keeps both
`getDefaultToolkit()` and the instance `getSystemClipboard()`, so
`Toolkit.getDefaultToolkit().getSystemClipboard()` compiles unchanged.

- The clipboard *payload* types from `java.awt.datatransfer.*` (`Clipboard`, `StringSelection`,
  `DataFlavor`, `Transferable`) **stay JDK**; drag-and-drop ports via `vaadinx.swing.TransferHandler`.
- `getScreenSize()` reports the **browser viewport** — the area your windows actually place in — so
  classic screen math (centering a window, anchoring a toast at `screen.width - w - margin`, sizing
  to a fraction of the screen) lands on the visible page with no code change.
- **Leaving the JDK import in place is a runtime failure, not a silent degrade**, which is unlike
  most stay-JDK types: `getSystemClipboard()` on a headless server throws
  `java.awt.HeadlessException` at the peer→Swing seam, surfacing as an ErrorHandler crash rather
  than a triageable WARN.

***Watch out: HTML panes — two classes port, the rest of `javax.swing.text.html` does not.*** If you render HTML in a `JEditorPane`, swap exactly `javax.swing.text.html.HTMLEditorKit` → `vaadinx.swing.text.html.HTMLEditorKit` and `javax.swing.text.html.StyleSheet` → `vaadinx.swing.text.html.StyleSheet`. That makes the common styling idiom work as written:

```java
HTMLEditorKit kit = new HTMLEditorKit();
pane.setEditorKit(kit);
kit.getStyleSheet().addRule("body { font-family: sans-serif; }");
pane.setEditable(false);
pane.setText("<h2>Report</h2><ul><li>one</li></ul>");
```

Everything else in that package — `HTML`, `HTMLDocument`, and all of `javax.swing.text.html.parser` — **stays JDK**. (`HTMLDocument` staying JDK is what makes the cast below work: the pane hands out a subclass of it.) Consequences worth knowing:

- **Links only work for `http`, `https` and `mailto` targets — check your help pages.** A `HyperlinkListener` fires when the user clicks a link in a **non-editable** pane (that restriction is Swing's own). But the editor's sanitizer keeps an `href` only for those three protocols: a **relative** href (`topics.html`), a root-relative one (`/help/x.html`), a `file:` or `jar:` URL, or a bare `#fragment` all lose the attribute, and the text stops being a link at all. So a help set whose pages link to each other relatively — the usual way to write one — arrives with its navigation silently gone. The fix is in your listener, which is already where Swing puts the decision: link by `http(s)` URL (a made-up host is fine, nothing is fetched) and map those URLs onto your own resources:

  ```java
  pane.addHyperlinkListener(e -> {
      if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
      String page = e.getURL().getPath();                  // "/topics.html"
      pane.setPage(getClass().getResource("/help" + page)); // load it yourself
  });
  ```
- **Rules style what is rendered, not what is dropped.** A rule reaches the real `<h2>`/`<p>`/`<a>` elements, and `body` maps onto the pane's content. But no stylesheet brings back markup the editor's model refuses — tables above all. Style the elements that survive; don't expect CSS to restore structure.
- **Headless HTML parsing needs no changes at all.** Code that uses `HTMLEditorKit` purely as a parser — a `ParserCallback` handed to `javax.swing.text.html.parser.ParserDelegator`, with no pane involved — keeps working whether or not you swap the import, because the nested `ParserCallback` / `Parser` types are the JDK's own either way. Leave `javax.swing.text.html.parser` imports alone.
- **The `HTMLDocument` element model is readable, not writable.** A `text/html` pane's `getDocument()` **is** an `HTMLDocument`, so the usual introspection compiles and works as written — keep your `javax.swing.text.html.HTMLDocument` import:

  ```java
  pane.setContentType("text/html");
  pane.setText("<h2>Invoice</h2><p id=\"total\">1 240.00 EUR</p>");

  HTMLDocument doc = (HTMLDocument) pane.getDocument();
  Element total = doc.getElement("total");          // resolves
  Element root  = doc.getDefaultRootElement();      // the parsed tree
  ```

  The **mutators do not** — `insertBeforeEnd`, `setOuterHTML`, `setInnerHTML`, `insertAfterEnd` and the rest WARN and change nothing, because the model is a read-only projection of the pane's markup. Rewrite an appending pane (a console or transcript, the common case by far) to `pane.setText(pane.getText() + html)`; rebuild-and-`setText` covers interior edits. Three details worth knowing: the pane's `getText()` returns **markup** while the *document's* own `getText(0, len)` is the **rendered text** (as in Swing); `getElement(id)` sees only markup you set server-side, since the editor drops `id` on the way back from a user edit; and `getIterator(HTML.Tag)` answers `null` for block tags like `<p>` or `<li>`, which is the JDK's own behaviour, not ours.
- **`setBase` compiles, runs, and redirects nothing.** `((HTMLDocument) pane.getDocument()).setBase(url)` round-trips the value and changes no resolution: relative links already resolve against the page `setPage(url)` loaded, and relative *images* don't load at any base, because only base64 data-URL images survive. Keep the call if it documents intent; delete it if it only restated the page URL.
- **A custom `ViewFactory`** compiles but has no effect — Vaadin renders the HTML, so a factory that suppresses images is redundant (images outside base64 data-URLs are dropped anyway).

***Watch out: cell renderers port.*** `DefaultListCellRenderer` (top-level `javax.swing`, ports under the `javax.swing.*` rule) and `DefaultTableCellRenderer` / `DefaultTreeCellRenderer` (sub-package classes, port if emulated — see the stay-JDK sub-package note above) all extend the ported `vaadinx.swing.JLabel`. If your code subclasses one and overrides `getListCellRendererComponent` / `getTableCellRendererComponent` / `getTreeCellRendererComponent`, the **return type** rewrites to `vaadinx.awt.Component` — the ported renderer interfaces hand back our component hierarchy, not `java.awt.Component`. The *first* parameter keeps its same generic shape (`JList<? extends E>` / `JTable` / `JTree`) — but because those component classes have themselves ported, it now refers to the `vaadinx.swing` type, matching your import. So only the return type changes visibly; a JDK-faithful override otherwise compiles unchanged.

## See also

- [Phase 2](./guide.md#S_run_import_swap) — the tool run this page backs.
- [`dates.md`](./dates.md) — the three date/time buckets, of which the swap fixes the first.
- [`addons.md`](./addons.md) — the pre-built add-ons, each contributing its own rows to the table.
