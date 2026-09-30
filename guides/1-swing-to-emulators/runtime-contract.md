# Runtime contract — WARN logs and throws

Reference for [Phase 6](./guide.md#S_triage_warns)'s WARN triage. Read it when you want to know
whether something you are seeing is a gap, a bug in your app, or a deliberate refusal.

**Anything not implemented WARNs; it does not throw.** `EHelper.onUnimplemented(class, method, args)`
logs at WARN level through slf4j and returns a sensible default, so a gap shows up in your log rather
than as an outage. Those WARNs are your gap inventory and your progress signal — read them, triage
them, ignore the cosmetics.

**Throws are a short, enumerated list.** Every one of them is either a bug real Swing would also have
punished, or a decision only you can make:

| what throws | why, and what to do |
|---|---|
| Inputs Swing itself rejects — `getComponent(invalidIndex)` → `ArrayIndexOutOfBoundsException`, `setValue(null)` on a `SpinnerNumberModel` → `IllegalArgumentException` | A real bug in your code, with the exception type Swing would have used. Fix the call site. |
| `SwingWorker.get()` or `SwingUtilities.invokeAndWait` **from the EDT** | Would have deadlocked under Swing too. Restructure the call. |
| An `EXIT_ON_CLOSE` close-attempt on a **non-`@MainWindow`** JFrame | It renders as a Vaadin Dialog, where killing the app is not a defensible reading. Use `DISPOSE_ON_CLOSE` or `HIDE_ON_CLOSE` ([`lifecycle.md`](./lifecycle.md#how-your-app-ends)). |
| `JSpinner(SpinnerDateModel)` or a date-format `JFormattedTextField`, constructed with no browser time zone | The [init listener](./guide.md#S_register_bootstrap) is not wired. One line, and the message says so. |
| A `java.util.prefs.Preferences` read from a `static` initializer or from `main()` | Prefs are backed by the browser's `localStorage`, so the first read needs a live UI. Move it into UI code — an `invokeLater` body, a frame constructor, a listener callback. |
| `JFileChooser.showDialog(parent, text)` | The one refusal-to-guess in the library. [Its own section below](#jfilechoosershowdialog--you-must-pick-a-direction) explains what to do per call site. |
| A blocking dialog call — `JOptionPane.show*`, a modal `setVisible(true)`, a worker's `SwingUtilities.invokeAndWait` — whose **tab closed or session ended** before anyone answered | `vaadinx.BrowserSessionClosedError`, deliberately an `Error` so your `catch (Exception e)` does not turn it into a made-up answer. Nothing to fix: the listener ends, its `finally` blocks run, and nothing is logged as a failure. Don't catch `Error` or `Throwable` around a dialog to carry on. |
| Updating a component from **a thread your app started itself** — a raw `Thread`, or a pool you did not wrap | That thread has no `EmulatorContext`, so there is no session to reach the browser through. One line where the pool is built: `EmulatorContext.wrap(executor)` ([`dates.md` § Background threads](./dates.md#2-background-threads--carry-the-context-with-emulatorcontext)). `SwingWorker` needs nothing. |

**Where Swing is buggy or undefined, `:emulators` reproduces that rather than fixing it.** A forgotten
`revalidate()` still looks broken. The paper cut is the point: you already learned it writing the
Swing app, and reproducing it keeps that knowledge worth having — and keeps debugging symmetric with
how you debugged before the migration.

## `JFileChooser.showDialog` — you must pick a direction

`showDialog(parent, "…")` throws `IllegalStateException`, always, and is `@Deprecated` so every call
site shows up as a compiler warning during the port. This is the one place the emulator refuses to
guess instead of doing something plausible, and the reason is worth understanding, because it tells
you what to do at the call site.

On the desktop none of `showOpenDialog` / `showSaveDialog` / `showDialog` reads or writes a byte.
All three put up a picker and hand you back a `File`; **your** code does the I/O afterwards. So the
`OPEN`/`SAVE`/`CUSTOM` distinction costs nothing and the JDK never acts on it.

In a browser the two directions are entirely different machinery, and it has to be chosen *before*
the dialog opens:

- **Load** puts up an upload widget; the file arrives from the browser and is staged into a temp
  `File` your code then reads.
- **Save** prompts for a name, hands you a writable temp target, and offers a download link so the
  user can pull the bytes after your code has written them.

`showDialog`'s `CUSTOM_DIALOG` declares neither, so there is nothing to dispatch on. Guessing
"load" — the literal reading of the JDK contract — breaks the very common idiom of using a
custom-button chooser to **name an output**: your user gets an upload prompt, your code writes a
perfectly good file on the server, no download is ever offered, and the export *reports success*.
Nothing throws, nothing WARNs. That silent-success failure is what the throw exists to prevent.

**The fix, per call site.** Look at what the surrounding code does with `getSelectedFile()`:

```java
// BEFORE — direction undeclared, throws
jf.showDialog(this, "Select Save location");
ExcelUtils.write(table, jf.getSelectedFile().getAbsolutePath() + ".xls");

// AFTER — the next line writes, so it is a SAVE. Direction swapped, nothing else.
jf.setApproveButtonText("Select Save location");   // keeps your custom button label
jf.showSaveDialog(this);
ExcelUtils.write(table, jf.getSelectedFile().getAbsolutePath() + ".xls");
```

- the code **reads** the file it gets back → `showOpenDialog(parent)`
- the code **writes** it → `showSaveDialog(parent)`
- `setApproveButtonText("…")` before the call keeps the custom label either way
- direction decided at runtime → branch, and call the matching variant in each branch
- can't tell? Leave the `showDialog` call and mark it TODO. It won't compile clean and won't run,
  which is exactly what a TODO should be — no silent wrong behaviour is possible.

Note what the AFTER block does *not* do: it doesn't check the return value, and it keeps the
`+ ".xls"`. Both are deliberate — see the next two points.

**If your call site ignores the return value, port it that way.** Most `show*` callers do, and the
consequence is the same one they always had: cancel (or the dialog's X, or ESC) clears the
selection, `getSelectedFile()` returns `null`, and the next line throws `NullPointerException`. That
is your app's pre-existing bug, not something the migration introduced — desktop Swing nulls the
selection on cancel too, via the JDK's own `CancelSelectionAction` — and the emulator reproduces it
faithfully on both the LOAD and SAVE paths.

**Resist fixing it in the same edit.** Not because the bug is harmless, but because this call site
is one you are *forced* to touch, and a port whose diff mixes required rewrites with unrelated
behaviour changes is one nobody can review: when the migrated app then behaves differently from the
desktop one, you can't tell which of your edits did it. Keep the port behaviour-preserving, write
the bug down, fix it in a separate commit once the migration is verified. The same reasoning applies
to every latent bug the port walks past.

What genuinely changes in the browser is the **hit rate**, and what you see when you hit it:

- Desktop users rarely cancel a save dialog they deliberately opened. Browser users cancel an
  unexpected upload widget or name prompt readily — so a crash that fired once a year now fires in
  ordinary use. Expect it during your first walkthrough; that is the bug working as it always did.
- The NPE escapes your `ActionListener` into Vaadin's `ErrorHandler` — logged server-side, default
  behaviour, no dialog. The button looks like it did nothing. Your own `catch` around the write does
  **not** fire, because the throw happens on the `getSelectedFile()` line, before the `try`. If a
  file-export button is silently inert in the browser, check the server log for an NPE here first.

The guarded form, for that later commit:

```java
if (jf.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
```

**Deriving a path from the name is fine** — which is why the AFTER block keeps its `+ ".xls"`.
An extension swap, a suffix a library appends, several files from one save: the download dialog
watches the *directory* your target sits in, so whatever your app writes there is found and offered,
under the name your app actually produced. What it cannot offer is a file written somewhere else
entirely: an absolute path of your own choosing goes to the **server's** filesystem, where the
browser user can't reach it. If a save produces no download, that is why, and the server log says
so.

## A listener that blocks holds the session

A Swing listener that blocks on anything other than a dialog — a slow query, a `Thread.sleep`, a bare
`future.get()` — freezes the desktop window until it returns, because it holds the EDT. It holds the
browser session the same way here: no other request of that session is processed until it returns.
That is faithful, and usually what you want (two clicks cannot interleave inside one listener). Past
ten seconds the server logs a WARN with the listener's stack, and repeats it at 30 s, 90 s, and so on.
A long query WARNs too, rightly: the user is looking at a frozen tab. Move genuinely long work into a
`SwingWorker`, as you would on the desktop.

## Modal dialogs inside `synchronized`

**The one place where a desktop-safe pattern freezes a session.** On the desktop every listener runs
on the one EDT, and a thread re-enters a monitor it already holds — so a `JOptionPane` shown from inside
a `synchronized` method never blocks the next listener, even one that enters the same method. Here each
listener is a thread of its own. If a modal dialog waits inside `synchronized (lock)`, the next
listener that enters `lock` — the dialog's own OK handler, a `Timer`, a click in another tab — waits
for it *while holding the session*, and the answer that would release `lock` can never arrive. The
session stays frozen for good, and the WARN above names the waiting listener.

The scan lists every `synchronized` site; most guard no dialog and need nothing. For the ones that do:
decide under the lock, ask outside it.

```java
// BEFORE — the dialog waits while holding `this`
synchronized void save() {
    if (JOptionPane.showConfirmDialog(this, "Overwrite?") == JOptionPane.YES_OPTION) {
        write();
    }
}

// AFTER — ask first, then take the lock for the part that needs it
void save() {
    if (JOptionPane.showConfirmDialog(this, "Overwrite?") == JOptionPane.YES_OPTION) {
        synchronized (this) {
            write();
        }
    }
}
```

## See also

- [Phase 6](./guide.md#S_triage_warns) — the triage step that sends you here.
- [`lifecycle.md`](./lifecycle.md) — which start-up hooks can reach what, for throw (e).
