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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SWindow#dispose()} detaches only once the client's {@code closed}
 * event arrives, when one is coming (SD_detach_after_closed).
 *
 * <p>A {@link MockVaadin#clientRoundtrip()} between show and dispose is what
 * "the browser saw it open" means here: it runs the {@code beforeClientResponse}
 * that records it. None may run between dispose and the fired {@code closed},
 * because Karibu's {@code cleanupDialogs} removes every closed Dialog on a
 * roundtrip and would detach it for us.
 */
class SWindowDetachAfterClosedTest extends AbstractKaribuTest {

    private static boolean attached(SWindow w) {
        return w.getElement().getNode().isAttached();
    }

    private static void fireClosed(SWindow w) {
        w.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(w.getElement(), "closed", JsonNodeFactory.instance.objectNode()));
    }

    private static SWindow shownOnClient() {
        SWindow w = new SWindow();
        w.setVisible(true);
        MockVaadin.clientRoundtrip();
        return w;
    }

    @Test
    @DisplayName("a window the browser saw open stays attached until its closed event")
    void detachWaitsForClosed() {
        SWindow w = shownOnClient();
        w.dispose();
        assertTrue(attached(w));
        assertFalse(w.isOpened());

        fireClosed(w);
        assertFalse(attached(w));
    }

    @Test
    @DisplayName("WINDOW_CLOSED fires at dispose, not when the detach lands")
    void windowClosedIsNotDeferred() {
        SWindow w = shownOnClient();
        List<Integer> ids = new ArrayList<>();
        w.addWindowListener(new SWindowAdapter() {
            @Override
            public void windowClosed(SWindowEvent e) {
                ids.add(e.getID());
            }
        });
        w.dispose();
        assertEquals(List.of(SWindowEvent.WINDOW_CLOSED), ids);
        fireClosed(w);
        assertEquals(List.of(SWindowEvent.WINDOW_CLOSED), ids);
    }

    @Test
    @DisplayName("shown and disposed in one request: no closed event is coming, so it detaches at once")
    void showAndDisposeInOneRequestDetachesAtOnce() {
        SWindow w = new SWindow();
        w.setVisible(true);
        w.dispose();
        assertFalse(attached(w));
    }

    @Test
    @DisplayName("hidden earlier and its close answered: a later dispose detaches at once")
    void disposeAfterAnsweredHideDetachesAtOnce() {
        SWindow w = shownOnClient();
        w.setVisible(false);
        fireClosed(w);
        assertTrue(attached(w));

        w.dispose();
        assertFalse(attached(w));
    }

    @Test
    @DisplayName("hidden in a request, disposed before the client answered: the closed event detaches")
    void disposeWhileHideStillAnimatingWaits() {
        SWindow w = shownOnClient();
        w.setVisible(false);
        w.dispose();
        assertTrue(attached(w));

        fireClosed(w);
        assertFalse(attached(w));
    }

    @Test
    @DisplayName("re-shown before the closed event: the stale event leaves the window up")
    void reshowCancelsPendingDetach() {
        SWindow w = shownOnClient();
        w.dispose();
        w.setVisible(true);

        fireClosed(w);
        assertTrue(attached(w));
        assertTrue(w.isOpened());
    }

    @Test
    @DisplayName("never shown: dispose detaches nothing and waits for nothing")
    void neverShownDispose() {
        SWindow w = new SWindow();
        w.dispose();
        assertFalse(attached(w));
    }
}
