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

package vaadinx;

import com.github.mvysny.vaadin.tabscope.TabScope;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Attributes;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The app's identity within a session: the live {@link UI} it currently renders
 * in — the delivery target for {@link vaadinx.swing.Timer} fires,
 * {@link vaadinx.swing.SwingWorker} callbacks, {@link EHelper#callSwing}
 * continuations, and blocking-dialog resume — plus the browser tab
 * ({@link TabScope}) that hosts it, whose close ends the app. One app per
 * session (D_active_ui_pointer), so one {@code AppTab} per session, held under a session
 * attribute.
 *
 * <pre>{@code
 * // MainWindowRoute, on first load AND every F5 rebind — bind this UI + its tab:
 * AppTab.markAppUI(ui);
 * // EHelper.singleLiveUI, from a background thread under the session lock:
 * UI target = AppTab.forSession(session).liveUI();   // null if the app UI is gone
 * // SwingBridgeEmulatorsBootstrap's tab-destroy listener:
 * AppTab.onTabClosed(session, destroyedScope);        // closes the session iff it was the app tab
 * }</pre>
 *
 * <p>The tab identity is F5-stable: a refresh keeps the same {@code window.name}
 * → the same {@link TabScope}, so re-binding the fresh UI to the same tab is
 * idempotent and only a real tab close destroys the scope. When no tab scope is
 * available (browserless Karibu without opt-in, or a deployment that didn't wire
 * {@link vaadinx.swing.app.SwingBridgeEmulatorsBootstrap}) the tab is {@code null}: UI identity
 * still drives delivery, only tab-close detection goes inert.
 *
 * <p>The single home for app-identity state — the live app UI (delivery) and
 * its browser tab (close detection) — resolved by {@link EHelper#singleLiveUI},
 * bound by {@link vaadinx.swing.app.MainWindowRoute}, and torn down by {@link
 * vaadinx.swing.app.SwingBridgeEmulatorsBootstrap}'s tab-destroy listener. See D_session_scoped_pools/D_active_ui_pointer. It also
 * carries {@link AppInstance}'s handle on the tab scope's values, which is what
 * lets app-instance state resolve on the request-less teardown threads.
 */
public final class AppTab {

    private static final Logger log = LoggerFactory.getLogger(AppTab.class);
    private static final String KEY = AppTab.class.getName();

    private UI currentUI;
    private final TabScope tabScope; // nullable: no browser tab scope in this context
    private Attributes values;       // AppInstance's store; null until first touched under a UI

    private AppTab(UI currentUI, TabScope tabScope) {
        this.currentUI = currentUI;
        this.tabScope = tabScope;
    }

    /**
     * Records {@code ui} as the session's app UI — on first load and on every F5
     * rebind — binding it to the current browser tab. Idempotent across F5 (same
     * {@code window.name} → same tab): re-binds the fresh UI in place. A fresh
     * tab claiming the app after the previous one is gone replaces the record.
     * Must run under the session lock (a UI attach does).
     */
    public static void markAppUI(UI ui) {
        final VaadinSession session = ui.getSession();
        final AppTab existing = forSession(session);
        final TabScope scope = currentScopeOrNull();
        if (existing != null && existing.liveUI() != null
                && (scope == null || existing.tabScope == scope)) {
            existing.currentUI = ui;                             // F5 rebind, same tab
        } else {
            session.setAttribute(KEY, new AppTab(ui, scope));   // first load / reclaim
        }
    }

    /** The session's app tab, or {@code null} if none is recorded yet. */
    public static AppTab forSession(VaadinSession session) {
        return (AppTab) session.getAttribute(KEY);
    }

    /**
     * The app's UI if it is still live — in {@link VaadinSession#getUIs()} and
     * not {@code isClosing()} — else {@code null} (a stale record; a later tab
     * can then claim the app). An F5 rebind has already moved the record to the
     * fresh UI by the time the old one closes, so this never spuriously drops the
     * app mid-refresh. Must be called under the session lock.
     */
    public UI liveUI() {
        final UI ui = currentUI;
        if (ui == null) {
            return null;
        }
        final VaadinSession session = ui.getSession();
        if (session != null && !ui.isClosing() && session.getUIs().contains(ui)) {
            return ui;
        }
        return null;
    }

    /** True if {@code scope} is the browser tab hosting this app. */
    public boolean ownsTab(TabScope scope) {
        return tabScope != null && tabScope == scope;
    }

    /**
     * Tab-scope destroy hook (wired by {@link vaadinx.swing.app.SwingBridgeEmulatorsBootstrap}):
     * on a real app-tab close — the destroyed {@code scope} is this app's tab —
     * fires the D_shutdown_lifecycle {@code WINDOW_CLOSING} dispatch and ends the session; a
     * curtained (non-app) tab's close is ignored, leaving the app running.
     * Best-effort — a session already tearing down is fine.
     *
     * <p>Runs on the tab-scope reaper thread. The dispatch goes through
     * {@link vaadinx.swing.JFrame#dispatchShutdownFromTabClose} <em>directly</em>
     * rather than leaning on {@code session.close()} → {@code VaadinSession}-destroy:
     * that thread is request-less, so {@code close()} only sets state {@code CLOSING}
     * and {@code fireSessionDestroy} (hence the session-destroy backstop dispatch)
     * would not run until the HTTP-session timeout. We still call {@code close()} for
     * the eventual teardown; the dispatch dedups so the backstop won't re-fire.
     */
    public static void onTabClosed(VaadinSession session, TabScope scope) {
        if (session == null) {
            return;
        }
        final AppTab app = forSession(session);
        if (app == null || !app.ownsTab(scope)) {
            return;
        }
        try {
            vaadinx.swing.JFrame.dispatchShutdownFromTabClose(session);   // prompt WINDOW_CLOSING (D_shutdown_lifecycle)
        } catch (RuntimeException e) {
            if (log.isDebugEnabled()) log.debug("tab-close WINDOW_CLOSING dispatch failed: {}", e.toString());
        }
        try {
            session.close();                                             // eventual session teardown
        } catch (RuntimeException e) {
            if (log.isDebugEnabled()) log.debug("tab-close session.close() skipped: {}", e.toString());
        }
    }

    /**
     * Records the tab scope's value store so {@link AppInstance} can still reach it
     * on a thread with a session but no UI.
     *
     * <p>Holds the {@link Attributes} object rather than the {@link TabScope}
     * because the scope nulls its own reference to it once its destroy listeners
     * have run, and nothing pins that against {@code JFrame}'s session-destroy
     * listener firing the D_shutdown_lifecycle {@code WINDOW_CLOSING} the app cleans up in.
     */
    void rememberValues(Attributes values) {
        this.values = values;
    }

    /** @return {@link AppInstance}'s store, or {@code null} if it was never touched under a UI. */
    Attributes rememberedValues() {
        return values;
    }

    private static TabScope currentScopeOrNull() {
        try {
            return TabScope.getCurrent();
        } catch (RuntimeException e) {
            return null; // no tab scope in this context (browserless / not wired)
        }
    }
}
