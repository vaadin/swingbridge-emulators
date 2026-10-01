# Mixed emulator / vanilla-Vaadin component trees

**Status:** brainstorm / not decided. Opened 2026-08-28 out of
[D_component_displayable](../emulators/decisions.md#D_component_displayable), which had to give the
mixed shape *some* answer in order to close `Q_displayable_for_components` and gave it the smallest
defensible one. This doc is where the real design happens, plus the visibility front that entry
deferred.

**Maintainer-facing.** Assumes [D_window_displayable](../emulators/decisions.md#D_window_displayable),
D_component_displayable, and the migration arc in CLAUDE.md.

---

## 1. What "mixed" means here, and why it is not the thing CLAUDE.md already rules on

CLAUDE.md's migration arc bans one kind of mixing: **`:emulators` and `:surrogates` APIs inside a
single view.** That is a stage-2-vs-stage-3 rule and it is settled.

This doc is about a *third* combination it does not cover: an **emulator component and a vanilla
Vaadin component in one live component tree.** Two directions, and they are not symmetric:

- **Emulator into Vaadin** — `vaadinLayout.add(panel.getPeer())`. Works today. `getPeer()` is public
  and explicitly sanctioned by [R_no_vaadin_in_api](../CLAUDE.md#hard-rules) ("the peer accessor
  itself … because no JDK class claims that name").
- **Vaadin into emulator** — putting a Vaadin `Grid` inside a `JPanel`. **No seam exists.**
  `Container.add` takes `vaadinx.awt.Component` and nothing else.

So mixing is currently one-directional *by construction*, and nobody decided that — it fell out of
`getPeer()` being public while `Container.add` is JDK-typed.

**The working direction is about to break this shape** (agreed 2026-10-01,
[vaadin-ui-thread-only.md](./vaadin-ui-thread-only.md) § "The mechanism"): calls on a detached
emulator queue until the write that attaches its island, and `getPeer()` leaves the migrator's surface
for a `void` `withPeer(Consumer)`. `vaadinLayout.add(panel.getPeer())` then has no expression, and
its obvious translation `panel.withPeer(p -> vaadinLayout.add(p))` never runs — the body waits for an
attach only it would cause. **Emulator into Vaadin therefore needs a seam of its own that is the
attaching write** (take the lock, drain the island, add), e.g. `EHelper.addTo(HasComponents,
Component)`. That turns this direction from an accident of `getPeer()` into a decided API — which is
also the natural place to answer §9 question 7 for it. The test corpus's bare-peer attach keeps
working either way, through the test accessor.

### It is already load-bearing, so "ban it" is not on the table

Worth stating plainly before designing anything: **the entire `:emulators` test corpus is mixed
mode.** Hundreds of tests build a bare `JPanel` / `JButton` and attach its peer straight to the mock
UI with no `JFrame` anywhere. Whatever we decide for migrators, this shape has to keep working, which
is why D_component_displayable's realisation-root rule had to exist rather than defaulting rootless
components to "never displayable."

### Which direction migrators actually need

Both, at different moments:

- **Vaadin into emulator** is the *incremental widget swap* — a migrator replaces one `JTable` with a
  real Vaadin `Grid` inside an otherwise-Swing view. This is the direction with no seam, and it is
  probably the more valuable one, because it is how a view gets rewritten a piece at a time instead
  of all at once.
- **Emulator into Vaadin** is the *not-yet-ported island* — a Vaadin view that still hosts one
  Swing-shaped panel. This is the direction that works today.

Open: is the widget swap actually how migration goes, or do views get rewritten whole? The arc doc
implies whole-view rewrites (stage 2 → stage 3 per view), which would make the missing direction less
urgent than it looks. Worth asking a real migration round rather than assuming.

---

## 2. What ships today (D_component_displayable's minimum)

- An emulator with **no emulator parent** whose peer attaches to a live UI is a **realisation root**:
  it becomes displayable and cascades `addNotify` + `DISPLAYABILITY_CHANGED` through its subtree.
  Detach unrealises it.
- This is the same rule that already realised a `@MainWindow` JFrame from a route, with `Window`
  un-hard-coded. It is a generalisation, not a new mode.
- A realised mixed root adopted into a Swing container is unrealised first, then realised by its new
  parent — mirroring the ordinary reparent path, because a displayable-with-no-parent component is a
  state AWT cannot reach and so has no precedent to copy.

Nothing else about mixing is designed.

---

## 3. What the displayability/visibility measurements taught, that constrains this

All measured against JDK 25 on a real display, and all of it bears on mixed mode:

- **AWT never writes a descendant's `visible` field.** Showing or hiding a `JFrame` leaves every
  child `visible == true`; only the derived `isShowing()` changes, and it derives by walking the
  parent chain. This is why no visibility-inheritance machinery was needed — and it is exactly what
  makes mixed mode *hard*, see `Q_mixed_showing` below: the walk bottoms out at `parent == null`,
  which in mixed mode is a lie.
- **Visibility defaults are per-class, not inherited.** `java.awt.Panel`, `Button`, `JPanel` all
  construct visible; only `Window` constructs hidden.
- **Realisation cascades top-down and unrealisation bottom-up** (`addNotify` chains to super then
  walks children forwards; `removeNotify` walks children backwards then chains). The JDK's own
  comment explains the index loops: a menu is a child of `JLayeredPane` rather than of a particular
  component, so the collection mutates under an iterator when a menu shows or hides.
- **`DISPLAYABILITY_CHANGED` does not recurse; `PARENT_CHANGED` does.** Each component fires its own
  displayability event with `changed` = itself, because the cascade already visits everyone.
- **`AncestorEvent` is keyed to *showing*, not to displayability.** `pack()` fires no
  `ancestorAdded`; hiding a window fires `ancestorRemoved`.
- **A component added to a hidden-but-displayable container becomes displayable and not showing**,
  and fires no showing event — it was never showing to begin with.

---

## 4. `Q_mixed_showing` — `isShowing()` lies at the boundary

<a id="Q_mixed_showing"></a>

`Component.isShowing()` is now `visible && displayable && (parent == null || parent.isShowing())`.
For a mixed root `parent == null`, so the walk terminates and the answer is `visible && displayable`
— **regardless of whether the Vaadin ancestors are visible.** Put the panel inside a Vaadin `Details`
that is collapsed, or a `Tab` that is not selected, and `isShowing()` says `true`.

This is the sharpest concrete defect the current design has, and it is new — before
D_component_displayable, `isDisplayable()` read Vaadin attachment, which at least tracked *some* of
the browser's reality.

Candidate answers:

- **Walk the peer's Vaadin parent chain when the emulator parent is null.** `peer.getParent()` up to
  the UI, `&&`-ing `isVisible()`. Cheap, and it makes the mixed root's answer as good as Vaadin's own.
  Cost: `isShowing()` becomes a two-vocabulary method, and Vaadin's `isVisible()` is not the same
  question as "in the viewport" (a `Tab`'s content may be attached and visible but scrolled away —
  though AWT's `isShowing()` doesn't answer that either, so the mismatch may be acceptable).
- **Answer `false` unless the whole chain is emulator-owned.** Conservative, wrong in the common case
  (a panel in a plain `VerticalLayout` really is showing), rejected on sight.
- **Leave it, document it.** Defensible only if mixed mode stays a test-only shape, which §1 says it
  is not.

Related and unresolved: a mixed root's `setVisible(false)` writes `peer.setVisible(false)`, which
Vaadin honours — so the *outbound* direction works and only the *inbound* one is blind.

---

## 5. `Q_mixed_ancestor_lookups` — the null `getParent()` and everything that walks it

<a id="Q_mixed_ancestor_lookups"></a>

In AWT terms a mixed root's `getParent()` genuinely **is** `null` — it is in no `Container`. That is
JDK-faithful and should stay. The problem is everything that walks up from a component expecting to
find a window:

- `JComponent.getTopLevelAncestor()` → `null`
- `SwingUtilities.getWindowAncestor(c)` → `null`
- `JComponent.getRootPane()` → `null`
- `JOptionPane.showMessageDialog(parentComponent, …)` with a rootless `parentComponent`
- `JComponent.getGraphics()`, and anything else keyed off a root pane
- `FocusTracker` / `transferFocus`, which walk the emulator tree and simply stop at the boundary

Each of these has a defensible null-tolerant answer in isolation (a Vaadin dialog does not need an
owner window the way an AWT one does). The question is whether they should all independently tolerate
null, or whether there should be **one resolver** that answers "what is above me" across the gap.

**A unifying option worth thinking about:** keep `getParent()` null and JDK-faithful, but give the
internal walks a *peer-chain-aware* resolver — from `this.peer`, walk Vaadin parents until another
emulator's peer turns up (`EHelper.getEmulator(peer)` already does that lookup), and continue the
Swing walk from there. That would make a `JPanel` nested three Vaadin layouts below a `JFrame`
resolve its window correctly.

Its cost is the thing to weigh: the two hierarchies would then *disagree by design* —
`frame.getContentPane().getComponents()` would not contain the panel, but the panel's
`getWindowAncestor()` would be the frame. Is a component that is in the window's logical hierarchy
but not in its `getComponents()` a coherent object, or a trap?

---

## 6. `Q_vaadin_into_emulator` — the missing seam, and where it can live

<a id="Q_vaadin_into_emulator"></a>

`Container.add(com.vaadin.flow.component.Component)` is the obvious shape and probably the wrong one:

- It does not *shadow* a JDK signature, so R_no_vaadin_in_api limb 1 does not forbid it outright
  (the rule's test is "does the JDK define this signature?"). But it puts a Vaadin type on the public
  surface of a JDK-named class, which is the spirit of the rule if not its letter.
- **Overload-resolution hazard:** `container.add(null)` becomes ambiguous where it compiles today.
  That is a migrated-code breakage caused by an API we added for a feature the migrator may not use.

Better candidates:

- **An adapter that produces a `vaadinx.awt.Component`** — `EHelper.wrap(vaadinComponent)` or a
  `vaadinx.awt.VaadinComponentHolder`, which the migrator then `add`s through the ordinary JDK-typed
  path. No new signature on `Container`, no ambiguity, and the wrapper is a real emulator so the
  hierarchy, the cascade, layout and `getComponents()` all stay consistent. This looks right.
- **Nothing at all; the migrator inserts into `peerContentElement()` directly.** Already possible
  (it's public for layout managers). Costs: the Vaadin child is invisible to `getComponents()`,
  `getComponentCount()`, the `LayoutManager`, and the realisation cascade — i.e. exactly the silent
  divergence the wrapper avoids. Probably the thing to *document against* rather than support.

Open: does the wrapper need to be a `Container` (so the Vaadin subtree can hold further emulators),
or is a leaf enough?

---

## 7. `Q_mixed_layout` — two layout systems meeting at the seam

<a id="Q_mixed_layout"></a>

An emulator `Container` writes its `LayoutManager`'s CSS to `peerContentElement()`; a Vaadin
`VerticalLayout` writes its own flex CSS to the same kind of element one level up. At the boundary,
one of them is styling a child the other thinks it owns.

Unknowns worth a spike rather than a guess: does a `BorderLayout`-emitting `Div` behave sanely as a
flex child of a `VerticalLayout` (does it stretch, collapse, or size to content)? Does a Vaadin
component wrapped per §6 receive the grid-area CSS its emulator wrapper is assigned? Under
[R_layouts_close_enough](../CLAUDE.md#hard-rules) pixel drift is fine, but "renders at preferred
width leaving half the dialog empty" is explicitly *structural* and not.

---

## 8. The deferred visibility front (folded in from D_component_displayable)

Two accepted limitations were recorded there rather than fixed, and they are one piece of work.

### `Q_showing_cascade` — `SHOWING_CHANGED` has no producer

<a id="Q_showing_cascade"></a>

SB-Emulators fires `DISPLAYABILITY_CHANGED` (new) and `PARENT_CHANGED` (existing). It fires
`SHOWING_CHANGED` **never** — so the canonical lazy-populate idiom

```java
addHierarchyListener(e -> {
    if ((e.getChangeFlags() & SHOWING_CHANGED) != 0 && isShowing()) populate();
});
```

registers and never fires. R_no_vaadin_in_api limb 2, in its purest form.

The JDK fires it, measured: on `Window` show/hide down the whole subtree with `changed` = the window,
**and** on any intermediate `Container.setVisible` down its subtree with `changed` = that container.
`Container.fireHierarchyEvent` already recurses, so the fan-out is nearly free — the work is picking
the sites (`Component.setVisible`, `Window.show()`/`hide()`) and the guard (only when the derived
`isShowing()` of the subtree actually flips, which is not the same as the flag flipping).

One ordering divergence to fix or accept while in there: **the JDK fires deepest-first, SB-Emulators fires
self-first.** `Container.fireHierarchyEvent` does `super` (self) then children; the JDK's measured
order for both `PARENT_CHANGED` and `SHOWING_CHANGED` is leaf before parent. Pre-existing, small, and
cheap to fix in the same pass.

### `Q_ancestor_event_edge` — `AncestorEvent` is on the wrong edge

<a id="Q_ancestor_event_edge"></a>

SB-Emulators fires `ancestorAdded`/`ancestorRemoved` from `addNotify`/`removeNotify`. The JDK fires them from
the *showing* transition. Consequences today:

- an explicit `pack()` fires `ancestorAdded` where the JDK is silent — **an invented event**, which
  D_property_fanout_audit calls a strictly emulator-layer bug with no policy to hide behind;
- a hide/show cycle fires **neither**, where the JDK fires both — so `ancestorRemoved`-on-hide
  teardown never runs.

Re-keying needs `Q_showing_cascade` first, which is why they travel together. Expect real test churn:
the current keying is asserted in several places, and (per the D_window_registry precedent) those
assertions should be **inverted rather than deleted**, since each currently pins the wrong edge as
deliberate.

---

## 9. Open questions for the design pass

1. Does the widget-swap direction (**Vaadin into emulator**) actually occur in migration, or do views
   get rewritten whole? Decides whether §6 is urgent or theoretical.
2. `Q_mixed_showing`: peer-chain walk, or documented lie?
3. `Q_mixed_ancestor_lookups`: null-tolerate each site independently, or one peer-chain-aware
   resolver — and is "in the logical hierarchy but not in `getComponents()`" coherent or a trap?
4. `Q_vaadin_into_emulator`: wrapper component (leaf or container?), or nothing-plus-documentation?
5. `Q_mixed_layout`: what actually happens at the seam? Needs a browser spike, not reasoning.
6. Should a mixed root appear in any registry? Today `Frame.getFrames()` cannot see it, which seems
   right — it is not a window — but `FocusManager.getFocusedWindow()` then has no answer for a focused
   component in a mixed tree.
7. Is there a **migrator-facing** story here at all, or is mixed mode an internal capability that
   happens to keep the test corpus working? If the former, it needs a section in the migration docs;
   if the latter, it needs to be said somewhere so nobody builds on it.
