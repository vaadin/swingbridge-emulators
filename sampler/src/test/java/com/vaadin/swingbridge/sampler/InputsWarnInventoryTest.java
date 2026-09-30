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
import com.vaadin.flow.component.slider.IntegerSlider;
import com.vaadin.flow.component.textfield.IntegerField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSlider;
import vaadinx.swing.JSpinner;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.SpinnerNumberModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Inputs view + numeric-input API surface exit gate. Both tests fail if
 * any {@code EHelper.onUnimplemented} / {@code onUnsupported} /
 * {@code onUnsupportedPeerShape} fires during the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_inputs_user_path} — {@link InputsView} driven
 *       end-to-end (construction, slider drag, spinner edit).
 *       Regression guard for the "normal user operation" exit
 *       criterion: any new stub call during form use trips the test.</li>
 *   <li>{@link #inventory_numeric_api_surface} — a micro-driver over the
 *       JSlider + JSpinner API buckets on an isolated fixture. Guards
 *       against reintroducing stubs into the supported surface. The SD_sjslider
 *       SJSlider surrogate closed the D_emulator_surrogate_split carve-outs for
 *       {@code setOrientation(VERTICAL)} / {@code setInverted} /
 *       {@code setSnapToTicks} / tick-spacing — the driver exercises
 *       them now and asserts no WARN. Tick / label rendering stays
 *       deferred behind <a href="https://github.com/vaadin/flow-components/issues/9181">
 *       vaadin/flow-components#9181</a> (SJSlider field-round-trip +
 *       WARN on visual-effect arguments). The SD_sjspinner SJSpinner surrogate
 *       closed the D_emulator_surrogate_split carve-outs for Long / Double / Date / List
 *       spinner models — the driver exercises each now and asserts no
 *       WARN (the unknown-model TextField fallback still WARNs by
 *       design and is not probed here).</li>
 * </ol>
 */
class InputsWarnInventoryTest {

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
    void inventory_inputs_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;

        // Navigate to the Sampler shell, then click the Inputs nav
        // button to swap InputsPanel in.
        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Inputs");
        dump("Step 0b (InputsPanel swap)", warnings);

        // Step 1: locate each peer. Lookups themselves shouldn't fire
        // anything, but we capture here so later steps start from empty.
        IntegerSlider slider = LocatorJ._get(IntegerSlider.class);
        IntegerField qty = LocatorJ._get(IntegerField.class);
        dump("Step 1 (lookups)", warnings);

        // Step 2: browser-style slider drag. _setValue fires
        // ValueChangeEvent with isFromClient=true — our ValueChangeListener
        // mirrors it into the BoundedRangeModel, and the model's
        // ChangeListener re-fires a Swing ChangeEvent that the view's
        // listener turns into a readout update. Raw slider.setValue would
        // fire isFromClient=false and not match the user-path intent.
        LocatorJ._setValue(slider, 75);
        dump("Step 2 (slider drag to 75)", warnings);

        // Step 3: browser-style spinner edit. Same R_swing_is_truth peer → model
        // path as the slider, just through IntegerField.
        LocatorJ._setValue(qty, 7);
        dump("Step 3 (spinner edit to 7)", warnings);

        // Step 4: model-side write (as if the app mutated programmatically).
        // Raw setValue here is intentional — isFromClient=false to mark
        // the path as server-originated, distinct from the user steps above.
        slider.setValue(10);
        qty.setValue(99);
        dump("Step 4 (programmatic model writes)", warnings);

        WarnDump.println();
        WarnDump.println("=== inputs user-path WARN total: " + warnings.size()
                + " ===");
    }

    @Test
    void inventory_numeric_api_surface() {
        // Micro-driver for the two numeric-input component buckets on an
        // isolated fixture. Only supported-surface APIs are exercised.
        // Post-SD_sjspinner every JDK SpinnerModel variant is probed below —
        // the D_emulator_surrogate_split carve-out is closed and the driver locks in
        // Long / Double / Date / List coverage. The unknown-model
        // TextField fallback still WARNs by design (app boots instead
        // of NPE'ing) and is not probed here.

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        JPanel panel = new JPanel();
        frame.add(panel);
        dump("Fixture setup", warnings);

        // -- Bucket 5a: JSlider + BoundedRangeModel core --

        JSlider slider = new JSlider();
        panel.add(slider);
        dump("5a  new JSlider() + add", warnings);

        slider.setMinimum(10);
        slider.setMaximum(200);
        dump("5a  slider.setMinimum/setMaximum", warnings);

        slider.setValue(42);
        slider.getValue();
        slider.getMinimum();
        slider.getMaximum();
        slider.getExtent();
        slider.setExtent(5);
        dump("5a  slider value/min/max/extent accessors", warnings);

        slider.getModel();
        slider.setModel(new DefaultBoundedRangeModel(0, 0, 0, 300));
        dump("5a  slider.getModel/setModel", warnings);

        javax.swing.event.ChangeListener sliderL = e -> {};
        slider.addChangeListener(sliderL);
        slider.getChangeListeners();
        slider.removeChangeListener(sliderL);
        dump("5a  slider ChangeListener add/get/remove", warnings);

        slider.setValueIsAdjusting(true);
        slider.getValueIsAdjusting();
        slider.setValueIsAdjusting(false);
        dump("5a  slider setValueIsAdjusting", warnings);

        // Orientation: HORIZONTAL always silent; VERTICAL closed by
        // SD_sjslider via SJSlider's `transform: rotate(-90deg)` — no WARN.
        slider.setOrientation(javax.swing.SwingConstants.HORIZONTAL);
        slider.setOrientation(javax.swing.SwingConstants.VERTICAL);
        slider.setOrientation(javax.swing.SwingConstants.HORIZONTAL);
        slider.getOrientation();
        dump("5a  slider orientation (HORIZONTAL + VERTICAL round-trip)", warnings);

        // Inverted: closed by SD_sjslider via `scaleX(-1)` — no WARN.
        slider.setInverted(true);
        slider.setInverted(false);
        slider.getInverted();
        dump("5a  slider inverted toggle", warnings);

        // Tick spacing: field storage is always non-visual (drives snap
        // logic + BeanInfo introspection), never WARNs.
        slider.setMajorTickSpacing(20);
        slider.setMinorTickSpacing(5);
        slider.getMajorTickSpacing();
        slider.getMinorTickSpacing();
        dump("5a  slider tick spacing setters", warnings);

        // snapToTicks: closed by SD_sjslider via server-side rounding in
        // SJSlider.syncValueFromPeer — no WARN (pure logic, no rendering).
        slider.setSnapToTicks(true);
        slider.setSnapToTicks(false);
        slider.getSnapToTicks();
        dump("5a  slider snapToTicks toggle", warnings);

        frame.setVisible(true);  // triggers validate/attach pass
        dump("5a  frame.setVisible(true) — attach the Slider peer", warnings);

        // -- Bucket 5b: JSpinner + SpinnerNumberModel core --

        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        panel.add(spinner);
        dump("5b  new JSpinner(SpinnerNumberModel) + add", warnings);

        spinner.setValue(3);
        spinner.getValue();
        spinner.getNextValue();
        spinner.getPreviousValue();
        dump("5b  spinner value + next/previous", warnings);

        spinner.getModel();
        spinner.setModel(new SpinnerNumberModel(0, -100, 100, 5));
        dump("5b  spinner.getModel/setModel", warnings);

        javax.swing.event.ChangeListener spinnerL = e -> {};
        spinner.addChangeListener(spinnerL);
        spinner.getChangeListeners();
        spinner.removeChangeListener(spinnerL);
        dump("5b  spinner ChangeListener add/get/remove", warnings);

        // -- Bucket 5c: JSpinner SD_sjspinner model-type coverage --
        // The non-Integer SpinnerModel paths the surrogate closed.

        JSpinner longSpinner = new JSpinner(new SpinnerNumberModel(
                Long.valueOf(1L), Long.valueOf(0L), Long.valueOf(1000L), Long.valueOf(1L)));
        panel.add(longSpinner);
        longSpinner.setValue(500L);
        dump("5c  new JSpinner(SpinnerNumberModel<Long>) + setValue", warnings);

        JSpinner doubleSpinner = new JSpinner(new SpinnerNumberModel(1.5, 0.0, 10.0, 0.1));
        panel.add(doubleSpinner);
        doubleSpinner.setValue(2.5);
        dump("5c  new JSpinner(SpinnerNumberModel<Double>) + setValue", warnings);

        JSpinner dateSpinner = new JSpinner(new javax.swing.SpinnerDateModel());
        panel.add(dateSpinner);
        dateSpinner.setValue(new java.util.Date());
        dump("5c  new JSpinner(SpinnerDateModel) + setValue", warnings);

        JSpinner listSpinner = new JSpinner(
                new javax.swing.SpinnerListModel(java.util.List.of("a", "b", "c")));
        panel.add(listSpinner);
        listSpinner.setValue("b");
        dump("5c  new JSpinner(SpinnerListModel) + setValue", warnings);

        // Cross-type model-swap on a single spinner — the swap path
        // itself must not emit WARNs (inner-field rebuild is an
        // implementation detail, not a stub).
        JSpinner morph = new JSpinner();  // Integer initially
        panel.add(morph);
        morph.setModel(new SpinnerNumberModel(1.0, 0.0, 10.0, 0.1));   // → NumberField
        morph.setModel(new javax.swing.SpinnerDateModel());             // → DatePicker
        morph.setModel(new javax.swing.SpinnerListModel(java.util.List.of("x", "y"))); // → Select
        dump("5c  JSpinner.setModel across types (rebuild)", warnings);

        // -- Bucket 5d: JSpinner DateEditor surface (D_jspinner_editor, item 18) --
        // SpinnerDateModel + DateEditor("yyyy-MM-dd") is the CRUD edit
        // dialog's date-of-birth shape. The editor's pattern routes
        // through SJSpinner.setDatePattern → DatePicker.setI18n.
        // setEditor must not emit WARNs on the DateEditor branch; the
        // synthetic JFormattedTextField inside DefaultEditor is built
        // with a DateFormatter so the strategy pin matches DateStrategy
        // (no cross-family swap WARN).

        JSpinner dobSpinner = new JSpinner(new javax.swing.SpinnerDateModel());
        panel.add(dobSpinner);
        dump("5d  new JSpinner(SpinnerDateModel) for DateEditor", warnings);

        JSpinner.DateEditor de = new JSpinner.DateEditor(dobSpinner, "yyyy-MM-dd");
        dump("5d  new JSpinner.DateEditor(spinner, \"yyyy-MM-dd\")", warnings);

        dobSpinner.setEditor(de);
        dump("5d  spinner.setEditor(DateEditor) — pattern → DatePicker.i18n", warnings);

        // Editor introspection round-trip: getEditor returns the same
        // instance; getFormat returns the SimpleDateFormat with the
        // pattern; getTextField returns a non-null JFormattedTextField
        // (synthetic, off-DOM) so migrated code that introspects doesn't
        // NPE. None of these accessors should fire WARNs.
        org.junit.jupiter.api.Assertions.assertSame(de, dobSpinner.getEditor());
        org.junit.jupiter.api.Assertions.assertEquals("yyyy-MM-dd", de.getFormat().toPattern());
        org.junit.jupiter.api.Assertions.assertNotNull(de.getTextField());
        org.junit.jupiter.api.Assertions.assertSame(dobSpinner, de.getSpinner());
        dump("5d  DateEditor introspection (getEditor/getFormat/getTextField/getSpinner)", warnings);

        // Pattern propagation: assert the DatePicker peer received the
        // i18n setting. Reaching through the surrogate's CustomField
        // children via Karibu locator confirms the wire is real and not
        // just field-shadowed.
        com.vaadin.flow.component.datepicker.DatePicker peerDp =
                LocatorJ._get((com.vaadin.flow.component.Component) dobSpinner.getPeer(),
                        com.vaadin.flow.component.datepicker.DatePicker.class);
        org.junit.jupiter.api.Assertions.assertNotNull(peerDp.getI18n(),
                "DatePicker should have received an I18n object from setDatePattern");
        java.util.List<String> formats = peerDp.getI18n().getDateFormats();
        org.junit.jupiter.api.Assertions.assertEquals(1, formats.size(),
                "i18n.getDateFormats should contain the single pattern");
        org.junit.jupiter.api.Assertions.assertEquals("yyyy-MM-dd", formats.get(0));
        dump("5d  DatePicker.i18n.getDateFormats receives 'yyyy-MM-dd'", warnings);

        // Re-applying after setModel rebuild: a SpinnerDateModel → fresh
        // SpinnerDateModel swap rebuilds the DatePicker, but the previously-
        // set pattern survives via the surrogate's stored datePattern.
        dobSpinner.setModel(new javax.swing.SpinnerDateModel());
        com.vaadin.flow.component.datepicker.DatePicker peerDp2 =
                LocatorJ._get((com.vaadin.flow.component.Component) dobSpinner.getPeer(),
                        com.vaadin.flow.component.datepicker.DatePicker.class);
        org.junit.jupiter.api.Assertions.assertEquals("yyyy-MM-dd",
                peerDp2.getI18n().getDateFormats().get(0),
                "Pattern must survive setModel(SpinnerDateModel) rebuild");
        dump("5d  setModel rebuild preserves date pattern on fresh DatePicker", warnings);

        WarnDump.println();
        WarnDump.println("=== numeric API-surface WARN total across buckets above ===");
    }

    /**
     * Print the per-step ledger line (always, so the record survives
     * even when the test passes), drain {@code warnings}, then fail if
     * the step emitted any stub WARN. The {@link AssertionError}
     * message carries both the banner (locates the regression) and the
     * list of WARNs (tells you which stubs fired), so fixing the break
     * is a straight-shot.
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
                    + " stub WARN(s) fired — regression in the inputs exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
