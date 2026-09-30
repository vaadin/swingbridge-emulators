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
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import java.awt.FlowLayout;
import java.awt.LayoutManager;

/**
 * Surrogate for {@link javax.swing.JPanel} (SD_sjpanel). Extends Vaadin
 * {@link Div} directly (is-a) so it reaches the migrator either through
 * {@code :emulators.JPanel} as peer, or as a richer-than-stock Vaadin
 * container in its own right.
 *
 * <h2>Why Div as peer</h2>
 *
 * Bare HasComponents container — no shadow DOM (so the inline-flex /
 * border / opaque / background CSS the inherited
 * {@link JComponentMixin} writes lands on the host element where it
 * actually renders), no Vaadin-specific value/selection/event behaviour
 * to override. Same peer the existing emulator already uses.
 *
 * <h2>Inherited mixin surface</h2>
 *
 * Almost the entire JPanel surface comes from {@link JComponentMixin}:
 * {@link com.vaadin.swingbridge.surrogates.awt.ContainerMixin#setLayout setLayout} +
 * CSS dispatch (recognises {@link FlowLayout} /
 * {@link java.awt.BorderLayout}, custom LayoutManagers store-but-don't-
 * dispatch), {@link JComponentMixin#setOpaque setOpaque} + lossy CSS
 * readback, {@link JComponentMixin#setBorder setBorder}
 * + CSS round-trip (SD_border_css_lossy), {@code add} / {@code remove} via inherited
 * {@code HasComponents}, focus / key / ancestor / mouse / property-change /
 * client-property surface from JComponentMixin. SJPanel itself adds
 * only the four ctors + L&amp;F surface + paramString tail.
 *
 * <h2>Default opaque divergence</h2>
 *
 * JDK JPanel installs {@code opaque=true} via L&amp;F at construction.
 * The mixin's {@code setOpaque} is Vaadin-first (no shadow store) and
 * its readback is lossy CSS-based: a fresh SJPanel with no background
 * set reports {@link JComponentMixin#isOpaque() isOpaque() == false}
 * even after {@code setOpaque(true)} (no background-color CSS to read
 * back). R_vaadin_first-accepted lossy direction; emulator-side {@code :emulators.JPanel}
 * preserves the JDK-faithful {@code true} via field shadow per R_swing_is_truth
 * (two-layer split, same shape SD_sjbutton takes for state-conditional icons
 * and SD_toggle_checkbox_first_cut takes for {@code borderPaintedFlat}).
 */
public class SJPanel extends Div implements JComponentMixin {

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

    public SJPanel() {
        this(new FlowLayout(), true);
    }

    public SJPanel(boolean isDoubleBuffered) {
        this(new FlowLayout(), isDoubleBuffered);
    }

    public SJPanel(LayoutManager layout) {
        this(layout, true);
    }

    public SJPanel(LayoutManager layout, boolean isDoubleBuffered) {
        super();
        _installSwingClass();
        // isDoubleBuffered is accepted-and-dropped per R_layouts_close_enough (no Vaadin
        // counterpart for AWT's double-buffer flag); same stance the
        // emulator's JPanel takes. JDK FlowLayout default reaches the
        // mixin's setLayout which dispatches inline-flex CSS via
        // LayoutCss.flowLayoutCss (D_layout_css_on_content).
        setLayout(layout);
        // setOpaque(true) deliberately NOT called — see class javadoc
        // §"Default opaque divergence". R_vaadin_first-accepted lossy: a fresh
        // SJPanel reports isOpaque() == false; emulator JPanel
        // preserves the JDK-faithful true via field shadow.
    }

    // --- L&F surface (matches :emulators.JPanel + every other surrogate) -

    /** Swing constant for L&amp;F lookup; PanelUI isn't pluggable here. */
    public String getUIClassID() {
        return "PanelUI";
    }

    /** L&amp;F swap — no-op for us; Vaadin owns the DOM. */
    public void updateUI() {
        // Same shape as SJButton.updateUI / :emulators.JPanel.updateUI.
    }

    // Note: no JDK-shaped getUI() — Vaadin Component.getUI() returns
    // Optional<UI>, signatures clash the same way SJButton.getIcon
    // documents per SD_sjbutton. Pure-surrogate users access UI via inherited
    // Component.getUI(); migrated code reaching JPanel.getUI() against
    // a pure SJPanel won't compile (against :emulators.JPanel it works
    // unchanged via the emulator's own field shadow).

    /** L&amp;F surgery isn't modelled; WARN so integration gaps surface. setUI's parameter type doesn't clash with Vaadin's setUI signature (no such method on Div), so the JDK-typed setter survives as a plain method. */
    public void setUI(javax.swing.plaf.PanelUI ui) {
        SHelper.onUnimplemented(this, "setUI", ui);
    }

    /**
     * JDK JPanel.paramString chains up and adds nothing — JPanel has no
     * state of its own beyond what JComponent (opaque, doubleBuffered)
     * and Container (layout=) already report. We don't model
     * AbstractButton/JComponent paramString chains, so the tail is
     * empty here too.
     */
    protected String paramString() {
        return "";
    }

    // --- Accessibility (deferred per surrogate-wide stance) ----------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
