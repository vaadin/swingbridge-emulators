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
import vaadinx.awt.GridLayout;
import vaadinx.awt.Label;
import vaadinx.awt.Scrollbar;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.Color;
import java.awt.event.AdjustmentEvent;

/**
 * {@link vaadinx.awt.Scrollbar} — the AWT 1.0 scrollbar, not
 * {@link vaadinx.swing.JScrollBar}. Decisions: {@code D_awt_scrollbar} / {@code SD_sscrollbar}; the
 * lane's rationale: {@code ideas/awt-widgets.md}.
 *
 * <ol>
 *   <li><b>The poor-man's slider</b> — a horizontal 0–255 bar tinting a
 *       {@code java.awt.Label}, with the {@code AdjustmentEvent} readout that
 *       makes two facts visible: every browser-sourced event says
 *       {@code TRACK}, and {@code getValueIsAdjusting()} goes false on
 *       drag-end. AWT 1.0 had no slider, so this <em>is</em> what the class
 *       was used for.</li>
 *   <li><b>A vertical bar in {@code BorderLayout.EAST}</b> — the default
 *       orientation, in the region where a rotate-transform host would
 *       visibly fail. This one has to be eyeballed in a browser: a
 *       server-side round-trip passes either way.</li>
 *   <li><b>Live {@code setValues} clamping</b> — buttons that push the band
 *       around, each refreshing a four-field readout. The readout is refreshed
 *       <em>by the button</em>, never by the listener, because a programmatic
 *       write fires nothing — which is itself the assertion.</li>
 * </ol>
 */
public class AwtScrollbarPanel extends JPanel {

    public AwtScrollbarPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(demoSection("Demo 1 — java.awt.Scrollbar as the value knob AWT-era code used it as: "
                + "every browser event is TRACK, and isAdjusting goes false on drag-end",
                colourKnob()));
        add(demoSection("Demo 2 — the default (VERTICAL) orientation in BorderLayout.EAST: "
                + "the bar must be tall and narrow, and must not steal the region's width",
                verticalInEast()));
        add(demoSection("Demo 3 — setValues clamping: the reachable band is "
                + "[minimum, maximum - visibleAmount], and programmatic writes fire nothing",
                clampingBench()));
    }

    /** Demo 1 — the idiom that survives in AWT residue. */
    private static JPanel colourKnob() {
        Scrollbar grey = new Scrollbar(Scrollbar.HORIZONTAL, 128, 10, 0, 255);
        Label swatch = new Label("background tracks the bar");
        swatch.setAlignment(Label.CENTER);
        tint(swatch, grey.getValue());

        JLabel eventReadout = new JLabel("no AdjustmentEvent yet — drag the bar");
        grey.addAdjustmentListener(e -> {
            tint(swatch, e.getValue());
            eventReadout.setText("AdjustmentEvent: getValue()=" + e.getValue()
                    + ", getAdjustmentType()=" + adjustmentType(e)
                    + ", getValueIsAdjusting()=" + e.getValueIsAdjusting()
                    + ", getAdjustable() is the Scrollbar=" + (e.getAdjustable() == grey));
        });

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(grey, BorderLayout.NORTH);
        panel.add(swatch, BorderLayout.CENTER);
        panel.add(eventReadout, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Demo 2 — the browser check. A vertical bar's rendering is the one thing
     * no server-side test can assert: a bar that renders horizontally, or one
     * whose layout box claims the width of a horizontal control, round-trips
     * its API identically.
     */
    private static JPanel verticalInEast() {
        Scrollbar vertical = new Scrollbar();   // VERTICAL is AWT's default
        JLabel readout = new JLabel("value = " + vertical.getValue()
                + " (minimum is at the top, as in AWT)");
        vertical.addAdjustmentListener(e ->
                readout.setText("value = " + e.getValue()
                        + " (minimum is at the top, as in AWT)"));

        JPanel region = new JPanel(new BorderLayout(6, 0));
        region.setPreferredSize(new java.awt.Dimension(420, 160));
        region.add(readout, BorderLayout.CENTER);
        region.add(vertical, BorderLayout.EAST);
        return region;
    }

    /** Demo 3 — the clamping table, driven by hand. */
    private static JPanel clampingBench() {
        Scrollbar bar = new Scrollbar(Scrollbar.HORIZONTAL, 40, 10, 0, 100);
        JLabel readout = new JLabel(bandState(bar));
        // Deliberately NOT refreshed from an AdjustmentListener: every button
        // below is a programmatic write, and AWT fires nothing for those.
        JButton setValue = new JButton("setValue(999)");
        setValue.addActionListener(e -> {
            bar.setValue(999);
            readout.setText(bandState(bar));
        });
        JButton widen = new JButton("setVisibleAmount(50)");
        widen.addActionListener(e -> {
            bar.setVisibleAmount(50);
            readout.setText(bandState(bar));
        });
        JButton shrinkMax = new JButton("setMaximum(20) below the value");
        shrinkMax.addActionListener(e -> {
            bar.setMaximum(20);
            readout.setText(bandState(bar));
        });
        JButton reset = new JButton("setValues(40, 10, 0, 100)");
        reset.addActionListener(e -> {
            bar.setValues(40, 10, 0, 100);
            readout.setText(bandState(bar));
        });

        JPanel controls = new JPanel();
        controls.add(setValue);
        controls.add(widen);
        controls.add(shrinkMax);
        controls.add(reset);
        JPanel readouts = new JPanel(new GridLayout(2, 1, 0, 2));
        readouts.add(readout);
        readouts.add(new JLabel("the readout is refreshed by the button, never by a "
                + "listener — a programmatic write fires no AdjustmentEvent"));

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(bar, BorderLayout.NORTH);
        panel.add(controls, BorderLayout.CENTER);
        panel.add(readouts, BorderLayout.SOUTH);
        return panel;
    }

    private static void tint(Label swatch, int grey) {
        swatch.setBackground(new Color(grey, grey, grey));
        // Keep the caption legible across the whole range.
        swatch.setForeground(grey > 127 ? Color.BLACK : Color.WHITE);
    }

    /**
     * @return the constant's name — always {@code TRACK} from a browser, since
     *         nothing server-side distinguishes an end-arrow click from a track
     *         click from a keypress (R_match_swing_errors sub-bucket (c))
     */
    private static String adjustmentType(AdjustmentEvent e) {
        return switch (e.getAdjustmentType()) {
            case AdjustmentEvent.UNIT_INCREMENT -> "UNIT_INCREMENT";
            case AdjustmentEvent.UNIT_DECREMENT -> "UNIT_DECREMENT";
            case AdjustmentEvent.BLOCK_DECREMENT -> "BLOCK_DECREMENT";
            case AdjustmentEvent.BLOCK_INCREMENT -> "BLOCK_INCREMENT";
            case AdjustmentEvent.TRACK -> "TRACK";
            default -> "?" + e.getAdjustmentType();
        };
    }

    /** The four fields whose interaction is the whole class. */
    private static String bandState(Scrollbar bar) {
        return "getValue() = " + bar.getValue()
                + ", getVisibleAmount() = " + bar.getVisibleAmount()
                + ", getMinimum() = " + bar.getMinimum()
                + ", getMaximum() = " + bar.getMaximum()
                + "  →  reachable band [" + bar.getMinimum() + ", "
                + (bar.getMaximum() - bar.getVisibleAmount()) + "]";
    }

    private static JPanel demoSection(String labelText, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        return wrap;
    }
}
