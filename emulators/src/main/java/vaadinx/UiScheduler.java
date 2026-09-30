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

import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * INTERNAL — do not use. Not part of the emulated Swing API surface and has
 * no stability guarantee; the signature exists only to serve emulator
 * internals and may change without notice.
 *
 * <p>A shared per-{@link VaadinSession} {@link ScheduledExecutorService} for
 * emulator machinery that needs to fire on a wall-clock cadence —
 * {@link vaadinx.swing.Timer} repeats, the {@link vaadinx.awt.FileDialog}
 * SAVE download-size watcher, and any future periodic chore. One single
 * platform <em>daemon</em> thread per session, re-used by every caller;
 * lazy-attached on first {@link #forSession(VaadinSession)} and disposed (via
 * {@code shutdownNow()}) when the session is destroyed. Concurrent sessions
 * get independent schedulers.
 *
 * <p><b>Session-scoped, not UI-scoped, by design (F5 / {@code
 * @PreserveOnRefresh}).</b> On a browser refresh Vaadin closes the old UI and
 * teleports the preserved component tree onto a fresh one; a UI-scoped
 * scheduler would be {@code shutdownNow()}'d on the old UI's detach, silently
 * killing any Timer that survived the teleport. Session scope outlives the
 * refresh, so a repeating Timer keeps firing. The fire itself resolves the
 * now-current UI lazily via {@link EHelper#onSessionLiveUI} — see that method
 * and {@code UiScopedFeaturesF5Test}.
 *
 * <p>The scheduler thread does <em>no</em> user work and holds no UI context:
 * callers schedule a task that immediately hops back onto the session's live
 * UI thread (via {@link EHelper#onSessionLiveUI}, and through
 * {@link EHelper#callSwing} per R_callswing_envelope). Because every task returns quickly after
 * enqueuing that hop, a single shared thread never meaningfully serializes its
 * callers. Since the thread holds no lock, a task body reaches the session's
 * {@code ErrorHandler} only through {@link EHelper#reportUncaught}.
 *
 * <p><b>A throwing task is reported and keeps repeating.</b> That is the faithful
 * answer, not the pool's: a JDK {@code Timer} does not stop firing because one fire
 * threw — the EDT catches it and keeps pumping — whereas a bare
 * {@code ScheduledExecutorService} cancels the future and discards the throwable,
 * which would strand a repeating {@link vaadinx.swing.Timer} forever with no log,
 * no handler and nothing to grep for. See {@code D_uncaught_handler_chain}.
 *
 * <p>Scheduling requires the session lock — the scheduler is a session attribute, and
 * reading or writing one does. Emulator callers reach here from a UI thread (Timer
 * construction / start under {@code callSwing}, or a peer listener), where it is held.
 */
public final class UiScheduler {

    private static final String KEY = UiScheduler.class.getName();
    private static final AtomicLong SESSION_COUNTER = new AtomicLong();

    private UiScheduler() {
    }

    /** Schedules {@code body} to run once after {@code delay}. */
    public static ScheduledFuture<?> schedule(
            VaadinSession session, Runnable body, long delay, TimeUnit unit) {
        return forSession(session).schedule(guarded(session, body), delay, unit);
    }

    /**
     * Schedules {@code body} to repeat, each run starting {@code delay} after the previous
     * one finished. A run that throws is reported and the repeat continues (see class doc).
     */
    public static ScheduledFuture<?> scheduleWithFixedDelay(
            VaadinSession session, Runnable body, long initialDelay, long delay, TimeUnit unit) {
        return forSession(session).scheduleWithFixedDelay(
                guarded(session, body), initialDelay, delay, unit);
    }

    /**
     * Wraps rather than hooking {@code afterExecute}, which sees a periodic task
     * only once it has already been cancelled — too late to keep it repeating.
     */
    private static Runnable guarded(VaadinSession session, Runnable body) {
        Objects.requireNonNull(body, "body");
        return () -> {
            try {
                body.run();
            } catch (Throwable t) {
                EHelper.reportUncaught(session, t);
            }
        };
    }

    /**
     * Returns the per-session scheduler, creating it — and registering its
     * session-destroy disposal — on first call.
     *
     * <p>Private on purpose: a caller holding the raw executor submits an
     * unguarded body, and the class contract is that no task can silently
     * kill its own repeat.
     */
    private static ScheduledExecutorService forSession(VaadinSession session) {
        Objects.requireNonNull(session, "session");
        ScheduledExecutorService executor = (ScheduledExecutorService) session.getAttribute(KEY);
        if (executor == null) {
            long id = SESSION_COUNTER.incrementAndGet();
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "vaadinx-UiScheduler-session-" + id);
                t.setDaemon(true);
                return t;
            });
            session.setAttribute(KEY, executor);
            registerShutdownOnSessionDestroy(session, executor);
        }
        return executor;
    }

    /**
     * Self-removing one-shot listener: shuts the scheduler down when the
     * session is destroyed, then unregisters itself so we don't leak a
     * listener per session across the service lifetime (mirrors
     * {@link SessionTempFiles}' cleanup wiring).
     */
    private static void registerShutdownOnSessionDestroy(VaadinSession session, ScheduledExecutorService executor) {
        VaadinService service = session.getService();
        if (service == null) {
            // No service to hang the listener on (unusual outside tests) — the
            // daemon thread then relies on JVM-exit cleanup. Don't fail over it.
            return;
        }
        Registration[] reg = new Registration[1];
        reg[0] = service.addSessionDestroyListener(event -> {
            if (event.getSession() == session) {
                executor.shutdownNow();
                session.setAttribute(KEY, null);
                if (reg[0] != null) {
                    reg[0].remove();
                }
            }
        });
    }
}
