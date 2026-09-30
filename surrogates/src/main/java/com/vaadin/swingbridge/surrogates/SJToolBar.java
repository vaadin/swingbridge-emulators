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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Style;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import javax.swing.SwingConstants;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.util.Objects;

/**
 * Surrogate for {@link javax.swing.JToolBar} (SD_sjtoolbar).
 * Action-button row on top of Vaadin {@link Div} — same peer SJPanel /
 * SJRootPane use. The toolbar is a free-standing flex container, not a
 * Vaadin {@code HorizontalLayout}: the orientation flip
 * ({@code HORIZONTAL} ↔ {@code VERTICAL}) is symmetric on a plain Div
 * (just a CSS {@code flex-direction} toggle), and using HorizontalLayout
 * would force a "horizontal layout that's actually rendering vertically"
 * misnomer the moment a user calls {@code setOrientation(VERTICAL)}.
 *
 * <h2>Pinned flex CSS</h2>
 *
 * The ctor writes the toolbar's flex CSS directly on the host element:
 * {@code display:inline-flex; align-items:center; gap:var(--vaadin-gap-xs);
 * flex-direction:row|column} per orientation. We do not go through the
 * inherited {@link com.vaadin.swingbridge.surrogates.awt.ContainerMixin#setLayout} CSS
 * dispatch — that's parameterised on JDK {@link java.awt.FlowLayout} /
 * {@link java.awt.BorderLayout} / BoxLayout / GridBagLayout instances and
 * the toolbar wants none of those.
 *
 * <p>{@link #setLayout(LayoutManager)} is overridden to drop-and-WARN per
 * R_vaadin_first (we hard-pin flex flow per orientation). Migrators calling
 * {@code toolbar.setLayout(new BorderLayout())} would otherwise have the
 * Border-grid CSS clobber the toolbar's flex CSS — a silent visual
 * regression. Pinning is the safer default; if a real migration target
 * needs custom toolbar layouts we lift the restriction then.
 *
 * <h2>Orientation flip (HORIZONTAL ↔ VERTICAL)</h2>
 *
 * Field source-of-truth + CSS write on every change. {@link #setOrientation}
 * validates per R_match_swing_errors ({@link IllegalArgumentException} matches JDK) and
 * fires {@code "orientation"} PCE on actual change.
 *
 * <h2>{@code addSeparator} — inline perpendicular thin Div</h2>
 *
 * The toolbar owns its separators inline rather than reaching for the
 * {@link SJSeparator} surrogate (SD_sjtoolbar): the 1px-stretch trick is a
 * one-liner and keeps the child a plain toolbar-managed {@code <div>}.
 * {@link #addSeparator()} builds a {@code <div>} sized to 1px on the
 * toolbar's cross axis with {@code align-self:stretch} and adds it —
 * the same visual shape SJSeparator produces as a standalone flex child.
 * The one-arg form {@link #addSeparator(java.awt.Dimension)} accepts the
 * dimension and drops it per R_layouts_close_enough — JDK uses the size as a spacing hint;
 * we always paint a thin bar.
 *
 * <h2>{@code setMargin(Insets)} → CSS padding</h2>
 *
 * Maps {@code Insets(t,l,b,r)} to {@code padding: t r b l} on the host
 * element (the CSS shorthand order). {@link #getMargin()} reads the
 * computed {@code padding-top/right/bottom/left} back; null margin
 * removes the property entirely (matches JDK's {@code setMargin(null)}
 * semantics of "use L&amp;F default", which here means "no padding").
 *
 * <h2>What lives on the emulator only (asymmetric R_swing_is_truth shadow)</h2>
 *
 * {@code floatable}, {@code rollover}, and {@code borderPainted} have no
 * Vaadin counterpart — no detachable toolbars in a browser, no Vaadin
 * rollover toggle, no painted-border flag distinct from CSS border. R_vaadin_first
 * says drop them on the surrogate side: the methods are not declared
 * here at all. Round-trip lives on the emulator
 * ({@code :emulators.JToolBar}'s own field shadows, per R_swing_is_truth — same shape
 * SD_sjbutton takes for state-conditional icons and SD_toggle_checkbox_first_cut takes for
 * {@code borderPaintedFlat}). Pure-surrogate Vaadin-shape users don't
 * reach for these.
 */
public class SJToolBar extends Div implements JComponentMixin {

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

    // ---- Swing-side state (source of truth, R_swing_is_truth) ----

    /** JDK default orientation. */
    private int orientation = SwingConstants.HORIZONTAL;

    /** Last-set margin Insets, null until {@link #setMargin} is called. */
    private Insets margin;

    // ---- Constructors ----

    public SJToolBar() {
        this(SwingConstants.HORIZONTAL);
    }

    public SJToolBar(int orientation) {
        super();
        if (orientation != SwingConstants.HORIZONTAL && orientation != SwingConstants.VERTICAL) {
            throw new IllegalArgumentException(
                    "orientation must be one of: HORIZONTAL, VERTICAL");
        }
        this.orientation = orientation;
        _installSwingClass();
        applyPinnedFlexCss();
    }

    /**
     * Write the toolbar's pinned flex CSS to the host element. Called
     * once from the ctor and again from {@link #setOrientation} when the
     * direction flips. Composed as a whole rather than diffing which key
     * changed so a future addition (e.g. {@code gap} tweak) can't leave
     * stale keys behind.
     */
    private void applyPinnedFlexCss() {
        Style s = getElement().getStyle();
        s.set("display", "inline-flex");
        s.set("flex-direction", orientation == SwingConstants.VERTICAL ? "column" : "row");
        s.set("align-items", "center");
        s.set("gap", "var(--vaadin-gap-xs)");
    }

    // ---- Orientation (HORIZONTAL / VERTICAL via CSS flex-direction) ----

    public int getOrientation() {
        return orientation;
    }

    public void setOrientation(int orientation) {
        if (orientation != SwingConstants.HORIZONTAL && orientation != SwingConstants.VERTICAL) {
            throw new IllegalArgumentException(
                    "orientation must be one of: HORIZONTAL, VERTICAL");
        }
        int old = this.orientation;
        if (old == orientation) return;
        this.orientation = orientation;
        applyPinnedFlexCss();
        firePropertyChange("orientation", old, orientation);
    }

    // ---- setLayout — pinned flex, drop-and-WARN any user override ----

    /**
     * Hard-pinned flex flow per orientation. R_vaadin_first drop-and-WARN — see
     * class javadoc §"Pinned flex CSS" for why we don't honour user
     * {@link LayoutManager} swaps. {@code null} is silently accepted
     * since {@code Container.removeAll} and similar JDK code paths pass
     * it as a normal teardown step.
     */
    @Override
    public void setLayout(LayoutManager mgr) {
        if (mgr != null) {
            SHelper.onUnimplemented(this, "setLayout", mgr);
        }
    }

    // ---- addSeparator (perpendicular thin Div) ----

    /**
     * Push a thin bar oriented perpendicular to the toolbar axis. Same
     * visual shape an {@code SJSeparator} has as a flex child of a toolbar
     * row — Lumo-toned 1px line that
     * {@code align-self:stretch}es across the cross axis.
     */
    public void addSeparator() {
        Div sep = new Div();
        sep.getElement().setAttribute("data-emul-toolbar-separator", "");
        Style ss = sep.getElement().getStyle();
        ss.set("background-color", "var(--vaadin-border-color-secondary)");
        ss.set("align-self", "stretch");
        if (orientation == SwingConstants.HORIZONTAL) {
            // Toolbar runs horizontally → separator is a vertical bar.
            ss.set("min-width", "1px");
        } else {
            ss.set("min-height", "1px");
        }
        add(sep);
    }

    /**
     * Dimension-sized separator overload. We accept the dimension and
     * drop it per R_layouts_close_enough (no pixel-accurate layout); the visual output
     * matches {@link #addSeparator()}. JDK uses the size as a gap hint
     * for the L&amp;F-painted separator; migrated code rarely depends on
     * the gap being exactly that many pixels.
     */
    public void addSeparator(java.awt.Dimension size) {
        addSeparator();
    }

    // ---- setMargin → CSS padding ----

    /**
     * Write {@code padding: top right bottom left} on the host element.
     * {@code null} removes the property — matches JDK's "use L&amp;F
     * default" semantics, which here means no padding. Fires
     * {@code "margin"} PCE on actual change.
     */
    public void setMargin(Insets m) {
        Insets old = this.margin;
        if (Objects.equals(old, m)) return;
        this.margin = m;
        Style s = getElement().getStyle();
        if (m == null) {
            s.remove("padding");
        } else {
            s.set("padding",
                    m.top + "px " + m.right + "px " + m.bottom + "px " + m.left + "px");
        }
        firePropertyChange("margin", old, m);
    }

    /** Returns the last-set margin Insets, or {@code null} if never set. */
    public Insets getMargin() {
        return margin;
    }

    // ---- L&F surface ----

    /** Swing constant for L&amp;F lookup; ToolBarUI isn't pluggable here. */
    public String getUIClassID() {
        return "ToolBarUI";
    }

    /** L&amp;F swap — no-op for us; Vaadin owns the DOM. */
    public void updateUI() {
        // Same shape as SJPanel.updateUI (implicit).
    }

    /** L&amp;F surgery isn't modelled; WARN so integration gaps surface. */
    public void setUI(javax.swing.plaf.ToolBarUI ui) {
        SHelper.onUnimplemented(this, "setUI", ui);
    }

    /**
     * JDK JToolBar.paramString appends paintBorder + margin + floatable.
     * None of those round-trip on the surrogate (floatable/borderPainted
     * are emulator-only; margin lives here but is rendered via CSS). The
     * pure-surrogate paramString tail is empty.
     */
    protected String paramString() {
        return "";
    }

    // ---- Accessibility (deferred per surrogate-wide stance) ----

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
