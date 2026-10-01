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

package vaadinx.awt;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;

/**
 * Gets one peer's writes onto the peer with the session locked and a UI current — now when a
 * UI is reachable, otherwise later, in the order they were made. The {@link Component#withPeer}
 * seam, as a component of its own; every emulator holds one for its peer.
 *
 * <p>A write runs:
 * <ul>
 *   <li>inline, when a UI is already current (the caller holds the lock);</li>
 *   <li>otherwise, when the peer has been attached and its owner UI is still open, through a
 *       synchronous hop onto that UI, under the session the peer attached to;</li>
 *   <li>otherwise — never attached, or its UI closed — it is <b>queued</b>, and drained under
 *       the lock by the next thing that has a UI: the peer's attach, {@link #drainIfUICurrent()}
 *       (which is how {@code parentPeer.add(child.getPeer())} drains the child before attaching
 *       it), or a later write.</li>
 * </ul>
 * Queued writes always drain before a newer one runs, so writes reach the peer in the order
 * they were made. A queued write must be a pure sink, observable by nothing on the Swing side
 * before attach; {@link vaadinx.EHelper#callSwing} asserts it. See
 * ideas/vaadin-ui-thread-only.md § "The mechanism".
 *
 * <p>Thread-safe. The queue is guarded by a private lock, held only for bookkeeping and never
 * while a write runs, and always taken after the session lock, never before it.
 */
final class PeerWriteQueue {

    private static final Logger log = LoggerFactory.getLogger(PeerWriteQueue.class);

    /** Queue length at which {@link #enqueue} WARNs, once per queue. */
    private static final int WARN_THRESHOLD = 1000;

    /**
     * {@code null} until {@link #bind}: a lazy emulator's queue exists before its peer, and only
     * ever queues until then, since nothing can attach a peer that does not exist.
     */
    private volatile com.vaadin.flow.component.Component peer;

    /** The emulator class the WARN names, which is more useful in a migrator's log than the peer's. */
    private final Class<?> owner;

    private final Object lock = new Object();

    /** Oldest first; {@code null} when empty. Guarded by {@link #lock}. */
    private ArrayDeque<Runnable> pending;

    /** The session of the peer's latest attach, {@code null} before the first. Guarded by {@link #lock}. */
    private VaadinSession attachedSession;

    /** Whether the {@link #WARN_THRESHOLD} WARN has fired. Guarded by {@link #lock}. */
    private boolean warned;

    /**
     * The thread draining, so a write nested in a drained one runs in place rather than
     * draining the newer writes ahead of itself. Only the lock-holding thread drains.
     */
    private Thread drainingThread;

    PeerWriteQueue(Class<?> owner) {
        this.owner = owner;
    }

    /**
     * Binds the queue to the peer once it exists, registering the attach listener that drains —
     * call it before the emulator registers its own attach listeners, so they see a peer that
     * already carries its queued writes. The caller drains whatever was queued before.
     */
    void bind(com.vaadin.flow.component.Component peer) {
        this.peer = peer;
        peer.addAttachListener(e -> {
            synchronized (lock) {
                attachedSession = e.getSession();
            }
            drain();
        });
    }

    /** Runs {@code write}, which touches the peer, as the class javadoc describes. */
    void write(Runnable write) {
        if (UI.getCurrent() != null) {
            drain();
            write.run();
            return;
        }
        VaadinSession session;
        synchronized (lock) {
            session = attachedSession;
            if (session == null) {
                enqueue(write);
                return;
            }
        }
        hopOrEnqueue(session, write);
    }

    /** Drains the queue if this thread has a UI current, which means it holds the lock. */
    void drainIfUICurrent() {
        if (UI.getCurrent() != null) drain();
    }

    /**
     * The hop for a peer that has been attached: onto its owner UI under {@code session}'s lock,
     * draining first — or into the queue, when that UI has closed or the session is gone.
     */
    private void hopOrEnqueue(VaadinSession session, Runnable write) {
        // Set where the write starts, so a throw from the write itself propagates and a
        // failure of the hop never runs it twice.
        boolean[] started = {false};
        try {
            session.accessSynchronously(() -> {
                UI ui = SHelper.ownerUI(peer);
                if (ui == null || ui.isClosing() || ui.getSession() != session) {
                    enqueue(write);
                    return;
                }
                try {
                    ui.accessSynchronously(() -> {
                        started[0] = true;
                        drain();
                        write.run();
                    });
                } catch (UIDetachedException e) {
                    if (started[0]) throw e;
                    enqueue(write);
                }
            });
        } catch (RuntimeException e) {
            if (started[0]) throw e;
            // The session went away while this waited for its lock. Nothing will attach the peer
            // to it again; the write waits with the rest, for an attach that may not come.
            enqueue(write);
        }
    }

    private void enqueue(Runnable write) {
        synchronized (lock) {
            if (pending == null) pending = new ArrayDeque<>();
            pending.add(write);
            if (pending.size() >= WARN_THRESHOLD && !warned) {
                warned = true;
                log.warn("{} has queued {} peer writes while it has no UI to render to. They drain "
                        + "when it is attached; until then each one is held in memory. The stack is "
                        + "the write that crossed the threshold.",
                        owner.getName(), WARN_THRESHOLD, new Throwable("enqueued here"));
            }
        }
    }

    /**
     * Runs every queued write, oldest first, on this thread, which holds the session lock with a
     * UI current. A no-op from inside a drained write.
     */
    private void drain() {
        Thread me = Thread.currentThread();
        if (drainingThread == me) return;
        for (;;) {
            Runnable next;
            synchronized (lock) {
                if (pending == null || pending.isEmpty()) {
                    pending = null;
                    return;
                }
                next = pending.poll();
            }
            drainingThread = me;
            try {
                vaadinx.EHelper.runQueuedPeerWrite(next);
            } finally {
                drainingThread = null;
            }
        }
    }
}
