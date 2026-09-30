/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.DefaultCellEditor
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import vaadinx.awt.Component;
import vaadinx.awt.event.MouseEvent;
import vaadinx.swing.table.TableCellEditor;
import vaadinx.swing.tree.TreeCellEditor;

import javax.swing.AbstractCellEditor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.Serializable;
import java.util.EventObject;

/**
 * Port of {@link javax.swing.DefaultCellEditor} — the stock cell editor over a
 * {@link JTextField}, {@link JComboBox}, or {@link JCheckBox}. Body ported
 * faithfully (R_swing_is_truth) from the JDK class: same three ctors, same {@code EditorDelegate}
 * shape, same {@code clickCountToStart} defaults (2 for text field, 1 for combo
 * and check box).
 *
 * <p>Layering: this is an <b>emulator-only</b> type (confirmed 2026-07-07, see
 * D_jtable_cell_editing in {@code emulators/decisions.md}). It is inherently stage-2 / Swing-shaped — its
 * ctors take emulator components and {@link #getTableCellEditorComponent} returns
 * {@link vaadinx.awt.Component}. Stage-3 (pure-surrogate) code edits via the
 * Vaadin {@code Grid.Editor} that {@code SJTable} inherits, not through this class.
 *
 * <p>Reuse boundary: extends the JDK {@link AbstractCellEditor} (which references
 * no {@code java.awt.Component}, so it is reused unchanged per D_whitelist_porting — it carries the
 * {@code CellEditorListener} list + {@code fireEditingStopped}/{@code fireEditingCanceled}
 * plumbing) and implements the ported {@link TableCellEditor} + {@link TreeCellEditor}.
 *
 * <p>The {@code EditorDelegate} registers itself as an {@link ActionListener} on the
 * editor component; because the emulator {@code JTextField}/{@code JComboBox}/{@code JCheckBox}
 * fire real Swing {@code ActionEvent}s via their R_swing_is_truth peer bridges, a commit on the
 * editor component ({@code JTextField} Enter, combo selection) fans out to the
 * {@code CellEditorListener}s with no Grid involved — which is what lets this class
 * be unit-tested in isolation ahead of the JTable Grid.Editor bridge.
 */
@SuppressWarnings("serial") // Same-version serialization only
public class DefaultCellEditor extends AbstractCellEditor
        implements TableCellEditor, TreeCellEditor {

    /** The editing component; set by one of the three type-specific ctors. */
    protected JComponent editorComponent;

    /**
     * Bridges the editor component's value in/out and translates its action/item
     * events into {@code stopCellEditing} calls. A type-specific subclass instance
     * is created by each ctor.
     */
    protected EditorDelegate delegate;

    /** Clicks needed to start editing: 2 for a text field, 1 for combo/check box. */
    protected int clickCountToStart = 1;

    public DefaultCellEditor(final JTextField textField) {
        editorComponent = textField;
        this.clickCountToStart = 2;
        delegate = new EditorDelegate() {
            @Override
            public void setValue(Object value) {
                textField.setText((value != null) ? value.toString() : "");
            }

            @Override
            public Object getCellEditorValue() {
                return textField.getText();
            }
        };
        textField.addActionListener(delegate);
    }

    public DefaultCellEditor(final JCheckBox checkBox) {
        editorComponent = checkBox;
        delegate = new EditorDelegate() {
            @Override
            public void setValue(Object value) {
                boolean selected = false;
                if (value instanceof Boolean) {
                    selected = ((Boolean) value).booleanValue();
                } else if (value instanceof String) {
                    selected = value.equals("true");
                }
                checkBox.setSelected(selected);
            }

            @Override
            public Object getCellEditorValue() {
                return Boolean.valueOf(checkBox.isSelected());
            }
        };
        checkBox.addActionListener(delegate);
    }

    public DefaultCellEditor(final JComboBox<?> comboBox) {
        editorComponent = comboBox;
        // JDK also sets the "JComboBox.isTableCellEditor" client property here;
        // dropped — it is a BasicComboBoxUI L&F hint (R_match_swing_errors sub-bucket (b)), and we
        // model no L&F, so nothing reads it.
        delegate = new EditorDelegate() {
            @Override
            public void setValue(Object value) {
                comboBox.setSelectedItem(value);
            }

            @Override
            public Object getCellEditorValue() {
                return comboBox.getSelectedItem();
            }

            @Override
            public boolean shouldSelectCell(EventObject anEvent) {
                if (anEvent instanceof MouseEvent) {
                    MouseEvent e = (MouseEvent) anEvent;
                    return e.getID() != MouseEvent.MOUSE_DRAGGED;
                }
                return true;
            }

            // JDK's combo delegate also overrides stopCellEditing() to flush an
            // *editable* combo's typed text via comboBox.actionPerformed(...) before
            // stopping. Dropped for now: the emulator JComboBox exposes no public
            // actionPerformed seam, and editable-combo-in-cell commit stays a minor
            // drop after the JTable Grid.Editor pass (D_jtable_cell_editing). Non-editable
            // combos — the common case — stop-edit correctly via the inherited
            // EditorDelegate.stopCellEditing.
        };
        comboBox.addActionListener(delegate);
    }

    /** Returns the editor component (JDK: {@code getComponent}). */
    public Component getComponent() {
        return editorComponent;
    }

    public void setClickCountToStart(int count) {
        clickCountToStart = count;
    }

    public int getClickCountToStart() {
        return clickCountToStart;
    }

    @Override
    public Object getCellEditorValue() {
        return delegate.getCellEditorValue();
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
        return delegate.isCellEditable(anEvent);
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        return delegate.shouldSelectCell(anEvent);
    }

    @Override
    public boolean stopCellEditing() {
        return delegate.stopCellEditing();
    }

    @Override
    public void cancelCellEditing() {
        delegate.cancelCellEditing();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
                                                 boolean isSelected, int row, int column) {
        delegate.setValue(value);
        // JDK adds a JCheckBox-only block here that copies the cell renderer's
        // border/background onto the editor to avoid a "flashing" effect. Dropped
        // per R_layouts_close_enough (layout/visual is best-effort) + R_match_swing_errors sub-bucket (b): it is pure L&F
        // visual polish with no bearing on the edited value.
        return editorComponent;
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value,
                                                boolean isSelected, boolean expanded,
                                                boolean leaf, int row) {
        // JTree in-cell editing is deferred (D_gap_severity_triage sub-bucket (b)); this method is not
        // invoked today (JTree in-cell editing is deferred). Kept
        // faithful-enough that a migrated DefaultCellEditor recompiles and, should the
        // JTree rename build later land, the editor seeds from the node value. JDK
        // first routes value through tree.convertValueToText; that faithful text
        // conversion is deferred with the rest of JTree editing.
        delegate.setValue(value);
        return editorComponent;
    }

    /**
     * Handles the editor component's value marshalling and turns its action/item
     * events into {@code stopCellEditing}. Ported from JDK's inner class; each ctor
     * subclasses it to bind the specific component's get/set.
     */
    protected class EditorDelegate implements ActionListener, ItemListener, Serializable {

        /** The delegate's cached value (base impl; type-specific subclasses read the component live). */
        protected Object value;

        public Object getCellEditorValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public boolean isCellEditable(EventObject anEvent) {
            if (anEvent instanceof MouseEvent) {
                return ((MouseEvent) anEvent).getClickCount() >= clickCountToStart;
            }
            return true;
        }

        public boolean shouldSelectCell(EventObject anEvent) {
            return true;
        }

        public boolean startCellEditing(EventObject anEvent) {
            return true;
        }

        public boolean stopCellEditing() {
            fireEditingStopped();
            return true;
        }

        public void cancelCellEditing() {
            fireEditingCanceled();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            DefaultCellEditor.this.stopCellEditing();
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            DefaultCellEditor.this.stopCellEditing();
        }
    }
}
