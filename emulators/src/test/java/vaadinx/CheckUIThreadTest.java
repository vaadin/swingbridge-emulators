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
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.swing.JButton;

import com.vaadin.swingbridge.surrogates.SJLabel;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * {@link EHelper#checkUIThread()} as reached through {@link vaadinx.awt.Component#getPeer()}.
 *
 * <p>Asserts on the once-per-JVM latch rather than on log output: the test classpath
 * binds slf4j-simple, which offers no appender to capture, and the latch is the
 * mechanism under test anyway — whether the WARN reaches a log file is slf4j's
 * business, whether it is armed exactly once is ours.
 */
class CheckUIThreadTest extends AbstractKaribuTest {

    @BeforeEach
    @AfterEach
    void rearmLatch() {
        EHelper.resetUIThreadWarning();
    }

    /**
     * Touches a peer on a bare thread — no current UI, no session, no lock, which is
     * what a {@code SwingWorker.doInBackground()} body sees.
     */
    private static void touchPeerOffUiThread() {
        JButton button = new JButton("probe");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                button.getPeer();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "off-ui-probe");
        t.setDaemon(true);
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted waiting for the probe thread");
        }
        if (t.isAlive()) fail("probe thread did not finish — getPeer() blocked");
        if (failure.get() != null) fail("getPeer() threw off the UI thread", failure.get());
    }

    @Test
    @DisplayName("a peer reached on the UI thread reports nothing")
    void uiThreadIsSilent() {
        new JButton("ok").getPeer();
        assertFalse(EHelper.uiThreadWarned(), "getPeer() on the UI thread must not arm the warning");
    }

    @Test
    @DisplayName("a peer reached off the UI thread reports once")
    void offUiThreadReports() {
        touchPeerOffUiThread();
        assertTrue(EHelper.uiThreadWarned(), "getPeer() with no current UI must arm the warning");
    }

    @Test
    @DisplayName("the report is gated by the latch, so it fires once per JVM")
    void reportIsLatched() {
        touchPeerOffUiThread();
        assertTrue(EHelper.uiThreadWarned());

        // Latch still set: a second off-thread access changes nothing, which is what
        // makes the paragraph-long WARN affordable on a path every emulator takes.
        touchPeerOffUiThread();
        assertTrue(EHelper.uiThreadWarned());

        EHelper.resetUIThreadWarning();
        assertFalse(EHelper.uiThreadWarned());
        touchPeerOffUiThread();
        assertTrue(EHelper.uiThreadWarned(), "a re-armed latch must report again");
    }

    /**
     * The teardown shape: session current and locked, no current UI. Run on the test
     * thread because that is the only thread here that holds the session lock —
     * spawning one would deadlock against it, and an unlocked thread cannot read the
     * shutdown flag at all.
     */
    private static void touchPeerWithLockedSessionAndNoUi() {
        // Built while the UI is current, so the reach below is a peer access, not a build.
        JButton button = new JButton("probe");
        UI ui = UI.getCurrent();
        try {
            UI.setCurrent(null);
            button.getPeer();
        } finally {
            UI.setCurrent(ui);
        }
    }

    @Test
    @DisplayName("shutdown teardown reaches peers without a UI and is not a mistake")
    void shutdownIsSilent() {
        EHelper.markShuttingDown(VaadinSession.getCurrent());
        touchPeerWithLockedSessionAndNoUi();
        assertFalse(EHelper.uiThreadWarned(),
                "a UI-less teardown thread is expected during shutdown (R_callswing_envelope)");
    }

    @Test
    @DisplayName("outside shutdown, a locked session with no UI still reports")
    void lockedSessionWithoutShutdownReports() {
        touchPeerWithLockedSessionAndNoUi();
        assertTrue(EHelper.uiThreadWarned(),
                "the carve-out is the shutdown flag, not merely the absence of a UI");
    }

    // ─── EHelper.runInUIThread ───────────────────────────────────────────

    /**
     * Runs {@code body} on a background thread carrying this session's
     * {@link EmulatorContext} — a {@code SwingWorker.doInBackground()} in miniature —
     * and blocks until it finishes.
     *
     * <p>Releases the session lock around the join, because Karibu holds it on the test
     * thread for the whole test and {@link EHelper#runInUIThread} blocks acquiring it.
     * Without the release the probe waits on the test thread and the test waits on the
     * probe; that deadlock is the very property under test, seen from the wrong side.
     */
    private static void onBackgroundThreadWithContext(Runnable body) {
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        EmulatorContext ctx = EmulatorContext.get();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "hop-probe");
        t.setDaemon(true);
        t.start();

        session.unlock();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted waiting for the probe thread");
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("probe thread did not finish within 10s — the hop did not complete");
        if (failure.get() != null) fail("background body failed", failure.get());
    }

    @Test
    @DisplayName("on the UI thread the body runs inline")
    void runsInlineOnTheUiThread() {
        UI here = UI.getCurrent();
        AtomicReference<UI> seen = new AtomicReference<>();
        EHelper.runInUIThread(false, () -> seen.set(UI.getCurrent()));
        assertSame(here, seen.get(), "the body must run on this very thread, under this UI");
    }

    @Test
    @DisplayName("from a background thread the body runs under the live UI, before the call returns")
    void hopsToTheLiveUiSynchronously() {
        AtomicReference<UI> seenUi = new AtomicReference<>();
        AtomicBoolean ranBeforeReturn = new AtomicBoolean();

        onBackgroundThreadWithContext(() -> {
            AtomicBoolean ran = new AtomicBoolean();
            EHelper.runInUIThread(false, () -> {
                seenUi.set(UI.getCurrent());
                ran.set(true);
            });
            // The whole point of choosing synchronous: by the time runInUIThread
            // returns, the body has already run. Under UI.access() this would be false.
            ranBeforeReturn.set(ran.get());
        });

        assertNotNull(seenUi.get(), "the body must see a current UI");
        assertTrue(ranBeforeReturn.get(), "runInUIThread must not return before the body has run");
    }

    @Test
    @DisplayName("with no EmulatorContext on the thread the write throws, naming the wrap")
    void noContextThrowsNamingTheFix() {
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                // No EmulatorContext: a hand-rolled thread in migrated code.
                EHelper.runInUIThread(false, () -> ran.set(true));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "no-context-probe");
        t.setDaemon(true);
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
        assertInstanceOf(IllegalStateException.class, failure.get(),
                "no context is a migrator configuration error, not a soft gap (D_no_context_throws)");
        // The whole value of the throw is that it names the fix, which is at the submit
        // site rather than here — a bare "no UI" would send the reader to the wrong file.
        assertTrue(failure.get().getMessage().contains("EmulatorContext.wrap(executor)"),
                "the message must name the one-line fix; was: " + failure.get().getMessage());
        assertFalse(ran.get(), "with no UI to reach there is nothing to run");
    }

    @Test
    @DisplayName("through a wrapped pool the throw names the submit site, not the generic advice")
    void wrappedPoolThrowNamesTheSubmitSite() throws Exception {
        // The case the generic message gets wrong: the migrator DID wrap their pool, and the
        // defect is that the submit itself ran on a context-less thread (D_submit_site).
        java.util.concurrent.ExecutorService pool =
                EmulatorContext.wrap(java.util.concurrent.Executors.newSingleThreadExecutor());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            Thread submitter = new Thread(() -> {
                try {
                    pool.submit(() -> EHelper.runInUIThread(false, () -> {})).get();
                } catch (Exception e) {
                    failure.set(e.getCause() != null ? e.getCause() : e);
                }
            }, "contextless-submitter");
            submitter.setDaemon(true);
            submitter.start();
            submitter.join(10_000);
        } finally {
            pool.shutdownNow();
        }

        assertInstanceOf(IllegalStateException.class, failure.get());
        String message = failure.get().getMessage();
        assertTrue(message.contains("SUBMITTED"),
                "the wrapped-pool message must point at the submit, not repeat the wrap advice; was: "
                        + message);
        assertTrue(message.contains(CheckUIThreadTest.class.getName()),
                "the message must name the submitting frames; was: " + message);
    }

    @Test
    @DisplayName("a closed tab throws for a blocking caller and is still dropped for a write")
    void noLiveUiThrowsOnlyWhenTheCallerWillBlock() {
        // The tab is gone: the AppTab record goes stale, so singleLiveUI() is null while
        // the session itself is still alive and lockable — the exact window the two legs
        // disagree about.
        UI.getCurrent().close();

        AtomicBoolean writeRan = new AtomicBoolean();
        AtomicReference<Throwable> blockingFailure = new AtomicReference<>();
        onBackgroundThreadWithContext(() -> {
            // An ordinary write keeps R_decline_effect_only's bargain: the effect is lost,
            // nothing is thrown, and the rest of the worker — the saveToDatabase() that made
            // the unconditional throw lose data — still runs.
            EHelper.runInUIThread(false, () -> writeRan.set(true));
            blockingFailure.set(assertThrows(BrowserSessionClosedError.class,
                    () -> EHelper.runInUIThread(true, () -> fail("the body must not run"))));
        });

        assertFalse(writeRan.get(), "with no UI to reach there is nothing to run");
        assertTrue(blockingFailure.get().getMessage().contains("browser tab is gone"),
                "the message must name the cause; was: " + blockingFailure.get().getMessage());
    }

    @Test
    @DisplayName("JLabel.setIcon from a background thread keeps the state and lands the effect")
    void setIconFromBackgroundThreadLandsOnThePeer() {
        vaadinx.swing.JLabel label = new vaadinx.swing.JLabel();
        vaadinx.swing.ImageIcon icon =
                new vaadinx.swing.ImageIcon(new java.awt.image.BufferedImage(8, 8, TYPE_INT_ARGB));

        // The measured inventory case: an ImageIcon set from doInBackground(), which
        // becomes a Vaadin Image whose ctor resolves a StreamResource URI and so needs
        // a current UI. Before runInUIThread this threw IllegalStateException.
        onBackgroundThreadWithContext(() -> label.setIcon(icon));

        assertSame(icon, label.getIcon(), "Swing-side state is set on the calling thread");
        assertNotNull(((SJLabel) label.getPeer()).getIcon(), "the icon must have reached the peer");
    }
}
