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
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.MockVirtualThreadAwareServlet;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SwingWorker tests that need the test thread to be off-EDT —
 * untimed {@code get()}, cancel-of-running surfacing CancellationException,
 * doInBackground exception via {@code get()}. By NOT setting up Karibu in a
 * {@code @BeforeEach}, the test method itself runs as a background thread:
 * {@code UI.getCurrent()} is null on the test thread, which is the actual
 * production shape for code legitimately calling {@code worker.get()}.
 *
 * <p>Each test sets up Karibu inline, builds the worker inside a
 * {@link EHelper#callSwing} block (so the worker constructor's UI requirement
 * is satisfied), then clears the test thread's UI/Session ThreadLocals
 * so subsequent {@code w.get()} / {@code w.cancel()} calls on the test thread see
 * the off-EDT code path.
 */
class SwingWorkerOffEdtTest {

    @AfterEach
    void teardown() {
        try {
            MockVaadin.tearDown();
        } catch (Throwable ignored) {
            // tearDown after we've cleared UI may fizzle on some paths;
            // safe to ignore — JVM exits after this test runs anyway.
        }
    }

    @Test
    @DisplayName("get blocks off EDT and returns result")
    void getBlocksOffEdtAndReturnsResult() throws Exception {
        SwingWorker<Integer, Void> w = withKaribuThenClear(() -> {
            SwingWorker<Integer, Void> worker = new SwingWorker<>() {
                @Override
                protected Integer doInBackground() {
                    return 42;
                }
            };
            worker.execute();
            return worker;
        });
        assertEquals(42, w.get());
    }

    @Test
    @DisplayName("cancel before execute finalises with CancellationException")
    void cancelBeforeExecuteFinalisesCancelled() {
        // Cancel-before-execute is the deterministic cancel test: the
        // worker's run() detects cancelled=true at the top and skips
        // the work, the finally-block counts down doneLatch with the
        // cancelled flag set, and get() resolves to
        // CancellationException. No reliance on VT-interrupt-wake
        // timing (which has a multi-second scheduler delay on some
        // JVM/Loom builds, making cancel-while-running tests flaky).
        SwingWorker<String, Void> w = withKaribuThenClear(() -> {
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    throw new IllegalStateException(
                            "doInBackground should not run on a cancel-before-execute worker");
                }
            };
            worker.cancel(true);
            worker.execute();
            return worker;
        });
        assertTrue(w.isCancelled());
        assertThrows(CancellationException.class, () -> w.get(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("doInBackground exception surfaces as ExecutionException via get")
    void doInBackgroundExceptionSurfacesViaGet() {
        SwingWorker<String, Void> w = withKaribuThenClear(() -> {
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override
                protected String doInBackground() {
                    throw new IllegalStateException("kaboom");
                }
            };
            worker.execute();
            return worker;
        });
        ExecutionException ex = assertThrows(ExecutionException.class, w::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("kaboom", ex.getCause().getMessage());
    }

    /**
     * Sets up Karibu, runs {@code block} inside {@link EHelper#callSwing} (so the
     * worker can be constructed under a UI), then clears the test
     * thread's UI/Session so subsequent calls on the test thread run
     * as off-EDT. The worker continues to run on its own VT against
     * the still-attached UI — only the test thread's view of "current
     * UI" changes.
     */
    private static <T> T withKaribuThenClear(Supplier<T> block) {
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(new Routes()));
        AtomicReference<T> ref = new AtomicReference<>();
        EHelper.callSwing(() -> ref.set(block.get()));
        UI.setCurrent(null);
        VaadinSession.setCurrent(null);
        return ref.get();
    }
}
