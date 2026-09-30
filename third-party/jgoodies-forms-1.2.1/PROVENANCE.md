# `swingbridge-emulators-jgoodies-forms-1.2.1` — provenance

**Strategy: fork.** Upstream sources are vendored into this module with rewritten imports, and the
layout engine is replaced by a CSS emitter. Contrast the sibling
[`../jcalendar-1.4/`](../jcalendar-1.4/PROVENANCE.md), which is a clean reimplementation — the two
modules are deliberately different shapes, and this file plus that one are the record of which is
which.

## Upstream

- **Project:** JGoodies Forms, by Karsten Lentzsch
- **Coordinate:** `com.jgoodies:forms:1.2.1` (published 2008-10-13)
- **Source artifact:** `com.jgoodies:forms:1.2.1:jar:sources`
- **SHA-1 of the sources jar:** `b90388ea897ed63f5125c5a4ae381abfaa8004e9` — matches the
  `.sha1` published alongside it on Maven Central

Pinned by the published artifact's checksum rather than an SCM tag: jgoodies.com is unreliable
(403s on the licence page), and the Maven Central artifact is what a migrator's own build resolves,
so it is the honest baseline for a diff.

**Why 1.2.1 specifically.** The artifactId names the upstream version because it *is* part of the
fork's identity: Forms 1.9 renamed `FormFactory` to `FormSpecs`, a materially different API surface.
A migrator on 1.2.1 cannot use a fork of 1.9. A 1.9 fork, if ever needed, is a **new module**, not a
new version of this one — which is why this module tracks no upstream release stream and needs no
maintenance lane.

## Licence: 3-clause BSD

Verified from the distribution itself on 2026-08-19, twice over:

- `LICENSE.txt` at the root of `forms-1.2.1.jar` — reproduced verbatim as this module's
  [`LICENSE`](./LICENSE).
- a `<licenses>` block naming *The BSD License* in the published
  `META-INF/maven/com.jgoodies/forms/pom.xml`.

Copyright (c) 2002-2008 JGoodies Karsten Lentzsch. All rights reserved.

**Obligations this module must honour:**

1. **Retention.** Redistributions of source must keep the copyright notice, the list of conditions
   and the disclaimer. So: `LICENSE` ships in the module, and **every forked source file keeps its
   upstream copyright header intact** — a rewritten `import` line is not licence to strip one.
2. **No endorsement.** The third clause bars using the JGoodies name to promote derived products.
   `swingbridge-emulators-jgoodies-forms` therefore describes *provenance*, and neither this module's README nor its
   javadoc may imply that JGoodies endorses, reviewed, or supports the fork.

The pom's `<licenses>` block overrides the parent's Apache-2.0 (Maven replaces rather than merges it),
and its `<resources>` block likewise overrides the parent's, so this jar ships *this* BSD `LICENSE`
and not the repository's.

## What is changed relative to upstream

**17 of upstream's 36 source files are vendored.** The subset was derived by the compiler, not
by guesswork: seed from the target app's call sites, rewrite imports, compile, and add whatever
`cannot find symbol` names until it goes green.

| change | files | why |
|---|---|---|
| package + import rewrite | all | `com.jgoodies.forms.` → `vaadinx.jgoodies.forms.`; `java.awt.Component`/`Container` → `vaadinx.awt.*`. `Dimension` / `Insets` / `Rectangle` / `Font` stay JDK — only the Component hierarchy is ported. |
| line endings normalised to LF | all | The 2008 sources are CRLF. They are modified anyway, so byte-verbatim was already off the table; `LICENSE` is kept CRLF-verbatim. |
| `layoutContainer` replaced | `FormLayout` | The pixel engine can't run server-side. Delegates to `CssEmittingLayoutManager`, and `containerCss`/`childCss` emit CSS Grid. **This is the port.** |
| `DefaultUnitConverter` / `AbstractUnitConverter` not vendored | — | They measure an L&F dialog font and call `Toolkit.getScreenResolution()`, which throws `HeadlessException` in a servlet JVM. Replaced by `CssUnitConverter` (WARNs) plus `FormCss` (the real conversion, to CSS units). |
| `PrototypeSize.maximumSize` stubbed | `PrototypeSize` | Same reason — it measured the prototype string with `FontMetrics`. |
| `LayoutStyle` pinned to Windows | `LayoutStyle` | Upstream picks Mac vs Windows from the OS name; the OS running the *servlet* is not the one rendering the form. `MacLayoutStyle` is not vendored (L&F is out of scope, R_match_swing_errors(b)); `setCurrent` still allows a deliberate choice. |
| L&F half of `FormUtils` removed | `FormUtils` | Only `assertNotNull` / `assertNotBlank` / `equals` are used. `isLafAqua` and its `UIManager` cache had no caller and no meaning here. |

**Not vendored at all** (no class file, so reaching for one is a compile error): the whole
`builder` package, `debug`, `Borders`, `ButtonBarFactory`, `ComponentFactory`,
`DefaultComponentFactory`, `MacLayoutStyle`, `AbstractUnitConverter`, `DefaultUnitConverter`.

**SB-Emulators-authored, not upstream:** `FormCss` (spec → CSS Grid) and `CssUnitConverter`.

## The dialog-unit constants, and how they were measured

Upstream converts dialog units through `DefaultUnitConverter`, which measures a real font through
`FontMetrics` and calls `Toolkit.getScreenResolution()` — neither available (or meaningful) on a
server. `FormCss` maps the units to font-relative CSS instead, which needs two constants. Both are now
**measured against a live page** rather than estimated, on 2026-08-21:

**What upstream actually does** — read from the pinned sources jar, not from memory:

- **dluX**: base unit is `computeAverageCharWidth(metrics, averageCharWidthTestString) / 4`, and
  `averageCharWidthTestString` **defaults to `"X"`** — a single capital letter, despite the field name.
  So the base unit is the advance of "X", not an average over any alphabet.
- **dluY**: base unit is `(ascent > 14 ? ascent : ascent + (15 - ascent) / 3) / 8` — the font's
  **ascent**, nudged upward for small fonts. Not the line height.

**What the browser gives** — Chromium, `testapps/jlawyer-shape/1-emulators` (Aura, so the default
`Instrument Sans` at 14px/20px), measured through both a canvas `measureText` and a `100ch` DOM probe,
which agreed to four decimals:

| quantity | value |
|---|---:|
| `width("X")` — upstream's dluX base unit | 9.633px |
| `width("0")` — CSS `1ch` | 9.317px |
| **`width("X") / width("0")`** | **1.034** |
| font ascent (`fontBoundingBoxAscent`) | 14px |
| 1 dluY upstream (14 → 14.333/8) | 1.792px |
| 1 dluY here (`em`/8) | 1.750px — **2.3%** low |

**Outcome.** `DLU_Y_PER_EM = 8.0` is confirmed: `em` substitutes for the ascent within 2.3% for this
font, so it needs no calibration factor. `DLU_X_CALIBRATION` **was 0.88 and is now 1.03**, because the
old value's rationale was wrong rather than merely imprecise: it shrank the conversion on the grounds
that `ch` measures "0" while a dluX is a quarter of the font's *average* character and "0" is wider
than average. Upstream never averages — its base unit is "X", which in this font is *wider* than "0" —
so the correction pointed the wrong way, and every dlu gap and column bound came out ~15% tight
(`RELATED_GAP_COLSPEC`, 4dlu, emitted `0.88ch` = 8.2px where upstream gives 9.63px).

**Why 1.03 and not 1.0.** The measurement rounds to 1.03, and keeping a value that is visibly *not* 1
protects the factor from being inlined away as a no-op multiply by a later reader.

**What is still open.** The constant is font-dependent by nature and CSS has no "advance of X" unit, so
a constant against `ch` is the only expressible form; for proportional sans-serif faces "X" and "0" sit
within a few percent, so a value near 1 is the right default for any font stack. What this measurement
does **not** establish is whether real forms *look* right — that wants a page of dlu-specified panels
in front of a human, and `testapps/inventory/1-emulators`' 13 form panels are the intended occasion.

## Subset

See [`README.md`](./README.md).
