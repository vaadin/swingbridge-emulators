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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.shared.Registration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code FocusTracker}'s transition seam (SD_focus_owner_listener) — the pointer
 * move a layer above turns into events of its own.
 *
 * <p>Its one consumer is {@code vaadinx.awt.KeyboardFocusManager}'s bound
 * properties, whose own behaviour is asserted in {@code :emulators}; what is
 * pinned here is the seam's contract: per-UI scoping, the identity guard, and the
 * never-throw stance the rest of the class keeps.
 */
class FocusOwnerListenerTest extends AbstractKaribuTest {

    private final List<String> moves = new ArrayList<>();

    private Registration record() {
        return FocusTracker.addFocusOwnerListener(
                (oldOwner, newOwner) -> moves.add(name(oldOwner) + "->" + name(newOwner)));
    }

    private static String name(Component c) {
        return c == null ? "null" : c.getElement().getProperty("label", "?");
    }

    private static TextField field(String label) {
        TextField field = new TextField();
        field.setLabel(label);
        UI.getCurrent().add(field);
        return field;
    }

    @Test
    void everyPointerMoveIsReported() {
        record();
        TextField first = field("first");
        TextField second = field("second");

        FocusTracker.setFocusOwner(first);
        FocusTracker.setFocusOwner(second);
        FocusTracker.setFocusOwner(null);

        assertEquals(List.of("null->first", "first->second", "second->null"), moves);
    }

    @Test
    void anUnchangedOwnerReportsNothing() {
        record();
        TextField field = field("only");
        FocusTracker.setFocusOwner(field);
        moves.clear();

        FocusTracker.setFocusOwner(field);

        assertEquals(List.of(), moves);
    }

    @Test
    void aRemovedListenerStopsHearing() {
        Registration registration = record();
        registration.remove();

        FocusTracker.setFocusOwner(field("after"));

        assertEquals(List.of(), moves);
    }

    @Test
    void aListenerMayUnsubscribeFromItsOwnCallback() {
        // Guards the copy in moveOwner: iterating the live list here would throw
        // ConcurrentModificationException.
        Registration[] holder = new Registration[1];
        holder[0] = FocusTracker.addFocusOwnerListener((oldOwner, newOwner) -> {
            moves.add("once");
            holder[0].remove();
        });

        FocusTracker.setFocusOwner(field("a"));
        FocusTracker.setFocusOwner(field("b"));

        assertEquals(List.of("once"), moves);
    }

    @Test
    void listenersAreScopedToTheirOwnUi() {
        record();
        UI firstUi = UI.getCurrent();
        UI secondUi = new UI();
        secondUi.getInternals().setSession(firstUi.getSession());
        UI.setCurrent(secondUi);
        try {
            FocusTracker.setFocusOwner(new TextField());
        } finally {
            UI.setCurrent(firstUi);
        }
        assertEquals(List.of(), moves, "the other UI's move must not be delivered here");
    }

    @Test
    void registeringWithNoCurrentUiIsANoOpRatherThanAThrow() {
        UI firstUi = UI.getCurrent();
        UI.setCurrent(null);
        try {
            Registration registration = record();
            assertNotNull(registration);
            registration.remove();
        } finally {
            UI.setCurrent(firstUi);
        }
        assertTrue(moves.isEmpty());
    }
}
