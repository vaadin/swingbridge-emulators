# Emulating the AWT widget set — the widgets not yet built

**Status:** design-passed, uncommitted. **The AWT lane is delayed and paused** (maintainer's call,
2026-09-22) — the three AWT design files stay as they are, and nothing in them is queued for the
alpha. Recorded because ~2,000 lines of finished design with no commitment behind it reads as a plan,
and the question *"is this alpha scope?"* had to be asked once already.

**Maintainer-facing.** What is built, why the lane exists, the Sampler convention and the traps every
AWT widget re-checks are decided in
[D_awt_lane](../emulators/decisions.md#D_awt_lane). This file is what is left: a pointer per unbuilt
component and the lane's stopping point. **The per-component design passes are the detail** — one
`ideas/awt-*.md` each; none of them is planned work.

## Design-passed, not planned

| class | pass | cost: was → now | the headline call |
|---|---|---|---|
| `TextField` (+ `TextComponent`) | [awt-textfield.md](./awt-textfield.md) | ~1 → **~1.5–2** | a new AWT-level `TextComponentMixin`, *not* SD_sbutton's duplicate-the-shell call — the shared surface is the JDK's own base class with a named second consumer |
| `TextArea` | [awt-textarea.md](./awt-textarea.md) | ~1 → ordered **after** TextField, which pays for the shared base | scrollbar policy as host CSS classes over the slotted `<textarea>`; `TextComponent` is `sealed`, which settles R_leaf_peer_lockdown without a search |
| `MenuBar` / `Menu` / `MenuItem` / `CheckboxMenuItem` / `PopupMenu` | [awt-menus.md](./awt-menus.md) | multi-session → **~2** | **zero new surrogates** — `MenuNode` is already layer-neutral, so the AWT tree plugs into `MenuTreeBuilder` unchanged |

**Reading the passes:** they predate D_awt_lane's one-pane-per-class Sampler convention, so their
Sampler / exit-gate sections say "Demo 5 on the existing `AWT widgets` route" and name
`AwtWidgetsPanel` / `AwtWidgetsWarnInventoryTest`. Read those as "a new `Awt<Class>Panel` +
`Awt<Class>WarnInventoryTest`, and a `SamplerCatalogue` line" — which is a required edit where those
docs say none is needed. The demo *content* each pass specifies is unaffected. Check each pass
against D_awt_lane's traps too, since several were found after it was written.

## Where the lane stops

A Swing app carrying AWT residue is well served by `Button` + `Label` + `TextField` + `Panel` and
stops there — so `TextField` is the one widget in that set still missing.

For a *pure* AWT app, the menu pass demotes `MenuComponent` from blocker: a second root hierarchy
turns out to be ~5 methods a migrator touches, with zero new surrogates, and its irreducible cost is
not the menus at all but re-typing `MenuContainer` through the landed `vaadinx.awt.Component` and
`Frame`. **`Canvas` alone is the blocker** (permanently out, D_awt_lane), and a pure-AWT app should
probably still be pointed at SwingBridge Streamer instead.
