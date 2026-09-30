/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.BoundedRangeModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A progress bar's {@link BoundedRangeModel} advanced from a background thread
 * (SD_background_model_hop): the fan-out and the peer write land on the bar's UI thread when
 * it has one, and run inline when it does not — synchronously either way, as Swing fires a
 * model's listeners before the mutator returns.
 *
 * <p>Karibu's test thread holds the session lock for the whole test, so {@link #runOnWorker}
 * releases it around the join; the worker needs it to hop.
 */
class SJProgressBarBackgroundModelTest extends AbstractKaribuTest {

    /** What one listener call observed about the thread it ran on. */
    private record Seen(int value, Thread thread, UI ui, boolean locked) {}

    private static void runOnWorker(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "progress-reporter");
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("the worker never returned");
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
    }

    private static List<Seen> recordFanOut(SJProgressBar bar) {
        List<Seen> seen = new CopyOnWriteArrayList<>();
        bar.addChangeListener(e -> seen.add(new Seen(
                bar.getModel().getValue(), Thread.currentThread(), UI.getCurrent(), sessionLocked())));
        return seen;
    }

    private static boolean sessionLocked() {
        VaadinSession s = VaadinSession.getCurrent();
        return s != null && s.hasLock();
    }

    @Test
    @DisplayName("attached: the worker's progress fans out on the UI thread, before setValue returns")
    void attachedBarHopsToItsUI() {
        SJProgressBar bar = new SJProgressBar(0, 10);
        UI ui = UI.getCurrent();
        ui.add(bar);
        List<Seen> seen = recordFanOut(bar);
        AtomicReference<Integer> seenWhenSetReturned = new AtomicReference<>();
        Thread[] worker = new Thread[1];

        runOnWorker(() -> {
            worker[0] = Thread.currentThread();
            bar.getModel().setValue(3);
            seenWhenSetReturned.set(seen.size());
            bar.getModel().setValue(7);
        });

        assertEquals(List.of(3, 7), seen.stream().map(Seen::value).toList());
        assertEquals(1, seenWhenSetReturned.get(), "setValue returned before its listeners ran");
        for (Seen s : seen) {
            assertSame(worker[0], s.thread(), "accessSynchronously runs the body on the calling thread");
            assertSame(ui, s.ui());
            assertTrue(s.locked(), "value " + s.value() + " fanned out without the session lock");
        }
        assertEquals(7.0, bar.getValue());
    }

    @Test
    @DisplayName("detached but built in a session: the fan-out runs under that session's lock, with no UI current")
    void detachedBarRunsUnderItsSessionLock() {
        SJProgressBar bar = new SJProgressBar(0, 10);
        List<Seen> seen = recordFanOut(bar);

        runOnWorker(() -> bar.getModel().setValue(4));

        assertEquals(List.of(4), seen.stream().map(Seen::value).toList());
        assertNull(seen.get(0).ui(), "no UI holds the tree, so none is made current");
        assertTrue(seen.get(0).locked(), "the bar knows its session, so the hop takes its lock");
        assertEquals(4.0, bar.getValue());
    }

    @Test
    @DisplayName("a UI that has left its session is treated as detached")
    void orphanedBarRunsInline() {
        SJProgressBar bar = new SJProgressBar(0, 10);
        UI ui = UI.getCurrent();
        ui.add(bar);
        List<Seen> seen = recordFanOut(bar);
        VaadinSession session = ui.getSession();
        ui.getInternals().setSession(null);
        try {
            runOnWorker(() -> bar.getModel().setValue(5));
        } finally {
            ui.getInternals().setSession(session); // Karibu's teardown needs it back
        }

        assertEquals(List.of(5), seen.stream().map(Seen::value).toList());
        assertNull(seen.get(0).ui());
    }
}
