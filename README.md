# SwingBridge Emulators

Runtime emulation of the Swing API on top of [Vaadin 25](https://vaadin.com/). Take an existing Swing desktop application, swap its `javax.swing` imports for `vaadinx.swing`, recompile, and it runs as a Vaadin web app — real Vaadin components in the browser, not a picture of a desktop window.

**SwingBridge Emulators** — **SB-Emulators** for short — is a **migration bridge** to a future native-Vaadin rewrite, not a permanent target or a 1:1 reimplementation of Swing. It gets your desktop app running in a browser cheaply, so you can then iterate it toward idiomatic Vaadin view by view.

The migration is designed to be **run by an AI agent, with you making the decisions**. The mechanical work is done by tools, the procedure is a tickable guide the agent follows, and the handful of judgement calls are put to you. This README is the overview: what you need before you start, what the steps are, where the migrated code ends up, and how to check the result.

**Maintenance is best-effort.** SB-Emulators is developed as the migration bridge described above, not against a support commitment — issues and pull requests are read and acted on as time allows. What it covers is the [documented scope](#whats-implemented) below: a component or method outside that list is a known gap rather than a defect, and [Permanently out of scope](#permanently-out-of-scope) names what will not be added at all.

> Developing SB-Emulators itself (not migrating an app with it)? See [DEVELOPING.md](./DEVELOPING.md).

## How it works

- `vaadinx.awt.*` mirrors `java.awt.*` for the `Component` / `Container` hierarchy, and `vaadinx.swing.*` mirrors `javax.swing.*` (`JFrame`, `JButton`, `JTable`, …).
- Every emulated component is backed by a real Vaadin component — its *peer* — and forwards behaviour to it. Your code stays Swing-shaped; the browser renders Vaadin.
- Data types (`Color`, `Font`, `Dimension`, `ActionEvent`, models) are reused straight from the JDK.
- A method with no Vaadin counterpart logs a WARN and returns a sensible default — it never crashes your app. Genuine programming errors still throw what Swing would throw. See [D_never_fail_on_gaps](./emulators/decisions.md#D_never_fail_on_gaps).
- Modal dialogs **block exactly as in Swing** — `JOptionPane.showConfirmDialog(...)` returns the user's answer, no rewrite into callbacks. See [Blocking dialogs](#blocking-dialogs).

This is **import-swap, not a binary drop-in**: you need the source of every piece of Swing code the app depends on, because compiled Swing jars must be recompiled against `vaadinx.*`. See [D_import_swap_target](./emulators/decisions.md#D_import_swap_target).

For the bulk of a codebase, the only difference after migration is the import lines:

```java
import vaadinx.swing.BorderFactory;      // was javax.swing.BorderFactory
import vaadinx.swing.JLabel;             // was javax.swing.JLabel
import vaadinx.swing.JPanel;             // was javax.swing.JPanel
import vaadinx.awt.BorderLayout;         // was java.awt.BorderLayout

public final class EmployeePreviewPanel extends JPanel {   // body unchanged, character for character
    public EmployeePreviewPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Preview"));
        // …
    }
}
```

A small set of files need real hand edits — the `main()` split, the `@MainWindow` annotation, exit paths, state that was global on the desktop and must become per-user on a server — and those are what the guide and the agent spend their time on. That view is from the [bundled CRUD example](./testapps/crud/): [`swing/`](./testapps/crud/swing/) is the original, [`1-emulators/`](./testapps/crud/1-emulators/) the finished migration.

## Migrating with an AI agent

### What you work from: the migration kit

Everything a migration needs ships as one download, the **migration kit** — the guides, the three migration tools, the agent prompt, Claude Code skills and three example apps to practise on. To build one from a checkout, `./mvnw -C clean install` leaves it at `zip-distro/target/swingbridge-emulators-<version>-dist.zip`. Unzip it outside your app's repository. The kit's own [`README.md`](./zip-distro/src/main/kit/README.md) is the starting page once you have it.

Two names appear in every command:

- **`SWINGBRIDGE_HOME`** — the unzipped kit (or a clone of this repository).
- **`MIGRATED_APP_FOLDER`** — the root of the app being migrated.

The kit is **not** the library your app builds against. The migrated app depends on SB-Emulators by Maven coordinate, like any other dependency.

### Before you start

Settle these before the agent touches anything. Each one, missed, costs more later than it does now.

**Your environment**

- [ ] **JDK 24 or newer** for building *and* running — a runtime requirement, not a preference: on 21–23 every modal dialog deadlocks, and SB-Emulators refuses to start there. The tools in the kit need nothing but a JDK; Maven comes with each example's wrapper.
- [ ] **Network access to Maven Central** and Vaadin's frontend build for the migrated app. The tools themselves run offline, and your sources never leave your machine.
- [ ] **An agent that can run shell commands and edit files.** Claude Code gets three ready-made commands (below); any other agent works from the plain-Markdown [agent prompt](./guides/1-swing-to-emulators/agent-prompt.md).
- [ ] **Browser automation for the agent, if you can give it one** (for example Claude in Chrome, or a Playwright MCP server). Without it, the agent stops at "it starts" and the click-through checks in [Verifying the migration](#verifying-the-migration) are yours.

**Your app**

- [ ] **It builds cleanly today**, from the command line, with `clean`. The `static` sweep reads the app's compiled classes, so a stale or IDE-compiled `target/` gives it wrong input.
- [ ] **It is under version control, with a clean working tree.** An in-place migration is meant to be one reviewable, revertible change set.
- [ ] **You have source for every Swing-touching dependency.** List them now — this is the single biggest driver of how long the migration takes. JCalendar 1.4 and JGoodies Forms 1.2.1 have pre-built [add-ons](./guides/1-swing-to-emulators/addons.md); a closed-source Swing library with no add-on is a blocker to resolve *before* you start, not during. Look-and-Feel libraries (FlatLaf and the like) are simply deleted. [How to triage one](./guides/1-swing-to-emulators/third-party-libraries.md).
- [ ] **It is not a Spring-based Swing app.** A desktop `ApplicationContext` serves one user; a web one serves all of them, and a singleton bean holding the current user leaks it to the next — a hazard no tool checks yet. (Using Spring Boot to *host* an app that had no Spring is fine, and the default.)
- [ ] **Its value is not mainly in the [permanently out-of-scope](#permanently-out-of-scope) surfaces** — custom `paintComponent` graphics, Look-and-Feel code, applets. Those views need a rewrite whatever else happens.
- [ ] **You have run it on the desktop recently**, or have screenshots. Judging the migrated app needs a clear picture of the original.

**Decisions to have an answer for** — the agent asks, but you will answer faster if you have thought about them:

- **Spring Boot or Vaadin Boot** as the host. Spring Boot is the recommendation unless the app already uses Spring ([why](./guides/1-swing-to-emulators/build-wiring.md#BW_bootstrap)).
- **How the app ends.** On the desktop, closing the window ended the process. On a server, it must end only that user's session — every `System.exit` gets rewritten.
- **What is per user.** Anything the desktop app kept in a `static` field or a singleton — current user, open document, settings, connection — is shared by *every* user on a server unless it is moved. The agent drafts the verdicts; you confirm them.

### The main steps

The procedure is [`guide.md`](./guides/1-swing-to-emulators/guide.md): seven phases, each a list of `- [ ]` steps. The agent copies the guide into your app and ticks its copy as it goes, so the ticked guide is the progress record you can open at any time.

| Phase | What happens | Done by |
|---|---|---|
| **0 — Build and choose the host** | The original app is compiled as input for the sweep; Spring Boot or Vaadin Boot is chosen. | agent; **you confirm the host** |
| **1 — Pre-flight** | `hazard-scan` lists migration hazards (exits, singletons, threads, dates, custom layouts…); `static-sweep` lists every `static` field that needs a decision; dependencies are triaged. The reports are the worklist. | tools; agent triages; **you review the static verdicts and library routes** |
| **2 — Import rewrite** | `import-swap` rewrites imports and fully-qualified references — the bulk of the migration, in one tool run. Anything it could not decide is listed, not guessed. | tool; agent reviews the diff |
| **3 — Entry point** | `@MainWindow` on the main frame; `main()` split into process start-up and per-browser-tab start-up; the host-app seed copied in; exit paths and single-instance guards removed; background threads given the session context. | agent — the one mandatory hand edit |
| **4 — Production wiring** | `@Push`, the servlet, the error handler, the JVM flags and the JDK floor — mostly already in the seed, checked rather than written. | agent |
| **5 — Build and run** | The migrated app builds and starts. | agent |
| **6 — Verification** | WARN triage, golden-path click-through, two-session check, time-zone check, and the guardrails test that keeps the result from regressing. | agent and **you** — see [Verifying the migration](#verifying-the-migration) |

Phases 1 and 2 are mechanical and run as tools, so they give the same result every time. From Phase 3 on the work is judgement, and the guide says at each step what the decision is and where its rationale lives. The ten reference documents beside the guide are opened when a step points at one, never read up front — which also keeps the agent's context free for your code.

### Running it

**With Claude Code**, open it in the unzipped kit folder and type one command:

| Command | Migrates | Where the result goes |
|---|---|---|
| `/migrate-testapp <app>` | one of the bundled examples (`crud`, `jlawyer-shape`, `inventory`) — the way to try the process first | a copy at `SWINGBRIDGE_HOME/work/<app>/` |
| `/migrate-your-app` | your app, copied into the kit's `your-app/swing/` | a copy at `SWINGBRIDGE_HOME/your-app/1-emulators/`, with `swing/` left as the baseline to diff against |
| `/migrate-swing-app <folder>` | your app, where it already lives | **in place** in `<folder>`, as one change set to review |

Each command does the migration in the session you typed it into — no subagent — so you can watch it work, answer its questions as they come up and see where it hesitated.

**With any other agent**, substitute the two paths in [`agent-prompt.md`](./guides/1-swing-to-emulators/agent-prompt.md) and hand it over verbatim. It is the same instruction set the skills follow, and it is what the maintainers run against the example apps on every release.

**By hand**, follow [`guide.md`](./guides/1-swing-to-emulators/guide.md) yourself. Nothing in the process depends on an agent; the agent is there to do the typing.

### Where the migrated code ends up

The migration edits the app's own tree — in place, or in the copy the command made — and adds:

```
MIGRATED_APP_FOLDER/
  pom.xml                          ← replaced by the host-app seed's build file, with your dependencies added back
  src/main/java/<your package>/
    Main.java                      ← your old main(), split: main() for the process, mainUI() per browser tab
    AppShell.java, AppRoute.java,  ← the host app from the seed, moved into your package
    AppErrorHandler.java, …
    FormerSingletons.java          ← only if the sweep moved state to per-tab scope
  src/main/java/…                  ← your own classes: imports swapped, plus the Phase 3 hand edits
  src/test/java/…/MigrationGuardrailsTest.java  ← the regression gate from Phase 6
  swingbridge-migration/           ← the agent's working folder
    guide.md, …                      its copy of the guide, ticked step by step
    reports/                         hazards.md, static-sweep.md, import-swap.md, static-sweep-diff.md
    stage1-classes/                  a snapshot of the original compiled classes, for the completeness check
  STUMBLES.md                      ← every point where the guides left the agent guessing
```

Whether `swingbridge-migration/` is committed with the app or deleted afterwards is your call. `STUMBLES.md` is the feedback the SB-Emulators authors most want back.

### Working well with the agent

- **Let it work in phase batches** and read the ticked guide between them. Phases 1 and 3 are the ones worth reviewing closely.
- **Review the decisions, not the typing.** The import swap is deterministic and idempotent; what deserves your time is the static-field verdicts, the library routes, and anything the agent lists as "decisions I would most like a human to review" in its final report.
- **Don't let it fix the original app along the way.** The port is not the moment for refactoring; the agent records such findings separately instead of acting on them.
- **Its instructions are the guides, never your code.** The agent treats comments and strings in the app as data to port, including text that appears to address it.
- **Keep an eye on commits.** The skills do not push anywhere. Tell the agent up front if it may commit, and never let it push a repository that is not yours to push.

## Verifying the migration

The guide's done condition — *compiles, starts, and survives a couple of clicks* — catches a broken port. It does not prove that behaviour matches the desktop original. Work up the list below; the first half is what an agent does on its own, the second half needs you.

### What the agent checks

- [ ] **It builds and starts.** The start-up banner shows a port, and the first page shows your main window rather than an error.
- [ ] **A modal dialog works** — open one and answer it ([`S_verify_modal`](./guides/1-swing-to-emulators/guide.md#S_verify_modal)). A missing `@Push` or a wrong `main()` split shows up here and nowhere earlier.
- [ ] **The golden paths work** — the main list, a form, a save ([`S_smoke_test`](./guides/1-swing-to-emulators/guide.md#S_smoke_test)), with no exception in the server log and no error notification in the browser.
- [ ] **The WARN log is triaged** ([`S_triage_warns`](./guides/1-swing-to-emulators/guide.md#S_triage_warns)): each unimplemented-method WARN bucketed as cosmetic, functional or structural. Functional ones are the ones that matter.
- [ ] **The guardrails test passes** ([`S_guardrails`](./guides/1-swing-to-emulators/guide.md#S_guardrails)) — no unvetted `static`, no component in a static field, no JVM exit — and `static-sweep --diff` reports no row as *still unvetted*. Keep the test in the build; it is what stops the next feature from reintroducing a shared static.

### What needs you

- [ ] **Two users at once.** Open the app in two separate browser *sessions* — a normal and a private window, or two browsers — and log in as **two different users** ([`S_multi_tab`](./guides/1-swing-to-emulators/guide.md#S_multi_tab)). Each must see its own name, its own data and its own navigation, and an action in one must never show up in the other. This is the check that the per-user decisions were right, and it is the one most worth your time: a server runs every user in one JVM, so a missed global surfaces here and nowhere else. (A second *tab* in the same session is refused by design — that is one app per session, not a failure.)
- [ ] **Permissions are the logged-in user's.** In the second session, try something only the first user may do. It must be refused, and the server should see the second user's identity on its calls.
- [ ] **Nothing per-user is written to the server's disk.** A desktop app often keeps settings, recent files or login profiles under the user's home directory. On a server that is one directory for everyone — look in it after the two-user test. If one user's name or settings appear as the other's default, that state needs a per-user home ([`platform.md`](./guides/1-swing-to-emulators/platform.md) covers what the host machine now means).
- [ ] **Concurrent start-up.** Log both users in at the same moment, at least once. Caches built at start-up and shared across users fail only when two sessions race, and a quiet single-user test never shows it.
- [ ] **Background work reaches the right user.** Trigger whatever the app does off the UI thread — a refresh timer, an import, a mail check — and confirm the result lands in the session that started it. The server log names any thread that touched a component without the session context ([`S_wrap_executors`](./guides/1-swing-to-emulators/guide.md#S_wrap_executors)).
- [ ] **Dates in a far-off time zone.** Run a browser in a zone several hours from the server's and check the dates the app shows and saves ([`S_resolve_tz_markers`](./guides/1-swing-to-emulators/guide.md#S_resolve_tz_markers)). Same-zone testing never surfaces drift.
- [ ] **Side by side with the original.** Walk the same flows in the desktop app and in the browser. Layout is close-enough by design — a few pixels off is expected, a component in the wrong region is a bug. Behaviour should be the same: same validation, same dialogs, same results.
- [ ] **Your existing tests.** The ones that never touched Swing still pass and still mean something; the ones that drove Swing components need rewriting either way.
- [ ] **HTTPS before anyone else uses it** ([`S_serve_https`](./guides/1-swing-to-emulators/guide.md#S_serve_https)). Every value a component shows now travels over the network, passwords included.

**Reading the server log.** It is the migration's main diagnostic: unimplemented-method WARNs, the one-time warning about UI access from the wrong thread, and the stack trace behind every error notification (quoted by reference number, so the user can report it). An error in code the migration never touched usually means a stale build — rebuild clean before believing it.

## After step 1

The emulator stage is a working web app, not a waiting room — you can stay there as long as you like. When you want Vaadin-shaped code, the app moves on view by view:

1. **Your Swing app** — the starting point.
2. **Emulators** (`:emulators`) — after the import swap; Swing-shaped code running in the browser. *This README and the guide cover this step.*
3. **Surrogates** (`:surrogates`, optional) — view by view onto classes that *are* Vaadin components, with Swing-flavoured helpers.
4. **Stock Vaadin** — SB-Emulators removed.

Views you have not rewritten keep running on the emulators, but each view's code is at one layer, never a mix. Style the app as an ordinary Vaadin app — stock Aura first, your own CSS on top. And because the import swap is idempotent, a Swing mainline that keeps shipping features can still be merged into the migrated fork: merge, re-run the swap on the delta, rebuild.

**Is this the right route for your app?** SB-Emulators is one of several Vaadin offers, and Vaadin publishes no default path. [SwingBridge Streamer](https://vaadin.com/docs/latest/tools/modernization-toolkit/swing-bridge) runs the unmodified app and streams it — no source needed, and a better fit for a frozen app or one built on custom painting. A direct rewrite fits a small app whose UX you mean to redesign. [COMPARISON.md](./COMPARISON.md) sets the approaches side by side: what each leaves you owning, and where SB-Emulators is weaker.

## What's implemented

**Supported components.** `JFrame`, `JDialog`, `JWindow`, `JOptionPane`, `JColorChooser`, `JFileChooser`, `JButton`, `JPanel`, `JLabel`, `JScrollPane`, `JTextField`, `JPasswordField`, `JTextArea`, `JFormattedTextField`, `JEditorPane`, `JTextPane`, `JToolBar`, `JSplitPane`, `JTabbedPane`, `JCheckBox`, `JToggleButton`, `JRadioButton`, `JSlider`, `JSpinner`, `JProgressBar`, `JComboBox`, `JMenuBar`, `JMenu`, `JMenuItem`, `JCheckBoxMenuItem`, `JRadioButtonMenuItem`, `JPopupMenu`, `JSeparator`, `JTable`, `JList`, `JTree`, `JDesktopPane` / `JInternalFrame`, `JRootPane` / `JLayeredPane`, `Box` / `Box.Filler`. Layout managers: `FlowLayout`, `BorderLayout`, `BoxLayout`, `GridLayout`, `GridBagLayout`, `GroupLayout` (layouts are close-enough, not pixel-accurate).

**Supported plumbing.** `setAction` + `Action` routing, `InputMap` / `ActionMap` + `registerKeyboardAction`, mnemonics & accelerators, `setToolTipText`, `JComponent.setBorder` (→ CSS) + `BorderFactory`, `requestFocus` / `InputVerifier` / focus traversal, `JRootPane` / `JLayeredPane` with default-button Enter wiring.

**Concurrency.** `Timer`, `SwingWorker`, `SwingUtilities` (`invokeLater` / `invokeAndWait` / `isEventDispatchThread`).

**Platform capabilities.** Clipboard (`Transferable` / `DataFlavor`), drag-and-drop, `java.util.prefs.Preferences` (browser `localStorage`-backed; no import swap), and printing of hand-crafted `Printable`s (optional `emulators-printing` module). See [Printing and preferences](#printing-and-preferences).

Within a supported component, an individual method with no Vaadin equivalent WARNs and returns a default rather than throwing.

## Blocking dialogs

In Swing, modal dialogs *block*: `JOptionPane.showConfirmDialog(...)` doesn't return until the user answers, and Swing code relies on reading straight down the page. A web server can't normally do that — the browser only sees a response once the request thread finishes. **SB-Emulators makes blocking dialog code work unchanged**: every Swing listener runs as a *UI fiber* of [vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs), a virtual thread that suspends at the dialog without holding the server thread. (This is why Vaadin Push must be enabled — see [Requirements](#requirements).)

Three consequences, each Swing's own behaviour showing through:

- **A listener that blocks on anything other than a dialog holds the UI, as it holds the EDT.** A slow query or a `Thread.sleep` in an `actionPerformed` keeps the session waiting. Past ten seconds the server logs a WARN with the listener's stack.
- **Don't show a modal dialog from inside a `synchronized` block that another listener also enters.** On the desktop both listeners run on one EDT, which re-enters its own lock; here each is its own thread, and the session freezes.
- **A dialog nobody will answer ends its listener.** When the tab closes or the session expires with a modal open, the blocking call throws `vaadinx.BrowserSessionClosedError` — an `Error`, so `catch (Exception e)` lets it through and `finally` blocks run.

## Printing and preferences

Printing of hand-crafted `Printable`s works through a virtual PDF printer: add the optional `emulators-printing` dependency, swap one import (`java.awt.print.PrinterJob` → `vaadinx.awt.print.PrinterJob`), and `print()` renders your pages server-side into a PDF offered as a download. Printing *components* (`JTable.print()`, `printAll`-based Printables) stays out; those sites need a rewrite to a server-side report.

`java.util.prefs.Preferences` works with no code change and is backed by the browser's `localStorage`. Read preferences from inside your UI code — not from a `static` initializer or `main()`, which run too early. Machine-wide `systemRoot()` preferences are ignored: a browser can't share settings across users.

## Permanently out of scope

- **`JApplet`** — browsers dropped applets years ago, and the JDK class itself was removed in Java 26 ([JEP 504](https://openjdk.org/jeps/504)).
- **Look-and-Feel dispatch** — styling is handled in a later Vaadin + CSS pass, outside SB-Emulators.
- **User-authored painting of components via `Graphics`** — `paintComponent`, custom-painted components, and component printing (`JTable.print()`). There is no `Graphics2D` surface to override. Hand-crafted `Printable`s are the exception — see [Printing and preferences](#printing-and-preferences).

## Requirements

- **JDK 24+** at runtime. SB-Emulators' virtual-thread machinery blocks inside `synchronized` regions, which deadlocks on JDK 21 (the carrier thread is pinned); [JEP 491](https://openjdk.org/jeps/491) in JDK 24 fixes this. Your migrated app's bytecode may still target Java 21 — only the JVM it *runs* on must be 24+. An older JVM is refused at startup, naming the fix.
- **`--add-opens java.base/java.lang=ALL-UNNAMED`** on that JVM — tests and your production launch alike. The virtual-thread machinery reaches one JDK-internal constructor to run its threads on Vaadin's request thread.
- Vaadin 25 host app with `@Push` enabled.
- **HTTP requests served by platform threads**, not virtual ones: Vaadin Boot's default, and Spring Boot's as long as `spring.threads.virtual.enabled` stays off. A deployment that serves requests on virtual threads is refused, naming the fix.
- **SB-Emulators' servlet** (or, on Spring Boot, the `swingbridge-emulators-spring` dependency, which registers it for you) — it wraps the Vaadin session lock so the virtual threads can hold it. Without it, each session's first request fails with an error naming the fix. The [migration guide](./guides/1-swing-to-emulators/guide.md#S_app_servlet) has the one-line servlet.

The host-app seed the migration copies in already satisfies all five.

## License

**The licence is per artifact**, so the short answer depends on which coordinate you are declaring:

| artifact | licence |
|---|---|
| `swingbridge-emulators`, `swingbridge-emulators-printing` | [GPLv2 + Classpath Exception](./LICENSE) |
| `swingbridge-surrogates`, `swingbridge-emulators-spring` | [Apache-2.0](./LICENSE-APACHE-2.0) |
| `swingbridge-migration-annotations`, `swingbridge-migration-guardrails`, `swingbridge-migration-tool` | [Apache-2.0](./LICENSE-APACHE-2.0) |
| `swingbridge-emulators-jgoodies-forms-1.2.1` | BSD |
| `swingbridge-emulators-jcalendar-1.4` | LGPL-2.1 |

Each jar's pom declares its licence in a `<licenses>` block — its own, or the Apache-2.0 one it
inherits from `swingbridge-emulators-parent` — and the jar carries that licence's text inside itself under
`META-INF/`, so a coordinate you resolved without reading this page still tells you what it is.

**The emulators are GPLv2 + Classpath Exception because they are a derivative work of OpenJDK.** They
reproduce the `javax.swing` / `java.awt` API and were written with OpenJDK's own source open as the
specification, so they take OpenJDK's terms rather than picking their own.

**Linking your application against them creates no obligation to publish your source** — exactly as
running your application on OpenJDK does not. That is what the Classpath Exception is for: it grants
permission "to link this library with independent modules to produce an executable, regardless of the
license terms of these independent modules". Your application is an independent module. What GPLv2
does reach is SB-Emulators itself: modify our files and distribute the result, and those
modifications are GPLv2.

**`swingbridge-surrogates` is Apache-2.0**, which matters mainly at stage 3: the `S*` classes your
views get rewritten onto are permissive, so modifying and redistributing *them* carries no copyleft
obligation at all. `swingbridge-emulators-spring` — the Spring Boot wiring — is Apache-2.0 for the
same reason: Vaadin wrote it, and it derives from nothing in OpenJDK. So are the migration artifacts:
the annotation and the guardrails your own code and tests compile against, and the migration tool.
The one third-party library on the runtime path that is not Vaadin's own,
[vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs) — the virtual-thread
machinery that lets a modal dialog park, which `swingbridge-emulators` pulls in — is Apache-2.0 as
well.

[`PROVENANCE.md`](./PROVENANCE.md) records how the code was made, which tree is under which licence,
which files carry Oracle's copyright notice and why, and what the exception means for you in one
paragraph. Outside the jars, everything Vaadin wrote — this README, the migration guides, the
distribution kit's own files — is Apache-2.0 too. (GitHub's own detector reads the root `LICENSE`
and reports one licence for the whole repository; the table above is the accurate answer.)

**The third-party library add-ons are licensed differently, and deliberately so: each carries the
licence of the upstream library whose API it reproduces**, not SB-Emulators'. So
`swingbridge-emulators-jgoodies-forms-1.2.1` is **BSD** (a fork of JGoodies Forms) and
`swingbridge-emulators-jcalendar-1.4` is **LGPL-2.1** (a clean reimplementation of JCalendar 1.4's
`JDateChooser`, which takes upstream's terms anyway). That is the same licence your application
already accepted for the library the add-on replaces, so swapping one for the other changes nothing
about your position. Each module states its licence in its pom's `<licenses>` block and its
`PROVENANCE.md`, and ships the licence text inside its own jar at `META-INF/LICENSE`; each
`MIGRATION.md` says in one paragraph what those terms ask of you.

Both add-ons are **starter prototypes**: free to use, bounded to what one real migration needed, and
shipped as-is — no further development is planned. Each add-on's `MIGRATION.md` states exactly what
it provides.

## Further reading

- [Migration guide](./guides/1-swing-to-emulators/guide.md) — the step-by-step procedure, and the ten references beside it.
- [Agent prompt](./guides/1-swing-to-emulators/agent-prompt.md) — the instruction set for any agent.
- [Migration tools](./migration-tool/README.md) — `hazard-scan`, `static-sweep` and `import-swap`: options, example reports, what each declines to do.
- [COMPARISON.md](./COMPARISON.md) — how SB-Emulators compares with the other Swing-to-web approaches and with a direct rewrite.
- [CLAUDE.md](./CLAUDE.md) — terminology, the migration arc, and the authoritative scope reference.
- [DEVELOPING.md](./DEVELOPING.md) — building SB-Emulators itself.
