# The `java.awt.MenuComponent` family — design pass (not planned work)

**Status:** brainstorm, no commitment. Estimated 2026-08-21. Part of the AWT-widget lane, which is
**delayed and paused (2026-09-22).** Nothing below is implemented: `vaadinx.awt` ports no
`MenuComponent` class, `Component.add(PopupMenu)` / `Component.remove(MenuComponent)` /
`Frame.remove(MenuComponent)` are WARN stubs typed on the JDK classes, and `Frame.setMenuBar` stores
a `java.awt.MenuBar` and WARNs, rendering nothing.

**Maintainer-facing.** The AWT-lane sizing gave this family one row — *"**multi-session** — the real
cost: they extend `java.awt.MenuComponent`, a second unported root hierarchy. `MenuTreeBuilder` logic
is reusable, the base classes are not."* This is the pass that checks how bad that actually is.

**Headline, stated up front so the rest can be read as evidence.** The "second root hierarchy" framing
is *structurally* correct and *economically* wrong. The second root is real — `java.awt.MenuComponent
extends Object`, so none of `vaadinx.awt.Component` / `ComponentMixin` / the `peer` field / R_leaf_peer_lockdown's
lock-down mechanism apply. But the second root is also **tiny and nearly inert**: `MenuComponent`
contributes five methods a migrator ever calls, the whole seven-class family fires exactly **two**
event types and **both only on user interaction**, and — the decisive fact — the expensive half of
menu rendering was already built layer-neutral. `com.vaadin.swingbridge.surrogates.MenuNode` is a plain record with no
Swing type in it, and `MenuTreeBuilder` consumes only that record. An AWT menu tree plugs into the
existing builder unchanged. **Expected new surrogate classes: zero.** Re-estimate: ~2 sessions, and
the irreducible part is not the menus at all — it is re-typing `MenuContainer` through two landed
classes (`vaadinx.awt.Component`, `vaadinx.awt.Frame`) and giving a plain `vaadinx.awt.Frame` (peer
`SFrame`, no root pane) the menubar slot only root-pane containers have today.

---

## What the JDK classes actually are

Surfaces from `javap` on JDK 25, trimmed to `public` (package-private internals — `getItemImpl`,
`handleShortcut`, `doMenuEvent`, `getFont_NoClientCode`, the `AWTAccessor` plumbing — are omitted;
they matter for *behaviour*, not for what a migrator compiles against). Behavioural notes are read
from `$JAVA_HOME/lib/src.zip`, not from memory.

### `MenuComponent` (abstract, extends `Object`, implements `Serializable`)

```
public MenuComponent() throws HeadlessException;
public String getName();                 public void setName(String);
public MenuContainer getParent();
public Font getFont();                   public void setFont(Font);
public void removeNotify();
public boolean postEvent(Event);         // deprecated-shaped AWT 1.0
public final void dispatchEvent(AWTEvent);
protected void processEvent(AWTEvent);   // empty body
protected String paramString();
public String toString();
protected final Object getTreeLock();
public AccessibleContext getAccessibleContext();
```

Quirks worth naming:

- **`getFont()` walks *up* and bottoms out at `null`** — local field, else `parent.getFont()` (the
  `MenuContainer` method), else `null`. Different from `java.awt.Component`'s walk, which
  `vaadinx.awt.Component` deliberately bottoms out at a non-null `CssConvert.DEFAULT_FONT` so the
  NetBeans `getFont().deriveFont(...)` idiom doesn't NPE. An *attached* menu item inherits the
  frame's font (non-null); a *detached* one legitimately returns null.
- **`getName()` lazily auto-generates** (`"menuitem0"`, `"menu3"`, …) on first read when nothing was
  set. `vaadinx.awt.Component.getName` already declines to do this for widgets, with a comment saying
  the auto-name is rarely observed; same call applies here.
- **`dispatchEvent` is `final`.** It cannot be overridden, which makes it a *safe* funnel to route
  the browser click through — see the R_no_vaadin_in_api section.
- **`dispatchEventImpl`'s legacy branch is unreachable for us.** When `newEventsOnly` is false (no
  listener registered and no `enableEvents`) it calls `e.convertToOld()` and posts the AWT-1.0
  `Event`. `AWTEvent.convertToOld()` is **package-private in `java.awt`** — verified with
  `javap -p` — so `vaadinx.awt` cannot call it. That branch is permanently out; see the WARN table.
- **`getTreeLock()` is `protected final` and returns `java.awt.Component.LOCK`** — a package-private
  static. A port returns its own lock object; nothing observable depends on the identity.
- **No `PropertyChangeListener`, no `ChangeListener`, no `VetoableChangeListener` anywhere in the
  family.** `MenuComponent` has no bean-event surface at all.

### `MenuContainer` (interface)

```
public Font getFont();
public void remove(MenuComponent);
public boolean postEvent(Event);
```

Implemented by `MenuBar`, `Menu`, **and `java.awt.Component`** (`javap java.awt.Component` →
`implements ImageObserver, MenuContainer, Serializable`), plus `java.awt.Frame` redundantly.
`vaadinx.awt.Component` today declares the **JDK's** `java.awt.MenuContainer`, with
`remove(java.awt.MenuComponent)` a WARN stub and `postEvent(java.awt.Event)` beside it.

### `MenuBar` (extends `MenuComponent`, implements `MenuContainer, Accessible`)

```
public MenuBar() throws HeadlessException;
public void addNotify();                 public void removeNotify();
public Menu getHelpMenu();               public void setHelpMenu(Menu);
public Menu add(Menu);
public void remove(int);                 public void remove(MenuComponent);
public int getMenuCount();               public int countMenus();      // deprecated alias
public Menu getMenu(int);
public synchronized Enumeration<MenuShortcut> shortcuts();
public MenuItem getShortcutMenuItem(MenuShortcut);
public void deleteShortcut(MenuShortcut);
public AccessibleContext getAccessibleContext();
```

- **`add(Menu)` reparents**: `if (m.parent != null) m.parent.remove(m)`. `add(null)` **NPEs** on that
  field read — AWT does not null-guard. (`vaadinx.swing.JMenuBar.add` returns null instead; the AWT
  emulator should NPE.)
- **`setHelpMenu(m)`** removes the previous help menu, then `add(m)` if not already a child, sets
  `m.isHelpMenu = true`. Purely a platform-motif concept (Motif right-aligned the Help menu). No
  Vaadin counterpart.
- `shortcuts()` / `getShortcutMenuItem` / `deleteShortcut` are **whole-tree walks** — trivially
  portable, pure bookkeeping over the child lists.
- `getMenu(i)` is `menus.elementAt(i)` on a `Vector` → **`ArrayIndexOutOfBoundsException`**, not
  `IndexOutOfBoundsException`.

### `Menu` (extends `MenuItem`, implements `MenuContainer, Accessible`)

```
public Menu() / Menu(String) / Menu(String, boolean tearOff) throws HeadlessException;
public void addNotify();                 public void removeNotify();
public boolean isTearOff();
public int getItemCount();               public int countItems();      // deprecated alias
public MenuItem getItem(int);
public MenuItem add(MenuItem);           public void add(String);
public void insert(MenuItem, int);       public void insert(String, int);
public void addSeparator();              public void insertSeparator(int);
public void remove(int);                 public void remove(MenuComponent);
public void removeAll();
public String paramString();
public AccessibleContext getAccessibleContext();
```

- **`Menu` *is* a `MenuItem`** — it has a label, an actionCommand, a shortcut, and an
  `ActionListener` list. An empty `Menu` clicked in AWT fires its own `ActionEvent`.
- **`addSeparator()` is literally `add("-")`** — a separator is a real `MenuItem` whose label is
  `"-"`, sitting in the same `items` Vector. There is no `Separator` class. So an AWT menu's
  `getItemCount()` counts separators and `getItem(i)` returns them, which is the opposite of
  `vaadinx.swing.JMenu`, where separators are `JSeparator` instances the child list type-tests out.
- **`insert(mi, index)` is implemented by remove-tail / add / re-add-tail.** Consequence: an
  `index > getItemCount()` does **not** throw — the tail loop simply doesn't run and the item
  appends. Only `index < 0` throws, `IllegalArgumentException("index less than zero.")`.
- `getItem(i)` → `items.elementAt(i)` → `ArrayIndexOutOfBoundsException`.
- `add(null)` NPEs (`mi.parent` read), same as `MenuBar.add`.
- `remove(MenuComponent)` with null or a non-child is a **silent no-op** (`indexOf` → -1).
- `isTearOff()` **returns the ctor value verbatim** even though no JDK L&F ever implemented tear-off.

### `MenuItem` (extends `MenuComponent`, implements `Accessible`)

```
public MenuItem() / MenuItem(String) / MenuItem(String, MenuShortcut) throws HeadlessException;
public void addNotify();
public String getLabel();                public synchronized void setLabel(String);
public boolean isEnabled();              public synchronized void setEnabled(boolean);
public synchronized void enable();       public void enable(boolean);   // deprecated
public synchronized void disable();                                     // deprecated
public MenuShortcut getShortcut();       public void setShortcut(MenuShortcut);
public void deleteShortcut();
protected final void enableEvents(long); protected final void disableEvents(long);
public void setActionCommand(String);    public String getActionCommand();
public synchronized void addActionListener(ActionListener);
public synchronized void removeActionListener(ActionListener);
public synchronized ActionListener[] getActionListeners();
public <T extends EventListener> T[] getListeners(Class<T>);
protected void processEvent(AWTEvent);
protected void processActionEvent(ActionEvent);
public String paramString();
public AccessibleContext getAccessibleContext();
```

- **`getActionCommand()` falls back to the label** (`actionCommand == null ? label : actionCommand`)
  — the same rule `java.awt.Button` has and `SButton` / `AbstractButtonMixin` already implement.
- **Every setter here is silent.** `setLabel`, `setEnabled`/`enable`/`disable`, `setShortcut`,
  `deleteShortcut`, `setActionCommand` fire nothing — no `ActionEvent`, no `PropertyChangeEvent`, no
  `ChangeEvent`. The peer calls in the JDK bodies are pure redraw pokes. This is the "AWT setters are
  frequently silent where the Swing equivalent fires" case, verified: contrast
  `vaadinx.swing.JMenuItem.setAccelerator`, which fires an `"accelerator"` PCE.
- **The one real event path is `doMenuEvent(when, modifiers)`** (package-private): posts
  `new ActionEvent(this, ACTION_PERFORMED, getActionCommand(), when, modifiers)` to the event queue.
  Reached only from a native menu selection or from `handleShortcut`.
- `eventEnabled` gates on `actionListener != null || (eventMask & ACTION_EVENT_MASK) != 0`.
- **The child-enabled cascade is computed, not stored.** `isItemEnabled()` walks ancestors and
  returns false if any enclosing `Menu` is disabled — but only `handleShortcut` consults it;
  `isEnabled()` itself returns the local flag. So AWT's `isEnabled()` on an item inside a disabled
  menu returns **true**. (Vaadin needs the *effective* value for rendering, which
  `JMenuItem.toMenuNode(parentEnabled)` already computes without disturbing `isEnabled()` — the same
  split works verbatim here.)

### `CheckboxMenuItem` (extends `MenuItem`, implements `ItemSelectable, Accessible`)

```
public CheckboxMenuItem() / (String) / (String, boolean) throws HeadlessException;
public void addNotify();
public boolean getState();               public synchronized void setState(boolean);
public synchronized Object[] getSelectedObjects();
public synchronized void addItemListener(ItemListener);
public synchronized void removeItemListener(ItemListener);
public synchronized ItemListener[] getItemListeners();
public <T extends EventListener> T[] getListeners(Class<T>);
protected void processEvent(AWTEvent);
protected void processItemEvent(ItemEvent);
public String paramString();
public AccessibleContext getAccessibleContext();
```

- **`setState(boolean)` fires nothing, and the JDK javadoc says so explicitly**: *"Programmatically
  setting the state of the check box menu item will **not** trigger an `ItemEvent`. The only way to
  trigger an `ItemEvent` is by user interaction."* This is a **behavioural divergence from the Swing
  sibling** that must be honoured: `vaadinx.swing.JCheckBoxMenuItem.setSelected` fires `ItemEvent` +
  `ChangeEvent`, correctly, because Swing's does. AWT's must stay mute.
- `doMenuEvent` toggles state *then* posts `ItemEvent(this, ITEM_STATE_CHANGED, getLabel(), SELECTED|DESELECTED)`.
  Note the item is `getLabel()` (a `String`), where Swing's `JCheckBoxMenuItem` passes `this`.
- `getSelectedObjects()` returns `new Object[]{ label }` when on, **`null`** when off (not an empty
  array — `ItemSelectable`'s documented null).
- **No `CheckboxGroup` equivalent.** AWT has no radio menu item at all; `JRadioButtonMenuItem` has no
  AWT counterpart, so there is nothing analogous to `vaadinx.swing.JRadioButtonMenuItem` to write.

### `PopupMenu` (extends `Menu`)

```
public PopupMenu() / PopupMenu(String) throws HeadlessException;
public MenuContainer getParent();        // null when a tray-icon popup
public void addNotify();
public void show(Component origin, int x, int y);
public AccessibleContext getAccessibleContext();
```

- Two lives: parented to a `java.awt.Component` (a real popup, `show` works) or parented to a `Menu` /
  `MenuBar` (then *"this PopupMenu is really just a plain, old Menu"* — `addNotify` chains to
  `super`, and `show` throws).
- The parent is set by **`java.awt.Component.add(PopupMenu)`** — which is a WARN stub on
  `vaadinx.awt.Component` today. Wiring it is what makes `show`'s exception ladder
  meaningful.
- `show`'s exception ladder is the richest R_match_swing_errors surface in the family — see the table.

### `MenuShortcut` (extends `Object`, implements `Serializable`)

```
public MenuShortcut(int key);            public MenuShortcut(int key, boolean useShiftModifier);
public int getKey();                     public boolean usesShiftModifier();
public boolean equals(MenuShortcut);     public boolean equals(Object);
public int hashCode();                   public String toString();
protected String paramString();
```

**`MenuShortcut` needs no port.** It holds an `int` and a `boolean`; nothing in it references
`Component` or `MenuComponent`, so per the class-porting rule (D_whitelist_porting) it is reused from the JDK
unchanged — like `Color`, `Font`, `ActionEvent`. A migrator's `import java.awt.MenuShortcut` stays
exactly as written; only the classes that *take* one get import-swapped.

One caveat: `MenuShortcut.toString()` calls `Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()`
guarded by `GraphicsEnvironment.isHeadless()`. Under headless it yields `"+A"` rather than
`"Ctrl+A"`. That is the JDK's own degradation, not ours, but it will look like an SB-Emulators bug if it shows
in a UI string — see Open questions.

### Constant collisions: none

SD_slabel's trap (`java.awt.Label.LEFT/CENTER/RIGHT` = 0/1/2 vs `SwingConstants`' 2/0/4) **does not recur
here.** `javap` shows the entire family declares no `public static final` fields — only synthetic
`static {}` initialisers. There is no `MenuShortcut.SHIFT_MASK`, no `Menu.TEAR_OFF`. This is the
first AWT slice where nothing numeric can collide.

---

## The second-root problem

`java.awt.MenuComponent extends Object`. Everything the AWT-widget lane leaned on is therefore
unavailable:

| what `vaadinx.awt.Button` got for free | available to `vaadinx.awt.MenuItem`? |
|---|---|
| `vaadinx.awt.Component` base (1715 lines: bounds, colours, font, visibility, enabled, focus, listeners, parent walk, `firePropertyChange`) | **no** |
| the `peer` field + `getPeer()` + attach/detach → `addNotify`/`removeNotify` bridge | **no** |
| `com.vaadin.swingbridge.surrogates.awt.ComponentMixin` on the surrogate side | **no** — there is no surrogate (see next section) |
| R_leaf_peer_lockdown's protected `(Component peer)` ctor seam to close | **no** — there is no peer ctor to omit |
| `EventListenerList listenerList` on the base | **no** — each listener-bearing class carries its own |

What that costs, concretely:

- **Two new files that are pure base plumbing**: `vaadinx.awt.MenuContainer` (3 methods) and
  `vaadinx.awt.MenuComponent`. The latter is genuinely small — a `name` field, a `font` field, a
  `MenuContainer parent` field, `getTreeLock()`, an empty `processEvent`, and `dispatchEvent` →
  `dispatchEventImpl` → `eventEnabled` → `processEvent`. Call it 120 lines with javadoc.
- **No inherited geometry, colour or visibility API — and none is wanted.** `MenuComponent` has no
  `setBounds`, `setBackground`, `setVisible`, `setEnabled` (that is on `MenuItem`), `addMouseListener`
  or `requestFocus`. So the absence of `vaadinx.awt.Component` costs *nothing in surface area*; the
  JDK class deliberately has none of it. `MenuComponent.AccessibleAWTMenuComponent` is one long
  litany of `// Not supported for MenuComponents` returning null / false / no-op, which is the JDK
  telling you exactly this.
- **What a migrator actually touches on `MenuComponent` itself**: `setFont` (menu-font styling is a
  real AWT idiom), `getName`/`setName` (occasionally, for test lookup), `getParent()` (rarely).
  `postEvent`, `dispatchEvent`, `removeNotify`, `getTreeLock`, `paramString` and
  `getAccessibleContext` are effectively never called by application code — `dispatchEvent` matters
  only as *our own* internal funnel.
- **`setFont` has no peer to push to.** On `vaadinx.awt.Component` it writes four CSS properties onto
  `peer.getElement()`. A `MenuItem` has no element of its own — its rendered identity is a Vaadin
  `MenuItem` built by `MenuTreeBuilder` and thrown away on the next rebuild. So per-item font is
  either dropped-and-WARNed, or carried into `MenuNode` as a new field the builder applies to the
  Vaadin item's style. See Open questions — this is the one genuinely open design call.

**Verdict on the framing.** "Second unported root hierarchy" is accurate and sounds like it implies a
second `vaadinx.awt.Component`. It does not: `MenuComponent` is ~5 real methods against
`Component`'s ~200, and the family below it needs no geometry, no painting, no focus, no mouse
events, and no property-change machinery.

---

## How the Swing menu machinery already works

Three files carry it, and the split is the reason this slice is cheap.

**`com.vaadin.swingbridge.surrogates.MenuNode`** — an immutable record:

```java
public record MenuNode(String text, com.vaadin.flow.component.Component icon,
                       boolean enabled, boolean separator, boolean checkable, boolean checked,
                       javax.swing.KeyStroke accelerator, Runnable onClick, List<MenuNode> children)
```

**Read the type list again: there is no Swing type in it and no emulator type in it.** `KeyStroke` is
a JDK value class reused unchanged (same status as `Color`); `icon` is already a *converted* Vaadin
component; `onClick` is a bare `Runnable`. The record is a **layer-neutral descriptor**, not a Swing
mirror.

**`com.vaadin.swingbridge.surrogates.internal.MenuTreeBuilder`** — a stateless static walker over `MenuNode` into
Vaadin's `HasMenuItems` (`MenuBar`, `ContextMenu` and `SubMenu` all implement it, so the walk is
identical above the peer type, per SD_sjpopupmenu). It owns `rebuild`, `buildSubMenu`, `addNode`,
`buildIconAndText`, `installAccelerators` (JDK `KeyStroke` → `KeyConvert.toVaadinKeyBinding` →
UI-scoped `Shortcuts.addShortcutListener`), and `recoverLeafMap` (re-pairing the two isomorphic trees
after attach). **It never mentions `javax.swing` except `KeyStroke`, and never mentions `vaadinx`.**

**`SJMenuBar` / `SJPopupMenu`** — the only two Vaadin components in the whole Swing menu stack.
Public surface beyond what they inherit from Vaadin: `rebuildFromTree(List<MenuNode>)` and
`getCurrentTree()`. State lives in `MenuBarStateStore` (accelerator `Registration`s to tear down,
last-pushed tree, `preventRebuild` re-entrancy guard). The one asymmetry is the top-level separator,
handed to the builder as a per-caller `Consumer<MenuNode>` — `MenuBar` has no top-level
`addSeparator()` so `SJMenuBar` WARNs and skips; `ContextMenu` does so `SJPopupMenu` renders it.

**The raw-`Span` peer consequence.** `vaadinx.swing.JMenuItem`'s ctor is
`super(new com.vaadin.flow.component.html.Span())` and its own comment says the Span *"is never
attached to a real UI; it exists only to satisfy AbstractButton's (Component peer) ctor chain."* The
entire `JMenuItem` / `JMenu` / `JCheckBoxMenuItem` / `JRadioButtonMenuItem` family renders through
**nothing of its own**. Instead:

1. Each item stores its state emulator-side (text, icon, mnemonic, accelerator, actionCommand,
   enabled, selected) and holds an untyped `Object parentNode` pointer.
2. Every mutating setter calls `notifyTreeMutated()`, which walks `parentNode` up through the `JMenu`
   chain to the root `JMenuBar` **or** `JPopupMenu`.
3. The root's `pushTree()` walks the *emulator* tree via `JMenuItem.toMenuNode(parentEnabled)` /
   `JMenu.collectChildren(...)`, producing a fresh `List<MenuNode>`, and hands it to
   `SJMenuBar.rebuildFromTree` / `SJPopupMenu.rebuildFromTree`.
4. The surrogate updates the existing Vaadin items in place when the tree's shape is unchanged
   (`MenuTreeBuilder.patchInPlace`), and otherwise tears down the old item tree and old shortcut
   registrations and rebuilds from scratch.

**This is exactly the hypothesis the lane sizing's framing obscures.** Because the tree is rebuilt
from the emulator hierarchy on every mutation, and because the descriptor it is rebuilt *from* is
layer-neutral, **the peer type of an individual item is irrelevant — and for AWT items, there needn't
be one at all.** The Swing family only carries a Span because `AbstractButton`'s ctor chain demands
one. `vaadinx.awt.MenuItem` has no such chain and can hold nothing.

---

## Reuse verdict

**Yes — the AWT tree plugs into the existing builder with no change to `:surrogates`, and no new
surrogate class appears.**

The chain of facts:

1. `MenuTreeBuilder` consumes only `List<MenuNode>`. `MenuNode`'s nine components are all satisfiable
   from an AWT tree: `text` = `MenuItem.getLabel()`; `icon` = always `null` (AWT menu items have no
   icon at all — one field the AWT lane makes *simpler*); `enabled` = the computed cascade;
   `separator` = `"-".equals(label)`; `checkable` = `item instanceof CheckboxMenuItem`; `checked` =
   `getState()`; `accelerator` = `MenuShortcut` → `KeyStroke` (below); `onClick` = a `Runnable` that
   dispatches the AWT `ActionEvent` / `ItemEvent`; `children` = the recursive walk of `Menu.items`.
2. Therefore **`MenuNode` needs no new field and no variant** (modulo the per-item-font open question).
3. Therefore `SJMenuBar` / `SJPopupMenu` need no new method: `rebuildFromTree(List<MenuNode>)` is
   already the whole API, and it is already free of Swing types.
4. Therefore **there is no `SMenuBar` / `SMenu` / `SMenuItem` / `SPopupMenu` to write.**
   `vaadinx.awt.MenuBar`'s ctor holds a `new SJMenuBar()`; `vaadinx.awt.PopupMenu`'s holds a
   `new SJPopupMenu()`.

**Why reusing the `SJ*` class here is right, when SD_sbutton and SD_slabel both said "write a new surrogate".**
Those two rejected reuse for a specific reason: `SJButton` carries `AbstractButtonMixin` — a
`ButtonModel` state machine, `Action` installation, icon child, mnemonic — and `SJLabel` carries
`JComponentMixin` plus an icon child, an HTML-mode markup shadow, `labelFor` and a mnemonic pair.
Grafting that onto a JDK class that has none of it is the "fold shallow-commonality shells into a
base" anti-pattern. **`SJMenuBar` carries none of that.** Its entire state is a `MenuNode` tree and a
map of shortcut registrations; its `JComponentMixin` contributes border / tooltip / name helpers that
are inert for a menubar and unreachable from the AWT emulator (which has no `getPeer()`-typed
accessor pointing at them). There is nothing Swing-shaped to graft.

The `SJ` prefix reads slightly wrong in `vaadinx.awt.MenuBar`'s ctor, and that is the whole cost of
the reuse. Per the S-prefix rule the prefix marks the *module*, not a Swing/AWT split; a rename to
`SMenuBar` used by both lanes would be honest but is a churn-for-naming trade, and `SJMenuBar` is
what `SJRootPane.setJMenuBar(SJMenuBar)`, `SJFrame` / `SJDialog`, `RootPaneScaffold` and the Swing
emulators are all typed on today. Recommendation: **reuse under the existing name; note the naming
wart in the decision entry** rather than renaming the typed call sites.

**Does a shared abstraction over the Swing and AWT *emulator* trees belong anywhere?** No, and the
module direction is not even the reason. Both trees live in `:emulators`, so a shared helper there
would respect `:emulators → :surrogates` fine. The reason is that the two trees have almost nothing
in common *below* `MenuNode`:

| | Swing tree | AWT tree |
|---|---|---|
| item base | `JMenuItem extends AbstractButton extends JComponent extends vaadinx.awt.Component` | `MenuItem extends MenuComponent extends Object` |
| separator | a distinct `JSeparator` in an `Object` child list, type-tested out | a `MenuItem` whose label is `"-"`, counted by `getItemCount()` |
| icon | `vaadinx.swing.Icon` → `EHelper.toVaadinIconComponent` | none exists |
| accelerator | `javax.swing.KeyStroke`, fires an `"accelerator"` PCE | `java.awt.MenuShortcut`, silent |
| radio variant | `JRadioButtonMenuItem` + `ButtonGroup` veto | none exists |
| checkbox setter | `setSelected` fires `ItemEvent` + `ChangeEvent` | `setState` fires nothing |

The genuinely shared part — the descriptor and the Vaadin walk — **is already shared and already in
the right module.** What would be duplicated is the emission walk: `toMenuNode`, `collectChildren`,
`notifyTreeMutated`, `pushTree` — roughly 60 lines, and the AWT version is *shorter* (no icon
conversion, no mnemonic, no `ButtonGroup`). Duplicating 60 shorter lines is the cheaper mistake, the
same call SD_sbutton made about `SButton`'s listener fan-out.

**Net new/changed files in `:surrogates`: zero.** If that holds through implementation, this would be
the first AWT-lane slice landing with a `D*` entry and no `SD*` entry.

---

## Emulator design, per class

All under `vaadinx.awt`. None of them extends `vaadinx.awt.Component`; none has a `peer` field.

### `MenuContainer` (interface)

```java
public interface MenuContainer {
    java.awt.Font getFont();
    void remove(vaadinx.awt.MenuComponent m);
    boolean postEvent(java.awt.Event e);      // java.awt.Event reused unchanged (its target is Object)
}
```

Ported because it references `MenuComponent`, which is ported. Then — and this is the ripple —
`vaadinx.awt.Component` (and redundantly `vaadinx.awt.Frame`, as in the JDK) should **declare**
`implements vaadinx.awt.MenuContainer` (today `Component` declares the JDK's `java.awt.MenuContainer`
instead), and their
`remove(java.awt.MenuComponent)` stubs must re-type to `remove(vaadinx.awt.MenuComponent)`. Both are
**breaking signature changes on landed classes** — and both are *required* by R_no_vaadin_in_api's first limb: once
`vaadinx.awt.MenuComponent` exists, a public method that shadows the JDK's `remove(MenuComponent)`
must speak the `vaadinx` type, exactly as `Container.remove` speaks `vaadinx.awt.Component`.

### `MenuComponent` (abstract)

Fields: `String name`, `java.awt.Font font`, `MenuContainer parent`, `boolean newEventsOnly`.
Methods per the JDK surface, with:

- `getFont()` — the faithful walk, `null` at the root. **Deliberately not** `vaadinx.awt.Component`'s
  non-null default: the JDK contract here really is null, and a menu tree that is attached inherits
  the frame's non-null font through `parent.getFont()` anyway.
- `dispatchEvent(AWTEvent)` — `public final`, delegating to a package-private `dispatchEventImpl`:
  `if (eventEnabled(e)) processEvent(e); else if (e instanceof ActionEvent && parent instanceof MenuComponent mc) { e.setSource(parent); mc.dispatchEvent(e); }`.
  The `newEventsOnly == false` legacy `convertToOld()` branch is dropped (inaccessible; see WARN table).
  **The `else` limb is worth porting rather than dropping** — it is how AWT bubbles an unhandled item
  action up to the enclosing `Menu`, and a migrated app with a single `ActionListener` on the `Menu`
  rather than per item depends on it.
- `getPeer()` — **not exposed.** There is no per-item Vaadin component; adding one would be
  fabricating a peer to satisfy symmetry. The two classes that *do* hold a Vaadin component
  (`MenuBar`, `PopupMenu`) expose their own accessor; see below.
- `removeNotify()` — no-op with a rationale comment (our Vaadin side is torn down and rebuilt by
  `rebuildFromTree`, not disposed per item), kept rather than dropped so a user override chaining
  `super.removeNotify()` compiles and runs.
- `getTreeLock()` — `protected final`, returns a private static lock object.
- `getAccessibleContext()` — WARN + null, as everywhere.

### `MenuBar`

Tree root. Holds `List<Menu> menus`, `Menu helpMenu`, and **`private final com.vaadin.swingbridge.surrogates.SJMenuBar bar`**
constructed in the ctor. Full JDK children API, plus the emission walk (`pushTree()`,
`notifyTreeMutated()`) copied in shape from `vaadinx.swing.JMenuBar`.

**Peer accessor and R_no_vaadin_in_api.** `MenuBar` needs to hand its `SJMenuBar` to `Frame.setMenuBar`. A public
`getPeer()` returning `com.vaadin.flow.component.Component` is **R_no_vaadin_in_api-legal by provenance**:
`java.awt.MenuComponent` declares no public `getPeer` (the `peer` field is package-private, and
`java.awt.Component.getPeer` was removed in Java 9), so the signature is SB-Emulators-invented and free to
speak Vaadin — the same reasoning that licenses `vaadinx.awt.Component.getPeer`. R_no_vaadin_in_api's test is *"does
the JDK define this signature?"*, not *"is it in a `vaadinx.awt` class?"*.

### `Menu`

`extends MenuItem implements MenuContainer`. Holds `List<MenuItem> items` — **one homogeneous list,
no `JSeparator` analogue**, because AWT separators *are* `MenuItem("-")`. `isTearOff()` returns the
stored ctor value (R_swing_is_truth: the JDK returns it verbatim, so we do too) and the ctor WARNs on `true`
because nothing renders a tear-off. `insert` and `insertSeparator` reproduce AWT's remove-tail /
add / re-add-tail implementation — not for fidelity theatre, but because the *observable* index
behaviour (no throw above `getItemCount()`) falls out of it for free.

`collectChildren` emits `MenuNode.ofSeparator()` for a `"-"`-labelled item and recurses otherwise.

### `MenuItem`

Fields: `String label`, `boolean enabled = true`, `MenuShortcut shortcut`, `String actionCommand`,
`EventListenerList listenerList` (its own — no `vaadinx.awt.Component` base to inherit one from),
`long eventMask`, `Object parentNode`-equivalent (here properly typed: `MenuComponent.parent` is
already a `MenuContainer`, so the walk is typed, unlike `vaadinx.swing.JMenuItem`'s untyped
`Object parentNode`).

- All setters silent, per the JDK. No `firePropertyChange` — `MenuComponent` has no PCE surface to
  fire on. Each setter calls `notifyTreeMutated()` so the render follows.
- `notifyTreeMutated()` walks `parent` up through `Menu`s to a `MenuBar` **or** a `PopupMenu`, exactly
  like the Swing version's two-root walk.
- `toMenuNode(boolean parentEnabled)` — `icon` always null, `checkable`/`checked` from overridable
  hooks (`CheckboxMenuItem` overrides), `accelerator` from the `MenuShortcut` conversion.

### `CheckboxMenuItem`

`boolean state` field. `setState` writes the field and re-pushes the tree and **fires nothing** — the
divergence from `vaadinx.swing.JCheckBoxMenuItem.setSelected` is the JDK's, and the class javadoc
should say so out loud so nobody "fixes" it. Its `onClick` toggles state, then dispatches an
`ItemEvent(this, ITEM_STATE_CHANGED, getLabel(), SELECTED|DESELECTED)` — note `getLabel()`, not
`this` — and re-pushes. `getSelectedObjects()` returns `new Object[]{label}` or **null**.

Unlike the Swing sibling, there is **no `ActionEvent` on a checkbox click**: AWT's
`CheckboxMenuItem.doMenuEvent` posts *only* the `ItemEvent`, overriding `MenuItem.doMenuEvent`
entirely. `vaadinx.swing.JCheckBoxMenuItem.makeOnClick` fires Item + Change + Action; the AWT one
fires Item only. Easy to get wrong by copy-paste.

### `PopupMenu`

`extends Menu`, holds `private final com.vaadin.swingbridge.surrogates.SJPopupMenu menu`. `getParent()` overridden per
the JDK (the tray-icon null case is moot — no `SystemTray`). `show(vaadinx.awt.Component, int, int)`
per the next-but-one section. Second tree root: its own `pushTree()`, and `MenuItem.notifyTreeMutated`
recognises it as a root.

### R_no_vaadin_in_api, second limb — the call-hierarchy hooks

D_r12_provenance's second limb says an exposed JDK hook must actually be *on the invoked path*. The hooks here are
`processEvent(AWTEvent)`, `processActionEvent(ActionEvent)` and `processItemEvent(ItemEvent)`. The
correct wiring, and it is not the obvious one:

```
MenuNode.onClick  →  EHelper.callSwing(() -> item.dispatchEvent(new ActionEvent(item, ...)))
                     dispatchEvent (final) → dispatchEventImpl → eventEnabled → processEvent → processActionEvent → listeners
```

**Route the browser click through `dispatchEvent`, not straight to `processActionEvent`.** Because
`MenuComponent.dispatchEvent` is `final`, it is a safe funnel: a user override of `processEvent` or
`processActionEvent` is then genuinely reached by a real click, which is precisely what limb 2 asks
for. It also gets the unhandled-action-bubbles-to-parent limb for free. (`vaadinx.awt.Button`'s
bridge enters at `processEvent` for the same reason — `Component.dispatchEvent` is still a stub
there, while `MenuComponent.dispatchEvent` would be ours to implement.)

### R_leaf_peer_lockdown, and why it is vacuous here

R_leaf_peer_lockdown asks a leaf emulator to omit the protected `(Component peer)` ctor so no subclass can swap the
peer. **This family has no peer ctor to omit** — `MenuComponent` has no peer field, and the two
classes that hold a Vaadin component (`MenuBar`, `PopupMenu`) hard-code it in their own ctors with no
alternative path. So R_leaf_peer_lockdown's *guarantee* holds absolutely and its *mechanism* is unused. Which is the
honest thing to write in the decision entry: the family is peer-locked by construction, not by
lock-down. (For the record, the leaf/non-leaf split is `MenuBar`, `CheckboxMenuItem`, `PopupMenu`
leaf; `MenuComponent`, `MenuItem`, `Menu` non-leaf — but it changes nothing.)

---

## `MenuShortcut` → Vaadin `Shortcuts`

The Swing path already exists end to end and is what we hook into:

```
JMenuItem.accelerator (javax.swing.KeyStroke)
  → MenuNode.accelerator
  → MenuTreeBuilder.installAccelerators
  → KeyConvert.toVaadinKeyBinding(KeyStroke)  →  Key + KeyModifier[]   (event.code-shaped, physical keys)
  → Shortcuts.addShortcutListener(ui, () -> SHelper.callSwing(onClick), key, mods)
  → Registration tracked in MenuBarStateStore.acceleratorRegistrations, torn down on the next rebuild
```

So the whole AWT-side job is **`MenuShortcut` → `KeyStroke`**, in the emitter:

```java
int mask = new vaadinx.awt.Toolkit().getMenuShortcutKeyMaskEx()      // META on mac, CTRL elsewhere
         | (s.usesShiftModifier() ? InputEvent.SHIFT_DOWN_MASK : 0);
KeyStroke.getKeyStroke(s.getKey(), mask);
```

`vaadinx.awt.Toolkit.getMenuShortcutKeyMaskEx()` **already exists and is already browser-aware** —
it reads `BrowserToolkitInfo.get().mac()` and returns `META_DOWN_MASK` or `CTRL_DOWN_MASK`. That is
the single piece of platform knowledge `MenuShortcut` needs and it is already sitting there. The
conversion is ~4 lines and `KeyConvert` needs no change.

What is lost:

- **The implicit-accelerator-modifier asymmetry.** A Swing `KeyStroke` states its modifiers
  explicitly; a `MenuShortcut` states only *shift*, and the accelerator modifier is implicit and
  platform-resolved. Under Vaadin, "platform" means *the browser we resolved at UI init*, so a
  session's shortcuts are pinned to whatever `BrowserToolkitInfo` reported. An AWT app that ran on
  Linux with `Ctrl+S` reports mac `Meta+S` to a mac browser — which is the *right* answer and also a
  behaviour change from what the desktop app did on that user's machine.
- **`MenuBar.shortcuts()` / `getShortcutMenuItem` / `deleteShortcut` are bookkeeping only.** They
  walk our own child lists and are exact; but they answer about the *AWT* model, and the *browser*
  registration is whatever `installAccelerators` last managed to install. An unmappable `VK_` code
  WARNs and skips the browser install while remaining visible to `shortcuts()`. That skew already
  exists on the Swing side.
- **Toolkit-level shortcut interception is not modelled.** AWT's `MenuBar.handleShortcut(KeyEvent)`
  is reached from the toolkit's global key dispatch; we install per-item UI-scoped Vaadin shortcuts
  instead. Observably equivalent for firing the item; not equivalent if a migrator calls
  `handleShortcut` themselves — but it is package-private, so they cannot.
- **No mnemonics.** AWT menus have no mnemonic concept at all (`setMnemonic` is Swing's), so the
  top-level-mnemonic gap `SJMenuBar` documents does not even arise.

---

## `PopupMenu.show`

`show(Component origin, int x, int y)` maps onto `SJPopupMenu`'s existing mechanism the same way
`vaadinx.swing.JPopupMenu.show` already does — `setTarget(...)` binds right-click / long-press, and
programmatic open at coordinates WARNs:

```java
public void show(vaadinx.awt.Component origin, int x, int y) {
    // ... the JDK exception ladder, verbatim, first ...
    menu.setTarget(origin.getPeer());
    EHelper.onUnimplemented("PopupMenu", "show/programmatic-open-at-coordinates", x, y);
}
```

R_no_vaadin_in_api first limb: the JDK says `java.awt.Component`, so the emulator says **`vaadinx.awt.Component`** —
matching what `vaadinx.swing.JPopupMenu.show(vaadinx.awt.Component, int, int)` already does.

Two things the AWT version gets that the Swing one does not:

1. **A real exception ladder that we can honour exactly**, because everything it checks already
   exists: `Container.isAncestorOf(vaadinx.awt.Component)` and `Component.isShowing()`. See the
   R_match_swing_errors table.
2. **The parent must be wired.** `java.awt.Component.add(PopupMenu)` is what sets `popup.parent`; on
   `vaadinx.awt.Component` it is a WARN stub. Implementing it — store the popup, set
   `popup.parent = this`, and `menu.setTarget(this.getPeer())` right away — is what makes both `show`
   and the plain right-click idiom work. `Component.remove(MenuComponent)` is the matching
   teardown, and is also the `MenuContainer` method, so it stops being a stub for two reasons at once.

Since binding the target at `add(PopupMenu)` time already makes right-click work, **the common AWT
popup idiom needs no `show` call at all** — which is the same conclusion `SJPopupMenu` reached for
Swing.

---

## `Frame.setMenuBar`

Today `vaadinx.awt.Frame.setMenuBar(java.awt.MenuBar)` / `getMenuBar()` are a field round-trip over
the **JDK** type — `setMenuBar` stores the bar (with AWT's early return on an unchanged bar) and
WARNs, rendering nothing — and `Frame.remove(java.awt.MenuComponent)` is a WARN stub. The JDK type is
correct while `MenuBar` is unported (same status as `java.awt.Color`), and **required to change the
moment it is ported**, per R_no_vaadin_in_api limb 1: the migrator's `import java.awt.MenuBar`
becomes `import vaadinx.awt.MenuBar`, so the signature must follow. `JFrame extends Frame` inherits
all three, giving a `JFrame` both `setJMenuBar(vaadinx.swing.JMenuBar)` and
`setMenuBar(vaadinx.awt.MenuBar)` — exactly the JDK's shape.

What it needs beyond re-typing:

- **A render slot.** A Swing menu bar is planted by the root pane, not the frame strategy:
  `JFrame.setJMenuBar` → `JRootPane` → `SJRootPane.setJMenuBar(SJMenuBar)`, which inserts the bar at
  index 0 of the layered pane, above the content pane (D_rootpane_containment). A `JFrame` receiving
  an *AWT* `MenuBar` can hand that same `SJRootPane` slot its `SJMenuBar`. **A plain
  `vaadinx.awt.Frame` peers on `SFrame`, which has no root pane and no menubar slot.** Either lift a
  minimal slot onto `SFrame`, or insert the element at index 0 from the emulator side. The
  element-insert route needs no `:surrogates` change, which keeps the zero-`SD*` property; the lift
  is cleaner. Open question.
- **`Frame.remove(MenuComponent)` clears the bar** when the argument is the current menubar (the
  JDK's `MenuBar.remove` path does the reverse bookkeeping), and the JDK's `setMenuBar →
  remove(MenuComponent)` edge — which D_frame_state_pairs deliberately did not take while both were
  stubs over `java.awt.MenuBar` — gets taken.

Note the asymmetry a migrator will hit: mixing `setJMenuBar` and `setMenuBar` on one `JFrame` — legal
in the JDK, where they are two independent slots the L&F renders differently — has **one** slot here.
Last writer wins, and the loser should WARN rather than silently vanish.

---

## Exception fidelity (R_match_swing_errors)

All messages read from JDK 25 source, not recalled.

| input | JDK exception + message | which layer throws |
|---|---|---|
| `Menu.getItem(i)`, `i >= getItemCount()` | `ArrayIndexOutOfBoundsException: "<i> >= <count>"` (from `Vector.elementAt`) | emulator `Menu` — note **A**IOOBE, not `IndexOutOfBoundsException` |
| `Menu.getItem(-1)` | `ArrayIndexOutOfBoundsException: "Index -1 out of bounds for length <n>"` (array store, past `elementAt`'s guard) | emulator `Menu` |
| `Menu.remove(int)` out of range | same AIOOBE — `remove` calls `getItem(index)` first | emulator `Menu` |
| `Menu.insert(mi, index)`, `index < 0` | `IllegalArgumentException: "index less than zero."` (trailing period is in the JDK string) | emulator `Menu` |
| `Menu.insert(mi, index)`, `index > getItemCount()` | **no throw** — appends | emulator `Menu` (must not "helpfully" validate) |
| `Menu.insertSeparator(index)`, `index < 0` | `IllegalArgumentException: "index less than zero."` | emulator `Menu` |
| `Menu.add(null)` | `NullPointerException` (unmessaged — `mi.parent` field read) | emulator `Menu` |
| `Menu.remove(null)` / non-child | **no throw**, silent no-op (`indexOf` → -1) | emulator `Menu` |
| `MenuBar.getMenu(i)` out of range | `ArrayIndexOutOfBoundsException: "<i> >= <count>"` | emulator `MenuBar` |
| `MenuBar.remove(int)` out of range | same AIOOBE | emulator `MenuBar` |
| `MenuBar.add(null)` | `NullPointerException` (`m.parent` read) | emulator `MenuBar` |
| `MenuBar.setHelpMenu(null)` | **no throw** — clears the slot | emulator `MenuBar` |
| `PopupMenu.show(o,x,y)`, parent null | `NullPointerException("parent is null")` | emulator `PopupMenu` |
| `PopupMenu.show`, parent not a `Component` (popup used as a plain `Menu`) | `IllegalArgumentException("PopupMenus with non-Component parents cannot be shown")` | emulator `PopupMenu` |
| `PopupMenu.show`, `origin` not the parent and not in its hierarchy | `IllegalArgumentException("origin not in parent's hierarchy")` — honourable exactly, via `Container.isAncestorOf` | emulator `PopupMenu` |
| `PopupMenu.show`, parent not on screen | `RuntimeException("parent not showing on screen")` — honourable via `Component.isShowing()` (`visible && isDisplayable()`); JDK also checks `peer == null`, which is never true for us | emulator `PopupMenu` |
| `MenuItem.getListeners(NotAnEventListener.class)` | `ClassCastException` (documented) | emulator `MenuItem` — falls out of the `EventListenerList` path |
| `new MenuShortcut(anyInt)` | no validation of any kind | JDK class, unported |
| `setLabel(null)` / `setActionCommand(null)` / `setState`, all setters | **no throw, no event** | emulator |

The whole family has **no `IllegalStateException`-shaped major-gap trigger** and adds no case to R_match_swing_errors's
enumerated throw set. Everything above is a JDK-faithful throw; everything not above is WARN.

---

## Dropped / WARN surface (R_match_swing_errors sub-buckets)

| item | bucket | note |
|---|---|---|
| tear-off menus — `Menu(String, boolean)` rendering | **(b)** permanently deferred | no JDK L&F ever implemented tear-off either; the ctor WARNs on `true` and `isTearOff()` round-trips the value per R_swing_is_truth (the JDK returns it verbatim). Diverges from `vaadinx.swing.JMenu.isTearOff()`, which hard-returns false |
| `MenuBar.setHelpMenu` / `getHelpMenu` special rendering | **(c)** R_vaadin_first drop-and-WARN | a Motif right-align convention with no Vaadin `MenuBar` counterpart. The menu still renders as an ordinary top-level menu; only the *help* distinction is dropped, and `getHelpMenu()` round-trips |
| top-level separators on a `MenuBar` | **(a)** blocked-upstream | inherited verbatim from SD_sjmenubar — Vaadin `MenuBar` has no top-level `addSeparator()`. `PopupMenu` (→ `ContextMenu`) renders them, per SD_sjpopupmenu |
| `PopupMenu.show` at explicit `(x, y)` | **(a)** blocked-upstream | Vaadin `ContextMenu` has no server-side open-at-coordinates. The right-click idiom works natively once the target is bound; same gap `vaadinx.swing.JPopupMenu.show` documents |
| `dispatchEventImpl`'s `newEventsOnly == false` legacy branch (`Event` via `AWTEvent.convertToOld()`) | **(b)** permanently deferred | **mechanically impossible**, not a choice: `convertToOld()` is package-private in `java.awt`. Our `dispatchEventImpl` treats `newEventsOnly` as always true. Observable only for an app that registers no listener, calls no `enableEvents`, and overrides the AWT-1.0 `postEvent` — a 1995 idiom |
| `postEvent(Event)` / `deliverEvent(Event)` AWT-1.0 dispatch | **(b)** permanently deferred | `MenuComponent.postEvent` forwards to the parent and returns false, faithfully; nothing ever generates the old `Event` to forward. Matches the existing stance on `vaadinx.awt.Component.postEvent` |
| `enableEvents` / `disableEvents` | **(c)** R_vaadin_first drop-and-WARN, partial | the `eventMask` bits are stored and consulted by `eventEnabled` (cheap and real); no other effect, since nothing else is masked |
| per-item `setFont` on a `MenuComponent` | **(c)** R_vaadin_first drop-and-WARN *or* a new `MenuNode` field — see Open questions | the field round-trips either way; the question is whether the rendered Vaadin item picks it up |
| `getAccessibleContext()` on all six classes | **(b)** permanently deferred | as everywhere in the repo |
| `removeNotify()` per-item peer dispose | **(b)** permanently deferred | our Vaadin side is torn down wholesale by `rebuildFromTree`; the override is kept and chains, so a user override runs |
| `getName()`'s lazy auto-generated `"menuitem0"` | **(c)** R_vaadin_first drop-and-WARN, silent | matches `vaadinx.awt.Component.getName`'s existing call; returns null when unset |
| `getTreeLock()` identity | **(c)** | returns our own lock, not `java.awt.Component.LOCK` (package-private) |
| `AWTEventListener` global notification in `dispatchEventImpl` | **(b)** permanently deferred | `Toolkit.notifyAWTEventListeners` is toolkit-global dispatch, already out of scope |
| `MenuBar.handleShortcut` toolkit key path | **(b)** permanently deferred | package-private; per-item Vaadin `Shortcuts` replaces it, observably equivalent |

---

## Sampler demo + exit gate

Per the AWT lane's Sampler convention — **one pane per AWT class** (`AwtButtonPanel`,
`AwtLabelPanel`, …), each with its own `Awt<Class>WarnInventoryTest` and one `SamplerCatalogue` line
under category `"AWT"` labelled with the bare JDK class name — the two demos below become two panes,
e.g. `AwtPopupMenuPanel` and `AwtMenuBarPanel`, each with its own exit-gate test.

**Demo 1 — `PopupMenu` on an AWT `Button`.** Entirely in-panel, no `Frame` needed: build a
`PopupMenu` with two `MenuItem`s, a separator, a `CheckboxMenuItem` and one nested `Menu`;
`button.add(popup)`; right-click the button. Readout label shows the last `ActionEvent`'s
`getActionCommand()` and the checkbox's `getState()`. This demo carries the load: it exercises the
whole tree walk, both event types, the separator-is-a-`MenuItem("-")` rule, and nesting — without
needing a window.

**Demo 2 — `MenuBar` on a `vaadinx.awt.Frame`.** A `MenuBar` cannot live in a `JPanel`; it needs a
`Frame`. So: a Swing `JButton` that constructs a `vaadinx.awt.Frame`, gives it a `MenuBar` with a
`File` menu (an item with a `MenuShortcut(VK_S)`, a separator, a `CheckboxMenuItem`), a `Help` menu
installed via `setHelpMenu`, and `setVisible(true)` — rendering as an overlay frame with an AWT
menubar. The accelerator is the point: pressing the platform accelerator + `S` fires the item's
`ActionListener` while the overlay is open. Note in the panel's javadoc that a *pure*-AWT frame is
the rarer case; the realistic residue shape is `setMenuBar` on a `JFrame`, and Demo 2's frame is
chosen because it also proves `SFrame` grew a working menubar slot.

**The two `WarnInventoryTest`s.** User paths: assert the `SJPopupMenu` / `SJMenuBar` peer counts,
click through a popup item and a checkbox item, then open the frame and fire the accelerator.
API-surface buckets:

- `inventory_awt_menu_api_surface` — `MenuComponent` (`get`/`setName`, `get`/`setFont`, `getParent`,
  `removeNotify`, `postEvent`, `toString`) plus `MenuBar` (all ctors, `add`, `getMenu`,
  `getMenuCount`, `countMenus`, `remove(int)`, `remove(MenuComponent)`, `set`/`getHelpMenu`,
  `shortcuts`, `getShortcutMenuItem`, `deleteShortcut`, `addNotify`, `removeNotify`) plus `Menu` (all
  three ctors, `add(MenuItem)`, `add(String)`, both `insert`s, `addSeparator`, `insertSeparator`,
  `remove` ×2, `removeAll`, `getItem`, `getItemCount`, `countItems`, `isTearOff`, `paramString`) plus
  `MenuItem` (all three ctors, `get`/`setLabel`, `isEnabled`/`setEnabled`/`enable()`/`enable(b)`/
  `disable()`, `get`/`setShortcut`, `deleteShortcut`, `get`/`setActionCommand`, listener add/remove/
  query, `getListeners`, `addNotify`, `paramString`).
- `inventory_awt_popup_api_surface` — `CheckboxMenuItem` (three ctors, `get`/`setState`,
  `getSelectedObjects` in both states, `ItemListener` add/remove/query) plus `PopupMenu` (both ctors,
  `getParent`, `addNotify`, and `show` *after* a successful `Component.add(popup)` — expect exactly
  one WARN for the coordinate drop, so `show` is driven from the per-class unit test rather than the
  zero-WARN bucket).

`processEvent` / `processActionEvent` / `processItemEvent` are `protected` and so are driven from the
same-package unit tests, exactly as `vaadinx.awt.ButtonTest` does for `Button`'s pair.

Expected-WARN exclusions from the zero-WARN buckets: `getAccessibleContext` (×6),
`Menu(String, true)`, `PopupMenu.show`'s coordinate drop, `MenuBar`'s top-level separator if one is
added, and `setFont` if the drop-and-WARN option is chosen.

---

## Tests

R_java_karibu_tests shape: Java + Karibu (`LocatorJ`), reaching the rendered tree through `SJMenuBar.getCurrentTree()` and Vaadin
`MenuBar.getItems()` / `ContextMenu.getItems()` — the same handles `SJMenuBarTest` and
`SJPopupMenuTest` already use.

| file | what earns a test |
|---|---|
| `emulators/src/test/java/vaadinx/awt/MenuComponentTest.java` | `getFont()` null at root, non-null once attached under a `Frame`; `setName` round-trip; `getParent()` typed as `MenuContainer`; `dispatchEvent` reaching a `processEvent` override; the unhandled-`ActionEvent`-bubbles-to-parent limb |
| `emulators/src/test/java/vaadinx/awt/MenuBarTest.java` | `add` reparents (item moved out of its old menu); `add(null)` NPEs; `getMenu` AIOOBE both directions; `setHelpMenu` replacing an existing help menu; `shortcuts()` enumerating the whole tree; `getShortcutMenuItem` hit and miss; `deleteShortcut` clearing across menus; the rendered `SJMenuBar` tree matching the AWT tree |
| `emulators/src/test/java/vaadinx/awt/MenuTest.java` | `addSeparator()` is an item with label `"-"` and **counts** in `getItemCount()`; `insert` above the count appends without throwing; `insert(-1)` IAE with the exact message; `insertSeparator` ordering; `removeAll`; a nested `Menu` rendering as a Vaadin `SubMenu`; `isTearOff()` round-trip + the ctor WARN |
| `emulators/src/test/java/vaadinx/awt/MenuItemTest.java` | `getActionCommand()` label fallback including the null-label case; **every setter fires nothing** (a `PropertyChangeListener`-shaped probe is impossible — assert no `ActionEvent` and that no rebuild-visible event escapes); `setEnabled` reaching the rendered item; the effective-enabled cascade rendering disabled while `isEnabled()` still reports true; browser click → `dispatchEvent` → `processEvent` override → `processActionEvent` → listener, with `source == this`; `MenuShortcut` → `KeyStroke` conversion and the installed Vaadin shortcut firing the item |
| `emulators/src/test/java/vaadinx/awt/CheckboxMenuItemTest.java` | **`setState` fires no `ItemEvent`** (the headline divergence from the Swing sibling); a click fires `ItemEvent` and **no `ActionEvent`**; `ItemEvent.getItem()` is the label `String`, not `this`; `getSelectedObjects()` null when off; rendered `checkable`/`checked` following the state |
| `emulators/src/test/java/vaadinx/awt/PopupMenuTest.java` | the full four-rung exception ladder with exact messages; `Component.add(popup)` binding the `ContextMenu` target; a right-click firing a nested item; top-level separator rendering (where `SJMenuBar` drops it); a `PopupMenu` parented to a `Menu` behaving as a plain `Menu` and throwing from `show` |
| `emulators/src/test/java/vaadinx/awt/FrameMenuBarTest.java` | `setMenuBar` / `getMenuBar` round-trip; the bar's element landing at index 0 before the content on an `SFrame`, and (via `JFrame`) at index 0 of the root pane's layered pane under both frame strategies; replacing a bar detaching the old one; `setMenuBar(null)` clearing; the `setJMenuBar`-and-`setMenuBar`-on-one-`JFrame` last-writer-wins WARN |

No new `:surrogates` tests are expected, which is itself worth asserting: if a test has to be added
to `SJMenuBarTest`, the reuse verdict was wrong.

---

## File checklist

Mirroring the Button (`eea1c09`, 12 files) and Label (`4d3eb27`, 11 files) commits, scaled up.

**New — `:emulators` (7):**
- `emulators/src/main/java/vaadinx/awt/MenuContainer.java`
- `emulators/src/main/java/vaadinx/awt/MenuComponent.java`
- `emulators/src/main/java/vaadinx/awt/MenuBar.java`
- `emulators/src/main/java/vaadinx/awt/Menu.java`
- `emulators/src/main/java/vaadinx/awt/MenuItem.java`
- `emulators/src/main/java/vaadinx/awt/CheckboxMenuItem.java`
- `emulators/src/main/java/vaadinx/awt/PopupMenu.java`

**Changed — `:emulators` (2–3):**
- `vaadinx/awt/Component.java` — `implements vaadinx.awt.MenuContainer` in place of the JDK's;
  `remove(MenuComponent)` re-typed and implemented; `add(PopupMenu)` re-typed and implemented
  (target bind + parent set)
- `vaadinx/awt/Frame.java` — `implements MenuContainer`; `setMenuBar` / `getMenuBar` /
  `remove(MenuComponent)` re-typed and implemented
- possibly `vaadinx/swing/JFrame.java` / `JRootPane.java` — handing an AWT bar's `SJMenuBar` to the
  root pane's existing slot

**Changed — `:surrogates` (0–1):** none if the `SFrame` menubar slot is done by element insert;
`com/vaadin/swingbridge/surrogates/SFrame.java` if the slot is lifted. `MenuNode` gains a field only
if the per-item-font question resolves that way.

**Tests (7):** the files in the table above.

**Sampler (5):** `AwtPopupMenuPanel.java` + `AwtMenuBarPanel.java`, their two `WarnInventoryTest`s,
`SamplerCatalogue` (two `"AWT"` lines), and `sampler/description.md` (exit-gate inventory lines).

**Docs (2):** `emulators/decisions.md` (one new slug-named entry, e.g. `D_awt_menus`) and `CLAUDE.md`
(component-surface line). **`surrogates/decisions.md`: no entry**, if the reuse verdict holds.

Total ~25 files, against Button's 12 and Label's 11.

---

## Effort — is it really multi-session?

**Re-estimate: ~2 sessions.** The lane sizing's "multi-session" is right in letter and misleading in
spirit — it reads as *"budget for a hard, open-ended design fight"*, and the design fight is already
won by machinery that landed for Swing.

What makes it cheaper than the row suggests:

- **The expensive half is already built and already layer-neutral.** `MenuTreeBuilder` (211 lines) +
  `MenuNode` (97) + `SJMenuBar` (149) + `SJPopupMenu` (137) — ~600 lines of Vaadin-side rendering,
  accelerator install/teardown, sub-menu recursion, leaf-map recovery, and re-entrancy guarding — is
  consumed unchanged. Zero new surrogate classes.
- **The second root is small.** `MenuComponent` is ~5 real methods, no geometry, no colours, no
  focus, no mouse events, no PCE.
- **The family's event surface is two types, both user-driven only.** Compare
  `vaadinx.swing.JMenuItem`, which juggles `ActionEvent`, `ItemEvent`, `ChangeEvent`, `"accelerator"`
  PCE, `MenuListener`, `MenuKeyListener`, `MenuDragMouseListener` and `ButtonGroup` veto. AWT has
  none of that. Every setter is silent.
- **No icons, no mnemonics, no `Action`, no radio item, no `ButtonGroup`, no `JSeparator` class.**
  Each is a whole sub-mechanism the Swing side needed and this one does not.
- **No constant collisions** (the family declares no public constants), and **`MenuShortcut` needs no
  port at all**.
- The one platform fact needed — the accelerator modifier — is already sitting in
  `vaadinx.awt.Toolkit.getMenuShortcutKeyMaskEx()`, browser-aware.

**The irreducible part, and it is not the menus:**

1. **The `MenuContainer` re-typing ripple.** `vaadinx.awt.Component.remove(MenuComponent)`,
   `Component.add(PopupMenu)`, `Frame.setMenuBar`, `Frame.getMenuBar`, `Frame.remove(MenuComponent)`
   all change signature from a `java.awt.*` type to a `vaadinx.awt.*` one. That is R_no_vaadin_in_api limb 1 doing
   its job, but it is a **breaking change to two landed, widely-inherited classes** (every emulator
   extends `Component`; `JFrame extends Frame`). This is the part that cannot be scoped down or
   deferred: you cannot ship `vaadinx.awt.MenuBar` and leave `Frame.setMenuBar(java.awt.MenuBar)`
   standing.
2. **`SFrame` has no menubar slot.** The slot lives on `SJRootPane`'s layered pane, reachable only
   from root-pane containers. A plain `vaadinx.awt.Frame` peers on `SFrame`. Either lift or
   element-insert — a small job, but a *new* one, and the only candidate for a `:surrogates` change.
3. **The emission-walk duplication.** ~60 lines of `toMenuNode` / `collectChildren` /
   `notifyTreeMutated` / `pushTree`, written a second time for a tree whose separators, icons,
   accelerators and check semantics all differ. Deliberate duplication per SD_sbutton's reasoning, but
   still 60 lines that must be got right twice.
4. **Two behavioural divergences from the Swing siblings that copy-paste will get wrong**:
   `CheckboxMenuItem.setState` fires nothing, and an AWT checkbox click fires `ItemEvent` **only** (no
   `ActionEvent`).

**Session split, if someone picks it up:** session 1 — `MenuContainer` + `MenuComponent` + `MenuItem`
+ `Menu` + `MenuBar` + the `Frame`/`Component` ripple + the `SFrame` slot + four test files.
Session 2 — `CheckboxMenuItem` + `PopupMenu` + `Component.add(PopupMenu)` + the `MenuShortcut`
conversion + three test files + both Sampler demos + the exit gates + the decision entry.

---

## Open questions

Things a human must decide, and things this pass could not verify.

1. **Per-item `setFont`.** `MenuComponent.setFont` is a real AWT idiom and there is nowhere to put it:
   an item has no element. Three options — (a) drop-and-WARN, field round-trips, R_match_swing_errors (c); (b) add a
   `java.awt.Font font` component to `MenuNode` and have `MenuTreeBuilder.addNode` apply it as CSS to
   the Vaadin item — a `MenuNode` change, so a `:surrogates` change, so the zero-`SD*` property goes;
   (c) apply it once at the bar level (`SJMenuBar`'s own element), which covers the common
   "restyle the whole menubar" case and drops per-item. **Leaning (a) for the first slice, (c) as a
   cheap follow-up** — but this is the only genuinely open design call in the family.
2. **`SFrame` menubar slot: lift or element-insert?** Lifting a slot onto `SFrame` (mirroring
   `SJRootPane.setJMenuBar`'s index-0 insert) is cleaner; an emulator-side element insert keeps
   `:surrogates` untouched and preserves the "first slice with no `SD*` entry" property. Cleanliness
   vs. a nice-sounding property is a human call.
3. **`SJMenuBar` / `SJPopupMenu` naming under AWT reuse.** Reading `new SJMenuBar()` inside
   `vaadinx.awt.MenuBar` is a wart. Rename to `SMenuBar` / `SPopupMenu` (typed call sites in
   `SJRootPane`, `SJFrame`, `SJDialog`, `RootPaneScaffold`, the Swing menu emulators, plus tests), or
   accept the wart and document it? The S-prefix rule says the prefix marks the
   module, which argues the current name is *already* fine and the wart is cosmetic.
4. **Mixing `setJMenuBar` and `setMenuBar` on one `JFrame`.** The JDK has two independent slots; we
   have one (index 0 of the root pane's layered pane, under either frame strategy). Last-writer-wins
   plus a WARN is proposed. Is a WARN right, or should the second call
   throw (it is arguably a programming error to install two menubars)? R_match_swing_errors's enumerated throw set is
   deliberately closed, which argues WARN.
5. **`Menu.insert`'s O(n²) faithful implementation.** AWT's remove-tail / add / re-add-tail means an
   insert into a 20-item menu triggers ~40 tree pushes if each `add`/`remove` calls
   `notifyTreeMutated()`. `preventRebuild` guards re-entrancy but not repetition. Either suppress the
   pushes for the duration of `insert` (a batch flag, correct and slightly un-faithful) or accept the
   churn. **Unverified**: whether the churn is actually observable at typical menu sizes.
6. **`GraphicsEnvironment.isHeadless()` in the Vaadin server.** `MenuShortcut.toString()` degrades to
   `"+A"` under headless. I did not verify what `isHeadless()` returns in a running Sampler — it
   depends on `java.awt.headless`, which the test suites set to `true` (root pom's Surefire
   `argLine`) but nothing obviously sets for a running app. If it is true, any
   migrated app that puts `shortcut.toString()` in a UI string shows a broken label, and the fix
   would be a `vaadinx`-side formatter. Needs a one-line probe in a running app.
7. **Is `Component.add(PopupMenu)` binding the target at `add` time correct?** The JDK's `add` only
   sets the parent; the popup becomes *showable* but not *shown*, and `show` is what displays it.
   Binding `ContextMenu.setTarget` at `add` time means right-click works **without** the app ever
   calling `show` — more useful, less faithful. An AWT app that calls `show` from a
   `mousePressed(isPopupTrigger())` handler would then have the popup open on right-click *and* on
   its own trigger logic. Probably harmless (both are right-click), but I could not verify the double
   fire without running it.
8. **What does swapping `Component`'s `java.awt.MenuContainer` for `vaadinx.awt.MenuContainer`
   break?** The grep for `java.awt.MenuComponent` / `MenuContainer` finds only the stub sites, so the
   blast radius looks small — but a signature change on `Component` is the kind of thing that surfaces
   in `:emulators-printing`, the `third-party/` add-ons, and `testapps/`, none of which I compiled.
   **Unverified**: the actual compile blast radius. A full `./mvnw -C clean install` is the only honest
   check, and it is a build, not a read.
