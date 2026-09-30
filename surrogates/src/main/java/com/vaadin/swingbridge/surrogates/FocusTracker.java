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
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.shared.Registration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Vaadin component that currently has browser focus, per UI (SD_focus_tracker) — the
 * "who has focus" fact both layers need, cached where both can reach it:
 *
 * <pre>{@code
 * FocusTracker.install();          // once per UI, from SwingBridgeEmulatorsBootstrap's UI-init leg
 * Component focused = FocusTracker.getFocusOwner();   // null when nothing of ours has it
 * }</pre>
 *
 * <p>Fed by <b>one</b> {@code focusin} listener on the UI's own element, which
 * covers every peer regardless of type — a per-peer {@code FocusNotifier}
 * subscription would miss everything sitting on a bare {@code Div}. The focused
 * peer is identified by {@link #FOCUS_HOST}, whose caveat is worth reading
 * before touching this wiring.
 *
 * <p><b>Per UI, not per session:</b> a browser tab has its own DOM and its own
 * {@code activeElement}, so focus cannot be shared across tabs — deliberately
 * the opposite call from {@code D_session_scoped_pools}'s session-scoped {@code Timer} /
 * {@code SwingWorker} pools.
 *
 * <p>Never throws: an unpopulated pointer means "nothing focused", a legitimate
 * state rather than the wiring mistake {@link BrowserTimeZone#get()} fails loud
 * on.
 *
 * <p>UI-thread-confined.
 */
public final class FocusTracker {

    /** Per-UI state. Deliberately not per session — see the class javadoc. */
    private static final class State implements Serializable {
        /** The focused peer, or {@code null} when focus sits outside our components. */
        Component owner;
        /** Guards against a second {@code focusin} listener on the same UI. */
        boolean installed;
        /** @see #addFocusOwnerListener */
        final List<FocusOwnerListener> listeners = new ArrayList<>();
    }

    /**
     * Notified when this UI's focus pointer moves, so a layer above can turn the
     * move into events of its own — {@code vaadinx.awt.KeyboardFocusManager}'s
     * bound properties are the one consumer shape this was built for.
     */
    public interface FocusOwnerListener extends Serializable {
        /**
         * @param oldOwner the peer that held the pointer, or {@code null}
         * @param newOwner the peer that holds it now, or {@code null} when focus
         *     left for somewhere outside our components
         */
        void focusOwnerChanged(Component oldOwner, Component newOwner);
    }

    /**
     * JS expression naming the focused element's outermost shadow host — the
     * first element on the composed path whose root node is the document, which
     * for an {@code <input>} inside a peer's shadow root is the peer element.
     *
     * <p><b>Do not simplify this to {@code mapEventTargetElement()}.</b>
     * For a listener on the UI's own {@code <body>} element, Flow resolves
     * {@code event.target} to {@code <body>} itself — so the pointer reads
     * back as "nothing focused" and, worse, overwrites the correct value the
     * peer's own focus event had just stored. Browser-verified; and since
     * Karibu never dispatches DOM events, only
     * {@code FocusTrackerTest.focusin bridge identifies the peer by composed
     * path} stands between that regression and a silently dead bridge.
     */
    private static final String FOCUS_HOST =
            "event.composedPath().find(n => n.nodeType === 1 && n.getRootNode() === document)";

    private FocusTracker() {}

    /**
     * Installs this UI's {@code focusin} / {@code focusout} listeners.
     * Idempotent; a no-op without a current UI.
     *
     * <p>Registered browser or browserless — the registration is inert
     * under Karibu, which never dispatches DOM events, so gating it would
     * only make production and test wiring diverge.
     */
    public static void install() {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        State state = stateFor(ui);
        if (state.installed) return;
        state.installed = true;

        // focusin bubbles and is composed, so one listener on <body> sees every
        // focus change in the tab, overlays included. allowInert: a modal dialog
        // makes the UI element inert, and Flow then drops its events server-side —
        // so without it the pointer freezes on whatever had focus when the modal
        // opened, and every focus question inside a modal answers wrong.
        ui.getElement()
                .addEventListener("focusin",
                        event -> moveOwner(state, event.getEventDataElement(FOCUS_HOST)
                                .map(FocusTracker::resolve)
                                .orElse(null)))
                .addEventDataElement(FOCUS_HOST)
                .allowInert();

        // Clicking empty space blurs without focusing anything, and fires no
        // focusin at all — so the losing side has to clear the pointer. The
        // filter keeps the round trip to the one case that matters: relatedTarget
        // is the element *gaining* focus, and a null one means focus left for
        // nowhere. When it is non-null the focusin above is about to arrive and
        // overwrite the pointer anyway.
        ui.getElement()
                .addEventListener("focusout", event -> moveOwner(state, null))
                .setFilter("!event.relatedTarget")
                .allowInert();
    }

    /**
     * The focused Vaadin component in the current UI, or {@code null} when
     * nothing our side owns has focus (including: no current UI, or
     * {@link #install()} was never called because the app skipped
     * {@code SwingBridgeEmulatorsBootstrap}).
     */
    public static Component getFocusOwner() {
        UI ui = UI.getCurrent();
        if (ui == null) return null;
        State state = ComponentUtil.getData(ui, State.class);
        return state == null ? null : state.owner;
    }

    /**
     * Moves the pointer without waiting for the browser, so a {@code requestFocus}
     * and a {@code getFocusOwner()} read in the same round trip agree. No-op
     * without a current UI.
     *
     * <p>Best-effort: if the browser refuses the focus (hidden element, not
     * focusable) the next real {@code focusin} corrects us.
     */
    public static void setFocusOwner(Component owner) {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        moveOwner(stateFor(ui), owner);
    }

    /**
     * Subscribes to the current UI's focus transitions:
     *
     * <pre>{@code
     * Registration r = FocusTracker.addFocusOwnerListener(
     *         (oldOwner, newOwner) -> fireFocusProperties(oldOwner, newOwner));
     * }</pre>
     *
     * <p>Per UI, like the pointer itself — a listener registered from one tab
     * never hears another's. Registering without a current UI returns a no-op
     * registration rather than throwing, matching {@link #getFocusOwner()}'s
     * stance on the same situation.
     *
     * @return the handle that unsubscribes; never {@code null}
     */
    public static Registration addFocusOwnerListener(FocusOwnerListener listener) {
        UI ui = UI.getCurrent();
        if (ui == null) return () -> { };
        State state = stateFor(ui);
        state.listeners.add(listener);
        return () -> state.listeners.remove(listener);
    }

    /**
     * Writes the pointer and notifies, or does nothing when the owner is
     * unchanged.
     *
     * <p>Identity, not {@code equals}: a component is a place in the tree,
     * and Vaadin components are compared by identity everywhere else here.
     * The listener list is copied before the walk so a listener may
     * unsubscribe itself from its own callback.
     */
    private static void moveOwner(State state, Component owner) {
        Component old = state.owner;
        if (old == owner) return;
        state.owner = owner;
        if (state.listeners.isEmpty()) return;
        for (FocusOwnerListener listener : new ArrayList<>(state.listeners)) {
            listener.focusOwnerChanged(old, owner);
        }
    }

    /**
     * A peer's own Vaadin focus event moved focus to {@code owner}. Same effect
     * as {@link #setFocusOwner}, named apart because the caller is reporting a
     * browser fact rather than requesting a move.
     *
     * <p>Additive to the {@code focusin} bridge, not a replacement: it
     * fires only for peers something subscribed to, where the pointer has to
     * answer for every component. Free on a round trip the peer was already
     * making — and the only path a browserless test can drive.
     */
    public static void noteFocusGained(Component owner) {
        setFocusOwner(owner);
    }

    /**
     * A peer's own Vaadin blur event: focus left {@code loser}. Clears the
     * pointer only if {@code loser} still holds it — the browser fires
     * {@code focusout} on the old element before {@code focusin} on the new one,
     * so an unconditional clear here would wipe a pointer the newer event has
     * already moved on.
     */
    public static void noteFocusLost(Component loser) {
        if (getFocusOwner() == loser) setFocusOwner(null);
    }

    /**
     * The nearest component-bearing ancestor of {@code element}, or {@code null}
     * when the walk reaches the UI itself — focus on {@code <body>} means
     * nothing of ours owns it.
     *
     * <p>Flow resolves the raw DOM target only as far as the closest
     * <em>server-controlled element</em>, which can still be a bare
     * {@code Element} inside a composite peer with no {@code Component} of
     * its own; hence the walk.
     */
    private static Component resolve(Element element) {
        for (Element e = element; e != null; e = e.getParent()) {
            Optional<Component> component = e.getComponent();
            if (component.isPresent()) {
                return component.get() instanceof UI ? null : component.get();
            }
        }
        return null;
    }

    private static State stateFor(UI ui) {
        State state = ComponentUtil.getData(ui, State.class);
        if (state == null) {
            state = new State();
            ComponentUtil.setData(ui, State.class, state);
        }
        return state;
    }
}
