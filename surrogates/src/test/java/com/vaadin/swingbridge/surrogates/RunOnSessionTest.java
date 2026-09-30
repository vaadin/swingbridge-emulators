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
import com.vaadin.flow.component.UIDetachedException;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/** {@link SHelper#runOnSession}'s failure paths: nothing is swallowed silently, and the body never runs twice. */
class RunOnSessionTest extends AbstractKaribuTest {

    /** Runs {@code body} on a plain thread with the Karibu lock released; returns what it threw. */
    private static Throwable onWorker(Runnable body) {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                thrown.set(e);
            }
        }, "run-on-session");
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
        if (t.isAlive()) fail("the worker did not finish");
        return thrown.get();
    }

    @Test
    @DisplayName("a UIDetachedException thrown by the body propagates, and the body runs once")
    void bodyThrowingUiDetachedRunsOnce() {
        Div peer = new Div();
        UI.getCurrent().add(peer);
        VaadinSession session = VaadinSession.getCurrent();
        AtomicInteger runs = new AtomicInteger();

        Throwable thrown = onWorker(() -> SHelper.runOnSession(session, peer, () -> {
            runs.incrementAndGet();
            throw new UIDetachedException();
        }));

        assertInstanceOf(UIDetachedException.class, thrown, "the body's own exception must reach the caller");
        assertEquals(1, runs.get(), "a failure inside the body must not be mistaken for a closed UI and re-run");
    }

    @Test
    @DisplayName("a hop that fails before the body starts runs the body once, inline, and does not throw")
    void hopFailureFallsBackOnce() {
        Div peer = new Div();
        // A session nobody ever gave a lock: accessSynchronously fails before the body runs.
        VaadinSession lockless = new VaadinSession(null);
        AtomicInteger runs = new AtomicInteger();

        Throwable thrown = onWorker(() -> SHelper.runOnSession(lockless, peer, runs::incrementAndGet));

        assertNull(thrown, "the fallback is logged at WARN, not thrown");
        assertEquals(1, runs.get());
    }
}
