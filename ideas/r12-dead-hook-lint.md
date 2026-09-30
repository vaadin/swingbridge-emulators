# R_no_vaadin_in_api limb 2 — the dead-hook lint's residue

**The gate is built and green, and everything about it has graduated.**
`emulators/src/test/java/vaadinx/R12DeadHookTest.java`, over the **protected** surface: 0 violations,
10 hooks individually declined, 19 caller mechanisms suppressed, over a call graph that now collapses
chains on our side as well as the JDK's. The design as built, the five things measurement changed
about it, the four hooks it caused to be wired and the two it declined:
[D_dead_hook_lint](../emulators/decisions.md#D_dead_hook_lint) (the pre-build design, and why limb 2
cannot be allow-list-free where limb 1 needed none: [D_r12_mechanized](../emulators/decisions.md#D_r12_mechanized)).
Scanners, per-refinement numbers and the public tier's rows lived under `ideas/tools/dead-hook/`, retired once the gate landed.
The sibling **property-fan-out** front closed on every axis; its calibration — the seven
requirements, the per-bucket noise rates, the ten value specimens — lives in
[D_property_fanout_audit](../emulators/decisions.md#D_property_fanout_audit),
[D_owed_events](../emulators/decisions.md#D_owed_events),
[D_reverse_fanout_rows](../emulators/decisions.md#D_reverse_fanout_rows),
[D_event_value_audit](../emulators/decisions.md#D_event_value_audit),
[D_missing_constants](../emulators/decisions.md#D_missing_constants) and the `SD_` pair. Nothing
about either front's *reasoning* is left in this file — only what is still unbuilt.

**Status: paused, deliberately** (2026-08-27; chain collapse taken off the list 2026-09-10, having
found and fixed one hook — `JTextPane.createDefaultEditorKit`). The gate holds the line, which is
what makes pausing safe — a new dead hook on the protected surface now fails the build rather than
waiting to be found by luck. Nothing below is scheduled; pick it up when it is the most valuable
thing on the table, not because the list exists. Item 2 is the only one with a suspected live defect
behind it.

**The residue**, in rough priority order:

1. **The public surface** — 396 rows, 289 distinct unreached names, and **no gate is possible** on
   them: SB-Emulators drives a Vaadin peer where the JDK computes geometry, so "the JDK's
   `getColumnName` calls `convertColumnIndexToModel` and ours does not" is a different
   implementation, not a stranded override (D_dead_hook_lint). So this is a one-shot triage pass
   whose findings graduate to `D*` entries, not a rule. `R12DeadHookTest.reportProtectedHookCoverage`
   prints the triage view; the scratch probe that adjudicated a single row was retired with `ideas/tools/`.
2. **`AbstractButton`'s three listener factories.** `createChangeListener` /
   `createActionListener` / `createItemListener`, declined against `setModel`'s javadoc, which folds
   the JDK's listener rewiring into the declined effect. Worth revisiting: the attachment and the
   fan-out are pure Java, and [R_decline_effect_only](../CLAUDE.md#hard-rules) says only the repaint
   is declinable. Wants its own slice — it changes `ButtonModel` semantics for every button.
3. **Signature-level keying.** Rows are `(class, name)`, so overloads merge and
   `java.awt.Component` carries three `firePropertyChange` declarations as one. Needs the
   `vaadinx.awt.Component` ↔ `java.awt.Component` descriptor mapping limb 1 already does on type
   names. Same family of caveats: the "is it called by us" join also accepts a call on any emulator
   superclass **or** subclass, which is loose in our favour.
4. **Property fan-out as a second gate off the same `jrt:/java.desktop` import.** *"For each
   `(class, property)` SB-Emulators fires, does the JDK class's ancestry reach a
   `firePropertyChange` with that constant?"* — bytecode, no source parsing, constants already
   resolved by the compiler, and the one-hop walk it needs is the walk limb 2 already does. That
   would replace the regex-and-brace scanners that lived under `ideas/tools/property-fanout/` until
   they were retired. Three things it must respect, each already paid for once:
   enumerate over emulated *classes*, never over fire sites (a class that fires nothing owes the
   most); know which module it is reading before scoring a silence (R_decline_effect_only declines
   only the effect, R_vaadin_first declines the property whole); and never score "same number of
   fires as the JDK" (D_focus_property_registry). One query nothing in the family answers yet, and a
   gate is the only thing that could: **which methods write a bound property's backing field instead
   of calling its setter** — the `JFileChooser.doLoad` / `doSave` shape, fixed per
   D_reverse_fanout_rows but uncheckable, since both sides "have" the setter and the caller that
   bypasses it has no JDK counterpart to diff against.
