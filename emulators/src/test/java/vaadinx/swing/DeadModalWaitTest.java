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
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A modal parked on the UI thread whose dialog goes away unanswered — a tab close, a session
 * destroy — must stop the listener that opened it, and quietly: R_match_swing_errors case (8).
 * The dialog's peer anchors the park, and a session destroy detaches it for good. (Removing the
 * peer by hand is no death signal: an open Vaadin {@code Dialog} re-attaches itself before the
 * response, and the wait rightly lives on.)
 */
class DeadModalWaitTest extends AbstractKaribuTest {

    @Test
    @DisplayName("an unanswered modal whose peer detaches unwinds past catch (Exception), and reports nothing")
    void deadModalUnwindsQuietly() {
        final List<Throwable> reported = new ArrayList<>();
        VaadinSession.getCurrent().setErrorHandler(e -> reported.add(e.getThrowable()));
        final JDialog modal = new JDialog((JFrame) null, "modal", true);
        final AtomicBoolean ranOn = new AtomicBoolean();
        final AtomicBoolean swallowed = new AtomicBoolean();
        final AtomicBoolean unwound = new AtomicBoolean();

        EHelper.callSwing(() -> {
            try {
                modal.setVisible(true);   // parks
                ranOn.set(true);
            } catch (Exception e) {
                swallowed.set(true);      // the migrated code's catch-all: must not see it
            } finally {
                unwound.set(true);
            }
        });
        assertTrue(modal.getPeer().isAttached(), "the modal is open and its caller parked");

        // the session goes away with the dialog still open
        final String log = capturingStdErr(MockVaadin::tearDown);

        assertTrue(unwound.get(), "the parked listener must end, not stay parked forever");
        assertFalse(ranOn.get(), "the code after setVisible(true) must not run on a fabricated answer");
        assertFalse(swallowed.get(), "a catch (Exception e) must not swallow the dead wait");
        assertEquals(List.of(), reported, "nobody is left to report a dead wait to");
        assertFalse(log.contains(FIBER_FAILED), "a dead wait is no failure to log:\n" + log);
    }

    /**
     * A tab close: the session lives on, and Vaadin removes the closed UI at the end of the
     * request, which detaches it and the dialog with it.
     */
    @Test
    @DisplayName("an unanswered modal whose tab closes ends its listener quietly")
    void tabCloseEndsTheModalQuietly() {
        final List<Throwable> reported = new ArrayList<>();
        final VaadinSession session = VaadinSession.getCurrent();
        session.setErrorHandler(e -> reported.add(e.getThrowable()));
        final JDialog modal = new JDialog((JFrame) null, "modal", true);
        final AtomicBoolean ranOn = new AtomicBoolean();
        final AtomicBoolean unwound = new AtomicBoolean();

        EHelper.callSwing(() -> {
            try {
                modal.setVisible(true);   // parks
                ranOn.set(true);
            } finally {
                unwound.set(true);
            }
        });

        final UI ui = UI.getCurrent();
        final String log = capturingStdErr(() -> {
            ui.close();
            session.removeUI(ui);
            session.getService().runPendingAccessTasks(session);
        });

        assertTrue(unwound.get(), "the parked listener must end, not stay parked forever");
        assertFalse(ranOn.get(), "the code after setVisible(true) must not run on a fabricated answer");
        assertEquals(List.of(), reported, "a dead wait is not an error of the app's");
        assertFalse(log.contains(FIBER_FAILED), "a dead wait is no failure to log:\n" + log);
    }

    /**
     * What vaadin-blocking-dialogs logs for an error escaping a UI fiber that has no session
     * left — the resumed fiber's case in both tests, so it is what a leaked error looks like.
     */
    private static final String FIBER_FAILED = "A UI fiber failed";

    /** Runs {@code body} and returns what it wrote to {@code System.err}, which slf4j-simple logs to. */
    private static String capturingStdErr(Runnable body) {
        final java.io.PrintStream original = System.err;
        final java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        System.setErr(new java.io.PrintStream(captured, true));
        try {
            body.run();
        } finally {
            System.setErr(original);
        }
        return captured.toString();
    }
}
