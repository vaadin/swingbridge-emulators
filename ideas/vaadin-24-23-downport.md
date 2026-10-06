# Downporting SB-Emulators to Vaadin 24 (Java 17) and maybe Vaadin 23 (Java 11)

**Status:** quick skim, 2026-09-02. Not a design pass. Filed because typical enterprise
customers sit on Java 8 / 11 / 17 for years, and a Vaadin 25 / JDK 24+ floor puts SB-Emulators out of
their reach. Prerequisite for *any* of this: vaadin-blocking-dialogs' planned
[session-unlock runner](https://github.com/mvysny/vaadin-blocking-dialogs/blob/master/design/ideas/session-unlock-runner.md), a platform thread parking with the session lock released, because
Java 17 and 11 have no virtual threads at all — the loom runner cannot exist there, so what is
an *option* on JDK 21 becomes the *only* mechanism below it (and the library itself compiles for
Java 21, so a downport needs it built lower too). Graduate this file into a
`D_vaadin_floor` entry once a target is picked (or into a one-line why-not in CLAUDE.md's
JDK paragraph if we decide 25-only is final).

## The version ladder (checked 2026-09-02)

| Vaadin | latest | Java floor | servlet API | maintenance |
| --- | --- | --- | --- | --- |
| 25.2.6 | current | 21 | jakarta | free |
| 24.10.9 | current | 17 | jakarta | **free** |
| 23.6.13 | still patched | 11 | `javax.servlet` | **commercial-only** (extended maintenance) |
| 14.14.5 | still patched | 8 | `javax.servlet` | commercial-only |

Consequences before looking at a single SB-Emulators class:

- **Java 8 customers have no route.** Only Vaadin 14 runs on Java 8; a Vaadin 14 port would
  mean Polymer-era components and a commercial licence for the customer. Declare out.
- **Java 11 ⇒ Vaadin 23, which is itself a paid product now.** A customer who cannot leave
  Java 11 is already buying extended maintenance from Vaadin; SB-Emulators-on-23 is only worth building
  if such customers actually show up. Otherwise the honest answer is "SB-Emulators brings you to Java 17
  + Vaadin 24, which is free".
- **Java 17 ⇒ Vaadin 24, free and current.** This is the realistic target and the cheap one.

## Vaadin 24 (Java 17): almost free

`~/.m2` holds Flow 24.9.11 / components 24.9.10 jars, so the presence checks below are against
real class files, not memory. Nearly everything SB-Emulators touches already exists in 24.x:
`com.vaadin.flow.server.streams.*` (`DownloadHandler`, `UploadHandler` — 24.8+, used by
`BrowserFileTransfer`), `com.vaadin.flow.server.Attributes` (`AppInstance` / `AppTab`),
`NativeLabel`, `RangeInput`, `TabSheet`, `SlotUtils`, `HasTooltip`, `Scroller`, `MenuBar` /
`ContextMenu` / `SubMenu`, `Grid` DnD, `TreeGrid`, `RichTextEditor` with the HTML main value
(the `SJEditorPane` / `RteHtmlCodec` contract is 24-shaped already).

**Vaadin-side blockers, all small:**

1. **`IntegerSlider` does not exist in 24** — `vaadin-slider-flow` is a 25.x component and
   `SJSlider extends IntegerSlider` (is-a). A 24 build needs a different peer: `RangeInput`
   (present in 24.9; already `SScrollbar`'s peer, with the known no-ticks / rotate-for-vertical
   limitations SD_sscrollbar and [sjslider-vertical-via-writing-mode.md](./sjslider-vertical-via-writing-mode.md)
   discuss) or a hand-rolled `<input type=range>`. The one structural divergence in the whole
   surface, because it changes a surrogate's superclass.
2. **`Dialog.setModality(ModalityMode)` is 25.0** — 24 has `setModal(boolean)`. Five call
   sites (`vaadinx.awt.Dialog`, `BrowserFileTransfer`, `SJWindow`, `SJInternalFrame`), and
   `STRICT`/`MODELESS` maps 1:1 onto `true`/`false`. Trivial.
3. **`@NpmPackage(version = "25.2.7")` pins** on `LongField` and `VaadinRadioButton` must
   track the target's web-component line (a mismatched pin is a build-time npm conflict, not a
   runtime one).
4. **`karibu-testing-v24`** — same artefact serves 24 and 25; the Karibu line is not a blocker.
   `vaadin-boot` 13.x (sampler / testapps only) needs Java 17 — verify, else pin an older line.
5. **Aura** is only mentioned in comments and a CSS-variable fallback chain (Aura → Lumo →
   literal) in `JOptionPane`; no API dependency. Lumo is what 24 ships; the fallback already
   handles it.

**Java-side blockers (bytecode 21 → 17):**

- **The loom runner is Java-21-only by nature** (`Executors.newThreadPerTaskExecutor`,
  `Thread.Builder.OfVirtual`, `Thread.isVirtual()`): it has no Java 17 form. Hence the
  prerequisite above — the session-unlock runner, and on 17 it is the only one. This also removes
  `--add-opens` and the `VirtualThreadAwareLock` routing.
- **Pattern-matching `switch`** (Java 21): one occurrence, the peer-type dispatch in
  `SJSpinner` (`case IntegerField f ->` …). Rewrite as an `instanceof` chain.
- Everything else in `src/main` is Java 17-clean: records (19), `instanceof` patterns, switch
  expressions, text blocks, sealed `permits` (4) are all ≤ 17; the JDK-API scan found only
  Java 11-era calls (`isBlank`, `strip`, `repeat`, `lines`, `List.copyOf`) plus 7 uses of
  `Stream.toList()` (Java 16, fine for 17). No `SequencedCollection`, no `Math.clamp`, no
  `String.formatted`.
- JDK 24+ was *only* ever mandated for JEP 491 pinning (the why-not is the "JDK 24+ is
  mandatory" paragraph in [CLAUDE.md](../CLAUDE.md); a loom backport to 21 is closed as
  won't-implement). Without virtual threads that motivation evaporates — the session-unlock
  runner sidesteps the pin rather than fixing it, so it carries no JDK floor of its own.

Estimate: a **Vaadin 24 / Java 17 build is a few days of work on top of the session-unlock
runner**, with
`SJSlider`'s peer swap the only piece that needs design rather than mechanical edits.

## Vaadin 23 (Java 11): real work, and a paid target

Everything above plus:

- **`javax.servlet`, not `jakarta`.** SB-Emulators' `src/main` has zero direct servlet imports (the
  `jakarta.servlet-api` dependency is only there because `VaadinSession` references
  `HttpSessionBindingListener`), so this is a `provided`-dependency swap — but every app-side
  piece (`vaadin-boot`, Jetty line, `SwingBridgeEmulatorsBootstrap` wiring) changes flavour.
- **Bytecode 11:** records (19 declarations), `instanceof` patterns (~140), switch
  expressions / arrows, text blocks (12), `Stream.toList()` (7) all have to go. Mechanical but
  wide, and it makes the shared source tree ugly for the 24/25 builds. A syntax-desugaring
  compiler plugin (Jabel-style) covers most of it but **records need `java.lang.Record` at
  runtime** — verify before betting on that route; otherwise this forces a maintained fork or a
  source preprocessor.
- **Missing components / APIs** (23.5 component list checked via the Vaadin MCP; 23.x is
  Polymer-era Flow):
  - **`TabSheet` has no Java API in 23** (web component only from 23.3) → `SJTabbedPane`
    rebuilds on `Tabs` + a manual content switcher.
  - **No `com.vaadin.flow.server.streams`** → `BrowserFileTransfer` goes back to
    `StreamResource` + `Upload` with a `Receiver`. Medium.
  - **`NativeLabel`, `RangeInput` absent** → `Label` (fine in 23, removed in 25) and a raw
    `Input` with `type=range` (or `Element`-level). Small.
  - **`RichTextEditor` value is Delta in 23, HTML only through `asHtml()`** → the
    `SJEditorPane` / `RteHtmlCodec` contract inverts; `JTextPane` styled runs need re-plumbing.
  - **`SlotUtils`**, `TextFieldBase`-era APIs, `AbstractNumberField` generics: 23 text fields
    are `GeneratedVaadin*`-based; anything that extends them (`LongField`, the `SJFormatted*`
    family, `SJTextField`) needs an audit rather than a recompile.
  - `HasTooltip` / `Tooltip` exist only from **23.3** — floor at 23.3, not 23.0.
  - `Dialog` header/footer API is 23.1+; `Grid`, `TreeGrid`, `MenuBar`, `ContextMenu`,
    `Scroller`, `Select`, `DatePicker`, `Checkbox`, `SplitLayout`, `ProgressBar`, `Upload`,
    `VirtualList` are all present.
- **Test stack:** `karibu-testing-v23` exists (2.7.2). tab-scope: assumed downported per the
  premise of this note; it is the one dependency SB-Emulators owns.

Estimate: **weeks, plus a permanent fork-shaped maintenance cost** because of the bytecode-11
constraint. Only justified by a concrete app on a Vaadin extended-maintenance contract.

## Shape if it lands

Not a branch per Vaadin version — that duplicates the whole emulator surface for a handful of
divergences. The 24/25 delta is small enough for **one source tree with a thin compat seam**:

- A Maven profile per target selecting `vaadin.version`, `maven.compiler.release`, the npm
  pins, and which blocking-dialog runner jar is on the classpath (a 17 build carries the
  session-unlock one only).
- The three real divergences behind SB-Emulators-internal helpers: modality (`SHelper.setModal(dialog,
  boolean)`), the slider peer (`SJSlider` over a peer chosen at build time — but note this
  fights the is-a surrogate contract; `SJSlider` may have to become has-a over an `Input`, the
  one place where 24 support changes a surrogate's public shape), and the streams façade.
- CI matrix: Vaadin 25 on JDK 21/24/25 and Vaadin 24 on JDK 17 — the second lane is what keeps
  the 17-clean bytecode from silently regressing (a Java 21 pattern-switch compiles fine under
  `--release 21` and only fails in the 17 lane).

Vaadin 23 does **not** fit the seam model (servlet flavour, Delta-vs-HTML RTE, `Tabs` vs
`TabSheet`, bytecode 11) and would be a separate `swingbridge-v23` fork if ever built.

## The Spring angle (unbrainstormed, noted 2026-09-02)

The Java floor is not the only ladder a downport moves. Each Vaadin major pins a Spring line:

| Vaadin | Spring Boot | Spring Framework | servlet namespace |
| --- | --- | --- | --- |
| 25 | 4 | 7 | `jakarta` |
| 24 | 3 | 6 | `jakarta` |
| 23 | 2.7 | 5.3 | `javax` |

A Swing app already wired with Spring must first be on the Spring line its Vaadin target demands. On
Vaadin 25 that is Spring 7, which for a Spring-5 desktop app means a Spring major upgrade *before*
the import swap — the cliff that keeps Vaadin Boot (Spring kept as a plain library) the documented
escape hatch even though Spring Boot is the recommended bootstrap, per
[M1D_bootstrap_choice](../migration/1-swing-to-emulators/decisions.md#M1D_bootstrap_choice). A Vaadin 24 build lowers the cliff to
Spring 6, and a 23 build to Spring 5.3 / `javax`, where a large share of desktop Spring apps
actually sit. So a downport is a **second demand signal** for the 24 target, independent of the
Java-17 one, and the only thing that would make a Spring-5 app's Spring Boot route feasible at all.
Nothing beyond this observation has been thought through — whether the apps that need it exist
is the same open question as below.

## Open questions

- Is there demand for 24 at all, or do migrators arrive on Java 21 anyway? One conversation with
  a real migrator settles this cheaper than the build does. Ask the Spring question in the same
  conversation (see *The Spring angle*): "which Spring line are you on" decides whether 24 buys
  them anything.
- The session-unlock runner's own open questions (Karibu determinism, the modal gap, the scale
  budget) are *this* idea's open questions too; a 24 target cannot ship until they close.
- Does the `SJSlider` is-a → has-a change ripple into the `JSlider` emulator's
  R_leaf_peer_lockdown status? Check before committing to the seam.
- Third-party add-ons (`swingbridge-emulators-jcalendar-1.4`, `swingbridge-emulators-jgoodies-forms-1.2.1`) were not skimmed;
  they sit on `:emulators` and should inherit the target, but their own Vaadin imports need the
  same one-pass check.
