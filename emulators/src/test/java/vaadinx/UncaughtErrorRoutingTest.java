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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.NotificationsKt;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.server.DefaultErrorHandler;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.swing.JButton;
import vaadinx.swing.JFrame;
import vaadinx.swing.JOptionPane;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parity for what happens when a migrated app's listener throws: Swing's EDT catches
 * inside its pump loop, hands the throwable to the uncaught handler, and keeps
 * dispatching. Our equivalent is {@code EHelper.callSwing} plus the session
 * {@code ErrorHandler}, and these tests pin that the two behave the same — including
 * the two ways they must <em>not</em> behave (no JVM-wide handler is consulted;
 * nothing is shown to the user). See {@code D_uncaught_handler_chain}.
 */
class UncaughtErrorRoutingTest extends AbstractKaribuTest {

    @AfterEach
    void clearJvmWideHandler() {
        // Global process state: a leak turns an unrelated later test into a heisenbug.
        Thread.setDefaultUncaughtExceptionHandler(null);
    }

    /** Installs a collecting session ErrorHandler and returns the list it fills. */
    private static List<Throwable> collectErrors() {
        List<Throwable> seen = new ArrayList<>();
        VaadinSession.getCurrent().setErrorHandler(event -> seen.add(event.getThrowable()));
        return seen;
    }

    private static void showFrameWith(JButton... buttons) {
        JFrame frame = new JFrame("errors");
        for (JButton b : buttons) {
            frame.getContentPane().add(b);
        }
        frame.setVisible(true);
    }

    /**
     * A JFrame's own peer is a Vaadin {@code Dialog}, so "is a dialog showing" has to
     * ask for the option pane itself rather than for the Dialog type.
     */
    private static void assertOneOptionPane() {
        LocatorJ._assertOne(Div.class, spec -> spec.withAttribute("data-swing-class", "JOptionPane"));
    }

    /** @see #assertOneOptionPane() */
    private static void assertNoOptionPane() {
        LocatorJ._assertNone(Div.class, spec -> spec.withAttribute("data-swing-class", "JOptionPane"));
    }

    private static void clickPeer(JButton button) {
        EHelper.callSwing(() -> LocatorJ._click(
                (com.vaadin.flow.component.button.Button) button.getPeer()));
    }

    @Test
    @DisplayName("a throwing listener reaches the ErrorHandler, and the next click still works")
    void throwingListenerIsReportedAndAppSurvives() {
        List<Throwable> seen = collectErrors();
        Counter runs = new Counter();
        JButton b = new JButton("Boom");
        showFrameWith(b);
        b.addActionListener(e -> {
            runs.inc();
            throw new RuntimeException("Hello!");
        });

        clickPeer(b);
        clickPeer(b);

        assertEquals(2, runs.get(), "the EDT keeps dispatching after a throw, as Swing's pump does");
        assertEquals(2, seen.size());
        assertEquals("Hello!", seen.get(0).getMessage());
    }

    @Test
    @DisplayName("nothing is shown to the user — Swing's default shows no dialog either")
    void nothingIsShownToTheUser() {
        collectErrors();
        JButton b = new JButton("Boom");
        showFrameWith(b);
        b.addActionListener(e -> { throw new RuntimeException("Hello!"); });

        clickPeer(b);

        NotificationsKt.expectNoNotifications();
        assertNoOptionPane();
    }

    @Test
    @DisplayName("work done before the throw stays applied, on both layers")
    void partialWorkSurvivesTheThrow() {
        collectErrors();
        JButton b = new JButton("Boom");
        JButton other = new JButton("Other");
        showFrameWith(b, other);
        b.addActionListener(e -> {
            other.setText("changed");
            throw new RuntimeException("Hello!");
        });

        clickPeer(b);

        assertEquals("changed", other.getText());
        assertEquals("changed",
                ((com.vaadin.flow.component.button.Button) other.getPeer()).getText());
    }

    @Test
    @DisplayName("a ported handler can open a modal dialog — it runs on the EDT with a current UI")
    void handlerCanOpenAModalDialog() {
        VaadinSession.getCurrent().setErrorHandler(event ->
                JOptionPane.showMessageDialog(null, "boom: " + event.getThrowable().getMessage()));
        JButton b = new JButton("Boom");
        showFrameWith(b);
        b.addActionListener(e -> { throw new RuntimeException("Hello!"); });

        clickPeer(b);

        assertOneOptionPane();
    }

    @Test
    @DisplayName("a JVM-wide Thread handler is NOT consulted — it is ported, not read back")
    void jvmWideHandlerIsNotConsulted() {
        // The negative half of D_uncaught_handler_chain. Without it, someone later adds
        // the one-line Thread.getDefaultUncaughtExceptionHandler() read back as an
        // obvious improvement, and nothing goes red.
        List<Throwable> jvmWide = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> jvmWide.add(e));
        assertInstanceOf(DefaultErrorHandler.class, VaadinSession.getCurrent().getErrorHandler(),
                "baseline: Karibu installs no session ErrorHandler of its own");

        JButton b = new JButton("Boom");
        showFrameWith(b);
        b.addActionListener(e -> { throw new RuntimeException("Hello!"); });
        clickPeer(b);

        assertTrue(jvmWide.isEmpty(), "the JVM-wide handler must stay untouched");
    }

    @Test
    @DisplayName("with both installed, the session handler fires and the JVM-wide one stays untouched")
    void sessionHandlerFiresAndJvmWideStaysUntouched() {
        List<Throwable> jvmWide = new ArrayList<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> jvmWide.add(e));
        List<Throwable> seen = collectErrors();

        JButton b = new JButton("Boom");
        showFrameWith(b);
        b.addActionListener(e -> { throw new RuntimeException("Hello!"); });
        clickPeer(b);

        assertEquals(1, seen.size());
        assertTrue(jvmWide.isEmpty());
    }

    @Test
    @DisplayName("reportUncaught reaches the handler from a thread holding no session lock")
    void reportUncaughtAcquiresTheLock() throws Exception {
        List<Throwable> seen = collectErrors();
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();

        Thread t = new Thread(() ->
                EHelper.reportUncaught(session, new IllegalStateException("off-thread")));
        t.start();
        t.join();
        // The report is queued via session.access; drain it the way a request boundary would.
        ui.getSession().getService().runPendingAccessTasks(session);

        assertEquals(1, seen.size());
        assertEquals("off-thread", seen.get(0).getMessage());
    }
}
