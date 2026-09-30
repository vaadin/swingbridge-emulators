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

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A text area's {@link Document} appended to from a background thread, the log-console shape
 * (SD_background_model_hop) — and the case the JDK itself calls safe, since
 * {@code AbstractDocument.insertString} is documented as thread safe. The peer write lands on
 * the area's UI thread when it has one and runs under its session's lock when it does not —
 * synchronously either way, before {@code insertString} returns.
 *
 * <p>The observer is the peer's own value-change listener, since the Document's listeners run
 * on the mutating thread by the JDK's contract and are not the surrogate's to move.
 *
 * <p>Karibu's test thread holds the session lock for the whole test, so {@link #runOnWorker}
 * releases it around the join; the worker needs it to hop.
 */
class SJTextAreaBackgroundModelTest extends AbstractKaribuTest {

    /** What one value-change call observed about the thread it ran on. */
    private record Seen(String value, Thread thread, UI ui, boolean locked) {}

    private static void runOnWorker(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "log-writer");
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

    private static List<Seen> recordPeerWrites(SJTextArea area) {
        List<Seen> seen = new CopyOnWriteArrayList<>();
        area.addValueChangeListener(e -> seen.add(new Seen(
                e.getValue(), Thread.currentThread(), UI.getCurrent(), sessionLocked())));
        return seen;
    }

    private static boolean sessionLocked() {
        VaadinSession s = VaadinSession.getCurrent();
        return s != null && s.hasLock();
    }

    private static void append(Document doc, String line) {
        try {
            doc.insertString(doc.getLength(), line, null);
        } catch (BadLocationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    @DisplayName("attached: each worker append reaches the peer on the UI thread, before insertString returns")
    void attachedAreaHopsToItsUI() {
        SJTextArea area = new SJTextArea();
        UI ui = UI.getCurrent();
        ui.add(area);
        List<Seen> seen = recordPeerWrites(area);
        AtomicReference<Integer> seenWhenAppendReturned = new AtomicReference<>();
        Thread[] worker = new Thread[1];

        runOnWorker(() -> {
            worker[0] = Thread.currentThread();
            append(area.getDocument(), "one\n");
            seenWhenAppendReturned.set(seen.size());
            append(area.getDocument(), "two\n");
        });

        assertEquals(List.of("one\n", "one\ntwo\n"), seen.stream().map(Seen::value).toList());
        assertEquals(1, seenWhenAppendReturned.get(), "insertString returned before the peer write");
        for (Seen s : seen) {
            assertSame(worker[0], s.thread(), "accessSynchronously runs the body on the calling thread");
            assertSame(ui, s.ui());
            assertTrue(s.locked(), "the write of " + s.value().strip() + " ran without the session lock");
        }
        assertEquals("one\ntwo\n", area.getValue());
    }

    @Test
    @DisplayName("detached but built in a session: the peer write runs under that session's lock, with no UI current")
    void detachedAreaRunsUnderItsSessionLock() {
        SJTextArea area = new SJTextArea();
        List<Seen> seen = recordPeerWrites(area);

        runOnWorker(() -> append(area.getDocument(), "one\n"));

        assertEquals(List.of("one\n"), seen.stream().map(Seen::value).toList());
        assertNull(seen.get(0).ui(), "no UI holds the tree, so none is made current");
        assertTrue(seen.get(0).locked(), "the area knows its session, so the hop takes its lock");
        assertEquals("one\n", area.getValue());
    }
}
