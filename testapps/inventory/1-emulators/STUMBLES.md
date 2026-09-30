# Migration log — `inventory`

Points where the guides left me guessing, contradicted themselves, or said something that did not
work. Appended as the migration went.

```yaml
- id: 1
  doc: seed/vaadin-boot/example-pom.xml
  section: "header comment, item 2, and the exec-maven-plugin <arguments>"
  confusion: "The header tells you to change `<argument>com.example.app.Main</argument>`, and quotes that element verbatim — so the string occurs twice in the file, and a replace-exactly-once edit (or an agent asserting uniqueness) trips on the comment."
  what-i-did: "Replaced only the indented occurrence inside <arguments>."
  evidence: "[error]"
  suggested-fix: "Quote it in the header without the element tags (e.g. 'the com.example.app.Main argument at the bottom'), so the literal element exists once."
```

```yaml
- id: 2
  doc: guide.md
  section: "Phase 3 — S_wrap_executors ('SwingWorker needs nothing'); Phase 1 hazard table"
  confusion: "The app runs its three save handlers — validation, JOptionPane confirms, JDialog construction, component updates — entirely inside SwingWorker.doInBackground(). Nothing in Phase 1 flags that: the hazard scan has no SwingWorker/doInBackground pattern and the guide says SwingWorker needs nothing. It surfaced only at runtime, as EHelper's once-per-JVM off-UI-thread diagnostic, which itself warns of possible IllegalStateException. It also matters for the static sweep: FormerSingletons.get() and a VaadinSession shim both throw from doInBackground, so every tab/session routing has to be checked against worker bodies by hand."
  what-i-did: "Kept upstream's threading as is (it worked in the click-through). Grepped the three worker paths and confirmed none reaches AppFrame.getInstance(), getCurrentWindow() or isLoggedIn()."
  evidence: "[missing]"
  suggested-fix: "Add a hazard-scan row for `doInBackground` bodies (listing them for review), and in the Phase 1 triage table say: 'SwingWorker needs nothing for dates — but component work inside doInBackground is still off-thread, and anything you route to tab/session scope must not be read from there'."
```

## Not a doc gap

**(a) Kept as upstream had it, not fixed in the port:**

- `ExitButton.handleExit` starts with `res = 0`, which equals `JOptionPane.YES_OPTION`, so on panels
  with `isReadyToClose = true` (Login, Home, Change Password) Exit quits without asking.
- `AbstractFunctionPanel.mainApp` is never assigned, so `validateFailed()` would NPE if reached. It
  never is, because `Validator.parent` is never assigned either (the README's known quirk).
- The three `SwingWorker`s run UI code in `doInBackground()` (see id 2).
- The Excel export ignores `showSaveDialog`'s return value, so Cancel NPEs on `getSelectedFile()`.
  The port keeps it, per runtime-contract.md.
- `ResourceManager.readImage` does `new File(url.toString())` on a `file:`/`jar:` URL, so the frame
  icon is always `null`.
- `AppStarter.alreadyRunning` meant the opposite of its name (`true` = not running). The class is
  deleted now, so this is moot.

**(b) Emulators / add-ons came up short (cosmetic):**

- A toolbar `ActionButton` swaps its icon on hover. When that swap happens while a modal dialog has
  the frame inert, the new icon's `/VAADIN/dynamic/resource/...` URL returns 403 and a broken image
  shows until the next hover. Seen on "Item Entry" when opening Tools → Change Username/Password.
- The `GDialog` for Change Username/Password clips its content at the 480×340 size the app asks for:
  the left-hand labels are cut off and the fields scroll.

**(c) Environment:**

- Port 8080 was held by an unrelated Java process on the machine. Ran on 8090 via `SERVER_PORT`, as
  host-app-vaadin-boot.md says.
- Vaadin's dev-mode "App is running in development mode" toast covers the toolbar's Logout/Exit
  buttons until you close it. Browser automation clicking there hits the toast instead.
