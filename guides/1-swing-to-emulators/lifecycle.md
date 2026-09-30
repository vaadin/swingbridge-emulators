# App lifecycle — what runs when, what it can reach, and how the app ends

Reference for [Phase 3](./guide.md#phase-3--entry-point-setup)'s `main()` / `mainUI()` split. That phase
tells you the split exists; this page tells you **which of your start-up work goes on which side**, and
what each side is allowed to touch. It closes with the other end of the lifetime: what ends a migrated
app, which is where the single-instance guard and every `System.exit` call site get decided.

This page owns one axis: *when does this code run, and what is reachable from there*. The other axis —
*where does this piece of state live* — is [`static-fields.md`](./static-fields.md)'s, and the two are
routinely confused. If your question is "should this be a `static`?", you are on the wrong page.

## The desktop conflated three things; the server separates them

On the desktop, one JVM start was one app launch was one user. A single `main()` served all three, which
is why "process-level vs. UI-level" is not enough to route real start-up work: it splits a *three*-way
distinction in two. The server pulls them apart:

- the **deployment** — the JVM, its filesystem, its one database. Starts once, serves everybody.
- the **app launch** — one running instance of your app. That is now a **browser tab**.
- the **user** — one person, across all their tabs.

So the routing question is not "is this process-level?" but **"did this work belong to the deployment, or
to one launch of the app?"** Logging config and a connection pool belong to the deployment. Building the
frame belongs to a launch. Everything else is decided by asking which of the two it *meant*, and the
answer is usually obvious once the question is put that way.

## The hooks

Top to bottom is the order they run in. **The fourth column is the one to read** — most routing mistakes
are made by putting work somewhere that cannot reach what the work needs.

| hook | how often | what is reachable | what goes here |
|---|---|---|---|
| **`main()`, before the bootstrap's run call** | once per **deployment** | the filesystem, your database, config, system properties, logging. **Nothing Vaadin at all** | schema / DDL, seeding **a shared database**, first-run accounts, connection pools, creating a log directory |
| **`VaadinServiceInitListener`** | once per deployment | the `VaadinService`, and its hooks for registering the listeners below | *registering listeners.* `SwingBridgeEmulatorsBootstrap` is exactly this and nothing more |
| **`SessionInitListener`** | once per user session | the `VaadinSession`. No UI yet | **usually nothing** — see below |
| **`UIInitListener`** | per `UI` — **again on every F5** | the `UI`, the session, the browser | SB-Emulators' own per-UI wiring. **Not** your start-up work |
| **`bootstrap()` → `mainUI()`** | once per **browser tab** | the above, plus your app's tab scope — [`FormerSingletons`](./former-singletons.md) resolves here | frame construction; anything the desktop did once per launch |
| **the `@MainWindow` frame ctor** (inside `SwingUtilities.invokeLater`) | once per tab | the above, plus the `callSwing` virtual-thread envelope — a blocking dialog can park | `Preferences` reads, restoring window bounds, anything that may open a modal |

### `main()` is entirely pre-boot — there is no "after boot" in it

The bootstrap's run call — `SpringApplication.run(…)` or `new VaadinBoot()…run()`, whichever your seed
carries — **blocks until the server stops**. Whatever you put after it runs during shutdown, not during
service, so the familiar "start the server, then do the rest" shape is not available: everything you
write in `main()` runs before the first request. (Vaadin Boot's `start()` / `stop()` exist for an app
that wants to own the server lifecycle itself, which a migration rarely needs.)

That is a constraint, not just a fact, and it is the reason row 1's third column is so short. Code in
`main()` cannot read the browser time zone, cannot read
[`Preferences`](./static-fields.md#static-initializer-reading-the-browser-zone-or-preferences--defer-to-a-ui-thread-call-site),
has no `VaadinSession`, and cannot reach the
[`FormerSingletons`](./former-singletons.md#what-still-throws) holder. **Three of those fail loudly** —
`BrowserTimeZone.get()`, a `Preferences` read and `FormerSingletons.get()` each throw — which is the good
news; the bad news is that you find out by running it.

**The date emulators are the quiet exception, deliberately.** `vaadinx.text.SimpleDateFormat` and
`vaadinx.util.Calendar` do *not* throw here: they fall back to the server zone and log one WARN, so
pre-boot date code keeps working as written. That is the intended behaviour, not a gap — see the bill
below.

### `VaadinServiceInitListener` is for registration, not for work

Its body should install listeners and return. `SwingBridgeEmulatorsBootstrap` — [Phase 4 step
5](./guide.md#phase-4--production-wiring)'s one mandatory line — is the canonical shape: its whole body is
two registrations, an `addUIInitListener(...)` for the per-UI legs and a tab-scope handshake whose destroy
listener ends the app on tab close. Nothing in it *runs* at service init. If you find yourself doing
start-up *work* here, the work almost certainly belongs in row 1 (deployment-owned) or row 5
(launch-owned).

Register your own listener by adding a second line to the same SPI file, never by editing SB-Emulators' class or
copying its body — a copied body is a fork that stops receiving the wiring SB-Emulators adds in later versions, and
the class is the one place that wiring is guaranteed to stay complete.

### `SessionInitListener` — you probably do not need it

It is the hook migrators reach for when they see the word "session", and a migrated app rarely has
anything to put in it. Per-user state is written when the user *does* something — logs in, picks a locale
— not when their session is created, so the natural home for it is the code that already handles that
event. Reach for this hook only for genuine session-lifetime setup that must exist before any view runs.

Its sibling `SessionDestroyListener` is the more useful half, and even then only if you have per-user
resources to release that your own shutdown path does not already cover.

### ⚠ `UIInitListener` re-runs on F5; `mainUI()` does not

This is the distinction most likely to cost you an afternoon, because both look like "once, at the start."

A browser refresh **destroys the `UI` and builds a fresh one**, so a `UIInitListener` fires again — which
is correct for SB-Emulators' own legs (the browser zone and the focus bridge genuinely are per-`UI` facts) and
wrong for yours. Start-up work placed there runs on every refresh: a duplicate audit row per F5, a
re-opened dialog, a counter that climbs while the user presses F5.

`mainUI()` does not have this problem. It is called from `MainWindowRoute.bootstrap()`, which runs **at
most once per route instance**, and `@PreserveOnRefresh` preserves the instance across a refresh — the
whole reason that annotation is on the route scaffold. So `mainUI()` is once per browser **tab**, which is
the lifetime "once per app launch" translates to.

**If you are choosing between the two, you want `mainUI()`.** The rule of thumb: `UIInitListener` is for
facts about the *rendering surface*, `mainUI()` is for your *application*.

This is the same trap [`static-fields.md`](./static-fields.md#the-three-scopes) names on the storage axis
— "UI scope is a trap, not a fourth option" — seen from the code side rather than the state side.

## Two worked examples

Both are from one migrated app, and together they are the whole page: the first shows the routing, the
second shows what the routing then costs you.

### Seeding an empty database → `main()`

```java
public static void main(String[] args) throws Exception {
    new File("log").mkdir();
    Seed.seedIfEmpty();          // reference data, if the tables are empty
    addUserForFirstTime();       // the first-run ADMIN account
    // ...then the seed's bootstrap call, last
}
```

All three touch the one shared database or the one filesystem, so they are **deployment**-owned and stay
in `main()`. Note that "it is guarded, so it would be harmless per tab" is not a reason to move it: two
tabs opening simultaneously against an empty database *race*, and the guard does not save you. Idempotent
work still belongs where it means something.

The deciding property is what the seed writes **to**, not that it is a seed. If your "store" is an
in-memory `List` — no database behind it — then there is nothing deployment-owned to seed: the store
itself is per-launch, so it and its `seedIfEmpty()` both move to `mainUI()`, one per tab. Putting the
seed in `main()` there would force the store to a `static` shared by every user, which is the leak
[`static-fields.md`](./static-fields.md) exists to prevent.

### An "Application Started" audit row → `mainUI()`

```java
public static void mainUI() {
    SwingUtilities.invokeLater(() -> {
        setApplicationStartLog();     // one row per app launch
        setUpAndShowGui();
    });
}
```

On the desktop, one JVM start was one launch, so the row could sit in `main()` and mean "the app was
started". Here those have come apart, and **only you know which one the row meant**. If it recorded a
launch, it belongs in `mainUI()`, where a launch now happens. If it is really a deployment log — "the
service came up" — leave it in `main()` and rename it, because that is what it will now record.

That is the general shape of the judgement: the code does not change, the *meaning* of where you put it
does.

### And the bill for the first one

Putting the seed in `main()` is correct, and it immediately costs you the browser time zone: `main()` has
no session, so `BrowserTimeZone.get()` throws there, and there is no UI thread whose `EmulatorContext` you
could capture.

This is not a mistake in the routing — it is the routing working, and it is what row 1's third column is
warning you about. The bill is smaller than it looks, and it splits by bucket. See the third residual in
[`dates.md`](./dates.md#3-javatime-and-browserdateutils--the-residuals) for both halves:

- **Bucket-1 code — `SimpleDateFormat` / `Calendar` / `GregorianCalendar` — stays exactly as written.** It
  does not throw here; it takes the server zone. There is no user, so there is no browser zone to want, and
  a migrated Swing app is typically intranet-deployed, where the server's zone *is* its users' zone.
- **`java.time` code keeps the server zone too, but calendar dates move to midday.** `atStartOfDay` stores
  an instant that renders a day early for any browser west of the server, and unlike the zone itself that
  is expensive to fix later, because the value is **stored**: a zone is a one-line change, midnight rows are
  a data migration.

Moving the computation to a UI-thread call site is right only when it was really per-user work that `main()`
happened to host, which a seed is not.

## How your app ends

The other end of the lifetime, and the reason three separate greps in
[Phase 1](./guide.md#S_triage_reports) and [Phase 3](./guide.md#S_delete_single_instance_guard) all
land on this page: **`System.exit`, the single-instance guard and every Quit path are one question**,
which is *what ends a migrated app*.

**Disposing the app's last displayable window ends the user's session** — the JVM keeps running for
everyone else. That is the desktop's own rule (AWT exits the JVM once no displayable window, no
pending event and an idle EDT remain), so it holds **whatever the `defaultCloseOperation` is**, and it
holds for both peer-originated close (browser tab close) and a programmatic `frame.dispose()`.

**The tab then shows "The application has ended"**, and a click or Esc starts a fresh app instance —
the relaunch, left to the user. The same notice appears whenever the session expires, since that
ends the app instance too. `SwingBridgeEmulatorsBootstrap` installs it only over Vaadin's default, so to word it
differently set your own `SystemMessagesProvider` (`VaadinService.setSystemMessagesProvider` in a
`VaadinServiceInitListener`) and it wins.

Three consequences, and they are the whole rewrite:

- **Any `dispose(); System.exit(0)` call site rewrites to a bare `dispose()`** — button click, menu
  item, `AbstractAction`, key shortcut. The existing `setDefaultCloseOperation(...)` call in the
  frame's constructor stays exactly as it is, whichever op it names; it is the `System.exit` line that
  goes. Each surviving `System.exit` would kill the JVM and every other user's session — a production
  outage, which is why the [guardrail](./guide.md#S_guardrails) bans it outright afterwards.
- **A window still open, or a `Timer` still running, keeps the app alive** — an ownerless dialog you
  never disposed, a login frame's replacement, a repeating `javax.swing.Timer` nobody `stop()`s. Same
  on the desktop; the difference is only what the user sees (there, a process that won't quit; here, a
  live session behind an empty tab). Add an explicit `UI.getCurrent().getSession().close()` only where
  the `System.exit` ran **with windows still open** (an exit that skipped the app's own teardown) or
  **while a `javax.swing.Timer` is still running** — a status-bar clock you never `stop()` otherwise
  leaves the user on a live session with nothing on screen.
- **`EXIT_ON_CLOSE` resolves differently by frame role.** On the `@MainWindow` JFrame (rendered inline
  as the route content) it behaves as `DISPOSE_ON_CLOSE` — it disposes, and the app ends per the rule
  above. On a regular `JFrame` rendered as a Vaadin Dialog, a close-attempt with `EXIT_ON_CLOSE`
  throws `IllegalStateException`; use `DISPOSE_ON_CLOSE` or `HIDE_ON_CLOSE` there.

### Delete the single-instance guard

Many desktop apps refuse to start twice, and the guard is always JVM-startup code — so it lands in
`main()` at exactly the point [Phase 3](./guide.md#S_split_main) splits. Recognise it by the shape
rather than the name:

- a `ServerSocket` bound to a fixed port, with a `Socket` probe of `localhost` first (a successful probe means "already running");
- a lock file — `File.createNewFile()` on a `.lock`, a `FileLock` on a `FileChannel`, a PID file;
- `SingleInstanceService` (Java Web Start), a named OS mutex through JNI/JNA, or an RMI registry bind;
- and often a second half that *raises the running window* when a later launch pokes it — `frame.setState(Frame.NORMAL)`, `toFront()`, `requestFocus()`.

**Delete the guard, its call site, and the raise-the-window handler.** Keep whatever real startup work
sat next to it. A guard that decided whether to continue typically looks like
`if (!alreadyRunning()) { startGui(); } else { System.exit(0); }` — keep the `startGui()` branch, drop
the condition and the `else`.

**This is also why one `System.exit` is not a Phase 1 fix.** An exit that is the `else` of a guard goes
*with the guard*, whole; deleting just the call leaves an empty `else { }` in code that is about to go,
and there is no `dispose()` beside it to keep. Leave it where it is and note it for this step.

Two reasons the guard goes, and the second is the one that bites:

1. **It's redundant: the session already is the instance.** A Vaadin session gets one navigated live app UI — a second tab is curtained rather than run as a second copy — and the app's lifetime is tied to its browser tab. You get the guarantee the guard was enforcing, at the scope that makes sense on the web, without writing anything.
2. **Kept, it inverts into an outage.** Your process-level resources are now per *JVM*, shared by every user, while the guard assumes they're per *app instance*. The first session binds the port (or takes the lock); every later session's probe then succeeds, concludes "another instance is running", and runs the `System.exit(0)` branch — killing the server and every other user's session with it. The guard doesn't just stop guarding; it becomes a self-inflicted outage that arrives with your second user.

Multiple sessions are multiple *users*, which is the point of a web app, not a condition to defend
against. And the raise-the-window half has a browser-native answer: the user switches to the tab they
already have open.

## See also

- [Phase 3](./guide.md#phase-3--entry-point-setup) — the split itself, and the route scaffold.
- [Phase 4](./guide.md#phase-4--production-wiring) — the wiring the rows above assume is in place.
- [`static-fields.md`](./static-fields.md) — the other axis: where state lives, and the three scopes.
- [`former-singletons.md`](./former-singletons.md#what-still-throws) — the holder's own reachability rules,
  which match row 5 onward.
