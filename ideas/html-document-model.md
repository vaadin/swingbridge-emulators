# HTML document model for the rich text editor (`HTMLEditorKit` / `HTMLDocument`)

**Status:** **read-only peer plumbing SHIPPED; the document model and the write
half stay PARKED.** The 2026-08-17 prevalence survey below
settled the general question — **Mode A (render-only) is ~91% of files and 13-of-18
apps do zero authoring** — and its sting was that the read-only majority's two
most-used APIs were both gaps. Those gaps are now closed, on the RTE peer, with no
`HTMLDocument` and no second peer:

| Slice | Outcome | Durable record |
|---|---|---|
| 0 — read-only look-check | no work owed; a `readOnly` RTE already reads as a viewer | [SD_sjeditorpane_rte](../surrogates/decisions.md#SD_sjeditorpane_rte) accepted costs |
| 1 — `HyperlinkListener` (34 files) | `ACTIVATED` via a shadow-root delegated click, read-only panes only | [SD_hyperlink_listener](../surrogates/decisions.md#SD_hyperlink_listener) |
| 2 — `HTMLEditorKit` + `StyleSheet.addRule` (56 / 49 files) | both classes **extend** their JDK counterparts; rules become shadow-root CSS | [D_htmleditorkit](../emulators/decisions.md#D_htmleditorkit) + [SD_add_css_rule](../surrogates/decisions.md#SD_add_css_rule) |
| 3 — `setPage` / `setBase` (12 / 11 files) | audit; two silent defects fixed, `setBase` deferred to slice 4 | [SD_setpage_audit](../surrogates/decisions.md#SD_setpage_audit) |
| P — parser-only (13 files) | free, and *stays* free: D_htmleditorkit's extend-the-JDK-class shape makes the nested `ParserCallback` / `Parser` the JDK's own types | [D_htmleditorkit](../emulators/decisions.md#D_htmleditorkit) |
| 4 — read-only element model (17 / 11 / 3 files) | `getDocument()` on a `text/html` pane is a real `HTMLDocument`, parsed by the JDK's own reader; mutators WARN | [D_htmldocument](../emulators/decisions.md#D_htmldocument) |

**The read-only half is done.** What remains is the **write half** (Modes B and C),
and it is the part the survey says almost nobody needs: 11 of 127 files show any
authoring at all, 7 of the 9 mutating files are appends that need no element model,
and only **2 files** — one chat client's message-correction feature — splice an
interior element.
**Next action:** on any app that reaches for this, grep its source for the tells
in ["The four usage modes"](#the-four-usage-modes--what-each-looks-like-in-a-swing-app)
(`StyleConstants.set*` → B; `insertAfterEnd`/`setOuterHTML` → C). Mode A or Mode P
means this file is finished and can be deleted.

Previously researched and **rejected** (2026-07-20 spike, recorded in
[D_jtextpane](../emulators/decisions.md#D_jtextpane) "Rejected: `HTMLEditorKit`
supersede") on prevalence grounds — "rarely used, niche-within-a-niche." Re-opened
because a real app turned up that uses it. This file gathers the prior research so
we don't re-walk it, and scopes what is left to build.

**Delete this file** when slice 4 is either built (fold its design into `D_jtextpane`/`D_jeditorpane`
plus a new `SD*`) or rejected again (the one-line why-not in `D_jtextpane`
already stands). Everything shipped so far is already recorded in the decision
logs above — nothing durable would be lost today except the survey and the spike
facts below, so move those to `D_jtextpane` rather than dropping them.

## What slice 4 still needs answered

> The read-only half no longer waits on this; slice 4 and the write half do.
>
> - Does the app drive the **element/DOM model** (`getElement`, `setInnerHTML`,
>   `insertAfterEnd`, `HTMLDocument.Iterator`) or the `StyleConstants` styled-run
>   API? This is the only question slice 4 turns on.
> - If it mutates: is it **C-append** (transcript/console, 7 of 9 corpus files —
>   cheap, needs no element model) or **C-splice** (interior element located by id
>   or caret, 2 of 9 — the expensive shape)?
> - What HTML constructs actually appear — tables? forms? nested lists? Or the
>   RTE subset (headings/lists/quote/code/link/inline styles) we already handle?
>   The peer's rendering ceiling binds regardless of how faithful a document model is.
>
> Already answered by the shipped slices, so no longer blocking: whether the app calls
> `new HTMLEditorKit()` explicitly (either way works — D_htmleditorkit), whether it relies on
> `HyperlinkListener` / `setPage` (both supported — SD_hyperlink_listener / SD_setpage_audit), and whether it
> only renders static HTML (that path is done).


## The four usage modes — what each looks like in a Swing app

The verdict pivots on **which of these the app's code actually does**. The
triage tell is a grep of its source: `setText`/`setPage`/`setEditable(false)`
only → **A**; `StyleConstants.set*` + `setEditable(true)` → **B**;
`getElement`/`insertAfterEnd`/`setInnerHTML`/`getIterator` → **C**.

**Measured field distribution (2026-08-17 survey): A ≈ 91% of files, C-mutation
7%, B 2%.** Two triage corrections that survey forced, apply them when grepping
an app: `StyleConstants.` alone is **not** a B tell (reads of
`StyleConstants.NameAttribute` are a render-only `ViewFactory` idiom — require
`StyleConstants.set*`), and bare `getIterator(` is **not** a C tell (require an
`HTML.Tag` argument). A fourth mode the original A/B/C triage missed is now
[Mode P](#mode-p--parser-only-no-pane-at-all-cheapest-win-pure-jdk-reuse) —
**parser-only** (`ParserDelegator`, no pane at all), 13 files in the corpus,
more than all DOM mutation combined.

### Mode A — render-only (display static HTML). *Dominant (~91%); shipped.*

The pane is a viewer; the user never edits it.

```java
// (A1) about-box / help viewer
JEditorPane pane = new JEditorPane();
pane.setContentType("text/html");
pane.setEditable(false);
pane.setText("<html><body><h1>About</h1><p>Version 2.3</p></body></html>");
pane.setPage(getClass().getResource("/help/index.html"));   // or a URL/classpath resource

pane.addHyperlinkListener(e -> {                            // clickable links
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED)
        Desktop.getDesktop().browse(e.getURL().toURI());
});

// (A2) explicit kit + stylesheet, still read-only
HTMLEditorKit kit = new HTMLEditorKit();
pane.setEditorKit(kit);
kit.getStyleSheet().addRule("body { font-family: sans-serif; }");
pane.setText("<h2>Report</h2><ul><li>one</li><li>two</li></ul>");
```

**(A3) the custom-`ViewFactory` idiom — recognise it, don't mis-triage it.** Five
corpus apps ship a near-identical copy, and it is what makes a render-only file
*look* like Mode B: it reads `StyleConstants.NameAttribute` as an attribute
**key** to find `<img>` tags and refuse them a view, so a report pane never hits
the network.

```java
public class NoImagesKit extends HTMLEditorKit {
    @Override public ViewFactory getViewFactory() {
        return new HTMLFactory() {
            @Override public View create(Element elem) {
                Object o = elem.getAttributes().getAttribute(StyleConstants.NameAttribute);
                if (o == HTML.Tag.IMG) return new ComponentView(elem);   // or a blank view
                return super.create(elem);
            }
        };
    }
}
```

Nothing is authored here. Since we drop images anyway, such a kit is **inert for
us** — the migrator's intent (don't render images) is already our behaviour, so
the whole subclass can WARN-and-noop rather than needing a `ViewFactory` seam.

**Status: shipped.** `StyleSheet.addRule` (D_htmleditorkit / SD_add_css_rule), `HyperlinkListener`
(SD_hyperlink_listener) and the `setPage` fidelity audit (SD_setpage_audit) all landed on the RTE peer, so
Mode A is covered end-to-end within the RTE subset. What Mode A can still want and
not get is introspection via the element model — slice 4, below. No
`StyleConstants` trap here.

### Mode B — `StyleConstants` authoring. *Provably capped (spike fact #3).*

User edits interactively; toolbar buttons apply attributes via `StyleConstants`.

```java
JTextPane pane = new JTextPane();
pane.setContentType("text/html");     // installs HTMLEditorKit + HTMLDocument
pane.setEditable(true);
StyledDocument doc = pane.getStyledDocument();

// "Bold" toolbar button on the current selection
MutableAttributeSet bold = new SimpleAttributeSet();
StyleConstants.setBold(bold, true);
doc.setCharacterAttributes(pane.getSelectionStart(),
    pane.getSelectionEnd() - pane.getSelectionStart(), bold, false);

// built-in styled action, same effect
new StyledEditorKit.BoldAction().actionPerformed(new ActionEvent(pane, 0, "bold"));

// ...and reading it back — THE TRAP:
boolean b = StyleConstants.isBold(doc.getCharacterElement(off).getAttributes());
// on an HTMLDocument this is FALSE for <strong> — stored as HTML.Tag.*, not StyleConstants.*
```

**Verified, not assumed** (headless JDK 25, 2026-08-17 — re-runnable in ten lines):

```
text at offset: 'bi'                                    // from <p><strong>bi</strong></p>
StyleConstants.isBold()      = false
attribute keys present       = strong(Tag) name(StyleConstants)
```

The key is an `HTML.Tag`, so the styled-run API cannot see the bold at all.
Colour is the exception and *does* resolve — same probe,
`StyleConstants.getForeground()` → `java.awt.Color[r=255,g=0,b=0]` — which is
exactly the asymmetry that makes a `StyleConstants`-settable heading story
impossible rather than merely awkward.

Cannot be delivered faithfully on `HTMLDocument`. For an app in this mode, the
honest offer is today's `RteHtmlCodec` styled surface (D_jtextpane) with its documented
stopping point — not a real `HTMLDocument`.

### Mode C — DOM/element authoring. *Rare (7%); the common half is cheap, the rare half is not.*

Code treats the document as a live HTML DOM and splices markup into it. **The
survey splits this mode in two, and the cheap half dominates** — triage which
half an app is in before assuming this mode is expensive.

**C-append — 7 of 9 mutating files.** Everything happens at the end or relative
to the root: a transcript, console, or log pane. **No element is ever located.**

```java
// the whole shape, three spellings seen in the wild
doc.insertBeforeEnd(doc.getDefaultRootElement(), "<p>" + line + "</p>");
kit.insertHTML(doc, doc.getLength(), "<p>" + line + "</p>", 0, 0, null);
doc.insertAfterEnd(root, line);
```

Expressible on the RTE peer as read-value → concatenate → set-value. It needs
**no element model at all**, so it is not "new territory" and should not be
priced as such.

**C-splice — 2 of 9 files, both one chat client's message-correction feature.**
Locates an interior element, then rewrites it in place:

```java
Element msg = doc.getElement(MESSAGE_HEADER_ID);       // by id
doc.setOuterHTML(msg, newMessage);                     // replace that element

Element cur = doc.getCharacterElement(pane.getCaretPosition());  // by caret
doc.insertAfterEnd(cur, text);
```

**And the navigate-only slice — 3 files** — which mutates nothing; it walks
anchors to post-process links:

```java
HTMLDocument.Iterator it = doc.getIterator(HTML.Tag.A);
while (it.isValid()) { AttributeSet a = it.getAttributes(); it.next(); }
```

`setInnerHTML` — which this doc originally led with — appears in **zero** files
across 27 apps; treat it as vestigial, not as the representative call.

A faithful in-memory model is buildable, but the **RTE peer can't render tables
or arbitrary CSS** (SD_sjeditorpane_rte) — so `getText()`/introspection would be correct while
the *visual result* diverges. Confirm what they need to *see* vs. *round-trip*.

### Mode P — parser-only, no pane at all. *Cheapest win; pure JDK reuse.*

Not a UI mode, and missing from the original A/B/C taxonomy — but **13 files** in
the corpus, more than all DOM mutation combined. `HTMLEditorKit` used purely as a
headless HTML scraper (NetBeans javadoc parsing, a chat client's `Html2Text`):

```java
new ParserDelegator().parse(new StringReader(html),
    new HTMLEditorKit.ParserCallback() {
        @Override public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
            if (t == HTML.Tag.A) collect(a.getAttribute(HTML.Attribute.HREF));
        }
    }, true);
```

No peer, no rendering, no document model — nothing to emulate. `javax.swing.text.html.parser`
is not in the `Component` hierarchy, so it is **reused unchanged from the JDK**
per [D_whitelist_porting](../emulators/decisions.md), and an import-swap of a Mode-P file needs
no `vaadinx` class to exist at all.

**Settled, and it cost nothing.** The risk was that adding
`vaadinx.swing.text.html.HTMLEditorKit` to the rewrite list would make
`HTMLEditorKit.ParserCallback` / `.Parser` resolve to *our* nested types, which the
JDK's `ParserDelegator.parse(Reader, javax…ParserCallback, boolean)` would reject.
[D_htmleditorkit](../emulators/decisions.md#D_htmleditorkit)
dissolved it by **extending** the JDK kit rather than porting it: the nested types
come along by inheritance and *are* the JDK's types, so a Mode-P file keeps
compiling and running after the swap — pinned by a deliberately-Java test
(`HTMLEditorKitJavaCompatTest`), since Kotlin cannot express inherited nested-type
resolution.

## Prevalence survey (2026-08-17) — Mode A dominates, but not the part we assumed

Run to settle the prevalence input that the re-opening raised. Supersedes the
2026-07-20 skim (fact 5) with measured data; facts 1–4 are untouched API facts.

**Method.** Two prongs, because GitHub code search proved unusable for corpus
building — `total_count` is served but **item retrieval is degraded** (2–13 items
per query, later pages empty; `gh search code` burns the 10/min quota and fails),
so file-level classification had to come from real clones.
1. *Aggregate co-occurrence counts* via `/search/code` `total_count` (order-of-magnitude only).
2. *Real-app corpus*: **27 substantial open-source Swing apps** shallow-cloned
   (4.2 GB) — freeplane, jitsi, Spark, OmegaT, NetBeans, JOSM, JabRef, jEveAssets,
   Weka, ArgoUML, zaproxy, JMeter, ImageJ, muCommander, runelite, TripleA, gephi,
   cytoscape, SikuliX, jpexs, Audiveris, RSyntaxTextArea, processing, Arduino, dbeaver, JMRI, frostwire.
   Population = the **127 files across 18 apps** that import `javax.swing.text.html`.
   Every file classified by grep-tells, precedence C > B > A, then **hand-verified**.

**Hand-verification mattered — the naive tells over-count authoring 2×:**
- `getIterator(` matched unrelated app methods (NetBeans `WizardDescriptor.data.getIterator(this)` — 29 hits, all false).
- `StyleConstants.` mostly matched *reads* of attribute keys — `StyleConstants.NameAttribute` in a
  **render-only image-suppressing `ViewFactory`** (JMeter `RenderAsHTML`, JOSM `JosmHTMLFactory`,
  TripleA `GameNotesView`, NetBeans `HTMLEditorKitEx`, gephi `JHTMLEditorPane`). Authoring requires
  `StyleConstants.set*` / `set*Attributes` / `StyledEditorKit.*Action`. Corrected, Mode B collapsed from 13 files to **2**.

**Mode split (127 files, 18 apps):**

| Mode | Files | Share |
|---|---|---|
| **A — render-only** | 72 | 57% |
| UNCLEAR (13 of them = headless parser use, see below) | 41 | 32% |
| **C — DOM mutation** | 9 | 7% |
| C — navigate/read only | 3 | 2% |
| **B — `StyleConstants` authoring** | 2 | 2% |

**Any authoring evidence at all: 11 / 127 files = 8.7%** (13.0% with NetBeans
excluded as an atypical IDE platform; 12.8% of the non-UNCLEAR subset).
**13 of 18 apps do zero authoring.** Authoring concentrates in five apps:
jitsi (5 files), cytoscape (2), freeplane (2), SikuliX (1), NetBeans (1).

**What the 9 mutating files actually do — append, not splicing.** 7 of 9 mutate
only at the **end or relative to the root**: `insertBeforeEnd(root, …)` (cytoscape
`LogViewer`/`JResultsPane`), `insertHTML(doc, doc.getLength(), …)` (freeplane
`ChatMessageHistory`, SikuliX `EditorConsolePane`), `insertAfterEnd(root)` /
`insertBeforeStart(root)` (jitsi `StyledHTMLEditorPane` ×2, `HistoryWindow`).
That shape is a **transcript/console append** — expressible as read-HTML,
concatenate, set-value; it needs no element model at all. Only **2 files address
an interior element**, both in jitsi's chat: `setOuterHTML(correctedMsgElement, …)`
(message-correction) and `insertAfterEnd(currentElement, …)` located via
`getCharacterElement(caretPos)`; lookup is by **id** (`document.getElement(MESSAGE_HEADER_ID)`).

**Actual API surface (files in the corpus using each member) — the headline finding:**

| Member | Files | | Member | Files |
|---|---|---|---|---|
| `getStyleSheet` | **56** | | `getDefaultRootElement` | 11 |
| `addRule` | **49** | | `setBase` | 11 |
| `addHyperlinkListener` | **34** | | `insertAfterEnd` | 4 |
| `getElement(…)` | 17 | | `insertBeforeEnd` | 3 |
| `ParserDelegator` | 14 | | `setOuterHTML` | 2 |
| `setPage` | 12 | | **`setInnerHTML`** | **0** |

**Conclusions.**
1. **Read-only wins decisively** — scope read-only-first, as `D_jtextpane`'s escape hatch already allowed.
2. **`setInnerHTML` — the API this doc leads with in Mode C — appears in ZERO files across 27 apps.**
   The DOM-authoring model we feared owing is close to vestigial in the wild.
3. **The sting: "Mode A is already half-built" is wrong.** The corpus's two most-used
   APIs are `StyleSheet.getStyleSheet`/`addRule` (56/49 files — the A2 stylesheet
   pattern) and `addHyperlinkListener` (34). Today **`HyperlinkListener` drops-and-WARNs**
   and **arbitrary CSS has no RTE counterpart** (both per SD_sjeditorpane_rte). So the dominant mode is
   precisely where our two live gaps are — read-only-first is real work, not a
   victory lap, and mostly *not* work on a document model.
4. **A fourth mode the A/B/C taxonomy missed — now written up as
   [Mode P](#mode-p--parser-only-no-pane-at-all-cheapest-win-pure-jdk-reuse).** 13 files use
   `ParserDelegator` / `HTMLEditorKit.Parser` / `ParserCallback` to **scrape HTML
   headlessly with no pane at all** (NetBeans javadoc parsing, jitsi `Html2Text`).
   It needs no peer, no rendering, no document model — arguably the cheapest win
   in the whole idea, and it is pure-JDK reuse (D_whitelist_porting) rather than emulation.
5. **The SD_sjeditorpane_rte `Div`-peer question this raised is settled: RTE stays the single
   peer, no viewer peer returns.** The read-only majority is real, but the
   motivating case is *editing* rich text on the web, which a `Div` cannot do — so the
   subset loss (tables above all) is accepted as the price of one coherent,
   edit-capable peer, and SD_sjeditorpane_rte's mutable-`editable` / order-dependence objections
   stay dead-lettered rather than worked around. Rationale + the revisit trigger
   now live in [SD_sjeditorpane_rte](../surrogates/decisions.md#SD_sjeditorpane_rte);
   the shadow-root technique above is what keeps the majority's top APIs reachable
   without a second peer.

**Caveats.** Purposive (not random) app sample — substantial, mostly-mature OSS
desktop apps, skewed toward tools/IDEs over line-of-business CRUD; NetBeans alone
contributes 56 of 136 raw files (reported separately above). File-level counts,
not call-site or runtime-weighted: one `setOuterHTML` file can matter more to a
migration than ten `setText` files. GitHub `total_count` figures are
order-of-magnitude only, from a degraded endpoint. Corrected `new JButton`
baseline: 876,544 files, so `HTMLEditorKit`'s 16,096 is **~1.8%**, not the ~5%
recorded in `D_jtextpane` fact 5.

## Technique: shadow-root injection — **graduated**

The per-instance shadow-root seam this idea discovered (inject a `<style>` into the
component's own shadow root to style content Vaadin renders into shadow DOM;
attach a delegated listener there to catch events that retarget at the host) is
implemented and recorded in [SD_add_css_rule](../surrogates/decisions.md#SD_add_css_rule)
(styling, incl. why `::part()` cannot reach content and why the proposed
`theme`-attribute scoping proved unnecessary for runtime injection) and
[SD_hyperlink_listener](../surrogates/decisions.md#SD_hyperlink_listener)
(events, incl. the retargeting trap and the re-install-on-every-attach rule).
It generalises past this idea, so it is also summarised in
[surrogates/architecture.md](../surrogates/architecture.md#shadow-root-injection-styling-and-eventing-shadow-dom-content).

**Tables, for the record: no trick exists and none is needed.** The constraint is
Quill's *model*, not CSS — non-whitelisted tags are dropped entering the Delta,
before styling could matter. `<div>` is not an escape either (unknown tags
collapse to `<p>`). A table degrades in the codec to a run of `<p>` (one per row,
cells joined) or `<ul><li>`, with a WARN. Plain `RteHtmlCodec` work, deferred
until a feature request asks for it.

## Where we are today (the baseline the write half would extend)

- `vaadinx.swing.JEditorPane` — thin shell over `SJEditorPane` (Vaadin
  `RichTextEditor` peer), [D_jeditorpane](../emulators/decisions.md#D_jeditorpane) / [SD_sjeditorpane_rte](../surrogates/decisions.md#SD_sjeditorpane_rte).
  `createDefaultDocument` returns `PlainDocument`; `getText()` returns HTML.
  `setPage` fetches server-side with browser-style charset resolution (SD_setpage_audit);
  `addHyperlinkListener` fires `ACTIVATED` on read-only panes (SD_hyperlink_listener).
- `vaadinx.swing.text.html.HTMLEditorKit` / `StyleSheet` — **extend** their JDK
  counterparts; `setEditorKit` accepts the kit and `getStyleSheet().addRule(…)`
  styles the pane's content via shadow-root CSS ([D_htmleditorkit](../emulators/decisions.md#D_htmleditorkit) / [SD_add_css_rule](../surrogates/decisions.md#SD_add_css_rule)).
  Sampler's `HtmlViewer` route demos the whole read-only slice.
- `vaadinx.swing.JTextPane` — thin subclass reusing the same RTE peer, with a
  **faithful `StyledDocument` surface via `RteHtmlCodec`** (`DefaultStyledDocument`
  backing model, `StyleConstants`⇄RTE-subset-HTML both ways, jsoup on the reverse
  path), [D_jtextpane](../emulators/decisions.md#D_jtextpane).
- `vaadinx.swing.text.html.HTMLDocument` — a `text/html` pane's `getDocument()`,
  extending the JDK class and reparsed from the pane's markup by the JDK's own
  reader ([D_htmldocument](../emulators/decisions.md#D_htmldocument)).
  Reads resolve; the six HTML mutators WARN. `getText()` still returns markup —
  the document is a projection, not the source of truth.
- **Writing through the document is what stays OOS** — [D_direct_document_mutation](../emulators/decisions.md#D_direct_document_mutation)
  narrowed to its rendering half: `setText(String)` remains the entry point, and an
  `HTMLDocument`'s *rendering* is still `View.paint(Graphics,...)`, permanently OOS
  per R_match_swing_errors sub-bucket (b).

## Prior research — the rejected `HTMLEditorKit` supersede (do not re-spike blind)

Verbatim findings from the 2026-07-20 headless JDK-25 spike + GitHub prevalence
check (source: `D_jtextpane` "Rejected: `HTMLEditorKit` supersede"). These are *facts
about the JDK API*, not opinions — they constrain any implementation:

1. **`kit.read()` / `kit.write()` are headless** ✓ — the only point in its
   favour. So a server-side pipeline is at least *possible* without a display.
2. **`write()` emits an HTML-3.2 dialect** — `<html>` wrapper, `align=` attrs,
   `<font color>`, `<s>`/`<strike>`, `<b>`/`<i>`, `<table>`, pretty-print
   whitespace — that clashes with RTE's subset on nearly every construct. So
   normalizing kit output to what RTE accepts needs **jsoup again on top** — the
   codec grows *larger and additive*, not smaller. We don't get to delete the
   existing `RteHtmlCodec`; we'd stack a second translation on it.
3. **The headline payoff is partly unreachable.** On an `HTMLDocument`,
   `StyleConstants.isBold()` / `isItalic()` return **false** for `<strong>` /
   `<em>` — they're stored as `HTML.Tag.*` keys, not `StyleConstants.*` keys
   (only colour + alignment resolve through `StyleConstants`). And building a doc
   `StyleConstants`-first serializes to **malformed HTML** — the model is
   HTML-string-driven (`setInnerHTML` / `insertAfterEnd` / `getElement`), not
   `StyleConstants`-driven. So "headings/lists/quote become first-class
   `StyleConstants`-settable" — the whole motivation for the supersede — **cannot
   be delivered** on top of `HTMLDocument`. This one is the killer for the
   styled-API story.
4. **`HTMLEditorKit` is not a sanitizer.** `read()`→`write()` passes
   `onclick` / `onerror` / `javascript:` through (only `<script>` bodies drop).
   Adopting it as-is would *remove* today's whitelist-emit XSS safety unless a
   sanitizer is bolted on — more code.
5. **Prevalence** — superseded by the [2026-08-17 survey](#prevalence-survey-2026-08-17--mode-a-dominates-but-not-the-part-we-assumed)
   above, which measures the ratio at **~1.8%** of `new JButton` files (not the ~5%
   `D_jtextpane` fact 5 records) and confirms the read-only dominance quantitatively. The
   qualitative half stands: interactive HTML *authoring* is a known Swing pain point
   that serious apps route around (SHEF / FlyingSaucer / embedded-Chromium), and it
   is not a staple of the typical-CRUD-app R_match_swing_errors baseline.
   **→ A motivating app changes the prevalence input for itself, but NOT facts
   1–4.** Whatever is built on top of `HTMLDocument` still has to live with the
   HTML-3.2 dialect clash, the `StyleConstants`-unreachability, and the sanitizer gap.

## What's left: the write half

`D_jtextpane`'s escape hatch scoped this as **read-only-first**, and read-only is now
complete:

| Work | Corpus files | Status |
|---|---|---|
| `getStyleSheet` / `addRule` | 56 / 49 | shipped (D_htmleditorkit / SD_add_css_rule) |
| `addHyperlinkListener` | 34 | shipped (SD_hyperlink_listener) |
| `setPage(url)` | 12 | shipped (SD_setpage_audit) |
| `getElement` / `getDefaultRootElement` / `getIterator` / `setBase` | 17 / 11 / 3 / 11 | shipped (D_htmldocument) |
| **HTML mutators** (`insertBeforeEnd`, `setOuterHTML`, …) | 9 files total | **open — the write half** |

**What the write half actually costs, now that the read half is built.** Reaching
the peer means serializing the mutated document back to RTE-subset HTML —
`kit.write()`'s HTML-3.2 dialect through a second jsoup pass (fact 2's additive
translation), plus two-way R_swing_is_truth sync between a live element model and the peer, which
is where the real expense sits. Against that: **C-append is 7 of the 9 mutating
files and needs none of it** — it is `pane.setText(pane.getText() + html)`, which
works today and is what the migration guide prescribes. So the honest scope of the
write half is **C-splice: 2 files in a 127-file corpus**, both one chat client's
message-correction feature. Build it only for an app that *is* those two files.

The `StyleConstants`-settable-heading story (Mode B) stays dead regardless — fact 3.
The plan's up-front decisions are all consumed now: P1 was avoidable rather than
just deferrable (see slice 4 below), P2's split-the-Karibu-invisible-slice test
strategy is the module's habit, P3 (no `Div` peer) is settled in SD_sjeditorpane_rte, P4 shipped
with D_htmleditorkit.

### Slice 4 — read-only element model. **DONE (2026-08-18)** — graduated to [D_htmldocument](../emulators/decisions.md#D_htmldocument)

Durable design lives in D_htmldocument. **Three things this file had wrong, kept because they
are the reusable lessons:**

1. **It priced the wrong implementation.** The plan was "parse the HTML into a
   read-only `Element` tree with jsoup." Unnecessary: `kit.read()` is headless
   (spike fact 1), so extending the JDK `HTMLDocument` makes the JDK's own parser
   build the tree, and `getElement` / `getDefaultRootElement` /
   `getCharacterElement` / `getIterator` become JDK code paths rather than
   emulation. The extend-the-JDK-class move that saved slice 2 saved this one too,
   and for a second reason: it is also what makes the migrator's cast compile.
2. **P1 turned out to be avoidable, not just deferrable.** "No `HTMLDocument`
   instance ships until slice 4" was right about the danger — a document's text is
   *rendered* text, so `getText()` would stop returning markup — but wrong that the
   danger is inherent. Keeping the markup as the source of truth and treating the
   document as a *derived projection* dissolves it in one `getText()` override.
   SD_setpage_audit's `setBase` deferral rested on the same assumption and lifts with it.
3. **The identity trap that looked like a design constraint.** A second
   `kit.read()` into a live document throws `Must insert new content into body
   element-`, which reads as "swap instances per refresh" and would have silently
   dropped user `DocumentListener` registrations. `remove(0, getLength())` first —
   what Swing's own `setText` does — keeps one instance alive. Measured, not
   reasoned; the wrong version would have passed a naive test.

Two limitations found while building, both now in D_htmldocument: `id` attributes do not
survive a round trip through the editor (so `getElement(id)` serves server-authored
markup only — the same family as SD_hyperlink_listener's protocol allowlist), and
`getIterator(HTML.Tag)` is null for block tags by the JDK's own contract.

### Exit gates, if the write half is ever built

Same shape every shipped slice used: a Sampler demo addition, an
`EditorPanesWarnInventoryTest` / `HtmlViewerWarnInventoryTest` extension moving the
newly-supported call off the expected-WARN list, and per-class unit tests in
`:emulators` / `:surrogates`. Anything touching shadow DOM or `executeJs`
additionally needs a recorded browser verification — SD_hyperlink_listener is the cautionary tale:
its Karibu gate passed green while the feature was broken in the browser.

### Explicitly not in this plan

`StyleConstants`-settable headings over `HTMLDocument` (fact 3 — dead);
`HTMLEditorKit.write()` output as the peer value (facts 2 + 4); a second `Div`
viewer peer (SD_sjeditorpane_rte); tables; `View.paint(Graphics,…)`.

## Open questions / risks

- **Rendering ceiling is the RTE peer, not the document model.** Even a perfect
  `HTMLDocument` can only *display* what `RichTextEditor` renders — its HTML
  subset (SD_sjeditorpane_rte: tables/arbitrary CSS lost in display too). A faithful in-memory
  model over an editor that can't show tables may satisfy `getText()`/element
  introspection but not the visual result. Confirm what the app needs to
  *see* vs. *round-trip*.
- **Sanitizer.** Any HTML that reaches the browser must keep the current
  whitelist-emit XSS safety (Flow's jsoup both directions). Don't regress it.
- **`View.paint(Graphics,...)`** stays OOS regardless (R_match_swing_errors sub-bucket (b)) — we
  drive the RTE peer, never the JDK view hierarchy.
- ~~**Surrogate impact?**~~ **Answered: none.** The element model turned out to be
  pure Swing-side document semantics, so it stayed emulator-only like `RteHtmlCodec`
  — no `SD*`, no peer plumbing. A write path would be the same question again, and
  the answer would likely differ (serializing back to the peer is peer work).
- ~~**Reconsider `createDefaultDocument`**~~ **Settled by D_htmldocument**: the element model
  follows the *content type* rather than the kit, `createDefaultDocument` still
  answers `PlainDocument` (it runs before any content type exists), and `getText()`
  keeps returning markup because the document is derived from it.


## Rejected alternatives (carried from the spike — don't retry)

- **Replace `RteHtmlCodec` with `HTMLDocument`/`HTMLEditorKit` to make
  headings/lists/quote `StyleConstants`-settable** — dead per fact 3; the model
  isn't `StyleConstants`-driven and can't be made so.
- **Adopt `HTMLEditorKit.write()` output directly as the peer value** — dead per
  fact 2 (HTML-3.2 dialect clash) + fact 4 (no sanitization).
