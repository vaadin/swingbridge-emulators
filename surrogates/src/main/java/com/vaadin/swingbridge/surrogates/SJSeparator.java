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

/**
 * Surrogate for {@link javax.swing.JSeparator} (SD_sjseparator) — a standalone
 * visual divider on top of a Vaadin {@link Div} (same peer SJPanel /
 * SJToolBar use).
 *
 * <pre>
 *   var form = new SJPanel();                    // BoxLayout(Y_AXIS)
 *   form.add(nameRow);
 *   form.add(new SJSeparator());                 // full-width Lumo hairline
 *   form.add(addressRow);
 *
 *   var cols = new SJPanel();                     // horizontal row
 *   cols.add(left);
 *   cols.add(new SJSeparator(SwingConstants.VERTICAL)); // full-height bar
 *   cols.add(right);
 * </pre>
 *
 * A separator carries no interactive surface — no clicks, model, value,
 * or event fan-out — so unlike SJButton / SJSlider there is nothing to
 * bridge back to Swing listeners. Its one property is {@code orientation},
 * which drives the CSS in {@link #applyOrientationCss()}: a 1px
 * Lumo-toned bar that {@code align-self:stretch}es to fill the cross axis
 * of any flex parent (BoxLayout, FlowLayout, JToolBar row). A bare
 * {@code new SJSeparator()} is therefore visible without the caller
 * dialing in a preferred size.
 *
 * <p>Menu separators are drawn inline by the menu tree (SD_sjmenubar), and
 * {@code SJToolBar.addSeparator}'s bar by the toolbar itself (SD_sjtoolbar), so
 * neither routes through here.
 *
 * <h2>Cross-axis fill needs a flex parent</h2>
 *
 * {@code align-self:stretch} fills the cross axis in flex layouts
 * (BoxLayout, FlowLayout — the mixin's {@code setLayout} writes
 * {@code inline-flex}). In a BorderLayout region the separator fills its
 * region for the same reason; a GridBagLayout cell that isn't told to
 * fill may render the bar at its 1px intrinsic size — an accepted R_layouts_close_enough
 * cosmetic gap, not a structural one (the divider is still in the right
 * place, just not stretched).
 */
public class SJSeparator extends Div implements JComponentMixin {

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

    /** {@link SwingConstants#HORIZONTAL} or {@code VERTICAL}; JDK default HORIZONTAL. */
    private int orientation = SwingConstants.HORIZONTAL;

    public SJSeparator() {
        this(SwingConstants.HORIZONTAL);
    }

    public SJSeparator(int orientation) {
        super();
        if (orientation != SwingConstants.HORIZONTAL && orientation != SwingConstants.VERTICAL) {
            throw new IllegalArgumentException(
                    "orientation must be one of: HORIZONTAL, VERTICAL");
        }
        this.orientation = orientation;
        _installSwingClass();
        applyOrientationCss();
    }

    public int getOrientation() {
        return orientation;
    }

    /**
     * Sets HORIZONTAL / VERTICAL, repaints the bar, and fires
     * {@code "orientation"} PCE on actual change.
     *
     * @throws IllegalArgumentException on any other value (matches JDK, R_match_swing_errors).
     */
    public void setOrientation(int orientation) {
        if (orientation != SwingConstants.HORIZONTAL && orientation != SwingConstants.VERTICAL) {
            throw new IllegalArgumentException(
                    "orientation must be one of: HORIZONTAL, VERTICAL");
        }
        int old = this.orientation;
        if (old == orientation) return;
        this.orientation = orientation;
        applyOrientationCss();
        firePropertyChange("orientation", old, orientation);
    }

    /**
     * Paint the host Div as a thin Lumo-toned bar. {@code align-self:stretch}
     * fills the cross axis (a VERTICAL separator fills a horizontal row's
     * height, a HORIZONTAL one fills a column's width); the 1px minimum
     * sits on the main axis so the bar stays a hairline. Composed whole
     * (both min-* keys touched every call) so a flip can't leave the prior
     * orientation's key behind.
     */
    private void applyOrientationCss() {
        Style s = getElement().getStyle();
        s.set("background-color", "var(--vaadin-border-color-secondary)");
        s.set("align-self", "stretch");
        if (orientation == SwingConstants.VERTICAL) {
            s.set("min-width", "1px");
            s.remove("min-height");
        } else {
            s.set("min-height", "1px");
            s.remove("min-width");
        }
    }

    // --- L&F surface (matches SJPanel + every other surrogate) -----------

    /** Swing constant for L&amp;F lookup; SeparatorUI isn't pluggable here. */
    public String getUIClassID() {
        return "SeparatorUI";
    }

    /** L&amp;F swap — no-op for us; Vaadin owns the DOM. */
    public void updateUI() {
        // Same shape as SJPanel.updateUI.
    }

    /** L&amp;F surgery isn't modelled; WARN so integration gaps surface. */
    public void setUI(javax.swing.plaf.SeparatorUI ui) {
        SHelper.onUnimplemented(this, "setUI", ui);
    }

    /** JDK JSeparator.paramString reports only orientation, already public. */
    protected String paramString() {
        return "";
    }

    // --- Accessibility (deferred per surrogate-wide stance) --------------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
