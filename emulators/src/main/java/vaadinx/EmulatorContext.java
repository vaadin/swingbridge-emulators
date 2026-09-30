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

import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Carries the per-user emulator context — today the browser time zone, plus the
 * owning {@link VaadinSession} — from a Vaadin UI thread onto a background
 * thread, so emulator date code ({@link vaadinx.text.SimpleDateFormat} and the
 * Calendar emulator) formats in the user's zone off the UI thread too.
 *
 * <p><b>Why this is needed.</b> On the desktop one JVM serves one user, so
 * {@code TimeZone.getDefault()} is the user's zone on every thread — EDT and
 * background alike. On the server the user's zone is per-session and lives on
 * the UI thread; a background thread doesn't know whose user it serves, so
 * without help it falls back to the <em>server</em> zone, which is usually
 * wrong. This class is the propagation vehicle: {@link #get()} on the UI thread
 * snapshots the zone, {@link #run(Runnable)} / {@link #call(Callable)} make it
 * visible on the worker thread.
 *
 * <p><b>This never calls {@link VaadinSession#setCurrent} or
 * {@code UI.setCurrent}.</b> Inside a {@link #run}/{@link #call} body,
 * {@code VaadinSession.getCurrent()} stays {@code null} — only the captured
 * browser time zone is exposed, and only to emulator date code via
 * {@link #currentTimeZone()}. The captured {@link VaadinSession} is a <b>handle</b>,
 * never a current session: it is what lock-aware delivery ({@code SwingWorker.done()}
 * through {@link EHelper#onSessionLiveUI}) and the off-UI-thread peer write
 * ({@link vaadinx.awt.Component#withPeer} through {@link EHelper#runInUIThread}) take
 * the lock <em>with</em>. It is never used for off-lock session-attribute access.
 *
 * <p>{@code SB-Emulators}'s own background primitives ({@code SwingWorker}) capture and
 * apply the context automatically. A migrator's own executor gets it with one wrap:
 * <pre>{@code
 * this.pool = EmulatorContext.wrap(Executors.newFixedThreadPool(4));   // once, at startup
 * pool.submit(() -> { ...; label.setText("done"); });                  // ordinary submits
 * }</pre>
 * A thread the app creates by hand carries it the long way, since there is no
 * construction to wrap:
 * <pre>{@code
 * EmulatorContext ctx = EmulatorContext.get();      // on the UI thread
 * new Thread(() -> ctx.run(() -> { ... })).start(); // on the worker thread
 * }</pre>
 */
public final class EmulatorContext {

    private static final Logger log = LoggerFactory.getLogger(EmulatorContext.class);

    /** The context made active for the current thread by {@link #run}/{@link #call}. */
    private static final ThreadLocal<EmulatorContext> CURRENT = new ThreadLocal<>();

    /**
     * Per-thread dedup for the {@link #currentTimeZone()} server-zone-fallback
     * WARN, so a background formatting loop logs the diagnostic once, not per call.
     */
    private static final ThreadLocal<Boolean> WARNED_SERVER_ZONE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final VaadinSession session;
    private final ZoneId zone;

    private EmulatorContext(VaadinSession session, ZoneId zone) {
        this.session = session;
        this.zone = zone;
    }

    // ===========================================================
    // Obtain.
    // ===========================================================

    /**
     * The emulator context for the current thread. On a UI thread (a current
     * {@link VaadinSession}) builds a fresh snapshot of the session + browser
     * zone; on a background thread returns whatever {@link #run}/{@link #call}
     * installed. Lenient on the zone — the snapshot's {@link #zone()} may be
     * {@code null} if {@code BrowserTimeZone.fetch()} hasn't run yet; the loud
     * SD_browser_timezone check is enforced only by {@link #currentTimeZone()}, when a zone is
     * actually needed to format.
     *
     * @throws IllegalStateException on a background thread with no context —
     *         call {@code get()} on the UI thread and propagate it via
     *         {@link #run}/{@link #call}.
     */
    public static EmulatorContext get() {
        EmulatorContext ctx = getOrNull();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No EmulatorContext on this background thread. Call EmulatorContext.get() "
                            + "on a Vaadin UI thread (where a VaadinSession is current) and "
                            + "propagate it to the worker via ctx.run(...) / ctx.call(...). "
                            + "SwingWorker does this for you.");
        }
        return ctx;
    }

    /**
     * As {@link #get()} but returns {@code null} instead of throwing when there
     * is no context. <b>Never throws</b> — including on a session-associated but
     * <em>unlocked</em> thread (Vaadin's license checker on a Jetty request-pool
     * thread is the canonical case): a session snapshot captures the browser zone
     * via {@link BrowserTimeZone#getOrNull()}, which is lock-safe (it briefly
     * locks to read when needed). With no current session, falls back to whatever
     * context {@link #run}/{@link #call} propagated onto this thread.
     */
    public static EmulatorContext getOrNull() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            return new EmulatorContext(session, BrowserTimeZone.getOrNull());
        }
        return CURRENT.get();
    }

    // ===========================================================
    // Propagate + run.
    // ===========================================================

    /**
     * Runs {@code body} on the calling thread with this context active — its
     * zone visible to {@link #currentTimeZone()} — restoring any previous
     * context in a {@code finally} (so it nests, and pooled threads don't leak
     * a stale context). Intended for background threads; on a UI thread the live
     * session takes precedence in {@link #currentTimeZone()}, so this is a no-op
     * for zone resolution there. Does <b>not</b> install a current session
     * (see class javadoc).
     */
    public void run(Runnable body) {
        EmulatorContext previous = CURRENT.get();
        CURRENT.set(this);
        try {
            body.run();
        } finally {
            restore(previous);
        }
    }

    /** As {@link #run(Runnable)} for a body that returns a value or throws. */
    public <T> T call(Callable<T> body) throws Exception {
        EmulatorContext previous = CURRENT.get();
        CURRENT.set(this);
        try {
            return body.call();
        } finally {
            restore(previous);
        }
    }

    private static void restore(EmulatorContext previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    // ===========================================================
    // Propagate across an executor.
    // ===========================================================

    /**
     * Wraps {@code delegate} so every task submitted through it runs with the submitting
     * thread's context active — the {@link #run(Runnable)} recipe applied once, at the
     * executor, instead of at every call site:
     *
     * <pre>{@code
     * // once, wherever the app builds its pool — no session needed here
     * this.pool = EmulatorContext.wrap(Executors.newFixedThreadPool(4));
     *
     * // thereafter, ordinary submits from a UI thread; the worker sees the context
     * pool.submit(() -> { ...; label.setText("done"); });
     * }</pre>
     *
     * <p>The context is captured <b>per submit, on the submitting thread</b> — never when
     * the executor is wrapped. That is the whole reason this exists: a pool is typically
     * built once at app startup, where there is no session to capture, so a wrap-time
     * snapshot would capture nothing for the pool's entire life. Capturing per submit also
     * chains — a submit from a worker that already carries a context propagates that one.
     *
     * <p>A submit from a thread with no context of its own is passed through unchanged, so
     * the task runs contextless rather than borrowing an unrelated user's session.
     *
     * @param delegate the app's own executor; it is not shut down or otherwise adopted
     * @return a view over {@code delegate} — both remain usable, and only work submitted
     *         through the returned instance carries the context
     */
    public static ExecutorService wrap(ExecutorService delegate) {
        return new ContextPropagatingExecutorService(delegate);
    }

    /** As {@link #wrap(ExecutorService)}, for a bare {@link Executor}. */
    public static Executor wrap(Executor delegate) {
        return command -> delegate.execute(capturing(command));
    }

    private static Runnable capturing(Runnable task) {
        EmulatorContext ctx = getOrNull();
        if (ctx != null) {
            return () -> ctx.run(task);
        }
        String site = captureSubmitSite();
        return () -> runRecordingSubmitSite(site, task);
    }

    private static <T> Callable<T> capturing(Callable<T> task) {
        EmulatorContext ctx = getOrNull();
        if (ctx != null) {
            return () -> ctx.call(task);
        }
        String site = captureSubmitSite();
        return () -> callRecordingSubmitSite(site, task);
    }

    private static <T> Collection<Callable<T>> capturingAll(Collection<? extends Callable<T>> tasks) {
        EmulatorContext ctx = getOrNull();
        List<Callable<T>> wrapped = new ArrayList<>(tasks.size());
        if (ctx == null) {
            String site = captureSubmitSite();
            for (Callable<T> task : tasks) {
                wrapped.add(() -> callRecordingSubmitSite(site, task));
            }
            return wrapped;
        }
        for (Callable<T> task : tasks) {
            wrapped.add(() -> ctx.call(task));
        }
        return wrapped;
    }

    // ===========================================================
    // Contextless-submit diagnostics (D_submit_site).
    // ===========================================================

    /** Frames kept from a contextless submit — raise if six proves too shallow to reach app code. */
    private static final int SUBMIT_SITE_FRAMES = 6;

    private static final StackWalker WALKER = StackWalker.getInstance();

    /**
     * Where a task now running was submitted from, when that submit carried no context.
     * Set only around such a task, so a thread that never ran one reads {@code null}.
     */
    private static final ThreadLocal<String> SUBMIT_SITE = new ThreadLocal<>();

    /**
     * The submitting frames of the contextless submit that produced the currently running
     * task, ready to append to a message, or {@code null} if this task did not come through
     * {@link #wrap} — a plain {@code Thread}, or an executor nobody wrapped.
     *
     * @return already-indented lines, one {@code at …} per frame
     */
    static String submitSiteOrNull() {
        return SUBMIT_SITE.get();
    }

    /**
     * Snapshots the submitting frames, skipping this class's own.
     *
     * <p>{@code StackWalker} with a limit rather than {@code new Throwable()}: the latter
     * walks the whole stack at construction, where this stops after {@link #SUBMIT_SITE_FRAMES}.
     * Frames are formatted here because a {@code StackFrame} is valid only during the walk.
     */
    private static String captureSubmitSite() {
        return WALKER.walk(frames -> frames
                .dropWhile(f -> isOurs(f.getClassName()))
                .limit(SUBMIT_SITE_FRAMES)
                .map(f -> "\tat " + f)
                .collect(java.util.stream.Collectors.joining("\n")));
    }

    /**
     * Whether a frame is this class's own plumbing — {@code EmulatorContext} itself or a nested
     * type such as {@code ContextPropagatingExecutorService}.
     *
     * <p>Deliberately not {@code startsWith(getName())}: that also swallows a sibling whose
     * name merely extends ours, which is how {@code EmulatorContextTest}'s own frames vanished.
     */
    private static boolean isOurs(String className) {
        String self = EmulatorContext.class.getName();
        return className.equals(self) || className.startsWith(self + "$");
    }

    private static void runRecordingSubmitSite(String site, Runnable task) {
        String previous = SUBMIT_SITE.get();
        SUBMIT_SITE.set(site);
        try {
            task.run();
        } finally {
            restoreSubmitSite(previous);
        }
    }

    private static <T> T callRecordingSubmitSite(String site, Callable<T> task) throws Exception {
        String previous = SUBMIT_SITE.get();
        SUBMIT_SITE.set(site);
        try {
            return task.call();
        } finally {
            restoreSubmitSite(previous);
        }
    }

    /** {@code remove()} rather than {@code set(null)}: a pooled thread must not retain the site. */
    private static void restoreSubmitSite(String previous) {
        if (previous == null) {
            SUBMIT_SITE.remove();
        } else {
            SUBMIT_SITE.set(previous);
        }
    }

    /**
     * The {@link #wrap(ExecutorService)} view: every submitting entry point captures, every
     * lifecycle method delegates untouched.
     */
    private record ContextPropagatingExecutorService(ExecutorService delegate) implements ExecutorService {

        @Override
        public void execute(Runnable command) {
            delegate.execute(capturing(command));
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            return delegate.submit(capturing(task));
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            return delegate.submit(capturing(task), result);
        }

        @Override
        public Future<?> submit(Runnable task) {
            return delegate.submit(capturing(task));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            return delegate.invokeAll(capturingAll(tasks));
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegate.invokeAll(capturingAll(tasks), timeout, unit);
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws InterruptedException, ExecutionException {
            return delegate.invokeAny(capturingAll(tasks));
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.invokeAny(capturingAll(tasks), timeout, unit);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    // ===========================================================
    // Accessors.
    // ===========================================================

    /**
     * The captured session — retained only as a handle for lock-aware result
     * delivery (see class javadoc); it is never made current and grants no UI
     * access.
     */
    public VaadinSession session() {
        return session;
    }

    /** The captured browser zone; may be {@code null} if unfetched at capture time. */
    public ZoneId zone() {
        return zone;
    }

    // ===========================================================
    // Zone resolver for emulator date code.
    // ===========================================================

    /**
     * The time zone emulator date code ({@link vaadinx.text.SimpleDateFormat},
     * the Calendar emulator) should use on the current thread:
     * <ul>
     *   <li>UI thread → the browser zone ({@link BrowserTimeZone#get()}, which
     *       throws SD_browser_timezone if {@code fetch()} was never called — a loud, one-line-fix
     *       wiring bug);</li>
     *   <li>background thread with a propagated context → that context's zone;</li>
     *   <li>background thread with none (or a zone-less context) → the server
     *       default zone, best-effort, with a once-per-thread WARN pointing at
     *       {@link #run}/{@link #call}.</li>
     * </ul>
     * Never returns {@code null}; the only throw is the UI-thread SD_browser_timezone case.
     */
    public static TimeZone currentTimeZone() {
        if (VaadinSession.getCurrent() != null) {
            return TimeZone.getTimeZone(BrowserTimeZone.get());
        }
        EmulatorContext ctx = CURRENT.get();
        if (ctx != null && ctx.zone != null) {
            return TimeZone.getTimeZone(ctx.zone);
        }
        warnServerZoneOnce();
        return TimeZone.getDefault();
    }

    private static void warnServerZoneOnce() {
        if (Boolean.TRUE.equals(WARNED_SERVER_ZONE.get())) {
            return;
        }
        WARNED_SERVER_ZONE.set(Boolean.TRUE);
        String msg = "emulator date code ran on a background thread with no EmulatorContext; "
                + "using the server default zone (" + TimeZone.getDefault().getID() + ") — "
                + "capture EmulatorContext.get() on the UI thread and wrap the background work "
                + "in ctx.run(...) / ctx.call(...) to format in the user's browser zone.";
        log.warn(msg);
        EHelper.warnHook.accept(msg);
    }
}
