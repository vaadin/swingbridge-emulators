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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JFrame;
import vaadinx.swing.JToggleButton;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Buttons view + button-family API surface exit gate (SD_sjbutton + SD_toggle_checkbox_first_cut).
 * Both tests fail if any {@code EHelper.onUnimplemented} fires during
 * the asserted path.
 *
 * <ol>
 *   <li>{@link #inventory_buttons_user_path} — {@link ButtonsPanel}
 *       end-to-end via the Sampler shell: plain ActionListener click,
 *       Action-driven click with NAME PCE round-trip, JCheckBox +
 *       JToggleButton toggles firing ItemEvent fan-out.</li>
 *   <li>{@link #inventory_buttons_api_surface} — micro-driver over the
 *       JToggleButton + JCheckBox + JButton surface
 *       FormWarnInventoryTest does not exercise: the SJToggleButton
 *       (Button peer) / SJCheckBox (Checkbox peer) path, setMnemonic,
 *       doClick, addItemListener / addChangeListener fan-out.</li>
 * </ol>
 */
class ButtonsWarnInventoryTest {

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
    void inventory_buttons_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Buttons");
        dump("Step 0b (ButtonsPanel swap)", warnings);

        Button clickMe = LocatorJ._get(Button.class, spec -> spec.withText("Click me"));
        Button enable = LocatorJ._get(Button.class, spec -> spec.withText("Disable"));
        Checkbox notifyBox = LocatorJ._get(Checkbox.class, spec -> spec.withLabel("Notify on click"));
        // Bold JToggleButton peers over a Vaadin Button (SD_sjtogglebutton_button_peer), not a
        // Checkbox — located by text, driven by click (no boolean value).
        Button boldToggle = LocatorJ._get(Button.class, spec -> spec.withText("Bold"));
        dump("Step 1 (lookups)", warnings);

        // Plain ActionListener — counter flip on the readout label.
        LocatorJ._click(clickMe);
        dump("Step 2 (clickMe.click — ActionListener)", warnings);

        // Action-driven click — toggles clickMe.enabled and re-labels
        // itself via Action.NAME PCE round-trip.
        LocatorJ._click(enable);
        dump("Step 3 (enable.click — Action toggles + NAME PCE)", warnings);

        // JCheckBox ItemListener.
        LocatorJ._setValue(notifyBox, true);
        dump("Step 4 (notifyBox on)", warnings);

        // JToggleButton ItemListener — SJToggleButton on Button peer.
        // A click drives the ButtonModel pulse → Item + Change + Action.
        LocatorJ._click(boldToggle);
        dump("Step 5 (boldToggle on — JToggleButton ItemEvent)", warnings);

        LocatorJ._click(boldToggle);
        dump("Step 6 (boldToggle off)", warnings);

        WarnDump.println();
        WarnDump.println("=== buttons user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_buttons_api_surface() {
        // Micro-driver over the JToggleButton + JCheckBox + JButton API
        // FormWarnInventoryTest doesn't exercise — primarily the
        // SJToggleButton path (Button-backed peer) and AbstractButton
        // surface (doClick, ButtonModel, ItemListener, ChangeListener).

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        dump("Fixture setup", warnings);

        // -- Bucket a: plain JButton + addActionListener path --
        JButton button = new JButton("plain");
        ActionListener al = e -> {};
        button.addActionListener(al);
        button.getActionListeners();
        button.removeActionListener(al);
        dump("a  JButton.addActionListener / get / remove", warnings);

        // -- Bucket b: JToggleButton (SJToggleButton on Button peer) --
        // Note: AbstractButton.getModel() / setModel(ButtonModel) are
        // intentional R_vaadin_first drop-and-WARN on the emulator side (the
        // emulator flattens ButtonModel; the surrogate has its own
        // ButtonModel internally for fan-out but doesn't expose it back
        // through the AbstractButton API). Don't exercise them here.
        JToggleButton toggle = new JToggleButton("toggle");
        toggle.setSelected(true);
        toggle.isSelected();
        ItemListener il = e -> {};
        toggle.addItemListener(il);
        toggle.getItemListeners();
        toggle.removeItemListener(il);
        ChangeListener cl = e -> {};
        toggle.addChangeListener(cl);
        toggle.removeChangeListener(cl);
        dump("b  JToggleButton selection + listeners", warnings);

        // doClick fires the surrogate's Item + Change + Action sequence
        // — locks in the SJToggleButton ButtonModel-driven fan-out path.
        toggle.doClick();
        dump("b  JToggleButton.doClick", warnings);

        // setMnemonic routes through the emulator's own Shortcuts wiring
        // (peer-agnostic) — exercise it on the toggle path too.
        toggle.setMnemonic(KeyEvent.VK_B);
        toggle.getMnemonic();
        dump("b  JToggleButton.setMnemonic", warnings);

        // -- Bucket c: JCheckBox (SJCheckBox — standalone Checkbox peer) --
        JCheckBox check = new JCheckBox("check");
        check.setSelected(true);
        check.doClick();
        check.setMnemonic(KeyEvent.VK_C);
        dump("c  JCheckBox round-trip + doClick + setMnemonic", warnings);

        // -- Bucket d: Action.NAME PCE + enabled propagation on JButton.
        // FormWarnInventoryTest covers setAction once; this exercises
        // the post-install Action mutation propagation path.
        Action action = new AbstractAction("First") {
            @Override public void actionPerformed(ActionEvent e) {}
        };
        JButton actionButton = new JButton(action);
        action.putValue(Action.NAME, "Second");
        actionButton.getText();
        action.setEnabled(false);
        actionButton.isEnabled();
        action.setEnabled(true);
        dump("d  Action.NAME PCE + enabled propagation", warnings);

        frame.setVisible(true);
        dump("e  frame.setVisible(true)", warnings);

        WarnDump.println();
        WarnDump.println("=== buttons API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the buttons exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
