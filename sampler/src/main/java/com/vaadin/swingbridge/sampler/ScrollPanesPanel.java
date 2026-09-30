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
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JTable;
import vaadinx.swing.JTextArea;

import javax.swing.table.AbstractTableModel;

/**
 * JScrollPane demo. Three sub-demos stacked vertically
 * via {@link BoxLayout#Y_AXIS}; each demo is sized so its inner
 * JScrollPane / Vaadin auto-scroll surface actually has overflowing
 * content to scroll:
 *
 * <ol>
 *   <li><b>Tall labels</b> — JScrollPane around a JPanel of 40
 *       label rows under {@link BoxLayout#Y_AXIS}. Plain Div-shaped
 *       content; the auto-scroll guard does NOT fire, so the Scroller's
 *       BOTH direction stands and overflow:auto on the host scroller is
 *       the actual rendering. Demo wrap takes {@code flex-grow:1} so the
 *       scroller has a bounded viewport that the 40-row stack overflows.</li>
 *   <li><b>Multi-line text</b> — JScrollPane around a JTextArea seeded
 *       with enough text (12 paragraphs) to overflow the textarea's
 *       intrinsic height. Vaadin TextArea owns its own overflow:auto
 *       layer; the auto-scroll guard forces NONE on the outer Scroller
 *       per SD_sjscrollpane, so only the inner textarea scrolls (no double
 *       scrollbars). Demo wrap stays auto-height — the textarea displays
 *       at its rows×cols intrinsic size.</li>
 *   <li><b>JTable</b> — JScrollPane around a JTable with 50 rows. Vaadin
 *       Grid (the SJTable peer) owns its own scrolling; the auto-scroll
 *       guard forces NONE on the outer Scroller. Demo wrap takes
 *       {@code flex-grow:1} so the Grid expands into the remaining
 *       column height, exercising the D_inline_route_sizing route-sizing chain end-to-end
 *       (route setSizeFull → SJPanel fills route → contentPane fills
 *       SJPanel → flex column → JScrollPane → Grid scrolls).</li>
 * </ol>
 *
 * <p>Demo 1 and Demo 3 wraps each get {@code flex-grow:1} written
 * directly to their peer element so the master {@link BoxLayout#Y_AXIS}
 * column distributes leftover height between them. Demo 2 stays at flex
 * default ({@code 0 0 auto}, intrinsic) — its scrolling is driven by
 * the textarea's own internal overflow, not the outer column height.
 */
public class ScrollPanesPanel extends JPanel {

    public ScrollPanesPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Demo 1: tall labels in a JScrollPane (non-auto-scrolling content).
        JScrollPane tallScroll = new JScrollPane(buildLabelStack(40));

        // Demo 2: JTextArea inside a JScrollPane (auto-scroll guard fires).
        // Seed enough wrapped text to overflow the textarea's intrinsic
        // height so the inner Vaadin TextArea actually shows a scrollbar.
        JTextArea textArea = new JTextArea(8, 40);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setText(buildLongText(12));
        JScrollPane textScroll = new JScrollPane(textArea);

        // Demo 3: JTable inside a JScrollPane.
        JTable table = new JTable(new SmallTableModel());
        JScrollPane tableScroll = new JScrollPane(table);

        // Three demos stacked under the master Y_AXIS BoxLayout. Vertical
        // struts carry the inter-demo gap. All three wraps get
        // flex-grow:1 + flex-basis:0 + min-height:0 + align-self:stretch
        // so the master column distributes its bounded height equally
        // across them. Without flex-basis:0 the textarea's intrinsic
        // ~12-paragraph height would otherwise dominate the column,
        // shrinking demos 1 and 3 to zero. Each wrap's inner Vaadin peer
        // (Scroller / Grid / TextArea) handles overflow within its share.
        JPanel demo1Wrap = demoSection(
                "Demo 1 — JScrollPane around a tall JPanel of labels (default BOTH direction)",
                tallScroll);
        applyFlexFill(demo1Wrap);
        add(demo1Wrap);
        add(Box.createVerticalStrut(8));

        JPanel demo2Wrap = demoSection(
                "Demo 2 — JScrollPane around a JTextArea (auto-scroll guard forces NONE on outer)",
                textScroll);
        applyFlexFill(demo2Wrap);
        add(demo2Wrap);
        add(Box.createVerticalStrut(8));

        JPanel demo3Wrap = demoSection(
                "Demo 3 — JScrollPane around a JTable (auto-scroll guard; Grid owns scroll)",
                tableScroll);
        applyFlexFill(demo3Wrap);
        add(demo3Wrap);
    }

    /**
     * Mark a demo wrap as a stretch-fill flex child of the master
     * BoxLayout column: equal share of the column's bounded height
     * (flex-grow:1 + flex-basis:0), stretch across width, and allow
     * shrinking below intrinsic min-content (otherwise an inner
     * JScrollPane → tall content / textarea / Grid forces the wrap
     * past its flex share, defeating the height chain).
     */
    private static void applyFlexFill(JPanel wrap) {
        wrap.peerContentElement().getStyle().set("flex", "1 1 0");
        wrap.peerContentElement().getStyle().set("align-self", "stretch");
        wrap.peerContentElement().getStyle().set("min-height", "0");
    }

    /**
     * Build a labelled demo section. BorderLayout(NORTH=label, CENTER=body).
     */
    private static JPanel demoSection(String labelText, JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.add(label(labelText), BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }

    /**
     * {@code count} vertically-stacked JLabels under {@link BoxLayout#Y_AXIS}.
     */
    private static JPanel buildLabelStack(int count) {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (int i = 1; i <= count; i++) {
            stack.add(new JLabel("Row " + i + " — JScrollPane content row"));
            if (i < count) {
                stack.add(Box.createVerticalStrut(2));
            }
        }
        return stack;
    }

    private static String buildLongText(int paragraphs) {
        String para = "Wrap a JTextArea in a JScrollPane. Vaadin TextArea "
                + "already auto-scrolls when its content overflows, so SJScrollPane's "
                + "auto-scroll guard forces ScrollDirection.NONE on the outer Scroller per "
                + "SD_sjscrollpane. Type more text — only the inner textarea scrolls; no double "
                + "scrollbars stack on top of each other.";
        StringBuilder sb = new StringBuilder(para.length() * paragraphs + paragraphs * 2);
        for (int i = 0; i < paragraphs; i++) {
            if (i > 0) sb.append("\n\n");
            sb.append(para);
        }
        return sb.toString();
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    /**
     * TableModel used by demo 3. Three columns, fifty rows — enough to
     * overflow the Grid viewport so the SJTable peer's own scrolling
     * actually demos.
     */
    static final class SmallTableModel extends AbstractTableModel {
        private static final String[] COLS = { "Name", "Role", "Level" };
        private static final String[] FIRST = {
                "Ada", "Alan", "Grace", "Guest", "Edsger", "Donald", "Ken", "Dennis",
                "Linus", "Margaret", "Barbara", "Tony", "Niklaus", "John", "Bjarne",
                "Anders", "Brendan", "Yukihiro", "Guido", "James", "Rasmus", "Larry",
                "Bram", "Richard", "Brian", "Rob", "Doug", "Joshua", "Martin", "Eric"
        };
        private static final String[] LAST = {
                "Lovelace", "Turing", "Hopper", "Account", "Dijkstra", "Knuth",
                "Thompson", "Ritchie", "Torvalds", "Hamilton", "Liskov", "Hoare",
                "Wirth", "Backus", "Stroustrup", "Hejlsberg", "Eich", "Matsumoto",
                "van Rossum", "Gosling", "Lerdorf", "Wall", "Moolenaar", "Stallman",
                "Kernighan", "Pike", "Lea", "Bloch", "Fowler", "Raymond"
        };
        private static final String[] ROLES = { "ADMIN", "USER", "GUEST" };
        private static final int ROW_COUNT = 50;

        @Override public int getRowCount() { return ROW_COUNT; }
        @Override public int getColumnCount() { return COLS.length; }
        @Override public String getColumnName(int c) { return COLS[c]; }
        @Override public Class<?> getColumnClass(int c) { return c == 2 ? Integer.class : String.class; }
        @Override public Object getValueAt(int r, int c) {
            return switch (c) {
                case 0 -> FIRST[r % FIRST.length] + " " + LAST[(r * 7) % LAST.length];
                case 1 -> ROLES[r % ROLES.length];
                case 2 -> 1 + ((r * 13) % 10);
                default -> "";
            };
        }
    }
}
