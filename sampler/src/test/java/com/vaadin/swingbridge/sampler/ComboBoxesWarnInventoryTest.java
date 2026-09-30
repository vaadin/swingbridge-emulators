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
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.ComboBoxBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.DefaultListCellRenderer;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

import javax.swing.DefaultComboBoxModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ComboBoxes view + JComboBox API surface exit gate (SD_sjcombobox / D_jcombobox).
 * Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code onUnsupported} /
 * {@code onUnsupportedPeerShape} fires during the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_comboboxes_user_path} — {@link ComboBoxesPanel}
 *       end-to-end via the Sampler shell (construction, non-editable
 *       selection, editable custom-value commit).</li>
 *   <li>{@link #inventory_combo_api_surface} — micro-driver over the
 *       JComboBox API surface on an isolated fixture: ctors, selection,
 *       items mutation, model swap, editable, popup, listeners,
 *       custom renderer.</li>
 * </ol>
 */
class ComboBoxesWarnInventoryTest {

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
    void inventory_comboboxes_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;

        // Navigate to the Sampler shell, then click the ComboBoxes nav
        // button to swap ComboBoxesPanel in.
        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("ComboBoxes");
        dump("Step 0b (ComboBoxesPanel swap)", warnings);

        // Step 1: locate each peer by id. Both peers are SJComboBox via
        // surrogate-first thin-down; LocatorJ resolves them as ComboBox (the
        // Vaadin parent class). By id and not by position: the shell's
        // SamplerJumpTo picker is a JComboBox too, so a positional pick would
        // shift the moment the shell grows another one.
        ComboBox<String> colour = LocatorJ._get(ComboBox.class, spec -> spec.withId("combo-colour"));
        ComboBox<String> browser = LocatorJ._get(ComboBox.class, spec -> spec.withId("combo-browser"));
        dump("Step 1 (lookups)", warnings);

        // Step 2: browser-style selection on the non-editable colour
        // combo. Vaadin's setValue with isFromClient=true mirrors a
        // user pick — our addValueChangeListener fires through
        // ComboBoxModel and the view's ItemListener updates the
        // readout.
        LocatorJ._setValue(colour, "blue");
        dump("Step 2 (colour selection to blue)", warnings);

        // Step 3: editable browser combo, custom-value commit. We
        // simulate the customValueSetEvent Vaadin fires when the user
        // types a value not in the items list and presses Enter.
        ComponentUtil.fireEvent(browser,
                new ComboBoxBase.CustomValueSetEvent(browser, true, "Edge"));
        dump("Step 3 (custom value commit)", warnings);

        // Step 4: programmatic selection — server-side write
        // distinct from the user steps above.
        colour.setValue("red");
        browser.setValue("Firefox");
        dump("Step 4 (programmatic selection)", warnings);

        // Step 5: the user opens and closes the lazily filled planet combo, as
        // the browser's opened-changed synchronization does it.
        ComboBox<String> planet = LocatorJ._get(ComboBox.class, spec -> spec.withId("combo-planet"));
        setOpenedFromClient(planet, true);
        setOpenedFromClient(planet, false);
        org.junit.jupiter.api.Assertions.assertEquals(4,
                planet.getListDataView().getItemCount(), "popupMenuWillBecomeVisible filled the model");
        org.junit.jupiter.api.Assertions.assertEquals("popup: opened 1×, closed 1×",
                LocatorJ._get(com.vaadin.flow.component.Component.class,
                        spec -> spec.withId("popup-readout")).getElement().getTextRecursively());
        dump("Step 5 (user opens the lazily filled combo)", warnings);

        WarnDump.println();
        WarnDump.println("=== comboboxes user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_combo_api_surface() {
        // Micro-driver over the JComboBox API. Only supported-surface
        // calls — R_vaadin_first drop-and-WARN methods (ComboBoxEditor /
        // KeySelectionManager / lightWeightPopup / prototypeDisplayValue
        // / selectWithKeyChar) are out of scope for this gate by design.

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        JPanel panel = new JPanel();
        frame.add(panel);
        // Shown, so the combo box below is showing: opening a popup on a combo box
        // that is not needs a screen location, and Swing throws for it
        // (D_return_value_audit) — as bucket 16c's showPopup would discover.
        frame.setVisible(true);
        dump("Fixture setup", warnings);

        // -- Bucket 16a: JComboBox + ComboBoxModel core --

        JComboBox<String> combo = new JComboBox<>(new String[] {"a", "b", "c"});
        panel.add(combo);
        dump("16a  new JComboBox(String[]) + add", warnings);

        combo.setSelectedItem("b");
        combo.getSelectedItem();
        combo.getSelectedIndex();
        combo.setSelectedIndex(2);
        combo.getSelectedObjects();
        dump("16a  selection accessors", warnings);

        combo.addItem("d");
        combo.insertItemAt("a-prime", 0);
        combo.removeItem("a");
        combo.removeItemAt(0);
        combo.getItemAt(0);
        combo.getItemCount();
        dump("16a  items API", warnings);

        combo.getModel();
        combo.setModel(new DefaultComboBoxModel<>(new String[] {"x", "y", "z"}));
        dump("16a  getModel/setModel", warnings);

        // -- Bucket 16b: listeners --

        java.awt.event.ItemListener il = e -> {};
        combo.addItemListener(il);
        combo.getItemListeners();
        combo.removeItemListener(il);
        dump("16b  ItemListener add/get/remove", warnings);

        java.awt.event.ActionListener al = e -> {};
        combo.addActionListener(al);
        combo.getActionListeners();
        combo.removeActionListener(al);
        dump("16b  ActionListener add/get/remove", warnings);

        javax.swing.event.PopupMenuListener pml = new javax.swing.event.PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {}
        };
        combo.addPopupMenuListener(pml);
        combo.getPopupMenuListeners();
        combo.removePopupMenuListener(pml);
        dump("16b  PopupMenuListener add/get/remove", warnings);

        // -- Bucket 16c: editable + popup --

        combo.setEditable(true);
        combo.isEditable();
        combo.setEditable(false);
        dump("16c  editable round-trip", warnings);

        combo.setMaximumRowCount(15);
        combo.getMaximumRowCount();
        dump("16c  maximumRowCount round-trip", warnings);

        combo.setPopupVisible(true);
        combo.isPopupVisible();
        combo.showPopup();
        combo.hidePopup();
        dump("16c  popup show/hide", warnings);

        // -- Bucket 16d: custom renderer (D_jcombobox carve-out) --

        combo.setRenderer(new DefaultListCellRenderer());
        combo.getRenderer();
        dump("16d  default renderer install", warnings);

        // Custom subclass of DefaultListCellRenderer — covers the
        // typical migration pattern.
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public vaadinx.awt.Component getListCellRendererComponent(
                    vaadinx.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                vaadinx.awt.Component out = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                setText("[" + value + "]");
                return out;
            }
        });
        dump("16d  custom DefaultListCellRenderer subclass", warnings);

        // Restoring default rendering (null) — no WARN.
        combo.setRenderer(null);
        dump("16d  setRenderer(null) restore default", warnings);

        // -- Bucket 16e: actionCommand + setAction --

        combo.setActionCommand("myCmd");
        combo.getActionCommand();
        dump("16e  actionCommand round-trip", warnings);

        javax.swing.Action a = new javax.swing.AbstractAction("ignored") {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {}
        };
        a.putValue(javax.swing.Action.SHORT_DESCRIPTION, "tip");
        a.putValue(javax.swing.Action.ACTION_COMMAND_KEY, "doit");
        combo.setAction(a);
        combo.getAction();
        dump("16e  setAction with SHORT_DESCRIPTION + ACTION_COMMAND_KEY", warnings);

        // Frame attach — triggers validate / attach pass through the
        // JComboBox + JPanel ancestry.
        frame.setVisible(true);
        dump("16f  frame.setVisible(true)", warnings);

        // Add some noise to the readout so the test message is visible.
        JLabel readout = new JLabel("readout");
        panel.add(readout);
        dump("16f  add JLabel readout", warnings);

        WarnDump.println();
        WarnDump.println("=== comboboxes API-surface WARN total across buckets above ===");
    }

    private static void setOpenedFromClient(ComboBox<?> combo, boolean opened) {
        try {
            combo.getElement().getNode()
                    .getFeature(com.vaadin.flow.internal.nodefeature.ElementPropertyMap.class)
                    .deferredUpdateFromClient("opened", opened).run();
        } catch (com.vaadin.flow.internal.nodefeature.PropertyChangeDeniedException e) {
            throw new AssertionError(e);
        }
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
                    + " stub WARN(s) fired — regression in the comboboxes exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
