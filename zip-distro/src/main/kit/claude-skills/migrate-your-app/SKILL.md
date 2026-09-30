---
name: migrate-your-app
description: Migrate the Swing app the user placed in the kit's your-app/swing/ onto SwingBridge Emulators, as a copy at your-app/1-emulators/, so it runs in a browser as a Vaadin web app. Use when the user asks to migrate their own app and it is in the slot, or types /migrate-your-app. Takes no argument.
---

## What this does

Migrates the user's own Swing application from `javax.swing` onto `vaadinx.swing` emulators,
following this kit's guides, until the app compiles, starts and survives a click-through as a Vaadin web app. The app is the one
in **`SWINGBRIDGE_HOME/your-app/swing/`**; the migration happens in a **copy** at
`SWINGBRIDGE_HOME/your-app/1-emulators/`, so `swing/` stays the untouched baseline and the two folders
diff into exactly the migration — the same `swing/` → `1-emulators/` layout the bundled examples under
`testapps/` use.

For an app that lives in its own version-controlled checkout and should be migrated in place, use
`/migrate-swing-app <folder>` instead. `your-app/swing/README.md` spells out the choice.

## Procedure

**1. Resolve the two paths.** `SWINGBRIDGE_HOME` is this kit's root — the folder holding
`README.md`, which is this skill's own `../../..`. Look at `SWINGBRIDGE_HOME/your-app/swing/`: it
must hold an app — a `pom.xml` or `build.gradle`, and `src/`. If it holds only its own `README.md`,
nothing was placed there: say so and stop. Ask for nothing else — the folder is the whole contract
of this skill, and an app elsewhere is `/migrate-swing-app <folder>`'s job.

**2. Copy the app out of the slot.** Never edit `your-app/swing/` itself:

```bash
if [ -d SWINGBRIDGE_HOME/your-app/1-emulators ] && diff -rq SWINGBRIDGE_HOME/your-app/swing SWINGBRIDGE_HOME/your-app/1-emulators >/dev/null; then
  echo "keeping the untouched copy from a previous attempt"
else
  rm -rf SWINGBRIDGE_HOME/your-app/1-emulators
  cp -r SWINGBRIDGE_HOME/your-app/swing SWINGBRIDGE_HOME/your-app/1-emulators
fi
```

An existing copy is kept only when it is still identical to `swing/` — a previous attempt that
stopped before editing anything, typically. **A copy that has been edited is a finished or half-finished
migration and is not thrown away silently**: if `1-emulators/` exists and differs, stop and ask
whether to replace it. `MIGRATED_APP_FOLDER` is `SWINGBRIDGE_HOME/your-app/1-emulators`.

**3. Sanity-check the app against what will not migrate**, before spending an hour on it. Three
capabilities are permanently out of scope: `JApplet`, Look-and-Feel dispatch
(`UIManager.setLookAndFeel`, pluggable `*UI` classes), and user-authored `Graphics` painting of
components (`paintComponent` overrides, custom-painted widgets, `JTable.print()`). Grep for them.
If the app's value is mostly there, say so now — the honest answer may be that a different route
fits better, and `SWINGBRIDGE_HOME/README.md` § "Where SwingBridge fits" names the sibling that
covers exactly those cases.

**Then settle the bootstrap, once, before starting.** Look for Spring in the app's build file
(`org.springframework`). Recommend Spring Boot, or Vaadin Boot if the app is already built on Spring
(`SWINGBRIDGE_HOME/guides/1-swing-to-emulators/build-wiring.md` § "Which bootstrap?"), and ask the
user to confirm or change it. If the answer is Spring Boot, also ask how the packaged app will be
launched: as a fat jar with `java -jar`, recommended, or with `java -cp`. If nobody is there to
answer, take both recommendations and say so. These are the guide's Phase 0 questions, asked here so
the migration never stops mid-way to ask them.

**4. Do the migration — in this session.** Read
`SWINGBRIDGE_HOME/guides/1-swing-to-emulators/agent-prompt.md` and follow the prompt section between
its two `---` dividers as your own instructions, resolving `SWINGBRIDGE_HOME` and
`MIGRATED_APP_FOLDER` to the real absolute paths as you read them.

**Do not delegate this to a subagent.** A spawned agent works where the user cannot watch it and
reports back a summary — and for a migration, *where it hesitated* is the interesting part.
Working here keeps it visible as it happens, and leaves you holding the detail step 5 has to
report.

**5. Report.** Hand the user the URL to open, and say which flows you clicked through and what you
saw — a thorough look at the UI is theirs. Say plainly whether the app compiles, starts and
survived the click-through, which decisions want a
human's review (the `static` field verdicts, and any third-party Swing library with no add-on),
and what could not be migrated and what was done instead. Point at
`MIGRATED_APP_FOLDER/STUMBLES.md` if it has entries — those are worth sending back to Vaadin. Add
one line: `diff -r your-app/swing your-app/1-emulators` is the whole migration, ready to review.

## What this is not

- Not a rewrite to idiomatic Vaadin. That is step 2 of the migration arc and out of scope here: the
  code stays Swing-shaped, which is the point.
- Not a UI review. The exit criterion is *compiles, starts and doesn't crash under a couple of
  clicks* — do click through it; how faithfully it looks in the browser is for the human to judge.
- Not for an app outside the slot. `/migrate-swing-app <folder>` migrates an app where it lives.
