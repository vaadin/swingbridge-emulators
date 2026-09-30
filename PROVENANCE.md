# SwingBridge Emulators — provenance

*Links in this file are absolute because it also travels inside every jar's `META-INF/`, where a
relative link has nothing to point at.*

**Strategy: derivative work of OpenJDK, licensed as OpenJDK — in the trees the derivation reaches.**
SB-Emulators reproduces the `javax.swing` / `java.awt` API on Vaadin, and the emulator layer —
`:emulators` and `:emulators-printing` — was built with OpenJDK's own source open as the
specification. It is therefore licensed **GNU General Public License version 2 only, with the
"Classpath" exception** — the same licence as OpenJDK itself. SPDX:
`GPL-2.0-only WITH Classpath-exception-2.0`. The full text is in [`LICENSE`](https://github.com/vaadin/swingbridge-emulators/blob/main/LICENSE).

**That is the narrow lane, not the default.** Everything else in the repository — every other
module, and the docs, guides, skills and build files — carries no JDK expression and is
**Apache-2.0** ([`LICENSE-APACHE-2.0`](https://github.com/vaadin/swingbridge-emulators/blob/main/LICENSE-APACHE-2.0)), which is also what a surface nobody
has mapped yet falls to. The exceptions are three trees we did not write and the two `third-party/`
add-ons, which take the licence of the upstream library whose API each reproduces, per
[M1D_addon_upstream_licence](https://github.com/vaadin/swingbridge-emulators/blob/main/migration/1-swing-to-emulators/decisions.md#M1D_addon_upstream_licence)
— [`jcalendar-1.4`](https://github.com/vaadin/swingbridge-emulators/blob/main/third-party/jcalendar-1.4/PROVENANCE.md) is **LGPL-2.1** (a clean
reimplementation that takes upstream's terms anyway) and
[`jgoodies-forms-1.2.1`](https://github.com/vaadin/swingbridge-emulators/blob/main/third-party/jgoodies-forms-1.2.1/PROVENANCE.md) is **BSD** (a fork). Each
of those two has its own `LICENSE` and its own `PROVENANCE.md`; [§ Lanes](#lanes) below is the map
for everything else, and this file is the record for SB-Emulators' own code.

Rationale for the licence choice and the alternatives rejected:
[`D_gplv2_ce_relicense`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_gplv2_ce_relicense). Why the map has lanes at
all, and which module may be in which:
[`D_licence_lanes`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_licence_lanes). The rule that carries both forward in
day-to-day work: [`R_gpl_provenance`](https://github.com/vaadin/swingbridge-emulators/blob/main/CLAUDE.md#R_gpl_provenance).

## How the code was made

Three sentences, because the honest answer is short:

1. **API shape by reflection.** The `:generator` module reflects over a whitelisted JDK class and
   emits a `vaadinx.*` skeleton whose every method forwards to `EHelper.onUnimplemented`.
2. **Behaviour by reading OpenJDK's source as the specification** — not incidentally, but *by
   written policy*: `R_swing_is_truth` ("replicate its original behaviour as closely as possible"),
   `R_decline_effect_only` ("the JDK's method body is the specification, not a judgement about what
   seems reasonable"), `R_infra_not_surface` ("copy code from Swing which calls it").
3. **Much of it LLM-assisted**, against those same sources.

**SB-Emulators is not clean-room, is not an independent implementation, and must never be described
as either.** 160 of its source files are named after and shaped on a JDK class, and a similarity
measurement on 2026-09-04 found roughly 14 of them to be OpenJDK source with lines deleted. The
numbers are in [`D_gplv2_ce_relicense`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_gplv2_ce_measurement) (measured on
the tree of that day); they are not restated here, so that there is one copy.

This is not a confession — reading the specification's source *is* the method, and the licence we
adopted is the one that makes it fine. What would not be fine is claiming otherwise.

## Upstream

- **Project:** [OpenJDK](https://github.com/openjdk/jdk)
- **Tag:** `jdk-25-ga` — the reference every Oracle header in this repository was copied from. A
  vendor build is *not* the reference: JetBrains Runtime 25.0.4's `src.zip` differs from
  `jdk-25-ga` on 4 of the 160 files, in both directions (`java.awt.GraphicsEnvironment` is
  `1997, 2021` there against `1997, 2024` at the tag; `javax.swing.border.LineBorder` is
  `1997, 2025` there against `1997, 2023`). Cite the tag.
- **Modules:** `java.desktop` (`java.awt.*`, `javax.swing.*`), plus `java.base` for
  `java.util.Calendar` / `java.util.GregorianCalendar` / `java.text.SimpleDateFormat`.
- **One-time snapshot, not tracked.** SB-Emulators does not merge upstream changes, so there is no
  upstream-merge bookkeeping to keep. GPLv2 §2(a) marking is therefore the per-file Vaadin year, not
  a running changelog.

## <a id="lanes"></a>Lanes: which tree is under which licence

**This table is the map; it is authoritative and exhaustive.** A path's licence is what this table
says, and a per-file header wins over the table. Every tracked surface has a row, so the last row —
the residual, **Apache-2.0** — is a net for the file nobody has mapped yet, not a place to put a
surface on purpose: a new top-level surface gets a row of its own.

| path | licence | header | why |
|---|---|---|---|
| `emulators/src/`, `emulators-printing/src/` | **GPLv2+CE** | [`HDR_jdk_derived`](#HDR_jdk_derived) where a JDK class corresponds, [`HDR_vaadin_gpl`](#HDR_vaadin_gpl) elsewhere | derivative work of OpenJDK — [`D_gplv2_ce_relicense`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_gplv2_ce_relicense). The two modules' *sources*, main and test: what the derivation reaches and what their jars are built from |
| `surrogates/`, `emulators-spring/`, `sampler/`, `migration-annotations/`, `migration-guardrails/`, `migration-tool/`, `zip-distro/`, `generator/`, `testapps/crud/` | **Apache-2.0** | [`HDR_vaadin_apache`](#HDR_vaadin_apache) | Vaadin-authored, carrying no JDK expression — [`D_licence_lanes`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_licence_lanes). Whole trees, docs included — so the kit's own README, `kit-CLAUDE.md` and skills (under `zip-distro/src/main/kit/`) are here. **One per-file exception:** in a testapp's migrated stage (`testapps/crud/1-emulators/`, any stage but `swing/`), a file the migration copied from a seed keeps the seed's [`HDR_vaadin_0bsd`](#HDR_vaadin_0bsd), as it would in a migrator's app |
| `third-party/jcalendar-1.4/` | **LGPL-2.1** | [`HDR_vaadin_lgpl`](#HDR_vaadin_lgpl) | Vaadin's code under the licence of the library whose API it reproduces. Whole tree: its `MIGRATION.md` ships inside the jar |
| `guides/1-swing-to-emulators/seed/` | **0BSD** | [`HDR_vaadin_0bsd`](#HDR_vaadin_0bsd) on a `.java` file; the tree's own `LICENSE` for the rest | the host-app templates a migrator copies into their own app, so they carry no condition that would follow them there — [`D_licence_lanes`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_licence_lanes). **Wins over the `guides/` docs row it sits in** |
| `third-party/jgoodies-forms-1.2.1/`, `testapps/inventory/`, `testapps/jlawyer-shape/` | **upstream's** | none of ours — [§ Trees we do not touch](#not-ours) | we did not write it (or, for `jlawyer-shape`, it contains a work whose licence governs the whole) |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` — at the root and in every testapp stage | **upstream's** — Apache-2.0, the ASF's | the ASF's own, kept | the Maven Wrapper, generated rather than written. **Wins over the tree row it sits in**, as a per-file header does: the copies under `testapps/crud/` are the ASF's, not Vaadin's, and the ones under `testapps/inventory/` were added by us (upstream shipped none) and are still the ASF's rather than upstream's |
| **the docs** — root `*.md` (`README.md`, `CLAUDE.md` ≡ `AGENTS.md`, `COMPARISON.md`, `DEVELOPING.md`, `DECISION-ID-MAP.md`, this file), the GPL modules' own docs (`emulators/README.md`, `emulators/architecture.md`, `emulators/decisions.md`, `emulators-printing/README.md`), `guides/`, `migration/`, `ideas/` (its probe code included), `testapps/CLAUDE.md` | **Apache-2.0** | none — Markdown carries no header, and `ideas/` is exempt from the header test | Vaadin-authored prose. What it *quotes* is not ours to license — see the first consequence below |
| **the agent, CI and build surface** — `.claude/skills/`, `.github/`, `.gitattributes`, `.gitignore`, the root `pom.xml`, `emulators/pom.xml`, `emulators-printing/pom.xml` | **Apache-2.0** | none | configuration Vaadin wrote. A pom's `<licenses>` block states its **artifact's** licence, not the pom file's own — which is how the two GPL modules' poms declare GPLv2+CE while being Apache-2.0 files |
| `LICENSE`, `LICENSE-APACHE-2.0` | **each its own terms** | — | licence texts, verbatim; their authors' (the FSF with Oracle's preamble, the ASF), ours to ship and not to license |
| anything not named above | **Apache-2.0** | [`HDR_vaadin_apache`](#HDR_vaadin_apache) on a `.java` file | the residual — [`D_licence_lanes`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_licence_lanes) |

Four consequences, because they are what a reader checks:

- **Third-party text quoted on an Apache-2.0 row remains under its own terms**, and quoting it
  relicenses nothing: Oracle's notice and the JDK fragments in the passages of this file, JDK source
  and javadoc quoted in the decision logs and guides, upstream licence grants recorded verbatim.
  This file would be Apache-2.0 even without its row, for a reason of its own: `PROVENANCE.md`
  travels inside *every* jar's `META-INF/`, the GPL ones included, so it cannot sit on a lane
  narrower than the widest artifact it ships into.
- **An Apache-2.0 jar carries no GPL text and no Oracle notice.** Every module on the Apache row
  ships [`LICENSE-APACHE-2.0`](https://github.com/vaadin/swingbridge-emulators/blob/main/LICENSE-APACHE-2.0) in its `META-INF/` rather than
  [`LICENSE`](https://github.com/vaadin/swingbridge-emulators/blob/main/LICENSE) and states Apache-2.0 in its pom, inheriting both from the parent pom,
  whose default they are — `:emulators`, `:emulators-printing` and the two add-ons override it — and
  `LicenseHeaderTest` fails the build on a Classpath-exception header anywhere on that row. The
  test names the GPL row and treats the rest of our code as this one, so a new module is
  Apache-2.0 in its pom, its headers and `JdkSimilarityTest`'s measurement at once, and joins the
  GPL row only on evidence.
  **That holds for the jar and its `-sources` twin, not for the `-javadoc` one.** Every
  `-javadoc` jar, on every lane, carries a `legal/` folder the javadoc tool writes itself —
  OpenJDK's GPLv2+CE `LICENSE`, byte-identical to our root one, beside the notices for jQuery and
  the DejaVu fonts — governing the scripts, stylesheet and fonts the tool generated into the jar,
  as it does in every javadoc jar built on a modern JDK. It is the tool's text about the tool's
  output and states nothing about our code; stripping it would ship those files without their
  licence, so it stays. A `-javadoc` jar carries none of *our* licence texts either way.
  (`testapps/crud` has standalone poms and no jar to publish; its stage-2 pom is the template a
  migrator copies, so it states no licence — in the kit, the root `LICENSE` covers it instead.)
- **The gate is the jar, not the file.** A GPL-derived file inside an Apache-2.0 jar is not a
  boundary a consumer can see, which is why all eleven of `:surrogates`' `HDR_jdk_derived` files had
  to clear ([§ The exceptions](#exceptions)) before the module could move, and why a future one
  arriving there is a build failure rather than a footnote. A header is only what a file *says*,
  so `JdkSimilarityTest` also measures what those trees *contain* against the JDK's own
  sources on every build, and fails on a contiguous run of JDK method bodies or a copied JDK
  comment ([`D_similarity_gate`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_similarity_gate)).
- **The split composes in both directions, because all of it is linking.** `:emulators` (GPLv2+CE)
  depends on `:surrogates` (Apache-2.0) and on the third-party vaadin-blocking-dialogs (Apache-2.0);
  `:emulators-spring` (Apache-2.0) depends on `:emulators`. Linking is what the Classpath Exception
  exists to permit, and Vaadin holds the copyright of every module involved. **What puts a module on the GPL lane is expression, not coupling**: JDK
  expression (an Oracle notice owed), or emulator source copied in, forked or shipped modified.
  Calling or subclassing the API is neither — if it were, extending `javax.swing.JButton` would
  make an app derived too, and the exception would grant nothing. `:sampler` is the worked case:
  433 `vaadinx` imports and 50 classes extending emulator types, and on the permissive lane.
  `D_licence_lanes` carries the argument and the earlier, stricter rule it retracts.

## <a id="headers"></a>Headers: which file carries which

**Five header texts, one per lane**, reproduced verbatim below, plus the trees where we write no
header at all. `vaadinx.LicenseHeaderTest` gates the whole repository against this section — if the
test fails, this file is the specification it is quoting.

Each is named by a slug rather than by a letter, because the letters were a lookup table whose
ordering was historical: the axes are *who wrote the file* × *which lane it is in*, and
`HDR_jdk_derived` says out loud the thing that matters most — a JDK-derived file cannot be in the
Apache lane. The prefix is `HDR_` and not `H_`, which belongs to the migration hazards.

| slug | what it is | lane |
|---|---|---|
| [`HDR_jdk_derived`](#HDR_jdk_derived) | the JDK counterpart's own Oracle block byte-for-byte, then Vaadin's derived-and-modified block | GPLv2+CE |
| [`HDR_vaadin_gpl`](#HDR_vaadin_gpl) | Vaadin's copyright, GPLv2 with the Classpath exception inline | GPLv2+CE |
| [`HDR_vaadin_apache`](#HDR_vaadin_apache) | Vaadin's copyright, Apache-2.0 | Apache-2.0 |
| [`HDR_vaadin_lgpl`](#HDR_vaadin_lgpl) | Vaadin's copyright under upstream's licence | LGPL-2.1 |
| [`HDR_vaadin_0bsd`](#HDR_vaadin_0bsd) | Vaadin's copyright, then 0BSD's unconditional grant | 0BSD |

**The licence identifier cannot carry the distinction that matters most**: `HDR_jdk_derived` and
`HDR_vaadin_gpl` have byte-identical SPDX lines, and what separates them is Oracle's block — the
false-attribution direction, which is the one that gets missed.

### <a id="HDR_jdk_derived"></a>HDR_jdk_derived — JDK-derived files

**Two comment blocks, in this order.**

Block 1 is **the JDK counterpart's own Oracle header, byte-for-byte from `jdk-25-ga`**. Its
`DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER` line is an instruction we obey: the
block is never edited, never reflowed, and above all **never stamped from a template**. The years
differ per file — `javax.swing.table.TableColumn` is `1997, 2017`, `java.awt.CheckboxGroup` is
`1995, 2021`, `javax.swing.JButton` is `1997, 2021`, `java.util.Calendar` is `1996, 2024` — across
42 distinct year pairs over the 160 files. Copy it from the counterpart, one file at a time.

Block 2 is Vaadin's own notice. It does three things: it is GPLv2 §2(a)'s "prominent notice" that
the file was changed and when; it names what the file was derived *from*, so a reader can diff; and
it **extends** the Classpath exception to Vaadin's modifications, using the words `LICENSE` itself
supplies for that act ("If you modify this library, you may extend this exception to your version").

```java
/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.table.TableColumn
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */
```

Per file, two things vary: block 1 entirely, and in block 2 the fully-qualified **JDK** class name
(`javax.swing.table.TableColumn`, not the `vaadinx` name; `java.util.Calendar` for a `java.base`
class) plus the year of modification.

### <a id="HDR_vaadin_gpl"></a>HDR_vaadin_gpl — Vaadin-authored files in the GPL lane

The GNU Classpath project's own header shape: Vaadin's copyright, GPLv2-only, and the exception text
**inline**, so the grant stands on its own rather than depending on the Oracle-specific preamble in
`LICENSE` (which by its own terms reaches only files where *Oracle* included the designation).

```java
/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */
```

`Copyright 2000-2026 Vaadin Ltd.` is Vaadin's house line, byte-identical to every file in
`vaadin/flow` and `vaadin/flow-components`, so the company-wide annual year bump is the same
one-line change here as everywhere else. The end year is the file's last-modified year and doubles
as GPLv2 §2(a)'s date.

### <a id="HDR_vaadin_apache"></a>HDR_vaadin_apache — Vaadin-authored files in the Apache lane

Every `.java` file we author outside the two GPL modules and `jcalendar-1.4`, per
[§ Lanes](#lanes). Vaadin's house copyright line
again — the same line, so the annual year sweep does not fork — then Apache-2.0's own recommended
notice, which names the licence by **URL** rather than by file. That is the notice's own shape and is
left alone; [`LICENSE-APACHE-2.0`](https://github.com/vaadin/swingbridge-emulators/blob/main/LICENSE-APACHE-2.0) is that same text, and is what those
modules ship into their jars.

```java
/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
```

Everything but line 3 is `vaadin/flow`'s own block byte-for-byte — measured, not recalled: all 826
files in `flow-server-25.2.8-sources.jar` carry it identically, and **not one of them carries an
SPDX line**. Ours does, in the house position directly under the copyright, because
`LicenseHeaderTest` rule 1 asserts an SPDX identifier on every authored file and a lane that opted
out would make that rule conditional in shape rather than in value. Adding the line is the smaller
divergence, and it keeps the file machine-readable.

**No Oracle notice may appear in this lane, and no Classpath-exception text either** — the first
would be a false attribution, the second would state GPL terms over an Apache-2.0 jar. Rule 6 fails
the build on both, which is the same shape as rule 5's guard over the LGPL tree.

### <a id="HDR_vaadin_0bsd"></a>HDR_vaadin_0bsd — the host-app seeds, which a migrator copies into their own app

`guides/1-swing-to-emulators/seed/**` is the one tree whose files are *meant* to leave SB-Emulators
and become part of someone else's code: the migrator copies a seed into their app and edits it
there. Any condition in its header would follow it into their app — Apache-2.0's notice and
`NOTICE` terms, say — and they would have to carry Vaadin's licence on files that are, by then,
theirs. So the seeds carry **0BSD**, a grant with no conditions at all: use, copy, modify and
redistribute, with no notice to keep. The house copyright line stays, so the year sweep is the
same one-line change here; the sentence under it says in plain words what the grant permits.

```java
/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: 0BSD
 *
 * A seed file: copy it into your app and license the result as you choose. This
 * notice need not be kept.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
```

The poms, properties files and resources in the tree carry no header; `seed/LICENSE` is their
grant, with the same text. **Neither the Apache notice nor any GPL or Oracle text may appear in the
tree**, and `LicenseHeaderTest` rule 7 fails the build on both, as well as on a missing
`seed/LICENSE`. `JdkSimilarityTest` measures the tree like the Apache lane: JDK expression in a
0BSD file would be GPL code under terms that do not carry it.

**The header travels with the seed into a testapp's migrated stage.** A migration run copies the
seeds into the app it migrates and keeps their header, exactly as a migrator does, and the harness
copies that output back over `testapps/<app>/1-emulators/`. Those files stay 0BSD there — rewriting
them to `HDR_vaadin_apache` would be a hand-edit in a tree that is migration output, undone by the
next run. `LicenseHeaderTest` admits `HDR_vaadin_0bsd` on any stage under `testapps/<app>/` but
`swing/`, and holds such a file to the whole 0BSD header, as rule 7 does the seed tree.

### <a id="not-ours"></a>Trees we do not touch

`testapps/inventory/**`, `testapps/jlawyer-shape/**` and `third-party/jgoodies-forms-1.2.1/**` keep
whatever header upstream gave them, **including the files that have none**. Adding a Vaadin header
to a file Vaadin did not author is a false claim, in exactly the way that stamping Oracle's header
onto a Vaadin file would be. `ideas/**` is throwaway probe code that ships in nothing.

### <a id="HDR_vaadin_lgpl"></a>HDR_vaadin_lgpl — `third-party/jcalendar-1.4/**`, Vaadin's code under upstream's licence

Every file there is Vaadin-authored — so it carries Vaadin's copyright line, like `HDR_vaadin_gpl`
and `HDR_vaadin_apache` — but the module reproduces JCalendar 1.4's API and takes **LGPL-2.1**,
upstream's licence, per
[M1D_addon_upstream_licence](https://github.com/vaadin/swingbridge-emulators/blob/main/migration/1-swing-to-emulators/decisions.md#M1D_addon_upstream_licence).
Its header is therefore its own: `SPDX-License-Identifier: LGPL-2.1-only` and the LGPL's own notice,
pointing at that module's `LICENSE` rather than this one. The module's
[`PROVENANCE.md`](https://github.com/vaadin/swingbridge-emulators/blob/main/third-party/jcalendar-1.4/PROVENANCE.md) is its record.

**`HDR_vaadin_gpl` on a file in that tree is a failure, not an untidiness**, and `LicenseHeaderTest`
rule 5 says so in both directions: LGPL-2.1 §3 permits relicensing an LGPL work to the **ordinary**
GPLv2, but not to GPLv2 + Classpath Exception, because the exception grants a linking permission over code
that would not be ours to grant it over. Copy a header there from a sibling file in the same module,
never from another module.

Two shortcuts were considered here and rejected:

- **JetBrains Runtime's**, which puts `Copyright 2025 JetBrains s.r.o.` above Oracle's boilerplate
  *unchanged* — including the sentence "Oracle designates this particular file as subject to the
  Classpath exception". Oracle designated nothing about a file Oracle did not write, and the
  Classpath section of `LICENSE` says so itself: it applies "only where Oracle has expressly
  included in the particular source file's header" those words. Vaadin's own files need Vaadin's
  own grant, which is what `HDR_vaadin_gpl` is.
- **`vaadin/feature-pack-internal`'s**, which stamped `Copyright (c) 1997, 2021` on all 13 of its
  JDK-derived files regardless of the counterpart's real years (`java.awt.Dimension` is
  `1995, 2021`). That is the template mistake the per-file copy rule exists to prevent, and
  `LicenseHeaderTest` rule 4 is the mechanical version of the prohibition.

## <a id="which-files"></a>Which files are JDK-derived

**A rule, not a list**, so that nothing here can rot:

> A file under `emulators/src/main/java` or `emulators-printing/src/main/java` whose class name maps
> onto a real JDK class carries **`HDR_jdk_derived`**, naming that class. Everything else
> SB-Emulators wrote carries the header of its lane: **`HDR_vaadin_gpl`** in the rest of those two
> modules, **`HDR_vaadin_lgpl`** under `third-party/jcalendar-1.4/`, **`HDR_vaadin_0bsd`** under
> `guides/1-swing-to-emulators/seed/` (and on a seed file a migration carried into a testapp's
> migrated stage), and **`HDR_vaadin_apache`** everywhere else.

The mapping is the one `vaadinx.audit.JdkCounterpart` computes — `vaadinx.awt` → `java.awt`,
`vaadinx.swing` → `javax.swing`, `vaadinx.util` → `java.util`, `vaadinx.text` → `java.text`,
longest prefix first — which is the same mapping the return-value audit has always used. Today it
resolves for **160** files (159 in `:emulators` plus `vaadinx.awt.print.PrinterJob`) and not for
**41** (`EHelper`, the `FormattedFieldStrategy` family, the app bootstrap, the `vaadinx.util.prefs`
implementations, `CssEmittingLayoutManager`, `package-info`, …). Add an emulator and it is
`HDR_jdk_derived` automatically; the test will say so before the build goes green.

### <a id="exceptions"></a>The exceptions: derivation the name does not show

The name rule is a **floor**, because the header follows the code, not the file name. A file with no
JDK name counterpart still carries `HDR_jdk_derived` if JDK expression was carried into it — a
constant table copied wholesale, or a method body translated rather than written against the
javadoc. `LicenseHeaderTest` rule 3 holds the same list, so it and this section cannot drift apart.

**The list is empty**, and that is the claim rather than an omission: every `HDR_jdk_derived` file
in the repo today is one the name rule finds, and every Oracle notice outside `:emulators` /
`:emulators-printing` is a build failure. Eleven `:surrogates` files were on it and all eleven left
the same way — not by being re-judged, but by having the carried-over expression removed. The two
departures are below, because how a file leaves is the part that has to stay checkable, and because
emptying this list is what let `:surrogates` take the Apache lane ([§ Lanes](#lanes)): the gate
bites at the jar, so one entry back here would take the whole module's licence with it.

**Ten `S*Event` classes left on their constant tables.** `SKeyEvent`, `SInputEvent`,
`SComponentEvent`, `SContainerEvent`, `SFocusEvent`, `SHierarchyEvent`, `SMouseEvent`,
`SWindowEvent`, `SAncestorEvent` and `SInternalFrameEvent` each claimed `HDR_jdk_derived` on a
**transcribed constant table** — for `SKeyEvent`, 200 of 203 field lines, in OpenJDK's own
declaration order and with its own inconsistent hex casing.

All ten tables are now **emitted by `DelegatedConstantsTest`** from the JDK's public runtime API:
the generator enumerates names by reflection, sorts them itself, and writes each constant as
`public static final int VK_A = java.awt.event.KeyEvent.VK_A;`. So **no constant value appears
anywhere in `:surrogates` source**, and the arrangement is ours rather than the JDK's. Every target
was first verified to declare exactly its counterpart's constant set, so emitting the counterpart's
set widens no public API.

**That is a claim about the source text and must never be made about the jar.** A delegating field
is still a *constant variable* (JLS §4.12.4), so the API contract is untouched — verified with
`javac` + `javap -c`: a migrator's `case SKeyEvent.VK_A:` still compiles, a consumer still compiles
down to `bipush 65`, and `java.awt.event.KeyEvent` is never loaded at runtime, the reference being
erased at compile time. The flip side is that the 189 values are inlined into our class files either
way. **Defeating that inlining is a dead end and should not be attempted**: `Integer.valueOf(...)`
still folds to `bipush` inside `<clinit>`, so the value stays in the bytecode and the only casualty
is constant-ness, which would break every migrator's `case` label.

**What remains in those ten files, measured rather than assumed, is API shape and plumbing.** The
per-file check was to read the matched lines, not the counts: the only `paramString()` line that
matches the JDK in any of them is its **signature**, because ours are switch *expressions* against
the JDK's classic `switch`/`break`. The rest is `super(source, id);`, `this.child = child;`,
`return child;` and constructor parameter lists — lines with no other way to write them, and the
same category already kept off this list for the 20 listener and adapter siblings. A
`javax.swing.event`-style data holder is mostly plumbing by nature, so its *percentage* of matched
lines stays high while the matched lines themselves carry no expression; the percentage is the wrong
instrument for a 20-line class and the line list is the right one.

None of these files is clean-room, and nothing here says otherwise: the project is derivative of
OpenJDK throughout and the people who work on it have read its source. The narrow and checkable
claim is that **these files' constants were regenerated from the public runtime API, with no
OpenJDK source consulted for them** — and `DelegatedConstantsTest` re-checks it on every build, so
it cannot quietly stop being true.

**`SJTextArea` left on a rewrite, and it is a different case from all ten.** It was a **body** port
(37 of 91 body lines) rather than a constant table, so no generator reached it, and **it admitted
nothing** — the self-admission grep that built the original list could not see it. It was caught by
measuring line overlap against `src.zip` directly, which is why any future rebuild of this list must
be a measurement rather than a grep; `LicenseHeaderTest`'s `DERIVED_WITHOUT_NAME` carries the same
warning.

What was carried over was `append` / `insert` / `replaceRange` as three whole contiguous methods,
each with its own `getDocument()`, bounds check, `insertString` and `catch (BadLocationException
e)`. They are now **one** private `spliceDocument(method, str, start, end)` with the three public
methods as bounds checks in front of it, because an append is a splice of the empty span at the end
and an insert a splice of the empty span at `pos` — a shape `javax.swing.JTextArea` does not have.
Re-measured with the same script, the file went from 64 of 124 code lines (37 body) to **54 of 121
(27 body)**, and every one of the ten lines that left came from those three methods.

The residue was then **read rather than scored**, the same per-line check the ten event files got,
and it is API shape with nothing left over: the five-constructor delegation chain, the four
`rows < 0` / `columns < 0` guards, `if (doc != null) { setDocument(doc); }`, `this.rows = rows;`,
`return columns;`, three `firePropertyChange("lineWrap", old, wrap)` lines, and one
`} catch (BadLocationException e) {`. **That is `SJTextField`'s profile reached from the other
direction** — 27 matched body lines each, the same constructor chain, the same validation — and
`SJTextField` was examined and kept off this list on exactly those grounds (below). The four guards
keep the JDK's own four message strings deliberately, per the note on verbatim messages at the end
of this section; folding them into one helper would have unified two message styles the JDK
distinguishes, to move a number.

One difference from the ten is worth stating plainly: a generator re-derives its output on every
build, a rewrite does not. The measurement is what is checkable here, which is why the before/after
numbers are in this section and not only in a commit message.

**Deliberately *not* on the list**, though they sit in the same package and use the same "port of"
phrasing: the 20 listener and adapter types (`SKeyListener`, `SMouseAdapter`, `SWindowListener`, …).
Those are method signatures and nothing else — API shape, which is what this entire project copies
by design and is the one thing the name rule already accounts for everywhere else. Attributing
Oracle's copyright over a file containing no Oracle expression is the same false statement as the
JetBrains shortcut, just pointed the other way, so the line is drawn at carried-over *expression*.

Six files were examined for this list and **kept off it** — so each carries its own lane's Vaadin
header and no Oracle notice:

- `com.vaadin.swingbridge.surrogates.SJTextField` — the highest-scoring file not on the list (27 of
  87 body lines against `javax.swing.JTextField`), and the decision rests on reading it, not on the
  number. Every body the JDK spends effort on is written differently here, and visibly so:
  `fireActionPerformed` is six lines of `getListeners(...)` for-each where the JDK's is twenty of
  pairwise `getListenerList()` iteration plus `EventQueue` modifier archaeology;
  `configurePropertiesFromAction` writes out what the JDK delegates to package-private
  `AbstractAction` statics; `TextFieldActionPropertyChangeListener` is a strong-ref
  `PropertyChangeListener` where the JDK's extends a `WeakReference`-based
  `ActionPropertyChangeListener<JTextField>` with `shouldReconfigure` dispatch; `setColumns` drops
  the JDK's `oldVal` / `invalidate()`. What *does* match has no contiguous run over four lines, and
  is the five-constructor delegation chain (forced by the API), the `columns < 0` validation, and
  the `listenerList.add/remove/getListeners(ActionListener.class)` idiom this whole module uses.
  That is the "API shape alone" side of the line, not a translated body — and it is the line
  `SJTextArea` sat on the wrong side of until its three text-mutation methods were rewritten, after
  which the two files' residues are the same thing. The shared inner-class *name* is not a tell
  either: the module applies a `<Component>ActionPropertyChangeListener` convention to
  `SJPasswordField` too, and `javax.swing.JPasswordField` has no such class.

- `vaadinx.util.prefs.VaadinPreferences`, `BridgedPreferences`, `JvmLocalPreferences` — they
  *extend* `java.util.prefs.AbstractPreferences` and implement only its `*Spi` hooks, with bodies
  (a localStorage cache, a session bridge, a `ConcurrentHashMap`) that are SB-Emulators' own.
  Extending a JDK class is linking, which is precisely what the Classpath exception is for;
  `VaadinPreferences`' javadoc line "all come from the JDK" describes *inherited* behaviour, not
  copied text.
- `vaadinx.swing.LookAndFeelDefaults` — Metal's colour values are measurable facts about the JDK's
  rendering (and were measured, per `D_theme_is_lookandfeel`), sitting in a hand-built table. No
  JDK method body.
- `vaadinx.awt.CssEmittingLayoutManager` — an SB-Emulators invention (`containerCss` / `childCss`)
  with no JDK counterpart at all.

**Verbatim JDK exception messages do not earn `HDR_jdk_derived`, and they are everywhere.**
`SJTextField` throws `"columns less than zero."` exactly as `javax.swing.JTextField` does, period
included — and so do 29 `:surrogates` files sharing 41 multi-word literals with the JDK (`"dataModel must be non
null"`, `"illegal scrollbar orientation"`, `"unknown type"`, …), including files whose code sits at
the measured noise floor. That is `R_match_swing_errors` working as intended: matching Swing's error
handling reasonably extends to the text, since a migrated app may be reading it. Worth stating
because the similarity measurement **cannot see this axis at all** — it strips string-literal
contents before comparing — so a file's score says nothing about it either way.

One unrelated note, recorded here because a licensing sweep is where it surfaced:
`com.vaadin.swingbridge.surrogates.BrowserTimeZone` is a slimmed port of
[karibu-tools](https://github.com/mvysny/karibu-tools)' `BrowserTimeZone.kt` (Apache-2.0), not of
anything in the JDK. **The Apache lane makes it ordinary**: the file now sits in an Apache-2.0
module, which is the one direction that always composes, so the argument this note used to rest on —
that karibu-tools' copyright holder is this repository's author, and may license the same work
twice — no longer has to carry it, though it is still true. A port of third-party code into the
*GPL* lane remains a decision rather than a commit, per `R_gpl_provenance` rule 5.

## What the Classpath Exception means if you are migrating an app

Factual, not advice; your own counsel is your own.

Your application **links** SB-Emulators exactly as it links the JDK: you compile against
`vaadinx.swing.*` and ship your app alongside our jars. The Classpath exception gives permission "to
link this library with independent modules to produce an executable, **regardless of the license
terms of these independent modules**, and to copy and distribute the resulting executable under
terms of your choice". Your application's source is an independent module. So linking against
SB-Emulators places no licensing obligation on your own code — the same position your app is
already in with respect to OpenJDK, whose licence this is.

What GPLv2 does reach is **SB-Emulators itself**: if you modify our files and distribute the result,
those modifications are GPLv2, and the notice rules above apply to them.

**And it does not reach all of SB-Emulators.** [§ Lanes](#lanes) is the per-artifact answer, and the
part that changes the sentence above is every published artifact but `swingbridge-emulators`,
`swingbridge-emulators-printing` and the two add-ons — `swingbridge-surrogates`,
`swingbridge-emulators-spring`, `swingbridge-migration-annotations`,
`swingbridge-migration-guardrails` and `swingbridge-migration-tool` — which are **Apache-2.0**: modifying and redistributing *those* carries no
copyleft obligation at all. A stage-3 migrator, whose views are being rewritten onto the `S*`
classes, is linking the permissive artifact; the copyleft one is the Swing-API-reproducing core,
exactly as it is in OpenJDK. Each jar states its own licence in its pom and ships the text it is
under at `META-INF/`.

## Trademarks

"Java" and "Swing" are trademarks of Oracle and/or its affiliates. SB-Emulators uses them
**descriptively**, to say what the software does — it emulates the Swing API — and claims no
affiliation with, sponsorship by, or endorsement from Oracle. It is **not** a certified Java
implementation and makes no claim of Java compatibility or conformance. The project's naming is
decided separately, in [`D_naming_scheme`](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_naming_scheme), and is not
affected by the licence change.
