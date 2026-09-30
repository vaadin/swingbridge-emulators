# Honour `<body style='width:Npx'>` in a JLabel's HTML text

`<html><body style='width:230px'>…</body></html>` is the standard Swing idiom for a label (or a
`JOptionPane` message) that wraps at a fixed width. `SJLabel.stripHtmlBodyWrapper` drops the
`<body …>` tag and its attributes, so the width is lost and the text renders as one unwrapped line.

Found on `testapps/crud`'s preview panel (2026-09-25), where it was *not* the cause of the widening
panel: that was the stretched CENTER child's pref never reaching its parent, fixed by
D_layout_pref_intrinsic. With that fix in, restoring the body width has no visible effect in
`crud`; restoring it alone gave 374px instead of 280px.

## Open questions

- `Q_body_width_swing_behaviour`: what does real Swing do when the body width (230) is wider than
  the space the layout gives the label (~128px in `crud`'s Bio row)? Clip at the label's bounds, or
  re-wrap at the allocated width (`BasicHTML.Renderer.paint` sizes the view to the allocation)?
  Measure on a real JDK (`xvfb-run`) before deciding the CSS: `width` + `overflow: hidden`, `width`
  alone, or `max-width`.
- `Q_body_width_mapping`: carry only `width`, or the whole `style` attribute onto a wrapper element?
  Swing's HTML supports a CSS1 subset, so a wholesale copy would render styles Swing ignores
  (R_no_silent_improvements).
- `Q_body_width_scope`: the same stripping applies wherever label-style HTML is rendered —
  `JOptionPane` messages, tooltips, button text. One shared helper, or per surrogate?
