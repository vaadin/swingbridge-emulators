# Platform checks, shell-outs and native code

Reference for [Phase 3](./guide.md#S_triage_platform)'s platform triage. It closes with the debt
this migration deliberately walks past.

Your desktop app had **one machine**. The migrated app has **two**: the server running your code and
the browser in front of your user. Every call that touched "the machine" now has to be re-asked *which
one?* — and that question, not the call itself, is the migration work.

**First, the principle that decides the hard cases: migrate the intent, not what happens on your dev
box.** A Windows-only feature ported on a Linux workstation fails locally for reasons that have nothing
to do with the migration. Don't take that as licence to drop it — read what the code was *for* and port
that. "It doesn't work on my machine anyway" would quietly delete every platform-conditional feature in
the app.

## Platform checks are not migration items

`SystemUtils.IS_OS_WINDOWS`, `System.getProperty("os.name")`, `File.separator` and friends still
compile and still work — they now describe the **server**, which is a real and correct thing to
describe. Leave the check. Classify **what it gates**:

| the check gates… | what to do |
|---|---|
| Look-and-feel selection | Ignore — L&F is out of scope, `setLookAndFeel` WARNs and continues. |
| Native code for a **server-side** service (a converter, a licence dongle on the host, a printer queue the server owns) | Leave as-is. It was always "run this on the machine hosting the logic", and that machine is now the server — your customer can keep running the server on Windows. |
| Native code for hardware **at the user's end** — fingerprint reader, smartcard, barcode scanner, serial port | This is the case that genuinely breaks. The device is no longer on the machine running your code, and there is no server-side substitute. It needs a browser API, a small local agent the browser talks to, or it comes off the feature list. Budget for it explicitly. |

Same triage for `System.loadLibrary` / JNI / JNA, `java.awt.Desktop`, `SystemTray`, and `Robot`.

## `Runtime.exec` — classify by intent, not by command

Ask who the command was serving:

- **Backend intent** — a converter, an archiver, a database tool, a report generator, a shell script.
  **Stays as-is.** Two things to check, both easy to miss: the working directory differs under a
  servlet container, and the binary must exist *on the server* — a Windows-only invocation on a Linux
  host is now broken, which is where the platform-check triage above comes back.
- **UI intent** — the command exists to *show something to the human*. The human is in a browser, so
  this must be re-homed. Recognise it by shape: `cmd.exe /c start <file>`,
  `rundll32 url.dll,FileProtocolHandler <url>`, `explorer.exe <file>`, `xdg-open` / `gnome-open`,
  `open <file>` (macOS), launching a browser binary directly, or any
  `Desktop.getDesktop().open/browse/edit/mail/print`.

| UI intent | web-shaped replacement |
|---|---|
| Open a bundled document | Put the file on the classpath and serve it: `new Anchor(DownloadHandler.forClassResource(MyClass.class, "/help.pdf", "help.pdf"), "Read Manual")`. To keep a `JMenuItem` a menu item instead of restructuring the menu into a link, read the resource and call `vaadinx.BrowserFileTransfer.openDownloadDialog(bytes, "help.pdf")` — the same path the virtual PDF printer uses. |
| Open a URL | `UI.getCurrent().getPage().open(url)`, or an `Anchor` with `target="_blank"`. |
| Print | The virtual PDF printer in `:emulators-printing` — the user gets a PDF to print. |
| Send mail | A `mailto:` link. |
| Open a file the *user* just picked | It never left their machine; there is nothing to open server-side. Reconsider the feature. |

***Watch out:*** a document-opening shell-out usually points at a **relative** path resolved against the
process working directory. After migration that path means somewhere inside your deployment, not next
to the app the user installed — so the asset has to move onto the classpath (or into `webapp/`) as part
of the port. Grep for the file name; it is easy to migrate the *call* and forget the *file*.

## Looks like migration work — isn't

Some of what you'll notice while porting is real technical debt that has *nothing to do with the move
to the browser*. Fixing it here costs time, adds diff, and buys nothing the migration needs — and
worse, it entangles two changes so that when something breaks you can't tell which one did it. Leave
it; note it; come back after the app runs.

**A second logging framework is the standard example.** If the app logs through `org.apache.log4j`,
`java.util.logging` or commons-logging while SB-Emulators and Vaadin use slf4j, **leave it alone.** Two logging
frameworks coexist fine in one JVM: different packages, independent configuration, no classpath or SPI
conflict. You get two output formats and two config files, which is untidy and harmless. Rewriting the
call sites is pure churn — and it is *not* the thing that makes the app work in a browser.

Two caveats worth knowing, neither of which is a migration step:

- If you later want one configuration, the cheap route is a **bridge**, not a rewrite:
  `org.slf4j:log4j-over-slf4j` owns the `org.apache.log4j` package and routes those calls into slf4j
  with **zero source changes**. Two gotchas: you must `<exclusion>`/`exclude` the real log4j jar
  (often arriving transitively through some other dependency), or two jars own the same package and
  classpath order decides which wins; and the bridge implements the common API only — `getLogger`,
  `info`, `error` are fine, `Category.setLevel` / `getAppender` are not.
- Check *where* the old framework's jar comes from. If it arrives transitively and is declared nowhere
  in your build file, that's worth fixing on its own merits — a version bump of the library that drags
  it in will silently change your logging stack. Same fix on the desktop as on the web.

**The same rule covers the neighbours:** upgrading a dependency "while we're in here", replacing an
abandoned library, switching build tools, reformatting. Each is a legitimate task and none of them is
this one. The exception is a dependency that actually blocks the port — a Swing library needing the
[triage in `third-party-libraries.md`](./third-party-libraries.md), or something that won't run on
your target JDK at all.

## See also

- [`third-party-libraries.md`](./third-party-libraries.md) — the dependency triage this page defers to.
- [`runtime-contract.md`](./runtime-contract.md) — what a WARN from an out-of-scope surface means.
