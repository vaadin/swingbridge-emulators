---
name: migrate-testapp
description: Migrate one of the SwingBridge Emulators kit's bundled example Swing apps (crud, jlawyer-shape, inventory) onto :emulators, so it runs in a browser. Use when the user asks to migrate, port or try a bundled example, or types /migrate-testapp.
---

## What this does

Migrates a bundled example app from Swing to a running Vaadin web app, following the kit's own
guides — the same instructions, and the same tool commands, a customer migrating their own app gets.

The app is migrated in a **copy**, so this kit stays pristine and the command stays re-runnable.

## Procedure

**1. Resolve the two paths.** `SWINGBRIDGE_HOME` is this kit's root — the folder holding
`README.md`, which is this skill's own `../../..`. `<app>` is the argument: `crud`, `jlawyer-shape`
or `inventory` (`ls SWINGBRIDGE_HOME/testapps/` if the user named something else; a
`<app>.placeholder` file there means that app is not in this release, and its text says why).
`inventory` is a third party's app shipped on its author's terms: its `@author` headers stay intact in
the migrated copy, and its `PROVENANCE.md` says what else the grant asks.

**2. Copy the app out of the kit.** Never migrate in place under `testapps/` — that tree is
shipped content, and `testapps/crud/1-emulators/` is a finished migration that must survive:

```bash
mkdir -p SWINGBRIDGE_HOME/work
if [ -d SWINGBRIDGE_HOME/work/<app> ] && diff -rq SWINGBRIDGE_HOME/testapps/<app>/swing SWINGBRIDGE_HOME/work/<app> >/dev/null; then
  echo "keeping the untouched copy from a previous attempt"
else
  rm -rf SWINGBRIDGE_HOME/work/<app>
  cp -r SWINGBRIDGE_HOME/testapps/<app>/swing SWINGBRIDGE_HOME/work/<app>
fi
```

An existing copy is kept only when it is still identical to `swing/` — a previous attempt that
stopped before editing anything, typically. Anything edited is replaced, so the command stays
re-runnable. `MIGRATED_APP_FOLDER` is `SWINGBRIDGE_HOME/work/<app>`. If the user named a different
destination, use theirs.

**3. Offer to run the app first.** `SWINGBRIDGE_HOME/testapps/<app>/README.md` says how to launch
the desktop app and what to expect on screen. Knowing what it looked like before is what makes the
migrated version judgeable — but it needs a display, so ask rather than assume.

**4. Do the migration — in this session.** Read
`SWINGBRIDGE_HOME/guides/1-swing-to-emulators/agent-prompt.md` and follow the prompt section between
its two `---` dividers as your own instructions, resolving `SWINGBRIDGE_HOME` and
`MIGRATED_APP_FOLDER` to the real absolute paths as you read them. **At the guide's Phase 0
bootstrap step, do not ask — each shipped testapp has its lane:** `crud` on Spring Boot, launched
as a fat jar, `inventory` and `jlawyer-shape` on Vaadin Boot. Between them they cover both bootstraps, and the
finished `crud/1-emulators/` is on Spring Boot.

**Do not delegate this to a subagent.** A spawned agent works where the user cannot watch it and
reports back a summary — and for a migration, *where it hesitated* is the interesting part.
Working here keeps it visible as it happens, and leaves you holding the detail step 5 has to
report.

**5. Report.** Hand the user the URL to open, and say which flows you clicked through and what you
saw — a thorough look at the UI is theirs. Say plainly whether the app compiles, starts and
survived the click-through, what could not be migrated and what was done instead, and point at
`MIGRATED_APP_FOLDER/STUMBLES.md` if it has entries.

For `crud`, add one line: `SWINGBRIDGE_HOME/testapps/crud/1-emulators/` is the finished migration,
worth diffing against.

## What this is not

- Not a rewrite to idiomatic Vaadin. That is step 2 of the migration arc and out of scope here: the
  code stays Swing-shaped, which is the point.
- Not a UI review. The exit criterion is *compiles, starts and doesn't crash under a couple of
  clicks* — do click through it; but how faithfully it looks and lays out in the browser is for the
  human to judge, and pixel differences are not worth sweating over.
