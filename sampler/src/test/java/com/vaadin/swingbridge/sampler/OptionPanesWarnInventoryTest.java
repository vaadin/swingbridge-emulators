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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JOptionPane;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * OptionPanesView (JOptionPane host) WARN inventory
 * exit gate. Both tests fail if any {@code EHelper.onUnimplemented} /
 * {@code onUnsupportedPeerShape} fires.
 *
 * <ol>
 *   <li>{@link #inventory_message_user_path} — drives the route end-to-end:
 *       click "Show message" → JOptionPane opens (parks the click-handler
 *       VT) → click "OK" inside the dialog → dispose unparks the VT →
 *       status updates. Covers the static-factory + modal-park + button
 *       fan-out → ActionListener → dispose chain.</li>
 *   <li>{@link #inventory_joptionpane_api_surface} — micro-driver over
 *       the JOptionPane state surface (ctors, getters/setters, PCE,
 *       constants). Locks in zero-WARN coverage for the SD_no_sjoptionpane emulator-
 *       only landing.</li>
 * </ol>
 */
class OptionPanesWarnInventoryTest {

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
    void inventory_message_user_path() throws InterruptedException {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Option panes");
        dump("Step 0b (OptionPanesPanel swap)", warnings);

        // Click "Show message" — handler wraps showMessageDialog in
        // EHelper.callSwing, which spawns a VT, parks on the modal latch,
        // and returns control to the test once the carrier completes.
        Button showMessage = LocatorJ._get(Button.class, spec -> spec.withText("Show message"));
        LocatorJ._click(showMessage);
        dump("Step 1 (open JOptionPane message dialog)", warnings);

        // Click the dialog's OK button — fires another callSwing VT
        // whose ActionListener disposes the dialog, releasing the
        // parked VT, which then runs refreshStatus.
        Button ok = LocatorJ._get(Button.class, spec -> spec.withText("OK"));
        LocatorJ._click(ok);
        dump("Step 2 (OK dispose path → status update)", warnings);

        WarnDump.println();
        WarnDump.println("=== option-panes user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_joptionpane_api_surface() {
        // Micro-driver over the JOptionPane state surface — ctors,
        // setters/getters, PCE round-trip. Doesn't show any dialog
        // (modal park requires VT context); covered separately in
        // JOptionPaneTest.

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Default ctor + getters --
        JOptionPane p = new JOptionPane();
        p.getMessage();
        p.getMessageType();
        p.getOptionType();
        p.getValue();
        p.getInputValue();
        p.getOptions();
        p.getInitialValue();
        p.getIcon();
        p.getSelectionValues();
        p.getInitialSelectionValue();
        p.getWantsInput();
        dump("JOptionPane  default ctor + getters", warnings);

        // -- Setters with PCE --
        p.setMessage("hello");
        p.setMessageType(JOptionPane.WARNING_MESSAGE);
        p.setOptionType(JOptionPane.OK_CANCEL_OPTION);
        p.setIcon(null);
        p.setOptions(new Object[] { "A", "B" });
        p.setInitialValue("A");
        p.setValue("A");
        p.setInputValue("typed");
        p.setSelectionValues(new Object[] { "x", "y" });
        p.setInitialSelectionValue("x");
        p.setWantsInput(true);
        dump("JOptionPane  setters round-trip", warnings);

        // -- All seven public ctor overloads --
        new JOptionPane();
        new JOptionPane("m");
        new JOptionPane("m", JOptionPane.PLAIN_MESSAGE);
        new JOptionPane("m", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
        new JOptionPane("m", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null);
        new JOptionPane("m", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] { "OK" });
        new JOptionPane("m", JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, null,
                new Object[] { "OK" }, "OK");
        dump("JOptionPane  all seven ctor overloads", warnings);

        // -- createDialog with a null parent (no window ancestor walk) --
        vaadinx.swing.JDialog d = new JOptionPane("hi", JOptionPane.INFORMATION_MESSAGE)
                .createDialog(null, "Title");
        dump("JOptionPane  createDialog(null, title)", warnings);
        d.dispose();
        dump("JOptionPane  dialog dispose", warnings);

        WarnDump.println();
        WarnDump.println("=== JOptionPane API-surface WARN total: " + warnings.size() + " ===");
    }

    /**
     * Coordinated VT-park assertion helper: wires a CountDownLatch to
     * the EHelper.callSwing body so the test can block until the show*
     * static factory has unparked. Not currently used but available
     * for tests that need explicit wait semantics.
     */
    @SuppressWarnings("unused")
    private static int callBlockingDialogAndAwaitClose(Runnable opener) throws InterruptedException {
        int[] ret = { -99 };
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            opener.run();
            finished.countDown();
        });
        finished.await(5, TimeUnit.SECONDS);
        return ret[0];
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
                    + " stub WARN(s) fired — regression in the OptionPanes exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
