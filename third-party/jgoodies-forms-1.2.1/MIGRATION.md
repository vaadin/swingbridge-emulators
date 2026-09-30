# Migrating an app off `com.jgoodies:forms:1.2.1`

**This file applies if your app depends on `com.jgoodies:forms` version `1.2.1`.** If it depends on
JGoodies Forms 1.9 or later, stop: 1.9 renamed `FormFactory` to `FormSpecs` and changed the API, and
this module provides the 1.2.1 API only.

**This file is written to be followed literally.** Every instruction is stated in full; nothing here
requires another document to resolve.

Not affiliated with, endorsed by, or supported by JGoodies. This module is a fork of BSD-licensed
JGoodies Forms 1.2.1 sources, copyright (c) 2002-2008 JGoodies Karsten Lentzsch. Report problems
with the fork to SB-Emulators, never to JGoodies.

**This module is a starter prototype.** It is free to use (BSD, upstream's terms), it provides exactly
what the "Not ported" section below says and nothing else, and it is shipped as-is, with no further
development planned — the boundary stays where it is.

## Dependency swap

Remove this from your `pom.xml`:

```xml
<dependency>
    <groupId>com.jgoodies</groupId>
    <artifactId>forms</artifactId>
    <version>1.2.1</version>
</dependency>
```

Add this:

```xml
<dependency>
    <groupId>com.vaadin.swingbridge</groupId>
    <artifactId>swingbridge-emulators-jgoodies-forms-1.2.1</artifactId>
    <version>${swingbridge-emulators.version}</version>
</dependency>
```

Remove the original dependency, do not merely add the new one. This module uses a different package
root, so leaving the old jar on the classpath breaks nothing — but it pulls in `java.desktop` and
nothing needs it.

## Version match

The `1.2.1` in `swingbridge-emulators-jgoodies-forms-1.2.1` is JGoodies Forms' version, not SB-Emulators'. It says which API
surface this module provides. SB-Emulators' own version goes in `<version>`.

## Import rewrite

**Two rules, and the order matters. Apply the longest prefix first.**

Rule 1 — rewrite the `forms` subtree:

```
com.jgoodies.forms.  ->  vaadinx.jgoodies.forms.
```

Rule 2 — **delete**, do not rewrite, every import starting with:

```
com.jgoodies.looks.
```

**Do not use a blanket `com.jgoodies.` rule.** This module ships only the `forms` subtree. A blanket
rule rewrites JGoodies Looks imports into `vaadinx.jgoodies.looks.*`, which does not exist in any
jar, and you get a compile error that looks like a missing port when it is actually a wrong rewrite
rule.

JGoodies Looks is a Look-and-Feel library. Look-and-Feel is not emulated at all. So for Looks:

1. Delete every `import com.jgoodies.looks.*;` line.
2. Delete the `UIManager.setLookAndFeel(...)` calls that used those classes, and any
   `Options.set*` / `PlasticLookAndFeel.*` configuration lines around them. Deleting a
   `setLookAndFeel` call never changes application logic — it changes how widgets are painted, and
   in the browser they are painted by Vaadin's theme instead.
3. Remove the `com.jgoodies:looks` dependency from your `pom.xml`.

Example of the whole rewrite:

```java
import com.jgoodies.forms.layout.FormLayout;          // before
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.looks.plastic.PlasticLookAndFeel;

import vaadinx.jgoodies.forms.layout.FormLayout;      // after
import vaadinx.jgoodies.forms.layout.CellConstraints;
// the looks import is deleted, not rewritten
```

The package structure below `vaadinx.jgoodies.forms.` mirrors upstream's, so no other line in your
source changes.

## Not ported

**Provided** — the layout manager and the whole spec model it parses:

- `vaadinx.jgoodies.forms.layout` — `FormLayout`, `CellConstraints`, `ColumnSpec`, `RowSpec`,
  `FormSpec`, `FormSpecParser`, `Size`, `Sizes`, `ConstantSize`, `BoundedSize`, `PrototypeSize`,
  `LayoutMap`
- `vaadinx.jgoodies.forms.factories` — `FormFactory` (including its `RELATED_GAP_COLSPEC`,
  `DEFAULT_COLSPEC`, `RELATED_GAP_ROWSPEC`, `DEFAULT_ROWSPEC` and sibling constants)
- `vaadinx.jgoodies.forms.util` — `LayoutStyle`, `UnitConverter`, `FormUtils`

Both ways of passing constraints work, unchanged:

```java
panel.setLayout(new FormLayout("4dlu, max(65dlu;default):grow, 4dlu", "4dlu, default, 4dlu"));
panel.add(field, "2, 2, fill, default");        // string constraints
panel.add(field, cc.xy(2, 2));                  // CellConstraints object
```

**Not provided — no class file is shipped for any of these:**

- the entire builder layer: `PanelBuilder`, `DefaultFormBuilder`, `ButtonBarBuilder`,
  `ButtonStackBuilder`, and everything else in `com.jgoodies.forms.builder`
- the entire `com.jgoodies.forms.debug` package: `FormDebugPanel`, `FormDebugUtils`
- `Borders`, `ButtonBarFactory`, `ComponentFactory`, `DefaultComponentFactory`
- `AbstractUnitConverter`, `DefaultUnitConverter`, `MacLayoutStyle`

Using any of them is a **compile error naming the missing package or class**, for example:

```
package vaadinx.jgoodies.forms.builder does not exist
```

That error is expected and correct. It means the code was deliberately not ported. Do not try to
create the missing class, and do not add a different jar to supply it — no jar provides these
symbols.

**If your forms are built with `PanelBuilder` or `DefaultFormBuilder` rather than with a raw
`FormLayout`, this module does not cover them.** That is the largest part of JGoodies Forms by
volume. Your options are to rewrite those panels against a raw `FormLayout` — the builders are a
convenience over exactly the same specs — or to rewrite them as native Vaadin layouts.

## Beyond the import swap

Three things to change or check in your own source. None of them is an import rewrite.

**1. Delete code that asks Forms for pixel sizes.** Any call that converts a size to pixels now logs
a warning and returns `0`. These are the affected methods: `UnitConverter.inchAsPixel`,
`millimeterAsPixel`, `centimeterAsPixel`, `pointAsPixel`, `dialogUnitXAsPixel`,
`dialogUnitYAsPixel`, and `ConstantSize.getPixelSize`. There is no pixel answer on a server: the
browser does the sizing, from CSS. If you find such a call, delete it and the arithmetic around it
rather than trying to make it return something — code that lays out by computing pixels has to be
rewritten to state its intent in specs.

**2. `LayoutStyle` is pinned to the Windows style.** Upstream chose Mac or Windows gaps by reading
the operating system's name. On a server that names the wrong machine — the one running the servlet,
not the one showing the form. Upstream's `MacLayoutStyle` is not provided, so there is nothing to
switch to out of the box. If you need different gaps, write your own subclass of `LayoutStyle` and
install it once during startup with `LayoutStyle.setCurrent(yourStyle)`. Otherwise change nothing —
this needs no action in almost every app.

**3. Column groups, row groups and cell insets have no effect.** Calls to
`FormLayout.setColumnGroups`, `setRowGroups`, and `CellConstraints` built with an `Insets` argument
still compile and still run, but log a warning and are ignored, because they need measured content
widths that CSS does not expose. Also, `FormLayout.setHonorsVisibility` is stored and returned but
does not change the layout: track sizes come from the column and row specs, not from which children
are currently visible. If any of those three is load-bearing in a form, that form needs a layout
rewrite; otherwise remove the calls to silence the warnings.

## Known divergence

- **Layout fidelity is structural, not pixel-perfect.** The right components land in the right cells,
  spanning and filling as the specs say. Gap widths, fonts and the last pixel are not reproduced.
  A form that looks slightly differently proportioned is expected; a form whose fields are in the
  wrong cells is a bug — report it.
- **Dialog units become font-relative.** A `dlu` is defined against a Look-and-Feel dialog font,
  which does not exist on a server, so dialog units map to the CSS `ch` and `em` units:
  `max(18dlu;default)` becomes `minmax(3.96ch, auto)`. Sizes therefore follow the browser's real
  font at any zoom level instead of a fixed pixel count. If your forms come out consistently too
  wide or too narrow, that is a single calibration constant on our side — report it rather than
  editing your specs.
- **Gap specs stay real tracks.** A gap spec is still an entry in the spec array, so a
  `CellConstraints` column or row index means exactly what it meant in Swing. Constraint strings
  copied from your Swing source stay correct.
- **Warnings in the log are informative, not errors.** `FormLayout setColumnGroups`,
  `CellConstraints.insets`, `PrototypeSize maximumSize` and `CssUnitConverter` warnings all mean
  "this input was dropped", and the app keeps running.

## Verify

After applying everything above, all of these must hold:

```bash
grep -rn 'com\.jgoodies' src/         # must print nothing — covers forms and looks
grep -rnE 'setLookAndFeel.*([Pp]lastic|[Ww]indows[Ll]ooks|com\.jgoodies)' src/   # must print nothing
mvn -q compile                        # must succeed
```

If the first command prints a `com.jgoodies.forms.` line, an import rewrite was missed. If it prints
a `com.jgoodies.looks.` line, rule 2 was not applied.

**The second gate is about JGoodies Looks only, not `setLookAndFeel` as such.** A call naming a *JDK*
look-and-feel (`UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel")`) is
fine and stays: `guide.md` classifies look-and-feel selection as *ignore* — the call is inert under
SB-Emulators, not harmful. An unqualified `grep -rn 'setLookAndFeel'` would tell you to delete code the core
guide tells you to keep, which is why this one is narrowed to the Looks class names. If the compile fails naming a
`vaadinx.jgoodies.forms` class, see "Not ported" — that is the boundary, not a bug.
