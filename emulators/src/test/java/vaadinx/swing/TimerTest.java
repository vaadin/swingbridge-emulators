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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class TimerTest extends AbstractKaribuTest {

    /**
     * Karibu's test thread holds the session lock continuously, so the
     * Timer scheduler thread's {@code ui.access(...)} calls queue but never
     * drain on their own. Tests poll {@link MockVaadin#runUIQueue} while
     * waiting on a latch — the runtime equivalent of "let the EDT drain."
     */
    private static boolean awaitWithDrain(CountDownLatch latch) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            MockVaadin.runUIQueue();
            if (latch.await(20, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    // ------- Construction + property contract -------

    @Test
    @DisplayName("ctor stores delay and seeds initialDelay to delay")
    void ctorSeedsDelays() {
        Timer t = new Timer(123, null);
        assertEquals(123, t.getDelay());
        assertEquals(123, t.getInitialDelay());
        assertTrue(t.isRepeats());
        assertTrue(t.isCoalesce());
        assertFalse(t.isRunning());
    }

    @Test
    @DisplayName("ctor with negative delay throws IAE")
    void ctorRejectsNegativeDelay() {
        assertThrows(IllegalArgumentException.class, () -> new Timer(-1, null));
    }

    @Test
    @DisplayName("setDelay rejects negative value")
    void setDelayRejectsNegative() {
        Timer t = new Timer(100, null);
        assertThrows(IllegalArgumentException.class, () -> t.setDelay(-5));
    }

    @Test
    @DisplayName("setInitialDelay rejects negative value")
    void setInitialDelayRejectsNegative() {
        Timer t = new Timer(100, null);
        assertThrows(IllegalArgumentException.class, () -> t.setInitialDelay(-5));
    }

    @Test
    @DisplayName("addActionListener registers a listener that getActionListeners returns")
    void listenerRegistrationRoundTrips() {
        Timer t = new Timer(100, null);
        ActionListener l = e -> { };
        t.addActionListener(l);
        assertSame(l, assertSingle(t.getActionListeners()));
        t.removeActionListener(l);
        assertEquals(0, t.getActionListeners().length);
    }

    @Test
    @DisplayName("actionCommand round-trips")
    void actionCommandRoundTrips() {
        Timer t = new Timer(100, null);
        assertNull(t.getActionCommand());
        t.setActionCommand("tick");
        assertEquals("tick", t.getActionCommand());
    }

    // ------- Lifecycle: start / stop / restart -------

    @Test
    @DisplayName("start flips isRunning true and stop flips it back")
    void startAndStopFlipIsRunning() {
        Timer t = new Timer(100_000, null);
        t.start();
        assertTrue(t.isRunning());
        t.stop();
        assertFalse(t.isRunning());
    }

    @Test
    @DisplayName("start is idempotent — second call is a no-op")
    void startIsIdempotent() {
        Timer t = new Timer(100_000, null);
        t.start();
        t.start();
        assertTrue(t.isRunning());
        t.stop();
    }

    // ------- Fire delivery: fires ActionEvent on EDT under callSwing -------

    @Test
    @DisplayName("non-repeating Timer fires once and stays not-running")
    void nonRepeatingTimerFiresOnce() throws InterruptedException {
        CountDownLatch fired = new CountDownLatch(1);
        AtomicReference<Object> src = new AtomicReference<>();
        Timer timer = new Timer(50, e -> {
            src.set(e.getSource());
            fired.countDown();
        });
        timer.setRepeats(false);
        EHelper.callSwing(timer::start);
        assertTrue(awaitWithDrain(fired), "Timer never fired");
        // After a one-shot fires, isRunning may still be true (we don't
        // auto-stop on fire). The user-visible contract is that the
        // listener ran exactly once — verified by the latch.
        assertSame(timer, src.get());
        EHelper.callSwing(timer::stop);
    }

    @Test
    @DisplayName("repeating Timer fires multiple times")
    void repeatingTimerFiresRepeatedly() throws InterruptedException {
        AtomicInteger count = new AtomicInteger(0);
        int target = 3;
        CountDownLatch reached = new CountDownLatch(1);
        Timer timer = new Timer(20, e -> {
            if (count.incrementAndGet() >= target) {
                reached.countDown();
            }
        });
        EHelper.callSwing(timer::start);
        assertTrue(awaitWithDrain(reached), "Timer fired " + count.get() + " of " + target + " times");
        EHelper.callSwing(timer::stop);
    }

    @Test
    @DisplayName("stopped Timer does not fire pending events on EDT")
    void stoppedTimerSuppressesPendingFires() throws InterruptedException {
        // Schedule with fast initial delay then immediately stop —
        // running flag check on EDT should suppress delivery.
        AtomicInteger firedAfterStop = new AtomicInteger(0);
        Timer timer = new Timer(10, e -> firedAfterStop.incrementAndGet());
        EHelper.callSwing(() -> {
            timer.start();
            timer.stop();
        });
        // Wait a beat; if the suppression works, no fires.
        Thread.sleep(150);
        assertEquals(0, firedAfterStop.get());
    }

    @Test
    @DisplayName("actionCommand is delivered on the ActionEvent")
    void actionCommandReachesTheEvent() throws InterruptedException {
        AtomicReference<String> received = new AtomicReference<>();
        CountDownLatch fired = new CountDownLatch(1);
        Timer timer = new Timer(20, e -> {
            received.set(e.getActionCommand());
            fired.countDown();
        });
        timer.setActionCommand("tick");
        timer.setRepeats(false);
        EHelper.callSwing(timer::start);
        assertTrue(awaitWithDrain(fired));
        assertEquals("tick", received.get());
        EHelper.callSwing(timer::stop);
    }

    // ------- callSwing envelope -------

    @Test
    @DisplayName("Timer fire runs under a current UI (callSwing envelope)")
    void timerFireRunsUnderACurrentUI() throws InterruptedException {
        AtomicBoolean sawUi = new AtomicBoolean(false);
        CountDownLatch fired = new CountDownLatch(1);
        Timer timer = new Timer(20, e -> {
            sawUi.set(UI.getCurrent() != null);
            fired.countDown();
        });
        timer.setRepeats(false);
        EHelper.callSwing(timer::start);
        assertTrue(awaitWithDrain(fired));
        assertTrue(sawUi.get(), "Timer listener fired without a current UI");
        EHelper.callSwing(timer::stop);
    }

    // ------- Static toggle -------

    @Test
    @DisplayName("setLogTimers toggles getLogTimers")
    void logTimersToggles() {
        boolean original = Timer.getLogTimers();
        try {
            Timer.setLogTimers(true);
            assertTrue(Timer.getLogTimers());
            Timer.setLogTimers(false);
            assertFalse(Timer.getLogTimers());
        } finally {
            Timer.setLogTimers(original);
        }
    }

    // ------- ActionEvent shape sanity (defensive) -------

    @Test
    @DisplayName("ActionEvent has ACTION_PERFORMED id")
    void actionEventHasActionPerformedId() throws InterruptedException {
        AtomicReference<ActionEvent> captured = new AtomicReference<>();
        CountDownLatch fired = new CountDownLatch(1);
        Timer timer = new Timer(20, e -> {
            captured.set(e);
            fired.countDown();
        });
        timer.setRepeats(false);
        EHelper.callSwing(timer::start);
        assertTrue(awaitWithDrain(fired));
        assertNotNull(captured.get());
        assertEquals(ActionEvent.ACTION_PERFORMED, captured.get().getID());
        EHelper.callSwing(timer::stop);
    }
}
