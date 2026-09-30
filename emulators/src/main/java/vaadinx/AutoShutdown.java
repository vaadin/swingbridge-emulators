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
import com.vaadin.flow.server.VaadinSessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * INTERNAL — do not use. Not part of the emulated Swing API surface and has no
 * stability guarantee; the signature exists only to serve emulator internals and
 * may change without notice.
 *
 * <p>AWT's {@code AWTAutoShutdown} at session scope: the app ends — {@code
 * session.close()} — when its last displayable {@link vaadinx.awt.Window} is
 * disposed and nothing holds it open (D_auto_shutdown). Four seams, wired into
 * the call graph rather than called as an API:
 *
 * <pre>{@code
 * AutoShutdown.enableForApp(session);      // @MainWindow JFrame ctor: this session is an app
 * AutoShutdown.armFromUndisplayable();     // Window.dispose -> removeNotify: it may be over
 * AutoShutdown.holdOpen(session, timer);   // a started Timer: not over yet
 * AutoShutdown.checkAppOver(session);      // the callSwing epilogue: decide
 * }</pre>
 *
 * <p>The decision lands in that epilogue because it is AWT's idle-EDT analogue:
 * the envelope's access-queue drain has run, so a {@code dispose();
 * invokeLater(() -> new MainFrame())} handoff already has its new window up.
 *
 * <p>Every method must be called with the session lock held — each one reads or
 * writes a session attribute.
 */
public final class AutoShutdown {

    private static final Logger log = LoggerFactory.getLogger(AutoShutdown.class);

    private static final String STATE_KEY = AutoShutdown.class.getName();

    private AutoShutdown() {
    }

    /**
     * Per-app-instance auto-shutdown state, held as a session attribute.
     *
     * <p>Only {@link #enabled} serializes — an app that comes back is
     * still an app, while a deserialized one has no disposed window to answer
     * for. {@link #holds} must not: it is a strong reference to a live
     * {@link vaadinx.swing.Timer}, which session replication would drag along
     * with its listeners.
     */
    private static final class State implements Serializable {
        private boolean enabled;
        private transient boolean armed;
        private transient Set<Object> holds;

        Set<Object> holds() {
            if (holds == null) {
                holds = Collections.newSetFromMap(new IdentityHashMap<>());
            }
            return holds;
        }
    }

    /**
     * Declares this session a running app, which is what puts it in scope for
     * auto-shutdown at all — nothing arms before it.
     *
     * <p>A host that never started an app — a Sampler route, a test popping
     * a bare {@code JDialog} — has no program lifetime to end, so the
     * {@code @MainWindow} frame is the anchor and every other host stays out
     * (D_auto_shutdown). Idempotent, F5 included.
     */
    public static void enableForApp(VaadinSession session) {
        if (session == null) {
            return;
        }
        state(session).enabled = true;
    }

    /**
     * Records that a window just stopped being displayable, so the app
     * <em>may</em> be over.
     *
     * <p>No-op off any app thread, on a session that is not an app, and during
     * shutdown — the teardown detach cascade undisplayables every window of a
     * session that is already closing (D_shutdown_lifecycle).
     */
    static void armFromUndisplayable() {
        final VaadinSession session = VaadinSession.getCurrent();
        if (session == null || EHelper.isShuttingDown(session)) {
            return;
        }
        final State state = stateIfPresent(session);
        if (state == null || !state.enabled) {
            return;
        }
        state.armed = true;
    }

    /**
     * Registers {@code token} as something that keeps the app alive with no
     * window of its own — AWT's "busy" marker, whose one holder today is a
     * started {@link vaadinx.swing.Timer}.
     *
     * @param token retained <b>strongly</b> until {@link #releaseHold}, as the
     *     JDK's {@code TimerQueue} retains a started Timer — which is why one
     *     keeps the JVM alive there. Bounded by the session, not the process.
     */
    public static void holdOpen(VaadinSession session, Object token) {
        if (session == null) {
            return;
        }
        state(session).holds().add(token);
    }

    /** Drops {@code token}'s {@linkplain #holdOpen hold}; idempotent. */
    public static void releaseHold(VaadinSession session, Object token) {
        if (session == null) {
            return;
        }
        final State state = stateIfPresent(session);
        if (state != null) {
            state.holds().remove(token);
        }
    }

    /**
     * Decides whether the app is over, closing the session if it is.
     *
     * <p>Stays armed when a hold vetoes, so the holder's own callbacks
     * become the re-check ticks: a repeating Timer fires through {@code
     * callSwing}, and the fire that stops it closes the session on that same
     * epilogue. Finding a displayable window disarms instead — the app is
     * back, and the next dispose re-arms.
     */
    static void checkAppOver(VaadinSession session) {
        if (session == null || session.getState() != VaadinSessionState.OPEN
                || EHelper.isShuttingDown(session)) {
            return;     // already ending — a nested envelope got here first, or a tab close did
        }
        final State state = stateIfPresent(session);
        if (state == null || !state.enabled || !state.armed) {
            return;
        }
        final WindowRegistry registry = WindowRegistry.current();
        // An unresolvable registry answers "not over": a store we cannot read is no
        // evidence that the app has no windows (D_never_fail_on_gaps's stance for queries).
        if (registry == null || registry.anyDisplayable()) {
            state.armed = false;
            return;
        }
        if (!state.holds().isEmpty()) {
            return;
        }
        state.armed = false;
        log.info("App instance over — last displayable window disposed, nothing holding it open. "
                + "Closing the Vaadin session (D_auto_shutdown).");
        session.close();
    }

    private static State state(VaadinSession session) {
        State state = stateIfPresent(session);
        if (state == null) {
            state = new State();
            session.setAttribute(STATE_KEY, state);
        }
        return state;
    }

    /** Read-only lookup — what lets every read path bail without writing a session attribute. */
    private static State stateIfPresent(VaadinSession session) {
        return (State) session.getAttribute(STATE_KEY);
    }
}
