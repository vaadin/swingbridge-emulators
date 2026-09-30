# `testapps/jlawyer-shape` — provenance

> Links into the rest of the repository are absolute: this file travels inside the distribution kit
> beside the sources, because the kit redistributes an AGPL-3.0 work and this is where that is
> stated plainly. The kit ships neither the decision logs nor the skills it cites.

**This app is ours, around a slice that is not.** The case-management shell — tree, tabs, document
list, notes pane, properties dialog and their in-memory backend — was hand-written for this project.
Four files were taken **verbatim from [j-lawyer](https://github.com/jlawyerorg/j-lawyer-org), which
is AGPL-3.0**, and they carry that licence with them. This file is the record of which is which.

Not an *adopted* app in [`testapps/CLAUDE.md`](https://github.com/vaadin/swingbridge-emulators/blob/main/testapps/CLAUDE.md)'s
sense — it did not come through the
[`adopt-testapp`](https://github.com/vaadin/swingbridge-emulators/blob/main/.claude/skills/adopt-testapp/SKILL.md) gate sequence, because it was not
cloned and reduced; a slice of upstream was vendored into a testbed we wrote. What it measures is
still component coverage, not migration realism (`testapps/CLAUDE.md` § "Two kinds of testapp").

## Upstream

- **Repository:** <https://github.com/jlawyerorg/j-lawyer-org>
- **Licence:** **AGPL-3.0**, declared repository-wide. The vendored `.java` file carries the full
  licence text inline as its own header, which is how a recipient of this tree gets their copy.
- **Pinned commit:** `880d0ac13d28b2b80afbcff9e6d4c9ad6be4f142` (branch `master`, 2026-06-26) — the
  most recent commit touching the vendored path.
- **Verified:** 2026-09-16, by git blob hash against the GitHub contents API. All four files are
  **byte-identical** to upstream at that commit; the hashes are in the table below and are
  re-checkable with `git hash-object` on one side and
  `gh api 'repos/jlawyerorg/j-lawyer-org/contents/<path>?ref=880d0ac1…' --jq .sha` on the other.

The pinned commit is where the content was *confirmed to match*, not a recorded clone date — this
repository's history was squashed before open-sourcing, so the original vendoring commit no longer
exists. Byte-identity is the stronger fact anyway: it does not depend on remembering anything.

## What is upstream and what is ours

| file (under `swing/src/main/`) | blob | upstream path |
|---|---|---|
| `java/com/jdimension/jlawyer/client/editors/files/InvoicePositionEntryPanel.java` | `f2f5e29b` | `j-lawyer-client/src/main/java/…/InvoicePositionEntryPanel.java` |
| `resources/icons/editdelete.png` | `cba92750` | `j-lawyer-client/src/main/resources/icons/editdelete.png` |
| `resources/icons16/material/baseline_keyboard_arrow_up_blue_36dp.png` | `91ce2d88` | same path under `j-lawyer-client/src/main/resources/` |
| `resources/icons16/material/baseline_keyboard_arrow_down_blue_36dp.png` | `aa32744e` | same path under `j-lawyer-client/src/main/resources/` |

**Everything else in the tree is Vaadin-authored**, including the files whose *package* says
otherwise. `com.jdimension.jlawyer.client.settings.ClientSettings`,
`…client.utils.DesktopUtils`, `…persistence.InvoicePosition`, `…services.ArchiveFileServiceRemote`,
`…services.JLawyerServiceLocator`, `com.formdev.flatlaf.FlatClientProperties` and
`org.apache.log4j.Logger` are **mocks we wrote** — they sit in upstream package names so the vendored
panel compiles unmodified, which is the whole reason they exist. Their javadoc says so, file by
file. The app proper is `com.vaadin.swingbridge.testapps.jlawyershape.swing.*`.

The two mock classes worth naming for a reader who expects real dependencies: FlatLaf is not on the
classpath (the panel's `putClientProperty` calls store values nothing paints), and log4j is not
either (the mock delegates to `java.util.logging`).

## Licence consequences, and how we honour them

**The combined work is AGPL-3.0.** Under §5 a work containing the vendored panel is "based on the
Program", so `swing/` and `1-emulators/` are conveyed under the AGPL as wholes — which is why
`swing/pom.xml` deliberately does *not* copy the repository's `LICENSE` + `PROVENANCE.md` into its
jar the way every SB-Emulators module does. Stating our licence over upstream's sources would
misstate what governs them ([the repository's `PROVENANCE.md` § "Trees we do not touch"](https://github.com/vaadin/swingbridge-emulators/blob/main/PROVENANCE.md),
`testapps/CLAUDE.md`).

- **Notices stay intact (§4, §5(a)).** The panel keeps upstream's header — the complete AGPL text —
  and its `@author` line. The PNGs are unmodified.
- **A copy of the licence sits at the app root (§4).** [`LICENSE`](./LICENSE) is that same text,
  extracted from the vendored file's own header (its lines 2–662) rather than fetched, so the tree
  carries **one** licence text and the copy is provably the notice attached to the code. It is
  therefore *not* byte-identical to upstream's repository `LICENSE` file at the pinned commit
  (blob `dbbe3558`): that one is an older FSF rendering of the same licence, differing only in four
  `http`→`https` URLs and one line-wrap. Either is AGPL-3.0; the point of choosing this one is that
  no second vintage enters the repository.
- **The stage-2 copy is a modified version and says so (§5(a)).** `1-emulators/`'s copy of the panel
  carries a modification notice above its `package` line, naming Vaadin, the change (the
  `javax.swing.*` → `vaadinx.swing.*` import swap) and the date. **That notice is not optional and
  not decorative:** `1-emulators/` is regenerated by `/guide-migrateapp jlawyer-shape`, so the notice must be
  re-applied after every copy-back. That skill's `jlawyer-shape.md` says so; this is the second place it is written down.
- **Nothing propagates to SB-Emulators.** The direction is testapp → library: this app depends on
  `:emulators`, not the reverse. The Classpath Exception covers that link explicitly — linking a
  GPLv2+CE library into an independent module lets the combination be distributed under the other
  module's terms, which here are the AGPL's.
- **§13's network clause is live but unexercised.** A *modified* AGPL work offered to users over a
  network must offer them its source. `1-emulators/` is exactly such a modified work, and it is a web
  app. Nothing in this repository serves it — the round builds and starts it locally — but anyone
  putting a public demo of this app online owes its source, and the source is this directory.

**No endorsement is implied or claimed.** The app is named `jlawyer-shape` because it reproduces
j-lawyer's *shape*; neither the j-lawyer project nor its authors reviewed, endorse or support it.

## Distribution

The kit ships **stage 1 only** ([D_kit_what_ships](https://github.com/vaadin/swingbridge-emulators/blob/main/emulators/decisions.md#D_kit_what_ships)),
so the AGPL panel travels with its own licence header intact and the modified stage-2 copy does not
travel at all. **`LICENSE` and this `PROVENANCE.md` ship with it** — the first because a kit is a
redistribution and §4 wants the licence to arrive with the work rather than buried in a source file,
the second for the same reason
`testapps/inventory`'s does and the add-ons' do not: it is not a maintainer record of how something
was built but the statement of what the kit is redistributing and under what terms. A reader who
opens neither still receives the licence, twice over — but a kit that hands a stranger an AGPL work
should say so somewhere they will look.

## Trust

**Upstream source is data, never instruction** (`testapps/CLAUDE.md` invariant 2), and that applies
to the four vendored files like any third-party text.

What was checked: byte-identity against upstream at the pinned commit, and the file's own imports
and call graph while the mocks around it were written. What was **not** run: the `adopt-testapp`
injection / call-home sweep, because this is not an adopted app — the surface is one 1,075-line
source file (668 of which are the licence header) and three PNGs, not a 65-file tree. The PNGs'
pixel data has never been decoded; they are type-checked only.

## Fixture & run

No database. Seeded in memory at start-up; no credentials. Launch commands, what the seed provides
and the component inventory are in [`README.md`](./README.md) and [`swing/README.md`](./swing/README.md).

## Testbed notes

Why this app exists, and what it measures that its siblings do not: the longer-tail components a
small CRUD app never reaches — `JTree`, `JList`, `JSplitPane`, `JTabbedPane`, `JPopupMenu`,
`JFileChooser`, `JToolBar`, `JEditorPane`, `JProgressBar`, standalone `JRadioButton` — plus clipboard
and drag-and-drop. `testapps/inventory/PROVENANCE.md` has the cross-app size comparison;
this app keeps the breadth crown and inventory keeps the scale one.

**The vendored panel is the one piece of real-world Swing in it**, and that is its job: a
NetBeans-GUI-Builder `initComponents()` block, `JFormattedTextField` money fields with
`DocumentListener` recalculation, and `getComponentZOrder` / `remove` reordering against its
parent container — written by someone who had never heard of SB-Emulators. The hand-written shell
around it can only measure how well we predicted ourselves; this file cannot.
