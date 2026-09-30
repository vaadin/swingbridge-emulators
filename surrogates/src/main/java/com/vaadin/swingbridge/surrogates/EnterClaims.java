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
import com.vaadin.flow.component.ComponentUtil;

import java.io.Serializable;
import java.util.function.Predicate;

/**
 * Whether the focused component takes Enter itself, so the root pane's default button must not
 * (SD_enter_claims). Swing offers a key to the focused component's {@code WHEN_FOCUSED} bindings
 * before any window-wide one, and a binding whose action is enabled consumes it:
 *
 * <pre>{@code
 * class SJTextArea extends TextArea implements EnterClaims.Claimant {
 *     public boolean claimsEnter() { return isEditable(); }            // Enter inserts a newline
 * }
 * // a formatted field: Enter commits an edit, and only an unedited field lets it through
 * addValueChangeListener(e -> { if (e.isFromClient()) EnterClaims.noteClientCommit(this); });
 * public boolean claimsEnter() { return EnterClaims.committedThisRoundTrip(this); }
 * }</pre>
 *
 * <p>UI-thread-confined.
 */
public final class EnterClaims {

    /** A component with an Enter binding of its own; asked only while it, or a descendant, has focus. */
    public interface Claimant {
        /** Whether that binding is enabled right now — Swing's {@code Action.isEnabled()}. */
        boolean claimsEnter();
    }

    /** {@link ComponentUtil} key: present while the browser's commit of this round trip is unread. */
    private record ClientCommit() implements Serializable {
    }

    private EnterClaims() {
    }

    /**
     * Records that the browser committed {@code component}'s value in the round trip being processed.
     * The mark lasts until the response is written.
     *
     * <p>This is how "edited" reaches the server at all: the browser sends typed text only on
     * commit, and Vaadin's fields commit in their own Enter handler, so the value change arrives
     * ahead of the default button's shortcut in the same round trip (measured on
     * {@code DatePicker} and {@code IntegerField}).
     */
    public static void noteClientCommit(Component component) {
        component.getUI().ifPresent(ui -> {
            ComponentUtil.setData(component, ClientCommit.class, new ClientCommit());
            ui.beforeClientResponse(component,
                    context -> ComponentUtil.setData(component, ClientCommit.class, null));
        });
    }

    /** Whether {@link #noteClientCommit} marked {@code component} in the current round trip. */
    public static boolean committedThisRoundTrip(Component component) {
        return ComponentUtil.getData(component, ClientCommit.class) != null;
    }

    /**
     * Whether {@code focusOwner} or an ancestor below {@code root} claims Enter.
     *
     * @param focusOwner the focused peer, or {@code null}; a focus outside {@code root} claims nothing
     * @param layerClaims the claims a layer above knows and the surrogate cannot — the emulator's own
     *     {@code ActionListener}s, say; consulted beside {@link Claimant}
     */
    static boolean claimed(Component focusOwner, Component root, Predicate<Component> layerClaims) {
        boolean claimed = false;
        for (Component c = focusOwner; c != null; c = c.getParent().orElse(null)) {
            if (c == root) {
                return claimed;
            }
            claimed |= (c instanceof Claimant claimant && claimant.claimsEnter()) || layerClaims.test(c);
        }
        return false;
    }
}
