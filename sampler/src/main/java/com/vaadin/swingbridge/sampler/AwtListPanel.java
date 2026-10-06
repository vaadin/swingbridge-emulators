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
import vaadinx.awt.List;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JComponent;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.awt.event.ItemEvent;
import java.util.Arrays;

/**
 * {@link vaadinx.awt.List} — the AWT 1.0 scrolling list box, not
 * {@link vaadinx.swing.JList}. Decisions: {@code D_awt_list} / {@code SD_slist}; the
 * lane's rationale: {@code D_awt_lane}.
 *
 * <ol>
 *   <li><b>Single mode, and the two event shapes</b> — an {@code ItemEvent}
 *       readout proving {@code getItem()} is the {@code Integer} index, an
 *       {@code ActionEvent} readout fed by double-click, and a button proving
 *       {@code select()} fires nothing while the state readout moves.</li>
 *   <li><b>Multiple mode, and {@code getSelectedIndex()}'s sharpest
 *       surprise</b> — the singular and plural reads side by side, the former
 *       collapsing to {@code -1} the moment two rows are selected.</li>
 *   <li><b>Mutators</b> — the coercions and the index shifting, over a list
 *       carrying a deliberate duplicate.</li>
 * </ol>
 *
 * <p>Note the unqualified {@code List} in this file is
 * {@code vaadinx.awt.List}, so {@code java.util} collections here are
 * fully qualified.
 */
public class AwtListPanel extends JPanel {

    public AwtListPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(demoSection("Demo 1 — single mode: ItemEvent carries the INDEX, "
                + "ActionEvent carries the TEXT, and select() fires neither", singleMode()));
        add(demoSection("Demo 2 — multiple mode: getSelectedIndex() is -1 whenever "
                + "two rows are selected", multipleMode()));
        add(demoSection("Demo 3 — mutators: null becomes \"\", a bad index appends, "
                + "and the selection shifts with the rows", mutators()));
    }

    /**
     * The two event shapes and the silent-select contract. The
     * {@code select(2)} button is the load-bearing one: its readout must
     * <em>not</em> move, which is what the exit gate asserts.
     */
    private static JPanel singleMode() {
        List planets = new List(5, false);
        planets.add("Mercury");
        planets.add("Venus");
        planets.add("Earth");
        planets.add("Mars");
        planets.add("Jupiter");

        JLabel itemReadout = new JLabel("no ItemEvent yet — click a row");
        JLabel actionReadout = new JLabel("no ActionEvent yet — double-click a row");
        JLabel stateReadout = new JLabel(state(planets));

        planets.addItemListener(e ->
                // getItem() is an Integer index here, not the item String —
                // unlike java.awt.Choice, which posts the String.
                itemReadout.setText("ItemEvent: getItem() = " + e.getItem()
                        + " (" + e.getItem().getClass().getSimpleName() + "), stateChange="
                        + (e.getStateChange() == ItemEvent.SELECTED ? "SELECTED" : "DESELECTED")));
        planets.addActionListener(e ->
                actionReadout.setText("ActionEvent: getActionCommand() = " + e.getActionCommand()));

        JButton silentSelect = new JButton("select(2) — the ItemEvent readout must not move");
        silentSelect.addActionListener(e -> {
            planets.select(2);
            stateReadout.setText(state(planets));
        });
        JButton scroll = new JButton("makeVisible(4)");
        scroll.addActionListener(e -> {
            planets.makeVisible(4);
            stateReadout.setText(state(planets) + ", getVisibleIndex() = " + planets.getVisibleIndex());
        });

        JPanel controls = new JPanel();
        controls.add(silentSelect);
        controls.add(scroll);
        JPanel readouts = new JPanel(new GridLayout(3, 1, 0, 2));
        readouts.add(itemReadout);
        readouts.add(actionReadout);
        readouts.add(stateReadout);

        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.add(planets, BorderLayout.NORTH);
        wrap.add(controls, BorderLayout.CENTER);
        wrap.add(readouts, BorderLayout.SOUTH);
        return wrap;
    }

    /**
     * The singular/plural reads side by side. Selecting a second row collapses
     * {@code getSelectedIndex()} to {@code -1} and {@code getSelectedItem()}
     * to {@code null} while the plural getters still see both — one readout
     * teaching the class's sharpest surprise.
     */
    private static JPanel multipleMode() {
        List toppings = new List(4, true);
        toppings.add("Anchovy");
        toppings.add("Basil");
        toppings.add("Caper");
        toppings.add("Dough");

        JLabel readout = new JLabel(state(toppings));
        toppings.addItemListener(e -> readout.setText(state(toppings)));

        JButton selectTwo = new JButton("select(0) + select(2)");
        selectTwo.addActionListener(e -> {
            toppings.select(0);
            toppings.select(2);
            readout.setText(state(toppings));
        });
        JButton toSingle = new JButton("setMultipleMode(false)");
        toSingle.addActionListener(e -> {
            toppings.setMultipleMode(false);
            readout.setText(state(toppings) + ", isMultipleMode() = " + toppings.isMultipleMode());
        });
        JButton toMultiple = new JButton("setMultipleMode(true)");
        toMultiple.addActionListener(e -> {
            toppings.setMultipleMode(true);
            readout.setText(state(toppings) + ", isMultipleMode() = " + toppings.isMultipleMode());
        });

        JPanel controls = new JPanel();
        controls.add(selectTwo);
        controls.add(toSingle);
        controls.add(toMultiple);

        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.add(toppings, BorderLayout.NORTH);
        wrap.add(controls, BorderLayout.CENTER);
        wrap.add(readout, BorderLayout.SOUTH);
        return wrap;
    }

    /** The coercions, the index shifting, and two identical rows on the page. */
    private static JPanel mutators() {
        List items = new List(4, true);
        items.add("Apple");
        // Two equal Strings: distinct rows, independently selectable. The case
        // a Grid peer would have needed SD_sjlist's index identity to survive, and
        // that <option> children get for free.
        items.add("Apple");
        items.add("Cherry");

        JLabel readout = new JLabel(state(items));
        // Without this the readout only refreshes on a button click, so a
        // browser selection leaves it stale and reading a lie — caught in the
        // browser pass, invisible to the server-side gate.
        items.addItemListener(e -> readout.setText(state(items)));

        JButton append = new JButton("add(\"Date\")");
        append.addActionListener(e -> {
            items.add("Date");
            readout.setText(state(items));
        });
        JButton insertHead = new JButton("add(\"New\", 0) — shifts the selection");
        insertHead.addActionListener(e -> {
            items.add("New", 0);
            readout.setText(state(items));
        });
        JButton coerce = new JButton("add(null, 99) — appends \"\"");
        coerce.addActionListener(e -> {
            items.add((String) null, 99);
            readout.setText(state(items));
        });
        JButton replace = new JButton("replaceItem(\"Banana\", 0)");
        replace.addActionListener(e -> {
            if (items.getItemCount() == 0) {
                readout.setText("empty list — nothing to replace");
                return;
            }
            items.replaceItem("Banana", 0);
            readout.setText(state(items));
        });
        JButton removeFirst = new JButton("remove(0)");
        removeFirst.addActionListener(e -> {
            // AWT throws on an out-of-range position, so a real app guards
            // exactly like this rather than relying on a silent no-op.
            if (items.getItemCount() == 0) {
                readout.setText("empty list — nothing to remove");
                return;
            }
            items.remove(0);
            readout.setText(state(items));
        });
        JButton clearAll = new JButton("removeAll()");
        clearAll.addActionListener(e -> {
            items.removeAll();
            readout.setText(state(items));
        });

        JPanel controls = new JPanel();
        controls.add(append);
        controls.add(insertHead);
        controls.add(coerce);
        controls.add(replace);
        controls.add(removeFirst);
        controls.add(clearAll);

        JPanel wrap = new JPanel(new BorderLayout(0, 6));
        wrap.add(items, BorderLayout.NORTH);
        wrap.add(controls, BorderLayout.CENTER);
        wrap.add(readout, BorderLayout.SOUTH);
        return wrap;
    }

    /**
     * The readout every demo shares: the item count plus the three selection
     * reads a migrator compares. {@code getSelectedIndex()} renders as
     * {@code -1} whenever the count is not exactly one, and
     * {@code getSelectedItems()} renders as {@code []} on an empty selection
     * where {@code java.awt.Choice}'s {@code getSelectedObjects()} would
     * render as {@code null}.
     */
    private static String state(List l) {
        return "getItemCount() = " + l.getItemCount()
                + ", getSelectedIndex() = " + l.getSelectedIndex()
                + ", getSelectedItem() = " + l.getSelectedItem()
                + ", getSelectedItems() = " + Arrays.toString(l.getSelectedItems());
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
