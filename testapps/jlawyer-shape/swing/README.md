# `testapps/jlawyer-shape/swing`

Stage-1 (pure Swing) of the JLawyer-shape testbed. See the [parent README](../README.md) for staging and rationale.

## Run

From this directory — this app is not part of any reactor, it has its own `mvnw`:

```
./mvnw -C compile exec:exec
```

Requires JDK 21+. Opens a desktop window. Pre-seeded with a few case folders, cases, and documents.

## What it does

The app mimics a small legal-practice case-management UI:

- **Left pane**: `JTree` of case folders (Corporate / Family Law / Personal Injury). Right-click a folder to add a case, or a case to delete / open.
- **Right pane**: `JTabbedPane`. Selecting a case in the tree opens it as a tab. Each tab is a vertical `JSplitPane`:
  - **Top**: `JList` of documents within the case. Right-click for context actions (open / copy path / delete). Drag a document onto another open case's list to move it.
  - **Bottom**: `JEditorPane` rendering the case's HTML notes.
- **Toolbar** (top): New Case, Properties…, Delete, Copy Case ID, Upload Document…, Simulate Long Op.
- **Menubar** (top): File / Edit / View / Tools / Help — standard.
- **Status bar** (bottom): `JLabel` (left), `JProgressBar` (centre, indeterminate during "Simulate Long Op"), counts (right).
- **Properties dialog** (modal): edit case title / client / priority (standalone `JRadioButton` group) / archived flag / date opened / case ID.

## Component coverage

This is the component checklist (see [`../README.md`](../README.md) for the rationale tying each back to JLawyer's actual usage):

| Component | Location in this app |
|---|---|
| `JTree` | Left pane case-folder tree |
| `JList` | Top of each case tab — document list |
| `JSplitPane` | Outer horizontal (tree / tabs); inner vertical (list / notes) |
| `JTabbedPane` | Right pane — multi-case open |
| `JPopupMenu` | Tree right-click, document-list right-click |
| `JFileChooser` | Toolbar "Upload Document…" |
| `JToolBar` | Top action bar |
| `JEditorPane` | Bottom of each case tab — HTML notes |
| `JProgressBar` | Status bar — Simulate Long Op |
| standalone `JRadioButton` | Properties dialog — priority |
| Clipboard | Toolbar "Copy Case ID"; popup "Copy document path-hint" |
| Drag-and-drop | Drag document between case tabs |

The CRUD surface (JFrame, JDialog, JOptionPane, JMenuBar + tree, JButton, JTextField, JCheckBox, JComboBox, JSpinner, JFormattedTextField, JLabel, JPanel, JScrollPane, BoxLayout, BorderLayout, GridBagLayout) is exercised throughout.
