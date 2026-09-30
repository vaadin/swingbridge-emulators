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

import com.vaadin.swingbridge.sampler.SamplerCatalogue.Demo;
import vaadinx.awt.FlowLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import java.util.function.Consumer;

/**
 * Flat quick-picker over the whole {@link SamplerCatalogue} — every demo in one
 * dropdown, typed as {@code "Data › Tables"}, so a known demo is one filter
 * keystroke away instead of an accordion expand plus a click.
 *
 * <pre>{@code
 * SamplerJumpTo jumpTo = new SamplerJumpTo(this::showDemo);
 * jumpTo.select(SamplerCatalogue.byLabel("Tables"));   // silent: fires nothing
 * }</pre>
 *
 * <p>Complements rather than replaces {@link SamplerNav}: the accordion answers
 * "what is in here", this answers "take me to X". Selection flows up through the
 * ctor callback; the composing frame owns the content swap.
 */
public class SamplerJumpTo extends JPanel {

    private static final String SEPARATOR = " › ";

    /**
     * Swing name of the picker's combo, mirrored onto the peer's HTML id — the
     * handle a test uses to tell the shell's combo from a demo panel's own.
     */
    public static final String COMBO_NAME = "nav-jump-to";

    private final JComboBox<String> combo;

    /**
     * Set while {@link #select} pushes a programmatic selection, so the combo's
     * own ActionListener can tell an echo from a real user pick — the standard
     * R_swing_is_truth {@code preventPeerEvents} shape, one level up in user code.
     */
    private boolean syncing;

    public SamplerJumpTo(Consumer<Demo> onSelect) {
        super(new FlowLayout(FlowLayout.LEADING, 8, 4));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        combo = new JComboBox<>(
                SamplerCatalogue.ALL.stream().map(SamplerJumpTo::itemFor).toArray(String[]::new));
        combo.setName(COMBO_NAME);
        combo.addActionListener(e -> {
            if (syncing) {
                return;
            }
            String item = (String) combo.getSelectedItem();
            if (item != null) {
                onSelect.accept(demoFor(item));
            }
        });

        add(new JLabel("Jump to:"));
        add(combo);
    }

    /** Moves the selection to {@code demo} without firing the select callback. */
    public void select(Demo demo) {
        syncing = true;
        try {
            combo.setSelectedItem(itemFor(demo));
        } finally {
            syncing = false;
        }
    }

    private static String itemFor(Demo demo) {
        return demo.category() == null ? demo.label() : demo.category() + SEPARATOR + demo.label();
    }

    private static Demo demoFor(String item) {
        int sep = item.lastIndexOf(SEPARATOR);
        return SamplerCatalogue.byLabel(sep < 0 ? item : item.substring(sep + SEPARATOR.length()));
    }
}
