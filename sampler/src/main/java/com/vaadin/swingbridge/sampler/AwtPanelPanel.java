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
import vaadinx.awt.Button;
import vaadinx.awt.FlowLayout;
import vaadinx.awt.Label;
import vaadinx.awt.Panel;
import vaadinx.awt.event.ContainerEvent;
import vaadinx.awt.event.ContainerListener;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.Color;

/**
 * {@link vaadinx.awt.Panel} — AWT's generic container, not {@link JPanel}.
 * Decisions: {@code D_awt_panel} / {@code SD_no_spanel}; the lane's rationale:
 * {@code ideas/awt-widgets.md}.
 *
 * <p>The class is four lines over {@code vaadinx.awt.Container}, so the demos
 * show the two things the AWT lane's leaf widgets could not: an all-AWT
 * subtree that actually lays itself out, and {@code ContainerListener} firing
 * on live add/remove — the one place the emulator layer does something the
 * surrogate layer explicitly cannot (SD_listeners_without_analog).
 *
 * <ol>
 *   <li><b>An all-AWT subtree with layout</b> — a default-{@code FlowLayout}
 *       Panel of AWT Buttons beside a {@code BorderLayout} Panel of AWT
 *       Labels. Nothing in either subtree has a {@code JComponent} in its
 *       chain; both are added into this Swing {@code JPanel} body.</li>
 *   <li><b>Default FlowLayout vs {@code Panel(null)}</b> — the same four
 *       children under each, making the one functional difference between
 *       {@code java.awt.Panel} and {@code java.awt.Container} visible rather
 *       than merely asserted.</li>
 *   <li><b>ContainerListener on live add/remove</b> — Add / Remove controls
 *       over an AWT Panel, with a readout of the last event and the current
 *       {@code getComponentCount()}.</li>
 * </ol>
 */
public class AwtPanelPanel extends JPanel {

    private static final Color TINT = new Color(0xEE, 0xEE, 0xF5);

    private final Panel live = new Panel();
    private final JLabel readout = new JLabel("no ContainerEvent yet — getComponentCount() = 0");
    private int added;

    public AwtPanelPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(demoSection("Demo 1 — an all-AWT subtree: FlowLayout wrap + BorderLayout regions",
                allAwtSubtree()));
        add(demoSection("Demo 2 — default FlowLayout vs Panel(null): the one difference from Container",
                defaultVsNull()));
        add(demoSection("Demo 3 — ContainerListener fires on live add / remove (emulator-only, SD_listeners_without_analog)",
                containerListenerDemo()));
    }

    /**
     * Left: a default {@code new Panel()} — FlowLayout, centered, 5px gaps —
     * holding enough AWT Buttons that the flex-wrap is visible when the
     * viewport narrows. Right: {@code Panel(new BorderLayout(8, 4))} with
     * Labels in NORTH / CENTER / SOUTH, where R_layouts_close_enough promises CENTER absorbs the
     * slack. Backgrounds are tinted because an AWT Label has no setBorder.
     */
    private static JPanel allAwtSubtree() {
        Panel flow = new Panel();
        flow.setBackground(new Color(0xE8, 0xF0, 0xE8));
        for (String caption : new String[] {"One", "Two", "Three", "Four", "Five", "Six"}) {
            flow.add(new Button(caption));
        }

        Panel border = new Panel(new BorderLayout(8, 4));
        border.setBackground(new Color(0xF0, 0xE8, 0xE8));
        border.add(tinted(new Label("NORTH", Label.CENTER)), BorderLayout.NORTH);
        border.add(tinted(new Label("CENTER — absorbs the slack", Label.CENTER)), BorderLayout.CENTER);
        border.add(tinted(new Label("SOUTH", Label.CENTER)), BorderLayout.SOUTH);

        JPanel row = new JPanel(new vaadinx.awt.GridLayout(1, 2, 12, 0));
        row.add(flow);
        row.add(border);
        return row;
    }

    /** Same children, one Panel with AWT's default FlowLayout and one with no manager. */
    private static JPanel defaultVsNull() {
        JPanel row = new JPanel(new vaadinx.awt.GridLayout(1, 2, 12, 0));
        row.add(withChildren(new Panel(), "new Panel() — FlowLayout"));
        row.add(withChildren(new Panel(null), "new Panel(null) — no manager, block flow"));
        return row;
    }

    private static JPanel withChildren(Panel p, String caption) {
        p.setBackground(TINT);
        for (String s : new String[] {"alpha", "beta", "gamma", "delta"}) {
            p.add(new Button(s));
        }
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        wrap.add(new JLabel(caption), BorderLayout.NORTH);
        wrap.add(p, BorderLayout.CENTER);
        return wrap;
    }

    /**
     * The Panel-specific behaviour worth demoing: {@code ContainerListener} is
     * real on the emulator (stored in {@code Component.listenerList}, dispatched
     * through {@code processContainerEvent}) where {@code ContainerMixin} onNoops
     * all three methods, because Vaadin 25 has no server-side child-list
     * mutation event.
     */
    private JPanel containerListenerDemo() {
        live.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
        live.setBackground(TINT);
        live.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                report("componentAdded", e);
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
                report("componentRemoved", e);
            }
        });

        JButton add = new JButton("Add a Button");
        add.addActionListener(e -> live.add(new Button("btn " + (++added))));
        JButton removeLast = new JButton("Remove last");
        removeLast.addActionListener(e -> {
            int n = live.getComponentCount();
            if (n > 0) {
                live.remove(n - 1);
            }
        });

        JPanel controls = new JPanel();
        controls.add(add);
        controls.add(removeLast);

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(controls, BorderLayout.NORTH);
        body.add(live, BorderLayout.CENTER);
        body.add(readout, BorderLayout.SOUTH);
        return body;
    }

    private void report(String kind, ContainerEvent e) {
        // Name the child by its caption, not its class — the readout's job is to
        // show *which* child the event carried, and every child here is a Button.
        vaadinx.awt.Component child = e.getChild();
        String name = switch (child) {
            case Button b -> b.getLabel();
            case Label l -> l.getText();
            default -> child.getClass().getSimpleName();
        };
        readout.setText(kind + "(" + name + ") — getComponentCount() = " + live.getComponentCount());
    }

    private static Label tinted(Label label) {
        label.setBackground(TINT);
        return label;
    }

    private static JPanel demoSection(String labelText, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
