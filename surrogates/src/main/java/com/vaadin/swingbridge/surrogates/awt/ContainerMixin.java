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

package com.vaadin.swingbridge.surrogates.awt;

import com.vaadin.flow.component.Component;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.util.LayoutCss;
import com.vaadin.swingbridge.surrogates.awt.event.SContainerListener;
import com.vaadin.swingbridge.surrogates.internal.LayoutStore;

import java.awt.LayoutManager;

/**
 * Mixin that adds the {@link java.awt.Container} API surface on top of
 * {@link ComponentMixin}.
 *
 * <p>Excluded entirely: {@code add(Component)}, {@code remove(Component)},
 * {@code getComponent(int)}, {@code getComponents()},
 * {@code getComponentCount()}, {@code isAncestorOf(Component)} —
 * Vaadin's own child-management API on {@code HasComponents} wins.
 *
 * <p>{@code setLayout} stores the {@link LayoutManager} via
 * {@link LayoutStore} and applies layout CSS to the surrogate's element
 * for the two built-in {@link java.awt.LayoutManager} types
 * ({@link java.awt.FlowLayout} / {@link java.awt.BorderLayout}).
 * Per-child {@code grid-area} writes for
 * BorderLayout's region constraints aren't covered here — pure-surrogate
 * users that need region-anchored layout currently rebuild the DOM in
 * concrete-surrogate code; the {@code :emulators.BorderLayout.addLayoutComponent}
 * path keeps owning that for emulator-stage migrators.
 *
 * <p>{@code addContainerListener} / {@code removeContainerListener} /
 * {@code getContainerListeners} are Bucket B {@link SHelper#onNoop} (see
 * SD_listeners_without_analog): Vaadin 25 has no server-side child-list mutation event. Vaadin
 * 8's {@code ComponentContainer.addComponentAttachListener()} was
 * removed in the Flow rewrite and never reinstated — we cannot observe
 * our own children being added or removed from a generic mixin. A
 * concrete container surrogate (e.g. an {@code SPanel}) could override
 * {@code HasComponents.add} / {@code remove} and fire the event itself;
 * that's scope-bound per-surrogate work. {@code getContainerListeners()}
 * returns an empty array — honestly reflecting the fact that a
 * registered listener on this mixin would never fire.
 */
public interface ContainerMixin extends ComponentMixin {

    /** See {@link ComponentMixin#_self()}. */
    private Component _self() {
        return (Component) this;
    }

    // --- layout manager (LayoutStore + CSS dispatch) -

    /**
     * Stores the {@link LayoutManager} and, for the two built-in JDK
     * types ({@link java.awt.FlowLayout} / {@link java.awt.BorderLayout}),
     * applies the equivalent container-wide CSS to the surrogate's
     * element via {@link LayoutCss#applyContainerCss}. Custom or unrecognised
     * layout managers store but don't dispatch — the renderer ends up
     * with default block flow, matching the pre-Track-A inert behaviour.
     * The previous manager's container CSS is cleared first via
     * {@link LayoutCss#resetContainerCss}, so replacing a built-in layout with a
     * custom one — or with {@code null}, which clears the store — leaves plain
     * block flow rather than the old layout's keys.
     */
    default void setLayout(LayoutManager mgr) {
        LayoutStore.of(_self()).set(mgr);
        // Undo the outgoing manager's keys before the incoming one writes: the
        // built-in builders each write the full CONTAINER_KEYS union and so
        // overwrite each other cleanly, but a custom or null manager dispatches
        // nothing and would otherwise inherit the old layout's CSS.
        LayoutCss.resetContainerCss(_self().getElement());
        if (mgr instanceof java.awt.FlowLayout fl) {
            LayoutCss.applyContainerCss(_self().getElement(), "FlowLayout",
                    LayoutCss.flowLayoutCss(fl.getAlignment(), fl.getHgap(), fl.getVgap()));
        } else if (mgr instanceof java.awt.BorderLayout bl) {
            LayoutCss.applyContainerCss(_self().getElement(), "BorderLayout",
                    LayoutCss.borderLayoutCss(bl.getHgap(), bl.getVgap()));
        }
        // Other LayoutManager impls (custom user code, or LayoutManagers
        // outside the built-in two) store + render-as-default-block.
        // Field round-trip via getLayout so user code reads back what it
        // installed.
    }

    /** Returns whatever {@link #setLayout(LayoutManager)} last stored. */
    default LayoutManager getLayout() {
        return LayoutStore.of(_self()).get();
    }

    // --- container listeners (Bucket B onNoop — no Vaadin analog; SD_listeners_without_analog) -

    /** Bucket B {@link SHelper#onNoop} — see class-level javadoc for the SD_listeners_without_analog rationale. */
    default void addContainerListener(SContainerListener listener) {
        SHelper.onNoop(this, "addContainerListener");
    }

    /** Bucket B {@link SHelper#onNoop}. */
    default void removeContainerListener(SContainerListener listener) {
        SHelper.onNoop(this, "removeContainerListener");
    }

    /** Always an empty array — nothing registered; see class-level javadoc. */
    default SContainerListener[] getContainerListeners() {
        SHelper.onNoop(this, "getContainerListeners");
        return new SContainerListener[0];
    }

    // --- Container-only paint dispatch (onNoop) ---------------------

    /** Deliberate no-op like the {@link ComponentMixin#paint} family. */
    default void paintComponents(java.awt.Graphics g) { SHelper.onNoop(this, "paintComponents"); }
    /** Print-pipeline counterpart to {@link #paintComponents(java.awt.Graphics)}. */
    default void printComponents(java.awt.Graphics g) { SHelper.onNoop(this, "printComponents"); }
}
