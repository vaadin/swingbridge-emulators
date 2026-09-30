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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJComboBox;
import vaadinx.AbstractKaribuTest;

import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Groundwork tests for the emulator-only {@link DefaultCellEditor} (see D_jtable_cell_editing in
 * {@code emulators/decisions.md}). Deliberately exercises the editor type system in
 * isolation — no {@code JTable}, no Grid, no {@code Grid.Editor} — to prove the three
 * flavors' value marshalling, {@code clickCountToStart} defaults, {@code CellEditorListener}
 * fan-out, and (critically) that the {@code EditorDelegate} rides the emulator
 * components' R_swing_is_truth {@code ActionEvent} bridges to fire {@code editingStopped}. That last part
 * is what the forthcoming JTable Grid.Editor bridge relies on.
 */
class DefaultCellEditorTest extends AbstractKaribuTest {

    /** Records editingStopped / editingCanceled callbacks for assertions. */
    private static class RecordingListener implements CellEditorListener {

        final List<ChangeEvent> stopped = new ArrayList<>();
        final List<ChangeEvent> canceled = new ArrayList<>();

        @Override
        public void editingStopped(ChangeEvent e) {
            stopped.add(e);
        }

        @Override
        public void editingCanceled(ChangeEvent e) {
            canceled.add(e);
        }
    }

    // --- Text field flavor -------------------------------------------------

    @Test
    @DisplayName("text field editor round-trips String value and defaults clickCountToStart to 2")
    void textFieldEditorRoundTrips() {
        JTextField tf = new JTextField();
        DefaultCellEditor editor = new DefaultCellEditor(tf);
        assertEquals(2, editor.getClickCountToStart());
        assertSame(tf, editor.getComponent());

        vaadinx.awt.Component comp =
                editor.getTableCellEditorComponent(null, "hello", false, 0, 0);
        assertSame(tf, comp);
        assertEquals("hello", tf.getText());
        assertEquals("hello", editor.getCellEditorValue());
    }

    @Test
    @DisplayName("text field editor seeds empty string for null value")
    void textFieldEditorSeedsEmptyForNull() {
        JTextField tf = new JTextField("stale");
        DefaultCellEditor editor = new DefaultCellEditor(tf);
        editor.getTableCellEditorComponent(null, null, false, 0, 0);
        assertEquals("", tf.getText());
        assertEquals("", editor.getCellEditorValue());
    }

    // --- Check box flavor --------------------------------------------------

    @Test
    @DisplayName("check box editor round-trips Boolean value and defaults clickCountToStart to 1")
    void checkBoxEditorRoundTrips() {
        JCheckBox cb = new JCheckBox();
        DefaultCellEditor editor = new DefaultCellEditor(cb);
        assertEquals(1, editor.getClickCountToStart());

        editor.getTableCellEditorComponent(null, Boolean.TRUE, false, 0, 0);
        assertTrue(cb.isSelected());
        assertEquals(Boolean.TRUE, editor.getCellEditorValue());

        editor.getTableCellEditorComponent(null, Boolean.FALSE, false, 0, 0);
        assertFalse(cb.isSelected());
        assertEquals(Boolean.FALSE, editor.getCellEditorValue());
    }

    @Test
    @DisplayName("check box editor coerces the String true to selected")
    void checkBoxEditorCoercesStrings() {
        JCheckBox cb = new JCheckBox();
        DefaultCellEditor editor = new DefaultCellEditor(cb);
        editor.getTableCellEditorComponent(null, "true", false, 0, 0);
        assertTrue(cb.isSelected());
        editor.getTableCellEditorComponent(null, "false", false, 0, 0);
        assertFalse(cb.isSelected());
    }

    // --- Combo box flavor --------------------------------------------------

    @Test
    @DisplayName("combo box editor round-trips selected item and defaults clickCountToStart to 1")
    void comboBoxEditorRoundTrips() {
        JComboBox<String> combo = new JComboBox<>(new String[] {"a", "b", "c"});
        DefaultCellEditor editor = new DefaultCellEditor(combo);
        assertEquals(1, editor.getClickCountToStart());

        editor.getTableCellEditorComponent(null, "b", false, 0, 0);
        assertEquals("b", combo.getSelectedItem());
        assertEquals("b", editor.getCellEditorValue());
    }

    // --- CellEditorListener fan-out ---------------------------------------

    @Test
    @DisplayName("stopCellEditing fires editingStopped with the editor as ChangeEvent source")
    void stopCellEditingFiresStopped() {
        DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        RecordingListener listener = new RecordingListener();
        editor.addCellEditorListener(listener);

        assertTrue(editor.stopCellEditing());
        assertSame(editor, assertSingle(listener.stopped).getSource());
        assertEquals(0, listener.canceled.size());
    }

    @Test
    @DisplayName("cancelCellEditing fires editingCanceled")
    void cancelCellEditingFiresCanceled() {
        DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        RecordingListener listener = new RecordingListener();
        editor.addCellEditorListener(listener);

        editor.cancelCellEditing();
        assertEquals(0, listener.stopped.size());
        assertSame(editor, assertSingle(listener.canceled).getSource());
    }

    @Test
    @DisplayName("removed listener no longer fires")
    void removedListenerNoLongerFires() {
        DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        RecordingListener listener = new RecordingListener();
        editor.addCellEditorListener(listener);
        editor.removeCellEditorListener(listener);
        editor.stopCellEditing();
        assertEquals(0, listener.stopped.size());
    }

    // --- Emulator event -> delegate -> editingStopped ---------------------

    @Test
    @DisplayName("combo selection fires editingStopped via the delegate's ActionListener bridge")
    void comboSelectionFiresStopped() {
        // Proves the EditorDelegate is wired to the emulator JComboBox's R_swing_is_truth
        // ActionEvent fan-out: committing a selection stops editing with no Grid
        // in the loop. This is the seam the JTable Grid.Editor bridge builds on.
        JComboBox<String> combo = new JComboBox<>(new String[] {"a", "b"});
        DefaultCellEditor editor = new DefaultCellEditor(combo);
        RecordingListener listener = new RecordingListener();
        editor.addCellEditorListener(listener);

        combo.setSelectedItem("b");
        assertSame(editor, assertSingle(listener.stopped).getSource());
    }

    @Test
    @DisplayName("client-driven combo selection fires editingStopped")
    void clientDrivenComboSelectionFiresStopped() {
        // The realistic in-cell editing gesture: the user picks from the dropdown,
        // i.e. a client-side value change on the peer (Karibu's _setValue,
        // isFromClient=true) — not a programmatic emulator setter. The JTable
        // Grid.Editor bridge depends on this path reaching the delegate, so pin it.
        JComboBox<String> combo = new JComboBox<>(new String[] {"a", "b"});
        DefaultCellEditor editor = new DefaultCellEditor(combo);
        RecordingListener listener = new RecordingListener();
        editor.addCellEditorListener(listener);

        @SuppressWarnings("unchecked")
        SJComboBox<String> peer = (SJComboBox<String>) combo.getPeer();
        LocatorJ._setValue(peer, "b");

        assertEquals("b", editor.getCellEditorValue());
        assertSame(editor, assertSingle(listener.stopped).getSource());
    }

    @Test
    @DisplayName("text field postActionEvent fires editingStopped via the delegate")
    void postActionEventFiresStopped() {
        // JTextField.addActionListener fires on Enter (a Vaadin KeyPressListener);
        // postActionEvent drives the same fan-out without simulating a keystroke,
        // proving the text-field delegate commits through its ActionListener.
        JTextField tf = new JTextField("x");
        DefaultCellEditor editor = new DefaultCellEditor(tf);
        RecordingListener listener = new RecordingListener();
        editor.addCellEditorListener(listener);

        tf.postActionEvent();
        assertEquals(1, listener.stopped.size());
    }

    // --- isCellEditable clickCount gate -----------------------------------

    @Test
    @DisplayName("isCellEditable is true for a non-mouse event")
    void isCellEditableForNonMouseEvents() {
        DefaultCellEditor editor = new DefaultCellEditor(new JTextField());
        assertTrue(editor.isCellEditable(new EventObject("src")));
        // null event: JDK treats absent event as editable.
        assertTrue(editor.isCellEditable(null));
    }

    // --- Tree editor path (deferred, but must not throw) ------------------

    @Test
    @DisplayName("getTreeCellEditorComponent seeds the editor and returns the component")
    void treeCellEditorComponentSeedsTheEditor() {
        JTextField tf = new JTextField();
        DefaultCellEditor editor = new DefaultCellEditor(tf);
        vaadinx.awt.Component comp =
                editor.getTreeCellEditorComponent(null, "node", false, false, true, 0);
        assertSame(tf, comp);
        assertEquals("node", tf.getText());
    }
}
