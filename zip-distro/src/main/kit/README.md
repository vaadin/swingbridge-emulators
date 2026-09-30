# SwingBridge Emulators — migration kit

Runtime emulation of the Swing API on top of [Vaadin 25](https://vaadin.com/). Take an existing
Swing desktop application, swap `javax.swing` imports for `vaadinx.swing`, recompile, and it runs as
a Vaadin web app — real Vaadin components in the DOM, not a picture of a desktop window.

**SwingBridge Emulators** — **SB-Emulators** for short — is a **migration bridge** to a future
native-Vaadin rewrite, not a permanent target or a 1:1 reimplementation of Swing. It gets your app
running in a browser cheaply, so you can iterate it toward idiomatic Vaadin view by view afterwards.

**This download is the kit**: the migration guides, the three tools the mechanical phases use, three
example apps to practise on, and an agent prompt that ties them together. **Nothing here is the
library to build against** — every build in this kit names SB-Emulators by coordinate and Maven
fetches it, which is also how your own app will depend on it. (`tools/lib/` does hold a copy of the
emulators jar, but only as a lookup table: the import-swap tool reads the list of ported types out
of it. Never point your app's build at `tools/lib/`.)

`SWINGBRIDGE_HOME` in the guides and the commands means **the folder this README is in**.

## Prerequisites

- **A JDK 24+.** That is the whole list: the three tools in `tools/bin/` are shell scripts over
  `java -cp`, and every example app carries the Maven wrapper (`mvnw`), so Maven itself need not be
  installed. The floor is a *runtime* one — SB-Emulators' modal dialogs park a virtual thread inside
  a `synchronized` region, which deadlocks before [JEP 491](https://openjdk.org/jeps/491) — so the
  guides' example build files simply target 24 as well. Emitting older bytecode still works if
  something downstream needs it.
- **Network access to build the migrated app** — Maven Central for the SB-Emulators coordinate,
  and Vaadin's own frontend build. **The three tools need none of it**: `tools/lib/` already holds
  every jar they read, so Phases 1 and 2 run on an air-gapped machine as they are. A fully offline
  *kit* is still not on offer, because Vaadin is not ours to bundle and its frontend build reaches
  the network anyway. If your sources may not leave the building, that is fine and supported — the
  *sources* never do. On an air-gapped machine, clone SB-Emulators, run `./mvnw -C clean install`
  once against an internal mirror, and everything here resolves out of your local `~/.m2`.
- **Vaadin `@Push` must be enabled** on your app's shell class. This is not optional and not a
  performance setting: a blocking `JOptionPane.showMessageDialog` renders through the push channel,
  so without it a modal dialog never appears. The guide's Phase 4 has the annotation.
- **Your usual IDE, and nothing special beyond it.** Nothing in the migration depends on particular
  tooling: the `static` sweep reads your app's compiled classes with `tools/bin/static-sweep`, and
  `javac` is the authority on every type question. An IDE just makes the hand edits in Phases 3–5
  quicker to navigate. (Driving an agent instead? `CLAUDE.md` beside this file carries the two
  language-server caveats that apply to *that* setup.)
- **Claude Code is optional.** It makes the three-command path below work; the same migration runs
  with any agent, or by hand, off the same guides.

**Versions.** This kit is SB-Emulators `${project.version}`, built against Vaadin 25.3.0. Your
migrated app needs both — the SB-Emulators coordinate *and* a compatible Vaadin version — and
each bootstrap's seed under `guides/1-swing-to-emulators/seed/` pins a matching set for you to copy, so there is
nothing to resolve to get started (`example-build.gradle.kts` is the Gradle twin). For a version
neither file carries, read `com.vaadin.swingbridge:swingbridge-emulators-parent`'s own published pom, which
travels with the artifacts and so cannot drift from this kit.

**Where the coordinate resolves from depends on how you got this kit, and this is how it stays for
the foreseeable future.** A **release kit** — downloaded from a GitHub Release, version without
`-SNAPSHOT` — resolves from Maven Central like any dependency; nothing to set up. A kit **built from
source** — version ending in `-SNAPSHOT` — resolves from the local `~/.m2/repository` that the very
same `./mvnw -C clean install` populated, so it works on the machine that built it and nowhere else.
If a SNAPSHOT kit's build fails resolving `com.vaadin.swingbridge:*`, that install did not happen on
this machine: run it in the SB-Emulators checkout, then rebuild the app. Do not point the pom at
`tools/lib/` to get past it.

## Three ways to start

**1. See every emulated component running, in a browser.** The Sampler app demos the whole emulated
surface. It is not bundled — it is a big Vaadin app with no commitment surface of its own — so it is
a clone and a command:

```bash
git clone https://github.com/vaadin/swingbridge-emulators
cd swingbridge-emulators && ./mvnw -C clean install -DskipTests && ./mvnw -C -pl sampler exec:exec
```

**2. Migrate one of the bundled example apps, with an agent.** Copy the example out first, *then*
open Claude Code in this folder and type the command:

```bash
cp -r testapps/crud/swing work/crud
claude
```
```
/migrate-testapp crud
```

It migrates `work/crud/` following the guides and hands you a URL. The kit's own copy stays pristine,
so you can run it again. The copy comes first only so a language server, if you are running one,
imports the project at start-up — the skill makes the copy itself if you skipped it, and nothing
stops. `testapps/crud/1-emulators/`
is the finished migration — the answer key, worth diffing against afterwards. `jlawyer-shape` is the
second example, and `inventory` the third: a real inventory-management app written by someone who had
never heard of SwingBridge Emulators — Hibernate, H2, JGoodies Forms, JCalendar, a login window,
sixty-odd UI classes — so it measures the migration rather than our prediction of it.

**Then your own app.** Copy it into `your-app/swing/` (so that `your-app/swing/pom.xml` exists)
*before* opening Claude Code — same reason as above — and type:

```
/migrate-your-app
```

The skill migrates a copy at `your-app/1-emulators/` and leaves `swing/` as the baseline to diff
against, the same layout the examples use. Or leave the app where it is, in its own version-controlled
checkout, and run `/migrate-swing-app <folder>` to migrate it in place as one reviewable commit.
`your-app/swing/README.md` spells out the choice.

All three skills migrate in the session you typed the command into, rather than handing the work to
a subagent — so you can watch it happen and see where it hesitated. Open Claude Code in this
folder: anywhere else loses the skills.

Driving a different agent? [`guides/1-swing-to-emulators/agent-prompt.md`](./guides/1-swing-to-emulators/agent-prompt.md)
is the same instruction set as plain Markdown, with the two paths to substitute called out at the
top.

**3. Migrate your own app, yourself.** Copy the guides folder into your app and work from the copy,
so your ticks and notes live with the migration:

```bash
cp -r guides/1-swing-to-emulators <your app>/swingbridge-migration
```

Then read that copy's `guide.md`. Each of its six phases is a list of `- [ ]` steps — tick them as
you go; the references it links are for opening when a step points at one, not for reading up front.
Phases 1 and 2 are tool runs — the sweep reads compiled classes rather than sources, so build the app
before running it:

```bash
tools/bin/hazard-scan  <your app>/src/main/java --report hazards.md
tools/bin/static-sweep <your app>/target/classes --report static-sweep.md
tools/bin/import-swap  <your app>/src/main/java --dry-run --report import-swap.md
```

All three exit 0 whenever they ran: their reports are for you to triage, never a gate. On Windows use
`tools\bin\hazard-scan.cmd` / `tools\bin\static-sweep.cmd` / `tools\bin\import-swap.cmd` with the
same arguments.

## What is in here

```
README.md                       ← this file
CLAUDE.md                       ← the brief an agent working in this folder reads
LICENSE                         ← Apache-2.0 — not the only licence here; see below
tools/                          ← the three migration tools; a JDK is all they need
  README.md                        their options and exit codes, and how to add a swap table
  bin/                             hazard-scan, static-sweep, import-swap, and a .cmd twin of each
  lib/                             every jar they read — the tool, and the three swap tables
  swing-mcp/                       behavioural capture off the desktop app — not in this release
guides/1-swing-to-emulators/    ← the migration guides; copy the folder, tick the steps in your copy
  guide.md                         the six phases as tickable steps — start here, it is the procedure
  static-fields.md                 the `static` sweep: scopes, decision tree, worked examples
  former-singletons.md             the per-tab holder the sweep routes fields into
  lifecycle.md                     start-up hooks in run order, what each can reach, how the app ends
  import-swap-reference.md         the swap table in prose — for reviewing the tool's diff
  dates.md                         the three date/time buckets and the browser-zone recipes
  build-wiring.md                  bootstrap choice, dependencies, plugins, JVM flags, the JDK floor
  host-app-spring-boot.md          the host app to put your migrated views in, on Spring Boot…
  host-app-vaadin-boot.md          …or on Vaadin Boot
  seed/                            that host app as real files, one tree per bootstrap — copy and edit
  example-build.gradle.kts         the same for Gradle
  runtime-contract.md              what WARNs, what throws, and JFileChooser.showDialog
  third-party-libraries.md         triaging a Swing dependency with no add-on
  platform.md                      OS checks, shell-outs, native code; and the debt to leave alone
  addons.md                        pre-built add-ons for third-party Swing libraries
  agent-prompt.md                  the migration prompt, for any agent
third-party/                    ← per-add-on migration instructions, provenance and licences
testapps/                       ← example apps to practise on
  crud/                            stage 1 and the finished stage 2 — the guide's worked example
  jlawyer-shape/                   stage 1 only
  inventory/                       stage 1 only; a third party's app, on its author's terms (see its PROVENANCE.md)
your-app/                       ← the slot for your own app
  swing/                           copy your Swing app here; /migrate-your-app migrates a copy
                                   into your-app/1-emulators/ beside it
.claude/skills/                 ← /migrate-testapp, /migrate-your-app, /migrate-swing-app
```

**Anything named `*.placeholder` is a slot this release does not fill.** The file says what would be
there, why it is not, and where to get it meanwhile. `find . -name '*.placeholder'` is the kit's
open-items list.

**Licences — more than one, and each file says which is its own.** The root `LICENSE`
(Apache-2.0) covers everything Vaadin wrote around the jars: the guides, the skills, this kit's own
docs and the `testapps/crud` worked example — except the host-app seeds under
`guides/1-swing-to-emulators/seed/`, which you copy into your own app and so are **0BSD**
(`seed/LICENSE`): use them, change them and license the result as you choose, with no notice to
keep — including the copies of them inside `testapps/crud/1-emulators/`, which keep that header. The
rest carry their own text beside them:
`tools/LICENSE` (Apache-2.0 again) for the tool jars, with `tools/THIRD-PARTY.md` for what they
redistribute; `tools/lib/swingbridge-emulators-LICENSE.txt` (GPLv2 with the Classpath Exception)
for the `swingbridge-emulators` jar beside it, the library your migrated app links; each add-on's
`LICENSE` under `third-party/` (LGPL-2.1 for JCalendar, BSD for JGoodies Forms); the other two
example apps' own terms beside them; and the Maven Wrapper scripts, which carry the Apache Software
Foundation's header. Every `swingbridge-*` jar also carries its own licence text inside, under
`META-INF/`. Linking the
library places no obligation on your own code — the Classpath Exception is the same one your app
already relies on to run on a JDK.

## Where SwingBridge fits

SB-Emulators is one of several routes Vaadin offers from Swing to the web, and they are **aids, not
itineraries** — you compose your own route from them according to appetite, source availability and
risk tolerance. Vaadin publishes no recommended default path, and neither does this document.

The sibling worth knowing about before you commit is
[**SwingBridge Streamer**](https://vaadin.com/docs/latest/tools/modernization-toolkit/swing-bridge)
(`com.vaadin:vaadin-swing-bridge`). It changes **no source**: your Swing app runs unmodified on the
server, its rendered UI is streamed to the browser as images, and input is routed back into the AWT
event queue. Because it executes the real bytecode on the real toolkit, it needs no source access,
no recompilation and no per-component coverage — third-party Swing toolkits and custom `Graphics2D`
painting simply work, and both are permanently out of SB-Emulators' scope. What the browser receives
is an image rather than components.

So: reach for **Streamer** when you need browser access now and the app is effectively frozen, or
when you do not have full source for everything it depends on. Reach for **SB-Emulators** when the
app is still under active development and the destination is genuinely a Vaadin app — what you get
back is code you keep evolving. **And they compose:** precisely because Streamer changes no source,
starting there costs nothing you would later have to undo.

## The add-ons are free starter prototypes

`third-party/` carries two pre-built add-ons — JCalendar and JGoodies Forms — so an app importing
`com.toedter.*` or `com.jgoodies.*` keeps compiling after the swap. **They are frozen at what they
ship**, and the boundary each one's `MIGRATION.md` describes is where it stays. Whether
fuller-coverage add-ons will exist, and on what terms, is not decided; bug fixes to what is here
are not affected. Say this upfront in any plan that leans on one:
[`guides/1-swing-to-emulators/addons.md`](./guides/1-swing-to-emulators/addons.md) has the detail
and the routes for a library with no add-on at all.

## What will not migrate

Three Swing capabilities are permanently out of scope, and it is worth checking your app against
them before you start: **`JApplet`** (gone from the JDK itself), **Look-and-Feel dispatch**
(`UIManager.setLookAndFeel`, pluggable `*UI` classes — styling is a Vaadin + CSS concern
afterwards), and **user-authored `Graphics` painting of components** (`paintComponent`,
custom-painted widgets, `JTable.print()`). Hand-crafted `Printable.print(Graphics)` is the
exception and is supported, rendering to a downloadable PDF. Everything outside the supported
surface logs a WARN and returns a sensible default rather than crashing your app — so a gap shows
up in your log, not as an outage.

If your app's value is mostly in those surfaces, this route does not help yet.

## Further reading

The full source, the decision logs and the issue tracker:
<https://github.com/vaadin/swingbridge-emulators>.
