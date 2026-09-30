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
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EmulatorContext}. {@link AbstractKaribuTest} makes the test thread a
 * "UI thread" with a current session + UTC browser zone; background-thread
 * behavior is exercised on a fresh {@link Thread} (no {@code VaadinSession.getCurrent()}).
 */
class EmulatorContextTest extends AbstractKaribuTest {

    /**
     * Runs {@code block} on a bare platform thread and re-throws whatever it threw,
     * so {@code assertThrows} around the call sees the body's own exception.
     */
    private static <T> T onBackgroundThread(Supplier<T> block) {
        AtomicReference<Object> slot = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                slot.set(block.get());
            } catch (Throwable e) {
                slot.set(e);
            }
        });
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        Object r = slot.get();
        if (r instanceof Throwable throwable) {
            throw throwable instanceof RuntimeException re ? re : new IllegalStateException(throwable);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) r;
        return typed;
    }

    /** The void form — {@code onBackgroundThread(Runnable)} without a value to carry back. */
    private static void onBackgroundThread(Runnable block) {
        onBackgroundThread(() -> {
            block.run();
            return null;
        });
    }

    @Test
    @DisplayName("get on a UI thread captures the session and the browser zone")
    void getOnAUiThreadCapturesTheSessionAndTheBrowserZone() {
        EmulatorContext ctx = EmulatorContext.get();
        assertNotNull(ctx.session());
        assertEquals(ZoneOffset.UTC, ctx.zone()); // AbstractKaribuTest installs UTC
    }

    @Test
    @DisplayName("get on a background thread with no context throws")
    void getOnABackgroundThreadWithNoContextThrows() {
        assertThrows(IllegalStateException.class, () -> onBackgroundThread(EmulatorContext::get));
    }

    @Test
    @DisplayName("getOrNull on a background thread with no context returns null")
    void getOrNullOnABackgroundThreadWithNoContextReturnsNull() {
        assertNull(onBackgroundThread(EmulatorContext::getOrNull));
    }

    @Test
    @DisplayName("getOrNull on a session-present-but-unlocked thread does not throw and carries the zone")
    void getOrNullOnASessionPresentButUnlockedThreadDoesNotThrowAndCarriesTheZone() {
        // The license-checker shape: a current VaadinSession we don't hold the
        // lock on. getOrNull() must not throw (evaluating it reads BrowserTimeZone
        // off the session), and its lock-and-read path yields the real zone.
        VaadinSession session = VaadinSession.getCurrent();
        session.unlock();
        try {
            EmulatorContext ctx = EmulatorContext.getOrNull();
            assertNotNull(ctx, "getOrNull must return a context for a current (if unlocked) session");
            assertEquals(ZoneOffset.UTC, ctx.zone(), "lock-and-read should recover the stored zone");
        } finally {
            session.lock();   // restore for MockVaadin.tearDown()
        }
    }

    @Test
    @DisplayName("currentTimeZone on a UI thread is the browser zone")
    void currentTimeZoneOnAUiThreadIsTheBrowserZone() {
        assertEquals(0, EmulatorContext.currentTimeZone().getRawOffset()); // UTC
    }

    @Test
    @DisplayName("run propagates the captured zone to a background thread")
    void runPropagatesTheCapturedZoneToABackgroundThread() {
        EmulatorContext ctx = EmulatorContext.get(); // UTC, captured on the UI thread
        TimeZone saved = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00")); // server != user
            TimeZone tz = onBackgroundThread(() -> {
                AtomicReference<TimeZone> ref = new AtomicReference<>();
                ctx.run(() -> ref.set(EmulatorContext.currentTimeZone()));
                return ref.get();
            });
            assertEquals(0, tz.getRawOffset(),
                    "should use the propagated UTC zone, not the GMT+9 server default");
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    @DisplayName("currentTimeZone on a bare background thread falls back to the server default and warns once")
    void currentTimeZoneOnABareBackgroundThreadFallsBackToTheServerDefaultAndWarnsOnce() {
        List<String> warns = new ArrayList<>();
        Consumer<String> savedHook = EHelper.warnHook;
        TimeZone savedDefault = TimeZone.getDefault();
        try {
            EHelper.warnHook = warns::add;
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00"));
            TimeZone tz = onBackgroundThread(EmulatorContext::currentTimeZone);
            assertEquals("GMT+09:00", tz.getID(), "no context → server default zone");
            assertTrue(warns.stream().anyMatch(it -> it.contains("no EmulatorContext")),
                    "expected a server-zone WARN; got " + warns);
        } finally {
            EHelper.warnHook = savedHook;
            TimeZone.setDefault(savedDefault);
        }
    }

    @Test
    @DisplayName("run restores the previous context on exit (nesting)")
    void runRestoresThePreviousContextOnExitNesting() {
        EmulatorContext ctxUtc = EmulatorContext.get();
        BrowserTimeZone.setZoneId(ZoneId.of("GMT+05:00"));
        EmulatorContext ctxPlus5 = EmulatorContext.get();
        BrowserTimeZone.setZoneId(ZoneOffset.UTC); // restore session zone for other assertions

        onBackgroundThread(() -> {
            ctxUtc.run(() -> {
                assertEquals(ZoneOffset.UTC, EmulatorContext.getOrNull().zone());
                ctxPlus5.run(() ->
                        assertEquals(ZoneId.of("GMT+05:00"), EmulatorContext.getOrNull().zone()));
                assertEquals(ZoneOffset.UTC, EmulatorContext.getOrNull().zone(),
                        "inner run must restore the outer context");
            });
            assertNull(EmulatorContext.getOrNull(), "outer run must clear the context");
        });
    }

    @Test
    @DisplayName("call returns the body value")
    void callReturnsTheBodyValue() {
        EmulatorContext ctx = EmulatorContext.get();
        // call() takes a Callable, so it declares `throws Exception` — invisible
        // in Kotlin, and the Supplier the harness wants cannot carry it.
        assertEquals("ok", onBackgroundThread(() -> {
            try {
                return ctx.call(() -> "ok");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }));
    }

    @Test
    @DisplayName("run on a UI thread does not shadow the live session zone")
    void runOnAUiThreadDoesNotShadowTheLiveSessionZone() {
        // Even if a context carrying a different zone is applied, a live session
        // wins in currentTimeZone() — run/call are meant for background threads.
        BrowserTimeZone.setZoneId(ZoneId.of("GMT+05:00"));
        EmulatorContext ctxPlus5 = EmulatorContext.get();
        BrowserTimeZone.setZoneId(ZoneOffset.UTC);
        ctxPlus5.run(() -> assertEquals(0, EmulatorContext.currentTimeZone().getRawOffset(),
                "session (UTC) wins over the applied context on a UI thread"));
    }

    // ===========================================================
    // wrap(ExecutorService) / wrap(Executor)
    // ===========================================================

    /** Resolves {@code future}, unwrapping the worker's own failure. */
    private static <T> T await(Future<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a task submitted through a wrapped executor carries the submitter's context")
    void aTaskSubmittedThroughAWrappedExecutorCarriesTheSubmittersContext() {
        ExecutorService pool = EmulatorContext.wrap(Executors.newSingleThreadExecutor());
        try {
            // The worker has no current VaadinSession, so a non-null context there can
            // only have come from the submit.
            assertEquals(ZoneOffset.UTC, await(pool.submit(() -> EmulatorContext.get().zone())));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the context is captured per submit, not when the executor is wrapped")
    void theContextIsCapturedPerSubmitNotWhenTheExecutorIsWrapped() {
        // The property the decorator exists for: a pool is built once at startup, where
        // there is often no session at all, so a wrap-time snapshot would be useless.
        ExecutorService pool = EmulatorContext.wrap(Executors.newSingleThreadExecutor());
        try {
            BrowserTimeZone.setZoneId(ZoneId.of("GMT+05:00"));
            assertEquals(ZoneId.of("GMT+05:00"),
                    await(pool.submit(() -> EmulatorContext.get().zone())),
                    "the submit-time zone, not the wrap-time one");
        } finally {
            BrowserTimeZone.setZoneId(ZoneOffset.UTC);
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a submit from a context-less thread passes through rather than borrowing one")
    void aSubmitFromAContextLessThreadPassesThroughRatherThanBorrowingOne() {
        ExecutorService pool = EmulatorContext.wrap(Executors.newSingleThreadExecutor());
        try {
            // Submitting from a bare thread: nothing to capture, and the worker must not
            // inherit some unrelated user's session.
            assertNull(onBackgroundThread(() -> await(pool.submit(EmulatorContext::getOrNull))));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a wrapped submit leaves no context behind on the pooled thread")
    void aWrappedSubmitLeavesNoContextBehindOnThePooledThread() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = EmulatorContext.wrap(raw);
        try {
            assertNotNull(await(wrapped.submit(EmulatorContext::getOrNull)));
            // Same single thread, submitted around the decorator: the previous task's
            // context must have been restored away.
            assertNull(await(raw.submit(EmulatorContext::getOrNull)));
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    @DisplayName("invokeAll carries the context to every task")
    void invokeAllCarriesTheContextToEveryTask() {
        ExecutorService pool = EmulatorContext.wrap(Executors.newFixedThreadPool(2));
        try {
            List<Callable<ZoneId>> tasks = List.of(
                    () -> EmulatorContext.get().zone(),
                    () -> EmulatorContext.get().zone());
            List<ZoneId> zones = pool.invokeAll(tasks).stream().map(EmulatorContextTest::await).toList();
            assertEquals(List.of(ZoneOffset.UTC, ZoneOffset.UTC), zones);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("wrap(Executor) propagates too")
    void wrapExecutorPropagatesToo() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        Executor wrapped = EmulatorContext.wrap((Executor) raw);
        try {
            AtomicReference<EmulatorContext> seen = new AtomicReference<>();
            CountDownLatch ran = new CountDownLatch(1);
            wrapped.execute(() -> {
                seen.set(EmulatorContext.getOrNull());
                ran.countDown();
            });
            assertTrue(ran.await(10, TimeUnit.SECONDS), "the task should have run");
            assertNotNull(seen.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            raw.shutdownNow();
        }
    }

    // ─── D_submit_site: the contextless submit remembers where it came from ───

    /** The submit site as the task itself sees it, or null. Runs the submit off any session. */
    private static String submitSiteSeenBy(ExecutorService pool) {
        return onBackgroundThread(() -> await(pool.submit(EmulatorContext::submitSiteOrNull)));
    }

    @Test
    @DisplayName("a contextless submit records the frames it was submitted from")
    void aContextlessSubmitRecordsTheFramesItWasSubmittedFrom() {
        ExecutorService pool = EmulatorContext.wrap(Executors.newSingleThreadExecutor());
        try {
            String site = submitSiteSeenBy(pool);
            assertNotNull(site, "a contextless submit through a wrapped pool must record its site");
            // The whole value: the frames name the submitting code, not our own plumbing.
            assertTrue(site.contains(EmulatorContextTest.class.getName()),
                    "the site must reach the submitting caller; was:\n" + site);
            assertFalse(site.contains(EmulatorContext.class.getName() + ".capturing"),
                    "EmulatorContext's own frames must be dropped; was:\n" + site);
            assertTrue(site.lines().count() <= 6, "at most six frames; was:\n" + site);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a submit that had a context to propagate records no site")
    void aSubmitThatHadAContextRecordsNoSite() {
        // The happy path pays nothing: there is no diagnosis to carry, so no walk is taken.
        ExecutorService pool = EmulatorContext.wrap(Executors.newSingleThreadExecutor());
        try {
            assertNull(await(pool.submit(EmulatorContext::submitSiteOrNull)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("a recorded site leaves no residue on the pooled thread")
    void aRecordedSiteLeavesNoResidueOnThePooledThread() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = EmulatorContext.wrap(raw);
        try {
            assertNotNull(submitSiteSeenBy(wrapped));
            // Same single thread, submitted around the decorator: a retained site would
            // misattribute this task's failure to an unrelated earlier submit.
            assertNull(await(raw.submit(EmulatorContext::submitSiteOrNull)));
        } finally {
            raw.shutdownNow();
        }
    }

    @Test
    @DisplayName("invokeAll records the site on every task")
    void invokeAllRecordsTheSiteOnEveryTask() {
        ExecutorService pool = EmulatorContext.wrap(Executors.newFixedThreadPool(2));
        try {
            List<String> sites = onBackgroundThread(() -> {
                List<Callable<String>> tasks = List.of(
                        EmulatorContext::submitSiteOrNull, EmulatorContext::submitSiteOrNull);
                try {
                    return pool.invokeAll(tasks).stream().map(EmulatorContextTest::await).toList();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            });
            assertEquals(2, sites.size());
            sites.forEach(s -> assertNotNull(s, "every task of a contextless invokeAll carries it"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("lifecycle calls reach the delegate")
    void lifecycleCallsReachTheDelegate() {
        ExecutorService raw = Executors.newSingleThreadExecutor();
        ExecutorService wrapped = EmulatorContext.wrap(raw);
        wrapped.shutdown();
        assertTrue(raw.isShutdown());
        assertTrue(wrapped.isShutdown());
    }
}
