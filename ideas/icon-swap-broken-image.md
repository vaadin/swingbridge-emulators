# Swapped icons render as a broken image

**Status:** brainstorm / not decided. **Maintainer-facing.** Found by the inventory guide round of
2026-09-30 (`testapps/inventory/1-emulators/STUMBLES.md`, "Not a doc gap" (b)).

## What was seen

The inventory app's `ActionButton` swaps a toolbar icon on hover and press with `setIcon(on)` /
`setIcon(off)`. The first icon renders; after the first swap the icon shows as a broken image, and
the browser logs a **403** on `/VAADIN/dynamic/resource/…`. Cosmetic in effect, but a 403 on our own
resource URL is not a cosmetic *cause*.

## Where to look

- `surrogates/.../util/Icons.imageIconToVaadinImage` builds a Vaadin `Image` from the icon's
  PNG-encoded raster through the `Image(byte[], String)` constructor, which registers a
  download handler behind a `/VAADIN/dynamic/resource/…` URL.
- A 403 there usually means the URL points at a resource that is no longer registered — its owning
  element was detached (unregistering the handler) while the browser still holds, or is handed
  again, the old URL.

## Open

- `Q_reattach_or_rebuild`: does the swap re-attach a *cached* `Image` whose registration died on its
  first detach, or build a fresh one whose URL the client never learns? Reproduce with two
  `ImageIcon`s alternated on one `JLabel` / `JButton` before theorising further.
- `Q_encode_per_swap`: every `setIcon` PNG-encodes the raster again and registers a new resource.
  A hover handler swaps many times a second — is per-`ImageIcon` caching of the encoded bytes (or
  of a data URL) worth it, independent of the 403?
- `Q_which_peers`: `JLabel` only, or every `setIcon` path (`AbstractButton`, menu items via
  `MenuNode`, `JComboBox` renderers)?
