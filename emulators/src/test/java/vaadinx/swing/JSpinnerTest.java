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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.LongField;
import com.vaadin.swingbridge.surrogates.SJSpinner;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

/**
 * Emulator tests for the thin {@code JSpinner} shell over
 * {@code com.vaadin.swingbridge.surrogates.SJSpinner} (SD_sjspinner). The surrogate carries the
 * model / inner-field / R_swing_is_truth / R_callswing_envelope plumbing — coverage for that lives in
 * {@code com.vaadin.swingbridge.surrogates.SJSpinnerTest}. These tests focus on the emulator's
 * responsibilities: JDK-shape API delegation, ChangeEvent re-sourcing to
 * the emulator, independent PCE listener lists, and the hosted
 * end-to-end user-edit path.
 */
class JSpinnerTest extends AbstractKaribuTest {

    private static Component inner(JSpinner s) {
        return ((SJSpinner) s.getPeer()).getChildren().findFirst().get();
    }

    @Test
    @DisplayName("default ctor seeds SpinnerNumberModel with Integer value")
    void defaultCtorSeedsNumberModelWithIntegerValue() {
        // JDK default is new SpinnerNumberModel() — (0, null, null, 1).
        JSpinner s = new JSpinner();
        assertInstanceOf(SJSpinner.class, s.getPeer());
        assertInstanceOf(SpinnerNumberModel.class, s.getModel());
        assertEquals(0, s.getValue());
        assertInstanceOf(IntegerField.class, inner(s));
    }

    @Test
    @DisplayName("Integer-backed SpinnerNumberModel populates IntegerField bounds and value")
    void integerModelPopulatesIntegerFieldBoundsAndValue() {
        SpinnerNumberModel model = new SpinnerNumberModel(5, 0, 10, 1);
        JSpinner s = new JSpinner(model);
        assertSame(model, s.getModel());
        assertEquals(5, s.getValue());
        IntegerField f = assertInstanceOf(IntegerField.class, inner(s));
        assertEquals(0, f.getMin());
        assertEquals(10, f.getMax());
        assertEquals(1, f.getStep());
        assertEquals(5, f.getValue());
    }

    @Test
    @DisplayName("null min and max push Integer MIN_VALUE and MAX_VALUE to the peer")
    void nullBoundsPushIntegerExtremesToThePeer() {
        // SpinnerNumberModel allows null bounds (unbounded); IntegerField
        // has no unbounded sentinel — surrogate approximates with Integer range.
        JSpinner s = new JSpinner(new SpinnerNumberModel(0, null, null, 1));
        IntegerField f = assertInstanceOf(IntegerField.class, inner(s));
        assertEquals(Integer.MIN_VALUE, f.getMin());
        assertEquals(Integer.MAX_VALUE, f.getMax());
    }

    @Test
    @DisplayName("setValue writes through to the inner field")
    void setValueWritesThroughToTheInnerField() {
        JSpinner s = new JSpinner();
        s.setValue(42);
        assertEquals(42, s.getValue());
        assertEquals(42, ((IntegerField) inner(s)).getValue());
    }

    @Test
    @DisplayName("setValue does not cause infinite feedback loop")
    void setValueDoesNotCauseInfiniteFeedbackLoop() {
        // R_swing_is_truth preventPeerEvents regression — emulator fires once per change.
        JSpinner s = new JSpinner();
        Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        s.setValue(10);
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("ChangeEvent source is the JSpinner emulator, not the SJSpinner surrogate")
    void changeEventSourceIsTheEmulator() {
        // The whole point of the emulator's ChangeListener bridge: user
        // casts `(JSpinner) e.getSource()` and must see the emulator.
        JSpinner s = new JSpinner();
        List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);
        s.setValue(3);
        assertSame(s, assertSingle(events).getSource());
    }

    @Test
    @DisplayName("peer-originated value change mirrors into the model")
    void peerOriginatedValueChangeMirrorsIntoTheModel() {
        // R_swing_is_truth peer → model: editing the inner IntegerField simulates a
        // browser edit; the emulator sees the new value through the model.
        JSpinner s = new JSpinner();
        ((IntegerField) inner(s)).setValue(17);
        assertEquals(17, s.getValue());
    }

    @Test
    @DisplayName("getNextValue and getPreviousValue delegate to the model")
    void nextAndPreviousValueDelegateToTheModel() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(5, 0, 10, 2));
        assertEquals(7, s.getNextValue());
        assertEquals(3, s.getPreviousValue());
    }

    @Test
    @DisplayName("setModel replaces the model and re-subscribes the ChangeListener")
    void setModelReplacesAndResubscribes() {
        JSpinner s = new JSpinner();
        SpinnerModel oldModel = s.getModel();
        SpinnerNumberModel newModel = new SpinnerNumberModel(8, 0, 20, 4);
        s.setModel(newModel);
        assertSame(newModel, s.getModel());
        assertNotSame(oldModel, newModel);

        Counter hits = new Counter();
        s.addChangeListener(e -> hits.inc());
        ((SpinnerNumberModel) oldModel).setValue(99);  // detached — must not fire
        hits.assertEquals(0);
        newModel.setValue(12);  // attached — must fire
        hits.assertEquals(1);
    }

    @Test
    @DisplayName("setModel fires the emulator-level model PropertyChangeEvent")
    void setModelFiresTheEmulatorLevelModelPce() {
        // Surrogate fires its own "model" PCE too, but the two listener
        // lists are independent. A listener added via emulator.addPropertyChangeListener
        // only hears the emulator's fire.
        JSpinner s = new JSpinner();
        List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener("model", events::add);
        s.setModel(new SpinnerNumberModel(1, 0, 2, 1));
        assertEquals(1, events.size());
    }

    // ---- Post-SD_sjspinner: all SpinnerModel variants are now supported ----

    @Test
    @DisplayName("SpinnerListModel installs a Select-backed peer")
    void listModelInstallsASelectBackedPeer() {
        // Was UnsupportedOperationException pre-SD_sjspinner (D_emulator_surrogate_split boundary);
        // surrogate now covers it.
        JSpinner s = new JSpinner(new SpinnerListModel(List.of("a", "b", "c")));
        assertInstanceOf(Select.class, inner(s));
        assertEquals("a", s.getValue());
    }

    @Test
    @DisplayName("SpinnerDateModel installs a DatePicker-backed peer")
    void dateModelInstallsADatePickerBackedPeer() {
        JSpinner s = new JSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        assertInstanceOf(DatePicker.class, inner(s));
    }

    @Test
    @DisplayName("SpinnerNumberModel with Double installs a NumberField-backed peer")
    void doubleModelInstallsANumberFieldBackedPeer() {
        JSpinner s = new JSpinner(new SpinnerNumberModel(1.5, 0.0, 10.0, 0.1));
        assertInstanceOf(NumberField.class, inner(s));
        assertEquals(1.5, s.getValue());
    }

    @Test
    @DisplayName("SpinnerNumberModel with Long installs a LongField-backed peer")
    void longModelInstallsALongFieldBackedPeer() {
        // Every argument is explicitly boxed: bare `5L` would let Java widen
        // long → double in phase 1 of overload resolution and pick the
        // (double,double,double,double) ctor — a Double-valued model with a
        // NumberField peer, which is a different test.
        JSpinner s = new JSpinner(new SpinnerNumberModel(
                Long.valueOf(5L), Long.valueOf(0L), Long.valueOf(10L), Long.valueOf(1L)));
        assertInstanceOf(LongField.class, inner(s));
        assertEquals(5L, s.getValue());
    }

    @Test
    @DisplayName("setValue with Double on Integer-model succeeds and stores the Double (JDK quirk)")
    void setValueWithDoubleOnIntegerModelStoresTheDouble() {
        // Pre-SD_sjspinner this threw UOE (D_emulator_surrogate_split bare-minimum). JDK JSpinner accepts
        // the Double — SpinnerNumberModel.setValue only requires non-null
        // Number, no type narrowing. We match that now.
        JSpinner s = new JSpinner();
        s.setValue(1.5);
        assertEquals(1.5, s.getValue());
    }

    @Test
    @DisplayName("setValue null throws IAE matching SpinnerNumberModel")
    void setValueNullThrows() {
        JSpinner s = new JSpinner();
        assertThrows(IllegalArgumentException.class, () -> s.setValue(null));
    }

    @Test
    @DisplayName("setModel with null throws IllegalArgumentException matching JDK")
    void setModelNullThrows() {
        JSpinner s = new JSpinner();
        // JDK 25: IllegalArgumentException("null model"), measured; the ctor's null is an NPE.
        assertThrows(IllegalArgumentException.class, () -> s.setModel(null));
    }

    @Test
    @DisplayName("setValue out of range stores in model without validation (Swing quirk)")
    void setValueOutOfRangeStoresWithoutValidation() {
        // SpinnerNumberModel doesn't range-check setValue — it accepts
        // any Number (the UI-facing arrows are the only range guard).
        JSpinner s = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
        s.setValue(999);  // must not throw
        assertEquals(999, s.getValue());
    }

    @Test
    @DisplayName("getChangeListeners reflects add and remove")
    void getChangeListenersReflectsAddAndRemove() {
        JSpinner s = new JSpinner();
        ChangeListener l = e -> { };
        s.addChangeListener(l);
        assertTrue(Arrays.asList(s.getChangeListeners()).contains(l));
        s.removeChangeListener(l);
        assertFalse(Arrays.asList(s.getChangeListeners()).contains(l));
    }

    @Test
    @DisplayName("getUIClassID is SpinnerUI")
    void getUiClassIdIsSpinnerUi() {
        assertEquals("SpinnerUI", new JSpinner().getUIClassID());
    }

    // --- Editor surface (D_jspinner_editor) ----------------

    @Test
    @DisplayName("DateEditor pattern propagates to underlying DatePicker via setEditor")
    void dateEditorPatternPropagatesToDatePicker() {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor de = new JSpinner.DateEditor(s, "yyyy-MM-dd");

        s.setEditor(de);

        // The pattern should land on the DatePicker peer's i18n.
        DatePicker dp = assertInstanceOf(DatePicker.class, inner(s));
        assertEquals(List.of("yyyy-MM-dd"), dp.getI18n().getDateFormats());
        // Editor round-trips through getEditor.
        assertSame(de, s.getEditor());
        // SimpleDateFormat carries the pattern.
        assertEquals("yyyy-MM-dd", de.getFormat().toPattern());
        // Synthetic JFormattedTextField is non-null so migrators that
        // call `editor.getTextField().setColumns(8)` don't NPE.
        assertNotNull(de.getTextField());
        assertSame(s, de.getSpinner());
    }

    @Test
    @DisplayName("DateEditor different pattern updates DatePicker i18n")
    void dateEditorDifferentPatternUpdatesI18n() {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        s.setEditor(new JSpinner.DateEditor(s, "yyyy-MM-dd"));
        s.setEditor(new JSpinner.DateEditor(s, "MM/dd/yyyy"));
        DatePicker dp = assertInstanceOf(DatePicker.class, inner(s));
        assertEquals(List.of("MM/dd/yyyy"), dp.getI18n().getDateFormats());
    }

    @Test
    @DisplayName("getEditor returns null until setEditor is called")
    void getEditorReturnsNullUntilSetEditorIsCalled() {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        // Pre-setEditor: null. JDK lazily-creates a default editor; we
        // skip that — the peer renders without one and we surface only
        // what the user explicitly set.
        assertNull(s.getEditor());
    }

    @Test
    @DisplayName("setEditor fires editor PCE")
    void setEditorFiresEditorPce() {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        List<PropertyChangeEvent> pces = new ArrayList<>();
        s.addPropertyChangeListener("editor", pces::add);

        JSpinner.DateEditor de = new JSpinner.DateEditor(s, "yyyy-MM-dd");
        s.setEditor(de);
        PropertyChangeEvent pce = assertSingle(pces);
        assertSame(de, pce.getNewValue());
        assertNull(pce.getOldValue());
    }

    @Test
    @DisplayName("setEditor with same instance is no-op")
    void setEditorWithSameInstanceIsANoOp() {
        JSpinner s = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor de = new JSpinner.DateEditor(s, "yyyy-MM-dd");
        s.setEditor(de);

        List<PropertyChangeEvent> pces = new ArrayList<>();
        s.addPropertyChangeListener("editor", pces::add);
        s.setEditor(de);  // same instance — no PCE fire
        assertEquals(0, pces.size());
    }

    @Test
    @DisplayName("user edit while hosted drives ChangeListener end-to-end")
    void userEditWhileHostedDrivesChangeListener() {
        // End-to-end: JSpinner inside a visible JFrame; editing the
        // backing IntegerField simulates a browser edit; the Swing
        // ChangeListener sees it with source=JSpinner.
        JSpinner s = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        JFrame frame = new JFrame();
        frame.add(s);
        frame.setVisible(true);

        List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);

        LocatorJ._setValue(LocatorJ._get(IntegerField.class), 77);

        assertEquals(77, s.getValue());
        assertSame(s, assertSingle(events).getSource());
    }
}
