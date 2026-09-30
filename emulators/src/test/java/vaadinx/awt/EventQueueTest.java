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

package vaadinx.awt;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirror of {@code vaadinx.swing.SwingUtilitiesTest} for the {@link EventQueue}
 * narrow port. EventQueue's three EDT statics delegate to SwingUtilities;
 * these tests assert the delegation actually wires the contract through.
 *
 * <p>Off-EDT cases live in {@link EventQueueOffEdtTest}, which doesn't set up
 * Karibu in {@code @BeforeEach}.
 */
class EventQueueTest extends AbstractKaribuTest {

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

    @Test
    @DisplayName("isDispatchThread returns true under callSwing")
    void isDispatchThreadReturnsTrueUnderCallSwing() {
        AtomicBoolean onEdt = new AtomicBoolean(false);
        EHelper.callSwing(() -> onEdt.set(EventQueue.isDispatchThread()));
        assertTrue(onEdt.get());
    }

    @Test
    @DisplayName("invokeLater runs the runnable on the EDT")
    void invokeLaterRunsTheRunnableOnTheEdt() throws InterruptedException {
        AtomicBoolean onEdt = new AtomicBoolean(false);
        CountDownLatch ran = new CountDownLatch(1);
        EHelper.callSwing(() -> EventQueue.invokeLater(() -> {
            onEdt.set(EventQueue.isDispatchThread());
            ran.countDown();
        }));
        assertTrue(awaitWithDrain(ran));
        assertTrue(onEdt.get());
    }

    @Test
    @DisplayName("invokeAndWait from EDT throws ISE with deadlock message")
    void invokeAndWaitFromEdtThrowsIseWithDeadlockMessage() {
        EHelper.callSwing(() -> {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> EventQueue.invokeAndWait(() -> { /* never runs */ }));
            assertTrue(
                    ex.getMessage().contains("would deadlock under Swing"),
                    "Expected deadlock-pointer message; got: " + ex.getMessage());
        });
    }
}
