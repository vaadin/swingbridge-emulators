# How SwingBridge Emulators compares

Where SwingBridge Emulators — SB-Emulators for short — sits among the other ways of getting a Swing application into a browser, and against the alternative every migrator is really weighing: rewriting the app.

This document compares *approaches*, not products: the ways a Swing application can reach a browser, and the rewrite. The only product it names is SB-Emulators.

**Two things to know about how this document is written.**

1. **It is factual about *mechanism* and generous about *fit*.** Several of the approaches below solve a problem SB-Emulators does not solve, and this document says so rather than arguing the point.
2. **Its figures are dated.** The coverage and test counts were measured on **2026-08-19** and the cited evidence carries its own dates; all of it moves, and a stale figure is worse than none.

## What each approach leaves you owning

Products within an approach class differ less than the classes do. What matters when you choose is what you are holding a year later.

| Approach | What you end up owning |
|---|---|
| **Pixel streaming** | The desktop app, rendered on the server and streamed as pixels. Server cost scales per connected user; there is no real DOM, so no HTML, no SEO, no mobile layout — and **the application has not been migrated**. You still own the Swing app. |
| **Bytecode → WebAssembly** | The same Swing app, executing client-side. Technically impressive, and again **no migration**: same code, same destination, plus a large download. |
| **Java → JavaScript transpilation** | Your Swing app as generated JavaScript. Nobody wrote that output and nobody will maintain it by hand. |
| **One-shot source transformation** to another UI framework | Generated code in the target framework, lossy where the frameworks disagree — and you inherit a codebase nobody authored. |
| **SB-Emulators** | Your source becomes **a real Vaadin application** — real DOM, real Vaadin components — with a defined, incremental path to [stop depending on SB-Emulators at all](./README.md#the-migration-arc-four-stages-two-api-surfaces). |

**That last row is the whole distinction.** Every other destination is *"your Swing app, reachable over a network."* SB-Emulators' destination is *a normal Vaadin codebase* — which is worth something only if that is genuinely where you want to end up. If it is not, streaming is a better answer than SB-Emulators.

## On licensing

**The artifact a migrated app compiles against — `swingbridge-emulators` — is GPLv2 + Classpath Exception.** It is a derivative work of OpenJDK and takes OpenJDK's terms ([`PROVENANCE.md`](./PROVENANCE.md), [D_gplv2_ce_relicense](./emulators/decisions.md#D_gplv2_ce_relicense)). For a migrator the distinction that matters is the *exception*, not copyleft:

- **The Classpath Exception is what makes linking obligation-free**, and it says so in as many words: permission "to link this library with independent modules to produce an executable, regardless of the license terms of these independent modules". A proprietary app linking SB-Emulators is in exactly the position it is already in with respect to OpenJDK, which it links every day under this same licence. The comparison a reader needs is *"the terms your app already accepted by running on a JVM"*, not *"permissive versus copyleft"*.
- **Modifying the emulators is GPLv2.** An enterprise that forks `swingbridge-emulators` to fix a component has an obligation an Apache-2.0 fork would not carry. That is a real cost of the licence. **Not every artifact in the set is on those terms**, though, and the accurate sentence is per artifact: every artifact but `swingbridge-emulators` and `swingbridge-emulators-printing` — `swingbridge-surrogates` (the stage-3 API a rewritten view targets), the Spring artifact, and the migration annotation, guardrails and tool — is **Apache-2.0**, so forking *those* carries no copyleft obligation ([D_licence_lanes](./emulators/decisions.md#D_licence_lanes), and README's per-artifact table is the migrator-facing copy). That is a per-artifact fact, not a rebuttal to the comparison above: the copyleft artifact is the one doing the Swing work, and that is the one a route-level comparison is about.

## What is actually new here

Three claims hold up under scrutiny. They are "nobody has been here" claims, not "we are better" claims — which is also why someone else shipping a release does not answer them.

1. **No one has finished a `javax.swing` API reimplementation.** The one earlier open-source attempt at this artifact class **stopped in 2013**. Against that record, SB-Emulators covers **46 of the 50 top-level `javax.swing` component classes** (measured 2026-08-19 by reflection over JDK 25's `java.desktop`; the denominator is `javax.swing` top-level only, and the [component list](./README.md#whats-implemented) is the precise version of this number) behind **2,695 tests**. Whether that surface is *enough* for your app is a question the component list answers; that nobody else has built it is not really disputable.
2. **It keeps Swing's control-flow contract while producing framework-native output.** `JOptionPane.showConfirmDialog` blocks and returns the user's answer — in a browser — because a virtual thread parks in place of the request thread (see [Blocking dialogs](./README.md#blocking-dialogs)). Streaming and WASM get that for free by *being* Swing; **every source-transformation approach loses it**, which is the concrete mechanism behind the word "lossy". SB-Emulators is alone in holding both halves.
3. **It is stoppable in more than one place.** Streaming, WASM, transpilation and a direct rewrite each have exactly one terminal state. SB-Emulators has four stages and **every stopping point is a working product** — including stopping at stage 2 indefinitely. That optionality is what makes the sequencing argument in the next section possible at all.

## SB-Emulators versus a direct rewrite

[README § "How they compare"](./README.md#how-they-compare) sets out the axes. This section adds the two things that document leaves implicit: the evidence behind "the specification is the hard part", and why choosing SB-Emulators is a **sequencing** decision rather than a decision not to rewrite.

| | **Rewrite it** | **Emulators first** |
|---|---|---|
| The first question you must answer | *"what is this view supposed to do?"* | **none — it runs the same code** |
| Behaviour preservation | an achievement, verified per view, afterwards | **a property of the method** |
| In production | at the end | **in weeks — and it stays there** |
| Abandonable | no: until it lands you have nothing | **at any view** |
| Variables when something misbehaves | the code **and** the web platform, at once | **one, then the other** |
| Funding shape | one big-bang modernization approval | **two smaller ones, at different times** |
| What the endgame is | a rewrite against a spec nobody can write | **a rewrite against a running oracle** |

### Fidelity is by construction

The import swap leaves the method bodies character-for-character identical ([D_import_swap_target](./emulators/decisions.md#D_import_swap_target)), and the emulators reproduce Swing's contract without silent improvements ([D_no_silent_improvements](./emulators/decisions.md#D_no_silent_improvements)). Behaviour preservation is therefore not something the migration achieves and then verifies; it is a property of running the same code.

The published evidence about the alternative is worth citing precisely, because it is easy to get wrong:

> **ScarfBench** (Pavuluri et al., IBM Software Innovation Labs / RPI / Columbia; arXiv:2605.06754v2, May 2026) evaluates five production coding agents on 204 behaviour-preserving migration tasks between Spring, Jakarta EE and Quarkus. Agents **compile** the migrated application in up to **93%** of cases, yet the best pass only **15.3%** of behavioural tests on single-layer migrations and **12.2%** on whole applications — and **exactly 1 of 204 tasks** produced a fully behaviourally equivalent result.

**The finding is the compile/test gap, not the pass rate.** The failure mode is not agents producing garbage; it is agents producing code that builds cleanly and quietly does something different — divergences you discover in production, one edge case at a time.

**Three caveats travel with the number:**

- **The benchmark contains no GUI migration at all.** The pairs are server-side enterprise Java. Citing it as direct evidence about Swing→web is an extrapolation the paper does not license. What it does support is an *a fortiori* argument: behaviour-preserving migration is already this hard **between close cousins sharing a language, a build system and an execution model**, and Swing→web is harder on each of those axes.
- **Single-shot, temperature 0, no repair loop** — the authors' own limitation, described as *"a compute-bounded estimate of current capability rather than an upper bound."*
- **Preprint, not peer-reviewed.**

The result is not "under 10% behavioural success": that reading is wrong on the primary metric (15.3% / 12.2%) and simultaneously *understates* strict equivalence (1 of 204).

### The code that cannot be specified

The sharper form of the same argument needs no benchmark at all.

Real legacy Swing applications have no oracle. ScarfBench measured agents against expert-authored specifications, Playwright tests and a known-correct target implementation. The applications SB-Emulators exists for have none of that: much of the code works and **nobody currently employed knows why**. The engineer who understood it left and took the reasoning with them, and the team's policy on those files is *"don't touch it."*

For that class of code a rewrite is not merely risky, it is unspecifiable. Any rewrite — human or agentic — must first answer *"what is this supposed to do?"*, and there is nobody left who can answer. **SB-Emulators never asks the question, because it runs the same code.**

**This argument belongs to behaviour-preserving migration as a category, not to SB-Emulators.** It is equally true of any approach that runs the original code unchanged, and stronger there, since nothing is even recompiled.

### You refactor a live product, not a long-lived branch

By the time the codebase work starts, the app is in production, users are on it, and it behaves correctly. There is no eighteen-month branch that merges like a car crash; each improved view ships on its own; and stopping at any point still leaves a working product. This property does not depend on how the app got into the browser first.

### Two one-variable problems, and two budgets

Web **delivery** (ops, infosec, sign-off, session and multi-user semantics) and **the codebase** are two projects with almost no technical overlap. Doing them simultaneously means every failure is a two-variable problem, which is where most *"the migration went badly"* stories come from. The commercial half of the same point: two phases separated in time can be funded and approved separately, and two smaller approvals are often obtainable where one big-bang modernization budget is not.

### It does not cancel the rewrite — it changes when you do it

This is the spine of the argument, and it is why SB-Emulators' own README calls SB-Emulators [a bridge and not a destination](./README.md#the-honest-trade-off). A per-view rewrite from a running, behaviour-verified emulator app is a far better-posed problem than a rewrite from a specification: the running app *is* the specification, and it can be diffed against.

So the claim is not *"don't rewrite"*. It is **"you will rewrite either way; the choice is whether you do it blind or second."**

One consequence worth flagging as exploratory: a mechanical, oracle-checked port is plausibly within reach of a *local* model where a from-scratch rewrite is frontier-cloud-only, which would mean the customer's source never leaves its network. That is [an idea under exploration](./ideas/local-llm-migration-target.md), not a shipped capability, and must be said that way.

## Where SB-Emulators is weaker

The comparisons above are only credible next to this list.

- **SB-Emulators needs source and a recompile.** Streaming and WebAssembly need neither. If the source for a dependency is missing, SB-Emulators is not an option.
- **Third-party Swing toolkits need porting** — SwingX, JideSoft, JGoodies, NetBeans RCP, JOSM. Approaches that execute the real bytecode run them unchanged; SB-Emulators must implement each one or the app must drop it. This is the single biggest practical gap. The add-on emulators are the shape of an answer to it, a library at a time, but the two that exist are starter prototypes, shipped as-is (see "Claims this project does not make").
- **An unconverted singleton is shared by every user.** SB-Emulators has no per-user classloader boundary of the kind a streaming deployment can give each session — mitigated by the [static-state scope model](./migration/1-swing-to-emulators/decisions.md#M1D_static_taxonomy), the migrator-facing [static-fields reference](./guides/1-swing-to-emulators/static-fields.md) and an ArchUnit lint, but mitigation is not isolation.
- **Your app compiles against `vaadinx.*` for as long as the migration lasts**, and that dependency does not end until stage 4 does. Emulators-first buys its sequencing by taking on that exposure.
- **The sources stay Swing-shaped in the meantime**, and the stage-3 migration guide (emulators → surrogates) does not exist yet; stage 3 is a built API without a written technique behind it.
- **Custom `Graphics2D` painting and Look-and-Feel dispatch are [permanently out of scope](./README.md#permanently-out-of-scope)** — streaming and WASM handle both for free.
- **A direct rewrite is genuinely the right answer** for a small, well-understood app whose UX is being redesigned anyway.

## Claims this project does not make

- **"Nothing else is free or open source."** False: free, open-source Swing-to-web projects exist.
- **"SB-Emulators is a clean-room implementation."** It is not, and this must never be said or attested to anyone. It was written with OpenJDK's source open as the specification — by written policy, not incidentally ([R_swing_is_truth](./CLAUDE.md#R_swing_is_truth), [R_decline_effect_only](./CLAUDE.md#R_decline_effect_only)) — and 160 of its files are shaped on a JDK class. That is why it is licensed as OpenJDK is; [`PROVENANCE.md`](./PROVENANCE.md) states it plainly. "Reimplementation" above means *implemented again on a different substrate*, which is accurate; **"independent implementation" is not**, and neither is any phrasing that implies the JDK's source was not read.
- **"Swing is dying."** Oracle's client-libraries lead stated in December 2025 that AWT/Swing *"continues to be supported and enhanced"*, JavaOne 2026 ran a session titled *"2026 and Still Swinging"*, and only `JApplet` has been removed. Swing's collapse in new Stack Overflow questions (−94.5% 2019→2025) is the site-wide post-ChatGPT collapse, not a Swing signal: `java` fell −95.5% over the identical window, and Swing's *share* of Java questions drifted slightly **up**, 2.20% → 2.72%. What the numbers do show is a staffing gap: **3.87M GitHub files containing `javax.swing`**, an actively-released modern look-and-feel (FlatLaf, 13 releases since 2024), and **14 permanent UK job ads mentioning Swing over six months, with zero contract ads**.
- **"62% of financial desktop applications still use Swing."** Untraceable — chased independently twice, and its only home is a self-inconsistent content-farm article.
- **Any memory or CPU comparison against frame streaming.** SB-Emulators' lower resource use is *expected*, not measured, and nobody has published numbers. The difference that is architectural, and needs no benchmark, is **instant client-side interaction**.
- **"The third-party add-ons cover their libraries."** They do not. The two that ship (JCalendar 1.4, JGoodies Forms 1.2.1) are free starter prototypes bounded to what one real migration needed, shipped as-is with no further development planned; each add-on's `MIGRATION.md` lists what is in and what is out, and the boundary is a compile error.
- **Any market size for Swing migration.** No analyst sizes Swing, Java desktop, or even Java modernization specifically; the circulating figures are the generic application-modernization market, and they disagree with each other by a factor of nearly two. The honest answer to *"how big is this market?"* is that nobody has measured it.

## How this was checked

SB-Emulators' coverage figure was produced by reflection over JDK 25's `java.desktop` module with `javax.swing.plaf.**` excluded, and the test count by counting `@Test` methods across the modules — both on 2026-08-19, and both worth re-running before reuse, because this repository moves.
