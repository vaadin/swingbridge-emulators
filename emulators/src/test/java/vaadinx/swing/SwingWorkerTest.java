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

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.EmulatorContext;
import vaadinx.util.prefs.BridgedPreferences;
import vaadinx.util.prefs.VaadinPreferencesFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SwingWorker behavior that runs entirely on-EDT (under
 * Karibu's session lock): construction, configuration, the EDT-throw
 * guards on get(), publish/process/done callbacks, PCE for state and
 * progress. Tests that need the test thread to be off-EDT (untimed
 * get(), cancel surfacing CancellationException) live in
 * {@link SwingWorkerOffEdtTest}, which doesn't @BeforeEach Karibu — its test
 * methods run as background threads.
 */
class SwingWorkerTest extends AbstractKaribuTest {

    /**
     * Karibu's test thread holds the session lock continuously, so the
     * worker thread's {@code ui.access(...)} hops for done() / process() / PCE
     * queue but never drain on their own. Tests poll {@link MockVaadin#runUIQueue}
     * while waiting on a latch — the runtime equivalent of "let the EDT drain."
     */
    private static boolean awaitWithDrain(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            MockVaadin.runUIQueue();
            if (latch.await(20, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    // ------- doInBackground reads/writes browser-backed prefs (D_prefs_scope_split Part 3) -------

    @Test
    @DisplayName("doInBackground reads and writes localStorage prefs via the bridge")
    void doInBackgroundReadsAndWritesLocalStoragePrefs() throws InterruptedException {
        VaadinPreferencesFactory.setTestMode(true);
        try {
            Class<?>[] kind = new Class<?>[1];
            String[] readBack = new String[1];
            // doInBackground throwing lands in the worker's exception field, not
            // the test thread — surface it here (via the happens-before the
            // finished latch establishes) so a bridge failure fails loud with its
            // real cause instead of masquerading as a null read-back.
            Throwable[] workerError = new Throwable[1];
            CountDownLatch finished = new CountDownLatch(1);
            EHelper.callSwing(() -> new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        // No current UI on the worker VT; userRoot() routes to the
                        // BridgedPreferences, whose Spi ops bounce to the live UI.
                        Preferences node = Preferences.userNodeForPackage(SwingWorkerTest.class);
                        kind[0] = Preferences.userRoot().getClass();
                        node.put("serverUrl", "https://example.com");   // bridged write
                        readBack[0] = node.get("serverUrl", "MISSING"); // bridged read
                    } catch (Throwable t) {
                        workerError[0] = t;
                        throw t;
                    }
                    return null;
                }

                @Override
                protected void done() {
                    finished.countDown();
                }
            }.execute());
            assertTrue(awaitWithDrain(finished), "worker did not finish (bridge may have hung)");
            if (workerError[0] != null) {
                throw new AssertionError("doInBackground failed via the prefs bridge: " + workerError[0],
                        workerError[0]);
            }
            assertEquals(BridgedPreferences.class, kind[0]);
            assertEquals("https://example.com", readBack[0]);
        } finally {
            VaadinPreferencesFactory.setTestMode(false);
        }
    }

    @Test
    @DisplayName("callOnLiveUISync propagates a body failure to the worker (fails loud)")
    void callOnLiveUiSyncPropagatesABodyFailure() throws InterruptedException {
        Throwable[] err = new Throwable[1];
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                EHelper.callOnLiveUISync(EmulatorContext.get().session(), () -> {
                    throw new IllegalStateException("boom from the UI body");
                });
                return null;
            }

            @Override
            protected void done() {
                // Worker already finished → get() doesn't block; it wraps
                // doInBackground's exception in ExecutionException.
                try {
                    get();
                } catch (Throwable t) {
                    err[0] = t;
                }
                finished.countDown();
            }
        }.execute());
        assertTrue(awaitWithDrain(finished));
        ExecutionException ex = assertInstanceOf(ExecutionException.class, err[0], "expected ExecutionException");
        assertEquals("boom from the UI body", ex.getCause().getMessage());
    }

    // ------- doInBackground + done() round-trip -------

    @Test
    @DisplayName("doInBackground runs off EDT, done runs on EDT")
    void doInBackgroundOffEdtDoneOnEdt() throws InterruptedException {
        boolean[] onEdtBg = new boolean[1];
        boolean[] onEdtDone = new boolean[1];
        CountDownLatch finished = new CountDownLatch(1);

        EHelper.callSwing(() -> new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                onEdtBg[0] = UI.getCurrent() != null;
                return "ok";
            }

            @Override
            protected void done() {
                onEdtDone[0] = UI.getCurrent() != null;
                finished.countDown();
            }
        }.execute());
        assertTrue(awaitWithDrain(finished));
        assertFalse(onEdtBg[0], "doInBackground should not see a current UI");
        assertTrue(onEdtDone[0], "done() should run on the EDT");
    }

    // ------- doInBackground sees the user's browser zone -------

    @Test
    @DisplayName("doInBackground formats dates in the browser zone, not the server zone")
    void doInBackgroundFormatsInTheBrowserZone() throws InterruptedException {
        String[] formatted = new String[1];
        CountDownLatch finished = new CountDownLatch(1);
        TimeZone savedDefault = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("GMT+09:00")); // server != user (UTC)
            EHelper.callSwing(() -> new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    // The SDF emulator on this worker thread must resolve the
                    // captured browser zone via the EmulatorContext, not the
                    // GMT+9 server default.
                    formatted[0] = new vaadinx.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(0));
                    return null;
                }

                @Override
                protected void done() {
                    finished.countDown();
                }
            }.execute());
            assertTrue(awaitWithDrain(finished));
            assertEquals("1970-01-01 00:00:00", formatted[0],
                    "doInBackground should format in the browser zone (UTC)");
        } finally {
            TimeZone.setDefault(savedDefault);
        }
    }

    // ------- doInBackground really is a background thread (D_worker_platform_threads) -------

    /**
     * Starts a worker the way an app does — from inside {@link EHelper#callSwing} — and
     * returns the thread its body ran on. Starting it there is what makes the test
     * faithful: a virtual thread created inside a callSwing UI fiber inherits the loom
     * runner's custom scheduler, and the plain {@code Executors.newVirtualThreadPerTaskExecutor()}
     * that reads as "give me the default scheduler" does not opt out of that.
     *
     * <p>The caller asserts the thread is <b>not virtual</b> — a mechanism check, and
     * deliberately so. Karibu's carrier is the test thread, so a mounted worker VT
     * still reports a distinct {@code Thread}, a null {@code UI.getCurrent()} and a
     * prompt {@code callSwing} return; all three property-level framings were tried
     * and none separates the two pools (D_worker_platform_threads). Here the thread
     * kind is the only honest signal.
     *
     * @return the thread {@code doInBackground} ran on
     */
    private Thread runWorkerAndCaptureItsThread(Runnable alsoRecord) throws InterruptedException {
        Thread[] workerThread = new Thread[1];
        CountDownLatch started = new CountDownLatch(1);
        EHelper.callSwing(() -> new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                workerThread[0] = Thread.currentThread();
                alsoRecord.run();
                started.countDown();
                return null;
            }
        }.execute());
        assertTrue(started.await(5, TimeUnit.SECONDS), "doInBackground never ran");
        return workerThread[0];
    }

    @Test
    @DisplayName("doInBackground runs on a platform thread, off the UI thread")
    void doInBackgroundRunsOffTheUiThread() throws InterruptedException {
        boolean[] sawUi = new boolean[1];
        boolean[] sawEdt = new boolean[1];
        Thread worker = runWorkerAndCaptureItsThread(() -> {
            sawUi[0] = UI.getCurrent() != null;
            sawEdt[0] = SwingUtilities.isEventDispatchThread();
        });
        assertFalse(worker.isVirtual(),
                "a virtual worker thread inherits the loom runner's scheduler rather than the JDK's "
                        + "default one — see D_worker_platform_threads");
        assertFalse(sawUi[0], "doInBackground should have no current UI");
        assertFalse(sawEdt[0], "doInBackground is not the EDT");
    }

    @Test
    @DisplayName("worker threads are daemon and named SwingWorker-*")
    void workerThreadsAreDaemonAndNamed() throws InterruptedException {
        Thread worker = runWorkerAndCaptureItsThread(() -> {});
        assertTrue(worker.isDaemon(), "a pending worker must not hold the JVM open");
        assertTrue(worker.getName().startsWith("SwingWorker-"),
                "unexpected worker thread name: " + worker.getName());
    }

    // ------- get() throws ISE when called from EDT -------

    @Test
    @DisplayName("get from EDT throws ISE with deadlock message")
    void getFromEdtThrowsWithDeadlockMessage() {
        SwingWorker<String, Void> w = createWorkerInsideCallSwing(() -> {
            Thread.sleep(1000);
            return "never-read";
        });
        EHelper.callSwing(w::execute);
        EHelper.callSwing(() -> {
            IllegalStateException ex = assertThrows(IllegalStateException.class, w::get);
            assertTrue(ex.getMessage().contains("would deadlock under Swing"),
                    "Expected deadlock-pointer message; got: " + ex.getMessage());
        });
        // Don't leave the worker hanging — it'll finish its sleep on its own,
        // and detach-listener teardown will clean up if not.
        w.cancel(true);
    }

    @Test
    @DisplayName("get with timeout from EDT throws ISE")
    void getWithTimeoutFromEdtThrows() {
        SwingWorker<String, Void> w = createWorkerInsideCallSwing(() -> {
            Thread.sleep(1000);
            return "x";
        });
        EHelper.callSwing(w::execute);
        EHelper.callSwing(() ->
                assertThrows(IllegalStateException.class, () -> w.get(10, TimeUnit.MILLISECONDS)));
        w.cancel(true);
    }

    // ------- publish/process coalesces -------

    @Test
    @DisplayName("publish chunks coalesce into one process call when fast")
    void publishChunksCoalesceIntoOneProcessCall() throws InterruptedException {
        List<Integer> received = Collections.synchronizedList(new java.util.ArrayList<>());
        AtomicInteger processCalls = new AtomicInteger(0);
        CountDownLatch finished = new CountDownLatch(1);

        EHelper.callSwing(() -> new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() {
                publish(1, 2, 3);
                publish(4, 5);
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                processCalls.incrementAndGet();
                received.addAll(chunks);
            }

            @Override
            protected void done() {
                finished.countDown();
            }
        }.execute());
        assertTrue(awaitWithDrain(finished));
        // process should have run exactly once with all 5 chunks merged.
        assertEquals(1, processCalls.get());
        assertEquals(List.of(1, 2, 3, 4, 5), received);
    }

    // ------- State PCE: PENDING -> STARTED -> DONE -------

    @Test
    @DisplayName("state PCE fires PENDING-STARTED-DONE in order")
    void statePceFiresInOrder() throws InterruptedException {
        List<SwingWorker.StateValue> states = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch finished = new CountDownLatch(1);

        EHelper.callSwing(() -> {
            SwingWorker<Void, Void> w = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    return null;
                }

                @Override
                protected void done() {
                    finished.countDown();
                }
            };
            w.addPropertyChangeListener(e -> {
                if (SwingWorker.STATE_PROPERTY.equals(e.getPropertyName())) {
                    states.add((SwingWorker.StateValue) e.getNewValue());
                }
            });
            w.execute();
        });
        assertTrue(awaitWithDrain(finished));
        assertEquals(List.of(SwingWorker.StateValue.STARTED, SwingWorker.StateValue.DONE), states);
    }

    // ------- progress PCE -------

    @Test
    @DisplayName("setProgress fires progress PCE")
    void setProgressFiresProgressPce() throws InterruptedException {
        List<Integer> received = Collections.synchronizedList(new java.util.ArrayList<>());
        CountDownLatch finished = new CountDownLatch(1);

        EHelper.callSwing(() -> {
            SwingWorker<Void, Void> w = new SwingWorker<>() {
                @Override
                protected Void doInBackground() {
                    setProgress(25);
                    setProgress(50);
                    setProgress(100);
                    return null;
                }

                @Override
                protected void done() {
                    finished.countDown();
                }
            };
            w.addPropertyChangeListener(e -> {
                if (SwingWorker.PROGRESS_PROPERTY.equals(e.getPropertyName())) {
                    received.add((Integer) e.getNewValue());
                }
            });
            w.execute();
        });
        assertTrue(awaitWithDrain(finished));
        assertEquals(List.of(25, 50, 100), received);
    }

    @Test
    @DisplayName("setProgress out of range throws IAE")
    void setProgressOutOfRangeThrows() {
        // `var` keeps the anonymous type, so the extra trigger() method is callable.
        var w = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                return null;
            }

            void trigger(int value) {
                setProgress(value);
            }
        };
        assertThrows(IllegalArgumentException.class, () -> w.trigger(-1));
        assertThrows(IllegalArgumentException.class, () -> w.trigger(101));
    }

    @Test
    @DisplayName("getProgress reads back what setProgress wrote")
    void getProgressReadsBackWhatSetProgressWrote() throws InterruptedException {
        // Off-EDT read of the volatile field; doesn't depend on PCE drain.
        CountDownLatch finished = new CountDownLatch(1);
        SwingWorker<Integer, Void> w = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                setProgress(42);
                return 0;
            }

            @Override
            protected void done() {
                finished.countDown();
            }
        };
        EHelper.callSwing(w::execute);
        assertTrue(awaitWithDrain(finished));
        assertEquals(42, w.getProgress());
    }

    private static <T> SwingWorker<T, Void> createWorkerInsideCallSwing(Callable<T> body) {
        List<SwingWorker<T, Void>> ref = new java.util.ArrayList<>();
        EHelper.callSwing(() -> ref.add(new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return body.call();
            }
        }));
        return ref.get(0);
    }
}
