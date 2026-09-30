# `testapps/crud/swing` — happy-path test scenarios

Manual / AI-driven UI tests against the running Swing CRUD app
(`./mvnw -C compile exec:exec` from this directory). Steps are written
so an AI-driver (e.g. via Swing-MCP `swing_snapshot` + `swing_click` /
`swing_set_text` / `swing_set_selection`) can follow them step-by-step;
a human can read them just the same.

Each scenario assumes the app has just been launched and the **starting
state** below holds. Run scenarios independently — restart the app between
scenarios to reset the seeded data.

## Starting state

The main window is `Employees — Swing CRUD`. The toolbar has buttons
`Add…`, `Edit…`, `Delete`, a vertical separator, and a `Favorites only`
toggle. Below the toolbar is a `JTable` with columns `Name`, `Role`,
`Level`, `Active`, `Favorite` and 4 seeded rows:

| Row | Name         | Role  | Level | Active | Favorite |
|-----|--------------|-------|-------|--------|----------|
| 0   | Ada Lovelace | ADMIN | 10    | yes    | yes      |
| 1   | Alan Turing  | USER  | 8     | yes    | no       |
| 2   | Grace Hopper | ADMIN | 9     | yes    | yes      |
| 3   | Guest        | GUEST | 1     | no     | no       |

The right side shows a `Preview` panel (empty initially — no row selected).
The bottom status bar reads `4 employees`. Menu bar: `File`, `Edit`,
`View`, `Help`. `Edit…` and `Delete` toolbar buttons are disabled until a
row is selected.

---

## Scenario 1 — Create an employee

1. Click the toolbar button captioned `Add…`.
2. Verify a modal dialog titled `New employee` is now visible.
3. Verify the dialog contains these fields (in order): `Name` (text),
   `Password` (password), `Role` (combo box), `Level` (spinner), `Rating`
   (slider), `Date of birth` (spinner showing today's date in
   `yyyy-MM-dd`), an `Active` checkbox (checked), `Bio` (multi-line text
   area), and `OK` / `Cancel` buttons.
4. Type `Marie Curie` into the `Name` text field.
5. Type `radium` into the `Password` field.
6. Open the `Role` combo box and select `USER` (items are `ADMIN`,
   `USER`, `GUEST`).
7. Set the `Level` spinner value to `7`.
8. Set the `Rating` slider value to `80`.
9. Set the `Date of birth` field to `1867-11-07` (clear the embedded text
   field and type the new value).
10. Leave the `Active` checkbox checked.
11. Type `Pioneering research on radioactivity.` into the `Bio` text area.
12. Click the `OK` button.
13. Verify the `New employee` dialog closes.
14. Verify the table now has 5 rows and the last row reads
    `Marie Curie | USER | 7 | yes | no` (Active checked, Favorite empty —
    the edit dialog has no `Favorite` field, so new rows are never
    favourite).
15. Verify the status bar reads `5 employees`.

### 1a — Create cancelled (no change)

1. Click `Add…`.
2. In the `New employee` dialog, type `Throwaway` into `Name`.
3. Click `Cancel`.
4. Verify the dialog closes and the table still has 4 rows; status bar
   still reads `4 employees`.

### 1b — Validation: empty name rejected

1. Click `Add…`.
2. Leave `Name` empty and click `OK`.
3. Verify a modal alert dialog titled `Validation` appears with the
   message `Name must not be empty.` and a single `OK` button.
4. Click `OK` on the alert.
5. Verify the alert closes and the `New employee` dialog is still open
   with focus returned to the `Name` field.
6. Click `Cancel` to dismiss.

---

## Scenario 2 — Update an employee

1. Click the row in the table whose `Name` cell reads `Alan Turing`
   (row index 1).
2. Verify the toolbar buttons `Edit…` and `Delete` are now enabled.
3. Verify the `Preview` panel shows `Name: Alan Turing`, `Role: USER`,
   `Level: 8`, `Rating: 90/100`, `Active: yes`, `Favorite: no`,
   `Date of birth: 1912-06-23`, `Bio: Theoretical computer scientist; foundational work on computability.`.
4. Click the toolbar button captioned `Edit…`.
5. Verify a modal dialog titled `Edit employee` is now visible, with
   fields pre-filled: `Name=Alan Turing`, `Level=8`, `Rating=90`,
   `Date of birth=1912-06-23`, `Active=checked`,
   `Bio=Theoretical computer scientist; foundational work on computability.`.
   `Role` shows `USER`. `Password` is masked (cannot be read).
6. Change `Name` to `Alan M. Turing`.
7. Change `Level` to `9`.
8. Change `Rating` to `95`.
9. Click `OK`.
10. Verify the `Edit employee` dialog closes.
11. Verify the table row at index 1 now reads
    `Alan M. Turing | USER | 9 | yes | no`.
12. Verify the `Preview` panel is empty (all value labels blank) and
    that the `Edit…` / `Delete` toolbar buttons are disabled. JTable
    clears its selection on `fireTableDataChanged()` (the whole-data
    update the app fires after `store.update`), which fires the
    `ListSelectionListener` with no selection.
13. Click the `Alan M. Turing` row to re-select it.
14. Verify the `Preview` panel now shows `Name: Alan M. Turing`,
    `Role: USER`, `Level: 9`, `Rating: 95/100`, `Active: yes`,
    `Favorite: no`, `Date of birth: 1912-06-23`,
    `Bio: Theoretical computer scientist; foundational work on computability.`.
15. Verify the status bar still reads `4 employees`.

### 2a — Edit via menu instead of toolbar

Same as Scenario 2 but in step 4, open the `Edit` menu and click
`Edit selected…` instead of the toolbar button. Behaviour must be
identical.

### 2b — Edit cancelled (no change)

1. Select the `Ada Lovelace` row.
2. Click `Edit…`.
3. In the `Edit employee` dialog, change `Name` to `xxx`.
4. Click `Cancel`.
5. Verify the dialog closes and the row's `Name` is still
   `Ada Lovelace`. Preview still reads `Name: Ada Lovelace`.

---

## Scenario 3 — Delete an employee

1. Click the row whose `Name` cell reads `Guest` (row index 3).
2. Verify `Edit…` and `Delete` toolbar buttons are enabled.
3. Click the toolbar button captioned `Delete`.
4. Verify a modal dialog titled `Confirm delete` is visible, containing
   the text `Delete "Guest"?` and buttons `OK` and `Cancel`.
5. Click `OK`.
6. Verify the dialog closes.
7. Verify the table now has 3 rows: `Ada Lovelace`, `Alan Turing`,
   `Grace Hopper` (no `Guest`).
8. Verify the status bar reads `3 employees`.
9. Verify no row is selected, so `Edit…` and `Delete` toolbar buttons
   are disabled again, and the `Preview` panel is empty.

### 3a — Delete cancelled (no change)

1. Select the `Alan Turing` row.
2. Click `Delete`.
3. In the `Confirm delete` dialog (showing `Delete "Alan Turing"?`),
   click `Cancel`.
4. Verify the dialog closes and the table still has 4 rows; the
   `Alan Turing` row is still present. Status bar reads `4 employees`.

### 3b — Delete via menu instead of toolbar

Same as Scenario 3 but in step 3, open the `Edit` menu and click
`Delete selected` instead of the toolbar button. Behaviour must be
identical.

---

## Scenario 4 — Showing preview

This scenario verifies the `Preview` panel reacts to selection changes
and that the `View → Show preview` toggle hides/shows it.

1. Verify the `View` menu's `Show preview` checkbox menu item is
   currently checked, and the `Preview` panel is visible on the right.
2. Verify that with no row selected, the `Preview` panel's value labels
   (after `Name:`, `Role:`, …, `Bio:`) are all empty.
3. Select the `Ada Lovelace` row (row 0).
4. Verify the `Preview` panel shows: `Name: Ada Lovelace`, `Role: ADMIN`,
   `Level: 10`, `Rating: 95/100`, `Active: yes`, `Favorite: yes`,
   `Date of birth: 1815-12-10`,
   `Bio: Mathematician; conceived the first algorithm intended for a machine.`.
5. Select the `Grace Hopper` row (row 2).
6. Verify the `Preview` panel updates to: `Name: Grace Hopper`,
   `Role: ADMIN`, `Level: 9`, `Rating: 92/100`, `Active: yes`,
   `Favorite: yes`, `Date of birth: 1906-12-09`,
   `Bio: Compiler pioneer; coined "debugging".`.
7. Select the `Guest` row (row 3).
8. Verify the `Preview` panel shows `Active: no`, `Favorite: no`
   (the only seeded inactive non-favorite employee).
9. Open the `View` menu and click the `Show preview` checkbox menu item
   to uncheck it.
10. Verify the `Preview` panel is no longer visible (the table now
    occupies the space previously held by the preview).
11. Open the `View` menu and click `Show preview` again to re-check it.
12. Verify the `Preview` panel reappears, still bound to the
    `Guest` selection: `Name: Guest`, `Active: no`, `Favorite: no`.

---

## Scenario 5 — Favorites only filter

This scenario verifies the toolbar `Favorites only` toggle filters the
table to favorite employees and that the status bar reflects the
filtered count. The seeded favorites are `Ada Lovelace` and
`Grace Hopper`.

1. Verify the toolbar toggle button captioned `Favorites only` is
   currently *not* selected (un-pressed look), and the table shows all
   4 seeded rows. Status bar reads `4 employees`.
2. Click the `Favorites only` toggle button.
3. Verify the toggle is now in the selected (pressed) state.
4. Verify the table now shows exactly 2 rows in this order:
   `Ada Lovelace | ADMIN | 10 | yes | yes` and
   `Grace Hopper | ADMIN | 9 | yes | yes`. `Alan Turing` and `Guest`
   must not appear.
5. Verify the status bar reads `2 of 4 employees (filtered)`.
6. Click the row `Grace Hopper` in the filtered table.
7. Verify the `Preview` panel shows `Name: Grace Hopper`,
   `Favorite: yes` and the `Edit…` / `Delete` toolbar buttons are
   enabled.
8. Click `Favorites only` again to deselect it.
9. Verify the table returns to all 4 rows and the status bar reads
   `4 employees`. Selection is cleared (no row highlighted), the
   `Preview` panel is empty, and the `Edit…` / `Delete` toolbar buttons
   are disabled — the filter swap fires `fireTableDataChanged()` on the
   underlying `JTable` model, which clears `ListSelectionModel` just as
   the post-edit refresh does in Scenario 2 step 12.
