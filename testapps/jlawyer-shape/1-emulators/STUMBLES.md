# Migration log — jlawyer-shape

```yaml
- id: 1
  doc: static-fields.md
  section: "Monotonic counter / ID sequence → application, usually (may stay static)"
  confusion: "The counter example assumes the counter hands out IDs for shared data. Here `Case.idSequence` numbers cases in an in-memory store that is built per app instance (seeded in `mainUI()`), so once it is shared, a second session's seed cases come out as #1007… instead of the desktop's #1001…. The IDs are unique, but the numbering no longer matches what the desktop showed. The doc gives no rule for a global counter that feeds per-tab data."
  what-i-did: "Kept it static as `@IntentionallyStatic(COUNTER)` with a note. The in-memory store stands in for j-lawyer's server DB, whose sequence is shared by all users, and no code depends on the numbers being contiguous. Flagged it for human review; the alternative is tab scope through `FormerSingletons`."
  evidence: "[guess]"
  suggested-fix: "Add one line to the counter example: when the counter numbers rows of per-tab state (an in-memory store the frame owns), decide by whether the numbers are shown to the user and expected to repeat per launch — if so, tab scope; otherwise COUNTER."

- id: 2
  doc: guide.md
  section: "Phase 1 — S_triage_reports, table row 'timezone drift (three buckets)'"
  confusion: "The row says 'bucket and tag `// TODO[browser-tz]:`', which reads as: tag every date hit. dates.md § Bucketing says bucket 1 gets no marker. All five hits here were bucket 1."
  what-i-did: "Followed dates.md and added no markers. The Phase 6 step ('an app whose date code is entirely bucket-1 legitimately has zero markers') confirms that reading, but only three phases later."
  evidence: "[doc-contradiction]"
  suggested-fix: "Make the row say 'bucket; tag buckets 2–3 and shared Calendars with `// TODO[browser-tz]:` (bucket 1 needs no marker)'."

- id: 3
  doc: static-fields.md
  section: "The allowlist annotation — 'Watch out: on a SNAPSHOT kit there is no javadoc to read.'"
  confusion: "On this machine's source-built 0.1-SNAPSHOT, `~/.m2` has `-sources.jar` and `-javadoc.jar` for both swingbridge-migration-annotations and swingbridge-migration-guardrails, so the javadoc is available."
  what-i-did: "Nothing blocked; I read the annotation's members with javap. The watch-out is wrong for this install."
  evidence: "[doc-contradiction]"
  suggested-fix: "Say 'may have no javadoc' (it depends on how the SNAPSHOT was built) and point at the -sources jar in ~/.m2 as the first place to look."
```

## Not a doc gap

- **(b) emulators — three WARNs, all non-functional.** `JEditorPane.createEditorKitForContentType(text/html)`
  is reported unimplemented, yet the HTML notes render correctly (headings, bold, lists), because
  `setText` with HTML still goes through. `JTextComponent.setCaretPosition/non-text-peer()` comes
  from `CaseView.refreshNotes` scrolling the notes pane to the top; it's cosmetic. And
  `WebClipboard.setContents (browser NotAllowedError)` was the headless browser having no clipboard
  permission: with `clipboard-write` granted, `#<id>` really lands on the browser clipboard.
- **(b) seed vs library Vaadin version.** Every start logs `Multiple npm versions for
  @vaadin/integer-field / @vaadin/radio-group found: [25.3.0, 25.2.7]`. The seed pom pins Vaadin
  25.3.0, while something on the SB-Emulators side declares 25.2.7 web components. It's harmless
  here (the first one wins), but the seed and the library should agree.
- **(a) latent app behaviour, not fixed.** `MainFrame.onUpload` stores `f.getAbsolutePath()` as the
  document's path hint, which in the browser is the server-side temp path of the upload. The app
  only displays it, so it stays as ported.

