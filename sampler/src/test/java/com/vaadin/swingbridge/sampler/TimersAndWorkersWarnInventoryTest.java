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
import vaadinx.swing.SwingUtilities;
import vaadinx.swing.SwingWorker;
import vaadinx.swing.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TimersAndWorkersView (Timer + SwingWorker host)
 * WARN inventory exit gate. Both tests fail if any
 * {@code EHelper.onUnimplemented} / {@code onUnsupportedPeerShape} fires.
 *
 * <ol>
 *   <li>{@link #inventory_user_path} — drives the route end-to-end:
 *       click "Start timer" → tick fires → label updates / click "Run
 *       worker" → worker doInBackground → publish + setProgress + done →
 *       status updates. Covers the per-UI scheduler / VT pool / ui.access
 *       hop / EHelper.callSwing fan-out chain.</li>
 *   <li>{@link #inventory_api_surface} — micro-driver over the
 *       Timer + SwingWorker + SwingUtilities API surface (ctors,
 *       lifecycle, listener add/remove, progress + state PCE,
 *       isEventDispatchThread, the EDT-throw guards).</li>
 * </ol>
 */
class TimersAndWorkersWarnInventoryTest {

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
    void inventory_user_path() throws InterruptedException {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Timers");
        dump("Step 0b (TimersAndWorkersPanel swap)", warnings);

        // Start the Timer, drain a few ticks, stop it. The scheduler
        // thread queues fire callbacks via ui.access; MockVaadin.runUIQueue
        // drains them synchronously on this test thread.
        Button startTimer = LocatorJ._get(Button.class, spec -> spec.withText("Start timer"));
        LocatorJ._click(startTimer);
        // Sleep > 1 timer interval (500ms) so the scheduler thread has
        // queued at least one fire.
        Thread.sleep(700);
        MockVaadin.runUIQueue();
        dump("Step 1 (Timer started + ticked)", warnings);

        Button stopTimer = LocatorJ._get(Button.class, spec -> spec.withText("Stop timer"));
        LocatorJ._click(stopTimer);
        dump("Step 2 (Timer stopped)", warnings);

        // Run the SwingWorker: doInBackground sleeps 5×150ms (~750ms total)
        // publishing chunks + setting progress; done() runs on the EDT
        // and re-enables the button. Drain throughout.
        Button runWorker = LocatorJ._get(Button.class, spec -> spec.withText("Run worker"));
        LocatorJ._click(runWorker);
        // Wait for the worker to finish — it sleeps in 5×150ms steps.
        // Poll-and-drain.
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            MockVaadin.runUIQueue();
            if (runWorker.isEnabled()) break;
            Thread.sleep(50);
        }
        dump("Step 3 (SwingWorker ran + done() re-enabled button)", warnings);

        WarnDump.println();
        WarnDump.println("=== timers-workers user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_api_surface() throws Exception {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Timer ctor + property contract --
        Timer t = new Timer(100, e -> {});
        t.getDelay();
        t.getInitialDelay();
        t.isRepeats();
        t.isCoalesce();
        t.getActionCommand();
        t.setDelay(200);
        t.setInitialDelay(50);
        t.setRepeats(false);
        t.setCoalesce(false);
        t.setActionCommand("tick");
        t.addActionListener(e -> {});
        t.removeActionListener(e -> {});
        t.getActionListeners();
        Timer.setLogTimers(false);
        Timer.getLogTimers();
        dump("Timer  ctor + property contract", warnings);

        // -- Timer lifecycle (no fire — we don't drain here) --
        t.start();
        t.isRunning();
        t.stop();
        t.restart();
        t.stop();
        dump("Timer  lifecycle", warnings);

        // -- SwingWorker ctor + state queries (no execute — we don't drain) --
        final CountDownLatch done = new CountDownLatch(1);
        SwingWorker<Integer, Unit> worker = new SwingWorker<Integer, Unit>() {
            @Override
            protected Integer doInBackground() throws Exception {
                return 42;
            }
            @Override
            protected void done() {
                done.countDown();
            }
        };
        worker.getState();
        worker.isDone();
        worker.isCancelled();
        worker.getProgress();
        worker.addPropertyChangeListener(e -> {});
        worker.execute();
        // Drain so done() can fire and we can also exercise get() from
        // done() (the carve-out path).
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            MockVaadin.runUIQueue();
            if (done.await(20, TimeUnit.MILLISECONDS)) break;
        }
        worker.isDone();
        worker.getState();
        dump("SwingWorker  ctor + execute + done", warnings);

        // -- SwingUtilities EDT helpers --
        EHelper.callSwing(() -> {
            SwingUtilities.isEventDispatchThread();
            SwingUtilities.invokeLater(() -> { /* no-op */ });
        });
        MockVaadin.runUIQueue();
        dump("SwingUtilities  EDT helpers", warnings);

        WarnDump.println();
        WarnDump.println("=== Timer + SwingWorker API-surface WARN total: " + warnings.size() + " ===");
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
                    + " stub WARN(s) fired — regression in the TimersAndWorkers exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }

    /** Empty type used as the `V` param of [SwingWorker]. */
    private static final class Unit {}
}
