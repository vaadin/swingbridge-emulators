/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.JScrollPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.swingbridge.surrogates.SJScrollPane;

/**
 * Emulator for {@link javax.swing.JScrollPane}. Per
 * <a href="../../../emulators/decisions.md#D_jscrollpane">D_jscrollpane</a>,
 * a thin delegating shell over the
 * {@link com.vaadin.swingbridge.surrogates.SJScrollPane} surrogate (extends Vaadin
 * {@link Scroller}). The scrollbar-policy ints field-shadow JDK-faithfully
 * (R_swing_is_truth store-then-drive); the {@link Scroller#setScrollDirection
 * setScrollDirection} push to the surrogate is skipped when the current
 * viewport content is auto-scrolling (per
 * <a href="../../../surrogates/decisions.md#SD_sjscrollpane">SD_sjscrollpane</a>'s
 * setContent override on the surrogate side), so user code reading back
 * its policy ints sees what it set even when wrapping a Grid / TextArea.
 *
 * <p>R_leaf_peer_lockdown-locked-down: no protected {@code (Component peer)} ctor — every
 * public ctor funnels through a private {@code (SJScrollPane)} ctor that
 * hard-codes the surrogate. {@link javax.swing.JScrollPane} is a leaf in
 * the public {@code javax.swing.*} hierarchy, so user-code subclasses
 * extend the public form and inherit the locked-in peer.
 *
 * <p>{@link JViewport} and {@link JScrollBar} are emulator-only shadow
 * JDK components (per D_viewport_scrollbar_shadows — Vaadin has no separate viewport object;
 * Vaadin Scroller's web component owns scrollbar rendering internally).
 * The shadow ports field-shadow their JDK state for round-trip but never
 * fire listeners (no Vaadin source for scroll-position events without a
 * custom DOM stack — R_match_swing_errors sub-bucket (a) until a migration target asks).
 */
public class JScrollPane extends vaadinx.swing.JComponent
        implements javax.swing.ScrollPaneConstants, javax.accessibility.Accessible {

    // --- Field shadows (R_swing_is_truth — JDK round-trip, source of truth) -----------

    protected int verticalScrollBarPolicy = VERTICAL_SCROLLBAR_AS_NEEDED;
    protected int horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_AS_NEEDED;

    // The wrapped emulator component (whose peer is the Scroller's content).
    // Tracked so getViewportView() and the JViewport facade can return the
    // emulator-side reference without resolving through EHelper.getEmulator
    // each time.
    private vaadinx.awt.Component viewportView;

    // Shadow JDK components, eagerly constructed in the ctor and added to
    // Container.components via Container.addSlotChild so getComponentCount()
    // matches JDK's post-ctor "viewport + verticalScrollBar + horizontalScrollBar"
    // tree shape (count=3). The Vaadin DOM under the Scroller peer doesn't
    // change — the shadows' inert Div peers are kept out of the peer DOM by
    // the addSlotChild seam.
    protected JViewport viewport;
    protected JScrollBar horizontalScrollBar;
    protected JScrollBar verticalScrollBar;

    // R_vaadin_first round-trip-only fields per D_jscrollpane's accepted-limitations bullet.
    private vaadinx.swing.border.Border viewportBorder;
    private boolean wheelScrollingEnabled = true;

    // --- Constructors (all funnel through the four-arg private ctor) ---

    public JScrollPane() {
        this(null, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    public JScrollPane(vaadinx.awt.Component view) {
        this(view, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
    }

    public JScrollPane(int vsbPolicy, int hsbPolicy) {
        this(null, vsbPolicy, hsbPolicy);
    }

    public JScrollPane(vaadinx.awt.Component view, int vsbPolicy, int hsbPolicy) {
        // R_leaf_peer_lockdown funnel: locked SJScrollPane peer chosen by the framework;
        // user-code subclasses inherit it without an escape hatch.
        super(new SJScrollPane());
        validatePolicy(vsbPolicy, /*vertical*/ true);
        validatePolicy(hsbPolicy, /*vertical*/ false);
        this.verticalScrollBarPolicy = vsbPolicy;
        this.horizontalScrollBarPolicy = hsbPolicy;
        // Eagerly install shadow JDK children so Container.components matches
        // JDK's post-ctor tree shape: [viewport, verticalScrollBar,
        // horizontalScrollBar]. Order mirrors OpenJDK's JScrollPane ctor,
        // which calls setViewport → setVerticalScrollBar → setHorizontalScrollBar.
        this.viewport = new JViewport(this);
        this.verticalScrollBar = new JScrollBar(java.awt.Adjustable.VERTICAL);
        this.horizontalScrollBar = new JScrollBar(java.awt.Adjustable.HORIZONTAL);
        addSlotChild(viewport, -1);
        addSlotChild(verticalScrollBar, -1);
        addSlotChild(horizontalScrollBar, -1);
        if (view != null) {
            setViewportView(view);
        } else {
            // No content yet — propagate the chosen ScrollDirection to the
            // empty Scroller. Auto-scroll guard is a no-op for null content.
            pushPolicyToSurrogate();
        }
    }

    private SJScrollPane surrogate() {
        return (SJScrollPane) getPeer();
    }

    // --- Viewport view (the canonical "what JScrollPane wraps") --------

    /**
     * Set the wrapped component. Field-shadows the emulator-side reference,
     * delegates to {@link SJScrollPane#setContent(com.vaadin.flow.component.Component)}
     * (which applies the auto-scroll guard per SD_sjscrollpane), then re-pushes the
     * scrollbar policy via {@link #pushPolicyToSurrogate} — the push is a
     * no-op when content is auto-scrolling.
     */
    public void setViewportView(vaadinx.awt.Component view) {
        vaadinx.awt.Component old = this.viewportView;
        if (old == view) return;
        // Wire the AWT-side tree first so listeners and getComponents() see
        // the new shape before the peer flips: detach the old view from the
        // shadow JViewport's components, then attach the new one. Matches
        // JDK's JScrollPane → JViewport → view chain.
        if (old != null) viewport.detachView(old);
        this.viewportView = view;
        if (view != null) viewport.attachView(view);
        com.vaadin.flow.component.Component vaadinChild = view != null ? view.getPeer() : null;
        withPeer(p -> surrogate().setContent(vaadinChild));
        // Re-apply policy after content swap: auto-scrolling content forces
        // NONE on the surrogate (already done by setContent's guard); the
        // push below skips for auto-scrolling content so the field-shadowed
        // ints round-trip JDK-faithfully.
        pushPolicyToSurrogate();
        // JDK fires "viewport" PCE when the viewport's view changes; we fire
        // it on the JScrollPane itself since the viewport is a derived holder.
        firePropertyChange("viewport", null, getViewport());
    }

    /** The wrapped emulator component, or {@code null} if none was set. */
    public vaadinx.awt.Component getViewportView() {
        return viewportView;
    }

    // --- JViewport facade (eager, bound to this JScrollPane) ------------

    /**
     * Returns the {@link JViewport} that wraps the viewport view. Eagerly
     * constructed in the ctor (added to {@link vaadinx.awt.Container#components}
     * so the JDK tree shape round-trips). Per D_viewport_scrollbar_shadows, the JViewport is an
     * emulator-only shadow JDK component — its {@code getView} /
     * {@code setView} round-trip the wrapped component, but
     * viewport-position / extent-size APIs are round-trip-only (no Vaadin
     * counterpart).
     */
    public JViewport getViewport() {
        return viewport;
    }

    /**
     * Replace the {@link JViewport}. JDK contract: the new viewport's view
     * becomes the JScrollPane's viewport view. The old viewport is detached
     * from {@link vaadinx.awt.Container#components} and the new one inserted
     * at the same index (viewport always lives at index 0 — JDK ordering).
     */
    public void setViewport(JViewport vp) {
        JViewport old = this.viewport;
        if (old == vp) return;
        // Move the current viewportView from the old JViewport's children
        // to the new one — JDK's view stays attached to the JScrollPane
        // across viewport replacement (its owner-resolved getView() always
        // returns the JScrollPane's viewportView, so independent storage
        // on the shadow JViewport would just duplicate state).
        vaadinx.awt.Component currentView = this.viewportView;
        if (old != null && currentView != null) old.detachView(currentView);
        if (old != null) removeSlotChild(old);
        this.viewport = vp;
        if (vp != null) {
            vp.bindOwner(this);
            addSlotChild(vp, 0);
            if (currentView != null) vp.attachView(currentView);
        }
        firePropertyChange("viewport", old, vp);
    }

    // --- JScrollBar facades (eager, shadow JDK components) -------------

    public JScrollBar getHorizontalScrollBar() {
        return horizontalScrollBar;
    }

    public JScrollBar getVerticalScrollBar() {
        return verticalScrollBar;
    }

    /**
     * Replace the horizontal scrollbar. JDK accepts user-supplied JScrollBar
     * instances (rare; common in subclassed L&amp;F-aware code). The old
     * scrollbar is detached from {@link vaadinx.awt.Container#components}
     * and the new one inserted at the same JDK index (2 — after viewport
     * and verticalScrollBar). No DOM impact: Vaadin Scroller renders its
     * own scrollbar; the shadow's inert Div peer stays out of the peer DOM.
     */
    public void setHorizontalScrollBar(JScrollBar sb) {
        JScrollBar old = this.horizontalScrollBar;
        if (old == sb) return;
        if (old != null) removeSlotChild(old);
        this.horizontalScrollBar = sb;
        if (sb != null) addSlotChild(sb, -1);
        firePropertyChange("horizontalScrollBar", old, sb);
    }

    public void setVerticalScrollBar(JScrollBar sb) {
        JScrollBar old = this.verticalScrollBar;
        if (old == sb) return;
        if (old != null) removeSlotChild(old);
        this.verticalScrollBar = sb;
        // verticalScrollBar lives between viewport (0) and horizontalScrollBar
        // (last) — pin to index 1 to keep JDK order when horizontalScrollBar
        // is still present, append otherwise.
        if (sb != null) {
            int hsbIndex = horizontalScrollBar != null
                    ? java.util.Arrays.asList(getComponents()).indexOf(horizontalScrollBar)
                    : -1;
            addSlotChild(sb, hsbIndex >= 0 ? hsbIndex : -1);
        }
        firePropertyChange("verticalScrollBar", old, sb);
    }

    // --- Scrollbar policy (field-shadow + push-via-mapping) -------------

    public int getVerticalScrollBarPolicy() {
        return verticalScrollBarPolicy;
    }

    public void setVerticalScrollBarPolicy(int policy) {
        validatePolicy(policy, /*vertical*/ true);
        int old = this.verticalScrollBarPolicy;
        if (old == policy) return;
        this.verticalScrollBarPolicy = policy;
        pushPolicyToSurrogate();
        firePropertyChange("verticalScrollBarPolicy", old, policy);
    }

    public int getHorizontalScrollBarPolicy() {
        return horizontalScrollBarPolicy;
    }

    public void setHorizontalScrollBarPolicy(int policy) {
        validatePolicy(policy, /*vertical*/ false);
        int old = this.horizontalScrollBarPolicy;
        if (old == policy) return;
        this.horizontalScrollBarPolicy = policy;
        pushPolicyToSurrogate();
        firePropertyChange("horizontalScrollBarPolicy", old, policy);
    }

    /**
     * R_match_swing_errors-faithful validation: JDK throws IllegalArgumentException on unknown
     * policy ints. The validate-call sites are the two setters (and the
     * three-arg ctor), all of which need to fail loudly on bad input —
     * silently mapping unknown to AS_NEEDED would hide real bugs.
     */
    private static void validatePolicy(int policy, boolean vertical) {
        if (vertical) {
            if (policy != VERTICAL_SCROLLBAR_AS_NEEDED
                    && policy != VERTICAL_SCROLLBAR_NEVER
                    && policy != VERTICAL_SCROLLBAR_ALWAYS) {
                throw new IllegalArgumentException("invalid verticalScrollBarPolicy");
            }
        } else {
            if (policy != HORIZONTAL_SCROLLBAR_AS_NEEDED
                    && policy != HORIZONTAL_SCROLLBAR_NEVER
                    && policy != HORIZONTAL_SCROLLBAR_ALWAYS) {
                throw new IllegalArgumentException("invalid horizontalScrollBarPolicy");
            }
        }
    }

    /**
     * Translate the field-shadowed {@code (vsbPolicy, hsbPolicy)} pair to a
     * {@link Scroller.ScrollDirection} and push to the surrogate, except
     * when the current viewport content is auto-scrolling (Grid / TextArea /
     * Scroller). The surrogate's auto-scroll guard already forced
     * {@link Scroller.ScrollDirection#NONE} per SD_sjscrollpane; skipping the push lets
     * the field-shadowed ints round-trip without driving a contradictory
     * direction.
     *
     * <p>{@code ALWAYS} on either axis is R_match_swing_errors sub-bucket (a) blocked-upstream
     * (Vaadin Scroller has no force-show affordance) — WARN and treat as
     * {@code AS_NEEDED}.
     */
    private void pushPolicyToSurrogate() {
        com.vaadin.flow.component.Component content = surrogate().getContent();
        if (content != null && isAutoScrollingType(content)) {
            // Auto-scroll guard already forced NONE; skip the policy push.
            return;
        }
        Scroller.ScrollDirection dir = translatePolicy(verticalScrollBarPolicy, horizontalScrollBarPolicy);
        withPeer(p -> surrogate().setScrollDirection(dir));
    }

    private static boolean isAutoScrollingType(com.vaadin.flow.component.Component content) {
        return content instanceof com.vaadin.flow.component.grid.Grid
                || content instanceof com.vaadin.flow.component.textfield.TextArea
                || content instanceof Scroller;
    }

    private static Scroller.ScrollDirection translatePolicy(int v, int h) {
        // 9-case cross-product per D_scrollbar_policy_mapping. ALWAYS WARNs and falls through to
        // AS_NEEDED — Vaadin Scroller has no force-show affordance.
        if (v == VERTICAL_SCROLLBAR_ALWAYS) {
            vaadinx.EHelper.onUnimplemented("JScrollPane", "setVerticalScrollBarPolicy.ALWAYS",
                    "Vaadin Scroller has no force-show; treating as AS_NEEDED.");
            v = VERTICAL_SCROLLBAR_AS_NEEDED;
        }
        if (h == HORIZONTAL_SCROLLBAR_ALWAYS) {
            vaadinx.EHelper.onUnimplemented("JScrollPane", "setHorizontalScrollBarPolicy.ALWAYS",
                    "Vaadin Scroller has no force-show; treating as AS_NEEDED.");
            h = HORIZONTAL_SCROLLBAR_AS_NEEDED;
        }
        boolean noV = v == VERTICAL_SCROLLBAR_NEVER;
        boolean noH = h == HORIZONTAL_SCROLLBAR_NEVER;
        if (noV && noH) return Scroller.ScrollDirection.NONE;
        if (noV) return Scroller.ScrollDirection.HORIZONTAL;
        if (noH) return Scroller.ScrollDirection.VERTICAL;
        return Scroller.ScrollDirection.BOTH;
    }

    // --- Viewport border (round-trip-only per D_jscrollpane) ----------------------

    public void setViewportBorder(vaadinx.swing.border.Border viewportBorder) {
        // JDK has a separate viewport-border slot (between the scrollpane's
        // own border and the viewport content). Vaadin Scroller's single host
        // element doesn't reproduce this dual-border surface — WARN and store
        // for round-trip. Migrators wanting a real border should use
        // setBorder on the JScrollPane itself.
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setViewportBorder", viewportBorder);
        vaadinx.swing.border.Border old = this.viewportBorder;
        this.viewportBorder = viewportBorder;
        firePropertyChange("viewportBorder", old, viewportBorder);
    }

    public vaadinx.swing.border.Border getViewportBorder() {
        return viewportBorder;
    }

    // --- Headers / corners (R_vaadin_first drop-and-WARN) ---------------------------

    public void setColumnHeaderView(vaadinx.awt.Component view) {
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setColumnHeaderView", view);
    }

    public void setRowHeaderView(vaadinx.awt.Component view) {
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setRowHeaderView", view);
    }

    /**
     * Stores the header viewport and fires {@code "columnHeader"}; nothing is
     * rendered. A Vaadin {@code Scroller} has one content slot and no
     * header/corner grid, so the JDK's {@code add(columnHeader, COLUMN_HEADER)}
     * has no target — the declined effect. State and notification are owed
     * regardless (R_decline_effect_only, D_owed_events).
     */
    public void setColumnHeader(JViewport columnHeader) {
        JViewport old = this.columnHeader;
        this.columnHeader = columnHeader;
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setColumnHeader(render)", columnHeader);
        firePropertyChange("columnHeader", old, columnHeader);
    }

    public JViewport getColumnHeader() {
        return columnHeader;
    }

    /** Stores and fires; nothing is rendered — see {@link #setColumnHeader}. */
    public void setRowHeader(JViewport rowHeader) {
        JViewport old = this.rowHeader;
        this.rowHeader = rowHeader;
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setRowHeader(render)", rowHeader);
        firePropertyChange("rowHeader", old, rowHeader);
    }

    public JViewport getRowHeader() {
        return rowHeader;
    }

    // Header viewports: stored so the getters and the bound properties are
    // honest, never rendered (R_decline_effect_only, D_owed_events).
    protected JViewport columnHeader;
    protected JViewport rowHeader;

    // Corner components: stored so the getters and the bound properties are
    // honest, never rendered — same R_decline_effect_only shape as the header
    // viewports above (D_instance_field_surface).
    protected vaadinx.awt.Component lowerLeft;
    protected vaadinx.awt.Component lowerRight;
    protected vaadinx.awt.Component upperLeft;
    protected vaadinx.awt.Component upperRight;

    /**
     * Stores the corner and fires the key-named bound property; nothing is
     * rendered — see {@link #setColumnHeader}. Invalid keys throw
     * {@code IllegalArgumentException} as in the JDK (R_match_swing_errors).
     */
    public void setCorner(String key, vaadinx.awt.Component corner) {
        vaadinx.awt.Component old;
        key = resolveCornerKey(key);
        switch (key) {
            case LOWER_LEFT_CORNER  -> { old = lowerLeft;  lowerLeft = corner; }
            case LOWER_RIGHT_CORNER -> { old = lowerRight; lowerRight = corner; }
            case UPPER_LEFT_CORNER  -> { old = upperLeft;  upperLeft = corner; }
            case UPPER_RIGHT_CORNER -> { old = upperRight; upperRight = corner; }
            default -> throw new IllegalArgumentException("invalid corner key");
        }
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setCorner(render)", key, corner);
        firePropertyChange(key, old, corner);
    }

    public vaadinx.awt.Component getCorner(String key) {
        return switch (resolveCornerKey(key)) {
            case LOWER_LEFT_CORNER  -> lowerLeft;
            case LOWER_RIGHT_CORNER -> lowerRight;
            case UPPER_LEFT_CORNER  -> upperLeft;
            case UPPER_RIGHT_CORNER -> upperRight;
            default -> null; // JDK getCorner answers null for an unknown key
        };
    }

    /** JDK key resolution: LEADING/TRAILING → LEFT/RIGHT by component orientation. */
    private String resolveCornerKey(String key) {
        boolean ltr = getComponentOrientation().isLeftToRight();
        if (key.equals(LOWER_LEADING_CORNER))  return ltr ? LOWER_LEFT_CORNER : LOWER_RIGHT_CORNER;
        if (key.equals(LOWER_TRAILING_CORNER)) return ltr ? LOWER_RIGHT_CORNER : LOWER_LEFT_CORNER;
        if (key.equals(UPPER_LEADING_CORNER))  return ltr ? UPPER_LEFT_CORNER : UPPER_RIGHT_CORNER;
        if (key.equals(UPPER_TRAILING_CORNER)) return ltr ? UPPER_RIGHT_CORNER : UPPER_LEFT_CORNER;
        return key;
    }

    // --- Wheel scrolling (always-on in browsers; round-trip-only) ------

    public void setWheelScrollingEnabled(boolean handleWheel) {
        // Browsers always handle wheel scrolling on overflow:auto containers.
        // Field round-trip; no peer drive. Silent (onNoop) — apps routinely
        // pair setVisible(true) with setWheelScrollingEnabled(true) and
        // logging would swamp.
        boolean old = this.wheelScrollingEnabled;
        this.wheelScrollingEnabled = handleWheel;
        firePropertyChange("wheelScrollingEnabled", old, handleWheel);
    }

    public boolean isWheelScrollingEnabled() {
        return wheelScrollingEnabled;
    }

    // --- Layout (single-content-slot — JDK ScrollPaneLayout not modelled) -

    /**
     * Accepts only {@code null}, throwing the JDK's own {@code ClassCastException} and
     * message for anything else — {@code javax.swing.JScrollPane.setLayout} takes a
     * {@code ScrollPaneLayout} or {@code null} and rejects the rest, so tolerating a
     * {@code BorderLayout} here would let code run that fails on the desktop
     * (R_match_swing_errors).
     *
     * <p>The JDK's accept-branch has nothing to accept, and costs nothing: SB-Emulators
     * ports no {@code ScrollPaneLayout} (its viewport / header / corner orchestration is
     * R_vaadin_first drop-and-WARN per D_jscrollpane, the peer being one content slot),
     * and {@code javax.swing.ScrollPaneLayout} is not a {@code vaadinx.awt.LayoutManager},
     * so a migrator's surviving call would not compile either way.
     *
     * @param mgr must be {@code null}
     * @throws ClassCastException for any non-{@code null} manager
     */
    @Override
    public void setLayout(vaadinx.awt.LayoutManager mgr) {
        if (mgr != null) {
            throw new ClassCastException("layout of JScrollPane must be a ScrollPaneLayout");
        }
        super.setLayout(mgr);
    }

    /**
     * setViewportView attaches the view through {@code Scroller.setContent}
     * (one-content-slot per SD_sjscrollpane), bypassing {@code Container.addImpl} — the
     * view never enters this JScrollPane's {@code components} list. The
     * inherited {@link vaadinx.awt.Container#validateTree} walks
     * {@code components} only, so without this override an inner panel
     * created with {@code new JPanel()} then {@code setLayout(new BoxLayout(...))}
     * keeps the FlowLayout CSS the SJPanel ctor wrote at construction time —
     * its BoxLayout never gets a layoutContainer pass. Recursing into the
     * viewportView closes that gap.
     */
    @Override
    protected void validateTree() {
        super.validateTree();
        if (viewportView != null) {
            viewportView.validate();
        }
    }

    // --- L&F surface ----------------------------------------------------

    public String getUIClassID() {
        return "ScrollPaneUI";
    }

    public void updateUI() {
        // L&F swap — no-op; Vaadin owns the DOM. Same shape JPanel.updateUI.
    }

    public javax.swing.plaf.ScrollPaneUI getUI() {
        vaadinx.EHelper.onUnimplemented("JScrollPane", "getUI");
        return null;
    }

    public void setUI(javax.swing.plaf.ScrollPaneUI ui) {
        vaadinx.EHelper.onUnimplemented("JScrollPane", "setUI", ui);
    }

    // --- Accessibility (deferred per project-wide stance) ---------------

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JScrollPane", "getAccessibleContext");
        return null;
    }
}
