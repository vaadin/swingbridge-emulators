# `/guide-migrateapp crud`

## What this app probes

The CRUD-baseline surface: list view + detail form + dialogs + menubar, the same "typical CRUD app"
D_gap_severity_triage uses as its yardstick. Hand-built, so a run here partly measures how well we
predicted ourselves; `jlawyer-shape` and `inventory` cover what it cannot.

## Residue

Append to the kit command, verbatim:

> Additionally, do not read `testapps/crud/1-emulators/**` — the finished migration that ships beside
> the app you are migrating. A customer is welcome to it; you are being measured on deriving it.

That is the whole residue. Everything else the kit ships is in scope — its `CLAUDE.md`, its README,
every guide — because the customer sees all of it.

## After the copy-back

Nothing to restore.

## Report, in addition to the generic one

Nothing app-specific. For `crud`, `git diff testapps/crud/1-emulators/src` against the previous
baseline is the quickest read of what the run did differently.
