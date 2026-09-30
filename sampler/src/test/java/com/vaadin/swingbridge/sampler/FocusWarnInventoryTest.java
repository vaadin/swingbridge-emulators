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
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.awt.KeyboardFocusManager;
import vaadinx.swing.FocusManager;
import vaadinx.swing.JButton;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextField;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Focus view + focus-manager API surface exit gate (D_focus_managers / SD_focus_tracker). Both tests
 * fail if any {@code EHelper.onUnimplemented} / {@code onUnsupported} /
 * {@code onUnsupportedPeerShape} — or the {@code SHelper} equivalents — fires during the
 * asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_focus_user_path} — {@link FocusPanel} driven
 *       end-to-end: panel swap, the auto-advance {@code PlainDocument} filters
 *       that ask the focus manager who owns focus, the readout its
 *       FocusListeners drive, and each traversal button.</li>
 *   <li>{@link #inventory_focus_api_surface} — a micro-driver over tiers 1 and
 *       2 on an isolated fixture: the two statics, the focus-owner and window
 *       queries, {@code transferFocus} in both directions, and the
 *       focusable flag.</li>
 * </ol>
 *
 * <p>Not covered here, and not coverable here: the UI-wide {@code focusin}
 * bridge that feeds the pointer from the browser, and the {@code blur()} call
 * behind {@code clearFocusOwner} — both are client-side, and Karibu records
 * DOM listeners and {@code executeJs} without running them. Everything below
 * therefore moves focus the way production does server-side, through
 * {@code requestFocus}'s optimistic pointer update.
 */
class FocusWarnInventoryTest {

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
    void inventory_focus_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = collect;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Focus");
        dump("Step 0b (FocusPanel swap)", warnings);

        // The panel's five TextField peers, in construction order: the three
        // phone parts, then the plain field and the opted-out one.
        List<TextField> fields = LocatorJ._find(TextField.class);
        dump("Step 1 (lookups)", warnings);

        // Step 2: the panel's own buttons. Focusing through the button keeps
        // the ActionListener wiring in the asserted path too.
        clickButton("Focus the area code");
        dump("Step 2a (requestFocus)", warnings);
        clickButton("Who has focus?");
        dump("Step 2b (getFocusOwner + getFocusedWindow readout)", warnings);
        clickButton("Transfer focus");
        dump("Step 2c (transferFocus)", warnings);
        clickButton("Transfer focus backward");
        dump("Step 2d (transferFocusBackward)", warnings);

        // Step 3: the browser reporting focus back — the peer's own focus event,
        // which drives the readout on a click or a Tab and moves the pointer.
        focus(fields.get(0));
        dump("Step 3 (peer focus → AWT FocusEvent + pointer)", warnings);

        // Step 4: browser-style typing into the now-focused auto-advance field.
        // Its PlainDocument filter is NumberFormatDocument's shape —
        // getFocusOwner, the "is this my component?" guard, then transferFocus.
        // Asserted, not merely driven: a silent guard miss would leave the
        // WARN inventory clean while the demo did nothing.
        LocatorJ._setValue(fields.get(0), "415");
        assertFocused(fields.get(1), "auto-advance should hop to the phone prefix");
        dump("Step 4a (auto-advance fires on a browser edit)", warnings);
        LocatorJ._setValue(fields.get(1), "555");
        assertFocused(fields.get(2), "auto-advance should hop to the line number");
        dump("Step 4b (second hop)", warnings);

        // Step 5: traversal skips the setFocusable(false) field — the last
        // field in the panel, so the plain one before it is the answer.
        LocatorJ._setValue(fields.get(2), "0123");
        dump("Step 4c (last part full — nothing to advance into but the plain field)", warnings);

        com.vaadin.flow.component.ComponentUtil.fireEvent(fields.get(3),
                new com.vaadin.flow.component.BlurNotifier.BlurEvent<>(fields.get(3), false));
        dump("Step 5 (peer blur → AWT FocusEvent)", warnings);

        clickButton("Clear focus");
        dump("Step 6 (clearFocusOwner)", warnings);

        WarnDump.println();
        WarnDump.println("=== focus user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_focus_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = collect;

        JFrame frame = new JFrame("driver");
        JPanel panel = new JPanel();
        frame.add(panel);
        JTextField first = new JTextField("first");
        JTextField second = new JTextField("second");
        JButton go = new JButton("go");
        panel.add(first);
        panel.add(second);
        panel.add(go);
        dump("Fixture setup", warnings);

        // -- Bucket a: the two statics that hand out the manager --

        FocusManager swingManager = FocusManager.getCurrentManager();
        KeyboardFocusManager awtManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        FocusManager.isFocusManagerEnabled();
        dump("Bucket a (managers)", warnings);

        // -- Bucket b: the focus-owner and window queries --

        first.requestFocus();
        swingManager.getFocusOwner();
        swingManager.getPermanentFocusOwner();
        swingManager.getFocusedWindow();
        swingManager.getActiveWindow();
        first.isFocusOwner();
        first.hasFocus();
        dump("Bucket b (tier-1 queries)", warnings);

        // -- Bucket c: traversal, from both the component and the manager --

        first.transferFocus();
        second.transferFocusBackward();
        awtManager.focusNextComponent(first);
        awtManager.focusPreviousComponent(second);
        awtManager.focusNextComponent();
        awtManager.focusPreviousComponent();
        dump("Bucket c (tier-2 traversal)", warnings);

        // -- Bucket d: the focusable flag traversal consults --

        second.setFocusable(false);
        second.isFocusable();
        first.transferFocus();
        second.setFocusable(true);
        dump("Bucket d (focusable)", warnings);

        // -- Bucket e: requestFocus's other spellings --

        go.requestFocus();
        go.requestFocusInWindow();
        go.grabFocus();
        go.isRequestFocusEnabled();
        dump("Bucket e (requestFocus family)", warnings);

        // -- Bucket f: focus listeners on a peer that can report focus --

        vaadinx.awt.event.FocusListener listener = new vaadinx.awt.event.FocusListener() {
            @Override public void focusGained(vaadinx.awt.event.FocusEvent e) {}
            @Override public void focusLost(vaadinx.awt.event.FocusEvent e) {}
        };
        first.addFocusListener(listener);
        first.getFocusListeners();
        first.removeFocusListener(listener);
        dump("Bucket f (focus listeners)", warnings);

        // -- Bucket g: dropping focus, and the honest empty traversal-key answer --

        awtManager.clearFocusOwner();
        awtManager.getDefaultFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
        first.getFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS);
        dump("Bucket g (clear + traversal keys)", warnings);

        WarnDump.println();
        WarnDump.println("=== focus API-surface WARN total: " + warnings.size() + " ===");
    }

    /** What the browser does when the user clicks into a field. */
    private static void focus(TextField field) {
        com.vaadin.flow.component.ComponentUtil.fireEvent(field,
                new com.vaadin.flow.component.FocusNotifier.FocusEvent<>(field, false));
    }

    private static void assertFocused(TextField expected, String message) {
        com.vaadin.flow.component.Component owner = com.vaadin.swingbridge.surrogates.FocusTracker.getFocusOwner();
        if (owner != expected) {
            throw new AssertionError(message + " — focus owner is " + owner);
        }
    }

    private static void clickButton(String text) {
        LocatorJ._click(LocatorJ._get(com.vaadin.flow.component.button.Button.class,
                spec -> spec.withText(text)));
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
                    + " stub WARN(s) fired — regression in the focus exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
