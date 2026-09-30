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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.JButton;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;
import vaadinx.swing.InputVerifier;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Form panel + plumbing exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code onUnsupported} /
 * {@code onUnsupportedPeerShape} fires during the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_form_user_path} — {@link FormPanel} driven
 *       end-to-end via the Sampler shell (navigate to {@link SamplerRoute}
 *       which mounts {@link SamplerFrame} + {@link HomePanel}, click the
 *       "Form" nav target to swap FormPanel in, then run field edits /
 *       checkbox toggles / save). Regression guard for the "normal user
 *       operation" exit criterion: any new stub call during form use
 *       trips the test.</li>
 *   <li>{@link #inventory_plumbing_api_surface} — a micro-driver over
 *       the four plumbing buckets (Action / Border / Focus /
 *       JRootPane) on an isolated fixture. Guards against reintroducing
 *       stubs into setAction, registerKeyboardAction, Input/ActionMap,
 *       setBorder (+ every BorderFactory product), requestFocus
 *       variants, InputVerifier, setFocusTraversalKeys, and the
 *       root/layered/glass-pane accessors.</li>
 * </ol>
 *
 * <p>On success each {@link #dump} step still prints a ledger line for
 * the record — useful when the test fails under CI so the break is
 * trivially located. On failure, {@code dump} throws an
 * {@link AssertionError} naming the step.
 */
class FormWarnInventoryTest {

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
    void inventory_form_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;

        // Navigate to the Sampler shell — constructs SamplerFrame +
        // HomePanel. Hook is attached before navigation, so any shell-
        // construction WARNs are captured here.
        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        // Navigate to FormPanel — expands the "Forms" group, then clicks
        // the leaf, which swaps the panel into the content area.
        Navigate.to("Form");
        dump("Step 0b (FormPanel swap)", warnings);

        // Step 1: locate each peer. Lookups themselves shouldn't fire
        // anything, but we capture here so later steps start from empty.
        TextField name = LocatorJ._get(TextField.class);
        PasswordField password = LocatorJ._get(PasswordField.class);
        TextArea notes = LocatorJ._get(TextArea.class);
        Checkbox terms = LocatorJ._get(Checkbox.class, spec -> spec.withLabel("I accept the terms"));
        Button save = LocatorJ._get(Button.class, spec -> spec.withText("Save"));
        dump("Step 1 (lookups)", warnings);

        // _setValue / _click drive isFromClient=true (matching real user
        // input) and assert visibility+enabled along the way — both
        // semantically more accurate than raw HasValue.setValue() /
        // ClickNotifier.click() for a "user path" exit gate.
        LocatorJ._setValue(name, "Ada");
        dump("Step 2 (name.setValue)", warnings);

        LocatorJ._setValue(password, "hunter2");
        dump("Step 3 (password.setValue)", warnings);

        LocatorJ._setValue(notes, "Hello there");
        dump("Step 4 (notes.setValue)", warnings);

        LocatorJ._setValue(terms, true);
        dump("Step 5 (terms accepted)", warnings);

        LocatorJ._click(save);
        dump("Step 6 (save.click)", warnings);

        LocatorJ._setValue(terms, false);
        dump("Step 7 (terms revoked)", warnings);

        WarnDump.println();
        WarnDump.println("=== form user-path WARN total: " + warnings.size()
                + " ===");
    }

    @Test
    void inventory_plumbing_api_surface() {
        // Micro-driver for the plumbing buckets (Action / Border /
        // Focus / JRootPane). Uses a bare JFrame + JButton + JPanel +
        // JTextField since FormView doesn't exercise these APIs end-to-end
        // on every revision; this method is the per-bucket visibility tool.

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        JFrame frame = new JFrame("driver");
        JPanel panel = new JPanel();
        JButton button = new JButton("click");
        JTextField field = new JTextField(10);
        frame.add(panel);
        panel.add(field);
        panel.add(button);
        frame.setVisible(true);
        dump("Fixture setup", warnings);

        // -- Bucket 4a: Action, mnemonic, keybindings --

        Action action = new AbstractAction("Hello") {
            @Override public void actionPerformed(ActionEvent e) {}
        };
        action.putValue(Action.MNEMONIC_KEY, KeyEvent.VK_H);
        action.putValue(Action.SHORT_DESCRIPTION, "tooltip");
        action.putValue(Action.ACTION_COMMAND_KEY, "do-it");
        button.setAction(action);
        dump("4a  button.setAction", warnings);

        // setMnemonic(int) is already wired (stores field, fires PCE) but
        // doesn't install a real Alt+H accelerator — record silent state
        // so the ledger shows whether 4a-2 covers the browser-side key
        // binding or leaves it as an accepted-gap.
        button.setMnemonic(KeyEvent.VK_H);
        dump("4a  button.setMnemonic", warnings);

        // JComponent-level keybinding API. 2 = JComponent.WHEN_IN_FOCUSED_WINDOW.
        button.registerKeyboardAction(
                e -> {},
                KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK),
                /* JComponent.WHEN_IN_FOCUSED_WINDOW */ 2);
        dump("4a  button.registerKeyboardAction", warnings);

        button.getInputMap();
        button.getInputMap(/* WHEN_FOCUSED */ 0);
        button.getActionMap();
        dump("4a  button.getInputMap/getActionMap", warnings);

        button.setInputMap(0, new javax.swing.InputMap());
        button.setActionMap(new javax.swing.ActionMap());
        dump("4a  button.setInputMap/setActionMap", warnings);

        // -- Bucket 4b: Borders (concrete BorderFactory surface) --

        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        dump("4b  panel.setBorder(empty)", warnings);

        panel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        dump("4b  panel.setBorder(line)", warnings);

        panel.setBorder(BorderFactory.createTitledBorder("Title"));
        dump("4b  panel.setBorder(titled)", warnings);

        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.BLACK),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        dump("4b  panel.setBorder(compound)", warnings);

        panel.getBorder();
        dump("4b  panel.getBorder", warnings);

        // -- Bucket 4c: Focus + InputVerifier --

        field.requestFocus();
        dump("4c  field.requestFocus", warnings);

        field.requestFocusInWindow();
        dump("4c  field.requestFocusInWindow", warnings);

        field.grabFocus();
        dump("4c  field.grabFocus", warnings);

        InputVerifier iv = new InputVerifier() {
            @Override public boolean verify(vaadinx.swing.JComponent input) { return true; }
        };
        field.setInputVerifier(iv);
        field.getInputVerifier();
        dump("4c  field.setInputVerifier/getInputVerifier", warnings);

        field.setFocusTraversalKeys(0, Collections.emptySet());
        dump("4c  field.setFocusTraversalKeys", warnings);

        // -- Bucket 4d: JRootPane / layered pane / glass pane --

        frame.getRootPane();
        dump("4d  frame.getRootPane", warnings);

        frame.getLayeredPane();
        dump("4d  frame.getLayeredPane", warnings);

        frame.getGlassPane();
        dump("4d  frame.getGlassPane", warnings);

        // JComponent.getRootPane too — different code path.
        button.getRootPane();
        dump("4d  button.getRootPane", warnings);

        WarnDump.println();
        WarnDump.println("=== plumbing API-surface WARN total across buckets above ===");
    }

    /**
     * Print the per-step ledger line (always, so the record survives
     * even when the test passes), drain {@code warnings}, then fail if
     * the step emitted any stub WARN. The {@link AssertionError}
     * message carries both the banner (locates the regression) and the
     * list of WARNs (tells you which stubs fired), so fixing the break
     * is a straight-shot.
     */
    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the form/plumbing exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
