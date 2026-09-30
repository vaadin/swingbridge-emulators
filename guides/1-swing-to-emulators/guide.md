# Migrate a Swing app to Vaadin — Step 1: Swing to emulators

You have a running Swing desktop app. You want it running in a browser. This guide walks you through step 1 of the migration: import-swap your Swing imports to `vaadinx` equivalents, recompile against `:emulators`, run as a Vaadin web app.

The work is **mostly mechanical** — most of it is one tool run over your imports (Phase 2) — with one mandatory non-mechanical refactor (splitting your `main()` into a process-level entry and a per-UI entry) and a handful of app-dependent hazards (`System.exit` calls, static `JFrame` singletons, custom `LayoutManager`s) that some apps need to refactor and some don't.

Code shape stays Swing-shaped throughout. Your `JFrame` is still a `JFrame` (an emulator one). Your `ActionListener` still fires on button click. Your `JTable` model still feeds rows. You can iterate on styling in CSS without touching code.

Step 1 is the first of three migration steps. Step 2 rewrites your views one at a time onto `:surrogates`, where the code shape becomes Vaadin's rather than Swing's; step 3 is the final rewrite to native Vaadin components, at which point SB-Emulators is out of the picture. Both are out of scope for this guide — and neither is a prerequisite for running in the browser, which step 1 alone gets you.

## How to read this guide

**Each phase is a list of `- [ ]` steps.** A step says what to run or edit, one sentence of why, and
links a reference document when there is more to know. **The references are not required reading** —
open one when the step you are on points at it.

**Work off your own copy and tick as you go.** Copy this whole folder into the app you are migrating,
so the ticks and any notes live with the migration rather than in the kit:

```bash
cp -r SWINGBRIDGE_HOME/guides/1-swing-to-emulators MIGRATED_APP_FOLDER/swingbridge-migration
```

Copy the **folder**, not this one file — the references link each other relatively. Whether you commit
it with the app or delete it when the migration is done is your call.

## Conventions

Two placeholders appear in commands and paths throughout this guide. Substitute both with real
absolute paths before running anything — an agent driving this guide is handed them already
substituted.

- **`SWINGBRIDGE_HOME`** — the folder holding the SwingBridge Emulators kit: this file's own
  grandparent, i.e. the directory you unzipped, or your clone of the repository. (In a downloaded
  kit, it is the folder holding the welcome `README.md`.)
- **`MIGRATED_APP_FOLDER`** — the root of the app you are migrating. The default assumption is an
  **in-place** migration: you have version control, and the import swap is one commit.

## What this guide covers — and doesn't

This guide focuses on the hard parts of the migration: the import-swap rules, the seams where Swing and `:emulators` disagree, the wiring you can't grep for, the app-dependent hazards. **Mechanical cleanup that your IDE handles well — pruning unused imports, removing now-dead helper methods after a refactor, formatting — is out of scope.** Run it as a separate cleanup round at the end. (Phase 2's tool is an exception you don't have to think about — it emits only the imports a file actually references, so it leaves no unused-import cleanup of its own behind.)

## What won't migrate

Some Swing capabilities are **permanently out of scope**:

- **`JApplet`** — removed from the JDK in Java 26 ([JEP 504](https://openjdk.org/jeps/504)); browsers dropped applet runtimes years ago.
- **Look-and-Feel dispatch** — `BasicButtonUI`, `MetalLookAndFeel`, `UIManager.setLookAndFeel`. Use Vaadin + CSS for styling.
- **User-authored paint of components** — `paintComponent(Graphics)`, custom-painted components, and component printing like `JTable.print()`. *(Hand-crafted `Printable.print(Graphics)` is the exception — it renders to a downloadable PDF via the optional `:emulators-printing` module.)*

**Migrating a Spring-based Swing app is currently unsupported.** Nothing stops the migration from running, but nothing checks the hazard it carries either: a desktop `ApplicationContext` serves one user, a web one serves every user at once, so a singleton bean holding per-user state (current user, open document, selection) leaks it from one user to the next — and because bean state lives in instance fields, the Phase 1 `static` sweep and the Phase 6 guardrails cannot see it. *Using* Spring Boot to host an app that had no Spring is fine and fully supported — see [`build-wiring.md`](./build-wiring.md#BW_bootstrap).

**Printing** is supported for hand-crafted `Printable`s: add the optional `:emulators-printing` dependency and swap one import (`java.awt.print.PrinterJob` → `vaadinx.awt.print.PrinterJob`), and `print()` renders your pages server-side into a PDF offered as a download. Printing *components* (`JTable.print()`, `printAll`-based Printables) stays out of scope — those sites need a rewrite to a server-side report.

`java.util.prefs.Preferences`, on the other hand, is **fully supported — with no code change at all**: your prefs code keeps its `java.util.prefs` imports untouched (there's nothing to swap — it was never a `javax.swing` type), and SB-Emulators backs it with the browser's `localStorage`. One rule, and it's the same one the time-zone setup already follows: read preferences from inside your UI code (the `SwingUtilities.invokeLater` where you build your window, or an event listener) — not from a `static` initializer or your `main()` body before the app starts, which run too early and throw `IllegalStateException`. Machine-wide `systemRoot()` preferences are the exception: a browser can't share settings across users, so those are ignored (writes WARN, reads come back empty).

If your app's value is mostly in the surfaces above, step 1 doesn't help yet — rewrite first or wait. Otherwise, in-scope features keep working; the rest surfaces as WARN logs you can triage. For the full list of supported classes, see [Supported components](#supported-components) below.

## The migration

### Phase 0 — Build the app, and choose its bootstrap

**Build it once, before you change anything.** Not as a smoke test — as an input: Phase 1's
[`static` sweep](./static-fields.md) reads your app's **compiled classes**, so they have to exist and
be current. And decide now what will host it, so that Phase 3 is a copy rather than a choice.

- [ ] <a id="S_choose_bootstrap"></a>**Choose the bootstrap — Spring Boot or Vaadin Boot — and write the choice down** under this step in your copy of the guide. It decides which seed you copy in Phase 3 and which host-app doc you read; every other step is the same on both. **The recommendation is Spring Boot, unless the app is already built on Spring — then Vaadin Boot** (look for `org.springframework` in its build file); the table and why: [`build-wiring.md` § Which bootstrap?](./build-wiring.md#BW_bootstrap). **An agent migrating for someone asks them to confirm or change that recommendation before going on.** If the answer is Spring Boot, ask one more thing: the packaged app will be launched as a **fat jar** with `java -jar` — the recommendation — or with `java -cp` on a classpath of their own? Nothing in the migration changes with that answer; it decides the launch line handed over at the end ([`host-app-spring-boot.md`](./host-app-spring-boot.md#SBS_launch)). Running unattended with nobody to ask? Take both recommendations and say which you took.

- [ ] <a id="S_build_stage1"></a>**Build the stage-1 app.** `cd MIGRATED_APP_FOLDER && mvn -q compile` — or `./gradlew classes`, or whatever your build is — so that `target/classes` exists. Rebuild whenever you edit source and want the sweep re-run; `static-sweep --src` warns you when the classes have gone stale behind the sources. If you find a `target/` (or `build/`) an IDE wrote, delete it and use `clean` from then on: some IDEs compile with their own compiler, which rejects code `javac` accepts, and a build failing on classes you did not write reads as a bug in your migration.

### Phase 1 — Pre-flight

Scope the migration before touching code. Two tool runs produce the worklist; the third step is
reading it.

- [ ] <a id="S_run_hazard_scan"></a>**Run the hazard scan.** It applies every migration-hazard pattern in one pass and writes them up grouped by hazard, so the report *is* this phase's worklist:

  ```bash
  SWINGBRIDGE_HOME/tools/bin/hazard-scan \
      MIGRATED_APP_FOLDER/src/main/java MIGRATED_APP_FOLDER/src/main/resources \
      --report MIGRATED_APP_FOLDER/swingbridge-migration/reports/hazards.md
  ```

  Point it at your resources too, not just your sources: one hazard's evidence is in the app's own HTML files. On Windows, `SWINGBRIDGE_HOME\tools\bin\hazard-scan.cmd` takes the same arguments. The launcher is a five-line script over `java -cp "…/tools/lib/*"`, so **a JDK is all it needs** — no Maven, no network. Options: `--report <path>` (Markdown to a file instead of stdout, creating its directory), `--list` (print the pattern table and exit). All three phase reports go beside your copy of this guide rather than into `target/`: later phases read them back, and a `clean` would erase them.

  **A finder, not a gate.** It exits 0 whenever it ran; every hit is a candidate to triage, and false positives are expected — a `SimpleDateFormat` hit is fixed by Phase 2's import swap alone. Clean sections are printed rather than omitted, and the hazards no scan can catch come out as a closing checklist.

- [ ] <a id="S_run_static_sweep"></a>**Run the `static` sweep** over the classes Phase 0 built. This is the half of the pre-flight that produces work rather than warnings:

  ```bash
  SWINGBRIDGE_HOME/tools/bin/static-sweep \
      MIGRATED_APP_FOLDER/target/classes \
      --src MIGRATED_APP_FOLDER/src/main/java \
      --report MIGRATED_APP_FOLDER/swingbridge-migration/reports/static-sweep.md
  ```

  One row per `static` field that needs a decision, with its declared type, whether that type is a **component** (subclasses included, resolved through the hierarchy), the line it is declared on, and **which methods write it outside initialization** — the read-mostly question answered rather than asked. Every mechanical column of [`static-fields.md`](./static-fields.md)'s review table is pre-filled; the verdicts are yours, and they are the only part that was ever a judgement.

  **Then keep a copy of those classes** — `cp -r MIGRATED_APP_FOLDER/target/classes MIGRATED_APP_FOLDER/swingbridge-migration/stage1-classes` — and re-take it whenever you rebuild before Phase 2. The completeness check at the end diffs against them, and from Phase 2 on your build overwrites `target/classes` with the migrated app.

  **If your app has dependencies, add `--lib MIGRATED_APP_FOLDER/target/dependency`** (after `mvn dependency:copy-dependencies`) **or `--cp`** to put them on the resolution classpath. An app with no dependencies has no such directory and needs neither. Both are optional either way: a type that will not load is reported **unresolved** and stays on the worklist, never quietly dropped from it. `--src` compares the newest source against the newest class file and warns when your build has gone stale — read that line before the report under it. Nothing in your app runs: classes load without initialising, so a `<clinit>` that would open a database connection is read as a type and no more.

- [ ] <a id="S_triage_reports"></a>**Triage the two reports.** Each hazard's decision is made in one place, and the point of this step is to route rather than to re-derive:

  | hazard the scan reports | where the decision lives |
  |---|---|
  | `System.exit` / `Runtime.halt`; cancelable tab close | [`lifecycle.md` § How your app ends](./lifecycle.md#how-your-app-ends) — including the one exit that is *not* fixed here, the `else` of a single-instance guard |
  | static frame singletons; multi-tab / user-scoped singletons | [`static-fields.md`](./static-fields.md), starting at its [Step 0](./static-fields.md#step-0--classify-your-modules-before-you-touch-a-field) module classification. The sweep report is its worklist |
  | timezone drift (three buckets), shared mutable `Calendar` | [`dates.md`](./dates.md) — bucket; tag buckets 2–3 and shared `Calendar`s with `// TODO[browser-tz]:` (bucket 1 gets no marker), but **do not resolve the markers yet**: the helpers throw until Phase 4 wires the init listener |
  | background threads the app starts itself (`new Thread`, `Executors.`, `ExecutorService`) | [Phase 3's wrap step](#S_wrap_executors). Nothing to decide here beyond which pools are yours — note the scan also matches pools that touch no component and format no user-facing date, and those can be left alone |
  | JVM-wide uncaught-exception handler | drop the registration; the [`ErrorHandler`](#S_error_handler) in Phase 4 replaces both it and whatever `showError` helper it dispatched through, which becomes dead code |
  | custom `LayoutManager` | a hand-rolled pixel layout can't be translated (the browser has no synchronous child measurement to drive `setBounds`), so it renders as a **vertical stack with a WARN** — the app runs, the layout isn't faithful. Rewrite to a built-in (`FlowLayout` / `BorderLayout` / …), or implement `vaadinx.awt.CssEmittingLayoutManager` to express the layout's intent as CSS (`containerCss` + optional `childCss`, the same seam every built-in uses) |
  | `JFileChooser.showDialog`; `HTMLDocument` mutators; HTML pane links; `Preferences` reads; unswapped printing | [`runtime-contract.md`](./runtime-contract.md) — what throws, what WARNs, and what the throw is protecting you from |
  | password fields (`JPasswordField`) | [Phase 4's HTTPS step](#S_serve_https). Nothing to change in the code; it is what makes that step urgent |
  | reading layout numbers (`getPreferredSize`, `getBounds`, …) | [Phase 6's audit step](#S_check_layout_code). Nothing to decide here; the scan lists the sites the audit reads |
  | fully-qualified `java.awt.Component`; `instanceof` on ported event types | Phase 2's tool handles both; [`import-swap-reference.md`](./import-swap-reference.md) has the rule if you are rewriting by hand |
  | modal dialogs from listeners | Phase 4 is that recipe. Nothing to decide here — just don't skip it |
  | `synchronized` blocks and methods | [`runtime-contract.md` § Modal dialogs inside `synchronized`](./runtime-contract.md#modal-dialogs-inside-synchronized) — only the ones that guard a modal dialog matter, and most guard none |

- [ ] <a id="S_triage_dependencies"></a>**Triage every Swing-touching dependency**, and do it now rather than at build time: it is the single biggest driver of how long the migration takes. Every one needs source access or a recompilation path — compiled jars must be recompiled against `:emulators`, and binary drop-in is not supported. Check [`addons.md`](./addons.md) first: JCalendar 1.4 and JGoodies Forms 1.2.1 already have pre-built add-ons. For the rest, two `javap` greps over each jar say whether an import-swap works, misbehaves, or merely compiles — see [`third-party-libraries.md`](./third-party-libraries.md). Look-and-Feel libraries get deleted outright; anything closed-source is a blocker to resolve now.

### Phase 2 — Import rewrite

The mechanical part. Most apps land 80%+ of the migration here — and **it is a tool run, not a set of
rules you apply by hand.**

- [ ] <a id="S_run_import_swap"></a>**Run the import swap.** Point it at your source root, not the module root. The kit's `tools/lib/` already holds the emulators jar and both add-on jars, so their swap tables are on the classpath without you assembling one:

  ```bash
  SWINGBRIDGE_HOME/tools/bin/import-swap \
      MIGRATED_APP_FOLDER/src/main/java --dry-run \
      --report MIGRATED_APP_FOLDER/swingbridge-migration/reports/import-swap.md
  ```

  Read the report, then drop `--dry-run` to apply. Options: `--report <path>`, `--dry-run`, `--table <tsv>` (feed a table directly, if you'd rather not resolve the jars). Exit status is 0 whenever the tool ran — **including when it declined files** — and non-zero only when it could not run at all.

  **What it does, in two passes.** Imports — every wildcard shape included — *and* in-code fully-qualified references (`new javax.swing.JTextField()`, a `java.awt.Component` parameter type). The second matters more than it sounds: NetBeans/Matisse form code writes every widget fully-qualified and imports no Swing at all, so on a builder-authored app the import half alone would change nothing. Each referenced name is resolved the way `javac` resolved it when the file last compiled, so an existing `import java.util.List;` still wins over a `java.awt.*` wildcard, exactly as before.

  It is **idempotent** (a second run changes nothing) and **tolerant of a half-swapped tree**, so re-running after hand edits is safe.

- [ ] <a id="S_read_declines"></a>**Read the declined section of the report.** **The tool declines rather than guesses**: an import it cannot parse, or a simple name that two deleted wildcards both supply, leaves the whole file untouched with the reason given. So does a type under an add-on's package root that no add-on maps — `com.jgoodies.forms.builder.PanelBuilder` reports "no mapping" instead of waiting to become a compile error. That section is what the rest of this guide triages, and on a library-heavy app a suspiciously clean report means a missing add-on jar rather than good news.

  **The classpath is the configuration.** The tool has no compiled-in knowledge of Swing: it reads every `META-INF/emul/ported-types.tsv` on its classpath and unions them, so what is in `lib/` decides what gets rewritten. A `lib/` with no table-carrying jar makes the tool exit non-zero naming the fix, rather than reporting a successful run that rewrote nothing. Invoke it through `tools/bin/`, never `java -jar` — `-jar` ignores `-cp` and would leave the tables invisible.

  **Without the kit** (you came by Maven coordinate), download `swingbridge-migration-tool-<version>-dist.zip` from Maven Central (`com.vaadin.swingbridge:swingbridge-migration-tool:<version>:zip:dist`), unzip it, and drop one jar into its `lib/` per swap table you need — `:emulators`, plus one per [add-on](./addons.md) your app uses:

  ```bash
  mvn -q dependency:copy -DoutputDirectory=lib \
      -Dartifact=com.vaadin.swingbridge:swingbridge-emulators:<version>
  bin/import-swap src/main/java --dry-run --report import-swap.md
  ```

  The hazard scan of Phase 1 ships in the same zip and needs nothing added to `lib/` — its pattern table is inside the tool's own jar.

- [ ] <a id="S_review_diff"></a>**Review the diff.** The buckets are surprising in places — `GridBagLayout` moves while `GridBagConstraints` doesn't, `ButtonGroup` moves while `Action` doesn't, `Toolkit` moves and its `datatransfer` payload types don't. [`import-swap-reference.md`](./import-swap-reference.md) is the whole swap table written out in prose, with the seven watch-outs that make a diff readable. It is also what you follow if you are rewriting by hand instead.

### Phase 3 — Entry-point setup

This is the one mandatory hand edit. Vaadin's request lifecycle and Swing's `main()` thread don't
share a clock: process-level work (logging, command-line parsing, config) needs to happen at JVM
startup, while UI construction needs to defer to per-UI activation (per browser tab). The split makes
both happen in the right place.

- [ ] <a id="S_annotate_mainwindow"></a>**Annotate your main `JFrame` subclass with `@MainWindow`** (`import vaadinx.swing.MainWindow;`). The class may be `final` — the framework constructs it directly and never subclasses it.

- [ ] <a id="S_copy_seed"></a>**Copy your bootstrap's seed into the app** — the host-app classes and resources, plus a complete build file you will edit in [the last step of this phase](#S_wire_build). The kit builds each seed exactly as shipped, so start from it rather than writing these files:

  ```bash
  SEED=SWINGBRIDGE_HOME/guides/1-swing-to-emulators/seed/spring-boot    # or …/seed/vaadin-boot
  cp -r "$SEED/src/." MIGRATED_APP_FOLDER/src/
  cp "$SEED/example-pom.xml" MIGRATED_APP_FOLDER/pom.xml    # Gradle: the vaadin-boot seed's example-build.gradle.kts
  ```

  Then **move the seed's `com.example.app` classes into your own package** — typically the one holding your main `JFrame`, or its parent — and keep them together there, so the framework wiring is easy to spot; you don't need to rename the package, even if it still contains a `.swing` segment. **If your package already has a class with one of the seed's names** (`Main` most often, but also `AppShell`, `AppRoute`, `AppErrorHandler`, or `AppServlet` on Vaadin Boot), **rename yours first**, for example to `OldMain`. The move would otherwise overwrite it before [`S_split_main`](#S_split_main) has taken anything out of it, and you delete it at the end of that step. What each file is for, and which parts of it are not a free choice: [`host-app-spring-boot.md`](./host-app-spring-boot.md) or [`host-app-vaadin-boot.md`](./host-app-vaadin-boot.md). Your app's old build file stays in version control; [`S_wire_build`](#S_wire_build) adds your dependencies back from it. The seed files are **0BSD** (`seed/LICENSE`): once copied they are yours, to license as you choose, and their headers need not be kept.

- [ ] <a id="S_split_main"></a>**Split your existing `main()` in two, into the seed's `Main`:** its `main()` keeps its one bootstrap call at the end, and gains your process-level work before it; its `mainUI()` gains your frame construction.

  ```java
  public class Main {
      public static void main(String[] args) throws Exception {
          // process-level: runs once per JVM at startup
          configureLogging();        // illustrative — drop if your app has nothing like this
          loadConfig(args);          // illustrative
          // ...the seed's bootstrap call stays last: it blocks until the server stops
      }

      public static void mainUI() {
          // launch-level: runs once per browser tab — NOT once per UI. F5 builds a
          // fresh UI but @PreserveOnRefresh keeps the route, so this does not re-run.
          SwingUtilities.invokeLater(() -> new MyMainFrame().setVisible(true));
      }
  }
  ```

  Then delete your old `main()`, or its whole class if that was all it held. If it had no JVM-level work beyond frame construction, the seed's `main()` stays exactly as shipped, and that's fine. (Keeping your own class instead works too — move the seed's bootstrap call and its annotations into it — but then the build file's main class and `AppRoute` must name yours.)

  **The `SwingUtilities.invokeLater(...)` wrapper is load-bearing.** `mainUI()` runs on the Vaadin UI thread but **not** on a virtual thread; `vaadinx.swing.SwingUtilities.invokeLater` routes through `EHelper.callSwing`, the virtual-thread envelope that lets a blocking dialog reachable from `mainUI()` park instead of deadlocking the request thread. If your `main()` already wrapped frame construction this way, Phase 2 gave you the envelope for free. `java.awt.EventQueue.invokeLater(...)` counts as already wrapped — it delegates to the same place.

  **Start-up work that is neither obviously process-level nor obviously UI-level splits by owner:** schema / seeding **a shared database** / first-run bootstrap account → `main()` (once per deployment); anything that *recorded a launch* — an audit row, a per-launch counter → `mainUI()` (once per app instance). From `mainUI()` a database seed re-runs per tab and races itself; from `main()` an audit row records the server, not a user. The deciding property is what the work writes *to*, not what it is called: seeding an in-memory store the frame owns is per-launch and stays in `mainUI()` — hoisting it to `main()` would create the shared `static` Phase 1 removes. A first-run bootstrap account is the one item on that list that *sounds* per-user and is not: it writes a row every later session reads. Full hook table and what each can reach: [`lifecycle.md`](./lifecycle.md).

- [ ] <a id="S_add_route"></a>**Check the seed's `AppRoute` mounts your app**: its `bootstrap()` calls `Main.mainUI()`, which is all it needs if you kept the seed's `Main`. **Vaadin does not auto-discover `@MainWindow`** — without this class the app compiles cleanly and a request to `/` returns 404. `@Route("")` / `@PreserveOnRefresh` are stock Vaadin Flow (`com.vaadin.flow.router.*`) — not vaadinx-rewritten. `MainWindowRoute` lives in `vaadinx.swing.app`; `@MainWindow` is one package up in `vaadinx.swing`. `bootstrap()` (no args, no checked exceptions) runs once per route instance, so once per browser tab; `@PreserveOnRefresh` keeps the same frame instance across refreshes.

- [ ] <a id="S_delete_single_instance_guard"></a>**Delete any single-instance guard** — a `ServerSocket` on a fixed port with a `localhost` probe, a `.lock` file / `FileLock` / PID file, `SingleInstanceService`, a named mutex — along with its call site and any raise-the-running-window handler. Keep the startup work that sat beside it. This is not merely redundant: the port or lock is per *JVM*, so the first session takes it and every later session's probe succeeds, hits the `System.exit(0)` branch, and takes the server down with every other user's session. Shapes to recognise and the full reasoning: [`lifecycle.md` § How your app ends](./lifecycle.md#how-your-app-ends).

- [ ] <a id="S_rewrite_exit_paths"></a>**Rewrite every exit path.** `dispose(); System.exit(0)` becomes a bare `dispose()` — disposing the app's last displayable window ends the user's session, exactly as it ended the JVM on the desktop, whatever the `defaultCloseOperation` says. A window left open or a `Timer` left running keeps the app alive. Non-`@MainWindow` JFrames rendered as Vaadin Dialogs additionally throw on an `EXIT_ON_CLOSE` close-attempt — use `DISPOSE_ON_CLOSE` or `HIDE_ON_CLOSE` there. Details and the `UI.getCurrent().getSession().close()` cases: [`lifecycle.md` § How your app ends](./lifecycle.md#how-your-app-ends).

- [ ] <a id="S_wrap_executors"></a>**Wrap every executor your app builds, and carry the context onto every `Thread` it starts by hand.** Phase 1's hazard scan listed them (`new Thread` / `Executors.` / `ExecutorService`). A pool gets it with one line where the pool is built — `this.pool = EmulatorContext.wrap(Executors.newFixedThreadPool(4));` — and nothing changes at the call sites; a hand-rolled `Thread` captures `EmulatorContext.get()` on the UI thread and runs its body inside `ctx.run(...)`. Without this a background thread has no session to reach the browser through, so **updating a component from it throws** and dates on it use the server zone. `SwingWorker` needs nothing. ***Watch out:*** wrapping the pool is only half — the **submit** must also run on a thread that has a context (a UI thread, a `SwingWorker`, or another wrapped task), so a pool fed by a timer thread you own still fails; the error names the submitting frames when that happens. Recipes for both shapes: [`dates.md` § Background threads](./dates.md#2-background-threads--carry-the-context-with-emulatorcontext).

- [ ] <a id="S_triage_platform"></a>**Triage every platform check, `Runtime.exec` and `Desktop.getDesktop()` call by intent.** Backend-serving commands stay as-is (check the working directory, and that the binary exists on the *server*); user-serving ones — `cmd /c start`, `xdg-open`, `open`, `explorer.exe`, `browse`, `print` — must be re-homed to the browser (a download, an `Anchor`, `Page.open`, the virtual PDF printer). An OS check itself is fine and now describes the server; what matters is what it gates. **Migrate the intent, not what happens on your dev box** — a Windows-only feature failing on your Linux workstation is not a reason to drop it. Routes, the replacement table, and the native-code cases that genuinely break: [`platform.md`](./platform.md).

- [ ] <a id="S_wire_build"></a>**Edit the build file you copied with the seed — don't assemble one.** Change your coordinates, point the main class at the end of the file at your `Main` in its new package, set `swingbridge-emulators.version`, and add back your app's own dependencies from its old build file — minus the Look-and-Feel libraries and minus anything an add-on replaced. **Every seed build file pins Java 24**, which is a runtime requirement before it is a compile one, and carries the pieces that fail in ways the error message does not name if you assemble them yourself — the `--add-opens` wiring first among them. The comments say which edits are safe and which are not; the reasoning is in [`build-wiring.md`](./build-wiring.md).

  The only host-app file the seed cannot give you is `FormerSingletons`, if the sweep routed anything to tab scope — which it usually does, tab being its default ([`former-singletons.md`](./former-singletons.md)).

### Phase 4 — Production wiring

**Mostly already done by the seed you copied** — this phase says what each piece is for, so you know
not to "tidy" it away. It is **non-optional if your app uses modal dialogs**: without it,
`JOptionPane.showMessageDialog` and modal `JDialog` calls never reach the browser.

- [ ] <a id="S_app_shell"></a>**Keep the seed's `AppShell` — `@Push` and the Aura stylesheet.** `@Push` is what makes blocking modal dialogs work at all. The `@StyleSheet(Aura.STYLESHEET)` is **not optional** either: Vaadin auto-loads its default theme only for an app that defines *no* `AppShellConfigurator`, and this class is one. Style the app afterwards as a normal Vaadin app — stock Aura first, your own CSS on top. Keep exactly one `AppShellConfigurator` in the project; a second is a start-up error.

- [ ] <a id="S_app_servlet"></a>**Keep the servlet wiring: on Vaadin Boot the seed's `AppServlet`, on Spring Boot nothing at all.** The Vaadin Boot seed's `AppServlet` extends `SwingBridgeVaadinServlet` (from `vaadinx.swing.app`) and exists only to carry its `@WebServlet` mapping, which is yours to choose — `urlPatterns = {"/*"}` and `asyncSupported = true` **are** load-bearing, the `name` is not. On Spring Boot, `swingbridge-emulators-spring` registers the equivalent servlet for you, and writing one of your own is worse than redundant ([`host-app-spring-boot.md`](./host-app-spring-boot.md)).

  **What the base class does:** it routes the session lock through `VirtualThreadAwareLock`. The session's `ReentrantLock` is held by the carrier platform thread, not the virtual thread mounted onto it, so the stock `isHeldByCurrentThread()` check returns `false` inside a virtual-thread continuation — and a virtual thread that *takes* the session lock never gets it, recursing until `StackOverflowError`. The wrapper makes every lock operation bookkeeping-only on those virtual threads and the real lock everywhere else. **Anything of yours that serves the app needs the same routing**, tests included — the continuation runs on a virtual thread there too. SB-Emulators refuses to start its virtual-thread executor on a session whose lock is unwrapped, so a missed routing is an exception naming this step rather than a hang.

  ***Watch out: if you need your own `VaadinServletService`, override `createSwingBridgeService`, not `createServletService`*** — on either servlet. The latter is `final` precisely because overriding it is how the lock wrap gets dropped silently, and a dropped wrap surfaces later and elsewhere as a `StackOverflowError` inside Vaadin. The seam hands you the already-wrapped service to subclass; the class javadoc has the shape.

- [ ] <a id="S_error_handler"></a>**Adapt the seed's `AppErrorHandler` to what your Swing app did on an uncaught exception.** It is installed from a service-init listener rather than on the servlet, because it is your app's policy, not SB-Emulators' plumbing. As shipped it logs every uncaught UI error under a reference number and shows the user a `Notification` quoting that number. It is registered through the seed's SPI file on Vaadin Boot, and as a `@Component` on Spring Boot.

  **Why you need one, even if your Swing app had none:** it catches uncaught exceptions inside event listeners, attach/detach hooks and value-change callbacks — the moral equivalent of Swing's `Thread.setDefaultUncaughtExceptionHandler` for UI threads, and **the only thing that consults your app's handler**: a JVM-wide `Thread` handler is never read back, so whatever your app registered there has to move here (the hazard scan flags it; the two-step split is in its *Fix*). It runs with a current UI, so a handler ported from the desktop can still open a dialog — through `SwingUtilities.invokeLater` (below). Without it Vaadin logs the exception and the user sees nothing change — which *looks* like what your desktop app did with no handler, but is not: there the stack trace went to `System.err` **on the user's own machine**, a console or log file their help desk could collect, while here it goes to the server log, which the user behind a browser has no way to see. The failure would be hidden from the one person who witnessed it.

  **Why the counter is not `static`:** the SPI (or the Spring context) creates exactly one of these per deployment, so an instance field already has the lifetime a per-deployment sequence needs — which keeps this scaffolding off [Phase 6's static gate](#S_guardrails) entirely, with no `@IntentionallyStatic` to justify.

  **The ref number is what replaces that lost channel**: the user quotes it to support, who greps the server log for `#42` and finds the stack trace. A bare "an error occurred" tells the user something broke and gives nobody a way to find it. **Show the user exactly one signal:**

  | your Swing app had | what `AppErrorHandler` does |
  |---|---|
  | no handler | keep it as shipped — log plus the ref-numbered `Notification` |
  | a handler that shows the user something (an error dialog, a status-bar message) | port that body here in place of the `Notification`, inside `SwingUtilities.invokeLater(...)`; don't toast on top of it |
  | a handler that only logs (a file, a reporter) | port the logging, and keep the `Notification`, for the same reason as the no-handler row |

  **Open a ported dialog through `SwingUtilities.invokeLater`, always**, even if your desktop handler called it directly or chose with `EventQueue.isDispatchThread()`. The handler sometimes runs on a thread a modal dialog cannot block, and there `isDispatchThread()` still answers `true`, so the desktop idiom calls the dialog directly and it throws inside your error handler. `invokeLater` gives the dialog a thread it can block on, wherever the error came from.

  **The stack trace stays in the log, never in the browser**, whatever surface shows the error: the `Notification`, or a dialog ported from your desktop handler. Traces leak class names, paths, credentials in messages and library versions useful for CVE matching, and the exception's own message leaks the same way. So a desktop `showError` that put the trace into a `JOptionPane` ports as the same dialog with the ref-number sentence in place of the trace, and a title built from the exception (its class name, its message) becomes a plain one such as `"Error"`. Showing the trace in the browser is a deliberate risk to take explicitly, not by default.

  The seed's `if (UI.getCurrent() != null)` guard is not optional either: if you also keep a JVM-wide handler for your non-UI threads, it gets invoked where there is no UI, and an unguarded UI call then throws from inside your error handler.

- [ ] <a id="S_add_opens"></a>**Keep `--add-opens java.base/java.lang=ALL-UNNAMED` on every JVM that runs the app** — the virtual-thread executor behind blocking dialogs requires it, and without it the app refuses to start. The seed build file already passes it to the dev run and the test JVM; what is left for you is any JVM it cannot reach: your IDE's own run configuration, and whatever launches the packaged app. On Maven with Vaadin Boot the flag must come **before** the main class in `exec:exec`'s arguments, never `exec:java` — after it, the JVM hands the flag to your app as a program argument and ignores it. Per-bootstrap detail: [`build-wiring.md`](./build-wiring.md#BW_add_opens).

- [ ] <a id="S_jdk_floor"></a>**Confirm you will *run* on JDK 24 or newer, and add a build gate for it** (`maven-enforcer-plugin` `requireJavaVersion` `[24,)`; on Gradle a `check`-time assertion). Every seed build file carries the gate and compiles to 24 already, so this step is a confirmation unless you assembled your own. On 21–23 the executor pins its carrier — the request thread holding the session lock — and every modal dialog deadlocks, triggered by any monitor above the call including ones inside libraries you don't own. SB-Emulators refuses such a runtime at servlet init, so without the gate the symptom is a startup failure naming the version rather than a hang. Mechanism and the plugin block: [`build-wiring.md`](./build-wiring.md#BW_jdk_floor).

- [ ] <a id="S_register_bootstrap"></a>**Keep `SwingBridgeEmulatorsBootstrap` registered** — the one init listener SB-Emulators needs. On Vaadin Boot the seed's `src/main/resources/META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener` lists it, beside your `AppErrorHandler` — **edit that second line when you move the class into your package**. On Spring Boot, `swingbridge-emulators-spring` registers it and that file must *not* exist: a listener registered both ways runs twice.

  It wires five legs: the **browser time zone** (`BrowserTimeZone.fetch()`, cached before any view's constructor runs — what `BrowserDateUtils.toLocalDate` / `toDate`, `JSpinner(SpinnerDateModel)` and date-formatter `JFormattedTextField` read), **focus tracking** (the one `focusin` listener behind `FocusManager.getFocusOwner()` and focus traversal), the **emulator theme** stylesheet, **tab-close shutdown**, and the **"The application has ended" notice** an ended or expired session shows instead of reloading into a fresh app ([`lifecycle.md`](./lifecycle.md#how-your-app-ends)).

  ***Do not copy this class's body into a listener of your own.*** Legs get added over time, and a copy keeps working while wiring fewer of them than the app needs — silently, since each leg fails in its own way and none of them names the listener. Lose the registration entirely and you find out immediately: `MainWindowRoute` throws on attach, at first page load, naming the fix.

- [ ] <a id="S_verify_modal"></a>**Deliberately exercise a modal dialog flow** before declaring this phase done. "Modal dialogs work" is one of the easiest things to forget to test, and it is what all six steps above exist for.

- [ ] <a id="S_serve_https"></a>**Tell whoever deploys the app that it must be served over HTTPS.** On the desktop a value in a component never left the process. In the browser every value a component shows travels to the user's browser and every keystroke travels back — a `JPasswordField`'s text included, and a password field your app pre-fills from storage sends that stored password to the browser. Over plain HTTP anyone on the network path can read it. Nothing in the app's code fixes that; TLS does, terminated at the app or at a reverse proxy in front of it. Plain HTTP on `localhost` while you migrate is fine.

### Phase 5 — Build & run

- [ ] <a id="S_build"></a>**Build and start it** with your bootstrap's command — `./mvnw -C spring-boot:run` on Spring Boot; `./mvnw -C compile exec:exec` or `./gradlew run` on Vaadin Boot ([`host-app-spring-boot.md`](./host-app-spring-boot.md#HSB_run), [`host-app-vaadin-boot.md`](./host-app-vaadin-boot.md#HVB_run)). Compilation errors are usually import-rewrite residue — re-read Phase 2's report (`swingbridge-migration/reports/import-swap.md`) before hunting: a declined file, or a "no mapping" line for an add-on type, explains most of them.

- [ ] <a id="S_run_app"></a>**Run it and open [http://localhost:8080](http://localhost:8080).** Runtime stack traces during boot are usually Phase 3 — confirm the `@MainWindow` annotation, the `main()`/`mainUI()` split, the route scaffold and the AppShell. Another port has a different spelling per bootstrap, and on Vaadin Boot the obvious `-Dserver.port` is silently ignored — your host-app doc's § Running it has the one that works.

### Phase 6 — Post-migration verification

The app loads. Now verify it behaves.

- [ ] <a id="S_triage_warns"></a>**Triage the WARN log.** Every WARN from `EHelper.onUnimplemented` is a method your app calls that `:emulators` hasn't implemented. Bucket each as **cosmetic** (alignment, visual hint, L&F touchpoint — ignore for now), **functional** (a setter whose side effect your code depends on — file an issue, work around it) or **structural** (a whole component category — rewrite the affected view). What throws instead of WARNing, and why: [`runtime-contract.md`](./runtime-contract.md).

- [ ] <a id="S_smoke_test"></a>**Smoke-test the golden path.** Click through your app's main user flows. The mechanical parts usually work; the surprises are layout-dependent code and modal dialog flows.

- [ ] <a id="S_check_layout_code"></a>**Audit code that reads layout numbers.** `getPreferredSize()`, `getBounds()`, `getWidth()`, `getHeight()` return dummy values, not rendered geometry. Anything that acts on them — custom popup positioning, manual scrolling math, drag-and-drop hit-testing — needs rewriting to Vaadin equivalents.

- [ ] <a id="S_multi_tab"></a>**Open the app in two browser *sessions* and drive both** — an incognito/private window, a second browser, or a second browser profile. A second plain **tab** is refused by design and shows only "This application is already open in another browser tab.": that is one app per session, not a failed migration. Two sessions share **one JVM**: anything still in a `static` field is shared between them, and so between every user of the server. This is the check that the Phase 1 sweep worked — the two must not steer each other. If one session's navigation, selection or status text shows up in the other, a field was left at application scope that belonged at session or tab scope. Driving this from a browser-automation tool rather than by hand? Open a second **browser context** — clearing the session cookie replaces the session rather than adding one, so the obvious attempt silently tests nothing.

- [ ] <a id="S_resolve_tz_markers"></a>**Resolve every `// TODO[browser-tz]:` marker** from Phase 1. `SimpleDateFormat` / `Calendar` / `GregorianCalendar` are already browser-zoned via the Phase 2 swap — nothing to do there, and an app whose date code is entirely bucket-1 legitimately has zero markers. `java.time` seams take `BrowserTimeZone.get()`; shared mutable `Calendar`s get unshared; background-thread date code needs no marker of its own if you did [Phase 3's wrap step](#S_wrap_executors) — that is the same fix. ***Watch out:*** verify under a browser zone several hours off the server (Chromium DevTools → ⋮ → More tools → Sensors → Location, or boot under `TZ=Pacific/Auckland`) — same-zone testing never surfaces drift. Recipes per bucket, and the one marker resolved by a decision rather than an edit: [`dates.md`](./dates.md).

- [ ] <a id="S_guardrails"></a>**Add the migration guardrails and one test.** Three build-time checks back the Phase 1 greps by catching what a name-based grep can't:

  ```java
  class MigrationGuardrailsTest {
      @Test
      void guardrails() {
          new MigrationGuardrails("com.myapp")   // ← your root packages, as many as you have
                  .run();
      }
  }
  ```

  `run()` runs all three checks, collects every violation and reports them in one failure, so you get the whole worklist per run. Pass **every** root package that contains source you compile — **including packages you vendored or forked from someone else**: ownership here is "it compiles into my jar", not "I chose the name", and a root you leave out makes the checks pass by not looking at it. Add `.allowSystemExitInMainMethods()` only if a `main()` of yours legitimately still exits; a migration that did Phase 1 and Phase 3 usually has no JVM exit left at all. It runs as a test, so JUnit and a runner arrive with it if your app had none — four coordinates and one BOM-ordering trap, in [`build-wiring.md`](./build-wiring.md#BW_test_deps). Every seed build file already carries all of it.

  - **The static-state check** — the sweep's gate, and the one that has to pass before you call the sweep done. Every `static` field must be **provably immutable** (`final` **and** an immutable type — both, because `final` governs whether the *reference* can change and the type whether the *referent* can) or carry [`@IntentionallyStatic`](./static-fields.md#the-allowlist-annotation) with a reason. There is deliberately no off-switch. Useful corollary: **the gate's flag list should equal your worklist's "needs an annotation" set** — two independent enumerations agreeing is the completeness signal, not the flag count. Get that worklist side as a diff, over the snapshot Phase 1 kept:

    ```bash
    SWINGBRIDGE_HOME/tools/bin/static-sweep \
        MIGRATED_APP_FOLDER/swingbridge-migration/stage1-classes \
        --diff MIGRATED_APP_FOLDER/target/classes \
        --lib MIGRATED_APP_FOLDER/target/dependency \
        --report MIGRATED_APP_FOLDER/swingbridge-migration/reports/static-sweep-diff.md
    ```

    The migrated tree has dependencies even if the Swing app had none, so run `mvn dependency:copy-dependencies` first (or pass `--cp`); every **still unvetted** row is unfinished sweep. [What each fate means](./former-singletons.md#checking-you-finished).
  - **The component check** — narrower, and kept because its message is specific: the classic leak is `static MainFrame FRAME` where `MainFrame extends JFrame`, which a grep for `static .*JFrame` skips and this catches by resolving the type hierarchy. It matches Vaadin's own `Component` too, so it keeps working after you rewrite a view off the emulators.
  - **The JVM-exit check** — a blanket ban on JVM termination. Its two relaxations name their target rather than switching the check off: `allowSystemExitInMainMethods()` permits an exit in the body of a `public static void main(String[])` and nowhere else (a lambda *inside* `main` is still flagged), and `allowSystemExitIn("com.myapp.Bootstrap")` names exact classes.

- [ ] <a id="S_final_cleanup"></a>**Run your IDE's optimize-imports pass** one last time — resolving the date markers swapped out some pre-migration API calls, so their imports may now be unused.

---

When every box is ticked you're done with step 1. The app runs in the browser; styling iteration can
begin in CSS without touching code. Step 2 — the view-by-view rewrite onto `:surrogates`, where the
code shape becomes Vaadin's — is the next migration step, and there is no hurry: the emulator stage is
a working web app, not a waiting room.

**Two things worth doing next, neither part of this guide.** Style the app as an ordinary Vaadin app,
starting from stock Aura. And **build yourself some confidence that behaviour still matches the
desktop original** — this guide's done condition is "compiles, starts, and the golden paths work by
hand", which catches the port but not every changed behaviour. Your own existing tests are the first
place to look: the ones that never touched Swing still pass and still mean something, while any that
drove Swing components need rewriting either way. Beyond that, how to test a migrated app well is a
question SB-Emulators has not answered yet, and we would rather say so than hand you half a recipe.

## Supported components

The import-swap targets the `javax.swing` / `java.awt` classes below. Anything outside this set logs a WARN via `EHelper.onUnimplemented` and returns a sensible default; it doesn't crash your app.

- **Top-level windows & dialogs:** `JFrame` (rendered inline via `@MainWindow` + `MainWindowRoute`), `JDialog`, `JWindow`, `JOptionPane`, `JColorChooser`, `JFileChooser`; AWT `Frame` / `Dialog` / `Window` / `FileDialog`.
- **Buttons & toggles:** `JButton`, `JToggleButton`, `JCheckBox`, `JRadioButton`, `AbstractButton`, `ButtonGroup`.
- **Text inputs:** `JTextField`, `JPasswordField`, `JTextArea`, `JFormattedTextField`, `JEditorPane` (read-only HTML).
- **Selection & data:** `JComboBox`, `JList`, `JTable`, `JTree` — with their cell-renderer families (`DefaultListCellRenderer` / `DefaultTableCellRenderer` / `DefaultTreeCellRenderer`).
- **Menus:** `JMenuBar`, `JMenu`, `JMenuItem`, `JCheckBoxMenuItem`, `JRadioButtonMenuItem`, `JPopupMenu`, `JSeparator`.
- **Ranges & progress:** `JSlider`, `JSpinner`, `JProgressBar`.
- **Containers & structure:** `JPanel`, `JLabel`, `JScrollPane` (+ `JViewport` / `JScrollBar`), `JSplitPane`, `JTabbedPane`, `JToolBar`, `JDesktopPane` + `JInternalFrame`, `JRootPane` / `JLayeredPane`, `Box` / `Box.Filler`.
- **Layout managers:** `FlowLayout`, `BorderLayout`, `BoxLayout`, `GridBagLayout`, `GridLayout`, `GroupLayout` (+ `LayoutStyle`).
- **Borders & icons:** the `border.*` hierarchy via `BorderFactory`; `Icon` / `ImageIcon`.
- **Concurrency:** `javax.swing.Timer`, `SwingWorker`, `SwingUtilities`, AWT `EventQueue`.
- **Cross-cutting platform:** clipboard (`Toolkit.getSystemClipboard` + `java.awt.datatransfer.*`), drag-and-drop (`TransferHandler`), `java.util.prefs.Preferences` (no import swap — SPI-backed by `localStorage`), printing (hand-crafted `Printable` via the optional `:emulators-printing` module).
- **Plumbing:** `Action` / `setAction`, `InputMap` / `ActionMap` / `registerKeyboardAction`, `setToolTipText`, `setBorder`, `requestFocus` / `InputVerifier`.

**Permanently out of scope** are the three surfaces [What won't migrate](#what-wont-migrate) opens with — `JApplet`, Look-and-Feel dispatch, and user-authored `Graphics` painting of components. They WARN and no-op; plan to rewrite the affected views.

## Where to look next

The references, in the order the phases reach for them:

| document | what it owns |
|---|---|
| [`static-fields.md`](./static-fields.md) | the `static` sweep: three scopes, the decision tree, worked examples, the allowlist annotation |
| [`former-singletons.md`](./former-singletons.md) | the per-tab holder the sweep routes fields into |
| [`import-swap-reference.md`](./import-swap-reference.md) | the swap table in prose — what ports, what stays, and the seven watch-outs |
| [`dates.md`](./dates.md) | the three date/time buckets and the `// TODO[browser-tz]:` recipes |
| [`lifecycle.md`](./lifecycle.md) | start-up hooks in run order and what each can reach; how the app ends |
| [`build-wiring.md`](./build-wiring.md) | bootstrap choice, dependencies, plugin blocks, JVM flags, the JDK floor |
| [`host-app-spring-boot.md`](./host-app-spring-boot.md), [`host-app-vaadin-boot.md`](./host-app-vaadin-boot.md) | the host app on each bootstrap: its seed's files, how to run it, how to package it |
| [`runtime-contract.md`](./runtime-contract.md) | what WARNs, what throws, and `JFileChooser.showDialog` |
| [`third-party-libraries.md`](./third-party-libraries.md) | triaging a Swing dependency; the three states of a library with no add-on |
| [`addons.md`](./addons.md) | the pre-built add-ons and their own migration instructions |
| [`platform.md`](./platform.md) | OS checks, shell-outs, native code; and the debt to leave alone |

**Worked example:** the kit ships `SWINGBRIDGE_HOME/testapps/crud` — a small employee-CRUD app as pure Swing (`swing/`, the step-1 input shape) and after the import-swap onto `:emulators` (`1-emulators/`, the step-1 output), hosted on Spring Boot. The diff between the two is the canonical "what changes during step 1" reference, and its `README.md` says how to launch either stage. On Vaadin Boot only the host-app files differ, and they are the ones `seed/vaadin-boot/` holds.

Further reading lives in SB-Emulators' own repository, and is maintainer-facing rather than migrator-facing — useful when you want the mechanism, not the recipe:
[`emulators/README.md`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/README.md) (the API surface and dependency setup),
[vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs) (the virtual-thread machinery that makes blocking modal dialogs work),
[`surrogates/README.md`](https://github.com/vaadin/swingbridge-emulators/blob/main/surrogates/README.md) (a preview of what step 2's code looks like).
