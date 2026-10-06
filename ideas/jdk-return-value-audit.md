# The return-value audit: what is left to do

Everything built, measured and decided has graduated to
[D_return_value_audit](../emulators/decisions.md#D_return_value_audit); invocations are in
[DEVELOPING.md §"Standing audits"](../DEVELOPING.md#standing-audits), which also carries the
standing instruction to run the `xvfb` sweep by hand from time to time. **This file holds only what
has not been done.**

1. **`StateDropSweep`'s worklist is one row, and it is not this front's.**
   `JMenu.setPopupMenuVisible` needs the structural call — whether a `JMenu` gets a real
   `JPopupMenu` child — which belongs to a brainstorm, not to a triage pass; the measurement and
   the reasoning are in the decision. **Re-run the sweep after each probe batch anyway**: it is
   how a newly-dropped setter shows up. The same structural gap now surfaces in a second place,
   `MenuSelectionManager.isComponentPartOfCurrentMenu`, whose walk stops at the path root because
   a `JMenu` reports no sub-elements — worth mentioning in that brainstorm as a second payoff.

2. **Tighten the 47 projection-only comparisons** (`Q_probe_coverage`). A getter whose type
   SB-Emulators ports is currently checked for `null` / identity-with-the-probe / array length, so
   two layers that both hand back a *fresh* instance agree no matter what it contains. The report
   ranks the types (`Icon` 15, `Component` 13, `LayoutManager` 9, …); giving one a real comparator
   upgrades its pairs to content equality. **Worth it exactly where a getter legitimately returns
   a copy**, since that is where projection is blind — and not urgent anywhere else, because the
   misses are false negatives, never false positives.

3. **The probe-value tail, 87 pairs, most of which will never have one.** `ComponentUI` alone is
   19 and is pure L&F (R_match_swing_errors sub-bucket (b)), and a long tail of one-pair `*UI`
   types (`ButtonUI`, `LabelUI`, `ListUI`, `TableUI`, `TreeUI`, …) goes with it. **So the first
   step here is to teach the report to separate "no probe yet" from "L&F, never will have one",**
   or the count keeps reading as a backlog it is not. What genuinely remains:
   - *Ported* types, one `ported()` line each in the shape of the 13 already there, and they pay
     by the pair: `TableColumn`, `TableColumnModel`, `TableCellEditor`, `TreeCellRenderer`,
     `JPopupMenu`, `JTable`, `JTableHeader`, `JDesktopIcon`, `Checkbox`, `CheckboxGroup`,
     `MenuElement[]`, `AbstractColorChooserPanel[]`.
   - *Reused* types, one `reused()` line each: `AbstractFormatter`, `AbstractFormatterFactory`,
     `DesktopManager`, `Dictionary`, `DropTarget`, `FileFilter`, `FileView`, `FilenameFilter`,
     `FocusTraversalPolicy`, `ImageObserver`, `Keymap`, `List`, `MenuBar`, `RowSorter`,
     `Calendar`, `File[]`, `int[]`, `Object[]`, `TreePath[]`, `StyleSheet`, `InputVerifier`.

4. **`SJColorChooser`'s `chooserPanels` / `previewPanel` stores need an eye, not a tool.** The
   emulator now owns both values itself
   ([D_colorchooser_panels_dropped](../emulators/decisions.md#D_colorchooser_panels_dropped)), so
   what is left is a *surrogate* round-trip store R_vaadin_first would rather see dropped —
   [SD_colorchooser_panels_dropped](../surrogates/decisions.md#SD_colorchooser_panels_dropped) still
   keeps it "so getters are honest". Neither tool can see it (an array return with no probe value),
   and it is cosmetic against the rules rather than observable to a migrator.

5. **`JInternalFrame.restoreSubcomponentFocus()` is missing, and adding it needs a focus target.**
   Deliberately absent — reasoning, including why stubbing it with a WARN is not the fix, is in
   the decision. Closing it means giving a `JInternalFrame` something focusable to hand focus to,
   which is a D_focus_managers question rather than a return-value one.

6. **The 125 pairs no host constructs on both layers**, the report's newest section. Most are the
   headless-only AWT and window classes an `xvfb` run reaches anyway — **so run that sweep before
   funding anything here**, since it is the cheap half. What is left after it is the ctor-less
   hosts (`Box`, `Box.Filler`, `GroupLayout`, `FileDialog`), which need the differ to learn a
   per-class construction recipe rather than `getDeclaredConstructor()`.

7. **Widen past pairs.** Most shadowed methods are neither a setter nor a paired reader and are
   entirely unaudited by either tool. Only worth funding on the evidence the pair sweep produced —
   which now exists, and now has a gate to land against.
