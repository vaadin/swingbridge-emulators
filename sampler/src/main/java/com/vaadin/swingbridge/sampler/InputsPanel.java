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

import vaadinx.awt.BorderLayout;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSlider;
import vaadinx.swing.JSpinner;

import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Numeric-input demo. {@link JSlider} +
 * {@link JSpinner} with live read-back into a {@link JLabel} via
 * {@link javax.swing.event.ChangeListener} on each.
 *
 * <ul>
 *   <li>{@link JSlider} 0..100 — reuses JDK {@code DefaultBoundedRangeModel}.</li>
 *   <li>{@link JSpinner} 1..99 — reuses JDK {@link SpinnerNumberModel}.</li>
 *   <li>{@link JSpinner} date — {@link SpinnerDateModel} +
 *       {@link JSpinner.DateEditor}{@code ("yyyy-MM-dd")}; demonstrates the
 *       D_jspinner_editor plumbing (DateEditor pattern routes
 *       to the underlying Vaadin DatePicker via SJSpinner.setDatePattern).</li>
 *   <li>{@link JLabel} live readout updated from each component's
 *       ChangeListener; the source on the event is the emulator
 *       (JSlider / JSpinner), so {@code (JSlider) e.getSource()} works
 *       in migrated code.</li>
 * </ul>
 */
public class InputsPanel extends JPanel {

    private static final SimpleDateFormat ISO_DATE = new SimpleDateFormat("yyyy-MM-dd");

    public InputsPanel() {
        super(new BorderLayout(8, 8));

        JSlider volume = new JSlider(0, 100, 50);
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 99, 1));
        JSpinner dob = new JSpinner(new SpinnerDateModel());
        dob.setEditor(new JSpinner.DateEditor(dob, "yyyy-MM-dd"));

        JLabel readout = new JLabel(format(volume.getValue(),
                (Integer) quantity.getValue(), (Date) dob.getValue()));

        Runnable refresh = () -> readout.setText(format(volume.getValue(),
                (Integer) quantity.getValue(), (Date) dob.getValue()));
        volume.addChangeListener(e -> refresh.run());
        quantity.addChangeListener(e -> refresh.run());
        dob.addChangeListener(e -> refresh.run());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        controls.add(new JLabel("Volume:"));
        controls.add(volume);
        controls.add(new JLabel("Quantity:"));
        controls.add(quantity);
        controls.add(new JLabel("Date of birth:"));
        controls.add(dob);
        add(controls, BorderLayout.CENTER);
        add(readout, BorderLayout.SOUTH);
    }

    private static String format(int volume, int quantity, Date dob) {
        String dobStr;
        synchronized (ISO_DATE) {
            dobStr = dob == null ? "(none)" : ISO_DATE.format(dob);
        }
        return "volume = " + volume + ", quantity = " + quantity + ", dob = " + dobStr;
    }
}
