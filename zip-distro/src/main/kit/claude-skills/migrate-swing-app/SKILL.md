---
name: migrate-swing-app
description: Migrate a Java Swing desktop application, in place in its own folder, onto SwingBridge Emulators so it runs in a browser as a Vaadin web app. Use when the user asks to migrate, port or web-enable their own Swing app that lives in its own checkout, or types /migrate-swing-app <folder>. For an app placed in the kit's your-app/swing/ slot, /migrate-your-app is the command.
---

## What this does

Migrates the user's own Swing application from `javax.swing` onto `vaadinx.swing` emulators,
following this kit's guides, until the app compiles, starts and survives a click-through as a Vaadin web app.

The app lives wherever it lives and is migrated **in place** — the right shape when it is in its own
version-controlled checkout: the import swap is one commit to review and revert. For the other shape
— a copy beside an untouched baseline, inside this kit — the user puts the app in
`SWINGBRIDGE_HOME/your-app/swing/` and runs **`/migrate-your-app`** instead; `your-app/swing/README.md`
spells out the choice.

## Procedure

**1. Resolve the two paths.** `SWINGBRIDGE_HOME` is this kit's root — the folder holding
`README.md`, which is this skill's own `../../..`. `MIGRATED_APP_FOLDER` is the argument. If none
was given, ask for the app's folder — this is the one thing that cannot be guessed, and a wrong guess
edits the wrong tree; if the user means the app in `your-app/swing/`, point them at
`/migrate-your-app`.

**2. Check an in-place migration is safe.** Confirm the app is under version control with a clean
working tree (`git -C MIGRATED_APP_FOLDER status --short`). If it is not, stop and say so — offer
to migrate a copy instead (copying the app into `SWINGBRIDGE_HOME/your-app/swing/` and running
`/migrate-your-app` is that offer, ready-made), and let the user choose.

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
`MIGRATED_APP_FOLDER/STUMBLES.md` if it has entries — those are worth sending back to Vaadin.

## What this is not

- Not a rewrite to idiomatic Vaadin. That is step 2 of the migration arc and out of scope here: the
  code stays Swing-shaped, which is the point.
- Not a UI review. The exit criterion is *compiles, starts and doesn't crash under a couple of
  clicks* — do click through it; how faithfully it looks in the browser is for the human to judge.
- Not a substitute for reading the guides. If the user wants to understand the migration rather
  than have it done, point them at `SWINGBRIDGE_HOME/guides/1-swing-to-emulators/guide.md`.
