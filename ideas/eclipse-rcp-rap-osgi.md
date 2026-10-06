# Eclipse RCP / RAP / RWT / OSGi — the shell around the SWT problem

**Status: brainstorm. No commitment, no code, nothing decided.** Companion to
[swt-emulators.md](./swt-emulators.md), which covers plain SWT and should be read first —
everything here presupposes an emulated SWT layer exists. Scoped against the same driving
scenario: a mixed Swing + SWT + Eclipse RCP application, ~50–60 RCP views, plus
a separable Swing/gantt planning half.

**Maintainer-facing.** Nothing here is a promise to anyone.

## Bottom line

**The API is not the problem; tenancy and OSGi are.** RCP contributes no widgets of its own, so in
principle an emulated SWT plus an emulated workbench and "RCP just runs". In practice Eclipse is
written **one-app / one-JVM / one-user**, and its stateful singletons (`PlatformUI.getWorkbench()`,
`Display.getDefault()`, the e4 `IEclipseContext`, `IWorkspace` on local disk, preference
`InstanceScope`, plugin `Activator` registries) all assume that.

**But this is solved art, not open research.** Eclipse RAP has been running RCP applications
multi-user on a server for two decades and is actively released — **RAP 4.7.0, June 2026**,
quarterly with the Eclipse simultaneous release, with a session-scoped `Display`, a
`SingletonUtil`/session-singleton mechanism, per-application/session/UI scopes, and *forked*
platform bundles (`org.eclipse.rap.jface`, `org.eclipse.rap.ui.workbench`) whose statics were
rewritten to be session-scoped. So the answer to "can RCP be made multi-tenant" is *yes, and here
is the fifteen-year-old existence proof* — with the caveat that it only holds for code written or
ported to RAP's **single-sourcing** rules.

The three viable paths, ranked by risk-adjusted value:

| | path | what we build | biggest risk |
|---|---|---|---|
| **C** | **Drop RCP, keep SWT** — flat classpath, thin Workbench-shaped shell over the RCP API the views actually touch | `:swt-emulators` + a small `swingbridge-workbench` reading `plugin.xml` as data | the *used* RCP surface might not be small; OSGi-dependent code (DS, `BundleContext`, fragments) breaks |
| **B** | **Two runtimes** — Eclipse RAP renders the SWT/RCP half as-is, SB-Emulators renders the Swing half, stitched at the browser/URL/SSO level | almost nothing new | Swing-inside-a-view is unreachable; two look-and-feels; two deployments |
| **A** | **SB-Emulators-SWT under RAP** — keep RAP's session model + forked workbench, replace RWT with a Vaadin-backed SWT | an RWT-API-compatible SWT *and* RAP's server-side runtime contract | you inherit RAP's entire internal API as your compat target, plus its version pin. Extremely fragile. |

Rejected outright: **emulate all of RCP/e4** — that is re-implementing Eclipse, measured in years,
and there is no plausible business case.

The correct next action is **not** to pick a path. It is to run
[the inventory](#the-measurement-that-decides-everything) over the target app's codebase, because
every one of these paths is decided by numbers we don't have.

## Correcting the record

Claims that circulate about this problem space and need adjusting before anything is built on
them:

- **"Eclipse RAP … offering a servlet bridge that somehow solves the singleton issues."** Two
  separate things. The servlet bridge (`org.eclipse.equinox.servletbridge` / `BridgeServlet`) solves
  **deployment** — it runs an OSGi framework inside a WAR in a normal servlet container. Tenancy is
  solved by a different set of mechanisms: a **session-scoped `Display`** (SWT's own thread-affinity
  rules then do the enforcement for free), `SingletonUtil`/`SessionSingletonBase` for
  session-scoped plugin singletons, explicit **application / session / UI scopes**, and a **fork of
  the workbench** in which each session gets its own workbench instance.
- **"RAP is 3.x-era / possibly stale."** No — **RAP 4.7.0 shipped June 2026**; 69 releases since
  1.0.0 in 2007; it has been in every Eclipse simultaneous release since Ganymede. RAP 3.2 (2017)
  is the version most blog posts are about, which is where the stale impression comes from.
- **e4 is the sharp caveat, not RAP's age.** RAP's supported single-sourcing target is the Eclipse
  **3.x-style workbench** (`ViewPart`, `IWorkbenchPage`, extension-point-declared views and
  perspectives). e4 support exists, but as **incubator** forks of `eclipse.platform.ui` running e4
  in *3.x compatibility mode*. **If the target app is e4-native — `Application.e4xmi`,
  `@Inject`, `EModelService`, `EPartService`, DI-everywhere — path B gets much weaker and path A
  gets much worse.** Whether the app is 3.x-style or e4-native is the single highest-value fact
  to establish, and it is a five-minute check (does an `.e4xmi` exist; is `org.eclipse.e4.ui.model`
  a dependency).
- **"The extension registry is a JVM-wide singleton [and therefore a problem]."** It is JVM-wide,
  but it is a mostly **read-only description** of what plugins contribute — sharing it across users
  is fine. The tenancy problems are the *stateful* singletons. Sorting the singletons into
  "shareable" and "must be per-session" makes the problem look a lot smaller; see the next table.
- **"Emulating SWT is another ~4 months."** Directionally fine for a *scoped first slice*, but the
  reasoning behind it was "SWT is as big as Swing", which is not quite right — see
  [swt-emulators.md § Effort](./swt-emulators.md#effort--how-to-talk-about-it-honestly). Do not
  repeat the number without the scope attached.

## The tenancy inventory

What actually breaks when two users share one JVM, sorted by what it costs us:

| singleton | JVM-wide? | verdict |
|---|---|---|
| `Platform.getExtensionRegistry()` | yes | **shareable** — read-only contribution description |
| `Display.getDefault()` | yes | **per session.** RAP's approach; SB-Emulators already scopes per-session state (D_session_scoped_pools). Cheap, and SWT's `checkWidget()` enforces the boundary for us |
| `PlatformUI.getWorkbench()`, `IWorkbenchWindow`/`Page`/`Part`, perspectives | yes | **per session.** The core of the work in any path. RAP solved it by forking the workbench |
| e4 `IEclipseContext`, `MApplication` model, `EPartService` | yes | **per session**, and much harder than 3.x — a live mutable model tree plus DI. RAP only has an incubator answer |
| `Job`/`JobManager` (`org.eclipse.core.runtime.jobs`) | yes | **pool shareable, job context is not.** A `Job` scheduled by user A must run with A's session context; a `WorkbenchJob` touching the UI needs A's `Display`. Same problem SB-Emulators solved for `SwingWorker`/`Timer` (D_timer_swingworker/D_session_scoped_pools) — deliver via the session's live UI |
| Eclipse preferences (`InstanceScope`, `ConfigurationScope`) | yes, disk-backed | **per session/user.** SB-Emulators already did exactly this for `java.util.prefs` via localStorage (D_preferences); different API, identical trick |
| `ResourcesPlugin.getWorkspace()` / `IWorkspace` | yes, single `-data` dir + lock | **the hard one.** A local-disk workspace with an exclusive lock is fundamentally single-user. Options: per-user workspace directory (server-side, needs storage + quota + cleanup), or establish that the app doesn't use `org.eclipse.core.resources` at all (many RCP apps don't) |
| `Platform.getInstanceLocation()`, single-instance lock, `-configuration` area | yes | per session or drop; follows the workspace decision |
| plugin `Activator` statics — `ImageRegistry`, `ColorRegistry`, `FontRegistry`, cached `Display` | yes | **the documented single-sourcing gotcha.** Registries created at bundle start bind to whichever session started the bundle first. Grep-detectable, and mostly mechanical to fix in app code |
| `Display.getDefault()` in static initialisers | — | **poison.** Runs at classload, i.e. in whatever session happened to touch the class first. Also grep-detectable |
| `System.getProperty`/`setProperty`, static caches in app code | — | the migrator's problem, same as in a Swing migration |

Reading of the table: **about half of it SB-Emulators has already solved once**, in a different API's clothes
(per-session pools, session-scoped prefs, live-UI delivery, tab lifecycle). The genuinely new
items are the workbench itself, e4 if present, and the workspace if used.

## Path C — drop RCP, keep SWT (the recommended strategic bet)

The move: **don't emulate Eclipse; emulate the slice of Eclipse those 50–60 views actually touch,
and run it on a flat classpath with no OSGi at all.**

Mechanism sketch:

- **No Equinox.** `plugin.xml` becomes *data*: a plain reader scans the classpath for `plugin.xml`
  / `MANIFEST.MF`, and builds an in-memory contribution model — views, perspectives, commands,
  handlers, menus. This is what `IExtensionRegistry` returns. The whole classloader can-of-worms
  disappears, and with it the hardest-to-debug class of failure.
- **A session-scoped workbench.** `PlatformUI.getWorkbench()` resolves per Vaadin session, the
  same way `Display` does. `IWorkbenchPage.showView(id)` instantiates the declared `ViewPart` and
  places it. `ISelectionService` is a session-scoped listener bus. `IActionBars`, commands +
  handlers, `IMemento` state save/restore, `ISharedImages` — each is a small, well-bounded API.
- **Perspectives → Vaadin routes / layouts.** A perspective is a named arrangement of view stacks;
  a view stack is `Tabs`/`TabSheet`; a sash is `SplitLayout`. Perspective switching is either a
  route change or a layout swap. `@PreserveOnRefresh` + `IMemento` gives F5 survival.
- **`ViewPart`/`EditorPart` become components.** `createPartControl(Composite parent)` is handed an
  emulated `Composite` — the view's own code is untouched, which is the entire point.

Why this is attractive: it is R_match_swing_errors's triage philosophy applied one level up. SB-Emulators' whole scoping
discipline says *the API surface a typical app uses is a small fraction of the API surface that
exists*, and the "typical CRUD app" baseline is exactly what an RCP view is. `ViewPart` +
`showView` + selection + actions + memento is maybe a dozen types, not the thousands in the
platform.

Failure modes to check for in the inventory:

- Code using `BundleContext`, `Platform.getBundle()`, `FrameworkUtil`, `Bundle.loadClass`,
  bundle-relative resource lookup (`FileLocator.find`), fragments, or buddy classloading.
- **Declarative Services / OSGi services** (`@Component`, `ServiceTracker`) — if the app's business
  layer is wired with DS, a flat classpath needs a substitute service registry. Doable (it's a
  map), but it is scope.
- **Split packages and duplicate classes** across bundles, which OSGi tolerates and a flat
  classpath does not. This is the most likely nasty surprise, and it is measurable up front.
- Activator ordering / lifecycle assumptions.
- e4 DI: `@Inject`-annotated parts have no shell to inject them without an e4 context. If the app
  is e4-native, path C grows an e4-context emulation and the "small surface" claim weakens badly.

## Path B — two runtimes, stitched in the browser

The option nobody in the thread raised, and it deserves to be on the table because it is the
lowest-risk one: **let RAP do the thing RAP has done for twenty years.** RAP renders the SWT/RCP
half; SB-Emulators renders the Swing/gantt half; they are two deployments joined by URL/navigation, shared
auth (SSO) and shared backend services, not by a shared JVM widget tree.

- Cost: two runtimes to operate, two visual languages to reconcile, no shared component tree.
- The **blocking** cost: `SWT_AWT`-embedded Swing content inside a view has no path — RAP cannot
  render Swing. Those views need a rewrite, or must be lifted out into the SB-Emulators half as separate
  windows/routes — which is reasonable whenever that Swing content is standalone-screen-shaped
  anyway.
- It also requires the app to satisfy RAP's single-sourcing rules, which is real work — but it is
  *well-documented, well-trodden* work with a large body of prior art, rather than work we invent.

**Which path fits depends on the goal:** if it is "get the 50–60 SWT views into a browser with the
least novel risk", path B is the truthful answer and SB-Emulators' role is the Swing half. If it is
"become a Vaadin application", path C is. Those are two different projects, not two estimates for
one.

## Path A — SB-Emulators-SWT under RAP (research note only)

The idea — "use RCP + RAP but replace RWT with our fake-SWT rendering to Vaadin" —
does not decompose the way it sounds. Precisely:

RAP's forked bundles (`org.eclipse.rap.jface`, `org.eclipse.rap.ui.workbench`, …) bind to
`org.eclipse.swt.*` **as exported by `org.eclipse.rap.rwt`** — and RWT's export is not stock SWT.
It is an SWT *subset* plus RAP-specific API (`RWT.getUISession()`, `RWT.getApplicationContext()`,
scopes, the request/lifecycle phases, client services, custom variants, the protocol/`ClientObject`
seam). To slot in underneath RAP's workbench we would have to reproduce **RAP's** contract, not
SWT's — i.e. re-implement RAP's server-side runtime with Vaadin as the client. And then pin
ourselves to a specific RAP version forever, since those are internal contracts.

So the honest description is *"write a second RAP"*, and RAP already has a perfectly good client.
The only reason to want this is Swing-inside-a-view — and paths B and C reach that goal without
inheriting RAP's internals. **Keep as a note; it is not a route to take.**

## Path D — Streamer-style pixel streaming for the SWT half?

Worth stating explicitly because the question arises in the context of SwingBridge Streamer, and because the
answer is a genuine technical asymmetry, not a product position.

[SwingBridge Streamer](https://vaadin.com/docs/latest/tools/modernization-toolkit/swing-bridge) runs the
migrator's **unmodified** Swing app server-side and streams its rendered UI to the browser. That
works because Swing is pure Java painting into an offscreen image — a JVM is all you need.
**SWT is the opposite: it is a thin binding over native platform widgets** (GTK / Win32 / Cocoa).
To run it server-side you need a real display and toolkit per user — Xvfb + GTK, effectively a
container per session — which is a different operational model (per-user processes, memory,
lifecycle, licensing) rather than a different library. That, rather than any lack of interest, is
why SWT isn't covered there today. Also relevant: the browser receives an image, so an SWT app
streamed this way would still not be a Vaadin application — a valid answer to "browser access now",
not to "become a Vaadin app".

**But the composition is interesting and should be mentioned**: Streamer on the Swing/gantt half
*today*, in parallel with a path-B or path-C project on the RCP half, gives something
in a browser early without prejudicing the eventual target. The routes compose in the documented
way — Streamer first, then port view-by-view.

## The measurement that decides everything

This turns "anywhere from easy to intractable" into arithmetic. It is a day of work, it runs on the
app's own machine and shares no source, and **nothing downstream should be decided before it
exists.** Deliverable: a table of counts.

```bash
# --- which toolkits, and in what proportion
grep -rho 'import org\.eclipse\.swt\.[a-z]*' --include=*.java . | sort | uniq -c | sort -rn
grep -rho 'import org\.eclipse\.jface\.[a-z]*' --include=*.java . | sort | uniq -c | sort -rn
grep -rlc 'import javax\.swing' --include=*.java . | wc -l

# --- the exclusions: how much custom drawing is there really?
grep -rlc 'PaintListener\|addPaintListener\|extends Canvas\|new GC(' --include=*.java . | wc -l
grep -rlc 'MeasureItem\|EraseItem\|PaintItem' --include=*.java . | wc -l
grep -rlc 'StyledText' --include=*.java . | wc -l
grep -rlc 'TableEditor\|TreeEditor\|ControlEditor' --include=*.java . | wc -l
grep -rlc 'CTabFolder\|ScrolledComposite\|SashForm' --include=*.java . | wc -l
grep -rlc 'org\.eclipse\.swt\.ole\|org\.eclipse\.swt\.browser' --include=*.java . | wc -l

# --- the differentiator: how many views embed Swing?
grep -rlc 'SWT_AWT' --include=*.java . | wc -l

# --- 3.x workbench or e4? (decides how bad the shell problem is)
find . -name '*.e4xmi' | wc -l
grep -rlc 'org\.eclipse\.e4\.\|@Inject\|EModelService\|EPartService' --include=*.java . | wc -l
grep -rc 'org\.eclipse\.ui\.views\|org\.eclipse\.ui\.perspectives' --include=plugin.xml . | wc -l

# --- OSGi entanglement (decides whether path C's flat classpath is viable)
grep -rlc 'BundleContext\|FrameworkUtil\|Platform\.getBundle\|FileLocator' --include=*.java . | wc -l
grep -rlc 'org\.osgi\.service\.component\|ServiceTracker\|@Component' --include=*.java . | wc -l
find . -name MANIFEST.MF | wc -l          # bundle count
grep -rlc 'ResourcesPlugin\|IWorkspace' --include=*.java . | wc -l

# --- tenancy poison
grep -rn 'static.*Display\.getDefault()' --include=*.java . | wc -l
grep -rlc 'ImageRegistry\|ColorRegistry\|FontRegistry' --include=*.java . | wc -l

# --- third-party SWT ecosystem (each is its own fork decision)
grep -rho 'import org\.eclipse\.\(nebula\|gef\|draw2d\|ui\.forms\)[a-z.]*' --include=*.java . | sort | uniq -c
```

Decision rules to state *before* seeing the numbers, so we can't rationalise afterwards:

- **`SWT_AWT` count decides whether the emulator approach is the only one that reaches the app.**
  Near zero → path B is honest and cheap. Substantial → only paths A/C reach it.
- **`PaintListener`/`Canvas`/`GC` count is the feasibility gate.** Concentrated in a handful of
  widgets → carve them out as islands. Spread across the view layer → the emulator approach is the
  wrong tool and we should say so.
- **`.e4xmi` present** → the shell problem roughly doubles; RAP's answer is incubator-grade.
- **DS / `BundleContext` widespread** → path C needs a service-registry substitute; scope it.
- **`ResourcesPlugin` present** → the workspace question is live, and it is the ugliest one.
- **GEF/draw2d present at all** → that subsystem is out of scope, full stop (draw2d *is* a
  `GC`-painting framework). Say it early.

## Open questions

1. Is the target app 3.x-workbench or e4-native? (Everything downstream branches here.)
2. Does it use `org.eclipse.core.resources` / `IWorkspace`? If yes, what is the per-user story —
   a workspace directory per session, or a rewrite off the resources API?
3. How many of the 50–60 views embed Swing via `SWT_AWT`?
4. Path C: how small is the *used* RCP API surface, really? Can it be enumerated from an app's code
   in an afternoon?
5. Path C: is a `plugin.xml`-as-data reader plus a service-registry substitute enough, or does OSGi
   leak in through DS/fragments/split-packages?
6. Does anything in the app rely on OSGi at *runtime* for behaviour (hot deploy, dynamic
   extensions, per-customer plugin sets)? If a customer-specific plugin set is a product feature,
   the flat classpath is a product regression, not just a technical simplification.
7. Would we ever want an emulated `Job`/`JobManager` on the D_timer_swingworker/D_session_scoped_pools session-scoped pools, or should
   jobs be pushed down to plain executors during migration?
8. If path B (two runtimes): can a RAP app and a Vaadin app share a session/auth realm cleanly
   enough for one perceived application? Who has done this?
9. If path C: does `swingbridge-workbench` belong in SB-Emulators at all, or is it a separate product? It has no
   Swing in it.
10. Licence lane: RCP/JFace/RAP are EPL. SB-Emulators' lanes are per module
    ([D_licence_lanes](../emulators/decisions.md#D_licence_lanes)): the emulator core is GPLv2 +
    Classpath Exception, every other module Apache-2.0, and an add-on reproducing a third-party
    library's API takes *that library's* licence
    ([M1D_addon_upstream_licence](../migration/1-swing-to-emulators/decisions.md#M1D_addon_upstream_licence)) —
    so an RCP-shaped module would presumably be EPL whether forked or reimplemented, as `third-party/`
    already does for BSD and LGPL. Legal review of the EPL lane before a line of prototype code.
11. Is there an open-source RCP app small enough to adopt as a testbed (via the
    [`adopt-testapp`](../.claude/skills/adopt-testapp/SKILL.md) skill)?
12. What if the app dropped Eclipse RCP as part of the migration? Everything good here (path C)
    depends on that being acceptable. Where it is not, the honest answer is RAP.

## Graduation

This file is on ice. It becomes real work only if (a) the inventory gets run, and (b) the
`SWT_AWT`-embedding count justifies the differentiation. If either fails, the file's lasting value
is the tenancy table, the correction list, and the measurement script — keep those even if the
project never happens.
