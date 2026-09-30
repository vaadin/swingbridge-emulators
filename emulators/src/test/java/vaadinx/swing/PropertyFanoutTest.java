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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.swing.text.PeerCaret;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.Component;
import vaadinx.swing.text.JTextComponent;

import java.awt.Adjustable;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.DefaultButtonModel;
import javax.swing.DefaultDesktopManager;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileView;
import javax.swing.table.TableRowSorter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * D_property_fanout_audit's sweep, pinned: each test drives a setter whose JDK body fires <b>no</b>
 * bound property and asserts the emulator stays as silent. An invented
 * {@code firePropertyChange} is the same class of bug as a dropped one — a migrated
 * app's {@code PropertyChangeListener} runs where it never ran on the desktop — and
 * it is invisible to the compiler, to {@code WarnInventoryTest}, and to any test that
 * only checks the value round-trips. Fourteen of the sites removed in D_property_fanout_audit had
 * no test at all, which is why the absences are asserted rather than merely
 * deleted.
 *
 * <p>The pins for properties with a natural per-component home live there instead
 * ({@code ComponentVisibilityEnabledTest} for {@code enabled} at the AWT level,
 * {@code JComponentTest} for its relocation and for the tooltip's client-property
 * routing, {@code JToolBarTest} for {@code rollover}, {@code JInternalFrameTest} for the
 * {@code iconable}-not-{@code iconifiable} name, {@code DialogTest} / {@code JDialogTest} for {@code modal},
 * {@code JListTest} for {@code selectionMode}, {@code JMenuItemTest} for {@code actionCommand},
 * {@code JPasswordFieldTest} for {@code echoChar}). This file holds the rest.
 *
 * <p>D_owed_events's sweep is pinned in the second half, and asserts the mirror-image
 * property: a setter whose JDK body <em>does</em> fire, and whose emulator was silent.
 * The tests there are weighted toward the <b>values</b> an event carries rather
 * than its mere arrival, because that is where the JDK is unguessable — a
 * sentinel {@code -1}, a deliberate {@code (null, null)}, an uppercased char, one setter
 * firing two names. A missing event fails a count assertion; a wrongly-typed or
 * wrongly-valued one passes every count and still breaks a listener reading
 * {@code getOldValue()}.
 */
class PropertyFanoutTest extends AbstractKaribuTest {

    /** Every PCE the component fires, whatever the name. */
    private static List<PropertyChangeEvent> record(Component c) {
        List<PropertyChangeEvent> events = new ArrayList<>();
        c.addPropertyChangeListener(events::add);
        return events;
    }

    private static List<PropertyChangeEvent> named(List<PropertyChangeEvent> events, String name) {
        return events.stream().filter(e -> name.equals(e.getPropertyName())).toList();
    }

    /** The property names of {@code events}, in delivery order — the cascade-ordering assertions. */
    private static List<String> names(List<PropertyChangeEvent> events) {
        return events.stream().map(PropertyChangeEvent::getPropertyName).toList();
    }

    /** A bare Div-peered AWT component, for the java.awt.Component-level pins. */
    private static Component bareComponent() {
        return new Component(new Div()) {
        };
    }

    /** The one-row, one-column table the JTable pins share. */
    private static JTable oneCellTable() {
        return new JTable(new Object[][]{{"x"}}, new Object[]{"c"});
    }

    // --- java.awt.Component ----------------------------------------------

    @Test
    @DisplayName("setCursor fires no bound property")
    void setCursorFiresNoBoundProperty() {
        // java.awt.Component.setCursor assigns the field and calls
        // updateCursorImmediately(). "cursor" is not a bound property anywhere
        // in java.awt or javax.swing.
        Component c = bareComponent();
        List<PropertyChangeEvent> events = record(c);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        assertEquals(Cursor.HAND_CURSOR, c.getCursor().getType());
        assertTrue(named(events, "cursor").isEmpty());
    }

    // --- JTextField -------------------------------------------------------

    @Test
    @DisplayName("setColumns fires no bound property")
    void setColumnsFiresNoBoundProperty() {
        // JTextField.setColumns assigns and calls invalidate() — a layout
        // hint, not a bound property. Same for JTextArea.
        JTextField tf = new JTextField();
        List<PropertyChangeEvent> events = record(tf);
        tf.setColumns(12);
        assertEquals(12, tf.getColumns());
        assertTrue(named(events, "columns").isEmpty());
    }

    // --- JFormattedTextField ---------------------------------------------

    @Test
    @DisplayName("setFocusLostBehavior fires no bound property")
    void setFocusLostBehaviorFiresNoBoundProperty() {
        // The JDK setter validates the argument, assigns, returns.
        JFormattedTextField f = new JFormattedTextField();
        List<PropertyChangeEvent> events = record(f);
        f.setFocusLostBehavior(JFormattedTextField.PERSIST);
        assertEquals(JFormattedTextField.PERSIST, f.getFocusLostBehavior());
        assertTrue(named(events, "focusLostBehavior").isEmpty());
    }

    // --- JProgressBar: the sharp case ------------------------------------

    @Test
    @DisplayName("JProgressBar minimum maximum and model fire no bound property unlike JSlider")
    void progressBarRangeAndModelFireNothing() {
        // JSlider.setMinimum / setMaximum / setModel all genuinely fire.
        // JProgressBar's minimum/maximum are one-liners delegating to the
        // model, and its setModel fires only ACCESSIBLE_VALUE_PROPERTY — on
        // the AccessibleContext's own listener list, which
        // addPropertyChangeListener on the component never reaches. Reasoning
        // by family gets this backwards, which is why the audit unit is the
        // (class, property) pair and not the property name.
        JProgressBar pb = new JProgressBar();
        List<PropertyChangeEvent> events = record(pb);
        pb.setMinimum(5);
        pb.setMaximum(50);
        pb.setModel(new DefaultBoundedRangeModel(10, 0, 5, 50));
        assertEquals(5, pb.getMinimum());
        assertEquals(50, pb.getMaximum());
        assertTrue(named(events, "minimum").isEmpty());
        assertTrue(named(events, "maximum").isEmpty());
        assertTrue(named(events, "model").isEmpty());
    }

    // --- dragEnabled: three classes, all silent in the JDK ----------------

    @Test
    @DisplayName("setDragEnabled fires no bound property on JList JTable and JTree")
    void dragEnabledFiresNothingOnAnyOfTheThree() {
        // All three JDK setters are a headless/security check plus an
        // assignment. dragEnabled is not a bound property on any of them.
        JList<String> list = new JList<>(new String[]{"a", "b"});
        List<PropertyChangeEvent> listEvents = record(list);
        list.setDragEnabled(true);
        assertTrue(list.getDragEnabled());
        assertTrue(named(listEvents, "dragEnabled").isEmpty());

        JTable table = oneCellTable();
        List<PropertyChangeEvent> tableEvents = record(table);
        table.setDragEnabled(true);
        assertTrue(table.getDragEnabled());
        assertTrue(named(tableEvents, "dragEnabled").isEmpty());

        JTree tree = new JTree();
        List<PropertyChangeEvent> treeEvents = record(tree);
        tree.setDragEnabled(true);
        assertTrue(tree.getDragEnabled());
        assertTrue(named(treeEvents, "dragEnabled").isEmpty());
    }

    // --- JTable ------------------------------------------------------------

    @Test
    @DisplayName("setIntercellSpacing splits into rowMargin and columnMargin like the JDK")
    void setIntercellSpacingSplitsIntoTwoMargins() {
        // JTable.setIntercellSpacing has no field and no fire of its own: it
        // routes height to setRowMargin (which *does* fire "rowMargin") and
        // width to the column model's columnMargin (whose notification is a
        // COLUMN_MARGIN_CHANGED ChangeEvent). getIntercellSpacing recomposes
        // the Dimension from the two, so there is nothing to round-trip
        // through a shadow field.
        JTable table = oneCellTable();
        List<PropertyChangeEvent> events = record(table);

        table.setIntercellSpacing(new Dimension(4, 7));

        assertEquals(new Dimension(4, 7), table.getIntercellSpacing());
        assertEquals(7, table.getRowMargin());
        assertEquals(4, table.getColumnModel().getColumnMargin());
        assertTrue(named(events, "intercellSpacing").isEmpty());
        assertEquals(1, named(events, "rowMargin").size());
    }

    @Test
    @DisplayName("setPreferredScrollableViewportSize fires no bound property")
    void setPreferredScrollableViewportSizeFiresNothing() {
        // A bare field assignment in the JDK.
        JTable table = oneCellTable();
        List<PropertyChangeEvent> events = record(table);
        table.setPreferredScrollableViewportSize(new Dimension(200, 100));
        assertEquals(new Dimension(200, 100), table.getPreferredScrollableViewportSize());
        assertTrue(named(events, "preferredScrollableViewportSize").isEmpty());
    }

    @Test
    @DisplayName("JTable setSelectionMode fires no bound property and clears the selection")
    void tableSetSelectionModeFiresNothingAndClears() {
        // The JDK clears the selection first, then delegates to both selection
        // models; their ListSelectionEvents are the whole notification.
        JTable table = new JTable(new Object[][]{{"x"}, {"y"}}, new Object[]{"c"});
        table.setRowSelectionInterval(0, 0);
        List<PropertyChangeEvent> events = record(table);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        assertEquals(-1, table.getSelectedRow());
        assertTrue(named(events, "selectionMode").isEmpty());
    }

    // --- JViewport --------------------------------------------------------

    @Test
    @DisplayName("setExtentSize fires no bound property")
    void setExtentSizeFiresNoBoundProperty() {
        // JViewport.setExtentSize resizes and calls fireStateChanged();
        // "extentSize" is not a bound property. The ChangeListener list is the
        // JDK's channel, and it stays silent here for the reason
        // JViewport.addChangeListener documents.
        JViewport vp = new JViewport();
        List<PropertyChangeEvent> events = record(vp);
        vp.setExtentSize(new Dimension(30, 40));
        assertEquals(new Dimension(30, 40), vp.getExtentSize());
        assertTrue(named(events, "extentSize").isEmpty());
    }

    // =====================================================================
    // D_owed_events — the JDK fires and the emulator used to not. Values, not just
    // arrival: see the class javadoc.
    // =====================================================================

    // --- JTextComponent: nine bare stubs, now state + notification --------

    @Test
    @DisplayName("the JTextComponent properties round-trip and each fires once")
    void textComponentPropertiesRoundTripAndFireOnce() {
        // Every one of these JDK setters is a plain store-then-fire, and every
        // one of them was a generated one-liner that WARNed and returned —
        // dropping the state and the notification along with the effect, which
        // R_decline_effect_only does not permit.
        JTextField tf = new JTextField();
        List<PropertyChangeEvent> events = record(tf);

        PeerCaret caret = new PeerCaret(null);
        Insets margin = new Insets(1, 2, 3, 4);
        tf.setCaret(caret);
        tf.setCaretColor(Color.RED);
        tf.setSelectionColor(Color.BLUE);
        tf.setSelectedTextColor(Color.GREEN);
        tf.setDisabledTextColor(Color.GRAY);
        tf.setMargin(margin);

        assertEquals(caret, tf.getCaret());
        assertEquals(Color.RED, tf.getCaretColor());
        assertEquals(Color.BLUE, tf.getSelectionColor());
        assertEquals(Color.GREEN, tf.getSelectedTextColor());
        assertEquals(Color.GRAY, tf.getDisabledTextColor());
        assertEquals(margin, tf.getMargin());

        for (String p : List.of("caret", "caretColor", "selectionColor", "selectedTextColor",
                "disabledTextColor", "margin")) {
            assertEquals(1, named(events, p).size(), "expected exactly one " + p + " event");
        }
    }

    @Test
    @DisplayName("a user-installed caret replaces the default one")
    void aUserInstalledCaretWins() {
        // The JDK's getCaret returns whatever setCaret stored (D_emulator_caret).
        JTextField tf = new JTextField();
        assertInstanceOf(vaadinx.swing.text.DefaultCaret.class, tf.getCaret());

        vaadinx.swing.text.DefaultCaret mine = new vaadinx.swing.text.DefaultCaret();
        tf.setCaret(mine);
        assertSame(mine, tf.getCaret());
    }

    @Test
    @DisplayName("setCaretColor pushes an inherited CSS declaration and null removes it")
    void setCaretColorPushesAnInheritedDeclaration() {
        // The one colour of the four whose effect is deliverable: caret-color
        // is inherited, so a declaration on the host reaches the <input> inside
        // Vaadin's shadow root with no shadow-part rule.
        JTextField tf = new JTextField();
        tf.setCaretColor(Color.RED);
        assertEquals("rgb(255,0,0)", tf.getPeer().getElement().getStyle().get("caret-color"));

        tf.setCaretColor(null);
        assertNull(tf.getPeer().getElement().getStyle().get("caret-color"));
    }

    @Test
    @DisplayName("setFocusAccelerator fires two names, both carrying the uppercased Character")
    void setFocusAcceleratorFiresTwoNames() {
        // The JDK fires FOCUS_ACCELERATOR_KEY ("focusAcceleratorKey") *and*
        // "focusAccelerator", its own source comment naming the compatibility
        // bug (4341002) behind the pair. Both carry the uppercased char, so a
        // listener never sees the argument as passed.
        //
        // The Character assertion is the load-bearing one: char widens to int,
        // so a slip in overload resolution would box an Integer here and no
        // compiler would object.
        JTextField tf = new JTextField();
        List<PropertyChangeEvent> events = record(tf);

        tf.setFocusAccelerator('q');

        assertEquals('Q', tf.getFocusAccelerator());
        List<PropertyChangeEvent> keyed = named(events, JTextComponent.FOCUS_ACCELERATOR_KEY);
        List<PropertyChangeEvent> plain = named(events, "focusAccelerator");
        assertEquals(1, keyed.size());
        assertEquals(1, plain.size());
        List<PropertyChangeEvent> both = new ArrayList<>(keyed);
        both.addAll(plain);
        for (PropertyChangeEvent e : both) {
            assertEquals(Character.valueOf('Q'), e.getNewValue(),
                    "uppercased, and a Character not an Integer");
            assertEquals(Character.valueOf((char) 0), e.getOldValue());
        }
    }

    // --- JComponent / Component: flags that were dropped whole ------------

    @Test
    @DisplayName("inheritsPopupMenu and verifyInputWhenFocusTarget round-trip and fire")
    void inheritsPopupMenuAndVerifyInputRoundTripAndFire() {
        // Both getters used to hardcode the Swing default and contradict their
        // own setter. The effects stay unmodelled; the state does not.
        JButton jb = new JButton();
        List<PropertyChangeEvent> events = record(jb);

        assertFalse(jb.getInheritsPopupMenu());         // Swing default
        assertTrue(jb.getVerifyInputWhenFocusTarget()); // Swing default

        jb.setInheritsPopupMenu(true);
        jb.setVerifyInputWhenFocusTarget(false);

        assertTrue(jb.getInheritsPopupMenu());
        assertFalse(jb.getVerifyInputWhenFocusTarget());
        assertEquals(1, named(events, "inheritsPopupMenu").size());
        assertEquals(1, named(events, "verifyInputWhenFocusTarget").size());
    }

    @Test
    @DisplayName("setFocusTraversalKeysEnabled round-trips and fires at the AWT level")
    void setFocusTraversalKeysEnabledRoundTripsAndFires() {
        // AWT's default is true; Tab is not intercepted either way (D_focus_managers).
        Component c = bareComponent();
        List<PropertyChangeEvent> events = record(c);

        assertTrue(c.getFocusTraversalKeysEnabled());
        c.setFocusTraversalKeysEnabled(false);

        assertFalse(c.getFocusTraversalKeysEnabled());
        assertEquals(1, named(events, "focusTraversalKeysEnabled").size());
    }

    @Test
    @DisplayName("addNotify and removeNotify fire the ancestor bound property")
    void addAndRemoveNotifyFireTheAncestorProperty() {
        // JComponent fires *both*: the AncestorEvent family and an "ancestor"
        // bound property alongside it. With no emulator parent the pair is
        // (null, null), which PropertyChangeSupport lets through — the JDK
        // relies on that.
        JButton jb = new JButton("Go");
        List<PropertyChangeEvent> events = record(jb);

        jb.addNotify();
        assertNull(assertSingle(named(events, "ancestor")).getOldValue());

        jb.removeNotify();
        assertEquals(2, named(events, "ancestor").size());
    }

    // --- The value-shape oddities, one test each ---------------------------

    @Test
    @DisplayName("setMnemonicAt fires with both values null, on purpose")
    void setMnemonicAtFiresWithBothValuesNull() {
        // firePropertyChange("mnemonicAt", null, null) is literally the JDK's
        // call. The event always arrives (both-null is not deduped) and carries
        // no payload, so a listener has to re-read the pane.
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("One", new JPanel());
        List<PropertyChangeEvent> events = record(tp);

        tp.setMnemonicAt(0, 'O');

        PropertyChangeEvent fired = assertSingle(named(events, "mnemonicAt"));
        assertNull(fired.getOldValue());
        assertNull(fired.getNewValue());
    }

    @Test
    @DisplayName("setTitleAt fires indexForTitle with a -1 sentinel, guarded by reference identity")
    void setTitleAtFiresIndexForTitle() {
        // The event reports *which tab* changed, not what it changed to. And
        // the JDK's guard is `oldTitle != title` — reference inequality — so
        // re-setting the identical String reference is silent while an equal
        // but distinct one fires.
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("One", new JPanel());
        List<PropertyChangeEvent> events = record(tp);

        tp.setTitleAt(0, "Two");
        PropertyChangeEvent fired = assertSingle(named(events, "indexForTitle"));
        assertEquals(-1, fired.getOldValue());
        assertEquals(0, fired.getNewValue());

        // Same reference back in — silent.
        String same = tp.getTitleAt(0);
        tp.setTitleAt(0, same);
        assertEquals(1, named(events, "indexForTitle").size());

        // Equal but distinct reference — fires.
        tp.setTitleAt(0, new StringBuilder(same).toString());
        assertEquals(2, named(events, "indexForTitle").size());
    }

    @Test
    @DisplayName("insertTab fires indexForNullComponent only for a null component")
    void insertTabFiresIndexForNullComponentOnlyForNull() {
        // And it carries the *argument* index, not the adjusted insertion point
        // the JDK actually used.
        JTabbedPane tp = new JTabbedPane();
        List<PropertyChangeEvent> events = record(tp);

        tp.insertTab("real", null, new JPanel(), null, 0);
        assertTrue(named(events, "indexForNullComponent").isEmpty());

        tp.insertTab("null", null, null, null, 1);
        PropertyChangeEvent fired = assertSingle(named(events, "indexForNullComponent"));
        assertEquals(-1, fired.getOldValue());
        assertEquals(1, fired.getNewValue());
    }

    @Test
    @DisplayName("setRowSorter fires both rowSorter and sorter")
    void setRowSorterFiresBothNames() {
        // Two names for one change, the JDK's legacy alias alongside the
        // documented one.
        JTable table = oneCellTable();
        List<PropertyChangeEvent> events = record(table);

        TableRowSorter<javax.swing.table.TableModel> sorter = new TableRowSorter<>(table.getModel());
        table.setRowSorter(sorter);

        assertEquals(1, named(events, "rowSorter").size());
        assertEquals(sorter, assertSingle(named(events, "sorter")).getNewValue());
    }

    @Test
    @DisplayName("setCellSelectionEnabled fires from a field its own getter never reads")
    void setCellSelectionEnabledFiresFromItsOwnField() {
        // The JDK keeps a cellSelectionEnabled field purely to supply this
        // event's old value; getCellSelectionEnabled() derives its answer from
        // the two allowed-flags instead, so the two can drift. Reproduced as
        // the JDK has it.
        JTable table = oneCellTable();
        List<PropertyChangeEvent> events = record(table);

        table.setCellSelectionEnabled(true);

        PropertyChangeEvent fired = assertSingle(named(events, "cellSelectionEnabled"));
        assertEquals(false, fired.getOldValue());
        assertEquals(true, fired.getNewValue());
    }

    // --- Delegating setters that relied on the surrogate's own fire --------

    @Test
    @DisplayName("a surrogate-side fire does not reach the emulator, so the emulator fires too")
    void aSurrogateSideFireDoesNotReachTheEmulator() {
        // There is no PCE bridge between the layers: Component.changeSupport
        // and the surrogate's support are separate objects with separate
        // listener lists. These four setters delegated and relied on the
        // surrogate's fire, which a migrator holding the emulator never sees.
        JSplitPane sp = new JSplitPane();
        List<PropertyChangeEvent> spEvents = record(sp);
        sp.setOrientation(JSplitPane.VERTICAL_SPLIT);
        assertEquals(1, named(spEvents, JSplitPane.ORIENTATION_PROPERTY).size());

        JToolBar tb = new JToolBar();
        List<PropertyChangeEvent> tbEvents = record(tb);
        tb.setOrientation(SwingConstants.VERTICAL);
        tb.setMargin(new Insets(2, 2, 2, 2));
        assertEquals(1, named(tbEvents, "orientation").size());
        assertEquals(1, named(tbEvents, "margin").size());

        JList<String> list = new JList<>(new String[]{"a", "b"});
        List<PropertyChangeEvent> listEvents = record(list);
        list.setPrototypeCellValue("wide-enough");
        assertEquals(1, named(listEvents, "prototypeCellValue").size());
    }

    // --- Inert-but-stored objects -----------------------------------------

    @Test
    @DisplayName("inert installed objects round-trip through their own getters and fire")
    void inertInstalledObjectsRoundTripAndFire() {
        // Each of these drives nothing — the effect is declined — but R_decline_effect_only owes
        // the state and the notification, and returning the caller's own object
        // beats the null these getters used to answer.
        JComboBox<String> combo = new JComboBox<>(new String[]{"a", "b"});
        List<PropertyChangeEvent> comboEvents = record(combo);
        Object editor = new Object();
        combo.setEditor(editor);
        combo.setLightWeightPopupEnabled(false);
        combo.setPrototypeDisplayValue("b");
        assertEquals(editor, combo.getEditor());
        assertFalse(combo.isLightWeightPopupEnabled());
        assertEquals("b", combo.getPrototypeDisplayValue());
        assertEquals(1, named(comboEvents, "editor").size());
        assertEquals(1, named(comboEvents, "lightWeightPopupEnabled").size());
        assertEquals(1, named(comboEvents, "prototypeDisplayValue").size());

        JScrollPane sp = new JScrollPane();
        List<PropertyChangeEvent> spEvents = record(sp);
        JViewport header = new JViewport();
        sp.setColumnHeader(header);
        sp.setRowHeader(new JViewport());
        assertEquals(header, sp.getColumnHeader());
        assertEquals(1, named(spEvents, "columnHeader").size());
        assertEquals(1, named(spEvents, "rowHeader").size());
    }

    @Test
    @DisplayName("AbstractButton setModel stores an inert model, propagates enabled, and fires")
    void abstractButtonSetModelStoresPropagatesAndFires() {
        // The button's real state machine is the surrogate's (SD_sjbutton), so a
        // foreign ButtonModel drives nothing. But the JDK's
        // super.setEnabled(model.isEnabled()) propagation is cheap and
        // observable, so it runs.
        JButton jb = new JButton("Go");
        assertNull(jb.getModel());   // none installed — unchanged from before D_owed_events
        List<PropertyChangeEvent> events = record(jb);

        DefaultButtonModel model = new DefaultButtonModel();
        model.setEnabled(false);
        jb.setModel(model);

        assertEquals(model, jb.getModel());
        assertEquals(1, named(events, AbstractButton.MODEL_CHANGED_PROPERTY).size());
        assertFalse(jb.isEnabled(), "the JDK propagates the model's enabled state");
    }

    @Test
    @DisplayName("AbstractButton exposes the JDK's bound-property name constants")
    void abstractButtonExposesTheBoundPropertyNameConstants() {
        // Compile-level API, not decoration: migrated code writes
        // addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, l).
        // All 19 were absent before D_owed_events.
        assertEquals("model", AbstractButton.MODEL_CHANGED_PROPERTY);
        assertEquals("text", AbstractButton.TEXT_CHANGED_PROPERTY);
        assertEquals("disabledSelectedIcon", AbstractButton.DISABLED_SELECTED_ICON_CHANGED_PROPERTY);
        assertEquals("focusAcceleratorKey", JTextComponent.FOCUS_ACCELERATOR_KEY);
    }

    // --- JPopupMenu: the one "visible" bound property in the whole surface ---

    @Test
    @DisplayName("JPopupMenu setVisible fires the visible property in both directions")
    void popupMenuSetVisibleFiresTheVisibleProperty() {
        // JPopupMenu is the *only* class in java.awt + javax.swing that fires a
        // "visible" bound property. It fires with no gate of any kind — measured
        // on JDK 25, a bare popup with no invoker reports isVisible() true — so
        // the emulator keeps the state and the event even though the programmatic
        // *open* is a declined effect (R_decline_effect_only).
        JPopupMenu popup = new JPopupMenu();
        List<PropertyChangeEvent> events = record(popup);

        popup.setVisible(true);
        assertTrue(popup.isVisible());
        assertEquals(Boolean.TRUE, assertSingle(named(events, "visible")).getNewValue());

        events.clear();
        popup.setVisible(false);
        assertFalse(popup.isVisible());
        assertEquals(Boolean.FALSE, assertSingle(named(events, "visible")).getNewValue());
    }

    // --- D_reverse_fanout_rows: the rows D_owed_events's classifier could not see ----------------------
    // Eight emulator classes fired *nothing at all*, which is exactly what put
    // them outside a work-list indexed by fire sites. The pins below are again
    // weighted toward guard order and payload rather than arrival.

    @Test
    @DisplayName("JFileChooser setSelectedFile cascades the directory before its own event")
    void fileChooserSetSelectedFileCascadesTheDirectoryFirst() {
        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File("/start"));
        List<PropertyChangeEvent> events = record(fc);

        File picked = new File("/elsewhere/report.pdf");
        fc.setSelectedFile(picked);

        // The JDK reparents to the file's directory *before* firing its own
        // event, so the directory change arrives first.
        assertEquals(List.of("directoryChanged", "SelectedFileChangedProperty"), names(events));
        assertEquals(new File("/elsewhere"), fc.getCurrentDirectory());

        // The setter carries no changed-guard, and yet re-setting the same file
        // delivers nothing — because PropertyChangeSupport does its own equality
        // check before dispatch. Worth pinning: reading only the setter body says
        // this fires. It is also why the JDK fires (null, null) by hand where it
        // wants an unconditional event (D_window_fanout's setIconImage).
        events.clear();
        fc.setSelectedFile(picked);
        assertTrue(events.isEmpty(), "PropertyChangeSupport swallows an equal-valued change");
    }

    @Test
    @DisplayName("JFileChooser setSelectedFiles carries the argument, nulled when empty")
    void fileChooserSetSelectedFilesCarriesTheArgument() {
        JFileChooser fc = new JFileChooser();
        List<PropertyChangeEvent> events = record(fc);
        File[] two = {new File("/a/one.txt"), new File("/a/two.txt")};

        fc.setSelectedFiles(two);
        PropertyChangeEvent first = assertSingle(named(events, "SelectedFilesChangedProperty"));
        assertNull(first.getOldValue());
        // The JDK's event carries the caller's array, not the defensive clone it
        // stored — same identity, so this is reference equality on purpose.
        assertSame(two, first.getNewValue());
        assertEquals(two[0], fc.getSelectedFile());

        events.clear();
        fc.setSelectedFiles(new File[0]);
        PropertyChangeEvent cleared = assertSingle(named(events, "SelectedFilesChangedProperty"));
        // Empty in, null out: a File[0] here would be a payload real Swing never
        // sends. The getter still normalises to an empty array.
        assertNull(cleared.getNewValue());
        assertEquals(0, fc.getSelectedFiles().length);
        assertNull(fc.getSelectedFile());
    }

    @Test
    @DisplayName("JFileChooser setCurrentDirectory returns early on an equal directory")
    void fileChooserSetCurrentDirectoryReturnsEarlyOnEqual() {
        JFileChooser fc = new JFileChooser();
        fc.setCurrentDirectory(new File("/x"));
        List<PropertyChangeEvent> events = record(fc);

        // The JDK's own changed-guard, on File.equals — distinct instance, same
        // path, so the method returns before doing anything.
        fc.setCurrentDirectory(new File("/x"));
        assertTrue(named(events, "directoryChanged").isEmpty());
        assertEquals(new File("/x"), fc.getCurrentDirectory());

        fc.setCurrentDirectory(new File("/y"));
        assertEquals(1, named(events, "directoryChanged").size());
    }

    @Test
    @DisplayName("JFileChooser setDialogType guards before it validates")
    void fileChooserSetDialogTypeGuardsBeforeValidating() {
        JFileChooser fc = new JFileChooser();   // starts at OPEN_DIALOG
        List<PropertyChangeEvent> events = record(fc);

        // Guard first: re-setting the current type returns before the check, so a
        // label set beforehand survives showOpenDialog. Reversing guard and
        // validate would also throw where Swing does not.
        fc.setApproveButtonText("Run");
        events.clear();
        fc.setDialogType(JFileChooser.OPEN_DIALOG);
        assertEquals("Run", fc.getApproveButtonText());
        assertTrue(events.isEmpty());

        // A real change resets the approve text, cascading its own event first.
        fc.setDialogType(JFileChooser.SAVE_DIALOG);
        assertEquals(List.of("ApproveButtonTextChangedProperty", "DialogTypeChangedProperty"),
                names(events));
        assertNull(fc.getApproveButtonText());
    }

    @Test
    @DisplayName("JFileChooser approve-button guards on reference, but the listener cannot tell")
    void fileChooserApproveButtonGuardsOnReference() {
        JFileChooser fc = new JFileChooser();
        List<PropertyChangeEvent> events = record(fc);

        fc.setApproveButtonText("Go");
        // The JDK's guard is `==`, so a distinct-but-equal String gets *past the
        // setter* — and is then swallowed by PropertyChangeSupport's equals check
        // on the way out. The reference guard is real and unobservable: two layers
        // of filtering with different rules, only the looser one in the body.
        fc.setApproveButtonText(new StringBuilder("Go").toString());
        assertEquals(1, named(events, "ApproveButtonTextChangedProperty").size());
        assertEquals("Go", fc.getApproveButtonText());

        String same = "Tip";
        fc.setApproveButtonToolTipText(same);
        fc.setApproveButtonToolTipText(same);        // identical reference — silent
        assertEquals(1, named(events, "ApproveButtonToolTipTextChangedProperty").size());
    }

    @Test
    @DisplayName("JFileChooser mnemonic char overload stores the upper-cased key code")
    void fileChooserMnemonicCharOverloadUpperCases() {
        JFileChooser fc = new JFileChooser();
        List<PropertyChangeEvent> events = record(fc);
        fc.setApproveButtonMnemonic('r');
        // Never the char as passed: the JDK routes through the int overload after
        // upper-casing, so both the field and the payload are the key code.
        assertEquals('R', fc.getApproveButtonMnemonic());
        assertEquals((int) 'R',
                assertSingle(named(events, "ApproveButtonMnemonicChangedProperty")).getNewValue());
    }

    @Test
    @DisplayName("JFileChooser filter list events snapshot the list either side of the change")
    void fileChooserFilterListEventsSnapshotBothSides() {
        JFileChooser fc = new JFileChooser();
        FileNameExtensionFilter f1 = new FileNameExtensionFilter("text", "txt");
        FileNameExtensionFilter f2 = new FileNameExtensionFilter("pdf", "pdf");
        List<PropertyChangeEvent> events = record(fc);

        fc.addChoosableFileFilter(f1);
        // First filter added becomes the active one, cascading fileFilterChanged.
        assertEquals(List.of("ChoosableFileFilterChangedProperty", "fileFilterChanged"), names(events));
        PropertyChangeEvent added = assertSingle(named(events, "ChoosableFileFilterChangedProperty"));
        assertEquals(0, ((Object[]) added.getOldValue()).length);
        assertEquals(1, ((Object[]) added.getNewValue()).length);

        // Already choosable: the whole JDK body sits inside the guard.
        events.clear();
        fc.addChoosableFileFilter(f1);
        assertTrue(events.isEmpty());

        // A second filter does not steal the active slot (JDK's size()==1 half).
        fc.addChoosableFileFilter(f2);
        assertEquals(f1, fc.getFileFilter());
        assertTrue(named(events, "fileFilterChanged").isEmpty());

        events.clear();
        assertTrue(fc.removeChoosableFileFilter(f2));
        assertEquals(1, named(events, "ChoosableFileFilterChangedProperty").size());
        assertFalse(fc.removeChoosableFileFilter(f2), "already gone — no event");
    }

    @Test
    @DisplayName("JFileChooser setFileFilter prunes a selection the filter rejects")
    void fileChooserSetFileFilterPrunesTheSelection() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("/a/notes.txt"));
        List<PropertyChangeEvent> events = record(fc);

        fc.setFileFilter(new FileNameExtensionFilter("pdf", "pdf"));

        // The JDK clears a selection its new filter rejects, cascading first.
        assertNull(fc.getSelectedFile());
        assertEquals(List.of("SelectedFileChangedProperty", "fileFilterChanged"), names(events));
    }

    @Test
    @DisplayName("JFileChooser setFileSelectionMode guards before validating")
    void fileChooserSetFileSelectionModeGuardsBeforeValidating() {
        JFileChooser fc = new JFileChooser();   // FILES_ONLY
        List<PropertyChangeEvent> events = record(fc);

        // Guard first, so re-setting the current mode never reaches the check.
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        assertTrue(events.isEmpty());

        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        assertEquals(1, named(events, "fileSelectionChanged").size());
    }

    @Test
    @DisplayName("JFileChooser accessory, file view and control buttons keep state and event")
    void fileChooserAccessoryFileViewAndControlButtons() {
        JFileChooser fc = new JFileChooser();
        List<PropertyChangeEvent> events = record(fc);
        JPanel accessory = new JPanel();

        fc.setAccessory(accessory);
        assertEquals(accessory, fc.getAccessory(), "the value is state; only the render is declined");
        assertEquals(accessory, assertSingle(named(events, "AccessoryChangedProperty")).getNewValue());

        FileView view = new FileView() {
        };
        fc.setFileView(view);
        assertEquals(view, fc.getFileView());
        assertEquals(1, named(events, "fileViewChanged").size());

        assertTrue(fc.getControlButtonsAreShown(), "JDK default is true");
        fc.setControlButtonsAreShown(false);
        assertFalse(fc.getControlButtonsAreShown());
        assertEquals(1, named(events, "ControlButtonsAreShownChangedProperty").size());
        fc.setControlButtonsAreShown(false);         // guarded
        assertEquals(1, named(events, "ControlButtonsAreShownChangedProperty").size());

        assertTrue(fc.isFileHidingEnabled(), "JDK default is true");
        fc.setFileHidingEnabled(false);
        assertFalse(fc.isFileHidingEnabled());
        assertEquals(1, named(events, "FileHidingChanged").size());
    }

    // --- JScrollBar, JDesktopPane, JWindow ---------------------------------

    @Test
    @DisplayName("JScrollBar increments and orientation fire, and orientation is stored")
    void scrollBarIncrementsAndOrientationFire() {
        JScrollBar sb = new JScrollBar();
        List<PropertyChangeEvent> events = record(sb);

        sb.setUnitIncrement(16);
        sb.setBlockIncrement(64);
        assertEquals(16, assertSingle(named(events, "unitIncrement")).getNewValue());
        assertEquals(64, assertSingle(named(events, "blockIncrement")).getNewValue());

        // Stored now rather than refused: nothing renders a JScrollBar (D_viewport_scrollbar_shadows's
        // inert Div peer), so declining the write would only make the getter lie.
        events.clear();
        sb.setOrientation(Adjustable.HORIZONTAL);
        assertEquals(Adjustable.HORIZONTAL, sb.getOrientation());
        assertEquals(1, named(events, "orientation").size());

        assertThrows(IllegalArgumentException.class, () -> sb.setOrientation(99));
    }

    @Test
    @DisplayName("JDesktopPane dragMode and desktopManager fire while the effect is declined")
    void desktopPaneDragModeAndManagerFire() {
        JDesktopPane desktop = new JDesktopPane();
        List<PropertyChangeEvent> events = record(desktop);

        desktop.setDragMode(JDesktopPane.OUTLINE_DRAG_MODE);
        assertEquals(JDesktopPane.OUTLINE_DRAG_MODE, desktop.getDragMode());
        assertEquals(1, named(events, "dragMode").size());

        DefaultDesktopManager manager = new DefaultDesktopManager() {
        };
        desktop.setDesktopManager(manager);
        assertEquals(manager, desktop.getDesktopManager());
        assertEquals(manager, assertSingle(named(events, "desktopManager")).getNewValue());
    }

    // --- D_event_value_audit: the values an event carries, not just its arrival ------------

    @Test
    @DisplayName("setHideActionText guards, resyncs the Action text, and derives its old value")
    void setHideActionTextGuardsAndResyncs() {
        AbstractAction action = new AbstractAction("Save") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        JButton button = new JButton();
        button.setAction(action);
        assertEquals("Save", button.getText(), "Action.NAME drives the label");
        List<PropertyChangeEvent> events = record(button);

        button.setHideActionText(true);

        // The guard gates the resync, not just the event: hiding the action text
        // has to re-run the Action -> text sync or the label goes stale. The
        // stale comment claiming Action wiring was stubbed is what hid this.
        assertNull(button.getText());
        PropertyChangeEvent e = assertSingle(named(events, "hideActionText"));
        // The JDK derives the old value by negating the new one, which is exact
        // *because* the guard already established they differ.
        assertEquals(false, e.getOldValue());
        assertEquals(true, e.getNewValue());

        events.clear();
        button.setHideActionText(true);      // unchanged — the JDK returns early
        assertTrue(events.isEmpty());

        button.setHideActionText(false);
        assertEquals("Save", button.getText(), "and unhiding restores it");
    }

    @Test
    @DisplayName("JComponent setEnabled reports its getter, not the argument it was handed")
    void jComponentSetEnabledReportsItsGetter() {
        // The one row the value diff still flags, and deliberately so: the JDK
        // echoes its parameter, we read isEnabled() back. Identical for every
        // peer that can be disabled, and for the one that cannot it keeps the
        // event from announcing a state the getter denies — D_owed_events's
        // JPopupMenu.setVisible reasoning, applied to a value rather than a fire.
        JPanel panel = new JPanel();
        List<PropertyChangeEvent> events = record(panel);

        panel.setEnabled(false);

        assertFalse(panel.isEnabled());
        PropertyChangeEvent e = assertSingle(named(events, "enabled"));
        assertEquals(true, e.getOldValue());
        assertEquals(false, e.getNewValue());
        assertEquals(panel.isEnabled(), e.getNewValue(), "the payload agrees with the getter");
    }

    @Test
    @DisplayName("the primitive fire overloads box to the JDK's wrapper types")
    void primitiveFireOverloadsBoxToTheJdkWrappers() {
        // Where the JDK writes Integer.valueOf(x) / Boolean.valueOf(x) we pass the
        // primitive and let PropertyChangeSupport box. Same wrapper, so a
        // listener's cast holds — but only because int boxes to Integer and
        // boolean to Boolean. A widening slip would compile and break the cast,
        // which is why this is asserted on the payload's runtime class.
        JSlider slider = new JSlider();
        List<PropertyChangeEvent> sliderEvents = record(slider);
        slider.setMaximum(42);
        assertInstanceOf(Integer.class, assertSingle(named(sliderEvents, "maximum")).getNewValue());

        JTextField field = new JTextField();
        List<PropertyChangeEvent> fieldEvents = record(field);
        field.setEditable(false);
        assertInstanceOf(Boolean.class, assertSingle(named(fieldEvents, "editable")).getNewValue());
    }

    @Test
    @DisplayName("JWindow setTransferHandler fires, as JDialog's already did")
    void windowSetTransferHandlerFires() {
        // The calibration row of D_reverse_fanout_rows: a one-line gap on a method whose twin was
        // fixed in D_owed_events, invisible only because JWindow fired nothing at all.
        JWindow w = new JWindow();
        List<PropertyChangeEvent> events = record(w);
        TransferHandler handler = new TransferHandler() {
        };

        w.setTransferHandler(handler);

        assertEquals(handler, w.getTransferHandler());
        assertEquals(handler, assertSingle(named(events, "transferHandler")).getNewValue());
    }
}
