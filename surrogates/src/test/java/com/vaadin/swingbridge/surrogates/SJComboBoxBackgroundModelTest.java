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

import java.awt.event.ItemEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultComboBoxModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@link DefaultComboBoxModel} filled from a background thread (SD_background_model_hop):
 * the fan-out and the peer write land on the combo's UI thread when it has one, and run
 * inline when it does not — synchronously either way, as Swing fires a model's listeners
 * before the mutator returns.
 *
 * <p>Karibu's test thread holds the session lock for the whole test, so {@link #runOnWorker}
 * releases it around the join; the worker needs it to hop.
 */
class SJComboBoxBackgroundModelTest extends AbstractKaribuTest {

    /** What one listener call observed about the thread it ran on. */
    private record Seen(String what, Thread thread, UI ui, boolean locked) {}

    private static void runOnWorker(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "model-filler");
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

    private static List<Seen> recordFanOut(SJComboBox<String> cb) {
        List<Seen> seen = new CopyOnWriteArrayList<>();
        cb.addItemListener(e -> seen.add(new Seen(
                (e.getStateChange() == ItemEvent.SELECTED ? "selected " : "deselected ") + e.getItem(),
                Thread.currentThread(), UI.getCurrent(), sessionLocked())));
        cb.addActionListener(e -> seen.add(new Seen(
                "action", Thread.currentThread(), UI.getCurrent(), sessionLocked())));
        return seen;
    }

    private static boolean sessionLocked() {
        VaadinSession s = VaadinSession.getCurrent();
        return s != null && s.hasLock();
    }

    @Test
    @DisplayName("attached: the worker's mutation fans out on the UI thread, before addElement returns")
    void attachedComboHopsToItsUI() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        SJComboBox<String> cb = new SJComboBox<>(model);
        UI ui = UI.getCurrent();
        ui.add(cb);
        List<Seen> seen = recordFanOut(cb);
        AtomicReference<Integer> seenWhenAddReturned = new AtomicReference<>();
        Thread[] worker = new Thread[1];

        runOnWorker(() -> {
            worker[0] = Thread.currentThread();
            model.addElement("a");
            seenWhenAddReturned.set(seen.size());
            model.addElement("b");
        });

        assertEquals(List.of("selected a", "action"), seen.stream().map(Seen::what).toList());
        assertEquals(2, seenWhenAddReturned.get(), "addElement returned before its listeners ran");
        for (Seen s : seen) {
            assertSame(worker[0], s.thread(), "accessSynchronously runs the body on the calling thread");
            assertSame(ui, s.ui());
            assertTrue(s.locked(), s.what() + " ran without the session lock");
        }
        assertEquals(List.of("a", "b"), cb.getListDataView().getItems().toList());
        assertEquals("a", cb.getValue());
    }

    @Test
    @DisplayName("detached but built in a session: the fan-out runs under that session's lock, with no UI current")
    void detachedComboRunsUnderItsSessionLock() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        SJComboBox<String> cb = new SJComboBox<>(model);
        List<Seen> seen = recordFanOut(cb);

        runOnWorker(() -> {
            model.addElement("a");
            model.addElement("b");
        });

        assertEquals(List.of("selected a", "action"), seen.stream().map(Seen::what).toList());
        for (Seen s : seen) {
            assertNull(s.ui(), "no UI holds the tree, so none is made current");
            assertTrue(s.locked(), "the combo knows its session, so the hop takes its lock");
        }
        assertEquals(List.of("a", "b"), cb.getListDataView().getItems().toList());
        assertEquals("a", cb.getValue());
    }

    @Test
    @DisplayName("a UI that has left its session is treated as detached")
    void orphanedComboRunsInline() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        SJComboBox<String> cb = new SJComboBox<>(model);
        UI ui = UI.getCurrent();
        ui.add(cb);
        List<Seen> seen = recordFanOut(cb);
        VaadinSession session = ui.getSession();
        ui.getInternals().setSession(null);
        try {
            runOnWorker(() -> model.addElement("a"));
        } finally {
            ui.getInternals().setSession(session); // Karibu's teardown needs it back
        }

        assertEquals(List.of("selected a", "action"), seen.stream().map(Seen::what).toList());
        assertNull(seen.get(0).ui());
    }
}
