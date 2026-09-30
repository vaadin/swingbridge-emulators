# Third-party library add-ons — the index

## Conventions

One placeholder appears in the paths below. Substitute it with a real absolute path.

- **`SWINGBRIDGE_HOME`** — the folder holding the SwingBridge Emulators kit: this file's own
  grandparent, i.e. the directory you unzipped, or your clone of the repository.

Some third-party Swing libraries have a **pre-built SB-Emulators add-on**: a jar that provides that library's
API on top of `:emulators`, so your app keeps compiling after the import swap instead of needing the
library ported by hand. This file lists every add-on that exists, keyed by the dependency you already
have in your `pom.xml`.

**Every add-on listed here is a starter prototype.** It is free to use under its own licence, it
covers what one real migration needed and no more, and **it is shipped as-is, with no further
development planned** — the boundary each `MIGRATION.md` describes is where it stays. Read the
add-on's "Not ported" section before choosing this route, so the boundary does not surprise you on
your first call into the library.

**This file is only an index.** It carries enough to decide *whether* to use an add-on. The
instructions for actually using one live in the add-on itself, as a `MIGRATION.md` — see
[Reading an add-on's instructions](#reading-an-add-ons-instructions) below. That split is deliberate:
this file changes when SB-Emulators gains an add-on, the instructions change when an add-on changes, and
neither ever restates the other.

For a library that is **not** listed here, go back to
[`third-party-libraries.md`](./third-party-libraries.md) — it carries the
triage recipe (does an import-swap of this library work, misbehave, or merely compile?) and the three
outcomes for a library with no add-on.

## The add-ons

| your dependency | replace with | you get |
|---|---|---|
| `com.toedter:jcalendar:1.4` | `com.vaadin.swingbridge:swingbridge-emulators-jcalendar-1.4` | `JDateChooser`, reimplemented over the Vaadin date picker. `JDateChooser` only — the rest of JCalendar is not provided. |
| `com.jgoodies:forms:1.2.1` | `com.vaadin.swingbridge:swingbridge-emulators-jgoodies-forms-1.2.1` | `FormLayout` and its full spec model, with the pixel engine replaced by CSS Grid. The builder layer (`PanelBuilder`, `DefaultFormBuilder`) is not provided. |

Three rules apply to every row:

1. **The version in the artifactId is the *upstream* version**, because it says which API surface you
   get. `swingbridge-emulators-jgoodies-forms-1.2.1` will not compile against code written for Forms 1.9, which renamed
   `FormFactory` to `FormSpecs`. If your version differs from the row, you are in
   [`third-party-libraries.md`](./third-party-libraries.md#libraries-with-no-add-on--the-three-states)'s
   "fork it yourself" case, not this table. SB-Emulators' own version goes in `<version>`.
2. **Every add-on is a subset, and the boundary is a compile error.** Only what a real migration
   needed is ported. Reaching for anything else fails at build time naming the exact missing class,
   rather than blowing up in production — no jar anywhere provides those symbols, because the
   namespace is SB-Emulators-owned. Each add-on's `MIGRATION.md` lists what is in and what is out.
3. **They are not affiliated with or endorsed by their upstream projects.** Report problems with an
   add-on to SB-Emulators, never upstream.

## Reading an add-on's instructions

Each add-on ships its migration instructions **twice**: at `MIGRATION.md` in its module directory
beside these guides, and inside its jar at `META-INF/emul/MIGRATION.md`. The two are the same file.
Use whichever you can reach.

**In the kit** — `SWINGBRIDGE_HOME/third-party/jcalendar-1.4/MIGRATION.md` and
`SWINGBRIDGE_HOME/third-party/jgoodies-forms-1.2.1/MIGRATION.md`. (Paths rather than links, because
these guides are meant to be copied into the app you are migrating, where a relative link out of the
folder would point at nothing.)

**From the artifact, with no checkout.** Fetch the jar into the local Maven repository and read the
entry out of it. Substitute the coordinate from the table above and SB-Emulators' version for `<version>`:

```bash
mvn -q dependency:copy \
    -Dartifact=com.vaadin.swingbridge:swingbridge-emulators-jcalendar-1.4:<version> \
    -DoutputDirectory=target/swingbridge-addons

unzip -p target/swingbridge-addons/swingbridge-emulators-jcalendar-1.4-<version>.jar META-INF/emul/MIGRATION.md
```

This works offline against any repository that has the artifact, including an internal mirror — the
instructions travel with the dependency, so nothing has to be downloaded from the internet and no
source leaves your machine.

**Read it, do not vendor it.** Unpack into a build directory, follow the instructions, and let the
copy be deleted with the rest of `target/`. Do not commit an add-on's `MIGRATION.md` into your app's
source tree: it belongs to the add-on's version, and a copy in your repository goes stale silently.

## For maintainers

Adding an add-on means adding a row above. Each add-on module's own `MigrationDocTest` asserts that
this file names both its Maven artifactId and the upstream `groupId:artifactId` it replaces, so a
missing row fails that module's build rather than shipping an add-on no migrator can find.
