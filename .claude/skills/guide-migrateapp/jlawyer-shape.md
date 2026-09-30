# `/guide-migrateapp jlawyer-shape`

## What this app probes

Where `crud` exercises the CRUD-baseline surface, this one exercises the larger JLawyer-shaped
surface — JTree / JList / JSplitPane / JTabbedPane / JPopupMenu / JFileChooser / JToolBar /
JEditorPane / JProgressBar / standalone JRadioButton, plus clipboard and drag-and-drop. App-specific
gaps the crud app never reached are exactly what this probe is for.

## Residue

**None.** The kit ships stage 1 only for this app
([D_kit_what_ships](../../../emulators/decisions.md#D_kit_what_ships)), so there is no answer key
beside it to deny, and everything else in the kit is in scope because the customer sees all of it.
Anything you find yourself adding is a finding against the kit.

## After the copy-back — re-apply the AGPL notice

Step 4's copy-back overwrites `1-emulators/src`, which destroys a notice the licence requires.
This app vendors one **AGPL-3.0** file from j-lawyer
([`testapps/jlawyer-shape/PROVENANCE.md`](../../../testapps/jlawyer-shape/PROVENANCE.md)), and the
migrated copy of it is a modified work, so AGPL §5(a) needs a prominent notice saying so. Restore it
above the `package` line of
`1-emulators/src/main/java/com/jdimension/jlawyer/client/editors/files/InvoicePositionEntryPanel.java`,
copying the block from the previous committed revision (`git show HEAD:<that path>`) and updating the
year if it has changed. **Do not touch `swing/`'s copy** — it is the unmodified original, and a
modification notice on it would be false.

The migrating agent is not told any of this: whether a migration preserves a licence header it was
never warned about is itself worth knowing, and the answer belongs in `STUMBLES.md` rather than in
the prompt. Note in the report which way it went.

## Report, in addition to the generic one

- Which of the components above the run actually reached, and which it stubbed or WARNed past — the
  point of this app is the long tail, so "compiles and starts" says less here than for `crud`.
