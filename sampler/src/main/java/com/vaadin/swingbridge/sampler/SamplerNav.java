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

import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JPanel;
import vaadinx.swing.border.Border;
import com.vaadin.swingbridge.sampler.SamplerCatalogue.Demo;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The two-level nav column: one collapsible button group per
 * {@link SamplerCatalogue} category, at most one group open at a time.
 *
 * <pre>{@code
 * SamplerNav nav = new SamplerNav(this::showDemo);   // click → your callback
 * nav.reveal(SamplerCatalogue.byLabel("Tables"));    // open its group, mark it active
 * }</pre>
 *
 * <p>Selection flows *up* through the ctor's callback rather than being handled
 * here — the composing frame owns the content swap and keeps the sibling
 * {@link SamplerJumpTo} in sync.
 *
 * <p>Collapsing hides a group with {@code setVisible(false)}. That is
 * deliberate rather than incidental: lookups skip invisible components, so a
 * test must expand a category before it can click into it, exactly as a user
 * does, and a collapsed leaf can never make a {@code withText} locator
 * ambiguous.
 *
 * <p>The category header's caption carries the disclosure triangle, so it
 * changes as the group toggles. {@link #categoryLabel} is the single
 * source of that string — tests locate headers through it rather than
 * hard-coding the marker.
 */
public class SamplerNav extends JPanel {

    private static final String COLLAPSED = "▸";
    private static final String EXPANDED = "▾";

    /** Border on the button of the currently shown demo. */
    private static final Border ACTIVE = BorderFactory.createLineBorder(Color.GRAY);

    /**
     * Border on every non-active leaf. An empty 1px border rather than
     * {@code null} so a button doesn't shift by a pixel as the active mark moves.
     */
    private static final Border INACTIVE = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    private final Map<String, JButton> headers = new LinkedHashMap<>();
    private final Map<String, JPanel> groups = new LinkedHashMap<>();
    private final Map<String, JButton> leaves = new LinkedHashMap<>();

    public SamplerNav(Consumer<Demo> onSelect) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        for (Demo demo : SamplerCatalogue.ALL) {
            if (demo.category() == null) {
                add(leaf(demo, onSelect));
                continue;
            }
            JPanel group = groups.computeIfAbsent(demo.category(), c -> {
                add(header(c));
                JPanel g = new JPanel();
                g.setLayout(new BoxLayout(g, BoxLayout.Y_AXIS));
                g.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
                g.setVisible(false);
                add(g);
                return g;
            });
            group.add(leaf(demo, onSelect));
        }
        // Glue at the bottom so the groups stack from the top instead of
        // stretching to fill the column when the nav has more vertical room.
        add(Box.createVerticalGlue());
    }

    /**
     * The caption of a category's header button in the given state — the exact
     * string a {@code withText} locator needs.
     */
    public static String categoryLabel(String category, boolean expanded) {
        return (expanded ? EXPANDED : COLLAPSED) + " " + category;
    }

    /**
     * Opens the demo's group (closing any other) and marks its button active.
     *
     * <p>Re-stamps every leaf instead of clearing a remembered
     * previously-active one, so there is no bookkeeping that can go
     * stale — nav callbacks arrive on per-event virtual threads (R_callswing_envelope),
     * where a mutable "last active" field is exactly the kind of state
     * an interleaved call can leave wrong. The full sweep is free
     * anyway: {@code setBorder} short-circuits on an unchanged border
     * and {@code INACTIVE} is a singleton, so every button but this one
     * and the previous one no-ops.
     */
    public void reveal(Demo demo) {
        if (demo.category() != null) {
            expand(demo.category());
        }
        leaves.forEach((label, button) ->
                button.setBorder(label.equals(demo.label()) ? ACTIVE : INACTIVE));
    }

    private JButton header(String category) {
        JButton b = new JButton(categoryLabel(category, false));
        b.addActionListener(e -> {
            if (groups.get(category).isVisible()) {
                collapseAll();
            } else {
                expand(category);
            }
        });
        headers.put(category, b);
        return b;
    }

    private JButton leaf(Demo demo, Consumer<Demo> onSelect) {
        JButton b = new JButton(demo.label());
        b.setBorder(INACTIVE);
        b.addActionListener(e -> onSelect.accept(demo));
        leaves.put(demo.label(), b);
        return b;
    }

    private void expand(String category) {
        groups.forEach((c, group) -> setGroupVisible(c, c.equals(category)));
        revalidate();
        repaint();
    }

    private void collapseAll() {
        groups.keySet().forEach(c -> setGroupVisible(c, false));
        revalidate();
        repaint();
    }

    private void setGroupVisible(String category, boolean visible) {
        groups.get(category).setVisible(visible);
        headers.get(category).setText(categoryLabel(category, visible));
    }
}
