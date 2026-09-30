# `FormerSingletons` — where your tab-scoped state lives

Reference for [`static-fields.md`](./static-fields.md)'s **tab scope** verdict — the default the
decision tree falls through to. That verdict says *where* a former `static` belongs; this page says
*in what*, concretely, in code you write.

You write this class **once per app**. After that, each field the sweep routes to tab scope is a
three-line edit against it.

**Keep the uncomfortable name.** It preserves your app's global-widget shape and makes it *correct*
(per-user) rather than *broken* (shared by everyone) — the right trade for this stage, and not an
endorsement. Renaming it to something architectural would hide that; see
[Emptying it](#emptying-it).

## Conventions

Two placeholders appear in the command on this page. Substitute both with real absolute paths before
running anything — an agent driving this guide is handed them already substituted.

- **`SWINGBRIDGE_HOME`** — the folder holding the SwingBridge Emulators kit: this file's own
  grandparent, i.e. the directory you unzipped, or your clone of the repository.
- **`MIGRATED_APP_FOLDER`** — the root of the app you are migrating.

## The class

```java
public final class FormerSingletons implements Serializable {

    /** The holder for this app instance, created on first touch. */
    public static FormerSingletons get() {
        return vaadinx.AppInstance.get(FormerSingletons.class, FormerSingletons::new);
    }

    // One member per former static, in origin order.
    public MainFrame mainFrame;               // was MainFrame._instance
    public JLabel    appStatusBar;            // was App.statusBar
    public JPanel    appBodyPanel;            // was App.bodyPanel
    public JButton   confirmDialogOkButton;   // was ConfirmDialog.OK_BUTTON

    // ...except a former static that carried an INITIALIZER, which becomes a
    // lazy accessor so class-load semantics survive intact. See "Fields with
    // an initializer" below.
    private JPanel toolbarP;                  // was Toolbar.P = new JPanel()
    public  JPanel toolbarP() {
        if (toolbarP == null) toolbarP = new JPanel();
        return toolbarP;
    }
}
```

**Public fields, not getter/setter pairs**, for the plain case: one substitution rule covers reads and
writes, there is nothing to author per field, and the member count diffs directly against the
[Phase 6 static gate](./guide.md#S_guardrails)'s flagged set
([Checking you finished](#checking-you-finished)). Neither shape is visible from a call site anyway,
because reads go through an accessor — see [the accessor rule](#FS_accessor).

**`AppInstance` is the resolver SB-Emulators ships, and it is a lifetime rather than a mechanism:**
state that lives exactly as long as one running app instance, reachable everywhere that app runs.
That is the [tab-scope row](./static-fields.md#the-three-scopes)'s lifetime; it is stored in the
browser tab today, and deliberately not part of what you write, so the storage can change without
your code changing. Use it rather than reaching for the tab scope yourself — the contexts it covers
are ones you would not discover before shipping, and [What still throws](#what-still-throws) is the
list of the ones it does not.

## The transform, field by field

| site | before | after |
|---|---|---|
| declaration | `static JButton OK_BUTTON;` on `ConfirmDialog` | deleted; `public JButton confirmDialogOkButton;` on the holder |
| **the reader** — kept if it exists, **created if it does not** | `ConfirmDialog.OK_BUTTON` (a bare field) | `static JButton getOkButton() { return FormerSingletons.get().confirmDialogOkButton; }`, left on `ConfirmDialog` |
| read call sites | `ConfirmDialog.OK_BUTTON` | `ConfirmDialog.getOkButton()` |
| an accessor you already had | `if (_i == null) _i = new MainFrame(); return _i;` | `return FormerSingletons.get().mainFrame;` |
| its call sites | `MainFrame.getInstance()` | **unchanged** |
| write | `OK_BUTTON = ok;` (inside `ConfirmDialog`) | `FormerSingletons.get().confirmDialogOkButton = ok;` — direct, no shim |
| the construction an accessor used to hide | implicit, on first call | explicit in `mainUI()`, assigning the holder member |

<a id="FS_accessor"></a>
### The holder is internal storage — every read goes through an accessor

**Exactly one accessor per member touches `FormerSingletons`: the static getter that replaced the
original `static` field. Every other call site calls that getter, and nothing else in your app names
`FormerSingletons` at all.** Keep the accessor where your app already had one, create it where it did
not — either way call sites end at `Owner.getX()`.

```java
// BEFORE
public static JButton OK_BUTTON;                          // on ConfirmDialog
    ...
if (ConfirmDialog.OK_BUTTON != null) { ... }              // read from anywhere

// AFTER — one accessor, written once, still on ConfirmDialog
public static JButton getOkButton() {
    return FormerSingletons.get().confirmDialogOkButton;
}
    ...
if (ConfirmDialog.getOkButton() != null) { ... }          // every call site, mechanically
```

A bare `public static final JPanel P` forces its call sites to change under any scheme — a field read
cannot become a holder read without becoming a method call — so the accessor is free at the call site,
and it buys four things:

- **The sweep becomes mechanical.** Write the accessor once and every existing reference is a rename;
  no call site has to learn about tab scope, the holder, or `get()`. Without it, each reference is a
  small judgement, and there are as many as your app has readers. (The getter keeping its `static` is
  [`Q_method_or_type`](./static-fields.md#the-decision-tree)'s reader carve-out, not a new exception.)
- **The storage decision becomes reversible, so stop deliberating over it.** Moving state between the
  owning component and this holder is then a one-file change. That answers the question this rule is
  most often asked alongside — *a widget is built by one parent but read from outside it; does that
  outside reader force a holder member?* It does not, and it does not have to be right first time.
- **The call site keeps naming your class.** `Toolbar.getP()` reads like the code you wrote;
  `FormerSingletons.get().toolbarP` announces a junk drawer at every use — and it hides the holder's
  shape, so the public-field-vs-lazy-accessor distinction never leaks.
- **The holder stays reviewable.** Its members are read in one place each, so what is tab-scoped and
  why is answerable from this one class plus its accessors.

Your `MainFrame.getInstance()` already *is* such an accessor, so the main frame needs no special
handling. **Writes stay direct**, because the assignment site is nearly always inside the owning
class, where the holder reference is no less local than a shim would be; add a setter shim only for
the rarer field written from outside (`App.statusBar = x` from another class).

**Where to put the state, in one sentence:** on the natural owner when there is one — `AppFrame` both
builds and writes `currentWindow`, so it owns it, and tab scope comes free because `AppFrame` is
itself resolved per tab through the holder — and in the holder when no class owns it. The example
above is the second case: a `ConfirmDialog` is constructed per use, so nothing about it is resolved
per tab and the holder is the only tab-scoped place its button can live.

### Naming

Deterministic, so that two people — or two agents — produce the same name:

- the classic self-typed singleton (field type == owning class) → **name it after the type**, lowerCamel:
  `MainFrame._instance` → `mainFrame`;
- otherwise → **`<OwningSimpleName><FieldName>`**, lowerCamel, `SCREAMING_SNAKE` camelized:
  `ConfirmDialog.OK_BUTTON` → `confirmDialogOkButton`;
- two classes with the same simple name → prefix the last package segment.

Readers are `get<FieldName>()`, `SCREAMING_SNAKE` camelized; if that name is taken on the class, use
`<fieldName>()`.

## Fields with an initializer

A field that carried its own initializer — `static final JPanel P = new JPanel();` — becomes a **lazy
accessor** on the holder rather than a plain member:

```java
private JPanel toolbarP;
public JPanel toolbarP() {
    if (toolbarP == null) toolbarP = new JPanel();
    return toolbarP;
}
```

This reproduces what `static` gave you exactly: per-field, built once, on first touch. That matters for
one reason above all — **cross-class dependencies keep resolving themselves**:

```java
class A { static final JPanel P = new JPanel(new BorderLayout()); }
class B { static final JLabel L = new JLabel(); static { A.P.add(L); } }
```

On the desktop, `B`'s initializer touching `A.P` triggers `A`'s class init, and the order sorts itself
out. If you instead move every initializer into one eager sequence — the holder's constructor, or
`mainUI()` — *you* have to work out the correct order, and getting it wrong fails silently.

> **This memoization is fine**, even though `static-fields.md` tells you to delete the
> `if (_instance == null)` line from a singleton accessor. That rule is about an accessor **creating a
> singleton**. A tab-scoped field building itself on first use is the scope mechanism doing its job, and
> it cannot leak across users — the holder it lives in is already per-app-instance.

**There is no runtime warning for getting this wrong.** Constructing a Swing emulator with no UI present
does not fail — it quietly succeeds, and the widget is then shared by every user of your server, forever,
with no symptom until two people use the app at once. The two exceptions announce themselves:
`new JSpinner(new SpinnerDateModel())` and `new Timer(...)` both throw with a message naming the cause.
If you see either message coming from a class initializer, you have found one of these fields — and
probably its neighbours too.

One more silent case worth knowing: a `JFrame` built at class-load never registers for `WINDOW_CLOSING`,
so its shutdown handler simply never runs. (Shutdown code that reads *the holder* is fine — see
[What still throws](#what-still-throws).)

## `static { }` blocks

Most of them need nothing. Triage before you touch one:

**1. Touches nothing tab-scoped → leave it exactly as it is.** Registering a JDBC driver, filling a
`static final Map` of constants, setting a system property, building a logger. This is most static
blocks in a real app, and rewriting them is pure risk for no gain.

**2. Configures its own class's static UI fields → fold each statement into the lazy accessor of the
field it configures.** This is the common UI shape, and it is really just *the rest of that field's
initializer*, split off only because a Java field initializer has to be a single expression:

```java
// BEFORE                                    // AFTER, on the holder
static final JPanel P = new JPanel();        private JPanel toolbarP;
static {                                     public JPanel toolbarP() {
    P.setLayout(new BorderLayout());             if (toolbarP == null) {
    P.add(new JLabel("x"));                          toolbarP = new JPanel();
}                                                    toolbarP.setLayout(new BorderLayout());
                                                     toolbarP.add(new JLabel("x"));
                                                 }
                                                 return toolbarP;
                                             }
```

A block touching several fields usually decomposes cleanly, because each statement configures one of
them: `static { P.add(L); }` belongs to `toolbarP()`, which calls `toolbarL()` on its way, and the
ordering resolves itself just as class-init did.

**After that, nothing tab-scoped is left in the block** — it is either empty (delete it) or entirely
benign (leave it). You never need to rebuild the "runs once, before anything uses it" guarantee
somewhere else; it moved into the accessors along with the code.

> **Do not extract the block into an `init()` method behind an `initialized` flag**, called from the
> class's constructor and its static entry points. It looks like the obvious port, and it is a trap: you
> would be reimplementing the JVM's class-initialization barrier by hand, and *every* static method,
> static field read and constructor on the class has to remember to call it — forever, including the ones
> added next year. There is no way to check you got them all.

**Two shapes still need your judgement**, and they are the only ones on this page that do:

- a block whose effect is not on a field at all — `Toolkit.getDefaultToolkit().addAWTEventListener(...)`,
  a global registry hook. There is nothing to fold it into; move it to `mainUI()` so it runs per app
  instance. (`UIManager.setLookAndFeel` needs no decision — look-and-feel is not emulated, so the call is
  inert.)
- a block where the order *between* two different fields genuinely matters, for a reason "each statement
  belongs to the field it configures" cannot see.

A block reading `Preferences` or the browser time zone is already covered by
[the early-read recipe](./static-fields.md#static-initializer-reading-the-browser-zone-or-preferences--defer-to-a-ui-thread-call-site),
and both throw with a message naming the cause, so they report themselves.

## What must not go in

- **User-specific state** — `currentUser`, `isLoggedIn`, a user's connection. That goes in a
  `VaadinSession` attribute. It *works* here today, and becomes a bug the moment a user opens a second
  tab: each tab would get its own login. This holder is for what belongs to one running **app instance**;
  identity belongs to the person.
- **Application-wide read-mostly state** — immutable config, reference data, a connection pool, an ID
  sequence. That stays `static`. A member here would multiply it per tab.
- **Components the tree already owns — including ones that *did* have a `static`.** If a widget is
  built by one parent and read nowhere outside it,
  [the tree is the storage](./static-fields.md#the-decision-tree): it becomes a plain instance field on
  that parent, not a member here. This is the biggest single reason a real app's holder comes out small
  — the classic shape is a `menuBar` / `bodyPanel` / `toolBarPanel` trio of `static` fields on the very
  frame that builds them, all three of which leave the sweep as instance fields. A component that never
  had a `static` stays put for the same reason: this holder is filled by the sweep, not by ambition.
  **A reader outside the parent does not by itself overturn this** — see
  [the accessor rule](#FS_accessor), which is what makes the question cheap to get wrong.

**One thing that does go in, but not because it should:** a widget belonging to a short-lived dialog,
because it *was* in a `static`. Dialog #2's constructor overwrites dialog #1's — exactly what your app
already did — and one detached widget stays reachable until the tab closes. That is your app's original
behaviour preserved, now bounded by the tab rather than by the life of the server, and it is not a leak
this migration introduced.

## One holder, or several?

**One, unless a compiler stops you.** Put it in the module that holds `main()` / your `@MainWindow`
frame, and do not split because the class is getting long — a real app's holder comes out small, most
of its `static` fields having resolved [somewhere other than here](#what-must-not-go-in).

Split only when one holder genuinely **cannot compile**: a multi-module app where no single module can
see every member's type without you adding a dependency edge, or without pulling component types into a
module [`static-fields.md`'s Step 0](./static-fields.md#step-0--classify-your-modules-before-you-touch-a-field)
just classified UI-free. Then one holder per **module**, named `<Module>FormerSingletons`, and which
holder a member goes in is not a choice: **the module its original `static` lived in.** Not per
*feature* — feature boundaries are opinion, so two people sweeping the same app get two partitions and
a field with two plausible homes has no tiebreak.

What splitting costs is the single-glance property: one file that *is* your app's former global state.
It costs nothing structurally (`AppInstance` keys on the class) and nothing at the call sites (they all
go through accessors), and you keep the [completeness diff](#checking-you-finished) by enumerating your
holders with `grep -rn "AppInstance.get("`.

## What still throws

`get()` needs the app's context, so it fails loudly rather than guessing:

- **From a background thread** — a `SwingWorker.doInBackground` body, a raw `Thread`. Read what you need
  on the UI thread *before* starting the work and pass it in. This is the one context the holder cannot
  be reached from at all.
- **From a `static` initializer or `main()`**, before any UI exists. Move the read into UI code — a frame
  constructor, a listener body, `mainUI()`.

**Your shutdown code is not one of these.** A `WINDOW_CLOSING` handler runs with no current UI, but the
resolver reaches the holder there anyway — so cleanup that flushes a cache or saves preferences out of a
former `static` keeps working, unchanged.

Failing loudly is the point: returning `null` or a fresh instance would turn a one-line fix at the call
site into a bug that surfaces later as somebody else's data.

## Checking you finished

Every field the [Phase 6 static gate](./guide.md#S_guardrails) flags must end up in exactly one of
three places: a member of this holder, a
`VaadinSession` attribute, or an allowlist annotation saying it is genuinely application-wide. **A
flagged field in none of the three is an unfinished sweep** — which turns "did we get them all?" from a
review question into a diff.

**Run the diff rather than comparing counts.** The sweep tool takes both trees and gives every
pre-migration row a fate. The first tree is the snapshot
[Phase 1](./guide.md#S_run_static_sweep) keeps of the stage-1 classes; an in-place migration has no
other copy of them.

```bash
SWINGBRIDGE_HOME/tools/bin/static-sweep \
    MIGRATED_APP_FOLDER/swingbridge-migration/stage1-classes \
    --diff MIGRATED_APP_FOLDER/target/classes \
    --lib MIGRATED_APP_FOLDER/target/dependency \
    --report MIGRATED_APP_FOLDER/swingbridge-migration/reports/static-sweep-diff.md
```

Each row comes back *gone* (routed, or its class is gone), *made a constant*, *annotated* with the
`Reason` it claims, or **still unvetted** — and the report closes with the `static` fields the
migration itself introduced, which owe the same verdict. **Still unvetted** is the only row type that
is a finding; the rest are the work, recorded. A field whose class you *renamed* shows as *gone* and
again among the new statics: same field, and nothing in the class files ties the two names together.
**The fates are at the end of the report**, under `## Completeness diff against …`. Everything above
that heading reprints the stage-1 worklist unchanged, so start reading from the heading.

If you [split into several holders](#one-holder-or-several), enumerate them with
`grep -rn "AppInstance.get("` first, so the diff is against all of them rather than the one you
remembered.

## Emptying it

A member leaves this class when its users take the reference properly instead — passed to a constructor,
or owned by the component that uses it. The holder shrinks, and when it is empty you delete it. Its
member count is a usable progress metric for that work.
