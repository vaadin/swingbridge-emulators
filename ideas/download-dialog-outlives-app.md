# The download dialog outlives the app

**Status:** brainstorm / not decided. **Maintainer-facing.** Found by the inventory guide round of
2026-09-30 (`testapps/inventory/1-emulators/STUMBLES.md`, "Not a doc gap" (b)).

## What was seen

Help → Read Manual opened a "Download help.pdf" dialog, which the user left open. Exit → Yes then
ended the app — the tab showed "The application has ended" — but the download dialog was still
rendered **on top of** that notice.

## Where to look

`vaadinx.BrowserFileTransfer.openDownloadDialog` (both overloads) builds a bare Vaadin `Dialog`
that no emulator window owns. It is not a `vaadinx.awt.Window`, so the app-end path — disposing the
last displayable window ([D_shutdown_lifecycle](../emulators/decisions.md#D_shutdown_lifecycle),
`lifecycle.md` § How your app ends) — has nothing to dispose it through. The file carries a comment
saying as much: "There is no 'app is done' signal, so the close is…".

## Open

- `Q_owner`: should the download dialog be owned by the chooser's owner window, so it closes when
  that window is disposed — the way a real `JDialog` child would?
- `Q_app_end_hook`: or should the app-end curtain close every overlay on the UI, emulator-owned or
  not? That also catches any other bare Vaadin overlay SB-Emulators opens.
- `Q_rnsi`: is "the dialog stays" arguably right — the user may still want the file after the app
  ended? The desktop analogue (a download-manager window outliving the app) says yes; the curtain
  drawn *under* it says no.
