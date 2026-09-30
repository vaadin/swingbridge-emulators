/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.swing.table.TableCellEditor;

import javax.swing.AbstractCellEditor;
import javax.swing.table.DefaultTableModel;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A <b>modal cell editor</b> — a {@code TableCellEditor} whose {@code getCellEditorValue()} opens
 * a blocking {@code JOptionPane} — which D_jtable_cell_editing recorded as supported in principle and
 * never smoke-tested. The shape is {@code testapps/inventory}'s
 * {@code CartTableQuantityCellEditor}: on a quantity over stock it pops "Max Qty Exceed"
 * and substitutes 0.
 *
 * <p>What makes it delicate is <i>where</i> the dialog opens: {@code getCellEditorValue()} runs
 * inside the {@code editingStopped} commit, so the park happens while the {@code Grid.Editor}
 * is closing and with the editor's own peer still on screen.
 */
class JTableModalEditorTest extends AbstractKaribuTest {

    /** Reproduces the app's editor: a JTextField, a max, and a blocking dialog on overflow. */
    private static class MaxQtyEditor extends AbstractCellEditor implements TableCellEditor {

        private final int max;
        final JTextField field = new JTextField();

        MaxQtyEditor(int max) {
            this.max = max;
        }

        @Override
        public vaadinx.awt.Component getTableCellEditorComponent(
                JTable table, Object value, boolean isSelected, int row, int column) {
            field.setText(value == null ? "" : value.toString());
            return field;
        }

        @Override
        public Object getCellEditorValue() {
            int qty = parseOrZero(field.getText());
            if (qty > max) {
                JOptionPane.showMessageDialog(
                        null, "The maximum qty in stock is " + max, "Max Qty Exceed",
                        JOptionPane.INFORMATION_MESSAGE);
                return 0;
            }
            return qty;
        }

        /** Kotlin's {@code toIntOrNull() ?: 0} — the app's own lenient parse. */
        private static int parseOrZero(String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    private static JTable tableWith(MaxQtyEditor editor) {
        JTable table = new JTable(new DefaultTableModel(
                new Object[][] {{1}}, new Object[] {"qty"}));
        table.getColumnModel().getColumn(0).setCellEditor(editor);
        return table;
    }

    @Test
    @DisplayName("a modal dialog from getCellEditorValue parks the commit and resumes on OK")
    void modalDialogParksTheCommitAndResumesOnOk() throws InterruptedException {
        MaxQtyEditor editor = new MaxQtyEditor(5);
        JTable table = tableWith(editor);
        CountDownLatch committed = new CountDownLatch(1);

        EHelper.callSwing(() -> {
            table.editCellAt(0, 0, null);
            editor.field.setText("99");        // over stock → the dialog path
            table.getCellEditor().stopCellEditing();
            committed.countDown();
        });

        // The commit must still be parked: the dialog is up and the latch un-tripped.
        assertEquals(1L, committed.getCount(),
                "stopCellEditing should be parked inside the modal dialog, not returned");
        Button ok = LocatorJ._get(Button.class, spec -> spec.withText("OK"));

        EHelper.callSwing(() -> LocatorJ._click(ok));

        assertTrue(committed.await(5, TimeUnit.SECONDS), "the commit never resumed after OK");
        assertEquals(Integer.valueOf(0), table.getValueAt(0, 0),
                "the editor's substituted value must be stored");
        assertFalse(table.isEditing(), "the editor must be closed after the parked commit finished");
        assertTrue(LocatorJ._find(Dialog.class).isEmpty(), "the dialog must be gone");
    }

    @Test
    @DisplayName("the same editor commits without a dialog when the value is in range")
    void inRangeValueCommitsWithoutADialog() {
        MaxQtyEditor editor = new MaxQtyEditor(5);
        JTable table = tableWith(editor);

        EHelper.callSwing(() -> {
            table.editCellAt(0, 0, null);
            editor.field.setText("3");
            table.getCellEditor().stopCellEditing();
        });

        assertEquals(Integer.valueOf(3), table.getValueAt(0, 0));
        assertFalse(table.isEditing());
    }

    @Test
    @DisplayName("the parked commit has settled by the time the OK click returns")
    void parkedCommitHasSettledWhenTheClickReturns() {
        // D_callswing_loom: a callSwing drains its access queue synchronously, so a cascade
        // settles before the next user action. Here the cascade is the resumed
        // commit, and the "next user action" is the second edit below — which is
        // why this property, not just eventual completion, is the one that matters.
        MaxQtyEditor editor = new MaxQtyEditor(5);
        JTable table = tableWith(editor);

        EHelper.callSwing(() -> {
            table.editCellAt(0, 0, null);
            editor.field.setText("99");
            table.getCellEditor().stopCellEditing();
        });
        // Look up OUTSIDE callSwing — Karibu's lookup does not see the tree from the VT.
        Button ok = LocatorJ._get(Button.class, spec -> spec.withText("OK"));
        EHelper.callSwing(() -> LocatorJ._click(ok));

        assertEquals(Integer.valueOf(0), table.getValueAt(0, 0),
                "the resumed commit must have completed before the OK click returned");
        assertFalse(table.isEditing(), "and the editor must be closed");
    }

    @Test
    @DisplayName("editing the same cell again after a parked commit still works")
    void editingAgainAfterAParkedCommitWorks() throws InterruptedException {
        // Regression guard for editor state stranded by the park.
        MaxQtyEditor editor = new MaxQtyEditor(5);
        JTable table = tableWith(editor);
        CountDownLatch firstCommit = new CountDownLatch(1);

        EHelper.callSwing(() -> {
            table.editCellAt(0, 0, null);
            editor.field.setText("99");
            table.getCellEditor().stopCellEditing();
            firstCommit.countDown();
        });
        // Look up OUTSIDE callSwing — Karibu's lookup does not see the tree from the VT.
        Button ok = LocatorJ._get(Button.class, spec -> spec.withText("OK"));
        EHelper.callSwing(() -> LocatorJ._click(ok));
        assertTrue(firstCommit.await(5, TimeUnit.SECONDS));

        EHelper.callSwing(() -> {
            table.editCellAt(0, 0, null);
            editor.field.setText("4");
            table.getCellEditor().stopCellEditing();
        });
        assertEquals(Integer.valueOf(4), table.getValueAt(0, 0));
    }
}
