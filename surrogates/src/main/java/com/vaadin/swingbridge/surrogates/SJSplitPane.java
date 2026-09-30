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
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

/**
 * Surrogate for {@link javax.swing.JSplitPane} (SD_sjsplitpane).
 * Two-slot resizable splitter on top of Vaadin {@link SplitLayout} —
 * primary/secondary slots map directly onto JDK's left/right
 * (HORIZONTAL_SPLIT) or top/bottom (VERTICAL_SPLIT) alias pairs.
 *
 * <h2>API partition (per SD_sjsplitpane)</h2>
 *
 * <p>Methods whose UI behavior Vaadin can reproduce are real impls
 * (orientation, slot setters, {@code setDividerLocation(double 0–1)},
 * {@code resetToPreferredSizes}). The cluster Vaadin doesn't expose
 * Java-side — {@code setResizeWeight}, {@code setOneTouchExpandable},
 * {@code setContinuousLayout}, {@code setDividerSize},
 * {@code setLastDividerLocation}, and the int-pixel
 * {@code setDividerLocation(int)} form — is present-but-drops per R_vaadin_first:
 * setters log {@code SHelper.onUnimplemented} and discard the value;
 * getters return the JDK default (no {@code Store} shadow). Round-trip
 * + PCE for these knobs lives on the emulator side per
 * {@code vaadinx.swing.JSplitPane}.
 *
 * <h2>Orientation mapping</h2>
 *
 * JDK constants: {@link #HORIZONTAL_SPLIT} (= 1, side-by-side, left|right)
 * and {@link #VERTICAL_SPLIT} (= 0, stacked, top/bottom). Vaadin
 * {@link SplitLayout.Orientation#HORIZONTAL} matches JDK
 * HORIZONTAL_SPLIT — naming-convention align (both mean "the layout's
 * axis is horizontal," so children sit side-by-side).
 */
public class SJSplitPane extends SplitLayout implements JComponentMixin {

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

    /** JDK vertical-split sentinel — children stack top/bottom. */
    public static final int VERTICAL_SPLIT = 0;
    /** JDK horizontal-split sentinel — children sit side-by-side. */
    public static final int HORIZONTAL_SPLIT = 1;

    // ---- Constructors ----

    public SJSplitPane() {
        this(HORIZONTAL_SPLIT);
    }

    public SJSplitPane(int orientation) {
        super(toVaadinOrientation(orientation));
        _installSwingClass();
    }

    /**
     * Translate JDK {@link #HORIZONTAL_SPLIT} / {@link #VERTICAL_SPLIT}
     * to Vaadin {@link SplitLayout.Orientation}. Throws
     * {@link IllegalArgumentException} on garbage int per R_match_swing_errors (matches
     * JDK's contract).
     */
    private static SplitLayout.Orientation toVaadinOrientation(int orientation) {
        if (orientation == HORIZONTAL_SPLIT) return SplitLayout.Orientation.HORIZONTAL;
        if (orientation == VERTICAL_SPLIT) return SplitLayout.Orientation.VERTICAL;
        throw new IllegalArgumentException(
                "orientation must be one of: HORIZONTAL_SPLIT, VERTICAL_SPLIT");
    }

    // ---- Orientation (JDK int form on top of inherited enum API) ----

    /** Returns JDK {@link #HORIZONTAL_SPLIT} or {@link #VERTICAL_SPLIT}. */
    public int getOrientationAsInt() {
        return getOrientation() == SplitLayout.Orientation.HORIZONTAL
                ? HORIZONTAL_SPLIT : VERTICAL_SPLIT;
    }

    /**
     * JDK-shaped int setter. Validates per R_match_swing_errors, forwards to the inherited
     * {@link SplitLayout#setOrientation(SplitLayout.Orientation)}, fires
     * {@code "orientation"} PCE on actual change.
     */
    public void setOrientation(int orientation) {
        SplitLayout.Orientation newO = toVaadinOrientation(orientation);
        SplitLayout.Orientation old = getOrientation();
        if (old == newO) return;
        super.setOrientation(newO);
        int oldInt = old == SplitLayout.Orientation.HORIZONTAL
                ? HORIZONTAL_SPLIT : VERTICAL_SPLIT;
        firePropertyChange("orientation", oldInt, orientation);
    }

    // ---- Slot setters / getters (addToPrimary / addToSecondary aliases) ----

    /**
     * Set the primary slot occupant. JDK {@code HORIZONTAL_SPLIT} reads
     * this as the left child; {@code VERTICAL_SPLIT} reads it as the top
     * child. Replaces any existing primary occupant per Vaadin's
     * {@code setComponent(c, slotName)} semantics.
     */
    public void setLeftComponent(Component c) {
        if (c == null) {
            Component existing = getPrimaryComponent();
            if (existing != null) remove(existing);
            return;
        }
        addToPrimary(c);
    }

    /** Alias for {@link #setLeftComponent} matching the JDK VERTICAL_SPLIT name. */
    public void setTopComponent(Component c) {
        setLeftComponent(c);
    }

    /** The current primary-slot occupant (left under HORIZONTAL_SPLIT, top under VERTICAL_SPLIT). */
    public Component getLeftComponent() {
        return getPrimaryComponent();
    }

    /** Alias for {@link #getLeftComponent}. */
    public Component getTopComponent() {
        return getPrimaryComponent();
    }

    /**
     * Set the secondary slot occupant. JDK {@code HORIZONTAL_SPLIT} reads
     * this as the right child; {@code VERTICAL_SPLIT} reads it as the
     * bottom child. Replaces any existing secondary occupant.
     */
    public void setRightComponent(Component c) {
        if (c == null) {
            Component existing = getSecondaryComponent();
            if (existing != null) remove(existing);
            return;
        }
        addToSecondary(c);
    }

    /** Alias for {@link #setRightComponent} matching the JDK VERTICAL_SPLIT name. */
    public void setBottomComponent(Component c) {
        setRightComponent(c);
    }

    /** The current secondary-slot occupant. */
    public Component getRightComponent() {
        return getSecondaryComponent();
    }

    /** Alias for {@link #getRightComponent}. */
    public Component getBottomComponent() {
        return getSecondaryComponent();
    }

    // ---- Divider location (proportional form: real; int form: drop-and-WARN) ----

    /**
     * Proportional divider position, 0.0–1.0. Multiplies by 100 and
     * forwards to {@link SplitLayout#setSplitterPosition}. No PCE — JDK
     * fires {@code "dividerLocation"} with Integer pixels, which we
     * can't translate from a proportion without container width
     * (R_layouts_close_enough: no server-side coordinates).
     */
    public void setDividerLocation(double proportional) {
        setSplitterPosition(proportional * 100.0);
    }

    /**
     * Int-pixel form — drop-and-WARN per R_match_swing_errors sub-bucket (a) blocked-upstream.
     * Vaadin's splitter is percent-only; we have no container-width to
     * translate. Round-trip + PCE live on the emulator's field shadow.
     */
    public void setDividerLocation(int pixels) {
        SHelper.onUnimplemented(this, "setDividerLocation(int)", pixels);
    }

    /** Returns the JDK sentinel -1 ("not set / compute from children"). Round-trip lives on the emulator. */
    public int getDividerLocation() {
        return -1;
    }

    /** Drop-and-WARN — no server-side coordinates (R_layouts_close_enough). */
    public int getMinimumDividerLocation() {
        SHelper.onUnimplemented(this, "getMinimumDividerLocation");
        return -1;
    }

    /** Drop-and-WARN — no server-side coordinates (R_layouts_close_enough). */
    public int getMaximumDividerLocation() {
        SHelper.onUnimplemented(this, "getMaximumDividerLocation");
        return -1;
    }

    /**
     * Snap divider to a neutral 50/50 position — best-effort R_best_effort_behaviour for JDK's
     * "compute from child preferred sizes," which we have neither the
     * preferred-size machinery nor the pixel coordinates (R_layouts_close_enough) to honor.
     */
    public void resetToPreferredSizes() {
        setSplitterPosition(50.0);
    }

    // ---- No-counterpart cluster — present-but-drops (round-trip on emulator) ----

    /**
     * No Vaadin counterpart. Drop-and-WARN; round-trip + PCE on the
     * emulator's field shadow. JDK uses the value to weight extra space
     * during container resize — Vaadin's primary/secondary stretch by
     * their own flex rules.
     */
    public void setResizeWeight(double weight) {
        SHelper.onUnimplemented(this, "setResizeWeight", weight);
    }

    /** Returns the JDK default 0.0. Round-trip lives on the emulator. */
    public double getResizeWeight() {
        return 0.0;
    }

    /**
     * No Vaadin counterpart (no one-touch chevrons on SplitLayout's
     * splitter). Drop-and-WARN; round-trip + PCE on the emulator.
     */
    public void setOneTouchExpandable(boolean b) {
        SHelper.onUnimplemented(this, "setOneTouchExpandable", b);
    }

    /** Returns the JDK default false. Round-trip lives on the emulator. */
    public boolean isOneTouchExpandable() {
        return false;
    }

    /**
     * No Vaadin counterpart (SplitLayout only relays children on
     * drag-end). Drop-and-WARN; round-trip + PCE on the emulator.
     */
    public void setContinuousLayout(boolean b) {
        SHelper.onUnimplemented(this, "setContinuousLayout", b);
    }

    /** Returns the JDK default false. Round-trip lives on the emulator. */
    public boolean isContinuousLayout() {
        return false;
    }

    /**
     * No Vaadin Java-side counterpart (splitter thickness is theme-controlled).
     * Drop-and-WARN; round-trip + PCE on the emulator.
     */
    public void setDividerSize(int size) {
        SHelper.onUnimplemented(this, "setDividerSize", size);
    }

    /** Returns 0 (no L&amp;F-installed default). Round-trip lives on the emulator. */
    public int getDividerSize() {
        return 0;
    }

    /**
     * No-op; round-trip + PCE on the emulator. JDK uses the value to
     * snap the divider back on toggle.
     */
    public void setLastDividerLocation(int location) {
        SHelper.onUnimplemented(this, "setLastDividerLocation", location);
    }

    /** Returns the JDK sentinel -1. Round-trip lives on the emulator. */
    public int getLastDividerLocation() {
        return -1;
    }

    // ---- L&F surface ----

    /** Swing constant for L&amp;F lookup; SplitPaneUI isn't pluggable here. */
    public String getUIClassID() {
        return "SplitPaneUI";
    }

    /** L&amp;F swap — no-op for us; Vaadin owns the DOM. */
    public void updateUI() {
        // Same shape as SJPanel.updateUI / SJToolBar.updateUI.
    }

    /** L&amp;F surgery isn't modelled; WARN so integration gaps surface. */
    public void setUI(javax.swing.plaf.SplitPaneUI ui) {
        SHelper.onUnimplemented(this, "setUI", ui);
    }

    // getUI() omitted on the surrogate — Vaadin Component.getUI() returns
    // Optional<UI> and the return-type clash is unrecoverable. JDK
    // getUI() lives on the emulator's vaadinx.swing.JSplitPane.

    // ---- Accessibility (deferred per surrogate-wide stance) ----

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
