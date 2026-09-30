/*
 * Copyright (c) 1995, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's java.awt.Checkbox
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.awt;

// Hand-written emulator — fourth widget in the AWT lane after Button, Label
// and Choice, and the first that carries a second ported class
// (CheckboxGroup) and two-way package-private hooks into it. Rationale + the
// sizing of the rest of the AWT widget family: ideas/awt-widgets.md;
// decisions: D_awt_checkbox / SD_scheckbox.

/**
 * Emulator for {@link java.awt.Checkbox} — the AWT 1.0 checkbox, not
 * {@link vaadinx.swing.JCheckBox}. A thin delegating shell over its
 * {@link com.vaadin.swingbridge.surrogates.SCheckbox} peer, with the
 * {@link CheckboxGroup} bookkeeping that turns a checkbox into a radio
 * button:
 *
 * <pre>{@code
 * Checkbox verbose = new Checkbox("Verbose", false);
 * verbose.addItemListener(e -> setVerbose(e.getStateChange() == ItemEvent.SELECTED));
 * verbose.setState(true);        // silent: no ItemEvent, exactly as in AWT
 *
 * CheckboxGroup units = new CheckboxGroup();
 * new Checkbox("metric", true, units);      // grouped → renders as a radio
 * new Checkbox("imperial", false, units);
 * }</pre>
 *
 * <h2>Only the browser posts events</h2>
 *
 * Every programmatic path here is silent — {@link #setState}, the ctors,
 * {@link #setCheckboxGroup} and every {@code CheckboxGroup} mutator. In AWT
 * only the toolkit posts an {@link java.awt.event.ItemEvent}, which inverts
 * Swing, where {@code AbstractButton.setSelected} drives a
 * {@code ButtonModel} fan-out. A browser toggle posts exactly <em>one</em>
 * event, on the clicked box: the sibling a group cascade turns off gets
 * nothing.
 *
 * <p>Two clicks post nothing at all, both because the group vetoes the state
 * change: unchecking the box that is its group's current selection, and
 * re-clicking that box. Both bounce the peer back to checked.
 *
 * <p>Responsibility split with the peer:
 *
 * <ul>
 *   <li><b>Surrogate</b> — the rendered Vaadin {@code Checkbox}, the
 *       from-client value → {@code ItemEvent} fan-out, and the radio glyph
 *       (appearance only).</li>
 *   <li><b>Emulator (here)</b> — the AWT-shape API migrated code compiles
 *       against, the {@code label}/{@code state}/{@code group} fields, and
 *       all group semantics: {@code :surrogates} knows nothing about groups
 *       (D_buttongroup_browser_click).</li>
 * </ul>
 *
 * <p>Leaf peer is locked down per R_leaf_peer_lockdown: {@code java.awt.Checkbox} has no
 * public subclass in {@code java.awt} ({@code CheckboxMenuItem} is a
 * {@code MenuItem}, an unrelated {@code MenuComponent} branch), so there is
 * no protected {@code (Component peer)} ctor and every instance peers over an
 * {@link com.vaadin.swingbridge.surrogates.SCheckbox}. The checkbox-vs-radio duality does not
 * force a peer seam — AWT reads the glyph off {@code group != null} at paint
 * time, so it is a live property of one peer, not two peer types.
 */
public class Checkbox extends vaadinx.awt.Component
        implements java.awt.ItemSelectable, javax.accessibility.Accessible {

    // AWT stores the label verbatim, null included, and getLabel() returns it
    // as-is; the peer normalises null to "". The emulator is the only side
    // where the JDK's null round-trip survives — and it matters twice over,
    // since the label is also the ItemEvent's item payload and
    // getSelectedObjects()'s single element.
    private java.lang.String label;

    // The AWT-side truth, so getState() answers without a peer round-trip and
    // the group's re-selection dance has a field to read.
    private boolean state;

    // Package-private: CheckboxGroup.setCurrent reads it to reject a foreign
    // box and to decide whether the outgoing box is still one of its own.
    CheckboxGroup group;

    public Checkbox() throws java.awt.HeadlessException {
        this("", false, null);
    }

    public Checkbox(java.lang.String label) throws java.awt.HeadlessException {
        this(label, false, null);
    }

    public Checkbox(java.lang.String label, boolean state) throws java.awt.HeadlessException {
        this(label, state, null);
    }

    /**
     * @param label null is legal and reads back from {@link #getLabel} verbatim
     * @param state a checked box joining a group makes itself that group's
     *        selection, turning off whichever box held it — silently
     * @throws java.awt.HeadlessException never in practice — the clause is kept
     *         for signature fidelity, matching {@link vaadinx.awt.Frame}
     */
    public Checkbox(java.lang.String label, boolean state, CheckboxGroup group)
            throws java.awt.HeadlessException {
        // R_leaf_peer_lockdown lock-down: super(...) takes the SCheckbox directly, no peer seam.
        super(new com.vaadin.swingbridge.surrogates.SCheckbox());
        installPeerBridge();
        // Field assignments before any peer push (R_swing_is_truth), and in the JDK's own
        // order — group.setSelectedCheckbox(this) below reads all three.
        this.label = label;
        this.state = state;
        this.group = group;
        if (state && group != null) {
            group.setSelectedCheckbox(this);
        }
        surrogate().setLabel(label);
        surrogate().setState(this.state);
        surrogate().setRadioLook(group != null);
    }

    /** Argument order swapped; no other difference from the three-arg ctor. */
    public Checkbox(java.lang.String label, CheckboxGroup group, boolean state)
            throws java.awt.HeadlessException {
        this(label, state, group);
    }

    private void installPeerBridge() {
        // Peer → AWT event pipeline. The surrogate funnels browser toggles
        // through SHelper.callSwing into its own ItemListener fan-out; we
        // re-source the event to `this` so migrated code casting
        // `(Checkbox) e.getItemSelectable()` — or reading e.getItem() — sees
        // the emulator and the emulator's label. callSwing again per R_callswing_envelope: the
        // surrogate's envelope is already open, and nested callSwing runs
        // inline (D_callswing_loom).
        //
        // Enter at processEvent, not processItemEvent: AWT routes a
        // peer-posted event dispatchEvent → processEvent → processItemEvent,
        // so entering at the second hop would leave a migrator's processEvent
        // override compiling, looking wired, and never running on a real
        // toggle (R_no_vaadin_in_api limb 2).
        surrogate().addItemListener(e -> vaadinx.EHelper.callSwing(() -> {
            boolean selected = e.getStateChange() == java.awt.event.ItemEvent.SELECTED;
            setState(selected);
            if (state != selected) {
                // The group vetoed it — an AWT radio cannot be clicked off,
                // and re-clicking the current selection is a no-op that posts
                // nothing (JDK bug 4039594's comment in XCheckboxPeer.action).
                // setStateInternal has already pushed the peer back to
                // checked, so there is nothing left to do but stay silent.
                return;
            }
            processEvent(new java.awt.event.ItemEvent(
                    this, e.getID(), this.label, e.getStateChange()));
        }));
    }

    private com.vaadin.swingbridge.surrogates.SCheckbox surrogate() {
        return (com.vaadin.swingbridge.surrogates.SCheckbox) getPeer();
    }

    @Override
    public void addNotify() {
        // JDK creates the native peer here. Ours is eternal and created in
        // the ctor, so there is nothing to allocate — but the override is
        // kept rather than dropped: user code overriding addNotify() and
        // calling super.addNotify() is a common AWT idiom, and Component's
        // implementation is what logs the displayable transition.
        super.addNotify();
    }

    // --- label --------------------------------------------------------

    public java.lang.String getLabel() {
        // AWT returns the field verbatim — null if constructed with null.
        return label;
    }

    public void setLabel(java.lang.String label) {
        // AWT's changed-only guard, null-safe in both directions.
        if (label == this.label || (this.label != null && this.label.equals(label))) {
            return;
        }
        // R_swing_is_truth: store the Swing-side field first, then push to the peer.
        this.label = label;
        withPeer(p -> surrogate().setLabel(label));
    }

    // --- state --------------------------------------------------------

    public boolean getState() {
        return state;
    }

    /**
     * Sets the checked state, silently — no {@link java.awt.event.ItemEvent},
     * in any of the paths below.
     *
     * @param state {@code false} is <em>ignored</em> when this box is its
     *        group's current selection: AWT upgrades it back to {@code true}
     *        rather than leaving the group with nothing selected. Passing
     *        {@code true} on a grouped box turns its sibling off.
     */
    public void setState(boolean state) {
        // Read the field once: group.setSelectedCheckbox re-enters this class
        // and the JDK deliberately holds no lock across the call.
        CheckboxGroup group = this.group;
        if (group != null) {
            if (state) {
                group.setSelectedCheckbox(this);
            } else if (group.getSelectedCheckbox() == this) {
                state = true;
            }
        }
        setStateInternal(state);
    }

    /**
     * Assigns the state and pushes it to the peer, consulting no group and
     * firing nothing.
     *
     * <p>{@link CheckboxGroup#setCurrent} calls this for the
     * <em>incoming</em> box specifically to bypass the group logic
     * in {@link #setState}, which would otherwise recurse.
     */
    void setStateInternal(boolean state) {
        this.state = state;
        withPeer(p -> surrogate().setState(state));
    }

    /**
     * @return a one-element array holding the label — which may itself be
     *         null — when checked, else null. AWT's convention here, which
     *         {@code java.awt.List} does <em>not</em> share (it answers an
     *         empty array)
     */
    @Override
    public java.lang.Object[] getSelectedObjects() {
        if (state) {
            return new java.lang.Object[]{label};
        }
        return null;
    }

    // --- group --------------------------------------------------------

    public CheckboxGroup getCheckboxGroup() {
        return group;
    }

    /**
     * Re-homes this box into {@code g}, switching the glyph to match.
     *
     * <p>Leaves the <em>old</em> group with no selection at all when
     * this box was holding it. That is AWT's behaviour and not an
     * oversight: the clearing call runs after the box has already
     * been re-homed, so {@code setCurrent}'s
     * still-one-of-mine guard skips the deselect and only the
     * pointer is dropped.
     *
     * @param g null removes the box from its group; the same group it already
     *        belongs to is an early return
     */
    public void setCheckboxGroup(CheckboxGroup g) {
        CheckboxGroup oldGroup;
        boolean oldState;

        if (this.group == g) {
            return;
        }

        synchronized (this) {
            oldGroup = this.group;
            oldState = getState();

            this.group = g;
            // XCheckboxPeer.setCheckboxGroup repaints on change — the glyph is
            // a live property of group != null, not a ctor-time choice.
            withPeer(p -> surrogate().setRadioLook(g != null));
            if (this.group != null && getState()) {
                if (this.group.getSelectedCheckbox() != null) {
                    setState(false);
                } else {
                    this.group.setSelectedCheckbox(this);
                }
            }
        }

        // Outside the lock: the JDK notes that locking the box across
        // CheckboxGroup.setSelectedCheckbox deadlocks (JDK bug 4726853).
        if (oldGroup != null && oldState) {
            oldGroup.setSelectedCheckbox(null);
        }
    }

    // --- ItemListener -------------------------------------------------

    @Override
    public synchronized void addItemListener(java.awt.event.ItemListener l) {
        // AWT no-ops on null rather than throwing; EventListenerList would
        // store it and NPE at dispatch.
        if (l == null) return;
        listenerList.add(java.awt.event.ItemListener.class, l);
    }

    @Override
    public synchronized void removeItemListener(java.awt.event.ItemListener l) {
        if (l == null) return;
        listenerList.remove(java.awt.event.ItemListener.class, l);
    }

    public synchronized java.awt.event.ItemListener[] getItemListeners() {
        return awtListeners(java.awt.event.ItemListener.class);
    }

    @Override
    protected void processEvent(java.awt.AWTEvent e) {
        // JDK's Checkbox.processEvent peels ItemEvent off before delegating
        // the rest to Component.processEvent.
        if (e instanceof java.awt.event.ItemEvent ie) {
            processItemEvent(ie);
            return;
        }
        super.processEvent(e);
    }

    /**
     * The single funnel every toggle reaches the listeners through — both the
     * peer bridge and a user-code {@code processEvent} call. AWT subclasses
     * override this (calling super) to intercept toggles wholesale, so it must
     * stay the only dispatch path.
     */
    protected void processItemEvent(java.awt.event.ItemEvent e) {
        if (e == null) return;
        for (java.awt.event.ItemListener l
                : awtListeners(java.awt.event.ItemListener.class)) {
            l.itemStateChanged(e);
        }
    }

    @Override
    protected java.lang.String paramString() {
        // JDK's Checkbox.paramString omits the label clause entirely when the
        // label is null — unlike Button.paramString, which appends it always.
        java.lang.String str = super.paramString();
        if (label != null) {
            str += ",label=" + label;
        }
        return str + ",state=" + state;
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("Checkbox", "getAccessibleContext");
        return null;
    }
}
