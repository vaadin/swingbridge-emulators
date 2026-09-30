/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JSplitPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JSplitPane (D_jsplitpane).
// Thin delegating shell over com.vaadin.swingbridge.surrogates.SJSplitPane — same shape as
// JToolBar / JEditorPane / JPanel over their surrogates.
//
// Field-shadow cluster per the design pass: the six JDK knobs without a
// Vaadin counterpart (dividerLocation int, lastDividerLocation,
// resizeWeight, oneTouchExpandable, continuousLayout, dividerSize) live
// as emulator-side fields. Surrogate-side setters log and drop per SD_sjsplitpane
// "present-but-drops"; the emulator overrides them to write the field +
// fire JDK-shape PCE on change, never touching the peer.
//
// Peer lock-down per R_leaf_peer_lockdown: javax.swing.JSplitPane is a leaf in the public
// Swing hierarchy. Every ctor calls super(new SJSplitPane(orientation))
// directly — no protected (Component peer) ctor. User-code subclasses
// inherit the locked peer.

import com.vaadin.swingbridge.surrogates.SJSplitPane;

/** Emulator for {@link javax.swing.JSplitPane}. See D_jsplitpane. */
public class JSplitPane extends vaadinx.swing.JComponent
        implements javax.accessibility.Accessible, vaadinx.FieldReconciler.Reconcilable {

    /** JDK vertical-split sentinel — children stack top/bottom. */
    public static final int VERTICAL_SPLIT = 0;
    /** JDK horizontal-split sentinel — children sit side-by-side. */
    public static final int HORIZONTAL_SPLIT = 1;

    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final String TOP = "top";
    public static final String BOTTOM = "bottom";
    public static final String DIVIDER = "divider";

    public static final String ORIENTATION_PROPERTY = "orientation";
    public static final String CONTINUOUS_LAYOUT_PROPERTY = "continuousLayout";
    public static final String DIVIDER_SIZE_PROPERTY = "dividerSize";
    public static final String ONE_TOUCH_EXPANDABLE_PROPERTY = "oneTouchExpandable";
    public static final String LAST_DIVIDER_LOCATION_PROPERTY = "lastDividerLocation";
    public static final String DIVIDER_LOCATION_PROPERTY = "dividerLocation";
    public static final String RESIZE_WEIGHT_PROPERTY = "resizeWeight";

    // ---- Field shadows (emulator-only, no peer touch) ----------------
    // dividerLocation int form: JDK fires "dividerLocation" PCE with
    // Integer pixels. Vaadin's splitter is percent-only and we can't
    // faithfully translate without container width (R_layouts_close_enough) — see D_jsplitpane
    // §"setDividerLocation(int pixel)". The field round-trips, the peer
    // stays at its default 50/50 until a setDividerLocation(double) or
    // drag arrives.
    private int dividerLocation = -1;
    protected int lastDividerLocation = -1;
    private double resizeWeight = 0.0;
    protected boolean oneTouchExpandable = false;
    protected boolean continuousLayout = false;
    protected int dividerSize = 0;

    // Slot occupants — field shadow, bypasses Container.components per
    // the JScrollPane viewportView precedent (D_viewport_scrollbar_shadows). getComponentCount()
    // returns 0 even after slot population.
    protected vaadinx.awt.Component leftComponent;
    protected vaadinx.awt.Component rightComponent;

    // ---- Constructors ------------------------------------------------

    public JSplitPane() {
        this(HORIZONTAL_SPLIT, false, null, null);
    }

    public JSplitPane(int orientation) {
        this(orientation, false, null, null);
    }

    public JSplitPane(int orientation, boolean continuousLayout) {
        this(orientation, continuousLayout, null, null);
    }

    public JSplitPane(int orientation, vaadinx.awt.Component left, vaadinx.awt.Component right) {
        this(orientation, false, left, right);
    }

    public JSplitPane(int orientation, boolean continuousLayout,
                      vaadinx.awt.Component left, vaadinx.awt.Component right) {
        // R_leaf_peer_lockdown funnel: SJSplitPane validates orientation per R_match_swing_errors (IAE on
        // garbage int). Re-validating here would double-throw.
        super(new SJSplitPane(orientation));
        this.continuousLayout = continuousLayout;
        if (left != null) setLeftComponent(left);
        if (right != null) setRightComponent(right);
        // Seed the JDK-shaped field (and write-detection baseline) from the peer
        // (D_field_write_reconcile; see JSlider).
        this.orientation = pushedOrientation = surrogate().getOrientationAsInt();
        vaadinx.FieldReconciler.register(this, surrogate());
    }

    // JDK protected field, Swing-side truth per D_field_write_reconcile (see JSlider for the
    // canonical commentary); write-throughs to the surrogate's split direction.
    protected int orientation;

    // Last value pushed to the peer — reconcileFields()'s write-detection baseline.
    private int pushedOrientation;

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (orientation != pushedOrientation) {
            surrogate().setOrientation(orientation);
            pushedOrientation = orientation;
            vaadinx.FieldReconciler.reportDirectWrite(this, "orientation", "setOrientation");
        }
    }

    private SJSplitPane surrogate() {
        return (SJSplitPane) getPeer();
    }

    // ---- Orientation ------------------------------

    public int getOrientation() {
        return orientation;
    }

    /**
     * The surrogate validates the argument and applies the split; the
     * {@code "orientation"} fire has to happen here too. The surrogate's own
     * fire lands on the surrogate's listener list, which a migrator holding a
     * JSplitPane never subscribes to — the two layers fan out independently
     * (D_owed_events).
     */
    public void setOrientation(int orientation) {
        // JDK validates before any state change (IAE); replicated here so the field is
        // never left holding a value the peer rejected.
        if (orientation != HORIZONTAL_SPLIT && orientation != VERTICAL_SPLIT) {
            throw new IllegalArgumentException(
                    "JSplitPane: orientation must be one of: HORIZONTAL_SPLIT, VERTICAL_SPLIT");
        }
        int old = this.orientation;
        this.orientation = orientation;
        withPeer(p -> surrogate().setOrientation(orientation));
        pushedOrientation = orientation;
        firePropertyChange(ORIENTATION_PROPERTY, old, orientation);
    }

    // ---- Slot setters / getters (forwards to peer + field shadow) ----

    /**
     * Set the primary slot occupant. JDK {@code HORIZONTAL_SPLIT} reads
     * this as the left child; {@code VERTICAL_SPLIT} reads it as the top
     * child. Field-shadows the emulator-side reference, delegates to the
     * surrogate's slot setter, and maintains {@link vaadinx.awt.Container#components}
     * via the slot-child seam (D_jsplitpane_slot_tree_shape — JDK tree-shape fidelity, sibling fix
     * to [D_shadow_children_tree_shape](#sub-decision-d35d--shadow-children-populate-containercomponents-for-r7-tree-shape-fidelity)).
     * {@code null} clears the slot — the old occupant is detached.
     */
    public void setLeftComponent(vaadinx.awt.Component c) {
        vaadinx.awt.Component old = this.leftComponent;
        if (c == old) return;
        if (old != null) removeSlotChild(old);
        this.leftComponent = c;
        com.vaadin.flow.component.Component vc = c != null ? c.getPeer() : null;
        withPeer(p -> surrogate().setLeftComponent(vc));
        if (c != null) addSlotChild(c, -1);
    }

    public void setTopComponent(vaadinx.awt.Component c) {
        setLeftComponent(c);
    }

    public vaadinx.awt.Component getLeftComponent() {
        return leftComponent;
    }

    public vaadinx.awt.Component getTopComponent() {
        return leftComponent;
    }

    public void setRightComponent(vaadinx.awt.Component c) {
        vaadinx.awt.Component old = this.rightComponent;
        if (c == old) return;
        if (old != null) removeSlotChild(old);
        this.rightComponent = c;
        com.vaadin.flow.component.Component vc = c != null ? c.getPeer() : null;
        withPeer(p -> surrogate().setRightComponent(vc));
        if (c != null) addSlotChild(c, -1);
    }

    public void setBottomComponent(vaadinx.awt.Component c) {
        setRightComponent(c);
    }

    public vaadinx.awt.Component getRightComponent() {
        return rightComponent;
    }

    public vaadinx.awt.Component getBottomComponent() {
        return rightComponent;
    }

    // ---- addImpl constraint dispatch ---------------------------------

    /**
     * JDK JSplitPane.addImpl routes constraint strings (LEFT/RIGHT/TOP/
     * BOTTOM/DIVIDER) into the slot setters. We mirror that — null
     * constraint with no left yet → left; null constraint with left but
     * no right → right; otherwise drop-and-WARN.
     *
     * <p>Per D_jsplitpane_slot_tree_shape the slot setters maintain {@code Container.components}
     * via the slot-child seam, so {@code getComponentCount()} matches
     * JDK (2 with both slots filled). Vaadin SplitLayout owns the peer
     * DOM placement via {@code setPrimaryComponent} / {@code setSecondaryComponent};
     * the AWT-side {@code components} list and the Vaadin-side slots are
     * separate edges, both maintained per R_swing_is_truth.
     */
    @Override
    protected void addImpl(vaadinx.awt.Component comp, Object constraints, int index) {
        if (constraints == null) {
            if (leftComponent == null) {
                setLeftComponent(comp);
            } else if (rightComponent == null) {
                setRightComponent(comp);
            } else {
                vaadinx.EHelper.onUnimplemented("JSplitPane",
                        "addImpl/no-slot-available", comp, constraints, index);
            }
            return;
        }
        if (constraints instanceof String s) {
            switch (s) {
                case LEFT:
                case TOP:
                    setLeftComponent(comp);
                    return;
                case RIGHT:
                case BOTTOM:
                    setRightComponent(comp);
                    return;
                case DIVIDER:
                    vaadinx.EHelper.onUnimplemented("JSplitPane",
                            "addImpl/DIVIDER", comp);
                    return;
                default:
                    vaadinx.EHelper.onUnimplemented("JSplitPane",
                            "addImpl/unknown-constraint", comp, constraints);
                    return;
            }
        }
        vaadinx.EHelper.onUnimplemented("JSplitPane",
                "addImpl/non-String-constraint", comp, constraints);
    }

    @Override
    protected void validateTree() {
        super.validateTree();
        if (leftComponent != null) leftComponent.validate();
        if (rightComponent != null) rightComponent.validate();
    }

    // ---- Divider location --------------------------------------------

    /**
     * Field-shadows the int value, fires {@code "dividerLocation"} PCE
     * on change, does NOT touch the peer. Vaadin's splitter is
     * percent-only and we have no container width to translate (R_layouts_close_enough).
     * The surrogate-side setter logs WARN per SD_sjsplitpane; we don't reach for
     * it here — the field round-trip suffices on the emulator path.
     */
    public void setDividerLocation(int location) {
        int old = this.dividerLocation;
        if (old == location) return;
        // JDK also pushes the prior into lastDividerLocation; mirror.
        if (old != -1) {
            int oldLast = this.lastDividerLocation;
            this.lastDividerLocation = old;
            if (oldLast != old) {
                firePropertyChange(LAST_DIVIDER_LOCATION_PROPERTY, oldLast, old);
            }
        }
        this.dividerLocation = location;
        firePropertyChange(DIVIDER_LOCATION_PROPERTY, old, location);
    }

    /**
     * Proportional 0–1 form — forwards to the surrogate (which multiplies
     * by 100 and writes splitter position). Does NOT update the int
     * field shadow (different semantic — JDK's proportional form doesn't
     * pre-compute a pixel value), does NOT fire PCE (no Integer pixels
     * to put in the event, same gap as drag-end PCE).
     */
    public void setDividerLocation(double proportional) {
        withPeer(p -> surrogate().setDividerLocation(proportional));
    }

    public int getDividerLocation() {
        return dividerLocation;
    }

    public int getLastDividerLocation() {
        return lastDividerLocation;
    }

    public void setLastDividerLocation(int location) {
        int old = this.lastDividerLocation;
        if (old == location) return;
        this.lastDividerLocation = location;
        firePropertyChange(LAST_DIVIDER_LOCATION_PROPERTY, old, location);
    }

    /**
     * JDK reads from the UI delegate's pixel coords; we have no such
     * thing (R_layouts_close_enough). Drop-and-WARN, return -1.
     */
    public int getMinimumDividerLocation() {
        vaadinx.EHelper.onUnimplemented("JSplitPane", "getMinimumDividerLocation");
        return -1;
    }

    public int getMaximumDividerLocation() {
        vaadinx.EHelper.onUnimplemented("JSplitPane", "getMaximumDividerLocation");
        return -1;
    }

    /**
     * Best-effort snap-back per R_best_effort_behaviour — forwards to the surrogate which
     * writes splitter position 50/50, AND resets the int field shadow
     * to -1 + fires {@code "dividerLocation"} PCE if the prior value was
     * non-sentinel.
     */
    public void resetToPreferredSizes() {
        withPeer(p -> surrogate().resetToPreferredSizes());
        int old = this.dividerLocation;
        if (old != -1) {
            this.dividerLocation = -1;
            firePropertyChange(DIVIDER_LOCATION_PROPERTY, old, -1);
        }
    }

    // ---- Field-shadow knobs without a peer counterpart ----------------

    public double getResizeWeight() {
        return resizeWeight;
    }

    /**
     * Round-trip the weight on the emulator only — Vaadin SplitLayout
     * has no Java-side flex-weight knob. JLawyer calls
     * {@code setResizeWeight(0.0)} on the outer split and {@code 0.45}
     * on the inner one; the values round-trip but don't affect Vaadin's
     * flex distribution.
     */
    public void setResizeWeight(double weight) {
        if (weight < 0.0 || weight > 1.0) {
            // JDK throws IAE on out-of-range; preserve per R_match_swing_errors.
            throw new IllegalArgumentException(
                    "proportion must be between 0 and 1.");
        }
        double old = this.resizeWeight;
        if (old == weight) return;
        this.resizeWeight = weight;
        firePropertyChange(RESIZE_WEIGHT_PROPERTY, old, weight);
    }

    public boolean isOneTouchExpandable() {
        return oneTouchExpandable;
    }

    public void setOneTouchExpandable(boolean b) {
        boolean old = this.oneTouchExpandable;
        if (old == b) return;
        this.oneTouchExpandable = b;
        firePropertyChange(ONE_TOUCH_EXPANDABLE_PROPERTY, old, b);
    }

    public boolean isContinuousLayout() {
        return continuousLayout;
    }

    public void setContinuousLayout(boolean b) {
        boolean old = this.continuousLayout;
        if (old == b) return;
        this.continuousLayout = b;
        firePropertyChange(CONTINUOUS_LAYOUT_PROPERTY, old, b);
    }

    public int getDividerSize() {
        return dividerSize;
    }

    public void setDividerSize(int size) {
        int old = this.dividerSize;
        if (old == size) return;
        this.dividerSize = size;
        firePropertyChange(DIVIDER_SIZE_PROPERTY, old, size);
    }

    // ---- L&F surface --------------------------------------------------

    public String getUIClassID() {
        return "SplitPaneUI";
    }

    public void updateUI() {
        // L&F swap — no-op for us; Vaadin owns the DOM. Same shape JPanel.updateUI.
    }

    public javax.swing.plaf.SplitPaneUI getUI() {
        vaadinx.EHelper.onUnimplemented("JSplitPane", "getUI");
        return null;
    }

    public void setUI(javax.swing.plaf.SplitPaneUI ui) {
        vaadinx.EHelper.onUnimplemented("JSplitPane", "setUI", ui);
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JSplitPane", "getAccessibleContext");
        return null;
    }

    @Override
    protected String paramString() {
        // JDK's JSplitPane.paramString appends: dividerSize, lastDividerLocation,
        // continuousLayout, dividerLocation, oneTouchExpandable, orientation,
        // resizeWeight. All seven round-trip on the emulator.
        String orientStr = (getOrientation() == VERTICAL_SPLIT) ? "VERTICAL_SPLIT" : "HORIZONTAL_SPLIT";
        return super.paramString()
                + ",dividerSize=" + dividerSize
                + ",lastDividerLocation=" + lastDividerLocation
                + ",continuousLayout=" + continuousLayout
                + ",dividerLocation=" + dividerLocation
                + ",oneTouchExpandable=" + oneTouchExpandable
                + ",orientation=" + orientStr
                + ",resizeWeight=" + resizeWeight;
    }
}
