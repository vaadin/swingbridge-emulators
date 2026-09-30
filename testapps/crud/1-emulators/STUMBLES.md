# Migration log — `crud`

Points where the guides left me guessing, contradicted themselves, or said something that did not
work. Schema per `agent-prompt.md`.

```yaml
- id: 1
  doc: static-fields.md
  section: "Step 1 — build the worklist: 'When you are done, diff rather than count. Re-run the same tool with --diff pointed at the migrated tree'"
  confusion: "--diff compares a stage-1 classes dir against the migrated one, but by the time the diff is due the stage-1 classes are gone: Phase 0 builds them into target/classes, and the migrated app's own build (a clean package, per the seed's advice) overwrites that same directory. An in-place migration has no second copy."
  what-i-did: "Compiled the kit's pristine testapps/crud/swing sources with javac into a scratch directory and diffed that against work/crud/target/classes, with --cp from dependency:build-classpath. A customer migrating in place would have to check out the pre-swap commit and build it again."
  evidence: "[missing]"
  suggested-fix: "In Phase 1's sweep step, say: copy target/classes aside now (e.g. cp -r target/classes swingbridge-migration/stage1-classes) — the Phase 6 completeness diff needs it. Then give the full --diff command line at S_guardrails, including that the migrated tree needs --cp for its Vaadin types."

- id: 2
  doc: guide.md
  section: "Phase 4 — S_error_handler: 'port that body here in place of the Notification … the ref-number sentence in place of the trace'"
  confusion: "The desktop handler built the dialog's TITLE from the exception (getClass().getSimpleName() + ': ' + getMessage()). The guide rules out the trace and says the message leaks the same way, but only talks about the body; whether the title should keep the class name was a guess."
  what-i-did: "Titled the ported dialog plainly 'Error' — the class name is exactly the kind of detail the guide says traces leak."
  evidence: "[guess]"
  suggested-fix: "One clause: 'a title or header built from the exception (class name, message) goes too'."
```

## Not a doc gap

- **(c) environment** — the session has no `DISPLAY`, so the stage-1 desktop app could not be run
  before migrating; the before-picture comes from `testapps/crud/README.md` and `swing/test.md` only.
- **(b) tool** — `hazard-scan` was given `src/main/resources`, which this app does not have, and
  said nothing about it (the report lists the root as read, `0 × *.{html,htm}`). Harmless here; a
  typo'd resources path would silently drop the HTML-link hazard.
- **(b) emulator** — `JSlider.setPaintTicks(true)` / `setPaintLabels(true)` are unimplemented (WARN
  per dialog open); the Rating slider shows no ticks or labels. Cosmetic.
- **(b) emulator** — Enter in the edit dialog's *edited* date field (`JSpinner.DateEditor`) commits
  the date **and** fires the root pane's default button, closing the dialog. Desktop Swing consumes
  that first Enter: `JFormattedTextField.CommitAction.isEnabled()` is true while the field is edited
  (checked in the JDK 25 `src.zip`), so the default button only fires on a second Enter. The
  committed value is correct; only the early close differs. Found by probe, not by `test.md`.
- **(b) emulator, cosmetic** — the `Favorites only` `JToggleButton` shows almost no pressed state
  when selected (`test.md` scenario 5 step 3 expects a pressed look); the filter itself works.
