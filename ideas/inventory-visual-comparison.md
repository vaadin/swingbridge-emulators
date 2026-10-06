# Inventory, side by side: what the first visual comparison found

**Status:** open, a worklist. **Maintainer-facing.** On 2026-10-06 the inventory app ran twice at
once — stage 1 under `xvfb-run`, driven through [swing-mcp](https://github.com/vaadin/swing-mcp), and
stage 2 in a browser — and the same twelve screens were screenshotted at the same 850×580 (the size
`AppFrame.setBounds` asks for; Xvfb has no window manager to honour `MAXIMIZED_BOTH`). This is the
first time anything past login was *looked at* against the desktop, and the first verdict on
[M1D_addon_packaging](../migration/1-swing-to-emulators/decisions.md#M1D_addon_packaging)'s open
criterion: **the `FormLayout` panels are structurally wrong**, per
[R_layouts_close_enough](../CLAUDE.md#R_layouts_close_enough) — fields not filling their cells,
labels wrapping, panels overlapping their neighbours. Functionally the app is fine: login, every
toolbar screen, every Initial Records screen and both dialogs open on seeded data with no server
exception.

## The steps

Ordered by certainty, not size. Each names its root cause where one was confirmed in the DOM.

1. **Done 2026-10-06** (`JViewport.addImpl` → `setView`, `JScrollPaneTest.viewportAddSetsTheView`;
   Vendor's Address field confirmed on screen). **`JViewport.add(view)` never shows the view.** `GTextArea extends JScrollPane` does
   `getViewport().add(textArea)`. The JDK's `JViewport.addImpl` is `setView(child)`; ours has no
   `addImpl`, so the call lands in the viewport's inert `Div` peer, the `vaadin-scroller` stays
   empty, and `getViewportView()` stays `null`. Vendor's and Branch Office's **Address field is not
   on screen at all.** [R_no_vaadin_in_api](../CLAUDE.md#R_no_vaadin_in_api) limb 2 in its plainest
   form: a JDK call path we expose and do not route.
2. **Done 2026-10-06** for the text positions (`JLabel.pushTextPosition`,
   `JLabelTest.textPositionsReachThePeer`; toolbar confirmed icon-above-text on screen).
   **`JLabel` text position is stored, never rendered.** `setVerticalTextPosition(BOTTOM)` /
   `setHorizontalTextPosition(CENTER)` store and fire the PCE but drive nothing, so every toolbar
   `ActionButton` puts its icon left of its text instead of above it. `SJLabel` already maps both
   onto `flex-direction`. Its siblings `horizontalAlignment`, `verticalAlignment` and `iconTextGap`
   are in the same state — stored, never pushed, though `SJLabel` maps them too
   (`justify-content`, `align-items`, `gap`) — and are the natural follow-up.
3. **A `JTextField(columns)` does not fill its cell.** The column count becomes an inline
   `width: var(--emul-layout-w, calc(10ch + 2em))`, and an explicit `width` beats the cell's
   `justify-self: stretch`. In Swing `columns` is only the preferred width, and `FormLayout`'s
   default `FILL` alignment overrides it. Visible on nearly every form (Category's Name field: 120px
   on the web, 240px on the desktop); `ChangePasswordPanel` shows both behaviours side by side,
   because one of its fields has no column count and does fill. Fix at whichever layer owns the
   preferred-width-vs-stretch rule — probably not the add-on, since `GridBagLayout` `fill` would hit
   the same thing.
4. **`JLabel` plain text wraps.** Its peer carries `white-space: normal`; a Swing `JLabel` without
   `<html>` never wraps. "Item Name", "Rack Number", "New userName:" break onto two lines, and the
   home screen's welcome sentence becomes three. Check against
   [jlabel-html-body-width.md](./jlabel-html-body-width.md) before fixing: the `<html>` case *must*
   still wrap.
5. **Forms overflow the top of their `JSplitPane` and paint over the bottom.** Vaadin fields are
   ~36px tall against Swing's ~20px, so the forms are taller; where the desktop clips the overflow,
   the browser draws it over the button row and the table. Item Entry's buttons sit on its Rack
   Number row and its Quantity/Unit/Rate/Total column is past the right edge; Category loses
   Specification 7–10 and Save; Transfer's Receiver Office and Date overlap. Root cause not traced:
   look first at whether a split pane's halves clip (`overflow`) and whether `max(Ndlu;default)`
   rows grow with their content.
6. **Every `TitledBorder` caption is cut in half** by the edge of whatever sits above the panel.
   `UIManager.getBorder("TitledBorder.border")` WARNs twice in the same run, which may be related.
7. **`UIManager.get("TextField.background")` / `("TextField.inactiveBackground")` WARN 198 times
   each** — the app reads them on every enable/disable. With the 24 `setBounds` WARNs, one
   `setExtendedState(6)`, one `setMaximizedBounds` and one no-`EmulatorContext` date WARN from a
   worker path, the zero-stub-WARN gate is **not met**. Triage the `UIManager` keys under
   [D_gap_severity_triage](../emulators/decisions.md#D_gap_severity_triage): L&F dispatch is out,
   but a handful of colour defaults that apps read directly may not be L&F dispatch.
8. **The 403 on a swapped icon reproduces**, and the broken `<img>`'s alt text is the icon's
   `file:/home/…/target/classes/images/…` URL — `ImageIcon(URL)`'s JDK-default description, so
   faithful, but it puts the server's absolute path in the page. Owned by
   [icon-swap-broken-image.md](./icon-swap-broken-image.md); add the alt-text observation there.
9. **Dialog sizes: answers [dialog-too-small-for-content.md](./dialog-too-small-for-content.md).**
   `Q_structural`: the Change Password dialog's left-hand clipping is **faithful** — real Swing clips
   it identically at the 480×340 `GDialog.setAbstractFunctionPanel` asks for — but its Change/Reset
   buttons fall below the fold on the web only, which is step 5's height difference again.
   `Q_pack_semantics` does not arise: `GDialog` calls `setSize`, never `pack`. New: About/Support
   at 400×190 shows ~4 of its 7 lines, because Vaadin's dialog header and padding come out of the
   size the app gave the whole window. Fold that idea file into this one and delete it.
10. **Bookkeeping, once the above is triaged.** Record the verdict in M1D_addon_packaging (the
    "Run 1 stopped at the login screen" sentence there is stale); correct
    `testapps/inventory/1-emulators/STUMBLES.md` "Not a doc gap" (b), whose dialog-clipping bullet
    blames SB-Emulators for faithful behaviour; fix the same stale sentence in
    `.claude/skills/guide-migrateapp/inventory.md`; then graduate
    [inventory-testapp-migration.md](./inventory-testapp-migration.md), whose gate ladder still needs
    a durable home (`migration/1-swing-to-emulators/testing.md`).
11. **swing-mcp, upstream (sibling repo, not ours to fix here).** `swing_screenshot` renders the
    frame with `printAll` and **omits the `JMenuBar`**; its Gradle 8.14 wrapper refuses JDK 25 (the
    agent jar built fine with Gradle 9.8 `--offline`).

## Re-running the comparison

```
# stage 1, with swing-mcp on 127.0.0.1:18088
cd testapps/inventory/swing
xvfb-run -a -s "-screen 0 1400x900x24" ./mvnw -C test-compile exec:exec \
  -Dexec.args="-javaagent:<swing-mcp>/swing-mcp-agent/build/libs/swing-mcp-agent-<v>.jar -classpath %classpath com.ca.ui.Main"
# stage 2
cd testapps/inventory/1-emulators && SERVER_PORT=8090 ./mvnw -C test-compile exec:exec
```

Capture the desktop with `java.awt.Robot` against the Xvfb display (`DISPLAY=:99`,
`XAUTHORITY=/tmp/xvfb-run.*/Xauthority`), not with `swing_screenshot` (step 11). Sign in
`ADMIN`/`ADMIN` on both, set the browser viewport to the frame's size, and dismiss Vaadin's dev-mode
toast — it covers the toolbar's right end. swing-mcp needs a fresh `swing_snapshot` before every
mutation, since a mutation clears its ref map.
