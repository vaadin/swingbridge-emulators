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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.github.mvysny.blockingdialogs.UIFibers;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Vaadin session lock, as seen from inside {@link EHelper#callSwing} — i.e. from the virtual
 * thread every peer→Swing callback runs on, whose carrier holds the lock.
 *
 * <p>Without {@link VirtualThreadAwareLock} a virtual thread cannot take that lock at all:
 * {@code ReentrantLock} keys on {@link Thread} identity and the carrier is a different thread, so
 * the virtual thread would wait for a lock its own carrier holds while the carrier waits for it —
 * a deadlocked session (D_vt_aware_session_lock). The wiring under test is
 * {@link MockVirtualThreadAwareServlet}'s {@code getSessionLock()} routing — the test-side mirror of
 * what a migrated app's {@code AppServlet} does.
 */
public class SessionLockFromVirtualThreadTest extends AbstractKaribuTest {

    @Test
    public void theSessionLockIsTheVirtualThreadAwareWrapper() {
        Assertions.assertInstanceOf(VirtualThreadAwareLock.class,
                VaadinSession.getCurrent().getLockInstance());
    }

    @Test
    public void hasLockAnswersTrueInsideCallSwing() {
        final AtomicBoolean hasLock = new AtomicBoolean();
        EHelper.callSwing(() -> hasLock.set(VaadinSession.getCurrent().hasLock()));
        Assertions.assertTrue(hasLock.get());
    }

    /** The issue-#3 shape: this used to recurse until {@link StackOverflowError}. */
    @Test
    public void lockAndUnlockCompleteInsideCallSwing() {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean ran = new AtomicBoolean();
        EHelper.callSwing(() -> {
            try {
                final VaadinSession session = VaadinSession.getCurrent();
                session.lock();
                try {
                    ran.set(true);
                } finally {
                    session.unlock();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        Assertions.assertNull(failure.get());
        Assertions.assertTrue(ran.get());
    }

    /** {@code UI.accessSynchronously} takes the session lock too, so it hit the same recursion. */
    @Test
    public void accessSynchronouslyRunsInsideCallSwing() {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicBoolean ran = new AtomicBoolean();
        EHelper.callSwing(() -> {
            try {
                UI.getCurrent().accessSynchronously(() -> ran.set(true));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        Assertions.assertNull(failure.get());
        Assertions.assertTrue(ran.get());
    }

    /**
     * A UI virtual thread may not unlock below its carrier's hold: the carrier owns the lock until
     * the thread returns or unmounts.
     */
    @Test
    public void unlockingBelowTheCarriersHoldThrows() {
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        EHelper.callSwing(() -> {
            try {
                VaadinSession.getCurrent().unlock();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        Assertions.assertInstanceOf(IllegalStateException.class, thrown.get());
    }

    /** Off the virtual thread the wrapper is the real lock again — no pretend hold in sight. */
    @Test
    public void theTestThreadSeesTheRealLock() {
        final VirtualThreadAwareLock lock = (VirtualThreadAwareLock) VaadinSession.getCurrent().getLockInstance();
        Assertions.assertFalse(UIFibers.isInUIFiber());
        Assertions.assertTrue(lock.isHeldByCurrentThread());
        Assertions.assertEquals(1, lock.getHoldCount());
    }
}
