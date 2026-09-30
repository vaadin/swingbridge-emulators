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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;
import com.github.mvysny.blockingdialogs.UIFibers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link EHelper#callSwing} on a virtual thread that is not one of its own — the
 * {@code spring.threads.virtual.enabled=true} deployment (R_match_swing_errors case (10),
 * D_virtual_request_threads).
 *
 * <p>The point of the case is that {@code Thread.isVirtual()} cannot tell a container's request
 * thread from a {@code callSwing} continuation, so the re-entrancy fast path used to absorb the
 * former silently: no virtual thread spawned, no access-queue drain, no epilogue. These tests pin
 * the three-way split the discriminator now makes — refuse inside a request, stay inline outside
 * one, stay inline for our own continuations.
 *
 * <p>Simulating the container is faithful rather than approximate: a real Tomcat request thread
 * differs from these virtual threads in exactly the two things set up here, a current
 * {@link VaadinRequest} and a virtual {@link Thread}.
 */
public class VirtualRequestThreadTest extends AbstractKaribuTest {

    @Test
    public void aVirtualRequestThreadIsRefused() {
        final VaadinSession session = VaadinSession.getCurrent();
        final UI ui = UI.getCurrent();
        final VaadinRequest request = VaadinRequest.getCurrent();
        final VaadinResponse response = VaadinResponse.getCurrent();
        Assertions.assertNotNull(request, "Karibu is expected to put a request on the test thread");

        final VaadinService service = VaadinService.getCurrent();
        final Throwable thrown = onVirtualThread(() -> {
            service.setCurrentInstances(request, response);
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
            EHelper.callSwing(() -> Assertions.fail("the callback must not run"));
        });

        Assertions.assertInstanceOf(IllegalStateException.class, thrown);
        Assertions.assertTrue(thrown.getMessage().contains("spring.threads.virtual.enabled=false"),
                "the message must name the fix, not the symptom: " + thrown.getMessage());
    }

    /**
     * The migrator's own background virtual thread — an {@code EmulatorContext.wrap}ped
     * {@code newVirtualThreadPerTaskExecutor}, already inside {@link EHelper#runInUIThread}'s
     * {@code accessSynchronously} when a peer write echoes back. No request is current there, and
     * inline dispatch is right: the lock is held and the write has happened.
     */
    @Test
    public void aBackgroundVirtualThreadWithNoRequestRunsInline() {
        final VaadinSession session = VaadinSession.getCurrent();
        final UI ui = UI.getCurrent();
        final AtomicBoolean ran = new AtomicBoolean();

        final Throwable thrown = onVirtualThread(() -> {
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
            Assertions.assertNull(VaadinRequest.getCurrent());
            EHelper.callSwing(() -> ran.set(true));
        });

        Assertions.assertNull(thrown);
        Assertions.assertTrue(ran.get());
    }

    /**
     * Our own continuation keeps the inline path even with a request current — which is the
     * ordinary production shape, since {@code callSwing} is entered from a request thread and the
     * nested envelope is what a cascading fan-out produces. Non-vacuous: dropping the
     * {@code isInUIFiber()} half of the discriminator reddens this.
     */
    @Test
    public void ourOwnContinuationRunsInlineEvenInsideARequest() {
        final VaadinRequest request = VaadinRequest.getCurrent();
        final VaadinResponse response = VaadinResponse.getCurrent();
        final VaadinService service = VaadinService.getCurrent();
        final AtomicBoolean nestedRan = new AtomicBoolean();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        EHelper.callSwing(() -> {
            try {
                Assertions.assertTrue(Thread.currentThread().isVirtual());
                Assertions.assertTrue(UIFibers.isInUIFiber());
                // A continuation does not inherit the carrier's request; put one back, so the
                // test fails if the marker stops being what distinguishes us.
                service.setCurrentInstances(request, response);
                EHelper.callSwing(() -> nestedRan.set(true));
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        Assertions.assertNull(failure.get());
        Assertions.assertTrue(nestedRan.get());
    }

    /** Runs {@code body} on a fresh virtual thread and returns what it threw, or {@code null}. */
    private static Throwable onVirtualThread(Runnable body) {
        final AtomicReference<Throwable> thrown = new AtomicReference<>();
        final Thread thread = Thread.ofVirtual().start(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        return thrown.get();
    }
}
