# SJSlider / SJProgressBar VERTICAL — try `writing-mode` instead of `rotate`

**Status:** idea, not planned work. Filed 2026-08-24.
"SJSlider / SJProgressBar VERTICAL orientation".

## The idea

`SJSlider` renders VERTICAL as `transform: rotate(-90deg)` (`applyTransformCss`), and `SJProgressBar`
tried the same and reverted it. The known failure is that a transform rotates the *visual* and leaves
the *layout box* at its pre-rotation dimensions, so the control overflows or collapses its region.

While probing `java.awt.Scrollbar`'s peer choice, `writing-mode: vertical-lr` measured as a strictly
better mechanism on a bare `<input type=range>`: vertical rendering **with a correct layout box** in
Firefox 153 and Chromium 151, and `direction: rtl` flips which end is the minimum (giving JSlider's
min-at-bottom without `scaleX(-1)`). The engine-by-engine table lives in
[SD_sscrollbar](../surrogates/decisions.md#SD_sscrollbar).

**So: does the same trick work on `<vaadin-slider>` and `<vaadin-progress-bar>`?**

## Why it is an open question and not a fix

The probe measured a *native* input. Both surrogate hosts are web components whose shadow DOM owns
the track/fill layout. Writing-mode inherits into a shadow tree, but only helps if the internals are
built on flex or logical properties — not physical `width` / `left`. So the problem splits in two,
and a fix needs both halves:

1. **The host's layout box** — writing-mode fixes this for any element. Host-agnostic.
2. **The internals drawing along the new axis** — per-component, unknown, lives in the shadow root.

Half a fix is not shippable: a squashed horizontal slider inside a correctly-shaped tall narrow box
looks worse than today's rotate, even though it stops wrecking the surrounding layout.

Prior expectation per host, to be confirmed or killed by the probe:

- **`SJProgressBar`** — likely needs more than writing-mode. Its javadoc records the fill as
  `width: calc(progress * 100%)`; `width` is physical, so writing-mode won't redirect it. Suspected
  route is writing-mode for the box *plus* a shadow-part override for the fill direction.
- **`SJSlider`** — genuinely unknown, and the likelier of the two to work. Could not be resolved
  offline: no `@vaadin/slider` npm package exists on the dev machine (the flow jar carries Java plus
  npm coordinates only), so the component CSS isn't readable without a Vite build.

## The probe

Boot Sampler, hit the Inputs route, set `writing-mode` (and `direction: rtl`) on the live
`<vaadin-slider>` / `<vaadin-progress-bar>` from Playwright, and read back visual box, layout box,
and parent cell width, plus a screenshot of whether the internals followed. Same protocol as
awt-scrollbar's probe, against the real hosts. ~20 minutes.

## Priority

Low — both TODO entries are explicitly "revisit only on real demand" and no migration target has
asked for a vertical slider or progress bar. Natural trigger: a migrated app that actually has one,
or the AWT `Scrollbar` slice landing and making the vertical CSS a shared helper worth reusing.

If the probe comes back green for either host, the win is bigger than the visual: it retires
`applyTransformCss`'s transform-composition machinery (orientation × inverted) in favour of two
inherited CSS properties.

Upstream asks this would sharpen, either way: `vaadin-progress-bar` should fill via logical
`inline-size` rather than physical `width`; `RangeInput.setOrientation` should set
`writing-mode: vertical-lr` (it still sets only the pre-standardization recipe).
