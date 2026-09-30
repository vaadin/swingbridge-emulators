# Migrating an app off `com.toedter:jcalendar:1.4`

**This file applies if your app depends on `com.toedter:jcalendar` version `1.4`.** If it depends on
a different JCalendar version, stop: this module provides the 1.4 API and nothing else.

**This file is written to be followed literally.** Every instruction is stated in full; nothing here
requires another document to resolve.

Not affiliated with, endorsed by, or supported by the JCalendar project. Report problems with this
module to SB-Emulators.

**This module is a starter prototype.** It is free to use, it provides exactly what the "Not ported"
section below says and nothing else, and it is shipped as-is, with no further development planned —
the boundary stays where it is.

**Licence: LGPL-2.1**, the same licence as the `com.toedter:jcalendar:1.4` you are replacing — so
this swap changes nothing about your legal position. (SwingBridge Emulators' own modules are
GPLv2 + Classpath Exception; each add-on instead takes the licence of the library it stands in for.)
Linking your application against it puts your own source under no obligation. What LGPL-2.1 §6 does
ask, and only when you **distribute** your application, is that whoever receives it can replace this
library with a modified build — which the Vaadin-Boot packaging the migration guide sets up already
satisfies, because it ships every dependency as its own jar under `lib/`. **Hosting your
application is not distributing it**, so a cloud-hosted or SaaS deployment triggers none of this.
The full text is in the jar at `META-INF/LICENSE`.

## Dependency swap

Remove this from your `pom.xml`:

```xml
<dependency>
    <groupId>com.toedter</groupId>
    <artifactId>jcalendar</artifactId>
    <version>1.4</version>
</dependency>
```

Add this:

```xml
<dependency>
    <groupId>com.vaadin.swingbridge</groupId>
    <artifactId>swingbridge-emulators-jcalendar-1.4</artifactId>
    <version>${swingbridge-emulators.version}</version>
</dependency>
```

Remove the original dependency, do not merely add the new one. This module uses a different package
root, so leaving the old jar on the classpath breaks nothing — but it pulls in `java.desktop` and
nothing needs it.

## Version match

The `1.4` in `swingbridge-emulators-jcalendar-1.4` is JCalendar's version, not SB-Emulators'. It says which API surface this
module provides. SB-Emulators' own version goes in `<version>`.

## Import rewrite

Add one rule to the import rewrite you are already running over your sources:

```
com.toedter.  ->  vaadinx.toedter.
```

That is the only rule for this library. The package structure below `vaadinx.` mirrors JCalendar's,
so no other line in your source changes. Example:

```java
import com.toedter.calendar.JDateChooser;      // before
import vaadinx.toedter.calendar.JDateChooser;  // after
```

## Not ported

One class is provided: `vaadinx.toedter.calendar.JDateChooser`. Of its API, these four members
exist:

- the no-argument constructor `JDateChooser()`
- `Date getDate()` — returns `null` when the field is empty
- `void setDate(Date)`
- `String DATE_PROPERTY` — the bound property name, fired on change from either side

Everything else in JCalendar ships **no class file and no method**. These classes do not exist:
`JCalendar`, `JDayChooser`, `JMonthChooser`, `JYearChooser`, `JSpinField`, `JTextFieldDateEditor`,
`JDateChooserCellEditor`, `JLocaleChooser`, `IDateEvaluator`, `IDateEditor`. These `JDateChooser`
methods and constructors do not exist: the constructors taking an `IDateEditor`, a `JCalendar` or a
date-format string, `getCalendar`, `setCalendar`, `getDateFormatString`, `setDateFormatString`,
`getJCalendar`, `getCalendarButton`, `getDateEditor`.

Using any of them is a **compile error naming the missing symbol**, for example:

```
package vaadinx.toedter.calendar.JDateChooserCellEditor does not exist
```

That error is expected and correct. It means the class was deliberately not ported. Do not try to
create the missing class, and do not add a different jar to supply it — no jar provides these
symbols. Rewrite the code that used it, or drop the feature.

## Beyond the import swap

Two changes are needed in your own source. Neither is an import rewrite.

**1. `JDateChooser` is no longer a `JPanel`.** Upstream JCalendar's `JDateChooser` extends
`javax.swing.JPanel`. This one extends `vaadinx.swing.JComponent`. Any cast to `JPanel`, and any
call to a `Container` method such as `add`, `remove`, `getComponents` or `setLayout` on a chooser,
is a compile error. Fix it by deleting the cast or the call — a date chooser has no children to
manage. Everything a `JComponent` provides still works: `setEnabled`, `setBackground`,
`setToolTipText`, `addPropertyChangeListener`, adding the chooser to a container, and
`instanceof JDateChooser`.

**2. The app must register `SwingBridgeEmulatorsBootstrap`.** Showing a `java.util.Date` in the
date picker converts it to a day in the browser, and that conversion needs the browser's time zone. The zone
is fetched by `vaadinx.swing.app.SwingBridgeEmulatorsBootstrap`, which every migrated app registers itself — SB-Emulators
libraries never self-register it. If your app is a plain Vaadin app, add this line to your own
`src/main/resources/META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener`:

```
vaadinx.swing.app.SwingBridgeEmulatorsBootstrap
```

If your app runs on Spring, expose it as a bean instead:

```java
@Bean
VaadinServiceInitListener swingBridgeEmulatorsBootstrap() { return new vaadinx.swing.app.SwingBridgeEmulatorsBootstrap(); }
```

Without this registration, `setDate` throws an `IllegalStateException` telling you the same thing,
and so does showing a chooser whose date was set on a background thread. They throw rather than
guessing UTC, because a guessed zone produces dates that are off by one day in a way nobody notices
until a customer does. `getDate` never converts, so it works on any thread, `doInBackground()`
included.

No library-wide singletons, factories or services need configuring for this module.

## Known divergence

- **It renders as a Vaadin date picker**, not as JCalendar's hand-drawn day grid. The user sees a
  text field with a calendar overlay. This is deliberate and will not change.
- **`setDate(null)` clears the field**, and `getDate()` then returns `null`. Same as upstream.
- **A date the user picks reads back as midnight** of that day, in the browser's time zone. Upstream
  keeps the time of day of the date it held before. A date you set with `setDate` reads back
  exactly, time of day included, as upstream.
- **The date-format constructors are gone**, so the displayed format follows the Vaadin date
  picker's locale handling rather than a format string you pass in.
- **Dropping `getDateEditor()` changes behaviour, not only the API.** Upstream code reaches the
  chooser's text field through it, typically to disable typing while leaving the calendar button
  live (`chooser.getDateEditor().setEnabled(false)`). A Vaadin date picker has no separable editor,
  so there is nothing to disable: delete the call, and the field stays typeable. Disable the whole
  chooser with `setEnabled(false)` if that is close enough; otherwise the feature goes.

## Verify

After applying everything above, all three of these must hold. **Run them only once the dependency
swap is on your build**: before that the grep passes on imports nothing on the classpath resolves,
and the compile is not yet checking this module's surface.

```bash
grep -rn 'com\.toedter' src/          # must print nothing
mvn -q compile                        # must succeed
```

and a `grep -rn 'JPanel' src/` over the files that use a chooser must show no cast of a chooser to
`JPanel`. If the first command prints anything, an import rewrite was missed. If the compile fails
naming a `vaadinx.toedter.calendar` class, see "Not ported" — that is the boundary, not a bug.
