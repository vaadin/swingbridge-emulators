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
package vaadinx;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JList;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPasswordField;
import vaadinx.swing.JProgressBar;
import vaadinx.swing.JSeparator;
import vaadinx.swing.JSlider;
import vaadinx.swing.JSpinner;
import vaadinx.swing.JTabbedPane;
import vaadinx.swing.JTable;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JToolBar;
import vaadinx.swing.JTree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Emulators that own their state answer every getter without reaching the peer
 * (D_emulator_owned_state): read from a bare thread — no UI, no session, no lock, what
 * a {@code SwingWorker.doInBackground()} body sees — on an attached component, the getters
 * return the right values and {@link EHelper#checkUIThread()}'s off-thread latch stays unarmed.
 * Every peer access goes through {@code getPeer()}, which arms it, so an unarmed latch is
 * proof that no getter touched the peer.
 */
class EmulatorOwnedStateTest extends AbstractKaribuTest {

    @BeforeEach
    @AfterEach
    void rearmLatch() {
        EHelper.resetUIThreadWarning();
    }

    /** Runs {@code reads} on a bare thread and returns what they answered. */
    private static List<Object> readOffUiThread(Callable<List<Object>> reads) {
        AtomicReference<Object> result = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(reads.call());
            } catch (Throwable e) {
                result.set(e);
            }
        }, "getter-probe");
        t.setDaemon(true);
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted waiting for the probe thread");
        }
        if (t.isAlive()) fail("the probe thread did not finish");
        if (result.get() instanceof Throwable e) throw new AssertionError("a getter threw off the UI thread", e);
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) result.get();
        return values;
    }

    private static void attach(vaadinx.awt.Component c) {
        UI.getCurrent().add(c.getPeer());
    }

    @Test
    @DisplayName("JSlider getters read the model and fields, never the peer")
    void jSlider() {
        JSlider slider = new JSlider(0, 10, 3);
        attach(slider);
        slider.setInverted(true);
        slider.setPaintTicks(true);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(slider.getValue(), slider.getMinimum(),
                slider.getMaximum(), slider.getExtent(), slider.getInverted(), slider.getPaintTicks(),
                slider.getPaintTrack(), slider.getOrientation()));

        assertEquals(List.of(3, 0, 10, 0, true, true, true, JSlider.HORIZONTAL), values);
        assertFalse(EHelper.uiThreadWarned(), "a JSlider getter reached the peer");
    }

    @Test
    @DisplayName("JProgressBar getters read the model and fields, never the peer")
    void jProgressBar() {
        JProgressBar bar = new JProgressBar(0, 4);
        attach(bar);
        bar.setValue(1);
        bar.setIndeterminate(true);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(bar.getValue(), bar.getPercentComplete(),
                bar.isIndeterminate(), bar.getString()));

        assertEquals(1, values.get(0));
        assertEquals(0.25, values.get(1));
        assertEquals(true, values.get(2));
        assertEquals(java.text.NumberFormat.getPercentInstance().format(0.25), values.get(3));
        assertFalse(EHelper.uiThreadWarned(), "a JProgressBar getter reached the peer");
    }

    @Test
    @DisplayName("JSpinner getters read the model, never the peer")
    void jSpinner() {
        JSpinner spinner = new JSpinner(new javax.swing.SpinnerNumberModel(5, 0, 10, 1));
        attach(spinner);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(spinner.getValue(), spinner.getNextValue(),
                spinner.getPreviousValue()));

        assertEquals(List.of(5, 6, 4), values);
        assertFalse(EHelper.uiThreadWarned(), "a JSpinner getter reached the peer");
    }

    @Test
    @DisplayName("JComboBox getters read the model and fields, never the peer")
    void jComboBox() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b", "c"});
        attach(combo);
        combo.setSelectedIndex(1);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> {
            List<Object> v = new ArrayList<>();
            v.add(combo.getSelectedItem());
            v.add(combo.getSelectedIndex());
            v.add(combo.getItemCount());
            v.add(combo.getItemAt(2));
            v.add(combo.getSelectedObjects().length);
            v.add(combo.isEditable());
            v.add(combo.isPopupVisible());
            v.add(combo.getMaximumRowCount());
            return v;
        });

        assertEquals(List.of("b", 1, 3, "c", 1, false, false, 8), values);
        assertFalse(EHelper.uiThreadWarned(), "a JComboBox getter reached the peer");
    }

    @Test
    @DisplayName("JTree expansion and row getters read the emulator's expandedState, never the peer")
    void jTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode colors = new DefaultMutableTreeNode("colors");
        colors.add(new DefaultMutableTreeNode("blue"));
        root.add(colors);
        root.add(new DefaultMutableTreeNode("sports"));
        JTree tree = new JTree(new DefaultTreeModel(root));
        attach(tree);
        TreePath colorsPath = new TreePath(new Object[]{root, colors});
        tree.expandPath(colorsPath);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(tree.getRowCount(), tree.isExpanded(colorsPath),
                tree.isExpanded(1), tree.hasBeenExpanded(colorsPath), tree.getRowForPath(colorsPath),
                tree.getPathForRow(2).getLastPathComponent().toString(), tree.isVisible(colorsPath),
                tree.getRowForLocation(0, 0), tree.getClosestRowForLocation(0, 0),
                String.valueOf(tree.getPathForLocation(0, 0))));

        assertEquals(List.of(4, true, true, true, 1, "blue", true, -1, -1, "null"), values);
        assertFalse(EHelper.uiThreadWarned(), "a JTree getter reached the peer");
    }

    @Test
    @DisplayName("JList getters read the list model and the selection model, never the peer")
    void jList() {
        JList<String> list = new JList<>(new String[] {"a", "b", "c", "d"});
        attach(list);
        list.setSelectedIndices(new int[] {1, 3});
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> {
            List<Object> v = new ArrayList<>();
            v.add(list.getModel().getSize());
            v.add(list.getSelectedIndex());
            v.add(java.util.Arrays.toString(list.getSelectedIndices()));
            v.add(list.getSelectedValue());
            v.add(list.getSelectedValuesList());
            v.add(list.getMinSelectionIndex());
            v.add(list.getMaxSelectionIndex());
            v.add(list.isSelectedIndex(3));
            v.add(list.isSelectionEmpty());
            v.add(list.getAnchorSelectionIndex());
            v.add(list.getLeadSelectionIndex());
            v.add(list.getSelectionMode());
            v.add(list.getValueIsAdjusting());
            v.add(list.getCellBounds(0, 0));
            v.add(list.locationToIndex(new java.awt.Point(0, 0)));
            return v;
        });

        List<Object> expected = new ArrayList<>(List.of(4, 1, "[1, 3]", "b", List.of("b", "d"), 1, 3, true, false, 3, 3,
                javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, false));
        expected.add(null);
        expected.add(-1);
        assertEquals(expected, values);
        assertFalse(EHelper.uiThreadWarned(), "a JList getter reached the peer");
    }

    @Test
    @DisplayName("JTable getters read the models, the sorter and the column model, never the peer")
    void jTable() {
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(
                new Object[][] {{"c", 3}, {"a", 1}, {"b", 2}}, new Object[] {"Name", "Qty"});
        JTable table = new JTable(model);
        attach(table);
        table.setAutoCreateRowSorter(true);
        table.getRowSorter().toggleSortOrder(0);
        table.moveColumn(0, 1);
        table.setRowSelectionInterval(0, 0);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> {
            List<Object> v = new ArrayList<>();
            v.add(table.getRowCount());
            v.add(table.getColumnCount());
            v.add(table.getColumnName(0));
            v.add(table.getColumnClass(1).getSimpleName());
            v.add(table.getValueAt(0, 1));
            v.add(table.isCellEditable(0, 1));
            v.add(table.convertRowIndexToModel(0));
            v.add(table.convertRowIndexToView(0));
            v.add(table.convertColumnIndexToModel(0));
            v.add(table.convertColumnIndexToView(0));
            v.add(table.getSelectedRow());
            v.add(table.getCellRect(0, 0, true).width);
            v.add(table.getPreferredScrollableViewportSize().width);
            v.add(table.rowAtPoint(new java.awt.Point(0, 0)));
            v.add(table.columnAtPoint(new java.awt.Point(0, 0)));
            return v;
        });

        assertEquals(List.of(3, 2, "Qty", "Object", "a", true, 1, 2, 1, 1, 0, 75, 450, -1, -1), values);
        assertFalse(EHelper.uiThreadWarned(), "a JTable getter reached the peer");
    }

    @Test
    @DisplayName("JTextArea getters read fields and the Document, never the peer")
    void jTextArea() {
        JTextArea area = new JTextArea("two\nlines", 3, 20);
        attach(area);
        area.setTabSize(4);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(area.getRows(), area.getColumns(),
                area.getTabSize(), area.getLineWrap(), area.getWrapStyleWord(),
                area.getScrollableTracksViewportWidth(), area.getLineCount(), area.getText()));

        assertEquals(List.of(3, 20, 4, true, true, true, 2, "two\nlines"), values);
        assertFalse(EHelper.uiThreadWarned(), "a JTextArea getter reached the peer");
    }

    @Test
    @DisplayName("JPasswordField getters read the field and the Document, never the peer")
    void jPasswordField() {
        // A login SwingWorker reads the password in doInBackground().
        JPasswordField field = new JPasswordField("s3cret");
        attach(field);
        field.setEchoChar('#');
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(new String(field.getPassword()),
                field.getEchoChar(), field.echoCharIsSet(), field.getColumns()));

        assertEquals(List.of("s3cret", '#', true, 0), values);
        assertFalse(EHelper.uiThreadWarned(), "a JPasswordField getter reached the peer");
    }

    @Test
    @DisplayName("JTabbedPane getters read its pages and its selection model, never the peer")
    void jTabbedPane() {
        JTabbedPane pane = new JTabbedPane(JTabbedPane.BOTTOM, JTabbedPane.SCROLL_TAB_LAYOUT);
        attach(pane);
        JPanel content = new JPanel();
        JPanel header = new JPanel();
        pane.addTab("One", new JPanel());
        pane.addTab("Two", null, content, "tip");
        pane.setTabComponentAt(0, header);
        pane.setEnabledAt(0, false);
        pane.setMnemonicAt(1, java.awt.event.KeyEvent.VK_W);
        pane.setSelectedIndex(1);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> {
            List<Object> v = new ArrayList<>();
            v.add(pane.getTabCount());
            v.add(pane.getSelectedIndex());
            v.add(pane.getSelectedComponent() == content);
            v.add(pane.getTitleAt(1));
            v.add(pane.getToolTipTextAt(1));
            v.add(pane.isEnabledAt(0));
            v.add(pane.getTabComponentAt(0) == header);
            v.add(pane.indexOfTab("Two"));
            v.add(pane.indexOfComponent(content));
            v.add(pane.indexOfTabComponent(header));
            v.add(pane.getMnemonicAt(1));
            v.add(pane.getDisplayedMnemonicIndexAt(1));
            v.add(pane.getTabPlacement());
            v.add(pane.getTabLayoutPolicy());
            v.add(pane.getTabRunCount());
            return v;
        });

        assertEquals(List.of(2, 1, true, "Two", "tip", false, true, 1, 1, 0,
                java.awt.event.KeyEvent.VK_W, 1, JTabbedPane.BOTTOM, JTabbedPane.SCROLL_TAB_LAYOUT, 1), values);
        assertFalse(EHelper.uiThreadWarned(), "a JTabbedPane getter reached the peer");
    }

    @Test
    @DisplayName("AWT Scrollbar getters read its fields, never the peer")
    @SuppressWarnings("deprecation") // the AWT-1.0 names carry the bodies
    void awtScrollbar() {
        vaadinx.awt.Scrollbar bar = new vaadinx.awt.Scrollbar(vaadinx.awt.Scrollbar.HORIZONTAL, 30, 5, 10, 60);
        attach(bar);
        bar.setUnitIncrement(4);
        bar.setBlockIncrement(20);
        bar.setValueIsAdjusting(true);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(bar.getValue(), bar.getVisibleAmount(),
                bar.getVisible(), bar.getMinimum(), bar.getMaximum(), bar.getOrientation(),
                bar.getUnitIncrement(), bar.getLineIncrement(), bar.getBlockIncrement(),
                bar.getPageIncrement(), bar.getValueIsAdjusting()));

        assertEquals(List.of(30, 5, 5, 10, 60, vaadinx.awt.Scrollbar.HORIZONTAL, 4, 4, 20, 20, true), values);
        assertFalse(EHelper.uiThreadWarned(), "a Scrollbar getter reached the peer");
    }

    @Test
    @DisplayName("AWT Choice getters read its item vector and selection, never the peer")
    @SuppressWarnings("deprecation") // countItems carries the body
    void awtChoice() {
        vaadinx.awt.Choice choice = new vaadinx.awt.Choice();
        attach(choice);
        choice.add("a");
        choice.add("b");
        choice.add("c");
        choice.select(1);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(choice.getItemCount(), choice.countItems(),
                choice.getItem(2), choice.getSelectedIndex(), choice.getSelectedItem(),
                choice.getSelectedObjects().length));

        assertEquals(List.of(3, 3, "c", 1, "b", 1), values);
        assertFalse(EHelper.uiThreadWarned(), "a Choice getter reached the peer");
    }

    @Test
    @DisplayName("AWT List getters read its items and selection, never the peer")
    @SuppressWarnings("deprecation") // the AWT-1.0 names carry the bodies
    void awtList() {
        vaadinx.awt.List list = new vaadinx.awt.List(3, true);
        attach(list);
        list.add("a");
        list.add("b");
        list.add("c");
        list.select(0);
        list.select(2);
        list.makeVisible(2);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(list.getItemCount(), list.countItems(),
                list.getItem(1), java.util.Arrays.toString(list.getItems()), list.getSelectedIndex(),
                java.util.Arrays.toString(list.getSelectedIndexes()),
                java.util.Arrays.toString(list.getSelectedItems()), list.isIndexSelected(2),
                list.isMultipleMode(), list.getRows(), list.getVisibleIndex()));

        assertEquals(List.of(3, 3, "b", "[a, b, c]", -1, "[0, 2]", "[a, c]", true, true, 3, 2), values);
        assertFalse(EHelper.uiThreadWarned(), "a List getter reached the peer");
    }

    @Test
    @DisplayName("JTextComponent caret and selection getters read the caret, never the peer")
    void jTextComponentCaret() {
        // A SwingWorker that inserts at the caret reads it in doInBackground().
        vaadinx.swing.JTextField field = new vaadinx.swing.JTextField("hello world");
        attach(field);
        field.select(3, 8);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(field.getCaretPosition(), field.getSelectionStart(),
                field.getSelectionEnd(), field.getSelectedText(), field.getCaret().getDot(),
                field.getCaret().getMark()));

        assertEquals(List.of(8, 3, 8, "lo wo", 8, 3), values);
        assertFalse(EHelper.uiThreadWarned(), "a caret getter reached the peer");
    }

    @Test
    @DisplayName("JEditorPane content type and page read its fields and the document, never the peer")
    void jEditorPane() throws Exception {
        java.nio.file.Path page = java.nio.file.Files.createTempFile("owned-state", ".html");
        try {
            java.nio.file.Files.writeString(page, "<p>report</p>");
            vaadinx.swing.JEditorPane pane = new vaadinx.swing.JEditorPane(page.toUri().toURL());
            attach(pane);
            EHelper.resetUIThreadWarning();

            List<Object> values = readOffUiThread(() -> List.of(pane.getContentType(), pane.getPage(),
                    pane.getText().contains("report")));

            assertEquals(List.of("text/html", page.toUri().toURL(), true), values);
            assertFalse(EHelper.uiThreadWarned(), "a JEditorPane getter reached the peer");
        } finally {
            java.nio.file.Files.deleteIfExists(page);
        }
    }

    @Test
    @DisplayName("JColorChooser getters read the model and its fields, never the peer")
    void jColorChooser() {
        vaadinx.swing.JColorChooser chooser = new vaadinx.swing.JColorChooser(java.awt.Color.RED);
        attach(chooser);
        chooser.setDragEnabled(true);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(chooser.getColor(),
                chooser.getSelectionModel().getSelectedColor(), chooser.getChooserPanels().length,
                chooser.getDragEnabled(), String.valueOf(chooser.getPreviewPanel())));

        assertEquals(List.of(java.awt.Color.RED, java.awt.Color.RED, 5, true, "null"), values);
        assertFalse(EHelper.uiThreadWarned(), "a JColorChooser getter reached the peer");
    }

    @Test
    @DisplayName("Dialog getters read its fields, never the peer")
    void dialog() {
        // A worker deciding whether to reuse an open dialog reads its state in doInBackground().
        vaadinx.swing.JDialog dialog = new vaadinx.swing.JDialog((vaadinx.awt.Frame) null, "Edit", true);
        dialog.setResizable(false);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(dialog.isResizable(), dialog.getTitle(),
                dialog.isModal(), dialog.getModalityType(), dialog.isUndecorated()));

        assertEquals(List.of(false, "Edit", true, vaadinx.awt.Dialog.ModalityType.APPLICATION_MODAL, false), values);
        assertFalse(EHelper.uiThreadWarned(), "a Dialog getter reached the peer");
    }

    @Test
    @DisplayName("AWT Label getters read its fields, never the peer")
    void awtLabel() {
        vaadinx.awt.Label label = new vaadinx.awt.Label(null, vaadinx.awt.Label.CENTER);
        attach(label);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> {
            List<Object> v = new ArrayList<>();
            v.add(label.getText());
            v.add(label.getAlignment());
            return v;
        });

        List<Object> expected = new ArrayList<>();
        expected.add(null);
        expected.add(vaadinx.awt.Label.CENTER);
        assertEquals(expected, values);
        assertFalse(EHelper.uiThreadWarned(), "a Label getter reached the peer");
    }

    @Test
    @DisplayName("JToolBar and JSeparator getters read their fields, never the peer")
    void jToolBarAndJSeparator() {
        JToolBar toolBar = new JToolBar(JToolBar.VERTICAL);
        attach(toolBar);
        toolBar.setMargin(new java.awt.Insets(1, 2, 3, 4));
        toolBar.addSeparator();
        JSeparator separator = new JSeparator(JSeparator.VERTICAL);
        attach(separator);
        EHelper.resetUIThreadWarning();

        List<Object> values = readOffUiThread(() -> List.of(toolBar.getOrientation(), toolBar.getMargin(),
                toolBar.getComponentAtIndex(0) instanceof JToolBar.Separator, toolBar.getComponentAtIndex(1) == null,
                ((JSeparator) toolBar.getComponent(0)).getOrientation(), separator.getOrientation()));

        assertEquals(List.of(JToolBar.VERTICAL, new java.awt.Insets(1, 2, 3, 4), true, true,
                JSeparator.HORIZONTAL, JSeparator.VERTICAL), values);
        assertFalse(EHelper.uiThreadWarned(), "a JToolBar or JSeparator getter reached the peer");
    }

    @Test
    @DisplayName("JComboBox.isPopupVisible mirrors the peer's opened property, whoever opened it")
    void jComboBoxPopupMirrorsThePeer() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b"});
        attach(combo);
        ComboBox<?> peer = (ComboBox<?>) combo.getPeer();

        // What the browser's opened-changed synchronization writes.
        peer.getElement().setProperty("opened", true);
        assertTrue(combo.isPopupVisible());
        peer.getElement().setProperty("opened", false);
        assertFalse(combo.isPopupVisible());

        combo.setPopupVisible(true);
        assertTrue(combo.isPopupVisible());
        assertTrue(peer.isOpened());
    }
}
