# R_no_vaadin_in_api limb 2 — the dead-hook lint's residue

The gate is built and green: `emulators/src/test/java/vaadinx/R12DeadHookTest.java`, over the
**protected** surface. Its design, and why limb 2 cannot be allow-list-free where limb 1 needed none:
[D_dead_hook_lint](../emulators/decisions.md#D_dead_hook_lint) /
[D_r12_mechanized](../emulators/decisions.md#D_r12_mechanized). The sibling property-fan-out front's
calibration lives in [D_property_fanout_audit](../emulators/decisions.md#D_property_fanout_audit),
[D_owed_events](../emulators/decisions.md#D_owed_events),
[D_reverse_fanout_rows](../emulators/decisions.md#D_reverse_fanout_rows),
[D_event_value_audit](../emulators/decisions.md#D_event_value_audit) and
[D_missing_constants](../emulators/decisions.md#D_missing_constants). This file holds only what is
still unbuilt.

**Status: paused, deliberately** (2026-08-27). The gate holds the line, which is what makes pausing
safe — a new dead hook on the protected surface fails the build rather than waiting to be found by
luck. Nothing below is scheduled; pick it up when it is the most valuable thing on the table, not
because the list exists.

**The residue**, in rough priority order:

1. **The public surface** — 396 rows, 289 distinct unreached names, and **no gate is possible** on
   them: SB-Emulators drives a Vaadin peer where the JDK computes geometry, so "the JDK's
   `getColumnName` calls `convertColumnIndexToModel` and ours does not" is a different
   implementation, not a stranded override (D_dead_hook_lint). So this is a one-shot triage pass
   whose findings graduate to `D*` entries, not a rule. `R12DeadHookTest.reportProtectedHookCoverage`
   prints the triage view.
2. **Signature-level keying.** Rows are `(class, name)`, so overloads merge and
   `java.awt.Component` carries three `firePropertyChange` declarations as one. Needs the
   `vaadinx.awt.Component` ↔ `java.awt.Component` descriptor mapping limb 1 already does on type
   names. Same family of caveats: the "is it called by us" join also accepts a call on any emulator
   superclass **or** subclass, which is loose in our favour.
3. **Property fan-out as a second gate off the same `jrt:/java.desktop` import.** *"For each
   `(class, property)` SB-Emulators fires, does the JDK class's ancestry reach a
   `firePropertyChange` with that constant?"* — bytecode, no source parsing, constants already
   resolved by the compiler, and the one-hop walk it needs is the walk limb 2 already does. That
   would replace the retired regex-and-brace scanners. Three things it must respect, each already
   paid for once: enumerate over emulated *classes*, never over fire sites (a class that fires
   nothing owes the most); know which module it is reading before scoring a silence
   (R_decline_effect_only declines only the effect, R_vaadin_first declines the property whole); and
   never score "same number of fires as the JDK" (D_focus_property_registry). One query nothing in
   the family answers yet, and a gate is the only thing that could: **which methods write a bound
   property's backing field instead of calling its setter** — the `JFileChooser.doLoad` / `doSave`
   shape, fixed per D_reverse_fanout_rows but uncheckable, since both sides "have" the setter and
   the caller that bypasses it has no JDK counterpart to diff against.
