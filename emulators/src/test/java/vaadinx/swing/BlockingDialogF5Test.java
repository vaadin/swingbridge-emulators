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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AppTab;
import vaadinx.EHelper;
import vaadinx.MockVirtualThreadAwareServlet;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F5 / {@code @PreserveOnRefresh} gate for <b>blocking modal dialogs</b>: a UI fiber
 * ({@link EHelper#callSwing}) parked on a dialog's answer must survive the old UI closing,
 * resume when the answer comes from the new UI, and see the new UI as {@code UI.getCurrent()}.
 *
 * <h2>What makes it hold</h2>
 *
 * The park is anchored to a component that outlives the F5 — the dialog's peer, which Flow
 * teleports onto the new UI — so the wait is not ended by the old UI's detach, and on resume
 * {@code UI.getCurrent()} is the UI the anchor was last attached to. A wait anchored to
 * something the F5 destroys would end instead, and the code after the dialog call would never
 * run.
 *
 * <h2>Machinery-level pins plus a faithful {@code JOptionPane} E2E</h2>
 *
 * The first two tests pin the survival machinery directly (a parked
 * continuation; the {@code awaitModal} UI-rebind). The third is a faithful
 * end-to-end: a real {@code JOptionPane} dialog held open across an F5. Before
 * karibu-testing 2.7.1 this E2E could prove nothing — Karibu's {@code page.reload()}
 * recreated the UI without a <i>browser's</i> overlay lifecycle, so the dialog's
 * buttons resolved to the post-reload UI and it returned {@code YES_OPTION} whether or
 * not the overlay actually teleported. karibu-testing 2.7.1
 * (<a href="https://github.com/mvysny/karibu-testing/issues/207">karibu-testing#207</a>,
 * PR #208) reordered {@code MockPage.reload()} to match production Flow — the new UI
 * is created and registered first, overlays teleport via
 * {@code UIInternals.moveElementsFrom}, the old UI closes last — so the teleport is
 * now Flow's real code path and this test genuinely exercises teleport +
 * continuation-survival + rebind together. (A real browser's overlay <i>disposal</i>
 * when teleport is bypassed remains unmodeled — a negative case SB-Emulators does not
 * depend on.)
 */
public class BlockingDialogF5Test {

    /** Non-preserved landing so {@code MockVaadin.setup}'s default navigation lands somewhere. */
    @Route("")
    public static class DlgLandingView extends Div {
    }

    /**
     * Preserved host so an F5 teleports to a fresh UI (mirrors the Timer/Worker F5 views).
     * Binds the app tab on every attach (initial + F5 rebind) via {@link AppTab#markAppUI},
     * mirroring {@code MainWindowRoute} in production, so the post-park continuation
     * re-resolves the live post-F5 UI through {@code EHelper.singleLiveUI} (now pure —
     * AppTab-or-null, no getUIs() guess) rather than resolving to null.
     */
    @Route("dlg")
    @PreserveOnRefresh
    public static class DlgReloadView extends Div {
        @Override
        protected void onAttach(AttachEvent attachEvent) {
            super.onAttach(attachEvent);
            AppTab.markAppUI(attachEvent.getUI());
        }
    }

    @BeforeEach
    void setup() {
        Routes routes = new Routes(Set.of(DlgLandingView.class, DlgReloadView.class), Set.of(), true);
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(routes));
    }

    @AfterEach
    void teardown() {
        MockVaadin.tearDown();
    }

    /**
     * A {@code callSwing} continuation parked on a result future (exactly how a modal
     * dialog parks) must survive an F5 UI-close and resume when the future later
     * completes on the new UI. The park goes through {@code UIFibers.parkAndAwait}: a
     * bare {@code fut.get()} would hold the session lock, and under Karibu the test
     * thread carrying it would wait forever.
     */
    @Test
    @DisplayName("a parked callSwing continuation survives F5 and resumes on the new UI")
    void parkedContinuationSurvivesF5() throws InterruptedException {
        UI.getCurrent().navigate("dlg");
        UI ui1 = UI.getCurrent();

        CompletableFuture<Integer> fut = new CompletableFuture<>();
        CountDownLatch resumed = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(-99);

        // the @PreserveOnRefresh view survives the F5, so it anchors the wait like a dialog peer
        com.vaadin.flow.component.Component anchor = ui1.getCurrentView();
        // Park the UI fiber on fut, as a modal dialog does.
        EHelper.callSwing(() -> {
            result.set(com.github.mvysny.blockingdialogs.UIFibers.parkAndAwait(anchor, fut));
            resumed.countDown();
        });

        // F5 while the continuation is parked.
        UI.getCurrent().getPage().reload();
        UI ui2 = UI.getCurrent();
        assertNotSame(ui1, ui2, "reload must create a new UI");

        // Deliver the modal result from the live post-F5 UI.
        EHelper.callSwing(() -> fut.complete(7));

        assertTrue(resumed.await(5, TimeUnit.SECONDS),
                "the parked continuation was ended by the F5 UI-close instead of following "
                        + "its anchor onto the new UI");
        assertEquals(7, result.get(),
                "the value delivered after F5 must reach the resumed continuation");
    }

    /**
     * The {@code awaitModal} resume seam: a continuation parked on a modal latch (as
     * {@code Dialog.parkUntilClose} does) must resume with the VT's own
     * {@code UI.getCurrent()} rebound to the <i>live</i> UI — so a chained dialog or any
     * {@code UI.getCurrent()}-reading code after the dialog lands on the post-F5 UI,
     * not the closed old one. A raw {@code latch.await()} would leave {@code UI.getCurrent()}
     * naming the stale pre-park UI (Vaadin's {@code CurrentInstance} is a
     * non-VT-propagating {@code ThreadLocal}).
     */
    @Test
    @DisplayName("awaitModal rebinds UI.getCurrent to the live UI after an F5")
    void awaitModalRebindsCurrentUI() throws InterruptedException {
        UI.getCurrent().navigate("dlg");
        UI ui1 = UI.getCurrent();

        // the @PreserveOnRefresh view survives the F5, so it anchors the wait like a dialog peer
        com.vaadin.flow.component.Component anchor = ui1.getCurrentView();
        java.util.concurrent.CompletableFuture<Void> closed = new java.util.concurrent.CompletableFuture<>();
        CountDownLatch resumed = new CountDownLatch(1);
        AtomicReference<UI> resumedUI = new AtomicReference<>();

        EHelper.callSwing(() -> {
            EHelper.awaitModal(anchor, closed); // parks the UI fiber, like a modal dialog
            resumedUI.set(UI.getCurrent());
            resumed.countDown();
        });

        UI.getCurrent().getPage().reload();
        UI ui2 = UI.getCurrent();
        assertNotSame(ui1, ui2, "reload must create a new UI");

        EHelper.callSwing(() -> closed.complete(null)); // release the modal from the live UI

        assertTrue(resumed.await(5, TimeUnit.SECONDS), "awaitModal continuation must resume after F5");
        assertSame(ui2, resumedUI.get(),
                "awaitModal rebinds the VT's UI.getCurrent() to the live post-F5 UI, "
                        + "not the closed old one");
    }

    /**
     * Faithful end-to-end: a real {@code JOptionPane} dialog open across an F5,
     * clicked afterwards, whose post-dialog code and a chained dialog land on
     * the live UI. Two mechanisms cooperate: (1) Karibu (≥ 2.7.1,
     * <a href="https://github.com/mvysny/karibu-testing/issues/207">karibu-testing#207</a>)
     * drives Flow's <i>real</i> {@code @PreserveOnRefresh} navigation, which teleports the
     * UI-attached Dialog overlay off the old UI onto the new one via
     * {@code UIInternals.moveElementsFrom}; (2) SB-Emulators' session-scoped executor survives
     * the F5 and {@code awaitModal} rebinds the resumed VT's {@code UI.getCurrent()}, so
     * {@code postDialogUI == ui2} / {@code chainedDialogUI == ui2} hold.
     *
     * <p>Enabled once #207 landed: the overlay teleport is now Flow's real code
     * path exercised through Karibu, not a Karibu fake, so this is a genuine
     * integration test of teleport + continuation-survival + rebind. (A real
     * browser's overlay <i>disposal</i> when teleport is bypassed remains unmodeled —
     * a negative case SB-Emulators does not depend on.)
     */
    @Test
    @DisplayName("a modal dialog open across F5 resumes, and post-dialog plus chained dialog "
            + "land on the new UI")
    void modalDialogAcrossF5ResumesOnTheNewUI() throws InterruptedException {
        UI.getCurrent().navigate("dlg");
        UI ui1 = UI.getCurrent();

        AtomicInteger result = new AtomicInteger(-99);
        AtomicReference<UI> postDialogUI = new AtomicReference<>();
        AtomicReference<UI> chainedDialogUI = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        EHelper.callSwing(() -> {
            result.set(JOptionPane.showConfirmDialog(
                    null, "Save changes?", "Confirm", JOptionPane.YES_NO_OPTION));
            postDialogUI.set(UI.getCurrent());
            JOptionPane.showMessageDialog(null, "Saved", "Info", JOptionPane.INFORMATION_MESSAGE);
            chainedDialogUI.set(UI.getCurrent());
            finished.countDown();
        });

        UI.getCurrent().getPage().reload();
        UI ui2 = UI.getCurrent();
        assertNotSame(ui1, ui2, "reload must create a new UI");

        Button yes = LocatorJ._get(Button.class, spec -> spec.withText("Yes"));
        EHelper.callSwing(() -> LocatorJ._click(yes));
        Button ok = LocatorJ._get(Button.class, spec -> spec.withText("OK"));
        EHelper.callSwing(() -> LocatorJ._click(ok));

        assertTrue(finished.await(5, TimeUnit.SECONDS), "dialog continuation did not survive the F5");
        assertEquals(JOptionPane.YES_OPTION, result.get());
        assertSame(ui2, postDialogUI.get(), "post-dialog code must run on the live post-F5 UI");
        assertSame(ui2, chainedDialogUI.get(), "a chained dialog must land on the live post-F5 UI");
    }
}
