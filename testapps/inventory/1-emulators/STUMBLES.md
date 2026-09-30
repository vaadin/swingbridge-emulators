# Migration log — inventory

```yaml
- id: 1
  doc: guide.md
  section: "Phase 3 — S_triage_platform (and Phase 1's hazard scan)"
  confusion: "S_triage_platform says to triage every platform check, Runtime.exec and Desktop.getDesktop() call, but the Phase 1 hazard scan has no pattern for any of them, so the worklist never names the app's one user-facing shell-out (AppFrame's `Runtime.getRuntime().exec(\"cmd.exe /c start help.pdf\")`) or its 14 SystemUtils.IS_OS_WINDOWS checks."
  what-i-did: "Grepped by hand for Runtime., Desktop, SystemUtils and os.name. Found the Read Manual shell-out and routed it per platform.md (help.pdf moved to src/main/resources, served via BrowserFileTransfer.openDownloadDialog)."
  evidence: "[missing]"
  suggested-fix: "Add hazard-scan rows for `Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder|Desktop\\.getDesktop|IS_OS_|os\\.name`, routed to platform.md, so this step has a worklist like the others."

- id: 2
  doc: guide.md
  section: "Phase 3 — S_copy_seed: \"move the seed's com.example.app classes into your own package — typically the one holding your main JFrame, or its parent\""
  confusion: "Here the main JFrame (AppFrame) lives in a reusable UI-library package (com.gt.uilib.components) while the old main() lived in com.ca.ui. Neither \"the JFrame's package\" nor \"its parent\" (com.gt.uilib) is where the entry point was."
  what-i-did: "Put the seed classes and FormerSingletons in com.ca.ui, beside the old Main (renamed to OldMain first, then deleted). This kept the pom's main class name com.ca.ui.Main unchanged."
  evidence: "[guess]"
  suggested-fix: "Add: \"or the package your old main() lived in, if the frame sits in a library package\"."

- id: 3
  doc: guide.md
  section: "Phase 3 — S_wrap_executors: \"SwingWorker needs nothing.\" (also dates.md § 2, runtime-contract.md throws table)"
  confusion: "The guide says SwingWorker needs nothing, and the runtime-contract throws table lists only 'a thread your app started itself'. But all three Save buttons (Item Entry, Transfer, Return) run their whole save — confirm dialogs, Validator.setBackground(PINK), field updates — inside doInBackground(). There the first component write throws IllegalStateException: Cannot access state in VaadinSession or UI without locking the session; SwingWorker swallows it, so Save silently does nothing. The only signal is a once-per-JVM WARN whose text says the failure modes are lost updates and 'where the Vaadin API resolves a resource URL or reaches for the current UI, an outright IllegalStateException'. A plain setBackground threw too."
  what-i-did: "Found it only by temporarily wrapping doInBackground in a catch-and-print (a JFR exception recording did not show the ISE). Fixed all three by marshalling the body with SwingUtilities.invokeAndWait(...) inside doInBackground, which keeps the worker and its DONE listener that re-enables the button."
  evidence: "[error]"
  suggested-fix: "In S_wrap_executors: 'SwingWorker needs nothing for its *context*, but a doInBackground() that touches components throws on the first write and SwingWorker swallows the exception. grep doInBackground and move component work to done()/process() or wrap it in SwingUtilities.invokeAndWait.' Also add a hazard-scan row for doInBackground, and have the emulator log the swallowed exception."
```

## Not a doc gap

**(a) Changes I wanted to make in the original app and did not** (all present on the desktop too):

- 14 per-panel dev-harness `public static void main` methods (`BranchOfficePanel.main`,
  `LoginPanel.main`, …) build a bare `JFrame` with `EXIT_ON_CLOSE` and select the Windows L&F. They
  cannot run without Vaadin and are dead code now; ported as they are.
- `ResourceManager.getString(StrConstants.COMPANY_NAME)` looks up the constant's *value* in a map keyed
  by field *name*, so the Login/Home/About labels show `null` (the About box reads "null, null").
- About/Support opens twice: `GDialog.setAbstractFunctionPanel` already calls the modal
  `setVisible(true)`, and the menu listener calls it again, so it has to be closed twice.
- Exit from HOME or Login quits without asking: `ExitButton.handleExit` asks only when
  `currentWindow.isReadyToClose` is false, and `res` defaults to `0 == YES_OPTION`.
- `ResourceManager.getImage` feeds a `jar:`/`file:` URL string to `new File(...)`, so the frame icon is
  always `null`.
- The "Save to Excel" handlers ignore the chooser's return value, so Cancel NPEs (per
  runtime-contract.md, ported as is).
- `SwingWorker` bodies touching components off the EDT: this one *was* changed (STUMBLES #3), because
  it breaks every Save in the browser. The change was moving the body into `done()`, in three files.
- The Item Entry date field is typeable again: its `getDateEditor().setEnabled(false)` has no
  counterpart in the JCalendar add-on (the add-on's MIGRATION.md documents this).

**(b) Emulators / add-ons came up short:**

- **Swapped icons sometimes break.** `ActionButton` swaps its icon on `mouseEntered` and again on
  `mousePressed`. Each `setIcon` registers a new `VAADIN/dynamic/resource/…` URL and drops the old
  one, so a fast swap leaves the browser requesting a dropped URL: 403 in the console, and a broken
  image until the next hover.
- **That broken image's `alt` text is the server's absolute path**
  (`file:/tmp/swingbridge-kit…/target/classes/images/itementry-off.png`). It is the `ImageIcon`'s
  default description. That leaks the install path to every browser, and the long text overlaps the
  neighbouring toolbar buttons.
- **A thrown exception in `SwingWorker.doInBackground()` is silent.** That matches Swing, but it
  made STUMBLES #3 cost a temporary debug patch to find. A JFR `jdk.JavaExceptionThrow` recording did
  not show the `IllegalStateException` either.
- `SwingUtilities.invokeAndWait` from a `SwingWorker` thread throws (it needs a current UI), which I
  found out only by trying it as a fix for #3. `import-swap-reference.md` says it "carries working
  semantics", with only the from-the-EDT case excluded.

**(c) Environment failures that cost time:**

- The IDE's JDT language server imported the new pom and rewrote all of `target/classes` two minutes
  after the app had started. It also created `.project` / `.classpath` / `.settings`. JDT numbers
  anonymous classes differently from javac, so the running app hit `ClassNotFoundException:
  ItemEntryPanel$3` on the first Item Entry click. Fixed by `clean` + restart, and by not editing
  sources while the app runs. The kit's CLAUDE.md warns about exactly this.
- No display on the machine, so the desktop stage-1 app was not run for comparison.
- The Vaadin dev-mode notification covers the Logout/Exit toolbar buttons at the top right until it
  is dismissed (dev mode only).
