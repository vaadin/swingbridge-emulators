# `your-app/swing/` — put your Swing app here

This folder is the slot for **the Swing application you want to migrate**. Copy your app's source
tree into it — the folder holding your `pom.xml` (or `build.gradle`) becomes `your-app/swing/`
itself, so that `your-app/swing/pom.xml` and `your-app/swing/src/main/java/` exist afterwards.
Delete this README once your app is in place, or leave it; nothing reads it.

**Copy the app in before you open Claude Code**, or restart Claude Code after: a Java language
server, if you run one, scans for projects when it starts, and an app copied into place afterwards
is not imported until the next start.

Then, from Claude Code opened in the kit's root folder:

```
/migrate-your-app
```

The skill takes `your-app/swing/` as the app, copies it to `your-app/1-emulators/` and migrates
**the copy**. `swing/` stays untouched as the pre-migration
baseline, so the diff between the two folders is exactly what the migration changed — the same
side-by-side layout the bundled examples under `testapps/` use, and the layout the guides describe
as the migration arc's stages.

**You do not have to use this folder.** `/migrate-swing-app <path>` points the same machinery at an
app anywhere on disk and migrates it **in place** — the right choice when the app lives in its own
version-controlled checkout, because the import swap is then one commit you can review and revert.
Pick one: a copy here when the app has no version control of its own or you want the two stages
side by side; in place when it does.

**Nothing in this kit builds against `tools/lib/`.** Your migrated app declares SwingBridge Emulators
by Maven coordinate, as the guide's Phase 4 describes: you start from the seed's `example-pom.xml`
under `guides/1-swing-to-emulators/seed/` (one per bootstrap), and `testapps/crud/1-emulators/pom.xml`
shows the result for the Spring Boot lane.
