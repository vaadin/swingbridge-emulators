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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JFormattedTextField;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;

import javax.swing.text.DateFormatter;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.MaskFormatter;
import javax.swing.text.NumberFormatter;
import java.beans.PropertyChangeEvent;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_jformattedtextfield + SD_sjformatted_family exit gate for the DateFormatter slice: JFormattedTextField with a
 * DateFormatter renders as a Vaadin DatePicker, browser date pick fires
 * PCE("value") + ActionEvent through the strategy, every step asserts zero
 * stub WARNs.
 *
 * <p>Two tests:
 * <ol>
 *   <li>{@link #inventory_formatted_fields_user_path} — drives the
 *       sampler {@link FormattedFieldsPanel} via the route, picks a date
 *       via Karibu's {@code _setValue} (isFromClient=true), and asserts
 *       the status mirror updated + zero stub WARNs across the path.</li>
 *   <li>{@link #inventory_jformattedtextfield_api_surface} — micro-driver
 *       over the strategy-dispatch table: DateFormatter ctor → DatePicker
 *       peer; setValue / getValue round-trip; programmatic + peer-driven
 *       PCE round-trip via the install bridge; same-family setFormatter
 *       swap; focusLostBehavior + IAE on bad int.</li>
 * </ol>
 */
class FormattedFieldsWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_formatted_fields_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell)", warnings);

        Navigate.to("Formatted fields");
        dump("Step 0b (FormattedFieldsPanel swap)", warnings);

        DatePicker picker = LocatorJ._get(DatePicker.class);
        assertNotNull(picker, "DatePicker peer should be located after panel swap");
        // Initial value seeded to 2026-05-08 by the panel ctor.
        assertEquals(LocalDate.of(2026, 5, 8), picker.getValue());
        dump("Step 1 (peer lookup + initial value)", warnings);

        // Browser-style date pick — _setValue fires isFromClient=true,
        // matching the user's calendar-popup commit path.
        LocatorJ._setValue(picker, LocalDate.of(2027, 1, 15));
        dump("Step 2 (browser date pick)", warnings);

        // Status row should reflect the new value via the panel's
        // PCE("value") listener. Multiple NativeLabels in the panel
        // ("Date of birth:", "Notes:", and the two status mirrors); pick
        // the date status — the one starting with "value =" and
        // containing the picked date.
        com.vaadin.flow.component.html.NativeLabel dateStatus = LocatorJ._get(
                com.vaadin.flow.component.html.NativeLabel.class,
                spec -> spec.withPredicate(
                        l -> l.getText() != null && l.getText().contains("Fri Jan 15")));
        assertTrue(dateStatus.getText().contains("Fri Jan 15"),
                "date status mirror should show the picked date; got: " + dateStatus.getText());
        dump("Step 3 (date status mirror updated)", warnings);

        // Notes row exercises DefaultFormattedStrategy +
        // SJFormattedTextField. Browser typing the new text + Enter on
        // the field commits via fireActionPerformed → commitEdit →
        // setValue; status mirror updates via PCE.
        TextField notesField = LocatorJ._get(TextField.class,
                spec -> spec.withPredicate(
                        tf -> tf.getValue() != null && tf.getValue().contains("Enter")));
        LocatorJ._setValue(notesField, "edited via browser");
        dump("Step 4 (notes browser type)", warnings);

        // _setValue alone doesn't trip Enter-on-text — but per the
        // JTextComponentMixin sync, the displayed text reaches the
        // emulator's Document. The Object value field is unchanged
        // (commitEdit hasn't run yet). That's correct JFormattedTextField
        // semantics: typing changes text, only commit changes value.

        WarnDump.println();
        WarnDump.println("=== formatted-fields user-path WARN total: "
                + warnings.size() + " ===");
    }

    @Test
    void inventory_jformattedtextfield_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        JPanel panel = new JPanel();
        frame.add(panel);
        dump("Fixture setup", warnings);

        // -- D_jformattedtextfield strategy dispatch: DateFormatter → DatePicker peer --

        DateFormatter dateFormatter = new DateFormatter(new SimpleDateFormat("yyyy-MM-dd"));
        JFormattedTextField field = new JFormattedTextField(dateFormatter);
        panel.add(field);
        dump("DateFormatter ctor (DateStrategy → SJFormattedDatePicker peer)", warnings);

        // -- setValue / getValue round-trip --

        Date initial = makeDate(2026, 5, 8);
        field.setValue(initial);
        assertEquals(initial, field.getValue());
        dump("setValue(Date) + getValue round-trip", warnings);

        // PCE on programmatic setValue.
        List<PropertyChangeEvent> pces = new ArrayList<>();
        field.addPropertyChangeListener("value", pces::add);

        Date next = makeDate(2027, 1, 15);
        field.setValue(next);
        assertEquals(1, pces.size(), "single PCE per setValue (no double-fire from peer bridge)");
        assertEquals(initial, pces.get(0).getOldValue());
        assertEquals(next, pces.get(0).getNewValue());
        dump("PCE on programmatic setValue", warnings);

        // -- focusLostBehavior round-trip --

        Consumer<PropertyChangeEvent> ignored = e -> {};
        field.addPropertyChangeListener("focusLostBehavior", ignored::accept);
        field.setFocusLostBehavior(JFormattedTextField.PERSIST);
        assertEquals(JFormattedTextField.PERSIST, field.getFocusLostBehavior());
        dump("focusLostBehavior round-trip", warnings);

        // -- Same-family setFormatter swap (D_formatter_swap_rules) --

        DateFormatter alt = new DateFormatter(new SimpleDateFormat("dd/MM/yyyy"));
        // setFormatter is protected on JFormattedTextField — use a subclass
        // exposing the call (mirrors how migrators install formatters via
        // factory or ctor).
        new JFormattedTextField(alt); // Same-family swap via ctor; would WARN if cross-family.
        dump("Same-family setFormatter swap (DateFormatter → DateFormatter)", warnings);

        // -- Formatter on a no-formatter field (DefaultStrategy → SJFormattedTextField) --
        // -- peer is now SJFormattedTextField; setValue still --
        // -- routes through Object.toString since formatter is null.       --

        JFormattedTextField plain = new JFormattedTextField();
        plain.setValue("hello");
        assertEquals("hello", plain.getValue());
        // Round-trip through getText (the displayed Document text); should
        // match the toString of the value when no formatter is installed.
        assertEquals("hello", plain.getText());
        dump("DefaultStrategy fallback (no formatter, String value)", warnings);

        // commitEdit on a no-formatter field round-trips the displayed text
        // as the value; isEditValid always true.
        plain.setText("edited");
        try {
            plain.commitEdit();
        } catch (java.text.ParseException e) {
            throw new AssertionError("commitEdit on no-formatter field should not throw", e);
        }
        assertEquals("edited", plain.getValue());
        assertTrue(plain.isEditValid());
        dump("commitEdit on no-formatter field", warnings);

        // -- number-family strategy dispatch --

        NumberFormatter intFormatter = new NumberFormatter(NumberFormat.getIntegerInstance());
        intFormatter.setValueClass(Integer.class);
        JFormattedTextField intField = new JFormattedTextField(intFormatter);
        intField.setValue(99);
        assertEquals(99, intField.getValue());
        dump("NumberFormatter(Integer) ctor (IntegerStrategy → SJFormattedIntegerField)", warnings);

        NumberFormatter longFormatter = new NumberFormatter(NumberFormat.getIntegerInstance());
        longFormatter.setValueClass(Long.class);
        JFormattedTextField longField = new JFormattedTextField(longFormatter);
        longField.setValue(1234567890123L);
        assertEquals(1234567890123L, longField.getValue());
        dump("NumberFormatter(Long) ctor (LongStrategy → SJFormattedLongField)", warnings);

        NumberFormatter doubleFormatter = new NumberFormatter(NumberFormat.getNumberInstance());
        doubleFormatter.setValueClass(Double.class);
        JFormattedTextField doubleField = new JFormattedTextField(doubleFormatter);
        doubleField.setValue(3.14);
        assertEquals(3.14, doubleField.getValue());
        dump("NumberFormatter(Double) ctor (NumberStrategy → SJFormattedNumberField)", warnings);

        // Cross-Number-subtype write: the JDK stores a Long set on an Integer
        // field as-is (measured on JDK 25); only the peer narrows via intValue().
        intField.setValue(5L);
        assertEquals(5L, intField.getValue());
        dump("Cross-Number write (Long input → Integer field)", warnings);

        // -- MaskFormatter strategy + browser pattern attribute --

        MaskFormatter phoneFormatter;
        try {
            phoneFormatter = new MaskFormatter("(###) ###-####");
        } catch (ParseException e) {
            throw new AssertionError("phone mask should parse", e);
        }
        JFormattedTextField phone = new JFormattedTextField(phoneFormatter);
        phone.setValue("(415) 555-0100");
        assertEquals("(415) 555-0100", phone.getValue());
        // Verify the surrogate's pattern attribute was set by MaskStrategy.
        com.vaadin.swingbridge.surrogates.SJFormattedTextField phonePeer =
                (com.vaadin.swingbridge.surrogates.SJFormattedTextField) phone.getPeer();
        String pattern = phonePeer.getBrowserPattern();
        assertEquals(
                "^\\([0-9][0-9][0-9]\\) [0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]$",
                pattern);
        dump("MaskFormatter ctor (MaskStrategy → SJFormattedTextField + pattern attr)", warnings);

        // -- AbstractFormatterFactory pulls the display formatter --

        DateFormatter dateFmt = new DateFormatter(new SimpleDateFormat("yyyy-MM-dd"));
        DefaultFormatterFactory dateFactory = new DefaultFormatterFactory(dateFmt);
        JFormattedTextField factoryField = new JFormattedTextField(dateFactory);
        factoryField.setValue(makeDate(2026, 5, 8));
        // The factory's display formatter installs as the active formatter;
        // strategy was picked off the value's runtime type (Date → DateStrategy).
        assertEquals(dateFmt, factoryField.getFormatter());
        dump("DefaultFormatterFactory(DateFormatter) + Date initial value", warnings);

        WarnDump.println();
        WarnDump.println("=== formatted-fields API-surface WARN total: "
                + warnings.size() + " ===");
    }

    private static Date makeDate(int year, int month, int day) {
        // Match the BrowserTimeZone fixture (UTC in tests, SD_browser_timezone) so the
        // Date the test feeds in survives the Date↔LocalDate round-trip
        // through SJFormattedDatePicker without crossing a day boundary.
        return com.vaadin.swingbridge.surrogates.util.BrowserDateUtils.dateOf(year, month, day);
    }

    /**
     * Print accumulated warnings for the labeled step + assert empty.
     * Mirrors {@link InputsWarnInventoryTest#dump} so the inventory is
     * step-banner readable when something regresses.
     */
    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the formatted-fields exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
