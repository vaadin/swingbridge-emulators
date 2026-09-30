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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.awt.event.WindowAdapter;
import vaadinx.awt.event.WindowEvent;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JDialog;
import vaadinx.swing.JLabel;
import vaadinx.swing.JTextField;

import javax.swing.WindowConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * EditFormView (CRUD edit-form host) WARN inventory exit gate.
 * Both tests fail if any {@code EHelper.onUnimplemented} /
 * {@code onUnsupportedPeerShape} fires.
 *
 * <ol>
 *   <li>{@link #inventory_modeless_user_path} — drives the route end-to-end
 *       in the modeless variant: click "Edit (modeless)" → JDialog opens
 *       → click OK in the dialog. Modeless avoids the modal-blocking
 *       VT park (which Karibu's micro-test loop can't easily satisfy)
 *       while still exercising the full JDialog show / form / dispose
 *       cycle.</li>
 *   <li>{@link #inventory_jdialog_api_surface} — micro-driver over the
 *       JDialog surface (Dialog + JDialog): owner ctors, modal /
 *       modalityType round-trip, defaultCloseOperation transitions,
 *       window listener fan-out, content-pane routing, dispose. Locks
 *       in zero-WARN coverage for the SJDialog landing.</li>
 * </ol>
 */
class EditFormWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_modeless_user_path() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Edit form");
        dump("Step 0b (EditFormPanel swap)", warnings);

        // Click "Edit (modeless, non-blocking)" — opens JDialog without
        // parking. Karibu's _click drives isFromClient=true.
        Button editModeless = LocatorJ._get(Button.class,
                spec -> spec.withText("Edit (modeless, non-blocking)"));
        LocatorJ._click(editModeless);
        dump("Step 1 (open modeless JDialog)", warnings);

        // Click the dialog's OK button — disposes the dialog, fires
        // windowClosed → status updates. Locate by the underlying Vaadin
        // Button peer (Karibu's LocatorJ takes Vaadin Component subclasses,
        // and JButton's peer is a Vaadin Button).
        com.vaadin.flow.component.button.Button ok =
                LocatorJ._get(com.vaadin.flow.component.button.Button.class,
                        spec -> spec.withText("OK"));
        LocatorJ._click(ok);
        dump("Step 2 (OK dispose path)", warnings);

        WarnDump.println();
        WarnDump.println("=== edit-form modeless user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_jdialog_api_surface() {
        // Micro-driver over the SJDialog landing surface. Mirrors
        // DialogsWarnInventoryTest.inventory_jframe_api_surface but for
        // JDialog: owner ctors, modal / modalityType round-trip,
        // defaultCloseOperation, window listeners, dispose.

        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;

        // -- Fixture: a no-arg JDialog --
        JDialog dialog = new JDialog();
        dump("SJDialog  new JDialog()", warnings);

        // -- Title round-trip --
        dialog.setTitle("t1");
        dialog.getTitle();
        dialog.setTitle("t2");
        dialog.setTitle(null);
        dialog.setTitle("final");
        dump("SJDialog  title round-trip", warnings);

        // -- Modal / modality type round-trip --
        dialog.setModal(true);
        dialog.isModal();
        dialog.setModal(false);
        dialog.setModalityType(vaadinx.awt.Dialog.ModalityType.DOCUMENT_MODAL);
        dialog.setModalityType(vaadinx.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setModalityType(vaadinx.awt.Dialog.ModalityType.MODELESS);
        dialog.getModalityType();
        dump("SJDialog  modal / modalityType round-trip", warnings);

        // -- Window listener registration + accessor --
        WindowAdapter wa = new WindowAdapter() {};
        dialog.addWindowListener(wa);
        dialog.getWindowListeners();
        dialog.removeWindowListener(wa);
        dump("SJDialog  WindowListener add/get/remove", warnings);

        // -- Lifecycle: setVisible(true) on a modeless dialog --
        RecordingWindowListener rec = new RecordingWindowListener();
        dialog.addWindowListener(rec);
        dialog.setVisible(true);
        dump("SJDialog  setVisible(true) modeless — attach + WINDOW_OPENED", warnings);

        // -- defaultCloseOperation: three valid values (no EXIT_ON_CLOSE
        //    on JDialog — JDK rejects it at set-time). --
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        dialog.getDefaultCloseOperation();
        dump("SJDialog  setDefaultCloseOperation full round-trip", warnings);

        // -- Hide / re-show --
        dialog.setVisible(false);
        dialog.setVisible(true);
        dump("SJDialog  hide / re-show", warnings);

        // -- Content pane routing --
        JTextField field = new JTextField("input", 20);
        JCheckBox check = new JCheckBox("check");
        JLabel label = new JLabel("label");
        dialog.add(field);
        dialog.add(check);
        dialog.add(label);
        dialog.getContentPane();
        dialog.remove(field);
        dump("SJDialog  contentPane routing (add/remove/getContentPane)", warnings);

        // -- Owner-taking ctor variations --
        vaadinx.swing.JFrame ownerFrame = new vaadinx.swing.JFrame();
        new JDialog(ownerFrame);
        new JDialog(ownerFrame, true);
        new JDialog(ownerFrame, "title");
        new JDialog(ownerFrame, "title", true);
        new JDialog(ownerFrame, vaadinx.awt.Dialog.ModalityType.APPLICATION_MODAL);
        new JDialog(ownerFrame, "title", vaadinx.awt.Dialog.ModalityType.DOCUMENT_MODAL);
        dump("SJDialog  owner-taking ctor variations", warnings);

        // -- dispose fires WINDOW_CLOSED --
        dialog.dispose();
        dump("SJDialog  dispose() — WINDOW_CLOSED + detach", warnings);

        // -- Resizable / undecorated defaults --
        dialog.isResizable();
        dialog.setResizable(true);
        dialog.setResizable(false);
        dialog.isUndecorated();
        dialog.setUndecorated(false);
        dump("SJDialog  resizable/undecorated defaults", warnings);

        WarnDump.println();
        WarnDump.println("=== SJDialog API-surface WARN total: " + warnings.size() + " ===");
    }

    private static class RecordingWindowListener extends WindowAdapter {
        final List<String> events = new ArrayList<>();
        @Override public void windowOpened(WindowEvent e) { events.add("opened"); }
        @Override public void windowClosing(WindowEvent e) { events.add("closing"); }
        @Override public void windowClosed(WindowEvent e) { events.add("closed"); }
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the EditForm exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
