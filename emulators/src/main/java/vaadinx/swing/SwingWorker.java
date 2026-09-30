/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.SwingWorker
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.shared.Registration;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emulator for {@link javax.swing.SwingWorker}. Emulator-only per D_timer_swingworker — no
 * surrogate (SwingWorker isn't a Component).
 *
 * <p>Backed by a per-session pool of the JDK's own shape — ten daemon
 * <b>platform</b> threads over a {@code LinkedBlockingQueue} — so an eleventh
 * concurrent worker queues exactly as it does on the desktop
 * (D_worker_platform_threads). Lazy-attached on first {@link #execute()},
 * disposed when the session is destroyed (interrupts running workers).
 *
 * <p>EDT routing: {@link #publish(Object[])}, {@link #process(List)},
 * {@link #done()}, and PCE fires hop from the worker thread to the
 * session's live UI via {@link vaadinx.EHelper#onSessionLiveUI}, which
 * resolves the current UI and enters {@link vaadinx.EHelper#callSwing},
 * so user callbacks see the same {@link UI#getCurrent()} + virtual-thread
 * envelope as any other Swing-side event handler (R_callswing_envelope). PCE fires raised
 * from the EDT itself bypass the hop and run inline (fast path + matches
 * Swing's synchronous PCE-on-EDT delivery).
 *
 * <p>{@link #get()} and {@link #get(long, TimeUnit)} called from the EDT
 * throw {@link IllegalStateException} per D_gap_severity_triage case 1 (loom rescue
 * trigger): real Swing deadlocks; we fail-fast with a message naming
 * the cause, preserving the migrator's mental model.
 *
 * <p>A {@link vaadinx.EmulatorContext} — the owning {@link VaadinSession} plus
 * the user's browser time zone — is captured at construction (not the UI), so
 * an in-flight worker survives an F5 {@code @PreserveOnRefresh} teleport and
 * delivers {@code done()}/{@code process()} onto the UI that is live at
 * completion time, not the (closed) one it started under. See
 * {@code UiScopedFeaturesF5Test}. Constructing with no current session throws
 * {@link IllegalStateException}. {@link #doInBackground()} runs inside
 * {@link vaadinx.EmulatorContext#call}, so {@code SimpleDateFormat} / the
 * Calendar emulator format in the user's zone off the EDT rather than the
 * server default.
 */
public abstract class SwingWorker<T, V> implements RunnableFuture<T> {

    public enum StateValue {
        PENDING,
        STARTED,
        DONE
    }

    public static final String STATE_PROPERTY = "state";
    public static final String PROGRESS_PROPERTY = "progress";

    // ===========================================================
    // State.
    // ===========================================================

    private final vaadinx.EmulatorContext context;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private volatile StateValue state = StateValue.PENDING;
    private volatile T result;
    private volatile Throwable exception;
    private volatile boolean cancelled;
    private volatile boolean done;
    private volatile int progress;
    private volatile Thread workerThread;

    private final CountDownLatch doneLatch = new CountDownLatch(1);

    // Guarded by `publishedChunks`. processScheduled tracks whether a
    // process(List) has been queued on the EDT but not yet drained, so
    // subsequent publish() calls coalesce into the pending list rather
    // than spawning new ui.access tasks.
    private final List<V> publishedChunks = new ArrayList<>();
    private boolean processScheduled;

    // ===========================================================
    // Constructor.
    // ===========================================================

    /**
     * @throws IllegalStateException if no current Vaadin session — SwingWorkers
     *         are bound to the session they were constructed in.
     */
    protected SwingWorker() {
        if (VaadinSession.getCurrent() == null) {
            throw new IllegalStateException(
                    "new SwingWorker() called with no current VaadinSession. SwingWorkers are bound to a Vaadin " +
                    "session and must be constructed on a UI thread (typically inside a peer event listener). A " +
                    "background thread cannot construct one even when it carries an EmulatorContext, which holds the " +
                    "session as a handle but never makes it current: construct the worker on the UI thread and hand " +
                    "it to the background work.");
        }
        // Capture the emulator context (owning session + the user's browser zone).
        // doInBackground runs inside context.call (see run()), so SimpleDateFormat /
        // Calendar there format in the user's zone rather than the server default.
        this.context = vaadinx.EmulatorContext.get();
    }

    // ===========================================================
    // Subclass extension points.
    // ===========================================================

    /** Background work. Runs on a virtual thread off the EDT. */
    protected abstract T doInBackground() throws Exception;

    /** EDT-side callback for chunks published via {@link #publish(Object[])}. Default no-op. */
    protected void process(List<V> chunks) {}

    /** EDT-side callback after {@link #doInBackground()} returns (success, exception, or cancel). Default no-op. */
    protected void done() {}

    // ===========================================================
    // Lifecycle.
    // ===========================================================

    /**
     * Submits this worker to the per-UI VT executor. Idempotent — second
     * and subsequent calls are no-ops (matches JDK contract: a worker
     * may only be executed once).
     */
    public final void execute() {
        if (state != StateValue.PENDING) return;
        WorkerPool.forSession(context.session()).executor.submit(this);
    }

    /**
     * Worker body. Drives the state machine: PENDING → STARTED → run
     * doInBackground → DONE → schedule done() on EDT. The finally-block
     * always finalises, so a thrown {@code doInBackground} still
     * transitions to DONE and fires done() (matches JDK).
     */
    @Override
    public final void run() {
        workerThread = Thread.currentThread();
        try {
            if (cancelled) {
                // Cancelled before run started — skip the work, but still
                // finalise in the finally block so get() unblocks with
                // CancellationException and done() fires.
                return;
            }
            setState(StateValue.STARTED);
            try {
                // context.call makes the user's browser zone visible to
                // EmulatorContext.currentTimeZone() (and thus SimpleDateFormat /
                // Calendar) on this worker thread — the desktop had the user's
                // zone as the JVM default on every thread; here we propagate it.
                result = context.call(this::doInBackground);
            } catch (Throwable t) {
                exception = t;
                if (t instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            workerThread = null;
            setState(StateValue.DONE);
            done = true;
            doneLatch.countDown();
            // Resolve the live UI at completion time (survives an F5 teleport);
            // if none is live the session is going down and done() is dropped —
            // the user-visible state (isDone, get) is already correct.
            vaadinx.EHelper.onSessionLiveUI(context.session(), this::done);
        }
    }

    /**
     * Cancels the worker. If {@code mayInterruptIfRunning} is true and a
     * doInBackground is in flight, the worker thread is interrupted —
     * doInBackground's I/O calls or sleeps see {@link InterruptedException}.
     * Returns true unless the worker had already completed.
     */
    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        if (done) return false;
        cancelled = true;
        if (mayInterruptIfRunning) {
            Thread t = workerThread;
            if (t != null) t.interrupt();
        }
        return true;
    }

    @Override
    public final boolean isDone() {
        return done;
    }

    @Override
    public final boolean isCancelled() {
        return cancelled;
    }

    public final StateValue getState() {
        return state;
    }

    // ===========================================================
    // Result retrieval.
    // ===========================================================

    /**
     * Blocks until the worker completes; returns its result.
     *
     * <p>Allowed from the EDT only when {@link #isDone()} — that's the
     * canonical {@link #done()} call site, where the worker's already
     * finished and {@code get()} returns immediately without blocking.
     * Throws {@link IllegalStateException} if called from the EDT
     * before the worker finishes (D_gap_severity_triage case 1 loom rescue: under
     * Swing this would deadlock the EDT).
     *
     * @throws IllegalStateException if called from the EDT before the
     *         worker is done.
     */
    @Override
    public final T get() throws InterruptedException, ExecutionException {
        assertNotShuttingDown();
        assertNotEdtBeforeDone();
        doneLatch.await();
        return resolveResult();
    }

    /**
     * @throws IllegalStateException if called from the EDT before the
     *         worker is done (D_gap_severity_triage case 1).
     * @throws TimeoutException if the wait elapses without completion.
     */
    @Override
    public final T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        assertNotShuttingDown();
        assertNotEdtBeforeDone();
        if (!doneLatch.await(timeout, unit)) {
            throw new TimeoutException();
        }
        return resolveResult();
    }

    private T resolveResult() throws ExecutionException {
        if (cancelled) throw new CancellationException();
        if (exception != null) throw new ExecutionException(exception);
        return result;
    }

    private void assertNotShuttingDown() {
        // D_shutdown_lifecycle: inside the session-destroy → WINDOW_CLOSING dispatch the worker
        // pool is being torn down, so a blocking get() may never complete.
        // Name shutdown as the cause rather than let the caller hang or read a
        // cryptic downstream error.
        if (vaadinx.EHelper.isShuttingDown()) {
            throw new IllegalStateException(
                    "Application is shutting down — blocking on SwingWorker.get() may never complete "
                            + "(the worker pool is being torn down). Do synchronous cleanup only inside "
                            + "WINDOW_CLOSING listeners.");
        }
    }

    private void assertNotEdtBeforeDone() {
        if (UI.getCurrent() != null && !done) {
            throw new IllegalStateException(
                    "SwingWorker.get() called from the EDT before the worker finished; this would deadlock under Swing — " +
                    "dispatch asynchronously (use done()/process()) or move the call off the EDT. " +
                    "Calling get() from done() is fine — by then the worker is finished and get() returns immediately.");
        }
    }

    // ===========================================================
    // publish/process — coalescing snapshot dispatch.
    // ===========================================================

    /**
     * Coalescing publish. Appends chunks to a pending list; the first
     * publish since the last drain schedules one EDT-side
     * {@link #process(List)} call that drains and clears the list.
     * Subsequent publishes before the drain merge into the snapshot
     * the scheduled process(...) will see.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    protected final void publish(V... chunks) {
        if (chunks == null || chunks.length == 0) return;
        boolean schedule;
        synchronized (publishedChunks) {
            publishedChunks.addAll(Arrays.asList(chunks));
            schedule = !processScheduled;
            if (schedule) processScheduled = true;
        }
        if (!schedule) return;
        vaadinx.EHelper.onSessionLiveUI(context.session(), () -> {
            List<V> snapshot;
            synchronized (publishedChunks) {
                snapshot = new ArrayList<>(publishedChunks);
                publishedChunks.clear();
                processScheduled = false;
            }
            process(snapshot);
        });
    }

    // ===========================================================
    // Progress + PCE.
    // ===========================================================

    /**
     * @throws IllegalArgumentException if {@code progress} is outside
     *         {@code [0, 100]} (JDK-faithful).
     */
    protected final void setProgress(int progress) {
        if (progress < 0 || progress > 100) {
            throw new IllegalArgumentException("the value should be from 0 to 100");
        }
        if (progress == this.progress) return;
        int old = this.progress;
        this.progress = progress;
        firePropertyChange(PROGRESS_PROPERTY, old, progress);
    }

    public final int getProgress() {
        return progress;
    }

    private void setState(StateValue newState) {
        StateValue old = this.state;
        this.state = newState;
        firePropertyChange(STATE_PROPERTY, old, newState);
    }

    /**
     * Fires a PropertyChangeEvent. On-EDT callers fire synchronously
     * (matches Swing's same-thread PCE delivery); off-EDT callers hop
     * via {@link vaadinx.EHelper#onSessionLiveUI} so listeners always run
     * under the session's live UI lock inside a {@link
     * vaadinx.EHelper#callSwing} VT continuation (R_callswing_envelope).
     */
    public final void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (UI.getCurrent() != null) {
            // Already on an EDT (a UI thread) — fire synchronously, matching
            // Swing's same-thread PCE delivery. Under the single-app-per-session
            // invariant this UI belongs to the worker's session.
            pcs.firePropertyChange(propertyName, oldValue, newValue);
            return;
        }
        vaadinx.EHelper.onSessionLiveUI(context.session(),
                () -> pcs.firePropertyChange(propertyName, oldValue, newValue));
    }

    public final PropertyChangeSupport getPropertyChangeSupport() {
        return pcs;
    }

    public final void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public final void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public final void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public final void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    // ===========================================================
    // Per-session worker pool.
    // ===========================================================

    /** The JDK's own {@code SwingWorker.MAX_WORKER_THREADS}; an eleventh worker queues. */
    private static final int MAX_WORKER_THREADS = 10;

    private static final AtomicLong POOL_COUNTER = new AtomicLong();

    /**
     * Per-session worker pool; lazy-attached on first execute(), disposed when the
     * session is destroyed (shutdownNow interrupts running workers, which surfaces as
     * InterruptedException inside doInBackground).
     *
     * <p>Session-scoped, not UI-scoped, so an in-flight worker survives an F5
     * {@code @PreserveOnRefresh} teleport instead of being interrupted when
     * the old UI detaches — see the class header and {@link
     * vaadinx.EHelper#onSessionLiveUI}. Must be called under the session lock
     * (session-attribute access requires it); execute() reaches here from a UI
     * thread.
     */
    private static final class WorkerPool {

        final ExecutorService executor;

        private WorkerPool() {
            this.executor = newWorkerExecutor(POOL_COUNTER.incrementAndGet());
        }

        static WorkerPool forSession(VaadinSession session) {
            Objects.requireNonNull(session, "session");
            WorkerPool pool = session.getAttribute(WorkerPool.class);
            if (pool == null) {
                pool = new WorkerPool();
                session.setAttribute(WorkerPool.class, pool);
                final ExecutorService exec = pool.executor;
                VaadinService service = session.getService();
                if (service != null) {
                    // Self-removing one-shot listener; mirrors UiScheduler /
                    // SessionTempFiles cleanup wiring.
                    Registration[] reg = new Registration[1];
                    reg[0] = service.addSessionDestroyListener(event -> {
                        if (event.getSession() == session) {
                            exec.shutdownNow();
                            session.setAttribute(WorkerPool.class, null);
                            if (reg[0] != null) {
                                reg[0].remove();
                            }
                        }
                    });
                }
            }
            return pool;
        }

        /**
         * The JDK's pool, session-scoped: ten daemon platform threads over a
         * {@code LinkedBlockingQueue}, named {@code SwingWorker-…}.
         *
         * <p>Platform threads because the JDK's pool is platform threads
         * (D_worker_platform_threads). {@code execute()} is called from inside a
         * {@link vaadinx.EHelper#callSwing} UI fiber, so a virtual-thread pool here
         * would inherit the loom runner's scheduler, which carries such threads off
         * the UI carrier (D_inherited_scheduler).
         * {@code SwingWorkerTest.doInBackgroundRunsOffTheUiThread} pins the platform
         * pool.
         * {@code allowCoreThreadTimeOut} is the lone deviation from the JDK's pool,
         * which is one per app where this is one per session: idle sessions must
         * not each pin ten threads. Cap and queueing are unchanged.
         */
        private static ExecutorService newWorkerExecutor(long poolId) {
            ThreadFactory factory = new ThreadFactory() {
                private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = defaultFactory.newThread(r);
                    t.setName("SwingWorker-session-" + poolId + "-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            };
            ThreadPoolExecutor pool = new ThreadPoolExecutor(MAX_WORKER_THREADS, MAX_WORKER_THREADS,
                    10L, TimeUnit.MINUTES, new LinkedBlockingQueue<>(), factory);
            pool.allowCoreThreadTimeOut(true);
            return pool;
        }
    }
}
