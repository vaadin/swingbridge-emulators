# One `ApplicationContext` per tab — making a Spring-based Swing app migratable

**Status:** parked, 2026-09-23. Research, not a plan. Maintainer-facing.

A Swing app **already built on Spring** is currently unsupported
([M1D_spring_bootloader_only](../migration/1-swing-to-emulators/decisions.md#M1D_spring_bootloader_only)).
This file is what it would take to lift that. The supported half — an app with no Spring, hosted on
Spring Boot as a bootloader only, former singletons on vaadin-tab-scope — is settled there and is
not this file's subject.

## The hazard

On the desktop there is one JVM per user, so one `ApplicationContext` per user: a singleton bean
*is* per-user state, and apps are written that way (a `SessionService` holding the logged-in user,
a `DocumentManager` holding the open document, a selection model bean). On a server one context
serves every user, and every such bean leaks between them.

It is the static-field hazard in bean form, with one difference that makes it worse: **nothing sees
it.**

- `StaticSweep` reads `static` fields. Bean state lives in instance fields.
- `MigrationGuardrails` — same engine, same blind spot.
- `HazardScan` has no Spring row at all (`service-lifecycle-teardown.md`'s DI-detection step was
  never built, and its grep list is ready). Even with one, a grep finds the container, not the
  hazard: it cannot tell a stateless service bean from a stateful one.
- Phase 6's two-sessions check (`S_multi_tab`) is the only thing that catches it, and only on the
  paths somebody happens to drive.

So a migrated Spring app can pass every gate and still show one user's open document to another.
That is why the position is *unsupported* rather than *supported with a caveat*.

## The idea

Mirror what tab scope does for statics. `R_tab_is_the_new_jvm` says the tab is the new JVM; if the
desktop had one context per JVM, the migrated app gets **one context per tab**. The migrator's
singleton beans then keep their desktop meaning with no reclassification, and — the attractive part
for the local-LLM target — no bean-by-bean judgement at all on the default path.

Spring supports this natively: `AnnotationConfigApplicationContext` (or a `GenericApplicationContext`
over the app's existing config) created per tab, `refresh()`ed, `close()`d on tab close.

## Open questions

`Q_parent_child_split` — some beans really are deployment-wide: a `DataSource`, a connection pool, a
JPA `EntityManagerFactory`, a cache of reference data. A pool per tab exhausts the database; an
`EntityManagerFactory` per tab costs seconds and memory per tab. Spring's parent/child contexts fit
this — infrastructure in a shared parent, stateful beans in a per-tab child — but *which bean goes
where* is a classification, and a classification is exactly what the per-tab default was meant to
avoid. Is it the same judgement as `M1D_static_taxonomy`'s `Q_world_global` ("does its state come
from, or feed, a resource outside the process?"), and can that taxonomy be reused verbatim? A
`DataSource` passes that test cleanly; a `@Service` with an injected repository and a mutable
`currentCustomer` field does not split cleanly at all.

`Q_refresh_cost` — every new tab pays a full context refresh: component scanning, proxy creation,
`@PostConstruct`s. Seconds on a large app. F5 costs nothing (tab scope survives it), so it is the
first page load per tab that pays. Measure it on a real app before judging; consider whether the
parent/child split moves most of the cost into the parent anyway.

`Q_creation_timing` — tab scope is not available during UI init: it keys on the browser window name,
which arrives asynchronously (Flow issue #13468; `SwingBridgeEmulatorsBootstrap` already waits for
`retrieveExtendedClientDetails` before `TabScope.setup`). The child context has to be created after
that and before `MainWindowRoute` constructs the `@MainWindow` frame, since the frame's constructor
is where the app first `getBean`s. Is there a clean hook between the two, or does it force a new
`AppInstance`-level lifecycle event?

`Q_teardown` — tab close → tab-scope destroy listener → `child.close()` → `@PreDestroy` /
`DisposableBean`. That would answer `service-lifecycle-teardown.md`'s `Q_di_containers` (container
shutdown per app instance) almost for free. Ordering against `AppTab.onTabClosed` and the shutdown
coordinator is the part to design: the app's `WINDOW_CLOSING` handler may still need its beans, so
the context must outlive it.

`Q_both_lanes` — this is not a Spring Boot question. The Vaadin Boot arm of the consent fork keeps
the app's Spring "as a plain library managing its own bean graph", so the per-tab context is needed
there just the same, built by our code instead of Boot's. On the Spring Boot lane there is an extra
sub-question: is Boot's own context the shared parent (it already holds the Vaadin servlet and
`SwingBridgeEmulatorsBootstrap`), or does the app's config stay entirely separate from it? The
former is tidier; the latter keeps the migrator's Spring version decoupled from Boot's, which is the
whole point of the fork's Vaadin Boot arm.

`Q_static_context_holder` — many Spring Swing apps keep the context in a static
(`AppContext.get()`, `ApplicationContextProvider implements ApplicationContextAware`). The static
sweep will find it and route it to tab — which, under this idea, is *correct*: the holder becomes a
`FormerSingletons` member resolving the tab's child context. Check that the sweep's verdict and this
idea agree without special-casing, and that `ApplicationContextAware` beans in the *parent* do not
capture a child.

`Q_request_scoped_leftovers` — does anything in a typical desktop Spring app assume a single thread
or a single user in ways a per-tab context does not fix? Candidates: `@Scheduled` jobs (one per tab
now — probably wrong for a nightly import, right for a per-user poll), `ApplicationEvent`s published
on the parent and expected by a child, Spring Security's `SecurityContextHolder` (thread-local,
desktop apps sometimes set it once globally).

`Q_testbed` — none exists. `testapps/crud` has no DI. This needs an adopted open-source Swing app
that is genuinely Spring-wired, with at least one stateful singleton bean, via the `adopt-testapp`
skill. Everything above should be measured on it, not argued.

## What to do instead, meanwhile

Nothing is built for this. The guide states the position in three places
(`M1D_spring_bootloader_only` lists them), and the advice for anyone going ahead is a manual audit
of every singleton bean plus the Phase 6 two-sessions check driven hard. Note that the migrator is
told only if they read the guide: with no `HazardScan` row, an agent following the procedure step
by step never meets the word "Spring" in a report. The DI-detection row from
`service-lifecycle-teardown.md` would fix that, and would be the natural place to print
"unsupported" — also deliberately not done now.

A cheaper intermediate worth considering once a testbed exists: a `HazardScan` row, or a
`StaticSweep`-style class-file pass, listing every singleton-scoped bean class with a non-final
instance field. Not a verdict — a worklist, the same shape as the static sweep — which would turn
"audit every bean by hand" into a report. Deliberately not done now.
