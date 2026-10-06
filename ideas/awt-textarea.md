# `java.awt.TextArea` — design pass (not planned work)

**Status:** brainstorm, no commitment. Estimated 2026-08-21. Sized in [awt-widgets.md](./awt-widgets.md),
whose status line carries the lane's own: **delayed and paused, 2026-09-22.**

Third widget shape in the AWT lane after `Button` ([SD_sbutton](../surrogates/decisions.md) / [D_awt_button](../emulators/decisions.md))
and `Label` (SD_slabel / D_awt_label) — and the first one that is **not** a leaf hanging straight off
`vaadinx.awt.Component`: it needs `java.awt.TextComponent` underneath it. The
`TextComponent` base is analysed in the sibling pass, [awt-textfield.md](./awt-textfield.md);
this doc covers the base only as far as `TextArea` bends it, and defers every
base-level call there.

Everything below was read out of the JDK 25 sources (`lib/src.zip`) and out of
Vaadin 25.2.3 with `javap` + reflection. Where something is a guess it is in
*Open questions*, not in the body.

---

## What the JDK class actually is

`javap`, trimmed to public + the protected hooks that matter:

```
public class java.awt.TextArea extends java.awt.TextComponent {     // "non-sealed"
  public static final int SCROLLBARS_BOTH            = 0;
  public static final int SCROLLBARS_VERTICAL_ONLY   = 1;
  public static final int SCROLLBARS_HORIZONTAL_ONLY = 2;
  public static final int SCROLLBARS_NONE            = 3;
  public TextArea() throws HeadlessException;
  public TextArea(String);
  public TextArea(int rows, int columns);
  public TextArea(String, int rows, int columns);
  public TextArea(String, int rows, int columns, int scrollbars);
  public void addNotify();
  public void insert(String, int);          public synchronized void insertText(String, int);   // @Deprecated
  public void append(String);               public synchronized void appendText(String);        // @Deprecated
  public void replaceRange(String,int,int); public synchronized void replaceText(String,int,int);// @Deprecated
  public int getRows();      public void setRows(int);
  public int getColumns();   public void setColumns(int);
  public int getScrollbarVisibility();                       // no setter — ctor-only
  public Dimension getPreferredSize(int,int);  public Dimension preferredSize(int,int);  // @Deprecated
  public Dimension getPreferredSize();         public Dimension preferredSize();         // @Deprecated
  public Dimension getMinimumSize(int,int);    public Dimension minimumSize(int,int);    // @Deprecated
  public Dimension getMinimumSize();           public Dimension minimumSize();           // @Deprecated
  protected String paramString();
  public AccessibleContext getAccessibleContext();
}
```

Nineteen callable members, of which **seven are AWT-1.0 deprecated aliases**. Nothing else:
no `Document`, no `Caret`, no `tabSize`, no `lineWrap`, no `Action`, no `Highlighter`,
no `EditorKit`. `javax.swing.JTextArea` has all of those plus the line-offset queries;
this class is that surface with the Swing half deleted, exactly as `java.awt.Button`
is `JButton` minus `ButtonModel`.

### Behavioural quirks worth naming

Each of these is read out of the JDK source, and each one is a place where a
"port it like the Swing surrogate" reflex produces the wrong answer.

1. **The deprecated aliases are the *implementations*, not the wrappers.**
   `insert(str,pos)` is a one-liner calling `insertText(str,pos)`; `append` → `appendText`;
   `replaceRange` → `replaceText`; `getPreferredSize()` → `preferredSize()`. The delegation
   direction is the inverse of what "deprecated" suggests, and a migrated AWT-1.0 app calls
   the deprecated names *directly*. **Verdict: keep all seven, `@Deprecated`-annotated,
   with the JDK's delegation direction intact.** Dropping them is a compile error at stage 2,
   which is the whole reason the AWT lane exists.

2. **Constructor arguments are clamped, never rejected.** `TextArea(text, -3, -3, 99)`
   silently yields `rows=0, columns=0, scrollbarVisibility=SCROLLBARS_BOTH`. The
   *setters* `setRows` / `setColumns` throw `IllegalArgumentException` on the same
   negative value. That asymmetry is deliberate in the JDK and is the exact inverse of
   [SD_slabel](../surrogates/decisions.md)'s call for `Label`, where a bad alignment throws
   out of the ctor. **This slice must not copy SD_slabel's throw-in-the-ctor shape.**

3. **`append(null)` appends the four characters `null`.** `insertText` does its arithmetic
   with `text.substring(0,pos) + str + text.substring(pos)` — string concatenation, so a
   null `str` stringifies. Verified headless on JDK 25: `"hello"` + `append(null)` → `"hellonull"`.
   (Swing's `SJTextArea.append` documents null as a no-op — the opposite contract.)

4. **`replaceRange` with `end < start` grows the text instead of throwing.**
   `replaceText("X", 4, 2)` on `"hello"` yields `"hellXllo"` — `substring(0,4) + "X" + substring(2)`
   duplicates the overlap. Swing's `JTextArea.replaceRange` throws `IllegalArgumentException`
   here, and `SJTextArea` follows Swing. Two lanes, two contracts.

5. **Out-of-range offsets surface as `StringIndexOutOfBoundsException`, not `IllegalArgumentException`
   and not `BadLocationException`** — because they come out of `String.substring`, not out of a
   validator. Messages in the table below.

6. **`getScrollbarVisibility()` has no setter.** The policy is fixed at construction. That
   removes a whole class of "does the round-trip survive a re-set" worry, and makes a
   `final` field or an immutable CSS class equally safe.

7. **The default is `SCROLLBARS_BOTH`, i.e. AWT's default TextArea does *not* soft-wrap.**
   A horizontal scrollbar and soft wrapping are mutually exclusive; AWT picks scrolling.
   Vaadin's `TextArea` always soft-wraps — `SJTextArea` documents `lineWrap=false` as
   "observably ignored". So the AWT *default* lands on the one behaviour the Swing surrogate
   gave up on. See *Peer choice*.

8. **The ctor rebinds focus traversal to ctrl-TAB** (`setFocusTraversalKeys` with
   `"ctrl TAB"` / `"ctrl shift TAB"`), because plain TAB must insert a tab character.
   Permanently out per [D_focus_managers](../emulators/decisions.md) — real key dispatch and focus
   cycle roots are tier-1/2 exclusions.

### What it inherits from `TextComponent`

Base analysis lives in [awt-textfield.md](./awt-textfield.md). The four base facts
`TextArea` cannot be designed without:

- **`TextComponent` is `sealed permits TextArea, TextField`,** and has no public
  constructor. So the JDK hierarchy is closed at exactly two leaves — which settles
  the R_leaf_peer_lockdown question below without a search.
- **There is no `Document`.** State is a `String text` field plus the peer's text,
  and `getText()` re-reads the peer on every call (`if (peer != null) text = peer.getText()`).
  The two-layer `Document`↔peer sync that `SJTextArea` + `vaadinx.swing.JTextArea` run
  has nothing to sync here. This is the single biggest simplification in the slice.
  (The emulator still owns `text` as the JDK's field per
  [D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state); the JDK's own
  peer read is that entry's "the JDK sometimes keeps state in its own peer" trap — measure both
  branches.)
- **`setText(null)` normalises to `""`,** in the ctor *and* in the setter. `getText()`
  can therefore never return null — unlike `Button.getLabel()` / `Label.getText()`.
- **`getSelectedText()` returns `""` when nothing is selected**, not null — it is
  literally `getText().substring(start,end)`. `JTextComponentMixin.getSelectedText()`
  returns null, per *Swing's* contract. Another two-lane divergence.
- **`getBackground()` returns `SystemColor.control` when `!editable && !backgroundSetByClientCode`.**
  A base-level quirk with a real rendering consequence; the sibling doc owns it.

### The event surface is JDK-reusable

`java.awt.event.TextEvent(Object source, int id)` and `TextListener` reference
`Object`, not `Component`. Per [D_event_port_policy](../emulators/decisions.md)'s reuse rule ("plus any
event whose source is `Object`") they are **reused unchanged** — no
`vaadinx.awt.event.TextEvent`, and a migrator's `import java.awt.event.TextListener`
compiles untouched. Same status `ActionEvent` has. Nothing to port.

---

## Peer choice

**Chosen: `com.vaadin.flow.component.textfield.TextArea`** — the same host
`SJTextArea` uses. It renders `<vaadin-text-area>` over a slotted HTML `<textarea>`,
extends `TextFieldBase` (so `ValueChangeMode.EAGER` is available, which is what makes
per-keystroke `TextEvent` delivery possible), and owns its own overflow layer.

Rejected alternatives:

- **`SJTextArea` reused as the peer** (the [D_jtextpane](../emulators/decisions.md) `JTextPane`-over-`SJEditorPane`
  precedent). Rejected on SD_slabel's grounds, and harder here than there: `SJTextArea` carries
  `JComponentMixin` (borders + CSS round-trip, client properties, opaque flag), a
  `PlainDocument` with a two-layer sync, `tabSize`/`lineWrap`/`wrapStyleWord`, the caret
  store and the line-offset queries — *none* of which `java.awt.TextArea` has. D_jtextpane earns
  its keep when the reused surrogate holds large machinery the emulator needs (there, the
  RTE HTML codec); here it would put a `JComponent`-level peer under an AWT-level emulator
  and force AWT-only behaviour *into* `SJTextArea` behind "not for JTextArea" guards.
- **A hand-rolled `Component` over a raw `<textarea>` element.** Rejected: no `HasValue`,
  no value synchronisation, no `Focusable`, no theming — and `ComponentMixin`'s focus/key
  wiring keys off Vaadin's notifier interfaces. There is no `NativeTextArea` in
  `flow-html-components` (checked: the package has `Input`, `RangeInput`, `Pre`, no textarea),
  so this really would be from scratch.
- **Wrapping in `SJScrollPane` to get the scrollbars.** Rejected, and the reason is
  already codified: `SJScrollPane.setContent` *forces* `ScrollDirection.NONE` for content
  that owns its own overflow layer, naming `TextArea` explicitly, to prevent a double-scroll
  pathology. AWT's model — a text widget with its own scrollbars — maps onto the browser's
  `<textarea>` **more** directly than Swing's `JTextArea`-inside-a-`JScrollPane` does.
  This is the one place the AWT lane is *easier* than the Swing lane.

### How the scrollbar policy maps

Four AWT values → four CSS states on the slotted `<textarea>`:

| AWT | soft wrap | overflow-x | overflow-y |
|---|---|---|---|
| `SCROLLBARS_BOTH` (0, default) | off | `auto` | `auto` |
| `SCROLLBARS_VERTICAL_ONLY` (1) | on | `hidden` | `auto` |
| `SCROLLBARS_HORIZONTAL_ONLY` (2) | off | `auto` | `hidden` |
| `SCROLLBARS_NONE` (3) | on | `hidden` | `hidden` |

"Soft wrap off" is `white-space: pre` on the textarea (CSS route) or `wrap="off"`
(HTML-attribute route). Two implementation shapes:

1. **Preferred — four host CSS classes + a `@StyleSheet("emul/stextarea.css")`** with
   light-DOM selectors (`vaadin-text-area.emul-awt-scroll-both > textarea { … }`). The
   inner textarea is *slotted*, i.e. light DOM, so no `::part` and no shadow piercing
   is needed. `@StyleSheet` over `@CssImport` per the standing preference, and
   `sjtabbedpane.css` / `sjtogglebutton.css` are the precedent. The decisive advantage:
   the class list is **readable server-side**, so `getScrollbarVisibility()` reconstructs
   from it, the round-trip is lossless over all four values, and R_vaadin_first is satisfied outright
   with no shadow field — exactly SD_slabel's argument for alignment-as-`text-align`.
2. **Fallback — JS on the inner element** via the `whenInputElementReady` helper that
   `JTextComponentMixin` already uses for `setSelectionRange` (it waits on `updateComplete`
   because a fresh peer's `inputElement` is null until first render). Needed if (1) loses a
   specificity fight with the web component's own styles. Cost: the state is no longer
   readable server-side, so `scrollbarVisibility` becomes a stored `final` field.

**The divergence I cannot design away: Vaadin's `TextArea` auto-grows.** `minRows`
defaults to 2 and the field grows with content unless `maxRows` caps it, so a vertical
scrollbar never appears on an uncapped field — whereas AWT's `TextArea` is a fixed box
that scrolls. Mapping `rows` to **both** `setMinRows(rows)` and `setMaxRows(rows)` fixes
the box at `rows` lines and makes overflow scroll, which is what AWT does;
`rows == 0` leaves Vaadin's auto-grow alone. Note this differs from `SJTextArea`, which
sets `minRows` only — correctly, because a Swing `JTextArea` grows inside its
`JScrollPane` too.

**Finding worth carrying back:** if the `white-space: pre` route works in a browser, it
is a **backport candidate for `SJTextArea.setLineWrap(false)`**, which today documents
itself as observably ignored. The AWT slice would be the thing that proves the mechanism.

---

## Surrogate design (`STextArea`)

```java
public class STextArea extends com.vaadin.flow.component.textfield.TextArea
        implements com.vaadin.swingbridge.surrogates.awt.TextComponentMixin   // which extends ComponentMixin
```

**Not `JTextComponentMixin`** — third application of SD_sbutton's rule. That mixin is 791
lines of `Document`, `Caret`, `Keymap`, `Highlighter`, `NavigationFilter`, `EditorKit`
actions and view↔model math, and `java.awt.TextComponent` has none of it. What the
two genuinely share is a text passthrough, an editable flag and a clamped selection pair.

Whether the AWT-level base is a `TextComponentMixin` interface at all is
[awt-textfield.md](./awt-textfield.md)'s call — two consumers (`STextField`,
`STextArea`) with *identical* JDK semantics and a real shared JDK base is a much
stronger case for a mixin than SD_sbutton/SD_slabel faced, so SD_sbutton's "duplicate rather than
fold shallow commonality" objection does not obviously apply. One hard constraint
that doc must weigh, though, and that I hit from this side:

> **An interface cannot declare a `protected` member.** `SButton.processActionEvent` is
> `protected` because `SButton` is a class. If `processTextEvent` lives on a mixin it is
> implicitly `public`, and no implementing class may narrow it back. Either the surrogate's
> hook is public (harmless — the surrogate is Vaadin-shaped per R_vaadin_first, and the *emulator's*
> `processTextEvent` stays `protected` per the JDK; this is the same visibility bend
> `ComponentMixin.validate()` already forces in the opposite direction), or each concrete
> class declares its own protected hook and the mixin cannot be the dispatch funnel.
> Recommend the former: keeping `processTextEvent` the single funnel matters more than
> its modifier on a class no migrator imports.

### R_vaadin_first state partition

**Vaadin-bound, not stored:**

| AWT property | Vaadin property |
|---|---|
| `text` | `getValue()` / `setValue()` — and `setValue(null)` **NPEs** (verified: "throws NullPointerException, if the value is null"), which is moot because AWT normalises null to `""` upstream of us |
| `editable` | `!isReadOnly()` / `setReadOnly(!b)` |
| `scrollbarVisibility` | the host class list (CSS route) — lossless over all four values, so no field |
| `rows` → rendering | `setMinRows` + `setMaxRows` |
| `columns` → rendering | `setWidth(CssConvert.columnsToCssWidth(columns))` — `calc(Nch + 2em)`, the same hint `SJTextField`/`SJTextArea` use |

**Stored, each with its R_vaadin_first justification:**

| field | why it is not a round-trip shadow |
|---|---|
| `rows`, `columns` | They *drive* the peer sizing above, and `0` means "no hint" while Vaadin's `getMinRows()` defaults to `2` — so the AWT value is not reconstructible from the peer. Same justification `SJTextArea` already carries. |
| `TextListener` list | Event delivery. R_vaadin_first names listener lists as UI functionality outright, and there *is* a Vaadin subscription behind it (below), so it is not storage for storage's sake. |
| `selectionStart` / `selectionEnd` | The browser's selection is not a readable Vaadin property; the pair drives a `setSelectionRange` push. Identical justification to `TextStateStore`'s `dot`/`mark` under [SD_caret_selection](../surrogates/decisions.md). If the mixin route is taken these live in an `AwtTextStore` holder mirroring `TextStateStore`; if the duplicate-per-class route is taken they are plain fields. |

Nothing else. In particular there is no `text` shadow, no `caret` object, and — unlike
`SJTextArea` — no `tabSize`/`lineWrap`/`wrapStyleWord`, because the JDK class has no
such properties to round-trip.

### Clash list (all verified by reflection over Vaadin 25.2.3)

| what | shape | resolution |
|---|---|---|
| `getListeners(Class)` | Flow `Component` declares `protected Collection<?> getListeners(Class<? extends ComponentEvent>)`. Same erasure, unrelated return type → the JVM forbids both on one class. | **Drop on the surrogate**, exactly as SD_sbutton did. Consequence: `getTextListeners()` cannot use the JDK's implementation (`return getListeners(TextListener.class)`) and must read the list directly. The emulator inherits a working `getListeners(Class<T>)` from `vaadinx.awt.Component` (which already special-cases `PropertyChangeListener` and otherwise delegates to `listenerList`), so the migrator-facing call site is unaffected. |
| `validate()` | Vaadin `TextArea` declares it `protected` (input validation); `ComponentMixin` declares it `public` (Swing's layout cycle). | Visibility-widening override delegating to `super` — verbatim the `SJTextArea` / `SJComboBox` shape. |
| `getParent()` | Flow returns `Optional<Component>`; AWT returns `Container`. | Pre-existing `ComponentMixin` problem; the surrogate declares nothing. Migrated code reads the parent through the emulator. |
| `getLocale()` | `protected` on Flow `Component`, `public` on AWT `Component`. | `ComponentMixin`'s existing concern. |
| `setEnabled(boolean)` | Reaches `STextArea` from the **`HasEnabled` interface default**, not from a class. | **No redirect needed** — `ComponentMixin extends HasEnabled` makes the mixin's default more specific. Same as `SLabel`; unlike `SButton`, where Vaadin's `Button` *class* declares a concrete `setEnabled`. |
| `setLabel(String)` | `TextFieldBase` declares it public — Vaadin's floating label above the field. | No JDK clash (`java.awt.TextArea` has no `setLabel`), but note the cross-surrogate inconsistency worth a javadoc note: `SButton.setLabel` is the AWT *caption*, `STextArea.setLabel` is a Vaadin decoration. |
| `getText` / `setText` | **Free.** Verified: `TextArea` does *not* implement `HasText` anywhere in its chain. | Declare AWT's names directly over `getValue`/`setValue`. |
| `getRows` / `setRows` / `getColumns` / `setColumns` | **Free.** Vaadin uses `minRows`/`maxRows` and no column concept. | Declare AWT's names. |
| `insert` / `append` / `replaceRange` / `select` / `selectAll` / `getSelectedText` / `getCaretPosition` / `setCaretPosition` / `isEditable` / `setEditable` | **Free** — none exist in the Vaadin chain. | Declare as-is. |
| `getPreferredSize()` / `getMinimumSize()` | `ComponentMixin` already declares both (returning `getSize()`, R_layouts_close_enough dummy bounds). AWT `TextArea` adds `(int,int)` overloads and the deprecated no-arg aliases. | New names, no clash. Delegate; see the WARN section. |
| `scrollToStart()` / `scrollToEnd()` | Vaadin-only extras the surrogate inherits. | Harmless; the emulator does not expose them (R_no_vaadin_in_api limb 1 — no JDK signature claims those names, but there is also no reason to surface them). |
| **numeric constants** | `TextArea.SCROLLBARS_BOTH/VERTICAL_ONLY/HORIZONTAL_ONLY/NONE` are `0/1/2/3` and collide numerically with `java.awt.ScrollPane.SCROLLBARS_AS_NEEDED/ALWAYS/NEVER` = `0/1/2` (verified with `javap -constants`). They are unrelated to `ScrollPaneConstants`' 20–32 range. | SD_slabel's trap, second instance: one constant collision per AWT widget that carries constants. A note in the class javadoc, asserted in both suites. The mapping switch stays its own, not a route through any Swing-keyed helper. |

### Listener wiring (R_vaadin_first + R_callswing_envelope)

One Vaadin subscription drives the whole fan-out — R_vaadin_first's shared-subscription shape,
the same one `SButton` uses:

```
ctor:  setValueChangeMode(ValueChangeMode.EAGER);            // per-keystroke, = AWT's contract
       addValueChangeListener(e -> SHelper.callSwing(
               () -> processTextEvent(new TextEvent(this, TextEvent.TEXT_VALUE_CHANGED))));
```

`processTextEvent` is the single dispatch funnel (SD_sbutton's rule: overriding it and calling
super is AWT's documented interception idiom, so a subclass must see browser-originated
events too). `SHelper.callSwing` per R_callswing_envelope, so a `TextListener` that opens a modal dialog
can park.

**Deliberately not filtered on `isFromClient()`.** That is what makes a *programmatic*
`setText` fire a `TextEvent` too — and Vaadin's own value-equality guard inside
`AbstractField.setValue` reproduces the JDK's `if (peer != null && !text.equals(peer.getText()))`
guard *exactly*: same text in, no event out. (See *Open questions* #4 for why I believe
the JDK does fire here; it is the one behavioural claim in this doc I could not execute.)

No `preventPeerEvents` flag is needed anywhere in this slice. That flag exists to break
an echo *write*; with the text living only in the peer there is no second copy to echo
into, and the listener body writes nothing back.

---

## Emulator design (`vaadinx.awt.TextArea`)

```java
public class TextArea extends vaadinx.awt.TextComponent      // new base, sibling doc
        implements javax.accessibility.Accessible
```

**R_leaf_peer_lockdown lock-down: yes, and the leaf check is unusually clean.** `java.awt.TextComponent`
is `sealed permits TextArea, TextField`, so the public hierarchy is closed at two leaves
and no search is required: nothing in `java.awt` extends `TextArea`. So `vaadinx.awt.TextArea`
omits the protected `(Component peer)` ctor and its root public ctor passes the `STextArea`
directly (the lazy `super(STextArea.class, () -> new STextArea(...))` shape `Label` / `Choice` /
`List` use). Symmetrically, `vaadinx.awt.TextComponent` is
**non-leaf** and keeps the protected `(Component peer)` ctor as its [D_peer_ctor_injection](../emulators/decisions.md)
seam — precisely the `JTextField` → `JPasswordField` shape. (`java.awt.TextArea` is itself
declared `non-sealed`, so migrator subclassing for behaviour keeps working, which is what
R_leaf_peer_lockdown is designed to allow.)

**State is emulator-owned**, per [D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state):
`text`, `rows`, `columns`, `scrollbarVisibility` and the selection pair are the JDK's fields
under the JDK's names, the getters never read the peer, and the surrogate renders them through
`withPeer` flushes (as `vaadinx.swing.JTextArea` now holds its own `rows` / `columns`). A
programmatic `setText` fires its `TextEvent` from the emulator on the caller's thread (that
entry's rule 2); the bridge below relays browser-originated changes only.

**Peer → AWT bridge with source re-binding**, same shape as D_awt_button:

```
surrogate().addTextListener(e -> EHelper.callSwing(
        () -> processTextEvent(new TextEvent(this, e.getID()))));
```

so migrated code casting `(TextArea) e.getSource()` sees the emulator, not the surrogate.
`callSwing` again per R_callswing_envelope — the surrogate's envelope is already open and a nested call
runs inline per [D_callswing_loom](../emulators/decisions.md).

### R_no_vaadin_in_api call-hierarchy hooks — all four must be genuinely on the invoked path

R_no_vaadin_in_api's second limb is the trap this slice is most exposed to, because `TextComponent`
exposes four JDK hooks and three of them are easy to declare and never call.

| hook | who invokes it here |
|---|---|
| `processTextEvent(TextEvent)` | The peer bridge above, on every browser keystroke and every programmatic `setText`. Also the only path `processEvent` routes a `TextEvent` down. **This is the hook a migrator's subclass overrides**, so an unreachable version would be the exact failure D_r12_provenance describes: compiles, looks wired, silently never runs. |
| `processEvent(AWTEvent)` | Declared on `vaadinx.awt.TextComponent`, peeling `TextEvent` off before `super.processEvent(e)` — mirroring the JDK, and mirroring what `vaadinx.awt.Button.processEvent` does for `ActionEvent`. `vaadinx.awt.Component.processEvent` already exists as the routing sink for the mouse/key/focus/hierarchy families, so this is one more branch in an established path. Reachable from user-code `dispatchEvent`/`processEvent` calls in a subclass. |
| `addNotify()` | **Verified reachable**: `vaadinx.awt.Component`'s ctor wires `peer.addAttachListener(e -> EHelper.callSwing(this::addNotify))`. Override, call super, and say in a comment why there is nothing to allocate (the peer is eternal) — the D_awt_button/D_awt_label precedent, and the reason not to just delete the override. |
| `removeNotify()` | **Verified reachable**: the matching `peer.addDetachListener`. The JDK's `TextComponent.removeNotify` pulls `text` / `selectionStart` / `selectionEnd` out of the peer *before* it dies. Ours calls super and documents that the pull is a no-op because the emulator owns that state — there is no peer copy to rescue. |

R_no_vaadin_in_api's first limb (types) is unremarkable here: every declared signature is JDK-shaped
(`java.awt.Dimension`, `java.awt.event.TextEvent`, `String`, `int`), the only
Vaadin-typed accessor in the chain is the SB-Emulators-invented `Component.getPeer`, and this
slice adds no new one.

---

## Exception fidelity (R_match_swing_errors)

Messages are verbatim from JDK 25 — the `IllegalArgumentException` texts read out of
`TextArea.java` / `TextComponent.java`, the `StringIndexOutOfBoundsException` texts
produced by running the equivalent `String.substring` calls.

| input | JDK behaviour | which layer throws |
|---|---|---|
| `setRows(-1)` | `IllegalArgumentException: rows less than zero.` (trailing period is the JDK's) | surrogate validator; emulator delegates |
| `setColumns(-1)` | `IllegalArgumentException: columns less than zero.` | surrogate validator; emulator delegates |
| `new TextArea(t, -3, -3)` | **no throw** — clamps both to `0` | emulator ctor (must *not* copy SD_slabel's throw-in-ctor) |
| `new TextArea(t, r, c, 99)` | **no throw** — silently falls back to `SCROLLBARS_BOTH` | emulator ctor |
| `insert("x", 99)` on 5 chars | `StringIndexOutOfBoundsException: Range [0, 99) out of bounds for length 5` | surrogate |
| `insert("x", -1)` | `StringIndexOutOfBoundsException: Range [0, -1) out of bounds for length 5` | surrogate |
| `replaceRange("x", 99, 2)` | `StringIndexOutOfBoundsException: Range [99, 5) out of bounds for length 5` | surrogate |
| `replaceRange("x", 0, 99)` | `StringIndexOutOfBoundsException: Range [99, 5) out of bounds for length 5` (from the tail `substring(end)`) | surrogate |
| `replaceRange("X", 4, 2)` | **no throw** — `"hello"` → `"hellXllo"` | surrogate |
| `append(null)` | **no throw** — appends the literal `null` | surrogate |
| `setCaretPosition(-1)` | `IllegalArgumentException: position less than zero.` | `TextComponent` base (sibling doc) |
| `select(-5, 999)` | **no throw** — clamps to `[0, length]`, and `end < start` collapses to `start` | `TextComponent` base |
| any ctor, headless | `HeadlessException` (unchecked, extends `UnsupportedOperationException`) | never thrown here; the `throws` clause is kept for signature fidelity, matching `vaadinx.awt.Frame` / `Button` / `Label` |

**The one divergence I would accept knowingly:** the JDK calls `peer.insert(...)` *before*
the `substring` arithmetic that throws, so a real AWT `insert("x", 99)` mutates the widget
**and then** throws. Recommend validating first and throwing before touching the peer —
SD_slabel's throw-before-write guard — and documenting that AWT leaves the text in the widget
where we leave it untouched. Reproducing the JDK's half-applied mutation would be faithful
to a bug.

**A quirk we improve on invisibly:** `insertText` does its arithmetic against the possibly
stale `text` field rather than `getText()`, so in real AWT an insert after the user typed
corrupts the field — invisibly, because the next `getText()` re-reads the peer. We compute
against the live value. Observably identical, so not an R_match_swing_errors case.

---

## Dropped / WARN surface (R_match_swing_errors sub-buckets)

| item | bucket | note |
|---|---|---|
| `getAccessibleContext()` | (b) | WARN + null, as on every other emulator and surrogate. |
| `getInputMethodRequests()` | (b) | Input methods are out. `vaadinx.awt.Component` currently WARNs; **override to `onNoop` + return null**, because null is what the JDK itself returns with no peer — a JDK-legal answer should not spend a WARN, and it keeps the exit gate clean. |
| `enableInputMethods(boolean)` | (b) | `onNoop` already on `Component`; the JDK override only flips an internal `checkForEnableIM` flag. |
| `getPreferredSize(int,int)`, `getMinimumSize(int,int)`, and the four deprecated size aliases | (b) | R_layouts_close_enough dummy bounds — `ComponentMixin.getPreferredSize()` returns `getSize()`. Delegate to it and `onNoop` (DEBUG-level, not a stub WARN), so signature fidelity is kept without a WARN per call. |
| ctrl-TAB focus traversal keys installed by the ctor | (b) | Real key dispatch and focus cycle roots are permanently out per D_focus_managers. |
| low-level `dispatchEvent` / `enableEvents` | (b) | Already stubbed on `vaadinx.awt.Component`; `dispatchEvent` is `public final` there and WARNs. |
| `paint`/`update`/`print(Graphics)` | (b) | User-authored `Graphics` paint is permanently out. |
| horizontal scrolling actually rendering | (a) | Depends on `white-space: pre` / `wrap="off"` surviving inside `<vaadin-text-area>`; not a JDK-contract gap but a Vaadin-behaviour unknown. If it fails, `SCROLLBARS_BOTH` and `SCROLLBARS_HORIZONTAL_ONLY` degrade to soft wrap, i.e. `SJTextArea`'s existing accepted divergence — a (c) drop, not a throw. |
| browser-side caret/selection read-back | (a) | Vaadin exposes no selection property or `selectionchange` event; the Swing lane solved this with held selection reports (`JTextComponentMixin.addSelectionReportListener`, fed into the emulator-owned caret per [D_emulator_caret](../emulators/decisions.md#D_emulator_caret)), so it is implementable rather than blocked — the AWT lane should reuse that protocol rather than re-derive it. Recommend lifting it (and `whenInputElementReady`, a private interface method there) out of `JTextComponentMixin` into a shared `:surrogates` helper, per the "dedup cross-module helpers into the S-prefixed home" rule — the sibling TextField pass's open question 2. |
| `getBackground()`'s `SystemColor.control` for a non-editable field | — | Implementable (`editable` + a `backgroundSetByClientCode` flag); base-level, sibling doc's call. Listed so it is not silently forgotten. |

Nothing in this class lands in (c). That is a consequence of how small the JDK surface is:
there is no `tabSize`, no `lineWrap`, no alignment, no icon — nothing whose only purpose
would be to round-trip through its own getter.

---

## Sampler demo + exit gate

**A new `AwtTextAreaPanel`**, one `SamplerCatalogue` line under category `"AWT"` labelled
`TextArea` — the lane's one-pane-per-AWT-class convention (`AwtButtonPanel`, `AwtLabelPanel`, …).
Each demo exists so the exit gate can assert a WARN-free user path:

- **1a — `TextListener` fan-out.** A `new TextArea("edit me", 5, 30, SCROLLBARS_VERTICAL_ONLY)`
  with a `JLabel` readout showing `getText().length()` and `getCaretPosition()`, driven by
  an `addTextListener`. Karibu can drive it with `_setValue`, which is exactly the
  browser-keystroke path.
- **1b — the mutators a migrated app actually calls.** Swing buttons driving `append`,
  `insert(str, pos)`, `replaceRange`, `selectAll` + a `getSelectedText()` readout (which
  shows AWT's `""`-not-null contract), and `setEditable` toggling.
- **1c — the scrollbar policy, side by side.** Three `TextArea`s of the same size pinned
  to `SCROLLBARS_BOTH` / `VERTICAL_ONLY` / `NONE`, each pre-filled with one very long line
  plus enough short lines to overflow vertically, so the wrap-vs-scroll difference is
  *visible*. This is the demo that carries the browser-verification gate — a server-side
  test would pass with the scrollbars entirely absent, the same blind spot SD_slabel called out
  for alignment on an inline host.

**Exit gate.** A new `AwtTextAreaWarnInventoryTest` holds the pane's user path plus an API
bucket, `inventory_awt_textarea_api_surface`, structured like the other AWT panes': all five ctors
(including the clamping and bad-scrollbars paths), `getRows`/`setRows`/`getColumns`/`setColumns`,
`getScrollbarVisibility`, all three modern mutators **and** all three deprecated aliases,
`getText`/`setText` (including null), `getSelectedText`, `select`/`selectAll`,
`setCaretPosition`/`getCaretPosition`, `isEditable`/`setEditable`,
`addTextListener`/`removeTextListener`/`getTextListeners`, the six size methods,
`addNotify`, `toString`. Minus the expected-WARN `getAccessibleContext`, which stays in the
unit tests. Like the Button and Label buckets this is genuinely exhaustive — the class has
nothing else. `processEvent` / `processTextEvent` are protected and get driven from
`vaadinx.awt.TextAreaTest` instead, same split the Button bucket documents.

The user path: an `_find(STextArea.class, withCount(4))` assertion (one in 1a, three in 1c —
exact, since the pane holds only its own widget's peers), a `_setValue` on 1a's field, and
clicks through 1b's four buttons.

---

## Tests

- `surrogates/src/test/java/com/vaadin/swingbridge/surrogates/STextAreaTest.java`
- `emulators/src/test/java/vaadinx/awt/TextAreaTest.java`

Behaviours that earn a test because they would otherwise regress silently:

**Both suites**
1. All five ctors and the JDK defaults (`rows=0`, `columns=0`, `getScrollbarVisibility()==SCROLLBARS_BOTH`).
2. **The clamp/throw asymmetry** — negative ctor args clamp to 0, `setRows(-1)`/`setColumns(-1)`
   throw with the JDK's exact messages. This is the single most likely thing to be
   "fixed" wrongly by a later reader.
3. Bad `scrollbars` value in the ctor silently becomes `SCROLLBARS_BOTH` (no IAE — the
   inverse of `SLabel`'s alignment contract).
4. The constant-collision trap: `SCROLLBARS_*` are 0/1/2/3, `java.awt.ScrollPane`'s are 0/1/2.
5. `setText(null)` reads back `""` — and asserted as *not* null, unlike `Button.getLabel()` /
   `Label.getText()`.
6. `append(null)` appends the literal `"null"`; `replaceRange("X",4,2)` on `"hello"` gives
   `"hellXllo"`; the three `StringIndexOutOfBoundsException` messages verbatim.
7. `getSelectedText()` with nothing selected returns `""`, not null.
8. Scrollbar policy lands in the host class list / CSS, and round-trips losslessly over
   all four values.
9. `rows` reaching both `minRows` and `maxRows`; `columns` reaching the `calc(Nch + 2em)` width.
10. `getAccessibleContext` WARNs and returns null; a zero-WARN happy path.

**Surrogate-specific**
11. A browser value change fires exactly one `TextEvent` with `source == the STextArea`,
    through `processTextEvent` as the funnel (subclass override sees it).
12. A programmatic `setValue`/`setText` with *different* text fires one `TextEvent`;
    with *identical* text fires none — the Vaadin-equality-guard = AWT-guard claim.
13. Null-listener add/remove are no-ops (AWT's contract), not NPEs.
14. The inherited `ComponentMixin` surface works on a non-`JComponent` host
    (`setBackground`, `setFont`, focus), and `validate()` is callable as public.

**Emulator-specific**
15. R_leaf_peer_lockdown peer type is `STextArea` through a plain instance *and* through a user-code subclass.
16. `processEvent(new TextEvent(...))` routes to `processTextEvent`; a non-`TextEvent`
    falls through to `super`.
17. Peer-originated `TextEvent` arrives re-sourced with `source == the emulator`.
18. `getListeners(TextListener.class)` returns what `addTextListener` registered
    (the surrogate cannot declare that method; the emulator must).
19. An AWT `TextArea` dropped into a Swing `JPanel` inside a `JFrame` renders and
    round-trips — the residue scenario, no `JComponent` in its own chain.
20. `addNotify` / `removeNotify` are reached on attach/detach and are WARN-free.

---

## File checklist

Mirroring `eea1c09` (Button) and `4d3eb27` (Label), plus the two base files the
sibling slice may already have landed:

| file | new/edit |
|---|---|
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/awt/TextComponentMixin.java` | new — **sibling doc owns**; whichever of TextField/TextArea lands first pays for it |
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/internal/AwtTextStore.java` | new, only on the mixin route (mirrors `TextStateStore`) |
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/STextArea.java` | new |
| `surrogates/src/main/resources/META-INF/resources/emul/stextarea.css` | new, only on the CSS route for the scrollbar policy |
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/SHelper.java` | edit, if the selection-report protocol / `whenInputElementReady` is lifted out of `JTextComponentMixin` |
| `surrogates/src/test/java/com/vaadin/swingbridge/surrogates/STextAreaTest.java` | new |
| `surrogates/decisions.md` | new `SD_stextarea` entry |
| `emulators/src/main/java/vaadinx/awt/TextComponent.java` | new — **sibling doc owns**; non-leaf, keeps the D_peer_ctor_injection protected `(Component peer)` ctor |
| `emulators/src/main/java/vaadinx/awt/TextArea.java` | new |
| `emulators/src/test/java/vaadinx/awt/TextAreaTest.java` | new |
| `emulators/decisions.md` | new `D_awt_textarea` entry |
| `sampler/src/main/java/com/vaadin/swingbridge/sampler/AwtTextAreaPanel.java` | new — Demos 1a/1b/1c |
| `sampler/src/test/java/com/vaadin/swingbridge/sampler/AwtTextAreaWarnInventoryTest.java` | new — API bucket + user path |
| `sampler/src/main/java/com/vaadin/swingbridge/sampler/SamplerCatalogue.java` | edit — one `"AWT"` / `TextArea` line |
| `sampler/description.md` | edit — the exit-gate inventory line |
| `CLAUDE.md` | edit — component-surface list gains `vaadinx.awt.TextArea` (+ `TextComponent`) |

---

## Open questions

1. **Does light-DOM CSS on the slotted `<textarea>` actually win?** The whole no-shadow-field
   argument for the scrollbar policy rests on `vaadin-text-area.SB-Emulators-… > textarea { … }`
   reaching and beating the web component's own styles. I did not run a browser. If it
   loses, fall back to the JS route and accept a stored `final` field.
2. **Does `white-space: pre` (or `wrap="off"`) give real horizontal scrolling inside
   `<vaadin-text-area>`,** whose shadow `input-field` part may clip or its own overflow
   handling may fight? Unverified — and it is the interesting question, because a yes is a
   **backport candidate for `SJTextArea.setLineWrap(false)`**, which currently documents
   itself as observably ignored.
3. **Mixin or duplication for the AWT text base?** [awt-textfield.md](./awt-textfield.md)'s
   call. From this side the mixin looks right (identical JDK semantics, a real sealed JDK
   base, two consumers), but the `protected`-on-an-interface constraint above is real and
   forces `processTextEvent` public on the surrogate either way I would resolve it.
4. **Does programmatic `setText` fire a `TextEvent` in real AWT?** The Java-side code fires
   nothing; the *peer* does. I inferred "yes" from the JDK's own comment — *"we do not want
   to post an event if TextArea.setText() replaces text by same text"* — which only makes
   sense if `peer.setText` posts one. I could not run a headful AWT test (no display), and
   the answer may differ between XAWT and the Windows peer. **This is the highest-value
   thing to measure** — under `xvfb-run -a`, as [D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state)'s
   procedure does for AWT widgets; the sibling TextField pass's reading of `XTextFieldPeer`
   (quirk 1 there) points to yes for XAWT. If AWT is actually
   silent, the fix is a one-line `isFromClient()` filter in the surrogate's listener, and
   the "Vaadin's equality guard == AWT's guard" argument above is decoration rather than
   design.
5. **`append(null)` with a live peer.** Headless, the literal `"null"` is appended (verified).
   With a peer, `peer.insert(null, pos)` runs *first* and may NPE inside the native peer
   before the arithmetic. Also unverified headful. Our surrogate has no such split, so we
   would always produce `"null"` — likely right, possibly more forgiving than some platform.
6. **`maxRows = rows` — right call, or should `rows` map to an explicit CSS height?**
   Capping `maxRows` is what makes a vertical scrollbar appear at all, but it also freezes
   the field at exactly `rows` lines, and I have not checked how `<vaadin-text-area>`
   behaves when `minRows == maxRows`.
7. **`getMinRows()` defaults to `2`,** so an AWT `TextArea()` (rows = 0, "peer picks") renders
   two lines tall where a real AWT peer picks something larger. R_layouts_close_enough cosmetic, but it will
   look wrong in the Sampler demo and someone will file it as a bug.
8. **`getInputMethodRequests()` — WARN or silent null?** I argue silent (`onNoop`), because
   null is a documented JDK return. That is a judgement call about what the exit gate should
   be allowed to see, not a fact.
9. **Ordering.** `awt-widgets.md` sizes TextField and TextArea at ~1 session each, but the
   first of the two also pays for `vaadinx.awt.TextComponent` + the surrogate base.
   `TextField` is the cheaper vehicle for that (no scrollbar policy, no rows, no
   auto-grow question), so **TextField first** — which is presumably why the sibling doc exists.

---

## Effort

- **`TextArea` alone, once the `TextComponent` base exists: ~1 session.** The surrogate is
  small (no `Document`, no caret object, no field shadows), the emulator is the JDK's few
  fields and bodies (D_emulator_owned_state), and every mechanism it needs — `ComponentMixin`, `callSwing`, `whenInputElementReady`,
  `CssConvert.columnsToCssWidth`, the peer attach/detach → `addNotify`/`removeNotify` wiring —
  is already built.
- **`TextArea` first, base included: ~1.5–2 sessions**, and the base is the bigger half
  (selection/caret pair, `editable`, the `SystemColor.control` background quirk, the
  `TextListener` plumbing, `processEvent` routing).
- **Plus a browser-verification gate** for the scrollbar policy, on SD_slabel's precedent: the
  server-side tests cannot distinguish "policy applied" from "policy invisible", and this
  slice's entire interesting claim is a rendering one.
