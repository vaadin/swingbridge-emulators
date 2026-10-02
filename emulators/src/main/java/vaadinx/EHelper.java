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

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.mvysny.blockingdialogs.UIFibers;
import com.github.mvysny.blockingdialogs.WaitDiedException;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Internal plumbing shared by every {@code vaadinx.*} emulator — hand-written,
 * so it lives outside the generated {@code vaadinx.awt.Component} that would
 * otherwise clobber it on regeneration. A grab-bag by design; the members
 * cluster into seven families:
 *
 * <ul>
 *   <li><b>Incompleteness hooks</b> (R_match_swing_errors/D_never_fail_on_gaps) — {@link #onUnimplemented},
 *       {@link #onUnsupported}, {@link #onNoop}, {@link #onUnsupportedPeerShape}: WARN
 *       (or DEBUG/ERROR) and continue, never throw.</li>
 *   <li><b>Thread hygiene</b> — {@link #checkUIThread}: the once-per-JVM report
 *       that emulator code reached its peer off the Vaadin UI thread, wired into
 *       {@link vaadinx.awt.Component#getPeer()}; and {@link #runInUIThread}, the
 *       synchronous Swing→peer write hop that lets such code work anyway.</li>
 *   <li><b>The R_callswing_envelope callback seam</b> — {@link #callSwing}: the virtual-thread
 *       envelope every peer→Swing listener funnels through.</li>
 *   <li><b>Live-UI resolution</b> — {@link #singleLiveUI}, {@link
 *       #onSessionLiveUI}, {@link #awaitModal}: finding the one UI an app lives
 *       in across an F5 teleport (via {@link AppTab}, D_session_scoped_pools/D_active_ui_pointer).</li>
 *   <li><b>Peer↔emulator registry</b> — {@link #onCreated},
 *       {@link #getEmulator}, and the id/name stamping ({@link #applyEmulatorId}).</li>
 *   <li><b>Window registry</b> — {@link #getWindows} plus the three call-graph
 *       edges that feed it ({@link #onWindowCreated},
 *       {@link #onWindowDisplayable}, {@link #onWindowUndisplayable}) over
 *       {@link WindowRegistry}, whose undisplayable edge also arms
 *       {@link AutoShutdown}.</li>
 *   <li><b>Enumeration + conversion</b> — {@link #getDialogs},
 *       {@link #toVaadinIconComponent}.</li>
 * </ul>
 *
 * Thread contracts vary per method and are stated there: the hooks and
 * conversions are callable from anywhere; {@link #callSwing} requires a current
 * UI; the live-UI family must hold the session lock.
 */
public final class EHelper {

    private static final Logger log = LoggerFactory.getLogger(EHelper.class);

    /**
     * Test-only capture sink for {@link #onUnimplemented}, {@link #onUnsupported},
     * and {@link #onUnsupportedPeerShape}. Tests install a collector to assert "this
     * path fires no stub-level WARNs" without having to reconfigure logging;
     * production stays on the no-op default so call sites can feed the hook
     * unconditionally (no null guards).
     *
     * <p>Intentionally a plain field and not an AtomicReference: tests install
     * the hook from a single thread around a known-single-threaded Karibu
     * scenario, then restore the default; no cross-thread concurrency to
     * defend against.
     */
    public static java.util.function.Consumer<String> warnHook = msg -> {};

    private EHelper() {}

    /** Warn-and-continue stub hook. Never throws — see D_never_fail_on_gaps. */
    public static void onUnimplemented(String className, String method, Object... args) {
        String msg = "Unimplemented " + className + "." + method + "(" + format(args) + ")";
        log.warn(msg);
        warnHook.accept(msg);
    }

    /**
     * Whether {@link #checkUIThread} has already reported. Read before the thread
     * test so the steady-state cost of the check is a single volatile read on a
     * path ({@link vaadinx.awt.Component#getPeer}) that every emulator takes
     * constantly.
     */
    private static final java.util.concurrent.atomic.AtomicBoolean offEdtWarned =
            new java.util.concurrent.atomic.AtomicBoolean();

    private static final String OFF_EDT_WARNING = """
            A Swing component's Vaadin peer was reached from a thread that is not the Vaadin UI \
            thread — typically a SwingWorker.doInBackground() body or a hand-rolled Thread.

            Swing's own threading policy already forbids this: "All Swing components and related \
            classes, unless otherwise documented, must be accessed on the event dispatching thread" \
            (javax.swing package javadoc). The JDK enforces nothing, so a desktop app can violate \
            it for years with no visible symptom, which is why this is common in real Swing code.

            Under SB-Emulators the same code also drives Vaadin components. Their state is \
            likewise unsynchronized, and it is additionally shared with the request thread that \
            serialises changes to the browser — so the failure modes are the same in kind as on \
            the desktop and more likely in practice: updates lost or interleaved, components that \
            render half-configured, state the browser never receives, and, where the Vaadin API \
            resolves a resource URL or reaches for the current UI, an outright IllegalStateException.

            The fix is the same shape as Swing's: capture the UI while still on the UI thread \
            (UI ui = UI.getCurrent()) and run the component work inside ui.access(...), or move it \
            out of doInBackground() into SwingWorker.done() / process(), which are already \
            delivered on the UI thread.

            Logged once per JVM. The stack trace below is the first off-thread access seen, not \
            necessarily the only one — other call sites stay silent.""";

    /**
     * Reports, once per JVM, that emulator code reached its Vaadin peer from off the
     * Vaadin UI thread. Never throws and never changes behaviour: SB-Emulators
     * tolerates off-thread component work (it is what real Swing apps do, and Vaadin
     * components happen to construct and mutate fine while detached), so this is an
     * observation, not a gate.
     *
     * <p>Wired into {@link vaadinx.awt.Component#getPeer()} rather than sprinkled over
     * every mutator: the accessor is the one narrow waist between an emulator and its
     * peer, so a new emulator method cannot forget it. Because the report is once per
     * JVM, over-firing (a read, internal plumbing) costs nothing while under-firing is
     * the failure — which is why the chokepoint is the accessor and not a curated list
     * of write paths.
     *
     * <p>Deliberately <b>not</b> routed through {@link #warnHook}: that channel is the
     * stub-WARN inventory the {@code WarnInventoryTest} exit gates assert on, and this
     * is thread hygiene in the migrator's own code, not an SB-Emulators gap.
     *
     * <p>Defers to {@link vaadinx.swing.SwingUtilities#isEventDispatchThread()} for what
     * "the UI thread" means, so there is exactly one answer to that question; a second
     * copy would eventually disagree with the one migrators can call themselves. Silent
     * during {@linkplain #isShuttingDown() shutdown}, where a UI-less teardown thread is
     * expected rather than a mistake (R_callswing_envelope).
     */
    public static void checkUIThread() {
        if (offEdtWarned.get()) return;
        if (vaadinx.swing.SwingUtilities.isEventDispatchThread()) return;
        // Not plain isShuttingDown(): the flag lives in a session attribute, and
        // VaadinSession's default SessionLockCheckStrategy asserts the lock on every
        // read. This method's whole point is running on threads that may hold nothing,
        // so it asks only when the answer is legally readable. An unlocked caller
        // therefore reports — which is right: it is not in the teardown dispatch, where
        // the lock is always held.
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null && session.hasLock() && isShuttingDown(session)) return;
        if (offEdtWarned.compareAndSet(false, true)) {
            log.warn(OFF_EDT_WARNING, new Throwable("off-UI-thread peer access — diagnostic stack, not a failure"));
        }
    }

    /** A hop slower than this is worth a WARN — see {@link #runInUIThread}'s instrumentation. */
    private static final long SLOW_UI_HOP_MILLIS = 1000;

    /**
     * Runs {@code body} on the Vaadin UI thread and <b>returns only once it has run</b> —
     * the seam a Swing→peer write goes through when it may be called from a background
     * thread. One contract on every thread: inline when a UI is already current,
     * otherwise the session lock is acquired and the body runs before this returns.
     *
     * <p>Not {@link #callSwing}'s inverse and not its sibling: {@code callSwing} is the
     * peer→Swing envelope (R_callswing_envelope) and spawns a virtual thread so a modal
     * dialog can park. This is a Swing→peer <em>write</em>, which cannot park, so no VT
     * is spawned and the body simply runs under the lock.
     *
     * <p><b>Synchronous on purpose, and the reasoning is a decision, not a default.</b>
     * The alternative — {@code UI.access()} — never runs inline even for a caller holding
     * the lock (flow-server's {@code ensureAccessQueuePurged}: "if the lock is held by the
     * current thread, we just release it knowing that the queue gets purged once the lock
     * is ultimately released"), so it would defer every write past the end of the request
     * and reorder background writes against inline ones. An app whose EDT blocks on a
     * worker that needs the EDT deadlocks on real Swing too, so blocking here reproduces
     * a JDK failure mode; deferring would invent an execution order Swing never had, which
     * is what R_no_silent_improvements forbids. The lock is taken directly rather than via
     * {@link #callOnLiveUISync}'s queue-and-wait so a stall is attributable — a thread
     * parked on a lock names its owner in a thread dump; one parked on a latch does not.
     *
     * <p><b>Deliberately unbounded.</b> A hand-rolled {@code tryLock} + current-instance
     * dance is likelier to be wrong than the deadlock is to happen, so a slow hop is
     * reported (see {@link #SLOW_UI_HOP_MILLIS}) rather than broken.
     *
     * <p><b>No {@link EmulatorContext} on the thread throws</b> (R_match_swing_errors case (7),
     * D_no_context_throws): the migrator started this work on a thread that never carried one,
     * and the fix is one line at its construction — {@code EmulatorContext.wrap(executor)}, or
     * {@code ctx.run(...)} for a hand-rolled {@code Thread}. When the task came through a wrapped
     * executor whose <em>submitter</em> had no context, the message says so and names the submit
     * frames instead (D_submit_site). That throw does not depend on
     * {@code callerPlansToBlockAfterwards}: a missing context is a configuration error either way.
     *
     * <p>Callers may block in a constructor through this — {@code new JLabel(icon)} on a
     * background thread waits for the session lock. That is a property no Swing constructor
     * has, and it is the accepted cost of letting the icon arrive at all.
     *
     * <p>Not the rejected "is this write cosmetic?" flag, which needed a taxonomy of peer
     * operations nobody can finish. This asks whether the <em>caller</em> is about to
     * block, which the caller always knows, and the set that answers yes is small and
     * closed — the modal park and what funnels into it.
     *
     * @param callerPlansToBlockAfterwards a modal show, not an ordinary write — the caller waits
     *                  for a browser answer once this returns. (Named for the <em>caller</em>
     *                  because this method always blocks; the flag is about what happens next.)
     *                  It decides what a <em>reachable session with no live UI</em> means, and
     *                  only that: {@code false} keeps R_decline_effect_only's bargain and drops
     *                  the effect, since the state is stored and the event fires regardless;
     *                  {@code true} throws, because degrading would be followed by parking on a
     *                  latch nothing can ever count down. Mandatory rather than defaulted so the
     *                  question is answered at every site.
     * @throws IllegalStateException if this thread carries no {@link EmulatorContext}
     * @throws BrowserSessionClosedError if {@code callerPlansToBlockAfterwards} and no live UI is
     *                  reachable — a closed tab, or a session that went away while this waited
     */
    public static void runInUIThread(boolean callerPlansToBlockAfterwards, Runnable body) {
        if (vaadinx.swing.SwingUtilities.isEventDispatchThread()) {
            body.run();
            return;
        }
        EmulatorContext ctx = EmulatorContext.getOrNull();
        VaadinSession session = ctx != null ? ctx.session() : null;
        if (session == null) {
            throw new IllegalStateException(noContextMessage());
        }
        long startedAt = System.nanoTime();
        try {
            session.accessSynchronously(() -> {
                UI live = singleLiveUI(session);
                if (live == null) {
                    // Teardown, or an app that never marked an AppTab. Nothing to push to.
                    if (callerPlansToBlockAfterwards) {
                        throw new BrowserSessionClosedError("This thread is about to wait for an "
                                + "answer from the browser, but the browser tab is gone (no live UI "
                                + "in this session), so no answer can ever arrive.");
                    }
                    if (log.isDebugEnabled()) log.debug("runInUIThread: no live UI; effect dropped");
                    return;
                }
                live.accessSynchronously(body::run);
            });
        } catch (RuntimeException e) {
            // Session invalidated while we waited.
            if (callerPlansToBlockAfterwards) {
                throw new BrowserSessionClosedError("This thread is about to wait for an answer "
                        + "from the browser, but its session went away while it waited for the "
                        + "session lock, so no answer can ever arrive.", e);
            }
            // Same bargain as a missing UI: the Swing-side state stands, the effect is lost.
            log.warn("A component update was dropped: the session went away while waiting for its lock ({})",
                    e.toString());
            return;
        }
        long waitedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        if (waitedMillis >= SLOW_UI_HOP_MILLIS) {
            log.warn("A background component update held up the UI thread for {} ms. Sustained "
                    + "contention here is the expected cost of touching components off the UI thread "
                    + "(each write waits for the session lock); batch the work, or move it onto the "
                    + "UI thread, if this repeats.", waitedMillis);
        }
    }

    /**
     * The {@link #runInUIThread} no-context message, which says something different depending on
     * whether {@link EmulatorContext#wrap} saw the submit — and the wrapped case is the one worth
     * the extra text, because there the migrator has already done what the generic advice asks.
     */
    private static String noContextMessage() {
        String submitSite = EmulatorContext.submitSiteOrNull();
        if (submitSite == null) {
            return "This thread carries no EmulatorContext, so a component update had no Vaadin "
                    + "session to reach a UI through. Background work started with a plain Thread "
                    + "or an unwrapped executor carries no session. Fix it where the work is "
                    + "started, not here: build the pool with EmulatorContext.wrap(executor), or "
                    + "capture EmulatorContext.get() on the UI thread and run the body inside "
                    + "ctx.run(...) / ctx.call(...). SwingWorker does this for you.";
        }
        return "This task came through an EmulatorContext-wrapped executor, but the thread that "
                + "SUBMITTED it carried no context either, so there was nothing to propagate and a "
                + "component update had no Vaadin session to reach a UI through. Wrapping the pool "
                + "is not enough on its own — the submit has to happen on a thread that has a "
                + "context (a UI thread, a SwingWorker, or another wrapped task). Submitted from:\n"
                + submitSite;
    }

    /** Test seam: re-arms {@link #checkUIThread}'s once-per-JVM latch. */
    static void resetUIThreadWarning() {
        offEdtWarned.set(false);
    }

    /** Test seam: whether {@link #checkUIThread} has reported. */
    static boolean uiThreadWarned() {
        return offEdtWarned.get();
    }

    /** How many lazy peers were built with no UI current; see {@link #onPeerBuiltOffUIThread}. */
    private static final java.util.concurrent.atomic.AtomicInteger PEERS_BUILT_OFF_UI_THREAD =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * A lazy emulator's peer is being built with no UI current: something reached the peer itself,
     * through a raw {@code getPeer()}, before any write could give it a UI — so the Vaadin
     * component is constructed off the UI thread after all, as an eager emulator's always is. It
     * still works; the WARN (once per JVM) names the emulator, and the stack names the reach that
     * should have been a {@code withPeer} write. Counted for the tests that assert it never happens.
     */
    public static void onPeerBuiltOffUIThread(Class<?> emulatorClass) {
        Throwable reach = new Throwable(emulatorClass.getName() + "'s peer reached here, on "
                + Thread.currentThread().getName());
        lastPeerBuiltOffUIThread = reach;
        if (PEERS_BUILT_OFF_UI_THREAD.getAndIncrement() == 0) {
            log.warn("{} built its Vaadin peer off the UI thread: its peer was reached directly before "
                    + "any write could give it a UI. Reported once per JVM; the stack is the reach.",
                    emulatorClass.getName(), reach);
        }
    }

    /** How many lazy peers {@link #onPeerBuiltOffUIThread} has seen built with no UI current. */
    public static int peersBuiltOffUIThread() {
        return PEERS_BUILT_OFF_UI_THREAD.get();
    }

    private static volatile Throwable lastPeerBuiltOffUIThread;

    /**
     * The stack of the most recent reach {@link #onPeerBuiltOffUIThread} saw, which the WARN logs
     * only for the first one in the JVM.
     *
     * @return {@code null} when no peer was ever built off the UI thread
     */
    public static Throwable lastPeerBuiltOffUIThread() {
        return lastPeerBuiltOffUIThread;
    }

    /**
     * Set while a queued peer write runs during a drain ({@code vaadinx.awt.Component.withPeer});
     * {@link #callSwing} asserts it is clear, since a queued write must be a pure sink.
     */
    private static final ThreadLocal<Boolean> IN_QUEUED_PEER_WRITE = new ThreadLocal<>();

    /** Whether this thread is running a queued peer write; see {@link #runQueuedPeerWrite}. */
    public static boolean inQueuedPeerWrite() {
        return IN_QUEUED_PEER_WRITE.get() != null;
    }

    /**
     * Runs a queued peer write as it is drained, marked so that {@link #callSwing} can assert the
     * write is a pure sink. Nested writes and Vaadin's own attach cascade run inside the mark;
     * {@link #outsideQueuedPeerWrite} lifts it for the attach and detach listeners, which are
     * peer→Swing work by design.
     */
    public static void runQueuedPeerWrite(Runnable write) {
        Boolean outer = IN_QUEUED_PEER_WRITE.get();
        IN_QUEUED_PEER_WRITE.set(Boolean.TRUE);
        try {
            write.run();
        } finally {
            IN_QUEUED_PEER_WRITE.set(outer);
        }
    }

    /** Runs {@code body} with the {@link #runQueuedPeerWrite} mark lifted. */
    public static void outsideQueuedPeerWrite(Runnable body) {
        Boolean outer = IN_QUEUED_PEER_WRITE.get();
        IN_QUEUED_PEER_WRITE.remove();
        try {
            body.run();
        } finally {
            IN_QUEUED_PEER_WRITE.set(outer);
        }
    }

    /**
     * The R_callswing_envelope seam: runs {@code runnable} — a peer listener's Swing-side body —
     * on a virtual thread so it can park on a modal dialog without blocking the
     * Vaadin request thread. Every peer→Swing listener wraps its body in this:
     *
     * <pre>{@code
     * button.addClickListener(e -> EHelper.callSwing(() -> {
     *     fireActionPerformed(...);   // may call JOptionPane.showMessageDialog and park
     * }));
     * }</pre>
     *
     * 100% coverage is the contract, not an optimization: one listener that
     * fires directly instead of through here is one button whose {@code
     * dialog.show()} deadlocks the carrier thread. See D_callswing_funnel, D_callswing_loom, hard rule R_callswing_envelope.
     *
     * @param runnable the Swing-side callback body (fire events, mutate fields).
     * @throws IllegalStateException if {@link UI#getCurrent()} is null and the
     *         session is not shutting down — every legitimate live call site has
     *         a UI by construction, so a missing one is a programming error that
     *         should fail loudly, not silently no-op. The one carve-out is app
     *         shutdown (see the shutdown note below). Also when the Vaadin request
     *         itself is being handled on a virtual thread, which SB-Emulators cannot
     *         run on at all — see {@link #assertRequestThreadIsNotVirtual}.
     *
     * <h4>Implementation details</h4>
     *
     * <p>Re-entrant: when already inside an outer {@code callSwing} continuation
     * — e.g. a Swing listener mutated a peer and fired another peer event — the
     * runnable runs inline; the outer VT can already park, and a nested VT would
     * only waste machinery. This backstops the feedback cases R_swing_is_truth's
     * {@code preventPeerEvents} flag can't cover (events with no prior value to
     * diff). "Inside a continuation" is {@link UIFibers#isInUIFiber()}, not a bare
     * {@code Thread.isVirtual()}: the two differ on a virtual request thread,
     * where the bare check would silently take this path.
     *
     * <p><b>Shutdown carve-out (R_callswing_envelope relaxation, D_shutdown_lifecycle).</b> During app termination
     * — a real browser-tab close, where {@code JFrame.dispatchShutdownClosing}
     * fires {@code WINDOW_CLOSING} and the peer tree detaches — the detach
     * listeners funnel {@code removeNotify} back through here on the request-less
     * tab-scope reaper thread, which has no current UI. That is expected teardown,
     * not a programming error: the browser tab is gone, so there is nothing to
     * push to. When {@link #isShuttingDown()}, the runnable therefore runs
     * <em>inline</em> instead of throwing (and instead of spinning the VT/{@code
     * UI.access} machinery, which would buy nothing with no browser attached).
     * Gated by our own {@code shuttingDown} flag, set before the detach cascade,
     * so it never masks a genuine missing-UI bug outside shutdown.
     *
     * <p>Exceptions propagate to the session {@code ErrorHandler} via
     * {@link UIFibers#runUntilPark} — swallowing here would pre-empt
     * user-configured error handling and hide bugs.
     */
    public static void callSwing(Runnable runnable) {
        assert !inQueuedPeerWrite() : "A queued peer write reached EHelper.callSwing while being drained: "
                + "a queued write must be a pure sink, whose effect Swing cannot observe until attach "
                + "(ideas/vaadin-ui-thread-only.md § \"The mechanism\")";
        final UI ui = UI.getCurrent();
        if (ui == null) {
            // Shutdown carve-out to R_callswing_envelope: on the request-less reaper thread that
            // fires WINDOW_CLOSING → detaches the peer tree, the detach listeners
            // call back here with no current UI. The tab is gone (nothing to push
            // to), so run the teardown callback inline rather than throw. Flag is
            // set before the detach (JFrame.dispatchShutdownClosing), and reads
            // VaadinSession.getCurrent() which is set on that thread. See D_shutdown_lifecycle.
            if (isShuttingDown()) {
                runnable.run();
                return;
            }
            throw new IllegalStateException(
                    "EHelper.callSwing called with no current UI. callSwing is the peer-to-Swing envelope and runs " +
                    "on a Vaadin UI thread; reaching it without one means a peer event fired on a thread that has " +
                    "no UI — typically the echo of a Swing-side write that should have been suppressed by " +
                    "preventPeerEvents before entering callSwing, not something the calling code fixes at the call site.");
        }
        if (UIFibers.isInUIFiber()) {
            // Inline, not UIFibers.runUntilPark's own inline branch: that one reports an
            // exception and carries on, where a nested Swing listener's exception must unwind
            // the outer dispatch.
            runnable.run();
            return;
        }
        if (Thread.currentThread().isVirtual()) {
            // A virtual thread outside any UI fiber is either a Vaadin request thread (refused
            // below — the deployment is unsupported) or the migrator's own background thread
            // already inside runInUIThread's accessSynchronously, where inline is right: it holds
            // the lock, the peer write has happened, and a UI fiber would buy nothing.
            assertRequestThreadIsNotVirtual();
            runnable.run();
            return;
        }
        // Held for the whole envelope rather than re-read: a nested envelope inside the
        // drain below can end the app (D_auto_shutdown closes the session), and Vaadin
        // unlinks a closed session from its UI — a re-read would hand the epilogue a null.
        final VaadinSession session = ui.getSession();
        // The first segment runs on the invoking UI, up to its first park or end. A park
        // resumes on whatever UI its anchor was last attached to, which is how a modal
        // follows an F5 teleport.
        UIFibers.runUntilPark(() -> {
            try {
                runnable.run();
            } catch (BrowserSessionClosedError e) {
                // A wait nobody will answer (awaitModal): it unwound the listener past every
                // catch (Exception), and at the fiber's root there is nobody left to report it to.
                if (e.getCause() instanceof WaitDiedException died) {
                    throw died;  // UIFibers ends the fiber quietly
                }
                throw e;
            }
        });
        // A UI fiber the runnable woke — the modal whose OK it clicked — resumes as an access
        // task. Drain the queue so its observable side effects (SJButton enable flips,
        // emulator field shadows, fired Swing event handlers) have settled by the time
        // callSwing returns, as a Swing modal's caller runs before the next event. Otherwise
        // the next callSwing or the surrounding emulator code reads stale state — and Karibu
        // tests fail mid-cascade because _setValue / _click don't auto-flush the queue (only
        // _get / _find do, via clientRoundtrip).
        session.getService().runPendingAccessTasks(session);
        // Epilogue: repair any direct writes to JDK-shaped protected fields the drained
        // user code made (D_instance_field_surface). Outermost envelope only — the VT-inline
        // re-entrant path above skips it, so a cascade reconciles once, after it settles.
        FieldReconciler.reconcileAll(session);
        // Then AWT's auto-shutdown idle check (D_auto_shutdown) — last, because it may
        // close the session.
        AutoShutdown.checkAppOver(session);
    }

    /**
     * Refuses a deployment that handles Vaadin requests on virtual threads
     * (R_match_swing_errors case (10), D_virtual_request_threads).
     *
     * <p>Called only from {@link #callSwing}, and only for a virtual thread that is not one of
     * ours. The discriminator is a current {@link VaadinRequest}: inside request handling, a
     * virtual thread can only be the container's, which is the unsupported configuration; outside
     * it, the thread is the migrator's own background thread and inline dispatch is correct.
     *
     * <p>Why it cannot degrade: the loom runner supports platform request threads only — on a
     * virtual one it hands each UI-fiber segment to a platform thread while the request thread
     * waits, which nobody has verified in a real container. Letting the inline path absorb it
     * instead is the worst outcome of all: the envelope would skip the UI fiber, the
     * access-queue drain and the epilogue, leaving state unsettled and a modal dialog with
     * nothing to park on, with no WARN and no stack trace — a silent mis-route rather than a
     * deadlock.
     */
    private static void assertRequestThreadIsNotVirtual() {
        if (VaadinRequest.getCurrent() == null) {
            return;
        }
        throw new IllegalStateException(
                "This Vaadin request is being handled on a virtual thread, which SB-Emulators "
                + "cannot run on: the blocking-dialog machinery mounts its own virtual threads on "
                + "a platform carrier, so on a virtual request thread no modal dialog can park and "
                + "no peer→Swing callback settles. Fix it in the deployment, not at this call "
                + "site: on Spring Boot set spring.threads.virtual.enabled=false (its default — a "
                + "starter or a parent pom flipped it), on Vaadin Boot leave "
                + "useVirtualThreadsIfAvailable(false).");
    }

    /**
     * Relays a surrogate's Swing-model event to the emulator's own listeners: through
     * {@link #callSwing} when a UI is current, inline on the calling thread otherwise.
     *
     * <pre>{@code
     * // JList, re-firing the surrogate's forwarded selection event:
     * EHelper.relayModelEvent(() -> fireSelectionValueChanged(first, last, adjusting));
     * }</pre>
     *
     * <p>Only for bridges a Swing model mutated off the UI thread can reach
     * (SD_background_model_hop); peer listeners keep {@code callSwing} and its throw. A
     * browser event always has a UI current, so no UI means an off-thread mutation on a
     * component with no UI to hop to — and the JDK fires those on the mutating thread too.
     */
    public static void relayModelEvent(Runnable runnable) {
        if (UI.getCurrent() == null) {
            runnable.run();
        } else {
            callSwing(runnable);
        }
    }

    /** Session-attribute key for the {@linkplain #markShuttingDown shutdown} flag. */
    private static final String SHUTTING_DOWN_KEY = "vaadinx.EHelper.shuttingDown";

    /**
     * Marks the session as shutting down — set once at the top of the
     * session-destroy → {@code WINDOW_CLOSING} dispatch (D_shutdown_lifecycle), before any
     * user {@code WindowListener} runs.
     *
     * <p>Its one job is to turn the browser-round-trip seams into an actionable
     * throw: once set, a modal-dialog park ({@link vaadinx.awt.Dialog}) or a
     * blocking {@link vaadinx.swing.SwingWorker#get()} / {@code
     * SwingUtilities.invokeAndWait} inside a shutdown listener throws a
     * "shutting down" {@link IllegalStateException} instead of hanging on a
     * browser that's already gone (or surfacing a cryptic generic error). Pure
     * server-side cleanup (save, release, log) is unaffected. Not for dedup:
     * session-destroy fires exactly once.
     */
    public static void markShuttingDown(VaadinSession session) {
        if (session != null) session.setAttribute(SHUTTING_DOWN_KEY, Boolean.TRUE);
    }

    /** Whether {@code session} is in the {@linkplain #markShuttingDown shutdown} dispatch; {@code false} for a null session. */
    public static boolean isShuttingDown(VaadinSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(SHUTTING_DOWN_KEY));
    }

    /** {@link #isShuttingDown(VaadinSession)} for the {@linkplain VaadinSession#getCurrent() current} session — the form the throw-seams use, since they run inside the dispatch where the current session is set. */
    public static boolean isShuttingDown() {
        return isShuttingDown(VaadinSession.getCurrent());
    }

    /**
     * The modal-park seam: parks the calling UI fiber until {@code closed} completes, the
     * session lock released so the click that closes the dialog can get in.
     *
     * <p>The wait belongs to {@code anchor}, the dialog's peer: when it stays detached — the
     * session is destroyed, the tab closes — the wait ends rather than parking forever. On resume
     * {@code UI.getCurrent()} is the UI the anchor was last attached to, which is the live one
     * after an F5 {@code @PreserveOnRefresh} teleport.
     *
     * @throws IllegalStateException outside a UI fiber.
     * @throws BrowserSessionClosedError if the anchor died, or the park was interrupted: nobody
     *         will answer, and an {@code Error} is what the migrated code's {@code catch (Exception e)}
     *         can't swallow into a fabricated answer (R_match_swing_errors case (8)).
     *         {@link #callSwing} ends the UI fiber quietly on it.
     */
    public static void awaitModal(com.vaadin.flow.component.Component anchor, CompletableFuture<?> closed) {
        try {
            UIFibers.parkAndAwait(anchor, closed);
        } catch (WaitDiedException e) {
            throw nobodyWillAnswer("a modal dialog", e);
        }
    }

    /**
     * The {@link BrowserSessionClosedError} for a UI-fiber wait that died; its cause is what
     * {@link #callSwing} recognises to end the fiber quietly.
     */
    private static BrowserSessionClosedError nobodyWillAnswer(String what, WaitDiedException cause) {
        return new BrowserSessionClosedError("This UI fiber was waiting for an answer from " + what
                + ", and nobody will answer any more: the tab closed, or the session is going away.", cause);
    }

    /**
     * Waits for a browser round-trip — an {@code executeJs} result, an {@code
     * ExtendedClientDetails} refresh — with {@link java.util.concurrent.Future#get()}'s contract.
     * Inside a UI fiber it parks, the session lock released so the response can get in, and the
     * wait dies with {@code anchor}; on any other thread it is a plain {@code future.get()}.
     *
     * <p>{@code future} must be a plain {@link CompletableFuture}, never Vaadin's
     * {@code DeadlockDetectingCompletableFuture}, whose {@code get()} refuses any caller that
     * {@code hasLock()} — a UI fiber included (D_clipboard_future_bridge).
     *
     * @throws BrowserSessionClosedError if {@code anchor} died, or the park was interrupted, as
     *         for {@link #awaitModal}.
     */
    public static <T> T awaitBrowserRoundTrip(com.vaadin.flow.component.Component anchor, CompletableFuture<T> future)
            throws java.util.concurrent.ExecutionException, InterruptedException {
        if (!UIFibers.isInUIFiber()) {
            return future.get();
        }
        try {
            return UIFibers.parkAndAwait(anchor, future);
        } catch (WaitDiedException e) {
            throw nobodyWillAnswer("the browser", e);
        } catch (java.util.concurrent.CancellationException e) {
            throw e;
        } catch (Throwable cause) {
            // parkAndAwait rethrows the future's failure as-is; restore Future.get()'s wrapping
            throw new java.util.concurrent.ExecutionException(cause);
        }
    }

    /**
     * The one UI an app lives in — the delivery target for an emulator
     * {@link vaadinx.swing.Timer} fire, a {@link vaadinx.swing.SwingWorker}
     * callback, and a parked {@link #callSwing} continuation resuming after an F5.
     * Resolves purely through the {@link AppTab} identity {@code MainWindowRoute}
     * maintains across an F5 {@code @PreserveOnRefresh} teleport (the rebind
     * re-binds the fresh UI to the app tab). Returns {@code null} when no live
     * {@code AppTab} is recorded.
     *
     * <p><b>Pure, never guesses.</b> There is deliberately no {@code getUIs()}
     * fallback: delivering to some arbitrary live UI could run app logic against
     * the wrong (or a second-tab curtain) UI and produce unreproducible state.
     * A null return is the fail-loud signal — {@link #onSessionLiveUI} drops the
     * callback, {@link #callOnLiveUISync} throws. In production an {@code AppTab}
     * is always set before any user {@code callSwing} ({@code MainWindowRoute}
     * marks it before {@code bootstrap()}), so null means a genuine teardown, not
     * a missing identity. A plain (non-parking) {@code callSwing} never needs
     * this: it delivers to its <em>invoking</em> UI (see {@link #callSwing} and
     * {@code UIFibers#runUntilPark}), which is why callSwing
     * is inherently UI-local and not part of the orphan bug.
     *
     * <p><b>Never throws</b> here either — a genuine second browser tab is a D_active_ui_pointer
     * curtain that never becomes the {@code AppTab}, so the one-app-per-session
     * invariant is enforced at attach, not by guessing among live UIs.
     *
     * <p>Must be called while holding the session lock ({@link AppTab#liveUI()}
     * reads {@link VaadinSession#getUIs()}, which enforces it) — typically from
     * inside {@link #onSessionLiveUI}'s
     * {@code session.access} block, which is also what guarantees an in-flight F5
     * has settled (the {@code AppTab} already re-bound to the fresh UI) before
     * this reads it (D_session_scoped_pools).
     *
     * @return the app's delivery UI, or {@code null} if no live {@link AppTab} is
     *         recorded (teardown, or an app that never marked one).
     */
    public static UI singleLiveUI(VaadinSession session) {
        AppTab app = AppTab.forSession(session);
        return app != null ? app.liveUI() : null;
    }

    /**
     * Delivers a Swing-side callback (a {@link vaadinx.swing.Timer} fire, a
     * {@link vaadinx.swing.SwingWorker} {@code done()}/{@code process()}/PCE)
     * onto the session's single live UI, from a non-UI thread (a scheduler or
     * worker thread that holds no lock).
     *
     * <p>The UI is resolved <b>lazily, at delivery time</b> — never captured
     * eagerly at construction. That is what survives an F5
     * {@code @PreserveOnRefresh} teleport: the refresh runs inside one
     * lock-holding request, so by the time this callback's {@code
     * session.access} block gets the lock the transition has fully settled and
     * {@link #singleLiveUI} sees exactly the new UI — independent of whether
     * the framework created-then-closed (real Flow) or closed-then-created
     * (Karibu). See {@code UiScopedFeaturesF5Test}.
     *
     * <p>{@code session.access} acquires the lock; inside it we resolve the
     * live UI and hop to it via {@link UI#accessSynchronously} (re-entrant on
     * the already-held lock) so {@link #callSwing} runs with {@code
     * UI.getCurrent()} set — the R_callswing_envelope envelope the loom VT carrier needs. A
     * {@code null} live UI (session going down) drops the callback silently.
     */
    public static void onSessionLiveUI(VaadinSession session, Runnable swingBody) {
        try {
            session.access(() -> {
                UI live = singleLiveUI(session);
                if (live != null) {
                    live.accessSynchronously(() -> callSwing(swingBody));
                }
            });
        } catch (RuntimeException e) {
            // Session invalid / going away before the task could be queued — drop.
            if (log.isDebugEnabled()) log.debug("onSessionLiveUI dropped a callback: {}", e.toString());
        }
    }

    /**
     * Hands an uncaught throwable to the session's {@link com.vaadin.flow.server.ErrorHandler},
     * acquiring the session lock first if the caller doesn't already hold it —
     * the emulator's {@code EventDispatchThread.processException}.
     *
     * <p>The lock is what this method is for: {@code getErrorHandler()} is state
     * access behind {@code checkHasLock()}, whose default
     * {@code SessionLockCheckStrategy} is {@code ASSERT} — so an unlocked read
     * blows up under {@code -ea} and passes silently in production.
     *
     * <p>Deliberately no lookup chain, and in particular no
     * {@link Thread#getDefaultUncaughtExceptionHandler()} read — a migrated app's
     * JVM-wide handler is ported to the session instead. Adding the read back is
     * the plausible "improvement" this note exists to stop;
     * {@code UncaughtErrorRoutingTest.jvmWideHandlerIsNotConsulted} is what goes
     * red. Reasons: {@code D_uncaught_handler_chain}.
     *
     * @param session {@code null} logs here instead; no handler is reachable without one
     */
    public static void reportUncaught(VaadinSession session, Throwable t) {
        Objects.requireNonNull(t, "t");
        if (session == null) {
            log.error("Uncaught exception with no VaadinSession to route it to", t);
            return;
        }
        if (session.hasLock()) {
            session.getErrorHandler().error(new ErrorEvent(t));
            return;
        }
        try {
            session.access(() -> session.getErrorHandler().error(new ErrorEvent(t)));
        } catch (RuntimeException e) {
            // Session invalid / going away before the report could be queued. Log
            // here rather than drop: unlike onSessionLiveUI's callbacks, this IS
            // the error report, so losing it silently is the disease.
            log.error("Uncaught exception could not be routed to the session ErrorHandler ({})",
                    e.toString(), t);
        }
    }

    /** Default ceiling for a background→UI synchronous round-trip; a backstop against a never-draining access queue. */
    private static final long LIVE_UI_SYNC_TIMEOUT_SECONDS = 30;

    /**
     * Runs {@code body} on the session's single live UI and <b>returns its
     * value to the calling background thread</b>, blocking until it completes.
     * The synchronous, result-returning sibling of {@link #onSessionLiveUI}:
     * used when a background worker ({@code SwingWorker.doInBackground}) must
     * read/write browser-backed state (e.g. {@code localStorage} preferences)
     * and needs the answer inline.
     *
     * <pre>{@code
     * // on a SwingWorker VT (no current UI, session held via EmulatorContext):
     * String v = EHelper.callOnLiveUISync(ctx.session(),
     *         () -> Preferences.userRoot().node("com/acme").get("k", "def"));
     * }</pre>
     *
     * Mechanism (mirrors {@code SwingUtilities.invokeAndWait}'s block-until-done,
     * plus {@link #onSessionLiveUI}'s UI resolution): queue onto the session via
     * {@code session.access}; inside, resolve {@link #singleLiveUI} and run
     * {@code body} under {@code accessSynchronously} + {@link #callSwing} so it
     * has a current UI, the lock, and the loom carrier to park on a browser
     * round-trip; latch the result/exception back and {@code await} it on the
     * caller. The access queue is drained by {@code @Push} in production (the
     * mandatory-Push contract) / {@code MockVaadin.runUIQueue} in tests — the
     * same drain {@code invokeAndWait} depends on. The caller must be a parkable
     * (virtual) thread; a {@code SwingWorker} worker thread is.
     *
     * <p><b>Never hangs, fails loud.</b> Unlike {@code onSessionLiveUI} (which
     * silently drops), every terminal state completes the latch: no live UI
     * (tab/session gone), a {@code body} exception, or the
     * {@value #LIVE_UI_SYNC_TIMEOUT_SECONDS}s timeout backstop all surface as a
     * thrown exception (per D_prefs_scope_split / D_gap_severity_triage — loud over silently-stale). A thrown
     * exception propagates out of {@code doInBackground} as the worker's
     * {@code ExecutionException}.
     *
     * @throws IllegalStateException if no single live UI is reachable, the wait
     *         times out, or the wait is interrupted; any {@code body} runtime
     *         exception propagates as-is.
     */
    public static <T> T callOnLiveUISync(VaadinSession session, Supplier<T> body) {
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<T> result = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            session.access(() -> {
                try {
                    UI live = singleLiveUI(session);
                    if (live == null) {
                        error.set(new IllegalStateException(
                                "No live UI to serve a background preferences access — the session or "
                                + "browser tab may have closed. Read preferences from the UI thread "
                                + "(or under EHelper.callSwing) instead. See D_prefs_scope_split."));
                    } else {
                        // Catch INSIDE the callSwing body: callSwing routes a
                        // thrown body to the session ErrorHandler (R_callswing_envelope) rather
                        // than rethrowing, so capturing here is what lets a body
                        // failure propagate back to the worker instead of being
                        // silently swallowed.
                        live.accessSynchronously(() -> callSwing(() -> {
                            try {
                                result.set(body.get());
                            } catch (Throwable t) {
                                error.set(t);
                            }
                        }));
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    done.countDown();
                }
            });
        } catch (RuntimeException e) {
            // Session invalid / going away before the task could even queue.
            error.set(new IllegalStateException(
                    "Session unavailable while serving a background preferences access. See D_prefs_scope_split.", e));
            done.countDown();
        }
        try {
            if (!done.await(LIVE_UI_SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out after " + LIVE_UI_SYNC_TIMEOUT_SECONDS + "s waiting for the live UI to "
                        + "serve a background preferences access — is @Push enabled? See D_prefs_scope_split.");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for a background preferences access.", ie);
        }
        Throwable t = error.get();
        if (t instanceof RuntimeException re) throw re;
        if (t instanceof Error err) throw err;
        if (t != null) throw new IllegalStateException(t);
        return result.get();
    }

    /**
     * Deliberate no-op hook for Swing API that's redundant in the Vaadin world
     * (layout invalidation, repaint, peer lifecycle). Logs at DEBUG only — these
     * get hit hot. No varargs on purpose: args don't add information at DEBUG for
     * a "we ignored it by design" call.
     */
    public static void onNoop(String className, String method) {
        if (log.isDebugEnabled()) log.debug("no-op {}.{}()", className, method);
    }

    /**
     * Swing method we *have* implemented, but the underlying Vaadin peer can't
     * honour this particular call — e.g. {@code setEnabled(false)} on a peer
     * that doesn't implement {@link com.vaadin.flow.component.HasEnabled}.
     * Distinct from {@link #onUnimplemented}: there, *we* haven't coded the
     * behaviour yet; here, the peer is the blocker. WARN so it shows up in
     * migration logs, but never throws — R_match_swing_errors.
     */
    public static void onUnsupported(String className, String method, Object... args) {
        String msg = "Peer cannot support " + className + "." + method + "(" + format(args) + ")";
        log.warn(msg);
        warnHook.accept(msg);
    }

    /**
     * ERROR-log hook for situations where emulation is fundamentally unable
     * to honor a well-formed Swing request and the safest response is to do
     * nothing — e.g. setting a {@link vaadinx.awt.LayoutManager} on a
     * Container whose peer content element isn't a plain {@code <div>}
     * (D_layout_css_on_content). Louder than {@link #onUnimplemented}'s WARN because the user
     * has stepped into genuinely broken territory, not just unimplemented
     * territory. Returns without throwing; R_match_swing_errors still applies.
     */
    public static void onUnsupportedPeerShape(String className, String situation) {
        String msg = "Unsupported peer shape in " + className + ": " + situation;
        log.error(msg);
        warnHook.accept(msg);
    }

    /**
     * Apply a block of layout CSS to a Container's peer content element per
     * D_layout_css_on_content. Gates on the element being a plain {@code <div>}: non-Div peers
     * get a {@link #onUnsupportedPeerShape} ERROR log and no CSS is written — setting
     * a LayoutManager on a JComponent whose peer isn't a Div (a JButton, a
     * JTextField, ...) leaves children under the peer's own rendering rather
     * than corrupting it.
     *
     * <p>Takes the target {@link com.vaadin.flow.dom.Element} rather than
     * the Container so {@link vaadinx.awt.Container#peerContentElement()}
     * stays protected — callers are the built-in LayoutManagers in
     * {@code vaadinx.awt}, which already have package access to resolve it.
     *
     * <p>{@code null} values remove the corresponding style property, mirroring
     * the {@code null → inherit from parent} contract used elsewhere in the
     * peer-CSS layer (see {@link vaadinx.awt.Component#setForeground}).
     */
    public static void applyContainerCss(com.vaadin.flow.dom.Element target,
                                         String layoutManagerName,
                                         java.util.Map<String, String> css) {
        // Body lifted to com.vaadin.swingbridge.surrogates.SHelper so
        // pure-surrogate containers honor setLayout(BorderLayout) directly.
        // Direction matches SD_shelper_statics (:emulators → :surrogates).
        com.vaadin.swingbridge.surrogates.util.LayoutCss.applyContainerCss(target, layoutManagerName, css);
    }

    /**
     * Apply per-child layout CSS to a single child's element — the counterpart to
     * {@link #applyContainerCss} for the per-child leg of a
     * {@link vaadinx.awt.CssEmittingLayoutManager} pass ({@code grid-area},
     * {@code justify-self}, {@code margin}, …). No {@code <div>} gate: a child is any
     * element (a button, a field). {@code null} values remove the property, absent keys
     * are left untouched — so a fill-stretched cell can drop a field surrogate's inline
     * {@code width} without a non-filled sibling losing its columns size.
     */
    public static void applyChildCss(com.vaadin.flow.dom.Element target,
                                     java.util.Map<String, String> css) {
        com.vaadin.swingbridge.surrogates.util.LayoutCss.applyChildCss(target, css);
    }

    /**
     * Clear a container's layout CSS — the undo for {@link #applyContainerCss},
     * called from {@link vaadinx.awt.Container#setLayout} when one
     * {@code LayoutManager} replaces another. Rationale for why the undo exists
     * (and why per-child CSS is left alone) lives on
     * {@link com.vaadin.swingbridge.surrogates.util.LayoutCss#resetContainerCss}.
     */
    public static void resetContainerCss(com.vaadin.flow.dom.Element target) {
        com.vaadin.swingbridge.surrogates.util.LayoutCss.resetContainerCss(target);
    }

    /**
     * Register the 1:1 mapping {@code peer → emulator}. Invoked from
     * {@link vaadinx.awt.Component}'s constructor so lookup via
     * {@link #getEmulator(com.vaadin.flow.component.Component)} never has to
     * synthesize a fresh emulator.
     */
    public static void onCreated(com.vaadin.flow.component.Component peer, vaadinx.awt.Component emulator) {
        ComponentUtil.setData(peer, vaadinx.awt.Component.class, emulator);
    }

    /**
     * Per-UI map of {@code Swing-name → next-suffix} used by
     * {@link #applyEmulatorId} to disambiguate duplicate Swing names into
     * unique HTML ids ({@code "ok"}, {@code "ok-2"}, {@code "ok-3"}, …).
     * Plain {@link java.util.HashMap} of plain {@link Integer}s — Vaadin's
     * session lock already serializes access, so the reflexive
     * {@link java.util.concurrent.atomic.AtomicInteger} would falsely
     * advertise concurrency safety the codebase doesn't need (and break
     * session replication, since AtomicInteger is non-Serializable).
     * Counter only grows; old slots are not reclaimed when a name is freed
     * (cheap and fine for debug-tier ids).
     */
    private static final class IdCounter implements java.io.Serializable {
        final java.util.Map<String, Integer> seq = new java.util.HashMap<>();
    }

    private static IdCounter idCounterFor(UI ui) {
        IdCounter c = ComponentUtil.getData(ui, IdCounter.class);
        if (c == null) {
            c = new IdCounter();
            ComponentUtil.setData(ui, IdCounter.class, c);
        }
        return c;
    }

    /**
     * Map a Swing name to the peer's HTML id, with a per-UI suffix counter
     * to keep ids unique when two components share a Swing name (Swing's
     * {@code name} has no uniqueness requirement; HTML's {@code id} does).
     * First {@code setName("ok")} → {@code id="ok"}; second → {@code id="ok-2"};
     * third → {@code id="ok-3"}. {@code null} / blank name removes the id.
     *
     * <p>Surrogate-side tagging takes a different shape ({@code data-swing-name}
     * attribute through {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin#setName})
     * because surrogates deliberately leave Vaadin's {@code id} alone — see
     * {@code SD_dropin_stance} / {@code NameStore}. The id-vs-attribute split mirrors the
     * stage-2 vs stage-3 layering: emulators own their peers and can stamp
     * the id; surrogates respect the id semantic boundary.
     *
     * <p>Falls back to {@code peer.setId(name)} without disambiguation when
     * called outside a UI context — pre-attach construction without a current
     * UI is rare but possible, and an unsuffixed id is the same as the first-
     * claim outcome.
     */
    public static void applyEmulatorId(com.vaadin.flow.component.Component peer, String name) {
        if (name == null || name.isBlank()) {
            // Vaadin's setId rejects null; "" is the empty-id sentinel that
            // removes the underlying id attribute.
            peer.setId("");
            return;
        }
        UI ui = UI.getCurrent();
        if (ui == null) {
            peer.setId(name);
            return;
        }
        java.util.Map<String, Integer> counter = idCounterFor(ui).seq;
        int n = counter.merge(name, 1, Integer::sum);
        peer.setId(n == 1 ? name : name + "-" + n);
    }

    /**
     * Convert a {@link vaadinx.swing.Icon} to a Vaadin
     * {@link com.vaadin.flow.component.Component} the peer can install via
     * {@code Button.setIcon(Component)}. Two icon-rendering paths funnel
     * through here:
     * <ul>
     *   <li>Path 1 — {@link vaadinx.swing.ImageIcon}: raster encoded as
     *       PNG and wrapped in a Vaadin {@link com.vaadin.flow.component.html.Image}
     *       via {@link com.vaadin.swingbridge.surrogates.util.Icons#imageIconToVaadinImage}.</li>
     *   <li>Path 2 (external) — {@link vaadinx.swing.VaadinIconAdapter}:
     *       fresh {@link com.vaadin.flow.component.icon.VaadinIcon}-glyph
     *       component built per call. Reaches us when migrated code
     *       sourced an icon via {@link vaadinx.swing.UIManager#getIcon}.</li>
     * </ul>
     * Other {@code Icon} impls log via {@link #onUnimplemented} and
     * return {@code null} (Path 3 — offscreen-paint of arbitrary Icon
     * impls — is permanently deferred per R_match_swing_errors sub-bucket (b)).
     *
     * <p>{@code null} icon → {@code null} (callers interpret as "remove
     * icon"). The {@code className} argument feeds the WARN message for
     * non-recognized {@code Icon} inputs so migration logs identify which
     * emulator surfaced the dropped icon.
     */
    public static com.vaadin.flow.component.Component toVaadinIconComponent(String className, vaadinx.swing.Icon icon) {
        if (icon == null) return null;
        if (icon instanceof vaadinx.swing.ImageIcon ours) {
            return com.vaadin.swingbridge.surrogates.util.Icons.imageIconToVaadinImage(ours.asJdk());
        }
        if (icon instanceof vaadinx.swing.VaadinIconAdapter glyph) {
            return glyph.createComponent();
        }
        onUnimplemented(className, "setIcon", icon);
        return null;
    }

    /**
     * The emulator ({@link vaadinx.awt.Component}) whose peer is the given
     * Vaadin component. Throws {@link NullPointerException} if the peer has
     * no registered emulator — an internal invariant check (see D_peer_emulator_mapping). D_never_fail_on_gaps
     * scopes to emulation incompleteness; this is an internal-plumbing
     * invariant and unrelated.
     */
    public static vaadinx.awt.Component getEmulator(com.vaadin.flow.component.Component peer) {
        return Objects.requireNonNull(ComponentUtil.getData(peer, vaadinx.awt.Component.class), peer + " has no emulator associated");
    }

    /**
     * Lenient, walking variant of {@link #getEmulator}: the emulator owning
     * {@code peer}, or the one owning its nearest Vaadin ancestor, or
     * {@code null} when no ancestor is a registered peer. Never throws.
     *
     * <p>Needed wherever the starting point is a Vaadin component we did not
     * choose — {@code com.vaadin.swingbridge.surrogates.FocusTracker}'s focus owner is the
     * canonical case: the browser focuses the inner field of a composite peer
     * (an {@code SJSpinner}'s {@code NumberField}), which is not itself a
     * registered peer, so the answer sits one or two levels up. Focus landing
     * on a Vaadin component the app added outside vaadinx has no emulator at
     * all, and {@code null} is the honest answer rather than
     * {@link #getEmulator}'s invariant failure.
     */
    public static vaadinx.awt.Component emulatorFor(com.vaadin.flow.component.Component peer) {
        for (com.vaadin.flow.component.Component p = peer; p != null; p = p.getParent().orElse(null)) {
            vaadinx.awt.Component emulator = ComponentUtil.getData(p, vaadinx.awt.Component.class);
            if (emulator != null) return emulator;
        }
        return null;
    }

    /**
     * Every {@link com.vaadin.flow.component.dialog.Dialog} attached to the current
     * UI's component graph — the Vaadin-side view of our Windows. A Dialog
     * that hasn't been opened (not in the tree) isn't returned. No retained
     * list: the graph is the source of truth, which avoids the classic
     * registry leak where disposed Windows linger in a static list.
     *
     * <p>Returns an empty list when there's no current UI — the typical
     * case for code running off-thread. Callers get "no windows" rather
     * than an exception, matching D_never_fail_on_gaps's never-fail stance for queries.
     *
     * <p>We walk {@link com.vaadin.flow.component.Component#getChildren()}
     * recursively rather than calling {@link ComponentUtil#findComponents},
     * because the latter short-circuits on the first component it finds
     * below a given element — starting from UI it would accept UI and stop
     * without ever reaching the Dialog children.
     */
    public static java.util.List<com.vaadin.flow.component.dialog.Dialog> getDialogs() {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (ui == null) return java.util.Collections.emptyList();
        java.util.List<com.vaadin.flow.component.dialog.Dialog> out = new java.util.ArrayList<>();
        collectDialogs(ui, out);
        return out;
    }

    private static void collectDialogs(com.vaadin.flow.component.Component c,
                                       java.util.List<com.vaadin.flow.component.dialog.Dialog> out) {
        if (c instanceof com.vaadin.flow.component.dialog.Dialog d) {
            out.add(d);
        }
        c.getChildren().forEach(child -> collectDialogs(child, out));
    }

    /**
     * Every {@link vaadinx.awt.Window} this app instance has constructed and not
     * yet let go of, in construction order — the source of truth behind
     * {@link vaadinx.awt.Window#getWindows()},
     * {@link vaadinx.awt.Window#getOwnerlessWindows()} and
     * {@link vaadinx.awt.Frame#getFrames()}.
     *
     * <p><b>Creation-based, as AWT is.</b> A Window appears here from its
     * constructor and stays while anything references it — never shown, hidden
     * again, even {@code dispose()}d. Displayability only decides whether the
     * registry itself is what keeps it alive: while displayable it is pinned
     * strongly, so a frame the app has dropped every reference to is still
     * enumerable exactly as on the desktop. Details, and why the earlier
     * walk-the-UI-graph shape was dropped, live on {@link WindowRegistry}.
     *
     * <p>Returns an empty list when no app store resolves on this thread,
     * matching {@link #getDialogs()}'s never-fail stance for queries (D_never_fail_on_gaps).
     */
    public static java.util.List<vaadinx.awt.Window> getWindows() {
        final WindowRegistry registry = WindowRegistry.current();
        return registry == null ? java.util.Collections.emptyList() : registry.all();
    }

    /**
     * The {@code Window.init()} -> {@code addToWindowList()} edge, called from
     * {@link vaadinx.awt.Window}'s peer-injection constructor. Silently does
     * nothing off any app thread, per {@link WindowRegistry#current()}.
     */
    public static void onWindowCreated(vaadinx.awt.Window w) {
        final WindowRegistry registry = WindowRegistry.current();
        if (registry != null) registry.register(w);
    }

    /** The {@code addNotify()} -> {@code allWindows.add(this)} edge. */
    public static void onWindowDisplayable(vaadinx.awt.Window w) {
        final WindowRegistry registry = WindowRegistry.current();
        if (registry != null) registry.pin(w);
    }

    /**
     * The {@code removeNotify()} -> {@code allWindows.remove(this)} edge, and the
     * one place a Window stops being displayable — so also where
     * {@link AutoShutdown} learns the app may be over.
     */
    public static void onWindowUndisplayable(vaadinx.awt.Window w) {
        final WindowRegistry registry = WindowRegistry.current();
        if (registry != null) registry.unpin(w);
        AutoShutdown.armFromUndisplayable();
    }

    private static String format(Object[] args) {
        return Arrays.stream(args).map(EHelper::formatOne).collect(Collectors.joining(","));
    }

    private static String formatOne(Object o) {
        if (o == null) return "null";
        Package p = o.getClass().getPackage();
        if (p != null && "java.lang".equals(p.getName())) return o.toString();
        // Anonymous classes have an empty simple name, which makes a log
        // line read as e.g. "setAction()" with no payload. Walk up to the
        // nearest named ancestor. Stop at Object so an anonymous
        // interface impl (whose superclass is directly Object) falls
        // through to the implemented-interface check below — migrated
        // code's "new Border(){...}" should log as "Border", not "Object".
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            String name = c.getSimpleName();
            if (!name.isEmpty()) return name;
        }
        for (Class<?> i : o.getClass().getInterfaces()) {
            String name = i.getSimpleName();
            if (!name.isEmpty()) return name;
        }
        return o.getClass().getName();
    }
}
