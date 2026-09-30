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
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

/**
 * Surrogate for {@link javax.swing.JScrollPane} (SD_sjscrollpane). Extends Vaadin
 * {@link Scroller} directly (is-a) so it reaches the migrator either
 * through {@code :emulators.JScrollPane} as peer, or as a richer-than-
 * stock Vaadin scroll container in its own right.
 *
 * <h2>Why Scroller as peer</h2>
 *
 * Vaadin 25's native scroll container — a single-content-slot
 * container with a built-in {@link ScrollDirection} enum
 * ({@link ScrollDirection#VERTICAL} / {@link ScrollDirection#HORIZONTAL} /
 * {@link ScrollDirection#BOTH} / {@link ScrollDirection#NONE}). The
 * single-child invariant is what JScrollPane already enforces (one
 * viewport view at a time); {@link Scroller#setContent(Component)}
 * IS the {@code setViewportView} mapping.
 *
 * <h2>Auto-scroll guard</h2>
 *
 * {@link #setContent(Component)} forces {@link ScrollDirection#NONE} for
 * content that owns its own overflow layer (Grid / TextArea / Scroller) — see
 * that method for the pathology it prevents. The guard fires only on content
 * swap, so an explicit {@link #setScrollDirection setScrollDirection(VERTICAL)}
 * <i>after</i> {@code setContent(grid)} sticks: opt-out is one line.
 *
 * <h2>Inherited mixin surface</h2>
 *
 * Almost the entire JComponent surface comes from {@link JComponentMixin}:
 * border CSS round-trip (SD_border_css_lossy), client properties, ancestor listeners,
 * tooltip pass-through, etc. SJScrollPane itself adds only the three
 * ctors + the auto-scroll guard + L&amp;F surface tail.
 *
 * <h2>R_vaadin_first lossy: getScrollDirection() after auto-scroll setContent</h2>
 *
 * After {@code setContent(grid)}, {@link #getScrollDirection()} returns
 * {@link ScrollDirection#NONE} regardless of any prior
 * {@link #setScrollDirection setScrollDirection} call — the auto-scroll
 * guard overwrote it. R_vaadin_first-accepted lossy direction; the JDK-shaped
 * scrollbar policy ints live on the emulator side via field shadow
 * per R_swing_is_truth. Migration convention: read scrollbar policy through the
 * emulator ({@code JScrollPane.getVerticalScrollBarPolicy()}), not
 * by inspecting the surrogate's Scroller direction.
 */
public class SJScrollPane extends Scroller implements JComponentMixin {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    public SJScrollPane() {
        super();
        _installSwingClass();
    }

    public SJScrollPane(Component view) {
        super();
        _installSwingClass();
        // Funnel through our setContent override — the Scroller(Component)
        // ctor's body would do super.setContent which skips the guard.
        // Explicitly route through our override so a fresh
        // SJScrollPane(grid) lands in NONE direction without a separate
        // setContent call.
        setContent(view);
    }

    public SJScrollPane(Component view, ScrollDirection direction) {
        super();
        _installSwingClass();
        setScrollDirection(direction);
        setContent(view);
    }

    /**
     * Set the viewport content, applying the auto-scroll guard.
     *
     * <p>Delegates to {@link Scroller#setContent(Component)}, then if the
     * content is an instance of any {@linkplain #isAutoScrollingType
     * known auto-scrolling Vaadin component type}, forces this Scroller's
     * direction to {@link ScrollDirection#NONE}. Prevents double-scrollbars
     * when a Grid / TextArea / Scroller is wrapped by SJScrollPane.
     *
     * <p>{@code null} content clears the slot without applying the guard
     * (no content to inspect; whatever direction was set last sticks).
     */
    @Override
    public void setContent(Component content) {
        super.setContent(content);
        if (content != null && isAutoScrollingType(content)) {
            // Skip via super to avoid any future override loops; the
            // guard is the canonical "force NONE" channel for autoscrolling
            // content.
            super.setScrollDirection(ScrollDirection.NONE);
            // Stretch the auto-scrolling content to fill the Scroller's
            // viewport. The guard's intent is "this content owns its own
            // overflow:auto, so let it scroll within the JScrollPane area"
            // — but Vaadin Grid / TextArea size to their intrinsic content
            // by default and overflow the Scroller visually. Setting size
            // 100% gives them the bounded viewport their internal
            // scrolling needs. Inline style on the content's element
            // overrides the Vaadin component default; user code that
            // explicitly setSize'd before setContent gets re-stretched
            // (R_layouts_close_enough close-enough — the JScrollPane wrap is the contract,
            // not the inner component's own preferred size).
            content.getElement().getStyle().set("width", "100%");
            content.getElement().getStyle().set("height", "100%");
        }
    }

    /**
     * Returns {@code true} if {@code content} is a Vaadin component type
     * whose shadow DOM owns its own overflow:auto layer — wrapping it in
     * a Scroller produces stacked scrollbars or zero-height collapse.
     *
     * <p>Recognised today: {@link Grid},
     * {@link TextArea}, {@link Scroller} itself. Adding more is a
     * one-line {@code instanceof} branch when another component
     * lands a peer that auto-scrolls (e.g. {@code VirtualList} if it
     * ever sits inside a JScrollPane). Per [R_infra_not_surface](../../../../CLAUDE.md),
     * don't pre-emptively expand the list — three matches cover the
     * testbed surface (`JScrollPane(JTable)`,
     * `JScrollPane(JTextArea)`, defensive nesting). The check uses
     * runtime {@code instanceof} on the Vaadin class, so any subclass
     * (including emulator surrogates {@code SJTable} / {@code SJTextArea}
     * themselves) is caught transitively.
     */
    private static boolean isAutoScrollingType(Component content) {
        return content instanceof Grid
                || content instanceof TextArea
                || content instanceof Scroller;
    }

    // --- L&F surface (matches SJPanel + every other surrogate) ----------

    /** Swing constant for L&amp;F lookup; ScrollPaneUI isn't pluggable here. */
    public String getUIClassID() {
        return "ScrollPaneUI";
    }

    /** L&amp;F swap — no-op for us; Vaadin owns the DOM. */
    public void updateUI() {
        // Same shape as SJPanel.updateUI / SJButton.updateUI.
    }

    /** L&amp;F surgery isn't modelled; WARN so integration gaps surface. */
    public void setUI(javax.swing.plaf.ScrollPaneUI ui) {
        SHelper.onUnimplemented(this, "setUI", ui);
    }

    // Note: no JDK-shaped getUI() — Vaadin Component.getUI() returns
    // Optional<UI>, signatures clash the same way SJPanel.getUI is
    // documented to clash per SD_sjpanel. Pure-surrogate users access UI
    // via inherited Component.getUI(); migrated code reaching
    // JScrollPane.getUI() against a pure SJScrollPane won't compile
    // (against :emulators.JScrollPane it works unchanged via the
    // emulator's own field shadow).

    // --- Accessibility (deferred per surrogate-wide stance) ------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
