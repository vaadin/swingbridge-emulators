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
import com.vaadin.swingbridge.surrogates.SJRadioButton;
import com.vaadin.swingbridge.surrogates.VaadinRadioButton;
import vaadinx.EHelper;
import vaadinx.swing.ButtonGroup;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JRadioButton;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.SwingConstants;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link JRadioButton} WARN inventory exit gate. Both
 * tests fail if any {@code EHelper.onUnimplemented} fires along the
 * asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — navigates to the Sampler shell,
 *       clicks "RadioButtons", asserts the three demos construct
 *       WARN-free, then exercises the headline D_abstractbutton_mixin_dispatch behavioural unlock:
 *       toggling members of the mixed JCheckBox+JRadioButton group sees
 *       the mutex cascade run through both peer types without any stub
 *       fire.</li>
 *   <li>{@link #inventory_jradiobutton_api_surface} — micro-driver over
 *       the JRadioButton / SJRadioButton API buckets. R_leaf_peer_lockdown-locked-down
 *       leaf so the (Component peer) ctor is intentionally absent —
 *       inventory walks every public ctor + the supported state surface
 *       to lock in WARN-free coverage.</li>
 * </ol>
 *
 * <p>Both surrogate and emulator warnHooks are wired to a single sink so
 * a stub fire from either layer surfaces.
 */
class RadioButtonsWarnInventoryTest {

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
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("RadioButtons");
        dump("Step 0b (RadioButtonsPanel swap)", warnings);

        // Five SJRadioButton peers expected (Demo 1: 1; Demo 2: 3; Demo 3: 2).
        // Pinned explicitly so a future refactor that accidentally drops a
        // demo trips this gate.
        LocatorJ._assert(SJRadioButton.class, 6);
        dump("Step 1 (6 SJRadioButton peers found)", warnings);

        // Demo 2 — group of three. Medium is pre-selected in the ctor.
        // Click "High" via the peer toggle (browser-click equivalent) and
        // assert the cascade: High becomes selected, Medium becomes
        // deselected. Both via the emulator's own model, which the peer
        // pulses (D_emulator_button_model).
        SJRadioButton highPeer = LocatorJ._get(SJRadioButton.class,
                spec -> spec.withPredicate(r -> "High".equals(r.getText())));
        SJRadioButton mediumPeer = LocatorJ._get(SJRadioButton.class,
                spec -> spec.withPredicate(r -> "Medium".equals(r.getText())));
        highPeer.setSelected(true);
        if (!highPeer.isSelected()) {
            throw new AssertionError("High must be selected after click");
        }
        if (mediumPeer.isSelected()) {
            throw new AssertionError("Medium must be deselected after High click (group cascade)");
        }
        dump("Step 2 (priority group cascade: High selected, Medium deselected)", warnings);

        // Demo 3 — mixed JCheckBox + JRadioButton. The D_abstractbutton_mixin_dispatch headline:
        // toggling the JRadioButton "Auto" must deselect the JCheckBox
        // "Off" (which starts unselected anyway, so we pre-arm it first).
        // Then toggle "Manual" and assert "Auto" deselects — JRadioButton
        // → JRadioButton cascade.
        SJRadioButton autoPeer = LocatorJ._get(SJRadioButton.class,
                spec -> spec.withPredicate(r -> "Auto".equals(r.getText())));
        SJRadioButton manualPeer = LocatorJ._get(SJRadioButton.class,
                spec -> spec.withPredicate(r -> "Manual".equals(r.getText())));

        // Pre-arm: select the JCheckBox "Off" so the mixed cascade has
        // something to deselect.
        com.vaadin.flow.component.checkbox.Checkbox offCheckbox = LocatorJ._get(
                com.vaadin.flow.component.checkbox.Checkbox.class,
                spec -> spec.withLabel("Off"));
        offCheckbox.setValue(true);
        if (!offCheckbox.getValue()) {
            throw new AssertionError("Off must be selected before mixed-cascade check");
        }
        dump("Step 3 (Off pre-armed)", warnings);

        // Toggle Auto — D_abstractbutton_mixin_dispatch headline: the JCheckBox "Off" must deselect.
        autoPeer.setSelected(true);
        if (!autoPeer.isSelected()) {
            throw new AssertionError("Auto must be selected after toggle");
        }
        if (offCheckbox.getValue()) {
            throw new AssertionError(
                    "JCheckBox 'Off' must be deselected by the JRadioButton group cascade "
                            + "(D_abstractbutton_mixin_dispatch mixin-generalised mutex)");
        }
        dump("Step 4 (mixed cascade: Auto selected, Off deselected)", warnings);

        // Toggle Manual — JRadioButton → JRadioButton cascade in the same group.
        manualPeer.setSelected(true);
        if (!manualPeer.isSelected()) {
            throw new AssertionError("Manual must be selected after toggle");
        }
        if (autoPeer.isSelected()) {
            throw new AssertionError("Auto must be deselected by Manual click");
        }
        dump("Step 5 (mixed cascade: Manual selected, Auto deselected)", warnings);

        WarnDump.println();
        WarnDump.println("=== radiobuttons user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jradiobutton_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> sink = warnings::add;
        EHelper.warnHook = sink;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = sink;

        // -- Ctors (all 8 JDK ctors). Icon-bearing ctors pass null since
        //    a non-null Icon would WARN via the AbstractButtonMixin's
        //    non-Button else branch (Vaadin Checkbox / VaadinRadioButton
        //    expose no icon slot — per-class unit tests cover that path).
        new JRadioButton();
        dump("JRadioButton  new JRadioButton()", warnings);

        new JRadioButton((vaadinx.swing.Icon) null);
        dump("JRadioButton  new JRadioButton(Icon=null)", warnings);

        new JRadioButton((vaadinx.swing.Icon) null, true);
        dump("JRadioButton  new JRadioButton(Icon=null, selected)", warnings);

        new JRadioButton("Yes");
        dump("JRadioButton  new JRadioButton(String)", warnings);

        new JRadioButton("Yes", true);
        dump("JRadioButton  new JRadioButton(String, selected)", warnings);

        new JRadioButton("Yes", null);
        dump("JRadioButton  new JRadioButton(String, Icon=null)", warnings);

        new JRadioButton("Yes", null, true);
        dump("JRadioButton  new JRadioButton(String, Icon=null, selected)", warnings);

        Action action = new AbstractAction("Action label") {
            @Override
            public void actionPerformed(ActionEvent e) {}
        };
        new JRadioButton(action);
        dump("JRadioButton  new JRadioButton(Action)", warnings);

        // -- Selection round-trip via the surrogate ButtonModel pulse --
        JRadioButton rb = new JRadioButton("Pick");
        rb.setSelected(true);
        rb.isSelected();
        rb.setSelected(false);
        dump("JRadioButton  setSelected round-trip", warnings);

        // -- doClick (full Item + Change + Action via ToggleButtonModel) --
        rb.doClick();
        rb.doClick(50);
        dump("JRadioButton  doClick round-trip", warnings);

        // -- Text round-trip via HasLabel (D_abstractbutton_mixin_dispatch) --
        rb.setText("renamed");
        rb.getText();
        dump("JRadioButton  setText / getText round-trip", warnings);

        // -- actionCommand round-trip --
        rb.setActionCommand("pick-cmd");
        rb.getActionCommand();
        dump("JRadioButton  actionCommand round-trip", warnings);

        // -- Listener registration (add + get + remove for each family) --
        java.awt.event.ItemListener il = e -> {};
        rb.addItemListener(il);
        rb.getItemListeners();
        rb.removeItemListener(il);

        java.awt.event.ActionListener al = e -> {};
        rb.addActionListener(al);
        rb.getActionListeners();
        rb.removeActionListener(al);

        javax.swing.event.ChangeListener cl = e -> {};
        rb.addChangeListener(cl);
        rb.getChangeListeners();
        rb.removeChangeListener(cl);
        dump("JRadioButton  Item/Action/Change listener add+get+remove", warnings);

        // -- ButtonGroup membership (D_buttongroup + D_abstractbutton_mixin_dispatch) --
        JRadioButton a = new JRadioButton("A");
        JRadioButton b = new JRadioButton("B");
        ButtonGroup g = new ButtonGroup();
        g.add(a);
        g.add(b);
        a.setSelected(true);
        b.setSelected(true); // cascade
        g.clearSelection();
        g.remove(a);
        dump("JRadioButton  ButtonGroup integration round-trip", warnings);

        // -- Mixed JCheckBox + JRadioButton group — the D_abstractbutton_mixin_dispatch unlock --
        JRadioButton mixedA = new JRadioButton("A");
        JCheckBox mixedB = new JCheckBox("B");
        ButtonGroup mixed = new ButtonGroup();
        mixed.add(mixedA);
        mixed.add(mixedB);
        mixedA.setSelected(true);
        mixedB.setSelected(true); // cascade across types
        dump("JRadioButton  Mixed JCheckBox+JRadioButton ButtonGroup (D_abstractbutton_mixin_dispatch)", warnings);

        // -- Enabled round-trip --
        rb.setEnabled(false);
        rb.isEnabled();
        rb.setEnabled(true);
        dump("JRadioButton  setEnabled round-trip", warnings);

        // -- L&F surface --
        rb.getUIClassID();
        rb.updateUI();
        dump("JRadioButton  L&F stubs", warnings);

        // -- Alignment / text-position (round-trip the JDK defaults — non-defaults
        //    are R_match_swing_errors IAE for bad keys / R_vaadin_first drop-and-WARN for non-default values
        //    covered in JRadioButtonTest, not the inventory gate).
        rb.setHorizontalAlignment(SwingConstants.CENTER);
        rb.getHorizontalAlignment();
        rb.setVerticalAlignment(SwingConstants.CENTER);
        rb.getVerticalAlignment();
        rb.setHorizontalTextPosition(SwingConstants.TRAILING);
        rb.getHorizontalTextPosition();
        rb.setVerticalTextPosition(SwingConstants.CENTER);
        rb.getVerticalTextPosition();
        rb.setIconTextGap(4);
        rb.getIconTextGap();
        dump("JRadioButton  alignment/text-position default round-trip", warnings);

        // -- Peer type assertions (post-R_leaf_peer_lockdown lock-down) --
        if (!(rb.getPeer() instanceof SJRadioButton)) {
            throw new AssertionError("Peer must be SJRadioButton per R_leaf_peer_lockdown lock-down");
        }
        if (!(rb.getPeer() instanceof VaadinRadioButton)) {
            throw new AssertionError("Peer must be VaadinRadioButton (SJRadioButton IS-A VaadinRadioButton)");
        }
        dump("JRadioButton  peer type assertions", warnings);

        WarnDump.println();
        WarnDump.println("=== JRadioButton API-surface WARN total across buckets above ===");
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
                    + " stub WARN(s) fired — regression in the RadioButtons exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
