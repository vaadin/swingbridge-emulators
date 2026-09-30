/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockBrowser;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AppTab;
import vaadinx.EHelper;
import vaadinx.MockVirtualThreadAwareServlet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F5 / {@code @PreserveOnRefresh} gate for <b>every UI-scoped feature SB-Emulators offers</b>.
 *
 * <p>On a browser refresh, Flow creates a fresh UI, teleports the preserved
 * component tree onto it ({@code AbstractNavigationStateRenderer} →
 * {@code UIInternalUpdater.moveToNewUI}), and closes the old UI. As of karibu-testing
 * 2.7.1 (<a href="https://github.com/mvysny/karibu-testing/issues/207">karibu-testing#207</a>,
 * PR #208), Karibu's {@code UI.getCurrent().getPage().reload()} reproduces this in the
 * production order — the new UI is created and registered first, overlays
 * teleport via {@code moveElementsFrom}, the old UI closes last — carrying
 * {@code ExtendedClientDetails} forward so {@code @PreserveOnRefresh} correlates (needs
 * {@code fakeExtendedClientDetails = true}, the Karibu default).
 *
 * <h2>The UI-scoped surface</h2>
 *
 * A feature is F5-fragile only if it holds UI-bound state the teleport
 * doesn't carry. Surveying every per-UI/per-component holder in the codebase:
 *
 * <ul>
 *  <li><b>Timer</b> — WAS orphaned: captured the UI eagerly and fired via
 *    {@code ui.access(capturedUI)}, while the old UI's {@code UiScheduler} was
 *    {@code shutdownNow()}'d on detach. Fixed by session-scoping the scheduler and
 *    resolving the live UI lazily per fire (D_session_scoped_pools). Guarded below.
 *  <li><b>SwingWorker</b> — WAS orphaned the same way (per-UI {@code WorkerPool};
 *    {@code done()}/{@code process()} delivered to the captured, closed UI). Same fix
 *    (D_session_scoped_pools). Guarded below.
 *  <li><b>{@code EHelper.callSwing}</b> — always SAFE. Resolves {@code UI.getCurrent()} lazily
 *    per call and starts its UI fiber on whatever UI is current. Guarded below as the
 *    contrast case.
 *  <li><b>Surrogate {@code *Store} holders, {@code DndBridge}</b> — SAFE. Keyed on the
 *    <em>component/peer</em> ({@code ComponentUtil.setData(component, …)}), which teleports
 *    with the preserved instance.
 *  <li><b>{@code BrowserTimeZone}</b> — SAFE. Session-scoped ({@code VaadinSession.getAttribute}),
 *    survives the same-session reload.
 *  <li><b>{@code SHelper} per-UI CSS flags</b> — SAFE. Lost on F5, but the new UI needs
 *    the CSS re-injected anyway, so the reset is correct.
 * </ul>
 *
 * <p>The Timer and SwingWorker gates assert delivery lands on the LIVE post-F5
 * UI. Both were verified failing before the fix (the eager-capture bug) and
 * now pass on the session-scoped machinery. See D_session_scoped_pools and
 * {@link vaadinx.EHelper#onSessionLiveUI}.
 */
class UiScopedFeaturesF5Test {

    @BeforeEach
    void setup() {
        Routes routes = new Routes(
                new LinkedHashSet<>(List.of(
                        F5LandingView.class,
                        TimerReloadView.class,
                        WorkerReloadView.class,
                        PlainReloadView.class)),
                new LinkedHashSet<>(), false);
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
    }

    @AfterEach
    void teardown() {
        MockVaadin.tearDown();
    }

    /** Poll-drain the UI queue until {@code counter} reaches {@code target} or the deadline passes. */
    private static boolean awaitAtLeast(AtomicInteger counter, int target) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            MockVaadin.runUIQueue();
            if (counter.get() >= target) {
                return true;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Live (non-closing) UIs in {@code session}. */
    private static long liveUiCount(VaadinSession session) {
        return session.getUIs().stream().filter(ui -> !ui.isClosing()).count();
    }

    // ---- Timer (was orphaned pre-D_session_scoped_pools — now session-scoped) -----------

    @Test
    @DisplayName("Timer keeps firing across an F5 reload")
    void timerKeepsFiringAcrossAnF5Reload() {
        UI.getCurrent().navigate("timer");
        TimerReloadView view1 = LocatorJ._get(TimerReloadView.class);
        UI ui1 = UI.getCurrent();
        assertTrue(awaitAtLeast(view1.fireCount, 2), "timer should fire on the initial UI");

        UI.getCurrent().getPage().reload();

        UI ui2 = UI.getCurrent();
        assertNotSame(ui1, ui2, "reload must create a new UI");
        TimerReloadView view2 = LocatorJ._get(TimerReloadView.class);
        assertSame(view1, view2, "@PreserveOnRefresh preserves the same view instance across F5");

        int countAfterReload = view2.fireCount.get();
        assertTrue(awaitAtLeast(view2.fireCount, countAfterReload + 2),
                "timer keeps firing after F5 — session-scoped scheduler survives the teleport and each "
                        + "fire re-resolves the live UI (D_session_scoped_pools)");
    }

    // ---- SwingWorker (was orphaned pre-D_session_scoped_pools — now session-scoped) -----

    @Test
    @DisplayName("SwingWorker delivers done() to the live UI after an F5 reload")
    void swingWorkerDeliversDoneToTheLiveUiAfterF5() throws InterruptedException {
        UI.getCurrent().navigate("worker");
        WorkerReloadView view1 = LocatorJ._get(WorkerReloadView.class);
        UI ui1 = UI.getCurrent();
        assertTrue(view1.started.await(5, TimeUnit.SECONDS),
                "worker's doInBackground should start on the initial UI");

        UI.getCurrent().getPage().reload();

        UI ui2 = UI.getCurrent();
        assertNotSame(ui1, ui2, "reload must create a new UI");
        WorkerReloadView view2 = LocatorJ._get(WorkerReloadView.class);
        assertSame(view1, view2, "@PreserveOnRefresh preserves the same worker-holding view across F5");

        view2.release.countDown(); // let doInBackground finish (if not already interrupted)
        // doneCount alone is UI-agnostic — it can't distinguish delivery to the live UI from
        // (buggy) delivery to the closed pre-reload UI. The faithful check is *which* UI done()
        // ran on: under the bug, done() delivers via ui.access(capturedUI==ui1), so it either
        // never runs (real browser: ui1 is gone) or runs against the dead ui1 (Karibu: its
        // access queue still drains). Either way doneUI != ui2.
        assertTrue(awaitAtLeast(view2.doneCount, 1), "done() must run after F5");
        assertSame(ui2, view2.doneUI,
                "done() is delivered on the LIVE post-F5 UI — the worker holds the session (not the "
                        + "closed UI) and resolves the live UI at completion time (D_session_scoped_pools)");
    }

    // ---- singleLiveUI resolution = the AppTab identity, no heuristic ----
    //
    // singleLiveUI resolves the AppTab (D_session_scoped_pools/D_active_ui_pointer): the app's UI is tracked
    // explicitly at attach / F5 rebind, so there is no getUIs()-heuristic to isolate and no
    // F5 double-live window to disambiguate — a resolver acquiring the session lock only
    // ever sees the settled AppTab (D_session_scoped_pools). A genuine second browser tab is a D_active_ui_pointer curtain: it
    // never becomes the AppTab, so delivery keeps targeting the running app rather than
    // throwing (the old Blocker-3 multi-tab IllegalStateException is gone by construction).

    @Test
    @DisplayName("a second browser tab does not disturb delivery — singleLiveUI stays on the app")
    void aSecondBrowserTabDoesNotDisturbDelivery() {
        UI.getCurrent().navigate("timer");
        UI appUI = UI.getCurrent(); // the app: sets the active-UI pointer on attach
        VaadinSession session = appUI.getSession();

        // A genuine second browser tab (own UI + window.name), navigated to a real view.
        // In production it would render the D_active_ui_pointer curtain and never claim the pointer; here
        // the bare "plain" route simply never sets it, which is the same thing.
        UI ui2 = MockBrowser.newTab("tab-2", "plain");
        assertTrue(!ui2.getInternals().getActiveRouterTargetsChain().isEmpty(), "the second tab hosts a view");
        assertEquals(2, liveUiCount(session), "two live navigated tabs");

        assertSame(appUI, EHelper.singleLiveUI(session),
                "delivery resolves to the app's UI (the pointer), not the curtain tab — and never throws");
    }

    // ---- lost-beacon reap (MockBrowser.closeTab(beaconLost) + reapInactiveUIs) ----

    /**
     * A background tab whose F5/close unload beacon was lost lingers in the session until Flow's
     * heartbeat/idle-UI cleanup reaps it. {@link MockVaadin#reapInactiveUIs} simulates that reap's outcome.
     * The app UI holds the active-UI pointer throughout, so {@link EHelper#singleLiveUI} resolves to it both
     * before and after the reap — exercising that SB-Emulators' session-scoped machinery (D_session_scoped_pools) survives a
     * sibling UI dying and being cleaned up out from under it.
     */
    @Test
    @DisplayName("a lost-beacon tab is reaped, leaving the app UI as the single live UI")
    void aLostBeaconTabIsReapedLeavingTheAppUi() {
        UI.getCurrent().navigate("timer");
        UI appUI = UI.getCurrent();
        VaadinSession session = appUI.getSession();
        String appTab = MockBrowser.getCurrentWindowName();

        // Open a second tab, then return focus to the app tab and lose the ghost tab's beacon
        // (closeTab refuses to close the focused tab, mirroring a browser).
        MockBrowser.newTab("ghost", "plain");
        MockBrowser.switchTo(appTab);
        assertEquals(2, liveUiCount(session), "app tab + lingering ghost tab");

        MockBrowser.closeTab("ghost", true);
        MockVaadin.reapInactiveUIs();

        assertEquals(List.of(appUI), List.copyOf(session.getUIs()), "only the app UI survives the reap");
        assertSame(appUI, EHelper.singleLiveUI(session), "singleLiveUI resolves to the surviving app UI");
    }

    // ---- callSwing (self-heals — currently GREEN, guards the good case) ----

    @Test
    @DisplayName("callSwing resolves and runs on the post-F5 UI")
    void callSwingResolvesAndRunsOnThePostF5Ui() {
        UI.getCurrent().navigate("plain");
        UI ui1 = UI.getCurrent();

        UI.getCurrent().getPage().reload();

        UI ui2 = UI.getCurrent();
        assertNotSame(ui1, ui2, "reload must create a new UI");
        vaadinx.Counter ran = new vaadinx.Counter();
        EHelper.callSwing(ran::inc);
        assertEquals(1, ran.get(),
                "callSwing resolves UI.getCurrent() lazily, so it runs on the live post-F5 UI — "
                        + "this is why callSwing is NOT part of the orphan bug");
    }

    // ---- Routes ------------------------------------------------------

    /** Empty landing route so {@code MockVaadin.setup}'s default navigation to {@code ""} lands somewhere. */
    @Route("")
    public static class F5LandingView extends Div {
    }

    /**
     * Scaffold-shaped root: holds a running Timer captured on the initial UI.
     * Binds the app tab on every attach (initial + F5 rebind) via {@link AppTab#markAppUI},
     * mirroring what {@code MainWindowRoute} does in production, so {@code singleLiveUI} resolves
     * the app UI deterministically (rather than the best-effort fallback).
     */
    @Route("timer")
    @PreserveOnRefresh
    public static class TimerReloadView extends Div {

        final AtomicInteger fireCount = new AtomicInteger(0);
        private final Timer timer = new Timer(20, e -> fireCount.incrementAndGet());

        public TimerReloadView() {
            timer.start();
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            AppTab.markAppUI(attachEvent.getUI());
        }
    }

    /** Holds a SwingWorker kept in flight (blocked on {@code release}) so an F5 lands mid-work. */
    @Route("worker")
    @PreserveOnRefresh
    public static class WorkerReloadView extends Div {

        final AtomicInteger doneCount = new AtomicInteger(0);
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        volatile UI doneUI;

        private final SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws InterruptedException {
                started.countDown();
                release.await(); // stay in flight until the test releases (or an interrupt aborts)
                return "ok";
            }

            @Override
            protected void done() {
                doneUI = UI.getCurrent();
                doneCount.incrementAndGet();
            }
        };

        public WorkerReloadView() {
            worker.execute();
        }

        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            AppTab.markAppUI(attachEvent.getUI());
        }
    }

    /** Bare preserved view for the callSwing self-heal check. */
    @Route("plain")
    @PreserveOnRefresh
    public static class PlainReloadView extends Div {
    }
}
