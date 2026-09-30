# Emulating the AWT widget set — the lane, and where each component's design pass lives

**Status:** seven widgets landed; the rest is design-passed but uncommitted. Estimated 2026-08-21,
last updated 2026-08-25. **The AWT lane is delayed and paused** (maintainer's call, 2026-09-22) —
the four AWT design files stay as they are, and nothing in them is queued for the alpha. Recorded
because 2,100 lines of finished design with no commitment behind it reads as a plan, and the
question *"is this alpha scope?"* had to be asked once already.
**Maintainer-facing.** Extends the [component surface](../CLAUDE.md#current-scope) sideways into
`java.awt.*` widgets. Naming was pre-decided by
[SD_naming](../surrogates/decisions.md#SD_naming)
(`SButton` for `java.awt.Button`, distinct from `SJButton`).

This file is the lane's rationale, its cross-cutting findings, and a pointer per component. **The
per-component design passes are the detail** — one `ideas/awt-*.md` each; none of them is planned
work.

## Why bother — the honest version

**Any single AWT widget buys little.** No real application is Swing-plus-one-AWT-button. What makes
the lane worth doing anyway:

1. **It is the cheapest possible proof that the AWT lane works end-to-end** — `java.awt.*` →
   `vaadinx.awt.*` import-swap, an emulator hanging directly off `vaadinx.awt.Component` (not
   `JComponent`), a surrogate on `ComponentMixin` (not `JComponentMixin`).
2. **Real Swing apps carry AWT residue.** Long-lived apps ported from AWT 1.0 keep a
   `java.awt.Label` in a corner, an `java.awt.List`, a `Choice` in a legacy dialog. Those are hard
   compile errors at stage 2 with no workaround short of hand-rewriting the view. One
   `java.awt.Button` in a 200-view app is a migration blocker of exactly the same size as a missing
   `JButton` would be.
3. **`vaadinx.awt.Component` was abstract-with-no-leaf-widgets.** Every concrete emulator under it
   was a Swing class or a window (`Frame`, `Window`, `Dialog`, `FileDialog`). Adding plain AWT leaf
   widgets exercises that base on the path it was designed for and flushes out Swing-shaped
   assumptions that quietly leaked into it — which it did: see *Findings in landed code* below.

The counterweight, stated once: **no `java.awt.Panel`, `Choice`, `List` or `Checkbox` usage exists
anywhere under `testapps/`.** The whole lane is motivated by argument, not by an observed app. That
has not blocked anything — `Choice`, `Checkbox` and `Panel` all shipped on the argument — so treat it
as a standing caveat on the lane's *priority*, not a gate on any one slice.

## Landed

| class | decisions |
|---|---|
| `java.awt.Button` | [SD_sbutton](../surrogates/decisions.md#SD_sbutton) / [D_awt_button](../emulators/decisions.md#D_awt_button) |
| `java.awt.Label` | [SD_slabel](../surrogates/decisions.md#SD_slabel) / [D_awt_label](../emulators/decisions.md#D_awt_label) |
| `java.awt.Choice` | [SD_schoice](../surrogates/decisions.md#SD_schoice) / [D_awt_choice](../emulators/decisions.md#D_awt_choice) |
| `java.awt.Checkbox` + `CheckboxGroup` | [SD_scheckbox](../surrogates/decisions.md#SD_scheckbox) / [D_awt_checkbox](../emulators/decisions.md#D_awt_checkbox) |
| `java.awt.Panel` | [SD_no_spanel](../surrogates/decisions.md#SD_no_spanel) / [D_awt_panel](../emulators/decisions.md#D_awt_panel) |
| `java.awt.Scrollbar` | [SD_sscrollbar](../surrogates/decisions.md#SD_sscrollbar) / [D_awt_scrollbar](../emulators/decisions.md#D_awt_scrollbar) |
| `java.awt.List` | [SD_slist](../surrogates/decisions.md#SD_slist) / [D_awt_list](../emulators/decisions.md#D_awt_list) |

Each ships its **own** Sampler route under the `AWT` nav category — `AwtButtonPanel` / `AwtLabelPanel` / `AwtChoicePanel` / `AwtCheckboxPanel` / `AwtPanelPanel`, catalogued as `Button` / `Label` / `Choice` / `Checkbox` / `Panel` — and its own `Awt<Class>WarnInventoryTest`. `Choice` also settled the estimate the sizing table below carries for the rest of the family: the ~1-session figure held, but its *reasoning* did not — the cost was a fresh surrogate, not the re-skin of `SJComboBox` the table assumed.

`Checkbox` landed close to its re-estimated ~1.5–2, and closed two of its own open questions against the docs rather than the browser: `vaadin-checkbox` exposes `::part(checkbox)` with the checkmark at `::part(checkbox)::after` (the pass guessed `::before`), but the radio glyph is better done with the documented `--vaadin-checkbox-border-radius` / `--vaadin-checkbox-checkmark-char` custom properties, which the theme is authored to read. Two of its findings generalise to the widgets still ahead:

- **A package-private field named like a Java property silently shadows the accessor pair for same-package Kotlin.** *(Historical — the corpus is Java as of [D_kotlin_retired](../emulators/decisions.md#D_kotlin_retired), so this exact failure can no longer occur. Kept because it is why `CheckboxGroup`'s field is named and scoped as it is, and because the guidance in its last sentence stands on its own.)* `CheckboxGroup.selectedCheckbox` mirrored the JDK's visibility, and every `group.selectedCheckbox = box` in the Kotlin test bound to the *field*, routing around `setCurrent` entirely — six failures with no compile error. Mirror the JDK's field *names* freely; mirror package-private *visibility* only when something outside the class actually needs it.
- **A JDK class that renders two ways is not automatically an R_leaf_peer_lockdown problem.** The test is whether the two renderings need two *types*. AWT reads the checkbox-vs-radio glyph off `group != null` at paint time, so one peer with a live property is the faithful model and R_leaf_peer_lockdown holds in its strongest form. `java.awt.List`'s `setMultipleMode` is the next instance of this shape.

`List` was the lane's first **peer-choice reversal**, and the finding generalises: its design pass (`ideas/awt-list.md`, deleted at graduation per the lane's convention; the rejected peers and their specifics live in SD_slist) had
settled on `Grid<Integer>` — the SD_sjlist index-identity answer — and a second read against the JDK source
overturned it in favour of a native `<select multiple size=N>`. The test that flipped it: *is the AWT
widget and some HTML primitive the same 1995 control?* Where the answer is yes, the primitive already
holds semantics a Vaadin component would have to re-implement — `size` is `rows`, `multiple` is the
runtime mode flip, `<option>`s are the item store with positional identity, and a programmatic
`option.selected` write fires no `change`, so the silent-mutation contract stops being an assumption
to verify (`isFromClient()`) and becomes a property of the platform. It also killed three of the pass's
four open questions outright. `Choice`→`Select` is not a counter-example: a `Choice` *is* a dropdown,
which is what `Select` renders. Two further findings worth carrying:

- **A new `vaadinx.awt.*` type shadows same-named types for everything in that package.**
  *(The Kotlin half is historical — no `.kt` file survives [D_kotlin_retired](../emulators/decisions.md#D_kotlin_retired). The Java half is live and is the reason to keep reading it.)*
  `vaadinx.awt.List` shadows `java.util.List` for every source file declaring `package vaadinx.awt`,
  which the design pass predicted; it *also* shadowed `kotlin.collections.List` for every `.kt` file
  in that package, which it missed, and there the failure was a wall of overload-ambiguity errors
  pointing at an unrelated line. Expect the Java half again from a future `vaadinx.awt.Menu` /
  `Window` / `Container`-adjacent name; the fix is one qualified reference.
- **Flow re-checks a DOM `setFilter` expression server-side**, keyed by the expression string, so a
  synthetic `keydown` in a Karibu test must carry `"event.key === 'Enter'"` → `true` or the listener
  silently never runs. Costs a confusing red test the first time.

`Panel` came in at the bottom of its estimate — `vaadinx.awt.Container` already implemented all but
four members, so the deliverable was the Sampler pane and the browser verification rather than the
class. Two findings generalise:

- **A server-side layout assertion cannot see the bug it is most likely to have.** Every layout test
  reads style off `peerContentElement()`, which passes whether or not that element is the one the
  children are actually in — and Karibu never computes layout, resolves shadow DOM, or applies the
  theme. The `<div>` gate in `LayoutCss.applyContainerCss` checks the *tag*, not the *identity*, so
  "right tag, wrong div" is invisible to the whole suite. Panel's browser pass added an explicit
  co-location check (each child's `parentElement` *is* the element carrying the computed CSS); since
  Panel is the simplest container in the surface, the result retro-validates the mechanism generally.
  Every future container slice should make that check.
- **Read the JDK source of the *next* widget before sizing the current one.** `java.awt.ScrollPane`'s
  `addToPanel` wraps a lightweight child in `new Panel()` with a `BorderLayout`, and both its
  `setLayout` (which throws `AWTError`) and `addImpl` are `final` — so Panel is a *dependency* of
  ScrollPane, which fixes the lane's ordering, and the wrapper hands ScrollPane back the `<div>` host
  that its `vaadin-scroller` peer otherwise denies it. Also worth carrying forward: `JScrollPane`
  faced the same peer-has-internal-structure problem and **declined** `peerContentElement()`,
  bypassing `addImpl` instead — nothing in the tree overrides that seam, so the awt-panel pass's
  prediction that the next container "will have to override it" is unproven, not established.

## The Sampler convention: one pane per AWT class

**Superseded, and it supersedes every per-component pass below.** The four landed widgets first
shipped as Demos 1-7 of a single `AwtWidgetsPanel` on one `AWT widgets` route, and each design pass
in the table below was written against that shape ("append Demo 5 to `AwtWidgetsPanel`"). That does
not scale: eleven classes on one pane is a scroll-forever page and one omnibus
`AwtWidgetsWarnInventoryTest` whose user-path test fails without saying which widget regressed.

The pane boundary that scales is **the class**, because that is also the boundary of the lane's
teaching content: the *Lane-wide findings* below are all per-class facts (which setters are silent,
which constants collide, what `getSelectedObjects()` returns), re-checked once per widget rather than
inherited from the previous one. So:

- one `Awt<Class>Panel` per AWT class, `SamplerCatalogue` category `"AWT"`, label the **bare JDK
  class name** (`Button`, `Label`, `Choice`, `Checkbox`) — the category header already says AWT, and
  the labels are collision-free against the Swing leaves (`Buttons`, `ComboBoxes`, `RadioButtons`, …);
- one `Awt<Class>WarnInventoryTest` per pane, holding that pane's user path plus that class's
  API-surface bucket. Peer-count assertions get sharper, not weaker: a pane containing only its own
  widget's peers can assert an exact count without arithmetic across sibling demos;
- demos *within* a pane number from 1 (`Checkbox` has two: ungrouped, then the group).

`CheckboxGroup` is the one carve-out and stays on the `Checkbox` pane — it is not a `Component`, has
no rendering of its own, and its entire observable behaviour is what the grouped boxes do.

**Reading the passes below:** their Sampler / exit-gate sections say "Demo 5 on the existing `AWT
widgets` route" and name `AwtWidgetsPanel` / `AwtWidgetsWarnInventoryTest`. Read those as "a new
`Awt<Class>Panel` + `Awt<Class>WarnInventoryTest`, and a `SamplerCatalogue` line" — which is now a
required edit where those docs say none is needed. The demo *content* each pass specifies is
unaffected.

## Design-passed, not planned

| class | pass | cost: was → now | the headline call |
|---|---|---|---|
| `TextField` (+ `TextComponent`) | [awt-textfield.md](./awt-textfield.md) | ~1 → **~1.5–2** | a new AWT-level `TextComponentMixin`, *not* SD_sbutton's duplicate-the-shell call — the shared surface is the JDK's own base class with a named second consumer |
| `TextArea` | [awt-textarea.md](./awt-textarea.md) | ~1 → ordered **after** TextField, which pays for the shared base | scrollbar policy as host CSS classes over the slotted `<textarea>`; `TextComponent` is `sealed`, which settles R_leaf_peer_lockdown without a search |
| `Scrollbar` | **landed** — [D_awt_scrollbar](../emulators/decisions.md) / [SD_sscrollbar](../surrogates/decisions.md) | ~1 for three → **~1 alone**, and that held | a real `SScrollbar` on `RangeInput`, *not* `JScrollBar`'s inert-`Div` path — `ScrollPaneAdjustable` doesn't extend `Scrollbar`, so D_viewport_scrollbar_shadows's justification is false here |
| `Panel` | **shipped** — [D_awt_panel](../emulators/decisions.md#D_awt_panel) / [SD_no_spanel](../surrogates/decisions.md#SD_no_spanel) | **trivial** — the estimate held | bare `Div`, **no `SPanel`**; `vaadinx.awt.Container` covered all but four members, so the slice was the Sampler pane and the browser verification, not the class. Also settled the lane's ordering: `java.awt.ScrollPane.addToPanel` wraps a lightweight child in `new Panel()` + `BorderLayout`, so Panel is a *dependency* of ScrollPane |
| `ScrollPane` (+ `ScrollPaneAdjustable`) | **shipped** — [D_awt_scrollpane](../emulators/decisions.md#D_awt_scrollpane) / [SD_sscrollpane](../surrogates/decisions.md#SD_sscrollpane) | ~1 for three → **~½ alone**, once the read direction was descoped | a new `SScrollPane` on `Scroller`; `ScrollPaneAdjustable` is emulator-only (JDK's is `final` with a package-private ctor). The row's original sizing held *because* the scroll channel was cut to server→browser only — the full bidirectional channel was the half-session the design pass added. Also settled: this pass's own prediction that `addToPanel` would hand ScrollPane a `<div>` host was **not taken** — the `vaadin-scroller` shadow root is a bare `<slot>`, so `peerContentElement()` is never touched and the "next container will have to override it" prediction below stays unproven |
| `MenuBar` / `Menu` / `MenuItem` / `CheckboxMenuItem` / `PopupMenu` | [awt-menus.md](./awt-menus.md) | multi-session → **~2** | **zero new surrogates** — `MenuNode` is already layer-neutral, so the AWT tree plugs into `MenuTreeBuilder` unchanged |

**Permanently out:** `Canvas` + `paint(Graphics)` — user-authored `Graphics` paint is out of scope
per R_match_swing_errors sub-bucket (b), and it is the single most common thing AWT-era code does.

## Lane-wide findings

Seven patterns the per-component passes found independently. Each is a per-widget trap, so each
recurs: check it once per class rather than assuming the previous widget's answer.

1. **The deprecated AWT-1.0 names are the JDK's *implementations*; the modern names are the
   aliases.** `getItemCount()` calls `countItems()`, `add(String)` calls `addItem(String)`,
   `setValue` funnels through `setValues`, `setLineIncrement`/`getVisible` are primary,
   `clear`/`delItems` are what `removeAll`/`remove` delegate to. Wiring these the intuitive way round
   leaves a migrator's override of the deprecated name silently dead — **R_no_vaadin_in_api limb 2's exact failure
   mode, once per widget.**
2. **AWT setters are silent where the Swing equivalent fires.** `Choice.select`, `Checkbox.setState`,
   `CheckboxGroup.setSelectedCheckbox`, `Scrollbar.setValue`/`setValues`, every `java.awt.List`
   mutator, every menu setter: no event. Only the toolkit peer fires. The one exception found is
   `TextComponent.setText`, which *does* fire exactly one `TextEvent`. Any design that re-skins the
   Swing surrogate gets this wrong by construction — which is why six of the eight passes reject a
   re-skin.
3. **Index identity (SD_sjlist) is the answer to duplicate items, not a WARN.** Vaadin's `KeyMapper` keys
   items in a `HashMap` on `identifierGetter.apply(item)`, so two *equal* Strings collapse to one
   key — and interning defeats a custom `IdentifierProvider`. `SJList` already solved this by keying
   rows on `Integer`; `Choice` and `List` inherit the solution.
4. **Three clashes recur on almost every Vaadin host.** `getListeners(Class<T>)` has the same
   erasure as Vaadin `Component`'s with an unrelated return type, so it is dropped surrogate-side and
   lives on the emulator (SD_sbutton's resolution, hit four times). `validate()` is `protected` on most
   Vaadin fields against `ComponentMixin`'s `public` default, needing a visibility-widening override
   (`SJComboBox`'s resolution). And `setLabel`/`getLabel` exist on several hosts meaning the *field
   caption* — a nominal trap, not a compile error.
5. **Constant collisions are per-widget and must be re-checked.** `Label`'s LEFT/CENTER/RIGHT are
   0/1/2 against `SwingConstants`' 2/0/4 (SD_slabel). `ScrollPane`'s SCROLLBARS_AS_NEEDED/ALWAYS/NEVER are
   0/1/2, *numerically identical* to `Adjustable.HORIZONTAL/VERTICAL/NO_ORIENTATION` — so
   `new ScrollPane(Adjustable.VERTICAL)` compiles and silently means ALWAYS. `TextArea`'s four are
   0–3 against `ScrollPane`'s three. `Scrollbar`'s match `SwingConstants` exactly, the inverse trap.
   `Choice`, `List`, `Checkbox` and the whole menu family declare **no** public constants.
6. **`getSelectedObjects()` has no single convention.** null-or-single-element for `Checkbox`,
   `CheckboxMenuItem` and `Choice`; an **empty array** for `java.awt.List`. Swing's `SJComboBox`
   returns an empty array where AWT's `Choice` returns null. Per class, every time.
7. **A new surrogate is the default, but not the rule.** Six passes propose one, on SD_sbutton/SD_slabel's test
   ("what state does the Swing class carry that the AWT one doesn't?"). Three do not, and the
   exceptions are principled: `Panel` gets a bare `Div` (nothing left to hold), the menu family
   reuses `SJMenuBar`/`SJPopupMenu` unchanged (their entire state is a layer-neutral `MenuNode`
   tree), and `ScrollPaneAdjustable` is emulator-only (the JDK class is `final`).

## Findings in landed code

All five defects the passes turned up are **fixed**; the rationale, the JDK-vs-ours comparison and
the Swing-sites-deliberately-untouched split live in
[D_awt_dead_hooks](../emulators/decisions.md#D_awt_dead_hooks).
The one worth carrying forward into the next widget: **a peer bridge must enter at `processEvent`,
not at `processXEvent`** — AWT routes peer-posted events through the first hop, and skipping it
leaves a migrator's override compiling, looking wired, and never running.

## The shape of the conclusion

A Swing app carrying AWT residue is well served by `Button` + `Label` + `TextField` + `Panel` and
stops there.

For a *pure* AWT app, the earlier reading of this file was wrong: it named `MenuComponent` and
`Canvas` as the two things that dominate. The menu pass demotes the first — a second root hierarchy
turns out to be ~5 methods a migrator touches, with zero new surrogates, and its irreducible cost is
not the menus at all but re-typing `MenuContainer` through the landed `vaadinx.awt.Component` and
`Frame`. **`Canvas` alone is the blocker**, and a pure-AWT app should probably still be pointed at
[SwingBridge Streamer](../CLAUDE.md#see-also) instead.
