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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.server.communication.rpc.EventRpcHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-UI focus pointer (SD_focus_tracker). The {@code focusin} bridge is mostly
 * browser-verified — Karibu records DOM listener registrations without dispatching
 * them — so what is testable here is the pointer's contract: the optimistic
 * server-side path every {@code requestFocus} takes, the per-UI scoping, the
 * never-throw stance, and one bridge event driven through Flow's own RPC handler.
 */
class FocusTrackerTest extends AbstractKaribuTest {

    @Test
    @DisplayName("no focus owner by default")
    void noFocusOwnerByDefault() {
        assertNull(FocusTracker.getFocusOwner());
    }

    @Test
    @DisplayName("setFocusOwner is read back")
    void setFocusOwnerIsReadBack() {
        TextField field = new TextField();
        UI.getCurrent().add(field);
        FocusTracker.setFocusOwner(field);
        assertSame(field, FocusTracker.getFocusOwner());
    }

    @Test
    @DisplayName("null clears the pointer")
    void nullClearsThePointer() {
        FocusTracker.setFocusOwner(new TextField());
        FocusTracker.setFocusOwner(null);
        assertNull(FocusTracker.getFocusOwner());
    }

    @Test
    @DisplayName("install is idempotent")
    void installIsIdempotent() {
        // Called once per UI init in production; a second call must not plant a
        // second focusin listener (which would double every round trip).
        FocusTracker.install();
        int before = countListeners(UI.getCurrent().getElement());
        FocusTracker.install();
        assertEquals(before, countListeners(UI.getCurrent().getElement()));
    }

    @Test
    @DisplayName("pointer does not leak across UIs")
    void pointerDoesNotLeakAcrossUis() {
        TextField first = new TextField();
        FocusTracker.setFocusOwner(first);
        UI firstUi = UI.getCurrent();

        // A second browser tab has its own DOM and its own activeElement, so it
        // must start out with nothing focused — deliberately unlike D_session_scoped_pools's
        // session-scoped Timer/SwingWorker pools.
        UI secondUi = new UI();
        secondUi.getInternals().setSession(firstUi.getSession());
        UI.setCurrent(secondUi);
        try {
            assertNull(FocusTracker.getFocusOwner());
        } finally {
            UI.setCurrent(firstUi);
        }
        assertSame(first, FocusTracker.getFocusOwner());
    }

    @Test
    @DisplayName("no current UI is answered, not thrown")
    void noCurrentUiIsAnsweredNotThrown() {
        UI ui = UI.getCurrent();
        UI.setCurrent(null);
        try {
            assertNull(FocusTracker.getFocusOwner());
            FocusTracker.setFocusOwner(new Div());   // must not throw
            FocusTracker.install();                  // must not throw
        } finally {
            UI.setCurrent(ui);
        }
    }

    @Test
    @DisplayName("focusin bridge identifies the peer by composed path")
    void focusinBridgeIdentifiesThePeerByComposedPath() {
        // The one thing Karibu can pin about the bridge, and it is worth pinning:
        // mapEventTargetElement() resolves event.target to <body> for a listener
        // on the UI's own element, which reads back as "nothing focused" AND
        // overwrites the correct value the peer's focus event just stored. Only
        // this assertion stands between that regression and a dead bridge.
        FocusTracker.install();
        var expressions = UI.getCurrent().getElement().getNode()
                .getFeature(ElementListenerMap.class)
                .getExpressions("focusin");
        assertTrue(
                expressions.stream().anyMatch(
                        it -> it.contains("composedPath()") && it.contains("getRootNode() === document")),
                "focusin must identify the peer off the composed path, not event.target: " + expressions);
    }

    @Test
    @DisplayName("focusin still moves the pointer while a modal makes the UI inert")
    void focusinReachesTheServerUnderAModal() {
        // A modal child makes the UI element inert, and Flow drops an inert
        // element's DOM events server-side unless the listener allowInert()s —
        // the pointer then froze on whatever had focus when the dialog opened.
        // Dispatched through EventRpcHandler, the browser's own entry point:
        // Karibu's _fireDomEvent skips the inert check this test is about.
        FocusTracker.install();
        Div modal = new Div();
        TextField inModal = new TextField();
        modal.add(inModal);
        UI ui = UI.getCurrent();
        ui.add(modal);
        ui.setChildComponentModal(modal, true);
        // Flow resolves inertness while writing a response; Karibu writes none.
        ui.getInternals().getStateTree().collectChanges(change -> { });
        assertTrue(ui.getElement().getNode().isInert(), "precondition: the modal made the UI inert");

        // The registered expression is the event-data key the browser sends back,
        // element-data prefix included.
        String focusHostKey = ui.getElement().getNode().getFeature(ElementListenerMap.class)
                .getExpressions("focusin").stream()
                .filter(it -> it.contains("composedPath()")).findFirst().orElseThrow();
        var invocation = JacksonUtils.createObjectNode();
        invocation.put("type", "event");
        invocation.put("node", ui.getElement().getNode().getId());
        invocation.put("event", "focusin");
        invocation.putObject("data").put(focusHostKey, inModal.getElement().getNode().getId());
        new EventRpcHandler().handle(ui, invocation).ifPresent(Runnable::run);

        assertSame(inModal, FocusTracker.getFocusOwner());
    }

    private static int countListeners(Element element) {
        ElementListenerMap map = element.getNode().getFeature(ElementListenerMap.class);
        return List.of("focusin", "focusout").stream()
                .mapToInt(name -> map.getExpressions(name).size())
                .sum();
    }
}
