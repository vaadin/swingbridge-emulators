# `JTextArea.append` is quadratic: the surrogate re-inserts the whole text on every push

**Status:** found 2026-09-29, while measuring `Q_document_granularity` for
[SD_background_model_hop](../surrogates/decisions.md#SD_background_model_hop). Not decided, not
fixed.

**Maintainer-facing.** It hits the log-console pattern: a worker, or a `Timer`, appending one line
per event to a `JTextArea`. That pattern is common in real Swing apps.

## Measured

Karibu, attached `JTextArea`, lines of about 40 chars, 2026-09-29:

| appends | on the UI thread | on a worker |
|---|---|---|
| 1,000 | 1.0 s | 1.1 s |
| 5,000 | 19.8 s | 18.2 s |
| 20,000 | 252 s | 284 s |

The hop costs nothing: the UI-thread column is as slow as the worker one. Splitting the 5,000 case
by layer:

- `SJTextArea` `Document.insertString`, which pushes to the peer: 303 ms.
- A plain Vaadin `TextArea.setValue(fullText)` per line: 126 ms.
- **`SJTextArea.setValue(fullText)` per line: 23.5 s.** Almost all of the emulator's cost is here.

## Why

`JTextArea.append` inserts into the emulator's `Document`, and `docToPeer` pushes the *whole* text
with `peer.setValue`. Pushing the whole text is cheap. The expensive part is the surrogate's
value-change listener, `JTextComponentMixin.syncDocumentFromPeer`, which mirrors each value into the
surrogate's *own* `PlainDocument` as `remove(0, len)` plus `insertString(0, all)`. So every append
rebuilds a line element per line, which is O(lines) per append and O(lines²) overall.

## Options

- **Delta in the surrogate's echo.** Insert only what changed, from the common prefix and suffix, as
  the emulator's `syncDocumentFromPeer` already does ("the Document sees a delta, not a wholesale
  replacement"). An append then costs O(appended). The cost is a change in what a stage-3
  `DocumentListener` on the surrogate sees for a programmatic `setValue`: one insert instead of
  remove-all plus insert-all. R_vaadin_first probably allows that, since Vaadin's `setValue` has no
  JDK contract to match.
- **Skip the surrogate's echo for the emulator's own push.** The emulator owns the `Document` its
  getters read, so the surrogate's copy is unread at stage 2. That needs a seam the emulator can
  flag across, and the surrogate's `Document` still drifts for any stage-2 code that reads it.
- **Coalesce the peer push.** It is asynchronous, and D_sync_ui_hop rejects that for ordering.

The first option looks right. Check `JTextField` and `JPasswordField` too: they share the mixin, but
nobody appends to them in a loop.
