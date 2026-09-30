/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.Timer
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.event.EventListenerList;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Emulator for {@link javax.swing.Timer}. Emulator-only per D_timer_swingworker — no
 * surrogate (Timer isn't a Component, no peer to back).
 *
 * <p>Backed by the shared per-session {@link vaadinx.UiScheduler} (a
 * single-thread {@link ScheduledExecutorService} on a platform daemon
 * thread). The scheduler thread does no user work — on fire it hops back to
 * the session's live UI thread via {@link vaadinx.EHelper#onSessionLiveUI},
 * which resolves the current UI and enters {@link vaadinx.EHelper#callSwing}
 * so the {@link ActionEvent} is delivered inside the R_callswing_envelope virtual-thread
 * envelope. User listeners run under the session lock and can park on
 * blocking dialogs.
 *
 * <p>The owning {@link VaadinSession} is captured at construction — <b>not</b>
 * the UI. Session scope is what lets a running Timer survive an F5
 * {@code @PreserveOnRefresh} teleport (the old UI closes, a fresh one takes
 * over): the scheduler outlives the refresh and each fire re-resolves the
 * now-current UI. Constructing a Timer with no current session throws
 * {@link IllegalStateException}. See {@code UiScopedFeaturesF5Test}.
 */
public class Timer implements java.io.Serializable {

    private static final Logger log = LoggerFactory.getLogger(Timer.class);

    // Static debug switch; matches javax.swing.Timer.setLogTimers / getLogTimers.
    private static volatile boolean logTimers = false;

    // ===========================================================
    // State (mirrors javax.swing.Timer's own fields).
    // ===========================================================

    private volatile int delay;
    private volatile int initialDelay;
    private volatile boolean repeats = true;
    private volatile boolean coalesce = true;
    private volatile String actionCommand;

    protected final EventListenerList listenerList = new EventListenerList();

    // ===========================================================
    // Runtime — guarded by `this` for start/stop/restart consistency.
    // ===========================================================

    private final VaadinSession session;
    private boolean running;
    private ScheduledFuture<?> future;
    // Set true when a fire has been submitted for delivery but not yet
    // drained; cleared in the EDT-side delivery's finally. Coalesce reads
    // this to drop duplicate fires (JDK contract: if the previous Action
    // event is still queued on the EDT when the next would be posted,
    // merge them).
    private volatile boolean pendingDelivery;

    // ===========================================================
    // Constructors.
    // ===========================================================

    /**
     * @throws IllegalArgumentException if {@code delay < 0} (JDK-faithful).
     * @throws IllegalStateException if no current Vaadin session — Timers are
     *         session-bound.
     */
    public Timer(int delay, ActionListener listener) {
        if (delay < 0) {
            throw new IllegalArgumentException("Invalid initial delay: " + delay);
        }
        this.delay = delay;
        this.initialDelay = delay;
        this.session = VaadinSession.getCurrent();
        if (this.session == null) {
            throw new IllegalStateException(
                    "new Timer(...) called with no current VaadinSession. Timers are bound to a Vaadin " +
                    "session and must be constructed on a UI thread (typically inside a peer event listener). A " +
                    "background thread cannot construct one even when it carries an EmulatorContext, which holds the " +
                    "session as a handle but never makes it current: construct the Timer on the UI thread and hand " +
                    "it to the background work.");
        }
        if (listener != null) addActionListener(listener);
    }

    // ===========================================================
    // Listener support.
    // ===========================================================

    public void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    public ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

    public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        return listenerList.getListeners(listenerType);
    }

    // ===========================================================
    // Properties — JDK-faithful field shadow + getter/setter pairs.
    // ===========================================================

    public int getDelay() {
        return delay;
    }

    /**
     * @throws IllegalArgumentException if {@code delay < 0} (JDK-faithful).
     */
    public void setDelay(int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Invalid delay: " + delay);
        }
        this.delay = delay;
    }

    public int getInitialDelay() {
        return initialDelay;
    }

    /**
     * @throws IllegalArgumentException if {@code initialDelay < 0} (JDK-faithful).
     */
    public void setInitialDelay(int initialDelay) {
        if (initialDelay < 0) {
            throw new IllegalArgumentException("Invalid initial delay: " + initialDelay);
        }
        this.initialDelay = initialDelay;
    }

    public boolean isRepeats() {
        return repeats;
    }

    public void setRepeats(boolean repeats) {
        this.repeats = repeats;
    }

    public boolean isCoalesce() {
        return coalesce;
    }

    public void setCoalesce(boolean coalesce) {
        this.coalesce = coalesce;
    }

    public String getActionCommand() {
        return actionCommand;
    }

    public void setActionCommand(String actionCommand) {
        this.actionCommand = actionCommand;
    }

    public static void setLogTimers(boolean flag) {
        logTimers = flag;
    }

    public static boolean getLogTimers() {
        return logTimers;
    }

    // ===========================================================
    // Lifecycle.
    // ===========================================================

    public synchronized boolean isRunning() {
        return running;
    }

    /**
     * Starts the Timer. Safe to call when already running — JDK-faithful no-op.
     *
     * <p>A started Timer holds the app open ({@link vaadinx.AutoShutdown}), so
     * disposing the last window while it runs leaves the session alive — the
     * status-bar-clock paper cut, reproduced (D_auto_shutdown).
     */
    public synchronized void start() {
        if (running) return;
        running = true;
        pendingDelivery = false;
        vaadinx.AutoShutdown.holdOpen(session, this);
        if (repeats) {
            future = vaadinx.UiScheduler.scheduleWithFixedDelay(
                    session, this::onFireFromScheduler, initialDelay, delay, TimeUnit.MILLISECONDS);
        } else {
            future = vaadinx.UiScheduler.schedule(
                    session, this::onFireFromScheduler, initialDelay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the Timer. Pending fires already queued on the EDT are
     * dropped on delivery (the EDT-side fire body re-checks {@link
     * #running}); fires that haven't yet reached the EDT are cancelled
     * via {@code future.cancel(false)}.
     */
    public synchronized void stop() {
        running = false;
        vaadinx.AutoShutdown.releaseHold(session, this);
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    public synchronized void restart() {
        stop();
        start();
    }

    // ===========================================================
    // Fire path.
    // ===========================================================

    /**
     * Runs on the per-session scheduler thread (no UI context). Hops to the
     * session's live UI via {@link vaadinx.EHelper#onSessionLiveUI}, which
     * resolves the current UI under the session lock (surviving an F5
     * teleport) and delivers inside {@link vaadinx.EHelper#callSwing}. The
     * delivered body re-checks {@link #running} (so a stop() between schedule
     * and delivery drops the fire) and respects {@link #coalesce} (a previous
     * fire still pending drops the new one). {@code pendingDelivery} is
     * cleared in the body's finally so coalescing recovers on the next tick;
     * if no UI is live (session going down) the body doesn't run, but the
     * scheduler is torn down with the session anyway.
     */
    private void onFireFromScheduler() {
        // Coalesce check at scheduler-side: if the previous fire hasn't
        // been delivered yet, merge by skipping. Matches JDK Timer's
        // post-then-merge semantics (the observable contract is "fires
        // collapse when the EDT is busy", which our pendingDelivery
        // flag captures).
        if (coalesce && pendingDelivery) {
            if (logTimers) log.debug("Timer fire coalesced (previous delivery still pending)");
            return;
        }
        pendingDelivery = true;
        vaadinx.EHelper.onSessionLiveUI(session, () -> {
            try {
                // A one-shot's hold ends before its listener runs: a Timer that disposes
                // the last window must not veto its own shutdown. Here rather than on the
                // scheduler thread, which holds no session lock (D_auto_shutdown).
                if (!repeats) vaadinx.AutoShutdown.releaseHold(session, this);
                if (isRunning()) fireActionPerformedOnEdt();
            } finally {
                pendingDelivery = false;
            }
        });
    }

    /**
     * Runs on the UI thread inside a {@link vaadinx.EHelper#callSwing}
     * VT continuation. Builds the {@link ActionEvent} and delivers to
     * each registered listener in registration order.
     */
    private void fireActionPerformedOnEdt() {
        ActionEvent ev = new ActionEvent(
                this, ActionEvent.ACTION_PERFORMED,
                actionCommand,
                System.currentTimeMillis(),
                0);
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            l.actionPerformed(ev);
        }
    }
}
