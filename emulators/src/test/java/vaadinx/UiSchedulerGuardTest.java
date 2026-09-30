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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A throwing {@link UiScheduler} task is reported and keeps repeating — the faithful
 * answer, since a JDK {@code Timer} does not stop firing because one fire threw. A bare
 * {@code ScheduledExecutorService} would cancel the future and discard the throwable.
 * Also the only test that drives {@code EHelper.reportUncaught} from the scheduler
 * thread, which holds no session lock. See {@code D_uncaught_handler_chain}.
 */
class UiSchedulerGuardTest extends AbstractKaribuTest {

    @Test
    @DisplayName("a repeating task that throws is reported and still repeats")
    void throwingRepeatingTaskReportsAndKeepsRepeating() throws Exception {
        List<Throwable> seen = new CopyOnWriteArrayList<>();
        VaadinSession session = VaadinSession.getCurrent();
        session.setErrorHandler(event -> seen.add(event.getThrowable()));

        AtomicInteger runs = new AtomicInteger();
        CountDownLatch thirdRun = new CountDownLatch(3);
        ScheduledFuture<?> future = UiScheduler.scheduleWithFixedDelay(session, () -> {
            // Only the first three runs throw. A fourth report could be queued after the
            // drain below and would then land in Karibu's teardown drain, where the error
            // handler is no longer this test's collector but one that rethrows.
            if (runs.incrementAndGet() > 3) return;
            thirdRun.countDown();
            throw new IllegalStateException("boom");
        }, 0, 10, TimeUnit.MILLISECONDS);

        assertTrue(thirdRun.await(5, TimeUnit.SECONDS),
                "a bare ScheduledExecutorService would have cancelled the task after run 1");

        // The latch counts down before the throw, so run 3's report is still on its way
        // when await() returns. Reports arrive via session.access from the lock-less
        // scheduler thread; drain the way a request boundary would, until they land.
        for (int i = 0; i < 500 && seen.size() < 3; i++) {
            session.getService().runPendingAccessTasks(session);
            Thread.sleep(10);
        }
        future.cancel(false);
        assertTrue(seen.size() >= 3, "expected one report per run, saw " + seen.size());
        assertEquals("boom", seen.get(0).getMessage());
    }

    @Test
    @DisplayName("a one-shot task that throws is reported")
    void throwingOneShotTaskIsReported() throws Exception {
        List<Throwable> seen = new CopyOnWriteArrayList<>();
        VaadinSession session = VaadinSession.getCurrent();
        session.setErrorHandler(event -> seen.add(event.getThrowable()));

        CountDownLatch ran = new CountDownLatch(1);
        UiScheduler.schedule(session, () -> {
            ran.countDown();
            throw new IllegalStateException("one-shot boom");
        }, 0, TimeUnit.MILLISECONDS);

        assertTrue(ran.await(5, TimeUnit.SECONDS));
        // The report is queued after the body ran; give the access task a moment to land.
        for (int i = 0; i < 50 && seen.isEmpty(); i++) {
            session.getService().runPendingAccessTasks(session);
            Thread.sleep(10);
        }
        assertEquals(1, seen.size());
        assertEquals("one-shot boom", seen.get(0).getMessage());
    }
}
