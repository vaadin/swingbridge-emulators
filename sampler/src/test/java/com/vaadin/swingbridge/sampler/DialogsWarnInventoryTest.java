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
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;
import vaadinx.swing.JButton;
import vaadinx.swing.JFrame;

import javax.swing.WindowConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialogs view + JFrame surface exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape} fires during the
 * asserted path. {@link DialogsView} is the canonical "I opened a Frame"
 * fixture, so the SD_sframe + SD_sjframe surrogate surface (SFrame / SJFrame /
 * SJRootPane + close-X header chrome) extends its WARN-inventory
 * coverage here.
 *
 * <ol>
 *   <li>{@link #inventory_dialogs_user_path} — {@link DialogsPanel}
 *       driven end-to-end via the Sampler shell (JFrame opens; the
 *       inside-frame "Close from inside" button dispose()s).</li>
 *   <li>{@link #inventory_jframe_api_surface} — a micro-driver over the
 *       JFrame surface (Window + Frame + JFrame): setVisible / dispose /
 *       defaultCloseOperation transitions / WindowListener fan-out /
 *       title round-trip / getFrames / contentPane routing. Locks in
 *       zero-WARN coverage so future changes to the surrogate can't
 *       silently reintroduce a stub.</li>
 * </ol>
 *
 * <p>All four {@link javax.swing.WindowConstants} values are in scope
 * for the set-time WARN inventory — per D_gap_severity_triage the EXIT_ON_CLOSE signal is
 * a close-time throw rather than a set-time WARN, so
 * setting it is silent. The throw fires only on a peer-originated
 * close-attempt, which the inventory driver doesn't simulate.
 */
class DialogsWarnInventoryTest {

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
    void inventory_dialogs_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Dialogs");
        dump("Step 0b (DialogsPanel swap — JFrame opens)", warnings);

        // The "Close from inside" button is a JButton inside the JFrame's
        // contentPane, narrow by its caption (route also has a "Reopen
        // frame" button outside the frame). _click drives isFromClient=true
        // and asserts the button is visible+enabled before firing.
        com.vaadin.flow.component.button.Button closeBtn =
                LocatorJ._get(com.vaadin.flow.component.button.Button.class,
                        spec -> spec.withText("Close from inside"));
        LocatorJ._click(closeBtn);
        dump("Step 1 (close-from-inside dispose path)", warnings);

        WarnDump.println();
        WarnDump.println("=== dialogs user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jframe_api_surface() {
        // Micro-driver over the SD_sframe surface — JFrame lifecycle, window
        // listeners, title, defaultCloseOperation transitions across all
        // four values (EXIT_ON_CLOSE is silent at set-time per D_gap_severity_triage),
        // getFrames.

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Fixture: an untitled JFrame --
        JFrame frame = new JFrame();
        dump("SD_sframe  new JFrame()", warnings);

        // -- Title round-trip --
        frame.setTitle("t1");
        frame.getTitle();
        frame.setTitle("t2");
        frame.setTitle(null);  // coerces to ""
        frame.setTitle("final");
        dump("SD_sframe  title round-trip", warnings);

        // -- Window listener registration + accessor --
        WindowAdapter wa = new WindowAdapter() {};
        frame.addWindowListener(wa);
        frame.getWindowListeners();
        frame.removeWindowListener(wa);
        dump("SD_sframe  WindowListener add/get/remove", warnings);

        // -- Lifecycle: setVisible(true) attaches + fires WINDOW_OPENED --
        RecordingWindowListener rec = new RecordingWindowListener();
        frame.addWindowListener(rec);
        frame.setVisible(true);
        dump("SD_sframe  setVisible(true) — attach + WINDOW_OPENED", warnings);

        // -- defaultCloseOperation transitions across all four values --
        // EXIT_ON_CLOSE is silent at set-time (D_gap_severity_triage); the
        // close-attempt throw isn't simulated here.
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);     // default → default, no-op
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        frame.getDefaultCloseOperation();
        dump("SD_sframe  setDefaultCloseOperation full round-trip", warnings);

        // -- Hide / re-show --
        frame.setVisible(false);
        frame.setVisible(true);
        dump("SD_sframe  hide / re-show", warnings);

        // -- getFrames / getWindows live-graph walk --
        JFrame.getFrames();
        vaadinx.awt.Window.getWindows();
        vaadinx.awt.Window.getOwnerlessWindows();
        dump("SD_sframe  getFrames / getWindows / getOwnerlessWindows", warnings);

        // -- Add / remove child through content-pane routing --
        JButton b = new JButton("add-me");
        frame.add(b);
        frame.getContentPane();
        frame.remove(b);
        dump("SD_sframe  contentPane routing (add/remove/getContentPane)", warnings);

        // -- Content pane explicit swap --
        vaadinx.awt.Container fresh = new vaadinx.awt.Container();
        frame.setContentPane(fresh);
        dump("SD_sframe  setContentPane swap", warnings);

        // -- Glass pane: structural busy-curtain surface (D_glasspane_structural) --
        // getGlassPane lazy-constructs + structurally attaches the default
        // pane; setGlassPane swaps it; setVisible toggles the curtain (which
        // drives the emul-glasspane / emul-has-glasspane class wiring). All
        // synchronous — no Timer — so the gate stays deterministic.
        frame.getGlassPane();
        vaadinx.swing.JPanel curtain = new vaadinx.swing.JPanel();
        frame.setGlassPane(curtain);
        frame.getGlassPane().setVisible(true);
        frame.getGlassPane().setVisible(false);
        dump("D_glasspane_structural  glass-pane curtain (get/set/setVisible)", warnings);

        // -- dispose fires WINDOW_CLOSED --
        frame.dispose();
        dump("SD_sframe  dispose() — WINDOW_CLOSED + detach", warnings);

        // -- Frame state / resizable / undecorated defaults --
        frame.getState();
        frame.setState(java.awt.Frame.NORMAL);
        frame.getExtendedState();
        frame.setExtendedState(java.awt.Frame.NORMAL);
        frame.isResizable();
        frame.setResizable(true);
        frame.setResizable(false);
        frame.isUndecorated();
        frame.setUndecorated(false);
        dump("SD_sframe  Frame state/resizable/undecorated defaults", warnings);

        WarnDump.println();
        WarnDump.println("=== SD_sframe API-surface WARN total: " + warnings.size() + " ===");
    }

    /** Records (id-name, id-int) for each fired window event. */
    private static class RecordingWindowListener extends WindowAdapter {
        final List<String> events = new ArrayList<>();
        @Override public void windowOpened(WindowEvent e) { events.add("opened"); }
        @Override public void windowClosing(WindowEvent e) { events.add("closing"); }
        @Override public void windowClosed(WindowEvent e) { events.add("closed"); }
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
                    + " stub WARN(s) fired — regression in the SD_sframe exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
