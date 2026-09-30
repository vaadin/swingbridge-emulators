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

package vaadinx.awt;

import java.util.Map;

/**
 * A {@link LayoutManager} that positions children by <em>emitting CSS</em> onto the
 * container's peer {@code <div>} (and optionally onto each child) instead of the pixel
 * {@code setBounds} arithmetic a desktop {@code LayoutManager} does — there is no
 * synchronous child-size measurement in the browser to drive that arithmetic. This is
 * the seam a migrator implements to teach the emulator a custom Swing layout; every
 * built-in ({@link FlowLayout}, {@link BorderLayout}, {@code BoxLayout},
 * {@code GridLayout}, {@code GridBagLayout}, {@code GroupLayout}) is one of these.
 *
 * <p>Implement {@link #containerCss}; add {@link #childCss} only when children need
 * per-item CSS. The framework drives {@link #layoutContainer} for you — you never touch
 * {@code EHelper} or a Vaadin {@code Element}:
 *
 * <pre>{@code
 * // A two-column form: labels hug their content, fields absorb the rest.
 * public class TwoColumnLayout implements CssEmittingLayoutManager {
 *     public Map<String,String> containerCss(Container parent) {
 *         return Map.of("display", "grid",
 *                       "grid-template-columns", "auto 1fr",
 *                       "gap", "4px 8px");
 *     }
 * }
 * // parent.setLayout(new TwoColumnLayout());   // children flow into the grid
 * }</pre>
 *
 * <p>A container whose installed layout is <em>not</em> a {@code CssEmittingLayoutManager}
 * — a hand-rolled pixel {@code LayoutManager} — can't be translated: {@link Container#doLayout}
 * WARNs once and falls back to a readable vertical stack (M1D_custom_layoutmanager).
 *
 * <h2>Implementation details</h2>
 *
 * <p><b>Call order (contract).</b> Within one {@link #layoutContainer} pass
 * {@link #containerCss} runs exactly once, before any {@link #childCss} call. A layout
 * whose per-child CSS is a byproduct of a whole-container analysis (GridBag track
 * weights, GroupLayout bands) may compute in {@code containerCss}, stash the result, and
 * read it back in {@code childCss} — {@code GridBagLayout} and {@code GroupLayout} do
 * exactly this. A layout whose per-child CSS is already known at add time can instead
 * write it eagerly from {@code addLayoutComponent} and skip {@code childCss} entirely —
 * {@code BorderLayout} does that with {@code grid-area}.
 *
 * <p><b>Child sizing (D_layout_owns_child_sizing).</b> A child's
 * {@link Component#setPreferredSize} does not write {@code width} / {@code height}
 * outright — it writes {@code width: var(--emul-layout-w, Npx)}, and the parent layout
 * rules on each axis by writing that variable from {@link #childCss}:
 * {@code com.vaadin.swingbridge.surrogates.util.LayoutCss.sizingCss(layoutSizesWidth, layoutSizesHeight)}
 * builds the pair. Say {@code true} for an axis this layout sizes itself (the pref is
 * ignored, as {@code BorderLayout} NORTH ignores the pref width), {@code false} to let
 * the pref stand. A layout that returns neither variable leaves whatever the last layout
 * wrote in place, so emit both for every child — {@code sizingCss} always does.
 *
 * <p><b>Map semantics.</b> A returned map applies key-by-key: a value sets the property,
 * {@code null} removes it, an absent key is left untouched — so a layout can clear a
 * sibling layout's leftover keys without clobbering co-owned ones (a field surrogate's
 * inline {@code width}). {@link #containerCss} writes gate on the peer being a
 * {@code <div>} (a non-Div peer gets an ERROR and no write); {@link #childCss} writes do
 * not gate — a child is any element.
 */
public interface CssEmittingLayoutManager extends LayoutManager {

    /**
     * Container-wide CSS for {@code parent}'s peer content element (its {@code <div>}).
     *
     * @return property→value map; a {@code null} value removes the property, an absent
     *         key is left untouched.
     */
    Map<String, String> containerCss(Container parent);

    /**
     * Per-child CSS for one {@code child} of {@code parent}; {@code null} (the default)
     * means the child needs none. Called once per child per pass, after
     * {@link #containerCss} (see the call-order contract).
     */
    default Map<String, String> childCss(Container parent, Component child) {
        return null;
    }

    /** Name used in the non-Div ERROR log for {@link #containerCss} writes. */
    default String cssLayoutName() {
        return getClass().getSimpleName();
    }

    @Override
    default void layoutContainer(Container parent) {
        // One hop for the whole pass: the children share the parent's UI.
        parent.withPeer(p -> {
            vaadinx.EHelper.applyContainerCss(
                    parent.peerContentElement(), cssLayoutName(), containerCss(parent));
            int n = parent.getComponentCount();
            for (int i = 0; i < n; i++) {
                Component child = parent.getComponent(i);
                Map<String, String> css = childCss(parent, child);
                if (css != null && !css.isEmpty()) {
                    vaadinx.EHelper.applyChildCss(child.getPeer().getElement(), css);
                }
            }
        });
    }
}
