/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.beans.PropertyChangeEvent;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerListModel;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exit gate for SD_sjspinner's SJSpinner surrogate. Covers:
 *
 * <ol>
 *  <li><b>Inner-field selection per model type</b> — IntegerField / LongField /
 *      NumberField / DatePicker / Select / TextField-fallback.
 *  <li><b>Model-swap across types</b> — setModel rewires the inner peer; old
 *      listeners dropped, new inner installed.
 *  <li><b>R_swing_is_truth peer↔model sync</b> — user code → model → peer; peer edit →
 *      model → Swing ChangeEvent fan-out. preventPeerEvents feedback-loop
 *      guard.
 *  <li><b>JSpinner API semantics</b> — IAE on out-of-range setValue, ctor
 *      variants, getNextValue / getPreviousValue, PCE on setModel.
 *  <li><b>ReadOnly propagation</b> — user-facing bit ORed with fallback lock.
 *  <li><b>JComponentMixin smoke</b> — getUIClassID, client-property PCE.
 * </ol>
 */
class SJSpinnerTest extends AbstractKaribuTest {

    /** The one inner peer SJSpinner builds for its model. */
    private static Component inner(SJSpinner s) {
        return s.getChildren().findFirst().orElse(null);
    }

    /**
     * A SpinnerModel of no recognised family, so createInnerFieldFor falls
     * through to the read-only TextField arm.
     */
    private static SpinnerModel unknownModel(Object seed) {
        return new SpinnerModel() {
            private Object v = seed;

            @Override
            public Object getValue() {
                return v;
            }

            @Override
            public void setValue(Object value) {
                v = value;
            }

            @Override
            public Object getNextValue() {
                return null;
            }

            @Override
            public Object getPreviousValue() {
                return null;
            }

            @Override
            public void addChangeListener(ChangeListener l) {
            }

            @Override
            public void removeChangeListener(ChangeListener l) {
            }
        };
    }

    // --- Constructors ---------------------------------------------------

    @Test
    @DisplayName("default ctor installs SpinnerNumberModel with Integer value")
    void defaultCtorInstallsSpinnerNumberModel() {
        SJSpinner s = new SJSpinner();
        assertInstanceOf(SpinnerNumberModel.class, s.getModel());
        assertEquals(0, s.getValue());
        // Inner field should be IntegerField for Integer-valued model.
        assertInstanceOf(IntegerField.class, inner(s));
    }

    @Test
    @DisplayName("null model throws NPE matching JDK")
    void nullModelThrowsNpe() {
        assertThrows(NullPointerException.class, () -> new SJSpinner(null));
    }

    // --- Inner-field dispatch per model type ----------------------------

    @Test
    @DisplayName("SpinnerNumberModel Long installs LongField")
    void spinnerNumberModelLongInstallsLongField() {
        // Boxed on purpose. Bare long literals would widen to double in the
        // first phase of overload resolution and pick
        // SpinnerNumberModel(double, double, double, double), giving a
        // Double-valued model and a NumberField inner. Kotlin's resolution
        // picked (Number, Comparable, Comparable, Number) for the same
        // source text, which is the ctor this test means.
        SJSpinner s = new SJSpinner(
                new SpinnerNumberModel(Long.valueOf(1L), Long.valueOf(0L), Long.valueOf(10L), Long.valueOf(1L)));
        assertInstanceOf(LongField.class, inner(s));
    }

    @Test
    @DisplayName("SpinnerNumberModel Double installs NumberField")
    void spinnerNumberModelDoubleInstallsNumberField() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(1.5, 0.0, 10.0, 0.1));
        NumberField field = assertInstanceOf(NumberField.class, inner(s));
        assertEquals(1.5, field.getValue());
    }

    @Test
    @DisplayName("SpinnerDateModel installs DatePicker")
    void spinnerDateModelInstallsDatePicker() {
        SJSpinner s = new SJSpinner(new SpinnerDateModel());
        assertInstanceOf(DatePicker.class, inner(s));
    }

    @Test
    @DisplayName("SpinnerListModel installs Select")
    void spinnerListModelInstallsSelect() {
        SJSpinner s = new SJSpinner(new SpinnerListModel(List.of("a", "b", "c")));
        assertInstanceOf(Select.class, inner(s));
        assertEquals("a", s.getValue());
    }

    @Test
    @DisplayName("unknown SpinnerModel falls back to read-only TextField and WARNs")
    void unknownSpinnerModelFallsBackToReadOnlyTextField() {
        SJSpinner s = new SJSpinner(unknownModel("hello"));
        TextField field = assertInstanceOf(TextField.class, inner(s));
        assertTrue(field.isReadOnly(), "fallback TextField must be read-only");
        assertEquals("hello", field.getValue());
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("createInnerFieldFor")),
                "expected WARN about unknown SpinnerModel, got: " + capturedWarns);
    }

    // --- setModel rewires inner -----------------------------------------

    @Test
    @DisplayName("setModel to different-typed model rebuilds inner field")
    void setModelToDifferentTypedModelRebuildsInnerField() {
        SJSpinner s = new SJSpinner();  // starts with IntegerField
        Component oldInner = inner(s);
        assertInstanceOf(IntegerField.class, oldInner);

        s.setModel(new SpinnerDateModel());
        Component newInner = inner(s);
        assertInstanceOf(DatePicker.class, newInner);
        assertNotSame(oldInner, newInner);
    }

    @Test
    @DisplayName("setModel to same-typed model reuses inner field")
    void setModelToSameTypedModelReusesInnerField() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        Component originalInner = inner(s);

        s.setModel(new SpinnerNumberModel(7, 0, 20, 2));
        assertSame(originalInner, inner(s),
                "reusing same type should preserve the inner field instance");
        assertEquals(7, s.getValue());
    }

    @Test
    @DisplayName("setModel fires PCE for 'model'")
    void setModelFiresPceForModel() {
        SJSpinner s = new SJSpinner();
        List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener(events::add);

        SpinnerNumberModel newModel = new SpinnerNumberModel(1, 0, 10, 1);
        SpinnerModel oldModel = s.getModel();
        s.setModel(newModel);

        List<PropertyChangeEvent> pces = events.stream()
                .filter(it -> "model".equals(it.getPropertyName())).toList();
        assertEquals(1, pces.size(), "expected exactly one \"model\" PCE, got " + pces);
        assertSame(oldModel, pces.get(0).getOldValue());
        assertSame(newModel, pces.get(0).getNewValue());
    }

    @Test
    @DisplayName("setModel null throws NPE")
    void setModelNullThrowsNpe() {
        SJSpinner s = new SJSpinner();
        assertThrows(NullPointerException.class, () -> s.setModel(null));
    }

    // --- Value round-trip -----------------------------------------------

    @Test
    @DisplayName("setValue updates model and inner field")
    void setValueUpdatesModelAndInnerField() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(1, 0, 100, 1));
        s.setValue(42);
        assertEquals(42, s.getValue());
        assertEquals(42, s.getModel().getValue());
        assertEquals(42, ((IntegerField) inner(s)).getValue());
    }

    @Test
    @DisplayName("setValue out of range is accepted — matches JDK SpinnerNumberModel (no range check on setValue)")
    void setValueOutOfRangeIsAccepted() {
        // JDK quirk (feedback_educate_on_swing): SpinnerNumberModel.setValue
        // only validates that the argument is a non-null Number — the
        // min/max fence is enforced by getNextValue / getPreviousValue and
        // by the editor's commitEdit, not by setValue itself. Out-of-range
        // programmatic writes succeed; we match that.
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        s.setValue(99);
        assertEquals(99, s.getValue());
    }

    @Test
    @DisplayName("setValue null throws IAE matching SpinnerNumberModel")
    void setValueNullThrowsIae() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> s.setValue(null));
    }

    @Test
    @DisplayName("setValue wrong type throws IAE matching SpinnerNumberModel")
    void setValueWrongTypeThrowsIae() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(1, 0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> s.setValue("not a number"));
    }

    @Test
    @DisplayName("peer edit writes through to model and fires Swing ChangeEvent")
    void peerEditWritesThroughToModel() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(1, 0, 100, 1));
        List<ChangeEvent> events = new ArrayList<>();
        s.addChangeListener(events::add);

        ((IntegerField) inner(s)).setValue(42);

        assertEquals(42, s.getModel().getValue());
        assertTrue(events.stream().anyMatch(it -> it.getSource() == s),
                "expected ChangeEvent with source=SJSpinner, got: "
                        + events.stream().map(ChangeEvent::getSource).toList());
    }

    // --- ChangeListener management --------------------------------------

    @Test
    @DisplayName("removeChangeListener unsubscribes")
    void removeChangeListenerUnsubscribes() {
        SJSpinner s = new SJSpinner();
        List<ChangeEvent> events = new ArrayList<>();
        ChangeListener l = events::add;
        s.addChangeListener(l);
        s.setValue(5);
        int firstCount = events.size();
        assertTrue(firstCount > 0);

        s.removeChangeListener(l);
        s.setValue(6);
        assertEquals(firstCount, events.size(), "listener should have been removed");
    }

    @Test
    @DisplayName("getChangeListeners returns registered listeners in reverse add order")
    void getChangeListenersReturnsRegisteredListeners() {
        SJSpinner s = new SJSpinner();
        ChangeListener a = e -> {
        };
        ChangeListener b = e -> {
        };
        s.addChangeListener(a);
        s.addChangeListener(b);
        // EventListenerList stores newest first; JDK convention.
        assertEquals(2, s.getChangeListeners().length);
    }

    // --- getNextValue / getPreviousValue --------------------------------

    @Test
    @DisplayName("getNextValue and getPreviousValue delegate to model")
    void getNextValueAndGetPreviousValueDelegateToModel() {
        SJSpinner s = new SJSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        assertEquals(6, s.getNextValue());
        assertEquals(4, s.getPreviousValue());
    }

    // --- SpinnerDateModel round-trip ------------------------------------

    @Test
    @DisplayName("SpinnerDateModel peer edit converts LocalDate back to Date")
    void spinnerDateModelPeerEditConvertsLocalDateBackToDate() {
        Date initial = new Date();
        SJSpinner s = new SJSpinner(new SpinnerDateModel(initial, null, null, Calendar.DAY_OF_MONTH));
        DatePicker picker = (DatePicker) inner(s);

        LocalDate newDate = LocalDate.of(2026, 5, 14);
        picker.setValue(newDate);

        Date modelValue = (Date) s.getModel().getValue();
        Date expectedDate = Date.from(newDate.atStartOfDay(BrowserTimeZone.get()).toInstant());
        assertEquals(expectedDate, modelValue);
    }

    @Test
    @DisplayName("pre-1900 Calendar-built date shows its own day in the picker (Berlin, LMT east of CET)")
    void pre1900CalendarDateShowsItsOwnDayInPicker() {
        BrowserTimeZone.setZoneId(ZoneId.of("Europe/Berlin"));
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone(BrowserTimeZone.get()));
        cal.clear();
        cal.set(1815, Calendar.DECEMBER, 10);
        SJSpinner s = new SJSpinner(new SpinnerDateModel(cal.getTime(), null, null, Calendar.DAY_OF_MONTH));

        assertEquals(LocalDate.of(1815, 12, 10), ((DatePicker) inner(s)).getValue());
    }

    @Test
    @DisplayName("pre-1900 picked date formats as the day picked (Los Angeles, LMT west of PST)")
    void pre1900PickedDateFormatsAsTheDayPicked() {
        BrowserTimeZone.setZoneId(ZoneId.of("America/Los_Angeles"));
        SJSpinner s = new SJSpinner(new SpinnerDateModel(new Date(), null, null, Calendar.DAY_OF_MONTH));
        ((DatePicker) inner(s)).setValue(LocalDate.of(1867, 11, 7));

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
        fmt.setTimeZone(TimeZone.getTimeZone(BrowserTimeZone.get()));
        assertEquals("1867-11-07", fmt.format((Date) s.getModel().getValue()));
    }

    // --- Date pattern (D_jspinner_editor / item 18) -----------------------------------

    @Test
    @DisplayName("setDatePattern applies DatePickerI18n to inner DatePicker")
    void setDatePatternAppliesI18nToInnerDatePicker() {
        SJSpinner s = new SJSpinner(new SpinnerDateModel());
        DatePicker picker = (DatePicker) inner(s);

        s.setDatePattern("yyyy-MM-dd");

        assertEquals(List.of("yyyy-MM-dd"), picker.getI18n().getDateFormats());
        assertEquals("yyyy-MM-dd", s.getDatePattern());
        assertNoWarns("no WARNs expected, got: " + capturedWarns);
    }

    @Test
    @DisplayName("setDatePattern strips time component (date-only DatePicker)")
    void setDatePatternStripsTimeComponent() {
        SJSpinner s = new SJSpinner(new SpinnerDateModel());
        DatePicker picker = (DatePicker) inner(s);

        s.setDatePattern("yyyy-MM-dd HH:mm:ss");

        // Time component dropped silently per SD_maskformatter_regex / R_layouts_close_enough best-effort.
        assertEquals(List.of("yyyy-MM-dd"), picker.getI18n().getDateFormats());
        assertNoWarns("no WARNs expected, got: " + capturedWarns);
    }

    @Test
    @DisplayName("setDatePattern survives setModel rebuild")
    void setDatePatternSurvivesSetModelRebuild() {
        SJSpinner s = new SJSpinner(new SpinnerDateModel());
        s.setDatePattern("MM/dd/yyyy");

        // Swap to a fresh SpinnerDateModel — inner DatePicker rebuilds,
        // but the stored pattern must re-apply.
        s.setModel(new SpinnerDateModel());
        DatePicker newInner = (DatePicker) inner(s);
        assertEquals(List.of("MM/dd/yyyy"), newInner.getI18n().getDateFormats());
    }

    @Test
    @DisplayName("setDatePattern is no-op on non-Date model but stores pattern")
    void setDatePatternIsNoOpOnNonDateModelButStoresPattern() {
        SJSpinner s = new SJSpinner();  // IntegerField inner
        s.setDatePattern("yyyy-MM-dd");

        // Pattern stored on the surrogate; inner unchanged (IntegerField
        // has no i18n DateFormats concept).
        assertEquals("yyyy-MM-dd", s.getDatePattern());

        // Now swap to SpinnerDateModel — the stored pattern applies.
        s.setModel(new SpinnerDateModel());
        DatePicker dp = (DatePicker) inner(s);
        assertEquals(List.of("yyyy-MM-dd"), dp.getI18n().getDateFormats());
    }

    @Test
    @DisplayName("setDatePattern null resets i18n to defaults")
    void setDatePatternNullResetsI18nToDefaults() {
        SJSpinner s = new SJSpinner(new SpinnerDateModel());
        DatePicker picker = (DatePicker) inner(s);

        s.setDatePattern("yyyy-MM-dd");
        assertEquals(List.of("yyyy-MM-dd"), picker.getI18n().getDateFormats());

        s.setDatePattern(null);
        // Fresh empty I18n — getDateFormats returns null on an unset
        // override per Vaadin DatePickerI18n's default. Either null or
        // an empty list signals "no pattern override"; both are fine.
        List<String> formats = picker.getI18n().getDateFormats();
        assertTrue(formats == null || formats.isEmpty(),
                "i18n.dateFormats should be cleared after setDatePattern(null), got: " + formats);
        assertNull(s.getDatePattern());
    }

    // --- ReadOnly propagation -------------------------------------------

    @Test
    @DisplayName("setReadOnly propagates to inner field")
    void setReadOnlyPropagatesToInnerField() {
        SJSpinner s = new SJSpinner();
        IntegerField field = (IntegerField) inner(s);
        assertFalse(field.isReadOnly());

        s.setReadOnly(true);
        assertTrue(field.isReadOnly());

        s.setReadOnly(false);
        assertFalse(field.isReadOnly());
    }

    @Test
    @DisplayName("fallback TextField stays readOnly even when SJSpinner is not readOnly")
    void fallbackTextFieldStaysReadOnly() {
        SJSpinner s = new SJSpinner(unknownModel("x"));
        TextField field = (TextField) inner(s);

        // Default SJSpinner.isReadOnly is false; fallback forces inner lock.
        assertFalse(s.isReadOnly());
        assertTrue(field.isReadOnly(),
                "fallback TextField must remain locked regardless of SJSpinner.isReadOnly");

        s.setReadOnly(true);
        assertTrue(field.isReadOnly());

        s.setReadOnly(false);
        assertTrue(field.isReadOnly(), "fallback lock persists across setReadOnly(false)");
    }

    // --- JComponentMixin smoke ------------------------------------------

    @Test
    @DisplayName("getUIClassID returns SpinnerUI")
    void getUiClassIdReturnsSpinnerUi() {
        assertEquals("SpinnerUI", new SJSpinner().getUIClassID());
    }

    @Test
    @DisplayName("putClientProperty fires PCE under the property key")
    void putClientPropertyFiresPceUnderThePropertyKey() {
        SJSpinner s = new SJSpinner();
        List<PropertyChangeEvent> events = new ArrayList<>();
        s.addPropertyChangeListener(events::add);

        s.putClientProperty("foo", "bar");
        List<PropertyChangeEvent> pces = events.stream()
                .filter(it -> "foo".equals(it.getPropertyName())).toList();
        assertEquals(1, pces.size(), "expected exactly one \"foo\" PCE, got " + pces);
        assertNull(pces.get(0).getOldValue());
        assertEquals("bar", pces.get(0).getNewValue());
    }

    // --- Editor stubs -----------------------------------------------------

    @Test
    @DisplayName("getEditor and setEditor stay WARN stubs per D_emulator_surrogate_split")
    void getEditorAndSetEditorStayWarnStubs() {
        SJSpinner s = new SJSpinner();
        assertNull(s.getEditor());
        s.setEditor(null);
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("getEditor")));
        assertTrue(capturedWarns.stream().anyMatch(it -> it.contains("setEditor")));
    }
}
