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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultListModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@link DefaultListModel} filled from a background thread (SD_background_model_hop): the
 * selection shift an insert or removal causes, and the Grid refresh, land on the list's UI
 * thread when it has one and run under its session's lock when it does not — synchronously
 * either way, as Swing fires a model's listeners before the mutator returns.
 *
 * <p>Karibu's test thread holds the session lock for the whole test, so {@link #runOnWorker}
 * releases it around the join; the worker needs it to hop.
 */
class SJListBackgroundModelTest extends AbstractKaribuTest {

    /** What one selection-listener call observed about the thread it ran on. */
    private record Seen(int selectedIndex, Thread thread, UI ui, boolean locked) {}

    private static void runOnWorker(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "list-loader");
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

    private static List<Seen> recordSelectionFanOut(SJList<String> list) {
        List<Seen> seen = new CopyOnWriteArrayList<>();
        list.getListSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            seen.add(new Seen(list.getListSelectionModel().getMinSelectionIndex(),
                    Thread.currentThread(), UI.getCurrent(), sessionLocked()));
        });
        return seen;
    }

    private static boolean sessionLocked() {
        VaadinSession s = VaadinSession.getCurrent();
        return s != null && s.hasLock();
    }

    private static DefaultListModel<String> abc() {
        DefaultListModel<String> model = new DefaultListModel<>();
        model.addAll(List.of("a", "b", "c"));
        return model;
    }

    @Test
    @DisplayName("attached: the worker's insert shifts the selection on the UI thread, before add returns")
    void attachedListHopsToItsUI() {
        DefaultListModel<String> model = abc();
        SJList<String> list = new SJList<>(model);
        UI ui = UI.getCurrent();
        ui.add(list);
        list.setSelectedIndex(1);
        List<Seen> seen = recordSelectionFanOut(list);
        AtomicReference<Integer> seenWhenAddReturned = new AtomicReference<>();
        Thread[] worker = new Thread[1];

        runOnWorker(() -> {
            worker[0] = Thread.currentThread();
            model.add(0, "z");
            seenWhenAddReturned.set(seen.size());
            model.addElement("y");
        });

        assertEquals(List.of(2), seen.stream().map(Seen::selectedIndex).toList());
        assertEquals(1, seenWhenAddReturned.get(), "add returned before its listeners ran");
        Seen s = seen.get(0);
        assertSame(worker[0], s.thread(), "accessSynchronously runs the body on the calling thread");
        assertSame(ui, s.ui());
        assertTrue(s.locked(), "the selection shift fanned out without the session lock");
        assertEquals(5, GridKt._size(list));
        assertEquals(Set.of(2), list.getSelectedItems(), "the Grid follows the shifted selection");
    }

    @Test
    @DisplayName("detached but built in a session: the fan-out runs under that session's lock, with no UI current")
    void detachedListRunsUnderItsSessionLock() {
        DefaultListModel<String> model = abc();
        SJList<String> list = new SJList<>(model);
        list.setSelectedIndex(2);
        List<Seen> seen = recordSelectionFanOut(list);

        runOnWorker(() -> model.remove(0));

        assertEquals(List.of(1), seen.stream().map(Seen::selectedIndex).toList());
        assertNull(seen.get(0).ui(), "no UI holds the tree, so none is made current");
        assertTrue(seen.get(0).locked(), "the list knows its session, so the hop takes its lock");
        assertEquals(2, GridKt._size(list));
        assertEquals(Set.of(1), list.getSelectedItems());
    }
}
