# :emulators-printing — a virtual PDF printer for `java.awt.print`

Optional add-on module for [`:emulators`](../emulators): a **virtual printer** that lets
migrated Swing code keep its hand-crafted `Printable.print(Graphics)` printing. The virtual
printer renders every page to a **PDF** (server-side, via a `Graphics2D`-to-PDF bridge) and
opens a Vaadin dialog in the user's browser offering the PDF for download — the user then
prints it with their local PDF viewer, on their own printer. One printer, always present,
no OS print subsystem involved.

## What is and isn't supported

Printing in Swing splits into two cases:

- **Printing hand-crafted graphics — supported.** A `Printable` that draws shapes, text and
  images directly (`drawString`, `drawLine`, `drawImage`, …) is static one-shot vector
  rendering; it reproduces faithfully into PDF. This is the moral equivalent of a Vaadin app
  hand-crafting a JasperReports template.
- **Printing components — not supported.** Anything that paints the widget tree onto the
  printer — `component.printAll(g)` / `paint(g)` inside a `Printable`, `JTable.print()`,
  `JTextComponent.print()` — stays out: emulated components have no paint. Component-print
  calls inside your `Printable` produce a blank region and a WARN; `JTable.print()` and
  friends WARN-and-noop. Rewrite those to a server-side PDF report (JasperReports, openhtmltopdf,
  …) — see the migration guide.

## Migrator instructions

1. Add this module next to your `:emulators` dependency:

   ```kotlin
   implementation("com.vaadin.swingbridge:swingbridge-emulators-printing:VERSION")
   ```

2. Swap **one import** (the only `java.awt.print` type that changes — `Printable`,
   `PageFormat`, `Paper`, `Book`, `Pageable` and the exceptions all stay JDK):

   ```java
   // import java.awt.print.PrinterJob;
   import vaadinx.awt.print.PrinterJob;
   ```

   Code using a wildcard `import java.awt.print.*;` just *adds* the `vaadinx` import — an
   explicit import beats a wildcard, so nothing else moves.

3. The classic flow now works unchanged:

   ```java
   PrinterJob job = PrinterJob.getPrinterJob();
   job.setPrintable(myPrintable);
   if (job.printDialog()) {   // always true — one virtual printer, nothing to configure
       job.print();           // renders the PDF, opens the download dialog
   }
   ```

Create the `PrinterJob` from code running in a UI context (an event listener, a
`SwingWorker`, a `Timer` callback) so the virtual printer knows which browser session
receives the download dialog; `print()` itself may then run on a background thread.

## Accepted divergences

- `printDialog()` returns `true` without showing a dialog — there are no printer options to
  ask about; the download dialog at the end is the cancelable moment.
- `pageDialog(...)` returns its argument unchanged (no page-setup UI). Pass the `PageFormat`
  you want to `setPrintable(printable, pageFormat)` instead.
- `defaultPage()` is A4 portrait with 1-inch margins (the JDK default is locale-dependent).
- Copies are ignored — a PDF download is inherently one copy (WARN if `copies > 1`).
- `lookupPrintServices()` returns an empty array; the virtual printer is not a
  `javax.print.PrintService`. `javax.print.*` in general stays untouched JDK API — code
  using it talks to the **server's** print subsystem, which is almost never what you want;
  the migration checklist flags it.
- Text is drawn as vector glyph outlines: the PDF always looks and prints right, but its
  text is not selectable/searchable.
