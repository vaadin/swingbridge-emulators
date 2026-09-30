# Migration log — inventory → SB-Emulators (Vaadin Boot)

```yaml
- id: 1
  doc: guide.md
  section: "Phase 1 — S_triage_reports, row 'System.exit / Runtime.halt; cancelable tab close'"
  confusion: "The triage table routes the H_cancelable_close hit (a windowClosing listener / .consume()) to lifecycle.md § How your app ends, but that section never mentions windowClosing, a vetoed close, or the scan's own hint 'browser owns unload — save proactively'. No document in the guide folder does (grep for cancelable/windowClosing/veto finds only the table row)."
  what-i-did: "Read the app's listener: AppFrame's windowClosing body is empty and the frame is DO_NOTHING_ON_CLOSE, so there is no veto logic and no unsaved-state flush to port. Left it as-is."
  evidence: "[missing]"
  suggested-fix: "Add a short paragraph to lifecycle.md § How your app ends: a windowClosing veto (DO_NOTHING_ON_CLOSE + a confirm dialog) cannot stop a browser tab close, so save-on-close logic must move to save-on-change or to a WINDOW_CLOSING handler that does not ask; and an empty listener needs nothing."

- id: 2
  doc: former-singletons.md
  section: "Checking you finished — 'Each row comes back gone, made a constant, annotated … or still unvetted'"
  confusion: "The --diff report opens by reprinting the stage-1 worklist verbatim (same 20 rows, same 'writers' columns, no fate column), so at first glance it looks as though --diff was ignored and nothing was settled. The fates are a separate table ~170 lines down under '## Completeness diff against …'."
  what-i-did: "Grepped the report for 'fate' and found the section at the end: 20/20 rows settled, 0 new statics."
  evidence: "[guess]"
  suggested-fix: "Say in the doc (or in the report's header) that the fate table is the last section, or put it first in --diff mode; the reprinted stage-1 worklist is what a reader sees first."
```

## Not a doc gap

- **(c) environment — jdtls rewrote `target/classes` under the running app.** Two minutes after the
  first successful start, every class file in `target/classes` was rewritten (timestamps after boot;
  `.project` / `.classpath` / `.settings` appeared in the app folder) by the IDE language server's
  JDT build. JDT emits switch-map classes differently, so javac's `ItemEntryPanel$3` vanished and
  opening Item Entry failed with `NoClassDefFoundError: com/ca/ui/panels/ItemEntryPanel$3`
  (AppErrorHandler ref #1). The kit's CLAUDE.md warns about exactly this. Fix: an `ide-output`
  profile in the pom (activated only by `m2e.version`, moves the IDE's output to `target/ide`),
  then `clean` + restart. Cost ~5 minutes.
- **(c) environment — port 8080 was taken** by an unrelated Java process on this machine. Ran on
  `SERVER_PORT=8090`, which host-app-vaadin-boot.md documents; worked first time.
- **(b) emulators — `JFileChooser.setApproveButtonText` is not shown.** runtime-contract.md's recipe
  keeps the custom label via `setApproveButtonText("Select Save location")` before `showSaveDialog`;
  the browser's save prompt still reads "Save". Cosmetic; the save and download work.
- **(b) emulators — swapped `JLabel` icons break.** `ActionButton` swaps icons with `setIcon(on/off)`
  on hover/press; after a swap the toolbar icon renders as a broken image and the browser logs a
  403 on `/VAADIN/dynamic/resource/…`. Initial icons render fine. Cosmetic.
- **(b) emulators — the `BrowserFileTransfer` download dialog outlives the app.** After Exit →
  Yes, the "Download help.pdf" dialog (left open) was still shown on top of "The application has
  ended". Cosmetic.
- **(a) app bugs walked past, kept as-is:** `ItemEntryPanel` runs its save (which builds the
  Validator's popup `JDialog`s) inside `SwingWorker.doInBackground()` — off-EDT on the desktop too,
  now a once-per-JVM WARN; `GDialog.setAbstractFunctionPanel()` calls `setVisible(true)` and so do
  its callers, so the modal Change Password / Support dialogs must be closed twice;
  `ResourceManager.getImage` builds `new File(url.toString())` from a `file:`/`jar:` URL, so the
  frame icon was always null ("Error:Can't read input file!"); both Save-to-Excel buttons NPE on
  cancel (`getSelectedFile()` is null) — ported unguarded per runtime-contract.md; and the
  README's known Validator quirk (status message never shown) is preserved.
