# Migration log — inventory

```yaml
- id: 1
  doc: guide.md
  section: "Phase 3 — S_triage_platform: 'Triage every platform check, Runtime.exec and Desktop.getDesktop() call by intent'"
  confusion: "The step presumes a worklist, but the Phase 1 hazard scan has no pattern for Runtime.exec, Desktop.getDesktop() or OS checks (hazard-scan --list shows none), and the Phase 1 triage table does not route them either. This app's only user-serving shell-out (Help → Read Manual runs `cmd.exe /c start help.pdf`) would have been missed by anyone working from the reports alone."
  what-i-did: "Found it by grepping `Runtime.getRuntime|Desktop\\.|IS_OS|os.name` by hand while reading AppFrame, and recorded it in my triage notes for S_triage_platform."
  evidence: "[missing]"
  suggested-fix: "Add an H_platform row to the hazard table (`Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder|Desktop\\.getDesktop|IS_OS_|os\\.name`) routed to platform.md, or say in S_triage_platform that the scan does not cover it and give the grep."

- id: 2
  doc: third-party/jcalendar-1.4/MIGRATION.md
  section: "Beyond the import swap — 1. JDateChooser is no longer a JPanel"
  confusion: "It says any Container call on a chooser is a compile error, so fixing the compile covers it. That holds only for calls on a JDateChooser-typed reference. This app's UIUtils.clearAllFields / toggleAllChildren walk `((JComponent) parent).getComponents()` recursively; on the desktop that reached the chooser's inner JTextFieldDateEditor (a JTextField) and blanked it or disabled it. After the swap the code compiles and the walk silently finds no children, so the chooser is no longer cleared by 'clear all fields'."
  what-i-did: "Left it as it is: toggleAllChildren still disables the chooser itself (it calls setEnabled on the parent before recursing), so only the clear-all behaviour may differ. Flagged it for the click-through."
  evidence: "[missing]"
  suggested-fix: "Add to 'Known divergence': code that recurses through getComponents() and acted on the chooser's inner text field or button (clear-all-fields, enable-all helpers) now reaches no children, with no compile error. Grep for generic child-walkers and handle `instanceof JDateChooser` explicitly (setDate(null) / setEnabled)."

- id: 3
  doc: guide.md
  section: "Phase 3 — S_wire_build: 'add back your app's own dependencies from its old build file'"
  confusion: "The step covers dependencies only. This app's old pom also had a run configuration the app depends on (exec workingDirectory=target/run, because it writes ./log/ and an H2 file relative to the working directory; classpathScope=test) and a -Pfile-db profile. The seed's exec block has neither, and the step does not say whether they carry over or how they may be combined with the seed's load-bearing argument list."
  what-i-did: "Carried over <classpathScope>, <workingDirectory> and the file-db profile into the seed's exec-maven-plugin <configuration> and <profiles>, leaving the seed's <arguments> order untouched."
  evidence: "[guess]"
  suggested-fix: "One sentence in S_wire_build: also carry over profiles and exec/run settings that are not JVM arguments (working directory, classpath scope); add JVM flags only before the main class in <arguments>."
```

## Not a doc gap

**(b) Emulator shortfall — `JLabel.setIcon` after attach serves a 403.** The toolbar's `ActionButton`s
(`JLabel`s) swap between `-on`/`-off` `ImageIcon`s (built from `getResource` URLs) in
`mouseEntered`/`mouseExited`. The icon given in the constructor renders; every icon set later by
`setIcon(...)` gets a fresh `VAADIN/dynamic/resource/...` URL that answers **403**, so after the first
hover the button shows a broken image whose alt text is the full `file:/…/target/classes/images/*.png`
path. That text overflows into the neighbouring toolbar buttons and swallows their clicks: after a hover,
the toolbar is hard to use, and the menus are the reliable navigation. No WARN is logged for it. Not
worked around in app code.

**(c) Environment — a language server compiled into `target/classes` under the running app.** An IDE's
JDT language server imported the project (writing `.project`, `.classpath`, `.settings/`, first under
the seed pom's `myapp` name) and recompiled into Maven's `target/classes` while the app ran. JDT names
anonymous/switch-map classes differently from javac, so the live JVM hit
`NoClassDefFoundError: com/ca/ui/panels/ItemEntryPanel$3` on opening Item Entry and the error handler
showed ref #1. Fixed by stopping the app, deleting `target/` and JDT's three files, and rebuilding with
`clean`. JDT re-created `.project` later in the session, and it was deleted again before the final run.
Costs ~15 minutes; the kit's CLAUDE.md warns about exactly this.

**(a) Upstream behaviour noticed and ported unchanged:**
- `ResourceManager.getString(key)` is called with a constant's *value* (`"A Company"`) but
  `StrConstants.getMap()` keys by field *name* (`COMPANY_NAME`), so company/department labels read
  `null` (About dialog, login and home screens).
- `GDialog.setAbstractFunctionPanel` already calls `setVisible(true)` on a modal, and every caller calls
  `setVisible(true)` again, so *Help → Support* and *Tools → Change Password* must be closed twice.
- `ResourceManager.getImage` reads an image via `new File(url.toString())`, which is always null, so the
  frame never had an icon.
- The two *Save to Excel* buttons ignore `showSaveDialog`'s return value; cancel → NPE (per
  `runtime-contract.md`, ported as it stands).
- 18 developer test-harness `main()` methods on panels/utilities (`EXIT_ON_CLOSE`, `setBounds(getBounds())`)
  are unreachable from the web app and were left alone.
