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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.awt.GridLayout;
import vaadinx.swing.GroupLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JSeparator;
import vaadinx.swing.JTextField;
import vaadinx.swing.LayoutStyle.ComponentPlacement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Layouts view + layout-manager API surface exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code onUnsupported} / {@code onUnsupportedPeerShape}
 * fires during the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_layouts_user_path} — the {@link LayoutsPanel} route
 *       driven end-to-end (construction of all seven sub-demos, including the
 *       BorderLayout five-region demo, the {@link GridLayout} keypad and the
 *       {@link GroupLayout} form). Regression
 *       guard for the "normal user operation" exit criterion.</li>
 *   <li>{@link #inventory_layout_api_surface} — a micro-driver over the two new
 *       layout managers on an isolated fixture. {@link GridLayout} (D_gridlayout) and
 *       {@link GroupLayout} + {@code LayoutStyle} (D_grouplayout) build + lay out with no
 *       WARN; guards against reintroducing stubs into the layout surface.</li>
 * </ol>
 */
class LayoutsWarnInventoryTest {

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
    void inventory_layouts_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        // Swap LayoutsPanel in — constructs all seven sub-demos, so BorderLayout,
        // GridLayout and GroupLayout render as part of the user path.
        Navigate.to("Layouts");
        dump("Step 0b (LayoutsPanel swap — all six layout demos built)", warnings);

        WarnDump.println();
        WarnDump.println("=== layouts user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_layout_api_surface() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        JPanel root = new JPanel();
        frame.add(root);
        dump("Fixture setup", warnings);

        // -- GridLayout (D_gridlayout): equal-cell grid, dimensions from child count --

        JPanel keypad = new JPanel(new GridLayout(3, 3, 4, 4));
        for (int i = 1; i <= 9; i++) {
            keypad.add(new JButton(Integer.toString(i)));
        }
        root.add(keypad);
        dump("GridLayout(3,3,4,4) + 9 buttons", warnings);

        // Column-derived variant (rows == 0 → columns fixed).
        JPanel colFixed = new JPanel(new GridLayout(0, 2));
        colFixed.add(new JLabel("a"));
        colFixed.add(new JLabel("b"));
        colFixed.add(new JLabel("c"));
        root.add(colFixed);
        dump("GridLayout(0,2) + 3 labels (rows derived)", warnings);

        // -- GroupLayout (D_grouplayout): full NetBeans free-design builder + render --

        JPanel form = new JPanel();
        GroupLayout gl = new GroupLayout(form);
        form.setLayout(gl);
        gl.setAutoCreateGaps(true);
        gl.setAutoCreateContainerGaps(true);

        JLabel nameLabel = new JLabel("Name:");
        JTextField nameField = new JTextField(16);
        JLabel emailLabel = new JLabel("Email:");
        JTextField emailField = new JTextField(16);
        JSeparator separator = new JSeparator();
        JButton saveButton = new JButton("Save");

        gl.setHorizontalGroup(
            gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                .addComponent(separator)
                .addGroup(gl.createSequentialGroup()
                    .addGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(nameLabel)
                        .addComponent(emailLabel))
                    .addPreferredGap(ComponentPlacement.RELATED)
                    .addGroup(gl.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(nameField)
                        .addComponent(emailField)))
                .addComponent(saveButton, GroupLayout.Alignment.TRAILING)
        );
        gl.setVerticalGroup(
            gl.createSequentialGroup()
                .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(nameLabel)
                    .addComponent(nameField))
                .addPreferredGap(ComponentPlacement.RELATED)
                .addGroup(gl.createParallelGroup(GroupLayout.Alignment.BASELINE)
                    .addComponent(emailLabel)
                    .addComponent(emailField))
                .addPreferredGap(ComponentPlacement.UNRELATED)
                .addComponent(separator)
                .addPreferredGap(ComponentPlacement.RELATED)
                .addComponent(saveButton)
        );
        root.add(form);
        dump("GroupLayout free-design form (build + auto-add)", warnings);

        // Attach the whole tree — triggers validate → layoutContainer on every
        // layout, which is where the CSS-grid reconstruction runs.
        frame.setVisible(true);
        dump("frame.setVisible(true) — layoutContainer pass on all layouts", warnings);

        WarnDump.println();
        WarnDump.println("=== layout API-surface WARN total across buckets above ===");
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the layouts exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
