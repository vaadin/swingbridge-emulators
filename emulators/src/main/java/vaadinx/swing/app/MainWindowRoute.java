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

package vaadinx.swing.app;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import vaadinx.AppTab;

/**
 * Base class for the Vaadin route that hosts a migrated app's main
 * {@link vaadinx.swing.JFrame}. Migrators paste a one-class scaffold
 * extending this base into their app, owning {@code @Route} /
 * {@code @PreserveOnRefresh} / the bootstrap call:
 *
 * <pre>{@code
 * @Route("") @PreserveOnRefresh
 * public class AppRoute extends MainWindowRoute {
 *     @Override protected void bootstrap() { MyApp.mainUI(); }
 * }
 * }</pre>
 *
 * <p>Attaching throws {@link IllegalStateException} when {@link SwingBridgeEmulatorsBootstrap} never ran for the
 * attaching UI — the app forgot the one SPI line, and the route is the earliest place that is
 * knowable (M1D_bootstrap_canonical).
 *
 * <p>{@link #bootstrap()} runs <strong>at most once per instance</strong>.
 * On every attach (initial + {@code @PreserveOnRefresh} re-bind to a fresh
 * UI), the route registers itself as the current {@code MainWindowRoute}
 * on the attaching UI via {@link ComponentUtil#setData}, so the
 * {@code @MainWindow}-annotated {@link vaadinx.swing.JFrame} constructed
 * inside (or transitively, via {@code SwingUtilities.invokeLater} +
 * {@code EHelper.callSwing}) captures the route as its inline-render
 * target. Subsequent {@code onAttach} calls on the same instance skip
 * {@link #bootstrap()}; the user's preserved component subtree (including
 * the {@code @MainWindow} JFrame's peer) is reused.
 *
 * <h2>UI-scoped data, not {@code ThreadLocal}</h2>
 *
 * The route→UI binding lives on {@code UI.getCurrent()}
 * ({@link ComponentUtil#setData}), <em>not</em> a {@code ThreadLocal} around
 * {@link #bootstrap()}, because the canonical Swing entry wraps frame
 * construction in {@code SwingUtilities.invokeLater(...)}: that runs through
 * {@code EHelper.callSwing}'s virtual-thread executor, so the frame ctor fires
 * on a VT continuation <em>after</em> {@code bootstrap()} has returned (a
 * bootstrap-scoped {@code ThreadLocal} would already be cleared) and on a fresh
 * thread with its own thread-local table (so it'd read {@code null} anyway).
 * UI-scoped data survives both the synchronous and VT-deferred hop because
 * {@code callSwing} guarantees {@code UI.getCurrent()} is the calling thread's
 * UI (R_callswing_envelope / D_callswing_loom).
 *
 * <h2>Why an instance flag and not {@code AttachEvent#isInitialAttach}</h2>
 *
 * Flow's {@code isInitialAttach()} returns {@code true} on <em>every</em>
 * {@code @PreserveOnRefresh} re-bind (the detached node's id resets to
 * {@code -1}, so the rebind reads as initial), so gating {@link #bootstrap()}
 * on it would construct a fresh {@code @MainWindow} frame on every browser
 * refresh and stack it under the preserved Div. The {@code bootstrapped}
 * instance flag is the reliable gate — {@code @PreserveOnRefresh} preserves the
 * instance, so an instance field is exactly once-per-instance.
 *
 * <h2>App-tab identity &amp; multi-tab (option 1)</h2>
 *
 * {@code onAttach} maintains the session's {@link vaadinx.AppTab} — the one UI
 * (and browser tab) the app lives in — via {@link vaadinx.AppTab#markAppUI}.
 * The same {@code bootstrapped} flag that gates {@code bootstrap()}
 * doubles as the F5-vs-new-tab discriminator: a preserved-instance
 * re-attach (F5) re-binds the fresh UI to the app tab; a fresh instance
 * while the app is already live in another UI is a <b>second tab</b>,
 * which renders {@link #onAppAlreadyActive()} instead of running a second
 * copy (one app per session). {@link vaadinx.EHelper#singleLiveUI}
 * resolves the {@code AppTab} first. See
 * <a href="../../../../emulators/decisions.md#D_active_ui_pointer">D_active_ui_pointer</a>.
 *
 * <p>Per
 * <a href="../../../../emulators/decisions.md#D_mainwindow_route">D_mainwindow_route</a>:
 * lifecycle plumbing (once-per-instance bootstrap gating, UI-scoped
 * route registration, eventual session-destroy → {@code WINDOW_CLOSING}
 * listener) lives here so we can evolve it without touching every
 * migrated app.
 */
public abstract class MainWindowRoute extends Div {

    /**
     * Per-instance gate. Set the first time {@link #onAttach} fires;
     * checked on every subsequent attach to suppress re-running
     * {@link #bootstrap()}. Survives {@code @PreserveOnRefresh} re-binds
     * because the route instance itself is preserved across refreshes.
     */
    private boolean bootstrapped = false;

    /**
     * Default sizing: fill the browser viewport. Per
     * <a href="../../../../emulators/decisions.md#D_inline_route_sizing">D_inline_route_sizing</a>,
     * a {@code @MainWindow} JFrame's route fills the viewport unless the
     * user calls {@code pack()} — main windows occupy as much space as
     * possible, opposed to dialogs that wrap their children. The ctor sets
     * {@code setSizeFull()} so the default-fill mode lights up before any
     * JFrame ctor runs; height-stretchable children (Vaadin Grid inside
     * SJScrollPane, future TabPane / SplitPane) get a real height to
     * expand into.
     *
     * <p>{@code JFrame.pack()} on InlineStrategy clears this — see
     * {@code InlineStrategy.applyInlineSizingOnPack}.
     */
    public MainWindowRoute() {
        setSizeFull();
    }

    /**
     * Read by {@link vaadinx.swing.JFrame}'s ctor to bind a
     * {@code @MainWindow} frame to the route registered on the current
     * UI. Returns {@code null} if there is no current UI or no
     * {@code MainWindowRoute} is mounted on it.
     */
    public static MainWindowRoute current() {
        UI ui = UI.getCurrent();
        return ui == null ? null : ComponentUtil.getData(ui, MainWindowRoute.class);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        // Before anything else, including the F5 rebind path: an app that never
        // registered SwingBridgeEmulatorsBootstrap has nothing SB-Emulators needs wired, and every symptom
        // of that shows up later and elsewhere (M1D_bootstrap_canonical).
        if (!SwingBridgeEmulatorsBootstrap.isWired(attachEvent.getUI())) {
            throw new IllegalStateException("vaadinx.swing.app.SwingBridgeEmulatorsBootstrap never ran for this UI, so "
                + "nothing SB-Emulators needs per session is wired (browser time zone, focus tracking, "
                + "emulator theme, tab-close shutdown). The app registers it, never the library "
                + "(R_no_spi_selfregister): add the line 'vaadinx.swing.app.SwingBridgeEmulatorsBootstrap' to "
                + "src/main/resources/META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener, "
                + "or on Spring expose it as a @Bean. See M1D_bootstrap_canonical.");
        }
        super.onAttach(attachEvent);
        final UI ui = attachEvent.getUI();
        ComponentUtil.setData(ui, MainWindowRoute.class, this);

        if (bootstrapped) {
            // F5 @PreserveOnRefresh rebind of this preserved instance: Flow
            // teleported the app's component tree onto a fresh UI. Re-bind the
            // fresh UI to the app tab so Timer/SwingWorker delivery and
            // blocking-dialog resume follow the app across the refresh (D_session_scoped_pools /
            // D_callswing_loom), and its close still drives session shutdown (D_shutdown_lifecycle). The tab
            // scope survives F5 (window.name-keyed), so this is idempotent. Do
            // not re-run bootstrap() — the subtree is preserved.
            AppTab.markAppUI(ui);
            return;
        }

        final AppTab existing = AppTab.forSession(ui.getSession());
        final UI active = existing == null ? null : existing.liveUI();
        if (active != null && active != ui) {
            // A fresh route instance while the app is already live in another UI
            // (a second browser tab). One app per session — don't run a second
            // copy here; show the "already active elsewhere" curtain instead
            // (multi-tab option 1). bootstrapped stays false, so if the active
            // tab later goes away this tab can claim the app on its next attach.
            onAppAlreadyActive();
            return;
        }

        // First load in this session (or a re-add on the same UI): this UI + its
        // browser tab host the app (its close drives session shutdown, D_shutdown_lifecycle).
        AppTab.markAppUI(ui);
        bootstrapped = true;
        bootstrap();
    }

    /**
     * Rendered instead of the app when this UI is a <em>second tab</em> and the
     * app is already active in another UI (multi-tab option 1: one app per
     * session, non-active tabs don't run it). Default shows a plain message;
     * override to customise (e.g. a "reload to take over here" action once the
     * active tab is gone — a future option-2/3 upgrade). Runs under the session
     * lock on the attaching UI.
     */
    protected void onAppAlreadyActive() {
        removeAll();
        add(new Span("This application is already open in another browser tab."));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        UI ui = detachEvent.getUI();
        if (ComponentUtil.getData(ui, MainWindowRoute.class) == this) {
            ComponentUtil.setData(ui, MainWindowRoute.class, null);
        }
        super.onDetach(detachEvent);
    }

    /**
     * Invoked once per route instance, on first attach. Implementations
     * typically call the migrated app's {@code mainUI()} method, which
     * instantiates the {@code @MainWindow} JFrame and calls
     * {@code setVisible(true)}. Wrapping construction in
     * {@code SwingUtilities.invokeLater(...)} is supported — the route's
     * registration on the current UI survives the VT hop.
     */
    protected abstract void bootstrap();
}
