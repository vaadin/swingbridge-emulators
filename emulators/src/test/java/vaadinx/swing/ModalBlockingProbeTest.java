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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Vaadin modality facts that D_modality_blocking rests on: while a
 * modal dialog is parked, Vaadin's server-side modality (the active-modal
 * component + inert curtain) plays {@code Dialog.blockWindows} /
 * {@code updateChildrenBlocking}'s role, so SB-Emulators re-declined porting AWT's
 * blocking bookkeeping. If one of these assertions breaks on a Vaadin upgrade,
 * that decision's ground moved.
 *
 * <p>The second test covers the one case the curtain gets wrong on its own —
 * a modal's own owned hierarchy, which the desktop keeps interactive — and so
 * pins D_owned_window_attach's attach rule from the emulator side.
 *
 * <p>{@code StateNode.isInert()} is unreliable under Karibu — {@code InertData}
 * resolves during UIDL change collection, which MockVaadin never runs — so the
 * assertions read the production-semantics signals instead: the UI's
 * active-modal pointer, and the element attachment point (a body-sibling of the
 * active modal is curtained in production; a descendant of it is interactive).
 */
class ModalBlockingProbeTest extends AbstractKaribuTest {

    private static com.vaadin.flow.component.Component activeModal() {
        com.github.mvysny.kaributesting.v10.MockVaadin.clientRoundtrip();
        return com.vaadin.flow.component.UI.getCurrent().getInternals().getActiveModalComponent();
    }

    private static boolean underActiveModal(vaadinx.awt.Component c) {
        com.vaadin.flow.component.Component modal = activeModal();
        if (modal == null) return false;
        for (com.vaadin.flow.dom.Element e = c.getPeer().getElement(); e != null; e = e.getParent()) {
            if (e.equals(modal.getElement())) return true;
        }
        return false;
    }

    @Test
    @DisplayName("parked modal is the UI's active modal; other windows sit outside it (curtained)")
    void parkedModalCurtainsOtherWindows() {
        JFrame frame = new JFrame("main");
        frame.getContentPane().add(new JButton("main-btn"));
        frame.setVisible(true);

        JDialog modal = new JDialog(frame, "modal", true);
        EHelper.callSwing(() -> modal.setVisible(true));   // parks on the VT

        assertSame(modal.getPeer(), activeModal(),
                "the parked modal dialog's peer is the UI's active modal component");
        assertFalse(underActiveModal(frame),
                "pre-existing frame is outside the modal → inert in production, "
                        + "matching desktop APPLICATION_MODAL blocking");

        // Windows opened while the modal is parked also land outside it —
        // matching desktop blocking for frame-owned/ownerless windows. A
        // modeless dialog OWNED BY the modal is the one that must not, and
        // has its own test below.
        JDialog second = new JDialog(frame, "second", false);
        EHelper.callSwing(() -> second.setVisible(true));
        assertTrue(second.isVisible());
        assertFalse(underActiveModal(second));

        // Nested modal: becomes the new active modal (Vaadin modality stack)…
        JDialog nested = new JDialog(modal, "nested", true);
        EHelper.callSwing(() -> nested.setVisible(true));
        assertSame(nested.getPeer(), activeModal(),
                "a nested modal becomes the active modal — cascading JOptionPane shape");

        // …and disposing it pops the stack back to the outer modal.
        EHelper.callSwing(nested::dispose);
        assertSame(modal.getPeer(), activeModal(),
                "disposing the nested modal restores the outer as active modal");

        EHelper.callSwing(modal::dispose);
        assertSame(null, activeModal(), "disposing the last modal clears the curtain");
    }

    @Test
    @DisplayName("a modal closed by its own windowOpened listener does not park")
    void closedDuringShowDoesNotPark() {
        JDialog modal = new JDialog(new JFrame("main"), "modal", true);
        modal.addWindowListener(new vaadinx.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(vaadinx.awt.event.WindowEvent e) {
                modal.dispose();
            }
        });
        AtomicBoolean returned = new AtomicBoolean();
        EHelper.callSwing(() -> {
            modal.setVisible(true);
            returned.set(true);
        });
        assertTrue(returned.get(), "a close fired inside the show must end the modal wait");
    }

    @Test
    @DisplayName("a modeless dialog owned by the parked modal stays inside the curtain")
    void modalKeepsItsOwnedHierarchyInteractive() {
        JFrame frame = new JFrame("main");
        frame.setVisible(true);

        JDialog modal = new JDialog(frame, "modal", true);
        EHelper.callSwing(() -> modal.setVisible(true));

        JDialog ownedByModal = new JDialog(modal, "owned-by-modal", false);
        EHelper.callSwing(() -> ownedByModal.setVisible(true));
        assertTrue(underActiveModal(ownedByModal),
                "the desktop keeps a modal's owned hierarchy interactive, so the owned "
                        + "window attaches under the active modal rather than beside it");

        // A nested modal demotes the outer one, and AWT's application-modal
        // nested dialog blocks the outer's owned chain too — so a window owned
        // by the demoted outer modal opens outside the curtain, not inside it.
        JDialog nested = new JDialog(modal, "nested", true);
        EHelper.callSwing(() -> nested.setVisible(true));
        assertSame(nested.getPeer(), activeModal());

        JDialog ownedByDemoted = new JDialog(modal, "owned-by-demoted", false);
        EHelper.callSwing(() -> ownedByDemoted.setVisible(true));
        assertFalse(underActiveModal(ownedByDemoted));
    }
}
