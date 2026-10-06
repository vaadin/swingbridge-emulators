# `java.awt.TextField` — design pass (not planned work)

**Status:** brainstorm, no commitment. Estimated 2026-08-21. Sized in [awt-widgets.md](./awt-widgets.md),
whose status line carries the lane's own: **delayed and paused, 2026-09-22.**

Third widget in the AWT lane after [SD_sbutton](../surrogates/decisions.md#SD_sbutton)/[D_awt_button](../emulators/decisions.md#D_awt_button) (`Button`) and [SD_slabel](../surrogates/decisions.md#SD_slabel)/[D_awt_label](../emulators/decisions.md#D_awt_label) (`Label`), and the first that is **not** a re-run of the same shape at the same cost: it drags in the abstract base `java.awt.TextComponent`, a listener type nothing in the repo has ported (`TextListener`), a selection/caret surface with no Vaadin counterpart, and one genuinely hard problem (`setEchoChar`).

**This file owns the `java.awt.TextComponent` analysis.** A sibling `ideas/awt-textarea.md` would reference it rather than repeat it — `TextArea` is `TextComponent` plus rows/columns/append/insert/replaceRange and a scrollbar policy, so everything below the "Surrogate design" heading is shared.

## What the JDK classes actually are

`java.awt.TextComponent`, public surface (`javap`, trimmed; package-private members `constructComponentName` / `eventEnabled` / `areInputMethodsEnabled` omitted — invisible to migrated code):

```
public void enableInputMethods(boolean);
public java.awt.im.InputMethodRequests getInputMethodRequests();
public void addNotify();                    public void removeNotify();
public synchronized void setText(String);   public synchronized String getText();
public synchronized String getSelectedText();
public boolean isEditable();                public synchronized void setEditable(boolean);
public java.awt.Color getBackground();      public void setBackground(java.awt.Color);
public synchronized int getSelectionStart(); public synchronized void setSelectionStart(int);
public synchronized int getSelectionEnd();   public synchronized void setSelectionEnd(int);
public synchronized void select(int, int);   public synchronized void selectAll();
public synchronized void setCaretPosition(int); public synchronized int getCaretPosition();
public synchronized void addTextListener(TextListener);
public synchronized void removeTextListener(TextListener);
public synchronized TextListener[] getTextListeners();
public <T extends EventListener> T[] getListeners(Class<T>);
protected void processEvent(java.awt.AWTEvent);
protected void processTextEvent(java.awt.event.TextEvent);
protected String paramString();
public javax.accessibility.AccessibleContext getAccessibleContext();
```

`java.awt.TextField` adds:

```
public TextField() / (String) / (int) / (String, int)  throws HeadlessException;
public void addNotify();
public char getEchoChar();  public void setEchoChar(char);
public synchronized void setEchoCharacter(char);          // @Deprecated; the actual impl
public void setText(String);                             // overrides, adds replaceEOL + invalidate
public boolean echoCharIsSet();
public int getColumns();    public void setColumns(int);
public java.awt.Dimension getPreferredSize(int) / preferredSize(int) / getPreferredSize() / preferredSize();
public java.awt.Dimension getMinimumSize(int)   / minimumSize(int)   / getMinimumSize()   / minimumSize();
public synchronized void addActionListener(ActionListener) / removeActionListener / getActionListeners;
public <T extends EventListener> T[] getListeners(Class<T>);
protected void processEvent(AWTEvent);  protected void processActionEvent(ActionEvent);
protected String paramString();
public javax.accessibility.AccessibleContext getAccessibleContext();
```

`java.awt.event.TextEvent` is `TextEvent(Object source, int id)` + `paramString()`, with `TEXT_FIRST == TEXT_LAST == TEXT_VALUE_CHANGED == 900`. `TextListener` is one method, `textValueChanged(TextEvent)`. **Both reuse unchanged from the JDK** — their source is `Object`, so [D_event_port_policy](../emulators/decisions.md)'s reuse limb applies exactly as it does for `ActionEvent`; no `vaadinx.awt.event.TextEvent` is needed and none should be created.

### Behavioural quirks worth naming

Read from the JDK sources (`$JAVA_HOME/lib/src.zip`) and, for the event-posting question, from `sun.awt.X11.XTextFieldPeer`. **The brief's suspicion is half-right and the half that's wrong is the load-bearing one:** AWT's setters are silent about `PropertyChangeEvent`s (there are none anywhere in these two classes), but `setText` is *not* silent about `TextEvent`.

1. **`setText` fires a `TextEvent`.** `TextComponent.setText` pushes to the peer only when the text actually changed (`if (peer != null && !text.equals(peer.getText()))`, with an explicit comment saying an unchanged text must not post an event), and `XTextFieldPeer.setXAWTTextField` posts exactly one `TEXT_VALUE_CHANGED` per push — it detaches its own `DocumentListener` around the inner `JTextField.setText` precisely so the remove+insert pair *coalesces into one* event. The `firstChangeSkipped` guard suppresses the one push that happens while the peer is being created. So the faithful contract is: **one `TextEvent` per text-changing `setText`, none for a no-op `setText`, none from the constructor.** This is the single most important behavioural fact in this document, and it is the opposite of the "AWT setters are silent" prior.
2. **Every keystroke fires a `TextEvent` too** — `insertUpdate` / `removeUpdate` / `changedUpdate` on the peer's document each post one.
3. **`null` text is normalised to `""`, in the constructor and in `setText`.** `TextComponent(String text) { this.text = (text != null) ? text : ""; }` and `setText(String t) { text = (t != null) ? t : ""; }`. **There is no null round-trip to preserve** — `new TextField(null).getText()` is `""`, not null, unlike `Button.getLabel()` / `Label.getText()`.
4. **`TextField.setText` and the ctor run `replaceEOL`** — every `System.lineSeparator()` and every `"\n"` becomes a single space. `new TextField("a\nb").getText()` is `"a b"`. `replaceEOL(null)` returns null and is then normalised to `""` upstream.
5. **`TextField(String text)` derives columns from the text**: `this(text, text != null ? text.length() : 0)`. `new TextField("hello").getColumns() == 5`; `new TextField("hello", 0).getColumns() == 0`. Migrated code that constructs `new TextField("initial value")` gets a 13-column field and will notice if we return 0.
6. **The ctor clamps a negative column count; the setter throws.** `TextField(String, int)` does `this.columns = (columns >= 0) ? columns : 0`, while `setColumns(int)` throws `IllegalArgumentException("columns less than zero.")`. Same asymmetry as `setCaretPosition`, below — AWT is inconsistent here and we have to be inconsistent with it.
7. **`setCaretPosition` throws below zero, clamps above the length.** `IllegalArgumentException("position less than zero.")` for negatives; `position > getText().length()` is silently reduced. Swing's `JTextComponent.setCaretPosition` throws on both ends — so the AWT message *and* the AWT tolerance both differ from what [SD_caret_selection](../surrogates/decisions.md#SD_caret_selection) implements today.
8. **`getSelectedText()` returns `""` when nothing is selected** — it is literally `getText().substring(getSelectionStart(), getSelectionEnd())`. Swing's returns **null**, which is what `JTextComponentMixin.getSelectedText()` implements. A shared implementation would be wrong for one of the two layers.
9. **`select` / `setSelectionStart` / `setSelectionEnd` / `selectAll` never throw** — every out-of-range argument is clamped silently, and `end < start` collapses to `start`. `setSelectionStart`/`setSelectionEnd` are routed through `select` so the clamp policy is single-sourced; worth copying that structure.
10. **`getBackground()` lies for non-editable fields.** `if (!editable && !backgroundSetByClientCode) return SystemColor.control;` — the flag is set by `setBackground`, not by the CSS write, so it is real JDK state and not reconstructible from the peer.
11. **The constructor sets the cursor** to `Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)`. Free to reproduce — `ComponentMixin.setCursor` maps it to CSS `cursor: text`.
12. **`setEchoCharacter(char)` is the implementation and `setEchoChar(char)` the delegate**, not the other way round; the deprecated method is the one a subclass would override. Neither fires anything, and both no-op when the char is unchanged. `echoCharIsSet()` is `echoChar != 0`.
13. **The `ActionEvent` from Enter carries the field's text as its action command** — `XTextFieldPeer` builds `new ActionEvent(target, ACTION_PERFORMED, getText(), when, modifiers)`. `java.awt.TextField` has **no `setActionCommand`**, so unlike `java.awt.Button` there is no command to fall back from; the command *is* the text, always.
14. **`TextComponent` is `sealed … permits TextArea, TextField`** and its only constructor is package-private. `TextField` is declared `non-sealed`, so user code *can* subclass `TextField` but *cannot* subclass `TextComponent`. That shapes the R_leaf_peer_lockdown verdict below.
15. **`removeNotify` snapshots off the dying peer** (`text = peer.getText(); selectionStart = …; selectionEnd = …`) before calling super. Structurally interesting, functionally moot for us — see the emulator section.
16. **`processTextEvent` is gated.** The JDK only reaches it when `eventEnabled` says text events are on, i.e. a `TextListener` is registered or `enableEvents(TEXT_EVENT_MASK)` was called; its own javadoc says so. Same for `processActionEvent`. Reproducing the gate matters for R_no_vaadin_in_api limb 2 — see below.

## Peer choice

**`com.vaadin.flow.component.textfield.TextField`** — the same host `SJTextField` uses.

Rejected alternatives:

- **`PasswordField`** — the only Vaadin component that masks. Rejected as the *default* host: an AWT `TextField` starts unmasked and `setEchoChar` can arrive at any time, so a permanently-masked host is wrong for the overwhelmingly common case, and `setRevealButtonVisible` is a per-viewer click rather than server-side state.
- **Ctor-time peer dispatch** in the [D_jformattedtextfield](../emulators/decisions.md#D_jformattedtextfield) `FormattedFieldStrategy` shape (`TextField` or `PasswordField` depending on echo) — impossible: `echoChar` is not a constructor argument in AWT, so the dispatch has nothing to read. Re-creating the peer on the first `setEchoChar` is out under [D_peer_ctor_injection](../emulators/decisions.md#D_peer_ctor_injection)/R_leaf_peer_lockdown, which pin one peer per instance for its whole life.
- **A raw `Div` + `<input>`** (the `Box` / `JViewport` treatment) — rejected: unlike those, an AWT text field has real behaviour, and hand-rolling an input throws away `TextFieldBase`'s value-change plumbing, `KeyNotifier`, focus and the whole `ComponentMixin` styling path.
- **`EmailField` / `IntegerField` / `NumberField`** — not applicable; AWT `TextField` is untyped.

### The `echoChar` problem, and the proposed mechanism

Masking is the one place where this slice can produce a **security regression rather than a cosmetic one**: a migrated login dialog calling `setEchoChar('*')` that renders cleartext is not an R_vaadin_first "minor round-trip loss", it is a password on screen. Drop-and-WARN is therefore not an acceptable resting place here even though R_vaadin_first sub-bucket (c) would otherwise nominate it.

`SJPasswordField` is not the precedent it looks like: Swing's `JPasswordField` is *always* masked, so it peers on `PasswordField` and only has to WARN about the *unmasking* direction (`echoChar = 0`) and silently accept a wrong glyph. AWT needs the toggle in both directions on one host.

Proposed: **set `-webkit-text-security` on the peer's inner input element**, through the same `whenInputElementReady` shape `JTextComponentMixin` already uses for `setSelectionRange` (SD_caret_selection):

```
i.style.webkitTextSecurity = <echo set ? 'disc' : ''>
```

Why this over the alternatives:

- It is a *style* toggle, so it flips in both directions from server state, needs no attribute plumbing, and cannot be undone by the web component re-rendering its value.
- It leaves `<input type="text">` alone, so no browser password manager wakes up and no autofill heuristics change — a real hazard of the type-flipping alternative.
- The masked glyph is the browser's disc, not the requested char. That *is* an R_vaadin_first loss, and it is the same loss `SJPasswordField` already accepts and documents under "Glyph".

Two alternatives, both worse but worth recording: flipping `i.type` between `text` and `password` (works, but drags in password-manager and autofill behaviour and fights the component on re-render), and a global `@StyleSheet` rule plus a host attribute (`emul/stextfield.css`, the pattern `sjtabbedpane.css` / `swindow.css` already use — declarative and JS-free, but only if Vaadin 25's `<input slot="input">` really is light-DOM-reachable from document CSS, which is unverified). **Browser verification is required before either is chosen** — see Open questions.

## Surrogate design (`STextField`)

```
com.vaadin.swingbridge.surrogates.STextField
    extends com.vaadin.flow.component.textfield.TextField
    implements com.vaadin.swingbridge.surrogates.awt.TextComponentMixin      // new; extends ComponentMixin
```

Naming per [SD_naming](../surrogates/decisions.md#SD_naming): `S` + AWT short name for the concrete, `*Mixin` + the JDK base-class name for the interface, in `com.vaadin.swingbridge.surrogates.awt` next to `ComponentMixin` / `ContainerMixin`.

### Why a new mixin, and why that is *not* SD_sbutton/SD_slabel's anti-pattern

SD_sbutton and SD_slabel both refused a shared base (`AbstractButtonMixin`, `SJLabel`) because the commonality was shallow — ~40 and ~10 lines respectively. That reasoning does not transfer here, and it is worth being explicit about why, because the superficial pattern-match says "duplicate it again":

- The shared surface is **the JDK's own base class**: ~20 public methods of text / editable / selection / caret / listener state machinery that `java.awt.TextArea` inherits *identically*. There is nothing shallow about it, and the second consumer is named and imminent (`STextArea`, `ideas/awt-textarea.md`).
- The repo's precedent for exactly this is `JTextComponentMixin`, which shipped with one consumer (`SJTextField`) and now has three. `ContainerMixin` is the same shape one level up.

Rejected: **reusing `JTextComponentMixin`**. It extends `JComponentMixin` and would graft `Document` + `DocumentListener` + `Caret` + `CaretListener` + `Highlighter` + `Keymap` + `NavigationFilter` + `margin` + `dragEnabled` + `dropMode` + borders + client properties + the opaque flag + `updateUI`/`setUI` onto a surrogate for a JDK class that has **none of them** — the same call SD_sbutton made against `AbstractButtonMixin`, for the same reason, just with a much longer list. It would also be wrong in two places rather than merely oversized: its `getSelectedText()` returns null where AWT returns `""`, and its `setCaretPosition` throws where AWT clamps.

Rejected: **putting everything on `STextField` and lifting it when `STextArea` lands.** The lift would be a rewrite of a landed file, and `TextArea` inherits the surface verbatim, so the "duplicate rather than fold shallow-commonality shells into a base" instruction does not apply.

### R_vaadin_first state partition

Vaadin-bound, nothing stored:

| property | binding | note |
|---|---|---|
| `text` | `getValue()` / `setValue(t == null ? "" : t)` | **lossless** — AWT's own contract normalises null to `""`, so unlike `SButton.label` / `SLabel.text` there is no R_vaadin_first loss |
| `editable` | `!isReadOnly()` / `setReadOnly(!b)` | `JTextComponentMixin` precedent |
| `enabled`, `foreground`, `background`, `font`, `cursor`, `name`, `tooltip`, focus | `ComponentMixin` | free |

Stored, with the R_vaadin_first justification for each:

| field | home | why it is legitimate |
|---|---|---|
| `selectionStart` / `selectionEnd` (dot / mark) | `AwtTextStateStore` | Vaadin surfaces **no** selection or caret property — verified: no `select`, `selectAll` or `setSelection*` anywhere in `vaadin-text-field-flow` 25.2.3. The pair reaches the browser as `setSelectionRange` on the inner input, exactly SD_caret_selection's shape, so it drives real UI behaviour and is not a getter shadow |
| `textListeners` | `AwtTextStateStore` | listener list behind one Vaadin `ValueChangeListener` subscription — R_vaadin_first's sanctioned shared-subscription shape, same as `SButton`'s `ActionListener` list behind `ClickNotifier` |
| `actionListeners` | `STextField` (concrete) | `TextArea` has no `ActionListener` surface, so this stays off the mixin — the same call `SJTextField` makes for the same reason |
| `echoChar` | `STextField` | drives the *rendering* (the mask style write) and `echoCharIsSet()`; UI functionality, not round-trip |
| `columns` | `STextField` | **the weakest justification in the class, stated honestly.** It exists so `getColumns()` answers, and its only render effect is `setWidth(CssConvert.columnsToCssWidth(n))` → `calc(Nch + 2em)`. Parsing that back out of the CSS would technically remove the field, and would be a worse trick than the field. Precedent: `SJTextField` and `SJPasswordField` both store it |

Deliberately **not** stored on the surrogate: `backgroundSetByClientCode`. That flag only changes what a getter returns, which is precisely R_vaadin_first's shadow-cache prohibition — it lives on the emulator, where JDK-state fidelity is the rule (R_swing_is_truth). See below.

### Clash list

Verified against `javap` of Vaadin 25.2.3 `TextField` / `TextFieldBase` / flow-server `Component`:

1. **`<T extends EventListener> T[] getListeners(Class<T>)` — dropped from the surrogate.** `com.vaadin.flow.component.Component` declares `protected Collection<?> getListeners(Class<? extends ComponentEvent>)`: same erasure, unrelated return type, and *narrower* visibility. Two of the three JVM rules broken at once. Exactly the clash SD_sbutton records for `SButton` and SD_sjbutton for `getIcon()`. `getTextListeners()` / `getActionListeners()` cover both listener families the class owns, and the emulator inherits a working `getListeners` from `vaadinx.awt.Component` — which is where migrated code calls it.
2. **`validate()` — visibility-widening override required.** Vaadin's `TextField` declares `protected void validate()` (input validation), `ComponentMixin` declares `public void validate()` (Swing's layout cycle). A class cannot inherit the public interface default *through* a protected class method. `STextField` needs the same one-line `@Override public void validate() { super.validate(); }` `SJTextField` / `SJPasswordField` / `SJComboBox` all carry.
3. **`getWidth()` / `getHeight()` — pre-existing gap, inherited.** AWT returns `int`, `HasSize` returns `String`. `ComponentMixin` already declines to declare them (its javadoc names the conflict); `STextField` inherits that gap rather than introducing a new one. Not new, but the exit-gate driver must not call them expecting AWT semantics.
4. **`setEnabled` — no redirect needed, unlike `SButton`.** Vaadin declares it only as `HasEnabled`'s interface default, not as a class method anywhere in `TextField`'s chain, so `ComponentMixin extends HasEnabled` makes the mixin's default the more specific one. (`SButton` needs its redirect because Vaadin's `Button` *class* declares a concrete `setEnabled`.) Evidence: `SJTextField` compiles today with no such override.
5. **`getText()` / `setText(String)` — free.** `TextFieldBase` does **not** implement `HasText`; verified from its `javap` interface list. Had it done so, `setText` would have written the host's light-DOM text content instead of the value, which would have been a silent and very confusing bug.
6. **`setLabel(String)` from `HasLabel` — a nominal trap, not a compile clash.** `java.awt.TextField` has no label, so nothing collides; but `STextField` inherits a `setLabel` that means "Vaadin's floating field label" while three files away `SButton.setLabel` means "the button's caption". Worth a javadoc note. This is the same family of trap as SD_slabel's constant collision, in nominal rather than numeric form.
7. **Numeric constants: no collision this time.** `java.awt.TextField` and `java.awt.TextComponent` declare **no public constants at all** (unlike `java.awt.Label`), and `TextEvent`'s three are reused from the JDK rather than redeclared. SD_slabel's "expect one collision per AWT widget carrying constants" simply does not fire here — stated as an explicit negative finding so the next slice does not go looking for one.
8. Harmless inherited noise with no AWT counterpart: `getTitle`/`setTitle`, `setRequired`, `setMaxLength`/`setMinLength`, `setPattern`, `setAutoselect`, `setI18n`, `getEmptyValue`, `focus()`/`blur()`. None shadows an AWT signature.

### Listener wiring (R_vaadin_first + R_callswing_envelope)

Two Vaadin subscriptions, both installed from the constructor, both in R_vaadin_first's shared-subscription shape and both wrapped in `SHelper.callSwing` per R_callswing_envelope:

- **`ValueChangeMode.EAGER` + `addValueChangeListener` → `processTextEvent(new TextEvent(this, TEXT_VALUE_CHANGED))`.** One keystroke, one `TextEvent`, matching quirk 2. `EAGER` is what `JTextComponentMixin` already sets for the same reason.
- **`addKeyPressListener(Key.ENTER, …)` → `processActionEvent(new ActionEvent(this, ACTION_PERFORMED, getText(), …))`** — command is the text, per quirk 13. Lives on `STextField`, not the mixin, because Enter in a `TextArea` inserts a newline (the same reason `SJTextField.installEnterWiring` is not in `JTextComponentMixin`).
- **Programmatic `setText`** fires a `TextEvent` too, once, only when the text actually changed, and never from the constructor (quirk 1). The `preventPeerEvents` guard has to be shaped so that the *`setValue` echo* is suppressed while the *deliberate* single event is fired — i.e. fire from `setText` itself and suppress the value-change listener, rather than letting the listener fire and hoping it fires once.
- **`processTextEvent` / `processActionEvent` are the single funnels**, both reachable from a subclass, matching SD_sbutton's call on `SButton.processActionEvent`.
- **Selection read-back** — install the browser's selection reports **eagerly in the constructor**: AWT has no `CaretListener` to hang a lazy install on, and without them `getSelectedText()` cannot see a selection the user made with the mouse, which is precisely the read AWT code performs. The held, debounced reports `JTextComponentMixin.addSelectionReportListener` sends ([SD_caret_selection](../surrogates/decisions.md#SD_caret_selection), [D_emulator_caret](../emulators/decisions.md#D_emulator_caret)) cost no round trip per caret move, which is what makes eager install cheap — `vaadinx.swing.text.JTextComponent` installs them from its constructor the same way. (The JS itself — the `emul-selection` custom event and its client-side dedupe — is the one piece I would rather not duplicate; see Open questions.)

## Emulator design (`vaadinx.awt.TextField`)

```
vaadinx.awt.TextComponent extends vaadinx.awt.Component implements javax.accessibility.Accessible
vaadinx.awt.TextField     extends vaadinx.awt.TextComponent
```

**R_leaf_peer_lockdown verdict — lock down `TextField`, keep the seam on `TextComponent`.**

- `java.awt.TextField` is `non-sealed` and **no public class in `java.awt` extends it** (`TextArea` extends `TextComponent`, not `TextField`). It is a leaf in the public hierarchy, so R_leaf_peer_lockdown applies: no protected `(Component peer)` ctor, and all four public ctors reach the lazy `super(STextField.class, STextField::new)` shape `Label` / `Choice` / `List` use.
- `vaadinx.awt.TextComponent` is a non-leaf and needs the D_peer_ctor_injection peer seam for `TextField` and a future `TextArea`. Proposal: make that ctor **package-private**, mirroring the JDK's own package-private `TextComponent(String)` — both subclasses live in `vaadinx.awt`, so they reach it, and user code cannot subclass `TextComponent`, which is exactly the JDK's constraint (there, expressed as `sealed`). Alternative: `protected` per R_leaf_peer_lockdown's literal non-leaf wording, or a faithful `sealed … permits TextField, TextArea`, which costs a second edit to the `permits` clause when `TextArea` lands. Judgement call; flagged in Open questions.

**State is emulator-owned**, per [D_emulator_owned_state](../emulators/decisions.md#D_emulator_owned_state): `text`, `editable`, `selectionStart` / `selectionEnd`, `echoChar` and `columns` are the JDK's fields under the JDK's names, the getters never read the peer, and the surrogate renders them through `withPeer` flushes (as `vaadinx.swing.JPasswordField` now holds its own `echoChar` and `JTextField` its own `columns`). A programmatic `setText` fires its `TextEvent` from the emulator on the caller's thread (that entry's rule 2), so the peer bridge below relays browser-originated changes only. The selection pair is fed by the browser's held selection reports, as [D_emulator_caret](../emulators/decisions.md#D_emulator_caret) feeds `vaadinx.swing.text.JTextComponent`'s caret.

**One JDK field worth naming: `backgroundSetByClientCode`** (quirk 10). `vaadinx.awt.Component` already keeps its own `background` field and walks parents when it is null, so the override is the JDK's shape verbatim:

```
public Color getBackground() {
    if (!isEditable() && !backgroundSetByClientCode) return SystemColor.control;
    return super.getBackground();
}
public void setBackground(Color c) { backgroundSetByClientCode = true; super.setBackground(c); }
```

This is R_swing_is_truth state on the layer that owns JDK state, which is why it must *not* be on the surrogate — there it would be the shadow-cache R_vaadin_first forbids.

**Free faithfulness:** the ctor calls `setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR))` (quirk 11), which `ComponentMixin.setCursor` turns into CSS `cursor: text`.

**Peer → AWT bridge, with source re-binding.** The D_awt_button shape, twice: the emulator registers a `TextListener` and an `ActionListener` on its own surrogate, and rebuilds each event with `source == this` so migrated code casting `(TextField) e.getSource()` sees the emulator, not the peer. `EHelper.callSwing` again per R_callswing_envelope — nested calls run inline ([D_callswing_loom](../emulators/decisions.md#D_callswing_loom)). Both rebuilt events route through `processEvent`, not straight to the listener list.

**R_no_vaadin_in_api limb 2 — the hooks that must be on the invoked path.** Four, and this is the limb the last sweep found four violations of, so it is worth being explicit:

| hook | must be reached by | shape |
|---|---|---|
| `processEvent(AWTEvent)` | the peer bridge, and a user `processEvent` call from a subclass | `TextField.processEvent` peels `ActionEvent` and delegates the rest to `TextComponent.processEvent`, which peels `TextEvent` and delegates to `Component.processEvent` — the JDK's exact two-level chain |
| `processTextEvent(TextEvent)` | keystrokes **and** programmatic `setText` | the single funnel to `TextListener`s |
| `processActionEvent(ActionEvent)` | Enter | the single funnel to `ActionListener`s |
| `addNotify()` / `removeNotify()` | already funnelled by `vaadinx.awt.Component` from the peer's attach/detach listeners | override → `super`, per the JDK's structure; keep the override even though it adds nothing, as `Button` and `Label` both do |

On the `eventEnabled` gate (quirk 16): the JDK does not call `processTextEvent` unless a `TextListener` is registered. Reproducing the gate means a subclass that overrides `processTextEvent` and registers no listener sees nothing — which is faithful, and is what the JDK's own javadoc promises. The alternative (always dispatch; let the funnel's null-listener check no-op) is more generous than the JDK and makes the override always reachable, which R_no_vaadin_in_api limb 2 arguably prefers. Recommendation: **reproduce the gate**, since limb 2 asks for "actually called by the path that calls it in the JDK", and the JDK's path is gated. Flagged in Open questions because it cuts both ways.

`removeNotify`'s JDK snapshot (quirk 15) has nothing to do: the emulator owns text and selection, so there is no peer copy to rescue before detach. Override, call super, one comment saying so — the shape the memory rule about not dropping overrides asks for.

**Not overridden:** `enableInputMethods` and `getInputMethodRequests` already exist on `vaadinx.awt.Component` as a noop and a WARN respectively; `TextComponent`'s JDK overrides are pure input-method machinery.

## Exception fidelity (R_match_swing_errors)

| input | JDK behaviour | layer that throws |
|---|---|---|
| `setColumns(-1)` | `IllegalArgumentException("columns less than zero.")` | both — surrogate validates before writing CSS, emulator validates before storing (the doubled guard `JTextField`/`SJTextField` already use, so a surrogate used directly is as safe as through the emulator) |
| `new TextField("x", -1)` | **no throw**; clamps to 0 | emulator ctor + surrogate ctor (clamp, do not reuse the setter's validator) |
| `setCaretPosition(-1)` | `IllegalArgumentException("position less than zero.")` | mixin. **Note the message differs from Swing's** — `JTextComponentMixin` uses `"setCaretPosition: bad position: N (document length L)"`; AWT's is the shorter string, and copying Swing's here would be a fidelity bug |
| `setCaretPosition(len + 5)` | **no throw**; clamps to `len` | mixin (Swing throws here — do not share the validator) |
| `select(-5, 999)` | no throw; `start→0`, `end→len`, `end<start → start` | mixin |
| `setSelectionStart(oob)` / `setSelectionEnd(oob)` | no throw; routed through `select` | mixin |
| `selectAll()` on empty text | no throw; `0..0` | mixin |
| `getSelectedText()` with no selection | `""` — **not** Swing's null | mixin |
| `setText(null)` | stored as `""`; `getText()` → `""`; no exception | mixin |
| `new TextField(null)` | `getText()` → `""`, `getColumns()` → 0 | emulator ctor |
| `new TextField("a\nb")` | `getText()` → `"a b"` (`replaceEOL`) | emulator ctor (and `setText`) |
| `setEchoChar(0)` | unmasks; no exception | surrogate |
| `addTextListener(null)` / `addActionListener(null)` | silent no-op (`AWTEventMulticaster.add` tolerates null) | both — an `EventListenerList` would store the null and NPE at dispatch, the same trap `SButton` guards |
| `getListeners(null)` | JDK NPEs | emulator (inherited `Component.getListeners` returns null defensively — pre-existing deliberate divergence, see its comment) |
| ctor `HeadlessException` | unchecked, never thrown | clause kept for signature fidelity, as `vaadinx.awt.Frame` / `Button` / `Label` do |

No case here is a candidate for R_match_swing_errors's major-gap throw list; the `echoChar` security concern is addressed by implementing masking, not by throwing.

## Dropped / WARN surface (R_match_swing_errors sub-buckets)

- **(a) blocked-upstream — selection/caret read-back.** Vaadin surfaces no selection or caret property and no `selectionchange` server-side event (verified against the whole `vaadin-text-field-flow` module). Our workaround is the held selection reports on the inner input (SD_caret_selection / D_emulator_caret); a first-class Vaadin selection API would replace it. Until then, `getCaretPosition()` read by code the browser did not trigger (a Timer, a worker) can be up to a second stale — D_emulator_caret's accepted limitation.
- **(a) blocked-upstream — the echo *glyph*.** No Vaadin API and no CSS property lets the mask character be chosen; `-webkit-text-security` draws a disc. Masked-vs-unmasked is honoured, the glyph is not. Silent (documented), not a WARN — same call `SJPasswordField` makes for the identical divergence.
- **(b) permanently deferred — `getAccessibleContext()`.** WARN + null, as everywhere.
- **(b) permanently deferred — input methods.** `enableInputMethods` (noop) and `getInputMethodRequests` (WARN + null) on `vaadinx.awt.Component`. Consequence for the exit gate: `getInputMethodRequests` must stay out of the API driver, like `getAccessibleContext`.
- **(b) permanently deferred — low-level dispatch.** `dispatchEvent` stays stubbed on `vaadinx.awt.Component`, and `enableEvents` is a documented no-op per [D_frameinit_shape](../emulators/decisions.md#D_frameinit_shape), so `enableEvents(AWTEvent.TEXT_EVENT_MASK)` cannot switch text events on without a listener. Named because it is the one way the `eventEnabled` gate above is observably narrower than the JDK's.

  **Tripwire for whoever implements point 16's gate:** a listener-gated `processTextEvent` turns `enableEvents`' no-op into a silently dropped request — recorded in [D_frameinit_shape](../emulators/decisions.md#D_frameinit_shape) § `Q_enableevents_meaningful`, which names the two ways out (model the mask, or revert `enableEvents` to `onUnimplemented` at a WARN per window). Cheapest resolution if it comes to this: gate on the listener list only (as the bullet above already describes) and leave `enableEvents` unable to widen it, which is the *current* divergence made explicit rather than a new one.
- **(b) permanently deferred — `paramString()` byte fidelity.** Debug-only; we reproduce the shape (`,text=…`, `,editable`, `,selection=s-e`, `,echo=c`) and not the `Component` prefix, since `vaadinx.awt.Component.paramString` returns `""` by design.
- **(c) R_vaadin_first drop-and-WARN — `getPreferredSize(int)` / `preferredSize(int)` / `getMinimumSize(int)` / `minimumSize(int)`.** The JDK computes these from the native peer's `FontMetrics`; we have R_layouts_close_enough dummy bounds. Proposal: return the same `Dimension` the no-arg overloads return and **do not WARN** — `ComponentMixin.getPreferredSize` does not WARN either, and a WARN here would either evict these four methods from the exit-gate driver or make the gate un-passable. Documented as an R_layouts_close_enough divergence instead.
- **(c) — non-editable rendering.** The `getBackground()` → `SystemColor.control` *value* is reproduced (emulator, above); the *paint* is not — Vaadin renders its own readonly styling rather than AWT's control-grey. Cosmetic, R_layouts_close_enough.
- **(c) — `getName()` default.** AWT lazily assigns `"textfield0"`, `"textfield1"`, … via the package-private `constructComponentName`; `NameStore` starts null. Pre-existing for `Button` (`"button0"`) and `Label` (`"label0"`); noted, not new.
- **Negative findings, so nobody goes looking:** there are no `PropertyChangeEvent`s to reproduce (AWT fires none from any setter on either class), and no numeric constants to collide with `SwingConstants`.

## Sampler demo + exit gate

**A new `AwtTextFieldPanel`**, one `SamplerCatalogue` line under category `"AWT"` labelled `TextField` — the lane's one-pane-per-AWT-class convention (`AwtButtonPanel`, `AwtLabelPanel`, …) — in the established shape: a `demoSection` wrapper, `BorderLayout` sub-panels, a readout `JLabel` that Swing owns so the AWT-beside-Swing point keeps being made. A login-form shape covers the whole surface on one screen:

1. A `TextField("user")` whose `TextListener` writes each keystroke's `getText()` into a readout — the keystroke → `TextEvent` path, and the one thing this slice adds that no earlier AWT widget had.
2. A `TextField` with `setEchoChar('*')` and a Swing "Show / hide" button toggling `setEchoChar(0)` — the masking mechanism, both directions, which is also the demo a human has to *look at* in a browser, since a server-side test cannot see a mask.
3. An `ActionListener` on Enter in either field writing `"submitted: " + e.getActionCommand()` — the command-is-the-text rule (quirk 13).
4. Buttons driving `selectAll()`, `select(2, 5)`, `setCaretPosition(0)` and `getSelectedText()` into the readout — the selection surface, including the `""`-not-null result.
5. A `setEditable(false)` field beside an editable one, with `getBackground()` printed, so the `SystemColor.control` quirk is visible.
6. A `new TextField("initial value")` with its `getColumns()` printed — the ctor-derives-columns quirk, which is the one a migrator hits first without knowing it.

**A new `AwtTextFieldWarnInventoryTest`** holds an API bucket, `inventory_awt_textfield_api_surface`, and the pane's user path: an exact `_find(STextField.class, withCount(N))` peer-count assertion (the pane holds only its own widget's peers), a `_setValue` on the user field to drive the keystroke path, and clicks through the show/hide, selection and editable toggles.

The API bucket cannot be exhaustive the way `Button`'s and `Label`'s are — this is a ~45-method surface across two classes, with two methods expected to WARN (`getAccessibleContext`, `getInputMethodRequests`) and the four `process*` / `paramString` hooks protected and therefore driven from the unit tests instead. That difference should be stated in the test's javadoc rather than papered over, since `sampler/description.md` currently advertises the AWT buckets as exhaustive.

## Tests

`surrogates/src/test/java/com/vaadin/swingbridge/surrogates/STextFieldTest.java` (~24) and `emulators/src/test/java/vaadinx/awt/TextFieldTest.java` (~22). The behaviours that earn a test, in rough priority — every one of them is a quirk that a plausible implementation gets wrong:

- All four ctors, including `new TextField("hello")` → `columns == 5` and `new TextField("hello", 0)` → `columns == 0`.
- `new TextField(null).getText() == ""` and `setText(null)` → `""` — the *absence* of a null round-trip, unlike `Button` / `Label`.
- `replaceEOL`: `setText("a\nb")` → `"a b"`, and with `System.lineSeparator()` on a platform where it is `\r\n`.
- **`setText` fires exactly one `TextEvent`**, an unchanged `setText` fires none, and the constructor fires none. The single most breakable assertion in the slice.
- One `TextEvent` per browser keystroke, driven through Karibu `_setValue` on the peer.
- Enter → one `ActionEvent` whose `getActionCommand()` is the current text and whose `getSource()` is the emulator (on the emulator side) / the surrogate (on the surrogate side).
- `setColumns(-1)` throws with AWT's message; `new TextField("x", -1)` does not throw and yields 0.
- `setCaretPosition(-1)` throws with `"position less than zero."`; `setCaretPosition(len + 5)` clamps silently.
- `select(-5, 999)` clamps; `select(5, 2)` collapses to `5..5`; `selectAll()` on empty text is `0..0`.
- `getSelectedText()` with no selection is `""`, **not** null — asserted explicitly against the Swing behaviour it differs from.
- `setEchoChar('*')` → `echoCharIsSet()`, `getEchoChar() == '*'`, and the mask reaching the peer (surrogate side, whatever the mechanism ends up being); `setEchoChar(0)` reverses it.
- `setEditable(false)` → peer `isReadOnly()`, and `getBackground() == SystemColor.control` on the emulator, then `setBackground(RED)` → `getBackground() == RED` even while non-editable (the `backgroundSetByClientCode` flag).
- Null listener add/remove are silent no-ops.
- `processTextEvent` / `processActionEvent` reachable and overridable from a subclass in the same package, and `processEvent` routing a `TextEvent` and an `ActionEvent` to the right funnel (`Button`'s test does this and it is where the protected surface gets covered).
- R_leaf_peer_lockdown: peer type is `STextField` through every public ctor *and* through a user-code subclass.
- An AWT `TextField` dropped into a Swing `JPanel` inside a `JFrame`, plus the inherited `ComponentMixin` / `vaadinx.awt.Component` surface with no `JComponent` in the chain.
- A zero-WARN happy path.
- Cursor defaults to `TEXT_CURSOR`.

## File checklist

Mirroring `eea1c09` (Button) and `4d3eb27` (Label):

| file | action |
|---|---|
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/awt/TextComponentMixin.java` | **new** |
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/internal/AwtTextStateStore.java` | **new** (dot/mark, `textListeners`, `preventPeerEvents`) |
| `surrogates/src/main/java/com/vaadin/swingbridge/surrogates/STextField.java` | **new** |
| `surrogates/src/test/java/com/vaadin/swingbridge/surrogates/STextFieldTest.java` | **new** |
| `surrogates/decisions.md` | new `SD_stextfield` entry (`STextField` + the mixin), possibly a separate entry if the masking mechanism deserves its own |
| `emulators/src/main/java/vaadinx/awt/TextComponent.java` | **new** |
| `emulators/src/main/java/vaadinx/awt/TextField.java` | **new** |
| `emulators/src/test/java/vaadinx/awt/TextFieldTest.java` | **new** |
| `emulators/decisions.md` | new `D_awt_textfield` entry; repoint `D_frameinit_shape`'s `Q_enableevents_meaningful` tripwire, which cites this plan |
| `emulators/src/test/java/vaadinx/EmulatorOwnedStateTest.java` | a bare-thread read of the getters, per D_emulator_owned_state |
| `sampler/src/main/java/com/vaadin/swingbridge/sampler/AwtTextFieldPanel.java` | **new** |
| `sampler/src/test/java/com/vaadin/swingbridge/sampler/AwtTextFieldWarnInventoryTest.java` | **new** — API bucket + user path |
| `sampler/src/main/java/com/vaadin/swingbridge/sampler/SamplerCatalogue.java` | one `"AWT"` / `TextField` line |
| `sampler/description.md` | add the `AwtTextFieldWarnInventoryTest` inventory line; correct the "exhaustive bucket" claim |
| `CLAUDE.md` | one clause in the *Component surface* paragraph, next to `vaadinx.awt.Button` / `vaadinx.awt.Label` |

No `META-INF/resources` CSS file unless the declarative masking variant wins over the inline-style one.

## Open questions

Things a human has to decide, and the places I am guessing.

1. **The masking mechanism is unverified in a browser.** I verified from `javap` and the Vaadin sources that no server-side API exists, and `-webkit-text-security` is the mechanism I would reach for — but I have not confirmed that it applies to Vaadin 25's inner input, nor that Firefox (which shipped the property late) honours it, nor whether the input is light-DOM-reachable from a global stylesheet. **This slice should not land on a WARN-and-render-cleartext fallback**: if none of the three mechanisms survives verification, that is a finding to bring back, not a divergence to accept quietly.
2. **Should the selection-report JS be shared rather than duplicated?** The `emul-selection` / `emul-selection-now` custom events behind `JTextComponentMixin.addSelectionReportListener`, their client-side dedupe and `whenInputElementReady` are ~40 lines of JS protocol that `TextComponentMixin` would need verbatim. Duplicating a JS protocol is materially worse than duplicating SD_sbutton's 40 lines of Java listener fan-out, because the two copies drift silently. The clean fix is extracting it into a small `com.vaadin.swingbridge.surrogates.internal` helper parameterised by a text-length supplier and a dot/mark holder — but that is **a refactor of a landed Swing path to accommodate an AWT widget**, which is exactly the kind of change the project treats as a decision rather than a chore. My recommendation is to extract; the call is not mine.
3. **`vaadinx.awt.TextComponent`'s ctor visibility.** Package-private (mirrors the JDK, closes the seam to user code, works because both subclasses are in `vaadinx.awt`) vs `protected` (R_leaf_peer_lockdown's literal wording for a non-leaf) vs a faithful `sealed … permits`. I lean package-private; it is a one-word change either way.
4. **Reproduce the `eventEnabled` gate on `processTextEvent`, or always dispatch?** Gating is faithful and matches the JDK's own javadoc; always dispatching makes a subclass override unconditionally reachable, which R_no_vaadin_in_api limb 2 is arguably about. I lean faithful, but this is the kind of thing the R_no_vaadin_in_api sweep would want stated one way in a `D*` entry.
5. **Is `columns` on the surrogate really R_vaadin_first-legitimate?** I said the justification is the weakest in the class and I stand by that. The precedent (`SJTextField`, `SJPasswordField`) is what carries it, not the reasoning.
6. **Does `getPreferredSize(int)` WARN?** I propose no, for exit-gate reasons as much as consistency. If the answer is yes, four methods leave the API driver and the "exhaustive bucket" language weakens further.
7. **`TextField` or `TextArea` first?** `TextArea` is the same base plus five methods, and doing them together would settle the mixin's shape with two real consumers instead of one — at the cost of a much bigger single slice. The lane's habit so far is one widget per slice.
8. **Unverified: whether `ValueChangeMode.EAGER` + the `setText`-fires-once requirement compose cleanly.** The `preventPeerEvents` interaction is described above from first principles, not from a working implementation; it is the part I would expect to need a second pass.

## Effort

**~1.5–2 sessions**, against the ~1 session `awt-widgets.md` estimated — that estimate assumed a "re-skin of `SJTextField`", and the re-skin is the one thing this design rejects. The overrun is concentrated in three places: the new `TextComponentMixin` + state store (the first AWT-level mixin beyond `Component`/`Container`), the selection/caret surface with its unresolved sharing question (Open question 2 could add a refactor of a landed Swing path), and `echoChar`, which needs browser verification before it can be called done. The `TextField`-specific surface on top of the mixin — echo, columns, Enter, the four size overloads — is genuinely cheap, and a subsequent `STextArea` would be the ~1 session the parent doc predicted, because it inherits everything expensive.
