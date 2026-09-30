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
import vaadinx.awt.Label;
import vaadinx.awt.Panel;
import vaadinx.awt.ScrollPane;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.Color;
import java.awt.Point;

/**
 * {@link vaadinx.awt.ScrollPane} — AWT's scrolling container, not
 * {@link vaadinx.swing.JScrollPane}. Decisions: {@code D_awt_scrollpane} / {@code SD_sscrollpane}.
 *
 * <p>Unlike the lane's leaf widgets this class has a mechanism in it, so the
 * demos target the three things that distinguish it from a plain container:
 * a display policy that maps onto Vaadin's scroll direction, a single-child
 * rule enforced by replacement, and a pair of {@code Adjustable} value models
 * standing in for scrollbars that are not components.
 *
 * <ol>
 *   <li><b>Policy side by side</b> — {@code SCROLLBARS_AS_NEEDED} against
 *       {@code SCROLLBARS_NEVER}, each over the same tall stack of AWT
 *       Labels, so the mapping onto {@code ScrollDirection} is visible.</li>
 *   <li><b>Programmatic scrolling</b> — Scroll to 200 / Scroll to top, with a
 *       readout fed by {@code getScrollPosition()}: the same-request
 *       round-trip migrated code relies on.</li>
 *   <li><b>An AdjustmentListener on {@code getVAdjustable()}</b>, appending
 *       each event's value to the readout. <b>It fires for the buttons and
 *       not for your mouse wheel</b> — the slice's one real limitation, put on
 *       screen rather than left in a javadoc. See the note in the demo.</li>
 *   <li><b>The single-child rule</b> — Replace child, with the readout showing
 *       {@code getComponentCount()} staying at 1.</li>
 * </ol>
 *
 * <p>{@code SCROLLBARS_ALWAYS}, {@code setWheelScrollingEnabled(false)} and
 * {@code getAccessibleContext()} stay off the user path: all three WARN by
 * design, and the route's exit gate asserts zero WARNs across it.
 */
public class AwtScrollPanePanel extends JPanel {

    private static final Color TINT = new Color(0xEE, 0xEE, 0xF5);

    private final ScrollPane live = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
    private final JLabel readout = new JLabel("scrollPosition = (0,0) — getComponentCount() = 1");
    private int generation;

    public AwtScrollPanePanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(demoSection("Demo 1 — SCROLLBARS_AS_NEEDED vs SCROLLBARS_NEVER over the same content",
                policySideBySide()));
        add(demoSection("Demo 2 — setScrollPosition / getScrollPosition, and an AdjustmentListener",
                scrollingDemo()));
    }

    /**
     * Both panes wrap an identical tall stack, so the only variable is the
     * policy. NEVER maps to {@code ScrollDirection.NONE} and clips; AS_NEEDED
     * maps to BOTH and scrolls.
     */
    private static JPanel policySideBySide() {
        JPanel row = new JPanel(new vaadinx.awt.GridLayout(1, 2, 12, 0));
        row.add(captioned(pane(ScrollPane.SCROLLBARS_AS_NEEDED),
                "SCROLLBARS_AS_NEEDED — scrolls"));
        row.add(captioned(pane(ScrollPane.SCROLLBARS_NEVER),
                "SCROLLBARS_NEVER — clips"));
        return row;
    }

    private static ScrollPane pane(int policy) {
        ScrollPane sp = new ScrollPane(policy);
        sp.add(tallStack("row"));
        bound(sp);
        return sp;
    }

    /**
     * Give the pane a bounded height, so its content actually overflows and
     * there is something to scroll.
     *
     * <p>Necessary because a Vaadin {@code Scroller} with no height constraint
     * grows to fit its content and therefore never scrolls — browser-verified,
     * and invisible to every browserless test, since {@code scrollHeight} and
     * {@code clientHeight} are both browser facts. In a real Swing app the
     * height comes from the enclosing layout and frame; the Swing lane's
     * {@code ScrollPanesPanel} gets it from the route-sizing chain (D_inline_route_sizing) via
     * a flex-fill wrap. A fixed height is used here instead because this pane
     * puts two panes side by side and needs the overflow to be deterministic
     * for the exit gate. Reaching to peer CSS from a Sampler pane follows
     * {@code ScrollPanesPanel.applyFlexFill}'s precedent; {@code
     * peerContentElement()} is an SB-Emulators-invented accessor and so R_no_vaadin_in_api-limb-1 clean.
     */
    private static void bound(ScrollPane sp) {
        sp.peerContentElement().getStyle().set("height", "260px");
    }

    /**
     * The scrolled child is a Swing {@code JPanel} of AWT {@code Label}s —
     * legal, since {@code add} takes any {@code vaadinx.awt.Component}, and it
     * doubles as an AWT-inside-Swing-inside-AWT residue demo.
     *
     * <p>Note this is exactly the case where the JDK would have wrapped the
     * child in a {@code java.awt.Panel} (a lightweight child goes through
     * {@code addToPanel}), and where we deliberately do not — so
     * {@code getComponent(0)} here is the JPanel itself. See D_awt_scrollpane.
     */
    private static JPanel tallStack(String prefix) {
        JPanel stack = new JPanel(new vaadinx.awt.GridLayout(0, 1, 0, 2));
        stack.setBackground(TINT);
        for (int i = 1; i <= 40; i++) {
            stack.add(new Label(prefix + " " + i, Label.LEFT));
        }
        return stack;
    }

    private JPanel scrollingDemo() {
        live.add(tallStack("line"));
        bound(live);

        // Fires for the buttons below; silent for a mouse wheel. The
        // browser->server scroll channel is descoped per D_awt_scrollpane, so a gesture
        // never reaches the server.
        live.getVAdjustable().addAdjustmentListener(e ->
                readout.setText("AdjustmentEvent value=" + e.getValue()
                        + " — scrollPosition = " + fmt(live.getScrollPosition())
                        + " — getComponentCount() = " + live.getComponentCount()));

        JButton down = new JButton("Scroll to 200");
        down.addActionListener(e -> live.setScrollPosition(0, 200));
        JButton top = new JButton("Scroll to top");
        top.addActionListener(e -> live.setScrollPosition(0, 0));
        JButton replace = new JButton("Replace child");
        replace.addActionListener(e -> {
            live.add(tallStack("gen" + (++generation) + " line"));
            readout.setText("replaced — getComponentCount() = " + live.getComponentCount()
                    + " (still 1) — scrollPosition = " + fmt(live.getScrollPosition()));
        });

        JPanel controls = new JPanel();
        controls.add(down);
        controls.add(top);
        controls.add(replace);

        JLabel caveat = new JLabel("Note: the AdjustmentListener fires for these buttons, "
                + "not for a mouse wheel — user scrolls are not reported to the server (D_awt_scrollpane).");

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(controls, BorderLayout.NORTH);
        body.add(live, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(0, 2));
        footer.add(readout, BorderLayout.NORTH);
        footer.add(caveat, BorderLayout.SOUTH);
        body.add(footer, BorderLayout.SOUTH);
        return body;
    }

    private static String fmt(Point p) {
        return "(" + p.x + "," + p.y + ")";
    }

    private static JPanel captioned(ScrollPane sp, String caption) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        wrap.add(new JLabel(caption), BorderLayout.NORTH);
        wrap.add(sp, BorderLayout.CENTER);
        return wrap;
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
