# HTML document model — the write half (`HTMLDocument` mutators)

**Status: parked.** The read-only half of `javax.swing.text.html` support has shipped —
stylesheets, hyperlinks, `setPage`, and a read-only `HTMLDocument` element model — scoped by
a 27-app field survey; the survey, its mode taxonomy and the slice map are recorded in
[D_html_read_only_first](../emulators/decisions.md#D_html_read_only_first), and the element
model in [D_htmldocument](../emulators/decisions.md#D_htmldocument). What remains is the
**write half**: today the six HTML mutators (`setInnerHTML`, `setOuterHTML`,
`insertAfterStart` / `insertBeforeEnd` / `insertBeforeStart` / `insertAfterEnd`) WARN and
change nothing, because the document is a read-only projection of the pane's markup.

**Next action:** on any app that reaches for this, grep its source for the tells
(`insertAfterEnd` / `insertBeforeEnd` / `setOuterHTML` / `insertHTML`, plus `getElement` /
`getCharacterElement` locating the target). If every mutation is an append, this file does
not apply to it — see below.

## The honest scope: C-splice, not C-append

The survey split DOM authoring in two, and only the rare half needs a write path:

- **C-append — 7 of the 9 mutating corpus files.** A transcript, console or log pane that
  mutates only at the end or relative to the root; no element is ever located.

  ```java
  doc.insertBeforeEnd(doc.getDefaultRootElement(), "<p>" + line + "</p>");
  kit.insertHTML(doc, doc.getLength(), "<p>" + line + "</p>", 0, 0, null);
  doc.insertAfterEnd(root, line);
  ```

  Already served: it rewrites to `pane.setText(pane.getText() + html)`, which works today and
  is what the migration guide prescribes (spec.md `H_htmldocument_mutators`).

- **C-splice — 2 of 9 files, both one chat client's (jitsi) message-correction feature.**
  Locates an interior element, then rewrites it in place:

  ```java
  Element msg = doc.getElement(MESSAGE_HEADER_ID);       // by id
  doc.setOuterHTML(msg, newMessage);                     // replace that element

  Element cur = doc.getCharacterElement(pane.getCaretPosition());  // by caret
  doc.insertAfterEnd(cur, text);
  ```

  **This is the whole open scope.** Build it only for an app that *is* this shape.

## What the write half would cost

Reaching the peer means serializing the mutated document back to RTE-subset HTML —
`kit.write()`'s HTML-3.2 dialect through a second jsoup pass (the additive translation
D_jtextpane's spike fact 2 warns about) — plus two-way R_swing_is_truth sync between a live
element model and the peer, which is where the real expense sits. Note also D_htmldocument's
limitation that `id` attributes do not survive a browser edit, so an id-located splice works
only on server-authored markup.

## Questions an app that needs it must answer first

- Is it **C-append** (cheap, already served) or **C-splice** (the expensive shape)?
- What HTML constructs actually appear — tables? forms? nested lists? Or the RTE subset
  (headings/lists/quote/code/link/inline styles) we already handle?
- What does it need to *see* vs. *round-trip*? (See the rendering ceiling below.)

## Open risks

- **Rendering ceiling is the RTE peer, not the document model.** Even a perfect
  `HTMLDocument` can only *display* what `RichTextEditor` renders — its HTML subset
  ([SD_sjeditorpane_rte](../surrogates/decisions.md#SD_sjeditorpane_rte): tables and arbitrary
  structure are lost in display too). A faithful in-memory model may satisfy
  `getText()`/element introspection but not the visual result.
- **Sanitizer.** Any HTML that reaches the browser must keep the current whitelist-emit XSS
  safety (Flow's jsoup both directions). `HTMLEditorKit` is not a sanitizer (D_jtextpane spike
  fact 4) — don't regress it.
- **Surrogate impact.** The read-only element model stayed emulator-only (pure Swing-side
  document semantics). A write path would likely differ: serializing back to the peer is peer
  work.
- `View.paint(Graphics, …)` stays out regardless (R_match_swing_errors sub-bucket (b)).

## Exit gates, if it is ever built

Same shape every read-only slice used: a Sampler demo addition, an
`EditorPanesWarnInventoryTest` / `HtmlViewerWarnInventoryTest` extension moving the
newly-supported call off the expected-WARN list, and per-class unit tests in `:emulators` /
`:surrogates`. Anything touching shadow DOM or `executeJs` additionally needs a recorded browser
verification — SD_hyperlink_listener is the cautionary tale: its Karibu gate passed green while
the feature was broken in the browser.

## Explicitly not in this plan (settled elsewhere — don't retry)

`StyleConstants`-settable headings over `HTMLDocument` (Mode B — dead per D_jtextpane spike
fact 3); `HTMLEditorKit.write()` output as the peer value (facts 2 + 4); a second `Div` viewer
peer (SD_sjeditorpane_rte); `View.paint(Graphics, …)`.

## Adjacent open item: tables degrading in the codec

No CSS trick restores tables and none is needed: the constraint is Quill's *model* —
non-whitelisted tags are dropped entering the Delta, before styling could matter, and `<div>` is
no escape (unknown tags collapse to `<p>`). A table could instead degrade in `RteHtmlCodec` to a
run of `<p>` (one per row, cells joined) or `<ul><li>`, with a WARN. Plain codec work, deferred
until a feature request asks for it.
