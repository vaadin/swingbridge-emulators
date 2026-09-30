# `:sampler`

Vaadin-Boot app that demos the Swing API rendered to Vaadin — exactly what `:emulators` is, made browser-runnable. Internal validation testbed: each route exercises one component slice's API surface, and a per-route `WarnInventoryTest` asserts the slice runs end-to-end with zero `EHelper.onUnimplemented` WARNs.

Full role definition (what Sampler is and is *not*, scope, exit-gate inventory) lives in [description.md](./description.md). This README is the practical "how do I run it" pointer.

## Run

From the repo root.

**Already built — just running it (or after doc-only changes):**

```
./mvnw -C -pl sampler exec:exec
```

**First run, or after changing `surrogates` / `emulators`:** install the SB-Emulators modules `sampler` depends on into `~/.m2` first, then run:

```
./mvnw -C install -DskipTests && ./mvnw -C -pl sampler exec:exec
```

`exec:exec` forks the app JVM off the already-installed artifacts, so it only reflects those modules as of the last `install` — that's why a change in a dependency module needs the `install` step, but a bare re-run doesn't. `sampler` alone can't use `-am` here — `exec:exec` would then run on every reactor module.

Open <http://localhost:8080>. Sampler mounts a single `SamplerRoute` at `/` whose `@MainWindow` `SamplerFrame` renders inline. Two nav affordances, both rendering `SamplerCatalogue.ALL`, swap demo `JPanel`s into the content area:

- the **left column** (`SamplerNav`) — one collapsible group per category (Forms, Buttons & actions, Data, Containers, Windows & dialogs, Text, Async, Platform), at most one open at a time;
- the **"Jump to" picker** (`SamplerJumpTo`) — a filterable dropdown of every demo, for going straight to one you can name.

Requires JDK 24+. The parent `pom.xml` wires `--add-opens java.base/java.lang=ALL-UNNAMED` into the `exec:exec` run (and into Surefire) — needed by the blocking-dialog runner's reflection bridge for the virtual-thread carrier ([D_callswing_loom](../emulators/decisions.md#D_callswing_loom)).

## Test

```
./mvnw -C clean install -DskipTests && ./mvnw -C -pl sampler test
```

Karibu only — server-side, synchronous, no headless browser. Per slice: a `WarnInventoryTest` walking the user path (often combined with a per-bucket API micro-driver), plus a `NavigationSmokeTest` that walks every `SamplerCatalogue.ALL` entry and confirms each demo loads with zero stub WARN. Per-component method-level coverage stays in `:emulators` and `:surrogates` test suites.

## Adding a demo slice

Demos are plain `JPanel` subclasses swapped into `SamplerFrame`'s content area — not separate `@Route`s. To add one:

1. Drop a new `XxxPanel extends JPanel` under `src/main/java/com/vaadin/swingbridge/sampler/`.
2. Add one line to `SamplerCatalogue.ALL`: `new Demo("Data", "Xxx", XxxPanel::new)`. Keep it adjacent to its category's other entries — `SamplerNav` emits a category header the first time the category appears. Both nav affordances and `NavigationSmokeTest` pick it up from here; nothing else needs editing.
3. Add an `XxxWarnInventoryTest` under `src/test/java/com/vaadin/swingbridge/sampler/` that drives the panel's user path (navigate via `SamplerRoute`, then `Navigate.to("Xxx")`) and asserts zero `EHelper.onUnimplemented` / `EHelper.onUnsupportedPeerShape` calls. Optionally, an API-surface micro-driver locking in zero-stub coverage on the slice's documented surface.

## See also

- [description.md](./description.md) — full role, scope, exit-gate inventory, project trajectory.
- [`../CLAUDE.md`](../CLAUDE.md) — project rules, current scope.
- [vaadin-blocking-dialogs](https://github.com/mvysny/vaadin-blocking-dialogs) — virtual-thread machinery the modal-dialog routes (OptionPanes, blocking JDialog) park through.
