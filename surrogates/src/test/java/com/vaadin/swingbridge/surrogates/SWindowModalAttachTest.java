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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where {@link SWindow#setVisible(boolean)} attaches a window
 * (D_surrogate_attach / D_reparent_each_show), from the surrogate side —
 * {@code ModalBlockingProbeTest} covers the same seam through the emulators.
 *
 * <p>Element parentage is what these assertions read, because it is what
 * decides the outcome: Flow's inert curtain inherits down the StateNode parent
 * chain, so a window under the active modal is live and a sibling of it is
 * dead. Reading {@code StateNode.isInert()} instead would prove nothing —
 * {@code InertData} resolves during UIDL change collection, which MockVaadin
 * never runs. The client-side half, whether the modal overlay's focus trap
 * lets the owned overlay take keystrokes, is browser-verified through
 * Sampler's Dialogs route.
 */
class SWindowModalAttachTest extends AbstractKaribuTest {

    private static Component activeModal() {
        return UI.getCurrent().getInternals().getActiveModalComponent();
    }

    private static boolean underActiveModal(SWindow w) {
        Component modal = activeModal();
        if (modal == null) return false;
        for (Element e = w.getElement(); e != null; e = e.getParent()) {
            if (e.equals(modal.getElement())) return true;
        }
        return false;
    }

    /** A STRICT-modality dialog is what registers as the UI's active modal. */
    private static SJDialog modal(String title, SFrame owner) {
        SJDialog d = new SJDialog(title, owner);
        d.setModality(ModalityMode.STRICT);
        return d;
    }

    private static SJDialog modeless(String title, SFrame owner) {
        SJDialog d = new SJDialog(title, owner);
        d.setModality(ModalityMode.MODELESS);
        return d;
    }

    @Test
    @DisplayName("a window owned by the active modal attaches under it")
    void windowOwnedByTheActiveModalAttachesUnderIt() {
        SJDialog blocker = modal("blocker", null);
        blocker.setVisible(true);
        assertSame(blocker, activeModal());

        SJDialog owned = modeless("owned", blocker);
        owned.setVisible(true);

        assertEquals(blocker.getElement(), owned.getElement().getParent());
        assertTrue(underActiveModal(owned));
    }

    @Test
    @DisplayName("a window owned by an unrelated window attaches to the UI")
    void windowOwnedByAnUnrelatedWindowAttachesToTheUi() {
        SFrame frame = new SFrame("main");
        frame.setVisible(true);

        SJDialog blocker = modal("blocker", null);
        blocker.setVisible(true);

        SJDialog sibling = modeless("sibling", frame);
        sibling.setVisible(true);

        assertEquals(UI.getCurrent().getElement(), sibling.getElement().getParent());
        assertFalse(underActiveModal(sibling));
    }

    @Test
    @DisplayName("a window owned by a modal a nested modal has demoted stays outside")
    void windowOwnedByADemotedModalStaysOutside() {
        SJDialog outer = modal("outer", null);
        outer.setVisible(true);
        SJDialog inner = modal("inner", outer);
        inner.setVisible(true);
        assertSame(inner, activeModal(), "the nested modal is the active one");

        SJDialog ownedByOuter = modeless("owned-by-outer", outer);
        ownedByOuter.setVisible(true);

        assertEquals(UI.getCurrent().getElement(), ownedByOuter.getElement().getParent(),
                "outer is no longer the active modal, so its owned window is curtained "
                        + "— as AWT curtains it behind an application-modal inner dialog");
        assertFalse(underActiveModal(ownedByOuter));
    }

    @Test
    @DisplayName("a reshow moves the window under an owner that has since become the active modal")
    void reshowMovesTheWindowUnderAnOwnerThatBecameTheActiveModal() {
        SJDialog owner = modal("owner", null);
        SJDialog owned = modeless("owned", owner);

        // Shown before the owner is up: no modal, so the UI is the attach point.
        owned.setVisible(true);
        assertEquals(UI.getCurrent().getElement(), owned.getElement().getParent());

        owned.setVisible(false);
        owner.setVisible(true);
        owned.setVisible(true);

        assertEquals(owner.getElement(), owned.getElement().getParent());
    }

    @Test
    @DisplayName("a reshow moves the window back to the UI once its owner is gone")
    void reshowMovesTheWindowBackToTheUiOnceItsOwnerIsGone() {
        SJDialog owner = modal("owner", null);
        owner.setVisible(true);
        SJDialog owned = modeless("owned", owner);
        owned.setVisible(true);
        assertEquals(owner.getElement(), owned.getElement().getParent());

        // Disposing the owner detaches its whole subtree, this window included.
        owned.setVisible(false);
        owner.dispose();
        owned.setVisible(true);

        assertEquals(UI.getCurrent().getElement(), owned.getElement().getParent());
    }
}
