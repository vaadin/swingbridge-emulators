# A dialog renders too small for its contents

**Status:** brainstorm / not decided — triage first. **Maintainer-facing.** Found by the inventory
guide round of 2026-09-30, reported by the migrating agent (not in that round's `STUMBLES.md`).

## What was seen

The inventory app's Tools → Change Username/Password dialog (`GDialog` hosting
`ChangePasswordPanel`, a JGoodies `FormLayout` form) renders too small for what it holds.

## First question: cosmetic or structural?

[R_layouts_close_enough](../CLAUDE.md#R_layouts_close_enough) decides what happens next. A dialog a
few pixels short is out of scope; **content clipped or scrolled out of reach is a structural
failure** and in scope. Screenshot it against the stage-1 app before anything else.

## Where to look

- How `GDialog` sizes itself: `pack()`, an explicit `setSize`, or `setPreferredSize` from pixel
  constants — each maps onto a different emulator path.
- `FormLayout` via the jgoodies add-on's `CssEmittingLayoutManager`: does the CSS grid report an
  intrinsic size the hosting Vaadin `Dialog` honours, or does the dialog take a fixed size first?
  The add-on is a frozen starter ([M1D_addon_starter_status](../migration/1-swing-to-emulators/decisions.md#M1D_addon_starter_status)),
  but bugfixes are not parked.

## Open

- `Q_pack_semantics`: what does `Window.pack()` do to a Dialog-rendered window today, and is a
  content-sized dialog (`width: auto`) the right translation of it?
- `Q_structural`: is it clipped, or only tight?
