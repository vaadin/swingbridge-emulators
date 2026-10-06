# Tab close is eventual, not prompt — no unload beacon is installed

**Status:** brainstorm / not decided. Measured 2026-09-21 while walking the Spring Boot lane on
`testapps/crud`, but **the finding
is bootstrap-independent** — it is a property of
[D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle) that no doc states, and it
reproduces on Vaadin Boot. Filed separately so it survives whatever happens to the bootstrap
question. Maintainer-facing.

Neighbours: [`cancelable-tab-close.md`](./cancelable-tab-close.md) (can a close be *stopped*),
[`multi-tab-and-session-scoping.md`](./multi-tab-and-session-scoping.md) §"Current limitations" item
2, which names the same transient window from the curtain's side.

---

## The finding

`tab-scope` 0.3 exposes `TabScope.installTabCloseBeacon(List<RequestHandler>)` and
`onUnloadBeacon(UI)` — **and nothing a migrated app gets calls either.** Only Sampler's
`SamplerServlet` installs the beacon (the piece D_shutdown_lifecycle's "Prompt-shutdown latency"
bullet describes as "the app installs"); `SwingBridgeVaadinServlet`, `:emulators-spring`'s
`SwingBridgeSpringServlet`, the host-app seeds and the guides do not, and
`SwingBridgeEmulatorsBootstrap` calls `TabScope.setup` only. So in a migrated app a closing tab sends
*nothing*, and teardown falls to the scheduled reaper: `CLEANUP_DURATION_MS = 60000`, `scheduledReapEnabled = true`,
both read off the class's `<clinit>`.

**Measured with a 5 s heartbeat:** at **+20 s** after the app tab closed the session still believed
the app was open and a second tab got the curtain; by **+75 s** ownership had been released and the
survivor had taken over, building a fresh working `MainFrame`. So the mechanism works; it is the
latency that is undocumented.

## Why it matters

`crud`'s `MainFrame` sets `EXIT_ON_CLOSE`, which is the common shape. *"The app shuts down when you
close the tab"* and *"the app shuts down a minute or more after you close the tab"* are different
sentences. [`lifecycle.md`](../guides/1-swing-to-emulators/lifecycle.md) § "A `windowClosing` handler
runs, but cannot stop the tab closing" says `WINDOW_CLOSING` arrives *"usually within a minute, at
the latest when the session times out"* — the "within a minute" is the beacon-installed (Sampler)
figure, which a migrated app without the beacon is not shown to meet (`Q_lone_tab_close`).

## Open

`Q_tab_close_beacon` — should the library install the beacon (in `SwingBridgeVaadinServlet` /
`SwingBridgeSpringServlet`'s `createRequestHandlers`, as `SamplerServlet` does), making tab close
prompt instead of eventual? It is one call at a place we already own. Against: it adds a
`RequestHandler` to every migrated app, and the library warns *"no stock UidlRequestHandler found"*
when it cannot attach, so that failure mode needs knowing before it is on by default; and
`SamplerServlet`'s javadoc states the opposite stance — "the app wires it, never the library
(R_no_spi_selfregister)" — which would need re-arguing, since a servlet subclass the app already
extends is not SPI self-registration. The alternative is the seeds and host-app guides telling the
migrator to wire it.

`Q_reap_at_default_heartbeat` — **inferred, not measured.** The reaper collects *orphans* (scopes
whose UIs are all detached), and detachment is heartbeat-driven. The 60 s above was observed with
`heartbeatInterval=5`; at Vaadin's default 300 s (3 missed ≈ 15 min) teardown could be far slower.
Measure before any of this reaches a guide — an `EXIT_ON_CLOSE` app that keeps running for a quarter
of an hour after its last tab closed is a documentation problem even if nothing is broken.

`Q_lone_tab_close` — closing the *only* tab produced no observable signal in ~2 min. The mechanism
above was verified with two tabs, where the survivor's traffic drives the cleanup; whether the app's
own `WINDOW_CLOSING` handlers run when the last tab goes is untested, and for `EXIT_ON_CLOSE` that is
the case that matters. Likely the same reaper latency rather than a separate defect, but it is
unmeasured either way, and a beacon (`Q_tab_close_beacon`) would answer it as a side effect.

**Method caveat that made this hard, and will again:** the teardown success path logs *nothing*.
`AppTab.onTabClosed` logs only at DEBUG *on failure*, and `AutoShutdown`'s "App instance over" INFO
is the dispose-driven trigger, which the tab-close path does not reach. So the evidence above is
behavioural (ownership released, new `MainFrame` constructed), not a direct observation of the
dispatch. Anyone re-running this should not read silence as absence.

## Graduation

- Whichever way `Q_tab_close_beacon` goes → an amendment to
  [D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle), since the tab-close
  detector is its mechanism.
- The measured latency, once `Q_reap_at_default_heartbeat` is answered at a realistic heartbeat →
  confirm or correct the "usually within a minute" sentence in
  [`guides/1-swing-to-emulators/lifecycle.md`](../guides/1-swing-to-emulators/lifecycle.md),
  because a migrator whose app holds a lock or a file handle until shutdown needs the number.
